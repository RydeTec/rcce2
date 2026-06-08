<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**Media.bb**

The unified asset registry — meshes, textures, sounds, music. Owns four file-backed `.dat` index databases (`Data\Game Data\{Meshes,Textures,Sounds,Music}.dat`), the in-memory caches that load entries on demand, the duplicate-rejecting add path, the slow rebuild-and-rewrite remove path, and the bounded-filename + path-traversal defenses applied at every load site.

Every other module that mentions a `MeshID` / `TextureID` / `SoundID` / `MusicID` indexes into this module's registries. The IDs are 16-bit positive (`0..65534`); ID `65535` and negative values are reserved sentinels meaning "no asset".

This module is **shared between engine and tools.** It's deliberately self-contained (no `Logging.bb` dependency for `WriteLog` / `ReadBoundedString$`) so editor tools (`RC Architect`, GUE, Terrain Editor) can include it without pulling the full server-side helper graph.

## Conceptual overview

### The nine global registries

| Global | Width per slot | Purpose |
|---|---|---|
| `LoadedTextures(65534)` | int (Blitz Texture handle) | Cached texture entity. `0` = not yet loaded. |
| `LoadedMeshes(65534)` | int (Blitz Mesh entity) | Cached mesh entity. `0` = not yet loaded. |
| `LoadedMeshScales#(65534)` | float | Per-mesh uniform scale factor. |
| `LoadedMeshX#(65534)` / `LoadedMeshY#(65534)` / `LoadedMeshZ#(65534)` | float | Per-mesh offset for positioning (`SetMeshOffset` mutator). |
| `LoadedMeshShaders(65534)` | int | Optional shader ID per mesh (`SetMeshShader` mutator). |
| `LoadedSounds(65534)` | int (Blitz Sound handle) | Cached sound handle. `0` = not yet loaded. |
| `TextureFlags(65534)` | int | The flag bits the texture was originally loaded with — needed by `CopyTexture` since Blitz's `CreateTexture` re-takes the flag list. |

