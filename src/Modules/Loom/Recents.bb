// =============================================================================
// Loom/Recents.bb -- persisted "recently focused entities" list (Ctrl+R)
// =============================================================================
//
// Non-Strict. The persist() path uses WriteFile / WriteLine which return /
// take BBStream in BlitzForge; Strict's type inference forces a Local
// declaration with a BBStream type that's awkward to thread through the
// SafeWriteCommit% signature (which takes an int). The other Loom modules
// stay Strict; this one alone goes the legacy route for the file IO.
//
// Hero workflow this enables: "where's that goblin shaman I was tuning
// yesterday?" Ctrl+R opens a modal with the entities the user has touched
// most recently across sessions; one click jumps back to it.
//
// PERSISTENCE
// Written to Data/Loom/recents.txt as one line per entry:
//   kind|stableKey|label|millisecsAt
// One file per project (Loom's CWD is the project root, so the relative
// path resolves correctly).
//
// STABLE KEYS
// Zone Handles regenerate on each load (ServerLoadArea returns a fresh
// instance), so we can't persist Handle ints. We persist:
//   actor/item/spell/animset  -> Str(refID)         (array index is stable)
//   faction                   -> Str(refID)         (FactionNames$ slot is stable)
//   zone                      -> Ar\Name$           (resolved by name at load)
//
// On load: walk every line, resolve the stable key back to a current
// refID. If the entity was deleted between sessions, drop the entry.
//
// CAPACITY
// Capped at RECENTS_MAX_ENTRIES; oldest entries fall off when a new
// entry pushes past the cap. Insertion is "move-to-front" semantics --
// re-focusing an existing entity bumps it to position 0 rather than
// adding a duplicate.
//
// Architecture: Type with Methods + a free-function recorder facade
// (Recents_Record) that the caller can invoke without needing an
// instance ref. Module-level LoomRecents global wires the facade.


Const RECENTS_MAX_ENTRIES   = 30
Const RECENTS_FILE          = "Data\Loom\recents.txt"
Const RECENTS_MODAL_W       = 640
Const RECENTS_MODAL_H       = 460
Const RECENTS_PAD           = 16
Const RECENTS_HEADER_H      = 32
Const RECENTS_ROW_H         = 26
Const RECENTS_HINT_H        = 24


Type RecentEntry
    Field Kind$
    Field StableKey$        // see "STABLE KEYS" above
    Field Label$            // cached display name (for the row when the
                            // current entity name has changed since save)
    Field At%               // MilliSecs() at record time -- used for
                            // newest-first ordering and "5m ago" display
    Field Position%         // 0 = most recent; bumped by addToFront
End Type


