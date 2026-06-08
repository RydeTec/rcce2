#!/usr/bin/env bash
# One-time toolchain setup for building RCCE2 on macOS (Apple Silicon).
#
# This is a thin wrapper that:
#   1. Initializes git submodules (so the BlitzForge submodule is populated).
#   2. Runs BlitzForge's own bootstrap_macos.sh to install Homebrew packages
#      required to build blitzcc + runtime/linker dylibs from source.
#
# After this script succeeds, run `./compile.sh -b` from the rcce2 root to
# build BlitzForge, then `./compile.sh` to build the engine and tools.
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "bootstrap_macos.sh is intended for macOS hosts." >&2
  exit 1
fi

SCRIPTDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOTDIR="$(cd "${SCRIPTDIR}/.." && pwd)"
BLITZPATH="${ROOTDIR}/compiler/BlitzForge"

if [[ ! -d "${BLITZPATH}/.git" || ! -f "${BLITZPATH}/ReadMe.md" ]]; then
  echo "BlitzForge submodule missing — initializing submodules..."
  (cd "${ROOTDIR}" && git submodule update --init --recursive)
fi

if [[ ! -x "${BLITZPATH}/scripts/bootstrap_macos.sh" ]]; then
  echo "BlitzForge macOS bootstrap not found at ${BLITZPATH}/scripts/bootstrap_macos.sh." >&2
  echo "The pinned BlitzForge commit may predate macOS support." >&2
  exit 1
fi

"${BLITZPATH}/scripts/bootstrap_macos.sh"

echo
echo "Toolchain ready. Next steps:"
echo "  ./compile.sh -b   # build BlitzForge (blitzcc + runtime/linker)"
echo "  ./compile.sh      # build the RCCE2 engine and tools"
