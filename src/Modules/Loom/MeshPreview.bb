; =============================================================================
; Loom/MeshPreview.bb -- 3D actor / item mesh preview widget
; =============================================================================
;
; First cut of the deferred "3D viewport" workstream. Renders a single mesh
; (typically an actor's MeshIDs[0] base body) into a render-to-texture
; target via a dedicated camera + light, then DrawImages the result into
; the composer at the requested rect.
;
; Architecture:
;   - PreviewCam       Camera entity that renders only the preview scene
;   - PreviewLight     Single DirectionalLight so the mesh is visible
;   - PreviewRT        TextureBuffer the camera renders into
;   - PreviewMesh      Current mesh entity (loaded via GetMesh)
;   - PreviewMeshID    Cached ID so we don't reload on every frame
;
; The preview camera is positioned FAR from the world origin (10000 units
; up) so it can't accidentally see any future world geometry the editor
; might create. The mesh sits next to the camera in that isolated space.
;
; Render flow per frame (called by composer):
;   1. position the mesh + camera; tick auto-spin
;   2. SetBuffer(TextureBuffer(rt))
;   3. CameraClsColor + RenderWorld
;   4. SetBuffer(BackBuffer())   <- critical: restore 2D drawing target
;   5. DrawImage rt at (x, y)
;
; Non-Strict (matches Settings.bb / ImageCache.bb / Recents.bb -- all the
; "free-function-with-module-globals" files).
;
; Caveats:
;   - This is a first cut. No textures applied (face/body textures would
;     need additional GetTexture + EntityTexture calls).
;   - Mesh animation isn't ticked.
;   - Failure mode is graceful: if mesh load fails, the placeholder draws
;     instead.


; ---- Constants --------------------------------------------------------------
Const LOOM_PREVIEW_SIZE       = 192     ; render-target dim (square)
Const LOOM_PREVIEW_CAM_X#     = 0.0
Const LOOM_PREVIEW_CAM_Y#     = 10000.0 ; far from any world geometry
Const LOOM_PREVIEW_CAM_Z#     = 0.0
Const LOOM_PREVIEW_CAM_RANGE# = 50.0    ; pulled back to fit a typical actor
Const LOOM_PREVIEW_AUTOSPIN_DEG_PER_SEC# = 36.0   ; one full rotation / 10s


; ---- Module state -----------------------------------------------------------
Global PreviewCam        = 0
Global PreviewLight      = 0
Global PreviewRT         = 0          ; render-target texture handle
Global PreviewMesh       = 0          ; current mesh entity
Global PreviewMeshID     = -1         ; cached ID so we know when to reload
Global PreviewInitOK     = False      ; True once the camera + RT exist
Global PreviewSpinTime#  = 0.0
Global PreviewLastTickMs = 0          ; for delta calc
Global PreviewMeshScale# = 1.0        ; auto-fit scale for current mesh
Global PreviewLookTarget = 0          ; hidden pivot at character CENTER;
                                      ; camera PointEntity-s this so the
                                      ; view frames the body's middle, not
                                      ; the mesh origin (which is at feet
                                      ; for character meshes -> looking at
                                      ; the floor is the wrong default).

; PreviewActorID lives in ImageCache.bb (included BEFORE Composer)
; so the Strict Composer module can write it without the dim-write-
; from-Strict trap. See feedback_loom_module_include_order.

; ---- Manual orbit state ----------------------------------------------------
; Drag-to-orbit: while LMB is held inside the preview rect, the mesh's
; Y/X rotation tracks the mouse delta. Releases the auto-spin while
; dragging; resumes when LMB releases. Wheel adjusts camera distance.
Global PreviewManualYaw#   = 0.0       ; user-controlled yaw override
Global PreviewManualPitch# = 0.0       ; user-controlled pitch override
Global PreviewManualActive = False     ; True if user has interacted (kills auto-spin)
Global PreviewDragging     = False     ; True between LMB-down + LMB-up inside rect
Global PreviewLastDragMX   = 0
Global PreviewLastDragMY   = 0
Global PreviewCamDistance# = LOOM_PREVIEW_CAM_RANGE#   ; runtime-zoomable


; =============================================================================
; Loom_InitMeshPreview -- one-time setup of camera + light + render target.
; Called once from Loom.bb after Graphics3D + entity data load.
; =============================================================================
Function Loom_InitMeshPreview()
    If PreviewInitOK = True Then Return

    ; Render-target texture. Flag 1 = color (RGB) + 256 = render-target
    ; in Blitz3D's CreateTexture flag bitfield. (1 + 256 = 257)
    PreviewRT = CreateTexture(LOOM_PREVIEW_SIZE, LOOM_PREVIEW_SIZE, 1 + 256)
    If PreviewRT = 0
        WriteLog(LoomLog, "MeshPreview: CreateTexture failed -- preview disabled")
        Return
    EndIf
    TextureBlend PreviewRT, 0

    ; Camera positioned far from world origin so it can't accidentally
    ; see future world geometry. Renders only entities near (0, 10000, 0).
    PreviewCam = CreateCamera()
    PositionEntity PreviewCam, LOOM_PREVIEW_CAM_X#, LOOM_PREVIEW_CAM_Y# + 5.0, LOOM_PREVIEW_CAM_Z# - LOOM_PREVIEW_CAM_RANGE#
    PointEntity   PreviewCam, 0   ; will be re-pointed at the mesh each frame
    CameraClsColor PreviewCam, 16, 16, 22   ; dark stone-950 background
    CameraRange    PreviewCam, 0.1, 1000.0
    ; CRITICAL: confine the camera to the render-target's pixel rect.
    ; CreateCamera() seeds a camera's viewport from the CURRENT buffer at
    ; creation time (bbCreateCamera -> gx_canvas->getViewport) -- which is
    ; the full back buffer here. Without this override, RenderWorld paints
    ; PreviewCam across the WHOLE window (the grey-mesh bleed), because the
    ; viewport stays full-screen even when SetBuffer points at the small RT.
    ; ZoneViewport's VPCam already does this; MeshPreview didn't, which is
    ; the entire bug. Match the RT dimensions exactly.
    CameraViewport PreviewCam, 0, 0, LOOM_PREVIEW_SIZE, LOOM_PREVIEW_SIZE
    HideEntity     PreviewCam     ; only show when actively rendering

    ; Single directional light so the mesh is visible.
    PreviewLight = CreateLight(1)   ; 1 = directional
    PositionEntity PreviewLight, LOOM_PREVIEW_CAM_X#, LOOM_PREVIEW_CAM_Y# + 20.0, LOOM_PREVIEW_CAM_Z# - 10.0
    RotateEntity   PreviewLight, 45, -30, 0
    LightColor     PreviewLight, 255, 255, 230

    ; Hidden pivot the camera points at. Position set per-mesh in
    ; Loom_LoadPreviewMesh to mesh.origin + meshHeight/2 so the view
    ; frames the body's middle, not the feet. Until then, parked at
    ; the camera-region origin.
    PreviewLookTarget = CreatePivot()
    PositionEntity PreviewLookTarget, LOOM_PREVIEW_CAM_X#, LOOM_PREVIEW_CAM_Y#, LOOM_PREVIEW_CAM_Z#
    HideEntity PreviewLookTarget

    PreviewInitOK = True
    PreviewLastTickMs = MilliSecs()
    WriteLog(LoomLog, "MeshPreview: initialized (RT=" + LOOM_PREVIEW_SIZE + "x" + LOOM_PREVIEW_SIZE + ")")
End Function


; =============================================================================
; Loom_FreePreviewMesh -- free the currently-loaded preview mesh (if any).
; Called when switching to a different mesh ID, and from Loom shutdown.
; =============================================================================
Function Loom_FreePreviewMesh()
    If PreviewMesh <> 0
        FreeEntity PreviewMesh
        PreviewMesh = 0
    EndIf
    PreviewMeshID = -1
End Function


; =============================================================================
; Loom_LoadPreviewMesh -- swap the preview mesh to a new ID. Reuses
; Media.bb's GetMesh which lazy-loads + caches. We pass Duplicate=True
; so we get our own owned entity to position freely. The CACHED entity
; (LoadedMeshes(ID)) stays hidden where Media.bb put it.
; =============================================================================
Function Loom_LoadPreviewMesh(meshID)
    If PreviewInitOK = False Then Return False
    If meshID = PreviewMeshID Then Return (PreviewMesh <> 0)
    If meshID <= 0
        Loom_FreePreviewMesh()
        Return False
    EndIf

    Loom_FreePreviewMesh()

    Local ent = GetMesh(meshID, True)
    If ent = 0
        PreviewMeshID = meshID    ; remember the miss so we don't retry every frame
        Return False
    EndIf

    ; Position the mesh next to the preview camera in the isolated 10000-y
    ; region. The GetMesh-applied scale is already set; we add auto-fit
    ; positioning relative to the camera.
    PositionEntity ent, LOOM_PREVIEW_CAM_X#, LOOM_PREVIEW_CAM_Y#, LOOM_PREVIEW_CAM_Z#
    ShowEntity ent

    ; Texture pass -- when PreviewActorID > 0, the composer is showing
    ; an actor and we should drape the actor's first defined body
    ; texture over the mesh so the preview looks like the actual
    ; character. First cut: whole-entity EntityTexture with body[0];
    ; multi-surface face/body distinction is the follow-up.
    If PreviewActorID > 0 And PreviewActorID < 65536
        Local Ac.Actor = ActorList(PreviewActorID)
        If Ac <> Null
            Loom_ApplyActorTextures(ent, Ac)
        EndIf
    EndIf

    PreviewMesh = ent
    PreviewMeshID = meshID

    ; Reposition the look-target pivot at the character's vertical
    ; midline. MeshHeight returns the unscaled bbox extent in mesh
    ; units; for typical RC actor meshes this is roughly the height
    ; in world units after scale, so half is the centre. Cameras then
    ; PointEntity this pivot and frame the body not the feet.
    Local mh# = MeshHeight#(ent)
    If mh# <= 0.0 Then mh# = 10.0   ; sane default if mesh has no bbox
    PositionEntity PreviewLookTarget, LOOM_PREVIEW_CAM_X#, LOOM_PREVIEW_CAM_Y# + mh# / 2.0, LOOM_PREVIEW_CAM_Z#

    ; New mesh = fresh view. Reset orbit + zoom so the user doesn't
    ; jump into a previous mesh's last camera angle.
    Loom_ResetPreviewOrbit()
    WriteLog(LoomLog, "MeshPreview: loaded mesh ID " + meshID + " (height " + mh# + ")")
    Return True
End Function


; =============================================================================
; Loom_ApplyActorTextures -- drape the actor's body/face textures over the
; loaded mesh. Mirrors a stripped-down version of Actors3D.bb's
; multi-surface paint:
;   - If mesh has 2+ surfaces, paint body onto one and face onto the other,
;     guessed from texture-name "HEAD" hint.
;   - Otherwise EntityTexture the whole entity with body.
; Failures are silent (no toast) -- a missing texture just leaves the
; surface at the mesh's default appearance.
; =============================================================================
Function Loom_ApplyActorTextures(ent, Ac.Actor)
    ; Pick first non-empty body / face slot; bound to 0..4
    Local bodyIdx = 0
    Local faceIdx = 0
    Local bi
    For bi = 0 To 4
        If Ac\MaleBodyIDs[bi] > 0 And Ac\MaleBodyIDs[bi] < 65535 Then bodyIdx = bi : Exit
    Next
    Local fi
    For fi = 0 To 4
        If Ac\MaleFaceIDs[fi] > 0 And Ac\MaleFaceIDs[fi] < 65535 Then faceIdx = fi : Exit
    Next

    Local bodyTexID = Ac\MaleBodyIDs[bodyIdx]
    Local faceTexID = Ac\MaleFaceIDs[faceIdx]

    Local bodyTex = 0
    Local faceTex = 0
    If bodyTexID > 0 And bodyTexID < 65535 Then bodyTex = GetTexture(bodyTexID)
    If faceTexID > 0 And faceTexID < 65535 Then faceTex = GetTexture(faceTexID)

    If CountSurfaces(ent) > 1 And faceTex <> 0
        ; Distinguish head vs body surface via texture-name hint
        ; (mirrors the Actors3D dispatch). Default: surface 1=body, 2=face.
        Local headSurface = GetSurface(ent, 2)
        Local bodySurface = GetSurface(ent, 1)
        Local probeBrush = GetSurfaceBrush(GetSurface(ent, 1))
        Local probeTex = GetBrushTexture(probeBrush)
        Local probeName$ = TextureName$(probeTex)
        If probeTex <> 0 Then FreeTexture probeTex
        If probeBrush <> 0 Then FreeBrush probeBrush
        If Instr(Upper$(probeName$), "HEAD") > 0
            headSurface = GetSurface(ent, 1)
            bodySurface = GetSurface(ent, 2)
        EndIf

        Local brush = CreateBrush()
        If bodyTex <> 0
            BrushTexture brush, bodyTex
            PaintSurface bodySurface, brush
        EndIf
        If faceTex <> 0
            BrushTexture brush, faceTex
            PaintSurface headSurface, brush
        EndIf
        FreeBrush brush
    Else If bodyTex <> 0
        EntityTexture ent, bodyTex
    EndIf

    ; Note: GetTexture returns CopyEntity-like handles; we don't free
    ; them here because the brush still references them. The textures
    ; live until the mesh entity is freed (FreeEntity cascades).
End Function


; =============================================================================
; Loom_DrawMeshPreview -- public entry: ensure the mesh is loaded, tick
; auto-spin, render to RT, paint at (x, y) at (size x size). Falls back to
; the same "?" placeholder ImageCache uses if anything goes wrong.
; =============================================================================
Function Loom_DrawMeshPreview(meshID, x, y, size)
    If PreviewInitOK = False
        Loom_DrawMeshPlaceholder(x, y, size, "init failed")
        Return False
    EndIf

    Local loaded = Loom_LoadPreviewMesh(meshID)
    If loaded = False
        Loom_DrawMeshPlaceholder(x, y, size, "no mesh")
        Return False
    EndIf

    ; ---- Input handling -----------------------------------------------------
    ; Check whether mouse is inside the preview rect. All orbit/zoom
    ; input gated on this so dragging outside the rect doesn't affect
    ; the preview.
    Local mx = MouseX()
    Local my = MouseY()
    Local inside = (mx >= x And mx < x + LOOM_PREVIEW_SIZE And my >= y And my < y + LOOM_PREVIEW_SIZE)

    ; Drag-to-orbit -- LMB held inside the rect rotates the mesh.
    ; Uses MouseDown (not MouseHit/Loom_MouseClicked) since we want
    ; continuous state, not the single press event.
    If MouseDown(1) = True And inside = True
        If PreviewDragging = False
            ; Drag just started -- seed last position so the first
            ; frame's delta is zero (no instant snap).
            PreviewDragging = True
            PreviewLastDragMX = mx
            PreviewLastDragMY = my
        Else
            ; Accumulate delta into manual rotation. 1 pixel = 0.5 deg
            ; gives a comfortable feel without too much sensitivity.
            Local dx = mx - PreviewLastDragMX
            Local dy = my - PreviewLastDragMY
            PreviewManualYaw#   = PreviewManualYaw#   + Float(dx) * 0.5
            PreviewManualPitch# = PreviewManualPitch# + Float(dy) * 0.5
            ; Clamp pitch so the mesh doesn't flip upside-down
            If PreviewManualPitch# > 89.0 Then PreviewManualPitch# = 89.0
            If PreviewManualPitch# < -89.0 Then PreviewManualPitch# = -89.0
            PreviewLastDragMX = mx
            PreviewLastDragMY = my
            PreviewManualActive = True   ; kills auto-spin permanently for this session
        EndIf
    Else
        PreviewDragging = False
    EndIf

    ; Mouse wheel -- adjust camera distance. Uses the cached
    ; LoomFrameMouseWheel so multiple previews on screen don't race
    ; for the wheel state (and the same one preview doesn't
    ; double-consume between input check and render).
    If inside = True
        Local wheel = Loom_MouseWheel()
        If wheel <> 0
            ; Each tick = 5% of current distance. Smooth zoom feel.
            PreviewCamDistance# = PreviewCamDistance# - Float(wheel) * (PreviewCamDistance# * 0.05)
            ; Clamp so we don't zoom into / past the mesh
            If PreviewCamDistance# < 2.0 Then PreviewCamDistance# = 2.0
            If PreviewCamDistance# > 500.0 Then PreviewCamDistance# = 500.0
            ; Reposition the camera at the new distance from the mesh
            PositionEntity PreviewCam, LOOM_PREVIEW_CAM_X#, LOOM_PREVIEW_CAM_Y# + 5.0, LOOM_PREVIEW_CAM_Z# - PreviewCamDistance#
            PointEntity PreviewCam, PreviewLookTarget
            PreviewManualActive = True
            ; Consume the wheel so the composer's scroll handler doesn't
            ; ALSO see the same tick and scroll the body while we zoom.
            Loom_ConsumeWheel()
        EndIf
    EndIf

    ; ---- Rotation: manual override OR auto-spin -----------------------------
    Local nowMs = MilliSecs()
    Local deltaMs = nowMs - PreviewLastTickMs
    If deltaMs < 0 Then deltaMs = 0
    If deltaMs > 1000 Then deltaMs = 1000   ; clamp giant deltas (alt-tab away/back)
    PreviewLastTickMs = nowMs

    If PreviewManualActive = True
        ; User has driven the camera; use their orbit values directly.
        RotateEntity PreviewMesh, PreviewManualPitch#, PreviewManualYaw#, 0
    Else
        ; Auto-spin (delta-time based for consistency under variable frame rate).
        PreviewSpinTime# = PreviewSpinTime# + (Float(deltaMs) / 1000.0) * LOOM_PREVIEW_AUTOSPIN_DEG_PER_SEC#
        If PreviewSpinTime# >= 360.0 Then PreviewSpinTime# = PreviewSpinTime# - 360.0
        RotateEntity PreviewMesh, 0, PreviewSpinTime#, 0
    EndIf

    ; ---- Render directly to the back buffer at the on-screen rect ----------
    ; Render-to-texture (SetBuffer TextureBuffer + RenderWorld) does NOT
    ; capture 3D in this BlitzForge build: RenderWorld lands on the BACK
    ; BUFFER at the camera's viewport regardless of SetBuffer (confirmed from
    ; a user screenshot -- the mesh painted at the top-left 0,0 box while the
    ; CopyRect'd texture stayed black). So aim the camera's viewport AT the
    ; on-screen box and render straight to the back buffer. CameraViewport
    ; confines RenderWorld's clear + draw to the box, leaving the rest of the
    ; 2D composer frame intact.
    CameraViewport PreviewCam, x, y, size, size
    ShowEntity PreviewCam
    RenderWorld
    HideEntity PreviewCam
    ; Brass border on top (used to live in Loom_BlitPreviewTexture).
    LoomBorder x, y, size, size, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B

    ; Hover hint: tiny text in the bottom-left corner of the widget
    ; describing the controls. Only painted when the mouse is inside
    ; so the widget reads as clean when ignored.
    If inside = True
        LoomText x + 6, y + LOOM_PREVIEW_SIZE - 18, "drag=orbit  wheel=zoom", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B
    EndIf

    Return True
End Function


; =============================================================================
; Loom_ResetPreviewOrbit -- restore auto-spin + reset zoom. Called from a
; future "reset view" button (not wired in this iter) and could be
; invoked on mesh change to give a fresh start per actor.
; =============================================================================
Function Loom_ResetPreviewOrbit()
    PreviewManualActive = False
    PreviewManualYaw#   = 0.0
    PreviewManualPitch# = 0.0
    PreviewSpinTime#    = 0.0
    PreviewCamDistance# = LOOM_PREVIEW_CAM_RANGE#
    If PreviewCam <> 0
        PositionEntity PreviewCam, LOOM_PREVIEW_CAM_X#, LOOM_PREVIEW_CAM_Y# + 5.0, LOOM_PREVIEW_CAM_Z# - PreviewCamDistance#
        If PreviewMesh <> 0 Then PointEntity PreviewCam, PreviewLookTarget
    EndIf
End Function


; =============================================================================
; Loom_BlitPreviewTexture -- copy the RT to the BackBuffer at the target
; rect. Blitz3D's DrawImage doesn't take a texture handle, only an Image,
; so we use CopyRect from the texture buffer.
; =============================================================================
Function Loom_BlitPreviewTexture(rt, x, y, size)
    ; CopyRect copies pixels between buffers. Source = the RT's buffer,
    ; dest = the BackBuffer at the requested position. Since RT is
    ; LOOM_PREVIEW_SIZE square and we want it at (x, y) of `size`
    ; pixels, we scale by copying the whole RT then stretching... but
    ; CopyRect doesn't stretch. For first cut, render at exactly
    ; LOOM_PREVIEW_SIZE and ignore the size parameter.
    CopyRect 0, 0, LOOM_PREVIEW_SIZE, LOOM_PREVIEW_SIZE, x, y, TextureBuffer(rt), BackBuffer()

    ; Brass border around the preview so it reads as a discrete widget.
    LoomBorder x, y, LOOM_PREVIEW_SIZE, LOOM_PREVIEW_SIZE, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B
End Function


; =============================================================================
; Loom_DrawMeshPlaceholder -- stone-900 box with "?" + status text for
; missing-mesh / init-failed cases. Same visual shape as ImageCache's
; placeholder so the composer layout stays stable.
; =============================================================================
Function Loom_DrawMeshPlaceholder(x, y, size, note$)
    Local s = LOOM_PREVIEW_SIZE
    If size > 0 Then s = size
    LoomFill x, y, s, s, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B
    LoomBorder x, y, s, s, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B
    LoomText x + (s / 2) - 4, y + (s / 2) - 16, "?", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B
    LoomText x + 8, y + s - 18, note, LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B
End Function


; =============================================================================
; Loom_ShutdownMeshPreview -- free GPU resources on Loom exit. Called from
; Loom.bb after the main loop ends.
; =============================================================================
Function Loom_ShutdownMeshPreview()
    Loom_FreePreviewMesh()
    If PreviewCam <> 0 Then FreeEntity PreviewCam : PreviewCam = 0
    If PreviewLight <> 0 Then FreeEntity PreviewLight : PreviewLight = 0
    If PreviewRT <> 0 Then FreeTexture PreviewRT : PreviewRT = 0
    PreviewInitOK = False
End Function
