Strict
EnableGC

; RC Architect deletes handled F-UI Events in three modal dialogs. Each walk
; must retain the successor before deleting the current Event so a queued
; follow-up input is not skipped.
Function ArchitectModalEventDrainsUseAfterCursor%()
	Local F.BBStream = ReadFile("Modules\\Architect_Gui_Shell_Fui.bb")
	Local Line$
	Local Active%, Stage%, Finished%
	If F = Null Then F = ReadFile("..\\Modules\\Architect_Gui_Shell_Fui.bb")
	If F = Null Then F = ReadFile("..\\..\\Modules\\Architect_Gui_Shell_Fui.bb")
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Left$(Line$, Len("Function FUI_Confirm")) = "Function FUI_Confirm" Then Active = 1 : Stage = 1
		If Left$(Line$, Len("Function ControlsWindow")) = "Function ControlsWindow" Then Active = 2 : Stage = 1
		If Left$(Line$, Len("Function AboutWindow")) = "Function AboutWindow" Then Active = 3 : Stage = 1
		If Active = 0 Then Continue
		If Instr(Line$, "For e.Event = Each Event") > 0 Then
			CloseFile F
			Return False
		EndIf
		If Stage = 1 And Instr(Line$, "Local ArchitectEvent.Event = First Event") > 0 Then Stage = 2
		If Stage = 2 And Instr(Line$, "Local ArchitectNextEvent.Event = Null") > 0 Then Stage = 3
		If Stage = 3 And Instr(Line$, "While ArchitectEvent <> Null") > 0 Then Stage = 4
		If Stage = 4 And Instr(Line$, "ArchitectNextEvent = After ArchitectEvent") > 0 Then Stage = 5
		If Stage < 5 And Instr(Line$, "Delete ArchitectEvent") > 0 Then
			CloseFile F
			Return False
		EndIf
		If Stage = 5 And Instr(Line$, "Delete ArchitectEvent") > 0 Then Stage = 6
		If Stage = 6 And Instr(Line$, "ArchitectEvent = ArchitectNextEvent") > 0 Then
			Finished = Finished + 1
			Active = 0
		EndIf
		If Active > 0 And Instr(Line$, "End Function") > 0 Then
			CloseFile F
			Return False
		EndIf
	Wend

	CloseFile F
	Return Finished = 3
End Function

Test testArchitectModalEventDrainsCaptureNextBeforeDelete()
	Assert(ArchitectModalEventDrainsUseAfterCursor%() = True)
End Test
