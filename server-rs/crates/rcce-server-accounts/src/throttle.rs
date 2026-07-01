//! Per-source login-attempt throttle — parity with the `LoginAttempt` family in
//! `AccountsServer.bb`. Each connected peer (`FromID`) gets a recent-failure
//! count and a window start; crossing [`MAX_FAILURES`] failures within
//! [`WINDOW_MS`] refuses further attempts (the handler still replies with the
//! generic failure code so the throttle itself isn't an oracle). A success
//! resets that peer's counter.
//!
//! Unlike the Blitz version (which uses `MilliSecs()`, a wrapping signed clock),
//! this takes an explicit monotonically-increasing `now_ms` so it is
//! deterministically testable and immune to clock wrap.

use std::collections::HashMap;

/// `LoginAttemptMaxFailures`.
pub const MAX_FAILURES: u32 = 5;
/// `LoginAttemptWindowMs` (60 s).
pub const WINDOW_MS: u64 = 60_000;

#[derive(Clone, Copy, Debug)]
struct Entry {
    failures: u32,
    window_start_ms: u64,
}

/// Tracks recent failed login attempts per peer id.
#[derive(Debug, Default)]
pub struct LoginThrottle {
    entries: HashMap<u32, Entry>,
}

impl LoginThrottle {
    pub fn new() -> Self {
        Self::default()
    }

    /// `LoginAttemptOk%` — true if a fresh attempt from `peer` should be
    /// processed. False once the peer has tripped the failure threshold inside
    /// the current window.
    pub fn ok(&self, peer: u32, now_ms: u64) -> bool {
        match self.entries.get(&peer) {
            None => true,
            Some(e) => {
                // Window expired → allow (the next failure reopens it).
                now_ms.saturating_sub(e.window_start_ms) >= WINDOW_MS
                    || e.failures < MAX_FAILURES
            }
        }
    }

    /// `LoginAttemptRecord` — record an outcome. Success clears the peer's
    /// counter; failure increments it (opening a fresh window if the prior one
    /// closed).
    pub fn record(&mut self, peer: u32, success: bool, now_ms: u64) {
        if success {
            self.entries.remove(&peer);
            return;
        }
        match self.entries.get_mut(&peer) {
            None => {
                self.entries.insert(
                    peer,
                    Entry {
                        failures: 1,
                        window_start_ms: now_ms,
                    },
                );
            }
            Some(e) => {
                if now_ms.saturating_sub(e.window_start_ms) >= WINDOW_MS {
                    e.window_start_ms = now_ms;
                    e.failures = 1;
                } else {
                    e.failures += 1;
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fresh_peer_is_ok() {
        let t = LoginThrottle::new();
        assert!(t.ok(7, 0));
    }

    #[test]
    fn trips_after_max_failures() {
        let mut t = LoginThrottle::new();
        for i in 0..MAX_FAILURES {
            assert!(t.ok(7, 1000), "still ok before the {i}th failure");
            t.record(7, false, 1000);
        }
        // 5 failures inside the window → refused.
        assert!(!t.ok(7, 1000));
    }

    #[test]
    fn success_resets_counter() {
        let mut t = LoginThrottle::new();
        for _ in 0..MAX_FAILURES {
            t.record(7, false, 1000);
        }
        assert!(!t.ok(7, 1000));
        t.record(7, true, 1000);
        assert!(t.ok(7, 1000));
    }

    #[test]
    fn window_expiry_reopens() {
        let mut t = LoginThrottle::new();
        for _ in 0..MAX_FAILURES {
            t.record(7, false, 1000);
        }
        assert!(!t.ok(7, 1000));
        // Past the window → allowed again.
        assert!(t.ok(7, 1000 + WINDOW_MS));
        // A failure after expiry opens a fresh single-failure window.
        t.record(7, false, 1000 + WINDOW_MS);
        assert!(t.ok(7, 1000 + WINDOW_MS));
    }

    #[test]
    fn peers_are_independent() {
        let mut t = LoginThrottle::new();
        for _ in 0..MAX_FAILURES {
            t.record(1, false, 0);
        }
        assert!(!t.ok(1, 0));
        assert!(t.ok(2, 0));
    }
}
