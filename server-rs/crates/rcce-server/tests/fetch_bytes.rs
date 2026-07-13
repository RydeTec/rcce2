//! Byte-exact tests for the stock-Blitz-client onboarding packets
//! `P_FetchActors` (=7) + `P_FetchUpdateFiles` (=10), driven through the real
//! `ServerState::dispatch` against the **real shipped `data/`** files.
//!
//! Since a Windows Blitz client can't be run on macOS/CI, the accepted evidence
//! tier is: encode with the port's handler, then DECODE each sub-packet exactly
//! as `MainMenu.bb`'s client parser does (`:203` update-files, `:891`
//! actors) and assert the reconstructed catalog equals an independent parse of
//! the same `.dat` files. That proves the wire the port emits is the wire the
//! Blitz client reads — the same round-trip the live client would perform.

use rcce_server::config::ServerConfig;
use rcce_server::fetch::{P_FETCH_ACTORS, P_FETCH_UPDATE_FILES};
use rcce_server::state::ServerState;
use rcce_server_accounts::store::AccountStore;
use rcce_server_core::ActorCatalog;
use std::path::PathBuf;

fn data_dir() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../../data")
}

fn config_for(dir: PathBuf) -> ServerConfig {
    ServerConfig {
        port: 25000,
        allow_account_creation: true,
        max_account_chars: 4,
        start_gold: 5000,
        start_reputation: 50,
        attribute_assignment: 0,
        data_dir: dir,
    }
}

fn tmp_store(name: &str) -> AccountStore {
    let mut p = std::env::temp_dir();
    p.push(format!("rcce_fetch_test_{name}.dat"));
    let _ = std::fs::remove_file(&p);
    AccountStore::load(&p).unwrap()
}

fn real_state() -> ServerState {
    let dir = data_dir();
    let catalog = ActorCatalog::load(dir.join("Server Data/Actors.dat"));
    let store = tmp_store("state");
    ServerState::new(config_for(dir), store, catalog)
}

// ---- little decode helpers mirroring RCE_IntFromStr / Mid$ ----------------

fn u16le(b: &[u8], o: usize) -> u16 {
    u16::from_le_bytes([b[o], b[o + 1]])
}
fn i32le(b: &[u8], o: usize) -> i32 {
    i32::from_le_bytes([b[o], b[o + 1], b[o + 2], b[o + 3]])
}
fn f32le(b: &[u8], o: usize) -> f32 {
    f32::from_le_bytes([b[o], b[o + 1], b[o + 2], b[o + 3]])
}
/// 1-byte-len string; returns (string, next offset).
fn str8(b: &[u8], o: usize) -> (String, usize) {
    let n = b[o] as usize;
    (String::from_utf8_lossy(&b[o + 1..o + 1 + n]).into_owned(), o + 1 + n)
}
/// 2-byte-len string.
fn str16(b: &[u8], o: usize) -> (String, usize) {
    let n = u16le(b, o) as usize;
    (String::from_utf8_lossy(&b[o + 2..o + 2 + n]).into_owned(), o + 2 + n)
}

#[test]
fn fetch_update_files_empty_manifest_is_no_files_packet() {
    // The shipped Files.dat is empty → a single 2-byte packet the client reads
    // as "no files" (`MainMenu.bb:206`: `If Len(Pa$) = 2 Then … RequiredFiles = 0`).
    let mut state = real_state();
    let out = state.dispatch(1, P_FETCH_UPDATE_FILES, &[]);
    assert_eq!(out.len(), 1, "one packet for the empty manifest");
    assert_eq!(out[0].msg_type, P_FETCH_UPDATE_FILES);
    assert_eq!(out[0].payload, vec![0u8, 0u8], "just the 2-byte total = 0");
}

#[test]
fn fetch_update_files_ignores_malformed_request_body() {
    // The Blitz client sends an empty body; a hostile client could send junk.
    // The handler has no request fields, so junk must produce the same reply and
    // never panic (soft-fail).
    let mut state = real_state();
    let a = state.dispatch(2, P_FETCH_UPDATE_FILES, &[]);
    let b = state.dispatch(2, P_FETCH_UPDATE_FILES, b"\xff\xff\x00garbage");
    assert_eq!(
        a.iter().map(|o| o.payload.clone()).collect::<Vec<_>>(),
        b.iter().map(|o| o.payload.clone()).collect::<Vec<_>>(),
        "payload is independent of the request body"
    );
}

