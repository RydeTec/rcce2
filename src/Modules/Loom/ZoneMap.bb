// =============================================================================
// Loom/ZoneMap.bb -- top-down zone map surface
// =============================================================================
//
// When the user picks a zone in the atlas, Loom switches to this surface:
// a top-down 2D map of the chosen zone, drawing every waypoint, spawn
// point, trigger volume, and portal pulled from the Area type's fixed-size
// arrays. Click an entity to "select" it; PR #4's composer panel will
// paint the focused entity's full properties off that selection.
//
// Why a 2D map and not a literal 3D viewport:
//   The Loom design's "world scene" is itself stylized 2D SVG with a fake
//   3/4 perspective -- the design medium never assumed real 3D. A literal
//   3D render would also require LoadArea (ClientAreas.bb), which is
//   deeply entangled with GUE-specific UI globals (GY_Cam, GY_*, the
//   Gooey lib, GUE's GetFilename$ helper). The 2D map view ships now,
//   does the alpha job, and stays faithful to the design's aesthetic. A
//   literal 3D viewport can land as a beta refactor once LoadArea's data
//   path is decoupled from its UI path.
//
// Coordinate transform:
//   World coords use (X, Y, Z) with Y up. The map view projects (X, Z) to
//   2D screen, scaled to fit a bounding box computed once per zone open
//   (so a 200m zone and a 5000m zone both fill the view).
//
// Selection model:
//   `ZoneMap_Selected*` globals expose the last clicked entity. PR #4's
//   composer reads them to paint the property panel. Kinds are strings:
//   "waypoint" | "spawn" | "trigger" | "portal" | "" (none).
//
// Public API:
//   ZoneMap_Open(zoneHandle)
//     Switch into map mode for the given Handle(Area). Resets selection
//     and recomputes the view's bounding box.
//
//   ZoneMap_RenderAndUpdate(sw, sh) -> backRequested%
//     Per-frame. Returns True if the user clicked the Back-to-Atlas
//     button this frame (or pressed Esc), in which case Loom.bb should
//     switch back to atlas mode.
//
//   ZoneMap_SelectedKind$  ZoneMap_SelectedIndex   -- the current pick
// =============================================================================


// State -------------------------------------------------------------------
Global ZM_Open = False               // Is the map currently visible
Global ZM_Area.Area = Null           // The zone being viewed

Global ZoneMap_SelectedKind$ = ""    // "waypoint" | "spawn" | "trigger" | "portal" | ""
Global ZoneMap_SelectedIndex = -1    // index into the corresponding Area array

// View bounding box (world coords, X/Z plane). Computed once per Open.
Global ZM_MinX#, ZM_MaxX#, ZM_MinZ#, ZM_MaxZ#

// Pre-computed Back button rect (top-right of the map ribbon).
Global ZM_BackBtnX, ZM_BackBtnY, ZM_BackBtnW, ZM_BackBtnH

// Layout (mirrors atlas chrome heights).
Const ZM_TOP_RIBBON = 56
Const ZM_BOT_RIBBON = 36
Const ZM_RIGHT_PAD  = 16
Const ZM_LEFT_PAD   = 16

// Click target radius for entity hit-testing (pixels).
Const ZM_PICK_RADIUS = 12


