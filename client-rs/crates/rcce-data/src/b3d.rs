//! Blitz3D `.b3d` mesh parser — the format the GUE editor writes and the engine
//! loads via native `LoadMesh`. Standard B3D: a `BB3D` magic + version, then
//! nested length-tagged chunks (`TEXS`/`BRUS`/`NODE`/`MESH`/`VRTS`/`TRIS`/
//! `BONE`/`ANIM`/`KEYS`). All values little-endian; node names are NUL-terminated.
//!
//! This extracts geometry (positions + optional normals/UVs + triangle indices)
//! per mesh, with each mesh's full node bind-pose transform (translate · rotate
//! · scale, composed down the hierarchy) applied so a model assembles in place —
//! enough to render. Skeleton/animation (`BONE`/`ANIM`) are skipped for now
//! (added when skeletal animation lands).

use crate::reader::{BlitzReader, ReadError};

/// A 4×4 row-major matrix (`p' = M · [p,1]`). Just enough linear algebra to
/// compose the B3D node hierarchy's bind-pose transforms (translate · rotate ·
/// scale) without pulling in a math crate.
type Mat4 = [f32; 16];

const IDENTITY: Mat4 = [
    1.0, 0.0, 0.0, 0.0, //
    0.0, 1.0, 0.0, 0.0, //
    0.0, 0.0, 1.0, 0.0, //
    0.0, 0.0, 0.0, 1.0,
];

/// `a · b` (row-major).
fn mat_mul(a: &Mat4, b: &Mat4) -> Mat4 {
    let mut m = [0.0f32; 16];
    for r in 0..4 {
        for c in 0..4 {
            let mut s = 0.0;
            for k in 0..4 {
                s += a[r * 4 + k] * b[k * 4 + c];
            }
            m[r * 4 + c] = s;
        }
    }
    m
}

/// Node local transform `T · R · S` from translation, quaternion `(w,x,y,z)`,
/// and per-axis scale.
///
/// **B3D quaternions are CONJUGATED relative to the standard math convention**
/// — the vector part `(x,y,z)` is negated here before building the rotation.
/// Without this, the bind pose and frame 1 still look correct (both are
/// identity tautologies: `bind_world · bind_world⁻¹ = I`, and frame-1 keys ==
/// the bind local), so the bug is invisible until an animated frame, where the
/// skinned legs splay grossly. The bind mesh node chain is near-identity, so
/// conjugation leaves it ≈unchanged; only the real bone rotations are corrected.
fn trs(t: [f32; 3], q: [f32; 4], s: [f32; 3]) -> Mat4 {
    // Conjugate: B3D stores the rotation as the inverse of the math convention.
    let (w, x, y, z) = (q[0], -q[1], -q[2], -q[3]);
    // Normalise the quaternion.
    let n = (w * w + x * x + y * y + z * z).sqrt();
    let (w, x, y, z) = if n > 1e-12 {
        (w / n, x / n, y / n, z / n)
    } else {
        (1.0, 0.0, 0.0, 0.0)
    };
    // Rotation 3×3.
    let r = [
        1.0 - 2.0 * (y * y + z * z),
        2.0 * (x * y - w * z),
        2.0 * (x * z + w * y),
        2.0 * (x * y + w * z),
        1.0 - 2.0 * (x * x + z * z),
        2.0 * (y * z - w * x),
        2.0 * (x * z - w * y),
        2.0 * (y * z + w * x),
        1.0 - 2.0 * (x * x + y * y),
    ];
    // M = T · R · S : scale multiplies each rotation column; translation last col.
    [
        r[0] * s[0], r[1] * s[1], r[2] * s[2], t[0], //
        r[3] * s[0], r[4] * s[1], r[5] * s[2], t[1], //
        r[6] * s[0], r[7] * s[1], r[8] * s[2], t[2], //
        0.0, 0.0, 0.0, 1.0,
    ]
}

/// Transform a position (includes translation).
fn xform_point(m: &Mat4, p: [f32; 3]) -> [f32; 3] {
    [
        m[0] * p[0] + m[1] * p[1] + m[2] * p[2] + m[3],
        m[4] * p[0] + m[5] * p[1] + m[6] * p[2] + m[7],
        m[8] * p[0] + m[9] * p[1] + m[10] * p[2] + m[11],
    ]
}

/// Transpose a row-major [`Mat4`] to column-major layout. WGSL reads a uniform/
/// storage `mat4x4` column-major, but these matrices are stored row-major (see
/// [`xform_point`]); transposing makes a shader `M * vec4(p,1)` equal the CPU
/// `xform_point(M, p)`.
fn transpose16(m: &Mat4) -> [f32; 16] {
    let mut t = [0.0f32; 16];
    for r in 0..4 {
        for c in 0..4 {
            t[c * 4 + r] = m[r * 4 + c];
        }
    }
    t
}

/// Transform a direction (rotation + scale, no translation) — for normals.
fn xform_dir(m: &Mat4, v: [f32; 3]) -> [f32; 3] {
    [
        m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
        m[4] * v[0] + m[5] * v[1] + m[6] * v[2],
        m[8] * v[0] + m[9] * v[1] + m[10] * v[2],
    ]
}

