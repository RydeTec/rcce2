Strict

// =============================================================================
// Loom/Composer.bb -- per-kind detail page for the focused entity
// =============================================================================
//
// When the Browser focuses an entity (or a thread chip jumps), the Composer
// paints that entity's properties on the right side of the screen. Each kind
// has its own field layout. Reference fields render as thread chips via the
// held Threads instance; clicking a chip jumps and pushes the current focus
// onto the back stack.
//
// Reads:
//   self\threads\focusKind / focusID  (the Threads instance held at construction)
//   the underlying data modules' globals (ActorList, ItemList, SpellsList,
//   Each Area, FactionNames$, Each AnimSet)
//
// Writes:
//   When edit mode is active for a field, mutates the underlying type instance
//   on commit (Enter or Save button). Sets the corresponding per-tab *Saved
//   global to False so GUE sees Loom's edits as dirty too. Save dispatch
//   (Composer::commitSaveForKind) calls GUE's existing Save* serializers
//   (SaveSpells, SaveItems, ...) so the on-disk format stays canonical.
//
// Architecture: Type with Methods, called as `Composer::method(self, args)`.


Const CMP_W              = 380
// Max thread-web nodes rendered radially around the focused entity. Extra
// references past this are summed into a "+N more" note (a faction with 200
// members can't render as 200 radial chips). 12 fits the left area legibly.
Const TW_MAX             = 12
Const CMP_COLLAPSED_W    = 28      ; width when self\collapsed = True
Const CMP_COLLAPSE_BTN_W = 18      ; chevron-button size (square)
Const CMP_COLLAPSE_BTN_H = 22
Const CMP_TOP            = LOOM_TOP_RIBBON_H + 56   // matches BR_TOP_RIBBON (84)
Const CMP_BOT_PAD        = 36     // matches BR_BOT_RIBBON
Const CMP_PAD            = 16
Const CMP_ROW_H          = 22
Const CMP_CHIP_H         = 26

// Save button (top-right of the composer title block).
Const CMP_SAVE_BTN_W  = 70
Const CMP_SAVE_BTN_H  = 22

// Delete button (top-right, immediately left of Save). Two-click arm/confirm
// pattern -- first click arms (button turns red, footer hint changes),
// second click within CMP_DELETE_ARM_MS commits the delete. Click anywhere
// else (or wait out the window) cancels.
Const CMP_DELETE_BTN_W  = 24
Const CMP_DELETE_BTN_H  = 22
Const CMP_DELETE_ARM_MS = 4000

// Discard button (top-right, immediately left of Delete). Reverts the
// current kind's in-memory state by re-running its loader against the
// on-disk file. Same shape as the Delete arm/confirm so accidents are
// avoided when there's unsaved work.
Const CMP_DISCARD_BTN_W  = 60
Const CMP_DISCARD_BTN_H  = 22

// Duplicate button (top-right cluster). Allocates a copy of the focused
// entity via EntityFactory_Duplicate. No arm/confirm -- duplicate is
// non-destructive (just creates more state); user can delete the
// duplicate if it was a mistake.
Const CMP_DUP_BTN_W  = 56
Const CMP_DUP_BTN_H  = 22

// Edit-buffer cursor blink rate (ms). MilliSecs() Mod CMP_CURSOR_PERIOD < half = visible.
Const CMP_CURSOR_PERIOD = 1000

// Scroll step (pixels per wheel tick). A standard wheel tick is one
// detent; 22px = 1 standard row, so scrolling jumps a row at a time.
Const CMP_SCROLL_STEP = 22

// Scrollbar indicator track width on the right edge of the panel.
Const CMP_SCROLLBAR_W = 4

; bulk field broadcast -- "Apply to all" button width inside the bulk-edit panel
Const CMP_BULK_APPLY_W  = 56
Const CMP_BULK_APPLY_H  = 22
Const CMP_BULK_INPUT_W  = 140


// -----------------------------------------------------------------------------
// BulkDeleteTarget -- per-iteration snapshot of (kind, refID) for the
// bulk-delete loop. Snapshotting decouples the delete iteration from
// concurrent mutation of the SelectedEntity pool (EntityFactory_Delete
// invokes Toast_Show, WorldCache_Invalidate, etc. which could conceivably
// touch other globals). Allocated + freed within commitBulkDelete.
// -----------------------------------------------------------------------------
Type BulkDeleteTarget
    Field Kind$
    Field RefID%
End Type


