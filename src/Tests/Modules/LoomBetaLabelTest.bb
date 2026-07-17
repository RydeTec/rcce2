Strict
EnableGC

; Source-contract regression for Loom's current-facing beta identity. Loom and
; Project Manager pull the full editor/UI graph, so this focused test reads the
; bounded labels instead of including either executable entry point.

Function FileContains%(Path$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then F = ReadFile("..\\..\\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, Needle$) > 0
			CloseFile F
			Return True
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Test testLoomLauncherAndWindowIdentifyTheShippedBeta()
	Assert(FileContains%("Project Manager.bb", "Loom (Beta)") = True)
	Assert(FileContains%("Loom.bb", "AppTitle(" + Chr$(34) + "Loom -- World Editor (Beta) -- Realm Crafter ") = True)
End Test

Test testLoomGuidanceMatchesTheBetaLauncher()
	Assert(FileContains%("docs\\loom\\README.md", "**Loom (Beta)**") = True)
	Assert(FileContains%("CLAUDE.md", "## Loom (beta redesigned editor)") = True)
End Test

Test testCurrentFacingGuidanceCannotRegressToAlpha()
	Assert(FileContains%("Project Manager.bb", "Loom (Alpha)") = False)
	Assert(FileContains%("Loom.bb", "AppTitle(" + Chr$(34) + "Loom -- World Editor (Alpha) -- Realm Crafter ") = False)
	Assert(FileContains%("Loom.bb", "Read-only in this alpha") = False)
	Assert(FileContains%("docs\\loom\\README.md", "**Loom (Alpha)**") = False)
	Assert(FileContains%("CLAUDE.md", "## Loom (alpha redesigned editor)") = False)
	Assert(FileContains%("CLAUDE.md", "Read-only in the alpha; editing is a beta concern") = False)
End Test

Test testLoomGuidanceIdentifiesTheShippedWorldMode()
	Assert(FileContains%("docs\\loom\\README.md", "World mode renders the focused zone's real terrain, scenery, and water") = True)
	Assert(FileContains%("docs\\loom\\README.md", "falls back to schematic mode when a zone has no visual `.dat`") = True)
	Assert(FileContains%("docs\\loom\\README.md", "Terrain sculpting, water-volume editing, weather/environment editing, or scenery texture painting.") = True)
	Assert(FileContains%("docs\\loom\\architecture.md", "Loom can render the 3D zone mesh in World mode") = True)
	Assert(FileContains%("docs\\loom\\roadmap.md", "Out-of-scope items (walk-in playtest and multi-cursor) remain in the deferred list below") = True)
	Assert(FileContains%("docs\\loom\\README.md", "Render zones in 3D.") = False)
	Assert(FileContains%("docs\\loom\\architecture.md", "Loom cannot render the 3D zone mesh.") = False)
	Assert(FileContains%("docs\\loom\\roadmap.md", "Out-of-scope items (3D viewport, walk-in playtest, multi-cursor)") = False)
End Test