/// Invert an affine matrix (upper-3×3 invertible + translation). Node matrices
/// are always affine, so this is exact and cheaper than a full 4×4 inverse.
/// Returns identity if the 3×3 is singular.
pub(crate) fn invert_affine(m: &Mat4) -> Mat4 {
    let a = [
        m[0], m[1], m[2], //
        m[4], m[5], m[6], //
        m[8], m[9], m[10],
    ];
    let det = a[0] * (a[4] * a[8] - a[5] * a[7]) - a[1] * (a[3] * a[8] - a[5] * a[6])
        + a[2] * (a[3] * a[7] - a[4] * a[6]);
    if det.abs() < 1e-20 {
        return IDENTITY;
    }
    let inv_det = 1.0 / det;
    // Inverse of the 3×3 (row-major).
    let i = [
        (a[4] * a[8] - a[5] * a[7]) * inv_det,
        (a[2] * a[7] - a[1] * a[8]) * inv_det,
        (a[1] * a[5] - a[2] * a[4]) * inv_det,
        (a[5] * a[6] - a[3] * a[8]) * inv_det,
        (a[0] * a[8] - a[2] * a[6]) * inv_det,
        (a[2] * a[3] - a[0] * a[5]) * inv_det,
        (a[3] * a[7] - a[4] * a[6]) * inv_det,
        (a[1] * a[6] - a[0] * a[7]) * inv_det,
        (a[0] * a[4] - a[1] * a[3]) * inv_det,
    ];
    let t = [m[3], m[7], m[11]];
    // -inv3x3 · t
    let nt = [
        -(i[0] * t[0] + i[1] * t[1] + i[2] * t[2]),
        -(i[3] * t[0] + i[4] * t[1] + i[5] * t[2]),
        -(i[6] * t[0] + i[7] * t[1] + i[8] * t[2]),
    ];
    [
        i[0], i[1], i[2], nt[0], //
        i[3], i[4], i[5], nt[1], //
        i[6], i[7], i[8], nt[2], //
        0.0, 0.0, 0.0, 1.0,
    ]
}

/// Which channel of a keyframe to sample.
#[derive(Clone, Copy)]
enum KeyChan {
    Pos,
    Scale,
}

/// Find the keyframe bracket around `frame` and the interpolation factor.
/// Returns `(i, j, t)` with the value = lerp(key[i], key[j], t).
fn bracket(keys: &[B3dKey], frame: f32) -> (usize, usize, f32) {
    if keys.len() == 1 || frame <= keys[0].frame as f32 {
        return (0, 0, 0.0);
    }
    let last = keys.len() - 1;
    if frame >= keys[last].frame as f32 {
        return (last, last, 0.0);
    }
    let mut i = 0;
    while i + 1 < keys.len() && (keys[i + 1].frame as f32) <= frame {
        i += 1;
    }
    let (f0, f1) = (keys[i].frame as f32, keys[i + 1].frame as f32);
    let t = if f1 > f0 { (frame - f0) / (f1 - f0) } else { 0.0 };
    (i, i + 1, t)
}

/// Sample a Vec3 channel (position/scale) at `frame`, falling back to
/// `default` if the channel is absent.
fn sample_v3(keys: &[B3dKey], frame: f32, chan: KeyChan, default: [f32; 3]) -> [f32; 3] {
    let pick = |k: &B3dKey| match chan {
        KeyChan::Pos => k.position,
        KeyChan::Scale => k.scale,
    };
    if keys.iter().all(|k| pick(k).is_none()) {
        return default;
    }
    let (i, j, t) = bracket(keys, frame);
    let a = pick(&keys[i]).unwrap_or(default);
    let b = pick(&keys[j]).unwrap_or(default);
    [
        a[0] + (b[0] - a[0]) * t,
        a[1] + (b[1] - a[1]) * t,
        a[2] + (b[2] - a[2]) * t,
    ]
}

/// Sample the rotation channel at `frame` (nlerp), falling back to `default`.
fn sample_quat(keys: &[B3dKey], frame: f32, default: [f32; 4]) -> [f32; 4] {
    if keys.iter().all(|k| k.rotation.is_none()) {
        return default;
    }
    let (i, j, t) = bracket(keys, frame);
    let a = keys[i].rotation.unwrap_or(default);
    let mut b = keys[j].rotation.unwrap_or(default);
    // Shortest-path: flip b if the dot product is negative.
    let dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
    if dot < 0.0 {
        b = [-b[0], -b[1], -b[2], -b[3]];
    }
    let mut q = [
        a[0] + (b[0] - a[0]) * t,
        a[1] + (b[1] - a[1]) * t,
        a[2] + (b[2] - a[2]) * t,
        a[3] + (b[3] - a[3]) * t,
    ];
    let n = (q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]).sqrt();
    if n > 1e-8 {
        q = [q[0] / n, q[1] / n, q[2] / n, q[3] / n];
    }
    q
}

/// One animation keyframe for a bone (any of position/scale/rotation may be
/// absent, per the `KEYS` flags). Rotation is a quaternion `(w,x,y,z)`.
#[derive(Debug, Clone)]
pub struct B3dKey {
    pub frame: i32,
    pub position: Option<[f32; 3]>,
    pub scale: Option<[f32; 3]>,
    pub rotation: Option<[f32; 4]>,
}

/// A skeleton bone/joint: a node in the B3D hierarchy with its bind-pose
/// transforms, the vertices it skins (by mesh vertex id + weight), and its
/// animation keyframes.
#[derive(Debug, Clone)]
pub struct B3dBone {
    pub name: String,
    /// Index of the parent bone in [`B3dModel::bones`], or `None` for a root.
    pub parent: Option<usize>,
    /// This node's own local transform (`T·R·S`) in the bind pose.
    pub local_bind: Mat4,
    /// Decomposed bind-pose local channels (translation, quaternion w,x,y,z,
    /// scale) — the fallback for animation channels a `KEYS` stream omits.
    pub local_t: [f32; 3],
    pub local_r: [f32; 4],
    pub local_s: [f32; 3],
    /// Accumulated bind-pose world matrix.
    pub bind_world: Mat4,
    /// `bind_world⁻¹` — maps a vertex from model space into this bone's space.
    pub inverse_bind: Mat4,
    /// `(vertex_id, weight)` into the (single) mesh's vertex array.
    pub weights: Vec<(u32, f32)>,
    /// Per-bone animation keyframes (sorted by frame as stored).
    pub keys: Vec<B3dKey>,
}

