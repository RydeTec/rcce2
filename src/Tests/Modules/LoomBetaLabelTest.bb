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
	Assert(FileContains%("Loom.bb", "Loom World Editor (Beta)") = True)
End Test

Test testLoomGuidanceMatchesTheBetaLauncher()
	Assert(FileContains%("docs\\loom\\README.md", "**Loom (Beta)**") = True)
	Assert(FileContains%("CLAUDE.md", "## Loom (beta redesigned editor)") = True)
End Test
