; Accounts.dat format version. Older files have no magic header
; (the very first ReadString$ in legacy LoadAccounts is User$),
; so we use the absence of the magic to detect them. The magic is
; carefully chosen so the first byte cannot legally be the first byte
; of a Blitz ReadString$ length prefix that would actually parse as a
; sensible string -- it's a four-byte sentinel "ACCT" (Big-endian).
;
; Version history:
;   v0 -- pre-magic (legacy). Identified by the file not starting
;         with ACCOUNTS_MAGIC.
;   v1 -- adds the per-character LastPortal / LastPortalArea /
;         LastPortalTime triad at the END of WriteActorInstance, so
;         the portal-lock anti-exploit (Track TT) survives a
;         logout/login cycle. Older saves load with all three
;         defaulted to 0/-1, which still locks out per-area portals
;         on the first re-entry.
Const ACCOUNTS_MAGIC = $41434354  ; "ACCT"
Const ACCOUNTS_VERSION_CURRENT% = 1
Global ACCOUNTS_LOAD_VERSION% = 0

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

Function FindAccountByListID.Account(ListID)

	If ListID < 0 Then Return Null

	For A.Account = Each Account
		If A\ListID = ListID Then Return A
	Next

	Return Null

End Function

; Per-source login-attempt throttle. Each entry tracks one peer's recent
; failure count and the millisecond timestamp of the first counted failure.
; When failures cross the threshold within the window, further P_VerifyAccount
; attempts from that peer are refused (with a "N" reply so the response
; doesn't betray why) until the window expires. A successful login resets
; the counter for that peer.
Const LoginAttemptMaxFailures = 5
Const LoginAttemptWindowMs    = 60000 ; 60 seconds

Type LoginAttempt
	Field FromID%
	Field Failures%
	Field WindowStart%
End Type

Function LoginAttemptFind.LoginAttempt(FromID)
	For LA.LoginAttempt = Each LoginAttempt
		If LA\FromID = FromID Then Return LA
	Next
	Return Null
End Function

; Returns True if a fresh P_VerifyAccount from FromID should be processed.
; False if the source has tripped the failure threshold within the window.
Function LoginAttemptOk%(FromID)
	LA.LoginAttempt = LoginAttemptFind(FromID)
	If LA = Null Then Return True
	; Window expired — let the next failure record reset it.
	If MilliSecs() - LA\WindowStart >= LoginAttemptWindowMs Then Return True
	If LA\Failures < LoginAttemptMaxFailures Then Return True
	Return False
End Function

; Record the outcome of a login attempt. Success resets the source's count;
; failure increments it (creating a fresh entry / opening a new window if
; the previous one expired).
Function LoginAttemptRecord(FromID, Success)
	LA.LoginAttempt = LoginAttemptFind(FromID)
	If Success
		If LA <> Null Then Delete LA
		Return
	EndIf
	If LA = Null
		LA = New LoginAttempt
		LA\FromID = FromID
		LA\WindowStart = MilliSecs()
		LA\Failures = 1
		Return
	EndIf
	; Reset window if the previous one closed.
	If MilliSecs() - LA\WindowStart >= LoginAttemptWindowMs
		LA\WindowStart = MilliSecs()
		LA\Failures = 1
		Return
	EndIf
	LA\Failures = LA\Failures + 1
End Function

; Returns True iff some live character on this account has its RNID matching
; the requester's connection — i.e. the requester is the currently-logged-in
; session for this account.
;
; Used to gate destructive account ops (ChangePassword, DeleteCharacter) so
; a replayed packet with the (broken-MD5) hash can't take over an account
; or destroy its characters unless the attacker is *also* currently holding
; the session.
Function RequesterOwnsAccountSession%(A.Account, FromID)
	If A = Null Then Return False
	If FromID < 1 Then Return False
	For i = 0 To 9
		If A\Character[i] <> Null
			If A\Character[i]\RNID = FromID Then Return True
		EndIf
	Next
	Return False
End Function

; Builds the display string used in the Accounts list box.
; LoggedOn matches Account\LoggedOn semantics: -1 means logged out, anything
; greater than or equal to 0 indicates an active character index.
Function FormatAccountListEntry$(IsDM, IsBanned, LoggedOn, User$, Email$)

	Local Prefix$ = ""
	If LoggedOn > -1 Then Prefix$ = "* "
	If IsBanned Then Prefix$ = Prefix$ + "[BAN]"
	If IsDM Then Prefix$ = Prefix$ + "[GM]"
	If Len(Prefix$) > 0 And Right$(Prefix$, 1) <> " " Then Prefix$ = Prefix$ + " "

	Return Prefix$ + User$ + "  (" + Email$ + ")"

End Function

; Alters the logged in status of an account
Function SetLoginStatus(A.Account, Status)

	A\LoggedOn = Status
	ModifyGadgetItem Accounts\List, A\ListID, FormatAccountListEntry$(A\IsDM, A\IsBanned, A\LoggedOn, A\User$, A\Email$)

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

; Creates a new account. Pass$ is the client-supplied MD5; we wrap it
; in the v1 salted-SHA-256 storage format so the on-disk record is
; not directly replayable on the wire.
Function AddAccount(User$, Pass$, Email$)

	; Create account
	A.Account = New Account
	A\User$ = User$
	A\Pass$ = HashPassword$(Pass$)
	A\Email$ = Email$
	A\LoggedOn = -1
	AddListBoxItem(Accounts\List, User$ + "  (" + Email$ + ")")
	A\ListID = CountGadgetItems(Accounts\List) - 1
	Accounts\TotalAccounts = Accounts\TotalAccounts + 1
	SetGadgetText(Accounts\AccountsLabel, "Total accounts: " + Str(Accounts\TotalAccounts))

	; Add to accounts file
	F = OpenFile("Data\Server Data\Accounts.dat")
	SeekFile(F, FileSize("Data\Server Data\Accounts.dat"))
		WriteString F, A\User$
		WriteString F, A\Pass$
		WriteString F, A\Email$
		WriteByte F, 0
	CloseFile(F)

End Function

; Saves all game accounts atomically.
;
; Writes to Accounts.dat.tmp, then on success demotes the current
; Accounts.dat to Accounts.dat.bak and promotes the temp into place.
; A crash, power loss, or disk-full during the write leaves Accounts.dat
; unchanged (and Accounts.dat.tmp on disk for manual inspection).
; A crash during the promote step leaves Accounts.dat.bak containing the
; previous good copy. See SafeWriteOpen / SafeWriteCommit in Logging.bb.
;
; Returns True on commit success, False on any failure — callers must
; treat False as "save did not happen" so they don't log "Saved accounts"
; when the data didn't land.
Function SaveAccounts()

	If MySQL Then Return True

	Local FinalPath$ = "Data\Server Data\Accounts.dat"
	Local TempPath$ = SafeWriteOpen(FinalPath$)
	F = WriteFile(TempPath$)
	If F = 0
		WriteLog(MainLog, "SaveAccounts: cannot open " + TempPath$ + " for write")
		Return False
	EndIf

		; v1 header: magic + version. New writes always v1.
		WriteInt F, ACCOUNTS_MAGIC
		WriteByte F, ACCOUNTS_VERSION_CURRENT%

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

	Return SafeWriteCommit(TempPath$, FinalPath$, F)

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

		; Detect format version by peeking the first 4 bytes for the
		; magic header. If absent (pre-v1 file), seek back to 0 and
		; read as legacy.
		Local PeekMagic = ReadInt(F)
		If PeekMagic = ACCOUNTS_MAGIC
			ACCOUNTS_LOAD_VERSION% = ReadByte(F)
			; Reject unknown versions to avoid silently mis-reading a
			; future format. New rcce2 builds must be paired with the
			; saves they wrote.
			If ACCOUNTS_LOAD_VERSION% > ACCOUNTS_VERSION_CURRENT%
				WriteLog(MainLog, "LoadAccounts: file version " + ACCOUNTS_LOAD_VERSION% + " > supported " + ACCOUNTS_VERSION_CURRENT% + ", aborting load")
				CloseFile(F)
				Return 0
			EndIf
		Else
			; Legacy file: no magic, treat as v0 and rewind.
			ACCOUNTS_LOAD_VERSION% = 0
			SeekFile(F, 0)
		EndIf

		; File does exist, read in all accounts. Use ReadBoundedString$
		; on every length-prefixed field so a corrupted / hostile save
		; file with absurd length prefixes can't trigger 2GB allocations
		; or read past EOF into silent zero-padded strings.
		;   Usernames / passwords / emails: 256 bytes is a generous cap
		;   QuestLog entry text: 1024 bytes
		;   ActionBar slot text: 256 bytes
		While Eof(F) = False
			Accounts\TotalAccounts = Accounts\TotalAccounts + 1
			A.Account = New Account
			A\User$ = ReadBoundedString$(F, 256)
			A\Pass$ = ReadBoundedString$(F, 256)
			A\Email$ = ReadBoundedString$(F, 256)
			A\IsDM = ReadByte(F)
			A\IsBanned = ReadByte(F)
			A\Ignore$ = ReadBoundedString$(F, 4096)
			A\LoggedOn = -1
			AddListBoxItem Accounts\List, FormatAccountListEntry$(A\IsDM, A\IsBanned, A\LoggedOn, A\User$, A\Email$)
			If A\IsBanned Then Accounts\TotalBanned = Accounts\TotalBanned + 1
			If A\IsDM Then Accounts\TotalDMs = Accounts\TotalDMs + 1
			A\ListID = CountGadgetItems(Accounts\List) - 1
			Chars = ReadByte(F)
			; Bound the character count -- a corrupted file with 255 in
			; this slot would read 255 character blobs, walking past EOF.
			If Chars > 10 Then Chars = 10
			For i = 1 To Chars
				A\Character[i - 1] = ReadActorInstance(F)
				; Previously this dereferenced A\Character[i - 1]\Account
				; before the Null cleanup below; a corrupted/partial
				; Accounts.dat (server crash mid-save, manual edit) crashed
				; every subsequent server startup. Skip the Account-link and
				; per-character allocations on a Null read; we still need to
				; consume the trailing QuestLog + ActionBar bytes from the
				; stream so following characters parse correctly.
				If A\Character[i - 1] <> Null
					A\Character[i - 1]\Account = Handle(A)
				EndIf
				A\QuestLog[i - 1] = New QuestLog
				For j = 0 To 499
					; Bail the per-character QuestLog block on early EOF so we
					; don't fill the remaining 500 - j slots with zero-padded
					; strings consumed off the end of the file.
					If Eof(F) Then Exit
					A\QuestLog[i - 1]\EntryName$[j] = ReadBoundedString$(F, 1024)
					A\QuestLog[i - 1]\EntryStatus$[j] = ReadBoundedString$(F, 1024)
				Next
				A\ActionBar[i - 1] = New ActionBarData
				For j = 0 To 35
					If Eof(F) Then Exit
					A\ActionBar[i - 1]\Slots$[j] = ReadBoundedString$(F, 256)
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

	//If MySQL = True Then Return My_CreateAccountsWindow()

	A.AccountsWindow = New AccountsWindow
	A\Window = CreateWindow("Accounts", 10, 10, 500, 450, Desktop(), 1)

	A\List = CreateListBox(5, 10, ClientWidth(A\Window) - 150, ClientHeight(A\Window) - 50, A\Window)

	A\DMButton     = CreateButton("Toggle Account GM Status", ClientWidth(A\Window) - 140, 10, 135, 25, A\Window)
	A\BanButton    = CreateButton("Ban/Unban Account", ClientWidth(A\Window) - 140, 40, 135, 25, A\Window)
	A\DeleteButton = CreateButton("Remove Account", ClientWidth(A\Window) - 140, 70, 135, 25, A\Window)

	A\AccountsLabel = CreateLabel("Total accounts: 999", ClientWidth(A\Window) - 140, ClientHeight(A\Window) - 80, 135, 20, A\Window)
	A\DMLabel       = CreateLabel("GM accounts: 999", ClientWidth(A\Window) - 140, ClientHeight(A\Window) - 60, 135, 20, A\Window)
	A\BannedLabel   = CreateLabel("Banned accounts: 999", ClientWidth(A\Window) - 140, ClientHeight(A\Window) - 40, 135, 20, A\Window)

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
	; Object.Account returns Null for stale handles -- bare \Ignore$ or
	; \User$ on a Null crashes the server. Called from /me, /yell,
	; /pmsay, /trade, /partysay, etc., so the deref runs on every chat
	; message from every connected player.
	Ac1.Account = Object.Account(A1\Account)
	If Ac1 = Null Then Return 0
	If Ac1\Ignore$ <> ""
		Ac2.Account = Object.Account(A2\Account)
		If Ac2 = Null Then Return 0

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
