Strict
EnableGC

; P_Dialog is a server-to-client multiplexed frame. The production UI graph is
; too broad for a standalone runtime test, so this contract pins the guards
; before their decode, mutation, and acknowledgement boundaries.

Function FileContains%(Path$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	If F = Null Then F = ReadFile("..\\" + Path$)
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

Function DialogCaseContainsOrdered%(Path$, First$, Second$, Third$, Fourth$)
	Local F.BBStream = ReadFile(Path$)
	Local InDialog%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Case P_Dialog") > 0 Then InDialog = True
		If InDialog = True And Instr(Line$, "Case P_ActorDead") > 0 Then Exit
		If InDialog = True
			If Stage = 0 And Instr(Line$, First$) > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, Second$) > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, Third$) > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, Fourth$) > 0 Then Stage = 4
		EndIf
	Wend
	CloseFile F
	Return Stage = 4
End Function

Test testDialogGuardsContainSensitiveWork()
	Assert(DialogCaseContainsOrdered%("Modules\\ClientNet.bb", "Case " + Chr$(34) + "N" + Chr$(34), "If Len(M\\MessageData$) < 9", "Else", "D = CreateDialog") = True)
	Assert(DialogCaseContainsOrdered%("Modules\\ClientNet.bb", "Case " + Chr$(34) + "T" + Chr$(34), "If Len(M\\MessageData$) < 8", "Else", "DialogOutput") = True)
	Assert(DialogCaseContainsOrdered%("Modules\\ClientNet.bb", "Case " + Chr$(34) + "C" + Chr$(34), "If Len(M\\MessageData$) <> 5", "Else", "FreeDialog") = True)
End Test
Test testDialogOptionFramePrevalidatesEveryDeclaredLength()
	Assert(DialogCaseContainsOrdered%("Modules\\ClientNet.bb", "Case " + Chr$(34) + "O" + Chr$(34), "If Len(M\\MessageData$) < 5", "If DialogOptionsValid", "AddDialogOption") = True)
	Assert(FileContains%("Modules\\ClientNet.bb", "If NameLen > Len(M\\MessageData$) - Offset") = True)
End Test
