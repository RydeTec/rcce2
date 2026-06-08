//! Dump the parsed Animations.dat anim sets, and for each actor template show
//! which set it uses and that set's named clips. Verifies the anim-range table
//! and surfaces the idle/walk/etc. frame ranges.
//!
//!   cargo run -p rcce-client --bin anim_probe --release

use rcce_data::{ActorCatalog, AnimSetCatalog};

fn main() {
    let data_root = std::env::var("RCCE_DATA")
        .unwrap_or_else(|_| r"C:\Users\dyanr\Desktop\rcce2\data".to_string());
    let root = std::path::PathBuf::from(&data_root);

    let cat = AnimSetCatalog::parse(&std::fs::read(root.join("Game Data/Animations.dat")).unwrap())
        .unwrap();
    println!("Animations.dat: {} anim set(s)", cat.sets.len());
    let mut ids: Vec<_> = cat.sets.keys().copied().collect();
    ids.sort();
    for id in &ids {
        let s = &cat.sets[id];
        println!("\nset #{id} '{}' — {} clips:", s.name, s.clips.len());
        for c in &s.clips {
            println!("  {:<24} [{:>4}..{:>4}] speed {}", c.name, c.start, c.end, c.speed);
        }
    }

    if let Ok(ab) = std::fs::read(root.join("Server Data/Actors.dat")) {
        let actors = ActorCatalog::parse(&ab).unwrap();
        let mut t: Vec<_> = actors.templates.values().collect();
        t.sort_by_key(|t| t.id);
        println!("\n--- actor -> anim set ---");
        for a in t {
            let mset = cat.get(a.m_anim_set).map(|s| s.clips.len()).unwrap_or(0);
            println!(
                "#{:<3} '{}' male set {} ({} clips), female set {}",
                a.id, a.race, a.m_anim_set, mset, a.f_anim_set
            );
        }
    }
}
