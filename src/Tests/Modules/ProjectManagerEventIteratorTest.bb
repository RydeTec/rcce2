Strict
EnableGC

; Project Manager must preserve the successor before deleting a queued F-UI
; Event. Deleting from a For Each traversal corrupts Blitz3D's iterator.

Function ProjectManagerEventDrainUsesAfterCursor%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Stage%
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then F = ReadFile("..\\..\\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Stage > 0
			If Instr(Line$, "For E.Event = Each Event") > 0
				CloseFile F
				Return False
			EndIf
			If Stage < 5
				If Instr(Line$, "Delete CurrentEvent") > 0
					CloseFile F
					Return False
				EndIf
			EndIf
		EndIf

		If Stage = 0
			If Instr(Line$, ";Start Loop") > 0 Then Stage = 1
		Else If Stage = 1
			If Instr(Line$, "Local CurrentEvent.Event = First Event") > 0 Then Stage = 2
		Else If Stage = 2
			If Instr(Line$, "Local NextEvent.Event = Null") > 0 Then Stage = 3
		Else If Stage = 3
			If Instr(Line$, "While CurrentEvent <> Null") > 0 Then Stage = 4
		Else If Stage = 4
			If Instr(Line$, "NextEvent = After CurrentEvent") > 0 Then Stage = 5
		Else If Stage = 5
			If Instr(Line$, "Delete CurrentEvent") > 0 Then Stage = 6
		Else If Stage = 6
			If Instr(Line$, "CurrentEvent = NextEvent") > 0
				CloseFile F
				Return True
			EndIf
		EndIf

		If Stage > 0 And Instr(Line$, "Until app\\Quit = True") > 0 Then Exit
	Wend

	CloseFile F
	Return False
End Function

Test testProjectManagerCapturesNextEventBeforeDelete()
	Assert(ProjectManagerEventDrainUsesAfterCursor%("Project Manager.bb") = True)
End Test