/// Top-level animation header (`ANIM`).
#[derive(Debug, Clone, Copy, Default)]
pub struct B3dAnim {
    pub frames: i32,
    pub fps: f32,
}

/// One mesh's geometry, ready for upload to the GPU.
#[derive(Debug, Default, Clone)]
pub struct B3dMesh {
    pub positions: Vec<[f32; 3]>,
    pub normals: Vec<[f32; 3]>,
    pub uvs: Vec<[f32; 2]>,
    /// Second texture-coordinate set (`VRTS` tex_coord_set 1), used by the
    /// lightmap. Empty when the mesh has only one UV set. Aligns to `positions`.
    pub uvs2: Vec<[f32; 2]>,
    /// Per-vertex RGBA colors (`VRTS` flags&2). Empty when absent. Terrain splat
    /// layers blend by the **alpha** channel; props rarely carry these.
    pub colors: Vec<[f32; 4]>,
    /// Triangle vertex indices (3 per triangle).
    pub indices: Vec<u32>,
    /// Brush index this mesh defaults to (`-1` = none). Resolved into `texture`.
    pub brush_id: i32,
    /// Texture filename for this mesh (from its brush's first texture slot), as
    /// stored in the B3D (often a stale absolute author path — resolve by
    /// basename against the project's texture dirs).
    pub texture: Option<String>,
    /// Blitz3D texture flags for this mesh's resolved texture slot (`TEXS`
    /// flags: `1`=color, `2`=alpha, `4`=masked/color-key, `8`=mipmap, …).
    /// `4` (masked) means the texture's black pixels are transparent — the
    /// engine color-keys them; foliage/grass billboards rely on this.
    pub texture_flag: i32,
    /// Texcoord scale `(u,v)` from the resolved texture's `TEXS` transform —
    /// the texture tiles this many times across the mesh's UVs. `(1,1)` = none.
    pub uv_scale: [f32; 2],
    /// Texcoord offset `(u,v)` from the resolved texture's `TEXS` transform.
    pub uv_offset: [f32; 2],
    /// Lightmap texture filename (the brush's **second** non-empty texture slot),
    /// a baked light bake the renderer multiplies onto the base colour. `None`
    /// when the mesh isn't lightmapped (the common case).
    pub lightmap: Option<String>,
}

/// A parsed `.b3d` model: all meshes, flattened (node translation applied).
#[derive(Debug, Default, Clone)]
pub struct B3dModel {
    pub meshes: Vec<B3dMesh>,
    /// `TEXS` texture filenames, by index.
    pub textures: Vec<String>,
    /// `TEXS` Blitz3D flags, parallel to [`textures`](Self::textures).
    pub tex_flags: Vec<i32>,
    /// `TEXS` texture-coordinate **scale** `(xscale, yscale)`, parallel to
    /// [`textures`](Self::textures). The engine applies this as a texcoord
    /// transform (`ScaleTexture`), so the texture tiles `scale×` across the
    /// mesh's UVs — terrain textures rely on it for crisp tiling. `(1,1)` = none.
    pub tex_scales: Vec<[f32; 2]>,
    /// `TEXS` texture-coordinate **offset** `(xpos, ypos)`, parallel to textures.
    pub tex_offsets: Vec<[f32; 2]>,
    /// `BRUS` brushes — each brush's texture-slot indices into `textures`
    /// (`-1` = empty slot).
    pub brushes: Vec<Vec<i32>>,
    /// Skeleton — every `NODE` in hierarchy order (parents before children),
    /// with bind-pose transforms, skin weights, and animation keyframes. Empty
    /// for unskinned meshes.
    pub bones: Vec<B3dBone>,
    /// Animation header (`ANIM`): total frames + fps. `None` if not animated.
    pub anim: Option<B3dAnim>,
}

impl B3dModel {
    pub fn vertex_count(&self) -> usize {
        self.meshes.iter().map(|m| m.positions.len()).sum()
    }

    /// Bind-pose world position of the first bone/node whose name matches
    /// `name` (case-insensitive), e.g. `"Head"`. `None` if absent.
    pub fn joint_pos(&self, name: &str) -> Option<[f32; 3]> {
        self.bones
            .iter()
            .find(|b| b.name.eq_ignore_ascii_case(name))
            .map(|b| [b.bind_world[3], b.bind_world[7], b.bind_world[11]])
    }

    /// **Animated** model-space position of the named joint at `frame` — the
    /// joint's world translation in the same posed hierarchy the body is skinned
    /// with. Falls back to the bind pose when `frame` is `None` or the joint
    /// (and its ancestors) carry no keyframes. Unlike [`joint_pos`] (always the
    /// bind pose), this lets head/hand attachments — hair, weapon, shield —
    /// *track* the animation instead of floating at the rest position.
    pub fn joint_pos_at(&self, name: &str, frame: Option<f32>) -> Option<[f32; 3]> {
        let target = self.bones.iter().position(|b| b.name.eq_ignore_ascii_case(name))?;
        // Bones precede their children, so a single forward pass over 0..=target
        // resolves every ancestor before it's needed.
        let mut world = vec![IDENTITY; target + 1];
        for i in 0..=target {
            let b = &self.bones[i];
            let local = match frame {
                Some(f) if !b.keys.is_empty() => {
                    let t = sample_v3(&b.keys, f, KeyChan::Pos, b.local_t);
                    let s = sample_v3(&b.keys, f, KeyChan::Scale, b.local_s);
                    let r = sample_quat(&b.keys, f, b.local_r);
                    trs(t, r, s)
                }
                _ => b.local_bind,
            };
            world[i] = match b.parent {
                Some(p) => mat_mul(&world[p], &local),
                None => local,
            };
        }
        let w = &world[target];
        Some([w[3], w[7], w[11]])
    }

