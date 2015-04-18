;##############################################################################################################################
; Realm Crafter version 1.10																									
; Copyright (C) 2007 Solstar Games, LLC. All rights reserved																	
; contact@solstargames.com																																																		
;																																																																#
; Programmer: Rob Williams																										
; Program: Realm Crafter Actors module
;																																
;This is a licensed product:
;BY USING THIS SOURCECODE, YOU ARE CONFIRMING YOUR ACCEPTANCE OF THE SOFTWARE AND AGREEING TO BECOME BOUND BY THE TERMS OF 
;THIS AGREEMENT. IF YOU DO NOT AGREE TO BE BOUND BY THESE TERMS, THEN DO NOT USE THE SOFTWARE.
;																		
;Licensee may NOT: 
; (i)   create any derivative works of the Engine, including translations Or localizations, other than Games;
; (ii)  redistribute, encumber, sell, rent, lease, sublicense, Or otherwise transfer rights To the Engine; or
; (iii) remove Or alter any trademark, logo, copyright Or other proprietary notices, legends, symbols Or labels in the Engine.
; (iv)   licensee may Not distribute the source code Or documentation To the engine in any manner, unless recipient also has a 
;       license To the Engine.													
; (v)  use the Software to develop any software or other technology having the same primary function as the Software, 
;       including but not limited to using the Software in any development or test procedure that seeks to develop like 
;       software or other technology, or to determine if such software or other technology performs in a similar manner as the
;       Software																																
;##############################################################################################################################

; Blitz Thread class remake
Type BBThread
	Field Hand%		; Handle
	Field Cont%		; Container
	Field Actor%	; Actor Instance
	Field Quest%	; Questlog
	Field Action%	; Actionbar
	Field isslave%	; Slave status
	Field Account%	; Account ID
	Field MsgID%	; Messaged ID
End Type

; Get a counter on how many open threads there are
Global BBThreadCount = 0

; Update all BB Threads
Function My_UpdateThreads()
	
	; Loop through the threads	
	For T.BBThread = Each BBThread
		If BBThreadComplete(T\Hand) Then
			
			; Update all the new info!
			A.ActorInstance = Object.ActorInstance(T\Actor)
			
			A\My_ID					= BBGetInt(T\Cont,0)
			A\Attribute_ID			= BBGetInt(T\Cont,1)
			A\Inventory\My_ID 		= BBGetInt(T\Cont,2)
			A\Faction_ID			= BBGetInt(T\Cont,3)
			A\Inventory\My_AttrID	= BBGetInt(T\Cont,4)
			A\Spell_ID				= BBGetInt(T\Cont,5)
			A\Script_ID				= BBGetInt(T\Cont,6)
			A\Memorised_ID			= BBGetInt(T\Cont,7)
			A\Resistance_ID			= BBGetInt(T\Cont,8)
			
			; If its a PC
			If T\isslave = False Then
			
				; Get their data
				Q.Questlog = Object.QuestLog(T\Quest)
				C.ActionBarData = Object.ActionBarData(T\Action)
				
				Q\My_ID = BBGetInt(T\Cont,8)
				C\My_ID = BBGetInt(T\Cont,9)
				
				; Update the client (slaves are stored when they leave,
				; 	all Human characters will be at this point)
				RCE_Send(Host, T\MsgID, P_CreateCharacter, "Y", True)
				
			End If
			
			; Write to the log
			WriteLog(MainLog,"SQL Thread Ended: "+T\Hand , True, True)
			
			; Free Thread Instance information
			BBFreeThread(T\Hand)
			Delete T
			; Test code to correctly report current threads
			BBThreadCount = BBThreadCount - 1
		End If
	Next
	
End Function
			
			
; Add a new account to the DB
Function My_AddAccount(User$, Pass$, Email$)
	
	; Add New Account (Revision: Specifiy fieldnames - Tracers Suggstion)
	Result = SQLQuery(hSQL,"INSERT INTO `rc_accounts` (`username`,`password`,`email`,`isdm`,`isbanned`) VALUES ('"+User$+"','"+Pass$+"','"+Email$+"','0','0')")
	
	; If the query failed, let people know
	If Not Result Then
		WriteLog(MainLog,"Error adding user account for: " + User$,True,True)
		End
	End If
	
	; Clean up
	FreeSQLQuery(Result)

End Function

; Check if an account exists
Function My_AccountExists(User$)
	
	; Find the account
	Result = SQLQuery(hSQL,"SELECT `account_id` FROM `rc_accounts` WHERE `username` LIKE '"+User$+"'")
	
	; Grab the row, and free the query
	Row = SQLFetchRow(Result)
	FreeSQLQuery(Result)
	
	; Return if it exists or doesn't
	If Row Then
			FreeSQLRow(Row)
			Return 1
	Else
		Return 0
	End If
	
