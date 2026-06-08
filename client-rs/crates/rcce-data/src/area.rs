//! Client area (`Data/Areas/<zone>.dat`) — the VISUAL zone data the client
//! loads (`ClientAreas_FE.bb::LoadArea`). We parse the scenery placement list
//! (the props/terrain meshes that fill the world); the rest of the header is
//! display/environment settings the renderer doesn't need yet.
//!
//! The area file's header (the Width..ShadowR fields in LoadArea come from
//! Options.dat, NOT this file) is a fixed 41-byte prefix:
//! LoadingTexID,LoadingMusicID,SkyTexID,CloudTexID,StormCloudTexID,StarsTexID
//! (i16×6) · FogR,G,B(u8×3) · FogNear,FogFar(f32×2) · MapTexID(i16) ·
//! Outdoors(u8) · AmbientR,G,B(u8×3) · DefaultLightPitch,Yaw,SlopeRestrict(f32×3).
//! Then `Sceneries:i16`, then each record:
//! MeshID(i16) · X,Y,Z(f32) · Pitch,Yaw,Roll(f32) · ScaleX,Y,Z(f32) ·
//! AnimMode(u8) · SceneryID(u8) · TextureID(i16) · CatchRain(u8) · Collides(u8) ·
//! Lightmap(str) · RCTE(str) · CastShadow(u8) · ReceiveShadow(u8) · RenderRange(u8).

use crate::reader::{BlitzReader, ReadError};

/// One placed scenery object (a mesh-catalog id at a world transform).
#[derive(Debug, Clone)]
pub struct SceneryPlacement {
    pub mesh_id: u16,
    pub pos: [f32; 3],
    /// Pitch, Yaw, Roll in degrees.
    pub rot: [f32; 3],
    pub scale: [f32; 3],
    /// Optional retexture id (texture catalog), 65535/none if unused.
    pub texture_id: u16,
}

/// Zone environment/atmosphere from the area header — what the renderer needs
/// for sky colour, distance fog, and ambient light.
#[derive(Debug, Clone)]
pub struct AreaEnv {
    pub sky_tex_id: u16,
    /// Cloud / storm-cloud / night-stars texture ids (Textures.dat; 65535 = none).
    /// Drawn as slowly-drifting sky overlays (`Environment3D.bb` CloudEN/StarsEN).
    pub cloud_tex_id: u16,
    pub storm_cloud_tex_id: u16,
    pub stars_tex_id: u16,
    /// `LoadingMusicID` — indexes `Music.dat` for the zone's looping track
    /// (65535 = none).
    pub music_id: u16,
    /// Fog colour (0..1). Also the natural sky/clear colour.
    pub fog_color: [f32; 3],
    pub fog_near: f32,
    pub fog_far: f32,
    pub ambient: [f32; 3],
    /// Unit vector *toward* the zone's directional light (for diffuse shading),
    /// derived from the stored `DefaultLightPitch`/`Yaw` the engine feeds to
    /// `RotateEntity(DefaultLight, pitch, yaw, 0)`.
    pub light_dir: [f32; 3],
    pub outdoors: bool,
}

/// Toward-light unit vector from the engine's pitch/yaw (degrees). The Blitz
/// directional light shines along its rotated local +Z; shading wants the
/// opposite (the direction light arrives from).
pub fn light_dir_from_pitch_yaw(pitch_deg: f32, yaw_deg: f32) -> [f32; 3] {
    let (p, y) = (pitch_deg.to_radians(), yaw_deg.to_radians());
    // Forward (shine) = (cosP·sinY, -sinP, cosP·cosY); toward-light = -forward.
    [-(p.cos() * y.sin()), p.sin(), -(p.cos() * y.cos())]
}

impl Default for AreaEnv {
    fn default() -> Self {
        AreaEnv {
            sky_tex_id: 65535,
            cloud_tex_id: 65535,
            storm_cloud_tex_id: 65535,
            stars_tex_id: 65535,
            music_id: 65535,
            fog_color: [0.45, 0.62, 0.82],
            fog_near: 1000.0,
            fog_far: 8000.0,
            ambient: [0.5, 0.5, 0.5],
            light_dir: [0.0, 0.5, -0.866],
            outdoors: true,
        }
    }
}