    /// Total skin-weight count across all bones (diagnostic).
    pub fn weight_count(&self) -> usize {
        self.bones.iter().map(|b| b.weights.len()).sum()
    }
    /// Total keyframe count across all bones (diagnostic).
    pub fn keyframe_count(&self) -> usize {
        self.bones.iter().map(|b| b.keys.len()).sum()
    }

    /// Per-bone skinning matrix `currentWorld · inverseBind` for a pose.
    /// `frame = None` is the bind pose (every matrix ≈ identity). With a frame,
    /// each bone's local transform is sampled from its `KEYS` (channels the
    /// stream omits fall back to the bind local), recomposed down the hierarchy.
    /// Bones precede their children, so a single forward pass suffices.
    pub fn skinning_matrices(&self, frame: Option<f32>) -> Vec<Mat4> {
        let n = self.bones.len();
        let mut world = vec![IDENTITY; n];
        let mut skin = vec![IDENTITY; n];
        for i in 0..n {
            let b = &self.bones[i];
            let local = match frame {
                Some(f) if !b.keys.is_empty() => {
                    let t = sample_v3(&b.keys, f, KeyChan::Pos, b.local_t);
                    let s = sample_v3(&b.keys, f, KeyChan::Scale, b.local_s);
                    let r = sample_quat(&b.keys, f, b.local_r);
                    trs(t, r, s)
                }
                _ => b.local_bind,
            };
            world[i] = match b.parent {
                Some(p) => mat_mul(&world[p], &local),
                None => local,
            };
            skin[i] = mat_mul(&world[i], &b.inverse_bind);
        }
        skin
    }

    /// The GPU bone-matrix palette for `frame`: each skinning matrix transposed
    /// to column-major [f32;16] for upload to a WGSL `mat4x4` storage/uniform
    /// buffer, so a shader doing `bone * vec4(pos,1)` matches the CPU
    /// [`posed_meshes`](Self::posed_meshes) / `xform_point` result. Pairs with
    /// [`skin_attributes`](Self::skin_attributes) for hardware linear-blend
    /// skinning.
    pub fn bone_palette(&self, frame: Option<f32>) -> Vec<[f32; 16]> {
        self.skinning_matrices(frame).iter().map(transpose16).collect()
    }

    /// Deformed copy of the meshes for a pose (linear-blend skinning). `frame =
    /// None` reproduces the bind pose exactly (skin matrices are identity), the
    /// sanity check for the pipeline. Mesh count/order is preserved so existing
    /// per-mesh textures still align. Unweighted vertices keep their bind
    /// position. Unskinned models (no bones) are returned unchanged.
    /// Per-vertex bone influences `(bone_index, normalized_weight)`. The vertex
    /// id space is shared across the split meshes; an empty inner vec means the
    /// vertex is unweighted (keeps its bind position). Shared by the CPU
    /// [`posed_meshes`](Self::posed_meshes) path and the GPU
    /// [`skin_attributes`](Self::skin_attributes) export.
    pub fn vertex_influences(&self) -> Vec<Vec<(usize, f32)>> {
        let maxv = self.meshes.iter().map(|m| m.positions.len()).max().unwrap_or(0);
        let mut infl: Vec<Vec<(usize, f32)>> = vec![Vec::new(); maxv];
        for (bi, b) in self.bones.iter().enumerate() {
            for &(vid, w) in &b.weights {
                let v = vid as usize;
                if v < maxv && w != 0.0 {
                    infl[v].push((bi, w));
                }
            }
        }
        for list in &mut infl {
            let sum: f32 = list.iter().map(|x| x.1).sum();
            if sum > 0.0 {
                for e in list.iter_mut() {
                    e.1 /= sum;
                }
            }
        }
        infl
    }

    /// Per-vertex GPU skinning attributes: for each vertex (0..max mesh vertex
    /// count), the up-to-4 strongest bone indices + their renormalized weights,
    /// for hardware linear-blend skinning. Unweighted vertices get all-zero
    /// weights (no deformation, falls back to the bind position). The bone
    /// indices match [`skinning_matrices`](Self::skinning_matrices) palette order.
    pub fn skin_attributes(&self) -> (Vec<[u32; 4]>, Vec<[f32; 4]>) {
        let infl = self.vertex_influences();
        let mut ids = Vec::with_capacity(infl.len());
        let mut wts = Vec::with_capacity(infl.len());
        for list in &infl {
            let mut v = list.clone();
            v.sort_by(|a, b| b.1.total_cmp(&a.1)); // strongest first
            v.truncate(4);
            let sum: f32 = v.iter().map(|x| x.1).sum();
            let mut id = [0u32; 4];
            let mut wt = [0.0f32; 4];
            for (k, &(bi, w)) in v.iter().enumerate() {
                id[k] = bi as u32;
                wt[k] = if sum > 0.0 { w / sum } else { 0.0 };
            }
            ids.push(id);
            wts.push(wt);
        }
        (ids, wts)
    }

