//! Explicit, descriptor-relative, read-only project-root capabilities.

mod backend;

pub use backend::{WalkBudget, WalkFile, WalkResult};
use std::fmt;
use std::path::{Path, PathBuf};
use unicode_normalization::UnicodeNormalization;

/// A validated, portable path relative to an already opened project root.
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct ProjectRelativePath {
    components: Vec<String>,
}

impl ProjectRelativePath {
    /// Validate an untrusted project path without touching the filesystem.
    pub fn parse(value: &str) -> Result<Self, RootError> {
        if value.is_empty()
            || value.starts_with('/')
            || value.starts_with('\\')
            || value.contains('\\')
        {
            return Err(RootError::new(RootErrorCode::InvalidPath));
        }
        let components = value.split('/').map(str::to_owned).collect::<Vec<_>>();
        if components.is_empty()
            || components
                .iter()
                .any(|component| validate_component(component).is_err())
        {
            return Err(RootError::new(RootErrorCode::InvalidPath));
        }
        Ok(Self { components })
    }

    /// Validate a host path representation as a project-relative path.
    pub fn from_path(value: &Path) -> Result<Self, RootError> {
        let value = value
            .to_str()
            .ok_or_else(|| RootError::new(RootErrorCode::InvalidPath))?;
        Self::parse(value)
    }

    fn first(&self) -> &str {
        &self.components[0]
    }

    fn remainder(&self) -> Option<String> {
        (self.components.len() > 1).then(|| self.components[1..].join("/"))
    }

    /// Return the validated portable spelling for diagnostics and identity.
    #[must_use]
    pub fn as_portable_str(&self) -> String {
        self.components.join("/")
    }
}

impl TryFrom<&str> for ProjectRelativePath {
    type Error = RootError;

    fn try_from(value: &str) -> Result<Self, Self::Error> {
        Self::parse(value)
    }
}

/// Opaque identity of the directory handle held by a [`ProjectRoot`].
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct RootIdentity {
    namespace: u64,
    object: u64,
}

/// Availability of an independently testable read assurance.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CapabilityAvailability {
    Available,
    Unavailable,
}

/// Truthful platform capabilities; baseline, transient detection, and strong
/// no-read authority are intentionally separate fields.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct RootCapabilities {
    pub baseline_quarantine: CapabilityAvailability,
    pub transient_race_detection: CapabilityAvailability,
    pub strong_no_read: CapabilityAvailability,
}

/// Assurance required before a read or walk begins.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ReadAssurance {
    BaselineQuarantine,
    TransientRaceDetection,
    StrongNoRead,
}

/// Ceiling for one accepted content read.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct ReadBudget {
    pub max_bytes: u64,
}

/// Bytes released only after the selected speculative gate's post-read checks.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AcceptedBytes(Vec<u8>);

impl AcceptedBytes {
    #[must_use]
    pub fn as_slice(&self) -> &[u8] {
        &self.0
    }

    #[must_use]
    pub fn into_vec(self) -> Vec<u8> {
        self.0
    }
}

/// An opened, read-only project-root capability.
pub struct ProjectRoot {
    backend: backend::Backend,
    identity: RootIdentity,
}

impl ProjectRoot {
    /// Open an explicitly selected absolute directory without following a link.
    pub fn open_explicit(path: &Path) -> Result<Self, RootError> {
        if !path.is_absolute() {
            return Err(RootError::new(RootErrorCode::InvalidRoot));
        }
        let backend = backend::Backend::open(path).map_err(RootError::from_backend)?;
        let (namespace, object) = backend.identity_tokens();
        Ok(Self {
            backend,
            identity: RootIdentity { namespace, object },
        })
    }

    #[must_use]
    pub fn identity(&self) -> RootIdentity {
        self.identity
    }

    #[must_use]
    pub fn capabilities(&self) -> RootCapabilities {
        RootCapabilities {
            baseline_quarantine: CapabilityAvailability::Available,
            transient_race_detection: if self.backend.transient_race_detection_available() {
                CapabilityAvailability::Available
            } else {
                CapabilityAvailability::Unavailable
            },
            strong_no_read: CapabilityAvailability::Unavailable,
        }
    }

    /// Inspect whether one validated root-level entry exists without following it.
    pub fn root_entry_exists(&self, path: &ProjectRelativePath) -> Result<bool, RootError> {
        if path.components.len() != 1 {
            return Err(RootError::new(RootErrorCode::InvalidPath));
        }
        self.backend
            .component_exists(path.first())
            .map_err(RootError::from_backend)
    }

    /// Read one validated file into quarantine and release it only after checks.
    pub fn read(
        &self,
        path: &ProjectRelativePath,
        budget: ReadBudget,
        assurance: ReadAssurance,
    ) -> Result<AcceptedBytes, RootError> {
        self.require_assurance(assurance)?;
        let bytes = match path.remainder() {
            Some(remainder) => {
                self.backend
                    .read_fixture_file(path.first(), &remainder, budget.max_bytes)
            }
            None => self.backend.read_component(path.first(), budget.max_bytes),
        }
        .map_err(RootError::from_backend)?;
        Ok(AcceptedBytes(bytes))
    }

    /// Walk and hash one validated root-level subtree under bounded quarantine.
    pub fn walk(
        &self,
        path: &ProjectRelativePath,
        budget: WalkBudget,
        assurance: ReadAssurance,
    ) -> Result<WalkResult, RootError> {
        self.require_assurance(assurance)?;
        if path.components.len() != 1 {
            return Err(RootError::new(RootErrorCode::InvalidPath));
        }
        self.backend
            .inventory(path.first(), budget)
            .map_err(RootError::from_backend)
    }

