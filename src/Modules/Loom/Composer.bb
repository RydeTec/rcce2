// =============================================================================
// Loom/Composer.bb -- right-side property panel for the selected entity
// =============================================================================
//
// The Loom design's composer is the right-hand panel that always shows
// whatever the user has focused. In the alpha it reads the selection set by
// ZoneMap (waypoint / spawn / trigger / portal) and paints the relevant
// fields from the Area type's per-entity arrays. It is read-only -- the
// alpha is about reading your world through Loom's lens, not editing it yet.
//
// Rendering model:
//   The composer overlays the right edge of the screen at a fixed width.
//   ZoneMap shrinks its view area by Composer_Width() pixels when a
//   selection exists, so markers along the right edge of a zone don't get
//   covered. When nothing is selected the composer is invisible and ZoneMap
//   uses the full viewport.
//
// Data resolution:
//   - waypoint: reads WaypointX/Y/Z, WaypointPause, NextWaypointA/B,
//     PrevWaypoint from ZM_Area's fixed-size arrays
//   - spawn: same plus ActorList lookup for the spawned actor's race+class
//   - trigger: TriggerX/Y/Z, TriggerSize, TriggerScript$, TriggerMethod$
//   - portal: PortalName$, PortalLinkArea$, PortalLinkName$, PortalX/Y/Z,
//     PortalYaw, PortalSize
//
// Public API:
//   Composer_Width()                     -- returns 0 when no selection,
//                                         else the panel's pixel width.
//   Composer_RenderIfVisible(sw, sh)     -- paints the panel if a
//                                         selection exists. Cheap when
//                                         not visible.
// =============================================================================


Const COMPOSER_W       = 340
Const COMPOSER_TOP     = 56     // matches ZM_TOP_RIBBON so the panel
                                // starts flush below the top ribbon
Const COMPOSER_BOT_PAD = 36     // matches ZM_BOT_RIBBON so it stops above the footer
Const COMPOSER_PAD     = 16


// =============================================================================
// Composer_Width -- returns the panel's pixel width, or 0 if there's no
// active selection. ZoneMap uses this to shrink its view so markers don't
// get hidden behind the composer.
// =============================================================================
Function Composer_Width()
    If ZoneMap_SelectedKind$ = "" Then Return 0
    Return COMPOSER_W
End Function


