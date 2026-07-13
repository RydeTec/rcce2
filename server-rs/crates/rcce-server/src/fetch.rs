//! `P_FetchActors` (=7) + `P_FetchUpdateFiles` (=10) — the two stock-Blitz-client
//! onboarding packets (`ServerNet.bb:2257` / `:2242`).
//!
//! The Rust client loads its catalogs locally and never sends these, so the
//! Rust↔Rust path never needed them. A **stock, unmodified Blitz `Client.exe`**,
//! however, requests them at connect (`MainMenu.bb:187` update-files, `:879`
//! actors) and stalls at character-select until it gets byte-shaped replies.
//! Implementing them is what makes the Rust server a true drop-in for
//! `bin/Server.exe`.
//!
//! Both are pure functions of loaded server data (no request-payload fields to
//! parse — the Blitz client sends an empty body), so there's nothing to
//! validate; a malformed/empty request simply produces the standard reply. The
//! replies are streamed as multiple sub-packets sharing the one packet type,
//! each tagged by a leading letter the client dispatches on. All integers are
//! little-endian (the `RCE_StrFromInt$` convention, `RCEnet.bb:98`); wire
//! strings are 1-byte-length-prefixed except the actor `Description` (2-byte)
//! and the update-file/actor/item COUNT terminators (2-byte).
//!
//! Fragmentation thresholds + terminators are copied verbatim from the Blitz
//! sender so a Blitz client's accumulate-until-count parser terminates
//! identically:
//! * update-files: flush the running buffer when adding the next record would
//!   reach 950 bytes; the final packet carries `+TotalFiles(2)`.
//! * items: flush an `"IN"` block every 6 items (except the last); the final
//!   block is `"IY"+TotalItems(2)+…`.
//! * factions: flush an `"F"` block whenever the buffer reaches 800 bytes.
//! * actors: flush an `"N"` block every 2 actors (except the last); the final
//!   block is `"Y"+TotalActors(2)+…`.

use rcce_data::items::ItemDef;
use rcce_server_core::actor_catalog::ActorTemplate;
use rcce_server_core::environment::{Month, Season};
use rcce_server_core::update_files::UpdateFile;

use crate::state::ServerState;

/// `P_FetchActors` (`Packets.bb:8`).
pub const P_FETCH_ACTORS: u8 = 7;
/// `P_FetchUpdateFiles` (`Packets.bb:11`).
pub const P_FETCH_UPDATE_FILES: u8 = 10;

/// Blitz item-type constants (`Items.bb:1-7`) — select the record tail.
const I_WEAPON: u8 = 1;
const I_ARMOUR: u8 = 2;
const I_POTION: u8 = 4;
const I_INGREDIENT: u8 = 5;
const I_IMAGE: u8 = 6;

/// The `+5000` bias the wire applies to equipped-item attribute deltas
/// (`ServerNet.bb:2300`, mirrored on the client at `MainMenu.bb`
/// `It\Attributes\Value[j] = … - 5000`).
const ATTR_BIAS: i32 = 5000;

/// A 1-byte-length-prefixed wire string (`RCE_StrFromInt$(Len(s),1)+s`). Blitz
/// writes `Len` into a single byte; content strings are always short, but we
/// clamp defensively so a pathological name can't corrupt the length byte.
fn push_str8(buf: &mut Vec<u8>, s: &str) {
    let b = s.as_bytes();
    let n = b.len().min(255);
    buf.push(n as u8);
    buf.extend_from_slice(&b[..n]);
}

/// A 2-byte-length-prefixed wire string (`RCE_StrFromInt$(Len(s),2)+s`) — the
/// actor `Description` field.
fn push_str16(buf: &mut Vec<u8>, s: &str) {
    let b = s.as_bytes();
    let n = b.len().min(u16::MAX as usize);
    buf.extend_from_slice(&(n as u16).to_le_bytes());
    buf.extend_from_slice(&b[..n]);
}

fn push_i16(buf: &mut Vec<u8>, v: i16) {
    buf.extend_from_slice(&v.to_le_bytes());
}
fn push_u16(buf: &mut Vec<u8>, v: u16) {
    buf.extend_from_slice(&v.to_le_bytes());
}