#[test]
fn fetch_actors_ignores_malformed_request_body() {
    let mut state = real_state();
    let a = state.dispatch(3, P_FETCH_ACTORS, &[]);
    let b = state.dispatch(3, P_FETCH_ACTORS, b"junk-request-\x00\xff");
    assert_eq!(a.len(), b.len(), "same packet count regardless of request body");
    for (x, y) in a.iter().zip(&b) {
        assert_eq!(x.payload, y.payload);
    }
}

/// Fully decode the `P_FetchActors` stream the way `MainMenu.bb` does and assert
/// the reconstructed catalog matches an independent parse of the real `.dat`
/// files — the byte-exact round-trip proof.
#[test]
fn fetch_actors_round_trips_real_data_through_the_client_parser() {
    let dir = data_dir();
    let mut state = real_state();
    let out = state.dispatch(4, P_FETCH_ACTORS, &[]);
    assert!(!out.is_empty());
    for o in &out {
        assert_eq!(o.msg_type, P_FETCH_ACTORS);
    }

    // Client-side accumulators (mirror the MainMenu.bb globals).
    let mut had_attributes = false;
    let mut had_damage = false;
    let mut had_environment = false;
    let mut factions_received = 0usize;
    let mut items_created = 0usize;
    let mut items_required: i64 = -1;
    let mut actors_created = 0usize;
    let mut actors_required: i64 = -1;

    let mut attr_assignment = 0u8;
    let mut attr_names: Vec<String> = Vec::new();
    let mut damage_names: Vec<String> = Vec::new();
    let mut env_clock = (0i32, 0i32, 0u8, 0u8, 0u8); // year, day, h, m, factor
    let mut season0 = (String::new(), 0i32, 0u8, 0u8);
    let mut month0 = (String::new(), 0i32);
    let mut faction0 = String::new();
    let mut item_ids: Vec<u16> = Vec::new();
    #[allow(clippy::type_complexity)]
    let mut item_by_id: std::collections::HashMap<u16, (u8, String, [u8; 6], [i32; 40])> =
        Default::default();
    let mut actor_ids: Vec<u16> = Vec::new();
    let mut actor_by_id: std::collections::HashMap<u16, (String, f32)> = Default::default();

    for (_t, pa) in out.iter().map(|o| (o.msg_type, &o.payload)) {
        match pa[0] {
            b'A' => {
                attr_assignment = pa[1];
                let mut o = 2;
                for _ in 0..40 {
                    let _is_skill = pa[o];
                    let _hidden = pa[o + 1];
                    let (name, no) = str8(pa, o + 2);
                    attr_names.push(name);
                    o = no;
                }
                had_attributes = true;
            }
            b'D' => {
                let mut o = 1;
                for _ in 0..20 {
                    let (name, no) = str8(pa, o);
                    damage_names.push(name);
                    o = no;
                }
                had_damage = true;
            }
            b'E' => {
                let year = i32le(pa, 1);
                let day = u16le(pa, 5) as i32;
                let (h, m, factor) = (pa[7], pa[8], pa[9]);
                env_clock = (year, day, h, m, factor);
                let mut o = 10;
                for i in 0..12 {
                    let (name, no) = str8(pa, o);
                    let start = u16le(pa, no) as i32;
                    let dusk = pa[no + 2];
                    let dawn = pa[no + 3];
                    if i == 0 {
                        season0 = (name, start, dusk, dawn);
                    }
                    o = no + 4;
                }
                for i in 0..20 {
                    let (name, no) = str8(pa, o);
                    let start = u16le(pa, no) as i32;
                    if i == 0 {
                        month0 = (name, start);
                    }
                    o = no + 2;
                }
                had_environment = true;
            }
            b'F' => {
                let mut o = 1;
                while o < pa.len() {
                    let nlen = pa[o] as usize;
                    let num = pa[o + 1];
                    let name = String::from_utf8_lossy(&pa[o + 2..o + 2 + nlen]).into_owned();
                    if num == 0 {
                        faction0 = name;
                    }
                    factions_received += 1;
                    o += 2 + nlen;
                }
            }
            b'I' => {
                // "IY" carries the 2-byte total (client `Offset = 5`, 1-based →
                // byte 4); "IN" does not (`Offset = 3`, 1-based → byte 2).
                let mut o = if pa[1] == b'Y' {
                    items_required = u16le(pa, 2) as i64;
                    4
                } else {
                    2
                };
                while o < pa.len() {
                    items_created += 1;
                    let id = u16le(pa, o);
                    let item_type = pa[o + 2];
                    o += 3; // id + type
                    o += 1; // takes_damage
                    o += 4; // value
                    o += 2; // mass
                    o += 2; // thumbnail
                    // Gubbins: 6 × 1 byte (Blitz sends the low byte only).
                    let mut gubbins = [0u8; 6];
                    for g in &mut gubbins {
                        *g = pa[o];
                        o += 1;
                    }
                    o += 2; // mmesh
                    o += 2; // fmesh
                    o += 2; // slot
                    o += 1; // stackable
                    // 40 attribute deltas, each wire-biased +5000 (client subtracts).
                    let mut attrs = [0i32; 40];
                    for a in &mut attrs {
                        *a = u16le(pa, o) as i32 - 5000;
                        o += 2;
                    }
                    let (name, no) = str8(pa, o);
                    o = no;
                    let (_er, no) = str8(pa, o);
                    o = no;
                    let (_ec, no) = str8(pa, o);
                    o = no;
                    match item_type {
                        1 => o += 2 + 2 + 2 + 4, // weapon: dmg,dtype,wtype,range
                        2 | 4 | 5 | 6 => o += 2,
                        _ => {}
                    }
                    let (_misc, no) = str8(pa, o);
                    o = no;
                    item_ids.push(id);
                    item_by_id.insert(id, (item_type, name, gubbins, attrs));
                }
            }
            b'N' | b'Y' => {
                let mut o = if pa[0] == b'Y' {
                    actors_required = u16le(pa, 1) as i64;
                    3
                } else {
                    1
                };
                while o < pa.len() {
                    actors_created += 1;
                    let id = u16le(pa, o);
                    o += 2; // id
                    o += 1; // playable
                    o += 1; // poly_collision
                    o += 8 * 2; // mesh
                    o += 5 * 2 * 7; // beard + 6 gendered appearance arrays (5 each)
                    o += 16 * 2 * 2; // m+f speech (16 each)
                    o += 1; // rideable
                    o += 1; // trade_mode
                    o += 2; // blood_tex
                    o += 1; // aggressiveness
                    o += 1; // genders
                    o += 1; // environment
                    o += 2; // inventory_slots
                    o += 2; // m_anim
                    o += 2; // f_anim
                    let scale = f32le(pa, o);
                    o += 4;
                    let _default_faction = pa[o];
                    o += 1;
                    o += 40 * 4; // 40 × (value, maximum)
                    let (race, no) = str8(pa, o);
                    o = no;
                    let (_class, no) = str8(pa, o);
                    o = no;
                    let (_desc, no) = str16(pa, o);
                    o = no;
                    actor_ids.push(id);
                    actor_by_id.insert(id, (race, scale));
                }
            }
            other => panic!("unexpected sub-packet tag {other:?}"),
        }
    }

    // The client's continue-gate (`MainMenu.bb:1130`): everything present + counts match.
    assert!(had_attributes && had_damage && had_environment);
    assert_eq!(factions_received, 100, "all 100 faction slots streamed");
    assert_eq!(items_created as i64, items_required, "item count matches terminator");
    assert_eq!(actors_created as i64, actors_required, "actor count matches terminator");

    // Cross-check against independent parses of the same files.
    let attrs = rcce_data::attributes::AttributeNames::parse(
        &std::fs::read(dir.join("Server Data/Attributes.dat")).unwrap(),
    )
    .unwrap();
    assert_eq!(attr_assignment, attrs.assignment);
    assert_eq!(&attr_names[0], "Health");
    assert_eq!(attr_names.len(), 40);
    for (i, ad) in attrs.attrs.iter().enumerate() {
        assert_eq!(&attr_names[i], &ad.name, "attr name {i}");
    }

    let dmg = rcce_data::damage::DamageTypes::parse(
        &std::fs::read(dir.join("Server Data/Damage.dat")).unwrap(),
    );
    assert_eq!(damage_names.len(), 20);
    assert_eq!(&damage_names[0], "Piercing");
    for (i, n) in dmg.names.iter().enumerate() {
        assert_eq!(&damage_names[i], n, "damage name {i}");
    }

    // No Environment.dat shipped → CreateEnvironment defaults (year 1, day 1,
    // noon, factor 10; season/month slot-0 start-day overwritten to 336).
    assert_eq!(env_clock, (1, 1, 12, 0, 10));
    assert_eq!(season0, ("Season 1".into(), 336, 18, 6));
    assert_eq!(month0, ("Month 1".into(), 336));

    assert_eq!(faction0, "Traders");

    let items = rcce_data::items::ItemCatalog::parse(
        &std::fs::read(dir.join("Server Data/Items.dat")).unwrap(),
    );
    assert_eq!(item_ids.len(), items.items.len(), "all items streamed");
    for it in &items.items {
        let (ty, name, gubbins, attrs) = item_by_id.get(&it.id).expect("item id present on wire");
        assert_eq!(*ty, it.item_type, "item {} type", it.id);
        assert_eq!(name, &it.name, "item {} name", it.id);
        // Gubbins low-byte truncation round-trips.
        for (j, g) in it.gubbins.iter().enumerate() {
            assert_eq!(gubbins[j], *g as u8, "item {} gubbin {j} low byte", it.id);
        }
        // The +5000 attribute bias is applied in the right direction: decoding
        // it back (u16 - 5000) recovers the source ItemDef attribute exactly.
        for (j, v) in it.attributes.iter().enumerate() {
            assert_eq!(attrs[j], *v as i32, "item {} attr {j} (bias direction)", it.id);
        }
    }

    let catalog = ActorCatalog::load(dir.join("Server Data/Actors.dat"));
    assert_eq!(actor_ids.len(), catalog.templates.len());
    assert!(actor_ids.len() >= 6, "shipped Actors.dat has 6 templates");
    for t in catalog.templates.values() {
        let (race, scale) = actor_by_id.get(&t.id).expect("actor id present on wire");
        assert_eq!(race, &t.race, "actor {} race", t.id);
        assert_eq!(*scale, t.scale, "actor {} scale", t.id);
    }
}

