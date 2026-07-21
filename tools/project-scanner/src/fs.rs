use crate::error::ScanError;
use sha2::{Digest, Sha256};
use std::io::Read;
use std::path::Path;

#[derive(Debug, Clone, Copy)]
pub(crate) struct Budget {
    pub max_bytes: u64,
    pub max_files: u64,
    pub max_single_file_bytes: u64,
    pub max_directories: u64,
    pub max_depth: u64,
    pub max_path_bytes: u64,
    pub max_component_bytes: u64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct InventoryFile {
    pub path: String,
    pub size: u64,
    pub sha256: [u8; 32],
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct Inventory {
    pub files: Vec<InventoryFile>,
    pub directories: u64,
    pub bytes: u64,
}

pub(crate) struct Root {
    #[cfg(unix)]
    inner: unix::Root,
    #[cfg(windows)]
    inner: windows::Root,
}

impl Root {
    pub(crate) fn open(path: &Path) -> Result<Self, ScanError> {
        #[cfg(unix)]
        {
            unix::Root::open(path).map(|inner| Self { inner })
        }
        #[cfg(windows)]
        {
            windows::Root::open(path).map(|inner| Self { inner })
        }
        #[cfg(not(any(unix, windows)))]
        {
            let _ = path;
            Err(ScanError::UnsupportedPlatform(
                "no descriptor-relative no-follow backend exists for this platform",
            ))
        }
    }

    pub(crate) fn read_component(&self, name: &str, max_bytes: u64) -> Result<Vec<u8>, ScanError> {
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
            Err(ScanError::UnsupportedPlatform("no safe backend"))
        }
    }

    pub(crate) fn component_exists(&self, name: &str) -> Result<bool, ScanError> {
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
            Err(ScanError::UnsupportedPlatform("no safe backend"))
        }
    }

    pub(crate) fn inventory(
        &self,
        component: &str,
        budget: Budget,
    ) -> Result<Inventory, ScanError> {
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
            Err(ScanError::UnsupportedPlatform("no safe backend"))
        }
    }

    pub(crate) fn read_fixture_file(
        &self,
        fixture: &str,
        path: &str,
        max_bytes: u64,
    ) -> Result<Vec<u8>, ScanError> {
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
            Err(ScanError::UnsupportedPlatform("no safe backend"))
        }
    }
}

#[cfg(windows)]
mod windows {
    use super::*;
    use cap_fs_ext::{DirExt, FollowSymlinks, MetadataExt, OpenOptionsFollowExt};
    use cap_std::fs::{Dir, File, OpenOptions};
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

    #[derive(Debug)]
    struct PendingFile {
        components: Vec<String>,
        path: String,
        size: u64,
        identity: Identity,
    }

    pub(super) struct Root {
        dir: Dir,
        identity: Identity,
    }

    impl Root {
        pub(super) fn open(path: &Path) -> Result<Self, ScanError> {
            let file = std::fs::OpenOptions::new()
                .read(true)
                .custom_flags(FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OPEN_REPARSE_POINT)
                .open(path)
                .map_err(|error| ScanError::io("open root reparse point itself", path, error))?;
            let metadata = file
                .metadata()
                .map_err(|error| ScanError::io("stat opened root", path, error))?;
            if metadata.file_type().is_symlink() || !metadata.is_dir() {
                return Err(ScanError::UnsafeObject {
                    path: path.display().to_string(),
                    reason: "root is not a direct directory or is a reparse point",
                });
            }
            let dir = Dir::from_std_file(file);
            let metadata = dir
                .dir_metadata()
                .map_err(|error| ScanError::io("read root handle identity", path, error))?;
            Ok(Self {
                identity: identity(&metadata),
                dir,
            })
        }

        pub(super) fn component_exists(&self, name: &str) -> Result<bool, ScanError> {
            validate_component(name)?;
            match self.dir.symlink_metadata(name) {
                Ok(_) => Ok(true),
                Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(false),
                Err(error) => Err(ScanError::io("inspect root component", name, error)),
            }
        }

        pub(super) fn read_component(
            &self,
            name: &str,
            max_bytes: u64,
        ) -> Result<Vec<u8>, ScanError> {
            validate_component(name)?;
            let mut file = open_file_nofollow(&self.dir, name, name)?;
            let before = checked_file_metadata(&file, name, self.identity.volume)?;
            if before.len() > max_bytes {
                return Err(ScanError::ResourceLimit {
                    path: name.to_owned(),
                    limit: "bounded metadata-file read ceiling",
                });
            }
            let mut bytes = Vec::with_capacity(usize::try_from(before.len()).unwrap_or(0));
            file.by_ref()
                .take(max_bytes.saturating_add(1))
                .read_to_end(&mut bytes)
                .map_err(|error| ScanError::io("read bounded root component", name, error))?;
            let after = checked_file_metadata(&file, name, self.identity.volume)?;
            if identity(&before) != identity(&after)
                || before.len() != after.len()
                || u64::try_from(bytes.len()).unwrap_or(u64::MAX) != before.len()
            {
                return Err(ScanError::Integrity {
                    path: name.to_owned(),
                    reason: "file identity, link count, or size changed during read",
                });
            }
            Ok(bytes)
        }

