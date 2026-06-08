//! Terrain height field for actor foot-seating.
//!
//! `P_StandardUpdate` only sends an actor's X/Z, and the engine keeps actors on
//! the ground via gravity + terrain collision (`Client.bb` applies `Gravity#` to
//! each `AI\Y#` and resets it on contact). The Rust client has no collision, so
//! it samples the ground height directly: at zone load we gather the world-space
//! near-horizontal scenery triangles into a coarse XZ grid, and `height_at(x,z)`
//! returns the highest ground Y under a point. Actors are then seated feet-on-ground
//! instead of on a stale spawn Y (which floated/sank them on varying terrain).

use std::collections::HashMap;

use glam::Vec3;

/// Cosine threshold for a triangle to count as walkable ground (normal.y).
/// ~0.5 = up to 60° slopes; steeper faces (walls, roofs sides) are ignored.
const GROUND_NORMAL_Y: f32 = 0.5;

pub struct HeightField {
    tris: Vec<[Vec3; 3]>,
    grid: HashMap<(i32, i32), Vec<u32>>,
    cell: f32,
    /// When set, `height_at` returns this constant everywhere (a flat floor) —
    /// used to seat the menu character on the set's rug at a known Y without the
    /// set's other horizontal surfaces (ceiling vaults, tables) interfering.
    flat: Option<f32>,
}

impl HeightField {
    /// Build from world-space ground triangles, bucketed into `cell`-sized XZ
    /// cells. Empty input yields a field that always returns `None`.
    pub fn build(tris: Vec<[Vec3; 3]>, cell: f32) -> HeightField {
        let cell = cell.max(1.0);
        let mut grid: HashMap<(i32, i32), Vec<u32>> = HashMap::new();
        for (i, t) in tris.iter().enumerate() {
            let xmin = t[0].x.min(t[1].x).min(t[2].x);
            let xmax = t[0].x.max(t[1].x).max(t[2].x);
            let zmin = t[0].z.min(t[1].z).min(t[2].z);
            let zmax = t[0].z.max(t[1].z).max(t[2].z);
            for cx in (xmin / cell).floor() as i32..=(xmax / cell).floor() as i32 {
                for cz in (zmin / cell).floor() as i32..=(zmax / cell).floor() as i32 {
                    grid.entry((cx, cz)).or_default().push(i as u32);
                }
            }
        }
        HeightField { tris, grid, cell, flat: None }
    }

    /// A flat field that reports `y` at every `(x, z)`.
    pub fn flat(y: f32) -> HeightField {
        HeightField { tris: Vec::new(), grid: HashMap::new(), cell: 1.0, flat: Some(y) }
    }

    pub fn is_empty(&self) -> bool {
        self.flat.is_none() && self.tris.is_empty()
    }

    /// Highest ground Y at `(x, z)`, or `None` if no ground triangle covers it.
    pub fn height_at(&self, x: f32, z: f32) -> Option<f32> {
        if let Some(y) = self.flat {
            return Some(y);
        }
        let key = ((x / self.cell).floor() as i32, (z / self.cell).floor() as i32);
        let ids = self.grid.get(&key)?;
        let mut best: Option<f32> = None;
        for &i in ids {
            if let Some(y) = tri_height(&self.tris[i as usize], x, z) {
                best = Some(best.map_or(y, |b| b.max(y)));
            }
        }
        best
    }

    /// Lowest ground Y at `(x, z)`, or `None` if no ground triangle covers it.
    /// The set's rug is its lowest horizontal surface under the character (the
    /// ceiling vault / tables sit above it), so the menu seats the character on
    /// this rather than `height_at` (which returns the highest).
    pub fn lowest_at(&self, x: f32, z: f32) -> Option<f32> {
        let key = ((x / self.cell).floor() as i32, (z / self.cell).floor() as i32);
        let ids = self.grid.get(&key)?;
        let mut best: Option<f32> = None;
        for &i in ids {
            if let Some(y) = tri_height(&self.tris[i as usize], x, z) {
                best = Some(best.map_or(y, |b| b.min(y)));
            }
        }
        best
    }

    /// Keep only near-horizontal (walkable) triangles — used while gathering.
    pub fn is_ground(a: Vec3, b: Vec3, c: Vec3) -> bool {
        let n = (b - a).cross(c - a);
        let len = n.length();
        len > 1e-6 && (n.y / len).abs() >= GROUND_NORMAL_Y
    }
}

/// Barycentric height of `(x,z)` inside triangle `t` (XZ projection), or `None`
/// if the point is outside. Interpolates Y from the three vertices.
fn tri_height(t: &[Vec3; 3], x: f32, z: f32) -> Option<f32> {
    let (a, b, c) = (t[0], t[1], t[2]);
    let d = (b.z - c.z) * (a.x - c.x) + (c.x - b.x) * (a.z - c.z);
    if d.abs() < 1e-9 {
        return None;
    }
    let w1 = ((b.z - c.z) * (x - c.x) + (c.x - b.x) * (z - c.z)) / d;
    let w2 = ((c.z - a.z) * (x - c.x) + (a.x - c.x) * (z - c.z)) / d;
    let w3 = 1.0 - w1 - w2;
    let e = -1e-4; // small slack so shared edges still hit
    if w1 >= e && w2 >= e && w3 >= e {
        Some(w1 * a.y + w2 * b.y + w3 * c.y)
    } else {
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn height_on_flat_quad() {
        // A flat ground quad at y=5 spanning [0,10]×[0,10] (two tris).
        let v = |x: f32, z: f32| Vec3::new(x, 5.0, z);
        let tris = vec![
            [v(0.0, 0.0), v(10.0, 0.0), v(10.0, 10.0)],
            [v(0.0, 0.0), v(10.0, 10.0), v(0.0, 10.0)],
        ];
        let hf = HeightField::build(tris, 8.0);
        assert_eq!(hf.height_at(5.0, 5.0), Some(5.0));
        assert_eq!(hf.height_at(1.0, 9.0), Some(5.0));
        assert_eq!(hf.height_at(-1.0, 5.0), None); // off the quad
    }

    #[test]
    fn height_interpolates_slope() {
        // A ramp from y=0 at z=0 to y=10 at z=10.
        let tris = vec![[
            Vec3::new(0.0, 0.0, 0.0),
            Vec3::new(10.0, 0.0, 0.0),
            Vec3::new(0.0, 10.0, 10.0),
        ]];
        let hf = HeightField::build(tris, 16.0);
        let y = hf.height_at(1.0, 5.0).unwrap();
        assert!((y - 5.0).abs() < 0.5, "mid-ramp ~5: {y}");
    }

    #[test]
    fn ground_filter_rejects_walls() {
        // Horizontal triangle (normal up) is ground; vertical one is not.
        assert!(HeightField::is_ground(
            Vec3::ZERO,
            Vec3::new(1.0, 0.0, 0.0),
            Vec3::new(0.0, 0.0, 1.0)
        ));
        assert!(!HeightField::is_ground(
            Vec3::ZERO,
            Vec3::new(0.0, 1.0, 0.0),
            Vec3::new(0.0, 0.0, 1.0)
        ));
    }
}
