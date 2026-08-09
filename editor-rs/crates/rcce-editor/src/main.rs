use eframe::egui::{
    self, Align, Color32, FontFamily, FontId, Frame, Layout, Margin, RichText, ScrollArea, Sense,
    Stroke, TextEdit, Vec2,
};
use rcce_editor_core::{
    load_feedback_project, FeedbackActor, FeedbackActorCount, FeedbackEntry, FeedbackEvidence,
    FeedbackLoadProgress, FeedbackMediaStatus, FeedbackProject, FeedbackScript,
    FeedbackScriptFamily, FeedbackZone, FeedbackZoneStatus, Lens,
};
use std::{
    path::{Path, PathBuf},
    process::ExitCode,
    sync::mpsc::{self, Receiver},
    thread,
    time::Duration,
};

#[cfg(test)]
#[path = "../tests/support/reload.rs"]
mod reload_test_support;

const INK: Color32 = Color32::from_rgb(221, 218, 205);
const MUTED: Color32 = Color32::from_rgb(143, 147, 143);
const BRASS: Color32 = Color32::from_rgb(194, 151, 70);
const BRASS_SOFT: Color32 = Color32::from_rgb(112, 88, 44);
const CANVAS: Color32 = Color32::from_rgb(17, 20, 21);
const PANEL: Color32 = Color32::from_rgb(23, 27, 28);
const PANEL_RAISED: Color32 = Color32::from_rgb(29, 34, 34);
const GREEN: Color32 = Color32::from_rgb(111, 184, 139);
const ISSUE: Color32 = Color32::from_rgb(206, 125, 96);

fn main() -> ExitCode {
    let data_root = parse_project_arg().unwrap_or_else(default_data_root);
    if std::env::args().any(|arg| arg == "--smoke-exit") {
        return match load_feedback_project(data_root, |_| {}) {
            Ok(project) => {
                let actor_catalog = project.actor_catalog();
                println!(
                    "[super-editor-mvp] ready: {} files, {} bytes, {} unavailable; actors={} actor_evidence={} actor_reference_issues={} actor_base_meshes={} zones={} zone_pairing_issues={} scripts={} script_adjuncts={} script_inventory_issues={}",
                    project.total_files(),
                    project.total_bytes(),
                    project.unavailable,
                    actor_count_smoke(actor_catalog.count),
                    evidence_label(actor_catalog.evidence).to_ascii_lowercase(),
                    actor_catalog.diagnostics.len(),
                    project.asset_catalog().meshes.len(),
                    project.zone_catalog().zones.len(),
                    project.zone_catalog().diagnostics.len(),
                    project.script_catalog().scripts.len(),
                    project.script_catalog().adjunct_files,
                    project.script_catalog().diagnostics.len()
                );
                ExitCode::SUCCESS
            }
            Err(error) => {
                eprintln!("[super-editor-mvp] failed: {error}");
                ExitCode::from(2)
            }
        };
    }

    match run_ui(data_root) {
        Ok(()) => ExitCode::SUCCESS,
        Err(error) => {
            eprintln!("[super-editor-mvp] UI failed: {error}");
            ExitCode::from(1)
        }
    }
}

fn run_ui(data_root: PathBuf) -> eframe::Result {
    let options = eframe::NativeOptions {
        renderer: eframe::Renderer::Wgpu,
        viewport: egui::ViewportBuilder::default()
            .with_title("RCCE Super Editor — Feedback MVP")
            .with_inner_size([1440.0, 900.0])
            .with_min_inner_size([1024.0, 720.0]),
        ..Default::default()
    };
    eframe::run_native(
        "RCCE Super Editor — Feedback MVP",
        options,
        Box::new(move |creation| {
            configure_style(&creation.egui_ctx);
            Ok(Box::new(LedgerApp::new(data_root)))
        }),
    )
}

fn parse_project_arg() -> Option<PathBuf> {
    let mut args = std::env::args_os().skip(1);
    while let Some(arg) = args.next() {
        if arg == "--project" {
            return args.next().map(PathBuf::from).map(normalize_selected_root);
        }
    }
    None
}

fn default_data_root() -> PathBuf {
    if let Some(path) = std::env::var_os("RCCE_DATA") {
        return normalize_selected_root(PathBuf::from(path));
    }
    let current = std::env::current_dir().unwrap_or_else(|_| PathBuf::from("."));
    for candidate in [
        current.join("data"),
        current.clone(),
        executable_candidate("data"),
        executable_candidate("../data"),
    ] {
        if looks_like_data_root(&candidate) {
            return candidate;
        }
    }
    current.join("data")
}

fn executable_candidate(relative: &str) -> PathBuf {
    std::env::current_exe()
        .ok()
        .and_then(|path| path.parent().map(|parent| parent.join(relative)))
        .unwrap_or_else(|| PathBuf::from(relative))
}

fn normalize_selected_root(path: PathBuf) -> PathBuf {
    let path = if path.is_absolute() {
        path
    } else {
        std::env::current_dir()
            .map(|current| current.join(&path))
            .unwrap_or(path)
    };
    if looks_like_data_root(&path) {
        path
    } else {
        let child = path.join("data");
        if looks_like_data_root(&child) {
            child
        } else {
            path
        }
    }
}

fn looks_like_data_root(path: &Path) -> bool {
    path.join("Game Data").is_dir() && path.join("Server Data").is_dir()
}

fn configure_style(context: &egui::Context) {
    let mut style = (*context.style()).clone();
    style.visuals.dark_mode = true;
    style.visuals.panel_fill = CANVAS;
    style.visuals.window_fill = PANEL;
    style.visuals.extreme_bg_color = Color32::from_rgb(12, 15, 16);
    style.visuals.faint_bg_color = PANEL_RAISED;
    style.visuals.selection.bg_fill = Color32::from_rgb(83, 65, 35);
    style.visuals.selection.stroke = Stroke::new(1.0, BRASS);
    style.visuals.widgets.inactive.fg_stroke = Stroke::new(1.0, INK);
    style.visuals.widgets.hovered.bg_fill = Color32::from_rgb(43, 45, 39);
    style.visuals.widgets.hovered.fg_stroke = Stroke::new(1.0, BRASS);
    style.visuals.widgets.active.bg_fill = Color32::from_rgb(70, 56, 33);
    style.spacing.item_spacing = Vec2::new(10.0, 9.0);
    style.spacing.button_padding = Vec2::new(12.0, 8.0);
    style.text_styles.insert(
        egui::TextStyle::Heading,
        FontId::new(24.0, FontFamily::Proportional),
    );
    style.text_styles.insert(
        egui::TextStyle::Body,
        FontId::new(14.0, FontFamily::Proportional),
    );
    context.set_style(style);
}

