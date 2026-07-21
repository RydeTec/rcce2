fn try_to_share_existing_resources(
    instance: wgpu22::Instance,
    adapter: wgpu22::Adapter,
    device: wgpu22::Device,
    queue: wgpu22::Queue,
    caller_owned_texture: wgpu22::Texture,
) {
    let configuration = slint::wgpu_26::WGPUConfiguration::Manual {
        instance,
        adapter,
        device,
        queue,
    };
    let _selector = slint::BackendSelector::new().require_wgpu_26(configuration);

    let _: Result<slint::Image, _> = slint::Image::try_from(caller_owned_texture);
}

fn main() {}
