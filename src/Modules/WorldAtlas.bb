// =============================================================================
// WorldAtlas.bb -- Ctrl+M zone picker overlay
// =============================================================================
//
// Modal overlay invoked by Ctrl+M that shows every zone in the project as a
// scrollable list with a compact one-line summary -- name, portal/spawn/
// trigger counts, and a "loaded" pip for the currently-open zone. Clicking
// a row loads that zone in the Zones tab (via GUE_JumpToEntity), closing the
// overlay.
//
// Motivation: GUE currently picks the active zone through a combobox in the
// top-right corner of the Zones tab. To switch zones you must already be on
// the Zones tab, scroll the dropdown, and click. From any other tab it takes
// a tab-flip first. The atlas works from anywhere, shows useful per-zone
// detail at a glance, and lets you compare zones side-by-side instead of one
// at a time.
//
// Why this is its own module rather than a flag on the command palette: the
// palette is a free-text find-anywhere; the atlas is a spatial overview
// specifically for zones with per-zone metadata. Different jobs, different
// shapes, different hotkeys.
//
// Public API:
//   Atlas_Init()                -- create widgets. Call once at startup.
//   Atlas_PollKeys()            -- per-frame; watches for Ctrl+M / Esc.
//   Atlas_HandleEvent(EID,ED)   -- consume list-click events.
//
// Future room to grow (out of scope for this PR):
//   - World-map xy layout instead of a flat list, with portal lines between
//     connected zones
//   - "Stub" / "draft" / "live" status tags driven by a real completeness
//     heuristic
//   - Drag-drop to reorder zone load order
// =============================================================================


Global ATL_Window     = 0
Global ATL_List       = 0
Global ATL_HintLabel  = 0
Global ATL_Open       = False

Const ATL_W = 640
Const ATL_H = 460


// Side-table from listbox-item-index to the Area handle the row represents.
// F-UI's M_SETDATA on a listbox item can carry an int; we just stash Handle(Ar)
// directly. No BBList needed (Handle ints are stable for the Area's lifetime
// and that's all we need to round-trip).
//
// (If we ever add more per-row metadata than fits in an int, switch to the
// CPResult / CRFinding pattern used by the palette and conscience modules.)


// =============================================================================
// Atlas_Init -- create the hidden modal.
// =============================================================================
Function Atlas_Init()
    If ATL_Window <> 0 Then Return  // idempotent

    Local sw = GraphicsWidth()
    Local sh = GraphicsHeight()
    Local px = (sw - ATL_W) / 2
    Local py = (sh - ATL_H) / 3
    If py < 60 Then py = 60

    ATL_Window = FUI_Window(px, py, ATL_W, ATL_H, "World atlas -- pick a zone", "", 0, WS_TITLEBAR Or WS_CLOSEBUTTON)

    ATL_HintLabel = FUI_Label(ATL_Window, 12, 28, "Click a zone to load it.  Esc to close.")
    ATL_List = FUI_ListBox(ATL_Window, 12, 50, ATL_W - 24, ATL_H - 90, False, False)

    // F-UI Window gadgets respond to M_CLOSE/M_OPEN, not M_HIDE/M_SHOW. The
    // latter are no-ops for windows, which is why this overlay was popping up
    // on launch -- the init-time hide silently did nothing.
    FUI_SendMessage(ATL_Window, M_CLOSE)
End Function


// =============================================================================
// Atlas_PollKeys -- per-frame hotkey check. Call from the main loop.
// =============================================================================
Function Atlas_PollKeys()
    If FUI_ShortCut("Ctrl", "M") = True
        If ATL_Open = True
            Atlas_Close()
        Else
            Atlas_Open()
        EndIf
        Return
    EndIf

    If ATL_Open = False Then Return
    If KeyHit(1) = True Then Atlas_Close()   // Esc
End Function


// Show, repopulating the list from current world state (zone count can change
// while the editor's open).
Function Atlas_Open()
    If ATL_Window = 0 Then Atlas_Init()

    Atlas_Repopulate()

    FUI_SendMessage(ATL_Window, M_OPEN)
    FUI_SendMessage(ATL_Window, M_BRINGTOFRONT)
    ATL_Open = True
