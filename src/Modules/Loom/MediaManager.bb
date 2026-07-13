Strict

// =============================================================================
// Loom/MediaManager.bb -- portable editing operations for GUE's "Media" tab.
// =============================================================================
//
// GUE's Media tab (GUE.bb ~407-446 gadgets, ~6325-6553 events) manages the
// four asset catalogs (Meshes / Textures / Sounds / Music) stored as
// Data\Game Data\*.dat index files. Its editing operations are:
//
//   - Add New File   -> AddXToDatabase   (import a file, register an ID)
//   - Remove File    -> RemoveXFromDatabase
//   - Mesh scale     -> SetMeshScale     (per-mesh initial scale)
//
// Loom already BROWSES + PREVIEWS these catalogs (Texture/Mesh/Sound/Music
// catalogs + composer views). This module adds the missing EDITING half,
// routed through GUE's own Media.bb writers so the two editors cannot drift
// in the .dat format.
//
// IMMEDIATE-WRITE, NO DIRTY FLAG. Unlike the deferred-serialized kinds
// (Items / Actors / Spells ... which flip a *Saved global and persist on
// Ctrl+S / SaveAll), the media catalogs have NO *Saved flag and are NOT part
// of GUE's menuSaveAll (GUE.bb:10654). AddXToDatabase / RemoveXFromDatabase /
// SetMeshScale each seek+write the .dat the instant they're called. Loom
// follows that model faithfully: every operation below persists immediately,
// then rebuilds the affected Loom catalog so the Browser grid + Composer view
// reflect the change on the next frame. There is therefore no ribbon dirty
// badge / SaveAll / exit-prompt integration for media -- there is nothing to
// defer.
//
// FILE-DIALOG LIMITATION. GUE's "Add New File" pops a native Windows open
// dialog (FUI_CustomOpenDialog), lets the user pick a file anywhere on disk,
// then COPIES it into Data\Meshes\ (etc.) before registering. Loom's
// custom-draw convention has no native file dialog (see the sun-texture
// picker, PR #594, which reassigns to an already-cataloged ID rather than
// browsing disk). The portable subset implemented here REGISTERS a file that
// is ALREADY in place under the media folder, addressed by its relative
// path -- the same AddXToDatabase call GUE makes after the copy. Importing +
// copying a brand-new file from an arbitrary disk location remains a
// documented GUE-only capability.
//
// Stateless free-function module (per the skill's rule of thumb: no state
// across calls -> free functions). Operates on the databases + rebuilds the
// catalog pools, both of which are owned elsewhere.


// -----------------------------------------------------------------------------
// Remove -- route through RemoveXFromDatabase, then rebuild the catalog.
//
// refID is the catalog Index (the Threads::focus refID convention for these
// kinds). We resolve it to the engine-side ID before the DB call because the
// rebuild renumbers indices. RemoveXFromDatabase also UnloadX()es the live
// handle so a subsequent re-add of the same slot reloads cleanly.
//
// Catalog-only removal: we do NOT delete the file from disk. GUE prompts
// "Also delete file from disk? [Yes/No]"; Loom has no such modal here, so we
// take the non-destructive path (the composer's existing copy already frames
// this as "safe to remove from the catalog"). The on-disk asset survives and
// can be re-registered.
// -----------------------------------------------------------------------------
Function MediaManager_RemoveTexture%(refID%)
    Local te.TextureEntry = Textures_GetByIndex(refID)
    If te = Null Then Return False
    Local id% = te\ID
    Local nm$ = te\Filename$
    RemoveTextureFromDatabase(id)
    Textures_Rebuild()
    Timeline_RecordDelete("texture", refID, nm)
    Toast_Show("Removed texture " + nm, "danger")
    WriteLog(LoomLog, "MediaManager: removed texture #" + Str(id) + " " + nm)
    Return True
End Function

Function MediaManager_RemoveMesh%(refID%)
    Local mh.MeshEntry = Meshes_GetByIndex(refID)
    If mh = Null Then Return False
    Local id% = mh\ID
    Local nm$ = mh\Filename$
    RemoveMeshFromDatabase(id)
    Meshes_Rebuild()
    Timeline_RecordDelete("mesh", refID, nm)
    Toast_Show("Removed mesh " + nm, "danger")
    WriteLog(LoomLog, "MediaManager: removed mesh #" + Str(id) + " " + nm)
    Return True
End Function

Function MediaManager_RemoveSound%(refID%)
    Local sd.SoundEntry = Sounds_GetByIndex(refID)
    If sd = Null Then Return False
    Local id% = sd\ID
    Local nm$ = sd\Filename$
    RemoveSoundFromDatabase(id)
    Sounds_Rebuild()
    Timeline_RecordDelete("sound", refID, nm)
    Toast_Show("Removed sound " + nm, "danger")
    WriteLog(LoomLog, "MediaManager: removed sound #" + Str(id) + " " + nm)
    Return True
