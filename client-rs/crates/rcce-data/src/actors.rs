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

#[derive(Debug, Clone, Default)]
pub struct ActorTemplate {
    pub id: u16,
    pub race: String,
    /// Character-create "class" name (`Actors.dat` Class field). Multiple
    /// playable templates can share a `race` and differ only by `class` — the
    /// create screen's class picker cycles those (Blitz `BNextClass`/`BPrevClass`
    /// walk the Actor list for entries with the same `Race$`, MainMenu.bb:2565).
    pub class: String,
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
    /// Per-gender speech/voice sound ids (`MSpeechIDs`/`FSpeechIDs`, 16 slots
    /// each: Greet1/2, Bye1/2, Attack1/2, Hit1/2, RequestHelp, Death, Footstep×2,
    /// …). `65535` = no sound for that slot. Indexed by the `Speech_*` constants
    /// (Actors.bb:12-23). The client plays Attack/Hit/Death on combat events.
    pub male_speech: [u16; 16],
    pub female_speech: [u16; 16],
    /// Template gender mode: 0 = player-selectable (the P_NewActor wire then
    /// carries a gender byte), 1 = male-only, 2 = female-only.
    pub genders: u8,
    /// Whether this template is a playable race (offered in character create).
    pub playable: bool,
    /// AI hostility (`Actors.dat`): 0 = passive, 1 = defensive, 2 = always
    /// attacks (the proactive hunters), 3 = non-combatant. Drives the nameplate
    /// colour so a player can read an NPC's hostility at a glance (Blitz
    /// Actors3D.bb:546-559).
    pub aggressiveness: u8,
    /// Blood-spurt texture id (`Actors.dat` BloodTexID). When > 0, Blitz spawns a
    /// `Blood.rpc` particle emitter (textured with this id) at the actor on a
    /// connecting combat hit (ClientNet.bb:1136/1168). 0 = no blood for this race.
    pub blood_tex: i16,
    /// Per-attribute starting Value / Maximum for a fresh character of this
    /// template (`Actors.dat` `Attributes[40]` × (Value, Maximum), Actors.bb:480).
    /// Indexed by attribute slot 0..39. The create screen shows `attr_value[i]`
    /// as the base and clamps a point-spend increase at `attr_max[i]`
    /// (MainMenu.bb:2499 `(Value + PointSpends) < Maximum`). Both hold 40 entries
    /// after a successful parse (empty on a `Default` template).
    pub attr_value: Vec<i16>,
    pub attr_max: Vec<i16>,
    /// Locomotion environment (`Actors.dat` Environment, Actors.bb:26-29):
    /// [`environment::AMPHIBIOUS`] (0, can walk on land and swim underwater),
    /// [`environment::SWIM`] (1), [`environment::FLY`] (2), or
    /// [`environment::WALK`] (3, ground-only — blocked from entering water,
    /// MOVE-8). Drives the swim-anim (ANIM-4) and water-destination-rejection
    /// (MOVE-8) behaviours.
    pub environment: u8,
}

/// Locomotion environment values (`Actors.bb:26-29`).
pub mod environment {
    /// Walks on land, swims underwater (the default player mode). ANIM-4 swim
    /// anims apply to this mode when submerged (Client.bb:478 gates `Underwater`
    /// on `Environment_Amphibious`).
    pub const AMPHIBIOUS: u8 = 0;
    /// Water-only creature (fish).
    pub const SWIM: u8 = 1;
    /// Flying creature.
    pub const FLY: u8 = 2;
    /// Ground-only. MOVE-8 rejects a destination inside a water volume below its
    /// surface for this mode (Client.bb:1000 `If EType = Environment_Walk`).
    pub const WALK: u8 = 3;
}

