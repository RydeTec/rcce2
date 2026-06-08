//! Texture loading: decode BMP (in-crate) and PNG (`png` crate) to RGBA8, and
//! resolve the stale absolute texture paths stored in B3D files to real files
//! by basename. JPG is a later add (needs a decoder crate).

use std::path::{Path, PathBuf};

/// Decoded RGBA8 image, top-down (row 0 = top), 4 bytes/pixel.
#[derive(Debug, Clone)]
pub struct Image {
    pub width: u32,
    pub height: u32,
    pub rgba: Vec<u8>,
}

/// Load + decode a texture file, dispatching on extension. Returns `None` for
/// unreadable files or unsupported formats (e.g. JPG, for now).
pub fn load(path: &Path) -> Option<Image> {
    let bytes = std::fs::read(path).ok()?;
    match path
        .extension()
        .and_then(|e| e.to_str())
        .map(|e| e.to_ascii_lowercase())
        .as_deref()
    {
        Some("bmp") => decode_bmp(&bytes),
        Some("png") => decode_png(&bytes),
        Some("jpg") | Some("jpeg") => decode_jpeg(&bytes),
        Some("tga") => decode_tga(&bytes),
        Some("dds") => decode_dds(&bytes),
        _ => None,
    }
}

/// Decode a DXT-compressed DDS (BC1/DXT1, BC2/DXT3, BC3/DXT5) to RGBA8 — the
/// format a few prop textures use. Uncompressed/other-fourCC DDS (cubemaps,
/// spherical-harmonic maps) return `None`. Only the top mip is decoded.
pub fn decode_dds(b: &[u8]) -> Option<Image> {
    if b.len() < 128 || &b[0..4] != b"DDS " {
        return None;
    }
    let height = u32::from_le_bytes([b[12], b[13], b[14], b[15]]);
    let width = u32::from_le_bytes([b[16], b[17], b[18], b[19]]);
    if width == 0 || height == 0 || width > 16384 || height > 16384 {
        return None;
    }
    let (block_bytes, kind) = match &b[84..88] {
        b"DXT1" => (8usize, 1u8),
        b"DXT3" => (16, 3),
        b"DXT5" => (16, 5),
        _ => return None,
    };
    let data = &b[128..];
    let (w, h) = (width as usize, height as usize);
    let bw = w.div_ceil(4);
    let bh = h.div_ceil(4);
    if data.len() < bw * bh * block_bytes {
        return None;
    }

    // RGB565 -> (r,g,b) with full-range rounding.
    let rgb565 = |c: u16| -> (u8, u8, u8) {
        let r = ((c >> 11) & 0x1f) as u32;
        let g = ((c >> 5) & 0x3f) as u32;
        let bl = (c & 0x1f) as u32;
        (
            ((r * 255 + 15) / 31) as u8,
            ((g * 255 + 31) / 63) as u8,
            ((bl * 255 + 15) / 31) as u8,
        )
    };

    let mut rgba = vec![0u8; w * h * 4];
    for by in 0..bh {
        for bx in 0..bw {
            let block = &data[(by * bw + bx) * block_bytes..];

            // Per-texel alpha (16 values), and the 8-byte color sub-block.
            let mut alpha = [255u8; 16];
            let color: &[u8] = match kind {
                1 => block,
                3 => {
                    for (i, a) in alpha.iter_mut().enumerate() {
                        let byte = block[i / 2];
                        let nib = if i % 2 == 0 { byte & 0x0f } else { byte >> 4 };
                        *a = nib * 17; // 0..15 -> 0..255
                    }
                    &block[8..]
                }
                5 => {
                    let (a0, a1) = (block[0] as u16, block[1] as u16);
                    let mut lut = [0u16; 8];
                    lut[0] = a0;
                    lut[1] = a1;
                    if a0 > a1 {
                        for i in 1..7 {
                            lut[i + 1] = ((7 - i as u16) * a0 + i as u16 * a1) / 7;
                        }
                    } else {
                        for i in 1..5 {
                            lut[i + 1] = ((5 - i as u16) * a0 + i as u16 * a1) / 5;
                        }
                        lut[6] = 0;
                        lut[7] = 255;
                    }
                    let mut bits: u64 = 0;
                    for i in 0..6 {
                        bits |= (block[2 + i] as u64) << (8 * i);
                    }
                    for (i, a) in alpha.iter_mut().enumerate() {
                        *a = lut[((bits >> (3 * i)) & 0x7) as usize] as u8;
                    }
                    &block[8..]
                }
                _ => unreachable!(),
            };

            let c0 = u16::from_le_bytes([color[0], color[1]]);
            let c1 = u16::from_le_bytes([color[2], color[3]]);
            let (r0, g0, b0) = rgb565(c0);
            let (r1, g1, b1) = rgb565(c1);
            let mut pal = [[0u8; 4]; 4];
            pal[0] = [r0, g0, b0, 255];
            pal[1] = [r1, g1, b1, 255];
            let mix = |a: u8, b: u8, na: u16, nb: u16, d: u16| -> u8 {
                ((a as u16 * na + b as u16 * nb) / d) as u8
            };
            if kind == 1 && c0 <= c1 {
                // DXT1 1-bit-alpha mode: index 2 = average, index 3 = transparent.
                pal[2] = [mix(r0, r1, 1, 1, 2), mix(g0, g1, 1, 1, 2), mix(b0, b1, 1, 1, 2), 255];
                pal[3] = [0, 0, 0, 0];
            } else {
                pal[2] = [mix(r0, r1, 2, 1, 3), mix(g0, g1, 2, 1, 3), mix(b0, b1, 2, 1, 3), 255];
                pal[3] = [mix(r0, r1, 1, 2, 3), mix(g0, g1, 1, 2, 3), mix(b0, b1, 1, 2, 3), 255];
            }

            let idx_bits = u32::from_le_bytes([color[4], color[5], color[6], color[7]]);
            for py in 0..4 {
                for px in 0..4 {
                    let pix = py * 4 + px;
                    let ci = ((idx_bits >> (2 * pix)) & 0x3) as usize;
                    let mut col = pal[ci];
                    if kind != 1 {
                        col[3] = alpha[pix];
                    }
                    let (x, y) = (bx * 4 + px, by * 4 + py);
                    if x < w && y < h {
                        let o = (y * w + x) * 4;
                        rgba[o..o + 4].copy_from_slice(&col);
                    }
                }
            }
        }
    }
    Some(Image {
        width,
        height,
        rgba,
    })
}