    /// Fail closed before content access when an assurance is unavailable for
    /// this opened root's backend and filesystem.
    pub fn require_assurance(&self, assurance: ReadAssurance) -> Result<(), RootError> {
        let capabilities = self.capabilities();
        let available = match assurance {
            ReadAssurance::BaselineQuarantine => capabilities.baseline_quarantine,
            ReadAssurance::TransientRaceDetection => capabilities.transient_race_detection,
            ReadAssurance::StrongNoRead => capabilities.strong_no_read,
        };
        if available == CapabilityAvailability::Available {
            Ok(())
        } else {
            Err(RootError::new(RootErrorCode::AssuranceUnavailable))
        }
    }
}

/// Stable non-echoing category for a root-capability failure.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RootErrorCode {
    InvalidRoot,
    InvalidPath,
    UnsupportedPlatform,
    AssuranceUnavailable,
    Io,
    UnsafeObject,
    ResourceLimit,
    Integrity,
}

/// A centralized non-echoing root-capability error.
#[derive(Debug)]
pub struct RootError {
    code: RootErrorCode,
}

impl RootError {
    fn new(code: RootErrorCode) -> Self {
        Self { code }
    }

    fn from_backend(error: BackendError) -> Self {
        let code = match error {
            BackendError::UnsupportedPlatform(_) => RootErrorCode::UnsupportedPlatform,
            BackendError::Io { .. } => RootErrorCode::Io,
            BackendError::Semantic(_) => RootErrorCode::InvalidPath,
            BackendError::UnsafeObject { .. } => RootErrorCode::UnsafeObject,
            BackendError::ResourceLimit { .. } => RootErrorCode::ResourceLimit,
            BackendError::Integrity { .. } => RootErrorCode::Integrity,
        };
        Self::new(code)
    }

    #[must_use]
    pub fn code(&self) -> RootErrorCode {
        self.code
    }
}

impl fmt::Display for RootError {
    fn fmt(&self, output: &mut fmt::Formatter<'_>) -> fmt::Result {
        let message = match self.code {
            RootErrorCode::InvalidRoot => "explicit project root rejected",
            RootErrorCode::InvalidPath => "project-relative path rejected",
            RootErrorCode::UnsupportedPlatform => "project-root backend unavailable",
            RootErrorCode::AssuranceUnavailable => "requested read assurance unavailable",
            RootErrorCode::Io => "project-root filesystem operation failed",
            RootErrorCode::UnsafeObject => "unsafe project filesystem object rejected",
            RootErrorCode::ResourceLimit => "project read resource ceiling exceeded",
            RootErrorCode::Integrity => "project read integrity check failed",
        };
        output.write_str(message)
    }
}

impl std::error::Error for RootError {}

fn validate_component(component: &str) -> Result<(), RootError> {
    if component.is_empty()
        || component == "."
        || component == ".."
        || component.contains('/')
        || component.contains('\\')
        || component.contains(':')
        || component.ends_with('.')
        || component.ends_with(' ')
        || component.bytes().any(|byte| byte < 0x20 || byte == 0x7f)
    {
        return Err(RootError::new(RootErrorCode::InvalidPath));
    }
    let base = component
        .split('.')
        .next()
        .unwrap_or(component)
        .to_ascii_uppercase();
    let reserved_numbered_device = ["COM", "LPT"].iter().any(|prefix| {
        base.strip_prefix(prefix).is_some_and(|suffix| {
            matches!(
                suffix,
                "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" | "¹" | "²" | "³"
            )
        })
    });
    let reserved =
        matches!(base.as_str(), "CON" | "PRN" | "AUX" | "NUL") || reserved_numbered_device;
    if reserved {
        return Err(RootError::new(RootErrorCode::InvalidPath));
    }
    let normalized = component.nfc().collect::<String>();
    if normalized.is_empty() {
        return Err(RootError::new(RootErrorCode::InvalidPath));
    }
    Ok(())
}

fn portable_alias_key(component: &str) -> String {
    component.nfc().collect::<String>().to_lowercase()
}

#[derive(Debug)]
enum BackendError {
    #[cfg_attr(windows, allow(dead_code))]
    UnsupportedPlatform(&'static str),
    Io {
        operation: &'static str,
        path: PathBuf,
        source: std::io::Error,
    },
    Semantic(String),
    UnsafeObject {
        path: String,
        reason: &'static str,
    },
    ResourceLimit {
        path: String,
        limit: &'static str,
    },
    Integrity {
        path: String,
        reason: &'static str,
    },
}

impl BackendError {
    fn io(
        operation: &'static str,
        path: impl Into<PathBuf>,
        source: impl Into<std::io::Error>,
    ) -> Self {
        Self::Io {
            operation,
            path: path.into(),
            source: source.into(),
        }
    }
}

impl fmt::Display for BackendError {
    fn fmt(&self, output: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::UnsupportedPlatform(message) => output.write_str(message),
            Self::Io {
                operation,
                path,
                source,
            } => write!(
                output,
                "{operation} failed for {}: {source}",
                path.display()
            ),
            Self::Semantic(message) => output.write_str(message),
            Self::UnsafeObject { path, reason } | Self::Integrity { path, reason } => {
                write!(output, "{reason} at {path}")
            }
            Self::ResourceLimit { path, limit } => write!(output, "{limit} at {path}"),
        }
    }
}

impl std::error::Error for BackendError {}
