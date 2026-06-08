//! Audio output: zone music (looped) via rodio. Mirrors the engine, which on
//! zone load does `LoadSound("Data\Music\" + GetMusicName(LoadingMusicID))` +
//! `LoopSound` + `PlaySound` (ClientAreas.bb:147-149).
//!
//! Construction is fallible and non-fatal: a machine with no audio device (or a
//! headless CI run) yields `None` and the client simply runs silently. The
//! music-id → filename lookup lives in [`crate::assets`] via `Music.dat`.

use std::fs::File;
use std::io::BufReader;
use std::path::Path;

use rodio::{Decoder, OutputStream, OutputStreamHandle, Sink, Source};

/// Effective linear gain for a sound: its base volume scaled by the master
/// volume, or 0 when muted. Pure so it's testable without an audio device.
pub fn effective_gain(master: f32, base: f32, muted: bool) -> f32 {
    if muted {
        0.0
    } else {
        (master.clamp(0.0, 1.0) * base).clamp(0.0, 1.0)
    }
}

/// Owns the output stream + the current music sink. Dropping it stops audio.
pub struct Audio {
    // Keep the stream alive for as long as we play; dropping it cuts output.
    _stream: OutputStream,
    handle: OutputStreamHandle,
    music: Option<Sink>,
    /// The music id currently playing, so a re-entered zone doesn't restart it.
    current_music: Option<u16>,
    /// Master volume (0..1) and mute, applied on top of each sound's base gain.
    master_volume: f32,
    muted: bool,
    /// Base volume of the current music track (pre-master), to re-derive the
    /// sink gain when master/mute change.
    music_base: f32,
    /// Looped weather-ambient layers keyed by name (e.g. "rain", "wind"), each
    /// with its pre-master base volume. Lets storm play rain + wind together.
    ambient: std::collections::HashMap<&'static str, (Sink, f32)>,
}

impl Audio {
    /// Open the default output device. `None` if there is no device (headless /
    /// no audio) — callers treat audio as optional.
    pub fn new() -> Option<Audio> {
        match OutputStream::try_default() {
            Ok((stream, handle)) => Some(Audio {
                _stream: stream,
                handle,
                music: None,
                current_music: None,
                master_volume: 0.8,
                muted: false,
                music_base: 0.0,
                ambient: std::collections::HashMap::new(),
            }),
            Err(e) => {
                eprintln!("[audio] no output device ({e}); running silent");
                None
            }
        }
    }

    /// Loop a music track at `path` (replaces any current track). `volume` is a
    /// linear gain (0..1). `id` tags the track so [`set_music`] can skip a
    /// redundant restart. Returns false (and logs) on open/decode failure.
    pub fn play_music_looped(&mut self, path: &Path, volume: f32, id: u16) -> bool {
        if let Some(s) = self.music.take() {
            s.stop();
        }
        let file = match File::open(path) {
            Ok(f) => f,
            Err(e) => {
                eprintln!("[audio] open {}: {e}", path.display());
                return false;
            }
        };
        let decoder = match Decoder::new(BufReader::new(file)) {
            Ok(d) => d,
            Err(e) => {
                eprintln!("[audio] decode {}: {e}", path.display());
                return false;
            }
        };
        let sink = match Sink::try_new(&self.handle) {
            Ok(s) => s,
            Err(e) => {
                eprintln!("[audio] sink: {e}");
                return false;
            }
        };
        self.music_base = volume;
        sink.set_volume(effective_gain(self.master_volume, volume, self.muted));
        // `.buffered()` makes the source `Clone`, which `repeat_infinite`
        // requires (a bare Decoder isn't restartable).
        sink.append(decoder.buffered().repeat_infinite());
        self.music = Some(sink);
        self.current_music = Some(id);
        true
    }

    /// Set the master volume (0..1) and re-apply it to the playing music.
    pub fn set_master_volume(&mut self, v: f32) {
        self.master_volume = v.clamp(0.0, 1.0);
        self.reapply_music_gain();
    }

    /// Nudge the master volume by `delta`, clamped to [0,1].
    pub fn adjust_master_volume(&mut self, delta: f32) {
        self.set_master_volume(self.master_volume + delta);
    }

    /// Toggle mute; returns the new muted state.
    pub fn toggle_mute(&mut self) -> bool {
        self.muted = !self.muted;
        self.reapply_music_gain();
        self.muted
    }

    pub fn master_volume(&self) -> f32 {
        self.master_volume
    }

    pub fn is_muted(&self) -> bool {
        self.muted
    }

    fn reapply_music_gain(&self) {
        if let Some(s) = &self.music {
            s.set_volume(effective_gain(self.master_volume, self.music_base, self.muted));
        }
        for (sink, base) in self.ambient.values() {
            sink.set_volume(effective_gain(self.master_volume, *base, self.muted));
        }
    }

    /// Start (or keep) a looping ambient layer under `key` (e.g. "rain", "wind").
    /// Re-calling with the same key is a no-op so the loop isn't restarted each
    /// frame. Open/decode failure leaves the layer absent.
    pub fn set_ambient_loop(&mut self, key: &'static str, path: &Path, volume: f32) {
        if self.ambient.contains_key(key) {
            return;
        }
        let Ok(file) = File::open(path) else {
            eprintln!("[audio] ambient open {}: missing", path.display());
            return;
        };
        let Ok(decoder) = Decoder::new(BufReader::new(file)) else {
            eprintln!("[audio] ambient decode {}", path.display());
            return;
        };
        let Ok(sink) = Sink::try_new(&self.handle) else { return };
        sink.set_volume(effective_gain(self.master_volume, volume, self.muted));
        sink.append(decoder.buffered().repeat_infinite());
        self.ambient.insert(key, (sink, volume));
    }