/// Decode a TGA (true-color, uncompressed type 2 or RLE type 10, 24/32-bit) to
/// RGBA8. This is the format the foliage/tree/grass atlases use — 32-bit BGRA
/// with a keyed alpha channel for cutout leaves. Color-mapped and grayscale
/// TGAs (rare here) return `None`.
pub fn decode_tga(b: &[u8]) -> Option<Image> {
    if b.len() < 18 {
        return None;
    }
    let id_len = b[0] as usize;
    let cmap_type = b[1];
    let img_type = b[2];
    let width = u16::from_le_bytes([b[12], b[13]]) as u32;
    let height = u16::from_le_bytes([b[14], b[15]]) as u32;
    let bpp = b[16];
    let desc = b[17];

    if width == 0 || height == 0 || cmap_type != 0 {
        return None;
    }
    // Clamp absurd dimensions before allocating, matching decode_dds. A hostile
    // .tga declaring e.g. 65535x65535 @ 32bpp would otherwise force a ~17 GB
    // zero-filled allocation below (vec![0u8; width*height*bpp]) → OOM abort =
    // client crash. Real textures are well under this bound.
    if width > 16384 || height > 16384 {
        return None;
    }
    if !(bpp == 24 || bpp == 32) {
        return None;
    }
    let bytes_pp = (bpp / 8) as usize;
    let n = (width as usize) * (height as usize);
    let mut off = 18 + id_len; // no color map when cmap_type == 0
    let mut pixels = vec![0u8; n * bytes_pp];

    match img_type {
        2 => {
            // Uncompressed true-color.
            let end = off + n * bytes_pp;
            if b.len() < end {
                return None;
            }
            pixels.copy_from_slice(&b[off..end]);
        }
        10 => {
            // RLE true-color.
            let mut i = 0usize;
            while i < n {
                if off >= b.len() {
                    return None;
                }
                let packet = b[off];
                off += 1;
                let count = (packet & 0x7f) as usize + 1;
                if packet & 0x80 != 0 {
                    // Run-length: one pixel repeated `count` times.
                    if off + bytes_pp > b.len() {
                        return None;
                    }
                    for _ in 0..count {
                        if i >= n {
                            break;
                        }
                        let d = i * bytes_pp;
                        pixels[d..d + bytes_pp].copy_from_slice(&b[off..off + bytes_pp]);
                        i += 1;
                    }
                    off += bytes_pp;
                } else {
                    // Raw: `count` literal pixels.
                    if off + count * bytes_pp > b.len() {
                        return None;
                    }
                    for _ in 0..count {
                        if i >= n {
                            break;
                        }
                        let d = i * bytes_pp;
                        pixels[d..d + bytes_pp].copy_from_slice(&b[off..off + bytes_pp]);
                        off += bytes_pp;
                        i += 1;
                    }
                }
            }
        }
        _ => return None,
    }

    // TGA stores BGR(A); bit 5 of the descriptor set = top-left origin, else
    // bottom-left (flip vertically to top-down).
    let top_origin = desc & 0x20 != 0;
    let mut rgba = vec![0u8; n * 4];
    for y in 0..height as usize {
        let src_y = if top_origin {
            y
        } else {
            height as usize - 1 - y
        };
        for x in 0..width as usize {
            let s = (src_y * width as usize + x) * bytes_pp;
            let d = (y * width as usize + x) * 4;
            rgba[d] = pixels[s + 2]; // R
            rgba[d + 1] = pixels[s + 1]; // G
            rgba[d + 2] = pixels[s]; // B
            rgba[d + 3] = if bytes_pp == 4 { pixels[s + 3] } else { 255 };
        }
    }
    Some(Image {
        width,
        height,
        rgba,
    })
}

