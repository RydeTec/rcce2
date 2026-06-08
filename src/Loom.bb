// =============================================================================
// Loom.bb -- Loom World Editor (Alpha)
// =============================================================================
//
// **Read docs/loom/README.md first** if you're picking this up. The north
// star, architecture, roadmap, and the ADRs that explain why the code is
// shaped the way it is all live under docs/loom/. The literal Claude Design
// prototype the alpha is built against is preserved at docs/loom/prototype/.
//
// A drop-in alternative to GUE, sharing the on-disk data formats but with
// a fresh UI built around the Loom design concept: every entity is browsable,
// every reference between entities is a clickable thread.
//
// Surface model:
//   BROWSER         everything-grid by category (Actors / Items / Spells /
//                   Zones / Factions / Animation Sets). Click a card to
//                   focus the entity in the COMPOSER.
//
//   COMPOSER        right-side property panel for the focused entity.
//                   Reference fields render as thread chips (Threads.bb);
//                   clicking a chip jumps focus and pushes a back-stack
//                   entry. Esc pops the stack (or, if empty, closes the
//                   composer back to the browser).
//
// Esc behavior:
//   composer focused, back stack non-empty   ->  pop one step back
//   composer focused, back stack empty       ->  close composer
//   browser only (nothing focused)           ->  exit Loom
//
// Read-only in this alpha. Editing is a beta concern (needs save/dirty
// tracking that's its own design surface -- see docs/loom/decisions/002).
//
// Architecture: `Type Loom` owns instances of `Threads`, `Browser`, and
// `Composer`. The main loop calls `Loom::renderFrame(app)` once per frame.
// All three sub-modules are Types with Methods, called as
// `Module::method(self, args)` per the project's OO convention. See
// .claude/skills/blitzforge-language/SKILL.md "Module architecture" section.
// =============================================================================


// -----------------------------------------------------------------------------
// Bootstrap globals (mirrors GUE.bb so paths and log placement match -- both
// binaries live in bin/ and are launched from PM with CWD set to <proj>/Data).
// -----------------------------------------------------------------------------
Global rcceVersion$ = "2.0.0"
Global componentName$ = "loom"
Global RootDir$ = "..\"

ChangeDir RootDir$


// -----------------------------------------------------------------------------
// Per-tab dirty flags (shared with GUE). GUE declares the same set in GUE.bb
// at lines 47-48; declaring them here lets Loom write to them so the two
// editors see each other's dirty state. False = unsaved changes pending;
// True = on-disk == in-memory.
// -----------------------------------------------------------------------------
Global ItemsSaved = True
Global ActorsSaved = True
Global FactionsSaved = True
Global ParticlesSaved = True
Global DamageTypesSaved = True
Global ZoneSaved = True
Global AnimsSaved = True
Global StatsSaved = True
Global SpellsSaved = True
Global InterfaceSaved = True
Global ProjectilesSaved = True
Global EnvironmentSaved = True


// -----------------------------------------------------------------------------
// Includes
//
// Data layer: same modules GUE pulls in, minus UI-tied ones (F-UI,
// MediaDialogs, CharacterEditorLoader, ClientAreas). The loaders parse .dat
// files into the global type instances; Loom reads through those same
// instances so the two editors can't drift in how they parse the format.
//
// Order matters for Type declarations -- mirror GUE.bb's order.
// -----------------------------------------------------------------------------
Include "Modules\Path.bb"
Include "Modules\RCEnet.bb"
Include "Modules\Media.bb"
Include "Modules\MediaImport.bb"
Include "Modules\Projectiles.bb"
Include "Modules\Language.bb"
Include "Modules\Items.bb"
Include "Modules\Inventories.bb"
Include "Modules\Animations.bb"
Include "Modules\Spells.bb"
Include "Modules\Actors.bb"
Include "Modules\Environment.bb"
Include "Modules\Interface.bb"
// ClientAreas.bb deliberately omitted -- depends on GetFilename$ which lives
// inside GUE.bb. We don't need 3D zone meshes for the alpha; the composer
// renders zone metadata as text + portal chips. See
// docs/loom/decisions/004-deferred-3d-viewport.md.
Include "Modules\ServerAreas.bb"
Include "Modules\Packets.bb"
Include "Modules\Logging.bb"

