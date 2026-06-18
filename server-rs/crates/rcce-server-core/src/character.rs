//! `Character` (ActorInstance) stream serialization — parity with
//! `WriteActorInstance` / `ReadActorInstance` (`Actors.bb:324-594`).
//!
//! This is the per-character payload inside `Accounts.dat` (and, later, the
//! basis for the live in-world actor). The field order, widths, and the
//! read-side clamps are reproduced exactly; a byte that round-trips here is the
//! same byte the Blitz server reads. The read clamps are security-relevant: a
//! corrupt/tampered `Accounts.dat` must degrade to a sane actor, never crash or
//! over-allocate the shared server.

use crate::blitz_io::{Reader, Writer};
use crate::item::{read_item, write_item, ItemInstance};

/// `Slots_Inventory = 45` → the loop `For i = 0 To 45` is **46** slots.
pub const INVENTORY_SLOTS: usize = 46;
const ATTR_COUNT: usize = 40;
const RESIST_COUNT: usize = 20;
const FACTION_COUNT: usize = 100;
const SCRIPT_GLOBAL_COUNT: usize = 10;
const SPELL_COUNT: usize = 1000;
const MEMORISED_COUNT: usize = 10;
/// `MemorisedSpells` "no spell" sentinel.
const MEMORISED_EMPTY: i16 = 5000;
/// `WorldCoordMax#` (RCEnet.bb) — positions outside ±this (or NaN/Inf) clamp to 0.
const WORLD_COORD_MAX: f32 = 100_000.0;
/// Max valid spell id (`SpellsList` is `Dim`'d 0..65534).
const MAX_SPELL_ID_VAL: i32 = 65534;
/// Slave (pet) cap from `ReadActorInstance`.
const MAX_SLAVES: u8 = 32;

/// `ReadBoundedString` caps from `ReadActorInstance`.
const MAX_AREA_NAME_TAG: u32 = 256;
const MAX_SCRIPT: u32 = 1024;
const MAX_SCRIPT_GLOBAL: u32 = 1024;
const MAX_PORTAL_NAME: u32 = 256;

/// 40 per-actor attribute value/maximum pairs.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Attributes {
    pub value: Vec<i16>,
    pub maximum: Vec<i16>,
}

impl Default for Attributes {
    fn default() -> Self {
        Self {
            value: vec![0; ATTR_COUNT],
            maximum: vec![0; ATTR_COUNT],
        }
    }
}

/// One inventory slot: an optional item + a stack amount.
#[derive(Clone, Debug, PartialEq, Eq, Default)]
pub struct InventorySlot {
    pub item: Option<ItemInstance>,
    pub amount: i16,
}

/// A persisted character (ActorInstance). Use [`Character::blank`] then set the
/// fields you need; fixed-size arrays are kept as `Vec`s sized to the on-disk
/// counts (the serializer always writes/reads exactly the right count).
#[derive(Clone, Debug, PartialEq)]
pub struct Character {
    pub actor_id: u16,
    pub area: String,
    pub name: String,
    pub tag: String,
    pub team_id: i32,
    pub x: f32,
    pub y: f32,
    pub z: f32,
    pub gender: u8,
    pub xp: i32,
    pub xp_bar_level: u8,
    pub level: i16,
    pub face_tex: i16,
    pub hair: i16,
    pub beard: i16,
    pub body_tex: i16,
    pub attributes: Attributes,
    pub resistances: Vec<i16>,
    pub inventory: Vec<InventorySlot>,
    pub script: String,
    pub death_script: String,
    pub reputation: i16,
    pub gold: i32,
    pub home_faction: u8,
    pub faction_ratings: Vec<u8>,
    pub script_globals: Vec<String>,
    pub known_spells: Vec<i16>,
    pub spell_levels: Vec<i16>,
    pub memorised_spells: Vec<i16>,
    pub last_portal_area_name: String,
    pub last_portal: i16,
    pub last_portal_time: i32,
    pub slaves: Vec<Character>,
}

