use super::{portable_alias_key, BackendError};
use sha2::{Digest, Sha256};
use std::collections::BTreeSet;
use std::io::Read;
use std::path::Path;

const MAX_SAFE_RECURSION_DEPTH: u64 = 64;

const fn effective_max_depth(requested: u64) -> u64 {
    if requested < MAX_SAFE_RECURSION_DEPTH {
        requested
    } else {
        MAX_SAFE_RECURSION_DEPTH
    }
}

#[derive(Debug, Clone, Copy)]
pub struct WalkBudget {
    pub max_bytes: u64,
    pub max_files: u64,
    pub max_single_file_bytes: u64,
    pub max_directories: u64,
    pub max_depth: u64,
    pub max_path_bytes: u64,
    pub max_component_bytes: u64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct WalkFile {
    pub path: String,
    pub size: u64,
    pub sha256: [u8; 32],
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct WalkResult {
    pub files: Vec<WalkFile>,
    pub directories: u64,
    pub bytes: u64,
}

#[derive(Debug, Clone, Copy)]
pub struct MetadataBudget {
    pub max_entries: u64,
    pub max_directories: u64,
    pub max_depth: u64,
    pub max_path_bytes: u64,
    pub max_component_bytes: u64,
    pub max_declared_bytes: u64,
    pub max_single_file_bytes: u64,
}

#[derive(Clone, PartialEq, Eq, PartialOrd, Ord)]
pub enum MetadataLocation {
    Portable(String),
    Opaque(Vec<Vec<u8>>),
}

impl std::fmt::Debug for MetadataLocation {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Portable(path) => formatter.debug_tuple("Portable").field(path).finish(),
            Self::Opaque(parts) => formatter
                .debug_struct("Opaque")
                .field("components", &parts.len())
                .field("bytes", &parts.iter().map(Vec::len).sum::<usize>())
                .finish(),
        }
    }
}