enum LoadMessage {
    Progress(FeedbackLoadProgress),
    Ready(Box<FeedbackProject>),
    Failed(String),
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum RecordsView {
    Actors,
    Files,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum WorldView {
    Zones,
    Files,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum AssetsView {
    Relationships,
    Files,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum RelationshipTarget {
    Actor(u16),
    Mesh(u16),
}

const fn actor_mesh_target(base_mesh: Option<u16>) -> Option<RelationshipTarget> {
    match base_mesh {
        Some(mesh_id) => Some(RelationshipTarget::Mesh(mesh_id)),
        None => None,
    }
}

fn actor_thread_targets(actor_ids: impl IntoIterator<Item = u16>) -> Vec<RelationshipTarget> {
    actor_ids
        .into_iter()
        .map(RelationshipTarget::Actor)
        .collect()
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum ScriptsView {
    Catalog,
    Files,
}

fn transition_records_view(
    current: RecordsView,
    target: RecordsView,
    selected_file: &mut Option<String>,
    selected_actor: &mut Option<u16>,
) -> RecordsView {
    if current != target {
        *selected_file = None;
        *selected_actor = None;
    }
    target
}

fn transition_world_view(
    current: WorldView,
    target: WorldView,
    selected_file: &mut Option<String>,
    selected_zone: &mut Option<String>,
) -> WorldView {
    if current != target {
        *selected_file = None;
        *selected_zone = None;
    }
    target
}

fn transition_assets_view(
    current: AssetsView,
    target: AssetsView,
    selected_file: &mut Option<String>,
    selected_mesh: &mut Option<u16>,
) -> AssetsView {
    if current != target {
        *selected_file = None;
        *selected_mesh = None;
    }
    target
}

fn navigate_relationship(
    target: RelationshipTarget,
    lens: &mut Lens,
    records_view: &mut RecordsView,
    assets_view: &mut AssetsView,
    filter: &mut String,
    selected_file: &mut Option<String>,
    selected_actor: &mut Option<u16>,
    selected_mesh: &mut Option<u16>,
    selected_zone: &mut Option<String>,
    selected_script: &mut Option<String>,
) {
    *selected_file = None;
    *selected_actor = None;
    *selected_mesh = None;
    *selected_zone = None;
    *selected_script = None;
    filter.clear();

    match target {
        RelationshipTarget::Actor(actor_id) => {
            *lens = Lens::Records;
            *records_view = RecordsView::Actors;
            *selected_actor = Some(actor_id);
        }
        RelationshipTarget::Mesh(mesh_id) => {
            *lens = Lens::Assets;
            *assets_view = AssetsView::Relationships;
            *selected_mesh = Some(mesh_id);
        }
    }
}

fn transition_scripts_view(
    current: ScriptsView,
    target: ScriptsView,
    selected_file: &mut Option<String>,
    selected_script: &mut Option<String>,
) -> ScriptsView {
    if current != target {
        *selected_file = None;
        *selected_script = None;
    }
    target
}

#[derive(Default)]
struct LoadGate {
    active: bool,
}

impl LoadGate {
    fn try_begin(&mut self) -> bool {
        if self.active {
            return false;
        }
        self.active = true;
        true
    }

    fn finish(&mut self) {
        self.active = false;
    }

    const fn is_active(&self) -> bool {
        self.active
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum LoadPurpose {
    Initial,
    #[cfg(any(windows, test))]
    Open,
    Reload,
}

impl LoadPurpose {
    const fn action(self) -> &'static str {
        match self {
            Self::Initial => "Open",
            #[cfg(any(windows, test))]
            Self::Open => "Open",
            Self::Reload => "Reload",
        }
    }

    const fn progress(self) -> &'static str {
        match self {
            Self::Initial => "Opening selected project…",
            #[cfg(any(windows, test))]
            Self::Open => "Opening selected project…",
            Self::Reload => "Reloading accepted snapshot…",
        }
    }

    const fn accepted(self) -> &'static str {
        match self {
            Self::Initial => "Snapshot accepted",
            #[cfg(any(windows, test))]
            Self::Open => "Snapshot accepted",
            Self::Reload => "Snapshot reloaded",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
struct PendingLoad {
    root: PathBuf,
    purpose: LoadPurpose,
}

fn retain_resolved_focus(
    project: &FeedbackProject,
    selected_file: &mut Option<String>,
    selected_actor: &mut Option<u16>,
    selected_mesh: &mut Option<u16>,
    selected_zone: &mut Option<String>,
    selected_script: &mut Option<String>,
) {
    if selected_file
        .as_ref()
        .is_some_and(|selected| !project.all_entries().any(|entry| entry.path == *selected))
    {
        *selected_file = None;
    }
    if selected_actor.as_ref().is_some_and(|selected| {
        !project
            .actor_catalog()
            .actors
            .iter()
            .any(|actor| actor.actor_id == *selected)
    }) {
        *selected_actor = None;
    }
    if selected_mesh.as_ref().is_some_and(|selected| {
        !project
            .asset_catalog()
            .meshes
            .iter()
            .any(|mesh| mesh.mesh_id == *selected)
    }) {
        *selected_mesh = None;
    }
    if selected_zone.as_ref().is_some_and(|selected| {
        !project
            .zone_catalog()
            .zones
            .iter()
            .any(|zone| zone.name == *selected)
    }) {
        *selected_zone = None;
    }
    if selected_script.as_ref().is_some_and(|selected| {
        !project
            .script_catalog()
            .scripts
            .iter()
            .any(|script| script.source_path == *selected)
    }) {
        *selected_script = None;
    }
}

struct LedgerApp {
    data_root: PathBuf,
    project: Option<FeedbackProject>,
    receiver: Option<Receiver<LoadMessage>>,
    pending_load: Option<PendingLoad>,
    lens: Lens,
    filter: String,
    selected: Option<String>,
    selected_actor: Option<u16>,
    selected_mesh: Option<u16>,
    selected_zone: Option<String>,
    selected_script: Option<String>,
    records_view: RecordsView,
    world_view: WorldView,
    assets_view: AssetsView,
    scripts_view: ScriptsView,
    script_family: Option<FeedbackScriptFamily>,
    status: String,
    activity: Vec<String>,
    load_gate: LoadGate,
}

impl LedgerApp {
    fn shell(data_root: PathBuf) -> Self {
        Self {
            data_root,
            project: None,
            receiver: None,
            pending_load: None,
            lens: Lens::Records,
            filter: String::new(),
            selected: None,
            selected_actor: None,
            selected_mesh: None,
            selected_zone: None,
            selected_script: None,
            records_view: RecordsView::Actors,
            world_view: WorldView::Zones,
            assets_view: AssetsView::Relationships,
            scripts_view: ScriptsView::Catalog,
            script_family: None,
            status: "Preparing project inventory…".to_owned(),
            activity: vec!["Feedback MVP started in read-only mode".to_owned()],
            load_gate: LoadGate::default(),
        }
    }

    fn new(data_root: PathBuf) -> Self {
        let mut app = Self::shell(data_root.clone());
        app.begin_load(data_root, LoadPurpose::Initial);
        app
    }

    fn begin_load(&mut self, path: PathBuf, purpose: LoadPurpose) {
        if !self.load_gate.try_begin() {
            self.activity.insert(
                0,
                "Project open ignored while an inventory is active".to_owned(),
            );
            return;
        }
        let (sender, receiver) = mpsc::channel();
        self.receiver = Some(receiver);
        self.pending_load = Some(PendingLoad {
            root: path.clone(),
            purpose,
        });
        self.status = purpose.progress().to_owned();
        self.activity.insert(
            0,
            format!("{} requested: {}", purpose.action(), path.display()),
        );
        thread::spawn(move || {
            let result = load_feedback_project(path, |progress| {
                let _ = sender.send(LoadMessage::Progress(progress));
            });
            let _ = sender.send(match result {
                Ok(project) => LoadMessage::Ready(Box::new(project)),
                Err(error) => LoadMessage::Failed(error),
            });
        });
    }

    fn poll_load(&mut self) {
        let Some(receiver) = self.receiver.take() else {
            return;
        };
        loop {
            let message = match receiver.try_recv() {
                Ok(message) => message,
                Err(mpsc::TryRecvError::Empty) => {
                    self.receiver = Some(receiver);
                    return;
                }
                Err(mpsc::TryRecvError::Disconnected) => {
                    self.finish_disconnected();
                    return;
                }
            };
            match message {
                LoadMessage::Progress(progress) => self.apply_progress(progress),
                LoadMessage::Ready(project) => {
                    self.finish_ready(project);
                    return;
                }
                LoadMessage::Failed(error) => {
                    self.finish_failed(error);
                    return;
                }
            }
        }
    }

    fn finish_ready(&mut self, project: Box<FeedbackProject>) {
        let purpose = self
            .pending_load
            .take()
            .map_or(LoadPurpose::Initial, |pending| {
                self.data_root = pending.root;
                pending.purpose
            });
        let actor_count = actor_count_label(project.actor_catalog().count);
        let actor_base_mesh_count = project.asset_catalog().meshes.len();
        let zone_count = project.zone_catalog().zones.len();
        let script_count = project.script_catalog().scripts.len();
        self.status = format!(
            "{} files indexed · {actor_count} · {} unavailable",
            project.total_files(),
            project.unavailable
        );
        self.activity.insert(
            0,
            format!(
                "Asset relationships: {actor_base_mesh_count} actor-referenced base mesh IDs · {} evidence",
                evidence_label(project.asset_catalog().evidence).to_ascii_lowercase()
            ),
        );
        self.activity.insert(
            0,
            format!(
                "Zone atlas: {zone_count} filename identities · {} observed pairing issues",
                project.zone_catalog().diagnostics.len()
            ),
        );
        self.activity.insert(
            0,
            format!(
                "Actor catalog: {actor_count} · {}",
                diagnostic_count_label(
                    project.actor_catalog().evidence,
                    project.actor_catalog().diagnostics.len()
                )
            ),
        );
        self.activity.insert(
            0,
            format!(
                "{}: {} across {}",
                purpose.accepted(),
                format_bytes(project.total_bytes()),
                project.shape
            ),
        );
        self.activity.insert(
            0,
            format!(
                "Script constellation: {script_count} active .rsl identities · {} observed adjuncts · {} inventory issues",
                project.script_catalog().adjunct_files,
                project.script_catalog().diagnostics.len()
            ),
        );
        retain_resolved_focus(
            &project,
            &mut self.selected,
            &mut self.selected_actor,
            &mut self.selected_mesh,
            &mut self.selected_zone,
            &mut self.selected_script,
        );
        self.project = Some(*project);
        self.receiver = None;
        self.load_gate.finish();
    }

    fn finish_failed(&mut self, error: String) {
        let purpose = self
            .pending_load
            .take()
            .map_or(LoadPurpose::Initial, |pending| pending.purpose);
        self.status = if self.project.is_some() {
            format!(
                "{} failed: {error}; prior accepted snapshot remains visible",
                purpose.action()
            )
        } else {
            format!("{} failed: {error}", purpose.action())
        };
        self.activity.insert(0, self.status.clone());
        self.receiver = None;
        self.load_gate.finish();
    }

    fn finish_disconnected(&mut self) {
        self.finish_failed("project inventory ended unexpectedly".to_owned());
    }

    fn apply_progress(&mut self, progress: FeedbackLoadProgress) {
        self.status = match progress {
            FeedbackLoadProgress::Discovering => "Discovering project state…".to_owned(),
            FeedbackLoadProgress::Inventory {
                entries,
                files,
                unavailable,
            } => {
                format!("Discovered {entries} entries · {files} files · {unavailable} unavailable")
            }
            FeedbackLoadProgress::Reading {
                path,
                completed_files,
                total_files,
            } => format!("Reading {completed_files}/{total_files} · {path}"),
            FeedbackLoadProgress::Complete {
                files,
                unavailable,
                bytes,
            } => format!(
                "Accepted {files} files · {unavailable} unavailable · {}",
                format_bytes(bytes)
            ),
        };
    }

    fn open_project(&mut self) {
        if self.load_gate.is_active() {
            return;
        }
        #[cfg(windows)]
        if let Some(path) = rfd::FileDialog::new()
            .set_title("Select an RCCE project or data folder")
            .pick_folder()
        {
            self.begin_load(normalize_selected_root(path), LoadPurpose::Open);
        }
        #[cfg(not(windows))]
        self.activity.insert(
            0,
            "Folder selection is enabled in the Windows feedback build".to_owned(),
        );
    }

    fn reload_snapshot(&mut self) {
        if self.load_gate.is_active() || self.project.is_none() {
            return;
        }
        self.begin_load(self.data_root.clone(), LoadPurpose::Reload);
    }

    fn selected_entry(&self) -> Option<&FeedbackEntry> {
        let selected = self.selected.as_deref()?;
        self.project
            .as_ref()?
            .all_entries()
            .find(|entry| entry.path == selected)
    }

    fn selected_actor(&self) -> Option<&FeedbackActor> {
        let selected = self.selected_actor?;
        self.project
            .as_ref()?
            .actor_catalog()
            .actors
            .iter()
            .find(|actor| actor.actor_id == selected)
    }

    fn selected_zone(&self) -> Option<&FeedbackZone> {
        let selected = self.selected_zone.as_deref()?;
        self.project
            .as_ref()?
            .zone_catalog()
            .zones
            .iter()
            .find(|zone| zone.name == selected)
    }

    fn selected_script(&self) -> Option<&FeedbackScript> {
        let selected = self.selected_script.as_deref()?;
        self.project
            .as_ref()?
            .script_catalog()
            .scripts
            .iter()
            .find(|script| script.source_path == selected)
    }

    fn top_bar(&mut self, context: &egui::Context) {
        egui::TopBottomPanel::top("ledger_top")
            .exact_height(103.0)
            .frame(
                Frame::none()
                    .fill(Color32::from_rgb(14, 18, 19))
                    .inner_margin(Margin::symmetric(20.0, 13.0))
                    .stroke(Stroke::new(1.0, BRASS_SOFT)),
            )
            .show(context, |ui| {
                ui.horizontal(|ui| {
                    ui.vertical(|ui| {
                        ui.label(
                            RichText::new("RCCE  /  THE LEDGER")
                                .size(11.0)
                                .color(BRASS)
                                .strong()
                                .extra_letter_spacing(1.7),
                        );
                        ui.label(RichText::new("Super Editor").size(28.0).color(INK).strong());
                    });
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        if ui
                            .add_enabled(
                                !self.load_gate.is_active(),
                                egui::Button::new(RichText::new("OPEN PROJECT").color(BRASS)),
                            )
                            .on_hover_text("Choose another existing RCCE project")
                            .clicked()
                        {
                            self.open_project();
                        }
                        if ui
                            .add_enabled(
                                self.project.is_some() && !self.load_gate.is_active(),
                                egui::Button::new(RichText::new("RELOAD SNAPSHOT").color(BRASS)),
                            )
                            .on_hover_text(
                                "Re-inventory the accepted project from disk; no files are written",
                            )
                            .clicked()
                        {
                            self.reload_snapshot();
                        }
                        ui.label(
                            RichText::new("●  READ ONLY")
                                .size(12.0)
                                .color(GREEN)
                                .strong(),
                        );
                    });
                });
                ui.horizontal(|ui| {
                    ui.label(RichText::new(self.data_root.display().to_string()).color(MUTED));
                    ui.separator();
                    ui.label(RichText::new(&self.status).color(INK));
                });
            });
    }

    fn lens_rail(&mut self, context: &egui::Context) {
        egui::SidePanel::left("lens_rail")
            .exact_width(205.0)
            .frame(
                Frame::none()
                    .fill(PANEL)
                    .inner_margin(Margin::same(14.0))
                    .stroke(Stroke::new(1.0, Color32::from_rgb(46, 50, 48))),
            )
            .show(context, |ui| {
                ui.label(
                    RichText::new("PROJECT LENSES")
                        .size(11.0)
                        .color(MUTED)
                        .strong(),
                );
                ui.add_space(5.0);
                for lens in Lens::ALL {
                    let count = self
                        .project
                        .as_ref()
                        .map_or(0, |project| project.entries(lens).len());
                    let label = format!("{}\n{:>4} observed", lens.label(), count);
                    if ui
                        .add_sized(
                            [177.0, 48.0],
                            egui::SelectableLabel::new(
                                self.lens == lens,
                                RichText::new(label).size(14.0),
                            ),
                        )
                        .clicked()
                    {
                        self.lens = lens;
                        self.selected = None;
                        self.selected_actor = None;
                        self.selected_mesh = None;
                        self.selected_zone = None;
                        self.selected_script = None;
                    }
                }
                ui.with_layout(Layout::bottom_up(Align::LEFT), |ui| {
                    ui.label(
                        RichText::new("Feedback build 0.7\nNo save or mutation commands exist")
                            .size(11.0)
                            .color(MUTED),
                    );
                });
            });
    }

    fn inspector(&mut self, context: &egui::Context) {
        let mut pending_relationship = None;
        egui::SidePanel::right("inspector")
            .exact_width(325.0)
            .frame(
                Frame::none()
                    .fill(PANEL)
                    .inner_margin(Margin::same(18.0))
                    .stroke(Stroke::new(1.0, Color32::from_rgb(46, 50, 48))),
            )
            .show(context, |ui| {
                ui.label(
                    RichText::new("INSPECTOR")
                        .size(11.0)
                        .color(BRASS)
                        .strong(),
                );
                ui.add_space(8.0);
                ScrollArea::vertical()
                    .id_salt("inspector_body")
                    .auto_shrink([false, false])
                    .show(ui, |ui| {
                if let Some(actor) = self.selected_actor() {
                    let evidence = self
                        .project
                        .as_ref()
                        .map(|project| project.actor_catalog().evidence)
                        .unwrap_or(FeedbackEvidence::Unavailable);
                    let evidence_color = match evidence {
                        FeedbackEvidence::Consensus => GREEN,
                        FeedbackEvidence::Provisional => BRASS,
                        FeedbackEvidence::Unavailable => ISSUE,
                    };
                    ui.label(RichText::new(&actor.race).size(22.0).color(INK));
                    ui.label(
                        RichText::new(format!("ACTOR  /  #{:05}", actor.actor_id))
                            .size(12.0)
                            .color(BRASS),
                    );
                    ui.add_space(16.0);
                    property(ui, "Stable identity", &format!("Actor #{}", actor.actor_id));
                    property(
                        ui,
                        "Legacy display",
                        if actor.race_is_lossy {
                            "Lossy byte projection"
                        } else {
                            "Exact UTF-8 projection"
                        },
                    );
                    property(
                        ui,
                        "Base mesh",
                        &actor
                            .base_mesh
                            .map_or_else(|| "None".to_owned(), |id| format!("Mesh #{id}")),
                    );
                    property(ui, "Media state", media_status_label(actor.media_status));
                    property(
                        ui,
                        "Physical source",
                        actor.physical_path.as_deref().unwrap_or("Not resolved"),
                    );
                    ui.add_space(12.0);
                    let evidence_fill = match evidence {
                        FeedbackEvidence::Consensus => Color32::from_rgb(19, 31, 27),
                        FeedbackEvidence::Provisional => Color32::from_rgb(37, 31, 20),
                        FeedbackEvidence::Unavailable => Color32::from_rgb(39, 25, 23),
                    };
                    let evidence_stroke = match evidence {
                        FeedbackEvidence::Consensus => Color32::from_rgb(54, 91, 71),
                        FeedbackEvidence::Provisional => BRASS_SOFT,
                        FeedbackEvidence::Unavailable => Color32::from_rgb(104, 62, 48),
                    };
                    Frame::none()
                        .fill(evidence_fill)
                        .stroke(Stroke::new(1.0, evidence_stroke))
                        .inner_margin(Margin::same(12.0))
                        .show(ui, |ui| {
                            ui.label(
                                RichText::new(format!(
                                    "{} · OBSERVATION ONLY",
                                    evidence_label(evidence)
                                ))
                                .color(evidence_color)
                                .strong(),
                            );
                            ui.label(
                                RichText::new(if evidence == FeedbackEvidence::Consensus {
                                    "Client and server parser evidence agrees; no edit path exists."
                                } else {
                                    "The consensus layer marked this slice provisional; missing-media diagnostics are withheld."
                                })
                                .size(12.0)
                                .color(MUTED),
                            );
                        });
                    ui.add_space(12.0);
                    if let Some(RelationshipTarget::Mesh(mesh_id)) =
                        actor_mesh_target(actor.base_mesh)
                    {
                        if ui
                            .add_sized(
                                [ui.available_width(), 34.0],
                                egui::Button::new(
                                    RichText::new(format!(
                                        "OPEN MESH #{mesh_id} RELATIONSHIP"
                                    ))
                                    .color(BRASS)
                                    .strong(),
                                ),
                            )
                            .on_hover_text(
                                "Open this observed raw actor/base-mesh relationship in Assets",
                            )
                            .clicked()
                        {
                            pending_relationship = Some(RelationshipTarget::Mesh(mesh_id));
                        }
                    } else {
                        ui.label(
                            RichText::new("No actor-referenced base mesh to open")
                                .size(12.0)
                                .color(MUTED),
                        );
                    }
                } else if let Some(mesh) = self.selected_mesh.and_then(|selected| {
                    self.project
                        .as_ref()?
                        .asset_catalog()
                        .meshes
                        .iter()
                        .find(|mesh| mesh.mesh_id == selected)
                }) {
                    let evidence = self
                        .project
                        .as_ref()
                        .map(|project| project.asset_catalog().evidence)
                        .unwrap_or(FeedbackEvidence::Unavailable);
                    let actor_links = mesh
                        .actors
                        .iter()
                        .map(|actor| {
                            format!(
                                "{} (#{}{})",
                                actor.race,
                                actor.actor_id,
                                if actor.race_is_lossy {
                                    ", lossy display"
                                } else {
                                    ""
                                }
                            )
                        })
                        .collect::<Vec<_>>()
                        .join(" · ");
                    ui.label(
                        RichText::new(format!("Mesh #{}", mesh.mesh_id))
                            .size(22.0)
                            .color(INK),
                    );
                    ui.label(
                        RichText::new("BASE MESH  /  ACTOR RELATIONSHIP")
                            .size(12.0)
                            .color(BRASS),
                    );
                    ui.add_space(16.0);
                    property(ui, "Raw identity", &format!("Mesh #{}", mesh.mesh_id));
                    property(ui, "Actor backlinks", &actor_links);
                    property(ui, "Media state", media_status_label(mesh.media_status));
                    property(
                        ui,
                        "Physical source",
                        mesh.physical_path.as_deref().unwrap_or("Not asserted"),
                    );
                    ui.add_space(12.0);
                    Frame::none()
                        .fill(if evidence == FeedbackEvidence::Consensus {
                            Color32::from_rgb(19, 31, 27)
                        } else {
                            Color32::from_rgb(37, 31, 20)
                        })
                        .stroke(Stroke::new(
                            1.0,
                            if evidence == FeedbackEvidence::Consensus {
                                Color32::from_rgb(54, 91, 71)
                            } else {
                                BRASS_SOFT
                            },
                        ))
                        .inner_margin(Margin::same(12.0))
                        .show(ui, |ui| {
                            ui.label(
                                RichText::new(format!(
                                    "{} · ACTOR-BASE SLICE ONLY",
                                    evidence_label(evidence)
                                ))
                                .color(if evidence == FeedbackEvidence::Consensus {
                                    GREEN
                                } else {
                                    BRASS
                                })
                                .strong(),
                            );
                            ui.label(
                                RichText::new(if evidence == FeedbackEvidence::Consensus {
                                    "Backlinks and media observations come from accepted actor/media consensus; this is not a complete asset catalog."
                                } else {
                                    "Raw mesh IDs and actor backlinks remain visible, but catalog and physical-file conclusions are withheld."
                                })
                                .size(12.0)
                                .color(MUTED),
                            );
                        });
                    ui.add_space(12.0);
                    ui.label(
                        RichText::new("ACTOR THREADS")
                            .size(10.0)
                            .color(MUTED)
                            .strong(),
                    );
                    let thread_targets =
                        actor_thread_targets(mesh.actors.iter().map(|actor| actor.actor_id));
                    for (actor, target) in mesh.actors.iter().zip(thread_targets) {
                        if ui
                            .add_sized(
                                [ui.available_width(), 32.0],
                                egui::Button::new(
                                    RichText::new(format!(
                                        "OPEN ACTOR #{:05}  ·  {}",
                                        actor.actor_id, actor.race
                                    ))
                                    .color(BRASS),
                                ),
                            )
                            .on_hover_text("Open this observed raw backlink in Records")
                            .clicked()
                        {
                            pending_relationship = Some(target);
                        }
                    }
                } else if let Some(zone) = self.selected_zone() {
                    ui.label(RichText::new(&zone.name).size(22.0).color(INK));
                    ui.label(RichText::new("ZONE  /  FILENAME IDENTITY").size(12.0).color(BRASS));
                    ui.add_space(16.0);
                    property(ui, "Stable identity", &format!("{}.dat", zone.name));
                    property(ui, "Pairing state", zone_status_label(zone.status));
                    let visual = zone.visual_path.as_ref().map_or_else(
                        || "Not observed".to_owned(),
                        |path| {
                            format!(
                                "{} · {}",
                                path,
                                format_bytes(zone.visual_size.unwrap_or_default())
                            )
                        },
                    );
                    let gameplay = zone.gameplay_path.as_ref().map_or_else(
                        || "Not observed".to_owned(),
                        |path| {
                            format!(
                                "{} · {}",
                                path,
                                format_bytes(zone.gameplay_size.unwrap_or_default())
                            )
                        },
                    );
                    property(ui, "Visual half", &visual);
                    property(ui, "Gameplay half", &gameplay);
                    ui.add_space(12.0);
                    Frame::none()
                        .fill(Color32::from_rgb(19, 31, 27))
                        .stroke(Stroke::new(1.0, Color32::from_rgb(54, 91, 71)))
                        .inner_margin(Margin::same(12.0))
                        .show(ui, |ui| {
                            ui.label(
                                RichText::new("INVENTORY OBSERVATION · READ ONLY")
                                    .color(GREEN)
                                    .strong(),
                            );
                            ui.label(
                                RichText::new(
                                    "Pairing reflects observed filenames only; zone contents are not parsed or editable in this feedback surface.",
                                )
                                .size(12.0)
                                .color(MUTED),
                            );
                        });
                } else if let Some(script) = self.selected_script() {
                    ui.label(RichText::new(&script.name).size(22.0).color(INK));
                    ui.label(RichText::new("SCRIPT  /  ACTIVE .RSL IDENTITY").size(12.0).color(BRASS));
                    ui.add_space(16.0);
                    property(ui, "Stable identity", &script.name);
                    property(
                        ui,
                        "Filename grouping",
                        script_family_observation(script.family),
                    );
                    property(
                        ui,
                        "Active source",
                        &format!(
                            "{} · {}",
                            script.source_path,
                            format_bytes(script.source_size)
                        ),
                    );
                    property(
                        ui,
                        "Legacy module adjunct",
                        &script_artifact_label(
                            script.module_path.as_deref(),
                            script.module_size,
                        ),
                    );
                    property(
                        ui,
                        "Alternate artifact",
                        &script_artifact_label(
                            script.alternate_path.as_deref(),
                            script.alternate_size,
                        ),
                    );
                    ui.add_space(12.0);
                    Frame::none()
                        .fill(Color32::from_rgb(19, 31, 27))
                        .stroke(Stroke::new(1.0, Color32::from_rgb(54, 91, 71)))
                        .inner_margin(Margin::same(12.0))
                        .show(ui, |ui| {
                            ui.label(
                                RichText::new("INVENTORY RELATION · READ ONLY")
                                    .color(GREEN)
                                    .strong(),
                            );
                            ui.label(
                                RichText::new(
                                    "The .rsl source anchors this identity. Same-stem adjuncts are observed only; contents and generation provenance are not inferred.",
                                )
                                .size(12.0)
                                .color(MUTED),
                            );
                        });
                } else if let Some(entry) = self.selected_entry() {
                    ui.label(RichText::new(file_name(&entry.path)).size(22.0).color(INK));
                    ui.label(RichText::new(&entry.path).size(12.0).color(MUTED));
                    ui.add_space(16.0);
                    property(ui, "Observed size", &format_bytes(entry.size));
                    property(ui, "Format family", entry.family.unwrap_or("unclassified"));
                    property(ui, "Compatibility", entry.compatibility);
                    property(
                        ui,
                        "State classes",
                        if entry.classes.is_empty() {
                            "unclassified".to_owned()
                        } else {
                            entry.classes.join(" · ")
                        }
                        .as_str(),
                    );
                    ui.add_space(12.0);
                    ui.label(RichText::new("SOURCE FINGERPRINT").size(10.0).color(MUTED));
                    ui.label(
                        RichText::new(&entry.source_sha256)
                            .size(11.0)
                            .color(Color32::from_rgb(175, 178, 168))
                            .monospace(),
                    );
                    ui.add_space(16.0);
                    Frame::none()
                        .fill(Color32::from_rgb(19, 31, 27))
                        .stroke(Stroke::new(1.0, Color32::from_rgb(54, 91, 71)))
                        .inner_margin(Margin::same(12.0))
                        .show(ui, |ui| {
                            ui.label(RichText::new("OBSERVATION ONLY").color(GREEN).strong());
                            ui.label(
                                RichText::new(
                                    "This surface has no write, repair, rename, or conversion path.",
                                )
                                .size(12.0)
                                .color(MUTED),
                            );
                        });
                } else {
                    ui.label(
                        RichText::new("Select an actor, asset relationship, zone, script, or file")
                            .size(18.0)
                            .color(INK),
                    );
                    ui.label(
                        RichText::new(
                            "Actors expose media health, Assets exposes actor↔base-mesh backlinks, zones expose paired-file presence, scripts expose source/adjunct relationships, and files expose classification plus exact fingerprints.",
                        )
                        .color(MUTED),
                    );
                }
                    });
            });
        if let Some(target) = pending_relationship {
            let activity = match target {
                RelationshipTarget::Actor(actor_id) => {
                    format!("Followed actor thread to Actor #{actor_id}")
                }
                RelationshipTarget::Mesh(mesh_id) => {
                    format!("Followed base-mesh thread to Mesh #{mesh_id}")
                }
            };
            navigate_relationship(
                target,
                &mut self.lens,
                &mut self.records_view,
                &mut self.assets_view,
                &mut self.filter,
                &mut self.selected,
                &mut self.selected_actor,
                &mut self.selected_mesh,
                &mut self.selected_zone,
                &mut self.selected_script,
            );
            self.activity.insert(0, activity);
        }
    }

    fn ledger(&mut self, context: &egui::Context) {
        egui::TopBottomPanel::bottom("activity_ledger")
            .exact_height(132.0)
            .frame(
                Frame::none()
                    .fill(Color32::from_rgb(15, 18, 18))
                    .inner_margin(Margin::symmetric(20.0, 12.0))
                    .stroke(Stroke::new(1.0, BRASS_SOFT)),
            )
            .show(context, |ui| {
                ui.horizontal(|ui| {
                    ui.label(RichText::new("THE LEDGER").size(11.0).color(BRASS).strong());
                    ui.label(RichText::new("0 pending · 0 committed · 0 external").color(MUTED));
                });
                ScrollArea::vertical().max_height(84.0).show(ui, |ui| {
                    for event in self.activity.iter().take(8) {
                        ui.horizontal(|ui| {
                            ui.label(RichText::new("—").color(BRASS_SOFT));
                            ui.label(RichText::new(event).size(12.0).color(INK));
                        });
                    }
                });
            });
    }

    fn atlas(&mut self, context: &egui::Context) {
        let actor_view = self.lens == Lens::Records && self.records_view == RecordsView::Actors;
        let zone_view = self.lens == Lens::World && self.world_view == WorldView::Zones;
        let asset_view = self.lens == Lens::Assets && self.assets_view == AssetsView::Relationships;
        let script_view = self.lens == Lens::Scripts && self.scripts_view == ScriptsView::Catalog;
        egui::CentralPanel::default()
            .frame(Frame::none().fill(CANVAS).inner_margin(Margin::same(20.0)))
            .show(context, |ui| {
                ui.horizontal(|ui| {
                    ui.vertical(|ui| {
                        let title = if actor_view {
                            "Actor catalog".to_owned()
                        } else if zone_view {
                            "Paired zone atlas".to_owned()
                        } else if asset_view {
                            "Actor base-mesh relationships".to_owned()
                        } else if script_view {
                            "Script constellation".to_owned()
                        } else {
                            format!("{} atlas", self.lens.label())
                        };
                        ui.label(
                            RichText::new(title)
                                .size(25.0)
                                .color(INK)
                                .strong(),
                        );
                        ui.label(
                            RichText::new(if actor_view {
                                "Client/server consensus · stable actor identities · live media health"
                            } else if zone_view {
                                "Filename identities · visual/gameplay pairing · directly observed gaps"
                            } else if asset_view {
                                "Raw mesh IDs · actor backlinks · evidence-qualified media observations"
                            } else if script_view {
                                "Active .rsl anchors · literal-prefix families · observed same-stem adjuncts"
                            } else {
                                "One project snapshot · stable observed identities"
                            })
                                .color(MUTED),
                        );
                    });
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        ui.add_sized(
                            [260.0, 34.0],
                            TextEdit::singleline(&mut self.filter)
                                .hint_text(if actor_view {
                                    "Filter actors, ids, or states…"
                                } else if zone_view {
                                    "Filter zones or pairing states…"
                                } else if asset_view {
                                    "Filter mesh IDs, actors, or states…"
                                } else if script_view {
                                    "Filter scripts, families, or paths…"
                                } else {
                                    "Filter observed paths…"
                                }),
                        );
                        if self.lens == Lens::Records {
                            if ui
                                .selectable_label(self.records_view == RecordsView::Files, "FILES")
                                .clicked()
                            {
                                self.records_view = transition_records_view(
                                    self.records_view,
                                    RecordsView::Files,
                                    &mut self.selected,
                                    &mut self.selected_actor,
                                );
                            }
                            if ui
                                .selectable_label(
                                    self.records_view == RecordsView::Actors,
                                    "ACTORS",
                                )
                                .clicked()
                            {
                                self.records_view = transition_records_view(
                                    self.records_view,
                                    RecordsView::Actors,
                                    &mut self.selected,
                                    &mut self.selected_actor,
                                );
                            }
                        }
                        if self.lens == Lens::World {
                            if ui
                                .selectable_label(self.world_view == WorldView::Files, "FILES")
                                .clicked()
                            {
                                self.world_view = transition_world_view(
                                    self.world_view,
                                    WorldView::Files,
                                    &mut self.selected,
                                    &mut self.selected_zone,
                                );
                            }
                            if ui
                                .selectable_label(self.world_view == WorldView::Zones, "ZONES")
                                .clicked()
                            {
                                self.world_view = transition_world_view(
                                    self.world_view,
                                    WorldView::Zones,
                                    &mut self.selected,
                                    &mut self.selected_zone,
                                );
                            }
                        }
                        if self.lens == Lens::Assets {
                            if ui
                                .selectable_label(self.assets_view == AssetsView::Files, "FILES")
                                .clicked()
                            {
                                self.assets_view = transition_assets_view(
                                    self.assets_view,
                                    AssetsView::Files,
                                    &mut self.selected,
                                    &mut self.selected_mesh,
                                );
                            }
                            if ui
                                .selectable_label(
                                    self.assets_view == AssetsView::Relationships,
                                    "RELATIONSHIPS",
                                )
                                .clicked()
                            {
                                self.assets_view = transition_assets_view(
                                    self.assets_view,
                                    AssetsView::Relationships,
                                    &mut self.selected,
                                    &mut self.selected_mesh,
                                );
                            }
                        }
                        if self.lens == Lens::Scripts {
                            if ui
                                .selectable_label(self.scripts_view == ScriptsView::Files, "FILES")
                                .clicked()
                            {
                                self.scripts_view = transition_scripts_view(
                                    self.scripts_view,
                                    ScriptsView::Files,
                                    &mut self.selected,
                                    &mut self.selected_script,
                                );
                            }
                            if ui
                                .selectable_label(
                                    self.scripts_view == ScriptsView::Catalog,
                                    "SCRIPTS",
                                )
                                .clicked()
                            {
                                self.scripts_view = transition_scripts_view(
                                    self.scripts_view,
                                    ScriptsView::Catalog,
                                    &mut self.selected,
                                    &mut self.selected_script,
                                );
                            }
                        }
                    });
                });
                ui.add_space(14.0);
                if actor_view {
                    self.actor_catalog(ui);
                } else if zone_view {
                    self.zone_catalog(ui);
                } else if asset_view {
                    self.asset_relationships(ui);
                } else if script_view {
                    self.script_catalog(ui);
                } else {
                    self.file_atlas(ui);
                }
                if let Some(project) = &self.project {
                    ui.add_space(8.0);
                    ui.label(
                        RichText::new(format!(
                            "{} total files · {} · {} unclassified formats shown in Records · {} unavailable",
                            project.total_files(),
                            format_bytes(project.total_bytes()),
                            project.unclassified_files(),
                            project.unavailable
                        ))
                        .size(11.0)
                        .color(MUTED),
                    );
                }
            });
    }

    fn actor_catalog(&mut self, ui: &mut egui::Ui) {
        let Some(project) = self.project.as_ref() else {
            ui.vertical_centered(|ui| {
                ui.spinner();
                ui.label(RichText::new(&self.status).color(MUTED));
            });
            return;
        };
        let catalog = project.actor_catalog();
        let evidence_color = match catalog.evidence {
            FeedbackEvidence::Consensus => GREEN,
            FeedbackEvidence::Provisional => BRASS,
            FeedbackEvidence::Unavailable => ISSUE,
        };
        Frame::none()
            .fill(PANEL_RAISED)
            .stroke(Stroke::new(1.0, Color32::from_rgb(48, 53, 51)))
            .inner_margin(Margin::symmetric(14.0, 11.0))
            .show(ui, |ui| {
                ui.horizontal(|ui| {
                    ui.label(
                        RichText::new(evidence_label(catalog.evidence))
                            .color(evidence_color)
                            .strong(),
                    );
                    ui.separator();
                    ui.label(RichText::new(actor_count_label(catalog.count)).color(INK));
                    ui.separator();
                    ui.label(
                        RichText::new(diagnostic_count_label(
                            catalog.evidence,
                            catalog.diagnostics.len(),
                        ))
                        .color(match catalog.evidence {
                            FeedbackEvidence::Consensus if catalog.diagnostics.is_empty() => GREEN,
                            FeedbackEvidence::Consensus => ISSUE,
                            FeedbackEvidence::Provisional => BRASS,
                            FeedbackEvidence::Unavailable => ISSUE,
                        }),
                    );
                });
                if let Some(reason) = &catalog.unavailable_reason {
                    ui.label(RichText::new(reason).size(12.0).color(MUTED));
                }
            });
        ui.add_space(8.0);

        let filter = self.filter.to_ascii_lowercase();
        let mut clicked_actor = None;
        ScrollArea::vertical()
            .max_height(ui.available_height())
            .show(ui, |ui| {
                for diagnostic in &catalog.diagnostics {
                    if !filter.is_empty()
                        && !diagnostic.message.to_ascii_lowercase().contains(&filter)
                        && !diagnostic.code.to_ascii_lowercase().contains(&filter)
                        && !diagnostic.actor_id.to_string().contains(&filter)
                    {
                        continue;
                    }
                    let response = Frame::none()
                        .fill(Color32::from_rgb(42, 29, 25))
                        .stroke(Stroke::new(1.0, Color32::from_rgb(104, 62, 48)))
                        .inner_margin(Margin::symmetric(13.0, 9.0))
                        .show(ui, |ui| {
                            ui.set_min_width(ui.available_width());
                            ui.label(
                                RichText::new(format!(
                                    "{}  ·  ACTOR #{}",
                                    diagnostic.code, diagnostic.actor_id
                                ))
                                .size(10.0)
                                .color(ISSUE)
                                .strong(),
                            );
                            ui.label(RichText::new(&diagnostic.message).size(12.0).color(INK));
                        })
                        .response;
                    if response.interact(Sense::click()).clicked() {
                        clicked_actor = Some(diagnostic.actor_id);
                    }
                    ui.add_space(5.0);
                }

                for actor in &catalog.actors {
                    let searchable = format!(
                        "{} {} {}",
                        actor.race,
                        actor.actor_id,
                        media_status_label(actor.media_status)
                    )
                    .to_ascii_lowercase();
                    if !filter.is_empty() && !searchable.contains(&filter) {
                        continue;
                    }
                    let selected = self.selected_actor == Some(actor.actor_id);
                    let response = Frame::none()
                        .fill(if selected {
                            Color32::from_rgb(59, 49, 31)
                        } else {
                            Color32::from_rgb(25, 30, 30)
                        })
                        .stroke(Stroke::new(
                            1.0,
                            if selected {
                                BRASS
                            } else {
                                Color32::from_rgb(48, 53, 51)
                            },
                        ))
                        .inner_margin(Margin::symmetric(13.0, 10.0))
                        .show(ui, |ui| {
                            ui.set_min_width(ui.available_width());
                            ui.horizontal(|ui| {
                                ui.vertical(|ui| {
                                    ui.label(
                                        RichText::new(&actor.race).size(16.0).color(INK).strong(),
                                    );
                                    ui.label(
                                        RichText::new(format!(
                                            "ACTOR #{:05}  ·  {}",
                                            actor.actor_id,
                                            actor.base_mesh.map_or_else(
                                                || "NO BASE MESH".to_owned(),
                                                |id| format!("BASE MESH #{id}")
                                            )
                                        ))
                                        .size(10.0)
                                        .color(MUTED),
                                    );
                                });
                                ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                                    ui.label(
                                        RichText::new(media_status_badge(actor.media_status))
                                            .size(10.0)
                                            .color(media_status_color(actor.media_status))
                                            .strong(),
                                    );
                                });
                            });
                        })
                        .response;
                    if response.interact(Sense::click()).clicked() {
                        clicked_actor = Some(actor.actor_id);
                    }
                    ui.add_space(5.0);
                }
            });
        if let Some(actor_id) = clicked_actor {
            self.selected_actor = Some(actor_id);
            self.selected = None;
            self.selected_mesh = None;
            self.selected_zone = None;
            self.selected_script = None;
        }
    }

    fn asset_relationships(&mut self, ui: &mut egui::Ui) {
        let Some(project) = self.project.as_ref() else {
            ui.vertical_centered(|ui| {
                ui.spinner();
                ui.label(RichText::new(&self.status).color(MUTED));
            });
            return;
        };
        let catalog = project.asset_catalog();
        let evidence_color = match catalog.evidence {
            FeedbackEvidence::Consensus => GREEN,
            FeedbackEvidence::Provisional => BRASS,
            FeedbackEvidence::Unavailable => ISSUE,
        };
        Frame::none()
            .fill(PANEL_RAISED)
            .stroke(Stroke::new(1.0, Color32::from_rgb(48, 53, 51)))
            .inner_margin(Margin::symmetric(14.0, 11.0))
            .show(ui, |ui| {
                ui.horizontal(|ui| {
                    ui.label(
                        RichText::new(evidence_label(catalog.evidence))
                            .color(evidence_color)
                            .strong(),
                    );
                    ui.separator();
                    ui.label(
                        RichText::new(format!(
                            "{} actor-referenced base mesh IDs",
                            catalog.meshes.len()
                        ))
                        .color(INK),
                    );
                });
                ui.label(
                    RichText::new(asset_evidence_copy(catalog.evidence))
                        .size(12.0)
                        .color(MUTED),
                );
                if let Some(reason) = &catalog.unavailable_reason {
                    ui.label(RichText::new(reason).size(12.0).color(MUTED));
                }
            });
        ui.add_space(8.0);

        let filter = self.filter.to_ascii_lowercase();
        let mut clicked_mesh = None;
        ScrollArea::vertical()
            .max_height(ui.available_height())
            .show(ui, |ui| {
                for mesh in &catalog.meshes {
                    let actor_search = mesh
                        .actors
                        .iter()
                        .map(|actor| format!("{} {}", actor.race, actor.actor_id))
                        .collect::<Vec<_>>()
                        .join(" ");
                    let searchable = format!(
                        "{} {} {} {}",
                        mesh.mesh_id,
                        media_status_label(mesh.media_status),
                        mesh.physical_path.as_deref().unwrap_or_default(),
                        actor_search
                    )
                    .to_ascii_lowercase();
                    if !filter.is_empty() && !searchable.contains(&filter) {
                        continue;
                    }
                    let selected = self.selected_mesh == Some(mesh.mesh_id);
                    let response = Frame::none()
                        .fill(if selected {
                            Color32::from_rgb(59, 49, 31)
                        } else {
                            Color32::from_rgb(25, 30, 30)
                        })
                        .stroke(Stroke::new(
                            1.0,
                            if selected {
                                BRASS
                            } else {
                                Color32::from_rgb(48, 53, 51)
                            },
                        ))
                        .inner_margin(Margin::symmetric(13.0, 10.0))
                        .show(ui, |ui| {
                            ui.set_min_width(ui.available_width());
                            ui.horizontal(|ui| {
                                ui.vertical(|ui| {
                                    ui.label(
                                        RichText::new(format!("Mesh #{:05}", mesh.mesh_id))
                                            .size(16.0)
                                            .color(INK)
                                            .strong(),
                                    );
                                    ui.label(
                                        RichText::new(format!(
                                            "{} ACTOR BACKLINK{} · {}",
                                            mesh.actors.len(),
                                            if mesh.actors.len() == 1 { "" } else { "S" },
                                            media_status_label(mesh.media_status)
                                        ))
                                        .size(10.0)
                                        .color(MUTED),
                                    );
                                    ui.horizontal_wrapped(|ui| {
                                        for actor in &mesh.actors {
                                            ui.label(
                                                RichText::new(format!(
                                                    "{}  #{}",
                                                    actor.race, actor.actor_id
                                                ))
                                                .size(11.0)
                                                .color(BRASS),
                                            );
                                        }
                                    });
                                });
                                ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                                    ui.label(
                                        RichText::new(media_status_badge(mesh.media_status))
                                            .size(10.0)
                                            .color(media_status_color(mesh.media_status))
                                            .strong(),
                                    );
                                });
                            });
                        })
                        .response;
                    if response.interact(Sense::click()).clicked() {
                        clicked_mesh = Some(mesh.mesh_id);
                    }
                    ui.add_space(5.0);
                }
            });
        if let Some(mesh_id) = clicked_mesh {
            self.selected_mesh = Some(mesh_id);
            self.selected = None;
            self.selected_actor = None;
            self.selected_zone = None;
            self.selected_script = None;
        }
    }

    fn zone_catalog(&mut self, ui: &mut egui::Ui) {
        let Some(project) = self.project.as_ref() else {
            ui.vertical_centered(|ui| {
                ui.spinner();
                ui.label(RichText::new(&self.status).color(MUTED));
            });
            return;
        };
        let catalog = project.zone_catalog();
        let paired = catalog
            .zones
            .iter()
            .filter(|zone| zone.status == FeedbackZoneStatus::Paired)
            .count();
        Frame::none()
            .fill(PANEL_RAISED)
            .stroke(Stroke::new(1.0, Color32::from_rgb(48, 53, 51)))
            .inner_margin(Margin::symmetric(14.0, 11.0))
            .show(ui, |ui| {
                ui.horizontal(|ui| {
                    ui.label(
                        RichText::new("INVENTORY OBSERVATION")
                            .color(GREEN)
                            .strong(),
                    );
                    ui.separator();
                    ui.label(
                        RichText::new(format!("{} zones · {paired} paired", catalog.zones.len()))
                            .color(INK),
                    );
                    ui.separator();
                    ui.label(
                        RichText::new(format!(
                            "{} observed pairing issues",
                            catalog.diagnostics.len()
                        ))
                        .color(if catalog.diagnostics.is_empty() {
                            GREEN
                        } else {
                            ISSUE
                        }),
                    );
                });
                ui.label(
                    RichText::new(
                        "A zone is paired only when both Data/Areas and Data/Server Data/Areas contain its .dat filename.",
                    )
                    .size(12.0)
                    .color(MUTED),
                );
            });
        ui.add_space(8.0);

        let filter = self.filter.to_lowercase();
        let mut clicked_zone = None;
        ScrollArea::vertical()
            .max_height(ui.available_height())
            .show(ui, |ui| {
                for diagnostic in &catalog.diagnostics {
                    let searchable = format!(
                        "{} {} {}",
                        diagnostic.code, diagnostic.zone_name, diagnostic.message
                    )
                    .to_lowercase();
                    if !filter.is_empty() && !searchable.contains(&filter) {
                        continue;
                    }
                    let response = Frame::none()
                        .fill(Color32::from_rgb(42, 29, 25))
                        .stroke(Stroke::new(1.0, Color32::from_rgb(104, 62, 48)))
                        .inner_margin(Margin::symmetric(13.0, 9.0))
                        .show(ui, |ui| {
                            ui.set_min_width(ui.available_width());
                            ui.label(
                                RichText::new(format!(
                                    "{}  ·  {}",
                                    diagnostic.code, diagnostic.zone_name
                                ))
                                .size(10.0)
                                .color(ISSUE)
                                .strong(),
                            );
                            ui.label(RichText::new(&diagnostic.message).size(12.0).color(INK));
                        })
                        .response;
                    if response.interact(Sense::click()).clicked() {
                        clicked_zone = Some(diagnostic.zone_name.clone());
                    }
                    ui.add_space(5.0);
                }

                for zone in &catalog.zones {
                    let searchable =
                        format!("{} {}", zone.name, zone_status_label(zone.status)).to_lowercase();
                    if !filter.is_empty() && !searchable.contains(&filter) {
                        continue;
                    }
                    let selected = self.selected_zone.as_deref() == Some(zone.name.as_str());
                    let response = Frame::none()
                        .fill(if selected {
                            Color32::from_rgb(59, 49, 31)
                        } else {
                            Color32::from_rgb(25, 30, 30)
                        })
                        .stroke(Stroke::new(
                            1.0,
                            if selected {
                                BRASS
                            } else {
                                Color32::from_rgb(48, 53, 51)
                            },
                        ))
                        .inner_margin(Margin::symmetric(13.0, 10.0))
                        .show(ui, |ui| {
                            ui.set_min_width(ui.available_width());
                            ui.horizontal(|ui| {
                                ui.vertical(|ui| {
                                    ui.label(
                                        RichText::new(&zone.name).size(16.0).color(INK).strong(),
                                    );
                                    ui.horizontal(|ui| {
                                        zone_half_label(ui, "VISUAL", zone.visual_size);
                                        ui.label(RichText::new("·").color(MUTED));
                                        zone_half_label(ui, "GAMEPLAY", zone.gameplay_size);
                                    });
                                });
                                ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                                    ui.label(
                                        RichText::new(zone_status_badge(zone.status))
                                            .size(10.0)
                                            .color(zone_status_color(zone.status))
                                            .strong(),
                                    );
                                });
                            });
                        })
                        .response;
                    if response.interact(Sense::click()).clicked() {
                        clicked_zone = Some(zone.name.clone());
                    }
                    ui.add_space(5.0);
                }
            });
        if let Some(zone_name) = clicked_zone {
            self.selected_zone = Some(zone_name);
            self.selected = None;
            self.selected_actor = None;
            self.selected_mesh = None;
            self.selected_script = None;
        }
    }

    fn script_catalog(&mut self, ui: &mut egui::Ui) {
        let Some(project) = self.project.as_ref() else {
            ui.vertical_centered(|ui| {
                ui.spinner();
                ui.label(RichText::new(&self.status).color(MUTED));
            });
            return;
        };
        let catalog = project.script_catalog();
        Frame::none()
            .fill(PANEL_RAISED)
            .stroke(Stroke::new(1.0, Color32::from_rgb(48, 53, 51)))
            .inner_margin(Margin::symmetric(14.0, 11.0))
            .show(ui, |ui| {
                ui.horizontal(|ui| {
                    ui.label(RichText::new("ACTIVE SOURCE INVENTORY").color(GREEN).strong());
                    ui.separator();
                    ui.label(
                        RichText::new(format!("{} .rsl identities", catalog.scripts.len()))
                            .color(INK),
                    );
                    ui.separator();
                    ui.label(
                        RichText::new(format!("{} observed adjuncts", catalog.adjunct_files))
                            .color(BRASS),
                    );
                    ui.separator();
                    ui.label(
                        RichText::new(format!(
                            "{} inventory issues",
                            catalog.diagnostics.len()
                        ))
                        .color(if catalog.diagnostics.is_empty() {
                            GREEN
                        } else {
                            ISSUE
                        }),
                    );
                });
                ui.label(
                    RichText::new(
                        "Only immediate .rsl files anchor active script identities; .rcm and .rcscript files are adjacent observations, not asserted build outputs.",
                    )
                    .size(12.0)
                    .color(MUTED),
                );
                ui.add_space(5.0);
                ui.horizontal_wrapped(|ui| {
                    let all_label = format!("ALL  {}", catalog.scripts.len());
                    if ui
                        .selectable_label(self.script_family.is_none(), all_label)
                        .clicked()
                    {
                        self.script_family = None;
                        self.selected_script = None;
                    }
                    for family in FeedbackScriptFamily::ALL {
                        let count = catalog
                            .scripts
                            .iter()
                            .filter(|script| script.family == family)
                            .count();
                        if ui
                            .selectable_label(
                                self.script_family == Some(family),
                                format!("{}  {count}", family.label()),
                            )
                            .clicked()
                        {
                            self.script_family = Some(family);
                            self.selected_script = None;
                        }
                    }
                });
            });
        ui.add_space(8.0);

        let filter = self.filter.to_lowercase();
        let mut clicked_script = None;
        ScrollArea::vertical()
            .max_height(ui.available_height())
            .show(ui, |ui| {
                for diagnostic in &catalog.diagnostics {
                    let searchable = format!(
                        "{} {} {}",
                        diagnostic.code, diagnostic.script_name, diagnostic.message
                    )
                    .to_lowercase();
                    if !filter.is_empty() && !searchable.contains(&filter) {
                        continue;
                    }
                    Frame::none()
                        .fill(Color32::from_rgb(42, 29, 25))
                        .stroke(Stroke::new(1.0, Color32::from_rgb(104, 62, 48)))
                        .inner_margin(Margin::symmetric(13.0, 9.0))
                        .show(ui, |ui| {
                            ui.set_min_width(ui.available_width());
                            ui.label(
                                RichText::new(format!(
                                    "{}  ·  {}",
                                    diagnostic.code, diagnostic.script_name
                                ))
                                .size(10.0)
                                .color(ISSUE)
                                .strong(),
                            );
                            ui.label(RichText::new(&diagnostic.message).size(12.0).color(INK));
                        });
                    ui.add_space(5.0);
                }

                for script in &catalog.scripts {
                    if self
                        .script_family
                        .is_some_and(|family| family != script.family)
                    {
                        continue;
                    }
                    let searchable = format!(
                        "{} {} {} {} {}",
                        script.name,
                        script.family.label(),
                        script.source_path,
                        script.module_path.as_deref().unwrap_or_default(),
                        script.alternate_path.as_deref().unwrap_or_default()
                    )
                    .to_lowercase();
                    if !filter.is_empty() && !searchable.contains(&filter) {
                        continue;
                    }
                    let selected =
                        self.selected_script.as_deref() == Some(script.source_path.as_str());
                    let response = Frame::none()
                        .fill(if selected {
                            Color32::from_rgb(59, 49, 31)
                        } else {
                            Color32::from_rgb(25, 30, 30)
                        })
                        .stroke(Stroke::new(
                            1.0,
                            if selected {
                                BRASS
                            } else {
                                Color32::from_rgb(48, 53, 51)
                            },
                        ))
                        .inner_margin(Margin::symmetric(13.0, 10.0))
                        .show(ui, |ui| {
                            ui.set_min_width(ui.available_width());
                            ui.horizontal(|ui| {
                                ui.vertical(|ui| {
                                    ui.label(
                                        RichText::new(&script.name).size(16.0).color(INK).strong(),
                                    );
                                    ui.label(
                                        RichText::new(format!(
                                            ".RSL  {}  ·  {}",
                                            format_bytes(script.source_size),
                                            script_family_observation(script.family)
                                        ))
                                        .size(10.0)
                                        .color(MUTED),
                                    );
                                });
                                ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                                    if script.alternate_path.is_some() {
                                        ui.label(
                                            RichText::new("RCSCRIPT")
                                                .size(10.0)
                                                .color(MUTED)
                                                .strong(),
                                        );
                                    }
                                    if script.module_path.is_some() {
                                        ui.label(
                                            RichText::new("RCM").size(10.0).color(BRASS).strong(),
                                        );
                                    }
                                    ui.label(
                                        RichText::new("SOURCE").size(10.0).color(GREEN).strong(),
                                    );
                                });
                            });
                        })
                        .response;
                    if response.interact(Sense::click()).clicked() {
                        clicked_script = Some(script.source_path.clone());
                    }
                    ui.add_space(5.0);
                }
            });
        if let Some(source_path) = clicked_script {
            self.selected_script = Some(source_path);
            self.selected = None;
            self.selected_actor = None;
            self.selected_mesh = None;
            self.selected_zone = None;
        }
    }

    fn file_atlas(&mut self, ui: &mut egui::Ui) {
        Frame::none()
            .fill(PANEL_RAISED)
            .stroke(Stroke::new(1.0, Color32::from_rgb(48, 53, 51)))
            .inner_margin(Margin::same(1.0))
            .show(ui, |ui| {
                let available = ui.available_height();
                ScrollArea::vertical().max_height(available).show(ui, |ui| {
                    let entries = self
                        .project
                        .as_ref()
                        .map(|project| project.entries(self.lens))
                        .unwrap_or(&[]);
                    let filter = self.filter.to_ascii_lowercase();
                    if entries.is_empty() && self.receiver.is_some() {
                        ui.add_space(30.0);
                        ui.vertical_centered(|ui| {
                            ui.spinner();
                            ui.label(RichText::new(&self.status).color(MUTED));
                        });
                    }
                    for entry in entries.iter().filter(|entry| {
                        filter.is_empty() || entry.path.to_ascii_lowercase().contains(&filter)
                    }) {
                        let selected = self.selected.as_deref() == Some(entry.path.as_str());
                        let row = Frame::none()
                            .fill(if selected {
                                Color32::from_rgb(59, 49, 31)
                            } else {
                                Color32::TRANSPARENT
                            })
                            .inner_margin(Margin::symmetric(13.0, 8.0))
                            .show(ui, |ui| {
                                ui.set_min_width(ui.available_width());
                                ui.label(RichText::new(&entry.path).size(13.0).color(INK));
                                ui.label(
                                    RichText::new(format!(
                                        "{}  ·  {}  ·  {}",
                                        format_bytes(entry.size),
                                        entry.family.unwrap_or("unclassified"),
                                        entry.compatibility
                                    ))
                                    .size(11.0)
                                    .color(MUTED),
                                );
                            });
                        let response = ui.interact(
                            row.response.rect,
                            ui.make_persistent_id(&entry.path),
                            Sense::click(),
                        );
                        if response.hovered() {
                            ui.painter().rect_stroke(
                                response.rect,
                                0.0,
                                Stroke::new(1.0, BRASS_SOFT),
                            );
                        }
                        if response.clicked() {
                            self.selected = Some(entry.path.clone());
                            self.selected_actor = None;
                            self.selected_mesh = None;
                            self.selected_zone = None;
                            self.selected_script = None;
                        }
                    }
                });
            });
    }
}

