#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
canonical="$repo_root/docs/plans/plan/rust-super-editor-engine-migration.md"
index="$repo_root/docs/plans/plan/rust-super-editor-implementation.md"
workbook_dir="$repo_root/docs/plans/plan/rust-super-editor"

mapfile -t workbooks < <(find "$workbook_dir" -maxdepth 1 -type f -name 'm[0-9]*-*.md' | sort -V)
if [[ ${#workbooks[@]} -ne 11 ]]; then
    echo "expected 11 milestone workbooks, found ${#workbooks[@]}" >&2
    exit 1
fi

expected="$(seq 1 95)"
canonical_tasks="$(
    sed -n '/^### Task breakdown$/,/^### Rollout \/ migration notes$/p' "$canonical" \
        | sed -nE 's/^([0-9]+)\..*/\1/p'
)"
if [[ "$canonical_tasks" != "$expected" ]]; then
    echo "canonical task breakdown is not the exact sequence 1..95" >&2
    diff -u <(printf '%s\n' "$expected") <(printf '%s\n' "$canonical_tasks") || true
    exit 1
fi

declared_tasks="$(
    sed -n 's/^- \*\*Canonical tasks\*\*: //p' "${workbooks[@]}" \
        | grep -oE '[0-9]+' \
        | sort -n
)"
if [[ "$declared_tasks" != "$expected" ]]; then
    echo "workbook task declarations do not cover each task 1..95 exactly once" >&2
    diff -u <(printf '%s\n' "$expected") <(printf '%s\n' "$declared_tasks") || true
    exit 1
fi

expected_range_for() {
    case "$(basename "$1")" in
        m0-compatibility-laboratory.md) seq 1 8 ;;
        m1-read-only-project-platform.md) seq 9 19 ;;
        m2-lossless-command-storage.md) seq 20 33 ;;
        m3-canonical-records.md) seq 34 43 ;;
        m4-media-lifecycle.md) seq 44 49 ;;
        m5-paired-world.md) seq 50 59 ;;
        m6-specialist-authoring.md) seq 60 67 ;;
        m7-scripts-playtest.md) seq 68 74 ;;
        m8-external-administration.md) seq 75 80 ;;
        m9-conversion-publication.md) seq 81 88 ;;
        m10-retirement.md) seq 89 95 ;;
        *) echo "unknown milestone workbook: $1" >&2; return 1 ;;
    esac
}

for workbook in "${workbooks[@]}"; do
    actual_range="$(
        sed -n 's/^- \*\*Canonical tasks\*\*: //p' "$workbook" \
            | grep -oE '[0-9]+' \
            | sort -n
    )"
    expected_range="$(expected_range_for "$workbook")"
    if [[ "$actual_range" != "$expected_range" ]]; then
        echo "wrong canonical range in ${workbook#$repo_root/}" >&2
        diff -u <(printf '%s\n' "$expected_range") <(printf '%s\n' "$actual_range") || true
        exit 1
    fi
done

for workbook in "${workbooks[@]}"; do
    for section in Identity Outcome Verification 'Exit gate' Non-goals; do
        if ! grep -Fqx "## $section" "$workbook"; then
            echo "missing section '$section' in ${workbook#$repo_root/}" >&2
            exit 1
        fi
    done
done

check_links() {
    local source="$1"
    local source_dir target clean
    source_dir="$(dirname "$source")"
    while IFS= read -r target; do
        case "$target" in
            ''|'#'*|http://*|https://*|mailto:*) continue ;;
        esac
        clean="${target%%#*}"
        clean="${clean%%\?*}"
        clean="${clean#<}"
        clean="${clean%>}"
        if [[ ! -e "$source_dir/$clean" ]]; then
            echo "broken local link '$target' in ${source#$repo_root/}" >&2
            exit 1
        fi
    done < <(perl -ne 'while (/\]\(([^)]+)\)/g) { print "$1\n" }' "$source")
}

check_links "$index"
for workbook in "${workbooks[@]}" "$workbook_dir/starting-increments.md"; do
    check_links "$workbook"
done

echo "super-editor plan check passed: 95 canonical tasks, 11 exact milestone ranges, required sections, local links"
