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
fn slint_supported_backend_selector_accepts_wgpu_26_manual_configuration() {
    fn type_check(
        instance: slint::wgpu_26::wgpu::Instance,
        adapter: slint::wgpu_26::wgpu::Adapter,
        device: slint::wgpu_26::wgpu::Device,
        queue: slint::wgpu_26::wgpu::Queue,
    ) -> slint::BackendSelector {
        let configuration = slint::wgpu_26::WGPUConfiguration::Manual {
            instance,
            adapter,
            device,
            queue,
        };

        slint::BackendSelector::new().require_wgpu_26(configuration)
    }

    let _ = type_check;
}

#[test]
fn slint_image_import_is_bound_to_wgpu_26_texture() {
    fn type_check(texture: slint::wgpu_26::wgpu::Texture) {
        let _: Result<slint::Image, _> = slint::Image::try_from(texture);
    }

    let _ = type_check as fn(slint::wgpu_26::wgpu::Texture);
}

#[test]
fn nominal_gpu_resource_types_are_not_identical() {
    assert_ne!(
        TypeId::of::<wgpu22::Instance>(),
        TypeId::of::<slint::wgpu_26::wgpu::Instance>()
    );
    assert_ne!(
        TypeId::of::<wgpu22::Adapter>(),
        TypeId::of::<slint::wgpu_26::wgpu::Adapter>()
    );
    assert_ne!(
        TypeId::of::<wgpu22::Device>(),
        TypeId::of::<slint::wgpu_26::wgpu::Device>()
    );
    assert_ne!(
        TypeId::of::<wgpu22::Queue>(),
        TypeId::of::<slint::wgpu_26::wgpu::Queue>()
    );
    assert_ne!(
        TypeId::of::<wgpu22::Texture>(),
        TypeId::of::<slint::wgpu_26::wgpu::Texture>()
    );
}

#[test]
fn same_resource_compile_contract_is_rejected() {
    let cases = trybuild::TestCases::new();
    cases.compile_fail("tests/ui/wgpu22_shared_resources.rs");
}
