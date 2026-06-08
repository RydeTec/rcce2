Strict

// =============================================================================
// Loom/Tools.bb -- launcher for GUE's standalone editor tools
// =============================================================================
//
// A drop-in GUE replacement needs to let users launch the seven standalone
// editor binaries that ship under bin/tools/. GUE links them via the
// Project Manager's menu; Loom puts them on a dedicated Tools tab in the
// browser, treating each as a clickable card whose action is "launch the
// .exe with the project's Data/ folder as the working directory."
//
// Tool catalog (matches src/Project Manager.bb's set):
//   RC Architect       bin\tools\RC Architect.exe       prefab placement
//   RC Terrain Editor  bin\tools\RC Terrain Editor.exe  heightmap painting
//   RC Caves Editor    bin\tools\RC Caves Editor.exe    voxel cave geometry
//   RC Rock Editor     bin\tools\RC Rock Editor.exe     rock cluster placement
//   RC Tree Editor     bin\tools\RC Tree Editor.exe     tree placement
//   Gubbin Tool        bin\tools\Gubbin Tool.exe        item-mesh attach points
//   RC Spell Wizard    bin\tools\RC Spell Wizard\...    spell scaffolding
//
// Working directory: each tool expects to run with CWD = <project>/Data/
// (same convention GUE / Loom use for their data paths). Loom's runtime
// CWD is the project root (after the boot-time ChangeDir "..\"), so we
// pass CurrentDir$() + "Data\\" as the third ExecFile arg.
//
// Architecture: free-function module + a Type ToolDef populated at boot
// (Type pool walk for rendering). Tools::launch routes to ExecFile.


Type ToolDef
    Field Name$         // display name on the card
    Field ExePath$      // path relative to project root
    Field Description$  // short hint shown on the card
End Type


// =============================================================================
// Tools_Init -- register the seven standard tools. Called once at boot
// from Loom.bb after the data loaders run. ToolDef instances are
// allocated into the global type pool and live for the Loom session.
// =============================================================================
Function Tools_Init()
    Tools_Register("RC Architect",      "bin\tools\RC Architect.exe",      "Place scenery and prefabs in a zone")
    Tools_Register("Terrain Editor",    "bin\tools\RC Terrain Editor.exe", "Paint heightmap terrain and textures")
    Tools_Register("Caves Editor",      "bin\tools\RC Caves Editor.exe",   "Sculpt voxel cave geometry")
    Tools_Register("Rock Editor",       "bin\tools\RC Rock Editor.exe",    "Place rock clusters")
    Tools_Register("Tree Editor",       "bin\tools\RC Tree Editor.exe",    "Place trees and foliage")
    Tools_Register("Gubbin Tool",       "bin\tools\Gubbin Tool.exe",       "Adjust item / gubbin mesh attach points")
    Tools_Register("Spell Wizard",      "bin\tools\RC Spell Wizard\RC Spell Wizard.exe", "Scaffold a new spell from a template")
End Function


Function Tools_Register(name$, exePath$, description$)
    Local t.ToolDef = New ToolDef()
    t\Name = name
    t\ExePath = exePath
    t\Description = description
End Function


// =============================================================================
// Tools_Launch -- ExecFile a tool with the project's Data/ folder as CWD.
// Returns True on success (the tool process was spawned), False if the
// .exe doesn't exist on disk (probably a partial build).
//
// Why not just hand the click to ExecFile directly: we want to log every
// launch + a missing-binary check so the user gets a hint when nothing
// appears instead of a silent no-op.
// =============================================================================
Function Tools_Launch%(t.ToolDef)
    If t = Null Then Return False

    // Sanity-check the binary exists -- a partial bin/ (e.g. compile.bat
    // -t was run instead of full compile) is a common gotcha.
    If FileType(t\ExePath) <> 1
        WriteLog(LoomLog, "Tools: " + t\Name + " -- binary missing at " + t\ExePath)
        Return False
    EndIf

    Local workDir$ = CurrentDir$() + "Data\"
    ExecFile(t\ExePath, "", workDir)
    WriteLog(LoomLog, "Tools: launched " + t\Name + " (cwd " + workDir + ")")
    Return True
End Function


// =============================================================================
// Tools_FindByName -- helper used by the Browser's per-card click to
// resolve the cached display name back to the ToolDef. Walks the global
// pool (small N, no need for an index).
// =============================================================================
Function Tools_FindByName.ToolDef(name$)
    Local t.ToolDef
    For t = Each ToolDef
        If t\Name = name Then Return t
    Next
    Return Null
End Function
