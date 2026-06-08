#!/usr/bin/env bash
# RCCE2 test runner for macOS / Linux. Mirrors test.bat.
# Compiles every test source in src/Tests with `blitzcc -t`. Any failure marks
# the run as failed but the loop continues so you see every broken test in one
# pass.
#
# Usage:
#   ./test.sh              run every test file
#   ./test.sh ItemsTest    run only files whose basename contains "ItemsTest"
#                          (useful for reproducing the documented intermittent
#                          ItemsTest stack-overflow flake locally)
set -uo pipefail

ROOTDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BLITZPATH="${ROOTDIR}/compiler/BlitzForge"

if [[ -d "${ROOTDIR}/src/Tests" ]]; then
  TESTDIR="${ROOTDIR}/src/Tests"
elif [[ -d "${ROOTDIR}/src/tests" ]]; then
  TESTDIR="${ROOTDIR}/src/tests"
else
  echo "Test directory not found. Expected src/Tests or src/tests." >&2
  exit 1
fi

BLITZCC_UNIX="${BLITZPATH}/bin/blitzcc"
BLITZCC_WIN="${BLITZPATH}/bin/blitzcc.exe"
if [[ -x "${BLITZCC_UNIX}" ]]; then
  BLITZCC="${BLITZCC_UNIX}"
elif [[ -f "${BLITZCC_WIN}" ]]; then
  BLITZCC="${BLITZCC_WIN}"
else
  echo "${BLITZPATH}/bin/blitzcc not found!" >&2
  echo "Compile source with ./compile.sh -b, or download binaries from" >&2
  echo "  https://github.com/RydeTec/blitz-forge/releases" >&2
  exit 1
fi

FILTER="${1:-}"

cd "${TESTDIR}"

# Collect matching files first so we can report TOTAL up front and detect
# the "no files matched filter" case.
FILES=()
while IFS= read -r -d '' f; do
  if [[ -z "${FILTER}" || "$(basename "${f}")" == *"${FILTER}"* ]]; then
    FILES+=("${f}")
  fi
done < <(find "${TESTDIR}" -type f -name '*.bb' -print0)

TOTAL="${#FILES[@]}"

if [[ "${TOTAL}" -eq 0 ]]; then
  if [[ -n "${FILTER}" ]]; then
    echo "No test files matched filter \"${FILTER}\"" >&2
  else
    echo "No test files found in ${TESTDIR}" >&2
  fi
  exit 1
fi

if [[ -n "${FILTER}" ]]; then
  echo "Filter: only files matching *${FILTER}*.bb"
fi

PASSED=0
FAILED=0
FAILED_FILES=()

for f in "${FILES[@]}"; do
  name="$(basename "${f}")"
  echo "[RUN ] ${name}"
  if "${BLITZCC}" -t -w "${ROOTDIR}/src" "${f}"; then
    echo "[PASS] ${name}"
    PASSED=$((PASSED+1))
  else
    echo "[FAIL] ${name}"
    FAILED=$((FAILED+1))
    FAILED_FILES+=("${name}")
  fi
done

cd "${ROOTDIR}"

echo
echo "Ran ${TOTAL} files: ${PASSED} passed, ${FAILED} failed."

if [[ "${FAILED}" -gt 0 ]]; then
  echo "Failed files:"
  for f in "${FAILED_FILES[@]}"; do
    echo "  - ${f}"
  done
  echo "Tests failed"
  exit 1
fi

echo "Tests passed"
