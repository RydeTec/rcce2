//! Data-driven asset resolution: actor template id → base mesh id (`Actors.dat`)
//! → `.b3d` path (`Meshes.dat` catalog) → parsed [`B3dModel`], with caching.
//! Reads the same files the GUE editor writes, so the client draws each actor
//! as its real model.

use std::collections::HashMap;
use std::path::PathBuf;
use std::rc::Rc;

use rcce_data::{
    texture, ActorCatalog, AnimClip, AnimSetCatalog, B3dModel, Image, InterfaceLayout, ItemCatalog,
    MeshCatalog, MoneyConfig, MusicCatalog, SoundCatalog, TextureCatalog,
};

/// Parse the account name from `Last Username.dat` contents (MENU-12): the first
/// line, trimmed (line 2 is an obfuscated password we ignore). `None` if empty.
pub fn parse_last_username(raw: &str) -> Option<String> {
    let name = raw.lines().next().unwrap_or("").trim().to_string();
    (!name.is_empty()).then_some(name)
}

pub struct AssetStore {
    data_root: PathBuf,
    actors: ActorCatalog,
    meshes: MeshCatalog,
    textures: TextureCatalog,
    anims: AnimSetCatalog,
    music: MusicCatalog,
    sounds: SoundCatalog,
    items: ItemCatalog,
    /// In-game HUD layout (Interface.dat) — fractional element positions that
    /// match the real Client.exe. `None` if the file is absent.
    interface: Option<InterfaceLayout>,
    /// Currency denominations (Money.dat) for the HUD money readout (HUD-3).
    /// Defaults to the stock Copper/Silver/Gold/Platinum if the file is absent.
    money: MoneyConfig,
    attribute_names: Option<rcce_data::AttributeNames>,
    /// Project-assigned role→slot indices (Health/Energy/… ) from
    /// `Game Data/Fixed Attributes.dat`. `None` when the file is absent (the
    /// `health_stat()` accessor then falls back to the index-0 default).
    fixed_attributes: Option<rcce_data::FixedAttributes>,
    /// Project weapon damage-type names (`Server Data/Damage.dat`), for the
    /// weapon tooltip. Empty when the file is absent (then no type is shown).
    damage_types: rcce_data::DamageTypes,
    /// Project client-display options (`Game Data/Other.dat`): nametag visibility,
    /// view mode, chat-bubble options. Default (all-zero) when the file is absent.
    other: rcce_data::OtherConfig,
    /// Project sun/moon directional lights from `Game Data/Suns.dat`. `None` when
    /// absent → the renderer uses a neutral white sun. See `sun_light`.
    suns: Option<rcce_data::Suns>,
    /// Localizable client strings from `Game Data/Language.txt`. Empty when the
    /// file is absent → callers fall back to hardcoded English. See `language`.
    language: rcce_data::Language,
    cache: HashMap<u16, Option<Rc<B3dModel>>>,
    /// Memoised decoded actor skins, keyed by appearance, so per-frame actor
    /// rebuilds don't re-read + re-decode the skin files from disk.
    actor_tex_cache: HashMap<String, Rc<Vec<Option<Image>>>>,
}

/// The looping frame for a clip at `elapsed` seconds, given the timeline `fps`.
/// Honors the clip's speed; single-frame clips return their start.
pub fn clip_frame(clip: &AnimClip, fps: f32, elapsed: f32) -> f32 {
    let len = (clip.end - clip.start).max(0) as f32;
    if len <= 0.0 {
        return clip.start as f32;
    }
    let advanced = elapsed * fps.max(0.001) * clip.speed.max(0.001);
    // Wrap over `len`, NOT `len + 1`: the frame stays within [start, end), so the
    // skeleton never interpolates the clip's last keyframe against the *next*
    // clip's first keyframe (which is what the +1 did — a one-frame wrong pose
    // every loop, the "snap"). The keyframe bracket is global, so it can't blend
    // end→start; keeping the frame inside the clip avoids the cross-clip blend.
    clip.start as f32 + advanced.rem_euclid(len)
}

/// A head attachment (hair or beard): its model + textures, plus the mesh
/// catalog's own position offset and scale (the engine's `LoadedMeshX/Y/Z` and
/// `LoadedMeshScales`, applied relative to the body's `Head` joint).
pub struct Attachment {
    pub mesh_id: u16,
    pub model: Rc<B3dModel>,
    pub textures: Vec<Option<Image>>,
    pub offset: [f32; 3],
    pub scale: f32,
}

/// World placement (translation, rot radians, per-axis scale) for an attachment
/// parented to the body's `Head` joint. Mirrors the engine: the attachment sits
/// at `head_offset + catalog_offset` in the actor's scaled, yaw-rotated frame.
/// `body_translation` is the body instance's ground-seated world translation;
/// `yaw` matches `glam::Mat4::from_rotation_y`.
pub fn attachment_placement(
    body_translation: [f32; 3],
    yaw: f32,
    actor_scale: f32,
    head_offset: [f32; 3],
    att: &Attachment,
) -> ([f32; 3], [f32; 3], [f32; 3]) {
    let lx = (head_offset[0] + att.offset[0]) * actor_scale;
    let ly = (head_offset[1] + att.offset[1]) * actor_scale;
    let lz = (head_offset[2] + att.offset[2]) * actor_scale;
    let (s, c) = yaw.sin_cos(); // from_rotation_y: x' = x c + z s, z' = -x s + z c
    let translation = [
        body_translation[0] + lx * c + lz * s,
        body_translation[1] + ly,
        body_translation[2] - lx * s + lz * c,
    ];
    let scale = actor_scale * att.scale;
    (translation, [0.0, yaw, 0.0], [scale, scale, scale])
}

