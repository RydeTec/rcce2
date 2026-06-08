//! Particle emitter simulation — a faithful port of RottParticles.bb
//! (`RP_SpawnParticle` + `RP_Update`). Each emitter owns a particle pool; every
//! frame it spawns, integrates force→velocity→position, ages, and fades, then
//! emits camera-facing billboard quads for the renderer.
//!
//! `Delta` is in Blitz frames (the sim is authored at ~60 fps); the caller passes
//! `dt_seconds * 60`.

use rcce_data::emitter::{shape, vmode, EmitterConfig};
use rcce_render::gpu::Vertex;

/// Tiny deterministic PRNG (xorshift) so the sim needs no `rand` dependency and
/// is reproducible. Seeded per system.
struct Rng(u64);
impl Rng {
    fn next_u32(&mut self) -> u32 {
        let mut x = self.0;
        x ^= x << 13;
        x ^= x >> 7;
        x ^= x << 17;
        self.0 = x;
        (x >> 32) as u32
    }
    /// Uniform in `[-m, m]`.
    fn signed(&mut self, m: f32) -> f32 {
        (self.next_u32() as f32 / u32::MAX as f32 * 2.0 - 1.0) * m
    }
    /// Uniform in `[a, b]`.
    fn range(&mut self, a: f32, b: f32) -> f32 {
        a + (self.next_u32() as f32 / u32::MAX as f32) * (b - a)
    }
}

struct Particle {
    pos: [f32; 3],
    vel: [f32; 3],
    force: [f32; 3],
    color: [f32; 3], // 0..255
    alpha: f32,
    scale: f32,
    ttl: f32,
    in_use: bool,
}

impl Particle {
    fn dead() -> Particle {
        Particle { pos: [0.0; 3], vel: [0.0; 3], force: [0.0; 3], color: [0.0; 3], alpha: 0.0, scale: 0.0, ttl: 0.0, in_use: false }
    }
}

/// One placed emitter: its config, world position/orientation, and live pool.
pub struct Emitter {
    pub config: EmitterConfig,
    /// Resolved texture id (the placement's, falling back to the config default).
    pub tex_id: u16,
    pub blend_add: bool,
    pos: [f32; 3],
    /// Emitter basis (yaw/pitch/roll applied) for shape-relative spawn.
    basis: [[f32; 3]; 3],
    scale: f32,
    particles: Vec<Particle>,
    to_spawn: i32,
    rng: Rng,
}

fn rot_basis(deg: [f32; 3]) -> [[f32; 3]; 3] {
    // Y·X·Z (Blitz RotateEntity), columns = local axes in world.
    let m = glam::Mat3::from_rotation_y(deg[1].to_radians())
        * glam::Mat3::from_rotation_x(deg[0].to_radians())
        * glam::Mat3::from_rotation_z(deg[2].to_radians());
    [m.x_axis.to_array(), m.y_axis.to_array(), m.z_axis.to_array()]
}

fn apply_basis(b: &[[f32; 3]; 3], v: [f32; 3]) -> [f32; 3] {
    [
        b[0][0] * v[0] + b[1][0] * v[1] + b[2][0] * v[2],
        b[0][1] * v[0] + b[1][1] * v[1] + b[2][1] * v[2],
        b[0][2] * v[0] + b[1][2] * v[1] + b[2][2] * v[2],
    ]
}

impl Emitter {
    pub fn new(config: EmitterConfig, tex_id: u16, pos: [f32; 3], rot: [f32; 3], seed: u64) -> Emitter {
        let cap = config.max_particles.clamp(1, 20_000) as usize;
        let blend_add = config.blend_mode != 1; // 1 = alpha; 2/3 treated additive
        let mut particles = Vec::with_capacity(cap);
        particles.resize_with(cap, Particle::dead);
        Emitter {
            config,
            tex_id,
            blend_add,
            pos,
            basis: rot_basis(rot),
            scale: 1.0,
            particles,
            to_spawn: 0,
            rng: Rng(seed | 1),
        }
    }

