#!/usr/bin/env bash
# RCCE2 build entry point for macOS / Linux. Mirrors compile.bat.
#
# Mac/Linux uses the BlitzForge `blitzcc` Mach-O / ELF binary at
# `compiler/BlitzForge/bin/blitzcc`. macOS support in BlitzForge is alpha and
# coverage of the engine sources is incomplete — see ReadMe.md for current
# status.
set -euo pipefail

ROOTDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BLITZPATH="${ROOTDIR}/compiler/BlitzForge"

TOOLCHAIN=0
RCCETOOLS=1
RCCE=1
RUSTCLIENT=0

usage() {
  cat <<'EOF'
RCCE2 Compiler Script

Usage: compile.sh [flags]

  -t | --skip-tools     Skip compilation of the RCCE2 tool applications in src/Tools
  -b | --blitz          Compile the BlitzForge toolchain
  -e | --skip-engine    Skip compilation of the RCCE2 engine itself in src
  -r | --rust           Build the Rust client (client-rs) to bin/ClientRS (needs cargo)
  -h | --help           Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -b|--blitz) TOOLCHAIN=1 ;;
    -t|--skip-tools) RCCETOOLS=0 ;;
    -e|--skip-engine) RCCE=0 ;;
    -r|--rust) RUSTCLIENT=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown flag: $1" >&2; usage; exit 1 ;;
  esac
  shift
done

# Resolve which blitzcc binary to invoke — Mach-O/ELF on Unix, .exe fallback so
# this also works under WSL or Git Bash on Windows.
resolve_blitzcc() {
  local unix="${BLITZPATH}/bin/blitzcc"
  local win="${BLITZPATH}/bin/blitzcc.exe"
  if [[ -x "${unix}" ]]; then
    echo "${unix}"
  elif [[ -f "${win}" ]]; then
    echo "${win}"
  else
    echo ""
  fi
}

if [[ "${TOOLCHAIN}" -eq 1 ]]; then
  echo "Compiling BlitzForge Toolchain..."
  if [[ ! -d "${BLITZPATH}/.git" ]]; then
    (cd "${ROOTDIR}" && git submodule update --init --recursive) \
      || { echo "Failed to initialize submodules." >&2; exit 1; }
  fi
  if [[ ! -x "${BLITZPATH}/compile.sh" ]]; then
    echo "BlitzForge compile.sh not found at ${BLITZPATH}/compile.sh." >&2
    echo "The pinned BlitzForge commit may predate macOS/Linux build support." >&2
    exit 1
  fi
  "${BLITZPATH}/compile.sh"
fi

if [[ "${RCCE}" -eq 1 || "${RCCETOOLS}" -eq 1 ]]; then
  BLITZCC="$(resolve_blitzcc)"
  if [[ -z "${BLITZCC}" ]]; then
    echo "${BLITZPATH}/bin/blitzcc not found!" >&2
    echo "Compile source with ./compile.sh -b, or download binaries from" >&2
    echo "  https://github.com/RydeTec/blitz-forge/releases" >&2
    exit 1
  fi
fi

# Drop the .ico flag on macOS until BlitzForge supports native icon embedding.
# See https://github.com/RydeTec/blitz-forge — fix/custom-exe-icons.
# Only true Windows bash shells (MSYS/MinGW/Cygwin) emit .exe binaries and
# embed the .ico. macOS AND Linux produce suffix-less binaries and don't embed
# a Windows icon — previously everything non-Darwin was treated as Windows,
# which broke the Linux build (it looked for client-window.exe).
IS_WINDOWS=0
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) IS_WINDOWS=1 ;;
esac

ICON_FLAG=()
if [[ "$IS_WINDOWS" == "1" && -f "${ROOTDIR}/res/Icon.ico" ]]; then
  ICON_FLAG=(-n "${ROOTDIR}/res/Icon.ico")
fi

# Match compile.bat output names on macOS/Linux: drop the .exe extension so that
# `Project Manager` (no suffix) launches as the README documents.
EXE_SUFFIX=""
if [[ "$IS_WINDOWS" == "1" ]]; then
  EXE_SUFFIX=".exe"
fi

if [[ "${RCCE}" -eq 1 ]]; then
  echo "Compiling RealmCrafter CE Engine..."
  mkdir -p "${ROOTDIR}/bin"
  cd "${ROOTDIR}/src"

  "${BLITZCC}" -o "${ROOTDIR}/bin/Server${EXE_SUFFIX}" \
    "${ROOTDIR}/src/Server.bb"
  "${BLITZCC}" -o "${ROOTDIR}/Project Manager${EXE_SUFFIX}" "${ICON_FLAG[@]}" \
    "${ROOTDIR}/src/Project Manager.bb"
  "${BLITZCC}" -o "${ROOTDIR}/bin/GUE${EXE_SUFFIX}" "${ICON_FLAG[@]}" \
    "${ROOTDIR}/src/GUE.bb"
  "${BLITZCC}" -o "${ROOTDIR}/bin/Loom${EXE_SUFFIX}" "${ICON_FLAG[@]}" \
    "${ROOTDIR}/src/Loom.bb"
  "${BLITZCC}" -o "${ROOTDIR}/bin/Client${EXE_SUFFIX}" "${ICON_FLAG[@]}" \
    "${ROOTDIR}/src/Client.bb"
fi

if [[ "${RCCETOOLS}" -eq 1 ]]; then
  echo "Compiling RealmCrafter CE Tools..."
  mkdir -p "${ROOTDIR}/bin/tools"

  if [[ -d "${ROOTDIR}/src/Tools" ]]; then
    TOOLSDIR="${ROOTDIR}/src/Tools"
  elif [[ -d "${ROOTDIR}/src/tools" ]]; then
    TOOLSDIR="${ROOTDIR}/src/tools"
  else
    echo "Tools directory not found. Expected src/Tools or src/tools." >&2
    exit 1
  fi

  cd "${TOOLSDIR}"
  shopt -s nullglob nocaseglob
  tool_count=0
  for f in *.bb; do
    name="${f%.*}"
    "${BLITZCC}" -o "${ROOTDIR}/bin/tools/${name}${EXE_SUFFIX}" \
      "${ICON_FLAG[@]}" -w "${ROOTDIR}/src" "${TOOLSDIR}/${f}"
    tool_count=$((tool_count + 1))
  done
  shopt -u nullglob nocaseglob
  if [[ "${tool_count}" -eq 0 ]]; then
    echo "No tools found in ${TOOLSDIR}." >&2
  fi
fi

if [[ "${RUSTCLIENT}" -eq 1 ]]; then
  echo "Compiling RealmCrafter CE Rust client (client-rs)..."
  if ! command -v cargo >/dev/null 2>&1; then
    echo "  cargo not found on PATH -- install Rust from https://rustup.rs to build the Rust client. Skipping ClientRS." >&2
  else
    mkdir -p "${ROOTDIR}/bin"
    (cd "${ROOTDIR}/client-rs" && cargo build --release -p rcce-client --bin client-window)
    cp -f "${ROOTDIR}/client-rs/target/release/client-window${EXE_SUFFIX}" \
      "${ROOTDIR}/bin/ClientRS${EXE_SUFFIX}"
    echo "  Built bin/ClientRS${EXE_SUFFIX}"
  fi
fi

cd "${ROOTDIR}"
