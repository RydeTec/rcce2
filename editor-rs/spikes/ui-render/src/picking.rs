#[must_use]
pub fn expected_pick_id(viewport: usize, x: u32, width: u32) -> u32 {
    let base = (u32::try_from(viewport).unwrap_or(u32::MAX) + 1) * 100;
    base + u32::from(x >= width.max(1) / 2) + 1
}