impl eframe::App for LedgerApp {
    fn update(&mut self, context: &egui::Context, _frame: &mut eframe::Frame) {
        self.poll_load();
        self.top_bar(context);
        self.ledger(context);
        self.lens_rail(context);
        self.inspector(context);
        self.atlas(context);
        if self.receiver.is_some() {
            context.request_repaint_after(Duration::from_millis(16));
        }
    }
}

fn property(ui: &mut egui::Ui, label: &str, value: &str) {
    ui.label(RichText::new(label.to_uppercase()).size(10.0).color(MUTED));
    ui.label(RichText::new(value).size(14.0).color(INK));
    ui.add_space(9.0);
}

fn file_name(path: &str) -> &str {
    path.rsplit('/').next().unwrap_or(path)
}

const fn evidence_label(evidence: FeedbackEvidence) -> &'static str {
    match evidence {
        FeedbackEvidence::Consensus => "CONSENSUS",
        FeedbackEvidence::Provisional => "PROVISIONAL",
        FeedbackEvidence::Unavailable => "UNAVAILABLE",
    }
}

const fn asset_evidence_copy(evidence: FeedbackEvidence) -> &'static str {
    match evidence {
        FeedbackEvidence::Consensus => {
            "Derived only from the accepted actor/media slice; this does not assert a complete asset catalog or global orphan status."
        }
        FeedbackEvidence::Provisional => {
            "Raw IDs and actor backlinks remain browsable; catalog and physical-file conclusions are withheld."
        }
        FeedbackEvidence::Unavailable => {
            "Actor/base-mesh relationships are unavailable; use Files for exhaustive accepted asset inventory."
        }
    }
}