impl AssetStore {
    /// `data_root` is the project `data/` directory (containing `Server Data/`,
    /// `Game Data/`, `Meshes/`).
    pub fn load(data_root: impl Into<PathBuf>) -> Result<Self, String> {
        let data_root = data_root.into();
        let actors_bytes = std::fs::read(data_root.join("Server Data/Actors.dat"))
            .map_err(|e| format!("Actors.dat: {e}"))?;
        let mesh_bytes = std::fs::read(data_root.join("Game Data/Meshes.dat"))
            .map_err(|e| format!("Meshes.dat: {e}"))?;
        let actors = ActorCatalog::parse(&actors_bytes).map_err(|e| format!("Actors.dat: {e}"))?;
        let meshes = MeshCatalog::parse(&mesh_bytes)
            .map_err(|e| format!("Meshes.dat: {e}"))?
            .value;
        // Texture catalog (for real actor skins). Non-fatal if absent.
        let textures = std::fs::read(data_root.join("Game Data/Textures.dat"))
            .ok()
            .and_then(|b| TextureCatalog::parse(&b).ok())
            .map(|p| p.value)
            .unwrap_or_default();
        // Animation-set table (named clip ranges). Non-fatal if absent.
        let anims = std::fs::read(data_root.join("Game Data/Animations.dat"))
            .ok()
            .and_then(|b| AnimSetCatalog::parse(&b).ok())
            .unwrap_or_default();
        // Music index (zone track id → filename). Non-fatal if absent.
        let music = std::fs::read(data_root.join("Game Data/Music.dat"))
            .ok()
            .and_then(|b| MusicCatalog::parse(&b).ok())
            .map(|p| p.value)
            .unwrap_or_default();
        // Sound index (sound id → filename, for P_Sound/P_Speech). Non-fatal.
        let sounds = std::fs::read(data_root.join("Game Data/Sounds.dat"))
            .ok()
            .and_then(|b| SoundCatalog::parse(&b).ok())
            .map(|p| p.value)
            .unwrap_or_default();
        // Item definitions (id → name, for the inventory panel). Non-fatal.
        let items = std::fs::read(data_root.join("Server Data/Items.dat"))
            .map(|b| ItemCatalog::parse(&b))
            .unwrap_or_default();
        // In-game HUD layout (fractional positions matching Client.exe).
        let interface = std::fs::read(data_root.join("Game Data/Interface.dat"))
            .ok()
            .and_then(|b| InterfaceLayout::parse(&b).ok());
        // Currency denominations (HUD-3). Falls back to the stock config so the
        // money readout always renders even if Money.dat is missing.
        let money = std::fs::read(data_root.join("Game Data/Money.dat"))
            .ok()
            .and_then(|b| MoneyConfig::parse(&b).ok())
            .unwrap_or_default();
        // Attribute slot names (Health/Mana/Strength/…) for the character panel.
        let attribute_names = std::fs::read(data_root.join("Server Data/Attributes.dat"))
            .ok()
            .and_then(|b| rcce_data::AttributeNames::parse(&b).ok());
        // Which attribute slot is Health/Energy/etc. (project-configurable).
        let fixed_attributes = std::fs::read(data_root.join("Game Data/Fixed Attributes.dat"))
            .ok()
            .and_then(|b| rcce_data::FixedAttributes::parse(&b).ok());
        // Project weapon damage-type names (Piercing/Fire/…), shown in the weapon
        // tooltip. Absent/unreadable → empty table → no "(type)" suffix.
        let damage_types = std::fs::read(data_root.join("Server Data/Damage.dat"))
            .map(|b| rcce_data::DamageTypes::parse(&b))
            .unwrap_or_default();
        // Project client-display options (nametag visibility, view mode, bubbles).
        // Absent/short → all-zero defaults (Blitz ReadByte-past-EOF semantics).
        let other = std::fs::read(data_root.join("Game Data/Other.dat"))
            .map(|b| rcce_data::OtherConfig::parse(&b))
            .unwrap_or_default();
        // Project sun/moon directional light colours (warm day / cool night).
        let suns = std::fs::read(data_root.join("Game Data/Suns.dat"))
            .ok()
            .and_then(|b| rcce_data::Suns::parse(&b).ok());
        // Localizable client strings (Language.txt is UTF-8/ASCII text, not a
        // .dat). Absent / unreadable → empty table → hardcoded-English fallback.
        let language = std::fs::read_to_string(data_root.join("Game Data/Language.txt"))
            .map(|t| rcce_data::Language::parse(&t))
            .unwrap_or_default();
        Ok(Self {
            data_root,
            actors,
            meshes,
            textures,
            anims,
            music,
            sounds,
            items,
            interface,
            money,
            attribute_names,
            fixed_attributes,
            damage_types,
            other,
            suns,
            language,
            cache: HashMap::new(),
            actor_tex_cache: HashMap::new(),
        })
    }

    /// Which attribute slot is Health for this project (from
    /// `Fixed Attributes.dat`). Falls back to `0` when the file is absent or
    /// Health is unassigned, matching the prior hardcoded behaviour and the
    /// shipped default project (Health = slot 0).
    pub fn health_stat(&self) -> u8 {
        self.fixed_attributes.and_then(|f| f.health).unwrap_or(0)
    }

