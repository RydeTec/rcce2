//! Non-interchangeable legacy identity domains.
//!
//! ```compile_fail
//! use rcce_project::{ActorId, ItemId};
//! fn requires_item<T: Into<ItemId>>() {}
//! requires_item::<ActorId>();
//! ```
//!
//! ```compile_fail
//! use rcce_project::{EmitterName, ZoneName};
//! fn accepts_zone(_: ZoneName) {}
//! fn mismatched_domain(value: EmitterName) { accepts_zone(value); }
//! ```

use crate::legacy::RawLegacyString;
use std::hash::{Hash, Hasher};

const LEGACY_NONE: u16 = u16::MAX;

/// The identity domain whose legacy sentinel was rejected.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum IdentityKind {
    Item,
    Media,
}

/// A value that cannot identify a record in its declared domain.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct InvalidIdentity {
    kind: IdentityKind,
    raw: u16,
}

impl InvalidIdentity {
    #[must_use]
    pub const fn kind(&self) -> IdentityKind {
        self.kind
    }

    #[must_use]
    pub const fn raw(&self) -> u16 {
        self.raw
    }
}

/// An actor slot. The complete legacy `u16` range, including 65535, is valid.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub struct ActorId(u16);

impl ActorId {
    #[must_use]
    pub const fn new(raw: u16) -> Self {
        Self(raw)
    }

    #[must_use]
    pub const fn raw(self) -> u16 {
        self.0
    }
}

/// An occupied item slot. Legacy value 65535 denotes no item and is not an ID.
///
/// Domain IDs are deliberately non-interchangeable:
///
/// ```compile_fail
/// use rcce_project::{ActorId, ItemId};
/// fn accepts_item(_: ItemId) {}
/// accepts_item(ActorId::new(7));
/// ```
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub struct ItemId(u16);

impl ItemId {
    pub const fn try_new(raw: u16) -> Result<Self, InvalidIdentity> {
        if raw == LEGACY_NONE {
            Err(InvalidIdentity {
                kind: IdentityKind::Item,
                raw,
            })
        } else {
            Ok(Self(raw))
        }
    }

    #[must_use]
    pub const fn from_legacy_reference(raw: u16) -> LegacyReference<Self> {
        if raw == LEGACY_NONE {
            LegacyReference::None
        } else {
            LegacyReference::Identity(Self(raw))
        }
    }

    #[must_use]
    pub const fn raw(self) -> u16 {
        self.0
    }
}

/// An occupied media-catalog slot. Legacy value 65535 denotes no media.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub struct MediaId(u16);

impl MediaId {
    pub const fn try_new(raw: u16) -> Result<Self, InvalidIdentity> {
        if raw == LEGACY_NONE {
            Err(InvalidIdentity {
                kind: IdentityKind::Media,
                raw,
            })
        } else {
            Ok(Self(raw))
        }
    }

    #[must_use]
    pub const fn from_legacy_reference(raw: u16) -> LegacyReference<Self> {
        if raw == LEGACY_NONE {
            LegacyReference::None
        } else {
            LegacyReference::Identity(Self(raw))
        }
    }

    #[must_use]
    pub const fn raw(self) -> u16 {
        self.0
    }
}

/// A legacy optional reference whose sentinel is not an identity.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LegacyReference<T> {
    None,
    Identity(T),
}

macro_rules! raw_name_identity {
    ($name:ident) => {
        #[derive(Debug, Clone)]
        pub struct $name(RawLegacyString);

        impl $name {
            #[must_use]
            pub const fn from_raw(raw: RawLegacyString) -> Self {
                Self(raw)
            }

            /// Return byte-authoritative identity evidence, not canonical text.
            #[must_use]
            pub const fn raw(&self) -> &RawLegacyString {
                &self.0
            }
        }

        impl PartialEq for $name {
            fn eq(&self, other: &Self) -> bool {
                self.raw().raw_bytes() == other.raw().raw_bytes()
            }
        }

        impl Eq for $name {}

        impl PartialOrd for $name {
            fn partial_cmp(&self, other: &Self) -> Option<std::cmp::Ordering> {
                Some(self.cmp(other))
            }
        }

        impl Ord for $name {
            fn cmp(&self, other: &Self) -> std::cmp::Ordering {
                self.raw().raw_bytes().cmp(other.raw().raw_bytes())
            }
        }

        impl Hash for $name {
            fn hash<H: Hasher>(&self, state: &mut H) {
                self.raw().raw_bytes().hash(state);
            }
        }
    };
}

raw_name_identity!(ZoneName);
raw_name_identity!(EmitterName);
raw_name_identity!(ScriptPath);
