/// Version of the executable project-format classification table.
pub const PROJECT_FORMAT_RULES_VERSION: u32 = 1;

/// Stable matrix census. Some rows describe embedded, absent, external, or
/// bundle-local state and therefore intentionally have no project-path rule.
pub const MATRIX_FAMILY_IDS: [&str; 82] = [
    "PF-CAN-001",
    "PF-CAN-002",
    "PF-CAN-003",
    "PF-CAN-004",
    "PF-CAN-005",
    "PF-CAN-006",
    "PF-CAN-007",
    "PF-CAN-008",
    "PF-CAN-009",
    "PF-CAN-010",
    "PF-CAN-011",
    "PF-CFG-001",
    "PF-CFG-002",
    "PF-CFG-003",
    "PF-CFG-004",
    "PF-CFG-005",
    "PF-CFG-006",
    "PF-CFG-007",
    "PF-CFG-008",
    "PF-CFG-009",
    "PF-CFG-010",
    "PF-CFG-011",
    "PF-CFG-012",
    "PF-CFG-013",
    "PF-CFG-014",
    "PF-MED-001",
    "PF-MED-002",
    "PF-MED-003",
    "PF-MED-004",
    "PF-MED-005",
    "PF-MED-006",
    "PF-MED-007",
    "PF-MED-008",
    "PF-MED-009",
    "PF-WLD-001",
    "PF-WLD-002",
    "PF-WLD-003",
    "PF-WLD-004",
    "PF-WLD-005",
    "PF-SCR-001",
    "PF-SCR-002",
    "PF-SCR-003",
    "PF-SCR-004",
    "PF-SCR-005",
    "PF-ADM-001",
    "PF-ADM-002",
    "PF-ADM-003",
    "PF-SPC-001",
    "PF-SPC-002",
    "PF-SPC-003",
    "PF-SPC-004",
    "PF-SPC-005",
    "PF-SPC-006",
    "PF-SPC-007",
    "PF-SPC-008",
    "PF-SPC-009",
    "PF-BND-001",
    "PF-BND-002",
    "PF-BND-003",
    "PF-BND-004",
    "PF-BND-005",
    "PF-EDM-001",
    "PF-EDM-002",
    "PF-EDM-003",
    "PF-EDM-004",
    "PF-EDM-005",
    "PF-EDM-006",
    "PF-DYN-001",
    "PF-DYN-002",
    "PF-DYN-003",
    "PF-DYN-004",
    "PF-DYN-005",
    "PF-DYN-006",
    "PF-GEN-001",
    "PF-GEN-002",
    "PF-GEN-003",
    "PF-UNK-001",
    "PF-UNK-002",
    "PF-UNK-003",
    "PF-UNK-004",
    "PF-UNK-005",
    "PF-UNK-006",
];

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MatrixApplicability {
    ProjectPathRule,
    EmbeddedOrAbsent,
    ExternalOrBundleLocal,
}