    /// Whether the project hides actor nametags (the floating name/tag text) —
    /// `Other.dat` HideNametags == 1 (Actors3D.bb:508). Health bars are unaffected.
    pub fn nametags_hidden(&self) -> bool {
        self.other.nametags_hidden()
    }

    /// Whether the camera starts in first-person (`Other.dat` ViewMode == 1).
    pub fn default_first_person(&self) -> bool {
        self.other.default_first_person()
    }

    /// Whether the first/third-person toggle key is allowed (`Other.dat` ViewMode
    /// not locked to 1/3). See [`rcce_data::OtherConfig::view_toggle_allowed`].
    pub fn view_toggle_allowed(&self) -> bool {
        self.other.view_toggle_allowed()
    }

    /// Whether player-chat speech bubbles are shown (`Other.dat` UseBubbles > 1).
    pub fn bubbles_enabled(&self) -> bool {
        self.other.bubbles_enabled()
    }

    /// The project's chat-bubble text colour (`Other.dat` BubblesR/G/B; readable
    /// warm-white fallback when unset). See [`rcce_data::OtherConfig::bubble_color`].
    pub fn bubble_color(&self) -> [f32; 4] {
        self.other.bubble_color()
    }

    /// The project's name for a weapon damage-type index (`Damage.dat`), e.g.
    /// `3 -> "Fire"`. `None` when the table is absent or the slot is unnamed/out
    /// of range — the tooltip then omits the "(type)" suffix.
    pub fn damage_type_name(&self, idx: i16) -> Option<&str> {
        self.damage_types.name(idx)
    }

    /// The project's localizable string table (`Game Data/Language.txt`), for
    /// threading into `World` at enter-world so chat strings honor the project's
    /// translation. Empty when the file is absent → callers fall back to
    /// hardcoded English. Cloned (a few KB of small strings, once per login).
    pub fn language(&self) -> rcce_data::Language {
        self.language.clone()
    }

    /// The project's active directional sun colour at `minutes` (game-minutes
    /// since midnight), normalised `0..1`, or `None` when no sun is visible /
    /// `Suns.dat` is absent (caller falls back to neutral white). Season 0 (the
    /// renderer doesn't yet track seasons). The shipped project gives a warm amber
    /// by day and a cool blue at night.
    pub fn sun_light(&self, minutes: u16) -> Option<[f32; 3]> {
        self.suns.as_ref().and_then(|s| s.light_at(minutes, 0))
    }

    /// Display name for attribute slot `i` (Health, Mana, Strength, …), or
    /// `None` if unnamed / hidden / out of range.
    pub fn attribute_name(&self, i: usize) -> Option<&str> {
        let a = self.attribute_names.as_ref()?;
        if a.hidden(i) {
            return None;
        }
        a.name(i)
    }

    /// Display name for an item id (`#<id>` if unknown).
    pub fn item_name(&self, id: u16) -> String {
        self.items.name_or_id(id)
    }

    /// Full item record (for tooltip stats: mass, weapon damage, armour level).
    pub fn item_def(&self, id: u16) -> Option<&rcce_data::ItemDef> {
        self.items.get(id)
    }

    /// On-disk path for a texture-catalog id under `data/Textures/` (the same
    /// resolution actor skins use), if the file exists. Public so the HUD can
    /// draw item / spell thumbnail icons.
    pub fn texture_path(&self, tex_id: u16) -> Option<PathBuf> {
        self.skin_path(tex_id)
    }

    /// On-disk path to an item's inventory thumbnail icon (its `ThumbnailTexID`
    /// resolved through the texture catalog), if the item and the texture file
    /// exist. Used to draw real per-item icons in inventory slots.
    pub fn item_icon_path(&self, item_id: u16) -> Option<PathBuf> {
        let tex = self.items.get(item_id)?.thumbnail_tex_id;
        if tex < 0 {
            return None;
        }
        self.skin_path(tex as u16)
    }

    /// First catalogued item's `thumbnail_tex_id` that resolves to a real
    /// texture on disk — a known-renderable id for the image-window self-test.
    pub fn first_item_thumbnail(&self) -> Option<u16> {
        self.items.items.iter().find_map(|i| {
            let t = i.thumbnail_tex_id;
            (t >= 0 && self.texture_path(t as u16).is_some()).then_some(t as u16)
        })
    }

    /// The in-game HUD layout from Interface.dat (fractional positions), if
    /// present — used to place the HUD exactly where Client.exe does.
    pub fn interface(&self) -> Option<&InterfaceLayout> {
        self.interface.as_ref()
    }

    /// Currency denominations (Money.dat) for formatting a base-unit amount as
    /// `"Platinum 1, Gold 23, …"` — always present (stock fallback). HUD-3.
    pub fn money(&self) -> &MoneyConfig {
        &self.money
    }

    /// Base value (gold) for an item id, 0 if unknown.
    pub fn item_value(&self, id: u16) -> i32 {
        self.items.get(id).map(|i| i.value).unwrap_or(0)
    }

    /// Equipment slot index an item equips into, or `None` if not wearable.
    pub fn item_equip_slot(&self, id: u16) -> Option<u8> {
        self.items.equip_slot(id)
    }

    /// Path to a sound file under `Data/Sounds/<rel>` if it exists (e.g.
    /// `Weather/Rain.ogg`).
    pub fn sound_path(&self, rel: &str) -> Option<PathBuf> {
        let p = self.data_root.join("Sounds").join(rel);
        p.exists().then_some(p)
    }

