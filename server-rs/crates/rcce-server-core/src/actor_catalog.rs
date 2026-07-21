//! Server-side actor template catalog — parity with `LoadActors`
//! (`Actors.bb:927-1012`). Parses `Data/Server Data/Actors.dat`.
//!
//! This is the **server** view of an actor race, distinct from the client's
//! `rcce-data::actors::ActorTemplate` (which only carries the appearance/mesh
//! fields the renderer needs). The server additionally needs `start_area` /
//! `start_portal` (where a new character of this race spawns), the base
//! attributes/resistances (copied into a new character), and the gameplay flags
//! (`playable`, `genders`, `default_faction`, …) that `P_CreateCharacter` and
//! the world simulation consume.

use std::collections::HashMap;

use crate::blitz_io::Reader;
use crate::character::Character;

/// One actor race/template as stored in `Actors.dat`.
#[derive(Clone, Debug, PartialEq)]
pub struct ActorTemplate {
    pub id: u16,
    pub race: String,
    pub class: String,
    pub description: String,
    pub start_area: String,
    pub start_portal: String,
    pub m_animation_set: i16,
    pub f_animation_set: i16,
    pub scale: f32,
    pub radius: f32,
    pub mesh_ids: [i16; 8],
    pub beard_ids: [i16; 5],
    pub male_hair_ids: [i16; 5],
    pub female_hair_ids: [i16; 5],
    pub male_face_ids: [i16; 5],
    pub female_face_ids: [i16; 5],
    pub male_body_ids: [i16; 5],
    pub female_body_ids: [i16; 5],
    pub m_speech_ids: [i16; 16],
    pub f_speech_ids: [i16; 16],
    pub blood_tex_id: i16,
    /// Base attribute values (40) — copied into a new character.
    pub attr_value: [i16; 40],
    /// Base attribute maximums (40).
    pub attr_maximum: [i16; 40],
    pub resistances: [i16; 20],
    pub genders: u8,
    pub playable: bool,
    pub rideable: bool,
    pub aggressiveness: u8,
    pub aggressive_range: i32,
    pub trade_mode: u8,
    pub environment: u8,
    pub inventory_slots: i32,
    pub default_damage_type: u8,
    pub default_faction: u8,
    pub xp_multiplier: i32,
    pub poly_collision: u8,
}

impl ActorTemplate {
    /// Build a fresh character from this race template — the copies
    /// `CreateActorInstance` makes (`Actors.bb:637+`): base attributes,
    /// resistances, home faction, and a forced gender for single-gender races
    /// (`Genders == 2`). The caller then overlays the player's chosen name,
    /// appearance, start position, gold, and reputation.
    pub fn new_character(&self) -> Character {
        let mut c = Character::blank();
        c.actor_id = self.id;
        c.attributes.value = self.attr_value.to_vec();
        c.attributes.maximum = self.attr_maximum.to_vec();
        c.resistances = self.resistances.to_vec();
        c.home_faction = self.default_faction;
        c.name = self.race.clone();
        // `If A\Actor\Genders = 2 Then A\Gender = 1` — a 2-here means the race
        // is female-only (the appearance arrays are gendered), so gender is
        // forced regardless of the client's byte.
        if self.genders == 2 {
            c.gender = 1;
        }
        c
    }
}

