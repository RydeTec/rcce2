//! RCCE2 headless server library — config, login/account packet handlers, and
//! the authoritative state + dispatch. The `rcce-server` binary is a thin tick
//! loop over this; the end-to-end integration test drives the same dispatch.

pub mod characters;
pub mod config;
pub mod language;
pub mod login;
pub mod packet_names;
pub mod scripts;
pub mod spawn;
pub mod state;
pub mod world;
