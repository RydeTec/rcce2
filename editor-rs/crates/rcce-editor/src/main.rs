use eframe::egui::{
    self, Align, Color32, FontFamily, FontId, Frame, Layout, Margin, RichText, ScrollArea, Sense,
    Stroke, TextEdit, Vec2,
};
use rcce_editor_core::{
    load_feedback_project, FeedbackActor, FeedbackActorCount, FeedbackEntry, FeedbackEvidence,
    FeedbackLoadProgress, FeedbackMediaStatus, FeedbackProject, Lens,
};
use std::{
    path::{Path, PathBuf},
    process::ExitCode,
    sync::mpsc::{self, Receiver},
    thread,
    time::Duration,
};

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
                    "[super-editor-mvp] ready: {} files, {} bytes, {} unavailable; actors={} actor_evidence={} actor_reference_issues={}",
                    project.total_files(),
                    project.total_bytes(),
                    project.unavailable,
                    actor_count_smoke(actor_catalog.count),
                    evidence_label(actor_catalog.evidence).to_ascii_lowercase(),
                    actor_catalog.diagnostics.len()
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

struct LedgerApp {
    data_root: PathBuf,
    project: Option<FeedbackProject>,
    receiver: Option<Receiver<LoadMessage>>,
    lens: Lens,
    filter: String,
    selected: Option<String>,
    selected_actor: Option<u16>,
    records_view: RecordsView,
    status: String,
    activity: Vec<String>,
    load_gate: LoadGate,
}

impl LedgerApp {
    fn new(data_root: PathBuf) -> Self {
        let mut app = Self {
            data_root,
            project: None,
            receiver: None,
            lens: Lens::Records,
            filter: String::new(),
            selected: None,
            selected_actor: None,
            records_view: RecordsView::Actors,
            status: "Preparing project inventory…".to_owned(),
            activity: vec!["Feedback MVP started in read-only mode".to_owned()],
            load_gate: LoadGate::default(),
        };
        app.begin_load();
        app
    }

    fn begin_load(&mut self) {
        if !self.load_gate.try_begin() {
            self.activity.insert(
                0,
                "Project open ignored while an inventory is active".to_owned(),
            );
            return;
        }
        let path = self.data_root.clone();
        let (sender, receiver) = mpsc::channel();
        self.receiver = Some(receiver);
        self.project = None;
        self.selected = None;
        self.selected_actor = None;
        self.status = "Opening selected project…".to_owned();
        self.activity
            .insert(0, format!("Inventory requested: {}", path.display()));
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
                    self.status = "Project inventory ended unexpectedly".to_owned();
                    self.activity.insert(0, self.status.clone());
                    self.load_gate.finish();
                    return;
                }
            };
            match message {
                LoadMessage::Progress(progress) => self.apply_progress(progress),
                LoadMessage::Ready(project) => {
                    let actor_count = actor_count_label(project.actor_catalog().count);
                    self.status = format!(
                        "{} files indexed · {actor_count} · {} unavailable",
                        project.total_files(),
                        project.unavailable
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
                            "Snapshot accepted: {} across {}",
                            format_bytes(project.total_bytes()),
                            project.shape
                        ),
                    );
                    self.project = Some(*project);
                    self.load_gate.finish();
                    return;
                }
                LoadMessage::Failed(error) => {
                    self.status = error.clone();
                    self.activity.insert(0, format!("Open failed: {error}"));
                    self.load_gate.finish();
                    return;
                }
            }
        }
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
            self.data_root = normalize_selected_root(path);
            self.begin_load();
        }
        #[cfg(not(windows))]
        self.activity.insert(
            0,
            "Folder selection is enabled in the Windows feedback build".to_owned(),
        );
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
                    }
                }
                ui.with_layout(Layout::bottom_up(Align::LEFT), |ui| {
                    ui.label(
                        RichText::new("Feedback build 0.2\nNo save or mutation commands exist")
                            .size(11.0)
                            .color(MUTED),
                    );
                });
            });
    }

    fn inspector(&mut self, context: &egui::Context) {
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
                    ui.label(RichText::new("Select a record or file").size(18.0).color(INK));
                    ui.label(
                        RichText::new(
                            "Actor records expose stable identity and media health. Files expose classification and exact fingerprints.",
                        )
                        .color(MUTED),
                    );
                }
            });
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
        egui::CentralPanel::default()
            .frame(Frame::none().fill(CANVAS).inner_margin(Margin::same(20.0)))
            .show(context, |ui| {
                ui.horizontal(|ui| {
                    ui.vertical(|ui| {
                        let title = if self.lens == Lens::Records
                            && self.records_view == RecordsView::Actors
                        {
                            "Actor catalog".to_owned()
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
                            RichText::new(if self.lens == Lens::Records
                                && self.records_view == RecordsView::Actors
                            {
                                "Client/server consensus · stable actor identities · live media health"
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
                                .hint_text(if self.lens == Lens::Records
                                    && self.records_view == RecordsView::Actors
                                {
                                    "Filter actors, ids, or states…"
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
                    });
                });
                ui.add_space(14.0);
                if self.lens == Lens::Records && self.records_view == RecordsView::Actors {
                    self.actor_catalog(ui);
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
    use super::{transition_records_view, LoadGate, RecordsView};

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
}
