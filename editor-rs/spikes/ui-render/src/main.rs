use bytemuck::{Pod, Zeroable};
use egui::{Color32, Key, RichText, Sense, TextureId, Vec2, Widget};
use egui_wgpu::{Renderer, ScreenDescriptor};
use rcce_render::{view_proj, WorldView};
use rcce_ui_render_spike::{
    evidence::{current_gate_results, CandidateDecision},
    harness::{physical_extent, Recorder, TraceConfig},
    picking::expected_pick_id,
    provenance::{cargo_lock_sha256, executable_source_sha256, hash_bytes},
    state::{camera_angle, HarnessSnapshot, InputAction},
};
use serde::Serialize;
use std::{
    fs,
    path::{Path, PathBuf},
    sync::{mpsc, Arc},
    time::{Duration, Instant},
};
use wgpu::util::DeviceExt;
use winit::{
    application::ApplicationHandler,
    dpi::LogicalSize,
    event::WindowEvent,
    event_loop::{ActiveEventLoop, ControlFlow, EventLoop, EventLoopProxy},
    window::{Window, WindowAttributes, WindowId},
};

const VIEWPORT_FORMAT: wgpu::TextureFormat = wgpu::TextureFormat::Rgba8UnormSrgb;
const PICK_FORMAT: wgpu::TextureFormat = wgpu::TextureFormat::R32Uint;

#[derive(Debug)]
enum UserEvent {
    Accessibility(egui_winit::accesskit_winit::Event),
    Repaint,
}

impl From<egui_winit::accesskit_winit::Event> for UserEvent {
    fn from(value: egui_winit::accesskit_winit::Event) -> Self {
        Self::Accessibility(value)
    }
}

#[derive(Debug)]
struct Args {
    output: PathBuf,
    frames: Option<u64>,
    auto_loss_frame: Option<u64>,
    screenshot: Option<PathBuf>,
    trace: PathBuf,
    viewport_count: u8,
    scale_percent: u16,
    cache_state: String,
}

