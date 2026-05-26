// =============================================================================
// ConscienceRibbon.bb -- continuous world-health status at the top of GUE
// =============================================================================
//
// Adds an always-visible at-a-glance status group in the top-right corner of
// the editor window. Shows:
//
//   "N unsaved  ·  M issues  ·  saved 2m ago"     [Issues...]
//
// Plus a hidden modal that opens when the user clicks "Issues..." -- a
// scrollable list of findings, each clickable to jump straight to the
// offending entity (uses GUE_JumpToEntity from PR 1).
//
// The validator pass is cheap and runs lazily (every ~3 seconds). v1 checks:
//   - Items   whose Script$ is set but doesn't match any loaded Item_*.rsl
//   - Spells  whose Script$ is set but doesn't match any loaded Spell_*.rsl
//
// More findings (missing mesh refs, orphaned portals, etc.) can plug in later
// by following the Conscience_AddFinding pattern.
//
// Why top-right of the menu bar area: GUE today uses Y=0..20 for the menu
// titles ("File", "Help") and leaves the rest of that horizontal strip empty.
// Putting the ribbon there means zero coordinate churn for the 14 existing
// tabs and their thousands of inner gadgets. A bottom status bar would have
// required shrinking TabMain's height and every gadget that bottom-anchors
// off GUE_height (and there are many).
//
// Public API:
//   Conscience_Init()              -- create widgets. Call once at startup
//                                     after WMain exists.
//   Conscience_Update()            -- called from the main loop. Cheap;
//                                     internally throttled.
//   Conscience_HandleEvent(EID,ED) -- consume button clicks. Call from the
//                                     event loop.
// =============================================================================


// Widget handles --------------------------------------------------------------
Global CR_LblStatus  = 0
Global CR_BtnIssues  = 0

Global CR_Window     = 0   // findings modal
Global CR_List       = 0
Global CR_LblEmpty   = 0
Global CR_Open       = False

// Layout
Const CR_RIBBON_W = 440
Const CR_RIBBON_H = 18
Const CR_RIBBON_Y = 2

Const CR_FW_W = 600
Const CR_FW_H = 380

// Throttling: validator runs at most every ~3 seconds; label refreshes every
// ~500ms (cheap enough but the dirty-flag counts can change frequently).
Const CR_VALIDATE_INTERVAL_MS = 3000
Const CR_REFRESH_INTERVAL_MS  = 500

Global CR_LastValidatedMs% = -999999
Global CR_LastRefreshMs%   = -999999
Global CR_LastSaveMs%      = -1   // -1 means "never observed a save this session"

// Snapshot of last seen save-flag state, used to detect a save happening.
Global CR_PrevSavedHash = 0


// A finding row. We use a BBList because we don't know the count ahead of
// time and the listbox UI is rebuilt from this each validation pass.
Type CRFinding
    Field Message$
    Field JumpKind$
    Field JumpRefID
End Type
Global CR_Findings.BBList = Null


// =============================================================================
// Conscience_Init -- create the ribbon widgets and the hidden findings modal.
// =============================================================================
Function Conscience_Init()
    If CR_LblStatus <> 0 Then Return  // idempotent

    CR_Findings = CreateList()

    // Place the status label in the top-right, in the menu bar's empty real
    // estate. Owned by WMain so it sits over the menu strip (the menu titles
    // start at the left and don't extend this far).
    Local sw = GraphicsWidth()
    Local startX = sw - CR_RIBBON_W - 10

    CR_LblStatus = FUI_Label(WMain, startX, CR_RIBBON_Y + 2, "World ready.", ALIGN_LEFT)

    // "Issues..." button -- only opens the modal if there are findings, but
    // shown at all times so users learn it exists.
    CR_BtnIssues = FUI_Button(WMain, sw - 90, CR_RIBBON_Y, 80, CR_RIBBON_H, "Issues...", "", 0, 0)

    // Hidden findings modal.
    Local px = (sw - CR_FW_W) / 2
    Local py = (GraphicsHeight() - CR_FW_H) / 3
    If py < 60 Then py = 60

    CR_Window = FUI_Window(px, py, CR_FW_W, CR_FW_H, "World issues", "", 0, WS_TITLEBAR Or WS_CLOSEBUTTON)
    CR_LblEmpty = FUI_Label(CR_Window, 12, 36, "No issues found. The world is clean.")
    CR_List = FUI_ListBox(CR_Window, 12, 36, CR_FW_W - 24, CR_FW_H - 70, False, False)
    FUI_SendMessage(CR_Window, M_HIDE)

    // Force a first pass so the ribbon starts populated rather than blank.
    Conscience_Refresh(True)