    /// Footstep `.ogg` files under `Data/Sounds/Footsteps/`, sorted. Empty if
    /// the folder is absent.
    pub fn footstep_sounds(&self) -> Vec<PathBuf> {
        let dir = self.data_root.join("Sounds").join("Footsteps");
        let mut v: Vec<PathBuf> = std::fs::read_dir(dir)
            .into_iter()
            .flatten()
            .flatten()
            .map(|e| e.path())
            .filter(|p| p.extension().map(|x| x.eq_ignore_ascii_case("ogg")).unwrap_or(false))
            .collect();
        v.sort();
        v
    }

    /// Number of loaded item definitions (for diagnostics).
    pub fn item_count(&self) -> usize {
        self.items.items.len()
    }

    /// Resolve a `LoadingMusicID` to an on-disk `.ogg` path under `Data/Music/`,
    /// or `None` if the id is empty/unknown or the file is missing. Backslashes
    /// in the stored filename are normalised to the platform separator.
    pub fn music_path(&self, id: u16) -> Option<std::path::PathBuf> {
        let entry = self.music.get(id)?;
        let rel = entry.filename.replace('\\', "/");
        let path = self.data_root.join("Music").join(rel);
        path.exists().then_some(path)
    }

    /// Resolve a `P_Sound`/`P_Speech` sound id to an on-disk path under
    /// `Data/Sounds/`, stripping the trailing `chr(1)` 3D-marker byte from the
    /// stored name first. `None` if the id is unknown or the file is missing.
    pub fn sound_path_by_id(&self, id: u16) -> Option<std::path::PathBuf> {
        let entry = self.sounds.get(id)?;
        let rel = entry.clean_name().replace('\\', "/");
        let path = self.data_root.join("Sounds").join(rel);
        path.exists().then_some(path)
    }

    /// Whether this sound id carries the engine's trailing `chr(1)` marker for
    /// actor-attached playback. Unknown entries deliberately remain 2D/silent.
    pub fn sound_is_positional(&self, id: u16) -> bool {
        self.sounds.get(id).is_some_and(|entry| entry.is_3d())
    }

    /// Path to the looping menu track `Data/Music/Menu.ogg` (MENU-10), or `None`
    /// if the starter project doesn't ship it. ref `MainMenu.bb:99-103`.
    pub fn menu_music_path(&self) -> Option<std::path::PathBuf> {
        let p = self.data_root.join("Music").join("Menu.ogg");
        p.exists().then_some(p)
    }

    /// `DamageInfoStyle` from `Data/Game Data/Combat.dat` (CBT-5): byte 2, after
    /// the 2-byte `CombatDelay`. 3 = floating numbers (default), 2 = chat lines.
    /// Falls back to 3 when the file is absent/too short. ref `ClientCombat.bb:84-88`.
    pub fn damage_info_style(&self) -> u8 {
        std::fs::read(self.data_root.join("Game Data").join("Combat.dat"))
            .ok()
            .and_then(|b| b.get(2).copied())
            .unwrap_or(3)
    }

    /// The remembered account name from `Data/Last Username.dat` (MENU-12): the
    /// first line of the file (line 2 is an obfuscated password we don't restore).
    /// `None` if absent/empty. ref `MainMenu.bb:728-733`.
    pub fn last_username(&self) -> Option<String> {
        let raw = std::fs::read_to_string(self.data_root.join("Last Username.dat")).ok()?;
        parse_last_username(&raw)
    }

    /// Persist the account `name` to `Data/Last Username.dat` (MENU-12), preserving
    /// the existing obfuscated-password line 2 when present so the Blitz client's
    /// own pre-fill isn't disrupted (the Rust client doesn't reimplement `Encrypt$`,
    /// so it never restores the password). Best-effort; errors are ignored.
    pub fn save_last_username(&self, name: &str) {
        let path = self.data_root.join("Last Username.dat");
        let pw_line = std::fs::read_to_string(&path)
            .ok()
            .and_then(|s| s.lines().nth(1).map(str::to_string))
            .unwrap_or_default();
        let _ = std::fs::write(&path, format!("{name}\n{pw_line}\n"));
    }

    /// Path to a full-screen menu backdrop image under `Data/Textures/Menu/`
    /// (MENU-SCENE-b), e.g. `EULA.PNG` / `Login.PNG` / `Menu.PNG`. `None` if the
    /// project doesn't ship it. Tries the given name then a `.PNG`/`.png` swap so
    /// callers don't have to match the on-disk case.
    pub fn menu_backdrop_path(&self, name: &str) -> Option<PathBuf> {
        let dir = self.data_root.join("Textures").join("Menu");
        let direct = dir.join(name);
        if direct.exists() {
            return Some(direct);
        }
        let stem = name.rsplit_once('.').map(|(s, _)| s).unwrap_or(name);
        for ext in ["PNG", "png"] {
            let p = dir.join(format!("{stem}.{ext}"));
            if p.exists() {
                return Some(p);
            }
        }
        None
    }

    /// Path to the game logo sprite `Data/Textures/Menu Logo.bmp` (MENU-1), shown
    /// above the login window. `None` if absent. ref `MainMenu.bb:441`.
    pub fn menu_logo_path(&self) -> Option<PathBuf> {
        let p = self.data_root.join("Textures").join("Menu Logo.bmp");
        p.exists().then_some(p)
    }

    /// The optional EULA / license text from `Data/Game Data/EULA.txt` (MENU-13).
    /// `None` when the file is absent or contains only whitespace — in which case
    /// the gate is skipped, exactly like the engine. ref `MainMenu.bb:2908-2911`.
    pub fn eula_text(&self) -> Option<String> {
        let p = self.data_root.join("Game Data").join("EULA.txt");
        let raw = std::fs::read_to_string(p).ok()?;
        let trimmed = raw.trim();
        (!trimmed.is_empty()).then(|| trimmed.to_string())
    }

