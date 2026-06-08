<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**MediaDialogs.bb**

Editor-tool asset pickers. Four modal dialogs (Mesh / Texture / Sound / Music) that let the user browse a folder tree under `Data\` and pick a loaded media asset by name. Used by GUE, RC Architect, and other editor tools — not by the runtime game client.

Each dialog caches an in-memory name table snapshotted from the media subsystem's `GetMeshName$ / GetTextureName$ / GetSoundName$ / GetMusicName$` registries (0..65534, mirroring [`Media.bb`](media.md)'s ID-space) and presents them as a two-pane Folder + File listbox using the **`F-UI`** alternate UI toolkit (not Gooey).

## Conceptual overview

### The four dialogs

| Dialog | Window global | Filter constants |
|---|---|---|
| Mesh | `WMeshDialog` | `MeshDialog_All = 1`, `MeshDialog_Animated = 2`, `MeshDialog_Static = 3` |
| Texture | `WTextureDialog` | (no filter — all textures) |
| Sound | `WSoundDialog` | `SoundDialog_All = 1`, `SoundDialog_3D = 2`, `SoundDialog_Normal = 3` |
| Music | `WMusicDialog` | (no filter — all music) |

The Sound and Music dialogs additionally have a `BSoundDialogPlay` / `BMusicDialogPlay` button for in-dialog preview playback. The Mesh and Texture dialogs are pure pickers.

### Name caches: four 65535-slot string arrays

```basic
Dim MeshNames$(65534)
Dim TextureNames$(65534)
Dim SoundNames$(65534)
Dim MusicNames$(65534)
```

Populated once at `InitMediaDialogs()` time by iterating the entire ID space and calling `GetMeshName$(i) / GetTextureName$(i) / etc.` under the corresponding `LockMeshes() / LockTextures() / LockSounds() / LockMusic()` guards. **Snapshotted at init** — not refreshed as new assets are loaded. If a media asset is added to the registry after `InitMediaDialogs` runs, it won't appear in subsequent dialog invocations until the editor restarts.

The 65535-slot size mirrors the runtime [`Media.bb`](media.md) registry capacity. Empty slots store `""`.

### `F-UI` (Float-UI) — not Gooey

The dialogs allocate gadgets via `FUI_Window`, `FUI_Button`, `FUI_ListBox` — the **F-UI** toolkit in [`F-UI.bb`](../../src/Modules/F-UI.bb), not the Gooey toolkit used by the runtime in-game HUD. F-UI is the editor-tool flavor with different sizing semantics and absolute-pixel positioning (vs Gooey's `ClientWidth / ClientHeight` scaling). The window is created off-screen (`-1000, -1000`) and re-positioned at show time by the `ChooseXDialog(...)` entry points.

This means **MediaDialogs cannot be embedded in the runtime client** without re-implementing on Gooey, or pulling F-UI into the runtime include cascade. Today F-UI is only included by editor tool entry points.

### The `ChooseXDialog(filter, InitialFolder$, XPos = -1, YPos = -1)` entry points

Four parallel functions — `ChooseMeshDialog`, `ChooseTextureDialog`, `ChooseSoundDialog`, `ChooseMusicDialog` — implement the modal-event-loop pattern:

1. Reposition the dialog window to `(XPos, YPos)` (or screen-center if `-1`).
2. Populate the folder list via `FillXFolderList(LFolder, InitialFolder$)`.
3. Populate the file list via `FillXList(LDialog, InitialFolder$, filter)`.
4. Show the window and `Repeat ... Until` event loop with `FUI_HandleEvents()`:
   - Folder list click → re-fill file list at new folder.
   - File list click → enable OK button (or play preview for Sound/Music).
   - OK / Cancel button → exit loop with selected name or empty string.
5. Hide window; return selected `"<Folder>\<Filename>"` (or `""` on cancel).

### `FolderChangeHandler$(Name$, InitialFolder$)`

Helper that resolves the `(Previous folder)` parent-folder semantic in a folder-list click. If `Name$ = "(Previous folder)"`, returns `InitialFolder$` with its final path component stripped; otherwise returns `InitialFolder$ + "\" + Name$`. Used by all four dialogs.

### `FillXList` / `FillXFolderList` family — eight similar functions

The eight `Fill*` functions are direct parallels:

- `FillMeshesList`, `FillMeshesFolderList`
- `FillTexturesList`, `FillTexturesFolderList`
- `FillSoundsList`, `FillSoundsFolderList`
- `FillMusicList`, `FillMusicFolderList`

