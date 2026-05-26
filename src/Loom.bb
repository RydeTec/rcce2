// =============================================================================
// Loom.bb -- Loom World Editor (Alpha)
// =============================================================================
//
// A drop-in alternative to GUE, sharing the on-disk data formats but with
// a fresh UI built around the Loom design concept (see
// .claude/skills/loom-design-brief/ and the prototype handoff bundle).
//
// Architecture overview (the multi-PR roadmap; this commit ships only #1):
//
//   #1  Skeleton + theme + Project Manager launcher
//         Loom.exe compiles, Project Manager launches it,
//         shows a themed splash, exits cleanly. THIS COMMIT.
//
//   #2  Data loading + atlas
//         Loom uses GUE's existing data modules (Items.bb, Actors.bb,
//         Spells.bb, ServerAreas.bb, ...) via Include. After load,
//         the atlas surface lists every zone in the project.
//
//   #3  World view
//         Picking a zone in the atlas renders it in a 3D viewport
//         using Blitz3D's engine (the same engine GUE's Zones tab uses).
//         Click an entity to select it.
//
//   #4  Composer
//         Right-side property panel that paints the focused entity's
//         data (faction, level, mesh, equipped items) using Loom theme
//         primitives. Read-only for the alpha.
//
// Design intent for the alpha as a whole: "Loom can open my existing
// Realm Crafter project and let me look at my world through a different
// lens." Editing comes in beta.
// =============================================================================


// -----------------------------------------------------------------------------
// Bootstrap globals (mirrors GUE.bb's startup so the relative paths work
// identically -- both binaries live in bin/ and are launched with CWD set to
// <project>/Data/).
// -----------------------------------------------------------------------------
Global rcceVersion$ = "2.0.0"
Global componentName$ = "loom"
Global RootDir$ = "..\"

ChangeDir RootDir$


// -----------------------------------------------------------------------------
// Includes
//
// Data layer: the same modules GUE includes for its data layer, MINUS the
// UI-tied ones (F-UI, MediaDialogs, CharacterEditorLoader). The loaders here
// just parse .dat files into the global type instances (ItemList, ActorList,
// SpellsList, Each Area, ...). Loom reads through these same in-memory
// instances so anything GUE can edit, Loom can see.
//
// Order matters: types must be declared before any code that uses them in
// later includes. We mirror GUE.bb's include order to stay in lockstep.
// -----------------------------------------------------------------------------
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
// NOTE: ClientAreas.bb deliberately omitted -- it depends on GetFilename$,
// which lives inside GUE.bb itself (not in a shared module). ClientAreas
// loads the 3D zone mesh; we don't need that for the atlas. PR #3 will
// pull it in via either extracting GetFilename$ to a shared helper or
// defining a Loom-side zone-mesh loader.
Include "Modules\ServerAreas.bb"
Include "Modules\Packets.bb"
Include "Modules\Logging.bb"

// Loom UI layer.
Include "Modules\Loom\Theme.bb"
Include "Modules\Loom\Atlas.bb"
Include "Modules\Loom\ZoneMap.bb"
Include "Modules\Loom\Composer.bb"


// -----------------------------------------------------------------------------
// Graphics mode -- match GUE's window sizing so the two editors feel sibling.
// -----------------------------------------------------------------------------
Local Loom_width# = GetSystemMetrics(0) * 0.9
Local Loom_height# = GetSystemMetrics(1) * 0.8
If (Loom_width < 1280 And Loom_height < 800)
    Loom_width = 1280
    Loom_height = 800
EndIf

Graphics3D(Loom_width, Loom_height, 0, 2)
SetBuffer(BackBuffer())
AppTitle("Loom -- World Editor (Alpha) -- Realm Crafter " + rcceVersion$)


// -----------------------------------------------------------------------------
// Log -- written to Data\Logs\Loom Log.txt (relative to project root, the
// same place GUE writes its log).
// -----------------------------------------------------------------------------
Global LoomLog = StartLog("Loom Log", False)
WriteLog(LoomLog, "** Loom startup begins **", True, True)
WriteLog(LoomLog, "Resolution: " + Str(Loom_width) + "x" + Str(Loom_height))


// -----------------------------------------------------------------------------
// Resolve project name from the working directory. When PM launches us, CWD
// has been set to <project>/Data/ and then ChangeDir "..\" walked us up to
// <project>/. The leaf folder name is the project's display name.
// -----------------------------------------------------------------------------
Local cwd$ = CurrentDir$()
Global LoomProjectName$ = LoomGetLeafDir(cwd$)
WriteLog(LoomLog, "Project root: " + cwd$)
WriteLog(LoomLog, "Project name: " + LoomProjectName$)

LoomTheme_Init()


