Strict

// =============================================================================
// Loom/Threads.bb -- focus + back-stack navigation + clickable thread chips
// =============================================================================
//
// The centerpiece of the Loom design: every reference between entities is
// rendered as a clickable chip. Clicking a chip jumps the focused entity
// from the source to the target and pushes the source onto a back stack;
// Esc pops the stack to walk back through the navigation chain.
//
// Hero flow this enables:
//   browse to "Goblin Shaman" -> composer paints the actor ->
//   click [F Forest Tribe] chip on its faction field ->
//   composer paints Forest Tribe with its member roster ->
//   click [A Goblin Scout] in that roster ->
//   composer paints that actor ->
//   Esc -> back to Forest Tribe ->
//   Esc -> back to Goblin Shaman.
//
// Entity-kind identifier convention (kept consistent across Browser /
// Composer / Threads / Loom.bb):
//
//   ""         no entity focused
//   "actor"    refID = Actor\ID (array index in ActorList)
//   "item"     refID = Item\ID  (array index in ItemList)
//   "spell"    refID = Spell\ID (array index in SpellsList)
//   "zone"     refID = Handle(Area)
//   "faction"  refID = FactionNames$ array index 0..99
//   "animset"  refID = AnimSet\ID
//
// Architecture: Type with Methods, called as `Threads::method(self, args)`.
// See [docs/loom/architecture.md](../../../docs/loom/architecture.md) and
// the BlitzForge skill's "Module architecture" section for the project
// convention this follows.


// -----------------------------------------------------------------------------
// LoomFocusEntry -- one back-stack record. Manual lifecycle (no EnableGC) so
// every push has a matching Delete in `back` / `clearStack` below.
// -----------------------------------------------------------------------------
Type LoomFocusEntry
    Field Kind$
    Field RefID%
End Type


// Chip layout constants
Const CHIP_PAD_X = 10
Const CHIP_ICON_W = 18


