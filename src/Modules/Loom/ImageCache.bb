; =============================================================================
; Loom/ImageCache.bb -- 2D image (texture thumbnail) loader + cache
; =============================================================================
;
; Designers want to SEE what a texture looks like, not just its integer ID.
; This module lazy-loads textures via the existing Media.bb's
; GetTextureName$(ID) + Blitz3D's LoadImage, scales each to a fixed
; thumbnail size via CopyImage + ScaleImage, caches the scaled handle
; by (ID, size), and exposes a stable handle so render code can call
; DrawImage on it.
;
; Why two caches (small 32x32 + large 64x64) rather than one + runtime scale:
; Blitz3D's DrawImage does NOT scale -- it paints at the image's native
; size. Iter 34's first cut tried to "scale at draw time" which silently
; rendered every texture at its native pixel size (a 256x256 fireball
; texture exploded across the composer). The fix is per-size cached
; CopyImage+ScaleImage'd handles; drawing then uses DrawImage on the
; pre-scaled handle.
;
; Per-frame load budget: synchronous LoadImage is slow (10-50ms per disk
; seek). The first frame on a new browser tab with 50 visible Item cards
; would freeze for a second or more, during which the user's clicks
; appear "lost" (input still arrives but the frame didn't progress).
; LOOM_IMAGE_LOADS_PER_FRAME bounds how many fresh loads any one frame
; can do; over-budget requests return 0 (placeholder paints) and try
; again next frame. After ~30 frames the cache is warm and rendering is
; instant.
;
; Non-Strict file (matches Settings.bb / Recents.bb).

Const IMAGE_CACHE_MISS = -1
Const LOOM_IMAGE_LOADS_PER_FRAME = 3

Const LOOM_THUMB_SMALL = 32
Const LOOM_THUMB_LARGE = 64

; Two parallel caches per texture ID.
; Slot value: 0 = not tried, -1 (IMAGE_CACHE_MISS) = tried + failed, >0 = handle.
Dim ImageCacheSmall(65535)
Dim ImageCacheLarge(65535)

; Per-frame load counter. Loom.bb's renderFrame calls Loom_BeginFrame() at
; top to reset; each first-time load increments. Over-budget = return 0.
Global FrameImageLoadCount = 0

; =============================================================================
; Per-frame mouse cache. Blitz3D's MouseHit(b) is READ-AND-CLEAR -- the
; first caller in a frame consumes the press count; every subsequent
; caller sees 0. With 11+ surfaces (Browser / Composer / Ribbon / Atlas /
; 6 modals / SaveAll) all calling MouseHit(1) independently, clicks
; on anything except the FIRST surface to call MouseHit got silently
; eaten. Symptom: "have to click 3 times for the composer to register".
;
; Fix: cache MouseHit(1) + MouseHit(2) once at the top of each frame
; via Loom_BeginFrame(); every surface reads the cached value via
; Loom_MouseClicked() / Loom_MouseRightClicked() instead of calling
; MouseHit themselves. Re-entrant safe (just reads a Global).
; =============================================================================
Global LoomFrameMouseClicked      = False
Global LoomFrameMouseRightClicked = False
; MouseZ() returns the CUMULATIVE wheel position (a running total since
; program start), NOT a per-frame delta -- verified in BlitzForge's
; gxinput.cpp (wm_mousewheel does axis_states[2] += dz; MouseZ reads
; that accumulator without resetting). The earlier code treated MouseZ
; as a delta and multiplied it by the scroll step every frame, so a
; single non-zero wheel position drove scrollOffset to its clamp on
; every subsequent frame -- the "won't scroll, then jumps to the
; bottom" bug. Fix: cache the per-frame DELTA here (current absolute
; minus last frame's absolute) so Loom_MouseWheel() returns true ticks.
Global LoomFrameMouseWheel        = 0
Global LoomPrevMouseWheelAbs      = 0

; Zone viewport <-> composer highlight sync. Composer's renderZone
; sub-section renderers set these when their header lands inside
; the visible body region; ZoneViewport reads them and scales the
; matching marker bigger. Declared here (before Composer.bb in the
; include order) so Strict Composer can write them.
Global LoomZoneHighlightKind$ = ""
Global LoomZoneHighlightIdx   = -1

; Actor-context publish for MeshPreview: composer sets this to the
; current actor's ID before Loom_DrawMeshPreview so the loader can
; drape the actor's body/face textures over the bare mesh. Declared
; here for the same Strict-mode write reason.
Global PreviewActorID = 0


; =============================================================================
; Loom_BeginFrame -- called once per frame at the top of renderFrame.
; Resets the image-load budget AND captures the frame's mouse-click
; state once so every renderAndUpdate sees the same value.
; =============================================================================
Function Loom_BeginFrame()
    FrameImageLoadCount = 0
    ; Capture mouse buttons once. > 0 = pressed this frame; we treat
    ; multi-press (rare at human click rate) as a single True since
    ; rendering pipelines aren't equipped to handle counts.
    LoomFrameMouseClicked      = (MouseHit(1) > 0)
    LoomFrameMouseRightClicked = (MouseHit(2) > 0)
    ; Per-frame wheel delta = current cumulative position - last frame's.
    ; MouseZ() never resets, so this is the only correct way to read it.
    Local rawWheel% = MouseZ()
    LoomFrameMouseWheel = rawWheel - LoomPrevMouseWheelAbs
    LoomPrevMouseWheelAbs = rawWheel
End Function


; Read-only accessors for the captured state. Renderers MUST use these
; instead of calling MouseHit directly, or the bug returns.
Function Loom_MouseClicked%()
    Return LoomFrameMouseClicked
End Function

Function Loom_MouseRightClicked%()
    Return LoomFrameMouseRightClicked
End Function

Function Loom_MouseWheel%()
    Return LoomFrameMouseWheel
End Function

; Loom_ConsumeWheel -- zero the per-frame wheel cache so later
; surfaces in the same frame see 0. Used by viewports (MeshPreview,
; ZoneViewport, Atlas) when the cursor is inside their rect and
; they're handling the wheel for zoom -- prevents the composer's
; scroll handler from ALSO consuming the same tick.
Function Loom_ConsumeWheel()
    LoomFrameMouseWheel = 0
End Function


; =============================================================================
; Chrome immersion toggle (Tool / Balanced / In-world). Three-mode slider
; from the original design (roadmap #2). Session-only for this first cut;
; persistence comes in a follow-up via Settings.bb / Loom_SaveSettings.
;
; Each renderer that wants to vary its chrome by mode calls one of the
; predicate helpers (Loom_ChromeIsTool / Loom_ChromeIsInWorld) and
; branches inline. The default ("balanced") matches the pre-toggle
; rendering exactly, so no behavior change for users who never click
; the toggle.
; =============================================================================
Global LoomChromeMode$ = "balanced"

Function Loom_ChromeMode$()
    Return LoomChromeMode$
End Function

Function Loom_ChromeIsTool%()
    Return (LoomChromeMode$ = "tool")
End Function

Function Loom_ChromeIsBalanced%()
    Return (LoomChromeMode$ = "balanced")
End Function

Function Loom_ChromeIsInWorld%()
    Return (LoomChromeMode$ = "in-world")
End Function

Function Loom_CycleChromeMode()
    If LoomChromeMode$ = "tool"
        LoomChromeMode$ = "balanced"
    Else If LoomChromeMode$ = "balanced"
        LoomChromeMode$ = "in-world"
    Else
        LoomChromeMode$ = "tool"
    EndIf
End Function


; =============================================================================
; Loom_ConsumeClick -- mark the current frame's click as handled so no
; subsequent renderer in this frame reacts to it. Call this when an action
; fires that conceptually "uses up" the click, e.g. openModal triggered
; from a ribbon button.
;
; Why it's needed: before the MouseHit-cache hotfix, Blitz3D's
; MouseHit(b) drained on first call -- the modal that opened from a
; ribbon click never saw the click again, because Ribbon::MouseHit had
; already drained it. With the cache, every surface sees the SAME
; click; the newly-opened modal's "click outside modal = close"
; check then fires on the very click that opened it. The modal opens
; and immediately closes on the same frame.
;
; Sites: every openModal that's triggered by a click (BrokenRefs /
; Palette / Timeline / Recents / Help / ExitPrompt). Keyboard
; activations (Ctrl+K / F1 etc) don't need this since they don't set
; LoomFrameMouseClicked in the first place.
; =============================================================================
Function Loom_ConsumeClick()
    LoomFrameMouseClicked      = False
    LoomFrameMouseRightClicked = False
End Function


; =============================================================================
; Loom_GetThumbnailSized -- internal helper that returns a cached image
; handle pre-scaled to (size x size). Lazy loads + scales on first
; request per (ID, size). Returns 0 if can't be loaded (missing file,
; over-budget, etc) so the caller can paint a placeholder.
; =============================================================================
Function Loom_GetThumbnailSized(ID, size)
    If ID < 0 Or ID > 65534 Then Return 0
    If size <= 0 Then Return 0

    ; Pick the right cache slot
    Local cached
    If size = LOOM_THUMB_SMALL
        cached = ImageCacheSmall(ID)
    Else
        cached = ImageCacheLarge(ID)
    EndIf

    If cached > 0 Then Return cached
    If cached = IMAGE_CACHE_MISS Then Return 0

    ; Over-budget for this frame? Return 0 -- placeholder paints, retry
    ; next frame. Important: do NOT cache IMAGE_CACHE_MISS here; we
    ; want to attempt the load again next frame.
    If FrameImageLoadCount >= LOOM_IMAGE_LOADS_PER_FRAME Then Return 0

    ; First attempt: filename from index file.
    Local NameAndFlags$ = GetTextureName$(ID)
    If NameAndFlags = ""
        If size = LOOM_THUMB_SMALL
            ImageCacheSmall(ID) = IMAGE_CACHE_MISS
        Else
            ImageCacheLarge(ID) = IMAGE_CACHE_MISS
        EndIf
        Return 0
    EndIf

    ; GetTextureName$ returns filename + Chr$(flags); strip the trailing byte.
    Local NameLen = Len(NameAndFlags) - 1
    If NameLen < 1
        If size = LOOM_THUMB_SMALL
            ImageCacheSmall(ID) = IMAGE_CACHE_MISS
        Else
            ImageCacheLarge(ID) = IMAGE_CACHE_MISS
        EndIf
        Return 0
    EndIf
    Local Name$ = Left$(NameAndFlags, NameLen)
    If Name = ""
        If size = LOOM_THUMB_SMALL
            ImageCacheSmall(ID) = IMAGE_CACHE_MISS
        Else
            ImageCacheLarge(ID) = IMAGE_CACHE_MISS
        EndIf
        Return 0
    EndIf

    If Instr(Name$, "..") > 0
        If size = LOOM_THUMB_SMALL
            ImageCacheSmall(ID) = IMAGE_CACHE_MISS
        Else
            ImageCacheLarge(ID) = IMAGE_CACHE_MISS
        EndIf
        Return 0
    EndIf

    Local FullPath$ = "Data\Textures\" + Name$
    If FileType(FullPath$) <> 1
        If size = LOOM_THUMB_SMALL
            ImageCacheSmall(ID) = IMAGE_CACHE_MISS
        Else
            ImageCacheLarge(ID) = IMAGE_CACHE_MISS
        EndIf
        Return 0
    EndIf

    Local original = LoadImage(FullPath$)
    If original = 0
        If size = LOOM_THUMB_SMALL
            ImageCacheSmall(ID) = IMAGE_CACHE_MISS
        Else
            ImageCacheLarge(ID) = IMAGE_CACHE_MISS
        EndIf
        Return 0
    EndIf

    ; Scale a COPY (ScaleImage is destructive in place; if we scaled the
    ; original, asking for the other size next would scale-of-scale).
    Local scaled = CopyImage(original)
    Local iw = ImageWidth(original)
    Local ih = ImageHeight(original)
    FreeImage(original)

    If iw <= 0 Or ih <= 0
        If scaled <> 0 Then FreeImage(scaled)
        If size = LOOM_THUMB_SMALL
            ImageCacheSmall(ID) = IMAGE_CACHE_MISS
        Else
            ImageCacheLarge(ID) = IMAGE_CACHE_MISS
        EndIf
        Return 0
    EndIf

    ; Uniform scale -- fit within (size x size), preserve aspect.
    Local fitScale# = Float(size) / Float(iw)
    Local fitScaleY# = Float(size) / Float(ih)
    If fitScaleY# < fitScale# Then fitScale# = fitScaleY#

    ScaleImage scaled, fitScale#, fitScale#

    ; Cache + budget book-keeping
    If size = LOOM_THUMB_SMALL
        ImageCacheSmall(ID) = scaled
    Else
        ImageCacheLarge(ID) = scaled
    EndIf
    FrameImageLoadCount = FrameImageLoadCount + 1
    Return scaled
