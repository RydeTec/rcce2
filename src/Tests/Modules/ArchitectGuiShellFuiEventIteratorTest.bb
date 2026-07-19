Strict
EnableGC

; RC Architect deletes handled F-UI Events in three modal dialogs. Each walk
; must retain the successor before deleting the current Event so a queued
; follow-up input is not skipped.
Function ArchitectModalEventDrainUsesAfterCursor%(FunctionMarker$, LegacyFor$, FirstCursor$, NextDeclaration$, WhileCursor$, Capture$, DeleteEvent$, Advance$)
	Local F.BBStream = ReadFile("Modules\\Architect_Gui_Shell_Fui.bb")
	Local Line$
	Local Stage%
	If F = Null Then F = ReadFile("..\\Modules\\Architect_Gui_Shell_Fui.bb")
	If F = Null Then F = ReadFile("..\\..\\Modules\\Architect_Gui_Shell_Fui.bb")
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

Test testFUIConfirmCapturesNextEventBeforeDelete()
	Assert(ArchitectModalEventDrainUsesAfterCursor%("Function FUI_Confirm", "For e.Event = Each Event", "Local ArchitectEvent.Event = First Event", "Local ArchitectNextEvent.Event = Null", "While ArchitectEvent <> Null", "ArchitectNextEvent = After ArchitectEvent", "Delete ArchitectEvent", "ArchitectEvent = ArchitectNextEvent") = True)
End Test

Test testControlsWindowCapturesNextEventBeforeDelete()
	Assert(ArchitectModalEventDrainUsesAfterCursor%("Function ControlsWindow", "For e.Event = Each Event", "Local ArchitectEvent.Event = First Event", "Local ArchitectNextEvent.Event = Null", "While ArchitectEvent <> Null", "ArchitectNextEvent = After ArchitectEvent", "Delete ArchitectEvent", "ArchitectEvent = ArchitectNextEvent") = True)
End Test

Test testAboutWindowCapturesNextEventBeforeDelete()
	Assert(ArchitectModalEventDrainUsesAfterCursor%("Function AboutWindow", "For e.Event = Each Event", "Local ArchitectEvent.Event = First Event", "Local ArchitectNextEvent.Event = Null", "While ArchitectEvent <> Null", "ArchitectNextEvent = After ArchitectEvent", "Delete ArchitectEvent", "ArchitectEvent = ArchitectNextEvent") = True)
End Test
