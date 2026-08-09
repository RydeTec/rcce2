[CmdletBinding()]
param(
    [string]$Project,
    [switch]$DebugBuild
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$manifest = Join-Path $repo 'editor-rs\Cargo.toml'
$rustup = Join-Path $HOME '.cargo\bin\rustup.exe'

if (-not (Test-Path -LiteralPath $rustup -PathType Leaf)) {
    throw "Rustup was not found at $rustup. Install the repository's Rust 1.85 toolchain first."
}

if ([string]::IsNullOrWhiteSpace($Project)) {
    $Project = Join-Path $repo 'data'
}
$Project = (Resolve-Path -LiteralPath $Project).Path

$targetRoot = Join-Path $env:LOCALAPPDATA 'RCCE\SuperEditorMVP\rust-1.85-target'
$env:CARGO_TARGET_DIR = $targetRoot

$cargoArgs = @(
    'run',
    '--manifest-path', $manifest,
    '--package', 'rcce-editor',
    '--locked'
)
if (-not $DebugBuild) {
    $cargoArgs += '--release'
}
$cargoArgs += @('--', '--project', $Project)

Write-Host "Launching the RCCE Super Editor feedback MVP"
Write-Host "Project: $Project"
Write-Host "Build cache: $targetRoot"
& $rustup run 1.85.0 cargo @cargoArgs
exit $LASTEXITCODE
