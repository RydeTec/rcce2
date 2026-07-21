use std::path::PathBuf;
use std::{error::Error, fmt};

/// A scanner rejection. Errors intentionally contain paths and bounded metadata,
/// never file bodies or matched canary bytes.
pub enum ScanError {
    UnsupportedPlatform(&'static str),
    Io {
        operation: &'static str,
        path: PathBuf,
        source: std::io::Error,
    },
    ManifestToml(String),
    SchemaDefinition(String),
    SchemaValidation(String),
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
    Canary {
        id: String,
        reason: &'static str,
    },
}

impl fmt::Display for ScanError {
    fn fmt(&self, output: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::UnsupportedPlatform(message) => {
                write!(output, "platform unsupported: {}", Redacted(message))
            }
            Self::Io {
                operation,
                path,
                source,
            } => write!(
                output,
                "filesystem operation {} failed for {}: {}",
                Redacted(operation),
                Redacted(&path.display().to_string()),
                Redacted(&source.to_string())
            ),
            Self::ManifestToml(message) => {
                write!(output, "manifest TOML is invalid: {}", Redacted(message))
            }
            Self::SchemaDefinition(message) => {
                write!(output, "manifest schema is invalid: {}", Redacted(message))
            }
            Self::SchemaValidation(message) => write!(
                output,
                "manifest schema validation failed: {}",
                Redacted(message)
            ),
            Self::Semantic(message) => write!(
                output,
                "manifest semantic validation failed: {}",
                Redacted(message)
            ),
            Self::UnsafeObject { path, reason } => write!(
                output,
                "unsafe filesystem object at {}: {}",
                Redacted(path),
                Redacted(reason)
            ),
            Self::ResourceLimit { path, limit } => write!(
                output,
                "resource ceiling exceeded at {}: {}",
                Redacted(path),
                Redacted(limit)
            ),
            Self::Integrity { path, reason } => write!(
                output,
                "fixture integrity failed at {}: {}",
                Redacted(path),
                Redacted(reason)
            ),
            Self::Canary { id, reason } => write!(
                output,
                "canary validation failed for {}: {}",
                Redacted(id),
                Redacted(reason)
            ),
        }
    }
}

impl Error for ScanError {
    fn source(&self) -> Option<&(dyn Error + 'static)> {
        None
    }
}

impl fmt::Debug for ScanError {
    fn fmt(&self, output: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(output, "ScanError({self})")
    }
}

struct Redacted<'a>(&'a str);

impl fmt::Display for Redacted<'_> {
    fn fmt(&self, output: &mut fmt::Formatter<'_>) -> fmt::Result {
        if self.0.contains("RCCE_CANARY_V1__") {
            output.write_str("<redacted-marker-bearing-value>")
        } else {
            output.write_str(self.0)
        }
    }
}

impl ScanError {
    pub(crate) fn io(
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