End Function


// =============================================================================
// Conscience_Update -- per-frame tick. Refreshes the label and re-runs the
// validator on its own schedules. Cheap to call every frame.
// =============================================================================
Function Conscience_Update()
    If CR_LblStatus = 0 Then Return

    Local now = MilliSecs()
    Local doRefresh = False
    Local doValidate = False

    If now - CR_LastRefreshMs >= CR_REFRESH_INTERVAL_MS
        doRefresh = True
        CR_LastRefreshMs = now
    EndIf
    If now - CR_LastValidatedMs >= CR_VALIDATE_INTERVAL_MS
        doValidate = True
        CR_LastValidatedMs = now
    EndIf

    If doRefresh = False And doValidate = False Then Return
    Conscience_Refresh(doValidate)
End Function


// Internal: refresh the label, optionally re-running the validator and
// rebuilding the findings list.
Function Conscience_Refresh(runValidator)
    If runValidator = True Then Conscience_Validate()

    // -- Count unsaved tabs -------------------------------------------------
    Local unsaved = 0
    If ItemsSaved       = False Then unsaved = unsaved + 1
    If ActorsSaved      = False Then unsaved = unsaved + 1
    If FactionsSaved    = False Then unsaved = unsaved + 1
    If ParticlesSaved   = False Then unsaved = unsaved + 1
    If DamageTypesSaved = False Then unsaved = unsaved + 1
    If ZoneSaved        = False Then unsaved = unsaved + 1
    If AnimsSaved       = False Then unsaved = unsaved + 1
    If StatsSaved       = False Then unsaved = unsaved + 1
    If SpellsSaved      = False Then unsaved = unsaved + 1
    If InterfaceSaved   = False Then unsaved = unsaved + 1
    If ProjectilesSaved = False Then unsaved = unsaved + 1
    If EnvironmentSaved = False Then unsaved = unsaved + 1

    // -- Detect a save happening (any True transition from False) ----------
    // Cheap fingerprint: bitfield of all 12 save flags. Compare to last seen.
    Local hash = 0
    If ItemsSaved       = True Then hash = hash + 1
    If ActorsSaved      = True Then hash = hash + 2
    If FactionsSaved    = True Then hash = hash + 4
    If ParticlesSaved   = True Then hash = hash + 8
    If DamageTypesSaved = True Then hash = hash + 16
    If ZoneSaved        = True Then hash = hash + 32
    If AnimsSaved       = True Then hash = hash + 64
    If StatsSaved       = True Then hash = hash + 128
    If SpellsSaved      = True Then hash = hash + 256
    If InterfaceSaved   = True Then hash = hash + 512
    If ProjectilesSaved = True Then hash = hash + 1024
    If EnvironmentSaved = True Then hash = hash + 2048

    // If more bits are True now than were True last time, someone saved.
    If Conscience_PopCount(hash) > Conscience_PopCount(CR_PrevSavedHash)
        CR_LastSaveMs = MilliSecs()
    EndIf
    CR_PrevSavedHash = hash

    // -- Build the label ----------------------------------------------------
    Local issueCount = ListSize(CR_Findings)
    Local msg$ = ""

    If unsaved = 0
        msg$ = "All saved"
    Else
        msg$ = Str(unsaved) + " unsaved"
    EndIf

    msg$ = msg$ + "  ·  " + Str(issueCount) + " issues"

    msg$ = msg$ + "  ·  " + Conscience_SaveAge$()

    FUI_SendMessage(CR_LblStatus, M_SETCAPTION, msg$)
End Function


// Format an age string for the "saved Nm ago" tail.
Function Conscience_SaveAge$()
    If CR_LastSaveMs < 0 Then Return "no save yet"

    Local age = (MilliSecs() - CR_LastSaveMs) / 1000   // seconds
    If age < 5  Then Return "just saved"
    If age < 60 Then Return "saved " + Str(age) + "s ago"
    Local mins = age / 60
    If mins < 60 Then Return "saved " + Str(mins) + "m ago"
    Local hrs = mins / 60
    Return "saved " + Str(hrs) + "h ago"
End Function


// Count set bits in an int. Brian Kernighan's trick.
Function Conscience_PopCount(n)
    Local c = 0
    While n <> 0
        n = n And (n - 1)
        c = c + 1
    Wend
    Return c
End Function