impl Character {
    /// A zeroed character with all fixed-size collections sized correctly and
    /// empty memorised-spell slots set to the sentinel (matching a fresh actor).
    pub fn blank() -> Self {
        Self {
            actor_id: 0,
            area: String::new(),
            name: String::new(),
            tag: String::new(),
            team_id: 0,
            x: 0.0,
            y: 0.0,
            z: 0.0,
            gender: 0,
            xp: 0,
            xp_bar_level: 0,
            level: 0,
            face_tex: 0,
            hair: 0,
            beard: 0,
            body_tex: 0,
            attributes: Attributes::default(),
            resistances: vec![0; RESIST_COUNT],
            inventory: vec![InventorySlot::default(); INVENTORY_SLOTS],
            script: String::new(),
            death_script: String::new(),
            reputation: 0,
            gold: 0,
            home_faction: 0,
            faction_ratings: vec![0; FACTION_COUNT],
            script_globals: vec![String::new(); SCRIPT_GLOBAL_COUNT],
            known_spells: vec![0; SPELL_COUNT],
            spell_levels: vec![0; SPELL_COUNT],
            memorised_spells: vec![MEMORISED_EMPTY; MEMORISED_COUNT],
            last_portal_area_name: String::new(),
            last_portal: 0,
            last_portal_time: 0,
            slaves: Vec::new(),
        }
    }
}

fn clamp_world_coord(v: f32) -> f32 {
    // NaN compares false against both bounds → falls through to 0.
    if v > -WORLD_COORD_MAX && v < WORLD_COORD_MAX {
        v
    } else {
        0.0
    }
}

/// Serialize a character — `WriteActorInstance`. The slave records are appended
/// after this character's own fields (depth-first), matching the Blitz walk of
/// the `FirstSlave` chain.
pub fn write_character(w: &mut Writer, c: &Character) {
    w.u16(c.actor_id);
    w.string(&c.area);
    w.string(&c.name);
    w.string(&c.tag);
    w.i32(c.team_id);
    w.f32(c.x);
    w.f32(c.y);
    w.f32(c.z);
    w.u8(c.gender);
    w.i32(c.xp);
    w.u8(c.xp_bar_level);
    w.i16(c.level);
    w.i16(c.face_tex);
    w.i16(c.hair);
    w.i16(c.beard);
    w.i16(c.body_tex);
    for i in 0..ATTR_COUNT {
        w.i16(c.attributes.value.get(i).copied().unwrap_or(0));
        w.i16(c.attributes.maximum.get(i).copied().unwrap_or(0));
    }
    for i in 0..RESIST_COUNT {
        w.i16(c.resistances.get(i).copied().unwrap_or(0));
    }
    for i in 0..INVENTORY_SLOTS {
        let slot = c.inventory.get(i).cloned().unwrap_or_default();
        write_item(w, &slot.item);
        w.i16(slot.amount);
    }
    w.string(&c.script);
    w.string(&c.death_script);
    w.i16(c.reputation);
    w.i32(c.gold);
    w.u8(c.slaves.len().min(MAX_SLAVES as usize) as u8); // NumberOfSlaves
    w.u8(c.home_faction);
    for i in 0..FACTION_COUNT {
        w.u8(c.faction_ratings.get(i).copied().unwrap_or(0));
    }
    for i in 0..SCRIPT_GLOBAL_COUNT {
        w.string(c.script_globals.get(i).map(String::as_str).unwrap_or(""));
    }
    for i in 0..SPELL_COUNT {
        w.i16(c.known_spells.get(i).copied().unwrap_or(0));
        w.i16(c.spell_levels.get(i).copied().unwrap_or(0));
    }
    for i in 0..MEMORISED_COUNT {
        w.i16(c.memorised_spells.get(i).copied().unwrap_or(MEMORISED_EMPTY));
    }
    // v1 portal triad.
    w.string(&c.last_portal_area_name);
    w.i16(c.last_portal);
    w.i32(c.last_portal_time);
    // Slave records, depth-first.
    for slave in c.slaves.iter().take(MAX_SLAVES as usize) {
        write_character(w, slave);
    }
}

