Strict

// =============================================================================
// Loom/Timeline.bb -- session edit history with click-to-revert
// =============================================================================
//
// The design's #5 signature surface (README.md): "Session timeline scrubber
// - visible history of the current session's edits, with a draggable
// handle to rewind."
//
// What this surface does:
//   - Records every in-memory mutation that flows through the Composer or
//     EntityFactory: field edits (text / numeric / bool), entity creates,
//     entity deletes.
//   - On Ctrl+H, opens a modal showing entries chronologically (newest
//     first). Each entry shows time-since, who changed what, before/after.
//   - Click a revert affordance on an EDIT entry to write the old value
//     back via the existing Composer::writeField + markDirtyForKind path.
//     Create / delete entries are recorded but not revertable in this
//     iteration -- the entity is gone (delete) or has been edited since
//     create (create) so a simple revert would lose work.
//
// Why not a full undo stack: a real undo would need a sequence-aware
// revert (a later edit of the same field could depend on the new value
// before being itself undone). That's a session-state model unto itself.
// Per-entry click-to-revert covers the dominant "I just made a typo"
// case without that overhead.
//
// Capacity: ring-buffered at TIMELINE_MAX_ENTRIES so a long session
// doesn't grow the type pool unbounded. Oldest entries fall off the end.
//
// Architecture: Type with Methods + a free-function recorder facade
// (Timeline_Record*) that the Composer / EntityFactory call without
// needing a Timeline reference. The facade reaches the single instance
// via a Global LoomTimeline pointer that Loom.bb sets at boot.


Const TIMELINE_MAX_ENTRIES   = 200
Const TIMELINE_MODAL_W       = 700
Const TIMELINE_MODAL_H       = 480
Const TIMELINE_PAD           = 16
Const TIMELINE_ROW_H         = 24
Const TIMELINE_HEADER_H      = 32
Const TIMELINE_HINT_H        = 24


// Entry action types -- string constants because Strict doesn't allow
// enums and the values appear in WriteLog output.
Const TLE_EDIT    = "edit"
Const TLE_TOGGLE  = "toggle"
Const TLE_CREATE  = "create"
Const TLE_DELETE  = "delete"


// -----------------------------------------------------------------------------
// TimelineEntry -- one recorded action. Allocated by Timeline_Record*;
// freed by Timeline::trim or Timeline::clear. Manual Delete (no EnableGC
// in Loom modules) per the established pattern.
// -----------------------------------------------------------------------------
Type TimelineEntry
    Field Action$       // TLE_EDIT / TLE_TOGGLE / TLE_CREATE / TLE_DELETE
    Field Kind$         // entity kind (actor / item / spell / zone / faction / animset)
    Field RefID%        // entity ID
    Field FieldId$      // field name (edits/toggles), "" for create/delete
    Field OldValue$     // pre-change value (string-encoded)
    Field NewValue$     // post-change value
    Field Label$        // cached entity display name at record time
    Field At%           // MilliSecs() at record time
End Type


