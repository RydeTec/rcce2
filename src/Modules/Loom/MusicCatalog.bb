Strict

// =============================================================================
// Loom/MusicCatalog.bb -- catalog of every defined music track in
// Data\Game Data\Music.dat, browseable as first-class entities.
// =============================================================================
//
// Final piece of the media-asset trio (Textures + Meshes + Sounds + Music).
// Same architecture as SoundCatalog (iter 68). Music tracks are
// referenced from client-side SoundZone records inside zone files,
// which Loom doesn't currently edit -- so no reverse refs for now.
// The audition button + browseable list still earn their keep:
// designers exporting their music collection no longer have to grep
// a binary .dat file to see what's defined.


// ---- Constants -------------------------------------------------------------
Const MUSIC_MAX_ID = 65534


// ---- Type ------------------------------------------------------------------
Type MusicEntry
    Field ID%           // engine-side music track ID
    Field Filename$     // basename relative to Data\Music\
    Field Index%        // 0-based catalog index for Threads::focus refID
End Type


// ---- Module state ----------------------------------------------------------
Global MusicTotalCount% = 0


// =============================================================================
// Music_Init -- walk every slot in Data\Game Data\Music.dat. Music
// entries lack the trailing flag byte that sounds/meshes/textures
// carry, so the GetMusicName$ return is the basename verbatim.
// =============================================================================
Function Music_Init()
    If LockMusic() = 0 Then Return

    Local idx% = 0
    Local id% = 0
    For id = 0 To MUSIC_MAX_ID
        Local name$ = GetMusicName$(id)
        If name <> ""
            Local mu.MusicEntry = New MusicEntry()
            mu\ID = id
            mu\Filename = name
            mu\Index = idx
            idx = idx + 1
        EndIf
    Next
    UnlockMusic()
    MusicTotalCount = idx
End Function


// =============================================================================
// Music_GetByIndex.MusicEntry / Music_GetByID.MusicEntry -- standard
// lookup helpers mirroring the sibling catalogs.
// =============================================================================
Function Music_GetByIndex.MusicEntry(idx%)
    For mu.MusicEntry = Each MusicEntry
        If mu\Index = idx Then Return mu
    Next
    Return Null
End Function


Function Music_GetByID.MusicEntry(id%)
    For mu.MusicEntry = Each MusicEntry
        If mu\ID = id Then Return mu
    Next
    Return Null
End Function


// =============================================================================
// Music_Play -- audition a track. Uses LoadSound + PlaySound directly
// (engine plays music via the sound channel, not a CD track), matching
// the path used in ClientAreas.bb for the loading-screen music.
// =============================================================================
Function Music_Play%(idx%)
    Local mu.MusicEntry = Music_GetByIndex(idx)
    If mu = Null Then Return False
    Local path$ = "Data\Music\" + mu\Filename$
    Local sndH.BBSound = LoadSound(path)
    If sndH = Null Then Return False
    PlaySound(sndH)
    // Same caveat as Sounds_Play: no FreeSound (would cut playback).
    // Minor leak per audition, acceptable for editor session lifetime.
    Return True
End Function