// Loom UI layer. Order: Theme (constants) -> Threads (focus state) ->
// Browser / Composer (the two surfaces both depend on Threads) -> Palette
// (depends on Threads only) -> Ribbon (depends on Threads + Composer for
// its dirty-badge save dispatch) -> EntityFactory (free functions, last
// since it calls Threads::focus + reads the *Saved globals).
Include "Modules\Loom\Theme.bb"
// NameUtil + Clamp -- pure, dependency-free helpers (name-dedup for
// EntityFactory; parse-and-clamp for the Composer numeric fields).
// Included early; no deps.
Include "Modules\Loom\NameUtil.bb"
Include "Modules\Loom\Clamp.bb"
// SearchScore -- pure command-palette name ranking; Palette delegates to it.
Include "Modules\Loom\SearchScore.bb"
Include "Modules\Loom\Threads.bb"
// Settings BEFORE Composer so the LoomCfg_* / SettingsSaved globals
// are declared by the time Strict Composer methods reference them.
Include "Modules\Loom\Settings.bb"
// ImageCache also before Composer -- its globals + Loom_GetItemImage
// helper are called from renderItem.
Include "Modules\Loom\ImageCache.bb"
Include "Modules\Loom\Browser.bb"
Include "Modules\Loom\Composer.bb"
Include "Modules\Loom\Palette.bb"
Include "Modules\Loom\WorldCache.bb"
Include "Modules\Loom\BrokenRefs.bb"
Include "Modules\Loom\Ribbon.bb"
Include "Modules\Loom\Atlas.bb"
Include "Modules\Loom\Timeline.bb"
Include "Modules\Loom\Tools.bb"
Include "Modules\Loom\ScriptsCatalog.bb"
Include "Modules\Loom\ScriptSearch.bb"
Include "Modules\Loom\TextureCatalog.bb"
Include "Modules\Loom\MeshCatalog.bb"
Include "Modules\Loom\SoundCatalog.bb"
Include "Modules\Loom\MusicCatalog.bb"
Include "Modules\Loom\Recents.bb"
Include "Modules\Loom\EntityFactory.bb"
Include "Modules\Loom\SaveAll.bb"
Include "Modules\Loom\Help.bb"
Include "Modules\Loom\Toasts.bb"
// 3D mesh preview -- after ImageCache (shares Loom_BeginFrame infra)
// and after every other module that might need it. Pulls in Media.bb's
// GetMesh + Blitz3D 3D primitives.
Include "Modules\Loom\MeshPreview.bb"
// Zone schematic 3D viewport -- renders portal/spawn/trigger/waypoint
// markers in a 384x384 RT. Built on top of the same render-to-texture
// pattern as MeshPreview but with its own camera/light at y=20000 so
// the two previews can coexist without conflicting.
Include "Modules\Loom\ZoneViewport.bb"