#[must_use]
pub fn matrix_applicability(id: &str) -> Option<MatrixApplicability> {
    if !MATRIX_FAMILY_IDS.contains(&id) {
        return None;
    }
    Some(match id {
        "PF-WLD-003" | "PF-SPC-008" | "PF-SPC-009" => MatrixApplicability::EmbeddedOrAbsent,
        "PF-ADM-002" | "PF-BND-001" | "PF-BND-002" | "PF-BND-003" | "PF-BND-004" | "PF-BND-005"
        | "PF-EDM-004" => MatrixApplicability::ExternalOrBundleLocal,
        _ => MatrixApplicability::ProjectPathRule,
    })
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum StateClass {
    AuthoringSource,
    EditorMetadata,
    DynamicPrivate,
    Secret,
    PublicClient,
    ServerConfig,
    Unknown,
}

impl Ord for StateClass {
    fn cmp(&self, other: &Self) -> std::cmp::Ordering {
        canonical_class_rank(*self).cmp(&canonical_class_rank(*other))
    }
}

impl PartialOrd for StateClass {
    fn partial_cmp(&self, other: &Self) -> Option<std::cmp::Ordering> {
        Some(self.cmp(other))
    }
}

const fn canonical_class_rank(class: StateClass) -> u8 {
    match class {
        StateClass::PublicClient => 0,
        StateClass::ServerConfig => 1,
        StateClass::Secret => 2,
        StateClass::DynamicPrivate => 3,
        StateClass::EditorMetadata => 4,
        StateClass::AuthoringSource => 5,
        StateClass::Unknown => 6,
    }
}

#[derive(Debug, Clone, PartialEq, Eq, PartialOrd, Ord)]
pub struct ConstraintEvidence {
    pub source: &'static str,
    pub evidence: &'static str,
    pub class: Option<StateClass>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CompatibilityLevel {
    InventoryOnly,
    Read,
    Unknown,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Classification {
    pub family: Option<&'static str>,
    pub primary: StateClass,
    /// Compatibility alias for the accepted primary class.
    pub state: StateClass,
    pub classes: Vec<StateClass>,
    pub compatibility: CompatibilityLevel,
    pub recovery_of: Option<String>,
    pub conflicted: bool,
    pub authoring_input: bool,
    pub constraints: Vec<ConstraintEvidence>,
}

#[derive(Clone, Copy)]
enum Pattern {
    Exact(&'static str),
    Prefix(&'static str),
    ScopedSuffix {
        prefix: &'static str,
        suffix: &'static str,
    },
}

#[derive(Clone, Copy)]
struct Rule {
    id: &'static str,
    pattern: Pattern,
    specificity: u16,
    state: StateClass,
    compatibility: CompatibilityLevel,
    authoring_input: bool,
}

const RULES: &[Rule] = &[
    Rule {
        id: "PF-CAN-001",
        pattern: Pattern::Exact("Data/Server Data/Actors.dat"),
        specificity: 1000,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::Read,
        authoring_input: true,
    },
    Rule {
        id: "PF-CAN-002",
        pattern: Pattern::Exact("Data/Server Data/Items.dat"),
        specificity: 1000,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::Read,
        authoring_input: true,
    },
    Rule {
        id: "PF-CAN-003",
        pattern: Pattern::Exact("Data/Server Data/Spells.dat"),
        specificity: 1000,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: true,
    },
    Rule {
        id: "PF-CAN-004",
        pattern: Pattern::Exact("Data/Server Data/Projectiles.dat"),
        specificity: 1000,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::Read,
        authoring_input: true,
    },
    Rule {
        id: "PF-CAN-005",
        pattern: Pattern::Exact("Data/Game Data/Animations.dat"),
        specificity: 1000,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::Read,
        authoring_input: true,
    },
    Rule {
        id: "PF-CAN-006",
        pattern: Pattern::Exact("Data/Server Data/Factions.dat"),
        specificity: 1000,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::Read,
        authoring_input: true,
    },
    Rule {
        id: "PF-CAN-007",
        pattern: Pattern::Exact("Data/Server Data/Attributes.dat"),
        specificity: 1000,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::Read,
        authoring_input: true,
    },
    Rule {
        id: "PF-CAN-008",
        pattern: Pattern::Exact("Data/Server Data/Damage.dat"),
        specificity: 1000,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::Read,
        authoring_input: true,
    },
    Rule {
        id: "PF-CAN-009",
        pattern: Pattern::Exact("Data/Server Data/Environment.dat"),
        specificity: 1000,
        state: StateClass::ServerConfig,
        compatibility: CompatibilityLevel::Read,
        authoring_input: false,
    },
    Rule {
        id: "PF-CAN-010",
        pattern: Pattern::Exact("Data/Game Data/Suns.dat"),
        specificity: 1000,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::Read,
        authoring_input: true,
    },
    Rule {
        id: "PF-CAN-011",
        pattern: Pattern::Exact("Data/Game Data/Interface.dat"),
        specificity: 1000,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::Read,
        authoring_input: true,
    },
    Rule {
        id: "PF-CFG-001",
        pattern: Pattern::Exact("Data/Game Data/Misc.dat"),
        specificity: 1000,
        state: StateClass::PublicClient,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: true,
    },
    Rule {
        id: "PF-CFG-002",
        pattern: Pattern::Exact("Data/Game Data/Hosts.dat"),
        specificity: 1000,
        state: StateClass::PublicClient,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-CFG-003",
        pattern: Pattern::Exact("Data/Game Data/Other.dat"),
        specificity: 1000,
        state: StateClass::PublicClient,
        compatibility: CompatibilityLevel::Read,
        authoring_input: false,
    },
    Rule {
        id: "PF-CFG-004",
        pattern: Pattern::Exact("Data/Server Data/Misc.dat"),
        specificity: 1000,
        state: StateClass::ServerConfig,
        compatibility: CompatibilityLevel::Read,
        authoring_input: false,
    },
    Rule {
        id: "PF-CFG-005",
        pattern: Pattern::Exact("Data/Game Data/Money.dat"),
        specificity: 1000,
        state: StateClass::PublicClient,
        compatibility: CompatibilityLevel::Read,
        authoring_input: true,
    },
    Rule {
        id: "PF-CFG-006",
        pattern: Pattern::Exact("Data/Game Data/Combat.dat"),
        specificity: 1000,
        state: StateClass::PublicClient,
        compatibility: CompatibilityLevel::Read,
        authoring_input: false,
    },
    Rule {
        id: "PF-CFG-007",
        pattern: Pattern::Exact("Data/Game Data/Fixed Attributes.dat"),
        specificity: 1000,
        state: StateClass::PublicClient,
        compatibility: CompatibilityLevel::Read,
        authoring_input: true,
    },
    Rule {
        id: "PF-CFG-008",
        pattern: Pattern::Exact("Data/Server Data/Fixed Attributes.dat"),
        specificity: 1000,
        state: StateClass::ServerConfig,
        compatibility: CompatibilityLevel::Read,
        authoring_input: true,
    },
    Rule {
        id: "PF-CFG-009",
        pattern: Pattern::Exact("Data/Game Data/Gubbins.dat"),
        specificity: 1000,
        state: StateClass::PublicClient,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: true,
    },
    Rule {
        id: "PF-CFG-010",
        pattern: Pattern::Exact("Data/Controls.dat"),
        specificity: 1000,
        state: StateClass::DynamicPrivate,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-CFG-011",
        pattern: Pattern::Exact("Data/Options.dat"),
        specificity: 1000,
        state: StateClass::DynamicPrivate,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-CFG-012",
        pattern: Pattern::Exact("Data/Last Username.dat"),
        specificity: 1000,
        state: StateClass::Secret,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-CFG-013",
        pattern: Pattern::Exact("Data/Game Data/RCTE.dat"),
        specificity: 1000,
        state: StateClass::PublicClient,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: true,
    },
    Rule {
        id: "PF-MED-001",
        pattern: Pattern::Exact("Data/Game Data/Meshes.dat"),
        specificity: 1000,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::Read,
        authoring_input: true,
    },
    Rule {
        id: "PF-MED-002",
        pattern: Pattern::Exact("Data/Game Data/Textures.dat"),
        specificity: 1000,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::Read,
        authoring_input: true,
    },
    Rule {
        id: "PF-MED-003",
        pattern: Pattern::Exact("Data/Game Data/Sounds.dat"),
        specificity: 1000,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: true,
    },
    Rule {
        id: "PF-MED-004",
        pattern: Pattern::Exact("Data/Game Data/Music.dat"),
        specificity: 1000,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::Read,
        authoring_input: true,
    },
    Rule {
        id: "PF-MED-005",
        pattern: Pattern::Prefix("Data/Meshes/"),
        specificity: 600,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: true,
    },
    Rule {
        id: "PF-MED-005",
        pattern: Pattern::Prefix("Data/Textures/"),
        specificity: 600,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: true,
    },
    Rule {
        id: "PF-MED-005",
        pattern: Pattern::Prefix("Data/Sounds/"),
        specificity: 600,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: true,
    },
    Rule {
        id: "PF-MED-005",
        pattern: Pattern::Prefix("Data/Music/"),
        specificity: 600,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: true,
    },
    Rule {
        id: "PF-MED-006",
        pattern: Pattern::ScopedSuffix {
            prefix: "Data/Emitter Configs/",
            suffix: ".rpc",
        },
        specificity: 800,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::Read,
        authoring_input: true,
    },
    Rule {
        id: "PF-MED-007",
        pattern: Pattern::Prefix("Data/UI/"),
        specificity: 600,
        state: StateClass::PublicClient,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: true,
    },
    Rule {
        id: "PF-MED-008",
        pattern: Pattern::Exact("Data/Game Data/Language.txt"),
        specificity: 1000,
        state: StateClass::PublicClient,
        compatibility: CompatibilityLevel::Read,
        authoring_input: false,
    },
    Rule {
        id: "PF-MED-008",
        pattern: Pattern::Exact("Data/Server Data/Language.txt"),
        specificity: 1000,
        state: StateClass::ServerConfig,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-MED-009",
        pattern: Pattern::Exact("Data/DefaultParticle.bmp"),
        specificity: 1000,
        state: StateClass::EditorMetadata,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-WLD-001",
        pattern: Pattern::ScopedSuffix {
            prefix: "Data/Areas/",
            suffix: ".dat",
        },
        specificity: 800,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::Read,
        authoring_input: true,
    },
    Rule {
        id: "PF-WLD-002",
        pattern: Pattern::ScopedSuffix {
            prefix: "Data/Server Data/Areas/",
            suffix: ".dat",
        },
        specificity: 800,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::Read,
        authoring_input: true,
    },
    Rule {
        id: "PF-WLD-004",
        pattern: Pattern::ScopedSuffix {
            prefix: "Data/Areas/Radar/",
            suffix: ".rdr",
        },
        specificity: 900,
        state: StateClass::DynamicPrivate,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-WLD-005",
        pattern: Pattern::ScopedSuffix {
            prefix: "Data/Server Data/Areas/Ownerships/",
            suffix: " Ownerships.dat",
        },
        specificity: 900,
        state: StateClass::DynamicPrivate,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-SCR-001",
        pattern: Pattern::ScopedSuffix {
            prefix: "Data/Server Data/Scripts/",
            suffix: ".rsl",
        },
        specificity: 800,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::Read,
        authoring_input: true,
    },
    Rule {
        id: "PF-SCR-002",
        pattern: Pattern::ScopedSuffix {
            prefix: "Data/Server Data/Scripts/",
            suffix: ".rcm",
        },
        specificity: 800,
        state: StateClass::ServerConfig,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-SCR-003",
        pattern: Pattern::Exact("Data/Server Data/Privileged Scripts.dat"),
        specificity: 1000,
        state: StateClass::ServerConfig,
        compatibility: CompatibilityLevel::Read,
        authoring_input: false,
    },
    Rule {
        id: "PF-SCR-004",
        pattern: Pattern::ScopedSuffix {
            prefix: "Data/Server Data/Scripts/",
            suffix: ".rcscript",
        },
        specificity: 800,
        state: StateClass::Unknown,
        compatibility: CompatibilityLevel::Unknown,
        authoring_input: false,
    },
    Rule {
        id: "PF-SCR-005",
        pattern: Pattern::ScopedSuffix {
            prefix: "Data/Server Data/Scripts/",
            suffix: ".rcs",
        },
        specificity: 800,
        state: StateClass::Unknown,
        compatibility: CompatibilityLevel::Unknown,
        authoring_input: false,
    },
    Rule {
        id: "PF-ADM-001",
        pattern: Pattern::Exact("Data/Server Data/MySQL.dat"),
        specificity: 1000,
        state: StateClass::Secret,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-ADM-003",
        pattern: Pattern::Exact("Data/Server Data/Names Filter.txt"),
        specificity: 1000,
        state: StateClass::ServerConfig,
        compatibility: CompatibilityLevel::Read,
        authoring_input: false,
    },
    Rule {
        id: "PF-DYN-001",
        pattern: Pattern::Exact("Data/Server Data/Accounts.dat"),
        specificity: 1000,
        state: StateClass::Secret,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-DYN-002",
        pattern: Pattern::Exact("Data/Server Data/Dropped Items.dat"),
        specificity: 1000,
        state: StateClass::DynamicPrivate,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-DYN-003",
        pattern: Pattern::Exact("Data/Server Data/Superglobals.dat"),
        specificity: 1000,
        state: StateClass::DynamicPrivate,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-DYN-004",
        pattern: Pattern::Prefix("Data/Server Data/Script Files/"),
        specificity: 700,
        state: StateClass::DynamicPrivate,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-DYN-005",
        pattern: Pattern::Prefix("Data/Logs/"),
        specificity: 700,
        state: StateClass::DynamicPrivate,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-EDM-001",
        pattern: Pattern::Exact("Data/Loom/recents.txt"),
        specificity: 1000,
        state: StateClass::EditorMetadata,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-EDM-002",
        pattern: Pattern::Exact("Data/Loom/atlas.txt"),
        specificity: 1000,
        state: StateClass::EditorMetadata,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-EDM-003",
        pattern: Pattern::Exact("Data/Loom/chrome.txt"),
        specificity: 1000,
        state: StateClass::EditorMetadata,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-SPC-001",
        pattern: Pattern::ScopedSuffix {
            prefix: "Data/Architect/Saves/",
            suffix: ".act",
        },
        specificity: 800,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: true,
    },
    Rule {
        id: "PF-SPC-004",
        pattern: Pattern::ScopedSuffix {
            prefix: "Data/RCTE/RCTE_SAVED/",
            suffix: ".rct",
        },
        specificity: 800,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: true,
    },
    Rule {
        id: "PF-SPC-005",
        pattern: Pattern::ScopedSuffix {
            prefix: "Data/RCTE/RCTE_SAVED/",
            suffix: ".mbr",
        },
        specificity: 800,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: true,
    },
    Rule {
        id: "PF-SPC-002",
        pattern: Pattern::ScopedSuffix {
            prefix: "Data/RCCAVES/",
            suffix: ".b3d",
        },
        specificity: 800,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: true,
    },
    Rule {
        id: "PF-SPC-003",
        pattern: Pattern::ScopedSuffix {
            prefix: "Data/RCCAVES/",
            suffix: ".LGT",
        },
        specificity: 800,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: true,
    },
    Rule {
        id: "PF-SPC-006",
        pattern: Pattern::Exact("Data/RCTE/Settings.dat"),
        specificity: 1000,
        state: StateClass::EditorMetadata,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-SPC-007",
        pattern: Pattern::ScopedSuffix {
            prefix: "Data/RCTREES/",
            suffix: ".fte",
        },
        specificity: 800,
        state: StateClass::AuthoringSource,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: true,
    },
    Rule {
        id: "PF-CFG-014",
        pattern: Pattern::Exact("Data/Server Data/TaskbarIcon.ico"),
        specificity: 1000,
        state: StateClass::ServerConfig,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-CFG-014",
        pattern: Pattern::Exact("Data/Server Data/GreenLight.bmp"),
        specificity: 1000,
        state: StateClass::ServerConfig,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-CFG-014",
        pattern: Pattern::Exact("Data/Server Data/RedLight.bmp"),
        specificity: 1000,
        state: StateClass::ServerConfig,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-EDM-005",
        pattern: Pattern::Prefix("Data/.rcce/"),
        specificity: 700,
        state: StateClass::EditorMetadata,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-EDM-006",
        pattern: Pattern::Prefix("Data/GUE/"),
        specificity: 400,
        state: StateClass::EditorMetadata,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-EDM-006",
        pattern: Pattern::Prefix("Data/Architect/"),
        specificity: 400,
        state: StateClass::EditorMetadata,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-EDM-006",
        pattern: Pattern::Prefix("Data/RCCAVES/"),
        specificity: 400,
        state: StateClass::EditorMetadata,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-EDM-006",
        pattern: Pattern::Prefix("Data/RCTE/"),
        specificity: 400,
        state: StateClass::EditorMetadata,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-EDM-006",
        pattern: Pattern::Prefix("Data/RCTREES/"),
        specificity: 400,
        state: StateClass::EditorMetadata,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-GEN-001",
        pattern: Pattern::Exact("Data/Server Data/Files.dat"),
        specificity: 1000,
        state: StateClass::ServerConfig,
        compatibility: CompatibilityLevel::Read,
        authoring_input: false,
    },
    Rule {
        id: "PF-GEN-002",
        pattern: Pattern::Exact("Data/Server Data/Items_debug.txt"),
        specificity: 1000,
        state: StateClass::EditorMetadata,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-GEN-002",
        pattern: Pattern::Exact("Data/Game Data/Animations_debug.txt"),
        specificity: 1000,
        state: StateClass::EditorMetadata,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-UNK-001",
        pattern: Pattern::Exact("Data/Created.dat"),
        specificity: 1000,
        state: StateClass::Unknown,
        compatibility: CompatibilityLevel::Unknown,
        authoring_input: false,
    },
    Rule {
        id: "PF-UNK-002",
        pattern: Pattern::Exact("Data/Game Data/Controls.dat"),
        specificity: 1000,
        state: StateClass::Unknown,
        compatibility: CompatibilityLevel::Unknown,
        authoring_input: false,
    },
    Rule {
        id: "PF-UNK-003",
        pattern: Pattern::Exact("Data/Game Data/Web.dat"),
        specificity: 1000,
        state: StateClass::Unknown,
        compatibility: CompatibilityLevel::Unknown,
        authoring_input: false,
    },
    Rule {
        id: "PF-UNK-003",
        pattern: Pattern::Exact("Data/Game Data/patchversion.dat"),
        specificity: 1000,
        state: StateClass::Unknown,
        compatibility: CompatibilityLevel::Unknown,
        authoring_input: false,
    },
    Rule {
        id: "PF-UNK-003",
        pattern: Pattern::Exact("Data/Version.dat"),
        specificity: 1000,
        state: StateClass::Unknown,
        compatibility: CompatibilityLevel::Unknown,
        authoring_input: false,
    },
    Rule {
        id: "PF-UNK-004",
        pattern: Pattern::Exact("Data/Game Data/xMeshes.dat"),
        specificity: 1000,
        state: StateClass::Unknown,
        compatibility: CompatibilityLevel::Unknown,
        authoring_input: false,
    },
    Rule {
        id: "PF-UNK-005",
        pattern: Pattern::Exact("Data/Game Data/EULA.txt"),
        specificity: 1000,
        state: StateClass::PublicClient,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-UNK-005",
        pattern: Pattern::Exact("Data/Game Data/Help.txt"),
        specificity: 1000,
        state: StateClass::PublicClient,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-UNK-005",
        pattern: Pattern::Exact("Data/language.xml"),
        specificity: 1000,
        state: StateClass::Unknown,
        compatibility: CompatibilityLevel::Unknown,
        authoring_input: false,
    },
    Rule {
        id: "PF-UNK-006",
        pattern: Pattern::Exact("Data/RCTREES/TEXTURES/Thumbs.db"),
        specificity: 1000,
        state: StateClass::Unknown,
        compatibility: CompatibilityLevel::Unknown,
        authoring_input: false,
    },
    Rule {
        id: "PF-GEN-003",
        pattern: Pattern::Prefix("Game/"),
        specificity: 500,
        state: StateClass::PublicClient,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
    Rule {
        id: "PF-GEN-003",
        pattern: Pattern::Prefix("Server/"),
        specificity: 500,
        state: StateClass::ServerConfig,
        compatibility: CompatibilityLevel::InventoryOnly,
        authoring_input: false,
    },
];

fn matches(pattern: Pattern, path: &str) -> bool {
    match pattern {
        Pattern::Exact(value) => path == value,
        Pattern::Prefix(value) => path.starts_with(value),
        Pattern::ScopedSuffix { prefix, suffix } => {
            path.starts_with(prefix) && path.ends_with(suffix)
        }
    }
}

fn recovery_final(path: &str) -> Option<&str> {
    path.strip_suffix(".bak.tmp")
        .or_else(|| path.strip_suffix(".tmp"))
        .or_else(|| path.strip_suffix(".bak"))
}

fn exact_winner<'a>(candidates: &'a [&Rule], specificity: u16) -> Option<&'a Rule> {
    let mut winners = candidates
        .iter()
        .copied()
        .filter(|rule| rule.specificity == specificity);
    let first = winners.next()?;
    winners.next().is_none().then_some(first)
}

#[must_use]
pub fn classify(path: &str) -> Classification {
    let recovery = recovery_final(path);
    let target = recovery.unwrap_or(path);
    let candidates = RULES
        .iter()
        .filter(|rule| matches(rule.pattern, target))
        .collect::<Vec<_>>();
    let mut constraints = candidates
        .iter()
        .map(|rule| ConstraintEvidence {
            source: rule.id,
            evidence: "docs/compat/project-format-matrix.md",
            class: None,
        })
        .collect::<Vec<_>>();
    if recovery.is_some() {
        constraints.push(ConstraintEvidence {
            source: "PF-DYN-006",
            evidence: "recovery-sibling suffix contract",
            class: None,
        });
    }
    constraints.sort();
    constraints.dedup();
    let Some(max) = candidates.iter().map(|rule| rule.specificity).max() else {
        return Classification {
            family: None,
            primary: StateClass::Unknown,
            state: StateClass::Unknown,
            classes: vec![StateClass::Unknown],
            compatibility: CompatibilityLevel::Unknown,
            recovery_of: recovery.map(str::to_owned),
            conflicted: false,
            authoring_input: false,
            constraints: vec![ConstraintEvidence {
                source: "UNCLASSIFIED-PATH-FALLBACK-V1",
                evidence: "project-format matrix unknown-path policy",
                class: Some(StateClass::Unknown),
            }],
        };
    };
    let Some(first) = exact_winner(&candidates, max) else {
        return Classification {
            family: None,
            primary: StateClass::Unknown,
            state: StateClass::Unknown,
            classes: vec![StateClass::Unknown],
            compatibility: CompatibilityLevel::Unknown,
            recovery_of: recovery.map(str::to_owned),
            conflicted: true,
            authoring_input: false,
            constraints,
        };
    };
    let mut classes = authoritative_classes(first.id, target, first.state);
    classes.sort_by_key(|class| canonical_class_rank(*class));
    classes.dedup();
    constraints.extend(classes.iter().copied().map(|class| ConstraintEvidence {
        source: first.id,
        evidence: "project-format matrix complete state-class set",
        class: Some(class),
    }));
    constraints.sort();
    constraints.dedup();
    Classification {
        family: Some(first.id),
        primary: first.state,
        state: first.state,
        classes,
        compatibility: first.compatibility,
        recovery_of: recovery.map(str::to_owned),
        conflicted: false,
        authoring_input: first.authoring_input,
        constraints,
    }
}

fn authoritative_classes(id: &str, path: &str, primary: StateClass) -> Vec<StateClass> {
    use StateClass::{
        AuthoringSource, DynamicPrivate, PublicClient, Secret, ServerConfig, Unknown,
    };
    let mixed: Option<&[StateClass]> = match id {
        "PF-CAN-009" => Some(&[ServerConfig, DynamicPrivate]),
        "PF-CAN-010" | "PF-CAN-011" | "PF-CFG-005" | "PF-CFG-007" | "PF-CFG-009" | "PF-CFG-013"
        | "PF-MED-001" | "PF-MED-002" | "PF-MED-003" | "PF-MED-004" | "PF-MED-005"
        | "PF-MED-006" | "PF-MED-007" | "PF-WLD-001" => Some(&[AuthoringSource, PublicClient]),
        "PF-CFG-001" => Some(&[PublicClient, AuthoringSource]),
        "PF-CFG-002" | "PF-MED-008" => Some(&[PublicClient, ServerConfig]),
        "PF-CFG-008" | "PF-WLD-002" | "PF-SCR-001" => Some(&[ServerConfig, AuthoringSource]),
        "PF-CFG-012" | "PF-DYN-001" => Some(&[Secret, DynamicPrivate]),
        "PF-ADM-001" => Some(&[Secret, ServerConfig]),
        "PF-DYN-004" => Some(&[DynamicPrivate, Unknown]),
        "PF-UNK-005" => Some(&[PublicClient, Unknown]),
        "PF-GEN-003" if path.starts_with("Server/") => Some(&[ServerConfig, DynamicPrivate]),
        _ => None,
    };
    mixed.map_or_else(|| vec![primary], <[StateClass]>::to_vec)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn registry_class_order() -> Vec<String> {
        let default = std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("../../../test-data/canaries/registry-v1.toml");
        let path = std::env::var_os("RCCE_P07_REGISTRY")
            .map(std::path::PathBuf::from)
            .unwrap_or(default);
        let registry = std::fs::read_to_string(&path).unwrap_or_else(|error| {
            panic!(
                "read authoritative P07 registry {}: {error}",
                path.display()
            )
        });
        let line = registry
            .lines()
            .find(|line| line.starts_with("state_class_order = ["))
            .expect("P07 registry declares state_class_order");
        line.split_once('[')
            .expect("state_class_order opening bracket")
            .1
            .rsplit_once(']')
            .expect("state_class_order closing bracket")
            .0
            .split(',')
            .map(|value| value.trim().trim_matches('"').to_owned())
            .collect()
    }

    fn class_name(class: StateClass) -> &'static str {
        match class {
            StateClass::PublicClient => "PublicClient",
            StateClass::ServerConfig => "ServerConfig",
            StateClass::Secret => "Secret",
            StateClass::DynamicPrivate => "DynamicPrivate",
            StateClass::EditorMetadata => "EditorMetadata",
            StateClass::AuthoringSource => "AuthoringSource",
            StateClass::Unknown => "Unknown",
        }
    }

    fn ordered_by_registry(mut classes: Vec<StateClass>, order: &[String]) -> Vec<StateClass> {
        classes.sort_by_key(|class| {
            order
                .iter()
                .position(|name| name == class_name(*class))
                .expect("every class appears in the P07 registry")
        });
        classes
    }

    #[test]
    fn exact_rules_beat_fallback_and_recovery_inherits_without_resolution() {
        let actor = classify("Data/Server Data/Actors.dat.bak");
        assert_eq!(actor.family, Some("PF-CAN-001"));
        assert_eq!(
            actor.recovery_of.as_deref(),
            Some("Data/Server Data/Actors.dat")
        );
        assert!(actor.authoring_input);
    }

    #[test]
    fn extension_and_neighbor_names_do_not_infer_a_family() {
        assert_eq!(classify("random/Actors.dat").state, StateClass::Unknown);
        assert_eq!(
            classify("Data/Server Data/Actors.dat.copy").state,
            StateClass::Unknown
        );
    }

    #[test]
    fn projections_are_descriptive_but_never_authoring_inputs() {
        for path in ["Game/Data/Actors.dat", "Server/Data/Actors.dat"] {
            let value = classify(path);
            assert!(value.family.is_some());
            assert!(!value.authoring_input);
        }
    }

    #[test]
    fn mixed_classes_are_canonical_and_recovery_inherits_all_constraints() {
        let registry_order = registry_class_order();
        let mut implementation_order = [
            StateClass::Unknown,
            StateClass::AuthoringSource,
            StateClass::PublicClient,
            StateClass::EditorMetadata,
            StateClass::Secret,
            StateClass::ServerConfig,
            StateClass::DynamicPrivate,
        ];
        implementation_order.sort_by_key(|class| canonical_class_rank(*class));
        assert_eq!(
            implementation_order.map(class_name).as_slice(),
            registry_order
        );
        let cases = [
            (
                "Data/Server Data/Environment.dat",
                "PF-CAN-009",
                StateClass::ServerConfig,
                vec![StateClass::DynamicPrivate, StateClass::ServerConfig],
            ),
            (
                "Data/Game Data/Suns.dat",
                "PF-CAN-010",
                StateClass::AuthoringSource,
                vec![StateClass::AuthoringSource, StateClass::PublicClient],
            ),
            (
                "Data/Game Data/Interface.dat",
                "PF-CAN-011",
                StateClass::AuthoringSource,
                vec![StateClass::AuthoringSource, StateClass::PublicClient],
            ),
            (
                "Data/Game Data/Misc.dat",
                "PF-CFG-001",
                StateClass::PublicClient,
                vec![StateClass::AuthoringSource, StateClass::PublicClient],
            ),
            (
                "Data/Game Data/Hosts.dat",
                "PF-CFG-002",
                StateClass::PublicClient,
                vec![StateClass::PublicClient, StateClass::ServerConfig],
            ),
            (
                "Data/Game Data/Money.dat",
                "PF-CFG-005",
                StateClass::PublicClient,
                vec![StateClass::AuthoringSource, StateClass::PublicClient],
            ),
            (
                "Data/Game Data/Fixed Attributes.dat",
                "PF-CFG-007",
                StateClass::PublicClient,
                vec![StateClass::AuthoringSource, StateClass::PublicClient],
            ),
            (
                "Data/Server Data/Fixed Attributes.dat",
                "PF-CFG-008",
                StateClass::ServerConfig,
                vec![StateClass::AuthoringSource, StateClass::ServerConfig],
            ),
            (
                "Data/Game Data/Gubbins.dat",
                "PF-CFG-009",
                StateClass::PublicClient,
                vec![StateClass::AuthoringSource, StateClass::PublicClient],
            ),
            (
                "Data/Last Username.dat",
                "PF-CFG-012",
                StateClass::Secret,
                vec![StateClass::DynamicPrivate, StateClass::Secret],
            ),
            (
                "Data/Game Data/RCTE.dat",
                "PF-CFG-013",
                StateClass::PublicClient,
                vec![StateClass::AuthoringSource, StateClass::PublicClient],
            ),
            (
                "Data/Game Data/Meshes.dat",
                "PF-MED-001",
                StateClass::AuthoringSource,
                vec![StateClass::AuthoringSource, StateClass::PublicClient],
            ),
            (
                "Data/Game Data/Textures.dat",
                "PF-MED-002",
                StateClass::AuthoringSource,
                vec![StateClass::AuthoringSource, StateClass::PublicClient],
            ),
            (
                "Data/Game Data/Sounds.dat",
                "PF-MED-003",
                StateClass::AuthoringSource,
                vec![StateClass::AuthoringSource, StateClass::PublicClient],
            ),
            (
                "Data/Game Data/Music.dat",
                "PF-MED-004",
                StateClass::AuthoringSource,
                vec![StateClass::AuthoringSource, StateClass::PublicClient],
            ),
            (
                "Data/Meshes/model.b3d",
                "PF-MED-005",
                StateClass::AuthoringSource,
                vec![StateClass::AuthoringSource, StateClass::PublicClient],
            ),
            (
                "Data/Emitter Configs/fire.rpc",
                "PF-MED-006",
                StateClass::AuthoringSource,
                vec![StateClass::AuthoringSource, StateClass::PublicClient],
            ),
            (
                "Data/UI/skin.bmp",
                "PF-MED-007",
                StateClass::PublicClient,
                vec![StateClass::AuthoringSource, StateClass::PublicClient],
            ),
            (
                "Data/Game Data/Language.txt",
                "PF-MED-008",
                StateClass::PublicClient,
                vec![StateClass::PublicClient, StateClass::ServerConfig],
            ),
            (
                "Data/Server Data/Language.txt",
                "PF-MED-008",
                StateClass::ServerConfig,
                vec![StateClass::PublicClient, StateClass::ServerConfig],
            ),
            (
                "Data/Areas/zone.dat",
                "PF-WLD-001",
                StateClass::AuthoringSource,
                vec![StateClass::AuthoringSource, StateClass::PublicClient],
            ),
            (
                "Data/Server Data/Areas/zone.dat",
                "PF-WLD-002",
                StateClass::AuthoringSource,
                vec![StateClass::AuthoringSource, StateClass::ServerConfig],
            ),
            (
                "Data/Server Data/Scripts/quest.rsl",
                "PF-SCR-001",
                StateClass::AuthoringSource,
                vec![StateClass::AuthoringSource, StateClass::ServerConfig],
            ),
            (
                "Data/Server Data/MySQL.dat",
                "PF-ADM-001",
                StateClass::Secret,
                vec![StateClass::Secret, StateClass::ServerConfig],
            ),
            (
                "Data/Server Data/Accounts.dat",
                "PF-DYN-001",
                StateClass::Secret,
                vec![StateClass::DynamicPrivate, StateClass::Secret],
            ),
            (
                "Data/Server Data/Script Files/custom.DATA",
                "PF-DYN-004",
                StateClass::DynamicPrivate,
                vec![StateClass::DynamicPrivate, StateClass::Unknown],
            ),
            (
                "Server/Data/Server Data/Accounts.dat",
                "PF-GEN-003",
                StateClass::ServerConfig,
                vec![StateClass::DynamicPrivate, StateClass::ServerConfig],
            ),
            (
                "Data/Game Data/EULA.txt",
                "PF-UNK-005",
                StateClass::PublicClient,
                vec![StateClass::PublicClient, StateClass::Unknown],
            ),
            (
                "Data/language.xml",
                "PF-UNK-005",
                StateClass::Unknown,
                vec![StateClass::PublicClient, StateClass::Unknown],
            ),
        ];
        let observed_families = cases
            .iter()
            .map(|(_, family, _, _)| *family)
            .collect::<std::collections::BTreeSet<_>>();
        let expected_families = [
            "PF-CAN-009",
            "PF-CAN-010",
            "PF-CAN-011",
            "PF-CFG-001",
            "PF-CFG-002",
            "PF-CFG-005",
            "PF-CFG-007",
            "PF-CFG-008",
            "PF-CFG-009",
            "PF-CFG-012",
            "PF-CFG-013",
            "PF-MED-001",
            "PF-MED-002",
            "PF-MED-003",
            "PF-MED-004",
            "PF-MED-005",
            "PF-MED-006",
            "PF-MED-007",
            "PF-MED-008",
            "PF-WLD-001",
            "PF-WLD-002",
            "PF-SCR-001",
            "PF-ADM-001",
            "PF-DYN-001",
            "PF-DYN-004",
            "PF-GEN-003",
            "PF-UNK-005",
        ]
        .into_iter()
        .collect::<std::collections::BTreeSet<_>>();
        assert_eq!(observed_families, expected_families);
        for (path, family, primary, expected_set) in &cases {
            let expected = ordered_by_registry(expected_set.clone(), &registry_order);
            let direct = classify(path);
            assert_eq!(direct.family, Some(*family), "{path}");
            assert_eq!(direct.primary, *primary, "{path}");
            assert_eq!(direct.classes, expected);
            for class in &direct.classes {
                assert!(
                    direct.constraints.iter().any(|item| {
                        item.source == *family && item.class.as_ref() == Some(class)
                    }),
                    "missing class evidence for {family} {class:?} at {path}"
                );
            }
            let recovered = classify(&format!("{path}.bak.tmp"));
            assert_eq!(recovered.classes, expected);
            assert!(recovered
                .constraints
                .iter()
                .any(|item| item.source == "PF-DYN-006"));
        }
    }

    #[test]
    fn equally_specific_matches_fail_even_when_the_same_rule_is_repeated() {
        let same = &RULES[0];
        assert!(exact_winner(&[same, same], same.specificity).is_none());
    }

    #[test]
    fn matrix_census_is_unique_and_every_project_path_family_has_executable_evidence() {
        let ids = MATRIX_FAMILY_IDS
            .iter()
            .copied()
            .collect::<std::collections::BTreeSet<_>>();
        assert_eq!(ids.len(), 82);
        let mut ruled = RULES
            .iter()
            .map(|rule| rule.id)
            .collect::<std::collections::BTreeSet<_>>();
        ruled.insert("PF-DYN-006");
        for id in MATRIX_FAMILY_IDS {
            if matrix_applicability(id) == Some(MatrixApplicability::ProjectPathRule) {
                assert!(
                    ruled.contains(id),
                    "missing executable/fallback evidence for {id}"
                );
            }
        }
    }

    #[test]
    fn fallback_and_requested_specialist_generated_and_residual_rules_are_explicit() {
        assert_eq!(
            classify("Data/Server Data/Files.dat").family,
            Some("PF-GEN-001")
        );
        assert_eq!(
            classify("Data/RCTE/Settings.dat").family,
            Some("PF-SPC-006")
        );
        assert_eq!(classify("Data/RCCAVES/cave.LGT").family, Some("PF-SPC-003"));
        assert_eq!(
            classify("Data/RCTREES/source.fte").family,
            Some("PF-SPC-007")
        );
        let fallback = classify("totally/ordinary.bin");
        assert_eq!(fallback.family, None);
        assert_eq!(
            fallback.constraints[0].source,
            "UNCLASSIFIED-PATH-FALLBACK-V1"
        );
    }
}