/// A water surface placed in the zone (GUE water tool). A horizontal textured,
/// alpha-blended, tinted plane centred at `pos`, spanning `scale_x`×`scale_z`.
/// Wire layout (after the scenery list, ClientAreas.bb:546): `tex_id(i16)` ·
/// `tex_scale(f32)` · `x/y/z(f32×3)` · `scale_x/scale_z(f32×2)` · `rgb(u8×3)` ·
/// `opacity(u8, 0..100)`.
#[derive(Debug, Default, Clone, Copy)]
pub struct WaterPlane {
    pub tex_id: u16,
    pub tex_scale: f32,
    pub pos: [f32; 3],
    pub scale_x: f32,
    pub scale_z: f32,
    pub color: [f32; 3],
    /// 0..1 (the wire 0..100 opacity / 100).
    pub opacity: f32,
}

/// A Blitz LOD terrain patch (`CreateTerrain`), the ground system used by older
/// forks (e.g. Mythic Realms 1.26) instead of a scenery ground mesh. Writer:
/// `SaveArea` (ClientAreas.bb:935). Per patch: `base_tex(i16)` · `detail_tex(i16)`
/// · `grid(i32 = N)` · **`(N+1)²` height floats** (row-major, x outer, z inner) ·
/// `x/y/z(f32×3)` · `pitch/yaw/roll(f32×3)` · `scale_x/y/z(f32×3)` ·
/// `detail_scale(f32)` · `detail(i32)` · `morph(u8)` · `shading(u8)`. The local
/// grid spans `[0,N]×[0,N]` (1 unit/cell) before the entity transform.
#[derive(Debug, Default, Clone)]
pub struct TerrainPatch {
    pub base_tex_id: u16,
    pub detail_tex_id: u16,
    /// `TerrainSize` N; the grid is `(N+1)×(N+1)` vertices.
    pub grid: u32,
    /// `(N+1)²` heights, indexed `x*(N+1) + z`.
    pub heights: Vec<f32>,
    pub pos: [f32; 3],
    /// Pitch, yaw, roll in degrees (RotateEntity order).
    pub rot: [f32; 3],
    pub scale: [f32; 3],
    pub detail_tex_scale: f32,
}

/// A placed particle emitter (GUE emitter tool). Writer: `SaveArea`
/// (ClientAreas.bb:920): `config_name(str)` · `tex_id(i16)` · `x/y/z(f32×3)` ·
/// `pitch/yaw/roll(f32×3)`. The config name indexes `Data/Emitter Configs/<name>.rpc`.
#[derive(Debug, Default, Clone)]
pub struct EmitterPlacement {
    pub config_name: String,
    pub tex_id: u16,
    pub pos: [f32; 3],
    /// Pitch/yaw/roll in degrees (orients shape-based emission).
    pub rot: [f32; 3],
}

#[derive(Debug, Default, Clone)]
pub struct AreaScenery {
    pub env: AreaEnv,
    pub sceneries: Vec<SceneryPlacement>,
    pub waters: Vec<WaterPlane>,
    pub terrains: Vec<TerrainPatch>,
    pub emitters: Vec<EmitterPlacement>,
}

/// Byte offset of the `Sceneries` count (fixed header prefix length).
const SCENERY_COUNT_OFFSET: usize = 41;

