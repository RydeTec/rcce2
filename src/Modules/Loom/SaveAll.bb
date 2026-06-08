Strict

// =============================================================================
// Loom/SaveAll.bb -- Ctrl+S save-everything + save-on-exit modal prompt
// =============================================================================
//
// Two coupled surfaces:
//
//   1. SaveAll_Persist(composer) -- walks every kind, calls
//      Composer::commitSaveForKind for any that's dirty. Bound to Ctrl+S
//      in Loom.bb. Without this, the only way to save was per-kind clicks
//      on the Composer's Save button -- so a session with edits across
//      four tabs needed four clicks. Drop-in editors save the world.
//
//   2. ExitPrompt -- modal that intercepts a Loom exit when any kind is
//      dirty. Three buttons: Save All / Discard All / Cancel. "Save All"
//      calls SaveAll_Persist; "Discard All" lets Loom exit (changes lost
//      from memory; on-disk state intact); "Cancel" stays in Loom.
//
//      Without this prompt, a user who hit Esc with unsaved Spell edits
//      lost them silently. GUE asks before exit; Loom must too.
//
// Architecture: SaveAll_* are free functions (stateless dispatch); the
// ExitPrompt is a Type+Methods (modal owns its state + render path,
// mirrors the Timeline / Recents / Palette / BrokenRefs shape).


Const EXITPROMPT_MODAL_W = 480
Const EXITPROMPT_MODAL_H = 200
Const EXITPROMPT_PAD     = 16
Const EXITPROMPT_BTN_W   = 130
Const EXITPROMPT_BTN_H   = 34


// =============================================================================
// SaveAll_AnyDirty -- True if any per-kind *Saved global is False. Used by
// the Esc-exit handler in Loom.bb to decide whether to show the prompt.
// =============================================================================
Function SaveAll_AnyDirty%()
    If ActorsSaved   = False Then Return True
    If ItemsSaved    = False Then Return True
    If SpellsSaved   = False Then Return True
    If FactionsSaved = False Then Return True
    If AnimsSaved    = False Then Return True
    If ZoneSaved     = False Then Return True
    If SettingsSaved = False Then Return True
    Return False
End Function


// =============================================================================
// SaveAll_Persist -- dispatch commitSaveForKind for each dirty kind.
// Called from the Ctrl+S keybinding (in Loom.bb) and from ExitPrompt's
// "Save All" button.
//
// Order: zones last because ServerSaveArea takes the currently-focused
// Area, and the bulk savers (SaveActors / etc.) don't touch focus state.
// Actually it doesn't matter here -- ServerSaveArea reads focus via
// composer\threads\focusID but Composer::commitSaveForKind for "zone"
// pulls the Area from focusID directly; if the user is on a different
// kind when Save All fires, the zone branch becomes a no-op (logs
// "stale handle"). Acceptable: the focused kind is whatever the user
// was last on, and Save All from any tab still saves every OTHER kind.
// =============================================================================
Function SaveAll_Persist(composer.Composer)
    If composer = Null Then Return

    // Count what we save so the trailing toast is informative
    Local count% = 0
    If ActorsSaved = False   Then Composer::commitSaveForKind(composer, "actor")   : count = count + 1
    If ItemsSaved = False    Then Composer::commitSaveForKind(composer, "item")    : count = count + 1
    If SpellsSaved = False   Then Composer::commitSaveForKind(composer, "spell")   : count = count + 1
    If FactionsSaved = False Then Composer::commitSaveForKind(composer, "faction") : count = count + 1
    If AnimsSaved = False    Then Composer::commitSaveForKind(composer, "animset") : count = count + 1
    If ZoneSaved = False     Then Composer::commitSaveForKind(composer, "zone")    : count = count + 1
    If SettingsSaved = False Then Composer::commitSaveForKind(composer, "settings"): count = count + 1

    // Each commitSaveForKind already fires its own per-kind success
    // toast; the Save All summary kicks in only when there's more than
    // one kind to save (otherwise the single per-kind toast suffices).
    If count > 1
        Toast_Show("Save All: " + Str(count) + " kinds persisted", "success")
    Else If count = 0
        Toast_Show("Nothing to save", "info")
    EndIf

    WriteLog(LoomLog, "SaveAll: persisted " + Str(count) + " dirty kinds")
End Function