// =============================================================================
// Threads -- focus state + back stack + chip renderer.
//
// Single instance lives on the top-level Loom app type. Browser and Composer
// each hold a reference to it (set at construction) so card clicks call
// Threads::focus and chip clicks call Threads::jump.
// =============================================================================
Type Threads
    Field focusKind$
    Field focusID%
    Field backStack.BBList


    Method create.Threads()
        self\backStack = CreateList()
        self\focusKind = ""
        self\focusID = 0
        Return self
    End Method


    // -------------------------------------------------------------------------
    // focus -- direct set, no stack push. Use when entering composer from the
    // browser, or when restoring after a back().
    // -------------------------------------------------------------------------
    Method focus(kind$, refID%)
        self\focusKind = kind$
        self\focusID = refID
        // Record into Recents so Ctrl+R can find it later. Recents_Record
        // skips empty-kind invocations (used to close the composer back
        // to the browser); LoomRecents may be Null on the first focus
        // before Loom.bb's boot finishes wiring -- the facade handles
        // that defensively too.
        If kind$ <> "" Then Recents_Record(kind$, refID, Threads::lookupName(self, kind$, refID))
    End Method


    // -------------------------------------------------------------------------
    // jump -- push current focus onto the back stack, then set new. This is
    // what every chip click does. No-op on self-link.
    // -------------------------------------------------------------------------
    Method jump(kind$, refID%)
        If kind$ = self\focusKind And refID = self\focusID Then Return

        // Push current focus (if any) onto the back stack
        If self\focusKind <> ""
            Local prev.LoomFocusEntry = New LoomFocusEntry()
            prev\Kind = self\focusKind
            prev\RefID = self\focusID
            ListAdd(self\backStack, prev)
        EndIf

        self\focusKind = kind$
        self\focusID = refID

        // Same Recents hook as focus() -- mirror, so navigations land in
        // the recents list whether they come from card clicks or chip jumps.
        If kind$ <> "" Then Recents_Record(kind$, refID, Threads::lookupName(self, kind$, refID))

        WriteLog(LoomLog, "Threads: jumped to " + kind$ + "#" + Str(refID) + " (back stack: " + Str(ListSize(self\backStack)) + ")")
    End Method


    // -------------------------------------------------------------------------
    // back -- pop and focus; returns True if popped, False if stack was empty.
    //
    // No EnableGC at file top, so `Delete prev` is required -- ListRemove only
    // drops the list's reference, leaving the heap instance leaked otherwise.
    // -------------------------------------------------------------------------
    Method back%()
        Local n% = ListSize(self\backStack)
        If n = 0 Then Return False

        Local prev.LoomFocusEntry = ListAt(self\backStack, n - 1)
        If prev = Null Then Return False
        Local kind$ = prev\Kind
        Local refID% = prev\RefID
        ListRemove(self\backStack, n - 1)
        Delete prev

        self\focusKind = kind
        self\focusID = refID

        WriteLog(LoomLog, "Threads: back to " + kind + "#" + Str(refID) + " (back stack: " + Str(ListSize(self\backStack)) + ")")
        Return True
    End Method


    // -------------------------------------------------------------------------
    // clearStack -- drop the entire back stack. Call when leaving composer
    // back to the browser so a new browse session doesn't inherit stale
    // history. Same Delete rationale as back().
    // -------------------------------------------------------------------------
    Method clearStack()
        If self\backStack = Null Then Return
        Local entry.LoomFocusEntry
        For entry = Each LoomFocusEntry
            Delete entry
        Next
        ListClear(self\backStack)
    End Method


    // -------------------------------------------------------------------------
    // lookupName -- resolve a (kind, refID) pair to its display name. Returns
    // "" if the reference doesn't resolve (entity was deleted) -- callers
    // treat empty as a broken-ref signal.
    // -------------------------------------------------------------------------
    Method lookupName$(kind$, refID%)
        If kind = "actor"
            If refID < 0 Or refID > 65535 Then Return ""
            Local Ac.Actor = ActorList(refID)
            If Ac = Null Then Return ""
            Return Ac\Race$ + " [" + Ac\Class$ + "]"
        EndIf

        If kind = "item"
            If refID < 0 Or refID > 65534 Then Return ""
            Local It.Item = ItemList(refID)
            If It = Null Then Return ""
            Return It\Name$
        EndIf

        If kind = "spell"
            If refID < 0 Or refID > 65534 Then Return ""
            Local Sp.Spell = SpellsList(refID)
            If Sp = Null Then Return ""
            Return Sp\Name$
        EndIf

        If kind = "zone"
            Local Ar.Area = Object.Area(refID)
            If Ar = Null Then Return ""
            Return Ar\Name$
        EndIf

        If kind = "faction"
            If refID < 0 Or refID > 99 Then Return ""
            Local fName$ = FactionNames$(refID)
            If fName = "" Then Return ""
            Return fName
        EndIf

        If kind = "animset"
            // AnimSet is iterated, not array-indexed -- walk to find by ID.
            For As.AnimSet = Each AnimSet
                If As\ID = refID Then Return As\Name$
            Next
            Return ""
        EndIf

        If kind = "settings"
            // Singleton project-config "entity". refID is ignored.
            Return "Project Settings"
        EndIf

        If kind = "script"
            // refID is the ScriptFile\Index from Scripts_Init.
            Local sf.ScriptFile = Scripts_GetByIndex(refID)
            If sf = Null Then Return ""
            Return sf\Name$ + ".rsl"
        EndIf

        If kind = "texture"
            // refID is the TextureEntry\Index from Textures_Init.
            Local te.TextureEntry = Textures_GetByIndex(refID)
            If te = Null Then Return ""
            Return te\Filename$ + " #" + Str(te\ID)
        EndIf

        If kind = "mesh"
            // refID is the MeshEntry\Index from Meshes_Init.
            Local mh.MeshEntry = Meshes_GetByIndex(refID)
            If mh = Null Then Return ""
            Return mh\Filename$ + " #" + Str(mh\ID)
        EndIf

        If kind = "sound"
            // refID is the SoundEntry\Index from Sounds_Init.
            Local sd.SoundEntry = Sounds_GetByIndex(refID)
            If sd = Null Then Return ""
            Return sd\Filename$ + " #" + Str(sd\ID)
        EndIf

        If kind = "music"
            // refID is the MusicEntry\Index from Music_Init.
            Local mu.MusicEntry = Music_GetByIndex(refID)
            If mu = Null Then Return ""
            Return mu\Filename$ + " #" + Str(mu\ID)
        EndIf

        If kind = "stats"
            Return "Project Stats"
        EndIf

        Return ""
    End Method


    // -------------------------------------------------------------------------
    // renderChip -- draw a clickable chip, hit-test mouse, dispatch on click.
    //
    // Return codes:
    //   0  no interaction this frame
    //   1  left click consumed -- chip already called Threads::jump
    //   2  right click consumed -- caller should open a picker for (kind, refID)
    //
    // The chip handles the navigation internally so callers can be dumb --
    // they just lay out the rect and pass mouse state. Right-click never
    // jumps; it's a signal to the Composer's chipRow to open the palette
    // in picker mode targeting the field this chip represents.
    //
    // Broken refs accept right-click (so the user can pick a replacement
    // for a dangling chip) but not left-click (no entity to jump to).
    // -------------------------------------------------------------------------
    Method renderChip%(x%, y%, w%, h%, kind$, refID%, mx%, my%, clicked%, rightClicked%)
        Local hovered% = (mx >= x And mx < x + w And my >= y And my < y + h)

        Local cName$ = Threads::lookupName(self, kind, refID)
        Local broken% = (cName = "")
        If broken = True Then cName = "(broken " + kind + " #" + Str(refID) + ")"

        // Background
        If hovered = True And broken = False
            LoomFill(x, y, w, h, LOOM_ARCANE_900_R, LOOM_ARCANE_900_G, LOOM_ARCANE_900_B)
        Else
            LoomFill(x, y, w, h, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
        EndIf

        // Border -- broken refs get a danger-red border so they read as wrong
        If broken = True
            LoomBorder(x, y, w, h, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
        Else If hovered = True
            LoomBorder(x, y, w, h, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
            LoomBorder(x + 1, y + 1, w - 2, h - 2, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        Else
            LoomBorder(x, y, w, h, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        EndIf

        // Kind icon (text glyph in brass, left-aligned)
        Local icon$ = Threads::kindGlyph(self, kind)
        LoomText(x + CHIP_PAD_X, y + 5, icon, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        // Name (parchment, danger-red if broken)
        If broken = True
            LoomText(x + CHIP_PAD_X + CHIP_ICON_W, y + 5, cName, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
        Else
            LoomText(x + CHIP_PAD_X + CHIP_ICON_W, y + 5, cName, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        EndIf

        // Right-side affordance -- on hover, show pencil hint for the
        // right-click-to-edit affordance; otherwise the > arrow for the
        // left-click-jump affordance.
        If hovered = True
            LoomText(x + w - 32, y + 5, "RMB:edit", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        Else If broken = False
            LoomText(x + w - 16, y + 5, ">", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        EndIf

        // Left click jumps (only when not broken).
        If hovered And clicked And broken = False
            Threads::jump(self, kind, refID)
            Return 1
        EndIf

        // Right click signals picker request (works on broken refs too,
        // so a dangling chip can be fixed in place).
        If hovered And rightClicked
            Return 2
        EndIf

        Return 0
    End Method


    // -------------------------------------------------------------------------
    // kindGlyph -- short glyph used as the kind icon in chips. Pure mapping
    // from kind string to display character; lives on the type for symmetry
    // even though it doesn't read self.
    // -------------------------------------------------------------------------
    Method kindGlyph$(kind$)
        If kind = "actor"   Then Return "A"
        If kind = "item"    Then Return "I"
        If kind = "spell"   Then Return "S"
        If kind = "zone"    Then Return "Z"
        If kind = "faction" Then Return "F"
        If kind = "animset" Then Return "M"
        If kind = "script"  Then Return "x"      ; ".rsl" looks like an x-ish glyph
        If kind = "texture" Then Return "T"
        If kind = "mesh"    Then Return "m"
        If kind = "sound"   Then Return "s"
        If kind = "music"   Then Return "M"
        Return "?"
    End Method
End Type
