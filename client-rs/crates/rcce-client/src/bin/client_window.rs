//! Real-time client window: logs into a running server and renders the live
//! world — textured terrain/scenery (static) plus animated actors (dynamic),
//! at the display refresh rate, with a camera that orbits the local player.
//! Falls back to a zone-only spectator view if login fails.
//!
//!   cargo run -p rcce-client --bin client-window --release -- [host] [port] [zone]
//!
//! NOTE: needs a display + (for the live view) a running server. In a headless
//! agent environment it still opens on the host desktop; stdout logs init,
//! login, actor count, and fps so it can be sanity-checked without seeing
//! pixels.

use std::rc::Rc;
use std::sync::Arc;
use std::time::Instant;

use winit::application::ApplicationHandler;
use winit::event::{DeviceEvent, DeviceId, ElementState, MouseButton, MouseScrollDelta, WindowEvent};
use winit::event_loop::{ActiveEventLoop, ControlFlow, EventLoop};
use winit::keyboard::{KeyCode, PhysicalKey};
use winit::window::{CursorGrabMode, Window, WindowId};

use rcce_client::net::movement_packet;

use enet_sys::EnetTransport;
use rcce_client::assets::{attachment_placement, clip_frame, AssetStore};
use rcce_client::login::{
    account_login, create_char, delete_char, enter_world, login, CharInfo, Credentials,
};
use rcce_client::world::World;
use rcce_data::{AreaScenery, B3dModel, Image};
use rcce_net::Transport;
use rcce_render::{SceneInstance, WorldView};

/// Top-level client screen. The window boots into `Login`, advances to
/// `CharSelect` after a successful account login, and to `InWorld` once a
/// character enters the game. `RCCE_AUTOLOGIN` skips straight to `InWorld`
/// (the headless/benchmark path).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Mode {
    /// Optional license gate shown before login when `EULA.txt` is non-empty
    /// (MENU-13). Accept → Login; Decline (Esc) → quit.
    Eula,
    Login,
    /// Sound options screen (master volume + mute), reached from Login with `O`,
    /// Esc returns to Login.
    Options,
    /// Read-only keybind reference, reached from the Options screen with Tab.
    Controls,
    CharSelect,
    InWorld,
}

/// Result handed back from the background account-login worker: the moved-back
/// transport + peer + character roster on success, or an error string. Carried
/// over an `mpsc` channel so the connect/handshake never blocks the UI thread.
type LoginResult = Result<(EnetTransport, i32, Vec<CharInfo>), String>;

/// The in-world key bindings, shown on the `Mode::Controls` reference screen
/// (MENU-OPT). Kept in sync with the `WindowEvent::KeyboardInput` match below —
/// these are the literal bindings, not invented. `(action, key)`.
const KEYBINDS: &[(&str, &str)] = &[
    ("Move", "W A S D  /  Arrows"),
    ("Turn camera", "Q  E"),
    ("Run", "Shift (hold)"),
    ("Jump", "J"),
    ("Attack nearest", "Space  /  F"),
    ("Mouse-look (toggle)", "Tab"),
    ("First-person (toggle)", "V"),
    ("Snap camera behind", "Middle-click"),
    ("Zoom in / out", "=  /  -   ·  Wheel"),
    ("Cycle target", "T"),
    ("Interact with target", "R"),
    ("Examine target", "X"),
    ("Inventory", "I"),
    ("Spellbook", "K"),
    ("Quest log", "L"),
    ("Party", "P"),
    ("Open chat", "Enter"),
    ("Chat scrollback", "PageUp / PageDown"),
    ("Volume down / up", "[  /  ]"),
    ("Mute", "M"),
    ("Close panel / quit", "Esc"),
];

struct Gfx {
    surface: wgpu::Surface<'static>,
    device: wgpu::Device,
    queue: wgpu::Queue,
    config: wgpu::SurfaceConfiguration,
}

impl Gfx {
    fn new(window: Arc<Window>) -> (Gfx, wgpu::TextureFormat) {
        let size = window.inner_size();
        let instance = wgpu::Instance::default();
        let surface = instance.create_surface(window.clone()).expect("surface");
        let adapter = pollster::block_on(instance.request_adapter(&wgpu::RequestAdapterOptions {
            power_preference: wgpu::PowerPreference::HighPerformance,
            force_fallback_adapter: false,
            compatible_surface: Some(&surface),
        }))
        .expect("adapter");
        let (device, queue) = pollster::block_on(adapter.request_device(
            &wgpu::DeviceDescriptor {
                label: Some("window"),
                required_features: wgpu::Features::empty(),
                required_limits: wgpu::Limits::downlevel_defaults()
                    .using_resolution(adapter.limits()),
                memory_hints: wgpu::MemoryHints::Performance,
            },
            None,
        ))
        .expect("device");
        let caps = surface.get_capabilities(&adapter);
        let format = caps
            .formats
            .iter()
            .copied()
            .find(|f| !f.is_srgb())
            .unwrap_or(caps.formats[0]);
        // Uncap the frame rate by default: Mailbox (render uncapped, present the
        // latest at refresh — no tearing) → Immediate (uncapped, may tear) → Fifo
        // (vsync, ~refresh-capped). RCCE_VSYNC=1 forces Fifo back on.
        let present_mode = if std::env::var_os("RCCE_VSYNC").is_some() {
            wgpu::PresentMode::Fifo
        } else if caps.present_modes.contains(&wgpu::PresentMode::Mailbox) {
            wgpu::PresentMode::Mailbox
        } else if caps.present_modes.contains(&wgpu::PresentMode::Immediate) {
            wgpu::PresentMode::Immediate
        } else {
            wgpu::PresentMode::Fifo
        };
        // RCCE_RES="WxH" forces the surface/render size — used for headless HUD
        // captures (the offscreen RCCE_SHOT renders at config.width/height and the
        // overlay lays out against them, so this gives a full-res HUD screenshot
        // even when the headless window's physical size is tiny).
        let (cfg_w, cfg_h) = std::env::var("RCCE_RES")
            .ok()
            .and_then(|s| {
                let p: Vec<u32> = s.split(['x', 'X']).filter_map(|t| t.trim().parse().ok()).collect();
                (p.len() == 2 && p[0] > 0 && p[1] > 0).then(|| (p[0], p[1]))
            })
            .unwrap_or((size.width.max(1), size.height.max(1)));
        let config = wgpu::SurfaceConfiguration {
            usage: wgpu::TextureUsages::RENDER_ATTACHMENT,
            format,
            width: cfg_w,
            height: cfg_h,
            present_mode,
            desired_maximum_frame_latency: 2,
            alpha_mode: caps.alpha_modes[0],
            view_formats: vec![],
        };
        surface.configure(&device, &config);
        let info = adapter.get_info();
        println!(
            "[client-window] {}x{} via {} [{:?}] ({:?}) present={:?}",
            config.width, config.height, info.name, info.backend, format, present_mode
        );
        (Gfx { surface, device, queue, config }, format)
    }

    fn resize(&mut self, w: u32, h: u32) {
        if w > 0 && h > 0 {
            self.config.width = w;
            self.config.height = h;
            self.surface.configure(&self.device, &self.config);
        }
    }
}

struct Net {
    transport: EnetTransport,
    world: World,
    peer: i32,
    updates: u64,
    /// Whether we've requested the `P_FetchActors` env block (server clock) yet.
    env_requested: bool,
}

/// One thing that can sit on an action-bar slot: a spell (by id) or an item (by
/// id). Matches Blitz's `Slots$` `"S"name` / `"I"itemid` tagging. The server keys
/// spells by name on the wire; ids resolve to names at send time.
#[derive(Clone, Copy, PartialEq, Debug)]
enum HotbarEntry {
    Spell(u16),
    Item(u16),
}

/// Where an in-flight hotbar drag started — a spellbook row (known-spell index,
/// for the click-vs-drag fallback to memorise), an action-bar slot (slot index,
/// so a drop elsewhere moves/clears it), or an inventory slot (drag an item onto
/// the bar). Parity with Blitz's WSpells / inventory → action-bar drag.
#[derive(Clone, Copy)]
enum DragSrc {
    Spellbook(usize),
    Slot(usize),
    /// An inventory item slot (0..45). The source slot is retained so a drop onto
    /// another inventory slot can equip / move / unequip (dropping onto the hotbar
    /// instead just copies the carried `HotbarEntry::Item`).
    Inventory(u8),
}

/// An active left-button hotbar drag: the entry being carried, its origin, the
/// press position and whether the cursor has moved far enough to count as a drag
/// (vs a plain click, which falls back to memorise / cast on release).
#[derive(Clone, Copy)]
struct SpellDrag {
    entry: HotbarEntry,
    src: DragSrc,
    start: (f32, f32),
    moved: bool,
}

struct App {
    host: String,
    port: u16,
    zone: String,
    window: Option<Arc<Window>>,
    gfx: Option<Gfx>,
    view: Option<WorldView>,
    overlay: Option<rcce_render::Overlay>,
    store: Option<AssetStore>,
    net: Option<Net>,
    center: [f32; 3],
    span: f32,
    ground_y: f32,
    /// Terrain height field — actors sample it to stand on the ground (their
    /// server Y is only a stale spawn height; `P_StandardUpdate` omits Y).
    height_field: Option<rcce_client::terrain::HeightField>,
    /// Zone water planes (params + texture) + the current scroll offset — rebuilt
    /// per frame so the surface texture scrolls (Blitz `PositionTexture`).
    water_planes: Vec<(rcce_data::WaterPlane, rcce_data::Image)>,
    water_scroll: [f32; 2],
    /// Live particle emitters for the current zone (simulated each frame).
    emitters: Vec<ZoneEmitter>,
    /// Solid-prop occluder spheres (world centre, radius) for camera collision —
    /// buildings/rocks/props, excluding terrain and see-through foliage. The
    /// third-person boom shortens when it would pass through one.
    cam_occluders: Vec<([f32; 3], f32)>,
    fog_color: [f32; 3],
    fog_near: f32,
    fog_far: f32,
    /// Zone lighting: ambient floor + toward-light unit vector (from the area
    /// header's DefaultLightPitch/Yaw).
    ambient: [f32; 3],
    light_dir: [f32; 3],
    start: Instant,
    frames: u64,
    last_log: Instant,
    /// Benchmark mode (RCCE_BENCH=N): after a warmup, time N frames, print the
    /// average fps, and exit — so GPU/skinning perf is measurable. `bench_t0` /
    /// `bench_f0` mark the measurement window start.
    bench_target: Option<u64>,
    bench_t0: Option<Instant>,
    bench_f0: u64,
    /// Hash of the last dynamic-actor build (anim tick + actor states); the
    /// expensive re-skin/re-upload is skipped while it's unchanged.
    last_dyn_hash: u64,
    /// Movement input: W/A/S/D held + Shift (run). Camera yaw (radians) rotated
    /// by Left/Right arrows; WASD move relative to it. `last_move` throttles the
    /// P_StandardUpdate send; `was_moving` lets us send one stop packet on idle.
    keys_wasd: [bool; 4],
    run: bool,
    cam_yaw: f32,
    /// Vertical look angle (radians, + tilts the camera up over the player).
    cam_pitch: f32,
    /// Third-person boom length (CAM-3). Adjusted by the mouse wheel and the
    /// `-`/`=` keys, clamped to [`CAM_DIST_MIN`, `CAM_DIST_MAX`]; fed as the
    /// boom's max distance (camera collision may pull it in further).
    cam_dist: f32,
    /// Mouse-look active: cursor grabbed/hidden, mouse motion drives yaw/pitch.
    /// Toggled with Tab; off by default so the headless/autowalk path and the
    /// arrow/Q-E discrete turn keep working unchanged.
    mouse_look: bool,
    last_move: Instant,
    was_moving: bool,
    /// Click-to-move destination in world XZ. `Some` while walking toward a
    /// left-clicked ground point; the per-frame movement steers `dir` toward it
    /// and clears it on arrival or when WASD/auto input overrides (MOVE-5).
    move_target: Option<[f32; 2]>,
    /// MOVE-6: the current click-to-move was issued by a double-click, so the
    /// player runs to it (set on a ground double-click, cleared on arrival).
    move_running: bool,
    /// Last ground left-click time + screen pos, for ground double-click detection.
    last_ground_click: Instant,
    last_ground_pos: [f32; 2],
    /// `Some` while the chat line is open (the typed buffer); movement keys are
    /// suppressed. Enter sends + closes, Esc cancels.
    chat_input: Option<String>,
    /// Chat scrollback offset: how many newest lines to skip (PageUp/PageDown),
    /// so older history scrolls into the chat window (CHAT-3).
    chat_scroll: usize,
    /// Runtime id of the last-attacked actor (for the target highlight).
    target: Option<u16>,
    /// Open "Actions" context menu over the selected actor (TGT-3), if any.
    context_menu: Option<ContextMenu>,
    /// Open item context menu over a right-clicked inventory slot (Use/Equip/Drop).
    item_menu: Option<ItemMenu>,
    /// Screen rects (x,y,w,h) of the current NPC-dialog option lines, rebuilt
    /// each frame as the dialog draws, so hud_click can hit-test them (TGT-5).
    dialog_hitboxes: Vec<(f32, f32, f32, f32)>,
    /// Auto-attacking the current target (CBT-1): the per-frame combat loop
    /// chases to melee range then swings on `last_attack` cooldown. Set by the
    /// Attack menu/key, cleared on target death/vanish or manual movement.
    attacking: bool,
    last_attack: Instant,
    /// Elapsed-time (secs) until which the local player plays the attack clip;
    /// set on each swing so the body visibly swings (ANIM-8).
    me_attack_until: f32,
    /// Local jump state (MOVE-7/ANIM-7): current vertical offset + velocity, and
    /// whether the player is on the ground (Blitz `PlayerHasTouchedDown`). Jump
    /// keys only fire when grounded; the offset is added to the local body's Y.
    jump_offset: f32,
    jump_vel: f32,
    grounded: bool,
    /// First-person view mode (CAM-4): camera at the head looking along `me_yaw`,
    /// own body hidden. Toggled by `V`.
    first_person: bool,
    /// ANIM-6 idle fidget: a small LCG advanced each frame, the elapsed-time the
    /// current fidget plays until, and which `FIDGET_CLIPS` entry is playing.
    rng: u32,
    fidget_until: f32,
    fidget_clip: usize,
    /// Active screen flash (P_ScreenFlash): the effect + its start time (secs).
    flash: Option<(rcce_client::world::ScreenFlash, f32)>,
    /// Active chat bubbles over actors (P_BubbleMessage), keyed by runtime id:
    /// (text, colour, start-time secs). Fade out after ~5s (CHAT-4).
    bubbles: std::collections::HashMap<u16, (String, [f32; 4], f32)>,
    /// Floating combat-damage numbers (drained from world.combat_events).
    floaters: rcce_client::floaters::Floaters,
    /// Damage display style (CBT-5): 3 = floating numbers (default), 2 = chat
    /// lines. From Combat.dat / RCCE_DMGSTYLE. `combat_chat_consumed` is the
    /// next unprocessed index into the append-only `combat_events` log (mirrors
    /// the floaters' `consumed` cursor) so each hit makes exactly one chat line.
    damage_info_style: u8,
    combat_chat_consumed: usize,
    /// Audio output (zone music). `None` when there's no audio device.
    audio: Option<rcce_client::audio::Audio>,
    /// Character sheet (gold/level/inventory/spells) from login's P_FetchCharacter.
    sheet: Option<rcce_client::fetch::CharacterSheet>,
    /// Inventory/spellbook panel visible (toggled with I).
    show_inventory: bool,
    /// Quest-log panel visible (Quests button / L key) — QST-1.
    show_quests: bool,
    /// First visible quest (mouse-wheel scroll). Quests are variable-height
    /// (title + wrapped status), so the render clips at the window bottom and this
    /// lets a long list be reached; clamped to keep the last quest reachable.
    quest_scroll: usize,
    /// Party panel visible (Party button / P key) — PTY-1.
    show_party: bool,
    /// Spellbook window visible (K key) — SPL-1; lists `World.known_spells`.
    show_spellbook: bool,
    /// First visible spellbook row (mouse-wheel scroll), so a player with more
    /// known spells than the window fits can reach them all. Clamped each frame.
    spellbook_scroll: usize,
    /// SPL-4 memorise: the in-progress `(known_spell index, start elapsed)` while
    /// the progress bar fills, and the set of memorised spell IDs. Keyed by spell
    /// id (not known-list index) so it's the single live source of truth shared by
    /// the spellbook (green dot), the spellbook drag, and the action-bar auto-fill;
    /// populated at login from the sheet's memorised flags (`enter_outcome`).
    memorising: Option<(usize, f32)>,
    memorised: std::collections::HashSet<u16>,
    /// Spellbook row hitboxes `(x,y,w,h, known index)`, rebuilt as the panel draws.
    spell_hitboxes: Vec<(f32, f32, f32, f32, usize)>,
    /// Staged vendor transaction (Blitz batches a whole shop visit into ONE
    /// `P_OpenTrading` confirm, which ends trading server-side). `pending_buys`
    /// holds offer indices to buy; `pending_sells` holds inventory slots to sell.
    /// Number keys toggle buys, drag-to-vendor toggles sells, and the Confirm
    /// button sends them all at once. Cleared when the trade opens / confirms /
    /// cancels.
    pending_buys: Vec<usize>,
    /// Staged sells as `(inventory slot, quantity)` — the quantity lets a partial
    /// stack be sold (chosen via the `qty_prompt`).
    pending_sells: Vec<(u8, u16)>,
    /// Open quantity prompt for a partial-stack sell/drop, if any.
    qty_prompt: Option<QtyPrompt>,
    /// Explicit action-bar assignments: `action_bar[i]` = the spell/item placed on
    /// hotbar slot `i` (drag-drop from the spellbook / inventory / rearranged within
    /// the bar). All-`None` means "not yet customised" and the bar falls back to
    /// auto-filling from the memorised spells, so the default view is unchanged
    /// until the player first drags onto it. See `action_bar_ids` /
    /// `materialize_action_bar`.
    action_bar: [Option<HotbarEntry>; 12],
    /// The in-flight left-button spell drag (spellbook row → hotbar slot, or
    /// slot → slot / off-bar to clear). `None` when nothing is being dragged.
    drag: Option<SpellDrag>,
    /// Footstep cadence + the resolved footstep .ogg files.
    footsteps: rcce_client::audio::FootstepTimer,
    footstep_paths: Vec<std::path::PathBuf>,
    /// Rain/snow particles + the previous frame's elapsed time (for dt).
    weather: rcce_client::weather::WeatherSystem,
    /// Lazily-loaded fallback "Loot Bag" mesh (model + textures) for dropped items
    /// whose own world mesh (`mmesh`) is 65535/missing — Blitz's `LootBagEN`.
    loot_bag: Option<(std::rc::Rc<rcce_data::B3dModel>, Vec<Option<rcce_data::Image>>)>,
    prev_elapsed: f32,
    /// Per-spell-id cooldown end time (elapsed seconds) for the action bar.
    spell_cooldowns: std::collections::HashMap<u16, f32>,
    /// Last cursor position in physical pixels (for HUD click hit-testing while
    /// mouse-look is off). Updated on CursorMoved.
    cursor: (f32, f32),
    /// Last frame's view-projection matrix (row-major), so a world click can
    /// project actors to screen and pick the nearest to the cursor.
    vp: [f32; 16],
    /// Time of the last world-pick click and the actor it hit, for double-click
    /// detection (a double-click or Shift+click interacts with the target).
    last_click: Instant,
    last_click_rid: Option<u16>,
    /// The inventory slot the cursor was last over (panel open) with an item, so
    /// the Drop / Eat buttons act on it even after the cursor moves onto them.
    last_inv_slot: Option<u8>,
    /// Storm thunder scheduling: next play time (elapsed secs) + a rotating index
    /// over Thunder1-3.ogg.
    next_thunder: f32,
    thunder_idx: usize,
    /// Cached cloud textures (regular + storm) for the current zone, so the
    /// cloud layer swaps to darker storm clouds when it's storming without a
    /// per-frame disk reload. `cloud_is_storm` tracks which is uploaded.
    cloud_regular_img: Option<rcce_data::texture::Image>,
    cloud_storm_img: Option<rcce_data::texture::Image>,
    cloud_is_storm: bool,
    /// Project data root + the zone whose geometry/sky is currently loaded, so a
    /// live area change (player warp) reloads the new zone's scenery + sky.
    data_root: String,
    loaded_zone: String,
    /// GPU linear-blend skinning for actor bodies. ON by default (the faster
    /// path: a static skinned vbuf per appearance + a small per-frame bone-
    /// palette uniform, vs. the CPU path that re-skins and re-uploads vertices
    /// on every pose change). Set `RCCE_CPUSKIN` to force the legacy CPU path.
    /// Per-actor it still falls back to CPU when an appearance `can_skin` check
    /// fails; attachments stay CPU either way.
    gpu_skin: bool,
    /// True once the menu has replaced the startup gameplay-zone geometry with
    /// the dedicated menu scene (void + posed character). Cleared so entering
    /// the world forces a fresh zone reload (MENU-SCENE).
    menu_scene_init: bool,
    /// Whether the looping menu track (Menu.ogg) is currently playing (MENU-10).
    /// Guards the once-only start in `render_menu` and the stop on enter-world.
    menu_music_on: bool,
    /// Open image-item popup (INV-5 / `WItemWindow`): the texture catalog id of
    /// the image to show full-screen-centred. `None` when closed. Set on using an
    /// I_Image item, cleared by ESC or another click.
    image_window: Option<u16>,
    /// The license text shown on the `Mode::Eula` gate (MENU-13); `None` when the
    /// project ships no `EULA.txt`. `eula_scroll` is the first visible wrapped
    /// line (PageUp/PageDown).
    eula_text: Option<String>,
    eula_scroll: usize,

    // ---- Login / character-select menu state (Mode::Login / CharSelect) ----
    /// Current screen.
    mode: Mode,
    /// The menu connection's transport (account login + char create/delete).
    /// Moved into `Net`'s transport when a character enters the world.
    login_transport: Option<EnetTransport>,
    /// When `Some`, an account-login worker thread is connecting in the
    /// background; the UI shows "Connecting…" and keeps painting. `poll_login`
    /// drains this each frame and applies the result. `None` = no login in
    /// flight. This is the non-blocking-login state (mode stays `Login`).
    login_rx: Option<std::sync::mpsc::Receiver<LoginResult>>,
    /// Open menu-connection peer handle (valid in CharSelect).
    login_peer: i32,
    /// Editable credential fields + which one has focus (0 = user, 1 = pass).
    login_user: String,
    login_pass: String,
    login_focus: u8,
    /// MD5 of the password, cached after a successful login for create/delete.
    login_md5: String,
    /// A status / error line shown under the fields.
    login_msg: String,
    /// The account's characters (CharSelect) + the highlighted row.
    chars: Vec<CharInfo>,
    char_sel: usize,
    /// `Some(name)` while typing a new character's name (create sub-screen); the
    /// `usize` is the chosen playable-template index.
    creating: Option<(String, usize)>,
    /// Playable templates (actor id, race name) for the create race picker.
    playable: Vec<(u16, String)>,
}

impl App {
    fn new(host: String, port: u16, zone: String) -> App {
        let now = Instant::now();
        App {
            host,
            port,
            zone,
            window: None,
            gfx: None,
            view: None,
            overlay: None,
            store: None,
            net: None,
            center: [0.0; 3],
            span: 100.0,
            ground_y: 0.0,
            height_field: None,
            water_planes: Vec::new(),
            water_scroll: [0.0, 0.0],
            emitters: Vec::new(),
            cam_occluders: Vec::new(),
            fog_color: [0.45, 0.62, 0.82],
            fog_near: 1000.0,
            fog_far: 9000.0,
            ambient: [0.5, 0.5, 0.5],
            light_dir: [0.0, 0.5, -0.866],
            start: now,
            frames: 0,
            bench_target: std::env::var("RCCE_BENCH").ok().and_then(|s| s.parse::<u64>().ok()).filter(|&n| n > 0),
            bench_t0: None,
            bench_f0: 0,
            last_log: now,
            last_dyn_hash: u64::MAX,
            keys_wasd: [false; 4],
            move_target: None,
            move_running: false,
            last_ground_click: now,
            last_ground_pos: [0.0, 0.0],
            menu_scene_init: false,
            menu_music_on: false,
            image_window: None,
            eula_text: None,
            eula_scroll: 0,
            run: false,
            cam_yaw: 0.0,
            cam_pitch: 0.25,
            cam_dist: CAM_DIST_DEFAULT,
            mouse_look: false,
            last_move: now,
            was_moving: false,
            chat_input: None,
            chat_scroll: 0,
            target: None,
            context_menu: None,
            item_menu: None,
            dialog_hitboxes: Vec::new(),
            attacking: false,
            last_attack: now,
            me_attack_until: 0.0,
            jump_offset: 0.0,
            jump_vel: 0.0,
            grounded: true,
            first_person: false,
            rng: 0x2545_F491, // nonzero LCG seed
            fidget_until: 0.0,
            fidget_clip: 0,
            flash: None,
            bubbles: std::collections::HashMap::new(),
            floaters: rcce_client::floaters::Floaters::new(),
            damage_info_style: 3,
            combat_chat_consumed: 0,
            audio: rcce_client::audio::Audio::new(),
            sheet: None,
            show_inventory: false,
            show_quests: false,
            quest_scroll: 0,
            show_party: false,
            show_spellbook: false,
            spellbook_scroll: 0,
            memorising: None,
            memorised: std::collections::HashSet::new(),
            spell_hitboxes: Vec::new(),
            action_bar: [None; 12],
            drag: None,
            pending_buys: Vec::new(),
            pending_sells: Vec::new(),
            qty_prompt: None,
            footsteps: rcce_client::audio::FootstepTimer::new(),
            footstep_paths: Vec::new(),
            weather: rcce_client::weather::WeatherSystem::new(240),
            loot_bag: None,
            prev_elapsed: 0.0,
            spell_cooldowns: std::collections::HashMap::new(),
            cursor: (0.0, 0.0),
            vp: [0.0; 16],
            last_click: now,
            last_click_rid: None,
            last_inv_slot: None,
            next_thunder: 0.0,
            thunder_idx: 0,
            cloud_regular_img: None,
            cloud_storm_img: None,
            cloud_is_storm: false,
            mode: Mode::Login,
            login_transport: None,
            login_rx: None,
            login_peer: 0,
            login_user: std::env::var("RCCE_USER").unwrap_or_else(|_| "rustbot".to_string()),
            // Interactive login starts with an EMPTY password (the user types it).
            // The "rustpass" dev default was pre-filling the field, so an
            // interactive login sent the wrong password for any non-rustbot
            // account. Keep the default only for the headless paths (RCCE_USER /
            // RCCE_PASS set), which log in as rustbot/rustpass.
            login_pass: std::env::var("RCCE_PASS").unwrap_or_else(|_| {
                if std::env::var_os("RCCE_USER").is_some() {
                    "rustpass".to_string()
                } else {
                    String::new()
                }
            }),
            login_focus: 0,
            login_md5: String::new(),
            login_msg: String::new(),
            chars: Vec::new(),
            char_sel: 0,
            creating: None,
            playable: Vec::new(),
            data_root: String::new(),
            loaded_zone: String::new(),
            // GPU skinning is the default; RCCE_CPUSKIN forces the legacy CPU
            // path (RCCE_GPUSKIN is still accepted as an explicit no-op opt-in).
            gpu_skin: std::env::var_os("RCCE_CPUSKIN").is_none(),
        }
    }
}

/// One bottom function button: a HUD action, its real Client.exe x-fraction
/// (Interface3D.bb 4:3 branch), GUI-icon key, and text-label fallback.
#[derive(Clone, Copy, PartialEq)]
enum HudAction { Chat, Map, Inventory, Spells, Character, Quests, Party, Menu }

const FUNCTION_BUTTONS: [(HudAction, f32, &str, &str); 8] = [
    (HudAction::Chat, 0.631906250, "gui:Chat", "Cht"),
    (HudAction::Map, 0.669015625, "gui:Map", "Map"),
    (HudAction::Inventory, 0.705148437, "gui:Inventory", "Inv"),
    (HudAction::Spells, 0.742257812, "gui:Abilities", "Spl"),
    (HudAction::Character, 0.780343750, "gui:Character", "Chr"),
    (HudAction::Quests, 0.816476562, "gui:Quests", "Qst"),
    (HudAction::Party, 0.853585937, "gui:Party", "Pty"),
    (HudAction::Menu, 0.893000000, "gui:Menu", "Mnu"),
];
/// Function-button baseline + size (fractions of screen) — the real GY button
/// geometry from CreateActionBarButton (4:3 branch).
const FBTN_Y: f32 = 0.9415;
const FBTN_W: f32 = 0.033203125 - 0.006;
const FBTN_H: f32 = 0.044270833 - 0.008;

/// One action in the actor "Actions" context menu (TGT-3).
#[derive(Clone, Copy, PartialEq, Debug)]
enum MenuAction {
    Interact,
    Attack,
    Examine,
    Trade,
}

/// Actions on a right-clicked inventory item (the item-slot context menu).
#[derive(Clone, Copy, PartialEq)]
enum ItemAction {
    Use,
    Equip,
    Drop,
    DropAll,
}

/// An open context menu over a right-clicked inventory item slot. Mirrors the
/// actor `ContextMenu` but keyed by inventory slot + item-specific actions.
struct ItemMenu {
    slot: u8,
    x: f32,
    y: f32,
    items: Vec<(&'static str, ItemAction)>,
}

impl ItemMenu {
    /// Build the menu for the item in `slot` at `(x,y)`: Use, Equip (equippable
    /// only), Drop, and Drop All (stacks only). Clamped to stay on screen.
    fn build(slot: u8, equippable: bool, stack: bool, x: f32, y: f32, sw: f32, sh: f32) -> ItemMenu {
        let mut items: Vec<(&'static str, ItemAction)> = vec![("Use", ItemAction::Use)];
        if equippable {
            items.push(("Equip", ItemAction::Equip));
        }
        items.push(("Drop", ItemAction::Drop));
        if stack {
            items.push(("Drop All", ItemAction::DropAll));
        }
        let h = CTX_ROW * items.len() as f32;
        let x = x.min(sw - CTX_W - 2.0).max(2.0);
        let y = y.min(sh - h - 2.0).max(2.0);
        ItemMenu { slot, x, y, items }
    }

    fn hit(&self, cx: f32, cy: f32) -> Option<ItemAction> {
        if cx < self.x || cx > self.x + CTX_W || cy < self.y {
            return None;
        }
        let row = ((cy - self.y) / CTX_ROW).floor() as usize;
        self.items.get(row).map(|&(_, a)| a)
    }
}

/// A modal "how many?" prompt for a partial-stack action (sell or drop). Opened
/// when a stack is dragged to the vendor / chosen to drop; the chosen `qty`
/// (1..=max) is applied on confirm. Mirrors Blitz's stack-quantity dialog.
#[derive(Clone)]
struct QtyPrompt {
    slot: u8,
    item_id: u16,
    max: u16,
    qty: u16,
    action: QtyAction,
}

#[derive(Clone, Copy, PartialEq)]
enum QtyAction {
    Sell,
    Drop,
}

/// Clamp a quantity into `1..=max` (max>=1). Pure — unit-tested.
fn clamp_qty(qty: i64, max: u16) -> u16 {
    qty.clamp(1, max.max(1) as i64) as u16
}

/// First free backpack slot (14..=45) given the currently `occupied` slots, or 14
/// as a fallback when the bag is full (the server then rejects/relocates). Pure —
/// shared by `[E]` and click-to-pickup and unit-tested.
fn first_free_backpack_slot(occupied: &std::collections::HashSet<u8>) -> u8 {
    (14u8..=45).find(|s| !occupied.contains(s)).unwrap_or(14)
}

/// An open "Actions" context menu anchored at a screen position over a target
/// actor — the Rust analogue of Client.exe's WContextMenu (Interface3D.bb:851).
struct ContextMenu {
    rid: u16,
    x: f32,
    y: f32,
    items: Vec<(&'static str, MenuAction)>,
}
const CTX_W: f32 = 104.0;
const CTX_ROW: f32 = 22.0;

impl ContextMenu {
    /// Build the menu for an actor at `(x,y)`; non-players also get Attack +
    /// Trade. Clamped to stay on screen.
    fn build(rid: u16, is_player: bool, x: f32, y: f32, sw: f32, sh: f32) -> ContextMenu {
        let mut items: Vec<(&'static str, MenuAction)> = vec![("Interact", MenuAction::Interact)];
        if !is_player {
            items.push(("Attack", MenuAction::Attack));
        }
        items.push(("Examine", MenuAction::Examine));
        if !is_player {
            items.push(("Trade", MenuAction::Trade));
        }
        let h = CTX_ROW * items.len() as f32;
        let x = x.min(sw - CTX_W - 2.0).max(2.0);
        let y = y.min(sh - h - 2.0).max(2.0);
        ContextMenu { rid, x, y, items }
    }

    /// The action under `(cx,cy)`, or `None` if the click is outside the menu
    /// (the caller then dismisses it).
    fn hit(&self, cx: f32, cy: f32) -> Option<MenuAction> {
        if cx < self.x || cx > self.x + CTX_W || cy < self.y {
            return None;
        }
        let row = ((cy - self.y) / CTX_ROW).floor() as usize;
        self.items.get(row).map(|&(_, a)| a)
    }
}

/// Spell action-bar slot grid (shared by the draw + hover-tooltip paths). The
/// 12 slots are left-anchored on the FBTN_Y baseline at FBTN_W×FBTN_H each.
const SPELLBAR_X0: f32 = 0.089867187;
const SPELLBAR_PITCH: f32 = 0.036132812;

/// Index of the inventory slot (0..=45) whose rect contains `(cx, cy)`, given the
/// InventoryWindow rect `iw` and the window-relative `buttons` (Interface.dat
/// inv_buttons), for an `(sw, sh)` viewport. Pure — shared by the click hit-test
/// and the hover tooltip.
fn inventory_slot_at(cx: f32, cy: f32, iw: rcce_data::IComp, buttons: &[rcce_data::IComp], sw: f32, sh: f32) -> Option<usize> {
    for (i, b) in buttons.iter().enumerate() {
        let bx = (iw.x + b.x * iw.w) * sw;
        let by = (iw.y + b.y * iw.h) * sh;
        let bw = (b.w * iw.w * sw).max(8.0);
        let bh = (b.h * iw.h * sh).max(8.0);
        if cx >= bx && cx < bx + bw && cy >= by && cy < by + bh {
            return Some(i);
        }
    }
    None
}

/// Runtime id of the actor nearest the cursor within `radius` px, projecting
/// each `(rid, [x,y,z])` through the view-projection `vp`. Pure — shared by the
/// world-click pick and the hover tooltip.
fn actor_at(cx: f32, cy: f32, actors: &[(u16, [f32; 3])], vp: &[f32; 16], sw: f32, sh: f32, radius: f32) -> Option<u16> {
    let mut best: Option<(u16, f32)> = None;
    for &(rid, pos) in actors {
        if let Some((px, py)) = rcce_render::project(vp, pos, sw, sh) {
            let d2 = (px - cx) * (px - cx) + (py - cy) * (py - cy);
            if d2 <= radius * radius && best.map(|(_, b)| d2 < b).unwrap_or(true) {
                best = Some((rid, d2));
            }
        }
    }
    best.map(|(rid, _)| rid)
}

/// Greedy word-wrap into lines of at most `max_chars` (for tooltip bodies).
fn wrap_text(s: &str, max_chars: usize) -> Vec<String> {
    let mut out = Vec::new();
    let mut cur = String::new();
    for word in s.split_whitespace() {
        if !cur.is_empty() && cur.len() + 1 + word.len() > max_chars {
            out.push(std::mem::take(&mut cur));
        }
        if !cur.is_empty() {
            cur.push(' ');
        }
        cur.push_str(word);
    }
    if !cur.is_empty() {
        out.push(cur);
    }
    out
}

/// The inventory action button (Drop / Eat) under `(cx, cy)`, given the
/// InventoryWindow rect and the two window-relative button rects. Pure — shared
/// by the draw and the click hit-test.
#[derive(Clone, Copy, PartialEq, Debug)]
enum InvAction {
    Drop,
    Eat,
}
fn inv_action_button_at(
    cx: f32,
    cy: f32,
    iw: rcce_data::IComp,
    drop: rcce_data::IComp,
    eat: rcce_data::IComp,
    sw: f32,
    sh: f32,
) -> Option<InvAction> {
    let hit = |c: rcce_data::IComp| {
        let (x, y, w, h) = ((iw.x + c.x * iw.w) * sw, (iw.y + c.y * iw.h) * sh, c.w * iw.w * sw, c.h * iw.h * sh);
        w > 1.0 && h > 1.0 && cx >= x && cx < x + w && cy >= y && cy < y + h
    };
    if hit(drop) {
        Some(InvAction::Drop)
    } else if hit(eat) {
        Some(InvAction::Eat)
    } else {
        None
    }
}

/// The effective 12-slot action bar: the explicit `action_bar` assignments once
/// the player has customised the bar (drag-drop), otherwise an auto-fill from the
/// first 12 memorised spells. A free fn (not a `&self` method) so the render loop
/// can call it while `self.gfx` is mutably borrowed — it only reads two disjoint
/// fields the caller passes in.
fn effective_action_bar(
    action_bar: &[Option<HotbarEntry>; 12],
    sheet: Option<&rcce_client::fetch::CharacterSheet>,
    memorised: &std::collections::HashSet<u16>,
) -> [Option<HotbarEntry>; 12] {
    if action_bar.iter().any(|s| s.is_some()) {
        return *action_bar;
    }
    let mut out = [None; 12];
    if let Some(sheet) = sheet {
        // Auto-fill from the LIVE memorised set (keyed by spell id), in the sheet's
        // spell order, taking the rich info (id) from the sheet. Using the live set
        // — not the sheet's login `memorised` flag — keeps the auto-fill in sync
        // with the spellbook as spells are memorised/unmemorised in-session.
        for (i, sp) in sheet.spells.iter().filter(|s| memorised.contains(&s.id)).take(12).enumerate() {
            out[i] = Some(HotbarEntry::Spell(sp.id));
        }
    }
    out
}

/// Destination slot for an inventory drag from slot `from` onto slot `to`.
/// Inventory slots 0..13 are the equipment column, 14.. the backpack. Dropping a
/// backpack item (`from >= 14`) into the equipment column (`to < 14`) equips it to
/// the item's *proper* slot `equip_slot` (so a sword dropped anywhere on the gear
/// column lands in the weapon slot); `None` equip_slot (not equippable) → no move.
/// Any other drop is a direct swap to `to`. Returns `None` for a no-op (same slot).
/// Pure, so the equip-vs-move decision is unit-testable without a live world.
fn resolve_inventory_dest(from: u8, to: u8, equip_slot: Option<u8>) -> Option<u8> {
    const BACKPACK_START: u8 = 14;
    if from == to {
        return None;
    }
    let dest = if to < BACKPACK_START && from >= BACKPACK_START {
        equip_slot?
    } else {
        to
    };
    if dest == from {
        None
    } else {
        Some(dest)
    }
}

/// Screen rect `(x, y, w, h)` of the vendor / trade window, right-anchored and
/// vertically centred. One definition shared by the render and the drag-to-sell
/// drop hit-test so they can't drift.
fn vendor_window_rect(sw: f32, sh: f32) -> (f32, f32, f32, f32) {
    let (pw, ph) = (320.0, 300.0);
    ((sw - pw - 40.0).round(), ((sh - ph) * 0.5).round(), pw, ph)
}

/// Whether `(cx, cy)` is inside the vendor window (drag-to-sell drop test).
fn point_in_vendor(cx: f32, cy: f32, sw: f32, sh: f32) -> bool {
    let (px, py, pw, ph) = vendor_window_rect(sw, sh);
    cx >= px && cx < px + pw && cy >= py && cy < py + ph
}

/// Spellbook window rect `(x, y, w, h)` — right of centre, vertically centred.
/// Shared by the render and the mouse-wheel scroll hit-test.
fn spellbook_rect(sw: f32, sh: f32) -> (f32, f32, f32, f32) {
    let (kwd, khd) = (240.0f32, 240.0f32);
    (sw * 0.5 + 12.0, (sh - khd) * 0.5, kwd, khd)
}

/// Quest-log window rect `(x, y, w, h)` — centred. Shared by the render and the
/// mouse-wheel scroll hit-test.
fn quest_window_rect(sw: f32, sh: f32) -> (f32, f32, f32, f32) {
    let (qw, qh) = (320.0f32, 240.0f32);
    ((sw - qw) * 0.5, (sh - qh) * 0.5, qw, qh)
}

/// The vendor "Confirm" button rect `(x, y, w, h)` — bottom of the vendor window.
/// Shared by the render and the click hit-test.
fn vendor_confirm_button_rect(sw: f32, sh: f32) -> (f32, f32, f32, f32) {
    let (px, py, pw, ph) = vendor_window_rect(sw, sh);
    let bw = pw - 16.0;
    (px + 8.0, py + ph - 24.0, bw, 18.0)
}

/// Whether `(cx, cy)` is on the vendor Confirm button.
fn point_in_confirm(cx: f32, cy: f32, sw: f32, sh: f32) -> bool {
    let (x, y, w, h) = vendor_confirm_button_rect(sw, sh);
    cx >= x && cx < x + w && cy >= y && cy < y + h
}

/// Index of the action-bar spell slot (0..=11) whose rect contains `(cx, cy)`.
/// Pure — uses the same geometry the spell-bar draw loop does.
fn spell_slot_at(cx: f32, cy: f32, sw: f32, sh: f32) -> Option<usize> {
    let by = FBTN_Y * sh;
    let (sw_, sh_) = (FBTN_W * sw, FBTN_H * sh);
    for i in 0..12usize {
        let x = (SPELLBAR_X0 + i as f32 * SPELLBAR_PITCH) * sw;
        if cx >= x && cx < x + sw_ && cy >= by && cy < by + sh_ {
            return Some(i);
        }
    }
    None
}

/// Compass strip marks: for a `heading` (radians, the way the player faces) and
/// a horizontal field-of-view `fov` (radians), return the visible compass points
/// as `(offset, label)` where offset ∈ [-0.5, 0.5] is the position across the
/// band (0 = centre = dead ahead) and an empty label is an intercardinal tick.
/// Pure so the live HUD and a unit test share one definition.
fn compass_marks(heading: f32, fov: f32) -> Vec<(f32, &'static str)> {
    use std::f32::consts::PI;
    let pts: [(f32, &str); 8] = [
        (0.0, "N"), (PI * 0.25, ""), (PI * 0.5, "E"), (PI * 0.75, ""),
        (PI, "S"), (PI * 1.25, ""), (PI * 1.5, "W"), (PI * 1.75, ""),
    ];
    let mut out = Vec::new();
    for (a, label) in pts {
        // Shortest signed angular difference into [-PI, PI].
        let mut d = a - heading;
        while d > PI {
            d -= 2.0 * PI;
        }
        while d < -PI {
            d += 2.0 * PI;
        }
        let off = d / fov;
        // Small epsilon so a mark sitting exactly on the edge (off = ±0.5, e.g. E
        // and W at fov = PI) isn't dropped by float rounding.
        if off.abs() <= 0.5 + 1e-3 {
            out.push((off, label));
        }
    }
    out
}

/// Which function button (if any) contains screen-pixel point `(cx, cy)` for a
/// `(sw, sh)` viewport. Pure geometry shared by the draw + click paths.
fn function_button_at(cx: f32, cy: f32, sw: f32, sh: f32) -> Option<HudAction> {
    let by = FBTN_Y * sh;
    let (bw, bh) = (FBTN_W * sw, FBTN_H * sh);
    for (action, fx, _, _) in FUNCTION_BUTTONS {
        let bx = fx * sw;
        if cx >= bx && cx < bx + bw && cy >= by && cy < by + bh {
            return Some(action);
        }
    }
    None
}

/// Value/max to draw on HUD vitals bar `i` (a slot index into Interface.dat's
/// 40 attribute bars). The Health bar reads the authoritative `me_health`
/// mirror at the project-configured slot (`health_stat`, from
/// `Fixed Attributes.dat`), NOT a hardcoded slot 0 — so a customized project's
/// Health bar tracks live HP/damage. Other bars read their attribute from
/// `me_attributes`; `None` means "skip this bar". Mirrors the model-side gate in
/// `World::on_stat_update`, which mirrors HP onto `me_health` for `health_stat`.
/// Pure — unit-tested.
fn vitals_value(
    i: usize,
    health_stat: u8,
    me_health: i16,
    me_health_max: i16,
    me_attributes: &std::collections::HashMap<u8, (i16, i16)>,
) -> Option<(f32, f32)> {
    if i == health_stat as usize {
        Some((me_health.max(0) as f32, me_health_max.max(1) as f32))
    } else if let Some(&(v, m)) = me_attributes.get(&(i as u8)) {
        Some((v.max(0) as f32, m.max(1) as f32))
    } else {
        None
    }
}

/// Build animated actor instances (the local player + tracked actors) for the
/// current frame. Returns owned models/textures (the instances borrow them) and
/// placement tuples (idx, translation, rot, color, scale).
type Placement = (usize, [f32; 3], [f32; 3], [f32; 3], [f32; 3]);
/// RuntimeID of the nearest living actor to (mx, mz), if any.
fn nearest_living_actor(world: &rcce_client::world::World, mx: f32, mz: f32) -> Option<u16> {
    world
        .actors
        .values()
        .filter(|a| a.alive)
        .min_by(|a, b| {
            let da = (a.x - mx).powi(2) + (a.z - mz).powi(2);
            let db = (b.x - mx).powi(2) + (b.z - mz).powi(2);
            da.total_cmp(&db)
        })
        .map(|a| a.runtime_id)
}

/// Whether storm lightning/thunder fires this frame (ENV-5): only during a
/// storm, once the scheduled `next` time is reached. Pure — unit-tested.
fn lightning_fires(storm: bool, now: f32, next: f32) -> bool {
    storm && now >= next
}

/// CAM-5: snap the orbit camera directly behind the character — camera yaw set
/// to the character's facing, pitch levelled. Mirrors the Blitz MMB handler
/// (`CamYaw = EntityYaw(Me)`, `CamPitch = 0`). Pure — unit-tested.
fn snap_camera(me_yaw: f32) -> (f32, f32) {
    (me_yaw, 0.0)
}

/// What a clickable menu button does. Each maps onto an existing keyboard
/// action, so the mouse path (MENU-2) is purely additive.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum MenuBtnAction {
    Login,
    Quit,
    EnterWorld,
    Create,
    Delete,
    Back,
}

/// A clickable menu button: pixel rect, sprite stem (`"BLogin"` →
/// `BLoginU.PNG` up / `BLoginH.PNG` hover), and the action it triggers.
struct MenuButton {
    rect: (f32, f32, f32, f32),
    sprite: &'static str,
    action: MenuBtnAction,
}

/// The clickable buttons for `mode`, anchored to the window rect (wx,wy,ww,wh).
/// Pure (no I/O) so the draw and hit-test paths share one layout — unit-tested.
/// Empty for non-button modes and while the create-character sub-flow is active
/// (that stays keyboard-driven). On CharSelect the Select/Delete buttons are
/// omitted when the roster is empty.
fn menu_buttons(
    mode: Mode,
    creating: bool,
    have_chars: bool,
    wx: f32,
    wy: f32,
    ww: f32,
    wh: f32,
) -> Vec<MenuButton> {
    let mut v = Vec::new();
    match mode {
        Mode::Login => {
            let (bw, bh) = (ww * 0.40, wh * 0.13);
            v.push(MenuButton {
                rect: (wx + (ww - bw) * 0.5, wy + wh * 0.68, bw, bh),
                sprite: "BLogin",
                action: MenuBtnAction::Login,
            });
            let (qw, qh) = (ww * 0.30, wh * 0.11);
            v.push(MenuButton {
                rect: (wx + (ww - qw) * 0.5, wy + wh * 0.84, qw, qh),
                sprite: "BQuit",
                action: MenuBtnAction::Quit,
            });
        }
        Mode::CharSelect if !creating => {
            let mut row: Vec<(&'static str, MenuBtnAction)> = Vec::new();
            if have_chars {
                row.push(("BSelectCharacter", MenuBtnAction::EnterWorld));
            }
            row.push(("BCreateChar", MenuBtnAction::Create));
            if have_chars {
                row.push(("BDeleteCharacter", MenuBtnAction::Delete));
            }
            row.push(("BBack", MenuBtnAction::Back));
            let n = row.len() as f32;
            let (bw, bh, gap) = (ww * 0.21, wh * 0.10, ww * 0.02);
            let total = bw * n + gap * (n - 1.0);
            let mut x = wx + (ww - total) * 0.5;
            let y = wy + wh * 0.87;
            for (sprite, action) in row {
                v.push(MenuButton { rect: (x, y, bw, bh), sprite, action });
                x += bw + gap;
            }
        }
        _ => {}
    }
    v
}

/// Topmost button whose rect contains `(cx, cy)`, or `None`. Pure — unit-tested.
fn menu_button_hit(buttons: &[MenuButton], cx: f32, cy: f32) -> Option<MenuBtnAction> {
    buttons.iter().rev().find_map(|b| {
        let (x, y, w, h) = b.rect;
        (cx >= x && cx < x + w && cy >= y && cy < y + h).then_some(b.action)
    })
}

/// Window rect for `mode`, matching `draw_menu_overlay` (the Login window drops
/// a little to leave room for the logo above it). Shared by the draw and click
/// paths so the button hit-rects line up exactly with what's drawn.
fn menu_window_for(mode: Mode, sw: f32, sh: f32) -> (f32, f32, f32, f32) {
    let wfrac = if mode == Mode::Login { 0.50 } else { 0.80 };
    let (wx, mut wy, ww, wh) = menu_window_rect(sw, sh, wfrac);
    if mode == Mode::Login {
        wy = ((sh - wh) * 0.5 + sh * 0.08).min(sh - wh - 8.0);
    }
    (wx, wy, ww, wh)
}

/// Centred menu-window rect at the 625×447 frame aspect (MENU-SCENE-b). The
/// EULA / Login window-frame PNGs are drawn here (NOT full-screen), so the 3D
/// menu scene shows around them — they are window graphics, not backdrops.
fn menu_window_rect(sw: f32, sh: f32, wfrac: f32) -> (f32, f32, f32, f32) {
    let aspect = 625.0 / 447.0;
    let ww = (sw * wfrac).min(sh * 0.86 * aspect);
    let wh = ww / aspect;
    ((sw - ww) * 0.5, (sh - wh) * 0.5, ww, wh)
}

/// The interactive client's initial menu screen (MENU-13): the EULA gate when
/// the project ships license text, otherwise straight to the login screen. Pure.
fn initial_menu_mode(eula_present: bool) -> Mode {
    if eula_present {
        Mode::Eula
    } else {
        Mode::Login
    }
}

/// Camera zoom bounds (CAM-3). Blitz clamps the mouse-wheel zoom to [5,50] and
/// the keyboard zoom to [3,50] (`Interface3D.bb:643-657`); we use [5,50] for
/// both with a 13.0 default (the prior hardcoded boom length).
const CAM_DIST_MIN: f32 = 5.0;
const CAM_DIST_MAX: f32 = 50.0;
const CAM_DIST_DEFAULT: f32 = 13.0;
/// Minimum clearance (world units) the camera eye keeps above the terrain at its
/// own X/Z, so the boom doesn't sink into a hill behind the player.
const CAM_GROUND_CLEARANCE: f32 = 1.5;
/// Menu-camera framing the character against the `Set.b3d` backdrop, matching
/// Blitz (MainMenu.bb:2023). `ANGLE` (radians) ≈ π looks +Z *into* the furnished
/// room rather than out at the banner wall; `DIST` is the pull-back; `EYE_H` /
/// `TGT_H` are the eye and look-at heights above the character's base; `LAT` is
/// the world-X strafe that pushes the character screen-right (window on the
/// left). Each is overridable at runtime via the matching `RCCE_MENU*` var.
const MENU_CAM_ANGLE: f32 = std::f32::consts::PI;
const MENU_CAM_DIST: f32 = 7.0;
const MENU_CAM_EYE_H: f32 = 1.2;
const MENU_CAM_TGT_H: f32 = 0.9;
const MENU_CAM_LAT: f32 = -2.0;
/// Menu `Set.b3d` scale + floor height. Blitz uses `ScaleEntity 30`, but the RCCE
/// Rust pipeline runs at a much smaller world scale (actor render scale ~0.05),
/// so the literal 30 over-scales the set ~20× relative to the character. Tuned
/// empirically so the rug/furniture are proportional to the character. The set
/// origin is derived from this scale to keep the character on the rug (see the
/// menu-set load). Override at runtime with `RCCE_SETSCALE` / `RCCE_SETY`.
const MENU_SET_SCALE: f32 = 1.0;
const MENU_SET_Y: f32 = 0.0;
/// Character anchor Y in the menu — now only the camera's vertical focus base
/// (the body is seated on the set floor via the height field, not this value).
/// Tuned so the camera frames the seated character full-body with headroom above
/// the head. `RCCE_CHARY` overrides.
const MENU_CHAR_Y: f32 = 2.4;
/// Set-model coordinates of the rug spot the character stands on, derived from
/// the Blitz scale-30 placement (`(char(30,_,100) - origin(-210,_,-145)) / 30`).
/// The set origin is `char - SCALE * RUG` so the character stays on the rug at
/// any scale.
const MENU_SET_RUG: [f32; 3] = [8.0, 0.0, 8.16667];
/// Water texture scroll rate (UV units/sec). Blitz scrolls `U += Δ·0.00025`,
/// `V += Δ·0.0007` per frame (`Environment3D.bb:270`) where `Δ = 30/fps`, i.e.
/// `0.0075` / `0.021` UV-units/sec — a gentle diagonal drift.
const WATER_SCROLL_U: f32 = 0.0075;
const WATER_SCROLL_V: f32 = 0.021;

/// Convert a scenery placement's stored Blitz `[pitch, yaw, roll]` (degrees) to
/// the renderer's rotation radians, **negating yaw**.
///
/// The world renders left-handed (`perspective_lh`/`look_at_lh`, chosen so the
/// path/NPC layout matches Blitz). glam's `from_rotation_y` is a right-handed
/// rotation, so a stored Blitz yaw applied directly turns scenery the wrong way
/// — fences and props face mirrored, not aligned. Negating yaw matches Blitz's
/// `RotateEntity`; pitch and roll are already correct in this frame (confirmed
/// by an in-client A/B sweep: only the yaw-negated variant matched Blitz).
///
/// Actor bodies are *not* affected: their facing is computed locally from
/// movement direction (eased toward the heading in `World::tick_movement`) and
/// calibrated independently, so they never consume a stored Blitz yaw here.
fn scenery_rot_radians(deg: [f32; 3]) -> [f32; 3] {
    [deg[0].to_radians(), -deg[1].to_radians(), deg[2].to_radians()]
}

/// Reserved music id for the looping menu track (MENU-10), distinct from any
/// zone `LoadingMusicID` so the zone-music switch on enter-world replaces it.
const MENU_MUSIC_ID: u16 = 65534;

/// Body facing (yaw, **degrees**) for a movement direction `(dx, dz)` in world
/// XZ (MOVE-1/3, blocker #4). Returns `fallback` when the direction is ~zero (so
/// Unproject a screen pixel to the world XZ where the camera ray hits the
/// **terrain** (not a flat plane). Click-to-move landed short/long because
/// `unproject_ground` intersects a flat plane at the stale `me_y`, while the
/// clicked ground is at a different elevation. Iterate: intersect the plane at
/// the current height guess, sample the real terrain there, re-intersect at that
/// height — converges to the ray/terrain crossing. `start_y` is the first guess
/// (the terrain under the player, else the zone ground).
fn unproject_terrain(
    vp: &[f32; 16],
    sw: f32,
    sh: f32,
    cx: f32,
    cy: f32,
    hf: Option<&rcce_client::terrain::HeightField>,
    start_y: f32,
) -> Option<[f32; 2]> {
    let mut plane_y = start_y;
    let mut hit = rcce_render::unproject_ground(vp, sw, sh, cx, cy, plane_y)?;
    for _ in 0..8 {
        match hf.and_then(|h| h.height_at(hit[0], hit[2])) {
            Some(ty) => {
                if (ty - plane_y).abs() < 0.05 {
                    break; // converged onto the terrain
                }
                plane_y = ty;
                hit = rcce_render::unproject_ground(vp, sw, sh, cx, cy, plane_y)?;
            }
            None => break, // off the height field — keep the last plane hit
        }
    }
    Some([hit[0], hit[2]])
}

/// Apply a zoom delta to the boom length and clamp (CAM-3). `delta` is in world
/// units: negative pulls the camera in (zoom toward the player), positive pushes
/// out. Pure — unit-tested. ref `Interface3D.bb:643-657` (CamDist ∓ MZSpeed*1.5).
fn zoom_step(dist: f32, delta: f32) -> f32 {
    (dist + delta).clamp(CAM_DIST_MIN, CAM_DIST_MAX)
}

/// Apply a volume delta and clamp to [0,1] for the Sound options screen. Pure.
fn volume_step(vol: f32, delta: f32) -> f32 {
    (vol + delta).clamp(0.0, 1.0)
}

/// Compose a chat-line for one combat event under DamageInfoStyle 2 (CBT-5),
/// mirroring `ClientCombat.bb:150-168`. Incoming (`target == me`): "<who> hits
/// you …" (red) or "… misses!" (blue); outgoing: "You hit <who> …" (green) or
/// "You attack <who> and miss!" (blue). `name_of` resolves a rid to a name. Pure.
fn compose_damage_line(
    target: u16,
    attacker: u16,
    damage: u16,
    me: u16,
    name_of: impl Fn(u16) -> String,
) -> (String, [f32; 4]) {
    if target == me {
        let who = name_of(attacker);
        if damage > 0 {
            (format!("{who} hits you for {damage} damage!"), [1.0, 0.4, 0.4, 1.0])
        } else {
            (format!("{who} attacks you and misses!"), [0.5, 0.6, 1.0, 1.0])
        }
    } else {
        let who = name_of(target);
        if damage > 0 {
            (format!("You hit {who} for {damage} damage!"), [0.4, 1.0, 0.4, 1.0])
        } else {
            (format!("You attack {who} and miss!"), [0.5, 0.6, 1.0, 1.0])
        }
    }
}

/// Which UI layers are currently open, for ESC close-precedence. Pure snapshot
/// so the ordering can be unit-tested without the live App (SPL-7 lesson).
#[derive(Debug, Clone, Copy, Default)]
struct EscOpen {
    mouse_look: bool,
    image_window: bool,
    script_input: bool,
    dialog: bool,
    context_menu: bool,
    item_menu: bool,
    trade: bool,
    spellbook: bool,
    inventory: bool,
    quests: bool,
    party: bool,
    target: bool,
}

/// The single thing ESC dismisses this press. Topmost-first; `ExitGame` only when
/// nothing is open. This is blocker #1 from DELTA.md — previously ESC fell through
/// to `event_loop.exit()` with any panel open, trapping or quitting the player.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum EscLayer {
    MouseLook,
    ImageWindow,
    ScriptInput,
    Dialog,
    ContextMenu,
    ItemMenu,
    Trade,
    Spellbook,
    Inventory,
    Quests,
    Party,
    Target,
    ExitGame,
}

/// Pure ESC precedence. Order mirrors Blitz `Interface3D.bb:412-413` (close the
/// frontmost window, only quit when the field is clear): transient overlays
/// (mouse-look, context menu) first, then modal/trade, then the toggled panels
/// in open-priority order, then the target selection, then exit.
fn esc_layer(o: EscOpen) -> EscLayer {
    if o.mouse_look {
        EscLayer::MouseLook
    } else if o.image_window {
        EscLayer::ImageWindow
    } else if o.script_input {
        EscLayer::ScriptInput
    } else if o.dialog {
        EscLayer::Dialog
    } else if o.context_menu {
        EscLayer::ContextMenu
    } else if o.item_menu {
        EscLayer::ItemMenu
    } else if o.trade {
        EscLayer::Trade
    } else if o.spellbook {
        EscLayer::Spellbook
    } else if o.inventory {
        EscLayer::Inventory
    } else if o.quests {
        EscLayer::Quests
    } else if o.party {
        EscLayer::Party
    } else if o.target {
        EscLayer::Target
    } else {
        EscLayer::ExitGame
    }
}

/// Whether a click `dt_ms` after the previous one and `dist_px` away counts as a
/// double-click (MOVE-6): close in both time (<350 ms) and space (<12 px). Pure.
fn is_double_click(dt_ms: u128, dist_px: f32) -> bool {
    dt_ms < 350 && dist_px < 12.0
}

/// Whether the player should run this frame (MOVE-6): Shift-run always wins;
/// otherwise a double-click-issued move runs while a click-to-move is active.
/// Pure — unit-tested.
fn move_run(shift_run: bool, has_move_target: bool, dbl_running: bool) -> bool {
    shift_run || (has_move_target && dbl_running)
}

/// Eye height (world units) of the first-person camera above the player's feet
/// — the same look height the third-person boom pivots around (CAM-4).
const FP_EYE_HEIGHT: f32 = 3.5;

/// First-person camera (CAM-4): eye at the player's head, looking out along the
/// character's facing `me_yaw` (flat). The forward vector matches the
/// third-person view direction when `cam_yaw = me_yaw` (the rear-follow looks
/// along the character's facing), i.e. `(-sin yaw, 0, -cos yaw)`. Returns
/// `(eye, target)`. Pure — unit-tested.
fn first_person_view(me: [f32; 3], me_yaw: f32) -> ([f32; 3], [f32; 3]) {
    let eye = [me[0], me[1] + FP_EYE_HEIGHT, me[2]];
    let fwd = [-me_yaw.sin(), 0.0, -me_yaw.cos()];
    (eye, [eye[0] + fwd[0], eye[1] + fwd[1], eye[2] + fwd[2]])
}

/// The next cycle-target after `current` in the sorted runtime-id list, wrapping
/// around; the first id if `current` is `None` or no longer present (TGT-7).
/// `None` only if the list is empty. Pure — unit-tested.
fn next_target(current: Option<u16>, sorted: &[u16]) -> Option<u16> {
    if sorted.is_empty() {
        return None;
    }
    match current.and_then(|c| sorted.iter().position(|&r| r == c)) {
        Some(i) => Some(sorted[(i + 1) % sorted.len()]),
        None => Some(sorted[0]),
    }
}

/// The visible chat lines for a scrollback `skip` (already clamped): newest-
/// first, skipping the `skip` most-recent lines and taking up to `max`. Pure —
/// shared by the renderer and unit-tested for the scrollback window (CHAT-3).
fn visible_chat(lines: &[(String, [f32; 4])], skip: usize, max: usize) -> Vec<&(String, [f32; 4])> {
    lines.iter().rev().skip(skip).take(max).collect()
}

/// Clamp a list scroll offset so it never scrolls past the last full page:
/// `min(scroll, total - visible)` (0 when everything fits). Pure — shared by the
/// spellbook render and unit-tested.
fn clamp_scroll(scroll: usize, total: usize, visible: usize) -> usize {
    scroll.min(total.saturating_sub(visible))
}

/// Per-frame combat decision for the auto-attack loop (CBT-1): chase if out of
/// melee range, swing if in range and the cooldown is ready, else wait.
#[derive(Clone, Copy, PartialEq, Debug)]
enum CombatStep {
    Swing,
    Chase,
    Wait,
}

/// Combat animation clip names (ANIM-8), tried in order (exact match first, then
/// substring). The shipped data labels them "Default attack"/"Death 1"; Hit
/// ranges are empty so there's no hit-react clip.
const ATTACK_CLIP: &[&str] = &["Default attack", "Right hand attack", "Staff attack", "attack"];
/// Per-actor death-clip name priority (CBT-6 death-anim variety). Humanoid sets
/// ship two real death clips ("Death 1" 900-932, "Death 2" 933-959); alternate
/// which is tried first by actor id so deaths aren't all identical, mirroring
/// Blitz's `Rand(Anim_FirstDeath, Anim_LastDeath)`. Animals ship a single "Die"
/// (the old `DEATH_CLIP` omitted it, so they played no death pose) — included as
/// a fallback. First name that resolves in the actor's anim set wins.
fn death_clip(rid: u16) -> &'static [&'static str] {
    if rid % 2 == 0 {
        &["Death 2", "Death 1", "Death", "Die", "death"]
    } else {
        &["Death 1", "Death 2", "Death", "Die", "death"]
    }
}
/// The Player body's jump clip (set #0 `Jump` [32..55]). ANIM-7.
const JUMP_CLIP: &[&str] = &["Jump"];
/// Jump physics (MOVE-7), the literal Blitz constants: `Gravity# = 0.0125`,
/// initial vertical velocity `JumpStrength# (8.0) * Gravity# = 0.1`. Per-frame
/// `position += velocity; velocity -= gravity`, landing when the offset hits 0.
const JUMP_GRAVITY: f32 = 0.0125;
const JUMP_INIT_VEL: f32 = 8.0 * JUMP_GRAVITY;
/// Apex height (world units) of a remote actor's `sin`-arc hop while its
/// `P_Jump` anim timer runs — matches the ~0.45-unit local-arc apex.
const JUMP_REMOTE_APEX: f32 = 0.45;

/// Idle-fidget clips (ANIM-6): the Player set's `Look around` [1231..1298] and
/// `Yawn` [193..296], played once when standing still (the Blitz
/// `Anim_LookRound..Anim_Yawn` idle variations).
const FIDGET_CLIPS: [&[&str]; 2] = [&["Look around"], &["Yawn"]];
/// Seconds a fidget plays before returning to idle (covers the longer Yawn).
const FIDGET_SECS: f32 = 3.5;

/// Seconds a memorise takes — the Blitz ~60-tick `MemorisingSpell` timer. SPL-4.
const MEMORISE_SECS: f32 = 3.0;

/// Memorise progress 0..1 for a memorise started at `start`, sampled at `now`
/// (both elapsed seconds). Clamped. Pure — unit-tested. SPL-4.
fn memorise_progress(start: f32, now: f32) -> f32 {
    ((now - start) / MEMORISE_SECS).clamp(0.0, 1.0)
}

/// Whether an idle fidget should start this frame (ANIM-6): only while idle, at
/// roughly the Blitz ~1/1000-frame probability. `rng_val` is a per-frame
/// pseudo-random word. Pure — unit-tested.
fn fidget_fires(rng_val: u32, idle: bool) -> bool {
    idle && rng_val % 1000 == 0
}

/// One frame of local jump physics (MOVE-7), mirroring Blitz's velocity
/// integration: `offset += vel`, `vel -= gravity`. Returns
/// `(offset, vel, grounded)`; lands (grounded) when the offset returns to ≤0.
/// Pure — unit-tested.
fn jump_step(offset: f32, vel: f32) -> (f32, f32, bool) {
    let o = offset + vel;
    if o <= 0.0 {
        (0.0, 0.0, true)
    } else {
        (o, vel - JUMP_GRAVITY, false)
    }
}

/// Melee reach (world units) — Client.exe's `MaxRange# = 4.0` (ClientCombat.bb:37)
/// plus a small radius pad.
const MELEE_RANGE: f32 = 4.5;
/// Client-side swing cadence (ms). The server enforces the authoritative
/// `CombatDelay`; this just paces our `P_AttackActor` sends.
const COMBAT_DELAY_MS: u64 = 1500;

fn combat_step(dist: f32, range: f32, cooldown_ready: bool) -> CombatStep {
    if dist > range {
        CombatStep::Chase
    } else if cooldown_ready {
        CombatStep::Swing
    } else {
        CombatStep::Wait
    }
}

/// Effective attack reach (CBT-2 / blocker #5b). A ranged weapon (`wtype` ==
/// `W_Ranged` = 3) with positive item-health attacks at `range - 0.5`, floored
/// at the melee base so a tiny configured range never shortens reach; every
/// other case (melee weapon, broken ranged weapon, no weapon) uses the melee
/// base. Mirrors `ClientCombat.bb:37-42`. Pure — unit-tested.
fn effective_attack_range(wtype: i16, weapon_range: f32, item_health: u8, melee_base: f32) -> f32 {
    if wtype == 3 && item_health > 0 {
        (weapon_range - 0.5).max(melee_base)
    } else {
        melee_base
    }
}

/// Colour a damage number by its damage-type index (defaults to red). The
/// indices loosely follow the engine's Damage.dat ordering; exact names aren't
/// loaded yet, so this is a stable palette for visual variety.
fn damage_color(dtype: u8, alpha: f32) -> [f32; 4] {
    let [r, g, b] = match dtype {
        0 => [1.0, 0.85, 0.30], // physical — amber
        1 => [1.0, 0.45, 0.20], // fire — orange
        2 => [0.50, 0.80, 1.0], // cold — blue
        3 => [0.70, 1.0, 0.40], // nature/poison — green
        4 => [0.85, 0.50, 1.0], // magic — violet
        _ => [1.0, 0.40, 0.40], // default — red
    };
    [r, g, b, alpha]
}

/// A GPU-skinned actor body: the source model (with bones), its textures, the
/// animation frame, the column-major instance transform, and tint. The body's
/// static mesh is uploaded once by [`WorldView::set_skinned`]; only the pose
/// uniform updates per frame.
struct SkinnedActor {
    key: String,
    model: Rc<B3dModel>,
    textures: Rc<Vec<Option<Image>>>,
    frame: Option<f32>,
    transform: [f32; 16],
    color: [f32; 3],
}

fn build_actors(
    store: &mut AssetStore,
    world: &World,
    elapsed: f32,
    gpu_skin: bool,
    me_moving: bool,
    me_running: bool,
    me_template: u16,
    me_attack: bool,
    me_jumping: bool,
    me_jump_offset: f32,
    hide_me: bool,
    me_fidget: Option<&'static [&'static str]>,
    height: Option<&rcce_client::terrain::HeightField>,
) -> (
    Vec<Rc<B3dModel>>,
    Vec<Rc<Vec<Option<Image>>>>,
    Vec<Placement>,
    Vec<String>,
    Vec<SkinnedActor>,
) {
    let mut models = Vec::new();
    let mut textures: Vec<Rc<Vec<Option<Image>>>> = Vec::new();
    let mut place = Vec::new();
    let mut keys: Vec<String> = Vec::new();
    let mut skinned: Vec<SkinnedActor> = Vec::new();

    let push = |store: &mut AssetStore,
                    models: &mut Vec<Rc<B3dModel>>,
                    textures: &mut Vec<Rc<Vec<Option<Image>>>>,
                    place: &mut Vec<Placement>,
                    keys: &mut Vec<String>,
                    skinned: &mut Vec<SkinnedActor>,
                    tmpl: u16,
                    gender: u8,
                    face: u8,
                    body: u8,
                    hair: u8,
                    beard: u8,
                    weapon_item: u16,
                    shield_item: u16,
                    weapon_override: Option<u16>,
                    rid: u16,
                    moving: bool,
                    running: bool,
                    combat: Option<(&'static [&'static str], bool)>,
                    pos: [f32; 3],
                    yaw: f32,
                    color: [f32; 3]| {
        let Some(src) = store.actor_model(tmpl, gender) else { return };
        let fps = src.anim.map(|a| a.fps).unwrap_or(15.0);
        // A combat clip (attack/death) overrides locomotion when present. Empty
        // clips (this data's Hit ranges are [0..0]) are skipped; `hold` pins the
        // clip's last frame for a static corpse pose (ANIM-8).
        let frame = match combat {
            Some((names, hold)) => store
                .actor_clip(tmpl, gender, names)
                .filter(|c| c.end > c.start)
                .map(|c| if hold { c.end as f32 } else { clip_frame(c, fps, elapsed + rid as f32 * 0.13) }),
            None => {
                let names: &[&str] = if running {
                    &["Run"]
                } else if moving {
                    &["Walk"]
                } else {
                    &["Idle", "Sit idle"]
                };
                store
                    .actor_clip(tmpl, gender, names)
                    .map(|c| clip_frame(c, fps, elapsed + rid as f32 * 0.13))
            }
        };
        let scale = store.actor_render_scale(tmpl, gender).unwrap_or(0.05);
        let tex = store.actor_textures_rc(tmpl, gender, face, body);
        // Joint positions at the CURRENT animation frame (bounds from bind pose).
        // Attachments parent to the *animated* joint so hair/weapon/shield track
        // the head/hands as the body moves, instead of floating at the rest pose
        // (the body itself can still take the GPU skinning path). One short bone
        // forward-pass per joint — negligible for the ~dozens of bones here.
        let (min, max) = src.bounds();
        let head = src.joint_pos_at("Head", frame).unwrap_or([0.0, 0.0, 0.0]);
        let hand = src.joint_pos_at("R_Hand", frame);
        let l_hand = src.joint_pos_at("L_Hand", frame);
        // Stand the actor on ITS OWN authoritative Y (from P_NewActor /
        // P_ChangeArea spawn; P_StandardUpdate carries only X/Z). The server's
        // actor Y is the COLLISION-PIVOT height, and the engine seats the body
        // CENTRED on that pivot, not feet-on-it: `Actors3D.bb` does
        // `PositionEntity body, 0, -(MaxY-MinY)/2, 0`. Matching that offset
        // (half the mesh height, scaled) stops actors floating ~half a body
        // above the terrain; the previous `- min*scale` put the feet at `pos[1]`,
        // lifting the whole body.
        // Seat the actor's feet on the sampled terrain height under its X/Z
        // (the engine keeps actors grounded via gravity+collision; the server's
        // actor Y is only a stale spawn height — `P_StandardUpdate` omits Y, so
        // it drifts on varying terrain). `th - min*scale` puts the mesh's feet
        // (model `min`) at the ground. Fall back to centring on the server Y when
        // no ground triangle is under the actor (e.g. mid-air / off-mesh).
        let half_h = (max[1] - min[1]) * 0.5;
        let ground = height.and_then(|h| h.height_at(pos[0], pos[2]));
        let trans_y = match ground {
            Some(th) => th - min[1] * scale,
            None => pos[1] - half_h * scale,
        };
        let trans = [pos[0], trans_y, pos[2]];
        if std::env::var("RCCE_SEATDIAG").is_ok() {
            eprintln!(
                "[seat] rid {rid} tmpl {tmpl} g{gender}: posY={:.2} ground={ground:?} min={:.1} max={:.1} scale={:.3} transY={:.2}",
                pos[1], min[1], max[1], scale, trans[1]
            );
        }
        let yaw_rad = yaw.to_radians();
        let key_body = format!("{tmpl}:{gender}:{face}:{body}");
        let can_skin = !src.bones.is_empty() && src.bones.len() <= rcce_render::gpu::MAX_BONES;
        if gpu_skin && can_skin {
            // GPU-skinned body: the static mesh is posed in the vertex shader.
            let m = glam::Mat4::from_translation(glam::Vec3::from(trans))
                * glam::Mat4::from_rotation_y(yaw_rad)
                * glam::Mat4::from_scale(glam::Vec3::splat(scale));
            skinned.push(SkinnedActor {
                key: key_body,
                model: src.clone(),
                textures: tex,
                frame,
                transform: m.to_cols_array(),
                color,
            });
        } else {
            // CPU-skinned body (default / fallback): pose on the CPU.
            let posed = Rc::new(B3dModel {
                meshes: src.posed_meshes(frame),
                textures: src.textures.clone(),
                tex_flags: src.tex_flags.clone(),
                brushes: src.brushes.clone(),
                bones: src.bones.clone(),
                anim: src.anim,
                ..Default::default()
            });
            let idx = models.len();
            models.push(posed);
            textures.push(tex);
            keys.push(key_body);
            place.push((idx, trans, [0.0, yaw_rad, 0.0], color, [scale, scale, scale]));
        }

        // Head attachments (hair, and beard for males) at the head joint.
        for att in store.actor_attachments(tmpl, gender, hair, beard) {
            let (t, r, s) = attachment_placement(trans, yaw_rad, scale, head, &att);
            let aidx = models.len();
            keys.push(format!("att:{tmpl}:{gender}:{}", att.mesh_id));
            models.push(att.model);
            textures.push(Rc::new(att.textures));
            place.push((aidx, t, r, color, s));
        }

        // Equipped weapon at the R_Hand joint (same mechanism). The override
        // forces a mesh for verification, since shipped items have no world
        // mesh (mmesh = 65535).
        let weapon_att = match weapon_override {
            Some(mesh) => store.gear_attachment_mesh(mesh),
            None if weapon_item != 0xFFFF => store.gear_attachment(weapon_item),
            None => None,
        };
        if let (Some(att), Some(hand)) = (weapon_att, hand) {
            let (t, r, s) = attachment_placement(trans, yaw_rad, scale, hand, &att);
            let widx = models.len();
            keys.push(format!("wpn:{}", att.mesh_id));
            models.push(att.model);
            textures.push(Rc::new(att.textures));
            place.push((widx, t, r, color, s));
        }

        // Equipped shield at the L_Hand joint (same mechanism). The override
        // also forces a mesh here for verification.
        let shield_att = match weapon_override {
            Some(mesh) => store.gear_attachment_mesh(mesh),
            None if shield_item != 0xFFFF => store.gear_attachment(shield_item),
            None => None,
        };
        if let (Some(att), Some(lh)) = (shield_att, l_hand) {
            let (t, r, s) = attachment_placement(trans, yaw_rad, scale, lh, &att);
            let sidx = models.len();
            keys.push(format!("shd:{}", att.mesh_id));
            models.push(att.model);
            textures.push(Rc::new(att.textures));
            place.push((sidx, t, r, color, s));
        }
    };

    // Debug override: force a weapon mesh on every actor (verifies the R_Hand
    // attach path; shipped items carry no world mesh).
    let weapon_override = std::env::var("RCCE_WEAPON_MESH").ok().and_then(|s| s.parse::<u16>().ok());
    // The local player's equipped weapon/shield are inventory slots 0/1.
    let me_weapon = world.me_inventory.get(&0).map(|it| it.item_id).unwrap_or(0xFFFF);
    let me_shield = world.me_inventory.get(&1).map(|it| it.item_id).unwrap_or(0xFFFF);
    // Animate the local player's own body from this frame's movement intent
    // (running > walking > idle), matching the remote-actor path below. The
    // Blitz client drives Me through the SAME locomotion machine as every other
    // actor (Client.bb UpdateActorInstances); passing false,false here was the
    // root cause of "the local player never walks/runs while moving" (ANIM-1).
    // Jump (ANIM-7) overrides the attack clip; the offset lifts the body (MOVE-7).
    // In first-person (CAM-4) the own body is hidden entirely. An idle fidget
    // (ANIM-6) overrides only when nothing more important is playing.
    let me_combat = if me_jumping {
        Some((JUMP_CLIP, false))
    } else if me_attack {
        Some((ATTACK_CLIP, false))
    } else {
        me_fidget.map(|f| (f, false))
    };
    if !hide_me {
        push(store, &mut models, &mut textures, &mut place, &mut keys, &mut skinned, me_template, world.me_gender, world.me_face_tex, world.me_body_tex, world.me_hair, world.me_beard, me_weapon, me_shield, weapon_override, world.my_runtime_id, me_moving, me_running, me_combat, [world.me_render_x, world.me_y + me_jump_offset, world.me_render_z], world.me_yaw, [0.85, 0.95, 0.85]);
    }
    for a in world.actors.values() {
        let dx = a.dest_x - a.x;
        let dz = a.dest_z - a.z;
        let moving = (dx * dx + dz * dz) > 1.0;
        let color = if a.is_player { [0.85, 0.9, 1.0] } else { [1.0, 1.0, 1.0] };
        // A dead actor holds its death pose (ANIM-8); a remote actor mid-jump
        // (P_Jump → world.jumps) plays the Jump clip + a sin-arc hop (ANIM-7); a
        // remote actor mid-attack (P_AttackActor → world.attack_anims) plays its
        // swing clip (CBT-3).
        let jump_left = world.jumps.get(&a.runtime_id).copied();
        // CBT-3: a remote actor mid-attack (P_AttackActor 'Y'/broadcast) plays its
        // swing clip. Priority: dead > jump > attack > none.
        let combat = if !a.alive {
            Some((death_clip(a.runtime_id), true))
        } else if jump_left.is_some() {
            Some((JUMP_CLIP, false))
        } else if world.attack_anims.contains_key(&a.runtime_id) {
            Some((ATTACK_CLIP, false))
        } else {
            None
        };
        let y_off = jump_left
            .map(|t| {
                let phase = 1.0 - (t / rcce_client::world::JUMP_ANIM_SECS).clamp(0.0, 1.0);
                (phase * std::f32::consts::PI).sin() * JUMP_REMOTE_APEX
            })
            .unwrap_or(0.0);
        push(store, &mut models, &mut textures, &mut place, &mut keys, &mut skinned, a.template_id, a.gender, a.face_tex, a.body_tex, a.hair, a.beard, a.equipped[0], a.equipped[1], weapon_override, a.runtime_id, moving, a.is_running, combat, [a.render_x, a.y + y_off, a.render_z], a.render_yaw, color);
    }
    (models, textures, place, keys, skinned)
}

/// Cheap fingerprint of everything that affects the actor drawables: a ~12 Hz
/// animation tick plus each actor's quantised position/yaw/run state. When it's
/// unchanged the dynamic geometry is reused (no re-skin/re-upload).
fn dyn_hash(world: &World, elapsed: f32, me_moving: bool, me_running: bool, me_attack: bool) -> u64 {
    use std::hash::{Hash, Hasher};
    let mut h = std::collections::hash_map::DefaultHasher::new();
    ((elapsed * 12.0) as u64).hash(&mut h);
    world.my_runtime_id.hash(&mut h);
    // Key on the SMOOTHED render position at fine granularity so the CPU-skin
    // rebuild tracks the per-frame interpolation (smooth movement) while moving,
    // and stabilises (throttled to the elapsed term) once converged/idle.
    ((world.me_render_x * 16.0) as i32).hash(&mut h);
    ((world.me_render_z * 16.0) as i32).hash(&mut h);
    (world.me_yaw as i32).hash(&mut h);
    // Include the local player's locomotion + attack state so the CPU-throttled
    // rebuild picks up walk/run/idle/attack transitions promptly (ANIM-1/ANIM-8).
    me_moving.hash(&mut h);
    me_running.hash(&mut h);
    me_attack.hash(&mut h);
    let mut rids: Vec<u16> = world.actors.keys().copied().collect();
    rids.sort_unstable();
    for rid in rids {
        let a = &world.actors[&rid];
        rid.hash(&mut h);
        ((a.render_x * 16.0) as i32).hash(&mut h);
        ((a.render_z * 16.0) as i32).hash(&mut h);
        (a.render_yaw as i32).hash(&mut h);
        a.is_running.hash(&mut h);
        a.alive.hash(&mut h); // death pose (ANIM-8)
        // Remote jump phase (ANIM-7) — quantised so the hop animates under the
        // CPU throttle while the timer runs.
        if let Some(t) = world.jumps.get(&rid) {
            ((t * 30.0) as i32).hash(&mut h);
        }
        // Remote attack-swing presence (CBT-3) so the swing shows under the throttle.
        world.attack_anims.contains_key(&rid).hash(&mut h);
    }
    // Dropped loot (DROP-1): hash the set of item handles + ids so the 3D ground
    // meshes rebuild when loot is dropped or picked up (positions are fixed at drop).
    let mut loot: Vec<u32> = world.dropped_items.keys().copied().collect();
    loot.sort_unstable();
    for hh in loot {
        hh.hash(&mut h);
        world.dropped_items[&hh].item_id.hash(&mut h);
    }
    h.finish()
}

/// Third-person camera collision. Marches the boom outward from the pivot
/// `look` along the unit direction `dir` and returns the furthest distance
/// (≤ `max_dist`) the camera can sit without its eye entering a solid occluder
/// sphere. Handles two cases the reference client does:
///   * boom passing *through* a building from outside → stop just before the wall;
///   * pivot *inside* a building (player indoors) → collapse to the minimum so
///     the camera sits close behind the player instead of deep in the geometry.
fn camera_boom(look: [f32; 3], dir: [f32; 3], max_dist: f32, occ: &[([f32; 3], f32)]) -> f32 {
    const MIN: f32 = 2.5;
    let inside = |t: f32| {
        let p = [look[0] + dir[0] * t, look[1] + dir[1] * t, look[2] + dir[2] * t];
        occ.iter().any(|&(c, r)| {
            let d2 = (p[0] - c[0]).powi(2) + (p[1] - c[1]).powi(2) + (p[2] - c[2]).powi(2);
            d2 < r * r
        })
    };
    let step = 0.4;
    let mut t = MIN;
    while t < max_dist {
        let next = (t + step).min(max_dist);
        if inside(next) {
            return t; // the next step would enter an occluder — stop here
        }
        t = next;
    }
    max_dist
}

/// Locate the project `data/` directory so the bin/-placed exe finds its
/// assets like the Blitz client does. Priority: `RCCE_DATA` env → a `data/`
/// next to or above the current dir → a `data/` next to or above the exe
/// (so `bin/ClientRS.exe` resolves `bin/../data`). Falls back to `"data"`.
fn resolve_data_root() -> String {
    if let Ok(p) = std::env::var("RCCE_DATA") {
        if !p.is_empty() {
            return p;
        }
    }
    let mut roots: Vec<std::path::PathBuf> = Vec::new();
    if let Ok(cwd) = std::env::current_dir() {
        roots.push(cwd.clone());
        roots.push(cwd.join(".."));
    }
    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            roots.push(dir.to_path_buf()); // e.g. bin/
            roots.push(dir.join("..")); // bin/.. = project root
            if let Some(up) = dir.parent() {
                roots.push(up.join("..")); // target/release/.. ladders
            }
        }
    }
    for r in &roots {
        let cand = r.join("data");
        if cand.is_dir() {
            return cand.to_string_lossy().into_owned();
        }
    }
    "data".to_string()
}

/// Load the GUI .bmp textures the HUD draws (function-button icons, the XP bar,
/// the empty action-bar slot) and register them with the overlay under stable
/// `gui:<Name>` keys. Missing files are skipped — the HUD falls back to text
/// labels / coloured rects when a key isn't registered.
fn register_gui_textures(overlay: &mut rcce_render::Overlay, device: &wgpu::Device, queue: &wgpu::Queue, data_root: &str) {
    let gui = std::path::Path::new(data_root).join("Textures").join("GUI");
    let files = [
        // Function-button icons (bottom-right cluster).
        ("gui:Chat", "Chat.bmp"),
        ("gui:Map", "Map.bmp"),
        ("gui:Inventory", "Inventory.bmp"),
        ("gui:Abilities", "Abilities.bmp"),
        ("gui:Character", "Character.bmp"),
        ("gui:Quests", "Quests.bmp"),
        ("gui:Party", "Party.bmp"),
        ("gui:Menu", "Menu.bmp"),
        ("gui:EmptySlot", "EmptySlot.bmp"),
        ("gui:XP", "Action Bar XP.bmp"),
        // Window background skins (textured leather/parchment) — replace the flat
        // dark panels so the HUD windows match Blitz's skinned look.
        ("gui:InventoryBG", "InventoryBG.png"),
        ("gui:CharBG", "CharBG.png"),
        ("gui:MenuBG", "MenuBG.png"),
        ("gui:QuestLogBG", "QuestLogBG.png"),
        ("gui:PartyBG", "PartyBG.png"),
        ("gui:AbilitiesBG", "AbilitiesBG.png"),
        ("gui:HelpBG", "HelpBG.png"),
        ("gui:ToolTip", "ToolTip.png"),
        // Action-bar frame + coin + equipment-slot placeholder icons.
        ("gui:ActionBar", "Action Bar.bmp"),
        ("gui:Coin", "Coin.bmp"),
        ("gui:slot:Hat", "Hat.bmp"),
        ("gui:slot:Amulet", "Amulet.bmp"),
        ("gui:slot:Chest", "Chest.bmp"),
        ("gui:slot:Hand", "Hand.bmp"),
        ("gui:slot:Ring", "Ring.bmp"),
        ("gui:slot:Belt", "Belt.bmp"),
        ("gui:slot:Legs", "Legs.bmp"),
        ("gui:slot:Feet", "Feet.bmp"),
        ("gui:slot:Shield", "Shield.bmp"),
        ("gui:slot:Backpack", "Backpack.bmp"),
    ];
    let mut ok = 0;
    for (key, name) in files {
        if let Some(img) = rcce_data::texture::load(&gui.join(name)) {
            overlay.register_texture(device, queue, key, img.width, img.height, &img.rgba);
            ok += 1;
        }
    }
    println!("[client-window] registered {ok}/{} GUI textures from {}", files.len(), gui.display());
}

#[allow(clippy::type_complexity)]
/// A live emitter + its resolved billboard texture, simulated per frame.
type ZoneEmitter = (rcce_client::particles::Emitter, Option<rcce_data::Image>);

type ZoneStatic = (
    [f32; 3],
    f32,
    f32,
    rcce_data::AreaEnv,
    Vec<([f32; 3], f32)>,
    rcce_client::terrain::HeightField,
    Vec<(rcce_data::WaterPlane, rcce_data::Image)>,
    Vec<ZoneEmitter>,
);

/// A flat water surface as a one-quad [`B3dModel`] (GUE water tool). The quad is
/// a unit plane in XZ centred at origin (scaled to the plane size by the
/// instance); UVs tile the water texture `tex_scale` times per world unit
/// (Blitz `ScaleTexture`, ClientAreas.bb:563); the per-vertex colour carries the
/// tint RGB + opacity, so `opacity < 1` routes it through the alpha-blend pass
/// (`mesh_is_alpha_overlay`) and the shader tints the texture by the RGB.
fn water_quad(w: &rcce_data::WaterPlane, scroll: [f32; 2]) -> B3dModel {
    // Blitz builds the plane with UVs 0..ScaleX (one tile/unit) then
    // `ScaleTexture(TexScale)`, which enlarges the texture → DIVIDES the tiling.
    // So the texture repeats `ScaleX / TexScale` times across the plane.
    let ts = if w.tex_scale.abs() > 1e-4 { w.tex_scale } else { 1.0 };
    let (tu, tv) = (w.scale_x / ts, w.scale_z / ts);
    // No RGB tint: Blitz never applies EntityColor to the water entity (the
    // area-file R/G/B is editor-only, like ServerWater) — it shows the texture at
    // full colour with alpha = opacity. White vertex colour + opacity alpha.
    let col = [1.0, 1.0, 1.0, w.opacity];
    B3dModel {
        meshes: vec![rcce_data::B3dMesh {
            positions: vec![[-0.5, 0.0, -0.5], [0.5, 0.0, -0.5], [0.5, 0.0, 0.5], [-0.5, 0.0, 0.5]],
            normals: vec![[0.0, 1.0, 0.0]; 4],
            uvs: vec![[0.0, 0.0], [tu, 0.0], [tu, tv], [0.0, tv]],
            uvs2: Vec::new(),
            colors: vec![col; 4],
            indices: vec![0, 1, 2, 0, 2, 3],
            brush_id: -1,
            texture: None,
            texture_flag: 0,
            uv_scale: [1.0, 1.0],
            // Scrolling offset (Blitz PositionTexture(U, V)) — animates the surface.
            uv_offset: scroll,
            lightmap: None,
        }],
        ..Default::default()
    }
}

/// A tall box for shadow-caster verification (`RCCE_TESTBOX`). When `skinned`, it
/// carries a single identity bone weighted to every vertex; with `frame = None`
/// the skin matrices are identity, so the GPU-skin path renders it at `model·pos`
/// — identical world geometry to the unskinned box. That gives a clean A/B of the
/// GPU-skinned shadow caster against the known-good CPU/static caster.
fn test_box_model(skinned: bool) -> B3dModel {
    let (hx, hz, hy) = (4.0f32, 4.0f32, 16.0f32); // ±4 in x/z, 0..16 in y (stands on the ground)
    let positions: Vec<[f32; 3]> = vec![
        [-hx, 0.0, -hz], [hx, 0.0, -hz], [hx, 0.0, hz], [-hx, 0.0, hz], // base
        [-hx, hy, -hz], [hx, hy, -hz], [hx, hy, hz], [-hx, hy, hz], // top
    ];
    let indices: Vec<u32> = vec![
        0, 1, 2, 0, 2, 3, // base
        4, 6, 5, 4, 7, 6, // top
        0, 4, 5, 0, 5, 1, // -z
        1, 5, 6, 1, 6, 2, // +x
        2, 6, 7, 2, 7, 3, // +z
        3, 7, 4, 3, 4, 0, // -x
    ];
    let mesh = rcce_data::B3dMesh {
        positions,
        normals: vec![[0.0, 1.0, 0.0]; 8], // crude; shadow casting ignores normals
        uvs: vec![[0.0, 0.0]; 8],
        uvs2: Vec::new(),
        colors: Vec::new(),
        indices,
        brush_id: -1,
        texture: None,
        texture_flag: 0,
        uv_scale: [1.0, 1.0],
        uv_offset: [0.0, 0.0],
        lightmap: None,
    };
    let mut m = B3dModel { meshes: vec![mesh], ..Default::default() };
    if skinned {
        m.bones = vec![rcce_data::B3dBone {
            name: "root".into(),
            parent: None,
            local_bind: glam::Mat4::IDENTITY.to_cols_array(),
            local_t: [0.0; 3],
            local_r: [1.0, 0.0, 0.0, 0.0],
            local_s: [1.0; 3],
            bind_world: glam::Mat4::IDENTITY.to_cols_array(),
            inverse_bind: glam::Mat4::IDENTITY.to_cols_array(),
            weights: (0..8u32).map(|v| (v, 1.0)).collect(),
            keys: Vec::new(),
        }];
    }
    m
}

/// Build a renderable grid mesh from a Blitz LOD [`TerrainPatch`] (`CreateTerrain`)
/// — `(N+1)²` vertices at local `(x, height, z)` for `x,z in 0..=N`, two triangles
/// per cell. The caller applies the patch's entity transform; the local grid spans
/// `[0,N]×[0,N]` (1 unit/cell, origin at the corner) to match `CreateTerrain`
/// before `PositionEntity`/`ScaleEntity`. UV tiles once per cell — the exact
/// tiling density is the lone cosmetic unknown until verified against a real
/// terrain (current rcce2 zones ship none). Winding is irrelevant (backface cull
/// is off).
/// Advance emitters by `dt` seconds and build their camera-facing billboard
/// batches `(texture, additive?, verts)`. A free function so the in-world render
/// can call it while holding `gfx`/`view` borrows of other `self` fields.
fn particle_batches(
    emitters: &mut [ZoneEmitter],
    eye: [f32; 3],
    target: [f32; 3],
    dt: f32,
) -> Vec<(Option<rcce_data::Image>, bool, Vec<rcce_render::gpu::Vertex>)> {
    let cross = |a: [f32; 3], b: [f32; 3]| [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
    let normd = |v: [f32; 3]| {
        let l = (v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).sqrt().max(1e-4);
        [v[0] / l, v[1] / l, v[2] / l]
    };
    let fwd = normd([target[0] - eye[0], target[1] - eye[1], target[2] - eye[2]]);
    let right = normd(cross(fwd, [0.0, 1.0, 0.0]));
    let up = cross(right, fwd);
    let delta = (dt * 60.0).clamp(0.0, 4.0); // Blitz authors the sim at ~60 fps
    // Softness vs Blitz: scale particle alpha (RCCE_PARTICLE_GAIN, default 0.6).
    let gain = std::env::var("RCCE_PARTICLE_GAIN").ok().and_then(|s| s.parse().ok()).unwrap_or(0.6_f32);
    let mut batches = Vec::with_capacity(emitters.len());
    for (e, tex) in emitters.iter_mut() {
        e.update(delta);
        let mut verts = Vec::new();
        e.billboards(right, up, gain, &mut verts);
        batches.push((tex.clone(), e.blend_add, verts));
    }
    batches
}

/// The water-tint colour if the camera `eye` is underwater — below a water
/// plane's surface Y and within its X/Z bounds (Blitz `CameraUnderwater`,
/// Client.bb:895-914). `None` when above water; the first containing plane wins.
/// The caller tints fog + a full-screen wash to this colour and clamps the view
/// distance, reproducing the murky submerged look (and hiding the sky).
fn underwater_color(water_planes: &[(rcce_data::WaterPlane, rcce_data::Image)], eye: [f32; 3]) -> Option<[f32; 3]> {
    water_planes.iter().find_map(|(w, _)| {
        let p = w.pos;
        let under =
            eye[1] < p[1] && (eye[0] - p[0]).abs() < w.scale_x * 0.5 && (eye[2] - p[2]).abs() < w.scale_z * 0.5;
        under.then_some(w.color)
    })
}

/// A soft radial glow sprite (white RGB, alpha falls off from the centre) used
/// for projectile orbs. Built once. The additive particle blend is
/// `dst += tex.rgb·col.rgb · (tex.a·col.a)`, so putting the falloff in the alpha
/// channel gives a round, soft, colour-tinted glow whose intensity scales with
/// the per-vertex `col.a` (used to fade the trail).
fn projectile_glow_image() -> rcce_data::Image {
    use std::sync::OnceLock;
    static GLOW: OnceLock<rcce_data::Image> = OnceLock::new();
    GLOW.get_or_init(|| {
        const N: usize = 64;
        let mut rgba = vec![0u8; N * N * 4];
        let c = (N as f32 - 1.0) * 0.5;
        for y in 0..N {
            for x in 0..N {
                let dx = (x as f32 - c) / c;
                let dy = (y as f32 - c) / c;
                let r = (dx * dx + dy * dy).sqrt();
                // Soft falloff: 1 at centre → 0 at the inscribed circle, squared
                // for a rounder core + softer halo.
                let a = (1.0 - r).clamp(0.0, 1.0);
                let a = a * a;
                let i = (y * N + x) * 4;
                rgba[i] = 255;
                rgba[i + 1] = 255;
                rgba[i + 2] = 255;
                rgba[i + 3] = (a * 255.0) as u8;
            }
        }
        rcce_data::Image { width: N as u32, height: N as u32, rgba }
    })
    .clone()
}

/// Emit camera-facing additive billboard quads for in-flight projectiles: a
/// bright warm core orb plus a short fading motion trail behind it (along the
/// reverse of its flight direction `target→pos`). Drawn through the particle
/// pipeline (additive, depth-tested, no depth-write) so projectiles glow and are
/// correctly occluded by terrain/scenery — unlike the old flat 2D overlay marker
/// that drew over everything. Approximates Blitz's 3D projectile mesh + trailing
/// emitter (Projectiles3D.bb) with a glow + trail.
fn projectile_billboards(
    projectiles: &[rcce_client::world::Projectile],
    eye: [f32; 3],
    target: [f32; 3],
    out: &mut Vec<rcce_render::gpu::Vertex>,
) {
    let cross = |a: [f32; 3], b: [f32; 3]| [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
    let normd = |v: [f32; 3]| {
        let l = (v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).sqrt().max(1e-4);
        [v[0] / l, v[1] / l, v[2] / l]
    };
    let fwd = normd([target[0] - eye[0], target[1] - eye[1], target[2] - eye[2]]);
    let right = normd(cross(fwd, [0.0, 1.0, 0.0]));
    let up = cross(right, fwd);
    let quad = |c: [f32; 3], s: f32, col: [f32; 4], out: &mut Vec<rcce_render::gpu::Vertex>| {
        let corner = |ox: f32, oy: f32, uv: [f32; 2]| rcce_render::gpu::Vertex {
            pos: [
                c[0] + right[0] * ox * s + up[0] * oy * s,
                c[1] + right[1] * ox * s + up[1] * oy * s,
                c[2] + right[2] * ox * s + up[2] * oy * s,
            ],
            normal: [0.0, 0.0, 1.0],
            uv,
            uv2: [0.0, 0.0],
            color: col,
        };
        let v00 = corner(-1.0, -1.0, [0.0, 1.0]);
        let v01 = corner(-1.0, 1.0, [0.0, 0.0]);
        let v11 = corner(1.0, 1.0, [1.0, 0.0]);
        let v10 = corner(1.0, -1.0, [1.0, 1.0]);
        out.extend_from_slice(&[v00, v01, v11, v00, v11, v10]);
    };
    const CORE: f32 = 0.95; // core orb half-size (world units)
    const TRAIL: usize = 6;
    const STEP: f32 = 0.5; // spacing between trail samples
    for p in projectiles {
        let dir = normd([p.tx - p.x, p.ty - p.y, p.tz - p.z]);
        // Trail behind the head, drawn first so the bright core composites on top.
        for i in 1..=TRAIL {
            let f = i as f32;
            let c = [p.x - dir[0] * STEP * f, p.y - dir[1] * STEP * f, p.z - dir[2] * STEP * f];
            let s = (CORE * (1.0 - 0.11 * f)).max(0.12);
            let a = 0.6 * (1.0 - f / (TRAIL as f32 + 1.0));
            quad(c, s, [1.0, 0.5, 0.16, a], out);
        }
        // Bright warm core orb at the head.
        quad([p.x, p.y, p.z], CORE, [1.0, 0.86, 0.42, 1.0], out);
    }
}

/// Parse a dynamic point light from a `LightModels` scenery mesh filename —
/// `light_<range>_<R>_<G>_<B>.b3d` (RGB 0..255), placed at the scenery's `pos`
/// (Blitz: `CreateLight(2)` + `LightColor` + `LightRange`, ClientAreas.bb:411).
/// `range_mul` scales the small authored range into world units; `gain` scales
/// brightness. Returns `None` for non-light scenery.
fn parse_light(filename: &str, pos: [f32; 3], range_mul: f32, gain: f32) -> Option<rcce_render::gpu::PointLight> {
    let base = filename.rsplit(['/', '\\']).next().unwrap_or(filename);
    let stem = base.strip_suffix(".b3d").or_else(|| base.strip_suffix(".B3D")).unwrap_or(base);
    let low = stem.to_lowercase();
    if !low.starts_with("light_") {
        return None;
    }
    let p: Vec<&str> = low.split('_').collect();
    if p.len() < 5 {
        return None;
    }
    let range: f32 = p[1].parse().ok()?;
    let (r, g, b): (f32, f32, f32) = (p[2].parse().ok()?, p[3].parse().ok()?, p[4].parse().ok()?);
    Some(rcce_render::gpu::PointLight {
        pos,
        range: range * range_mul,
        color: [r / 255.0 * gain, g / 255.0 * gain, b / 255.0 * gain],
    })
}

fn terrain_model(t: &rcce_data::TerrainPatch) -> B3dModel {
    let n = t.grid as usize;
    let stride = n + 1;
    let vcount = stride * stride;
    // 2nd UV set for the detail texture (Blitz ScaleTexture(DetailScale)): tiles
    // `detail_tex_scale` times across the whole terrain. Empty when there's no
    // detail texture (the shader's default grey lightmap is then a no-op).
    let has_detail = t.detail_tex_id != 65535 && t.detail_tex_scale > 0.0;
    let dtiles = t.detail_tex_scale / n.max(1) as f32;
    let mut positions = Vec::with_capacity(vcount);
    let mut uvs = Vec::with_capacity(vcount);
    let mut uvs2 = Vec::with_capacity(if has_detail { vcount } else { 0 });
    // Per-vertex normals from the height field (central differences), scaled into
    // world space by the cell size so the slopes — and thus the sun's form
    // shading — are correct, not flat.
    let mut normals = Vec::with_capacity(vcount);
    let (cx, cz) = (t.scale[0].abs().max(1e-3), t.scale[2].abs().max(1e-3));
    let hat = |xx: usize, zz: usize| t.heights.get(xx * stride + zz).copied().unwrap_or(0.0);
    for x in 0..=n {
        for z in 0..=n {
            let h = t.heights.get(x * stride + z).copied().unwrap_or(0.0);
            positions.push([x as f32, h, z as f32]);
            uvs.push([x as f32, z as f32]);
            if has_detail {
                uvs2.push([x as f32 * dtiles, z as f32 * dtiles]);
            }
            let (xl, xr) = (x.saturating_sub(1), (x + 1).min(n));
            let (zl, zr) = (z.saturating_sub(1), (z + 1).min(n));
            let dhx = (hat(xr, z) - hat(xl, z)) / (xr - xl).max(1) as f32;
            let dhz = (hat(x, zr) - hat(x, zl)) / (zr - zl).max(1) as f32;
            let nrm = glam::Vec3::new(-dhx / cx, 1.0, -dhz / cz).normalize();
            normals.push([nrm.x, nrm.y, nrm.z]);
        }
    }
    let mut indices = Vec::with_capacity(n * n * 6);
    for x in 0..n {
        for z in 0..n {
            let i = (x * stride + z) as u32;
            let ix = ((x + 1) * stride + z) as u32;
            let iz = (x * stride + z + 1) as u32;
            let ixz = ((x + 1) * stride + z + 1) as u32;
            indices.extend_from_slice(&[i, ix, ixz, i, ixz, iz]);
        }
    }
    B3dModel {
        meshes: vec![rcce_data::B3dMesh {
            normals,
            colors: vec![[1.0, 1.0, 1.0, 1.0]; vcount],
            positions,
            uvs,
            uvs2,
            indices,
            brush_id: -1,
            texture: None,
            texture_flag: 0,
            uv_scale: [1.0, 1.0],
            uv_offset: [0.0, 0.0],
            lightmap: None,
        }],
        ..Default::default()
    }
}

fn load_zone_static(store: &mut AssetStore, view: &mut WorldView, gfx: &Gfx, data_root: &str, zone: &str) -> Option<ZoneStatic> {
    let path = std::path::Path::new(data_root).join("Areas").join(format!("{zone}.dat"));
    let bytes = std::fs::read(&path).map_err(|e| eprintln!("[client-window] {}: {e}", path.display())).ok()?;
    let scenery = AreaScenery::parse(&bytes).ok()?;
    let mut models = Vec::new();
    let mut textures: Vec<Vec<Option<Image>>> = Vec::new();
    // Per-instance 2nd-texture (lightmap) slot, parallel to `textures`. Scenery
    // gets none today; LOD terrains get their detail texture here (multitexture
    // `base × detail × 2`, the same path as lightmaps).
    let mut lightmaps: Vec<Vec<Option<Image>>> = Vec::new();
    let mut dedup = std::collections::HashMap::new();
    let mut place = Vec::new();
    // Dynamic point lights placed via LightModels scenery (env-tunable scale).
    let mut lights: Vec<rcce_render::gpu::PointLight> = Vec::new();
    let lmul = std::env::var("RCCE_LIGHTRANGE").ok().and_then(|s| s.parse().ok()).unwrap_or(30.0_f32);
    let lgain = std::env::var("RCCE_LIGHTGAIN").ok().and_then(|s| s.parse().ok()).unwrap_or(1.0_f32);
    let (mut min, mut max) = ([f32::MAX; 3], [f32::MIN; 3]);
    for s in &scenery.sceneries {
        let key = format!("{}:{}", s.mesh_id, s.texture_id);
        let idx = match dedup.get(&key) {
            Some(&i) => i,
            None => {
                let Some(m) = store.mesh_model(s.mesh_id) else {
                    println!("[meshskip] mesh_id {} failed to load (scenery dropped)", s.mesh_id);
                    continue;
                };
                let tex = store.scenery_textures(s.mesh_id, s.texture_id);
                if std::env::var("RCCE_MESHDIAG").is_ok() {
                    let (mut umin, mut umax, mut vmin, mut vmax) =
                        (f32::MAX, f32::MIN, f32::MAX, f32::MIN);
                    let mut tris = 0usize;
                    for mesh in &m.meshes {
                        tris += mesh.indices.len() / 3;
                        for uv in &mesh.uvs {
                            umin = umin.min(uv[0]);
                            umax = umax.max(uv[0]);
                            vmin = vmin.min(uv[1]);
                            vmax = vmax.max(uv[1]);
                        }
                    }
                    let texs: Vec<String> = tex
                        .iter()
                        .map(|t| {
                            t.as_ref()
                                .map(|i| format!("{}x{}", i.width, i.height))
                                .unwrap_or_else(|| "none".into())
                        })
                        .collect();
                    let scales: Vec<String> = m
                        .meshes
                        .iter()
                        .map(|mm| format!("{:.1}x{:.1}", mm.uv_scale[0], mm.uv_scale[1]))
                        .collect();
                    // Per-surface alpha profile: %opaque (a>0.9) / %transparent (a<0.1).
                    let alpha: Vec<String> = m
                        .meshes
                        .iter()
                        .map(|mm| {
                            if mm.colors.is_empty() {
                                "noVC".into()
                            } else {
                                let n = mm.colors.len() as f32;
                                let op = mm.colors.iter().filter(|c| c[3] > 0.9).count() as f32 / n;
                                let tr = mm.colors.iter().filter(|c| c[3] < 0.1).count() as f32 / n;
                                format!("op{:.0}tr{:.0}", op * 100.0, tr * 100.0)
                            }
                        })
                        .collect();
                    eprintln!(
                        "[meshdiag] mesh {} tex {}: tris={tris} uv u[{umin:.1}..{umax:.1}] v[{vmin:.1}..{vmax:.1}] surfaces={} texs={texs:?} uv_scales={scales:?} alpha={alpha:?}",
                        s.mesh_id, s.texture_id, m.meshes.len()
                    );
                }
                let i = models.len();
                let nm = tex.len();
                models.push(m);
                textures.push(tex);
                lightmaps.push(vec![None; nm]); // scenery: no 2nd texture
                dedup.insert(key, i);
                i
            }
        };
        // A LightModels mesh placed as scenery is a dynamic point light.
        if let Some(light) = store.mesh_filename(s.mesh_id).and_then(|f| parse_light(f, s.pos, lmul, lgain)) {
            lights.push(light);
        }
        let rot = scenery_rot_radians(s.rot);
        if std::env::var_os("RCCE_SCENDIAG").is_some() {
            let fname = store.mesh_filename(s.mesh_id).unwrap_or("?").to_string();
            println!(
                "[scendiag] mesh={} '{fname}' pos=({:.1},{:.1},{:.1}) pyr=({:.1},{:.1},{:.1}) scale=({:.2},{:.2},{:.2})",
                s.mesh_id, s.pos[0], s.pos[1], s.pos[2], s.rot[0], s.rot[1], s.rot[2], s.scale[0], s.scale[1], s.scale[2]
            );
        }
        place.push((idx, s.pos, rot, s.scale));
        for k in 0..3 {
            min[k] = min[k].min(s.pos[k]);
            max[k] = max[k].max(s.pos[k]);
        }
    }
    // Water surfaces (GUE water tool): collect the plane params + texture so the
    // render loop can rebuild them per frame with a scrolling UV offset (animated
    // surface, Blitz PositionTexture). Not added to the static scene.
    let mut waters: Vec<(rcce_data::WaterPlane, rcce_data::Image)> = Vec::new();
    for w in &scenery.waters {
        if w.scale_x <= 0.0 || w.scale_z <= 0.0 {
            continue;
        }
        let Some(img) = store.texture_path(w.tex_id).and_then(|p| rcce_data::texture::load(&p)) else {
            continue;
        };
        waters.push((*w, img));
    }
    // Blitz LOD terrains (CreateTerrain): older forks (e.g. Mythic Realms 1.26)
    // build the ground from these instead of a scenery mesh. Render each as a grid
    // mesh at its entity transform (yaw negated like scenery); pushed into `place`
    // here so it also feeds the camera-occluder filter (skipped — too large) and
    // the height field (its near-horizontal tris seat actors) below.
    for t in &scenery.terrains {
        if t.grid == 0 || t.heights.is_empty() {
            continue;
        }
        let tex = store.texture_path(t.base_tex_id).and_then(|p| rcce_data::texture::load(&p));
        // Detail texture (Blitz stage-1 multitexture): blended as base × detail × 2.
        let detail = if t.detail_tex_id != 65535 {
            store.texture_path(t.detail_tex_id).and_then(|p| rcce_data::texture::load(&p))
        } else {
            None
        };
        let idx = models.len();
        models.push(std::rc::Rc::new(terrain_model(t)));
        textures.push(vec![tex]);
        lightmaps.push(vec![detail]);
        let rot = [t.rot[0].to_radians(), -t.rot[1].to_radians(), t.rot[2].to_radians()];
        place.push((idx, t.pos, rot, t.scale));
        // Expand the zone bounds (camera framing / centre) to the terrain footprint.
        let n = t.grid as f32;
        for [cx, cz] in [[0.0, 0.0], [n, 0.0], [0.0, n], [n, n]] {
            let wx = t.pos[0] + cx * t.scale[0];
            let wz = t.pos[2] + cz * t.scale[2];
            min[0] = min[0].min(wx);
            max[0] = max[0].max(wx);
            min[2] = min[2].min(wz);
            max[2] = max[2].max(wz);
        }
        min[1] = min[1].min(t.pos[1]);
        max[1] = max[1].max(t.pos[1]);
    }
    if !scenery.terrains.is_empty() {
        for t in &scenery.terrains {
            let (mn, mx) = t.heights.iter().fold((f32::MAX, f32::MIN), |(a, b), &h| (a.min(h), b.max(h)));
            println!(
                "[client-window] zone '{zone}': LOD terrain {}x{} heights [{mn:.1}..{mx:.1}] pos {:?} scale {:?}",
                t.grid, t.grid, t.pos, t.scale
            );
        }
    }
    let n_water = waters.len();
    if !scenery.waters.is_empty() {
        println!("[client-window] zone '{zone}': {} water plane(s), {n_water} drawn", scenery.waters.len());
        for w in &scenery.waters {
            println!(
                "  water @({:.0},{:.1},{:.0}) size {:.0}x{:.0} tex {} tscale {:.3} rgb({:.2},{:.2},{:.2}) a{:.2}",
                w.pos[0], w.pos[1], w.pos[2], w.scale_x, w.scale_z, w.tex_id, w.tex_scale, w.color[0], w.color[1], w.color[2], w.opacity
            );
        }
    }
    if place.is_empty() {
        return None;
    }
    let instances: Vec<SceneInstance> = place
        .iter()
        .map(|&(idx, pos, rot, scale)| SceneInstance {
            model: &models[idx],
            textures: &textures[idx],
            lightmaps: &lightmaps[idx],
            translation: pos,
            rot,
            scale,
            color: [1.0, 1.0, 1.0],
        })
        .collect();
    view.set_scene(&gfx.device, &gfx.queue, &instances, min[1]);
    view.set_lights(&lights);
    if !lights.is_empty() {
        println!("[client-window] zone '{zone}': {} dynamic point light(s)", lights.len());
    }
    // Real sky: resolve the area's SkyTexID through the texture catalog and
    // upload it for the textured skydome (else keep the gradient).
    let sky = scenery.env.sky_tex_id;
    if sky != 65535 {
        if let Some(img) = store.texture_path(sky).and_then(|p| rcce_data::texture::load(&p)) {
            view.set_sky_texture(&gfx.device, &gfx.queue, img.width, img.height, &img.rgba);
            println!("[client-window] zone '{zone}': sky texture {}x{} (id {sky})", img.width, img.height);
        } else {
            view.clear_sky_texture();
        }
    } else {
        view.clear_sky_texture();
    }
    // Cloud overlay (CloudTexID → drifting alpha-blended clouds).
    let cloud = scenery.env.cloud_tex_id;
    if cloud != 65535 {
        if let Some(img) = store.texture_path(cloud).and_then(|p| rcce_data::texture::load(&p)) {
            view.set_cloud_texture(&gfx.device, &gfx.queue, img.width, img.height, &img.rgba);
        } else {
            view.clear_cloud_texture();
        }
    } else {
        view.clear_cloud_texture();
    }
    // Night stars overlay (StarsTexID → additive stars, gated by night).
    let stars = scenery.env.stars_tex_id;
    if stars != 65535 {
        if let Some(img) = store.texture_path(stars).and_then(|p| rcce_data::texture::load(&p)) {
            view.set_stars_texture(&gfx.device, &gfx.queue, img.width, img.height, &img.rgba);
            println!("[client-window] zone '{zone}': stars texture {}x{} (id {stars})", img.width, img.height);
        } else {
            println!("[client-window] zone '{zone}': stars tex id {stars} FAILED to load -> no stars");
            view.clear_stars_texture();
        }
    } else {
        view.clear_stars_texture();
    }
    let center = [(min[0] + max[0]) * 0.5, (min[1] + max[1]) * 0.5, (min[2] + max[2]) * 0.5];
    let span = ((max[0] - min[0]).powi(2) + (max[2] - min[2]).powi(2)).sqrt().max(50.0);

    // Camera-collision occluders: a world bounding sphere per SOLID prop
    // (buildings, rocks, statues, barrels, fences…). Excluded: see-through
    // foliage (any masked sub-mesh — grass/trees), the huge terrain mesh
    // (radius > span*0.25), and tiny scatter (radius < 2). The third-person boom
    // shortens when it would pass through one of these, so the camera doesn't
    // end up inside a building.
    let mut occluders: Vec<([f32; 3], f32)> = Vec::new();
    for &(idx, pos, _rot, scale) in &place {
        let model = &models[idx];
        let foliage = model.meshes.iter().any(|m| m.texture_flag & 4 != 0);
        if foliage {
            continue;
        }
        let (lmin, lmax) = model.bounds();
        let smax = scale[0].abs().max(scale[1].abs()).max(scale[2].abs());
        let extent = [lmax[0] - lmin[0], lmax[1] - lmin[1], lmax[2] - lmin[2]];
        let radius = 0.5 * (extent[0] * extent[0] + extent[1] * extent[1] + extent[2] * extent[2]).sqrt() * smax;
        // Only building-sized occluders: large enough to be a structure the
        // camera can get stuck inside, but not the whole-zone terrain shell.
        // Small/medium props (barrels, fountains, lamp posts) are skipped so the
        // camera isn't yanked close every time the player walks past one.
        if radius < 8.0 || radius > span * 0.25 {
            continue;
        }
        // The bounding-sphere of a boxy building overshoots its footprint at the
        // corners; shrink it so "camera inside" only fires when the player is
        // genuinely within the walls, not merely standing beside the building.
        let radius = radius * 0.62;
        let c = [
            pos[0] + (lmin[0] + lmax[0]) * 0.5 * scale[0],
            pos[1] + (lmin[1] + lmax[1]) * 0.5 * scale[1],
            pos[2] + (lmin[2] + lmax[2]) * 0.5 * scale[2],
        ];
        occluders.push((c, radius));
    }

    // Ground height field for actor foot-seating (`terrain.rs`). Gather the
    // world-space near-horizontal triangles of the walkable scenery (terrain +
    // paths); masked foliage (grass/trees) is skipped. Actors sample this so
    // they stand on the terrain instead of on a stale spawn Y.
    let height_field = {
        use glam::{Mat3, Mat4, Vec3};
        let mut tris: Vec<[Vec3; 3]> = Vec::new();
        for &(idx, pos, rot, scale) in &place {
            let model = &models[idx];
            let nrot = Mat3::from_mat4(
                Mat4::from_rotation_y(rot[1]) * Mat4::from_rotation_x(rot[0]) * Mat4::from_rotation_z(rot[2]),
            );
            let (sv, tv) = (Vec3::from(scale), Vec3::from(pos));
            for mesh in &model.meshes {
                if mesh.texture_flag & 4 != 0 {
                    continue; // masked = see-through foliage, not ground
                }
                let w = |i: u32| tv + nrot * (Vec3::from(mesh.positions[i as usize]) * sv);
                for tri in mesh.indices.chunks_exact(3) {
                    let (a, b, c) = (w(tri[0]), w(tri[1]), w(tri[2]));
                    if rcce_client::terrain::HeightField::is_ground(a, b, c) {
                        tris.push([a, b, c]);
                    }
                }
            }
        }
        rcce_client::terrain::HeightField::build(tris, 8.0)
    };

    // Particle emitters: load each placement's .rpc config + billboard texture and
    // build a live emitter (simulated per frame in `tick_particles`).
    let mut emitters: Vec<ZoneEmitter> = Vec::new();
    for (ei, em) in scenery.emitters.iter().enumerate() {
        let cfg_path = std::path::Path::new(data_root).join("Emitter Configs").join(format!("{}.rpc", em.config_name));
        let Ok(bytes) = std::fs::read(&cfg_path) else { continue };
        let Ok(config) = rcce_data::EmitterConfig::parse(&bytes) else { continue };
        let tex = store.texture_path(em.tex_id).and_then(|p| rcce_data::texture::load(&p));
        let seed = 0x9E3779B97F4A7C15u64.wrapping_mul(ei as u64 + 1) ^ (em.pos[0].to_bits() as u64);
        emitters.push((rcce_client::particles::Emitter::new(config, em.tex_id, em.pos, em.rot, seed), tex));
    }
    if !emitters.is_empty() {
        println!("[client-window] zone '{zone}': {} particle emitter(s)", emitters.len());
        if std::env::var_os("RCCE_SCENDIAG").is_some() {
            for em in &scenery.emitters {
                println!("  emitter '{}' @({:.0},{:.0},{:.0}) tex {}", em.config_name, em.pos[0], em.pos[1], em.pos[2], em.tex_id);
            }
        }
    }

    println!(
        "[client-window] zone '{zone}': {} objects, {} meshes, span {span:.0}, {} cam occluders, ground {}",
        place.len(), models.len(), occluders.len(), if height_field.is_empty() { "none" } else { "ok" }
    );
    Some((center, span, min[1], scenery.env.clone(), occluders, height_field, waters, emitters))
}

/// Result of loading a zone: camera framing + env + the decoded cloud textures
/// (regular + storm) for the storm-cloud swap.
struct ZoneLoad {
    center: [f32; 3],
    span: f32,
    ground_y: f32,
    env: rcce_data::AreaEnv,
    cloud_regular: Option<rcce_data::texture::Image>,
    cloud_storm: Option<rcce_data::texture::Image>,
    occluders: Vec<([f32; 3], f32)>,
    height_field: rcce_client::terrain::HeightField,
    waters: Vec<(rcce_data::WaterPlane, rcce_data::Image)>,
    emitters: Vec<ZoneEmitter>,
}

/// Load a zone's scenery + sky/cloud/stars (via `load_zone_static`) and decode
/// its cloud textures. The single primitive used by both the initial load and a
/// live area-change reload.
fn load_zone_full(store: &mut AssetStore, view: &mut WorldView, gfx: &Gfx, data_root: &str, zone: &str) -> Option<ZoneLoad> {
    let (center, span, ground_y, env, occluders, height_field, waters, emitters) =
        load_zone_static(store, view, gfx, data_root, zone)?;
    let load_img = |id: u16| -> Option<rcce_data::texture::Image> {
        (id != 65535).then(|| store.texture_path(id).and_then(|p| rcce_data::texture::load(&p))).flatten()
    };
    let cloud_regular = load_img(env.cloud_tex_id);
    let cloud_storm = load_img(env.storm_cloud_tex_id);
    Some(ZoneLoad { center, span, ground_y, env, cloud_regular, cloud_storm, occluders, height_field, waters, emitters })
}

impl ApplicationHandler for App {
    fn resumed(&mut self, event_loop: &ActiveEventLoop) {
        if self.window.is_some() {
            return;
        }
        let attrs = Window::default_attributes()
            .with_title("RCCE2 — Rust client")
            .with_inner_size(winit::dpi::LogicalSize::new(1280, 800));
        let window = Arc::new(event_loop.create_window(attrs).expect("window"));
        let (gfx, format) = Gfx::new(window.clone());
        let mut view = WorldView::new(&gfx.device, format, gfx.config.width, gfx.config.height);

        let data_root = resolve_data_root();
        println!("[client-window] data root: {data_root}");
        self.data_root = data_root.clone();
        let mut store = match AssetStore::load(&data_root) {
            Ok(s) => s,
            Err(e) => {
                eprintln!("[client-window] assets: {e}");
                event_loop.exit();
                return;
            }
        };

        // Static scenery (always — also the fallback view).
        if let Some(z) = load_zone_full(&mut store, &mut view, &gfx, &data_root, &self.zone) {
            self.center = z.center;
            self.span = z.span;
            self.ground_y = z.ground_y;
            self.height_field = Some(z.height_field);
            self.water_planes = z.waters;
            self.emitters = z.emitters;
            self.cam_occluders = z.occluders;
            self.fog_color = z.env.fog_color;
            self.fog_near = z.env.fog_near;
            self.fog_far = z.env.fog_far;
            self.ambient = z.env.ambient;
            self.light_dir = z.env.light_dir;
            self.cloud_regular_img = z.cloud_regular;
            self.cloud_storm_img = z.cloud_storm;
            self.cloud_is_storm = false;
            if let Some(audio) = self.audio.as_mut() {
                audio.set_music(z.env.music_id, 0.4, |id| store.music_path(id));
            }
            self.loaded_zone = self.zone.clone();
        }
        // Resolve footstep sounds once (played as one-shots while moving).
        self.footstep_paths = store.footstep_sounds();
        // Playable races for the character-create screen.
        self.playable = store.playable_templates();

        // RCCE_AUTOLOGIN=1 keeps the old straight-to-world path. The headless
        // world harnesses (RCCE_BENCH / RCCE_AUTOWALK) are meaningless at the
        // login screen, so they imply auto-login too. Otherwise the window boots
        // into the interactive login screen; the menu connection opens on submit.
        let auto_login = std::env::var_os("RCCE_AUTOLOGIN").is_some()
            || std::env::var_os("RCCE_BENCH").is_some()
            || std::env::var_os("RCCE_AUTOWALK").is_some();
        if auto_login {
            println!("[client-window] auto-login to {}:{} ...", self.host, self.port);
            let mut transport = EnetTransport::new();
            let creds = Credentials {
                username: self.login_user.clone(),
                password: self.login_pass.clone(),
                email: "rust@bot.com".to_string(),
            };
            match login(&mut transport, &self.host, self.port, &creds) {
                Ok(outcome) => {
                    self.enter_outcome(transport, outcome, &store);
                }
                Err(e) => eprintln!("[client-window] auto-login failed ({e}); zone-only spectator view"),
            }
        } else {
            // MENU-13: show the EULA gate first when the project ships a non-empty
            // EULA.txt, otherwise straight to login (the engine's behavior).
            self.eula_text = store.eula_text();
            self.mode = initial_menu_mode(self.eula_text.is_some());
            self.login_msg = "Type your account name + password, then Enter".to_string();
            println!("[client-window] login screen (server {}:{})", self.host, self.port);
            // Headless test hook: jump straight to character select (drives the
            // real account-login against the server) so the screen is screenshot-
            // verifiable. Pre-fills from RCCE_USER like the live "Enter" press.
            if std::env::var_os("RCCE_AUTOSUBMIT").is_some() {
                // Synchronous here so the headless screenshot harness still has a
                // populated char-select by first capture (timing unchanged).
                self.submit_login_blocking();
            }
        }

        let mut overlay = rcce_render::Overlay::new(&gfx.device, format);
        register_gui_textures(&mut overlay, &gfx.device, &gfx.queue, &data_root);
        self.overlay = Some(overlay);
        // CBT-5: damage display style from Combat.dat (3=floaters, 2=chat lines),
        // with an RCCE_DMGSTYLE override for headless testing.
        self.damage_info_style = std::env::var("RCCE_DMGSTYLE")
            .ok()
            .and_then(|s| s.parse::<u8>().ok())
            .unwrap_or_else(|| store.damage_info_style());
        // MENU-12: pre-fill the account field from Last Username.dat unless
        // RCCE_USER pinned it (env override wins, so headless paths are unaffected).
        if std::env::var_os("RCCE_USER").is_none() {
            if let Some(name) = store.last_username() {
                self.login_user = name;
            }
        }
        self.store = Some(store);
        self.gfx = Some(gfx);
        self.view = Some(view);
        window.request_redraw();
        self.window = Some(window);
    }

    fn window_event(&mut self, event_loop: &ActiveEventLoop, _id: WindowId, event: WindowEvent) {
        match event {
            WindowEvent::CloseRequested => {
                // Disconnect BOTH the in-world and any menu connection so the
                // server clears the account session. Explicit — do not rely on
                // Drop, which winit's event_loop.exit() may skip on Windows.
                self.shutdown_net();
                event_loop.exit();
            }
            WindowEvent::Resized(size) => {
                if let Some(gfx) = self.gfx.as_mut() {
                    gfx.resize(size.width, size.height);
                }
                if let (Some(view), Some(gfx)) = (self.view.as_mut(), self.gfx.as_ref()) {
                    view.resize(&gfx.device, size.width, size.height);
                }
            }
            WindowEvent::KeyboardInput { event, .. } => {
                let pressed = event.state == ElementState::Pressed;
                // Login / character-select screens consume all keys.
                if self.mode != Mode::InWorld {
                    if pressed {
                        if let PhysicalKey::Code(code) = event.physical_key {
                            self.menu_key(event_loop, code, event.text.as_deref());
                            if let Some(w) = self.window.as_ref() {
                                w.request_redraw();
                            }
                        }
                    }
                    return;
                }
                // Quantity prompt modal: arrows/±/PageUp-Down/Home/End/digits
                // adjust, Enter confirms, Esc cancels. Consumes all keys.
                if self.qty_prompt.is_some() {
                    if pressed {
                        let max = self.qty_prompt.as_ref().map(|p| p.max).unwrap_or(1);
                        match event.physical_key {
                            PhysicalKey::Code(KeyCode::Enter | KeyCode::NumpadEnter) => self.confirm_qty(),
                            PhysicalKey::Code(KeyCode::Escape) => self.qty_prompt = None,
                            PhysicalKey::Code(k) => {
                                if let Some(p) = self.qty_prompt.as_mut() {
                                    let cur = p.qty as i64;
                                    let nq = match k {
                                        KeyCode::ArrowLeft | KeyCode::ArrowDown | KeyCode::Minus | KeyCode::NumpadSubtract => cur - 1,
                                        KeyCode::ArrowRight | KeyCode::ArrowUp | KeyCode::Equal | KeyCode::NumpadAdd => cur + 1,
                                        KeyCode::PageDown => cur - 10,
                                        KeyCode::PageUp => cur + 10,
                                        KeyCode::Home => 1,
                                        KeyCode::End => max as i64,
                                        KeyCode::Backspace => cur / 10,
                                        _ => match event.text.as_ref().and_then(|t| t.chars().next()).and_then(|c| c.to_digit(10)) {
                                            Some(d) => cur * 10 + d as i64,
                                            None => cur,
                                        },
                                    };
                                    p.qty = clamp_qty(nq, max);
                                }
                            }
                            _ => {}
                        }
                    }
                    return;
                }
                // Chat typing mode: capture text, Enter sends, Esc cancels.
                if self.chat_input.is_some() {
                    if pressed {
                        match event.physical_key {
                            PhysicalKey::Code(KeyCode::Enter | KeyCode::NumpadEnter) => {
                                let msg = self.chat_input.take().unwrap_or_default();
                                if !msg.is_empty() {
                                    if let Some(net) = self.net.as_mut() {
                                        net.transport.send(
                                            net.peer,
                                            rcce_net::packet_id::CHAT_MESSAGE,
                                            msg.as_bytes(),
                                            true,
                                        );
                                    }
                                }
                            }
                            PhysicalKey::Code(KeyCode::Escape) => self.chat_input = None,
                            PhysicalKey::Code(KeyCode::Backspace) => {
                                if let Some(b) = self.chat_input.as_mut() {
                                    b.pop();
                                }
                            }
                            _ => {
                                if let (Some(t), Some(b)) =
                                    (event.text.as_ref(), self.chat_input.as_mut())
                                {
                                    for c in t.chars() {
                                        if !c.is_control() && b.chars().count() < 100 {
                                            b.push(c);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    return;
                }
                // Scripted text-input dialog (TGT-8): capture typing, Enter
                // submits the reply, Esc cancels without replying.
                if self.net.as_ref().map(|n| n.world.script_input.is_some()).unwrap_or(false) {
                    if pressed {
                        match event.physical_key {
                            PhysicalKey::Code(KeyCode::Enter | KeyCode::NumpadEnter) => {
                                if let Some(net) = self.net.as_mut() {
                                    if let Some(si) = net.world.script_input.take() {
                                        net.transport.send(
                                            net.peer,
                                            rcce_net::packet_id::SCRIPT_INPUT,
                                            &rcce_client::net::script_input_reply(
                                                si.script_handle,
                                                &si.text,
                                            ),
                                            true,
                                        );
                                    }
                                }
                            }
                            PhysicalKey::Code(KeyCode::Escape) => {
                                if let Some(net) = self.net.as_mut() {
                                    net.world.script_input = None;
                                }
                            }
                            PhysicalKey::Code(KeyCode::Backspace) => {
                                if let Some(net) = self.net.as_mut() {
                                    if let Some(si) = net.world.script_input.as_mut() {
                                        si.text.pop();
                                    }
                                }
                            }
                            _ => {
                                if let Some(t) = event.text.as_ref() {
                                    if let Some(net) = self.net.as_mut() {
                                        if let Some(si) = net.world.script_input.as_mut() {
                                            for c in t.chars() {
                                                if !c.is_control() && si.text.chars().count() < 100 {
                                                    si.text.push(c);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    return;
                }
                if let PhysicalKey::Code(code) = event.physical_key {
                    match code {
                        // Open the chat line.
                        KeyCode::Enter | KeyCode::KeyT if pressed => {
                            self.chat_input = Some(String::new());
                            self.keys_wasd = [false; 4]; // stop moving while typing
                        }
                        // Attack the nearest living actor.
                        KeyCode::KeyF | KeyCode::Space if pressed => {
                            if let Some(net) = self.net.as_mut() {
                                let (mx, mz) = (net.world.me_x, net.world.me_z);
                                let target = net
                                    .world
                                    .actors
                                    .values()
                                    .filter(|a| a.alive)
                                    .min_by(|a, b| {
                                        let da = (a.x - mx).powi(2) + (a.z - mz).powi(2);
                                        let db = (b.x - mx).powi(2) + (b.z - mz).powi(2);
                                        da.total_cmp(&db)
                                    })
                                    .map(|a| a.runtime_id);
                                if let Some(rid) = target {
                                    // Engage auto-attack; the per-frame combat
                                    // loop chases + swings on cooldown (CBT-1).
                                    self.target = Some(rid);
                                    self.attacking = true;
                                }
                            }
                        }
                        // Pick up the nearest dropped item within range.
                        KeyCode::KeyE if pressed => {
                            let nearest = self.net.as_ref().and_then(|net| {
                                let (mx, mz) = (net.world.me_x, net.world.me_z);
                                net.world
                                    .dropped_items
                                    .values()
                                    .map(|d| (d.handle, (d.x - mx).powi(2) + (d.z - mz).powi(2)))
                                    .filter(|(_, d2)| *d2 < 60.0 * 60.0)
                                    .min_by(|a, b| a.1.total_cmp(&b.1))
                                    .map(|(h, _)| h)
                            });
                            if let Some(h) = nearest {
                                self.pickup_item(h);
                            }
                        }
                        // Action bar: cast the Nth memorised spell (1-9).
                        KeyCode::Digit1
                        | KeyCode::Digit2
                        | KeyCode::Digit3
                        | KeyCode::Digit4
                        | KeyCode::Digit5
                        | KeyCode::Digit6
                        | KeyCode::Digit7
                        | KeyCode::Digit8
                        | KeyCode::Digit9
                            if pressed =>
                        {
                            let idx = match code {
                                KeyCode::Digit1 => 0,
                                KeyCode::Digit2 => 1,
                                KeyCode::Digit3 => 2,
                                KeyCode::Digit4 => 3,
                                KeyCode::Digit5 => 4,
                                KeyCode::Digit6 => 5,
                                KeyCode::Digit7 => 6,
                                KeyCode::Digit8 => 7,
                                _ => 8,
                            };
                            // Inventory panel open: number keys act on the Nth
                            // item — Shift = equip (move to its gear slot), plain
                            // = drop one.
                            if self.show_inventory {
                                let item = self
                                    .net
                                    .as_ref()
                                    .and_then(|n| n.world.me_inventory.values().filter(|it| it.slot >= 14).nth(idx))
                                    .map(|it| (it.slot, it.item_id));
                                if let Some((slot, item_id)) = item {
                                    if self.run {
                                        // Equip: swap into the matching gear slot.
                                        let dest = self.store.as_ref().and_then(|s| s.item_equip_slot(item_id));
                                        if let (Some(dest), Some(net)) = (dest, self.net.as_mut()) {
                                            let rid = net.world.my_runtime_id;
                                            net.transport.send(
                                                net.peer,
                                                rcce_net::packet_id::INVENTORY_UPDATE,
                                                &rcce_client::net::inv_move_packet(rid, slot, dest, 0, true),
                                                true,
                                            );
                                        }
                                    } else if let Some(net) = self.net.as_mut() {
                                        net.transport.send(
                                            net.peer,
                                            rcce_net::packet_id::INVENTORY_UPDATE,
                                            &rcce_client::net::inv_drop_packet(slot, 1),
                                            true,
                                        );
                                    }
                                }
                                return;
                            }
                            // If a vendor window is open, the number keys STAGE the
                            // Nth offer for purchase (toggle); the Confirm button
                            // sends the whole basket. Otherwise they cast the Nth
                            // spell. Blitz batches a shop visit into one confirm, so
                            // staging matches it (and the server ends trading after
                            // a single confirm — sending per keypress only ever
                            // bought one item).
                            let has_offer = self
                                .net
                                .as_ref()
                                .and_then(|n| n.world.current_trade.as_ref())
                                .map(|t| idx < t.offers.len())
                                .unwrap_or(false);
                            if has_offer {
                                if let Some(p) = self.pending_buys.iter().position(|&b| b == idx) {
                                    self.pending_buys.remove(p);
                                } else {
                                    self.pending_buys.push(idx);
                                }
                                return;
                            }
                            // Activate hotbar slot `idx` (cast spell / use item).
                            self.use_slot(idx);
                        }
                        KeyCode::KeyW | KeyCode::ArrowUp => self.keys_wasd[0] = pressed,
                        KeyCode::KeyA => self.keys_wasd[1] = pressed,
                        KeyCode::KeyS | KeyCode::ArrowDown => self.keys_wasd[2] = pressed,
                        KeyCode::KeyD => self.keys_wasd[3] = pressed,
                        KeyCode::ShiftLeft | KeyCode::ShiftRight => self.run = pressed,
                        // Discrete camera turn (WASD move relative to it) —
                        // still available as a fallback when mouse-look is off.
                        KeyCode::ArrowLeft | KeyCode::KeyQ if pressed => self.cam_yaw -= 0.18,
                        KeyCode::ArrowRight | KeyCode::KeyE if pressed => self.cam_yaw += 0.18,
                        // Keyboard camera zoom (CAM-3): `-` zooms out, `=` zooms in.
                        KeyCode::Minus if pressed => self.cam_dist = zoom_step(self.cam_dist, 1.5),
                        KeyCode::Equal if pressed => self.cam_dist = zoom_step(self.cam_dist, -1.5),
                        // Toggle mouse-look (grab/hide the cursor).
                        KeyCode::Tab if pressed => {
                            let on = !self.mouse_look;
                            self.set_mouse_look(on);
                        }
                        // Chat scrollback (CHAT-3): PageUp shows older history,
                        // PageDown returns toward the newest line.
                        KeyCode::PageUp if pressed => self.chat_scroll = self.chat_scroll.saturating_add(3),
                        KeyCode::PageDown if pressed => self.chat_scroll = self.chat_scroll.saturating_sub(3),
                        // Toggle the inventory / spellbook panel.
                        KeyCode::KeyI if pressed => self.show_inventory = !self.show_inventory,
                        KeyCode::KeyL if pressed => self.show_quests = !self.show_quests,
                        KeyCode::KeyP if pressed => self.show_party = !self.show_party,
                        KeyCode::KeyT if pressed => {
                            // TGT-7: cycle the target to the next living NPC.
                            let rids = self.living_npc_rids();
                            self.target = next_target(self.target, &rids);
                        }
                        KeyCode::KeyK if pressed => self.show_spellbook = !self.show_spellbook,
                        KeyCode::KeyV if pressed => self.first_person = !self.first_person, // CAM-4
                        KeyCode::KeyJ if pressed && self.grounded => {
                            // MOVE-7: jump only when grounded. Kick the vertical
                            // velocity, leave the ground, and tell the server
                            // (empty payload — it identifies us by FromID).
                            self.jump_vel = JUMP_INIT_VEL;
                            self.jump_offset = 0.0;
                            self.grounded = false;
                            if let Some(net) = self.net.as_mut() {
                                net.transport.send(net.peer, rcce_net::packet_id::JUMP, &[], true);
                            }
                        }
                        // Interact (right-click) the target/nearest NPC — a
                        // vendor replies with P_OpenTrading → the vendor panel.
                        KeyCode::KeyR if pressed => {
                            if let Some(net) = self.net.as_mut() {
                                let rid = self
                                    .target
                                    .or_else(|| nearest_living_actor(&net.world, net.world.me_x, net.world.me_z));
                                if let Some(rid) = rid {
                                    self.target = Some(rid);
                                    net.transport.send(
                                        net.peer,
                                        rcce_net::packet_id::RIGHT_CLICK,
                                        &rcce_client::net::right_click_packet(rid),
                                        true,
                                    );
                                }
                            }
                        }
                        // Examine the target/nearest NPC — reply arrives as chat.
                        KeyCode::KeyX if pressed => {
                            if let Some(net) = self.net.as_mut() {
                                let rid = self
                                    .target
                                    .or_else(|| nearest_living_actor(&net.world, net.world.me_x, net.world.me_z));
                                if let Some(rid) = rid {
                                    self.target = Some(rid);
                                    net.transport.send(
                                        net.peer,
                                        rcce_net::packet_id::EXAMINE,
                                        &rcce_client::net::examine_packet(rid),
                                        true,
                                    );
                                }
                            }
                        }
                        // Audio: M mutes, [ / ] lower / raise master volume.
                        KeyCode::KeyM if pressed => {
                            if let Some(a) = self.audio.as_mut() {
                                let m = a.toggle_mute();
                                println!("[audio] muted = {m}");
                            }
                        }
                        KeyCode::BracketLeft if pressed => {
                            if let Some(a) = self.audio.as_mut() {
                                a.adjust_master_volume(-0.1);
                            }
                        }
                        KeyCode::BracketRight if pressed => {
                            if let Some(a) = self.audio.as_mut() {
                                a.adjust_master_volume(0.1);
                            }
                        }
                        KeyCode::Escape if pressed => {
                            // Blocker #1 (DELTA.md): ESC closes the topmost open
                            // layer and only exits when nothing is open — never
                            // quits out from under an open panel/menu/dialog.
                            let (dialog_open, script_input_open, trade_open) = self
                                .net
                                .as_ref()
                                .map(|n| {
                                    (
                                        n.world.dialog.is_some(),
                                        n.world.script_input.is_some(),
                                        n.world.current_trade.is_some(),
                                    )
                                })
                                .unwrap_or((false, false, false));
                            let open = EscOpen {
                                mouse_look: self.mouse_look,
                                image_window: self.image_window.is_some(),
                                script_input: script_input_open,
                                dialog: dialog_open,
                                context_menu: self.context_menu.is_some(),
                                item_menu: self.item_menu.is_some(),
                                trade: trade_open,
                                spellbook: self.show_spellbook,
                                inventory: self.show_inventory,
                                quests: self.show_quests,
                                party: self.show_party,
                                target: self.target.is_some(),
                            };
                            match esc_layer(open) {
                                EscLayer::MouseLook => self.set_mouse_look(false),
                                EscLayer::ImageWindow => self.image_window = None,
                                EscLayer::ScriptInput => {
                                    if let Some(net) = self.net.as_mut() {
                                        net.world.script_input = None;
                                    }
                                }
                                EscLayer::Dialog => {
                                    if let Some(net) = self.net.as_mut() {
                                        net.world.dialog = None;
                                    }
                                }
                                EscLayer::ContextMenu => self.context_menu = None,
                                EscLayer::ItemMenu => self.item_menu = None,
                                EscLayer::Trade => {
                                    if let Some(net) = self.net.as_mut() {
                                        net.transport.send(
                                            net.peer,
                                            rcce_net::packet_id::OPEN_TRADING,
                                            &rcce_client::net::trade_close_packet(),
                                            true,
                                        );
                                        net.world.current_trade = None;
                                    }
                                    // Discard the staged basket on cancel.
                                    self.pending_buys.clear();
                                    self.pending_sells.clear();
                                }
                                EscLayer::Spellbook => self.show_spellbook = false,
                                EscLayer::Inventory => self.show_inventory = false,
                                EscLayer::Quests => self.show_quests = false,
                                EscLayer::Party => self.show_party = false,
                                EscLayer::Target => self.target = None,
                                EscLayer::ExitGame => {
                                    self.shutdown_net();
                                    event_loop.exit();
                                }
                            }
                        }
                        _ => {}
                    }
                }
            }
            WindowEvent::CursorMoved { position, .. } => {
                self.cursor = (position.x as f32, position.y as f32);
                // Promote an armed press to a real drag once it moves past a small
                // dead-zone, so a click (memorise / cast) and a drag stay distinct.
                if let Some(drag) = self.drag.as_mut() {
                    if !drag.moved {
                        let (dx, dy) = (self.cursor.0 - drag.start.0, self.cursor.1 - drag.start.1);
                        if dx * dx + dy * dy > 25.0 {
                            drag.moved = true;
                        }
                    }
                }
            }
            WindowEvent::MouseInput { state, button, .. } => {
                // Left-click on the HUD acts only while mouse-look is off (cursor
                // free). Right-button toggles mouse-look on press for quick camera
                // grab, off on release.
                if self.qty_prompt.is_some() {
                    // The quantity modal is keyboard-driven; swallow mouse buttons
                    // so a stray click doesn't act on the HUD behind it.
                } else if button == MouseButton::Left && state == ElementState::Pressed && !self.mouse_look {
                    if matches!(self.mode, Mode::Login | Mode::CharSelect) {
                        // MENU-2: click the on-screen Login / character-select buttons.
                        self.menu_click(event_loop);
                    } else if !self.begin_drag() {
                        // Arm a spell drag if the press is over a draggable source
                        // (memorised spellbook row / occupied hotbar slot); the release
                        // decides click-vs-drag. Otherwise act on the click immediately.
                        self.hud_click();
                    }
                } else if button == MouseButton::Left && state == ElementState::Released {
                    self.end_drag();
                } else if button == MouseButton::Right {
                    // Right-click over an inventory item opens its context menu
                    // (Use/Equip/Drop) instead of grabbing the camera.
                    if state == ElementState::Pressed && !self.mouse_look && self.try_open_item_menu() {
                        // menu opened; no camera grab
                    } else {
                        self.set_mouse_look(state == ElementState::Pressed);
                    }
                } else if button == MouseButton::Middle && state == ElementState::Pressed {
                    // CAM-5: middle-click snaps the camera directly behind the
                    // character (cam_yaw -> me_yaw, cam_pitch -> 0). me_yaw is in
                    // degrees (wire unit); cam_yaw is radians.
                    let me_yaw = self
                        .net
                        .as_ref()
                        .map(|n| n.world.me_yaw.to_radians())
                        .unwrap_or(self.cam_yaw);
                    let (yaw, pitch) = snap_camera(me_yaw);
                    self.cam_yaw = yaw;
                    self.cam_pitch = pitch;
                }
            }
            WindowEvent::MouseWheel { delta, .. } => {
                // CAM-3: wheel zooms the third-person boom. One line/notch ≈ 1.5
                // units (Blitz MZSpeed*1.5); wheel-up (positive y) zooms IN.
                let lines = match delta {
                    MouseScrollDelta::LineDelta(_, y) => y,
                    MouseScrollDelta::PixelDelta(p) => (p.y as f32) / 40.0,
                };
                // With a scrollable panel open and the cursor over it, the wheel
                // scrolls that list instead of zooming the camera.
                let (sw, sh) = self.gfx.as_ref().map(|g| (g.config.width as f32, g.config.height as f32)).unwrap_or((0.0, 0.0));
                let (cx, cy) = self.cursor;
                let in_rect = |r: (f32, f32, f32, f32)| cx >= r.0 && cx < r.0 + r.2 && cy >= r.1 && cy < r.1 + r.3;
                if self.show_spellbook && in_rect(spellbook_rect(sw, sh)) {
                    if lines > 0.0 {
                        self.spellbook_scroll = self.spellbook_scroll.saturating_sub(1);
                    } else if lines < 0.0 {
                        self.spellbook_scroll += 1; // clamped in the render
                    }
                } else if self.show_quests && in_rect(quest_window_rect(sw, sh)) {
                    if lines > 0.0 {
                        self.quest_scroll = self.quest_scroll.saturating_sub(1);
                    } else if lines < 0.0 {
                        self.quest_scroll += 1; // clamped in the render
                    }
                } else if lines != 0.0 {
                    self.cam_dist = zoom_step(self.cam_dist, -lines * 1.5);
                }
            }
            WindowEvent::RedrawRequested => {
                self.render();
                // Headless clean-quit self-test: at RCCE_QUITAT=<frame>, quit via
                // the SAME path as an in-world Esc (shutdown_net + event_loop.exit,
                // NOT process::exit) so the graceful-disconnect-on-quit can be
                // verified end-to-end (re-login after must succeed).
                if let Ok(qv) = std::env::var("RCCE_QUITAT") {
                    if let Ok(at) = qv.parse::<u64>() {
                        if self.frames >= at {
                            println!("[quitat] frame {} clean-quit via event_loop.exit", self.frames);
                            self.shutdown_net();
                            event_loop.exit();
                            return;
                        }
                    }
                }
                if let Some(w) = &self.window {
                    w.request_redraw();
                }
            }
            _ => {}
        }
    }

    fn device_event(&mut self, _: &ActiveEventLoop, _: DeviceId, event: DeviceEvent) {
        if !self.mouse_look {
            return;
        }
        if let DeviceEvent::MouseMotion { delta: (dx, dy) } = event {
            const SENS: f32 = 0.0032;
            self.cam_yaw += dx as f32 * SENS;
            // Up-drag looks up; clamp so the camera can't flip past the poles.
            self.cam_pitch = (self.cam_pitch - dy as f32 * SENS).clamp(-0.35, 1.30);
        }
    }
}

impl App {
    /// Build the live `Net` + world from a successful login and switch to the
    /// in-world screen. Shared by auto-login and interactive enter-world. The
    /// render loop reloads the character's actual spawn zone on the next frame
    /// (its `P_ChangeArea` differs from the menu backdrop zone).
    fn enter_outcome(
        &mut self,
        transport: EnetTransport,
        outcome: rcce_client::login::LoginOutcome,
        store: &AssetStore,
    ) {
        let mut world = World {
            my_runtime_id: outcome.runtime_id,
            template_genders: store.template_genders(),
            // Which attribute slot is Health for this project (default 0);
            // P_StatUpdate reports HP under this slot.
            health_stat: store.health_stat(),
            ..Default::default()
        };
        for m in &outcome.world_packets {
            world.apply(m);
        }
        println!("[client-window] ✓ in world '{}', RuntimeID={}", world.zone.name, outcome.runtime_id);
        if let Some(s) = &outcome.sheet {
            println!(
                "[client-window] sheet: gold={} level={} {} item(s) {} spell(s)",
                s.gold, s.level, s.inventory.len(), s.spells.len()
            );
            for it in &s.inventory {
                world.me_inventory.insert(it.slot, *it);
            }
            // Seed the LIVE gold balance from the sheet. `me_gold` is a delta
            // accumulator (P_GoldChange adds U/D deltas); without this seed it
            // started at 0, so the HUD gold (which now reads me_gold) would have
            // shown a balance relative to login instead of the real total.
            world.me_gold = s.gold as i32;
            // Populate the spellbook's known-spell list from the login sheet.
            // Login spells arrive via P_FetchCharacter, NOT P_KnownSpellUpdate, so
            // without this the spellbook was EMPTY at login for any character that
            // already knew spells. The sheet is in the server's ascending
            // KnownSpells[] order, so the position is the wire memorise index.
            if world.known_spells.is_empty() {
                world.known_spells = s
                    .spells
                    .iter()
                    .enumerate()
                    .map(|(i, sp)| rcce_client::world::KnownSpell {
                        id: sp.id,
                        name: sp.name.clone(),
                        level: sp.level,
                        known_index: i as u16,
                    })
                    .collect();
                world
                    .known_spells
                    .sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
            }
        }
        // Load the persisted hotbar (P_ActionBarUpdate round-trip): resolve each
        // stored spell NAME back to its id via the sheet / known spells; item slots
        // carry their id directly. Slots past the 12 visible are dropped. A non-empty
        // result makes the bar explicit, so `effective_action_bar` shows exactly the
        // saved layout (no auto-fill).
        {
            let mut bar: [Option<HotbarEntry>; 12] = [None; 12];
            let mut unresolved = 0usize;
            for (slot, sl) in &outcome.action_bar {
                if *slot >= 12 {
                    continue;
                }
                match sl {
                    rcce_client::login::ActionSlot::Spell(name) => {
                        let id = outcome
                            .sheet
                            .as_ref()
                            .and_then(|s| s.spells.iter().find(|sp| sp.name.eq_ignore_ascii_case(name)).map(|sp| sp.id))
                            .or_else(|| world.known_spells.iter().find(|k| k.name.eq_ignore_ascii_case(name)).map(|k| k.id));
                        match id {
                            Some(id) => bar[*slot] = Some(HotbarEntry::Spell(id)),
                            None => unresolved += 1,
                        }
                    }
                    rcce_client::login::ActionSlot::Item(id) => bar[*slot] = Some(HotbarEntry::Item(*id)),
                }
            }
            let loaded = bar.iter().filter(|s| s.is_some()).count();
            if !outcome.action_bar.is_empty() {
                // Raw slot names as they came back off the wire (before id
                // resolution), so a relog visibly proves the save→load round-trip
                // even when the live sheet can't map every name to an id.
                let raw: Vec<String> = outcome
                    .action_bar
                    .iter()
                    .map(|(i, sl)| match sl {
                        rcce_client::login::ActionSlot::Spell(n) => format!("{i}:S '{n}'"),
                        rcce_client::login::ActionSlot::Item(id) => format!("{i}:I {id}"),
                    })
                    .collect();
                println!(
                    "[client-window] action bar: {} saved slot(s) [{}] -> {loaded} resolved{}",
                    outcome.action_bar.len(),
                    raw.join(", "),
                    if unresolved > 0 { format!(", {unresolved} spell name(s) unresolved") } else { String::new() }
                );
            }
            self.action_bar = bar;
        }
        // Seed the live memorised set (by spell id) from the sheet's login flags,
        // so the spellbook's memorised dots and the hotbar auto-fill agree from the
        // first frame (they share `self.memorised` from here on).
        self.memorised = outcome
            .sheet
            .as_ref()
            .map(|s| s.spells.iter().filter(|sp| sp.memorised).map(|sp| sp.id).collect())
            .unwrap_or_default();
        self.sheet = outcome.sheet;
        self.net = Some(Net { transport, world, peer: outcome.peer, updates: 0, env_requested: false });
        self.mode = Mode::InWorld;
        // MENU-10: stop the menu track so it doesn't bleed into a zone that ships
        // no music; the zone's own LoadingMusicID starts on the next render.
        if self.menu_music_on {
            if let Some(a) = self.audio.as_mut() {
                a.stop_music();
            }
            self.menu_music_on = false;
        }
    }

    /// Login screen submit: open the menu connection, create/verify the account,
    /// and advance to character select on success.
    /// Begin account login on a background thread so the window never freezes
    /// during the (up to ~5s) ENet connect + handshake. `poll_login` applies the
    /// result once it arrives. No-op if a login is already in flight. The menu
    /// keeps painting a "Connecting…" state throughout (mode stays `Login`).
    fn submit_login(&mut self) {
        if self.login_rx.is_some() {
            return;
        }
        let creds = Credentials {
            username: self.login_user.trim().to_string(),
            password: self.login_pass.clone(),
            email: "rust@bot.com".to_string(),
        };
        if creds.username.is_empty() {
            self.login_msg = "Account name required".to_string();
            return;
        }
        // MD5 derives from the password alone (no transport needed) — cache it
        // now for the later create/delete calls.
        self.login_md5 = rcce_net::auth::md5_hex(&creds.password);
        self.login_msg = "Connecting…".to_string();
        let (host, port) = (self.host.clone(), self.port);
        let (tx, rx) = std::sync::mpsc::channel::<LoginResult>();
        self.login_rx = Some(rx);
        std::thread::spawn(move || {
            // The transport is constructed, used, and (on success) moved back to
            // the UI thread through the channel — single-owner, never shared.
            let mut transport = EnetTransport::new();
            let result = match account_login(&mut transport, &host, port, &creds) {
                Ok((peer, chars)) => Ok((transport, peer, chars)),
                Err(e) => Err(e),
            };
            let _ = tx.send(result); // receiver gone (window closed) → drop
        });
    }

    /// Synchronous account login — used only by the headless `RCCE_AUTOSUBMIT`
    /// hook, which must complete before the first frame is captured (preserving
    /// the screenshot harness's timing). Interactive logins use `submit_login`.
    fn submit_login_blocking(&mut self) {
        let creds = Credentials {
            username: self.login_user.trim().to_string(),
            password: self.login_pass.clone(),
            email: "rust@bot.com".to_string(),
        };
        if creds.username.is_empty() {
            self.login_msg = "Account name required".to_string();
            return;
        }
        self.login_md5 = rcce_net::auth::md5_hex(&creds.password);
        self.login_msg = "Connecting…".to_string();
        let mut transport = EnetTransport::new();
        let result = match account_login(&mut transport, &self.host, self.port, &creds) {
            Ok((peer, chars)) => Ok((transport, peer, chars)),
            Err(e) => Err(e),
        };
        self.account_login_apply(result);
    }

    /// Drain the background login worker (if any) once per frame and apply its
    /// result. Non-blocking: returns immediately while the worker is still
    /// connecting, so the menu keeps animating.
    fn poll_login(&mut self) {
        let Some(rx) = self.login_rx.as_ref() else {
            return;
        };
        match rx.try_recv() {
            Ok(result) => {
                self.login_rx = None;
                self.account_login_apply(result);
            }
            Err(std::sync::mpsc::TryRecvError::Empty) => {}
            Err(std::sync::mpsc::TryRecvError::Disconnected) => {
                // Worker vanished without sending (panic) — fail soft.
                self.login_rx = None;
                self.login_msg = "Connection failed".to_string();
            }
        }
    }

    /// Apply an account-login result to the menu state. Shared by the async
    /// (`poll_login`) and synchronous (`submit_login_blocking`) paths.
    fn account_login_apply(&mut self, result: LoginResult) {
        match result {
            Ok((transport, peer, chars)) => {
                self.login_transport = Some(transport);
                self.login_peer = peer;
                self.chars = chars;
                self.char_sel = 0;
                self.creating = None;
                self.login_msg = if self.chars.is_empty() {
                    "No characters — press C to create one".to_string()
                } else {
                    String::new()
                };
                self.mode = Mode::CharSelect;
                // MENU-12: remember the account for next launch's pre-fill.
                if let Some(s) = self.store.as_ref() {
                    s.save_last_username(self.login_user.trim());
                }
            }
            Err(e) => self.login_msg = e,
        }
    }

    /// Enter the world as the highlighted character.
    fn enter_selected(&mut self) {
        if self.chars.is_empty() {
            self.login_msg = "Create a character first (press C)".to_string();
            return;
        }
        let idx = self.char_sel.min(self.chars.len() - 1) as u8;
        let user = self.login_user.trim().to_string();
        let md5 = self.login_md5.clone();
        let (host, port) = (self.host.clone(), self.port);
        let Some(mut transport) = self.login_transport.take() else { return };
        self.login_msg = "Entering world…".to_string();
        match enter_world(&mut transport, self.login_peer, &host, port, &user, &md5, idx) {
            Ok(outcome) => {
                let store = self.store.take();
                if let Some(s) = &store {
                    self.enter_outcome(transport, outcome, s);
                }
                self.store = store;
            }
            Err(e) => {
                self.login_msg = format!("Enter failed: {e}");
                self.login_transport = Some(transport);
            }
        }
    }

    /// Open the create sub-screen (type a name, Left/Right picks the race).
    fn begin_create(&mut self) {
        if self.playable.is_empty() {
            self.login_msg = "No playable races in this project".to_string();
            return;
        }
        self.creating = Some((String::new(), 0));
        self.login_msg = "Name it · ←/→ race · Enter create · Esc cancel".to_string();
    }

    /// Submit the create sub-screen.
    fn submit_create(&mut self) {
        let Some((name, tpl_idx)) = self.creating.clone() else { return };
        let name = name.trim().to_string();
        if name.is_empty() {
            self.login_msg = "Name required".to_string();
            return;
        }
        let Some(&(actor_id, _)) = self.playable.get(tpl_idx) else { return };
        let user = self.login_user.trim().to_string();
        let md5 = self.login_md5.clone();
        let Some(mut t) = self.login_transport.take() else { return };
        match create_char(&mut t, self.login_peer, &user, &md5, actor_id, &name) {
            Ok(chars) => {
                self.chars = chars;
                self.creating = None;
                self.char_sel = self.chars.len().saturating_sub(1);
                self.login_msg = format!("Created {name}");
            }
            Err(e) => self.login_msg = e,
        }
        self.login_transport = Some(t);
    }

    /// Delete the highlighted character (best-effort; the server may reject it
    /// pre-session).
    fn delete_selected(&mut self) {
        if self.chars.is_empty() {
            return;
        }
        let idx = self.char_sel.min(self.chars.len() - 1) as u8;
        let user = self.login_user.trim().to_string();
        let md5 = self.login_md5.clone();
        let Some(mut t) = self.login_transport.take() else { return };
        match delete_char(&mut t, self.login_peer, &user, &md5, idx) {
            Ok(chars) => {
                self.chars = chars;
                self.char_sel = 0;
                self.login_msg = "Character deleted".to_string();
            }
            Err(e) => self.login_msg = e,
        }
        self.login_transport = Some(t);
    }

    /// Left-click handling for the Login / Character-Select screens (MENU-2):
    /// hit-test the on-screen buttons against the cursor and dispatch to the
    /// same actions the keyboard triggers. No-op if a login is already in flight
    /// (the menu_key guard) or the click misses every button.
    fn menu_click(&mut self, event_loop: &ActiveEventLoop) {
        if self.login_rx.is_some() {
            return;
        }
        let (sw, sh) = match self.gfx.as_ref() {
            Some(g) => (g.config.width as f32, g.config.height as f32),
            None => return,
        };
        let (wx, wy, ww, wh) = menu_window_for(self.mode, sw, sh);
        let buttons = menu_buttons(
            self.mode,
            self.creating.is_some(),
            !self.chars.is_empty(),
            wx,
            wy,
            ww,
            wh,
        );
        let (cx, cy) = self.cursor;
        let Some(action) = menu_button_hit(&buttons, cx, cy) else {
            return;
        };
        match action {
            MenuBtnAction::Login => self.submit_login(),
            MenuBtnAction::Quit => {
                self.shutdown_net();
                event_loop.exit();
            }
            MenuBtnAction::EnterWorld => self.enter_selected(),
            MenuBtnAction::Create => self.begin_create(),
            MenuBtnAction::Delete => self.delete_selected(),
            MenuBtnAction::Back => {
                self.mode = Mode::Login;
                self.login_msg = String::new();
            }
        }
    }

    /// Keyboard handling for the login + character-select screens.
    fn menu_key(&mut self, event_loop: &ActiveEventLoop, code: KeyCode, text: Option<&str>) {
        // While a background account-login is connecting, swallow all menu input:
        // the transport is checked out to the worker, so create/delete/enter
        // would no-op, and a second Enter must not spawn a duplicate worker.
        if self.login_rx.is_some() {
            return;
        }
        match self.mode {
            // MENU-13 EULA gate: Enter accepts → Login; Esc declines → quit;
            // PageUp/PageDown scroll the license text.
            Mode::Eula => match code {
                KeyCode::Enter | KeyCode::NumpadEnter => self.mode = Mode::Login,
                KeyCode::Escape => {
                    self.shutdown_net();
                    event_loop.exit();
                }
                KeyCode::PageUp => self.eula_scroll = self.eula_scroll.saturating_sub(8),
                KeyCode::PageDown => self.eula_scroll = self.eula_scroll.saturating_add(8),
                _ => {}
            },
            // Sound options screen: Left/Right (or -/=) adjust master volume, M
            // mutes, Esc returns to Login. Wired straight to the audio engine.
            // Keybind reference: Esc/Tab returns to the Sound options screen.
            Mode::Controls => match code {
                KeyCode::Escape | KeyCode::Tab | KeyCode::Enter | KeyCode::NumpadEnter => {
                    self.mode = Mode::Options
                }
                _ => {}
            },
            Mode::Options => match code {
                KeyCode::Escape | KeyCode::Enter | KeyCode::NumpadEnter => self.mode = Mode::Login,
                KeyCode::Tab => self.mode = Mode::Controls,
                KeyCode::ArrowLeft | KeyCode::Minus => {
                    if let Some(a) = self.audio.as_mut() {
                        let v = volume_step(a.master_volume(), -0.05);
                        a.set_master_volume(v);
                    }
                }
                KeyCode::ArrowRight | KeyCode::Equal => {
                    if let Some(a) = self.audio.as_mut() {
                        let v = volume_step(a.master_volume(), 0.05);
                        a.set_master_volume(v);
                    }
                }
                KeyCode::KeyM => {
                    if let Some(a) = self.audio.as_mut() {
                        a.toggle_mute();
                    }
                }
                _ => {}
            },
            Mode::Login => match code {
                KeyCode::Enter | KeyCode::NumpadEnter => self.submit_login(),
                KeyCode::F1 => self.mode = Mode::Options,
                KeyCode::Tab | KeyCode::ArrowDown | KeyCode::ArrowUp => {
                    self.login_focus ^= 1;
                }
                KeyCode::Backspace => {
                    let f = if self.login_focus == 0 { &mut self.login_user } else { &mut self.login_pass };
                    f.pop();
                }
                KeyCode::Escape => {
                    self.shutdown_net();
                    event_loop.exit();
                }
                _ => {
                    if let Some(t) = text {
                        let f = if self.login_focus == 0 { &mut self.login_user } else { &mut self.login_pass };
                        for ch in t.chars().filter(|c| !c.is_control() && *c != ' ') {
                            if f.chars().count() < 24 {
                                f.push(ch);
                            }
                        }
                    }
                }
            },
            Mode::CharSelect => {
                if let Some((name, tpl)) = self.creating.as_mut() {
                    match code {
                        KeyCode::Enter | KeyCode::NumpadEnter => self.submit_create(),
                        KeyCode::Escape => {
                            self.creating = None;
                            self.login_msg = String::new();
                        }
                        KeyCode::Backspace => {
                            name.pop();
                        }
                        KeyCode::ArrowLeft => {
                            let n = self.playable.len();
                            if n > 0 {
                                *tpl = (*tpl + n - 1) % n;
                            }
                        }
                        KeyCode::ArrowRight => {
                            let n = self.playable.len();
                            if n > 0 {
                                *tpl = (*tpl + 1) % n;
                            }
                        }
                        _ => {
                            if let Some(t) = text {
                                for ch in t.chars().filter(|c| c.is_alphanumeric()) {
                                    if name.chars().count() < 16 {
                                        name.push(ch);
                                    }
                                }
                            }
                        }
                    }
                } else {
                    match code {
                        KeyCode::Enter | KeyCode::NumpadEnter => self.enter_selected(),
                        KeyCode::ArrowUp => {
                            if self.char_sel > 0 {
                                self.char_sel -= 1;
                            }
                        }
                        KeyCode::ArrowDown => {
                            if self.char_sel + 1 < self.chars.len() {
                                self.char_sel += 1;
                            }
                        }
                        KeyCode::KeyC => self.begin_create(),
                        KeyCode::Delete => self.delete_selected(),
                        KeyCode::Escape => {
                            self.mode = Mode::Login;
                            self.login_msg = String::new();
                        }
                        _ => {}
                    }
                }
            }
            Mode::InWorld => {}
        }
    }

    /// Handle a left-click on the HUD (mouse-look off). Hit-tests the bottom
    /// function-button row, then the inventory slot grid when the panel is open.
    /// Positions mirror the draw code exactly (shared FUNCTION_BUTTONS / the
    /// Interface.dat inv_buttons).
    /// SPL-4: toggle memorisation of known-spell `idx`. If already memorised,
    /// send `P_SpellUpdate "U"` and clear it; otherwise send `"M"` and start the
    /// memorise progress bar. `known_num` is the spell's index in the known list
    /// (the server applies it against its `KnownSpells[]` when `RequireMemorise`).
    fn toggle_memorise(&mut self, idx: usize) {
        // The spellbook row `idx` indexes the DISPLAY-sorted known-spell list; the
        // memorised SET is keyed by spell id. The wire packet must carry the
        // SERVER's KnownSpells[] index (`known_index`), not the sorted row position
        // — sending the row position memorised the wrong spell once the list had
        // more than one entry (the sort reorders them).
        let Some((id, known_index)) = self.net.as_ref().and_then(|n| n.world.known_spells.get(idx)).map(|s| (s.id, s.known_index)) else {
            return;
        };
        let now = self.start.elapsed().as_secs_f32();
        let already = self.memorised.contains(&id);
        let packet = if already {
            self.memorised.remove(&id);
            self.memorising = None;
            rcce_client::net::unmemorise_packet(known_index)
        } else {
            self.memorising = Some((idx, now));
            rcce_client::net::memorise_packet(known_index)
        };
        if let Some(net) = self.net.as_mut() {
            net.transport.send(net.peer, rcce_net::packet_id::SPELL_UPDATE, &packet, true);
        }
        println!("[memorise] idx {idx} id {id} {}", if already { "UNMEMORISE sent" } else { "MEMORISE sent" });
    }

    /// The effective action-bar contents: the explicit `action_bar` assignments
    /// once the player has customised the bar, otherwise an auto-fill from the
    /// first 12 memorised spells (the pre-drag-drop default). One source of truth
    /// for both the bar render and casting so they can never disagree.
    fn action_bar_ids(&self) -> [Option<HotbarEntry>; 12] {
        effective_action_bar(&self.action_bar, self.sheet.as_ref(), &self.memorised)
    }

    /// Promote the current auto-filled view into explicit `action_bar` slots, so
    /// a subsequent drop edits a concrete layout instead of fighting the auto-fill.
    /// No-op once any slot is already assigned (idempotent; never clobbers edits).
    fn materialize_action_bar(&mut self) {
        if self.action_bar.iter().all(|s| s.is_none()) {
            self.action_bar = self.action_bar_ids();
        }
    }

    /// Resolve a spell id to its name (for the `P_ActionBarUpdate` send, which
    /// keys the server bar by name) via the sheet, falling back to known spells.
    fn spell_name_for_id(&self, id: u16) -> Option<String> {
        self.sheet
            .as_ref()
            .and_then(|s| s.spells.iter().find(|sp| sp.id == id).map(|sp| sp.name.clone()))
            .or_else(|| {
                self.net.as_ref().and_then(|n| n.world.known_spells.iter().find(|k| k.id == id).map(|k| k.name.clone()))
            })
    }

    /// Persist the whole 12-slot hotbar to the server (`P_ActionBarUpdate`, one
    /// reliable packet per slot: spell-by-name or clear). Sending the full bar
    /// after any edit keeps the server's stored layout == what the player sees, so
    /// the next login's load (`enter_outcome`) reproduces it exactly — no drift
    /// between the materialised auto-fill and the saved slots. Cheap: at most 12
    /// reliable packets on an occasional drag, not a per-frame cost.
    fn persist_action_bar(&mut self) {
        if self.net.is_none() {
            return;
        }
        for slot in 0..12usize {
            let pkt = match self.action_bar[slot] {
                Some(HotbarEntry::Spell(id)) => match self.spell_name_for_id(id) {
                    Some(name) => rcce_client::net::action_bar_spell_packet(slot as u8, &name),
                    // Unknown name (shouldn't happen for an assigned spell) — skip
                    // rather than clobber the server slot with a bad value.
                    None => continue,
                },
                Some(HotbarEntry::Item(item_id)) => rcce_client::net::action_bar_item_packet(slot as u8, item_id),
                None => rcce_client::net::action_bar_clear_packet(slot as u8),
            };
            if let Some(net) = self.net.as_mut() {
                net.transport.send(net.peer, rcce_net::packet_id::ACTION_BAR_UPDATE, &pkt, true);
            }
        }
    }

    /// Activate action-bar slot `i` (Digit key or click): cast the spell (respecting
    /// the per-spell cooldown) or use the item (eat / run its Use script, found in
    /// the inventory by id). No-op for an empty slot or an item no longer carried.
    fn use_slot(&mut self, i: usize) {
        let Some(entry) = self.action_bar_ids().get(i).copied().flatten() else {
            return;
        };
        match entry {
            HotbarEntry::Spell(spell_id) => {
                let recharge = self
                    .sheet
                    .as_ref()
                    .and_then(|s| s.spells.iter().find(|x| x.id == spell_id))
                    .map(|x| x.recharge)
                    .unwrap_or(0);
                let now = self.start.elapsed().as_secs_f32();
                let ready = self.spell_cooldowns.get(&spell_id).copied().unwrap_or(0.0);
                if now < ready {
                    return;
                }
                let target = self.target;
                if let Some(net) = self.net.as_mut() {
                    net.transport.send(
                        net.peer,
                        rcce_net::packet_id::SPELL_UPDATE,
                        &rcce_client::net::cast_packet(spell_id, target),
                        true,
                    );
                }
                self.spell_cooldowns.insert(spell_id, now + recharge as f32 / 1000.0);
            }
            HotbarEntry::Item(item_id) => {
                // Find an inventory slot holding this item, then use it the same way
                // the inventory Eat button does (Interface3D.bb UseItem): Potion (4)
                // / Ingredient (5) are eaten, everything else runs its Use script.
                let slot = self
                    .net
                    .as_ref()
                    .and_then(|n| n.world.me_inventory.values().find(|it| it.item_id == item_id).map(|it| it.slot));
                let Some(slot) = slot else { return };
                let (item_type, image_id) = self
                    .store
                    .as_ref()
                    .and_then(|s| s.item_def(item_id))
                    .map(|d| (d.item_type, d.image_id))
                    .unwrap_or((0, -1));
                let edible = item_type == 4 || item_type == 5;
                let target = self.target;
                if let Some(net) = self.net.as_mut() {
                    if edible {
                        net.transport.send(net.peer, rcce_net::packet_id::EAT_ITEM, &rcce_client::net::eat_item_packet(slot, 1), true);
                    } else {
                        net.transport.send(net.peer, rcce_net::packet_id::ITEM_SCRIPT, &rcce_client::net::item_script_packet(slot, target), true);
                    }
                }
                if item_type == 6 && image_id >= 0 {
                    self.image_window = Some(image_id as u16);
                }
            }
        }
    }

    /// Left-button press while the cursor is over a draggable source: begin a drag
    /// (deferred — the release decides click-vs-drag). Returns true if a drag was
    /// armed, so the caller skips the immediate `hud_click`. Draggable sources: a
    /// *memorised* spellbook row (parity: only memorised spells slot), an occupied
    /// action-bar slot, or an inventory item slot (drag the item onto the bar).
    fn begin_drag(&mut self) -> bool {
        let Some(gfx) = self.gfx.as_ref() else { return false };
        let (sw, sh) = (gfx.config.width as f32, gfx.config.height as f32);
        let (cx, cy) = self.cursor;
        // Spellbook row first (it overlaps nothing on the bar row).
        if self.show_spellbook {
            let row = self
                .spell_hitboxes
                .iter()
                .find(|&&(x, y, w, h, _)| cx >= x && cx <= x + w && cy >= y && cy <= y + h)
                .map(|&(_, _, _, _, idx)| idx);
            if let Some(idx) = row {
                if let Some(id) = self.net.as_ref().and_then(|n| n.world.known_spells.get(idx)).map(|s| s.id) {
                    if self.memorised.contains(&id) {
                        self.drag = Some(SpellDrag { entry: HotbarEntry::Spell(id), src: DragSrc::Spellbook(idx), start: (cx, cy), moved: false });
                        return true;
                    }
                }
                // A non-memorised row is still a normal click (memorise) — let
                // hud_click handle it; don't arm a drag.
                return false;
            }
        }
        // Inventory item slot (panel open): drag the item onto the bar. A plain
        // click on a grid slot is otherwise a no-op, so this can't steal an action.
        if self.show_inventory {
            let slot = self
                .store
                .as_ref()
                .and_then(|s| s.interface())
                .and_then(|iface| inventory_slot_at(cx, cy, iface.inventory_window, &iface.inventory_buttons, sw, sh));
            if let Some(slot) = slot {
                if let Some(item_id) = self
                    .net
                    .as_ref()
                    .and_then(|n| n.world.me_inventory.values().find(|it| it.slot == slot as u8).map(|it| it.item_id))
                {
                    self.drag = Some(SpellDrag { entry: HotbarEntry::Item(item_id), src: DragSrc::Inventory(slot as u8), start: (cx, cy), moved: false });
                    return true;
                }
            }
        }
        // Action-bar slot that holds an entry.
        if let Some(slot) = spell_slot_at(cx, cy, sw, sh) {
            if let Some(entry) = self.action_bar_ids().get(slot).copied().flatten() {
                self.drag = Some(SpellDrag { entry, src: DragSrc::Slot(slot), start: (cx, cy), moved: false });
                return true;
            }
        }
        false
    }

    /// Left-button release: finish the in-flight drag (if any). A drag that never
    /// moved is treated as a click (memorise the row / use the slot); a real drag
    /// drops onto the action-bar slot under the cursor (assign), an inventory slot
    /// (equip / move / unequip), or nowhere (clear a bar slot / cancel).
    fn end_drag(&mut self) {
        let Some(drag) = self.drag.take() else { return };
        let Some(gfx) = self.gfx.as_ref() else { return };
        let (sw, sh) = (gfx.config.width as f32, gfx.config.height as f32);
        let (cx, cy) = self.cursor;
        if !drag.moved {
            // Plain click: same effect the press would have had.
            match drag.src {
                DragSrc::Spellbook(idx) => self.toggle_memorise(idx),
                DragSrc::Slot(i) => self.use_slot(i),
                DragSrc::Inventory(_) => { /* click on a bag slot — no-op */ }
            }
            return;
        }
        // Dropped onto the hotbar → assign / rearrange (covers every source).
        if let Some(slot) = spell_slot_at(cx, cy, sw, sh) {
            self.materialize_action_bar();
            // Move semantics: dragging a bar slot onto another swaps the source out
            // (so an entry isn't duplicated by a rearrange).
            if let DragSrc::Slot(from) = drag.src {
                if from != slot {
                    self.action_bar[from] = None;
                }
            }
            self.action_bar[slot] = Some(drag.entry);
            self.persist_action_bar();
            return;
        }
        // Not on the hotbar.
        match drag.src {
            DragSrc::Slot(from) => {
                // Dragged a bar slot off the bar: clear it (un-assign).
                self.materialize_action_bar();
                self.action_bar[from] = None;
                self.persist_action_bar();
            }
            DragSrc::Inventory(from) => {
                // Dropped onto an open vendor/container window → sell that slot.
                let selling = point_in_vendor(cx, cy, sw, sh)
                    && matches!(
                        self.net.as_ref().and_then(|n| n.world.current_trade.as_ref()).map(|t| t.kind),
                        Some(rcce_client::trade::TradeKind::Npc) | Some(rcce_client::trade::TradeKind::Scenery)
                    );
                if selling {
                    self.sell_inventory_slot(from);
                    return;
                }
                // Otherwise, dropped on an inventory slot → equip / move / unequip.
                if self.show_inventory {
                    let to = self
                        .store
                        .as_ref()
                        .and_then(|s| s.interface())
                        .and_then(|iface| inventory_slot_at(cx, cy, iface.inventory_window, &iface.inventory_buttons, sw, sh));
                    if let Some(to) = to {
                        self.inventory_move(from, to as u8);
                    }
                }
            }
            DragSrc::Spellbook(_) => { /* dropped nowhere — cancel */ }
        }
    }

    /// Move/equip the item in inventory slot `from` onto slot `to` (drag-drop): a
    /// `P_InventoryUpdate` swap. Dropping a backpack item into the equipment region
    /// equips it to the item's *proper* slot (matching Shift+equip), so a sword
    /// dropped anywhere on the gear column lands in the weapon slot; a non-equippable
    /// item dropped there is ignored. Any other drop is a direct slot swap (backpack
    /// rearrange, or unequip when `from` is an equipment slot and `to` is a bag slot).
    fn inventory_move(&mut self, from: u8, to: u8) {
        let item_id = self
            .net
            .as_ref()
            .and_then(|n| n.world.me_inventory.values().find(|it| it.slot == from).map(|it| it.item_id));
        let Some(item_id) = item_id else { return };
        let equip_slot = self.store.as_ref().and_then(|s| s.item_equip_slot(item_id));
        let Some(dest) = resolve_inventory_dest(from, to, equip_slot) else { return };
        let rid = self.net.as_ref().map(|n| n.world.my_runtime_id).unwrap_or(0);
        if let Some(net) = self.net.as_mut() {
            net.transport.send(
                net.peer,
                rcce_net::packet_id::INVENTORY_UPDATE,
                &rcce_client::net::inv_move_packet(rid, from, dest, 0, true),
                true,
            );
        }
    }

    /// Stage the item in inventory slot `slot` to be sold to the open vendor
    /// (toggle). The actual sell goes out with the whole basket when the player
    /// hits Confirm — Blitz batches buys+sells into one `P_OpenTrading` confirm.
    fn sell_inventory_slot(&mut self, slot: u8) {
        // Already staged → unstage (toggle).
        if let Some(p) = self.pending_sells.iter().position(|&(s, _)| s == slot) {
            self.pending_sells.remove(p);
            return;
        }
        let item = self
            .net
            .as_ref()
            .and_then(|n| n.world.me_inventory.values().find(|it| it.slot == slot))
            .map(|it| (it.item_id, it.amount.max(1)));
        let Some((item_id, amount)) = item else { return };
        if amount > 1 {
            // Stack → ask how many to sell.
            self.qty_prompt = Some(QtyPrompt { slot, item_id, max: amount, qty: amount, action: QtyAction::Sell });
        } else {
            self.pending_sells.push((slot, 1));
        }
    }

    /// Apply the open quantity prompt (Enter): stage the sell `(slot, qty)` or
    /// drop `qty` from the slot, then close the prompt.
    fn confirm_qty(&mut self) {
        let Some(p) = self.qty_prompt.take() else { return };
        let qty = clamp_qty(p.qty as i64, p.max);
        match p.action {
            QtyAction::Sell => {
                self.pending_sells.retain(|&(s, _)| s != p.slot);
                self.pending_sells.push((p.slot, qty));
            }
            QtyAction::Drop => {
                if let Some(net) = self.net.as_mut() {
                    net.transport.send(net.peer, rcce_net::packet_id::INVENTORY_UPDATE, &rcce_client::net::inv_drop_packet(p.slot, qty), true);
                }
            }
        }
    }

    /// Send the staged vendor basket as ONE `P_OpenTrading` confirm (all buys +
    /// all sells), then clear the staging and close the window — the server ends
    /// trading on a single confirm. No-op if nothing is staged.
    fn confirm_trade(&mut self) {
        if self.pending_buys.is_empty() && self.pending_sells.is_empty() {
            return;
        }
        let Some(net) = self.net.as_ref() else { return };
        let Some(trade) = net.world.current_trade.as_ref() else { return };
        let buys: Vec<(u32, u16)> = self
            .pending_buys
            .iter()
            .filter_map(|&i| trade.offers.get(i).map(|o| (o.server_trade_id, o.amount.max(1))))
            .collect();
        // Each staged sell carries its chosen quantity; clamp to the slot's live
        // amount in case the stack shrank since staging.
        let sells: Vec<(u8, u16)> = self
            .pending_sells
            .iter()
            .filter_map(|&(slot, qty)| {
                net.world
                    .me_inventory
                    .values()
                    .find(|it| it.slot == slot)
                    .map(|it| (slot, qty.clamp(1, it.amount.max(1))))
            })
            .collect();
        let pkt = rcce_client::net::trade_confirm_packet(&buys, &sells);
        if let Some(net) = self.net.as_mut() {
            net.transport.send(net.peer, rcce_net::packet_id::OPEN_TRADING, &pkt, true);
            // The server ends trading on confirm; reflect that client-side.
            net.world.current_trade = None;
        }
        self.pending_buys.clear();
        self.pending_sells.clear();
    }

    fn hud_click(&mut self) {
        // An open NPC dialog is modal (TGT-5): a click either selects an option
        // or is swallowed (no move/select while talking).
        if self.net.as_ref().map(|n| n.world.dialog.is_some()).unwrap_or(false) {
            let (cx, cy) = self.cursor;
            let opt = self
                .dialog_hitboxes
                .iter()
                .position(|&(x, y, w, h)| cx >= x && cx <= x + w && cy >= y && cy <= y + h);
            if let (Some(opt), Some(net)) = (opt, self.net.as_mut()) {
                if let Some(dl) = net.world.dialog.as_ref() {
                    let sh = dl.script_handle;
                    // Dialog options are 1-BASED on the server (AddDialogOption sets
                    // OptionNum = TotalOptions starting at 1; the script branches on
                    // 1,2,3…). `opt` is the 0-based hitbox position, so send opt+1 —
                    // sending the 0-based index meant the first option (e.g. a
                    // trainer's "learn fireball") never matched and silently no-op'd.
                    net.transport.send(
                        net.peer,
                        rcce_net::packet_id::DIALOG,
                        &rcce_client::net::dialog_option_packet(sh, (opt + 1) as u8),
                        true,
                    );
                }
                // Clear options after choosing; the server sends the next text.
                if let Some(dl) = net.world.dialog.as_mut() {
                    dl.options.clear();
                }
            }
            return;
        }
        // The Actions context menu takes priority: a click either picks an
        // action or dismisses the menu, and is consumed either way (TGT-3).
        if let Some(menu) = self.context_menu.take() {
            let (cx, cy) = self.cursor;
            if let Some(action) = menu.hit(cx, cy) {
                self.exec_menu_action(action, menu.rid);
            }
            return;
        }
        // Item context menu: same priority — pick an action or dismiss, consumed.
        if let Some(menu) = self.item_menu.take() {
            let (cx, cy) = self.cursor;
            if let Some(action) = menu.hit(cx, cy) {
                self.exec_item_action(action, menu.slot);
            }
            return;
        }
        // Spellbook memorise (SPL-4): a click on a spell row memorises it (or
        // un-memorises an already-memorised one), and is consumed.
        if self.show_spellbook {
            let (cx, cy) = self.cursor;
            let hit = self
                .spell_hitboxes
                .iter()
                .find(|&&(x, y, w, h, _)| cx >= x && cx <= x + w && cy >= y && cy <= y + h)
                .map(|&(_, _, _, _, idx)| idx);
            if let Some(idx) = hit {
                self.toggle_memorise(idx);
                return;
            }
        }
        let Some(gfx) = self.gfx.as_ref() else { return };
        let (sw, sh) = (gfx.config.width as f32, gfx.config.height as f32);
        let (cx, cy) = self.cursor;

        // Vendor Confirm button: sends the staged basket (buys + sells) as one
        // P_OpenTrading confirm. Checked while a trade is open and is consumed.
        if self.net.as_ref().map(|n| n.world.current_trade.is_some()).unwrap_or(false)
            && point_in_confirm(cx, cy, sw, sh)
        {
            self.confirm_trade();
            return;
        }

        // Function-button row.
        if let Some(action) = function_button_at(cx, cy, sw, sh) {
            match action {
                HudAction::Chat => {
                    if self.chat_input.is_none() {
                        self.chat_input = Some(String::new());
                    }
                }
                // Inventory / Character open the gear+backpack panel.
                HudAction::Inventory | HudAction::Character => {
                    self.show_inventory = !self.show_inventory;
                }
                // The Abilities button opens the Spellbook (memorise window), like
                // Blitz's WSpells — not the character panel.
                HudAction::Spells => self.show_spellbook = !self.show_spellbook,
                // Quest log (QST-1) / Party (PTY-1) toggle their own panels.
                HudAction::Quests => self.show_quests = !self.show_quests,
                HudAction::Party => self.show_party = !self.show_party,
                HudAction::Map | HudAction::Menu => {
                    println!("[client-window] HUD button not yet implemented");
                }
            }
            return;
        }

        // Inventory slot grid (only when the panel is open and we have positions).
        // When the panel is closed, a non-HUD click is a world click → select the
        // nearest actor under the cursor as the target.
        if !self.show_inventory {
            self.world_pick(sw, sh, cx, cy);
            return;
        }

        // Drop / Eat buttons act on the last-hovered inventory slot.
        let action_btn = self
            .store
            .as_ref()
            .and_then(|s| s.interface())
            .and_then(|i| inv_action_button_at(cx, cy, i.inventory_window, i.inventory_drop, i.inventory_eat, sw, sh));
        if let Some(action) = action_btn {
            if let Some(slot) = self.last_inv_slot {
                let item = self
                    .net
                    .as_ref()
                    .and_then(|n| n.world.me_inventory.values().find(|it| it.slot == slot))
                    .map(|it| it.item_id);
                if let Some(item_id) = item {
                    match action {
                        InvAction::Drop => {
                            if let Some(net) = self.net.as_mut() {
                                net.transport.send(
                                    net.peer,
                                    rcce_net::packet_id::INVENTORY_UPDATE,
                                    &rcce_client::net::inv_drop_packet(slot, 1),
                                    true,
                                );
                            }
                        }
                        InvAction::Eat => {
                            // Faithful to Blitz UseItem (Interface3D.bb:4138-4216):
                            // Potion (4) / Ingredient (5) are eaten (P_EatItem);
                            // every other type runs its Use script (P_ItemScript)
                            // with the selected target when one exists. Equipment
                            // (weapon/armour) is equipped via Shift-click elsewhere;
                            // here the script send still fires, matching the server
                            // contract (it tolerates items with no Use script).
                            let (item_type, image_id) = self
                                .store
                                .as_ref()
                                .and_then(|s| s.item_def(item_id))
                                .map(|d| (d.item_type, d.image_id))
                                .unwrap_or((0, -1));
                            let edible = item_type == 4 || item_type == 5;
                            let target = self.target;
                            if let Some(net) = self.net.as_mut() {
                                if edible {
                                    net.transport.send(
                                        net.peer,
                                        rcce_net::packet_id::EAT_ITEM,
                                        &rcce_client::net::eat_item_packet(slot, 1),
                                        true,
                                    );
                                } else {
                                    net.transport.send(
                                        net.peer,
                                        rcce_net::packet_id::ITEM_SCRIPT,
                                        &rcce_client::net::item_script_packet(slot, target),
                                        true,
                                    );
                                }
                            }
                            // I_Image (type 6): also open the full-size image
                            // popup (Interface3D.bb:4158-4204).
                            if item_type == 6 && image_id >= 0 {
                                self.image_window = Some(image_id as u16);
                            }
                        }
                    }
                }
            }
            return;
        }

        let Some(iface) = self.store.as_ref().and_then(|s| s.interface()) else { return };
        let clicked_slot = inventory_slot_at(cx, cy, iface.inventory_window, &iface.inventory_buttons, sw, sh)
            .map(|i| i as u8);
        let Some(slot) = clicked_slot else { return };
        // Resolve the item in the clicked slot from the live inventory.
        let item = self
            .net
            .as_ref()
            .and_then(|n| n.world.me_inventory.values().find(|it| it.slot == slot))
            .map(|it| (it.slot, it.item_id));
        let Some((slot, item_id)) = item else { return };
        let shift = self.run;
        if slot < 14 {
            // Equipment slot click → unequip to the first free backpack slot.
            let occupied: std::collections::HashSet<u8> = self
                .net
                .as_ref()
                .map(|n| n.world.me_inventory.values().map(|it| it.slot).collect())
                .unwrap_or_default();
            let dest = (14u8..=45).find(|s| !occupied.contains(s));
            if let (Some(dest), Some(net)) = (dest, self.net.as_mut()) {
                let rid = net.world.my_runtime_id;
                net.transport.send(
                    net.peer,
                    rcce_net::packet_id::INVENTORY_UPDATE,
                    &rcce_client::net::inv_move_packet(rid, slot, dest, 0, true),
                    true,
                );
            }
        } else if shift {
            // Shift-click a backpack item → equip into its gear slot.
            let dest = self.store.as_ref().and_then(|s| s.item_equip_slot(item_id));
            if let (Some(dest), Some(net)) = (dest, self.net.as_mut()) {
                let rid = net.world.my_runtime_id;
                net.transport.send(
                    net.peer,
                    rcce_net::packet_id::INVENTORY_UPDATE,
                    &rcce_client::net::inv_move_packet(rid, slot, dest, 0, true),
                    true,
                );
            }
        } else if let Some(net) = self.net.as_mut() {
            // Plain click a backpack item → drop one.
            net.transport.send(
                net.peer,
                rcce_net::packet_id::INVENTORY_UPDATE,
                &rcce_client::net::inv_drop_packet(slot, 1),
                true,
            );
        }
    }

    /// Execute a chosen context-menu action against `rid` (TGT-3). Interact→
    /// P_RightClick (runs the NPC Main script), Examine→P_Examine, Trade→P_Trade,
    /// Attack→engage the auto-attack loop (CBT-1, no one-shot send).
    fn exec_menu_action(&mut self, action: MenuAction, rid: u16) {
        use rcce_net::packet_id;
        if action == MenuAction::Attack {
            self.target = Some(rid);
            self.attacking = true;
            return;
        }
        let Some(net) = self.net.as_mut() else { return };
        let (ptype, payload) = match action {
            MenuAction::Interact => (packet_id::RIGHT_CLICK, rcce_client::net::right_click_packet(rid)),
            MenuAction::Examine => (packet_id::EXAMINE, rcce_client::net::examine_packet(rid)),
            MenuAction::Trade => (packet_id::TRADE, rcce_client::net::examine_packet(rid)),
            MenuAction::Attack => return, // engaged above
        };
        net.transport.send(net.peer, ptype, &payload, true);
    }

    /// Right-click in the inventory: open the item context menu over the slot
    /// under the cursor (if it holds an item). Returns true if a menu opened, so
    /// the caller skips the camera grab. Closes any open menu when re-clicked off.
    fn try_open_item_menu(&mut self) -> bool {
        if !self.show_inventory {
            return false;
        }
        let Some(gfx) = self.gfx.as_ref() else { return false };
        let (sw, sh) = (gfx.config.width as f32, gfx.config.height as f32);
        let (cx, cy) = self.cursor;
        let slot = self
            .store
            .as_ref()
            .and_then(|s| s.interface())
            .and_then(|iface| inventory_slot_at(cx, cy, iface.inventory_window, &iface.inventory_buttons, sw, sh));
        let Some(slot) = slot else { return false };
        let item = self
            .net
            .as_ref()
            .and_then(|n| n.world.me_inventory.values().find(|it| it.slot == slot as u8))
            .map(|it| (it.item_id, it.amount));
        let Some((item_id, amount)) = item else { return false };
        let equippable = self.store.as_ref().and_then(|s| s.item_equip_slot(item_id)).is_some();
        self.item_menu = Some(ItemMenu::build(slot as u8, equippable, amount > 1, cx, cy, sw, sh));
        true
    }

    /// Execute an item context-menu action on inventory slot `slot`.
    fn exec_item_action(&mut self, action: ItemAction, slot: u8) {
        let item_id = self.net.as_ref().and_then(|n| n.world.me_inventory.values().find(|it| it.slot == slot).map(|it| it.item_id));
        let Some(item_id) = item_id else { return };
        match action {
            ItemAction::Use => {
                let (item_type, image_id) = self
                    .store
                    .as_ref()
                    .and_then(|s| s.item_def(item_id))
                    .map(|d| (d.item_type, d.image_id))
                    .unwrap_or((0, -1));
                let edible = item_type == 4 || item_type == 5;
                let target = self.target;
                if let Some(net) = self.net.as_mut() {
                    if edible {
                        net.transport.send(net.peer, rcce_net::packet_id::EAT_ITEM, &rcce_client::net::eat_item_packet(slot, 1), true);
                    } else {
                        net.transport.send(net.peer, rcce_net::packet_id::ITEM_SCRIPT, &rcce_client::net::item_script_packet(slot, target), true);
                    }
                }
                if item_type == 6 && image_id >= 0 {
                    self.image_window = Some(image_id as u16);
                }
            }
            ItemAction::Equip => {
                if let Some(dest) = self.store.as_ref().and_then(|s| s.item_equip_slot(item_id)) {
                    let rid = self.net.as_ref().map(|n| n.world.my_runtime_id).unwrap_or(0);
                    if let Some(net) = self.net.as_mut() {
                        net.transport.send(net.peer, rcce_net::packet_id::INVENTORY_UPDATE, &rcce_client::net::inv_move_packet(rid, slot, dest, 0, true), true);
                    }
                }
            }
            ItemAction::DropAll => {
                let amount = self.net.as_ref().and_then(|n| n.world.me_inventory.values().find(|it| it.slot == slot)).map(|it| it.amount.max(1)).unwrap_or(1);
                if let Some(net) = self.net.as_mut() {
                    net.transport.send(net.peer, rcce_net::packet_id::INVENTORY_UPDATE, &rcce_client::net::inv_drop_packet(slot, amount), true);
                }
            }
            ItemAction::Drop => {
                let amount = self.net.as_ref().and_then(|n| n.world.me_inventory.values().find(|it| it.slot == slot)).map(|it| it.amount.max(1)).unwrap_or(1);
                if amount > 1 {
                    // Stack → ask how many to drop.
                    self.qty_prompt = Some(QtyPrompt { slot, item_id, max: amount, qty: 1, action: QtyAction::Drop });
                } else if let Some(net) = self.net.as_mut() {
                    net.transport.send(net.peer, rcce_net::packet_id::INVENTORY_UPDATE, &rcce_client::net::inv_drop_packet(slot, 1), true);
                }
            }
        }
    }

    /// World click: select the living actor whose projected position is nearest
    /// the cursor (within a pixel radius) as the target highlight. Uses the
    /// cached view-projection from the last rendered frame. No-op without a
    /// network world. The 'R'/'X' keys then interact with / examine the target.
    /// Send a pickup request for dropped-item `handle`, targeting the first free
    /// backpack slot computed from the LIVE inventory (`me_inventory`) — not the
    /// stale login snapshot, which went wrong after the first loot.
    fn pickup_item(&mut self, handle: u32) {
        let occupied: std::collections::HashSet<u8> = self
            .net
            .as_ref()
            .map(|n| n.world.me_inventory.keys().copied().collect())
            .unwrap_or_default();
        let slot = first_free_backpack_slot(&occupied);
        if let Some(net) = self.net.as_mut() {
            net.transport.send(
                net.peer,
                rcce_net::packet_id::INVENTORY_UPDATE,
                &rcce_client::net::pickup_packet(handle, slot),
                true,
            );
        }
    }

    fn world_pick(&mut self, sw: f32, sh: f32, cx: f32, cy: f32) {
        const PICK_RADIUS: f32 = 48.0;
        // Project each actor at the SAME height the body is rendered: feet on the
        // sampled terrain height under its X/Z (see build_actors seating), plus a
        // chest offset. The raw server `a.y` is only a stale spawn/collision-pivot
        // height — P_StandardUpdate omits Y — so projecting `a.y + 3.0` put the
        // pick target above/below the visible body and clicks fell through to
        // click-to-move. Sampling the terrain keeps the pick aligned with the body.
        let hf = self.height_field.as_ref();
        let pick = self.net.as_ref().and_then(|net| {
            let actors: Vec<(u16, [f32; 3])> = net
                .world
                .actors
                .values()
                .filter(|a| a.alive)
                .map(|a| {
                    let gy = hf.and_then(|h| h.height_at(a.x, a.z)).unwrap_or(a.y);
                    (a.runtime_id, [a.x, gy + 3.0, a.z])
                })
                .collect();
            actor_at(cx, cy, &actors, &self.vp, sw, sh, PICK_RADIUS)
        });
        if let Some(rid) = pick {
            let now = Instant::now();
            let double = self.last_click_rid == Some(rid)
                && now.duration_since(self.last_click).as_millis() < 400;
            self.last_click = now;
            self.last_click_rid = Some(rid);
            self.target = Some(rid);
            if double {
                // Double-click an actor (MOVE-6): run toward it, then quick
                // Interact (skipping the menu).
                if let Some(pos) = self.net.as_ref().and_then(|n| n.world.actors.get(&rid)).map(|a| [a.x, a.z]) {
                    self.move_target = Some(pos);
                    self.move_running = true;
                }
                self.context_menu = None;
                self.exec_menu_action(MenuAction::Interact, rid);
            } else {
                // Single click = select + open the "Actions" menu at the cursor.
                let is_player = self
                    .net
                    .as_ref()
                    .and_then(|n| n.world.actors.get(&rid))
                    .map(|a| a.is_player)
                    .unwrap_or(false);
                self.context_menu = Some(ContextMenu::build(rid, is_player, cx, cy, sw, sh));
            }
            return;
        }
        // No actor under the cursor → a dropped item under it (and in pickup range)
        // is looted directly, so you can target a specific pile instead of only the
        // nearest [E]. Otherwise fall through to click-to-move.
        let item_pick = self.net.as_ref().and_then(|net| {
            let (mx, mz) = (net.world.me_x, net.world.me_z);
            let mut best: Option<(u32, f32)> = None;
            for d in net.world.dropped_items.values() {
                if (d.x - mx).powi(2) + (d.z - mz).powi(2) >= 60.0 * 60.0 {
                    continue; // out of pickup range
                }
                if let Some((px, py)) = rcce_render::project(&self.vp, [d.x, d.y + 1.2, d.z], sw, sh) {
                    let sd = (px - cx).powi(2) + (py - cy).powi(2);
                    if sd < PICK_RADIUS * PICK_RADIUS && best.map(|(_, b)| sd < b).unwrap_or(true) {
                        best = Some((d.handle, sd));
                    }
                }
            }
            best.map(|(h, _)| h)
        });
        if let Some(h) = item_pick {
            self.pickup_item(h);
            return;
        }
        {
            // No actor under the cursor → click-to-move: walk to the ground
            // point the camera ray hits at the player's feet height (MOVE-5).
            // A double-click runs there instead (MOVE-6). A manual move also
            // breaks off any auto-attack.
            self.attacking = false;
            let now = Instant::now();
            let dt = now.duration_since(self.last_ground_click).as_millis();
            let dist = ((cx - self.last_ground_pos[0]).powi(2) + (cy - self.last_ground_pos[1]).powi(2)).sqrt();
            self.move_running = is_double_click(dt, dist);
            self.last_ground_click = now;
            self.last_ground_pos = [cx, cy];
            let start_y = self
                .net
                .as_ref()
                .and_then(|n| self.height_field.as_ref().and_then(|h| h.height_at(n.world.me_render_x, n.world.me_render_z)))
                .unwrap_or(self.ground_y);
            if let Some(g) = unproject_terrain(&self.vp, sw, sh, cx, cy, self.height_field.as_ref(), start_y) {
                self.move_target = Some(g);
            }
        }
    }

    /// Enable/disable mouse-look: grab + hide the cursor (Locked, falling back to
    /// Confined) when on; release it when off.
    fn set_mouse_look(&mut self, on: bool) {
        self.mouse_look = on;
        let Some(w) = self.window.as_ref() else { return };
        if on {
            if w.set_cursor_grab(CursorGrabMode::Locked).is_err() {
                let _ = w.set_cursor_grab(CursorGrabMode::Confined);
            }
            w.set_cursor_visible(false);
        } else {
            let _ = w.set_cursor_grab(CursorGrabMode::None);
            w.set_cursor_visible(true);
        }
    }

    /// Sorted runtime-ids of every living non-player actor — the cycle-target
    /// candidate set (TGT-7). Stable order so `next_target` wraps predictably.
    fn living_npc_rids(&self) -> Vec<u16> {
        let mut v: Vec<u16> = match self.net.as_ref() {
            Some(net) => net
                .world
                .actors
                .values()
                .filter(|a| a.alive && !a.is_player)
                .map(|a| a.runtime_id)
                .collect(),
            None => Vec::new(),
        };
        v.sort_unstable();
        v
    }

    /// Render the login / character-select screen: a slowly-orbiting view of the
    /// Advance every emitter and upload this frame's billboards (preview path —
    /// no other `self` borrow is live, so a `&mut self` method is fine).
    fn tick_particles(&mut self, eye: [f32; 3], target: [f32; 3], dt: f32) {
        let batches = particle_batches(&mut self.emitters, eye, target, dt);
        if let (Some(gfx), Some(view)) = (self.gfx.as_ref(), self.view.as_mut()) {
            view.set_particles(&gfx.device, &gfx.queue, &batches);
        }
    }

    /// Headless zone preview (`RCCE_VIEWZONE`): render the startup-loaded gameplay
    /// zone directly with a free camera — no menu UI, no `Set.b3d` swap, no server.
    /// Camera: `RCCE_CAMAT="x,y,z"` target (default zone centre), `RCCE_CAMYAW`
    /// (default slow auto-orbit) / `RCCE_CAMPITCH` / `RCCE_CAMDIST`. Reuses the
    /// in-world `view.render` path so terrain/scenery/water look exactly as in
    /// game. Pairs with the `gen-test-zone` bin to verify area rendering without a
    /// real fork's data or a running server.
    fn render_zone_preview(&mut self) {
        // RCCE_TIME pins the animation clock (sky/cloud/star pan, particle warmup)
        // so separate preview runs are byte-deterministic — needed to A/B animated
        // features (e.g. stars on/off) without the wall-clock sky drift as noise.
        let elapsed = std::env::var("RCCE_TIME")
            .ok()
            .and_then(|s| s.trim().parse::<f32>().ok())
            .unwrap_or_else(|| self.start.elapsed().as_secs_f32());
        let (w, h) = match self.gfx.as_ref() {
            Some(g) => (g.config.width, g.config.height),
            None => return,
        };
        let (sw, sh) = (w as f32, h.max(1) as f32);
        let envf = |k: &str, d: f32| std::env::var(k).ok().and_then(|s| s.trim().parse::<f32>().ok()).unwrap_or(d);
        let target = std::env::var("RCCE_CAMAT")
            .ok()
            .and_then(|s| {
                let p: Vec<f32> = s.split(',').filter_map(|t| t.trim().parse().ok()).collect();
                (p.len() == 3).then(|| [p[0], p[1], p[2]])
            })
            .unwrap_or(self.center);
        let dist = envf("RCCE_CAMDIST", self.span * 0.6);
        let pitch = envf("RCCE_CAMPITCH", 0.5);
        let yaw = envf("RCCE_CAMYAW", elapsed * 0.2);
        let (sp, cp) = pitch.sin_cos();
        let (sy, cy) = yaw.sin_cos();
        let eye = [target[0] + dist * sy * cp, target[1] + dist * sp, target[2] + dist * cy * cp];
        let vp = rcce_render::view_proj(eye, target, sw / sh);

        // Water planes (uploaded separately from the static scene, as in-world).
        if !self.water_planes.is_empty() {
            if let (Some(gfx), Some(view)) = (self.gfx.as_ref(), self.view.as_mut()) {
                // Scroll the UV with the (pinnable) clock so the surface + Fresnel
                // ripples animate in the preview like in-world (deterministic under
                // RCCE_TIME). Matches the in-world WATER_SCROLL_U/V drift.
                let scroll = [
                    (WATER_SCROLL_U * elapsed).rem_euclid(1.0),
                    (WATER_SCROLL_V * elapsed).rem_euclid(1.0),
                ];
                let models: Vec<B3dModel> = self.water_planes.iter().map(|(w, _)| water_quad(w, scroll)).collect();
                let texs: Vec<Vec<Option<Image>>> =
                    self.water_planes.iter().map(|(_, img)| vec![Some(img.clone())]).collect();
                let instances: Vec<SceneInstance> = self
                    .water_planes
                    .iter()
                    .enumerate()
                    .map(|(i, (w, _))| SceneInstance {
                        model: &models[i],
                        textures: &texs[i][..],
                        lightmaps: &[],
                        translation: w.pos,
                        rot: [0.0, 0.0, 0.0],
                        scale: [w.scale_x, 1.0, w.scale_z],
                        color: [1.0, 1.0, 1.0],
                    })
                    .collect();
                view.set_water(&gfx.device, &gfx.queue, &instances);
            }
        }
        // RCCE_TESTLIGHT="x,y,z,range,r,g,b" injects a point light for verifying
        // the dynamic-light shader without needing a LightModels mesh in the zone.
        if let Ok(s) = std::env::var("RCCE_TESTLIGHT") {
            let p: Vec<f32> = s.split(',').filter_map(|t| t.trim().parse().ok()).collect();
            if p.len() == 7 {
                if let Some(view) = self.view.as_mut() {
                    view.set_lights(&[rcce_render::gpu::PointLight {
                        pos: [p[0], p[1], p[2]],
                        range: p[3],
                        color: [p[4], p[5], p[6]],
                    }]);
                }
            }
        }
        // Ambient floor (RCCE_AMBIENT overrides) — lower it to make sun shadows
        // read clearly when verifying.
        let af = envf("RCCE_AMBIENT", 0.55);
        // Day/night: pin the cosmetic phase with RCCE_PHASE (0=midnight, 0.25=dawn,
        // 0.5=noon, 0.75=dusk), or free-run via RCCE_DAYNIGHT_SECS; default noon so
        // existing day renders are unchanged. Modulates fog + ambient and drives the
        // night-stars factor — the same path the in-world render uses, so the
        // preview can verify night/stars/atmosphere (was hardcoded to full day).
        let phase = std::env::var("RCCE_PHASE")
            .ok()
            .and_then(|s| s.trim().parse::<f32>().ok())
            .unwrap_or_else(|| match std::env::var("RCCE_DAYNIGHT_SECS").ok().and_then(|s| s.parse::<f32>().ok()) {
                Some(cycle) => rcce_client::daynight::phase_at(elapsed, cycle),
                None => 0.5,
            });
        let sky_mod = rcce_client::daynight::daynight(phase);
        let ambient = rcce_client::daynight::modulate([af, af, af], &sky_mod);
        let night = rcce_client::daynight::night_factor(phase);
        // Underwater (Blitz CameraUnderwater): tint fog to the water colour when the
        // free camera dips below a water plane, so the preview can verify the murk +
        // wash headlessly (the wash itself is composited onto the shot below).
        let underwater = underwater_color(&self.water_planes, eye);
        let fog = match underwater {
            Some(wc) => [wc[0] * 0.7, wc[1] * 0.7, wc[2] * 0.7],
            None => rcce_client::daynight::modulate(self.fog_color, &sky_mod),
        };
        // Clear/horizon = the modulated fog colour so the sky fades into it (and
        // darkens at night) instead of a fixed daytime blue.
        let clear = wgpu::Color { r: fog[0] as f64, g: fog[1] as f64, b: fog[2] as f64, a: 1.0 };
        // Sun direction: RCCE_SUNDIR="x,y,z" overrides; else the day/night phase
        // drives it (sun moves across the sky → shadows rotate) when RCCE_PHASE /
        // RCCE_DAYNIGHT is set; else the zone's authored light.
        let sun = std::env::var("RCCE_SUNDIR")
            .ok()
            .and_then(|s| {
                let p: Vec<f32> = s.split(',').filter_map(|t| t.trim().parse().ok()).collect();
                (p.len() == 3).then(|| [p[0], p[1], p[2]])
            })
            .unwrap_or_else(|| {
                if std::env::var_os("RCCE_PHASE").is_some() || std::env::var_os("RCCE_DAYNIGHT_SECS").is_some() {
                    rcce_client::daynight::sun_dir(phase)
                } else {
                    self.light_dir
                }
            });
        let (fn_, ff_) = match underwater {
            Some(_) => (2.0, 60.0), // murky short-range underwater view distance
            None => (self.fog_near.max(500.0), self.fog_far.max(40000.0)),
        };

        // Shadow-caster verification fixture (RCCE_TESTBOX=skinned|cpu): drop a tall
        // box at the camera target, either as a GPU-skinned actor (set_skinned —
        // the new shadow path) or as a CPU/dynamic caster (set_dynamic — the
        // known-good path). Same world geometry ⇒ the cast shadow must match; if
        // the skinned variant casts none, the GPU-skin shadow path is broken.
        if let Ok(mode) = std::env::var("RCCE_TESTBOX") {
            if let (Some(gfx), Some(view)) = (self.gfx.as_ref(), self.view.as_mut()) {
                let tex = [Some(rcce_data::Image { width: 1, height: 1, rgba: vec![255, 255, 255, 255] })];
                let pos = [target[0], envf("RCCE_BOXY", 0.0), target[2]];
                if mode == "skinned" {
                    let m = test_box_model(true);
                    let xf = glam::Mat4::from_translation(glam::Vec3::from(pos)).to_cols_array();
                    let inst = [rcce_render::SkinnedInstance {
                        key: "testbox",
                        model: &m,
                        textures: &tex[..],
                        frame: None,
                        transform: xf,
                        color: [0.8, 0.4, 0.4],
                    }];
                    view.set_skinned(&gfx.device, &gfx.queue, &inst);
                } else {
                    let m = test_box_model(false);
                    let inst = [SceneInstance {
                        model: &m,
                        textures: &tex[..],
                        lightmaps: &[],
                        translation: pos,
                        rot: [0.0, 0.0, 0.0],
                        scale: [1.0, 1.0, 1.0],
                        color: [0.8, 0.4, 0.4],
                    }];
                    view.set_dynamic(&gfx.device, &gfx.queue, &inst, &["testbox".to_string()]);
                }
            }
        }

        // RCCE_LOOTPREVIEW: drop the fallback Loot Bag mesh on the ground at the
        // camera target so the dropped-loot 3D render (DROP-1) is verifiable
        // headlessly — the in-world path needs a live server + an actual item drop.
        // Optional RCCE_LOOTITEM=<id> renders that item's own world mesh instead.
        if std::env::var_os("RCCE_LOOTPREVIEW").is_some() {
            let item = std::env::var("RCCE_LOOTITEM").ok().and_then(|s| s.parse::<u16>().ok());
            let loaded = if let Some(store) = self.store.as_mut() {
                match item.and_then(|id| store.gear_attachment(id)) {
                    Some(att) => Some((att.model, att.textures, att.scale * 0.05)),
                    None => store.mesh_by_path("Loot Bag.b3d").map(|(m, t, _)| (m, t, 0.075)),
                }
            } else {
                None
            };
            if let (Some((model, texs, s)), Some(gfx), Some(view)) = (loaded, self.gfx.as_ref(), self.view.as_mut()) {
                let (min, _) = model.bounds();
                let g = self
                    .height_field
                    .as_ref()
                    .and_then(|h| h.height_at(target[0], target[2]))
                    .unwrap_or(target[1]);
                let ty = g - min[1] * s;
                let inst = [SceneInstance {
                    model: &model,
                    textures: &texs[..],
                    lightmaps: &[],
                    translation: [target[0], ty, target[2]],
                    rot: [0.0, 0.0, 0.0],
                    scale: [s, s, s],
                    color: [1.0, 1.0, 1.0],
                }];
                view.set_dynamic(&gfx.device, &gfx.queue, &inst, &["loot:preview".to_string()]);
            }
        }

        // Particles: warm up the emitters so a single-frame preview shows a full
        // plume, then build this frame's billboards facing the preview camera.
        if !self.emitters.is_empty() {
            for _ in 0..180 {
                for (e, _) in self.emitters.iter_mut() {
                    e.update(1.0);
                }
            }
            self.tick_particles(eye, target, 1.0 / 60.0);
        }

        // RCCE_PROJPREVIEW: synthesize a projectile flying away from the camera so
        // the new world-space glowing-orb + trail render is verifiable headlessly
        // (the in-world path needs a live server). Builds the same projectile batch
        // the in-world path does and uploads it (merged with any zone emitters).
        if std::env::var_os("RCCE_PROJPREVIEW").is_some() {
            let normd = |v: [f32; 3]| {
                let l = (v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).sqrt().max(1e-4);
                [v[0] / l, v[1] / l, v[2] / l]
            };
            let dir = normd([target[0] - eye[0], target[1] - eye[1], target[2] - eye[2]]);
            // Place it part-way to the target, flying onward (so the trail points
            // back toward the camera).
            let pos = [eye[0] + dir[0] * 40.0, eye[1] + dir[1] * 40.0 + 6.0, eye[2] + dir[2] * 40.0];
            let pr = rcce_client::world::Projectile {
                x: pos[0],
                y: pos[1],
                z: pos[2],
                target_rid: 0,
                tx: pos[0] + dir[0] * 100.0,
                ty: pos[1] + dir[1] * 100.0,
                tz: pos[2] + dir[2] * 100.0,
                homing: false,
                speed: 40.0,
            };
            let mut batches = particle_batches(&mut self.emitters, eye, target, 1.0 / 60.0);
            let mut verts = Vec::new();
            projectile_billboards(std::slice::from_ref(&pr), eye, target, &mut verts);
            batches.push((Some(projectile_glow_image()), true, verts));
            if let (Some(gfx), Some(view)) = (self.gfx.as_ref(), self.view.as_mut()) {
                view.set_particles(&gfx.device, &gfx.queue, &batches);
            }
        }

        let shot = std::env::var("RCCE_SHOT").ok().filter(|_| {
            let want = std::env::var("RCCE_SHOT_FRAME").ok().and_then(|s| s.parse::<u64>().ok()).unwrap_or(45);
            self.frames + 1 >= want
        });
        let (Some(gfx), Some(view)) = (self.gfx.as_ref(), self.view.as_ref()) else {
            return;
        };
        if let Some(path) = shot {
            let tex = gfx.device.create_texture(&wgpu::TextureDescriptor {
                label: Some("zone-shot"),
                size: wgpu::Extent3d { width: w, height: h, depth_or_array_layers: 1 },
                mip_level_count: 1,
                sample_count: 1,
                dimension: wgpu::TextureDimension::D2,
                format: gfx.config.format,
                usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::COPY_SRC,
                view_formats: &[],
            });
            let oview = tex.create_view(&Default::default());
            view.render(&gfx.device, &gfx.queue, &oview, vp, eye, fog, fn_, ff_, ambient, sun, clear, yaw, elapsed, night, target);
            // Composite the underwater wash onto the shot (the in-world path draws
            // this in the HUD overlay), so the headless preview shows the full look.
            if let Some(wc) = underwater {
                if let Some(overlay) = self.overlay.as_mut() {
                    overlay.rect(0.0, 0.0, w as f32, h as f32, [wc[0], wc[1], wc[2], 0.6]);
                    overlay.render(&gfx.device, &gfx.queue, &oview, w as f32, h as f32);
                }
            }
            match rcce_render::save_texture_png(&gfx.device, &gfx.queue, &tex, w, h, gfx.config.format, &path) {
                Ok(()) => println!("[client-window] zone preview -> {path}"),
                Err(e) => eprintln!("[client-window] zone preview failed: {e}"),
            }
            std::process::exit(0);
        }
        let frame = match gfx.surface.get_current_texture() {
            Ok(f) => f,
            Err(_) => {
                gfx.surface.configure(&gfx.device, &gfx.config);
                match gfx.surface.get_current_texture() {
                    Ok(f) => f,
                    Err(_) => return,
                }
            }
        };
        let tview = frame.texture.create_view(&Default::default());
        view.render(&gfx.device, &gfx.queue, &tview, vp, eye, fog, fn_, ff_, ambient, sun, clear, yaw, elapsed, night, target);
        frame.present();
        self.frames += 1;
    }

    /// loaded zone as a backdrop, with the menu UI drawn over it.
    fn render_menu(&mut self) {
        let elapsed = self.start.elapsed().as_secs_f32();
        // MENU-10: loop Data/Music/Menu.ogg while in the menu, once. Skips
        // gracefully if the starter project doesn't ship the file. Stopped on
        // enter-world (see enter_selected) so zone music takes over.
        if !self.menu_music_on {
            if let (Some(store), Some(audio)) = (self.store.as_ref(), self.audio.as_mut()) {
                if let Some(path) = store.menu_music_path() {
                    if audio.play_music_looped(&path, 0.5, MENU_MUSIC_ID) {
                        println!("[audio] menu music: {}", path.display());
                    }
                }
            }
            // Set the guard regardless so we don't retry the (possibly missing)
            // file every frame; a present file is now looping, an absent one stays
            // silent until the next session.
            self.menu_music_on = true;
        }
        // Headless options-screen self-test: force Mode::Options for a capture.
        // No-op unless RCCE_OPTIONSTEST=<frame> set.
        if let Ok(v) = std::env::var("RCCE_OPTIONSTEST") {
            if let Ok(at) = v.parse::<u64>() {
                if self.frames >= at {
                    self.mode = Mode::Options;
                }
            }
        }
        // Headless controls-reference self-test: force Mode::Controls for a shot.
        if let Ok(v) = std::env::var("RCCE_CONTROLSTEST") {
            if let Ok(at) = v.parse::<u64>() {
                if self.frames >= at {
                    self.mode = Mode::Controls;
                }
            }
        }
        // MENU-13: auto-accept the EULA under any headless hook so the existing
        // login/world capture paths aren't blocked by the new gate.
        if self.mode == Mode::Eula
            && (std::env::var_os("RCCE_AUTOSUBMIT").is_some()
                || std::env::var_os("RCCE_AUTOENTER").is_some()
                || std::env::var_os("RCCE_AUTOLOGIN").is_some())
        {
            self.mode = Mode::Login;
        }
        // Headless test hook: enter the world from character select on the first
        // menu frame (the actual menu->world path), so it's verifiable end-to-end.
        if self.frames == 0
            && std::env::var_os("RCCE_AUTOENTER").is_some()
            && self.mode == Mode::CharSelect
            && !self.chars.is_empty()
            && self.creating.is_none()
        {
            self.enter_selected();
            if self.mode == Mode::InWorld {
                return;
            }
        }
        let (w, h) = match self.gfx.as_ref() {
            Some(g) => (g.config.width, g.config.height),
            None => return,
        };
        let (sw, sh) = (w as f32, h.max(1) as f32);

        // Live-tunable menu params (set scale/height + camera framing). Baked
        // defaults in the MENU_* consts; RCCE_* envs override for visual tuning.
        let envf = |k: &str, d: f32| {
            std::env::var(k).ok().and_then(|s| s.parse::<f32>().ok()).unwrap_or(d)
        };

        // Dedicated 3D menu scene (MENU-SCENE): the selected character posed in
        // the Set.b3d diorama playing Idle, framed by the menu camera. Mirrors
        // MainMenu.bb (char at world (30, _, 100)). The Y lifts the center-anchored
        // body so its feet sit on the rug (no terrain height field in the menu, so
        // build_actors centers it at Y=0 — RCCE_CHARY / MENU_CHAR_Y compensates).
        let char_anchor = [30.0f32, envf("RCCE_CHARY", MENU_CHAR_Y), 100.0];
        if let (Some(gfx), Some(view), Some(store)) =
            (self.gfx.as_ref(), self.view.as_mut(), self.store.as_mut())
        {
            // Replace the startup gameplay-zone geometry once with the menu
            // backdrop, and force a fresh zone reload when the player enters the
            // world (loaded_zone cleared).
            if !self.menu_scene_init {
                // MENU-SET: the character-creation "set" — the same
                // Data\Meshes\Character Set\Set.b3d the Blitz menu loads in
                // EULAScreen and keeps behind every menu screen (EULA / login /
                // char select). Blitz: PositionEntity Set, -210,-35,-145 +
                // ScaleEntity 30 (MainMenu.bb:2924), with the preview char seated
                // at (30,-35,100) (MainMenu.bb:1727). The Rust menu anchors the
                // char at (30,0,100), i.e. the Blitz frame raised +35 in Y, so the
                // set is positioned +35 in Y too to keep the char on its floor.
                // Falls back to the bare void if the asset is missing/unparseable.
                match store.mesh_by_path("Character Set/Set.b3d") {
                    Some((model, textures, lightmaps)) => {
                        // Origin derived from the scale so the character stays on
                        // the rug (model-space MENU_SET_RUG) at any scale:
                        // origin = char_anchor - scale * RUG.
                        let s = envf("RCCE_SETSCALE", MENU_SET_SCALE);
                        let oy = envf("RCCE_SETY", MENU_SET_Y);
                        let inst = SceneInstance {
                            model: &model,
                            textures: &textures[..],
                            lightmaps: &lightmaps[..],
                            translation: [
                                char_anchor[0] - s * MENU_SET_RUG[0],
                                oy,
                                char_anchor[2] - s * MENU_SET_RUG[2],
                            ],
                            rot: [0.0, 0.0, 0.0],
                            scale: [s, s, s],
                            color: [1.0, 1.0, 1.0],
                        };
                        // NAN ground_y: skip the green terrain ground plane — the
                        // set carries its own floor; the plane only showed as a
                        // green void past the set's floor edge.
                        view.set_scene(&gfx.device, &gfx.queue, std::slice::from_ref(&inst), f32::NAN);
                        // Build a height field from the set's floor so the
                        // character seats its feet ON the rug (height_at(char x,z)),
                        // instead of guessing an anchor Y — the set's floor is well
                        // below Y=0 (mesh min ~ -1.3 world), so a fixed anchor sank
                        // the feet through it. Reuses self.height_field (the world
                        // overwrites it on enter; the menu doesn't otherwise use it).
                        {
                            use glam::Vec3;
                            let sv = Vec3::splat(s);
                            let tv = Vec3::new(
                                char_anchor[0] - s * MENU_SET_RUG[0],
                                oy,
                                char_anchor[2] - s * MENU_SET_RUG[2],
                            );
                            let mut tris: Vec<[Vec3; 3]> = Vec::new();
                            for mesh in &model.meshes {
                                if mesh.texture_flag & 4 != 0 {
                                    continue; // masked = see-through, not floor
                                }
                                let w = |i: u32| tv + Vec3::from(mesh.positions[i as usize]) * sv;
                                for tri in mesh.indices.chunks_exact(3) {
                                    let (a, b, c) = (w(tri[0]), w(tri[1]), w(tri[2]));
                                    if rcce_client::terrain::HeightField::is_ground(a, b, c) {
                                        tris.push([a, b, c]);
                                    }
                                }
                            }
                            // The rug is the LOWEST ground surface under the
                            // character (the ceiling vault / tables are higher, and
                            // height_at returns the highest). Seat on a flat field
                            // at that Y so the feet rest on the rug.
                            let full = rcce_client::terrain::HeightField::build(tris, 2.0);
                            let rug_y = full.lowest_at(char_anchor[0], char_anchor[2]);
                            println!("[client-window] menu floor under character: {rug_y:?}");
                            self.height_field = Some(match rug_y {
                                Some(y) => rcce_client::terrain::HeightField::flat(y),
                                None => full,
                            });
                        }
                        let n_lm = lightmaps.iter().filter(|l| l.is_some()).count();
                        println!("[client-window] menu set: {} meshes, scale {s}, {n_lm} lightmapped", model.meshes.len());
                    }
                    None => {
                        view.set_scene(&gfx.device, &gfx.queue, &[], 0.0);
                        println!("[client-window] menu set: Set.b3d unavailable, bare void");
                    }
                }
                self.loaded_zone = String::new();
                self.menu_scene_init = true;
            }
            // The highlighted character (CharSelect only); Login shows the void.
            let sel = if self.mode == Mode::CharSelect {
                self.chars.get(self.char_sel).cloned()
            } else {
                None
            };
            if let Some(c) = sel {
                let mut mw = World::default();
                mw.my_runtime_id = 1;
                mw.me_gender = c.gender;
                mw.me_face_tex = c.face;
                mw.me_body_tex = c.body;
                mw.me_hair = c.hair;
                mw.me_beard = c.beard;
                mw.me_x = char_anchor[0];
                mw.me_y = char_anchor[1];
                mw.me_z = char_anchor[2];
                // Static menu pose: render position = position (no interpolation).
                mw.me_render_x = char_anchor[0];
                mw.me_render_z = char_anchor[2];
                mw.me_render_init = true;
                mw.me_yaw = 0.0; // faces +Z; the camera circles it
                // Seat on the set's floor height field (built at scene init) so
                // the feet rest on the rug instead of a guessed anchor Y.
                let (models, textures, place, keys, skinned) =
                    build_actors(store, &mw, elapsed, self.gpu_skin, false, false, c.actor_id, false, false, 0.0, false, None, self.height_field.as_ref());
                let instances: Vec<SceneInstance> = place
                    .iter()
                    .map(|&(idx, t, r, color, s)| SceneInstance {
                        model: &models[idx],
                        textures: &textures[idx][..],
                        lightmaps: &[],
                        translation: t,
                        rot: r,
                        scale: s,
                        color,
                    })
                    .collect();
                view.set_dynamic(&gfx.device, &gfx.queue, &instances, &keys);
                if self.gpu_skin {
                    let sinst: Vec<rcce_render::SkinnedInstance> = skinned
                        .iter()
                        .map(|a| rcce_render::SkinnedInstance {
                            key: &a.key,
                            model: &a.model,
                            textures: &a.textures[..],
                            frame: a.frame,
                            transform: a.transform,
                            color: a.color,
                        })
                        .collect();
                    view.set_skinned(&gfx.device, &gfx.queue, &sinst);
                } else {
                    view.set_skinned(&gfx.device, &gfx.queue, &[]);
                }
            } else {
                view.set_dynamic(&gfx.device, &gfx.queue, &[], &[]);
                view.set_skinned(&gfx.device, &gfx.queue, &[]);
            }
        }

        // Camera framing the character against the menu set, matching Blitz
        // (MainMenu.bb:2023): the camera sits behind a pivot at the character's
        // chest, looks +Z *into* the furnished room (table / rug / column), and
        // strafes sideways so the character sits screen-right with the window on
        // the left. ang≈π looks into the room (not at the banner wall behind).
        // All five params are env-overridable (RCCE_MENU*) for live tuning.
        let ang = envf("RCCE_MENUANG", MENU_CAM_ANGLE);
        let dist = envf("RCCE_MENUDIST", MENU_CAM_DIST);
        let eye_h = envf("RCCE_MENUEYEH", MENU_CAM_EYE_H);
        let tgt_h = envf("RCCE_MENUTGTH", MENU_CAM_TGT_H);
        let lat = envf("RCCE_MENULAT", MENU_CAM_LAT);
        let target = [char_anchor[0] + lat, char_anchor[1] + tgt_h, char_anchor[2]];
        let eye = [
            char_anchor[0] + lat + dist * ang.sin(),
            char_anchor[1] + eye_h,
            char_anchor[2] + dist * ang.cos(),
        ];
        let vp = rcce_render::view_proj(eye, target, sw / sh);
        // Interior lighting. The flat 0.9 white ambient washed the room out; the
        // Set.b3d's real richness is a baked lightmap (cset_lightmap.png, the
        // brushes' 2nd texture slot) that the renderer doesn't yet sample — that
        // is a separate multitexture feature (see DELTA). Until then, approximate
        // depth with a lower, slightly warm ambient plus a frontal key light so
        // the character and walls aren't flat. Dark near-neutral background
        // (interior — no blue sky) so the void past the floor edge doesn't read as
        // a teal triangle; minimal fog since the room is small. All env-tunable.
        let bg = envf("RCCE_MENUBG", 0.05);
        let amb = envf("RCCE_MENUAMB", 0.62);
        let lx = envf("RCCE_MENULX", 0.25);
        let ly = envf("RCCE_MENULY", -0.5);
        let lz = envf("RCCE_MENULZ", 1.0);
        let fog = [bg, bg * 0.92, bg * 0.85];
        let clear = wgpu::Color { r: bg as f64, g: (bg * 0.92) as f64, b: (bg * 0.85) as f64, a: 1.0 };
        let menu_fog_near = 200.0f32;
        let menu_fog_far = 40000.0f32;
        let menu_ambient = [amb, amb * 0.95, amb * 0.85];
        let menu_light = [lx, ly, lz];

        // Build the overlay command list first (a `&mut self` call, so no other
        // borrow of `self` may be live), then render world + overlay together.
        self.draw_menu_overlay(elapsed, sw, sh);

        // Headless screenshot (RCCE_SHOT): render to an offscreen texture and
        // read it back, then exit — so the login / char-select screens are
        // verifiable without a visible window. `overlay.render` consumes the
        // command list, so this path skips the surface present.
        let shot = std::env::var("RCCE_SHOT").ok().filter(|_| {
            let want = std::env::var("RCCE_SHOT_FRAME").ok().and_then(|s| s.parse::<u64>().ok()).unwrap_or(45);
            self.frames + 1 >= want
        });

        let (Some(gfx), Some(view), Some(overlay)) =
            (self.gfx.as_ref(), self.view.as_ref(), self.overlay.as_mut())
        else {
            return;
        };
        if let Some(path) = shot {
            let tex = gfx.device.create_texture(&wgpu::TextureDescriptor {
                label: Some("menu-shot"),
                size: wgpu::Extent3d { width: w, height: h, depth_or_array_layers: 1 },
                mip_level_count: 1,
                sample_count: 1,
                dimension: wgpu::TextureDimension::D2,
                format: gfx.config.format,
                usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::COPY_SRC,
                view_formats: &[],
            });
            let oview = tex.create_view(&Default::default());
            // Far shadow centre → menu geometry projects out of the shadow region
            // (always lit): the Set.b3d keeps its baked-lightmap look, no dynamic
            // shadows from the frontal key light.
            view.render(&gfx.device, &gfx.queue, &oview, vp, eye, fog, menu_fog_near, menu_fog_far, menu_ambient, menu_light, clear, ang, elapsed, 0.0, [1.0e6, 0.0, 1.0e6]);
            overlay.render(&gfx.device, &gfx.queue, &oview, sw, sh);
            match rcce_render::save_texture_png(&gfx.device, &gfx.queue, &tex, w, h, gfx.config.format, &path) {
                Ok(()) => println!("[client-window] menu screenshot -> {path}"),
                Err(e) => eprintln!("[client-window] menu screenshot failed: {e}"),
            }
            self.shutdown_net();
            std::process::exit(0);
        }

        let frame = match gfx.surface.get_current_texture() {
            Ok(f) => f,
            Err(_) => {
                gfx.surface.configure(&gfx.device, &gfx.config);
                match gfx.surface.get_current_texture() {
                    Ok(f) => f,
                    Err(_) => return,
                }
            }
        };
        let tview = frame.texture.create_view(&Default::default());
        view.render(&gfx.device, &gfx.queue, &tview, vp, eye, fog, menu_fog_near, menu_fog_far, menu_ambient, menu_light, clear, ang, elapsed, 0.0, [1.0e6, 0.0, 1.0e6]);
        overlay.render(&gfx.device, &gfx.queue, &tview, sw, sh);
        frame.present();
        self.frames += 1;
        if let Some(win) = self.window.as_ref() {
            win.request_redraw();
        }
    }

    /// Populate the overlay's command list with the login / character-select UI
    /// (the panel, fields, character roster). Called before the world render so
    /// both the live window and the offscreen screenshot draw the same thing.
    fn draw_menu_overlay(&mut self, elapsed: f32, sw: f32, sh: f32) {
        let Some(overlay) = self.overlay.as_mut() else { return };
        overlay.clear();
        // MENU-SCENE-b: EULA / Login draw their dedicated WINDOW-FRAME image at
        // window size (NOT full-screen) — these are bordered window graphics with
        // a baked title bar, so the 3D menu scene shows around them. The game logo
        // sits above the login window. Options/Controls draw their own sized panel
        // (the project ships no dedicated frame art for them).
        // The login window is compact (Blitz frames it small with the logo above
        // and the 3D scene around); EULA is larger (it holds the license text).
        // Window rect (Login drops a little for the logo above it). Shared with
        // the click hit-test via menu_window_for so buttons line up with draws.
        let (wx, wy, ww, wh) = menu_window_for(self.mode, sw, sh);
        let frame = match self.mode {
            Mode::Eula => Some("EULA.PNG"),
            Mode::Login => Some("Login.PNG"),
            _ => None,
        };
        if let (Some(name), Some(gfx), Some(store)) = (frame, self.gfx.as_ref(), self.store.as_ref()) {
            let key = format!("menuwin:{name}");
            if !overlay.has_texture(&key) {
                if let Some(im) = store.menu_backdrop_path(name).and_then(|p| rcce_data::texture::load(&p)) {
                    overlay.register_texture(&gfx.device, &gfx.queue, &key, im.width, im.height, &im.rgba);
                }
            }
            if overlay.has_texture(&key) {
                overlay.image(wx, wy, ww, wh, &key, [1.0, 1.0, 1.0, 1.0]);
            }
            // Game logo (256×128, 2:1) centred above the login window (MENU-1).
            if self.mode == Mode::Login {
                let lkey = "menu:logo";
                if !overlay.has_texture(lkey) {
                    if let Some(mut im) = store.menu_logo_path().and_then(|p| rcce_data::texture::load(&p)) {
                        // Menu Logo.bmp is a 24-bit sprite with a black mask
                        // (Blitz LoadSprite flag 4 = masked); key out near-black
                        // pixels to alpha so the background is transparent.
                        for px in im.rgba.chunks_exact_mut(4) {
                            if px[0] < 16 && px[1] < 16 && px[2] < 16 {
                                px[3] = 0;
                            }
                        }
                        overlay.register_texture(&gfx.device, &gfx.queue, lkey, im.width, im.height, &im.rgba);
                    }
                }
                if overlay.has_texture(lkey) {
                    let lw = (ww * 0.72).min(sw * 0.46);
                    let lh = lw * 0.5;
                    let lx = (sw - lw) * 0.5;
                    let ly = (wy - lh - 6.0).max(4.0);
                    overlay.image(lx, ly, lw, lh, lkey, [1.0, 1.0, 1.0, 1.0]);
                }
            }
        }
        // MENU-2: mouse-clickable buttons drawn over the window using the shipped
        // B*.PNG sprites (hover swaps the U sprite for H). Drawn here (before the
        // per-mode content blocks, which `return` early) so it's reached by both
        // Login and CharSelect; the buttons sit in clear areas so draw order with
        // the fields/roster doesn't matter. Hit-tested in `menu_click`; keyboard
        // shortcuts remain fully functional. Falls back to a flat rect if a
        // project ships no button art.
        {
            let buttons =
                menu_buttons(self.mode, self.creating.is_some(), !self.chars.is_empty(), wx, wy, ww, wh);
            if let (Some(gfx), Some(store)) = (self.gfx.as_ref(), self.store.as_ref()) {
                let (cx, cy) = self.cursor;
                for b in &buttons {
                    let (bx, by, bw, bh) = b.rect;
                    let hover = cx >= bx && cx < bx + bw && cy >= by && cy < by + bh;
                    let file = format!("{}{}.PNG", b.sprite, if hover { "H" } else { "U" });
                    let key = format!("menubtn:{file}");
                    if !overlay.has_texture(&key) {
                        if let Some(im) =
                            store.menu_backdrop_path(&file).and_then(|p| rcce_data::texture::load(&p))
                        {
                            overlay.register_texture(
                                &gfx.device, &gfx.queue, &key, im.width, im.height, &im.rgba,
                            );
                        }
                    }
                    if overlay.has_texture(&key) {
                        overlay.image(bx, by, bw, bh, &key, [1.0, 1.0, 1.0, 1.0]);
                    } else {
                        overlay.rect(bx, by, bw, bh, [0.18, 0.22, 0.32, 0.92]);
                    }
                }
            }
        }
        // MENU-13: the EULA gate gets a full-screen license panel instead of the
        // login/character UI. Wrapped text + PageUp/PageDown scroll + Accept/Decline.
        if self.mode == Mode::Eula {
            let text = self.eula_text.clone().unwrap_or_default();
            // Text lives INSIDE the EULA.PNG window frame's body (below its baked
            // "License Agreement" title bar) — the frame is drawn above.
            let bx = wx + ww * 0.035;
            let body_top = wy + wh * 0.13;
            let body_bottom = wy + wh * 0.85;
            let body_w = ww * 0.93;
            let max_chars = (((body_w - 10.0) / 7.0) as usize).max(20);
            let mut lines: Vec<String> = Vec::new();
            for para in text.split('\n') {
                if para.trim().is_empty() {
                    lines.push(String::new());
                } else {
                    lines.extend(wrap_text(para, max_chars));
                }
            }
            let line_h = 15.0;
            let visible = ((body_bottom - body_top) / line_h).max(1.0) as usize;
            let start = self.eula_scroll.min(lines.len().saturating_sub(1));
            for (i, wl) in lines.iter().skip(start).take(visible).enumerate() {
                overlay.text(bx, body_top + i as f32 * line_h, 1.0, wl, [0.88, 0.89, 0.92, 1.0]);
            }
            if lines.len() > visible {
                let more = format!("[{}-{}/{} · PgUp/PgDn]", start + 1, (start + visible).min(lines.len()), lines.len());
                overlay.text(bx, wy + wh * 0.89, 0.85, &more, [0.55, 0.6, 0.7, 0.9]);
            }
            let prompt = "Accept (Enter)     Decline (Esc)";
            overlay.text_shadow(wx + ww - prompt.len() as f32 * 9.0 - 18.0, wy + wh * 0.89, 1.0, prompt, [0.5, 0.95, 0.5, 1.0]);
            return;
        }
        // Sound options screen (front-of-game shell): master volume bar + mute.
        if self.mode == Mode::Options {
            let (vol, muted) = self
                .audio
                .as_ref()
                .map(|a| (a.master_volume(), a.is_muted()))
                .unwrap_or((0.0, false));
            let (pw, ph) = ((sw * 0.46).clamp(420.0, 720.0), sh * 0.40);
            let (px, py) = ((sw - pw) * 0.5, (sh - ph) * 0.5);
            overlay.rect(px, py, pw, ph, [0.05, 0.06, 0.10, 0.92]);
            overlay.rect(px, py, pw, 3.0, [0.5, 0.55, 0.7, 0.95]);
            overlay.rect(px, py + ph - 3.0, pw, 3.0, [0.5, 0.55, 0.7, 0.95]);
            let title = "Sound Options";
            overlay.text_shadow(px + (pw - title.len() as f32 * 9.0 * 1.5) * 0.5, py + 18.0, 1.5, title, [0.95, 0.88, 0.55, 1.0]);
            let bx = px + 30.0;
            let bw = pw - 60.0;
            // Master volume label + bar + percentage.
            overlay.text(bx, py + 70.0, 1.2, "Master Volume", [0.75, 0.82, 0.95, 1.0]);
            let by = py + 92.0;
            overlay.rect(bx - 2.0, by - 2.0, bw + 4.0, 22.0 + 4.0, [0.0, 0.0, 0.0, 0.5]);
            overlay.bar(bx, by, bw, 22.0, vol, [0.35, 0.7, 1.0, 1.0]);
            overlay.text(bx + bw - 54.0, by + 3.0, 1.2, &format!("{:>3}%", (vol * 100.0).round() as i32), [1.0, 1.0, 1.0, 1.0]);
            // Mute state.
            let mute_col = if muted { [1.0, 0.5, 0.4, 1.0] } else { [0.6, 0.85, 0.6, 1.0] };
            overlay.text(bx, by + 44.0, 1.2, &format!("Mute: {}", if muted { "ON" } else { "off" }), mute_col);
            // Hints.
            overlay.text(bx, py + ph - 56.0, 1.0, "Left / Right  adjust volume", [0.6, 0.66, 0.8, 0.9]);
            overlay.text(bx, py + ph - 38.0, 1.0, "M  toggle mute     Tab  controls     Esc  back", [0.6, 0.66, 0.8, 0.9]);
            return;
        }
        // Keybind reference screen (read-only): two columns action | key.
        if self.mode == Mode::Controls {
            let (pw, ph) = ((sw * 0.6).clamp(520.0, 860.0), (sh * 0.84).min(KEYBINDS.len() as f32 * 22.0 + 110.0));
            let (px, py) = ((sw - pw) * 0.5, (sh - ph) * 0.5);
            overlay.rect(px, py, pw, ph, [0.05, 0.06, 0.10, 0.93]);
            overlay.rect(px, py, pw, 3.0, [0.5, 0.55, 0.7, 0.95]);
            overlay.rect(px, py + ph - 3.0, pw, 3.0, [0.5, 0.55, 0.7, 0.95]);
            let title = "Controls";
            overlay.text_shadow(px + (pw - title.len() as f32 * 9.0 * 1.5) * 0.5, py + 16.0, 1.5, title, [0.95, 0.88, 0.55, 1.0]);
            let col_a = px + 30.0;
            let col_b = px + pw * 0.5 + 10.0;
            let mut y = py + 54.0;
            for (action, key) in KEYBINDS {
                overlay.text(col_a, y, 1.05, action, [0.72, 0.8, 0.95, 1.0]);
                overlay.text(col_b, y, 1.05, key, [1.0, 0.96, 0.8, 1.0]);
                y += 22.0;
            }
            overlay.text(col_a, py + ph - 26.0, 1.0, "Esc / Tab  back", [0.6, 0.66, 0.8, 0.9]);
            return;
        }
        // Login fields INSIDE the Login.PNG window frame's body (below its baked
        // "Account Login" title bar). The frame + game logo are drawn above; the
        // 3D menu scene shows around the window.
        if self.mode == Mode::Login {
            let lbl = [0.72, 0.8, 0.95, 0.95];
            let fs = 1.6;
            let field_bg = |o: &mut rcce_render::Overlay, x, y, w, focused: bool| {
                o.rect(x, y, w, 30.0, [0.10, 0.12, 0.18, 0.95]);
                let c = if focused { [0.9, 0.8, 0.4, 1.0] } else { [0.3, 0.34, 0.45, 1.0] };
                o.rect(x, y + 30.0, w, 2.0, c);
            };
            let fx = wx + ww * 0.10;
            let fw = ww * 0.80;
            let mut y = wy + wh * 0.20;
            overlay.text(fx, y, 1.1, "ACCOUNT", lbl);
            y += 18.0;
            field_bg(overlay, fx, y, fw, self.login_focus == 0);
            overlay.text(fx + 8.0, y + 7.0, fs, &self.login_user, [1.0, 1.0, 1.0, 1.0]);
            if self.login_focus == 0 && (elapsed * 2.0) as i32 % 2 == 0 {
                overlay.text(fx + 8.0 + self.login_user.chars().count() as f32 * 9.0 * fs, y + 7.0, fs, "_", [1.0, 1.0, 1.0, 1.0]);
            }
            y += 52.0;
            overlay.text(fx, y, 1.1, "PASSWORD", lbl);
            y += 18.0;
            field_bg(overlay, fx, y, fw, self.login_focus == 1);
            let masked: String = "*".repeat(self.login_pass.chars().count());
            overlay.text(fx + 8.0, y + 7.0, fs, &masked, [1.0, 1.0, 1.0, 1.0]);
            if self.login_focus == 1 && (elapsed * 2.0) as i32 % 2 == 0 {
                overlay.text(fx + 8.0 + masked.chars().count() as f32 * 9.0 * fs, y + 7.0, fs, "_", [1.0, 1.0, 1.0, 1.0]);
            }
            y += 50.0;
            if !self.login_msg.is_empty() {
                overlay.text(fx, y, 1.05, &self.login_msg, [1.0, 0.7, 0.5, 1.0]);
            }
            let srv = format!("server {}:{}", self.host, self.port);
            overlay.text(fx, wy + wh * 0.88, 0.9, &srv, [0.5, 0.55, 0.65, 0.85]);
            let hint = "Tab switch field   Enter login   F1 sound options   Esc quit";
            overlay.text(sw * 0.5 - hint.len() as f32 * 9.0 * 0.5, wy + wh + 12.0, 1.0, hint, [0.6, 0.66, 0.8, 0.9]);
            return;
        }
        let title = "RCCE2";
        let ts = 5.0;
        overlay.text_shadow(sw * 0.5 - title.len() as f32 * 9.0 * ts * 0.5, sh * 0.12, ts, title, [0.95, 0.85, 0.5, 1.0]);
        let sub = "RealmCrafter Community Edition";
        overlay.text_shadow(sw * 0.5 - sub.len() as f32 * 9.0 * 1.3 * 0.5, sh * 0.12 + 9.0 * ts + 6.0, 1.3, sub, [0.8, 0.85, 0.95, 0.9]);

        // Char-select roster panel: flush-left and tall, mirroring the Blitz
        // layout (the 3D character fills the open right area). Only CharSelect
        // reaches here — the other modes draw + return above.
        let pw = (sw * 0.30).clamp(300.0, 520.0);
        let ph = sh * 0.66;
        let px = sw * 0.025;
        let py = sh * 0.17;
        overlay.rect(px, py, pw, ph, [0.05, 0.06, 0.10, 0.86]);
        overlay.rect(px, py, pw, 2.5, [0.45, 0.5, 0.65, 0.95]);
        overlay.rect(px, py + ph - 2.5, pw, 2.5, [0.45, 0.5, 0.65, 0.95]);
        let pad = 26.0;

        match self.mode {
            // Eula + Options + Controls + Login are fully drawn + returned above.
            Mode::Eula | Mode::Options | Mode::Controls | Mode::Login => {}
            Mode::CharSelect => {
                let fx = px + pad;
                overlay.text(fx, py + pad, 1.6, "SELECT CHARACTER", [0.85, 0.9, 1.0, 1.0]);
                let list_y = py + pad + 40.0;
                let row_h = 34.0;
                if self.chars.is_empty() {
                    overlay.text(fx, list_y, 1.3, "(no characters yet)", [0.6, 0.64, 0.75, 0.9]);
                }
                for (i, c) in self.chars.iter().enumerate() {
                    let ry = list_y + i as f32 * row_h;
                    if i == self.char_sel {
                        overlay.rect(fx - 6.0, ry - 4.0, pw - pad * 2.0 + 12.0, row_h - 4.0, [0.18, 0.22, 0.34, 0.9]);
                        overlay.rect(fx - 6.0, ry - 4.0, 3.0, row_h - 4.0, [0.9, 0.8, 0.4, 1.0]);
                    }
                    let race = self
                        .playable
                        .iter()
                        .find(|(aid, _)| *aid == c.actor_id)
                        .map(|(_, r)| r.as_str())
                        .unwrap_or("?");
                    let g = if c.gender == 1 { "F" } else { "M" };
                    overlay.text(fx + 4.0, ry, 1.5, &c.name, [1.0, 1.0, 1.0, 1.0]);
                    overlay.text(fx + 220.0, ry + 2.0, 1.1, &format!("{race}  ({g})"), [0.7, 0.78, 0.9, 0.95]);
                }

                if let Some((name, tpl)) = &self.creating {
                    let by = py + ph - 96.0;
                    overlay.rect(fx - 6.0, by - 8.0, pw - pad * 2.0 + 12.0, 78.0, [0.08, 0.10, 0.16, 0.96]);
                    overlay.text(fx, by, 1.2, "NEW CHARACTER", [0.85, 0.9, 1.0, 1.0]);
                    let race = self.playable.get(*tpl).map(|(_, r)| r.as_str()).unwrap_or("?");
                    overlay.text(fx, by + 22.0, 1.5, &format!("{name}_"), [1.0, 1.0, 0.9, 1.0]);
                    overlay.text(fx, by + 48.0, 1.2, &format!("< {race} >"), [0.7, 0.85, 0.95, 1.0]);
                }

                if !self.login_msg.is_empty() {
                    overlay.text(fx, py + ph - 30.0, 1.05, &self.login_msg, [1.0, 0.75, 0.5, 1.0]);
                }
                let hint = if self.creating.is_some() {
                    "Type name   Left/Right race   Enter create   Esc cancel"
                } else {
                    "Up/Down select   Enter play   C create   Del delete   Esc back"
                };
                overlay.text(sw * 0.5 - hint.len() as f32 * 9.0 * 0.5, py + ph + 14.0, 1.0, hint, [0.6, 0.66, 0.8, 0.9]);
            }
            Mode::InWorld => {}
        }
    }

    /// Gracefully disconnect any open ENet connection. Called before a hard
    /// `std::process::exit` (RCCE_SHOT / RCCE_BENCH), which skips destructors —
    /// so the server clears the account session (LoggedOn) promptly rather than
    /// waiting out the connection timeout and rejecting the next login with 'L'.
    fn shutdown_net(&mut self) {
        if let Some(net) = self.net.as_mut() {
            net.transport.disconnect(net.peer);
        }
        let lp = self.login_peer;
        if let Some(t) = self.login_transport.as_mut() {
            t.disconnect(lp);
        }
    }

    fn render(&mut self) {
        // Drain any in-flight background account-login before drawing this frame
        // (non-blocking; only does work while a login worker is connecting).
        self.poll_login();
        // Headless zone-render verification: bypass the menu (and its Set.b3d
        // backdrop swap) to view the loaded gameplay zone directly.
        if std::env::var_os("RCCE_VIEWZONE").is_some() {
            self.render_zone_preview();
            return;
        }
        // Login / character-select: a slowly-orbiting zone backdrop + the menu.
        if self.mode != Mode::InWorld {
            self.render_menu();
            return;
        }
        let (Some(gfx), Some(view), Some(store)) =
            (self.gfx.as_mut(), self.view.as_mut(), self.store.as_mut())
        else {
            return;
        };
        let elapsed = self.start.elapsed().as_secs_f32();

        // Auto-combat (CBT-1): while attacking a live target, chase to melee
        // range then send P_AttackActor on the CombatDelay cooldown. Chasing
        // reuses move_target; `attacking` clears when the target dies/vanishes.
        if self.attacking {
            let tgt = self.target;
            let info = self.net.as_ref().and_then(|n| {
                tgt.and_then(|rid| {
                    n.world
                        .actors
                        .get(&rid)
                        .filter(|a| a.alive)
                        .map(|a| (rid, a.x, a.z, n.world.me_x, n.world.me_z))
                })
            });
            match info {
                None => self.attacking = false,
                Some((rid, tx, tz, mx, mz)) => {
                    let dist = ((tx - mx).powi(2) + (tz - mz).powi(2)).sqrt();
                    let ready = self.last_attack.elapsed().as_millis() as u64 >= COMBAT_DELAY_MS;
                    // CBT-2 / blocker #5b: an equipped ranged weapon (slot 0,
                    // wtype W_Ranged, item-health > 0) attacks at `range - 0.5`;
                    // otherwise the melee base. Computed from the live inventory.
                    let weapon = self.net.as_ref().and_then(|n| n.world.me_inventory.get(&0)).copied();
                    let range = match weapon {
                        Some(w) => {
                            let (wt, wr) = store
                                .item_def(w.item_id)
                                .map(|d| (d.weapon_wtype, d.weapon_range))
                                .unwrap_or((0, 0.0));
                            effective_attack_range(wt, wr, w.health, MELEE_RANGE)
                        }
                        None => MELEE_RANGE,
                    };
                    match combat_step(dist, range, ready) {
                        CombatStep::Chase => self.move_target = Some([tx, tz]),
                        CombatStep::Wait => self.move_target = None,
                        CombatStep::Swing => {
                            self.move_target = None;
                            if let Some(net) = self.net.as_mut() {
                                net.transport.send(
                                    net.peer,
                                    rcce_net::packet_id::ATTACK_ACTOR,
                                    &rid.to_le_bytes(),
                                    true,
                                );
                            }
                            self.last_attack = Instant::now();
                            self.me_attack_until = elapsed + 0.8; // play the swing clip
                            println!("[combat] swing -> rid={rid} (dist {dist:.1})");
                        }
                    }
                }
            }
        }

        // Movement intent from WASD relative to the camera yaw (computed before
        // borrowing `net`). Camera-space basis: forward = into-screen, right.
        let (sy, cy) = self.cam_yaw.sin_cos();
        let fwd = [-sy, -cy];
        // `right` is forward rotated -90° about Y so that D strafes screen-right
        // and A screen-left. The naive `[cy, -sy]` is the +90° rotation, which
        // inverted both keys (A walked right, D walked left); negating fixes it.
        let right = [-cy, sy];
        let mut dir = [0.0f32, 0.0];
        // RCCE_AUTOWALK forces forward movement (for headless verification of
        // the movement-send path without a keyboard).
        let auto = std::env::var_os("RCCE_AUTOWALK").is_some();
        // RCCE_STRAFE forces strafe-right (blocker #4 facing verification): the
        // body should turn to face world-right while the camera still looks
        // forward, so its profile is visible — proving facing follows movement.
        let strafe = std::env::var_os("RCCE_STRAFE").is_some();
        if self.keys_wasd[0] || auto { dir[0] += fwd[0]; dir[1] += fwd[1]; }
        if self.keys_wasd[2] { dir[0] -= fwd[0]; dir[1] -= fwd[1]; }
        if self.keys_wasd[3] || strafe { dir[0] += right[0]; dir[1] += right[1]; }
        if self.keys_wasd[1] { dir[0] -= right[0]; dir[1] -= right[1]; }
        // Click-to-move (MOVE-5): with no manual/auto input this frame, steer
        // toward a pending clicked ground point until within the Blitz
        // dist-to-dest stop threshold (2.0 units, Client.bb:548). Any manual
        // WASD/auto input cancels the click destination (manual override wins).
        let manual = (dir[0] * dir[0] + dir[1] * dir[1]).sqrt() > 0.01;
        if manual {
            // Manual WASD/auto input cancels click-to-move AND auto-combat.
            self.move_target = None;
            self.attacking = false;
        } else if let Some(tgt) = self.move_target {
            if let Some(p) = self.net.as_ref().map(|n| [n.world.me_x, n.world.me_z]) {
                let (tdx, tdz) = (tgt[0] - p[0], tgt[1] - p[1]);
                if (tdx * tdx + tdz * tdz).sqrt() > 2.0 {
                    dir = [tdx, tdz]; // steer toward the click; normalised below
                } else {
                    self.move_target = None; // arrived
                    self.move_running = false; // stop running once we get there
                }
            }
        }
        let mag = (dir[0] * dir[0] + dir[1] * dir[1]).sqrt();
        let moving = mag > 0.01;
        let want_send = self.last_move.elapsed().as_millis() >= 110;
        // MOVE-6: a double-click move runs; Shift-run always wins.
        // RCCE_RUN forces running for headless diagnosis of the run-speed path.
        let run = move_run(self.run, self.move_target.is_some(), self.move_running)
            || std::env::var_os("RCCE_RUN").is_some();

        // Pump the network, send movement, and rebuild animated actors.
        let mut cam_target = self.center;
        let mut cam_me_yaw = 0.0f32; // player facing, for the first-person camera (CAM-4)
        let mut following = false;
        let mut did_send = false;
        // Set when a vendor/container trade opens this frame, so the inventory
        // panel auto-opens alongside it (parity: Blitz shows both, and you drag
        // items between them to sell). Applied after the `net` borrow ends.
        let mut open_inventory_for_trade = false;
        if let Some(net) = self.net.as_mut() {
            let was_trading = net.world.current_trade.is_some();
            for m in net.transport.poll() {
                net.updates += 1;
                net.world.apply(&m);
            }
            if !was_trading {
                use rcce_client::trade::TradeKind;
                if matches!(
                    net.world.current_trade.as_ref().map(|t| t.kind),
                    Some(TradeKind::Npc) | Some(TradeKind::Scenery)
                ) {
                    open_inventory_for_trade = true;
                }
            }
            // Once in-world, request the P_FetchActors env block (empty payload,
            // like MainMenu.bb) so we learn the server's time-of-day; the "E"
            // sub-packet is parsed in World::on_fetch_actors. Then advance the
            // game clock locally each frame so day/night tracks server time.
            if !net.env_requested {
                net.transport.send(net.peer, rcce_net::packet_id::FETCH_ACTORS, &[], true);
                net.env_requested = true;
            }
            net.world.advance_time((elapsed - self.prev_elapsed).clamp(0.0, 0.1));
            // Live area change (player warp): reload the new zone's scenery +
            // sky/clouds/stars + music. Gated by the zone name so it only fires
            // on an actual change, not every frame.
            if !net.world.zone.name.is_empty() && net.world.zone.name != self.loaded_zone {
                let zone = net.world.zone.name.clone();
                if let Some(z) = load_zone_full(store, view, gfx, &self.data_root, &zone) {
                    self.center = z.center;
                    self.span = z.span;
                    self.ground_y = z.ground_y;
                    self.height_field = Some(z.height_field);
                    self.water_planes = z.waters;
                    self.emitters = z.emitters;
                    self.cam_occluders = z.occluders;
                    self.fog_color = z.env.fog_color;
                    self.fog_near = z.env.fog_near;
                    self.fog_far = z.env.fog_far;
                    self.ambient = z.env.ambient;
                    self.light_dir = z.env.light_dir;
                    self.cloud_regular_img = z.cloud_regular;
                    self.cloud_storm_img = z.cloud_storm;
                    self.cloud_is_storm = false;
                    if let Some(audio) = self.audio.as_mut() {
                        audio.set_music(z.env.music_id, 0.4, |id| store.music_path(id));
                    }
                    println!("[client-window] reloaded zone '{zone}'");
                }
                // Mark loaded even if the area file was missing, so we don't
                // retry the reload (and its disk I/O) every frame.
                self.loaded_zone = zone;
            }
            // Flush any replies the apply() logic queued (e.g. the "GY" accept
            // when the server gives us an item).
            for (ptype, data) in net.world.pending_sends.drain(..) {
                net.transport.send(net.peer, ptype, &data, true);
            }
            // Combat-damage feedback (CBT-4/CBT-5). DamageInfoStyle 2 = chat
            // lines, 3 = floating numbers. Faithful to the engine, show ONE
            // style — so style 2 drains new combat events to chat lines and skips
            // the floaters; any other value uses floaters (the default).
            if self.damage_info_style == 2 {
                let me = net.world.my_runtime_id;
                let total = net.world.combat_events.len();
                if self.combat_chat_consumed > total {
                    self.combat_chat_consumed = 0; // log reset; restart
                }
                for i in self.combat_chat_consumed..total {
                    let ev = net.world.combat_events[i];
                    let (line, col) = compose_damage_line(ev.target, ev.attacker, ev.damage, me, |rid| {
                        net.world
                            .actors
                            .get(&rid)
                            .map(|a| {
                                let n = a.name.trim();
                                if n.is_empty() { "Someone".to_string() } else { n.to_string() }
                            })
                            .unwrap_or_else(|| "Someone".to_string())
                    });
                    net.world.chat.push((line, col));
                }
                self.combat_chat_consumed = total;
            } else {
                // Spawn floating damage numbers for any new combat hits, expire old.
                self.floaters.ingest(&net.world.combat_events, elapsed);
            }
            self.floaters.tick(elapsed);
            // Advance in-flight projectiles (PRJ-1). prev_elapsed is updated
            // later (weather), so this read gives the same per-frame dt.
            let proj_dt = (elapsed - self.prev_elapsed).clamp(0.0, 0.1);
            net.world.tick_projectiles(proj_dt);
            // MOVE-SMOOTH (Blitz UpdateActorInstances parity): dead-reckon actors
            // toward their destination at the real move speed + gently reconcile
            // to the server echo, instead of teleporting between ~9 Hz updates.
            // Time-based render interpolation for every actor + the local player
            // (MOVE-SMOOTH): the body renders at `now - RENDER_DELAY` interpolated
            // across the buffered server positions — smooth regardless of frame or
            // echo-cadence jitter, no velocity guessing.
            let rz_before = net.world.me_render_z;
            net.world.tick_movement(elapsed, proj_dt, dir, moving, run);
            // RCCE_MOVEDIAG: per-frame trace — me_z (server), me_render_z, the
            // frame's render delta, and the sample count. A smooth render shows a
            // steady delta proportional to dt.
            if std::env::var_os("RCCE_MOVEDIAG").is_some() && moving {
                let w = &net.world;
                let n = w.me_samples.len();
                // Echo gap + per-echo distance of the latest two samples, and
                // whether `now - delay` is extrapolating past the newest sample.
                let (gap, ddist, extrap) = if n >= 2 {
                    let a = w.me_samples[n - 2];
                    let b = w.me_samples[n - 1];
                    let g = b[0] - a[0];
                    let d = ((b[1] - a[1]).powi(2) + (b[2] - a[2]).powi(2)).sqrt();
                    let rd: f32 = std::env::var("RCCE_RENDERDELAY").ok().and_then(|s| s.parse().ok()).unwrap_or(0.13);
                    (g, d, (elapsed - rd) > b[0])
                } else {
                    (0.0, 0.0, false)
                };
                println!(
                    "[movediag] f{} dt={:.4} me_z={:.2} rz={:.2} d_rz={:+.3} n={} gap={:.3} ddist={:.2} {}",
                    self.frames, proj_dt, w.me_z, w.me_render_z, w.me_render_z - rz_before, n, gap, ddist,
                    if extrap { "EXTRAP" } else { "interp" }
                );
            }
            // Remote jump-anim timers (ANIM-7) + the local jump arc (MOVE-7).
            net.world.tick_jumps(proj_dt);
            // Remote attack-swing timers (CBT-3).
            net.world.tick_attack_anims(proj_dt);
            if !self.grounded {
                let (o, v, g) = jump_step(self.jump_offset, self.jump_vel);
                self.jump_offset = o;
                self.jump_vel = v;
                self.grounded = g;
            }
            // Start a new screen flash when one arrives (ENV-6), stamping its
            // start time for the fade.
            if let Some(f) = net.world.flash.take() {
                self.flash = Some((f, elapsed));
            }
            // Chat bubbles (CHAT-4): adopt new ones (stamping the start time),
            // and drop any that have faded out after ~5s.
            for (rid, text, col) in net.world.pending_bubbles.drain(..) {
                self.bubbles.insert(rid, (text, col, elapsed));
            }
            self.bubbles.retain(|_, (_, _, start)| elapsed - *start < 5.0);
            // Inbound sound (AUD-4/5: P_Sound/P_Speech) + mid-zone music switch
            // (AUD-1: P_Music). Drain the queued events to the audio engine —
            // one-shots play 2D for the alpha; the music switch replaces the
            // looping track. (3D positional attenuation is a noted follow-up.)
            if let Some(audio) = self.audio.as_mut() {
                for sid in net.world.pending_sounds.drain(..) {
                    if let Some(path) = store.sound_path_by_id(sid) {
                        audio.play_oneshot(&path, 0.7);
                    }
                }
                if let Some(mid) = net.world.pending_music.take() {
                    audio.set_music(mid, 0.4, |id| store.music_path(id));
                    println!("[audio] P_Music -> music id {mid}");
                }
            }
            // Send a P_StandardUpdate toward the input direction (unreliable,
            // like ClientNet.bb): the server walks the actor toward Dest and
            // echoes its authoritative position, which on_standard_update
            // applies back to me_x/z. A single stop packet on key-release.
            // Report the CLIENT-AUTHORITATIVE position (me_render, advanced this
            // frame by tick_movement) so the server takes our position directly
            // (NewX/NewZ) like Blitz — instead of the stale last-echo position,
            // which made movement wait on the server walking toward a dest. `my`
            // (height) still comes from the server echo. (RCCE_SERVERMOVE keeps the
            // old behaviour: me_render reconciles to me_x, so this ≈ the echo.)
            let (mx, my, mz) = (net.world.me_render_x, net.world.me_y, net.world.me_render_z);
            // Blocker #4 (MOVE-1/3): the local body faces its steering direction.
            // The P_StandardUpdate wire carries no yaw — the server faces the actor
            // toward Dest (PointEntity) and the echo never updates me_yaw — so
            // without a client-side facing the body stays frozen at its spawn
            // heading. This is now done by `tick_movement` (called above), which
            // EASES me_yaw toward the movement heading at the same rate as remote
            // actors, so the turn glides instead of snapping. Idle keeps the facing.
            if moving && want_send {
                let (nx, nz) = (dir[0] / mag, dir[1] / mag);
                let p = movement_packet(mx + nx * 16.0, mz + nz * 16.0, my, mx, mz, run, false);
                net.transport.send(net.peer, rcce_net::packet_id::STANDARD_UPDATE, &p, false);
                did_send = true;
                // MOVE-6 trace: confirm a double-click move sends the run flag.
                if self.move_running && run && std::env::var("RCCE_DBLRUN").is_ok() {
                    println!("[dblrun] frame {} sent RUN move packet (run={run})", self.frames);
                }
            } else if !moving && self.was_moving {
                let p = movement_packet(mx, mz, my, mx, mz, false, false);
                net.transport.send(net.peer, rcce_net::packet_id::STANDARD_UPDATE, &p, false);
            }

            // GPU skinning makes the per-actor pose update cheap (just the
            // bone-palette uniform; the static body mesh is cached), so rebuild
            // every frame for smooth animation. The CPU path stays throttled to
            // ~12 Hz by dyn_hash (each rebuild re-skins + re-uploads vertices).
            let me_attack = self.me_attack_until > elapsed;
            let me_jumping = !self.grounded;
            let me_jump_offset = self.jump_offset;
            // ANIM-6 idle fidget: advance the LCG, and while standing still kick
            // off an occasional Look-around/Yawn that plays for FIDGET_SECS. Any
            // movement / jump / attack cancels it.
            self.rng = self.rng.wrapping_mul(1_664_525).wrapping_add(1_013_904_223);
            let idle = !moving && self.grounded && !me_attack;
            if !idle {
                self.fidget_until = 0.0;
            } else if elapsed >= self.fidget_until && fidget_fires(self.rng, idle) {
                self.fidget_clip = (self.rng >> 16) as usize % FIDGET_CLIPS.len();
                self.fidget_until = elapsed + FIDGET_SECS;
            }
            let me_fidget = if idle && elapsed < self.fidget_until {
                Some(FIDGET_CLIPS[self.fidget_clip])
            } else {
                None
            };
            // SPL-4: complete a memorise once its progress bar fills (insert the
            // spell id — the memorised set is id-keyed).
            if let Some((idx, start)) = self.memorising {
                if memorise_progress(start, elapsed) >= 1.0 {
                    if let Some(id) = net.world.known_spells.get(idx).map(|s| s.id) {
                        self.memorised.insert(id);
                    }
                    self.memorising = None;
                }
            }
            let hash = dyn_hash(&net.world, elapsed, moving, run, me_attack)
                ^ (((me_jump_offset * 100.0) as i64 as u64).rotate_left(7))
                ^ if me_jumping { 0x4A_4D_50_00 } else { 0 }
                ^ if self.first_person { 0x46_50_00_00 } else { 0 }
                ^ if me_fidget.is_some() { 0x46_49_44_00 } else { 0 };
            if self.gpu_skin || hash != self.last_dyn_hash {
                let (models, textures, place, keys, skinned) = build_actors(
                    store, &net.world, elapsed, self.gpu_skin, moving, run, 0, me_attack, me_jumping,
                    me_jump_offset, self.first_person, me_fidget, self.height_field.as_ref(),
                );
                // CPU drawables: attachments (+ bodies when GPU skinning is off).
                let mut instances: Vec<SceneInstance> = place
                    .iter()
                    .map(|&(idx, t, r, color, s)| SceneInstance {
                        model: &models[idx],
                        textures: &textures[idx][..],
                        lightmaps: &[],
                        translation: t,
                        rot: r,
                        scale: s,
                        color,
                    })
                    .collect();
                // Dropped loot (DROP-1): render each item's world mesh (its `mmesh`)
                // seated on the ground, or the Loot Bag fallback for items with no
                // world mesh (most shipped items) — like Blitz (ClientNet.bb:1378).
                // Replaces the flat 2D pip with a real, terrain-occluded 3D object.
                let mut loot_models: Vec<std::rc::Rc<rcce_data::B3dModel>> = Vec::new();
                let mut loot_texs: Vec<Vec<Option<rcce_data::Image>>> = Vec::new();
                let mut loot_xf: Vec<([f32; 3], f32, String)> = Vec::new();
                if !net.world.dropped_items.is_empty() {
                    if self.loot_bag.is_none() {
                        self.loot_bag = store.mesh_by_path("Loot Bag.b3d").map(|(m, t, _)| (m, t));
                    }
                    for d in net.world.dropped_items.values() {
                        let (model, texs, s, key) = match store.gear_attachment(d.item_id) {
                            // Item's own world mesh (Blitz: LoadedMeshScales × 0.05).
                            Some(att) => (att.model, att.textures, att.scale * 0.05, format!("loot:i{}", d.item_id)),
                            // Fallback Loot Bag (Blitz scales it 0.075).
                            None => match &self.loot_bag {
                                Some((m, t)) => (m.clone(), t.clone(), 0.075, "loot:bag".to_string()),
                                None => continue,
                            },
                        };
                        // Seat the mesh's lowest vertex on the terrain under it.
                        let (min, _) = model.bounds();
                        let ground = self.height_field.as_ref().and_then(|h| h.height_at(d.x, d.z)).unwrap_or(d.y);
                        loot_models.push(model);
                        loot_texs.push(texs);
                        loot_xf.push(([d.x, ground - min[1] * s, d.z], s, key));
                    }
                }
                let mut keys = keys;
                for (i, (pos, s, key)) in loot_xf.iter().enumerate() {
                    instances.push(SceneInstance {
                        model: &loot_models[i],
                        textures: &loot_texs[i][..],
                        lightmaps: &[],
                        translation: *pos,
                        rot: [0.0, 0.0, 0.0],
                        scale: [*s, *s, *s],
                        color: [1.0, 1.0, 1.0],
                    });
                    keys.push(key.clone());
                }
                view.set_dynamic(&gfx.device, &gfx.queue, &instances, &keys);
                // GPU-skinned bodies (when enabled) — static mesh + pose uniform.
                if self.gpu_skin {
                    let sinst: Vec<rcce_render::SkinnedInstance> = skinned
                        .iter()
                        .map(|a| rcce_render::SkinnedInstance {
                            key: &a.key,
                            model: &a.model,
                            textures: &a.textures[..],
                            frame: a.frame,
                            transform: a.transform,
                            color: a.color,
                        })
                        .collect();
                    view.set_skinned(&gfx.device, &gfx.queue, &sinst);
                }
                self.last_dyn_hash = hash;
            }
            // Follow the SMOOTHED player render position (MOVE-SMOOTH) — the body
            // renders there too, so camera + body move in lockstep and there's no
            // relative jitter. The Y tracks the TERRAIN under the player (the body
            // is seated on the height field, and P_StandardUpdate omits Y so me_y
            // is a stale spawn height) — otherwise the camera stays at the spawn
            // elevation and clips through hills the player walks up.
            let (mrx, mrz) = (net.world.me_render_x, net.world.me_render_z);
            let cam_y = self
                .height_field
                .as_ref()
                .and_then(|h| h.height_at(mrx, mrz))
                .unwrap_or(net.world.me_y);
            cam_target = [mrx, cam_y, mrz];
            // Headless inspection: RCCE_CAMAT="x,y,z" points the camera at a fixed
            // world spot (e.g. a water plane) for a screenshot without walking there.
            if let Ok(s) = std::env::var("RCCE_CAMAT") {
                let p: Vec<f32> = s.split(',').filter_map(|t| t.trim().parse().ok()).collect();
                if p.len() == 3 {
                    cam_target = [p[0], p[1], p[2]];
                }
            }
            cam_me_yaw = net.world.me_yaw.to_radians(); // degrees -> radians for the FP camera
            following = true;
        }
        if did_send {
            self.last_move = Instant::now();
        }
        if open_inventory_for_trade {
            self.show_inventory = true;
            // Fresh basket for the new shop visit.
            self.pending_buys.clear();
            self.pending_sells.clear();
        }
        self.was_moving = moving;

        // Animate the zone's water surfaces: advance the scroll offset and rebuild
        // the water quads with it (Blitz PositionTexture(U, V) on the water texture).
        // Uses the gfx/view bound at the top of the world render path.
        if !self.water_planes.is_empty() {
            let dt = (elapsed - self.prev_elapsed).clamp(0.0, 0.1);
            self.water_scroll[0] = (self.water_scroll[0] + WATER_SCROLL_U * dt).rem_euclid(1.0);
            self.water_scroll[1] = (self.water_scroll[1] + WATER_SCROLL_V * dt).rem_euclid(1.0);
            let scroll = self.water_scroll;
            let models: Vec<B3dModel> = self.water_planes.iter().map(|(w, _)| water_quad(w, scroll)).collect();
            let texs: Vec<Vec<Option<Image>>> =
                self.water_planes.iter().map(|(_, img)| vec![Some(img.clone())]).collect();
            let instances: Vec<SceneInstance> = self
                .water_planes
                .iter()
                .enumerate()
                .map(|(i, (w, _))| SceneInstance {
                    model: &models[i],
                    textures: &texs[i][..],
                    lightmaps: &[],
                    translation: w.pos,
                    rot: [0.0, 0.0, 0.0],
                    scale: [w.scale_x, 1.0, w.scale_z],
                    color: [1.0, 1.0, 1.0],
                })
                .collect();
            view.set_water(&gfx.device, &gfx.queue, &instances);
        }

        // Footstep one-shots while the local player moves (faster when running).
        if let Some(idx) = self.footsteps.tick(elapsed, moving && following, self.run) {
            if let Some(audio) = self.audio.as_ref() {
                if !self.footstep_paths.is_empty() {
                    let p = &self.footstep_paths[idx % self.footstep_paths.len()];
                    audio.play_oneshot(p, 0.55);
                }
            }
        }

        // Weather ambient: rain loops while it's raining; a storm adds a wind
        // loop and periodic thunder one-shots — mirrors Environment3D.bb
        // SetWeather (W_Rain plays Snd_Rain; W_Storm adds Snd_Wind + thunder).
        {
            let wx = self
                .net
                .as_ref()
                .map(|n| rcce_client::weather::weather_from_byte(n.world.zone.weather))
                .unwrap_or(rcce_client::weather::Weather::Clear);
            let storm = wx == rcce_client::weather::Weather::Storm;
            // Storm-cloud swap: upload the darker storm clouds while storming,
            // the regular clouds otherwise — only on a change (no per-frame
            // reload). StormCloudTexID absent → keep the regular clouds.
            let want_storm_clouds = storm && self.cloud_storm_img.is_some();
            if want_storm_clouds != self.cloud_is_storm {
                let img = if want_storm_clouds {
                    self.cloud_storm_img.as_ref()
                } else {
                    self.cloud_regular_img.as_ref()
                };
                if let Some(img) = img {
                    view.set_cloud_texture(&gfx.device, &gfx.queue, img.width, img.height, &img.rgba);
                    self.cloud_is_storm = want_storm_clouds;
                }
            }
            let rain_p = wx.is_rainy().then(|| store.sound_path("Weather/Rain.ogg")).flatten();
            let wind_p = storm.then(|| store.sound_path("Weather/Wind.ogg")).flatten();
            let fire = lightning_fires(storm, elapsed, self.next_thunder);
            let thunder_p = if fire {
                store.sound_path(&format!("Weather/Thunder{}.ogg", self.thunder_idx % 3 + 1))
            } else {
                None
            };
            if let Some(audio) = self.audio.as_mut() {
                let mut keep: Vec<&'static str> = Vec::new();
                if let Some(p) = &rain_p {
                    audio.set_ambient_loop("rain", p, 0.5);
                    keep.push("rain");
                }
                if let Some(p) = &wind_p {
                    audio.set_ambient_loop("wind", p, 0.4);
                    keep.push("wind");
                }
                audio.retain_ambient(&keep);
                if let Some(p) = &thunder_p {
                    audio.play_oneshot(p, 0.7);
                }
            }
            // On a lightning strike: reschedule + flash the screen bright white
            // (ENV-5, reusing the ENV-6 ScreenFlash render). Deterministic
            // 8–15s gap from the counter (no RNG).
            if fire {
                self.thunder_idx = self.thunder_idx.wrapping_add(1);
                self.next_thunder = elapsed + 8.0 + (self.thunder_idx as f32 * 2.6) % 7.0;
                self.flash = Some((
                    rcce_client::world::ScreenFlash { color: [1.0, 1.0, 1.0], alpha: 0.7, length: 0.4 },
                    elapsed,
                ));
            } else if !storm {
                self.next_thunder = elapsed + 6.0;
            }
        }

        let frame = match gfx.surface.get_current_texture() {
            Ok(f) => f,
            Err(_) => {
                gfx.surface.configure(&gfx.device, &gfx.config);
                match gfx.surface.get_current_texture() {
                    Ok(f) => f,
                    Err(_) => return,
                }
            }
        };
        let tview = frame.texture.create_view(&Default::default());

        // Camera: third-person follow behind the player along cam_yaw (live),
        // or a slow orbit of the zone centre (spectator). `behind = -forward`.
        let (eye, target) = if following && self.first_person {
            // First-person (CAM-4): eye at the head, looking along the character's
            // facing. The own body is hidden in build_actors (hide_me).
            first_person_view(cam_target, cam_me_yaw)
        } else if following {
            // Orbit behind the player: yaw places the camera on the -forward
            // side, pitch raises it. `dist` is the boom length (CAM-3 zoom).
            let dist = self.cam_dist;
            let pitch = std::env::var("RCCE_CAMPITCH").ok().and_then(|s| s.trim().parse().ok()).unwrap_or(self.cam_pitch);
            let (sp, cp) = pitch.sin_cos();
            let look = [cam_target[0], cam_target[1] + 3.5, cam_target[2]];
            // RCCE_CAMYAW overrides the orbit yaw (radians) for headless framing.
            let (sy, cy) = std::env::var("RCCE_CAMYAW")
                .ok()
                .and_then(|s| s.trim().parse::<f32>().ok())
                .map(|a| a.sin_cos())
                .unwrap_or((sy, cy));
            // Boom direction (pivot -> desired eye), unit length.
            let dir = [sy * cp, sp, cy * cp];
            // Camera collision: march the boom outward and stop before it enters
            // a building occluder, so the camera never clips into / through a
            // wall. Matches the reference client's zoom-in-on-obstruction.
            let dist = camera_boom(look, dir, dist, &self.cam_occluders).max(2.5);
            let mut eye = [look[0] + dir[0] * dist, look[1] + dir[1] * dist, look[2] + dir[2] * dist];
            // Keep the eye above the terrain at its own X/Z — the boom only avoids
            // building occluders, so walking up a hill would otherwise sink the
            // camera into the slope behind the player. Lift it to clear the ground.
            if let Some(ty) = self.height_field.as_ref().and_then(|h| h.height_at(eye[0], eye[2])) {
                eye[1] = eye[1].max(ty + CAM_GROUND_CLEARANCE);
            }
            (eye, look)
        } else {
            let ang = elapsed * 0.3;
            let r = self.span * 0.75;
            let eye = [self.center[0] + r * ang.cos(), self.ground_y + self.span * 0.55, self.center[2] + r * ang.sin()];
            (eye, [self.center[0], self.ground_y + self.span * 0.05, self.center[2]])
        };
        let aspect = gfx.config.width as f32 / gfx.config.height.max(1) as f32;
        let vp = rcce_render::view_proj(eye, target, aspect);
        self.vp = vp; // cache for world-click picking
        // Headless click-to-move self-test (MOVE-5): at the configured frame,
        // synthesize a ground click below screen-centre so the walk-there path
        // is verifiable without a mouse. The destination is consumed by next
        // frame's movement steering; the periodic `me=()` log shows the player
        // converge on it. No-op unless RCCE_CLICKMOVE=<frame> is set.
        if let Ok(cm) = std::env::var("RCCE_CLICKMOVE") {
            if let Ok(at) = cm.parse::<u64>() {
                if self.frames == at && self.move_target.is_none() {
                    let (sw, sh) = (gfx.config.width as f32, gfx.config.height as f32);
                    let start_y = self
                        .net
                        .as_ref()
                        .and_then(|n| self.height_field.as_ref().and_then(|h| h.height_at(n.world.me_render_x, n.world.me_render_z)))
                        .unwrap_or(self.ground_y);
                    if let Some(g) = unproject_terrain(&vp, sw, sh, sw * 0.5, sh * 0.80, self.height_field.as_ref(), start_y) {
                        self.move_target = Some(g);
                        let me = self.net.as_ref().map(|n| (n.world.me_x, n.world.me_z)).unwrap_or((0.0, 0.0));
                        println!(
                            "[clickmove] frame {} me=({:.1},{:.1}) -> target=({:.1},{:.1}) start_y={start_y:.1}",
                            self.frames, me.0, me.1, g[0], g[1]
                        );
                    }
                }
            }
        }
        // Headless double-click-run self-test (MOVE-6): set a ground move target
        // AND the run flag (as a ground double-click would); the next frames send
        // a RUN move packet ([dblrun] trace above). No-op unless RCCE_DBLRUN=<frame>.
        if let Ok(dr) = std::env::var("RCCE_DBLRUN") {
            if let Ok(at) = dr.parse::<u64>() {
                if self.frames == at {
                    let (sw, sh) = (gfx.config.width as f32, gfx.config.height as f32);
                    let start_y = self
                        .net
                        .as_ref()
                        .and_then(|n| self.height_field.as_ref().and_then(|h| h.height_at(n.world.me_render_x, n.world.me_render_z)))
                        .unwrap_or(self.ground_y);
                    if let Some(g) = unproject_terrain(&vp, sw, sh, sw * 0.5, sh * 0.80, self.height_field.as_ref(), start_y) {
                        self.move_target = Some(g);
                        self.move_running = true;
                        println!("[dblrun] frame {} double-click RUN target=({:.1},{:.1})", self.frames, g[0], g[1]);
                    }
                }
            }
        }
        // Headless target-select self-test (TGT-1/TGT-3): at the configured
        // frame, select the nearest living actor AND open its "Actions" context
        // menu (as a single left-click would), so the highlight + Char-Interaction
        // panel + the context menu are capturable via RCCE_SHOT without a mouse.
        // No-op unless RCCE_SELECT=<frame> is set.
        if let Ok(sv) = std::env::var("RCCE_SELECT") {
            if let Ok(at) = sv.parse::<u64>() {
                if self.frames == at && self.target.is_none() {
                    if let Some(net) = self.net.as_ref() {
                        if let Some(rid) = nearest_living_actor(&net.world, net.world.me_x, net.world.me_z) {
                            self.target = Some(rid);
                            let a = net.world.actors.get(&rid);
                            let nm = a.map(|a| a.name.clone()).unwrap_or_default();
                            let is_player = a.map(|a| a.is_player).unwrap_or(false);
                            let (sw, sh) = (gfx.config.width as f32, gfx.config.height as f32);
                            self.context_menu =
                                Some(ContextMenu::build(rid, is_player, sw * 0.42, sh * 0.34, sw, sh));
                            println!("[select] frame {} target rid={rid} name='{nm}' (menu open)", self.frames);
                        }
                    }
                }
            }
        }
        // Headless cycle-target self-test (TGT-7): at the configured frame, cycle
        // the target to the next living NPC (as the cycle key would) so the
        // highlight + Char-Interaction panel are capturable via RCCE_SHOT.
        // No-op unless RCCE_CYCLE=<frame> is set.
        if let Ok(cv) = std::env::var("RCCE_CYCLE") {
            if let Ok(at) = cv.parse::<u64>() {
                if self.frames == at {
                    // Inline (not living_npc_rids): self.gfx is mutably borrowed in
                    // this scope, so only disjoint-field access to self.net is legal.
                    let mut rids: Vec<u16> = match self.net.as_ref() {
                        Some(net) => net
                            .world
                            .actors
                            .values()
                            .filter(|a| a.alive && !a.is_player)
                            .map(|a| a.runtime_id)
                            .collect(),
                        None => Vec::new(),
                    };
                    rids.sort_unstable();
                    self.target = next_target(self.target, &rids);
                    println!(
                        "[cycle] frame {} candidates={:?} target={:?}",
                        self.frames, rids, self.target
                    );
                }
            }
        }
        // Headless local-jump self-test (MOVE-7/ANIM-7): at frame `at`, if
        // grounded, kick the jump (capture the apex ~8 frames later with
        // RCCE_SHOT_FRAME=at+8). No-op unless RCCE_JUMP=<frame> is set.
        if let Ok(jv) = std::env::var("RCCE_JUMP") {
            if let Ok(at) = jv.parse::<u64>() {
                if self.frames == at && self.grounded {
                    self.jump_vel = JUMP_INIT_VEL;
                    self.jump_offset = 0.0;
                    self.grounded = false;
                    println!("[jump] frame {} kicked local jump vel={:.3}", self.frames, self.jump_vel);
                }
            }
        }
        // Headless remote-jump self-test (ANIM-7): at frame `at`, start the jump
        // anim timer on the nearest non-me actor so its Jump pose + hop are
        // capturable. No-op unless RCCE_REMOTEJUMP=<frame> is set.
        if let Ok(rv) = std::env::var("RCCE_REMOTEJUMP") {
            if let Ok(at) = rv.parse::<u64>() {
                if self.frames == at {
                    if let Some(net) = self.net.as_mut() {
                        let my = net.world.my_runtime_id;
                        if let Some(rid) = net.world.actors.keys().copied().find(|&r| r != my) {
                            net.world.jumps.insert(rid, rcce_client::world::JUMP_ANIM_SECS);
                            println!("[remotejump] frame {} started jump anim on rid {rid}", self.frames);
                        }
                    }
                }
            }
        }
        // Headless idle-fidget self-test (ANIM-6): force a Yawn fidget so its
        // pose is capturable (the 1/1000 chance is impractical to wait out).
        // No-op unless RCCE_FIDGET=<frame> is set.
        if let Ok(fg) = std::env::var("RCCE_FIDGET") {
            if let Ok(at) = fg.parse::<u64>() {
                if self.frames == at {
                    self.fidget_clip = 1; // Yawn — the most visible fidget
                    self.fidget_until = self.start.elapsed().as_secs_f32() + 10.0;
                    println!("[fidget] frame {} -> forced Yawn fidget", self.frames);
                }
            }
        }
        // Headless first-person self-test (CAM-4): at frame `at` switch to
        // first-person (capture with RCCE_SHOT to see the body gone + a forward
        // view). No-op unless RCCE_FIRSTPERSON=<frame> is set.
        if let Ok(fv) = std::env::var("RCCE_FIRSTPERSON") {
            if let Ok(at) = fv.parse::<u64>() {
                if self.frames == at {
                    self.first_person = true;
                    println!("[firstperson] frame {} -> first-person view", self.frames);
                }
            }
        }
        // Headless MMB snap-camera self-test (CAM-5): at frame `at` swing the
        // camera 180° round + tilt it up (the "before" state, capturable with
        // RCCE_SHOT_FRAME=at+10), then at frame at+30 snap it behind the
        // character (the "after" state, RCCE_SHOT_FRAME=at+40). No-op unless
        // RCCE_CAMSNAP=<frame> is set.
        if let Ok(cv) = std::env::var("RCCE_CAMSNAP") {
            if let Ok(at) = cv.parse::<u64>() {
                let me_yaw = self.net.as_ref().map(|n| n.world.me_yaw.to_radians()).unwrap_or(0.0);
                if self.frames == at {
                    self.cam_yaw = me_yaw + std::f32::consts::PI;
                    self.cam_pitch = 0.9;
                    println!("[camsnap] frame {} OFF cam_yaw={:.2} pitch={:.2}", self.frames, self.cam_yaw, self.cam_pitch);
                } else if self.frames == at + 30 {
                    let (yaw, pitch) = snap_camera(me_yaw);
                    self.cam_yaw = yaw;
                    self.cam_pitch = pitch;
                    println!("[camsnap] frame {} SNAP cam_yaw={:.2} pitch={:.2}", self.frames, self.cam_yaw, self.cam_pitch);
                }
            }
        }
        // Headless interact self-test (TGT-5): fire RIGHT_CLICK at the selected
        // target (runs the NPC Main script → server may push P_Dialog). No-op
        // unless RCCE_INTERACT=<frame> is set.
        if let Ok(iv) = std::env::var("RCCE_INTERACT") {
            if let Ok(at) = iv.parse::<u64>() {
                if self.frames == at {
                    if let Some(rid) = self.target {
                        if let Some(net) = self.net.as_mut() {
                            net.transport.send(
                                net.peer,
                                rcce_net::packet_id::RIGHT_CLICK,
                                &rcce_client::net::right_click_packet(rid),
                                true,
                            );
                            println!("[interact] frame {} -> RIGHT_CLICK rid={rid}", self.frames);
                        }
                    }
                }
            }
        }
        // Headless trainer flow self-test: interact with the nearest NPC, then pick
        // dialog option 1, then report known spells — exercises the real
        // server/trainer script to prove the dialog-option fix learns the spell.
        // No-op unless RCCE_TRAINERTEST=<frame> is set.
        if let Ok(tv) = std::env::var("RCCE_TRAINERTEST") {
            if let Ok(at) = tv.parse::<u64>() {
                if self.frames == at {
                    let rid = self.net.as_ref().and_then(|n| nearest_living_actor(&n.world, n.world.me_x, n.world.me_z));
                    if let Some(rid) = rid {
                        self.target = Some(rid);
                        if let Some(net) = self.net.as_mut() {
                            net.transport.send(net.peer, rcce_net::packet_id::RIGHT_CLICK, &rcce_client::net::right_click_packet(rid), true);
                        }
                        println!("[trainertest] frame {} interact rid {rid}", self.frames);
                    } else {
                        println!("[trainertest] frame {} no actor to interact with", self.frames);
                    }
                } else if self.frames == at + 60 {
                    if let Some(net) = self.net.as_mut() {
                        if let Some(dl) = net.world.dialog.as_ref() {
                            let (sh, n) = (dl.script_handle, dl.options.len());
                            net.transport.send(net.peer, rcce_net::packet_id::DIALOG, &rcce_client::net::dialog_option_packet(sh, 1), true);
                            if let Some(dl) = net.world.dialog.as_mut() { dl.options.clear(); }
                            println!("[trainertest] frame {} dialog open ({n} opts) -> sent option 1", self.frames);
                        } else {
                            println!("[trainertest] frame {} NO dialog open", self.frames);
                        }
                    }
                } else if self.frames == at + 150 {
                    let names: Vec<String> = self.net.as_ref().map(|n| n.world.known_spells.iter().map(|s| s.name.clone()).collect()).unwrap_or_default();
                    println!("[trainertest] frame {} known_spells = {names:?}", self.frames);
                }
            }
        }
        // Headless dialog-render self-test (TGT-5): inject a synthetic dialog so
        // the window rendering is verifiable without a scripted NPC. No-op unless
        // RCCE_DIALOGTEST=<frame> is set.
        if let Ok(dv) = std::env::var("RCCE_DIALOGTEST") {
            if let Ok(at) = dv.parse::<u64>() {
                if self.frames == at {
                    if let Some(net) = self.net.as_mut() {
                        net.world.dialog = Some(rcce_client::world::Dialog {
                            script_handle: 1,
                            runtime_id: self.target.unwrap_or(0),
                            title: "Greetings, traveler".to_string(),
                            lines: vec![(
                                "Welcome to the realm. The roads have been dangerous of late."
                                    .to_string(),
                                [1.0, 1.0, 1.0, 1.0],
                            )],
                            options: vec![
                                "Tell me about this place".to_string(),
                                "I seek work".to_string(),
                                "Farewell".to_string(),
                            ],
                        });
                    }
                }
            }
        }
        // Headless camera-zoom self-test (CAM-3): pin the boom length so two
        // zoom levels can be captured. No-op unless RCCE_CAMDIST=<units> is set.
        if let Ok(cv) = std::env::var("RCCE_CAMDIST") {
            if let Ok(d) = cv.parse::<f32>() {
                self.cam_dist = zoom_step(d, 0.0);
            }
        }
        // Headless script-input + progress-bar self-test (TGT-8): inject a
        // Headless remote-attack self-test (CBT-3): make the nearest actor play
        // its attack swing so it's capturable without a hostile NPC. No-op unless
        // RCCE_REMOTEATTACK=<frame> set.
        if let Ok(av) = std::env::var("RCCE_REMOTEATTACK") {
            if let Ok(at) = av.parse::<u64>() {
                if self.frames >= at {
                    if let Some(net) = self.net.as_mut() {
                        // Re-arm every living actor each frame so whichever is in
                        // frame (incl. a humanoid) holds the swing across capture.
                        let rids: Vec<u16> = net
                            .world
                            .actors
                            .values()
                            .filter(|a| a.alive)
                            .map(|a| a.runtime_id)
                            .collect();
                        for rid in &rids {
                            net.world.attack_anims.insert(*rid, rcce_client::world::ATTACK_ANIM_SECS);
                        }
                        if self.frames == at {
                            println!("[remoteattack] frame {} attackers={rids:?}", self.frames);
                        }
                    }
                }
            }
        }
        // Headless image-window self-test (INV-5): open the WItemWindow popup
        // with a texture id so it's capturable without an I_Image item. No-op
        // unless RCCE_IMAGEWINDOWTEST=<frame> set; texture id from
        // RCCE_IMAGEWINDOWID (default: the first catalogued item's thumbnail).
        if let Ok(dv) = std::env::var("RCCE_IMAGEWINDOWTEST") {
            if let Ok(at) = dv.parse::<u64>() {
                if self.frames == at {
                    let id = std::env::var("RCCE_IMAGEWINDOWID")
                        .ok()
                        .and_then(|s| s.parse::<u16>().ok())
                        .or_else(|| store.first_item_thumbnail());
                    if let Some(id) = id {
                        self.image_window = Some(id);
                        println!("[imagewindowtest] frame {} opened image window tex {id}", self.frames);
                    }
                }
            }
        }
        // synthetic P_ScriptInput dialog and a P_ProgressBar so both render
        // without a scripted NPC. No-op unless RCCE_SCRIPTINPUTTEST=<frame> set.
        if let Ok(dv) = std::env::var("RCCE_SCRIPTINPUTTEST") {
            if let Ok(at) = dv.parse::<u64>() {
                if self.frames == at {
                    if let Some(net) = self.net.as_mut() {
                        net.world.script_input = Some(rcce_client::world::ScriptInput {
                            script_handle: 7,
                            masked: false,
                            title: "Name your blade".to_string(),
                            prompt: "The smith waits. What shall this sword be called?"
                                .to_string(),
                            text: "Frostbite".to_string(),
                        });
                        net.world.progress_bars.push(rcce_client::world::ProgressBar {
                            client_handle: 1,
                            color: [0.2, 0.7, 1.0],
                            x: 0.30,
                            y: 0.80,
                            w: 0.40,
                            h: 0.035,
                            max: 100,
                            value: 64,
                            text: "Forging...".to_string(),
                        });
                        println!("[scriptinputtest] frame {} injected dialog + progress bar", self.frames);
                    }
                }
            }
        }
        // Headless attack self-test (CBT-1): select the nearest living actor and
        // engage auto-attack so the combat loop is exercisable without a mouse.
        // No-op unless RCCE_ATTACK=<frame> is set.
        if let Ok(av) = std::env::var("RCCE_ATTACK") {
            if let Ok(at) = av.parse::<u64>() {
                if self.frames == at {
                    if let Some(net) = self.net.as_ref() {
                        if let Some(rid) = nearest_living_actor(&net.world, net.world.me_x, net.world.me_z) {
                            self.target = Some(rid);
                            self.attacking = true;
                            println!("[attack] frame {} engage rid={rid}", self.frames);
                        }
                    }
                }
            }
        }
        // Headless combat-anim self-test (ANIM-8): hold the local player's attack
        // pose AND kill the nearest actor (death pose) so both render in one
        // RCCE_SHOT. No-op unless RCCE_COMBATANIM=<frame> is set.
        if let Ok(cv) = std::env::var("RCCE_COMBATANIM") {
            if let Ok(at) = cv.parse::<u64>() {
                if self.frames == at {
                    self.me_attack_until = elapsed + 1.0e6;
                    if let Some(net) = self.net.as_mut() {
                        let (mx, mz) = (net.world.me_x, net.world.me_z);
                        if let Some(rid) = nearest_living_actor(&net.world, mx, mz) {
                            if let Some(a) = net.world.actors.get_mut(&rid) {
                                a.alive = false;
                            }
                            println!("[combatanim] frame {} attack-pose + killed rid={rid}", self.frames);
                        }
                    }
                }
            }
        }
        // Headless projectile self-test (PRJ-1): spawn a synthetic projectile a
        // few units in front of the player so its billboard is capturable. No-op
        // unless RCCE_PROJTEST=<frame> is set.
        if let Ok(pv) = std::env::var("RCCE_PROJTEST") {
            if let Ok(at) = pv.parse::<u64>() {
                if self.frames == at {
                    let (sy, cy) = self.cam_yaw.sin_cos();
                    // Place it to the player's right at chest height (clear of the
                    // body) drifting forward, so the billboard is unobstructed.
                    let right = [cy, -sy];
                    let fwd = [-sy, -cy];
                    if let Some(net) = self.net.as_mut() {
                        let (mx, my, mz) = (net.world.me_x, net.world.me_y, net.world.me_z);
                        // Mostly in front of the player (along the look axis), a
                        // touch to the right, so it projects near screen centre.
                        let sx = mx + fwd[0] * 10.0 + right[0] * 1.0;
                        let sy_ = my + 2.5;
                        let sz = mz + fwd[1] * 10.0 + right[1] * 1.0;
                        net.world.projectiles.push(rcce_client::world::Projectile {
                            x: sx,
                            y: sy_,
                            z: sz,
                            target_rid: 0,
                            tx: sx + fwd[0] * 200.0,
                            ty: sy_,
                            tz: sz + fwd[1] * 200.0,
                            homing: false,
                            speed: 4.0,
                        });
                        let (sw, sh) = (gfx.config.width as f32, gfx.config.height as f32);
                        match rcce_render::project(&vp, [sx, sy_, sz], sw, sh) {
                            Some((px, py)) => println!("[projtest] frame {} spawned, screen ({px:.0},{py:.0})", self.frames),
                            None => println!("[projtest] frame {} spawned, projects to None", self.frames),
                        }
                    }
                }
            }
        }
        // Headless chat-colour self-test (CHAT-2): inject coloured lines so the
        // chat log's colours are capturable. No-op unless RCCE_CHATTEST=<frame>.
        if let Ok(ct) = std::env::var("RCCE_CHATTEST") {
            if let Ok(at) = ct.parse::<u64>() {
                if self.frames == at {
                    if let Some(net) = self.net.as_mut() {
                        net.world.chat.push(("[system] a yellow notice".into(), [1.0, 1.0, 0.0, 1.0]));
                        net.world.chat.push(("[warn] a red warning".into(), [1.0, 0.2, 0.2, 1.0]));
                        net.world.chat.push(("[party] a green message".into(), [0.08, 0.86, 0.2, 1.0]));
                        net.world.chat.push(("<<rustbot>> my own blue line".into(), [0.0, 0.5, 1.0, 1.0]));
                        println!("[chattest] frame {} injected 4 coloured lines", self.frames);
                    }
                }
            }
        }
        // Headless chat-scrollback self-test (CHAT-3): inject 16 numbered lines
        // and set the scroll offset from RCCE_CHATSCROLL (default 0), so the
        // scrolled view is capturable. No-op unless RCCE_CHATSCROLLTEST=<frame>.
        if let Ok(cs) = std::env::var("RCCE_CHATSCROLLTEST") {
            if let Ok(at) = cs.parse::<u64>() {
                if self.frames == at {
                    if let Some(net) = self.net.as_mut() {
                        for n in 1..=16 {
                            net.world.chat.push((format!("chat line {n:02}"), [0.9, 0.9, 0.7, 1.0]));
                        }
                    }
                    self.chat_scroll = std::env::var("RCCE_CHATSCROLL")
                        .ok()
                        .and_then(|s| s.parse().ok())
                        .unwrap_or(0);
                    println!("[chatscroll] frame {} injected 16 lines, scroll={}", self.frames, self.chat_scroll);
                }
            }
        }
        // Headless chat-bubble self-test (CHAT-4): inject a bubble over the local
        // player (always framed by the follow camera). No-op unless
        // RCCE_BUBBLETEST=<frame> is set.
        if let Ok(bv) = std::env::var("RCCE_BUBBLETEST") {
            if let Ok(at) = bv.parse::<u64>() {
                if self.frames == at {
                    if let Some(net) = self.net.as_mut() {
                        let rid = net.world.my_runtime_id;
                        net.world
                            .pending_bubbles
                            .push((rid, "Hello, traveler!".into(), [0.4, 1.0, 0.5, 1.0]));
                        println!("[bubbletest] frame {} bubble over rid={rid}", self.frames);
                    }
                }
            }
        }
        // Headless quest-log self-test (QST-1): inject quests + open the panel.
        // No-op unless RCCE_QUESTTEST=<frame> is set.
        if let Ok(qv) = std::env::var("RCCE_QUESTTEST") {
            if let Ok(at) = qv.parse::<u64>() {
                if self.frames == at {
                    if let Some(net) = self.net.as_mut() {
                        net.world.quests.push(rcce_client::world::Quest {
                            name: "Find the Lost Sword".into(),
                            status: "Search the old ruins to the north.".into(),
                            color: [1.0, 1.0, 0.4, 1.0],
                            completed: false,
                        });
                        net.world.quests.push(rcce_client::world::Quest {
                            name: "Greet the Mayor".into(),
                            status: "Done - you spoke with the mayor.".into(),
                            color: [0.5, 1.0, 0.5, 1.0],
                            completed: true,
                        });
                    }
                    self.show_quests = true;
                    println!("[questtest] frame {} injected 2 quests + opened panel", self.frames);
                }
            }
        }
        // Headless quest-scroll self-test: inject 8 quests (enough to overflow the
        // window) + scroll, so the range indicator / clip / affordances show. No-op
        // unless RCCE_QUESTSCROLL=<frame> is set.
        if let Ok(qv) = std::env::var("RCCE_QUESTSCROLL") {
            if let Ok(at) = qv.parse::<u64>() {
                if self.frames == at {
                    if let Some(net) = self.net.as_mut() {
                        net.world.quests = (0..8)
                            .map(|i| rcce_client::world::Quest {
                                name: format!("Quest number {:02}", i + 1),
                                status: format!("Objective {}: travel to the far reaches and report back.", i + 1),
                                color: [0.9, 0.9, 0.5, 1.0],
                                completed: i % 3 == 0,
                            })
                            .collect();
                    }
                    self.show_quests = true;
                    self.quest_scroll = 2;
                    println!("[questscroll] frame {} injected 8 quests, scrolled to 2", self.frames);
                }
            }
        }
        // Headless party self-test (PTY-1): inject party names + open the panel.
        // No-op unless RCCE_PARTYTEST=<frame> is set.
        if let Ok(pv) = std::env::var("RCCE_PARTYTEST") {
            if let Ok(at) = pv.parse::<u64>() {
                if self.frames == at {
                    if let Some(net) = self.net.as_mut() {
                        net.world.party = vec!["Aldric".into(), "Mira".into(), "Thorne".into()];
                    }
                    self.show_party = true;
                    println!("[partytest] frame {} injected 3 party members + opened panel", self.frames);
                }
            }
        }
        // Headless spellbook self-test (SPL-1): inject known spells + open the
        // window. No-op unless RCCE_SPELLBOOKTEST=<frame> is set.
        if let Ok(kv) = std::env::var("RCCE_SPELLBOOKTEST") {
            if let Ok(at) = kv.parse::<u64>() {
                if self.frames == at {
                    if let Some(net) = self.net.as_mut() {
                        use rcce_client::world::KnownSpell;
                        net.world.known_spells = vec![
                            KnownSpell { id: 12, name: "Fireball".into(), level: 3, known_index: 0 },
                            KnownSpell { id: 7, name: "Heal".into(), level: 2, known_index: 1 },
                            KnownSpell { id: 21, name: "Lightning Bolt".into(), level: 1, known_index: 2 },
                        ];
                    }
                    self.show_spellbook = true;
                    println!("[spellbooktest] frame {} injected 3 known spells + opened window", self.frames);
                }
            }
        }
        // Headless spellbook-scroll self-test: inject 18 known spells, open the
        // window, and scroll down so the range indicator + later spells + the up
        // affordance are capturable. No-op unless RCCE_SPELLSCROLL=<frame> is set.
        if let Ok(sv) = std::env::var("RCCE_SPELLSCROLL") {
            if let Ok(at) = sv.parse::<u64>() {
                if self.frames == at {
                    if let Some(net) = self.net.as_mut() {
                        use rcce_client::world::KnownSpell;
                        net.world.known_spells = (0..18)
                            .map(|i| KnownSpell { id: 100 + i as u16, name: format!("Spell {:02}", i + 1), level: (i % 5 + 1) as u16, known_index: i as u16 })
                            .collect();
                    }
                    self.show_spellbook = true;
                    self.spellbook_scroll = 5; // show spells 6..15
                    println!("[spellscroll] frame {} injected 18 spells, scrolled to 5", self.frames);
                }
            }
        }
        // Headless memorise self-test (SPL-4): inject known spells, open the
        // spellbook, mark one memorised, and start a memorise on another so the
        // progress bar + memorised dot are capturable. No-op unless
        // RCCE_MEMORISE=<frame> is set.
        if let Ok(mv) = std::env::var("RCCE_MEMORISE") {
            if let Ok(at) = mv.parse::<u64>() {
                if self.frames == at {
                    if let Some(net) = self.net.as_mut() {
                        use rcce_client::world::KnownSpell;
                        if net.world.known_spells.is_empty() {
                            net.world.known_spells = vec![
                                KnownSpell { id: 12, name: "Fireball".into(), level: 3, known_index: 0 },
                                KnownSpell { id: 7, name: "Heal".into(), level: 2, known_index: 1 },
                                KnownSpell { id: 21, name: "Lightning Bolt".into(), level: 1, known_index: 2 },
                            ];
                        }
                    }
                    self.show_spellbook = true;
                    self.memorised.insert(12); // Fireball (id 12) already memorised
                    // Inline (not toggle_memorise): self.gfx is mutably borrowed in
                    // this scope, so only disjoint-field access is legal here.
                    self.memorising = Some((1, self.start.elapsed().as_secs_f32()));
                    let pkt = rcce_client::net::memorise_packet(1);
                    if let Some(net) = self.net.as_mut() {
                        net.transport.send(net.peer, rcce_net::packet_id::SPELL_UPDATE, &pkt, true);
                    }
                    println!("[memorise] frame {} marked idx0 memorised + started idx1", self.frames);
                }
            }
        }
        // Headless action-bar self-test (drag-drop result): inject a sheet with
        // memorised spells + an EXPLICIT, non-contiguous hotbar layout (slots 0,1,4
        // filled, 2,3 empty) — a layout the memorised auto-fill could never produce
        // — so the drag-drop render path is capturable. No-op unless
        // RCCE_ACTIONBARTEST=<frame> is set.
        if let Ok(av) = std::env::var("RCCE_ACTIONBARTEST") {
            if let Ok(at) = av.parse::<u64>() {
                if self.frames == at {
                    use rcce_client::fetch::{CharacterSheet, SpellInfo};
                    use rcce_client::world::KnownSpell;
                    let mk = |id: u16, name: &str, lvl: u16| SpellInfo {
                        id,
                        level: lvl,
                        thumb_tex: 0,
                        recharge: 1500,
                        name: name.to_string(),
                        description: format!("{name} — injected for the action-bar self-test."),
                        memorised: true,
                    };
                    self.sheet = Some(CharacterSheet {
                        spells: vec![
                            mk(12, "Fireball", 3),
                            mk(7, "Heal", 2),
                            mk(21, "Lightning Bolt", 1),
                        ],
                        ..Default::default()
                    });
                    if let Some(net) = self.net.as_mut() {
                        net.world.known_spells = vec![
                            KnownSpell { id: 12, name: "Fireball".into(), level: 3, known_index: 0 },
                            KnownSpell { id: 7, name: "Heal".into(), level: 2, known_index: 1 },
                            KnownSpell { id: 21, name: "Lightning Bolt".into(), level: 1, known_index: 2 },
                        ];
                    }
                    self.memorised = [12u16, 7, 21].into_iter().collect(); // ids, not indices
                    self.action_bar = [None; 12];
                    self.action_bar[0] = Some(HotbarEntry::Spell(12)); // Fireball, slot 1
                    self.action_bar[1] = Some(HotbarEntry::Spell(7)); // Heal, slot 2
                    self.action_bar[4] = Some(HotbarEntry::Spell(21)); // Lightning, slot 5
                    self.action_bar[6] = Some(HotbarEntry::Item(1)); // an item on slot 7
                    self.show_spellbook = true;
                    // Persist to the server (P_ActionBarUpdate) so a relog loads it
                    // back. Inlined (not persist_action_bar): self.gfx is mutably
                    // borrowed in this render scope, so only disjoint-field access
                    // is legal — a `&mut self` method call would conflict.
                    for slot in 0..12usize {
                        let pkt = match self.action_bar[slot] {
                            Some(HotbarEntry::Spell(id)) => self
                                .sheet
                                .as_ref()
                                .and_then(|s| s.spells.iter().find(|sp| sp.id == id))
                                .map(|sp| rcce_client::net::action_bar_spell_packet(slot as u8, &sp.name)),
                            Some(HotbarEntry::Item(id)) => Some(rcce_client::net::action_bar_item_packet(slot as u8, id)),
                            None => None,
                        };
                        if let Some(pkt) = pkt {
                            if let Some(net) = self.net.as_mut() {
                                net.transport.send(net.peer, rcce_net::packet_id::ACTION_BAR_UPDATE, &pkt, true);
                            }
                        }
                    }
                    println!("[actionbartest] frame {} explicit hotbar [0]=Fireball [1]=Heal [4]=Lightning [6]=Item(1) + persisted", self.frames);
                }
            }
        }
        // Headless screen-flash self-test (ENV-6): inject a red flash. No-op
        // unless RCCE_FLASHTEST=<frame> is set.
        if let Ok(fv) = std::env::var("RCCE_FLASHTEST") {
            if let Ok(at) = fv.parse::<u64>() {
                if self.frames == at {
                    if let Some(net) = self.net.as_mut() {
                        net.world.flash = Some(rcce_client::world::ScreenFlash {
                            color: [1.0, 0.1, 0.1],
                            alpha: 0.6,
                            length: 3.0,
                        });
                        println!("[flashtest] frame {} red flash", self.frames);
                    }
                }
            }
        }
        // Headless panel self-test: open the character/inventory panel so its
        // contents (HUD-8 sheet) are capturable. No-op unless RCCE_PANEL=<frame>.
        if let Ok(pl) = std::env::var("RCCE_PANEL") {
            if let Ok(at) = pl.parse::<u64>() {
                if self.frames == at {
                    self.show_inventory = true;
                }
            }
        }
        // Headless vendor self-test: inject a synthetic vendor trade + a couple of
        // backpack items and open the inventory, so the vendor window (icons +
        // sell drop-zone footer) and the side-by-side inventory are capturable.
        // No-op unless RCCE_VENDORTEST=<frame> is set.
        if let Ok(vv) = std::env::var("RCCE_VENDORTEST") {
            if let Ok(at) = vv.parse::<u64>() {
                if self.frames == at {
                    use rcce_client::fetch::InvItem;
                    use rcce_client::trade::{TradeKind, TradeOffer, TradeWindow};
                    if let Some(net) = self.net.as_mut() {
                        net.world.current_trade = Some(TradeWindow {
                            kind: TradeKind::Npc,
                            offers: vec![
                                TradeOffer { item_id: 1, amount: 1, server_trade_id: 1001 },
                                TradeOffer { item_id: 2, amount: 5, server_trade_id: 1002 },
                                TradeOffer { item_id: 3, amount: 1, server_trade_id: 1003 },
                            ],
                        });
                        net.world.me_inventory.insert(14, InvItem { slot: 14, item_id: 1, amount: 1, health: 100 });
                        net.world.me_inventory.insert(15, InvItem { slot: 15, item_id: 2, amount: 3, health: 100 });
                    }
                    self.show_inventory = true;
                    // Stage a buy (offer 1) + a sell (slot 14) so the basket /
                    // net-gold / Confirm button are capturable.
                    self.pending_buys = vec![1];
                    self.pending_sells = vec![(14, 1)];
                    println!("[vendortest] frame {} injected vendor (3 offers) + 2 backpack items + staged 1 buy/1 sell", self.frames);
                }
            }
        }
        // Headless vitals self-test: inject Health + Energy (attr 1) values so
        // both vital bars render with their numeric "cur/max" + name. No-op unless
        // RCCE_VITALSTEST=<frame> is set.
        if let Ok(vt) = std::env::var("RCCE_VITALSTEST") {
            if let Ok(at) = vt.parse::<u64>() {
                if self.frames == at {
                    if let Some(net) = self.net.as_mut() {
                        net.world.me_health = 85;
                        net.world.me_health_max = 100;
                        net.world.me_attributes.insert(0, (85, 100)); // Health
                        net.world.me_attributes.insert(1, (50, 80)); // Energy
                    }
                    println!("[vitalstest] frame {} injected Health 85/100 + Energy 50/80", self.frames);
                }
            }
        }
        // Headless item-menu self-test: inject a backpack item + open its
        // right-click context menu (Use/Equip/Drop/Drop All) for capture. No-op
        // unless RCCE_ITEMMENU=<frame> is set.
        if let Ok(im) = std::env::var("RCCE_ITEMMENU") {
            if let Ok(at) = im.parse::<u64>() {
                if self.frames == at {
                    use rcce_client::fetch::InvItem;
                    if let Some(net) = self.net.as_mut() {
                        net.world.me_inventory.insert(14, InvItem { slot: 14, item_id: 1, amount: 5, health: 100 });
                    }
                    self.show_inventory = true;
                    self.item_menu = Some(ItemMenu {
                        slot: 14,
                        x: 360.0,
                        y: 280.0,
                        items: vec![
                            ("Use", ItemAction::Use),
                            ("Equip", ItemAction::Equip),
                            ("Drop", ItemAction::Drop),
                            ("Drop All", ItemAction::DropAll),
                        ],
                    });
                    println!("[itemmenu] frame {} opened item menu over slot 14", self.frames);
                }
            }
        }
        // Headless memorise-sync self-test: sheet + known spells + a LIVE memorised
        // set (ids), action bar left to auto-fill + spellbook open, so the spellbook
        // dots and the hotbar auto-fill can be checked to agree. No-op unless
        // RCCE_MEMSYNC=<frame> is set.
        if let Ok(ms) = std::env::var("RCCE_MEMSYNC") {
            if let Ok(at) = ms.parse::<u64>() {
                if self.frames == at {
                    use rcce_client::fetch::{CharacterSheet, SpellInfo};
                    use rcce_client::world::KnownSpell;
                    let mk = |id: u16, name: &str, lvl: u16| SpellInfo {
                        id, level: lvl, thumb_tex: 0, recharge: 1500, name: name.to_string(),
                        description: String::new(), memorised: false,
                    };
                    self.sheet = Some(CharacterSheet {
                        spells: vec![mk(12, "Fireball", 3), mk(7, "Heal", 2), mk(21, "Lightning Bolt", 1)],
                        ..Default::default()
                    });
                    if let Some(net) = self.net.as_mut() {
                        net.world.known_spells = vec![
                            KnownSpell { id: 12, name: "Fireball".into(), level: 3, known_index: 0 },
                            KnownSpell { id: 7, name: "Heal".into(), level: 2, known_index: 1 },
                            KnownSpell { id: 21, name: "Lightning Bolt".into(), level: 1, known_index: 2 },
                        ];
                    }
                    // Fireball + Heal memorised (live set, by id); action bar left
                    // empty so it auto-fills from the SAME set.
                    self.memorised = [12u16, 7].into_iter().collect();
                    self.action_bar = [None; 12];
                    self.show_spellbook = true;
                    println!("[memsync] frame {} spellbook + auto-fill share memorised {{12,7}}", self.frames);
                }
            }
        }
        // Headless live-gold self-test: drive me_gold to a value distinct from the
        // sheet's login gold + open the inventory, so the HUD gold readouts can be
        // checked to follow the LIVE balance. No-op unless RCCE_GOLDTEST=<frame>.
        if let Ok(gv) = std::env::var("RCCE_GOLDTEST") {
            if let Ok(at) = gv.parse::<u64>() {
                if self.frames == at {
                    if let Some(net) = self.net.as_mut() {
                        // Drive the LIVE balance below the seeded login gold (as a
                        // P_GoldChange "D" would) so the HUD must read me_gold.
                        net.world.me_gold = (net.world.me_gold - 758).max(0);
                    }
                    self.show_inventory = true;
                    let g = self.net.as_ref().map(|n| n.world.me_gold).unwrap_or(0);
                    println!("[goldtest] frame {} me_gold now {g}", self.frames);
                }
            }
        }
        // Headless quantity-prompt self-test: inject a stack + open the sell
        // quantity modal at a partial value. No-op unless RCCE_QTYPROMPT=<frame>.
        if let Ok(qp) = std::env::var("RCCE_QTYPROMPT") {
            if let Ok(at) = qp.parse::<u64>() {
                if self.frames == at {
                    use rcce_client::fetch::InvItem;
                    if let Some(net) = self.net.as_mut() {
                        net.world.me_inventory.insert(20, InvItem { slot: 20, item_id: 1, amount: 25, health: 100 });
                    }
                    self.show_inventory = true;
                    self.qty_prompt = Some(QtyPrompt { slot: 20, item_id: 1, max: 25, qty: 8, action: QtyAction::Sell });
                    println!("[qtyprompt] frame {} opened sell-quantity modal (8/25)", self.frames);
                }
            }
        }
        // Headless buff self-test: inject status effects (two with real icon
        // textures borrowed from item thumbnails, one without) so the buff icons +
        // name-pill fallback are capturable. No-op unless RCCE_BUFFTEST=<frame>.
        if let Ok(bv) = std::env::var("RCCE_BUFFTEST") {
            if let Ok(at) = bv.parse::<u64>() {
                if self.frames == at {
                    use rcce_client::world::ActiveEffect;
                    let tex = |id: u16| store.item_def(id).map(|d| d.thumbnail_tex_id).filter(|&t| t >= 0).map(|t| t as u16).unwrap_or(0);
                    let (t1, t2) = (tex(1), tex(2));
                    if let Some(net) = self.net.as_mut() {
                        net.world.active_effects = vec![
                            ActiveEffect { id: 1, texture_id: t1, name: "Strength".into() },
                            ActiveEffect { id: 2, texture_id: t2, name: "Poison".into() },
                            ActiveEffect { id: 3, texture_id: 0, name: "Haste".into() },
                        ];
                    }
                    println!("[bufftest] frame {} injected 3 effects (icon tex {t1},{t2},0)", self.frames);
                }
            }
        }
        // Day/night phase, in priority order:
        //   1. RCCE_PHASE — pins a fixed phase (screenshots / tests).
        //   2. the SERVER clock (P_FetchActors "E" block) — so dusk/night follow
        //      the world's time-of-day, like Blitz.
        //   3. RCCE_DAYNIGHT_SECS — a cosmetic free-running local cycle.
        //   4. noon (0.5) — bright, stable default.
        let server_phase = self.net.as_ref().and_then(|n| n.world.day_phase());
        let phase = std::env::var("RCCE_PHASE")
            .ok()
            .and_then(|s| s.parse::<f32>().ok())
            .or(server_phase)
            .unwrap_or_else(|| {
                match std::env::var("RCCE_DAYNIGHT_SECS").ok().and_then(|s| s.parse::<f32>().ok()) {
                    Some(cycle) => rcce_client::daynight::phase_at(elapsed, cycle),
                    None => 0.5, // noon — bright, stable, like the real client
                }
            });
        let sky = rcce_client::daynight::daynight(phase);
        let mut fog_dn = rcce_client::daynight::modulate(self.fog_color, &sky);
        let ambient_dn = rcce_client::daynight::modulate(self.ambient, &sky);
        // Underwater (Blitz CameraUnderwater): when the camera dips below a water
        // plane, tint the fog/clear to the water colour and clamp to a short murky
        // view distance. The full-screen water wash added to the overlay below also
        // hides the sky/sun (Blitz hides Sky/Stars/Cloud entities underwater).
        let underwater = underwater_color(&self.water_planes, eye);
        let mut fog_near_eff = self.fog_near;
        let mut fog_far_eff = self.fog_far;
        if let Some(wc) = underwater {
            fog_dn = [wc[0] * 0.7, wc[1] * 0.7, wc[2] * 0.7];
            fog_near_eff = 2.0;
            fog_far_eff = 60.0;
        }
        // Move the sun with the time-of-day so shadows rotate + lengthen across the
        // day — but only when day/night is actually driving the phase (server clock
        // / RCCE_PHASE / RCCE_DAYNIGHT). With the static noon default, keep the
        // zone's authored light direction.
        let daynight_active = server_phase.is_some()
            || std::env::var_os("RCCE_PHASE").is_some()
            || std::env::var_os("RCCE_DAYNIGHT_SECS").is_some();
        let light_dir = if daynight_active {
            rcce_client::daynight::sun_dir(phase)
        } else {
            self.light_dir
        };
        // Particles: advance the sim + upload this frame's camera-facing billboards.
        // `&mut self.emitters` is disjoint from the live gfx/view/store borrows.
        let pdt = (elapsed - self.prev_elapsed).clamp(0.0, 0.1);
        let mut pbatches = particle_batches(&mut self.emitters, eye, cam_target, pdt);
        // Projectiles: a depth-correct glowing orb + motion trail, appended as an
        // additive particle batch (so terrain/scenery occludes them) — replaces the
        // old flat 2D overlay square. Disjoint borrow of `self.net` vs the gfx/view
        // borrows above.
        if let Some(net) = self.net.as_ref() {
            if !net.world.projectiles.is_empty() {
                let mut verts = Vec::new();
                projectile_billboards(&net.world.projectiles, eye, cam_target, &mut verts);
                if !verts.is_empty() {
                    pbatches.push((Some(projectile_glow_image()), true, verts));
                }
            }
        }
        view.set_particles(&gfx.device, &gfx.queue, &pbatches);
        view.render(
            &gfx.device,
            &gfx.queue,
            &tview,
            vp,
            eye,
            fog_dn,
            fog_near_eff,
            fog_far_eff,
            ambient_dn,
            light_dir,
            wgpu::Color {
                r: fog_dn[0] as f64,
                g: fog_dn[1] as f64,
                b: fog_dn[2] as f64,
                a: 1.0,
            },
            self.cam_yaw,
            elapsed,
            rcce_client::daynight::night_factor(phase),
            cam_target,
        );

        // (Headless RCCE_SHOT capture moved BELOW the overlay build so the PNG
        // includes the HUD / nameplates / target panel — see the offscreen
        // capture just before the surface overlay present.)

        // 2D overlay: nameplates + health bars over actors, and a player HUD.
        let target_rid = self.target;
        if let Some(overlay) = self.overlay.as_mut() {
            let (sw, sh) = (gfx.config.width as f32, gfx.config.height as f32);
            let white = [1.0, 1.0, 1.0, 1.0];

            // Underwater wash: a translucent full-screen water-colour tint, drawn
            // FIRST so it sits over the (already murk-fogged) world + sky but under
            // the HUD/nameplates — hiding the sky/sun and giving the submerged look.
            if let Some(wc) = underwater {
                overlay.rect(0.0, 0.0, sw, sh, [wc[0], wc[1], wc[2], 0.6]);
            }

            // Weather particles (rain/snow) — drawn first so they sit behind the
            // HUD/nameplates. Driven by the zone's weather byte.
            let wkind = self
                .net
                .as_ref()
                .map(|n| rcce_client::weather::weather_from_byte(n.world.zone.weather))
                .unwrap_or(rcce_client::weather::Weather::Clear);
            let dt = (elapsed - self.prev_elapsed).clamp(0.0, 0.1);
            self.prev_elapsed = elapsed;
            self.weather.update(dt, sw, sh, wkind);
            match wkind {
                // Storm rains too, with a slightly heavier/greyer streak.
                rcce_client::weather::Weather::Rain | rcce_client::weather::Weather::Storm => {
                    let streak = if wkind == rcce_client::weather::Weather::Storm {
                        (2.0, 11.0, [0.55, 0.6, 0.75, 0.6])
                    } else {
                        (1.5, 9.0, [0.6, 0.7, 0.9, 0.5])
                    };
                    for p in self.weather.particles() {
                        overlay.rect(p.x, p.y, streak.0, streak.1, streak.2);
                    }
                }
                rcce_client::weather::Weather::Snow => {
                    for p in self.weather.particles() {
                        overlay.rect(p.x, p.y, 3.0, 3.0, [1.0, 1.0, 1.0, 0.8]);
                    }
                }
                rcce_client::weather::Weather::Clear => {}
            }

            // Compass strip (top-centre) at the real Interface.dat `compass` rect,
            // scrolling with the camera heading. Always on (uses cam_yaw), like
            // Client.exe's compass.
            if let Some(comp) = store.interface().map(|i| i.compass) {
                if comp.w > 0.0 && comp.h > 0.0 {
                    let (cx0, cy0, cw, chh) = comp.px(sw, sh);
                    let center = cx0 + cw * 0.5;
                    overlay.rect(cx0, cy0, cw, chh, [0.0, 0.25, 0.0, 0.4]);
                    // Centre reference tick (dead ahead).
                    overlay.rect(center - 0.5, cy0, 1.5, chh, [0.7, 1.0, 0.7, 0.9]);
                    use std::f32::consts::PI;
                    for (off, label) in compass_marks(self.cam_yaw, PI) {
                        let mx = center + off * cw;
                        if label.is_empty() {
                            // Intercardinal tick.
                            overlay.rect(mx - 0.5, cy0 + chh * 0.5, 1.0, chh * 0.5, [0.4, 0.8, 0.4, 0.8]);
                        } else {
                            let tw = rcce_render::font::text_width(label, 1.0);
                            overlay.text_shadow(mx - tw * 0.5, cy0 + 1.0, 1.0, label, [0.7, 1.0, 0.7, 1.0]);
                        }
                    }
                }
            }

            if let Some(net) = self.net.as_ref() {
                for a in net.world.actors.values() {
                    if !a.alive {
                        continue;
                    }
                    // Project at the actor's RENDERED position: the smoothed
                    // render x/z, and the terrain-seated feet Y (the body stands on
                    // height_at(x,z), not the raw server `a.y` collision pivot —
                    // projecting a.y floated the nameplate/reticle high above the
                    // body). Falls back to a.y where there's no ground sample.
                    let gy = self
                        .height_field
                        .as_ref()
                        .and_then(|h| h.height_at(a.render_x, a.render_z))
                        .unwrap_or(a.y);
                    if let Some((px, py)) = rcce_render::project(&vp, [a.render_x, gy + 5.5, a.render_z], sw, sh) {
                        let frac = if a.health_max > 0 {
                            a.health as f32 / a.health_max as f32
                        } else {
                            1.0
                        };
                        let is_target = target_rid == Some(a.runtime_id);
                        let col = if is_target {
                            [1.0, 0.85, 0.2, 1.0]
                        } else if a.is_player {
                            [0.4, 0.7, 1.0, 1.0]
                        } else {
                            [0.9, 0.3, 0.3, 1.0]
                        };
                        overlay.bar(px - 24.0, py - 14.0, 48.0, 5.0, frac, col);
                        if is_target {
                            // Target brackets around the bar.
                            overlay.rect(px - 28.0, py - 15.0, 2.0, 7.0, col);
                            overlay.rect(px + 26.0, py - 15.0, 2.0, 7.0, col);
                            // Selection reticle: four corner brackets around the
                            // actor's screen extent (feet -> head), the on-screen
                            // analogue of the Blitz ActorSelectEN ground decal.
                            if let (Some((fx, fy)), Some((hx, hy))) = (
                                rcce_render::project(&vp, [a.render_x, gy, a.render_z], sw, sh),
                                rcce_render::project(&vp, [a.render_x, gy + 6.0, a.render_z], sw, sh),
                            ) {
                                let cxp = (fx + hx) * 0.5;
                                let (top, bot) = (hy.min(fy), hy.max(fy));
                                let halfw = ((bot - top) * 0.28).max(8.0);
                                let len = ((bot - top) * 0.22).max(6.0);
                                let th = 2.0;
                                for &(ex, sgn) in &[(cxp - halfw, 1.0f32), (cxp + halfw, -1.0)] {
                                    let x0 = if sgn > 0.0 { ex } else { ex - len };
                                    overlay.rect(x0, top, len, th, col);
                                    overlay.rect(x0, bot - th, len, th, col);
                                    let vx = if sgn > 0.0 { ex } else { ex - th };
                                    overlay.rect(vx, top, th, len, col);
                                    overlay.rect(vx, bot - len, th, len, col);
                                }
                            }
                        }
                        if !a.name.is_empty() {
                            let tw = rcce_render::font::text_width(&a.name, 1.0);
                            let nc = if is_target { col } else { white };
                            overlay.text_shadow(px - tw * 0.5, py - 26.0, 1.0, &a.name, nc);
                        }
                        // Equipped weapon (from P_InventoryUpdate "O") under the name.
                        if a.equipped[0] != 0xFFFF {
                            let wname = store.item_name(a.equipped[0]);
                            let tw = rcce_render::font::text_width(&wname, 1.0);
                            overlay.text_shadow(px - tw * 0.5, py - 38.0, 1.0, &wname, [0.85, 0.85, 0.7, 1.0]);
                        }
                    }
                }

                // Char-Interaction target panel (TGT-1/2): the selected actor's
                // name + HP, top-centre below the compass — the Rust analogue of
                // Client.exe's WCharInteract window. Cleared automatically when
                // the target dies/zones (on_actor_dead clears self.target).
                if let Some(rid) = target_rid {
                    if let Some(t) = net.world.actors.get(&rid).filter(|a| a.alive) {
                        let (pw, ph) = (240.0f32, 48.0f32);
                        let px0 = (sw - pw) * 0.5;
                        let py0 = 40.0;
                        overlay.rect(px0, py0, pw, ph, [0.0, 0.0, 0.0, 0.55]);
                        overlay.rect(px0, py0, pw, 2.0, [1.0, 0.85, 0.2, 0.9]);
                        let name: &str = if t.name.is_empty() { "Target" } else { t.name.as_str() };
                        overlay.text_shadow(px0 + 8.0, py0 + 6.0, 1.2, name, [1.0, 0.92, 0.6, 1.0]);
                        let frac = if t.health_max > 0 {
                            (t.health as f32 / t.health_max as f32).clamp(0.0, 1.0)
                        } else {
                            1.0
                        };
                        overlay.bar(px0 + 8.0, py0 + 28.0, pw - 16.0, 12.0, frac, [0.85, 0.25, 0.25, 1.0]);
                        let hp = format!("{} / {}", t.health.max(0), t.health_max.max(0));
                        let tw = rcce_render::font::text_width(&hp, 1.0);
                        overlay.text_shadow(px0 + pw * 0.5 - tw * 0.5, py0 + 29.0, 1.0, &hp, [1.0, 1.0, 1.0, 1.0]);
                    }
                }

                // NPC dialog window (TGT-5): title + wrapped text + green
                // clickable option lines, left-anchored like Client.exe's
                // CreateDialog (0.02,0.15,0.32,0.5). Option hitboxes are saved
                // for hud_click; cleared every frame so they never go stale.
                self.dialog_hitboxes.clear();
                if let Some(dl) = &net.world.dialog {
                    let (dx, dy) = (0.02 * sw, 0.15 * sh);
                    let (dw, dh) = (0.34 * sw, 0.5 * sh);
                    overlay.rect(dx - 2.0, dy - 2.0, dw + 4.0, dh + 4.0, [0.6, 0.5, 0.2, 0.95]);
                    if overlay.has_texture("gui:InventoryBG") {
                        overlay.image(dx, dy, dw, dh, "gui:InventoryBG", [1.0, 1.0, 1.0, 1.0]);
                    } else {
                        overlay.rect(dx, dy, dw, dh, [0.04, 0.04, 0.07, 0.93]);
                    }
                    overlay.text_shadow(dx + 8.0, dy + 6.0, 1.3, &dl.title, [1.0, 0.92, 0.6, 1.0]);
                    let max_chars = (((dw - 18.0) / 6.5) as usize).max(8);
                    let mut ty = dy + 30.0;
                    for (text, col) in &dl.lines {
                        for wl in wrap_text(text, max_chars) {
                            overlay.text_shadow(dx + 8.0, ty, 1.0, &wl, *col);
                            ty += 14.0;
                        }
                    }
                    ty += 10.0;
                    let (mcx, mcy) = self.cursor;
                    for (i, opt) in dl.options.iter().enumerate() {
                        let oh = 18.0;
                        let hovered = mcx >= dx && mcx <= dx + dw && mcy >= ty && mcy < ty + oh;
                        let oc = if hovered { [0.5, 1.0, 0.65, 1.0] } else { [0.2, 0.9, 0.4, 1.0] };
                        overlay.text_shadow(dx + 12.0, ty + 3.0, 1.0, &format!("{}. {}", i + 1, opt), oc);
                        self.dialog_hitboxes.push((dx, ty, dw, oh));
                        ty += oh;
                    }
                }

                // Scripted progress bars (TGT-8 / P_ProgressBar): server-driven
                // labelled bars at fractional screen coords.
                for pb in &net.world.progress_bars {
                    let (bx, by, bw, bh) = (pb.x * sw, pb.y * sh, pb.w * sw, pb.h * sh);
                    let frac = if pb.max > 0 {
                        (pb.value as f32 / pb.max as f32).clamp(0.0, 1.0)
                    } else {
                        0.0
                    };
                    overlay.rect(bx - 2.0, by - 2.0, bw + 4.0, bh + 4.0, [0.0, 0.0, 0.0, 0.6]);
                    overlay.bar(bx, by, bw, bh, frac, [pb.color[0], pb.color[1], pb.color[2], 1.0]);
                    if !pb.text.is_empty() {
                        let tw = rcce_render::font::text_width(&pb.text, 1.0);
                        overlay.text_shadow(
                            bx + (bw - tw) * 0.5,
                            by + (bh - 12.0) * 0.5,
                            1.0,
                            &pb.text,
                            [1.0, 1.0, 1.0, 1.0],
                        );
                    }
                }

                // Scripted text-input dialog (TGT-8 / P_ScriptInput): a centred
                // modal with the title, wrapped prompt, an editable field showing
                // the (optionally masked) reply + caret, and submit/cancel hints.
                if let Some(si) = &net.world.script_input {
                    let (dw, dh) = (0.40 * sw, 0.22 * sh);
                    let (dx, dy) = ((sw - dw) * 0.5, (sh - dh) * 0.5);
                    overlay.rect(dx - 2.0, dy - 2.0, dw + 4.0, dh + 4.0, [0.6, 0.5, 0.2, 0.97]);
                    if overlay.has_texture("gui:InventoryBG") {
                        overlay.image(dx, dy, dw, dh, "gui:InventoryBG", [1.0, 1.0, 1.0, 1.0]);
                    } else {
                        overlay.rect(dx, dy, dw, dh, [0.05, 0.05, 0.08, 0.97]);
                    }
                    overlay.text_shadow(dx + 10.0, dy + 8.0, 1.3, &si.title, [1.0, 0.92, 0.6, 1.0]);
                    let max_chars = (((dw - 20.0) / 6.5) as usize).max(8);
                    let mut ty = dy + 32.0;
                    for wl in wrap_text(&si.prompt, max_chars) {
                        overlay.text_shadow(dx + 10.0, ty, 1.0, &wl, [0.85, 0.85, 0.85, 1.0]);
                        ty += 14.0;
                    }
                    // Input field.
                    let (fx, fy, fw, fh) = (dx + 10.0, dy + dh - 52.0, dw - 20.0, 22.0);
                    overlay.rect(fx, fy, fw, fh, [0.0, 0.0, 0.0, 0.6]);
                    overlay.rect(fx, fy, fw, 1.0, [0.5, 0.5, 0.55, 0.9]);
                    let shown: String = if si.masked {
                        "*".repeat(si.text.chars().count())
                    } else {
                        si.text.clone()
                    };
                    overlay.text_shadow(fx + 4.0, fy + 5.0, 1.1, &format!("{shown}_"), [1.0, 1.0, 1.0, 1.0]);
                    overlay.text_shadow(
                        dx + 10.0,
                        dy + dh - 22.0,
                        0.85,
                        "Enter = submit    Esc = cancel",
                        [0.6, 0.6, 0.65, 1.0],
                    );
                }

                // Image-item popup (INV-5 / WItemWindow): a centred window showing
                // the used image item's full texture (lazily registered from its
                // ImageID). Closed via ESC (UI-ESC chain). ref Interface3D.bb:4158.
                if let Some(img_id) = self.image_window {
                    let key = format!("image:{img_id}");
                    if !overlay.has_texture(&key) {
                        if let Some(im) =
                            store.texture_path(img_id).and_then(|p| rcce_data::texture::load(&p))
                        {
                            overlay.register_texture(&gfx.device, &gfx.queue, &key, im.width, im.height, &im.rgba);
                        }
                    }
                    let (ww, wh) = (sw * 0.55, sh * 0.7);
                    let (wx, wy) = ((sw - ww) * 0.5, (sh - wh) * 0.5);
                    overlay.rect(wx - 3.0, wy - 3.0, ww + 6.0, wh + 6.0, [0.6, 0.5, 0.2, 0.97]);
                    overlay.rect(wx, wy, ww, wh, [0.05, 0.05, 0.08, 0.97]);
                    if overlay.has_texture(&key) {
                        let pad = 12.0;
                        overlay.image(wx + pad, wy + pad, ww - pad * 2.0, wh - pad * 2.0 - 16.0, &key, [1.0, 1.0, 1.0, 1.0]);
                    } else {
                        overlay.text_shadow(wx + 14.0, wy + 14.0, 1.1, "[image unavailable]", [0.8, 0.8, 0.8, 1.0]);
                    }
                    overlay.text_shadow(wx + 14.0, wy + wh - 22.0, 0.85, "Esc = close", [0.6, 0.6, 0.65, 1.0]);
                }

                // Projectiles now render as depth-correct glowing orbs + trails in
                // the world (additive particle batch, see the set_particles call
                // above) — no 2D overlay marker, so they're occluded by terrain.

                // Chat bubbles (CHAT-4): a small label over the speaking actor's
                // head (or me), projected each frame.
                for (rid, (text, col, _)) in &self.bubbles {
                    let pos = if *rid == net.world.my_runtime_id {
                        Some([net.world.me_x, net.world.me_y, net.world.me_z])
                    } else {
                        net.world.actors.get(rid).map(|a| [a.x, a.y, a.z])
                    };
                    if let Some(p) = pos {
                        // Project a chest-height anchor, then place the bubble a
                        // fixed pixel distance above it (camera-distance-
                        // independent — a fixed world-Y offset over-shoots when
                        // the follow camera is close to the local player).
                        if let Some((px, py)) = rcce_render::project(&vp, [p[0], p[1] + 3.5, p[2]], sw, sh) {
                            let tw = rcce_render::font::text_width(text, 1.0);
                            let by = py - 42.0;
                            overlay.rect(px - tw * 0.5 - 4.0, by, tw + 8.0, 15.0, [0.0, 0.0, 0.0, 0.7]);
                            overlay.text_shadow(px - tw * 0.5, by + 2.0, 1.0, text, *col);
                        }
                    }
                }

                // Floating damage numbers, anchored over their target actor
                // (or me), rising and fading over their lifetime.
                for fl in self.floaters.iter() {
                    let pos = if fl.rid == net.world.my_runtime_id {
                        Some([net.world.me_x, net.world.me_y, net.world.me_z])
                    } else {
                        net.world.actors.get(&fl.rid).map(|a| [a.x, a.y, a.z])
                    };
                    let Some(p) = pos else { continue };
                    if let Some((px, py)) = rcce_render::project(&vp, [p[0], p[1] + 6.5, p[2]], sw, sh) {
                        let s = fl.damage.to_string();
                        let col = damage_color(fl.damage_type, fl.alpha(elapsed));
                        let tw = rcce_render::font::text_width(&s, 1.5);
                        overlay.text_shadow(px - tw * 0.5, py - 30.0 - fl.rise(elapsed), 1.5, &s, col);
                    }
                }

                // Minimap/radar at the real Interface.dat Radar rect (right
                // side), forward-up radar of nearby actors + loot.
                {
                    let (cx, cy, r) = match store.interface() {
                        Some(iface) => {
                            let rd = iface.radar;
                            let r = (rd.w * sw).min(rd.h * sh) * 0.5;
                            ((rd.x + rd.w * 0.5) * sw, (rd.y + rd.h * 0.5) * sh, r)
                        }
                        None => (74.0, 74.0, 64.0),
                    };
                    let yaw = self.cam_yaw;
                    let range = 140.0;
                    let (mx, mz) = (net.world.me_x, net.world.me_z);
                    overlay.rect(cx - r - 4.0, cy - r - 4.0, (r + 4.0) * 2.0, (r + 4.0) * 2.0, [0.0, 0.0, 0.0, 0.5]);
                    // Heading line (forward = up) then the player pip at centre.
                    overlay.rect(cx - 1.0, cy - r * 0.5, 2.0, r * 0.5, [0.4, 0.8, 0.4, 0.7]);
                    overlay.rect(cx - 2.0, cy - 2.0, 4.0, 4.0, [0.6, 1.0, 0.6, 1.0]);
                    for a in net.world.actors.values() {
                        if let Some((ox, oy)) = rcce_client::radar::world_to_radar(a.x - mx, a.z - mz, yaw, range, r) {
                            let col = if Some(a.runtime_id) == target_rid {
                                [1.0, 0.85, 0.2, 1.0]
                            } else if a.is_player {
                                [0.4, 0.7, 1.0, 1.0]
                            } else {
                                [0.95, 0.35, 0.35, 1.0]
                            };
                            overlay.rect(cx + ox - 2.0, cy + oy - 2.0, 4.0, 4.0, col);
                        }
                    }
                    for d in net.world.dropped_items.values() {
                        if let Some((ox, oy)) = rcce_client::radar::world_to_radar(d.x - mx, d.z - mz, yaw, range, r) {
                            overlay.rect(cx + ox - 1.5, cy + oy - 1.5, 3.0, 3.0, [1.0, 0.85, 0.3, 1.0]);
                        }
                    }
                }

                // Status-effect icons at the real Buffs rect (top-right). Blitz
                // draws each effect's IconTexID; the Rust client only showed a name
                // pill. Now: the real icon (lazily registered from the catalog),
                // name pill as the fallback, and the name on hover.
                if !net.world.active_effects.is_empty() {
                    let (bx0, by0) = match store.interface() {
                        Some(iface) => (iface.buffs.x * sw, iface.buffs.y * sh),
                        None => (10.0, 152.0),
                    };
                    let isz = 24.0f32;
                    let (cxp, cyp) = self.cursor;
                    let mut ex = bx0;
                    let mut hover: Option<(f32, f32, String)> = None;
                    for eff in &net.world.active_effects {
                        let key = format!("buff:{}", eff.texture_id);
                        if !overlay.has_texture(&key) {
                            if let Some(img) = store.texture_path(eff.texture_id).and_then(|p| rcce_data::texture::load(&p)) {
                                overlay.register_texture(&gfx.device, &gfx.queue, &key, img.width, img.height, &img.rgba);
                            }
                        }
                        let w = if overlay.has_texture(&key) {
                            overlay.rect(ex - 1.0, by0 - 1.0, isz + 2.0, isz + 2.0, [0.0, 0.0, 0.0, 0.5]);
                            overlay.image(ex, by0, isz, isz, &key, [1.0, 1.0, 1.0, 1.0]);
                            isz
                        } else {
                            // Name-pill fallback (the previous behaviour).
                            let label: String = eff.name.chars().take(12).collect();
                            let pillw = rcce_render::font::text_width(&label, 1.0) + 10.0;
                            overlay.rect(ex, by0, pillw, 14.0, [0.32, 0.16, 0.36, 0.82]);
                            overlay.text_shadow(ex + 5.0, by0 + 2.0, 1.0, &label, [1.0, 0.85, 1.0, 1.0]);
                            pillw
                        };
                        if cxp >= ex && cxp < ex + w && cyp >= by0 && cyp < by0 + isz {
                            hover = Some((ex, by0 + isz + 2.0, eff.name.clone()));
                        }
                        ex += w + 4.0;
                    }
                    // Hovered effect's full name as a tooltip just under its icon.
                    if let Some((tx, ty, name)) = hover {
                        let tw = rcce_render::font::text_width(&name, 1.0) + 8.0;
                        overlay.rect(tx, ty, tw, 14.0, [0.05, 0.05, 0.1, 0.94]);
                        overlay.text_shadow(tx + 4.0, ty + 2.0, 1.0, &name, [1.0, 1.0, 1.0, 1.0]);
                    }
                }

                // Dropped-item loot markers: a gold pip + name/amount at the
                // item's world position. "[E]" hint on the nearest in range.
                if !net.world.dropped_items.is_empty() {
                    let (mx, mz) = (net.world.me_x, net.world.me_z);
                    let nearest = net
                        .world
                        .dropped_items
                        .values()
                        .map(|d| (d.handle, (d.x - mx).powi(2) + (d.z - mz).powi(2)))
                        .min_by(|a, b| a.1.total_cmp(&b.1));
                    let gold = [1.0, 0.85, 0.3, 1.0];
                    for d in net.world.dropped_items.values() {
                        if let Some((px, py)) = rcce_render::project(&vp, [d.x, d.y + 1.2, d.z], sw, sh) {
                            // The item now renders as a 3D mesh on the ground (see the
                            // dynamic-instance build above); keep just the floating
                            // name/amount label + the [E] pickup hint.
                            let name = store.item_name(d.item_id);
                            let label = if d.amount > 1 { format!("{name} x{}", d.amount) } else { name };
                            let in_range = nearest.map(|(h, d2)| h == d.handle && d2 < 60.0 * 60.0).unwrap_or(false);
                            let label = if in_range { format!("{label}  [E]") } else { label };
                            let tw = rcce_render::font::text_width(&label, 1.0);
                            overlay.text_shadow(px - tw * 0.5, py - 16.0, 1.0, &label, gold);
                        }
                    }
                }

                // Player HUD: zone, HP bar + numbers, fps; chat log above it.
                let w = &net.world;
                let hpf = if w.me_health_max > 0 {
                    w.me_health as f32 / w.me_health_max as f32
                } else {
                    1.0
                };
                let fps = self.frames as f32 / elapsed.max(0.001);

                // Quest log panel (QST-1, toggled by L / the Quests button): each
                // quest's name + coloured status; completed quests in gold.
                if self.show_quests {
                    let (qx, qy, qw, qh) = quest_window_rect(sw, sh);
                    if overlay.has_texture("gui:QuestLogBG") {
                        overlay.image(qx, qy, qw, qh, "gui:QuestLogBG", [1.0, 1.0, 1.0, 1.0]);
                        overlay.rect(qx, qy, qw, 22.0, [0.0, 0.0, 0.0, 0.45]);
                    } else {
                        overlay.rect(qx, qy, qw, qh, [0.05, 0.06, 0.10, 0.94]);
                        overlay.rect(qx, qy, qw, 22.0, [0.15, 0.18, 0.28, 0.96]);
                    }
                    let total = w.quests.len();
                    // Keep at least the last quest reachable; variable-height rows
                    // are clipped at the window bottom rather than counted.
                    let qscroll = clamp_scroll(self.quest_scroll, total, 1);
                    self.quest_scroll = qscroll;
                    overlay.text_shadow(qx + 10.0, qy + 6.0, 1.3, "Quest Log", white);
                    if total > 0 {
                        overlay.text(qx + 92.0, qy + 8.0, 1.0, &format!("{} / {}", (qscroll + 1).min(total), total), [0.6, 0.7, 0.85, 1.0]);
                    }
                    overlay.text(qx + qw - 78.0, qy + 7.0, 1.0, "[L] close", [0.6, 0.6, 0.6, 1.0]);
                    let mut yy = qy + 30.0;
                    let foot = qy + qh - 12.0; // clip rows past here (no overflow)
                    if w.quests.is_empty() {
                        overlay.text(qx + 10.0, yy, 1.0, "No quests available.", [0.7, 0.7, 0.7, 1.0]);
                    }
                    let mut clipped = false;
                    for q in w.quests.iter().skip(qscroll) {
                        if yy + 13.0 > foot {
                            clipped = true;
                            break;
                        }
                        let title = if q.completed {
                            format!("{}  (Completed)", q.name)
                        } else {
                            q.name.clone()
                        };
                        let tcol = if q.completed { [1.0, 0.88, 0.4, 1.0] } else { white };
                        overlay.text_shadow(qx + 10.0, yy, 1.0, &title, tcol);
                        yy += 13.0;
                        for line in wrap_text(&q.status, 46) {
                            if yy + 12.0 > foot {
                                clipped = true;
                                break;
                            }
                            overlay.text(qx + 18.0, yy, 1.0, &line, q.color);
                            yy += 12.0;
                        }
                        yy += 6.0;
                    }
                    // Scroll affordances (wheel over the window).
                    if qscroll > 0 {
                        overlay.text(qx + qw - 18.0, qy + 28.0, 1.0, "▲", [0.7, 0.8, 1.0, 1.0]);
                    }
                    if clipped {
                        overlay.text(qx + qw - 18.0, qy + qh - 16.0, 1.0, "▼", [0.7, 0.8, 1.0, 1.0]);
                    }
                }

                // Party panel (PTY-1, toggled by P / the Party button): current
                // party member names (left of centre so it clears the quest log).
                if self.show_party {
                    let (pwd, phd) = (220.0f32, 160.0f32);
                    let (pxp, pyp) = (sw * 0.5 - pwd - 12.0, (sh - phd) * 0.5);
                    if overlay.has_texture("gui:PartyBG") {
                        overlay.image(pxp, pyp, pwd, phd, "gui:PartyBG", [1.0, 1.0, 1.0, 1.0]);
                        overlay.rect(pxp, pyp, pwd, 22.0, [0.0, 0.0, 0.0, 0.45]);
                    } else {
                        overlay.rect(pxp, pyp, pwd, phd, [0.05, 0.06, 0.10, 0.94]);
                        overlay.rect(pxp, pyp, pwd, 22.0, [0.15, 0.18, 0.28, 0.96]);
                    }
                    overlay.text_shadow(pxp + 10.0, pyp + 6.0, 1.3, "Party", white);
                    overlay.text(pxp + pwd - 78.0, pyp + 7.0, 1.0, "[P] close", [0.6, 0.6, 0.6, 1.0]);
                    let mut py2 = pyp + 30.0;
                    if w.party.is_empty() {
                        overlay.text(pxp + 10.0, py2, 1.0, "Not in a party.", [0.7, 0.7, 0.7, 1.0]);
                    }
                    for name in &w.party {
                        overlay.text_shadow(pxp + 10.0, py2, 1.0, name, [0.5, 1.0, 0.5, 1.0]);
                        py2 += 15.0;
                    }
                }

                // Spellbook window (SPL-1, toggled by K): the live known-spell list
                // (`World.known_spells`, populated by SPL-7) with name + rank, kept
                // name-sorted by the handler. Right of centre so it clears the party
                // panel; scrolls nothing yet (list is short in practice).
                self.spell_hitboxes.clear();
                if self.show_spellbook {
                    let (kxp, kyp, kwd, khd) = spellbook_rect(sw, sh);
                    if overlay.has_texture("gui:AbilitiesBG") {
                        overlay.image(kxp, kyp, kwd, khd, "gui:AbilitiesBG", [1.0, 1.0, 1.0, 1.0]);
                        overlay.rect(kxp, kyp, kwd, 22.0, [0.0, 0.0, 0.0, 0.45]);
                    } else {
                        overlay.rect(kxp, kyp, kwd, khd, [0.05, 0.06, 0.10, 0.94]);
                        overlay.rect(kxp, kyp, kwd, 22.0, [0.18, 0.15, 0.28, 0.96]);
                    }
                    // How many rows fit between the header and the footer (memorise
                    // bar / hint). Scroll lets a long known-spell list be reached in
                    // full (mouse wheel over the window).
                    const SPELL_ROWS: usize = 10;
                    let total = w.known_spells.len();
                    let scroll = clamp_scroll(self.spellbook_scroll, total, SPELL_ROWS);
                    self.spellbook_scroll = scroll;
                    let shown = format!("{}", total);
                    overlay.text_shadow(kxp + 10.0, kyp + 6.0, 1.3, "Spellbook", white);
                    if total > SPELL_ROWS {
                        // Range indicator (e.g. "3-12 / 18") + close hint.
                        overlay.text(kxp + 86.0, kyp + 8.0, 1.0, &format!("{}-{} / {}", scroll + 1, (scroll + SPELL_ROWS).min(total), shown), [0.6, 0.7, 0.85, 1.0]);
                    }
                    overlay.text(kxp + kwd - 78.0, kyp + 7.0, 1.0, "[K] close", [0.6, 0.6, 0.6, 1.0]);
                    let mut ky2 = kyp + 30.0;
                    if w.known_spells.is_empty() {
                        overlay.text(kxp + 10.0, ky2, 1.0, "No spells known.", [0.7, 0.7, 0.7, 1.0]);
                    }
                    // Each row is clickable to memorise the spell (SPL-4); a green
                    // dot marks the memorised ones. `i` is the ABSOLUTE known index
                    // (scroll offset applied) so memorise/drag use the right spell.
                    for (i, sp) in w.known_spells.iter().enumerate().skip(scroll).take(SPELL_ROWS) {
                        let memorised = self.memorised.contains(&sp.id);
                        let name_col = if memorised { [0.5, 1.0, 0.6, 1.0] } else { [0.7, 0.85, 1.0, 1.0] };
                        if memorised {
                            overlay.rect(kxp + 4.0, ky2 + 3.0, 4.0, 4.0, [0.4, 1.0, 0.5, 1.0]);
                        }
                        overlay.text_shadow(kxp + 12.0, ky2, 1.0, &sp.name, name_col);
                        let rank = format!("Rank {}", sp.level);
                        overlay.text(kxp + kwd - 64.0, ky2, 1.0, &rank, [0.85, 0.8, 0.6, 1.0]);
                        self.spell_hitboxes.push((kxp + 2.0, ky2 - 2.0, kwd - 4.0, 15.0, i));
                        ky2 += 16.0;
                    }
                    // Up/down "more" affordances when scrolled.
                    if scroll > 0 {
                        overlay.text(kxp + kwd - 18.0, kyp + 30.0, 1.0, "▲", [0.7, 0.8, 1.0, 1.0]);
                    }
                    if scroll + SPELL_ROWS < total {
                        overlay.text(kxp + kwd - 18.0, kyp + 30.0 + SPELL_ROWS as f32 * 16.0 - 14.0, 1.0, "▼", [0.7, 0.8, 1.0, 1.0]);
                    }
                    // Memorise progress bar (SPL-4) while a memorise is in flight.
                    if let Some((idx, start)) = self.memorising {
                        let prog = memorise_progress(start, elapsed);
                        let name = w.known_spells.get(idx).map(|s| s.name.as_str()).unwrap_or("spell");
                        let by = kyp + khd - 30.0;
                        overlay.text(kxp + 10.0, by - 13.0, 1.0, &format!("Memorising {name}…"), [0.85, 0.85, 0.6, 1.0]);
                        overlay.rect(kxp + 10.0, by, kwd - 20.0, 8.0, [0.0, 0.0, 0.0, 0.6]);
                        overlay.bar(kxp + 10.0, by, kwd - 20.0, 8.0, prog, [0.5, 0.8, 1.0, 1.0]);
                    } else {
                        overlay.text(kxp + 10.0, kyp + khd - 16.0, 1.0, "Click a spell to memorise", [0.55, 0.55, 0.6, 1.0]);
                    }
                }

                // Vitals bars at the real Interface.dat fractional positions
                // (Health top-left red, Energy below it blue, …), matching
                // Client.exe instead of an invented bottom HUD.
                if let Some(iface) = store.interface() {
                    for (i, a) in iface.attributes.iter().enumerate() {
                        if a.w <= 0.001 || a.h <= 0.001 {
                            continue;
                        }
                        let Some((val, max)) = vitals_value(
                            i,
                            w.health_stat,
                            w.me_health,
                            w.me_health_max,
                            &w.me_attributes,
                        ) else {
                            continue;
                        };
                        let (vx, vy, vw, vh) = a.px(sw, sh);
                        let frac = (val / max).clamp(0.0, 1.0);
                        let col = [a.rgb[0] as f32 / 255.0, a.rgb[1] as f32 / 255.0, a.rgb[2] as f32 / 255.0, 1.0];
                        overlay.rect(vx - 1.0, vy - 1.0, vw + 2.0, vh + 2.0, [0.0, 0.0, 0.0, 0.6]);
                        overlay.bar(vx, vy, vw, vh, frac, col);
                        // Numeric "cur/max" on every vital bar (Energy/mana too, not
                        // just Health) — Blitz shows the value on each. The attribute
                        // name (Health/Energy/…) right-aligned when it fits.
                        let s = format!("{}/{}", val as i32, max as i32);
                        overlay.text_shadow(vx + 3.0, vy + vh * 0.5 - 4.0, 1.0, &s, white);
                        if let Some(name) = store.attribute_name(i) {
                            let nw = rcce_render::font::text_width(name, 1.0);
                            let sw_ = rcce_render::font::text_width(&s, 1.0);
                            if nw + sw_ + 12.0 < vw {
                                overlay.text_shadow(vx + vw - nw - 3.0, vy + vh * 0.5 - 4.0, 1.0, name, [0.85, 0.85, 0.9, 0.9]);
                            }
                        }
                    }
                } else {
                    overlay.rect(10.0, sh - 56.0, 270.0, 48.0, [0.0, 0.0, 0.0, 0.45]);
                    overlay.bar(18.0, sh - 28.0, 200.0, 12.0, hpf, [0.2, 0.8, 0.25, 1.0]);
                }
                overlay.text_shadow(8.0, sh - 16.0, 1.0, &w.zone.name, [0.8, 0.85, 0.9, 1.0]);
                overlay.text(sw - 84.0, 10.0, 1.0, &format!("{fps:.0} fps"), [0.8, 1.0, 0.8, 1.0]);
                // Character sheet readout (level + gold) from P_FetchCharacter,
                // plus the multi-denomination Money$ string (HUD-3) on the line
                // below, formatted via Money.dat (Platinum/Gold/Silver/Copper).
                if let Some(sheet) = &self.sheet {
                    // LIVE gold (me_gold, seeded from the sheet at login + updated by
                    // P_GoldChange) so buy/sell/loot are reflected immediately.
                    let gold = self.net.as_ref().map(|n| n.world.me_gold).unwrap_or(sheet.gold as i32);
                    let line = format!("Lv {}   {}g", sheet.level, gold);
                    let tw = rcce_render::font::text_width(&line, 1.0);
                    overlay.text_shadow(sw - tw - 12.0, 24.0, 1.0, &line, [1.0, 0.88, 0.4, 1.0]);
                    let money = store.money().format(gold as i64);
                    let mw = rcce_render::font::text_width(&money, 1.0);
                    overlay.text_shadow(sw - mw - 12.0, 36.0, 1.0, &money, [0.95, 0.82, 0.55, 1.0]);
                }
                // Audio readout (M mute, [ / ] volume).
                if let Some(a) = self.audio.as_ref() {
                    let s = if a.is_muted() {
                        "Audio: muted".to_string()
                    } else {
                        format!("Vol {}%", (a.master_volume() * 100.0).round() as i32)
                    };
                    let tw = rcce_render::font::text_width(&s, 1.0);
                    let col = if a.is_muted() { [1.0, 0.6, 0.6, 1.0] } else { [0.7, 0.85, 1.0, 1.0] };
                    overlay.text_shadow(sw - tw - 12.0, 50.0, 1.0, &s, col);
                }

                // Chat log at the real Chat rect (bottom-left), newest at the
                // bottom of the box.
                let (cx0, cy0, cw, chh) = match store.interface() {
                    Some(iface) => iface.chat.px(sw, sh),
                    None => (14.0, sh - 160.0, 388.0, 152.0),
                };
                overlay.rect(cx0, cy0, cw, chh, [0.0, 0.0, 0.0, 0.28]);
                let max_lines = ((chh / 12.0) as usize).max(1);
                let bottom = cy0 + chh - 13.0;
                // Scrollback (CHAT-3): skip the `chat_scroll` newest lines so
                // older history scrolls in. Clamp so ≥1 line stays visible.
                let scroll = self.chat_scroll.min(w.chat.len().saturating_sub(1));
                self.chat_scroll = scroll;
                for (i, (text, col)) in visible_chat(&w.chat, scroll, max_lines).into_iter().enumerate() {
                    let y = bottom - i as f32 * 12.0;
                    let s: String = text.chars().take(60).collect();
                    overlay.text_shadow(cx0 + 4.0, y, 1.0, &s, *col);
                }
                if scroll > 0 {
                    overlay.text_shadow(cx0 + cw - 68.0, cy0 + 2.0, 1.0, &format!("scroll +{scroll}"), [0.7, 0.85, 1.0, 1.0]);
                }
            }
            // Chat input line just under the chat box (real Chat rect bottom).
            if let Some(buf) = self.chat_input.as_ref() {
                let (cx0, cy0, cw, chh) = match store.interface() {
                    Some(iface) => iface.chat.px(sw, sh),
                    None => (14.0, sh - 160.0, 388.0, 152.0),
                };
                overlay.rect(cx0, cy0 + chh, cw, 16.0, [0.0, 0.0, 0.0, 0.6]);
                let caret = if (elapsed * 2.0) as i64 % 2 == 0 { "_" } else { " " };
                overlay.text_shadow(cx0 + 4.0, cy0 + chh + 2.0, 1.0, &format!("> {buf}{caret}"), [1.0, 1.0, 1.0, 1.0]);
            }

            // Inventory / spellbook panel (toggle with I). Item names resolve
            // through Items.dat; spell names arrive over the wire.
            if self.show_inventory {
                // Match the real InventoryWindow rect (centred ~0.25,0.2,0.5,0.55)
                // and draw the 46-slot grid at the real window-relative button
                // positions (Interface.dat inv_buttons): rows 0-1 are the 14
                // equipment slots, rows 2-5 the 32 backpack slots.
                let dim = [0.6, 0.6, 0.6, 1.0];
                let iface = store.interface();
                let (px, py, pw, ph) = match iface {
                    Some(i) => {
                        let r = i.inventory_window.px(sw, sh);
                        (r.0.round(), r.1.round(), r.2.round(), r.3.round())
                    }
                    None => {
                        let (pw, ph) = (340.0f32, 384.0f32);
                        (((sw - pw) * 0.5).round(), ((sh - ph) * 0.5).round(), pw, ph)
                    }
                };
                // Skinned leather window background (Blitz InventoryBG), with the
                // flat panel as the fallback. A slim translucent title strip keeps
                // the header text readable over the texture.
                if overlay.has_texture("gui:InventoryBG") {
                    overlay.image(px, py, pw, ph, "gui:InventoryBG", [1.0, 1.0, 1.0, 1.0]);
                    overlay.rect(px, py, pw, 22.0, [0.0, 0.0, 0.0, 0.45]);
                } else {
                    overlay.rect(px, py, pw, ph, [0.05, 0.06, 0.10, 0.92]);
                    overlay.rect(px, py, pw, 22.0, [0.15, 0.18, 0.28, 0.96]);
                }
                overlay.text_shadow(px + 10.0, py + 6.0, 1.5, "Character", white);
                overlay.text(px + pw - 78.0, py + 7.0, 1.0, "[I] close", dim);

                // Attributes column to the left of the inventory window: the
                // named, non-hidden attribute slots (Attributes.dat) with live
                // values from the character sheet.
                if let Some(sheet) = &self.sheet {
                    // Character sheet (HUD-8): name title + level/XP/reputation
                    // header, then the named attributes with live value/max.
                    let cname = self
                        .chars
                        .get(self.char_sel)
                        .map(|c| c.name.as_str())
                        .filter(|n| !n.is_empty())
                        .unwrap_or(self.login_user.as_str())
                        .to_string();
                    let mut rows: Vec<(String, [f32; 4])> = Vec::new();
                    rows.push((format!("Level {}", sheet.level), [0.7, 1.0, 0.7, 1.0]));
                    rows.push((format!("XP {}", sheet.xp), [0.92, 0.86, 0.6, 1.0]));
                    rows.push((format!("Reputation {}", sheet.reputation), [0.85, 0.85, 1.0, 1.0]));
                    rows.push((String::new(), white)); // spacer before attributes
                    for i in 0..sheet.attributes.len().min(rcce_data::AttributeNames::COUNT) {
                        if let Some(name) = store.attribute_name(i) {
                            let (val, mx) = sheet.attributes[i];
                            let line = if i <= 1 && mx > 0 {
                                format!("{name}: {val}/{mx}")
                            } else {
                                format!("{name}: {val}")
                            };
                            let col = match i {
                                0 => [1.0, 0.55, 0.5, 1.0],
                                1 => [0.55, 0.7, 1.0, 1.0],
                                _ => white,
                            };
                            rows.push((line, col));
                        }
                    }
                    let aw = 152.0f32;
                    let ax = (px - aw - 6.0).max(4.0);
                    let boxh = 24.0 + rows.len() as f32 * 13.0 + 6.0;
                    if overlay.has_texture("gui:CharBG") {
                        overlay.image(ax, py, aw, boxh, "gui:CharBG", [1.0, 1.0, 1.0, 1.0]);
                        overlay.rect(ax, py, aw, 20.0, [0.0, 0.0, 0.0, 0.45]);
                    } else {
                        overlay.rect(ax, py, aw, boxh, [0.05, 0.06, 0.10, 0.92]);
                        overlay.rect(ax, py, aw, 20.0, [0.15, 0.18, 0.28, 0.96]);
                    }
                    overlay.text_shadow(ax + 8.0, py + 5.0, 1.0, &cname, white);
                    let mut ay = py + 24.0;
                    for (line, col) in &rows {
                        overlay.text(ax + 8.0, ay, 1.0, line, *col);
                        ay += 13.0;
                    }
                }

                // Spells column to the right of the inventory window: all known
                // spells (icon + name + level), memorised ones highlighted.
                if let Some(sheet) = &self.sheet {
                    if !sheet.spells.is_empty() {
                        let cw2 = 174.0f32;
                        let sx = (px + pw + 6.0).min((sw - cw2 - 4.0).max(0.0));
                        let rowh = 16.0f32;
                        let cap = (((ph - 24.0) / rowh) as usize).max(1);
                        let total = sheet.spells.len();
                        let shown = if total > cap { cap - 1 } else { total };
                        let rows_drawn = shown + if total > cap { 1 } else { 0 };
                        let boxh = 24.0 + rows_drawn as f32 * rowh + 4.0;
                        if overlay.has_texture("gui:AbilitiesBG") {
                            overlay.image(sx, py, cw2, boxh, "gui:AbilitiesBG", [1.0, 1.0, 1.0, 1.0]);
                            overlay.rect(sx, py, cw2, 20.0, [0.0, 0.0, 0.0, 0.45]);
                        } else {
                            overlay.rect(sx, py, cw2, boxh, [0.05, 0.06, 0.10, 0.92]);
                            overlay.rect(sx, py, cw2, 20.0, [0.15, 0.18, 0.28, 0.96]);
                        }
                        overlay.text_shadow(sx + 8.0, py + 5.0, 1.0, &format!("Spells ({total})"), white);
                        let mut sy = py + 24.0;
                        for sp in sheet.spells.iter().take(shown) {
                            let key = format!("spell:{}", sp.id);
                            if !overlay.has_texture(&key) {
                                if let Some(img) =
                                    store.texture_path(sp.thumb_tex).and_then(|p| rcce_data::texture::load(&p))
                                {
                                    overlay.register_texture(&gfx.device, &gfx.queue, &key, img.width, img.height, &img.rgba);
                                }
                            }
                            if overlay.has_texture(&key) {
                                overlay.image(sx + 4.0, sy, 13.0, 13.0, &key, [1.0, 1.0, 1.0, 1.0]);
                            } else {
                                overlay.rect(sx + 4.0, sy, 13.0, 13.0, [0.1, 0.1, 0.16, 0.9]);
                            }
                            let mem = self.memorised.contains(&sp.id);
                            let col = if mem { [1.0, 0.9, 0.5, 1.0] } else { [0.85, 0.85, 0.9, 1.0] };
                            let star = if mem { "*" } else { "" };
                            let nm: String = format!("{}{} (L{})", star, sp.name, sp.level).chars().take(24).collect();
                            overlay.text(sx + 20.0, sy + 3.0, 1.0, &nm, col);
                            sy += rowh;
                        }
                        if total > cap {
                            overlay.text(sx + 8.0, sy + 3.0, 1.0, &format!("+{} more", total - shown), dim);
                        }
                    }
                }

                // Slot index -> item, from the live inventory.
                let me_inv = self.net.as_ref().map(|n| &n.world.me_inventory);
                let mut by_slot: std::collections::HashMap<u8, (u16, u16)> = std::collections::HashMap::new();
                if let Some(m) = me_inv {
                    for it in m.values() {
                        by_slot.insert(it.slot, (it.item_id, it.amount));
                    }
                }

                if let Some(iface) = iface {
                    let iw = &iface.inventory_window;
                    // Header line: level / gold (live) / xp.
                    if let Some(s) = &self.sheet {
                        let gold = self.net.as_ref().map(|n| n.world.me_gold).unwrap_or(s.gold as i32);
                        overlay.text_shadow(px + 10.0, py + 26.0, 1.0, &format!("Lv {}   {} gold   {} xp", s.level, gold, s.xp), [1.0, 0.88, 0.4, 1.0]);
                    }
                    for (i, b) in iface.inventory_buttons.iter().enumerate() {
                        // Window-relative fraction -> screen pixels.
                        let bx = (iw.x + b.x * iw.w) * sw;
                        let bgy = (iw.y + b.y * iw.h) * sh;
                        let bw = (b.w * iw.w * sw).max(8.0);
                        let bh = (b.h * iw.h * sh).max(8.0);
                        let equip = i < 14;
                        let occupied = by_slot.contains_key(&(i as u8));
                        // Real EmptySlot.bmp frame, with a translucent state tint
                        // layered on top (interleaved draw list); the rect is the
                        // opaque fallback when the texture is missing.
                        if overlay.has_texture("gui:EmptySlot") {
                            overlay.image(bx, bgy, bw, bh, "gui:EmptySlot", [1.0, 1.0, 1.0, 1.0]);
                            let tint = match (equip, occupied) {
                                (true, true) => [0.30, 0.45, 0.25, 0.35],
                                (false, true) => [0.30, 0.35, 0.55, 0.35],
                                _ => [0.0, 0.0, 0.0, 0.0],
                            };
                            if tint[3] > 0.0 {
                                overlay.rect(bx, bgy, bw, bh, tint);
                            }
                        } else {
                            let bg = match (equip, occupied) {
                                (true, true) => [0.20, 0.26, 0.18, 0.95],
                                (true, false) => [0.12, 0.14, 0.12, 0.85],
                                (false, true) => [0.16, 0.18, 0.26, 0.95],
                                (false, false) => [0.09, 0.10, 0.14, 0.82],
                            };
                            overlay.rect(bx, bgy, bw, bh, bg);
                        }
                        // Empty equipment slots show their Blitz placeholder ICON
                        // (Hat/Chest/Ring/…), dimmed, so you can see what goes where —
                        // like Blitz. Slot 0 ("Weapon") has no icon → name fallback.
                        if equip && !occupied {
                            let name = rcce_data::equip_slot_name(i as u8);
                            let key = name.map(|n| format!("gui:slot:{n}"));
                            if let Some(k) = key.as_ref().filter(|k| overlay.has_texture(k)) {
                                let pad = (bw * 0.14).min(3.0);
                                overlay.image(bx + pad, bgy + pad, bw - pad * 2.0, bh - pad * 2.0, k, [1.0, 1.0, 1.0, 0.55]);
                            } else if let Some(name) = name {
                                let abbr: String = name.chars().take(((bw / 6.0) as usize).max(2)).collect();
                                overlay.text(bx + 2.0, bgy + bh * 0.5 - 4.0, 1.0, &abbr, [0.45, 0.45, 0.5, 1.0]);
                            }
                        }
                        if let Some(&(item_id, amount)) = by_slot.get(&(i as u8)) {
                            // Draw the real item thumbnail (lazily registered from
                            // the item's ThumbnailTexID on first sight) over the
                            // slot frame; fall back to the name abbreviation.
                            let key = format!("item:{item_id}");
                            if !overlay.has_texture(&key) {
                                if let Some(img) =
                                    store.item_icon_path(item_id).and_then(|p| rcce_data::texture::load(&p))
                                {
                                    overlay.register_texture(&gfx.device, &gfx.queue, &key, img.width, img.height, &img.rgba);
                                }
                            }
                            if overlay.has_texture(&key) {
                                let pad = (bw * 0.1).min(3.0);
                                overlay.image(bx + pad, bgy + pad, bw - pad * 2.0, bh - pad * 2.0, &key, [1.0, 1.0, 1.0, 1.0]);
                            } else {
                                let name = store.item_name(item_id);
                                let maxc = ((bw / 6.0) as usize).max(2);
                                let abbr: String = name.chars().take(maxc).collect();
                                overlay.text_shadow(bx + 2.0, bgy + 2.0, 1.0, &abbr, white);
                            }
                            if amount > 1 {
                                overlay.text(bx + 2.0, bgy + bh - 9.0, 1.0, &format!("x{amount}"), [0.8, 1.0, 0.8, 1.0]);
                            }
                        }
                        // Backpack 1-9 keybind hint in the slot corner.
                        if !equip {
                            let bp_idx = i - 14;
                            if bp_idx < 9 {
                                overlay.text(bx + bw - 7.0, bgy + 1.0, 1.0, &format!("{}", bp_idx + 1), [1.0, 1.0, 0.5, 0.9]);
                            }
                        }
                    }
                    // Real inv_gold display + Drop / Eat buttons at their
                    // window-relative Interface.dat positions.
                    let iw = iface.inventory_window;
                    let to_scr = |c: rcce_data::IComp| -> (f32, f32, f32, f32) {
                        ((iw.x + c.x * iw.w) * sw, (iw.y + c.y * iw.h) * sh, c.w * iw.w * sw, c.h * iw.h * sh)
                    };
                    let gold = self.net.as_ref().map(|n| n.world.me_gold).unwrap_or_else(|| self.sheet.as_ref().map(|s| s.gold as i32).unwrap_or(0));
                    let (gx, gy, _, _) = to_scr(iface.inventory_gold);
                    overlay.text_shadow(gx, gy, 1.0, &format!("Gold: {gold}"), [1.0, 0.88, 0.4, 1.0]);
                    for (comp, label) in [(iface.inventory_drop, "Drop"), (iface.inventory_eat, "Eat")] {
                        let (dx, dy, dw, dh) = to_scr(comp);
                        if dw > 1.0 && dh > 1.0 {
                            overlay.rect(dx, dy, dw, dh, [0.20, 0.16, 0.12, 0.9]);
                            let tw = rcce_render::font::text_width(label, 1.0);
                            overlay.text_shadow(dx + (dw - tw) * 0.5, dy + dh * 0.5 - 4.0, 1.0, label, white);
                        }
                    }
                    overlay.text(px + 10.0, py + ph - 13.0, 1.0, "1-9 drop  ·  Shift+1-9 equip", dim);
                } else if let Some(s) = &self.sheet {
                    // Fallback text list when Interface.dat is absent.
                    let gold = self.net.as_ref().map(|n| n.world.me_gold).unwrap_or(s.gold as i32);
                    overlay.text_shadow(px + 10.0, py + 30.0, 1.0, &format!("Lv {}   {} gold", s.level, gold), [1.0, 0.88, 0.4, 1.0]);
                } else {
                    overlay.text(px + 10.0, py + 30.0, 1.0, "(no character data)", dim);
                }
            }

            // Vendor / trade window (P_OpenTrading) — lists what the NPC offers,
            // with item icons, names from Items.dat and prices from each item's
            // value. Drag an inventory item onto this window to sell it.
            if let Some(trade) = self.net.as_ref().and_then(|n| n.world.current_trade.as_ref()) {
                use rcce_client::trade::TradeKind;
                let dimc = [0.6, 0.6, 0.6, 1.0];
                let gold = [1.0, 0.88, 0.4, 1.0];
                let (px, py, pw, ph) = vendor_window_rect(sw, sh);
                let sellable = matches!(trade.kind, TradeKind::Npc | TradeKind::Scenery);
                // Leather skin to match the other windows (ItemShop.png is a wide
                // two-column layout that wouldn't fit this tall list, so reuse the
                // generic InventoryBG); flat rect fallback.
                if overlay.has_texture("gui:InventoryBG") {
                    overlay.image(px, py, pw, ph, "gui:InventoryBG", [1.0, 1.0, 1.0, 1.0]);
                    overlay.rect(px, py, pw, 22.0, [0.0, 0.0, 0.0, 0.45]);
                } else {
                    overlay.rect(px, py, pw, ph, [0.07, 0.06, 0.05, 0.92]);
                    overlay.rect(px, py, pw, 22.0, [0.28, 0.22, 0.12, 0.96]);
                }
                // Highlight the window as a drop target while dragging an inventory
                // item over it (drag-to-sell).
                let selling_here = sellable
                    && matches!(self.drag, Some(SpellDrag { moved: true, src: DragSrc::Inventory(_), .. }))
                    && point_in_vendor(self.cursor.0, self.cursor.1, sw, sh);
                if selling_here {
                    overlay.rect(px, py, pw, ph, [0.4, 0.7, 1.0, 0.18]);
                }
                let title = match trade.kind {
                    TradeKind::Npc => "Vendor",
                    TradeKind::Scenery => "Container",
                    TradeKind::Player => "Trade",
                };
                overlay.text_shadow(px + 10.0, py + 6.0, 1.5, title, white);
                overlay.text(px + pw - 80.0, py + 7.0, 1.0, "[Esc] close", dimc);
                let mut y = py + 30.0;
                let row_h = 20.0f32;
                // Reserve the bottom band for the staged-sells line, the net-gold
                // line, and the Confirm button.
                let foot = py + ph - 58.0;
                // Net gold delta = sell value (staged sells) − buy cost (staged buys).
                let mut net_gold: i64 = 0;
                if trade.offers.is_empty() {
                    overlay.text(px + 10.0, y, 1.0, "(nothing for sale)", dimc);
                } else {
                    overlay.text(px + 10.0, y, 1.0, "1-9 to add/remove:", dimc);
                    y += 16.0;
                    for (i, off) in trade.offers.iter().enumerate() {
                        if y + row_h > foot { break; }
                        let staged = self.pending_buys.contains(&i);
                        if staged {
                            overlay.rect(px + 8.0, y - 1.0, pw - 16.0, row_h, [0.25, 0.5, 0.25, 0.5]);
                            net_gold -= store.item_value(off.item_id).max(0) as i64 * off.amount.max(1) as i64;
                        }
                        // Item icon (lazily registered from the item's thumbnail).
                        let key = format!("item:{}", off.item_id);
                        if !overlay.has_texture(&key) {
                            if let Some(img) = store.item_icon_path(off.item_id).and_then(|p| rcce_data::texture::load(&p)) {
                                overlay.register_texture(&gfx.device, &gfx.queue, &key, img.width, img.height, &img.rgba);
                            }
                        }
                        overlay.rect(px + 10.0, y - 1.0, row_h - 2.0, row_h - 2.0, [0.0, 0.0, 0.0, 0.35]);
                        if overlay.has_texture(&key) {
                            overlay.image(px + 11.0, y, row_h - 4.0, row_h - 4.0, &key, [1.0, 1.0, 1.0, 1.0]);
                        }
                        let name = store.item_name(off.item_id);
                        let qty = if off.amount > 1 { format!(" x{}", off.amount) } else { String::new() };
                        let num = if i < 9 { format!("{}. ", i + 1) } else { String::new() };
                        let check = if staged { "+ " } else { "" };
                        let line: String = format!("{check}{num}{name}{qty}").chars().take(24).collect();
                        let col = if staged { [0.7, 1.0, 0.7, 1.0] } else { white };
                        overlay.text(px + 10.0 + row_h, y + 3.0, 1.0, &line, col);
                        let price = format!("{}g", store.item_value(off.item_id).max(0));
                        let pw2 = rcce_render::font::text_width(&price, 1.0);
                        overlay.text(px + pw - pw2 - 12.0, y + 3.0, 1.0, &price, gold);
                        y += row_h;
                    }
                }
                // Staged-sells summary line + their value (only for sellable trades).
                let sell_y = py + ph - 56.0;
                if sellable {
                    let inv = self.net.as_ref().map(|n| &n.world.me_inventory);
                    let names: Vec<String> = self
                        .pending_sells
                        .iter()
                        .filter_map(|&(slot, qty)| inv.and_then(|m| m.values().find(|it| it.slot == slot)).map(|it| (it, qty)))
                        .map(|(it, qty)| {
                            let q = qty.clamp(1, it.amount.max(1));
                            net_gold += store.item_value(it.item_id).max(0) as i64 * q as i64;
                            let name = store.item_name(it.item_id);
                            if q > 1 { format!("{name} x{q}") } else { name }
                        })
                        .collect();
                    let label = if names.is_empty() {
                        if selling_here { "Release to sell".to_string() } else { "Drag items here to sell".to_string() }
                    } else {
                        format!("Sell: {}", names.join(", "))
                    };
                    let lc = if names.is_empty() && !selling_here { [0.65, 0.6, 0.45, 1.0] } else { [0.6, 1.0, 0.7, 1.0] };
                    let label: String = label.chars().take(34).collect();
                    overlay.text(px + 12.0, sell_y, 1.0, &label, lc);
                }
                // Net gold delta.
                let (ng_txt, ng_col) = if net_gold >= 0 {
                    (format!("Net: +{net_gold}g"), [0.6, 1.0, 0.7, 1.0])
                } else {
                    (format!("Net: {net_gold}g"), [1.0, 0.6, 0.5, 1.0])
                };
                overlay.text(px + 12.0, py + ph - 42.0, 1.0, &ng_txt, ng_col);
                // Confirm button — sends the whole basket. Bright when staged.
                let (bx, by_, bw_, bh_) = vendor_confirm_button_rect(sw, sh);
                let staged_n = self.pending_buys.len() + self.pending_sells.len();
                let active = staged_n > 0;
                let hovering = point_in_confirm(self.cursor.0, self.cursor.1, sw, sh);
                let bgc = if active && hovering {
                    [0.3, 0.6, 0.3, 0.95]
                } else if active {
                    [0.2, 0.45, 0.2, 0.9]
                } else {
                    [0.18, 0.18, 0.2, 0.8]
                };
                overlay.rect(bx, by_, bw_, bh_, bgc);
                let btxt = if active { format!("Confirm ({staged_n})") } else { "Confirm".to_string() };
                let btw = rcce_render::font::text_width(&btxt, 1.0);
                overlay.text_shadow(bx + bw_ * 0.5 - btw * 0.5, by_ + 3.0, 1.0, &btxt, if active { white } else { dimc });
            }

            // Bottom action bar + function buttons, placed at the real
            // Client.exe fractional coordinates (Interface3D.bb:3511-3534 /
            // CreateActionBarButton). The row sits on the Y=0.9415 baseline.
            // 4:3 layout: 12 spell slots left-anchored at 0.089867187 + i*pitch,
            // function buttons right-anchored at fixed X positions.
            {
                // Bound to the shared module consts so the draw geometry and the
                // hover/click hit-tests (spell_slot_at) can't drift apart.
                const BAR_Y: f32 = FBTN_Y;
                const SLOT_W: f32 = FBTN_W;
                const SLOT_H: f32 = FBTN_H;
                const SLOT_PITCH: f32 = SPELLBAR_PITCH;
                const SLOT_X0: f32 = SPELLBAR_X0;
                let (sw_, sh_, bw, bh) = (SLOT_W * sw, SLOT_H * sh, SLOT_W * sw, SLOT_H * sh);
                let by = BAR_Y * sh;
                // 12 spell slots, resolved from the explicit action-bar layout
                // (drag-drop) or the memorised auto-fill default — one source of
                // truth shared with the Digit-key / click activate path (`use_slot`).
                let bar_ids = effective_action_bar(&self.action_bar, self.sheet.as_ref(), &self.memorised);
                // The slot under the cursor being dragged-over (drop highlight),
                // and the source slot of an in-flight bar drag (dim it as it lifts).
                let drag_target = self.drag.filter(|d| d.moved).and_then(|_| spell_slot_at(self.cursor.0, self.cursor.1, sw, sh));
                let drag_from = match self.drag {
                    Some(SpellDrag { moved: true, src: DragSrc::Slot(s), .. }) => Some(s),
                    _ => None,
                };
                for i in 0..12usize {
                    let x = (SLOT_X0 + i as f32 * SLOT_PITCH) * sw;
                    // Real EmptySlot.bmp frame under each slot (interleaved draw
                    // list lets the shading + number layer on top); coloured-rect
                    // fallback when the texture is missing.
                    if overlay.has_texture("gui:EmptySlot") {
                        overlay.image(x, by, sw_, sh_, "gui:EmptySlot", [1.0, 1.0, 1.0, 1.0]);
                    } else {
                        overlay.rect(x, by, sw_, sh_, [0.08, 0.08, 0.13, 0.78]);
                    }
                    // Drop-target highlight while dragging an entry over this slot.
                    if drag_target == Some(i) {
                        overlay.rect(x, by, sw_, sh_, [0.4, 0.7, 1.0, 0.28]);
                    }
                    if let Some(entry) = bar_ids[i] {
                        // Source slot of a bar→bar drag: dim it so the entry looks
                        // "picked up" while it follows the cursor.
                        let icon_a = if drag_from == Some(i) { 0.35 } else { 1.0 };
                        // Resolve (icon key, display name, spell recharge) per kind;
                        // register the icon lazily from the spell/item catalog.
                        let (key, name, recharge): (String, String, u16) = match entry {
                            HotbarEntry::Spell(spell_id) => {
                                let sp = self.sheet.as_ref().and_then(|s| s.spells.iter().find(|x| x.id == spell_id));
                                let name = sp.map(|s| s.name.clone()).or_else(|| {
                                    self.net.as_ref().and_then(|n| n.world.known_spells.iter().find(|s| s.id == spell_id)).map(|s| s.name.clone())
                                }).unwrap_or_default();
                                let key = format!("spell:{spell_id}");
                                if !overlay.has_texture(&key) {
                                    if let Some(img) = sp.and_then(|s| store.texture_path(s.thumb_tex)).and_then(|p| rcce_data::texture::load(&p)) {
                                        overlay.register_texture(&gfx.device, &gfx.queue, &key, img.width, img.height, &img.rgba);
                                    }
                                }
                                (key, name, sp.map(|s| s.recharge).unwrap_or(0))
                            }
                            HotbarEntry::Item(item_id) => {
                                let key = format!("item:{item_id}");
                                if !overlay.has_texture(&key) {
                                    if let Some(img) = store.item_icon_path(item_id).and_then(|p| rcce_data::texture::load(&p)) {
                                        overlay.register_texture(&gfx.device, &gfx.queue, &key, img.width, img.height, &img.rgba);
                                    }
                                }
                                (key, store.item_name(item_id), 0)
                            }
                        };
                        let has_icon = overlay.has_texture(&key);
                        if has_icon {
                            let pad = (sw_ * 0.08).min(2.0);
                            overlay.image(x + pad, by + pad, sw_ - pad * 2.0, sh_ - pad * 2.0, &key, [1.0, 1.0, 1.0, icon_a]);
                        }
                        // Cooldown shade (spells only — items have no recharge).
                        if let HotbarEntry::Spell(spell_id) = entry {
                            let ready = self.spell_cooldowns.get(&spell_id).copied().unwrap_or(0.0);
                            let remaining = (ready - elapsed).max(0.0);
                            if remaining > 0.0 {
                                let span = (recharge as f32 / 1000.0).max(0.1);
                                let frac = (remaining / span).clamp(0.0, 1.0);
                                overlay.rect(x, by, sw_, sh_ * frac, [0.0, 0.0, 0.0, 0.6]);
                            }
                        }
                        if i < 9 {
                            overlay.text_shadow(x + 2.0, by + 1.0, 1.0, &format!("{}", i + 1), [1.0, 1.0, 0.6, 1.0]);
                        }
                        if !has_icon {
                            let abbr: String = name.chars().take(4).collect();
                            overlay.text(x + 2.0, by + sh_ - 9.0, 1.0, &abbr, [white[0], white[1], white[2], icon_a]);
                        }
                    }
                }
                // Function buttons (right cluster), drawn with the real GUI .bmp
                // icons when registered; text labels are the fallback. The active
                // panel (inventory) is highlighted. Positions come from the shared
                // FUNCTION_BUTTONS table so hit-testing matches exactly.
                for (action, fx, key, label) in FUNCTION_BUTTONS {
                    let x = fx * sw;
                    let active = action == HudAction::Inventory && self.show_inventory;
                    if overlay.has_texture(key) {
                        let tint = if active { [1.0, 1.0, 0.6, 1.0] } else { [1.0, 1.0, 1.0, 1.0] };
                        overlay.image(x, by, bw, bh, key, tint);
                    } else {
                        let bg = if active { [0.32, 0.30, 0.12, 0.9] } else { [0.12, 0.12, 0.18, 0.82] };
                        overlay.rect(x, by, bw, bh, bg);
                        overlay.text_shadow(x + 3.0, by + bh * 0.5 - 4.0, 1.0, label, [0.85, 0.85, 0.7, 1.0]);
                    }
                }

                // XP bar along the very bottom. The server sends a 0..255 fill
                // (P_XPUpdate "B"); Client.exe clips the Action Bar XP texture to
                // that fraction (UpdateXPBar: ScaleEntity + VertexTexCoords), which
                // maps to drawing image_uv over a dark backing.
                let fill = self.net.as_ref().map(|n| n.world.me_xp_bar as f32 / 255.0).unwrap_or(0.0);
                let xb_x = SLOT_X0 * sw;
                let xb_w = (0.92 - SLOT_X0) * sw;
                let xb_h = (sh * 0.012).max(5.0);
                let xb_y = sh - xb_h - 2.0;
                overlay.rect(xb_x, xb_y, xb_w, xb_h, [0.0, 0.0, 0.0, 0.7]);
                if fill > 0.0 {
                    if overlay.has_texture("gui:XP") {
                        overlay.image_uv(xb_x, xb_y, xb_w * fill, xb_h, "gui:XP", [0.0, 0.0, fill, 1.0], [1.0, 1.0, 1.0, 1.0]);
                    } else {
                        overlay.rect(xb_x, xb_y, xb_w * fill, xb_h, [0.7, 0.55, 0.15, 0.95]);
                    }
                }
            }

            // Hover tooltip (topmost): an inventory slot while the panel is open,
            // else a spell-bar slot. Shows the item/spell name + stats near the
            // cursor, clamped to stay on screen.
            {
                let (cx, cy) = self.cursor;
                let white = [1.0, 1.0, 1.0, 1.0];
                let gold = [1.0, 0.88, 0.4, 1.0];
                let accent = [0.8, 0.85, 1.0, 1.0];
                let mut lines: Vec<(String, [f32; 4])> = Vec::new();
                if self.show_inventory {
                    if let Some(iface) = store.interface() {
                        if let Some(slot) =
                            inventory_slot_at(cx, cy, iface.inventory_window, &iface.inventory_buttons, sw, sh)
                        {
                            if let Some(it) = self
                                .net
                                .as_ref()
                                .and_then(|n| n.world.me_inventory.values().find(|it| it.slot == slot as u8))
                            {
                                // Remember this slot so the Drop / Eat buttons can
                                // act on it after the cursor moves onto them.
                                self.last_inv_slot = Some(slot as u8);
                                lines.push((store.item_name(it.item_id), white));
                                if let Some(def) = store.item_def(it.item_id) {
                                    if let Some(sname) = rcce_data::equip_slot_name(slot as u8) {
                                        lines.push((sname.to_string(), [0.7, 1.0, 0.8, 1.0]));
                                    }
                                    if def.weapon_damage > 0 {
                                        lines.push((format!("Damage: {}", def.weapon_damage), [1.0, 0.7, 0.6, 1.0]));
                                    }
                                    if def.armour_level > 0 {
                                        lines.push((format!("Armour: {}", def.armour_level), [0.7, 0.85, 1.0, 1.0]));
                                    }
                                    if def.mass > 0 {
                                        lines.push((format!("Mass: {}", def.mass), [0.7, 0.7, 0.7, 1.0]));
                                    }
                                }
                                lines.push((format!("Value: {}g", store.item_value(it.item_id)), gold));
                                if it.amount > 1 {
                                    lines.push((format!("Quantity: {}", it.amount), accent));
                                }
                            }
                        }
                    }
                }
                if lines.is_empty() {
                    if let Some(slot) = spell_slot_at(cx, cy, sw, sh) {
                        match effective_action_bar(&self.action_bar, self.sheet.as_ref(), &self.memorised).get(slot).copied().flatten() {
                            Some(HotbarEntry::Spell(spell_id)) => {
                                if let Some(sp) = self.sheet.as_ref().and_then(|s| s.spells.iter().find(|x| x.id == spell_id)) {
                                    lines.push((sp.name.clone(), white));
                                    lines.push((format!("Level {} · Recharge {:.1}s", sp.level, sp.recharge as f32 / 1000.0), accent));
                                    for chunk in wrap_text(&sp.description, 44).into_iter().take(6) {
                                        lines.push((chunk, [0.78, 0.78, 0.78, 1.0]));
                                    }
                                } else if let Some(ks) = self.net.as_ref().and_then(|n| n.world.known_spells.iter().find(|s| s.id == spell_id)) {
                                    lines.push((ks.name.clone(), white));
                                    lines.push((format!("Rank {}", ks.level), accent));
                                }
                            }
                            Some(HotbarEntry::Item(item_id)) => {
                                lines.push((store.item_name(item_id), white));
                                if let Some(def) = store.item_def(item_id) {
                                    if def.weapon_damage > 0 {
                                        lines.push((format!("Damage: {}", def.weapon_damage), [1.0, 0.7, 0.6, 1.0]));
                                    }
                                    if def.armour_level > 0 {
                                        lines.push((format!("Armour: {}", def.armour_level), accent));
                                    }
                                }
                                // Whether the player still carries one (greyed if not).
                                let have = self.net.as_ref().map(|n| n.world.me_inventory.values().any(|it| it.item_id == item_id)).unwrap_or(false);
                                lines.push((if have { "Click / number to use".into() } else { "(none in inventory)".to_string() }, if have { [0.7, 1.0, 0.7, 1.0] } else { [0.7, 0.5, 0.5, 1.0] }));
                            }
                            None => {}
                        }
                    }
                }
                // Hovered-actor world tooltip — only with the panel closed and
                // clear of the function-button row, so it doesn't fight the HUD.
                if lines.is_empty()
                    && !self.show_inventory
                    && function_button_at(cx, cy, sw, sh).is_none()
                {
                    if let Some(net) = self.net.as_ref() {
                        let actors: Vec<(u16, [f32; 3])> = net
                            .world
                            .actors
                            .values()
                            .filter(|a| a.alive)
                            .map(|a| (a.runtime_id, [a.x, a.y + 3.0, a.z]))
                            .collect();
                        if let Some(rid) = actor_at(cx, cy, &actors, &self.vp, sw, sh, 32.0) {
                            if let Some(a) = net.world.actors.get(&rid) {
                                let nm = if a.name.is_empty() { format!("Actor {rid}") } else { a.name.clone() };
                                let col = if a.is_player { [0.5, 0.8, 1.0, 1.0] } else { [1.0, 0.7, 0.6, 1.0] };
                                lines.push((nm, col));
                                if a.health_max > 0 {
                                    lines.push((format!("HP {} / {}", a.health.max(0), a.health_max), accent));
                                }
                            }
                        }
                    }
                }
                if !lines.is_empty() {
                    let (pad, lh) = (5.0f32, 12.0f32);
                    let tw = lines
                        .iter()
                        .map(|(s, _)| rcce_render::font::text_width(s, 1.0))
                        .fold(0.0f32, f32::max);
                    let boxw = tw + pad * 2.0;
                    let boxh = lines.len() as f32 * lh + pad * 2.0;
                    let mut tx = cx + 14.0;
                    let mut ty = cy + 14.0;
                    if tx + boxw > sw {
                        tx = (cx - boxw - 6.0).max(0.0);
                    }
                    if ty + boxh > sh {
                        ty = (sh - boxh).max(0.0);
                    }
                    overlay.rect(tx, ty, boxw, boxh, [0.04, 0.05, 0.09, 0.95]);
                    overlay.rect(tx, ty, boxw, 1.5, [0.4, 0.45, 0.6, 0.9]);
                    for (i, (s, c)) in lines.iter().enumerate() {
                        overlay.text_shadow(tx + pad, ty + pad + i as f32 * lh, 1.0, s, *c);
                    }
                }
            }

            // Actions context menu (TGT-3): drawn last so it sits over the HUD.
            // Hover-highlight the row under the cursor; clicks are handled in
            // hud_click (which has priority over selection/move).
            if let Some(menu) = &self.context_menu {
                let (mcx, mcy) = self.cursor;
                let mh = CTX_ROW * menu.items.len() as f32;
                overlay.rect(menu.x - 1.0, menu.y - 1.0, CTX_W + 2.0, mh + 2.0, [1.0, 0.85, 0.2, 0.95]);
                overlay.rect(menu.x, menu.y, CTX_W, mh, [0.05, 0.05, 0.09, 0.96]);
                for (i, (label, _)) in menu.items.iter().enumerate() {
                    let ry = menu.y + i as f32 * CTX_ROW;
                    let hovered = mcx >= menu.x && mcx <= menu.x + CTX_W && mcy >= ry && mcy < ry + CTX_ROW;
                    if hovered {
                        overlay.rect(menu.x, ry, CTX_W, CTX_ROW, [0.2, 0.32, 0.55, 0.85]);
                    }
                    overlay.text_shadow(menu.x + 8.0, ry + 6.0, 1.0, label, [0.94, 0.94, 0.72, 1.0]);
                }
            }

            // Item context menu (Use/Equip/Drop): same style as the actor menu.
            if let Some(menu) = &self.item_menu {
                let (mcx, mcy) = self.cursor;
                let mh = CTX_ROW * menu.items.len() as f32;
                overlay.rect(menu.x - 1.0, menu.y - 1.0, CTX_W + 2.0, mh + 2.0, [1.0, 0.85, 0.2, 0.95]);
                overlay.rect(menu.x, menu.y, CTX_W, mh, [0.05, 0.05, 0.09, 0.96]);
                for (i, (label, _)) in menu.items.iter().enumerate() {
                    let ry = menu.y + i as f32 * CTX_ROW;
                    let hovered = mcx >= menu.x && mcx <= menu.x + CTX_W && mcy >= ry && mcy < ry + CTX_ROW;
                    if hovered {
                        overlay.rect(menu.x, ry, CTX_W, CTX_ROW, [0.2, 0.32, 0.55, 0.85]);
                    }
                    overlay.text_shadow(menu.x + 8.0, ry + 6.0, 1.0, label, [0.94, 0.94, 0.72, 1.0]);
                }
            }

            // Quantity prompt modal (partial-stack sell/drop): centred, keyboard-
            // driven. Drawn over the HUD so it reads as a focused dialog.
            if let Some(p) = &self.qty_prompt {
                let (bw, bh) = (280.0f32, 92.0f32);
                let bx = ((sw - bw) * 0.5).round();
                let by = ((sh - bh) * 0.5).round();
                overlay.rect(bx - 2.0, by - 2.0, bw + 4.0, bh + 4.0, [1.0, 0.85, 0.2, 0.95]);
                overlay.rect(bx, by, bw, bh, [0.05, 0.05, 0.09, 0.98]);
                let verb = match p.action {
                    QtyAction::Sell => "Sell",
                    QtyAction::Drop => "Drop",
                };
                let name = store.item_name(p.item_id);
                overlay.text_shadow(bx + 12.0, by + 8.0, 1.1, &format!("{verb} {name}"), white);
                let q = format!("{} / {}", p.qty, p.max);
                let qw2 = rcce_render::font::text_width(&q, 1.6);
                overlay.text_shadow(bx + bw * 0.5 - qw2 * 0.5, by + 28.0, 1.6, &q, [1.0, 0.95, 0.6, 1.0]);
                let frac = p.qty as f32 / p.max.max(1) as f32;
                overlay.rect(bx + 12.0, by + 52.0, bw - 24.0, 8.0, [0.0, 0.0, 0.0, 0.6]);
                overlay.bar(bx + 12.0, by + 52.0, bw - 24.0, 8.0, frac, [0.4, 0.8, 1.0, 1.0]);
                overlay.text(bx + 12.0, by + bh - 15.0, 1.0, "←/→ ±1 · PgUp/Dn ±10 · Enter OK · Esc cancel", [0.7, 0.7, 0.78, 1.0]);
            }

            // Dragged spell/item icon following the cursor (drag-drop hotbar
            // assign): drawn over the whole HUD so it reads as "carried". Only once
            // the press has become a real drag (moved past the dead-zone).
            if let Some(drag) = self.drag.filter(|d| d.moved) {
                let (cx, cy) = self.cursor;
                let isz = FBTN_W * sw * 0.9;
                let (key, name) = match drag.entry {
                    HotbarEntry::Spell(id) => (
                        format!("spell:{id}"),
                        self.sheet
                            .as_ref()
                            .and_then(|s| s.spells.iter().find(|x| x.id == id).map(|x| x.name.clone()))
                            .or_else(|| self.net.as_ref().and_then(|n| n.world.known_spells.iter().find(|s| s.id == id).map(|s| s.name.clone())))
                            .unwrap_or_default(),
                    ),
                    HotbarEntry::Item(id) => (format!("item:{id}"), store.item_name(id)),
                };
                if overlay.has_texture(&key) {
                    overlay.image(cx - isz * 0.5, cy - isz * 0.5, isz, isz, &key, [1.0, 1.0, 1.0, 0.85]);
                } else {
                    // No icon registered: a labelled chip so the drag is still legible.
                    let cw = rcce_render::font::text_width(&name, 1.0) + 8.0;
                    overlay.rect(cx - cw * 0.5, cy - 8.0, cw, 16.0, [0.1, 0.14, 0.22, 0.9]);
                    overlay.text_shadow(cx - cw * 0.5 + 4.0, cy - 6.0, 1.0, &name, [0.9, 0.95, 1.0, 1.0]);
                }
            }

            // Screen flash (ENV-6): a full-screen colour fading out over its
            // length, drawn on top of the whole HUD.
            if let Some((f, start)) = self.flash {
                let t = (elapsed - start) / f.length;
                if t < 1.0 {
                    let a = f.alpha * (1.0 - t).max(0.0);
                    overlay.rect(0.0, 0.0, sw, sh, [f.color[0], f.color[1], f.color[2], a]);
                } else {
                    self.flash = None;
                }
            }

            // Headless screenshot INCLUDING the 2D overlay (HUD / nameplates /
            // target panel): render world + overlay to an offscreen texture and
            // exit. The old `capture_png` path rendered only the 3D world, so
            // every HUD/targeting feature was invisible to RCCE_SHOT. Default
            // frame 150 (zone + actors loaded).
            if let Ok(shot) = std::env::var("RCCE_SHOT") {
                let want = std::env::var("RCCE_SHOT_FRAME")
                    .ok()
                    .and_then(|s| s.parse::<u64>().ok())
                    .unwrap_or(150);
                if self.frames >= want {
                    let (w, h) = (gfx.config.width, gfx.config.height);
                    let stex = gfx.device.create_texture(&wgpu::TextureDescriptor {
                        label: Some("world-shot"),
                        size: wgpu::Extent3d { width: w, height: h, depth_or_array_layers: 1 },
                        mip_level_count: 1,
                        sample_count: 1,
                        dimension: wgpu::TextureDimension::D2,
                        format: gfx.config.format,
                        usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::COPY_SRC,
                        view_formats: &[],
                    });
                    let sview = stex.create_view(&Default::default());
                    let clear = wgpu::Color { r: fog_dn[0] as f64, g: fog_dn[1] as f64, b: fog_dn[2] as f64, a: 1.0 };
                    view.render(&gfx.device, &gfx.queue, &sview, vp, eye, fog_dn, self.fog_near, self.fog_far, ambient_dn, light_dir, clear, self.cam_yaw, elapsed, rcce_client::daynight::night_factor(phase), cam_target);
                    overlay.render(&gfx.device, &gfx.queue, &sview, sw, sh);
                    match rcce_render::save_texture_png(&gfx.device, &gfx.queue, &stex, w, h, gfx.config.format, &shot) {
                        Ok(()) => println!("[client-window] screenshot -> {shot}"),
                        Err(e) => eprintln!("[client-window] screenshot failed: {e}"),
                    }
                    self.shutdown_net();
                    std::process::exit(0);
                }
            }
            overlay.render(&gfx.device, &gfx.queue, &tview, sw, sh);
        }

        frame.present();

        self.frames += 1;

        // Benchmark mode: after a short warmup, time the next N frames and report
        // the average fps, then exit. Lets perf changes be measured headlessly-ish.
        if let Some(n) = self.bench_target {
            const WARMUP: u64 = 120;
            if self.bench_t0.is_none() && self.frames >= WARMUP {
                self.bench_t0 = Some(Instant::now());
                self.bench_f0 = self.frames;
            }
            if let Some(t0) = self.bench_t0 {
                let measured = self.frames - self.bench_f0;
                if measured >= n {
                    let secs = t0.elapsed().as_secs_f32().max(1e-4);
                    let draws = self.view.as_ref().map(|v| v.drawable_count()).unwrap_or(0);
                    let actors = self.net.as_ref().map(|nn| nn.world.actors.len()).unwrap_or(0);
                    println!(
                        "[bench] avg fps over {measured} frames: {:.1} ({secs:.2}s, {actors} actors, {draws} drawables)",
                        measured as f32 / secs
                    );
                    self.shutdown_net();
                    std::process::exit(0);
                }
            }
        }

        if self.last_log.elapsed().as_secs_f32() >= 2.0 {
            let fps = self.frames as f32 / elapsed.max(0.001);
            let (actors, ups, pos) = self
                .net
                .as_ref()
                .map(|n| (n.world.actors.len(), n.updates, (n.world.me_x, n.world.me_y, n.world.me_z)))
                .unwrap_or((0, 0, (0.0, 0.0, 0.0)));
            let (rx, rz, spd) = self
                .net
                .as_ref()
                .map(|n| (n.world.me_render_x, n.world.me_render_z, n.world.me_samples.len() as f32))
                .unwrap_or((0.0, 0.0, 0.0));
            let draws = self.view.as_ref().map(|v| v.drawable_count()).unwrap_or(0);
            println!(
                "[client-window] frame {} (~{fps:.0} fps), {actors} actor(s), {draws} drawables, {ups} packets, me=({:.1},{:.1},{:.1}) render=({:.1},{:.1}) samples={spd:.0}",
                self.frames, pos.0, pos.1, pos.2, rx, rz
            );
            self.last_log = Instant::now();
        }
    }
}

fn main() {
    let mut args = std::env::args().skip(1);
    // Server address: positional arg wins, else RCCE_HOST / RCCE_PORT, else the
    // 127.0.0.1:25000 default. Lets a macOS/Linux client point at a Windows
    // server host without passing CLI args (e.g. launched via compile.sh).
    let host = args
        .next()
        .or_else(|| std::env::var("RCCE_HOST").ok().filter(|s| !s.trim().is_empty()))
        .unwrap_or_else(|| "127.0.0.1".to_string());
    let port: u16 = args
        .next()
        .and_then(|s| s.parse().ok())
        .or_else(|| std::env::var("RCCE_PORT").ok().and_then(|s| s.trim().parse().ok()))
        .unwrap_or(25000);
    let zone = args.next().unwrap_or_else(|| "Plains".to_string());
    let event_loop = EventLoop::new().expect("event loop");
    event_loop.set_control_flow(ControlFlow::Poll);
    let mut app = App::new(host, port, zone);
    event_loop.run_app(&mut app).expect("run app");
}

#[cfg(test)]
mod tests {
    use super::*;

    // Non-blocking login moves the EnetTransport to a worker thread and back
    // through an mpsc channel, which requires `EnetTransport: Send` (a hand-
    // written `unsafe impl`). Gate that contract: if the impl is dropped, the
    // worker `thread::spawn` stops compiling — this test makes the requirement
    // explicit and fails fast with a clear message instead.
    #[test]
    fn enet_transport_and_login_result_are_send() {
        fn assert_send<T: Send>() {}
        assert_send::<EnetTransport>();
        assert_send::<LoginResult>();
    }

    #[test]
    fn vitals_value_uses_configured_health_slot() {
        use std::collections::HashMap;
        let mut attrs: HashMap<u8, (i16, i16)> = HashMap::new();
        attrs.insert(0, (40, 40)); // slot 0 holds some non-Health attribute
        attrs.insert(3, (7, 7)); // slot 3 also has a raw attribute entry

        // Customized project: Health on slot 3. The Health bar (i=3) reads the
        // authoritative me_health mirror (NOT the raw attribute at slot 3); the
        // bar at slot 0 reads its own attribute, NOT HP.
        assert_eq!(vitals_value(3, 3, 85, 100, &attrs), Some((85.0, 100.0)));
        assert_eq!(vitals_value(0, 3, 85, 100, &attrs), Some((40.0, 40.0)));
        // A bar that is neither the health slot nor has an attribute → skipped.
        assert_eq!(vitals_value(5, 3, 85, 100, &attrs), None);

        // Default project (Health = slot 0): unchanged behaviour — regression guard.
        assert_eq!(vitals_value(0, 0, 90, 100, &attrs), Some((90.0, 100.0)));
        // HP clamps: value >= 0, max >= 1 (no divide-by-zero on the bar fraction).
        assert_eq!(vitals_value(0, 0, -5, 0, &attrs), Some((0.0, 1.0)));
    }

    #[test]
    fn menu_buttons_layout_and_hit() {
        // Login screen: a Login button + a Quit button.
        let lb = menu_buttons(Mode::Login, false, false, 0.0, 0.0, 1000.0, 800.0);
        assert_eq!(lb.len(), 2);
        let login = lb.iter().find(|b| b.action == MenuBtnAction::Login).unwrap();
        let (x, y, w, h) = login.rect;
        // A click inside the Login rect dispatches Login; a miss returns None.
        assert_eq!(
            menu_button_hit(&lb, x + w * 0.5, y + h * 0.5),
            Some(MenuBtnAction::Login)
        );
        assert_eq!(menu_button_hit(&lb, x - 10.0, y - 10.0), None);
        assert_eq!(menu_button_hit(&lb, x + w * 0.5, y - 1.0), None); // just above

        // Character-select with a roster: Enter / Create / Delete / Back.
        let cs: Vec<MenuBtnAction> = menu_buttons(Mode::CharSelect, false, true, 0.0, 0.0, 1000.0, 800.0)
            .iter()
            .map(|b| b.action)
            .collect();
        assert_eq!(
            cs,
            vec![
                MenuBtnAction::EnterWorld,
                MenuBtnAction::Create,
                MenuBtnAction::Delete,
                MenuBtnAction::Back
            ]
        );
        // An empty roster drops Enter + Delete (nothing to enter/delete).
        let cs0: Vec<MenuBtnAction> = menu_buttons(Mode::CharSelect, false, false, 0.0, 0.0, 1000.0, 800.0)
            .iter()
            .map(|b| b.action)
            .collect();
        assert_eq!(cs0, vec![MenuBtnAction::Create, MenuBtnAction::Back]);
        // The create-character sub-flow stays keyboard-driven → no buttons.
        assert!(menu_buttons(Mode::CharSelect, true, true, 0.0, 0.0, 1000.0, 800.0).is_empty());
        // Non-button modes draw no buttons.
        assert!(menu_buttons(Mode::Eula, false, false, 0.0, 0.0, 1000.0, 800.0).is_empty());
    }

    // Scenery rotation maps Blitz [pitch,yaw,roll] degrees to render radians with
    // yaw negated (left-handed view) and pitch/roll preserved.
    #[test]
    fn scenery_rot_negates_yaw_only() {
        let r = scenery_rot_radians([30.0, 90.0, -45.0]);
        assert!((r[0] - 30f32.to_radians()).abs() < 1e-6, "pitch preserved");
        assert!((r[1] - (-90f32).to_radians()).abs() < 1e-6, "yaw negated");
        assert!((r[2] - (-45f32).to_radians()).abs() < 1e-6, "roll preserved");
        // Zero stays zero (no spurious offset for axis-aligned props).
        assert_eq!(scenery_rot_radians([0.0, 0.0, 0.0]), [0.0, 0.0, 0.0]);
    }

    // LightModels mesh name -> point light: range = setting1 × mul, colour = RGB
    // (0..255) / 255 × gain, at the scenery position. Non-light meshes -> None.
    #[test]
    fn parse_light_from_mesh_name() {
        let l = parse_light("LightModels\\light_1.5_125_150_210.b3d", [10.0, 5.0, -3.0], 30.0, 1.0).unwrap();
        assert_eq!(l.pos, [10.0, 5.0, -3.0]);
        assert!((l.range - 45.0).abs() < 1e-3, "1.5 × 30, got {}", l.range);
        assert!((l.color[0] - 125.0 / 255.0).abs() < 1e-4);
        assert!((l.color[1] - 150.0 / 255.0).abs() < 1e-4);
        assert!((l.color[2] - 210.0 / 255.0).abs() < 1e-4);
        // gain scales brightness; forward-slash path + no-dir name also parse.
        let l2 = parse_light("light_2_255_0_0.b3d", [0.0; 3], 10.0, 0.5).unwrap();
        assert!((l2.range - 20.0).abs() < 1e-3);
        assert!((l2.color[0] - 0.5).abs() < 1e-4); // 255/255 × 0.5
        assert!(parse_light("Trees/fir06summer.b3d", [0.0; 3], 30.0, 1.0).is_none());
    }

    // A projectile emits 1 core orb + 6 trail quads = 7 quads × 6 verts = 42
    // verts. The core is centred on the projectile head; the trail steps back
    // along the reverse flight direction (target→pos), so its samples are farther
    // from the head than the core's own corners and fade in alpha.
    #[test]
    fn projectile_billboards_core_plus_trail() {
        let pr = rcce_client::world::Projectile {
            x: 0.0,
            y: 10.0,
            z: 0.0,
            target_rid: 0,
            tx: 0.0,
            ty: 10.0,
            tz: 100.0, // flying toward +z
            homing: false,
            speed: 40.0,
        };
        let mut out = Vec::new();
        projectile_billboards(std::slice::from_ref(&pr), [0.0, 10.0, -30.0], [0.0, 10.0, 0.0], &mut out);
        assert_eq!(out.len(), 7 * 6, "1 core + 6 trail quads");
        // The last 6 verts are the bright core orb (drawn last); its centre is the
        // projectile head, so the quad's vertices straddle (0,10,0).
        let core = &out[36..42];
        let cx = core.iter().map(|v| v.pos[0]).sum::<f32>() / 6.0;
        let cy = core.iter().map(|v| v.pos[1]).sum::<f32>() / 6.0;
        let cz = core.iter().map(|v| v.pos[2]).sum::<f32>() / 6.0;
        assert!(cx.abs() < 1e-3 && (cy - 10.0).abs() < 1e-3 && cz.abs() < 1e-3, "core centred at head: {cx},{cy},{cz}");
        assert!((core[0].color[3] - 1.0).abs() < 1e-6, "core is full alpha");
        // Trail samples sit behind the head (−z, since flight is +z) and are fainter.
        let first_trail = &out[0..6];
        let tz = first_trail.iter().map(|v| v.pos[2]).sum::<f32>() / 6.0;
        assert!(tz < -0.4, "first trail sample is behind the head (−z): {tz}");
        assert!(first_trail[0].color[3] < 1.0, "trail is fainter than the core");
    }

    // Underwater = camera eye below a water plane's surface AND within its X/Z
    // bounds; returns the plane's tint colour (else None).
    #[test]
    fn underwater_only_below_surface_and_in_bounds() {
        let img = rcce_data::Image { width: 1, height: 1, rgba: vec![0, 0, 0, 255] };
        let w = rcce_data::WaterPlane {
            tex_id: 0,
            tex_scale: 1.0,
            pos: [0.0, 10.0, 0.0],
            scale_x: 20.0,
            scale_z: 20.0,
            color: [0.1, 0.3, 0.4],
            opacity: 0.5,
        };
        let planes = vec![(w, img)];
        // Below the surface, inside the footprint → tinted.
        assert_eq!(underwater_color(&planes, [0.0, 5.0, 0.0]), Some([0.1, 0.3, 0.4]));
        assert_eq!(underwater_color(&planes, [9.0, 9.9, -9.0]), Some([0.1, 0.3, 0.4]));
        // Above the surface → none, even within the footprint.
        assert_eq!(underwater_color(&planes, [0.0, 12.0, 0.0]), None);
        // Below the surface but outside the X / Z footprint → none.
        assert_eq!(underwater_color(&planes, [50.0, 5.0, 0.0]), None);
        assert_eq!(underwater_color(&planes, [0.0, 5.0, 50.0]), None);
    }

    // A LOD terrain patch (grid N=2) builds a (N+1)² vertex grid with 2 triangles
    // per cell, heights mapped at index x*(N+1)+z, local positions (x,h,z).
    #[test]
    fn terrain_model_grid_geometry() {
        let t = rcce_data::TerrainPatch {
            base_tex_id: 0,
            detail_tex_id: 65535,
            grid: 2,
            heights: vec![0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0],
            pos: [0.0; 3],
            rot: [0.0; 3],
            scale: [1.0; 3],
            detail_tex_scale: 1.0,
        };
        let m = terrain_model(&t);
        let mesh = &m.meshes[0];
        assert_eq!(mesh.positions.len(), 9, "(N+1)² verts");
        assert_eq!(mesh.indices.len(), 2 * 2 * 6, "N² cells × 2 tris × 3 idx");
        assert_eq!(mesh.positions[5], [1.0, 5.0, 2.0], "vertex (x=1,z=2) = height 5");
        assert!(mesh.indices.iter().all(|&i| (i as usize) < mesh.positions.len()), "indices in range");
        assert_eq!(mesh.colors.len(), 9);
        assert_eq!(mesh.uvs.len(), 9);
    }

    // The Actions context menu: NPCs get Interact/Attack/Examine/Trade; players
    // get Interact/Examine only. Hit-testing maps a click to the right row and
    // returns None outside the box, and the menu clamps onto the screen.
    #[test]
    fn context_menu_build_and_hit() {
        let npc = ContextMenu::build(7, false, 100.0, 100.0, 1280.0, 800.0);
        assert_eq!(npc.items.len(), 4);
        assert_eq!(npc.items[1].1, MenuAction::Attack);
        let player = ContextMenu::build(8, true, 100.0, 100.0, 1280.0, 800.0);
        assert_eq!(player.items.len(), 2);
        assert!(player.items.iter().all(|&(_, a)| a != MenuAction::Attack));
        // Row hit-tests (top-left origin).
        assert_eq!(npc.hit(120.0, 100.0 + CTX_ROW * 0.5), Some(MenuAction::Interact));
        assert_eq!(npc.hit(120.0, 100.0 + CTX_ROW * 3.5), Some(MenuAction::Trade));
        assert_eq!(npc.hit(120.0, 100.0 - 5.0), None); // above
        assert_eq!(npc.hit(120.0, 100.0 + CTX_ROW * 4.0 + 1.0), None); // below
        assert_eq!(npc.hit(100.0 + CTX_W + 5.0, 105.0), None); // right of box
        // Off-screen clamp keeps the whole menu visible.
        let edge = ContextMenu::build(9, false, 1279.0, 799.0, 1280.0, 800.0);
        assert!(edge.x + CTX_W <= 1280.0 && edge.y + CTX_ROW * 4.0 <= 800.0);
    }

    // MENU-13: the EULA gate is the initial screen only when license text exists.
    #[test]
    fn eula_gate_initial_mode() {
        assert_eq!(initial_menu_mode(true), Mode::Eula);
        assert_eq!(initial_menu_mode(false), Mode::Login);
    }

    // ESC close-precedence (DELTA blocker #1): ESC dismisses the topmost open
    // layer and only exits when the field is clear.
    #[test]
    fn esc_precedence() {
        // Nothing open -> quit.
        assert_eq!(esc_layer(EscOpen::default()), EscLayer::ExitGame);

        // Transient overlays win over everything beneath them.
        let everything = EscOpen {
            mouse_look: true,
            image_window: true,
            script_input: true,
            dialog: true,
            context_menu: true,
            item_menu: true,
            trade: true,
            spellbook: true,
            inventory: true,
            quests: true,
            party: true,
            target: true,
        };
        assert_eq!(esc_layer(everything), EscLayer::MouseLook);

        // Strict ordering: peel one layer at a time, top to bottom.
        let mut o = everything;
        o.mouse_look = false;
        assert_eq!(esc_layer(o), EscLayer::ImageWindow);
        o.image_window = false;
        assert_eq!(esc_layer(o), EscLayer::ScriptInput);
        o.script_input = false;
        assert_eq!(esc_layer(o), EscLayer::Dialog);
        o.dialog = false;
        assert_eq!(esc_layer(o), EscLayer::ContextMenu);
        o.context_menu = false;
        assert_eq!(esc_layer(o), EscLayer::ItemMenu);
        o.item_menu = false;
        assert_eq!(esc_layer(o), EscLayer::Trade);
        o.trade = false;
        assert_eq!(esc_layer(o), EscLayer::Spellbook);
        o.spellbook = false;
        assert_eq!(esc_layer(o), EscLayer::Inventory);
        o.inventory = false;
        assert_eq!(esc_layer(o), EscLayer::Quests);
        o.quests = false;
        assert_eq!(esc_layer(o), EscLayer::Party);
        o.party = false;
        assert_eq!(esc_layer(o), EscLayer::Target);
        o.target = false;
        assert_eq!(esc_layer(o), EscLayer::ExitGame);

        // A single open panel closes itself, does NOT exit (the actual bug).
        assert_eq!(
            esc_layer(EscOpen { inventory: true, ..Default::default() }),
            EscLayer::Inventory
        );
        assert_eq!(
            esc_layer(EscOpen { target: true, ..Default::default() }),
            EscLayer::Target
        );
    }

    // Storm lightning (ENV-5): fires only while storming and once due.
    #[test]
    fn lightning_trigger() {
        assert!(lightning_fires(true, 10.0, 9.0));
        assert!(!lightning_fires(true, 8.0, 9.0));
        assert!(!lightning_fires(false, 10.0, 9.0));
    }

    // Cycle-target (TGT-7): walks the sorted candidate list, wrapping; falls to
    // the first when the current target is absent; None on an empty list.
    #[test]
    fn cycle_target_wraps() {
        let s = [3u16, 7, 9];
        assert_eq!(next_target(None, &s), Some(3)); // no target -> first
        assert_eq!(next_target(Some(3), &s), Some(7)); // advance
        assert_eq!(next_target(Some(7), &s), Some(9));
        assert_eq!(next_target(Some(9), &s), Some(3)); // wrap to first
        assert_eq!(next_target(Some(99), &s), Some(3)); // stale -> first
        assert_eq!(next_target(Some(3), &[]), None); // nothing to target
    }

    // Memorise progress (SPL-4): 0 at start, ramps to 1.0 over MEMORISE_SECS, clamped.
    #[test]
    fn memorise_progress_ramps() {
        assert_eq!(memorise_progress(10.0, 10.0), 0.0);
        assert!((memorise_progress(10.0, 10.0 + MEMORISE_SECS * 0.5) - 0.5).abs() < 1e-6);
        assert_eq!(memorise_progress(10.0, 10.0 + MEMORISE_SECS), 1.0);
        assert_eq!(memorise_progress(10.0, 100.0), 1.0); // clamped at full
        assert_eq!(memorise_progress(10.0, 9.0), 0.0); // clamped at zero
    }

    // Idle fidget gate (ANIM-6): only when idle, at the 1/1000 probability.
    #[test]
    fn fidget_gate() {
        assert!(fidget_fires(1000, true)); // 1000 % 1000 == 0
        assert!(fidget_fires(0, true));
        assert!(!fidget_fires(1, true)); // not a multiple
        assert!(!fidget_fires(1000, false)); // not idle → never
        assert!(!fidget_fires(0, false));
        // Over a full LCG period the gate fires roughly 1/1000 of idle frames.
        let mut rng: u32 = 0x2545_F491;
        let mut fires = 0;
        for _ in 0..100_000 {
            rng = rng.wrapping_mul(1_664_525).wrapping_add(1_013_904_223);
            if fidget_fires(rng, true) {
                fires += 1;
            }
        }
        assert!((50..200).contains(&fires), "≈1/1000 over 100k: {fires}");
    }

    // Double-click gate (MOVE-6): close in time AND space.
    #[test]
    fn double_click_gate() {
        assert!(is_double_click(120, 4.0)); // fast + near
        assert!(!is_double_click(500, 4.0)); // too slow
        assert!(!is_double_click(120, 40.0)); // too far
        assert!(!is_double_click(500, 40.0));
        assert!(is_double_click(349, 11.9)); // just inside both bounds
    }

    // Run derivation (MOVE-6): Shift always runs; a double-click move runs only
    // while a click-to-move target is active.
    #[test]
    fn move_run_sources() {
        assert!(move_run(true, false, false)); // shift wins regardless
        assert!(move_run(false, true, true)); // double-click move-to
        assert!(!move_run(false, false, true)); // dbl flag but no active target
        assert!(!move_run(false, true, false)); // walking click (single)
        assert!(!move_run(false, false, false));
    }

    // First-person camera (CAM-4): eye at head height, looking along facing.
    #[test]
    fn first_person_eye_and_forward() {
        let (eye, target) = first_person_view([10.0, 2.0, -5.0], 0.0);
        // Eye is at the player's head (feet + eye height).
        assert_eq!(eye, [10.0, 2.0 + FP_EYE_HEIGHT, -5.0]);
        // yaw=0 looks toward -Z (matches the rear-follow view direction).
        let fwd = [target[0] - eye[0], target[1] - eye[1], target[2] - eye[2]];
        assert!((fwd[0]).abs() < 1e-6 && (fwd[2] + 1.0).abs() < 1e-6, "yaw 0 -> -Z: {fwd:?}");
        // A quarter turn looks toward -X.
        let (eye2, t2) = first_person_view([0.0, 0.0, 0.0], std::f32::consts::FRAC_PI_2);
        let f2 = [t2[0] - eye2[0], t2[2] - eye2[2]];
        assert!((f2[0] + 1.0).abs() < 1e-5 && f2[1].abs() < 1e-5, "yaw 90 -> -X: {f2:?}");
    }

    // MMB snap-camera (CAM-5): yaw -> character facing, pitch -> level.
    #[test]
    fn camera_snap_behind() {
        assert_eq!(snap_camera(1.5), (1.5, 0.0));
        assert_eq!(snap_camera(-2.0), (-2.0, 0.0));
        assert_eq!(snap_camera(0.0), (0.0, 0.0));
    }

    // Jump arc (MOVE-7): starting from the ground with the initial upward
    // velocity, the offset rises to a positive apex then lands (grounded again)
    // within a sane number of frames; once grounded the step holds at zero.
    #[test]
    fn jump_arc_rises_and_lands() {
        let (mut o, mut v, mut grounded) = (0.0f32, JUMP_INIT_VEL, false);
        let (mut apex, mut frames) = (0.0f32, 0);
        while !grounded && frames < 1000 {
            let (no, nv, g) = jump_step(o, v);
            o = no;
            v = nv;
            grounded = g;
            apex = apex.max(o);
            frames += 1;
        }
        assert!(grounded, "jump must land");
        assert!(apex > 0.3, "apex should rise meaningfully: {apex}");
        assert!((5..40).contains(&frames), "airborne a sane number of frames: {frames}");
        // Grounded is a fixed point: stepping from rest stays at rest.
        assert_eq!(jump_step(0.0, 0.0), (0.0, 0.0, true));
    }

    // Chat scrollback window (CHAT-3): newest-first, skipping the `skip` newest.
    #[test]
    fn chat_scrollback_window() {
        let lines: Vec<(String, [f32; 4])> = (1..=16).map(|n| (format!("line {n}"), [0.0; 4])).collect();
        let t0: Vec<&str> = visible_chat(&lines, 0, 5).into_iter().map(|(t, _)| t.as_str()).collect();
        assert_eq!(t0, vec!["line 16", "line 15", "line 14", "line 13", "line 12"]);
        let t8: Vec<&str> = visible_chat(&lines, 8, 5).into_iter().map(|(t, _)| t.as_str()).collect();
        assert_eq!(t8, vec!["line 8", "line 7", "line 6", "line 5", "line 4"]);
        assert!(visible_chat(&lines, 16, 5).is_empty()); // renderer clamps before this
    }

    // The auto-combat decision (CBT-1): chase when out of melee range, swing in
    // range when the cooldown is ready, else wait for the cooldown.
    #[test]
    fn combat_step_decisions() {
        let r = MELEE_RANGE;
        assert_eq!(combat_step(10.0, r, true), CombatStep::Chase);
        assert_eq!(combat_step(10.0, r, false), CombatStep::Chase);
        assert_eq!(combat_step(3.0, r, true), CombatStep::Swing);
        assert_eq!(combat_step(3.0, r, false), CombatStep::Wait);
        assert_eq!(combat_step(MELEE_RANGE, r, true), CombatStep::Swing);
        // With a longer (ranged) reach, a far target is in range → Swing.
        assert_eq!(combat_step(10.0, 19.5, true), CombatStep::Swing);
        assert_eq!(combat_step(20.0, 19.5, true), CombatStep::Chase);
    }

    // Effective attack range (CBT-2 / blocker #5b): ranged weapon with health
    // reaches range-0.5; broken ranged / melee / no weapon use the melee base.
    #[test]
    fn effective_range_ranged_vs_melee() {
        // Ranged (wtype 3), health>0, reach 20 → 19.5.
        assert_eq!(effective_attack_range(3, 20.0, 100, 4.5), 19.5);
        // Ranged but broken (health 0) → melee base.
        assert_eq!(effective_attack_range(3, 20.0, 0, 4.5), 4.5);
        // Melee weapon (wtype 1) → melee base regardless of range field.
        assert_eq!(effective_attack_range(1, 20.0, 100, 4.5), 4.5);
        // No weapon (wtype 0) → melee base.
        assert_eq!(effective_attack_range(0, 0.0, 0, 4.5), 4.5);
        // A configured ranged reach shorter than melee is floored at melee.
        assert_eq!(effective_attack_range(3, 3.0, 100, 4.5), 4.5);
    }

    // Camera zoom (CAM-3): steps adjust the boom length and clamp to [5,50];
    // negative zooms in, positive out.
    // Death-anim variety (CBT-6): alternates the first-tried humanoid death clip
    // by actor id; both orderings still include the animal "Die" fallback.
    #[test]
    fn death_clip_alternates() {
        assert_eq!(death_clip(2)[0], "Death 2"); // even id
        assert_eq!(death_clip(3)[0], "Death 1"); // odd id
        // Both contain "Death 1", "Death 2" and the animal "Die" fallback.
        for rid in [2u16, 3] {
            let c = death_clip(rid);
            assert!(c.contains(&"Death 1") && c.contains(&"Death 2") && c.contains(&"Die"));
        }
    }

    // CBT-5 chat-line damage style: outgoing/incoming/miss composition + names.
    #[test]
    fn damage_line_composition() {
        let me = 1u16;
        let name = |rid: u16| match rid {
            7 => "Goblin".to_string(),
            _ => "Someone".to_string(),
        };
        // Outgoing hit: target 7, attacker me.
        let (l, c) = compose_damage_line(7, me, 5, me, name);
        assert_eq!(l, "You hit Goblin for 5 damage!");
        assert_eq!(c, [0.4, 1.0, 0.4, 1.0]); // green
        // Incoming hit: target me, attacker 7.
        let (l, c) = compose_damage_line(me, 7, 3, me, name);
        assert_eq!(l, "Goblin hits you for 3 damage!");
        assert_eq!(c, [1.0, 0.4, 0.4, 1.0]); // red
        // Outgoing miss (damage 0).
        let (l, _) = compose_damage_line(7, me, 0, me, name);
        assert_eq!(l, "You attack Goblin and miss!");
        // Incoming miss.
        let (l, _) = compose_damage_line(me, 7, 0, me, name);
        assert_eq!(l, "Goblin attacks you and misses!");
        // Unknown attacker name falls back.
        let (l, _) = compose_damage_line(me, 99, 2, me, name);
        assert_eq!(l, "Someone hits you for 2 damage!");
    }

    #[test]
    fn free_backpack_slot_picks_first_gap() {
        use std::collections::HashSet;
        // Empty bag → first backpack slot (14).
        assert_eq!(first_free_backpack_slot(&HashSet::new()), 14);
        // 14 taken → 15.
        assert_eq!(first_free_backpack_slot(&HashSet::from([14])), 15);
        // A gap at 16 (14,15 taken) → 16; equipment slots (0..13) don't count.
        assert_eq!(first_free_backpack_slot(&HashSet::from([0, 1, 14, 15])), 16);
        // Full backpack → fallback 14 (server then relocates/rejects).
        let full: HashSet<u8> = (14u8..=45).collect();
        assert_eq!(first_free_backpack_slot(&full), 14);
    }

    #[test]
    fn quantity_clamps() {
        // 1..=max, never 0 or above the stack.
        assert_eq!(clamp_qty(5, 10), 5);
        assert_eq!(clamp_qty(0, 10), 1);
        assert_eq!(clamp_qty(-3, 10), 1);
        assert_eq!(clamp_qty(99, 10), 10);
        assert_eq!(clamp_qty(10, 10), 10);
        // max 0 is treated as 1 (defensive — a 0-stack shouldn't reach here).
        assert_eq!(clamp_qty(5, 0), 1);
    }

    #[test]
    fn spellbook_scroll_clamps() {
        // Everything fits → no scroll regardless of the requested offset.
        assert_eq!(clamp_scroll(0, 8, 10), 0);
        assert_eq!(clamp_scroll(5, 8, 10), 0);
        // 18 spells, 10 visible → last full page starts at 8.
        assert_eq!(clamp_scroll(0, 18, 10), 0);
        assert_eq!(clamp_scroll(3, 18, 10), 3);
        assert_eq!(clamp_scroll(8, 18, 10), 8);
        assert_eq!(clamp_scroll(99, 18, 10), 8); // can't scroll past the end
        // Exactly one page.
        assert_eq!(clamp_scroll(4, 10, 10), 0);
    }

    // Sound options master-volume step clamps to [0,1].
    #[test]
    fn volume_step_clamps() {
        assert_eq!(volume_step(0.5, 0.05), 0.55);
        assert_eq!(volume_step(0.5, -0.05), 0.45);
        assert_eq!(volume_step(0.98, 0.05), 1.0); // clamp high
        assert_eq!(volume_step(0.02, -0.05), 0.0); // clamp low
        assert_eq!(volume_step(0.0, -0.1), 0.0);
        assert_eq!(volume_step(1.0, 0.1), 1.0);
    }

    #[test]
    fn zoom_step_clamps() {
        assert_eq!(zoom_step(13.0, -1.5), 11.5); // zoom in
        assert_eq!(zoom_step(13.0, 1.5), 14.5); // zoom out
        assert_eq!(zoom_step(6.0, -10.0), CAM_DIST_MIN); // clamp at min
        assert_eq!(zoom_step(45.0, 100.0), CAM_DIST_MAX); // clamp at max
        assert_eq!(zoom_step(CAM_DIST_MIN, -1.0), CAM_DIST_MIN); // already at floor
        assert_eq!(zoom_step(CAM_DIST_MAX, 1.0), CAM_DIST_MAX); // already at ceil
    }

    #[test]
    fn unproject_terrain_converges_from_wrong_guess() {
        // Flat terrain at y=10; the click-to-move raycast should land on it even
        // when started from a stale guess (the old bug: a stale me_y plane gave a
        // short target). Converging means it matches a direct unproject at y=10.
        let hf = rcce_client::terrain::HeightField::flat(10.0);
        let vp = rcce_render::view_proj([0.0, 40.0, 30.0], [0.0, 10.0, 0.0], 1.6);
        let (sw, sh, cx, cy) = (1280.0, 800.0, 640.0, 560.0);
        let direct = rcce_render::unproject_ground(&vp, sw, sh, cx, cy, 10.0).unwrap();
        // Start from a very wrong height (0.0) — must still converge to the y=10 hit.
        let g = unproject_terrain(&vp, sw, sh, cx, cy, Some(&hf), 0.0).unwrap();
        assert!((g[0] - direct[0]).abs() < 0.1, "x: {} vs {}", g[0], direct[0]);
        assert!((g[1] - direct[2]).abs() < 0.1, "z: {} vs {}", g[1], direct[2]);
    }

    #[test]
    fn camera_boom_collision() {
        let dir = [0.0, 0.0, 1.0];
        // No occluders -> full boom.
        assert_eq!(camera_boom([0.0; 3], dir, 13.0, &[]), 13.0);
        // Sphere centred 10 ahead, r=2 (spans z=8..12): the march stops before
        // its eye enters at z=8 — within one 0.4 step below 8.
        let occ = [([0.0, 0.0, 10.0], 2.0)];
        let d = camera_boom([0.0; 3], dir, 13.0, &occ);
        assert!(d >= 7.6 && d < 8.0, "expected ~7.6..8, got {d}");
        // Sphere off to the side -> missed, full boom.
        let side = [([20.0, 0.0, 10.0], 2.0)];
        assert_eq!(camera_boom([0.0; 3], dir, 13.0, &side), 13.0);
        // Sphere behind the pivot (opposite dir) -> not a hit.
        let behind = [([0.0, 0.0, -10.0], 2.0)];
        assert_eq!(camera_boom([0.0; 3], dir, 13.0, &behind), 13.0);
        // Pivot INSIDE a sphere (player indoors) -> collapse to the minimum.
        let around = [([0.0, 0.0, 0.0], 6.0)];
        let d = camera_boom([0.0; 3], dir, 13.0, &around);
        assert!((d - 2.5).abs() < 1e-4, "indoors -> MIN 2.5, got {d}");
        // Nearest occluder wins.
        let many = [([0.0, 0.0, 30.0], 2.0), ([0.0, 0.0, 6.0], 1.0), ([0.0, 0.0, 12.0], 1.0)];
        let d = camera_boom([0.0; 3], dir, 40.0, &many);
        assert!(d >= 4.6 && d < 5.0, "nearest hit ~5 -> ~4.6..5, got {d}");
    }

    // The function-button row hit-test must agree with the draw geometry: a
    // click at each button's centre returns that button's action, and the gaps
    // / outside the row return None.
    #[test]
    fn function_button_hit_test() {
        let (sw, sh) = (1280.0f32, 800.0f32);
        let by = FBTN_Y * sh;
        let (bw, bh) = (FBTN_W * sw, FBTN_H * sh);
        let cy = by + bh * 0.5;
        // Centre of each button maps back to its own action, in order.
        let expect = [
            HudAction::Chat, HudAction::Map, HudAction::Inventory, HudAction::Spells,
            HudAction::Character, HudAction::Quests, HudAction::Party, HudAction::Menu,
        ];
        for (idx, (action, fx, _, _)) in FUNCTION_BUTTONS.iter().enumerate() {
            let cx = fx * sw + bw * 0.5;
            let got = function_button_at(cx, cy, sw, sh).expect("button hit");
            assert!(got == *action && got == expect[idx], "button {idx} mismatch");
        }
        // Above the row (well clear of the baseline) → nothing.
        assert!(function_button_at(0.7 * sw, by - bh, sw, sh).is_none());
        // Far left of the cluster (the spell-slot area) → no function button.
        assert!(function_button_at(0.1 * sw, cy, sw, sh).is_none());
        // Just past the last button's right edge → nothing.
        let last_x = FUNCTION_BUTTONS[7].1 * sw + bw + 2.0;
        assert!(function_button_at(last_x, cy, sw, sh).is_none());
    }

    #[test]
    fn compass_strip_marks() {
        use std::f32::consts::PI;
        // Facing N (heading 0): N is dead-centre; E to the right edge, W to the
        // left edge (fov = PI), S is just out of view.
        let m = compass_marks(0.0, PI);
        let n = m.iter().find(|(_, l)| *l == "N").expect("N visible");
        assert!(n.0.abs() < 1e-4, "N should be centred, got {}", n.0);
        let e = m.iter().find(|(_, l)| *l == "E").expect("E visible");
        assert!((e.0 - 0.5).abs() < 1e-4, "E at right edge, got {}", e.0);
        let w = m.iter().find(|(_, l)| *l == "W").expect("W visible");
        assert!((w.0 + 0.5).abs() < 1e-4, "W at left edge, got {}", w.0);
        assert!(m.iter().all(|(_, l)| *l != "S"), "S should be hidden facing N");

        // Turning right (heading +PI/2 = facing E): E becomes centred, N moves to
        // the left edge — the strip scrolls left as you turn right.
        let m = compass_marks(PI * 0.5, PI);
        let e = m.iter().find(|(_, l)| *l == "E").expect("E visible");
        assert!(e.0.abs() < 1e-4, "E centred facing E, got {}", e.0);
        let n = m.iter().find(|(_, l)| *l == "N").expect("N visible");
        assert!((n.0 + 0.5).abs() < 1e-4, "N at left edge, got {}", n.0);
    }

    #[test]
    fn spell_slot_hit_test() {
        let (sw, sh) = (1280.0f32, 800.0f32);
        let by = FBTN_Y * sh;
        let (sw_, sh_) = (FBTN_W * sw, FBTN_H * sh);
        let cy = by + sh_ * 0.5;
        // Centre of each of the 12 slots maps back to its index.
        for i in 0..12usize {
            let cx = (SPELLBAR_X0 + i as f32 * SPELLBAR_PITCH) * sw + sw_ * 0.5;
            assert_eq!(spell_slot_at(cx, cy, sw, sh), Some(i), "slot {i}");
        }
        // Above the bar → none; far right (past slot 11) → none.
        assert_eq!(spell_slot_at(0.2 * sw, by - sh_, sw, sh), None);
        let past = (SPELLBAR_X0 + 12.0 * SPELLBAR_PITCH) * sw + 4.0;
        assert_eq!(spell_slot_at(past, cy, sw, sh), None);
    }

    #[test]
    fn action_bar_autofill_vs_explicit() {
        use rcce_client::fetch::{CharacterSheet, SpellInfo};
        let sp = |id: u16, mem: bool| SpellInfo {
            id,
            level: 1,
            thumb_tex: 0,
            recharge: 1000,
            name: format!("S{id}"),
            description: String::new(),
            memorised: mem,
        };
        // Sheet lists 3 spells; the LIVE memorised set (ids 10, 30) drives the
        // auto-fill — the sheet's own `memorised` flag is now ignored, so the bar
        // tracks in-session memorise changes.
        let sheet = CharacterSheet {
            spells: vec![sp(10, false), sp(20, false), sp(30, false)],
            ..Default::default()
        };
        let mem: std::collections::HashSet<u16> = [10u16, 30].into_iter().collect();

        // All-None bar → auto-fill from the memorised SET, in sheet order, skipping
        // the un-memorised id (20).
        let empty = [None; 12];
        let auto = effective_action_bar(&empty, Some(&sheet), &mem);
        assert_eq!(auto[0], Some(HotbarEntry::Spell(10)));
        assert_eq!(auto[1], Some(HotbarEntry::Spell(30)));
        assert_eq!(auto[2], None);

        // Empty memorised set → no auto-fill even with spells in the sheet.
        assert_eq!(effective_action_bar(&empty, Some(&sheet), &std::collections::HashSet::new()), [None; 12]);

        // Any explicit assignment switches the whole bar to the explicit layout —
        // the auto-fill no longer leaks in, gaps are honoured, and an item entry is
        // preserved alongside spells.
        let mut explicit = [None; 12];
        explicit[4] = Some(HotbarEntry::Spell(30));
        explicit[5] = Some(HotbarEntry::Item(99));
        let resolved = effective_action_bar(&explicit, Some(&sheet), &mem);
        assert_eq!(resolved[0], None, "auto-fill must not leak once customised");
        assert_eq!(resolved[4], Some(HotbarEntry::Spell(30)));
        assert_eq!(resolved[5], Some(HotbarEntry::Item(99)));

        // No sheet → empty bar stays empty (no panic).
        assert_eq!(effective_action_bar(&empty, None, &mem), [None; 12]);
    }

    #[test]
    fn vendor_window_hit_test() {
        let (sw, sh) = (1280.0f32, 800.0f32);
        let (px, py, pw, ph) = vendor_window_rect(sw, sh);
        // Right-anchored (40px gap) and vertically centred.
        assert!((px - (sw - pw - 40.0)).abs() < 1.0);
        assert!((py - (sh - ph) * 0.5).abs() < 1.0);
        // Centre is inside; points just outside each edge are not.
        assert!(point_in_vendor(px + pw * 0.5, py + ph * 0.5, sw, sh));
        assert!(!point_in_vendor(px - 1.0, py + 5.0, sw, sh));
        assert!(!point_in_vendor(px + pw * 0.5, py - 1.0, sw, sh));
        assert!(!point_in_vendor(px + pw + 1.0, py + 5.0, sw, sh));
        // The vendor sits clear of the action bar's spell slots (left-anchored), so
        // a sell-drop and a hotbar-drop never both fire for one release.
        assert!(spell_slot_at(px + pw * 0.5, py + ph * 0.5, sw, sh).is_none());
    }

    #[test]
    fn inventory_drag_dest_resolution() {
        // Backpack (20) onto the equipment column (slot 2) equips to the item's
        // proper slot (here weapon = 0), regardless of which gear slot was hit.
        assert_eq!(resolve_inventory_dest(20, 2, Some(0)), Some(0));
        // A non-equippable item dropped on the gear column → no move.
        assert_eq!(resolve_inventory_dest(20, 2, None), None);
        // Backpack → backpack is a direct swap to the dropped slot.
        assert_eq!(resolve_inventory_dest(20, 31, Some(0)), Some(31));
        // Equipment (0) → backpack (25) unequips to that bag slot (direct).
        assert_eq!(resolve_inventory_dest(0, 25, Some(0)), Some(25));
        // Dropping back onto the same slot is a no-op.
        assert_eq!(resolve_inventory_dest(20, 20, Some(0)), None);
        // Equipment → equipment is a direct swap (the equip-resolve only applies
        // to a backpack source), e.g. weapon slot 0 onto shield slot 1.
        assert_eq!(resolve_inventory_dest(0, 1, Some(0)), Some(1));
    }

    #[test]
    fn inventory_slot_hit_test() {
        use rcce_data::IComp;
        let (sw, sh) = (1280.0f32, 800.0f32);
        // Mirror the real InventoryWindow + a 2-slot row at the documented
        // window-relative positions (button 0 at 0.035, button 1 at 0.155).
        let iw = IComp { x: 0.25, y: 0.2, w: 0.5, h: 0.55, alpha: 1.0, rgb: [0; 3] };
        let mk = |x: f32, y: f32| IComp { x, y, w: 0.09, h: 0.11, alpha: 1.0, rgb: [0; 3] };
        let buttons = [mk(0.035, 0.02), mk(0.155, 0.02)];
        // Centre of button 1 resolves to index 1.
        let b = &buttons[1];
        let cx = (iw.x + (b.x + b.w * 0.5) * iw.w) * sw;
        let cy = (iw.y + (b.y + b.h * 0.5) * iw.h) * sh;
        assert_eq!(inventory_slot_at(cx, cy, iw, &buttons, sw, sh), Some(1));
        // A point left of the whole window → no slot.
        assert_eq!(inventory_slot_at(0.0, cy, iw, &buttons, sw, sh), None);
    }

    #[test]
    fn inv_action_button_hit_test() {
        use rcce_data::IComp;
        let (sw, sh) = (1280.0f32, 800.0f32);
        let iw = IComp { x: 0.25, y: 0.2, w: 0.5, h: 0.55, alpha: 1.0, rgb: [0; 3] };
        // Real inv_drop / inv_eat window-relative rects from Interface.dat.
        let drop = IComp { x: 0.76, y: 0.93, w: 0.2, h: 0.045, alpha: 1.0, rgb: [0; 3] };
        let eat = IComp { x: 0.5, y: 0.93, w: 0.2, h: 0.045, alpha: 1.0, rgb: [0; 3] };
        let centre = |c: IComp| {
            (
                (iw.x + (c.x + c.w * 0.5) * iw.w) * sw,
                (iw.y + (c.y + c.h * 0.5) * iw.h) * sh,
            )
        };
        let (dx, dy) = centre(drop);
        assert_eq!(inv_action_button_at(dx, dy, iw, drop, eat, sw, sh), Some(InvAction::Drop));
        let (ex, ey) = centre(eat);
        assert_eq!(inv_action_button_at(ex, ey, iw, drop, eat, sw, sh), Some(InvAction::Eat));
        // Centre of the window (the slot grid) hits neither button.
        let cx = (iw.x + 0.5 * iw.w) * sw;
        let cy = (iw.y + 0.4 * iw.h) * sh;
        assert_eq!(inv_action_button_at(cx, cy, iw, drop, eat, sw, sh), None);
    }

    #[test]
    fn actor_pick_nearest() {
        // Identity view-proj: project maps world (x,y,_) → screen
        // ((x*0.5+0.5)*sw, (1-(y*0.5+0.5))*sh), so points are placeable by hand.
        let vp = [
            1.0, 0.0, 0.0, 0.0,
            0.0, 1.0, 0.0, 0.0,
            0.0, 0.0, 1.0, 0.0,
            0.0, 0.0, 0.0, 1.0,
        ];
        let (sw, sh) = (1000.0f32, 1000.0f32);
        // A at world 0,0,0 → screen centre (500,500); B at 0.5,0,0 → x=750.
        let actors = [(10u16, [0.0, 0.0, 0.0]), (20u16, [0.5, 0.0, 0.0])];
        assert_eq!(actor_at(505.0, 500.0, &actors, &vp, sw, sh, 32.0), Some(10));
        assert_eq!(actor_at(745.0, 500.0, &actors, &vp, sw, sh, 32.0), Some(20));
        // Equidistant-ish but closer to B; and a far point → None.
        assert_eq!(actor_at(700.0, 500.0, &actors, &vp, sw, sh, 60.0), Some(20));
        assert_eq!(actor_at(500.0, 100.0, &actors, &vp, sw, sh, 32.0), None);
    }
}
