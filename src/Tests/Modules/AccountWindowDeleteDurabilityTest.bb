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
	Local InDeleteCase%, Stage%, CommitBranch%, CommitIndent%
	Local SawMark%, SawCommit%, SawTotalAccounts%, SawDMCounter%, SawBannedCounter%, SawAccountLabel%, SawDMLabel%, SawBannedLabel%, SawReindexStart%, SawReindexStep%, SawDelete%, SawRemove%, SawFailureRestore%, SawFailureLog%
	Local Line$, Trimmed$
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		Trimmed$ = Trim$(Line$)
		If Trimmed$ = "Case Accounts\DeleteButton" Then InDeleteCase = True
		If InDeleteCase And Trimmed$ = "Case Updates\LockButton" Then Exit
		If InDeleteCase
			If Stage < 2 And (Trimmed$ = "Delete A" Or Instr(Trimmed$, "Accounts\TotalAccounts =") > 0 Or Instr(Trimmed$, "Accounts\TotalDMs =") > 0 Or Instr(Trimmed$, "Accounts\TotalBanned =") > 0 Or Instr(Trimmed$, "SetGadgetText(") > 0 Or Instr(Trimmed$, "Ac2.Account = After A") > 0 Or Instr(Trimmed$, "Ac2\ListID =") > 0 Or Instr(Trimmed$, "RemoveGadgetItem Accounts\List") > 0)
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Trimmed$ = "A\PendingDelete = True"
				SawMark = True
				Stage = 1
			ElseIf Stage = 1 And Trimmed$ = "If SaveAccounts()"
				SawCommit = True
				Stage = 2
				CommitBranch = 1
				CommitIndent = LeadingTabs(Line$)
			ElseIf CommitBranch = 1 And Trimmed$ = "Else" And LeadingTabs(Line$) = CommitIndent
				CommitBranch = 2
			ElseIf CommitBranch = 1
				If Instr(Trimmed$, "Accounts\TotalAccounts = Accounts\TotalAccounts - 1") > 0 Then SawTotalAccounts = True
				If Instr(Trimmed$, "Accounts\TotalDMs = Accounts\TotalDMs - 1") > 0 Then SawDMCounter = True
				If Instr(Trimmed$, "Accounts\TotalBanned = Accounts\TotalBanned - 1") > 0 Then SawBannedCounter = True
				If Instr(Trimmed$, "SetGadgetText(Accounts\AccountsLabel") > 0 Then SawAccountLabel = True
				If Instr(Trimmed$, "SetGadgetText(Accounts\DMLabel") > 0 Then SawDMLabel = True
				If Instr(Trimmed$, "SetGadgetText(Accounts\BannedLabel") > 0 Then SawBannedLabel = True
				If Trimmed$ = "Ac2.Account = After A" Then SawReindexStart = True
				If Trimmed$ = "Ac2\ListID = Ac2\ListID - 1" Then SawReindexStep = True
				If Trimmed$ = "Delete A" Then SawDelete = True
				If Instr(Trimmed$, "RemoveGadgetItem Accounts\List") > 0 Then SawRemove = True
			ElseIf CommitBranch = 2
				If Trimmed$ = "A\PendingDelete = False" Then SawFailureRestore = True
				If Instr(Trimmed$, "Could not delete account") > 0 Then SawFailureLog = True
			EndIf
		EndIf
	Wend

	CloseFile F
	Return SawMark And SawCommit And SawTotalAccounts And SawDMCounter And SawBannedCounter And SawAccountLabel And SawDMLabel And SawBannedLabel And SawReindexStart And SawReindexStep And SawDelete And SawRemove And SawFailureRestore And SawFailureLog

End Function

Function IsAccountRecordWrite%(Trimmed$)

	If Trimmed$ = "WriteString F, A\User$" Then Return True
	If Trimmed$ = "WriteString F, A\Pass$" Then Return True
	If Trimmed$ = "WriteString F, A\Email$" Then Return True
	If Trimmed$ = "WriteByte F, A\IsDM" Then Return True
	If Trimmed$ = "WriteByte F, A\IsBanned" Then Return True
	If Trimmed$ = "WriteString F, A\Ignore$" Then Return True
	If Trimmed$ = "WriteByte F, Chars" Then Return True
	If Trimmed$ = "WriteActorInstance(F, A\Character[i])" Then Return True
	If Trimmed$ = "WriteString F, A\QuestLog[i]\EntryName$[j]" Then Return True
	If Trimmed$ = "WriteString F, A\QuestLog[i]\EntryStatus$[j]" Then Return True
	If Trimmed$ = "WriteString F, A\ActionBar[i]\Slots$[j]" Then Return True
	Return False

End Function

Function PendingDeleteIsExcludedFromFlatFileSave%(Path$)

	Local F.BBStream = OpenContractFile(Path$)
	Local InSave%, Stage%, GuardIndent%, GuardActive%
	Local SawField%, SawGuard%, SawUserWrite%, SawPassWrite%, SawEmailWrite%, SawDMWrite%, SawBannedWrite%, SawIgnoreWrite%, SawCharsWrite%, SawActorWrite%, SawQuestNameWrite%, SawQuestStatusWrite%, SawActionBarWrite%, SawGuardEnd%
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
				GuardActive = True
				Stage = 2
			EndIf
			If Stage = 2
				If IsAccountRecordWrite%(Trimmed$)
					If GuardActive = False Or LeadingTabs(Line$) <= GuardIndent
						CloseFile F
						Return False
					EndIf
				EndIf
				If Trimmed$ = "WriteString F, A\User$" Then SawUserWrite = True
				If Trimmed$ = "WriteString F, A\Pass$" Then SawPassWrite = True
				If Trimmed$ = "WriteString F, A\Email$" Then SawEmailWrite = True
				If Trimmed$ = "WriteByte F, A\IsDM" Then SawDMWrite = True
				If Trimmed$ = "WriteByte F, A\IsBanned" Then SawBannedWrite = True
				If Trimmed$ = "WriteString F, A\Ignore$" Then SawIgnoreWrite = True
				If Trimmed$ = "WriteByte F, Chars" Then SawCharsWrite = True
				If Trimmed$ = "WriteActorInstance(F, A\Character[i])" Then SawActorWrite = True
				If Trimmed$ = "WriteString F, A\QuestLog[i]\EntryName$[j]" Then SawQuestNameWrite = True
				If Trimmed$ = "WriteString F, A\QuestLog[i]\EntryStatus$[j]" Then SawQuestStatusWrite = True
				If Trimmed$ = "WriteString F, A\ActionBar[i]\Slots$[j]" Then SawActionBarWrite = True
				If GuardActive And Trimmed$ = "EndIf" And LeadingTabs(Line$) = GuardIndent
					SawGuardEnd = True
					GuardActive = False
					Stage = 3
				EndIf
			EndIf
			If Trimmed$ = "End Function" Then Exit
		EndIf
	Wend

	CloseFile F
	Return SawField And SawGuard And SawUserWrite And SawPassWrite And SawEmailWrite And SawDMWrite And SawBannedWrite And SawIgnoreWrite And SawCharsWrite And SawActorWrite And SawQuestNameWrite And SawQuestStatusWrite And SawActionBarWrite And SawGuardEnd

End Function

Test testAccountWindowDeletionDoesNotTearDownBeforeSaveCommit()
	Assert(AccountWindowDeleteCommitsBeforeLiveTeardown%("Server.bb") = True)
End Test

Test testPendingAccountDeletionIsExcludedFromFlatFileSave()
	Assert(PendingDeleteIsExcludedFromFlatFileSave%("Modules\AccountsServer.bb") = True)
End Test
