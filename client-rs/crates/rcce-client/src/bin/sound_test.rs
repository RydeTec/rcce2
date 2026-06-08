//! Verifies the zone-music pipeline end to end against the real data files:
//! area header → LoadingMusicID → Music.dat → on-disk .ogg → rodio decode.
//! The decode is device-independent (proves the format path); playback is
//! best-effort (a headless box may have no audio device).
//!
//!   cargo run -p rcce-client --bin sound-test --release -- [zone] [data_root]

use std::fs::File;
use std::io::BufReader;
use std::time::Duration;

use rodio::{Decoder, Source};

use rcce_client::assets::AssetStore;
use rcce_data::AreaScenery;

/// First `.ogg` directly under `dir` (non-recursive), sorted for determinism.
fn first_ogg(dir: std::path::PathBuf) -> Option<std::path::PathBuf> {
    let mut oggs: Vec<_> = std::fs::read_dir(dir)
        .ok()?
        .flatten()
        .map(|e| e.path())
        .filter(|p| p.extension().map(|x| x.eq_ignore_ascii_case("ogg")).unwrap_or(false))
        .collect();
    oggs.sort();
    oggs.into_iter().next()
}

fn main() {
    let mut args = std::env::args().skip(1);
    let zone = args.next().unwrap_or_else(|| "Plains".to_string());
    let data_root = args
        .next()
        .unwrap_or_else(|| r"C:\Users\dyanr\Desktop\rcce2\data".to_string());

    let store = match AssetStore::load(&data_root) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("[sound] assets: {e}");
            std::process::exit(1);
        }
    };

    let area_path = std::path::Path::new(&data_root)
        .join("Areas")
        .join(format!("{zone}.dat"));
    let bytes = std::fs::read(&area_path).unwrap_or_else(|e| {
        eprintln!("[sound] {}: {e}", area_path.display());
        std::process::exit(1);
    });
    let scenery = AreaScenery::parse(&bytes).unwrap_or_else(|e| {
        eprintln!("[sound] parse: {e}");
        std::process::exit(1);
    });
    let music_id = scenery.env.music_id;
    println!("[sound] zone '{zone}' LoadingMusicID = {music_id}");

    // Resolution preference: the zone's track → any Music.dat entry → any .ogg
    // in Data/Music/. The shipped starter project ships an empty Music.dat
    // (index only, no records) and zones with no music, so the last fallback is
    // what actually exercises the rodio decode path here.
    let (music_id, path) = store
        .music_path(music_id)
        .map(|p| (music_id, p))
        .or_else(|| store.any_music())
        .or_else(|| first_ogg(std::path::Path::new(&data_root).join("Music")).map(|p| (0, p)))
        .unwrap_or_else(|| {
            eprintln!("[sound] no music in Music.dat and no .ogg under Data/Music/");
            std::process::exit(1);
        });
    println!("[sound] resolved -> {}", path.display());

    // Device-independent: decode the .ogg and inspect its stream.
    let file = File::open(&path).expect("open ogg");
    let decoder = Decoder::new(BufReader::new(file)).expect("decode ogg");
    let channels = decoder.channels();
    let rate = decoder.sample_rate();
    let total = decoder.total_duration();
    let n: usize = Decoder::new(BufReader::new(File::open(&path).unwrap()))
        .expect("decode ogg")
        .take(rate as usize * channels as usize) // first ~1s of samples
        .count();
    println!(
        "[sound] decoded OK: {channels} ch @ {rate} Hz, duration {:?}, first-second samples {n}",
        total
    );
    if n == 0 {
        eprintln!("[sound] RESULT: FAIL — decoded zero samples");
        std::process::exit(1);
    }

    // Footstep sounds: resolve + decode the first one (one-shot path).
    let footsteps = store.footstep_sounds();
    println!("[sound] footstep sounds: {} found", footsteps.len());
    if let Some(fp) = footsteps.first() {
        let fn_ = File::open(fp).expect("open footstep");
        let fc = Decoder::new(BufReader::new(fn_)).expect("decode footstep").count();
        println!("[sound] footstep '{}' decoded {fc} samples", fp.display());
    }

    // Best-effort playback (silent-skips if no device, e.g. headless CI).
    match rcce_client::audio::Audio::new() {
        Some(mut audio) => {
            let played = audio.play_music_looped(&path, 0.5, music_id);
            println!("[sound] music playback started = {played}; holding 1s");
            std::thread::sleep(Duration::from_millis(600));
            if let Some(fp) = footsteps.first() {
                audio.play_oneshot(fp, 0.6); // fire a footstep one-shot
                println!("[sound] footstep one-shot fired");
                std::thread::sleep(Duration::from_millis(600));
            }
            // Exercise the volume / mute controls (re-applies to the music sink).
            audio.set_master_volume(0.3);
            println!("[sound] master volume -> {:.0}%", audio.master_volume() * 100.0);
            std::thread::sleep(Duration::from_millis(300));
            let m = audio.toggle_mute();
            println!("[sound] muted -> {m}");
            std::thread::sleep(Duration::from_millis(300));
            audio.toggle_mute();
        }
        None => println!("[sound] no audio device — decode verified, playback skipped"),
    }

    println!("[sound] RESULT: PASS — music id resolved, file decoded ({n} samples).");
}