// =============================================================================
// ZoneMap_Open -- switch into map mode for the given zone.
// =============================================================================
Function ZoneMap_Open(zoneHandle)
    Local A.Area = Object.Area(zoneHandle)
    If A = Null Then Return

    ZM_Area = A
    ZM_Open = True
    ZoneMap_SelectedKind$ = ""
    ZoneMap_SelectedIndex = -1

    ZoneMap_ComputeBounds(A)

    WriteLog(LoomLog, "ZoneMap: opened '" + A\Name$ + "' bounds X=[" + Str(ZM_MinX#) + ".." + Str(ZM_MaxX#) + "] Z=[" + Str(ZM_MinZ#) + ".." + Str(ZM_MaxZ#) + "]")
End Function


// Compute the X/Z bounding box of every placed entity in the zone, so the
// view can be scaled to fit them. Falls back to a 200x200 box centered on
// origin if the zone has no entities.
Function ZoneMap_ComputeBounds(A.Area)
    Local firstFound = True
    ZM_MinX# = 0.0 : ZM_MaxX# = 0.0
    ZM_MinZ# = 0.0 : ZM_MaxZ# = 0.0

    Local i = 0
    // Waypoints (most common, big array)
    For i = 0 To 1999
        // A waypoint is "placed" if any coord is non-zero -- the array starts
        // zero-filled and zone editors don't typically place anything at
        // exactly (0,0,0).
        If A\WaypointX#[i] <> 0.0 Or A\WaypointZ#[i] <> 0.0
            If firstFound
                ZM_MinX# = A\WaypointX#[i] : ZM_MaxX# = A\WaypointX#[i]
                ZM_MinZ# = A\WaypointZ#[i] : ZM_MaxZ# = A\WaypointZ#[i]
                firstFound = False
            Else
                If A\WaypointX#[i] < ZM_MinX# Then ZM_MinX# = A\WaypointX#[i]
                If A\WaypointX#[i] > ZM_MaxX# Then ZM_MaxX# = A\WaypointX#[i]
                If A\WaypointZ#[i] < ZM_MinZ# Then ZM_MinZ# = A\WaypointZ#[i]
                If A\WaypointZ#[i] > ZM_MaxZ# Then ZM_MaxZ# = A\WaypointZ#[i]
            EndIf
        EndIf
    Next
    // Portals
    For i = 0 To 99
        If A\PortalName$[i] <> ""
            If firstFound
                ZM_MinX# = A\PortalX#[i] : ZM_MaxX# = A\PortalX#[i]
                ZM_MinZ# = A\PortalZ#[i] : ZM_MaxZ# = A\PortalZ#[i]
                firstFound = False
            Else
                If A\PortalX#[i] < ZM_MinX# Then ZM_MinX# = A\PortalX#[i]
                If A\PortalX#[i] > ZM_MaxX# Then ZM_MaxX# = A\PortalX#[i]
                If A\PortalZ#[i] < ZM_MinZ# Then ZM_MinZ# = A\PortalZ#[i]
                If A\PortalZ#[i] > ZM_MaxZ# Then ZM_MaxZ# = A\PortalZ#[i]
            EndIf
        EndIf
    Next
    // Triggers
    For i = 0 To 149
        If A\TriggerScript$[i] <> ""
            If firstFound
                ZM_MinX# = A\TriggerX#[i] : ZM_MaxX# = A\TriggerX#[i]
                ZM_MinZ# = A\TriggerZ#[i] : ZM_MaxZ# = A\TriggerZ#[i]
                firstFound = False
            Else
                If A\TriggerX#[i] < ZM_MinX# Then ZM_MinX# = A\TriggerX#[i]
                If A\TriggerX#[i] > ZM_MaxX# Then ZM_MaxX# = A\TriggerX#[i]
                If A\TriggerZ#[i] < ZM_MinZ# Then ZM_MinZ# = A\TriggerZ#[i]
                If A\TriggerZ#[i] > ZM_MaxZ# Then ZM_MaxZ# = A\TriggerZ#[i]
            EndIf
        EndIf
    Next

    // Empty zone -- give us a default 200x200 window so the chrome paints
    // sensibly and there's something to look at.
    If firstFound = True
        ZM_MinX# = -100.0 : ZM_MaxX# = 100.0
        ZM_MinZ# = -100.0 : ZM_MaxZ# = 100.0
    EndIf

    // Pad the bounds 5% on each side so markers near the edge aren't
    // clipped by the view.
    Local padX# = (ZM_MaxX# - ZM_MinX#) * 0.05
    Local padZ# = (ZM_MaxZ# - ZM_MinZ#) * 0.05
    If padX# < 10.0 Then padX# = 10.0
    If padZ# < 10.0 Then padZ# = 10.0
    ZM_MinX# = ZM_MinX# - padX#
    ZM_MaxX# = ZM_MaxX# + padX#
    ZM_MinZ# = ZM_MinZ# - padZ#
    ZM_MaxZ# = ZM_MaxZ# + padZ#
End Function


// World -> screen projection. Returns screen pixel coords for a (worldX, worldZ)
// inside the current zone's view area.
Function ZoneMap_ProjX(worldX#, viewX, viewW)
    Local span# = ZM_MaxX# - ZM_MinX#
    If span# <= 0.0 Then Return viewX + viewW / 2
    Local t# = (worldX# - ZM_MinX#) / span#
    Return viewX + Int(t# * Float(viewW))
End Function

Function ZoneMap_ProjY(worldZ#, viewY, viewH)
    // World Z increases "north"; screen Y increases "south". Invert so
    // larger Z renders higher on screen.
    Local span# = ZM_MaxZ# - ZM_MinZ#
    If span# <= 0.0 Then Return viewY + viewH / 2
    Local t# = (worldZ# - ZM_MinZ#) / span#
    Return viewY + viewH - Int(t# * Float(viewH))
End Function


// =============================================================================
// ZoneMap_RenderAndUpdate -- per-frame entry point.
// =============================================================================
Function ZoneMap_RenderAndUpdate(sw, sh)
    If ZM_Open = False Or ZM_Area = Null Then Return False

    Local mx = MouseX()
    Local my = MouseY()
    Local clicked = MouseHit(1)
    Local backRequested = False

    // -- Background --------------------------------------------------------
    LoomGradientV(0, 0, sw, sh, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B, LOOM_STONE_950_R, LOOM_STONE_950_G, LOOM_STONE_950_B)

    // -- Top ribbon --------------------------------------------------------
    ZoneMap_DrawTopRibbon(sw, mx, my, clicked, backRequested)
    // (backRequested set via global -- Blitz doesn't have out-params, so we
    // pass it via the per-frame flag below instead.)
    If ZM_BackBtnClickedThisFrame = True Then backRequested = True
    ZM_BackBtnClickedThisFrame = False

    // -- View area --------------------------------------------------------
    Local viewX = ZM_LEFT_PAD
    Local viewY = ZM_TOP_RIBBON + 16
    Local viewW = sw - (ZM_LEFT_PAD + ZM_RIGHT_PAD)
    Local viewH = sh - ZM_TOP_RIBBON - ZM_BOT_RIBBON - 32

    // Subtle grid panel
    LoomFill(viewX, viewY, viewW, viewH, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B)
    LoomBorder(viewX, viewY, viewW, viewH, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)

    // -- Entities ----------------------------------------------------------
    Local pickedKind$ = ""
    Local pickedIndex = -1

    // Waypoints firstFound (drawn underneath everything else)
    Local i = 0
    For i = 0 To 1999
        If ZM_Area\WaypointX#[i] <> 0.0 Or ZM_Area\WaypointZ#[i] <> 0.0
            Local px = ZoneMap_ProjX(ZM_Area\WaypointX#[i], viewX, viewW)
            Local py = ZoneMap_ProjY(ZM_Area\WaypointZ#[i], viewY, viewH)
            ZoneMap_DrawDot(px, py, 3, LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
            // Hit-test (small radius for waypoints since there are many)
            If ZoneMap_HitDot(mx, my, px, py, 6) And clicked
                pickedKind$ = "waypoint" : pickedIndex = i
            EndIf
        EndIf
    Next

    // Triggers (semi-transparent rectangles, sort of)
    For i = 0 To 149
        If ZM_Area\TriggerScript$[i] <> ""
            Local tx = ZoneMap_ProjX(ZM_Area\TriggerX#[i], viewX, viewW)
            Local ty = ZoneMap_ProjY(ZM_Area\TriggerZ#[i], viewY, viewH)
            ZoneMap_DrawTriggerMarker(tx, ty)
            If ZoneMap_HitDot(mx, my, tx, ty, ZM_PICK_RADIUS) And clicked
                pickedKind$ = "trigger" : pickedIndex = i
            EndIf
        EndIf
    Next

    // Spawns
    For i = 0 To 999
        If ZM_Area\SpawnActor[i] > 0
            Local sx = ZoneMap_ProjX(0.0, viewX, viewW)  // spawn uses a waypoint for placement
            Local sy = ZoneMap_ProjY(0.0, viewY, viewH)
            // Spawn positions are indirect: they reference a waypoint index
            // (SpawnWaypoint[i]) for placement. Look it up.
            Local wp = ZM_Area\SpawnWaypoint[i]
            If wp >= 0 And wp <= 1999
                sx = ZoneMap_ProjX(ZM_Area\WaypointX#[wp], viewX, viewW)
                sy = ZoneMap_ProjY(ZM_Area\WaypointZ#[wp], viewY, viewH)
            EndIf
            ZoneMap_DrawSpawnMarker(sx, sy)
            If ZoneMap_HitDot(mx, my, sx, sy, ZM_PICK_RADIUS) And clicked
                pickedKind$ = "spawn" : pickedIndex = i
            EndIf
        EndIf
    Next

    // Portals (drawn on top of everything, biggest icons)
    For i = 0 To 99
        If ZM_Area\PortalName$[i] <> ""
            Local px2 = ZoneMap_ProjX(ZM_Area\PortalX#[i], viewX, viewW)
            Local py2 = ZoneMap_ProjY(ZM_Area\PortalZ#[i], viewY, viewH)
            ZoneMap_DrawPortalMarker(px2, py2, ZM_Area\PortalName$[i])
            If ZoneMap_HitDot(mx, my, px2, py2, ZM_PICK_RADIUS) And clicked
                pickedKind$ = "portal" : pickedIndex = i
            EndIf
        EndIf
    Next

    // Apply selection if click landed on something this frame
    If pickedKind$ <> ""
        ZoneMap_SelectedKind$ = pickedKind$
        ZoneMap_SelectedIndex = pickedIndex
        WriteLog(LoomLog, "ZoneMap: selected " + pickedKind$ + "[" + Str(pickedIndex) + "]")
    EndIf

    // Highlight the current selection (if it's in this zone)
    ZoneMap_HighlightSelection(viewX, viewY, viewW, viewH)

    // -- Footer ------------------------------------------------------------
    ZoneMap_DrawFooter(sw, sh)

    Return backRequested
End Function


// Flag toggled by the Back button hit-test; consumed by the main render
// pass on the same frame so we can return it cleanly.
Global ZM_BackBtnClickedThisFrame = False


Function ZoneMap_DrawTopRibbon(sw, mx, my, clicked, backRequestedDummy)
    LoomFill(0, 0, sw, ZM_TOP_RIBBON, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B)
    LoomHRule(0, ZM_TOP_RIBBON - 1, sw, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
    LoomHRule(0, ZM_TOP_RIBBON, sw, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
    LoomHRule(0, ZM_TOP_RIBBON + 1, sw, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)

    // Brand mark on far left
    LoomText(20, 18, "LOOM", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
    LoomText(20, 32, "Zone Map", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

    // Zone name centered
    LoomTextCentered(sw / 2, 22, ZM_Area\Name$, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

    // Back-to-atlas button on far right
    ZM_BackBtnW = 120
    ZM_BackBtnH = 28
    ZM_BackBtnX = sw - ZM_BackBtnW - 16
    ZM_BackBtnY = 14

    Local hovered = (mx >= ZM_BackBtnX And mx < ZM_BackBtnX + ZM_BackBtnW And my >= ZM_BackBtnY And my < ZM_BackBtnY + ZM_BackBtnH)

    If hovered = True
        LoomFill(ZM_BackBtnX, ZM_BackBtnY, ZM_BackBtnW, ZM_BackBtnH, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
        LoomBorder(ZM_BackBtnX, ZM_BackBtnY, ZM_BackBtnW, ZM_BackBtnH, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
    Else
        LoomFill(ZM_BackBtnX, ZM_BackBtnY, ZM_BackBtnW, ZM_BackBtnH, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
        LoomBorder(ZM_BackBtnX, ZM_BackBtnY, ZM_BackBtnW, ZM_BackBtnH, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
    EndIf
    LoomTextCentered(ZM_BackBtnX + ZM_BackBtnW / 2, ZM_BackBtnY + 7, "< Back to Atlas", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

    If hovered And clicked Then ZM_BackBtnClickedThisFrame = True
End Function


Function ZoneMap_DrawFooter(sw, sh)
    Local y = sh - ZM_BOT_RIBBON
    LoomFill(0, y, sw, ZM_BOT_RIBBON, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B)
    LoomHRule(0, y, sw, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)

    Local hint$ = "click a marker to select  ·  Esc returns to atlas"
    If ZoneMap_SelectedKind$ <> ""
        hint$ = "Selected: " + ZoneMap_SelectedKind$ + " #" + Str(ZoneMap_SelectedIndex) + "  ·  click another marker, or Esc for atlas"
    EndIf
    LoomText(20, y + 10, hint$, LOOM_STONE_200_R, LOOM_STONE_200_G, LOOM_STONE_200_B)
End Function


// -----------------------------------------------------------------------------
// Marker primitives -- simple shapes built from filled / outlined rects so they
// read at any zone-map scale.
// -----------------------------------------------------------------------------

Function ZoneMap_DrawDot(x, y, r, rr, gg, bb)
    LoomFill(x - r, y - r, r * 2, r * 2, rr, gg, bb)
End Function

Function ZoneMap_DrawTriggerMarker(x, y)
    // Red diamond shape, faked with two stacked rects rotated 45 (we just
    // overlay an inner brass dot for visual interest).
    LoomFill(x - 7, y - 7, 14, 14, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
    LoomBorder(x - 7, y - 7, 14, 14, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
    LoomFill(x - 2, y - 2, 4, 4, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
End Function

Function ZoneMap_DrawSpawnMarker(x, y)
    // Brass-ringed dot for spawns
    LoomFill(x - 6, y - 6, 12, 12, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
    LoomBorder(x - 6, y - 6, 12, 12, LOOM_BRASS_300_R, LOOM_BRASS_300_G, LOOM_BRASS_300_B)
    LoomFill(x - 2, y - 2, 4, 4, LOOM_STONE_950_R, LOOM_STONE_950_G, LOOM_STONE_950_B)
End Function

Function ZoneMap_DrawPortalMarker(x, y, label$)
    // Arcane-blue pin with a label below
    LoomFill(x - 8, y - 8, 16, 16, LOOM_ARCANE_700_R, LOOM_ARCANE_700_G, LOOM_ARCANE_700_B)
    LoomBorder(x - 8, y - 8, 16, 16, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
    LoomBorder(x - 9, y - 9, 18, 18, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
    LoomFill(x - 3, y - 3, 6, 6, LOOM_ARCANE_300_R, LOOM_ARCANE_300_G, LOOM_ARCANE_300_B)

    // Label below the marker
    LoomTextCentered(x, y + 12, label$, LOOM_ARCANE_300_R, LOOM_ARCANE_300_G, LOOM_ARCANE_300_B)
End Function

Function ZoneMap_HitDot(mx, my, dx, dy, r)
    If mx < dx - r Or mx > dx + r Then Return False
    If my < dy - r Or my > dy + r Then Return False
    Return True
End Function


// Draw a brass ring around the currently selected entity, in zone-projected
// coords. Safe to call when no selection (returns immediately).
Function ZoneMap_HighlightSelection(viewX, viewY, viewW, viewH)
    If ZoneMap_SelectedKind$ = "" Then Return
    If ZM_Area = Null Then Return

    Local sx = -1
    Local sy = -1
    Local idx = ZoneMap_SelectedIndex

    If ZoneMap_SelectedKind$ = "waypoint" And idx >= 0 And idx <= 1999
        sx = ZoneMap_ProjX(ZM_Area\WaypointX#[idx], viewX, viewW)
        sy = ZoneMap_ProjY(ZM_Area\WaypointZ#[idx], viewY, viewH)
    Else If ZoneMap_SelectedKind$ = "trigger" And idx >= 0 And idx <= 149
        sx = ZoneMap_ProjX(ZM_Area\TriggerX#[idx], viewX, viewW)
        sy = ZoneMap_ProjY(ZM_Area\TriggerZ#[idx], viewY, viewH)
    Else If ZoneMap_SelectedKind$ = "spawn" And idx >= 0 And idx <= 999
        Local wp = ZM_Area\SpawnWaypoint[idx]
        If wp >= 0 And wp <= 1999
            sx = ZoneMap_ProjX(ZM_Area\WaypointX#[wp], viewX, viewW)
            sy = ZoneMap_ProjY(ZM_Area\WaypointZ#[wp], viewY, viewH)
        EndIf
    Else If ZoneMap_SelectedKind$ = "portal" And idx >= 0 And idx <= 99
        sx = ZoneMap_ProjX(ZM_Area\PortalX#[idx], viewX, viewW)
        sy = ZoneMap_ProjY(ZM_Area\PortalZ#[idx], viewY, viewH)
    EndIf

    If sx < 0 Or sy < 0 Then Return

    // Pulsing ring around the selection -- two outlined rects so it reads.
    LoomBorder(sx - 14, sy - 14, 28, 28, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
    LoomBorder(sx - 15, sy - 15, 30, 30, LOOM_ARCANE_300_R, LOOM_ARCANE_300_G, LOOM_ARCANE_300_B)
End Function
