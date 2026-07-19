; =============================================================================
; Loom/ZoneViewport.bb -- 3D viewport for zone sub-entities, two modes:
; =============================================================================
;
;   SCHEMATIC (default)  portal/spawn/trigger/waypoint markers over a flat
;                        ground plane, from ServerLoadArea's gameplay data.
;   WORLD (toggle pill)  the zone's REAL terrain/scenery/water loaded from
;                        Data\Areas\<name>.dat via LoadAreaData -- the
;                        data-only loader ADR-004 Phase B carved out of
;                        GUE's LoadArea -- with the markers overlaid at
;                        their true coordinates. This is ADR-004 Phase C.
;
; World-mode mechanics: the schematic scene lives at y=VP_SCENE_Y_OFFSET
; (camera isolation from MeshPreview); LoadAreaData places geometry at
; real coordinates (y~=0). Rather than re-parenting the loaded world, the
; whole viewport rides a mode-dependent offset (VPSceneYOff#): the const
; in schematic mode, 0.0 in world mode (markers reload at real coords and
; the camera / grid / drag math follow the same variable). Zones without
; a visual .dat soft-fail back to schematic with a toast. Editing works in
; BOTH modes: the add / drag-move / delete handlers pick through the shared
; Loom_PickGround helper, which lands on the flat editor floor (VPGround) in
; schematic mode and on the zone's REAL terrain / scenery in world mode, so
; entities can be placed and moved directly against the rendered ground
; (ADR-004's "editing against real terrain in world mode" follow-up).
;
; Visual:
;   Ground plane           dark stone-900 quad at y=0
;   Portal markers         brass cubes
;   Spawn markers          arcane-cyan cubes
;   Trigger markers        warning-orange cubes
;   Waypoint markers       small stone cubes + lines for prev/next chain
;
; Camera:
;   Same isolated-y trick as MeshPreview -- viewport camera lives at
;   y=20000 (far from mesh preview at 10000 and any future world).
;   The zone's own data sits at ground level (y~=0) but we OFFSET the
;   entire schematic scene up by VP_SCENE_Y_OFFSET so the viewport
;   camera can find it without conflicting with other previews.
;
; Mouse:
;   LMB drag = orbit around scene center
;   wheel    = zoom in/out
;   (Pan + click-to-focus-marker are follow-up iters.)
;
; Performance:
;   Markers are CreateCube'd once per visible sub-entity. Reset on zone
;   change. A zone with 100 portals + 1000 spawns + 150 triggers + 2000
;   waypoints = ~3250 entities -- well within Blitz3D's headroom.
;
; Non-Strict (matches MeshPreview / Settings / Recents).


; Sized to fit inside the 380px composer panel (CMP_W) with the
; standard CMP_PAD breathing room. Previously 384 which leaked
; left of the panel and produced the overlapping-text bug a user
; reported (composer fields and viewport overlay both drew in the
; same X column).
Const VP_RT_SIZE          = 320
Const VP_SCENE_Y_OFFSET#  = 20000.0      ; isolate from mesh preview at y=10000
Const VP_DEFAULT_CAM_DIST# = 400.0
Const VP_MARKER_SIZE#     = 4.0
Const VP_WAYPOINT_SIZE#   = 1.5
Const VP_AXIS_LENGTH#     = 30.0          ; XYZ axis indicator length
Const VP_AXIS_THICKNESS#  = 0.3           ; thin cube acting as a line
Const VP_LINE_THICKNESS#  = 0.25          ; waypoint connection line thickness
Const VP_MAX_LINES        = 500           ; cap connection line entities
Const VP_MAX_SPAWN_MESHES = 200           ; cap loaded actor meshes per zone
                                          ; (beyond this, spawns fall back to
                                          ; marker cubes -- bounds load cost
                                          ; on pathological 1000-spawn zones)


; ---- Module state -----------------------------------------------------------
; Mode-dependent Y offset for everything the viewport places or projects:
; VP_SCENE_Y_OFFSET# in schematic mode, 0.0 in world mode (the loaded world
; sits at real coordinates and cannot be re-parented upward).
Global VPSceneYOff#  = VP_SCENE_Y_OFFSET#
Global VPWorldMode   = False     ; user-facing toggle state
Global VPWorldLoaded = False     ; an UnloadArea() is owed when True

Global VPCam        = 0
Global VPLight      = 0
Global VPRT         = 0
Global VPGround     = 0           ; ground plane entity (at origin, for pick-land)
Global VPPivot      = 0           ; hidden orbit/look pivot, moved to the
                                  ; scene centre each frame so the camera
                                  ; orbits AND looks at the same point
Global VPInitOK     = False
Global VPLoadedZoneH = 0          ; Handle(Area) of currently-loaded zone

; Camera orbit state
Global VPYaw#       = 0.0
Global VPPitch#     = 25.0
Global VPDistance#  = VP_DEFAULT_CAM_DIST#
Global VPSceneCenterX# = 0.0     ; auto-fit center of the loaded zone
Global VPSceneCenterY# = 0.0
Global VPSceneCenterZ# = 0.0
Global VPDragging   = False
Global VPLastMX     = 0
Global VPLastMY     = 0
Global VPDragStartMX = 0
Global VPDragStartMY = 0

; Marker drag-to-edit state. Right-click on a marker to enter drag
; mode; subsequent frames track the cursor on the ground plane and
; reposition the marker + update the underlying Area coord field.
; Release RMB to commit.
Global VPMarkerDragging   = False
Global VPMarkerDragEN     = 0            ; entity handle of the marker being dragged
Global VPMarkerDragKind$  = ""
Global VPMarkerDragIdx    = -1
Global VPMarkerDragArH    = 0            ; Handle(Area) of the zone being edited

; Shift held at drag-start = Y-axis mode: vertical mouse delta becomes Y
; delta instead of CameraPick-on-ground driving XZ. Locked at press time
; (not re-sampled mid-drag) so the user can release shift after the press
; without breaking the gesture.
Global VPMarkerDragYMode = False
Global VPMarkerDragLastMY = 0    ; per-frame Y delta basis
; Centered cube markers render above their semantic Area Y. Keep that
; presentation-only lift with the active drag so it never leaks into saves.
Global VPMarkerDragVisualLift# = 0.0
; True once a drag actually committed a coordinate write. The release
; handler only toasts "Moved ..." + marks the zone dirty when a write
; happened -- an XZ drag whose every pick missed the ground (e.g. the
; ground is hidden) used to fire a false success toast and a spurious
; dirty flag. (Review finding on #551.)
Global VPMarkerDragChanged = False

; Result of the last Loom_PickGround call: the world-space point the pick
; ray landed on (ground plane in schematic mode, real terrain / scenery in
; world mode). Read by the add / drag handlers instead of PickedX#/Y#/Z#
; directly so the schematic-vs-world pick-target difference lives in one place.
Global VPPickX# = 0.0
Global VPPickY# = 0.0
Global VPPickZ# = 0.0

; MMB pan state. Middle-mouse drag translates VPSceneCenterX/Z in
; camera-aligned screen-right and screen-forward directions so the
; user can scroll the view to focus on a particular zone corner.
Global VPPanning   = False
Global VPPanLastMX = 0
Global VPPanLastMY = 0

; RMB edge-detect so shift+RMB add-trigger fires once on the press
; instead of repeatedly while the button is held.
Global VPRMBPrevDown = False
; MMB edge-detect for the same reason on shift+MMB add-spawn.
Global VPMMBPrevDown = False

; Per-zone counts cached at load time (saves recomputing in renderer
; just for the legend overlay).
Global VPCountPortals  = 0
Global VPCountSpawns   = 0
Global VPCountTriggers = 0
Global VPCountWaypoints = 0
Global VPCountLines    = 0     ; total connection lines emitted

; Render-on-change flag. Set True any time camera / markers / highlight
; change; consumed by Loom_DrawZoneViewport which only does
; RenderWorld + CopyRect when True. Cached pixels persist between
; frames so a static viewport costs ~0.
Global VPDirty         = True

; Last-frame highlight so we only ScaleEntity on TRANSITIONS instead
; of every frame's full marker walk. Empty/-1 = no previous highlight.
Global VPPrevHighlightKind$ = ""
Global VPPrevHighlightIdx   = -1

; Auto-fit values captured at zone load. The Reset View button
; restores these so the user can recover from a confusing camera
; orbit without reloading the zone.
Global VPInitialCenterX# = 0.0
Global VPInitialCenterZ# = 0.0
Global VPInitialDistance# = VP_DEFAULT_CAM_DIST#

; Module-level Composer pointer set by Loom.bb after construction.
; Lets Loom_PickZoneMarker dispatch into Composer::scrollToZoneSubEntity
; without holding a per-call reference. Same shape as LoomWorldCache.
Global LoomComposer.Composer = Null

; LoomZoneHighlightKind$ / LoomZoneHighlightIdx live in ImageCache.bb
; (included BEFORE Composer) so the Strict Composer module can write
; them without the dim-write-from-Strict trap that bit Settings
; globals. See ImageCache.bb / feedback_loom_module_include_order.


; ---- Scenery editing (Phase D-1) --------------------------------------------
; Scenery placement is WORLD-MODE ONLY: scenery lives in the visual .dat
; (loaded by LoadAreaData, persisted by SaveArea) -- it does not exist in the
; schematic gameplay data. So all scenery ops below are gated on VPWorldMode /
; VPWorldLoaded, and are mutually exclusive with the schematic portal/trigger/
; spawn ops (those pick against VPGround, which is hidden in world mode).
;
; DATA-LOSS GUARD: SaveArea rewrites the ENTIRE visual .dat (terrain / water /
; scenery / emitters / colboxes / sound zones) from the live Each-<Type> lists.
; It must NEVER run unless VPWorldLoaded is True (every section in memory), or
; it zeroes the on-disk file. SceneryDirty is a SEPARATE flag from the shared
; ZoneSaved so a scenery edit can never trigger the gameplay ServerSaveArea and
; a gameplay edit can never trigger SaveArea. See ADR-007.
;
; These MUST be module-level Global / Const: this file is Non-Strict, so an
; undeclared identifier silently auto-declares as a per-function local (zero-
; init each call) -- which would make every scenery interaction inert. Do not
; remove.
Global SceneryDirty = False        ; True = unsaved scenery edits pending

; "Add scenery" brush mode: a mesh is chosen from the MeshCatalog picker, then
; each LMB click on the terrain drops an instance of it.
Global ScnAddMode      = False     ; picker panel open + placement armed
Global ScnBrushMeshID  = 0         ; ENGINE mesh id of the selected brush (0 = none)
Global ScnBrushName$   = ""        ; display name of the selected brush mesh
Global ScnPickerScroll = 0         ; first visible row in the mesh picker list

; Currently-selected scenery instance (Handle(Scenery), 0 = none). Drives the
; property readout + is the move/delete target.
Global ScnSelectedH    = 0

; Scenery move-drag state (RMB-drag a scenery entity on the terrain). Mirrors
; the schematic marker-drag shape (XZ by default, Shift at press = Y mode).
Global ScnDragging     = False
Global ScnDragEN       = 0         ; entity handle being dragged
Global ScnDragH        = 0         ; Handle(Scenery) being dragged
Global ScnDragYMode    = False
Global ScnDragLastMY   = 0
Global ScnDragChanged  = False

; Picker-panel layout (drawn inside the viewport rect, right edge).
Const SCN_PICK_W       = 168
Const SCN_PICK_ROW_H   = 15
Const SCN_PICK_ROWS    = 18        ; visible rows before scroll


; =============================================================================
; AreaLoad* presentation hooks -- the contract AreaLoader.bb requires of any
; including target (GUE implements them with the Gooey loading screen in
; ClientAreas.bb). Loom needs no loading presentation: editor-side zone
; loads are sub-second and the viewport repaints continuously.
; =============================================================================
Function AreaLoadBegin(DisplayItems)
End Function

Function AreaLoadProgress(Pct)
End Function

Function AreaLoadEnd()
End Function


; =============================================================================
; Loom_InitZoneViewport -- one-time setup at boot.
; =============================================================================
Function Loom_InitZoneViewport()
    If VPInitOK = True Then Return

    VPRT = CreateTexture(VP_RT_SIZE, VP_RT_SIZE, 1 + 256)
    If VPRT = 0
        WriteLog(LoomLog, "ZoneViewport: CreateTexture failed -- viewport disabled")
        Return
    EndIf
    TextureBlend VPRT, 0

    VPCam = CreateCamera()
    PositionEntity VPCam, 0, VP_SCENE_Y_OFFSET# + 100, -VP_DEFAULT_CAM_DIST#
    PointEntity VPCam, 0
    CameraClsColor VPCam, 16, 16, 22
    CameraRange    VPCam, 1.0, 10000.0
    ; Constrain the camera's viewport to the render-target size so
    ; CameraPick(cam, x, y) treats (x, y) as 0..VP_RT_SIZE coords
    ; (which lets us pass mouse coords RELATIVE to the widget rect).
    CameraViewport VPCam, 0, 0, VP_RT_SIZE, VP_RT_SIZE
    HideEntity     VPCam

    VPLight = CreateLight(1)
    PositionEntity VPLight, 0, VP_SCENE_Y_OFFSET# + 500, -200
    RotateEntity   VPLight, 60, -45, 0
    LightColor     VPLight, 255, 255, 230

    ; Ground plane -- a large flat cube acting as the zone floor.
    ; CreateCube returns a unit cube; scale to a wide flat slab.
    ; Scale is huge so drag-to-edit works for big zones (cursor
    ; can fall outside a "normal" zone's bbox during a fast drag).
    ; EntityPickMode = 2 (poly pick) so CameraPick can land on the
    ; ground plane during a drag and return its world position.
    VPGround = CreateCube()
    ScaleEntity VPGround, 5000.0, 0.5, 5000.0
    PositionEntity VPGround, 0, VP_SCENE_Y_OFFSET#, 0
    EntityColor VPGround, 24, 24, 32      ; near-black stone
    EntityPickMode VPGround, 2            ; polygon pick (for ground drag-land)

    ; Orbit/look pivot -- a hidden point the camera both orbits around and
    ; points at. Repositioned to the scene centre every frame (see the camera
    ; block) so orbit is pure rotation and zoom is pure dolly. Previously the
    ; camera orbited the scene centre but PointEntity'd VPGround at the origin,
    ; so the content drifted in/out (orbit looked like zoom; zoom like rotate).
    VPPivot = CreatePivot()
    PositionEntity VPPivot, 0, VP_SCENE_Y_OFFSET#, 0
    HideEntity VPPivot

    ; Sky-entity shim for world mode: LoadAreaData unconditionally calls
    ; EntityTexture / EntityAlpha on the SkyEN / CloudEN / StarsEN globals
    ; (declared in AreaLoader.bb) and SetViewDistance scales them. Client
    ; and GUE create those entities in their environment setup; Loom has
    ; none, and entity commands on handle 0 are a crash. Create them as
    ; hidden placeholder spheres so the loader's calls land on real
    ; entities without rendering a sky in the editor viewport.
    If SkyEN = 0 Then SkyEN = CreateSphere(4) : HideEntity SkyEN
    If CloudEN = 0 Then CloudEN = CreateSphere(4) : HideEntity CloudEN
    If StarsEN = 0 Then StarsEN = CreateSphere(4) : HideEntity StarsEN

    VPInitOK = True
    WriteLog(LoomLog, "ZoneViewport: initialized (RT=" + VP_RT_SIZE + "x" + VP_RT_SIZE + ")")
End Function


; =============================================================================
; Loom_UnloadWorld -- tear down a loaded world: free the loader's entities
; (terrain / scenery / water / colboxes / emitters / sound zones) and undo
; the loader's camera + ambient side effects. Safe to call when nothing is
; loaded. Does NOT touch mode flags or markers -- callers own those.
; =============================================================================
Function Loom_UnloadWorld()
    If VPWorldLoaded = False Then Return
    ; Scenery edit state is world-scoped and lives only in memory until saved.
    ; UnloadArea frees the scenery entities below, so any pending edits are
    ; lost -- warn once, then clear all scenery edit state.
    If SceneryDirty = True
        Toast_Show("Unsaved scenery changes discarded (use 'save scenery' before leaving world view)", "warning")
        WriteLog(LoomLog, "ZoneViewport: discarded unsaved scenery edits on world unload")
    EndIf
    SceneryDirty   = False
    ScnAddMode     = False
    ScnSelectedH   = 0
    ScnDragging    = False
    ScnDragEN      = 0
    ScnDragH       = 0
    ScnDragChanged = False
    UnloadArea()
    VPWorldLoaded = False
    ; LoadAreaData set the camera's range/fog/cls colors (SetViewDistance +
    ; CameraFogColor/CameraClsColor) and the global AmbientLight from the
    ; zone's environment block. Restore the viewport's boot values; ambient
    ; goes back to the Blitz default so MeshPreview lighting is unaffected.
    If VPCam <> 0
        CameraRange    VPCam, 1.0, 10000.0
        CameraClsColor VPCam, 16, 16, 22
    EndIf
    AmbientLight 127, 127, 127
    WriteLog(LoomLog, "ZoneViewport: world unloaded")
End Function


; =============================================================================
; Loom_SetWorldMode -- toggle between schematic and world rendering for the
; focused zone. Loading the world soft-fails (toast + stay schematic) when
; the zone has no visual data file (Data\Areas\<name>.dat) -- many gameplay
; zones don't. On success the whole viewport drops its Y offset to 0 and
; the markers reload at real coordinates over the loaded geometry.
; =============================================================================
Function Loom_SetWorldMode(zoneHandle, enable)
    Local Ar.Area = Object.Area(zoneHandle)
    If Ar = Null Then Return

    If enable = True
        If VPWorldLoaded = True Then Loom_UnloadWorld()
        ; Pre-check the visual data file rather than letting LoadAreaData's
        ; missing-file Return False handle it, so the common Loom case --
        ; toggling world view on a gameplay-only zone -- gets the specific
        ; "no visual zone data" toast instead of a generic load failure.
        ; (Originally this also dodged a lock-handle leak in the loader's
        ; early return -- review finding on #551 -- but AreaLoader now
        ; locks only after the ReadFile check, so that's no longer a factor.)
        If FileType("Data\Areas\" + Ar\Name$ + ".dat") <> 1
            Toast_Show("No visual zone data (Data\Areas\" + Ar\Name$ + ".dat)", "warning")
            WriteLog(LoomLog, "ZoneViewport: no visual .dat for " + Ar\Name$ + " -- staying schematic")
            VPWorldMode = False
            Return
        EndIf
        If LoadAreaData(Ar\Name$, VPCam, True, False) = False
            Toast_Show("World load failed for " + Ar\Name$, "warning")
            WriteLog(LoomLog, "ZoneViewport: world load failed for " + Ar\Name$ + " -- staying schematic")
            VPWorldMode = False
            Return
        EndIf
        VPWorldLoaded = True
        VPWorldMode = True
        VPSceneYOff# = 0.0
        If VPGround <> 0 Then HideEntity VPGround
        ; Make every loaded scenery instance editor-pickable (pickmode 2),
        ; including collision-0 scenery LoadAreaData left unpickable. pickmode
        ; is runtime-only -- not serialized -- so this changes no on-disk data.
        Loom_MakeSceneryPickable()
        Loom_LoadZoneMarkers(Ar)
        VPDirty = True
        Toast_Show("World view: " + Ar\Name$, "success")
        WriteLog(LoomLog, "ZoneViewport: world loaded for " + Ar\Name$)
    Else
        Loom_UnloadWorld()
        VPWorldMode = False
        VPSceneYOff# = VP_SCENE_Y_OFFSET#
        If VPGround <> 0 Then ShowEntity VPGround
        Loom_LoadZoneMarkers(Ar)
        VPDirty = True
        WriteLog(LoomLog, "ZoneViewport: back to schematic for " + Ar\Name$)
    EndIf
End Function


; =============================================================================
; Loom_FreeZoneMarkers -- free every per-sub-entity marker entity. Called
; on zone change and at shutdown. Uses a marker-only collection via the
; ZoneViewportMarker type so we don't accidentally free the camera/ground/
; light.
; =============================================================================
Type ZoneViewportMarker
    Field EN
    Field Kind$        ; "portal" / "spawn" / "trigger" / "waypoint" / "" for axis/line decorations
    Field IndexN%      ; sub-entity slot index inside the zone (0..N-1 per kind)
    Field BaseScale#   ; uniform scale applied at marker creation; used by
                       ; highlight system to restore size when un-highlighted
    Field VisualLift#  ; render-only Y offset above the semantic Area coordinate
End Type

Function Loom_FreeZoneMarkers()
    Local m.ZoneViewportMarker
    For m = Each ZoneViewportMarker
        If m\EN <> 0 Then FreeEntity m\EN
    Next
    For m = Each ZoneViewportMarker
        Delete m
    Next
End Function


; =============================================================================
; Loom_MakeLine -- emit a thin scaled cube positioned + oriented as the
; segment from (x1, y1, z1) to (x2, y2, z2). Blitz3D has no native 3D
; line primitive; this is the cheap stand-in.
;
; Uses the trig form: midpoint + length + atan2(dx, dz) for yaw, then
; tilt for pitch via atan2(dy, horiz_len). Z axis is the cube's
; "length" direction; we scale Z to the segment length and X/Y to
; VP_LINE_THICKNESS.
; =============================================================================
Function Loom_MakeLine(x1#, y1#, z1#, x2#, y2#, z2#, r, g, b)
    If VPCountLines >= VP_MAX_LINES Then Return

    Local dx# = x2# - x1#
    Local dy# = y2# - y1#
    Local dz# = z2# - z1#
    Local len# = Sqr(dx# * dx# + dy# * dy# + dz# * dz#)
    If len# < 0.01 Then Return

    Local en = CreateCube()
    ScaleEntity en, VP_LINE_THICKNESS#, VP_LINE_THICKNESS#, len# / 2.0
    EntityColor en, r, g, b
    PositionEntity en, (x1# + x2#) / 2.0, (y1# + y2#) / 2.0, (z1# + z2#) / 2.0

    ; Orient: yaw around Y (atan2 of horiz), pitch around X (atan2 of dy/horiz)
    Local horiz# = Sqr(dx# * dx# + dz# * dz#)
    Local yaw#   = ATan2(dx#, dz#)
    Local pitch# = -ATan2(dy#, horiz#)
    RotateEntity en, pitch#, yaw#, 0

    Local marker.ZoneViewportMarker = New ZoneViewportMarker
    marker\EN = en
    VPCountLines = VPCountLines + 1
End Function


; =============================================================================
; Loom_MakeAxisMarkers -- three short colored lines from the scene origin
; along +X (red), +Y (green), +Z (blue). Gives the viewport an obvious
; orientation reference at scene origin.
; =============================================================================
Function Loom_MakeAxisMarkers()
    Local ox# = 0.0
    Local oy# = VPSceneYOff#
    Local oz# = 0.0
    Loom_MakeLine ox#, oy#, oz#, ox# + VP_AXIS_LENGTH#, oy#, oz#, 220, 60, 60
    Loom_MakeLine ox#, oy#, oz#, ox#, oy# + VP_AXIS_LENGTH#, oz#, 60, 220, 60
    Loom_MakeLine ox#, oy#, oz#, ox#, oy#, oz# + VP_AXIS_LENGTH#, 60, 120, 220
End Function


; =============================================================================
; Loom_CommitMarkerCoord -- write the new X/Z (and keep current Y) back
; to the underlying Area field for the dragged sub-entity. Called every
; frame during drag for live preview. ZoneSaved gets flipped to False
; via Composer::markDirtyForKind on release.
; =============================================================================
Function Loom_CommitMarkerCoord(zoneHandle, kind$, idx, newX#, newZ#)
    Local Ar.Area = Object.Area(zoneHandle)
    If Ar = Null Then Return
    If kind$ = "portal"
        If idx >= 0 And idx <= 99
            Ar\PortalX#[idx] = newX#
            Ar\PortalZ#[idx] = newZ#
        EndIf
    Else If kind$ = "trigger"
        If idx >= 0 And idx <= 149
            Ar\TriggerX#[idx] = newX#
            Ar\TriggerZ#[idx] = newZ#
        EndIf
    Else If kind$ = "spawn"
        If idx >= 0 And idx <= 999
            ; Spawn position is the waypoint position, not a direct
            ; spawn coord. Update the referenced waypoint instead.
            Local wpIdx = Ar\SpawnWaypoint[idx]
            If wpIdx >= 0 And wpIdx <= 1999
                Ar\WaypointX#[wpIdx] = newX#
                Ar\WaypointZ#[wpIdx] = newZ#
            EndIf
        EndIf
    EndIf
End Function


; =============================================================================
; Loom_CommitMarkerY -- companion to Loom_CommitMarkerCoord for Y-mode
; drags. Spawn case updates the referenced waypoint's Y (same data
; model rule as Loom_CommitMarkerCoord for X/Z).
; =============================================================================
Function Loom_CommitMarkerY(zoneHandle, kind$, idx, newY#)
    Local Ar.Area = Object.Area(zoneHandle)
    If Ar = Null Then Return
    If kind$ = "portal"
        If idx >= 0 And idx <= 99 Then Ar\PortalY#[idx] = newY#
    Else If kind$ = "trigger"
        If idx >= 0 And idx <= 149 Then Ar\TriggerY#[idx] = newY#
    Else If kind$ = "spawn"
        If idx >= 0 And idx <= 999
            Local wpIdx = Ar\SpawnWaypoint[idx]
            If wpIdx >= 0 And wpIdx <= 1999 Then Ar\WaypointY#[wpIdx] = newY#
        EndIf
    EndIf
End Function


; =============================================================================
; Loom_DeleteMarkerAtClick -- Ctrl+LMB on a marker deletes its
; sub-entity from the Area. Pick the marker via CameraPick; if hit,
; clear the defining field (PortalName$ / TriggerScript$ / SpawnActor)
; following the same pattern as the composer's per-sub-entity delete
; button (iter 30). Reload markers, mark dirty, toast.
;
; Waypoint deletion is currently a no-op: waypoints are shared
; chain-link state for spawns + AI patrols, and deleting one
; orphans every reference to it. Designers should clear waypoints
; via the composer where the deletion semantics are more explicit.
; =============================================================================
Function Loom_DeleteMarkerAtClick(zoneHandle, localX, localY)
    If VPInitOK = False Then Return
    ; Editable in both modes now (ADR-004 world-mode editing follow-up). The
    ; pick here is marker-vs-marker, so it works identically whether the floor
    ; plane (schematic) or the real terrain (world) sits behind the markers.
    ; Previously world mode was read-only and short-circuited here (#551).
    Local Ar.Area = Object.Area(zoneHandle)
    If Ar = Null Then Return

    CameraPick VPCam, localX, localY
    Local picked = PickedEntity()
    If picked = 0 Or picked = VPGround Then Return

    Local m.ZoneViewportMarker
    Local found = False
    Local kind$ = ""
    Local idx = -1
    For m = Each ZoneViewportMarker
        If m\EN = picked
            kind$ = m\Kind
            idx = m\IndexN
            found = True
            Exit
        EndIf
    Next

    If found = False Then Return

    If kind$ = "portal"
        If idx >= 0 And idx <= 99
            Ar\PortalName$[idx]     = ""
            Ar\PortalLinkArea$[idx] = ""
            Ar\PortalLinkName$[idx] = ""
        EndIf
    Else If kind$ = "trigger"
        If idx >= 0 And idx <= 149
            Ar\TriggerScript$[idx] = ""
            Ar\TriggerMethod$[idx] = ""
        EndIf
    Else If kind$ = "spawn"
        If idx >= 0 And idx <= 999 Then Ar\SpawnActor[idx] = 0
    Else If kind$ = "waypoint"
        ; Waypoint delete is intentionally not supported here; see
        ; comment above. Surface a warning so the user knows the
        ; click was registered but ignored.
        Toast_Show("Waypoint delete not supported via viewport (use composer)", "warning")
        Return
    EndIf

    Loom_LoadZoneMarkers(Ar)
    VPDirty = True
    If LoomComposer <> Null Then Composer::markDirtyForKind(LoomComposer, "zone")
    Toast_Show("Deleted " + kind$ + " " + Str(idx), "danger")
    WriteLog(LoomLog, "ZoneViewport: ctrl+click deleted " + kind$ + " " + Str(idx))
End Function


; =============================================================================
; Loom_PickGround -- resolve mouse-local widget coords (0..VP_RT_SIZE) to a
; world-space placement point, unified across both viewport modes:
;
;   SCHEMATIC  the pick must land on VPGround (the flat editor floor at
;              y=VP_SCENE_Y_OFFSET). Anything else (a marker) is a miss.
;   WORLD      the pick must land on the zone's REAL geometry loaded by
;              LoadAreaData -- terrain (EntityPickMode 2), scenery
;              (EntityPickMode 1/2/3) or collision boxes -- so a designer
;              places / drags entities directly onto the rendered ground.
;              VPGround is hidden in world mode (so it can't be picked) and
;              is rejected defensively here regardless.
;
; In BOTH modes every sub-entity marker's pick mode is temporarily disabled
; so the ray passes THROUGH markers to the ground beneath (the same trick the
; schematic add handlers used inline). On a hit the world point is stored in
; VPPickX#/Y#/Z# and the function returns True; on a miss it returns False and
; leaves the globals untouched. This is ADR-004's "editing against real
; terrain in world mode (pick-target rework)" follow-up.
; =============================================================================
Function Loom_PickGround(localX, localY)
    If VPInitOK = False Then Return False

    ; Disable marker picking so the ray reaches the ground / terrain, not a
    ; marker cube sitting in front of it. Waypoint markers count too (Kind
    ; is non-empty); line + water decorations already have pick mode 0.
    Local m.ZoneViewportMarker
    For m = Each ZoneViewportMarker
        If m\Kind <> "" Then EntityPickMode m\EN, 0
    Next
    CameraPick VPCam, localX, localY
    Local hit = PickedEntity()
    ; Restore marker picking (all markers are created with box-pick mode 1).
    For m = Each ZoneViewportMarker
        If m\Kind <> "" Then EntityPickMode m\EN, 1
    Next

    If hit = 0 Then Return False
    If VPWorldMode = False
        ; Schematic: only the editor floor is a valid placement surface.
        If hit <> VPGround Then Return False
    Else
        ; World: any loaded geometry is valid; never our hidden floor plane.
        If hit = VPGround Then Return False
    EndIf

    VPPickX# = PickedX#()
    VPPickY# = PickedY#()
    VPPickZ# = PickedZ#()
    Return True
End Function


; =============================================================================
; Loom_PlaceY# -- the scene-relative Y to STORE for a newly-placed sub-entity.
; Schematic mode plants everything on the flat floor (Y = 0). World mode uses
; the terrain height the pick landed on: VPPickY# is a real-world coordinate
; and VPSceneYOff# is 0 in world mode, so VPPickY# - VPSceneYOff# is the
; stored (scene-relative) height that lands the entity on the ground surface.
; =============================================================================
Function Loom_PlaceY#()
    If VPWorldMode = False Then Return 0.0
    Return VPPickY# - VPSceneYOff#
End Function


; =============================================================================
; Loom_AddPortalAtClick -- Shift+click on the ground creates a new portal at
; the picked XZ (schematic: flat floor; world: real terrain). Fails silently
; if no portal slot is available or the click missed the ground.
;
; After adding: reload markers so the new portal gets a visible cube,
; mark the zone dirty, fire toast. Composer's zoneAnchorPortal will
; refresh on the next frame's renderZone.
; =============================================================================
Function Loom_AddPortalAtClick(zoneHandle, localX, localY)
    If VPInitOK = False Then Return
    Local Ar.Area = Object.Area(zoneHandle)
    If Ar = Null Then Return

    ; Pick a placement point on the ground (schematic floor or, in world
    ; mode, the real terrain). Miss = no-op.
    If Loom_PickGround(localX, localY) = False Then Return
    Local nx# = VPPickX#
    Local nz# = VPPickZ#

    ; Find first empty portal slot
    Local i
    Local slot = -1
    For i = 0 To 99
        If Ar\PortalName$[i] = ""
            slot = i
            Exit
        EndIf
    Next
    If slot < 0
        Toast_Show("No empty portal slots in this zone", "warning")
        Return
    EndIf

    ; Seed defaults (mirrors Composer::zoneAddPortal but with the
    ; clicked position instead of (0, 0)). In world mode the Y follows
    ; the terrain height the click landed on (Loom_PlaceY#).
    Ar\PortalName$[slot]     = "New portal " + Str(slot)
    Ar\PortalLinkArea$[slot] = Ar\Name$
    Ar\PortalLinkName$[slot] = ""
    Ar\PortalX#[slot]        = nx#
    Ar\PortalY#[slot]        = Loom_PlaceY#()
    Ar\PortalZ#[slot]        = nz#
    Ar\PortalSize#[slot]     = 5.0
    Ar\PortalYaw#[slot]      = 0.0

    ; Refresh viewport markers + composer state
    Loom_LoadZoneMarkers(Ar)
    VPDirty = True
    If LoomComposer <> Null Then Composer::markDirtyForKind(LoomComposer, "zone")
    Toast_Show("Added portal " + Str(slot) + " at (" + Int(nx#) + ", " + Int(nz#) + ")", "success")
    WriteLog(LoomLog, "ZoneViewport: shift+click added portal " + Str(slot) + " at " + nx# + ", " + nz#)
End Function


; =============================================================================
; Loom_AddTriggerAtClick -- shift+RMB on empty ground creates a new
; trigger at the clicked XZ. Symmetric with Loom_AddPortalAtClick.
; Trigger needs no portal-style target reference; just XYZ + size +
; Script/Method.
; =============================================================================
Function Loom_AddTriggerAtClick(zoneHandle, localX, localY)
    If VPInitOK = False Then Return
    Local Ar.Area = Object.Area(zoneHandle)
    If Ar = Null Then Return

    ; Pick a placement point on the ground (schematic floor or real terrain).
    If Loom_PickGround(localX, localY) = False Then Return
    Local nx# = VPPickX#
    Local nz# = VPPickZ#

    Local i
    Local slot = -1
    For i = 0 To 149
        If Ar\TriggerScript$[i] = ""
            slot = i
            Exit
        EndIf
    Next
    If slot < 0
        Toast_Show("No empty trigger slots in this zone", "warning")
        Return
    EndIf

    Ar\TriggerScript$[slot] = "New trigger"
    Ar\TriggerMethod$[slot] = ""
    Ar\TriggerX#[slot]      = nx#
    Ar\TriggerY#[slot]      = Loom_PlaceY#()
    Ar\TriggerZ#[slot]      = nz#
    Ar\TriggerSize#[slot]   = 5.0

    Loom_LoadZoneMarkers(Ar)
    VPDirty = True
    If LoomComposer <> Null Then Composer::markDirtyForKind(LoomComposer, "zone")
    Toast_Show("Added trigger " + Str(slot) + " at (" + Int(nx#) + ", " + Int(nz#) + ")", "success")
    WriteLog(LoomLog, "ZoneViewport: shift+RMB added trigger " + Str(slot) + " at " + nx# + ", " + nz#)
End Function


; =============================================================================
; Loom_AddSpawnAtClick -- shift+MMB on empty ground creates a NEW
; waypoint at the click + a NEW spawn referencing that waypoint.
; Default spawn actor = first defined actor in the project. Spawns
; need a waypoint (their world position is the waypoint's position),
; so a fresh spawn that doesn't reuse an existing waypoint creates
; both side-by-side.
; =============================================================================
Function Loom_AddSpawnAtClick(zoneHandle, localX, localY)
    If VPInitOK = False Then Return
    Local Ar.Area = Object.Area(zoneHandle)
    If Ar = Null Then Return

    ; Pick a placement point on the ground (schematic floor or real terrain).
    If Loom_PickGround(localX, localY) = False Then Return
    Local nx# = VPPickX#
    Local nz# = VPPickZ#
    Local ny# = Loom_PlaceY#()

    ; Find first defined actor for the spawn ref. If no actors exist,
    ; bail with a toast -- a spawn without a valid actor is useless.
    Local defaultActor = 0
    Local ai
    For ai = 1 To 65534
        If ActorList(ai) <> Null
            defaultActor = ai
            Exit
        EndIf
    Next
    If defaultActor = 0
        Toast_Show("No actors defined -- create one before adding a spawn", "warning")
        Return
    EndIf

    ; Find first empty waypoint slot. Waypoints share the X/Y/Z = 0
    ; "empty" convention with the rest of the zone editor.
    Local wpSlot = -1
    Local wi
    For wi = 0 To 1999
        If Ar\WaypointX#[wi] = 0.0 And Ar\WaypointZ#[wi] = 0.0
            wpSlot = wi
            Exit
        EndIf
    Next
    If wpSlot < 0
        Toast_Show("No empty waypoint slots in this zone", "warning")
        Return
    EndIf

    ; Find first empty spawn slot (SpawnActor = 0 = empty).
    Local spSlot = -1
    Local si
    For si = 0 To 999
        If Ar\SpawnActor[si] = 0
            spSlot = si
            Exit
        EndIf
    Next
    If spSlot < 0
        Toast_Show("No empty spawn slots in this zone", "warning")
        Return
    EndIf

    ; Seed waypoint (spawn's world position IS the waypoint position; in
    ; world mode ny# is the terrain height the click landed on).
    Ar\WaypointX#[wpSlot] = nx#
    Ar\WaypointY#[wpSlot] = ny#
    Ar\WaypointZ#[wpSlot] = nz#
    Ar\NextWaypointA[wpSlot] = -1
    Ar\NextWaypointB[wpSlot] = -1
    Ar\PrevWaypoint[wpSlot]  = -1
    Ar\WaypointPause[wpSlot] = 0

    ; Seed spawn
    Ar\SpawnActor[spSlot]        = defaultActor
    Ar\SpawnWaypoint[spSlot]     = wpSlot
    Ar\SpawnSize#[spSlot]        = 5.0
    Ar\SpawnRange#[spSlot]       = 100.0
    Ar\SpawnFrequency[spSlot]    = 30000
    Ar\SpawnMax[spSlot]          = 1
    Ar\SpawnScript$[spSlot]      = ""
    Ar\SpawnActorScript$[spSlot] = ""
    Ar\SpawnDeathScript$[spSlot] = ""

    Loom_LoadZoneMarkers(Ar)
    VPDirty = True
    If LoomComposer <> Null Then Composer::markDirtyForKind(LoomComposer, "zone")
    Toast_Show("Added spawn " + Str(spSlot) + " (waypoint " + Str(wpSlot) + ") at (" + Int(nx#) + ", " + Int(nz#) + ")", "success")
    WriteLog(LoomLog, "ZoneViewport: shift+MMB added spawn " + Str(spSlot) + " + waypoint " + Str(wpSlot) + " at " + nx# + ", " + nz#)
End Function


; =============================================================================
; Loom_PickZoneMarker -- cast a ray from the camera through the requested
; local widget coords (0..VP_RT_SIZE), find which marker (if any) was
; hit. On hit, fire a toast naming the sub-entity. Iter 45 will turn
; this into a composer scroll-to-section dispatch.
;
; Note: line cubes (Loom_MakeLine emits these for axes + waypoint
; connections) have EntityPickMode = 0 (the default) so they don't
; intercept the pick ray. Only the marker cubes (portal/spawn/trigger/
; waypoint) are pickable.
; =============================================================================
Function Loom_PickZoneMarker(localX, localY)
    If VPInitOK = False Then Return

    ; CameraPick uses the camera's viewport (set at init to
    ; 0..VP_RT_SIZE), so we pass local widget coords directly.
    CameraPick VPCam, localX, localY
    Local picked = PickedEntity()
    If picked = 0 Then Return

    ; Find which marker has this entity. Linear walk is fine since
    ; total markers stay bounded (typically <50 per zone, capped at
    ; thousands worst-case).
    Local m.ZoneViewportMarker
    For m = Each ZoneViewportMarker
        If m\EN = picked
            If m\Kind <> ""
                ; Dispatch to composer scroll-to-section if available
                ; (waypoint clicks don't have a section to scroll to;
                ; they're rendered inline with other waypoints rather
                ; than as per-slot sub-sections, so we still toast).
                If LoomComposer <> Null And (m\Kind = "portal" Or m\Kind = "trigger" Or m\Kind = "spawn")
                    Local ok = Composer::scrollToZoneSubEntity(LoomComposer, m\Kind, m\IndexN)
                    If ok = True
                        Toast_Show("Jumped to " + m\Kind + " " + Str(m\IndexN), "info")
                    Else
                        Toast_Show("Picked " + m\Kind + " " + Str(m\IndexN) + " (anchor not ready)", "warning")
                    EndIf
                Else
                    Toast_Show("Picked " + m\Kind + " " + Str(m\IndexN), "info")
                EndIf
                WriteLog(LoomLog, "ZoneViewport: picked " + m\Kind + " " + Str(m\IndexN))
            EndIf
            Return
        EndIf
    Next
End Function


; =============================================================================
; Loom_LoadZoneMarkers -- walk the Area's portal/spawn/trigger/waypoint
; arrays, instantiate a colored cube for each defined entry. Also computes
; the scene bbox so the camera can auto-fit.
; =============================================================================
; =============================================================================
; Loom_LoadSpawnActorMesh -- load the spawned actor's base mesh as an OWNED
; entity (GetMesh Duplicate=True) scaled by the actor's Scale, for placing
; at a spawn point so the designer sees the actual creature. Returns 0 if
; the actor or its mesh doesn't resolve -- the caller falls back to a marker
; cube. The mesh origin is at the feet for character meshes, so the caller
; positions it directly on the ground (no half-size lift like the cube).
; =============================================================================
Function Loom_LoadSpawnActorMesh(actorID)
    If actorID < 0 Or actorID > 65535 Then Return 0
    Local A.Actor = ActorList(actorID)
    If A = Null Then Return 0
    Local meshID = A\MeshIDs[0]      ; base (male) body mesh
    If meshID <= 0 Then Return 0
    Local ent = GetMesh(meshID, True)
    If ent = 0 Then Return 0
    Local sc# = A\Scale#
    If sc# <= 0.0 Then sc# = 1.0
    ScaleEntity ent, sc#, sc#, sc#
    Return ent
End Function


Function Loom_LoadZoneMarkers(Ar.Area)
    Loom_FreeZoneMarkers()
    VPCountPortals  = 0
    VPCountSpawns   = 0
    VPCountTriggers = 0
    VPCountWaypoints = 0
    VPCountLines    = 0
    If Ar = Null Then Return

    ; Origin axis markers go first (cheap, always 3 lines).
    Loom_MakeAxisMarkers()

    Local minX# = 1000000.0
    Local minZ# = 1000000.0
    Local maxX# = -1000000.0
    Local maxZ# = -1000000.0
    Local found = False

    ; Portals -- brass cubes
    Local i
    For i = 0 To 99
        If Ar\PortalName$[i] <> ""
            Local pEn = CreateCube()
            ScaleEntity pEn, VP_MARKER_SIZE#, VP_MARKER_SIZE#, VP_MARKER_SIZE#
            PositionEntity pEn, Ar\PortalX#[i], VPSceneYOff# + Ar\PortalY#[i] + VP_MARKER_SIZE#, Ar\PortalZ#[i]
            EntityColor pEn, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B
            EntityPickMode pEn, 1     ; box-pick eligible
            Local pm.ZoneViewportMarker = New ZoneViewportMarker
            pm\EN = pEn
            pm\Kind = "portal"
            pm\IndexN = i
            pm\BaseScale = VP_MARKER_SIZE#
            pm\VisualLift# = VP_MARKER_SIZE#
            VPCountPortals = VPCountPortals + 1
            If Ar\PortalX#[i] < minX# Then minX# = Ar\PortalX#[i]
            If Ar\PortalX#[i] > maxX# Then maxX# = Ar\PortalX#[i]
            If Ar\PortalZ#[i] < minZ# Then minZ# = Ar\PortalZ#[i]
            If Ar\PortalZ#[i] > maxZ# Then maxZ# = Ar\PortalZ#[i]
            found = True
        EndIf
    Next

    ; Spawns -- render the spawned actor's actual mesh at the spawn point so
    ; the designer sees the creatures populating the zone. Falls back to an
    ; arcane cube when the actor / base mesh doesn't resolve, or once the
    ; per-zone mesh cap is hit (bounds load cost on huge zones).
    Local spawnMeshCount = 0
    For i = 0 To 999
        If Ar\SpawnActor[i] > 0
            Local waypointIdx = Ar\SpawnWaypoint[i]
            If waypointIdx >= 0 And waypointIdx <= 1999
                Local sEn = 0
                Local spawnScale# = VP_MARKER_SIZE#
                Local spawnVisualLift# = 0.0
                If spawnMeshCount < VP_MAX_SPAWN_MESHES
                    sEn = Loom_LoadSpawnActorMesh(Ar\SpawnActor[i])
                EndIf
                If sEn <> 0
                    ; Actor mesh: origin at the feet -> sit it on the ground.
                    spawnMeshCount = spawnMeshCount + 1
                    PositionEntity sEn, Ar\WaypointX#[waypointIdx], VPSceneYOff# + Ar\WaypointY#[waypointIdx], Ar\WaypointZ#[waypointIdx]
                    Local A2.Actor = ActorList(Ar\SpawnActor[i])
                    If A2 <> Null And A2\Scale# > 0.0 Then spawnScale# = A2\Scale#
                Else
                    ; Fallback marker cube (centered -> lift by half-size).
                    sEn = CreateCube()
                    ScaleEntity sEn, VP_MARKER_SIZE#, VP_MARKER_SIZE#, VP_MARKER_SIZE#
                    PositionEntity sEn, Ar\WaypointX#[waypointIdx], VPSceneYOff# + Ar\WaypointY#[waypointIdx] + VP_MARKER_SIZE#, Ar\WaypointZ#[waypointIdx]
                    EntityColor sEn, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B
                    spawnVisualLift# = VP_MARKER_SIZE#
                EndIf
                EntityPickMode sEn, 1
                Local sm.ZoneViewportMarker = New ZoneViewportMarker
                sm\EN = sEn
                sm\Kind = "spawn"
                sm\IndexN = i
                sm\BaseScale = spawnScale#
                sm\VisualLift# = spawnVisualLift#
                VPCountSpawns = VPCountSpawns + 1
                If Ar\WaypointX#[waypointIdx] < minX# Then minX# = Ar\WaypointX#[waypointIdx]
                If Ar\WaypointX#[waypointIdx] > maxX# Then maxX# = Ar\WaypointX#[waypointIdx]
                If Ar\WaypointZ#[waypointIdx] < minZ# Then minZ# = Ar\WaypointZ#[waypointIdx]
                If Ar\WaypointZ#[waypointIdx] > maxZ# Then maxZ# = Ar\WaypointZ#[waypointIdx]
                found = True
            EndIf
        EndIf
    Next

    ; Triggers -- warning cubes
    For i = 0 To 149
        If Ar\TriggerScript$[i] <> ""
            Local tEn = CreateCube()
            ScaleEntity tEn, VP_MARKER_SIZE#, VP_MARKER_SIZE#, VP_MARKER_SIZE#
            PositionEntity tEn, Ar\TriggerX#[i], VPSceneYOff# + Ar\TriggerY#[i] + VP_MARKER_SIZE#, Ar\TriggerZ#[i]
            EntityColor tEn, LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B
            EntityPickMode tEn, 1
            Local tm.ZoneViewportMarker = New ZoneViewportMarker
            tm\EN = tEn
            tm\Kind = "trigger"
            tm\IndexN = i
            tm\BaseScale = VP_MARKER_SIZE#
            tm\VisualLift# = VP_MARKER_SIZE#
            VPCountTriggers = VPCountTriggers + 1
            If Ar\TriggerX#[i] < minX# Then minX# = Ar\TriggerX#[i]
            If Ar\TriggerX#[i] > maxX# Then maxX# = Ar\TriggerX#[i]
            If Ar\TriggerZ#[i] < minZ# Then minZ# = Ar\TriggerZ#[i]
            If Ar\TriggerZ#[i] > maxZ# Then maxZ# = Ar\TriggerZ#[i]
            found = True
        EndIf
    Next

    ; Waypoints -- small stone cubes (only render defined ones)
    For i = 0 To 1999
        If Ar\WaypointX#[i] <> 0.0 Or Ar\WaypointZ#[i] <> 0.0
            Local wEn = CreateCube()
            ScaleEntity wEn, VP_WAYPOINT_SIZE#, VP_WAYPOINT_SIZE#, VP_WAYPOINT_SIZE#
            PositionEntity wEn, Ar\WaypointX#[i], VPSceneYOff# + Ar\WaypointY#[i] + VP_WAYPOINT_SIZE#, Ar\WaypointZ#[i]
            EntityColor wEn, 140, 140, 150
            EntityPickMode wEn, 1
            Local wm.ZoneViewportMarker = New ZoneViewportMarker
            wm\EN = wEn
            wm\Kind = "waypoint"
            wm\IndexN = i
            wm\BaseScale = VP_WAYPOINT_SIZE#
            VPCountWaypoints = VPCountWaypoints + 1
            If Ar\WaypointX#[i] < minX# Then minX# = Ar\WaypointX#[i]
            If Ar\WaypointX#[i] > maxX# Then maxX# = Ar\WaypointX#[i]
            If Ar\WaypointZ#[i] < minZ# Then minZ# = Ar\WaypointZ#[i]
            If Ar\WaypointZ#[i] > maxZ# Then maxZ# = Ar\WaypointZ#[i]
            found = True
        EndIf
    Next

    ; Waypoint connection lines -- emit a thin line for each NextA/NextB
    ; pointer from a defined waypoint to a defined target. Capped via
    ; VP_MAX_LINES to keep entity count bounded on huge zones.
    For i = 0 To 1999
        If Ar\WaypointX#[i] <> 0.0 Or Ar\WaypointZ#[i] <> 0.0
            Local na = Ar\NextWaypointA[i]
            If na >= 0 And na <= 1999
                If Ar\WaypointX#[na] <> 0.0 Or Ar\WaypointZ#[na] <> 0.0
                    Loom_MakeLine Ar\WaypointX#[i], VPSceneYOff# + Ar\WaypointY#[i] + VP_WAYPOINT_SIZE#, Ar\WaypointZ#[i], Ar\WaypointX#[na], VPSceneYOff# + Ar\WaypointY#[na] + VP_WAYPOINT_SIZE#, Ar\WaypointZ#[na], 100, 100, 110
                EndIf
            EndIf
            Local nb = Ar\NextWaypointB[i]
            If nb >= 0 And nb <= 1999
                If Ar\WaypointX#[nb] <> 0.0 Or Ar\WaypointZ#[nb] <> 0.0
                    Loom_MakeLine Ar\WaypointX#[i], VPSceneYOff# + Ar\WaypointY#[i] + VP_WAYPOINT_SIZE#, Ar\WaypointZ#[i], Ar\WaypointX#[nb], VPSceneYOff# + Ar\WaypointY#[nb] + VP_WAYPOINT_SIZE#, Ar\WaypointZ#[nb], 100, 100, 110
                EndIf
            EndIf
        EndIf
    Next

    ; Water rectangles -- walk the per-Area FirstWater linked list
    ; (populated by ServerLoadArea). Render each as a flat blue slab
    ; sized to (Width x small height x Depth) at (X, Y, Z). Damage
    ; type encoded in the color so designers can spot lava (red) vs
    ; acid (green) vs plain water (blue) at a glance.
    Local W.ServerWater = Ar\FirstWater
    While W <> Null
        Local wEn2 = CreateCube()
        ScaleEntity wEn2, W\Width# / 2.0, 1.0, W\Depth# / 2.0
        PositionEntity wEn2, W\X#, VPSceneYOff# + W\Y#, W\Z#
        ; Color by damage: 0 = water (blue), 1+ = damage type tinted
        If W\Damage > 0
            EntityColor wEn2, 200, 70, 70    ; harmful = red tint
        Else
            EntityColor wEn2, 70, 110, 200   ; neutral water = blue
        EndIf
        EntityAlpha wEn2, 0.5                 ; translucent so markers below are visible
        Local wm2.ZoneViewportMarker = New ZoneViewportMarker
        wm2\EN = wEn2
        wm2\Kind = ""                        ; not pickable as a sub-entity
        If W\X# - W\Width# / 2.0 < minX# Then minX# = W\X# - W\Width# / 2.0
        If W\X# + W\Width# / 2.0 > maxX# Then maxX# = W\X# + W\Width# / 2.0
        If W\Z# - W\Depth# / 2.0 < minZ# Then minZ# = W\Z# - W\Depth# / 2.0
        If W\Z# + W\Depth# / 2.0 > maxZ# Then maxZ# = W\Z# + W\Depth# / 2.0
        found = True
        W = W\NextWater
    Wend

    ; Auto-fit camera: center on midpoint of bbox, distance scaled
    ; to the larger of the two extents.
    If found = True
        VPSceneCenterX# = (minX# + maxX#) / 2.0
        VPSceneCenterY# = 0.0
        VPSceneCenterZ# = (minZ# + maxZ#) / 2.0
        Local extentX# = maxX# - minX#
        Local extentZ# = maxZ# - minZ#
        Local extent# = extentX#
        If extentZ# > extent# Then extent# = extentZ#
        If extent# < 100.0 Then extent# = 100.0
        VPDistance# = extent# * 1.5
    Else
        VPSceneCenterX# = 0.0
        VPSceneCenterY# = 0.0
        VPSceneCenterZ# = 0.0
        VPDistance# = VP_DEFAULT_CAM_DIST#
    EndIf

    ; Reset orbit so each new zone starts at a comfortable default angle.
    VPYaw# = 0.0
    VPPitch# = 25.0

    ; Capture auto-fit values so the Reset View button can restore them.
    VPInitialCenterX# = VPSceneCenterX#
    VPInitialCenterZ# = VPSceneCenterZ#
    VPInitialDistance# = VPDistance#
End Function


; =============================================================================
; Loom_ResetZoneView -- restore the zone's auto-fit camera. Called by
; the Reset View pill in the viewport overlay.
; =============================================================================
Function Loom_ResetZoneView()
    VPSceneCenterX# = VPInitialCenterX#
    VPSceneCenterZ# = VPInitialCenterZ#
    VPDistance# = VPInitialDistance#
    VPYaw# = 0.0
    VPPitch# = 25.0
    VPDirty = True
End Function


; =============================================================================
; Loom_MakeSceneryPickable -- force EntityPickMode 2 (polygon pick) on every
; loaded Scenery entity so CameraPick can select / move / delete them in the
; editor. pickmode is a RUNTIME property, NOT serialized (SaveArea writes
; GetEntityType, not pickmode), so this is a pure editor affordance -- it does
; not change the on-disk collision type. Called after each world load. Loaded
; scenery whose collision type is 0 would otherwise be unpickable (LoadAreaData
; only sets a pickmode for C_Sphere/Triangle/Box).
; =============================================================================
Function Loom_MakeSceneryPickable()
    Local S.Scenery
    For S = Each Scenery
        If S\EN <> 0 Then EntityPickMode S\EN, 2
    Next
End Function


; =============================================================================
; Loom_SceneryFromEN -- resolve a picked entity handle to its Scenery record,
; or Null if the entity is not a scenery instance (terrain / marker / miss).
; LoadAreaData + GUE both NameEntity(S\EN, Handle(S)), so the entity's name
; round-trips through Object.Scenery. Null-safe per handle-lookup discipline.
; =============================================================================
Function Loom_SceneryFromEN.Scenery(en)
    If en = 0 Then Return Null
    Return Object.Scenery(EntityName$(en))
End Function


; =============================================================================
; Loom_AddSceneryAtClick -- WORLD MODE ONLY. Drop a new scenery instance of the
; selected brush mesh at the terrain point under the cursor. Initializes EVERY
; field SaveArea serializes (no uninitialized field -- #618 review check), and
; writes the ENGINE mesh id, not a catalog index (#592). Falls back to a point
; in front of the camera if the pick misses (indoor zones without terrain).
; =============================================================================
Function Loom_AddSceneryAtClick(zoneHandle, localX, localY)
    If VPInitOK = False Then Return
    If VPWorldMode = False Or VPWorldLoaded = False Then Return
    If ScnBrushMeshID <= 0
        Toast_Show("Pick a mesh from the list first", "warning")
        Return
    EndIf

    ; Placement point: reuse the shared ground/terrain pick (world mode lands
    ; on the real loaded geometry; markers are temporarily disabled so the ray
    ; passes through them). A miss is a no-op -- never drop floating scenery.
    If Loom_PickGround(localX, localY) = False
        Toast_Show("Aim at the terrain to place scenery", "warning")
        Return
    EndIf
    Local px# = VPPickX#
    Local py# = VPPickY#
    Local pz# = VPPickZ#

    ; Media deletion rebuilds the catalog, so a previously selected brush ID
    ; can outlive its MeshEntry. Validate the catalog before loading or
    ; allocating anything: BlitzForge And is eager and cannot guard mEnt\Scale.
    Local mEnt.MeshEntry = Meshes_GetByID(ScnBrushMeshID)
    If mEnt = Null
        ScnBrushMeshID = 0
        ScnBrushName$ = ""
        Toast_Show("Selected scenery mesh is no longer available; pick another mesh", "warning")
        Return
    EndIf

    Local en = GetMesh(ScnBrushMeshID, False)
    If en = 0
        Toast_Show("Could not load mesh " + Str(ScnBrushMeshID), "warning")
        Return
    EndIf

    ; Brush scale mirrors GUE's place-from-browser: mesh's catalog scale * 0.05.
    Local sc# = 1.0
    If mEnt\Scale# > 0.0 Then sc# = mEnt\Scale# * 0.05
    If sc# <= 0.0 Then sc# = 1.0

    ; --- Create + fully initialize every serialized field ---
    Local S.Scenery = New Scenery
    S\MeshID        = ScnBrushMeshID     ; engine mesh id (SaveArea WriteShort)
    S\EN            = en
    S\ScaleX#       = sc#
    S\ScaleY#       = sc#
    S\ScaleZ#       = sc#
    S\AnimationMode = 0
    S\SceneryID     = 0
    S\TextureID     = 65535              ; 65535 = no retexture (GUE default)
    S\CatchRain     = 0
    S\Lightmap$     = ""
    S\RCTE$         = ""
    S\CastShadow    = 0
    S\ReceiveShadow = 0
    S\RenderRange   = 0
    NameEntity S\EN, Handle(S)
    ; Collision type 0 (parity with GUE's implicit default -> GetEntityType
    ; writes 0). pickmode 2 makes it editor-selectable without persisting a
    ; collision (pickmode isn't serialized).
    EntityType     S\EN, 0
    EntityPickMode S\EN, 2
    PositionEntity S\EN, px#, py#, pz#
    RotateEntity   S\EN, 0.0, 0.0, 0.0   ; Pitch/Yaw/Roll all 0 (serialized)
    ScaleEntity    S\EN, S\ScaleX#, S\ScaleY#, S\ScaleZ#

    ScnSelectedH = Handle(S)
    SceneryDirty = True
    VPDirty = True
    Toast_Show("Added scenery " + ScnBrushName$ + " at (" + Int(px#) + ", " + Int(pz#) + ")", "success")
    WriteLog(LoomLog, "ZoneViewport: added scenery mesh " + Str(ScnBrushMeshID) + " at " + px# + ", " + py# + ", " + pz#)
End Function


; =============================================================================
; Loom_DeleteSceneryAtClick -- WORLD MODE ONLY. Ctrl+LMB on a scenery instance
; frees its entity + Deletes the record. Single-target delete (find-then-delete-
; then-return), so no iterator-during-iteration hazard.
; =============================================================================
Function Loom_DeleteSceneryAtClick(zoneHandle, localX, localY)
    If VPInitOK = False Then Return
    If VPWorldMode = False Or VPWorldLoaded = False Then Return

    CameraPick VPCam, localX, localY
    Local picked = PickedEntity()
    If picked = 0 Then Return
    Local S.Scenery = Loom_SceneryFromEN(picked)
    If S = Null Then Return

    Local meshID = S\MeshID
    If Handle(S) = ScnSelectedH Then ScnSelectedH = 0
    ; If an RMB scenery-drag is in progress on this same instance (RMB held +
    ; Ctrl+LMB delete), cancel the drag so the next drag frame doesn't
    ; PositionEntity a freed handle. Clear the whole drag latch.
    If Handle(S) = ScnDragH Or S\EN = ScnDragEN
        ScnDragging    = False
        ScnDragEN      = 0
        ScnDragH       = 0
        ScnDragYMode   = False
        ScnDragChanged = False
    EndIf
    If S\EN <> 0 Then FreeEntity S\EN
    Delete S

    SceneryDirty = True
    VPDirty = True
    Toast_Show("Deleted scenery (mesh " + Str(meshID) + ")", "danger")
    WriteLog(LoomLog, "ZoneViewport: deleted scenery mesh " + Str(meshID))
End Function


; =============================================================================
; Loom_SelectSceneryAtClick -- WORLD MODE ONLY. Plain LMB pick sets the
; selected scenery (drives the property readout + is the move target). Miss
; clears the selection.
; =============================================================================
Function Loom_SelectSceneryAtClick(localX, localY)
    If VPInitOK = False Then Return
    CameraPick VPCam, localX, localY
    Local S.Scenery = Loom_SceneryFromEN(PickedEntity())
    If S = Null
        ScnSelectedH = 0
    Else
        ScnSelectedH = Handle(S)
        Toast_Show("Selected scenery (mesh " + Str(S\MeshID) + ")", "info")
    EndIf
    VPDirty = True
End Function


; =============================================================================
; Loom_SaveScenery -- persist scenery (and the rest of the visual area) via the
; relocated SaveArea. DATA-LOSS GUARD: SaveArea rewrites the ENTIRE visual .dat
; from the live Each-<Type> lists, so it must run ONLY when VPWorldLoaded is
; True. If the world is not fully loaded this is a no-op + a warning toast --
; NEVER a partial write. Clears the SEPARATE SceneryDirty flag (not ZoneSaved).
; =============================================================================
Function Loom_SaveScenery(zoneHandle)
    Local Ar.Area = Object.Area(zoneHandle)
    If Ar = Null Then Return

    ; The airtight guard: no fully-loaded world -> refuse to write.
    If VPWorldLoaded = False Or VPWorldMode = False
        Toast_Show("Scenery save skipped: world not loaded (switch to world view first)", "warning")
        WriteLog(LoomLog, "ZoneViewport: Loom_SaveScenery refused -- VPWorldLoaded=" + Str(VPWorldLoaded))
        Return
    EndIf

    ; SaveArea returns False if WriteFile fails (disk full / locked / bad path).
    ; Only clear the dirty flag + report success when the write actually
    ; committed -- otherwise keep the edits marked dirty and warn.
    If SaveArea(Ar\Name$) = False
        Toast_Show("Scenery save FAILED for " + Ar\Name$ + " (edits kept)", "danger")
        WriteLog(LoomLog, "ZoneViewport: SaveArea returned False for " + Ar\Name$ + " -- SceneryDirty kept")
        Return
    EndIf
    SceneryDirty = False
    Toast_Show("Saved scenery for " + Ar\Name$, "success")
    WriteLog(LoomLog, "ZoneViewport: SaveArea wrote visual .dat for " + Ar\Name$)
End Function


; =============================================================================
; Loom_DrawSceneryPicker -- mesh-catalog picker panel, drawn on the right edge
; of the viewport when ScnAddMode is on. Rows list mesh filenames; click a row
; to arm that mesh as the placement brush. Wheel over the panel scrolls. Returns
; True when the cursor is over the panel (caller suppresses orbit + zoom).
; =============================================================================
Function Loom_DrawSceneryPicker(x, y, w, h, mx, my)
    If ScnAddMode = False Then Return False

    Local pickX = x + w - SCN_PICK_W
    Local pickY = y + 28
    Local pickH = SCN_PICK_ROWS * SCN_PICK_ROW_H + 20
    Local over = (mx >= pickX And mx < x + w And my >= pickY And my < pickY + pickH)

    ; Panel backdrop + header
    LoomFill pickX, pickY, SCN_PICK_W, pickH, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B
    LoomBorder pickX, pickY, SCN_PICK_W, pickH, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B
    LoomText pickX + 6, pickY + 3, "SCENERY MESH (" + Str(MeshesTotalCount) + ")", LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B

    ; Wheel scroll (only while hovering the panel)
    If over = True
        Local wheel = Loom_MouseWheel()
        If wheel <> 0
            ScnPickerScroll = ScnPickerScroll - wheel
            Loom_ConsumeWheel()
        EndIf
    EndIf
    Local maxScroll = MeshesTotalCount - SCN_PICK_ROWS
    If maxScroll < 0 Then maxScroll = 0
    If ScnPickerScroll > maxScroll Then ScnPickerScroll = maxScroll
    If ScnPickerScroll < 0 Then ScnPickerScroll = 0

    Local rowY = pickY + 18
    Local i
    For i = 0 To SCN_PICK_ROWS - 1
        Local idx = ScnPickerScroll + i
        If idx >= MeshesTotalCount Then Exit
        Local mEnt.MeshEntry = Meshes_GetByIndex(idx)
        If mEnt <> Null
            Local rHover = (mx >= pickX And mx < pickX + SCN_PICK_W And my >= rowY And my < rowY + SCN_PICK_ROW_H)
            Local isBrush = (mEnt\ID = ScnBrushMeshID)
            If isBrush = True
                LoomFill pickX + 1, rowY, SCN_PICK_W - 2, SCN_PICK_ROW_H, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B
                LoomText pickX + 6, rowY + 1, mEnt\Filename$, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B
            Else If rHover = True
                LoomFill pickX + 1, rowY, SCN_PICK_W - 2, SCN_PICK_ROW_H, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B
                LoomText pickX + 6, rowY + 1, mEnt\Filename$, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B
            Else
                LoomText pickX + 6, rowY + 1, mEnt\Filename$, LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B
            EndIf
            If rHover = True And Loom_MouseClicked() = True
                ScnBrushMeshID = mEnt\ID
                ScnBrushName$  = mEnt\Filename$
                Loom_ConsumeClick()
                Toast_Show("Brush: " + mEnt\Filename$ + " -- click terrain to place", "info")
            EndIf
        EndIf
        rowY = rowY + SCN_PICK_ROW_H
    Next

    Return over
End Function


; =============================================================================
; Loom_DrawZoneViewport -- public render entry. Lazy-loads markers for the
; zone if the zone handle changed since last frame. Then handles orbit/
; zoom input, repositions the camera, renders to RT, blits to back buffer.
; =============================================================================
; Loom_DrawZoneViewport -- render the zone 3D scene into the screen rect
; (x, y, w, h). Full-screen capable: the composer passes the whole left area
; so the designer can fly around and edit markers directly. Renders the
; camera DIRECTLY to the back buffer at (x,y,w,h) -- render-to-texture does
; not capture 3D in this BlitzForge build (see MeshPreview note), so we aim
; the camera's viewport at the on-screen rect instead. CameraPick coords are
; viewport-relative (mx-x, my-y); CameraProject results are too, so the 2D
; grid/compass overlays are offset by (x,y) when drawn to the back buffer.
Function Loom_DrawZoneViewport(zoneHandle, x, y, w, h)
    If VPInitOK = False
        LoomFill x, y, w, h, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B
        LoomBorder x, y, w, h, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B
        LoomText x + 8, y + 8, "viewport init failed", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B
        Return False
    EndIf

    Local Ar.Area = Object.Area(zoneHandle)
    If Ar = Null
        LoomFill x, y, w, h, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B
        LoomBorder x, y, w, h, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B
        LoomText x + 8, y + 8, "no zone focused", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B
        Return False
    EndIf

    If zoneHandle <> VPLoadedZoneH
        ; World mode follows the focused zone: drop the old zone's world and
        ; try the new zone's. Loom_SetWorldMode soft-fails back to schematic
        ; (offset restored, ground shown) when the new zone has no visual
        ; data, and reloads markers in both outcomes.
        If VPWorldMode = True
            Loom_SetWorldMode(zoneHandle, True)
            If VPWorldMode = False
                ; Load failed -- finish the fallback to schematic placement.
                VPSceneYOff# = VP_SCENE_Y_OFFSET#
                If VPGround <> 0 Then ShowEntity VPGround
                Loom_LoadZoneMarkers(Ar)
            EndIf
        Else
            Loom_LoadZoneMarkers(Ar)
        EndIf
        VPLoadedZoneH = zoneHandle
        VPDirty = True
        WriteLog(LoomLog, "ZoneViewport: loaded zone " + Ar\Name$)
    EndIf

    ; Aim the camera's viewport at the on-screen rect up front so the
    ; CameraPick calls below (which run before this frame's RenderWorld)
    ; use the current rect. Picks are viewport-relative -> (mx-x, my-y).
    CameraViewport VPCam, x, y, w, h

    ; ---- Input handling -----------------------------------------------------
    Local mx = MouseX()
    Local my = MouseY()
    Local inside = (mx >= x And mx < x + w And my >= y And my < y + h)

    ; Scenery picker panel occupies the right strip in world + add mode.
    ; Clicks there select a brush (handled at draw time), so orbit-start and
    ; scenery placement are suppressed while the cursor is over it.
    Local overSceneryPanel = False
    If ScnAddMode = True And VPWorldMode = True
        Local spX = x + w - SCN_PICK_W
        Local spY = y + 28
        Local spH = SCN_PICK_ROWS * SCN_PICK_ROW_H + 20
        overSceneryPanel = (mx >= spX And mx < x + w And my >= spY And my < spY + spH)
    EndIf

    If MouseDown(1) = True And inside = True
        If VPDragging = False
            ; Don't start an orbit when the press begins over the picker panel
            ; -- that click belongs to the mesh list.
            If overSceneryPanel = False
                VPDragging = True
                VPLastMX = mx
                VPLastMY = my
                VPDragStartMX = mx     ; remember initial press for click-vs-drag distinguish
                VPDragStartMY = my
            EndIf
        Else
            Local dx = mx - VPLastMX
            Local dy = my - VPLastMY
            If dx <> 0 Or dy <> 0
                VPYaw# = VPYaw# + Float(dx) * 0.5
                VPPitch# = VPPitch# + Float(dy) * 0.5
                If VPPitch# > 89.0 Then VPPitch# = 89.0
                If VPPitch# < -89.0 Then VPPitch# = -89.0
                VPDirty = True
            EndIf
            VPLastMX = mx
            VPLastMY = my
        EndIf
    Else
        ; On LMB release: if the press-to-release total movement was
        ; small (no real drag), treat as a click. Loom_MouseClicked() is
        ; False here (it fires on PRESS not release), so check VPDragging
        ; transitioning to False.
        If VPDragging = True
            Local moveDist = Abs(mx - VPDragStartMX) + Abs(my - VPDragStartMY)
            If moveDist < 4 And inside = True
                ; KeyDown(42) = LShift, KeyDown(54) = RShift
                ; KeyDown(29) = LCtrl,  KeyDown(157) = RCtrl
                If VPWorldMode = True And ScnAddMode = True And overSceneryPanel = False
                    ; Scenery edit mode: Ctrl = delete scenery; a click on an
                    ; existing scenery selects it; a click on empty terrain
                    ; with a brush armed places a new instance.
                    If KeyDown(29) = True Or KeyDown(157) = True
                        Loom_DeleteSceneryAtClick(zoneHandle, mx - x, my - y)
                    Else
                        CameraPick VPCam, mx - x, my - y
                        Local sHit.Scenery = Loom_SceneryFromEN(PickedEntity())
                        If sHit <> Null
                            ScnSelectedH = Handle(sHit)
                            VPDirty = True
                        Else If ScnBrushMeshID > 0
                            Loom_AddSceneryAtClick(zoneHandle, mx - x, my - y)
                        EndIf
                    EndIf
                Else
                    ; Schematic / marker editing (both modes when not in
                    ; scenery mode): delete / add-portal / pick marker.
                    If KeyDown(29) = True Or KeyDown(157) = True
                        Loom_DeleteMarkerAtClick(zoneHandle, mx - x, my - y)
                    Else If KeyDown(42) = True Or KeyDown(54) = True
                        Loom_AddPortalAtClick(zoneHandle, mx - x, my - y)
                    Else
                        Loom_PickZoneMarker(mx - x, my - y)
                    EndIf
                EndIf
            EndIf
        EndIf
        VPDragging = False
    EndIf

    ; ---- Marker drag-to-edit (RMB) -----------------------------------------
    ; RMB inside viewport hit-tests a marker on press; subsequent frames
    ; track the cursor on the ground plane and update the marker +
    ; underlying Area coord. Release commits.
    Local rmbDown = MouseDown(2)
    Local rmbJustPressed = (rmbDown = True And VPRMBPrevDown = False)
    VPRMBPrevDown = rmbDown

    ; ---- Scenery move-drag (RMB, world + scenery mode) ---------------------
    ; In scenery mode RMB grabs the scenery instance under the cursor and
    ; drags it on the terrain (XZ; Shift at press = Y). Scenery position lives
    ; on the LIVE entity (SaveArea reads EntityX/Y/Z), so PositionEntity IS the
    ; data write -- no field commit needed. Owns RMB while active, so the
    ; marker-drag + fly blocks below are gated off.
    Local sceneryDragActive = (VPWorldMode = True And ScnAddMode = True)
    If sceneryDragActive = True And rmbDown = True And inside = True
        If ScnDragging = False
            CameraPick VPCam, mx - x, my - y
            Local sRec.Scenery = Loom_SceneryFromEN(PickedEntity())
            If sRec <> Null
                ScnDragging    = True
                ScnDragEN      = sRec\EN
                ScnDragH       = Handle(sRec)
                ScnSelectedH   = ScnDragH
                ScnDragYMode   = (KeyDown(42) = True Or KeyDown(54) = True)
                ScnDragLastMY  = my
                ScnDragChanged = False
            EndIf
        Else
            If ScnDragYMode = True
                Local sdy = my - ScnDragLastMY
                ScnDragLastMY = my
                If sdy <> 0
                    Local sYD# = Float(-sdy) * (VPDistance# / 200.0)
                    PositionEntity ScnDragEN, EntityX#(ScnDragEN), EntityY#(ScnDragEN) + sYD#, EntityZ#(ScnDragEN)
                    ScnDragChanged = True
                    VPDirty = True
                EndIf
            Else
                ; XZ drag: hide the dragged scenery so the ray passes through
                ; it, then pick the terrain beneath via the shared helper.
                HideEntity ScnDragEN
                Local scnGot = Loom_PickGround(mx - x, my - y)
                ShowEntity ScnDragEN
                If scnGot = True
                    PositionEntity ScnDragEN, VPPickX#, EntityY#(ScnDragEN), VPPickZ#
                    ScnDragChanged = True
                    VPDirty = True
                EndIf
            EndIf
        EndIf
    Else
        If ScnDragging = True
            If ScnDragChanged = True
                SceneryDirty = True
                Toast_Show("Moved scenery", "success")
                WriteLog(LoomLog, "ZoneViewport: scenery drag commit (mesh handle " + Str(ScnDragH) + ")")
            EndIf
            ScnDragging    = False
            ScnDragEN      = 0
            ScnDragH       = 0
            ScnDragYMode   = False
            ScnDragChanged = False
        EndIf
    EndIf

    If rmbDown = True And inside = True And sceneryDragActive = False
        If VPMarkerDragging = False
            ; Press: hit-test for a marker. Need to render the scene
            ; first so the camera + entity positions are current for
            ; CameraPick, but we already did that this frame at the
            ; END of the previous renderAndUpdate call. The picks
            ; should still be valid since nothing has moved.
            CameraPick VPCam, mx - x, my - y
            Local pickedEN = PickedEntity()
            ; Marker drag works in both modes now (ADR-004 world-mode editing
            ; follow-up): the XZ branch re-picks via Loom_PickGround (real
            ; terrain in world mode), and the shift+RMB Y-mode branch uses
            ; raw mouse deltas that are mode-independent. Resolve the pick to
            ; an actual draggable marker FIRST -- in world mode an empty-ground
            ; click returns the terrain entity (nonzero, not VPGround), which
            ; must NOT be mistaken for "grabbed a marker" or the shift+RMB
            ; add-trigger fallback below never fires (dead in world mode).
            Local grabbedMarker = False
            If pickedEN <> 0 And pickedEN <> VPGround
                Local pm.ZoneViewportMarker
                For pm = Each ZoneViewportMarker
                    If pm\EN = pickedEN And (pm\Kind = "portal" Or pm\Kind = "trigger" Or pm\Kind = "spawn")
                        VPMarkerDragging = True
                        VPMarkerDragEN   = pickedEN
                        VPMarkerDragKind$ = pm\Kind
                        VPMarkerDragIdx  = pm\IndexN
                        VPMarkerDragArH  = zoneHandle
                        VPMarkerDragVisualLift# = pm\VisualLift#
                        ; Shift at press = Y-axis drag mode (locked for
                        ; the duration of the drag; user can release
                        ; shift mid-drag and Y mode persists).
                        VPMarkerDragYMode = (KeyDown(42) = True Or KeyDown(54) = True)
                        VPMarkerDragLastMY = my
                        grabbedMarker = True
                        Exit
                    EndIf
                Next
            EndIf
            If grabbedMarker = False And rmbJustPressed = True And (KeyDown(42) = True Or KeyDown(54) = True)
                ; Press edge + shift held + no draggable marker grabbed =
                ; add trigger on the ground beneath (Loom_AddTriggerAtClick
                ; picks through Loom_PickGround, so it lands on the flat floor
                ; in schematic mode and the real terrain in world mode).
                ; Edge-detect so we don't add many triggers per held frame.
                Loom_AddTriggerAtClick(zoneHandle, mx - x, my - y)
            EndIf
        Else
            If VPMarkerDragYMode = True
                ; Y-mode drag: vertical mouse delta = Y delta. Scale
                ; by camera distance so the apparent feel stays
                ; consistent at different zooms. Mouse UP = move UP
                ; (negative dy in screen coords becomes positive Y).
                Local dyPx = my - VPMarkerDragLastMY
                VPMarkerDragLastMY = my
                If dyPx <> 0
                    Local yDelta# = Float(-dyPx) * (VPDistance# / 200.0)
                    ; EntityY includes the marker's render-only lift. Move and
                    ; persist the semantic Area coordinate, then restore that
                    ; lift only in the displayed entity position.
                    Local curSemanticY# = EntityY#(VPMarkerDragEN) - VPSceneYOff# - VPMarkerDragVisualLift#
                    Local curX# = EntityX#(VPMarkerDragEN)
                    Local curZ# = EntityZ#(VPMarkerDragEN)
                    Local newSemanticY# = curSemanticY# + yDelta#
                    Local newY# = VPSceneYOff# + newSemanticY# + VPMarkerDragVisualLift#
                    PositionEntity VPMarkerDragEN, curX#, newY#, curZ#
                    Loom_CommitMarkerY(VPMarkerDragArH, VPMarkerDragKind$, VPMarkerDragIdx, newSemanticY#)
                    VPMarkerDragChanged = True
                    VPDirty = True
                EndIf
            Else
                ; XZ-mode drag (default): re-pick against the ground.
                ; Loom_PickGround disables every marker's pick mode (so the
                ; ray passes through the dragged marker AND its neighbours)
                ; and lands on VPGround in schematic mode or the real terrain
                ; in world mode. Horizontal move only -- Y is preserved (use
                ; shift+RMB Y-mode to change height), identical in both modes.
                If Loom_PickGround(mx - x, my - y) = True
                    Local newX# = VPPickX#
                    Local newZ# = VPPickZ#
                    ; Update marker position (keep current Y)
                    PositionEntity VPMarkerDragEN, newX#, EntityY#(VPMarkerDragEN), newZ#
                    ; Update the underlying Area field via the existing zone
                    ; setter dispatch (handles Strict-mode dim-write trap).
                    Loom_CommitMarkerCoord(VPMarkerDragArH, VPMarkerDragKind$, VPMarkerDragIdx, newX#, newZ#)
                    VPMarkerDragChanged = True
                    VPDirty = True
                EndIf
            EndIf
        EndIf
    Else
        If VPMarkerDragging = True
            ; Commit on release. The per-frame updates already wrote
            ; through to the Area; only toast + mark dirty when a write
            ; actually happened (a drag whose picks all missed commits
            ; nothing and must not flip the zone dirty).
            If VPMarkerDragChanged = True
                If LoomComposer <> Null Then Composer::markDirtyForKind(LoomComposer, "zone")
                Toast_Show("Moved " + VPMarkerDragKind$ + " " + Str(VPMarkerDragIdx), "success")
                WriteLog(LoomLog, "ZoneViewport: drag commit " + VPMarkerDragKind$ + " " + Str(VPMarkerDragIdx))
            EndIf
            VPMarkerDragging = False
            VPMarkerDragEN   = 0
            VPMarkerDragKind$ = ""
            VPMarkerDragIdx  = -1
            VPMarkerDragYMode = False
            VPMarkerDragVisualLift# = 0.0
            VPMarkerDragChanged = False
        EndIf
    EndIf

    ; Wheel zooms -- unless the cursor is over the scenery picker panel, where
    ; the wheel scrolls the mesh list instead (handled in Loom_DrawSceneryPicker).
    If inside = True And overSceneryPanel = False
        Local wheel = Loom_MouseWheel()
        If wheel <> 0
            VPDistance# = VPDistance# - Float(wheel) * (VPDistance# * 0.08)
            If VPDistance# < 20.0 Then VPDistance# = 20.0
            If VPDistance# > 5000.0 Then VPDistance# = 5000.0
            VPDirty = True
            ; Consume so composer scroll doesn't also fire.
            Loom_ConsumeWheel()
        EndIf
    EndIf

    ; ---- MMB pan camera ----------------------------------------------------
    ; Middle-mouse drag translates the orbit center in camera-aligned
    ; XZ. Forward/right vectors derived from the current VPYaw so panning
    ; feels natural relative to the visible camera orientation. Pan speed
    ; scales with VPDistance so farther zooms produce larger per-pixel
    ; pan steps (keeps the apparent on-screen drag rate consistent).
    Local mmbDown = MouseDown(3)
    Local mmbJustPressed = (mmbDown = True And VPMMBPrevDown = False)
    VPMMBPrevDown = mmbDown

    ; shift+MMB-press on ground = add spawn (waypoint+spawn pair).
    ; Edge-detect so it fires once per press, not per held frame.
    ; Fires BEFORE the pan branch -- pan should still work with
    ; plain MMB (shift held = override to add-spawn).
    If mmbJustPressed = True And inside = True And (KeyDown(42) = True Or KeyDown(54) = True)
        Loom_AddSpawnAtClick(zoneHandle, mx - x, my - y)
    EndIf

    If mmbDown = True And inside = True
        If VPPanning = False
            VPPanning = True
            VPPanLastMX = mx
            VPPanLastMY = my
        Else
            Local pdx = mx - VPPanLastMX
            Local pdy = my - VPPanLastMY
            Local panSpeed# = VPDistance# / 200.0
            ; Camera-relative axes in world XZ (yaw=0 looks along -Z):
            ;   forward (away from camera) = (Sin(yaw), 0, -Cos(yaw))
            ;   right                       = (Cos(yaw), 0, Sin(yaw))
            Local fwdX# = Sin(VPYaw#)
            Local fwdZ# = -Cos(VPYaw#)
            Local rgtX# = Cos(VPYaw#)
            Local rgtZ# = Sin(VPYaw#)
            ; Drag right (positive pdx) should slide scene LEFT under
            ; the camera, so subtract pdx * right.
            VPSceneCenterX# = VPSceneCenterX# - Float(pdx) * rgtX# * panSpeed# + Float(pdy) * fwdX# * panSpeed#
            VPSceneCenterZ# = VPSceneCenterZ# - Float(pdx) * rgtZ# * panSpeed# + Float(pdy) * fwdZ# * panSpeed#
            VPPanLastMX = mx
            VPPanLastMY = my
        EndIf
    Else
        VPPanning = False
    EndIf

    ; ---- RMB-hold fly: WASD move + Q/E down/up ------------------------------
    ; While RMB is held over the viewport (and not dragging a marker), WASD
    ; flies through the scene and Q/E drop/raise. Movement translates the
    ; orbit pivot (VPSceneCenter) along the camera's facing/right vectors, so
    ; you keep your view angle while moving; LMB still orbits, wheel zooms.
    ; (Loom.bb silences the browser keyboard while a zone is focused, so
    ; these keys don't dribble into the card filter.) Scancodes: W17 A30 S31
    ; D32 Q16 E18.
    If rmbDown = True And inside = True And VPMarkerDragging = False And ScnDragging = False
        Local flyStep# = VPDistance# * 0.03
        If flyStep# < 2.0 Then flyStep# = 2.0
        Local ffX# = Sin(VPYaw#)
        Local ffZ# = -Cos(VPYaw#)
        Local frX# = Cos(VPYaw#)
        Local frZ# = Sin(VPYaw#)
        If KeyDown(17) = True
            VPSceneCenterX# = VPSceneCenterX# + ffX# * flyStep#
            VPSceneCenterZ# = VPSceneCenterZ# + ffZ# * flyStep#
            VPDirty = True
        EndIf
        If KeyDown(31) = True
            VPSceneCenterX# = VPSceneCenterX# - ffX# * flyStep#
            VPSceneCenterZ# = VPSceneCenterZ# - ffZ# * flyStep#
            VPDirty = True
        EndIf
        If KeyDown(30) = True
            VPSceneCenterX# = VPSceneCenterX# - frX# * flyStep#
            VPSceneCenterZ# = VPSceneCenterZ# - frZ# * flyStep#
            VPDirty = True
        EndIf
        If KeyDown(32) = True
            VPSceneCenterX# = VPSceneCenterX# + frX# * flyStep#
            VPSceneCenterZ# = VPSceneCenterZ# + frZ# * flyStep#
            VPDirty = True
        EndIf
        If KeyDown(18) = True
            VPSceneCenterY# = VPSceneCenterY# + flyStep#
            VPDirty = True
        EndIf
        If KeyDown(16) = True
            VPSceneCenterY# = VPSceneCenterY# - flyStep#
            VPDirty = True
        EndIf
    EndIf

    ; ---- Position camera by orbit math -------------------------------------
    Local yawRad# = VPYaw# * 3.14159 / 180.0
    ; ---- Highlight transition: only scale on change -----------------------
    ; Per-frame iteration over every marker was expensive on big zones
    ; (3000+ entities -> 3000+ ScaleEntity calls per frame). Now only
    ; the OLD highlighted marker shrinks back and the NEW one grows;
    ; if the highlight didn't change, no scaling work at all.
    If LoomZoneHighlightKind$ <> VPPrevHighlightKind$ Or LoomZoneHighlightIdx <> VPPrevHighlightIdx
        Local hm.ZoneViewportMarker
        Local newCenterX# = VPSceneCenterX#
        Local newCenterZ# = VPSceneCenterZ#
        Local centerChanged = False
        For hm = Each ZoneViewportMarker
            If hm\Kind = VPPrevHighlightKind$ And hm\IndexN = VPPrevHighlightIdx And VPPrevHighlightKind$ <> ""
                ScaleEntity hm\EN, hm\BaseScale, hm\BaseScale, hm\BaseScale
            Else If hm\Kind = LoomZoneHighlightKind$ And hm\IndexN = LoomZoneHighlightIdx And LoomZoneHighlightKind$ <> ""
                ScaleEntity hm\EN, hm\BaseScale * 1.6, hm\BaseScale * 1.6, hm\BaseScale * 1.6
                ; Camera-follow: pan the orbit center to the new marker
                ; so it lands inside the visible frustum even when it
                ; was previously off-screen. Don't touch zoom/yaw/pitch
                ; -- user keeps their viewing angle.
                newCenterX# = EntityX#(hm\EN)
                newCenterZ# = EntityZ#(hm\EN)
                centerChanged = True
            EndIf
        Next
        If centerChanged = True
            VPSceneCenterX# = newCenterX#
            VPSceneCenterZ# = newCenterZ#
        EndIf
        VPPrevHighlightKind$ = LoomZoneHighlightKind$
        VPPrevHighlightIdx   = LoomZoneHighlightIdx
        VPDirty = True
    EndIf

    ; ---- Render the scene directly to the back buffer, every frame ---------
    ; The back buffer is cleared at the top of renderFrame, so unlike the old
    ; render-to-texture path (which doesn't capture 3D in this BlitzForge
    ; build anyway) we re-render each frame. CameraViewport (set above)
    ; confines RenderWorld's clear + draw to the (x,y,w,h) rect.
    Local cx# = VPSceneCenterX# + Cos(VPPitch#) * Sin(VPYaw#) * VPDistance#
    Local cy# = VPSceneYOff# + VPSceneCenterY# + Sin(VPPitch#) * VPDistance#
    Local cz# = VPSceneCenterZ# - Cos(VPPitch#) * Cos(VPYaw#) * VPDistance#
    PositionEntity VPCam, cx#, cy#, cz#
    ; Look at the orbit pivot (the scene centre), NOT VPGround at the origin.
    ; The camera position above is computed at distance VPDistance from this
    ; same point, so pointing here makes orbit pure-rotation and zoom
    ; pure-dolly with the content staying centred.
    PositionEntity VPPivot, VPSceneCenterX#, VPSceneYOff# + VPSceneCenterY#, VPSceneCenterZ#
    PointEntity VPCam, VPPivot

    ShowEntity VPCam
    RenderWorld
    HideEntity VPCam
    VPDirty = False

    ; ---- 2D overlays (grid + compass) on the back buffer -------------------
    ; CameraProject results are viewport-relative, so offset by (x,y). The 2D
    ; Viewport clips them to the rect so projected lines/labels can't bleed
    ; onto the composer panel or window chrome. Reset to full buffer after.
    Viewport x, y, w, h
    Color 70, 70, 80      ; muted stone-grey for grid
    Local gridSpan# = 2000.0
    Local gridStep# = 250.0
    Local g# = -gridSpan#
    While g# <= gridSpan#
        ; X-direction line: constant z = g, x varies -gridSpan..+gridSpan.
        ; CameraProject is a void command; it leaves ProjectedZ()=0 (and
        ; X/Y at 0) for points outside the frustum (behind the camera / past
        ; the far plane). Drawing those produced the fan of lines converging
        ; on the top-left corner. ProjectedZ()>0 means the point is in view;
        ; capture each endpoint's values before the next CameraProject
        ; overwrites them, and only draw when BOTH endpoints are visible.
        CameraProject VPCam, -gridSpan#, VPSceneYOff#, g#
        Local ax = x + ProjectedX()
        Local ay = y + ProjectedY()
        Local az# = ProjectedZ#()
        CameraProject VPCam,  gridSpan#, VPSceneYOff#, g#
        Local bx = x + ProjectedX()
        Local by = y + ProjectedY()
        Local bz# = ProjectedZ#()
        If az# > 0.0 And bz# > 0.0 Then Line ax, ay, bx, by
        ; Z-direction line: constant x = g, z varies
        CameraProject VPCam, g#, VPSceneYOff#, -gridSpan#
        Local cx2 = x + ProjectedX()
        Local cy2 = y + ProjectedY()
        Local cz2# = ProjectedZ#()
        CameraProject VPCam, g#, VPSceneYOff#,  gridSpan#
        Local dx2 = x + ProjectedX()
        Local dy2 = y + ProjectedY()
        Local dz2# = ProjectedZ#()
        If cz2# > 0.0 And dz2# > 0.0 Then Line cx2, cy2, dx2, dy2
        g# = g# + gridStep#
    Wend

    ; Compass labels at +N/+S/+E/+W on the ground (project through the same
    ; camera so they tilt with the view). Only draw when in view, else they'd
    ; stack in the top-left corner like the grid lines did.
    Color 200, 200, 110   ; brass-light for compass letters
    CameraProject VPCam, 0, VPSceneYOff#, gridSpan#
    If ProjectedZ#() > 0.0 Then Text x + ProjectedX(), y + ProjectedY(), "N", True, True
    CameraProject VPCam, 0, VPSceneYOff#, -gridSpan#
    If ProjectedZ#() > 0.0 Then Text x + ProjectedX(), y + ProjectedY(), "S", True, True
    CameraProject VPCam, gridSpan#, VPSceneYOff#, 0
    If ProjectedZ#() > 0.0 Then Text x + ProjectedX(), y + ProjectedY(), "E", True, True
    CameraProject VPCam, -gridSpan#, VPSceneYOff#, 0
    If ProjectedZ#() > 0.0 Then Text x + ProjectedX(), y + ProjectedY(), "W", True, True
    Viewport 0, 0, GraphicsWidth(), GraphicsHeight()

    LoomBorder x, y, w, h, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B

    ; Legend overlay with live counts. Capitalized labels mirror the
    ; composer section names so the user can mentally map widget colors
    ; to the editable sections below the viewport.
    LoomText x + 8, y + 8,  "portals "   + Str(VPCountPortals),   LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B
    LoomText x + 8, y + 24, "spawns "    + Str(VPCountSpawns),    LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B
    LoomText x + 8, y + 40, "triggers "  + Str(VPCountTriggers),  LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B
    LoomText x + 8, y + 56, "waypoints " + Str(VPCountWaypoints), 200, 200, 210
    ; X/Y/Z axis legend (matches the colored lines at scene origin)
    LoomText x + w - 60, y + 26, "X", 220, 60, 60
    LoomText x + w - 48, y + 26, "Y", 60, 220, 60
    LoomText x + w - 36, y + 26, "Z", 60, 120, 220

    ; Reset View pill -- top-right corner. Click restores auto-fit
    ; camera + clears orbit/zoom for the loaded zone. Useful when
    ; the user has spun the camera so far they can't find anything.
    Local rsW = 60
    Local rsX = x + w - rsW - 6
    Local rsY = y + 6
    Local rsHover = (mx >= rsX And mx < rsX + rsW And my >= rsY And my < rsY + 16)
    If rsHover = True
        LoomFill rsX, rsY, rsW, 16, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B
        LoomText rsX + 4, rsY + 1, "reset view", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B
    Else
        LoomBorder rsX, rsY, rsW, 16, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B
        LoomText rsX + 4, rsY + 1, "reset view", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B
    EndIf
    If rsHover = True And Loom_MouseClicked() = True
        Loom_ResetZoneView()
        Loom_ConsumeClick()
    EndIf

    ; World/schematic toggle pill -- left of Reset View. World mode loads
    ; the zone's real terrain/scenery via LoadAreaData (ADR-004 Phase C);
    ; the label shows the mode you'd SWITCH TO, matching pill conventions.
    Local wmW = 64
    Local wmX = rsX - wmW - 6
    Local wmY = rsY
    Local wmLabel$ = "world view"
    If VPWorldMode = True Then wmLabel$ = "schematic"
    Local wmHover = (mx >= wmX And mx < wmX + wmW And my >= wmY And my < wmY + 16)
    If wmHover = True
        LoomFill wmX, wmY, wmW, 16, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B
        LoomText wmX + 4, wmY + 1, wmLabel$, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B
    Else
        LoomBorder wmX, wmY, wmW, 16, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B
        LoomText wmX + 4, wmY + 1, wmLabel$, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B
    EndIf
    If wmHover = True And Loom_MouseClicked() = True
        Loom_SetWorldMode(zoneHandle, Not VPWorldMode)
        Loom_ConsumeClick()
    EndIf

    ; Scenery pills -- WORLD MODE ONLY (scenery is visual world data). "add
    ; scenery" toggles the placement brush + mesh picker; "save scenery"
    ; persists via SaveArea (guarded on a fully-loaded world in Loom_SaveScenery).
    If VPWorldMode = True
        Local scAddW = 76
        Local scAddX = wmX - scAddW - 6
        Local scAddHover = (mx >= scAddX And mx < scAddX + scAddW And my >= rsY And my < rsY + 16)
        Local scAddLbl$ = "add scenery"
        If ScnAddMode = True Then scAddLbl$ = "done adding"
        If ScnAddMode = True Or scAddHover = True
            LoomFill scAddX, rsY, scAddW, 16, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B
            LoomText scAddX + 4, rsY + 1, scAddLbl$, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B
        Else
            LoomBorder scAddX, rsY, scAddW, 16, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B
            LoomText scAddX + 4, rsY + 1, scAddLbl$, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B
        EndIf
        If scAddHover = True And Loom_MouseClicked() = True
            ScnAddMode = Not ScnAddMode
            Loom_ConsumeClick()
        EndIf

        Local scSaveW = 84
        Local scSaveX = scAddX - scSaveW - 6
        Local scSaveLbl$ = "save scenery"
        If SceneryDirty = True Then scSaveLbl$ = "save scenery*"
        Local scSaveHover = (mx >= scSaveX And mx < scSaveX + scSaveW And my >= rsY And my < rsY + 16)
        If scSaveHover = True
            LoomFill scSaveX, rsY, scSaveW, 16, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B
            LoomText scSaveX + 4, rsY + 1, scSaveLbl$, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B
        Else
            LoomBorder scSaveX, rsY, scSaveW, 16, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B
            LoomText scSaveX + 4, rsY + 1, scSaveLbl$, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B
        EndIf
        If scSaveHover = True And Loom_MouseClicked() = True
            Loom_SaveScenery(zoneHandle)
            Loom_ConsumeClick()
        EndIf
    Else
        ; Leaving world mode retires the scenery brush affordance.
        ScnAddMode = False
    EndIf

    ; Scenery mesh picker panel (right strip) -- only paints in world+add mode.
    Loom_DrawSceneryPicker(x, y, w, h, mx, my)

    ; Selected-scenery property readout (bottom-left, above the hint bar).
    If VPWorldMode = True And ScnSelectedH <> 0
        Local selS.Scenery = Object.Scenery(ScnSelectedH)
        If selS = Null
            ScnSelectedH = 0    ; stale handle -> clear
        Else If selS\EN <> 0
            Local roX = x + 8
            Local roY = y + h - 66
            LoomFill roX, roY, 216, 44, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B
            LoomBorder roX, roY, 216, 44, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B
            LoomText roX + 5, roY + 2, "SCENERY  mesh " + Str(selS\MeshID), LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B
            LoomText roX + 5, roY + 15, "pos " + Int(EntityX#(selS\EN, True)) + ", " + Int(EntityY#(selS\EN, True)) + ", " + Int(EntityZ#(selS\EN, True)), LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B
            LoomText roX + 5, roY + 28, "yaw " + Int(EntityYaw#(selS\EN, True)) + "   scale " + selS\ScaleX#, LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B
        EndIf
    EndIf

    ; Highlighted-marker name label: project the highlighted marker's
    ; world position to screen via CameraProject and float its label
    ; above the marker. Gives a clear visual cross-reference between
    ; the composer's "you are here" and the 3D marker that lit up.
    If LoomZoneHighlightKind$ <> "" And LoomZoneHighlightIdx >= 0
        Local hl.ZoneViewportMarker
        For hl = Each ZoneViewportMarker
            If hl\Kind = LoomZoneHighlightKind$ And hl\IndexN = LoomZoneHighlightIdx
                CameraProject VPCam, EntityX#(hl\EN), EntityY#(hl\EN), EntityZ#(hl\EN)
                If ProjectedZ#() > 0
                    Local pxL = x + Int(ProjectedX#())
                    Local pyL = y + Int(ProjectedY#()) - 22  ; lift above marker
                    Local lbl$ = hl\Kind$ + " " + Str(hl\IndexN)
                    Local lblW = StringWidth(lbl) + 8
                    ; Clamp inside widget rect so the text doesn't
                    ; leak outside the viewport border.
                    If pxL < x + 4 Then pxL = x + 4
                    If pxL + lblW > x + w - 4 Then pxL = x + w - lblW - 4
                    If pyL < y + 4 Then pyL = y + 4
                    LoomFill pxL, pyL, lblW, 14, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B
                    LoomBorder pxL, pyL, lblW, 14, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B
                    LoomText pxL + 4, pyL - 1, lbl, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B
                EndIf
                Exit
            EndIf
        Next
    EndIf

    If inside = True
        If VPWorldMode = True And ScnAddMode = True
            LoomText x + 8, y + h - 18, "SCENERY MODE  |  pick a mesh (right)  |  LMB terrain: place  |  LMB scenery: select  |  RMB drag: move (Shift+RMB = height)  |  Ctrl+LMB: delete  |  'save scenery' to persist", LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B
        Else If VPWorldMode = True
            LoomText x + 8, y + h - 18, "WORLD VIEW (editable)  |  LMB: orbit  |  MMB: pan  |  wheel: zoom  |  hold RMB + WASD fly  |  RMB drag marker: move on terrain  |  Shift+LMB: add portal on terrain  |  Ctrl+LMB: delete  |  'add scenery' for meshes", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B
        Else
            LoomText x + 8, y + h - 18, "LMB: orbit  |  MMB: pan  |  wheel: zoom  |  hold RMB + WASD fly / QE up-down  |  RMB drag a marker: move  |  Shift+LMB: add portal  |  Ctrl+LMB: delete", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B
        EndIf
    EndIf

    Return True
End Function


; =============================================================================
; Loom_ShutdownZoneViewport -- free GPU resources at exit.
; =============================================================================
Function Loom_ShutdownZoneViewport()
    Loom_UnloadWorld()
    Loom_FreeZoneMarkers()
    If SkyEN <> 0 Then FreeEntity SkyEN : SkyEN = 0
    If CloudEN <> 0 Then FreeEntity CloudEN : CloudEN = 0
    If StarsEN <> 0 Then FreeEntity StarsEN : StarsEN = 0
    If VPGround <> 0 Then FreeEntity VPGround : VPGround = 0
    If VPPivot <> 0 Then FreeEntity VPPivot : VPPivot = 0
    If VPCam <> 0 Then FreeEntity VPCam : VPCam = 0
    If VPLight <> 0 Then FreeEntity VPLight : VPLight = 0
    If VPRT <> 0 Then FreeTexture VPRT : VPRT = 0
    VPInitOK = False
End Function