    /// Stop a single ambient layer by key, if present.
    pub fn stop_ambient(&mut self, key: &str) {
        if let Some((s, _)) = self.ambient.remove(key) {
            s.stop();
        }
    }

    /// Stop any ambient layer whose key isn't in `keep` (so weather changes drop
    /// stale loops). Pass `&[]` to stop everything.
    pub fn retain_ambient(&mut self, keep: &[&'static str]) {
        let drop: Vec<&'static str> =
            self.ambient.keys().copied().filter(|k| !keep.contains(k)).collect();
        for k in drop {
            self.stop_ambient(k);
        }
    }

    /// Whether an ambient layer is currently playing (for tests / UI).
    pub fn has_ambient(&self, key: &str) -> bool {
        self.ambient.contains_key(key)
    }

    /// Play the zone's music by id if it differs from what's already playing.
    /// `resolve` maps the id to an on-disk path (None = no track for that id).
    pub fn set_music<F>(&mut self, id: u16, volume: f32, resolve: F)
    where
        F: FnOnce(u16) -> Option<std::path::PathBuf>,
    {
        if id == 65535 || self.current_music == Some(id) {
            return;
        }
        if let Some(path) = resolve(id) {
            if self.play_music_looped(&path, volume, id) {
                println!("[audio] zone music #{id}: {}", path.display());
            }
        }
    }

    /// Stop and drop the current music track (rodio frees the sink on drop).
    /// Used on the menu→world transition so menu music doesn't bleed into a zone
    /// that ships no `LoadingMusicID` (MENU-10).
    pub fn stop_music(&mut self) {
        self.music = None;
        self.current_music = None;
    }

    /// Fire-and-forget a one-shot sound (footstep, UI blip). The sink detaches
    /// and frees itself when the clip finishes. Silently no-ops on failure.
    pub fn play_oneshot(&self, path: &Path, volume: f32) {
        let gain = effective_gain(self.master_volume, volume, self.muted);
        if gain <= 0.0 {
            return; // muted / zero — skip the decode entirely
        }
        let Ok(file) = File::open(path) else { return };
        let Ok(decoder) = Decoder::new(BufReader::new(file)) else { return };
        let Ok(sink) = Sink::try_new(&self.handle) else { return };
        sink.set_volume(gain);
        sink.append(decoder);
        sink.detach();
    }
}

/// Decides when the local player's footstep one-shot should fire, based on a
/// cadence that quickens when running. Pure (no audio device) so it's testable.
#[derive(Debug)]
pub struct FootstepTimer {
    last_step: f32,
    /// Advances each step so the caller can alternate between sound files.
    count: usize,
}

/// Seconds between footsteps at a walk / run.
pub const WALK_INTERVAL: f32 = 0.46;
pub const RUN_INTERVAL: f32 = 0.30;

impl Default for FootstepTimer {
    fn default() -> Self {
        FootstepTimer { last_step: f32::MIN, count: 0 }
    }
}

impl FootstepTimer {
    pub fn new() -> FootstepTimer {
        FootstepTimer::default()
    }

    /// Call once per frame. Returns `Some(step_index)` when a footstep should
    /// play (the index increments each step, for alternating sounds); `None`
    /// otherwise. Standing still never steps.
    pub fn tick(&mut self, now: f32, moving: bool, running: bool) -> Option<usize> {
        if !moving {
            return None;
        }
        let interval = if running { RUN_INTERVAL } else { WALK_INTERVAL };
        if now - self.last_step >= interval {
            self.last_step = now;
            let idx = self.count;
            self.count = self.count.wrapping_add(1);
            Some(idx)
        } else {
            None
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn gain_scales_by_master() {
        assert!((effective_gain(0.5, 0.8, false) - 0.4).abs() < 1e-6);
        assert_eq!(effective_gain(1.0, 0.6, false), 0.6);
    }

    #[test]
    fn gain_muted_is_zero() {
        assert_eq!(effective_gain(1.0, 1.0, true), 0.0);
        assert_eq!(effective_gain(0.5, 0.8, true), 0.0);
    }

    #[test]
    fn gain_clamps() {
        assert_eq!(effective_gain(2.0, 1.0, false), 1.0); // master clamped
        assert_eq!(effective_gain(1.0, 2.0, false), 1.0); // product clamped
        assert_eq!(effective_gain(-1.0, 0.5, false), 0.0); // negative master → 0
    }

    #[test]
    fn no_step_when_still() {
        let mut t = FootstepTimer::new();
        assert_eq!(t.tick(0.0, false, false), None);
        assert_eq!(t.tick(100.0, false, false), None);
    }

    #[test]
    fn first_move_steps_immediately_then_paces() {
        let mut t = FootstepTimer::new();
        // First moving frame fires (last_step starts at -inf).
        assert_eq!(t.tick(0.0, true, false), Some(0));
        // Too soon for the next.
        assert_eq!(t.tick(0.2, true, false), None);
        // After a walk interval, the next step (index advances).
        assert_eq!(t.tick(WALK_INTERVAL + 0.01, true, false), Some(1));
    }

    #[test]
    fn running_is_faster_than_walking() {
        let mut t = FootstepTimer::new();
        assert_eq!(t.tick(0.0, true, true), Some(0));
        // A gap that's enough for a run but not a walk only steps when running.
        assert_eq!(t.tick(RUN_INTERVAL + 0.01, true, false), None);
        assert_eq!(t.tick(RUN_INTERVAL + 0.01, true, true), Some(1));
    }
}
