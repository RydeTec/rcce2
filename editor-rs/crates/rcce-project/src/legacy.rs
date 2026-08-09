use crate::{InventoryFile, RootIdentity, SourceFingerprint};
use std::borrow::Cow;
use std::ops::Range;
use std::sync::Arc;

const MAX_STRING_DIAGNOSTICS: usize = 64;

/// Stable identity for the accepted bytes of one inventoried document.
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct DocumentIdentity {
    root: RootIdentity,
    path: String,
    source: SourceFingerprint,
}

impl DocumentIdentity {
    #[must_use]
    pub fn from_inventory(file: &InventoryFile) -> Self {
        Self {
            root: file.provenance.root,
            path: file.path.clone(),
            source: file.fingerprint,
        }
    }

    #[must_use]
    pub const fn root(&self) -> RootIdentity {
        self.root
    }

    /// Return the already-confined portable inventory spelling, not a host path.
    #[must_use]
    pub fn inventory_path(&self) -> &str {
        &self.path
    }

    /// Return the exact P03 fingerprint; this layer never computes another one.
    #[must_use]
    pub const fn source_fingerprint(&self) -> SourceFingerprint {
        self.source
    }
}

/// Half-open byte range within one immutable source document.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct ByteSpan {
    start: u64,
    end: u64,
}

impl ByteSpan {
    pub const fn checked(start: u64, length: u64) -> Result<Self, SpanError> {
        match start.checked_add(length) {
            Some(end) => Ok(Self { start, end }),
            None => Err(SpanError::ArithmeticOverflow),
        }
    }

    pub const fn from_bounds(start: u64, end: u64) -> Result<Self, SpanError> {
        if end < start {
            Err(SpanError::ReversedBounds)
        } else {
            Ok(Self { start, end })
        }
    }

    #[must_use]
    pub const fn start(self) -> u64 {
        self.start
    }

    #[must_use]
    pub const fn end(self) -> u64 {
        self.end
    }

    #[must_use]
    pub const fn length(self) -> u64 {
        self.end - self.start
    }

    pub fn checked_range(self, document_length: usize) -> Result<Range<usize>, SpanError> {
        let start = usize::try_from(self.start).map_err(|_| SpanError::OutOfBounds {
            end: self.end,
            document_length: document_length as u64,
        })?;
        let end = usize::try_from(self.end).map_err(|_| SpanError::OutOfBounds {
            end: self.end,
            document_length: document_length as u64,
        })?;
        if end > document_length {
            return Err(SpanError::OutOfBounds {
                end: self.end,
                document_length: document_length as u64,
            });
        }
        Ok(start..end)
    }