// =============================================================================
// Timeline -- the session history surface.
// =============================================================================
Type Timeline
    Field composer.Composer    // for revert dispatch via Composer::writeField

    Field open%
    Field entryCount%
    Field scrollOffset%        // top entry index shown (newest=0)


    Method create.Timeline()
        self\composer = Null
        self\open = False
        self\entryCount = 0
        self\scrollOffset = 0
        Return self
    End Method


    Method setComposer(composer.Composer)
        self\composer = composer
    End Method


    Method isOpen%()
        Return self\open
    End Method


    Method openModal()
        self\open = True
        self\scrollOffset = 0
        FlushKeys
        Loom_ConsumeClick()
        WriteLog(LoomLog, "Timeline: open (" + Str(self\entryCount) + " entries)")
    End Method


    Method closeModal()
        self\open = False
        WriteLog(LoomLog, "Timeline: close")
    End Method


    // -------------------------------------------------------------------------
    // record -- internal append. Trims oldest if over cap. Mark entries
    // by inserting newest-LAST in the type pool; render walks the pool
    // backward to show newest-first.
    // -------------------------------------------------------------------------
    Method record(action$, kind$, refID%, fieldId$, oldValue$, newValue$, label$)
        Local e.TimelineEntry = New TimelineEntry()
        e\Action = action
        e\Kind = kind
        e\RefID = refID
        e\FieldId = fieldId
        e\OldValue = oldValue
        e\NewValue = newValue
        e\Label = label
        e\At = MilliSecs()
        self\entryCount = self\entryCount + 1

        // Trim oldest (head of the pool) until at cap.
        While self\entryCount > TIMELINE_MAX_ENTRIES
            Local victim.TimelineEntry = First TimelineEntry
            If victim = Null Then Exit
            Delete victim
            self\entryCount = self\entryCount - 1
        Wend
    End Method


    // -------------------------------------------------------------------------
    // renderAndUpdate -- per-frame paint + input. Returns True when open
    // (so the outer Loom frame knows to skip its own Esc handler).
    // -------------------------------------------------------------------------
    Method renderAndUpdate%(sw%, sh%)
        If self\open = False Then Return False

        Timeline::pumpKeyboard(self)
        If self\open = False Then Return True

        // Dim background, draw centered modal
        LoomFill(0, 0, sw, sh, LOOM_STONE_950_R, LOOM_STONE_950_G, LOOM_STONE_950_B)

        Local mx% = MouseX()
        Local my% = MouseY()
        Local clicked% = Loom_MouseClicked()

        Local modalX% = (sw - TIMELINE_MODAL_W) / 2
        Local modalY% = (sh - TIMELINE_MODAL_H) / 3

        // Chrome
        LoomShadowCard(modalX, modalY, TIMELINE_MODAL_W, TIMELINE_MODAL_H)
        // Modal backdrop varies by chrome mode (mirrors Composer::
        // renderAndUpdate): tool=flat, balanced=subtle gradient,
        // in-world=dramatic gradient + brass bottom-rule ornament.
        If Loom_ChromeIsTool() = True
            LoomFill(modalX, modalY, TIMELINE_MODAL_W, TIMELINE_MODAL_H, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B)
        Else If Loom_ChromeIsInWorld() = True
            LoomGradientV(modalX, modalY, TIMELINE_MODAL_W, TIMELINE_MODAL_H, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B, LOOM_STONE_950_R, LOOM_STONE_950_G, LOOM_STONE_950_B)
        Else
            LoomGradientV(modalX, modalY, TIMELINE_MODAL_W, TIMELINE_MODAL_H, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B)
        EndIf
        LoomBorder(modalX, modalY, TIMELINE_MODAL_W, TIMELINE_MODAL_H, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomBorder(modalX + 1, modalY + 1, TIMELINE_MODAL_W - 2, TIMELINE_MODAL_H - 2, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        LoomFill(modalX, modalY, TIMELINE_MODAL_W, 3, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        If Loom_ChromeIsInWorld() = True Then LoomFill(modalX, modalY + TIMELINE_MODAL_H - 3, TIMELINE_MODAL_W, 3, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        // Header in display font
        LoomTheme_UseDisplay()
        LoomText(modalX + TIMELINE_PAD, modalY + 6, "SESSION TIMELINE  |  " + Str(self\entryCount) + " entries", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomTheme_UseBody()

        // Entry list (newest first -- walk type pool backward via After
        // /  Before, which Blitz3D exposes through Last + Before).
        Timeline::drawEntries(self, modalX, modalY + TIMELINE_HEADER_H, mx, my, clicked)

        // Footer hint
        Local hy% = modalY + TIMELINE_MODAL_H - TIMELINE_HINT_H - 4
        LoomHRule(modalX + TIMELINE_PAD, hy - 2, TIMELINE_MODAL_W - TIMELINE_PAD * 2, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        LoomText(modalX + TIMELINE_PAD, hy + 4, "Click revert on a row to undo  |  scroll / arrows  |  Esc to close", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)

        // Click-outside-modal closes
        If clicked = True
            If mx < modalX Or mx >= modalX + TIMELINE_MODAL_W Or my < modalY Or my >= modalY + TIMELINE_MODAL_H
                Timeline::closeModal(self)
            EndIf
        EndIf

        Return True
    End Method


    Method drawEntries(modalX%, listY%, mx%, my%, clicked%)
        Local listH% = TIMELINE_MODAL_H - TIMELINE_HEADER_H - TIMELINE_HINT_H - 12
        Local rowsVisible% = listH / TIMELINE_ROW_H
        Local rx% = modalX + TIMELINE_PAD
        Local rw% = TIMELINE_MODAL_W - TIMELINE_PAD * 2

        If self\entryCount = 0
            LoomText(rx, listY + 12, "No edits yet this session.", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
            Return
        EndIf

        // Walk pool newest-first via Last + Before. Skip scrollOffset
        // entries; render up to rowsVisible.
        Local skipped% = 0
        Local shown% = 0
        Local e.TimelineEntry = Last TimelineEntry
        While e <> Null
            If skipped < self\scrollOffset
                skipped = skipped + 1
            Else
                If shown >= rowsVisible Then Exit
                Local ry% = listY + shown * TIMELINE_ROW_H
                Timeline::drawOneEntry(self, e, rx, ry, rw, mx, my, clicked)
                shown = shown + 1
            EndIf
            e = Before e
        Wend

        // Scrollbar thumb in the right margin (between the rows and the
        // modal border) whenever the history overflows the visible band.
        // Gives the wheel/arrow scroll a position indicator -- every other
        // Loom scroll region (Composer body, browser grid) has one.
        If self\entryCount > rowsVisible
            Timeline::drawScrollbar(self, rx + rw + 4, listY, rowsVisible * TIMELINE_ROW_H)
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // drawScrollbar -- thin brass thumb in row space (mirrors Composer::
    // drawScrollbar, which works in pixel space). Track spans the visible
    // band; thumb height + position reflect rowsVisible / entryCount and the
    // current scrollOffset. Only called when the list overflows.
    // -------------------------------------------------------------------------
    Method drawScrollbar(barX%, barTopY%, barH%)
        If self\entryCount <= 0 Then Return

        // Track
        LoomFill(barX, barTopY, 4, barH, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)

        // Thumb height proportional to the visible fraction of the content;
        // floored at 16px so it stays grabbable with a long history.
        Local thumbH% = (barH * barH) / (self\entryCount * TIMELINE_ROW_H)
        If thumbH < 16 Then thumbH = 16
        If thumbH > barH Then thumbH = barH

        // Thumb y: scrollOffset (rows) mapped into track travel.
        Local maxScroll% = Timeline::maxScroll(self)
        Local travelTrack% = barH - thumbH
        Local thumbY% = barTopY
        If maxScroll > 0 Then thumbY = barTopY + (self\scrollOffset * travelTrack) / maxScroll

        LoomFill(barX, thumbY, 4, thumbH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
    End Method


    Method drawOneEntry(e.TimelineEntry, rx%, ry%, rw%, mx%, my%, clicked%)
        // Background alternation for readability
        Local hovered% = (mx >= rx And mx < rx + rw And my >= ry And my < ry + TIMELINE_ROW_H)
        If hovered = True
            LoomFill(rx, ry, rw, TIMELINE_ROW_H, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
        EndIf

        // Time-since (MM:SS ago)
        Local ageMs% = MilliSecs() - e\At
        Local ageSec% = ageMs / 1000
        Local ageStr$ = Timeline::formatAge(self, ageSec)
        LoomText(rx + 6, ry + 4, ageStr, LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)

        // Action glyph -- via helper Method to avoid the Strict-mode
        // reassign-Local-from-nested-If trap (architecture.md).
        LoomText(rx + 60, ry + 4, Timeline::actionGlyph(self, e\Action), LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        // Kind#ID + label + field
        Local body$ = e\Kind + "#" + Str(e\RefID) + " " + Chr(34) + e\Label + Chr(34)
        If e\FieldId <> "" Then body = body + " . " + e\FieldId
        LoomText(rx + 130, ry + 4, body, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        // Before -> after preview (truncated)
        If e\Action = TLE_EDIT Or e\Action = TLE_TOGGLE
            Local diff$ = Timeline::truncate(self, e\OldValue, 14) + " -> " + Timeline::truncate(self, e\NewValue, 14)
            LoomText(rx + 380, ry + 4, diff, LOOM_STONE_200_R, LOOM_STONE_200_G, LOOM_STONE_200_B)

            // Revert button on the right
            Local revX% = rx + rw - 70
            Local revY% = ry + 2
            Local revW% = 64
            Local revH% = TIMELINE_ROW_H - 4
            Local revHover% = (mx >= revX And mx < revX + revW And my >= revY And my < revY + revH)

            If revHover = True
                LoomFill(revX, revY, revW, revH, LOOM_ARCANE_700_R, LOOM_ARCANE_700_G, LOOM_ARCANE_700_B)
                LoomBorder(revX, revY, revW, revH, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
            Else
                LoomBorder(revX, revY, revW, revH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            EndIf
            LoomText(revX + 14, revY + 2, "Revert", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

            If revHover And clicked
                Timeline::revertEntry(self, e)
            EndIf
        Else
            // Create/Delete -- no revert in this iteration
            LoomText(rx + rw - 110, ry + 4, "(revert n/a)", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // revertEntry -- write the old value back to the field via the
    // existing Composer::writeField dispatch. Skips when composer is
    // unwired (defensive; Loom.bb wires it at boot).
    //
    // Doesn't itself record a reciprocal entry (to avoid spam); the user
    // sees the result in the composer immediately and the field will look
    // like its pre-edit state.
    // -------------------------------------------------------------------------
    Method revertEntry(e.TimelineEntry)
        If self\composer = Null
            WriteLog(LoomLog, "Timeline: revert -- composer not wired")
            Return
        EndIf
        Composer::writeField(self\composer, e\Kind, e\RefID, e\FieldId, e\OldValue)
        Composer::markDirtyForKind(self\composer, e\Kind)
        // Reverts mutate the same shared entity state as composer edits.
        WorldCache_Invalidate()
        WriteLog(LoomLog, "Timeline: reverted " + e\Kind + "#" + Str(e\RefID) + "." + e\FieldId + " back to " + Chr(34) + e\OldValue + Chr(34))
    End Method


    Method pumpKeyboard()
        If KeyHit(1)
            Timeline::closeModal(self)
            Return
        EndIf

        // Arrow keys nudge one row.
        If KeyHit(200) Then self\scrollOffset = self\scrollOffset - 1
        If KeyHit(208) Then self\scrollOffset = self\scrollOffset + 1

        // Mouse wheel scroll. Loom_MouseWheel() is the per-frame DELTA
        // (Loom_BeginFrame derives it from MouseZ's cumulative value);
        // wheel-up is positive and scrolls toward the newest entry, matching
        // the Composer body + browser grid convention. Consume the tick so a
        // surface painted earlier this frame can't also act on it.
        Local wheel% = Loom_MouseWheel()
        If wheel <> 0
            self\scrollOffset = self\scrollOffset - wheel
            Loom_ConsumeWheel()
        EndIf

        // Clamp to [0, maxScroll] so the last page sits flush against the
        // footer instead of scrolling into empty space (keeps the scrollbar
        // thumb honest).
        Local maxScroll% = Timeline::maxScroll(self)
        If self\scrollOffset > maxScroll Then self\scrollOffset = maxScroll
        If self\scrollOffset < 0 Then self\scrollOffset = 0
    End Method


    // -------------------------------------------------------------------------
    // maxScroll -- highest valid scrollOffset: entryCount minus the rows that
    // fit in the list band, floored at 0. Shared by pumpKeyboard's clamp and
    // the scrollbar thumb geometry so the two never disagree.
    // -------------------------------------------------------------------------
    Method maxScroll%()
        Local listH% = TIMELINE_MODAL_H - TIMELINE_HEADER_H - TIMELINE_HINT_H - 12
        Local rowsVisible% = listH / TIMELINE_ROW_H
        Local m% = self\entryCount - rowsVisible
        If m < 0 Then m = 0
        Return m
    End Method


    Method actionGlyph$(action$)
        If action = TLE_CREATE Then Return "[+] new"
        If action = TLE_DELETE Then Return "[X] del"
        If action = TLE_TOGGLE Then Return "[~] flip"
        Return "[E] edit"
    End Method


    Method formatAge$(sec%)
        If sec < 60 Then Return Str(sec) + "s ago"
        Local mins% = sec / 60
        Local rem% = sec Mod 60
        If mins < 60 Then Return Str(mins) + "m " + Str(rem) + "s"
        Local hrs% = mins / 60
        Return Str(hrs) + "h " + Str(mins Mod 60) + "m"
    End Method


    Method truncate$(s$, maxLen%)
        If Len(s) <= maxLen Then Return s
        Return Left$(s, maxLen - 2) + ".."
    End Method
End Type


// =============================================================================
// Module-level recorder facade. The Composer / EntityFactory call these
// without needing a Timeline reference; LoomTimeline is the singleton
// pointer Loom.bb sets at boot.
// =============================================================================
Global LoomTimeline.Timeline = Null


Function Timeline_RecordEdit(kind$, refID%, fieldId$, oldValue$, newValue$, label$)
    If LoomTimeline = Null Then Return
    Timeline::record(LoomTimeline, TLE_EDIT, kind, refID, fieldId, oldValue, newValue, label)
End Function


Function Timeline_RecordToggle(kind$, refID%, fieldId$, oldValue$, newValue$, label$)
    If LoomTimeline = Null Then Return
    Timeline::record(LoomTimeline, TLE_TOGGLE, kind, refID, fieldId, oldValue, newValue, label)
End Function


Function Timeline_RecordCreate(kind$, refID%, label$)
    If LoomTimeline = Null Then Return
    Timeline::record(LoomTimeline, TLE_CREATE, kind, refID, "", "", "", label)
End Function


Function Timeline_RecordDelete(kind$, refID%, label$)
    If LoomTimeline = Null Then Return
    Timeline::record(LoomTimeline, TLE_DELETE, kind, refID, "", "", "", label)
End Function