End Function

; Save an account
Function My_SaveAccount(A.Account, SaveInstance)
	
	; Update the account - Removed - when working, this query causes unneccessary overhead and potentially conflicts with data added to the server via other means (DM/Banned flags)
	;result = SQLQuery(hSQL, "UPDATE `rc_accounts` SET `username` = '"+A\User$+"', `password` = '"+A\Pass$+"', `email` = '"+A\Email$+"', `isdm` = '"+A\IsDM+"', `isbanned` = '"+A\IsBanned+"', `ignore` = '"+A\Ignore$+"' WHERE `account_id` = '"+A\My_ID+"'")
	;If Not result Then writelog(mainlog, "Save Account for " + Chr(34) + A\User$ + Chr(34) + " failed!",True,True)
	
	; Sometimes we aren't required to save the entire account
	If SaveInstance = True Then
	
		; Loop through each instance
		For i = 0 To 9
		
			; Check first if the character exists
			If A\Character[i] <> Null Then
			
				; Then Write it
				My_SaveActorInstance(A\Character[i], A\Questlog[i], A\ActionBar[i], False, A\My_ID, False)
			End If
		Next
	End If
	FreeSQLQuery (result)
End Function

; Load an account
Function My_LoadAccount.Account(User$, Pass$, Force)

	; Reason for failure/success
	My_Reason = 0
	
	If hSQL = 0 Then writelog(MainLog, "SQL Stream was terminated! Accounts can not be looked up.")
	; Find the account
	Result = SQLQuery(hSQL, "SELECT * FROM `rc_accounts` WHERE `username` = '"+User$+"'")
	
	; The query itself shouldn't fail, this is just debugging code
	If Not Result Then WriteLog(MainLog,"Error Executing Query (Loading Account "+Chr(34)+User+Chr(34)+")",True,True)
	
	; Check the account exists
	If SQLRowCount(Result) <> 1 And Force = False Then
	
		; If there is more than one row, this shouldn't have happened. Bad management maybe?
		If SQLRowCount(Result)>1 Then WriteLog(MainLog,"Error: More than one account of the same name has been made. How did this happen?",True,True):Shutdown():End
		
		; Return the failure
		My_Reason = MY_NOACCOUNT
		Return Null
	End If
	
	; Get the data
	Row = SQLFetchRow(Result)
	
	; Extract the data
	AccountID = ReadSQLField (Row, "account_id")
	SUser$    = ReadSQLField$(Row, "username")
	SPass$    = ReadSQLField$(Row, "password")
	SEmail$   = ReadSQLField$(Row, "email")
	IsDm	  = ReadSQLField$(Row, "isdm")
	IsBan	  = ReadSQLField$(Row, "isbanned")
	Ign$	  = ReadSQLField$(Row, "ignore")
	
	; Clean up
	FreeSQLRow(Row)
	FreeSQLQuery(Result)
	
	; Check the password and banned status
	If spass$ <> Pass$ And Force = False Then
		MY_Reason = MY_WRONGLOGIN
		Return Null
	ElseIf isban = True And Force = False Then
		MY_Reason = MY_BANNED
		Return Null
	EndIf

	Local A.Account
	For A.Account = Each Account
		If A\user = SUser
			If A\LoggedOn <> -1
				MY_Reason = MY_ACCOUNTLOGGEDIN
				Return Null
			Else
				Exit
			EndIf
		EndIf
	Next

	If A = Null
		; Create a new account, and set its data
		A.Account = New Account
		A\user$		= SUser$
		A\Pass$		= SPass$
		A\Email$	= SEmail$
		A\isdm		= IsDm
		A\isbanned	= IsBanned
		A\My_ID		= AccountID
		A\Ignore$   = Ign$
		A\LoggedOn  = -1
	
		; Update the GUI list
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
	EndIf
	
	; Read in the characters
	Result = SQLQuery(hSQL, "SELECT `id` FROM `rc_actorinstance` WHERE `account_id` = '" + AccountID + "' AND `isslave` = '0'")
	
	i=0
	While(True)
		
		; Fetch the character data and leave if there isn't anything left
		Row = SQLFetchRow(Result)
		If Row = 0 Then Exit
		
		; Get the actor data and clean up
		ActorId = ReadSQLField(Row, "id")
		FreeSQLRow(Row)
		
		; Create a new actionbar and questlog
		A\ActionBar[i] = New ActionBarData
		A\Questlog[i] = New Questlog
		
		; Load the account, then assign its account
		A\Character[i] = My_LoadActorInstance(ActorId, A\QuestLog[i], A\ActionBar[i], AccountID)
		A\Character[i]\Account = Handle(A)
		
		; Increment the counter
		i=i+1
		
	Wend
	
	; Clean up
	FreeSQLQuery(Result)
	
	; Return
	Return A
	