/// Decode a JPEG (RGB / grayscale) to RGBA8.
pub fn decode_jpeg(b: &[u8]) -> Option<Image> {
    let mut decoder = jpeg_decoder::Decoder::new(b);
    let pixels = decoder.decode().ok()?;
    let info = decoder.info()?;
    let (w, h) = (info.width as u32, info.height as u32);
    let n = (w as usize) * (h as usize);
    let rgba = match info.pixel_format {
        jpeg_decoder::PixelFormat::RGB24 if pixels.len() >= n * 3 => {
            let mut out = vec![0u8; n * 4];
            for i in 0..n {
                out[i * 4] = pixels[i * 3];
                out[i * 4 + 1] = pixels[i * 3 + 1];
                out[i * 4 + 2] = pixels[i * 3 + 2];
                out[i * 4 + 3] = 255;
            }
            out
        }
        jpeg_decoder::PixelFormat::L8 if pixels.len() >= n => {
            let mut out = vec![0u8; n * 4];
            for i in 0..n {
                let g = pixels[i];
                out[i * 4] = g;
                out[i * 4 + 1] = g;
                out[i * 4 + 2] = g;
                out[i * 4 + 3] = 255;
            }
            out
        }
        _ => return None,
    };
    Some(Image {
        width: w,
        height: h,
        rgba,
    })
}

/// Decode an uncompressed 24/32-bit BMP (`BITMAPINFOHEADER`) to RGBA8.
pub fn decode_bmp(b: &[u8]) -> Option<Image> {
    if b.len() < 54 || &b[0..2] != b"BM" {
        return None;
    }
    let rd_u32 = |o: usize| u32::from_le_bytes([b[o], b[o + 1], b[o + 2], b[o + 3]]);
    let rd_i32 = |o: usize| i32::from_le_bytes([b[o], b[o + 1], b[o + 2], b[o + 3]]);
    let rd_u16 = |o: usize| u16::from_le_bytes([b[o], b[o + 1]]);

    let data_offset = rd_u32(10) as usize;
    let width = rd_i32(18);
    let height_raw = rd_i32(22);
    let bpp = rd_u16(28);
    let compression = rd_u32(30);

    if width <= 0 || height_raw == 0 || !(bpp == 24 || bpp == 32) {
        return None;
    }
    // 0 = BI_RGB, 3 = BI_BITFIELDS (common for 32-bit; we treat channels as BGRA).
    if compression != 0 && compression != 3 {
        return None;
    }
    let width = width as u32;
    let bottom_up = height_raw > 0;
    let height = height_raw.unsigned_abs();
    let bytes_pp = (bpp / 8) as usize;
    let row_stride = ((width as usize * bytes_pp + 3) / 4) * 4;

    let needed = data_offset + row_stride * height as usize;
    if b.len() < needed {
        return None;
    }

    let mut rgba = vec![0u8; (width * height * 4) as usize];
    for y in 0..height as usize {
        let src_row = if bottom_up {
            height as usize - 1 - y
        } else {
            y
        };
        let row = data_offset + src_row * row_stride;
        for x in 0..width as usize {
            let p = row + x * bytes_pp;
            let (bl, gr, re) = (b[p], b[p + 1], b[p + 2]); // BMP is BGR(A)
            let o = (y * width as usize + x) * 4;
            rgba[o] = re;
            rgba[o + 1] = gr;
            rgba[o + 2] = bl;
            rgba[o + 3] = 255; // diffuse textures: force opaque
        }
    }
    Some(Image {
        width,
        height,
        rgba,
    })
}