// =============================================================================
// Loom -- top-level application type. Owns the Threads / Browser / Composer
// instances and orchestrates the render loop.
// =============================================================================
Type Loom
    Field windowWidth%
    Field windowHeight%
    Field projectName$
    Field threads.Threads
    Field browser.Browser
    Field composer.Composer
    Field palette.Palette
    Field ribbon.Ribbon
    Field atlas.Atlas
    Field timeline.Timeline
    Field brokenRefs.BrokenRefs
    Field recents.Recents
    Field worldCache.WorldCache
    Field exitPrompt.ExitPrompt
    Field help.Help
    Field scriptSearch.ScriptSearch
    Field toasts.Toasts


    Method create.Loom(windowWidth%, windowHeight%, projectName$)
        self\windowWidth = windowWidth
        self\windowHeight = windowHeight
        self\projectName = projectName$

        // Shared focus + back stack
        self\threads = New Threads()

        // World-state cache must exist before any surface that reads it
        // (Ribbon::recomputeCache, BrokenRefs::rebuild). Wired to the
        // global so mutation sites (Composer::commitEdit, EntityFactory
        // create/delete, Palette picker commit) can WorldCache_Invalidate
        // without an instance ref. Mirrors the Timeline / Recents
        // recorder facade pattern (ADR 005).
        self\worldCache = New WorldCache()
        LoomWorldCache = self\worldCache

        // Browser, Composer, Palette all hold a reference to the same Threads
        // instance; card clicks call Threads::focus, chip + palette-result
        // clicks call Threads::jump.
        self\browser = New Browser(self\threads)
        self\composer = New Composer(self\threads)
        // Module-level facade so non-Strict modules (e.g. ZoneViewport)
        // can dispatch into the composer without holding a reference.
        // Same recorder-facade pattern as LoomWorldCache (ADR 005).
        LoomComposer = self\composer
        self\palette = New Palette(self\threads)

        // Cross-link Palette <-> Composer for ref-field picker mode. The
        // Composer's chipRow opens the palette as a picker on right-click;
        // the Palette's commit path writes via Composer::writeField. The
        // cycle is fine because both refs are set after both instances
        // exist (no constructor-time call).
        Composer::setPalette(self\composer, self\palette)
        Palette::setComposer(self\palette, self\composer)

        // Composer reads Browser::hasSelection / Each SelectedEntity to
        // render the bulk-edit panel when the user has cards selected
        // but nothing focused. Set after both exist.
        Composer::setBrowser(self\composer, self\browser)

        // BrokenRefs modal -- shown when user clicks the ribbon's
        // broken-ref count chip. Holds a Threads ref for click-to-jump.
        self\brokenRefs = New BrokenRefs(self\threads)

        // Ribbon holds Threads (for click-to-jump from broken-ref chip) +
        // Composer (so a dirty-badge click can dispatch to the same
        // commitSaveForKind path the composer's Save button uses) +
        // BrokenRefs (clicking the broken-ref count chip opens the modal).
        self\ribbon = New Ribbon(self\threads, self\composer)
        Ribbon::setBrokenRefs(self\ribbon, self\brokenRefs)

        // Atlas holds a Threads reference for node-click focus dispatch.
        // The Browser activates / deactivates Atlas via Browser::setAtlas
        // and routes the viewport rect through to Atlas::renderAndUpdate
        // when the user toggles to atlas mode on the Zones tab.
        self\atlas = New Atlas(self\threads)
        Browser::setAtlas(self\browser, self\atlas)

        // Timeline holds Composer for revert dispatch. Module-level
        // recorder facade (Timeline_Record*) reaches the instance via
        // the LoomTimeline global, set immediately so the Composer's
        // commitEdit / EntityFactory's create+delete can record from
        // anywhere without an explicit reference.
        self\timeline = New Timeline()
        Timeline::setComposer(self\timeline, self\composer)
        LoomTimeline = self\timeline

        // Recents -- persisted per-project recently-focused list.
        // Threads::focus / jump emit via Recents_Record facade which
        // reaches the singleton via LoomRecents. Load any persisted
        // state from disk; persist back on shutdown (see end of main).
        self\recents = New Recents(self\threads)
        LoomRecents = self\recents
        Recents::load(self\recents)

        // ExitPrompt -- modal that intercepts Esc-exit when any kind is
        // dirty. Holds Composer for SaveAll_Persist dispatch on the
        // "Save All" button. Triggered by the Loom::renderFrame Esc
        // handler at exit time (see the chain below).
        self\exitPrompt = New ExitPrompt(self\composer)

        // Help (F1) -- static cheat sheet of every keybinding + mouse
        // interaction. No dependencies; just a paint surface.
        self\help = New Help()

        // Script search (Ctrl+F) -- modal that grep-searches every .rsl
        // in the Scripts catalog for a substring. Lets a designer find
        // every script that calls a BVM, references a faction by name,
        // mentions an item, etc. without leaving Loom.
        self\scriptSearch = New ScriptSearch(self\threads)

        // Toasts -- transient bottom-right notifications. Surfaces fire
        // via Toast_Show facade; the singleton renders auto-fades after
        // TOAST_TTL_MS. Same recorder-facade pattern as Timeline /
        // Recents / WorldCache (ADR 005).
        self\toasts = New Toasts()
        LoomToasts = self\toasts

        Return self
    End Method


    // -------------------------------------------------------------------------
    // renderFrame -- paint browser, then composer overlay if focused, then
    // palette overlay if open, then process global keys. Returns False when
    // the user wants to exit. Returning a bool from the frame is cleaner than
    // mutating an `app\quit` field; the loop owns its own control flow.
    //
    // Render-order rationale: browser at the back, composer on top of
    // browser, palette on top of everything. Palette dims the world behind
    // itself so it visually owns the frame while open.
    //
    // Input-order rationale: Ctrl+K opens the palette BEFORE the palette's
    // own pumpKeyboard fires (so the K keystroke isn't appended to its
    // query). When the palette is open, its pumpKeyboard owns Esc; the outer
    // Esc handler below only runs when the palette is closed.
    // -------------------------------------------------------------------------
    Method renderFrame%()
        Cls

        // Reset per-frame image load budget (ImageCache.bb). Bounds how
        // many fresh disk loads any one frame does so a tab switch with
        // many uncached thumbnails doesn't freeze the frame and tank
        // input responsiveness. Subsequent frames warm the cache.
        Loom_BeginFrame()

        // Ctrl+K opens the palette / Ctrl+H opens the timeline (each
        // no-ops if already open). Detect BEFORE any other input handler
        // so openModal's FlushKeys swallows the K/H keystroke before it
        // can land in a query buffer.
        If Palette::isOpen(self\palette) = False And Timeline::isOpen(self\timeline) = False And BrokenRefs::isOpen(self\brokenRefs) = False And Recents::isOpen(self\recents) = False And ExitPrompt::isOpen(self\exitPrompt) = False And Help::isOpen(self\help) = False And ScriptSearch::isOpen(self\scriptSearch) = False
            If (KeyDown(29) Or KeyDown(157)) And KeyHit(37)
                Palette::openModal(self\palette)
            Else If (KeyDown(29) Or KeyDown(157)) And KeyHit(35)
                Timeline::openModal(self\timeline)
            Else If (KeyDown(29) Or KeyDown(157)) And KeyHit(19)
                Recents::openModal(self\recents)
            Else If (KeyDown(29) Or KeyDown(157)) And KeyHit(31)
                // Ctrl+S -- Save All across every dirty kind. Per the
                // "drop-in for GUE" goal, the user expects one shortcut
                // to write everything; the per-kind Save buttons are
                // for when you only want to save one tab's state.
                SaveAll_Persist(self\composer)
            Else If (KeyDown(29) Or KeyDown(157)) And KeyHit(33)
                // Ctrl+F -- find in scripts. Grep across every .rsl in
                // the catalog. Closes the "where is BVM_X called?" gap.
                ScriptSearch::openModal(self\scriptSearch)
            Else If KeyHit(59)
                // F1 -- cheat sheet. No Ctrl required since F-keys are
                // unambiguous discovery affordances.
                Help::openModal(self\help)
            EndIf
        EndIf

        // Browser input is enabled only when no higher-priority surface is
        // already consuming keystrokes. Priority chain (highest first):
        //   any-modal > composer-edit > browser filter
        Local browserInput% = True
        If Timeline::isOpen(self\timeline) = True Then browserInput = False
        If Palette::isOpen(self\palette) = True Then browserInput = False
        If BrokenRefs::isOpen(self\brokenRefs) = True Then browserInput = False
        If Recents::isOpen(self\recents) = True Then browserInput = False
        If ExitPrompt::isOpen(self\exitPrompt) = True Then browserInput = False
        If Help::isOpen(self\help) = True Then browserInput = False
        If ScriptSearch::isOpen(self\scriptSearch) = True Then browserInput = False
        If Composer::isEditing(self\composer) = True Then browserInput = False
        // A focused zone shows the full-screen 3D viewport over the card
        // grid; the grid (and its type-to-filter) is hidden, and the
        // viewport owns WASD/QE for fly movement -- so silence the browser
        // keyboard to keep fly keys out of the (invisible) card filter.
        If self\threads\focusKind = "zone" Then browserInput = False

        // Pass composer width so the browser's card grid shrinks to
        // avoid right-column cards being half-hidden behind the panel.
        // Composer::width returns 0 when nothing focused, else CMP_W.
        Local composerW% = Composer::width(self\composer)
        Browser::renderAndUpdate(self\browser, self\windowWidth, self\windowHeight, self\projectName, browserInput, composerW)
        Composer::renderAndUpdate(self\composer, self\windowWidth, self\windowHeight)

        // Conscience Ribbon last among the on-canvas surfaces -- it
        // overlays the top LOOM_TOP_RIBBON_H pixels of whatever Browser /
        // Composer painted there (which is just the top of the brand
        // strip, harmless to overwrite). Sits BELOW the modal overlays.
        Ribbon::renderAndUpdate(self\ribbon, self\windowWidth)

        // Modal overlays. Order matters only for visual stacking when
        // multiple are somehow open at once (shouldn't happen -- each
        // openModal closes the others implicitly via closeModal call
        // chains in the user flow). Each consumes its own keys (including
        // Esc) when open and returns True so the outer Esc handler skips.
        Local timelineAte%   = Timeline::renderAndUpdate(self\timeline, self\windowWidth, self\windowHeight)
        Local brokenRefsAte% = BrokenRefs::renderAndUpdate(self\brokenRefs, self\windowWidth, self\windowHeight)
        Local recentsAte%    = Recents::renderAndUpdate(self\recents, self\windowWidth, self\windowHeight)
        Local paletteAte%    = Palette::renderAndUpdate(self\palette, self\windowWidth, self\windowHeight)
        Local exitPromptAte% = ExitPrompt::renderAndUpdate(self\exitPrompt, self\windowWidth, self\windowHeight)
        Local helpAte%       = Help::renderAndUpdate(self\help, self\windowWidth, self\windowHeight)
        Local scriptSearchAte% = ScriptSearch::renderAndUpdate(self\scriptSearch, self\windowWidth, self\windowHeight)
        Local modalAte%      = (timelineAte Or brokenRefsAte Or recentsAte Or paletteAte Or exitPromptAte Or helpAte Or scriptSearchAte)

        // Toasts paint on top of everything (above modals) so success/
        // failure feedback is visible even while a modal is up. They
        // don't consume input.
        Toasts::render(self\toasts, self\windowWidth, self\windowHeight)

        // If ExitPrompt's Save All / Discard All button closed the modal
        // with exitConfirmed = True, honor the user's decision NOW and
        // exit the main loop. This sits between the modal-render and the
        // Esc handler so the exit fires the same frame the user clicks.
        If ExitPrompt::isExitConfirmed(self\exitPrompt) = True Then Return False

        // Esc priority (when no modal ate the press):
        //   filter clear > back-stack pop > close composer > exit Loom
        If modalAte = False And KeyHit(1)   // Esc
            If Browser::hasFilter(self\browser) = True
                Browser::clearFilter(self\browser)
            Else If Browser::hasSelection(self\browser) = True
                Browser::clearSelection(self\browser)
            Else If Threads::back(self\threads) = False
                If self\threads\focusKind <> ""
                    // Close composer back to plain browser.
                    Threads::focus(self\threads, "", 0)
                    Threads::clearStack(self\threads)
                    WriteLog(LoomLog, "Esc: closed composer")
                Else
                    // Nothing left to close. If any kind is dirty, open
                    // the ExitPrompt modal so unsaved work isn't lost
                    // silently. The modal's Save All / Discard All
                    // buttons set exitConfirmed which the next frame
                    // observes (above) to break the main loop.
                    If SaveAll_AnyDirty() = True
                        ExitPrompt::openModal(self\exitPrompt)
                    Else
                        Return False
                    EndIf
                EndIf
            EndIf
        EndIf

        Flip
        Return True
    End Method
