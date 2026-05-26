// =============================================================================
// CommandPalette.bb -- Global find-anywhere modal (Ctrl+K)
// =============================================================================
//
// One-keystroke jump-to-any-entity overlay. Inspired by the "Loom" GUE
// redesign concept and patterned after VS Code's Cmd-P / Slack's quick-switcher
// / Linear's command palette: Ctrl+K opens, type to filter across actors,
// items, spells, and zones, Enter to jump.
//
// Motivation: GUE's per-tab dropdown picker doesn't scale past ~50 entities of
// any one kind, and switching between e.g. an actor's faction binding and that
// faction's editor takes 3-4 clicks. The palette collapses that to a keystroke
// + a few characters.
//
// Public API:
//   CmdPalette_Init()
//       Build the (hidden) modal window. Idempotent. Call once from GUE.bb
//       after the entity comboboxes have been populated.
//
//   CmdPalette_Open()
//       Show and focus. The query field starts empty; the result list shows
//       every entity.
//
//   CmdPalette_Close()
//       Hide. Cheap; safe to call when already closed.
//
//   CmdPalette_HandleEvent(EID, EData$)
//       Wire into the main event loop's For Each Event ... Next pass. Returns
//       True if the event was consumed. EID is E\EventID, EData$ is
//       E\EventData. Palette gadget IDs won't collide with anything in GUE so
//       the caller can ignore the return value.
//
//   CmdPalette_PollKeys()
//       Wire into the main loop once per frame. Watches for Ctrl+K (open),
//       Esc (close), and Enter (activate first match while open). Done as
//       a poll rather than a hook because F-UI doesn't expose per-window key
//       events for non-focused windows.
//
//   GUE_JumpToEntity(kind$, refID)
//       Public navigation primitive used by the palette today and by the
//       cross-reference / atlas / conscience features in later PRs. Switches
//       to the right tab and selects the target entity in that tab's combo.
//
//       kind$: "actor" | "item" | "spell" | "zone"
//       refID: actor/item/spell -> the entity's array-index ID
//              zone             -> Handle(Area)   (matches CZone's M_SETDATA)
//
// Why a fresh module instead of inline in GUE.bb: GUE.bb is already 10k lines
// and the palette is conceptually distinct. Keeping it in its own file means
// PR 2-4 can extend GUE_JumpToEntity without churn here.
// =============================================================================


// Palette UI handles ----------------------------------------------------------
Global CP_Window     = 0
Global CP_TextBox    = 0
Global CP_ListBox    = 0
Global CP_HintLabel  = 0
Global CP_Open       = False

// Layout constants
Const CP_W      = 620
Const CP_H      = 440
Const CP_PAD    = 12
Const CP_ROW    = 26
Const CP_INPUTH = 26


// A single search result. We use a parallel list because F-UI's ListBox stores
// only an integer in M_SETDATA, but each result needs both a kind tag and the
// entity's reference ID. We index into CP_Results by the listbox item's data.
Type CPResult
    Field Kind$
    Field RefID
    Field Caption$
End Type
Global CP_Results.BBList = Null


// =============================================================================
// CmdPalette_Init -- Build the modal window. Hidden by default.
// =============================================================================
Function CmdPalette_Init()
    If CP_Window <> 0 Then Return   // idempotent

    CP_Results = CreateList()

    // Center on the app window's current dimensions.
    Local sw = GraphicsWidth()
    Local sh = GraphicsHeight()
    Local px = (sw - CP_W) / 2
    Local py = (sh - CP_H) / 3   // a third of the way down feels right

    // Floor py so the window never collides with the top menu.
    If py < 60 Then py = 60

    CP_Window = FUI_Window(px, py, CP_W, CP_H, "Find anywhere", "", 0, WS_TITLEBAR Or WS_CLOSEBUTTON)

    CP_HintLabel = FUI_Label(CP_Window, CP_PAD, 32, "Type to search across actors, items, spells, and zones.  Enter to jump.  Esc to close.")

    CP_TextBox = FUI_TextBox(CP_Window, CP_PAD, 52, CP_W - CP_PAD * 2, CP_INPUTH, 200)

    CP_ListBox = FUI_ListBox(CP_Window, CP_PAD, 88, CP_W - CP_PAD * 2, CP_H - 130, False, False)

    FUI_SendMessage(CP_Window, M_HIDE)
End Function