    fn spawn(&mut self, i: usize) {
        let c = &self.config;
        let mut vel = [
            c.velocity[0] + self.rng.signed(c.velocity_rnd[0]),
            c.velocity[1] + self.rng.signed(c.velocity_rnd[1]),
            c.velocity[2] + self.rng.signed(c.velocity_rnd[2]),
        ];
        let mut p = [0.0f32; 3];
        match c.shape {
            shape::SPHERE => {
                let dist = self.rng.range(c.min_radius, c.max_radius) * self.scale;
                let pitch = self.rng.range(-90.0, 90.0).to_radians();
                let yaw = self.rng.range(-180.0, 180.0).to_radians();
                p[1] = pitch.sin() * dist;
                let fd = pitch.cos() * dist;
                p[0] = yaw.cos() * fd;
                p[2] = yaw.sin() * fd;
                if c.v_shape_based == vmode::SHAPE_BASED {
                    vel = [vel[0].abs() * p[0].signum(), vel[1].abs() * p[1].signum(), vel[2].abs() * p[2].signum()];
                } else if c.v_shape_based == vmode::HEAVILY_SHAPE_BASED {
                    let r = c.max_radius.max(1e-4);
                    vel = [vel[0].abs() * p[0] / r, vel[1].abs() * p[1] / r, vel[2].abs() * p[2] / r];
                }
            }
            shape::BOX => {
                p[0] = self.rng.signed(c.width * 0.5) * self.scale;
                p[1] = self.rng.signed(c.height * 0.5) * self.scale;
                p[2] = self.rng.signed(c.depth * 0.5) * self.scale;
                if c.v_shape_based == vmode::SHAPE_BASED {
                    let (ax, ay, az) = (p[0].abs(), p[1].abs(), p[2].abs());
                    if ax > ay && ax > az {
                        vel = [vel[0].abs() * p[0].signum(), 0.0, 0.0];
                    } else if ay > ax && ay > az {
                        vel = [0.0, vel[1].abs() * p[1].signum(), 0.0];
                    } else {
                        vel = [0.0, 0.0, vel[2].abs() * p[2].signum()];
                    }
                }
            }
            _ => {
                // Cylinder (default axis Y).
                let dist = self.rng.range(c.min_radius, c.max_radius) * self.scale;
                let yaw = self.rng.range(-180.0, 180.0).to_radians();
                let h = self.rng.signed(c.depth * 0.5) * self.scale;
                p = [yaw.cos() * dist, h, yaw.sin() * dist];
            }
        }
        // Orient by the emitter basis + place at its world position.
        let pw = apply_basis(&self.basis, p);
        let vw = apply_basis(&self.basis, vel);
        let part = &mut self.particles[i];
        part.pos = [self.pos[0] + pw[0], self.pos[1] + pw[1], self.pos[2] + pw[2]];
        part.vel = vw;
        part.force = c.force;
        part.scale = c.scale_start * self.scale;
        part.color = [c.color_start[0] as f32, c.color_start[1] as f32, c.color_start[2] as f32];
        part.alpha = c.alpha_start;
        part.ttl = c.lifespan as f32;
        part.in_use = true;
        self.to_spawn -= 1;
    }

    /// Advance the sim by `delta` Blitz-frames.
    pub fn update(&mut self, delta: f32) {
        let c = &self.config;
        self.to_spawn = (c.particles_per_frame as f32 * delta).ceil() as i32;
        let (fm, force_shaping) = (c.force_mod, c.force_shaping);
        for i in 0..self.particles.len() {
            if self.particles[i].in_use {
                {
                    let p = &mut self.particles[i];
                    if force_shaping == 1 {
                        // Linear: force accelerates.
                        p.force[0] += fm[0] * delta;
                        p.force[1] += fm[1] * delta;
                        p.force[2] += fm[2] * delta;
                    }
                    p.vel[0] += p.force[0] * delta;
                    p.vel[1] += p.force[1] * delta;
                    p.vel[2] += p.force[2] * delta;
                    p.pos[0] += p.vel[0] * delta;
                    p.pos[1] += p.vel[1] * delta;
                    p.pos[2] += p.vel[2] * delta;
                    p.scale += self.config.scale_change * delta;
                    for k in 0..3 {
                        p.color[k] = (p.color[k] + self.config.color_change[k] * delta).rem_euclid(255.0);
                    }
                    p.alpha += self.config.alpha_change * delta;
                    if p.alpha <= 0.0 {
                        p.ttl = -1.0;
                    }
                    p.ttl -= delta;
                }
                if self.particles[i].ttl < 0.0 {
                    if self.to_spawn > 0 {
                        self.spawn(i);
                    } else {
                        self.particles[i].in_use = false;
                    }
                }
            } else if self.to_spawn > 0 {
                self.spawn(i);
            }
        }
    }