End Type


// =============================================================================
// Main -- bootstrap graphics, load data, build the Loom app, run frames.
// =============================================================================

// Graphics mode -- match GUE's window sizing so the two editors feel sibling.
Local boot_width# = GetSystemMetrics(0) * 0.9
Local boot_height# = GetSystemMetrics(1) * 0.8
If (boot_width < 1280 And boot_height < 800)
    boot_width = 1280
    boot_height = 800
EndIf

Graphics3D(boot_width, boot_height, 0, 2)
SetBuffer(BackBuffer())
AppTitle("Loom -- World Editor (Alpha) -- Realm Crafter " + rcceVersion$)

// Log -- Data\Logs\Loom Log.txt (relative to project root, next to GUE's log).
Global LoomLog = StartLog("Loom Log", False)
WriteLog(LoomLog, "** Loom startup begins **", True, True)
WriteLog(LoomLog, "Resolution: " + Str(boot_width) + "x" + Str(boot_height))

// Resolve project name from the working directory leaf.
Local cwd$ = CurrentDir$()
Local projectName$ = LoomGetLeafDir(cwd$)
WriteLog(LoomLog, "Project root: " + cwd$)
WriteLog(LoomLog, "Project name: " + projectName$)

LoomTheme_Init()
Tools_Init()
; Scripts catalog: scan Data\Server Data\Scripts\*.rsl so the Scripts
; browser tab + reverse-ref scanners have something to walk. Tolerant
; of a missing dir (fresh project); logs ScriptsScanError$ for ribbon
; surfacing on a permission/disk failure.
Scripts_Init()
WriteLog(LoomLog, "Scripts catalog: " + Str(ScriptsTotalCount) + " .rsl files indexed")
; Texture catalog: walks Textures.dat's full 65535-slot index, populates
; one TextureEntry per defined slot. <50ms with LockTextures keeping
; the file warm; powers the Textures browser tab.
Textures_Init()
WriteLog(LoomLog, "Texture catalog: " + Str(TexturesTotalCount) + " textures indexed")
; Mesh catalog: same shape as the texture scan, against Meshes.dat.
Meshes_Init()
WriteLog(LoomLog, "Mesh catalog: " + Str(MeshesTotalCount) + " meshes indexed")
; Sound catalog: completes the asset trio. Composer view gets a Play
; button for in-place audition -- not present in GUE at all.
Sounds_Init()
WriteLog(LoomLog, "Sound catalog: " + Str(SoundsTotalCount) + " sounds indexed")
; Music catalog: final media family. No reverse refs (music isn't
; statically referenced from data Loom edits); audition + browse only.
Music_Init()
WriteLog(LoomLog, "Music catalog: " + Str(MusicTotalCount) + " tracks indexed")


