//! RealmCrafter particle emitter config (`Data/Emitter Configs/<name>.rpc`).
//!
//! Binary, decoded field-for-field by `RP_LoadEmitterConfig` (RottParticles.bb
//! :1179). Drives the particle simulation: spawn shape, initial velocity + random
//! spread, constant + accelerating force, scale/alpha/colour change over the
//! lifespan, texture-tile animation, and the Blitz blend mode.

use crate::reader::{BlitzReader, ReadError};

/// Emission shape (`RP_Sphere`/`Cylinder`/`Box`).
pub mod shape {
    pub const SPHERE: i32 = 1;
    pub const CYLINDER: i32 = 2;
    pub const BOX: i32 = 3;
}

/// Velocity mode (`RP_Normal`/`ShapeBased`/`HeavilyShapeBased`).
pub mod vmode {
    pub const SHAPE_BASED: i32 = 2;
    pub const HEAVILY_SHAPE_BASED: i32 = 3;
}

#[derive(Debug, Clone, Default)]
pub struct EmitterConfig {
    pub max_particles: i32,
    pub particles_per_frame: i32,
    pub tex_across: i32,
    pub tex_down: i32,
    /// Start each particle on a random texture tile.
    pub rnd_start_frame: i32,
    /// Frames between texture-tile advances.
    pub tex_anim_speed: i32,
    /// 1 normal · 2 shape-based · 3 heavily-shape-based initial velocity.
    pub v_shape_based: i32,
    pub velocity: [f32; 3],
    pub velocity_rnd: [f32; 3],
    pub force: [f32; 3],
    pub scale_start: f32,
    pub scale_change: f32,
    pub lifespan: i32,
    pub alpha_start: f32,
    pub alpha_change: f32,
    /// Blitz `EntityBlend`: 1 alpha · 2 multiply · 3 additive.
    pub blend_mode: i32,
    /// 1 sphere · 2 cylinder · 3 box.
    pub shape: i32,
    pub min_radius: f32,
    pub max_radius: f32,
    pub width: f32,
    pub height: f32,
    pub depth: f32,
    pub shape_axis: i32,
    pub force_mod: [f32; 3],
    /// 1 linear (force += force_mod) · 2 spherical (force vector rotates).
    pub force_shaping: i32,
    pub color_start: [u8; 3],
    pub color_change: [f32; 3],
}

impl EmitterConfig {
    /// Decode a `.rpc`. Field order is `RP_LoadEmitterConfig` exactly.
    pub fn parse(data: &[u8]) -> Result<EmitterConfig, ReadError> {
        let mut r = BlitzReader::new(data);
        let mut c = EmitterConfig::default();
        c.max_particles = r.read_int()?;
        c.particles_per_frame = r.read_int()?;
        c.tex_across = r.read_int()?;
        c.tex_down = r.read_int()?;
        c.rnd_start_frame = r.read_int()?;
        c.tex_anim_speed = r.read_int()?;
        c.v_shape_based = r.read_int()?;
        c.velocity = [r.read_float()?, r.read_float()?, r.read_float()?];
        c.velocity_rnd = [r.read_float()?, r.read_float()?, r.read_float()?];
        c.force = [r.read_float()?, r.read_float()?, r.read_float()?];
        c.scale_start = r.read_float()?;
        c.scale_change = r.read_float()?;
        c.lifespan = r.read_int()?;
        c.alpha_start = r.read_float()?;
        c.alpha_change = r.read_float()?;
        c.blend_mode = r.read_int()?;
        c.shape = r.read_int()?;
        c.min_radius = r.read_float()?;
        c.max_radius = r.read_float()?;
        c.width = r.read_float()?;
        c.height = r.read_float()?;
        c.depth = r.read_float()?;
        c.shape_axis = r.read_int()?;
        let _default_tex = r.read_short_u()?; // RealmCrafter-specific; unused
        c.force_mod = [r.read_float()?, r.read_float()?, r.read_float()?];
        c.force_shaping = r.read_int()?;
        c.color_start = [r.read_byte()?, r.read_byte()?, r.read_byte()?];
        c.color_change = [r.read_float()?, r.read_float()?, r.read_float()?];
        Ok(c)
    }
}
