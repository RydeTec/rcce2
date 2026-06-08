Strict

// =============================================================================
// Loom/ScriptSearch.bb -- Ctrl+F modal that searches across every .rsl
// in the Scripts catalog and lists each matching line.
// =============================================================================
//
// Why this exists: with ~40+ script files in a typical project, finding
// "every script that calls BVM_KillActor" or "every script that
// references the 'Trader' faction" previously meant grepping the file
// system. This modal puts that workflow inside Loom.
//
// Architecture:
//   - Type ScriptSearch holds modal state (query + results + scroll)
//   - Type ScriptSearchHit per match: script name + line number + snippet
//   - Modal opens via Ctrl+F (Loom.bb global keybinding)
//   - Query updates on every printable keystroke; results regenerate
//     on each query change (cheap -- catalog is in-memory after iter 60)
//   - Results capped at 200 to keep the modal responsive on a query like
//     "Return" which might hit every file
//   - Click a result row -> Threads::jump("script", catalogIdx) +
//     close modal. Esc back-stack walks back if needed.
//
// Match algorithm: case-insensitive substring. Regex support is a
// follow-up; substring covers the dominant "find this BVM name" use
// case. Each matching LINE produces one hit (multiple matches on the
// same line collapse to one).
//
// Caps:
//   - Per-file max hits: 20 (avoids one runaway file flooding the list)
//   - Total hits: 200
//   - Per-file content scan bytes: SCRIPTS_PREVIEW_MAX_BYTES (64KB, from
//     ScriptsCatalog.bb) -- truncates very large files


// ---- Modal sizing ----------------------------------------------------------
Const SS_MODAL_W      = 720
Const SS_MODAL_H      = 520
Const SS_PAD          = 16
Const SS_HEADER_H     = 32
Const SS_QUERY_H      = 28
Const SS_ROW_H        = 22
Const SS_HINT_H       = 24
Const SS_MAX_RESULTS  = 200
Const SS_MAX_PER_FILE = 20


// ---- Result entry ---------------------------------------------------------
Type ScriptSearchHit
    Field ScriptIndex%      // catalog index (Threads::focus refID)
    Field ScriptName$       // cached basename for display
    Field LineNum%          // 1-based line number where the match landed
    Field Snippet$          // the matching line itself (truncated for display)
End Type