fn actor_count_label(count: FeedbackActorCount) -> String {
    match count {
        FeedbackActorCount::Agreed(count) => format!("{count} actors · count agreed"),
        FeedbackActorCount::Disagreed { client, server } => {
            format!("actor count disagrees: client {client} / server {server}")
        }
        FeedbackActorCount::Unavailable => "actor count unavailable".to_owned(),
    }
}

fn diagnostic_count_label(evidence: FeedbackEvidence, count: usize) -> String {
    match evidence {
        FeedbackEvidence::Consensus => format!("{count} actionable reference issues"),
        FeedbackEvidence::Provisional => "reference diagnostics withheld".to_owned(),
        FeedbackEvidence::Unavailable => "reference diagnostics unavailable".to_owned(),
    }
}

fn actor_count_smoke(count: FeedbackActorCount) -> String {
    match count {
        FeedbackActorCount::Agreed(count) => count.to_string(),
        FeedbackActorCount::Disagreed { client, server } => format!("{client}/{server}"),
        FeedbackActorCount::Unavailable => "unavailable".to_owned(),
    }
}

const fn media_status_label(status: FeedbackMediaStatus) -> &'static str {
    match status {
        FeedbackMediaStatus::NoBaseMesh => "No base mesh",
        FeedbackMediaStatus::Present => "Media present",
        FeedbackMediaStatus::MissingCatalog => "Catalog entry missing",
        FeedbackMediaStatus::MissingPhysical => "Physical file missing",
        FeedbackMediaStatus::Provisional => "Media status provisional",
    }
}

