use std::any::TypeId;

#[test]
fn rcce_world_view_constructor_accepts_the_workspace_wgpu_22_device() {
    fn type_check(device: &wgpu22::Device) {
        let _ =
            rcce_render::WorldView::new(device, wgpu22::TextureFormat::Rgba8UnormSrgb, 320, 200);
    }

    let _ = type_check as fn(&wgpu22::Device);
}

#[test]
fn iced_engine_constructor_is_bound_to_iced_wgpu_types() {
    let _: fn(
        &iced_wgpu::wgpu::Adapter,
        &iced_wgpu::wgpu::Device,
        &iced_wgpu::wgpu::Queue,
        iced_wgpu::wgpu::TextureFormat,
        Option<iced_wgpu::graphics::Antialiasing>,
    ) -> iced_wgpu::Engine = iced_wgpu::Engine::new;
}

#[test]
fn nominal_gpu_resource_types_are_not_identical() {
    assert_ne!(
        TypeId::of::<wgpu22::Adapter>(),
        TypeId::of::<iced_wgpu::wgpu::Adapter>()
    );
    assert_ne!(
        TypeId::of::<wgpu22::Device>(),
        TypeId::of::<iced_wgpu::wgpu::Device>()
    );
    assert_ne!(
        TypeId::of::<wgpu22::Queue>(),
        TypeId::of::<iced_wgpu::wgpu::Queue>()
    );
    assert_ne!(
        TypeId::of::<wgpu22::TextureView>(),
        TypeId::of::<iced_wgpu::wgpu::TextureView>()
    );
}

#[test]
fn same_resource_compile_contract_is_rejected() {
    let cases = trybuild::TestCases::new();
    cases.compile_fail("tests/ui/wgpu22_shared_resources.rs");
}
