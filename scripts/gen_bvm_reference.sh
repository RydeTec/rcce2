#!/usr/bin/env bash
#
# Regenerates docs/bvm-reference.md from the source of truth in
# src/Modules/RC_Standard_Invoker.bb (the declaration block consumed
# by the BlitzForge command-set parser) and the privilege gates in
# src/Modules/ScriptingCommands.bb.
#
# Usage:
#   ./scripts/gen_bvm_reference.sh         # writes docs/bvm-reference.md
#   ./scripts/gen_bvm_reference.sh --check # exits 1 if the doc is stale
#
# Phase 1 (this script): auto-generated reference with signature,
# category, and privilege column. No prose -- entries are mechanically
# extracted. Future phases can layer hand-curated usage notes on top
# of this output via a sidecar YAML.
#
# The generator parses lines of the form:
#   s = s + "Function NAME<BVM_IMPL>SIGIL(ARGS)"+Chr(10)
# from RC_Standard_Invoker.bb. SIGIL is "" / "%" / "$" / "#".
# ARGS is the parameter list (may be empty).
#
# Privilege detection: greps for BVM_RequirePrivileged() and
# BVM_RequireSelfOrPrivileged(...) calls in each function body in
# ScriptingCommands.bb.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INVOKER="$REPO_ROOT/src/Modules/RC_Standard_Invoker.bb"
COMMANDS="$REPO_ROOT/src/Modules/ScriptingCommands.bb"
OUTPUT="$REPO_ROOT/docs/bvm-reference.md"

CHECK_MODE=0
if [ "${1:-}" = "--check" ]; then
    CHECK_MODE=1
fi

if [ ! -f "$INVOKER" ]; then
    echo "ERROR: $INVOKER not found" >&2
    exit 2
fi
if [ ! -f "$COMMANDS" ]; then
    echo "ERROR: $COMMANDS not found" >&2
    exit 2
fi

