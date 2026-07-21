use clap::Parser;
use std::path::PathBuf;

#[derive(Debug, Parser)]
#[command(about = "Validate an RCCE compatibility corpus without following links or writing files")]
struct Cli {
    /// Corpus manifest.toml to validate.
    #[arg(long)]
    manifest: PathBuf,
    /// Explicit P07 registry-v1.toml, required only for canary-bearing fixtures.
    #[arg(long)]
    canary_registry: Option<PathBuf>,
    /// Print the platform confinement capability and exit.
    #[arg(long)]
    platform_capability: bool,
    /// Fail closed unless transient hardlinks can be detected before results publish.
    #[arg(long)]
    require_transient_hardlink_detection: bool,
}

fn main() {
    let cli = Cli::parse();
    if cli.platform_capability {
        println!("{}", rcce_project_scanner::platform_capability());
        return;
    }
    if cli.require_transient_hardlink_detection {
        if let Err(error) = rcce_project_scanner::require_transient_hardlink_detection() {
            eprintln!("scan rejected: {error}");
            std::process::exit(2);
        }
    }
    match rcce_project_scanner::scan_manifest(&cli.manifest, cli.canary_registry.as_deref()) {
        Ok(report) => {
            let files: u64 = report.projects.iter().map(|project| project.files).sum();
            let bytes: u64 = report.projects.iter().map(|project| project.bytes).sum();
            let canaries: u64 = report.projects.iter().map(|project| project.canaries).sum();
            println!(
                "validated {} projects, {files} files, {bytes} bytes, {canaries} canaries",
                report.projects.len()
            );
        }
        Err(error) => {
            eprintln!("scan rejected: {error}");
            std::process::exit(2);
        }
    }
}
