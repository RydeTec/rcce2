Strict

// =============================================================================
// Loom/Toasts.bb -- transient bottom-right notifications
// =============================================================================
//
// Before this surface, save/create/delete actions had no visible feedback:
// the dirty asterisk disappeared on save, the focused entity changed on
// create/delete, but nothing said "yes, that worked." For destructive
// actions (Discard, Delete) silent success was particularly jarring.
//
// Toasts pop into the bottom-right corner stacked vertically, each one
// fading out after TOAST_TTL_MS. Four kinds with color cues:
//   "success"  -- arcane-blue border, parchment text  (Save, Create, Save All)
//   "info"     -- brass border, parchment text         (general notices)
//   "warning"  -- warning-orange border                (degraded operations)
//   "danger"   -- danger-red border                    (Delete, errors)
//
// Architecture: Type Toasts holds a BBList of Toast instances. Surfaces
// enqueue via the Toast_Show free function which reaches the singleton
// via LoomToasts (same recorder facade pattern as Timeline / Recents /
// WorldCache -- ADR 005).
//
// Lifecycle: each Toast knows its createdAt MilliSecs(); Toasts::render
// drops any whose age exceeds TOAST_TTL_MS. The last 500ms of the TTL
// fade the alpha so the toast doesn't pop out abruptly. Cap at
// TOAST_MAX_STACKED so a spam of saves doesn't fill the screen.


Const TOAST_TTL_MS       = 3000
Const TOAST_FADE_MS      = 500
Const TOAST_MAX_STACKED  = 5
Const TOAST_W            = 320
Const TOAST_H            = 36
Const TOAST_PAD_X        = 14
Const TOAST_GAP          = 8
Const TOAST_RIGHT_MARGIN = 24
Const TOAST_BOTTOM_MARGIN = 24


// -----------------------------------------------------------------------------
// Toast -- one notification. Allocated by Toast_Show, freed by Toasts::render
// after age exceeds TTL. Manual Delete (no EnableGC in Loom modules).
// -----------------------------------------------------------------------------
Type Toast
    Field Message$
    Field Kind$        // success / info / warning / danger
    Field CreatedAt%
    Field Picked%      // transient: render-pass marker so the repeated-max
                       // walk in Toasts::render doesn't re-pick the same
                       // entry. Cleared at the top of each render call.
End Type


