use sha2::{Digest, Sha256};

#[must_use]
pub fn hash_bytes(bytes: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(bytes);
    format!("{:x}", hasher.finalize())
}

/// Hashes only inputs compiled into `rcce-ui-render-spike`.
///
/// Tests, evidence, reports, matrix scripts, and the standalone provenance utility are excluded so
/// post-run adjudication cannot perturb the executable identity.
#[must_use]
pub fn executable_source_sha256() -> String {
    let mut hasher = Sha256::new();
    for (name, bytes) in [
        ("Cargo.toml", include_bytes!("../Cargo.toml").as_slice()),
        ("src/lib.rs", include_bytes!("lib.rs").as_slice()),
        ("src/main.rs", include_bytes!("main.rs").as_slice()),
        ("src/evidence.rs", include_bytes!("evidence.rs").as_slice()),
        ("src/harness.rs", include_bytes!("harness.rs").as_slice()),
        ("src/picking.rs", include_bytes!("picking.rs").as_slice()),
        (
            "src/provenance.rs",
            include_bytes!("provenance.rs").as_slice(),
        ),
        ("src/state.rs", include_bytes!("state.rs").as_slice()),
        ("src/pick.wgsl", include_bytes!("pick.wgsl").as_slice()),
    ] {
        hasher.update(name.as_bytes());
        hasher.update(bytes);
    }
    format!("{:x}", hasher.finalize())
}

#[must_use]
pub fn cargo_lock_sha256() -> String {
    hash_bytes(include_bytes!("../Cargo.lock"))
}
