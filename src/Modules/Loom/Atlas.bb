// =============================================================================
// Loom/Atlas.bb -- world atlas (zone picker) boot surface
// =============================================================================
//
// The atlas is the first surface the user sees after Loom finishes loading
// project data. It lists every zone in the project as a clickable card laid
// out in a grid. Clicking a card "selects" the zone -- in this PR that just
// records the selection and exits the atlas; PR #3 will hand off to the
// world-view surface that renders the chosen zone in 3D.
//
// Why custom-draw rather than FUI_ListBox:
//   The Loom design treats zone selection as a spatial overview, not a
//   dropdown. Cards with per-zone stats (portal / spawn / trigger counts)
//   beat a textual list of names, and the dark-fantasy aesthetic requires
//   colors and ornament F-UI can't render. We paint everything through
//   Theme.bb's primitives and hit-test the mouse ourselves -- a few dozen
//   rectangles, trivial cost per frame.
//
// Public API:
//   Atlas_Init()                           -- called once after data is loaded
//   Atlas_RenderAndUpdate(sw, sh, project$) -> selectedHandle
//                                          -- per-frame; returns the
//                                            Handle(Area) the user clicked
//                                            this frame (0 if no click)
//
// Internal state -- the tile rectangles built once at Init from Each Area
// and reused every frame for both render and hit-test.
// =============================================================================


// Layout constants (all in pixels).
Const ATLAS_TILE_W      = 280
Const ATLAS_TILE_H      = 90
Const ATLAS_GAP         = 16
Const ATLAS_TOP_RIBBON  = 56     // brand strip at the very top
Const ATLAS_BOT_RIBBON  = 36     // footer strip at the very bottom
Const ATLAS_SECTION_PAD = 32     // padding around the tile grid


// One entry per zone -- pre-computed at Init so render + hit-test don't
// recompute per frame. The Area handle round-trips to the caller as the
// "selected zone" identifier (matching the convention GUE_JumpToEntity used
// for zones in the previous round).
Type AtlasTile
    Field AreaHandle
    Field Name$
    Field Portals
    Field Spawns
    Field Triggers
    Field X, Y      // top-left corner, computed each frame from sw/sh
End Type
Global Atlas_FirstTile.AtlasTile = Null   // marker: any tiles built?

// Diagnostic state -- not load-bearing for the UI but useful for the log.
Global Atlas_ZoneCount = 0


// =============================================================================
// Atlas_Init -- walk every Area, build one tile per zone with its summary
// counts. Safe to call multiple times (clears previous tiles first).
// =============================================================================
Function Atlas_Init()
    // Clear any previous tiles (in case Init is called after data reload).
    For old.AtlasTile = Each AtlasTile
        Delete old
    Next
    Atlas_FirstTile = Null
    Atlas_ZoneCount = 0

    // One tile per Area. Counts walk the Area's fixed-size arrays.
    For Ar.Area = Each Area
        Local t.AtlasTile = New AtlasTile
        t\AreaHandle = Handle(Ar)
        t\Name$ = Ar\Name$
        t\Portals = Atlas_CountPortals(Ar)
        t\Spawns = Atlas_CountSpawns(Ar)
        t\Triggers = Atlas_CountTriggers(Ar)
        If Atlas_FirstTile = Null Then Atlas_FirstTile = t
        Atlas_ZoneCount = Atlas_ZoneCount + 1
    Next

    WriteLog(LoomLog, "Atlas: indexed " + Str(Atlas_ZoneCount) + " zones")
End Function


// Walk the Area's three reference arrays to produce summary counts. Same
// pattern (and same magic-number bounds) as Area's fixed-size storage.
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


