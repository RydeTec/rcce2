Strict
EnableGC

// GUE's full editor graph is not safe for the standalone test harness. These
// bounded source contracts pin the Days & seasons save-outcome handoff instead.

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

Function CountLinesContaining%(Path$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Count% = 0
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then F = ReadFile("..\\..\\" + Path$)
	If F = Null Then Return 0

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, Needle$) > 0 Then Count = Count + 1
	Wend

	CloseFile F
	Return Count
End Function

Function SaveEnvironmentAndSunsContract%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local InHelper% = False
	Local SavedEnvironment% = False
	Local SavedSuns% = False
	Local ReturnsAggregate% = False
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then F = ReadFile("..\\..\\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function SaveEnvironmentAndSuns%()") > 0 Then InHelper = True
		If InHelper = True
			If Instr(Line$, "If SaveEnvironment(True) = False Then SavedAll = False") > 0 Then SavedEnvironment = True
			If Instr(Line$, "If SaveSuns() = False Then SavedAll = False") > 0 Then SavedSuns = True
			If SavedEnvironment = True And SavedSuns = True
				If Instr(Line$, "Return SavedAll") > 0 Then ReturnsAggregate = True
			EndIf
			If Instr(Line$, "End Function") > 0
				CloseFile F
				Return ReturnsAggregate
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Test testGUEEnvironmentSaveRequiresBothAtomicWrites()
	Local Source$ = "GUE.bb"
	Assert(SaveEnvironmentAndSunsContract%(Source$) = True)
End Test

Test testGUEEnvironmentSaveRoutesKeepDirtyStateOnFailure()
	Local Source$ = "GUE.bb"
	Assert(CountLinesContaining%(Source$, "EnvironmentSaved = SaveEnvironmentAndSuns()") = 4)
	Assert(FileContains%(Source$, "If FUI_SendMessage(List, M_GETCAPTION) <> " + Chr$(34) + "Days & seasons" + Chr$(34) + " Or EnvironmentSaved = True") = True)
	Assert(FileContains%(Source$, "If EnvironmentSaved = False Then Result = False") = True)
	Assert(FileContains%(Source$, "If ParticlesSaved = True And DamageTypesSaved = True And EnvironmentSaved = True Then Result = True") = True)
End Test