impl Args {
    fn parse() -> Self {
        let mut output = PathBuf::from("editor-rs/spikes/ui-render/evidence-out");
        let mut frames = None;
        let mut auto_loss_frame = None;
        let mut screenshot = None;
        let mut trace = PathBuf::from("editor-rs/spikes/ui-render/traces/standard-v1.json");
        let mut viewport_count = 2;
        let mut scale_percent = 100;
        let mut cache_state = "warm".to_owned();
        let mut values = std::env::args().skip(1);
        while let Some(arg) = values.next() {
            match arg.as_str() {
                "--output" => output = values.next().map_or(output, PathBuf::from),
                "--frames" => frames = values.next().and_then(|value| value.parse().ok()),
                "--auto-loss-frame" => {
                    auto_loss_frame = values.next().and_then(|value| value.parse().ok());
                }
                "--screenshot" => screenshot = values.next().map(PathBuf::from),
                "--trace" => trace = values.next().map_or(trace, PathBuf::from),
                "--viewport-count" => {
                    viewport_count = values
                        .next()
                        .and_then(|value| value.parse().ok())
                        .unwrap_or(0);
                }
                "--scale-percent" => {
                    scale_percent = values
                        .next()
                        .and_then(|value| value.parse().ok())
                        .unwrap_or(0);
                }
                "--cache-state" => cache_state = values.next().unwrap_or_default(),
                _ => eprintln!("[ui-spike] ignoring unknown argument: {arg}"),
            }
        }
        Self {
            output,
            frames,
            auto_loss_frame,
            screenshot,
            trace,
            viewport_count,
            scale_percent,
            cache_state,
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Pod, Zeroable)]
struct PickUniform {
    width: u32,
    base: u32,
    _padding: [u32; 2],
}

struct PickTarget {
    texture: wgpu::Texture,
    view: wgpu::TextureView,
    readback: wgpu::Buffer,
    uniform: wgpu::Buffer,
    bind_group: wgpu::BindGroup,
    pipeline: wgpu::RenderPipeline,
    width: u32,
    height: u32,
    viewport: usize,
}

impl PickTarget {
    fn new(device: &wgpu::Device, width: u32, height: u32, viewport: usize) -> Self {
        let width = width.max(1);
        let height = height.max(1);
        let texture = pick_texture(device, width, height);
        let view = texture.create_view(&Default::default());
        let readback = device.create_buffer(&wgpu::BufferDescriptor {
            label: Some("ui-spike-pick-readback"),
            size: wgpu::COPY_BYTES_PER_ROW_ALIGNMENT.into(),
            usage: wgpu::BufferUsages::COPY_DST | wgpu::BufferUsages::MAP_READ,
            mapped_at_creation: false,
        });
        let uniform_data = PickUniform {
            width,
            base: (u32::try_from(viewport).unwrap_or(u32::MAX) + 1) * 100,
            _padding: [0; 2],
        };
        let uniform = device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
            label: Some("ui-spike-pick-uniform"),
            contents: bytemuck::bytes_of(&uniform_data),
            usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST,
        });
        let layout = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("ui-spike-pick-layout"),
            entries: &[wgpu::BindGroupLayoutEntry {
                binding: 0,
                visibility: wgpu::ShaderStages::FRAGMENT,
                ty: wgpu::BindingType::Buffer {
                    ty: wgpu::BufferBindingType::Uniform,
                    has_dynamic_offset: false,
                    min_binding_size: None,
                },
                count: None,
            }],
        });
        let bind_group = device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: Some("ui-spike-pick-bind-group"),
            layout: &layout,
            entries: &[wgpu::BindGroupEntry {
                binding: 0,
                resource: uniform.as_entire_binding(),
            }],
        });
        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("ui-spike-pick-shader"),
            source: wgpu::ShaderSource::Wgsl(include_str!("pick.wgsl").into()),
        });
        let pipeline_layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: Some("ui-spike-pick-pipeline-layout"),
            bind_group_layouts: &[&layout],
            push_constant_ranges: &[],
        });
        let pipeline = device.create_render_pipeline(&wgpu::RenderPipelineDescriptor {
            label: Some("ui-spike-pick-pipeline"),
            layout: Some(&pipeline_layout),
            vertex: wgpu::VertexState {
                module: &shader,
                entry_point: "vs_main",
                compilation_options: Default::default(),
                buffers: &[],
            },
            fragment: Some(wgpu::FragmentState {
                module: &shader,
                entry_point: "fs_main",
                compilation_options: Default::default(),
                targets: &[Some(wgpu::ColorTargetState {
                    format: PICK_FORMAT,
                    blend: None,
                    write_mask: wgpu::ColorWrites::ALL,
                })],
            }),
            primitive: Default::default(),
            depth_stencil: None,
            multisample: Default::default(),
            multiview: None,
            cache: None,
        });
        Self {
            texture,
            view,
            readback,
            uniform,
            bind_group,
            pipeline,
            width,
            height,
            viewport,
        }
    }

    fn resize(&mut self, device: &wgpu::Device, queue: &wgpu::Queue, width: u32, height: u32) {
        let width = width.max(1);
        let height = height.max(1);
        if self.width == width && self.height == height {
            return;
        }
        self.width = width;
        self.height = height;
        self.texture = pick_texture(device, width, height);
        self.view = self.texture.create_view(&Default::default());
        let data = PickUniform {
            width,
            base: (u32::try_from(self.viewport).unwrap_or(u32::MAX) + 1) * 100,
            _padding: [0; 2],
        };
        queue.write_buffer(&self.uniform, 0, bytemuck::bytes_of(&data));
    }

    fn read(
        &self,
        device: &wgpu::Device,
        queue: &wgpu::Queue,
        x: u32,
        y: u32,
    ) -> Result<u32, String> {
        let x = x.min(self.width.saturating_sub(1));
        let y = y.min(self.height.saturating_sub(1));
        let mut encoder = device.create_command_encoder(&wgpu::CommandEncoderDescriptor {
            label: Some("ui-spike-pick-encoder"),
        });
        {
            let mut pass = encoder.begin_render_pass(&wgpu::RenderPassDescriptor {
                label: Some("ui-spike-pick-pass"),
                color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                    view: &self.view,
                    resolve_target: None,
                    ops: wgpu::Operations {
                        load: wgpu::LoadOp::Clear(wgpu::Color::TRANSPARENT),
                        store: wgpu::StoreOp::Store,
                    },
                })],
                depth_stencil_attachment: None,
                timestamp_writes: None,
                occlusion_query_set: None,
            });
            pass.set_pipeline(&self.pipeline);
            pass.set_bind_group(0, &self.bind_group, &[]);
            pass.draw(0..3, 0..1);
        }
        encoder.copy_texture_to_buffer(
            wgpu::ImageCopyTexture {
                texture: &self.texture,
                mip_level: 0,
                origin: wgpu::Origin3d { x, y, z: 0 },
                aspect: wgpu::TextureAspect::All,
            },
            wgpu::ImageCopyBuffer {
                buffer: &self.readback,
                layout: wgpu::ImageDataLayout {
                    offset: 0,
                    bytes_per_row: Some(wgpu::COPY_BYTES_PER_ROW_ALIGNMENT),
                    rows_per_image: Some(1),
                },
            },
            wgpu::Extent3d {
                width: 1,
                height: 1,
                depth_or_array_layers: 1,
            },
        );
        queue.submit(Some(encoder.finish()));
        let slice = self.readback.slice(..4);
        let (sender, receiver) = mpsc::sync_channel(1);
        slice.map_async(wgpu::MapMode::Read, move |result| {
            let _ = sender.send(result);
        });
        device.poll(wgpu::Maintain::Wait);
        receiver
            .recv()
            .map_err(|error| error.to_string())?
            .map_err(|error| error.to_string())?;
        let mapped = slice.get_mapped_range();
        let id = u32::from_le_bytes(mapped[..4].try_into().map_err(|_| "short pick mapping")?);
        drop(mapped);
        self.readback.unmap();
        Ok(id)
    }
}

fn pick_texture(device: &wgpu::Device, width: u32, height: u32) -> wgpu::Texture {
    device.create_texture(&wgpu::TextureDescriptor {
        label: Some("ui-spike-pick-target"),
        size: wgpu::Extent3d {
            width,
            height,
            depth_or_array_layers: 1,
        },
        mip_level_count: 1,
        sample_count: 1,
        dimension: wgpu::TextureDimension::D2,
        format: PICK_FORMAT,
        usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::COPY_SRC,
        view_formats: &[],
    })
}

struct ViewportTarget {
    texture: wgpu::Texture,
    view: wgpu::TextureView,
    texture_id: TextureId,
    world: WorldView,
    picking: PickTarget,
    width: u32,
    height: u32,
}