    fn byte_at(self, offset: usize) -> Self {
        let start = self.start + offset as u64;
        Self {
            start,
            end: start + 1,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SpanError {
    ArithmeticOverflow,
    ReversedBounds,
    OutOfBounds { end: u64, document_length: u64 },
}

/// Declared persisted representation, not a promise about display decoding.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LegacyEncoding {
    UnspecifiedBytes,
    Utf8,
}

/// Field constraints are diagnostic-only in the M1 read-only model.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct LegacyStringConstraints {
    pub encoding: LegacyEncoding,
    pub maximum_bytes: Option<u64>,
    pub diagnose_nul: bool,
    pub diagnose_control_bytes: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DiagnosticCode {
    LossyDisplayDecoding,
    DeclaredUtf8Violation,
    EmbeddedNul,
    ControlByte,
    ByteLimitExceeded,
    AdditionalDiagnosticsSuppressed,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DiagnosticSeverity {
    Information,
    Warning,
    Error,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EvidenceStatus {
    Observed,
}

/// Read-only evidence. M1 diagnostics intentionally carry no repair action.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Diagnostic {
    code: DiagnosticCode,
    severity: DiagnosticSeverity,
    span: ByteSpan,
    document: DocumentIdentity,
    evidence: EvidenceStatus,
}

impl Diagnostic {
    #[must_use]
    pub const fn code(&self) -> DiagnosticCode {
        self.code
    }

    #[must_use]
    pub const fn severity(&self) -> DiagnosticSeverity {
        self.severity
    }

    #[must_use]
    pub const fn span(&self) -> ByteSpan {
        self.span
    }

    #[must_use]
    pub const fn document(&self) -> &DocumentIdentity {
        &self.document
    }

    #[must_use]
    pub const fn evidence(&self) -> EvidenceStatus {
        self.evidence
    }
}

/// A display-only projection. It is deliberately a different type from raw identity.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LegacyDisplay<'a> {
    text: Cow<'a, str>,
    lossy: bool,
}

impl LegacyDisplay<'_> {
    #[must_use]
    pub fn text(&self) -> &str {
        &self.text
    }

    #[must_use]
    pub const fn is_lossy(&self) -> bool {
        self.lossy
    }
}

/// Byte-authoritative legacy string evidence.
///
/// This type intentionally has no `Display`, `AsRef<str>`, path conversion,
/// serialization, or mutation API. Display decoding is explicitly requested
/// through [`RawLegacyString::best_effort_display`].
///
/// ```compile_fail
/// fn requires_display<T: std::fmt::Display>() {}
/// requires_display::<rcce_project::RawLegacyString>();
/// ```
///
/// ```compile_fail
/// fn requires_text<T: AsRef<str>>() {}
/// requires_text::<rcce_project::RawLegacyString>();
/// ```
///
/// ```compile_fail
/// use std::path::Path;
/// fn requires_path<T: AsRef<Path>>() {}
/// requires_path::<rcce_project::RawLegacyString>();
/// ```
///
/// ```compile_fail
/// use std::path::PathBuf;
/// fn requires_path_conversion<T: Into<PathBuf>>() {}
/// requires_path_conversion::<rcce_project::RawLegacyString>();
/// ```
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RawLegacyString {
    document_bytes: Arc<[u8]>,
    document: DocumentIdentity,
    span: ByteSpan,
    range: Range<usize>,
    constraints: LegacyStringConstraints,
    diagnostics: Vec<Diagnostic>,
}

impl RawLegacyString {
    pub(crate) fn from_document(
        document_bytes: Arc<[u8]>,
        document: DocumentIdentity,
        span: ByteSpan,
        constraints: LegacyStringConstraints,
    ) -> Result<Self, SpanError> {
        let range = span.checked_range(document_bytes.len())?;
        let diagnostics =
            diagnose_string(&document_bytes[range.clone()], &document, span, constraints);
        Ok(Self {
            document_bytes,
            document,
            span,
            range,
            constraints,
            diagnostics,
        })
    }

    #[must_use]
    pub fn raw_bytes(&self) -> &[u8] {
        &self.document_bytes[self.range.clone()]
    }

    #[must_use]
    pub fn best_effort_display(&self) -> LegacyDisplay<'_> {
        let raw = self.raw_bytes();
        LegacyDisplay {
            text: String::from_utf8_lossy(raw),
            lossy: std::str::from_utf8(raw).is_err(),
        }
    }

    #[must_use]
    pub const fn document(&self) -> &DocumentIdentity {
        &self.document
    }

    #[must_use]
    pub const fn span(&self) -> ByteSpan {
        self.span
    }

    #[must_use]
    pub const fn constraints(&self) -> LegacyStringConstraints {
        self.constraints
    }

    #[must_use]
    pub fn diagnostics(&self) -> &[Diagnostic] {
        &self.diagnostics
    }
}

