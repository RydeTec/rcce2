Strict
EnableGC

; P_ChangePassword accepts packets from unauthenticated peers. Its failure
; paths deliberately pay VerifyPassword%'s SHA-256 cost to avoid an account
; enumeration timing oracle, so the per-source LoginAttemptOk gate must run
; before the handler reads packet fields or starts account/hash work.
;
; ServerNet's live packet dispatcher pulls in the network/world graph and
; cannot be included in the standalone test harness. This bounded source
; contract pins the required case shape instead: throttle -> generic P reply
; -> Else -> the existing packet parsing and verification path.

Function LeadingTabs%(Line$)
	Local Count%, Pos% = 1
	While Pos <= Len(Line$)
		If Mid$(Line$, Pos, 1) <> Chr$(9) Then Exit
		Count = Count + 1
		Pos = Pos + 1
	Wend
	Return Count

End Function

Function ChangePasswordThrottlesBeforeHashing%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local GuardIndent%, InCase%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Case P_ChangePassword") > 0 Then InCase = True
		If InCase = True And Instr(Line$, "Case P_FetchCharacter") > 0 Then Exit
		If InCase = True
			; No packet read or hash work may precede the limiter.
			If Stage = 0 And (Instr(Line$, "UsernameLen =") > 0 Or Instr(Line$, "VerifyPassword%(") > 0)
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "If Not LoginAttemptOk(M\\FromID)") > 0
				GuardIndent = LeadingTabs(Line$)
				Stage = 1
			EndIf
			If Stage = 1 And Instr(Line$, "RCE_Send(Host, M\\FromID, P_ChangePassword, \"P\", True)") > 0 And LeadingTabs(Line$) = GuardIndent + 1 Then Stage = 2
			If Stage = 2 And Instr(Line$, "Else") > 0 And LeadingTabs(Line$) = GuardIndent Then Stage = 3
			If Stage = 3 And Instr(Line$, "UsernameLen = RCE_IntFromStr(Left$(M\\MessageData$, 1))") > 0 And LeadingTabs(Line$) = GuardIndent Then Stage = 4
			If Stage = 4 And Instr(Line$, "VerifyPassword%(") > 0
				CloseFile F
				Return True
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False

End Function

Function ChangePasswordRecordsThrottleOutcomes%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local FailureRecords%, InCase%, SuccessRecorded%
	Local Line$
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Case P_ChangePassword") > 0 Then InCase = True
		If InCase = True And Instr(Line$, "Case P_FetchCharacter") > 0 Then Exit
		If InCase = True
			If Instr(Line$, "LoginAttemptRecord(M\\FromID, True)") > 0 Then SuccessRecorded = True
			If Instr(Line$, "LoginAttemptRecord(M\\FromID, False)") > 0 Then FailureRecords = FailureRecords + 1
			If Instr(Line$, "RCE_Send(Host, M\\FromID, P_ChangePassword, \"Y\", True)") > 0 And SuccessRecorded = False
				CloseFile F
				Return False
			EndIf
		EndIf
	Wend

	CloseFile F
	Return SuccessRecorded = True And FailureRecords = 2

End Function

Test testChangePasswordThrottlePrecedesPacketReadsAndHashing()
	Assert(ChangePasswordThrottlesBeforeHashing%("Modules\\ServerNet.bb") = True)
End Test

Test testChangePasswordRecordsOutcomesForTheThrottle()
	Assert(ChangePasswordRecordsThrottleOutcomes%("Modules\\ServerNet.bb") = True)
End Test
