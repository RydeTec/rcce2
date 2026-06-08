Strict

// =============================================================================
// Loom/Browser.bb -- everything-browser (entity picker grid by category)
// =============================================================================
//
// The boot surface. Six categories (actor / item / spell / zone / faction /
// animset), each renders as a grid of clickable cards. Clicking a card calls
// Threads::focus on the held Threads reference, which the Composer then reads
// to paint its detail page.
//
// Per-category card content (kept compact so 3 cards fit per row at 1280):
//   actor   : "Race [Class]" + faction name + XP multiplier
//   item    : name + type label (Weapon / Armour / Potion / etc.) + value
//   spell   : name + "Recharge Ns" subtitle
//   zone    : name + portal/spawn/trigger counts
//   faction : name + member count (computed: actors whose DefaultFaction
//             equals this faction index)
//   animset : name + clip count + computed "used by" count
//
// Architecture: Type with Methods, called as `Browser::method(self, args)`.
// Holds a reference to the shared Threads instance (set at construction)
// so card clicks can dispatch the focus change without globals.


// Layout constants. BR_TOP_RIBBON is the Y where the tab bar starts (i.e.
// the conscience ribbon + the brand strip both fit above it). The brand
// strip height is BR_BRAND_STRIP_H and starts at y = LOOM_TOP_RIBBON_H.
Const BR_BRAND_STRIP_H = 56
Const BR_TOP_RIBBON    = LOOM_TOP_RIBBON_H + BR_BRAND_STRIP_H    // 28 + 56 = 84
Const BR_TAB_BAR_H     = 36
Const BR_FILTER_BAR_H  = 30
// Y where the filter bar starts (just below the tab bar). cardVisible
// and the card-grid scroll math derive the grid's top clip from this.
Const BR_FILTER_BAR_Y  = BR_TOP_RIBBON + BR_TAB_BAR_H
Const BR_BOT_RIBBON    = 36
Const BR_SECTION_PAD   = 28
Const BR_CARD_W        = 300
Const BR_CARD_H        = 96
Const BR_CARD_GAP      = 14

// Filter input cursor blink rate (ms). Matches Composer's edit cursor cadence
// so the two surfaces feel like one input system.
Const BR_FILTER_CURSOR_PERIOD = 1000


// -----------------------------------------------------------------------------
// BrowserCategory -- one entry per category, iterated in insertion order to
// drive the tab bar's rendering order. Owned by Browser; instances are
// allocated in Browser::create and live for the Browser's lifetime.
// -----------------------------------------------------------------------------
Type BrowserCategory
    Field Kind$
    Field Title$
End Type


// -----------------------------------------------------------------------------
// SelectedEntity -- one entry in the bulk-edit selection set. Allocated by
// Browser::toggleInSelection on Shift+Click; freed by clearSelection or by
// toggleInSelection when the kind+refID is already in the set.
//
// Selection is across-kind by design ("I want to bump the value on these
// five potions AND those three rings"). The composer's bulk-edit view
// (coming in a follow-up iteration) handles per-kind dispatch.
//
// Manual Delete -- no EnableGC in Loom modules; mirror of LoomFocusEntry's
// lifecycle pattern.
// -----------------------------------------------------------------------------
Type SelectedEntity
    Field Kind$
    Field RefID%
End Type


