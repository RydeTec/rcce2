Strict

// =============================================================================
// Loom/EmitterCatalog.bb -- catalog of emitter configs (.rpc files) under
// Data\Emitter Configs\, used by the Projectiles composer.
// =============================================================================
//
// Why this exists: Projectile\Emitter1$ / Emitter2$ reference emitter
// configs BY NAME (the RP_EmitterConfig Name$, which equals the .rpc
// basename -- GUE saves each config as "Data\Emitter Configs\<Name>.rpc",
// see GUE.bb's RP_SaveEmitterConfig call). GUE resolves the roster by
// loading every config through RP_LoadEmitterConfig, but that path needs
// a live 3D camera (it builds particle surfaces). Loom only needs the
// NAMES -- for validating a projectile's emitter fields and for the
// right-click picker roster -- so a filename scan is the whole catalog.
//
// Same module shape as ScriptsCatalog.bb (Strict, Type pool populated at
// boot, free-function API). Emitters are NOT browsable entities (no
// browser tab, no composer view, no Threads focus); the catalog exists
// only as a picker roster + name validator.


// =============================================================================
// Type EmitterEntry -- one entry per .rpc in Data\Emitter Configs\.
// =============================================================================
Type EmitterEntry
    Field Name$         // basename without .rpc extension (matches Projectile\EmitterN$)
    Field Index%        // 0-based catalog index (Palette picker refID)
End Type


Global EmittersTotalCount% = 0


// =============================================================================
// Emitters_Init -- scan Data\Emitter Configs\ for *.rpc, populate the
// EmitterEntry pool. Called once at boot from Loom.bb. Tolerant of a
// missing dir (fresh project) -- catalog just stays empty.
// =============================================================================
Function Emitters_Init()
    Local dir$ = "Data\Emitter Configs"
    Local D.BBDir = ReadDir(dir$)
    If D = Null Then Return

    Local idx% = 0
    Local f$
    Repeat
        f$ = NextFile$(D)
        If f$ <> "" And f$ <> "." And f$ <> ".."
            If Right$(Lower$(f$), 4) = ".rpc"
                If FileType(dir$ + "\" + f$) = 1
                    Local e.EmitterEntry = New EmitterEntry()
                    e\Name = Left$(f$, Len(f$) - 4)
                    e\Index = idx
                    idx = idx + 1
                EndIf
            EndIf
        EndIf
    Until f$ = ""
    CloseDir(D)

    EmittersTotalCount = idx
End Function


// =============================================================================
// Emitters_GetByIndex.EmitterEntry -- O(N) walk, insertion order == Index.
// Used by the Palette picker result dispatch.
// =============================================================================
Function Emitters_GetByIndex.EmitterEntry(idx%)
    For e.EmitterEntry = Each EmitterEntry
        If e\Index = idx Then Return e
    Next
    Return Null
End Function


// =============================================================================
// Emitters_GetByName.EmitterEntry -- case-insensitive lookup by name.
// Used by the Projectile composer's emitter rows to paint the "missing"
// pill when a stored name doesn't resolve to a config on disk.
// =============================================================================
Function Emitters_GetByName.EmitterEntry(name$)
    Local key$ = Lower$(name$)
    If key = "" Then Return Null
    For e.EmitterEntry = Each EmitterEntry
        If Lower$(e\Name) = key Then Return e
    Next
    Return Null
End Function
