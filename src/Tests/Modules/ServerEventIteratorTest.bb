Strict
EnableGC

; The Server administrator UI must preserve the next queued F-UI Event before
; deleting the current one. A For Each walk advances through the deleted Event
; and can skip a second operator action delivered in the same frame.

Function OpenServerSource.BBStream()
	Local F.BBStream = ReadFile("Server.bb")
	If F = Null Then F = ReadFile("..\\Server.bb")
	If F = Null Then F = ReadFile("..\\..\\Server.bb")
	If F = Null Then F = ReadFile("src\\Server.bb")
	Return F
End Function

Function ServerEventDrainUsesAfterCursor%()
	Local F.BBStream = OpenServerSource()
	Local Line$
	Local Stage = 0
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Stage > 0 And Instr(Line$, "For E.Event = Each Event") > 0
			CloseFile F
			Return False
		EndIf
		If Stage > 0 And Stage < 6 And Trim$(Line$) = "Delete E"
			CloseFile F
			Return False
		EndIf

		If Stage = 0
			If Instr(Line$, "; Process window events") > 0 Then Stage = 1
		Else If Stage = 1
			If Instr(Line$, "Local E.Event = First Event") > 0 Then Stage = 2
		Else If Stage = 2
			If Instr(Line$, "Local ENext.Event = Null") > 0 Then Stage = 3
		Else If Stage = 3
			If Instr(Line$, "While E <> Null") > 0 Then Stage = 4
		Else If Stage = 4
			If Instr(Line$, "ENext = After E") > 0 Then Stage = 5
		Else If Stage = 5
			If Instr(Line$, "Select E\EventID") > 0 Then Stage = 6
		Else If Stage = 6
			If Trim$(Line$) = "Delete E"
				Stage = 7
			EndIf
		Else If Stage = 7
			If Trim$(Line$) = "E = ENext"
				CloseFile F
				Return True
			EndIf
		EndIf

		If Stage > 0 And Instr(Line$, "; Things to do only if the server is unlocked") > 0 Then Exit
	Wend

	CloseFile F
	Return False
End Function

Test testServerEventDrainCapturesSuccessorBeforeDelete()
	Assert(ServerEventDrainUsesAfterCursor%() = True)
End Test
