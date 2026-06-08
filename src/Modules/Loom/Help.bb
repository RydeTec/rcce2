Strict

// =============================================================================
// Loom/Help.bb -- F1 keyboard shortcuts cheat sheet
// =============================================================================
//
// Loom's keybinding suite grew from "Esc + arrows" in the alpha to
// "Ctrl+K / Ctrl+H / Ctrl+R / Ctrl+S + arrows + Enter + right-click +
// the Esc cascade" in the beta. Without a discovery surface, users
// can't find them.
//
// F1 opens this modal showing every keybinding + mouse interaction in
// one table. Esc closes. The contents are static -- no per-session
// state, no scrolling needed at the current shortcut count.
//
// Architecture: Type with Methods (mirrors Timeline / Recents / BrokenRefs
// / Palette / ExitPrompt -- the five other modal surfaces).


Const HELP_MODAL_W   = 680
Const HELP_MODAL_H   = 520
Const HELP_PAD       = 16
Const HELP_HEADER_H  = 32
Const HELP_HINT_H    = 24
Const HELP_ROW_H     = 22
Const HELP_KEY_COL_W = 200


// =============================================================================
// Help -- F1 cheat sheet modal.
// =============================================================================
Type Help
    Field open%
    // Body scroll offset in pixels. The keybinding table is taller than the
    // modal body, so the rows scroll under a clipped viewport while the
    // chrome (header / footer / border) stays fixed. Measured-then-clamped:
    // lastContentH is filled in during render and used to clamp scroll on
    // the next frame (same pattern as the composer body).
    Field scroll%
    Field lastContentH%
    // Visible body band [bodyTop, bodyBottom], set each render. The row /
    // section helpers gate their own drawing to this band (composer-style
    // canPaintRow), so we scroll by pixel offset WITHOUT a 2D Viewport clip.
    // (TrueType text under a clipped Viewport overflowed the engine's
    // text path -- the composer avoids it the same way.)
    Field bodyTop%
    Field bodyBottom%


    Method create.Help()
        self\open = False
        self\scroll = 0
        self\lastContentH = 0
        Return self
    End Method


    Method isOpen%()
        Return self\open
    End Method


    Method openModal()
        self\open = True
        self\scroll = 0
        FlushKeys
        Loom_ConsumeClick()
        WriteLog(LoomLog, "Help: open")
    End Method


    Method closeModal()
        self\open = False
        WriteLog(LoomLog, "Help: close")
    End Method


    Method renderAndUpdate%(sw%, sh%)
        If self\open = False Then Return False

        If KeyHit(1) Or KeyHit(59)   // Esc or F1 toggle-off
            Help::closeModal(self)
            Return True
        EndIf

        LoomFill(0, 0, sw, sh, LOOM_STONE_950_R, LOOM_STONE_950_G, LOOM_STONE_950_B)

        Local mx% = MouseX()
        Local my% = MouseY()
        Local clicked% = Loom_MouseClicked()

        Local modalX% = (sw - HELP_MODAL_W) / 2
        Local modalY% = (sh - HELP_MODAL_H) / 3

        LoomShadowCard(modalX, modalY, HELP_MODAL_W, HELP_MODAL_H)
        LoomFill(modalX, modalY, HELP_MODAL_W, HELP_MODAL_H, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B)
        LoomBorder(modalX, modalY, HELP_MODAL_W, HELP_MODAL_H, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomBorder(modalX + 1, modalY + 1, HELP_MODAL_W - 2, HELP_MODAL_H - 2, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        LoomFill(modalX, modalY, HELP_MODAL_W, 3, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        // Header in display font
        LoomTheme_UseDisplay()
        LoomText(modalX + HELP_PAD, modalY + 6, "LOOM  |  KEYBINDINGS", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomTheme_UseBody()

        // Scrollable body region. The keybinding table overflows the modal,
        // so the rows paint under a clipped viewport that scrolls; the header
        // above and the footer hint below stay pinned. bodyTop/bodyBottom
        // bound the visible band.
        Local bodyTop%    = modalY + HELP_HEADER_H + 8
        Local bodyBottom% = modalY + HELP_MODAL_H - HELP_HINT_H - 10
        Local bodyH%      = bodyBottom - bodyTop
        // Publish the band so the row/section helpers can gate their draws.
        self\bodyTop    = bodyTop
        self\bodyBottom = bodyBottom

        // Mouse wheel + arrow keys scroll the body. Loom_MouseWheel is the
        // per-frame delta; each tick moves HELP_ROW_H * 3 px. Clamp against
        // last frame's measured content height.
        Local maxScroll% = self\lastContentH - bodyH
        If maxScroll < 0 Then maxScroll = 0
        Local wheelTicks% = Loom_MouseWheel()
        If wheelTicks <> 0
            self\scroll = self\scroll - wheelTicks * HELP_ROW_H * 3
            Loom_ConsumeWheel()
        EndIf
        If KeyDown(208) Then self\scroll = self\scroll + 8   // Down arrow
        If KeyDown(200) Then self\scroll = self\scroll - 8   // Up arrow
        If self\scroll < 0 Then self\scroll = 0
        If self\scroll > maxScroll Then self\scroll = maxScroll

        // Body -- rendered as a two-column table via per-row helpers so
        // the row layout stays consistent. Rows start at bodyTop offset by
        // the scroll; each helper gates its draw to [bodyTop, bodyBottom]
        // (no 2D Viewport clip -- see the field comment above).
        Local rowY% = bodyTop - self\scroll

        rowY = Help::section(self, modalX, rowY, "Global")
        rowY = Help::row(self, modalX, rowY, "Ctrl+K",       "Command palette (find anywhere)")
        rowY = Help::row(self, modalX, rowY, "Ctrl+H",       "Session timeline (edit history + revert)")
        rowY = Help::row(self, modalX, rowY, "Ctrl+R",       "Recents (jump to recently-focused entity)")
        rowY = Help::row(self, modalX, rowY, "Ctrl+S",       "Save All (every dirty kind)")
        rowY = Help::row(self, modalX, rowY, "Ctrl+F",       "Find in scripts (grep across .rsl)")
        rowY = Help::row(self, modalX, rowY, "F1",           "This help screen")
        rowY = Help::row(self, modalX, rowY, "Esc",          "Pop / close / exit (priority chain)")
        rowY = rowY + 6

        rowY = Help::section(self, modalX, rowY, "Browser")
        rowY = Help::row(self, modalX, rowY, "Arrow keys",   "Move card selection cursor")
        rowY = Help::row(self, modalX, rowY, "Enter",        "Focus the selected card")
        rowY = Help::row(self, modalX, rowY, "Type letters", "Filter the current tab by name")
        rowY = Help::row(self, modalX, rowY, "Click tab",    "Switch category")
        rowY = Help::row(self, modalX, rowY, "Click + New",  "Create a fresh entity of the current kind")
        rowY = rowY + 6

        rowY = Help::section(self, modalX, rowY, "Composer (focused entity)")
        rowY = Help::row(self, modalX, rowY, "Click field",        "Begin editing (text / number)")
        rowY = Help::row(self, modalX, rowY, "Enter",              "Commit edit")
        rowY = Help::row(self, modalX, rowY, "Tab / Shift+Tab",    "Commit + advance to next/prev editable field")
        rowY = Help::row(self, modalX, rowY, "Esc (during edit)",  "Cancel edit")
        rowY = Help::row(self, modalX, rowY, "Click toggle pill",  "Flip a bool field")
        rowY = Help::row(self, modalX, rowY, "Left-click chip",    "Jump to referenced entity")
        rowY = Help::row(self, modalX, rowY, "Right-click chip",   "Open palette as picker (swap referent)")
        rowY = Help::row(self, modalX, rowY, "Click Save / X / Discard", "Persist / delete (arm) / revert")
        rowY = Help::row(self, modalX, rowY, "Click Dup",                "Duplicate the focused entity")
        rowY = Help::row(self, modalX, rowY, "Click chevron",             "Collapse / expand composer to sliver")
        rowY = Help::row(self, modalX, rowY, "Mouse wheel",               "Scroll the composer body")
        rowY = Help::row(self, modalX, rowY, "Shift+click a card",        "Add/remove from bulk-select set")
        rowY = rowY + 6

        rowY = Help::section(self, modalX, rowY, "Bulk edit (when selection non-empty)")
        rowY = Help::row(self, modalX, rowY, "Click Delete in panel",     "Arm bulk delete; click again to commit")
        rowY = Help::row(self, modalX, rowY, "Click input field",         "Start typing a broadcast value (homogeneous kinds only)")
        rowY = Help::row(self, modalX, rowY, "Click Apply",               "Broadcast typed value to every selected entity")
        rowY = rowY + 6

        rowY = Help::section(self, modalX, rowY, "Ribbon (top strip)")
        rowY = Help::row(self, modalX, rowY, "Click dirty badge",        "Save that kind")
        rowY = Help::row(self, modalX, rowY, "Click broken-ref count",   "Open the broken-ref finder")

        // Done painting the table -- record the full content height (in
        // unscrolled coords) so next frame can clamp scroll, then drop the
        // clip so the footer + scrollbar draw normally.
        self\lastContentH = (rowY + self\scroll) - bodyTop

        // Scrollbar thumb on the right edge of the body when content overflows.
        // Derive the scroll denominator from THIS frame's freshly-measured
        // lastContentH -- NOT the frame-top `maxScroll`, which is computed
        // from the PREVIOUS frame's lastContentH (0 on the first open). On
        // that first frame the guard below is true (content overflows) but
        // frame-top maxScroll is still 0, so dividing by it was an integer
        // divide-by-zero -> BlitzForge "Stack overflow!" the instant F1 opened.
        // `denom` is > 0 whenever this branch runs (lastContentH > bodyH).
        If self\lastContentH > bodyH
            Local denom% = self\lastContentH - bodyH
            Local trackX% = modalX + HELP_MODAL_W - 6
            LoomFill(trackX, bodyTop, 3, bodyH, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
            Local thumbH% = (bodyH * bodyH) / self\lastContentH
            If thumbH < 20 Then thumbH = 20
            Local thumbScroll% = self\scroll
            If thumbScroll > denom Then thumbScroll = denom
            Local thumbY% = bodyTop + (thumbScroll * (bodyH - thumbH)) / denom
            LoomFill(trackX, thumbY, 3, thumbH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        EndIf

        // Footer hint
        Local hy% = modalY + HELP_MODAL_H - HELP_HINT_H - 4
        LoomHRule(modalX + HELP_PAD, hy - 2, HELP_MODAL_W - HELP_PAD * 2, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        LoomText(modalX + HELP_PAD, hy + 4, "Esc or F1 to close  |  wheel / arrows to scroll", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)

        // Click-outside-modal closes
        If clicked = True
            If mx < modalX Or mx >= modalX + HELP_MODAL_W Or my < modalY Or my >= modalY + HELP_MODAL_H
                Help::closeModal(self)
            EndIf
        EndIf

        Return True
    End Method


    // -------------------------------------------------------------------------
    // section -- brass-underlined section header. Returns next y.
    // -------------------------------------------------------------------------
    Method section%(modalX%, y%, title$)
        // Gate to the visible band -- only paint when the whole header
        // (text + underline) fits inside [bodyTop, bodyBottom]. Out-of-view
        // headers are skipped but still advance y so layout/scroll stay
        // consistent. This replaces the 2D Viewport clip.
        If y >= self\bodyTop And y + 24 <= self\bodyBottom
            LoomText(modalX + HELP_PAD, y, title, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            LoomHRule(modalX + HELP_PAD, y + 18, HELP_MODAL_W - HELP_PAD * 2, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        EndIf
        Return y + 24
    End Method


    // -------------------------------------------------------------------------
    // row -- two-column "key : description" row. Returns next y.
    // -------------------------------------------------------------------------
    Method row%(modalX%, y%, keyLabel$, desc$)
        // Same band gate as section() -- skip drawing rows scrolled out of
        // the visible body, but always advance y.
        If y >= self\bodyTop And y + HELP_ROW_H <= self\bodyBottom
            LoomText(modalX + HELP_PAD,                  y, keyLabel, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
            LoomText(modalX + HELP_PAD + HELP_KEY_COL_W, y, desc,     LOOM_STONE_200_R, LOOM_STONE_200_G, LOOM_STONE_200_B)
        EndIf
        Return y + HELP_ROW_H
    End Method
End Type
