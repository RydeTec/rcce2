//! Local day/night cycle. A purely cosmetic time-of-day phase modulates the
//! zone's sky/fog/ambient colours (the engine has no time-of-day packet, so
//! this is client-side only). Pure functions so the colour math is testable;
//! the gradient sky derives its zenith from the modulated fog colour, so it
//! darkens automatically.

use std::f32::consts::TAU;

/// Multiplicative sky modulation for a given phase: an overall `brightness`
/// (night is dim, noon full) and an RGB `tint` (warm at dawn/dusk, blue at
/// deep night, neutral at noon).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Sky {
    pub brightness: f32,
    pub tint: [f32; 3],
}

/// Compute the sky modulation for `phase` in [0,1): 0 = midnight, 0.25 = dawn,
/// 0.5 = noon, 0.75 = dusk. Wraps, so values outside [0,1) are fine.
pub fn daynight(phase: f32) -> Sky {
    let sun = ((phase.rem_euclid(1.0) - 0.25) * TAU).sin(); // -1 midnight .. +1 noon
    let day = (sun * 0.5 + 0.5).clamp(0.0, 1.0); // 0 night .. 1 day
    let brightness = 0.22 + 0.78 * day; // keep a night floor
    // Horizon glow (dawn/dusk) peaks as the sun crosses the horizon.
    let glow = (1.0 - sun.abs() / 0.35).clamp(0.0, 1.0);
    let night = (1.0 - day) * (1.0 - glow); // deep-night weight
    let tint = [
        (1.0 + 0.25 * glow - 0.30 * night).clamp(0.0, 2.0),
        (1.0 - 0.05 * glow - 0.12 * night).clamp(0.0, 2.0),
        (1.0 - 0.30 * glow + 0.28 * night).clamp(0.0, 2.0),
    ];
    Sky { brightness, tint }
}

/// Apply the sky modulation to a base colour (clamped to [0,1]).
pub fn modulate(color: [f32; 3], sky: &Sky) -> [f32; 3] {
    [
        (color[0] * sky.brightness * sky.tint[0]).clamp(0.0, 1.0),
        (color[1] * sky.brightness * sky.tint[1]).clamp(0.0, 1.0),
        (color[2] * sky.brightness * sky.tint[2]).clamp(0.0, 1.0),
    ]
}

/// Phase from elapsed seconds for a full cycle of `cycle_secs`.
pub fn phase_at(elapsed_secs: f32, cycle_secs: f32) -> f32 {
    (elapsed_secs / cycle_secs.max(1.0)).rem_euclid(1.0)
}

/// Sun direction (pointing TOWARD the sun, matching the renderer's `light_dir`
/// convention) for a phase, so shadows rotate and lengthen across the day instead
/// of staying fixed. Noon → overhead (short shadows); dawn → low in the east; dusk
/// → low in the west; night → kept just above the horizon (the scene is dark then
/// regardless, and this keeps the shadow projection well-defined).
pub fn sun_dir(phase: f32) -> [f32; 3] {
    let a = (phase.rem_euclid(1.0) - 0.25) * TAU; // 0 at dawn, π/2 noon, π dusk
    let elev = a.sin(); // -1 midnight .. +1 noon
    let horiz = a.cos(); // +1 dawn (east) .. 0 noon .. -1 dusk (west)
    let v = [horiz, elev.max(0.12), 0.35];
    let len = (v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).sqrt().max(1e-4);
    [v[0] / len, v[1] / len, v[2] / len]
}

/// How visible the night-sky stars are at `phase` (0 by day, 1 at deep night).
/// Stars fade in only once it's fairly dark and peak at midnight.
pub fn night_factor(phase: f32) -> f32 {
    let sun = ((phase.rem_euclid(1.0) - 0.25) * TAU).sin();
    let day = (sun * 0.5 + 0.5).clamp(0.0, 1.0); // 0 night .. 1 day
    ((0.45 - day) / 0.45).clamp(0.0, 1.0)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sun_moves_across_the_sky() {
        let noon = sun_dir(0.5);
        let dawn = sun_dir(0.25);
        let dusk = sun_dir(0.75);
        // Noon highest; dawn/dusk low.
        assert!(noon[1] > dawn[1] && noon[1] > dusk[1], "noon should be highest: {noon:?}");
        // Sun crosses east → west: dawn +x, dusk -x, noon ~overhead (x≈0).
        assert!(dawn[0] > 0.5, "dawn should be east (+x): {dawn:?}");
        assert!(dusk[0] < -0.5, "dusk should be west (-x): {dusk:?}");
        assert!(noon[0].abs() < 0.2, "noon should be ~overhead: {noon:?}");
        // Always normalised + never below the horizon.
        for p in [0.0_f32, 0.25, 0.5, 0.75] {
            let d = sun_dir(p);
            assert!((d[0] * d[0] + d[1] * d[1] + d[2] * d[2] - 1.0).abs() < 1e-3);
            assert!(d[1] > 0.0, "sun above horizon at {p}: {d:?}");
        }
    }

    #[test]
    fn noon_is_bright_and_neutral() {
        let s = daynight(0.5);
        assert!(s.brightness > 0.95, "noon brightness {}", s.brightness);
        // ~neutral tint.
        assert!(s.tint.iter().all(|c| (c - 1.0).abs() < 0.1), "tint {:?}", s.tint);
    }

    #[test]
    fn midnight_is_dim_and_blue() {
        let s = daynight(0.0);
        assert!(s.brightness < 0.3, "midnight brightness {}", s.brightness);
        // Blue dominates red at night.
        assert!(s.tint[2] > s.tint[0], "should be blue: {:?}", s.tint);
    }

    #[test]
    fn dawn_is_warm() {
        let s = daynight(0.25);
        // Warm: red boosted above blue.
        assert!(s.tint[0] > s.tint[2], "dawn should be warm: {:?}", s.tint);
        // Mid brightness — between night and noon.
        assert!(s.brightness > 0.4 && s.brightness < 0.85, "dawn brightness {}", s.brightness);
    }

    #[test]
    fn brightness_orders_night_dawn_noon() {
        assert!(daynight(0.0).brightness < daynight(0.25).brightness);
        assert!(daynight(0.25).brightness < daynight(0.5).brightness);
    }

    #[test]
    fn modulate_darkens_at_night() {
        let base = [0.5, 0.6, 0.8];
        let night = modulate(base, &daynight(0.0));
        let noon = modulate(base, &daynight(0.5));
        assert!(night.iter().sum::<f32>() < noon.iter().sum::<f32>());
    }

    #[test]
    fn stars_only_at_night() {
        assert!(night_factor(0.0) > 0.9, "midnight should be starry: {}", night_factor(0.0));
        assert_eq!(night_factor(0.5), 0.0, "no stars at noon");
        assert_eq!(night_factor(0.25), 0.0, "no stars at dawn");
        // Monotonic into deep night.
        assert!(night_factor(0.05) < night_factor(0.0));
    }

    #[test]
    fn phase_wraps() {
        assert!((phase_at(0.0, 100.0) - 0.0).abs() < 1e-6);
        assert!((phase_at(50.0, 100.0) - 0.5).abs() < 1e-6);
        assert!((phase_at(150.0, 100.0) - 0.5).abs() < 1e-6); // wrapped
    }
}
