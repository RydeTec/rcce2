//! `Spells.dat` (Server Data) — spell definitions. The Rust **server** needs
//! each spell's use-script (`Script`/`SMethod`), recharge time, and race/class
//! restriction to resolve a `P_SpellUpdate` cast (`ServerNet.bb:1276`:
//! `ThreadScript(Sp\Script$, Sp\SMethod$, caster, target, level)`). Mirrors
//! `Spells.bb` `LoadSpells`. The client doesn't use the script fields, but the
//! parser lives here alongside the other shared `.dat` parsers.
//!
//! Per record: `id i16 · Name str(256) · Description str(1024) · ThumbnailTexID
//! i16 · ExclRace str(256) · ExclClass str(256) · RechargeTime i32 · Script
//! str(1024) · SMethod str(1024)`.

use crate::reader::{BlitzReader, ReadError};

/// One spell definition.
#[derive(Debug, Clone, PartialEq)]
pub struct SpellDef {
    pub id: u16,
    pub name: String,
    /// Toolbar/character-sheet description text; sent to the client in the
    /// `P_FetchCharacter` `"S"` records and `P_KnownSpellUpdate` `"A"`.
    pub description: String,
    /// Toolbar art texture id (`Sp\ThumbnailTexID`), same wire consumers.
    pub thumbnail_tex_id: i16,
    /// Empty = any race may cast.
    pub exclusive_race: String,
    /// Empty = any class may cast.
    pub exclusive_class: String,
    /// Cooldown in milliseconds after a cast.
    pub recharge_time: i32,
    /// Server-side script run on cast (`Item`-style); empty = no effect.
    pub script: String,
    /// Method in `script`; empty → `"Main"`.
    pub smethod: String,
}

/// All spell definitions from `Spells.dat`, in file order.
#[derive(Debug, Clone, Default)]
pub struct SpellCatalog {
    pub spells: Vec<SpellDef>,
}

impl SpellCatalog {
    /// Parse a whole `Spells.dat`, stopping cleanly at EOF or the first corrupt
    /// record (same posture as the engine's `LoadSpells`).
    pub fn parse(data: &[u8]) -> SpellCatalog {
        let mut r = BlitzReader::new(data);
        let mut spells = Vec::new();
        while !r.eof() {
            match Self::parse_record(&mut r) {
                Ok(s) => spells.push(s),
                Err(_) => break,
            }
        }
        SpellCatalog { spells }
    }

    fn parse_record(r: &mut BlitzReader) -> Result<SpellDef, ReadError> {
        let id = r.read_short()?;
        // Signed read; the engine Dims SpellsList 0..65534, and a negative id is
        // a corrupt/misaligned record — stop here.
        if id < 0 {
            return Err(ReadError::UnexpectedEof { offset: 0, needed: 0, available: 0 });
        }
        let name = r.read_string(256)?;
        let description = r.read_string(1024)?;
        let thumbnail_tex_id = r.read_short()?;
        let exclusive_race = r.read_string(256)?;
        let exclusive_class = r.read_string(256)?;
        let recharge_time = r.read_int()?;
        let script = r.read_string(1024)?;
        let smethod = r.read_string(1024)?;
        Ok(SpellDef {
            id: id as u16,
            name,
            description,
            thumbnail_tex_id,
            exclusive_race,
            exclusive_class,
            recharge_time,
            script,
            smethod,
        })
    }

    /// Look up a spell by id (linear).
    pub fn get(&self, id: u16) -> Option<&SpellDef> {
        self.spells.iter().find(|s| s.id == id)
    }
}