    /// Append camera-facing billboard quads (6 verts each) for live particles.
    /// `right`/`up` are the camera's world basis vectors. `gain` scales the
    /// per-particle alpha (softness/transparency tuning vs Blitz).
    pub fn billboards(&self, right: [f32; 3], up: [f32; 3], gain: f32, out: &mut Vec<Vertex>) {
        for p in self.particles.iter().filter(|p| p.in_use && p.alpha > 0.0 && p.scale > 0.0) {
            let s = p.scale;
            let col = [p.color[0] / 255.0, p.color[1] / 255.0, p.color[2] / 255.0, (p.alpha * gain).clamp(0.0, 1.0)];
            let corner = |ox: f32, oy: f32, uv: [f32; 2]| Vertex {
                pos: [
                    p.pos[0] + right[0] * ox * s + up[0] * oy * s,
                    p.pos[1] + right[1] * ox * s + up[1] * oy * s,
                    p.pos[2] + right[2] * ox * s + up[2] * oy * s,
                ],
                normal: [0.0, 0.0, 1.0],
                uv,
                uv2: [0.0, 0.0],
                color: col,
            };
            let v00 = corner(-1.0, -1.0, [0.0, 1.0]);
            let v01 = corner(-1.0, 1.0, [0.0, 0.0]);
            let v11 = corner(1.0, 1.0, [1.0, 0.0]);
            let v10 = corner(1.0, -1.0, [1.0, 1.0]);
            out.extend_from_slice(&[v00, v01, v11, v00, v11, v10]);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn cfg() -> EmitterConfig {
        EmitterConfig {
            max_particles: 10,
            particles_per_frame: 5,
            lifespan: 4,
            shape: shape::SPHERE,
            max_radius: 1.0,
            scale_start: 1.0,
            alpha_start: 1.0,
            velocity: [0.0, 1.0, 0.0],
            force: [0.0, -0.1, 0.0],
            force_shaping: 1,
            blend_mode: 3,
            ..Default::default()
        }
    }

    #[test]
    fn spawns_ages_and_dies() {
        let mut e = Emitter::new(cfg(), 0, [10.0, 0.0, 0.0], [0.0; 3], 42);
        e.update(1.0);
        let alive = e.particles.iter().filter(|p| p.in_use).count();
        assert!(alive >= 5, "spawned {alive}");
        // Particles start near the emitter position (sphere radius ≤ 1).
        for p in e.particles.iter().filter(|p| p.in_use) {
            let d = ((p.pos[0] - 10.0).powi(2) + p.pos[1].powi(2) + p.pos[2].powi(2)).sqrt();
            assert!(d <= 2.0, "particle too far: {d}");
        }
        // After lifespan-worth of frames with no respawn, all die.
        for _ in 0..6 {
            e.config.particles_per_frame = 0;
            e.update(1.0);
        }
        assert_eq!(e.particles.iter().filter(|p| p.in_use).count(), 0);
    }

    #[test]
    fn billboards_are_camera_facing_quads() {
        let mut e = Emitter::new(cfg(), 0, [0.0; 3], [0.0; 3], 7);
        e.update(1.0);
        let mut v = Vec::new();
        e.billboards([1.0, 0.0, 0.0], [0.0, 1.0, 0.0], 1.0, &mut v);
        assert_eq!(v.len() % 6, 0);
        assert!(!v.is_empty());
    }
}