// =============================================================================
// Composer -- right-side property panel.
// =============================================================================
Type Composer
    Field threads.Threads      // shared focus state, set by caller

    // Per-frame chip-click latch -- the per-kind body renderers set this when
    // any thread chip consumed a click, and renderAndUpdate returns it so the
    // caller can react (e.g. log it, refresh another surface).
    Field chipHit%

    // Vertical scroll state for the body. Some kinds (Actor with all its
    // toggles + ints, Item with weapon+armour sections) render more rows
    // than fit in the panel height; without scroll the bottom fields
    // are silently clipped. MouseZ() drives scrollOffset; lastContentBottom
    // is the y where the previous frame's render ran out of rows, used
    // to clamp scrollOffset so the user can't scroll past the end.
    //
    // bodyTop / bodyBottom are set at the top of renderAndUpdate and
    // read by the row-painting helpers to skip rendering rows that
    // would leak outside the body area (no Blitz3D 2D clip-rect).
    Field scrollOffset%
    Field lastContentBottom%
    Field bodyTop%
    Field bodyBottom%
    // Last (kind, refID) the body was laid out for. When focus changes we
    // reset scrollOffset to 0 so a new entity always opens at the top --
    // otherwise the previous entity's scroll position "sticks" onto the new
    // one (and can sit past its shorter content). Tracks ALL focus changes,
    // not just edit-active ones (the edit-only reset missed plain navigation).
    Field scrollFocusKind$
    Field scrollFocusRefID%

    // Collapse state -- when True, the composer renders only as a thin
    // brass sliver on the right edge with a chevron to expand. Lets the
    // user see the full browser card grid while keeping focus state
    // active. Toggle via the chevron button at the top-left of the
    // composer panel.
    Field collapsed%

    // Browser reference -- set by Loom.bb via setBrowser. Composer reads
    // Browser::hasSelection / iterates Each SelectedEntity when rendering
    // the bulk-edit panel.
    Field browser.Browser

    // Bulk-delete arm timestamp. Same arm/confirm shape as single-entity
    // delete; clicking once arms (button turns red), clicking again
    // within CMP_DELETE_ARM_MS commits to delete every selected entity.
    Field bulkDeleteArmAt%

    // Bulk-field-broadcast state. Lets the user "Apply to all" a single
    // value across every selected entity of a homogeneous selection.
    // bulkEditField = "" means no bulk field edit in progress. The
    // (kind, fieldId) pair identifies which field of which kind is
    // being broadcast; we don't store the kind separately because the
    // panel is only shown when homogeneousSelectionKind returns nonempty.
    Field bulkEditField$
    Field bulkEditBuffer$

    // Per-zone-sub-entity Y anchors captured at render time so the
    // viewport's click-pick can scroll the composer to the right
    // section. Stored in unscrolled body coordinates (the y BEFORE
    // self\scrollOffset is subtracted) so scrollToZoneSubEntity can
    // just set scrollOffset = anchor - bodyTop. Refreshed every
    // frame the zone is being rendered; stale slots are harmless
    // because scrollToZoneSubEntity guards by source-data defined-ness.
    Field zoneAnchorPortal[99]
    Field zoneAnchorTrigger[149]
    Field zoneAnchorSpawn[999]

    // Edit-buffer state. editKind = "" means no edit in progress.
    // (kind, refID, fieldId) together identify which field of which entity
    // the user is currently typing into. editBuffer is the in-progress value
    // shown with a blinking cursor; on Enter it's written to the entity's
    // field via commitEdit.
    Field editKind$
    Field editRefID%
    Field editFieldId$
    Field editBuffer$
    Field editOldValue$    // snapshot at beginEdit, used by commitEdit
                           // to record the Timeline before/after pair

    // Delete-arm state. When the user clicks Delete, we record the kind/
    // refID and a timestamp. A second click within CMP_DELETE_ARM_MS on
    // the same (kind, refID) commits; clicking anywhere else cancels.
    Field deleteArmKind$
    Field deleteArmRefID%
    Field deleteArmAt%

    // Discard-arm state -- same shape as delete-arm. Only the kind matters
    // (discard reloads the whole kind from disk, not a single entity).
    Field discardArmKind$
    Field discardArmAt%

    // Tab-navigation per-frame field list. Each editable row helper
    // appends its (kind, refID, fieldId) here via recordField at paint
    // time. pumpKeyboard reads the list (from the PRIOR frame's paint)
    // to advance the active edit on Tab / Shift+Tab. 512 is enough for
    // even the densest entity composer (Actor with all 96 attribute
    // cells + appearance arrays comes in at ~250).
    Field rowFieldKinds$[512]
    Field rowFieldRefIDs%[512]
    Field rowFieldIds$[512]
    Field rowFieldValues$[512]   // cached storedValue for Tab-seed
    Field rowFieldCount%

    // Palette reference -- set by setPalette from Loom.bb at construction.
    // Used by chipRow to dispatch picker-mode opens on right-click of a
    // thread chip.
    Field palette.Palette

    // Thread-web state. When a NON-zone entity is focused, the left content
    // area (left of the panel) shows the design's signature "every reference
    // is a thread" view: the focused entity at centre, its references fanning
    // out as labelled, clickable nodes joined by dashed threads. The (kind,
    // refID) of each outgoing thread is collected once per focus change (the
    // collection walks ActorList for faction/animset back-refs, so it's not
    // free) and cached; twLast* drive the recompute.
    Field twKind$[31]
    Field twRefID%[31]
    Field twCount%
    Field twOverflow%
    Field twLastKind$
    Field twLastRefID%


    Method create.Composer(threads.Threads)
        self\threads = threads
        self\chipHit = False
        self\editKind = ""
        self\editRefID = 0
        self\editFieldId = ""
        self\editBuffer = ""
        self\editOldValue = ""
        self\deleteArmKind = ""
        self\deleteArmRefID = 0
        self\deleteArmAt = 0
        self\discardArmKind = ""
        self\discardArmAt = 0
        self\palette = Null
        self\scrollOffset = 0
        self\lastContentBottom = 0
        self\bodyTop = 0
        self\bodyBottom = 0
        self\collapsed = False
        self\browser = Null
        self\twCount = 0
        self\twOverflow = 0
        self\twLastKind = "?"
        self\twLastRefID = -1
        self\bulkDeleteArmAt = 0
        self\bulkEditField = ""
        self\bulkEditBuffer = ""
        Return self
    End Method


    // -------------------------------------------------------------------------
    // setBrowser -- injection point so the composer can read the
    // bulk-select set when rendering its bulk-edit panel. Called once
    // by Loom.bb at construction.
    // -------------------------------------------------------------------------
    Method setBrowser(browser.Browser)
        self\browser = browser
    End Method


    // -------------------------------------------------------------------------
    // setPalette -- injection point from Loom.bb so chipRow can dispatch
    // picker-mode opens. Called once at construction.
    // -------------------------------------------------------------------------
    Method setPalette(palette.Palette)
        self\palette = palette
    End Method


    // -------------------------------------------------------------------------
    // width -- 0 when nothing's showing (no focus AND no bulk selection),
    // CMP_COLLAPSED_W when the user collapsed the panel, else CMP_W.
    //
    // Browser bulk-selection causes the panel to render the bulk-edit
    // view instead of nothing, so width%() must report CMP_W in that
    // case too so the Browser shrinks its grid accordingly.
    // -------------------------------------------------------------------------
    Method width%()
        Local hasBulk% = False
        If self\browser <> Null Then hasBulk = Browser::hasSelection(self\browser)
        If self\threads\focusKind = "" And hasBulk = False Then Return 0
        If self\collapsed = True Then Return CMP_COLLAPSED_W
        Return CMP_W
    End Method


    // -------------------------------------------------------------------------
    // isEditing -- read accessor for the outer Loom frame so it knows the
    // composer is currently consuming keystrokes (and the Browser's filter
    // input must stay quiet).
    // -------------------------------------------------------------------------
    Method isEditing%()
        If self\editKind <> "" Then Return True
        If self\bulkEditField <> "" Then Return True
        Return False
    End Method


    // -------------------------------------------------------------------------
    // renderAndUpdate -- per-frame paint + chip hit-test. No-op when nothing
    // is focused. Returns True if any chip was clicked this frame.
    // -------------------------------------------------------------------------
    Method renderAndUpdate%(sw%, sh%)
        // Bulk-edit mode: no single entity focused, but browser has a
        // selection set. Render the bulk-edit panel and return.
        // Priority: focused entity > bulk selection > nothing.
        Local hasBulk% = False
        If self\browser <> Null Then hasBulk = Browser::hasSelection(self\browser)
        If self\threads\focusKind = "" And hasBulk = True
            Composer::renderBulkEdit(self, sw, sh)
            Return False
        EndIf

        If self\threads\focusKind = "" Then Return False

        Local kind$ = self\threads\focusKind
        Local refID% = self\threads\focusID

        // Reset scroll to the top on ANY focus change (new entity / new kind),
        // regardless of edit state. Without this the prior entity's
        // scrollOffset sticks onto the newly-focused one -- and since it's only
        // clamped to the new entity's measured height a frame LATER, it can
        // briefly sit further down than the new (often shorter) entity allows.
        If self\scrollFocusKind <> kind Or self\scrollFocusRefID <> refID
            self\scrollOffset = 0
            self\scrollFocusKind = kind
            self\scrollFocusRefID = refID
        EndIf

        // If focus changed while editing, cancel the pending edit (don't
        // mutate a stale target). Same kind+id keeps the edit active.
        If self\editKind <> ""
            If self\editKind <> kind Or self\editRefID <> refID
                Composer::cancelEdit(self)
            EndIf
        EndIf

        // Reset the per-frame editable-field list before render. Each
        // editableRow/editableIntRow/etc. helper appends via recordField
        // as it paints. pumpKeyboard reads this list (prior frame) to
        // advance the active edit on Tab.
        self\rowFieldCount = 0

        // Mouse wheel scroll. Loom_MouseWheel() is the per-frame wheel
        // DELTA (Loom_BeginFrame derives it from MouseZ's cumulative
        // value); each tick is CMP_SCROLL_STEP pixels. Inverted: wheel-up
        // is positive, which scrolls content toward the top.
        //
        // Gated to the cursor being over THIS panel (mx >= panel left) so
        // scrolling here doesn't also scroll the browser card grid behind
        // the composer. Also gated OUT when the cursor is over the pinned
        // viewport band -- there the wheel is the viewport's zoom, not body
        // scroll (the viewport calls Loom_ConsumeWheel itself when it draws,
        // but it draws AFTER this handler, so we must skip here too).
        // Loom_ConsumeWheel() then zeroes the cached delta so no other
        // surface this frame double-applies the same tick.
        Local overVP% = Composer::cursorOverViewportBand(self, kind, sw)
        Local wheelTicks% = Loom_MouseWheel()
        If wheelTicks <> 0 And self\collapsed = False And overVP = False And MouseX() >= (sw - CMP_W)
            self\scrollOffset = self\scrollOffset - wheelTicks * CMP_SCROLL_STEP
            If self\scrollOffset < 0 Then self\scrollOffset = 0
            // Clamp to last-frame's measured content height -- if the
            // user just scrolled past the end, the clamp here pulls
            // back to a sane offset on the NEXT frame. First-frame
            // overshoot is invisible because no rows would render
            // anyway.
            Local maxScroll% = self\lastContentBottom - self\bodyBottom
            If maxScroll < 0 Then maxScroll = 0
            If self\scrollOffset > maxScroll Then self\scrollOffset = maxScroll
            Loom_ConsumeWheel()
        EndIf

        // Drain keyboard into editBuffer if an edit is active. Consumes Esc
        // (cancel) and Enter (commit) so Loom's outer Esc handler doesn't
        // see them on the same frame.
        If self\editKind <> ""
            Composer::pumpKeyboard(self)
        EndIf

        Local mx% = MouseX()
        Local my% = MouseY()
        Local clicked% = Loom_MouseClicked()
        // Right-click also frame-cached for the same reason -- chipRow
        // would otherwise see right-click in only the first chip.
        Local rightClicked% = Loom_MouseRightClicked()

        // Collapsed mode short-circuits to a thin sliver-render. The
        // browser already shrank its grid by width%() so the sliver
        // doesn't overlap any cards. Return False (no chip click) since
        // chip rendering is skipped in this mode.
        If self\collapsed = True
            Composer::renderCollapsed(self, sw, sh, mx, my, clicked)
            Return False
        EndIf

        Local x% = sw - CMP_W
        Local y% = CMP_TOP
        Local w% = CMP_W
        Local h% = sh - CMP_TOP - CMP_BOT_PAD

        // Panel chrome -- brass left rule signals the primary surface.
        // Mode-varied background: tool=flat, balanced=subtle gradient,
        // in-world=dramatic darker gradient.
        If Loom_ChromeIsTool() = True
            LoomFill(x, y, w, h, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B)
        Else If Loom_ChromeIsInWorld() = True
            LoomGradientV(x, y, w, h, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B, LOOM_STONE_950_R, LOOM_STONE_950_G, LOOM_STONE_950_B)
        Else
            LoomGradientV(x, y, w, h, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B)
        EndIf
        LoomBorder(x, y, w, h, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        LoomFill(x, y, 3, h, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        // In-world mode adds a second brass rule on the right edge as a
        // mirror flourish (book-spine effect with the left edge).
        If Loom_ChromeIsInWorld() = True
            LoomFill(x + w - 3, y, 3, h, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        EndIf

        // Title block
        Local kindLabel$ = Composer::kindLabel(self, kind)
        Local entityName$ = Threads::lookupName(self\threads, kind, refID)
        If entityName = "" Then entityName = "(unknown)"

        // Dirty asterisk -- prefix the entity name when its kind has unsaved
        // edits. Visible regardless of who made the edit (Loom or GUE).
        Local dirty% = Composer::isDirtyForKind(self, kind)
        If dirty = True Then entityName = "* " + entityName

        LoomText(x + CMP_PAD, y + CMP_PAD, kindLabel, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomText(x + CMP_PAD, y + CMP_PAD + 16, entityName, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        // Collapse chevron -- top-left corner of the panel, just inside
        // the brass left rule. Click flips self\collapsed = True; next
        // frame the renderCollapsed short-circuit runs.
        Composer::drawCollapseButton(self, x + 6, y + CMP_PAD - 2, mx, my, clicked, ">")

        // Top-right action cluster (right-to-left): Save / Discard /
        // Duplicate / Delete. Save + Discard only render when there's
        // pending dirty state for this kind. Duplicate + Delete always
        // render (arm/confirm guards against Delete accidents; Duplicate
        // is non-destructive so no arm needed).
        Local btnY% = y + CMP_PAD - 2
        Local btnX% = x + w - CMP_SAVE_BTN_W - CMP_PAD
        If dirty = True
            Composer::drawSaveButton(self, btnX, btnY, mx, my, clicked, kind)
            btnX = btnX - CMP_DISCARD_BTN_W - 6
            Composer::drawDiscardButton(self, btnX, btnY, mx, my, clicked, kind)
            btnX = btnX - CMP_DUP_BTN_W - 6
        Else
            btnX = btnX + CMP_SAVE_BTN_W - CMP_DUP_BTN_W
        EndIf
        Composer::drawDuplicateButton(self, btnX, btnY, mx, my, clicked, kind, refID)
        btnX = btnX - CMP_DELETE_BTN_W - 6
        Composer::drawDeleteButton(self, btnX, btnY, mx, my, clicked, kind, refID)

        LoomHRule(x + CMP_PAD, y + CMP_PAD + 38, w - CMP_PAD * 2, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)

        // Body -- per-kind render dispatch
        // bodyY is the unscrolled top; rows render starting from
        // bodyY - scrollOffset and the helpers (row / editableRow / ...)
        // skip painting when their rowY falls outside [bodyTop, bodyBottom].
        // self\bodyTop / bodyBottom give the visible bounds; the per-kind
        // renderer's final y becomes self\lastContentBottom so next
        // frame can clamp scrollOffset.
        Local bodyY% = y + CMP_PAD + 50
        Local bodyH% = h - (bodyY - y) - 24

        // Pinned viewport band. Mesh-preview kinds (actor / item / mesh)
        // reserve a fixed, NON-scrolling band at the top of the body; the
        // field rows scroll BELOW it. viewportSize()=0 for zone, so it
        // reserves NO band -- zone renders full-screen on the left instead
        // (see the zone branch below), and its fields fill the full panel.
        Local vpSz% = Composer::viewportSize(self, kind)
        Local vpBandH% = 0
        If vpSz > 0 Then vpBandH = vpSz + 10

        self\bodyTop    = bodyY + vpBandH
        self\bodyBottom = bodyY + bodyH
        Local scrolledBodyY% = self\bodyTop - self\scrollOffset
        self\chipHit = False

        // Draw the pinned mesh-preview box first, at the fixed band top.
        If vpBandH > 0
            Composer::drawPinnedViewport(self, kind, refID, x, w, bodyY)
        EndIf

        // Zone gets a full-screen fly-around viewport in the left content
        // area (left of the composer panel). Direct-to-back-buffer; the
        // composer panel overlays on the right so the zone fields stay
        // editable while flying.
        If kind = "zone"
            Composer::drawZoneFullscreen(self, refID, sw, sh)
        Else
            // Non-zone: the left content area shows the thread web -- the
            // focused entity with its references fanning out as clickable
            // thread nodes (the design's signature surface).
            Composer::drawThreadWeb(self, kind, refID, sw, sh, mx, my, clicked, rightClicked)
        EndIf

        If kind = "actor"
            Composer::renderActor(self, x, scrolledBodyY, w, bodyH, mx, my, clicked, rightClicked)
        Else If kind = "item"
            Composer::renderItem(self, x, scrolledBodyY, w, bodyH, mx, my, clicked, rightClicked)
        Else If kind = "spell"
            Composer::renderSpell(self, x, scrolledBodyY, w, bodyH, mx, my, clicked, rightClicked)
        Else If kind = "zone"
            Composer::renderZone(self, x, scrolledBodyY, w, bodyH, mx, my, clicked, rightClicked)
        Else If kind = "faction"
            Composer::renderFaction(self, x, scrolledBodyY, w, bodyH, mx, my, clicked, rightClicked)
        Else If kind = "animset"
            Composer::renderAnimSet(self, x, scrolledBodyY, w, bodyH, mx, my, clicked, rightClicked)
        Else If kind = "settings"
            Composer::renderSettings(self, x, scrolledBodyY, w, bodyH, mx, my, clicked)
        Else If kind = "script"
            Composer::renderScript(self, x, scrolledBodyY, w, bodyH, mx, my, clicked, rightClicked)
        Else If kind = "texture"
            Composer::renderTexture(self, x, scrolledBodyY, w, bodyH, mx, my, clicked, rightClicked)
        Else If kind = "mesh"
            Composer::renderMesh(self, x, scrolledBodyY, w, bodyH, mx, my, clicked, rightClicked)
        Else If kind = "sound"
            Composer::renderSound(self, x, scrolledBodyY, w, bodyH, mx, my, clicked, rightClicked)
        Else If kind = "music"
            Composer::renderMusic(self, x, scrolledBodyY, w, bodyH, mx, my, clicked, rightClicked)
        Else If kind = "stats"
            Composer::renderStats(self, x, scrolledBodyY, w, bodyH, mx, my, clicked)
        EndIf

        // Scrollbar indicator -- thin brass thumb on the right edge,
        // visible only when content overflows.
        If self\lastContentBottom > self\bodyBottom
            Composer::drawScrollbar(self, x + w - CMP_SCROLLBAR_W - 2, self\bodyTop, bodyH)
            // Back-to-top pill -- shown when scrolled past 100px so
            // long composers (Actor 96+ cells, Zone 1000s) have an
            // O(1) way back to the top without manual scrolling.
            If self\scrollOffset > 100
                Composer::drawBackToTop(self, x + w - 56, self\bodyBottom - 28, mx, my, clicked)
            EndIf
        EndIf

        // Footer: back-stack hint (or edit-mode hint when editing). Two
        // separate Ifs rather than If/Else-If to dodge the BlitzForge
        // Else-If scope leak (issue #61).
        Local stackSize% = ListSize(self\threads\backStack)
        Local footMsg$ = "Esc returns to browser"
        If self\editKind = "" And stackSize > 0
            footMsg = "Esc walks back  |  " + Str(stackSize) + " in trail"
        EndIf
        If self\editKind <> ""
            footMsg = "Enter to commit  |  Esc to cancel edit"
        EndIf
        LoomText(x + CMP_PAD, y + h - 22, footMsg, LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)

        Return self\chipHit
    End Method


    // -------------------------------------------------------------------------
    // Layout helpers -- private-by-convention; called from per-kind renderers.
    // -------------------------------------------------------------------------

    // canPaintRow -- returns True when a row at (rowY, rowH) fits
    // entirely within the visible body bounds (self\bodyTop ..
    // self\bodyBottom). Used by every row helper to suppress painting
    // when the scrolled row is off-screen -- Blitz3D has no 2D
    // clip-rect, so without these gates the rows would leak into the
    // title block above or the footer below the panel body.
    //
    // Helpers always return rowY + rowH (advancing the cursor) even
    // when not painting, so subsequent rows position correctly.
    Method canPaintRow%(rowY%, rowH%)
        If self\bodyBottom = 0 Then Return True   ; defensive: before first frame
        If rowY < self\bodyTop Then Return False
        If rowY + rowH > self\bodyBottom Then Return False
        Return True
    End Method


    // label : value row. Returns the next Y.
    Method row%(panelX%, panelW%, rowY%, label$, value$)
        If Composer::canPaintRow(self, rowY, CMP_ROW_H) = True
            LoomText(panelX + CMP_PAD,       rowY, label, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            LoomText(panelX + CMP_PAD + 120, rowY, value, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        EndIf
        Return rowY + CMP_ROW_H
    End Method


    // -------------------------------------------------------------------------
    // editableIntRow / editableFloatRow / toggleRow -- thin wrappers over
    // editableRow that convert the stored numeric value to a string for
    // display + buffer-seed. On commit, writeField parses the buffer and
    // ignores bad input (leaves the stored value unchanged).
    //
    // toggleRow is special: there's no edit buffer because a bool only has
    // two states. Click the value cell to flip in-place, mark dirty.
    // -------------------------------------------------------------------------
    Method editableIntRow%(panelX%, panelW%, rowY%, label$, kind$, refID%, fieldId$, storedValue%, mx%, my%, clicked%)
        Return Composer::editableRow(self, panelX, panelW, rowY, label, kind, refID, fieldId, Str(storedValue), mx, my, clicked)
    End Method


    Method editableFloatRow%(panelX%, panelW%, rowY%, label$, kind$, refID%, fieldId$, storedValue#, mx%, my%, clicked%)
        Return Composer::editableRow(self, panelX, panelW, rowY, label, kind, refID, fieldId, Composer::formatFloat(self, storedValue), mx, my, clicked)
    End Method


    // -------------------------------------------------------------------------
    // enumIntRow -- editableIntRow plus a small annotation "(enumName)"
    // painted in stone-tone after the value. Designers see the enum-
    // resolved meaning without leaving the int input.
    //
    // enumKind selects the lookup family:
    //   "itemtype" / "slottype" / "weapontype" / "damagetype" /
    //   "aggressiveness" / "environment" / "genders" / "trademode"
    //
    // Empty enumName means the value is out-of-range; render "?".
    // Skipped while the field is being edited (the cursor would
    // visually conflict with the annotation).
    // -------------------------------------------------------------------------
    Method enumIntRow%(panelX%, panelW%, rowY%, label$, kind$, refID%, fieldId$, storedValue%, enumKind$, mx%, my%, clicked%)
        Local nextY% = Composer::editableIntRow(self, panelX, panelW, rowY, label, kind, refID, fieldId, storedValue, mx, my, clicked)

        If Composer::canPaintRow(self, rowY, CMP_ROW_H) = False Then Return nextY
        Local active% = (self\editKind = kind And self\editRefID = refID And self\editFieldId = fieldId)
        If active = True Then Return nextY

        Local name$ = Composer::enumName(self, enumKind, storedValue)
        If name = "" Then name = "?"

        // Paint annotation past the int value text. The value cell
        // starts at panelX + CMP_PAD + 120 + 4. 48px in is enough room
        // for typical ints (0..1000) but short enough to stay readable.
        Local annoX% = panelX + CMP_PAD + 120 + 4 + 48
        LoomText(annoX, rowY, "(" + name + ")", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)

        Return nextY
    End Method


    // -------------------------------------------------------------------------
    // enumName -- value-to-name lookup keyed by enumKind. Returns ""
    // for unresolved (out-of-range / undefined slot) values.
    // -------------------------------------------------------------------------
    Method enumName$(enumKind$, v%)
        If enumKind = "itemtype"
            If v = 1 Then Return "Weapon"
            If v = 2 Then Return "Armour"
            If v = 3 Then Return "Ring"
            If v = 4 Then Return "Potion"
            If v = 5 Then Return "Ingredient"
            If v = 6 Then Return "Image"
            If v = 7 Then Return "Other"
            Return ""
        EndIf
        If enumKind = "slottype"
            If v = 1  Then Return "Weapon"
            If v = 2  Then Return "Shield"
            If v = 3  Then Return "Hat"
            If v = 4  Then Return "Chest"
            If v = 5  Then Return "Hand"
            If v = 6  Then Return "Belt"
            If v = 7  Then Return "Legs"
            If v = 8  Then Return "Feet"
            If v = 9  Then Return "Ring"
            If v = 10 Then Return "Amulet"
            If v = 11 Then Return "Backpack"
            Return ""
        EndIf
        If enumKind = "weapontype"
            If v = 1 Then Return "OneHand"
            If v = 2 Then Return "TwoHand"
            If v = 3 Then Return "Ranged"
            Return ""
        EndIf
        If enumKind = "damagetype"
            If v < 0 Or v > 19 Then Return ""
            Local nm$ = DamageTypes$(v)
            If nm = "" Then Return ""
            Return nm
        EndIf
        If enumKind = "aggressiveness"
            If v = 0 Then Return "passive"
            If v = 1 Then Return "provoked"
            If v = 2 Then Return "on-sight"
            If v = 3 Then Return "no combat"
            Return ""
        EndIf
        If enumKind = "environment"
            If v = 0 Then Return "amphibious"
            If v = 1 Then Return "swim"
            If v = 2 Then Return "fly"
            If v = 3 Then Return "walk"
            Return ""
        EndIf
        If enumKind = "genders"
            If v = 0 Then Return "both"
            If v = 1 Then Return "male only"
            If v = 2 Then Return "female only"
            If v = 3 Then Return "none"
            Return ""
        EndIf
        If enumKind = "trademode"
            If v = 0 Then Return "no trade"
            If v = 1 Then Return "free"
            If v = 2 Then Return "charged"
            Return ""
        EndIf
        Return ""
    End Method


    // -------------------------------------------------------------------------
    // assetIntRow -- editableIntRow variant for entity fields that hold
    // a texture / mesh / sound ID. Renders the existing editable int
    // field PLUS, when the value resolves in the corresponding catalog,
    // paints a small "jump >>" brass pill at the right edge. Click pill
    // -> Threads::jump to the catalog entry (Esc back-stacks to the
    // originating entity). Right-click anywhere in the value cell opens
    // the palette as a picker filtered to that asset kind.
    //
    // assetKind: "texture" | "mesh" | "sound" -- selects the catalog
    //            and palette filter
    //
    // Value 0 typically means "no asset bound" (engine convention); we
    // skip the pill in that case to avoid jumping to a non-existent
    // catalog entry. Resolved 0 (e.g. ID 0 actually present) still
    // gets the pill since the catalog walk finds it.
    //
    // Same shape as scriptStringRow from iter 61.
    // -------------------------------------------------------------------------
    Method assetIntRow%(panelX%, panelW%, rowY%, label$, kind$, refID%, fieldId$, storedValue%, assetKind$, mx%, my%, clicked%, rightClicked%)
        Local nextY% = Composer::editableIntRow(self, panelX, panelW, rowY, label, kind, refID, fieldId, storedValue, mx, my, clicked)

        // Right-click on the value cell -> open picker for that asset
        // kind. Hit-test matches editableRow's value rect.
        Local valX% = panelX + CMP_PAD + 120
        Local valW% = panelW - CMP_PAD * 2 - 120
        Local valHovered% = (mx >= valX And mx < valX + valW And my >= rowY - 3 And my < rowY - 3 + CMP_ROW_H)
        If valHovered = True And rightClicked = True And self\palette <> Null
            Palette::openAsPicker(self\palette, assetKind, kind, refID, fieldId)
        EndIf

        // Skip pill when scrolled off or asset is unset (ID 0 has no
        // catalog entry in the shipped data; treat as "no binding").
        If storedValue = 0 Then Return nextY
        If Composer::canPaintRow(self, rowY, CMP_ROW_H) = False Then Return nextY

        // Catalog lookup per asset kind.
        Local resolved% = False
        Local catalogIdx% = -1
        If assetKind = "texture"
            Local te.TextureEntry = Textures_GetByID(storedValue)
            If te <> Null Then resolved = True : catalogIdx = te\Index
        EndIf
        If assetKind = "mesh"
            Local mh.MeshEntry = Meshes_GetByID(storedValue)
            If mh <> Null Then resolved = True : catalogIdx = mh\Index
        EndIf
        If assetKind = "sound"
            Local sd.SoundEntry = Sounds_GetByID(storedValue)
            If sd <> Null Then resolved = True : catalogIdx = sd\Index
        EndIf

        // Paint pill at right edge of value cell
        Local pillW% = 48
        Local pillH% = CMP_ROW_H - 2
        Local pillX% = panelX + panelW - CMP_PAD - pillW
        Local pillY% = rowY - 2
        Local pillHovered% = (mx >= pillX And mx < pillX + pillW And my >= pillY And my < pillY + pillH)

        If resolved = False
            // ID set but not in catalog: orange "missing" pill (less
            // severe than the broken-script "danger" red since assets
            // can be runtime-loaded outside the catalog by content).
            LoomFill(pillX, pillY, pillW, pillH, LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B)
            LoomText(pillX + 4, rowY, "missing", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        Else
            If pillHovered = True
                LoomFill(pillX, pillY, pillW, pillH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
                LoomBorder(pillX, pillY, pillW, pillH, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
                LoomText(pillX + 6, rowY, "jump >>", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
            Else
                LoomFill(pillX, pillY, pillW, pillH, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
                LoomBorder(pillX, pillY, pillW, pillH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
                LoomText(pillX + 6, rowY, "jump >>", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            EndIf

            If pillHovered = True And clicked = True
                Threads::jump(self\threads, assetKind, catalogIdx)
            EndIf
        EndIf

        Return nextY
    End Method


    // -------------------------------------------------------------------------
    // doubleIntRow -- [label | int input A | int input B] for two-column
    // grids (Attributes Value | Max, etc). Each cell independently
    // clickable / editable via a distinct fieldId.
    // -------------------------------------------------------------------------
    Method doubleIntRow%(panelX%, panelW%, rowY%, label$, kind$, refID%, fieldIdA$, valueA%, fieldIdB$, valueB%, mx%, my%, clicked%)
        Local labelX% = panelX + CMP_PAD
        Local cellW% = (panelW - CMP_PAD * 2 - 140) / 2
        If cellW < 40 Then cellW = 40
        Local cellAX% = panelX + CMP_PAD + 140
        Local cellBX% = cellAX + cellW + 4
        Local valY% = rowY - 3
        Local valH% = CMP_ROW_H

        Local visible% = Composer::canPaintRow(self, rowY, CMP_ROW_H)
        If visible = True
            LoomText(labelX, rowY, label, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        EndIf

        Composer::miniEditCell(self, cellAX, valY, cellW, valH, rowY, kind, refID, fieldIdA, Str(valueA), mx, my, clicked, visible)
        Composer::miniEditCell(self, cellBX, valY, cellW, valH, rowY, kind, refID, fieldIdB, Str(valueB), mx, my, clicked, visible)

        Return rowY + CMP_ROW_H
    End Method


    // -------------------------------------------------------------------------
    // miniEditCell -- paint one editable int cell at an explicit rect.
    // Used by doubleIntRow for compact two-column edits. Same edit
    // dispatch as editableRow but the cell is bounded to the caller's
    // (cellX, cellW) rather than the row's full value column.
    // -------------------------------------------------------------------------
    Method miniEditCell(cellX%, cellY%, cellW%, cellH%, textRowY%, kind$, refID%, fieldId$, storedValue$, mx%, my%, clicked%, visible%)
        // Record for Tab navigation -- doubleIntRow calls this for both
        // cells, so a Tab on the Value cell advances to Max and another
        // Tab to the next row's Value etc.
        Composer::recordField(self, kind, refID, fieldId, storedValue)
        Local active% = (self\editKind = kind And self\editRefID = refID And self\editFieldId = fieldId)
        Local hovered% = (mx >= cellX And mx < cellX + cellW And my >= cellY And my < cellY + cellH)

        If visible = True
            If active = True
                LoomFill(cellX, cellY, cellW, cellH, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
                LoomBorder(cellX, cellY, cellW, cellH, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
            Else If hovered = True
                LoomFill(cellX, cellY, cellW, cellH, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
                LoomBorder(cellX, cellY, cellW, cellH, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
            EndIf

            Local shown$
            If active = True
                shown = self\editBuffer
            Else
                shown = storedValue
            EndIf
            LoomText(cellX + 4, textRowY, shown, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

            If active = True
                If (MilliSecs() Mod CMP_CURSOR_PERIOD) < (CMP_CURSOR_PERIOD / 2)
                    Local cursorX% = cellX + 4 + StringWidth(self\editBuffer)
                    LoomFill(cursorX, textRowY, 2, 14, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
                EndIf
            EndIf
        EndIf

        If hovered And clicked And active = False
            Composer::beginEdit(self, kind, refID, fieldId, storedValue)
        Else If clicked And active = True And hovered = False
            Composer::commitEdit(self)
        EndIf
    End Method


    // toggleRow -- click the value cell to flip the stored bool. Returns next Y.
    Method toggleRow%(panelX%, panelW%, rowY%, label$, kind$, refID%, fieldId$, storedValue%, mx%, my%, clicked%)
        Local valX% = panelX + CMP_PAD + 120
        Local valY% = rowY - 3
        Local valW% = panelW - CMP_PAD * 2 - 120
        Local valH% = CMP_ROW_H
        Local hovered% = (mx >= valX And mx < valX + valW And my >= valY And my < valY + valH)
        Local visible% = Composer::canPaintRow(self, rowY, CMP_ROW_H)

        If visible = True
            If hovered = True
                LoomFill(valX, valY, valW, valH, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
                LoomBorder(valX, valY, valW, valH, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
            EndIf

            LoomText(panelX + CMP_PAD, rowY, label, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

            // Mini toggle indicator on the right of the value cell -- a small
            // brass pill that's filled when True, hollow when False. Affordance
            // makes the click target read as a switch.
            Local pillW% = 30
            Local pillH% = 14
            Local pillX% = valX + 4
            Local pillY% = valY + (valH - pillH) / 2
            If storedValue = True
                LoomFill(pillX, pillY, pillW, pillH, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
                LoomFill(pillX + pillW - 12, pillY + 2, 10, pillH - 4, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
            Else
                LoomBorder(pillX, pillY, pillW, pillH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
                LoomFill(pillX + 2, pillY + 2, 10, pillH - 4, LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
            EndIf

            // Label text
            Local labelTxt$ = "No"
            If storedValue = True Then labelTxt = "Yes"
            LoomText(pillX + pillW + 8, rowY, labelTxt, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        EndIf

        If hovered And clicked
            // Cancel any pending text-edit on a different field first.
            If self\editKind <> "" Then Composer::commitEdit(self)
            Local newVal% = True
            If storedValue = True Then newVal = False
            Composer::writeField(self, kind, refID, fieldId, Str(newVal))
            Composer::markDirtyForKind(self, kind)
            Timeline_RecordToggle(kind, refID, fieldId, Str(storedValue), Str(newVal), Threads::lookupName(self\threads, kind, refID))
            WorldCache_Invalidate()
            WriteLog(LoomLog, "Composer: toggled " + kind + "#" + Str(refID) + " " + fieldId + " -> " + Str(newVal))
        EndIf

        Return rowY + CMP_ROW_H
    End Method


    // -------------------------------------------------------------------------
    // editableRow -- like row(), but the value cell is clickable to begin
    // editing. When this exact (kind, refID, fieldId) is active, the cell
    // shows the edit buffer with a blinking cursor instead of the stored
    // value. Click elsewhere or Enter to commit; Esc to cancel.
    //
    // Returns the next Y.
    // -------------------------------------------------------------------------
    Method editableRow%(panelX%, panelW%, rowY%, label$, kind$, refID%, fieldId$, storedValue$, mx%, my%, clicked%)
        // Record for Tab navigation. Done unconditionally (even off-
        // screen rows), so Shift+Tab to a scrolled-up row still finds
        // it. The row's visibility only affects mouse hit-testing.
        Composer::recordField(self, kind, refID, fieldId, storedValue)
        Local valX% = panelX + CMP_PAD + 120
        Local valY% = rowY - 3
        Local valW% = panelW - CMP_PAD * 2 - 120
        Local valH% = CMP_ROW_H

        Local active% = (self\editKind = kind And self\editRefID = refID And self\editFieldId = fieldId)
        Local hovered% = (mx >= valX And mx < valX + valW And my >= valY And my < valY + valH)
        Local visible% = Composer::canPaintRow(self, rowY, CMP_ROW_H)

        If visible = True
            // Background + border. Editing > hover > flat.
            If active = True
                LoomFill(valX, valY, valW, valH, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
                LoomBorder(valX, valY, valW, valH, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
            Else If hovered = True
                LoomFill(valX, valY, valW, valH, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
                LoomBorder(valX, valY, valW, valH, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
            EndIf

            // Label
            LoomText(panelX + CMP_PAD, rowY, label, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

            // Value (buffer when editing; stored value otherwise)
            Local shown$
            If active = True
                shown = self\editBuffer
            Else
                shown = storedValue
            EndIf
            LoomText(valX + 4, rowY, shown, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

            // Blinking cursor at end of buffer when editing
            If active = True
                If (MilliSecs() Mod CMP_CURSOR_PERIOD) < (CMP_CURSOR_PERIOD / 2)
                    Local cursorX% = valX + 4 + StringWidth(self\editBuffer)
                    LoomFill(cursorX, rowY, 2, 14, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
                EndIf
            EndIf

            // Hover affordance -- a small "edit" cue on the right edge of
            // the cell so users discover the click-to-edit pattern without
            // having to click first. Only shown when hovering AND not
            // currently editing this field.
            If hovered = True And active = False
                LoomText(valX + valW - 26, rowY, "edit", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            EndIf
        EndIf

        // Click to begin / commit. Click on the value rect while NOT editing
        // begins; click outside while editing commits (handled here by no-op
        // -- the next-frame check at top of renderAndUpdate sees mismatched
        // focus and cancels, but we want commit-on-click-elsewhere).
        If hovered And clicked And active = False
            Composer::beginEdit(self, kind, refID, fieldId, storedValue)
        Else If clicked And active = True And hovered = False
            Composer::commitEdit(self)
        EndIf

        Return rowY + CMP_ROW_H
    End Method


    // -------------------------------------------------------------------------
    // scriptStringRow -- editableRow variant for entity fields that hold
    // a script basename (Item\Script$, Spell\Script$, Area\EntryScript$,
    // Area\TriggerScript$[i] etc). Renders the existing editable text
    // field PLUS, when the stored value resolves to a file in the
    // ScriptsCatalog, paints a small "jump >>" pill at the right of the
    // row. Click pill -> Threads::focus("script", catalogIndex), pushing
    // the current entity to the back stack -- the script preview pops up
    // and Esc walks back to the entity in one move.
    //
    // Broken-script case (value non-empty but no .rsl in catalog) paints
    // a danger-red "missing" tag instead. Issues modal also flags it for
    // bulk visibility, but the inline cue is faster for the designer
    // who's already on the entity.
    //
    // Empty value: pure editableRow behavior (just type a name to bind).
    // -------------------------------------------------------------------------
    Method scriptStringRow%(panelX%, panelW%, rowY%, label$, kind$, refID%, fieldId$, storedValue$, mx%, my%, clicked%, rightClicked%)
        // First paint the editable text field at the normal width.
        // We don't shrink it -- the jump pill paints OVER the right edge
        // since the editable buffer rarely fills its width and the visual
        // "edit" hover hint anchors there too (the pill replaces it).
        Local nextY% = Composer::editableRow(self, panelX, panelW, rowY, label, kind, refID, fieldId, storedValue, mx, my, clicked)

        // Right-click anywhere in the value cell opens the palette as a
        // script picker. Works on EMPTY rows too -- that's how a designer
        // binds a fresh script without remembering the name. Hit-test
        // matches editableRow's value rect (panelX + CMP_PAD + 120 onwards).
        Local valX% = panelX + CMP_PAD + 120
        Local valW% = panelW - CMP_PAD * 2 - 120
        Local valHovered% = (mx >= valX And mx < valX + valW And my >= rowY - 3 And my < rowY - 3 + CMP_ROW_H)
        If valHovered = True And rightClicked = True And self\palette <> Null
            Palette::openAsPicker(self\palette, "script", kind, refID, fieldId)
        EndIf

        // Skip the pill entirely if the field is empty -- nothing to
        // navigate TO. Also skip when the row scrolled off-screen.
        If storedValue = "" Then Return nextY
        If Composer::canPaintRow(self, rowY, CMP_ROW_H) = False Then Return nextY

        // Lookup the script in the catalog. Tolerates extension drift
        // and case (Scripts_NormalizeName$).
        Local sf.ScriptFile = Scripts_GetByName(storedValue)

        // Paint the pill at the far-right edge of the value cell. Y
        // matches editableRow's value baseline (rowY).
        Local pillW% = 48
        Local pillH% = CMP_ROW_H - 2
        Local pillX% = panelX + panelW - CMP_PAD - pillW
        Local pillY% = rowY - 2
        Local hovered% = (mx >= pillX And mx < pillX + pillW And my >= pillY And my < pillY + pillH)

        If sf = Null
            // Missing-script: danger-red "missing" pill, non-clickable.
            // Cell content already shows the typo'd name; this confirms
            // it's broken so the designer doesn't blame their click.
            LoomFill(pillX, pillY, pillW, pillH, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
            LoomText(pillX + 4, rowY, "missing", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        Else
            // Resolved -- paint a clickable brass pill.
            If hovered = True
                LoomFill(pillX, pillY, pillW, pillH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
                LoomBorder(pillX, pillY, pillW, pillH, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
                LoomText(pillX + 6, rowY, "jump >>", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
            Else
                LoomFill(pillX, pillY, pillW, pillH, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
                LoomBorder(pillX, pillY, pillW, pillH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
                LoomText(pillX + 6, rowY, "jump >>", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            EndIf

            // Click pill -> jump to script (back stack push so Esc walks
            // back to the originating entity).
            If hovered = True And clicked = True
                Threads::jump(self\threads, "script", sf\Index)
            EndIf
        EndIf

        Return nextY
    End Method


    // -------------------------------------------------------------------------
    // beginEdit -- enter edit mode for a specific (kind, refID, fieldId).
    // Seeds the buffer with the current stored value.
    // -------------------------------------------------------------------------
    Method beginEdit(kind$, refID%, fieldId$, currentValue$)
        // Commit any pending edit on a different field before switching.
        If self\editKind <> ""
            Composer::commitEdit(self)
        EndIf
        self\editKind = kind
        self\editRefID = refID
        self\editFieldId = fieldId
        self\editBuffer = currentValue
        self\editOldValue = currentValue
        FlushKeys      // discard any buffered keystrokes from the click itself
        WriteLog(LoomLog, "Composer: begin edit " + kind + "#" + Str(refID) + " " + fieldId + " = " + Chr(34) + currentValue + Chr(34))
    End Method


    // -------------------------------------------------------------------------
    // commitEdit -- write the buffer to the target field and mark the kind's
    // *Saved global False. Clears edit state.
    // -------------------------------------------------------------------------
    Method commitEdit()
        If self\editKind = "" Then Return

        Local k$ = self\editKind
        Local id% = self\editRefID
        Local fid$ = self\editFieldId
        Local val$ = self\editBuffer
        Local oldVal$ = self\editOldValue

        Composer::writeField(self, k, id, fid, val)
        Composer::markDirtyForKind(self, k)

        // Record only when the value actually changed (avoids spamming
        // the timeline with no-op commits from click-away-without-typing).
        If val <> oldVal
            Timeline_RecordEdit(k, id, fid, oldVal, val, Threads::lookupName(self\threads, k, id))
            // Reference-field edits + faction renames can change the
            // broken-ref count, and any edit changes "what to show in
            // the recents label" etc. Conservatively invalidate the
            // shared cache; the next ribbon paint recomputes.
            WorldCache_Invalidate()
        EndIf

        WriteLog(LoomLog, "Composer: commit " + k + "#" + Str(id) + " " + fid + " <- " + Chr(34) + val + Chr(34))

        self\editKind = ""
        self\editRefID = 0
        self\editFieldId = ""
        self\editBuffer = ""
        self\editOldValue = ""
    End Method


    // -------------------------------------------------------------------------
    // cancelEdit -- drop the buffer; field stays at its previous stored value.
    // -------------------------------------------------------------------------
    Method cancelEdit()
        If self\editKind = "" Then Return
        WriteLog(LoomLog, "Composer: cancel edit " + self\editKind + "#" + Str(self\editRefID) + " " + self\editFieldId)
        self\editKind = ""
        self\editRefID = 0
        self\editFieldId = ""
        self\editBuffer = ""
        self\editOldValue = ""
    End Method


    // -------------------------------------------------------------------------
    // pumpKeyboard -- called per-frame when editing. Drains the keyboard
    // input queue into editBuffer, handles Enter (commit) / Esc (cancel) /
    // Backspace.
    // -------------------------------------------------------------------------
    Method pumpKeyboard()
        // Backspace
        If KeyHit(14) And Len(self\editBuffer) > 0
            self\editBuffer = Left$(self\editBuffer, Len(self\editBuffer) - 1)
        EndIf

        // Enter -- commit
        If KeyHit(28)
            Composer::commitEdit(self)
            Return
        EndIf

        // Tab -- commit current edit + advance to next editable field
        // in the prior frame's render order. Shift+Tab goes backwards.
        // KeyHit(15) = Tab, KeyDown(42) = LShift, KeyDown(54) = RShift.
        If KeyHit(15)
            Local shift% = (KeyDown(42) Or KeyDown(54))
            Local stepDir% = 1
            If shift = True Then stepDir = -1
            Composer::advanceEditField(self, stepDir)
            Return
        EndIf

        // Esc -- cancel (consumed here so Loom's outer Esc doesn't fire)
        If KeyHit(1)
            Composer::cancelEdit(self)
            Return
        EndIf

        // Printable chars (drain the GetKey queue). Filter to ASCII 32..126
        // so we don't get control chars in the buffer.
        Local k% = GetKey()
        While k > 0
            If k >= 32 And k <= 126
                self\editBuffer = self\editBuffer + Chr(k)
            EndIf
            k = GetKey()
        Wend
    End Method


    // -------------------------------------------------------------------------
    // recordField -- per-frame field-list append. Called by every
    // editable-row helper during render. Used by Tab navigation in
    // pumpKeyboard to find the next/prev field after the current edit.
    // Cap silently at 512 -- the dropped fields just can't be tabbed to
    // (only matters for very dense composers; users will still hit a
    // visible row by mouse or scrolling).
    // -------------------------------------------------------------------------
    Method recordField(kind$, refID%, fieldId$, storedValue$)
        If self\rowFieldCount >= 512 Then Return
        self\rowFieldKinds$[self\rowFieldCount] = kind
        self\rowFieldRefIDs[self\rowFieldCount] = refID
        self\rowFieldIds$[self\rowFieldCount] = fieldId
        self\rowFieldValues$[self\rowFieldCount] = storedValue
        self\rowFieldCount = self\rowFieldCount + 1
    End Method


    // -------------------------------------------------------------------------
    // advanceEditField -- on Tab / Shift+Tab, commit the current edit
    // and beginEdit on the next/prev recorded field. Wraps end-to-start
    // and start-to-end so the user can cycle continuously.
    //
    // Uses the prior frame's recordField list (still in the rowField*
    // arrays since renderAndUpdate resets at start of frame and
    // pumpKeyboard runs from inside renderAndUpdate after the previous
    // frame's paint already populated them; effectively a one-frame
    // lag with no visible artifact).
    // -------------------------------------------------------------------------
    Method advanceEditField(stepDir%)
        If self\rowFieldCount = 0 Then Return
        If self\editKind = "" Then Return    // not editing -- nothing to advance from

        // Find current edit in the list.
        Local curIdx% = -1
        Local i% = 0
        For i = 0 To self\rowFieldCount - 1
            If self\rowFieldKinds$[i] = self\editKind And self\rowFieldRefIDs[i] = self\editRefID And self\rowFieldIds$[i] = self\editFieldId
                curIdx = i
                Exit
            EndIf
        Next
        If curIdx < 0 Then Return     // current edit isn't in the list (off-screen?)

        // Wrap-around index step.
        Local nextIdx% = curIdx + stepDir
        If nextIdx < 0 Then nextIdx = self\rowFieldCount - 1
        If nextIdx >= self\rowFieldCount Then nextIdx = 0

        // Commit current, then begin edit on the new target. We need
        // to read the new target's stored value -- the recordField
        // list only has identity, not value. Use a per-kind readField
        // helper would be ideal but we don't have one. Workaround:
        // commit current edit which writes the buffer back, then
        // call beginEdit with "" as the seed -- the user will see an
        // empty buffer they can type into, OR cancel (Esc) without
        // mutating. Acceptable for first cut; iter 76 can add proper
        // readField to seed the buffer with the current value.
        Composer::commitEdit(self)
        Composer::beginEdit(self, self\rowFieldKinds$[nextIdx], self\rowFieldRefIDs[nextIdx], self\rowFieldIds$[nextIdx], self\rowFieldValues$[nextIdx])
    End Method


    // -------------------------------------------------------------------------
    // writeField -- dispatch table mapping (kind, fieldId) -> in-memory write.
    // Only fields explicitly handled here are editable; unknown combinations
    // are no-ops (logged for diagnostics).
    //
    // Numeric fields use parseInt / parseFloat below; bad input leaves the
    // stored value alone (parseX returns the supplied default which is the
    // current stored value at call time, but we read+write in one expression
    // so the default flows correctly).
    //
    // Bool fields are toggled via toggleRow which writes "0"/"1" as the
    // value string; we just compare to "1".
    // -------------------------------------------------------------------------
    Method writeField(kind$, refID%, fieldId$, value$)
        // ---- SPELL ----------------------------------------------------------
        If kind = "spell"
            If refID < 0 Or refID > 65534 Then Return
            Local S.Spell = SpellsList(refID)
            If S = Null Then Return
            If fieldId = "name"           Then S\Name$ = value         : Return
            If fieldId = "description"    Then S\Description$ = value  : Return
            If fieldId = "recharge_ms"    Then S\RechargeTime = Composer::parseIntClamped(self, value, S\RechargeTime, 0, 3600000) : Return
            If fieldId = "thumb_tex"      Then S\ThumbnailTexID = Composer::parseIntClamped(self, value, S\ThumbnailTexID, 0, 65535) : Return
            If fieldId = "script"         Then S\Script$ = value       : Return
            If fieldId = "smethod"        Then S\SMethod$ = value      : Return
            If fieldId = "race"           Then S\ExclusiveRace$ = value  : Return
            If fieldId = "class"          Then S\ExclusiveClass$ = value : Return
        EndIf

        // ---- ITEM -----------------------------------------------------------
        If kind = "item"
            If refID < 0 Or refID > 65534 Then Return
            Local I.Item = ItemList(refID)
            If I = Null Then Return
            If fieldId = "name"           Then I\Name$ = value         : Return
            If fieldId = "value"          Then I\Value = Composer::parseIntClamped(self, value, I\Value, 0, 2000000000) : Return
            If fieldId = "mass"           Then I\Mass  = Composer::parseIntClamped(self, value, I\Mass,  0, 2000000000) : Return
            If fieldId = "weapon_damage"  Then I\WeaponDamage = Composer::parseIntClamped(self, value, I\WeaponDamage, 0, 2000000000) : Return
            If fieldId = "armour_level"   Then I\ArmourLevel  = Composer::parseIntClamped(self, value, I\ArmourLevel,  0, 2000000000) : Return
            If fieldId = "range"          Then I\Range#       = Composer::parseFloatClamped(self, value, I\Range#, 0.0, 100000.0) : Return
            If fieldId = "script"         Then I\Script$ = value       : Return
            If fieldId = "smethod"        Then I\SMethod$ = value      : Return
            If fieldId = "race"           Then I\ExclusiveRace$ = value  : Return
            If fieldId = "class"          Then I\ExclusiveClass$ = value : Return
            If fieldId = "stackable"      Then I\Stackable   = (value = "1") : Return
            If fieldId = "breakable"      Then I\TakesDamage = (value = "1") : Return
            If fieldId = "weapon_dmg_type" Then I\WeaponDamageType = Composer::parseIntClamped(self, value, I\WeaponDamageType, 0, 19) : Return
            If fieldId = "weapon_type"    Then I\WeaponType   = Composer::parseIntClamped(self, value, I\WeaponType, 0, 255) : Return
            If fieldId = "ranged_proj"    Then I\RangedProjectile = Composer::parseIntClamped(self, value, I\RangedProjectile, 0, 65535) : Return
            If fieldId = "ranged_anim"    Then I\RangedAnimation$ = value : Return
            If fieldId = "eat_length"     Then I\EatEffectsLength = Composer::parseIntClamped(self, value, I\EatEffectsLength, 0, 3600000) : Return
            If fieldId = "thumb_tex"      Then I\ThumbnailTexID = Composer::parseIntClamped(self, value, I\ThumbnailTexID, 0, 65535) : Return
            If fieldId = "m_mesh"         Then I\MMeshID      = Composer::parseIntClamped(self, value, I\MMeshID, 0, 65535) : Return
            If fieldId = "f_mesh"         Then I\FMeshID      = Composer::parseIntClamped(self, value, I\FMeshID, 0, 65535) : Return
            If fieldId = "image_id"       Then I\ImageID      = Composer::parseIntClamped(self, value, I\ImageID, 0, 65535) : Return
            If fieldId = "misc_data"      Then I\MiscData$    = value : Return
            // Gubbins -- 5 equip-slot activation flags, fieldId "gubbin_<i>"
            If Left$(fieldId, 7) = "gubbin_"
                Local giI% = Int(Mid$(fieldId, 8))
                If giI >= 0 And giI <= 4 Then I\Gubbins[giI] = (value = "1")
                Return
            EndIf
            // Attributes table -- same pattern as Actor; Items have
            // Attributes for equipped-bonus / consumable-effect encoding.
            If Left$(fieldId, 16) = "attribute_value_"
                Local aiIV% = Int(Mid$(fieldId, 17))
                If aiIV >= 0 And aiIV <= 39 And I\Attributes <> Null
                    I\Attributes\Value[aiIV] = Composer::parseIntClamped(self, value, I\Attributes\Value[aiIV], -2000000000, 2000000000)
                EndIf
                Return
            EndIf
            If Left$(fieldId, 14) = "attribute_max_"
                Local aiIM% = Int(Mid$(fieldId, 15))
                If aiIM >= 0 And aiIM <= 39 And I\Attributes <> Null
                    I\Attributes\Maximum[aiIM] = Composer::parseIntClamped(self, value, I\Attributes\Maximum[aiIM], -2000000000, 2000000000)
                EndIf
                Return
            EndIf
        EndIf

        // ---- ACTOR ----------------------------------------------------------
        If kind = "actor"
            If refID < 0 Or refID > 65535 Then Return
            Local A.Actor = ActorList(refID)
            If A = Null Then Return
            If fieldId = "race"           Then A\Race$ = value         : Return
            If fieldId = "class"          Then A\Class$ = value        : Return
            If fieldId = "description"    Then A\Description$ = value  : Return
            If fieldId = "scale"          Then A\Scale#      = Composer::parseFloatClamped(self, value, A\Scale#,      0.01, 100.0) : Return
            If fieldId = "xpmult"         Then A\XPMultiplier = Composer::parseIntClamped(self, value, A\XPMultiplier, 0, 1000000) : Return
            If fieldId = "aggressiveness" Then A\Aggressiveness  = Composer::parseIntClamped(self, value, A\Aggressiveness,  0, 3) : Return
            If fieldId = "agg_range"      Then A\AggressiveRange = Composer::parseIntClamped(self, value, A\AggressiveRange, 0, 100000) : Return
            If fieldId = "genders"        Then A\Genders         = Composer::parseIntClamped(self, value, A\Genders,         0, 3) : Return
            If fieldId = "trade_mode"     Then A\TradeMode       = Composer::parseIntClamped(self, value, A\TradeMode,       0, 2) : Return
            If fieldId = "playable"       Then A\Playable    = (value = "1") : Return
            If fieldId = "rideable"       Then A\Rideable    = (value = "1") : Return
            If fieldId = "poly_collision" Then A\PolyCollision = (value = "1") : Return
            If fieldId = "radius"         Then A\Radius#     = Composer::parseFloatClamped(self, value, A\Radius#, 0.0, 10000.0) : Return
            If fieldId = "environment"    Then A\Environment = Composer::parseIntClamped(self, value, A\Environment, 0, 255) : Return
            If fieldId = "inv_slots"      Then A\InventorySlots = Composer::parseIntClamped(self, value, A\InventorySlots, 0, 65535) : Return
            If fieldId = "default_dmg"    Then A\DefaultDamageType = Composer::parseIntClamped(self, value, A\DefaultDamageType, 0, 19) : Return
            If fieldId = "start_area"     Then A\StartArea$ = value : Return
            If fieldId = "start_portal"   Then A\StartPortal$ = value : Return
            // Reference fields edited via the palette picker. The picker
            // writes the chosen entity's refID as a string; clamp to the
            // valid slot range for the kind.
            If fieldId = "default_faction" Then A\DefaultFaction = Composer::parseIntClamped(self, value, A\DefaultFaction, 0, 99)   : Return
            If fieldId = "manim_set"       Then A\MAnimationSet  = Composer::parseIntClamped(self, value, A\MAnimationSet,  0, 999)  : Return
            If fieldId = "fanim_set"       Then A\FAnimationSet  = Composer::parseIntClamped(self, value, A\FAnimationSet,  0, 999)  : Return

            // Attributes table -- fieldId pattern "attribute_value_<i>" /
            // "attribute_max_<i>" with i in 0..39.
            If Left$(fieldId, 16) = "attribute_value_"
                Local aiV% = Int(Mid$(fieldId, 17))
                If aiV >= 0 And aiV <= 39 And A\Attributes <> Null
                    A\Attributes\Value[aiV] = Composer::parseIntClamped(self, value, A\Attributes\Value[aiV], -2000000000, 2000000000)
                EndIf
                Return
            EndIf
            If Left$(fieldId, 14) = "attribute_max_"
                Local aiM% = Int(Mid$(fieldId, 15))
                If aiM >= 0 And aiM <= 39 And A\Attributes <> Null
                    A\Attributes\Maximum[aiM] = Composer::parseIntClamped(self, value, A\Attributes\Maximum[aiM], -2000000000, 2000000000)
                EndIf
                Return
            EndIf

            // Resistances -- fieldId pattern "resistance_<i>" with i in 0..19.
            If Left$(fieldId, 11) = "resistance_"
                Local riR% = Int(Mid$(fieldId, 12))
                If riR >= 0 And riR <= 19
                    A\Resistances[riR] = Composer::parseIntClamped(self, value, A\Resistances[riR], -10000, 10000)
                EndIf
                Return
            EndIf

            // Appearance arrays: mesh_<i> (0..7), beard_<i> (0..4),
            // mhair_<i> / fhair_<i> (0..4), mface_<i> / fface_<i> (0..4),
            // mbody_<i> / fbody_<i> (0..4), haircol_<i> (0..15),
            // mspeech_<i> / fspeech_<i> (0..15), plus flat blood_tex.
            // All clamp to 0..65535 (texture/mesh/sound IDs).
            If fieldId = "blood_tex" Then A\BloodTexID = Composer::parseIntClamped(self, value, A\BloodTexID, 0, 65535) : Return

            If Left$(fieldId, 5) = "mesh_"
                Local mIdx% = Int(Mid$(fieldId, 6))
                If mIdx >= 0 And mIdx <= 7 Then A\MeshIDs[mIdx] = Composer::parseIntClamped(self, value, A\MeshIDs[mIdx], 0, 65535)
                Return
            EndIf
            If Left$(fieldId, 6) = "beard_"
                Local bdIdx% = Int(Mid$(fieldId, 7))
                If bdIdx >= 0 And bdIdx <= 4 Then A\BeardIDs[bdIdx] = Composer::parseIntClamped(self, value, A\BeardIDs[bdIdx], 0, 65535)
                Return
            EndIf
            If Left$(fieldId, 6) = "mhair_"
                Local mhIdx% = Int(Mid$(fieldId, 7))
                If mhIdx >= 0 And mhIdx <= 4 Then A\MaleHairIDs[mhIdx] = Composer::parseIntClamped(self, value, A\MaleHairIDs[mhIdx], 0, 65535)
                Return
            EndIf
            If Left$(fieldId, 6) = "fhair_"
                Local fhIdx% = Int(Mid$(fieldId, 7))
                If fhIdx >= 0 And fhIdx <= 4 Then A\FemaleHairIDs[fhIdx] = Composer::parseIntClamped(self, value, A\FemaleHairIDs[fhIdx], 0, 65535)
                Return
            EndIf
            If Left$(fieldId, 6) = "mface_"
                Local mfIdx% = Int(Mid$(fieldId, 7))
                If mfIdx >= 0 And mfIdx <= 4 Then A\MaleFaceIDs[mfIdx] = Composer::parseIntClamped(self, value, A\MaleFaceIDs[mfIdx], 0, 65535)
                Return
            EndIf
            If Left$(fieldId, 6) = "fface_"
                Local ffIdx% = Int(Mid$(fieldId, 7))
                If ffIdx >= 0 And ffIdx <= 4 Then A\FemaleFaceIDs[ffIdx] = Composer::parseIntClamped(self, value, A\FemaleFaceIDs[ffIdx], 0, 65535)
                Return
            EndIf
            If Left$(fieldId, 6) = "mbody_"
                Local mbIdx% = Int(Mid$(fieldId, 7))
                If mbIdx >= 0 And mbIdx <= 4 Then A\MaleBodyIDs[mbIdx] = Composer::parseIntClamped(self, value, A\MaleBodyIDs[mbIdx], 0, 65535)
                Return
            EndIf
            If Left$(fieldId, 6) = "fbody_"
                Local fbIdx% = Int(Mid$(fieldId, 7))
                If fbIdx >= 0 And fbIdx <= 4 Then A\FemaleBodyIDs[fbIdx] = Composer::parseIntClamped(self, value, A\FemaleBodyIDs[fbIdx], 0, 65535)
                Return
            EndIf
            If Left$(fieldId, 8) = "haircol_"
                Local hcIdx% = Int(Mid$(fieldId, 9))
                If hcIdx >= 0 And hcIdx <= 15 Then A\HairColours[hcIdx] = Composer::parseIntClamped(self, value, A\HairColours[hcIdx], -2147483647, 2147483647)
                Return
            EndIf
            If Left$(fieldId, 8) = "mspeech_"
                Local msIdx% = Int(Mid$(fieldId, 9))
                If msIdx >= 0 And msIdx <= 15 Then A\MSpeechIDs[msIdx] = Composer::parseIntClamped(self, value, A\MSpeechIDs[msIdx], 0, 65535)
                Return
            EndIf
            If Left$(fieldId, 8) = "fspeech_"
                Local fsIdx% = Int(Mid$(fieldId, 9))
                If fsIdx >= 0 And fsIdx <= 15 Then A\FSpeechIDs[fsIdx] = Composer::parseIntClamped(self, value, A\FSpeechIDs[fsIdx], 0, 65535)
                Return
            EndIf
        EndIf

        // ---- ZONE -----------------------------------------------------------
        If kind = "zone"
            Local Ar.Area = Object.Area(refID)
            If Ar = Null Then Return
            If fieldId = "name"           Then Ar\Name$ = value          : Return
            If fieldId = "gravity"        Then Ar\Gravity = Composer::parseIntClamped(self, value, Ar\Gravity, 0, 1000) : Return
            If fieldId = "entry_script"   Then Ar\EntryScript$ = value   : Return
            If fieldId = "exit_script"    Then Ar\ExitScript$ = value    : Return
            If fieldId = "weather_link"   Then Ar\WeatherLink$ = value   : Return
            If fieldId = "outdoors"       Then Ar\Outdoors = (value = "1") : Return
            If fieldId = "pvp"            Then Ar\PvP      = (value = "1") : Return

            // Weather chances -- 4 slots
            If Left$(fieldId, 8) = "weather_"
                Local wIdx% = Int(Mid$(fieldId, 9))
                If wIdx >= 0 And wIdx <= 3 Then Ar\WeatherChance[wIdx] = Composer::parseIntClamped(self, value, Ar\WeatherChance[wIdx], 0, 1000)
                Return
            EndIf

            // Portal coords + name -- specific prefixes MUST come before
            // the generic "portal_" link-target catch below.
            If Left$(fieldId, 12) = "portal_name_"
                Local pnIdx% = Int(Mid$(fieldId, 13))
                If pnIdx >= 0 And pnIdx <= 99 Then Ar\PortalName$[pnIdx] = value
                Return
            EndIf
            If Left$(fieldId, 9) = "portal_x_"
                Local pxIdx% = Int(Mid$(fieldId, 10))
                If pxIdx >= 0 And pxIdx <= 99 Then Ar\PortalX#[pxIdx] = Composer::parseFloatClamped(self, value, Ar\PortalX#[pxIdx], -1000000.0, 1000000.0)
                Return
            EndIf
            If Left$(fieldId, 9) = "portal_y_"
                Local pyIdx% = Int(Mid$(fieldId, 10))
                If pyIdx >= 0 And pyIdx <= 99 Then Ar\PortalY#[pyIdx] = Composer::parseFloatClamped(self, value, Ar\PortalY#[pyIdx], -1000000.0, 1000000.0)
                Return
            EndIf
            If Left$(fieldId, 9) = "portal_z_"
                Local pzIdx% = Int(Mid$(fieldId, 10))
                If pzIdx >= 0 And pzIdx <= 99 Then Ar\PortalZ#[pzIdx] = Composer::parseFloatClamped(self, value, Ar\PortalZ#[pzIdx], -1000000.0, 1000000.0)
                Return
            EndIf
            If Left$(fieldId, 12) = "portal_size_"
                Local psIdx% = Int(Mid$(fieldId, 13))
                If psIdx >= 0 And psIdx <= 99 Then Ar\PortalSize#[psIdx] = Composer::parseFloatClamped(self, value, Ar\PortalSize#[psIdx], 0.0, 10000.0)
                Return
            EndIf
            If Left$(fieldId, 11) = "portal_yaw_"
                Local pyaIdx% = Int(Mid$(fieldId, 12))
                If pyaIdx >= 0 And pyaIdx <= 99 Then Ar\PortalYaw#[pyaIdx] = Composer::parseFloatClamped(self, value, Ar\PortalYaw#[pyaIdx], -360.0, 360.0)
                Return
            EndIf
            // Portal-target reference field (link): editFieldId = "portal_<i>".
            // The picker passes the chosen zone's Name as the value (the
            // portal stores by name, not handle). Generic prefix MUST come
            // after the specific portal_x_ / portal_y_ etc. above so they
            // get first crack.
            If Left$(fieldId, 7) = "portal_"
                Local portIdx% = Int(Mid$(fieldId, 8))
                If portIdx >= 0 And portIdx <= 99 Then Ar\PortalLinkArea$[portIdx] = value
                Return
            EndIf

            // Triggers -- 150 slots
            If Left$(fieldId, 10) = "trigger_x_"
                Local txIdx% = Int(Mid$(fieldId, 11))
                If txIdx >= 0 And txIdx <= 149 Then Ar\TriggerX#[txIdx] = Composer::parseFloatClamped(self, value, Ar\TriggerX#[txIdx], -1000000.0, 1000000.0)
                Return
            EndIf
            If Left$(fieldId, 10) = "trigger_y_"
                Local tyIdx% = Int(Mid$(fieldId, 11))
                If tyIdx >= 0 And tyIdx <= 149 Then Ar\TriggerY#[tyIdx] = Composer::parseFloatClamped(self, value, Ar\TriggerY#[tyIdx], -1000000.0, 1000000.0)
                Return
            EndIf
            If Left$(fieldId, 10) = "trigger_z_"
                Local tzIdx% = Int(Mid$(fieldId, 11))
                If tzIdx >= 0 And tzIdx <= 149 Then Ar\TriggerZ#[tzIdx] = Composer::parseFloatClamped(self, value, Ar\TriggerZ#[tzIdx], -1000000.0, 1000000.0)
                Return
            EndIf
            If Left$(fieldId, 13) = "trigger_size_"
                Local tsIdx% = Int(Mid$(fieldId, 14))
                If tsIdx >= 0 And tsIdx <= 149 Then Ar\TriggerSize#[tsIdx] = Composer::parseFloatClamped(self, value, Ar\TriggerSize#[tsIdx], 0.0, 10000.0)
                Return
            EndIf
            If Left$(fieldId, 15) = "trigger_script_"
                Local tscIdx% = Int(Mid$(fieldId, 16))
                If tscIdx >= 0 And tscIdx <= 149 Then Ar\TriggerScript$[tscIdx] = value
                Return
            EndIf
            If Left$(fieldId, 15) = "trigger_method_"
                Local tmIdx% = Int(Mid$(fieldId, 16))
                If tmIdx >= 0 And tmIdx <= 149 Then Ar\TriggerMethod$[tmIdx] = value
                Return
            EndIf

            // Spawns -- 1000 slots, 9 fields each
            If Left$(fieldId, 12) = "spawn_actor_"
                Local saIdx% = Int(Mid$(fieldId, 13))
                If saIdx >= 0 And saIdx <= 999 Then Ar\SpawnActor[saIdx] = Composer::parseIntClamped(self, value, Ar\SpawnActor[saIdx], 0, 65535)
                Return
            EndIf
            If Left$(fieldId, 15) = "spawn_waypoint_"
                Local swIdx% = Int(Mid$(fieldId, 16))
                If swIdx >= 0 And swIdx <= 999 Then Ar\SpawnWaypoint[swIdx] = Composer::parseIntClamped(self, value, Ar\SpawnWaypoint[swIdx], 0, 1999)
                Return
            EndIf
            If Left$(fieldId, 11) = "spawn_size_"
                Local ssIdx% = Int(Mid$(fieldId, 12))
                If ssIdx >= 0 And ssIdx <= 999 Then Ar\SpawnSize#[ssIdx] = Composer::parseFloatClamped(self, value, Ar\SpawnSize#[ssIdx], 0.0, 100000.0)
                Return
            EndIf
            If Left$(fieldId, 12) = "spawn_range_"
                Local srIdx% = Int(Mid$(fieldId, 13))
                If srIdx >= 0 And srIdx <= 999 Then Ar\SpawnRange#[srIdx] = Composer::parseFloatClamped(self, value, Ar\SpawnRange#[srIdx], 0.0, 100000.0)
                Return
            EndIf
            If Left$(fieldId, 11) = "spawn_freq_"
                Local sfIdx% = Int(Mid$(fieldId, 12))
                If sfIdx >= 0 And sfIdx <= 999 Then Ar\SpawnFrequency[sfIdx] = Composer::parseIntClamped(self, value, Ar\SpawnFrequency[sfIdx], 0, 3600000)
                Return
            EndIf
            If Left$(fieldId, 10) = "spawn_max_"
                Local smIdx% = Int(Mid$(fieldId, 11))
                If smIdx >= 0 And smIdx <= 999 Then Ar\SpawnMax[smIdx] = Composer::parseIntClamped(self, value, Ar\SpawnMax[smIdx], 0, 65535)
                Return
            EndIf
            If Left$(fieldId, 13) = "spawn_script_"
                Local sscIdx% = Int(Mid$(fieldId, 14))
                If sscIdx >= 0 And sscIdx <= 999 Then Ar\SpawnScript$[sscIdx] = value
                Return
            EndIf
            If Left$(fieldId, 14) = "spawn_ascript_"
                Local sasIdx% = Int(Mid$(fieldId, 15))
                If sasIdx >= 0 And sasIdx <= 999 Then Ar\SpawnActorScript$[sasIdx] = value
                Return
            EndIf
            If Left$(fieldId, 14) = "spawn_dscript_"
                Local sdsIdx% = Int(Mid$(fieldId, 15))
                If sdsIdx >= 0 And sdsIdx <= 999 Then Ar\SpawnDeathScript$[sdsIdx] = value
                Return
            EndIf
        EndIf

        // ---- FACTION --------------------------------------------------------
        If kind = "faction"
            If refID < 0 Or refID > 99 Then Return
            // SetFactionName lives in Actors.bb (non-Strict) -- direct write
            // to the FactionNames$ global from this Strict file would error
            // per the Dim-inside-Method gotcha. Same shape applies to
            // SetFactionRelation for the 100x100 FactionDefaultRatings grid.
            If fieldId = "name" Then SetFactionName(refID, value) : Return
            // Relations -- fieldId pattern "rel_<j>" where j is the target
            // faction index. Clamps 0..255 (byte storage).
            If Left$(fieldId, 4) = "rel_"
                Local relJ% = Int(Mid$(fieldId, 5))
                Local relV% = Composer::parseIntClamped(self, value, FactionDefaultRatings(refID, relJ), 0, 255)
                SetFactionRelation(refID, relJ, relV)
                Return
            EndIf
        EndIf

        // ---- SETTINGS (project config singleton) ----------------------------
        // Globals can't be written from inside a Strict Method (per the
        // BlitzForge Dim-write trap), so every assignment goes through a
        // setter defined in the non-Strict Settings.bb module.
        If kind = "settings"
            If fieldId = "game_name"      Then LoomSettings_SetGameName$(value)        : Return
            If fieldId = "update_game"    Then LoomSettings_SetUpdateGame$(value)      : Return
            If fieldId = "update_music"   Then LoomSettings_SetUpdateMusic$(value)     : Return
            If fieldId = "server_host"    Then LoomSettings_SetServerHost$(value)      : Return
            If fieldId = "update_host"    Then LoomSettings_SetUpdateHost$(value)      : Return
            If fieldId = "server_port"    Then LoomSettings_SetServerPort(Composer::parseIntClamped(self, value, LoomCfg_ServerPort, 1, 65535))        : Return
            If fieldId = "hide_nametags"  Then LoomSettings_SetHideNametags(value = "1")  : Return
            If fieldId = "disable_collisions" Then LoomSettings_SetDisableCollisions(value = "1") : Return
            If fieldId = "view_mode"      Then LoomSettings_SetViewMode(Composer::parseIntClamped(self, value, LoomCfg_ViewMode, 0, 10))               : Return
            If fieldId = "require_memo"   Then LoomSettings_SetRequireMemorise(value = "1") : Return
            If fieldId = "use_bubbles"    Then LoomSettings_SetUseBubbles(value = "1") : Return
            If fieldId = "bubbles_r"      Then LoomSettings_SetBubblesR(Composer::parseIntClamped(self, value, LoomCfg_BubblesR, 0, 255))              : Return
            If fieldId = "bubbles_g"      Then LoomSettings_SetBubblesG(Composer::parseIntClamped(self, value, LoomCfg_BubblesG, 0, 255))              : Return
            If fieldId = "bubbles_b"      Then LoomSettings_SetBubblesB(Composer::parseIntClamped(self, value, LoomCfg_BubblesB, 0, 255))              : Return
            If fieldId = "money1_name"    Then LoomSettings_SetMoney1$(value)          : Return
            If fieldId = "money2_name"    Then LoomSettings_SetMoney2$(value)          : Return
            If fieldId = "money2x"        Then LoomSettings_SetMoney2x(Composer::parseIntClamped(self, value, LoomCfg_Money2x, 0, 32767))              : Return
            If fieldId = "money3_name"    Then LoomSettings_SetMoney3$(value)          : Return
            If fieldId = "money3x"        Then LoomSettings_SetMoney3x(Composer::parseIntClamped(self, value, LoomCfg_Money3x, 0, 32767))              : Return
            If fieldId = "money4_name"    Then LoomSettings_SetMoney4$(value)          : Return
            If fieldId = "money4x"        Then LoomSettings_SetMoney4x(Composer::parseIntClamped(self, value, LoomCfg_Money4x, 0, 32767))              : Return

            // Damage types catalog: fieldId = "damage_<i>" (i in 0..19)
            If Left$(fieldId, 7) = "damage_"
                Local dIdx% = Int(Mid$(fieldId, 8))
                SetDamageTypeName(dIdx, value)
                Return
            EndIf

            // Attribute catalog: fieldId = "attr_assignment" |
            // "attr_name_<i>" | "attr_skill_<i>" | "attr_hidden_<i>" (i in 0..39)
            If fieldId = "attr_assignment"
                SetAttributeAssignment(Composer::parseIntClamped(self, value, AttributeAssignment, 0, 10))
                Return
            EndIf
            If Left$(fieldId, 10) = "attr_name_"
                Local anIdx% = Int(Mid$(fieldId, 11))
                SetAttributeName(anIdx, value)
                Return
            EndIf
            If Left$(fieldId, 11) = "attr_skill_"
                Local askIdx% = Int(Mid$(fieldId, 12))
                SetAttributeIsSkill(askIdx, (value = "1"))
                Return
            EndIf
            If Left$(fieldId, 12) = "attr_hidden_"
                Local ahIdx% = Int(Mid$(fieldId, 13))
                SetAttributeHidden(ahIdx, (value = "1"))
                Return
            EndIf
        EndIf

        // ---- ANIMSET --------------------------------------------------------
        If kind = "animset"
            // AnimSet is iterated, not array-indexed; walk to the matching ID.
            Local As2.AnimSet
            For As2 = Each AnimSet
                If As2\ID = refID
                    If fieldId = "name" Then As2\Name$ = value : Return

                    // Per-clip fields. AnimSet has 150 slots (0..149); each
                    // has Name/Start/End/Speed. fieldId encodes the slot
                    // index. Prefix order matters: clip_speed_ before any
                    // shorter clip_ prefix would catch it.
                    If Left$(fieldId, 10) = "clip_name_"
                        Local cnIdx% = Int(Mid$(fieldId, 11))
                        If cnIdx >= 0 And cnIdx <= 149 Then As2\AnimName$[cnIdx] = value
                        Return
                    EndIf
                    If Left$(fieldId, 11) = "clip_start_"
                        Local csIdx% = Int(Mid$(fieldId, 12))
                        If csIdx >= 0 And csIdx <= 149 Then As2\AnimStart[csIdx] = Composer::parseIntClamped(self, value, As2\AnimStart[csIdx], 0, 100000)
                        Return
                    EndIf
                    If Left$(fieldId, 9) = "clip_end_"
                        Local ceIdx% = Int(Mid$(fieldId, 10))
                        If ceIdx >= 0 And ceIdx <= 149 Then As2\AnimEnd[ceIdx] = Composer::parseIntClamped(self, value, As2\AnimEnd[ceIdx], 0, 100000)
                        Return
                    EndIf
                    If Left$(fieldId, 11) = "clip_speed_"
                        Local cspIdx% = Int(Mid$(fieldId, 12))
                        If cspIdx >= 0 And cspIdx <= 149 Then As2\AnimSpeed#[cspIdx] = Composer::parseFloatClamped(self, value, As2\AnimSpeed#[cspIdx], -100.0, 100.0)
                        Return
                    EndIf
                    Exit
                EndIf
            Next
        EndIf

        WriteLog(LoomLog, "Composer: writeField -- no handler for " + kind + "." + fieldId)
    End Method


    // -------------------------------------------------------------------------
    // parseIntClamped / parseFloatClamped -- numeric parsers used by
    // writeField for editable int / float fields. Empty input returns the
    // fallback (cancels the edit); otherwise BlitzForge's Int() / Float()
    // handles parsing -- they return 0 for pure-garbage strings. The result
    // is clamped into [lo, hi] so an editing typo can't blow a field out to
    // range-breaking values.
    //
    // Rationale for skipping a strict "is this digits" pre-check: the
    // Strict-mode "reassigning a Method-scope Local from inside nested
    // If/For blocks" trap (architecture.md "Known BlitzForge gotchas") makes
    // a character-class loop awkward, and the cost of accepting "abc" -> 0
    // -> clamp-to-lo is minimal -- the user sees the wrong value and
    // re-edits. The clamp is the real protection here, not the validator.
    // -------------------------------------------------------------------------
    // Thin delegates to the pure, unit-tested helpers in Clamp.bb. Kept as
    // methods so the ~80 Composer::parseIntClamped(self, ...) call sites are
    // unchanged; the clamping contract is pinned by ClampTest.bb.
    Method parseIntClamped%(s$, fallback%, lo%, hi%)
        Return Loom_ParseIntClamped(s, fallback, lo, hi)
    End Method


    Method parseFloatClamped#(s$, fallback#, lo#, hi#)
        Return Loom_ParseFloatClamped(s, fallback, lo, hi)
    End Method


    // -------------------------------------------------------------------------
    // isDirtyForKind -- read the per-kind *Saved global, return True if
    // there are unsaved edits.
    // -------------------------------------------------------------------------
    Method isDirtyForKind%(kind$)
        If kind = "spell"   Then Return Not SpellsSaved
        If kind = "item"    Then Return Not ItemsSaved
        If kind = "actor"   Then Return Not ActorsSaved
        If kind = "faction" Then Return Not FactionsSaved
        If kind = "zone"    Then Return Not ZoneSaved
        If kind = "animset" Then Return Not AnimsSaved
        Return False
    End Method


    // -------------------------------------------------------------------------
    // markDirtyForKind -- set the per-kind *Saved global to False after
    // an in-memory edit.
    // -------------------------------------------------------------------------
    Method markDirtyForKind(kind$)
        If kind = "spell"    Then SpellsSaved = False
        If kind = "item"     Then ItemsSaved = False
        If kind = "actor"    Then ActorsSaved = False
        If kind = "faction"  Then FactionsSaved = False
        If kind = "zone"     Then ZoneSaved = False
        If kind = "animset"  Then AnimsSaved = False
        If kind = "settings" Then SettingsSaved = False
    End Method


    // -------------------------------------------------------------------------
    // scrollToZoneSubEntity -- called from the ZoneViewport when a marker
    // is clicked. Looks up the previously-captured Y anchor for the
    // (kind, index) sub-entity and sets scrollOffset so that anchor
    // becomes the top of the visible body region.
    //
    // Returns True if it scrolled, False if the anchor wasn't recorded
    // yet (zone not currently focused, or the slot was empty last frame).
    // The viewport's pick filters by source-data defined-ness before
    // calling here, so a False return mostly means "you haven't rendered
    // this zone yet this session".
    // -------------------------------------------------------------------------
    Method scrollToZoneSubEntity%(kind$, idx%)
        // Resolve anchor via per-kind helper to dodge the Strict-mode
        // "reassign Local from nested If" trap.
        Local anchor% = Composer::zoneSubEntityAnchor(self, kind, idx)
        If anchor < 0 Then Return False

        // Set scrollOffset so anchor lands at body top. Subtract bodyTop
        // since y values in the render are body-relative.
        Local target% = anchor - self\bodyTop - 8
        If target < 0 Then target = 0
        // Clamp to content bottom so we don't scroll past the end
        Local maxScroll% = self\lastContentBottom - self\bodyBottom
        If maxScroll < 0 Then maxScroll = 0
        If target > maxScroll Then target = maxScroll
        self\scrollOffset = target
        Return True
    End Method


    Method zoneSubEntityAnchor%(kind$, idx%)
        If kind = "portal" And idx >= 0 And idx <= 99 Then Return self\zoneAnchorPortal[idx]
        If kind = "trigger" And idx >= 0 And idx <= 149 Then Return self\zoneAnchorTrigger[idx]
        If kind = "spawn" And idx >= 0 And idx <= 999 Then Return self\zoneAnchorSpawn[idx]
        Return -1
    End Method


    // -------------------------------------------------------------------------
    // commitSaveForKind -- persist in-memory state for the given kind to
    // disk via GUE's existing Save* serializers, then clear the dirty flag.
    //
    // Zone is the special case: each Area has its own .dat file (Areas/<name>.dat)
    // so ServerSaveArea takes an Area instance instead of a file path. We
    // save only the focused zone, not the entire collection.
    // -------------------------------------------------------------------------
    Method commitSaveForKind(kind$)
        If kind = "spell"
            Local okS% = SaveSpells("Data\Server Data\Spells.dat")
            If okS = False
                WriteLog(LoomLog, "Composer: SaveSpells FAILED")
                Toast_Show("Save Spells FAILED", "danger")
                Return
            EndIf
            SpellsSaved = True
            WriteLog(LoomLog, "Composer: saved Spells.dat")
            Toast_Show("Saved Spells.dat", "success")
            Return
        EndIf

        If kind = "item"
            Local okI% = SaveItems("Data\Server Data\Items.dat")
            If okI = False
                WriteLog(LoomLog, "Composer: SaveItems FAILED")
                Toast_Show("Save Items FAILED", "danger")
                Return
            EndIf
            ItemsSaved = True
            WriteLog(LoomLog, "Composer: saved Items.dat")
            Toast_Show("Saved Items.dat", "success")
            Return
        EndIf

        If kind = "actor"
            Local okA% = SaveActors("Data\Server Data\Actors.dat")
            If okA = False
                WriteLog(LoomLog, "Composer: SaveActors FAILED")
                Toast_Show("Save Actors FAILED", "danger")
                Return
            EndIf
            ActorsSaved = True
            WriteLog(LoomLog, "Composer: saved Actors.dat")
            Toast_Show("Saved Actors.dat", "success")
            Return
        EndIf

        If kind = "faction"
            Local okF% = SaveFactions("Data\Server Data\Factions.dat")
            If okF = False
                WriteLog(LoomLog, "Composer: SaveFactions FAILED")
                Toast_Show("Save Factions FAILED", "danger")
                Return
            EndIf
            FactionsSaved = True
            WriteLog(LoomLog, "Composer: saved Factions.dat")
            Toast_Show("Saved Factions.dat", "success")
            Return
        EndIf

        If kind = "animset"
            Local okM% = SaveAnimSets("Data\Game Data\Animations.dat")
            If okM = False
                WriteLog(LoomLog, "Composer: SaveAnimSets FAILED")
                Toast_Show("Save Animations FAILED", "danger")
                Return
            EndIf
            AnimsSaved = True
            WriteLog(LoomLog, "Composer: saved Animations.dat")
            Toast_Show("Saved Animations.dat", "success")
            Return
        EndIf

        If kind = "zone"
            Local Ar.Area = Object.Area(self\threads\focusID)
            If Ar = Null
                WriteLog(LoomLog, "Composer: ServerSaveArea -- focused area handle is stale")
                Toast_Show("Save Zone failed (stale handle)", "danger")
                Return
            EndIf
            ServerSaveArea(Ar)
            // ServerSaveArea is void; we trust it. The atomic-write
            // discipline is owned by the serializer itself.
            ZoneSaved = True
            WriteLog(LoomLog, "Composer: saved zone " + Ar\Name$)
            Toast_Show("Saved zone " + Ar\Name$, "success")
            Return
        EndIf

        If kind = "settings"
            Local okCfg% = Loom_SaveSettings()
            If okCfg = False
                WriteLog(LoomLog, "Composer: Loom_SaveSettings FAILED")
                Toast_Show("Save Settings FAILED", "danger")
                Return
            EndIf
            ; Loom_SaveSettings already flips SettingsSaved = True on success.
            WriteLog(LoomLog, "Composer: saved project Settings")
            Toast_Show("Saved project Settings", "success")
            Return
        EndIf

        WriteLog(LoomLog, "Composer: commitSaveForKind -- no handler for " + kind)
    End Method


    // -------------------------------------------------------------------------
    // drawDeleteButton -- destructive action with arm/confirm. First click
    // arms (button turns red, footer hint updates); second click on the
    // same focus within CMP_DELETE_ARM_MS commits via EntityFactory_Delete.
    // Focus change / other-button click / timeout cancels.
    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // drawDuplicateButton -- non-destructive clone of the focused entity.
    // No arm/confirm: makes a copy, focuses the new entity, marks dirty.
    // Zone duplicate is unsupported (logged + toasted by EntityFactory).
    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // drawCollapseButton -- top-left chevron that flips self\collapsed.
    // Shown only in expanded mode; the collapsed-mode renderer paints
    // its own '<' button.
    //
    // glyph: ">" when expanded (click to collapse rightward), "<" when
    // collapsed (caller passes "<" from renderCollapsed).
    // -------------------------------------------------------------------------
    Method drawCollapseButton(btnX%, btnY%, mx%, my%, clicked%, glyph$)
        Local hovered% = (mx >= btnX And mx < btnX + CMP_COLLAPSE_BTN_W And my >= btnY And my < btnY + CMP_COLLAPSE_BTN_H)

        If hovered = True
            LoomFill(btnX, btnY, CMP_COLLAPSE_BTN_W, CMP_COLLAPSE_BTN_H, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
            LoomBorder(btnX, btnY, CMP_COLLAPSE_BTN_W, CMP_COLLAPSE_BTN_H, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        EndIf
        LoomText(btnX + 6, btnY + 4, glyph, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        If hovered And clicked
            self\collapsed = (Not self\collapsed)
            WriteLog(LoomLog, "Composer: collapsed -> " + Str(self\collapsed))
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // renderCollapsed -- thin-sliver render when self\collapsed = True.
    // Paints just the brass left rule + a chevron '<' to expand. Lets
    // the user see the full browser card grid while the composer keeps
    // its focused entity (so re-expanding returns to the same state).
    // -------------------------------------------------------------------------
    Method renderCollapsed(sw%, sh%, mx%, my%, clicked%)
        Local x% = sw - CMP_COLLAPSED_W
        Local y% = CMP_TOP
        Local w% = CMP_COLLAPSED_W
        Local h% = sh - CMP_TOP - CMP_BOT_PAD

        // Sliver chrome -- subtle gradient + brass left rule like the
        // expanded panel, just narrower.
        LoomGradientV(x, y, w, h, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B)
        LoomBorder(x, y, w, h, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        LoomFill(x, y, 3, h, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        // Expand chevron at top
        Composer::drawCollapseButton(self, x + 6, y + CMP_PAD - 2, mx, my, clicked, "<")

        // Vertical kind label rotated... actually Blitz3D Text doesn't
        // rotate; just paint the kind initial as a stack of letters
        // down the sliver for orientation. e.g. "A" for actor, "I" for
        // item, etc. -- same glyph the chips use, painted vertically.
        Local kind$ = self\threads\focusKind
        Local glyph$ = Composer::kindGlyphCollapsed(self, kind)
        Local labelY% = y + CMP_PAD - 2 + CMP_COLLAPSE_BTN_H + 12
        LoomText(x + 8, labelY, glyph, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
    End Method


    Method kindGlyphCollapsed$(kind$)
        If kind = "actor"   Then Return "A"
        If kind = "item"    Then Return "I"
        If kind = "spell"   Then Return "S"
        If kind = "zone"    Then Return "Z"
        If kind = "faction" Then Return "F"
        If kind = "animset" Then Return "M"
        Return "?"
    End Method


    // -------------------------------------------------------------------------
    // renderBulkEdit -- the panel shown when Browser has a non-empty
    // selection set and no single entity is focused.
    //
    // Shows the selected entities as a scrollable list + a "Bulk Delete"
    // button with arm/confirm. Per-field broadcast edits are the
    // follow-up iteration; this lands the visible bulk-mode panel
    // shape + the most-asked-for bulk action (delete-many).
    //
    // Mouse wheel + scrollbar reuse the same machinery as the focused
    // composer body via self\scrollOffset / bodyTop / bodyBottom /
    // canPaintRow / recordContentBottom.
    // -------------------------------------------------------------------------
    Method renderBulkEdit(sw%, sh%)
        // Pump keyboard for in-progress bulk-field input first so the
        // typed buffer is up-to-date when the Apply button reads it.
        If self\bulkEditField <> "" Then Composer::pumpBulkKeyboard(self)

        // Mouse wheel scroll -- same shape (and same cursor-region gate +
        // consume) as the focused-entity composer. Loom_MouseWheel() is a
        // true per-frame delta; gate to the panel so the browser grid
        // behind it doesn't scroll too.
        Local wheelTicks% = Loom_MouseWheel()
        If wheelTicks <> 0 And MouseX() >= (sw - CMP_W)
            self\scrollOffset = self\scrollOffset - wheelTicks * CMP_SCROLL_STEP
            If self\scrollOffset < 0 Then self\scrollOffset = 0
            Local maxScroll% = self\lastContentBottom - self\bodyBottom
            If maxScroll < 0 Then maxScroll = 0
            If self\scrollOffset > maxScroll Then self\scrollOffset = maxScroll
            Loom_ConsumeWheel()
        EndIf

        Local mx% = MouseX()
        Local my% = MouseY()
        Local clicked% = Loom_MouseClicked()

        Local x% = sw - CMP_W
        Local y% = CMP_TOP
        Local w% = CMP_W
        Local h% = sh - CMP_TOP - CMP_BOT_PAD

        // Panel chrome -- same gradient + brass left rule as focused
        // composer for visual continuity; users feel they're in the
        // "same panel" with a different mode.
        LoomGradientV(x, y, w, h, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B)
        LoomBorder(x, y, w, h, LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B)
        LoomFill(x, y, 3, h, LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B)

        // Title block -- display font, warning color to mirror the
        // selection's warning-orange visual language.
        LoomTheme_UseDisplay()
        LoomText(x + CMP_PAD, y + CMP_PAD, "BULK EDIT", LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B)
        LoomTheme_UseBody()
        Local count% = Browser::getSelectionCount(self\browser)
        LoomText(x + CMP_PAD, y + CMP_PAD + 22, Str(count) + " selected  |  kinds: " + Composer::bulkSelectionKindSummary(self), LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        // Bulk Delete button (top-right). Same arm/confirm shape as
        // single-entity delete.
        Composer::drawBulkDeleteButton(self, x + w - CMP_DISCARD_BTN_W - CMP_PAD, y + CMP_PAD - 2, mx, my, clicked)

        LoomHRule(x + CMP_PAD, y + CMP_PAD + 44, w - CMP_PAD * 2, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)

        // Body -- list of selected entities, scrollable
        Local bodyY% = y + CMP_PAD + 56
        Local bodyH% = h - (bodyY - y) - 24
        self\bodyTop = bodyY
        self\bodyBottom = bodyY + bodyH
        Local rowY% = bodyY - self\scrollOffset

        // ---- Apply-to-all section (homogeneous selections only) -------------
        // When every selected entity is the same kind we can offer a
        // small set of broadcast-editable fields. Heterogeneous
        // selections only get the entity list + delete -- mixed-kind
        // fields don't share enough semantics to bulk-edit safely.
        Local homKind$ = Composer::homogeneousSelectionKind(self)
        If homKind <> ""
            rowY = Composer::sectionHeader(self, x, w, rowY, "Apply to all (" + homKind + ")")
            rowY = Composer::renderBulkFieldsFor(self, x, w, rowY, homKind, mx, my, clicked)
            rowY = rowY + 6
        EndIf

        rowY = Composer::sectionHeader(self, x, w, rowY, "Selected entities")

        Local e.SelectedEntity
        For e = Each SelectedEntity
            Local label$ = Threads::lookupName(self\threads, e\Kind, e\RefID)
            If label = "" Then label = "(stale " + e\Kind + "#" + Str(e\RefID) + ")"
            rowY = Composer::row(self, x, w, rowY, e\Kind, label)
        Next

        Composer::recordContentBottom(self, rowY)

        // Scrollbar
        If self\lastContentBottom > self\bodyBottom
            Composer::drawScrollbar(self, x + w - CMP_SCROLLBAR_W - 2, self\bodyTop, bodyH)
        EndIf

        // Footer
        LoomText(x + CMP_PAD, y + h - 22, "Esc clears selection", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
    End Method


    // -------------------------------------------------------------------------
    // bulkSelectionKindSummary -- builds the "actor, item" comma-joined
    // distinct-kinds string for the bulk header. Uses a per-kind flag
    // walk so the output order is stable.
    // -------------------------------------------------------------------------
    Method bulkSelectionKindSummary$()
        Local hasActor% = False
        Local hasItem% = False
        Local hasSpell% = False
        Local hasZone% = False
        Local hasFaction% = False
        Local hasAnimSet% = False
        Local e.SelectedEntity
        For e = Each SelectedEntity
            If e\Kind = "actor"   Then hasActor   = True
            If e\Kind = "item"    Then hasItem    = True
            If e\Kind = "spell"   Then hasSpell   = True
            If e\Kind = "zone"    Then hasZone    = True
            If e\Kind = "faction" Then hasFaction = True
            If e\Kind = "animset" Then hasAnimSet = True
        Next

        Local out$ = ""
        If hasActor   = True Then out = Composer::appendKind(self, out, "actor")
        If hasItem    = True Then out = Composer::appendKind(self, out, "item")
        If hasSpell   = True Then out = Composer::appendKind(self, out, "spell")
        If hasZone    = True Then out = Composer::appendKind(self, out, "zone")
        If hasFaction = True Then out = Composer::appendKind(self, out, "faction")
        If hasAnimSet = True Then out = Composer::appendKind(self, out, "animset")
        Return out
    End Method


    Method appendKind$(acc$, k$)
        If acc = "" Then Return k
        Return acc + ", " + k
    End Method


    // -------------------------------------------------------------------------
    // drawBulkDeleteButton -- arm/confirm shape like the single-entity
    // delete. Confirmed click iterates Each SelectedEntity and
    // dispatches EntityFactory_Delete for each; clears selection.
    // -------------------------------------------------------------------------
    Method drawBulkDeleteButton(btnX%, btnY%, mx%, my%, clicked%)
        Local hovered% = (mx >= btnX And mx < btnX + CMP_DISCARD_BTN_W And my >= btnY And my < btnY + CMP_DISCARD_BTN_H)

        // Arm window timeout
        If self\bulkDeleteArmAt > 0 And (MilliSecs() - self\bulkDeleteArmAt) > CMP_DELETE_ARM_MS
            self\bulkDeleteArmAt = 0
        EndIf

        Local armed% = (self\bulkDeleteArmAt > 0)

        If armed = True
            LoomFill(btnX, btnY, CMP_DISCARD_BTN_W, CMP_DISCARD_BTN_H, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
            LoomBorder(btnX, btnY, CMP_DISCARD_BTN_W, CMP_DISCARD_BTN_H, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
            LoomText(btnX + 6, btnY + 4, "Confirm?", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        Else If hovered = True
            LoomFill(btnX, btnY, CMP_DISCARD_BTN_W, CMP_DISCARD_BTN_H, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
            LoomBorder(btnX, btnY, CMP_DISCARD_BTN_W, CMP_DISCARD_BTN_H, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
            LoomText(btnX + 10, btnY + 4, "Delete", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        Else
            LoomFill(btnX, btnY, CMP_DISCARD_BTN_W, CMP_DISCARD_BTN_H, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
            LoomBorder(btnX, btnY, CMP_DISCARD_BTN_W, CMP_DISCARD_BTN_H, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
            LoomText(btnX + 10, btnY + 4, "Delete", LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
        EndIf

        If hovered And clicked
            If armed = True
                Composer::commitBulkDelete(self)
                self\bulkDeleteArmAt = 0
            Else
                self\bulkDeleteArmAt = MilliSecs()
                WriteLog(LoomLog, "Composer: bulk delete armed")
            EndIf
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // commitBulkDelete -- iterate Each SelectedEntity, dispatch
    // EntityFactory_Delete for each. Then clear the selection.
    //
    // Iteration is tricky because EntityFactory_Delete mutates the
    // SelectedEntity-adjacent state (via WorldCache_Invalidate, Timeline
    // recording, etc.). We snapshot kind+refID pairs upfront so the
    // delete loop doesn't trip over its own collection.
    //
    // NB: we don't delete the SelectedEntity instances directly --
    // Browser::clearSelection does that at the end.
    // -------------------------------------------------------------------------
    Method commitBulkDelete()
        If self\browser = Null Then Return
        Local count% = Browser::getSelectionCount(self\browser)
        If count <= 0 Then Return

        // Snapshot phase: walk the pool once and collect (kind, refID)
        // into a parallel Type so the delete loop has a stable target
        // list independent of any side effects.
        Local e.SelectedEntity
        Local n.BulkDeleteTarget
        For e = Each SelectedEntity
            n = New BulkDeleteTarget()
            n\Kind = e\Kind
            n\RefID = e\RefID
        Next

        // Delete phase: iterate the snapshot. Bulk delete summary
        // toast at the end (per-entity toasts would spam).
        Local deleted% = 0
        Local t.BulkDeleteTarget
        For t = Each BulkDeleteTarget
            If EntityFactory_Delete(t\Kind, t\RefID, self\threads) = True
                deleted = deleted + 1
            EndIf
        Next

        // Drop the snapshot
        For t = Each BulkDeleteTarget
            Delete t
        Next

        // Clear selection -- the entities are gone, so the visual
        // highlight on now-deleted cards would be misleading.
        Browser::clearSelection(self\browser)

        Toast_Show("Bulk delete: " + Str(deleted) + " of " + Str(count) + " entities removed", "danger")
        WriteLog(LoomLog, "Composer: bulk-deleted " + Str(deleted) + " entities")
    End Method


    // -------------------------------------------------------------------------
    // homogeneousSelectionKind -- returns the single shared Kind when every
    // selected entity is the same kind, else "". The bulk-field-broadcast
    // panel only shows when this returns nonempty -- mixed-kind selections
    // don't share enough field semantics for safe broadcast edits.
    // -------------------------------------------------------------------------
    Method homogeneousSelectionKind$()
        Local seenKind$ = ""
        Local e.SelectedEntity
        For e = Each SelectedEntity
            If seenKind = ""
                seenKind = e\Kind
            Else
                If e\Kind <> seenKind Then Return ""
            EndIf
        Next
        Return seenKind
    End Method


    // -------------------------------------------------------------------------
    // renderBulkFieldsFor -- renders the per-kind bulk-editable field rows.
    // Returns the next y position so the caller can continue the layout.
    //
    // Each kind has a curated 2-4 field subset that's safe to broadcast:
    //   item:    value, mass, weapon_damage, armour_level
    //   spell:   recharge_ms
    //   actor:   xpmult, scale, aggressiveness, agg_range, default_faction
    //   zone:    gravity, outdoors, pvp
    //   faction: (no useful broadcast field -- 0 fields rendered)
    //   animset: (no useful broadcast field -- 0 fields rendered)
    //
    // We deliberately omit name + script + race/class restriction fields
    // -- broadcasting those would clobber per-entity identity / behavior.
    // -------------------------------------------------------------------------
    Method renderBulkFieldsFor%(panelX%, panelW%, y%, kind$, mx%, my%, clicked%)
        If kind = "item"
            y = Composer::bulkFieldRow(self, panelX, panelW, y, "Value",         "value",         mx, my, clicked)
            y = Composer::bulkFieldRow(self, panelX, panelW, y, "Mass",          "mass",          mx, my, clicked)
            y = Composer::bulkFieldRow(self, panelX, panelW, y, "Damage",        "weapon_damage", mx, my, clicked)
            y = Composer::bulkFieldRow(self, panelX, panelW, y, "Armour level",  "armour_level",  mx, my, clicked)
            Return y
        EndIf
        If kind = "spell"
            y = Composer::bulkFieldRow(self, panelX, panelW, y, "Recharge (ms)", "recharge_ms",   mx, my, clicked)
            Return y
        EndIf
        If kind = "actor"
            y = Composer::bulkFieldRow(self, panelX, panelW, y, "XP multiplier", "xpmult",         mx, my, clicked)
            y = Composer::bulkFieldRow(self, panelX, panelW, y, "Scale",         "scale",          mx, my, clicked)
            y = Composer::bulkFieldRow(self, panelX, panelW, y, "Aggression",    "aggressiveness", mx, my, clicked)
            y = Composer::bulkFieldRow(self, panelX, panelW, y, "Aggro range",   "agg_range",      mx, my, clicked)
            y = Composer::bulkFieldRow(self, panelX, panelW, y, "Faction",       "default_faction", mx, my, clicked)
            Return y
        EndIf
        If kind = "zone"
            y = Composer::bulkFieldRow(self, panelX, panelW, y, "Gravity",       "gravity",       mx, my, clicked)
            Return y
        EndIf
        // faction / animset: no useful broadcast field -- leave the section
        // header in place but show a single explanatory row.
        LoomText(panelX + CMP_PAD, y + 4, "(no broadcast-safe fields for this kind)", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
        Return y + CMP_ROW_H
    End Method


    // -------------------------------------------------------------------------
    // bulkFieldRow -- one row in the bulk-edit panel. Layout:
    //
    //   [label]   [input box (typing target)]   [Apply]
    //
    // The input box is the click target to start editing -- clicking it
    // sets bulkEditField and clears the buffer (typing freshly is the
    // expected interaction; no inherited "current value" since selection
    // has many values).
    //
    // Apply button click commits the buffer to writeField for every
    // SelectedEntity. Buffer is preserved after commit so the user can
    // re-apply if they want.
    // -------------------------------------------------------------------------
    Method bulkFieldRow%(panelX%, panelW%, y%, label$, fieldId$, mx%, my%, clicked%)
        // Only paint when row is inside the scroll viewport
        If Composer::canPaintRow(self, y, CMP_ROW_H) = False Then Return y + CMP_ROW_H + 4

        LoomText(panelX + CMP_PAD, y + 4, label, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        // Input box
        Local inputX% = panelX + CMP_PAD + 110
        Local inputY% = y
        Local inputH% = CMP_ROW_H - 4
        Local active% = (self\bulkEditField = fieldId)

        If active = True
            LoomFill(inputX, inputY, CMP_BULK_INPUT_W, inputH, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B)
            LoomBorder(inputX, inputY, CMP_BULK_INPUT_W, inputH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            Local cursor$ = ""
            If (MilliSecs() Mod CMP_CURSOR_PERIOD) < (CMP_CURSOR_PERIOD / 2) Then cursor = "|"
            LoomText(inputX + 4, inputY + 3, self\bulkEditBuffer + cursor, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        Else
            LoomFill(inputX, inputY, CMP_BULK_INPUT_W, inputH, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
            LoomBorder(inputX, inputY, CMP_BULK_INPUT_W, inputH, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
            Local placeholder$ = self\bulkEditBuffer
            If placeholder = "" Then placeholder = "type & Apply..."
            LoomText(inputX + 4, inputY + 3, placeholder, LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
        EndIf

        // Input click -> start editing this field
        If clicked = True
            If mx >= inputX And mx < inputX + CMP_BULK_INPUT_W And my >= inputY And my < inputY + inputH
                // Commit any in-progress single-field edit first to avoid
                // a stuck editKind interfering with palette / keyboard pumping
                If self\editKind <> "" Then Composer::commitEdit(self)
                self\bulkEditField = fieldId
                self\bulkEditBuffer = ""
                FlushKeys
            EndIf
        EndIf

        // Apply button
        Local btnX% = inputX + CMP_BULK_INPUT_W + 8
        Local btnY% = y
        Local hovered% = (mx >= btnX And mx < btnX + CMP_BULK_APPLY_W And my >= btnY And my < btnY + CMP_BULK_APPLY_H)
        Local canApply% = (self\bulkEditField = fieldId And self\bulkEditBuffer <> "")

        If canApply = True
            If hovered = True
                LoomFill(btnX, btnY, CMP_BULK_APPLY_W, CMP_BULK_APPLY_H, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            Else
                LoomFill(btnX, btnY, CMP_BULK_APPLY_W, CMP_BULK_APPLY_H, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
            EndIf
            LoomBorder(btnX, btnY, CMP_BULK_APPLY_W, CMP_BULK_APPLY_H, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            LoomText(btnX + 8, btnY + 4, "Apply", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        Else
            LoomFill(btnX, btnY, CMP_BULK_APPLY_W, CMP_BULK_APPLY_H, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
            LoomBorder(btnX, btnY, CMP_BULK_APPLY_W, CMP_BULK_APPLY_H, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
            LoomText(btnX + 8, btnY + 4, "Apply", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
        EndIf

        If hovered = True And clicked = True And canApply = True
            Composer::commitBulkField(self, fieldId, self\bulkEditBuffer)
        EndIf

        Return y + CMP_ROW_H + 4
    End Method


    // -------------------------------------------------------------------------
    // commitBulkField -- broadcast a single value to fieldId on every
    // SelectedEntity that matches the homogeneous kind. Marks the kind's
    // *Saved global False (one mark, not per-entity). Timeline records a
    // single rolled-up entry rather than N edits to avoid spamming
    // history with bulk operations.
    // -------------------------------------------------------------------------
    Method commitBulkField(fieldId$, value$)
        Local homKind$ = Composer::homogeneousSelectionKind(self)
        If homKind = "" Then Return

        Local applied% = 0
        Local e.SelectedEntity
        For e = Each SelectedEntity
            If e\Kind = homKind
                Composer::writeField(self, e\Kind, e\RefID, fieldId, value)
                applied = applied + 1
            EndIf
        Next

        If applied > 0
            Composer::markDirtyForKind(self, homKind)
            WorldCache_Invalidate()
            Timeline_RecordEdit(homKind, -1, fieldId, "bulk-" + Str(applied), value, "Bulk edit " + Str(applied) + " " + homKind + "s")
            Toast_Show("Bulk: " + fieldId + " <- " + value + " on " + Str(applied) + " " + homKind + "s", "success")
        EndIf

        WriteLog(LoomLog, "Composer: bulk-field " + homKind + "." + fieldId + " <- " + Chr(34) + value + Chr(34) + " (n=" + Str(applied) + ")")
    End Method


    // -------------------------------------------------------------------------
    // pumpBulkKeyboard -- called per-frame when bulkEditField is nonempty.
    // Drains the keyboard queue into bulkEditBuffer with the same shape as
    // pumpKeyboard for single-edit. Enter does NOT auto-apply (would be
    // surprising for a destructive broadcast); user must click Apply.
    // Esc cancels (clears field+buffer).
    // -------------------------------------------------------------------------
    Method pumpBulkKeyboard()
        If self\bulkEditField = "" Then Return

        // Backspace
        If KeyHit(14) And Len(self\bulkEditBuffer) > 0
            self\bulkEditBuffer = Left$(self\bulkEditBuffer, Len(self\bulkEditBuffer) - 1)
        EndIf

        // Esc -- cancel
        If KeyHit(1)
            self\bulkEditField = ""
            self\bulkEditBuffer = ""
            Return
        EndIf

        // Printable chars
        Local k% = GetKey()
        While k > 0
            If k >= 32 And k <= 126
                self\bulkEditBuffer = self\bulkEditBuffer + Chr(k)
            EndIf
            k = GetKey()
        Wend
    End Method



    Method drawDuplicateButton(btnX%, btnY%, mx%, my%, clicked%, kind$, refID%)
        Local hovered% = (mx >= btnX And mx < btnX + CMP_DUP_BTN_W And my >= btnY And my < btnY + CMP_DUP_BTN_H)

        If hovered = True
            LoomFill(btnX, btnY, CMP_DUP_BTN_W, CMP_DUP_BTN_H, LOOM_ARCANE_700_R, LOOM_ARCANE_700_G, LOOM_ARCANE_700_B)
            LoomBorder(btnX, btnY, CMP_DUP_BTN_W, CMP_DUP_BTN_H, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        Else
            LoomFill(btnX, btnY, CMP_DUP_BTN_W, CMP_DUP_BTN_H, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
            LoomBorder(btnX, btnY, CMP_DUP_BTN_W, CMP_DUP_BTN_H, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        EndIf
        LoomText(btnX + 8, btnY + 4, "Dup", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        If hovered And clicked
            // Commit any in-progress edit so the source state matches
            // what the user sees before we copy it.
            If self\editKind <> "" Then Composer::commitEdit(self)
            EntityFactory_Duplicate(kind, refID, self\threads)
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // drawDeleteButton -- destructive action with arm/confirm. First click
    // arms (button turns red, footer hint updates); second click on the
    // same focus within CMP_DELETE_ARM_MS commits via EntityFactory_Delete.
    // Focus change / other-button click / timeout cancels.
    // -------------------------------------------------------------------------
    Method drawDeleteButton(btnX%, btnY%, mx%, my%, clicked%, kind$, refID%)
        Local hovered% = (mx >= btnX And mx < btnX + CMP_DELETE_BTN_W And my >= btnY And my < btnY + CMP_DELETE_BTN_H)

        // Has the arm window expired? If so, drop it.
        If self\deleteArmKind <> "" And (MilliSecs() - self\deleteArmAt) > CMP_DELETE_ARM_MS
            self\deleteArmKind = ""
            self\deleteArmRefID = 0
            self\deleteArmAt = 0
        EndIf

        Local armed% = (self\deleteArmKind = kind And self\deleteArmRefID = refID)

        If armed = True
            LoomFill(btnX, btnY, CMP_DELETE_BTN_W, CMP_DELETE_BTN_H, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
            LoomBorder(btnX, btnY, CMP_DELETE_BTN_W, CMP_DELETE_BTN_H, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
            LoomText(btnX + 7, btnY + 4, "X", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        Else If hovered = True
            LoomFill(btnX, btnY, CMP_DELETE_BTN_W, CMP_DELETE_BTN_H, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
            LoomBorder(btnX, btnY, CMP_DELETE_BTN_W, CMP_DELETE_BTN_H, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
            LoomText(btnX + 8, btnY + 4, "X", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        Else
            LoomFill(btnX, btnY, CMP_DELETE_BTN_W, CMP_DELETE_BTN_H, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
            LoomBorder(btnX, btnY, CMP_DELETE_BTN_W, CMP_DELETE_BTN_H, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
            LoomText(btnX + 8, btnY + 4, "X", LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
        EndIf

        If hovered And clicked
            If armed = True
                // Commit
                EntityFactory_Delete(kind, refID, self\threads)
                self\deleteArmKind = ""
                self\deleteArmRefID = 0
                self\deleteArmAt = 0
            Else
                // Arm
                self\deleteArmKind = kind
                self\deleteArmRefID = refID
                self\deleteArmAt = MilliSecs()
                WriteLog(LoomLog, "Composer: delete armed for " + kind + "#" + Str(refID))
            EndIf
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // drawDiscardButton -- reverts the current kind's in-memory state by
    // re-running its loader. Two-click arm/confirm like Delete. Only
    // rendered when the kind is dirty.
    // -------------------------------------------------------------------------
    Method drawDiscardButton(btnX%, btnY%, mx%, my%, clicked%, kind$)
        Local hovered% = (mx >= btnX And mx < btnX + CMP_DISCARD_BTN_W And my >= btnY And my < btnY + CMP_DISCARD_BTN_H)

        If self\discardArmKind <> "" And (MilliSecs() - self\discardArmAt) > CMP_DELETE_ARM_MS
            self\discardArmKind = ""
            self\discardArmAt = 0
        EndIf

        Local armed% = (self\discardArmKind = kind)

        If armed = True
            LoomFill(btnX, btnY, CMP_DISCARD_BTN_W, CMP_DISCARD_BTN_H, LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B)
            LoomBorder(btnX, btnY, CMP_DISCARD_BTN_W, CMP_DISCARD_BTN_H, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
            LoomText(btnX + 6, btnY + 4, "Confirm?", LOOM_INK_900_R, LOOM_INK_900_G, LOOM_INK_900_B)
        Else If hovered = True
            LoomFill(btnX, btnY, CMP_DISCARD_BTN_W, CMP_DISCARD_BTN_H, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
            LoomBorder(btnX, btnY, CMP_DISCARD_BTN_W, CMP_DISCARD_BTN_H, LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B)
            LoomText(btnX + 8, btnY + 4, "Discard", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        Else
            LoomFill(btnX, btnY, CMP_DISCARD_BTN_W, CMP_DISCARD_BTN_H, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
            LoomBorder(btnX, btnY, CMP_DISCARD_BTN_W, CMP_DISCARD_BTN_H, LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
            LoomText(btnX + 8, btnY + 4, "Discard", LOOM_STONE_200_R, LOOM_STONE_200_G, LOOM_STONE_200_B)
        EndIf

        If hovered And clicked
            If armed = True
                Composer::discardKind(self, kind)
                self\discardArmKind = ""
                self\discardArmAt = 0
            Else
                self\discardArmKind = kind
                self\discardArmAt = MilliSecs()
                WriteLog(LoomLog, "Composer: discard armed for " + kind)
            EndIf
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // discardKind -- reload the kind's in-memory state from disk, drop any
    // dirty flag. Uses GUE's existing Load* functions; because Load* New's
    // fresh Type instances and the in-memory state already has stale ones,
    // we have to free the old instances first.
    //
    // For Spell/Item/Actor: walk the *List arrays, Delete every non-Null
    // slot, then re-run LoadX which re-populates.
    // For AnimSet: walk AnimList[0..999], Delete each, re-run LoadAnimSets.
    // For Factions: just re-run LoadFactions -- it overwrites FactionNames$
    // in place.
    // For Zone (the focused one only -- per-zone .dat): re-run
    // ServerLoadArea on the original name. Tricky because the focused
    // handle becomes stale; close the composer first.
    // -------------------------------------------------------------------------
    Method discardKind(kind$)
        // Any discard reloads the whole kind from disk -- the cache
        // can't possibly be accurate after that. Invalidate up front so
        // every branch below benefits without per-branch repeats.
        WorldCache_Invalidate()

        If kind = "spell"
            Composer::freeAllSpells(self)
            LoadSpells("Data\Server Data\Spells.dat")
            SpellsSaved = True
            WriteLog(LoomLog, "Composer: discarded -- reloaded Spells.dat")
            Composer::reFocusOrClose(self, kind)
            Return
        EndIf
        If kind = "item"
            Composer::freeAllItems(self)
            LoadItems("Data\Server Data\Items.dat")
            ItemsSaved = True
            WriteLog(LoomLog, "Composer: discarded -- reloaded Items.dat")
            Composer::reFocusOrClose(self, kind)
            Return
        EndIf
        If kind = "actor"
            Composer::freeAllActors(self)
            LoadActors("Data\Server Data\Actors.dat")
            ActorsSaved = True
            WriteLog(LoomLog, "Composer: discarded -- reloaded Actors.dat")
            Composer::reFocusOrClose(self, kind)
            Return
        EndIf
        If kind = "animset"
            Composer::freeAllAnimSets(self)
            LoadAnimSets("Data\Game Data\Animations.dat")
            AnimsSaved = True
            WriteLog(LoomLog, "Composer: discarded -- reloaded Animations.dat")
            Composer::reFocusOrClose(self, kind)
            Return
        EndIf
        If kind = "faction"
            // LoadFactions overwrites FactionNames$ in-place; no free needed.
            LoadFactions("Data\Server Data\Factions.dat")
            FactionsSaved = True
            WriteLog(LoomLog, "Composer: discarded -- reloaded Factions.dat")
            Composer::reFocusOrClose(self, kind)
            Return
        EndIf
        If kind = "zone"
            // Reload only the focused zone's .dat. Its handle becomes
            // stale after ServerUnloadArea, so we capture the name first,
            // close the composer, free, and reload.
            Local Ar.Area = Object.Area(self\threads\focusID)
            If Ar = Null
                WriteLog(LoomLog, "Composer: discard zone -- stale handle, no-op")
                Return
            EndIf
            Local zoneName$ = Ar\Name$
            Threads::focus(self\threads, "", 0)
            Threads::clearStack(self\threads)
            ServerUnloadArea(Ar)
            ServerLoadArea(zoneName)
            ZoneSaved = True
            WriteLog(LoomLog, "Composer: discarded -- reloaded zone " + zoneName)
            Return
        EndIf
        WriteLog(LoomLog, "Composer: discardKind -- no handler for " + kind)
    End Method


    // -------------------------------------------------------------------------
    // Free-all helpers -- pre-discard cleanup. The Load* functions assume
    // they're populating a clean slate; not freeing first would leak the
    // current set into the type pool. Delegate to non-Strict helpers in
    // the data modules where possible (Dim-write trap means Loom can't
    // null array slots from Strict).
    // -------------------------------------------------------------------------
    Method freeAllSpells()
        Local id% = 0
        For id = 0 To 65534
            DeleteSpellTemplate(id)
        Next
    End Method

    Method freeAllItems()
        Local id% = 0
        For id = 0 To 65534
            DeleteItemTemplate(id)
        Next
    End Method

    Method freeAllActors()
        Local id% = 0
        For id = 0 To 65535
            DeleteActorTemplate(id)
        Next
    End Method

    Method freeAllAnimSets()
        Local id% = 0
        For id = 0 To 999
            DeleteAnimSetTemplate(id)
        Next
    End Method


    // -------------------------------------------------------------------------
    // reFocusOrClose -- after a discard reload, the focused refID may no
    // longer exist (e.g. you deleted a spell, then discarded -- the spell
    // is back but the ID may match a different newly-loaded spell, or none).
    // Safe behavior: if the focused entity still resolves, keep focus; else
    // close the composer back to the browser.
    // -------------------------------------------------------------------------
    Method reFocusOrClose(kind$)
        Local nm$ = Threads::lookupName(self\threads, kind, self\threads\focusID)
        If nm = ""
            Threads::focus(self\threads, "", 0)
            Threads::clearStack(self\threads)
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // drawSaveButton -- top-right Save affordance, only painted by
    // renderAndUpdate when the focused kind is dirty.
    // -------------------------------------------------------------------------
    Method drawSaveButton(btnX%, btnY%, mx%, my%, clicked%, kind$)
        Local hovered% = (mx >= btnX And mx < btnX + CMP_SAVE_BTN_W And my >= btnY And my < btnY + CMP_SAVE_BTN_H)

        If hovered = True
            LoomFill(btnX, btnY, CMP_SAVE_BTN_W, CMP_SAVE_BTN_H, LOOM_ARCANE_700_R, LOOM_ARCANE_700_G, LOOM_ARCANE_700_B)
            LoomBorder(btnX, btnY, CMP_SAVE_BTN_W, CMP_SAVE_BTN_H, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        Else
            LoomFill(btnX, btnY, CMP_SAVE_BTN_W, CMP_SAVE_BTN_H, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
            LoomBorder(btnX, btnY, CMP_SAVE_BTN_W, CMP_SAVE_BTN_H, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        EndIf
        LoomText(btnX + 18, btnY + 4, "Save", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        If hovered And clicked
            // Commit any in-progress edit first so the buffer is written
            // before serialization.
            If self\editKind <> "" Then Composer::commitEdit(self)
            Composer::commitSaveForKind(self, kind)
        EndIf
    End Method


    // label : thread chip row. Returns the next Y. ORs into self\chipHit
    // when the chip is clicked so renderAndUpdate can surface it.
    //
    // editFieldId$: when non-empty + right-click consumed, open the palette
    // as a picker targeting (self\threads\focusKind, self\threads\focusID,
    // editFieldId). When empty (back-reference chips, e.g. faction members
    // or animset users), right-click is ignored -- there's no field on the
    // current focus to write the chosen entity into.
    Method chipRow%(panelX%, panelW%, rowY%, label$, kind$, refID%, mx%, my%, clicked%, rightClicked%, editFieldId$)
        // Chip row needs the full CMP_CHIP_H height to be visible; skip
        // painting + hit-test entirely when scrolled off-screen.
        If Composer::canPaintRow(self, rowY, CMP_CHIP_H) = False
            Return rowY + CMP_CHIP_H + 4
        EndIf

        LoomText(panelX + CMP_PAD, rowY + 4, label, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        Local chipX% = panelX + CMP_PAD + 120
        Local chipW% = panelW - CMP_PAD * 2 - 120
        Local code% = Threads::renderChip(self\threads, chipX, rowY, chipW, CMP_CHIP_H, kind, refID, mx, my, clicked, rightClicked)
        If code = 1 Then self\chipHit = True
        If code = 2 And editFieldId <> "" And self\palette <> Null
            Palette::openAsPicker(self\palette, kind, self\threads\focusKind, self\threads\focusID, editFieldId)
        EndIf

        Return rowY + CMP_CHIP_H + 4
    End Method


    // Section header -- 3-line brass ornament rule + display-font label.
    // Returns the next Y. The triple rule mirrors the brand strip's
    // separator and the card-top accent so the visual rhythm is
    // consistent across surfaces.
    Method sectionHeader%(panelX%, panelW%, rowY%, title$)
        If Composer::canPaintRow(self, rowY, 28) = True
            LoomHRule(panelX + CMP_PAD,     rowY + 4, panelW - CMP_PAD * 2, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
            LoomHRule(panelX + CMP_PAD,     rowY + 5, panelW - CMP_PAD * 2, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            LoomHRule(panelX + CMP_PAD,     rowY + 6, panelW - CMP_PAD * 2, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
            LoomTheme_UseDisplay()
            LoomText(panelX + CMP_PAD,      rowY + 10, title, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            LoomTheme_UseBody()
        EndIf
        Return rowY + 34
    End Method


    // -------------------------------------------------------------------------
    // addNewButton -- small brass "+ New" pill button. Paints + hit-tests in
    // one call; returns True iff hovered AND clicked this frame. Designed to
    // be called RIGHT AFTER a sectionHeader so the button sits in the header
    // strip, but the caller passes the row Y where the header started.
    // -------------------------------------------------------------------------
    Method addNewButton%(panelX%, panelW%, sectionRowY%, mx%, my%, clicked%)
        Local btnW% = 60
        Local btnH% = 20
        Local btnX% = panelX + panelW - btnW - CMP_PAD - 4
        Local btnY% = sectionRowY + 6
        Local visible% = Composer::canPaintRow(self, sectionRowY, 28)
        Local hovered% = (mx >= btnX And mx < btnX + btnW And my >= btnY And my < btnY + btnH)

        If visible = True
            If hovered = True
                LoomFill(btnX, btnY, btnW, btnH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
                LoomBorder(btnX, btnY, btnW, btnH, LOOM_BRASS_300_R, LOOM_BRASS_300_G, LOOM_BRASS_300_B)
                LoomText(btnX + 12, btnY + 2, "+ New", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
            Else
                LoomFill(btnX, btnY, btnW, btnH, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
                LoomBorder(btnX, btnY, btnW, btnH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
                LoomText(btnX + 12, btnY + 2, "+ New", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            EndIf
        EndIf

        Return (hovered And clicked)
    End Method


    // -------------------------------------------------------------------------
    // subDeleteButton -- tiny "x" button for deleting one sub-entity row
    // (a portal, trigger, or spawn inside a zone). Paints + hit-tests in one
    // call; returns True iff hovered AND clicked this frame. No arm/confirm
    // for sub-entities -- they're typically authored in the same session
    // and an accidental delete is recoverable via Discard. Whole-entity
    // delete still uses arm/confirm because that's a bigger blast radius.
    // -------------------------------------------------------------------------
    Method subDeleteButton%(panelX%, panelW%, headerRowY%, mx%, my%, clicked%)
        Local btnW% = 18
        Local btnH% = 18
        Local btnX% = panelX + panelW - btnW - CMP_PAD - 4
        Local btnY% = headerRowY + 1
        Local visible% = Composer::canPaintRow(self, headerRowY, CMP_ROW_H)
        Local hovered% = (mx >= btnX And mx < btnX + btnW And my >= btnY And my < btnY + btnH)

        If visible = True
            If hovered = True
                LoomFill(btnX, btnY, btnW, btnH, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
                LoomBorder(btnX, btnY, btnW, btnH, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
                LoomText(btnX + 6, btnY + 2, "x", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
            Else
                LoomFill(btnX, btnY, btnW, btnH, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
                LoomBorder(btnX, btnY, btnW, btnH, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
                LoomText(btnX + 6, btnY + 2, "x", LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
            EndIf
        EndIf

        Return (hovered And clicked)
    End Method


    Method kindLabel$(kind$)
        If kind = "actor"   Then Return "ACTOR"
        If kind = "item"    Then Return "ITEM"
        If kind = "spell"   Then Return "SPELL"
        If kind = "zone"    Then Return "ZONE"
        If kind = "faction" Then Return "FACTION"
        If kind = "animset" Then Return "ANIMATION SET"
        Return Upper$(kind)
    End Method


    // -------------------------------------------------------------------------
    // Pinned-viewport helpers. The 3D preview surfaces (actor/item/mesh mesh
    // preview, zone schematic) live in a fixed, non-scrolling band at the
    // top of the composer body. These helpers tell renderAndUpdate which
    // kinds get a band, how tall it is, whether the cursor is over it (so
    // the wheel zooms instead of scrolls), and dispatch the actual draw.
    // -------------------------------------------------------------------------
    Method kindHasViewport%(kind$)
        If kind = "actor" Then Return True
        If kind = "item"  Then Return True
        If kind = "mesh"  Then Return True
        If kind = "zone"  Then Return True
        Return False
    End Method

    // Square edge of the PINNED-BOX viewport for this kind (mesh preview).
    // Zone returns 0: it doesn't use a composer band -- it renders as a
    // full-screen fly-around viewport in the left content area instead
    // (see the zone branch in renderAndUpdate). 0 means no band reserved,
    // so the zone fields fill the full composer height on the right.
    Method viewportSize%(kind$)
        If kind = "zone" Then Return 0
        If Composer::kindHasViewport(self, kind) = True Then Return LOOM_PREVIEW_SIZE
        Return 0
    End Method

    // True when the cursor is inside the pinned viewport rect for the
    // focused kind. Computed from constants (panel is right-anchored at
    // sw - CMP_W; the band top is bodyY = CMP_TOP + CMP_PAD + 50) so the
    // scroll handler can call it BEFORE the body layout runs.
    Method cursorOverViewportBand%(kind$, sw%)
        If Composer::kindHasViewport(self, kind) = False Then Return False
        If self\collapsed = True Then Return False
        Local vpSize% = Composer::viewportSize(self, kind)
        Local vpX% = (sw - CMP_W) + (CMP_W - vpSize) / 2
        Local vpY% = CMP_TOP + CMP_PAD + 50
        Local mxp% = MouseX()
        Local myp% = MouseY()
        If mxp >= vpX And mxp < vpX + vpSize And myp >= vpY And myp < vpY + vpSize Then Return True
        Return False
    End Method

    // Draw the pinned viewport at the fixed band (centered in the panel).
    // Re-resolves the entity by refID and dispatches to the right widget.
    Method drawPinnedViewport(kind$, refID%, panelX%, panelW%, vpY%)
        Local vpSize% = Composer::viewportSize(self, kind)
        Local vpX% = panelX + (panelW - vpSize) / 2
        If kind = "actor"
            If refID >= 0 And refID <= 65535
                Local A.Actor = ActorList(refID)
                If A <> Null
                    PreviewActorID = A\ID
                    Loom_DrawMeshPreview(A\MeshIDs[0], vpX, vpY, LOOM_PREVIEW_SIZE)
                    PreviewActorID = 0
                EndIf
            EndIf
        Else If kind = "item"
            If refID >= 0 And refID <= 65535
                Local It.Item = ItemList(refID)
                If It <> Null Then Loom_DrawMeshPreview(It\MMeshID, vpX, vpY, LOOM_PREVIEW_SIZE)
            EndIf
        Else If kind = "mesh"
            Local mh.MeshEntry = Meshes_GetByIndex(refID)
            If mh <> Null Then Loom_DrawMeshPreview(mh\ID, vpX, vpY, LOOM_PREVIEW_SIZE)
        EndIf
        // Zone is intentionally NOT here -- it renders full-screen via
        // Composer::drawZoneFullscreen in renderAndUpdate, not as a box.
    End Method


    // Full-screen zone fly-around viewport. Fills the left content area --
    // everything left of the composer panel, between the browser filter bar
    // (tabs/filter stay usable above) and the footer. Loom_DrawZoneViewport
    // renders the 3D scene directly to the back buffer at this rect with
    // orbit / pan / zoom + click-to-edit markers. The composer panel
    // overlays on the right, so zone fields stay editable while flying.
    Method drawZoneFullscreen(refID%, sw%, sh%)
        Local zvX% = 0
        Local zvY% = BR_TOP_RIBBON + BR_TAB_BAR_H + BR_FILTER_BAR_H
        Local zvW% = sw - CMP_W
        Local zvH% = sh - zvY - BR_BOT_RIBBON
        If zvW < 120 Or zvH < 120 Then Return
        Local Ar.Area = Object.Area(refID)
        If Ar <> Null Then Loom_DrawZoneViewport(refID, zvX, zvY, zvW, zvH)
    End Method


    // -------------------------------------------------------------------------
    // Thread web -- the design's signature surface. For a focused NON-zone
    // entity, draw it at the centre of the left content area with its
    // outgoing references fanning out as dashed threads to labelled,
    // clickable nodes. Each node reuses Threads::renderChip, so a left-click
    // jumps focus to it. Refs are collected once per focus change and cached
    // (the faction/animset back-ref collectors walk ActorList).
    // -------------------------------------------------------------------------
    Method drawThreadWeb(kind$, refID%, sw%, sh%, mx%, my%, clicked%, rightClicked%)
        Local rx% = 0
        Local ry% = BR_TOP_RIBBON + BR_TAB_BAR_H + BR_FILTER_BAR_H
        Local rw% = sw - CMP_W
        Local rh% = sh - ry - BR_BOT_RIBBON
        If rw < 160 Or rh < 160 Then Return

        // Backdrop so the web reads as its own surface.
        LoomGradientV(rx, ry, rw, rh, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B, LOOM_STONE_950_R, LOOM_STONE_950_G, LOOM_STONE_950_B)

        // (Re)collect the focused entity's threads on focus change only.
        If kind <> self\twLastKind Or refID <> self\twLastRefID
            Composer::collectThreads(self, kind, refID)
            self\twLastKind = kind
            self\twLastRefID = refID
        EndIf

        Local cx% = rx + rw / 2
        Local cy% = ry + rh / 2

        Local nodeH% = 24
        Local radius# = Float(rh) / 2.0 - 60.0
        Local maxR# = Float(rw) / 2.0 - 150.0
        If radius# > maxR# Then radius# = maxR#
        If radius# < 90.0 Then radius# = 90.0

        // Layout: collectThreads emits in type order, so twKind is already
        // grouped (faction, animset, mesh, texture, sound). Give each type its
        // own contiguous arc with a gap + heading between groups, so same-type
        // refs cluster and the type is obvious. Chip widths size to their
        // label. Centre node + notes paint after (on top).
        Local n% = self\twCount
        If n > 0
            Local groupCount% = 1
            Local gi%
            For gi = 1 To n - 1
                If self\twKind[gi] <> self\twKind[gi - 1] Then groupCount = groupCount + 1
            Next
            Local gapDeg# = 16.0
            Local usable# = 360.0 - gapDeg# * Float(groupCount)
            If usable# < 60.0 Then usable# = 60.0
            Local perNode# = usable# / Float(n)

            Local curAng# = -90.0
            Local grpStartAng# = -90.0
            Local i%
            For i = 0 To n - 1
                // Boundary: close the previous group with a heading, then gap.
                // NESTED If (not `i > 0 And ...`): BlitzForge's And is
                // non-short-circuit, so the single-line form read twKind[i-1]
                // = twKind[-1] when i=0 -- a negative array index that
                // corrupted memory / stack-overflowed on actor select.
                If i > 0
                    If self\twKind[i] <> self\twKind[i - 1]
                        Composer::drawTypeLabel(self, cx, cy, (grpStartAng# + curAng# - perNode#) / 2.0, radius# + 50.0, self\twKind[i - 1])
                        curAng# = curAng# + gapDeg#
                        grpStartAng# = curAng#
                    EndIf
                EndIf
                // Chip sized to its label (matches renderChip's text layout).
                Local nm$ = Threads::lookupName(self\threads, self\twKind[i], self\twRefID[i])
                If nm = "" Then nm = "(broken " + self\twKind[i] + " #" + Str(self\twRefID[i]) + ")"
                Local nodeW% = StringWidth(nm) + 64
                Local nxC% = cx + Int(Cos(curAng#) * radius#)
                Local nyC% = cy + Int(Sin(curAng#) * radius#)
                LoomLine(cx, cy, nxC, nyC, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
                Threads::renderChip(self\threads, nxC - nodeW / 2, nyC - nodeH / 2, nodeW, nodeH, self\twKind[i], self\twRefID[i], mx, my, clicked, rightClicked)
                curAng# = curAng# + perNode#
            Next
            // Heading for the final group.
            Composer::drawTypeLabel(self, cx, cy, (grpStartAng# + curAng# - perNode#) / 2.0, radius# + 50.0, self\twKind[n - 1])
        EndIf

        // Centre node -- the focused entity (highlighted, non-clickable).
        Local cName$ = Threads::lookupName(self\threads, kind, refID)
        If cName = "" Then cName = "(this)"
        Local cw% = 180
        Local ch% = 30
        Local cnx% = cx - cw / 2
        Local cny% = cy - ch / 2
        LoomFill(cnx, cny, cw, ch, LOOM_ARCANE_900_R, LOOM_ARCANE_900_G, LOOM_ARCANE_900_B)
        LoomBorder(cnx, cny, cw, ch, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        LoomBorder(cnx + 1, cny + 1, cw - 2, ch - 2, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        LoomText(cnx + 12, cny + 8, Threads::kindGlyph(self\threads, kind) + "  " + cName, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        // Notes.
        If self\twCount = 0
            LoomTextCentered(cx, cy + 46, "no outgoing references", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
        EndIf
        If self\twOverflow > 0
            LoomTextCentered(cx, ry + rh - 20, "+ " + Str(self\twOverflow) + " more -- see the composer roster", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
        EndIf
    End Method


    // Draw a type heading for a thread group at the given angle/radius from
    // the centre (brass, centered) -- e.g. "MESHES" above the mesh cluster.
    Method drawTypeLabel(cx%, cy%, ang#, r#, kind$)
        Local lx% = cx + Int(Cos(ang#) * r#)
        Local ly% = cy + Int(Sin(ang#) * r#)
        LoomTextCentered(lx, ly - 6, Composer::typeLabel(self, kind), LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
    End Method

    Method typeLabel$(kind$)
        If kind = "faction" Then Return "FACTION"
        If kind = "animset" Then Return "ANIM SETS"
        If kind = "mesh"    Then Return "MESHES"
        If kind = "texture" Then Return "TEXTURES"
        If kind = "sound"   Then Return "SOUNDS"
        If kind = "actor"   Then Return "ACTORS"
        Return Upper$(kind)
    End Method


    // True if (kind, refID) is already a thread node -- so an asset
    // referenced from several slots (e.g. one mesh used by 3 gubbins) shows
    // as a single node, not duplicates.
    Method twHas%(kind$, refID%)
        Local i%
        For i = 0 To self\twCount - 1
            If self\twKind[i] = kind And self\twRefID[i] = refID Then Return True
        Next
        Return False
    End Method

    // Add a thread node. Dedups, caps at TW_MAX (surplus -> twOverflow note).
    Method twAdd(kind$, refID%)
        If Composer::twHas(self, kind, refID) Then Return
        If self\twCount >= TW_MAX
            self\twOverflow = self\twOverflow + 1
            Return
        EndIf
        self\twKind[self\twCount] = kind
        self\twRefID[self\twCount] = refID
        self\twCount = self\twCount + 1
    End Method

    // Add an asset-catalog reference as a thread node. The entity stores an
    // asset ID; the catalog node needs the catalog INDEX, so resolve ID ->
    // entry -> Index. Unset (0) and "none" (65535) sentinels are skipped, as
    // are IDs not present in the catalog.
    Method twAddAsset(catKind$, assetID%)
        If assetID <= 0 Or assetID >= 65535 Then Return
        If catKind = "mesh"
            Local m.MeshEntry = Meshes_GetByID(assetID)
            If m <> Null Then Composer::twAdd(self, "mesh", m\Index)
        Else If catKind = "texture"
            Local t.TextureEntry = Textures_GetByID(assetID)
            If t <> Null Then Composer::twAdd(self, "texture", t\Index)
        Else If catKind = "sound"
            Local sd.SoundEntry = Sounds_GetByID(assetID)
            If sd <> Null Then Composer::twAdd(self, "sound", sd\Index)
        EndIf
    End Method


    Method collectThreads(kind$, refID%)
        self\twCount = 0
        self\twOverflow = 0
        If kind = "actor"
            If refID >= 0 And refID <= 65535
                Local A.Actor = ActorList(refID)
                If A <> Null
                    // Faction slot 0 is a VALID faction (e.g. "Traders"), so
                    // gate on "resolves to a real faction" not ">0".
                    If A\DefaultFaction >= 0 And A\DefaultFaction <= 99
                        If FactionNames$(A\DefaultFaction) <> "" Then Composer::twAdd(self, "faction", A\DefaultFaction)
                    EndIf
                    If A\MAnimationSet <> 0 Then Composer::twAdd(self, "animset", A\MAnimationSet)
                    If A\FAnimationSet <> 0 Then Composer::twAdd(self, "animset", A\FAnimationSet)
                    // Meshes: base bodies + 6 gubbins, beards, hair.
                    Local mi%
                    For mi = 0 To 7
                        Composer::twAddAsset(self, "mesh", A\MeshIDs[mi])
                    Next
                    Local bi%
                    For bi = 0 To 4
                        Composer::twAddAsset(self, "mesh", A\BeardIDs[bi])
                        Composer::twAddAsset(self, "mesh", A\MaleHairIDs[bi])
                        Composer::twAddAsset(self, "mesh", A\FemaleHairIDs[bi])
                    Next
                    // Textures: face / body / blood.
                    Local ti%
                    For ti = 0 To 4
                        Composer::twAddAsset(self, "texture", A\MaleFaceIDs[ti])
                        Composer::twAddAsset(self, "texture", A\FemaleFaceIDs[ti])
                        Composer::twAddAsset(self, "texture", A\MaleBodyIDs[ti])
                        Composer::twAddAsset(self, "texture", A\FemaleBodyIDs[ti])
                    Next
                    Composer::twAddAsset(self, "texture", A\BloodTexID)
                    // Speech sounds.
                    Local si%
                    For si = 0 To 15
                        Composer::twAddAsset(self, "sound", A\MSpeechIDs[si])
                        Composer::twAddAsset(self, "sound", A\FSpeechIDs[si])
                    Next
                EndIf
            EndIf
        Else If kind = "item"
            If refID >= 0 And refID <= 65534
                Local It.Item = ItemList(refID)
                If It <> Null
                    Composer::twAddAsset(self, "texture", It\ThumbnailTexID)
                    Composer::twAddAsset(self, "texture", It\ImageID)
                    Composer::twAddAsset(self, "mesh", It\MMeshID)
                    Composer::twAddAsset(self, "mesh", It\FMeshID)
                EndIf
            EndIf
        Else If kind = "spell"
            If refID >= 0 And refID <= 65534
                Local Sp.Spell = SpellsList(refID)
                If Sp <> Null Then Composer::twAddAsset(self, "texture", Sp\ThumbnailTexID)
            EndIf
        Else If kind = "faction"
            // Iterate ONLY real actors via Each Actor (like renderFaction) --
            // never the 0..65535 ActorList walk, whose Null reads via
            // BlitzForge's non-short-circuit And caused the faction-0 crash.
            For Ac.Actor = Each Actor
                If Ac\DefaultFaction = refID Then Composer::twAdd(self, "actor", Ac\ID)
            Next
        Else If kind = "animset"
            For Aw.Actor = Each Actor
                If Aw\MAnimationSet = refID Or Aw\FAnimationSet = refID Then Composer::twAdd(self, "actor", Aw\ID)
            Next
        EndIf
        // mesh / texture / sound / settings: no outgoing threads (they're leaf
        // assets / config). The composer roster + "Used by" sections cover
        // their back-references.
    End Method


    // -------------------------------------------------------------------------
    // Per-kind body renderers. Each lays out rows starting at bodyY and
    // returns void; chipHit is latched onto self for renderAndUpdate to see.
    // -------------------------------------------------------------------------

    Method renderActor(panelX%, bodyY%, panelW%, bodyH%, mx%, my%, clicked%, rightClicked%)
        Local refID% = self\threads\focusID
        If refID < 0 Or refID > 65535 Then Return
        Local A.Actor = ActorList(refID)
        If A = Null Then Return

        Local y% = bodyY

        // No 3D mesh preview here. The embedded MeshPreview camera had no
        // CameraViewport sub-rect, so its RenderWorld painted the mesh to
        // the FULL backbuffer (the grey mesh bleeding across the whole
        // window) and it consumed the wheel, fighting the body scroll.
        // 3D inspection belongs in a dedicated surface, not the scrollable
        // property panel -- see ADR 004. Mesh IDs render as plain rows.

        y = Composer::row(self, panelX, panelW, y, "ID",            Str(A\ID))
        y = Composer::editableRow(self, panelX, panelW, y,    "Race",          "actor", A\ID, "race",          A\Race$,        mx, my, clicked)
        y = Composer::editableRow(self, panelX, panelW, y,    "Class",         "actor", A\ID, "class",         A\Class$,       mx, my, clicked)
        y = Composer::enumIntRow(self, panelX, panelW, y, "Aggressiveness", "actor", A\ID, "aggressiveness", A\Aggressiveness, "aggressiveness", mx, my, clicked)
        y = Composer::editableIntRow(self, panelX, panelW, y, "Agg range",     "actor", A\ID, "agg_range",     A\AggressiveRange, mx, my, clicked)
        y = Composer::enumIntRow(self, panelX, panelW, y, "Genders",       "actor", A\ID, "genders",       A\Genders,      "genders", mx, my, clicked)
        y = Composer::toggleRow(self,    panelX, panelW, y, "Playable",      "actor", A\ID, "playable",      A\Playable,     mx, my, clicked)
        y = Composer::toggleRow(self,    panelX, panelW, y, "Rideable",      "actor", A\ID, "rideable",      A\Rideable,     mx, my, clicked)
        y = Composer::editableIntRow(self, panelX, panelW, y, "XP multiplier", "actor", A\ID, "xpmult",        A\XPMultiplier, mx, my, clicked)
        y = Composer::editableFloatRow(self, panelX, panelW, y, "Scale",       "actor", A\ID, "scale",         A\Scale#,       mx, my, clicked)
        y = Composer::editableFloatRow(self, panelX, panelW, y, "Radius",      "actor", A\ID, "radius",        A\Radius#,      mx, my, clicked)
        y = Composer::enumIntRow(self, panelX, panelW, y, "Environment",  "actor", A\ID, "environment",   A\Environment,  "environment", mx, my, clicked)
        y = Composer::editableIntRow(self, panelX, panelW, y, "Inv slots",    "actor", A\ID, "inv_slots",     A\InventorySlots, mx, my, clicked)
        y = Composer::enumIntRow(self, panelX, panelW, y, "Default DT",   "actor", A\ID, "default_dmg",   A\DefaultDamageType, "damagetype", mx, my, clicked)
        y = Composer::enumIntRow(self, panelX, panelW, y, "Trade mode",   "actor", A\ID, "trade_mode",    A\TradeMode,    "trademode", mx, my, clicked)
        y = Composer::toggleRow(self,    panelX, panelW, y, "Poly collide",  "actor", A\ID, "poly_collision", A\PolyCollision, mx, my, clicked)

        // Description -- a long string; show as editable text field. Word
        // wrap is a future enhancement.
        y = Composer::editableRow(self, panelX, panelW, y, "Description", "actor", A\ID, "description", A\Description$, mx, my, clicked)

        y = Composer::sectionHeader(self, panelX, panelW, y, "Start location")
        y = Composer::editableRow(self, panelX, panelW, y, "Start area",   "actor", A\ID, "start_area",   A\StartArea$,   mx, my, clicked)
        y = Composer::editableRow(self, panelX, panelW, y, "Start portal", "actor", A\ID, "start_portal", A\StartPortal$, mx, my, clicked)

        y = Composer::sectionHeader(self, panelX, panelW, y, "Threads")

        // Editable ref chips: right-click opens the palette as a picker
        // filtered to the chip's kind; selection writes the new refID
        // into the named field via Composer::writeField.
        y = Composer::chipRow(self, panelX, panelW, y, "Faction",    "faction", A\DefaultFaction, mx, my, clicked, rightClicked, "default_faction")
        y = Composer::chipRow(self, panelX, panelW, y, "M anim set", "animset", A\MAnimationSet,  mx, my, clicked, rightClicked, "manim_set")
        y = Composer::chipRow(self, panelX, panelW, y, "F anim set", "animset", A\FAnimationSet,  mx, my, clicked, rightClicked, "fanim_set")

        // Attributes table -- 40 rows of (Name | Value | Maximum). Skip
        // rows with empty AttributeNames so designers see only the
        // project's defined attributes. Both columns are editable via
        // fieldId pattern "attribute_value_<i>" / "attribute_max_<i>".
        y = Composer::sectionHeader(self, panelX, panelW, y, "Attributes (Value | Max)")
        y = Composer::renderAttributesTable(self, panelX, panelW, y, "actor", A\ID, A\Attributes, mx, my, clicked)

        // Resistances -- 19 damage-type resistances (defined in
        // DamageTypes$()). Each is a single int. Skip rows with empty
        // DamageTypes name (lets a project define fewer than 20 types).
        y = Composer::sectionHeader(self, panelX, panelW, y, "Resistances")
        y = Composer::renderResistancesTable(self, panelX, panelW, y, A, mx, my, clicked)

        // Appearance -- meshes, beards, hair, face, body, hair colours,
        // speech, blood. Texture fields (Face/Body/Blood) get thumbnails
        // via the existing ImageCache. Mesh fields render as plain int
        // rows since mesh preview requires a 3D viewport (ADR 004).
        y = Composer::sectionHeader(self, panelX, panelW, y, "Body meshes")

        // 3D mesh preview now lives at the TOP of renderActor's body
        // (above ID/Race/Class etc.) so it's always visible without
        // scrolling and its hit-rect can't shadow any int input cell
        // here. See the actorPreview* block at the top of renderActor.

        y = Composer::assetIntRow(self, panelX, panelW, y, "Male base",     "actor", A\ID, "mesh_0", A\MeshIDs[0], "mesh", mx, my, clicked, rightClicked)
        y = Composer::assetIntRow(self, panelX, panelW, y, "Female base",   "actor", A\ID, "mesh_1", A\MeshIDs[1], "mesh", mx, my, clicked, rightClicked)
        Local mi%
        For mi = 2 To 7
            y = Composer::assetIntRow(self, panelX, panelW, y, "Gubbin " + Str(mi - 2), "actor", A\ID, "mesh_" + Str(mi), A\MeshIDs[mi], "mesh", mx, my, clicked, rightClicked)
        Next

        y = Composer::sectionHeader(self, panelX, panelW, y, "Beard meshes (male)")
        Local bi%
        For bi = 0 To 4
            y = Composer::assetIntRow(self, panelX, panelW, y, "Slot " + Str(bi), "actor", A\ID, "beard_" + Str(bi), A\BeardIDs[bi], "mesh", mx, my, clicked, rightClicked)
        Next

        y = Composer::sectionHeader(self, panelX, panelW, y, "Hair meshes")
        Local hi%
        For hi = 0 To 4
            y = Composer::assetIntRow(self, panelX, panelW, y, "Male " + Str(hi),   "actor", A\ID, "mhair_" + Str(hi), A\MaleHairIDs[hi],   "mesh", mx, my, clicked, rightClicked)
        Next
        For hi = 0 To 4
            y = Composer::assetIntRow(self, panelX, panelW, y, "Female " + Str(hi), "actor", A\ID, "fhair_" + Str(hi), A\FemaleHairIDs[hi], "mesh", mx, my, clicked, rightClicked)
        Next

        y = Composer::sectionHeader(self, panelX, panelW, y, "Face textures")
        Local fi%
        For fi = 0 To 4
            y = Composer::renderActorTextureRow(self, panelX, panelW, y, "Male " + Str(fi),   "actor", A\ID, "mface_" + Str(fi), A\MaleFaceIDs[fi],   mx, my, clicked, rightClicked)
        Next
        For fi = 0 To 4
            y = Composer::renderActorTextureRow(self, panelX, panelW, y, "Female " + Str(fi), "actor", A\ID, "fface_" + Str(fi), A\FemaleFaceIDs[fi], mx, my, clicked, rightClicked)
        Next

        y = Composer::sectionHeader(self, panelX, panelW, y, "Body textures")
        Local bdi%
        For bdi = 0 To 4
            y = Composer::renderActorTextureRow(self, panelX, panelW, y, "Male " + Str(bdi),   "actor", A\ID, "mbody_" + Str(bdi), A\MaleBodyIDs[bdi],   mx, my, clicked, rightClicked)
        Next
        For bdi = 0 To 4
            y = Composer::renderActorTextureRow(self, panelX, panelW, y, "Female " + Str(bdi), "actor", A\ID, "fbody_" + Str(bdi), A\FemaleBodyIDs[bdi], mx, my, clicked, rightClicked)
        Next

        y = Composer::sectionHeader(self, panelX, panelW, y, "Hair colours (packed RGB)")
        Local ci%
        For ci = 0 To 15
            y = Composer::editableIntRow(self, panelX, panelW, y, "Slot " + Str(ci), "actor", A\ID, "haircol_" + Str(ci), A\HairColours[ci], mx, my, clicked)
        Next

        y = Composer::sectionHeader(self, panelX, panelW, y, "Speech sounds (male)")
        Local si%
        For si = 0 To 15
            y = Composer::assetIntRow(self, panelX, panelW, y, "Slot " + Str(si), "actor", A\ID, "mspeech_" + Str(si), A\MSpeechIDs[si], "sound", mx, my, clicked, rightClicked)
        Next

        y = Composer::sectionHeader(self, panelX, panelW, y, "Speech sounds (female)")
        For si = 0 To 15
            y = Composer::assetIntRow(self, panelX, panelW, y, "Slot " + Str(si), "actor", A\ID, "fspeech_" + Str(si), A\FSpeechIDs[si], "sound", mx, my, clicked, rightClicked)
        Next

        y = Composer::sectionHeader(self, panelX, panelW, y, "Blood")
        y = Composer::renderActorTextureRow(self, panelX, panelW, y, "Blood tex", "actor", A\ID, "blood_tex", A\BloodTexID, mx, my, clicked, rightClicked)

        // Reverse references -- "Spawned in zones" -- walk every Area's
        // 1000-slot SpawnActor table for any match against this actor.
        // Closes the design's "every reference is a clickable thread,
        // both directions" gap for actors. A designer asking "where does
        // this NPC actually appear?" previously had to grep Zone .dat
        // files; now it's a click. Heavy walk in the worst case (areas
        // * 1000) so we cap the listing at 50 zones to keep the panel
        // legible on a project with thousands of spawns of one creature.
        y = Composer::sectionHeader(self, panelX, panelW, y, "Spawned in zones")
        Local spawnHitCount% = 0
        Local spawnZoneCount% = 0
        Local zoneIter.Area = Null
        For zoneIter = Each Area
            Local hitInZone% = False
            Local si2%
            For si2 = 0 To 999
                If zoneIter\SpawnActor[si2] = A\ID
                    hitInZone = True
                    spawnHitCount = spawnHitCount + 1
                EndIf
            Next
            If hitInZone = True
                If spawnZoneCount < 50
                    y = Composer::chipRow(self, panelX, panelW, y, "", "zone", Handle(zoneIter), mx, my, clicked, rightClicked, "")
                EndIf
                spawnZoneCount = spawnZoneCount + 1
            EndIf
        Next
        If spawnZoneCount = 0
            If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                LoomText(panelX + CMP_PAD, y + 4, "(not spawned in any zone)", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
            EndIf
            y = y + CMP_ROW_H
        EndIf
        If spawnZoneCount > 50
            If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                LoomText(panelX + CMP_PAD, y + 4, "(+ " + Str(spawnZoneCount - 50) + " more zones)", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
            EndIf
            y = y + CMP_ROW_H
        EndIf
        If spawnHitCount > 0
            If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                LoomText(panelX + CMP_PAD, y + 4, Str(spawnHitCount) + " spawn slot(s) across " + Str(spawnZoneCount) + " zone(s)", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            EndIf
            y = y + CMP_ROW_H
        EndIf

        Composer::recordContentBottom(self, y)
    End Method


    // -------------------------------------------------------------------------
    // renderActorTextureRow -- like editableIntRow but with a 32x32 thumbnail
    // anchored at the right side of the row. Used for actor face/body/blood
    // texture references so designers can SEE the asset, not just edit
    // the int ID. Thumbnail comes from the same ImageCache as the Item /
    // Spell composer thumbnails (so the cache stays warm across kinds).
    // -------------------------------------------------------------------------
    Method renderActorTextureRow%(panelX%, panelW%, rowY%, label$, kind$, refID%, fieldId$, storedValue%, mx%, my%, clicked%, rightClicked%)
        Local nextY% = Composer::editableIntRow(self, panelX, panelW, rowY, label, kind, refID, fieldId, storedValue, mx, my, clicked)

        // Thumbnail anchored to the right side of the row. The thumbnail
        // itself is the jump target: click it to focus the texture
        // catalog entry. Right-click the thumbnail opens the palette
        // as a texture picker for this field.
        Local tx% = panelX + panelW - 36
        Local ty% = rowY - 4
        Local tSize% = 32
        Local tHovered% = (mx >= tx And mx < tx + tSize And my >= ty And my < ty + tSize)

        If Composer::canPaintRow(self, rowY, CMP_ROW_H) = True
            Loom_DrawThumbnailSmall(storedValue, tx, ty)
            // Brass outline + "jump" cue on hover so users discover
            // the click-to-focus pattern.
            If tHovered = True
                LoomBorder(tx - 1, ty - 1, tSize + 2, tSize + 2, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            EndIf
        EndIf

        If tHovered = True And clicked = True And storedValue <> 0
            Local te.TextureEntry = Textures_GetByID(storedValue)
            If te <> Null
                Threads::jump(self\threads, "texture", te\Index)
            EndIf
        EndIf
        If tHovered = True And rightClicked = True And self\palette <> Null
            Palette::openAsPicker(self\palette, "texture", kind, refID, fieldId)
        EndIf

        Return nextY
    End Method


    // -------------------------------------------------------------------------
    // renderAttributesTable -- 40-row Value | Max grid scoped to the focused
    // Actor's Attributes sub-instance. Empty-name rows are skipped so the
    // table only shows what the project has defined in Attributes.dat.
    //
    // Layout: [label | value-int-input | max-int-input]. Each input
    // routes through writeField with a synthetic fieldId encoding the
    // attribute index ("attribute_value_<i>" / "attribute_max_<i>") so
    // the dispatch table can branch on the prefix.
    // -------------------------------------------------------------------------
    Method renderAttributesTable%(panelX%, panelW%, y%, kind$, refID%, attrs.Attributes, mx%, my%, clicked%)
        If attrs = Null Then Return y
        Local i%
        For i = 0 To 39
            If AttributeNames$(i) <> ""
                Local fidV$ = "attribute_value_" + Str(i)
                Local fidM$ = "attribute_max_"   + Str(i)
                y = Composer::doubleIntRow(self, panelX, panelW, y, AttributeNames$(i), kind, refID, fidV, attrs\Value[i], fidM, attrs\Maximum[i], mx, my, clicked)
            EndIf
        Next
        Return y
    End Method


    // -------------------------------------------------------------------------
    // renderResistancesTable -- 19 damage-type resistance rows. The label
    // for each comes from DamageTypes$(i); empty names skipped.
    // -------------------------------------------------------------------------
    Method renderResistancesTable%(panelX%, panelW%, y%, A.Actor, mx%, my%, clicked%)
        Local i%
        For i = 0 To 19
            If DamageTypes$(i) <> ""
                Local fid$ = "resistance_" + Str(i)
                y = Composer::editableIntRow(self, panelX, panelW, y, DamageTypes$(i), "actor", A\ID, fid, A\Resistances[i], mx, my, clicked)
            EndIf
        Next
        Return y
    End Method


    Method renderItem(panelX%, bodyY%, panelW%, bodyH%, mx%, my%, clicked%, rightClicked%)
        Local refID% = self\threads\focusID
        If refID < 0 Or refID > 65534 Then Return
        Local It.Item = ItemList(refID)
        If It = Null Then Return

        Local y% = bodyY
        y = Composer::row(self, panelX, panelW, y, "ID",        Str(It\ID))
        y = Composer::editableRow(self, panelX, panelW, y, "Name", "item", It\ID, "name", It\Name$, mx, my, clicked)
        y = Composer::row(self, panelX, panelW, y, "Type",      Composer::itemTypeLabel(self, It\ItemType))
        y = Composer::row(self, panelX, panelW, y, "Slot",      Str(It\SlotType) + " (" + Composer::enumName(self, "slottype", It\SlotType) + ")")
        y = Composer::editableIntRow(self, panelX, panelW, y, "Value", "item", It\ID, "value", It\Value, mx, my, clicked)
        y = Composer::editableIntRow(self, panelX, panelW, y, "Mass",  "item", It\ID, "mass",  It\Mass,  mx, my, clicked)
        y = Composer::toggleRow(self, panelX, panelW, y, "Stackable", "item", It\ID, "stackable", It\Stackable,   mx, my, clicked)
        y = Composer::toggleRow(self, panelX, panelW, y, "Breakable", "item", It\ID, "breakable", It\TakesDamage, mx, my, clicked)

        // Weapon-specific
        If It\ItemType = 1
            y = Composer::sectionHeader(self, panelX, panelW, y, "Weapon")
            y = Composer::editableIntRow(self, panelX, panelW, y, "Damage",      "item", It\ID, "weapon_damage", It\WeaponDamage, mx, my, clicked)
            y = Composer::enumIntRow(self, panelX, panelW, y, "Damage type", "item", It\ID, "weapon_dmg_type", It\WeaponDamageType, "damagetype", mx, my, clicked)
            y = Composer::enumIntRow(self, panelX, panelW, y, "Weapon type", "item", It\ID, "weapon_type",   It\WeaponType,   "weapontype", mx, my, clicked)
            y = Composer::editableFloatRow(self, panelX, panelW, y, "Range",     "item", It\ID, "range",         It\Range#,       mx, my, clicked)
            y = Composer::editableIntRow(self, panelX, panelW, y, "Ranged proj.","item", It\ID, "ranged_proj",   It\RangedProjectile, mx, my, clicked)
            y = Composer::editableRow(self,    panelX, panelW, y, "Ranged anim", "item", It\ID, "ranged_anim",   It\RangedAnimation$, mx, my, clicked)
        EndIf

        // Armour-specific
        If It\ItemType = 2
            y = Composer::sectionHeader(self, panelX, panelW, y, "Armour")
            y = Composer::editableIntRow(self, panelX, panelW, y, "Armour level", "item", It\ID, "armour_level", It\ArmourLevel, mx, my, clicked)
        EndIf

        // Potion / food -- ItemType 6/7 are potions/ingredients; show eat
        // effects regardless of type (some projects extend item types).
        y = Composer::sectionHeader(self, panelX, panelW, y, "Consumable")
        y = Composer::editableIntRow(self, panelX, panelW, y, "Eat duration (ms)", "item", It\ID, "eat_length", It\EatEffectsLength, mx, my, clicked)

        // Visuals -- texture / mesh / gubbin IDs.
        y = Composer::sectionHeader(self, panelX, panelW, y, "Visuals")
        // Thumbnail preview: 64x64 image rect to the right of the editable
        // ID field. Lazy-loaded via ImageCache module; missing/invalid IDs
        // paint a "?" placeholder.
        Local thumbY% = y
        y = Composer::assetIntRow(self, panelX, panelW, y, "Thumbnail tex",  "item", It\ID, "thumb_tex",  It\ThumbnailTexID, "texture", mx, my, clicked, rightClicked)
        If Composer::canPaintRow(self, thumbY, 70) = True
            Local thumbX% = panelX + panelW - 70 - CMP_PAD
            Loom_DrawThumbnailLarge(It\ThumbnailTexID, thumbX, thumbY)
        EndIf
        y = y + 50   ; padding so the next row clears the 64px-tall thumbnail

        // No embedded 3D mesh preview (removed: full-backbuffer bleed +
        // wheel conflict with the body scroll -- see renderActor). The
        // 2D thumbnail above stays; mesh IDs render as plain asset rows.
        y = Composer::assetIntRow(self, panelX, panelW, y, "Male mesh",      "item", It\ID, "m_mesh",     It\MMeshID,        "mesh",    mx, my, clicked, rightClicked)
        y = Composer::assetIntRow(self, panelX, panelW, y, "Female mesh",    "item", It\ID, "f_mesh",     It\FMeshID,        "mesh",    mx, my, clicked, rightClicked)
        y = Composer::assetIntRow(self, panelX, panelW, y, "Image (img-typ)","item", It\ID, "image_id",   It\ImageID,        "texture", mx, my, clicked, rightClicked)
        // Gubbins -- 5 equip-slot activation flags (booleans 0/1)
        Local gi%
        For gi = 0 To 4
            Local gFid$ = "gubbin_" + Str(gi)
            y = Composer::toggleRow(self, panelX, panelW, y, "Gubbin " + Str(gi), "item", It\ID, gFid, It\Gubbins[gi], mx, my, clicked)
        Next

        // Misc data -- free-form string slot the engine doesn't interpret
        y = Composer::sectionHeader(self, panelX, panelW, y, "Misc")
        y = Composer::editableRow(self, panelX, panelW, y, "Misc data", "item", It\ID, "misc_data", It\MiscData$, mx, my, clicked)

        // Restrictions -- always editable (typing into an empty field is how
        // a restriction is added in the first place).
        y = Composer::sectionHeader(self, panelX, panelW, y, "Restricted to")
        y = Composer::editableRow(self, panelX, panelW, y, "Race",  "item", It\ID, "race",  It\ExclusiveRace$,  mx, my, clicked)
        y = Composer::editableRow(self, panelX, panelW, y, "Class", "item", It\ID, "class", It\ExclusiveClass$, mx, my, clicked)

        // Script -- always editable
        y = Composer::sectionHeader(self, panelX, panelW, y, "Script")
        y = Composer::scriptStringRow(self, panelX, panelW, y, "Bound",  "item", It\ID, "script",  It\Script$,  mx, my, clicked, rightClicked)
        y = Composer::editableRow(self, panelX, panelW, y, "Method", "item", It\ID, "smethod", It\SMethod$, mx, my, clicked)

        // Attributes -- same 40-row Value | Max grid as Actor. For items,
        // these typically encode equipped-bonuses (a sword that adds +5 STR,
        // armor that adds +20 HP, a potion that adds +10 mana, etc).
        y = Composer::sectionHeader(self, panelX, panelW, y, "Attributes (Value | Max)")
        y = Composer::renderAttributesTable(self, panelX, panelW, y, "item", It\ID, It\Attributes, mx, my, clicked)

        Composer::recordContentBottom(self, y)
    End Method


    Method renderSpell(panelX%, bodyY%, panelW%, bodyH%, mx%, my%, clicked%, rightClicked%)
        Local refID% = self\threads\focusID
        If refID < 0 Or refID > 65534 Then Return
        Local S.Spell = SpellsList(refID)
        If S = Null Then Return

        Local y% = bodyY
        y = Composer::row(self, panelX, panelW, y, "ID",          Str(S\ID))
        y = Composer::editableRow(self,    panelX, panelW, y, "Name",     "spell", S\ID, "name",        S\Name$,        mx, my, clicked)
        y = Composer::editableIntRow(self, panelX, panelW, y, "Recharge (ms)", "spell", S\ID, "recharge_ms", S\RechargeTime, mx, my, clicked)
        Local spellThumbY% = y
        y = Composer::assetIntRow(self, panelX, panelW, y, "Thumbnail tex", "spell", S\ID, "thumb_tex",   S\ThumbnailTexID, "texture", mx, my, clicked, rightClicked)
        // Thumbnail preview at the right edge -- matches the Item visuals row.
        If Composer::canPaintRow(self, spellThumbY, 70) = True
            Local spellThumbX% = panelX + panelW - 70 - CMP_PAD
            Loom_DrawThumbnailLarge(S\ThumbnailTexID, spellThumbX, spellThumbY)
        EndIf
        y = y + 50

        y = Composer::sectionHeader(self, panelX, panelW, y, "Description")
        y = Composer::editableRow(self, panelX, panelW, y, "Text", "spell", S\ID, "description", S\Description$, mx, my, clicked)

        y = Composer::sectionHeader(self, panelX, panelW, y, "Restricted to")
        y = Composer::editableRow(self, panelX, panelW, y, "Race",  "spell", S\ID, "race",  S\ExclusiveRace$,  mx, my, clicked)
        y = Composer::editableRow(self, panelX, panelW, y, "Class", "spell", S\ID, "class", S\ExclusiveClass$, mx, my, clicked)

        y = Composer::sectionHeader(self, panelX, panelW, y, "Script")
        y = Composer::scriptStringRow(self, panelX, panelW, y, "Bound",  "spell", S\ID, "script",  S\Script$,  mx, my, clicked, rightClicked)
        y = Composer::editableRow(self, panelX, panelW, y, "Method", "spell", S\ID, "smethod", S\SMethod$, mx, my, clicked)
        Composer::recordContentBottom(self, y)
    End Method


    Method renderZone(panelX%, bodyY%, panelW%, bodyH%, mx%, my%, clicked%, rightClicked%)
        Local Ar.Area = Object.Area(self\threads\focusID)
        If Ar = Null Then Return
        Local h% = Handle(Ar)

        Local y% = bodyY

        // No embedded 3D zone viewport here. Like the actor/item mesh
        // preview, the VPCam RenderWorld competed for the wheel with the
        // body scroll and the spatial view belongs on its own surface
        // (the Zones tab "Atlas" toggle already gives a 2D portal-graph
        // view). The composer shows zone metadata + portal-target chips.

        y = Composer::editableRow(self,    panelX, panelW, y, "Name",     "zone", h, "name",     Ar\Name$,    mx, my, clicked)
        y = Composer::toggleRow(self,      panelX, panelW, y, "Outdoors", "zone", h, "outdoors", Ar\Outdoors, mx, my, clicked)
        y = Composer::toggleRow(self,      panelX, panelW, y, "PvP",      "zone", h, "pvp",      Ar\PvP,      mx, my, clicked)
        y = Composer::editableIntRow(self, panelX, panelW, y, "Gravity",  "zone", h, "gravity",  Ar\Gravity,  mx, my, clicked)
        y = Composer::editableRow(self, panelX, panelW, y, "Weather link", "zone", h, "weather_link", Ar\WeatherLink$, mx, my, clicked)

        // Counts
        Local portals% = 0
        Local spawns% = 0
        Local triggers% = 0
        Local waypoints% = 0
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
        For i = 0 To 1999
            If Ar\WaypointX#[i] <> 0.0 Or Ar\WaypointZ#[i] <> 0.0 Then waypoints = waypoints + 1
        Next

        y = Composer::sectionHeader(self, panelX, panelW, y, "Contents")
        y = Composer::row(self, panelX, panelW, y, "Portals",   Str(portals))
        y = Composer::row(self, panelX, panelW, y, "Spawns",    Str(spawns))
        y = Composer::row(self, panelX, panelW, y, "Triggers",  Str(triggers))
        y = Composer::row(self, panelX, panelW, y, "Waypoints", Str(waypoints))

        // Weather -- 4 chance slots (clear / rain / snow / etc, project-specific)
        y = Composer::sectionHeader(self, panelX, panelW, y, "Weather chances")
        Local wi%
        For wi = 0 To 3
            y = Composer::editableIntRow(self, panelX, panelW, y, "Slot " + Str(wi), "zone", h, "weather_" + Str(wi), Ar\WeatherChance[wi], mx, my, clicked)
        Next

        // Scripts -- always editable
        y = Composer::sectionHeader(self, panelX, panelW, y, "Scripts")
        y = Composer::scriptStringRow(self, panelX, panelW, y, "Entry", "zone", h, "entry_script", Ar\EntryScript$, mx, my, clicked, rightClicked)
        y = Composer::scriptStringRow(self, panelX, panelW, y, "Exit",  "zone", h, "exit_script",  Ar\ExitScript$,  mx, my, clicked, rightClicked)

        // Portals / Triggers / Spawns -- always render the section header
        // (with "+ New" button) even when empty, so designers can add
        // the first one. Each existing sub-entity sub-section gets a
        // delete button on its header row.
        Local sectY%

        sectY = y
        y = Composer::sectionHeader(self, panelX, panelW, y, "Portals")
        If Composer::addNewButton(self, panelX, panelW, sectY, mx, my, clicked) = True
            If Composer::zoneAddPortal(self, Ar) = True
                Composer::markDirtyForKind(self, "zone")
                WorldCache_Invalidate()
                Toast_Show("Added portal to " + Ar\Name$, "success")
            Else
                Toast_Show("No empty portal slots", "warning")
            EndIf
        EndIf
        y = Composer::renderZonePortals(self, panelX, panelW, y, Ar, h, mx, my, clicked, rightClicked)

        sectY = y
        y = Composer::sectionHeader(self, panelX, panelW, y, "Triggers")
        If Composer::addNewButton(self, panelX, panelW, sectY, mx, my, clicked) = True
            If Composer::zoneAddTrigger(self, Ar) = True
                Composer::markDirtyForKind(self, "zone")
                WorldCache_Invalidate()
                Toast_Show("Added trigger to " + Ar\Name$, "success")
            Else
                Toast_Show("No empty trigger slots", "warning")
            EndIf
        EndIf
        y = Composer::renderZoneTriggers(self, panelX, panelW, y, Ar, h, mx, my, clicked, rightClicked)

        sectY = y
        y = Composer::sectionHeader(self, panelX, panelW, y, "Spawns")
        If Composer::addNewButton(self, panelX, panelW, sectY, mx, my, clicked) = True
            If Composer::zoneAddSpawn(self, Ar) = True
                Composer::markDirtyForKind(self, "zone")
                WorldCache_Invalidate()
                Toast_Show("Added spawn to " + Ar\Name$, "success")
            Else
                Toast_Show("No empty spawn slots", "warning")
            EndIf
        EndIf
        y = Composer::renderZoneSpawns(self, panelX, panelW, y, Ar, h, mx, my, clicked, rightClicked)

        // Reverse references -- "Inbound portals" -- walk every other
        // Area's 100-slot portal table for any portal targeting THIS
        // zone (matched by PortalLinkArea$ = Ar\Name$). Closes the
        // design's bidirectional-thread gap for zones. Designer asking
        // "what zones lead here?" previously had to grep every other
        // zone's portal list; now it's a click.
        y = Composer::sectionHeader(self, panelX, panelW, y, "Inbound portals")
        Local inboundCount% = 0
        Local otherZone.Area = Null
        Local portIdx% = 0
        For otherZone = Each Area
            If otherZone <> Ar
                For portIdx = 0 To 99
                    If otherZone\PortalName$[portIdx] <> "" And Lower$(otherZone\PortalLinkArea$[portIdx]) = Lower$(Ar\Name$)
                        If inboundCount < 50
                            // Label with the source portal name so the
                            // designer can distinguish multiple portals
                            // from the same source zone.
                            y = Composer::chipRow(self, panelX, panelW, y, otherZone\PortalName$[portIdx], "zone", Handle(otherZone), mx, my, clicked, rightClicked, "")
                        EndIf
                        inboundCount = inboundCount + 1
                    EndIf
                Next
            EndIf
        Next
        If inboundCount = 0
            If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                LoomText(panelX + CMP_PAD, y + 4, "(no inbound portals)", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
            EndIf
            y = y + CMP_ROW_H
        EndIf
        If inboundCount > 50
            If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                LoomText(panelX + CMP_PAD, y + 4, "(+ " + Str(inboundCount - 50) + " more)", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
            EndIf
            y = y + CMP_ROW_H
        EndIf

        Composer::recordContentBottom(self, y)
    End Method


    // -------------------------------------------------------------------------
    // zoneAddPortal -- find first empty PortalName$ slot, seed with sensible
    // defaults. Returns True if added, False if no slots available.
    // -------------------------------------------------------------------------
    Method zoneAddPortal%(Ar.Area)
        Local i%
        For i = 0 To 99
            If Ar\PortalName$[i] = ""
                Ar\PortalName$[i]      = "New portal " + Str(i)
                Ar\PortalLinkArea$[i]  = Ar\Name$    ; loops back to self until designer changes it
                Ar\PortalLinkName$[i]  = ""
                Ar\PortalX#[i]         = 0.0
                Ar\PortalY#[i]         = 0.0
                Ar\PortalZ#[i]         = 0.0
                Ar\PortalSize#[i]      = 5.0
                Ar\PortalYaw#[i]       = 0.0
                Return True
            EndIf
        Next
        Return False
    End Method


    // -------------------------------------------------------------------------
    // zoneAddTrigger -- find first empty TriggerScript$ slot, seed defaults.
    // -------------------------------------------------------------------------
    Method zoneAddTrigger%(Ar.Area)
        Local i%
        For i = 0 To 149
            If Ar\TriggerScript$[i] = ""
                Ar\TriggerScript$[i] = "New trigger"
                Ar\TriggerMethod$[i] = ""
                Ar\TriggerX#[i]      = 0.0
                Ar\TriggerY#[i]      = 0.0
                Ar\TriggerZ#[i]      = 0.0
                Ar\TriggerSize#[i]   = 5.0
                Return True
            EndIf
        Next
        Return False
    End Method


    // -------------------------------------------------------------------------
    // zoneAddSpawn -- find first empty SpawnActor (=0) slot, seed defaults.
    // SpawnActor defaults to the first defined actor ID (so the slot is
    // immediately valid -- a Spawn with SpawnActor=0 is treated as empty).
    // -------------------------------------------------------------------------
    Method zoneAddSpawn%(Ar.Area)
        Local seedActor% = Composer::firstDefinedActorID(self)
        Local i%
        For i = 0 To 999
            If Ar\SpawnActor[i] = 0
                Ar\SpawnActor[i]        = seedActor
                Ar\SpawnWaypoint[i]     = 0
                Ar\SpawnSize#[i]        = 5.0
                Ar\SpawnRange#[i]       = 100.0
                Ar\SpawnFrequency[i]    = 30000
                Ar\SpawnMax[i]          = 1
                Ar\SpawnScript$[i]      = ""
                Ar\SpawnActorScript$[i] = ""
                Ar\SpawnDeathScript$[i] = ""
                Return True
            EndIf
        Next
        Return False
    End Method


    // -------------------------------------------------------------------------
    // firstDefinedActorID -- helper for seeding a new spawn's actor ref.
    // Returns the lowest defined actor ID, or 0 if there are no actors.
    // -------------------------------------------------------------------------
    Method firstDefinedActorID%()
        Local i%
        For i = 1 To 65534
            If ActorList(i) <> Null Then Return i
        Next
        Return 0
    End Method


    // -------------------------------------------------------------------------
    // renderZonePortals -- per-portal sub-section with link chip + 6 coord
    // fields. Empty PortalName$ slots are skipped.
    // -------------------------------------------------------------------------
    Method renderZonePortals%(panelX%, panelW%, y%, Ar.Area, h%, mx%, my%, clicked%, rightClicked%)
        Local p%
        For p = 0 To 99
            If Ar\PortalName$[p] <> ""
                // Capture pre-scroll Y anchor so the zone-viewport pick
                // can scroll the composer to this portal's section.
                self\zoneAnchorPortal[p] = y + self\scrollOffset
                // Sub-header + delete button
                If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                    LoomText(panelX + CMP_PAD, y + 4, "Portal " + Str(p), LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
                    // Header is on-screen -> tell the viewport to highlight
                    // this portal marker. Last-rendered-visible wins, so
                    // the user's scroll position naturally drives focus.
                    LoomZoneHighlightKind$ = "portal"
                    LoomZoneHighlightIdx   = p
                EndIf
                If Composer::subDeleteButton(self, panelX, panelW, y, mx, my, clicked) = True
                    Ar\PortalName$[p]     = ""
                    Ar\PortalLinkArea$[p] = ""
                    Ar\PortalLinkName$[p] = ""
                    Composer::markDirtyForKind(self, "zone")
                    WorldCache_Invalidate()
                    Toast_Show("Deleted portal " + Str(p), "danger")
                    Return y + CMP_ROW_H   ; bail; the index is gone, restart on next frame
                EndIf
                y = y + CMP_ROW_H

                y = Composer::editableRow(self, panelX, panelW, y, "Name",  "zone", h, "portal_name_" + Str(p), Ar\PortalName$[p], mx, my, clicked)

                // Link chip (existing flow) -- right-click swaps via picker
                Local targetHandle% = Composer::findZoneByName(self, Ar\PortalLinkArea$[p])
                If targetHandle <> 0
                    y = Composer::chipRow(self, panelX, panelW, y, "Target", "zone", targetHandle, mx, my, clicked, rightClicked, "portal_" + Str(p))
                Else
                    If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                        LoomText(panelX + CMP_PAD, y + 4, "Target", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
                        Local tgt$ = Ar\PortalLinkArea$[p]
                        If tgt = "" Then tgt = "(no target)"
                        LoomText(panelX + CMP_PAD + 120, y + 4, tgt, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
                    EndIf
                    y = y + CMP_ROW_H
                EndIf

                y = Composer::editableFloatRow(self, panelX, panelW, y, "X",    "zone", h, "portal_x_"    + Str(p), Ar\PortalX#[p],    mx, my, clicked)
                y = Composer::editableFloatRow(self, panelX, panelW, y, "Y",    "zone", h, "portal_y_"    + Str(p), Ar\PortalY#[p],    mx, my, clicked)
                y = Composer::editableFloatRow(self, panelX, panelW, y, "Z",    "zone", h, "portal_z_"    + Str(p), Ar\PortalZ#[p],    mx, my, clicked)
                y = Composer::editableFloatRow(self, panelX, panelW, y, "Size", "zone", h, "portal_size_" + Str(p), Ar\PortalSize#[p], mx, my, clicked)
                y = Composer::editableFloatRow(self, panelX, panelW, y, "Yaw",  "zone", h, "portal_yaw_"  + Str(p), Ar\PortalYaw#[p],  mx, my, clicked)

                y = y + 4
            EndIf
        Next
        Return y
    End Method


    // -------------------------------------------------------------------------
    // renderZoneTriggers -- per-trigger sub-section with X/Y/Z/Size + Script/
    // Method. Empty TriggerScript$ slots are skipped.
    // -------------------------------------------------------------------------
    Method renderZoneTriggers%(panelX%, panelW%, y%, Ar.Area, h%, mx%, my%, clicked%, rightClicked%)
        Local t%
        For t = 0 To 149
            If Ar\TriggerScript$[t] <> ""
                self\zoneAnchorTrigger[t] = y + self\scrollOffset
                If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                    LoomText(panelX + CMP_PAD, y + 4, "Trigger " + Str(t), LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
                    LoomZoneHighlightKind$ = "trigger"
                    LoomZoneHighlightIdx   = t
                EndIf
                If Composer::subDeleteButton(self, panelX, panelW, y, mx, my, clicked) = True
                    Ar\TriggerScript$[t] = ""
                    Ar\TriggerMethod$[t] = ""
                    Composer::markDirtyForKind(self, "zone")
                    WorldCache_Invalidate()
                    Toast_Show("Deleted trigger " + Str(t), "danger")
                    Return y + CMP_ROW_H
                EndIf
                y = y + CMP_ROW_H

                y = Composer::editableFloatRow(self, panelX, panelW, y, "X",    "zone", h, "trigger_x_"    + Str(t), Ar\TriggerX#[t],    mx, my, clicked)
                y = Composer::editableFloatRow(self, panelX, panelW, y, "Y",    "zone", h, "trigger_y_"    + Str(t), Ar\TriggerY#[t],    mx, my, clicked)
                y = Composer::editableFloatRow(self, panelX, panelW, y, "Z",    "zone", h, "trigger_z_"    + Str(t), Ar\TriggerZ#[t],    mx, my, clicked)
                y = Composer::editableFloatRow(self, panelX, panelW, y, "Size", "zone", h, "trigger_size_" + Str(t), Ar\TriggerSize#[t], mx, my, clicked)
                y = Composer::scriptStringRow(self,  panelX, panelW, y, "Script", "zone", h, "trigger_script_" + Str(t), Ar\TriggerScript$[t], mx, my, clicked, rightClicked)
                y = Composer::editableRow(self,      panelX, panelW, y, "Method", "zone", h, "trigger_method_" + Str(t), Ar\TriggerMethod$[t], mx, my, clicked)

                y = y + 4
            EndIf
        Next
        Return y
    End Method


    // -------------------------------------------------------------------------
    // renderZoneSpawns -- per-spawn sub-section with actor chip + waypoint +
    // scripts + frequency/max/range cluster. Empty SpawnActor (=0) slots are
    // skipped. Actor is shown as a clickable thread chip (right-click to swap).
    // -------------------------------------------------------------------------
    Method renderZoneSpawns%(panelX%, panelW%, y%, Ar.Area, h%, mx%, my%, clicked%, rightClicked%)
        Local s%
        For s = 0 To 999
            If Ar\SpawnActor[s] > 0
                self\zoneAnchorSpawn[s] = y + self\scrollOffset
                If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                    LoomText(panelX + CMP_PAD, y + 4, "Spawn " + Str(s), LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
                    LoomZoneHighlightKind$ = "spawn"
                    LoomZoneHighlightIdx   = s
                EndIf
                If Composer::subDeleteButton(self, panelX, panelW, y, mx, my, clicked) = True
                    Ar\SpawnActor[s] = 0
                    Composer::markDirtyForKind(self, "zone")
                    WorldCache_Invalidate()
                    Toast_Show("Deleted spawn " + Str(s), "danger")
                    Return y + CMP_ROW_H
                EndIf
                y = y + CMP_ROW_H

                // Actor reference -- chip, right-click to swap. SpawnActor
                // is an int refID into ActorList, fieldId encodes the spawn idx.
                y = Composer::chipRow(self, panelX, panelW, y, "Actor", "actor", Ar\SpawnActor[s], mx, my, clicked, rightClicked, "spawn_actor_" + Str(s))

                y = Composer::editableIntRow(self,   panelX, panelW, y, "Waypoint",   "zone", h, "spawn_waypoint_" + Str(s), Ar\SpawnWaypoint[s], mx, my, clicked)
                y = Composer::editableFloatRow(self, panelX, panelW, y, "Size",       "zone", h, "spawn_size_"     + Str(s), Ar\SpawnSize#[s],    mx, my, clicked)
                y = Composer::editableFloatRow(self, panelX, panelW, y, "Range",      "zone", h, "spawn_range_"    + Str(s), Ar\SpawnRange#[s],   mx, my, clicked)
                y = Composer::editableIntRow(self,   panelX, panelW, y, "Frequency",  "zone", h, "spawn_freq_"     + Str(s), Ar\SpawnFrequency[s], mx, my, clicked)
                y = Composer::editableIntRow(self,   panelX, panelW, y, "Max",        "zone", h, "spawn_max_"      + Str(s), Ar\SpawnMax[s],       mx, my, clicked)
                y = Composer::scriptStringRow(self,  panelX, panelW, y, "Script",     "zone", h, "spawn_script_"   + Str(s), Ar\SpawnScript$[s],   mx, my, clicked, rightClicked)
                y = Composer::scriptStringRow(self,  panelX, panelW, y, "Actor scr",  "zone", h, "spawn_ascript_"  + Str(s), Ar\SpawnActorScript$[s], mx, my, clicked, rightClicked)
                y = Composer::scriptStringRow(self,  panelX, panelW, y, "Death scr",  "zone", h, "spawn_dscript_"  + Str(s), Ar\SpawnDeathScript$[s], mx, my, clicked, rightClicked)

                y = y + 4
            EndIf
        Next
        Return y
    End Method


    Method renderFaction(panelX%, bodyY%, panelW%, bodyH%, mx%, my%, clicked%, rightClicked%)
        Local idx% = self\threads\focusID
        If idx < 0 Or idx > 99 Then Return

        Local y% = bodyY
        y = Composer::editableRow(self, panelX, panelW, y, "Name",  "faction", idx, "name", FactionNames$(idx), mx, my, clicked)
        y = Composer::row(self, panelX, panelW, y, "Index", Str(idx))

        // Members -- every actor whose DefaultFaction matches. Each
        // renders as an actor chip. The pre-scroll fit-cap dropped
        // members past the panel bottom; with scroll the cap is gone
        // (chipRow's own canPaintRow gate skips off-screen rows).
        y = Composer::sectionHeader(self, panelX, panelW, y, "Members")

        Local memberCount% = 0
        For Ac.Actor = Each Actor
            If Ac\DefaultFaction = idx
                // Members are a back-reference; no field on the focused
                // faction to edit via this chip, so editFieldId = "".
                y = Composer::chipRow(self, panelX, panelW, y, "", "actor", Ac\ID, mx, my, clicked, rightClicked, "")
                memberCount = memberCount + 1
            EndIf
        Next

        If memberCount = 0
            If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                LoomText(panelX + CMP_PAD, y + 4, "(no members)", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
            EndIf
            y = y + CMP_ROW_H
        EndIf

        // Relations -- per-other-faction integer rating. This faction's
        // view of every OTHER defined faction. Editing one cell only
        // affects this faction's outbound rating; the other direction
        // is editable from that faction's own composer view.
        //
        // Convention (matches GUE): 0 = friendly, 1000 = hostile, 500 =
        // neutral. The byte storage clamps 0..255 on disk per
        // SaveFactions; in-memory we accept the same range.
        y = Composer::sectionHeader(self, panelX, panelW, y, "Relations (this -> other)")
        Local relCount% = 0
        Local j%
        For j = 0 To 99
            If FactionNames$(j) <> "" And j <> idx
                Local fid$ = "rel_" + Str(j)
                y = Composer::editableIntRow(self, panelX, panelW, y, FactionNames$(j), "faction", idx, fid, FactionDefaultRatings(idx, j), mx, my, clicked)
                relCount = relCount + 1
            EndIf
        Next
        If relCount = 0
            If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                LoomText(panelX + CMP_PAD, y + 4, "(no other factions)", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
            EndIf
            y = y + CMP_ROW_H
        EndIf

        Composer::recordContentBottom(self, y)
    End Method


    Method renderAnimSet(panelX%, bodyY%, panelW%, bodyH%, mx%, my%, clicked%, rightClicked%)
        Local targetID% = self\threads\focusID

        // AnimSet is iterated, not indexed -- walk to find.
        Local A.AnimSet = Null
        For As.AnimSet = Each AnimSet
            If As\ID = targetID Then A = As : Exit
        Next
        If A = Null Then Return

        Local y% = bodyY
        y = Composer::editableRow(self, panelX, panelW, y, "Name", "animset", A\ID, "name", A\Name$, mx, my, clicked)
        y = Composer::row(self, panelX, panelW, y, "ID",   Str(A\ID))

        Local clips% = 0
        Local i% = 0
        For i = 0 To 149
            If A\AnimName$[i] <> "" Then clips = clips + 1
        Next
        y = Composer::row(self, panelX, panelW, y, "Clips", Str(clips))

        y = Composer::sectionHeader(self, panelX, panelW, y, "Used by")
        Local userCount% = 0
        For Ac.Actor = Each Actor
            If Ac\MAnimationSet = targetID Or Ac\FAnimationSet = targetID
                // Back-reference (actors using this anim set); no field
                // on the focused animset to edit via this chip.
                y = Composer::chipRow(self, panelX, panelW, y, "", "actor", Ac\ID, mx, my, clicked, rightClicked, "")
                userCount = userCount + 1
            EndIf
        Next

        If userCount = 0
            If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                LoomText(panelX + CMP_PAD, y + 4, "(no users)", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
            EndIf
            y = y + CMP_ROW_H
        EndIf

        // Per-clip editor. AnimSet has 150 slots; render a sub-section for
        // each clip that's either named OR is a well-known required clip
        // (Anim_Walk = 149, Anim_Run = 148, etc) even if currently empty
        // -- so designers can populate a known-empty slot.
        y = Composer::sectionHeader(self, panelX, panelW, y, "Clips (Name | Start | End | Speed)")
        y = Composer::renderAnimSetClips(self, panelX, panelW, y, A, mx, my, clicked)

        Composer::recordContentBottom(self, y)
    End Method


    // -------------------------------------------------------------------------
    // renderAnimSetClips -- walk 0..149 and render a sub-section per clip
    // that's either defined or known-required. Each clip row group:
    //   [Slot N -- Anim_XYZ]      (header in arcane cyan)
    //   Name:  [editable string]
    //   Start: [int] | End: [int]  (side-by-side via doubleIntRow)
    //   Speed: [float]
    //
    // Well-known anim names (Anim_Walk=149 etc) come from animSlotLabel
    // below. Slots that are both empty AND not well-known are skipped so
    // the panel doesn't show 150 blank entries on a fresh AnimSet.
    // -------------------------------------------------------------------------
    Method renderAnimSetClips%(panelX%, panelW%, y%, A.AnimSet, mx%, my%, clicked%)
        Local i%
        For i = 0 To 149
            Local known$ = Composer::animSlotLabel(self, i)
            Local defined% = (A\AnimName$[i] <> "")
            If defined = True Or known <> ""
                If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                    Local hdr$ = "Slot " + Str(i)
                    If known <> "" Then hdr = hdr + " (" + known + ")"
                    LoomText(panelX + CMP_PAD, y + 4, hdr, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
                EndIf
                y = y + CMP_ROW_H

                y = Composer::editableRow(self, panelX, panelW, y, "Name",  "animset", A\ID, "clip_name_"  + Str(i), A\AnimName$[i],  mx, my, clicked)
                y = Composer::doubleIntRow(self, panelX, panelW, y, "S | E", "animset", A\ID, "clip_start_" + Str(i), A\AnimStart[i], "clip_end_" + Str(i), A\AnimEnd[i], mx, my, clicked)
                y = Composer::editableFloatRow(self, panelX, panelW, y, "Speed", "animset", A\ID, "clip_speed_" + Str(i), A\AnimSpeed#[i], mx, my, clicked)
                y = y + 4
            EndIf
        Next
        Return y
    End Method


    // -------------------------------------------------------------------------
    // animSlotLabel -- returns the friendly name for one of the well-known
    // animation indices (Anim_Walk etc), else "". Used by the AnimSet clip
    // sub-section header so designers see "Slot 149 (Walk)" not just
    // "Slot 149".
    // -------------------------------------------------------------------------
    Method animSlotLabel$(idx%)
        If idx = 149 Then Return "Walk"
        If idx = 148 Then Return "Run"
        If idx = 147 Then Return "Swim idle"
        If idx = 146 Then Return "Swim slow"
        If idx = 145 Then Return "Swim fast"
        If idx = 144 Then Return "Ride idle"
        If idx = 143 Then Return "Ride walk"
        If idx = 142 Then Return "Ride run"
        If idx = 141 Then Return "Attack (default)"
        If idx = 140 Then Return "Attack (right)"
        If idx = 139 Then Return "Attack (2H)"
        If idx = 138 Then Return "Attack (staff)"
        If idx = 137 Then Return "Parry (default)"
        If idx = 136 Then Return "Parry (right)"
        If idx = 135 Then Return "Parry (2H)"
        If idx = 134 Then Return "Parry (staff)"
        If idx = 133 Then Return "Parry (shield)"
        If idx = 132 Then Return "Last hit"
        If idx = 130 Then Return "First hit"
        If idx = 129 Then Return "Last death"
        If idx = 127 Then Return "First death"
        If idx = 126 Then Return "Jump"
        If idx = 125 Then Return "Idle"
        If idx = 124 Then Return "Yawn"
        If idx = 123 Then Return "Look round"
        If idx = 122 Then Return "Sit down"
        If idx = 121 Then Return "Sit idle"
        If idx = 120 Then Return "Stand up"
        If idx = 119 Then Return "Strafe right"
        Return ""
    End Method


    // -------------------------------------------------------------------------
    // renderSettings -- project-level configuration panel. Singleton, not an
    // entity; refID is ignored. Backed by the LoomCfg_* globals in
    // Modules/Loom/Settings.bb; saves go through Loom_SaveSettings (called
    // from SaveAll dispatch when SettingsSaved = False).
    // -------------------------------------------------------------------------
    Method renderSettings(panelX%, bodyY%, panelW%, bodyH%, mx%, my%, clicked%)
        Local y% = bodyY

        y = Composer::sectionHeader(self, panelX, panelW, y, "Identity")
        y = Composer::editableRow(self, panelX, panelW, y, "Game name",    "settings", 0, "game_name",    LoomCfg_GameName$,    mx, my, clicked)
        y = Composer::editableRow(self, panelX, panelW, y, "Update URL",   "settings", 0, "update_game",  LoomCfg_UpdateGame$,  mx, my, clicked)
        y = Composer::editableRow(self, panelX, panelW, y, "Music URL",    "settings", 0, "update_music", LoomCfg_UpdateMusic$, mx, my, clicked)

        y = Composer::sectionHeader(self, panelX, panelW, y, "Hosts")
        y = Composer::editableRow(self, panelX, panelW, y, "Server host",  "settings", 0, "server_host",  LoomCfg_ServerHost$,  mx, my, clicked)
        y = Composer::editableRow(self, panelX, panelW, y, "Update host",  "settings", 0, "update_host",  LoomCfg_UpdateHost$,  mx, my, clicked)

        y = Composer::sectionHeader(self, panelX, panelW, y, "Network / runtime")
        y = Composer::editableIntRow(self, panelX, panelW, y, "Server port",    "settings", 0, "server_port",     LoomCfg_ServerPort,       mx, my, clicked)
        y = Composer::toggleRow(self,      panelX, panelW, y, "Hide nametags",  "settings", 0, "hide_nametags",   LoomCfg_HideNametags,     mx, my, clicked)
        y = Composer::toggleRow(self,      panelX, panelW, y, "No collisions",  "settings", 0, "disable_collisions", LoomCfg_DisableCollisions, mx, my, clicked)
        y = Composer::editableIntRow(self, panelX, panelW, y, "View mode",      "settings", 0, "view_mode",       LoomCfg_ViewMode,         mx, my, clicked)
        y = Composer::toggleRow(self,      panelX, panelW, y, "Require memo",   "settings", 0, "require_memo",    LoomCfg_RequireMemorise,  mx, my, clicked)

        y = Composer::sectionHeader(self, panelX, panelW, y, "Speech bubbles")
        y = Composer::toggleRow(self,      panelX, panelW, y, "Use bubbles",  "settings", 0, "use_bubbles", LoomCfg_UseBubbles, mx, my, clicked)
        y = Composer::editableIntRow(self, panelX, panelW, y, "Bubble R",     "settings", 0, "bubbles_r",   LoomCfg_BubblesR,   mx, my, clicked)
        y = Composer::editableIntRow(self, panelX, panelW, y, "Bubble G",     "settings", 0, "bubbles_g",   LoomCfg_BubblesG,   mx, my, clicked)
        y = Composer::editableIntRow(self, panelX, panelW, y, "Bubble B",     "settings", 0, "bubbles_b",   LoomCfg_BubblesB,   mx, my, clicked)

        y = Composer::sectionHeader(self, panelX, panelW, y, "Currency tiers")
        y = Composer::editableRow(self,    panelX, panelW, y, "Tier 1 name",    "settings", 0, "money1_name", LoomCfg_Money1$, mx, my, clicked)
        y = Composer::editableRow(self,    panelX, panelW, y, "Tier 2 name",    "settings", 0, "money2_name", LoomCfg_Money2$, mx, my, clicked)
        y = Composer::editableIntRow(self, panelX, panelW, y, "Tier 2 = N x 1", "settings", 0, "money2x",     LoomCfg_Money2x, mx, my, clicked)
        y = Composer::editableRow(self,    panelX, panelW, y, "Tier 3 name",    "settings", 0, "money3_name", LoomCfg_Money3$, mx, my, clicked)
        y = Composer::editableIntRow(self, panelX, panelW, y, "Tier 3 = N x 2", "settings", 0, "money3x",     LoomCfg_Money3x, mx, my, clicked)
        y = Composer::editableRow(self,    panelX, panelW, y, "Tier 4 name",    "settings", 0, "money4_name", LoomCfg_Money4$, mx, my, clicked)
        y = Composer::editableIntRow(self, panelX, panelW, y, "Tier 4 = N x 3", "settings", 0, "money4x",     LoomCfg_Money4x, mx, my, clicked)

        // Damage Types catalog (Damage.dat) -- 20 named slots used by
        // combat damage type lookups. Empty slot N = no damage type N.
        y = Composer::sectionHeader(self, panelX, panelW, y, "Damage types")
        Local di%
        For di = 0 To 19
            y = Composer::editableRow(self, panelX, panelW, y, "Type " + Str(di), "settings", 0, "damage_" + Str(di), DamageTypes$(di), mx, my, clicked)
        Next

        // Attribute Names catalog (Attributes.dat) -- 40 named slots used
        // by per-actor/per-item Attributes\Value[i] indexing. Each slot
        // has Name + IsSkill (skill vs stat) + Hidden (visible to player).
        y = Composer::sectionHeader(self, panelX, panelW, y, "Attribute assignment")
        y = Composer::editableIntRow(self, panelX, panelW, y, "Assignment mode", "settings", 0, "attr_assignment", AttributeAssignment, mx, my, clicked)

        y = Composer::sectionHeader(self, panelX, panelW, y, "Attributes catalog")
        Local ai%
        For ai = 0 To 39
            // Sub-header per slot index
            If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                LoomText(panelX + CMP_PAD, y + 4, "Slot " + Str(ai), LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
            EndIf
            y = y + CMP_ROW_H
            y = Composer::editableRow(self, panelX, panelW, y, "Name",   "settings", 0, "attr_name_" + Str(ai),   AttributeNames$(ai),    mx, my, clicked)
            y = Composer::toggleRow(self,   panelX, panelW, y, "Skill",  "settings", 0, "attr_skill_" + Str(ai),  AttributeIsSkill(ai),   mx, my, clicked)
            y = Composer::toggleRow(self,   panelX, panelW, y, "Hidden", "settings", 0, "attr_hidden_" + Str(ai), AttributeHidden(ai),    mx, my, clicked)
            y = y + 4
        Next

        Composer::recordContentBottom(self, y)
    End Method


    // -------------------------------------------------------------------------
    // renderScript -- preview a .rsl file's content + show every entity
    // that references it. refID is the ScriptFile catalog index from
    // Scripts_Init. Read-only view (no inline editing of script source --
    // designers use an external editor for that).
    //
    // Reverse-ref scanners walk every Area's 5 script-string field
    // families (Entry/Exit/Trigger/SpawnScript/SpawnActorScript/SpawnDeathScript)
    // plus Items.Script + Spells.Script. Each hit emits a chip that
    // focuses the referencing entity. Case-insensitive + extension-tolerant
    // match via Scripts_NormalizeName$.
    // -------------------------------------------------------------------------
    Method renderScript(panelX%, bodyY%, panelW%, bodyH%, mx%, my%, clicked%, rightClicked%)
        Local sf.ScriptFile = Scripts_GetByIndex(self\threads\focusID)
        If sf = Null Then Return

        Local y% = bodyY
        y = Composer::row(self, panelX, panelW, y, "Name",      sf\Name$)
        y = Composer::row(self, panelX, panelW, y, "Path",      sf\FullPath$)
        y = Composer::row(self, panelX, panelW, y, "Size",      Scripts_FormatSize(sf\SizeBytes))
        y = Composer::row(self, panelX, panelW, y, "Lines",     Str(sf\LineCount))

        // -- Source preview (read-only) ---------------------------------------
        // Lazy-loaded on every paint -- the read is cheap (typical script
        // is < 5KB) and avoids stashing potentially-stale content in a
        // cache that would have to invalidate on file change.
        y = Composer::sectionHeader(self, panelX, panelW, y, "Source preview")
        Local content$ = Scripts_GetContent(sf\Name$)
        If content = ""
            If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                LoomText(panelX + CMP_PAD, y + 4, "(could not read file)", LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
            EndIf
            y = y + CMP_ROW_H
        Else
            // Render line-by-line until we run out of content or hit a
            // reasonable preview cap (200 lines). Long scripts get a
            // truncation footer.
            Local maxLines% = 200
            Local lineNum% = 0
            Local cursor% = 1
            Local clen% = Len(content)
            While cursor <= clen And lineNum < maxLines
                Local nl% = cursor
                While nl <= clen And Mid$(content, nl, 1) <> Chr(10)
                    nl = nl + 1
                Wend
                Local L$ = Mid$(content, cursor, nl - cursor)
                // Tabs -> 4 spaces for legibility; LoomText is monospace
                // friendly via the body font.
                L = Replace$(L, Chr(9), "    ")
                If Composer::canPaintRow(self, y, 16) = True
                    // Faded line number gutter (col 4..30), code in
                    // parchment from col 38.
                    LoomText(panelX + CMP_PAD, y + 2, Str(lineNum + 1), LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
                    LoomText(panelX + CMP_PAD + 36, y + 2, L, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
                EndIf
                y = y + 16
                cursor = nl + 1
                lineNum = lineNum + 1
            Wend
            If lineNum >= maxLines And cursor <= clen
                If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                    LoomText(panelX + CMP_PAD, y + 4, "(+ more lines -- open externally to see the rest)", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
                EndIf
                y = y + CMP_ROW_H
            EndIf
        EndIf

        // -- Reverse references -----------------------------------------------
        // Walk every Zone's 5 script-string field families + every Item +
        // every Spell looking for a normalised-name match. Each hit emits
        // a chip that focuses the referencing entity. Cap each section
        // at 50 to keep the panel scrollable even for a heavily-shared
        // script like "In-game Commands".
        y = Composer::sectionHeader(self, panelX, panelW, y, "Used by")

        Local key$ = Scripts_NormalizeName$(sf\Name$)
        Local hitsTotal% = 0

        // Items
        Local itemHits% = 0
        For It.Item = Each Item
            If It\Script$ <> ""
                If Scripts_NormalizeName$(It\Script$) = key
                    If itemHits < 50
                        y = Composer::chipRow(self, panelX, panelW, y, "Item", "item", It\ID, mx, my, clicked, rightClicked, "")
                    EndIf
                    itemHits = itemHits + 1
                EndIf
            EndIf
        Next

        // Spells
        Local spellHits% = 0
        For Sp.Spell = Each Spell
            If Sp\Script$ <> ""
                If Scripts_NormalizeName$(Sp\Script$) = key
                    If spellHits < 50
                        y = Composer::chipRow(self, panelX, panelW, y, "Spell", "spell", Sp\ID, mx, my, clicked, rightClicked, "")
                    EndIf
                    spellHits = spellHits + 1
                EndIf
            EndIf
        Next

        // Zones -- 5 script families per zone. Emit ONE chip per zone
        // (not one per script slot) and tag the source family in the
        // label so designers see "Trigger / Entry / SpawnAct / SpawnDth".
        Local zoneHits% = 0
        Local zIter.Area = Null
        For zIter = Each Area
            Local label$ = ""
            // EntryScript / ExitScript
            If zIter\EntryScript$ <> "" And Scripts_NormalizeName$(zIter\EntryScript$) = key Then label = label + "Entry "
            If zIter\ExitScript$  <> "" And Scripts_NormalizeName$(zIter\ExitScript$)  = key Then label = label + "Exit "
            // Triggers (150)
            Local trIdx% = 0
            For trIdx = 0 To 149
                If zIter\TriggerScript$[trIdx] <> "" And Scripts_NormalizeName$(zIter\TriggerScript$[trIdx]) = key
                    label = label + "Trigger "
                    trIdx = 149 : Exit   ; one mention per zone is enough
                EndIf
            Next
            // Spawn scripts (1000 each, three families)
            Local spIdx% = 0
            For spIdx = 0 To 999
                If zIter\SpawnScript$[spIdx] <> "" And Scripts_NormalizeName$(zIter\SpawnScript$[spIdx]) = key
                    label = label + "Spawn " : spIdx = 999 : Exit
                EndIf
            Next
            For spIdx = 0 To 999
                If zIter\SpawnActorScript$[spIdx] <> "" And Scripts_NormalizeName$(zIter\SpawnActorScript$[spIdx]) = key
                    label = label + "SpAct " : spIdx = 999 : Exit
                EndIf
            Next
            For spIdx = 0 To 999
                If zIter\SpawnDeathScript$[spIdx] <> "" And Scripts_NormalizeName$(zIter\SpawnDeathScript$[spIdx]) = key
                    label = label + "SpDth " : spIdx = 999 : Exit
                EndIf
            Next
            If label <> ""
                If zoneHits < 50
                    y = Composer::chipRow(self, panelX, panelW, y, label, "zone", Handle(zIter), mx, my, clicked, rightClicked, "")
                EndIf
                zoneHits = zoneHits + 1
            EndIf
        Next

        hitsTotal = itemHits + spellHits + zoneHits
        If hitsTotal = 0
            If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                LoomText(panelX + CMP_PAD, y + 4, "(unused -- safe to delete)", LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B)
            EndIf
            y = y + CMP_ROW_H
        EndIf
        If Composer::canPaintRow(self, y, CMP_ROW_H) = True
            LoomText(panelX + CMP_PAD, y + 4, Str(itemHits) + " item(s) | " + Str(spellHits) + " spell(s) | " + Str(zoneHits) + " zone(s)", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        EndIf
        y = y + CMP_ROW_H

        Composer::recordContentBottom(self, y)
    End Method


    // -------------------------------------------------------------------------
    // renderTexture -- composer view for a focused texture from the
    // TextureCatalog. Shows metadata + large preview + every entity
    // that references this texture ID across the project.
    //
    // Reverse-ref scanners walk:
    //   - Item\ThumbnailTexID
    //   - Spell\ThumbnailTexID
    //   - Actor\BloodTexID
    //   - Actor\MaleFaceIDs[0..4] / FemaleFaceIDs[0..4]
    //   - Actor\MaleBodyIDs[0..4] / FemaleBodyIDs[0..4]
    // Each match emits a chip with a slot-tagged label so designers
    // see which appearance slot referenced this asset.
    // -------------------------------------------------------------------------
    Method renderTexture(panelX%, bodyY%, panelW%, bodyH%, mx%, my%, clicked%, rightClicked%)
        Local te.TextureEntry = Textures_GetByIndex(self\threads\focusID)
        If te = Null Then Return

        Local y% = bodyY
        y = Composer::row(self, panelX, panelW, y, "Filename", te\Filename$)
        y = Composer::row(self, panelX, panelW, y, "ID",       Str(te\ID))
        y = Composer::row(self, panelX, panelW, y, "Flags",    Str(te\Flags))
        y = Composer::row(self, panelX, panelW, y, "Path",     "Data\Textures\" + te\Filename$)

        // Large preview -- reuse the 64x64 cached scaled image. Future
        // iter could swap to a bigger size cache for full-size view.
        y = Composer::sectionHeader(self, panelX, panelW, y, "Preview")
        If Composer::canPaintRow(self, y, 80) = True
            Loom_DrawThumbnailLarge(te\ID, panelX + CMP_PAD, y)
        EndIf
        y = y + 72

        // Reverse references
        y = Composer::sectionHeader(self, panelX, panelW, y, "Used by")
        Local itemHits% = 0
        Local spellHits% = 0
        Local actorHits% = 0

        // Items
        For It.Item = Each Item
            If It\ThumbnailTexID = te\ID
                If itemHits < 50
                    y = Composer::chipRow(self, panelX, panelW, y, "Thumbnail", "item", It\ID, mx, my, clicked, rightClicked, "")
                EndIf
                itemHits = itemHits + 1
            EndIf
        Next

        // Spells
        For Sp.Spell = Each Spell
            If Sp\ThumbnailTexID = te\ID
                If spellHits < 50
                    y = Composer::chipRow(self, panelX, panelW, y, "Thumbnail", "spell", Sp\ID, mx, my, clicked, rightClicked, "")
                EndIf
                spellHits = spellHits + 1
            EndIf
        Next

        // Actors -- iterate the array-based ActorList; check each
        // appearance-array slot for a match. One chip per actor with
        // a slot label so multiple slot hits don't multiply chips.
        Local actorIdx% = 0
        For actorIdx = 0 To 65535
            Local Ac.Actor = ActorList(actorIdx)
            If Ac <> Null
                Local slotLabel$ = ""
                If Ac\BloodTexID = te\ID Then slotLabel = slotLabel + "Blood "
                Local ai% = 0
                For ai = 0 To 4
                    If Ac\MaleFaceIDs[ai]   = te\ID Then slotLabel = slotLabel + "MFace[" + Str(ai) + "] "
                    If Ac\FemaleFaceIDs[ai] = te\ID Then slotLabel = slotLabel + "FFace[" + Str(ai) + "] "
                    If Ac\MaleBodyIDs[ai]   = te\ID Then slotLabel = slotLabel + "MBody[" + Str(ai) + "] "
                    If Ac\FemaleBodyIDs[ai] = te\ID Then slotLabel = slotLabel + "FBody[" + Str(ai) + "] "
                Next
                If slotLabel <> ""
                    If actorHits < 50
                        y = Composer::chipRow(self, panelX, panelW, y, slotLabel, "actor", Ac\ID, mx, my, clicked, rightClicked, "")
                    EndIf
                    actorHits = actorHits + 1
                EndIf
            EndIf
        Next

        Local totalHits% = itemHits + spellHits + actorHits
        If totalHits = 0
            If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                LoomText(panelX + CMP_PAD, y + 4, "(unused -- safe to remove from the catalog)", LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B)
            EndIf
            y = y + CMP_ROW_H
        EndIf
        If Composer::canPaintRow(self, y, CMP_ROW_H) = True
            LoomText(panelX + CMP_PAD, y + 4, Str(itemHits) + " item(s) | " + Str(spellHits) + " spell(s) | " + Str(actorHits) + " actor(s)", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        EndIf
        y = y + CMP_ROW_H

        Composer::recordContentBottom(self, y)
    End Method


    // -------------------------------------------------------------------------
    // renderMesh -- composer view for a focused mesh from the
    // MeshCatalog. Shows metadata + 3D orbit/zoom preview (via the
    // existing Loom_DrawMeshPreview widget from iter 40) + every
    // entity that references this mesh ID.
    //
    // Reverse-ref scanners walk:
    //   - Item\MMeshID / Item\FMeshID
    //   - Actor\MeshIDs[0..7] (base + 6 gubbins)
    //   - Actor\BeardIDs[0..4]
    //   - Actor\MaleHairIDs[0..4] / FemaleHairIDs[0..4]
    // -------------------------------------------------------------------------
    Method renderMesh(panelX%, bodyY%, panelW%, bodyH%, mx%, my%, clicked%, rightClicked%)
        Local mh.MeshEntry = Meshes_GetByIndex(self\threads\focusID)
        If mh = Null Then Return

        Local y% = bodyY
        y = Composer::row(self, panelX, panelW, y, "Filename",  mh\Filename$)
        y = Composer::row(self, panelX, panelW, y, "ID",        Str(mh\ID))
        y = Composer::row(self, panelX, panelW, y, "Animated",  Composer::boolLabel(self, mh\IsAnim))
        y = Composer::row(self, panelX, panelW, y, "Path",      "Data\Meshes\" + mh\Filename$)

        // No 3D preview here (removed: full-backbuffer bleed + wheel
        // conflict with the body scroll -- see renderActor / ADR 004).
        // The catalog row + "Used by" reverse-refs are the value here.

        // Reverse refs
        y = Composer::sectionHeader(self, panelX, panelW, y, "Used by")
        Local itemHits% = 0
        Local actorHits% = 0

        For It.Item = Each Item
            If It\MMeshID = mh\ID Or It\FMeshID = mh\ID
                If itemHits < 50
                    Local label$ = ""
                    If It\MMeshID = mh\ID Then label = label + "Male "
                    If It\FMeshID = mh\ID Then label = label + "Female "
                    y = Composer::chipRow(self, panelX, panelW, y, label, "item", It\ID, mx, my, clicked, rightClicked, "")
                EndIf
                itemHits = itemHits + 1
            EndIf
        Next

        // Actors -- check all 8 MeshIDs + beard + hair slot families
        Local actorIdx% = 0
        For actorIdx = 0 To 65535
            Local Ac.Actor = ActorList(actorIdx)
            If Ac <> Null
                Local slotLabel$ = ""
                Local mi% = 0
                For mi = 0 To 7
                    If Ac\MeshIDs[mi] = mh\ID Then slotLabel = slotLabel + "Mesh[" + Str(mi) + "] "
                Next
                Local bi% = 0
                For bi = 0 To 4
                    If Ac\BeardIDs[bi]      = mh\ID Then slotLabel = slotLabel + "Beard[" + Str(bi) + "] "
                    If Ac\MaleHairIDs[bi]   = mh\ID Then slotLabel = slotLabel + "MHair[" + Str(bi) + "] "
                    If Ac\FemaleHairIDs[bi] = mh\ID Then slotLabel = slotLabel + "FHair[" + Str(bi) + "] "
                Next
                If slotLabel <> ""
                    If actorHits < 50
                        y = Composer::chipRow(self, panelX, panelW, y, slotLabel, "actor", Ac\ID, mx, my, clicked, rightClicked, "")
                    EndIf
                    actorHits = actorHits + 1
                EndIf
            EndIf
        Next

        Local totalHits% = itemHits + actorHits
        If totalHits = 0
            If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                LoomText(panelX + CMP_PAD, y + 4, "(unused -- safe to remove from the catalog)", LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B)
            EndIf
            y = y + CMP_ROW_H
        EndIf
        If Composer::canPaintRow(self, y, CMP_ROW_H) = True
            LoomText(panelX + CMP_PAD, y + 4, Str(itemHits) + " item(s) | " + Str(actorHits) + " actor(s)", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        EndIf
        y = y + CMP_ROW_H

        Composer::recordContentBottom(self, y)
    End Method


    // -------------------------------------------------------------------------
    // renderSound -- composer view for a focused sound from the
    // SoundCatalog. Shows metadata + an in-place Play button + reverse
    // refs from every actor's MSpeechIDs / FSpeechIDs slot families.
    //
    // The Play button is the killer feature here: GUE has no in-place
    // audition for sounds. Sounds_Play uses the engine's GetSound +
    // PlaySound path so the cache stays warm across rapid re-plays.
    // -------------------------------------------------------------------------
    Method renderSound(panelX%, bodyY%, panelW%, bodyH%, mx%, my%, clicked%, rightClicked%)
        Local sd.SoundEntry = Sounds_GetByIndex(self\threads\focusID)
        If sd = Null Then Return

        Local y% = bodyY
        y = Composer::row(self, panelX, panelW, y, "Filename", sd\Filename$)
        y = Composer::row(self, panelX, panelW, y, "ID",       Str(sd\ID))
        y = Composer::row(self, panelX, panelW, y, "Spatial",  Composer::boolLabel(self, sd\Is3D))
        y = Composer::row(self, panelX, panelW, y, "Path",     "Data\Sounds\" + sd\Filename$)

        // Play button -- audition the sound. Caller-owned cooldown
        // would be nicer but PlaySound is fire-and-forget and the
        // user rarely spam-clicks; let the engine handle channel
        // assignment.
        y = Composer::sectionHeader(self, panelX, panelW, y, "Audition")
        Local btnW% = 80
        Local btnH% = 26
        Local btnX% = panelX + CMP_PAD
        Local btnY% = y
        Local btnHovered% = (mx >= btnX And mx < btnX + btnW And my >= btnY And my < btnY + btnH)
        If Composer::canPaintRow(self, y, btnH) = True
            If btnHovered = True
                LoomFill(btnX, btnY, btnW, btnH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
                LoomBorder(btnX, btnY, btnW, btnH, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
                LoomText(btnX + 18, btnY + 6, "> play", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
            Else
                LoomFill(btnX, btnY, btnW, btnH, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
                LoomBorder(btnX, btnY, btnW, btnH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
                LoomText(btnX + 18, btnY + 6, "> play", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            EndIf
        EndIf
        If btnHovered = True And clicked = True
            Sounds_Play(sd\ID)
        EndIf
        y = y + btnH + 4

        // Reverse refs -- only actor speech slots reference sounds.
        y = Composer::sectionHeader(self, panelX, panelW, y, "Used by")
        Local actorHits% = 0
        Local actorIdx% = 0
        For actorIdx = 0 To 65535
            Local Ac.Actor = ActorList(actorIdx)
            If Ac <> Null
                Local slotLabel$ = ""
                Local si% = 0
                For si = 0 To 15
                    If Ac\MSpeechIDs[si] = sd\ID Then slotLabel = slotLabel + "MSpeech[" + Str(si) + "] "
                    If Ac\FSpeechIDs[si] = sd\ID Then slotLabel = slotLabel + "FSpeech[" + Str(si) + "] "
                Next
                If slotLabel <> ""
                    If actorHits < 50
                        y = Composer::chipRow(self, panelX, panelW, y, slotLabel, "actor", Ac\ID, mx, my, clicked, rightClicked, "")
                    EndIf
                    actorHits = actorHits + 1
                EndIf
            EndIf
        Next

        If actorHits = 0
            If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                LoomText(panelX + CMP_PAD, y + 4, "(unused -- safe to remove from the catalog)", LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B)
            EndIf
            y = y + CMP_ROW_H
        EndIf
        If Composer::canPaintRow(self, y, CMP_ROW_H) = True
            LoomText(panelX + CMP_PAD, y + 4, Str(actorHits) + " actor(s)", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        EndIf
        y = y + CMP_ROW_H

        Composer::recordContentBottom(self, y)
    End Method


    // -------------------------------------------------------------------------
    // renderMusic -- composer view for a focused music track. Shape
    // mirrors renderSound but without the "Used by" section -- music
    // isn't statically referenced from data Loom edits. The Play
    // button still earns its keep: designers cataloging their music
    // library no longer have to launch the full Server + Client to
    // hear a specific track.
    // -------------------------------------------------------------------------
    Method renderMusic(panelX%, bodyY%, panelW%, bodyH%, mx%, my%, clicked%, rightClicked%)
        Local mu.MusicEntry = Music_GetByIndex(self\threads\focusID)
        If mu = Null Then Return

        Local y% = bodyY
        y = Composer::row(self, panelX, panelW, y, "Filename", mu\Filename$)
        y = Composer::row(self, panelX, panelW, y, "ID",       Str(mu\ID))
        y = Composer::row(self, panelX, panelW, y, "Path",     "Data\Music\" + mu\Filename$)

        y = Composer::sectionHeader(self, panelX, panelW, y, "Audition")
        Local btnW% = 80
        Local btnH% = 26
        Local btnX% = panelX + CMP_PAD
        Local btnY% = y
        Local btnHovered% = (mx >= btnX And mx < btnX + btnW And my >= btnY And my < btnY + btnH)
        If Composer::canPaintRow(self, y, btnH) = True
            If btnHovered = True
                LoomFill(btnX, btnY, btnW, btnH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
                LoomBorder(btnX, btnY, btnW, btnH, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
                LoomText(btnX + 18, btnY + 6, "> play", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
            Else
                LoomFill(btnX, btnY, btnW, btnH, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
                LoomBorder(btnX, btnY, btnW, btnH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
                LoomText(btnX + 18, btnY + 6, "> play", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            EndIf
        EndIf
        If btnHovered = True And clicked = True
            Music_Play(self\threads\focusID)
        EndIf
        y = y + btnH + 4

        // No reverse refs section -- music isn't referenced from data
        // Loom edits. The composer footer notes this so designers don't
        // wonder where the "Used by" section went.
        If Composer::canPaintRow(self, y, CMP_ROW_H) = True
            LoomText(panelX + CMP_PAD, y + 4, "(music tracks have no in-data references)", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
        EndIf
        y = y + CMP_ROW_H

        Composer::recordContentBottom(self, y)
    End Method


    // -------------------------------------------------------------------------
    // renderStats -- read-only project dashboard. Counts entities,
    // assets, issues; highlights top 5 zones by spawn density; surfaces
    // top issue categories. Designed for "open Loom, hit Stats tab, get
    // an immediate read on project health." No editable rows.
    // -------------------------------------------------------------------------
    Method renderStats(panelX%, bodyY%, panelW%, bodyH%, mx%, my%, clicked%)
        Local y% = bodyY

        // Entity counts. Use the same counting walks BrokenRefs and
        // WorldCache use; cheap at typical project scale.
        Local actorCount% = 0
        Local Ac.Actor
        For Ac = Each Actor
            actorCount = actorCount + 1
        Next
        Local itemCount% = 0
        Local It.Item
        For It = Each Item
            itemCount = itemCount + 1
        Next
        Local spellCount% = 0
        Local Sp.Spell
        For Sp = Each Spell
            spellCount = spellCount + 1
        Next
        Local zoneCount% = 0
        Local Ar.Area
        For Ar = Each Area
            zoneCount = zoneCount + 1
        Next
        Local factionCount% = 0
        Local fi% = 0
        For fi = 0 To 99
            If FactionNames$(fi) <> "" Then factionCount = factionCount + 1
        Next
        Local animsetCount% = 0
        Local As.AnimSet
        For As = Each AnimSet
            animsetCount = animsetCount + 1
        Next

        y = Composer::sectionHeader(self, panelX, panelW, y, "Entities")
        y = Composer::row(self, panelX, panelW, y, "Actors",      Str(actorCount))
        y = Composer::row(self, panelX, panelW, y, "Items",       Str(itemCount))
        y = Composer::row(self, panelX, panelW, y, "Spells",      Str(spellCount))
        y = Composer::row(self, panelX, panelW, y, "Zones",       Str(zoneCount))
        y = Composer::row(self, panelX, panelW, y, "Factions",    Str(factionCount))
        y = Composer::row(self, panelX, panelW, y, "Anim Sets",   Str(animsetCount))

        // Asset catalog counts -- come straight from the *_Init module
        // globals (TexturesTotalCount etc.). These already cost nothing
        // to read since they're maintained at boot.
        y = Composer::sectionHeader(self, panelX, panelW, y, "Asset catalogs")
        y = Composer::row(self, panelX, panelW, y, "Scripts",     Str(ScriptsTotalCount)  + " .rsl files")
        y = Composer::row(self, panelX, panelW, y, "Textures",    Str(TexturesTotalCount) + " in Textures.dat")
        y = Composer::row(self, panelX, panelW, y, "Meshes",      Str(MeshesTotalCount)   + " in Meshes.dat")
        y = Composer::row(self, panelX, panelW, y, "Sounds",      Str(SoundsTotalCount)   + " in Sounds.dat")
        y = Composer::row(self, panelX, panelW, y, "Music",       Str(MusicTotalCount)    + " in Music.dat")

        // Top 5 zones by spawn count. Walk Area pool, compute total
        // defined SpawnActor slots per zone, surface the largest.
        y = Composer::sectionHeader(self, panelX, panelW, y, "Top zones by spawn density")
        Local zi% = 0
        Local topName$[5]
        Local topCount%[5]
        Local topHandle%[5]
        For zi = 0 To 4
            topName[zi] = "" : topCount[zi] = 0 : topHandle[zi] = 0
        Next
        For Ar = Each Area
            Local spawnSum% = 0
            Local sci% = 0
            For sci = 0 To 999
                If Ar\SpawnActor[sci] > 0 Then spawnSum = spawnSum + 1
            Next
            // Insertion into top-5
            Local insertAt% = -1
            Local ti% = 0
            For ti = 0 To 4
                If spawnSum > topCount[ti]
                    insertAt = ti
                    Exit
                EndIf
            Next
            If insertAt >= 0
                // Shift down
                Local sj% = 4
                For sj = 4 To insertAt + 1 Step -1
                    topName[sj]   = topName[sj - 1]
                    topCount[sj]  = topCount[sj - 1]
                    topHandle[sj] = topHandle[sj - 1]
                Next
                topName[insertAt] = Ar\Name$
                topCount[insertAt] = spawnSum
                topHandle[insertAt] = Handle(Ar)
            EndIf
        Next
        For zi = 0 To 4
            If topCount[zi] > 0
                y = Composer::row(self, panelX, panelW, y, topName[zi], Str(topCount[zi]) + " spawns")
            EndIf
        Next
        If topCount[0] = 0
            If Composer::canPaintRow(self, y, CMP_ROW_H) = True
                LoomText(panelX + CMP_PAD, y + 4, "(no zones with spawns)", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
            EndIf
            y = y + CMP_ROW_H
        EndIf

        // Issues breakdown by category. Walk the BrokenRef pool that
        // BrokenRefs::rebuild populates (cached -- no fresh scan here).
        y = Composer::sectionHeader(self, panelX, panelW, y, "Issues breakdown")
        Local brBroken% = 0
        Local brEmpty% = 0
        Local brOrphanZone% = 0
        Local brMissingTex% = 0
        Local brMissingMesh% = 0
        Local brMissingSound% = 0
        Local brBrokenScript% = 0
        Local brOrphanScript% = 0
        Local brOrphanTex% = 0
        Local brOrphanMesh% = 0
        Local brOrphanSound% = 0
        Local brPlayability% = 0
        Local brOther% = 0
        Local br.BrokenRef
        For br = Each BrokenRef
            // Sequential Ifs because BlitzForge doesn't chain ElseIf
            // after a single-line Then. Each compares one category.
            Local matched% = False
            If br\Category = "broken-ref"      Then brBroken        = brBroken + 1        : matched = True
            If br\Category = "empty-field"     Then brEmpty         = brEmpty + 1         : matched = True
            If br\Category = "orphan-zone"     Then brOrphanZone    = brOrphanZone + 1    : matched = True
            If br\Category = "missing-texture" Then brMissingTex    = brMissingTex + 1    : matched = True
            If br\Category = "missing-mesh"    Then brMissingMesh   = brMissingMesh + 1   : matched = True
            If br\Category = "missing-sound"   Then brMissingSound  = brMissingSound + 1  : matched = True
            If br\Category = "broken-script"   Then brBrokenScript  = brBrokenScript + 1  : matched = True
            If br\Category = "orphan-script"   Then brOrphanScript  = brOrphanScript + 1  : matched = True
            If br\Category = "orphan-texture"  Then brOrphanTex     = brOrphanTex + 1     : matched = True
            If br\Category = "orphan-mesh"     Then brOrphanMesh    = brOrphanMesh + 1    : matched = True
            If br\Category = "orphan-sound"    Then brOrphanSound   = brOrphanSound + 1   : matched = True
            If br\Category = "playability"     Then brPlayability   = brPlayability + 1   : matched = True
            If matched = False Then brOther = brOther + 1
        Next
        y = Composer::row(self, panelX, panelW, y, "Broken refs",     Str(brBroken))
        y = Composer::row(self, panelX, panelW, y, "Empty fields",    Str(brEmpty))
        y = Composer::row(self, panelX, panelW, y, "Broken scripts",  Str(brBrokenScript))
        y = Composer::row(self, panelX, panelW, y, "Missing texture", Str(brMissingTex))
        y = Composer::row(self, panelX, panelW, y, "Missing mesh",    Str(brMissingMesh))
        y = Composer::row(self, panelX, panelW, y, "Missing sound",   Str(brMissingSound))
        y = Composer::row(self, panelX, panelW, y, "Orphan zone",     Str(brOrphanZone))
        y = Composer::row(self, panelX, panelW, y, "Orphan script",   Str(brOrphanScript))
        y = Composer::row(self, panelX, panelW, y, "Orphan texture",  Str(brOrphanTex))
        y = Composer::row(self, panelX, panelW, y, "Orphan mesh",     Str(brOrphanMesh))
        y = Composer::row(self, panelX, panelW, y, "Orphan sound",    Str(brOrphanSound))
        y = Composer::row(self, panelX, panelW, y, "Playability",     Str(brPlayability))

        // Open Issues hint -- Ctrl+H shortcut to drill into details
        If Composer::canPaintRow(self, y, CMP_ROW_H) = True
            LoomText(panelX + CMP_PAD, y + 4, "Ctrl+H opens the Issues modal for per-entry detail.", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
        EndIf
        y = y + CMP_ROW_H

        Composer::recordContentBottom(self, y)
    End Method


    // -------------------------------------------------------------------------
    // Value formatters + helpers
    // -------------------------------------------------------------------------

    Method boolLabel$(b%)
        If b Then Return "Yes"
        Return "No"
    End Method


    Method formatFloat$(v#)
        Local rounded# = Float(Int(v# * 10.0)) / 10.0
        Return Str$(rounded)
    End Method


    Method actorAggLabel$(a%)
        If a = 0 Then Return "Passive"
        If a = 1 Then Return "Defensive"
        If a = 2 Then Return "Always attacks"
        If a = 3 Then Return "Non-combatant"
        Return Str(a)
    End Method


    Method actorGenderLabel$(g%)
        If g = 0 Then Return "Both"
        If g = 1 Then Return "Male only"
        If g = 2 Then Return "Female only"
        If g = 3 Then Return "No gender"
        Return Str(g)
    End Method


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


    // findZoneByName -- resolve a zone-name string (from a portal's
    // PortalLinkArea$) to its Handle, or 0 if not found.
    Method findZoneByName%(name$)
        If name = "" Then Return 0
        For Ar.Area = Each Area
            If Upper$(Ar\Name$) = Upper$(name) Then Return Handle(Ar)
        Next
        Return 0
    End Method


    // -------------------------------------------------------------------------
    // drawBackToTop -- small brass pill in the lower-right of the composer
    // body. Click resets scrollOffset to 0. Only painted when scrolled
    // (caller-gated by scrollOffset > 100). Big composers (Actor with
    // 96+ attribute cells, Zone with 1000s of sub-entities) otherwise
    // need many wheel-scrolls to return to the top.
    // -------------------------------------------------------------------------
    Method drawBackToTop(btnX%, btnY%, mx%, my%, clicked%)
        Local btnW% = 48
        Local btnH% = 22
        Local hovered% = (mx >= btnX And mx < btnX + btnW And my >= btnY And my < btnY + btnH)

        LoomShadowCard(btnX, btnY, btnW, btnH)
        If hovered = True
            LoomFill(btnX, btnY, btnW, btnH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            LoomBorder(btnX, btnY, btnW, btnH, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
            LoomText(btnX + 8, btnY + 4, "^ top", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        Else
            LoomFill(btnX, btnY, btnW, btnH, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
            LoomBorder(btnX, btnY, btnW, btnH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            LoomText(btnX + 8, btnY + 4, "^ top", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        EndIf

        If hovered = True And clicked = True
            self\scrollOffset = 0
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // drawScrollbar -- thin brass thumb on the right edge of the composer
    // body, visible only when content overflows. Thumb position +
    // height reflect scrollOffset / contentHeight.
    //
    // Track: stone-700 background spanning the full body height.
    // Thumb:  brass-500 rect sized proportionally to bodyHeight /
    //         contentHeight, positioned at scrollOffset / contentHeight.
    // -------------------------------------------------------------------------
    Method drawScrollbar(barX%, barTopY%, bodyH%)
        Local contentH% = self\lastContentBottom - self\bodyTop
        If contentH <= 0 Then Return

        // Track
        LoomFill(barX, barTopY, CMP_SCROLLBAR_W, bodyH, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)

        // Thumb height proportional to visible-fraction-of-content;
        // minimum 16px so the thumb stays grabbable even with very
        // tall content.
        Local thumbH% = (bodyH * bodyH) / contentH
        If thumbH < 16 Then thumbH = 16
        If thumbH > bodyH Then thumbH = bodyH

        // Thumb y: scrollOffset is in "content space", we translate to
        // "track space" by multiplying by track-travel / content-travel.
        Local travelTrack% = bodyH - thumbH
        Local travelContent% = contentH - bodyH
        Local thumbY% = barTopY
        If travelContent > 0
            thumbY = barTopY + (self\scrollOffset * travelTrack) / travelContent
        EndIf

        LoomFill(barX, thumbY, CMP_SCROLLBAR_W, thumbH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
    End Method


    // -------------------------------------------------------------------------
    // recordContentBottom -- per-kind body renderers call this at the end
    // with their final y cursor. We translate back from scrolled space to
    // absolute panel-y so scrollOffset clamping can use it.
    // -------------------------------------------------------------------------
    Method recordContentBottom(finalY%)
        self\lastContentBottom = finalY + self\scrollOffset
    End Method
End Type
