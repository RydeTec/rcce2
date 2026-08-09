//! Safe placeholder for the sole installed project and migration command.

use std::ffi::OsStr;
use std::process::ExitCode;

const HELP_BODY: &str =
    "Read-only RCCE project CLI placeholder. No project operation is implemented.\n\n\
USAGE:\n    rcce-project [--help | --version]\n\n\
OPTIONS:\n    -h, --help       Print help\n    -V, --version    Print version";

fn main() -> ExitCode {
    let mut arguments = std::env::args_os().skip(1);
    let first = arguments.next();
    let has_extra = arguments.next().is_some();

    match (first.as_deref(), has_extra) {
        (None, false) => {
            println!("rcce-project: read-only placeholder; no project operation is implemented");
            ExitCode::SUCCESS
        }
        (Some(argument), false)
            if argument == OsStr::new("--help") || argument == OsStr::new("-h") =>
        {
            println!("rcce-project {}\n{HELP_BODY}", env!("CARGO_PKG_VERSION"));
            ExitCode::SUCCESS
        }
        (Some(argument), false)
            if argument == OsStr::new("--version") || argument == OsStr::new("-V") =>
        {
            println!("rcce-project {}", env!("CARGO_PKG_VERSION"));
            ExitCode::SUCCESS
        }
        _ => {
            eprintln!(
                "rcce-project: unsupported argument; only --help and --version are available"
            );
            ExitCode::from(2)
        }
    }
}