const fn media_status_badge(status: FeedbackMediaStatus) -> &'static str {
    match status {
        FeedbackMediaStatus::NoBaseMesh => "NO BASE MESH",
        FeedbackMediaStatus::Present => "PRESENT",
        FeedbackMediaStatus::MissingCatalog => "MISSING CATALOG",
        FeedbackMediaStatus::MissingPhysical => "MISSING FILE",
        FeedbackMediaStatus::Provisional => "PROVISIONAL",
    }
}

const fn media_status_color(status: FeedbackMediaStatus) -> Color32 {
    match status {
        FeedbackMediaStatus::Present | FeedbackMediaStatus::NoBaseMesh => GREEN,
        FeedbackMediaStatus::MissingCatalog | FeedbackMediaStatus::MissingPhysical => ISSUE,
        FeedbackMediaStatus::Provisional => BRASS,
    }
}

const fn zone_status_label(status: FeedbackZoneStatus) -> &'static str {
    match status {
        FeedbackZoneStatus::Paired => "Visual and gameplay halves observed",
        FeedbackZoneStatus::VisualOnly => "Gameplay half not observed",
        FeedbackZoneStatus::GameplayOnly => "Visual half not observed",
    }
}

const fn zone_status_badge(status: FeedbackZoneStatus) -> &'static str {
    match status {
        FeedbackZoneStatus::Paired => "PAIRED",
        FeedbackZoneStatus::VisualOnly => "VISUAL ONLY",
        FeedbackZoneStatus::GameplayOnly => "GAMEPLAY ONLY",
    }
}

