//! Tiny dev helper: decode a BMP (or any texture `rcce_data::texture::load`
//! handles) and re-encode it as PNG, so opaque `.bmp` GUI skins can be viewed.
//! Usage: `bmp2png <input> <output.png>`.

fn main() {
    let mut args = std::env::args().skip(1);
    let inp = args.next().expect("usage: bmp2png <input> <output.png>");
    let out = args.next().expect("usage: bmp2png <input> <output.png>");
    let img = rcce_data::texture::load(std::path::Path::new(&inp)).expect("load failed");
    let file = std::fs::File::create(&out).expect("create output");
    let mut enc = png::Encoder::new(std::io::BufWriter::new(file), img.width, img.height);
    enc.set_color(png::ColorType::Rgba);
    enc.set_depth(png::BitDepth::Eight);
    enc.write_header().unwrap().write_image_data(&img.rgba).unwrap();
    eprintln!("{}x{} -> {out}", img.width, img.height);
}