End Function

Function MediaManager_RemoveMusic%(refID%)
    Local mu.MusicEntry = Music_GetByIndex(refID)
    If mu = Null Then Return False
    Local id% = mu\ID
    Local nm$ = mu\Filename$
    RemoveMusicFromDatabase(id)
    Music_Rebuild()
    Timeline_RecordDelete("music", refID, nm)
    Toast_Show("Removed music " + nm, "danger")
    WriteLog(LoomLog, "MediaManager: removed music #" + Str(id) + " " + nm)
    Return True
End Function


// -----------------------------------------------------------------------------
// Add / register -- the portable subset of GUE's "Add New File".
//
// filename$ is relative to the media folder (Data\Meshes\, Data\Textures\,
// Data\Sounds\, Data\Music\) and MUST already exist on disk -- we validate
// with FileType(...)=1 (a plain file) so a typo can't register a phantom ID
// that every downstream GetX would then fail to load. This is exactly what
// GUE does after its copy step; we skip only the native-dialog browse + copy.
//
// AddXToDatabase returns the new engine-side ID (>= 0) or -1 (already present
// / no free slot). On success we rebuild the catalog and return the NEW
// catalog Index (for Threads::focus), or -1 on any failure.
// -----------------------------------------------------------------------------
Function MediaManager_AddTexture%(filename$, flags%)
    If filename = "" Then Return -1
    If FileType("Data\Textures\" + filename) <> 1 Then Return -1
    Local id% = AddTextureToDatabase(filename, flags)
    If id < 0 Then Return -1
    Textures_Rebuild()
    Local te.TextureEntry = Textures_GetByID(id)
    If te = Null Then Return -1
    Timeline_RecordCreate("texture", te\Index, filename)
    Toast_Show("Registered texture " + filename, "success")
    WriteLog(LoomLog, "MediaManager: added texture #" + Str(id) + " " + filename)
    Return te\Index
End Function

Function MediaManager_AddMesh%(filename$, isAnim%)
    If filename = "" Then Return -1
    If FileType("Data\Meshes\" + filename) <> 1 Then Return -1
    Local id% = AddMeshToDatabase(filename, isAnim)
    If id < 0 Then Return -1
    Meshes_Rebuild()
    Local mh.MeshEntry = Meshes_GetByID(id)
    If mh = Null Then Return -1
    Timeline_RecordCreate("mesh", mh\Index, filename)
    Toast_Show("Registered mesh " + filename, "success")
    WriteLog(LoomLog, "MediaManager: added mesh #" + Str(id) + " " + filename)
    Return mh\Index
End Function

Function MediaManager_AddSound%(filename$, is3D%)
    If filename = "" Then Return -1
    If FileType("Data\Sounds\" + filename) <> 1 Then Return -1
    Local id% = AddSoundToDatabase(filename, is3D)
    If id < 0 Then Return -1
    Sounds_Rebuild()
    Local sd.SoundEntry = Sounds_GetByID(id)
    If sd = Null Then Return -1
    Timeline_RecordCreate("sound", sd\Index, filename)
    Toast_Show("Registered sound " + filename, "success")
    WriteLog(LoomLog, "MediaManager: added sound #" + Str(id) + " " + filename)
    Return sd\Index
End Function

Function MediaManager_AddMusic%(filename$)
    If filename = "" Then Return -1
    If FileType("Data\Music\" + filename) <> 1 Then Return -1
    Local id% = AddMusicToDatabase(filename)
    If id < 0 Then Return -1
    Music_Rebuild()
    Local mu.MusicEntry = Music_GetByID(id)
    If mu = Null Then Return -1
    Timeline_RecordCreate("music", mu\Index, filename)
    Toast_Show("Registered music " + filename, "success")
    WriteLog(LoomLog, "MediaManager: added music #" + Str(id) + " " + filename)
    Return mu\Index
End Function


// -----------------------------------------------------------------------------
// Mesh initial scale -- SetMeshScale writes the Scale# float into the mesh's
// data record immediately (Media.bb:681). We update the cached MeshEntry in
// place rather than rebuilding, since scale doesn't change the catalog roster
// (same ID / filename / index) -- avoids the O(65535) re-walk for a scalar
// edit. refID is the catalog Index.
// -----------------------------------------------------------------------------
Function MediaManager_SetMeshScale%(refID%, scale#)
    Local mh.MeshEntry = Meshes_GetByIndex(refID)
    If mh = Null Then Return False
    SetMeshScale(mh\ID, scale#)
    mh\Scale# = scale#
    WriteLog(LoomLog, "MediaManager: set mesh #" + Str(mh\ID) + " scale " + Str(scale#))
    Return True
End Function