// =============================================================================
// Toasts -- collection + render. Singleton owned by Loom.bb.
// =============================================================================
Type Toasts
    Field count%


    Method create.Toasts()
        self\count = 0
        Return self
    End Method


    // -------------------------------------------------------------------------
    // enqueue -- internal. Called by the Toast_Show facade. Trims oldest
    // when over TOAST_MAX_STACKED so a spam of saves doesn't fill the
    // screen.
    // -------------------------------------------------------------------------
    Method enqueue(message$, kind$)
        Local t.Toast = New Toast()
        t\Message = message
        t\Kind = kind
        t\CreatedAt = MilliSecs()
        self\count = self\count + 1

        // Trim oldest if past cap
        While self\count > TOAST_MAX_STACKED
            Local oldest.Toast = Toasts::findOldest(self)
            If oldest = Null Then Exit
            Delete oldest
            self\count = self\count - 1
        Wend
    End Method


    Method findOldest.Toast()
        Local oldest.Toast = Null
        Local t.Toast
        For t = Each Toast
            If oldest = Null Then oldest = t
            If t\CreatedAt < oldest\CreatedAt Then oldest = t
        Next
        Return oldest
    End Method


    // -------------------------------------------------------------------------
    // render -- paint every live Toast stacked bottom-up at the right
    // edge of the screen; drop any whose age exceeds TTL.
    //
    // Stacking order: newest at the bottom (closest to where the user's
    // attention probably is after taking an action), older above.
    //
    // We walk the type pool twice: once to garbage-collect expired,
    // once to render. Cheap at N <= TOAST_MAX_STACKED = 5.
    // -------------------------------------------------------------------------
    Method render(sw%, sh%)
        // Garbage-collect expired
        Local t.Toast
        For t = Each Toast
            If (MilliSecs() - t\CreatedAt) >= TOAST_TTL_MS
                Delete t
                self\count = self\count - 1
            EndIf
        Next

        If self\count = 0 Then Return

        // Reset Picked across the pool so repeated-max can run fresh.
        Local resetT.Toast
        For resetT = Each Toast
            resetT\Picked = False
        Next

        // Render newest first at the bottom; older above. Same repeated-
        // max-with-Picked pattern as PaletteResult.
        Local slot% = 0
        For slot = 0 To self\count - 1
            Local newest.Toast = Null
            Local newestAt% = -1
            Local cand.Toast
            For cand = Each Toast
                If cand\Picked = False And cand\CreatedAt > newestAt
                    newest = cand
                    newestAt = cand\CreatedAt
                EndIf
            Next
            If newest = Null Then Exit
            newest\Picked = True

            Local ty% = sh - TOAST_BOTTOM_MARGIN - (slot + 1) * (TOAST_H + TOAST_GAP)
            Local tx% = sw - TOAST_W - TOAST_RIGHT_MARGIN
            Toasts::drawOne(self, newest, tx, ty)
        Next
    End Method


    // -------------------------------------------------------------------------
    // drawOne -- paint a single toast at (x, y). Color cues per kind.
    // Fade-out via reduced alpha equivalent (we don't have alpha so we
    // mix toward background by drawing a partial overlay) -- skipped
    // here, just hard-disappear on TTL expiry. Adequate for v1.
    // -------------------------------------------------------------------------
    Method drawOne(t.Toast, x%, y%)
        // Background -- slightly transparent-looking via stone-800 over
        // the existing surface
        LoomShadowCard(x, y, TOAST_W, TOAST_H)
        LoomFill(x, y, TOAST_W, TOAST_H, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)

        // Border in the kind's color
        LoomBorder(x, y, TOAST_W, TOAST_H, Toasts::kindR(self, t\Kind), Toasts::kindG(self, t\Kind), Toasts::kindB(self, t\Kind))
        LoomBorder(x + 1, y + 1, TOAST_W - 2, TOAST_H - 2, Toasts::kindR(self, t\Kind), Toasts::kindG(self, t\Kind), Toasts::kindB(self, t\Kind))

        // Left brass accent stripe
        LoomFill(x, y, 3, TOAST_H, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        // Message text (truncate if too long for the toast width)
        Local maxChars% = 40
        Local shown$ = t\Message
        If Len(shown) > maxChars Then shown = Left$(shown, maxChars - 2) + ".."
        LoomText(x + TOAST_PAD_X, y + 10, shown, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
    End Method


    // -------------------------------------------------------------------------
    // kindR / G / B -- color cue per kind. Lifted to Methods to dodge
    // the Strict-mode reassign-Local-from-nested-If trap (same pattern
    // as ExitPrompt::actionR/G/B%, Timeline::actionGlyph$).
    // -------------------------------------------------------------------------
    Method kindR%(kind$)
        If kind = "success" Then Return LOOM_ARCANE_500_R
        If kind = "warning" Then Return LOOM_WARNING_R
        If kind = "danger"  Then Return LOOM_DANGER_R
        Return LOOM_BRASS_500_R
    End Method

    Method kindG%(kind$)
        If kind = "success" Then Return LOOM_ARCANE_500_G
        If kind = "warning" Then Return LOOM_WARNING_G
        If kind = "danger"  Then Return LOOM_DANGER_G
        Return LOOM_BRASS_500_G
    End Method

    Method kindB%(kind$)
        If kind = "success" Then Return LOOM_ARCANE_500_B
        If kind = "warning" Then Return LOOM_WARNING_B
        If kind = "danger"  Then Return LOOM_DANGER_B
        Return LOOM_BRASS_500_B
    End Method
End Type


// =============================================================================
// Module-level facade. Mirror of LoomTimeline / LoomRecents / LoomWorldCache
// shape (ADR 005). Surfaces call Toast_Show without needing the instance.
// =============================================================================
Global LoomToasts.Toasts = Null


Function Toast_Show(message$, kind$)
    If LoomToasts = Null Then Return
    Toasts::enqueue(LoomToasts, message, kind)
End Function
