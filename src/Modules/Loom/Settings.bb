; =============================================================================
; Loom/Settings.bb -- project-level configuration loader / saver
; =============================================================================
;
; Misc / Other / Money / Hosts .dat files hold project-level config that
; isn't an entity in any *List array. GUE has no editor surface for these
; (they're hand-edited as binary .dat); Loom exposes them via a "Settings"
; pseudo-entity routed through the existing composer / save infrastructure.
;
; Lifecycle:
;   Loom_LoadSettings   - called once at boot, reads all four .dat files
;                         into the LoomCfg_* globals.
;   Loom_SaveSettings   - called from SaveAll when SettingsSaved = False;
;                         writes all four files atomically via SafeWriteOpen
;                         / SafeWriteCommit so a mid-write crash doesn't
;                         corrupt the project.
;
; The dirty flag (SettingsSaved) is part of Loom's per-kind dirty tracking
; (matches the SpellsSaved / ItemsSaved / ActorsSaved / FactionsSaved /
; AnimSetsSaved pattern). Composer::markDirtyForKind("settings") flips it.
;
; Non-Strict file: SafeWriteOpen$ returns a string path, and BBStream/Int
; conversions through SafeWriteCommit% need flexible typing per the
; "BlitzForge EnableGC requires Strict" memory note. Same shape as
; Recents.bb (which had the same constraint).


; ---- Misc.dat ---------------------------------------------------------------
Global LoomCfg_GameName$       = ""
Global LoomCfg_UpdateGame$     = ""
Global LoomCfg_UpdateMusic$    = ""

; ---- Hosts.dat --------------------------------------------------------------
Global LoomCfg_ServerHost$     = ""
Global LoomCfg_UpdateHost$     = ""

; ---- Other.dat --------------------------------------------------------------
Global LoomCfg_HideNametags     = 0
Global LoomCfg_DisableCollisions = 0
Global LoomCfg_ViewMode         = 0
Global LoomCfg_ServerPort       = 25000
Global LoomCfg_RequireMemorise  = 0
Global LoomCfg_UseBubbles       = 0
Global LoomCfg_BubblesR         = 0
Global LoomCfg_BubblesG         = 0
Global LoomCfg_BubblesB         = 0

; ---- Money.dat --------------------------------------------------------------
Global LoomCfg_Money1$  = ""
Global LoomCfg_Money2$  = ""
Global LoomCfg_Money2x  = 0
Global LoomCfg_Money3$  = ""
Global LoomCfg_Money3x  = 0
Global LoomCfg_Money4$  = ""
Global LoomCfg_Money4x  = 0

; Dirty flag (matches the *Saved global pattern used by Items / Actors / etc).
; Set False by writeField when a Settings field changes; reset True after
; Loom_SaveSettings or Loom_LoadSettings (discard).
Global SettingsSaved = True


; =============================================================================
; Non-Strict setters -- per the BlitzForge "Strict can't write to Globals
; from inside a Method" trap, Composer.bb (which is Strict) routes every
; field write through one of these wrappers. Composer:writeField for kind=
; "settings" calls LoomSettings_SetX(value) instead of a direct assign.
; Same shape as SetFactionName in Actors.bb / Delete*Template helpers.
; =============================================================================
Function LoomSettings_SetGameName$(v$)    : LoomCfg_GameName$    = v : Return v : End Function
Function LoomSettings_SetUpdateGame$(v$)  : LoomCfg_UpdateGame$  = v : Return v : End Function
Function LoomSettings_SetUpdateMusic$(v$) : LoomCfg_UpdateMusic$ = v : Return v : End Function
Function LoomSettings_SetServerHost$(v$)  : LoomCfg_ServerHost$  = v : Return v : End Function
Function LoomSettings_SetUpdateHost$(v$)  : LoomCfg_UpdateHost$  = v : Return v : End Function
Function LoomSettings_SetServerPort(v)    : LoomCfg_ServerPort   = v : Return v : End Function
Function LoomSettings_SetHideNametags(v)  : LoomCfg_HideNametags = v : Return v : End Function
Function LoomSettings_SetDisableCollisions(v) : LoomCfg_DisableCollisions = v : Return v : End Function
Function LoomSettings_SetViewMode(v)      : LoomCfg_ViewMode     = v : Return v : End Function
Function LoomSettings_SetRequireMemorise(v) : LoomCfg_RequireMemorise = v : Return v : End Function
Function LoomSettings_SetUseBubbles(v)    : LoomCfg_UseBubbles   = v : Return v : End Function
Function LoomSettings_SetBubblesR(v)      : LoomCfg_BubblesR     = v : Return v : End Function
Function LoomSettings_SetBubblesG(v)      : LoomCfg_BubblesG     = v : Return v : End Function
Function LoomSettings_SetBubblesB(v)      : LoomCfg_BubblesB     = v : Return v : End Function
Function LoomSettings_SetMoney1$(v$)      : LoomCfg_Money1$      = v : Return v : End Function
Function LoomSettings_SetMoney2$(v$)      : LoomCfg_Money2$      = v : Return v : End Function
Function LoomSettings_SetMoney2x(v)       : LoomCfg_Money2x      = v : Return v : End Function
Function LoomSettings_SetMoney3$(v$)      : LoomCfg_Money3$      = v : Return v : End Function
Function LoomSettings_SetMoney3x(v)       : LoomCfg_Money3x      = v : Return v : End Function
Function LoomSettings_SetMoney4$(v$)      : LoomCfg_Money4$      = v : Return v : End Function
Function LoomSettings_SetMoney4x(v)       : LoomCfg_Money4x      = v : Return v : End Function


; =============================================================================
; Loom_LoadSettings -- read all four .dat files. Tolerant of missing files
; (a fresh project may not have all of them); missing files just leave
; the globals at their declared defaults.
; =============================================================================
Function Loom_LoadSettings()
    ; --- Misc.dat -----------------------------------------------------------
    F = ReadFile("Data\Game Data\Misc.dat")
    If F <> 0
        LoomCfg_GameName$    = ReadLine$(F)
        LoomCfg_UpdateGame$  = ReadLine$(F)
        LoomCfg_UpdateMusic$ = ReadLine$(F)
        CloseFile(F)
    EndIf

    ; --- Hosts.dat ----------------------------------------------------------
    F = ReadFile("Data\Game Data\Hosts.dat")
    If F <> 0
        LoomCfg_ServerHost$ = ReadLine$(F)
        LoomCfg_UpdateHost$ = ReadLine$(F)
        CloseFile(F)
    EndIf

    ; --- Other.dat (binary) -------------------------------------------------
    F = ReadFile("Data\Game Data\Other.dat")
    If F <> 0
        LoomCfg_HideNametags      = ReadByte(F)
        LoomCfg_DisableCollisions = ReadByte(F)
        LoomCfg_ViewMode          = ReadByte(F)
        LoomCfg_ServerPort        = ReadInt(F)
        If LoomCfg_ServerPort = 0 Then LoomCfg_ServerPort = 25000
        LoomCfg_RequireMemorise   = ReadByte(F)
        LoomCfg_UseBubbles        = ReadByte(F)
        LoomCfg_BubblesR          = ReadByte(F)
        LoomCfg_BubblesG          = ReadByte(F)
        LoomCfg_BubblesB          = ReadByte(F)
        CloseFile(F)
    EndIf

    ; --- Money.dat (mixed binary) -------------------------------------------
    F = ReadFile("Data\Game Data\Money.dat")
    If F <> 0
        LoomCfg_Money1$ = ReadBoundedString$(F, 64)
        LoomCfg_Money2$ = ReadBoundedString$(F, 64)
        LoomCfg_Money2x = ReadShort(F)
        LoomCfg_Money3$ = ReadBoundedString$(F, 64)
        LoomCfg_Money3x = ReadShort(F)
        LoomCfg_Money4$ = ReadBoundedString$(F, 64)
        LoomCfg_Money4x = ReadShort(F)
        CloseFile(F)
    EndIf

    ; --- Loom-private chrome mode (Data\Loom\chrome.txt) ----------------------
    ; Optional; if file missing or unreadable, mode stays at the declared
    ; "balanced" default. Stored in Loom's private dir so other engine
    ; binaries (Server/Client/GUE) never touch it.
    Loom_LoadChromeMode()

    SettingsSaved = True
    WriteLog(LoomLog, "Settings: loaded project config (game=" + LoomCfg_GameName$ + ", port=" + LoomCfg_ServerPort + ")")
End Function


; =============================================================================
; Loom_LoadChromeMode -- read Data\Loom\chrome.txt into LoomChromeMode$
; (declared in ImageCache.bb). Tolerant of missing file or unrecognized
; value -- leaves the global at whatever it was (default "balanced").
; =============================================================================
Function Loom_LoadChromeMode()
    Local f = ReadFile("Data\Loom\chrome.txt")
    If f = 0 Then Return
    Local mode$ = Trim$(ReadLine$(f))
    CloseFile(f)
    If mode = "tool" Or mode = "balanced" Or mode = "in-world"
        LoomChromeMode$ = mode
        WriteLog(LoomLog, "Settings: chrome mode restored to " + mode)
    EndIf
End Function


; =============================================================================
; Loom_SaveChromeMode -- persist current LoomChromeMode$ to chrome.txt.
; Creates Data\Loom\ if missing (mirrors Recents.bb's pattern).
; Atomic-write via SafeWriteOpen/Commit so a crash mid-write doesn't
; corrupt the file.
; =============================================================================
Function Loom_SaveChromeMode()
    If FileType("Data\Loom") <> 2 Then CreateDir "Data\Loom"
    Local finalPath$ = "Data\Loom\chrome.txt"
    Local tempPath$ = SafeWriteOpen$(finalPath$)
    Local f = WriteFile(tempPath$)
    If f = 0 Then Return False
    WriteLine f, LoomChromeMode$
    Return SafeWriteCommit%(tempPath$, finalPath$, f)
End Function


; =============================================================================
; Loom_SaveSettings -- write all four .dat files atomically. Returns True on
; success, False on first failure (in which case some files may already be
; updated -- the SafeWrite contract is per-file, not transactional across
; multiple files; matches how SaveAll dispatches per-kind).
; =============================================================================
Function Loom_SaveSettings()
    ; --- Misc.dat -----------------------------------------------------------
    Local FinalPath$ = "Data\Game Data\Misc.dat"
    Local TempPath$  = SafeWriteOpen$(FinalPath$)
    F = WriteFile(TempPath$)
    If F = 0 Then Return False
    WriteLine(F, LoomCfg_GameName$)
    WriteLine(F, LoomCfg_UpdateGame$)
    WriteLine(F, LoomCfg_UpdateMusic$)
    Result = SafeWriteCommit%(TempPath$, FinalPath$, F)
    If Result = False Then Return False

    ; --- Hosts.dat ----------------------------------------------------------
    FinalPath$ = "Data\Game Data\Hosts.dat"
    TempPath$  = SafeWriteOpen$(FinalPath$)
    F = WriteFile(TempPath$)
    If F = 0 Then Return False
    WriteLine(F, LoomCfg_ServerHost$)
    WriteLine(F, LoomCfg_UpdateHost$)
    Result = SafeWriteCommit%(TempPath$, FinalPath$, F)
    If Result = False Then Return False

    ; --- Other.dat ----------------------------------------------------------
    FinalPath$ = "Data\Game Data\Other.dat"
    TempPath$  = SafeWriteOpen$(FinalPath$)
    F = WriteFile(TempPath$)
    If F = 0 Then Return False
    WriteByte(F, LoomCfg_HideNametags)
    WriteByte(F, LoomCfg_DisableCollisions)
    WriteByte(F, LoomCfg_ViewMode)
    WriteInt(F,  LoomCfg_ServerPort)
    WriteByte(F, LoomCfg_RequireMemorise)
    WriteByte(F, LoomCfg_UseBubbles)
    WriteByte(F, LoomCfg_BubblesR)
    WriteByte(F, LoomCfg_BubblesG)
    WriteByte(F, LoomCfg_BubblesB)
    Result = SafeWriteCommit%(TempPath$, FinalPath$, F)
    If Result = False Then Return False

    ; --- Money.dat ----------------------------------------------------------
    FinalPath$ = "Data\Game Data\Money.dat"
    TempPath$  = SafeWriteOpen$(FinalPath$)
    F = WriteFile(TempPath$)
    If F = 0 Then Return False
    WriteString(F, LoomCfg_Money1$)
    WriteString(F, LoomCfg_Money2$)
    WriteShort(F,  LoomCfg_Money2x)
    WriteString(F, LoomCfg_Money3$)
    WriteShort(F,  LoomCfg_Money3x)
    WriteString(F, LoomCfg_Money4$)
    WriteShort(F,  LoomCfg_Money4x)
    Result = SafeWriteCommit%(TempPath$, FinalPath$, F)
    If Result = False Then Return False

    ; --- Damage.dat (DamageTypes$ catalog) ----------------------------------
    ; Uses the new SaveDamageTypes helper added to Items.bb.
    Result = SaveDamageTypes("Data\Server Data\Damage.dat")
    If Result = False Then Return False

    ; --- Attributes.dat (AttributeNames$ + flags + AttributeAssignment) -----
    ; Reuses the existing SaveAttributes function in Actors.bb.
    Result = SaveAttributes("Data\Server Data\Attributes.dat")
    If Result = False Then Return False

    SettingsSaved = True
    WriteLog(LoomLog, "Settings: saved project config (Misc+Hosts+Other+Money+Damage+Attributes)")
    Return True
End Function