// -----------------------------------------------------------------------------
// Load project data. Same order GUE uses, same loaders, same in-memory
// representation. Failures RuntimeError with a Win32 dialog -- mirrors
// GUE.bb's behavior; a half-loaded project would just confuse the user later.
// -----------------------------------------------------------------------------
WriteLog(LoomLog, "** Loading project data **")
Loom_DrawLoadingScreen("Loading project data...")

Loom_LoadStep("damage types", LoadDamageTypes("Data\Server Data\Damage.dat"), False)
Loom_LoadStep("attributes",   LoadAttributes("Data\Server Data\Attributes.dat"), False)
Loom_LoadStep("factions",     LoadFactions("Data\Server Data\Factions.dat"), True)
Loom_LoadStep("animations",   LoadAnimSets("Data\Game Data\Animations.dat"), True)

Global TotalProjectiles = LoadProjectiles("Data\Server Data\Projectiles.dat")
If TotalProjectiles = -1 Then RuntimeError("Loom could not open Data\Server Data\Projectiles.dat")
WriteLog(LoomLog, "Loaded " + Str(TotalProjectiles) + " projectiles")

Global TotalItems = LoadItems("Data\Server Data\Items.dat")
If TotalItems = -1 Then RuntimeError("Loom could not open Data\Server Data\Items.dat")
WriteLog(LoomLog, "Loaded " + Str(TotalItems) + " items")

