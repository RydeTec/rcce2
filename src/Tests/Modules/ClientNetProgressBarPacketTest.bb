Strict
EnableGC

; ClientNet includes the renderer and network graph, so this source-contract
; regression binds the P_ProgressBar create-frame boundary without pulling the
; live client into the standalone test harness.

Function ProgressBarCreateGuardPrecedesMutation%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InCase%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Case P_ProgressBar") > 0 Then InCase = True
		If InCase = True And Instr(Line$, "Case P_RepositionActor") > 0 Then Exit
		If InCase = True
			If Stage = 0 And Instr(Line$, "If Left$(M\MessageData$, 1) = " + Chr$(34) + "C" + Chr$(34) + " And Len(M\MessageData$) >= 28") > 0 Then Stage = 1
			If Instr(Line$, "RCE_IntFromStr(Mid$(M\MessageData$") > 0 Or Instr(Line$, "RCE_FloatFromStr#(Mid$(M\MessageData$") > 0
				If Stage <> 1
					CloseFile F
					Return False
				EndIf
			EndIf
			If Stage = 1 And Instr(Line$, "GY_CreateProgressBar(0, X#, Y#, W#, H#, Value, Max, Red, Green, Blue)") > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, "0.015 / H#") > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, "RCE_Send(Connection, PeerToHost, P_ProgressBar, " + Chr$(34) + "C" + Chr$(34)) > 0
				CloseFile F
				Return True
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Test testProgressBarCreateFrameNeedsTwentyEightBytesBeforeDecode()
	Assert(ProgressBarCreateGuardPrecedesMutation%("Modules\ClientNet.bb") = True)
End Test
