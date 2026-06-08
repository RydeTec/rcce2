Strict

// =============================================================================
// Loom/Palette.bb -- Ctrl+K command palette (find-anywhere)
// =============================================================================
//
// A modal search overlay invoked by Ctrl+K from anywhere. Type to filter every
// entity in the project by name substring; Enter (or click) jumps focus to
// the highlighted result. Esc closes without changing focus.
//
// Jumps use Threads::jump so the result becomes the focused entity AND the
// previous focus (if any) is pushed onto the back stack -- consistent with
// chip-click semantics. This is debatable per the roadmap (the alternative
// is Threads::focus to make palette nav "go to," not "follow a thread"); we
// land on `jump` because the palette is functionally a giant chip rack and
// users will want Esc to walk back to where they were before they searched.
//
// Architecture: Type with Methods, called as `Palette::method(self, args)`.
// Holds a reference to the shared Threads instance (set at construction) so
// result clicks dispatch focus changes without globals.
//
// State machine:
//   open=False                      -- hidden, Ctrl+K opens
//   open=True, query=""             -- modal visible, hint shown
//   open=True, query=<chars>        -- live-filtered results
//   open=True, Enter                -- jump to highlighted result, close
//   open=True, Esc                  -- close, no jump
//
// Result ranking: substring match with prefix bonus. Names that START with the
// query rank above names that merely CONTAIN it. Ties broken by name length
// (shorter first, since shorter names are more likely the canonical entity).
// Exact match beats both.
//
// Why selection-sort over a sorted insert: the BlitzForge Strict-mode Dim
// gotcha (writes to a Dim'd local array inside a Method error) ruled out
// the obvious "sort a temp array" path. Repeated-max with a `picked` flag
// on each PaletteResult is allocation-free and O(K*N) for K results from
// N candidates -- trivial at our scale (a few hundred entities, K=12).


// Layout constants
Const PAL_MODAL_W       = 640
Const PAL_MODAL_H       = 480
Const PAL_PAD           = 16
Const PAL_INPUT_H       = 36
Const PAL_RESULT_H      = 28
Const PAL_MAX_RESULTS   = 12
Const PAL_HINT_H        = 24
Const PAL_CURSOR_PERIOD = 1000

// Scoring constants + the scoreName ranking logic now live in
// SearchScore.bb (pure + unit-tested via SearchScoreTest.bb);
// Palette::scoreName delegates to Loom_ScoreName.


// -----------------------------------------------------------------------------
// PaletteResult -- one search hit. Allocated per-frame inside rebuildResults
// and freed at the top of each rebuild via clearResults. Holds the entity
// kind+refID for dispatch plus the display name, sub-label, and score (for
// ranking). The `picked` flag is used by drawResults' repeated-max walk to
// mark which entries have already been emitted in render order.
// -----------------------------------------------------------------------------
Type PaletteResult
    Field Kind$
    Field RefID%
    Field DisplayName$
    Field SubLabel$
    Field Score%
    Field Picked%
End Type