const fn zone_status_color(status: FeedbackZoneStatus) -> Color32 {
    match status {
        FeedbackZoneStatus::Paired => GREEN,
        FeedbackZoneStatus::VisualOnly | FeedbackZoneStatus::GameplayOnly => ISSUE,
    }
}

fn zone_half_label(ui: &mut egui::Ui, label: &str, size: Option<u64>) {
    let (text, color) = size.map_or_else(
        || (format!("{label} MISSING"), ISSUE),
        |size| (format!("{label} {}", format_bytes(size)), GREEN),
    );
    ui.label(RichText::new(text).size(10.0).color(color).strong());
}

fn script_artifact_label(path: Option<&str>, size: Option<u64>) -> String {
    match (path, size) {
        (Some(path), Some(size)) => format!("{} · {}", path, format_bytes(size)),
        (Some(path), None) => format!("{path} · size unavailable"),
        (None, _) => "Not observed".to_owned(),
    }
}

const fn script_family_observation(family: FeedbackScriptFamily) -> &'static str {
    match family {
        FeedbackScriptFamily::Click => "CLICK_ literal prefix",
        FeedbackScriptFamily::Init => "INIT_ literal prefix",
        FeedbackScriptFamily::Item => "ITEM_ literal prefix",
        FeedbackScriptFamily::Quest => "QUEST_ literal prefix",
        FeedbackScriptFamily::Spell => "SPELL_ literal prefix",
        FeedbackScriptFamily::Other => "no recognized literal prefix",
    }
}