// =============================================================================
// Browser -- everything-grid surface.
// =============================================================================
Type Browser
    Field category$            // currently-selected kind: actor/item/spell/...
    Field threads.Threads      // shared focus state, set by caller

    // Per-frame click latch -- set inside drawCardChrome when a card is
    // clicked, read by drawCardGrid at the end of the frame. Lives on the
    // Type rather than as a Method-local because BlitzForge Strict mode
    // rejects re-assigning a Method-scope Local from inside nested If/For
    // blocks ("assignment should start with local/global/const"); Field
    // writes through `self\` work at any nesting depth.
    Field cardClickLatch%

    // Per-category search filter. Empty = no filter; non-empty = case-
    // insensitive substring match against each card's primary display name
    // (Race+Class for actors, Name for items/spells/zones, etc.). The same
    // string applies to whatever category is active -- intentional, so the
    // filter persists when tabbing across categories ("looking for goblin
    // across actors AND items").
    //
    // Edit-buffer state: keyboard pumping is unconditional while the browser
    // surface is foreground (no palette / composer-edit-mode in front), so
    // typing into the browser feels immediate without a click-into-input.
    Field filterQuery$

    // Atlas state -- the Zones tab can swap from card grid to a spatial
    // portal-graph view. Atlas instance is set by Loom.bb at construction
    // via Browser::setAtlas; atlasMode tracks whether the user has toggled
    // it on (persists across tab switches so a return to Zones honors the
    // last setting). Defaults to card-mode.
    Field atlas.Atlas
    Field atlasMode%

    // Bulk-edit selection count -- the actual Each-SelectedEntity pool
    // is the source of truth; this Field is a cheap accessor for the
    // hot-loop count check (drawCardChrome reads it every card).
    Field selectionCount%


    // Keyboard-navigation state. selectedIndex is the per-category cursor
    // (which card the arrow keys highlight). Mouse hover doesn't move it
    // (so it stays put as the user reaches for the keyboard); arrow keys
    // do. Enter focuses the selected card (same as clicking it).
    //
    // Storing one selectedIndex (not per-category) is intentional -- when
    // the user tabs from Actors to Items, the selectedIndex clamps to the
    // new category's range. The simpler shape beats per-category state
    // for the modest UX cost.
    Field selectedIndex%

    // Cached grid geometry from the previous frame's drawCardGrid call --
    // arrow-key pump uses these to know how many cards exist (clamp) and
    // how wide a row is (Up/Down jumps). First frame defaults to 1 col / 0
    // count, which is harmless.
    Field lastCols%
    Field lastCount%

    // Pending-Enter flag: set by pumpNavKeyboard when the user presses
    // Enter; consumed by the per-kind grid method when it reaches the
    // selectedIndex card, dispatching Threads::focus then clearing.
    Field pendingEnter%

    // Vertical scroll for the card grid (pixels). Without it, a category
    // with more rows than fit on screen silently clipped the overflow
    // with no way to reach it. Driven by the mouse wheel (gated to the
    // cursor being over the grid). cardScrollMax / lastGridRows are
    // measured from the prior frame's card count so the clamp is correct.
    // lastScrollCat / lastScrollFilter reset the scroll to the top when
    // the visible set changes (category switch or filter edit).
    Field cardScroll%
    Field lastGridRows%
    Field lastScrollCat$
    Field lastScrollFilter$


    Method create.Browser(threads.Threads)
        self\threads = threads
        self\category = "actor"     // richest content; most useful starting point
        self\cardClickLatch = False
        self\filterQuery = ""
        self\atlas = Null
        self\atlasMode = False
        self\selectedIndex = 0
        self\lastCols = 1
        self\lastCount = 0
        self\pendingEnter = False
        self\selectionCount = 0
        self\cardScroll = 0
        self\lastGridRows = 0
        self\lastScrollCat = ""
        self\lastScrollFilter = ""

        // Build the ordered category list. Iterated via `Each BrowserCategory`
        // in insertion order (Blitz3D's global type pool is FIFO) -- also the
        // tab-bar order.
        Browser::addCategory(self, "actor",   "Actors")
        Browser::addCategory(self, "item",    "Items")
        Browser::addCategory(self, "spell",   "Spells")
        Browser::addCategory(self, "zone",    "Zones")
        Browser::addCategory(self, "faction", "Factions")
        Browser::addCategory(self, "animset", "Animation Sets")
        // Tools tab: standalone editor launchers (RC Architect, Terrain
        // Editor, etc.). Not an entity kind, so the composer / new / save
        // affordances don't apply on this tab -- it's pure launch surface.
        Browser::addCategory(self, "tools",   "Tools")
        // Scripts tab: every .rsl file in Data\Server Data\Scripts\ as
        // a clickable card. Composer view shows content preview + reverse
        // refs (which Zone/Item/Spell references this script). Closes
        // the "what scripts exist?" gap that GUE doesn't fill either.
        Browser::addCategory(self, "script",  "Scripts")
        // Textures tab: every defined texture from Data\Game Data\Textures.dat
        // as a 64x64 thumbnail card. Composer shows the texture preview +
        // reverse refs (every Item/Spell/Actor field that points to it).
        // Powered by TextureCatalog.bb (populated at boot).
        Browser::addCategory(self, "texture", "Textures")
        // Meshes tab: every defined mesh from Data\Game Data\Meshes.dat
        // as a card. Composer shows full 3D orbit/zoom preview via the
        // existing MeshPreview widget. Sibling of Textures from iter 66.
        Browser::addCategory(self, "mesh",    "Meshes")
        // Sounds tab: every defined sound from Data\Game Data\Sounds.dat
        // as a card. Composer view adds a Play button for in-place
        // audition -- a feature GUE doesn't expose at all.
        Browser::addCategory(self, "sound",   "Sounds")
        // Music tab: every defined music track. No reverse refs since
        // music IDs aren't statically referenced from data Loom edits.
        // Audition + browse only.
        Browser::addCategory(self, "music",   "Music")
        // Stats tab: singleton read-only dashboard showing project
        // health at a glance. Entity counts, top zones by spawn
        // density, asset catalog sizes, issue breakdown.
        Browser::addCategory(self, "stats",   "Stats")
        // Settings tab: project-level configuration singleton (Misc.dat /
        // Other.dat / Money.dat / Hosts.dat). Clicking the tab focuses
        // the singleton composer view directly -- no card grid since
        // there's only one "entity" here.
        Browser::addCategory(self, "settings", "Settings")

        Return self
    End Method


    Method addCategory(kind$, title$)
        Local c.BrowserCategory = New BrowserCategory()
        c\Kind = kind$
        c\Title = title$
    End Method


    // -------------------------------------------------------------------------
    // setAtlas -- injection point for the Loom top-level type to share the
    // Atlas instance with the Browser. Called once at construction.
    // -------------------------------------------------------------------------
    Method setAtlas(atlas.Atlas)
        self\atlas = atlas
    End Method


    // -------------------------------------------------------------------------
    // renderAndUpdate -- per-frame paint + hit-test.
    //
    // inputEnabled gates keyboard pumping into the filter buffer -- when the
    // palette is open or the composer is in field-edit mode, those surfaces
    // own the keystrokes and the browser must stay quiet.
    //
    // composerWidth: 0 when the composer is hidden, else the panel's
    // pixel width (CMP_W). The card grid uses (sw - composerWidth) for
    // its layout so cards in the right column don't end up half-hidden
    // behind the composer. Chrome bands (brand strip, tab bar, filter
    // bar, footer) still span full width -- the composer renders on top
    // of those, hiding the right end visually, which is the right
    // behavior since the composer's own border / accent reads as
    // continuing the chrome.
    // -------------------------------------------------------------------------
    Method renderAndUpdate%(sw%, sh%, project$, inputEnabled%, composerWidth%)
        Local mx% = MouseX()
        Local my% = MouseY()
        Local clicked% = Loom_MouseClicked()

        // Drain keyboard. Arrow keys + Enter handled FIRST so they don't
        // dribble into the filter buffer; the filter pump skips arrows.
        // Esc is owned by the outer Loom frame -- when the filter is
        // non-empty, the outer Esc handler clears it first via
        // Browser::clearFilter (called from Loom.bb).
        If inputEnabled = True
            Browser::pumpNavKeyboard(self)
            Browser::pumpFilterKeyboard(self)
        EndIf

        // Background gradient
        LoomGradientV(0, 0, sw, sh, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B, LOOM_STONE_950_R, LOOM_STONE_950_G, LOOM_STONE_950_B)

        // Chrome -- full width; composer renders on top to occlude its
        // right end. Cheaper than recomputing the chrome's layout when
        // the composer toggles.
        Browser::drawTopRibbon(self, sw, project$)
        Browser::drawTabBar(self, sw, mx, my, clicked)
        Browser::drawFilterBar(self, sw, mx, my, clicked)
        Browser::drawFooter(self, sw, sh)

        // Card grid -- shrink the effective width by composerWidth so
        // cards never end up half-hidden behind the composer panel.
        //
        // When ANY entity is focused, the composer covers the entire left
        // content area -- the zone 3D viewport (zone) or the thread web
        // (everything else). Skip the card grid so it doesn't paint behind /
        // steal the wheel + clicks meant for that surface (the grid runs
        // BEFORE the composer in the frame, and its wheel handler was
        // consuming the tick before the viewport could read it). The grid
        // returns when focus clears (Esc) or in bulk-select mode (focusKind="").
        self\cardClickLatch = False
        If self\threads <> Null And self\threads\focusKind <> ""
            Return False
        EndIf
        Browser::drawCardGrid(self, sw - composerWidth, sh, mx, my, clicked, inputEnabled)
        Return self\cardClickLatch
    End Method


    // -------------------------------------------------------------------------
    // hasFilter / clearFilter -- public surface for the outer Loom frame so
    // Esc on a non-empty filter clears the filter instead of falling through
    // to composer/exit handling. Keeps the priority chain explicit:
    //   palette > composer-edit > filter clear > back-stack pop > exit
    // -------------------------------------------------------------------------
    Method hasFilter%()
        If self\filterQuery <> "" Then Return True
        Return False
    End Method

    Method clearFilter()
        self\filterQuery = ""
        WriteLog(LoomLog, "Browser: filter cleared")
    End Method


    // -------------------------------------------------------------------------
    // Selection-set accessors -- used by the outer Loom Esc handler to
    // prioritize selection-clear above back-stack pop, and by the future
    // bulk-edit composer view to discover what's selected.
    // -------------------------------------------------------------------------
    Method hasSelection%()
        If self\selectionCount > 0 Then Return True
        Return False
    End Method


    Method getSelectionCount%()
        Return self\selectionCount
    End Method


    Method clearSelection()
        Local e.SelectedEntity
        For e = Each SelectedEntity
            Delete e
        Next
        self\selectionCount = 0
        WriteLog(LoomLog, "Browser: selection cleared")
    End Method


    Method isSelected%(kind$, refID%)
        Local e.SelectedEntity
        For e = Each SelectedEntity
            If e\Kind = kind And e\RefID = refID Then Return True
        Next
        Return False
    End Method


    // -------------------------------------------------------------------------
    // toggleInSelection -- add (kind, refID) if not present; remove if
    // present. Called from drawCardChrome on Shift+Click.
    // -------------------------------------------------------------------------
    Method toggleInSelection(kind$, refID%)
        // Try to remove existing entry first.
        Local e.SelectedEntity
        For e = Each SelectedEntity
            If e\Kind = kind And e\RefID = refID
                Delete e
                self\selectionCount = self\selectionCount - 1
                WriteLog(LoomLog, "Browser: deselected " + kind + "#" + Str(refID) + " (now " + Str(self\selectionCount) + ")")
                Return
            EndIf
        Next

        // Not present -- add.
        Local newEntry.SelectedEntity = New SelectedEntity()
        newEntry\Kind = kind
        newEntry\RefID = refID
        self\selectionCount = self\selectionCount + 1
        WriteLog(LoomLog, "Browser: selected " + kind + "#" + Str(refID) + " (now " + Str(self\selectionCount) + ")")
    End Method


    // -------------------------------------------------------------------------
    // pumpFilterKeyboard -- drain printable chars + Backspace into filterQuery.
    // Does NOT consume Esc (outer frame handles that). Does NOT consume Enter
    // (no commit semantics -- the filter is always live).
    //
    // Ctrl-anything is skipped so Ctrl+K opening the palette doesn't dribble
    // a "k" into the filter buffer before the palette claims keys.
    // -------------------------------------------------------------------------
    Method pumpFilterKeyboard()
        // Skip drain when any control modifier is held -- prevents Ctrl+K
        // (palette open) from depositing characters; also Ctrl+L, Ctrl+S
        // (future Save shortcut), etc.
        If KeyDown(29) Or KeyDown(157) Then Return

        // Backspace (scan code 14)
        If KeyHit(14) And Len(self\filterQuery) > 0
            self\filterQuery = Left$(self\filterQuery, Len(self\filterQuery) - 1)
        EndIf

        // Drain GetKey queue for printable ASCII
        Local k% = GetKey()
        While k > 0
            If k >= 32 And k <= 126
                self\filterQuery = self\filterQuery + Chr(k)
            EndIf
            k = GetKey()
        Wend
    End Method


    // -------------------------------------------------------------------------
    // pumpNavKeyboard -- arrow keys + Enter on the card grid. Move the
    // selectedIndex cursor by 1 (left/right) or by lastCols (up/down);
    // Enter sets pendingEnter so the next per-kind grid pass focuses the
    // selected card.
    //
    // Skipped when Ctrl is held (so Ctrl+K / Ctrl+H global shortcuts
    // don't accidentally move the cursor on the way through).
    // -------------------------------------------------------------------------
    Method pumpNavKeyboard()
        If KeyDown(29) Or KeyDown(157) Then Return

        // Scan codes: 200=Up, 208=Down, 203=Left, 205=Right, 28=Enter
        If KeyHit(200) And self\selectedIndex >= self\lastCols
            self\selectedIndex = self\selectedIndex - self\lastCols
        EndIf
        If KeyHit(208)
            Local nextDown% = self\selectedIndex + self\lastCols
            If nextDown < self\lastCount Then self\selectedIndex = nextDown
        EndIf
        If KeyHit(203) And self\selectedIndex > 0
            self\selectedIndex = self\selectedIndex - 1
        EndIf
        If KeyHit(205)
            If self\selectedIndex + 1 < self\lastCount Then self\selectedIndex = self\selectedIndex + 1
        EndIf
        If KeyHit(28)
            self\pendingEnter = True
        EndIf

        // Clamp (defensive -- category switch could have shrunk the count
        // since the last frame's drawCardGrid).
        If self\selectedIndex < 0 Then self\selectedIndex = 0
        If self\lastCount > 0 And self\selectedIndex >= self\lastCount Then self\selectedIndex = self\lastCount - 1
    End Method


    // -------------------------------------------------------------------------
    // matchesFilter -- case-insensitive substring check used by every per-
    // kind grid renderer to skip cards that don't match the active filter.
    // -------------------------------------------------------------------------
    Method matchesFilter%(name$)
        If self\filterQuery = "" Then Return True
        Local nm$ = Lower$(name)
        Local q$  = Lower$(self\filterQuery)
        If Instr(nm, q) > 0 Then Return True
        Return False
    End Method


    // -------------------------------------------------------------------------
    // drawFilterBar -- thin row above the card grid with a "+ New" button on
    // the left, a search input on the right, and a help hint between them.
    // The input shows the live filter buffer with a blinking cursor; typing
    // anywhere on the browser surface lands here.
    // -------------------------------------------------------------------------
    Method drawFilterBar(sw%, mx%, my%, clicked%)
        Local y% = BR_TOP_RIBBON + BR_TAB_BAR_H
        Local h% = BR_FILTER_BAR_H

        LoomGradientV(0, y, sw, h, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B)
        LoomHRule(0, y + h, sw, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)

        // "+ New" button on the left -- creates a fresh entity of the
        // current category and focuses it. Dispatches to EntityFactory.
        // Hidden on the Tools tab since tools aren't entities -- they're
        // launchers for external editor binaries.
        Local nbX% = 20
        Local nbY% = y + 4
        Local nbW% = 96
        Local nbH% = 22
        Local nbHover% = False
        If self\category <> "tools" And self\category <> "script" And self\category <> "texture" And self\category <> "mesh" And self\category <> "sound" And self\category <> "music" And self\category <> "stats"
            nbHover = (mx >= nbX And mx < nbX + nbW And my >= nbY And my < nbY + nbH)

            If nbHover = True
                LoomFill(nbX, nbY, nbW, nbH, LOOM_ARCANE_700_R, LOOM_ARCANE_700_G, LOOM_ARCANE_700_B)
                LoomBorder(nbX, nbY, nbW, nbH, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
            Else
                LoomFill(nbX, nbY, nbW, nbH, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
                LoomBorder(nbX, nbY, nbW, nbH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            EndIf
            LoomText(nbX + 10, nbY + 4, "+ New " + Browser::categoryLabel(self, self\category), LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

            If nbHover And clicked
                EntityFactory_Create(self\category, self\threads)
                // EntityFactory focuses the new entity on success; we don't
                // need to do anything else here. Leave cardClickLatch alone
                // (the Composer takes over from here).
            EndIf
        EndIf

        // Card / Atlas view toggle -- only present on the Zones tab. Lives
        // immediately right of the "+ New" button so the action cluster
        // stays packed together.
        Local hintX% = nbX + nbW + 16
        If self\category = "zone" And self\atlas <> Null
            Local tbX% = nbX + nbW + 10
            Local tbY% = y + 4
            Local tbW% = 130
            Local tbH% = 22
            Local tbHover% = (mx >= tbX And mx < tbX + tbW And my >= tbY And my < tbY + tbH)

            If tbHover = True
                LoomFill(tbX, tbY, tbW, tbH, LOOM_ARCANE_700_R, LOOM_ARCANE_700_G, LOOM_ARCANE_700_B)
                LoomBorder(tbX, tbY, tbW, tbH, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
            Else
                LoomFill(tbX, tbY, tbW, tbH, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
                LoomBorder(tbX, tbY, tbW, tbH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            EndIf

            // Active half gets a brass fill underline; inactive half stays
            // neutral. Click anywhere on the button flips.
            Local halfW% = tbW / 2
            If self\atlasMode = False
                LoomFill(tbX, tbY + tbH - 3, halfW, 3, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            Else
                LoomFill(tbX + halfW, tbY + tbH - 3, halfW, 3, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            EndIf
            LoomText(tbX + 10, tbY + 4, "Card", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
            LoomText(tbX + halfW + 10, tbY + 4, "Atlas", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

            If tbHover And clicked
                If mx < tbX + halfW
                    self\atlasMode = False
                Else
                    self\atlasMode = True
                EndIf
                WriteLog(LoomLog, "Browser: zone view -> " + Browser::viewModeLabel(self))
            EndIf
            hintX = tbX + tbW + 16
        EndIf

        // Hint between the button cluster and the input
        LoomText(hintX, y + 8, "TYPE TO FILTER  |  CTRL+K SEARCH ALL", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        // Input on the right -- 280px wide
        Local iw% = 280
        Local ix% = sw - iw - 20
        Local iy% = y + 4
        Local ih% = 22

        // Background -- darker when empty, arcane-tinted when active
        If self\filterQuery <> ""
            LoomFill(ix, iy, iw, ih, LOOM_ARCANE_900_R, LOOM_ARCANE_900_G, LOOM_ARCANE_900_B)
            LoomBorder(ix, iy, iw, ih, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        Else
            LoomFill(ix, iy, iw, ih, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
            LoomBorder(ix, iy, iw, ih, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        EndIf

        // Prompt glyph
        LoomText(ix + 8, iy + 4, ">", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        // Query string (or placeholder)
        If self\filterQuery = ""
            LoomText(ix + 22, iy + 4, "filter " + self\category + "s...", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
        Else
            LoomText(ix + 22, iy + 4, self\filterQuery, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

            // Blinking cursor at end -- only when filter is active so an
            // empty input doesn't blink at the user.
            If (MilliSecs() Mod BR_FILTER_CURSOR_PERIOD) < (BR_FILTER_CURSOR_PERIOD / 2)
                Local cursorX% = ix + 22 + StringWidth(self\filterQuery)
                LoomFill(cursorX, iy + 3, 2, 14, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
            EndIf
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // categoryLabel -- short singular form for the "+ New X" button label.
    // Could read from BrowserCategory\Title but those are plural ("Actors");
    // we want "Actor" for the button.
    // -------------------------------------------------------------------------
    Method categoryLabel$(kind$)
        If kind = "actor"   Then Return "Actor"
        If kind = "item"    Then Return "Item"
        If kind = "spell"   Then Return "Spell"
        If kind = "zone"    Then Return "Zone"
        If kind = "faction" Then Return "Faction"
        If kind = "animset" Then Return "Anim Set"
        If kind = "script"  Then Return "Script"
        If kind = "texture" Then Return "Texture"
        If kind = "mesh"    Then Return "Mesh"
        If kind = "sound"   Then Return "Sound"
        If kind = "music"   Then Return "Music"
        If kind = "stats"   Then Return "Stats"
        Return kind
    End Method


    // -------------------------------------------------------------------------
    // viewModeLabel -- short human label for the current zone view mode.
    // Used in WriteLog calls and could surface in the footer hint later.
    // -------------------------------------------------------------------------
    Method viewModeLabel$()
        If self\atlasMode = True Then Return "atlas"
        Return "card"
    End Method


    // -------------------------------------------------------------------------
    // Top brand strip -- sits between the Validation Conscience Ribbon
    // (top LOOM_TOP_RIBBON_H pixels) and the category tab bar. The brass
    // hairline at the bottom of the strip separates it from the tab bar.
    // -------------------------------------------------------------------------
    Method drawTopRibbon(sw%, project$)
        Local stripY% = LOOM_TOP_RIBBON_H
        // Chrome-mode varies the brand strip:
        //   tool      -- flat stone-900, single brass rule (utilitarian)
        //   balanced  -- subtle stone-800 -> stone-850 gradient + triple
        //                brass rule (current default)
        //   in-world  -- dramatic stone-700 -> stone-900 gradient + thick
        //                triple brass rule (more "engraved" feel)
        If Loom_ChromeIsTool() = True
            LoomFill(0, stripY, sw, BR_BRAND_STRIP_H, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B)
            LoomHRule(0, BR_TOP_RIBBON, sw, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        Else If Loom_ChromeIsInWorld() = True
            LoomGradientV(0, stripY, sw, BR_BRAND_STRIP_H, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B)
            LoomHRule(0, BR_TOP_RIBBON - 2, sw, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            LoomHRule(0, BR_TOP_RIBBON - 1, sw, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
            LoomHRule(0, BR_TOP_RIBBON,     sw, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            LoomHRule(0, BR_TOP_RIBBON + 1, sw, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
            LoomHRule(0, BR_TOP_RIBBON + 2, sw, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        Else
            LoomGradientV(0, stripY, sw, BR_BRAND_STRIP_H, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B)
            LoomHRule(0, BR_TOP_RIBBON - 1, sw, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
            LoomHRule(0, BR_TOP_RIBBON,     sw, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            LoomHRule(0, BR_TOP_RIBBON + 1, sw, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        EndIf

        // Brand mark in display font for visual weight; project name in
        // display font too since it's the user's anchor. Sub-label
        // ("Browser") stays in the body font as supporting text.
        LoomTheme_UseDisplay()
        LoomText(20, stripY + 14, "LOOM", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        LoomTextCentered(sw / 2, stripY + 18, project$, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        LoomTheme_UseBody()
        LoomText(20, stripY + 36, "Browser", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
    End Method


    // -------------------------------------------------------------------------
    // Category tab bar -- active tab gets a brass underline. Hit-test inline.
    // -------------------------------------------------------------------------
    Method drawTabBar(sw%, mx%, my%, clicked%)
        Local y% = BR_TOP_RIBBON
        Local h% = BR_TAB_BAR_H
        LoomGradientV(0, y, sw, h, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
        LoomHRule(0, y + h, sw, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)

        Local x% = 20
        For c.BrowserCategory = Each BrowserCategory
            Local w% = StringWidth(c\Title) + 40
            Local active% = (c\Kind = self\category)
            Local hovered% = (mx >= x And mx < x + w And my >= y And my < y + h)

            If hovered = True
                LoomFill(x, y, w, h, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
            EndIf

            If active = True
                LoomText(x + 20, y + 11, c\Title, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
                LoomFill(x + 8, y + h - 3, w - 16, 3, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            Else
                LoomText(x + 20, y + 11, c\Title, LOOM_STONE_200_R, LOOM_STONE_200_G, LOOM_STONE_200_B)
            EndIf

            If hovered And clicked
                self\category = c\Kind
                WriteLog(LoomLog, "Browser: category -> " + c\Kind)
            EndIf

            x = x + w + 6
        Next
    End Method


    // -------------------------------------------------------------------------
    // Card grid -- dispatcher to a per-kind grid method. Each per-kind method
    // owns its own loop counters and `count`; this avoids one giant
    // Else-If-chain Method that triggers Strict's nested-block reassignment
    // pathology, and is cleaner OO design anyway. Returns True via
    // self\cardClickLatch (which the per-kind methods set as a side effect).
    // -------------------------------------------------------------------------
    Method drawCardGrid%(sw%, sh%, mx%, my%, clicked%, inputEnabled%)
        Local gridX% = BR_SECTION_PAD
        Local gridY% = BR_TOP_RIBBON + BR_TAB_BAR_H + BR_FILTER_BAR_H + BR_SECTION_PAD
        Local gridW% = sw - (BR_SECTION_PAD * 2)
        Local cols% = (gridW + BR_CARD_GAP) / (BR_CARD_W + BR_CARD_GAP)
        If cols < 1 Then cols = 1

        // Cache geometry for the next frame's pumpNavKeyboard. Arrow keys
        // run BEFORE drawCardGrid so they operate on last-frame's cols /
        // count -- first frame defaults to (1, 0), harmless.
        self\lastCols = cols

        self\cardClickLatch = False

        Local cat$ = self\category

        // The Zones tab in Atlas mode swaps the card grid for the spatial
        // portal-graph view, which owns its own wheel (zoom) and origin.
        // Card-grid scroll must NOT run in that mode.
        Local atlasActive% = (cat = "zone" And self\atlasMode = True And self\atlas <> Null)

        // ---- Card-grid vertical scroll -------------------------------------
        // Reset to the top whenever the visible set changes (category switch
        // or filter edit) so a fresh list never opens mid-scroll.
        If self\category <> self\lastScrollCat Or self\filterQuery <> self\lastScrollFilter
            self\cardScroll = 0
        EndIf
        self\lastScrollCat = self\category
        self\lastScrollFilter = self\filterQuery

        // Wheel scroll, gated to the cursor being over the grid. `sw` here
        // is already reduced by the composer width (drawCardGrid is called
        // with sw - composerWidth), so `mx < sw` means "left of the composer
        // panel" and the composer owns the wheel when the cursor is on it.
        // Loom_MouseWheel() is a true per-frame delta after the BeginFrame
        // MouseZ fix; Loom_ConsumeWheel() stops any later surface re-applying.
        Local rowPitch% = BR_CARD_H + BR_CARD_GAP
        Local gridViewTop% = BR_FILTER_BAR_Y + BR_FILTER_BAR_H + 2
        Local gridViewBottom% = sh - BR_BOT_RIBBON
        // Gate wheel consumption on inputEnabled: when a modal (Help, Issues,
        // palette, etc.) is open, Loom sets browserInput=False, and the modal
        // -- which renders AFTER the browser in the frame -- needs the wheel
        // tick for its own scroll. Without this gate the card grid (sitting
        // behind the modal overlay) ate the tick first and the modal's wheel
        // appeared dead. (Reported: Help modal wheel not scrolling.)
        Local wheel% = Loom_MouseWheel()
        If wheel <> 0 And inputEnabled = True And atlasActive = False And mx < sw And my >= gridViewTop And my < gridViewBottom
            self\cardScroll = self\cardScroll - wheel * rowPitch
            If self\cardScroll < 0 Then self\cardScroll = 0
            Local contentH% = self\lastGridRows * rowPitch
            Local viewH% = gridViewBottom - gridViewTop
            Local maxScroll% = contentH - viewH
            If maxScroll < 0 Then maxScroll = 0
            If self\cardScroll > maxScroll Then self\cardScroll = maxScroll
            Loom_ConsumeWheel()
        EndIf

        // Apply the scroll offset to the grid origin so rows shift up as
        // the user scrolls down. cardVisible() clips rows that move above
        // the grid top or below the bottom ribbon. (Not in atlas mode --
        // the atlas places its own viewport at the unscrolled gridY.)
        If atlasActive = False Then gridY = gridY - self\cardScroll

        // Zone tab + atlasMode = swap the card grid for the spatial atlas.
        // Atlas owns its own paint + hit-test inside the viewport rect.
        // (Filter applies to the card view only -- the atlas always shows
        //  every zone since spatial context is what it's for.)
        If atlasActive = True
            Local viewportH% = sh - gridY - BR_BOT_RIBBON - BR_SECTION_PAD
            Local hit% = Atlas::renderAndUpdate(self\atlas, gridX, gridY, gridW, viewportH)
            If hit = True Then self\cardClickLatch = True
            self\lastCount = 0    // Atlas doesn't participate in keyboard nav
            Return self\cardClickLatch
        EndIf

        Local count% = 0

        // Clip card drawing to the grid band (below the filter bar, above the
        // footer). cardVisible() only decides WHETHER to draw a card, not clip
        // it -- so a partially-scrolled card at the top/bottom edge was drawn
        // in full and bled over the tab/filter chrome (top) or the footer
        // (bottom). The 2D Viewport pixel-clips them to the band. Reset to the
        // full buffer after the dispatch so later surfaces aren't clipped.
        Local clipTop% = BR_FILTER_BAR_Y + BR_FILTER_BAR_H + 2
        Local clipBot% = sh - BR_BOT_RIBBON
        If clipBot > clipTop
            Viewport 0, clipTop, sw, clipBot - clipTop
        EndIf

        If cat = "actor"
            count = Browser::drawActorGrid(self, sw, sh, mx, my, clicked, gridX, gridY, cols)
        EndIf
        If cat = "item"
            count = Browser::drawItemGrid(self, sw, sh, mx, my, clicked, gridX, gridY, cols)
        EndIf
        If cat = "spell"
            count = Browser::drawSpellGrid(self, sw, sh, mx, my, clicked, gridX, gridY, cols)
        EndIf
        If cat = "zone"
            count = Browser::drawZoneGrid(self, sw, sh, mx, my, clicked, gridX, gridY, cols)
        EndIf
        If cat = "faction"
            count = Browser::drawFactionGrid(self, sw, sh, mx, my, clicked, gridX, gridY, cols)
        EndIf
        If cat = "animset"
            count = Browser::drawAnimSetGrid(self, sw, sh, mx, my, clicked, gridX, gridY, cols)
        EndIf
        If cat = "tools"
            count = Browser::drawToolsGrid(self, sw, sh, mx, my, clicked, gridX, gridY, cols)
        EndIf
        If cat = "script"
            count = Browser::drawScriptsGrid(self, sw, sh, mx, my, clicked, gridX, gridY, cols)
        EndIf
        If cat = "texture"
            count = Browser::drawTexturesGrid(self, sw, sh, mx, my, clicked, gridX, gridY, cols)
        EndIf
        If cat = "mesh"
            count = Browser::drawMeshesGrid(self, sw, sh, mx, my, clicked, gridX, gridY, cols)
        EndIf
        If cat = "sound"
            count = Browser::drawSoundsGrid(self, sw, sh, mx, my, clicked, gridX, gridY, cols)
        EndIf
        If cat = "music"
            count = Browser::drawMusicGrid(self, sw, sh, mx, my, clicked, gridX, gridY, cols)
        EndIf
        If cat = "stats"
            count = Browser::drawStatsCard(self, sw, sh, mx, my, clicked, gridX, gridY)
        EndIf
        If cat = "settings"
            count = Browser::drawSettingsCard(self, sw, sh, mx, my, clicked, gridX, gridY)
        EndIf

        // Done drawing cards -- restore the full-buffer 2D viewport so the
        // empty-state text, composer, ribbon, and modals aren't clipped.
        Viewport 0, 0, GraphicsWidth(), GraphicsHeight()

        // Cache for next frame's pumpNavKeyboard.
        self\lastCount = count

        // Rows laid out this frame -> next frame's scroll clamp. ceil(count/cols).
        If cols < 1 Then cols = 1
        self\lastGridRows = (count + cols - 1) / cols

        // Pending Enter is consumed by drawCardChrome when the iteration
        // reaches selectedIndex; if it didn't (e.g. selectedIndex past the
        // visible/filtered subset), clear it anyway so it doesn't fire
        // later. Defensive cleanup -- no behavior change in the happy path.
        self\pendingEnter = False

        // Empty-state copy -- different message when the project HAS entities
        // of this kind but the filter excluded them all.
        If count = 0
            Local emptyMsg$
            If self\filterQuery <> ""
                emptyMsg = "No " + cat + "s match " + Chr(34) + self\filterQuery + Chr(34) + "  |  Esc to clear filter"
            Else
                emptyMsg = "No " + cat + "s in this project yet."
            EndIf
            LoomTextCentered(sw / 2, sh / 2, emptyMsg, LOOM_STONE_200_R, LOOM_STONE_200_G, LOOM_STONE_200_B)
        EndIf

        Return self\cardClickLatch
    End Method


    // -------------------------------------------------------------------------
    // Per-kind grid renderers. Each iterates its data store, lays out cards,
    // sets self\cardClickLatch on hover-click, and returns the count of
    // cards rendered (for the dispatcher's empty-state check).
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // cardVisible -- shared clip test for the scrollable card grid. A card
    // at screen-y `cy` is visible iff it overlaps the band between the
    // filter bar (top) and the bottom status ribbon. Replaces the old
    // bottom-only clip so cards scrolled above the grid don't paint over
    // the tab/filter chrome.
    // -------------------------------------------------------------------------
    Method cardVisible%(cy%, sh%)
        Local gridTop% = BR_FILTER_BAR_Y + BR_FILTER_BAR_H + 2
        If cy + BR_CARD_H <= gridTop Then Return False
        If cy >= sh - BR_BOT_RIBBON Then Return False
        Return True
    End Method


    Method drawActorGrid%(sw%, sh%, mx%, my%, clicked%, gridX%, gridY%, cols%)
        Local col% = 0
        Local row% = 0
        Local count% = 0
        For Ac.Actor = Each Actor
            Local aName$ = Ac\Race$ + " [" + Ac\Class$ + "]"
            If Browser::matchesFilter(self, aName) = True
                Local cx% = gridX + col * (BR_CARD_W + BR_CARD_GAP)
                Local cy% = gridY + row * (BR_CARD_H + BR_CARD_GAP)
                If Browser::cardVisible(self, cy, sh) = True
                    Browser::drawCardChrome(self, "actor", Ac\ID, cx, cy, mx, my, clicked, count)
                    Browser::drawActorCardBody(self, Ac, cx, cy)
                EndIf
                count = count + 1
                col = col + 1
                If col >= cols Then col = 0 : row = row + 1
            EndIf
        Next
        Return count
    End Method


    Method drawItemGrid%(sw%, sh%, mx%, my%, clicked%, gridX%, gridY%, cols%)
        Local col% = 0
        Local row% = 0
        Local count% = 0
        For It.Item = Each Item
            If Browser::matchesFilter(self, It\Name$) = True
                Local cx% = gridX + col * (BR_CARD_W + BR_CARD_GAP)
                Local cy% = gridY + row * (BR_CARD_H + BR_CARD_GAP)
                If Browser::cardVisible(self, cy, sh) = True
                    Browser::drawCardChrome(self, "item", It\ID, cx, cy, mx, my, clicked, count)
                    Browser::drawItemCardBody(self, It, cx, cy)
                EndIf
                count = count + 1
                col = col + 1
                If col >= cols Then col = 0 : row = row + 1
            EndIf
        Next
        Return count
    End Method


    Method drawSpellGrid%(sw%, sh%, mx%, my%, clicked%, gridX%, gridY%, cols%)
        Local col% = 0
        Local row% = 0
        Local count% = 0
        For Sp.Spell = Each Spell
            If Browser::matchesFilter(self, Sp\Name$) = True
                Local cx% = gridX + col * (BR_CARD_W + BR_CARD_GAP)
                Local cy% = gridY + row * (BR_CARD_H + BR_CARD_GAP)
                If Browser::cardVisible(self, cy, sh) = True
                    Browser::drawCardChrome(self, "spell", Sp\ID, cx, cy, mx, my, clicked, count)
                    Browser::drawSpellCardBody(self, Sp, cx, cy)
                EndIf
                count = count + 1
                col = col + 1
                If col >= cols Then col = 0 : row = row + 1
            EndIf
        Next
        Return count
    End Method


    Method drawZoneGrid%(sw%, sh%, mx%, my%, clicked%, gridX%, gridY%, cols%)
        Local col% = 0
        Local row% = 0
        Local count% = 0
        For Ar.Area = Each Area
            If Browser::matchesFilter(self, Ar\Name$) = True
                Local cx% = gridX + col * (BR_CARD_W + BR_CARD_GAP)
                Local cy% = gridY + row * (BR_CARD_H + BR_CARD_GAP)
                If Browser::cardVisible(self, cy, sh) = True
                    Browser::drawCardChrome(self, "zone", Handle(Ar), cx, cy, mx, my, clicked, count)
                    Browser::drawZoneCardBody(self, Ar, cx, cy)
                EndIf
                count = count + 1
                col = col + 1
                If col >= cols Then col = 0 : row = row + 1
            EndIf
        Next
        Return count
    End Method


    Method drawFactionGrid%(sw%, sh%, mx%, my%, clicked%, gridX%, gridY%, cols%)
        Local col% = 0
        Local row% = 0
        Local count% = 0
        Local i% = 0
        For i = 0 To 99
            If FactionNames$(i) <> ""
                If Browser::matchesFilter(self, FactionNames$(i)) = True
                    Local cx% = gridX + col * (BR_CARD_W + BR_CARD_GAP)
                    Local cy% = gridY + row * (BR_CARD_H + BR_CARD_GAP)
                    If Browser::cardVisible(self, cy, sh) = True
                        Browser::drawCardChrome(self, "faction", i, cx, cy, mx, my, clicked, count)
                        Browser::drawFactionCardBody(self, i, cx, cy)
                    EndIf
                    count = count + 1
                    col = col + 1
                    If col >= cols Then col = 0 : row = row + 1
                EndIf
            EndIf
        Next
        Return count
    End Method


    Method drawAnimSetGrid%(sw%, sh%, mx%, my%, clicked%, gridX%, gridY%, cols%)
        Local col% = 0
        Local row% = 0
        Local count% = 0
        For As.AnimSet = Each AnimSet
            If Browser::matchesFilter(self, As\Name$) = True
                Local cx% = gridX + col * (BR_CARD_W + BR_CARD_GAP)
                Local cy% = gridY + row * (BR_CARD_H + BR_CARD_GAP)
                If Browser::cardVisible(self, cy, sh) = True
                    Browser::drawCardChrome(self, "animset", As\ID, cx, cy, mx, my, clicked, count)
                    Browser::drawAnimSetCardBody(self, As, cx, cy)
                EndIf
                count = count + 1
                col = col + 1
                If col >= cols Then col = 0 : row = row + 1
            EndIf
        Next
        Return count
    End Method


    // -------------------------------------------------------------------------
    // drawToolsGrid -- the Tools tab. Each card is a standalone-editor
    // launcher, not a focusable entity. Click ExecFiles the .exe via
    // Tools_Launch instead of dispatching Threads::focus.
    //
    // Tools own their own paint (no drawCardChrome) because we want the
    // body layout to be different -- larger description, "Launch >>"
    // hint -- and because there's no kind/refID to feed the standard
    // chrome's hit-test path.
    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // drawSettingsCard -- the Settings tab body. Renders a single "Project
    // Settings" card that, when clicked, focuses the singleton settings
    // composer view via Threads::focus("settings", 0). Returns 1 (the
    // card count) so the empty-state copy doesn't fire.
    // -------------------------------------------------------------------------
    Method drawSettingsCard%(sw%, sh%, mx%, my%, clicked%, gridX%, gridY%)
        Local cx% = gridX
        Local cy% = gridY
        Local cw% = BR_CARD_W * 2 + BR_CARD_GAP   ; wider since it's a single card
        Local ch% = BR_CARD_H
        Local hovered% = (mx >= cx And mx < cx + cw And my >= cy And my < cy + ch)

        LoomShadowCard(cx, cy, cw, ch)
        If hovered = True
            LoomFill(cx, cy, cw, ch, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
        Else
            LoomFill(cx, cy, cw, ch, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
        EndIf
        LoomBorder(cx, cy, cw, ch, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomFill(cx, cy, cw, 3, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        LoomTheme_UseDisplay()
        LoomText(cx + 16, cy + 16, "PROJECT SETTINGS", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomTheme_UseBody()
        LoomText(cx + 16, cy + 44, "Game name | server port | currency tiers | runtime options", LOOM_STONE_200_R, LOOM_STONE_200_G, LOOM_STONE_200_B)
        LoomText(cx + 16, cy + ch - 24, "Click to open >>", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        If hovered = True And clicked = True
            Threads::focus(self\threads, "settings", 0)
            WriteLog(LoomLog, "Browser: opened Settings singleton")
        EndIf

        Return 1
    End Method


    // -------------------------------------------------------------------------
    // drawStatsCard -- Stats tab body. Singleton "Project Stats" card
    // that focuses the read-only dashboard composer view on click.
    // Mirrors drawSettingsCard's shape.
    // -------------------------------------------------------------------------
    Method drawStatsCard%(sw%, sh%, mx%, my%, clicked%, gridX%, gridY%)
        Local cx% = gridX
        Local cy% = gridY
        Local cw% = BR_CARD_W * 2 + BR_CARD_GAP
        Local ch% = BR_CARD_H
        Local hovered% = (mx >= cx And mx < cx + cw And my >= cy And my < cy + ch)

        LoomShadowCard(cx, cy, cw, ch)
        If hovered = True
            LoomFill(cx, cy, cw, ch, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
        Else
            LoomFill(cx, cy, cw, ch, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
        EndIf
        LoomBorder(cx, cy, cw, ch, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomFill(cx, cy, cw, 3, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        LoomTheme_UseDisplay()
        LoomText(cx + 16, cy + 16, "PROJECT STATS", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomTheme_UseBody()
        LoomText(cx + 16, cy + 44, "Entity counts | top zones | asset catalog sizes | issue breakdown", LOOM_STONE_200_R, LOOM_STONE_200_G, LOOM_STONE_200_B)
        LoomText(cx + 16, cy + ch - 24, "Click to open >>", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        If hovered = True And clicked = True
            Threads::focus(self\threads, "stats", 0)
            WriteLog(LoomLog, "Browser: opened Stats singleton")
        EndIf
        Return 1
    End Method


    Method drawToolsGrid%(sw%, sh%, mx%, my%, clicked%, gridX%, gridY%, cols%)
        Local col% = 0
        Local row% = 0
        Local count% = 0
        For t.ToolDef = Each ToolDef
            // Filter applies to the tool name -- you can type "terrain" to
            // narrow the grid to a single card, same as on entity tabs.
            If Browser::matchesFilter(self, t\Name$) = True
                Local cx% = gridX + col * (BR_CARD_W + BR_CARD_GAP)
                Local cy% = gridY + row * (BR_CARD_H + BR_CARD_GAP)
                If Browser::cardVisible(self, cy, sh) = True
                    Browser::drawToolCard(self, t, cx, cy, mx, my, clicked, count)
                EndIf
                count = count + 1
                col = col + 1
                If col >= cols Then col = 0 : row = row + 1
            EndIf
        Next
        Return count
    End Method


    // -------------------------------------------------------------------------
    // drawToolCard -- one tool launcher card. Shape mirrors the entity-card
    // chrome (so the Tools tab visually belongs in the same grid) but
    // dispatches Tools_Launch on click instead of Threads::focus.
    //
    // Missing-binary state: when Tools_Launch returns False, the card
    // shouldn't visually flicker since the click already happened. The
    // log line gives the diagnostic; future iterations may overlay a
    // toast or grey out the card when FileType(exe) <> 1.
    // -------------------------------------------------------------------------
    Method drawToolCard(t.ToolDef, x%, y%, mx%, my%, clicked%, cardIdx%)
        Local hovered% = (mx >= x And mx < x + BR_CARD_W And my >= y And my < y + BR_CARD_H)
        Local selected% = (cardIdx = self\selectedIndex)
        Local missing% = (FileType(t\ExePath) <> 1)

        // Drop shadow for visual lift; matches entity-card chrome.
        LoomShadowCard(x, y, BR_CARD_W, BR_CARD_H)

        // Background -- dimmed when the .exe is missing
        If missing = True
            LoomFill(x, y, BR_CARD_W, BR_CARD_H, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
        Else
            LoomFill(x, y, BR_CARD_W, BR_CARD_H, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
        EndIf

        // Border -- hover > keyboard selection > base. Missing-binary cards
        // get a danger-red base border so they read as broken.
        If hovered = True
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
            LoomBorder(x + 1, y + 1, BR_CARD_W - 2, BR_CARD_H - 2, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        Else If selected = True
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            LoomBorder(x + 1, y + 1, BR_CARD_W - 2, BR_CARD_H - 2, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        Else If missing = True
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
        Else
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        EndIf

        // Top brass accent
        LoomHRule(x + 12, y + 8, BR_CARD_W - 24, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        // Body: name + description + launch hint (or missing-binary note)
        LoomText(x + 12, y + 18, t\Name$, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        LoomText(x + 12, y + 44, t\Description$, LOOM_STONE_200_R, LOOM_STONE_200_G, LOOM_STONE_200_B)
        If missing = True
            LoomText(x + 12, y + 72, "binary not built", LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
        Else
            LoomText(x + BR_CARD_W - 70, y + 72, "Launch >>", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        EndIf

        // Click + missing-binary skip: log the failure inside Tools_Launch
        // so the user sees something useful in Loom Log.txt.
        If hovered And clicked
            Tools_Launch(t)
            self\cardClickLatch = True
        EndIf

        // Keyboard Enter on the selected tool card -- same dispatch.
        If selected = True And self\pendingEnter = True
            Tools_Launch(t)
            self\cardClickLatch = True
            self\pendingEnter = False
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // drawScriptsGrid -- one card per .rsl file in the project's Scripts
    // dir (populated at boot by Scripts_Init). Hover/click focuses the
    // composer to a script preview + reverse-ref view. Filter applies
    // against the script basename.
    // -------------------------------------------------------------------------
    Method drawScriptsGrid%(sw%, sh%, mx%, my%, clicked%, gridX%, gridY%, cols%)
        Local col% = 0
        Local row% = 0
        Local count% = 0
        For sf.ScriptFile = Each ScriptFile
            If Browser::matchesFilter(self, sf\Name$) = True
                Local cx% = gridX + col * (BR_CARD_W + BR_CARD_GAP)
                Local cy% = gridY + row * (BR_CARD_H + BR_CARD_GAP)
                If Browser::cardVisible(self, cy, sh) = True
                    Browser::drawScriptCard(self, sf, cx, cy, mx, my, clicked, count)
                EndIf
                count = count + 1
                col = col + 1
                If col >= cols Then col = 0 : row = row + 1
            EndIf
        Next
        Return count
    End Method


    // -------------------------------------------------------------------------
    // drawScriptCard -- one .rsl file card. Shape mirrors the entity-card
    // chrome (shadow + brass border + top accent rule + body). Click
    // focuses the composer to the script preview via Threads::focus.
    // -------------------------------------------------------------------------
    Method drawScriptCard(sf.ScriptFile, x%, y%, mx%, my%, clicked%, cardIdx%)
        Local hovered% = (mx >= x And mx < x + BR_CARD_W And my >= y And my < y + BR_CARD_H)
        Local selected% = (cardIdx = self\selectedIndex)

        LoomShadowCard(x, y, BR_CARD_W, BR_CARD_H)
        LoomFill(x, y, BR_CARD_W, BR_CARD_H, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)

        If hovered = True
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
            LoomBorder(x + 1, y + 1, BR_CARD_W - 2, BR_CARD_H - 2, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        Else If selected = True
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            LoomBorder(x + 1, y + 1, BR_CARD_W - 2, BR_CARD_H - 2, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        Else
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        EndIf

        // Top brass accent rule
        LoomHRule(x + 12, y + 8, BR_CARD_W - 24, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        // Body: name (truncated) + size + line count + filetype hint
        Local nm$ = sf\Name$
        If Len(nm) > 22 Then nm = Left$(nm, 21) + "~"
        LoomText(x + 12, y + 18, nm, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        LoomText(x + 12, y + 44, Scripts_FormatSize(sf\SizeBytes) + " | " + Str(sf\LineCount) + " lines", LOOM_STONE_200_R, LOOM_STONE_200_G, LOOM_STONE_200_B)
        LoomText(x + BR_CARD_W - 50, y + 72, ".rsl", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        If hovered And clicked
            Threads::focus(self\threads, "script", sf\Index)
            self\cardClickLatch = True
        EndIf

        If selected = True And self\pendingEnter = True
            Threads::focus(self\threads, "script", sf\Index)
            self\cardClickLatch = True
            self\pendingEnter = False
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // drawTexturesGrid -- one card per defined texture in the project.
    // Each card carries a 64x64 thumbnail via the existing ImageCache
    // (Loom_DrawThumbnailLarge), basename truncated to fit, and the
    // engine-side ID for context.
    // -------------------------------------------------------------------------
    Method drawTexturesGrid%(sw%, sh%, mx%, my%, clicked%, gridX%, gridY%, cols%)
        Local col% = 0
        Local row% = 0
        Local count% = 0
        For te.TextureEntry = Each TextureEntry
            If Browser::matchesFilter(self, te\Filename$) = True
                Local cx% = gridX + col * (BR_CARD_W + BR_CARD_GAP)
                Local cy% = gridY + row * (BR_CARD_H + BR_CARD_GAP)
                If Browser::cardVisible(self, cy, sh) = True
                    Browser::drawTextureCard(self, te, cx, cy, mx, my, clicked, count)
                EndIf
                count = count + 1
                col = col + 1
                If col >= cols Then col = 0 : row = row + 1
            EndIf
        Next
        Return count
    End Method


    // -------------------------------------------------------------------------
    // drawTextureCard -- card chrome + 64x64 thumbnail + filename +
    // ID label. Same hover/select pattern as script + entity cards;
    // click focuses the composer to the texture preview.
    // -------------------------------------------------------------------------
    Method drawTextureCard(te.TextureEntry, x%, y%, mx%, my%, clicked%, cardIdx%)
        Local hovered% = (mx >= x And mx < x + BR_CARD_W And my >= y And my < y + BR_CARD_H)
        Local selected% = (cardIdx = self\selectedIndex)

        LoomShadowCard(x, y, BR_CARD_W, BR_CARD_H)
        LoomFill(x, y, BR_CARD_W, BR_CARD_H, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)

        If hovered = True
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
            LoomBorder(x + 1, y + 1, BR_CARD_W - 2, BR_CARD_H - 2, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        Else If selected = True
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            LoomBorder(x + 1, y + 1, BR_CARD_W - 2, BR_CARD_H - 2, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        Else
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        EndIf

        LoomHRule(x + 12, y + 8, BR_CARD_W - 24, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        // Thumbnail anchored left under the accent rule. Item textures
        // and other texture IDs share the ID space so Loom_DrawThumbnailLarge
        // can render either source via the cached scaled image pool.
        Loom_DrawThumbnailLarge(te\ID, x + 12, y + 18)

        // Filename + ID column on the right of the thumbnail
        Local labelX% = x + 12 + 64 + 8
        Local nm$ = te\Filename$
        If Len(nm) > 14 Then nm = Left$(nm, 13) + "~"
        LoomText(labelX, y + 18, nm, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        LoomText(labelX, y + 36, "id " + Str(te\ID), LOOM_STONE_200_R, LOOM_STONE_200_G, LOOM_STONE_200_B)
        If te\Flags <> 0
            LoomText(labelX, y + 54, "flags " + Str(te\Flags), LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
        EndIf

        If hovered And clicked
            Threads::focus(self\threads, "texture", te\Index)
            self\cardClickLatch = True
        EndIf
        If selected = True And self\pendingEnter = True
            Threads::focus(self\threads, "texture", te\Index)
            self\cardClickLatch = True
            self\pendingEnter = False
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // drawMeshesGrid -- one card per defined mesh in the project.
    // Cards are text-only (no inline 3D preview -- the composer's
    // existing Loom_DrawMeshPreview widget handles full orbit/zoom
    // once the mesh is focused; per-card 3D preview is too expensive).
    // -------------------------------------------------------------------------
    Method drawMeshesGrid%(sw%, sh%, mx%, my%, clicked%, gridX%, gridY%, cols%)
        Local col% = 0
        Local row% = 0
        Local count% = 0
        For me.MeshEntry = Each MeshEntry
            If Browser::matchesFilter(self, me\Filename$) = True
                Local cx% = gridX + col * (BR_CARD_W + BR_CARD_GAP)
                Local cy% = gridY + row * (BR_CARD_H + BR_CARD_GAP)
                If Browser::cardVisible(self, cy, sh) = True
                    Browser::drawMeshCard(self, me, cx, cy, mx, my, clicked, count)
                EndIf
                count = count + 1
                col = col + 1
                If col >= cols Then col = 0 : row = row + 1
            EndIf
        Next
        Return count
    End Method


    // -------------------------------------------------------------------------
    // drawMeshCard -- card chrome + filename + ID + anim badge.
    // -------------------------------------------------------------------------
    Method drawMeshCard(me.MeshEntry, x%, y%, mx%, my%, clicked%, cardIdx%)
        Local hovered% = (mx >= x And mx < x + BR_CARD_W And my >= y And my < y + BR_CARD_H)
        Local selected% = (cardIdx = self\selectedIndex)

        LoomShadowCard(x, y, BR_CARD_W, BR_CARD_H)
        LoomFill(x, y, BR_CARD_W, BR_CARD_H, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)

        If hovered = True
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
            LoomBorder(x + 1, y + 1, BR_CARD_W - 2, BR_CARD_H - 2, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        Else If selected = True
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            LoomBorder(x + 1, y + 1, BR_CARD_W - 2, BR_CARD_H - 2, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        Else
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        EndIf

        LoomHRule(x + 12, y + 8, BR_CARD_W - 24, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        Local nm$ = me\Filename$
        If Len(nm) > 22 Then nm = Left$(nm, 21) + "~"
        LoomText(x + 12, y + 18, nm, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        LoomText(x + 12, y + 44, "id " + Str(me\ID), LOOM_STONE_200_R, LOOM_STONE_200_G, LOOM_STONE_200_B)
        // Anim badge -- only shown for animated meshes (most actor
        // models). Static meshes (props, scenery) skip it.
        If me\IsAnim <> 0
            LoomText(x + BR_CARD_W - 50, y + 72, "anim", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        EndIf

        If hovered And clicked
            Threads::focus(self\threads, "mesh", me\Index)
            self\cardClickLatch = True
        EndIf
        If selected = True And self\pendingEnter = True
            Threads::focus(self\threads, "mesh", me\Index)
            self\cardClickLatch = True
            self\pendingEnter = False
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // drawSoundsGrid -- one card per defined sound in the project.
    // Cards are text-only; the per-sound Play button lives in the
    // composer view where the user is committed to that sound's context.
    // -------------------------------------------------------------------------
    Method drawSoundsGrid%(sw%, sh%, mx%, my%, clicked%, gridX%, gridY%, cols%)
        Local col% = 0
        Local row% = 0
        Local count% = 0
        For se.SoundEntry = Each SoundEntry
            If Browser::matchesFilter(self, se\Filename$) = True
                Local cx% = gridX + col * (BR_CARD_W + BR_CARD_GAP)
                Local cy% = gridY + row * (BR_CARD_H + BR_CARD_GAP)
                If Browser::cardVisible(self, cy, sh) = True
                    Browser::drawSoundCard(self, se, cx, cy, mx, my, clicked, count)
                EndIf
                count = count + 1
                col = col + 1
                If col >= cols Then col = 0 : row = row + 1
            EndIf
        Next
        Return count
    End Method


    // -------------------------------------------------------------------------
    // drawSoundCard -- card chrome + filename + ID + 3D badge.
    // -------------------------------------------------------------------------
    Method drawSoundCard(se.SoundEntry, x%, y%, mx%, my%, clicked%, cardIdx%)
        Local hovered% = (mx >= x And mx < x + BR_CARD_W And my >= y And my < y + BR_CARD_H)
        Local selected% = (cardIdx = self\selectedIndex)

        LoomShadowCard(x, y, BR_CARD_W, BR_CARD_H)
        LoomFill(x, y, BR_CARD_W, BR_CARD_H, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)

        If hovered = True
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
            LoomBorder(x + 1, y + 1, BR_CARD_W - 2, BR_CARD_H - 2, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        Else If selected = True
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            LoomBorder(x + 1, y + 1, BR_CARD_W - 2, BR_CARD_H - 2, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        Else
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        EndIf

        LoomHRule(x + 12, y + 8, BR_CARD_W - 24, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        Local nm$ = se\Filename$
        If Len(nm) > 22 Then nm = Left$(nm, 21) + "~"
        LoomText(x + 12, y + 18, nm, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        LoomText(x + 12, y + 44, "id " + Str(se\ID), LOOM_STONE_200_R, LOOM_STONE_200_G, LOOM_STONE_200_B)
        If se\Is3D <> 0
            LoomText(x + BR_CARD_W - 40, y + 72, "3D", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        EndIf

        If hovered And clicked
            Threads::focus(self\threads, "sound", se\Index)
            self\cardClickLatch = True
        EndIf
        If selected = True And self\pendingEnter = True
            Threads::focus(self\threads, "sound", se\Index)
            self\cardClickLatch = True
            self\pendingEnter = False
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // drawMusicGrid / drawMusicCard -- final media catalog tab. Cards
    // are text-only with the filename + ID; composer view holds the
    // play button.
    // -------------------------------------------------------------------------
    Method drawMusicGrid%(sw%, sh%, mx%, my%, clicked%, gridX%, gridY%, cols%)
        Local col% = 0
        Local row% = 0
        Local count% = 0
        For mu.MusicEntry = Each MusicEntry
            If Browser::matchesFilter(self, mu\Filename$) = True
                Local cx% = gridX + col * (BR_CARD_W + BR_CARD_GAP)
                Local cy% = gridY + row * (BR_CARD_H + BR_CARD_GAP)
                If Browser::cardVisible(self, cy, sh) = True
                    Browser::drawMusicCard(self, mu, cx, cy, mx, my, clicked, count)
                EndIf
                count = count + 1
                col = col + 1
                If col >= cols Then col = 0 : row = row + 1
            EndIf
        Next
        Return count
    End Method


    Method drawMusicCard(mu.MusicEntry, x%, y%, mx%, my%, clicked%, cardIdx%)
        Local hovered% = (mx >= x And mx < x + BR_CARD_W And my >= y And my < y + BR_CARD_H)
        Local selected% = (cardIdx = self\selectedIndex)

        LoomShadowCard(x, y, BR_CARD_W, BR_CARD_H)
        LoomFill(x, y, BR_CARD_W, BR_CARD_H, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)

        If hovered = True
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
            LoomBorder(x + 1, y + 1, BR_CARD_W - 2, BR_CARD_H - 2, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        Else If selected = True
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            LoomBorder(x + 1, y + 1, BR_CARD_W - 2, BR_CARD_H - 2, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        Else
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        EndIf

        LoomHRule(x + 12, y + 8, BR_CARD_W - 24, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        Local nm$ = mu\Filename$
        If Len(nm) > 22 Then nm = Left$(nm, 21) + "~"
        LoomText(x + 12, y + 18, nm, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        LoomText(x + 12, y + 44, "id " + Str(mu\ID), LOOM_STONE_200_R, LOOM_STONE_200_G, LOOM_STONE_200_B)
        LoomText(x + BR_CARD_W - 56, y + 72, "music", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        If hovered And clicked
            Threads::focus(self\threads, "music", mu\Index)
            self\cardClickLatch = True
        EndIf
        If selected = True And self\pendingEnter = True
            Threads::focus(self\threads, "music", mu\Index)
            self\cardClickLatch = True
            self\pendingEnter = False
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // drawCardChrome -- shared card background + hover/keyboard-selected
    // border + brass accent + inline hit-test. Sets self\cardClickLatch
    // and calls Threads::focus on click (side effects; no return value so
    // per-kind grid methods don't need to propagate booleans through
    // nested scopes).
    //
    // cardIdx is the 0-based index of this card within the visible (post-
    // filter) iteration. When it equals selectedIndex, the card is the
    // keyboard cursor and paints with an extra brass selection ring; if
    // pendingEnter is True at that same index, we dispatch focus and
    // clear the flag.
    // -------------------------------------------------------------------------
    Method drawCardChrome(kind$, refID%, x%, y%, mx%, my%, clicked%, cardIdx%)
        Local hovered% = (mx >= x And mx < x + BR_CARD_W And my >= y And my < y + BR_CARD_H)
        Local selected% = (cardIdx = self\selectedIndex)
        Local inSelectionSet% = Browser::isSelected(self, kind, refID)

        // Drop shadow lifts each card off the body gradient so cards
        // read as physical tiles rather than printed-on labels.
        LoomShadowCard(x, y, BR_CARD_W, BR_CARD_H)

        // Card chrome varies by mode: tool=flat, balanced=subtle gradient,
        // in-world=dramatic gradient with darker bottom. Selection set
        // members get a brass-tinted variant regardless of mode.
        If inSelectionSet = True
            If Loom_ChromeIsTool() = True
                LoomFill(x, y, BR_CARD_W, BR_CARD_H, LOOM_BRASS_800_R, LOOM_BRASS_800_G, LOOM_BRASS_800_B)
            Else
                LoomGradientV(x, y, BR_CARD_W, BR_CARD_H, LOOM_BRASS_800_R, LOOM_BRASS_800_G, LOOM_BRASS_800_B, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
            EndIf
        Else If Loom_ChromeIsTool() = True
            LoomFill(x, y, BR_CARD_W, BR_CARD_H, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
        Else If Loom_ChromeIsInWorld() = True
            LoomGradientV(x, y, BR_CARD_W, BR_CARD_H, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B)
        Else
            LoomGradientV(x, y, BR_CARD_W, BR_CARD_H, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B)
        EndIf

        // Border priority: hover (arcane) > in-selection (warning) >
        // keyboard cursor (brass solid double) > base (brass-700).
        If hovered = True
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
            LoomBorder(x + 1, y + 1, BR_CARD_W - 2, BR_CARD_H - 2, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        Else If inSelectionSet = True
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B)
            LoomBorder(x + 1, y + 1, BR_CARD_W - 2, BR_CARD_H - 2, LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B)
        Else If selected = True
            // Keyboard cursor -- brass double ring so it reads as
            // distinct from arcane hover.
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            LoomBorder(x + 1, y + 1, BR_CARD_W - 2, BR_CARD_H - 2, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        Else
            LoomBorder(x, y, BR_CARD_W, BR_CARD_H, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        EndIf

        // Top brass accent -- a thicker brass band so the cards read as
        // ornament-trimmed rather than thinly-outlined.
        LoomHRule(x + 12, y + 6, BR_CARD_W - 24, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        LoomHRule(x + 12, y + 7, BR_CARD_W - 24, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomHRule(x + 12, y + 8, BR_CARD_W - 24, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)

        // Click handling -- Shift+Click adds/removes from selection set;
        // plain click clears selection + focuses (single-edit flow).
        If hovered And clicked
            Local shiftDown% = (KeyDown(42) Or KeyDown(54))
            If shiftDown = True
                Browser::toggleInSelection(self, kind, refID)
                self\cardClickLatch = True
            Else
                // Plain click -- clear any pending selection so the
                // user gets the simple "focus this" semantics.
                If self\selectionCount > 0 Then Browser::clearSelection(self)
                Threads::focus(self\threads, kind, refID)
                self\cardClickLatch = True
                WriteLog(LoomLog, "Browser: focused " + kind + "#" + Str(refID))
            EndIf
        EndIf

        // Keyboard Enter -- only the selected card consumes it. Treated
        // as a plain focus (no shift-modifier semantics for Enter).
        If selected = True And self\pendingEnter = True
            If self\selectionCount > 0 Then Browser::clearSelection(self)
            Threads::focus(self\threads, kind, refID)
            self\cardClickLatch = True
            self\pendingEnter = False
            WriteLog(LoomLog, "Browser: focused (Enter) " + kind + "#" + Str(refID))
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // Per-kind card body content
    // -------------------------------------------------------------------------

    Method drawActorCardBody(Ac.Actor, x%, y%)
        LoomText(x + 12, y + 18, Ac\Race$ + " [" + Ac\Class$ + "]", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        // Top-right kind/status badge -- prioritized: PLAYABLE > RIDEABLE > NPC
        If Ac\Playable = True
            Browser::drawBadge(self, x + BR_CARD_W - 12, y + 18, "PLAYABLE", LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        Else If Ac\Rideable = True
            Browser::drawBadge(self, x + BR_CARD_W - 12, y + 18, "RIDEABLE", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        Else
            Browser::drawBadge(self, x + BR_CARD_W - 12, y + 18, "NPC", LOOM_STONE_500_R, LOOM_STONE_500_G, LOOM_STONE_500_B)
        EndIf

        Local facName$ = FactionNames$(Ac\DefaultFaction)
        If facName = "" Then facName = "(no faction)"
        LoomText(x + 12, y + 44, "Faction", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomText(x + 12, y + 60, facName, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        LoomText(x + 180, y + 44, "XP mult", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomText(x + 180, y + 60, Str(Ac\XPMultiplier), LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
    End Method


    Method drawItemCardBody(It.Item, x%, y%)
        LoomText(x + 12, y + 18, It\Name$, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        Local typeLabel$ = Browser::itemTypeLabel(self, It\ItemType)
        // Item-type badge -- color per type so a glance distinguishes
        // weapons from armour from potions.
        Browser::drawBadge(self, x + BR_CARD_W - 12, y + 18, Upper$(typeLabel), Browser::itemTypeBadgeR(self, It\ItemType), Browser::itemTypeBadgeG(self, It\ItemType), Browser::itemTypeBadgeB(self, It\ItemType))

        LoomText(x + 12, y + 44, "Type", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomText(x + 12, y + 60, typeLabel, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        LoomText(x + 180, y + 44, "Value", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomText(x + 180, y + 60, Str(It\Value), LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        // Bottom-right thumbnail -- 32x32 preview of the item's icon
        // texture. Lazy-loaded via the same ImageCache module that
        // serves the composer thumbnail. Missing/invalid IDs paint
        // the cache's "?" placeholder so the layout stays stable.
        Loom_DrawThumbnailSmall(It\ThumbnailTexID, x + BR_CARD_W - 44, y + BR_CARD_H - 44)
    End Method


    Method drawSpellCardBody(Sp.Spell, x%, y%)
        LoomText(x + 12, y + 18, Sp\Name$, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        // Recharge badge -- color cue by speed (fast = arcane / slow = warning)
        Local rechargeSec% = Sp\RechargeTime / 1000
        Local rechargeBadge$ = Str(rechargeSec) + "S"
        If rechargeSec <= 2
            Browser::drawBadge(self, x + BR_CARD_W - 12, y + 18, rechargeBadge, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        Else If rechargeSec <= 10
            Browser::drawBadge(self, x + BR_CARD_W - 12, y + 18, rechargeBadge, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        Else
            Browser::drawBadge(self, x + BR_CARD_W - 12, y + 18, rechargeBadge, LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B)
        EndIf

        LoomText(x + 12, y + 44, "Recharge", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomText(x + 12, y + 60, Str(rechargeSec) + " s", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        If Sp\Script$ <> ""
            LoomText(x + 180, y + 44, "Script", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            LoomText(x + 180, y + 60, Sp\Script$, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        EndIf

        // Bottom-right thumbnail -- 32x32 preview of the spell icon
        Loom_DrawThumbnailSmall(Sp\ThumbnailTexID, x + BR_CARD_W - 44, y + BR_CARD_H - 44)
    End Method


    Method drawZoneCardBody(Ar.Area, x%, y%)
        LoomText(x + 12, y + 18, Ar\Name$, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        // Top-right badge -- PVP > OUTDOOR > INDOOR (PVP is the most
        // load-bearing zone-level flag for design decisions)
        If Ar\PvP = True
            Browser::drawBadge(self, x + BR_CARD_W - 12, y + 18, "PVP", LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
        Else If Ar\Outdoors = True
            Browser::drawBadge(self, x + BR_CARD_W - 12, y + 18, "OUTDOOR", LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        Else
            Browser::drawBadge(self, x + BR_CARD_W - 12, y + 18, "INDOOR", LOOM_STONE_500_R, LOOM_STONE_500_G, LOOM_STONE_500_B)
        EndIf

        Local portals% = 0
        Local spawns% = 0
        Local triggers% = 0
        Local i% = 0
        For i = 0 To 99
            If Ar\PortalName$[i] <> "" Then portals = portals + 1
        Next
        For i = 0 To 999
            If Ar\SpawnActor[i] > 0 Then spawns = spawns + 1
        Next
        For i = 0 To 149
            If Ar\TriggerScript$[i] <> "" Then triggers = triggers + 1
        Next

        LoomText(x + 12,  y + 44, "Portals",  LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomText(x + 12,  y + 60, Str(portals), LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        LoomText(x + 110, y + 44, "Spawns",   LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomText(x + 110, y + 60, Str(spawns), LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        LoomText(x + 208, y + 44, "Triggers", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomText(x + 208, y + 60, Str(triggers), LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
    End Method


    Method drawFactionCardBody(idx%, x%, y%)
        LoomText(x + 12, y + 18, FactionNames$(idx), LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        // Member count: walk actors with DefaultFaction == idx
        Local members% = 0
        For Ac.Actor = Each Actor
            If Ac\DefaultFaction = idx Then members = members + 1
        Next

        // Top-right badge -- member count as a chip with brass color so
        // the user can scan the faction grid for "the ones with people"
        // vs "the ones nobody belongs to"
        Browser::drawBadge(self, x + BR_CARD_W - 12, y + 18, Str(members) + " MEMBER", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        LoomText(x + 12, y + 44, "Members", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomText(x + 12, y + 60, Str(members), LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
    End Method


    Method drawAnimSetCardBody(As.AnimSet, x%, y%)
        LoomText(x + 12, y + 18, As\Name$, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        Local clips% = 0
        Local i% = 0
        For i = 0 To 149
            If As\AnimName$[i] <> "" Then clips = clips + 1
        Next

        // Count actors using this anim set (M or F)
        Local users% = 0
        For Ac.Actor = Each Actor
            If Ac\MAnimationSet = As\ID Or Ac\FAnimationSet = As\ID Then users = users + 1
        Next

        // Top-right badge -- ORPHAN if no actors use it (cleanup candidate);
        // user-count brass chip otherwise.
        If users = 0
            Browser::drawBadge(self, x + BR_CARD_W - 12, y + 18, "ORPHAN", LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
        Else
            Browser::drawBadge(self, x + BR_CARD_W - 12, y + 18, Str(users) + " IN USE", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        EndIf

        LoomText(x + 12,  y + 44, "Clips", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomText(x + 12,  y + 60, Str(clips), LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        LoomText(x + 110, y + 44, "Used by",  LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomText(x + 110, y + 60, Str(users), LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
    End Method


    // -------------------------------------------------------------------------
    // drawBadge -- right-anchored pill of `label` painted with `fillR/G/B`
    // background; parchment text inside. (rightX, y) is the top-right
    // corner of the pill so callers pass `x + BR_CARD_W - 12` and the
    // badge sizes itself based on label width.
    //
    // 16px tall, ~6px text padding each side, parchment border.
    // -------------------------------------------------------------------------
    Method drawBadge(rightX%, y%, label$, fillR%, fillG%, fillB%)
        Local bw% = StringWidth(label) + 12
        Local bh% = 16
        Local bx% = rightX - bw
        LoomFill(bx, y, bw, bh, fillR, fillG, fillB)
        LoomBorder(bx, y, bw, bh, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        LoomText(bx + 6, y + 1, label, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
    End Method


    // -------------------------------------------------------------------------
    // itemTypeBadgeR/G/B -- color cue per Item.ItemType. Channel split
    // (not a packed return) to dodge the Strict-mode reassign-Local-
    // from-nested-If trap. Same shape as Toasts::kindR/G/B,
    // ExitPrompt::actionR/G/B.
    // -------------------------------------------------------------------------
    Method itemTypeBadgeR%(t%)
        If t = 1 Then Return LOOM_DANGER_R    ; Weapon -- aggressive red
        If t = 2 Then Return LOOM_ARCANE_500_R ; Armour -- defensive blue
        If t = 3 Then Return LOOM_BRASS_500_R  ; Ring -- ornament brass
        If t = 4 Then Return LOOM_SUCCESS_R    ; Potion -- alchemy green
        If t = 5 Then Return LOOM_WARNING_R    ; Food -- warm orange
        Return LOOM_STONE_500_R                ; Other / Image / misc
    End Method

    Method itemTypeBadgeG%(t%)
        If t = 1 Then Return LOOM_DANGER_G
        If t = 2 Then Return LOOM_ARCANE_500_G
        If t = 3 Then Return LOOM_BRASS_500_G
        If t = 4 Then Return LOOM_SUCCESS_G
        If t = 5 Then Return LOOM_WARNING_G
        Return LOOM_STONE_500_G
    End Method

    Method itemTypeBadgeB%(t%)
        If t = 1 Then Return LOOM_DANGER_B
        If t = 2 Then Return LOOM_ARCANE_500_B
        If t = 3 Then Return LOOM_BRASS_500_B
        If t = 4 Then Return LOOM_SUCCESS_B
        If t = 5 Then Return LOOM_WARNING_B
        Return LOOM_STONE_500_B
    End Method


    // -------------------------------------------------------------------------
    // Footer
    // -------------------------------------------------------------------------
    Method drawFooter(sw%, sh%)
        Local y% = sh - BR_BOT_RIBBON
        // Mirror of the brand strip's gradient direction so the chrome
        // bands at top + bottom read as a matched pair framing the body.
        LoomGradientV(0, y, sw, BR_BOT_RIBBON, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B)
        LoomHRule(0, y, sw, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)

        Local hint$
        If self\selectionCount > 0
            hint = Str(self\selectionCount) + " selected  |  shift+click to add/remove  |  Esc to clear selection"
            LoomText(20, y + 10, hint, LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B)
        Else
            hint = "click a card to focus  |  shift+click to bulk-select  |  F1 for shortcuts  |  Esc to exit"
            LoomText(20, y + 10, hint, LOOM_STONE_200_R, LOOM_STONE_200_G, LOOM_STONE_200_B)
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // itemTypeLabel -- human-friendly item type. rcce2 stores item types as
    // ints (defined in Inventories.bb constants).
    // -------------------------------------------------------------------------
    Method itemTypeLabel$(t%)
        If t = 0 Then Return "Other"
        If t = 1 Then Return "Weapon"
        If t = 2 Then Return "Armour"
        If t = 3 Then Return "Ring"
        If t = 4 Then Return "Potion"
        If t = 5 Then Return "Food"
        If t = 6 Then Return "Image"
        Return "Type " + Str(t)
    End Method
End Type
