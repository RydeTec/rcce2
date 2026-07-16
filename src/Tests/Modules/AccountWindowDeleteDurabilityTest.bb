Strict
EnableGC

; The Accounts window lives in Server.bb and cannot be Included into an
; isolated test build. These bounded source contracts make the flat-file
; deletion transaction explicit: a marked account is omitted from the pending
; atomic write, and the live account/UI state changes only after that write
; commits.

Function LeadingTabs%(Line$)
	Local Count%, Pos% = 1
	While Pos <= Len(Line$)
		If Mid$(Line$, Pos, 1) <> Chr$(9) Then Exit
		Count = Count + 1
		Pos = Pos + 1
	Wend
	Return Count

End Function

Function OpenContractFile.BBStream(Path$)

	Local F.BBStream = ReadFile(Path$)
	If F = Null Then F = ReadFile("..\" + Path$)
	Return F

End Function

Function AccountWindowDeleteCommitsBeforeLiveTeardown%(Path$)

	Local F.BBStream = OpenContractFile(Path$)
	Local InDeleteCase%, Stage%, SuccessStage%, FailureStage%
	Local SawMark%, SawCommit%, SawCounters%, SawReindex%, SawDelete%, SawRemove%, SawFailureRestore%, SawFailureLog%
	Local Line$, Trimmed$
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		Trimmed$ = Trim$(Line$)
		If Trimmed$ = "Case Accounts\DeleteButton" Then InDeleteCase = True
		If InDeleteCase And Trimmed$ = "Case Updates\LockButton" Then Exit
		If InDeleteCase
			If Stage < 2 And (Trimmed$ = "Delete A" Or Instr(Trimmed$, "Accounts\TotalAccounts = Accounts\TotalAccounts - 1") > 0 Or Instr(Trimmed$, "RemoveGadgetItem Accounts\List") > 0)
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Trimmed$ = "A\PendingDelete = True"
				SawMark = True
				Stage = 1
			ElseIf Stage = 1 And Trimmed$ = "If SaveAccounts()"
				SawCommit = True
				Stage = 2
				SuccessStage = 1
			ElseIf Stage = 2 And Trimmed$ = "Else"
				SuccessStage = 0
				FailureStage = 1
			ElseIf SuccessStage = 1
				If Instr(Trimmed$, "Accounts\TotalAccounts = Accounts\TotalAccounts - 1") > 0 Then SawCounters = True
				If Trimmed$ = "Ac2.Account = After A" Then SawReindex = True
				If Trimmed$ = "Delete A" Then SawDelete = True
				If Instr(Trimmed$, "RemoveGadgetItem Accounts\List") > 0 Then SawRemove = True
			ElseIf FailureStage = 1
				If Trimmed$ = "A\PendingDelete = False" Then SawFailureRestore = True
				If Instr(Trimmed$, "Could not delete account") > 0 Then SawFailureLog = True
			EndIf
		EndIf
	Wend

	CloseFile F
	Return SawMark And SawCommit And SawCounters And SawReindex And SawDelete And SawRemove And SawFailureRestore And SawFailureLog

End Function

Function PendingDeleteIsExcludedFromFlatFileSave%(Path$)

	Local F.BBStream = OpenContractFile(Path$)
	Local InSave%, Stage%, GuardIndent%
	Local SawField%, SawGuard%, SawUserWrite%, SawPassWrite%, SawCharsWrite%, SawGuardEnd%
	Local Line$, Trimmed$
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		Trimmed$ = Trim$(Line$)
		If Trimmed$ = "Field PendingDelete" Then SawField = True
		If Trimmed$ = "Function SaveAccounts()" Then InSave = True
		If InSave
			If Stage = 0 And Trimmed$ = "For A.Account = Each Account" Then Stage = 1
			If Stage = 1 And Trimmed$ = "If A\PendingDelete = False"
				SawGuard = True
				GuardIndent = LeadingTabs(Line$)
				Stage = 2
			EndIf
			If Stage = 2
				If Trimmed$ = "WriteString F, A\User$" And LeadingTabs(Line$) = GuardIndent + 1 Then SawUserWrite = True
				If Trimmed$ = "WriteString F, A\Pass$" And LeadingTabs(Line$) = GuardIndent + 1 Then SawPassWrite = True
				If Trimmed$ = "WriteByte F, Chars" And LeadingTabs(Line$) = GuardIndent + 1 Then SawCharsWrite = True
				If Trimmed$ = "EndIf" And LeadingTabs(Line$) = GuardIndent
					SawGuardEnd = True
					Stage = 3
				EndIf
			EndIf
			If Trimmed$ = "End Function" Then Exit
		EndIf
	Wend

	CloseFile F
	Return SawField And SawGuard And SawUserWrite And SawPassWrite And SawCharsWrite And SawGuardEnd

End Function

Test testAccountWindowDeletionDoesNotTearDownBeforeSaveCommit()
	Assert(AccountWindowDeleteCommitsBeforeLiveTeardown%("Server.bb") = True)
End Test

Test testPendingAccountDeletionIsExcludedFromFlatFileSave()
	Assert(PendingDeleteIsExcludedFromFlatFileSave%("Modules\AccountsServer.bb") = True)
End Test
