Strict
EnableGC

; ServerNet.bb depends on the live network and world graph, so this bounded
; source contract verifies that P_ChangePassword cannot acknowledge a flat-file
; password mutation until SaveAccounts commits it. A failed commit must restore
; the prior in-memory hash and retain the generic failure reply.
Function ChangePasswordSaveContract%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InCase%, Stage%
	Local SawOldPass%, SawAssignment%, SawCommitGate%, SawSuccessRecord%, SawSuccessReply%
	Local SawRestore%, SawFailureRecord%, SawFailureReply%
	Local Line$, Trimmed$
	Local SuccessReply$, FailureReply$
	SuccessReply$ = "RCE_Send(Host, M\FromID, P_ChangePassword, " + Chr$(34) + "Y" + Chr$(34) + ", True)"
	FailureReply$ = "RCE_Send(Host, M\FromID, P_ChangePassword, " + Chr$(34) + "P" + Chr$(34) + ", True)"
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		Trimmed$ = Trim$(Line$)
		If Trimmed$ = "Case P_ChangePassword" Then InCase = True
		If InCase = True And Trimmed$ = "Case P_FetchCharacter" Then InCase = False
		If InCase = True
			If Trimmed$ = "Local OldPass$ = A\Pass$"
				SawOldPass = True
				Stage = 1
			EndIf
			If Stage = 1 And Trimmed$ = "A\Pass$ = HashPassword$(Mid$(M\MessageData$, Offset + 1, PwdLen))"
				SawAssignment = True
				Stage = 2
			EndIf
			If Stage = 2 And Trimmed$ = "If SaveAccounts()"
				SawCommitGate = True
				Stage = 3
			EndIf
			If Stage = 3 And Trimmed$ = "LoginAttemptRecord(M\FromID, True)"
				SawSuccessRecord = True
				Stage = 4
			EndIf
			If Stage = 4 And Trimmed$ = SuccessReply$
				SawSuccessReply = True
				Stage = 5
			EndIf
			If Stage = 5 And Trimmed$ = "Else" Then Stage = 6
			If Stage = 6 And Trimmed$ = "A\Pass$ = OldPass$"
				SawRestore = True
				Stage = 7
			EndIf
			If Stage = 7 And Trimmed$ = "LoginAttemptRecord(M\FromID, False)"
				SawFailureRecord = True
				Stage = 8
			EndIf
			If Stage = 8 And Trimmed$ = FailureReply$
				SawFailureReply = True
				Stage = 9
			EndIf
		EndIf
	Wend

	CloseFile F
	Return SawOldPass And SawAssignment And SawCommitGate And SawSuccessRecord And SawSuccessReply And SawRestore And SawFailureRecord And SawFailureReply

End Function

Test testChangePasswordSaveCommitsBeforeSuccessAndRollsBackOnFailure()
	Assert(ChangePasswordSaveContract%("Modules\ServerNet.bb") = True)
End Test