fn diagnose_string(
    bytes: &[u8],
    document: &DocumentIdentity,
    span: ByteSpan,
    constraints: LegacyStringConstraints,
) -> Vec<Diagnostic> {
    let mut diagnostics = Vec::new();
    let mut suppressed = false;
    {
        let mut add = |code, severity, affected_span| {
            if diagnostics.len() < MAX_STRING_DIAGNOSTICS - 1 {
                diagnostics.push(Diagnostic {
                    code,
                    severity,
                    span: affected_span,
                    document: document.clone(),
                    evidence: EvidenceStatus::Observed,
                });
            } else {
                suppressed = true;
            }
        };

        if let Some(maximum) = constraints.maximum_bytes {
            if bytes.len() as u64 > maximum {
                add(
                    DiagnosticCode::ByteLimitExceeded,
                    DiagnosticSeverity::Error,
                    span,
                );
            }
        }
        if let Err(error) = std::str::from_utf8(bytes) {
            let start = error.valid_up_to();
            let length = error.error_len().unwrap_or(bytes.len() - start).max(1);
            let invalid = ByteSpan::checked(span.start + start as u64, length as u64)
                .expect("validated field span contains its invalid UTF-8 sequence");
            add(
                DiagnosticCode::LossyDisplayDecoding,
                DiagnosticSeverity::Warning,
                invalid,
            );
            if constraints.encoding == LegacyEncoding::Utf8 {
                add(
                    DiagnosticCode::DeclaredUtf8Violation,
                    DiagnosticSeverity::Error,
                    invalid,
                );
            }
        }
        for (offset, byte) in bytes.iter().copied().enumerate() {
            if constraints.diagnose_nul && byte == 0 {
                add(
                    DiagnosticCode::EmbeddedNul,
                    DiagnosticSeverity::Warning,
                    span.byte_at(offset),
                );
            } else if constraints.diagnose_control_bytes && (byte < b' ' || byte == 0x7f) {
                add(
                    DiagnosticCode::ControlByte,
                    DiagnosticSeverity::Warning,
                    span.byte_at(offset),
                );
            }
        }
    }
    if suppressed {
        diagnostics.push(Diagnostic {
            code: DiagnosticCode::AdditionalDiagnosticsSuppressed,
            severity: DiagnosticSeverity::Warning,
            span,
            document: document.clone(),
            evidence: EvidenceStatus::Observed,
        });
    }
    diagnostics
}

/// Parser contract identity for deterministic provenance.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct ParserIdentity {
    name: &'static str,
    version: u32,
}

impl ParserIdentity {
    #[must_use]
    pub const fn new(name: &'static str, version: u32) -> Self {
        Self { name, version }
    }

    #[must_use]
    pub const fn name(self) -> &'static str {
        self.name
    }

    #[must_use]
    pub const fn version(self) -> u32 {
        self.version
    }
}

/// Parser provenance layered onto, but never replacing, the exact P03 record.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DocumentProvenance {
    inventory_file: InventoryFile,
    parser: ParserIdentity,
}

impl DocumentProvenance {
    #[must_use]
    pub const fn from_inventory(inventory_file: InventoryFile, parser: ParserIdentity) -> Self {
        Self {
            inventory_file,
            parser,
        }
    }

    #[must_use]
    pub const fn inventory_file(&self) -> &InventoryFile {
        &self.inventory_file
    }

    #[must_use]
    pub const fn parser(&self) -> ParserIdentity {
        self.parser
    }

    #[must_use]
    pub fn document_identity(&self) -> DocumentIdentity {
        DocumentIdentity::from_inventory(&self.inventory_file)
    }
}

/// Explicit absence of any writer, repair, or serialization authority in M1.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct NoWriteAuthority;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LegacyDocumentError {
    SourceSizeMismatch { inventory: u64, observed: u64 },
}

/// Minimal immutable read envelope. P05 will supply real parser values and fixtures.
///
/// Construction is deliberately unavailable outside this crate until a loader
/// can prove the byte-to-inventory binding:
///
/// ```compile_fail
/// use rcce_project::LegacyDocument;
/// use std::sync::Arc;
/// let _: LegacyDocument<()> = LegacyDocument::from_inventory_binding(
///     Arc::<[u8]>::from([]),
///     (),
///     todo!(),
/// ).unwrap();
/// ```
///
/// Its private fields also prevent external struct-literal construction:
///
/// ```compile_fail
/// use rcce_project::LegacyDocument;
/// let _ = LegacyDocument {
///     original_bytes: todo!(),
///     value: (),
///     provenance: todo!(),
///     diagnostics: Vec::new(),
///     write_authority: todo!(),
/// };
/// ```
///
/// There is no constructor accepting an independent diagnostic vector:
///
/// ```compile_fail
/// use rcce_project::LegacyDocument;
/// use std::sync::Arc;
/// let _: LegacyDocument<()> = LegacyDocument::new(
///     Arc::<[u8]>::from([]),
///     (),
///     todo!(),
///     Vec::new(),
/// ).unwrap();
/// ```
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LegacyDocument<T> {
    original_bytes: Arc<[u8]>,
    value: T,
    provenance: DocumentProvenance,
    diagnostics: Vec<Diagnostic>,
    write_authority: NoWriteAuthority,
}