    /// First `Music.dat` entry that resolves to a file on disk, as `(id, path)`.
    /// Used to exercise the music pipeline when no zone sets `LoadingMusicID`.
    pub fn any_music(&self) -> Option<(u16, std::path::PathBuf)> {
        self.music
            .entries
            .iter()
            .find_map(|e| self.music_path(e.id).map(|p| (e.id, p)))
    }

    /// Memoised [`actor_textures`](Self::actor_textures) — decodes the skins for
    /// a (template, gender, face, body) once and returns a shared handle.
    pub fn actor_textures_rc(
        &mut self,
        template_id: u16,
        gender: u8,
        face_sel: u8,
        body_sel: u8,
    ) -> Rc<Vec<Option<Image>>> {
        let key = format!("{template_id}:{gender}:{face_sel}:{body_sel}");
        if let Some(r) = self.actor_tex_cache.get(&key) {
            return r.clone();
        }
        let v = Rc::new(self.actor_textures(template_id, gender, face_sel, body_sel));
        self.actor_tex_cache.insert(key, v.clone());
        v
    }

    /// The animation clip for an actor's named state ("Idle", "Walk", "Run",
    /// "Default attack", "Death 1", …), resolved through the actor's animation
    /// set (per gender). Tries the names in order; `None` if unmatched.
    pub fn actor_clip(&self, template_id: u16, gender: u8, names: &[&str]) -> Option<&AnimClip> {
        let t = self.actors.templates.get(&template_id)?;
        let set_id = if gender == 1 { t.f_anim_set } else { t.m_anim_set };
        let set = self.anims.get(set_id)?;
        // Exact (case-insensitive) match wins over a fuzzy substring — so
        // "Idle" picks "Idle", not "Sit idle"; "Run" picks "Run", not "Ride run".
        for n in names {
            if let Some(c) = set.clip(n).filter(|c| c.end >= c.start) {
                return Some(c);
            }
        }
        set.find(names).filter(|c| c.end >= c.start)
    }

    /// Template gender-mode (`Actors.dat` `Genders`) for every actor template,
    /// keyed by id. The packet decoder needs this to know whether a P_NewActor
    /// carries a gender byte (only when mode == 0).
    pub fn template_genders(&self) -> HashMap<u16, u8> {
        self.actors
            .templates
            .iter()
            .map(|(&id, t)| (id, t.genders))
            .collect()
    }

    /// Resolve a texture-catalog id to an on-disk path under `data/Textures/`.
    fn skin_path(&self, id: u16) -> Option<PathBuf> {
        if id == 65535 {
            return None;
        }
        let entry = self.textures.get(id)?;
        let p = self
            .data_root
            .join("Textures")
            .join(entry.filename.replace('\\', "/"));
        p.exists().then_some(p)
    }

    /// The base body model for an actor template + gender (0 male / 1 female).
    pub fn actor_model(&mut self, template_id: u16, gender: u8) -> Option<Rc<B3dModel>> {
        let mesh_id = self.actors.mesh_for(template_id, gender)?;
        self.mesh_model(mesh_id)
    }

    /// The voice sound id for an actor `template_id`+`gender` at `Speech_*` `slot`,
    /// or `None` when unknown/unset. Pair with `sound_path_by_id` to resolve a
    /// playable file (which returns `None` for a project that ships no such voice
    /// sound — so combat voices are silent-safe on the default data).
    pub fn actor_speech_id(&self, template_id: u16, gender: u8, slot: u8) -> Option<u16> {
        self.actors.speech_id(template_id, gender, slot as usize)
    }

    /// Playable races offered in character create: `(template_id, race_name)`
    /// for every `playable` template that has a usable body mesh, sorted by id.
    pub fn playable_templates(&self) -> Vec<(u16, String)> {
        let mut out: Vec<(u16, String)> = self
            .actors
            .templates
            .values()
            .filter(|t| t.playable && (t.mesh_ids[0] != 65535 || t.mesh_ids[1] != 65535))
            .map(|t| (t.id, t.race.clone()))
            .collect();
        out.sort_by_key(|&(id, _)| id);
        out
    }

    /// Playable create-templates as `(id, race, class)` for every `playable`
    /// template with a usable body mesh, sorted by id. The create screen's Race
    /// picker steps between distinct `race` values and the Class picker cycles
    /// the classes within a race (Blitz `MainMenu.bb:2173-2187` race combobox +
    /// `:2565` class buttons, both filtered on `A\Playable`).
    pub fn playable_create_templates(&self) -> Vec<(u16, String, String)> {
        let mut out: Vec<(u16, String, String)> = self
            .actors
            .templates
            .values()
            .filter(|t| t.playable && (t.mesh_ids[0] != 65535 || t.mesh_ids[1] != 65535))
            .map(|t| (t.id, t.race.clone(), t.class.clone()))
            .collect();
        out.sort_by_key(|&(id, _, _)| id);
        out
    }

    /// The project's attribute-assignment pool (`Attributes.dat` byte 0,
    /// Actors.bb:1077). `0` = no point-spend step in character create. The
    /// create screen shows the `+/-` UI and sends the 40 point-spend bytes only
    /// when this is `> 0` (MainMenu.bb:2257/2392).
    pub fn attribute_assignment(&self) -> u8 {
        self.attribute_names.as_ref().map(|a| a.assignment).unwrap_or(0)
    }