impl AreaScenery {
    pub fn parse(data: &[u8]) -> Result<AreaScenery, ReadError> {
        let mut r = BlitzReader::new(data);
        // Header (41 bytes): 6×i16 tex/music ids · FogRGB(u8×3) · FogNear,Far
        // (f32×2) · MapTexID(i16) · Outdoors(u8) · AmbientRGB(u8×3) · light(f32×3).
        let env = (|| -> Result<AreaEnv, ReadError> {
            r.seek(2)?; // skip LoadingTexID (i16@0)
            let music_id = r.read_short_u()?; // LoadingMusicID (i16@2)
            let sky_tex_id = r.read_short_u()?; // @4
            let cloud_tex_id = r.read_short_u()?; // @6
            let storm_cloud_tex_id = r.read_short_u()?; // @8
            let stars_tex_id = r.read_short_u()?; // @10 (now at @12 = FogRGB)
            let fog_color = [
                r.read_byte()? as f32 / 255.0,
                r.read_byte()? as f32 / 255.0,
                r.read_byte()? as f32 / 255.0,
            ];
            let fog_near = r.read_float()?;
            let fog_far = r.read_float()?;
            r.seek(25)?; // skip MapTexID(i16) to Outdoors
            let outdoors = r.read_byte()? != 0;
            let ambient = [
                r.read_byte()? as f32 / 255.0,
                r.read_byte()? as f32 / 255.0,
                r.read_byte()? as f32 / 255.0,
            ];
            // DefaultLightPitch, DefaultLightYaw (degrees), SlopeRestrict follow.
            let light_pitch = r.read_float()?;
            let light_yaw = r.read_float()?;
            let light_dir = light_dir_from_pitch_yaw(light_pitch, light_yaw);
            Ok(AreaEnv {
                sky_tex_id,
                cloud_tex_id,
                storm_cloud_tex_id,
                stars_tex_id,
                music_id,
                fog_color,
                fog_near,
                fog_far,
                ambient,
                light_dir,
                outdoors,
            })
        })()
        .unwrap_or_default();

        r.seek(SCENERY_COUNT_OFFSET)?;
        let count = r.read_short_u()?;
        let mut sceneries = Vec::with_capacity(count as usize);
        for _ in 0..count {
            let mesh_id = r.read_short_u()?;
            let pos = [r.read_float()?, r.read_float()?, r.read_float()?];
            let rot = [r.read_float()?, r.read_float()?, r.read_float()?];
            let scale = [r.read_float()?, r.read_float()?, r.read_float()?];
            let _anim_mode = r.read_byte()?;
            let _scenery_id = r.read_byte()?;
            let texture_id = r.read_short_u()?;
            let _catch_rain = r.read_byte()?;
            let _collides = r.read_byte()?;
            let _lightmap = r.read_string(260)?;
            let _rcte = r.read_string(260)?;
            let _cast_shadow = r.read_byte()?;
            let _receive_shadow = r.read_byte()?;
            let _render_range = r.read_byte()?;
            sceneries.push(SceneryPlacement {
                mesh_id,
                pos,
                rot,
                scale,
                texture_id,
            });
        }

        // Water surfaces (ClientAreas.bb:546) — immediately follow the scenery
        // list. Best-effort: a truncated/old area file without the block just
        // yields no water (parse errors stop the loop rather than failing).
        let mut waters = Vec::new();
        if let Ok(n) = r.read_short_u() {
            for _ in 0..n {
                let tex_id = match r.read_short_u() {
                    Ok(v) => v,
                    Err(_) => break,
                };
                let tex_scale = r.read_float().unwrap_or(1.0);
                let pos = [
                    r.read_float().unwrap_or(0.0),
                    r.read_float().unwrap_or(0.0),
                    r.read_float().unwrap_or(0.0),
                ];
                let scale_x = r.read_float().unwrap_or(0.0);
                let scale_z = r.read_float().unwrap_or(0.0);
                let color = [
                    r.read_byte().unwrap_or(255) as f32 / 255.0,
                    r.read_byte().unwrap_or(255) as f32 / 255.0,
                    r.read_byte().unwrap_or(255) as f32 / 255.0,
                ];
                let opacity = (r.read_byte().unwrap_or(100) as f32 / 100.0).clamp(0.0, 1.0);
                waters.push(WaterPlane { tex_id, tex_scale, pos, scale_x, scale_z, color, opacity });
            }
        }

        // Collision boxes, emitters, then the Blitz LOD terrains (SaveArea order,
        // ClientAreas.bb:900-957). Skip the first two — they carry no client
        // visuals here — to land on the terrain block. All best-effort: a
        // truncated/older file without these blocks just yields no terrain.
        let mut terrains = Vec::new();
        let mut emitters = Vec::new();
        let parsed = (|| -> Result<(), ReadError> {
            // Collision boxes: 9 floats each.
            let n_col = r.read_short_u()?;
            for _ in 0..n_col {
                for _ in 0..9 {
                    r.read_float()?;
                }
            }
            // Emitters: ConfigName(str) · tex(i16) · x/y/z · pitch/yaw/roll.
            let n_emit = r.read_short_u()?;
            for _ in 0..n_emit {
                let config_name = r.read_string(260)?;
                let tex_id = r.read_short_u()?;
                let pos = [r.read_float()?, r.read_float()?, r.read_float()?];
                let rot = [r.read_float()?, r.read_float()?, r.read_float()?];
                emitters.push(EmitterPlacement { config_name, tex_id, pos, rot });
            }
            // Terrains.
            let n_terr = r.read_short_u()?;
            for _ in 0..n_terr {
                let base_tex_id = r.read_short_u()?;
                let detail_tex_id = r.read_short_u()?;
                let grid = r.read_int()?;
                // `grid` comes straight off disk. A corrupt or hostile area file
                // can carry a negative or absurd value; `(grid+1)^2` would then
                // overflow the vertex count (the multiply itself wraps `usize`,
                // and `Vec::with_capacity` panics past `isize::MAX`). Real LOD
                // terrain grids are tiny — reject anything implausible and let
                // the caller soft-fail the zone rather than crash the client.
                const MAX_TERRAIN_GRID: i32 = 4096;
                if !(0..=MAX_TERRAIN_GRID).contains(&grid) {
                    return Err(ReadError::CountTooLarge {
                        count: grid as i64,
                        max: MAX_TERRAIN_GRID as usize,
                    });
                }
                let grid = grid as u32;
                let verts = (grid as usize + 1) * (grid as usize + 1);
                let mut heights = Vec::with_capacity(verts);
                for _ in 0..verts {
                    heights.push(r.read_float()?);
                }
                let pos = [r.read_float()?, r.read_float()?, r.read_float()?];
                let rot = [r.read_float()?, r.read_float()?, r.read_float()?];
                let scale = [r.read_float()?, r.read_float()?, r.read_float()?];
                let detail_tex_scale = r.read_float()?;
                let _detail = r.read_int()?;
                let _morph = r.read_byte()?;
                let _shading = r.read_byte()?;
                terrains.push(TerrainPatch {
                    base_tex_id,
                    detail_tex_id,
                    grid,
                    heights,
                    pos,
                    rot,
                    scale,
                    detail_tex_scale,
                });
            }
            Ok(())
        })();
        // A mid-block parse error leaves whatever terrains fully decoded so far.
        let _ = parsed;

        Ok(AreaScenery { env, sceneries, waters, terrains, emitters })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn approx(a: [f32; 3], b: [f32; 3]) -> bool {
        (0..3).all(|i| (a[i] - b[i]).abs() < 1e-4)
    }

    // Synthetic area with empty scenery/water/colbox/emitter then ONE LOD terrain
    // — exercises the terrain field parse (and the colbox/emitter skip) that real
    // current zones can't (they ship zero terrains). Bytes laid out per SaveArea.
    #[test]
    fn parse_synthetic_terrain() {
        let mut d = vec![0u8; 41]; // zeroed 41-byte header (env reads → zeros)
        let u16 = |d: &mut Vec<u8>, v: u16| d.extend_from_slice(&v.to_le_bytes());
        let i32 = |d: &mut Vec<u8>, v: i32| d.extend_from_slice(&v.to_le_bytes());
        let f32 = |d: &mut Vec<u8>, v: f32| d.extend_from_slice(&v.to_le_bytes());
        for _ in 0..4 {
            u16(&mut d, 0); // scenery, water, colboxes, emitters: all empty
        }
        u16(&mut d, 1); // one terrain
        u16(&mut d, 5); // base tex
        u16(&mut d, 65535); // detail tex (none)
        i32(&mut d, 2); // grid N=2 → (N+1)²=9 heights
        let heights = [0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0];
        for h in heights {
            f32(&mut d, h);
        }
        for v in [10.0, -1.0, 20.0] {
            f32(&mut d, v); // pos
        }
        for v in [0.0, 90.0, 0.0] {
            f32(&mut d, v); // pitch/yaw/roll
        }
        for v in [4.0, 1.0, 4.0] {
            f32(&mut d, v); // scale
        }
        f32(&mut d, 8.0); // detail tex scale
        i32(&mut d, 1); // detail
        d.push(1); // morph
        d.push(0); // shading

        let area = AreaScenery::parse(&d).expect("parse");
        assert_eq!(area.terrains.len(), 1);
        let t = &area.terrains[0];
        assert_eq!(t.base_tex_id, 5);
        assert_eq!(t.detail_tex_id, 65535);
        assert_eq!(t.grid, 2);
        assert_eq!(t.heights, heights);
        assert!(approx(t.pos, [10.0, -1.0, 20.0]));
        assert!(approx(t.rot, [0.0, 90.0, 0.0]));
        assert!(approx(t.scale, [4.0, 1.0, 4.0]));
        assert_eq!(t.detail_tex_scale, 8.0);
    }

    #[test]
    fn light_dir_default_pitch() {
        // The engine default (pitch 30, yaw 0): light from above-and-behind.
        let l = light_dir_from_pitch_yaw(30.0, 0.0);
        assert!(approx(l, [0.0, 0.5, -0.8660254]), "got {l:?}");
        // Always a unit vector.
        let mag = (l[0] * l[0] + l[1] * l[1] + l[2] * l[2]).sqrt();
        assert!((mag - 1.0).abs() < 1e-4, "mag {mag}");
    }

    #[test]
    fn light_dir_straight_down() {
        // Pitch 90: light straight overhead → toward-light points +Y.
        let l = light_dir_from_pitch_yaw(90.0, 0.0);
        assert!(approx(l, [0.0, 1.0, 0.0]), "got {l:?}");
    }

    #[test]
    fn light_dir_yaw_rotates_horizontal() {
        // Pitch 0, yaw 90: purely horizontal, rotated onto -X.
        let l = light_dir_from_pitch_yaw(0.0, 90.0);
        assert!(approx(l, [-1.0, 0.0, 0.0]), "got {l:?}");
    }
}
