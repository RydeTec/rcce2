//! Per-zone water volumes (ENV-4): the queryable state behind the translucent
//! scrolling water planes the renderer draws.
//!
//! Blitz ground truth: the water planes are loaded per zone from the area file
//! (`ClientAreas_FE.bb:704-785` — translucent textured plane at the authored
//! height) and their texture UV scrolls every frame (`Environment3D.bb:266-295`:
//! `U += Delta·0.00025`, `V += Delta·0.0007`, where `Delta = 30/fps` — i.e.
//! 30 Delta-units per second → 0.0075 U/s and 0.021 V/s).
//!
//! [`WaterVolumes`] holds the current zone's planes and answers the queries the
//! rest of the client needs:
//! - swim seating (drawn actors float at [`WaterVolumes::surface_y`], shipped),
//! - MOVE-8 destination rejection (a walking-only character can't be sent below
//!   [`WaterVolumes::surface_y`] — consumer, not implemented here),
//! - CAM-6 underwater camera ([`WaterVolumes::underwater_color`] says whether an
//!   eye point is submerged and which tint to murk the view with — consumer,
//!   not implemented here).

use rcce_data::WaterPlane;

/// U scroll per second — Blitz `W\U# + Delta#*0.00025` (Environment3D.bb:270)
/// at `Delta = 30/fps` ⇒ `30 × 0.00025` per second.
pub const SCROLL_U_PER_SEC: f32 = 30.0 * 0.00025;
/// V scroll per second — Blitz `W\V# + Delta#*0.0007` (Environment3D.bb:271)
/// at `Delta = 30/fps` ⇒ `30 × 0.0007` per second.
pub const SCROLL_V_PER_SEC: f32 = 30.0 * 0.0007;

/// Advance a water UV scroll offset by `dt` seconds at the Blitz drift rate,
/// wrapped to `[0, 1)` (the texture tiles, so only the fraction matters — the
/// wrap keeps the offset from losing float precision over long sessions).
pub fn advance_scroll(scroll: [f32; 2], dt: f32) -> [f32; 2] {
    [
        (scroll[0] + SCROLL_U_PER_SEC * dt).rem_euclid(1.0),
        (scroll[1] + SCROLL_V_PER_SEC * dt).rem_euclid(1.0),
    ]
}

/// The current zone's water planes, replaced wholesale on every zone load.
/// All queries treat each plane as an axis-aligned volume: the water fills
/// everything below the plane's surface Y inside its X/Z footprint
/// (`pos ± scale/2`), matching Blitz's `EntityBox`/`CameraUnderwater` tests.
#[derive(Debug, Default, Clone)]
pub struct WaterVolumes {
    pub planes: Vec<WaterPlane>,
}

impl WaterVolumes {
    /// No water (zone without planes, or water intentionally disabled).
    pub const EMPTY: WaterVolumes = WaterVolumes { planes: Vec::new() };

    /// Replace the volume set from a zone's parsed planes.
    pub fn set(&mut self, planes: impl IntoIterator<Item = WaterPlane>) {
        self.planes = planes.into_iter().collect();
    }

    pub fn is_empty(&self) -> bool {
        self.planes.is_empty()
    }

    /// The surface Y of the water plane whose X/Z footprint contains `(x, z)`,
    /// if any (first match wins). This is the swim line for seating, the
    /// "would sink" test for MOVE-8, and the surface CAM-6 compares the eye to.
    pub fn surface_y(&self, x: f32, z: f32) -> Option<f32> {
        self.plane_at(x, z).map(|w| w.pos[1])
    }

    /// The water plane whose X/Z footprint contains `(x, z)`, if any.
    pub fn plane_at(&self, x: f32, z: f32) -> Option<&WaterPlane> {
        self.planes.iter().find(|w| {
            (x - w.pos[0]).abs() < w.scale_x * 0.5 && (z - w.pos[2]).abs() < w.scale_z * 0.5
        })
    }

