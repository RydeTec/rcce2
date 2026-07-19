Strict
EnableGC

; The Gubbin Tool owns this event queue and deletes each event after handling
; it.  The bounded source contract keeps the traversal from advancing through
; a freed Event when more than one UI action is queued in the same tick.
Function GubbinEventQueueUsesAfterCursor%()
	Local F.BBStream = ReadFile("Tools\\Gubbin Tool.bb")
	Local InEventPump%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\\Tools\\Gubbin Tool.bb")
	If F = Null Then F = ReadFile("..\\..\\Tools\\Gubbin Tool.bb")
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If InEventPump = False And Instr(Line$, "; Process events") > 0 Then InEventPump = True
		If InEventPump = True
			If Instr(Line$, "For E.Event = Each Event") > 0 Then
				CloseFile F
				Return False
			EndIf
			If Stage < 4 And Instr(Line$, "Delete E") > 0 Then
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "E.Event = First Event") > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, "ENext.Event = Null") > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, "While E <> Null") > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, "ENext = After E") > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, "Delete E") > 0 Then Stage = 5
			If Stage = 5 And Instr(Line$, "E = ENext") > 0 Then Stage = 6
			If Instr(Line$, "If MouseDown(1) And PreviewMesh <> 0") > 0
				CloseFile F
				Return Stage = 6
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Function GubbinMeshNameDialogUsesAfterCursor%()
	Local F.BBStream = ReadFile("Tools\\Gubbin Tool.bb")
	Local InDialog%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\\Tools\\Gubbin Tool.bb")
	If F = Null Then F = ReadFile("..\\..\\Tools\\Gubbin Tool.bb")
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function MeshNameDialog$()") > 0 Then InDialog = True
		If InDialog = True
			If Instr(Line$, "For E.Event = Each Event") > 0 Then
				CloseFile F
				Return False
			EndIf
			If Stage < 4 And Instr(Line$, "Delete E") > 0 Then
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "E.Event = First Event") > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, "ENext.Event = Null") > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, "While E <> Null") > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, "ENext = After E") > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, "Delete E") > 0 Then Stage = 5
			If Stage = 5 And Instr(Line$, "E = ENext") > 0 Then Stage = 6
			If Instr(Line$, "; Render") > 0
				CloseFile F
				Return Stage = 6
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Test testGubbinEventQueueCapturesNextBeforeDelete()
	Assert(GubbinEventQueueUsesAfterCursor%() = True)
End Test

Test testGubbinMeshNameDialogCapturesNextBeforeDelete()
	Assert(GubbinMeshNameDialogUsesAfterCursor%() = True)
End Test