End Function

; Delete a character
Function My_DeleteCharacter(A.Account, Number)
	
	; This is the characters index
	i = Number
	
	; Check the character actually exists
	If A\Character[i] <> Null Then
		
		; Delete the all SQL Data, make sure we clean up as well :)
		FreeSQLQuery(SQLQuery(hSQL, "DELETE FROM `rc_actorinstance` WHERE `id` = '"+A\Character[i]\My_ID+"'"))
		FreeSQLQuery(SQLQuery(hSQL, "DELETE FROM `rc_actionbar` WHERE `actor_id` = '"+A\Character[i]\My_ID+"'"))
		FreeSQLQuery(SQLQuery(hSQL, "DELETE FROM `rc_attributes` WHERE `actor_id` = '"+A\Character[i]\My_ID+"'"))
		FreeSQLQuery(SQLQuery(hSQL, "DELETE FROM `rc_factionratings` WHERE `actor_id` = '"+A\Character[i]\My_ID+"'"))
		FreeSQLQuery(SQLQuery(hSQL, "DELETE FROM `rc_memspells` WHERE `actor_id` = '"+A\Character[i]\My_ID+"'"))
		FreeSQLQuery(SQLQuery(hSQL, "DELETE FROM `rc_questlog` WHERE `actor_id` = '"+A\Character[i]\My_ID+"'"))
		FreeSQLQuery(SQLQuery(hSQL, "DELETE FROM `rc_scripts` WHERE `actor_id` = '"+A\Character[i]\My_ID+"'"))
		FreeSQLQuery(SQLQuery(hSQL, "DELETE FROM `rc_spells` WHERE `actor_id` = '"+A\Character[i]\My_ID+"'"))
        FreeSQLQuery(SQLQuery(hSQL, "DELETE FROM `rc_resistances` WHERE `actor_id` = '"+A\Character[i]\My_ID+"'"))
		
		; Items are a little more complicated
		Result = SQLQuery(hSQL, "SELECT * FROM `rc_items` WHERE `actorid` = '"+A\Character[i]\My_ID+"'")
		
		; Loop through all items
		For j = 0 To Slots_Inventory
		
			; Get their ID
			Row = SQLFetchRow(Result)
			ID = ReadSQLField(Row, "id")
			
			; Delete the item attributes
			FreeSQLQuery(SQLQuery(hSQL, "DELETE FROM `rc_itemvals` WHERE `item_id` = '"+ID+"'"))
			
			; Clean up
			FreeSQLRow(Row)
		Next
		
		; Clean up
		FreeSQLQuery(Result)
		
		; Delete the items
		FreeSQLQuery(SQLQuery(hSQL, "DELETE FROM `rc_items` WHERE `actorid` = '"+A\Character[i]\My_ID+"'"))
		
		; Tell the engine that it can remove its local instance
		FreeActorInstance(A\Character[i])
		
	EndIf
	
End Function

; Create an accounts window, this is an alternative to the other accounts window
; 	as we don't need any of the buttons
Function My_CreateAccountsWindow.AccountsWindow()
	
	A.AccountsWindow = New AccountsWindow
	A\Window = CreateWindow("Accounts", 10, 10, 350, 450, Desktop(), 1)

	A\List = CreateListBox(5, 10, ClientWidth(A\Window) -10, ClientHeight(A\Window) - 20, A\Window)
	
	A\DMButton     = -2
	A\BanButton    = -2
	A\DeleteButton = -2

	A\AccountsLabel = CreateLabel("", 0, 0, 0, 0, A\Window)
	A\DMLabel       = CreateLabel("", 0, 0, 0, 0, A\Window)
	A\BannedLabel   = CreateLabel("", 0, 0, 0, 0, A\Window)
	
	HideGadget A\AccountsLabel
	HideGadget A\DMLabel
	HideGadget A\BannedLabel

	Return A
	
End Function

; Check if an actor exists
Function My_ActorExists(ActorName$)
	; Find the account
	Result = SQLQuery(hSQL,"SELECT * FROM `rc_actorinstance` WHERE `name` LIKE '"+ActorName$+"'")
	
	; Grab the row, and free the query
	Local Count% = SQLRowCount(Result)
	FreeSQLQuery(Result)
	
	; Return if it exists or doesn't
	If Count > 0
		Return 1
	Else
		Return 0
	End If
End Function

