//! Probe: list actor templates whose Hair/Beard/Face/Body id arrays have any
//! non-empty (non-65535) slot, so we know which actors actually carry
//! configurable appearance in the shipped data.

use rcce_data::ActorCatalog;

fn main() {
    let data_root = std::env::var("RCCE_DATA")
        .unwrap_or_else(|_| r"C:\Users\dyanr\Desktop\rcce2\data".to_string());
    let bytes = std::fs::read(std::path::Path::new(&data_root).join("Server Data/Actors.dat"))
        .expect("Actors.dat");
    let cat = ActorCatalog::parse(&bytes).expect("parse");

    let nonempty = |a: &[u16; 5]| a.iter().filter(|&&v| v != 65535).count();
    let mut hair_any = 0;
    let mut beard_any = 0;
    let mut templ = cat.templates.values().collect::<Vec<_>>();
    templ.sort_by_key(|t| t.id);
    for t in templ {
        let mh = nonempty(&t.male_hair_ids);
        let fh = nonempty(&t.female_hair_ids);
        let bd = nonempty(&t.beard_ids);
        if mh + fh + bd > 0 {
            hair_any += (mh + fh > 0) as usize;
            beard_any += (bd > 0) as usize;
            println!(
                "#{:<3} '{}' genders={} maleHair={:?} femHair={:?} beard={:?}",
                t.id, t.race, t.genders, t.male_hair_ids, t.female_hair_ids, t.beard_ids
            );
        }
    }
    println!(
        "\n{} templates total; {} with hair, {} with beard",
        cat.templates.len(),
        hair_any,
        beard_any
    );
}
