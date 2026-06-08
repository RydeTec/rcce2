//! Raw B3D chunk walker: report the chunk tree and counts for BONE/KEYS/ANIM,
//! to decide whether shipped actor meshes are actually skinned/animated before
//! building skinning. Independent of the project's b3d parser.
//!
//!   cargo run -p rcce-client --bin b3d_probe --release -- <path-to.b3d>

fn rd_i32(b: &[u8], o: usize) -> i32 {
    i32::from_le_bytes([b[o], b[o + 1], b[o + 2], b[o + 3]])
}
fn rd_f32(b: &[u8], o: usize) -> f32 {
    f32::from_le_bytes([b[o], b[o + 1], b[o + 2], b[o + 3]])
}

struct Stats {
    bones: u32,
    bone_weights: u32,
    keys: u32,
    keyframes: u32,
    anim_frames: i32,
    anim_fps: f32,
    nodes: u32,
    meshes: u32,
    max_depth: u32,
}

fn cstr_len(b: &[u8], o: usize) -> usize {
    let mut i = o;
    while i < b.len() && b[i] != 0 {
        i += 1;
    }
    i - o + 1 // include NUL
}

/// Walk sibling chunks in [start,end). `container` true if these are inside a
/// chunk that has sub-chunks (BB3D/NODE/MESH).
fn walk(b: &[u8], start: usize, end: usize, depth: u32, s: &mut Stats) {
    s.max_depth = s.max_depth.max(depth);
    let mut p = start;
    while p + 8 <= end {
        let tag = &b[p..p + 4];
        let size = rd_i32(b, p + 4).max(0) as usize;
        let body = p + 8;
        let cend = (body + size).min(end);
        match tag {
            b"NODE" => {
                s.nodes += 1;
                // header: name(cstr) pos(3f) scale(3f) rot(4f) = name + 40 bytes
                let nl = cstr_len(b, body);
                let sub = body + nl + 40;
                walk(b, sub, cend, depth + 1, s);
            }
            b"MESH" => {
                s.meshes += 1;
                walk(b, body + 4, cend, depth + 1, s); // skip meshBrush i32
            }
            b"BONE" => {
                s.bones += 1;
                s.bone_weights += (size / 8) as u32; // {vid i32, weight f32}
            }
            b"KEYS" => {
                s.keys += 1;
                // flags i32, then per key: frame i32 + (pos3f if&1)+(scale3f if&2)+(rot4f if&4)
                let flags = rd_i32(b, body);
                let mut per = 4; // frame
                if flags & 1 != 0 {
                    per += 12;
                }
                if flags & 2 != 0 {
                    per += 12;
                }
                if flags & 4 != 0 {
                    per += 16;
                }
                if per > 0 {
                    s.keyframes += ((size - 4) / per) as u32;
                }
            }
            b"ANIM" => {
                // flags i32, frames i32, fps f32
                s.anim_frames = rd_i32(b, body + 4);
                s.anim_fps = rd_f32(b, body + 8);
            }
            _ => {}
        }
        p = cend;
    }
}

fn main() {
    let path = std::env::args().nth(1).unwrap_or_else(|| {
        r"C:\Users\dyanr\Desktop\rcce2\data\Meshes\Actors\Humans\Male_02.b3d".to_string()
    });
    let b = std::fs::read(&path).expect("read b3d");
    assert_eq!(&b[0..4], b"BB3D", "not a b3d");
    let total = rd_i32(&b, 4) as usize;
    let mut s = Stats {
        bones: 0,
        bone_weights: 0,
        keys: 0,
        keyframes: 0,
        anim_frames: 0,
        anim_fps: 0.0,
        nodes: 0,
        meshes: 0,
        max_depth: 0,
    };
    // BB3D body starts after magic+size+version(i32) = offset 12.
    walk(&b, 12, (8 + total).min(b.len()), 0, &mut s);
    println!("{path}");
    println!(
        "  nodes={} meshes={} maxDepth={}",
        s.nodes, s.meshes, s.max_depth
    );
    println!(
        "  BONE chunks={} (total weights={}), KEYS chunks={} (keyframes={})",
        s.bones, s.bone_weights, s.keys, s.keyframes
    );
    println!("  ANIM frames={} fps={}", s.anim_frames, s.anim_fps);
    println!(
        "  => {}",
        if s.bones > 0 {
            "SKINNED + animated"
        } else {
            "static (no bones)"
        }
    );
}