// =============================================================================
// Recents -- the per-project recent-focus list.
// =============================================================================
Type Recents
    Field threads.Threads

    Field open%
    Field entryCount%
    Field scrollOffset%


    Method create.Recents(threads.Threads)
        self\threads = threads
        self\open = False
        self\entryCount = 0
        self\scrollOffset = 0
        Return self
    End Method


    Method isOpen%()
        Return self\open
    End Method


    Method openModal()
        self\open = True
        self\scrollOffset = 0
        FlushKeys
        Loom_ConsumeClick()
        WriteLog(LoomLog, "Recents: open (" + Str(self\entryCount) + " entries)")
    End Method


    Method closeModal()
        self\open = False
        WriteLog(LoomLog, "Recents: close")
    End Method


    // -------------------------------------------------------------------------
    // record -- "move to front" insertion. If an entry for this stable key
    // already exists, bump its At + Position 0; else allocate. Trim oldest
    // (highest Position) past the cap.
    // -------------------------------------------------------------------------
    Method record(kind$, stableKey$, label$)
        If kind = "" Or stableKey = "" Then Return

        // Bump existing if found.
        Local existing.RecentEntry = Recents::find(self, kind, stableKey)
        If existing <> Null
            existing\At = MilliSecs()
            existing\Label = label   // refresh cached name in case it changed
            Recents::renumber(self)
            Return
        EndIf

        // New entry.
        Local e.RecentEntry = New RecentEntry()
        e\Kind = kind
        e\StableKey = stableKey
        e\Label = label
        e\At = MilliSecs()
        e\Position = 0
        self\entryCount = self\entryCount + 1
        Recents::renumber(self)

        // Trim oldest past cap.
        While self\entryCount > RECENTS_MAX_ENTRIES
            Local victim.RecentEntry = Recents::findOldest(self)
            If victim = Null Then Exit
            Delete victim
            self\entryCount = self\entryCount - 1
        Wend
    End Method


    Method find.RecentEntry(kind$, stableKey$)
        Local e.RecentEntry
        For e = Each RecentEntry
            If e\Kind = kind And e\StableKey = stableKey Then Return e
        Next
        Return Null
    End Method


    Method findOldest.RecentEntry()
        Local oldest.RecentEntry = Null
        Local e.RecentEntry
        For e = Each RecentEntry
            If oldest = Null Then oldest = e
            If e\At < oldest\At Then oldest = e
        Next
        Return oldest
    End Method


    // -------------------------------------------------------------------------
    // renumber -- after a record() call, walk every entry sorted by At
    // descending and assign Position 0..N-1. Used so drawEntries can
    // iterate the pool and place each row at its Position * ROW_H. With
    // RECENTS_MAX_ENTRIES = 30 this is cheap.
    // -------------------------------------------------------------------------
    Method renumber()
        // Reset all positions to a sentinel so the repeated-max walk
        // below can detect already-numbered entries.
        Local rr.RecentEntry
        For rr = Each RecentEntry
            rr\Position = -1
        Next

        Local pos% = 0
        While pos < self\entryCount
            Local newest.RecentEntry = Null
            Local newestAt% = -1
            Local cand.RecentEntry
            For cand = Each RecentEntry
                If cand\Position = -1 And cand\At >= newestAt
                    newest = cand
                    newestAt = cand\At
                EndIf
            Next
            If newest = Null Then Exit
            newest\Position = pos
            pos = pos + 1
        Wend
    End Method


    // -------------------------------------------------------------------------
    // resolveAndFocus -- click handler. Look up the stable key against
    // current in-memory state; if it resolves, dispatch Threads::focus.
    // -------------------------------------------------------------------------
    Method resolveAndFocus(e.RecentEntry)
        Local refID% = Recents::resolveStableKey(self, e\Kind, e\StableKey)
        If refID = -1
            WriteLog(LoomLog, "Recents: stale entry " + e\Kind + "/" + e\StableKey + " (not found)")
            Return
        EndIf
        Recents::closeModal(self)
        Threads::jump(self\threads, e\Kind, refID)
        WriteLog(LoomLog, "Recents: jumped to " + e\Kind + "#" + Str(refID))
    End Method


    Method resolveStableKey%(kind$, stableKey$)
        If kind = "zone"
            // Walk areas by name (case-insensitive)
            Local upr$ = Upper$(stableKey)
            For Ar.Area = Each Area
                If Upper$(Ar\Name$) = upr Then Return Handle(Ar)
            Next
            Return -1
        EndIf

        // All other kinds use Int(stableKey) as refID. Verify the entity
        // still exists; return -1 otherwise.
        Local id% = Int(stableKey)
        If kind = "actor"
            If id < 0 Or id > 65535 Then Return -1
            If ActorList(id) = Null Then Return -1
            Return id
        EndIf
        If kind = "item"
            If id < 0 Or id > 65534 Then Return -1
            If ItemList(id) = Null Then Return -1
            Return id
        EndIf
        If kind = "spell"
            If id < 0 Or id > 65534 Then Return -1
            If SpellsList(id) = Null Then Return -1
            Return id
        EndIf
        If kind = "faction"
            If id < 0 Or id > 99 Then Return -1
            If FactionNames$(id) = "" Then Return -1
            Return id
        EndIf
        If kind = "animset"
            If id < 0 Or id > 999 Then Return -1
            Local A.AnimSet
            For A = Each AnimSet
                If A\ID = id Then Return id
            Next
            Return -1
        EndIf
        Return -1
    End Method


    // -------------------------------------------------------------------------
    // persist -- atomic write to Data/Loom/recents.txt via SafeWriteOpen/
    // Commit (project convention -- see CLAUDE.md "Atomic writes"). Each
    // line is "kind|stableKey|label|at".
    // -------------------------------------------------------------------------
    Method persist()
        // Ensure the parent directory exists; CreateDir is a no-op when
        // already present.
        If FileType("Data\Loom") <> 2 Then CreateDir "Data\Loom"

        Local tempPath$ = SafeWriteOpen$(RECENTS_FILE)
        Local F = WriteFile(tempPath)
        If F = 0
            WriteLog(LoomLog, "Recents: failed to open temp for write")
            Return
        EndIf

        Local e.RecentEntry
        For e = Each RecentEntry
            WriteLine(F, e\Kind + "|" + e\StableKey + "|" + e\Label + "|" + Str(e\At))
        Next

        SafeWriteCommit%(tempPath, RECENTS_FILE, F)
        WriteLog(LoomLog, "Recents: persisted " + Str(self\entryCount) + " entries to " + RECENTS_FILE)
    End Method


    // -------------------------------------------------------------------------
    // load -- read Data/Loom/recents.txt and rebuild the pool. Silent
    // no-op when the file doesn't exist (first-run for this project).
    //
    // Skips lines where the resolved stable key no longer maps to an
    // entity (the recorded entity was deleted between sessions).
    // -------------------------------------------------------------------------
    Method load()
        If FileType(RECENTS_FILE) <> 1 Then Return

        Local F = ReadFile(RECENTS_FILE)
        If F = 0 Then Return

        Local loaded% = 0
        While Not Eof(F)
            Local raw$ = ReadLine$(F)
            If raw <> ""
                Recents::parseAndRecord(self, raw)
                loaded = loaded + 1
            EndIf
        Wend

        CloseFile(F)
        Recents::renumber(self)
        WriteLog(LoomLog, "Recents: loaded " + Str(loaded) + " lines from " + RECENTS_FILE)
    End Method


    // -------------------------------------------------------------------------
    // parseAndRecord -- split a "kind|stableKey|label|at" line and emit
    // a RecentEntry if the stable key still resolves to a current entity.
    // -------------------------------------------------------------------------
    Method parseAndRecord(raw$)
        // 4-field split by '|'. Use Instr() pairs.
        Local p1% = Instr(raw, "|")
        If p1 = 0 Then Return
        Local p2% = Instr(raw, "|", p1 + 1)
        If p2 = 0 Then Return
        Local p3% = Instr(raw, "|", p2 + 1)
        If p3 = 0 Then Return

        Local kind$ = Mid$(raw, 1, p1 - 1)
        Local stableKey$ = Mid$(raw, p1 + 1, p2 - p1 - 1)
        Local label$ = Mid$(raw, p2 + 1, p3 - p2 - 1)
        Local atStr$ = Mid$(raw, p3 + 1)

        // Skip if no longer resolves.
        If Recents::resolveStableKey(self, kind, stableKey) = -1 Then Return

        // Direct allocate (bypass record() because we want to preserve
        // the persisted At, not stamp MilliSecs() now).
        Local e.RecentEntry = New RecentEntry()
        e\Kind = kind
        e\StableKey = stableKey
        e\Label = label
        e\At = Int(atStr)
        e\Position = 0
        self\entryCount = self\entryCount + 1
    End Method


    // -------------------------------------------------------------------------
    // renderAndUpdate -- modal frame. Same shape as Timeline / BrokenRefs.
    // -------------------------------------------------------------------------
    Method renderAndUpdate%(sw%, sh%)
        If self\open = False Then Return False

        Recents::pumpKeyboard(self)
        If self\open = False Then Return True

        LoomFill(0, 0, sw, sh, LOOM_STONE_950_R, LOOM_STONE_950_G, LOOM_STONE_950_B)

        Local mx% = MouseX()
        Local my% = MouseY()
        Local clicked% = Loom_MouseClicked()

        Local modalX% = (sw - RECENTS_MODAL_W) / 2
        Local modalY% = (sh - RECENTS_MODAL_H) / 3

        LoomShadowCard(modalX, modalY, RECENTS_MODAL_W, RECENTS_MODAL_H)
        LoomFill(modalX, modalY, RECENTS_MODAL_W, RECENTS_MODAL_H, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B)
        LoomBorder(modalX, modalY, RECENTS_MODAL_W, RECENTS_MODAL_H, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomBorder(modalX + 1, modalY + 1, RECENTS_MODAL_W - 2, RECENTS_MODAL_H - 2, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        LoomFill(modalX, modalY, RECENTS_MODAL_W, 3, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        LoomTheme_UseDisplay()
        LoomText(modalX + RECENTS_PAD, modalY + 6, "RECENTS  |  " + Str(self\entryCount) + " entries", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomTheme_UseBody()

        Recents::drawEntries(self, modalX, modalY + RECENTS_HEADER_H, mx, my, clicked)

        Local hy% = modalY + RECENTS_MODAL_H - RECENTS_HINT_H - 4
        LoomHRule(modalX + RECENTS_PAD, hy - 2, RECENTS_MODAL_W - RECENTS_PAD * 2, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        LoomText(modalX + RECENTS_PAD, hy + 4, "Click a row to jump  |  arrows scroll  |  Esc to close", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)

        If clicked = True
            If mx < modalX Or mx >= modalX + RECENTS_MODAL_W Or my < modalY Or my >= modalY + RECENTS_MODAL_H
                Recents::closeModal(self)
            EndIf
        EndIf

        Return True
    End Method


    Method drawEntries(modalX%, listY%, mx%, my%, clicked%)
        Local listH% = RECENTS_MODAL_H - RECENTS_HEADER_H - RECENTS_HINT_H - 12
        Local rowsVisible% = listH / RECENTS_ROW_H
        Local rx% = modalX + RECENTS_PAD
        Local rw% = RECENTS_MODAL_W - RECENTS_PAD * 2

        If self\entryCount = 0
            LoomText(rx, listY + 12, "No recently-focused entities yet. Browse some entities and they'll appear here.", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
            Return
        EndIf

        // Walk pool in Position order (renumber set 0..N-1 by At desc)
        Local slot% = 0
        For slot = 0 To rowsVisible - 1
            Local targetPos% = self\scrollOffset + slot
            If targetPos >= self\entryCount Then Exit

            Local e.RecentEntry = Recents::findByPosition(self, targetPos)
            If e <> Null
                Local ry% = listY + slot * RECENTS_ROW_H
                Recents::drawOneEntry(self, e, rx, ry, rw, mx, my, clicked)
            EndIf
        Next
    End Method


    Method findByPosition.RecentEntry(pos%)
        Local e.RecentEntry
        For e = Each RecentEntry
            If e\Position = pos Then Return e
        Next
        Return Null
    End Method


    Method drawOneEntry(e.RecentEntry, rx%, ry%, rw%, mx%, my%, clicked%)
        Local hovered% = (mx >= rx And mx < rx + rw And my >= ry And my < ry + RECENTS_ROW_H)
        If hovered = True
            LoomFill(rx, ry, rw, RECENTS_ROW_H, LOOM_ARCANE_900_R, LOOM_ARCANE_900_G, LOOM_ARCANE_900_B)
        EndIf

        // Kind glyph + label + stale marker
        Local glyph$ = Recents::kindGlyph(self, e\Kind)
        LoomText(rx + 8, ry + 6, glyph, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        Local stale% = (Recents::resolveStableKey(self, e\Kind, e\StableKey) = -1)
        Local nameCol_R% = LOOM_PARCHMENT_100_R
        Local nameCol_G% = LOOM_PARCHMENT_100_G
        Local nameCol_B% = LOOM_PARCHMENT_100_B
        If stale = True
            nameCol_R = LOOM_DANGER_R
            nameCol_G = LOOM_DANGER_G
            nameCol_B = LOOM_DANGER_B
        EndIf
        LoomText(rx + 36, ry + 6, e\Label, nameCol_R, nameCol_G, nameCol_B)

        // Age on the right
        Local ageStr$ = Recents::formatAge(self, (MilliSecs() - e\At) / 1000)
        LoomText(rx + rw - StringWidth(ageStr) - 12, ry + 6, ageStr, LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)

        If hovered And clicked
            Recents::resolveAndFocus(self, e)
        EndIf
    End Method


    Method pumpKeyboard()
        If KeyHit(1)
            Recents::closeModal(self)
            Return
        EndIf
        If KeyHit(200) And self\scrollOffset > 0
            self\scrollOffset = self\scrollOffset - 1
        EndIf
        If KeyHit(208)
            self\scrollOffset = self\scrollOffset + 1
            If self\scrollOffset >= self\entryCount Then self\scrollOffset = self\entryCount - 1
            If self\scrollOffset < 0 Then self\scrollOffset = 0
        EndIf
    End Method


    Method kindGlyph$(kind$)
        If kind = "actor"   Then Return "A"
        If kind = "item"    Then Return "I"
        If kind = "spell"   Then Return "S"
        If kind = "zone"    Then Return "Z"
        If kind = "faction" Then Return "F"
        If kind = "animset" Then Return "M"
        Return "?"
    End Method


    Method formatAge$(sec%)
        If sec < 60 Then Return Str(sec) + "s ago"
        Local mins% = sec / 60
        If mins < 60 Then Return Str(mins) + "m ago"
        Local hrs% = mins / 60
        If hrs < 24 Then Return Str(hrs) + "h ago"
        Local days% = hrs / 24
        Return Str(days) + "d ago"
    End Method
End Type


// =============================================================================
// Recorder facade -- callers (Threads::focus / jump) emit through this
// without needing the instance ref. LoomRecents is set by Loom.bb at boot.
// =============================================================================
Global LoomRecents.Recents = Null


Function Recents_Record(kind$, refID%, label$)
    If LoomRecents = Null Then Return
    If kind = "" Then Return

    // Compute the stable key for persistence (Handles regenerate; names
    // don't, IDs in fixed arrays don't).
    Local stableKey$
    If kind = "zone"
        Local Ar.Area = Object.Area(refID)
        If Ar = Null Then Return
        stableKey = Ar\Name$
    Else
        stableKey = Str(refID)
    EndIf

    Recents::record(LoomRecents, kind, stableKey, label)
End Function
