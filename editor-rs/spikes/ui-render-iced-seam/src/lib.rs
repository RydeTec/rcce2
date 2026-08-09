//! Compile-only probe for the iced 0.13.1 / RCCE wgpu 22 ownership seam.

/// The crate intentionally contains no runtime adapter or second-device path.
pub const PROBE_SCOPE: &str = "one existing wgpu 22 device/queue; no bridge";