// =============================================================================
// Type ScriptSearch -- the modal.
// =============================================================================
Type ScriptSearch
    Field threads.Threads

    Field open%
    Field query$
    Field hitCount%
    Field scrollOffset%
    Field hoveredIdx%       // -1 if none
    Field highlightIdx%     // arrow-key keyboard selection


    Method create.ScriptSearch(threads.Threads)
        self\threads = threads
        self\open = False
        self\query = ""
        self\hitCount = 0
        self\scrollOffset = 0
        self\hoveredIdx = -1
        self\highlightIdx = 0
        Return self
    End Method


    Method isOpen%()
        Return self\open
    End Method


    Method openModal()
        self\open = True
        self\query = ""
        self\hitCount = 0
        self\scrollOffset = 0
        self\highlightIdx = 0
        ScriptSearch::clearHits(self)
        FlushKeys
        Loom_ConsumeClick()
        WriteLog(LoomLog, "ScriptSearch: open")
    End Method


    Method closeModal()
        self\open = False
        ScriptSearch::clearHits(self)
        WriteLog(LoomLog, "ScriptSearch: close")
    End Method


    Method clearHits()
        Local h.ScriptSearchHit
        For h = Each ScriptSearchHit
            Delete h
        Next
        self\hitCount = 0
        self\scrollOffset = 0
    End Method


    // -------------------------------------------------------------------------
    // runSearch -- scan every script's content for query as a case-
    // insensitive substring. Allocates one ScriptSearchHit per matching
    // line. Capped per-file and total.
    // -------------------------------------------------------------------------
    Method runSearch()
        ScriptSearch::clearHits(self)
        If self\query = "" Then Return
        If Len(self\query) < 2 Then Return    // single-char queries blow the modal apart

        Local q$ = Lower$(self\query)

        Local sf.ScriptFile
        For sf = Each ScriptFile
            If self\hitCount >= SS_MAX_RESULTS Then Exit

            Local content$ = Scripts_GetContent(sf\Name$)
            If content = "" Then Continue

            // Line-by-line walk. Manual newline split because Blitz3D
            // has no native split-by-char. cursor walks the buffer;
            // nl finds next Chr(10).
            Local fileHits% = 0
            Local lineNum% = 1
            Local cursor% = 1
            Local clen% = Len(content)
            While cursor <= clen And fileHits < SS_MAX_PER_FILE And self\hitCount < SS_MAX_RESULTS
                Local nl% = cursor
                While nl <= clen And Mid$(content, nl, 1) <> Chr(10)
                    nl = nl + 1
                Wend
                Local L$ = Mid$(content, cursor, nl - cursor)

                If Instr(Lower$(L), q) > 0
                    Local h.ScriptSearchHit = New ScriptSearchHit()
                    h\ScriptIndex = sf\Index
                    h\ScriptName = sf\Name$
                    h\LineNum = lineNum
                    // Trim leading/trailing whitespace for display
                    h\Snippet = ScriptSearch::trim(self, L)
                    If Len(h\Snippet) > 100 Then h\Snippet = Left$(h\Snippet, 97) + "..."
                    self\hitCount = self\hitCount + 1
                    fileHits = fileHits + 1
                EndIf

                cursor = nl + 1
                lineNum = lineNum + 1
            Wend
        Next

        // Reset selection/scroll into the new result set.
        self\scrollOffset = 0
        self\highlightIdx = 0
    End Method


    // -------------------------------------------------------------------------
    // trim -- strip leading/trailing whitespace from a snippet. Blitz3D
    // has no built-in.
    // -------------------------------------------------------------------------
    Method trim$(s$)
        Local n% = Len(s)
        Local lo% = 1
        While lo <= n
            Local c$ = Mid$(s, lo, 1)
            If c <> " " And c <> Chr(9) And c <> Chr(13) Then Exit
            lo = lo + 1
        Wend
        Local hi% = n
        While hi >= lo
            Local c2$ = Mid$(s, hi, 1)
            If c2 <> " " And c2 <> Chr(9) And c2 <> Chr(13) Then Exit
            hi = hi - 1
        Wend
        If hi < lo Then Return ""
        Return Mid$(s, lo, hi - lo + 1)
    End Method


    // -------------------------------------------------------------------------
    // pumpKeyboard -- drain printable chars / Backspace / Esc / arrows /
    // Enter into query + selection state.
    // -------------------------------------------------------------------------
    Method pumpKeyboard()
        If self\open = False Then Return

        // Esc closes
        If KeyHit(1)
            ScriptSearch::closeModal(self)
            Return
        EndIf

        Local queryChanged% = False

        // Backspace (14) -- pop last char
        If KeyHit(14) And Len(self\query) > 0
            self\query = Left$(self\query, Len(self\query) - 1)
            queryChanged = True
        EndIf

        // Up/Down arrow -- move highlight
        If KeyHit(200)    // up
            self\highlightIdx = self\highlightIdx - 1
            If self\highlightIdx < 0 Then self\highlightIdx = 0
            ScriptSearch::scrollToHighlight(self)
        EndIf
        If KeyHit(208)    // down
            self\highlightIdx = self\highlightIdx + 1
            If self\highlightIdx >= self\hitCount Then self\highlightIdx = self\hitCount - 1
            If self\highlightIdx < 0 Then self\highlightIdx = 0
            ScriptSearch::scrollToHighlight(self)
        EndIf

        // PageDown / PageUp
        If KeyHit(201)    // PageUp
            self\highlightIdx = self\highlightIdx - 10
            If self\highlightIdx < 0 Then self\highlightIdx = 0
            ScriptSearch::scrollToHighlight(self)
        EndIf
        If KeyHit(209)    // PageDown
            self\highlightIdx = self\highlightIdx + 10
            If self\highlightIdx >= self\hitCount Then self\highlightIdx = self\hitCount - 1
            If self\highlightIdx < 0 Then self\highlightIdx = 0
            ScriptSearch::scrollToHighlight(self)
        EndIf

        // Enter (28) -- focus highlighted hit
        If KeyHit(28) And self\hitCount > 0
            ScriptSearch::commitHighlighted(self)
            Return
        EndIf

        // Printable chars
        Local k% = GetKey()
        While k > 0
            If k >= 32 And k <= 126
                self\query = self\query + Chr(k)
                queryChanged = True
            EndIf
            k = GetKey()
        Wend

        If queryChanged = True
            ScriptSearch::runSearch(self)
        EndIf
    End Method


    Method scrollToHighlight()
        Local listH% = SS_MODAL_H - SS_HEADER_H - SS_QUERY_H - SS_HINT_H - SS_PAD * 2
        Local rowsVisible% = listH / SS_ROW_H
        If self\highlightIdx < self\scrollOffset Then self\scrollOffset = self\highlightIdx
        If self\highlightIdx >= self\scrollOffset + rowsVisible Then self\scrollOffset = self\highlightIdx - rowsVisible + 1
        If self\scrollOffset < 0 Then self\scrollOffset = 0
    End Method


    Method commitHighlighted()
        Local target% = 0
        Local h.ScriptSearchHit
        For h = Each ScriptSearchHit
            If target = self\highlightIdx
                Threads::jump(self\threads, "script", h\ScriptIndex)
                ScriptSearch::closeModal(self)
                WriteLog(LoomLog, "ScriptSearch: jump to " + h\ScriptName + ".rsl line " + Str(h\LineNum))
                Return
            EndIf
            target = target + 1
        Next
    End Method


    // -------------------------------------------------------------------------
    // renderAndUpdate -- one-shot per frame paint + input drain. Returns
    // True if the modal is visible (i.e. consumed input this frame).
    // -------------------------------------------------------------------------
    Method renderAndUpdate%(sw%, sh%)
        If self\open = False Then Return False

        ScriptSearch::pumpKeyboard(self)
        If self\open = False Then Return True

        LoomFill(0, 0, sw, sh, LOOM_STONE_950_R, LOOM_STONE_950_G, LOOM_STONE_950_B)

        Local mx% = MouseX()
        Local my% = MouseY()
        Local clicked% = Loom_MouseClicked()

        Local modalX% = (sw - SS_MODAL_W) / 2
        Local modalY% = (sh - SS_MODAL_H) / 3

        LoomShadowCard(modalX, modalY, SS_MODAL_W, SS_MODAL_H)
        LoomFill(modalX, modalY, SS_MODAL_W, SS_MODAL_H, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B)
        LoomBorder(modalX, modalY, SS_MODAL_W, SS_MODAL_H, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomBorder(modalX + 1, modalY + 1, SS_MODAL_W - 2, SS_MODAL_H - 2, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        LoomFill(modalX, modalY, SS_MODAL_W, 3, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        // Header
        LoomTheme_UseDisplay()
        LoomText(modalX + SS_PAD, modalY + 6, "FIND IN SCRIPTS  |  " + Str(self\hitCount) + " match(es)", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomTheme_UseBody()

        // Query input field
        Local qY% = modalY + SS_HEADER_H + 4
        Local qX% = modalX + SS_PAD
        Local qW% = SS_MODAL_W - SS_PAD * 2
        LoomFill(qX, qY, qW, SS_QUERY_H, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
        LoomBorder(qX, qY, qW, SS_QUERY_H, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        LoomText(qX + 8, qY + 6, "find: " + self\query, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        // Blinking cursor at end
        If (MilliSecs() Mod 1000) < 500
            Local cx% = qX + 8 + StringWidth("find: " + self\query)
            LoomFill(cx, qY + 4, 2, SS_QUERY_H - 8, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        EndIf

        // Results list
        Local listY% = qY + SS_QUERY_H + 4
        ScriptSearch::drawHits(self, modalX, listY, mx, my, clicked)

        // Hint footer
        Local hy% = modalY + SS_MODAL_H - SS_HINT_H - 4
        LoomHRule(modalX + SS_PAD, hy - 2, SS_MODAL_W - SS_PAD * 2, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        Local hint$ = "type to find  |  up/down + Enter to jump  |  Esc closes"
        If Len(self\query) > 0 And Len(self\query) < 2
            hint = "(type at least 2 chars to search)"
        EndIf
        LoomText(modalX + SS_PAD, hy + 4, hint, LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)

        // Click outside the modal closes
        If clicked = True
            If mx < modalX Or mx >= modalX + SS_MODAL_W Or my < modalY Or my >= modalY + SS_MODAL_H
                ScriptSearch::closeModal(self)
            EndIf
        EndIf

        Return True
    End Method


    Method drawHits(modalX%, listY%, mx%, my%, clicked%)
        Local listH% = SS_MODAL_H - SS_HEADER_H - SS_QUERY_H - SS_HINT_H - SS_PAD * 2
        Local rowsVisible% = listH / SS_ROW_H
        Local rx% = modalX + SS_PAD
        Local rw% = SS_MODAL_W - SS_PAD * 2

        If self\hitCount = 0
            If self\query = ""
                LoomText(rx, listY + 12, "Start typing to search across every .rsl in the project.", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
            Else If Len(self\query) >= 2
                LoomText(rx, listY + 12, "No matches for " + Chr(34) + self\query + Chr(34), LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
            EndIf
            Return
        EndIf

        // Iterate hits in pool order (== insertion order from runSearch).
        // Apply scrollOffset / rowsVisible window.
        Local idx% = 0
        Local h.ScriptSearchHit
        For h = Each ScriptSearchHit
            If idx >= self\scrollOffset And idx < self\scrollOffset + rowsVisible
                Local slot% = idx - self\scrollOffset
                Local ry% = listY + slot * SS_ROW_H
                Local hovered% = (mx >= rx And mx < rx + rw And my >= ry And my < ry + SS_ROW_H)
                Local isHighlight% = (idx = self\highlightIdx)

                If isHighlight = True
                    LoomFill(rx, ry, rw, SS_ROW_H, LOOM_ARCANE_700_R, LOOM_ARCANE_700_G, LOOM_ARCANE_700_B)
                Else If hovered = True
                    LoomFill(rx, ry, rw, SS_ROW_H, LOOM_ARCANE_900_R, LOOM_ARCANE_900_G, LOOM_ARCANE_900_B)
                EndIf

                // Left: brass-script-name + line number
                Local prefix$ = h\ScriptName + ":" + Str(h\LineNum)
                LoomText(rx + 8, ry + 4, prefix, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
                // Right of column: snippet
                LoomText(rx + 240, ry + 4, h\Snippet, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

                If hovered = True And clicked = True
                    self\highlightIdx = idx
                    ScriptSearch::commitHighlighted(self)
                    Return
                EndIf
            EndIf
            idx = idx + 1
        Next
    End Method
End Type
