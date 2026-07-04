//! Language string table (`Language.txt`) — the slash-command word source.
//!
//! Blitz's `ServerNet.bb` chat dispatch matches the player's (upper-cased)
//! command against `LanguageString$(LS_SC*)`, the localized command words. The
//! server loads `Data/Server Data/Language.txt` (`Server.bb:200`), seeded with
//! the built-in defaults (`Language.bb:241-270`) and overridden line-by-line by
//! the file.
//!
//! Parse (`Language.bb:282-321`): trim each line; skip empty and comment-only
//! (`;`-leading) lines WITHOUT advancing the running index; strip an inline
//! trailing comment; store the value at the running content-line index; the
//! slash-command range (`LS_SCKick..LS_SCSeason`, 190..=219) is upper-cased so it
//! matches the engine's upper-cased command input (`Language.bb:315`).

use std::path::Path;

// Slash-command string ids (`Language.bb` constants) the server dispatch needs.
pub const LS_SC_KICK: usize = 190;
pub const LS_SC_XP: usize = 198;
pub const LS_SC_GOLD: usize = 199;
pub const LS_SC_SETATTRIBUTE: usize = 200;
pub const LS_SC_SETATTRIBUTEMAX: usize = 201;
pub const LS_SC_SCRIPT: usize = 202;
pub const LS_SC_ME: usize = 203;
pub const LS_SC_YELL: usize = 204;
pub const LS_SC_GMSAY: usize = 205;
pub const LS_SC_GUILDSAY: usize = 206;
pub const LS_SC_PARTYSAY: usize = 207;
pub const LS_SC_PMSAY: usize = 208;

/// The slash-command range that is upper-cased on load (`Language.bb:315`).
const SC_LO: usize = 190;
const SC_HI: usize = 219;

/// Built-in slash-command defaults (`Language.bb:241-270`), applied before the
/// file so a project shipping a short/absent `Language.txt` still dispatches.
const SC_DEFAULTS: [(usize, &str); 30] = [
    (190, "KICK"), (191, "UNIGNORE"), (192, "IGNORE"), (193, "NETDUMP"), (194, "PET"),
    (195, "LEAVE"), (196, "ACCEPT"), (197, "INVITE"), (198, "XP"), (199, "GOLD"),
    (200, "SETATTRIBUTE"), (201, "SETATTRIBUTEMAX"), (202, "SCRIPT"), (203, "ME"), (204, "YELL"),
    (205, "GM"), (206, "G"), (207, "P"), (208, "PM"), (209, "TRADE"),
    (210, "ALLPLAYERS"), (211, "PLAYERS"), (212, "WARP"), (213, "WARPOTHER"), (214, "ABILITY"),
    (215, "GIVE"), (216, "WEATHER"), (217, "TIME"), (218, "DATE"), (219, "SEASON"),
];

/// The localized string table (indexed by id).
#[derive(Clone, Debug)]
pub struct Language {
    strings: Vec<String>,
}

impl Default for Language {
    fn default() -> Self {
        let mut strings = vec![String::new(); 230];
        for (id, s) in SC_DEFAULTS {
            strings[id] = s.to_string();
        }
        Language { strings }
    }
}

impl Language {
    /// Load the table from `path` (Blitz `LoadLanguage`). A missing/unreadable
    /// file keeps the built-in defaults, so slash-command dispatch still works.
    pub fn load(path: impl AsRef<Path>) -> Self {
        match std::fs::read(path.as_ref()) {
            Ok(bytes) => Self::parse(&String::from_utf8_lossy(&bytes)),
            Err(_) => Language::default(),
        }
    }

    /// Parse the file body: start from the defaults, then override per content
    /// line. Empty and comment-only lines don't advance the index (parity with
    /// `Language.bb:288-320`).
    pub fn parse(text: &str) -> Self {
        let mut lang = Language::default();
        let mut id = 0usize;
        for raw in text.lines() {
            let line = raw.trim();
            if line.is_empty() {
                continue;
            }
            let value = match line.find(';') {
                Some(0) => continue,         // comment-only line — no index advance
                Some(p) => line[..p].trim(), // strip the inline trailing comment
                None => line,
            };
            lang.set(id, value);
            id += 1;
        }
        lang
    }

    fn set(&mut self, id: usize, value: &str) {
        if id >= self.strings.len() {
            self.strings.resize(id + 1, String::new());
        }
        self.strings[id] = if (SC_LO..=SC_HI).contains(&id) {
            value.to_uppercase()
        } else {
            value.to_string()
        };
    }

    /// The string for `id`, or `""` if out of range.
    pub fn get(&self, id: usize) -> &str {
        self.strings.get(id).map(|s| s.as_str()).unwrap_or("")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn defaults_carry_the_slash_command_words() {
        let l = Language::default();
        assert_eq!(l.get(LS_SC_ME), "ME");
        assert_eq!(l.get(LS_SC_YELL), "YELL");
        assert_eq!(l.get(LS_SC_GMSAY), "GM");
        assert_eq!(l.get(LS_SC_PARTYSAY), "P");
        assert_eq!(l.get(LS_SC_PMSAY), "PM");
        assert_eq!(l.get(LS_SC_KICK), "KICK");
    }

    #[test]
    fn parse_skips_comments_and_blanks_and_uppercases_commands() {
        // Ids 0,1,2 then (blank + comment skipped) 3, ... Build a body whose
        // content-line index lands a lowercase command word in the SC range.
        let mut body = String::new();
        for i in 0..190 {
            body.push_str(&format!("line{i}\n"));
        }
        body.push('\n'); // blank — skipped
        body.push_str("; a comment only line — skipped\n");
        body.push_str("kick   ; inline comment stripped\n"); // id 190 → upper-cased
        let l = Language::parse(&body);
        assert_eq!(l.get(190), "KICK", "SC-range value is upper-cased + comment-stripped");
        assert_eq!(l.get(0), "line0", "content indexing starts at 0");
        assert_eq!(l.get(189), "line189");
        // Unset SC ids keep their built-in defaults.
        assert_eq!(l.get(LS_SC_ME), "ME", "an id the file didn't reach keeps its default");
    }

    #[test]
    fn missing_file_keeps_defaults() {
        let l = Language::load("does/not/exist/Language.txt");
        assert_eq!(l.get(LS_SC_YELL), "YELL");
    }
}
