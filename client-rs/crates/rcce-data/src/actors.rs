//! `Actors.dat` parser — actor templates (the server's `LoadActors`,
//! `Actors.bb`). Records are back-to-back with no length prefix, so the whole
//! record must be parsed to advance. We keep the fields needed to draw an actor
//! (id, race, scale, and the 8 mesh-id slots) and skip the rest.
//!
//! `mesh_ids[gender]` is the base body mesh (slot 0 = male, 1 = female; slots
//! 2..8 are gubbins/equipment). Resolve through the `Meshes.dat` catalog to a
//! `.b3d` path. `65535` means "no mesh".

use std::collections::HashMap;

use crate::reader::{BlitzReader, ReadError};

#[derive(Debug, Clone)]
pub struct ActorTemplate {
    pub id: u16,
    pub race: String,
    pub scale: f32,
    pub radius: f32,
    /// 0 = male base, 1 = female base, 2..8 = gubbins/equipment meshes.
    pub mesh_ids: [u16; 8],
    /// Animation-set ids (`Animations.dat`) for the named anim ranges, per
    /// gender. `mesh_for`'s gender selects which to use.
    pub m_anim_set: u16,
    pub f_anim_set: u16,
    /// Selectable hair/beard mesh-catalog ids (index by the character's
    /// Hair/Beard selection 0..4). `65535` = none. Beards are male-only.
    pub beard_ids: [u16; 5],
    pub male_hair_ids: [u16; 5],
    pub female_hair_ids: [u16; 5],
    /// Selectable face/body texture-catalog ids (index by the character's
    /// FaceTex/BodyTex selection 0..4), per gender.
    pub male_face_ids: [u16; 5],
    pub female_face_ids: [u16; 5],
    pub male_body_ids: [u16; 5],
    pub female_body_ids: [u16; 5],
    /// Template gender mode: 0 = player-selectable (the P_NewActor wire then
    /// carries a gender byte), 1 = male-only, 2 = female-only.
    pub genders: u8,
    /// Whether this template is a playable race (offered in character create).
    pub playable: bool,
}

#[derive(Debug, Default, Clone)]
pub struct ActorCatalog {
    pub templates: HashMap<u16, ActorTemplate>,
}

impl ActorCatalog {
    pub fn parse(data: &[u8]) -> Result<ActorCatalog, ReadError> {
        let mut r = BlitzReader::new(data);
        let mut templates = HashMap::new();
        // Records run until EOF; a parse error means we hit the tail/corruption.
        while !r.eof() {
            match parse_record(&mut r) {
                Ok(t) => {
                    templates.insert(t.id, t);
                }
                Err(_) => break,
            }
        }
        Ok(ActorCatalog { templates })
    }

    /// Base body mesh id for an actor of `id` with `gender` (0 male / 1 female).
    /// `None` if the template is unknown or the slot is empty (65535).
    pub fn mesh_for(&self, id: u16, gender: u8) -> Option<u16> {
        let t = self.templates.get(&id)?;
        let m = t.mesh_ids[(gender as usize).min(1)];
        if m == 65535 {
            None
        } else {
            Some(m)
        }
    }
}

fn parse_record(r: &mut BlitzReader) -> Result<ActorTemplate, ReadError> {
    let id = r.read_short_u()?;
    let race = r.read_string(256)?;
    let _class = r.read_string(256)?;
    let _description = r.read_string(4096)?;
    let _start_area = r.read_string(256)?;
    let _start_portal = r.read_string(256)?;
    let m_anim_set = r.read_short_u()?;
    let f_anim_set = r.read_short_u()?;
    let scale = r.read_float()?;
    let radius = r.read_float()?;

    let mut mesh_ids = [0u16; 8];
    for slot in &mut mesh_ids {
        *slot = r.read_short_u()?;
    }

    // Appearance id arrays (order per LoadActors): Beard(5), MaleHair(5),
    // FemHair(5), MaleFace(5), FemFace(5), MaleBody(5), FemBody(5).
    let read5 = |r: &mut BlitzReader| -> Result<[u16; 5], ReadError> {
        let mut a = [0u16; 5];
        for slot in &mut a {
            *slot = r.read_short_u()?;
        }
        Ok(a)
    };
    let beard_ids = read5(r)?;
    let male_hair_ids = read5(r)?;
    let female_hair_ids = read5(r)?;
    let male_face_ids = read5(r)?;
    let female_face_ids = read5(r)?;
    let male_body_ids = read5(r)?;
    let female_body_ids = read5(r)?;
    // Speech: MSpeech(16) + FSpeech(16) = 32 shorts.
    for _ in 0..32 {
        r.read_short()?;
    }
    let _blood_tex = r.read_short()?;
    // Attributes[40] × (Value + Maximum) = 80 shorts.
    for _ in 0..80 {
        r.read_short()?;
    }
    // Resistances[20].
    for _ in 0..20 {
        r.read_short()?;
    }
    let genders = r.read_byte()? as u8;
    let playable = r.read_byte()? != 0;
    let _rideable = r.read_byte()?;
    let _aggressiveness = r.read_byte()?;
    let _aggressive_range = r.read_int()?;
    let _trade_mode = r.read_byte()?;
    let _environment = r.read_byte()?;
    let _inventory_slots = r.read_int()?;
    let _default_damage_type = r.read_byte()?;
    let _default_faction = r.read_byte()?;
    let _xp_multiplier = r.read_int()?;
    let _poly_collision = r.read_byte()?;

    Ok(ActorTemplate {
        id,
        race,
        scale,
        radius,
        mesh_ids,
        m_anim_set,
        f_anim_set,
        beard_ids,
        male_hair_ids,
        female_hair_ids,
        male_face_ids,
        female_face_ids,
        male_body_ids,
        female_body_ids,
        genders,
        playable,
    })
}
