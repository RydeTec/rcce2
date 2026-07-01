//! RSL (RealmCrafter Scripting Language) interpreter.
//!
//! The Blitz server embeds **BriskVM** — a third-party, closed-source native VM
//! (a Windows DLL) — to compile and run the game's `.rsl` content scripts (NPC
//! dialog, quests, shops, spell/item effects, the `Login`/`Death`/`Attack`
//! hooks, slash-commands). That DLL is not available for a headless Linux Rust
//! server, so parity requires **reimplementing the script runtime in Rust** —
//! interpreting the `.rsl` *source* directly (the source ships in
//! `Data/Server Data/Scripts/`), not reverse-engineering BriskVM's bytecode.
//!
//! This crate is that runtime, built bottom-up: lexer → parser → tree-walking
//! interpreter → the `BVM_*` command surface (the native host functions scripts
//! call, ported from `ScriptingCommands.bb` with the privilege-gating model
//! intact). See `docs/rust-server/STATE.md` for the porting order.

pub mod ast;
pub mod builtins;
pub mod interp;
pub mod lexer;
pub mod parser;

pub use builtins::builtin;
pub use interp::{run_function, Host, Value};
