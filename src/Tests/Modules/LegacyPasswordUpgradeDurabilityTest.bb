Strict
EnableGC

; ServerNet.bb pulls in the live network and world graph, so this bounded
; source contract protects the durable lazy-password migration boundary. Both
; successful authentication handlers must only retain a new v1 hash when the
; atomic Accounts.dat save succeeds; a failed save restores the legacy hash.

Function LegacyPasswordUpgradeHelperIsDurable%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Stage%
	Local Line$, Trimmed$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		Trimmed$ = Trim$(Line$)
		If Trimmed$ = "Function PersistLegacyPasswordUpgrade%(A.Account, ClientMD5$)" Then Stage = 1
		If Stage = 1 And Trimmed$ = "If Not PasswordIsLegacy%(A\Pass$) Then Return True" Then Stage = 2
		If Stage = 2 And Trimmed$ = "Local OldPass$ = A\Pass$" Then Stage = 3
		If Stage = 3 And Trimmed$ = "A\Pass$ = UpgradePasswordIfLegacy$(A\Pass$, ClientMD5$)" Then Stage = 4
		If Stage = 4 And Trimmed$ = "If SaveAccounts() Then Return True" Then Stage = 5
		If Stage = 5 And Trimmed$ = "A\Pass$ = OldPass$" Then Stage = 6
		If Stage = 6 And Trimmed$ = "Return False" Then Stage = 7
	Wend

	CloseFile F
	Return Stage = 7

End Function

Function SuccessfulAuthHandlersPersistLegacyUpgrade%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Handler%, StartGameStage%, VerifyAccountStage%
	Local Line$, Trimmed$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		Trimmed$ = Trim$(Line$)
		If Instr(Trimmed$, "Case P_StartGame") > 0 Then Handler = 1
		If Instr(Trimmed$, "Case P_VerifyAccount") > 0 Then Handler = 2
		If Left$(Trimmed$, 5) = "Case " And Instr(Trimmed$, "Case P_StartGame") = 0 And Instr(Trimmed$, "Case P_VerifyAccount") = 0 Then Handler = 0
		If Handler = 1
			If StartGameStage = 0 And Trimmed$ = "PersistLegacyPasswordUpgrade%(A, IncomingPwd$)" Then StartGameStage = 1
			If StartGameStage = 1 And Trimmed$ = "LoginAttemptRecord(M\FromID, True)" Then StartGameStage = 2
		EndIf
		If Handler = 2
			If VerifyAccountStage = 0 And Trimmed$ = "PersistLegacyPasswordUpgrade%(FoundA, Mid$(M\MessageData$, Offset + 1, PwdLen))" Then VerifyAccountStage = 1
			If VerifyAccountStage = 1 And Trimmed$ = "LoginAttemptRecord(M\FromID, True)" Then VerifyAccountStage = 2
		EndIf
	Wend

	CloseFile F
	Return StartGameStage = 2 And VerifyAccountStage = 2

End Function

Test testLegacyPasswordUpgradeCommitsOrRollsBackBeforeSuccessfulAuth()
	Assert(LegacyPasswordUpgradeHelperIsDurable%("Modules\ServerNet.bb") = True)
	Assert(SuccessfulAuthHandlersPersistLegacyUpgrade%("Modules\ServerNet.bb") = True)
End Test