impl ServerState {
    /// Build the `P_FetchUpdateFiles` reply (`ServerNet.bb:2242`): one record
    /// per manifest entry (`checksum i32 · nameLen u8 · name`), flushed into
    /// ≤950-byte packets, with the final packet carrying the 2-byte total. An
    /// empty manifest (the shipped `Files.dat`) yields a single 2-byte packet
    /// (`Len == 2` → the client's "no files" branch, `MainMenu.bb:206`).
    pub fn handle_fetch_update_files(&self) -> Vec<(u8, Vec<u8>)> {
        build_fetch_update_files(&self.update_files)
    }

    /// Build the `P_FetchActors` reply (`ServerNet.bb:2257`): the attributes,
    /// damage-types, environment, item, faction, and actor blocks a stock Blitz
    /// client needs before it can render character-select.
    pub fn handle_fetch_actors(&self) -> Vec<(u8, Vec<u8>)> {
        // The E block uses the LIVE game clock (Blitz sends the current globals,
        // which advance via UpdateEnvironment), and the static season/month
        // tables from Environment.dat. game_time = (hour, minute, day, year).
        let (h, m, day, year) = self.game_time;
        let clock = ClockWire { year, day, time_h: h, time_m: m, time_factor: self.time_factor };

        // Actors sorted by id for deterministic output (Blitz iterates list
        // order; the client indexes by id so order is immaterial to it, but a
        // stable order makes the wire reproducible + testable).
        let mut actors: Vec<&ActorTemplate> = self.catalog.templates.values().collect();
        actors.sort_by_key(|t| t.id);

        build_fetch_actors(
            self.attr_names.assignment,
            &self.attr_names.attrs,
            &self.damage_types.names,
            clock,
            &self.environment.seasons,
            &self.environment.months,
            &self.items.items,
            &self.factions.names,
            &actors,
        )
    }
}

/// The five game-clock scalars the `"E"` block sends.
#[derive(Clone, Copy)]
struct ClockWire {
    year: i32,
    day: i32,
    time_h: i32,
    time_m: i32,
    time_factor: i32,
}

pub(crate) fn build_fetch_update_files(files: &[UpdateFile]) -> Vec<(u8, Vec<u8>)> {
    let mut out = Vec::new();
    let mut pa: Vec<u8> = Vec::new();
    let total = files.len().min(u16::MAX as usize) as u16;
    for f in files {
        let mut add: Vec<u8> = Vec::new();
        add.extend_from_slice(&f.checksum.to_le_bytes());
        push_str8(&mut add, &f.name);
        // `If Len(Pa$ + Add$) >= 950` → flush the buffer WITHOUT this record,
        // then start the new buffer with it (`ServerNet.bb:2247`).
        if pa.len() + add.len() >= 950 {
            out.push((P_FETCH_UPDATE_FILES, std::mem::take(&mut pa)));
        }
        pa.extend_from_slice(&add);
    }
    // Final packet: buffer + 2-byte total (`ServerNet.bb:2254`).
    pa.extend_from_slice(&total.to_le_bytes());
    out.push((P_FETCH_UPDATE_FILES, pa));
    out
}

