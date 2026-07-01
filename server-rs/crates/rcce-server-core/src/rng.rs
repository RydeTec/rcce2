//! Small deterministic PRNG for server-side randomness (combat rolls, spawn
//! jitter, XP variance). Seeded once at boot from the OS RNG; xorshift64 is
//! plenty for game randomness (not cryptographic — passwords use the OS CSPRNG).
//!
//! `Rand(a, b)` in Blitz is **inclusive** on both ends, and `Rand(n)` means
//! `Rand(1, n)`; [`Rng::range`] / [`Rng::rand`] match that.

#[derive(Clone, Debug)]
pub struct Rng {
    state: u64,
}

impl Rng {
    /// Seed the generator. Any seed works (0 is remapped so the state is never
    /// the xorshift fixed point).
    pub fn new(seed: u64) -> Self {
        Self {
            state: seed ^ 0x9E37_79B9_7F4A_7C15 | 1,
        }
    }

    fn next_u64(&mut self) -> u64 {
        let mut x = self.state;
        x ^= x << 13;
        x ^= x >> 7;
        x ^= x << 17;
        self.state = x;
        x
    }

    /// `Rand(lo, hi)` — uniform inclusive integer in `[lo, hi]`. If `hi <= lo`,
    /// returns `lo`.
    pub fn range(&mut self, lo: i32, hi: i32) -> i32 {
        if hi <= lo {
            return lo;
        }
        let span = (hi - lo + 1) as u64;
        lo + (self.next_u64() % span) as i32
    }

    /// `Rand(n)` — uniform inclusive integer in `[1, n]`.
    pub fn rand(&mut self, n: i32) -> i32 {
        self.range(1, n)
    }

    /// `Rnd#(lo, hi)` — uniform float in `[lo, hi)`. Returns `lo` if `hi <= lo`.
    /// Used for sub-unit jitter (spawn scatter, wander destinations).
    pub fn frange(&mut self, lo: f32, hi: f32) -> f32 {
        if hi <= lo {
            return lo;
        }
        // 53-bit mantissa → uniform [0, 1).
        let u = (self.next_u64() >> 11) as f64 / (1u64 << 53) as f64;
        lo + (u as f32) * (hi - lo)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn range_is_inclusive_and_bounded() {
        let mut r = Rng::new(42);
        for _ in 0..10_000 {
            let v = r.range(5, 8);
            assert!((5..=8).contains(&v));
            let h = r.rand(100);
            assert!((1..=100).contains(&h));
        }
    }

    #[test]
    fn degenerate_range_returns_low() {
        let mut r = Rng::new(1);
        assert_eq!(r.range(7, 7), 7);
        assert_eq!(r.range(9, 3), 9);
    }

    #[test]
    fn frange_is_bounded_and_handles_degenerate() {
        let mut r = Rng::new(7);
        for _ in 0..10_000 {
            let v = r.frange(-3.0, 3.0);
            assert!((-3.0..3.0).contains(&v), "got {v}");
        }
        assert_eq!(r.frange(2.0, 2.0), 2.0);
        assert_eq!(r.frange(5.0, 1.0), 5.0);
    }

    #[test]
    fn deterministic_for_seed() {
        let mut a = Rng::new(123);
        let mut b = Rng::new(123);
        for _ in 0..100 {
            assert_eq!(a.rand(1000), b.rand(1000));
        }
    }
}
