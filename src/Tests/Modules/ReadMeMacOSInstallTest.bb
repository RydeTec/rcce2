Strict
EnableGC

; Source-contract regression for the public macOS install path. The README is
; the onboarding contract here, so read its bounded wording rather than pulling
; any runtime module into this focused test.

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

Test testWindowsReleaseInstructionsDoNotPromiseAnOSAgnosticBuild()
	Assert(FileContains%("ReadMe.md", "### Install a release (Windows)") = True)
	Assert(FileContains%("ReadMe.md", "Grab the latest build for your OS") = False)
	Assert(FileContains%("ReadMe.md", "Project Manager` on macOS") = False)
End Test

Test testMacOSInstructionsPointToTheSupportedSourceBuildPath()
	Assert(FileContains%("ReadMe.md", "### macOS (Apple Silicon, alpha)") = True)
	Assert(FileContains%("ReadMe.md", "macOS currently has no downloadable release package") = True)
	Assert(FileContains%("ReadMe.md", "[macOS Apple Silicon notes](docs/macos-apple-silicon.md)") = True)
End Test