// =============================================================================
// CmdPalette_Open -- Show, focus the query, fill with everything.
// =============================================================================
Function CmdPalette_Open()
    If CP_Window = 0 Then CmdPalette_Init()

    FUI_SendMessage(CP_TextBox, M_SETTEXT, "")
    CmdPalette_Filter("")

    FUI_SendMessage(CP_Window, M_SHOW)
    FUI_SendMessage(CP_Window, M_BRINGTOFRONT)
    CP_Open = True
End Function


// =============================================================================
// CmdPalette_Close
// =============================================================================
Function CmdPalette_Close()
    If CP_Window = 0 Then Return
    If CP_Open = False Then Return
    FUI_SendMessage(CP_Window, M_HIDE)
    CP_Open = False
End Function


// =============================================================================
// CmdPalette_Filter -- Re-populate the listbox by case-insensitive substring
// match. Empty query = show everything. Order: zones, actors, items, spells
// (most-spatial to most-tabular).
// =============================================================================
Function CmdPalette_Filter(query$)
    FUI_SendMessage(CP_ListBox, M_RESET)
    ListClear(CP_Results)

    Local q$ = Lower$(Trim$(query$))
    Local resultIdx = 0
    Local caption$, name$, item

    // -- Zones --
    Local Ar.Area
    For Ar = Each Area
        name$ = Ar\Name$
        If q$ = "" Or Instr(Lower$(name$), q$) > 0
            caption$ = "[Zone]  " + name$
            CmdPalette_AddResult("zone", Handle(Ar), caption$, resultIdx)
            resultIdx = resultIdx + 1
        EndIf
    Next

    // -- Actors --
    Local At.Actor
    For At = Each Actor
        name$ = At\Race$ + " [" + At\Class$ + "]"
        If q$ = "" Or Instr(Lower$(name$), q$) > 0
            caption$ = "[Actor] " + name$
            CmdPalette_AddResult("actor", At\ID, caption$, resultIdx)
            resultIdx = resultIdx + 1
        EndIf
    Next

    // -- Items --
    Local It.Item
    For It = Each Item
        name$ = It\Name$
        If q$ = "" Or Instr(Lower$(name$), q$) > 0
            caption$ = "[Item]  " + name$
            CmdPalette_AddResult("item", It\ID, caption$, resultIdx)
            resultIdx = resultIdx + 1
        EndIf
    Next

    // -- Spells --
    Local Sp.Spell
    For Sp = Each Spell
        name$ = Sp\Name$
        If q$ = "" Or Instr(Lower$(name$), q$) > 0
            caption$ = "[Spell] " + name$
            CmdPalette_AddResult("spell", Sp\ID, caption$, resultIdx)
            resultIdx = resultIdx + 1
        EndIf
    Next

    // Update the hint line.
    If resultIdx = 0
        FUI_SendMessage(CP_HintLabel, M_SETCAPTION, "No matches.  Esc to close.")
    Else
        FUI_SendMessage(CP_HintLabel, M_SETCAPTION, Str(resultIdx) + " matches.  Enter to jump to first.  Esc to close.")
    EndIf
End Function


// Internal: append one result to both the in-memory list and the visible UI.
Function CmdPalette_AddResult(kind$, refID, caption$, idx)
    Local r.CPResult = New CPResult()
    r\Kind$ = kind$
    r\RefID = refID
    r\Caption$ = caption$
    ListAdd(CP_Results, r)

    Local item = FUI_ListBoxItem(CP_ListBox, caption$)
    FUI_SendMessage(item, M_SETDATA, idx)
End Function


// =============================================================================
// CmdPalette_HandleEvent -- Dispatch palette-owned events. Call from the main
// loop's event walk. Returns True when consumed.
// =============================================================================
Function CmdPalette_HandleEvent(EID, EData$)
    If CP_Open = False Then Return False

    If EID = CP_TextBox
        // Live filter: refilter on every keystroke. EData$ is the new value.
        CmdPalette_Filter(EData$)
        Return True
    EndIf

    If EID = CP_ListBox
        // Click on a result = jump.
        CmdPalette_Activate(False)
        Return True
    EndIf

    Return False
End Function