#[allow(clippy::too_many_arguments)]
fn build_fetch_actors(
    attr_assignment: u8,
    attrs: &[rcce_data::attributes::AttributeDef],
    damage_names: &[String],
    clock: ClockWire,
    seasons: &[Season],
    months: &[Month],
    items: &[ItemDef],
    faction_names: &[String],
    actors: &[&ActorTemplate],
) -> Vec<(u8, Vec<u8>)> {
    let mut out: Vec<(u8, Vec<u8>)> = Vec::new();
    let empty_attr = rcce_data::attributes::AttributeDef::default();
    let empty_str = String::new();

    // "A" — attribute assignment + 40 × (isSkill, hidden, name).
    let mut a = vec![b'A', attr_assignment];
    for i in 0..40 {
        let ad = attrs.get(i).unwrap_or(&empty_attr);
        a.push(ad.is_skill as u8);
        a.push(ad.hidden as u8);
        push_str8(&mut a, &ad.name);
    }
    out.push((P_FETCH_ACTORS, a));

    // "D" — 20 damage-type names.
    let mut d = vec![b'D'];
    for i in 0..20 {
        push_str8(&mut d, damage_names.get(i).unwrap_or(&empty_str));
    }
    out.push((P_FETCH_ACTORS, d));

    // "E" — environment: clock + 12 seasons + 20 months.
    let mut e = vec![b'E'];
    e.extend_from_slice(&clock.year.to_le_bytes()); // 4
    push_u16(&mut e, clock.day as u16); // 2
    e.push(clock.time_h as u8);
    e.push(clock.time_m as u8);
    e.push(clock.time_factor as u8);
    let empty_season = Season::default();
    let empty_month = Month::default();
    for i in 0..12 {
        let s = seasons.get(i).unwrap_or(&empty_season);
        push_str8(&mut e, &s.name);
        push_u16(&mut e, s.start_day as u16);
        e.push(s.dusk_h as u8);
        e.push(s.dawn_h as u8);
    }
    for i in 0..20 {
        let mo = months.get(i).unwrap_or(&empty_month);
        push_str8(&mut e, &mo.name);
        push_u16(&mut e, mo.start_day as u16);
    }
    out.push((P_FETCH_ACTORS, e));

    // Item blocks — 6 items per "IN" block, final "IY"+total.
    let total_items = items.len().min(u16::MAX as usize) as u16;
    let mut pa: Vec<u8> = Vec::new();
    let mut items_sent = 0u32;
    let last_item = items.len().saturating_sub(1);
    for (idx, it) in items.iter().enumerate() {
        items_sent += 1;
        encode_item(&mut pa, it);
        if items_sent >= 6 && idx != last_item {
            let mut block = vec![b'I', b'N'];
            block.append(&mut pa);
            out.push((P_FETCH_ACTORS, block));
            items_sent = 0;
        }
    }
    let mut iy = vec![b'I', b'Y'];
    iy.extend_from_slice(&total_items.to_le_bytes());
    iy.append(&mut pa);
    out.push((P_FETCH_ACTORS, iy));

    // Faction blocks — all 100 slots (name + index), flushed at 800 bytes. The
    // client requires exactly 100 (`FactionsReceived = 100`), so every slot is
    // sent even when unnamed.
    let mut fpa: Vec<u8> = Vec::new();
    for i in 0..100u8 {
        let name = faction_names.get(i as usize).unwrap_or(&empty_str);
        let b = name.as_bytes();
        let n = b.len().min(255);
        fpa.push(n as u8);
        fpa.push(i);
        fpa.extend_from_slice(&b[..n]);
        if fpa.len() >= 800 {
            let mut block = vec![b'F'];
            block.append(&mut fpa);
            out.push((P_FETCH_ACTORS, block));
        }
    }
    if !fpa.is_empty() {
        let mut block = vec![b'F'];
        block.append(&mut fpa);
        out.push((P_FETCH_ACTORS, block));
    }

    // Actor blocks — 2 actors per "N" block, final "Y"+total.
    let total_actors = actors.len().min(u16::MAX as usize) as u16;
    let mut apa: Vec<u8> = Vec::new();
    let mut actors_sent = 0u32;
    let last_actor = actors.len().saturating_sub(1);
    for (idx, ac) in actors.iter().enumerate() {
        actors_sent += 1;
        encode_actor(&mut apa, ac);
        if actors_sent >= 2 && idx != last_actor {
            let mut block = vec![b'N'];
            block.append(&mut apa);
            out.push((P_FETCH_ACTORS, block));
            actors_sent = 0;
        }
    }
    let mut y = vec![b'Y'];
    y.extend_from_slice(&total_actors.to_le_bytes());
    y.append(&mut apa);
    out.push((P_FETCH_ACTORS, y));

    out
}

/// One item record (`ServerNet.bb:2289-2312`).
fn encode_item(buf: &mut Vec<u8>, it: &ItemDef) {
    push_u16(buf, it.id);
    buf.push(it.item_type);
    buf.push(it.takes_damage);
    buf.extend_from_slice(&it.value.to_le_bytes());
    push_i16(buf, it.mass);
    push_i16(buf, it.thumbnail_tex_id);
    for g in &it.gubbins {
        buf.push(*g as u8); // low byte only (Blitz sends length 1)
    }
    push_u16(buf, it.mmesh);
    push_u16(buf, it.fmesh);
    push_i16(buf, it.slot_type);
    buf.push(it.stackable as u8);
    for v in &it.attributes {
        push_u16(buf, (*v as i32 + ATTR_BIAS) as u16);
    }
    push_str8(buf, &it.name);
    push_str8(buf, &it.excl_race);
    push_str8(buf, &it.excl_class);
    match it.item_type {
        I_WEAPON => {
            push_i16(buf, it.weapon_damage);
            push_i16(buf, it.weapon_damage_type);
            push_i16(buf, it.weapon_wtype);
            buf.extend_from_slice(&it.weapon_range.to_le_bytes());
        }
        I_ARMOUR => push_i16(buf, it.armour_level),
        I_POTION | I_INGREDIENT => push_i16(buf, it.eat_effects_length),
        I_IMAGE => push_i16(buf, it.image_id),
        _ => {}
    }
    push_str8(buf, &it.misc_data);
}