    pub fn posed_meshes(&self, frame: Option<f32>) -> Vec<B3dMesh> {
        if self.bones.is_empty() || self.weight_count() == 0 {
            return self.meshes.clone();
        }
        let skin = self.skinning_matrices(frame);
        let infl = self.vertex_influences();

        self.meshes
            .iter()
            .map(|m| {
                let mut out = m.clone();
                let has_n = m.normals.len() == m.positions.len();
                for (vi, p) in m.positions.iter().enumerate() {
                    let list = if vi < infl.len() { &infl[vi] } else { continue };
                    if list.is_empty() {
                        continue; // unweighted: keep bind position
                    }
                    let mut acc = [0.0f32; 3];
                    let mut nrm = [0.0f32; 3];
                    for &(bi, w) in list {
                        let d = xform_point(&skin[bi], *p);
                        acc[0] += d[0] * w;
                        acc[1] += d[1] * w;
                        acc[2] += d[2] * w;
                        if has_n {
                            let nd = xform_dir(&skin[bi], m.normals[vi]);
                            nrm[0] += nd[0] * w;
                            nrm[1] += nd[1] * w;
                            nrm[2] += nd[2] * w;
                        }
                    }
                    out.positions[vi] = acc;
                    if has_n {
                        let len = (nrm[0] * nrm[0] + nrm[1] * nrm[1] + nrm[2] * nrm[2]).sqrt();
                        if len > 1e-8 {
                            out.normals[vi] = [nrm[0] / len, nrm[1] / len, nrm[2] / len];
                        }
                    }
                }
                out
            })
            .collect()
    }
    pub fn triangle_count(&self) -> usize {
        self.meshes.iter().map(|m| m.indices.len() / 3).sum()
    }

    /// Axis-aligned bounds `(min, max)` over all vertices. Returns zeros for an
    /// empty model.
    pub fn bounds(&self) -> ([f32; 3], [f32; 3]) {
        let mut min = [f32::MAX; 3];
        let mut max = [f32::MIN; 3];
        let mut any = false;
        for mesh in &self.meshes {
            for p in &mesh.positions {
                any = true;
                for k in 0..3 {
                    min[k] = min[k].min(p[k]);
                    max[k] = max[k].max(p[k]);
                }
            }
        }
        if any {
            (min, max)
        } else {
            ([0.0; 3], [0.0; 3])
        }
    }

    /// Parse a whole `.b3d` file image.
    pub fn parse(data: &[u8]) -> Result<B3dModel, ReadError> {
        let mut r = BlitzReader::new(data);
        let magic = r.read_tag()?;
        if &magic != b"BB3D" {
            return Err(ReadError::StringTooLong { len: 0, max: 0 }); // reuse as "bad magic"
        }
        let size = r.read_int()? as usize;
        let end = r.position() + size;
        let _version = r.read_int()?;

        let mut model = B3dModel::default();
        parse_chunks(&mut r, end.min(data.len()), &IDENTITY, &mut model)?;
        model.resolve_textures();
        Ok(model)
    }

    /// Fill each mesh's `texture` from its brush's first texture slot.
    fn resolve_textures(&mut self) {
        for mesh in &mut self.meshes {
            if mesh.brush_id < 0 {
                continue;
            }
            let Some(brush) = self.brushes.get(mesh.brush_id as usize) else {
                continue;
            };
            // Non-empty texture slots in order: slot 0 = base, slot 1 = lightmap.
            let mut slots = brush.iter().copied().filter(|&t| t >= 0);
            if let Some(tex_id) = slots.next() {
                if let Some(name) = self.textures.get(tex_id as usize) {
                    mesh.texture = Some(name.clone());
                }
                mesh.texture_flag = self.tex_flags.get(tex_id as usize).copied().unwrap_or(0);
                // Carry the texture's texcoord scale/offset so the renderer can
                // tile it like the engine's `ScaleTexture` (terrain crispness).
                mesh.uv_scale = self.tex_scales.get(tex_id as usize).copied().unwrap_or([1.0, 1.0]);
                mesh.uv_offset = self.tex_offsets.get(tex_id as usize).copied().unwrap_or([0.0, 0.0]);
            }
            // Second non-empty slot is the baked lightmap (sampled with uvs2).
            if let Some(lm_id) = slots.next() {
                if let Some(name) = self.textures.get(lm_id as usize) {
                    mesh.lightmap = Some(name.clone());
                }
            }
        }
    }
}

/// Read a `[tag:4][size:i32]` chunk header.
fn chunk_header(r: &mut BlitzReader) -> Result<([u8; 4], usize), ReadError> {
    let tag = r.read_tag()?;
    let size = r.read_int()?.max(0) as usize;
    Ok((tag, size))
}

/// Walk sibling chunks until `end`. `parent` is the accumulated node matrix.
fn parse_chunks(
    r: &mut BlitzReader,
    end: usize,
    parent: &Mat4,
    model: &mut B3dModel,
) -> Result<(), ReadError> {
    while r.position() + 8 <= end {
        let (tag, size) = chunk_header(r)?;
        let chunk_end = (r.position() + size).min(end);
        match &tag {
            b"TEXS" => parse_texs(r, chunk_end, model)?,
            b"BRUS" => parse_brus(r, chunk_end, model)?,
            b"NODE" => parse_node(r, chunk_end, parent, None, model)?,
            b"ANIM" => parse_anim(r, model)?,
            _ => {}
        }
        r.seek(chunk_end)?;
    }
    Ok(())
}

/// `ANIM`: flags(i32) · frames(i32) · fps(f32).
fn parse_anim(r: &mut BlitzReader, model: &mut B3dModel) -> Result<(), ReadError> {
    let _flags = r.read_int()?;
    let frames = r.read_int()?;
    let fps = r.read_float()?;
    model.anim = Some(B3dAnim { frames, fps });
    Ok(())
}

