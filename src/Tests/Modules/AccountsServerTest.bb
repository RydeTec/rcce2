Strict
EnableGC

Global MySQL = False

Type ActorInstance
	Field Account
	Field RNID
End Type

Type QuestLog
	Field EntryName$[499]
	Field EntryStatus$[499]
End Type

Function WriteActorInstance(F, A.ActorInstance)
End Function

Function ReadActorInstance.ActorInstance(F)
	Return Null
End Function

Function FreeActorInstance(A.ActorInstance)
End Function

; UI stubs so AccountsServer's gadget calls resolve in this unit-test build.
Function ModifyGadgetItem(parent%, index%, text$)
End Function

Function SetGadgetText(parent%, text$)
End Function

Function CountGadgetItems(parent%)
	Return 0
End Function

Function AddListBoxItem(parent%, text$)
End Function

Function RemoveGadgetItem(parent%, index%)
End Function

Function CreateWindow(title$, x%, y%, width%, height%, parent%, style%)
	Return 0
End Function

Function CreateListBox(x%, y%, width%, height%, parent%)
	Return 0
End Function

Function CreateButton(text$, x%, y%, width%, height%, parent%)
	Return 0
End Function

Function CreateLabel(text$, x%, y%, width%, height%, parent%)
	Return 0
End Function

Function ClientWidth(window%)
	Return 0
End Function

Function ClientHeight(window%)
	Return 0
End Function

Function Desktop()
	Return 0
End Function

; Logging stubs so AccountsServer's SafeWrite/WriteLog calls resolve in this
; unit-test build. The real implementations live in Modules\Logging.bb but
; pulling that in here would also pull in its file/UI deps.
Global MainLog = 0

Function WriteLog(LogID%, Message$)
End Function

Function SafeWriteOpen$(FinalPath$)
	Return FinalPath$ + ".tmp"
End Function

Function SafeWriteCommit%(TempPath$, FinalPath$, F)
	Return True
End Function

Function SafeWriteAbort(TempPath$, F)
End Function

Function ReadBoundedString$(F, MaxLen)
	Return ""
End Function

Include "Modules\PasswordHash.bb"
Include "Modules\AccountsServer.bb"

Function LeadingTabs%(Line$)
	Local Count%, Pos% = 1
	While Pos <= Len(Line$)
		If Mid$(Line$, Pos, 1) <> Chr$(9) Then Exit
		Count = Count + 1
		Pos = Pos + 1
	Wend
	Return Count
End Function

; AddAccount is coupled to the account UI and full save graph, so this bounded
; source contract pins the atomic-v1 and rollback shape without faking that
; graph. It rejects the legacy direct append and requires every transient
; state change to be undone after SaveAccounts reports failure.
Function AddAccountUsesAtomicSaveAndRollback%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function AddAccount%(User$, Pass$, Email$)") > 0 Then Stage = 1
		If Stage > 0 And Instr(Line$, "Function SaveAccounts()") > 0 Then Exit
		If Stage > 0 And (Instr(Line$, "OpenFile(") > 0 Or Instr(Line$, "SeekFile(") > 0)
			CloseFile F
			Return False
		EndIf
		Select Stage
			Case 1
				If Instr(Line$, "If SaveAccounts() Then Return True") > 0 Then Stage = 2
			Case 2
				If Instr(Line$, "RemoveGadgetItem(Accounts\List, A\ListID)") > 0 Then Stage = 3
			Case 3
				If Instr(Line$, "Accounts\TotalAccounts = Accounts\TotalAccounts - 1") > 0 Then Stage = 4
			Case 4
				If Instr(Line$, "Delete A") > 0 Then Stage = 5
			Case 5
				If Trim$(Line$) = "Return False"
					CloseFile F
					Return True
				EndIf
		End Select
	Wend

	CloseFile F
	Return False
End Function

