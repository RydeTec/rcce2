Strict

// =============================================================================
// Loom/SoundCatalog.bb -- catalog of every defined sound in
// Data\Game Data\Sounds.dat, browseable as first-class entities.
// =============================================================================
//
// Sibling of TextureCatalog (iter 66) + MeshCatalog (iter 67). Completes
// the asset-trio. The composer view adds a "play" button so designers
// can audition a sound in-place -- a feature GUE doesn't offer at all.


// ---- Constants -------------------------------------------------------------
Const SOUNDS_MAX_ID = 65534


// ---- Type ------------------------------------------------------------------
Type SoundEntry
    Field ID%
    Field Filename$
    Field Is3D%
    Field Index%
End Type


// ---- Module state ----------------------------------------------------------
Global SoundsTotalCount% = 0


// =============================================================================
// Sounds_Init -- walk every slot in Data\Game Data\Sounds.dat, allocate
// a SoundEntry per populated one. Called once from Loom.bb after Meshes_Init.
// =============================================================================
Function Sounds_Init()
    If LockSounds() = 0 Then Return

    Local idx% = 0
    Local id% = 0
    For id = 0 To SOUNDS_MAX_ID
        Local raw$ = GetSoundName$(id)
        If raw <> ""
            // Strip the trailing Chr(Is3D) byte GetSoundName$ appends.
            Local nameLen% = Len(raw) - 1
            If nameLen < 0 Then nameLen = 0
            Local name$ = Left$(raw, nameLen)
            Local is3D% = Asc(Right$(raw, 1))

            Local se.SoundEntry = New SoundEntry()
            se\ID = id
            se\Filename = name
            se\Is3D = is3D
            se\Index = idx
            idx = idx + 1
        EndIf
    Next
    UnlockSounds()
    SoundsTotalCount = idx
End Function


// =============================================================================
// Sounds_GetByIndex.SoundEntry -- O(N) walk used by Threads::focus dispatch.
// =============================================================================
Function Sounds_GetByIndex.SoundEntry(idx%)
    For se.SoundEntry = Each SoundEntry
        If se\Index = idx Then Return se
    Next
    Return Null
End Function


// =============================================================================
// Sounds_GetByID.SoundEntry -- lookup by engine-side ID.
// =============================================================================
Function Sounds_GetByID.SoundEntry(id%)
    For se.SoundEntry = Each SoundEntry
        If se\ID = id Then Return se
    Next
    Return Null
End Function


// =============================================================================
// Sounds_Play -- audition a sound by its engine-side ID. Uses the
// existing GetSound (which lazy-loads via LoadSound + caches in
// LoadedSounds[]) so repeat-play is cheap. PlaySound is fire-and-
// forget; we don't track channel handles since there's no stop UI.
// Returns True if the sound played, False if GetSound failed.
// =============================================================================
Function Sounds_Play%(idx%)
    // Look up the SoundEntry to get the disk filename + Is3D flag,
    // then LoadSound + PlaySound directly. We bypass GetSound (which
    // returns Int in non-Strict Media.bb -- the Int -> BBSound
    // conversion barrier is the BlitzForge Strict gotcha we've hit
    // repeatedly for media file handles).
    //
    // The downside is no cache: each play does a LoadSound. The
    // engine's underlying file cache + small WAV/OGG files (typically
    // <100KB) make this fast enough for an audition use case.
    Local sd.SoundEntry = Sounds_GetByID(idx)
    If sd = Null Then Return False
    Local path$ = "Data\Sounds\" + sd\Filename$
    Local sndH.BBSound = LoadSound(path)
    If sndH = Null Then Return False
    PlaySound(sndH)
    // We don't FreeSound -- it would cut the playback immediately.
    // Blitz3D's runtime will GC the BBSound object when the user
    // navigates away. Minor leak per audition; acceptable for the
    // editor session lifetime.
    Return True
End Function
