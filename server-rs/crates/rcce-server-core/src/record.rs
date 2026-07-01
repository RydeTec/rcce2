//! Per-character on-disk record — the unit `SaveAccounts`/`LoadAccounts` writes
//! for each non-null character slot (`AccountsServer.bb:257-268, 340-367`):
//! `WriteActorInstance` + 500×(QuestName, QuestStatus) + 36×ActionBarSlot.

use crate::blitz_io::{Reader, Writer};
use crate::character::{read_character, write_character, Character};

/// Quest-log entries per character (`For j = 0 To 499`).
pub const QUEST_ENTRIES: usize = 500;
/// Action-bar slots per character (`For j = 0 To 35`).
pub const ACTION_BAR_SLOTS: usize = 36;
/// `ReadBoundedString` caps from `LoadAccounts`.
const MAX_QUEST_TEXT: u32 = 1024;
const MAX_ACTION_BAR_TEXT: u32 = 256;

/// One quest-log row: a name and its status text.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct QuestEntry {
    pub name: String,
    pub status: String,
}

/// A full character slot as persisted: the actor plus its quest log and
/// action bar.
#[derive(Clone, Debug, PartialEq)]
pub struct CharacterRecord {
    pub actor: Character,
    /// 500 quest rows (fixed on disk; serializer always writes/reads 500).
    pub quests: Vec<QuestEntry>,
    /// 36 action-bar slot strings.
    pub action_bar: Vec<String>,
}

impl CharacterRecord {
    /// A record wrapping `actor` with an empty quest log and action bar.
    pub fn new(actor: Character) -> Self {
        Self {
            actor,
            quests: vec![QuestEntry::default(); QUEST_ENTRIES],
            action_bar: vec![String::new(); ACTION_BAR_SLOTS],
        }
    }
}

/// Serialize a character record (actor + quest log + action bar).
pub fn write_record(w: &mut Writer, rec: &CharacterRecord) {
    write_character(w, &rec.actor);
    for i in 0..QUEST_ENTRIES {
        let q = rec.quests.get(i);
        w.string(q.map(|q| q.name.as_str()).unwrap_or(""));
        w.string(q.map(|q| q.status.as_str()).unwrap_or(""));
    }
    for i in 0..ACTION_BAR_SLOTS {
        w.string(rec.action_bar.get(i).map(String::as_str).unwrap_or(""));
    }
}

/// Deserialize a character record. `None` on stream underflow (soft-fail).
pub fn read_record(r: &mut Reader, has_portal_triad: bool) -> Option<CharacterRecord> {
    let actor = read_character(r, has_portal_triad)?;
    let mut quests = Vec::with_capacity(QUEST_ENTRIES);
    for _ in 0..QUEST_ENTRIES {
        let name = r.string(MAX_QUEST_TEXT)?;
        let status = r.string(MAX_QUEST_TEXT)?;
        quests.push(QuestEntry { name, status });
    }
    let mut action_bar = Vec::with_capacity(ACTION_BAR_SLOTS);
    for _ in 0..ACTION_BAR_SLOTS {
        action_bar.push(r.string(MAX_ACTION_BAR_TEXT)?);
    }
    Some(CharacterRecord {
        actor,
        quests,
        action_bar,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn record_roundtrip() {
        let mut actor = Character::blank();
        actor.actor_id = 7;
        actor.name = "Mira".into();
        let mut rec = CharacterRecord::new(actor);
        rec.quests[0] = QuestEntry {
            name: "The First Errand".into(),
            status: "started".into(),
        };
        rec.quests[499] = QuestEntry {
            name: "Last".into(),
            status: "done".into(),
        };
        rec.action_bar[0] = "fireball".into();
        rec.action_bar[35] = "potion".into();

        let mut w = Writer::new();
        write_record(&mut w, &rec);
        let bytes = w.into_bytes();
        let mut r = Reader::new(&bytes);
        let back = read_record(&mut r, true).expect("read");
        assert_eq!(back, rec);
        assert!(r.at_end());
    }

    #[test]
    fn truncated_record_is_none() {
        let rec = CharacterRecord::new(Character::blank());
        let mut w = Writer::new();
        write_record(&mut w, &rec);
        let mut bytes = w.into_bytes();
        bytes.truncate(bytes.len() - 1); // drop the last action-bar byte
        let mut r = Reader::new(&bytes);
        assert!(read_record(&mut r, true).is_none());
    }
}