/// Decode a PNG (RGB/RGBA/grayscale) to RGBA8.
pub fn decode_png(b: &[u8]) -> Option<Image> {
    let decoder = png::Decoder::new(b);
    let mut reader = decoder.read_info().ok()?;
    let mut buf = vec![0u8; reader.output_buffer_size()];
    let info = reader.next_frame(&mut buf).ok()?;
    let (w, h) = (info.width, info.height);
    let n = (w * h) as usize;
    let src = &buf[..info.buffer_size()];
    let rgba = match info.color_type {
        png::ColorType::Rgba => src.to_vec(),
        png::ColorType::Rgb => {
            let mut out = vec![0u8; n * 4];
            for i in 0..n {
                out[i * 4] = src[i * 3];
                out[i * 4 + 1] = src[i * 3 + 1];
                out[i * 4 + 2] = src[i * 3 + 2];
                out[i * 4 + 3] = 255;
            }
            out
        }
        png::ColorType::Grayscale => {
            let mut out = vec![0u8; n * 4];
            for i in 0..n {
                let g = src[i];
                out[i * 4] = g;
                out[i * 4 + 1] = g;
                out[i * 4 + 2] = g;
                out[i * 4 + 3] = 255;
            }
            out
        }
        _ => return None,
    };
    Some(Image {
        width: w,
        height: h,
        rgba,
    })
}

/// Apply Blitz3D **masked**-texture color-keying in place: pixels that are
/// (near-)pure black become fully transparent. This reproduces the engine's
/// `LoadTexture(..., flags)` behaviour for the `4` (mask) flag, which foliage
/// and tree-leaf billboards rely on — their cutout shape is a black background
/// the renderer keys out (the texture itself carries no alpha channel). Only
/// pixels already opaque are touched, so a real alpha channel is preserved.
///
/// The threshold is tight (all channels `< 16`) so only the keyed background is
/// removed, not legitimately dark texels (bark, shadowed cloth).
pub fn mask_black(img: &mut Image) {
    for px in img.rgba.chunks_exact_mut(4) {
        if px[3] != 0 && px[0] < 16 && px[1] < 16 && px[2] < 16 {
            px[3] = 0;
        }
    }
}

/// Decode a texture and, when `flags` carries the Blitz3D mask bit (`& 4`),
/// color-key its black background to transparent (see [`mask_black`]).
pub fn load_with_flags(path: &Path, flags: i32) -> Option<Image> {
    let mut img = load(path)?;
    if flags & 4 != 0 {
        mask_black(&mut img);
    }
    Some(img)
}

/// The basename of a (possibly Windows-absolute) texture path.
pub fn basename(stale: &str) -> &str {
    stale
        .rsplit(|c| c == '\\' || c == '/')
        .next()
        .unwrap_or(stale)
}

/// Find a texture file by basename (case-insensitive) under any of `roots`,
/// searched recursively. Returns the first match.
pub fn find_texture(roots: &[PathBuf], stale: &str) -> Option<PathBuf> {
    let target = basename(stale).to_ascii_lowercase();
    if target.is_empty() {
        return None;
    }
    for root in roots {
        if let Some(found) = walk_find(root, &target, 0) {
            return Some(found);
        }
    }
    None
}

