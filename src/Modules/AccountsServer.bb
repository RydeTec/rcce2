Type AccountsWindow
	Field Window
	Field List
	Field DeleteButton, DMButton, BanButton
	Field AccountsLabel, DMLabel, BannedLabel
	Field TotalAccounts, TotalDMs, TotalBanned
End Type

Type Account
	Field User$, Pass$, Email$, IsDM, IsBanned
	Field ListID
	Field LoggedOn
	Field Character.ActorInstance[9]
	Field QuestLog.QuestLog[9]
	Field ActionBar.ActionBarData[9]
	Field Ignore$
	Field My_ID
End Type

Type ActionBarData
	Field Slots$[35]
	Field My_ID ; Required for MySQL
End Type
Global Accounts.AccountsWindow

; Alters the logged in status of an account
Function SetLoginStatus(A.Account, Status)

	A\LoggedOn = Status
	If Status > -1
		If A\IsDM = False
			If A\IsBanned = False
				ModifyGadgetItem Accounts\List, A\ListID, "* " + A\User$ + "  (" + A\Email$ + ")"
			Else
				ModifyGadgetItem Accounts\List, A\ListID, "* [BAN] " + A\User$ + "  (" + A\Email$ + ")"
			EndIf
		Else
			If A\IsBanned = False
				ModifyGadgetItem Accounts\List, A\ListID, "* [GM] " + A\User$ + "  (" + A\Email$ + ")"
			Else
				ModifyGadgetItem Accounts\List, A\ListID, "* [BAN][GM] " + A\User$ + "  (" + A\Email$ + ")"
			EndIf
		EndIf
	Else
		If A\IsDM = False
			If A\IsBanned = False
				ModifyGadgetItem Accounts\List, A\ListID, A\User$ + "  (" + A\Email$ + ")"
			Else
				ModifyGadgetItem Accounts\List, A\ListID, "[BAN] " + A\User$ + "  (" + A\Email$ + ")"
			EndIf
		Else
			If A\IsBanned = False
				ModifyGadgetItem Accounts\List, A\ListID, "[GM] " + A\User$ + "  (" + A\Email$ + ")"
			Else
				ModifyGadgetItem Accounts\List, A\ListID, "[BAN][GM] " + A\User$ + "  (" + A\Email$ + ")"
			EndIf
		EndIf
	EndIf

End Function

; Alters the GM status of an account
Function SetAccountDMStatus(A.Account, Flag)

	If Flag <> A\IsDM
		If Flag = False
			Accounts\TotalDMs = Accounts\TotalDMs - 1
		Else
			Accounts\TotalDMs = Accounts\TotalDMs + 1
		EndIf
		SetGadgetText(Accounts\DMLabel, "GM accounts: " + Str(Accounts\TotalDMs))
		A\IsDM = Flag
		SetLoginStatus(A, A\LoggedOn)
	EndIf

End Function

; Alters the ban status of an account
Function SetAccountBanStatus(A.Account, Flag)

	If Flag <> A\IsBanned
		If Flag = False
			Accounts\TotalBanned = Accounts\TotalBanned - 1
		Else
			Accounts\TotalBanned = Accounts\TotalBanned + 1
		EndIf
		SetGadgetText(Accounts\BannedLabel, "Banned accounts: " + Str(Accounts\TotalBanned))
		A\IsBanned = Flag
		SetLoginStatus(A, A\LoggedOn)
	EndIf

End Function

; Creates a new account
Function AddAccount(User$, Pass$, Email$)

	; Create account
	A.Account = New Account
	A\User$ = User$
	A\Pass$ = Pass$
	A\Email$ = Email$
	A\LoggedOn = -1
	AddGadgetItem(Accounts\List, User$ + "  (" + Email$ + ")")
	A\ListID = CountGadgetItems(Accounts\List) - 1
	Accounts\TotalAccounts = Accounts\TotalAccounts + 1
	SetGadgetText(Accounts\AccountsLabel, "Total accounts: " + Str(Accounts\TotalAccounts))

	; Add to accounts file
	F = OpenFile("Data\Server Data\Accounts.dat")
	SeekFile(F, FileSize("Data\Server Data\Accounts.dat"))
		WriteString F, User$
		WriteString F, Pass$
		WriteString F, Email$
		WriteByte F, 0
	CloseFile(F)

End Function

; Saves all game accounts
Function SaveAccounts()

	If MySQL Then Return

	F = WriteFile("Data\Server Data\Accounts.dat")
	If F = 0 Then Return False

		For A.Account = Each Account
			WriteString F, A\User$
			WriteString F, A\Pass$
			WriteString F, A\Email$
			WriteByte F, A\IsDM
			WriteByte F, A\IsBanned
			WriteString F, A\Ignore$
			Chars = 0
			For i = 0 To 9
				If A\Character[i] <> Null Then Chars = Chars + 1
			Next
			WriteByte F, Chars
			For i = 0 To 9
				If A\Character[i] <> Null
					WriteActorInstance(F, A\Character[i])
					For j = 0 To 499
						WriteString F, A\QuestLog[i]\EntryName$[j]
						WriteString F, A\QuestLog[i]\EntryStatus$[j]
					Next
					For j = 0 To 35
						WriteString F, A\ActionBar[i]\Slots$[j]
					Next
				EndIf
			Next
		Next

	CloseFile(F)
	Return True

End Function

