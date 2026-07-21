Strict
EnableGC

; P_AttackActor has three fixed server-to-client frames: H and Y carry a
; DamageType byte (6 bytes total), while observer O has two RuntimeIDs only
; (5 bytes). Reject malformed frames before the first decode or combat effect.

Function AttackActorPayloadGuards%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InHandler%, SawH%, SawY%, SawO%, SawFailClosed%, SawDecode%
	Local Frame$, Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Case P_AttackActor") > 0 Then InHandler = True
		If InHandler = True And Instr(Line$, "; Chat bubble message") > 0 Then Exit
		If InHandler = True
			If Instr(Line$, "If AttackSub$ = " + Chr$(34) + "H" + Chr$(34)) > 0 Then Frame$ = "H"
			If Instr(Line$, "If AttackSub$ = " + Chr$(34) + "Y" + Chr$(34)) > 0 Then Frame$ = "Y"
			If Instr(Line$, "If AttackSub$ = " + Chr$(34) + "O" + Chr$(34)) > 0 Then Frame$ = "O"
			If Frame$ = "H"
				If Instr(Line$, "If Len(M\MessageData$) = 6 Then AttackFrameValid = True") > 0 Then SawH = True
			ElseIf Frame$ = "Y"
				If Instr(Line$, "If Len(M\MessageData$) = 6 Then AttackFrameValid = True") > 0 Then SawY = True
			ElseIf Frame$ = "O"
				If Instr(Line$, "If Len(M\MessageData$) = 5 Then AttackFrameValid = True") > 0 Then SawO = True
			EndIf
			If Instr(Line$, "If AttackFrameValid = False") > 0 Then SawFailClosed = True
			If Instr(Line$, "RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 2, 2))") > 0
				If SawFailClosed = False
					CloseFile F
					Return False
				EndIf
				SawDecode = True
			EndIf
		EndIf
	Wend

	CloseFile F
	Return SawH And SawY And SawO And SawFailClosed And SawDecode
End Function

Test testAttackActorRejectsMalformedFramesBeforeDecode()
	Assert(AttackActorPayloadGuards%("Modules\ClientNet.bb") = True)
End Test
