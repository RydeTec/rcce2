//! World environment — `Environment.dat` (`Environment.bb:85` `LoadEnvironment`)
//! plus the `CreateEnvironment` defaults (`Environment.bb:37`) used when the
//! file doesn't exist yet (a fresh project; the shipped `data/` has none).
//!
//! The Blitz server streams these values to a stock Blitz client inside the
//! `P_FetchActors` `"E"` block (`ServerNet.bb:2270-2284`), and its game clock
//! (`Year`/`Day`/`TimeH`/`TimeM`/`TimeFactor`) starts from them.
//!
//! Layout (all little-endian, strings = 4-byte-LE-length + bytes):
//! `Year i32 · Day i32 · TimeH i32 · TimeM i32 · TimeFactor i32`
//! then 12 × `[SeasonName str][SeasonStartDay i32][SeasonDuskH i32][SeasonDawnH i32]`
//! then 20 × `[MonthName str][MonthStartDay i32]`.
//!
//! Soft-fail posture: Blitz's `ReadInt`/`ReadString` return `0`/`""` past EOF,
//! so a truncated file yields zeros there — mirrored here with `unwrap_or`
//! defaults, then the same post-load clamps `LoadEnvironment` applies
//! (`TimeFactor < 1 → 10`, negative `Year`/`Day` → 0, out-of-range clock → 0).
//! Never panics.

use crate::blitz_io::Reader;

/// One of the 12 season slots (`Dim SeasonName$(11)` etc.).
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Season {
    pub name: String,
    pub start_day: i32,
    pub dusk_h: i32,
    pub dawn_h: i32,
}

/// One of the 20 month slots (`Dim MonthName$(19)` etc.).
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Month {
    pub name: String,
    pub start_day: i32,
}

/// The parsed `Environment.dat`: the boot game clock + season/month tables.
#[derive(Clone, Debug, PartialEq)]
pub struct Environment {
    pub year: i32,
    pub day: i32,
    pub time_h: i32,
    pub time_m: i32,
    pub time_factor: i32,
    pub seasons: Vec<Season>, // always 12
    pub months: Vec<Month>,   // always 20
}

impl Default for Environment {
    /// The `CreateEnvironment` defaults (`Environment.bb:37-68`): noon of day 1,
    /// year 1, TimeFactor 10; months "Month 1".."Month 20" starting every 28
    /// days; seasons "Season 1".."Season 12" starting every 84 days with dusk
    /// 18 / dawn 6 — **including** the quirk that `MonthStartDay(0)` and
    /// `SeasonStartDay(0)` are then overwritten with the year length (336),
    /// which is exactly what the Blitz server would send on a fresh project.
    fn default() -> Self {
        let year_length = 12 * 28; // months * monthLength
        let season_length = year_length / 4; // (months * monthLength) / seasons
        let mut months: Vec<Month> = (0..20)
            .map(|i| Month { name: format!("Month {}", i + 1), start_day: 28 * i })
            .collect();
        let mut seasons: Vec<Season> = (0..12)
            .map(|i| Season {
                name: format!("Season {}", i + 1),
                start_day: season_length * i,
                dusk_h: 18,
                dawn_h: 6,
            })
            .collect();
        months[0].start_day = year_length;
        seasons[0].start_day = year_length;
        Environment {
            year: 1,
            day: 1,
            time_h: 12,
            time_m: 0,
            time_factor: 10,
            seasons,
            months,
        }
    }
}

impl Environment {
    /// Parse `Environment.dat` bytes with `LoadEnvironment`'s exact read order
    /// and post-load clamps. Truncated streams read as zeros (Blitz EOF
    /// semantics), then get clamped.
    pub fn parse(data: &[u8]) -> Environment {
        let mut r = Reader::new(data);
        let mut env = Environment {
            year: r.i32().unwrap_or(0),
            day: r.i32().unwrap_or(0),
            time_h: r.i32().unwrap_or(0),
            time_m: r.i32().unwrap_or(0),
            time_factor: r.i32().unwrap_or(0),
            seasons: Vec::with_capacity(12),
            months: Vec::with_capacity(20),
        };
        for _ in 0..12 {
            env.seasons.push(Season {
                name: r.string(256).unwrap_or_default(),
                start_day: r.i32().unwrap_or(0),
                dusk_h: r.i32().unwrap_or(0),
                dawn_h: r.i32().unwrap_or(0),
            });
        }
        for _ in 0..20 {
            env.months.push(Month {
                name: r.string(256).unwrap_or_default(),
                start_day: r.i32().unwrap_or(0),
            });
        }
        // LoadEnvironment's clamps (Environment.bb:106-114).
        if env.time_factor < 1 {
            env.time_factor = 10;
        }
        if env.year < 0 {
            env.year = 0;
        }
        if env.day < 0 {
            env.day = 0;
        }
        if !(0..=23).contains(&env.time_h) {
            env.time_h = 0;
        }
        if !(0..=59).contains(&env.time_m) {
            env.time_m = 0;
        }
        env
    }

    /// Load from a path; a missing/unreadable file yields the
    /// `CreateEnvironment` defaults, exactly like `LoadEnvironment`'s
    /// `If F = 0 Then Return CreateEnvironment()` fallback (minus the
    /// file write-back — the port never writes to `data/`).
    pub fn load(path: impl AsRef<std::path::Path>) -> Environment {
        match std::fs::read(path) {
            Ok(bytes) => Self::parse(&bytes),
            Err(_) => Environment::default(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::blitz_io::Writer;

    #[test]
    fn defaults_match_create_environment() {
        let e = Environment::default();
        assert_eq!((e.year, e.day, e.time_h, e.time_m, e.time_factor), (1, 1, 12, 0, 10));
        assert_eq!(e.seasons.len(), 12);
        assert_eq!(e.months.len(), 20);
        // The CreateEnvironment slot-0 overwrite quirk.
        assert_eq!(e.months[0].start_day, 336);
        assert_eq!(e.seasons[0].start_day, 336);
        assert_eq!(e.months[1], Month { name: "Month 2".into(), start_day: 28 });
        assert_eq!(
            e.seasons[1],
            Season { name: "Season 2".into(), start_day: 84, dusk_h: 18, dawn_h: 6 }
        );
    }

    #[test]
    fn parse_round_trips_and_clamps() {
        let mut w = Writer::new();
        w.i32(5).i32(200).i32(99).i32(-3).i32(0); // TimeH/TimeM invalid, TimeFactor 0
        for i in 0..12 {
            w.string(&format!("S{i}")).i32(i * 10).i32(19).i32(7);
        }
        for i in 0..20 {
            w.string(&format!("M{i}")).i32(i * 5);
        }
        let e = Environment::parse(&w.into_bytes());
        assert_eq!((e.year, e.day), (5, 200));
        assert_eq!((e.time_h, e.time_m), (0, 0)); // clamped
        assert_eq!(e.time_factor, 10); // 0 → 10 (divide-by-zero guard)
        assert_eq!(e.seasons[3], Season { name: "S3".into(), start_day: 30, dusk_h: 19, dawn_h: 7 });
        assert_eq!(e.months[19], Month { name: "M19".into(), start_day: 95 });
        // Truncated file: zeros + clamps, never a panic.
        let t = Environment::parse(&[1, 0, 0]);
        assert_eq!(t.time_factor, 10);
        assert_eq!(t.seasons.len(), 12);
        assert_eq!(t.months.len(), 20);
    }
}
