use rcce_editor_core::{load_feedback_project, FeedbackProject};
use std::{
    fs,
    path::{Path, PathBuf},
    time::SystemTime,
};

pub(crate) struct ReloadFixture {
    root: PathBuf,
}

impl ReloadFixture {
    pub(crate) fn new(label: &str) -> Self {
        let nonce = SystemTime::now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .expect("system clock")
            .as_nanos();
        let root = std::env::temp_dir().join(format!(
            "rcce-editor-reload-{label}-{}-{nonce}",
            std::process::id()
        ));
        let source =
            PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../test-data/consensus/happy/Data");
        fs::create_dir_all(root.join("Server Data/Scripts")).expect("server fixture directory");
        fs::create_dir_all(root.join("Game Data")).expect("game fixture directory");
        fs::create_dir_all(root.join("Meshes")).expect("mesh fixture directory");
        fs::create_dir_all(root.join("Areas")).expect("area fixture directory");
        fs::copy(
            source.join("Server Data/Actors.dat"),
            root.join("Server Data/Actors.dat"),
        )
        .expect("actor fixture copy");
        fs::copy(
            source.join("Game Data/Meshes.dat"),
            root.join("Game Data/Meshes.dat"),
        )
        .expect("mesh catalog fixture copy");
        fs::copy(source.join("Meshes/Hero.b3d"), root.join("Meshes/Hero.b3d"))
            .expect("physical mesh fixture copy");
        fs::copy(source.join("Meshes/Hero.b3d"), root.join("Areas/Start.dat"))
            .expect("area fixture copy");
        fs::copy(
            source.join("Meshes/Hero.b3d"),
            root.join("Server Data/Scripts/Quest.rsl"),
        )
        .expect("script fixture copy");
        fs::copy(
            source.join("Meshes/Hero.b3d"),
            root.join("Server Data/Privileged Scripts.dat"),
        )
        .expect("vault fixture copy");
        Self { root }
    }

    pub(crate) fn root(&self) -> &Path {
        &self.root
    }

    pub(crate) fn project(&self) -> FeedbackProject {
        load_feedback_project(self.root.clone(), |_| {}).expect("feedback fixture")
    }

    pub(crate) fn add_missing_mesh(&self) {
        fs::copy(
            self.root.join("Meshes/Hero.b3d"),
            self.root.join("Meshes/Mage.b3d"),
        )
        .expect("external fixture repair");
    }
}

impl Drop for ReloadFixture {
    fn drop(&mut self) {
        fs::remove_dir_all(&self.root).expect("fixture cleanup");
    }
}