; Loads all game accounts and returns the number loaded
Function LoadAccounts()

	If MySQL Then Return 0

	F = ReadFile("Data\Server Data\Accounts.dat")
		; File does not exist
		If F = 0
			; Create it
			F = WriteFile("Data\Server Data\Accounts.dat")
			CloseFile(F)

			; Set labels and exit
			Accounts\TotalAccounts = 0
			SetGadgetText(Accounts\AccountsLabel, "Total accounts: 0")
			SetGadgetText(Accounts\DMLabel, "GM accounts: 0")
			SetGadgetText(Accounts\BannedLabel, "Banned accounts: 0")
			Return 0
		EndIf

		; File does exist, read in all accounts
		While Eof(F) = False
			Accounts\TotalAccounts = Accounts\TotalAccounts + 1
			A.Account = New Account
			A\User$ = ReadString$(F)
			A\Pass$ = ReadString$(F)
			A\Email$ = ReadString$(F)
			A\IsDM = ReadByte(F)
			A\IsBanned = ReadByte(F)
			A\Ignore$ = ReadString$(F)
			A\LoggedOn = -1
			If A\IsDM = False
				If A\IsBanned = False
					AddGadgetItem Accounts\List, A\User$ + "  (" + A\Email$ + ")"
				Else
					AddGadgetItem Accounts\List, "[BAN] " + A\User$ + "  (" + A\Email$ + ")"
					Accounts\TotalBanned = Accounts\TotalBanned + 1
				EndIf
			Else
				If A\IsBanned = False
					AddGadgetItem Accounts\List, "[GM] " + A\User$ + "  (" + A\Email$ + ")"
				Else
					AddGadgetItem Accounts\List, "[BAN][GM] " + A\User$ + "  (" + A\Email$ + ")"
					Accounts\TotalBanned = Accounts\TotalBanned + 1
				EndIf
				Accounts\TotalDMs = Accounts\TotalDMs + 1
			EndIf
			A\ListID = CountGadgetItems(Accounts\List) - 1
			Chars = ReadByte(F)
			For i = 1 To Chars
				A\Character[i - 1] = ReadActorInstance(F)
				A\Character[i - 1]\Account = Handle(A)
				A\QuestLog[i - 1] = New QuestLog
				For j = 0 To 499
					A\QuestLog[i - 1]\EntryName$[j] = ReadString$(F)
					A\QuestLog[i - 1]\EntryStatus$[j] = ReadString$(F)
				Next
				A\ActionBar[i - 1] = New ActionBarData
				For j = 0 To 35
					A\ActionBar[i - 1]\Slots$[j] = ReadString$(F)
				Next
				If A\Character[i - 1] = Null Then Delete A\QuestLog[i - 1] : Delete A\ActionBar[i - 1]
			Next

		Wend

	CloseFile(F)

	; Set labels and exit
	SetGadgetText(Accounts\AccountsLabel, "Total accounts: " + Str(Accounts\TotalAccounts))
	SetGadgetText(Accounts\DMLabel, "GM accounts: " + Str(Accounts\TotalDMs))
	SetGadgetText(Accounts\BannedLabel, "Banned accounts: " + Str(Accounts\TotalBanned))
	Return Accounts\TotalAccounts

End Function

; Creates the Accounts window
Function CreateAccountsWindow.AccountsWindow()

	If MySQL = True Then Return My_CreateAccountsWindow()

	A.AccountsWindow = New AccountsWindow
	A\Window = CreateWindow("Accounts", 10, 10, 500, 450, Desktop(), 1)

	A\List = CreateListBox(5, 10, ClientWidth(A\Window) - 150, ClientHeight(A\Window) - 20, A\Window)

	A\DMButton     = CreateButton("Toggle Account GM Status", ClientWidth(A\Window) - 140, 10, 135, 25, A\Window)
	A\BanButton    = CreateButton("Ban/Unban Account", ClientWidth(A\Window) - 140, 40, 135, 25, A\Window)
	A\DeleteButton = CreateButton("Remove Account", ClientWidth(A\Window) - 140, 70, 135, 25, A\Window)

	A\AccountsLabel = CreateLabel("Total accounts: 999", ClientWidth(A\Window) - 140, ClientHeight(A\Window) - 60, 135, 20, A\Window)
	A\DMLabel       = CreateLabel("GM accounts: 999", ClientWidth(A\Window) - 140, ClientHeight(A\Window) - 40, 135, 20, A\Window)
	A\BannedLabel   = CreateLabel("Banned accounts: 999", ClientWidth(A\Window) - 140, ClientHeight(A\Window) - 20, 135, 20, A\Window)

	Return A

End Function

; Deletes a character from an account
Function DeleteCharacter(A.Account, Number)

	If A\Character[Number] <> Null
		FreeActorInstance(A\Character[Number])
	EndIf

End Function

; Returns a number if a player character is ignoring another, the number being the position in the player's ignore string
Function PlayerIgnoring(A1.ActorInstance, A2.ActorInstance)

	; Is the player ignoring anyone?
	Ac1.Account = Object.Account(A1\Account)
	If Ac1\Ignore$ <> ""
		Ac2.Account = Object.Account(A2\Account)

		; Loop through every ignored account and check
		OldPos = 1
		Pos = Instr(Ac1\Ignore$, ",")
		While Pos > 0
			IgnoreUser$ = Mid$(Ac1\Ignore$, OldPos, Pos - OldPos)
			If IgnoreUser$ = Ac2\User$ Then Return OldPos
			OldPos = Pos + 1
			Pos = Instr(Ac1\Ignore$, ",", Pos + 1)
		Wend
	EndIf

	; Not ignored
	Return 0

End Function