End Function


; =============================================================================
; Loom_DrawThumbnailSmall -- 32x32 thumbnail centered in a 32x32 box at (x, y).
; Browser cards use this size.
; =============================================================================
Function Loom_DrawThumbnailSmall(ID, x, y)
    Local img = Loom_GetThumbnailSized(ID, LOOM_THUMB_SMALL)
    If img = 0
        Loom_DrawThumbnailPlaceholder(x, y, LOOM_THUMB_SMALL)
        Return False
    EndIf
    ; Center the scaled image inside the 32x32 box (uniform scale may
    ; produce w<32 or h<32 if the source isn't square).
    Local iw = ImageWidth(img)
    Local ih = ImageHeight(img)
    Local dx = x + (LOOM_THUMB_SMALL - iw) / 2
    Local dy = y + (LOOM_THUMB_SMALL - ih) / 2
    DrawImage img, dx, dy
    Return True
End Function


; =============================================================================
; Loom_DrawThumbnailLarge -- 64x64 thumbnail centered in a 64x64 box at (x, y).
; Composer Visuals row uses this size.
; =============================================================================
Function Loom_DrawThumbnailLarge(ID, x, y)
    Local img = Loom_GetThumbnailSized(ID, LOOM_THUMB_LARGE)
    If img = 0
        Loom_DrawThumbnailPlaceholder(x, y, LOOM_THUMB_LARGE)
        Return False
    EndIf
    Local iw = ImageWidth(img)
    Local ih = ImageHeight(img)
    Local dx = x + (LOOM_THUMB_LARGE - iw) / 2
    Local dy = y + (LOOM_THUMB_LARGE - ih) / 2
    DrawImage img, dx, dy
    Return True
End Function


; =============================================================================
; Loom_DrawThumbnailPlaceholder -- stone-900 box with brass-700 border + "?"
; for missing/over-budget IDs. Same visual shape at every size so the
; layout is consistent.
; =============================================================================
Function Loom_DrawThumbnailPlaceholder(x, y, size)
    LoomFill(x, y, size, size, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B)
    LoomBorder(x, y, size, size, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
    LoomText(x + (size / 2) - 4, y + (size / 2) - 8, "?", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
End Function


; =============================================================================
; Loom_DrawImageScaled -- back-compat shim for any caller that hasn't been
; updated to the new sized API. Routes to whichever size cache matches
; the requested rect (or large as fallback). Renderers should prefer
; Loom_DrawThumbnailSmall / Loom_DrawThumbnailLarge for honest sizing.
; =============================================================================
Function Loom_DrawImageScaled(ID, x, y, w, h)
    If w <= LOOM_THUMB_SMALL And h <= LOOM_THUMB_SMALL
        Return Loom_DrawThumbnailSmall(ID, x, y)
    EndIf
    Return Loom_DrawThumbnailLarge(ID, x, y)
End Function