impl ViewportTarget {
    fn new(device: &wgpu::Device, renderer: &mut Renderer, viewport: usize) -> Self {
        let width = 512;
        let height = 320;
        let texture = viewport_texture(device, width, height);
        let view = texture.create_view(&Default::default());
        let texture_id = renderer.register_native_texture(device, &view, wgpu::FilterMode::Linear);
        Self {
            texture,
            view,
            texture_id,
            world: WorldView::new(device, VIEWPORT_FORMAT, width, height),
            picking: PickTarget::new(device, width, height, viewport),
            width,
            height,
        }
    }

    fn resize(
        &mut self,
        device: &wgpu::Device,
        queue: &wgpu::Queue,
        renderer: &mut Renderer,
        size: [u32; 2],
    ) {
        let [width, height] = size;
        if self.width == width && self.height == height {
            return;
        }
        self.width = width;
        self.height = height;
        self.texture = viewport_texture(device, width, height);
        self.view = self.texture.create_view(&Default::default());
        renderer.update_egui_texture_from_wgpu_texture(
            device,
            &self.view,
            wgpu::FilterMode::Linear,
            self.texture_id,
        );
        self.world.resize(device, width, height);
        self.picking.resize(device, queue, width, height);
    }

    fn render(
        &self,
        device: &wgpu::Device,
        queue: &wgpu::Queue,
        frame: u64,
        viewport: usize,
        reduced_motion: bool,
        yaw_offset: f32,
    ) {
        let angle = camera_angle(frame, viewport, reduced_motion, yaw_offset);
        let eye = [angle.sin() * 18.0, 9.0, angle.cos() * 18.0];
        let matrix = view_proj(eye, [0.0, 0.0, 0.0], self.width as f32 / self.height as f32);
        self.world.render(
            device,
            queue,
            &self.view,
            matrix,
            eye,
            [0.10, 0.12, 0.17],
            1.0,
            250.0,
            [0.45, 0.48, 0.52],
            [0.35, 1.0, 0.25],
            wgpu::Color {
                r: 0.05,
                g: 0.06,
                b: 0.09,
                a: 1.0,
            },
            angle,
            frame as f32 / 60.0,
            0.0,
            [0.0, 0.0, 0.0],
        );
    }
}

fn viewport_texture(device: &wgpu::Device, width: u32, height: u32) -> wgpu::Texture {
    device.create_texture(&wgpu::TextureDescriptor {
        label: Some("ui-spike-world-target"),
        size: wgpu::Extent3d {
            width: width.max(1),
            height: height.max(1),
            depth_or_array_layers: 1,
        },
        mip_level_count: 1,
        sample_count: 1,
        dimension: wgpu::TextureDimension::D2,
        format: VIEWPORT_FORMAT,
        usage: wgpu::TextureUsages::RENDER_ATTACHMENT
            | wgpu::TextureUsages::TEXTURE_BINDING
            | wgpu::TextureUsages::COPY_SRC,
        view_formats: &[],
    })
}

struct GpuState {
    _instance: wgpu::Instance,
    surface: wgpu::Surface<'static>,
    device: wgpu::Device,
    queue: wgpu::Queue,
    config: wgpu::SurfaceConfiguration,
    renderer: Renderer,
    viewports: [ViewportTarget; 2],
    adapter_info: wgpu::AdapterInfo,
    generation: u64,
}

impl GpuState {
    fn new(
        window: Arc<Window>,
        generation: u64,
        lost_tx: mpsc::Sender<(wgpu::DeviceLostReason, String)>,
    ) -> Result<Self, String> {
        let instance = wgpu::Instance::default();
        let surface = instance
            .create_surface(window.clone())
            .map_err(|error| error.to_string())?;
        let adapter = pollster::block_on(instance.request_adapter(&wgpu::RequestAdapterOptions {
            power_preference: wgpu::PowerPreference::HighPerformance,
            force_fallback_adapter: false,
            compatible_surface: Some(&surface),
        }))
        .ok_or("no compatible adapter")?;
        let adapter_info = adapter.get_info();
        let (device, queue) = pollster::block_on(adapter.request_device(
            &wgpu::DeviceDescriptor {
                label: Some("rcce-ui-render-spike-single-device"),
                required_features: wgpu::Features::empty(),
                required_limits:
                    wgpu::Limits::downlevel_defaults().using_resolution(adapter.limits()),
                memory_hints: wgpu::MemoryHints::Performance,
            },
            None,
        ))
        .map_err(|error| error.to_string())?;
        device.set_device_lost_callback(move |reason, message| {
            let _ = lost_tx.send((reason, message));
        });
        let caps = surface.get_capabilities(&adapter);
        let format = caps
            .formats
            .iter()
            .copied()
            .find(|format| !format.is_srgb())
            .unwrap_or(caps.formats[0]);
        let size = window.inner_size();
        let mut usage = wgpu::TextureUsages::RENDER_ATTACHMENT;
        if caps.usages.contains(wgpu::TextureUsages::COPY_SRC) {
            usage |= wgpu::TextureUsages::COPY_SRC;
        }
        let config = wgpu::SurfaceConfiguration {
            usage,
            format,
            width: size.width.max(1),
            height: size.height.max(1),
            present_mode: wgpu::PresentMode::Fifo,
            desired_maximum_frame_latency: 2,
            alpha_mode: caps.alpha_modes[0],
            view_formats: vec![],
        };
        surface.configure(&device, &config);
        let mut renderer = Renderer::new(&device, format, None, 1, false);
        let viewports = [
            ViewportTarget::new(&device, &mut renderer, 0),
            ViewportTarget::new(&device, &mut renderer, 1),
        ];
        Ok(Self {
            _instance: instance,
            surface,
            device,
            queue,
            config,
            renderer,
            viewports,
            adapter_info,
            generation,
        })
    }