// =============================================================================
// CmdPalette_PollKeys -- Watch for Ctrl+K / Esc / Enter. Call once per frame
// from the main loop (after FUI_Update, before event dispatch is fine).
// =============================================================================
Function CmdPalette_PollKeys()
    // Global toggle. Ctrl+K opens; re-pressing closes.
    If FUI_ShortCut("Ctrl", "K") = True
        If CP_Open = True
            CmdPalette_Close()
        Else
            CmdPalette_Open()
        EndIf
        Return
    EndIf

    // Esc + Enter only while open.
    If CP_Open = False Then Return

    If KeyHit(1) = True   // Esc
        CmdPalette_Close()
        Return
    EndIf

    If KeyHit(28) = True Or KeyHit(156) = True   // Enter or numpad Enter
        // Activate; if nothing is selected, activate the first result.
        CmdPalette_Activate(True)
    EndIf
End Function


// Activate the current selection (or the first result if fallbackToFirst and
// the user hasn't clicked anything yet). Then close the palette.
Function CmdPalette_Activate(fallbackToFirst)
    Local sel = FUI_SendMessage(CP_ListBox, M_GETSELECTED)
    Local idx = -1

    If sel <> 0
        idx = FUI_SendMessage(sel, M_GETDATA)
    Else If fallbackToFirst = True
        If ListSize(CP_Results) > 0 Then idx = 0
    EndIf

    If idx < 0 Then Return

    Local r.CPResult = ListAt(CP_Results, idx)
    If r = Null Then Return

    Local kind$ = r\Kind$
    Local refID = r\RefID

    CmdPalette_Close()
    GUE_JumpToEntity(kind$, refID)
End Function


// =============================================================================
// GUE_JumpToEntity -- PUBLIC navigation primitive.
//
// Switches to the entity's owning tab and selects it. Reused by the palette
// today and by the conscience / cross-ref / atlas features.
//
// We programmatically set the tab index and the entity-picker selection, then
// fire the corresponding FUI events so the existing tab-switch / picker-
// change dispatchers run their normal hide/show + UpdateXDisplay logic next
// frame. This re-uses every line of the existing handlers instead of
// duplicating their show/hide bookkeeping.
//
// Supported kinds (and their entity-picker widget + data-payload convention):
//
//   kind     | tab | picker            | refID payload
//   ---------+-----+-------------------+--------------------------------------
//   actor    |  9  | CActorSelected    | Actor\ID                  (combobox)
//   item     | 10  | CItemSelected     | Item\ID                   (combobox)
//   spell    | 13  | CSpellSelected    | Spell\ID                  (combobox)
//   zone     | 12  | CZone             | Handle(Area)              (combobox)
//   faction  |  6  | LFactions         | FactionNames$ index 0..99 (listbox)
//   animset  |  7  | LAnimSets         | AnimSet\ID                (listbox)
//
// Listbox and combobox share the same M_SETINDEX / M_GETSELECTED / M_GETDATA
// shape in F-UI, so the picker-walk loop is identical.
// =============================================================================
Function GUE_JumpToEntity(kind$, refID)
    Local tabIdx = 0
    Local picker = 0

    Select Lower$(kind$)
        Case "actor"
            tabIdx = 9 : picker = CActorSelected
        Case "item"
            tabIdx = 10 : picker = CItemSelected
        Case "spell"
            tabIdx = 13 : picker = CSpellSelected
        Case "zone"
            tabIdx = 12 : picker = CZone
        Case "faction"
            tabIdx = 6 : picker = LFactions
        Case "animset"
            tabIdx = 7 : picker = LAnimSets
    End Select

    If tabIdx = 0 Or picker = 0 Then Return

    // Visible tab switch + ask the dispatcher to run its leave/enter logic.
    FUI_SendMessage(TabMain, M_SETINDEX, tabIdx)
    FUI_CreateEvent(TabMain, Str(tabIdx))

    // Walk the picker items to find the one whose stored data matches.
    // Items are 1-indexed in F-UI comboboxes and listboxes.
    Local n = FUI_SendMessage(picker, M_COUNTITEMS)
    Local i = 0
    Local pItem = 0
    Local pData = 0
    For i = 1 To n
        FUI_SendMessage(picker, M_SETINDEX, i)
        pItem = FUI_SendMessage(picker, M_GETSELECTED)
        pData = FUI_SendMessage(pItem, M_GETDATA)
        If pData = refID Then Exit
    Next

    // Fire the picker-change event so the downstream handler runs.
    FUI_CreateEvent(picker, "")
End Function