impl<T> LegacyDocument<T> {
    /// Internal seam reserved for a loader that has already bound these bytes
    /// to the consumed inventory record. P04 exposes no public binding path.
    pub(crate) fn from_inventory_binding(
        original_bytes: Arc<[u8]>,
        value: T,
        provenance: DocumentProvenance,
    ) -> Result<Self, LegacyDocumentError> {
        let observed = original_bytes.len() as u64;
        let inventory = provenance.inventory_file.size;
        if observed != inventory {
            return Err(LegacyDocumentError::SourceSizeMismatch {
                inventory,
                observed,
            });
        }
        Ok(Self {
            original_bytes,
            value,
            provenance,
            diagnostics: Vec::new(),
            write_authority: NoWriteAuthority,
        })
    }

    #[must_use]
    pub fn original_bytes(&self) -> &[u8] {
        &self.original_bytes
    }

    #[must_use]
    pub const fn value(&self) -> &T {
        &self.value
    }

    #[must_use]
    pub const fn provenance(&self) -> &DocumentProvenance {
        &self.provenance
    }

    #[must_use]
    pub fn diagnostics(&self) -> &[Diagnostic] {
        &self.diagnostics
    }

    #[must_use]
    pub const fn write_authority(&self) -> NoWriteAuthority {
        self.write_authority
    }

