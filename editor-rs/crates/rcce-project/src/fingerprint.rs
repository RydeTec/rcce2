use sha2::{Digest, Sha256};

const TREE_DOMAIN: &[u8] = b"RCCE-PROJECT-TREE-V1\0";

/// SHA-256 of one accepted source file.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub struct SourceFingerprint(pub [u8; 32]);

impl SourceFingerprint {
    #[must_use]
    pub fn from_bytes(bytes: &[u8]) -> Self {
        Self(Sha256::digest(bytes).into())
    }
}

/// Domain-separated digest of the complete, path-keyed accepted inventory.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct TreeFingerprint([u8; 32]);

impl TreeFingerprint {
    #[must_use]
    pub const fn as_bytes(&self) -> &[u8; 32] {
        &self.0
    }

    #[must_use]
    pub(crate) fn from_entries<'a>(
        entries: impl IntoIterator<Item = (&'a str, u64, SourceFingerprint)>,
    ) -> Self {
        let mut entries = entries.into_iter().collect::<Vec<_>>();
        entries.sort_by(|left, right| left.0.as_bytes().cmp(right.0.as_bytes()));
        assert!(
            entries.windows(2).all(|pair| pair[0].0 != pair[1].0),
            "accepted inventory paths must be unique"
        );
        let mut hasher = Sha256::new();
        hasher.update(TREE_DOMAIN);
        for (path, size, source) in entries {
            let path = path.as_bytes();
            hasher.update((path.len() as u64).to_le_bytes());
            hasher.update(path);
            hasher.update(size.to_le_bytes());
            hasher.update(source.0);
        }
        Self(hasher.finalize().into())
    }
}

impl AsRef<[u8]> for TreeFingerprint {
    fn as_ref(&self) -> &[u8] {
        self.as_bytes()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn tree_digest_is_domain_separated_path_keyed_and_caller_order_independent() {
        let same = SourceFingerprint::from_bytes(b"same");
        let one = TreeFingerprint::from_entries([("a", 4, same), ("b", 4, same)]);
        let reverse = TreeFingerprint::from_entries([("b", 4, same), ("a", 4, same)]);
        let merged = SourceFingerprint::from_bytes(b"samesame");
        assert_eq!(one, reverse);
        assert_ne!(one, TreeFingerprint::from_entries([("a", 8, merged)]));
    }

    #[test]
    #[should_panic(expected = "accepted inventory paths must be unique")]
    fn duplicate_tree_paths_are_rejected() {
        let same = SourceFingerprint::from_bytes(b"same");
        let _ = TreeFingerprint::from_entries([("a", 4, same), ("a", 4, same)]);
    }
}