    /// The slot indices (0..39) that are *assignable* in character create:
    /// named, not a skill, not hidden (MainMenu.bb:2154). Returned in slot order
    /// so the create screen lists them the way Blitz iterates them.
    pub fn assignable_attribute_slots(&self) -> Vec<usize> {
        let Some(a) = self.attribute_names.as_ref() else { return Vec::new() };
        (0..rcce_data::AttributeNames::COUNT)
            .filter(|&i| {
                a.attrs
                    .get(i)
                    .is_some_and(|d| !d.name.is_empty() && !d.is_skill && !d.hidden)
            })
            .collect()
    }

    /// Base `(value, maximum)` for actor template `id`'s attribute slot, or
    /// `None` when the template or slot is unknown. The create screen displays
    /// `value` (+ any spent points) and clamps an increase below `maximum`.
    pub fn template_attribute(&self, id: u16, slot: usize) -> Option<(i16, i16)> {
        let t = self.actors.templates.get(&id)?;
        Some((*t.attr_value.get(slot)?, *t.attr_max.get(slot)?))
    }

    /// The actor's in-world render scale, matching the engine
    /// (`Actors3D.bb:45`): `0.05 × LoadedMeshScales[mesh] × Actor.Scale`.
    /// Positions stay in raw world units. Falls back to `0.05` if a stored
    /// scale is non-positive.
    /// Load a particle-emitter config by name from `Data/Emitter Configs/<name>.rpc`.
    /// Used by the zone-emitter load and by `P_CreateEmitter` (dynamic emitters).
    /// `None` if the file is missing or unparseable (caller soft-fails).
    pub fn emitter_config(&self, name: &str) -> Option<rcce_data::EmitterConfig> {
        let path = self.data_root.join("Emitter Configs").join(format!("{name}.rpc"));
        let bytes = std::fs::read(path).ok()?;
        rcce_data::EmitterConfig::parse(&bytes).ok()
    }

    /// The template's AI hostility (`Actors.dat` Aggressiveness): 0 passive,
    /// 1 defensive, 2 always-attacks, 3 non-combatant. Defaults to 0 (passive)
    /// for an unknown template. Drives the nameplate hostility colour.
    pub fn actor_aggressiveness(&self, template_id: u16) -> u8 {
        self.actors.templates.get(&template_id).map(|t| t.aggressiveness).unwrap_or(0)
    }

    /// The template's locomotion environment (`Actors.dat` Environment):
    /// `environment::{AMPHIBIOUS,SWIM,FLY,WALK}`. Defaults to AMPHIBIOUS (0, the
    /// player mode) for an unknown template. Drives MOVE-8 water-destination
    /// rejection (WALK only) and ANIM-4 swim anims (AMPHIBIOUS underwater).
    pub fn actor_environment(&self, template_id: u16) -> u8 {
        self.actors.environment_for(template_id)
    }

    /// The template's blood-spurt texture id (`Actors.dat` BloodTexID), or `None`
    /// when the race has no blood (id <= 0). When present, a connecting combat hit
    /// spawns a `Blood.rpc` emitter textured with this id (Blitz ClientNet.bb:1136).
    pub fn actor_blood_tex(&self, template_id: u16) -> Option<u16> {
        self.actors
            .templates
            .get(&template_id)
            .map(|t| t.blood_tex)
            .filter(|&b| b > 0)
            .map(|b| b as u16)
    }

    pub fn actor_render_scale(&self, template_id: u16, gender: u8) -> Option<f32> {
        let mesh_id = self.actors.mesh_for(template_id, gender)?;
        let mesh = self.meshes.get(mesh_id)?;
        let actor = self.actors.templates.get(&template_id)?;
        let ms = if mesh.scale > 0.0 { mesh.scale } else { 1.0 };
        let as_ = if actor.scale > 0.0 { actor.scale } else { 1.0 };
        Some(0.05 * ms * as_)
    }

    /// Textures for an actor's model, one per mesh (aligned to
    /// `actor_model(...).meshes`). Each mesh's B3D texture filename is resolved
    /// by basename against the mesh's own directory and the project texture
    /// trees, then decoded (BMP/PNG/JPG). `None` where unresolved/undecodable.
    pub fn actor_textures(
        &mut self,
        template_id: u16,
        gender: u8,
        face_sel: u8,
        body_sel: u8,
    ) -> Vec<Option<Image>> {
        let Some(mesh_id) = self.actors.mesh_for(template_id, gender) else {
            return Vec::new();
        };

        // Real skins from this actor's chosen body/face texture selection
        // (0..4). These replace the b3d's embedded UV-guide textures.
        let fi = (face_sel as usize).min(4);
        let bi = (body_sel as usize).min(4);
        let (face_skin, body_skin) = match self.actors.templates.get(&template_id) {
            Some(t) => {
                let (faces, bodies) = if gender == 1 {
                    (t.female_face_ids, t.female_body_ids)
                } else {
                    (t.male_face_ids, t.male_body_ids)
                };
                (self.skin_path(faces[fi]), self.skin_path(bodies[bi]))
            }
            None => (None, None),
        };

        // Fallback search roots for the b3d's own textures.
        let mut roots = Vec::new();
        if let Some(entry) = self.meshes.get(mesh_id) {
            let rel = entry.filename.replace('\\', "/");
            if let Some(dir) = self.data_root.join("Meshes").join(&rel).parent() {
                roots.push(dir.to_path_buf());
            }
        }
        roots.push(self.data_root.join("Textures"));
        roots.push(self.data_root.join("Meshes"));

        let Some(model) = self.mesh_model(mesh_id) else {
            return Vec::new();
        };
        model
            .meshes
            .iter()
            .map(|m| {
                // Surface type from the b3d texture name: head/face vs body.
                let is_face = m
                    .texture
                    .as_deref()
                    .map(texture::basename)
                    .map(|b| {
                        let l = b.to_ascii_lowercase();
                        l.contains("head") || l.contains("face")
                    })
                    .unwrap_or(false);
                let skin = if is_face { &face_skin } else { &body_skin };
                // Prefer the real actor skin; fall back to the b3d's texture.
                skin.as_ref()
                    .and_then(|p| texture::load(p))
                    .or_else(|| {
                        m.texture
                            .as_ref()
                            .and_then(|name| texture::find_texture(&roots, name))
                            .and_then(|p| texture::load_with_flags(&p, m.texture_flag))
                    })
            })
            .collect()
    }