    fn resize_surface(&mut self, width: u32, height: u32) {
        if width > 0 && height > 0 {
            self.config.width = width;
            self.config.height = height;
            self.surface.configure(&self.device, &self.config);
        }
    }
}

#[derive(Default)]
struct UiModel {
    snapshot: HarnessSnapshot,
    rows_built: usize,
    status: String,
    last_pick: Option<(usize, u32, u32, Duration)>,
    request_loss: bool,
    request_file: bool,
}

#[derive(Default)]
struct UiFrame {
    viewport_points: [Vec2; 2],
    pick_requests: Vec<(usize, u32, u32)>,
}

impl UiModel {
    fn show(
        &mut self,
        context: &egui::Context,
        texture_ids: [TextureId; 2],
        viewport_count: usize,
    ) -> UiFrame {
        self.rows_built = 0;
        if context.input(|input| input.key_pressed(Key::ArrowDown)) {
            self.snapshot.apply(InputAction::SelectNext);
        }
        if context.input(|input| input.key_pressed(Key::ArrowUp)) {
            self.snapshot.apply(InputAction::SelectPrevious);
        }
        let mut frame = UiFrame::default();
        egui::TopBottomPanel::top("test-controls").show(context, |ui| {
            ui.horizontal_wrapped(|ui| {
                ui.heading("RCCE UI/render decision spike");
                ui.separator();
                if ui.button("Open project copy…").clicked() {
                    self.request_file = true;
                }
                if ui.button("Simulate device loss").clicked() {
                    self.request_loss = true;
                }
                let mut reduced_motion = self.snapshot.reduced_motion;
                if ui.checkbox(&mut reduced_motion, "Reduced motion").changed() {
                    self.snapshot
                        .apply(InputAction::SetReducedMotion(reduced_motion));
                }
                ui.label("F6/Tab: focus · arrows: catalog · Enter/Space: activate");
            });
        });
        egui::SidePanel::left("virtual-catalog")
            .resizable(true)
            .default_width(260.0)
            .show(context, |ui| {
                ui.heading("Virtualized catalog");
                ui.label("100,000 deterministic synthetic rows");
                let row_height = ui.spacing().interact_size.y;
                egui::ScrollArea::vertical().show_rows(ui, row_height, 100_000, |ui, range| {
                    self.rows_built += range.len();
                    for row in range {
                        if ui
                            .selectable_label(
                                self.snapshot.selected_row == row,
                                format!("Synthetic record {row:06}"),
                            )
                            .clicked()
                        {
                            self.snapshot.selected_row = row;
                        }
                    }
                });
            });
        egui::TopBottomPanel::bottom("evidence-status").show(context, |ui| {
            ui.horizontal_wrapped(|ui| {
                ui.label(RichText::new("Evidence status:").strong());
                ui.label(if self.status.is_empty() {
                    "candidate unselected; mandatory Windows observations remain"
                } else {
                    &self.status
                });
                ui.add(egui::ProgressBar::new(0.55).text("representative progress 55%"));
                if let Some((viewport, actual, expected, duration)) = self.last_pick {
                    let color = if actual == expected {
                        Color32::LIGHT_GREEN
                    } else {
                        Color32::LIGHT_RED
                    };
                    ui.colored_label(
                        color,
                        format!(
                            "viewport {} pick actual={actual} expected={expected} {:.2} ms",
                            viewport + 1,
                            duration.as_secs_f64() * 1_000.0
                        ),
                    );
                }
            });
        });
        egui::CentralPanel::default().show(context, |ui| {
            ui.heading("Two independent returned-texture viewports");
            ui.label(
                "This neutral harness does not prescribe a production layout or visual design.",
            );
            ui.columns(viewport_count, |columns| {
                for (viewport, column) in columns.iter_mut().enumerate() {
                    column.horizontal(|ui| {
                        ui.label(format!("Viewport {}", viewport + 1));
                        if ui.button("Orbit left").clicked() {
                            self.snapshot.apply(InputAction::OrbitLeft(viewport));
                        }
                        if ui.button("Orbit right").clicked() {
                            self.snapshot.apply(InputAction::OrbitRight(viewport));
                        }
                    });
                    let available = column.available_size();
                    let size = Vec2::new(available.x.max(120.0), (available.y - 8.0).max(120.0));
                    frame.viewport_points[viewport] = size;
                    let response = egui::Image::new((texture_ids[viewport], size))
                        .sense(Sense::click())
                        .ui(column);
                    let center = response.rect.center();
                    column.painter().line_segment(
                        [
                            egui::pos2(center.x - 12.0, center.y),
                            egui::pos2(center.x + 12.0, center.y),
                        ],
                        egui::Stroke::new(2.0, Color32::YELLOW),
                    );
                    column.painter().line_segment(
                        [
                            egui::pos2(center.x, center.y - 12.0),
                            egui::pos2(center.x, center.y + 12.0),
                        ],
                        egui::Stroke::new(2.0, Color32::YELLOW),
                    );
                    if let Some(pointer) = response
                        .interact_pointer_pos()
                        .filter(|_| response.clicked())
                    {
                        let local = pointer - response.rect.min;
                        let x = (local.x * context.pixels_per_point()).max(0.0) as u32;
                        let y = (local.y * context.pixels_per_point()).max(0.0) as u32;
                        frame.pick_requests.push((viewport, x, y));
                    }
                }
            });
        });
        frame
    }
}