// =============================================================================
// Composer_RenderIfVisible -- per-frame paint. Returns immediately when
// there's no selection.
// =============================================================================
Function Composer_RenderIfVisible(sw, sh)
    If ZoneMap_SelectedKind$ = "" Then Return
    If ZM_Area = Null Then Return

    Local x = sw - COMPOSER_W
    Local y = COMPOSER_TOP
    Local w = COMPOSER_W
    Local h = sh - COMPOSER_TOP - COMPOSER_BOT_PAD

    // Panel chrome -- darker stone with a brass left rule (the panel's
    // "ornamented edge"). Wider stroke than the atlas card borders so the
    // composer reads as the primary surface in this view.
    LoomFill(x, y, w, h, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B)
    LoomBorder(x, y, w, h, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
    LoomFill(x, y, 3, h, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

    // Title block -- kind + display name. The kind label is brass eyebrow
    // type; the name is parchment.
    Local kind$ = ZoneMap_SelectedKind$
    Local kindLabel$ = Composer_KindLabel$(kind$)
    LoomText(x + COMPOSER_PAD, y + COMPOSER_PAD, kindLabel$, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
    LoomText(x + COMPOSER_PAD, y + COMPOSER_PAD + 16, Composer_EntityName$(kind$, ZoneMap_SelectedIndex), LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
    LoomHRule(x + COMPOSER_PAD, y + COMPOSER_PAD + 38, w - COMPOSER_PAD * 2, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)

    // Body -- per-kind property rows starting below the divider.
    Local bodyY = y + COMPOSER_PAD + 50

    If kind$ = "waypoint"
        Composer_RenderWaypoint(x, bodyY, w, ZoneMap_SelectedIndex)
    Else If kind$ = "spawn"
        Composer_RenderSpawn(x, bodyY, w, ZoneMap_SelectedIndex)
    Else If kind$ = "trigger"
        Composer_RenderTrigger(x, bodyY, w, ZoneMap_SelectedIndex)
    Else If kind$ = "portal"
        Composer_RenderPortal(x, bodyY, w, ZoneMap_SelectedIndex)
    EndIf

    // Footer note: read-only-for-alpha disclosure so the user isn't
    // confused about why fields don't take typing.
    LoomText(x + COMPOSER_PAD, y + h - 24, "Read-only in alpha", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
End Function


// -----------------------------------------------------------------------------
// Per-kind body renderers. Each lays out rows of `label : value` at the
// composer's column. Spacing is fixed -- no scrolling needed in the alpha
// because no kind has more rows than will fit.
// -----------------------------------------------------------------------------

Function Composer_RenderWaypoint(panelX, y, panelW, idx)
    If idx < 0 Or idx > 1999 Then Return

    Composer_DrawRow(panelX, y +   0, panelW, "Index",          Str(idx))
    Composer_DrawRow(panelX, y +  24, panelW, "Position X",     Composer_FormatFloat$(ZM_Area\WaypointX#[idx]))
    Composer_DrawRow(panelX, y +  48, panelW, "Position Y",     Composer_FormatFloat$(ZM_Area\WaypointY#[idx]))
    Composer_DrawRow(panelX, y +  72, panelW, "Position Z",     Composer_FormatFloat$(ZM_Area\WaypointZ#[idx]))
    Composer_DrawRow(panelX, y +  96, panelW, "Pause (ms)",     Str(ZM_Area\WaypointPause[idx]))
    Composer_DrawRow(panelX, y + 120, panelW, "Next A",         Composer_WaypointRef$(ZM_Area\NextWaypointA[idx]))
    Composer_DrawRow(panelX, y + 144, panelW, "Next B",         Composer_WaypointRef$(ZM_Area\NextWaypointB[idx]))
    Composer_DrawRow(panelX, y + 168, panelW, "Previous",       Composer_WaypointRef$(ZM_Area\PrevWaypoint[idx]))
End Function


Function Composer_RenderSpawn(panelX, y, panelW, idx)
    If idx < 0 Or idx > 999 Then Return

    Local actorID = ZM_Area\SpawnActor[idx]
    Local actorName$ = "(unbound)"
    If actorID > 0 And actorID < 65535
        Local Ac.Actor = ActorList(actorID)
        If Ac <> Null Then actorName$ = Ac\Race$ + " [" + Ac\Class$ + "]"
    EndIf

    Composer_DrawRow(panelX, y +   0, panelW, "Index",          Str(idx))
    Composer_DrawRow(panelX, y +  24, panelW, "Actor",          actorName$)
    Composer_DrawRow(panelX, y +  48, panelW, "Waypoint",       Composer_WaypointRef$(ZM_Area\SpawnWaypoint[idx]))
    Composer_DrawRow(panelX, y +  72, panelW, "Size",           Composer_FormatFloat$(ZM_Area\SpawnSize#[idx]))
    Composer_DrawRow(panelX, y +  96, panelW, "Frequency",      Str(ZM_Area\SpawnFrequency[idx]))
    Composer_DrawRow(panelX, y + 120, panelW, "Max",            Str(ZM_Area\SpawnMax[idx]))
    Composer_DrawRow(panelX, y + 144, panelW, "Range",          Composer_FormatFloat$(ZM_Area\SpawnRange#[idx]))
    Composer_DrawRow(panelX, y + 168, panelW, "Spawn script",   Composer_OrDash$(ZM_Area\SpawnScript$[idx]))
    Composer_DrawRow(panelX, y + 192, panelW, "Actor script",   Composer_OrDash$(ZM_Area\SpawnActorScript$[idx]))
    Composer_DrawRow(panelX, y + 216, panelW, "Death script",   Composer_OrDash$(ZM_Area\SpawnDeathScript$[idx]))
End Function


Function Composer_RenderTrigger(panelX, y, panelW, idx)
    If idx < 0 Or idx > 149 Then Return

    Composer_DrawRow(panelX, y +   0, panelW, "Index",          Str(idx))
    Composer_DrawRow(panelX, y +  24, panelW, "Position X",     Composer_FormatFloat$(ZM_Area\TriggerX#[idx]))
    Composer_DrawRow(panelX, y +  48, panelW, "Position Y",     Composer_FormatFloat$(ZM_Area\TriggerY#[idx]))
    Composer_DrawRow(panelX, y +  72, panelW, "Position Z",     Composer_FormatFloat$(ZM_Area\TriggerZ#[idx]))
    Composer_DrawRow(panelX, y +  96, panelW, "Size",           Composer_FormatFloat$(ZM_Area\TriggerSize#[idx]))
    Composer_DrawRow(panelX, y + 120, panelW, "Script",         Composer_OrDash$(ZM_Area\TriggerScript$[idx]))
    Composer_DrawRow(panelX, y + 144, panelW, "Method",         Composer_OrDash$(ZM_Area\TriggerMethod$[idx]))
End Function


Function Composer_RenderPortal(panelX, y, panelW, idx)
    If idx < 0 Or idx > 99 Then Return

    Composer_DrawRow(panelX, y +   0, panelW, "Index",          Str(idx))
    Composer_DrawRow(panelX, y +  24, panelW, "Name",           Composer_OrDash$(ZM_Area\PortalName$[idx]))
    Composer_DrawRow(panelX, y +  48, panelW, "Target area",    Composer_OrDash$(ZM_Area\PortalLinkArea$[idx]))
    Composer_DrawRow(panelX, y +  72, panelW, "Target portal",  Composer_OrDash$(ZM_Area\PortalLinkName$[idx]))
    Composer_DrawRow(panelX, y +  96, panelW, "Position X",     Composer_FormatFloat$(ZM_Area\PortalX#[idx]))
    Composer_DrawRow(panelX, y + 120, panelW, "Position Y",     Composer_FormatFloat$(ZM_Area\PortalY#[idx]))
    Composer_DrawRow(panelX, y + 144, panelW, "Position Z",     Composer_FormatFloat$(ZM_Area\PortalZ#[idx]))
    Composer_DrawRow(panelX, y + 168, panelW, "Yaw",            Composer_FormatFloat$(ZM_Area\PortalYaw#[idx]))
    Composer_DrawRow(panelX, y + 192, panelW, "Size",           Composer_FormatFloat$(ZM_Area\PortalSize#[idx]))
End Function


// -----------------------------------------------------------------------------
// Row + formatting helpers
// -----------------------------------------------------------------------------

// One label/value row. Label is brass; value is parchment. Both painted with
// the default Blitz font; tighter layout (24px row height) than GUE's
// per-field gadgets so the composer fits 8-9 rows without scrolling.
Function Composer_DrawRow(panelX, rowY, panelW, label$, value$)
    Local labelX = panelX + COMPOSER_PAD
    Local valueX = panelX + COMPOSER_PAD + 100

    LoomText(labelX, rowY,     label$, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
    LoomText(valueX, rowY,     value$, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
End Function


Function Composer_KindLabel$(kind$)
    If kind$ = "waypoint" Then Return "WAYPOINT"
    If kind$ = "spawn"    Then Return "SPAWN POINT"
    If kind$ = "trigger"  Then Return "TRIGGER VOLUME"
    If kind$ = "portal"   Then Return "PORTAL"
    Return Upper$(kind$)
End Function


// Resolve a display name for the selected entity. Most kinds don't have
// a real name -- they're identified by index -- so we synthesize one.
Function Composer_EntityName$(kind$, idx)
    If kind$ = "waypoint" Then Return "Waypoint #" + Str(idx)
    If kind$ = "trigger"
        Local script$ = ZM_Area\TriggerScript$[idx]
        If script$ = "" Then Return "Trigger #" + Str(idx)
        Return script$
    EndIf
    If kind$ = "portal"
        Local name$ = ZM_Area\PortalName$[idx]
        If name$ = "" Then Return "Portal #" + Str(idx)
        Return name$
    EndIf
    If kind$ = "spawn"
        Local actorID = ZM_Area\SpawnActor[idx]
        If actorID > 0 And actorID < 65535
            Local Ac.Actor = ActorList(actorID)
            If Ac <> Null Then Return Ac\Race$ + " [" + Ac\Class$ + "]"
        EndIf
        Return "Spawn #" + Str(idx)
    EndIf
    Return ""
End Function


// Format a float to 1 decimal place. Blitz Str# rounds nastily by default;
// this clamps the displayed precision so coords don't render as
// "1.23456789e-07" or similar.
Function Composer_FormatFloat$(v#)
    Local rounded# = Float(Int(v# * 10.0)) / 10.0
    Return Str$(rounded#)
End Function


// Show "(none)" for empty strings -- avoids confusion between an empty
// field and a missing field.
Function Composer_OrDash$(s$)
    If s$ = "" Then Return "(none)"
    Return s$
End Function


// Format a waypoint reference index for display. Some Area fields use -1
// or 0 to mean "no link", depending on the field; we render both as "(none)".
Function Composer_WaypointRef$(idx)
    If idx <= 0 Then Return "(none)"
    Return "#" + Str(idx)
End Function
