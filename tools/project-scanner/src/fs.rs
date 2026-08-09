use crate::error::ScanError;
#[cfg(test)]
use rcce_project::WalkFile;
use rcce_project::{
    ProjectRelativePath, ProjectRoot, ReadAssurance, ReadBudget, RootCapabilities, WalkBudget,
    WalkResult,
};
use std::path::Path;

pub(crate) type Budget = WalkBudget;
#[cfg(test)]
pub(crate) type InventoryFile = WalkFile;
pub(crate) type Inventory = WalkResult;

pub(crate) struct Root {
    inner: ProjectRoot,
    assurance: ReadAssurance,
}

impl Root {
    pub(crate) fn open(path: &Path) -> Result<Self, ScanError> {
        Self::open_with_assurance(path, ReadAssurance::BaselineQuarantine)
    }

    pub(crate) fn open_with_assurance(
        path: &Path,
        assurance: ReadAssurance,
    ) -> Result<Self, ScanError> {
        let explicit = if path.is_absolute() {
            path.to_path_buf()
        } else {
            std::path::absolute(path)
                .map_err(|error| ScanError::io("resolve explicit root", path, error))?
        };
        let inner = ProjectRoot::open_explicit(&explicit)?;
        if let Err(error) = inner.require_assurance(assurance) {
            if assurance == ReadAssurance::TransientRaceDetection {
                return Err(ScanError::UnsupportedPlatform(
                    "transient hardlink detection is unavailable on this backend",
                ));
            }
            return Err(error.into());
        }
        Ok(Self { inner, assurance })
    }

    pub(crate) fn capabilities(&self) -> RootCapabilities {
        self.inner.capabilities()
    }

    pub(crate) fn read_component(&self, name: &str, max_bytes: u64) -> Result<Vec<u8>, ScanError> {
        let path = ProjectRelativePath::parse(name)?;
        Ok(self
            .inner
            .read(&path, ReadBudget { max_bytes }, self.assurance)?
            .into_vec())
    }

    pub(crate) fn component_exists(&self, name: &str) -> Result<bool, ScanError> {
        let path = ProjectRelativePath::parse(name)?;
        Ok(self.inner.root_entry_exists(&path)?)
    }

    pub(crate) fn inventory(
        &self,
        component: &str,
        budget: Budget,
    ) -> Result<Inventory, ScanError> {
        let path = ProjectRelativePath::parse(component)?;
        Ok(self.inner.walk(&path, budget, self.assurance)?)
    }

    pub(crate) fn read_fixture_file(
        &self,
        fixture: &str,
        path: &str,
        max_bytes: u64,
    ) -> Result<Vec<u8>, ScanError> {
        let path = ProjectRelativePath::parse(&format!("{fixture}/{path}"))?;
        Ok(self
            .inner
            .read(&path, ReadBudget { max_bytes }, self.assurance)?
            .into_vec())
    }
}