struct SpikeApp {
    args: Args,
    window: Option<Arc<Window>>,
    egui_context: egui::Context,
    egui_winit: Option<egui_winit::State>,
    proxy: EventLoopProxy<UserEvent>,
    gpu: Option<GpuState>,
    lost_tx: mpsc::Sender<(wgpu::DeviceLostReason, String)>,
    lost_rx: mpsc::Receiver<(wgpu::DeviceLostReason, String)>,
    ui: UiModel,
    recorder: Recorder,
    trace: TraceConfig,
    frame: u64,
    loss_requested_at: Option<Instant>,
    callback_observed: bool,
    picks: Vec<PickEvidence>,
}

impl SpikeApp {
    fn new(args: Args, proxy: EventLoopProxy<UserEvent>) -> Result<Self, String> {
        let trace = TraceConfig::load_checked(&args.trace).map_err(|error| error.to_string())?;
        if !trace.viewport_counts.contains(&args.viewport_count)
            || !trace.dpi_percent.contains(&args.scale_percent)
        {
            return Err("requested viewport/DPI case is not scheduled by the trace".to_owned());
        }
        let (lost_tx, lost_rx) = mpsc::channel();
        Ok(Self {
            args,
            window: None,
            egui_context: egui::Context::default(),
            egui_winit: None,
            proxy,
            gpu: None,
            lost_tx,
            lost_rx,
            ui: UiModel::default(),
            recorder: Recorder::default(),
            trace,
            frame: 0,
            loss_requested_at: None,
            callback_observed: false,
            picks: Vec::new(),
        })
    }