    /// Textures for a plain scenery mesh, one per sub-mesh (aligned to
    /// `mesh_model(mesh_id).meshes`). Resolves each sub-mesh's own B3D texture
    /// name against the mesh directory and the project texture trees. If
    /// `retexture_id` is a real texture-catalog id (not 65535), it overrides
    /// every sub-mesh (the engine's scenery `TextureID` retexture).
    pub fn scenery_textures(&mut self, mesh_id: u16, retexture_id: u16) -> Vec<Option<Image>> {
        // Optional whole-mesh retexture from the area file's TextureID.
        let retex = self
            .skin_path(retexture_id)
            .and_then(|p| texture::load(&p));

        // Search roots: the mesh's own directory, then the texture trees.
        let mut roots = Vec::new();
        if let Some(entry) = self.meshes.get(mesh_id) {
            let rel = entry.filename.replace('\\', "/");
            if let Some(dir) = self.data_root.join("Meshes").join(&rel).parent() {
                roots.push(dir.to_path_buf());
            }
        }
        roots.push(self.data_root.join("Textures"));
        roots.push(self.data_root.join("Meshes"));

        let Some(model) = self.mesh_model(mesh_id) else {
            return Vec::new();
        };
        model
            .meshes
            .iter()
            .map(|m| {
                if let Some(img) = &retex {
                    let mut img = img.clone();
                    if m.texture_flag & 4 != 0 {
                        texture::mask_black(&mut img);
                    }
                    return Some(img);
                }
                m.texture
                    .as_ref()
                    .and_then(|name| texture::find_texture(&roots, name))
                    .and_then(|p| texture::load_with_flags(&p, m.texture_flag))
            })
            .collect()
    }

    /// Head attachments (hair, and beard for males) for an actor, resolved from
    /// the template's Hair/Beard selection (0..4). Empty when the slots are
    /// unset (65535) or the meshes don't resolve.
    pub fn actor_attachments(
        &mut self,
        template_id: u16,
        gender: u8,
        hair_sel: u8,
        beard_sel: u8,
    ) -> Vec<Attachment> {
        let Some(t) = self.actors.templates.get(&template_id).cloned() else {
            return Vec::new();
        };
        let mut out = Vec::new();
        let hair_ids = if gender == 1 {
            t.female_hair_ids
        } else {
            t.male_hair_ids
        };
        if let Some(a) = self.mesh_attachment(hair_ids[(hair_sel as usize).min(4)]) {
            out.push(a);
        }
        // Beards are male-only.
        if gender != 1 {
            if let Some(a) = self.mesh_attachment(t.beard_ids[(beard_sel as usize).min(4)]) {
                out.push(a);
            }
        }
        out
    }

    /// An [`Attachment`] for an item's equipped/world mesh (its `mmesh`) — e.g.
    /// a weapon to hang at the actor's `R_Hand` joint. `None` if the item or its
    /// mesh is missing.
    pub fn gear_attachment(&mut self, item_id: u16) -> Option<Attachment> {
        let mesh_id = self.items.get(item_id).map(|i| i.mmesh)?;
        self.mesh_attachment(mesh_id)
    }

    /// Attachment for a mesh-catalog id directly (bypassing the item table) —
    /// for tools verifying the gear-attach mechanism.
    pub fn gear_attachment_mesh(&mut self, mesh_id: u16) -> Option<Attachment> {
        self.mesh_attachment(mesh_id)
    }

    /// Build an [`Attachment`] for a mesh-catalog id (its model + textures +
    /// catalog offset/scale). `None` for the 65535 "none" slot or a miss.
    fn mesh_attachment(&mut self, mesh_id: u16) -> Option<Attachment> {
        if mesh_id == 65535 {
            return None;
        }
        let entry = self.meshes.get(mesh_id)?.clone();
        let model = self.mesh_model(mesh_id)?;
        let textures = self.scenery_textures(mesh_id, 65535);
        Some(Attachment {
            mesh_id,
            model,
            textures,
            offset: entry.offset,
            scale: if entry.scale > 0.0 { entry.scale } else { 1.0 },
        })
    }

    /// Catalog filename for a mesh id (diagnostics).
    pub fn mesh_filename(&self, mesh_id: u16) -> Option<&str> {
        self.meshes.get(mesh_id).map(|e| e.filename.as_str())
    }

