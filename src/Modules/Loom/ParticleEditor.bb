Strict

// =============================================================================
// Loom/ParticleEditor.bb -- live RP_EmitterConfig editing backend for the
// Particles browser tab (GUE "Particles" tab parity).
// =============================================================================
//
// EmitterCatalog.bb is a filename-only roster (the Projectiles composer's
// emitter-name picker + missing-name validator -- it never reads field
// data). Particles EDITING needs the full RP_EmitterConfig field set, so
// this module loads the real config objects at boot via
// RP_LoadEmitterConfig. That loader reads fields ONLY -- it does NOT build
// any 3D particle surfaces (only RP_CreateEmitter does, and Loom never
// calls it), so it is safe to run headless with Texture / FaceEntity = 0.
// Neither Texture nor FaceEntity is persisted (RP_SaveEmitterConfig omits
// both), so passing 0 round-trips cleanly.
//
// refID for the "particle" kind is Handle(config) -- Handle-based like
// zones, unstable across sessions (see docs/loom/architecture.md refID
// table). Composer / Browser resolve it via Object.RP_EmitterConfig(refID),
// which returns Null for a stale handle (guarded at every call site).
//
// GUE's dirty-flag model for this tab is the DEFERRED *Saved global
// (ParticlesSaved, shared with GUE and redeclared in Loom.bb): field
// edits + New flip it False, "Save emitters" writes every config and sets
// it True. Delete is the one immediate-disk op (GUE DeleteFile's the .rpc
// on the spot). We match that exactly.


// =============================================================================
// Particles_Init -- boot-load every *.rpc under Data\Emitter Configs\ into
// the Each RP_EmitterConfig pool. Mirror of GUE.bb's boot loop (minus the
// DefaultTextureID->texture resolution, which needs the media pipeline and
// is irrelevant to editing). Tolerant of a missing dir (fresh project).
// =============================================================================
Function Particles_Init()
    Local dir$ = "Data\Emitter Configs"
    Local D.BBDir = ReadDir(dir$)
    If D = Null Then Return

    Local f$
    Repeat
        f$ = NextFile$(D)
        If f$ <> "" And f$ <> "." And f$ <> ".."
            If Right$(Lower$(f$), 4) = ".rpc"
                If FileType(dir$ + "\" + f$) = 1
                    RP_LoadEmitterConfig(dir$ + "\" + f$, 0, 0)
                EndIf
            EndIf
        EndIf
    Until f$ = ""
    CloseDir(D)
End Function


// =============================================================================
// Particles_Count -- number of live configs (browser empty-state + ribbon
// totals).
// =============================================================================
Function Particles_Count%()
    Local n% = 0
    For C.RP_EmitterConfig = Each RP_EmitterConfig
        n = n + 1
    Next
    Return n
End Function


// =============================================================================
// Particles_SaveAll -- write every config to Data\Emitter Configs\<Name>.rpc.
// Byte-identical to GUE's "Save emitters" button loop. RP_SaveEmitterConfig
// soft-fails per file (returns False on a bad handle / unwritable path)
// without aborting the loop, so this always returns True. After the write,
// refresh the projectile-picker roster so a newly-created / renamed emitter
// name resolves there.
// =============================================================================
Function Particles_SaveAll%()
    For C.RP_EmitterConfig = Each RP_EmitterConfig
        RP_SaveEmitterConfig(Handle(C), "Data\Emitter Configs\" + C\Name$ + ".rpc")
    Next
    Emitters_Rebuild()
    Return True
End Function


// =============================================================================
// Particles_FreeAll -- free every live config (the discard path frees then
// reloads from disk). After-cursor walk: RP_FreeEmitterConfig Deletes C, so
// capture After before the free (docs/loom/architecture.md iterator hazard).
// FreeTex = False -- Loom never loaded real textures for these configs.
// =============================================================================
Function Particles_FreeAll()
    Local C.RP_EmitterConfig = First RP_EmitterConfig
    Local CNext.RP_EmitterConfig = Null
    While C <> Null
        CNext = After C
        RP_FreeEmitterConfig(Handle(C), False)
        C = CNext
    Wend
End Function


// =============================================================================
// Particles_NameExists -- case-insensitive: is `name` taken by a live config
// OR an existing .rpc on disk? Save writes <Name>.rpc, so a duplicate name
// would clobber another config's file -- names must be unique across both.
// =============================================================================
Function Particles_NameExists%(name$)
    Local key$ = Lower$(name)
    If key = "" Then Return True
    For C.RP_EmitterConfig = Each RP_EmitterConfig
        If Lower$(C\Name$) = key Then Return True
    Next
    If FileType("Data\Emitter Configs\" + name + ".rpc") = 1 Then Return True
    Return False
End Function


// =============================================================================
// Particles_UniqueName -- an emitter name colliding with neither a live
// config nor an on-disk .rpc. GUE gets the name from a modal text dialog;
// Loom has no FUI, so New / Duplicate auto-name and the user renames after.
// =============================================================================
Function Particles_UniqueName$(base$)
    If base = "" Then base = "Emitter"
    Local candidate$ = base
    Local n% = 2
    While Particles_NameExists(candidate) = True
        candidate = base + " " + Str(n)
        n = n + 1
    Wend
    Return candidate
End Function