    fn render(&mut self, event_loop: &ActiveEventLoop) -> Result<(), String> {
        let started = Instant::now();
        let window = self.window.as_ref().ok_or("window unavailable")?.clone();
        let egui_winit = self.egui_winit.as_mut().ok_or("egui input unavailable")?;
        let gpu = self.gpu.as_mut().ok_or("gpu unavailable")?;
        let input = egui_winit.take_egui_input(&window);
        let texture_ids = [gpu.viewports[0].texture_id, gpu.viewports[1].texture_id];
        let mut ui_frame = UiFrame::default();
        let output = self.egui_context.run(input, |context| {
            ui_frame = self
                .ui
                .show(context, texture_ids, usize::from(self.args.viewport_count));
        });
        egui_winit.handle_platform_output(&window, output.platform_output);
        let pixels_per_point = self.egui_context.pixels_per_point();
        let paint_jobs = self
            .egui_context
            .tessellate(output.shapes, pixels_per_point);

        for (viewport, target) in gpu
            .viewports
            .iter_mut()
            .take(usize::from(self.args.viewport_count))
            .enumerate()
        {
            let size = physical_extent(
                [
                    ui_frame.viewport_points[viewport].x,
                    ui_frame.viewport_points[viewport].y,
                ],
                pixels_per_point,
            );
            target.resize(&gpu.device, &gpu.queue, &mut gpu.renderer, size);
            target.render(
                &gpu.device,
                &gpu.queue,
                self.frame,
                viewport,
                self.ui.snapshot.reduced_motion,
                self.ui.snapshot.camera_yaw[viewport],
            );
        }

        let mut pick_requests = ui_frame.pick_requests;
        if self.frame == 3 {
            for (viewport, target) in gpu
                .viewports
                .iter()
                .take(usize::from(self.args.viewport_count))
                .enumerate()
            {
                pick_requests.push((viewport, target.width / 4, target.height / 2));
                pick_requests.push((viewport, target.width * 3 / 4, target.height / 2));
            }
        }
        let mut pick_duration = None;
        for (viewport, x, y) in pick_requests {
            let pick_started = Instant::now();
            let target = &gpu.viewports[viewport];
            match target.picking.read(&gpu.device, &gpu.queue, x, y) {
                Ok(actual) => {
                    let expected = expected_pick_id(viewport, x, target.width);
                    let duration = pick_started.elapsed();
                    pick_duration = Some(duration);
                    self.ui.last_pick = Some((viewport, actual, expected, duration));
                    self.picks.push(PickEvidence {
                        frame: self.frame,
                        viewport,
                        x,
                        y,
                        actual,
                        expected,
                        duration_ms: duration.as_secs_f64() * 1_000.0,
                    });
                    self.ui.status = if actual == expected {
                        "GPU ID readback matched the independent expected ID".to_owned()
                    } else {
                        "GPU ID readback mismatch".to_owned()
                    };
                }
                Err(error) => self.ui.status = format!("pick readback failed: {error}"),
            }
        }

        let frame = match gpu.surface.get_current_texture() {
            Ok(frame) => frame,
            Err(wgpu::SurfaceError::Lost | wgpu::SurfaceError::Outdated) => {
                gpu.surface.configure(&gpu.device, &gpu.config);
                return Ok(());
            }
            Err(wgpu::SurfaceError::Timeout) => return Ok(()),
            Err(wgpu::SurfaceError::OutOfMemory) => return Err("surface out of memory".to_owned()),
        };
        let view = frame.texture.create_view(&Default::default());
        let descriptor = ScreenDescriptor {
            size_in_pixels: [gpu.config.width, gpu.config.height],
            pixels_per_point,
        };
        let mut encoder = gpu
            .device
            .create_command_encoder(&wgpu::CommandEncoderDescriptor {
                label: Some("ui-spike-egui-encoder"),
            });
        for (id, delta) in &output.textures_delta.set {
            gpu.renderer
                .update_texture(&gpu.device, &gpu.queue, *id, delta);
        }
        let user_buffers = gpu.renderer.update_buffers(
            &gpu.device,
            &gpu.queue,
            &mut encoder,
            &paint_jobs,
            &descriptor,
        );
        {
            let pass = encoder.begin_render_pass(&wgpu::RenderPassDescriptor {
                label: Some("ui-spike-egui-pass"),
                color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                    view: &view,
                    resolve_target: None,
                    ops: wgpu::Operations {
                        load: wgpu::LoadOp::Clear(wgpu::Color {
                            r: 0.025,
                            g: 0.03,
                            b: 0.045,
                            a: 1.0,
                        }),
                        store: wgpu::StoreOp::Store,
                    },
                })],
                depth_stencil_attachment: None,
                timestamp_writes: None,
                occlusion_query_set: None,
            });
            gpu.renderer
                .render(&mut pass.forget_lifetime(), &paint_jobs, &descriptor);
        }
        gpu.queue.submit(
            user_buffers
                .into_iter()
                .chain(std::iter::once(encoder.finish())),
        );
        for id in &output.textures_delta.free {
            gpu.renderer.free_texture(id);
        }

        let limit = self
            .args
            .frames
            .unwrap_or(self.trace.warmup_frames + self.trace.measured_frames);
        let terminal_frame = self.frame + 1 >= limit;
        if terminal_frame {
            if let Some(path) = &self.args.screenshot {
                if gpu.config.usage.contains(wgpu::TextureUsages::COPY_SRC) {
                    if let Some(parent) = path.parent() {
                        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
                    }
                    rcce_render::save_texture_png(
                        &gpu.device,
                        &gpu.queue,
                        &frame.texture,
                        gpu.config.width,
                        gpu.config.height,
                        gpu.config.format,
                        &path.to_string_lossy(),
                    )?;
                }
            }
        }
        frame.present();
        self.recorder.record(
            self.frame,
            gpu.generation,
            self.args.viewport_count,
            self.frame < self.trace.warmup_frames,
            self.ui.rows_built,
            started.elapsed(),
            pick_duration,
        );
        self.frame += 1;

        let automatic_loss = self.args.auto_loss_frame == Some(self.frame);
        if self.ui.request_loss || automatic_loss {
            self.ui.request_loss = false;
            self.loss_requested_at = Some(Instant::now());
            gpu.device.make_invalid();
        }
        if self.ui.request_file {
            self.ui.request_file = false;
            self.open_native_file();
        }
        if self.loss_requested_at.is_some() {
            self.recover_gpu()?;
        }
        if terminal_frame {
            self.finish()?;
            event_loop.exit();
        } else {
            window.request_redraw();
        }
        Ok(())
    }

    fn recover_gpu(&mut self) -> Result<(), String> {
        let window = self.window.as_ref().ok_or("window unavailable")?.clone();
        let old_generation = self.gpu.as_ref().map_or(0, |gpu| gpu.generation);
        let callback = self.lost_rx.recv_timeout(Duration::from_secs(2)).ok();
        self.callback_observed = callback.is_some();
        let callback_text = callback.map_or_else(
            || "device-loss callback not observed".to_owned(),
            |(reason, message)| format!("device-loss callback observed: {reason:?}: {message}"),
        );
        self.gpu = None;
        let mut recovered = GpuState::new(window, old_generation + 1, self.lost_tx.clone())?;
        // The renderer is new, so seed it with egui's current managed font atlas.
        let font_image = self.egui_context.fonts(|fonts| fonts.image());
        let font_delta = egui::epaint::ImageDelta::full(font_image, egui::TextureOptions::LINEAR);
        recovered.renderer.update_texture(
            &recovered.device,
            &recovered.queue,
            TextureId::default(),
            &font_delta,
        );
        self.ui.status = format!(
            "{callback_text}; recovered generation {}",
            recovered.generation
        );
        self.gpu = Some(recovered);
        self.loss_requested_at = None;
        Ok(())
    }

    #[cfg(windows)]
    fn open_native_file(&mut self) {
        let result = rfd::FileDialog::new()
            .set_title("Select a copied RCCE project file")
            .pick_file();
        self.ui.status = result.map_or_else(
            || "native file selection cancelled".to_owned(),
            |path| {
                format!(
                    "native selection returned {} (not opened or mutated)",
                    path.display()
                )
            },
        );
    }

    #[cfg(not(windows))]
    fn open_native_file(&mut self) {
        self.ui.status =
            "native file selection is a Windows evidence gate; not run on this host".to_owned();
    }

    fn finish(&mut self) -> Result<(), String> {
        let run_dir = &self.args.output;
        let trace_bytes = fs::read(&self.args.trace).map_err(|error| error.to_string())?;
        let identity = RunIdentity {
            executable_source_sha256: executable_source_sha256(),
            cargo_lock_sha256: cargo_lock_sha256(),
            trace_sha256: hash_bytes(&trace_bytes),
        };
        self.recorder
            .write(run_dir, &self.trace)
            .map_err(|error| error.to_string())?;
        bind_identity_to_json(&run_dir.join("summary.json"), &identity)?;
        let gpu = self.gpu.as_ref().ok_or("gpu unavailable")?;
        let environment = EnvironmentEvidence {
            schema: 1,
            candidate: "egui/egui-winit/egui-wgpu 0.29.1",
            rust_version: "1.85.0",
            os: std::env::consts::OS,
            arch: std::env::consts::ARCH,
            adapter_name: gpu.adapter_info.name.clone(),
            adapter_backend: format!("{:?}", gpu.adapter_info.backend),
            adapter_driver: gpu.adapter_info.driver.clone(),
            surface_format: format!("{:?}", gpu.config.format),
            physical_size: [gpu.config.width, gpu.config.height],
            scale_factor: self
                .window
                .as_ref()
                .map_or(0.0, |window| window.scale_factor()),
            scale_override_percent: self.args.scale_percent,
            viewport_count: self.args.viewport_count,
            cache_state: self.args.cache_state.clone(),
            trace_path: self.args.trace.display().to_string(),
            trace_seed: self.trace.seed,
            warmup_frames: self.trace.warmup_frames,
            measured_frames: self.trace.measured_frames,
            device_generations: gpu.generation,
            device_loss_callback_observed: self.callback_observed,
            frame_count: self.frame,
            executable_source_sha256: identity.executable_source_sha256.clone(),
            cargo_lock_sha256: identity.cargo_lock_sha256.clone(),
            trace_sha256: identity.trace_sha256.clone(),
        };
        fs::create_dir_all(run_dir).map_err(|error| error.to_string())?;
        fs::write(
            run_dir.join("environment.json"),
            serde_json::to_vec_pretty(&environment).map_err(|error| error.to_string())?,
        )
        .map_err(|error| error.to_string())?;
        fs::write(
            run_dir.join("gate-snapshot.json"),
            serde_json::to_vec_pretty(&current_gate_results())
                .map_err(|error| error.to_string())?,
        )
        .map_err(|error| error.to_string())?;
        fs::write(
            run_dir.join("picks.json"),
            serde_json::to_vec_pretty(&self.picks).map_err(|error| error.to_string())?,
        )
        .map_err(|error| error.to_string())?;
        let mut bindings = serde_json::Map::new();
        for name in [
            "environment.json",
            "frames.json",
            "picks.json",
            "gate-snapshot.json",
            "summary.json",
        ] {
            bindings.insert(
                name.to_owned(),
                serde_json::Value::String(hash_file(&run_dir.join(name))?),
            );
        }
        if let Some(path) = &self.args.screenshot {
            if path.exists() {
                let screenshot_hash = hash_file(path)?;
                bindings.insert(
                    "screenshot".to_owned(),
                    serde_json::Value::String(screenshot_hash.clone()),
                );
                let sidecar = serde_json::json!({
                    "schema": 1,
                    "screenshot_sha256": screenshot_hash,
                    "executable_source_sha256": identity.executable_source_sha256,
                    "cargo_lock_sha256": identity.cargo_lock_sha256,
                    "trace_sha256": identity.trace_sha256,
                });
                fs::write(
                    path.with_extension("png.provenance.json"),
                    serde_json::to_vec_pretty(&sidecar).map_err(|error| error.to_string())?,
                )
                .map_err(|error| error.to_string())?;
            }
        }
        let provenance = serde_json::json!({
            "schema": 1,
            "algorithm": "sha256",
            "executable_source_sha256": identity.executable_source_sha256,
            "cargo_lock_sha256": identity.cargo_lock_sha256,
            "trace_sha256": identity.trace_sha256,
            "bindings": bindings,
        });
        fs::write(
            run_dir.join("provenance.json"),
            serde_json::to_vec_pretty(&provenance).map_err(|error| error.to_string())?,
        )
        .map_err(|error| error.to_string())?;
        let selected = CandidateDecision::new(
            current_gate_results()
                .into_iter()
                .map(|result| result.status)
                .collect(),
        )
        .selected();
        println!(
            "[ui-spike] frames={} generation={} rows-built-last={} selected={} evidence={}",
            self.frame,
            gpu.generation,
            self.ui.rows_built,
            selected,
            run_dir.display()
        );
        Ok(())
    }
}