    /// A model by mesh-catalog id, cached (including negative cache for misses).
    pub fn mesh_model(&mut self, mesh_id: u16) -> Option<Rc<B3dModel>> {
        if let Some(cached) = self.cache.get(&mesh_id) {
            return cached.clone();
        }
        let result = self
            .meshes
            .get(mesh_id)
            .and_then(|entry| {
                let path = self
                    .data_root
                    .join("Meshes")
                    .join(entry.filename.replace('\\', "/"));
                std::fs::read(path).ok()
            })
            .and_then(|bytes| B3dModel::parse(&bytes).ok())
            .map(Rc::new);
        self.cache.insert(mesh_id, result.clone());
        result
    }

    /// Load a standalone mesh by its path relative to the project `Meshes` dir
    /// (e.g. `"Character Set/Set.b3d"`), resolving each of its meshes' embedded
    /// textures the same way scenery does — searching the mesh's own directory
    /// first, then the shared `Textures` / `Meshes` trees. Returns the parsed
    /// model plus a per-mesh-indexed texture vector ready for a `SceneInstance`.
    ///
    /// Used for the menu backdrop (`Data\Meshes\Character Set\Set.b3d`), which is
    /// not in the numeric `Meshes.dat` catalog `mesh_model` indexes. Returns
    /// `None` if the file is missing or unparseable so the caller can fall back
    /// to the bare void.
    pub fn mesh_by_path(
        &self,
        rel: &str,
    ) -> Option<(Rc<B3dModel>, Vec<Option<Image>>, Vec<Option<Image>>)> {
        let rel = rel.replace('\\', "/");
        let path = self.data_root.join("Meshes").join(&rel);
        let bytes = std::fs::read(&path).ok()?;
        let model = Rc::new(B3dModel::parse(&bytes).ok()?);

        let mut roots = Vec::new();
        if let Some(dir) = path.parent() {
            roots.push(dir.to_path_buf());
        }
        roots.push(self.data_root.join("Textures"));
        roots.push(self.data_root.join("Meshes"));

        let textures: Vec<Option<Image>> = model
            .meshes
            .iter()
            .map(|m| {
                m.texture
                    .as_ref()
                    .and_then(|name| texture::find_texture(&roots, name))
                    .and_then(|p| texture::load_with_flags(&p, m.texture_flag))
            })
            .collect();
        // Baked lightmaps (the brushes' 2nd texture slot), if any. Loaded plain
        // (no texture_flag — lightmaps aren't masked) and never color-keyed.
        let lightmaps: Vec<Option<Image>> = model
            .meshes
            .iter()
            .map(|m| {
                m.lightmap
                    .as_ref()
                    .and_then(|name| texture::find_texture(&roots, name))
                    .and_then(|p| texture::load(&p))
            })
            .collect();
        Some((model, textures, lightmaps))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use rcce_data::AnimClip;

    #[test]
    fn last_username_parses_first_line() {
        // Real file shape: name line + obfuscated password line (CRLF).
        assert_eq!(parse_last_username("will\r\n\x16\x16KOSUN\r\n").as_deref(), Some("will"));
        assert_eq!(parse_last_username("  Corey \nsecret").as_deref(), Some("Corey"));
        assert_eq!(parse_last_username(""), None);
        assert_eq!(parse_last_username("\n\n"), None); // blank first line
    }

    #[test]
    fn clip_frame_loops_within_range() {
        let clip = AnimClip { name: "Walk".into(), start: 10, end: 20, speed: 1.0 };
        // t=0 -> start.
        assert!((clip_frame(&clip, 10.0, 0.0) - 10.0).abs() < 1e-4);
        // Always within [start, end] (inclusive-ish, len+1 wrap).
        for i in 0..200 {
            let f = clip_frame(&clip, 10.0, i as f32 * 0.05);
            assert!((10.0..21.0).contains(&f), "frame {f} out of [10,21)");
        }
        // Single-frame clip pins to start.
        let one = AnimClip { name: "Sit".into(), start: 142, end: 142, speed: 1.0 };
        assert_eq!(clip_frame(&one, 30.0, 5.0), 142.0);
    }

    fn att(offset: [f32; 3], scale: f32) -> Attachment {
        Attachment {
            mesh_id: 1,
            model: Rc::new(B3dModel::default()),
            textures: Vec::new(),
            offset,
            scale,
        }
    }

    #[test]
    fn attachment_placement_no_yaw() {
        // Head 100 up, no catalog offset, actor scale 0.05 -> head sits at
        // body_y + 5; attachment scale = actor_scale * catalog_scale.
        let (t, r, s) = attachment_placement([10.0, 0.0, 20.0], 0.0, 0.05, [0.0, 100.0, 0.0], &att([0.0, 0.0, 0.0], 2.0));
        assert!((t[0] - 10.0).abs() < 1e-4);
        assert!((t[1] - 5.0).abs() < 1e-4);
        assert!((t[2] - 20.0).abs() < 1e-4);
        assert_eq!(r, [0.0, 0.0, 0.0]);
        assert!((s[0] - 0.1).abs() < 1e-4);
    }

    #[test]
    fn attachment_placement_yaw_rotates_offset() {
        // A +Z head offset under a 90° yaw rotates to +X (glam from_rotation_y).
        use std::f32::consts::FRAC_PI_2;
        let (t, _r, _s) = attachment_placement([0.0, 0.0, 0.0], FRAC_PI_2, 1.0, [0.0, 0.0, 10.0], &att([0.0, 0.0, 0.0], 1.0));
        assert!((t[0] - 10.0).abs() < 1e-3, "x={}", t[0]);
        assert!(t[1].abs() < 1e-3);
        assert!(t[2].abs() < 1e-3, "z={}", t[2]);
    }
}
