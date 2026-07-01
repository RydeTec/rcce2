//! Server configuration — parity with `Server.bb`'s `Misc.dat` load, plus
//! container-friendly env overrides.
//!
//! `Data/Server Data/Misc.dat` is 22 bytes, all little-endian (file convention):
//! ```text
//! [i32 StartGold][i32 StartReputation][u8 ForcePortals][i16 CombatDelay]
//! [u8 CombatFormula][u8 WeaponDamage][u8 ArmourDamage][u8 CombatRatingAdjust]
//! [u8 AllowAccountCreation][u8 MaxAccountChars][i32 ServerPort][u8 RequireMemorise]
//! ```
//! Field offsets are fixed; we read the few the server needs and ignore the
//! combat-tuning bytes until the combat phase.

use std::path::{Path, PathBuf};

/// Fixed byte offsets into `Misc.dat`.
const OFF_START_GOLD: usize = 0;
const OFF_START_REP: usize = 4;
const OFF_ALLOW_ACCOUNT_CREATION: usize = 15;
const OFF_MAX_ACCOUNT_CHARS: usize = 16;
const OFF_SERVER_PORT: usize = 17;
const MISC_LEN: usize = 22;

/// `Server.bb:265` default when the configured port reads 0.
const DEFAULT_PORT: u16 = 25000;

#[derive(Clone, Debug)]
pub struct ServerConfig {
    pub port: u16,
    pub allow_account_creation: bool,
    pub max_account_chars: u8,
    pub start_gold: i32,
    pub start_reputation: i32,
    /// Free attribute points a new character may distribute. Lives in
    /// `Attributes.dat` (not `Misc.dat`); defaulted to 0 until that loader
    /// lands — with 0, `P_CreateCharacter` ignores the 40 attribute-point bytes.
    pub attribute_assignment: u8,
    /// Resolved project `data/` directory (holds `Server Data/`, etc.).
    pub data_dir: PathBuf,
}

impl ServerConfig {
    /// Load config: resolve the data dir, parse `Misc.dat` if present, then
    /// apply env overrides. Missing/short `Misc.dat` falls back to defaults so a
    /// bare container still boots.
    pub fn load() -> Self {
        let data_dir = resolve_data_dir();
        let mut cfg = ServerConfig {
            port: DEFAULT_PORT,
            allow_account_creation: true,
            max_account_chars: 4,
            start_gold: 0,
            start_reputation: 0,
            attribute_assignment: 0,
            data_dir: data_dir.clone(),
        };
        let misc = data_dir.join("Server Data").join("Misc.dat");
        match std::fs::read(&misc) {
            Ok(bytes) => cfg.apply_misc(&bytes),
            Err(_) => { /* defaults; logged by the caller if desired */ }
        }
        cfg.apply_env_overrides();
        cfg
    }

    fn apply_misc(&mut self, b: &[u8]) {
        if b.len() < MISC_LEN {
            return; // too short / corrupt — keep defaults
        }
        self.start_gold = i32::from_le_bytes(b[OFF_START_GOLD..OFF_START_GOLD + 4].try_into().unwrap());
        self.start_reputation =
            i32::from_le_bytes(b[OFF_START_REP..OFF_START_REP + 4].try_into().unwrap());
        self.allow_account_creation = b[OFF_ALLOW_ACCOUNT_CREATION] != 0;
        self.max_account_chars = b[OFF_MAX_ACCOUNT_CHARS];
        let port = i32::from_le_bytes(b[OFF_SERVER_PORT..OFF_SERVER_PORT + 4].try_into().unwrap());
        self.port = if port <= 0 || port > u16::MAX as i32 {
            DEFAULT_PORT
        } else {
            port as u16
        };
    }

    fn apply_env_overrides(&mut self) {
        if let Some(p) = std::env::var("RCCE_PORT").ok().and_then(|v| v.parse().ok()) {
            self.port = p;
        }
        match std::env::var("RCCE_ALLOW_ACCOUNT_CREATION").ok().as_deref() {
            Some("1") | Some("true") => self.allow_account_creation = true,
            Some("0") | Some("false") => self.allow_account_creation = false,
            _ => {}
        }
    }

    /// Path to `Accounts.dat`.
    pub fn accounts_path(&self) -> PathBuf {
        self.data_dir.join("Server Data").join("Accounts.dat")
    }
}

/// Resolve the project `data/` directory: `RCCE_DATA` env, else the first of
/// `./data`, `../data`, `../../data` that exists, else `./data`. Mirrors the
/// Rust client's lookup so server + client share one project tree.
fn resolve_data_dir() -> PathBuf {
    if let Ok(d) = std::env::var("RCCE_DATA") {
        return PathBuf::from(d);
    }
    for candidate in ["data", "../data", "../../data"] {
        let p = Path::new(candidate);
        if p.join("Server Data").is_dir() || p.is_dir() {
            return p.to_path_buf();
        }
    }
    PathBuf::from("data")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_real_misc_layout() {
        // The shipped data/Server Data/Misc.dat bytes (22) from the repo:
        // StartGold=5000, StartRep=50, …, AllowAccountCreation=1,
        // MaxAccountChars=4, ServerPort=25000.
        let bytes: [u8; 22] = [
            0x88, 0x13, 0x00, 0x00, // start_gold = 5000
            0x32, 0x00, 0x00, 0x00, // start_rep = 50
            0x01, // force_portals
            0xe8, 0x03, // combat_delay = 1000
            0x01, // combat_formula
            0x00, // weapon_damage
            0x01, // armour_damage
            0x00, // combat_rating_adjust
            0x01, // allow_account_creation = true
            0x04, // max_account_chars = 4
            0xa8, 0x61, 0x00, 0x00, // server_port = 25000
            0x01, // require_memorise
        ];
        let mut cfg = ServerConfig {
            port: 0,
            allow_account_creation: false,
            max_account_chars: 0,
            start_gold: 0,
            start_reputation: 0,
            attribute_assignment: 0,
            data_dir: PathBuf::new(),
        };
        cfg.apply_misc(&bytes);
        assert_eq!(cfg.start_gold, 5000);
        assert_eq!(cfg.start_reputation, 50);
        assert!(cfg.allow_account_creation);
        assert_eq!(cfg.max_account_chars, 4);
        assert_eq!(cfg.port, 25000);
    }

    #[test]
    fn short_misc_keeps_defaults() {
        let mut cfg = ServerConfig {
            port: DEFAULT_PORT,
            allow_account_creation: true,
            max_account_chars: 4,
            start_gold: 0,
            start_reputation: 0,
            attribute_assignment: 0,
            data_dir: PathBuf::new(),
        };
        cfg.apply_misc(&[0u8; 5]);
        assert_eq!(cfg.port, DEFAULT_PORT);
        assert!(cfg.allow_account_creation);
    }

    #[test]
    fn zero_port_falls_back_to_default() {
        let mut bytes = [0u8; 22];
        bytes[OFF_MAX_ACCOUNT_CHARS] = 4;
        // server_port left as 0 → default.
        let mut cfg = ServerConfig {
            port: 0,
            allow_account_creation: false,
            max_account_chars: 0,
            start_gold: 0,
            start_reputation: 0,
            attribute_assignment: 0,
            data_dir: PathBuf::new(),
        };
        cfg.apply_misc(&bytes);
        assert_eq!(cfg.port, DEFAULT_PORT);
    }
}