/// `BONE`: a sequence of `{vertex_id(i32), weight(f32)}` for the bone's node.
fn parse_bone(r: &mut BlitzReader, end: usize) -> Result<Vec<(u32, f32)>, ReadError> {
    let mut w = Vec::new();
    while r.position() + 8 <= end {
        let vid = r.read_int()? as u32;
        let weight = r.read_float()?;
        w.push((vid, weight));
    }
    Ok(w)
}

/// `KEYS`: flags(i32) then per key `frame(i32)` + optional position(3f, flag&1)
/// + scale(3f, flag&2) + rotation(4f w,x,y,z, flag&4).
fn parse_keys(r: &mut BlitzReader, end: usize) -> Result<Vec<B3dKey>, ReadError> {
    let flags = r.read_int()?;
    let (has_pos, has_scale, has_rot) = (flags & 1 != 0, flags & 2 != 0, flags & 4 != 0);
    let mut keys = Vec::new();
    while r.position() + 4 <= end {
        let frame = r.read_int()?;
        let position = if has_pos {
            Some([r.read_float()?, r.read_float()?, r.read_float()?])
        } else {
            None
        };
        let scale = if has_scale {
            Some([r.read_float()?, r.read_float()?, r.read_float()?])
        } else {
            None
        };
        let rotation = if has_rot {
            Some([r.read_float()?, r.read_float()?, r.read_float()?, r.read_float()?])
        } else {
            None
        };
        keys.push(B3dKey {
            frame,
            position,
            scale,
            rotation,
        });
    }
    Ok(keys)
}

/// `TEXS`: a sequence of textures, each `file(cstr) · flags(i32) · blend(i32) ·
/// xpos,ypos,xscale,yscale,rotation(f32×5)`.
fn parse_texs(r: &mut BlitzReader, end: usize, model: &mut B3dModel) -> Result<(), ReadError> {
    while r.position() < end {
        let file = r.read_cstr(1024)?;
        let flags = r.read_int()?;
        let _blend = r.read_int()?;
        let xpos = r.read_float()?;
        let ypos = r.read_float()?;
        let xscale = r.read_float()?;
        let yscale = r.read_float()?;
        let _rot = r.read_float()?;
        model.textures.push(file);
        model.tex_flags.push(flags);
        // Blitz `setScale` only kicks in when != 1; treat 0 as "no scale" (1).
        let sx = if xscale != 0.0 { xscale } else { 1.0 };
        let sy = if yscale != 0.0 { yscale } else { 1.0 };
        model.tex_scales.push([sx, sy]);
        model.tex_offsets.push([xpos, ypos]);
    }
    Ok(())
}

/// `BRUS`: `n_texs(i32)` then brushes, each `name(cstr) · rgba(f32×4) ·
/// shininess(f32) · blend(i32) · fx(i32) · texture_id(i32 × n_texs)`.
fn parse_brus(r: &mut BlitzReader, end: usize, model: &mut B3dModel) -> Result<(), ReadError> {
    let n_texs = r.read_int()?.clamp(0, 8) as usize;
    while r.position() < end {
        let _name = r.read_cstr(256)?;
        for _ in 0..4 {
            r.read_float()?; // rgba
        }
        let _shininess = r.read_float()?;
        let _blend = r.read_int()?;
        let _fx = r.read_int()?;
        let mut tex_ids = Vec::with_capacity(n_texs);
        for _ in 0..n_texs {
            tex_ids.push(r.read_int()?);
        }
        model.brushes.push(tex_ids);
    }
    Ok(())
}

/// `NODE`: name(cstr) · position(3f) · scale(3f) · rotation(4f, w,x,y,z) ·
/// sub-chunks. Composes the node's bind-pose matrix onto `parent`.
fn parse_node(
    r: &mut BlitzReader,
    end: usize,
    parent: &Mat4,
    parent_bone: Option<usize>,
    model: &mut B3dModel,
) -> Result<(), ReadError> {
    let name = r.read_cstr(256)?;
    let px = r.read_float()?;
    let py = r.read_float()?;
    let pz = r.read_float()?;
    let sx = r.read_float()?;
    let sy = r.read_float()?;
    let sz = r.read_float()?;
    let rw = r.read_float()?;
    let rx = r.read_float()?;
    let ry = r.read_float()?;
    let rz = r.read_float()?;
    let local = trs([px, py, pz], [rw, rx, ry, rz], [sx, sy, sz]);
    let world = mat_mul(parent, &local);

    // Every node is a skeleton joint; weights/keys filled from sub-chunks below.
    let this_bone = model.bones.len();
    model.bones.push(B3dBone {
        name,
        parent: parent_bone,
        local_bind: local,
        local_t: [px, py, pz],
        local_r: [rw, rx, ry, rz],
        local_s: [sx, sy, sz],
        bind_world: world,
        inverse_bind: invert_affine(&world),
        weights: Vec::new(),
        keys: Vec::new(),
    });

    while r.position() + 8 <= end {
        let (tag, size) = chunk_header(r)?;
        let chunk_end = (r.position() + size).min(end);
        match &tag {
            b"MESH" => {
                for mesh in parse_mesh(r, chunk_end, &world)? {
                    if !mesh.positions.is_empty() && !mesh.indices.is_empty() {
                        model.meshes.push(mesh);
                    }
                }
            }
            b"NODE" => parse_node(r, chunk_end, &world, Some(this_bone), model)?,
            b"BONE" => {
                let w = parse_bone(r, chunk_end)?;
                model.bones[this_bone].weights = w;
            }
            b"KEYS" => {
                let k = parse_keys(r, chunk_end)?;
                model.bones[this_bone].keys = k;
            }
            b"ANIM" => parse_anim(r, model)?,
            _ => {} // unknown sub-chunk
        }
        r.seek(chunk_end)?;
    }
    Ok(())
}