fn format_bytes(bytes: u64) -> String {
    const KIB: f64 = 1024.0;
    const MIB: f64 = KIB * 1024.0;
    const GIB: f64 = MIB * 1024.0;
    let bytes = bytes as f64;
    if bytes >= GIB {
        format!("{:.2} GiB", bytes / GIB)
    } else if bytes >= MIB {
        format!("{:.1} MiB", bytes / MIB)
    } else if bytes >= KIB {
        format!("{:.1} KiB", bytes / KIB)
    } else {
        format!("{} B", bytes as u64)
    }
}

#[cfg(test)]
mod tests {
    use super::reload_test_support::ReloadFixture;
    use super::{
        actor_mesh_target, actor_thread_targets, asset_evidence_copy, navigate_relationship,
        script_family_observation, transition_assets_view, transition_records_view,
        transition_scripts_view, transition_world_view, AssetsView, FeedbackEvidence,
        FeedbackMediaStatus, FeedbackScriptFamily, LedgerApp, Lens, LoadGate, LoadPurpose,
        PendingLoad, RecordsView, RelationshipTarget, ScriptsView, WorldView,
    };

    #[test]
    fn only_one_project_load_can_be_active() {
        let mut gate = LoadGate::default();
        assert!(gate.try_begin());
        assert!(!gate.try_begin());
        gate.finish();
        assert!(gate.try_begin());
    }

    #[test]
    fn records_view_transition_clears_incompatible_selection() {
        let mut selected_file = Some("Data/Server Data/Actors.dat".to_owned());
        let mut selected_actor = Some(7);

        let view = transition_records_view(
            RecordsView::Actors,
            RecordsView::Files,
            &mut selected_file,
            &mut selected_actor,
        );

        assert_eq!(view, RecordsView::Files);
        assert_eq!(selected_file, None);
        assert_eq!(selected_actor, None);
    }

    #[test]
    fn world_view_transition_clears_incompatible_selection() {
        let mut selected_file = Some("Data/Areas/Start.dat".to_owned());
        let mut selected_zone = Some("Start".to_owned());

        let view = transition_world_view(
            WorldView::Zones,
            WorldView::Files,
            &mut selected_file,
            &mut selected_zone,
        );

        assert_eq!(view, WorldView::Files);
        assert_eq!(selected_file, None);
        assert_eq!(selected_zone, None);
    }

    #[test]
    fn assets_view_transition_clears_incompatible_selection() {
        let mut selected_file = Some("Data/Meshes/Hero.b3d".to_owned());
        let mut selected_mesh = Some(7);

        let view = transition_assets_view(
            AssetsView::Relationships,
            AssetsView::Files,
            &mut selected_file,
            &mut selected_mesh,
        );

        assert_eq!(view, AssetsView::Files);
        assert_eq!(selected_file, None);
        assert_eq!(selected_mesh, None);
    }

    #[test]
    fn actor_to_mesh_navigation_opens_relationship_and_clears_hidden_context() {
        let mut lens = Lens::Records;
        let mut records_view = RecordsView::Files;
        let mut assets_view = AssetsView::Files;
        let mut filter = "Ork".to_owned();
        let mut selected_file = Some("Data/Server Data/Actors.dat".to_owned());
        let mut selected_actor = Some(2);
        let mut selected_mesh = None;
        let mut selected_zone = Some("Start".to_owned());
        let mut selected_script = Some("Data/Server Data/Scripts/Attack.rsl".to_owned());

        navigate_relationship(
            RelationshipTarget::Mesh(83),
            &mut lens,
            &mut records_view,
            &mut assets_view,
            &mut filter,
            &mut selected_file,
            &mut selected_actor,
            &mut selected_mesh,
            &mut selected_zone,
            &mut selected_script,
        );

        assert_eq!(lens, Lens::Assets);
        assert_eq!(assets_view, AssetsView::Relationships);
        assert!(filter.is_empty());
        assert_eq!(selected_file, None);
        assert_eq!(selected_actor, None);
        assert_eq!(selected_mesh, Some(83));
        assert_eq!(selected_zone, None);
        assert_eq!(selected_script, None);
    }