/// The item + actor streams must fragment exactly as Blitz does: an "IN" block
/// per 6 items (except the last) then a final "IY"; an "N" block per 2 actors
/// (except the last) then a final "Y". 11 items → 1×IN + 1×IY; 6 actors →
/// 2×N + 1×Y.
#[test]
fn fetch_actors_fragments_like_the_blitz_sender() {
    let mut state = real_state();
    let out = state.dispatch(5, P_FETCH_ACTORS, &[]);

    let tag = |p: &[u8]| -> String {
        if p[0] == b'I' {
            format!("I{}", p[1] as char)
        } else {
            (p[0] as char).to_string()
        }
    };
    let tags: Vec<String> = out.iter().map(|o| tag(&o.payload)).collect();

    // Fixed leading blocks: A, D, E.
    assert_eq!(&tags[0..3], &["A", "D", "E"]);

    let count = |t: &str| tags.iter().filter(|x| x.as_str() == t).count();
    // 11 items, 6 per IN block (except last) → one "IN" (items 1-6) + final "IY".
    assert_eq!(count("IN"), 1, "one intermediate item block for 11 items");
    assert_eq!(count("IY"), 1, "one terminating item block");
    // 6 actors, 2 per N block (except last) → two "N" + final "Y".
    assert_eq!(count("N"), 2, "two intermediate actor blocks for 6 actors");
    assert_eq!(count("Y"), 1, "one terminating actor block");
    // All 100 factions fit under 800 bytes (3 short names) → a single "F" block.
    assert!(count("F") >= 1);
}