Global TotalActors = LoadActors("Data\Server Data\Actors.dat")
If TotalActors = -1 Then RuntimeError("Loom could not open Data\Server Data\Actors.dat")
WriteLog(LoomLog, "Loaded " + Str(TotalActors) + " actors")

Global TotalSpells = LoadSpells("Data\Server Data\Spells.dat")
If TotalSpells = -1 Then RuntimeError("Loom could not open Data\Server Data\Spells.dat")
WriteLog(LoomLog, "Loaded " + Str(TotalSpells) + " spells")

Global TotalZones = 0
Local zoneDir = ReadDir("Data\Server Data\Areas")
Local zoneFile$ = NextFile$(zoneDir)
While zoneFile$ <> ""
    If FileType("Data\Server Data\Areas\" + zoneFile$) = 1 And Len(zoneFile$) > 4
        ServerLoadArea(Left$(zoneFile$, Len(zoneFile$) - 4))
        TotalZones = TotalZones + 1
    EndIf
    zoneFile$ = NextFile$(zoneDir)
Wend
CloseDir(zoneDir)
WriteLog(LoomLog, "Loaded " + Str(TotalZones) + " zones")

// Project-level config (Misc/Hosts/Other/Money .dat). Loom-side reader
// since ClientLoaders.bb isn't included. Tolerant of missing files for
// half-set-up projects.
Loom_LoadSettings()

// 3D mesh preview infrastructure -- creates a hidden camera + light +
// render-to-texture. Used by the actor composer to show a spinning
// preview of the actor's base mesh. Initialized once at boot; freed
// at shutdown via Loom_ShutdownMeshPreview.
Loom_InitMeshPreview()

// Zone schematic viewport -- separate camera/light/RT at y=20000.
// Used by the zone composer to show portal/spawn/trigger/waypoint
// markers in a 3D viewport.
Loom_InitZoneViewport()

WriteLog(LoomLog, "** Data load complete **")


// -----------------------------------------------------------------------------
// Construct the app instance and run frames until renderFrame returns False.
// -----------------------------------------------------------------------------
Local app.Loom = New Loom(boot_width, boot_height, projectName)
WriteLog(LoomLog, "** Main loop running **")

While Loom::renderFrame(app) = True
Wend

WriteLog(LoomLog, "** Loom shutdown **")
// Persist the per-project recents list to Data/Loom/recents.txt so the
// next session can show "where was I" without rebuilding from scratch.
If app\recents <> Null Then Recents::persist(app\recents)
// Free 3D preview GPU resources before exit.
Loom_ShutdownMeshPreview()
Loom_ShutdownZoneViewport()
CloseAllLogs()
End


// =============================================================================
// Loom_LoadStep -- route the inconsistent loader return-value conventions
// (some return -1 on failure, some return False) through a single check.
// =============================================================================
Function Loom_LoadStep(stepName$, result, isMinusOneFailure)
    Local failed = False
    If isMinusOneFailure = True
        If result = -1 Then failed = True
    Else
        If result = False Then failed = True
    EndIf

    If failed = True
        WriteLog(LoomLog, "LOAD FAILED: " + stepName$)
        RuntimeError("Loom could not load " + stepName$ + ". Make sure the project's Data folder is intact and try again.")
    EndIf

    WriteLog(LoomLog, "Loaded " + stepName$)
End Function


// =============================================================================
// Loom_DrawLoadingScreen -- single-frame loading message while the data
// loaders run. The loads are fast enough on modern disks that an animated
// progress would just flicker.
// =============================================================================
Function Loom_DrawLoadingScreen(msg$)
    Cls
    LoomGradientV(0, 0, GraphicsWidth(), GraphicsHeight(), LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B, LOOM_STONE_950_R, LOOM_STONE_950_G, LOOM_STONE_950_B)
    Local cx = GraphicsWidth() / 2
    Local cy = GraphicsHeight() / 2
    LoomTextCentered(cx, cy - 10, "LOOM", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
    LoomTextCentered(cx, cy + 10, msg$, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
    Flip
End Function


// =============================================================================
// LoomGetLeafDir -- leaf folder name from a directory path.
// =============================================================================
Function LoomGetLeafDir$(path$)
    Local trimmed$ = path$
    While Len(trimmed$) > 1 And (Right$(trimmed$, 1) = "\" Or Right$(trimmed$, 1) = "/")
        trimmed$ = Left$(trimmed$, Len(trimmed$) - 1)
    Wend

    Local lastSep = 0
    Local i = 0
    For i = 1 To Len(trimmed$)
        Local ch$ = Mid$(trimmed$, i, 1)
        If ch$ = "\" Or ch$ = "/" Then lastSep = i
    Next

    If lastSep = 0 Then Return trimmed$
    Return Mid$(trimmed$, lastSep + 1)
End Function