Each `Fill<X>List(List, Folder$, [type])` walks the corresponding `<X>Names$(65534)` array. For each entry, the algorithm scans **backward from end-of-name** to the last `\` or `/`, splits the name into `Name$` (basename) and `Path$` (parent directory), then accepts the entry if and only if `Upper$(Right$(Path$, Len(Folder$))) = Upper$(Folder$)` — a **case-insensitive suffix match on the parent directory**. A file at `Meshes\Goblins\warlord.b3d` has parent `Meshes\Goblins`; opening dialog at `Folder$ = "Goblins"` accepts it because `Right$("Meshes\Goblins", Len("Goblins"))` is `"Goblins"`. The empty `Folder$ = ""` case takes the special branch: accept iff `Name$` has no slash (top-level only). The (Sound / Mesh)-only type filter (`MeshType` / `SoundType`) is an additional gate on the same loop.

`FillXFolderList(List, Folder$)` is the same shape but emits **distinct sub-folder names**: walks all names, computes each name's path relative to `Folder$`, splits at the first `\` or `/`, and inserts each unique first-component as a folder entry. If `Folder$ <> ""` it prepends a `(Previous folder)` row that `FolderChangeHandler$` (see below) maps to the parent path.

The result is the standard two-pane folder browser: folders on top, files at bottom.

## Conventions for new code touching this module

- **`InitMediaDialogs` snapshots the name caches at init.** If you need post-init refresh (e.g. after loading a content pack at runtime), add a refresh function that re-iterates `0..65534` under the appropriate `Lock*` / `Unlock*` guard — don't re-call `InitMediaDialogs` (it re-allocates the dialog windows on top of the existing ones, leaking gadget handles).
- **All four dialogs share the same gadget-naming pattern** — `W<Asset>Dialog`, `L<Asset>Folder`, `L<Asset>Dialog`, `B<Asset>DialogOK`, `B<Asset>DialogCancel`, optional `B<Asset>DialogPlay`. New asset-picker dialogs (e.g. shaders, fonts) should mirror this exactly so editor-tool consumers can dispatch generically.
- **F-UI-only toolkit.** Don't mix Gooey and F-UI gadget calls within the same window — they have different event dispatch and re-flow semantics. The Mesh / Texture / Sound / Music dialogs are pure F-UI by convention.
- **`GetXName$(i)` returning `""` is the empty-slot sentinel** — `FillXList` skips entries with `Len = 0` so empty slots are silently filtered. New asset Types should keep the empty-string-means-empty convention.
- **Folder paths tolerate both `\` and `/` separators** — the path-split scan accepts either ([`MediaDialogs.bb:418`](../../src/Modules/MediaDialogs.bb#L418), `:461-462`). Asset paths in the wild are typically Windows-native backslash, but mixed-separator names are handled correctly.

## Related modules

- [`Media.bb`](media.md) — owns the `GetMeshName$ / GetTextureName$ / GetSoundName$ / GetMusicName$` registries and the underlying load functions. This module is a viewer / picker on top of it.
- [`F-UI.bb`](../../src/Modules/F-UI.bb) — the alternate UI toolkit used here. Provides `FUI_Window / Button / ListBox / HandleEvents`. Editor-tool-side; not part of the runtime client.
- [`Gooey.bb`](gooey.md) — the runtime UI toolkit (sibling of F-UI). Not used by this module.

## See also

- This module has no `LS_*` localization dependency — strings are hard-coded English ("Accept", "Cancel", "Choose Mesh", etc.). Editor tools typically aren't localized.
- No `SafeWrite` / atomic-write integration — this module is read-only against the media registries.

* * *

The legacy function-by-function reference for this module has not been generated. The conceptual overview above is the primary reference; consult the source at [`src/Modules/MediaDialogs.bb`](../../src/Modules/MediaDialogs.bb) for full signatures.

### Functions

- **`InitMediaDialogs()`** — snapshot all four name registries into the 65535-slot caches; allocate the four F-UI windows + their gadgets off-screen.
- **`ChooseMeshDialog(MeshType, InitialFolder$, XPos, YPos)`** — modal mesh picker. Returns the selected `"<Folder>\<Filename>"` or `""` on cancel.
- **`ChooseTextureDialog(InitialFolder$, XPos, YPos)`** — same, for textures.
- **`ChooseSoundDialog(SoundType, InitialFolder$, XPos, YPos)`** — same, for sounds. Has in-dialog preview-play.
- **`ChooseMusicDialog(InitialFolder$, XPos, YPos)`** — same, for music. Has in-dialog preview-play.
- **`FolderChangeHandler$(Name$, InitialFolder$)`** — resolve `..` parent-folder navigation.
- **`FillMeshesList(List, Folder$, MeshType)` / `FillMeshesFolderList(List, Folder$)`** — populate file and folder list-boxes for meshes in `Folder$`. The eight parallel `Fill*` functions follow the same shape, with one detail: `FillMeshesList` and `FillSoundsList` take a third `MeshType` / `SoundType` argument; `FillTexturesList` and `FillMusicList` are two-argument (no filter).
