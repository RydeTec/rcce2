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

Test testDialogFramesHaveExplicitLengthGuards()
	Assert(FileContains%("Modules\\ClientNet.bb", "If Len(M\\MessageData$) < 9") = True)
	Assert(FileContains%("Modules\\ClientNet.bb", "If Len(M\\MessageData$) < 8") = True)
	Assert(FileContains%("Modules\\ClientNet.bb", "If Len(M\\MessageData$) < 5") = True)
	Assert(FileContains%("Modules\\ClientNet.bb", "If Len(M\\MessageData$) <> 5") = True)
End Test

Test testDialogOptionFramePrevalidatesEveryDeclaredLength()
	Assert(FileContains%("Modules\\ClientNet.bb", "DialogOptionsValid = True") = True)
	Assert(FileContains%("Modules\\ClientNet.bb", "If NameLen > Len(M\\MessageData$) - Offset") = True)
	Assert(FileContains%("Modules\\ClientNet.bb", "If DialogOptionsValid") = True)
End Test

