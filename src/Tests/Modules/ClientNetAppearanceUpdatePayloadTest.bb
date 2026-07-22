Strict
EnableGC

; P_AppearanceUpdate has fixed server-to-client subframes. Keep a focused
; source contract so malformed frames are rejected before their first decode
; or remote-actor appearance mutation.

Function OpenClientNetSource.BBStream()
	Local F.BBStream = ReadFile("Modules\ClientNet.bb")
	If F = Null Then F = ReadFile("..\Modules\ClientNet.bb")
	If F = Null Then F = ReadFile("..\..\Modules\ClientNet.bb")
	If F = Null Then F = ReadFile("src\Modules\ClientNet.bb")
	Return F
End Function

Function AppearanceFrameHasExpectedLength%(SubCode$, ExpectedLength$)
	Local F.BBStream = OpenClientNetSource()
	Local InAppearance%
	Local Line$
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Case P_AppearanceUpdate") > 0 Then InAppearance = True
		If InAppearance
			If Instr(Line$, "Case " + Chr$(34) + SubCode$ + Chr$(34) + " : AppearanceFrameLength = " + ExpectedLength$) > 0
				CloseFile F
				Return True
			EndIf
			If Instr(Line$, "AI.ActorInstance = RuntimeIDList") > 0 Then Exit
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Function AppearanceUpdateGuardsBeforeRuntimeDecode%()
	Local F.BBStream = OpenClientNetSource()
	Local InAppearance%, Stage%
	Local Line$
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Case P_AppearanceUpdate") > 0 Then InAppearance = True
		If InAppearance
			If Instr(Line$, "AI.ActorInstance = RuntimeIDList") > 0
				If Stage < 2
					CloseFile F
					Return False
				EndIf
				CloseFile F
				Return True
			EndIf
			If Stage = 0 And Instr(Line$, "If AppearanceFrameLength > 0") > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, "If Len(M\MessageData$) <> AppearanceFrameLength") > 0 Then Stage = 2
			If Instr(Line$, "Case P_PartyUpdate") > 0
				CloseFile F
				Return False
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Test testAppearanceFramesDeclareTheirExactLengths()
	Assert(AppearanceFrameHasExpectedLength%("C", "5") = True)
	Assert(AppearanceFrameHasExpectedLength%("G", "4") = True)
	Assert(AppearanceFrameHasExpectedLength%("D", "4") = True)
	Assert(AppearanceFrameHasExpectedLength%("H", "4") = True)
	Assert(AppearanceFrameHasExpectedLength%("F", "4") = True)
	Assert(AppearanceFrameHasExpectedLength%("B", "4") = True)
End Test

Test testAppearanceUpdateGuardsBeforeRuntimeDecode()
	Assert(AppearanceUpdateGuardsBeforeRuntimeDecode%() = True)
End Test