/// One actor record (`ServerNet.bb:2330-2356`).
fn encode_actor(buf: &mut Vec<u8>, ac: &ActorTemplate) {
    push_u16(buf, ac.id);
    buf.push(ac.playable as u8);
    buf.push(ac.poly_collision);
    for v in &ac.mesh_ids {
        push_i16(buf, *v);
    }
    for v in &ac.beard_ids {
        push_i16(buf, *v);
    }
    for v in &ac.male_hair_ids {
        push_i16(buf, *v);
    }
    for v in &ac.female_hair_ids {
        push_i16(buf, *v);
    }
    for v in &ac.male_face_ids {
        push_i16(buf, *v);
    }
    for v in &ac.female_face_ids {
        push_i16(buf, *v);
    }
    for v in &ac.male_body_ids {
        push_i16(buf, *v);
    }
    for v in &ac.female_body_ids {
        push_i16(buf, *v);
    }
    for v in &ac.m_speech_ids {
        push_i16(buf, *v);
    }
    for v in &ac.f_speech_ids {
        push_i16(buf, *v);
    }
    buf.push(ac.rideable as u8);
    buf.push(ac.trade_mode);
    push_i16(buf, ac.blood_tex_id);
    buf.push(ac.aggressiveness);
    buf.push(ac.genders);
    buf.push(ac.environment);
    push_u16(buf, ac.inventory_slots as u16);
    push_i16(buf, ac.m_animation_set);
    push_i16(buf, ac.f_animation_set);
    buf.extend_from_slice(&ac.scale.to_le_bytes());
    buf.push(ac.default_faction);
    for i in 0..40 {
        push_i16(buf, ac.attr_value.get(i).copied().unwrap_or(0));
        push_i16(buf, ac.attr_maximum.get(i).copied().unwrap_or(0));
    }
    push_str8(buf, &ac.race);
    push_str8(buf, &ac.class);
    push_str16(buf, &ac.description);
}

#[cfg(test)]
mod tests {
    use super::*;
    use rcce_server_core::update_files::UpdateFile;

    #[test]
    fn empty_manifest_is_a_single_two_byte_packet() {
        // The shipped Files.dat is empty → the client's `Len == 2` no-files branch.
        let out = build_fetch_update_files(&[]);
        assert_eq!(out.len(), 1);
        assert_eq!(out[0].0, P_FETCH_UPDATE_FILES);
        assert_eq!(out[0].1, vec![0u8, 0u8]); // total = 0, LE
    }

    #[test]
    fn update_files_records_and_terminator() {
        let files = vec![
            UpdateFile { name: "A".into(), checksum: 1 },
            UpdateFile { name: "BB".into(), checksum: 0x0203_0405 },
        ];
        let out = build_fetch_update_files(&files);
        // Small payload → one packet: rec1 + rec2 + total(2).
        assert_eq!(out.len(), 1);
        let p = &out[0].1;
        // rec1: checksum 1 LE (4) + len 1 + 'A'
        assert_eq!(&p[0..4], &1i32.to_le_bytes());
        assert_eq!(p[4], 1);
        assert_eq!(p[5], b'A');
        // rec2: checksum + len 2 + "BB"
        assert_eq!(&p[6..10], &0x0203_0405i32.to_le_bytes());
        assert_eq!(p[10], 2);
        assert_eq!(&p[11..13], b"BB");
        // total(2) terminator
        assert_eq!(&p[13..15], &2u16.to_le_bytes());
        assert_eq!(p.len(), 15);
    }

    #[test]
    fn update_files_fragments_at_950() {
        // 200 records × ~10 bytes each forces multiple ≤950-byte packets, each
        // parseable, with the total on the last.
        let files: Vec<UpdateFile> = (0..200)
            .map(|i| UpdateFile { name: format!("File{i:04}"), checksum: i })
            .collect();
        let out = build_fetch_update_files(&files);
        assert!(out.len() > 1, "should fragment");
        for (i, (_t, p)) in out.iter().enumerate() {
            // Only the last packet carries the 2-byte count tail; all packets
            // stay under the flush threshold + one record.
            assert!(p.len() < 950 + 32, "packet {i} over-long: {}", p.len());
        }
        // Last packet ends with total = 200.
        let last = &out.last().unwrap().1;
        assert_eq!(&last[last.len() - 2..], &200u16.to_le_bytes());
    }
}
