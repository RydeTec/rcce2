Strict
EnableGC

; ClientAreas_FE.bb includes the legacy client-area graph (Gooey, media,
; terrain, particles, and runtime globals), so it cannot be loaded into the
; standalone test runner safely. Pin its persistence boundary as a source
; contract instead: SaveArea must stage the complete binary write in a temp
; file and promote it through the shared atomic-write helper.

Function FunctionBodyContains%(Path$, FunctionMarker$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local InFunction%
	Local Line$
	; test.sh runs from src\Tests while IDEs commonly run from src.
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False
	InFunction = False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, FunctionMarker$) > 0 Then InFunction = True
		If InFunction = True And Instr(Line$, Needle$) > 0
			CloseFile F
			Return True
		EndIf
		If InFunction = True And Instr(Line$, "End Function") > 0 Then Exit
	Wend
	CloseFile F
	Return False
End Function

Test testSaveAreaStagesAndAtomicallyPromotesAreaFile()
	Assert(FunctionBodyContains%("Modules\ClientAreas_FE.bb", "Function SaveArea(Name$)", "SafeWriteOpen(FinalPath$)") = True)
	Assert(FunctionBodyContains%("Modules\ClientAreas_FE.bb", "Function SaveArea(Name$)", "WriteFile(TempPath$)") = True)
	Assert(FunctionBodyContains%("Modules\ClientAreas_FE.bb", "Function SaveArea(Name$)", "SafeWriteCommit(TempPath$, FinalPath$, F)") = True)
End Test