All nine are `Dim`ed `(65534)` at file scope ([`Media.bb:1-9`](../../src/Modules/Media.bb#L1)), giving **65535 slots indexed `0..65534`** (Blitz3D `Dim X(N)` is inclusive — see CLAUDE.md → "Gotchas"). New asset adds get the **first** free ID via the `For ID = 0 To 65534 ... If DataAddress = 0` walk in `AddXToDatabase`.

Per-music has no in-memory cache global — music is loaded fresh each time it plays. The `Music.dat` index file is still maintained on disk.

### Lock state (batched-lookup optimization)

```basic
Global LockedMeshes = 0, LockedTextures = 0, LockedSounds = 0, LockedMusic = 0
```

When code is about to do many `GetX(ID)` / `GetXName$(ID)` calls in a tight loop, it can call `LockXes()` first. The lock function `OpenFile`s the index `.dat` and stores the handle in `LockedX`. Subsequent `GetX` calls test `If LockedX = 0` and reuse the open handle instead of re-opening per call. `UnlockXes()` closes the handle and clears `LockedX`. The batching saves ~milliseconds per asset during inventory population, login character lists, and editor-tool refresh.

The Lock/Unlock pattern is **not a mutex** — it's a file-handle cache. There is no concurrent-access protection (Blitz3D is single-threaded at the engine layer; the only threads are the `SQLDLL` worker threads in [`MySQL.bb`](mysql.md), which don't touch Media).

### File format — flat index + variable record stream

Each `.dat` (`Meshes`, `Textures`, `Sounds`, `Music`) follows the same layout:

```
0..262139      Index table — 65535 entries × 4 bytes each.
               Each entry is a DataAddress (file offset) for the record
               belonging to that asset ID. Address 0 = empty slot.

262140..EOF    Record stream — variable-length records, packed back-to-back.
               No length prefix on the records themselves; readers seek to
               the address in the index and parse forward.
```

Record shapes (per asset type):

| Asset | Record fields (in order) |
|---|---|
| Mesh | `Byte IsAnim`, `Float Scale`, `Float X`, `Float Y`, `Float Z`, `Short Shader`, `String Name` |
| Texture | `Short Flags`, `String Name` |
| Sound | `Byte Is3D`, `String Name` |
| Music | `String Name` |

`String` here is the legacy Blitz `WriteString` shape: a 4-byte `Int` length prefix followed by raw bytes. The reader counterpart is the module-local `MediaReadFilename$(F, MaxLen=260)` helper, which:

- Returns `""` on `L < 0` or `L > MaxLen` (rejects tampered length prefixes).
- Returns `""` on `L = 0`.
- Reads `L` bytes one at a time, `Exit`-ing the loop on early EOF.

`MediaReadFilename$` is duplicated locally rather than imported from [`Logging.bb`](logging.md)'s `ReadBoundedString$` so the editor Tools can include this file without pulling in the full logging substrate ([`Media.bb:18-22`](../../src/Modules/Media.bb#L18) audit comment).

### Three security defenses (applied where each is load-bearing)

Defenses are layered but not uniformly applied — each is applied only where the relevant attack surface exists:

1. **ID-range gate** — `If ID < 0 Or ID > 65534 Then Return 0/""/False`. Catches sentinel IDs (`-1` is used in some Actors paths to mean "no asset") and out-of-range values. Without the gate, `SeekFile F, ID * 4` would seek to a negative or far-past-EOF offset, with Blitz3D-undefined behavior. **Applied in every function that takes an `ID` argument:** `GetMesh`, `GetTexture`, `GetSound`, `GetMeshName$`, `GetTextureName$`, `GetSoundName$`, `GetMusicName$`, `GetMeshNameClean$`, `SetMeshScale`, `SetMeshOffset`, `SetMeshShader`, `UnloadMesh`, `UnloadTexture`, `UnloadSound`.

2. **Bounded filename read** — `MediaReadFilename$(F, 260)`. 260 is a generous Windows-MAX_PATH ceiling. A corrupted or tampered `Meshes.dat` could otherwise carry a wild `Int` length prefix and hang the client allocating gigabytes for one filename. See the audit-comment block at [`Media.bb:812-823`](../../src/Modules/Media.bb#L812). **Applied in every function that reads a name from the `.dat` index:** `GetMesh`, `GetTexture`, `GetSound`, `GetMeshName$`, `GetTextureName$`, `GetSoundName$`, `GetMusicName$`, `GetMeshNameClean$`, plus the dup-check scans in `AddXToDatabase` and the rebuild-stream walks in `RemoveXFromDatabase`. Not relevant for `Set*` or `Unload*` (no name read).

3. **Path-traversal rejection** — `If Instr(Name$, "..") > 0 Then Return 0`. The update channel can rewrite `.dat` files in place; a hostile update payload could plant `..\..\<x>` in a stored filename and force `LoadMesh` / `LoadTexture` / `LoadSound` against an arbitrary file path. The traversal check rejects before `LoadX` is called. **Applied only in `GetMesh` ([Media.bb:832](../../src/Modules/Media.bb#L832)), `GetTexture` ([Media.bb:910](../../src/Modules/Media.bb#L910)), and `GetSound` ([Media.bb:954](../../src/Modules/Media.bb#L954))** — the three functions that concatenate the stored name into a `LoadX("Data\<dir>\" + Name$)` filesystem call. The `*Name$` accessors and `*NameClean$` return the name to the caller; if a downstream caller of `GetMeshName$` builds its own `LoadMesh` path, the caller must `Instr` itself (none currently do — `GetMeshName$` is consumed only by `MediaDialogs.bb` for UI display, and the `..` substring would just appear in the picker).

The three defenses are layered where they overlap (the load functions) — even if a future bug bypasses the bound check, the bounded read still caps allocation; even if both bypass, the path-traversal check still blocks the read. New asset Types (e.g. shaders, fonts) added to this module that build a `LoadX("Data\<dir>\" + Name$)` path must apply all three.

### `CreateDatabase` — atomic-zero-init via SafeWrite

```basic
Function CreateDatabase(Filename$)
    Local TempPath$ = SafeWriteOpen$(Filename$)
    F = WriteFile(TempPath$)
    If F = 0 Then Return False
    For ID = 0 To 65534 : WriteInt F, 0 : Next
    Return SafeWriteCommit%(TempPath$, Filename$, F)
End Function
```

Initializes a fresh `.dat` index by writing 65535 zero ints (`~256 KB`). Atomic via `SafeWriteOpen` / `SafeWriteCommit` — a crash mid-init would otherwise leave a truncated index; subsequent loads would `ReadInt` past EOF, which in Blitz3D returns 0 silently (no error), so missing entries would become "ID 0" — the classic "Meshes.dat is corrupted" mystery-load bug. The audit comment at [`Media.bb:97-104`](../../src/Modules/Media.bb#L97) records the threat history.

`CreateDatabase` is the only `WriteFile`-to-production-path site in this module. All other writes (`AddXToDatabase`, `SetMeshScale`, etc.) happen in-place on the existing `.dat` — but only modify the index entry or append to the record stream, never truncating the file. A crash during an in-place modification can corrupt one record but the file as a whole survives.

### Add / Remove pattern

**Add** is fast — `AddXToDatabase`:
1. Walks the existing record stream once to detect duplicates (`Upper$(Name$) = Upper$(Filename$)`). Returns `-1` on dup.
2. Scans the index table for the first `DataAddress = 0` slot (= first free ID).
3. Writes the new record at `FileSize(...)` and the index entry at `ID * 4`.

**Remove** is slow — `RemoveXFromDatabase`:
1. Walks the index, builds a `LoadedMediaData` Type instance per non-removed entry (in-memory scratch).
2. Walks the record stream, populates each Type instance with its record fields.
3. `CreateDatabase` blanks the file (atomic rewrite via SafeWrite — preserves a `.bak`).
4. Walks every `LoadedMediaData` and writes it back, appending records to the empty file.
5. `Delete Each LoadedMediaData`.

The cost difference is O(1)-vs-O(N), so editor-tool remove operations show a visible pause; add operations are instant.

> **`AddMusicToDatabase` fall-through bug — closed.** Prior to the fix at [`Media.bb:512-518`](../../src/Modules/Media.bb#L512), the duplicate-check `If Upper$(Name$) = Upper$(Filename$)` closed the file but then **fell through** to the "find first free ID" insert path. Every duplicate music asset was added a second time. The fix adds `Return -1` inside the dup branch. The Music path was the only family with this bug — Mesh / Texture / Sound dup branches all `Return -1` correctly.

### `LoadedMediaData` Type — scratch only

```basic
Type LoadedMediaData
    Field ID, DataAddress, Name$, ExtraData, Shader
    Field Scale#, X#, Y#, Z#
End Type
```

Used **only** by `RemoveXFromDatabase` to hold the not-being-removed records in memory while the file is rewritten. Always created with `New LoadedMediaData` at the start of a remove call; always `Delete Each LoadedMediaData`-d at the end. `ExtraData` is reused across asset types — `Byte` for mesh `IsAnim` / sound `Is3D`, `Short` for texture `Flags`.

### `MeshMinMaxVertices` Type + the two recursive vertex-bounds helpers

```basic
Type MeshMinMaxVertices
    Field MinX#, MaxX#
    Field MinY#, MaxY#
    Field MinZ#, MaxZ#
End Type
```

`MeshMinMaxVertices(EN)` and `MeshMinMaxVerticesTransformed(EN, Pitch, Yaw, Roll, ScaleX, ScaleY, ScaleZ)` recursively walk a mesh + its children, accumulating the AABB of every vertex. Allocated fresh per call; caller's responsibility to `Delete` the returned Type instance. The transformed variant applies a pitch/yaw/roll rotation matrix to each vertex before bounding — used by editor tools to preview rotated placements.

`SizeEntity(EN, Width, Height, Depth, Uniform=False)` is the convenient caller — measures, scales, deletes the scratch Type. The `Uniform=True` mode picks the smallest of the three computed scale factors and applies it on all three axes (preserves aspect ratio).

### `CopyTexture(Tex, Flags)` — pixel-level copy

Manual pixel-by-pixel copy via `LockBuffer` / `CopyPixelFast` / `UnlockBuffer`. Needed because Blitz3D's native `CopyImage` / `CopyEntity` don't deep-copy texture data, so the duplicate would share the original's pixels. The `Flags` argument lets the caller request different texture flags on the copy (e.g. converting a mip-mapped texture to non-mip).

`GetTexture(ID, Copy=True)` and a handful of editor sites are the only callers.

## Conventions for new code touching this module

- **Every Get/Set/Unload that takes an ID must guard `If ID < 0 Or ID > 65534`** at function entry. Negative IDs `SeekFile` to negative offsets; OOB-positive walks past the index table. The pattern is canonical — see every existing `Get*` function for the shape.
- **`MediaReadFilename$(F, 260)` is the only safe filename-read.** Don't use raw `ReadString$` in this module — a wild length prefix is the classic OOM vector.
- **`Instr(Name$, "..") > 0` is the canonical path-traversal guard.** Every `LoadX("Data\<dir>\" + Name$)` call site checks first. Without the guard, an update-channel-tampered `.dat` could redirect asset loads to arbitrary filesystem paths.
- **New asset Types** need: a `Dim Loaded<X>(65534)` cache; a `Get<X>(ID)`, `Get<X>Name$(ID)`, `Set<X>(...)`, and `Unload<X>(ID)` family; an `Add<X>ToDatabase` / `Remove<X>FromDatabase` pair; a `Lock<X>es()` / `Unlock<X>es()` for batched lookups; and `CreateDatabase("Data\Game Data\<X>.dat")` for the index file. The boilerplate is heavy but the shape is rigid — copy the existing Mesh family as a template.
- **`CreateDatabase` is the only SafeWrite site** in this module. In-place record edits use direct `WriteFile` to the open `.dat` — fine because they never truncate. Don't introduce a new SafeWrite site without a corresponding atomic-replace need.
- **`AddXToDatabase` dup checks must `Return -1` inside the dup branch.** The Music fall-through bug is a cautionary tale.
- **`UnloadX` is `Free<Resource>` + null the cache slot.** Don't clear the `.dat` index entry — `UnloadX` is a memory-only operation; `RemoveXFromDatabase` is the on-disk delete.

## Related modules

- [`Logging.bb`](logging.md) — provides `SafeWriteOpen$` / `SafeWriteCommit%` used by `CreateDatabase`. (Has its own `ReadBoundedString$`, which Media duplicates locally as `MediaReadFilename$` to avoid the include dependency.)
- [`Actors3D.bb`](actors3d.md) — heavy consumer of `GetMesh` / `LoadedMeshScales#(MeshID)` per actor body part.
- [`MediaDialogs.bb`](mediadialogs.md) — editor-tool asset pickers; snapshots all four name caches via `GetMeshName$` / `GetTextureName$` / `GetSoundName$` / `GetMusicName$` at init.
- [`Projectiles3D.bb`](projectiles3d.md) — `GetMesh(MeshID, Duplicate=True)` for projectile mesh allocation.
- [`Environment3D.bb`](environment3d.md) — terrain / scenery mesh loads.
- [`RC Architect`](../../src/Tools/) (editor tool) — calls `AddMeshToDatabase` / `AddTextureToDatabase` etc. when the user imports an asset.

## See also

- CLAUDE.md → "Atomic writes" — `CreateDatabase` is one of the migration-template sites for `SafeWriteOpen` / `SafeWriteCommit`.
- CLAUDE.md → "Bounds checks before array index" — Media's ID-range gates are textbook examples.
- CLAUDE.md → "Gotchas" → "Blitz3D array semantics" — the `Dim X(65534)` = 65535 slots indexed `0..65534` rule.

* * *

The legacy function-by-function reference for this module has not been generated. The conceptual overview above is the primary reference; consult the source at [`src/Modules/Media.bb`](../../src/Modules/Media.bb) for full signatures. The module exports 36 functions across the four asset families plus the `MeshMinMaxVertices` / `SizeEntity` / `CopyTexture` utility group.
