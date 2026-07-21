fn try_to_share_existing_resources(
    renderer: &mut iced_wgpu::Renderer,
    engine: &mut iced_wgpu::Engine,
    adapter: &wgpu22::Adapter,
    device: &wgpu22::Device,
    queue: &wgpu22::Queue,
    encoder: &mut wgpu22::CommandEncoder,
    caller_owned_view: &wgpu22::TextureView,
    viewport: &iced_wgpu::graphics::Viewport,
) {
    let _new_engine = iced_wgpu::Engine::new(
        adapter,
        device,
        queue,
        iced_wgpu::wgpu::TextureFormat::Rgba8UnormSrgb,
        None,
    );

    renderer.present::<&str>(
        engine,
        device,
        queue,
        encoder,
        None,
        iced_wgpu::wgpu::TextureFormat::Rgba8UnormSrgb,
        caller_owned_view,
        viewport,
        &[],
    );
}

fn main() {}
