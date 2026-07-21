//! Read-only RCCE compatibility-corpus validation.

mod error;
mod fs;
mod manifest;
mod registry;

pub use error::ScanError;
use rcce_project::ReadAssurance;
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
    report(scan)
}

/// Validate a corpus while retaining one transient-race-authorized root for
/// every project content read and walk in the scan.
pub fn scan_manifest_requiring_transient_hardlink_detection(
    manifest: &Path,
    canary_registry: Option<&Path>,
) -> Result<ScanReport, ScanError> {
    let scan = manifest::scan_with_assurance(
        manifest,
        canary_registry,
        ReadAssurance::TransientRaceDetection,
    )?;
    report(scan)
}

fn report(scan: manifest::ManifestScan) -> Result<ScanReport, ScanError> {
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

/// Opened-root capability statement emitted by the CLI and tests.
#[must_use = "the opened-root capability result must be inspected before content access"]
pub fn platform_capability(manifest: &Path) -> Result<String, ScanError> {
    let root = capability_root(manifest)?;
    let transient = match root.capabilities().transient_race_detection {
        rcce_project::CapabilityAvailability::Available => "detect-reject-no-publication",
        rcce_project::CapabilityAvailability::Unavailable => "unavailable",
    };
    #[cfg(target_os = "linux")]
    {
        Ok(format!("platform=linux;nofollow=descriptor-relative;mount-identity=required;hardlink-static=reject;hardlink-transient={transient}"))
    }
    #[cfg(all(unix, not(target_os = "linux")))]
    {
        Ok(format!("platform=unix-other;nofollow=descriptor-relative;mount-identity=unavailable;hardlink-static=reject;hardlink-transient={transient};scan=fail-closed"))
    }
    #[cfg(windows)]
    {
        Ok(format!("platform=windows;nofollow=descriptor-relative-reparse-safe;volume-identity=required;hardlink-static=reject;hardlink-transient={transient}"))
    }
    #[cfg(not(any(unix, windows)))]
    {
        Ok(format!("platform=unsupported;nofollow=unavailable;hardlink-static=unavailable;hardlink-transient={transient};scan=fail-closed"))
    }
}

fn capability_root(manifest: &Path) -> Result<fs::Root, ScanError> {
    let parent = manifest
        .parent()
        .filter(|path| !path.as_os_str().is_empty())
        .unwrap_or_else(|| Path::new("."));
    fs::Root::open(parent)
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
    fn strict_and_baseline_scans_follow_the_opened_root_capability() {
        let manifest =
            Path::new(env!("CARGO_MANIFEST_DIR")).join("../../test-data/projects/manifest.toml");
        let baseline = scan_manifest(&manifest, None).unwrap();
        let capability = platform_capability(&manifest).unwrap();
        let strict = scan_manifest_requiring_transient_hardlink_detection(&manifest, None);
        if capability.contains("hardlink-transient=detect-reject-no-publication") {
            assert_eq!(strict.unwrap(), baseline);
        } else {
            assert!(strict
                .unwrap_err()
                .to_string()
                .contains("transient hardlink detection"));
        }
    }

    #[test]
    fn platform_capability_is_truthful_about_reparse_support() {
        let manifest =
            Path::new(env!("CARGO_MANIFEST_DIR")).join("../../test-data/projects/manifest.toml");
        let capability = platform_capability(&manifest).unwrap();
        #[cfg(target_os = "linux")]
        {
            eprintln!("SKIP: Windows junction/reparse fixture unavailable on Linux host");
            assert!(capability.contains("platform=linux"));
            if capability.contains("hardlink-transient=detect-reject-no-publication") {
                assert!(
                    scan_manifest_requiring_transient_hardlink_detection(&manifest, None).is_ok()
                );
                assert_eq!(capability, "platform=linux;nofollow=descriptor-relative;mount-identity=required;hardlink-static=reject;hardlink-transient=detect-reject-no-publication");
            } else {
                assert!(capability.contains("hardlink-transient=unavailable"));
                let error = scan_manifest_requiring_transient_hardlink_detection(&manifest, None)
                    .unwrap_err();
                assert!(error.to_string().contains("transient hardlink detection"));
                assert_eq!(capability, "platform=linux;nofollow=descriptor-relative;mount-identity=required;hardlink-static=reject;hardlink-transient=unavailable");
            }
        }
        #[cfg(all(unix, not(target_os = "linux")))]
        assert!(capability.contains("hardlink-transient=unavailable"));
        #[cfg(windows)]
        {
            assert!(capability.contains("nofollow=descriptor-relative-reparse-safe"));
            assert!(capability.contains("hardlink-transient=unavailable"));
            let error =
                scan_manifest_requiring_transient_hardlink_detection(&manifest, None).unwrap_err();
            assert!(error.to_string().contains("transient hardlink detection"));
            assert_eq!(capability, "platform=windows;nofollow=descriptor-relative-reparse-safe;volume-identity=required;hardlink-static=reject;hardlink-transient=unavailable");
        }
        #[cfg(not(any(unix, windows)))]
        assert!(capability.contains("platform=unsupported"));
    }

    #[test]
    fn capability_output_has_one_machine_testable_transient_hardlink_token() {
        let manifest =
            Path::new(env!("CARGO_MANIFEST_DIR")).join("../../test-data/projects/manifest.toml");
        let capability = platform_capability(&manifest).unwrap();
        assert_eq!(capability.matches("hardlink-transient=").count(), 1);
        assert!(capability.split(';').all(|field| field.contains('=')));
    }

    #[cfg(target_os = "linux")]
    #[test]
    fn strict_requirement_rejects_an_unproved_filesystem_before_manifest_access() {
        let shared_memory = Path::new("/dev/shm");
        if !shared_memory.is_dir() {
            eprintln!("SKIP: /dev/shm is unavailable for an unproved-filesystem fixture");
            return;
        }
        let temp = tempfile::Builder::new()
            .prefix("rcce-unproved-filesystem-")
            .tempdir_in(shared_memory)
            .unwrap();
        let manifest = temp.path().join("manifest.toml");
        std::fs::create_dir(&manifest).unwrap();
        let capability = platform_capability(&manifest).unwrap();
        if capability.contains("hardlink-transient=detect-reject-no-publication") {
            eprintln!("SKIP: /dev/shm unexpectedly uses the proved filesystem family");
            return;
        }
        let error =
            scan_manifest_requiring_transient_hardlink_detection(&manifest, None).unwrap_err();
        assert!(error.to_string().contains("transient hardlink detection"));
        assert!(manifest.is_dir());
    }
}