// =============================================================================
// Palette -- type-to-search modal overlay.
// =============================================================================
Type Palette
    Field threads.Threads
    Field composer.Composer       // set via setComposer, used in picker mode
                                  // to dispatch writeField on selection

    Field open%
    Field query$
    Field highlightIdx%
    Field resultCount%      // total candidates with score > 0

    // Picker mode -- when True, the palette is acting as a reference-field
    // picker rather than a navigator. Only results matching pickerKind are
    // shown (filtered in rebuildResults); on selection, we WRITE the
    // chosen entity's refID into Composer::writeField at the target
    // coordinates instead of calling Threads::jump.
    //
    // pickerTargetValueStrFn is implicit: for typed-ID refs (faction,
    // animset) we write Str(refID). For zone-portal-by-name we write the
    // entity's name string. The kind discriminator handles this in
    // commitPicker below.
    Field pickerMode%
    Field pickerKind$              // kind to filter results by
    Field pickerTargetKind$        // entity kind that owns the field
    Field pickerTargetRefID%       // entity ID
    Field pickerTargetFieldId$     // field name within the entity


    Method create.Palette(threads.Threads)
        self\threads = threads
        self\composer = Null
        self\open = False
        self\query = ""
        self\highlightIdx = 0
        self\resultCount = 0
        self\pickerMode = False
        self\pickerKind = ""
        self\pickerTargetKind = ""
        self\pickerTargetRefID = 0
        self\pickerTargetFieldId = ""
        Return self
    End Method


    // -------------------------------------------------------------------------
    // setComposer -- injection point from Loom.bb so the picker mode can
    // dispatch writeField. Called once at construction.
    // -------------------------------------------------------------------------
    Method setComposer(composer.Composer)
        self\composer = composer
    End Method


    // -------------------------------------------------------------------------
    // openModal -- show the palette in navigator mode. Called when the
    // outer Loom frame detects Ctrl+K.
    // -------------------------------------------------------------------------
    Method openModal()
        self\open = True
        self\query = ""
        self\highlightIdx = 0
        self\pickerMode = False
        self\pickerKind = ""
        self\pickerTargetKind = ""
        self\pickerTargetRefID = 0
        self\pickerTargetFieldId = ""
        Palette::clearResults(self)
        FlushKeys
        Loom_ConsumeClick()
        WriteLog(LoomLog, "Palette: open (navigator)")
    End Method


    // -------------------------------------------------------------------------
    // openAsPicker -- show the palette in picker mode. Only entities of
    // `pickerKind` will be candidates; on selection, the chosen entity's
    // refID is written into (targetKind, targetRefID, targetFieldId) via
    // Composer::writeField + markDirtyForKind.
    //
    // Called by Composer::chipRow on a right-click of a thread chip. The
    // pickerKind matches the chip's kind so the user can only pick a
    // valid replacement (no cross-kind shenanigans).
    // -------------------------------------------------------------------------
    Method openAsPicker(pickerKind$, targetKind$, targetRefID%, targetFieldId$)
        self\open = True
        self\query = ""
        self\highlightIdx = 0
        self\pickerMode = True
        self\pickerKind = pickerKind
        self\pickerTargetKind = targetKind
        self\pickerTargetRefID = targetRefID
        self\pickerTargetFieldId = targetFieldId
        Palette::clearResults(self)
        FlushKeys
        Loom_ConsumeClick()
        WriteLog(LoomLog, "Palette: open (picker " + pickerKind + " -> " + targetKind + "#" + Str(targetRefID) + "." + targetFieldId + ")")
    End Method


    // -------------------------------------------------------------------------
    // closeModal -- hide and drop results. No focus change.
    // -------------------------------------------------------------------------
    Method closeModal()
        self\open = False
        self\query = ""
        self\highlightIdx = 0
        self\pickerMode = False
        self\pickerKind = ""
        self\pickerTargetKind = ""
        self\pickerTargetRefID = 0
        self\pickerTargetFieldId = ""
        Palette::clearResults(self)
        WriteLog(LoomLog, "Palette: close")
    End Method


    // -------------------------------------------------------------------------
    // isOpen -- read accessor used by the outer Loom frame to decide whether
    // to skip its own input handlers (so the palette gets the keystrokes
    // first).
    // -------------------------------------------------------------------------
    Method isOpen%()
        Return self\open
    End Method


    // -------------------------------------------------------------------------
    // clearResults -- drop every PaletteResult instance. Manual Delete (no
    // EnableGC in Loom modules) so results don't leak across frames.
    // -------------------------------------------------------------------------
    Method clearResults()
        Local r.PaletteResult
        For r = Each PaletteResult
            Delete r
        Next
        self\resultCount = 0
    End Method


    // -------------------------------------------------------------------------
    // renderAndUpdate -- per-frame paint + input. Returns True if the palette
    // consumed input this frame (so the outer Loom frame knows to skip its
    // own Esc handler, etc.).
    //
    // The palette renders ABOVE the browser/composer (called last in the
    // frame after they've drawn) so it overlays everything.
    // -------------------------------------------------------------------------
    Method renderAndUpdate%(sw%, sh%)
        If self\open = False Then Return False

        // Drain keyboard into the query before re-running search.
        Palette::pumpKeyboard(self)

        // Closed by pumpKeyboard? bail.
        If self\open = False Then Return True

        // Re-rank on every frame -- query may have changed and entity counts
        // are small enough that scanning is cheap.
        Palette::rebuildResults(self)

        // Dim the world behind the modal (full-screen wash).
        LoomFill(0, 0, sw, sh, LOOM_STONE_950_R, LOOM_STONE_950_G, LOOM_STONE_950_B)

        // Centered modal
        Local mx_screen% = MouseX()
        Local my_screen% = MouseY()
        Local clicked%   = Loom_MouseClicked()

        Local modalX% = (sw - PAL_MODAL_W) / 2
        Local modalY% = (sh - PAL_MODAL_H) / 3      // upper third, closer to eye line

        Palette::drawModalChrome(self, modalX, modalY)
        Palette::drawInput(self, modalX, modalY)
        Palette::drawResults(self, modalX, modalY, mx_screen, my_screen, clicked)
        Palette::drawHint(self, modalX, modalY)

        // Click-outside-modal closes.
        If clicked = True
            If mx_screen < modalX Or mx_screen >= modalX + PAL_MODAL_W Or my_screen < modalY Or my_screen >= modalY + PAL_MODAL_H
                Palette::closeModal(self)
            EndIf
        EndIf

        Return True
    End Method


    // -------------------------------------------------------------------------
    // drawModalChrome -- the modal backdrop, border, and brass top-rule.
    // -------------------------------------------------------------------------
    Method drawModalChrome(modalX%, modalY%)
        LoomShadowCard(modalX, modalY, PAL_MODAL_W, PAL_MODAL_H)
        LoomFill(modalX, modalY, PAL_MODAL_W, PAL_MODAL_H, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B)
        LoomBorder(modalX, modalY, PAL_MODAL_W, PAL_MODAL_H, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomBorder(modalX + 1, modalY + 1, PAL_MODAL_W - 2, PAL_MODAL_H - 2, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)

        // Top brass strip + LOOM tag
        LoomFill(modalX, modalY, PAL_MODAL_W, 3, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomTheme_UseDisplay()
        LoomText(modalX + PAL_PAD, modalY + 6, "FIND  ANYTHING", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomTheme_UseBody()
    End Method


    // -------------------------------------------------------------------------
    // drawInput -- the query input rect with blinking cursor at the end.
    // -------------------------------------------------------------------------
    Method drawInput(modalX%, modalY%)
        Local ix% = modalX + PAL_PAD
        Local iy% = modalY + 32
        Local iw% = PAL_MODAL_W - PAL_PAD * 2
        Local ih% = PAL_INPUT_H

        LoomFill(ix, iy, iw, ih, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
        LoomBorder(ix, iy, iw, ih, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)

        // Prompt glyph
        LoomText(ix + 10, iy + 11, ">", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        // Query string
        LoomText(ix + 28, iy + 11, self\query, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        // Blinking cursor at end of query
        If (MilliSecs() Mod PAL_CURSOR_PERIOD) < (PAL_CURSOR_PERIOD / 2)
            Local cursorX% = ix + 28 + StringWidth(self\query)
            LoomFill(cursorX, iy + 10, 2, 16, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // drawResults -- ranked result list. Uses repeated-max-selection over the
    // PaletteResult pool: on each render slot, find the highest-Score result
    // whose Picked=False, mark it Picked=True, paint it. K=PAL_MAX_RESULTS
    // sweeps over N candidates. After the loop, every Picked=True result is
    // released by the next clearResults call.
    //
    // Highlight follows arrow keys and mouse hover; click or Enter jumps.
    // -------------------------------------------------------------------------
    Method drawResults(modalX%, modalY%, mx%, my%, clicked%)
        Local startY% = modalY + 32 + PAL_INPUT_H + PAL_PAD
        Local rx%     = modalX + PAL_PAD
        Local rw%     = PAL_MODAL_W - PAL_PAD * 2

        If self\resultCount = 0
            If Len(self\query) = 0
                If self\pickerMode = True
                    LoomText(rx, startY, "Picking a " + self\pickerKind + ". Type to filter.", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
                Else
                    LoomText(rx, startY, "Type to search actors, items, spells, zones, factions, animation sets.", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
                EndIf
            Else
                LoomText(rx, startY, "No matches.", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
            EndIf
            Return
        EndIf

        // Render up to PAL_MAX_RESULTS slots via repeated-max.
        Local slotsToShow% = self\resultCount
        If slotsToShow > PAL_MAX_RESULTS Then slotsToShow = PAL_MAX_RESULTS

        Local slot% = 0
        For slot = 0 To slotsToShow - 1
            Local best.PaletteResult = Null
            Local bestScore% = -1
            Local cand.PaletteResult
            For cand = Each PaletteResult
                If cand\Picked = False And cand\Score > bestScore
                    best = cand
                    bestScore = cand\Score
                EndIf
            Next
            If best = Null Then Exit
            best\Picked = True

            Local ry% = startY + slot * PAL_RESULT_H
            Local hovered% = (mx >= rx And mx < rx + rw And my >= ry And my < ry + PAL_RESULT_H)
            If hovered = True Then self\highlightIdx = slot

            Local highlighted% = (slot = self\highlightIdx)
            If highlighted = True
                LoomFill(rx, ry, rw, PAL_RESULT_H, LOOM_ARCANE_900_R, LOOM_ARCANE_900_G, LOOM_ARCANE_900_B)
                LoomBorder(rx, ry, rw, PAL_RESULT_H, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
            EndIf

            // Kind glyph (one-letter)
            Local glyph$ = Palette::kindGlyph(self, best\Kind)
            LoomText(rx + 10, ry + 6, glyph, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

            // Name
            LoomText(rx + 36, ry + 6, best\DisplayName, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

            // Sub-label (kind name) right-aligned
            If best\SubLabel <> ""
                LoomText(rx + rw - StringWidth(best\SubLabel) - 12, ry + 6, best\SubLabel, LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
            EndIf

            If hovered And clicked
                Palette::jumpToResult(self, best)
                Return
            EndIf
        Next
    End Method


    // -------------------------------------------------------------------------
    // drawHint -- footer text reminding the user of keybindings.
    // -------------------------------------------------------------------------
    Method drawHint(modalX%, modalY%)
        Local hy% = modalY + PAL_MODAL_H - PAL_HINT_H - 4
        LoomHRule(modalX + PAL_PAD, hy - 2, PAL_MODAL_W - PAL_PAD * 2, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        LoomText(modalX + PAL_PAD, hy + 4, "Enter to jump  |  arrow keys to move  |  Esc to close", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
    End Method


    // -------------------------------------------------------------------------
    // pumpKeyboard -- drain key input into query, handle Enter / Esc / arrows /
    // Backspace. Called only when self\open = True.
    // -------------------------------------------------------------------------
    Method pumpKeyboard()
        // Esc -- close (consumed; outer Esc handler won't fire same frame).
        If KeyHit(1)
            Palette::closeModal(self)
            Return
        EndIf

        // Enter -- jump to current highlight.
        If KeyHit(28)
            Palette::jumpHighlighted(self)
            Return
        EndIf

        // Backspace
        If KeyHit(14) And Len(self\query) > 0
            self\query = Left$(self\query, Len(self\query) - 1)
            self\highlightIdx = 0
        EndIf

        // Up arrow (200) / Down arrow (208)
        If KeyHit(200) And self\highlightIdx > 0
            self\highlightIdx = self\highlightIdx - 1
        EndIf
        If KeyHit(208)
            self\highlightIdx = self\highlightIdx + 1
        EndIf

        // Drain printable chars
        Local k% = GetKey()
        While k > 0
            If k >= 32 And k <= 126
                self\query = self\query + Chr(k)
                self\highlightIdx = 0
            EndIf
            k = GetKey()
        Wend
    End Method


    // -------------------------------------------------------------------------
    // rebuildResults -- scan every entity, score against the query, keep
    // every candidate with score > 0 as a fresh PaletteResult. Empty query
    // -> empty results (no point listing the entire project unprompted).
    //
    // Result ordering is established at render time via repeated-max in
    // drawResults; this function just emits unordered candidates.
    // -------------------------------------------------------------------------
    Method rebuildResults()
        Palette::clearResults(self)

        Local q$ = Lower$(Trim$(self\query))

        // Navigator mode: empty query = no results (avoids dumping the
        // whole project unprompted). Picker mode: empty query = show
        // every candidate of the picker kind unranked (score 1) so the
        // user can scan a small fixed roster without typing -- common
        // case for "what factions exist?".
        Local showAllBaseline% = False
        If self\pickerMode = True And q = "" Then showAllBaseline = True
        If self\pickerMode = False And q = "" Then Return

        // Helper-less per-kind gates: in picker mode, skip kinds that
        // don't match pickerKind. In navigator mode, all kinds emit.
        Local emitActor% = (self\pickerMode = False Or self\pickerKind = "actor")
        Local emitItem% = (self\pickerMode = False Or self\pickerKind = "item")
        Local emitSpell% = (self\pickerMode = False Or self\pickerKind = "spell")
        Local emitZone% = (self\pickerMode = False Or self\pickerKind = "zone")
        Local emitFaction% = (self\pickerMode = False Or self\pickerKind = "faction")
        Local emitAnimSet% = (self\pickerMode = False Or self\pickerKind = "animset")
        Local emitScript% = (self\pickerMode = False Or self\pickerKind = "script")
        Local emitTexture% = (self\pickerMode = False Or self\pickerKind = "texture")
        Local emitMesh% = (self\pickerMode = False Or self\pickerKind = "mesh")
        Local emitSound% = (self\pickerMode = False Or self\pickerKind = "sound")
        Local emitMusic% = (self\pickerMode = False Or self\pickerKind = "music")

        If emitActor = True
            For Ac.Actor = Each Actor
                Local aName$ = Ac\Race$ + " [" + Ac\Class$ + "]"
                Local aScore% = Palette::scoreOrBaseline(self, q, aName, showAllBaseline)
                If aScore > 0
                    Palette::addResult(self, "actor", Ac\ID, aName, "actor", aScore)
                EndIf
            Next
        EndIf

        If emitItem = True
            For It.Item = Each Item
                Local iScore% = Palette::scoreOrBaseline(self, q, It\Name$, showAllBaseline)
                If iScore > 0
                    Palette::addResult(self, "item", It\ID, It\Name$, "item", iScore)
                EndIf
            Next
        EndIf

        If emitSpell = True
            For Sp.Spell = Each Spell
                Local sScore% = Palette::scoreOrBaseline(self, q, Sp\Name$, showAllBaseline)
                If sScore > 0
                    Palette::addResult(self, "spell", Sp\ID, Sp\Name$, "spell", sScore)
                EndIf
            Next
        EndIf

        If emitZone = True
            For Ar.Area = Each Area
                Local zScore% = Palette::scoreOrBaseline(self, q, Ar\Name$, showAllBaseline)
                If zScore > 0
                    Palette::addResult(self, "zone", Handle(Ar), Ar\Name$, "zone", zScore)
                EndIf
            Next
        EndIf

        If emitFaction = True
            Local fi% = 0
            For fi = 0 To 99
                If FactionNames$(fi) <> ""
                    Local fScore% = Palette::scoreOrBaseline(self, q, FactionNames$(fi), showAllBaseline)
                    If fScore > 0
                        Palette::addResult(self, "faction", fi, FactionNames$(fi), "faction", fScore)
                    EndIf
                EndIf
            Next
        EndIf

        If emitAnimSet = True
            For As.AnimSet = Each AnimSet
                Local mScore% = Palette::scoreOrBaseline(self, q, As\Name$, showAllBaseline)
                If mScore > 0
                    Palette::addResult(self, "animset", As\ID, As\Name$, "anim set", mScore)
                EndIf
            Next
        EndIf

        If emitScript = True
            // ScriptFile pool is populated by Scripts_Init at boot.
            // The display name surfaces ".rsl" so designers see the file
            // identity in the result list; matchers strip the extension
            // tolerantly via Scripts_NormalizeName$ on commit.
            For sf.ScriptFile = Each ScriptFile
                Local scriptScore% = Palette::scoreOrBaseline(self, q, sf\Name$, showAllBaseline)
                If scriptScore > 0
                    Palette::addResult(self, "script", sf\Index, sf\Name$ + ".rsl", "script", scriptScore)
                EndIf
            Next
        EndIf

        If emitTexture = True
            // TextureEntry pool is populated by Textures_Init at boot.
            For te.TextureEntry = Each TextureEntry
                Local texScore% = Palette::scoreOrBaseline(self, q, te\Filename$, showAllBaseline)
                If texScore > 0
                    Palette::addResult(self, "texture", te\Index, te\Filename$ + " #" + Str(te\ID), "texture", texScore)
                EndIf
            Next
        EndIf

        If emitMesh = True
            For mh.MeshEntry = Each MeshEntry
                Local meshScore% = Palette::scoreOrBaseline(self, q, mh\Filename$, showAllBaseline)
                If meshScore > 0
                    Palette::addResult(self, "mesh", mh\Index, mh\Filename$ + " #" + Str(mh\ID), "mesh", meshScore)
                EndIf
            Next
        EndIf

        If emitSound = True
            For sd.SoundEntry = Each SoundEntry
                Local soundScore% = Palette::scoreOrBaseline(self, q, sd\Filename$, showAllBaseline)
                If soundScore > 0
                    Palette::addResult(self, "sound", sd\Index, sd\Filename$ + " #" + Str(sd\ID), "sound", soundScore)
                EndIf
            Next
        EndIf

        If emitMusic = True
            For mu.MusicEntry = Each MusicEntry
                Local musicScore% = Palette::scoreOrBaseline(self, q, mu\Filename$, showAllBaseline)
                If musicScore > 0
                    Palette::addResult(self, "music", mu\Index, mu\Filename$ + " #" + Str(mu\ID), "music", musicScore)
                EndIf
            Next
        EndIf

        // Clamp highlight to the (possibly smaller) result count.
        Local cap% = self\resultCount
        If cap > PAL_MAX_RESULTS Then cap = PAL_MAX_RESULTS
        If self\highlightIdx >= cap Then self\highlightIdx = cap - 1
        If self\highlightIdx < 0 Then self\highlightIdx = 0
    End Method


    // -------------------------------------------------------------------------
    // scoreOrBaseline -- scoreName with a fallback: when showAllBaseline is
    // True (picker-mode empty-query), every name gets a score of 1 so it
    // shows up in the unranked list. Used to populate the picker with the
    // full roster when the user hasn't typed anything yet.
    // -------------------------------------------------------------------------
    Method scoreOrBaseline%(q$, name$, showAllBaseline%)
        If showAllBaseline = True Then Return 1
        Return Palette::scoreName(self, q, name)
    End Method


    // -------------------------------------------------------------------------
    // scoreName -- thin delegate to the pure, unit-tested Loom_ScoreName in
    // SearchScore.bb. q must be already lower-cased and trimmed by the
    // caller. Kept as a method so the scoreOrBaseline call site is unchanged.
    // -------------------------------------------------------------------------
    Method scoreName%(q$, name$)
        Return Loom_ScoreName(q, name)
    End Method


    // -------------------------------------------------------------------------
    // addResult -- allocate + populate one PaletteResult and bump the count.
    // Ranking is deferred to render-time repeated-max selection.
    // -------------------------------------------------------------------------
    Method addResult(kind$, refID%, displayName$, subLabel$, score%)
        Local r.PaletteResult = New PaletteResult()
        r\Kind = kind
        r\RefID = refID
        r\DisplayName = displayName
        r\SubLabel = subLabel
        r\Score = score
        r\Picked = False
        self\resultCount = self\resultCount + 1
    End Method


    // -------------------------------------------------------------------------
    // jumpHighlighted -- Enter handler: find the highlightIdx'th best result
    // by repeated-max selection over the pool, then jump.
    //
    // We can't index PaletteResult by an int -- it's a global pool, not a
    // BBList here -- so we re-do the same repeated-max walk drawResults uses,
    // counting until we hit highlightIdx, then act on that one.
    // -------------------------------------------------------------------------
    Method jumpHighlighted()
        If self\resultCount = 0 Then Return

        // Reset Picked across the pool so the count is honest.
        Local rr.PaletteResult
        For rr = Each PaletteResult
            rr\Picked = False
        Next

        Local slot% = 0
        While slot <= self\highlightIdx
            Local best.PaletteResult = Null
            Local bestScore% = -1
            Local cand.PaletteResult
            For cand = Each PaletteResult
                If cand\Picked = False And cand\Score > bestScore
                    best = cand
                    bestScore = cand\Score
                EndIf
            Next
            If best = Null Then Return
            If slot = self\highlightIdx
                Palette::jumpToResult(self, best)
                Return
            EndIf
            best\Picked = True
            slot = slot + 1
        Wend
    End Method


    // -------------------------------------------------------------------------
    // jumpToResult -- shared dispatch for click + Enter. Closes the modal
    // first so the focus change isn't shadowed by the dim overlay on the
    // same frame.
    //
    // In navigator mode: Threads::jump pushes prev focus to the back stack
    // and sets the new focus.
    // In picker mode: Composer::writeField writes the chosen entity's
    // refID into the target field; mark dirty so Save appears. The
    // current focus is preserved (the user is still on the entity whose
    // field they were picking for).
    // -------------------------------------------------------------------------
    Method jumpToResult(r.PaletteResult)
        Local k$ = r\Kind
        Local id% = r\RefID
        Local nm$ = r\DisplayName

        If self\pickerMode = True
            // Capture picker target before closeModal clears it.
            Local tKind$ = self\pickerTargetKind
            Local tID% = self\pickerTargetRefID
            Local tField$ = self\pickerTargetFieldId

            Palette::closeModal(self)

            If self\composer = Null
                WriteLog(LoomLog, "Palette: picker selected but Composer not wired -- noop")
                Return
            EndIf

            // Encode the chosen value for the target field. For zone
            // portals (string-by-name) we'd write the entity name; for
            // script-bound fields the value is the script basename
            // WITHOUT the .rsl extension (matches the storage shape on
            // disk -- Server expects "Click_Test" not "Click_Test.rsl").
            // All other ref fields take the integer ID as string.
            Local val$ = Str(id)
            If k = "zone" Then val = nm
            If k = "script"
                // DisplayName is "Name.rsl"; strip the extension.
                Local scriptBase$ = nm
                If Right$(nm, 4) = ".rsl" Then scriptBase = Left$(nm, Len(nm) - 4)
                val = scriptBase
            EndIf

            // Capture the old display value for the timeline -- best
            // effort; for typed-ID fields we look up the prior referenced
            // entity name. For portal-by-name fields the old value lives
            // in the target field already, but we don't have a readField
            // helper so we log the entry with old="" which the timeline
            // still renders usefully.
            Local oldVal$ = ""
            Composer::writeField(self\composer, tKind, tID, tField, val)
            Composer::markDirtyForKind(self\composer, tKind)
            Timeline_RecordEdit(tKind, tID, tField, oldVal, val, Threads::lookupName(self\threads, tKind, tID))
            WorldCache_Invalidate()
            WriteLog(LoomLog, "Palette: picked " + k + "#" + Str(id) + " (" + nm + ") -> " + tKind + "#" + Str(tID) + "." + tField)
            Return
        EndIf

        Palette::closeModal(self)
        Threads::jump(self\threads, k, id)
        WriteLog(LoomLog, "Palette: jumped to " + k + "#" + Str(id) + " (" + nm + ")")
    End Method


    // -------------------------------------------------------------------------
    // kindGlyph -- mirror of Threads::kindGlyph; copied locally so Palette
    // doesn't depend on Threads having a public method just for this one
    // glyph (keeps the chip primitive's contract narrower).
    // -------------------------------------------------------------------------
    Method kindGlyph$(kind$)
        If kind = "actor"   Then Return "A"
        If kind = "item"    Then Return "I"
        If kind = "spell"   Then Return "S"
        If kind = "zone"    Then Return "Z"
        If kind = "faction" Then Return "F"
        If kind = "animset" Then Return "M"
        If kind = "script"  Then Return "x"
        If kind = "texture" Then Return "T"
        If kind = "mesh"    Then Return "m"
        If kind = "sound"   Then Return "s"
        If kind = "music"   Then Return "M"
        Return "?"
    End Method
End Type