// -----------------------------------------------------------------------------
// Load project data. Same order GUE uses, same loaders, same in-memory
// representation. Loom never reads the .dat files directly -- it always
// goes through these loaders so the two editors can't drift apart in how
// they parse the files.
//
// Failure mode: if a required .dat is missing or unreadable, RuntimeError
// shows a Win32 dialog and exits. Mirrors GUE.bb's behavior; a half-loaded
// project would just confuse the user later.
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

// Server-side zones: every .dat in Data\Server Data\Areas\ is a zone.
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

WriteLog(LoomLog, "** Data load complete **")


// -----------------------------------------------------------------------------
// Build the atlas tile list now that Each Area is populated.
// -----------------------------------------------------------------------------
Atlas_Init()


// -----------------------------------------------------------------------------
// Main loop -- two surfaces, switched by clicks:
//
//   ATLAS  : zone picker (Atlas.bb). Click a zone to open it.
//   MAP    : top-down map of the opened zone (ZoneMap.bb). Click entities
//            to select. Back button or Esc returns to atlas.
//
// From the atlas, Esc exits Loom.
// From the map, Esc returns to the atlas (the user has to be back at the
// atlas to exit -- prevents accidentally killing the editor mid-edit).
// -----------------------------------------------------------------------------
WriteLog(LoomLog, "** Main loop running **")

Const LOOM_MODE_ATLAS = 1
Const LOOM_MODE_MAP   = 2
Global LoomMode = LOOM_MODE_ATLAS

Global LoomSelectedZone = 0   // Handle(Area); set when the user picks in atlas

Repeat
    Cls

    If LoomMode = LOOM_MODE_ATLAS
        Local pickedHandle = Atlas_RenderAndUpdate(Loom_width, Loom_height, LoomProjectName$)
        If pickedHandle <> 0
            LoomSelectedZone = pickedHandle
            ZoneMap_Open(LoomSelectedZone)
            LoomMode = LOOM_MODE_MAP
        EndIf

        // Esc exits from the atlas.
        If KeyHit(1) Then Exit
    EndIf

    If LoomMode = LOOM_MODE_MAP
        Local backRequested = ZoneMap_RenderAndUpdate(Loom_width, Loom_height)
        // Composer paints on top of the zone map if anything is selected;
        // ZoneMap reserves Composer_Width() pixels on the right so markers
        // along the right edge don't sit hidden behind the panel.
        Composer_RenderIfVisible(Loom_width, Loom_height)
        // Esc also returns to atlas (does not exit Loom from the map).
        If backRequested = True Or KeyHit(1)
            LoomMode = LOOM_MODE_ATLAS
            WriteLog(LoomLog, "Returned to atlas from zone map")
        EndIf
    EndIf

    Flip
Until False

WriteLog(LoomLog, "** Loom shutdown **")
CloseAllLogs()
End


// =============================================================================
// Loom_LoadStep -- check the return value of a Load* call, log it, RuntimeError
// on failure. isMinusOneFailure: True if the loader returns -1 on failure
// (LoadFactions, LoadAnimSets), False if it returns False (LoadDamageTypes,
// LoadAttributes). Mirrors the inconsistent return-value conventions of GUE's
// own loaders -- we don't reshape those here, just route them.
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
// Loom_DrawLoadingScreen -- show a single-frame loading message while the
// data loaders run. Called once before the slow Load* calls; the actual
// progress isn't streamed because the loads are fast enough on modern disks
// that an animated splash would just flicker.
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
// Loom_DrawSelectionToast -- bottom-left transient banner confirming which
// zone the user just clicked. Replaced in PR #3 by an actual world-view
// hand-off; here it's the visible feedback that the atlas click was received.
// =============================================================================
Function Loom_DrawSelectionToast(sw, sh, zoneName$)
    Local toastW = 360
    Local toastH = 56
    Local toastX = 20
    Local toastY = sh - ATLAS_BOT_RIBBON - toastH - 12

    LoomFill(toastX, toastY, toastW, toastH, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
    LoomBorder(toastX, toastY, toastW, toastH, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
    LoomBorder(toastX + 1, toastY + 1, toastW - 2, toastH - 2, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)

    LoomText(toastX + 12, toastY + 10, "Selected", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
    LoomText(toastX + 12, toastY + 28, zoneName$, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
End Function


// =============================================================================
// LoomGetLeafDir -- return the leaf folder name from a directory path.
// E.g. "C:\rcce2\projects\Embergloom" -> "Embergloom".
// Falls back to the whole path if no separator is found.
// =============================================================================
Function LoomGetLeafDir$(path$)
    Local trimmed$ = path$
    // Strip trailing slashes / backslashes so the leaf isn't an empty string.
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
