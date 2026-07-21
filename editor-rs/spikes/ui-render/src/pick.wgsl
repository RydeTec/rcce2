struct PickUniform {
    width: u32,
    base: u32,
    padding_a: u32,
    padding_b: u32,
}

@group(0) @binding(0) var<uniform> pick: PickUniform;

@vertex
fn vs_main(@builtin(vertex_index) index: u32) -> @builtin(position) vec4<f32> {
    var positions = array<vec2<f32>, 3>(
        vec2<f32>(-1.0, -1.0),
        vec2<f32>(3.0, -1.0),
        vec2<f32>(-1.0, 3.0),
    );
    return vec4<f32>(positions[index], 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) u32 {
    let side = select(1u, 2u, position.x >= f32(max(pick.width, 1u)) * 0.5);
    return pick.base + side;
}