/// `MESH`: meshBrush(i32) · `VRTS`(shared vertices) · `TRIS`(one or more, each a
/// brush_id + index list). Emits one [`B3dMesh`] per TRIS group (so each can
/// carry its own texture), sharing the mesh's vertex data.
fn parse_mesh(r: &mut BlitzReader, end: usize, world: &Mat4) -> Result<Vec<B3dMesh>, ReadError> {
    let mesh_brush = r.read_int()?;
    let mut positions = Vec::new();
    let mut normals = Vec::new();
    let mut uvs = Vec::new();
    let mut uvs2 = Vec::new();
    let mut colors = Vec::new();
    let mut groups: Vec<(i32, Vec<u32>)> = Vec::new();

    while r.position() + 8 <= end {
        let (tag, size) = chunk_header(r)?;
        let chunk_end = (r.position() + size).min(end);
        match &tag {
            b"VRTS" => parse_vrts(r, chunk_end, world, &mut positions, &mut normals, &mut uvs, &mut uvs2, &mut colors)?,
            b"TRIS" => {
                let brush = r.read_int()?;
                let mut indices = Vec::new();
                while r.position() + 12 <= chunk_end {
                    let a = r.read_int()? as u32;
                    let b = r.read_int()? as u32;
                    let c = r.read_int()? as u32;
                    indices.extend_from_slice(&[a, b, c]);
                }
                groups.push((brush, indices));
            }
            _ => {}
        }
        r.seek(chunk_end)?;
    }

    let mut out = Vec::with_capacity(groups.len());
    for (brush, indices) in groups {
        if indices.is_empty() {
            continue;
        }
        let brush_id = if brush >= 0 { brush } else { mesh_brush };
        out.push(B3dMesh {
            positions: positions.clone(),
            normals: normals.clone(),
            uvs: uvs.clone(),
            uvs2: uvs2.clone(),
            colors: colors.clone(),
            indices,
            brush_id,
            texture: None,
            texture_flag: 0,
            uv_scale: [1.0, 1.0],
            uv_offset: [0.0, 0.0],
            lightmap: None,
        });
    }
    Ok(out)
}

/// `VRTS`: flags(i32) · tex_coord_sets(i32) · tex_coord_set_size(i32) · vertices.
/// flags&1 = normals present, flags&2 = vertex colors present.
fn parse_vrts(
    r: &mut BlitzReader,
    end: usize,
    world: &Mat4,
    positions: &mut Vec<[f32; 3]>,
    normals: &mut Vec<[f32; 3]>,
    uvs: &mut Vec<[f32; 2]>,
    uvs2: &mut Vec<[f32; 2]>,
    colors: &mut Vec<[f32; 4]>,
) -> Result<(), ReadError> {
    let flags = r.read_int()?;
    let tex_coord_sets = r.read_int()?.clamp(0, 8) as usize;
    let tex_coord_set_size = r.read_int()?.clamp(0, 4) as usize;
    let has_normal = flags & 1 != 0;
    let has_color = flags & 2 != 0;

    while r.position() < end {
        let x = r.read_float()?;
        let y = r.read_float()?;
        let z = r.read_float()?;
        positions.push(xform_point(world, [x, y, z]));

        if has_normal {
            let nx = r.read_float()?;
            let ny = r.read_float()?;
            let nz = r.read_float()?;
            normals.push(xform_dir(world, [nx, ny, nz]));
        }
        if has_color {
            colors.push([r.read_float()?, r.read_float()?, r.read_float()?, r.read_float()?]);
        }
        // Keep set 0 (base UV) and set 1 (lightmap UV); ignore any further sets.
        let mut uv = [0.0f32; 2];
        let mut uv2 = [0.0f32; 2];
        for set in 0..tex_coord_sets {
            for comp in 0..tex_coord_set_size {
                let v = r.read_float()?;
                if comp < 2 {
                    if set == 0 {
                        uv[comp] = v;
                    } else if set == 1 {
                        uv2[comp] = v;
                    }
                }
            }
        }
        if tex_coord_sets > 0 {
            uvs.push(uv);
        }
        if tex_coord_sets > 1 {
            uvs2.push(uv2);
        }
    }
    Ok(())
}

#[cfg(test)]
mod mat_tests {
    use super::*;

    #[test]
    fn identity_is_neutral() {
        let p = xform_point(&IDENTITY, [3.0, -2.0, 5.0]);
        assert_eq!(p, [3.0, -2.0, 5.0]);
    }

    #[test]
    fn bone_palette_transpose_matches_cpu() {
        // transpose16 must make a WGSL column-major `M * vec4(p,1)` equal the CPU
        // row-major xform_point(M, p) — the keystone for correct GPU skinning.
        let s = std::f32::consts::FRAC_1_SQRT_2;
        let m = trs([10.0, 5.0, -3.0], [s, 0.0, s, 0.0], [2.0, 1.0, 0.5]);
        let t = transpose16(&m);
        // Replicate WGSL: result[r] = sum_c col[c][r]*v[c]; col[c][r] = buf[c*4+r].
        let col_mul = |buf: &[f32; 16], v: [f32; 3]| -> [f32; 3] {
            let mut out = [0.0f32; 3];
            for r in 0..3 {
                out[r] = buf[r] * v[0] + buf[4 + r] * v[1] + buf[8 + r] * v[2] + buf[12 + r];
            }
            out
        };
        for p in [[1.5, -2.0, 4.0], [0.0, 0.0, 0.0], [-3.0, 7.0, 1.0]] {
            let gpu = col_mul(&t, p);
            let cpu = xform_point(&m, p);
            for k in 0..3 {
                assert!((gpu[k] - cpu[k]).abs() < 1e-4, "p{p:?} k{k}: gpu {} cpu {}", gpu[k], cpu[k]);
            }
        }
    }

