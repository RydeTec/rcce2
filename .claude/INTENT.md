# INTENT.md — RealmCrafter: Community Edition

The north star. What this product is striving to become at its best. Use this to evaluate proposed changes — does this PR move us closer to the vision below, or sideways, or away?

## Core thesis

RealmCrafter let a generation of solo developers and small teams ship **real persistent online worlds** without writing a renderer, a netcode stack, or a database layer from scratch. Then the original project stopped being maintained. The community refused to let it die. RCCE is the actively-developed continuation: same accessibility, modern foundations, free forever.

The accessibility is the point. A passionate hobbyist with a story to tell should be able to ship a working MMORPG. They should not need to be a C++ engineer. They should not need to understand DirectX or BSD sockets or threading models. They should be able to focus on world, characters, and gameplay — the things that actually make their game theirs.

## What "good" looks like

A new RCCE developer should be able to:

- **Ship a working demo world in a day.** Sample project loads, server runs, client connects, character walks around, NPCs respond. Friction-free.
- **Add a quest in an evening.** Open the editor, place a trigger, write a few lines of script, save, restart server, play.
- **Run a small public server reliably.** Process stays up for weeks. Players who lose connection reconnect cleanly. Bad actors cannot crash the world for everyone.
- **Trust the persistence layer.** Saves never corrupt. Player data survives crashes, power loss, hardware failures.
- **Extend without forking.** The BVM scripting surface is powerful enough that almost any game logic is a script change, not an engine change.
- **Get help.** GitHub Discussions, Discord, Discord-linked forum, fandom wiki — community is reachable and responsive.

## Values (in priority order)

1. **Server stability.** A live world's reliability bar is high. A single malformed packet, a single misconfigured area, a single deleted item ID must not crash the server. Every handler that touches client- or data-file-sourced input gets bounds-checked, Null-checked, soft-failed. The pattern: log + recover + continue.
2. **Player data integrity.** Saves are atomic (`SafeWriteOpen$` / `SafeWriteCommit%`). Save formats carry magic + version headers so old saves load cleanly and new saves can't be misread as old. Passwords are salted-hashed. Auth gates everything destructive.
3. **Hostile-input resilience on both sides.** A hostile or buggy server should not crash connected clients (mesh load failures soft-fail, packet validators bounds-check). A hostile client should not crash the server (every BVM gate, every wire-derived index, every list lookup is validated).
4. **Backwards compatibility for content.** Existing RealmCrafter projects, scripts, and assets should keep working. Format changes happen with version headers and load-old/write-new gates, never breaking changes.
5. **Cross-platform.** Windows x86_64 is stable. macOS Apple Silicon is alpha; the goal is parity. Linux is on the horizon.
6. **Modern toolchain.** BlitzForge compiler with Strict + GC + inheritance + methods + async; VS Code extension; GitHub Actions CI on every change; agent skills that prevent regression of established patterns.
7. **Community-driven, open process.** PRs from anyone welcome. Issues triaged. Decisions explained. No private branches with secret work; develop is the source of truth.

## What we're walking away from

- Closed-source frozen toolchain.
- Windows-32-bit-only assumptions.
- `RuntimeError(...)` for any failure, anywhere — the legacy "server crashes when a player feeds it weird input" posture is the bug class we keep fixing.
- Manual edits to network handlers without bounds checks. The skill `rcce2-packet-handler` captures the recovery pattern; new handlers follow it.
- Fork-the-engine-to-change-anything. Almost every gameplay change should be a script. Engine changes are for engine concerns.
- Mystery rituals. Every non-obvious thing gets a comment explaining *why*, or a skill, or a note in CLAUDE.md. The reasoning outlives the author.

## Long-horizon items

These are the bigger improvements still worth doing. Not every PR, but worth carrying in mind:

- **Reconnect dialog** instead of `RuntimeError(LS_LostConnection)` — keep the player and their state across network blips. *Phase 1 landed: all 12 `RuntimeError(LS_LostConnection)` call sites now route through a single `OnLostConnection()` helper in `ClientNet.bb` (PR #153). Phase 2 (replace the helper body with a non-fatal reconnect dialog) and phase 3 (RakNet rebind + session resume) still pending.*
- **Modern UI dispatch tables** — replace the giant `Select Case PacketType` and BVM opcode switch with `FunctionPtr[256]` dispatch tables for clarity and extensibility.
- **HUDPanel inheritance hierarchy** — collapse the 16 free-standing `GY_Create*` windows into typed panels using BlitzForge inheritance.
- **AI state-machine subtypes** — turn the 180-line `If/ElseIf AIMode = ...` switch into `Type AIState` subtypes with a `Tick` method.
- **PausedScript ↔ ScriptInstance unification** via inheritance.
- **Async/Await for `BVM_THREADEXECUTE`** — real coroutines instead of the current polling.
- **Delta-encoding for `UpdateActorInstances`** — drop bandwidth for high-density zones.
- **Sparse item attribute wire encoding** — 83 → ~15 bytes for typical items.
- **Linux runtime** — once BlitzForge gets there.
- **Update-channel manifest signing** — the channel currently trusts the server; signatures would close the trust chain.

These are sketches, not commitments. Each one is its own design conversation when it surfaces.

## How to use this file

When considering a change, ask: does it advance one of the seven values above without regressing another? Does it move us toward something on the long-horizon list, or at minimum not away from it? Is the *why* of the change something a future maintainer will be able to recover from the diff?

When in doubt, ship the smaller change with the clearer reasoning, and write the *why* down.