fn check_opaque_path_budget(
    parent: &[Vec<u8>],
    component: &[u8],
    budget: &MetadataBudget,
) -> Result<(), BackendError> {
    let component_limit = || BackendError::ResourceLimit {
        path: "<opaque>".to_owned(),
        limit: "max_component_bytes",
    };
    let path_limit = || BackendError::ResourceLimit {
        path: "<opaque>".to_owned(),
        limit: "max_path_bytes",
    };
    let component_bytes = u64::try_from(component.len()).map_err(|_| component_limit())?;
    if component_bytes > budget.max_component_bytes {
        return Err(component_limit());
    }
    let mut path_bytes = component_bytes;
    for part in parent {
        let part_bytes = u64::try_from(part.len()).map_err(|_| component_limit())?;
        if part_bytes > budget.max_component_bytes {
            return Err(component_limit());
        }
        path_bytes = path_bytes
            .checked_add(part_bytes)
            .and_then(|total| total.checked_add(1))
            .ok_or_else(path_limit)?;
    }
    if path_bytes > budget.max_path_bytes {
        return Err(path_limit());
    }
    Ok(())
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum UnavailableKind {
    NonPortableName,
    AliasCollision,
    LinkOrReparsePoint,
    MultiplyLinkedFile,
    SpecialFile,
    MountBoundary,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MetadataKind {
    Directory,
    File { size: u64 },
    Unavailable(UnavailableKind),
}

#[derive(Debug)]
pub struct MetadataEntry {
    pub location: MetadataLocation,
    pub kind: MetadataKind,
    pub(crate) token: Option<EntryToken>,
}

#[derive(Debug)]
pub(crate) enum EntryToken {
    #[cfg(unix)]
    Unix {
        device: u64,
        inode: u64,
        size: u64,
        change_seconds: i64,
        change_nanoseconds: i64,
    },
    #[cfg(windows)]
    Windows {
        volume: u64,
        file: u64,
        size: u64,
        modified_seconds: u64,
        modified_nanoseconds: u32,
        opened: cap_std::fs::File,
    },
}

#[derive(Debug)]
pub struct MetadataResult {
    pub entries: Vec<MetadataEntry>,
    pub directories: u64,
    pub declared_bytes: u64,
}

fn validate_component(component: &str) -> Result<(), BackendError> {
    super::validate_component(component).map_err(|_| BackendError::UnsafeObject {
        path: component.to_owned(),
        reason: "non-portable or traversal-capable path component",
    })
}

pub(crate) struct Backend {
    #[cfg(unix)]
    inner: unix::Backend,
    #[cfg(windows)]
    inner: windows::Backend,
}

impl Backend {
    pub(crate) fn open(path: &Path) -> Result<Self, BackendError> {
        #[cfg(unix)]
        {
            unix::Backend::open(path).map(|inner| Self { inner })
        }
        #[cfg(windows)]
        {
            windows::Backend::open(path).map(|inner| Self { inner })
        }
        #[cfg(not(any(unix, windows)))]
        {
            let _ = path;
            Err(BackendError::UnsupportedPlatform(
                "no descriptor-relative no-follow backend exists for this platform",
            ))
        }
    }

    pub(crate) fn read_component(
        &self,
        name: &str,
        max_bytes: u64,
    ) -> Result<Vec<u8>, BackendError> {
        #[cfg(unix)]
        {
            self.inner.read_component(name, max_bytes)
        }
        #[cfg(windows)]
        {
            self.inner.read_component(name, max_bytes)
        }
        #[cfg(not(any(unix, windows)))]
        {
            let _ = (name, max_bytes);
            Err(BackendError::UnsupportedPlatform("no safe backend"))
        }
    }

    pub(crate) fn component_exists(&self, name: &str) -> Result<bool, BackendError> {
        #[cfg(unix)]
        {
            self.inner.component_exists(name)
        }
        #[cfg(windows)]
        {
            self.inner.component_exists(name)
        }
        #[cfg(not(any(unix, windows)))]
        {
            let _ = name;
            Err(BackendError::UnsupportedPlatform("no safe backend"))
        }
    }

    pub(crate) fn inventory(
        &self,
        component: &str,
        budget: WalkBudget,
    ) -> Result<WalkResult, BackendError> {
        #[cfg(unix)]
        {
            self.inner.inventory(component, budget)
        }
        #[cfg(windows)]
        {
            self.inner.inventory(component, budget)
        }
        #[cfg(not(any(unix, windows)))]
        {
            let _ = (component, budget);
            Err(BackendError::UnsupportedPlatform("no safe backend"))
        }
    }

    pub(crate) fn metadata(
        &self,
        budget: MetadataBudget,
        control: &mut dyn FnMut() -> bool,
    ) -> Result<MetadataResult, BackendError> {
        #[cfg(unix)]
        {
            self.inner.metadata(budget, control)
        }
        #[cfg(windows)]
        {
            self.inner.metadata(budget, control)
        }
        #[cfg(not(any(unix, windows)))]
        {
            let _ = (budget, control);
            Err(BackendError::UnsupportedPlatform("no safe backend"))
        }
    }

    pub(crate) fn read_bound(
        &self,
        path: &str,
        token: &EntryToken,
        max_bytes: u64,
        control: &mut dyn FnMut() -> bool,
    ) -> Result<Option<Vec<u8>>, BackendError> {
        #[cfg(unix)]
        {
            self.inner.read_bound(path, token, max_bytes, control)
        }
        #[cfg(windows)]
        {
            self.inner.read_bound(path, token, max_bytes, control)
        }
        #[cfg(not(any(unix, windows)))]
        {
            let _ = (path, token, max_bytes, control);
            Err(BackendError::UnsupportedPlatform("no safe backend"))
        }
    }

    pub(crate) fn read_fixture_file(
        &self,
        fixture: &str,
        path: &str,
        max_bytes: u64,
    ) -> Result<Vec<u8>, BackendError> {
        #[cfg(unix)]
        {
            self.inner.read_fixture_file(fixture, path, max_bytes)
        }
        #[cfg(windows)]
        {
            self.inner.read_fixture_file(fixture, path, max_bytes)
        }
        #[cfg(not(any(unix, windows)))]
        {
            let _ = (fixture, path, max_bytes);
            Err(BackendError::UnsupportedPlatform("no safe backend"))
        }
    }

    pub(crate) fn identity_tokens(&self) -> (u64, u64) {
        #[cfg(unix)]
        {
            self.inner.identity_tokens()
        }
        #[cfg(windows)]
        {
            self.inner.identity_tokens()
        }
        #[cfg(not(any(unix, windows)))]
        {
            (0, 0)
        }
    }

    pub(crate) fn transient_race_detection_available(&self) -> bool {
        #[cfg(any(unix, windows))]
        {
            self.inner.transient_race_detection_available()
        }
        #[cfg(not(any(unix, windows)))]
        {
            false
        }
    }
}

#[cfg(windows)]
mod windows {
    use super::*;
    use cap_fs_ext::{DirExt, FollowSymlinks, MetadataExt, OpenOptionsFollowExt};
    use cap_std::fs::{Dir, File, OpenOptions};
    use std::os::windows::ffi::OsStrExt;
    use std::os::windows::fs::FileExt as WindowsFileExt;
    use std::os::windows::fs::OpenOptionsExt;
    use windows_sys::Win32::Storage::FileSystem::{
        FILE_FLAG_BACKUP_SEMANTICS, FILE_FLAG_OPEN_REPARSE_POINT,
    };

    #[derive(Debug, Clone, Copy, PartialEq, Eq)]
    struct Identity {
        volume: u64,
        file: u64,
    }

    fn identity(metadata: &cap_std::fs::Metadata) -> Identity {
        Identity {
            volume: metadata.dev(),
            file: metadata.ino(),
        }
    }

    fn modified_token(
        metadata: &cap_std::fs::Metadata,
        path: &str,
    ) -> Result<(u64, u32), BackendError> {
        let modified = metadata
            .modified()
            .map_err(|error| BackendError::io("read file modification time", path, error))?;
        let duration = modified
            .duration_since(cap_std::time::SystemTime::from_std(std::time::UNIX_EPOCH))
            .map_err(|_| BackendError::Integrity {
                path: path.to_owned(),
                reason: "file modification time predates supported epoch",
            })?;
        Ok((duration.as_secs(), duration.subsec_nanos()))
    }

    #[derive(Debug)]
    struct PendingFile {
        components: Vec<String>,
        path: String,
        size: u64,
        identity: Identity,
    }

    pub(super) struct Backend {
        dir: Dir,
        identity: Identity,
    }

    impl Backend {
        pub(super) fn transient_race_detection_available(&self) -> bool {
            false
        }

        pub(super) fn identity_tokens(&self) -> (u64, u64) {
            (self.identity.volume, self.identity.file)
        }

        pub(super) fn open(path: &Path) -> Result<Self, BackendError> {
            let file = std::fs::OpenOptions::new()
                .read(true)
                .custom_flags(FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OPEN_REPARSE_POINT)
                .open(path)
                .map_err(|error| BackendError::io("open root reparse point itself", path, error))?;
            let metadata = file
                .metadata()
                .map_err(|error| BackendError::io("stat opened root", path, error))?;
            if metadata.file_type().is_symlink() || !metadata.is_dir() {
                return Err(BackendError::UnsafeObject {
                    path: path.display().to_string(),
                    reason: "root is not a direct directory or is a reparse point",
                });
            }
            let dir = Dir::from_std_file(file);
            let metadata = dir
                .dir_metadata()
                .map_err(|error| BackendError::io("read root handle identity", path, error))?;
            Ok(Self {
                identity: identity(&metadata),
                dir,
            })
        }

        pub(super) fn component_exists(&self, name: &str) -> Result<bool, BackendError> {
            validate_component(name)?;
            ensure_unique_portable_alias(&self.dir, name, name)?;
            match self.dir.symlink_metadata(name) {
                Ok(_) => Ok(true),
                Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(false),
                Err(error) => Err(BackendError::io("inspect root component", name, error)),
            }
        }

        pub(super) fn read_component(
            &self,
            name: &str,
            max_bytes: u64,
        ) -> Result<Vec<u8>, BackendError> {
            validate_component(name)?;
            ensure_unique_portable_alias(&self.dir, name, name)?;
            let mut file = open_file_nofollow(&self.dir, name, name)?;
            let before = checked_file_metadata(&file, name, self.identity.volume)?;
            if before.len() > max_bytes {
                return Err(BackendError::ResourceLimit {
                    path: name.to_owned(),
                    limit: "bounded metadata-file read ceiling",
                });
            }
            let mut bytes = Vec::with_capacity(usize::try_from(before.len()).unwrap_or(0));
            file.by_ref()
                .take(max_bytes.saturating_add(1))
                .read_to_end(&mut bytes)
                .map_err(|error| BackendError::io("read bounded root component", name, error))?;
            let after = checked_file_metadata(&file, name, self.identity.volume)?;
            if identity(&before) != identity(&after)
                || before.len() != after.len()
                || u64::try_from(bytes.len()).unwrap_or(u64::MAX) != before.len()
            {
                return Err(BackendError::Integrity {
                    path: name.to_owned(),
                    reason: "file identity, link count, or size changed during read",
                });
            }
            Ok(bytes)
        }

        pub(super) fn inventory(
            &self,
            component: &str,
            budget: WalkBudget,
        ) -> Result<WalkResult, BackendError> {
            self.inventory_with_before_hash(component, budget, || {})
        }

        pub(super) fn metadata(
            &self,
            budget: MetadataBudget,
            control: &mut dyn FnMut() -> bool,
        ) -> Result<MetadataResult, BackendError> {
            let mut result = MetadataResult {
                entries: Vec::new(),
                directories: 0,
                declared_bytes: 0,
            };
            enumerate_metadata_windows(
                &self.dir,
                &mut Vec::new(),
                &mut Vec::new(),
                &mut result,
                &budget,
                self.identity.volume,
                control,
            )?;
            result.entries.sort_by(|a, b| a.location.cmp(&b.location));
            Ok(result)
        }

        pub(super) fn read_bound(
            &self,
            path: &str,
            token: &EntryToken,
            max_bytes: u64,
            control: &mut dyn FnMut() -> bool,
        ) -> Result<Option<Vec<u8>>, BackendError> {
            let EntryToken::Windows {
                volume,
                file,
                size,
                modified_seconds,
                modified_nanoseconds,
                opened: retained,
            } = token;
            let components = path.split('/').collect::<Vec<_>>();
            if components.is_empty() {
                return Err(BackendError::Semantic("empty bound path".to_owned()));
            }
            for component in &components {
                validate_component(component)?;
            }
            let mut current = self
                .dir
                .open_dir_nofollow(".")
                .map_err(|error| BackendError::io("duplicate project root", path, error))?;
            for component in &components[..components.len().saturating_sub(1)] {
                ensure_unique_portable_alias(&current, component, path)?;
                current = current
                    .open_dir_nofollow(component)
                    .map_err(|error| BackendError::io("open bound parent", path, error))?;
                let metadata = current
                    .dir_metadata()
                    .map_err(|error| BackendError::io("stat bound parent", path, error))?;
                if identity(&metadata).volume != self.identity.volume {
                    return Err(BackendError::UnsafeObject {
                        path: path.to_owned(),
                        reason: "bound parent crosses a volume boundary",
                    });
                }
            }
            let name = components.last().expect("checked non-empty");
            ensure_unique_portable_alias(&current, name, path)?;
            let before = current
                .symlink_metadata(name)
                .map_err(|error| BackendError::io("lstat bound file", path, error))?;
            if !before.is_file() || before.nlink() != 1 {
                return Err(BackendError::Integrity {
                    path: path.to_owned(),
                    reason: "enumerated file is no longer a singly linked regular file",
                });
            }
            if identity(&before)
                != (Identity {
                    volume: *volume,
                    file: *file,
                })
                || before.len() != *size
                || modified_token(&before, path)? != (*modified_seconds, *modified_nanoseconds)
            {
                return Err(BackendError::Integrity {
                    path: path.to_owned(),
                    reason:
                        "enumerated file identity, size, or modification time changed before read",
                });
            }
            if *size > max_bytes {
                return Err(BackendError::ResourceLimit {
                    path: path.to_owned(),
                    limit: "max_single_file_bytes",
                });
            }
            let opened_metadata = checked_file_metadata(retained, path, self.identity.volume)?;
            if identity(&opened_metadata)
                != (Identity {
                    volume: *volume,
                    file: *file,
                })
                || opened_metadata.len() != *size
                || modified_token(&opened_metadata, path)?
                    != (*modified_seconds, *modified_nanoseconds)
            {
                return Err(BackendError::Integrity {
                    path: path.to_owned(),
                    reason: "enumerated file identity changed while opening",
                });
            }
            let opened = retained
                .try_clone()
                .map_err(|error| BackendError::io("clone retained bound file", path, error))?
                .into_std();
            let mut bytes = Vec::with_capacity(usize::try_from(*size).unwrap_or(0));
            let mut buffer = [0_u8; 64 * 1024];
            let mut offset = 0_u64;
            loop {
                if !control() {
                    return Ok(None);
                }
                let count = opened
                    .seek_read(&mut buffer, offset)
                    .map_err(|error| BackendError::io("read bound file chunk", path, error))?;
                if count == 0 {
                    break;
                }
                bytes.extend_from_slice(&buffer[..count]);
                offset = offset.checked_add(count as u64).ok_or_else(|| {
                    BackendError::ResourceLimit {
                        path: path.to_owned(),
                        limit: "observed byte overflow",
                    }
                })?;
                if bytes.len() as u64 > max_bytes {
                    return Err(BackendError::ResourceLimit {
                        path: path.to_owned(),
                        limit: "max_single_file_bytes",
                    });
                }
            }
            let after = checked_file_metadata(retained, path, self.identity.volume)?;
            if identity(&after)
                != (Identity {
                    volume: *volume,
                    file: *file,
                })
                || after.len() != *size
                || modified_token(&after, path)? != (*modified_seconds, *modified_nanoseconds)
                || bytes.len() as u64 != *size
            {
                return Err(BackendError::Integrity {
                    path: path.to_owned(),
                    reason:
                        "enumerated file identity, size, or modification time changed during read",
                });
            }
            Ok(Some(bytes))
        }

        fn inventory_with_before_hash(
            &self,
            component: &str,
            budget: WalkBudget,
            before_hash: impl FnOnce(),
        ) -> Result<WalkResult, BackendError> {
            validate_component(component)?;
            ensure_unique_portable_alias(&self.dir, component, component)?;
            let fixture = self.dir.open_dir_nofollow(component).map_err(|error| {
                BackendError::io(
                    "open fixture root without following reparse points",
                    component,
                    error,
                )
            })?;
            let fixture_metadata = fixture.dir_metadata().map_err(|error| {
                BackendError::io("read fixture root identity", component, error)
            })?;
            if identity(&fixture_metadata).volume != self.identity.volume {
                return Err(BackendError::UnsafeObject {
                    path: component.to_owned(),
                    reason: "fixture root crosses a volume boundary",
                });
            }
            let mut pending = Vec::new();
            let mut directories = 0;
            let mut declared_bytes = 0;
            enumerate_dir(
                &fixture,
                &mut Vec::new(),
                &mut pending,
                &mut directories,
                &mut declared_bytes,
                &budget,
                self.identity.volume,
            )?;
            pending.sort_by(|a, b| a.path.as_bytes().cmp(b.path.as_bytes()));
            before_hash();
            let mut files = Vec::with_capacity(pending.len());
            let mut hashed_bytes = 0_u64;
            for pending in pending {
                let mut file = reopen_file(&fixture, &pending, self.identity.volume)?;
                let before = checked_file_metadata(&file, &pending.path, self.identity.volume)?;
                if identity(&before) != pending.identity || before.len() != pending.size {
                    return Err(BackendError::Integrity {
                        path: pending.path,
                        reason: "file identity changed before hashing",
                    });
                }
                let mut hasher = Sha256::new();
                let copied = std::io::copy(&mut file, &mut hasher)
                    .map_err(|error| BackendError::io("hash fixture file", &pending.path, error))?;
                let after = checked_file_metadata(&file, &pending.path, self.identity.volume)?;
                if identity(&after) != pending.identity
                    || after.len() != pending.size
                    || copied != pending.size
                {
                    return Err(BackendError::Integrity {
                        path: pending.path,
                        reason: "file identity, link count, or size changed while hashing",
                    });
                }
                hashed_bytes = hashed_bytes.checked_add(pending.size).ok_or_else(|| {
                    BackendError::ResourceLimit {
                        path: pending.path.clone(),
                        limit: "aggregate byte counter overflow",
                    }
                })?;
                files.push(WalkFile {
                    path: pending.path,
                    size: pending.size,
                    sha256: hasher.finalize().into(),
                });
            }
            if hashed_bytes != declared_bytes {
                return Err(BackendError::Integrity {
                    path: component.to_owned(),
                    reason: "metadata and hashed byte totals differ",
                });
            }
            Ok(WalkResult {
                files,
                directories,
                bytes: declared_bytes,
            })
        }

        pub(super) fn read_fixture_file(
            &self,
            fixture: &str,
            path: &str,
            max_bytes: u64,
        ) -> Result<Vec<u8>, BackendError> {
            validate_component(fixture)?;
            let components = path.split('/').map(str::to_owned).collect::<Vec<_>>();
            if components.is_empty() {
                return Err(BackendError::Semantic("empty fixture file path".to_owned()));
            }
            for component in &components {
                validate_component(component)?;
            }
            ensure_unique_portable_alias(&self.dir, fixture, fixture)?;
            let fixture_dir = self.dir.open_dir_nofollow(fixture).map_err(|error| {
                BackendError::io("open fixture root for canary validation", fixture, error)
            })?;
            let pending = PendingFile {
                components,
                path: path.to_owned(),
                size: 0,
                identity: Identity { volume: 0, file: 0 },
            };
            let mut file = reopen_file_unchecked(&fixture_dir, &pending, self.identity.volume)?;
            let before = checked_file_metadata(&file, path, self.identity.volume)?;
            if before.len() > max_bytes {
                return Err(BackendError::ResourceLimit {
                    path: path.to_owned(),
                    limit: "max_single_file_bytes",
                });
            }
            let mut bytes = Vec::with_capacity(usize::try_from(before.len()).unwrap_or(0));
            file.by_ref()
                .take(max_bytes.saturating_add(1))
                .read_to_end(&mut bytes)
                .map_err(|error| {
                    BackendError::io("read bounded canary-bearing file", path, error)
                })?;
            let after = checked_file_metadata(&file, path, self.identity.volume)?;
            if identity(&before) != identity(&after)
                || before.len() != after.len()
                || u64::try_from(bytes.len()).unwrap_or(u64::MAX) != before.len()
            {
                return Err(BackendError::Integrity {
                    path: path.to_owned(),
                    reason: "canary-bearing file changed during read",
                });
            }
            Ok(bytes)
        }
    }

    fn open_file_nofollow(dir: &Dir, name: &str, path: &str) -> Result<File, BackendError> {
        let mut options = OpenOptions::new();
        options.read(true).follow(FollowSymlinks::No);
        dir.open_with(name, &options).map_err(|error| {
            BackendError::io("open file without following reparse points", path, error)
        })
    }

    fn ensure_unique_portable_alias(
        dir: &Dir,
        selected: &str,
        path: &str,
    ) -> Result<(), BackendError> {
        let selected_key = portable_alias_key(selected);
        let mut matching = 0_u8;
        let mut exact = 0_u8;
        let entries = dir
            .entries()
            .map_err(|error| BackendError::io("enumerate selected path parent", path, error))?;
        for entry in entries {
            let entry = entry.map_err(|error| {
                BackendError::io("read selected path parent entry", path, error)
            })?;
            let Ok(name) = entry.file_name().into_string() else {
                continue;
            };
            if validate_component(&name).is_err() {
                continue;
            }
            if portable_alias_key(&name) == selected_key {
                matching = matching.saturating_add(1);
                if name == selected {
                    exact = exact.saturating_add(1);
                }
            }
        }
        if matching > 1 || (matching == 1 && exact != 1) {
            return Err(BackendError::UnsafeObject {
                path: path.to_owned(),
                reason: "selected path has a case or Unicode-normalization alias collision",
            });
        }
        Ok(())
    }

    fn checked_file_metadata(
        file: &File,
        path: &str,
        root_volume: u64,
    ) -> Result<cap_std::fs::Metadata, BackendError> {
        let metadata = file
            .metadata()
            .map_err(|error| BackendError::io("read opened file identity", path, error))?;
        if !metadata.is_file() || metadata.file_type().is_symlink() {
            return Err(BackendError::UnsafeObject {
                path: path.to_owned(),
                reason: "only direct regular files are content-accessible",
            });
        }
        if metadata.nlink() != 1 {
            return Err(BackendError::UnsafeObject {
                path: path.to_owned(),
                reason: "multiply linked regular file is content-inaccessible",
            });
        }
        if identity(&metadata).volume != root_volume {
            return Err(BackendError::UnsafeObject {
                path: path.to_owned(),
                reason: "file crosses a volume boundary",
            });
        }
        Ok(metadata)
    }

    fn reopen_file(
        fixture: &Dir,
        pending: &PendingFile,
        root_volume: u64,
    ) -> Result<File, BackendError> {
        reopen_file_unchecked(fixture, pending, root_volume)
    }

    fn reopen_file_unchecked(
        fixture: &Dir,
        pending: &PendingFile,
        root_volume: u64,
    ) -> Result<File, BackendError> {
        let mut current = fixture
            .open_dir_nofollow(".")
            .map_err(|error| BackendError::io("duplicate fixture root", &pending.path, error))?;
        for component in &pending.components[..pending.components.len().saturating_sub(1)] {
            ensure_unique_portable_alias(&current, component, &pending.path)?;
            current = current.open_dir_nofollow(component).map_err(|error| {
                BackendError::io(
                    "reopen directory without following reparse points",
                    &pending.path,
                    error,
                )
            })?;
            let metadata = current.dir_metadata().map_err(|error| {
                BackendError::io("read reopened directory identity", &pending.path, error)
            })?;
            if identity(&metadata).volume != root_volume {
                return Err(BackendError::UnsafeObject {
                    path: pending.path.clone(),
                    reason: "directory crosses a volume boundary",
                });
            }
        }
        let name = pending
            .components
            .last()
            .ok_or_else(|| BackendError::Semantic("empty fixture file path".to_owned()))?;
        ensure_unique_portable_alias(&current, name, &pending.path)?;
        open_file_nofollow(&current, name, &pending.path)
    }

    fn enumerate_dir(
        dir: &Dir,
        components: &mut Vec<String>,
        files: &mut Vec<PendingFile>,
        directories: &mut u64,
        declared_bytes: &mut u64,
        budget: &WalkBudget,
        root_volume: u64,
    ) -> Result<(), BackendError> {
        let entries = dir.entries().map_err(|error| {
            BackendError::io(
                "enumerate fixture directory",
                portable_path(components),
                error,
            )
        })?;
        let mut aliases = BTreeSet::new();
        for entry in entries {
            let entry = entry.map_err(|error| {
                BackendError::io(
                    "read fixture directory entry",
                    portable_path(components),
                    error,
                )
            })?;
            let name = entry
                .file_name()
                .into_string()
                .map_err(|_| BackendError::UnsafeObject {
                    path: portable_path(components),
                    reason: "non-UTF-8 path component",
                })?;
            validate_component(&name)?;
            if !aliases.insert(portable_alias_key(&name)) {
                return Err(BackendError::UnsafeObject {
                    path: portable_path(components),
                    reason: "case or Unicode-normalization collision",
                });
            }
            components.push(name.clone());
            let path = portable_path(components);
            check_path_budget(components, &path, budget)?;
            let kind = entry
                .file_type()
                .map_err(|error| BackendError::io("inspect fixture entry type", &path, error))?;
            if kind.is_symlink() {
                return Err(BackendError::UnsafeObject {
                    path,
                    reason: "symbolic link or reparse point refused before content access",
                });
            } else if kind.is_dir() {
                *directories = directories.saturating_add(1);
                if *directories > budget.max_directories {
                    return Err(BackendError::ResourceLimit {
                        path,
                        limit: "max_directories",
                    });
                }
                let child = dir.open_dir_nofollow(&name).map_err(|error| {
                    BackendError::io(
                        "open directory without following reparse points",
                        &path,
                        error,
                    )
                })?;
                let metadata = child.dir_metadata().map_err(|error| {
                    BackendError::io("read opened directory identity", &path, error)
                })?;
                if identity(&metadata).volume != root_volume {
                    return Err(BackendError::UnsafeObject {
                        path,
                        reason: "directory crosses a volume boundary",
                    });
                }
                enumerate_dir(
                    &child,
                    components,
                    files,
                    directories,
                    declared_bytes,
                    budget,
                    root_volume,
                )?;
            } else if kind.is_file() {
                let file = open_file_nofollow(dir, &name, &path)?;
                let metadata = file
                    .metadata()
                    .map_err(|error| BackendError::io("stat opened project file", &path, error))?;
                let size = metadata.len();
                if size > budget.max_single_file_bytes {
                    return Err(BackendError::ResourceLimit {
                        path,
                        limit: "max_single_file_bytes",
                    });
                }
                if u64::try_from(files.len()).unwrap_or(u64::MAX) >= budget.max_files {
                    return Err(BackendError::ResourceLimit {
                        path,
                        limit: "max_files",
                    });
                }
                *declared_bytes = declared_bytes.checked_add(size).ok_or_else(|| {
                    BackendError::ResourceLimit {
                        path: path.clone(),
                        limit: "aggregate byte counter overflow",
                    }
                })?;
                if *declared_bytes > budget.max_bytes {
                    return Err(BackendError::ResourceLimit {
                        path,
                        limit: "max_bytes",
                    });
                }
                files.push(PendingFile {
                    components: components.clone(),
                    path,
                    size,
                    identity: identity(&metadata),
                });
            } else {
                return Err(BackendError::UnsafeObject {
                    path,
                    reason: "only directories and singly linked regular files are allowed",
                });
            }
            components.pop();
        }
        Ok(())
    }

    fn enumerate_metadata_windows(
        dir: &Dir,
        portable: &mut Vec<String>,
        opaque: &mut Vec<Vec<u8>>,
        result: &mut MetadataResult,
        budget: &MetadataBudget,
        root_volume: u64,
        control: &mut dyn FnMut() -> bool,
    ) -> Result<(), BackendError> {
        let entries = dir
            .entries()
            .map_err(|error| BackendError::io("enumerate project root", "<root>", error))?;
        let mut rows = Vec::new();
        for entry in entries {
            if !control() {
                return Err(BackendError::Cancelled);
            }
            if (result.entries.len() + rows.len()) as u64 >= budget.max_entries {
                return Err(BackendError::ResourceLimit {
                    path: "<root>".to_owned(),
                    limit: "max_entries",
                });
            }
            let entry =
                entry.map_err(|error| BackendError::io("read project entry", "<root>", error))?;
            let os = entry.file_name();
            let raw = os
                .encode_wide()
                .flat_map(u16::to_le_bytes)
                .collect::<Vec<_>>();
            let valid = os
                .to_str()
                .filter(|value| validate_component(value).is_ok())
                .map(str::to_owned);
            rows.push((entry, raw, valid));
        }
        let mut counts = std::collections::BTreeMap::new();
        for (_, _, valid) in &rows {
            if let Some(name) = valid {
                *counts.entry(portable_alias_key(name)).or_insert(0_u64) += 1;
            }
        }
        for (entry, raw, valid) in rows {
            if !control() {
                return Err(BackendError::Cancelled);
            }
            if result.entries.len() as u64 >= budget.max_entries {
                return Err(BackendError::ResourceLimit {
                    path: "<root>".to_owned(),
                    limit: "max_entries",
                });
            }
            let collision = valid.as_ref().is_some_and(|name| {
                counts.get(&portable_alias_key(name)).copied().unwrap_or(0) > 1
            });
            if valid.is_none() {
                check_opaque_path_budget(opaque, &raw, budget)?;
                let mut parts = opaque.clone();
                parts.push(raw);
                result.entries.push(MetadataEntry {
                    location: MetadataLocation::Opaque(parts),
                    kind: MetadataKind::Unavailable(UnavailableKind::NonPortableName),
                    token: None,
                });
                continue;
            }
            let name = valid.expect("checked above");
            portable.push(name.clone());
            opaque.push(raw);
            let path = portable.join("/");
            if path.len() as u64 > budget.max_path_bytes
                || name.len() as u64 > budget.max_component_bytes
            {
                return Err(BackendError::ResourceLimit {
                    path,
                    limit: "path budget",
                });
            }
            if collision {
                result.entries.push(MetadataEntry {
                    location: MetadataLocation::Portable(path),
                    kind: MetadataKind::Unavailable(UnavailableKind::AliasCollision),
                    token: None,
                });
                portable.pop();
                opaque.pop();
                continue;
            }
            let kind = entry
                .file_type()
                .map_err(|error| BackendError::io("inspect project entry", &path, error))?;
            if kind.is_symlink() {
                result.entries.push(MetadataEntry {
                    location: MetadataLocation::Portable(path),
                    kind: MetadataKind::Unavailable(UnavailableKind::LinkOrReparsePoint),
                    token: None,
                });
            } else if kind.is_dir() {
                result.directories += 1;
                if result.directories > budget.max_directories
                    || portable.len() as u64 > effective_max_depth(budget.max_depth)
                {
                    return Err(BackendError::ResourceLimit {
                        path,
                        limit: "directory budget",
                    });
                }
                let child = dir
                    .open_dir_nofollow(&name)
                    .map_err(|error| BackendError::io("open project directory", &path, error))?;
                let metadata = child
                    .dir_metadata()
                    .map_err(|error| BackendError::io("stat project directory", &path, error))?;
                if identity(&metadata).volume != root_volume {
                    result.entries.push(MetadataEntry {
                        location: MetadataLocation::Portable(path),
                        kind: MetadataKind::Unavailable(UnavailableKind::MountBoundary),
                        token: None,
                    });
                } else {
                    result.entries.push(MetadataEntry {
                        location: MetadataLocation::Portable(path),
                        kind: MetadataKind::Directory,
                        token: None,
                    });
                    enumerate_metadata_windows(
                        &child,
                        portable,
                        opaque,
                        result,
                        budget,
                        root_volume,
                        control,
                    )?;
                }
            } else if kind.is_file() {
                let file = open_file_nofollow(dir, &name, &path)?;
                let metadata = file
                    .metadata()
                    .map_err(|error| BackendError::io("stat opened project file", &path, error))?;
                let size = metadata.len();
                let unavailable = if metadata.nlink() != 1 {
                    Some(UnavailableKind::MultiplyLinkedFile)
                } else if identity(&metadata).volume != root_volume {
                    Some(UnavailableKind::MountBoundary)
                } else {
                    None
                };
                if unavailable.is_none() {
                    if size > budget.max_single_file_bytes {
                        return Err(BackendError::ResourceLimit {
                            path,
                            limit: "max_single_file_bytes",
                        });
                    }
                    result.declared_bytes =
                        result.declared_bytes.checked_add(size).ok_or_else(|| {
                            BackendError::ResourceLimit {
                                path: path.clone(),
                                limit: "declared byte overflow",
                            }
                        })?;
                    if result.declared_bytes > budget.max_declared_bytes {
                        return Err(BackendError::ResourceLimit {
                            path,
                            limit: "max_declared_bytes",
                        });
                    }
                }
                let (modified_seconds, modified_nanoseconds) = modified_token(&metadata, &path)?;
                result.entries.push(MetadataEntry {
                    location: MetadataLocation::Portable(path),
                    kind: unavailable
                        .map_or(MetadataKind::File { size }, MetadataKind::Unavailable),
                    token: unavailable.is_none().then_some(EntryToken::Windows {
                        volume: identity(&metadata).volume,
                        file: identity(&metadata).file,
                        size,
                        modified_seconds,
                        modified_nanoseconds,
                        opened: file,
                    }),
                });
            } else {
                result.entries.push(MetadataEntry {
                    location: MetadataLocation::Portable(path),
                    kind: MetadataKind::Unavailable(UnavailableKind::SpecialFile),
                    token: None,
                });
            }
            portable.pop();
            opaque.pop();
        }
        Ok(())
    }

    fn check_path_budget(
        components: &[String],
        path: &str,
        budget: &WalkBudget,
    ) -> Result<(), BackendError> {
        if components.len() as u64 > effective_max_depth(budget.max_depth) {
            return Err(BackendError::ResourceLimit {
                path: path.to_owned(),
                limit: "max_depth",
            });
        }
        if path.len() as u64 > budget.max_path_bytes {
            return Err(BackendError::ResourceLimit {
                path: path.to_owned(),
                limit: "max_path_bytes",
            });
        }
        if components.last().map_or(0, String::len) as u64 > budget.max_component_bytes {
            return Err(BackendError::ResourceLimit {
                path: path.to_owned(),
                limit: "max_component_bytes",
            });
        }
        Ok(())
    }

    fn portable_path(components: &[String]) -> String {
        components.join("/")
    }

    #[cfg(test)]
    mod tests {
        use super::*;
        use std::fs;
        use std::process::Command;

        fn budget() -> WalkBudget {
            WalkBudget {
                max_bytes: 1024 * 1024,
                max_files: 16,
                max_single_file_bytes: 1024 * 1024,
                max_directories: 16,
                max_depth: 8,
                max_path_bytes: 512,
                max_component_bytes: 128,
            }
        }

        fn metadata_budget() -> MetadataBudget {
            MetadataBudget {
                max_entries: 32,
                max_directories: 8,
                max_depth: 8,
                max_path_bytes: 512,
                max_component_bytes: 128,
                max_declared_bytes: 1024 * 1024,
                max_single_file_bytes: 1024 * 1024,
            }
        }

        fn junction(link: &Path, target: &Path) {
            let link = link.display().to_string().replace('\'', "''");
            let target = target.display().to_string().replace('\'', "''");
            let command =
                format!("New-Item -ItemType Junction -Path '{link}' -Target '{target}' | Out-Null");
            let output = Command::new("powershell.exe")
                .args(["-NoProfile", "-NonInteractive", "-Command"])
                .arg(command)
                .output()
                .unwrap();
            assert!(
                output.status.success(),
                "PowerShell junction creation failed: {}",
                String::from_utf8_lossy(&output.stderr)
            );
        }

        #[test]
        fn tolerant_metadata_continues_past_windows_hardlinks_and_junctions() {
            let root = tempfile::tempdir().unwrap();
            let outside = tempfile::tempdir().unwrap();
            fs::write(root.path().join("safe.bin"), b"safe").unwrap();
            fs::write(root.path().join("hard-a.bin"), b"hard").unwrap();
            fs::hard_link(
                root.path().join("hard-a.bin"),
                root.path().join("hard-b.bin"),
            )
            .unwrap();
            fs::write(outside.path().join("canary"), b"outside-secret").unwrap();
            junction(&root.path().join("junction"), outside.path());
            let backend = Backend::open(root.path()).unwrap();
            let result = backend.metadata(metadata_budget(), &mut || true).unwrap();
            assert!(result.entries.iter().any(|entry| matches!(&entry.location, MetadataLocation::Portable(path) if path == "safe.bin") && matches!(entry.kind, MetadataKind::File { .. })));
            assert_eq!(
                result
                    .entries
                    .iter()
                    .filter(|entry| matches!(entry.kind, MetadataKind::Unavailable(_)))
                    .count(),
                3
            );
        }

        #[test]
        fn inventories_regular_files_on_native_windows() {
            let temp = tempfile::tempdir().unwrap();
            fs::create_dir(temp.path().join("fixture")).unwrap();
            fs::write(temp.path().join("fixture/a.dat"), b"abc").unwrap();
            let inventory = Backend::open(temp.path())
                .unwrap()
                .inventory("fixture", budget())
                .unwrap();
            assert_eq!(inventory.files.len(), 1);
            assert_eq!(inventory.files[0].path, "a.dat");
        }

        #[test]
        fn rejects_superscript_device_aliases_before_native_windows_root_reads() {
            let temp = tempfile::tempdir().unwrap();
            let root = Backend::open(temp.path()).unwrap();
            for prefix in ["COM", "LPT"] {
                for digit in ['¹', '²', '³'] {
                    for suffix in ["", ".dat"] {
                        let name = format!("{prefix}{digit}{suffix}");
                        assert!(matches!(
                            root.read_component(&name, 1024),
                            Err(BackendError::UnsafeObject { .. })
                        ));
                    }
                }
            }
        }

        #[test]
        fn rejects_superscript_device_aliases_before_native_windows_nested_reads() {
            let temp = tempfile::tempdir().unwrap();
            let root = Backend::open(temp.path()).unwrap();
            for prefix in ["COM", "LPT"] {
                for digit in ['¹', '²', '³'] {
                    for suffix in ["", ".dat"] {
                        let path = format!("missing/{prefix}{digit}{suffix}");
                        assert!(matches!(
                            root.read_fixture_file("missing-fixture", &path, 1024),
                            Err(BackendError::UnsafeObject { .. })
                        ));
                    }
                }
            }
        }

        #[test]
        fn rejects_junction_without_observing_outside_body() {
            let temp = tempfile::tempdir().unwrap();
            fs::create_dir(temp.path().join("fixture")).unwrap();
            fs::create_dir(temp.path().join("outside")).unwrap();
            let marker = "WINDOWS_OUTSIDE_CANARY_NEVER_OBSERVED_9c72";
            fs::write(temp.path().join("outside/secret"), marker).unwrap();
            junction(
                &temp.path().join("fixture/link"),
                &temp.path().join("outside"),
            );
            let error = Backend::open(temp.path())
                .unwrap()
                .inventory("fixture", budget())
                .unwrap_err();
            let message = error.to_string();
            assert!(!message.contains(marker));
            assert!(!message.contains(&hex::encode(Sha256::digest(marker.as_bytes()))));
        }

        #[test]
        fn rejects_junction_swap_between_enumeration_and_hashing() {
            let temp = tempfile::tempdir().unwrap();
            fs::create_dir_all(temp.path().join("fixture/sub")).unwrap();
            fs::write(temp.path().join("fixture/sub/a.dat"), b"safe").unwrap();
            fs::create_dir(temp.path().join("outside")).unwrap();
            let marker = "WINDOWS_SWAP_CANARY_NEVER_OBSERVED_b86e";
            fs::write(temp.path().join("outside/a.dat"), marker).unwrap();
            let root = Backend::open(temp.path()).unwrap();
            let error = root
                .inventory_with_before_hash("fixture", budget(), || {
                    fs::rename(temp.path().join("fixture/sub"), temp.path().join("old-sub"))
                        .unwrap();
                    junction(
                        &temp.path().join("fixture/sub"),
                        &temp.path().join("outside"),
                    );
                })
                .unwrap_err();
            assert!(!error.to_string().contains(marker));
        }

        #[test]
        fn rejects_windows_hardlinks() {
            let temp = tempfile::tempdir().unwrap();
            fs::create_dir(temp.path().join("fixture")).unwrap();
            fs::write(temp.path().join("fixture/original"), b"hardlink-canary").unwrap();
            fs::hard_link(
                temp.path().join("fixture/original"),
                temp.path().join("fixture/alias"),
            )
            .unwrap();
            let error = Backend::open(temp.path())
                .unwrap()
                .inventory("fixture", budget())
                .unwrap_err();
            assert!(error.to_string().contains("multiply linked regular file"));
        }

        #[test]
        fn rejects_a_junction_as_the_explicit_root() {
            let temp = tempfile::tempdir().unwrap();
            fs::create_dir(temp.path().join("actual")).unwrap();
            junction(&temp.path().join("root-link"), &temp.path().join("actual"));
            let error = match Backend::open(&temp.path().join("root-link")) {
                Ok(_) => panic!("junction root was accepted"),
                Err(error) => error,
            };
            assert!(error.to_string().contains("reparse point"));
        }
    }
}

#[cfg(unix)]
mod unix {
    use super::*;
    use rustix::fd::OwnedFd;
    #[cfg(target_os = "linux")]
    use rustix::fs::fstatfs;
    use rustix::fs::{fstat, openat, statat, AtFlags, Dir, FileType, Mode, OFlags, Stat};
    use std::fs::File;

    #[derive(Debug, Clone, Copy, PartialEq, Eq)]
    struct Identity {
        device: u64,
        inode: u64,
    }

    impl Identity {
        fn from_stat(stat: &Stat) -> Self {
            Self {
                device: stat.st_dev,
                inode: stat.st_ino,
            }
        }
    }

    #[derive(Debug)]
    struct PendingFile {
        components: Vec<String>,
        path: String,
        size: u64,
        identity: Identity,
        change: ChangeStamp,
    }

    #[derive(Debug, Clone, Copy, PartialEq, Eq)]
    struct ChangeStamp {
        seconds: i64,
        nanoseconds: i64,
    }

    fn change_stamp(stat: &Stat) -> ChangeStamp {
        ChangeStamp {
            seconds: stat.st_ctime,
            nanoseconds: stat.st_ctime_nsec as i64,
        }
    }

    pub(super) struct Backend {
        fd: OwnedFd,
        identity: Identity,
        mount_id: u64,
        transient_race_detection: bool,
    }

    impl Backend {
        pub(super) fn transient_race_detection_available(&self) -> bool {
            self.transient_race_detection
        }

        pub(super) fn identity_tokens(&self) -> (u64, u64) {
            (self.identity.device, self.identity.inode)
        }

        pub(super) fn open(path: &Path) -> Result<Self, BackendError> {
            let fd = openat(
                rustix::fs::CWD,
                path,
                OFlags::RDONLY | OFlags::DIRECTORY | OFlags::NOFOLLOW | OFlags::CLOEXEC,
                Mode::empty(),
            )
            .map_err(|error| BackendError::io("open root without following links", path, error))?;
            let stat =
                fstat(&fd).map_err(|error| BackendError::io("stat opened root", path, error))?;
            let mount_id = mount_id(&fd)?;
            let transient_race_detection = transient_detection_for_opened_filesystem(&fd);
            Ok(Self {
                fd,
                identity: Identity::from_stat(&stat),
                mount_id,
                transient_race_detection,
            })
        }

        pub(super) fn component_exists(&self, name: &str) -> Result<bool, BackendError> {
            validate_component(name)?;
            ensure_unique_portable_alias(&self.fd, name, name)?;
            match statat(&self.fd, name, AtFlags::SYMLINK_NOFOLLOW) {
                Ok(_) => Ok(true),
                Err(rustix::io::Errno::NOENT) => Ok(false),
                Err(error) => Err(BackendError::io("inspect root component", name, error)),
            }
        }

        pub(super) fn read_component(
            &self,
            name: &str,
            max_bytes: u64,
        ) -> Result<Vec<u8>, BackendError> {
            self.read_component_with_before_open(name, max_bytes, || {})
        }

        fn read_component_with_before_open(
            &self,
            name: &str,
            max_bytes: u64,
            before_open: impl FnOnce(),
        ) -> Result<Vec<u8>, BackendError> {
            validate_component(name)?;
            ensure_unique_portable_alias(&self.fd, name, name)?;
            let metadata = statat(&self.fd, name, AtFlags::SYMLINK_NOFOLLOW)
                .map_err(|error| BackendError::io("lstat root component", name, error))?;
            ensure_regular_single_link(name, &metadata)?;
            let size =
                u64::try_from(metadata.st_size).map_err(|_| BackendError::ResourceLimit {
                    path: name.to_owned(),
                    limit: "negative file size",
                })?;
            if size > max_bytes {
                return Err(BackendError::ResourceLimit {
                    path: name.to_owned(),
                    limit: "bounded metadata-file read ceiling",
                });
            }
            before_open();
            let fd = openat(
                &self.fd,
                name,
                OFlags::RDONLY | OFlags::NOFOLLOW | OFlags::CLOEXEC,
                Mode::empty(),
            )
            .map_err(|error| {
                BackendError::io("open root component without following links", name, error)
            })?;
            let opened = fstat(&fd)
                .map_err(|error| BackendError::io("fstat root component", name, error))?;
            ensure_regular_single_link(name, &opened)?;
            if opened.st_dev != self.identity.device || crosses_mount(self.mount_id, mount_id(&fd)?)
            {
                return Err(BackendError::UnsafeObject {
                    path: name.to_owned(),
                    reason: "metadata file crosses a mount boundary",
                });
            }
            if Identity::from_stat(&opened) != Identity::from_stat(&metadata)
                || opened.st_size != metadata.st_size
                || change_stamp(&opened) != change_stamp(&metadata)
            {
                return Err(BackendError::Integrity {
                    path: name.to_owned(),
                    reason: "identity changed before read",
                });
            }
            let mut bytes = Vec::with_capacity(usize::try_from(size).unwrap_or(0));
            let mut file = File::from(fd);
            file.by_ref()
                .take(max_bytes.saturating_add(1))
                .read_to_end(&mut bytes)
                .map_err(|error| BackendError::io("read bounded root component", name, error))?;
            let after = file
                .metadata()
                .map_err(|error| BackendError::io("restat root component", name, error))?;
            use std::os::unix::fs::MetadataExt;
            if u64::try_from(bytes.len()).unwrap_or(u64::MAX) != size
                || after.nlink() != 1
                || after.dev() != opened.st_dev
                || after.ino() != opened.st_ino
                || after.size() != size
                || after.ctime() != opened.st_ctime
                || after.ctime_nsec() != opened.st_ctime_nsec as i64
            {
                return Err(BackendError::Integrity {
                    path: name.to_owned(),
                    reason: "size changed during read",
                });
            }
            Ok(bytes)
        }

        pub(super) fn inventory(
            &self,
            component: &str,
            budget: WalkBudget,
        ) -> Result<WalkResult, BackendError> {
            self.inventory_with_hooks(component, budget, || {}, || {})
        }

        pub(super) fn metadata(
            &self,
            budget: MetadataBudget,
            control: &mut dyn FnMut() -> bool,
        ) -> Result<MetadataResult, BackendError> {
            let mut result = MetadataResult {
                entries: Vec::new(),
                directories: 0,
                declared_bytes: 0,
            };
            enumerate_metadata_unix(
                &self.fd,
                &mut Vec::new(),
                &mut Vec::new(),
                &mut result,
                &budget,
                self.identity.device,
                self.mount_id,
                control,
            )?;
            result.entries.sort_by(|a, b| a.location.cmp(&b.location));
            Ok(result)
        }

        #[cfg(unix)]
        pub(super) fn read_bound(
            &self,
            path: &str,
            token: &EntryToken,
            max_bytes: u64,
            control: &mut dyn FnMut() -> bool,
        ) -> Result<Option<Vec<u8>>, BackendError> {
            let EntryToken::Unix {
                device,
                inode,
                size,
                change_seconds,
                change_nanoseconds,
            } = token;
            let components = path.split('/').collect::<Vec<_>>();
            if components.is_empty() {
                return Err(BackendError::Semantic("empty bound path".to_owned()));
            }
            for component in &components {
                validate_component(component)?;
            }
            let mut current = openat(
                &self.fd,
                ".",
                OFlags::RDONLY | OFlags::DIRECTORY | OFlags::CLOEXEC,
                Mode::empty(),
            )
            .map_err(|error| BackendError::io("duplicate project root", path, error))?;
            for component in &components[..components.len().saturating_sub(1)] {
                ensure_unique_portable_alias(&current, component, path)?;
                let next = openat(
                    &current,
                    *component,
                    OFlags::RDONLY | OFlags::DIRECTORY | OFlags::NOFOLLOW | OFlags::CLOEXEC,
                    Mode::empty(),
                )
                .map_err(|error| BackendError::io("open bound parent", path, error))?;
                let metadata = fstat(&next)
                    .map_err(|error| BackendError::io("stat bound parent", path, error))?;
                if metadata.st_dev != self.identity.device
                    || crosses_mount(self.mount_id, mount_id(&next)?)
                {
                    return Err(BackendError::UnsafeObject {
                        path: path.to_owned(),
                        reason: "bound parent crosses a mount boundary",
                    });
                }
                current = next;
            }
            let name = components.last().expect("checked non-empty");
            ensure_unique_portable_alias(&current, name, path)?;
            let before = statat(&current, *name, AtFlags::SYMLINK_NOFOLLOW)
                .map_err(|error| BackendError::io("lstat bound file", path, error))?;
            ensure_regular_single_link(path, &before)?;
            let expected_identity = Identity {
                device: *device,
                inode: *inode,
            };
            let expected_change = ChangeStamp {
                seconds: *change_seconds,
                nanoseconds: *change_nanoseconds,
            };
            if Identity::from_stat(&before) != expected_identity
                || u64::try_from(before.st_size).ok() != Some(*size)
                || change_stamp(&before) != expected_change
            {
                return Err(BackendError::Integrity {
                    path: path.to_owned(),
                    reason: "enumerated file identity, size, or change stamp changed before read",
                });
            }
            if *size > max_bytes {
                return Err(BackendError::ResourceLimit {
                    path: path.to_owned(),
                    limit: "max_single_file_bytes",
                });
            }
            let fd = openat(
                &current,
                *name,
                OFlags::RDONLY | OFlags::NOFOLLOW | OFlags::CLOEXEC,
                Mode::empty(),
            )
            .map_err(|error| BackendError::io("open bound file", path, error))?;
            let opened =
                fstat(&fd).map_err(|error| BackendError::io("fstat bound file", path, error))?;
            ensure_regular_single_link(path, &opened)?;
            if Identity::from_stat(&opened) != expected_identity
                || u64::try_from(opened.st_size).ok() != Some(*size)
                || change_stamp(&opened) != expected_change
                || opened.st_dev != self.identity.device
                || crosses_mount(self.mount_id, mount_id(&fd)?)
            {
                return Err(BackendError::Integrity {
                    path: path.to_owned(),
                    reason: "enumerated file changed while opening",
                });
            }
            let mut file = File::from(fd);
            let mut bytes = Vec::with_capacity(usize::try_from(*size).unwrap_or(0));
            let mut buffer = [0_u8; 64 * 1024];
            loop {
                if !control() {
                    return Ok(None);
                }
                let count = file
                    .read(&mut buffer)
                    .map_err(|error| BackendError::io("read bound file chunk", path, error))?;
                if count == 0 {
                    break;
                }
                bytes.extend_from_slice(&buffer[..count]);
                if bytes.len() as u64 > max_bytes {
                    return Err(BackendError::ResourceLimit {
                        path: path.to_owned(),
                        limit: "max_single_file_bytes",
                    });
                }
            }
            let after = file
                .metadata()
                .map_err(|error| BackendError::io("restat bound file", path, error))?;
            use std::os::unix::fs::MetadataExt;
            if after.nlink() != 1
                || after.dev() != *device
                || after.ino() != *inode
                || after.size() != *size
                || after.ctime() != *change_seconds
                || after.ctime_nsec() != *change_nanoseconds
                || bytes.len() as u64 != *size
            {
                return Err(BackendError::Integrity {
                    path: path.to_owned(),
                    reason: "enumerated file identity or size changed during read",
                });
            }
            Ok(Some(bytes))
        }

        #[cfg(test)]
        fn inventory_with_before_hash(
            &self,
            component: &str,
            budget: WalkBudget,
            before_hash: impl FnOnce(),
        ) -> Result<WalkResult, BackendError> {
            self.inventory_with_hooks(component, budget, before_hash, || {})
        }

        fn inventory_with_hooks(
            &self,
            component: &str,
            budget: WalkBudget,
            before_hash: impl FnOnce(),
            during_first_read: impl FnOnce(),
        ) -> Result<WalkResult, BackendError> {
            validate_component(component)?;
            ensure_unique_portable_alias(&self.fd, component, component)?;
            let before = statat(&self.fd, component, AtFlags::SYMLINK_NOFOLLOW)
                .map_err(|error| BackendError::io("lstat fixture root", component, error))?;
            if !FileType::from_raw_mode(before.st_mode).is_dir() {
                return Err(BackendError::UnsafeObject {
                    path: component.to_owned(),
                    reason: "fixture root is not a directory",
                });
            }
            let fixture_fd = openat(
                &self.fd,
                component,
                OFlags::RDONLY | OFlags::DIRECTORY | OFlags::NOFOLLOW | OFlags::CLOEXEC,
                Mode::empty(),
            )
            .map_err(|error| {
                BackendError::io(
                    "open fixture root without following links",
                    component,
                    error,
                )
            })?;
            let opened = fstat(&fixture_fd)
                .map_err(|error| BackendError::io("fstat fixture root", component, error))?;
            if Identity::from_stat(&before) != Identity::from_stat(&opened) {
                return Err(BackendError::Integrity {
                    path: component.to_owned(),
                    reason: "fixture root identity changed before enumeration",
                });
            }
            let fixture_mount = mount_id(&fixture_fd)?;
            if opened.st_dev != self.identity.device || crosses_mount(self.mount_id, fixture_mount)
            {
                return Err(BackendError::UnsafeObject {
                    path: component.to_owned(),
                    reason: "fixture root crosses a mount boundary",
                });
            }

            let mut pending = Vec::new();
            let mut directories = 0_u64;
            let mut declared_bytes = 0_u64;
            enumerate_dir(
                &fixture_fd,
                &mut Vec::new(),
                &mut pending,
                &mut directories,
                &mut declared_bytes,
                &budget,
                self.identity.device,
                fixture_mount,
            )?;
            if u64::try_from(pending.len()).unwrap_or(u64::MAX) > budget.max_files {
                return Err(BackendError::ResourceLimit {
                    path: component.to_owned(),
                    limit: "max_files",
                });
            }

            pending.sort_by(|a, b| a.path.as_bytes().cmp(b.path.as_bytes()));
            before_hash();
            let mut hashed_bytes = 0_u64;
            let mut files = Vec::with_capacity(pending.len());
            let mut during_first_read = Some(during_first_read);
            for file in pending {
                let (mut opened_file, stat) =
                    self.open_fixture_file(&fixture_fd, &file, fixture_mount)?;
                let mut hasher = Sha256::new();
                let mut copied = 0_u64;
                let mut buffer = [0_u8; 64 * 1024];
                loop {
                    let count = opened_file.read(&mut buffer).map_err(|error| {
                        BackendError::io("hash fixture file", &file.path, error)
                    })?;
                    if count == 0 {
                        break;
                    }
                    hasher.update(&buffer[..count]);
                    copied = copied.saturating_add(count as u64);
                    if let Some(hook) = during_first_read.take() {
                        hook();
                    }
                }
                let after = opened_file
                    .metadata()
                    .map_err(|error| BackendError::io("restat hashed file", &file.path, error))?;
                use std::os::unix::fs::MetadataExt;
                if after.nlink() != 1
                    || after.dev() != stat.st_dev
                    || after.ino() != stat.st_ino
                    || after.size() != file.size
                    || after.ctime() != stat.st_ctime
                    || after.ctime_nsec() != stat.st_ctime_nsec as i64
                    || copied != file.size
                {
                    return Err(BackendError::Integrity {
                        path: file.path,
                        reason: "file identity, link count, or size changed while hashing",
                    });
                }
                hashed_bytes = hashed_bytes.checked_add(file.size).ok_or_else(|| {
                    BackendError::ResourceLimit {
                        path: file.path.clone(),
                        limit: "aggregate byte counter overflow",
                    }
                })?;
                files.push(WalkFile {
                    path: file.path,
                    size: file.size,
                    sha256: hasher.finalize().into(),
                });
            }
            if hashed_bytes != declared_bytes {
                return Err(BackendError::Integrity {
                    path: component.to_owned(),
                    reason: "metadata and hashed byte totals differ",
                });
            }
            Ok(WalkResult {
                files,
                directories,
                bytes: declared_bytes,
            })
        }

        pub(super) fn read_fixture_file(
            &self,
            fixture: &str,
            path: &str,
            max_bytes: u64,
        ) -> Result<Vec<u8>, BackendError> {
            self.read_fixture_file_with_before_open(fixture, path, max_bytes, || {})
        }

        fn read_fixture_file_with_before_open(
            &self,
            fixture: &str,
            path: &str,
            max_bytes: u64,
            before_open: impl FnOnce(),
        ) -> Result<Vec<u8>, BackendError> {
            validate_component(fixture)?;
            let components = path.split('/').map(str::to_owned).collect::<Vec<_>>();
            if components.is_empty() {
                return Err(BackendError::Semantic("empty fixture file path".to_owned()));
            }
            for component in &components {
                validate_component(component)?;
            }
            ensure_unique_portable_alias(&self.fd, fixture, fixture)?;
            let fixture_fd = openat(
                &self.fd,
                fixture,
                OFlags::RDONLY | OFlags::DIRECTORY | OFlags::NOFOLLOW | OFlags::CLOEXEC,
                Mode::empty(),
            )
            .map_err(|error| {
                BackendError::io("open fixture root for canary validation", fixture, error)
            })?;
            let fixture_stat = fstat(&fixture_fd).map_err(|error| {
                BackendError::io("fstat fixture root for canary validation", fixture, error)
            })?;
            let fixture_mount = mount_id(&fixture_fd)?;
            if fixture_stat.st_dev != self.identity.device
                || crosses_mount(self.mount_id, fixture_mount)
            {
                return Err(BackendError::UnsafeObject {
                    path: fixture.to_owned(),
                    reason: "fixture root crosses a mount boundary",
                });
            }
            let pending = PendingFile {
                components,
                path: path.to_owned(),
                size: 0,
                identity: Identity {
                    device: 0,
                    inode: 0,
                },
                change: ChangeStamp {
                    seconds: 0,
                    nanoseconds: 0,
                },
            };
            let mut current = openat(
                &fixture_fd,
                ".",
                OFlags::RDONLY | OFlags::DIRECTORY | OFlags::CLOEXEC,
                Mode::empty(),
            )
            .map_err(|error| {
                BackendError::io("duplicate fixture root for canary validation", path, error)
            })?;
            for component in &pending.components[..pending.components.len().saturating_sub(1)] {
                ensure_unique_portable_alias(&current, component, path)?;
                let next = openat(
                    &current,
                    component,
                    OFlags::RDONLY | OFlags::DIRECTORY | OFlags::NOFOLLOW | OFlags::CLOEXEC,
                    Mode::empty(),
                )
                .map_err(|error| {
                    BackendError::io("open canary parent without following links", path, error)
                })?;
                let stat = fstat(&next)
                    .map_err(|error| BackendError::io("fstat canary parent", path, error))?;
                if stat.st_dev != self.identity.device
                    || crosses_mount(fixture_mount, mount_id(&next)?)
                {
                    return Err(BackendError::UnsafeObject {
                        path: path.to_owned(),
                        reason: "canary parent crosses a mount boundary",
                    });
                }
                current = next;
            }
            let name = pending.components.last().expect("checked non-empty");
            ensure_unique_portable_alias(&current, name, path)?;
            let before = statat(&current, name, AtFlags::SYMLINK_NOFOLLOW)
                .map_err(|error| BackendError::io("lstat canary-bearing file", path, error))?;
            ensure_regular_single_link(path, &before)?;
            let size = u64::try_from(before.st_size).map_err(|_| BackendError::ResourceLimit {
                path: path.to_owned(),
                limit: "negative file size",
            })?;
            if size > max_bytes {
                return Err(BackendError::ResourceLimit {
                    path: path.to_owned(),
                    limit: "max_single_file_bytes",
                });
            }
            before_open();
            let fd = openat(
                &current,
                name,
                OFlags::RDONLY | OFlags::NOFOLLOW | OFlags::CLOEXEC,
                Mode::empty(),
            )
            .map_err(|error| {
                BackendError::io(
                    "open canary-bearing file without following links",
                    path,
                    error,
                )
            })?;
            let opened = fstat(&fd)
                .map_err(|error| BackendError::io("fstat canary-bearing file", path, error))?;
            ensure_regular_single_link(path, &opened)?;
            if opened.st_dev != self.identity.device || crosses_mount(fixture_mount, mount_id(&fd)?)
            {
                return Err(BackendError::UnsafeObject {
                    path: path.to_owned(),
                    reason: "canary-bearing file crosses a mount boundary",
                });
            }
            if Identity::from_stat(&opened) != Identity::from_stat(&before)
                || opened.st_size != before.st_size
                || change_stamp(&opened) != change_stamp(&before)
            {
                return Err(BackendError::Integrity {
                    path: path.to_owned(),
                    reason: "canary-bearing file changed before read",
                });
            }
            let mut bytes = Vec::with_capacity(usize::try_from(size).unwrap_or(0));
            let mut file = File::from(fd);
            file.by_ref()
                .take(max_bytes.saturating_add(1))
                .read_to_end(&mut bytes)
                .map_err(|error| {
                    BackendError::io("read bounded canary-bearing file", path, error)
                })?;
            let after = file
                .metadata()
                .map_err(|error| BackendError::io("restat canary-bearing file", path, error))?;
            use std::os::unix::fs::MetadataExt;
            if u64::try_from(bytes.len()).unwrap_or(u64::MAX) != size
                || after.nlink() != 1
                || after.dev() != opened.st_dev
                || after.ino() != opened.st_ino
                || after.size() != size
                || after.ctime() != opened.st_ctime
                || after.ctime_nsec() != opened.st_ctime_nsec as i64
            {
                return Err(BackendError::Integrity {
                    path: path.to_owned(),
                    reason: "canary-bearing file size changed during read",
                });
            }
            Ok(bytes)
        }

        fn open_fixture_file(
            &self,
            fixture_fd: &OwnedFd,
            pending: &PendingFile,
            fixture_mount: u64,
        ) -> Result<(File, Stat), BackendError> {
            let mut current = openat(
                fixture_fd,
                ".",
                OFlags::RDONLY | OFlags::DIRECTORY | OFlags::CLOEXEC,
                Mode::empty(),
            )
            .map_err(|error| BackendError::io("duplicate fixture root", &pending.path, error))?;
            for component in &pending.components[..pending.components.len().saturating_sub(1)] {
                ensure_unique_portable_alias(&current, component, &pending.path)?;
                let next = openat(
                    &current,
                    component,
                    OFlags::RDONLY | OFlags::DIRECTORY | OFlags::NOFOLLOW | OFlags::CLOEXEC,
                    Mode::empty(),
                )
                .map_err(|error| {
                    BackendError::io("reopen fixture directory", &pending.path, error)
                })?;
                let stat = fstat(&next).map_err(|error| {
                    BackendError::io("fstat reopened directory", &pending.path, error)
                })?;
                if stat.st_dev != self.identity.device
                    || crosses_mount(fixture_mount, mount_id(&next)?)
                {
                    return Err(BackendError::UnsafeObject {
                        path: pending.path.clone(),
                        reason: "directory changed into a mount boundary",
                    });
                }
                current = next;
            }
            let name = pending
                .components
                .last()
                .ok_or_else(|| BackendError::Semantic("empty fixture file path".to_owned()))?;
            ensure_unique_portable_alias(&current, name, &pending.path)?;
            let fd = openat(
                &current,
                name,
                OFlags::RDONLY | OFlags::NOFOLLOW | OFlags::CLOEXEC,
                Mode::empty(),
            )
            .map_err(|error| {
                BackendError::io(
                    "open fixture file without following links",
                    &pending.path,
                    error,
                )
            })?;
            let stat = fstat(&fd).map_err(|error| {
                BackendError::io("fstat opened fixture file", &pending.path, error)
            })?;
            ensure_regular_single_link(&pending.path, &stat)?;
            if stat.st_dev != self.identity.device || crosses_mount(fixture_mount, mount_id(&fd)?) {
                return Err(BackendError::UnsafeObject {
                    path: pending.path.clone(),
                    reason: "file crosses a mount boundary",
                });
            }
            if Identity::from_stat(&stat) != pending.identity
                || u64::try_from(stat.st_size).ok() != Some(pending.size)
                || change_stamp(&stat) != pending.change
            {
                return Err(BackendError::Integrity {
                    path: pending.path.clone(),
                    reason: "file identity changed before hashing",
                });
            }
            Ok((File::from(fd), stat))
        }
    }

    #[cfg(target_os = "linux")]
    const EXT_FAMILY_SUPER_MAGIC: u64 = 0xEF53;

    #[cfg(target_os = "linux")]
    fn transient_detection_for_filesystem_magic(magic: u64) -> bool {
        magic == EXT_FAMILY_SUPER_MAGIC
    }

    fn transient_detection_for_opened_filesystem(fd: &OwnedFd) -> bool {
        #[cfg(target_os = "linux")]
        {
            fstatfs(fd)
                .map(|stat| transient_detection_for_filesystem_magic(stat.f_type as u64))
                .unwrap_or(false)
        }
        #[cfg(not(target_os = "linux"))]
        {
            let _ = fd;
            false
        }
    }

    fn ensure_unique_portable_alias(
        fd: &OwnedFd,
        selected: &str,
        path: &str,
    ) -> Result<(), BackendError> {
        let selected_key = portable_alias_key(selected);
        let mut matching = 0_u8;
        let mut exact = 0_u8;
        let mut dir = Dir::read_from(fd)
            .map_err(|error| BackendError::io("enumerate selected path parent", path, error))?;
        while let Some(entry) = dir.read() {
            let entry = entry.map_err(|error| {
                BackendError::io("read selected path parent entry", path, error)
            })?;
            let name_bytes = entry.file_name().to_bytes();
            if name_bytes == b"." || name_bytes == b".." {
                continue;
            }
            let Ok(name) = std::str::from_utf8(name_bytes) else {
                continue;
            };
            if validate_component(name).is_err() {
                continue;
            }
            if portable_alias_key(name) == selected_key {
                matching = matching.saturating_add(1);
                if name == selected {
                    exact = exact.saturating_add(1);
                }
            }
        }
        if matching > 1 || (matching == 1 && exact != 1) {
            return Err(BackendError::UnsafeObject {
                path: path.to_owned(),
                reason: "selected path has a case or Unicode-normalization alias collision",
            });
        }
        Ok(())
    }

    fn enumerate_dir(
        fd: &OwnedFd,
        components: &mut Vec<String>,
        files: &mut Vec<PendingFile>,
        directories: &mut u64,
        declared_bytes: &mut u64,
        budget: &WalkBudget,
        root_device: u64,
        root_mount: u64,
    ) -> Result<(), BackendError> {
        let mut dir = Dir::read_from(fd).map_err(|error| {
            BackendError::io("enumerate directory", portable_path(components), error)
        })?;
        let mut aliases = BTreeSet::new();
        while let Some(entry) = dir.read() {
            let entry = entry.map_err(|error| {
                BackendError::io("read directory entry", portable_path(components), error)
            })?;
            let name_bytes = entry.file_name().to_bytes();
            if name_bytes == b"." || name_bytes == b".." {
                continue;
            }
            let name = std::str::from_utf8(name_bytes).map_err(|_| BackendError::UnsafeObject {
                path: portable_path(components),
                reason: "filesystem name is not portable UTF-8",
            })?;
            validate_component(name)?;
            if !aliases.insert(portable_alias_key(name)) {
                return Err(BackendError::UnsafeObject {
                    path: portable_path(components),
                    reason: "case or Unicode-normalization collision",
                });
            }
            if u64::try_from(name.len()).unwrap_or(u64::MAX) > budget.max_component_bytes {
                return Err(BackendError::ResourceLimit {
                    path: name.to_owned(),
                    limit: "max_component_bytes",
                });
            }
            components.push(name.to_owned());
            let path = portable_path(components);
            if u64::try_from(path.len()).unwrap_or(u64::MAX) > budget.max_path_bytes {
                return Err(BackendError::ResourceLimit {
                    path,
                    limit: "max_path_bytes",
                });
            }
            let stat = statat(fd, entry.file_name(), AtFlags::SYMLINK_NOFOLLOW)
                .map_err(|error| BackendError::io("lstat directory entry", &path, error))?;
            let kind = FileType::from_raw_mode(stat.st_mode);
            if kind.is_symlink() {
                return Err(BackendError::UnsafeObject {
                    path,
                    reason: "symbolic link refused without reading target",
                });
            }
            if kind.is_dir() {
                *directories =
                    directories
                        .checked_add(1)
                        .ok_or_else(|| BackendError::ResourceLimit {
                            path: path.clone(),
                            limit: "directory counter overflow",
                        })?;
                if *directories > budget.max_directories {
                    return Err(BackendError::ResourceLimit {
                        path,
                        limit: "max_directories",
                    });
                }
                if u64::try_from(components.len()).unwrap_or(u64::MAX)
                    > effective_max_depth(budget.max_depth)
                {
                    return Err(BackendError::ResourceLimit {
                        path,
                        limit: "max_depth",
                    });
                }
                let child = openat(
                    fd,
                    entry.file_name(),
                    OFlags::RDONLY | OFlags::DIRECTORY | OFlags::NOFOLLOW | OFlags::CLOEXEC,
                    Mode::empty(),
                )
                .map_err(|error| {
                    BackendError::io("open child directory without following links", &path, error)
                })?;
                let opened = fstat(&child)
                    .map_err(|error| BackendError::io("fstat child directory", &path, error))?;
                if Identity::from_stat(&opened) != Identity::from_stat(&stat) {
                    return Err(BackendError::Integrity {
                        path,
                        reason: "directory identity changed during enumeration",
                    });
                }
                if opened.st_dev != root_device || crosses_mount(root_mount, mount_id(&child)?) {
                    return Err(BackendError::UnsafeObject {
                        path,
                        reason: "mount boundary refused",
                    });
                }
                enumerate_dir(
                    &child,
                    components,
                    files,
                    directories,
                    declared_bytes,
                    budget,
                    root_device,
                    root_mount,
                )?;
            } else if kind.is_file() {
                ensure_regular_single_link(&path, &stat)?;
                let size =
                    u64::try_from(stat.st_size).map_err(|_| BackendError::ResourceLimit {
                        path: path.clone(),
                        limit: "negative file size",
                    })?;
                if size > budget.max_single_file_bytes {
                    return Err(BackendError::ResourceLimit {
                        path,
                        limit: "max_single_file_bytes",
                    });
                }
                if u64::try_from(files.len()).unwrap_or(u64::MAX) >= budget.max_files {
                    return Err(BackendError::ResourceLimit {
                        path,
                        limit: "max_files",
                    });
                }
                *declared_bytes = declared_bytes.checked_add(size).ok_or_else(|| {
                    BackendError::ResourceLimit {
                        path: path.clone(),
                        limit: "aggregate byte counter overflow",
                    }
                })?;
                if *declared_bytes > budget.max_bytes {
                    return Err(BackendError::ResourceLimit {
                        path,
                        limit: "max_bytes",
                    });
                }
                files.push(PendingFile {
                    components: components.clone(),
                    path,
                    size,
                    identity: Identity::from_stat(&stat),
                    change: change_stamp(&stat),
                });
            } else {
                return Err(BackendError::UnsafeObject {
                    path,
                    reason: "only directories and singly linked regular files are allowed",
                });
            }
            components.pop();
        }
        Ok(())
    }

    fn enumerate_metadata_unix(
        fd: &OwnedFd,
        portable: &mut Vec<String>,
        raw_parent: &mut Vec<Vec<u8>>,
        result: &mut MetadataResult,
        budget: &MetadataBudget,
        root_device: u64,
        root_mount: u64,
        control: &mut dyn FnMut() -> bool,
    ) -> Result<(), BackendError> {
        let mut dir = Dir::read_from(fd)
            .map_err(|error| BackendError::io("enumerate project root", "<root>", error))?;
        let mut rows = Vec::new();
        while let Some(entry) = dir.read() {
            if !control() {
                return Err(BackendError::Cancelled);
            }
            if (result.entries.len() + rows.len()) as u64 >= budget.max_entries {
                return Err(BackendError::ResourceLimit {
                    path: "<root>".to_owned(),
                    limit: "max_entries",
                });
            }
            let entry =
                entry.map_err(|error| BackendError::io("read project entry", "<root>", error))?;
            let raw = entry.file_name().to_bytes().to_vec();
            if raw == b"." || raw == b".." {
                continue;
            }
            let valid = std::str::from_utf8(&raw)
                .ok()
                .filter(|value| validate_component(value).is_ok())
                .map(str::to_owned);
            rows.push((raw, valid));
        }
        let mut counts = std::collections::BTreeMap::new();
        for (_, valid) in &rows {
            if let Some(name) = valid {
                *counts.entry(portable_alias_key(name)).or_insert(0_u64) += 1;
            }
        }
        for (raw, valid) in rows {
            if !control() {
                return Err(BackendError::Cancelled);
            }
            if result.entries.len() as u64 >= budget.max_entries {
                return Err(BackendError::ResourceLimit {
                    path: "<root>".to_owned(),
                    limit: "max_entries",
                });
            }
            let collision = valid.as_ref().is_some_and(|name| {
                counts.get(&portable_alias_key(name)).copied().unwrap_or(0) > 1
            });
            if valid.is_none() {
                check_opaque_path_budget(raw_parent, &raw, budget)?;
                let mut components = raw_parent.clone();
                components.push(raw);
                result.entries.push(MetadataEntry {
                    location: MetadataLocation::Opaque(components),
                    kind: MetadataKind::Unavailable(UnavailableKind::NonPortableName),
                    token: None,
                });
                continue;
            }
            let name = valid.expect("checked above");
            portable.push(name.clone());
            raw_parent.push(raw.clone());
            let path = portable.join("/");
            if path.len() as u64 > budget.max_path_bytes
                || name.len() as u64 > budget.max_component_bytes
            {
                return Err(BackendError::ResourceLimit {
                    path,
                    limit: "path budget",
                });
            }
            if collision {
                result.entries.push(MetadataEntry {
                    location: MetadataLocation::Portable(path),
                    kind: MetadataKind::Unavailable(UnavailableKind::AliasCollision),
                    token: None,
                });
                portable.pop();
                raw_parent.pop();
                continue;
            }
            let cname = std::ffi::CString::new(raw).map_err(|_| BackendError::UnsafeObject {
                path: path.clone(),
                reason: "NUL in filesystem name",
            })?;
            let stat = statat(fd, cname.as_c_str(), AtFlags::SYMLINK_NOFOLLOW)
                .map_err(|error| BackendError::io("lstat project entry", &path, error))?;
            let kind = FileType::from_raw_mode(stat.st_mode);
            if kind.is_symlink() {
                result.entries.push(MetadataEntry {
                    location: MetadataLocation::Portable(path),
                    kind: MetadataKind::Unavailable(UnavailableKind::LinkOrReparsePoint),
                    token: None,
                });
            } else if kind.is_dir() {
                result.directories += 1;
                if result.directories > budget.max_directories
                    || portable.len() as u64 > effective_max_depth(budget.max_depth)
                {
                    return Err(BackendError::ResourceLimit {
                        path,
                        limit: "directory budget",
                    });
                }
                let child = openat(
                    fd,
                    cname.as_c_str(),
                    OFlags::RDONLY | OFlags::DIRECTORY | OFlags::NOFOLLOW | OFlags::CLOEXEC,
                    Mode::empty(),
                )
                .map_err(|error| BackendError::io("open project directory", &path, error))?;
                let opened = fstat(&child)
                    .map_err(|error| BackendError::io("fstat project directory", &path, error))?;
                if Identity::from_stat(&opened) != Identity::from_stat(&stat) {
                    return Err(BackendError::Integrity {
                        path,
                        reason: "directory identity changed during metadata enumeration",
                    });
                }
                let child_mount = mount_id(&child)?;
                if opened.st_dev != root_device || crosses_mount(root_mount, child_mount) {
                    result.entries.push(MetadataEntry {
                        location: MetadataLocation::Portable(path),
                        kind: MetadataKind::Unavailable(UnavailableKind::MountBoundary),
                        token: None,
                    });
                } else {
                    result.entries.push(MetadataEntry {
                        location: MetadataLocation::Portable(path),
                        kind: MetadataKind::Directory,
                        token: None,
                    });
                    enumerate_metadata_unix(
                        &child,
                        portable,
                        raw_parent,
                        result,
                        budget,
                        root_device,
                        root_mount,
                        control,
                    )?;
                }
            } else if kind.is_file() {
                let size =
                    u64::try_from(stat.st_size).map_err(|_| BackendError::ResourceLimit {
                        path: path.clone(),
                        limit: "negative file size",
                    })?;
                let entry_kind = if stat.st_nlink != 1 {
                    MetadataKind::Unavailable(UnavailableKind::MultiplyLinkedFile)
                } else if stat.st_dev != root_device {
                    MetadataKind::Unavailable(UnavailableKind::MountBoundary)
                } else {
                    MetadataKind::File { size }
                };
                if matches!(entry_kind, MetadataKind::File { .. }) {
                    if size > budget.max_single_file_bytes {
                        return Err(BackendError::ResourceLimit {
                            path,
                            limit: "max_single_file_bytes",
                        });
                    }
                    result.declared_bytes =
                        result.declared_bytes.checked_add(size).ok_or_else(|| {
                            BackendError::ResourceLimit {
                                path: path.clone(),
                                limit: "declared byte overflow",
                            }
                        })?;
                    if result.declared_bytes > budget.max_declared_bytes {
                        return Err(BackendError::ResourceLimit {
                            path,
                            limit: "max_declared_bytes",
                        });
                    }
                }
                result.entries.push(MetadataEntry {
                    location: MetadataLocation::Portable(path),
                    kind: entry_kind,
                    token: matches!(entry_kind, MetadataKind::File { .. }).then_some(
                        EntryToken::Unix {
                            device: stat.st_dev,
                            inode: stat.st_ino,
                            size,
                            change_seconds: stat.st_ctime,
                            change_nanoseconds: stat.st_ctime_nsec as i64,
                        },
                    ),
                });
            } else {
                result.entries.push(MetadataEntry {
                    location: MetadataLocation::Portable(path),
                    kind: MetadataKind::Unavailable(UnavailableKind::SpecialFile),
                    token: None,
                });
            }
            portable.pop();
            raw_parent.pop();
        }
        Ok(())
    }

    fn ensure_regular_single_link(path: &str, stat: &Stat) -> Result<(), BackendError> {
        if !FileType::from_raw_mode(stat.st_mode).is_file() {
            return Err(BackendError::UnsafeObject {
                path: path.to_owned(),
                reason: "not a regular file",
            });
        }
        if stat.st_nlink != 1 {
            return Err(BackendError::UnsafeObject {
                path: path.to_owned(),
                reason: "multiply linked regular file is content-inaccessible",
            });
        }
        Ok(())
    }

    fn portable_path(components: &[String]) -> String {
        components.join("/")
    }

    fn crosses_mount(expected: u64, observed: u64) -> bool {
        expected != observed
    }

    #[cfg(target_os = "linux")]
    fn mount_id(fd: &OwnedFd) -> Result<u64, BackendError> {
        use rustix::fs::{statx, StatxFlags};
        let observed = match statx(
            fd,
            c"",
            AtFlags::EMPTY_PATH | AtFlags::NO_AUTOMOUNT,
            StatxFlags::MNT_ID,
        ) {
            Ok(stat) if stat.stx_mask & StatxFlags::MNT_ID.bits() != 0 => Some(stat.stx_mnt_id),
            Ok(_) | Err(rustix::io::Errno::NOSYS) | Err(rustix::io::Errno::NOTSUP) => None,
            Err(error) => {
                return Err(BackendError::io(
                    "read mount identity",
                    "<opened-directory>",
                    error,
                ))
            }
        };
        require_mount_id(observed)
    }

    #[cfg(not(target_os = "linux"))]
    fn mount_id(_fd: &OwnedFd) -> Result<u64, BackendError> {
        require_mount_id(None)
    }

    fn require_mount_id(observed: Option<u64>) -> Result<u64, BackendError> {
        observed.ok_or(BackendError::UnsupportedPlatform(
            "filesystem mount identity is unavailable; no-follow scanning fails closed",
        ))
    }

    #[cfg(test)]
    mod tests {
        use super::*;
        use std::fs;

        fn budget() -> WalkBudget {
            WalkBudget {
                max_bytes: 1024,
                max_files: 8,
                max_single_file_bytes: 512,
                max_directories: 8,
                max_depth: 4,
                max_path_bytes: 128,
                max_component_bytes: 32,
            }
        }

        #[cfg(target_os = "linux")]
        #[test]
        fn only_the_proved_filesystem_family_advertises_transient_detection() {
            assert!(transient_detection_for_filesystem_magic(
                EXT_FAMILY_SUPER_MAGIC
            ));
            assert!(!transient_detection_for_filesystem_magic(0));
            assert!(!transient_detection_for_filesystem_magic(0x5846_5342));
        }

        #[cfg(target_os = "linux")]
        #[test]
        fn opened_unproved_filesystem_reports_transient_detection_unavailable() {
            let shared_memory = Path::new("/dev/shm");
            if !shared_memory.is_dir() {
                eprintln!("SKIP: /dev/shm is unavailable for an unproved-filesystem fixture");
                return;
            }
            let temp = tempfile::Builder::new()
                .prefix("rcce-unproved-filesystem-")
                .tempdir_in(shared_memory)
                .unwrap();
            let backend = Backend::open(temp.path()).unwrap();
            let stat = fstatfs(&backend.fd).unwrap();
            if transient_detection_for_filesystem_magic(stat.f_type as u64) {
                eprintln!("SKIP: /dev/shm unexpectedly uses the proved filesystem family");
                return;
            }
            assert!(!backend.transient_race_detection_available());
        }

        #[test]
        fn inventories_and_hashes_regular_files() {
            let temp = tempfile::tempdir().unwrap();
            fs::create_dir(temp.path().join("fixture")).unwrap();
            fs::write(temp.path().join("fixture/a.dat"), b"abc").unwrap();
            let inventory = Backend::open(temp.path())
                .unwrap()
                .inventory("fixture", budget())
                .unwrap();
            assert_eq!(inventory.files.len(), 1);
            assert_eq!(inventory.files[0].path, "a.dat");
            assert_eq!(inventory.bytes, 3);
        }

        #[test]
        fn rejects_symlink_without_disclosing_outside_canary() {
            use std::os::unix::fs::symlink;
            use std::time::{Duration, UNIX_EPOCH};
            let temp = tempfile::tempdir().unwrap();
            fs::create_dir(temp.path().join("fixture")).unwrap();
            let marker = "OUTSIDE_CANARY_NEVER_OBSERVED_5f793a";
            let outside = temp.path().join("outside-secret");
            fs::write(&outside, marker).unwrap();
            let sentinel_time = UNIX_EPOCH + Duration::from_secs(946_684_800);
            fs::File::options()
                .write(true)
                .open(&outside)
                .unwrap()
                .set_times(
                    fs::FileTimes::new()
                        .set_accessed(sentinel_time)
                        .set_modified(sentinel_time),
                )
                .unwrap();
            let accessed_before = fs::metadata(&outside).unwrap().accessed().unwrap();
            symlink(&outside, temp.path().join("fixture/link")).unwrap();
            let error = Backend::open(temp.path())
                .unwrap()
                .inventory("fixture", budget())
                .unwrap_err();
            let message = error.to_string();
            assert!(message.contains("symbolic link refused"));
            assert!(!message.contains(marker));
            assert!(!message.contains(&hex::encode(Sha256::digest(marker.as_bytes()))));
            assert_eq!(
                fs::metadata(&outside).unwrap().accessed().unwrap(),
                accessed_before
            );
        }

        #[test]
        fn rejects_hardlinks_inside_or_outside_root() {
            for outside in [false, true] {
                let temp = tempfile::tempdir().unwrap();
                fs::create_dir(temp.path().join("fixture")).unwrap();
                let original = if outside {
                    temp.path().join("outside")
                } else {
                    temp.path().join("fixture/original")
                };
                fs::write(&original, b"hardlink-canary").unwrap();
                fs::hard_link(&original, temp.path().join("fixture/alias")).unwrap();
                let error = Backend::open(temp.path())
                    .unwrap()
                    .inventory("fixture", budget())
                    .unwrap_err();
                assert!(error.to_string().contains("multiply linked regular file"));
            }
        }

        #[test]
        fn rejects_file_swapped_to_outside_symlink_before_hashing() {
            use std::os::unix::fs::symlink;
            let temp = tempfile::tempdir().unwrap();
            fs::create_dir(temp.path().join("fixture")).unwrap();
            fs::write(temp.path().join("fixture/a.dat"), b"safe").unwrap();
            fs::write(temp.path().join("outside"), b"swap-canary").unwrap();
            let root = Backend::open(temp.path()).unwrap();
            let error = root
                .inventory_with_before_hash("fixture", budget(), || {
                    fs::remove_file(temp.path().join("fixture/a.dat")).unwrap();
                    symlink(
                        temp.path().join("outside"),
                        temp.path().join("fixture/a.dat"),
                    )
                    .unwrap();
                })
                .unwrap_err();
            assert!(matches!(
                error,
                BackendError::Io { .. }
                    | BackendError::UnsafeObject { .. }
                    | BackendError::Integrity { .. }
            ));
            assert!(!error.to_string().contains("swap-canary"));
        }

        #[test]
        fn rejects_directory_swapped_to_outside_symlink_before_hashing() {
            use std::os::unix::fs::symlink;
            let temp = tempfile::tempdir().unwrap();
            fs::create_dir_all(temp.path().join("fixture/sub")).unwrap();
            fs::write(temp.path().join("fixture/sub/a.dat"), b"safe").unwrap();
            fs::create_dir(temp.path().join("outside")).unwrap();
            fs::write(temp.path().join("outside/a.dat"), b"directory-swap-canary").unwrap();
            let root = Backend::open(temp.path()).unwrap();
            let error = root
                .inventory_with_before_hash("fixture", budget(), || {
                    fs::rename(temp.path().join("fixture/sub"), temp.path().join("old-sub"))
                        .unwrap();
                    symlink(temp.path().join("outside"), temp.path().join("fixture/sub")).unwrap();
                })
                .unwrap_err();
            assert!(matches!(
                error,
                BackendError::Io { .. }
                    | BackendError::UnsafeObject { .. }
                    | BackendError::Integrity { .. }
            ));
            assert!(!error.to_string().contains("directory-swap-canary"));
        }

        #[test]
        fn rejects_hardlink_created_between_enumeration_and_hashing() {
            let temp = tempfile::tempdir().unwrap();
            fs::create_dir(temp.path().join("fixture")).unwrap();
            fs::write(temp.path().join("fixture/a.dat"), b"safe").unwrap();
            let root = Backend::open(temp.path()).unwrap();
            let error = root
                .inventory_with_before_hash("fixture", budget(), || {
                    fs::hard_link(
                        temp.path().join("fixture/a.dat"),
                        temp.path().join("outside-alias"),
                    )
                    .unwrap();
                })
                .unwrap_err();
            assert!(error.to_string().contains("multiply linked regular file"));
        }

        #[test]
        fn rejects_transient_hardlink_between_control_stat_and_open() {
            let temp = tempfile::tempdir().unwrap();
            let source = temp.path().join("manifest.toml");
            let alias = temp.path().join("transient-control-alias");
            fs::write(&source, b"control-bytes").unwrap();
            let root = Backend::open(temp.path()).unwrap();
            let error = root
                .read_component_with_before_open("manifest.toml", 1024, || {
                    fs::hard_link(&source, &alias).unwrap();
                    fs::remove_file(&alias).unwrap();
                })
                .unwrap_err();
            assert!(error.to_string().contains("identity changed before read"));
            assert!(!alias.exists());
        }

        #[test]
        fn rejects_transient_hardlink_between_fixture_stat_and_open() {
            let temp = tempfile::tempdir().unwrap();
            fs::create_dir(temp.path().join("fixture")).unwrap();
            let source = temp.path().join("fixture/canary.txt");
            let alias = temp.path().join("transient-fixture-alias");
            fs::write(&source, b"fixture-bytes").unwrap();
            let root = Backend::open(temp.path()).unwrap();
            let error = root
                .read_fixture_file_with_before_open("fixture", "canary.txt", 1024, || {
                    fs::hard_link(&source, &alias).unwrap();
                    fs::remove_file(&alias).unwrap();
                })
                .unwrap_err();
            assert!(error
                .to_string()
                .contains("canary-bearing file changed before read"));
            assert!(!alias.exists());
        }

        #[test]
        fn rejects_transient_hardlink_created_and_removed_during_read() {
            let temp = tempfile::tempdir().unwrap();
            fs::create_dir(temp.path().join("fixture")).unwrap();
            let source = temp.path().join("fixture/a.dat");
            let alias = temp.path().join("transient-alias");
            fs::write(&source, vec![b'x'; 128 * 1024]).unwrap();
            let root = Backend::open(temp.path()).unwrap();
            let error = root
                .inventory_with_hooks(
                    "fixture",
                    WalkBudget {
                        max_bytes: 256 * 1024,
                        max_single_file_bytes: 256 * 1024,
                        ..budget()
                    },
                    || {},
                    || {
                        fs::hard_link(&source, &alias).unwrap();
                        fs::remove_file(&alias).unwrap();
                    },
                )
                .unwrap_err();
            assert!(error
                .to_string()
                .contains("file identity, link count, or size changed while hashing"));
            assert!(!alias.exists());
        }

        #[test]
        fn rejects_non_regular_and_resource_ceiling_excesses() {
            let cases: Vec<(&str, Box<dyn Fn(&Path)>)> = vec![
                (
                    "max_single_file_bytes",
                    Box::new(|fixture| fs::write(fixture.join("large"), vec![0; 513]).unwrap()),
                ),
                (
                    "max_depth",
                    Box::new(|fixture| fs::create_dir_all(fixture.join("a/b/c/d/e")).unwrap()),
                ),
                (
                    "max_component_bytes",
                    Box::new(|fixture| fs::write(fixture.join("a".repeat(33)), b"x").unwrap()),
                ),
                (
                    "max_directories",
                    Box::new(|fixture| {
                        for index in 0..9 {
                            fs::create_dir(fixture.join(format!("d{index}"))).unwrap();
                        }
                    }),
                ),
                (
                    "max_files",
                    Box::new(|fixture| {
                        for index in 0..9 {
                            fs::write(fixture.join(format!("f{index}")), b"x").unwrap();
                        }
                    }),
                ),
                (
                    "max_bytes",
                    Box::new(|fixture| {
                        for index in 0..3 {
                            fs::write(fixture.join(format!("f{index}")), vec![0; 400]).unwrap();
                        }
                    }),
                ),
                (
                    "max_path_bytes",
                    Box::new(|fixture| {
                        let component = "a".repeat(32);
                        let deep = fixture.join(&component).join(&component).join(&component);
                        fs::create_dir_all(&deep).unwrap();
                        fs::write(deep.join("b".repeat(32)), b"x").unwrap();
                    }),
                ),
            ];
            for (expected, setup) in cases {
                let temp = tempfile::tempdir().unwrap();
                let fixture = temp.path().join("fixture");
                fs::create_dir(&fixture).unwrap();
                setup(&fixture);
                let error = Backend::open(temp.path())
                    .unwrap()
                    .inventory("fixture", budget())
                    .unwrap_err();
                assert!(error.to_string().contains(expected), "{expected}: {error}");
            }
        }

        #[test]
        fn rejects_traversal_before_filesystem_access() {
            let temp = tempfile::tempdir().unwrap();
            let error = Backend::open(temp.path())
                .unwrap()
                .inventory("..", budget())
                .unwrap_err();
            assert!(error.to_string().contains("traversal-capable"));
        }

        #[test]
        fn rejects_socket_without_opening_it() {
            use std::os::unix::net::UnixListener;
            let temp = tempfile::tempdir().unwrap();
            fs::create_dir(temp.path().join("fixture")).unwrap();
            let _listener = UnixListener::bind(temp.path().join("fixture/socket")).unwrap();
            let error = Backend::open(temp.path())
                .unwrap()
                .inventory("fixture", budget())
                .unwrap_err();
            assert!(error
                .to_string()
                .contains("only directories and singly linked regular files"));
        }

        #[test]
        fn rejects_fifo_without_blocking_or_opening_body() {
            use std::time::{Duration, Instant};
            let temp = tempfile::tempdir().unwrap();
            fs::create_dir(temp.path().join("fixture")).unwrap();
            rustix::fs::mkfifoat(
                rustix::fs::CWD,
                temp.path().join("fixture/pipe"),
                Mode::RWXU,
            )
            .unwrap();
            let started = Instant::now();
            let error = Backend::open(temp.path())
                .unwrap()
                .inventory("fixture", budget())
                .unwrap_err();
            assert!(started.elapsed() < Duration::from_secs(1));
            assert!(error
                .to_string()
                .contains("only directories and singly linked regular files"));
        }

        #[cfg(target_os = "linux")]
        #[test]
        fn rejects_real_bind_mount_when_host_permits_creation() {
            use std::process::Command;
            struct Unmount(std::path::PathBuf);
            impl Drop for Unmount {
                fn drop(&mut self) {
                    let _ = Command::new("umount").arg(&self.0).status();
                }
            }

            let corpus = tempfile::tempdir().unwrap();
            let outside = tempfile::tempdir().unwrap();
            fs::create_dir_all(corpus.path().join("fixture/mounted")).unwrap();
            fs::write(
                outside.path().join("outside-canary"),
                b"BIND_MOUNT_OUTSIDE_CANARY",
            )
            .unwrap();
            let output = Command::new("mount")
                .args(["--bind"])
                .arg(outside.path())
                .arg(corpus.path().join("fixture/mounted"))
                .output()
                .unwrap();
            if !output.status.success() {
                eprintln!(
                    "SKIP: host denied bind-mount creation: {}",
                    String::from_utf8_lossy(&output.stderr).trim()
                );
                return;
            }
            let _unmount = Unmount(corpus.path().join("fixture/mounted"));
            let error = Backend::open(corpus.path())
                .unwrap()
                .inventory("fixture", budget())
                .unwrap_err();
            assert!(error.to_string().contains("mount boundary"));
            assert!(!error.to_string().contains("BIND_MOUNT_OUTSIDE_CANARY"));
        }

        #[cfg(target_os = "linux")]
        #[test]
        fn reports_linux_mount_identity_availability() {
            let fd = openat(
                rustix::fs::CWD,
                ".",
                OFlags::RDONLY | OFlags::DIRECTORY | OFlags::CLOEXEC,
                Mode::empty(),
            )
            .unwrap();
            let id = mount_id(&fd).unwrap();
            eprintln!("ACTIVE: Linux statx mount identity {id}");
        }

        #[test]
        fn unavailable_mount_identity_fails_closed() {
            let error = require_mount_id(None).unwrap_err();
            assert!(error.to_string().contains("fails closed"));
        }
    }
}