; Save a character
Function My_SaveActorInstance(A.ActorInstance, Q.QuestLog, C.ActionbarData, IsSlave, AccountID, Parent)
	
	; Slave may not have been saved before
	If IsSlave Then
		Result = SQLQuery(hSQL, "SELECT `id` FROM `rc_actorinstance` WHERE `id` = '"+A\My_Id+"'")
		If Not Result Or SQLRowCount(Result) = 0 Then
			My_NewActorInstance(A,Null,Null,True,AccountID)
		End If
		FreeSQLQuery(Result)
	End If
	
	; Update their instance, long query :)
	FreeSQLQuery(SQLQuery(hSQL, "UPDATE `rc_actorinstance` SET `actorid` = '"+A\Actor\ID+"', `area` = '"+A\Area$+"',`name` = '"+A\Name$+"',`tag` = '"+A\Tag$+"',`teamid` = '"+A\TeamID%+"',`x` = '"+A\X#+"',`y` = '"+A\Y#+"',`z` = '"+A\Z#+"',`gender` = '"+A\Gender+"',`xp` = '"+A\XP+"',`level` = '"+A\Level+"',`face` = '"+A\FaceTex+"',`hair` = '"+A\Hair+"',`beard` = '"+A\Beard+"',`body` = '"+A\BodyTex+"',`script` = '"+A\Script$+"',`dscript` = '"+A\DeathScript$+"',`rep` = '"+A\Reputation+"',`gold` = '"+A\Gold+"',`slaves` = '"+A\NumberOfSlaves+"',`homefaction` = '"+A\HomeFaction+"', `isslave` = '"+IsSlave+"', `slot` = '"+Parent+"', `xpbarlev` = '"+A\XPBarLevel+"' WHERE `id` = '"+A\My_ID+"'"))
	
	; Update their attributes
	For i = 0 To 39
		FreeSQLQuery(SQLQuery(hSQL, "UPDATE `rc_attributes` SET `aval` = '"+A\Attributes\Value[i]+"', `amax` = '"+A\Attributes\Maximum[i]+"' WHERE `id` = '"+(A\Attribute_ID + i)+"'"))
	Next

	; Update the items
	For i = 0 To Slots_Inventory
		
		; if Items[i] is null, then ID is 65535 (to show nothingness)
		If A\Inventory\Items[i] = Null Then
		
			FreeSQLQuery(SQLQuery(hSQL, "UPDATE `rc_items` SET `iid` = '65535', `iheal` = '0', `iamnt` = '0' WHERE `item_id` = '"+(A\Inventory\My_ID + i)+"'"))

			; Item attributes
			For j = 0 To 39
				FreeSQLQuery(SQLQuery(hSQL, "UPDATE `rc_itemvals` SET `val` = '0' WHERE `id` = '"+(A\Inventory\My_AttrID + (i * 40) + j)+"'"))
			Next
		Else
			FreeSQLQuery(SQLQuery(hSQL, "UPDATE `rc_items` SET `iid` = '"+A\Inventory\Items[i]\Item\ID+"', `iheal` = '"+A\Inventory\Items[i]\ItemHealth+"', `iamnt` = '"+A\Inventory\Amounts[i]+"' WHERE `item_id` = '"+(A\Inventory\My_ID + i)+"'"))
			
			For j = 0 To 39
				FreeSQLQuery(SQLQuery(hSQL, "UPDATE `rc_itemvals` SET `val` = '"+(A\Inventory\Items[i]\Attributes\Value[j])+"' WHERE `id` = '"+(A\Inventory\My_AttrID + (i * 40) + j)+"'"))
			Next

		End If
		
	Next
	
	; Update their faction ratings
	For i = 0 To 99
		FreeSQLQuery(SQLQuery(hSQL, "UPDATE `rc_factionratings` SET `facrat` = '"+A\FactionRatings[i]+"' WHERE `id` = '"+(A\Faction_Id + i)+"'"))
	Next
	
	; Update Resistances
	For i = 0 To 19
		FreeSQLQuery(SQLQuery(hSQL, "UPDATE `rc_resistances` SET `resval` = '"+A\Resistances[i]+"' WHERE `id` = '"+(A\Resistance_Id + i)+"'"))
	Next
	
	; Update Known Spells
	For i = 0 To 999
		FreeSQLQuery(SQLQuery(hSQL, "UPDATE `rc_spells` SET `known` = '"+A\KnownSpells[i]+"', `level` = '"+A\SpellLevels[i]+"' WHERE `id` = '"+(A\Spell_Id + i)+"'"))
	Next
	
	; Update Script Globals and Memorised Spells
	; Using two querys in one loop saves time
	For i = 0 To 9
		FreeSQLQuery(SQLQuery(hSQL, "UPDATE `rc_scripts` SET `glob` = '"+A\ScriptGlobals$[i]+"' WHERE `id` = '"+(A\Script_ID + i)+"'"))
		FreeSQLQuery(SQLQuery(hSQL, "UPDATE `rc_memspells` SET `mem` = '"+A\MemorisedSpells[i]+"' WHERE `id` = '"+(A\Memorised_ID + i)+"'"))
	Next
	
	; Update Questlog
	; Q can be null if the actorinstance is a slave
	If Q <> Null Then
		For i = 0 To 499
			FreeSQLQuery(SQLQuery(hSQL, "UPDATE `rc_questlog` SET `qname` = '"+Q\EntryName$[i]+"', `qstat` = '"+Q\EntryStatus$[i]+"' WHERE `id` = '"+(Q\My_ID + i)+"'"))
		Next
	End If
	
	; Update Actionbar
	; C is null in a slave
	If C <> Null Then
		For i = 0 To 35
	        aaa$ = Left(C\Slots$[i], 1)
			bbb$ = Mid(C\Slots$[i], 2)		
			w% = RCE_IntFromStr(Right(C\Slots$[i],2))
			
			If aaa$ = "I" Then
				FreeSQLQuery(SQLQuery(hSQL, "UPDATE `rc_actionbar` SET `slot` = '" + aaa$ + "', `dat` = '" + w% + "' WHERE `id` = '"+(C\My_ID + i)+"'"))
			Else If aaa$ = "S" Then
				FreeSQLQuery(SQLQuery(hSQL, "UPDATE `rc_actionbar` SET `slot` = '" + aaa$ + "', `dat` = '" + bbb$ + "' WHERE `id` = '"+(C\My_ID + i)+"'"))
			Else If aaa$ = "" Then
				FreeSQLQuery(SQLQuery(hSQL, "UPDATE `rc_actionbar` SET `slot` = '', `dat` = '-1' WHERE `id` = '"+(C\My_ID + i)+"'"))
			End If
		Next
	End If
	
	; Save Slaves
	Slaves = A\NumberOfSlaves
	While Slaves > 0
		For Slave.ActorInstance = Each ActorInstance
			If Slave\Leader = A
				;   Saves Slaves | Instance | Quests | Actionbar | isSlave | AccountNumber | Parent
				My_SaveActorInstance(Slave,    Null,     Null,       True,    AccountID, A\My_ID)
				Slaves = Slaves -1
			End If
		Next
	Wend
	
End Function

; Make a new actor instance
Function My_NewActorInstance(A.ActorInstance, Q.Questlog, c.ActionbarData, IsSlave, AccountID, MsgID = 0)
	
	; This function calls SQLMakeInstance(), this is a threaded
	;   command, and is contained within SQLDLL.dll
	; 
	; The thread cannot callback, so it is checked every so often,
	;   check My_UpdateThreads to see where this function will "end up"
	
	; Setup types related to the actor instance
	If A = Null Then A.ActorInstance = New ActorInstance
	If A\Attributes = Null Then A\Attributes = New Attributes
	If A\Inventory = Null Then A\Inventory = New Inventory
	
	; Make a container (as in the storage kind, not the class kind :))
	byc = BBMakeContainer()
	
	; Set Integers
	BBSetInt(byc,0,AccountId)
	BBSetInt(byc,1,isslave)
	BBSetInt(byc,2,A\Actor\ID)
	BBSetInt(byc,3,A\TeamID%)
	BBSetInt(byc,4,A\Gender)
	BBSetInt(byc,5,A\XP)
	BBSetInt(byc,6,A\Level)
	BBSetInt(byc,7,A\FaceTex)
	BBSetInt(byc,8,A\Hair)
	BBSetInt(byc,9,A\Beard)
	BBSetInt(byc,10,A\BodyTex)
	BBSetInt(byc,11,A\Reputation)
	BBSetInt(byc,12,A\Gold)
	BBSetInt(byc,13,A\NumberOfSlaves)
	BBSetInt(byc,14,A\HomeFaction)
	
	; Set Floats
	BBSetFloat(byc,0,A\X#)
	BBSetFloat(byc,1,A\Y#)
	BBSetFloat(byc,2,A\Z#)
	
	; Set Strings
	BBSetStr(byc,0,A\Area$)
	BBSetStr(byc,1,A\Name$)
	BBSetStr(byc,2,A\Tag$)
	BBSetStr(byc,3,A\Script$)
	BBSetStr(byc,4,A\DeathScript$)
	
	; Set Counter
	f = 15
	
	; Add Attributes
	For i = 0 To 39
		BBSetInt(byc,f + 0,A\Attributes\Value[i])
		BBSetInt(byc,f + 1,A\Attributes\Maximum[i])
		f = f + 2
	Next
	
	; Increment and check thread count
	BBThreadCount = BBThreadCount + 1
	
	; To many threads would kill a program (please don't try it at home)
	If BBThreadCount = 17 Then RuntimeError "Thread Limit Reached - 16 concurrent threads should not be open"
	
	; Make Thread Instance
	T.BBThread = New BBThread
	T\Hand		= SQLMakeInstance(byc)
	T\Cont		= byc
	T\Actor		= Handle(A)
	T\isslave	= IsSlave
	T\Account	= AccountID
	T\MsgID		= MsgID
	
	If isslave = False Then
		T\Quest		= Handle(Q)
		T\Action	= Handle(C)
	End If
	
	
	Result = SQLQuery(hSQL, "SELECT `id` FROM `rc_actorinstance` WHERE `account_id` = '" + AccountID + "' AND `name` = '" + A\Name$ + "'")
		
	; Fetch the character data and leave if there isn't anything left
	Row = SQLFetchRow(Result)
		
	; Get the actor data and clean up
	ActorId = ReadSQLField(Row, "id")
	FreeSQLRow(Row)
	FreeSQLQuery(Result)

	For i = 0 To 19
		query$ = "INSERT INTO rc_resistances(actor_id, resval) VALUES(" + ActorId  +","+ A\resistances[i]+")"
		FreeSQLQuery(SQLQuery(hSQL, query$))
	Next
	
;	For i = 0 To 99
;		query$ = "INSERT INTO rc_factionratings(actor_id, facrat) VALUES(" + ActorId  +","+ A\factionratings[i]+")"
;		FreeSQLQuery(SQLQuery(hSQL, query$))
;	Next
	
	; Write to the log
	WriteLog(MainLog,"SQL Thread Started: New Actor Instance - ID: "+T\Hand , True, True)
	
End Function

; Load a character
Function My_LoadActorInstance.ActorInstance(ActID, Q.Questlog, C.ActionBarData, AccountID)

	; Get Actor Instance
	Result = SQLQuery(hSQL, "SELECT * FROM `rc_actorinstance` WHERE `id` = '" + ActID + "'")
	Row = SQLFetchRow(Result)

	ActorID = ReadSQLField(Row, "actorid")

	; Setup Instance
	If ActorList(ActorID) = Null Then
		A.ActorInstance = New ActorInstance
		A\Attributes = New Attributes
		A\Inventory = New Inventory
	Else
		A.ActorInstance = CreateActorInstance(ActorList(ActorID))
	End If

	If A\Attributes = Null Then A\Attributes = New Attributes
	If A\Inventory = Null Then A\Inventory = New Inventory

	; Read all the data
	A\My_ID				= ActID
	A\Area$				= ReadSQLField(Row, "area")
	A\Name$				= ReadSQLField(Row, "name")
	A\Tag$				= ReadSQLField(Row, "tag")
	A\TeamID			= ReadSQLField(Row, "teamid")
	A\X#				= ReadSQLField(Row, "x")
	A\Y#				= ReadSQLField(Row, "y")
	A\Z#				= ReadSQLField(Row, "z")
	A\Gender			= ReadSQLField(Row, "gender")
	A\XP				= ReadSQLField(Row, "xp")
	A\Level				= ReadSQLField(Row, "level")
	A\FaceTex			= ReadSQLField(Row, "face")
	A\Hair				= ReadSQLField(Row, "hair")
	A\Beard				= ReadSQLField(Row, "beard")
	A\BodyTex			= ReadSQLField(Row, "body")
	A\Script$			= ReadSQLField(Row, "script")
	A\DeathScript$		= ReadSQLField(Row, "dscript")
	A\Reputation		= ReadSQLField(Row, "rep")
	A\Gold				= ReadSQLField(Row, "gold")
	A\NumberOfslaves	= ReadSQLField(Row, "slaves")
	A\HomeFaction		= ReadSQLField(Row, "homefaction")
	A\XPBarLevel		= ReadSQLField(Row, "xbbarlev")
	A\Account_ID		= AccountID

	; Query attributes
	AttributeResult = SQLQuery(hSQL, "SELECT * FROM `rc_attributes` WHERE `actor_id` = '"+ActID+"' ORDER BY `id` ASC")
	
	For i = 0 To 39
		
		; Get the data
		AttributeRow = SQLFetchRow(AttributeResult)
		
		; Get the ID of the 0 row
		If i = 0 Then A\Attribute_ID = ReadSQLField(AttributeRow, "id")
		
		; Set the values
		A\Attributes\Value[i]	= ReadSQLField(AttributeRow, "aval")
		A\Attributes\Maximum[i]	= ReadSQLField(AttributeRow, "amax")
		
		; Clean up
		FreeSQLRow(AttributeRow)
	Next
	
	; Clean up
	FreeSQLQuery(AttributeResult)
		
	; Query Items
	ItemResult = SQLQuery(hSQL, "SELECT * FROM `rc_items` WHERE `actor_id` = '"+ActID+"' ORDER BY `item_id` ASC")
	
	For i = 0 To Slots_Inventory

			; Get Data
			ItemRow = SQLFetchRow(ItemResult)
			
			; Read in the ID, if the ID isn't "null" then it has useful data
			ID = ReadSQLField(ItemRow, "iid")
			
			; Read its row id for writing later
			If i = 0 Then A\Inventory\My_ID = ReadSQLField(ItemRow, "item_id")	
			
			If ID <> 65535 Then
				
				; This code for debugging
				If ItemList(ID) = Null
					WriteLog(MainLog, "Item Removal: Item with ID " + ID + " has been removed from actor as it is no longer existant!")
					A\Inventory\Items[i] = Null
				Else
					; Create an item instance
					A\Inventory\Items[i] = CreateItemInstance(ItemList(ID))
					
					A\Inventory\Amounts[i]			= ReadSQLField(ItemRow, "iamnt")
					A\Inventory\Items[i]\ItemHealth	= ReadSQLField(ItemRow, "iheal")
					
					; Query its attributes
					ItemAttributeResult = SQLQuery(hSQL, "SELECT * FROM `rc_itemvals` WHERE `item_id` = '"+(A\Inventory\My_ID + i)+"' ORDER BY `id` ASC")
					
					For j = 0 To 39
						; Get Data
						ItemAttributeRow = SQLFetchRow(ItemAttributeResult)
						
						; Read in its data, and its ID if its the first records
						If i = 0 And j = 0 Then A\Inventory\My_AttrID = ReadSQLField(ItemAttributeRow, "id" )
						A\Inventory\Items[i]\Attributes\Value[j] = ReadSQLField(ItemAttributeRow, "val")
						
						; Clean up
						FreeSQLRow(ItemAttributeRow)
					Next
				EndIf
				
				; Clean up
				FreeSQLQuery(ItemAttributeResult)
			Else
				
				; Query attributes
				ItemAttributeResult = SQLQuery(hSQL, "SELECT * FROM `rc_itemvals` WHERE `item_id` = '"+(A\Inventory\My_ID + i)+"' ORDER BY `id` ASC")
				For j = 0 To 39
				
					; Get and set data
					ItemAttributeRow = SQLFetchRow(ItemAttributeResult)
					If i = 0 And j = 0 Then A\Inventory\My_AttrID = ReadSQLField(ItemAttributeRow, "id" )
					
					; Clean up
					FreeSQLRow(ItemAttributeRow)
				Next
				
				; Clean up
				FreeSQLQuery(ItemAttributeResult)
			End If

			; Clean up
			FreeSQLRow(ItemRow)
	Next
	
	; Clean up
	FreeSQLQuery(ItemResult)
	
	; Query faction ratings
	FactionResult = SQLQuery(hSQL, "SELECT * FROM `rc_factionratings` WHERE `actor_id` = '"+ActID+"' ORDER BY `id` ASC")

	For i = 0 To 99
	
		; Get Data
		FactionRow = SQLFetchRow(FactionResult)
		
		; Read in data
		If i = 0 Then A\Faction_ID = ReadSQLField(FactionRow, "id")
		A\FactionRatings[i]	= ReadSQLField(FactionRow, "facrat")
		
		; Clean up
		FreeSQLRow(FactionRow)
	Next
	
	; Clean up
	FreeSQLQuery(FactionResult)
	
	; Query resistances
	ResistanceResult = SQLQuery(hSQL, "SELECT * FROM `rc_resistances` WHERE `actor_id` = '"+ActID+"' ORDER BY `id` ASC")
	
	For i = 0 To 19
	
		; Get Data
		ResistanceRow = SQLFetchRow(ResistanceResult)
		
		; Read in data
		If i = 0 Then A\Resistance_ID = ReadSQLField(ResistanceRow, "id")
		A\Resistances[i]	= ReadSQLField(ResistanceRow, "resval")
		
		; Clean up
		FreeSQLRow(ResistanceRow)
	Next
	
	; Clean up
	FreeSQLQuery(ResistanceResult)
		
	; Query spells
	SpellResult = SQLQuery(hSQL, "SELECT * FROM `rc_spells` WHERE `actor_id` = '"+ActID+"' ORDER BY `id` ASC")
	DebugLog "Loading Spelldata"
	For i = 0 To 999
	
		; Get Data
		SpellRow = SQLFetchRow(SpellResult)
		
		; Read in data
		If i = 0 Then A\Spell_ID	= ReadSQLField(SpellRow, "id")
		A\KnownSpells[i]			= ReadSQLField(SpellRow, "known")
		A\SpellLevels[i]			= ReadSQLField(SpellRow, "level")
		
		; Clean up
		FreeSQLRow(SpellRow)
	Next
	
	; Clean up
	FreeSQLQuery(SpellResult)
			
	; Query both script globals and memorised spells
	ScriptResult   = SQLQuery(hSQL, "SELECT * FROM `rc_scripts` WHERE `actor_id` = '"+ActID+"' ORDER BY `id` ASC")
	MemSpellResult = SQLQuery(hSQL, "SELECT * FROM `rc_memspells` WHERE `actor_id` = '"+ActID+"' ORDER BY `id` ASC")
	
	For i = 0 To 9
	
		; Get Data
		ScriptRow   = SQLFetchRow(ScriptResult)
		MemSpellRow = SQLFetchRow(MemSpellResult)
		
		; Read in data
		If i = 0 Then A\Script_ID			= ReadSQLField(ScriptRow, "id")
		nnn$ = ReadSQLField$(ScriptRow, "glob")
		A\ScriptGlobals$[i]		= nnn$
		
		If i = 0 Then A\Memorised_ID		= ReadSQLField(MemSpellRow, "id")
		A\MemorisedSpells[i] = ReadSQLField(MemSpellRow, "mem")
		
		; Clean up
		FreeSQLRow(ScriptRow)
		FreeSQLRow(MemSpellRow)
	Next
	
	; Clean up
	FreeSQLQuery(ScriptResult)
	FreeSQLQuery(MemSpellResult)
		
	
	If Q <> Null Then
		; Query quests if this isn't a slave
		QuestResult = SQLQuery(hSQL, "SELECT * FROM `rc_questlog` WHERE `actor_id` = '"+ActID+"' ORDER BY `id` ASC")
		
		For i = 0 To 499
		
			; Get Data
			QuestRow = SQLFetchRow(QuestResult)
			
			; Read in data
			If i = 0 Then Q\My_Id			= ReadSQLField(QuestRow, "id")
			nnn$ = ReadSQLField$(QuestRow, "qname")
			Q\EntryName$[i]		= nnn$
			nnn$ = ReadSQLField$(QuestRow, "qstat")
			Q\EntryStatus$[i]	= nnn$
			
			; Clean up
			FreeSQLRow(QuestRow)
		Next
		
		; Clean up
		FreeSQLQuery(QuestResult)
	EndIf
		
	If C <> Null Then
		; Query the actionbar if this isn't a slave
		ActionResult = SQLQuery(hSQL, "SELECT * FROM `rc_actionbar` WHERE `actor_id` = '"+ActID+"' ORDER BY `id` ASC")
		
		For i = 0 To 35
		
			; Get Data
			ActionRow = SQLFetchRow(ActionResult)
			
			; Read in data
			If i = 0 Then C\My_ID	= ReadSQLField(ActionRow, "id")
			nnn$ = ReadSQLField(ActionRow, "slot")
			bbb$ = ""
			
			If nnn$ = "I" Then
				aaa% = ReadSQLField(ActionRow, "dat")
			
				If aaa% = -1 Then
					bbb$ = ""
				Else
					bbb$ = RCE_StrFromInt$(aaa%, 2)
				EndIf
			Else If nnn$ = "S" Then
				bbb$ = ReadSQLField(ActionRow, "dat")
			End If
			C\Slots$[i]	= nnn$ + bbb$
			
			; Clean up
			FreeSQLRow(ActionRow)
		Next
		
		; Clean up
		FreeSQLQuery(ActionResult)
	EndIf
		
	; Qery Slaves
	SlaveResult = SQLQuery(hSQL, "SELECT `id` FROM `rc_actorinstance` WHERE `isslave` = '1' AND `slot` = '"+ActID+"'")
	
	While (True)
		
		; Get data and exit if we've run out
		SlaveRow = SQLFetchRow(SlaveResult)
		If Not SlaveRow Then Exit
		
		; Get the slaves ID
		SlavID = ReadSQLField(SlaveRow, "id")
		
		; Load the slave
		Slave.ActorInstance = My_LoadActorInstance(SlavID, Null, Null,AccountID)
		Slave\Leader = A
		Slave\AIMode = AI_Pet
		
		; Clean up
		FreeSQLRow(SlaveRow)
	Wend
	
	; Clean up
	FreeSQLQuery(SlaveResult)
	FreeSQLRow(Row)
	FreeSQLQuery(Result)
	
	; Return
	Return a

End Function

;Function Debug(What$)
;
;	Print What$
;	
;End Function