#[derive(Debug, Default, Clone)]
pub struct ActorCatalog {
    pub templates: HashMap<u16, ActorTemplate>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct RawSpan {
    pub start: usize,
    pub end: usize,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ActorRecordEvidence {
    pub id: u16,
    pub span: RawSpan,
    pub race_span: RawSpan,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ActorParseCompletion {
    Complete,
    Truncated { offset: usize },
}

#[derive(Debug, Clone)]
pub struct ActorParseEvidence {
    pub value: ActorCatalog,
    pub records: Vec<ActorRecordEvidence>,
    pub completion: ActorParseCompletion,
}

impl ActorCatalog {
    pub fn parse(data: &[u8]) -> Result<ActorCatalog, ReadError> {
        Ok(Self::parse_with_evidence(data).value)
    }

    /// Parse while retaining additive byte-span and completion evidence.
    /// Existing callers keep the same tolerant [`Self::parse`] view.
    pub fn parse_with_evidence(data: &[u8]) -> ActorParseEvidence {
        let mut r = BlitzReader::new(data);
        let mut templates = HashMap::new();
        let mut records = Vec::new();
        let mut completion = ActorParseCompletion::Complete;
        // Records run until EOF; a parse error means we hit the tail/corruption.
        while !r.eof() {
            let start = r.position();
            match parse_record(&mut r) {
                Ok((t, race_span)) => {
                    records.push(ActorRecordEvidence {
                        id: t.id,
                        span: RawSpan {
                            start,
                            end: r.position(),
                        },
                        race_span,
                    });
                    templates.insert(t.id, t);
                }
                Err(_) => {
                    completion = ActorParseCompletion::Truncated { offset: start };
                    break;
                }
            }
        }
        ActorParseEvidence {
            value: ActorCatalog { templates },
            records,
            completion,
        }
    }

    /// Locomotion environment for a template `id`, or [`environment::AMPHIBIOUS`]
    /// (the default player mode, 0) when the template is unknown — the same
    /// soft-default the swim/anim code wants for an unresolved actor (never
    /// spuriously blocks movement or forces a swim clip). MOVE-8 / ANIM-4.
    pub fn environment_for(&self, id: u16) -> u8 {
        self.templates
            .get(&id)
            .map(|t| t.environment)
            .unwrap_or(environment::AMPHIBIOUS)
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

    /// Voice sound id for actor `id`'s `gender` (`0` male → `MSpeechIDs`, else
    /// female → `FSpeechIDs`, matching `Actors3D.bb:790`) at `Speech_*` `slot`.
    /// `None` if the template is unknown, the slot is out of range, or the slot is
    /// unset (`65535`). Mirrors `mesh_for`'s soft-fail.
    pub fn speech_id(&self, id: u16, gender: u8, slot: usize) -> Option<u16> {
        let t = self.templates.get(&id)?;
        let arr = if gender == 0 {
            &t.male_speech
        } else {
            &t.female_speech
        };
        arr.get(slot).copied().filter(|&s| s != 65535)
    }
}

/// `Speech_*` voice-slot indices into a template's speech arrays (Actors.bb:12-23).
pub mod speech {
    pub const ATTACK1: usize = 4;
    pub const ATTACK2: usize = 5;
    pub const HIT1: usize = 6;
    pub const HIT2: usize = 7;
    pub const DEATH: usize = 9;
}

fn parse_record(r: &mut BlitzReader) -> Result<(ActorTemplate, RawSpan), ReadError> {
    let id = r.read_short_u()?;
    let race_prefix = r.position();
    let race = r.read_string(256)?;
    let race_span = RawSpan {
        start: race_prefix + 4,
        end: r.position(),
    };
    let class = r.read_string(256)?;
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
    // Speech: MSpeech(16) + FSpeech(16) = 32 shorts (voice sound ids, 65535=none).
    let mut male_speech = [0u16; 16];
    for slot in &mut male_speech {
        *slot = r.read_short_u()?;
    }
    let mut female_speech = [0u16; 16];
    for slot in &mut female_speech {
        *slot = r.read_short_u()?;
    }
    let blood_tex = r.read_short()?;
    // Attributes[40] × (Value + Maximum) = 80 shorts (Value, then Maximum, per
    // slot — Actors.bb:344-345 write order). Kept for the create-screen
    // attribute-point spend (base value shown, increase clamped at Maximum).
    let mut attr_value = Vec::with_capacity(40);
    let mut attr_max = Vec::with_capacity(40);
    for _ in 0..40 {
        attr_value.push(r.read_short()?);
        attr_max.push(r.read_short()?);
    }
    // Resistances[20].
    for _ in 0..20 {
        r.read_short()?;
    }
    let genders = r.read_byte()?;
    let playable = r.read_byte()? != 0;
    let _rideable = r.read_byte()?;
    let aggressiveness = r.read_byte()?;
    let _aggressive_range = r.read_int()?;
    let _trade_mode = r.read_byte()?;
    let environment = r.read_byte()?;
    let _inventory_slots = r.read_int()?;
    let _default_damage_type = r.read_byte()?;
    let _default_faction = r.read_byte()?;
    let _xp_multiplier = r.read_int()?;
    let _poly_collision = r.read_byte()?;

    Ok((
        ActorTemplate {
            id,
            race,
            class,
            attr_value,
            attr_max,
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
            male_speech,
            female_speech,
            genders,
            playable,
            aggressiveness,
            blood_tex,
            environment,
        },
        race_span,
    ))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn speech_id_resolves_per_gender_and_soft_fails() {
        let mut t = ActorTemplate {
            id: 3,
            ..Default::default()
        };
        t.male_speech[speech::ATTACK1] = 100;
        t.male_speech[speech::DEATH] = 105;
        t.female_speech[speech::ATTACK1] = 200;
        t.female_speech[speech::HIT1] = 65535; // explicitly "no sound"
        let mut cat = ActorCatalog::default();
        cat.templates.insert(3, t);

        // gender 0 → male array; gender 1 (and anything non-zero) → female array.
        assert_eq!(cat.speech_id(3, 0, speech::ATTACK1), Some(100));
        assert_eq!(cat.speech_id(3, 0, speech::DEATH), Some(105));
        assert_eq!(cat.speech_id(3, 1, speech::ATTACK1), Some(200));
        // 65535 sentinel → None (no sound for that slot).
        assert_eq!(cat.speech_id(3, 1, speech::HIT1), None);
        // Unknown template → None; out-of-range slot → None (no panic).
        assert_eq!(cat.speech_id(99, 0, speech::ATTACK1), None);
        assert_eq!(cat.speech_id(3, 0, 999), None);
    }

    #[test]
    fn blood_tex_defaults_to_none() {
        // Captured from Actors.dat; default 0 = no blood (the gate is `> 0`).
        assert_eq!(ActorTemplate::default().blood_tex, 0);
        let t = ActorTemplate {
            id: 5,
            blood_tex: 42,
            ..Default::default()
        };
        assert_eq!(t.blood_tex, 42);
    }

    // MOVE-8 / ANIM-4: the locomotion environment resolves per template and
    // soft-defaults to AMPHIBIOUS (the player mode) for an unknown template, so
    // an unresolved actor never spuriously blocks movement or forces a swim clip.
    #[test]
    fn environment_resolves_and_defaults_amphibious() {
        let mut cat = ActorCatalog::default();
        cat.templates.insert(
            1,
            ActorTemplate {
                id: 1,
                environment: environment::WALK,
                ..Default::default()
            },
        );
        cat.templates.insert(
            2,
            ActorTemplate {
                id: 2,
                environment: environment::AMPHIBIOUS,
                ..Default::default()
            },
        );
        cat.templates.insert(
            3,
            ActorTemplate {
                id: 3,
                environment: environment::FLY,
                ..Default::default()
            },
        );
        assert_eq!(cat.environment_for(1), environment::WALK);
        assert_eq!(cat.environment_for(2), environment::AMPHIBIOUS);
        assert_eq!(cat.environment_for(3), environment::FLY);
        // Unknown template → AMPHIBIOUS (0), never WALK — an unresolved actor must
        // not be blocked from water (MOVE-8) as a side effect.
        assert_eq!(cat.environment_for(999), environment::AMPHIBIOUS);
        assert_eq!(environment::AMPHIBIOUS, 0);
    }
}