// =============================================================================
// Conscience_Validate -- walk the entity graph, populate CR_Findings.
//
// v1 checks: items / spells whose Script$ is set but doesn't match any of the
// scripts loaded into the corresponding script-name combobox at startup. This
// catches the most common kind of broken reference (someone renamed or
// deleted a script file but the entity still binds to the old name).
//
// New validators slot in by appending to CR_Findings via Conscience_AddFinding.
// =============================================================================
Function Conscience_Validate()
    ListClear(CR_Findings)

    // -- Items with bad script bindings ------------------------------------
    Local It.Item
    For It = Each Item
        If It\Script$ <> ""
            If Conscience_ScriptExists(CItemScript, It\Script$) = False
                Conscience_AddFinding("Item '" + It\Name$ + "' references missing script '" + It\Script$ + "'", "item", It\ID)
            EndIf
        EndIf
    Next

    // -- Spells with bad script bindings -----------------------------------
    Local Sp.Spell
    For Sp = Each Spell
        If Sp\Script$ <> ""
            If Conscience_ScriptExists(CSpellScript, Sp\Script$) = False
                Conscience_AddFinding("Spell '" + Sp\Name$ + "' references missing script '" + Sp\Script$ + "'", "spell", Sp\ID)
            EndIf
        EndIf
    Next

    // -- If the findings window is currently open, rebuild its visible list.
    If CR_Open = True Then Conscience_PopulateModalList()
End Function


// Check whether a script name appears in one of the script-name comboboxes
// that GUE built at startup from Data\Server Data\Scripts\*.rsl. Case-
// insensitive match.
Function Conscience_ScriptExists(cb, name$)
    Local n = FUI_SendMessage(cb, M_COUNTITEMS)
    Local i = 0
    Local target$ = Upper$(Trim$(name$))
    For i = 1 To n
        FUI_SendMessage(cb, M_SETINDEX, i)
        Local item = FUI_SendMessage(cb, M_GETSELECTED)
        Local cap$ = Upper$(FUI_SendMessage(item, M_GETCAPTION))
        If cap$ = target$ Then Return True
    Next
    Return False
End Function


Function Conscience_AddFinding(message$, jumpKind$, jumpRefID)
    Local f.CRFinding = New CRFinding()
    f\Message$ = message$
    f\JumpKind$ = jumpKind$
    f\JumpRefID = jumpRefID
    ListAdd(CR_Findings, f)
End Function


// =============================================================================
// Findings modal show/hide + click handling
// =============================================================================
Function Conscience_OpenModal()
    If CR_Window = 0 Then Return

    Conscience_PopulateModalList()
    FUI_SendMessage(CR_Window, M_SHOW)
    FUI_SendMessage(CR_Window, M_BRINGTOFRONT)
    CR_Open = True
End Function


Function Conscience_CloseModal()
    If CR_Window = 0 Or CR_Open = False Then Return
    FUI_SendMessage(CR_Window, M_HIDE)
    CR_Open = False
End Function


Function Conscience_PopulateModalList()
    FUI_SendMessage(CR_List, M_RESET)
    Local n = ListSize(CR_Findings)

    If n = 0
        FUI_ShowGadget(CR_LblEmpty)
        FUI_HideGadget(CR_List)
        Return
    EndIf

    FUI_HideGadget(CR_LblEmpty)
    FUI_ShowGadget(CR_List)

    Local i = 0
    For i = 0 To n - 1
        Local f.CRFinding = ListAt(CR_Findings, i)
        Local item = FUI_ListBoxItem(CR_List, f\Message$)
        FUI_SendMessage(item, M_SETDATA, i)
    Next
End Function


// =============================================================================
// Conscience_HandleEvent -- dispatch our own gadget events.
// =============================================================================
Function Conscience_HandleEvent(EID, EData$)
    If EID = CR_BtnIssues
        Conscience_OpenModal()
        Return True
    EndIf

    If CR_Open = True
        If EID = CR_List
            // Click on a finding = jump.
            Local sel = FUI_SendMessage(CR_List, M_GETSELECTED)
            If sel = 0 Then Return True
            Local idx = FUI_SendMessage(sel, M_GETDATA)
            Local f.CRFinding = ListAt(CR_Findings, idx)
            If f = Null Then Return True

            Local jk$ = f\JumpKind$
            Local jr = f\JumpRefID

            Conscience_CloseModal()
            GUE_JumpToEntity(jk$, jr)
            Return True
        EndIf
    EndIf

    Return False
End Function
