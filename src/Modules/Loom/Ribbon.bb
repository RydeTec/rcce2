Strict

// =============================================================================
// Loom/Ribbon.bb -- top "validation conscience" status ribbon
// =============================================================================
//
// One of the six signature surfaces from the Loom design (README.md):
// "Validation conscience - top status ribbon showing world-health at a
// glance: unsaved count, broken references, balance hints."
//
// What this surface shows:
//   - Per-kind unsaved badges -- a chip for each kind whose *Saved global
//     is False (Spells, Items, Actors, Factions, Zone, Anims). Click a
//     badge to Save that kind.
//   - Broken-reference count -- entities with reference fields that don't
//     resolve (Actor pointing at a deleted Faction, Zone portal pointing
//     at a deleted Zone). Shown as a danger-red count; clicking opens the
//     palette pre-seeded with the next iteration's broken-ref-finder
//     (not yet implemented -- shows count only for now).
//   - Total entity counts -- shown subtly on the right.
//
// The ribbon sits ABOVE the existing browser top ribbon, pushing the tab
// bar and filter bar down by RIBBON_H pixels. This keeps the browser's
// own brand strip ("LOOM / Browser / project name") intact and adds the
// new conscience surface as a parallel band.
//
// Render contract: rendered FIRST in Loom.bb's frame (above everything
// else), so it owns the top RIBBON_H pixels. Browser / Composer / Palette
// all need to add RIBBON_H to their y origins to make room.
//
// Architecture: Type with Methods. Holds a Threads reference so click-a-
// broken-ref-count can eventually jump to the palette / a finder modal.
// Holds a Composer reference so click-a-dirty-badge can trigger a save
// (Composer owns the SaveX dispatch).


// Layout constants
Const RIBBON_H            = 28
Const RIBBON_PAD          = 12
Const RIBBON_BADGE_PAD_X  = 10
Const RIBBON_BADGE_H      = 20
Const RIBBON_BADGE_GAP    = 6


