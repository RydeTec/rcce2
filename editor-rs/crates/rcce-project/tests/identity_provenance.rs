use rcce_project::{
    ActorId, ByteSpan, DocumentProvenance, EmitterName, IdentityKind, ItemId, LegacyReference,
    MediaId, MetadataBudget, ParserIdentity, ProjectRoot, ProjectSnapshot, ReadAssurance,
    ScanControl, ScriptPath, SpanError, ZoneName,
};
use std::any::TypeId;
use std::fs;

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

fn inventoried(path: &str, bytes: &[u8]) -> rcce_project::InventoryFile {
    let temp = tempfile::tempdir().unwrap();
    let host_path = temp.path().join(path);
    fs::create_dir_all(host_path.parent().unwrap()).unwrap();
    fs::write(&host_path, bytes).unwrap();
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

#[test]
fn numeric_identity_domains_are_distinct_and_sentinels_are_explicit() {
    assert_ne!(TypeId::of::<ActorId>(), TypeId::of::<ItemId>());
    assert_ne!(TypeId::of::<ItemId>(), TypeId::of::<MediaId>());
    assert_ne!(TypeId::of::<ZoneName>(), TypeId::of::<EmitterName>());
    assert_ne!(TypeId::of::<EmitterName>(), TypeId::of::<ScriptPath>());
    assert_eq!(ActorId::new(u16::MAX).raw(), u16::MAX);
    assert_eq!(
        ItemId::try_new(u16::MAX).unwrap_err().kind(),
        IdentityKind::Item
    );
    assert_eq!(
        MediaId::try_new(u16::MAX).unwrap_err().kind(),
        IdentityKind::Media
    );
    assert_eq!(ItemId::try_new(u16::MAX).unwrap_err().raw(), u16::MAX);
    assert_eq!(
        ItemId::from_legacy_reference(u16::MAX),
        LegacyReference::None
    );
    assert_eq!(
        MediaId::from_legacy_reference(u16::MAX),
        LegacyReference::None
    );
    assert_eq!(
        ItemId::from_legacy_reference(u16::MAX - 1),
        LegacyReference::Identity(ItemId::try_new(u16::MAX - 1).unwrap())
    );
}

#[test]
fn byte_spans_check_arithmetic_address_space_and_document_bounds() {
    assert_eq!(ByteSpan::checked(7, 5).unwrap().start(), 7);
    assert_eq!(ByteSpan::checked(7, 5).unwrap().end(), 12);
    assert_eq!(ByteSpan::checked(7, 5).unwrap().length(), 5);
    assert_eq!(
        ByteSpan::checked(u64::MAX, 1),
        Err(SpanError::ArithmeticOverflow)
    );
    assert_eq!(ByteSpan::from_bounds(5, 4), Err(SpanError::ReversedBounds));
    assert_eq!(
        ByteSpan::checked(3, 3).unwrap().checked_range(5),
        Err(SpanError::OutOfBounds {
            end: 6,
            document_length: 5
        })
    );
}

#[test]
fn provenance_consumes_the_exact_p03_record_without_rehashing() {
    let bytes = b"unchanged legacy bytes";
    let inventory = inventoried("Data/Server Data/Items.dat", bytes);
    let accepted = inventory.clone();
    let parser = ParserIdentity::new("items-minimal-envelope", 7);
    let provenance = DocumentProvenance::from_inventory(inventory, parser);

    assert_eq!(provenance.inventory_file(), &accepted);
    assert_eq!(
        provenance.inventory_file().fingerprint,
        accepted.fingerprint
    );
    assert_eq!(
        provenance.inventory_file().provenance.source,
        accepted.provenance.source
    );
    assert_eq!(
        provenance.inventory_file().classification,
        accepted.classification
    );
    assert_eq!(provenance.parser().name(), "items-minimal-envelope");
    assert_eq!(provenance.parser().version(), 7);
    assert_eq!(
        provenance.document_identity().source_fingerprint(),
        accepted.fingerprint
    );
    assert_eq!(
        provenance.document_identity().inventory_path(),
        accepted.path
    );
}

#[test]
fn public_surface_has_no_text_coercion_host_path_serialization_or_mutation_api() {
    let identity_source = include_str!("../src/identity.rs");
    let legacy_source = include_str!("../src/legacy.rs");
    for forbidden in [
        "serde::",
        "Serialize",
        "pub fn raw_bytes_mut",
        "pub fn value_mut",
    ] {
        assert!(
            !legacy_source.contains(forbidden),
            "forbidden surface: {forbidden}"
        );
    }
    assert!(!identity_source.contains("impl From<ActorId> for ItemId"));
    assert!(!identity_source.contains("impl From<ItemId> for ActorId"));
    assert_legacy_document_has_no_public_construction_route(legacy_source);
}

fn balanced_body(source: &str, opening_brace: usize) -> (&str, usize) {
    let mut depth = 0_u32;
    for (offset, byte) in source.as_bytes()[opening_brace..]
        .iter()
        .copied()
        .enumerate()
    {
        match byte {
            b'{' => depth += 1,
            b'}' => {
                depth -= 1;
                if depth == 0 {
                    let closing = opening_brace + offset;
                    return (&source[opening_brace + 1..closing], closing + 1);
                }
            }
            _ => {}
        }
    }
    panic!("unbalanced Rust block")
}

fn legacy_document_impl_bodies(source: &str) -> Vec<&str> {
    let mut bodies = Vec::new();
    let mut cursor = 0;
    while let Some(relative) = source[cursor..].find("impl") {
        let start = cursor + relative;
        let before = source.as_bytes().get(start.wrapping_sub(1)).copied();
        let after = source.as_bytes().get(start + 4).copied();
        cursor = start + 4;
        if before.is_some_and(|byte| byte.is_ascii_alphanumeric() || byte == b'_')
            || after.is_some_and(|byte| byte.is_ascii_alphanumeric() || byte == b'_')
        {
            continue;
        }
        let Some(open_relative) = source[cursor..].find('{') else {
            break;
        };
        let opening = cursor + open_relative;
        let header = &source[start..opening];
        let (body, end) = balanced_body(source, opening);
        cursor = end;
        if header
            .split(|character: char| !character.is_alphanumeric() && character != '_')
            .any(|token| token == "LegacyDocument")
        {
            bodies.push(body);
        }
    }
    bodies
}

fn public_items_at_impl_depth(body: &str) -> Vec<String> {
    let mut depth = 0_i32;
    let mut items = Vec::new();
    let bytes = body.as_bytes();
    let mut index = 0;
    while index < bytes.len() {
        match bytes[index] {
            b'{' => depth += 1,
            b'}' => depth -= 1,
            b'p' if depth == 0
                && body[index..].starts_with("pub ")
                && index.checked_sub(1).is_none_or(|before| {
                    !bytes[before].is_ascii_alphanumeric() && bytes[before] != b'_'
                }) =>
            {
                let start = index;
                while index < bytes.len() && bytes[index] != b'{' && bytes[index] != b';' {
                    index += 1;
                }
                items.push(
                    body[start..index]
                        .split_whitespace()
                        .collect::<Vec<_>>()
                        .join(" "),
                );
                continue;
            }
            _ => {}
        }
        index += 1;
    }
    items
}

fn assert_legacy_document_has_no_public_construction_route(source: &str) {
    let declaration = source.find("pub struct LegacyDocument").unwrap();
    let opening = source[declaration..].find('{').unwrap() + declaration;
    let (fields, _) = balanced_body(source, opening);
    let field_lines = fields
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty())
        .collect::<Vec<_>>();
    assert!(field_lines.iter().all(|line| !line.starts_with("pub")));
    assert!(field_lines
        .iter()
        .any(|line| line.starts_with("original_bytes:")));
    assert!(field_lines
        .iter()
        .any(|line| line.starts_with("diagnostics:")));

    let impls = legacy_document_impl_bodies(source);
    assert!(!impls.is_empty());
    let public_items = impls
        .iter()
        .flat_map(|body| public_items_at_impl_depth(body))
        .collect::<Vec<_>>();
    assert!(!public_items.is_empty());
    for item in public_items {
        assert!(
            item.contains("&self"),
            "public associated construction route: {item}"
        );
        assert!(!item.contains("-> Self"));
        assert!(!item.contains("Result<Self"));
    }
}
