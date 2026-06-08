//! Minimap / radar projection. Maps a world-space offset from the player into
//! a forward-up radar disc (the direction the camera faces points up), so the
//! render is a pure function of position + camera yaw — testable without a GPU.

/// Project a world delta `(dx, dz)` from the player into radar-disc offset
/// pixels `(ox, oy)` from the centre (+x right, +y down, screen convention),
/// rotated so the camera's forward points up. `range` is the world radius the
/// disc covers; `radius` its pixel radius. Returns `None` when the point falls
/// outside the disc.
///
/// The camera basis matches the movement code (`client_window.rs`: `fwd =
/// (-sin yaw, -cos yaw)`, `right = (-cos yaw, sin yaw)` — the basis where D
/// strafes screen-right). The lateral component must project onto that `right`,
/// not its negative, or the disc is mirrored left↔right (a target on the
/// player's left shows on the right).
pub fn world_to_radar(dx: f32, dz: f32, yaw: f32, range: f32, radius: f32) -> Option<(f32, f32)> {
    let (s, c) = yaw.sin_cos();
    // Screen-right component = (dx, dz) · right, with right = (-cos yaw, sin yaw).
    let along_right = -dx * c + dz * s;
    let along_fwd = -dx * s - dz * c;
    let scale = radius / range.max(1.0);
    let ox = along_right * scale;
    let oy = -along_fwd * scale; // forward → up → negative screen-y
    if ox * ox + oy * oy <= radius * radius {
        Some((ox, oy))
    } else {
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const R: f32 = 72.0;
    const RANGE: f32 = 140.0;

    fn approx(a: f32, b: f32) -> bool {
        (a - b).abs() < 0.5
    }

    #[test]
    fn forward_maps_up() {
        // yaw 0 faces -z (fwd = (0,-1)); a target at -z is "in front" → top.
        let (ox, oy) = world_to_radar(0.0, -20.0, 0.0, RANGE, R).unwrap();
        assert!(approx(ox, 0.0), "ox {ox}");
        assert!(oy < 0.0, "front should be up (negative y), got {oy}");
    }

    #[test]
    fn behind_maps_down() {
        let (_, oy) = world_to_radar(0.0, 20.0, 0.0, RANGE, R).unwrap();
        assert!(oy > 0.0, "behind should be down, got {oy}");
    }

    // Handedness: facing -z (yaw 0), the movement basis has screen-right =
    // (-cos,sin) = (-1,0), so world -x is to the player's RIGHT and world +x is
    // to the LEFT. A target front-and-left (world +x) must land on the left of
    // the disc (ox < 0). This is the regression the mirrored sign caused.
    #[test]
    fn left_maps_left() {
        let (ox, oy) = world_to_radar(20.0, -20.0, 0.0, RANGE, R).unwrap();
        assert!(ox < 0.0, "front-left (world +x) should be on the left, got ox {ox}");
        assert!(oy < 0.0, "front-left should still be up, got oy {oy}");
    }

    #[test]
    fn right_maps_right() {
        let (ox, _) = world_to_radar(-20.0, -20.0, 0.0, RANGE, R).unwrap();
        assert!(ox > 0.0, "front-right (world -x) should be on the right, got ox {ox}");
    }

    // The radar's screen-right must agree with the movement code's strafe-right
    // basis `right = (-cos yaw, sin yaw)`, at an arbitrary yaw — guards against
    // the two drifting apart again.
    #[test]
    fn agrees_with_movement_right_basis() {
        let yaw = 0.7f32;
        let (s, c) = yaw.sin_cos();
        let right = [-c, s]; // client_window.rs movement strafe-right
        // A target one unit along screen-right in the world:
        let (ox, _) = world_to_radar(right[0], right[1], yaw, RANGE, R).unwrap();
        assert!(ox > 0.0, "moving along the strafe-right basis should plot to the right, got {ox}");
    }

    #[test]
    fn yaw_rotates_the_disc() {
        // Same world target, rotate the camera 180°: front becomes back.
        let front = world_to_radar(0.0, -20.0, 0.0, RANGE, R).unwrap();
        let flipped = world_to_radar(0.0, -20.0, std::f32::consts::PI, RANGE, R).unwrap();
        assert!(front.1 < 0.0 && flipped.1 > 0.0, "{front:?} vs {flipped:?}");
    }

    #[test]
    fn out_of_range_is_none() {
        assert!(world_to_radar(1000.0, 0.0, 0.0, RANGE, R).is_none());
    }

    #[test]
    fn scales_with_distance() {
        let near = world_to_radar(0.0, -20.0, 0.0, RANGE, R).unwrap();
        let far = world_to_radar(0.0, -40.0, 0.0, RANGE, R).unwrap();
        assert!(far.1 < near.1, "farther in front sits higher (more negative y)");
    }
}