// =============================================================================
// Ribbon -- top validation strip. Single instance owned by Loom.
// =============================================================================
Type Ribbon
    Field threads.Threads
    Field composer.Composer
    Field brokenRefs.BrokenRefs   // set by setBrokenRefs; click on the
                                  // broken-ref count opens this modal

    // Per-frame cache so multiple drawing passes don't re-walk all
    // entities. Recomputed at the top of renderAndUpdate.
    Field cachedBrokenRefs%
    Field cachedTotalActors%
    Field cachedTotalItems%
    Field cachedTotalSpells%
    Field cachedTotalZones%
    Field cachedTotalFactions%
    Field cachedTotalAnimSets%


    Method create.Ribbon(threads.Threads, composer.Composer)
        self\threads = threads
        self\composer = composer
        self\brokenRefs = Null
        Return self
    End Method


    // -------------------------------------------------------------------------
    // setBrokenRefs -- injection point from Loom.bb so a click on the
    // broken-ref count chip can open the BrokenRefs modal. Called once
    // at construction (after both instances exist).
    // -------------------------------------------------------------------------
    Method setBrokenRefs(brokenRefs.BrokenRefs)
        self\brokenRefs = brokenRefs
    End Method


    // -------------------------------------------------------------------------
    // height -- exposed for the Browser / Composer / Palette so they know
    // how many pixels to leave at the top.
    // -------------------------------------------------------------------------
    Method height%()
        Return RIBBON_H
    End Method


    // -------------------------------------------------------------------------
    // renderAndUpdate -- paint the ribbon, hit-test badges, return True if
    // any badge was clicked (so the outer frame can suppress other handlers
    // that might fire on the same coordinate).
    // -------------------------------------------------------------------------
    Method renderAndUpdate%(sw%)
        Local mx% = MouseX()
        Local my% = MouseY()
        Local clicked% = Loom_MouseClicked()

        Ribbon::recomputeCache(self)

        // Background -- subtle stone-900 -> stone-950 gradient so the
        // conscience strip doesn't read as a flat black bar above the
        // brand strip.
        LoomGradientV(0, 0, sw, RIBBON_H, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B, LOOM_STONE_950_R, LOOM_STONE_950_G, LOOM_STONE_950_B)
        LoomHRule(0, RIBBON_H - 1, sw, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)

        // Left side: "CONSCIENCE" label + dirty badges
        LoomText(RIBBON_PAD, 6, "CONSCIENCE", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        Local x% = RIBBON_PAD + 110

        Local consumed% = False
        Local result% = 0

        result = Ribbon::drawDirtyBadge(self, "Actors",   "actor",   ActorsSaved,   x, mx, my, clicked) : x = result
        If clicked And x = -1 Then consumed = True
        result = Ribbon::drawDirtyBadge(self, "Items",    "item",    ItemsSaved,    x, mx, my, clicked) : x = result
        If clicked And x = -1 Then consumed = True
        result = Ribbon::drawDirtyBadge(self, "Spells",   "spell",   SpellsSaved,   x, mx, my, clicked) : x = result
        result = Ribbon::drawDirtyBadge(self, "Zone",     "zone",    ZoneSaved,     x, mx, my, clicked) : x = result
        result = Ribbon::drawDirtyBadge(self, "Factions", "faction", FactionsSaved, x, mx, my, clicked) : x = result
        result = Ribbon::drawDirtyBadge(self, "Anims",    "animset", AnimsSaved,    x, mx, my, clicked) : x = result

        // Center: broken-ref count chip -- danger-red when > 0, dim when 0.
        // Clickable when > 0 + BrokenRefs is wired: opens the finder modal
        // so the user can jump to each broken source and fix it.
        If self\cachedBrokenRefs > 0
            // Label says "broken ref(s) + issues" because the modal also
            // surfaces warning/info issues (playability, weapon-config,
            // orphan zones) that aren't counted by WorldCache. The cache
            // only tracks the higher-severity broken-ref count for the
            // ribbon's at-a-glance signal.
            Local brokenLabel$ = Str(self\cachedBrokenRefs) + " broken ref"
            If self\cachedBrokenRefs > 1 Then brokenLabel = brokenLabel + "s"
            brokenLabel = brokenLabel + " + issues"
            Local brkW% = StringWidth(brokenLabel) + 24
            Local brkX% = sw / 2 - brkW / 2
            Local brkY% = 4
            Local brkH% = RIBBON_BADGE_H
            Local brkHover% = (mx >= brkX And mx < brkX + brkW And my >= brkY And my < brkY + brkH)

            If brkHover = True
                LoomFill(brkX, brkY, brkW, brkH, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
                LoomBorder(brkX, brkY, brkW, brkH, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
                LoomTextCentered(sw / 2, 6, brokenLabel, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
            Else
                LoomTextCentered(sw / 2, 6, brokenLabel, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
            EndIf

            If brkHover And clicked And self\brokenRefs <> Null
                BrokenRefs::openModal(self\brokenRefs)
                consumed = True
            EndIf
        Else
            // No broken refs but warning/info issues may still be in the
            // modal. Clickable so the user can open the modal regardless.
            Local emptyLabel$ = "no broken refs  |  click for all issues"
            Local emptyW% = StringWidth(emptyLabel) + 24
            Local emptyX% = sw / 2 - emptyW / 2
            Local emptyHover% = (mx >= emptyX And mx < emptyX + emptyW And my >= 4 And my < 4 + RIBBON_BADGE_H)
            If emptyHover = True
                LoomTextCentered(sw / 2, 6, emptyLabel, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            Else
                LoomTextCentered(sw / 2, 6, emptyLabel, LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
            EndIf
            If emptyHover And clicked And self\brokenRefs <> Null
                BrokenRefs::openModal(self\brokenRefs)
                consumed = True
            EndIf
        EndIf

        // Right side: chrome immersion toggle + total entity counts (compact)
        Local totals$ = Str(self\cachedTotalActors) + "A | " + Str(self\cachedTotalItems) + "I | " + Str(self\cachedTotalSpells) + "S | " + Str(self\cachedTotalZones) + "Z | " + Str(self\cachedTotalFactions) + "F | " + Str(self\cachedTotalAnimSets) + "M"
        Local totalsW% = StringWidth(totals)
        LoomText(sw - totalsW - RIBBON_PAD, 6, totals, LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)

        // Chrome toggle pill -- cycles tool / balanced / in-world.
        // Sits to the LEFT of the totals so it doesn't get cut off by
        // narrow window widths. Click cycles the global mode; the
        // brand strip + cards + composer panel re-render in the new
        // mode immediately on the next frame.
        Local chromeLabel$ = "[" + Loom_ChromeMode() + "]"
        Local chromeW% = StringWidth(chromeLabel) + 16
        Local chromeX% = sw - totalsW - RIBBON_PAD - chromeW - 12
        Local chromeY% = 4
        Local chromeH% = RIBBON_BADGE_H
        Local chromeHover% = (mx >= chromeX And mx < chromeX + chromeW And my >= chromeY And my < chromeY + chromeH)
        If chromeHover = True
            LoomFill(chromeX, chromeY, chromeW, chromeH, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
            LoomBorder(chromeX, chromeY, chromeW, chromeH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            LoomText(chromeX + 8, chromeY + 4, chromeLabel, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        Else
            LoomText(chromeX + 8, chromeY + 4, chromeLabel, LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
        EndIf
        If chromeHover And clicked
            Loom_CycleChromeMode()
            ; Persist immediately (no Save All needed) so the new mode
            ; survives a restart. Same set-and-forget shape as a
            ; browser zoom toggle.
            Loom_SaveChromeMode()
            consumed = True
            WriteLog(LoomLog, "Ribbon: chrome mode -> " + Loom_ChromeMode())
        EndIf

        Return consumed
    End Method


    // -------------------------------------------------------------------------
    // drawDirtyBadge -- paint one dirty-kind badge if the kind is dirty,
    // else paint nothing (and return x unchanged). Returns the next x cursor
    // (or x unchanged when the badge wasn't shown).
    //
    // Click triggers Composer::commitSaveForKind(kind) -- the same save
    // dispatch the Composer's Save button uses.
    // -------------------------------------------------------------------------
    Method drawDirtyBadge%(label$, kind$, saved%, x%, mx%, my%, clicked%)
        If saved = True Then Return x   // not dirty, skip
        Local bw% = StringWidth(label) + RIBBON_BADGE_PAD_X * 2
        Local by% = 4
        Local bh% = RIBBON_BADGE_H
        Local hovered% = (mx >= x And mx < x + bw And my >= by And my < by + bh)

        If hovered = True
            LoomFill(x, by, bw, bh, LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B)
        Else
            LoomFill(x, by, bw, bh, LOOM_BRASS_800_R, LOOM_BRASS_800_G, LOOM_BRASS_800_B)
        EndIf
        LoomBorder(x, by, bw, bh, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        LoomText(x + RIBBON_BADGE_PAD_X, by + 4, label, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        If hovered And clicked
            Composer::commitSaveForKind(self\composer, kind)
            WriteLog(LoomLog, "Ribbon: saved " + kind + " via badge click")
        EndIf

        Return x + bw + RIBBON_BADGE_GAP
    End Method


    // -------------------------------------------------------------------------
    // recomputeCache -- delegates to WorldCache (shared with BrokenRefs /
    // Atlas). The cache itself only re-walks the type pools when a
    // mutation has fired WorldCache_Invalidate since the last call; a
    // clean cache returns immediately.
    //
    // Before WorldCache landed this Method did the full O(actors *
    // animsets + zones * portals * zones) scan every frame; now the
    // expensive walk fires at most once per mutation and amortizes
    // across however many frames sit between mutations.
    //
    // The local cachedX fields stay populated as a snapshot for the
    // current frame so the drawing code below doesn't need a per-Method
    // re-fetch through the WorldCache getters.
    // -------------------------------------------------------------------------
    Method recomputeCache()
        If LoomWorldCache = Null Then Return
        self\cachedBrokenRefs    = WorldCache::brokenRefs(LoomWorldCache)
        self\cachedTotalActors   = WorldCache::totalActors(LoomWorldCache)
        self\cachedTotalItems    = WorldCache::totalItems(LoomWorldCache)
        self\cachedTotalSpells   = WorldCache::totalSpells(LoomWorldCache)
        self\cachedTotalZones    = WorldCache::totalZones(LoomWorldCache)
        self\cachedTotalFactions = WorldCache::totalFactions(LoomWorldCache)
        self\cachedTotalAnimSets = WorldCache::totalAnimSets(LoomWorldCache)
    End Method
End Type
