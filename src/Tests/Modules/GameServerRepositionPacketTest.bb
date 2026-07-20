Strict
EnableGC

; Source-contract regression for the move-form P_RepositionActor frame that
; SetArea broadcasts when an actor remains in the same area. This stays
; standalone because the sender and decoder belong to the server/client graph.

Function FileSectionContains%(Path$, StartNeedle$, EndNeedle$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local InSection = False
	If F = Null Then F = ReadFile("../" + Path$)
	If F = Null Then F = ReadFile("../../" + Path$)
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then F = ReadFile("..\\..\\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If InSection = False
			If Instr(Line$, StartNeedle$) > 0 Then InSection = True
		Else
			If Instr(Line$, EndNeedle$) > 0 Then Exit
			If Instr(Line$, Needle$) > 0
				CloseFile F
				Return True
			EndIf
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Function FileSectionContainsSequence%(Path$, StartNeedle$, EndNeedle$, FirstNeedle$, SecondNeedle$, ThirdNeedle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local InSection = False
	Local Stage = 0
	If F = Null Then F = ReadFile("../" + Path$)
	If F = Null Then F = ReadFile("../../" + Path$)
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then F = ReadFile("..\\..\\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If InSection = False
			If Instr(Line$, StartNeedle$) > 0 Then InSection = True
		Else
			If Instr(Line$, EndNeedle$) > 0 Then Exit
			If Stage = 0 And Instr(Line$, FirstNeedle$) > 0
				Stage = 1
			ElseIf Stage = 1 And Instr(Line$, SecondNeedle$) > 0
				Stage = 2
			ElseIf Stage = 2 And Instr(Line$, ThirdNeedle$) > 0
				CloseFile F
				Return True
			EndIf
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Test testSameAreaSetAreaUsesMoveFrame()
	Assert(FileSectionContainsSequence%("Modules\\GameServer.bb", "; If he's warped to the same area he was already in, tell players he has changed position", "; Removes an actor instance from a party", "Pa$ = \"M\" + RCE_StrFromInt$(A\\RuntimeID, 2)", "RCE_StrFromFloat$(A\\X#)", "RCE_Send(Host, A2\\RNID, P_RepositionActor, Pa$, True)") = True)
	Assert(FileSectionContains%("Modules\\GameServer.bb", "; If he's warped to the same area he was already in, tell players he has changed position", "; Removes an actor instance from a party", "Pa$ = RCE_StrFromInt$(A\\RuntimeID, 2)") = False)
End Test

Test testClientRepositionMoveDecoderRequiresMoveSubCode()
	Assert(FileSectionContains%("Modules\\ClientNet.bb", "Case P_RepositionActor", "; Floating number", "If Left$(M\\MessageData$, 1) = \"M\"") = True)
	Assert(FileSectionContains%("Modules\\ClientNet.bb", "Case P_RepositionActor", "; Floating number", "RuntimeID = RCE_IntFromStr(Mid$(M\\MessageData$, 2, 2))") = True)
End Test
