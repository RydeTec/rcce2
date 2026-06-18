//! `Projectiles.dat` (Server Data) — projectile definitions. The Rust **server**
//! needs each projectile's visual properties (mesh, emitters, homing, speed) to
//! broadcast `P_Projectile` (`=37`, `GameServer.bb:256`) when a spell script
//! calls `FireProjectile`. Mirrors `Projectiles.bb` `LoadProjectiles`.
//!
//! Per record: `id i16 · Name str(256) · MeshID i16 · Emitter1 str(256) ·
//! Emitter2 str(256) · Emitter1TexID i16 · Emitter2TexID i16 · Homing u8 ·
//! HitChance u8 · Damage i16 · DamageType i16 · Speed u8`.

use crate::reader::{BlitzReader, ReadError};

/// One projectile definition (visual fields the server broadcasts).
#[derive(Debug, Clone, PartialEq)]
pub struct ProjectileDef {
    pub id: u16,
    pub name: String,
    pub mesh_id: i16,
    pub emitter1: String,
    pub emitter2: String,
    pub emitter1_tex: i16,
    pub emitter2_tex: i16,
    pub homing: u8,
    pub speed: u8,
}

/// All projectile definitions from `Projectiles.dat`, in file order.
#[derive(Debug, Clone, Default)]
pub struct ProjectileCatalog {
    pub projectiles: Vec<ProjectileDef>,
}

impl ProjectileCatalog {
    /// Parse a whole `Projectiles.dat`, stopping at EOF or the first corrupt
    /// record (same posture as `LoadProjectiles`).
    pub fn parse(data: &[u8]) -> ProjectileCatalog {
        let mut r = BlitzReader::new(data);
        let mut projectiles = Vec::new();
        while !r.eof() {
            match Self::parse_record(&mut r) {
                Ok(p) => projectiles.push(p),
                Err(_) => break,
            }
        }
        ProjectileCatalog { projectiles }
    }

    fn parse_record(r: &mut BlitzReader) -> Result<ProjectileDef, ReadError> {
        let id = r.read_short()?;
        if id < 0 {
            return Err(ReadError::UnexpectedEof { offset: 0, needed: 0, available: 0 });
        }
        let name = r.read_string(256)?;
        let mesh_id = r.read_short()?;
        let emitter1 = r.read_string(256)?;
        let emitter2 = r.read_string(256)?;
        let emitter1_tex = r.read_short()?;
        let emitter2_tex = r.read_short()?;
        let homing = r.read_byte()?;
        let _hit_chance = r.read_byte()?;
        let _damage = r.read_short()?;
        let _damage_type = r.read_short()?;
        let speed = r.read_byte()?;
        Ok(ProjectileDef {
            id: id as u16,
            name,
            mesh_id,
            emitter1,
            emitter2,
            emitter1_tex,
            emitter2_tex,
            homing,
            speed,
        })
    }

    /// Look up a projectile by name (case-insensitive).
    pub fn get_by_name(&self, name: &str) -> Option<&ProjectileDef> {
        self.projectiles.iter().find(|p| p.name.eq_ignore_ascii_case(name))
    }
}