    #[test]
    fn actor_without_base_mesh_has_no_relationship_target() {
        assert_eq!(actor_mesh_target(None), None);
        assert_eq!(
            actor_mesh_target(Some(83)),
            Some(RelationshipTarget::Mesh(83))
        );
    }

    #[test]
    fn shared_mesh_exposes_every_ordered_actor_thread_without_truncation() {
        let actor_ids = (0_u16..512).collect::<Vec<_>>();
        let targets = actor_thread_targets(actor_ids.iter().copied());

        assert_eq!(targets.len(), actor_ids.len());
        assert_eq!(targets.first(), Some(&RelationshipTarget::Actor(0)));
        assert_eq!(targets.last(), Some(&RelationshipTarget::Actor(511)));
        assert!(targets.windows(2).all(|pair| match pair {
            [RelationshipTarget::Actor(left), RelationshipTarget::Actor(right)] => left < right,
            _ => false,
        }));
    }

    #[test]
    fn mesh_to_actor_navigation_opens_record_and_clears_hidden_context() {
        let mut lens = Lens::Assets;
        let mut records_view = RecordsView::Files;
        let mut assets_view = AssetsView::Files;
        let mut filter = "83".to_owned();
        let mut selected_file = Some("Data/Meshes/Orc.b3d".to_owned());
        let mut selected_actor = None;
        let mut selected_mesh = Some(83);
        let mut selected_zone = Some("Start".to_owned());
        let mut selected_script = Some("Data/Server Data/Scripts/Attack.rsl".to_owned());

        navigate_relationship(
            RelationshipTarget::Actor(4),
            &mut lens,
            &mut records_view,
            &mut assets_view,
            &mut filter,
            &mut selected_file,
            &mut selected_actor,
            &mut selected_mesh,
            &mut selected_zone,
            &mut selected_script,
        );

        assert_eq!(lens, Lens::Records);
        assert_eq!(records_view, RecordsView::Actors);
        assert!(filter.is_empty());
        assert_eq!(selected_file, None);
        assert_eq!(selected_actor, Some(4));
        assert_eq!(selected_mesh, None);
        assert_eq!(selected_zone, None);
        assert_eq!(selected_script, None);
    }

    #[test]
    fn asset_evidence_copy_distinguishes_provisional_from_unavailable() {
        assert_eq!(
            asset_evidence_copy(FeedbackEvidence::Provisional),
            "Raw IDs and actor backlinks remain browsable; catalog and physical-file conclusions are withheld."
        );
        assert_eq!(
            asset_evidence_copy(FeedbackEvidence::Unavailable),
            "Actor/base-mesh relationships are unavailable; use Files for exhaustive accepted asset inventory."
        );
    }

    #[test]
    fn scripts_view_transition_clears_incompatible_selection() {
        let mut selected_file = Some("Data/Server Data/Scripts/Default.rsl".to_owned());
        let mut selected_script = Some("Data/Server Data/Scripts/Default.rsl".to_owned());

        let view = transition_scripts_view(
            ScriptsView::Catalog,
            ScriptsView::Files,
            &mut selected_file,
            &mut selected_script,
        );

        assert_eq!(view, ScriptsView::Files);
        assert_eq!(selected_file, None);
        assert_eq!(selected_script, None);
    }

    #[test]
    fn script_family_copy_distinguishes_literal_prefixes_from_fallback() {
        assert_eq!(
            script_family_observation(FeedbackScriptFamily::Click),
            "CLICK_ literal prefix"
        );
        assert_eq!(
            script_family_observation(FeedbackScriptFamily::Other),
            "no recognized literal prefix"
        );
    }

    #[test]
    fn failed_open_and_disconnected_reload_preserve_the_accepted_session() {
        let fixture = ReloadFixture::new("failure-preserves");
        let accepted_root = fixture.root().to_path_buf();
        let accepted_project = fixture.project();
        let attempted_root = accepted_root.with_file_name("missing-replacement");
        let mut app = LedgerApp::shell(accepted_root.clone());
        app.project = Some(accepted_project.clone());
        app.lens = Lens::Assets;
        app.filter = "83".to_owned();
        app.selected = Some("Data/Meshes/Hero.b3d".to_owned());
        app.selected_mesh = Some(83);

        assert!(app.load_gate.try_begin());
        app.pending_load = Some(PendingLoad {
            root: attempted_root,
            purpose: LoadPurpose::Open,
        });
        app.finish_failed("replacement rejected".to_owned());

        assert_eq!(app.data_root, accepted_root);
        assert_eq!(app.project.as_ref(), Some(&accepted_project));
        assert_eq!(app.lens, Lens::Assets);
        assert_eq!(app.filter, "83");
        assert_eq!(app.selected.as_deref(), Some("Data/Meshes/Hero.b3d"));
        assert_eq!(app.selected_mesh, Some(83));
        assert!(app
            .status
            .contains("prior accepted snapshot remains visible"));
        assert!(!app.load_gate.is_active());
        assert!(app.pending_load.is_none());

        assert!(app.load_gate.try_begin());
        app.pending_load = Some(PendingLoad {
            root: accepted_root.clone(),
            purpose: LoadPurpose::Reload,
        });
        app.finish_disconnected();

        assert_eq!(app.data_root, accepted_root);
        assert_eq!(app.project.as_ref(), Some(&accepted_project));
        assert_eq!(app.lens, Lens::Assets);
        assert_eq!(app.filter, "83");
        assert_eq!(app.selected.as_deref(), Some("Data/Meshes/Hero.b3d"));
        assert_eq!(app.selected_mesh, Some(83));
        assert!(app
            .status
            .contains("prior accepted snapshot remains visible"));
        assert!(!app.load_gate.is_active());
        assert!(app.pending_load.is_none());
    }

    #[test]
    fn successful_open_changes_root_only_when_the_replacement_is_ready() {
        let accepted_fixture = ReloadFixture::new("open-old");
        let replacement_fixture = ReloadFixture::new("open-new");
        let accepted_root = accepted_fixture.root().to_path_buf();
        let replacement_root = replacement_fixture.root().to_path_buf();
        let accepted_project = accepted_fixture.project();
        let replacement_project = replacement_fixture.project();
        let mut app = LedgerApp::shell(accepted_root.clone());
        app.project = Some(accepted_project.clone());
        app.lens = Lens::World;
        app.filter = "Start".to_owned();
        app.selected_zone = Some("Start".to_owned());

        assert!(app.load_gate.try_begin());
        app.pending_load = Some(PendingLoad {
            root: replacement_root.clone(),
            purpose: LoadPurpose::Open,
        });

        assert_eq!(app.data_root, accepted_root);
        assert_eq!(app.project.as_ref(), Some(&accepted_project));
        assert_eq!(app.lens, Lens::World);
        assert_eq!(app.filter, "Start");
        assert_eq!(app.selected_zone.as_deref(), Some("Start"));

        app.finish_ready(Box::new(replacement_project.clone()));

        assert_eq!(app.data_root, replacement_root);
        assert_eq!(app.project.as_ref(), Some(&replacement_project));
        assert_eq!(app.lens, Lens::World);
        assert_eq!(app.filter, "Start");
        assert_eq!(app.selected_zone.as_deref(), Some("Start"));
        assert!(!app.load_gate.is_active());
        assert!(app.pending_load.is_none());
    }

    #[test]
    fn reload_atomically_refreshes_diagnostics_and_reconciles_raw_focus() {
        let fixture = ReloadFixture::new("refreshes-diagnostics");
        let root = fixture.root().to_path_buf();
        let before = fixture.project();
        assert_eq!(before.actor_catalog().diagnostics.len(), 2);
        let actor = before
            .actor_catalog()
            .actors
            .iter()
            .find(|actor| actor.actor_id == 4)
            .expect("missing-physical actor");
        assert_eq!(actor.media_status, FeedbackMediaStatus::MissingPhysical);
        let mesh_id = actor.base_mesh.expect("actor base mesh");

        let mut app = LedgerApp::shell(root.clone());
        app.project = Some(before);
        app.lens = Lens::Assets;
        app.filter = mesh_id.to_string();
        app.selected = Some("Data/Meshes/Hero.b3d".to_owned());
        app.selected_actor = Some(4);
        app.selected_mesh = Some(mesh_id);
        app.selected_zone = Some("Start".to_owned());
        app.selected_script = Some("Data/Server Data/Scripts/Quest.rsl".to_owned());

        fixture.add_missing_mesh();
        let refreshed = fixture.project();
        assert_eq!(refreshed.actor_catalog().diagnostics.len(), 1);
        assert_eq!(
            refreshed
                .actor_catalog()
                .actors
                .iter()
                .find(|actor| actor.actor_id == 4)
                .expect("refreshed actor")
                .media_status,
            FeedbackMediaStatus::Present
        );

        assert!(app.load_gate.try_begin());
        app.pending_load = Some(PendingLoad {
            root: root.clone(),
            purpose: LoadPurpose::Reload,
        });
        app.finish_ready(Box::new(refreshed));

        assert_eq!(app.data_root, root);
        assert_eq!(
            app.project
                .as_ref()
                .expect("accepted refreshed project")
                .actor_catalog()
                .diagnostics
                .len(),
            1
        );
        assert_eq!(app.lens, Lens::Assets);
        assert_eq!(app.filter, mesh_id.to_string());
        assert_eq!(app.selected.as_deref(), Some("Data/Meshes/Hero.b3d"));
        assert_eq!(app.selected_actor, Some(4));
        assert_eq!(app.selected_mesh, Some(mesh_id));
        assert_eq!(app.selected_zone.as_deref(), Some("Start"));
        assert_eq!(
            app.selected_script.as_deref(),
            Some("Data/Server Data/Scripts/Quest.rsl")
        );
        assert!(!app.load_gate.is_active());
        assert!(app.pending_load.is_none());

        app.selected = Some("Data/Meshes/Removed.b3d".to_owned());
        app.selected_actor = Some(65_535);
        app.selected_mesh = Some(65_535);
        app.selected_zone = Some("Removed".to_owned());
        app.selected_script = Some("Data/Server Data/Scripts/Removed.rsl".to_owned());
        assert!(app.load_gate.try_begin());
        app.pending_load = Some(PendingLoad {
            root: root.clone(),
            purpose: LoadPurpose::Reload,
        });
        let same_snapshot = fixture.project();
        app.finish_ready(Box::new(same_snapshot));

        assert_eq!(app.selected, None);
        assert_eq!(app.selected_actor, None);
        assert_eq!(app.selected_mesh, None);
        assert_eq!(app.selected_zone, None);
        assert_eq!(app.selected_script, None);
    }
}