// =============================================================================
// ExitPrompt -- save-on-exit confirmation modal.
//
// State machine:
//   closed                 -- normal Loom session
//   open, undecided        -- waiting for user click
//   open, save-clicked     -- transient; SaveAll_Persist invoked, modal
//                             closes, exitConfirmed = True
//   open, discard-clicked  -- modal closes, exitConfirmed = True
//   open, cancel-clicked   -- modal closes, exitConfirmed stays False
//
// Loom.bb reads exitConfirmed via isExitConfirmed; when True, the next
// frame returns False from renderFrame and the main loop breaks.
// =============================================================================
Type ExitPrompt
    Field composer.Composer       // for SaveAll_Persist dispatch

    Field open%
    Field exitConfirmed%          // set True by Save All or Discard All
                                  // click; read by Loom.bb to decide exit


    Method create.ExitPrompt(composer.Composer)
        self\composer = composer
        self\open = False
        self\exitConfirmed = False
        Return self
    End Method


    Method isOpen%()
        Return self\open
    End Method


    Method isExitConfirmed%()
        Return self\exitConfirmed
    End Method


    Method openModal()
        self\open = True
        self\exitConfirmed = False
        FlushKeys
        Loom_ConsumeClick()
        WriteLog(LoomLog, "ExitPrompt: open (dirty kinds present)")
    End Method


    Method closeModal()
        self\open = False
        WriteLog(LoomLog, "ExitPrompt: close")
    End Method


    // -------------------------------------------------------------------------
    // renderAndUpdate -- modal frame. Returns True when modal consumed
    // input so the outer Esc handler skips. NB: Esc INSIDE the modal
    // maps to "Cancel" (don't exit) -- a panicked user who hit Esc twice
    // shouldn't lose work.
    // -------------------------------------------------------------------------
    Method renderAndUpdate%(sw%, sh%)
        If self\open = False Then Return False

        // Esc here = Cancel (don't exit)
        If KeyHit(1)
            ExitPrompt::closeModal(self)
            Return True
        EndIf

        LoomFill(0, 0, sw, sh, LOOM_STONE_950_R, LOOM_STONE_950_G, LOOM_STONE_950_B)

        Local mx% = MouseX()
        Local my% = MouseY()
        Local clicked% = Loom_MouseClicked()

        Local modalX% = (sw - EXITPROMPT_MODAL_W) / 2
        Local modalY% = (sh - EXITPROMPT_MODAL_H) / 3

        LoomShadowCard(modalX, modalY, EXITPROMPT_MODAL_W, EXITPROMPT_MODAL_H)
        LoomFill(modalX, modalY, EXITPROMPT_MODAL_W, EXITPROMPT_MODAL_H, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B)
        LoomBorder(modalX, modalY, EXITPROMPT_MODAL_W, EXITPROMPT_MODAL_H, LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B)
        LoomBorder(modalX + 1, modalY + 1, EXITPROMPT_MODAL_W - 2, EXITPROMPT_MODAL_H - 2, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        LoomFill(modalX, modalY, EXITPROMPT_MODAL_W, 3, LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B)

        LoomTheme_UseDisplay()
        LoomText(modalX + EXITPROMPT_PAD, modalY + 12, "UNSAVED CHANGES", LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B)
        LoomTheme_UseBody()
        LoomText(modalX + EXITPROMPT_PAD, modalY + 40, "Loom has unsaved edits. What should it do?", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        // Three buttons across the bottom: Save All / Discard / Cancel
        Local btnY% = modalY + EXITPROMPT_MODAL_H - EXITPROMPT_BTN_H - EXITPROMPT_PAD
        Local gap% = 12
        Local totalW% = EXITPROMPT_BTN_W * 3 + gap * 2
        Local startX% = modalX + (EXITPROMPT_MODAL_W - totalW) / 2

        ExitPrompt::drawButton(self, "Save All", startX, btnY, mx, my, clicked, "save")
        ExitPrompt::drawButton(self, "Discard All", startX + EXITPROMPT_BTN_W + gap, btnY, mx, my, clicked, "discard")
        ExitPrompt::drawButton(self, "Cancel", startX + (EXITPROMPT_BTN_W + gap) * 2, btnY, mx, my, clicked, "cancel")

        Return True
    End Method


    // -------------------------------------------------------------------------
    // drawButton -- one of the three action buttons. action: "save" /
    // "discard" / "cancel".
    // -------------------------------------------------------------------------
    Method drawButton(label$, bx%, by%, mx%, my%, clicked%, action$)
        Local hovered% = (mx >= bx And mx < bx + EXITPROMPT_BTN_W And my >= by And my < by + EXITPROMPT_BTN_H)

        // Distinct colors per action -- arcane for safe (Save), danger
        // for Discard, stone for Cancel. Via helper Methods to dodge the
        // Strict "reassign Local from nested If/ElseIf" trap.
        If hovered = True
            LoomFill(bx, by, EXITPROMPT_BTN_W, EXITPROMPT_BTN_H, ExitPrompt::actionR(self, action), ExitPrompt::actionG(self, action), ExitPrompt::actionB(self, action))
            LoomBorder(bx, by, EXITPROMPT_BTN_W, EXITPROMPT_BTN_H, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        Else
            LoomFill(bx, by, EXITPROMPT_BTN_W, EXITPROMPT_BTN_H, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
            LoomBorder(bx, by, EXITPROMPT_BTN_W, EXITPROMPT_BTN_H, ExitPrompt::actionR(self, action), ExitPrompt::actionG(self, action), ExitPrompt::actionB(self, action))
        EndIf

        // Center the label
        Local labelW% = StringWidth(label)
        LoomText(bx + (EXITPROMPT_BTN_W - labelW) / 2, by + (EXITPROMPT_BTN_H - 14) / 2, label, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        If hovered And clicked
            If action = "save"
                SaveAll_Persist(self\composer)
                self\exitConfirmed = True
                ExitPrompt::closeModal(self)
            Else If action = "discard"
                self\exitConfirmed = True
                ExitPrompt::closeModal(self)
            Else
                ExitPrompt::closeModal(self)
            EndIf
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // Action color helpers -- pure (kind -> channel) lookups. Lifted out
    // of drawButton to dodge the Strict-mode "reassign Method Local from
    // nested If/ElseIf" trap (architecture.md gotcha).
    // -------------------------------------------------------------------------
    Method actionR%(action$)
        If action = "save"    Then Return LOOM_ARCANE_700_R
        If action = "discard" Then Return LOOM_DANGER_R
        Return LOOM_STONE_700_R
    End Method

    Method actionG%(action$)
        If action = "save"    Then Return LOOM_ARCANE_700_G
        If action = "discard" Then Return LOOM_DANGER_G
        Return LOOM_STONE_700_G
    End Method

    Method actionB%(action$)
        If action = "save"    Then Return LOOM_ARCANE_700_B
        If action = "discard" Then Return LOOM_DANGER_B
        Return LOOM_STONE_700_B
    End Method
End Type
