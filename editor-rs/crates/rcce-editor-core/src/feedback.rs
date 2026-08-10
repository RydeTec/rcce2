use rcce_project::{
    classify, ActorCountEvidence, ActorMediaAvailability, ActorMediaConsensus, CompatibilityLevel,
    ConsensusLevel, MetadataBudget, ProjectRoot, ProjectSnapshot, ReadAssurance, ScanControl,
    SnapshotProgress, StateClass,
};
use std::{
    collections::{BTreeMap, VecDeque},
    path::PathBuf,
};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Lens {
    Records,
    World,
    Assets,
    Scripts,
    Vault,
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub enum FeedbackFocusTarget {
    File { lens: Lens, path: String },
    Actor { actor_id: u16 },
    Mesh { mesh_id: u16 },
    Zone { name: String },
    Script { source_path: String },
}

pub type FeedbackFindTarget = FeedbackFocusTarget;

pub const FEEDBACK_RETURN_TRAIL_CAPACITY: usize = 32;

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct FeedbackReturnTrail {
    entries: VecDeque<FeedbackFocusTarget>,
}

impl FeedbackReturnTrail {
    pub fn push(&mut self, target: FeedbackFocusTarget) -> bool {
        if self.entries.back() == Some(&target) {
            return false;
        }
        if self.entries.len() == FEEDBACK_RETURN_TRAIL_CAPACITY {
            let _ = self.entries.pop_front();
        }
        self.entries.push_back(target);
        true
    }

    pub fn pop_resolved(
        &mut self,
        current: Option<&FeedbackFocusTarget>,
        mut is_resolved: impl FnMut(&FeedbackFocusTarget) -> bool,
    ) -> Option<FeedbackFocusTarget> {
        while let Some(target) = self.entries.pop_back() {
            if current == Some(&target) || !is_resolved(&target) {
                continue;
            }
            return Some(target);
        }
        None
    }

    pub fn next_resolved(
        &self,
        current: Option<&FeedbackFocusTarget>,
        mut is_resolved: impl FnMut(&FeedbackFocusTarget) -> bool,
    ) -> Option<&FeedbackFocusTarget> {
        self.entries
            .iter()
            .rev()
            .find(|target| current != Some(*target) && is_resolved(target))
    }

    pub fn retain_resolved(
        &mut self,
        mut is_resolved: impl FnMut(&FeedbackFocusTarget) -> bool,
    ) -> usize {
        let prior_len = self.entries.len();
        self.entries.retain(|target| is_resolved(target));
        prior_len - self.entries.len()
    }

    pub fn clear(&mut self) {
        self.entries.clear();
    }

    #[must_use]
    pub fn len(&self) -> usize {
        self.entries.len()
    }

    #[must_use]
    pub fn is_empty(&self) -> bool {
        self.entries.is_empty()
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackFindResult {
    pub target: FeedbackFindTarget,
    pub label: String,
    pub detail: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
struct FeedbackFindCandidate {
    result: FeedbackFindResult,
    fields: Vec<String>,
    target_kind: u8,
    target_identity: Vec<u8>,
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
    state_classes: Vec<StateClass>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FeedbackAcceptedFileDeltaKind {
    NewlyAccepted,
    NoLongerAccepted,
    ContentFingerprintChanged,
}

impl FeedbackAcceptedFileDeltaKind {
    pub const ALL: [Self; 3] = [
        Self::NewlyAccepted,
        Self::NoLongerAccepted,
        Self::ContentFingerprintChanged,
    ];

    #[must_use]
    pub const fn label(self) -> &'static str {
        match self {
            Self::NewlyAccepted => "NEWLY ACCEPTED",
            Self::NoLongerAccepted => "NO LONGER ACCEPTED",
            Self::ContentFingerprintChanged => "CONTENT FINGERPRINT CHANGED",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackAcceptedFileDelta {
    pub kind: FeedbackAcceptedFileDeltaKind,
    pub path: String,
    pub prior_size: Option<u64>,
    pub current_size: Option<u64>,
    pub prior_source_sha256: Option<String>,
    pub current_source_sha256: Option<String>,
    pub current_lens: Option<Lens>,
}

impl FeedbackAcceptedFileDelta {
    #[must_use]
    pub fn current_target(&self) -> Option<FeedbackFindTarget> {
        self.current_lens.map(|lens| FeedbackFindTarget::File {
            lens,
            path: self.path.clone(),
        })
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackAcceptedSnapshotDelta {
    pub prior_files: usize,
    pub current_files: usize,
    pub newly_accepted: usize,
    pub no_longer_accepted: usize,
    pub content_fingerprint_changed: usize,
    pub entries: Vec<FeedbackAcceptedFileDelta>,
}

impl FeedbackAcceptedSnapshotDelta {
    #[must_use]
    pub fn between_same_root(prior: &FeedbackProject, current: &FeedbackProject) -> Option<Self> {
        if prior.data_root != current.data_root {
            return None;
        }

        let prior_by_path = accepted_entries_by_path(prior);
        let current_by_path = accepted_entries_by_path(current);
        let mut newly_accepted = Vec::new();
        let mut no_longer_accepted = Vec::new();
        let mut fingerprint_changed = Vec::new();

        for (path, (lens, entry)) in &current_by_path {
            match prior_by_path.get(path) {
                None => newly_accepted.push(FeedbackAcceptedFileDelta {
                    kind: FeedbackAcceptedFileDeltaKind::NewlyAccepted,
                    path: (*path).to_owned(),
                    prior_size: None,
                    current_size: Some(entry.size),
                    prior_source_sha256: None,
                    current_source_sha256: Some(entry.source_sha256.clone()),
                    current_lens: Some(*lens),
                }),
                Some((_, prior_entry)) if prior_entry.source_sha256 != entry.source_sha256 => {
                    fingerprint_changed.push(FeedbackAcceptedFileDelta {
                        kind: FeedbackAcceptedFileDeltaKind::ContentFingerprintChanged,
                        path: (*path).to_owned(),
                        prior_size: Some(prior_entry.size),
                        current_size: Some(entry.size),
                        prior_source_sha256: Some(prior_entry.source_sha256.clone()),
                        current_source_sha256: Some(entry.source_sha256.clone()),
                        current_lens: Some(*lens),
                    });
                }
                Some(_) => {}
            }
        }

        for (path, (_, entry)) in &prior_by_path {
            if !current_by_path.contains_key(path) {
                no_longer_accepted.push(FeedbackAcceptedFileDelta {
                    kind: FeedbackAcceptedFileDeltaKind::NoLongerAccepted,
                    path: (*path).to_owned(),
                    prior_size: Some(entry.size),
                    current_size: None,
                    prior_source_sha256: Some(entry.source_sha256.clone()),
                    current_source_sha256: None,
                    current_lens: None,
                });
            }
        }

        let newly_accepted_count = newly_accepted.len();
        let no_longer_accepted_count = no_longer_accepted.len();
        let content_fingerprint_changed_count = fingerprint_changed.len();
        let mut entries = newly_accepted;
        entries.extend(no_longer_accepted);
        entries.extend(fingerprint_changed);
        Some(Self {
            prior_files: prior.total_files(),
            current_files: current.total_files(),
            newly_accepted: newly_accepted_count,
            no_longer_accepted: no_longer_accepted_count,
            content_fingerprint_changed: content_fingerprint_changed_count,
            entries,
        })
    }

    #[must_use]
    pub fn count(&self, kind: FeedbackAcceptedFileDeltaKind) -> usize {
        match kind {
            FeedbackAcceptedFileDeltaKind::NewlyAccepted => self.newly_accepted,
            FeedbackAcceptedFileDeltaKind::NoLongerAccepted => self.no_longer_accepted,
            FeedbackAcceptedFileDeltaKind::ContentFingerprintChanged => {
                self.content_fingerprint_changed
            }
        }
    }

    #[must_use]
    pub fn range(&self, kind: Option<FeedbackAcceptedFileDeltaKind>) -> std::ops::Range<usize> {
        match kind {
            None => 0..self.entries.len(),
            Some(FeedbackAcceptedFileDeltaKind::NewlyAccepted) => 0..self.newly_accepted,
            Some(FeedbackAcceptedFileDeltaKind::NoLongerAccepted) => {
                self.newly_accepted..self.newly_accepted + self.no_longer_accepted
            }
            Some(FeedbackAcceptedFileDeltaKind::ContentFingerprintChanged) => {
                self.newly_accepted + self.no_longer_accepted..self.entries.len()
            }
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FeedbackVaultFacet {
    All,
    Secret,
    DynamicPrivate,
    ServerConfig,
    Other,
}

impl FeedbackVaultFacet {
    pub const FILTERS: [Self; 4] = [
        Self::Secret,
        Self::DynamicPrivate,
        Self::ServerConfig,
        Self::Other,
    ];

    #[must_use]
    pub const fn label(self) -> &'static str {
        match self {
            Self::All => "ALL",
            Self::Secret => "SECRET",
            Self::DynamicPrivate => "DYNAMIC PRIVATE",
            Self::ServerConfig => "SERVER CONFIG",
            Self::Other => "UNKNOWN / OTHER",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackVaultEntry {
    pub path: String,
    pub facets: Vec<FeedbackVaultFacet>,
}

impl FeedbackVaultEntry {
    #[must_use]
    pub fn has_facet(&self, facet: FeedbackVaultFacet) -> bool {
        facet == FeedbackVaultFacet::All || self.facets.contains(&facet)
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackVaultCatalog {
    pub entries: Vec<FeedbackVaultEntry>,
}

impl FeedbackVaultCatalog {
    fn from_entries(entries: &[FeedbackEntry]) -> Self {
        let entries = entries
            .iter()
            .map(|entry| {
                let mut facets = Vec::new();
                if entry.state_classes.contains(&StateClass::Secret) {
                    facets.push(FeedbackVaultFacet::Secret);
                }
                if entry.state_classes.contains(&StateClass::DynamicPrivate) {
                    facets.push(FeedbackVaultFacet::DynamicPrivate);
                }
                if entry.state_classes.contains(&StateClass::ServerConfig) {
                    facets.push(FeedbackVaultFacet::ServerConfig);
                }
                if facets.is_empty() {
                    facets.push(FeedbackVaultFacet::Other);
                }
                FeedbackVaultEntry {
                    path: entry.path.clone(),
                    facets,
                }
            })
            .collect();
        Self { entries }
    }

    #[must_use]
    pub fn count(&self, facet: FeedbackVaultFacet) -> usize {
        self.entries
            .iter()
            .filter(|entry| entry.has_facet(facet))
            .count()
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FeedbackEvidence {
    Consensus,
    Provisional,
    Unavailable,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FeedbackObservationEvidence {
    ConsensusDiagnostic,
    FilenamePairing,
    ScriptInventory,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackObservation {
    pub evidence: FeedbackObservationEvidence,
    pub code: &'static str,
    pub raw_identity: String,
    pub message: String,
    pub target: FeedbackFocusTarget,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackObservationIndex {
    pub actor_evidence: FeedbackEvidence,
    pub actor_issues: usize,
    pub zone_observations: usize,
    pub script_observations: usize,
    pub observations: Vec<FeedbackObservation>,
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

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackActorReference {
    pub actor_id: u16,
    pub race: String,
    pub race_is_lossy: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackActorMesh {
    pub mesh_id: u16,
    pub media_status: FeedbackMediaStatus,
    pub physical_path: Option<String>,
    pub actors: Vec<FeedbackActorReference>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackAssetCatalog {
    pub evidence: FeedbackEvidence,
    pub meshes: Vec<FeedbackActorMesh>,
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
    pub path: String,
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
                push_script_diagnostics(
                    bundle.modules.iter().chain(&bundle.alternates),
                    "RCCE-SCRIPT-ADJUNCT-WITHOUT-SOURCE",
                    &display_name,
                    format!(
                        "{display_name} has an observed .rcm or .rcscript artifact but no active .rsl source"
                    ),
                    &mut diagnostics,
                );
                continue;
            }
            if bundle.sources.len() > 1 {
                push_script_diagnostics(
                    bundle.sources.iter(),
                    "RCCE-SCRIPT-SOURCE-CASE-COLLISION",
                    &display_name,
                    format!(
                        "{display_name} has multiple .rsl source paths that differ only by case"
                    ),
                    &mut diagnostics,
                );
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
        diagnostics.sort_by(|left, right| left.path.as_bytes().cmp(right.path.as_bytes()));
        Self {
            scripts,
            adjunct_files,
            diagnostics,
        }
    }
}

fn push_script_diagnostics<'a>(
    observations: impl Iterator<Item = &'a ScriptFileObservation>,
    code: &'static str,
    script_name: &str,
    message: String,
    diagnostics: &mut Vec<FeedbackScriptDiagnostic>,
) {
    diagnostics.extend(observations.map(|observation| FeedbackScriptDiagnostic {
        code,
        script_name: script_name.to_owned(),
        path: observation.path.clone(),
        message: message.clone(),
    }));
}

fn unique_adjunct<'a>(
    observations: &'a [ScriptFileObservation],
    script_name: &str,
    extension: &str,
    diagnostics: &mut Vec<FeedbackScriptDiagnostic>,
) -> Option<&'a ScriptFileObservation> {
    if observations.len() > 1 {
        push_script_diagnostics(
            observations.iter(),
            "RCCE-SCRIPT-ADJUNCT-CASE-COLLISION",
            script_name,
            format!(
                "{script_name} has multiple {extension} artifact paths that differ only by case"
            ),
            diagnostics,
        );
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

impl FeedbackObservationIndex {
    fn from_catalogs(
        actor_catalog: &FeedbackActorCatalog,
        zone_catalog: &FeedbackZoneCatalog,
        script_catalog: &FeedbackScriptCatalog,
    ) -> Self {
        let actor_issues = actor_catalog.diagnostics.len();
        let zone_observations = zone_catalog.diagnostics.len();
        let script_observations = script_catalog.diagnostics.len();
        let mut observations =
            Vec::with_capacity(actor_issues + zone_observations + script_observations);

        observations.extend(actor_catalog.diagnostics.iter().map(|diagnostic| {
            FeedbackObservation {
                evidence: FeedbackObservationEvidence::ConsensusDiagnostic,
                code: diagnostic.code,
                raw_identity: format!("Actor #{}", diagnostic.actor_id),
                message: diagnostic.message.clone(),
                target: FeedbackFocusTarget::Actor {
                    actor_id: diagnostic.actor_id,
                },
            }
        }));
        observations.extend(zone_catalog.diagnostics.iter().map(|diagnostic| {
            FeedbackObservation {
                evidence: FeedbackObservationEvidence::FilenamePairing,
                code: diagnostic.code,
                raw_identity: format!("Zone {}", diagnostic.zone_name),
                message: diagnostic.message.clone(),
                target: FeedbackFocusTarget::Zone {
                    name: diagnostic.zone_name.clone(),
                },
            }
        }));
        observations.extend(script_catalog.diagnostics.iter().map(|diagnostic| {
            FeedbackObservation {
                evidence: FeedbackObservationEvidence::ScriptInventory,
                code: diagnostic.code,
                raw_identity: diagnostic.path.clone(),
                message: diagnostic.message.clone(),
                target: FeedbackFocusTarget::File {
                    lens: Lens::Scripts,
                    path: diagnostic.path.clone(),
                },
            }
        }));

        Self {
            actor_evidence: actor_catalog.evidence,
            actor_issues,
            zone_observations,
            script_observations,
            observations,
        }
    }
}

impl FeedbackAssetCatalog {
    fn from_actor_catalog(actor_catalog: &FeedbackActorCatalog) -> Self {
        let mut actors_by_mesh = BTreeMap::<u16, Vec<&FeedbackActor>>::new();
        for actor in &actor_catalog.actors {
            if let Some(mesh_id) = actor.base_mesh {
                actors_by_mesh.entry(mesh_id).or_default().push(actor);
            }
        }

        let meshes = actors_by_mesh
            .into_iter()
            .map(|(mesh_id, actors)| {
                let first_status = actors
                    .first()
                    .map_or(FeedbackMediaStatus::Provisional, |actor| actor.media_status);
                let media_status = if actors
                    .iter()
                    .all(|actor| actor.media_status == first_status)
                {
                    first_status
                } else {
                    FeedbackMediaStatus::Provisional
                };
                let first_path = actors.first().and_then(|actor| actor.physical_path.clone());
                let physical_path = (actor_catalog.evidence == FeedbackEvidence::Consensus
                    && actors.iter().all(|actor| actor.physical_path == first_path))
                .then_some(first_path)
                .flatten();
                let mut actors = actors
                    .into_iter()
                    .map(|actor| FeedbackActorReference {
                        actor_id: actor.actor_id,
                        race: actor.race.clone(),
                        race_is_lossy: actor.race_is_lossy,
                    })
                    .collect::<Vec<_>>();
                actors.sort_by_key(|actor| actor.actor_id);
                FeedbackActorMesh {
                    mesh_id,
                    media_status,
                    physical_path,
                    actors,
                }
            })
            .collect();

        Self {
            evidence: actor_catalog.evidence,
            meshes,
            unavailable_reason: actor_catalog.unavailable_reason.clone(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FeedbackProject {
    pub data_root: PathBuf,
    pub shape: String,
    pub unavailable: usize,
    actor_catalog: FeedbackActorCatalog,
    asset_catalog: FeedbackAssetCatalog,
    zone_catalog: FeedbackZoneCatalog,
    script_catalog: FeedbackScriptCatalog,
    vault_catalog: FeedbackVaultCatalog,
    observation_index: FeedbackObservationIndex,
    find_candidates: Vec<FeedbackFindCandidate>,
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
                state_classes: classification.classes.clone(),
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
        let vault_catalog = FeedbackVaultCatalog::from_entries(&by_lens[Lens::Vault.index()]);
        let asset_catalog = FeedbackAssetCatalog::from_actor_catalog(&actor_catalog);
        let observation_index =
            FeedbackObservationIndex::from_catalogs(&actor_catalog, &zone_catalog, &script_catalog);
        let find_candidates = build_find_candidates(
            &by_lens,
            &actor_catalog,
            &asset_catalog,
            &zone_catalog,
            &script_catalog,
        );
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
            asset_catalog,
            zone_catalog,
            script_catalog,
            vault_catalog,
            observation_index,
            find_candidates,
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
    pub const fn asset_catalog(&self) -> &FeedbackAssetCatalog {
        &self.asset_catalog
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
    pub const fn vault_catalog(&self) -> &FeedbackVaultCatalog {
        &self.vault_catalog
    }

    #[must_use]
    pub const fn observation_index(&self) -> &FeedbackObservationIndex {
        &self.observation_index
    }

    #[must_use]
    pub fn unclassified_files(&self) -> usize {
        self.unclassified_files
    }

    pub fn all_entries(&self) -> impl Iterator<Item = &FeedbackEntry> {
        self.by_lens.iter().flat_map(|entries| entries.iter())
    }

    #[must_use]
    pub fn find_candidate_count(&self) -> usize {
        self.find_candidates.len()
    }

    #[must_use]
    pub fn find_anywhere(&self, query: &str) -> Vec<FeedbackFindResult> {
        search_find_candidates(&self.find_candidates, query)
    }

    #[must_use]
    pub fn contains_find_target(&self, target: &FeedbackFindTarget) -> bool {
        self.contains_focus_target(target)
    }

    #[must_use]
    pub fn contains_focus_target(&self, target: &FeedbackFocusTarget) -> bool {
        self.find_candidates
            .iter()
            .any(|candidate| candidate.result.target == *target)
    }
}

fn accepted_entries_by_path(project: &FeedbackProject) -> BTreeMap<&str, (Lens, &FeedbackEntry)> {
    let mut by_path = BTreeMap::new();
    for lens in Lens::ALL {
        for entry in project.entries(lens) {
            by_path.insert(entry.path.as_str(), (lens, entry));
        }
    }
    by_path
}

fn search_find_candidates(
    candidates: &[FeedbackFindCandidate],
    query: &str,
) -> Vec<FeedbackFindResult> {
    let query = normalize_find_match(query.trim());
    if query.is_empty() {
        return Vec::new();
    }

    let mut matches = candidates
        .iter()
        .filter_map(|candidate| find_rank(candidate, &query).map(|rank| (rank, &candidate.result)))
        .collect::<Vec<_>>();
    matches.sort_by(|left, right| left.0.cmp(&right.0));
    matches
        .into_iter()
        .map(|(_, result)| result.clone())
        .collect()
}

fn build_find_candidates(
    by_lens: &[Vec<FeedbackEntry>; 5],
    actor_catalog: &FeedbackActorCatalog,
    asset_catalog: &FeedbackAssetCatalog,
    zone_catalog: &FeedbackZoneCatalog,
    script_catalog: &FeedbackScriptCatalog,
) -> Vec<FeedbackFindCandidate> {
    let evidence = evidence_word(actor_catalog.evidence);
    let mut candidates = Vec::new();

    for actor in &actor_catalog.actors {
        let raw = format!("Actor #{}", actor.actor_id);
        let display_note = if actor.race_is_lossy {
            "lossy legacy display"
        } else {
            "legacy display"
        };
        let target = FeedbackFindTarget::Actor {
            actor_id: actor.actor_id,
        };
        candidates.push(FeedbackFindCandidate {
            result: FeedbackFindResult {
                target: target.clone(),
                label: format!("{raw} · {}", actor.race),
                detail: format!("{evidence} raw actor identity · {display_note}"),
            },
            fields: normalize_find_fields([actor.actor_id.to_string(), raw, actor.race.clone()]),
            target_kind: find_target_kind(&target),
            target_identity: find_target_identity(&target),
        });
    }

    for mesh in &asset_catalog.meshes {
        let raw = format!("Mesh #{}", mesh.mesh_id);
        let target = FeedbackFindTarget::Mesh {
            mesh_id: mesh.mesh_id,
        };
        candidates.push(FeedbackFindCandidate {
            result: FeedbackFindResult {
                target: target.clone(),
                label: raw.clone(),
                detail: format!(
                    "{} actor-referenced raw base-mesh identity · {} observed backlink{}",
                    evidence_word(asset_catalog.evidence),
                    mesh.actors.len(),
                    if mesh.actors.len() == 1 { "" } else { "s" }
                ),
            },
            fields: normalize_find_fields([mesh.mesh_id.to_string(), raw]),
            target_kind: find_target_kind(&target),
            target_identity: find_target_identity(&target),
        });
    }

    for zone in &zone_catalog.zones {
        let target = FeedbackFindTarget::Zone {
            name: zone.name.clone(),
        };
        candidates.push(FeedbackFindCandidate {
            result: FeedbackFindResult {
                target: target.clone(),
                label: zone.name.clone(),
                detail: "filename-derived zone identity · inventory observation".to_owned(),
            },
            fields: normalize_find_fields([zone.name.clone(), format!("{}.dat", zone.name)]),
            target_kind: find_target_kind(&target),
            target_identity: find_target_identity(&target),
        });
    }

    for script in &script_catalog.scripts {
        let target = FeedbackFindTarget::Script {
            source_path: script.source_path.clone(),
        };
        candidates.push(FeedbackFindCandidate {
            result: FeedbackFindResult {
                target: target.clone(),
                label: script.name.clone(),
                detail: format!("active .rsl identity · {}", script.source_path),
            },
            fields: normalize_find_fields([
                script.source_path.clone(),
                script.name.clone(),
                format!("{}.rsl", script.name),
            ]),
            target_kind: find_target_kind(&target),
            target_identity: find_target_identity(&target),
        });
    }

    for lens in Lens::ALL {
        for entry in &by_lens[lens.index()] {
            let target = FeedbackFindTarget::File {
                lens,
                path: entry.path.clone(),
            };
            candidates.push(FeedbackFindCandidate {
                result: FeedbackFindResult {
                    target: target.clone(),
                    label: entry.path.clone(),
                    detail: format!("accepted inventory file · {} lens", lens.label()),
                },
                fields: normalize_find_fields([entry.path.clone()]),
                target_kind: find_target_kind(&target),
                target_identity: find_target_identity(&target),
            });
        }
    }

    candidates
}

fn normalize_find_fields<const N: usize>(fields: [String; N]) -> Vec<String> {
    fields
        .into_iter()
        .map(|field| normalize_find_match(&field))
        .collect()
}

fn normalize_find_match(value: &str) -> String {
    value.to_lowercase()
}

const fn evidence_word(evidence: FeedbackEvidence) -> &'static str {
    match evidence {
        FeedbackEvidence::Consensus => "consensus",
        FeedbackEvidence::Provisional => "provisional",
        FeedbackEvidence::Unavailable => "unavailable",
    }
}

#[derive(Debug, Clone, PartialEq, Eq, PartialOrd, Ord)]
struct FeedbackFindRank<'a> {
    match_kind: u8,
    match_metric: usize,
    field_index: usize,
    target_kind: u8,
    target_identity: &'a [u8],
}

fn find_rank<'a>(
    candidate: &'a FeedbackFindCandidate,
    query: &str,
) -> Option<FeedbackFindRank<'a>> {
    candidate
        .fields
        .iter()
        .enumerate()
        .filter_map(|(field_index, field)| {
            let (match_kind, match_metric) = if field == query {
                (0, 0)
            } else if field.starts_with(query) {
                (1, field.chars().count())
            } else {
                let position = field.find(query)?;
                (2, field[..position].chars().count())
            };
            Some(FeedbackFindRank {
                match_kind,
                match_metric,
                field_index,
                target_kind: candidate.target_kind,
                target_identity: &candidate.target_identity,
            })
        })
        .min()
}

const fn find_target_kind(target: &FeedbackFindTarget) -> u8 {
    match target {
        FeedbackFindTarget::Actor { .. } => 0,
        FeedbackFindTarget::Mesh { .. } => 1,
        FeedbackFindTarget::Zone { .. } => 2,
        FeedbackFindTarget::Script { .. } => 3,
        FeedbackFindTarget::File { .. } => 4,
    }
}

fn find_target_identity(target: &FeedbackFindTarget) -> Vec<u8> {
    match target {
        FeedbackFindTarget::Actor { actor_id } => actor_id.to_be_bytes().to_vec(),
        FeedbackFindTarget::Mesh { mesh_id } => mesh_id.to_be_bytes().to_vec(),
        FeedbackFindTarget::Zone { name } => name.as_bytes().to_vec(),
        FeedbackFindTarget::Script { source_path } => source_path.as_bytes().to_vec(),
        FeedbackFindTarget::File { path, .. } => path.as_bytes().to_vec(),
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

#[cfg(test)]
mod tests {
    use super::{
        find_target_identity, find_target_kind, normalize_find_fields, search_find_candidates,
        FeedbackActor, FeedbackActorCatalog, FeedbackActorCount, FeedbackAssetCatalog,
        FeedbackEvidence, FeedbackFindCandidate, FeedbackFindResult, FeedbackFindTarget,
        FeedbackFocusTarget, FeedbackMediaStatus, FeedbackReturnTrail, Lens,
        FEEDBACK_RETURN_TRAIL_CAPACITY,
    };

    #[test]
    fn return_trail_is_bounded_lifo_and_evicts_the_oldest_focus() {
        let mut trail = FeedbackReturnTrail::default();
        for actor_id in 0..=FEEDBACK_RETURN_TRAIL_CAPACITY as u16 {
            trail.push(FeedbackFocusTarget::Actor { actor_id });
        }

        assert_eq!(trail.len(), FEEDBACK_RETURN_TRAIL_CAPACITY);
        assert_eq!(
            trail.pop_resolved(None, |_| true),
            Some(FeedbackFocusTarget::Actor {
                actor_id: FEEDBACK_RETURN_TRAIL_CAPACITY as u16,
            })
        );
        while trail.len() > 1 {
            let _ = trail.pop_resolved(None, |_| true);
        }
        assert_eq!(
            trail.pop_resolved(None, |_| true),
            Some(FeedbackFocusTarget::Actor { actor_id: 1 })
        );
        assert!(trail.is_empty());
    }

    #[test]
    fn return_trail_skips_stale_current_and_duplicate_origins() {
        let actor = FeedbackFocusTarget::Actor { actor_id: 4 };
        let mesh = FeedbackFocusTarget::Mesh { mesh_id: 83 };
        let stale = FeedbackFocusTarget::Zone {
            name: "Removed".to_owned(),
        };
        let mut trail = FeedbackReturnTrail::default();
        trail.push(actor.clone());
        trail.push(actor.clone());
        trail.push(mesh.clone());
        trail.push(stale.clone());

        assert_eq!(trail.len(), 3);
        assert_eq!(
            trail.pop_resolved(Some(&mesh), |target| target != &stale),
            Some(actor)
        );
        assert!(trail.is_empty());
    }

    #[test]
    fn return_trail_reconciliation_preserves_surviving_order() {
        let actor = FeedbackFocusTarget::Actor { actor_id: 4 };
        let mesh = FeedbackFocusTarget::Mesh { mesh_id: 83 };
        let zone = FeedbackFocusTarget::Zone {
            name: "Start".to_owned(),
        };
        let mut trail = FeedbackReturnTrail::default();
        trail.push(actor.clone());
        trail.push(mesh);
        trail.push(zone.clone());

        assert_eq!(trail.retain_resolved(|target| target != &zone), 1);
        assert_eq!(
            trail.pop_resolved(None, |_| true),
            Some(FeedbackFocusTarget::Mesh { mesh_id: 83 })
        );
        assert_eq!(trail.pop_resolved(None, |_| true), Some(actor));
        assert!(trail.is_empty());
    }

    #[test]
    fn asset_catalog_groups_shared_meshes_with_ordered_actor_backlinks() {
        let actor_catalog = FeedbackActorCatalog {
            evidence: FeedbackEvidence::Consensus,
            count: FeedbackActorCount::Agreed(2),
            actors: vec![
                FeedbackActor {
                    actor_id: 9,
                    race: "Second".to_owned(),
                    race_is_lossy: false,
                    base_mesh: Some(7),
                    media_status: FeedbackMediaStatus::Present,
                    physical_path: Some("Data/Meshes/Shared.b3d".to_owned()),
                },
                FeedbackActor {
                    actor_id: 3,
                    race: "First".to_owned(),
                    race_is_lossy: false,
                    base_mesh: Some(7),
                    media_status: FeedbackMediaStatus::Present,
                    physical_path: Some("Data/Meshes/Shared.b3d".to_owned()),
                },
            ],
            diagnostics: Vec::new(),
            unavailable_reason: None,
        };

        let assets = FeedbackAssetCatalog::from_actor_catalog(&actor_catalog);

        assert_eq!(assets.meshes.len(), 1);
        assert_eq!(assets.meshes[0].mesh_id, 7);
        assert_eq!(
            assets.meshes[0]
                .actors
                .iter()
                .map(|actor| actor.actor_id)
                .collect::<Vec<_>>(),
            vec![3, 9]
        );
    }

    #[test]
    fn asset_catalog_preserves_unavailable_state_without_relationships() {
        let actor_catalog = FeedbackActorCatalog::unavailable(
            "Actor catalog unavailable for this snapshot".to_owned(),
        );

        let assets = FeedbackAssetCatalog::from_actor_catalog(&actor_catalog);

        assert_eq!(assets.evidence, FeedbackEvidence::Unavailable);
        assert!(assets.meshes.is_empty());
        assert_eq!(
            assets.unavailable_reason.as_deref(),
            Some("Actor catalog unavailable for this snapshot")
        );
    }

    #[test]
    fn broad_find_query_returns_every_candidate_in_stable_raw_identity_order() {
        let candidates = (0..12_000)
            .map(|index| {
                let path = format!("Data/Broad/File{index:05}.dat");
                let target = FeedbackFindTarget::File {
                    lens: Lens::Records,
                    path: path.clone(),
                };
                FeedbackFindCandidate {
                    result: FeedbackFindResult {
                        target: target.clone(),
                        label: path.clone(),
                        detail: "accepted inventory file · Records lens".to_owned(),
                    },
                    fields: normalize_find_fields([path]),
                    target_kind: find_target_kind(&target),
                    target_identity: find_target_identity(&target),
                }
            })
            .collect::<Vec<_>>();

        let results = search_find_candidates(&candidates, " DATA/BROAD/ ");

        assert_eq!(results.len(), 12_000);
        assert_eq!(
            results.first().map(|result| &result.target),
            Some(&FeedbackFindTarget::File {
                lens: Lens::Records,
                path: "Data/Broad/File00000.dat".to_owned(),
            })
        );
        assert_eq!(
            results.last().map(|result| &result.target),
            Some(&FeedbackFindTarget::File {
                lens: Lens::Records,
                path: "Data/Broad/File11999.dat".to_owned(),
            })
        );
    }
}