// =============================================================================
// Atlas_RenderAndUpdate -- per-frame entry point. Paints the atlas surface
// (top brand strip + tile grid + footer hint), tracks mouse hover for the
// tiles, and returns Handle(Area) if the user clicked a tile this frame
// (0 otherwise).
// =============================================================================
Function Atlas_RenderAndUpdate(sw, sh, project$)
    Local mx = MouseX()
    Local my = MouseY()
    Local clicked = MouseHit(1)
    Local selectedHandle = 0

    // -- Background gradient ------------------------------------------------
    LoomGradientV(0, 0, sw, sh, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B, LOOM_STONE_950_R, LOOM_STONE_950_G, LOOM_STONE_950_B)

    // -- Top brand strip ----------------------------------------------------
    Atlas_DrawTopRibbon(sw, project$)

    // -- Footer hint --------------------------------------------------------
    Atlas_DrawFooter(sw, sh)

    // -- Tile grid ----------------------------------------------------------
    // Compute columns that fit. Reserve ATLAS_SECTION_PAD on either side.
    Local gridX = ATLAS_SECTION_PAD
    Local gridY = ATLAS_TOP_RIBBON + ATLAS_SECTION_PAD
    Local gridW = sw - (ATLAS_SECTION_PAD * 2)
    Local cols = (gridW + ATLAS_GAP) / (ATLAS_TILE_W + ATLAS_GAP)
    If cols < 1 Then cols = 1

    // Empty-state message
    If Atlas_FirstTile = Null
        LoomTextCentered(sw / 2, sh / 2, "No zones found in this project.", LOOM_STONE_200_R, LOOM_STONE_200_G, LOOM_STONE_200_B)
        LoomTextCentered(sw / 2, sh / 2 + 20, "Create a zone in GUE first, then come back to Loom.", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
        Return 0
    EndIf

    // Lay out + render each tile
    Local col = 0
    Local row = 0
    For t.AtlasTile = Each AtlasTile
        t\X = gridX + col * (ATLAS_TILE_W + ATLAS_GAP)
        t\Y = gridY + row * (ATLAS_TILE_H + ATLAS_GAP)

        Local hovered = (mx >= t\X And mx < t\X + ATLAS_TILE_W And my >= t\Y And my < t\Y + ATLAS_TILE_H)
        Atlas_DrawTile(t, hovered)

        If hovered And clicked Then selectedHandle = t\AreaHandle

        col = col + 1
        If col >= cols
            col = 0
            row = row + 1
        EndIf
    Next

    Return selectedHandle
End Function


// Paint one zone card.
Function Atlas_DrawTile(t.AtlasTile, hovered)
    // Card fill -- darker stone, with subtle border. Hover lifts to arcane.
    LoomFill(t\X, t\Y, ATLAS_TILE_W, ATLAS_TILE_H, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)

    If hovered = True
        LoomBorder(t\X, t\Y, ATLAS_TILE_W, ATLAS_TILE_H, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        LoomBorder(t\X + 1, t\Y + 1, ATLAS_TILE_W - 2, ATLAS_TILE_H - 2, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
    Else
        LoomBorder(t\X, t\Y, ATLAS_TILE_W, ATLAS_TILE_H, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
    EndIf

    // Top brass accent line -- mimics ornamented panel headers in the design.
    LoomHRule(t\X + 12, t\Y + 8, ATLAS_TILE_W - 24, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

    // Zone name
    LoomText(t\X + 12, t\Y + 16, t\Name$, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

    // Stats row -- portals / spawns / triggers, brass labels with parchment counts
    Local statsY = t\Y + 50
    LoomText(t\X + 12, statsY, "Portals", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
    LoomText(t\X + 12, statsY + 16, Str(t\Portals), LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

    LoomText(t\X + 100, statsY, "Spawns", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
    LoomText(t\X + 100, statsY + 16, Str(t\Spawns), LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

    LoomText(t\X + 188, statsY, "Triggers", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
    LoomText(t\X + 188, statsY + 16, Str(t\Triggers), LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
End Function


// Top brand strip: "LOOM" mark on the left, project name centered, count + Esc hint on the right.
Function Atlas_DrawTopRibbon(sw, project$)
    LoomFill(0, 0, sw, ATLAS_TOP_RIBBON, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B)
    LoomHRule(0, ATLAS_TOP_RIBBON - 1, sw, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
    LoomHRule(0, ATLAS_TOP_RIBBON, sw, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
    LoomHRule(0, ATLAS_TOP_RIBBON + 1, sw, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)

    LoomText(20, 18, "LOOM", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
    LoomText(20, 32, "World Atlas", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

    LoomTextCentered(sw / 2, 22, project$, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
End Function


Function Atlas_DrawFooter(sw, sh)
    Local y = sh - ATLAS_BOT_RIBBON
    LoomFill(0, y, sw, ATLAS_BOT_RIBBON, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B)
    LoomHRule(0, y, sw, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)

    LoomText(20, y + 10, Str(Atlas_ZoneCount) + " zones  ·  click a zone to inspect", LOOM_STONE_200_R, LOOM_STONE_200_G, LOOM_STONE_200_B)

    // Right-aligned: Esc hint
    LoomText(sw - 20, y + 10, "Esc to exit", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B, 2)
End Function
