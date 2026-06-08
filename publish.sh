#!/usr/bin/env bash
# RCCE2 release packager for macOS / Linux. Mirrors publish.bat.
# Builds the engine + tools, then copies runtime payloads into release/ for
# distribution. Forwards extra arguments to compile.sh so callers can pass
# `--blitz`, `--skip-tools`, etc.
set -euo pipefail

ROOTDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RELEASE_DIR="${ROOTDIR}/release"

"${ROOTDIR}/compile.sh" "$@"

cd "${ROOTDIR}"

rm -rf "${RELEASE_DIR}"
mkdir -p "${RELEASE_DIR}"

rsync -a "${ROOTDIR}/bin/" "${RELEASE_DIR}/bin/"
if [[ -f "${ROOTDIR}/bin/ReShade.ini.example" && ! -f "${RELEASE_DIR}/bin/ReShade.ini" ]]; then
  cp "${ROOTDIR}/bin/ReShade.ini.example" "${RELEASE_DIR}/bin/ReShade.ini"
fi

# Copy the freshly compiled Project Manager binary alongside the bin tree, so
# users can launch it directly from the release root just like the Windows
# zip layout.
if [[ -f "${ROOTDIR}/Project Manager.exe" ]]; then
  cp "${ROOTDIR}/Project Manager.exe" "${RELEASE_DIR}/"
fi
if [[ -f "${ROOTDIR}/Project Manager" ]]; then
  cp "${ROOTDIR}/Project Manager" "${RELEASE_DIR}/"
fi

for dir in data res docs; do
  if [[ -d "${ROOTDIR}/${dir}" ]]; then
    rsync -a "${ROOTDIR}/${dir}/" "${RELEASE_DIR}/${dir}/"
  fi
done

if [[ -d "${ROOTDIR}/extras/Freemake" ]]; then
  mkdir -p "${RELEASE_DIR}/extras/Freemake"
  rsync -a "${ROOTDIR}/extras/Freemake/" "${RELEASE_DIR}/extras/Freemake/"
fi

rm -f "${RELEASE_DIR}/res/Recent.dat"

# Generate the macOS build notes the README points users to. Only emitted on
# macOS so we don't lie about a Mach-O build on other hosts.
if [[ "$(uname -s)" == "Darwin" ]]; then
  arch="$(uname -m)"
  cat > "${RELEASE_DIR}/MACOS_NOTES.txt" <<EOF
This release was produced on macOS ${arch} via ./publish.sh.

macOS support is alpha — the BlitzForge runtime/linker path is incomplete and
many engine features are not yet wired up. Use this build for development and
feedback only; do not rely on it for shipping a game. Compatibility diagnostics
written by blitzcc -compat-report (when generated separately) live under
release/compat/*.compat.txt.
EOF
fi
