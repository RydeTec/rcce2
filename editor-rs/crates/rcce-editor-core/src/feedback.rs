use rcce_project::{
    classify, ActorCountEvidence, ActorMediaAvailability, ActorMediaConsensus, CompatibilityLevel,
    ConsensusLevel, MetadataBudget, ProjectRoot, ProjectSnapshot, ReadAssurance, ScanControl,
    SnapshotProgress, StateClass,
};
use std::{collections::BTreeMap, path::PathBuf};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Lens {
    Records,
    World,
    Assets,
    Scripts,
    Vault,
}

impl Lens {
    pub const ALL: [Self; 5] = [
        Self::Records,
        Self::World,
        Self::Assets,
        Self::Scripts,
        Self::Vault,
    ];

    #[must_use]
    pub const fn label(self) -> &'static str {
        match self {
            Self::Records => "Records",
            Self::World => "World",
            Self::Assets => "Assets",
            Self::Scripts => "Scripts",
            Self::Vault => "Vault",
        }
    }

    const fn index(self) -> usize {
        match self {
            Self::Records => 0,
            Self::World => 1,
            Self::Assets => 2,
            Self::Scripts => 3,
            Self::Vault => 4,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackEntry {
    pub path: String,
    pub size: u64,
    pub family: Option<&'static str>,
    pub compatibility: &'static str,
    pub classes: Vec<&'static str>,
    pub source_sha256: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FeedbackEvidence {
    Consensus,
    Provisional,
    Unavailable,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FeedbackActorCount {
    Agreed(usize),
    Disagreed { client: usize, server: usize },
    Unavailable,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FeedbackMediaStatus {
    NoBaseMesh,
    Present,
    MissingCatalog,
    MissingPhysical,
    Provisional,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackActor {
    pub actor_id: u16,
    pub race: String,
    pub race_is_lossy: bool,
    pub base_mesh: Option<u16>,
    pub media_status: FeedbackMediaStatus,
    pub physical_path: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackDiagnostic {
    pub code: &'static str,
    pub actor_id: u16,
    pub message: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackActorCatalog {
    pub evidence: FeedbackEvidence,
    pub count: FeedbackActorCount,
    pub actors: Vec<FeedbackActor>,
    pub diagnostics: Vec<FeedbackDiagnostic>,
    pub unavailable_reason: Option<String>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FeedbackZoneStatus {
    Paired,
    VisualOnly,
    GameplayOnly,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackZone {
    pub name: String,
    pub status: FeedbackZoneStatus,
    pub visual_path: Option<String>,
    pub visual_size: Option<u64>,
    pub gameplay_path: Option<String>,
    pub gameplay_size: Option<u64>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackZoneDiagnostic {
    pub code: &'static str,
    pub zone_name: String,
    pub message: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackZoneCatalog {
    pub zones: Vec<FeedbackZone>,
    pub diagnostics: Vec<FeedbackZoneDiagnostic>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FeedbackScriptFamily {
    Click,
    Init,
    Item,
    Quest,
    Spell,
    Other,
}

impl FeedbackScriptFamily {
    pub const ALL: [Self; 6] = [
        Self::Click,
        Self::Init,
        Self::Item,
        Self::Quest,
        Self::Spell,
        Self::Other,
    ];

    #[must_use]
    pub const fn label(self) -> &'static str {
        match self {
            Self::Click => "CLICK_",
            Self::Init => "INIT_",
            Self::Item => "ITEM_",
            Self::Quest => "QUEST_",
            Self::Spell => "SPELL_",
            Self::Other => "OTHER",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackScript {
    pub name: String,
    pub family: FeedbackScriptFamily,
    pub source_path: String,
    pub source_size: u64,
    pub module_path: Option<String>,
    pub module_size: Option<u64>,
    pub alternate_path: Option<String>,
    pub alternate_size: Option<u64>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackScriptDiagnostic {
    pub code: &'static str,
    pub script_name: String,
    pub message: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackScriptCatalog {
    pub scripts: Vec<FeedbackScript>,
    pub adjunct_files: usize,
    pub diagnostics: Vec<FeedbackScriptDiagnostic>,
}

#[derive(Debug, Clone)]
struct ScriptFileObservation {
    name: String,
    path: String,
    size: u64,
}

#[derive(Default)]
struct ScriptBundleBuilder {
    sources: Vec<ScriptFileObservation>,
    modules: Vec<ScriptFileObservation>,
    alternates: Vec<ScriptFileObservation>,
}

impl FeedbackScriptCatalog {
    fn from_script_entries(entries: &[FeedbackEntry]) -> Self {
        let mut bundles = BTreeMap::<String, ScriptBundleBuilder>::new();
        for entry in entries {
            let Some((name, kind)) = script_identity(&entry.path) else {
                continue;
            };
            let observation = ScriptFileObservation {
                name: name.clone(),
                path: entry.path.clone(),
                size: entry.size,
            };
            let bundle = bundles.entry(name.to_lowercase()).or_default();
            match kind {
                ScriptArtifactKind::Source => bundle.sources.push(observation),
                ScriptArtifactKind::Module => bundle.modules.push(observation),
                ScriptArtifactKind::Alternate => bundle.alternates.push(observation),
            }
        }

        let adjunct_files = bundles
            .values()
            .map(|bundle| bundle.modules.len() + bundle.alternates.len())
            .sum();
        let mut scripts = Vec::new();
        let mut diagnostics = Vec::new();
        for mut bundle in bundles.into_values() {
            for observations in [
                &mut bundle.sources,
                &mut bundle.modules,
                &mut bundle.alternates,
            ] {
                observations.sort_by(|left, right| left.path.as_bytes().cmp(right.path.as_bytes()));
            }
            let display_name = bundle
                .sources
                .first()
                .or_else(|| bundle.modules.first())
                .or_else(|| bundle.alternates.first())
                .map(|observation| observation.name.clone())
                .expect("script bundles contain an observation");

            if bundle.sources.is_empty() {
                diagnostics.push(FeedbackScriptDiagnostic {
                    code: "RCCE-SCRIPT-ADJUNCT-WITHOUT-SOURCE",
                    script_name: display_name.clone(),
                    message: format!(
                        "{display_name} has an observed .rcm or .rcscript artifact but no active .rsl source"
                    ),
                });
                continue;
            }
            if bundle.sources.len() > 1 {
                diagnostics.push(FeedbackScriptDiagnostic {
                    code: "RCCE-SCRIPT-SOURCE-CASE-COLLISION",
                    script_name: display_name.clone(),
                    message: format!(
                        "{display_name} has multiple .rsl source paths that differ only by case"
                    ),
                });
                continue;
            }
            let source = bundle.sources.pop().expect("one source was observed");
            let module = unique_adjunct(&bundle.modules, &display_name, ".rcm", &mut diagnostics);
            let alternate = unique_adjunct(
                &bundle.alternates,
                &display_name,
                ".rcscript",
                &mut diagnostics,
            );
            scripts.push(FeedbackScript {
                name: source.name.clone(),
                family: script_family(&source.name),
                source_path: source.path,
                source_size: source.size,
                module_path: module.map(|observation| observation.path.clone()),
                module_size: module.map(|observation| observation.size),
                alternate_path: alternate.map(|observation| observation.path.clone()),
                alternate_size: alternate.map(|observation| observation.size),
            });
        }
        scripts.sort_by(|left, right| {
            left.source_path
                .as_bytes()
                .cmp(right.source_path.as_bytes())
        });
        Self {
            scripts,
            adjunct_files,
            diagnostics,
        }
    }
}

fn unique_adjunct<'a>(
    observations: &'a [ScriptFileObservation],
    script_name: &str,
    extension: &str,
    diagnostics: &mut Vec<FeedbackScriptDiagnostic>,
) -> Option<&'a ScriptFileObservation> {
    if observations.len() > 1 {
        diagnostics.push(FeedbackScriptDiagnostic {
            code: "RCCE-SCRIPT-ADJUNCT-CASE-COLLISION",
            script_name: script_name.to_owned(),
            message: format!(
                "{script_name} has multiple {extension} artifact paths that differ only by case"
            ),
        });
        None
    } else {
        observations.first()
    }
}

#[derive(Default)]
struct ZonePairBuilder {
    name: String,
    visual_path: Option<String>,
    visual_size: Option<u64>,
    gameplay_path: Option<String>,
    gameplay_size: Option<u64>,
}

impl FeedbackZoneCatalog {
    fn from_world_entries(entries: &[FeedbackEntry]) -> Self {
        let mut pairs = BTreeMap::<String, ZonePairBuilder>::new();
        for entry in entries {
            let Some((name, half)) = zone_identity(&entry.path) else {
                continue;
            };
            let pair = pairs.entry(name.to_lowercase()).or_default();
            match half {
                ZoneHalf::Visual => {
                    pair.name.clone_from(&name);
                    pair.visual_path = Some(entry.path.clone());
                    pair.visual_size = Some(entry.size);
                }
                ZoneHalf::Gameplay => {
                    if pair.name.is_empty() {
                        pair.name.clone_from(&name);
                    }
                    pair.gameplay_path = Some(entry.path.clone());
                    pair.gameplay_size = Some(entry.size);
                }
            }
        }

        let zones = pairs
            .into_values()
            .map(|pair| {
                let status = match (pair.visual_path.is_some(), pair.gameplay_path.is_some()) {
                    (true, true) => FeedbackZoneStatus::Paired,
                    (true, false) => FeedbackZoneStatus::VisualOnly,
                    (false, true) => FeedbackZoneStatus::GameplayOnly,
                    (false, false) => unreachable!("zone pairs have at least one observed half"),
                };
                FeedbackZone {
                    name: pair.name,
                    status,
                    visual_path: pair.visual_path,
                    visual_size: pair.visual_size,
                    gameplay_path: pair.gameplay_path,
                    gameplay_size: pair.gameplay_size,
                }
            })
            .collect::<Vec<_>>();
        let diagnostics = zones
            .iter()
            .filter_map(|zone| match zone.status {
                FeedbackZoneStatus::Paired => None,
                FeedbackZoneStatus::VisualOnly => Some(FeedbackZoneDiagnostic {
                    code: "RCCE-ZONE-GAMEPLAY-HALF-MISSING",
                    zone_name: zone.name.clone(),
                    message: format!(
                        "{} has a visual area file but no observed gameplay area file",
                        zone.name
                    ),
                }),
                FeedbackZoneStatus::GameplayOnly => Some(FeedbackZoneDiagnostic {
                    code: "RCCE-ZONE-VISUAL-HALF-MISSING",
                    zone_name: zone.name.clone(),
                    message: format!(
                        "{} has a gameplay area file but no observed visual area file",
                        zone.name
                    ),
                }),
            })
            .collect();
        Self { zones, diagnostics }
    }
}

impl FeedbackActorCatalog {
    fn from_consensus(consensus: ActorMediaConsensus) -> Self {
        let evidence = match consensus.level() {
            ConsensusLevel::Consensus => FeedbackEvidence::Consensus,
            ConsensusLevel::Provisional => FeedbackEvidence::Provisional,
        };
        let count = match consensus.actor_count() {
            ActorCountEvidence::Agreed(count) => FeedbackActorCount::Agreed(count),
            ActorCountEvidence::Disagreed { client, server } => {
                FeedbackActorCount::Disagreed { client, server }
            }
        };
        let actors = consensus
            .actors()
            .iter()
            .map(|actor| {
                let display = actor.race.as_ref().map(|race| race.best_effort_display());
                FeedbackActor {
                    actor_id: actor.actor_id,
                    race: display.as_ref().map_or_else(
                        || format!("Actor {}", actor.actor_id),
                        |value| value.text().to_owned(),
                    ),
                    race_is_lossy: display.as_ref().is_some_and(|value| value.is_lossy()),
                    base_mesh: actor.base_mesh,
                    media_status: media_status(actor.availability),
                    physical_path: actor.physical_inventory_path.clone(),
                }
            })
            .collect::<Vec<_>>();
        let diagnostics = if evidence == FeedbackEvidence::Consensus {
            actors
                .iter()
                .filter_map(|actor| match actor.media_status {
                    FeedbackMediaStatus::MissingCatalog => Some(FeedbackDiagnostic {
                        code: "RCCE-ACTOR-MESH-CATALOG-MISSING",
                        actor_id: actor.actor_id,
                        message: format!(
                            "{} (actor #{}) references mesh #{} without a catalog entry",
                            actor.race,
                            actor.actor_id,
                            actor.base_mesh.unwrap_or_default()
                        ),
                    }),
                    FeedbackMediaStatus::MissingPhysical => Some(FeedbackDiagnostic {
                        code: "RCCE-ACTOR-MESH-FILE-MISSING",
                        actor_id: actor.actor_id,
                        message: format!(
                            "{} (actor #{}) references mesh #{} whose physical file is missing",
                            actor.race,
                            actor.actor_id,
                            actor.base_mesh.unwrap_or_default()
                        ),
                    }),
                    _ => None,
                })
                .collect()
        } else {
            Vec::new()
        };
        Self {
            evidence,
            count,
            actors,
            diagnostics,
            unavailable_reason: None,
        }
    }

    fn unavailable(reason: String) -> Self {
        Self {
            evidence: FeedbackEvidence::Unavailable,
            count: FeedbackActorCount::Unavailable,
            actors: Vec::new(),
            diagnostics: Vec::new(),
            unavailable_reason: Some(reason),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackProject {
    pub data_root: PathBuf,
    pub shape: String,
    pub unavailable: usize,
    actor_catalog: FeedbackActorCatalog,
    zone_catalog: FeedbackZoneCatalog,
    script_catalog: FeedbackScriptCatalog,
    by_lens: [Vec<FeedbackEntry>; 5],
    unclassified_files: usize,
    total_files: usize,
    total_bytes: u64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum FeedbackLoadProgress {
    Discovering,
    Inventory {
        entries: u64,
        files: u64,
        unavailable: u64,
    },
    Reading {
        path: String,
        completed_files: u64,
        total_files: u64,
    },
    Complete {
        files: u64,
        unavailable: u64,
        bytes: u64,
    },
}

pub fn load_feedback_project(
    data_root: PathBuf,
    mut progress: impl FnMut(FeedbackLoadProgress),
) -> Result<FeedbackProject, String> {
    progress(FeedbackLoadProgress::Discovering);
    let root = ProjectRoot::open_explicit(&data_root)
        .map_err(|error| format!("cannot open project data root: {:?}", error.code()))?;
    let snapshot = ProjectSnapshot::load(
        &root,
        MetadataBudget {
            max_entries: 10_000,
            max_directories: 2_000,
            max_depth: 64,
            max_path_bytes: 4_096,
            max_component_bytes: 255,
            max_declared_bytes: 1024 * 1024 * 1024,
            max_single_file_bytes: 256 * 1024 * 1024,
        },
        ReadAssurance::BaselineQuarantine,
        || ScanControl::Continue,
        |event| {
            progress(match event {
                SnapshotProgress::MetadataAccepted {
                    entries,
                    files,
                    unavailable,
                } => FeedbackLoadProgress::Inventory {
                    entries: *entries,
                    files: *files,
                    unavailable: *unavailable,
                },
                SnapshotProgress::FileAccepted {
                    path,
                    completed_files,
                    total_files,
                    ..
                } => FeedbackLoadProgress::Reading {
                    path: canonical_data_path(path),
                    completed_files: *completed_files,
                    total_files: *total_files,
                },
                SnapshotProgress::Complete {
                    files,
                    unavailable,
                    bytes,
                } => FeedbackLoadProgress::Complete {
                    files: *files,
                    unavailable: *unavailable,
                    bytes: *bytes,
                },
            });
        },
    )
    .map_err(|error| format!("project inventory failed: {error:?}"))?;
    let actor_catalog = match snapshot.load_actor_media_consensus(&root, || ScanControl::Continue) {
        Ok(consensus) => FeedbackActorCatalog::from_consensus(consensus),
        Err(error) => {
            FeedbackActorCatalog::unavailable(format!("Actor catalog unavailable: {error:?}"))
        }
    };
    Ok(FeedbackProject::from_data_root_with_actor_catalog(
        data_root,
        &snapshot,
        actor_catalog,
    ))
}

impl FeedbackProject {
    #[must_use]
    pub fn from_data_root(data_root: PathBuf, snapshot: &ProjectSnapshot) -> Self {
        Self::from_data_root_with_actor_catalog(
            data_root,
            snapshot,
            FeedbackActorCatalog::unavailable("Actor catalog was not requested".to_owned()),
        )
    }

    fn from_data_root_with_actor_catalog(
        data_root: PathBuf,
        snapshot: &ProjectSnapshot,
        actor_catalog: FeedbackActorCatalog,
    ) -> Self {
        let mut by_lens: [Vec<FeedbackEntry>; 5] = std::array::from_fn(|_| Vec::new());
        let mut unclassified_files = 0;
        let inventory = snapshot.inventory();
        let total_bytes = inventory.files.iter().map(|file| file.size).sum();
        let mut has_authoring_input = false;

        for file in &inventory.files {
            let path = canonical_data_path(&file.path);
            let classification = classify(&path);
            has_authoring_input |= classification.authoring_input;
            let entry = FeedbackEntry {
                path: path.clone(),
                size: file.size,
                family: classification.family,
                compatibility: compatibility_label(classification.compatibility),
                classes: classification
                    .classes
                    .iter()
                    .copied()
                    .map(class_label)
                    .collect(),
                source_sha256: hex(&file.fingerprint.0),
            };
            let lens = lens_for(&path).unwrap_or_else(|| {
                unclassified_files += 1;
                Lens::Records
            });
            by_lens[lens.index()].push(entry);
        }

        for entries in &mut by_lens {
            entries.sort_by(|left, right| left.path.as_bytes().cmp(right.path.as_bytes()));
        }
        let zone_catalog = FeedbackZoneCatalog::from_world_entries(&by_lens[Lens::World.index()]);
        let script_catalog =
            FeedbackScriptCatalog::from_script_entries(&by_lens[Lens::Scripts.index()]);
        Self {
            data_root,
            shape: if inventory.files.is_empty() {
                "Empty"
            } else if has_authoring_input {
                "AuthoringProject"
            } else {
                "UnknownOnly"
            }
            .to_owned(),
            unavailable: inventory.unavailable.len(),
            actor_catalog,
            zone_catalog,
            script_catalog,
            by_lens,
            unclassified_files,
            total_files: inventory.files.len(),
            total_bytes,
        }
    }

    #[must_use]
    pub const fn total_files(&self) -> usize {
        self.total_files
    }

    #[must_use]
    pub const fn total_bytes(&self) -> u64 {
        self.total_bytes
    }

    #[must_use]
    pub fn entries(&self, lens: Lens) -> &[FeedbackEntry] {
        &self.by_lens[lens.index()]
    }

    #[must_use]
    pub const fn actor_catalog(&self) -> &FeedbackActorCatalog {
        &self.actor_catalog
    }

    #[must_use]
    pub const fn zone_catalog(&self) -> &FeedbackZoneCatalog {
        &self.zone_catalog
    }

    #[must_use]
    pub const fn script_catalog(&self) -> &FeedbackScriptCatalog {
        &self.script_catalog
    }

    #[must_use]
    pub fn unclassified_files(&self) -> usize {
        self.unclassified_files
    }

    pub fn all_entries(&self) -> impl Iterator<Item = &FeedbackEntry> {
        self.by_lens.iter().flat_map(|entries| entries.iter())
    }
}

const fn media_status(availability: ActorMediaAvailability) -> FeedbackMediaStatus {
    match availability {
        ActorMediaAvailability::NoBaseMesh => FeedbackMediaStatus::NoBaseMesh,
        ActorMediaAvailability::Present => FeedbackMediaStatus::Present,
        ActorMediaAvailability::MissingCatalog => FeedbackMediaStatus::MissingCatalog,
        ActorMediaAvailability::MissingPhysical => FeedbackMediaStatus::MissingPhysical,
        ActorMediaAvailability::Provisional => FeedbackMediaStatus::Provisional,
    }
}

fn canonical_data_path(path: &str) -> String {
    if path
        .get(..5)
        .is_some_and(|prefix| prefix.eq_ignore_ascii_case("Data/"))
    {
        format!("Data/{}", &path[5..])
    } else {
        format!("Data/{path}")
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum ZoneHalf {
    Visual,
    Gameplay,
}

fn zone_identity(path: &str) -> Option<(String, ZoneHalf)> {
    let (relative, half) = if path
        .get(..11)
        .is_some_and(|prefix| prefix.eq_ignore_ascii_case("Data/Areas/"))
    {
        (&path[11..], ZoneHalf::Visual)
    } else if path
        .get(..23)
        .is_some_and(|prefix| prefix.eq_ignore_ascii_case("Data/Server Data/Areas/"))
    {
        (&path[23..], ZoneHalf::Gameplay)
    } else {
        return None;
    };
    if relative.contains('/') || relative.len() <= 4 {
        return None;
    }
    let suffix_start = relative.len().checked_sub(4)?;
    let suffix = relative.get(suffix_start..)?;
    if !suffix.eq_ignore_ascii_case(".dat") {
        return None;
    }
    Some((relative.get(..suffix_start)?.to_owned(), half))
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum ScriptArtifactKind {
    Source,
    Module,
    Alternate,
}

fn script_identity(path: &str) -> Option<(String, ScriptArtifactKind)> {
    const PREFIX: &str = "Data/Server Data/Scripts/";
    if !path
        .get(..PREFIX.len())
        .is_some_and(|prefix| prefix.eq_ignore_ascii_case(PREFIX))
    {
        return None;
    }
    let relative = path.get(PREFIX.len()..)?;
    if relative.contains('/') {
        return None;
    }
    let (name, extension) = relative.rsplit_once('.')?;
    if name.is_empty() {
        return None;
    }
    let kind = if extension.eq_ignore_ascii_case("rsl") {
        ScriptArtifactKind::Source
    } else if extension.eq_ignore_ascii_case("rcm") {
        ScriptArtifactKind::Module
    } else if extension.eq_ignore_ascii_case("rcscript") {
        ScriptArtifactKind::Alternate
    } else {
        return None;
    };
    Some((name.to_owned(), kind))
}

fn script_family(name: &str) -> FeedbackScriptFamily {
    if starts_with_ascii(name, "Click_") {
        FeedbackScriptFamily::Click
    } else if starts_with_ascii(name, "Init_") {
        FeedbackScriptFamily::Init
    } else if starts_with_ascii(name, "Item_") {
        FeedbackScriptFamily::Item
    } else if starts_with_ascii(name, "Quest_") {
        FeedbackScriptFamily::Quest
    } else if starts_with_ascii(name, "Spell_") {
        FeedbackScriptFamily::Spell
    } else {
        FeedbackScriptFamily::Other
    }
}

fn starts_with_ascii(value: &str, prefix: &str) -> bool {
    value
        .get(..prefix.len())
        .is_some_and(|candidate| candidate.eq_ignore_ascii_case(prefix))
}

fn lens_for(path: &str) -> Option<Lens> {
    let path = path.to_ascii_lowercase();
    if path.contains("/server data/accounts")
        || path.contains("/server data/mysql")
        || path.contains("/server data/privileged scripts")
        || path.contains("/server data/names filter")
        || path.contains("/server data/dropped items")
        || path.contains("/server data/superglobals")
    {
        Some(Lens::Vault)
    } else if path.contains("/server data/scripts/")
        || path.contains("/server data/script files/")
        || path.ends_with(".rsl")
    {
        Some(Lens::Scripts)
    } else if path.contains("/areas/")
        || path.contains("/rcte/")
        || path.contains("/rctrees/")
        || path.contains("/rccaves/")
        || path.contains("/architect/")
    {
        Some(Lens::World)
    } else if path.contains("/meshes/")
        || path.contains("/textures/")
        || path.contains("/sounds/")
        || path.contains("/music/")
        || path.contains("/emitter configs/")
        || path.contains("/ui/")
    {
        Some(Lens::Assets)
    } else if (path.contains("/game data/") || path.contains("/server data/"))
        && (path.ends_with(".dat") || path.ends_with(".txt") || path.ends_with(".xml"))
    {
        Some(Lens::Records)
    } else {
        None
    }
}

const fn compatibility_label(level: CompatibilityLevel) -> &'static str {
    match level {
        CompatibilityLevel::InventoryOnly => "inventory only",
        CompatibilityLevel::Read => "read",
        CompatibilityLevel::Unknown => "unknown",
    }
}

const fn class_label(class: StateClass) -> &'static str {
    match class {
        StateClass::AuthoringSource => "authoring source",
        StateClass::EditorMetadata => "editor metadata",
        StateClass::DynamicPrivate => "dynamic private",
        StateClass::Secret => "secret",
        StateClass::PublicClient => "public client",
        StateClass::ServerConfig => "server config",
        StateClass::Unknown => "unknown",
    }
}

fn hex(bytes: &[u8]) -> String {
    const HEX: &[u8; 16] = b"0123456789abcdef";
    let mut output = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        output.push(char::from(HEX[usize::from(byte >> 4)]));
        output.push(char::from(HEX[usize::from(byte & 0x0f)]));
    }
    output
}