    pub fn raw_string(
        &self,
        span: ByteSpan,
        constraints: LegacyStringConstraints,
    ) -> Result<RawLegacyString, SpanError> {
        RawLegacyString::from_document(
            Arc::clone(&self.original_bytes),
            self.provenance.document_identity(),
            span,
            constraints,
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::identity::{EmitterName, ScriptPath, ZoneName};
    use crate::{MetadataBudget, ProjectRoot, ProjectSnapshot, ReadAssurance, ScanControl};
    use std::collections::hash_map::DefaultHasher;
    use std::hash::{Hash as _, Hasher as _};
    use std::io::Write as _;

    fn budget() -> MetadataBudget {
        MetadataBudget {
            max_entries: 16,
            max_directories: 8,
            max_depth: 4,
            max_path_bytes: 512,
            max_component_bytes: 128,
            max_declared_bytes: 1024 * 1024,
            max_single_file_bytes: 1024 * 1024,
        }
    }

    fn inventoried(path: &str, bytes: &[u8]) -> InventoryFile {
        let temp = tempfile::tempdir().unwrap();
        let mut file = tempfile::Builder::new()
            .prefix(&path.replace(['/', ' '], "-"))
            .tempfile_in(temp.path())
            .unwrap();
        file.write_all(bytes).unwrap();
        file.flush().unwrap();
        let (_file, _path) = file.keep().unwrap();
        let root = ProjectRoot::open_explicit(temp.path()).unwrap();
        let snapshot = ProjectSnapshot::load(
            &root,
            budget(),
            ReadAssurance::BaselineQuarantine,
            || ScanControl::Continue,
            |_| {},
        )
        .unwrap();
        snapshot.inventory().files[0].clone()
    }

    fn constraints(maximum_bytes: Option<u64>) -> LegacyStringConstraints {
        LegacyStringConstraints {
            encoding: LegacyEncoding::Utf8,
            maximum_bytes,
            diagnose_nul: true,
            diagnose_control_bytes: true,
        }
    }

    #[test]
    fn bound_document_is_immutable_and_starts_without_injected_diagnostics() {
        let bytes = Arc::<[u8]>::from(&b"unchanged legacy bytes"[..]);
        let inventory = inventoried("Data/Server Data/Items.dat", &bytes);
        let accepted = inventory.clone();
        let provenance = DocumentProvenance::from_inventory(
            inventory,
            ParserIdentity::new("internal-binding-probe", 1),
        );
        let document =
            LegacyDocument::from_inventory_binding(Arc::clone(&bytes), 42_u16, provenance).unwrap();

        assert_eq!(document.original_bytes(), &*bytes);
        assert_eq!(*document.value(), 42);
        assert_eq!(document.write_authority(), NoWriteAuthority);
        assert!(document.diagnostics().is_empty());
        assert_eq!(document.provenance().inventory_file(), &accepted);
    }

    #[test]
    fn internal_binding_rejects_a_source_size_mismatch() {
        let accepted = b"accepted";
        let inventory = inventoried("Data/Server Data/Items.dat", accepted);
        let provenance = DocumentProvenance::from_inventory(
            inventory,
            ParserIdentity::new("size-contract-probe", 1),
        );
        let error = LegacyDocument::from_inventory_binding(
            Arc::<[u8]>::from(&b"different length"[..]),
            (),
            provenance,
        )
        .unwrap_err();
        assert_eq!(
            error,
            LegacyDocumentError::SourceSizeMismatch {
                inventory: accepted.len() as u64,
                observed: 16
            }
        );
    }

    #[test]
    fn checked_spans_and_diagnostics_preserve_authoritative_bytes() {
        let bytes = Arc::<[u8]>::from(&b"A/B\0\x01\xff"[..]);
        let inventory = inventoried("Data/Server Data/Actors.dat", &bytes);
        let provenance = DocumentProvenance::from_inventory(
            inventory,
            ParserIdentity::new("identity-contract-probe", 4),
        );
        let document =
            LegacyDocument::from_inventory_binding(Arc::clone(&bytes), (), provenance).unwrap();
        let raw = document
            .raw_string(
                ByteSpan::checked(0, bytes.len() as u64).unwrap(),
                constraints(Some(4)),
            )
            .unwrap();

        assert_eq!(raw.raw_bytes(), &*bytes);
        assert!(raw.best_effort_display().is_lossy());
        let codes = raw
            .diagnostics()
            .iter()
            .map(Diagnostic::code)
            .collect::<Vec<_>>();
        assert!(codes.contains(&DiagnosticCode::ByteLimitExceeded));
        assert!(codes.contains(&DiagnosticCode::LossyDisplayDecoding));
        assert!(codes.contains(&DiagnosticCode::DeclaredUtf8Violation));
        assert!(codes.contains(&DiagnosticCode::EmbeddedNul));
        assert!(codes.contains(&DiagnosticCode::ControlByte));
        assert_eq!(raw.raw_bytes(), b"A/B\0\x01\xff");
        assert_eq!(
            document.raw_string(
                ByteSpan::checked(bytes.len() as u64, 1).unwrap(),
                constraints(None)
            ),
            Err(SpanError::OutOfBounds {
                end: bytes.len() as u64 + 1,
                document_length: bytes.len() as u64
            })
        );
    }

    #[test]
    fn raw_name_wrappers_retain_case_normalization_and_separator_bytes() {
        let bytes = Arc::<[u8]>::from(&b"Zone|zone|e\xcc\x81|\xc3\xa9|a/b|a\\b"[..]);
        let inventory = inventoried("Data/Server Data/Actors.dat", &bytes);
        let provenance =
            DocumentProvenance::from_inventory(inventory, ParserIdentity::new("raw-name-probe", 1));
        let document = LegacyDocument::from_inventory_binding(bytes, (), provenance).unwrap();

        let zone = ZoneName::from_raw(
            document
                .raw_string(ByteSpan::checked(0, 4).unwrap(), constraints(None))
                .unwrap(),
        );
        let lower = ZoneName::from_raw(
            document
                .raw_string(ByteSpan::checked(5, 4).unwrap(), constraints(None))
                .unwrap(),
        );
        let decomposed = ZoneName::from_raw(
            document
                .raw_string(ByteSpan::checked(10, 3).unwrap(), constraints(None))
                .unwrap(),
        );
        let composed = ZoneName::from_raw(
            document
                .raw_string(ByteSpan::checked(14, 2).unwrap(), constraints(None))
                .unwrap(),
        );
        let slash = ZoneName::from_raw(
            document
                .raw_string(ByteSpan::checked(17, 3).unwrap(), constraints(None))
                .unwrap(),
        );
        let backslash = ZoneName::from_raw(
            document
                .raw_string(ByteSpan::checked(21, 3).unwrap(), constraints(None))
                .unwrap(),
        );

        let same_bytes = Arc::<[u8]>::from(&b"Zone"[..]);
        let same_inventory = inventoried("different-document", &same_bytes);
        let same_provenance = DocumentProvenance::from_inventory(
            same_inventory,
            ParserIdentity::new("second-raw-name-probe", 1),
        );
        let same_document =
            LegacyDocument::from_inventory_binding(same_bytes, (), same_provenance).unwrap();
        let same_zone = ZoneName::from_raw(
            same_document
                .raw_string(ByteSpan::checked(0, 4).unwrap(), constraints(None))
                .unwrap(),
        );

        assert_eq!(zone.raw().raw_bytes(), b"Zone");
        assert_eq!(zone, same_zone);
        assert_eq!(zone.cmp(&same_zone), std::cmp::Ordering::Equal);
        let mut zone_hash = DefaultHasher::new();
        zone.hash(&mut zone_hash);
        let mut same_hash = DefaultHasher::new();
        same_zone.hash(&mut same_hash);
        assert_eq!(zone_hash.finish(), same_hash.finish());
        assert_ne!(zone, lower);
        assert_eq!(decomposed.raw().best_effort_display().text(), "e\u{301}");
        assert_eq!(composed.raw().best_effort_display().text(), "\u{e9}");
        assert_ne!(decomposed, composed);
        assert_ne!(slash, backslash);
        assert_eq!(slash.cmp(&backslash), b"a/b".as_slice().cmp(b"a\\b"));
        assert_eq!(&document.original_bytes()[4..5], b"|");

        let emitter = EmitterName::from_raw(
            document
                .raw_string(ByteSpan::checked(0, 4).unwrap(), constraints(None))
                .unwrap(),
        );
        let same_emitter = EmitterName::from_raw(
            same_document
                .raw_string(ByteSpan::checked(0, 4).unwrap(), constraints(None))
                .unwrap(),
        );
        let script = ScriptPath::from_raw(
            document
                .raw_string(ByteSpan::checked(0, 4).unwrap(), constraints(None))
                .unwrap(),
        );
        let same_script = ScriptPath::from_raw(
            same_document
                .raw_string(ByteSpan::checked(0, 4).unwrap(), constraints(None))
                .unwrap(),
        );
        assert_eq!(emitter, same_emitter);
        assert_eq!(script, same_script);
        assert_eq!(emitter.raw().raw_bytes(), script.raw().raw_bytes());
    }

    #[test]
    fn generated_diagnostics_are_deterministic_and_bounded() {
        let bytes = Arc::<[u8]>::from(vec![0_u8; 200]);
        let inventory = inventoried("Data/Server Data/Actors.dat", &bytes);
        let provenance = DocumentProvenance::from_inventory(
            inventory,
            ParserIdentity::new("diagnostic-limit-probe", 1),
        );
        let document = LegacyDocument::from_inventory_binding(bytes, (), provenance).unwrap();
        let first = document
            .raw_string(ByteSpan::checked(0, 200).unwrap(), constraints(None))
            .unwrap();
        let second = document
            .raw_string(ByteSpan::checked(0, 200).unwrap(), constraints(None))
            .unwrap();
        assert_eq!(first, second);
        assert_eq!(first.diagnostics().len(), MAX_STRING_DIAGNOSTICS);
        assert_eq!(
            first.diagnostics().last().unwrap().code(),
            DiagnosticCode::AdditionalDiagnosticsSuppressed
        );
    }
}