fn walk_find(dir: &Path, target_lower: &str, depth: usize) -> Option<PathBuf> {
    if depth > 8 {
        return None;
    }
    let entries = std::fs::read_dir(dir).ok()?;
    let mut subdirs = Vec::new();
    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_dir() {
            subdirs.push(path);
        } else if let Some(name) = path.file_name().and_then(|n| n.to_str()) {
            if name.to_ascii_lowercase() == target_lower {
                return Some(path);
            }
        }
    }
    for sub in subdirs {
        if let Some(found) = walk_find(&sub, target_lower, depth + 1) {
            return Some(found);
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn mask_black_keys_only_black_opaque() {
        let mut img = Image {
            width: 4,
            height: 1,
            rgba: vec![
                0, 0, 0, 255, // pure black opaque -> keyed to a=0
                10, 8, 12, 255, // near-black opaque -> keyed
                40, 0, 0, 255, // dark red but channel >=16 -> kept
                0, 0, 0, 0, // already transparent -> untouched (still 0)
            ],
        };
        mask_black(&mut img);
        assert_eq!(img.rgba[3], 0, "pure black keyed");
        assert_eq!(img.rgba[7], 0, "near-black keyed");
        assert_eq!(img.rgba[11], 255, "dark-red kept opaque");
        assert_eq!(img.rgba[15], 0, "transparent untouched");
    }

    #[test]
    fn basename_handles_windows_paths() {
        assert_eq!(basename(r"C:\Users\X\Desktop\Body.bmp"), "Body.bmp");
        assert_eq!(basename("a/b/stag_1.jpg"), "stag_1.jpg");
        assert_eq!(basename("plain.png"), "plain.png");
    }

    #[test]
    fn decode_tiny_32bit_tga() {
        // 2x1 uncompressed (type 2) 32-bit TGA, bottom-left origin. Stored
        // BGRA. Pixel0 = red opaque, pixel1 = green with alpha 0 (keyed out).
        let mut b = vec![0u8; 18 + 2 * 4];
        b[2] = 2; // uncompressed true-color
        b[12] = 2; // width = 2
        b[14] = 1; // height = 1
        b[16] = 32; // bpp
        let px = 18;
        // pixel0 BGRA = (0,0,255,255) -> red
        b[px + 2] = 255;
        b[px + 3] = 255;
        // pixel1 BGRA = (0,255,0,0) -> green, alpha 0
        b[px + 4 + 1] = 255;
        b[px + 4 + 3] = 0;
        let img = decode_tga(&b).expect("decode tga");
        assert_eq!((img.width, img.height), (2, 1));
        assert_eq!(&img.rgba[0..4], &[255, 0, 0, 255]); // red opaque
        assert_eq!(&img.rgba[4..8], &[0, 255, 0, 0]); // green, alpha preserved
    }

    #[test]
    fn decode_rle_tga_runs() {
        // 4x1 RLE (type 10) 24-bit TGA: one RLE packet of 4 identical blue px.
        let mut b = vec![0u8; 18];
        b[2] = 10; // RLE true-color
        b[12] = 4; // width = 4
        b[14] = 1; // height = 1
        b[16] = 24; // bpp
        // RLE packet: 0x80 | (4-1) = 0x83, then one BGR pixel (255,0,0)=blue.
        b.push(0x83);
        b.extend_from_slice(&[255, 0, 0]);
        let img = decode_tga(&b).expect("decode rle tga");
        assert_eq!((img.width, img.height), (4, 1));
        for i in 0..4 {
            assert_eq!(&img.rgba[i * 4..i * 4 + 4], &[0, 0, 255, 255]);
        }
    }

    #[test]
    fn decode_dxt1_solid_block() {
        // Minimal DDS: 4x4 DXT1, one block. c0 = white (0xFFFF), c1 = black,
        // all indices 0 -> every texel = c0 = white opaque.
        let mut b = vec![0u8; 128];
        b[0..4].copy_from_slice(b"DDS ");
        b[12..16].copy_from_slice(&4u32.to_le_bytes()); // height
        b[16..20].copy_from_slice(&4u32.to_le_bytes()); // width
        b[84..88].copy_from_slice(b"DXT1");
        // color block: c0=0xFFFF, c1=0x0000, indices=0
        b.extend_from_slice(&[0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00]);
        let img = decode_dds(&b).expect("decode dxt1");
        assert_eq!((img.width, img.height), (4, 4));
        for px in img.rgba.chunks_exact(4) {
            assert_eq!(px, &[255, 255, 255, 255]);
        }
    }

    #[test]
    fn decode_tiny_24bit_bmp() {
        // 2x2 24-bit BMP, bottom-up. Row stride = ((2*3+3)/4)*4 = 8.
        // Bottom row then top row. Pixels are BGR.
        let mut b = vec![0u8; 54 + 8 * 2];
        b[0] = b'B';
        b[1] = b'M';
        b[10] = 54; // data offset
        b[14] = 40; // header size
        b[18] = 2; // width = 2
        b[22] = 2; // height = 2 (bottom-up)
        b[28] = 24; // bpp
        // bottom row (becomes output row 1): pixel0 = blue (B=255), pixel1 = green
        let base = 54;
        b[base] = 255; // B
        b[base + 3 + 1] = 255; // G of pixel1
        // top row (output row 0): pixel0 = red
        let top = 54 + 8;
        b[top + 2] = 255; // R
        let img = decode_bmp(&b).expect("decode");
        assert_eq!((img.width, img.height), (2, 2));
        // output row 0, pixel0 = red
        assert_eq!(&img.rgba[0..4], &[255, 0, 0, 255]);
        // output row 1, pixel0 = blue
        let r1 = (2 * 1) * 4;
        assert_eq!(&img.rgba[r1..r1 + 4], &[0, 0, 255, 255]);
    }
}