/// Deserialize a character — `ReadActorInstance`. `has_portal_triad` is true for
/// v1 `Accounts.dat` (always, for files this server writes); false for legacy
/// v0. Returns `None` on stream underflow (soft-fail). All read-side clamps from
/// the Blitz loader are applied.
pub fn read_character(r: &mut Reader, has_portal_triad: bool) -> Option<Character> {
    let mut c = Character::blank();
    c.actor_id = r.u16()?;
    c.area = r.string(MAX_AREA_NAME_TAG)?;
    c.name = r.string(MAX_AREA_NAME_TAG)?;
    c.tag = r.string(MAX_AREA_NAME_TAG)?;
    c.team_id = r.i32()?;
    c.x = clamp_world_coord(r.f32()?);
    c.y = clamp_world_coord(r.f32()?);
    c.z = clamp_world_coord(r.f32()?);
    c.gender = r.u8()?;
    c.xp = r.i32()?;
    c.xp_bar_level = r.u8()?;
    c.level = r.i16()?;
    c.face_tex = r.i16()?;
    c.hair = r.i16()?;
    c.beard = r.i16()?;
    c.body_tex = r.i16()?;
    // Appearance clamps.
    if c.gender > 1 {
        c.gender = 0;
    }
    if !(0..=4).contains(&c.face_tex) {
        c.face_tex = 0;
    }
    if !(0..=4).contains(&c.hair) {
        c.hair = 0;
    }
    if !(0..=4).contains(&c.beard) {
        c.beard = 0;
    }
    if !(0..=4).contains(&c.body_tex) {
        c.body_tex = 0;
    }
    for i in 0..ATTR_COUNT {
        c.attributes.value[i] = r.i16()?;
        c.attributes.maximum[i] = r.i16()?;
    }
    for i in 0..RESIST_COUNT {
        c.resistances[i] = r.i16()?;
    }
    for i in 0..INVENTORY_SLOTS {
        c.inventory[i].item = read_item(r)?;
        c.inventory[i].amount = r.i16()?;
    }
    c.script = r.string(MAX_SCRIPT)?;
    c.death_script = r.string(MAX_SCRIPT)?;
    c.reputation = r.i16()?;
    c.gold = r.i32()?;
    let mut number_of_slaves = r.u8()?;
    if number_of_slaves > MAX_SLAVES {
        number_of_slaves = 0;
    }
    c.home_faction = r.u8()?;
    if c.home_faction > 99 {
        c.home_faction = 0;
    }
    for i in 0..FACTION_COUNT {
        c.faction_ratings[i] = r.u8()?;
    }
    for i in 0..SCRIPT_GLOBAL_COUNT {
        c.script_globals[i] = r.string(MAX_SCRIPT_GLOBAL)?;
    }
    for i in 0..SPELL_COUNT {
        c.known_spells[i] = r.i16()?;
        c.spell_levels[i] = r.i16()?;
        if c.known_spells[i] < 0 || c.known_spells[i] as i32 > MAX_SPELL_ID_VAL {
            c.known_spells[i] = 0;
            c.spell_levels[i] = 0;
        }
    }
    for i in 0..MEMORISED_COUNT {
        c.memorised_spells[i] = r.i16()?;
        if c.memorised_spells[i] < 0 {
            c.memorised_spells[i] = MEMORISED_EMPTY;
        }
        if c.memorised_spells[i] > 999 && c.memorised_spells[i] != MEMORISED_EMPTY {
            c.memorised_spells[i] = MEMORISED_EMPTY;
        }
    }
    if has_portal_triad {
        c.last_portal_area_name = r.string(MAX_PORTAL_NAME)?;
        c.last_portal = r.i16()?;
        c.last_portal_time = r.i32()?;
    }
    // Slave records.
    for _ in 0..number_of_slaves {
        let slave = read_character(r, has_portal_triad)?;
        c.slaves.push(slave);
    }
    Some(c)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample() -> Character {
        let mut c = Character::blank();
        c.actor_id = 12;
        c.area = "Newhaven".into();
        c.name = "Aldric".into();
        c.tag = "knight".into();
        c.team_id = 3;
        c.x = 100.5;
        c.y = -12.25;
        c.z = 4096.0;
        c.gender = 1;
        c.xp = 5000;
        c.xp_bar_level = 2;
        c.level = 7;
        c.face_tex = 3;
        c.hair = 2;
        c.beard = 1;
        c.body_tex = 4;
        c.attributes.value[0] = 80;
        c.attributes.maximum[0] = 100;
        c.attributes.value[39] = -5;
        c.resistances[5] = 12;
        c.inventory[0].item = Some(ItemInstance::new(1234));
        c.inventory[0].amount = 1;
        c.inventory[3].item = Some({
            let mut it = ItemInstance::new(56);
            it.item_health = 50;
            it.attr_values[2] = 7;
            it
        });
        c.inventory[3].amount = 9;
        c.script = "scripts/aldric.bvm".into();
        c.death_script = "scripts/dead.bvm".into();
        c.reputation = -250;
        c.gold = 5000;
        c.home_faction = 4;
        c.faction_ratings[4] = 200;
        c.script_globals[0] = "g0".into();
        c.known_spells[0] = 17;
        c.spell_levels[0] = 3;
        c.known_spells[1] = 42;
        c.spell_levels[1] = 1;
        c.memorised_spells[0] = 0;
        c.last_portal_area_name = "PortalA".into();
        c.last_portal = 2;
        c.last_portal_time = 123456;
        c
    }

    #[test]
    fn character_roundtrip() {
        let c = sample();
        let mut w = Writer::new();
        write_character(&mut w, &c);
        let bytes = w.into_bytes();
        let mut r = Reader::new(&bytes);
        let back = read_character(&mut r, true).expect("read");
        assert_eq!(back, c);
        assert!(r.at_end(), "no trailing bytes after a clean character");
    }

    #[test]
    fn character_with_slave_roundtrips() {
        let mut c = sample();
        let mut pet = Character::blank();
        pet.actor_id = 99;
        pet.name = "Wolf".into();
        c.slaves.push(pet);
        let mut w = Writer::new();
        write_character(&mut w, &c);
        let bytes = w.into_bytes();
        let mut r = Reader::new(&bytes);
        let back = read_character(&mut r, true).expect("read");
        assert_eq!(back.slaves.len(), 1);
        assert_eq!(back.slaves[0].name, "Wolf");
        assert_eq!(back, c);
    }

    #[test]
    fn out_of_range_appearance_clamps_to_zero() {
        let mut c = sample();
        c.gender = 9;
        c.face_tex = 99;
        c.hair = -1;
        let mut w = Writer::new();
        write_character(&mut w, &c);
        let bytes = w.into_bytes();
        let mut r = Reader::new(&bytes);
        let back = read_character(&mut r, true).unwrap();
        assert_eq!(back.gender, 0);
        assert_eq!(back.face_tex, 0);
        assert_eq!(back.hair, 0);
    }

    #[test]
    fn out_of_range_spell_id_zeros_pair() {
        let mut c = sample();
        c.known_spells[10] = -3;
        c.spell_levels[10] = 5;
        c.known_spells[11] = 65535u16 as i16; // -1
        c.spell_levels[11] = 2;
        let mut w = Writer::new();
        write_character(&mut w, &c);
        let bytes = w.into_bytes();
        let mut r = Reader::new(&bytes);
        let back = read_character(&mut r, true).unwrap();
        assert_eq!(back.known_spells[10], 0);
        assert_eq!(back.spell_levels[10], 0);
        assert_eq!(back.known_spells[11], 0);
        assert_eq!(back.spell_levels[11], 0);
    }

    #[test]
    fn nan_position_clamps_to_zero() {
        let mut c = sample();
        c.x = f32::NAN;
        c.y = f32::INFINITY;
        c.z = 1e9;
        let mut w = Writer::new();
        write_character(&mut w, &c);
        let bytes = w.into_bytes();
        let mut r = Reader::new(&bytes);
        let back = read_character(&mut r, true).unwrap();
        assert_eq!(back.x, 0.0);
        assert_eq!(back.y, 0.0);
        assert_eq!(back.z, 0.0);
    }

    #[test]
    fn truncated_stream_is_none() {
        let c = sample();
        let mut w = Writer::new();
        write_character(&mut w, &c);
        let mut bytes = w.into_bytes();
        bytes.truncate(bytes.len() / 2);
        let mut r = Reader::new(&bytes);
        assert!(read_character(&mut r, true).is_none());
    }
}