Function CreateAccountRepliesAfterAtomicSave%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local FailureReply%, GuardIndent%, InCase%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Case P_CreateAccount") > 0 Then InCase = True
		If InCase = True And Instr(Line$, "Case P_VerifyAccount") > 0 Then Exit
		If InCase = True
			Select Stage
				Case 0
					If Instr(Line$, "ElseIf AddAccount(Username$, Password$, Email$)") > 0
						GuardIndent = LeadingTabs(Line$)
						Stage = 1
					EndIf
				Case 1
					If Instr(Line$, "P_CreateAccount, " + Chr$(34) + "Y" + Chr$(34) + ", True") > 0 And LeadingTabs(Line$) = GuardIndent + 1 Then Stage = 2
				Case 2
					If Trim$(Line$) = "Else" And LeadingTabs(Line$) = GuardIndent Then Stage = 3
				Case 3
					If Instr(Line$, "P_CreateAccount, " + Chr$(34) + "Y" + Chr$(34) + ", True") > 0 And LeadingTabs(Line$) > GuardIndent
						CloseFile F
						Return False
					EndIf
					If Instr(Line$, "P_CreateAccount, " + Chr$(34) + "N" + Chr$(34) + ", True") > 0 And LeadingTabs(Line$) = GuardIndent + 1 Then FailureReply = True
					If Trim$(Line$) = "EndIf" And LeadingTabs(Line$) = GuardIndent
						CloseFile F
						Return FailureReply
					EndIf
			End Select
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Test testFindAccountByListIDReturnsMatchingAccount()
	Local firstAccount.Account = New Account()
	firstAccount\User$ = "first"
	firstAccount\ListID = 0

	Local secondAccount.Account = New Account()
	secondAccount\User$ = "second"
	secondAccount\ListID = 1

	Local thirdAccount.Account = New Account()
	thirdAccount\User$ = "third"
	thirdAccount\ListID = 2

	Local found.Account = FindAccountByListID(1)
	Assert(found = secondAccount)
	Assert(found\User$ = "second")

	Delete Each Account
End Test

Test testFindAccountByListIDReturnsNullForInvalidSelection()
	Local firstAccount.Account = New Account()
	firstAccount\User$ = "first"
	firstAccount\ListID = 0

	Assert(FindAccountByListID(-1) = Null)
	Assert(FindAccountByListID(7) = Null)

	Delete Each Account
End Test

; FormatAccountListEntry$ should produce the exact display strings the
; Accounts list box has historically used for each combination of GM,
; banned, and logged-on status. These tests pin the format so that future
; refactors of SetLoginStatus / LoadAccounts cannot drift the user-visible
; output without being noticed.

Test testFormatAccountListEntryLoggedOutPlainAccount()
	Assert(FormatAccountListEntry$(False, False, -1, "alice", "alice@example.com") = "alice  (alice@example.com)")
End Test

Test testFormatAccountListEntryLoggedOutBanned()
	Assert(FormatAccountListEntry$(False, True, -1, "alice", "alice@example.com") = "[BAN] alice  (alice@example.com)")
End Test

Test testFormatAccountListEntryLoggedOutGM()
	Assert(FormatAccountListEntry$(True, False, -1, "alice", "alice@example.com") = "[GM] alice  (alice@example.com)")
End Test

Test testFormatAccountListEntryLoggedOutBannedGM()
	Assert(FormatAccountListEntry$(True, True, -1, "alice", "alice@example.com") = "[BAN][GM] alice  (alice@example.com)")
End Test

Test testFormatAccountListEntryLoggedInPlainAccount()
	Assert(FormatAccountListEntry$(False, False, 0, "alice", "alice@example.com") = "* alice  (alice@example.com)")
End Test

Test testFormatAccountListEntryLoggedInBanned()
	Assert(FormatAccountListEntry$(False, True, 3, "alice", "alice@example.com") = "* [BAN] alice  (alice@example.com)")
End Test

Test testFormatAccountListEntryLoggedInGM()
	Assert(FormatAccountListEntry$(True, False, 9, "alice", "alice@example.com") = "* [GM] alice  (alice@example.com)")
End Test

Test testFormatAccountListEntryLoggedInBannedGM()
	Assert(FormatAccountListEntry$(True, True, 5, "alice", "alice@example.com") = "* [BAN][GM] alice  (alice@example.com)")
End Test

Test testAddAccountUsesAtomicSaveAndRollsBackOnFailure()
	Assert(AddAccountUsesAtomicSaveAndRollback%("Modules\AccountsServer.bb") = True)
End Test

Test testCreateAccountSendsFailureWhenAtomicSaveFails()
	Assert(CreateAccountRepliesAfterAtomicSave%("Modules\ServerNet.bb") = True)
End Test