End Function


Function Atlas_Close()
    If ATL_Window = 0 Or ATL_Open = False Then Return
    FUI_SendMessage(ATL_Window, M_CLOSE)
    ATL_Open = False
End Function


// Walk every Area, produce one row per zone with a compact summary, store
// Handle(Ar) in M_SETDATA so click handling can recover the target.
Function Atlas_Repopulate()
    FUI_SendMessage(ATL_List, M_RESET)

    Local zoneCount = 0
    // Canonical inline-typed iterator. `Local Ar.Area` + `For Ar = Each Area`
    // wasn't binding the type info under non-Strict and the body's Ar\Name$
    // silently iterated nothing.
    For Ar.Area = Each Area
        zoneCount = zoneCount + 1

        Local portals = Atlas_CountPortals(Ar)
        Local spawns  = Atlas_CountSpawns(Ar)
        Local trigs   = Atlas_CountTriggers(Ar)

        Local prefix$ = "    "
        If Ar = CurrentArea Then prefix$ = " *  "   // loaded-zone pip

        Local caption$ = prefix$ + Atlas_PadRight$(Ar\Name$, 28) + "  " + Atlas_PadLeft$(Str(portals), 3) + " portals  " + Atlas_PadLeft$(Str(spawns), 4) + " spawns  " + Atlas_PadLeft$(Str(trigs), 3) + " triggers"

        Local item = FUI_ListBoxItem(ATL_List, caption$)
        FUI_SendMessage(item, M_SETDATA, Handle(Ar))
    Next

    If zoneCount = 0
        FUI_SendMessage(ATL_HintLabel, M_SETCAPTION, "No zones in this project yet.  Esc to close.")
    Else
        FUI_SendMessage(ATL_HintLabel, M_SETCAPTION, Str(zoneCount) + " zones.  " + "*" + " marks the currently loaded zone.  Click to load.  Esc to close.")
    EndIf
End Function


Function Atlas_CountPortals(Ar.Area)
    Local n = 0
    Local i = 0
    For i = 0 To 99
        If Ar\PortalName$[i] <> "" Then n = n + 1
    Next
    Return n
End Function


Function Atlas_CountSpawns(Ar.Area)
    Local n = 0
    Local i = 0
    For i = 0 To 999
        If Ar\SpawnActor[i] > 0 Then n = n + 1
    Next
    Return n
End Function


Function Atlas_CountTriggers(Ar.Area)
    Local n = 0
    Local i = 0
    For i = 0 To 149
        If Ar\TriggerScript$[i] <> "" Then n = n + 1
    Next
    Return n
End Function


// Right-pad a string to len chars with spaces (simple monospace alignment).
Function Atlas_PadRight$(s$, len)
    If Len(s) >= len Then Return Left$(s, len)
    Local pad$ = ""
    Local need = len - Len(s)
    Local i = 0
    For i = 1 To need
        pad$ = pad$ + " "
    Next
    Return s + pad$
End Function


Function Atlas_PadLeft$(s$, len)
    If Len(s) >= len Then Return s
    Local pad$ = ""
    Local need = len - Len(s)
    Local i = 0
    For i = 1 To need
        pad$ = pad$ + " "
    Next
    Return pad$ + s
End Function


// =============================================================================
// Atlas_HandleEvent -- consume list clicks.
// =============================================================================
Function Atlas_HandleEvent(EID, EData$)
    If ATL_Open = False Then Return False

    If EID = ATL_List
        // F-UI listbox: M_GETSELECTED -> 1-based row index (0 = none);
        //               M_GETINDEX    -> active row's item HANDLE.
        Local selIdx = FUI_SendMessage(ATL_List, M_GETSELECTED)
        If selIdx = 0 Then Return True
        Local selItem = FUI_SendMessage(ATL_List, M_GETINDEX)
        Local handleVal = FUI_SendMessage(selItem, M_GETDATA)
        If handleVal = 0 Then Return True

        Atlas_Close()
        GUE_JumpToEntity("zone", handleVal)
        Return True
    EndIf

    Return False
End Function
