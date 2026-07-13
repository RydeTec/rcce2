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

/// CAM-6 underwater view distance — Blitz `FogNearNow# = 1.0` (Client.bb:905).
pub const UNDERWATER_FOG_NEAR: f32 = 1.0;
/// CAM-6 underwater view distance — Blitz `FogFarNow# = 50.0` (Client.bb:905).
pub const UNDERWATER_FOG_FAR: f32 = 50.0;

/// CAM-6: the fog/clear colour + near/far view distance for a camera submerged in
/// water tinted `water_color` (0..1 RGB). Exact Blitz parity (Client.bb:903-911):
/// `CameraClsColor`/`CameraFogColor` are set to the **raw** water RGB (no
/// multiplier) and `FogNear/Far` to `1/50`; the Sky/Stars/Cloud entities are
/// hidden (`WorldView::set_hide_sky(true)`, called by the consumer). Blitz draws
/// NO separate overlay — the fog + clear colour + hidden sky ARE the whole effect,
/// so the port relies on them alone (no full-screen wash).
pub fn underwater_fog(water_color: [f32; 3]) -> ([f32; 3], f32, f32) {
    (water_color, UNDERWATER_FOG_NEAR, UNDERWATER_FOG_FAR)
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

    /// Actor-underwater test (Blitz Client.bb:478): the actor at `(x, z)` sits
    /// more than 0.5 units below a water surface. Unlike [`contains`](Self::contains)
    /// (which the camera/destination checks use with no margin, matching
    /// `CameraUnderwater`/`SetDestination`), the actor body test needs the body
    /// clearly submerged before it switches to the swim clip — Blitz gates
    /// `AI\Underwater` on `EntityY#(CollisionEN) < EntityY#(W\EN) - 0.5`. Drives
    /// ANIM-4 swim-clip selection. Uses the actor's authoritative Y (the
    /// collision-pivot height), NOT the raised swim-seat render height.
    pub fn actor_submerged(&self, x: f32, z: f32, y: f32) -> bool {
        self.plane_at(x, z).is_some_and(|w| y < w.pos[1] - 0.5)
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

    // MOVE-8 (Blitz SetDestination, Client.bb:997-1011): a walking-only local
    // player can't set a destination inside a water volume below its surface. The
    // client's guard is `contains([dest_x, current_y, dest_z])`. Verify the three
    // acceptance cases: destination in water (below surface) → rejected; dry-land
    // destination → allowed; destination whose XZ is over water but at/above the
    // surface → allowed (you can stand on the shoreline at water level).
    #[test]
    fn move8_destination_rejection() {
        let mut w = WaterVolumes::default();
        w.set([plane([0.0, 5.0, 0.0], 100.0, 100.0)]); // surface Y = 5, 100×100 footprint
        // In-water destination (XZ inside, mover Y below surface) → rejected.
        assert!(w.contains([10.0, 0.0, 10.0]), "destination below surface inside footprint → reject");
        // Dry land (XZ outside the footprint) → allowed regardless of Y.
        assert!(!w.contains([200.0, 0.0, 200.0]), "off-footprint destination → allow");
        // Over the water footprint but at/above the surface → allowed (Blitz tests
        // `Y# < surface`; equal or above passes).
        assert!(!w.contains([10.0, 5.0, 10.0]), "at the surface → allow");
        assert!(!w.contains([10.0, 6.0, 10.0]), "above the surface → allow");
        // No water at all → never rejected.
        assert!(!WaterVolumes::EMPTY.contains([10.0, 0.0, 10.0]));
    }

    // ANIM-4 (Blitz Client.bb:478): the actor-underwater test that switches an
    // amphibious actor to the swim clip requires the body more than 0.5 below the
    // surface (a small margin so wading at the waterline doesn't flicker into the
    // swim clip). This is stricter than `contains` (which the camera/destination
    // use with no margin).
    #[test]
    fn anim4_actor_submerged_has_half_unit_margin() {
        let mut w = WaterVolumes::default();
        w.set([plane([0.0, 10.0, 0.0], 50.0, 50.0)]); // surface Y = 10
        // Clearly under → swim.
        assert!(w.actor_submerged(0.0, 0.0, 9.0), "1.0 below surface → submerged");
        // Within the 0.5 margin of the surface → NOT yet swimming (wading).
        assert!(!w.actor_submerged(0.0, 0.0, 9.6), "0.4 below surface → still wading");
        assert!(!w.actor_submerged(0.0, 0.0, 10.0), "at the surface → not submerged");
        // Outside the footprint → never submerged, whatever the Y.
        assert!(!w.actor_submerged(100.0, 0.0, 0.0), "off-footprint → not submerged");
        // `contains` (no margin) is looser than `actor_submerged` (0.5 margin):
        // just under the surface is "in the volume" for the camera but not yet a
        // swimmer for the body.
        assert!(w.contains([0.0, 9.6, 0.0]) && !w.actor_submerged(0.0, 0.0, 9.6));
    }

    // CAM-6: the underwater camera parameters — near/far clamp to Blitz's 1/50 and
    // the fog/clear are the RAW water colour (no multiplier — exact Blitz
    // `CameraClsColor/FogColor W\Red,W\Green,W\Blue`) — apply only when the eye is
    // submerged. Above the surface there's no tint (the caller keeps the zone fog).
    #[test]
    fn cam6_underwater_fog_values() {
        // The shipped Plains pond colour is rgb(0,0,150)/255 = (0,0,0.588…). The fog
        // must come back as EXACTLY that, not a darkened 0.7× of it.
        let plains = [0.0, 0.0, 150.0 / 255.0];
        let mut w = WaterVolumes::default();
        w.set([WaterPlane { color: plains, ..plane([0.0, 0.0, 0.0], 200.0, 200.0) }]);
        let wc = w.underwater_color([0.0, -3.0, 0.0]).expect("eye below surface is underwater");
        let (fog, near, far) = underwater_fog(wc);
        assert!((near - 1.0).abs() < 1e-6, "Blitz FogNear = 1 (got {near})");
        assert!((far - 50.0).abs() < 1e-6, "Blitz FogFar = 50 (got {far})");
        // RAW water RGB — no 0.7× (regression guard: (0,0,0.588) must NOT become
        // (0,0,0.412)). Exact equality: the mapping is the identity on colour.
        assert_eq!(fog, plains, "fog/clear = raw water RGB (no multiplier)");
        assert_eq!(fog, wc, "underwater_fog passes the water colour through untouched");
        // A second colour to pin down that every channel is untouched.
        let (fog2, ..) = underwater_fog([0.1, 0.3, 0.5]);
        assert_eq!(fog2, [0.1, 0.3, 0.5], "all channels raw, no scaling");
        // Surfaced eye → no tint (the render path leaves the zone fog + shows sky).
        assert_eq!(w.underwater_color([0.0, 1.0, 0.0]), None, "above the surface → no underwater tint");
        // The exported consts match the Blitz literals exactly.
        assert_eq!((UNDERWATER_FOG_NEAR, UNDERWATER_FOG_FAR), (1.0, 50.0));
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
