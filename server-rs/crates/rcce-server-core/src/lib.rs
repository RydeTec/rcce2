//! RCCE2 server world types — the Blitz file codec and the `ItemInstance` /
//! `Character` (ActorInstance) serialization at `Accounts.dat` parity. Higher
//! layers (account persistence, character handlers, the live world) build on
//! these.

pub mod actor_catalog;
pub mod area;
pub mod blitz_io;
pub mod character;
pub mod combat;
pub mod environment;
pub mod faction;
pub mod item;
pub mod record;
pub mod rng;
pub mod update_files;
pub mod world;

pub use actor_catalog::{
    ActorCatalog, ActorParseCompletion, ActorParseEvidence, ActorRecordEvidence, ActorTemplate,
    RawSpan,
};
pub use area::{Area, Portal, SpawnPoint};
pub use environment::Environment;
pub use faction::FactionData;
pub use update_files::UpdateFile;
pub use world::RuntimeIdAllocator;

pub use character::{read_character, write_character, Attributes, Character, InventorySlot};
pub use item::ItemInstance;
pub use record::{read_record, write_record, CharacterRecord, QuestEntry};
