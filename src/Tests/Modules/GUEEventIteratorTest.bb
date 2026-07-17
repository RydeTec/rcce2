Strict
EnableGC

; GUE consumes F-UI Event objects in two modal loops. The current event must
; save its successor before Delete so a burst of queued events is fully drained.

Function GUEEventDrainUsesAfterCursor%(Path$, FunctionMarker$, LegacyFor$, FirstCursor$, NextDeclaration$, WhileCursor$, Capture$, DeleteEvent$, Advance$)
	Local F.BBStream = ReadFile(Path$)
	Local Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then F = ReadFile("..\\..\\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Stage = 0 And Instr(Line$, FunctionMarker$) > 0 Then Stage = 1
		If Stage > 0 And Instr(Line$, LegacyFor$) > 0 Then
			CloseFile F
			Return False
		EndIf
		If Stage = 1 And Instr(Line$, FirstCursor$) > 0 Then Stage = 2
		If Stage = 2 And Instr(Line$, NextDeclaration$) > 0 Then Stage = 3
		If Stage = 3 And Instr(Line$, WhileCursor$) > 0 Then Stage = 4
		If Stage = 4 And Instr(Line$, Capture$) > 0 Then Stage = 5
		If Stage < 5 And Instr(Line$, DeleteEvent$) > 0 Then
			CloseFile F
			Return False
		EndIf
		If Stage = 5 And Instr(Line$, DeleteEvent$) > 0 Then Stage = 6
		If Stage = 6 And Instr(Line$, Advance$) > 0 Then
			CloseFile F
			Return True
		EndIf
		If Stage > 0 And Instr(Line$, "End Function") > 0 Then Exit
	Wend

	CloseFile F
	Return False
End Function

Test testScaleEntireZoneDialogCapturesNextEventBeforeDelete()
	Assert(GUEEventDrainUsesAfterCursor%("GUE.bb", "Function ScaleEntireZoneDialog()", "For E.Event = Each Event", "Local ScaleEvent.Event = First Event", "Local NextScaleEvent.Event = Null", "While ScaleEvent <> Null", "NextScaleEvent = After ScaleEvent", "Delete(ScaleEvent)", "ScaleEvent = NextScaleEvent") = True)
End Test

Test testGenerateGamePatchCapturesNextEventBeforeDelete()
	Assert(GUEEventDrainUsesAfterCursor%("GUE.bb", "Function GenerateGamePatch()", "For E.Event = Each Event", "Local QuitEvent.Event = First Event", "Local NextQuitEvent.Event = Null", "While QuitEvent <> Null", "NextQuitEvent = After QuitEvent", "Delete(QuitEvent)", "QuitEvent = NextQuitEvent") = True)
End Test