# Step 1: build a privilege-gate index from ScriptingCommands.bb.
# Output is "BVM_NAME_UPPER|GATE" lines where GATE is "Privileged",
# "SelfOrPrivileged", or "None". Names are uppercased here so the
# downstream lookup is case-insensitive: RC_Standard_Invoker.bb uses
# uppercase <BVM_CREATEUDPSTREAM> declarations but ScriptingCommands.bb
# defines them as mixed-case Function BVM_CreateUDPStream. A
# case-sensitive lookup silently mis-labels the entire Networking
# section as "None" -- a security-relevant disclosure bug because
# the doc's stated purpose is communicating privilege gates.
gate_index=$(awk '
    /^Function BVM_/ {
        # Extract the function name (BVM_XYZ) and upper-case it.
        match($0, /Function (BVM_[A-Za-z0-9_]+)/, m)
        current = toupper(m[1])
        gate[current] = "None"
        next
    }
    /BVM_RequirePrivileged\(\)/ {
        if (current != "") gate[current] = "Privileged"
        next
    }
    /BVM_RequireSelfOrPrivileged\(/ {
        if (current != "" && gate[current] == "None") gate[current] = "SelfOrPrivileged"
        next
    }
    END {
        for (name in gate) print name "|" gate[name]
    }
' "$COMMANDS" | sort)

# Step 2: parse the declaration block. Emit
# "CATEGORY|NAME|BVM_IMPL|SIGIL|ARGS|GATE" lines.
declarations=$(awk -v gates="$gate_index" '
    BEGIN {
        # Load gates into an associative array.
        n = split(gates, lines, "\n")
        for (i = 1; i <= n; i++) {
            split(lines[i], parts, "|")
            if (parts[1] != "") gate[parts[1]] = parts[2]
        }
    }

    # Match: s = s + "Function NAME<BVM_IMPL>SIGIL(ARGS)"+Chr(10)
    /s = s \+ "Function [A-Z_][A-Z0-9_]*<BVM_[A-Za-z0-9_]+>/ {
        line = $0
        # Strip the prefix
        sub(/^.*s = s \+ "Function /, "", line)
        # Strip the trailing "+Chr(10). Anchor on the +Chr(10) literal
        # rather than the first `"` -- some declarations embed quotes
        # via Chr(34)+""+Chr(34) inside default-valued parameters
        # (e.g. SPAWN, THREADEXECUTE, SCRIPTLOG). Naively cutting at
        # the first `"` truncates the parameter list and the doc
        # would render `SPAWN()` instead of `SPAWN(PARAM1%, ...)`.
        sub(/"\+Chr\(10\).*$/, "", line)
        # Normalise the embedded `"+Chr(34)+"` (empty-string quote
        # idiom inside default-arg literals) to a single `"` so the
        # rendered signature shows `PARAM = ""` rather than the raw
        # Blitz string-builder shape.
        gsub(/"\+Chr\(34\)\+"/, "\"", line)
        # line now looks like: NAME<BVM_IMPL>SIGIL(ARGS) or NAME<BVM_IMPL>SIGIL or NAME<BVM_IMPL>(ARGS)

        # Extract NAME
        name_end = index(line, "<")
        if (name_end == 0) next
        name = substr(line, 1, name_end - 1)

        # Extract BVM_IMPL between < >
        rest = substr(line, name_end + 1)
        impl_end = index(rest, ">")
        if (impl_end == 0) next
        impl = substr(rest, 1, impl_end - 1)

        # The remainder after > may start with a sigil (% $ #) then optional (args)
        after = substr(rest, impl_end + 1)
        sigil = ""
        if (length(after) > 0) {
            c = substr(after, 1, 1)
            if (c == "%" || c == "$" || c == "#") {
                sigil = c
                after = substr(after, 2)
            }
        }

        # ARGS: between ( and )
        args = ""
        if (substr(after, 1, 1) == "(") {
            close_paren = index(after, ")")
            if (close_paren > 0) {
                args = substr(after, 2, close_paren - 2)
            }
        }

        # Category by name prefix
        category = "Misc"
        if (name ~ /^ACTOR/ || name ~ /^SETACTOR/ || name ~ /^SPAWN$/ || name ~ /^MOVEACTOR/ || name ~ /^ROTATEACTOR/) category = "Actor"
        else if (name ~ /^ITEM/ || name ~ /^GIVEITEM/ || name ~ /^SETITEM/ || name ~ /^SPAWNITEM/) category = "Item"
        else if (name ~ /^SPELL/ || name ~ /^CASTSPELL/) category = "Spell"
        else if (name ~ /^PARTY/) category = "Party"
        else if (name ~ /^PLAYER/) category = "Player"
        else if (name ~ /^ZONE/ || name ~ /^CREATEZONE/ || name ~ /^REMOVEZONE/) category = "World/Zone"
        else if (name ~ /^MYSQL/ || name ~ /^READFILE/ || name ~ /^WRITEFILE/ || name ~ /^APPENDFILE/ || name ~ /^DELETEFILE/ || name ~ /^OPENFILE/ || name ~ /^CLOSEFILE/ || name ~ /^FILEEXISTS/ || name ~ /^FILESIZE/ || name ~ /^CREATEDIR/ || name ~ /^FILETYPE/) category = "I/O & Persistence"
        else if (name ~ /^CREATEUDP/ || name ~ /^FREEUDP/ || name ~ /^SENDUDP/ || name ~ /^RECVUDP/ || name ~ /^UDP/) category = "Networking"
        else if (name ~ /^FACTION/) category = "Faction"
        else if (name ~ /^QUEST/) category = "Quest"
        else if (name ~ /^GLOBAL/ || name ~ /^SCRIPTGLOBAL/ || name ~ /^SUPERGLOBAL/) category = "Globals"
        else if (name ~ /^WEATHER/ || name ~ /^SETWEATHER/ || name ~ /^GETSEASON/ || name ~ /^SETSEASON/ || name ~ /^GETTIME/ || name ~ /^SETTIME/) category = "Time & Weather"
        else if (name ~ /^ATTRIBUTE/ || name ~ /^SETATTRIBUTE/ || name ~ /^CHANGEATTRIBUTE/) category = "Attributes"
        else if (name ~ /^GOLD/ || name ~ /^MONEY/ || name ~ /^XP$/ || name ~ /^GIVEXP/ || name ~ /^GIVEGOLD/ || name ~ /^GIVEMONEY/ || name ~ /^CHANGEGOLD/ || name ~ /^CHANGEMONEY/ || name ~ /^SETGOLD/ || name ~ /^SETMONEY/) category = "Currency & Progression"
        else if (name ~ /^THREAD/ || name ~ /^REFRESHSCRIPTS/ || name ~ /^EXECUTE/ || name ~ /^WAIT/) category = "Script Control"
        else if (name ~ /^SEND/ || name ~ /^CHAT/ || name ~ /^MSG/ || name ~ /^FLOAT/ || name ~ /^EMOTE/ || name ~ /^CREATEFLOATING/) category = "Chat & Effects"
        else if (name ~ /^OPENTRADING/ || name ~ /^TRADE/ || name ~ /^INVENTORY/) category = "Trade & Inventory"
        else if (name ~ /^RUNTIMEERROR/ || name ~ /^DEBUG/ || name ~ /^LOG/) category = "Diagnostic"
        else if (name ~ /^RAND/ || name ~ /^INT$/ || name ~ /^FLOAT$/ || name ~ /^SQR/ || name ~ /^SIN/ || name ~ /^COS/ || name ~ /^TAN/ || name ~ /^ABS/ || name ~ /^STR/ || name ~ /^CHR/ || name ~ /^ASC/ || name ~ /^LEN/ || name ~ /^LEFT/ || name ~ /^RIGHT/ || name ~ /^MID/ || name ~ /^UPPER/ || name ~ /^LOWER/ || name ~ /^TRIM/ || name ~ /^INSTR/) category = "String & Math"

        # Resolve gate (case-insensitive lookup; gate index is upper).
        # A name present in the opcode table but absent from the live
        # `Function BVM_*` index is DEAD API: the opcode survives in
        # RC_Standard_Invoker.bb for opcode-number stability, but the
        # implementation in ScriptingCommands.bb is commented out and the
        # dispatch case is a silent no-op (grep "DEAD-API" there). Classify
        # it as "Dead" rather than silently laundering it into "None" --
        # an ungated-callable row is an active footgun for scripters, who
        # would write the call, hit no error, and silently get nothing.
        g = gate[toupper(impl)]
        if (g == "") {
            g = "Dead"
            print "WARN: no live BVM impl for " impl " -- marking Dead (no-op) in reference" > "/dev/stderr"
        }

        print category "|" name "|" impl "|" sigil "|" args "|" g
    }
' "$INVOKER")

# Step 3: emit the markdown.
{
cat <<'HEADER'
# BVM scripting reference

Generated by `scripts/gen_bvm_reference.sh` from
[`src/Modules/RC_Standard_Invoker.bb`](../src/Modules/RC_Standard_Invoker.bb) and
[`src/Modules/ScriptingCommands.bb`](../src/Modules/ScriptingCommands.bb). **Do not edit this
file by hand** — rerun the generator after touching either source file.

This is the catalog of native functions callable from `.rsl` / `.rcscript` files that the
embedded BVM (Blitz Virtual Machine) compiles and runs. For an overview of how scripts are
loaded, dispatched, and gated, see [docs/modules/scripting.md](modules/scripting.md).

## Privilege legend

Each entry shows the privilege gate enforced at the top of the BVM implementation. The
gate determines whether the function is callable from a script spawned by an arbitrary
clicker, or only from a script the server itself spawned (or from a DM / GM):

| Gate | Meaning |
|---|---|
| `None` | Callable from any script. Pure-read or otherwise safe. |
| `SelfOrPrivileged` | Callable if the target actor is the script's spawning actor (`SI\AI`) OR the script was spawned privileged. **WRONG choice** for any function that must block clicker exploits in Examine / Trade / RightClick / ItemScript spawns (where `SI\AI = Handle(clicker)`) — use `Privileged` instead. |
| `Privileged` | Callable only from privileged scripts (server-spawned or DM-initiated). Mutates global state, opens host resources, or invokes terminal failures. |
| `Dead` | **Not callable — does nothing.** The opcode survives in the dispatch table for opcode-number stability, but the implementation is permanently disabled (commented out in `ScriptingCommands.bb`, marked `DEAD-API`) and the dispatch case is a silent no-op. A script that calls it compiles and runs without error but has no effect; do not write new scripts against it. |

See `CLAUDE.md`'s "Privilege gating in BVM commands" section for the full threat model.

## Sigil legend

The return-type sigil follows the BlitzForge convention: `%` = Int, `$` = String, `#` = Float,
none = void/Bool. Parameter sigils inside the argument list use the same.

---

HEADER

# Emit one section per category, in a stable order.
categories="Actor Item Spell Party Player World/Zone Faction Quest Globals Time-Weather Attributes Currency-Progression Script-Control Chat-Effects Trade-Inventory Networking I/O-Persistence String-Math Diagnostic Misc"

# Normalise category names for matching against the awk output.
# (We used spaces and ampersands in the awk script; here we map back.)
declare -A CATEGORY_LABEL=(
    ["Actor"]="Actor"
    ["Item"]="Item"
    ["Spell"]="Spell"
    ["Party"]="Party"
    ["Player"]="Player"
    ["World/Zone"]="World / Zone"
    ["Faction"]="Faction"
    ["Quest"]="Quest"
    ["Globals"]="Globals"
    ["Time & Weather"]="Time & Weather"
    ["Attributes"]="Attributes"
    ["Currency & Progression"]="Currency & Progression"
    ["Script Control"]="Script Control"
    ["Chat & Effects"]="Chat & Effects"
    ["Trade & Inventory"]="Trade & Inventory"
    ["Networking"]="Networking"
    ["I/O & Persistence"]="I/O & Persistence"
    ["String & Math"]="String & Math"
    ["Diagnostic"]="Diagnostic"
    ["Misc"]="Misc"
)

# Iterate in display order
DISPLAY_ORDER=(
    "Actor"
    "Item"
    "Spell"
    "Party"
    "Player"
    "Attributes"
    "Currency & Progression"
    "World/Zone"
    "Time & Weather"
    "Faction"
    "Quest"
    "Trade & Inventory"
    "Chat & Effects"
    "Script Control"
    "Globals"
    "Networking"
    "I/O & Persistence"
    "Diagnostic"
    "String & Math"
    "Misc"
)

for cat in "${DISPLAY_ORDER[@]}"; do
    rows=$(echo "$declarations" | awk -F'|' -v cat="$cat" '$1 == cat')
    if [ -z "$rows" ]; then
        continue
    fi
    label="${CATEGORY_LABEL[$cat]}"
    echo
    echo "## $label"
    echo
    echo "| Function | Signature | Gate |"
    echo "|---|---|---|"
    echo "$rows" | sort -t'|' -k2,2 | while IFS='|' read -r _cat name impl sigil args gate; do
        # Build signature column: NAME(args)[:return]
        if [ -n "$args" ]; then
            sig="\`$name($args)\`"
        else
            sig="\`$name()\`"
        fi
        if [ -n "$sigil" ]; then
            case "$sigil" in
                "%") sig="$sig : Int" ;;
                "\$") sig="$sig : String" ;;
                "#") sig="$sig : Float" ;;
            esac
        fi
        echo "| \`$name\` | $sig | $gate |"
    done
done

cat <<'FOOTER'

---

## See also

* [docs/modules/scripting.md](modules/scripting.md) — script lifecycle, entry-point names, privilege model overview.
* [`CLAUDE.md`](../CLAUDE.md) — agent-facing dev guide, including the four privilege-gate categories and the BVM clicker-handle trap.
* [`src/Modules/RC_Standard_Invoker.bb`](../src/Modules/RC_Standard_Invoker.bb) — the declaration block this catalog is derived from.
* [`src/Modules/ScriptingCommands.bb`](../src/Modules/ScriptingCommands.bb) — the BVM_* function bodies enforcing the gates.
FOOTER
} > "$OUTPUT.new"

if [ "$CHECK_MODE" = "1" ]; then
    if [ -f "$OUTPUT" ] && diff -q "$OUTPUT" "$OUTPUT.new" > /dev/null 2>&1; then
        rm -f "$OUTPUT.new"
        exit 0
    else
        echo "STALE: $OUTPUT does not match the regenerated version." >&2
        echo "Run: ./scripts/gen_bvm_reference.sh" >&2
        diff -u "$OUTPUT" "$OUTPUT.new" >&2 || true
        rm -f "$OUTPUT.new"
        exit 1
    fi
fi

mv "$OUTPUT.new" "$OUTPUT"
echo "Wrote $OUTPUT"