        pub(super) fn inventory(
            &self,
            component: &str,
            budget: Budget,
        ) -> Result<Inventory, ScanError> {
            self.inventory_with_before_hash(component, budget, || {})
        }

        fn inventory_with_before_hash(
            &self,
            component: &str,
            budget: Budget,
            before_hash: impl FnOnce(),
        ) -> Result<Inventory, ScanError> {
            validate_component(component)?;
            let fixture = self.dir.open_dir_nofollow(component).map_err(|error| {
                ScanError::io(
                    "open fixture root without following reparse points",
                    component,
                    error,
                )
            })?;
            let fixture_metadata = fixture
                .dir_metadata()
                .map_err(|error| ScanError::io("read fixture root identity", component, error))?;
            if identity(&fixture_metadata).volume != self.identity.volume {
                return Err(ScanError::UnsafeObject {
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
                    return Err(ScanError::Integrity {
                        path: pending.path,
                        reason: "file identity changed before hashing",
                    });
                }
                let mut hasher = Sha256::new();
                let copied = std::io::copy(&mut file, &mut hasher)
                    .map_err(|error| ScanError::io("hash fixture file", &pending.path, error))?;
                let after = checked_file_metadata(&file, &pending.path, self.identity.volume)?;
                if identity(&after) != pending.identity
                    || after.len() != pending.size
                    || copied != pending.size
                {
                    return Err(ScanError::Integrity {
                        path: pending.path,
                        reason: "file identity, link count, or size changed while hashing",
                    });
                }
                hashed_bytes = hashed_bytes.checked_add(pending.size).ok_or_else(|| {
                    ScanError::ResourceLimit {
                        path: pending.path.clone(),
                        limit: "aggregate byte counter overflow",
                    }
                })?;
                files.push(InventoryFile {
                    path: pending.path,
                    size: pending.size,
                    sha256: hasher.finalize().into(),
                });
            }
            if hashed_bytes != declared_bytes {
                return Err(ScanError::Integrity {
                    path: component.to_owned(),
                    reason: "metadata and hashed byte totals differ",
                });
            }
            Ok(Inventory {
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
        ) -> Result<Vec<u8>, ScanError> {
            validate_component(fixture)?;
            let components = path.split('/').map(str::to_owned).collect::<Vec<_>>();
            if components.is_empty() {
                return Err(ScanError::Semantic("empty fixture file path".to_owned()));
            }
            for component in &components {
                validate_component(component)?;
            }
            let fixture_dir = self.dir.open_dir_nofollow(fixture).map_err(|error| {
                ScanError::io("open fixture root for canary validation", fixture, error)
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
                return Err(ScanError::ResourceLimit {
                    path: path.to_owned(),
                    limit: "max_single_file_bytes",
                });
            }
            let mut bytes = Vec::with_capacity(usize::try_from(before.len()).unwrap_or(0));
            file.by_ref()
                .take(max_bytes.saturating_add(1))
                .read_to_end(&mut bytes)
                .map_err(|error| ScanError::io("read bounded canary-bearing file", path, error))?;
            let after = checked_file_metadata(&file, path, self.identity.volume)?;
            if identity(&before) != identity(&after)
                || before.len() != after.len()
                || u64::try_from(bytes.len()).unwrap_or(u64::MAX) != before.len()
            {
                return Err(ScanError::Integrity {
                    path: path.to_owned(),
                    reason: "canary-bearing file changed during read",
                });
            }
            Ok(bytes)
        }
    }

    fn open_file_nofollow(dir: &Dir, name: &str, path: &str) -> Result<File, ScanError> {
        let mut options = OpenOptions::new();
        options.read(true).follow(FollowSymlinks::No);
        dir.open_with(name, &options).map_err(|error| {
            ScanError::io("open file without following reparse points", path, error)
        })
    }

    fn checked_file_metadata(
        file: &File,
        path: &str,
        root_volume: u64,
    ) -> Result<cap_std::fs::Metadata, ScanError> {
        let metadata = file
            .metadata()
            .map_err(|error| ScanError::io("read opened file identity", path, error))?;
        if !metadata.is_file() || metadata.file_type().is_symlink() {
            return Err(ScanError::UnsafeObject {
                path: path.to_owned(),
                reason: "only direct regular files are content-accessible",
            });
        }
        if metadata.nlink() != 1 {
            return Err(ScanError::UnsafeObject {
                path: path.to_owned(),
                reason: "multiply linked regular file is content-inaccessible",
            });
        }
        if identity(&metadata).volume != root_volume {
            return Err(ScanError::UnsafeObject {
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
    ) -> Result<File, ScanError> {
        reopen_file_unchecked(fixture, pending, root_volume)
    }

    fn reopen_file_unchecked(
        fixture: &Dir,
        pending: &PendingFile,
        root_volume: u64,
    ) -> Result<File, ScanError> {
        let mut current = fixture
            .open_dir_nofollow(".")
            .map_err(|error| ScanError::io("duplicate fixture root", &pending.path, error))?;
        for component in &pending.components[..pending.components.len().saturating_sub(1)] {
            current = current.open_dir_nofollow(component).map_err(|error| {
                ScanError::io(
                    "reopen directory without following reparse points",
                    &pending.path,
                    error,
                )
            })?;
            let metadata = current.dir_metadata().map_err(|error| {
                ScanError::io("read reopened directory identity", &pending.path, error)
            })?;
            if identity(&metadata).volume != root_volume {
                return Err(ScanError::UnsafeObject {
                    path: pending.path.clone(),
                    reason: "directory crosses a volume boundary",
                });
            }
        }
        let name = pending
            .components
            .last()
            .ok_or_else(|| ScanError::Semantic("empty fixture file path".to_owned()))?;
        open_file_nofollow(&current, name, &pending.path)
    }

    fn enumerate_dir(
        dir: &Dir,
        components: &mut Vec<String>,
        files: &mut Vec<PendingFile>,
        directories: &mut u64,
        declared_bytes: &mut u64,
        budget: &Budget,
        root_volume: u64,
    ) -> Result<(), ScanError> {
        let entries = dir.entries().map_err(|error| {
            ScanError::io(
                "enumerate fixture directory",
                portable_path(components),
                error,
            )
        })?;
        for entry in entries {
            let entry = entry.map_err(|error| {
                ScanError::io(
                    "read fixture directory entry",
                    portable_path(components),
                    error,
                )
            })?;
            let name = entry
                .file_name()
                .into_string()
                .map_err(|_| ScanError::UnsafeObject {
                    path: portable_path(components),
                    reason: "non-UTF-8 path component",
                })?;
            validate_component(&name)?;
            components.push(name.clone());
            let path = portable_path(components);
            check_path_budget(components, &path, budget)?;
            let kind = entry
                .file_type()
                .map_err(|error| ScanError::io("inspect fixture entry type", &path, error))?;
            if kind.is_symlink() {
                return Err(ScanError::UnsafeObject {
                    path,
                    reason: "symbolic link or reparse point refused before content access",
                });
            } else if kind.is_dir() {
                *directories = directories.saturating_add(1);
                if *directories > budget.max_directories {
                    return Err(ScanError::ResourceLimit {
                        path,
                        limit: "max_directories",
                    });
                }
                let child = dir.open_dir_nofollow(&name).map_err(|error| {
                    ScanError::io(
                        "open directory without following reparse points",
                        &path,
                        error,
                    )
                })?;
                let metadata = child.dir_metadata().map_err(|error| {
                    ScanError::io("read opened directory identity", &path, error)
                })?;
                if identity(&metadata).volume != root_volume {
                    return Err(ScanError::UnsafeObject {
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
                let metadata = checked_file_metadata(&file, &path, root_volume)?;
                let size = metadata.len();
                if size > budget.max_single_file_bytes {
                    return Err(ScanError::ResourceLimit {
                        path,
                        limit: "max_single_file_bytes",
                    });
                }
                if u64::try_from(files.len()).unwrap_or(u64::MAX) >= budget.max_files {
                    return Err(ScanError::ResourceLimit {
                        path,
                        limit: "max_files",
                    });
                }
                *declared_bytes =
                    declared_bytes
                        .checked_add(size)
                        .ok_or_else(|| ScanError::ResourceLimit {
                            path: path.clone(),
                            limit: "aggregate byte counter overflow",
                        })?;
                if *declared_bytes > budget.max_bytes {
                    return Err(ScanError::ResourceLimit {
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
                return Err(ScanError::UnsafeObject {
                    path,
                    reason: "only directories and singly linked regular files are allowed",
                });
            }
            components.pop();
        }
        Ok(())
    }

    fn check_path_budget(
        components: &[String],
        path: &str,
        budget: &Budget,
    ) -> Result<(), ScanError> {
        if components.len() as u64 > budget.max_depth {
            return Err(ScanError::ResourceLimit {
                path: path.to_owned(),
                limit: "max_depth",
            });
        }
        if path.len() as u64 > budget.max_path_bytes {
            return Err(ScanError::ResourceLimit {
                path: path.to_owned(),
                limit: "max_path_bytes",
            });
        }
        if components.last().map_or(0, String::len) as u64 > budget.max_component_bytes {
            return Err(ScanError::ResourceLimit {
                path: path.to_owned(),
                limit: "max_component_bytes",
            });
        }
        Ok(())
    }

    fn validate_component(component: &str) -> Result<(), ScanError> {
        if component.is_empty()
            || component == "."
            || component == ".."
            || component.contains('/')
            || component.contains('\\')
            || component.contains(':')
            || component.ends_with('.')
            || component.ends_with(' ')
            || component.bytes().any(|byte| byte < 0x20 || byte == 0x7f)
        {
            return Err(ScanError::UnsafeObject {
                path: component.to_owned(),
                reason: "unsafe portable path component",
            });
        }
        let base = component
            .split('.')
            .next()
            .unwrap_or(component)
            .to_ascii_uppercase();
        let reserved = matches!(base.as_str(), "CON" | "PRN" | "AUX" | "NUL")
            || ((base.starts_with("COM") || base.starts_with("LPT"))
                && base.len() == 4
                && base.as_bytes()[3].is_ascii_digit()
                && base.as_bytes()[3] != b'0');
        if reserved {
            return Err(ScanError::UnsafeObject {
                path: component.to_owned(),
                reason: "Windows-reserved path component",
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

        fn budget() -> Budget {
            Budget {
                max_bytes: 1024 * 1024,
                max_files: 16,
                max_single_file_bytes: 1024 * 1024,
                max_directories: 16,
                max_depth: 8,
                max_path_bytes: 512,
                max_component_bytes: 128,
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
        fn inventories_regular_files_on_native_windows() {
            let temp = tempfile::tempdir().unwrap();
            fs::create_dir(temp.path().join("fixture")).unwrap();
            fs::write(temp.path().join("fixture/a.dat"), b"abc").unwrap();
            let inventory = Root::open(temp.path())
                .unwrap()
                .inventory("fixture", budget())
                .unwrap();
            assert_eq!(inventory.files.len(), 1);
            assert_eq!(inventory.files[0].path, "a.dat");
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
            let error = Root::open(temp.path())
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
            let root = Root::open(temp.path()).unwrap();
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
            let error = Root::open(temp.path())
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
            let error = match Root::open(&temp.path().join("root-link")) {
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

    pub(super) struct Root {
        fd: OwnedFd,
        identity: Identity,
        mount_id: u64,
    }

    impl Root {
        pub(super) fn open(path: &Path) -> Result<Self, ScanError> {
            let fd = openat(
                rustix::fs::CWD,
                path,
                OFlags::RDONLY | OFlags::DIRECTORY | OFlags::NOFOLLOW | OFlags::CLOEXEC,
                Mode::empty(),
            )
            .map_err(|error| ScanError::io("open root without following links", path, error))?;
            let stat =
                fstat(&fd).map_err(|error| ScanError::io("stat opened root", path, error))?;
            let mount_id = mount_id(&fd)?;
            Ok(Self {
                fd,
                identity: Identity::from_stat(&stat),
                mount_id,
            })
        }

        pub(super) fn component_exists(&self, name: &str) -> Result<bool, ScanError> {
            validate_component(name)?;
            match statat(&self.fd, name, AtFlags::SYMLINK_NOFOLLOW) {
                Ok(_) => Ok(true),
                Err(rustix::io::Errno::NOENT) => Ok(false),
                Err(error) => Err(ScanError::io("inspect root component", name, error)),
            }
        }

        pub(super) fn read_component(
            &self,
            name: &str,
            max_bytes: u64,
        ) -> Result<Vec<u8>, ScanError> {
            self.read_component_with_before_open(name, max_bytes, || {})
        }

        fn read_component_with_before_open(
            &self,
            name: &str,
            max_bytes: u64,
            before_open: impl FnOnce(),
        ) -> Result<Vec<u8>, ScanError> {
            validate_component(name)?;
            let metadata = statat(&self.fd, name, AtFlags::SYMLINK_NOFOLLOW)
                .map_err(|error| ScanError::io("lstat root component", name, error))?;
            ensure_regular_single_link(name, &metadata)?;
            let size = u64::try_from(metadata.st_size).map_err(|_| ScanError::ResourceLimit {
                path: name.to_owned(),
                limit: "negative file size",
            })?;
            if size > max_bytes {
                return Err(ScanError::ResourceLimit {
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
                ScanError::io("open root component without following links", name, error)
            })?;
            let opened =
                fstat(&fd).map_err(|error| ScanError::io("fstat root component", name, error))?;
            ensure_regular_single_link(name, &opened)?;
            if opened.st_dev != self.identity.device || crosses_mount(self.mount_id, mount_id(&fd)?)
            {
                return Err(ScanError::UnsafeObject {
                    path: name.to_owned(),
                    reason: "metadata file crosses a mount boundary",
                });
            }
            if Identity::from_stat(&opened) != Identity::from_stat(&metadata)
                || opened.st_size != metadata.st_size
                || change_stamp(&opened) != change_stamp(&metadata)
            {
                return Err(ScanError::Integrity {
                    path: name.to_owned(),
                    reason: "identity changed before read",
                });
            }
            let mut bytes = Vec::with_capacity(usize::try_from(size).unwrap_or(0));
            let mut file = File::from(fd);
            file.by_ref()
                .take(max_bytes.saturating_add(1))
                .read_to_end(&mut bytes)
                .map_err(|error| ScanError::io("read bounded root component", name, error))?;
            let after = file
                .metadata()
                .map_err(|error| ScanError::io("restat root component", name, error))?;
            use std::os::unix::fs::MetadataExt;
            if u64::try_from(bytes.len()).unwrap_or(u64::MAX) != size
                || after.nlink() != 1
                || after.dev() != opened.st_dev
                || after.ino() != opened.st_ino
                || after.size() != size
                || after.ctime() != opened.st_ctime
                || after.ctime_nsec() != opened.st_ctime_nsec as i64
            {
                return Err(ScanError::Integrity {
                    path: name.to_owned(),
                    reason: "size changed during read",
                });
            }
            Ok(bytes)
        }

        pub(super) fn inventory(
            &self,
            component: &str,
            budget: Budget,
        ) -> Result<Inventory, ScanError> {
            self.inventory_with_hooks(component, budget, || {}, || {})
        }

        #[cfg(test)]
        fn inventory_with_before_hash(
            &self,
            component: &str,
            budget: Budget,
            before_hash: impl FnOnce(),
        ) -> Result<Inventory, ScanError> {
            self.inventory_with_hooks(component, budget, before_hash, || {})
        }

        fn inventory_with_hooks(
            &self,
            component: &str,
            budget: Budget,
            before_hash: impl FnOnce(),
            during_first_read: impl FnOnce(),
        ) -> Result<Inventory, ScanError> {
            validate_component(component)?;
            let before = statat(&self.fd, component, AtFlags::SYMLINK_NOFOLLOW)
                .map_err(|error| ScanError::io("lstat fixture root", component, error))?;
            if !FileType::from_raw_mode(before.st_mode).is_dir() {
                return Err(ScanError::UnsafeObject {
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
                ScanError::io(
                    "open fixture root without following links",
                    component,
                    error,
                )
            })?;
            let opened = fstat(&fixture_fd)
                .map_err(|error| ScanError::io("fstat fixture root", component, error))?;
            if Identity::from_stat(&before) != Identity::from_stat(&opened) {
                return Err(ScanError::Integrity {
                    path: component.to_owned(),
                    reason: "fixture root identity changed before enumeration",
                });
            }
            let fixture_mount = mount_id(&fixture_fd)?;
            if opened.st_dev != self.identity.device || crosses_mount(self.mount_id, fixture_mount)
            {
                return Err(ScanError::UnsafeObject {
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
                return Err(ScanError::ResourceLimit {
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
                    let count = opened_file
                        .read(&mut buffer)
                        .map_err(|error| ScanError::io("hash fixture file", &file.path, error))?;
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
                    .map_err(|error| ScanError::io("restat hashed file", &file.path, error))?;
                use std::os::unix::fs::MetadataExt;
                if after.nlink() != 1
                    || after.dev() != stat.st_dev
                    || after.ino() != stat.st_ino
                    || after.size() != file.size
                    || after.ctime() != stat.st_ctime
                    || after.ctime_nsec() != stat.st_ctime_nsec as i64
                    || copied != file.size
                {
                    return Err(ScanError::Integrity {
                        path: file.path,
                        reason: "file identity, link count, or size changed while hashing",
                    });
                }
                hashed_bytes = hashed_bytes.checked_add(file.size).ok_or_else(|| {
                    ScanError::ResourceLimit {
                        path: file.path.clone(),
                        limit: "aggregate byte counter overflow",
                    }
                })?;
                files.push(InventoryFile {
                    path: file.path,
                    size: file.size,
                    sha256: hasher.finalize().into(),
                });
            }
            if hashed_bytes != declared_bytes {
                return Err(ScanError::Integrity {
                    path: component.to_owned(),
                    reason: "metadata and hashed byte totals differ",
                });
            }
            Ok(Inventory {
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
        ) -> Result<Vec<u8>, ScanError> {
            self.read_fixture_file_with_before_open(fixture, path, max_bytes, || {})
        }

        fn read_fixture_file_with_before_open(
            &self,
            fixture: &str,
            path: &str,
            max_bytes: u64,
            before_open: impl FnOnce(),
        ) -> Result<Vec<u8>, ScanError> {
            validate_component(fixture)?;
            let components = path.split('/').map(str::to_owned).collect::<Vec<_>>();
            if components.is_empty() {
                return Err(ScanError::Semantic("empty fixture file path".to_owned()));
            }
            for component in &components {
                validate_component(component)?;
            }
            let fixture_fd = openat(
                &self.fd,
                fixture,
                OFlags::RDONLY | OFlags::DIRECTORY | OFlags::NOFOLLOW | OFlags::CLOEXEC,
                Mode::empty(),
            )
            .map_err(|error| {
                ScanError::io("open fixture root for canary validation", fixture, error)
            })?;
            let fixture_stat = fstat(&fixture_fd).map_err(|error| {
                ScanError::io("fstat fixture root for canary validation", fixture, error)
            })?;
            let fixture_mount = mount_id(&fixture_fd)?;
            if fixture_stat.st_dev != self.identity.device
                || crosses_mount(self.mount_id, fixture_mount)
            {
                return Err(ScanError::UnsafeObject {
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
                ScanError::io("duplicate fixture root for canary validation", path, error)
            })?;
            for component in &pending.components[..pending.components.len().saturating_sub(1)] {
                let next = openat(
                    &current,
                    component,
                    OFlags::RDONLY | OFlags::DIRECTORY | OFlags::NOFOLLOW | OFlags::CLOEXEC,
                    Mode::empty(),
                )
                .map_err(|error| {
                    ScanError::io("open canary parent without following links", path, error)
                })?;
                let stat = fstat(&next)
                    .map_err(|error| ScanError::io("fstat canary parent", path, error))?;
                if stat.st_dev != self.identity.device
                    || crosses_mount(fixture_mount, mount_id(&next)?)
                {
                    return Err(ScanError::UnsafeObject {
                        path: path.to_owned(),
                        reason: "canary parent crosses a mount boundary",
                    });
                }
                current = next;
            }
            let name = pending.components.last().expect("checked non-empty");
            let before = statat(&current, name, AtFlags::SYMLINK_NOFOLLOW)
                .map_err(|error| ScanError::io("lstat canary-bearing file", path, error))?;
            ensure_regular_single_link(path, &before)?;
            let size = u64::try_from(before.st_size).map_err(|_| ScanError::ResourceLimit {
                path: path.to_owned(),
                limit: "negative file size",
            })?;
            if size > max_bytes {
                return Err(ScanError::ResourceLimit {
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
                ScanError::io(
                    "open canary-bearing file without following links",
                    path,
                    error,
                )
            })?;
            let opened = fstat(&fd)
                .map_err(|error| ScanError::io("fstat canary-bearing file", path, error))?;
            ensure_regular_single_link(path, &opened)?;
            if opened.st_dev != self.identity.device || crosses_mount(fixture_mount, mount_id(&fd)?)
            {
                return Err(ScanError::UnsafeObject {
                    path: path.to_owned(),
                    reason: "canary-bearing file crosses a mount boundary",
                });
            }
            if Identity::from_stat(&opened) != Identity::from_stat(&before)
                || opened.st_size != before.st_size
                || change_stamp(&opened) != change_stamp(&before)
            {
                return Err(ScanError::Integrity {
                    path: path.to_owned(),
                    reason: "canary-bearing file changed before read",
                });
            }
            let mut bytes = Vec::with_capacity(usize::try_from(size).unwrap_or(0));
            let mut file = File::from(fd);
            file.by_ref()
                .take(max_bytes.saturating_add(1))
                .read_to_end(&mut bytes)
                .map_err(|error| ScanError::io("read bounded canary-bearing file", path, error))?;
            let after = file
                .metadata()
                .map_err(|error| ScanError::io("restat canary-bearing file", path, error))?;
            use std::os::unix::fs::MetadataExt;
            if u64::try_from(bytes.len()).unwrap_or(u64::MAX) != size
                || after.nlink() != 1
                || after.dev() != opened.st_dev
                || after.ino() != opened.st_ino
                || after.size() != size
                || after.ctime() != opened.st_ctime
                || after.ctime_nsec() != opened.st_ctime_nsec as i64
            {
                return Err(ScanError::Integrity {
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
        ) -> Result<(File, Stat), ScanError> {
            let mut current = openat(
                fixture_fd,
                ".",
                OFlags::RDONLY | OFlags::DIRECTORY | OFlags::CLOEXEC,
                Mode::empty(),
            )
            .map_err(|error| ScanError::io("duplicate fixture root", &pending.path, error))?;
            for component in &pending.components[..pending.components.len().saturating_sub(1)] {
                let next = openat(
                    &current,
                    component,
                    OFlags::RDONLY | OFlags::DIRECTORY | OFlags::NOFOLLOW | OFlags::CLOEXEC,
                    Mode::empty(),
                )
                .map_err(|error| ScanError::io("reopen fixture directory", &pending.path, error))?;
                let stat = fstat(&next).map_err(|error| {
                    ScanError::io("fstat reopened directory", &pending.path, error)
                })?;
                if stat.st_dev != self.identity.device
                    || crosses_mount(fixture_mount, mount_id(&next)?)
                {
                    return Err(ScanError::UnsafeObject {
                        path: pending.path.clone(),
                        reason: "directory changed into a mount boundary",
                    });
                }
                current = next;
            }
            let name = pending
                .components
                .last()
                .ok_or_else(|| ScanError::Semantic("empty fixture file path".to_owned()))?;
            let fd = openat(
                &current,
                name,
                OFlags::RDONLY | OFlags::NOFOLLOW | OFlags::CLOEXEC,
                Mode::empty(),
            )
            .map_err(|error| {
                ScanError::io(
                    "open fixture file without following links",
                    &pending.path,
                    error,
                )
            })?;
            let stat = fstat(&fd).map_err(|error| {
                ScanError::io("fstat opened fixture file", &pending.path, error)
            })?;
            ensure_regular_single_link(&pending.path, &stat)?;
            if stat.st_dev != self.identity.device || crosses_mount(fixture_mount, mount_id(&fd)?) {
                return Err(ScanError::UnsafeObject {
                    path: pending.path.clone(),
                    reason: "file crosses a mount boundary",
                });
            }
            if Identity::from_stat(&stat) != pending.identity
                || u64::try_from(stat.st_size).ok() != Some(pending.size)
                || change_stamp(&stat) != pending.change
            {
                return Err(ScanError::Integrity {
                    path: pending.path.clone(),
                    reason: "file identity changed before hashing",
                });
            }
            Ok((File::from(fd), stat))
        }
    }

    fn enumerate_dir(
        fd: &OwnedFd,
        components: &mut Vec<String>,
        files: &mut Vec<PendingFile>,
        directories: &mut u64,
        declared_bytes: &mut u64,
        budget: &Budget,
        root_device: u64,
        root_mount: u64,
    ) -> Result<(), ScanError> {
        let mut dir = Dir::read_from(fd).map_err(|error| {
            ScanError::io("enumerate directory", portable_path(components), error)
        })?;
        while let Some(entry) = dir.read() {
            let entry = entry.map_err(|error| {
                ScanError::io("read directory entry", portable_path(components), error)
            })?;
            let name_bytes = entry.file_name().to_bytes();
            if name_bytes == b"." || name_bytes == b".." {
                continue;
            }
            let name = std::str::from_utf8(name_bytes).map_err(|_| ScanError::UnsafeObject {
                path: portable_path(components),
                reason: "filesystem name is not portable UTF-8",
            })?;
            validate_component(name)?;
            if u64::try_from(name.len()).unwrap_or(u64::MAX) > budget.max_component_bytes {
                return Err(ScanError::ResourceLimit {
                    path: name.to_owned(),
                    limit: "max_component_bytes",
                });
            }
            components.push(name.to_owned());
            let path = portable_path(components);
            if u64::try_from(path.len()).unwrap_or(u64::MAX) > budget.max_path_bytes {
                return Err(ScanError::ResourceLimit {
                    path,
                    limit: "max_path_bytes",
                });
            }
            let stat = statat(fd, entry.file_name(), AtFlags::SYMLINK_NOFOLLOW)
                .map_err(|error| ScanError::io("lstat directory entry", &path, error))?;
            let kind = FileType::from_raw_mode(stat.st_mode);
            if kind.is_symlink() {
                return Err(ScanError::UnsafeObject {
                    path,
                    reason: "symbolic link refused without reading target",
                });
            }
            if kind.is_dir() {
                *directories =
                    directories
                        .checked_add(1)
                        .ok_or_else(|| ScanError::ResourceLimit {
                            path: path.clone(),
                            limit: "directory counter overflow",
                        })?;
                if *directories > budget.max_directories {
                    return Err(ScanError::ResourceLimit {
                        path,
                        limit: "max_directories",
                    });
                }
                if u64::try_from(components.len()).unwrap_or(u64::MAX) > budget.max_depth {
                    return Err(ScanError::ResourceLimit {
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
                    ScanError::io("open child directory without following links", &path, error)
                })?;
                let opened = fstat(&child)
                    .map_err(|error| ScanError::io("fstat child directory", &path, error))?;
                if Identity::from_stat(&opened) != Identity::from_stat(&stat) {
                    return Err(ScanError::Integrity {
                        path,
                        reason: "directory identity changed during enumeration",
                    });
                }
                if opened.st_dev != root_device || crosses_mount(root_mount, mount_id(&child)?) {
                    return Err(ScanError::UnsafeObject {
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
                let size = u64::try_from(stat.st_size).map_err(|_| ScanError::ResourceLimit {
                    path: path.clone(),
                    limit: "negative file size",
                })?;
                if size > budget.max_single_file_bytes {
                    return Err(ScanError::ResourceLimit {
                        path,
                        limit: "max_single_file_bytes",
                    });
                }
                if u64::try_from(files.len()).unwrap_or(u64::MAX) >= budget.max_files {
                    return Err(ScanError::ResourceLimit {
                        path,
                        limit: "max_files",
                    });
                }
                *declared_bytes =
                    declared_bytes
                        .checked_add(size)
                        .ok_or_else(|| ScanError::ResourceLimit {
                            path: path.clone(),
                            limit: "aggregate byte counter overflow",
                        })?;
                if *declared_bytes > budget.max_bytes {
                    return Err(ScanError::ResourceLimit {
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
                return Err(ScanError::UnsafeObject {
                    path,
                    reason: "only directories and singly linked regular files are allowed",
                });
            }
            components.pop();
        }
        Ok(())
    }

    fn ensure_regular_single_link(path: &str, stat: &Stat) -> Result<(), ScanError> {
        if !FileType::from_raw_mode(stat.st_mode).is_file() {
            return Err(ScanError::UnsafeObject {
                path: path.to_owned(),
                reason: "not a regular file",
            });
        }
        if stat.st_nlink != 1 {
            return Err(ScanError::UnsafeObject {
                path: path.to_owned(),
                reason: "multiply linked regular file is content-inaccessible",
            });
        }
        Ok(())
    }

    fn validate_component(component: &str) -> Result<(), ScanError> {
        if component.is_empty()
            || component == "."
            || component == ".."
            || component.contains('/')
            || component.contains('\\')
            || component.contains(':')
            || component.ends_with('.')
            || component.ends_with(' ')
            || component.bytes().any(|byte| byte < 0x20 || byte == 0x7f)
        {
            return Err(ScanError::UnsafeObject {
                path: component.to_owned(),
                reason: "non-portable or traversal-capable path component",
            });
        }
        let base = component
            .split('.')
            .next()
            .unwrap_or(component)
            .to_ascii_uppercase();
        let reserved = matches!(base.as_str(), "CON" | "PRN" | "AUX" | "NUL")
            || (base.len() == 4
                && (base.starts_with("COM") || base.starts_with("LPT"))
                && base.as_bytes()[3].is_ascii_digit()
                && base.as_bytes()[3] != b'0');
        if reserved {
            return Err(ScanError::UnsafeObject {
                path: component.to_owned(),
                reason: "Windows-reserved path component",
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
    fn mount_id(fd: &OwnedFd) -> Result<u64, ScanError> {
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
                return Err(ScanError::io(
                    "read mount identity",
                    "<opened-directory>",
                    error,
                ))
            }
        };
        require_mount_id(observed)
    }

    #[cfg(not(target_os = "linux"))]
    fn mount_id(_fd: &OwnedFd) -> Result<u64, ScanError> {
        require_mount_id(None)
    }

    fn require_mount_id(observed: Option<u64>) -> Result<u64, ScanError> {
        observed.ok_or(ScanError::UnsupportedPlatform(
            "filesystem mount identity is unavailable; no-follow scanning fails closed",
        ))
    }

    #[cfg(test)]
    mod tests {
        use super::*;
        use std::fs;

        fn budget() -> Budget {
            Budget {
                max_bytes: 1024,
                max_files: 8,
                max_single_file_bytes: 512,
                max_directories: 8,
                max_depth: 4,
                max_path_bytes: 128,
                max_component_bytes: 32,
            }
        }

        #[test]
        fn inventories_and_hashes_regular_files() {
            let temp = tempfile::tempdir().unwrap();
            fs::create_dir(temp.path().join("fixture")).unwrap();
            fs::write(temp.path().join("fixture/a.dat"), b"abc").unwrap();
            let inventory = Root::open(temp.path())
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
            let error = Root::open(temp.path())
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
                let error = Root::open(temp.path())
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
            let root = Root::open(temp.path()).unwrap();
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
                ScanError::Io { .. } | ScanError::UnsafeObject { .. } | ScanError::Integrity { .. }
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
            let root = Root::open(temp.path()).unwrap();
            let error = root
                .inventory_with_before_hash("fixture", budget(), || {
                    fs::rename(temp.path().join("fixture/sub"), temp.path().join("old-sub"))
                        .unwrap();
                    symlink(temp.path().join("outside"), temp.path().join("fixture/sub")).unwrap();
                })
                .unwrap_err();
            assert!(matches!(
                error,
                ScanError::Io { .. } | ScanError::UnsafeObject { .. } | ScanError::Integrity { .. }
            ));
            assert!(!error.to_string().contains("directory-swap-canary"));
        }

        #[test]
        fn rejects_hardlink_created_between_enumeration_and_hashing() {
            let temp = tempfile::tempdir().unwrap();
            fs::create_dir(temp.path().join("fixture")).unwrap();
            fs::write(temp.path().join("fixture/a.dat"), b"safe").unwrap();
            let root = Root::open(temp.path()).unwrap();
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
            let root = Root::open(temp.path()).unwrap();
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
            let root = Root::open(temp.path()).unwrap();
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
            let root = Root::open(temp.path()).unwrap();
            let error = root
                .inventory_with_hooks(
                    "fixture",
                    Budget {
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
                let error = Root::open(temp.path())
                    .unwrap()
                    .inventory("fixture", budget())
                    .unwrap_err();
                assert!(error.to_string().contains(expected), "{expected}: {error}");
            }
        }

        #[test]
        fn rejects_traversal_before_filesystem_access() {
            let temp = tempfile::tempdir().unwrap();
            let error = Root::open(temp.path())
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
            let error = Root::open(temp.path())
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
            let error = Root::open(temp.path())
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
            let error = Root::open(corpus.path())
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