/// All actor templates, keyed by id.
#[derive(Clone, Debug, Default)]
pub struct ActorCatalog {
    pub templates: HashMap<u16, ActorTemplate>,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct RawSpan {
    pub start: usize,
    pub end: usize,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ActorRecordEvidence {
    pub id: u16,
    pub span: RawSpan,
    pub race_span: RawSpan,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ActorParseCompletion {
    Complete,
    Malformed { offset: usize },
    NegativeIdTerminator { offset: usize, raw: u16 },
}

#[derive(Clone, Debug)]
pub struct ActorParseEvidence {
    pub value: ActorCatalog,
    pub records: Vec<ActorRecordEvidence>,
    pub completion: ActorParseCompletion,
}

impl ActorCatalog {
    /// Parse the catalog from raw `Actors.dat` bytes. Stops at the first
    /// malformed record (a bad id or stream underflow), keeping whatever parsed
    /// cleanly — matching `LoadActors`'s `Exit`-on-bad-id behavior. Never panics.
    pub fn parse(data: &[u8]) -> Self {
        Self::parse_with_evidence(data).value
    }

    /// Parse with additive byte spans and the exact completion reason.
    pub fn parse_with_evidence(data: &[u8]) -> ActorParseEvidence {
        let mut r = Reader::new(data);
        let mut templates = HashMap::new();
        let mut records = Vec::new();
        let mut completion = ActorParseCompletion::Complete;
        while !r.at_end() {
            let start = r.pos();
            match read_template(&mut r) {
                TemplateRead::Record(t, race_span) => {
                    let t = *t;
                    records.push(ActorRecordEvidence {
                        id: t.id,
                        span: RawSpan {
                            start,
                            end: r.pos(),
                        },
                        race_span,
                    });
                    templates.insert(t.id, t);
                }
                TemplateRead::NegativeId(raw) => {
                    completion = ActorParseCompletion::NegativeIdTerminator { offset: start, raw };
                    break;
                }
                TemplateRead::Malformed => {
                    completion = ActorParseCompletion::Malformed { offset: start };
                    break;
                }
            }
        }
        ActorParseEvidence {
            value: Self { templates },
            records,
            completion,
        }
    }

    /// Load and parse the catalog from a file path. A missing file yields an
    /// empty catalog (so a misconfigured deploy degrades rather than crashes).
    pub fn load(path: impl AsRef<std::path::Path>) -> Self {
        match std::fs::read(path) {
            Ok(bytes) => Self::parse(&bytes),
            Err(_) => Self::default(),
        }
    }

    pub fn get(&self, id: u16) -> Option<&ActorTemplate> {
        self.templates.get(&id)
    }

    pub fn len(&self) -> usize {
        self.templates.len()
    }

    pub fn is_empty(&self) -> bool {
        self.templates.is_empty()
    }
}

fn read_i16_array<const N: usize>(r: &mut Reader) -> Option<[i16; N]> {
    let mut a = [0i16; N];
    for x in a.iter_mut() {
        *x = r.i16()?;
    }
    Some(a)
}

enum TemplateRead {
    Record(Box<ActorTemplate>, RawSpan),
    NegativeId(u16),
    Malformed,
}

fn read_template(r: &mut Reader) -> TemplateRead {
    let Some(id_raw) = r.i16() else {
        return TemplateRead::Malformed;
    };
    // `If A\ID < 0 … Exit` — a negative id ends the load (partial state kept).
    if id_raw < 0 {
        return TemplateRead::NegativeId(id_raw as u16);
    }
    let id = id_raw as u16;
    let race_prefix = r.pos();
    let Some(race) = r.string(256) else {
        return TemplateRead::Malformed;
    };
    let race_span = RawSpan {
        start: race_prefix + 4,
        end: r.pos(),
    };
    let Some(class) = r.string(256) else {
        return TemplateRead::Malformed;
    };
    let Some(description) = r.string(4096) else {
        return TemplateRead::Malformed;
    };
    let Some(start_area) = r.string(256) else {
        return TemplateRead::Malformed;
    };
    let Some(start_portal) = r.string(256) else {
        return TemplateRead::Malformed;
    };
    let Some(mut m_animation_set) = r.i16() else {
        return TemplateRead::Malformed;
    };
    let Some(mut f_animation_set) = r.i16() else {
        return TemplateRead::Malformed;
    };
    if !(0..=999).contains(&m_animation_set) {
        m_animation_set = 0;
    }
    if !(0..=999).contains(&f_animation_set) {
        f_animation_set = 0;
    }
    let parsed = (|| {
        let scale = r.f32()?;
        let radius = r.f32()?;
        let mesh_ids = read_i16_array::<8>(r)?;
        let beard_ids = read_i16_array::<5>(r)?;
        let male_hair_ids = read_i16_array::<5>(r)?;
        let female_hair_ids = read_i16_array::<5>(r)?;
        let male_face_ids = read_i16_array::<5>(r)?;
        let female_face_ids = read_i16_array::<5>(r)?;
        let male_body_ids = read_i16_array::<5>(r)?;
        let female_body_ids = read_i16_array::<5>(r)?;
        let m_speech_ids = read_i16_array::<16>(r)?;
        let f_speech_ids = read_i16_array::<16>(r)?;
        let blood_tex_id = r.i16()?;
        let mut attr_value = [0i16; 40];
        let mut attr_maximum = [0i16; 40];
        for i in 0..40 {
            attr_value[i] = r.i16()?;
            attr_maximum[i] = r.i16()?;
        }
        let resistances = read_i16_array::<20>(r)?;
        let genders = r.u8()?;
        let playable = r.u8()? != 0;
        let rideable = r.u8()? != 0;
        let aggressiveness = r.u8()?;
        let aggressive_range = r.i32()?;
        let trade_mode = r.u8()?;
        let environment = r.u8()?;
        let inventory_slots = r.i32()?;
        let default_damage_type = r.u8()?;
        let mut default_faction = r.u8()?;
        if default_faction > 99 {
            default_faction = 0;
        }
        let xp_multiplier = r.i32()?;
        let poly_collision = r.u8()?;

        Some(ActorTemplate {
            id,
            race,
            class,
            description,
            start_area,
            start_portal,
            m_animation_set,
            f_animation_set,
            scale,
            radius,
            mesh_ids,
            beard_ids,
            male_hair_ids,
            female_hair_ids,
            male_face_ids,
            female_face_ids,
            male_body_ids,
            female_body_ids,
            m_speech_ids,
            f_speech_ids,
            blood_tex_id,
            attr_value,
            attr_maximum,
            resistances,
            genders,
            playable,
            rideable,
            aggressiveness,
            aggressive_range,
            trade_mode,
            environment,
            inventory_slots,
            default_damage_type,
            default_faction,
            xp_multiplier,
            poly_collision,
        })
    })();
    match parsed {
        Some(template) => TemplateRead::Record(Box::new(template), race_span),
        None => TemplateRead::Malformed,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Path to the shipped catalog (repo root `data/`), relative to this crate.
    fn real_actors_dat() -> std::path::PathBuf {
        std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("../../../data/Server Data/Actors.dat")
    }

    #[test]
    fn parses_real_actors_dat() {
        let path = real_actors_dat();
        if !path.exists() {
            eprintln!("skipping: {path:?} not present");
            return;
        }
        let cat = ActorCatalog::load(&path);
        assert!(
            !cat.is_empty(),
            "shipped Actors.dat should yield >=1 template"
        );
        // Every template should have a non-empty race and parse cleanly; a
        // playable race should name a start area.
        for t in cat.templates.values() {
            assert!(!t.race.is_empty(), "template {} has empty race", t.id);
            if t.playable {
                assert!(
                    !t.start_area.is_empty(),
                    "playable race '{}' ({}) has no start area",
                    t.race,
                    t.id
                );
            }
        }
        eprintln!("parsed {} actor template(s)", cat.len());
    }

    #[test]
    fn empty_input_is_empty_catalog() {
        assert!(ActorCatalog::parse(&[]).is_empty());
    }

    #[test]
    fn truncated_record_stops_cleanly() {
        // A valid id then a truncated race string → no template, no panic.
        let mut bytes = Vec::new();
        bytes.extend_from_slice(&1i16.to_le_bytes());
        bytes.extend_from_slice(&100u32.to_le_bytes()); // race len 100, no data
        let cat = ActorCatalog::parse(&bytes);
        assert!(cat.is_empty());
    }
}