fn hash_file(path: &Path) -> Result<String, String> {
    fs::read(path)
        .map(|bytes| hash_bytes(&bytes))
        .map_err(|error| error.to_string())
}

fn bind_identity_to_json(path: &Path, identity: &RunIdentity) -> Result<(), String> {
    let bytes = fs::read(path).map_err(|error| error.to_string())?;
    let mut value: serde_json::Value =
        serde_json::from_slice(&bytes).map_err(|error| error.to_string())?;
    let object = value
        .as_object_mut()
        .ok_or_else(|| format!("{} is not a JSON object", path.display()))?;
    object.insert(
        "executable_source_sha256".to_owned(),
        serde_json::Value::String(identity.executable_source_sha256.clone()),
    );
    object.insert(
        "cargo_lock_sha256".to_owned(),
        serde_json::Value::String(identity.cargo_lock_sha256.clone()),
    );
    object.insert(
        "trace_sha256".to_owned(),
        serde_json::Value::String(identity.trace_sha256.clone()),
    );
    fs::write(
        path,
        serde_json::to_vec_pretty(&value).map_err(|error| error.to_string())?,
    )
    .map_err(|error| error.to_string())
}

#[derive(Serialize)]
struct EnvironmentEvidence {
    schema: u32,
    candidate: &'static str,
    rust_version: &'static str,
    os: &'static str,
    arch: &'static str,
    adapter_name: String,
    adapter_backend: String,
    adapter_driver: String,
    surface_format: String,
    physical_size: [u32; 2],
    scale_factor: f64,
    scale_override_percent: u16,
    viewport_count: u8,
    cache_state: String,
    trace_path: String,
    trace_seed: u64,
    warmup_frames: u64,
    measured_frames: u64,
    device_generations: u64,
    device_loss_callback_observed: bool,
    frame_count: u64,
    executable_source_sha256: String,
    cargo_lock_sha256: String,
    trace_sha256: String,
}