    #[test]
    fn joint_pos_at_follows_animation() {
        // Root -> Head. Head's bind sits at (0,10,0); a Pos key animates it to
        // (5,10,0). joint_pos_at must return the bind for None and the animated
        // position for a frame — the fix that stops hair/weapons floating.
        let head_bind = trs([0.0, 10.0, 0.0], [1.0, 0.0, 0.0, 0.0], [1.0, 1.0, 1.0]);
        let mk = |name: &str, parent, local_bind, keys| B3dBone {
            name: name.into(),
            parent,
            local_bind,
            local_t: [0.0; 3],
            local_r: [1.0, 0.0, 0.0, 0.0],
            local_s: [1.0; 3],
            bind_world: local_bind,
            inverse_bind: IDENTITY,
            weights: Vec::new(),
            keys,
        };
        let key = |p: [f32; 3]| B3dKey { frame: 0, position: Some(p), scale: None, rotation: None };
        let model = B3dModel {
            bones: vec![
                mk("Root", None, IDENTITY, Vec::new()),
                mk("Head", Some(0), head_bind, vec![key([5.0, 10.0, 0.0]), B3dKey { frame: 2, ..key([5.0, 10.0, 0.0]) }]),
            ],
            ..Default::default()
        };
        let bind = model.joint_pos_at("Head", None).unwrap();
        assert!(bind[0].abs() < 1e-5 && (bind[1] - 10.0).abs() < 1e-5, "bind {bind:?}");
        let anim = model.joint_pos_at("Head", Some(0.0)).unwrap();
        assert!((anim[0] - 5.0).abs() < 1e-5, "animated head should track to x=5, got {anim:?}");
        assert!(model.joint_pos_at("Missing", None).is_none());
    }

    #[test]
    fn skin_attributes_invert_and_normalize() {
        // One mesh, 2 vertices; two bones weighting them.
        let bone = |weights: Vec<(u32, f32)>| B3dBone {
            name: String::new(),
            parent: None,
            local_bind: IDENTITY,
            local_t: [0.0; 3],
            local_r: [1.0, 0.0, 0.0, 0.0],
            local_s: [1.0; 3],
            bind_world: IDENTITY,
            inverse_bind: IDENTITY,
            weights,
            keys: Vec::new(),
        };
        let mut mesh = B3dMesh::default();
        mesh.positions = vec![[0.0; 3], [0.0; 3]];
        let model = B3dModel {
            meshes: vec![mesh],
            // bone 0 weights v0 by 3; bone 1 weights v0 by 1 and v1 by 1.
            bones: vec![bone(vec![(0, 3.0)]), bone(vec![(0, 1.0), (1, 1.0)])],
            ..Default::default()
        };
        let (ids, wts) = model.skin_attributes();
        assert_eq!(ids.len(), 2);
        // v0: bone 0 strongest (3/4), bone 1 (1/4); renormalized, sum ~1.
        assert_eq!(ids[0][0], 0);
        assert!((wts[0][0] - 0.75).abs() < 1e-5, "w {:?}", wts[0]);
        assert!((wts[0][1] - 0.25).abs() < 1e-5);
        assert!((wts[0].iter().sum::<f32>() - 1.0).abs() < 1e-5);
        // v1: only bone 1.
        assert_eq!(ids[1][0], 1);
        assert!((wts[1][0] - 1.0).abs() < 1e-5);
        assert!(wts[1][1..].iter().all(|&w| w == 0.0));
    }

    #[test]
    fn trs_rotates_then_translates() {
        // trs CONJUGATES the b3d quaternion, so the stored (w=cos45,y=sin45)
        // [s,0,s,0] becomes a -90deg rotation about Y, mapping +X -> +Z. With
        // translation (10,0,0): (1,0,0) -> (10,0,+1).
        let s = std::f32::consts::FRAC_1_SQRT_2;
        let m = trs([10.0, 0.0, 0.0], [s, 0.0, s, 0.0], [1.0, 1.0, 1.0]);
        let p = xform_point(&m, [1.0, 0.0, 0.0]);
        assert!((p[0] - 10.0).abs() < 1e-4, "x={}", p[0]);
        assert!(p[1].abs() < 1e-4, "y={}", p[1]);
        assert!((p[2] - 1.0).abs() < 1e-4, "z={}", p[2]);
        // Directions ignore translation.
        let d = xform_dir(&m, [1.0, 0.0, 0.0]);
        assert!((d[2] - 1.0).abs() < 1e-4 && d[0].abs() < 1e-4);
    }

    #[test]
    fn nested_matrices_compose() {
        // Parent translates +X by 5, child translates +X by 3 in parent frame.
        let parent = trs([5.0, 0.0, 0.0], [1.0, 0.0, 0.0, 0.0], [1.0; 3]);
        let child = trs([3.0, 0.0, 0.0], [1.0, 0.0, 0.0, 0.0], [1.0; 3]);
        let world = mat_mul(&parent, &child);
        assert_eq!(xform_point(&world, [0.0, 0.0, 0.0]), [8.0, 0.0, 0.0]);
    }
}
