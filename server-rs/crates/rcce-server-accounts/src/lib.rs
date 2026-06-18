//! RCCE2 server-side accounts.
//!
//! Phase 1: password hashing at byte-for-byte parity with `PasswordHash.bb`
//! (salted SHA-256 v1 + legacy raw-MD5 acceptance + lazy upgrade), so account
//! records written by the Blitz server verify here and vice-versa. Account /
//! character flat-file persistence and the six login packet handlers build on
//! top of this module.

pub mod password;
pub mod store;
pub mod throttle;