#[derive(Clone, Serialize)]
struct RunIdentity {
    executable_source_sha256: String,
    cargo_lock_sha256: String,
    trace_sha256: String,
}

#[derive(Serialize)]
struct PickEvidence {
    frame: u64,
    viewport: usize,
    x: u32,
    y: u32,
    actual: u32,
    expected: u32,
    duration_ms: f64,
}

impl ApplicationHandler<UserEvent> for SpikeApp {
    fn resumed(&mut self, event_loop: &ActiveEventLoop) {
        if self.window.is_some() {
            return;
        }
        let attributes = WindowAttributes::default()
            .with_title("RCCE UI/render decision spike — candidate unselected")
            .with_inner_size(LogicalSize::new(1024.0, 768.0))
            .with_min_inner_size(LogicalSize::new(640.0, 480.0));
        let window = match event_loop.create_window(attributes) {
            Ok(window) => Arc::new(window),
            Err(error) => {
                eprintln!("[ui-spike] window creation failed: {error}");
                event_loop.exit();
                return;
            }
        };
        let gpu = match GpuState::new(window.clone(), 1, self.lost_tx.clone()) {
            Ok(gpu) => gpu,
            Err(error) => {
                eprintln!("[ui-spike] GPU creation failed: {error}");
                event_loop.exit();
                return;
            }
        };
        let effective_pixels_per_point =
            window.scale_factor() as f32 * f32::from(self.args.scale_percent) / 100.0;
        self.egui_context
            .set_pixels_per_point(effective_pixels_per_point);
        let mut egui_winit = egui_winit::State::new(
            self.egui_context.clone(),
            egui::ViewportId::ROOT,
            event_loop,
            Some(effective_pixels_per_point),
            window.theme(),
            Some(gpu.device.limits().max_texture_dimension_2d as usize),
        );
        egui_winit.init_accesskit(&window, self.proxy.clone());
        let repaint_proxy = self.proxy.clone();
        self.egui_context.set_request_repaint_callback(move |_| {
            let _ = repaint_proxy.send_event(UserEvent::Repaint);
        });
        self.egui_context.style_mut(|style| {
            style.visuals = egui::Visuals::dark();
            style.visuals.selection.stroke =
                egui::Stroke::new(2.0, Color32::from_rgb(90, 190, 255));
            style.animation_time = 0.0;
        });
        println!(
            "[ui-spike] one device/queue generation=1 adapter='{}' backend={:?} window={}x{} native-scale={} override={}%, viewports={}",
            gpu.adapter_info.name,
            gpu.adapter_info.backend,
            gpu.config.width,
            gpu.config.height,
            window.scale_factor(),
            self.args.scale_percent,
            self.args.viewport_count
        );
        self.window = Some(window.clone());
        self.gpu = Some(gpu);
        self.egui_winit = Some(egui_winit);
        window.request_redraw();
    }

    fn window_event(
        &mut self,
        event_loop: &ActiveEventLoop,
        window_id: WindowId,
        event: WindowEvent,
    ) {
        let Some(window) = self.window.as_ref() else {
            return;
        };
        if window.id() != window_id {
            return;
        }
        if let Some(egui_winit) = self.egui_winit.as_mut() {
            let response = egui_winit.on_window_event(window, &event);
            if response.repaint {
                window.request_redraw();
            }
        }
        match event {
            WindowEvent::CloseRequested => {
                if let Err(error) = self.finish() {
                    eprintln!("[ui-spike] evidence write failed: {error}");
                }
                event_loop.exit();
            }
            WindowEvent::Resized(size) => {
                if let Some(gpu) = self.gpu.as_mut() {
                    gpu.resize_surface(size.width, size.height);
                }
                window.request_redraw();
            }
            WindowEvent::ScaleFactorChanged { .. } => window.request_redraw(),
            WindowEvent::RedrawRequested => {
                if let Err(error) = self.render(event_loop) {
                    eprintln!("[ui-spike] render failed: {error}");
                    event_loop.exit();
                }
            }
            _ => {}
        }
    }

    fn user_event(&mut self, _event_loop: &ActiveEventLoop, event: UserEvent) {
        if let UserEvent::Accessibility(event) = event {
            if let Some(egui_winit) = self.egui_winit.as_mut() {
                match event.window_event {
                    egui_winit::accesskit_winit::WindowEvent::InitialTreeRequested => {
                        egui_winit.egui_ctx().enable_accesskit();
                    }
                    egui_winit::accesskit_winit::WindowEvent::ActionRequested(request) => {
                        egui_winit.on_accesskit_action_request(request);
                    }
                    egui_winit::accesskit_winit::WindowEvent::AccessibilityDeactivated => {
                        egui_winit.egui_ctx().disable_accesskit();
                    }
                }
            }
        }
        if let Some(window) = &self.window {
            window.request_redraw();
        }
    }
}

fn main() -> Result<(), String> {
    env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("warn")).init();
    let args = Args::parse();
    let event_loop: EventLoop<UserEvent> = EventLoop::with_user_event()
        .build()
        .map_err(|error| error.to_string())?;
    event_loop.set_control_flow(ControlFlow::Wait);
    let proxy = event_loop.create_proxy();
    let mut app = SpikeApp::new(args, proxy)?;
    event_loop
        .run_app(&mut app)
        .map_err(|error| error.to_string())
}

#[allow(dead_code)]
fn _assert_paths_are_spike_local(path: &Path) -> bool {
    path.starts_with("editor-rs/spikes/ui-render")
        || path.starts_with("docs/compat/ui-render-spike-evidence")
}
