//! Read-only RCCE compatibility-corpus validation.

mod error;
mod fs;
mod manifest;
mod registry;

pub use error::ScanError;
use std::path::Path;

/// One validated corpus project declaration.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ProjectReport {
    pub id: String,
    pub availability: String,
    pub files: u64,
    pub bytes: u64,
    pub state_classes: Vec<String>,
    pub canaries: u64,
}

/// Summary returned after validating one corpus manifest.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ScanReport {
    pub projects: Vec<ProjectReport>,
}

/// Validate schema, semantics, no-follow inventory, hashes, membership and
/// optional P07 canary occurrences without mutating the corpus.
pub fn scan_manifest(
    manifest: &Path,
    canary_registry: Option<&Path>,
) -> Result<ScanReport, ScanError> {
    let scan = manifest::scan(manifest, canary_registry)?;
    Ok(ScanReport {
        projects: scan
            .projects
            .into_iter()
            .map(|project| ProjectReport {
                id: project.id,
                availability: project.availability,
                files: project.files,
                bytes: project.bytes,
                state_classes: project.state_classes,
                canaries: project.canaries,
            })
            .collect(),
    })
}

/// Platform capability statement emitted by the CLI and tests.
#[must_use]
pub fn platform_capability() -> &'static str {
    #[cfg(target_os = "linux")]
    {
        "platform=linux;nofollow=descriptor-relative;mount-identity=required;hardlink-static=reject;hardlink-transient=detect-reject-no-publication"
    }
    #[cfg(all(unix, not(target_os = "linux")))]
    {
        "platform=unix-other;nofollow=descriptor-relative;mount-identity=unavailable;hardlink-static=reject;hardlink-transient=unavailable;scan=fail-closed"
    }
    #[cfg(windows)]
    {
        "platform=windows;nofollow=descriptor-relative-reparse-safe;volume-identity=required;hardlink-static=reject;hardlink-transient=unavailable"
    }
    #[cfg(not(any(unix, windows)))]
    {
        "platform=unsupported;nofollow=unavailable;hardlink-static=unavailable;hardlink-transient=unavailable;scan=fail-closed"
    }
}

/// Require the stronger capability that detects a transient hardlink before
/// publishing scan results. Windows has no change-time signal in this backend,
/// so it fails closed instead of presenting static-link rejection as parity.
pub fn require_transient_hardlink_detection() -> Result<(), ScanError> {
    #[cfg(target_os = "linux")]
    {
        Ok(())
    }
    #[cfg(not(target_os = "linux"))]
    {
        Err(ScanError::UnsupportedPlatform(
            "transient hardlink detection is unavailable on this backend",
        ))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn checked_in_placeholder_manifest_is_valid() {
        let manifest =
            Path::new(env!("CARGO_MANIFEST_DIR")).join("../../test-data/projects/manifest.toml");
        let report = scan_manifest(&manifest, None).expect("placeholder corpus must validate");
        assert_eq!(report.projects.len(), 3);
        assert!(report.projects.iter().all(|project| project.files == 0));
    }

    #[test]
    fn platform_capability_is_truthful_about_reparse_support() {
        let capability = platform_capability();
        #[cfg(target_os = "linux")]
        {
            eprintln!("SKIP: Windows junction/reparse fixture unavailable on Linux host");
            assert!(capability.contains("platform=linux"));
            assert!(capability.contains("hardlink-transient=detect-reject-no-publication"));
            assert!(require_transient_hardlink_detection().is_ok());
            assert_eq!(capability, "platform=linux;nofollow=descriptor-relative;mount-identity=required;hardlink-static=reject;hardlink-transient=detect-reject-no-publication");
        }
        #[cfg(all(unix, not(target_os = "linux")))]
        assert!(capability.contains("hardlink-transient=unavailable"));
        #[cfg(windows)]
        {
            assert!(capability.contains("nofollow=descriptor-relative-reparse-safe"));
            assert!(capability.contains("hardlink-transient=unavailable"));
            let error = require_transient_hardlink_detection().unwrap_err();
            assert!(error.to_string().contains("transient hardlink detection"));
            assert_eq!(capability, "platform=windows;nofollow=descriptor-relative-reparse-safe;volume-identity=required;hardlink-static=reject;hardlink-transient=unavailable");
        }
        #[cfg(not(any(unix, windows)))]
        assert!(capability.contains("platform=unsupported"));
    }

    #[test]
    fn capability_output_has_one_machine_testable_transient_hardlink_token() {
        let capability = platform_capability();
        assert_eq!(capability.matches("hardlink-transient=").count(), 1);
        assert!(capability.split(';').all(|field| field.contains('=')));
    }
}
