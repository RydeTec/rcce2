Strict
EnableGC

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
	Return AccountsServerTest_ListItems
End Function

Function AddListBoxItem(parent%, text$)
	AccountsServerTest_ListItems = AccountsServerTest_ListItems + 1
End Function

Function RemoveGadgetItem(parent%, index%)
	If AccountsServerTest_ListItems > 0 Then AccountsServerTest_ListItems = AccountsServerTest_ListItems - 1
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

Global MySQL = False
Global AccountsServerTest_ListItems = 0
Global AccountsServerTest_CommitSucceeds = True

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
	CloseFile(F)
	DeleteFile(TempPath$)
	Return AccountsServerTest_CommitSucceeds
End Function

Function SafeWriteAbort(TempPath$, F)
End Function

Function ReadBoundedString$(F, MaxLen)
	Return ""
End Function

Include "Modules\PasswordHash.bb"
Include "Modules\AccountsServer.bb"

Function ResetAccountsServerTestState()
	Delete Each Account
	Accounts = New AccountsWindow()
	AccountsServerTest_ListItems = 0
	AccountsServerTest_CommitSucceeds = True
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

Test testAddAccountCommitsBeforeReportingSuccess()
	ResetAccountsServerTestState()

	Assert(AddAccount("alice", "0123456789abcdef0123456789abcdef", "alice@example.com") = True)
	Local created.Account = First Account
	Assert(created <> Null)
	Assert(created\User$ = "alice")
	Assert(Accounts\TotalAccounts = 1)
	Assert(AccountsServerTest_ListItems = 1)

	ResetAccountsServerTestState()
End Test

Test testAddAccountRollsBackWhenAtomicCommitFails()
	ResetAccountsServerTestState()
	AccountsServerTest_CommitSucceeds = False

	Assert(AddAccount("alice", "0123456789abcdef0123456789abcdef", "alice@example.com") = False)
	Local remaining.Account = First Account
	Assert(remaining = Null)
	Assert(Accounts\TotalAccounts = 0)
	Assert(AccountsServerTest_ListItems = 0)

	ResetAccountsServerTestState()
End Test
