//! Screen-space weather particles (rain / snow) driven by the zone's weather
//! byte. Pure update logic (no GPU) so the motion + wrapping is unit-testable;
//! the client draws each particle as a small overlay rect.
//!
//! Weather byte values (`Environment.bb`): 0 Sun, 1 Rain, 2 Snow, 3 Fog,
//! 4 Storm, 5 Wind. Storm rains too.

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Weather {
    Clear,
    Rain,
    Snow,
    /// Storm: rain particles + (in the client) wind loop and thunder one-shots.
    Storm,
}

/// Map the wire weather byte to a particle mode.
pub fn weather_from_byte(b: u8) -> Weather {
    match b {
        1 => Weather::Rain,
        4 => Weather::Storm,
        2 => Weather::Snow,
        _ => Weather::Clear, // Sun, Fog, Wind → no particles
    }
}

impl Weather {
    /// Whether this weather draws rain particles (Rain or Storm).
    pub fn is_rainy(self) -> bool {
        matches!(self, Weather::Rain | Weather::Storm)
    }
}

#[derive(Debug, Clone, Copy)]
pub struct Particle {
    pub x: f32,
    pub y: f32,
    /// Per-particle phase, for snow sway + size variety.
    pub phase: f32,
}

/// A pool of falling particles. Positions are in screen pixels; the pool
/// respreads when the viewport size changes.
#[derive(Debug, Default)]
pub struct WeatherSystem {
    particles: Vec<Particle>,
    rng: u32,
    last_w: f32,
    last_h: f32,
    time: f32,
}

impl WeatherSystem {
    pub fn new(count: usize) -> WeatherSystem {
        WeatherSystem {
            particles: vec![Particle { x: 0.0, y: 0.0, phase: 0.0 }; count],
            rng: 0x1234_5678,
            last_w: 0.0,
            last_h: 0.0,
            time: 0.0,
        }
    }

    /// Deterministic LCG in [0,1) — keeps tests reproducible and avoids an rng
    /// dependency.
    fn rand(&mut self) -> f32 {
        self.rng = self.rng.wrapping_mul(1_664_525).wrapping_add(1_013_904_223);
        (self.rng >> 8) as f32 / (1u32 << 24) as f32
    }

    fn respread(&mut self, w: f32, h: f32) {
        for i in 0..self.particles.len() {
            let (rx, ry, rp) = (self.rand(), self.rand(), self.rand());
            self.particles[i] = Particle { x: rx * w, y: ry * h, phase: rp * 6.283 };
        }
        self.last_w = w;
        self.last_h = h;
    }

    /// Advance the pool by `dt` seconds for `kind`, wrapping particles that
    /// leave the bottom/sides back into view. No-op for `Clear`.
    pub fn update(&mut self, dt: f32, w: f32, h: f32, kind: Weather) {
        if kind == Weather::Clear || w <= 0.0 || h <= 0.0 {
            return;
        }
        if (w - self.last_w).abs() > 0.5 || (h - self.last_h).abs() > 0.5 {
            self.respread(w, h);
        }
        self.time += dt;
        // Vertical fall speed + horizontal drift per mode.
        let (vy, drift) = match kind {
            Weather::Rain => (900.0, 140.0),
            // Storm rains harder with stronger wind-driven drift.
            Weather::Storm => (1050.0, 240.0),
            Weather::Snow => (130.0, 0.0),
            Weather::Clear => (0.0, 0.0),
        };
        let t = self.time;
        for p in &mut self.particles {
            p.y += vy * dt;
            p.x += match kind {
                Weather::Rain | Weather::Storm => drift * dt,
                // Snow sways side to side.
                Weather::Snow => (t * 1.5 + p.phase).sin() * 22.0 * dt,
                Weather::Clear => 0.0,
            };
            // Wrap vertically (off the bottom → back to the top).
            if p.y >= h {
                p.y -= h;
            }
            // Wrap horizontally both ways.
            if p.x >= w {
                p.x -= w;
            } else if p.x < 0.0 {
                p.x += w;
            }
        }
    }

    pub fn particles(&self) -> &[Particle] {
        &self.particles
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn byte_mapping() {
        assert_eq!(weather_from_byte(0), Weather::Clear);
        assert_eq!(weather_from_byte(1), Weather::Rain);
        assert_eq!(weather_from_byte(2), Weather::Snow);
        assert_eq!(weather_from_byte(3), Weather::Clear); // Fog: handled by the fog shader
        assert_eq!(weather_from_byte(4), Weather::Storm); // Storm
        assert_eq!(weather_from_byte(5), Weather::Clear); // Wind
        assert!(Weather::Rain.is_rainy() && Weather::Storm.is_rainy());
        assert!(!Weather::Snow.is_rainy() && !Weather::Clear.is_rainy());
    }

    #[test]
    fn storm_rains_harder_than_rain() {
        // Storm uses rain particles with a higher fall speed. Use a huge viewport
        // so no particle wraps in one step, making the comparison exact.
        let (w, h) = (100_000.0f32, 100_000.0f32);
        let mut a = WeatherSystem::new(30);
        let mut b = WeatherSystem::new(30);
        a.update(0.0, w, h, Weather::Rain); // respread (same seed in both)
        b.update(0.0, w, h, Weather::Storm);
        let y0: f32 = a.particles().iter().map(|p| p.y).sum();
        a.update(0.1, w, h, Weather::Rain);
        b.update(0.1, w, h, Weather::Storm);
        let da: f32 = a.particles().iter().map(|p| p.y).sum::<f32>() - y0;
        let db: f32 = b.particles().iter().map(|p| p.y).sum::<f32>() - y0;
        assert!(db > da, "storm should fall faster: storm {db} vs rain {da}");
    }

    #[test]
    fn clear_is_noop() {
        let mut ws = WeatherSystem::new(10);
        ws.update(0.1, 640.0, 480.0, Weather::Clear);
        // Untouched: all particles still at the origin.
        assert!(ws.particles().iter().all(|p| p.x == 0.0 && p.y == 0.0));
    }

    #[test]
    fn rain_falls_and_wraps() {
        let mut ws = WeatherSystem::new(50);
        ws.update(0.016, 640.0, 480.0, Weather::Rain); // first call respreads
        // Every particle is on-screen after a respread.
        assert!(ws.particles().iter().all(|p| (0.0..640.0).contains(&p.x) && (0.0..480.0).contains(&p.y)));
        // After a long run they stay wrapped in-bounds (never escape downward).
        for _ in 0..200 {
            ws.update(0.05, 640.0, 480.0, Weather::Rain);
        }
        assert!(ws.particles().iter().all(|p| p.y >= 0.0 && p.y < 480.0));
    }

    #[test]
    fn resize_respreads() {
        let mut ws = WeatherSystem::new(20);
        ws.update(0.016, 640.0, 480.0, Weather::Snow);
        ws.update(0.016, 1280.0, 720.0, Weather::Snow);
        assert!(ws.particles().iter().all(|p| p.x < 1280.0 && p.y < 720.0));
    }
}