    /// Whether `pos` is inside a water volume — below a plane's surface and
    /// within its footprint (Blitz `CameraUnderwater`, Client.bb:895-914).
    pub fn contains(&self, pos: [f32; 3]) -> bool {
        self.plane_at(pos[0], pos[2]).is_some_and(|w| pos[1] < w.pos[1])
    }

    /// The water-tint colour if `eye` is underwater, else `None`. CAM-6's
    /// consumer tints fog + a full-screen wash to this and clamps the view
    /// distance, reproducing the murky submerged look.
    pub fn underwater_color(&self, eye: [f32; 3]) -> Option<[f32; 3]> {
        self.plane_at(eye[0], eye[2]).and_then(|w| (eye[1] < w.pos[1]).then_some(w.color))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn plane(pos: [f32; 3], sx: f32, sz: f32) -> WaterPlane {
        WaterPlane {
            tex_id: 331,
            tex_scale: 15.0,
            pos,
            scale_x: sx,
            scale_z: sz,
            color: [0.0, 0.0, 150.0 / 255.0],
            opacity: 0.68,
        }
    }

    // surface_y answers inside the footprint (regardless of probe height — it's
    // an X/Z query), misses outside it, and the first containing plane wins.
    #[test]
    fn surface_y_containment() {
        let mut w = WaterVolumes::default();
        w.set([plane([100.0, -5.0, 200.0], 40.0, 60.0), plane([100.0, -1.0, 200.0], 400.0, 400.0)]);
        assert_eq!(w.surface_y(100.0, 200.0), Some(-5.0), "first containing plane wins");
        assert_eq!(w.surface_y(119.0, 229.0), Some(-5.0), "inside the half-extent");
        assert_eq!(w.surface_y(121.0, 200.0), Some(-1.0), "outside plane 1 → falls to plane 2");
        assert_eq!(w.surface_y(1000.0, 1000.0), None, "dry land");
        assert_eq!(WaterVolumes::EMPTY.surface_y(100.0, 200.0), None, "no water at all");
    }

    // contains/underwater_color: below the surface inside the footprint only.
    #[test]
    fn underwater_below_surface_only() {
        let mut w = WaterVolumes::default();
        w.set([plane([0.0, 10.0, 0.0], 50.0, 50.0)]);
        assert!(w.contains([0.0, 9.0, 0.0]), "below the surface");
        assert!(!w.contains([0.0, 11.0, 0.0]), "above the surface");
        assert!(!w.contains([100.0, 9.0, 0.0]), "outside the footprint");
        assert_eq!(
            w.underwater_color([5.0, 0.0, -5.0]),
            Some([0.0, 0.0, 150.0 / 255.0]),
            "submerged eye gets the plane tint"
        );
        assert_eq!(w.underwater_color([5.0, 10.5, -5.0]), None, "surfaced eye gets no tint");
    }

    // The UV scroll advances with time at the Blitz rates and wraps to [0, 1).
    #[test]
    fn scroll_advances_with_time_and_wraps() {
        let s0 = [0.0, 0.0];
        let s1 = advance_scroll(s0, 1.0);
        assert!((s1[0] - 0.0075).abs() < 1e-6, "U drifts 30×0.00025/s (got {})", s1[0]);
        assert!((s1[1] - 0.021).abs() < 1e-6, "V drifts 30×0.0007/s (got {})", s1[1]);
        let s2 = advance_scroll(s1, 2.0);
        assert!(s2[0] > s1[0] && s2[1] > s1[1], "more time → more drift");
        // 200 s of V drift (0.021 × 200 = 4.2) wraps back into [0, 1).
        let sw = advance_scroll([0.0, 0.0], 200.0);
        assert!((0.0..1.0).contains(&sw[0]) && (0.0..1.0).contains(&sw[1]), "wrapped: {sw:?}");
        assert!((sw[1] - 0.2).abs() < 1e-4, "4.2 wraps to 0.2 (got {})", sw[1]);
    }
}
