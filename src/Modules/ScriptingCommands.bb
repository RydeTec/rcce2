; Realm Crafter BVM Scripting command module by William "Mr.Bill" Steelhammer
; Most commands ported from Rob William' Scrpting Mosule

Function BVM_ACTOR%()
	SI.ScriptInstance = Object.ScriptInstance(hSI%)
	If SI <> Null
		Result% = SI\AI
	EndIf
Return Result%
End Function

Function BVM_CONTEXTACTOR%()
		SI.ScriptInstance = Object.ScriptInstance(hSI%)
	If SI <> Null
		Result% = SI\AIContext
	EndIf
Return Result%
End Function

Function BVM_PERSISTENT(Param1%)
	SI.ScriptInstance = Object.ScriptInstance(hSI%)
	If SI <> Null
		SI\Persistent = Param1
	EndIf
End Function

Function BVM_FINDACTOR%(Param1$, ActorType% = 3)
	Param1$ = Upper$(Param1$)
	If ActorType < 1 Or ActorType > 3 Then ActorType = 3
	If Len(Param1$) > 0
		For Actor.ActorInstance = Each ActorInstance
			If Upper$(Actor\Name$) = Param1$
				If (ActorType = 1 And Actor\RNID > -1) Or (ActorType = 2 And Actor\RNID = -1) Or ActorType = 3
					Result% = Handle(Actor)
					Exit
				EndIf
			EndIf
		Next
	EndIf
Return Result%
End Function

Function BVM_GETARMOURLEVEL%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Result% = GetArmourLevel(Actor\Inventory)
	EndIf
Return Result%
End Function

Function BVM_THREADEXECUTE(Name$, Func$, AI%=0, AIContext%=0, Param$ = "")
	ThreadScript(Name$, Func$, AI, AIContext, Param$)
End Function

Function BVM_SAVESTATE()
	WriteLog(MainLog, "SaveState running...")
	SaveAccounts()
	WriteLog(MainLog, "Saved accounts...")
	SaveSuperGlobals("Data\Server Data\Superglobals.dat")
	WriteLog(MainLog, "Saved superglobal variables...")
	;For Ar.Area = Each Area : ServerSaveAreaOwnerships(Ar) : Next {##}
	WriteLog(MainLog, "Saved zone ownerships...")
	SaveEnvironment()
	WriteLog(MainLog, "Saved environment settings...")
	SaveDroppedItems("Data\Server Data\Dropped Items.dat")
	WriteLog(MainLog, "Saved dropped items...")
	WriteLog(MainLog, "SaveState complete")
End Function

Function BVM_PLAYERACCOUNTNAME$(Param%)
	Actor.ActorInstance = Object.ActorInstance(Param)
	If Actor <> Null
		A.Account = Object.Account(Actor\Account)
		If A <> Null Then Result$ = A\User$
	EndIf
Return Result$
End Function

Function BVM_PLAYERACCOUNTEMAIL$(Param%)
	Actor.ActorInstance = Object.ActorInstance(Param)
	If Actor <> Null
		A.Account = Object.Account(Actor\Account)
		If A <> Null Then Result$ = A\Email$
	EndIf
Return Result$
End Function

Function BVM_PLAYERISGM%(Param%)
	Actor.ActorInstance = Object.ActorInstance(Param)
	If Actor <> Null
		A.Account = Object.Account(Actor\Account)
		If A <> Null Then Result% = A\IsDM
	EndIf
Return Result%
End Function

Function BVM_PLAYERISDM%(Param%)
	Actor.ActorInstance = Object.ActorInstance(Param)
	If Actor <> Null
		A.Account = Object.Account(Actor\Account)
		If A <> Null Then Result% = A\IsDM
	EndIf
Return Result%
End Function

Function BVM_PLAYERISBANNED%(Param%)
	Actor.ActorInstance = Object.ActorInstance(Param)
	If Actor <> Null
		A.Account = Object.Account(Actor\Account)
		If A <> Null Then Result% = A\IsBanned
	EndIf
Return Result%
End Function

Function BVM_BANPLAYER(Param%)
	Actor.ActorInstance = Object.ActorInstance(Param)
	If Actor <> Null
		A.Account = Object.Account(Actor\Account)
		If A <> Null Then A\IsBanned = 1
	EndIf
End Function

Function BVM_KICKPLAYER(Param%)
	Actor.ActorInstance = Object.ActorInstance(Param%)
	If Actor <> Null
			DataAux$ = RCE_StrFromInt(Actor\RNID)
			RCE_FSend(0, RCE_PlayerKicked, DataAux$, True, Len(DataAux$))
			RCE_FSend(Actor\RNID, P_KickedPlayer, "", True, 0)
	EndIf
End Function

Function BVM_ACTORX#(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result# = Actor\X#
	Return Result#
End Function

Function BVM_ACTORY#(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result# = Actor\Y#
	Return Result#
End Function

Function BVM_ACTORZ#(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result# = Actor\Z#
	Return Result#
End Function

Function BVM_ACTORAGGRESSIVENESS%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Result% = Actor\Actor\Aggressiveness
	EndIf
Return Result%
End Function

Function BVM_ACTORINTRIGGER%(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		TriggerID = Param2%
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		If Len(AInstance\Area\TriggerScript$[TriggerID]) > 0
			Size# = AInstance\Area\TriggerSize#[TriggerID] * AInstance\Area\TriggerSize#[TriggerID]
			DistX# = Abs(Actor\X# - AInstance\Area\TriggerX#[TriggerID])
			DistY# = Abs(Actor\Y# - AInstance\Area\TriggerY#[TriggerID])
			DistZ# = Abs(Actor\Z# - AInstance\Area\TriggerZ#[TriggerID])
			Dist# = (DistX# * DistX#) + (DistY# * DistY#) + (DistZ# * DistZ#)
			If Dist# < Size# Then Result% = 1
		EndIf
	EndIf
Return Result%
End Function

Function BVM_ACTORSINZONE%(Param1$, Instance%=0)
	ZoneName$ = Upper$(Param1$)
	For Ar.Area = Each Area
		If Upper$(Ar\Name$) = ZoneName$
			Count = 0
			; In all instances
			If Instance = -1
				For Instance = 0 To 99
					AInstance.AreaInstance = Ar\Instances[Instance]
					If AInstance <> Null
						A2.ActorInstance = AInstance\FirstInZone
						While A2 <> Null
							Count = Count + 1
							A2 = A2\NextInZone
						Wend
					EndIf
				Next
				; In a specific instance
			Else
				AInstance.AreaInstance = Ar\Instances[Instance]
				If AInstance <> Null
					A2.ActorInstance = AInstance\FirstInZone
					While A2 <> Null
						Count = Count + 1
						A2 = A2\NextInZone
					Wend
				EndIf
			EndIf
			Result% = Count
			Exit
		EndIf
	Next
Return Result%
End Function

Function BVM_PLAYERSINZONE%(Param1$, Instance%=0)
	ZoneName$ = Upper$(Param1$)
	For Ar.Area = Each Area
		If Upper$(Ar\Name$) = ZoneName$
			Count = 0
			; In all instances
			If Instance = -1
				For Instance = 0 To 99
					AInstance.AreaInstance = Ar\Instances[Instance]
					If AInstance <> Null
						A2.ActorInstance = AInstance\FirstInZone
						While A2 <> Null
							If A2\RNID > 0 Then Count = Count + 1
							A2 = A2\NextInZone
						Wend
					EndIf
				Next
			; In a specific instance
			Else
				AInstance.AreaInstance = Ar\Instances[Instance]
				If AInstance <> Null
					A2.ActorInstance = AInstance\FirstInZone
					While A2 <> Null
						If A2\RNID > 0 Then Count = Count + 1
							A2 = A2\NextInZone
					Wend
				EndIf
			EndIf
			Result% = Count
			Exit
		EndIf
	Next
Return Result%
End Function

Function BVM_ZONEINSTANCEEXISTS%(Param1$, Param2%)
	Zone.Area = FindArea(Param1$)
	If Zone <> Null
		Instance = Param2%
		If Zone\Instances[Instance] <> Null Then Result% = 1
	EndIf
Return Result%
End Function

Function BVM_CREATEZONEINSTANCE%(Param1$, Instance%=0)
	Zone.Area = FindArea(Param1$)
	If Zone <> Null
		; Script requests a specific ID
		If Instance > 0
			If Zone\Instances[Instance] = Null
				ServerCreateAreaInstance(Zone, Instance)
				Result% = Instance
			EndIf
		; Use first free ID
		Else
			For i = 1 To 99
				If Zone\Instances[i] = Null
					ServerCreateAreaInstance(Zone, i)
					Result% = i
					Exit
				EndIf
			Next
		EndIf
	Else
		WriteLog(MainLog, "Instance can not be created, Zone " + Param1$ + " does not exist.")
	EndIf
Return Result%
End Function

Function BVM_REMOVEZONEINSTANCE(Param1$, Instance%)
	Zone.Area = FindArea(Param1$)
	If Zone <> Null
		If Instance > 0
			If Zone\Instances[Instance] <> Null
				; Move players to instance #0, and delete AI actor instances
				Actor.ActorInstance = Zone\Instances[Instance]\FirstInZone
				While Actor <> Null
					A2.ActorInstance = Actor\NextInZone
					If Actor\RNID > 0
						SetArea(Actor, Zone, 0, -1, -1, Actor\X#, Actor\Y#, Actor\Z#)
					Else
						FreeActorScripts(Actor)
						FreeActorInstance(Actor)
					EndIf
					Actor = A2
				Wend
				; Delete ownerships for instance from disk
				DeleteFile("Data\Server Data\Areas\Ownerships\" + Zone\Name$ + " (" + Zone\Instances[Instance]\ID + ") Ownerships.dat")
			; Delete dropped items
				For D.DroppedItem = Each DroppedItem
					AInstance.AreaInstance = Object.AreaInstance(D\ServerHandle)
					If AInstance = Zone\Instances[Instance]
						FreeItemInstance(D\Item)
						Delete(D)
					EndIf
				Next
				; Free Owned Scenery for the instance {##}
				;For i = 0 To 499
				;	If Zone\Instances[Instance]\OwnedScenery[i] <> Null
				;		If Zone\Instances[Instance]\OwnedScenery[i]\Inventory <> Null
				;			Delete Zone\Instances[Instance]\OwnedScenery[i]\Inventory
				;		EndIf
				;		Delete Zone\Instances[Instance]\OwnedScenery[i]
				;	EndIf
				;Next
				Delete Zone\Instances[Instance]
			EndIf
		EndIf
	Else
		WriteLog(MainLog, "Instance can not be created, Zone " + Param1$ + " does not exist.")
	EndIf
End Function

Function BVM_COUNTPARTYMEMBERS%(Param%)
	Actor.ActorInstance = Object.ActorInstance(Param%)
	If Actor <> Null
		Party.Party = Object.Party(Actor\PartyID)
		If Party <> Null Then Result% = Party\Members - 1
	EndIf
Return Result%
End Function

Function BVM_PARTYMEMBER%(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Party.Party = Object.Party(Actor\PartyID)
		If Party <> Null
			Member = Param2%
			If Member <= Party\Members - 1
				Count = 0
				For i = 0 To 7
					If Party\Player[i] <> Null And Party\Player[i] <> Actor
						Count = Count + 1
						If Count = Member
							Result = Handle(Party\Player[i])
							Exit
						EndIf
					EndIf
				Next
			EndIf
		EndIf
	EndIf
Return Result%
End Function

Function BVM_KILLACTOR(Param1%, Param2%=0)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Actor\Attributes\Value[HealthStat] = 0
		Killer.ActorInstance = Object.ActorInstance(Param2%)
		KillActor(Actor, Killer)
	EndIf
End Function

Function BVM_CHANGEACTOR(Param1%, Param2%)
	Local Success% = False
	ID% = Param2
	
	;Test for valid ActorID
	For aid.Actor = Each Actor
		If aid\ID = ID Then Success = True : Exit
	Next

	If Success = True 
		Actor.ActorInstance = Object.ActorInstance(Param1%)
		If Actor <> Null
			If ActorList(ID) <> Null
				Actor\Actor = ActorList(ID)
				If Actor\Actor\Genders = 2 And Actor\Gender <> 1 Then Actor\Gender = 1
				If (Actor\Actor\Genders = 1 Or Actor\Actor\Genders = 3) And Actor\Gender <> 0 Then Actor\Gender = 0
				; Tell other players in the area
				Pa$ = "C" + RCE_StrFromInt$(Actor\RuntimeID, 2) + RCE_StrFromInt$(ID, 2)
				AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
				A2.ActorInstance = AInstance\FirstInZone
				While A2 <> Null
					If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_AppearanceUpdate, Pa$, True)
					A2 = A2\NextInZone
				Wend
			EndIf
		EndIf
	Else
		WriteLog(MainLog, "Error: Invalid ActorID supplied in ChangeActor() command.")
	EndIf

End Function

Function BVM_SPAWNITEM(Param1$, Param2%, Param3$, Param4#, Param5#, Param6#, Param7%=0)
	ItemTemplate.Item = FindItem(Param1$)
	If ItemTemplate <> Null
		Zone.Area = FindArea(Param3$)
		If Zone <> Null
			D.DroppedItem = New DroppedItem
			D\Item = CreateItemInstance(ItemTemplate)
			D\Amount = Param2%
			D\X# = Param4#
			D\Y# = Param5#
			D\Z# = Param6#
			Instance = Param7%
			If Zone\Instances[Instance] = Null
				Instance = 0
				WriteLog(MainLog, "Error: Cannot spawn item in instance #" + Str$(Instance) + " of " + Zone\Name$ + " as the instance does not exist")
			EndIf
			D\ServerHandle = Handle(Zone\Instances[Instance])
			; Tell other players in the area
			Pa$ = RCE_StrFromInt$(D\Amount, 2) + RCE_StrFromFloat$(D\X#) + RCE_StrFromFloat$(D\Y#) + RCE_StrFromFloat$(D\Z#)
			Pa$ = Pa$ + RCE_StrFromInt$(Handle(D), 4) + ItemInstanceToString$(D\Item)
			A2.ActorInstance = Zone\Instances[Instance]\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_InventoryUpdate, "D" + Pa$, True)
				A2 = A2\NextInZone
			Wend
		EndIf
	EndIf
End Function

Function BVM_SETACTORGENDER(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Actor\Gender = (Param2% - 1)
		If Actor\Gender = 2 Then Actor\Gender = 0
		If Actor\Actor\Genders = 2 And Actor\Gender <> 1 Then Actor\Gender = 1
		If (Actor\Actor\Genders = 1 Or Actor\Actor\Genders = 3) And Actor\Gender <> 0 Then Actor\Gender = 0
		Pa$ = "G" + RCE_StrFromInt$(Actor\RuntimeID, 2) + Chr$(Actor\Gender)
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		A2.ActorInstance = AInstance\FirstInZone
		While A2 <> Null
			If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_AppearanceUpdate, Pa$, True)
				A2 = A2\NextInZone
		Wend
	EndIf
End Function

Function BVM_ACTORBEARD%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param%)
	If Actor <> Null Then Result% = Actor\Beard + 1
Return Result%
End Function

Function BVM_SETACTORBEARD(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\Gender = 0
			Actor\Beard = Param2% - 1
			Pa$ = "D" + RCE_StrFromInt$(Actor\RuntimeID, 2) + Chr$(Actor\Beard)
			AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
			A2.ActorInstance = AInstance\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_AppearanceUpdate, Pa$, True)
				A2 = A2\NextInZone
			Wend
		EndIf
	EndIf
End Function

Function BVM_ACTORHAIR%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param%)
	If Actor <> Null Then Result% = Actor\Hair + 1
Return Result%
End Function

Function BVM_SETACTORHAIR(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\Gender = 0
			Actor\Hair = Param2% - 1
			Pa$ = "D" + RCE_StrFromInt$(Actor\RuntimeID, 2) + Chr$(Actor\Hair)
			AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
			A2.ActorInstance = AInstance\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_AppearanceUpdate, Pa$, True)
				A2 = A2\NextInZone
			Wend
		EndIf
	EndIf
End Function

Function BVM_ACTORCALLFORHELP(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then AICallForHelp(Actor)
End Function

Function BVM_SETACTORAISTATE(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Actor\AIMode = Param2%
	EndIf
End Function

Function BVM_ACTORAISTATE%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Result% = Actor\AIMode
	EndIf
Return Result%
End Function

Function BVM_ACTORTARGET%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\AITarget <> Null
			Result% = Handle(Actor\AITarget)
		EndIf
	EndIf
Return Result%
End Function

Function BVM_SETACTORTARGET(Param1%, Param2%=0)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	Actor2.ActorInstance = Object.ActorInstance(Param2%)
	If Actor <> Null
		If Actor2 <> Null
			If Actor\Actor\Aggressiveness <> 3 And Actor2\Actor\Aggressiveness <> 3
				If Actor2\FactionRatings[Actor\HomeFaction] < 150 Then Actor\AITarget = Actor2
			EndIf
		Else
			Actor\AITarget = Null
		EndIf
	EndIf
End Function

Function BVM_SETACTORDESTINATION(Param1%, Param2#, Param3#)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Actor\DestX# = Param2#
		Actor\DestZ# = Param3#
	EndIf
End Function

Function BVM_GIVEKILLXP(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	Actor2.ActorInstance = Object.ActorInstance(Param2%)
	If Actor <> Null And Actor2 <> Null
		Diff = Actor2\Level - Actor\Level
		If Diff < 1 Then Diff = 1
		XP = (Diff * Actor2\Actor\XPMultiplier) + Rand(0, 20)
		GiveXP(Actor, XP)
	EndIf
End Function

Function BVM_SPAWN%(Param1%, Param2$, Param3#, Param4#, Param5#, Param6$ = "", Param7$ = "", Param8%=0)
	; Find actor
	ID = Param1%
	If ID > -1
		If ActorList(ID) <> Null
			; Find zone
			Name$ = Upper$(Param2$)
			For Ar.Area = Each Area
				If Upper$(Ar\Name$) = Name$
					AI.ActorInstance = CreateActorInstance.ActorInstance(ActorList(ID))
					AI\RNID = -1
					AssignRuntimeID(AI)
					Instance = Param8%
					SetArea(AI, Ar, Instance, -1, -1, Param3#, Param4#, Param5#)
					AI\AIMode = AI_Wait
					AI\Script$ = Param6$
					AI\DeathScript$ = Param7$
					WriteLog(MainLog, "Spawned AI actor from script: " + AI\Actor\Race$ + " in zone: " + Ar\Name$)
					Result% = Handle(AI)
					Exit
				EndIf
			Next
		EndIf
	EndIf
Return Result%
End Function

Function BVM_PARAMETER$(Param1%)
S.ScriptInstance = Object.ScriptInstance(hSI)
	If S\Param$ <> ""
		Result$ = SafeSplit(S\Param$, Param1%, ",")
	Else
		Result$ = ""
	EndIf
Return Result$
End Function

Function BVM_ROTATEACTOR(Param1%, Param2#)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Actor\Yaw# = Param2#
		Pa$ = "R" + RCE_StrFromInt$(Actor\RuntimeID, 2) + RCE_StrFromFloat$(Actor\Yaw#)
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		A2.ActorInstance = AInstance\FirstInZone
		While A2 <> Null
			If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_RepositionActor, Pa$, True)
			A2 = A2\NextInZone
		Wend
	EndIf
End Function

Function BVM_MOVEACTOR(Param1%, Param2#, Param3#, Param4#, Param5%=0, Param6%=0)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Actor\X# = Param2#
		Actor\Y# = Param3#
		Actor\Z# = Param4#
		Actor\DestX# = Actor\X#
		Actor\DestZ# = Actor\Z#
		Pa$ = "M" + RCE_StrFromInt$(Actor\RuntimeID, 2) + RCE_StrFromFloat$(Actor\X#) + RCE_StrFromFloat$(Actor\Y#) + RCE_StrFromFloat$(Actor\Z#)
		Pa$ = Pa$ + RCE_StrFromInt$(Param5%, 1) + RCE_StrFromInt$(Param6%, 1)
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		A2.ActorInstance = AInstance\FirstInZone
		While A2 <> Null
			If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_RepositionActor, Pa$, True)
			A2 = A2\NextInZone
		Wend
	EndIf
End Function

Function BVM_CREATEFLOATINGNUMBER(Param1%, Param2%, Param3%=255, Param4%=255, Param5%=255)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Amount = Param2%
		R = Param3%
		G = Param4%
		B = Param5%
		Pa$ = RCE_StrFromInt$(Actor\RuntimeID, 2) + RCE_StrFromInt$(Amount, 4)
		Pa$ = Pa$ + RCE_StrFromInt$(R, 1) + RCE_StrFromInt$(G, 1) + RCE_StrFromInt$(B, 1)
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		A2.ActorInstance = AInstance\FirstInZone
		While A2 <> Null
			If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_FloatingNumber, Pa$, True)
			A2 = A2\NextInZone
		Wend
	EndIf
End Function

Function BVM_ACTORRIDER%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1)
	If Actor <> Null
		If Actor\Rider <> Null Then Result = Handle(Actor\Rider)
	EndIf
Return Result%
End Function

Function BVM_ACTORMOUNT%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\Mount <> Null Then Result% = Handle(Actor\Mount)
	EndIf
Return Result%
End Function

Function BVM_ITEMID%(Param1%)
	Item.ItemInstance = Object.ItemInstance(Param1%)
	If Item <> Null Then Result% = Item\Item\ID
Return Result%
End Function

Function BVM_ITEMVALUE%(Param1%)
	Item.ItemInstance = Object.ItemInstance(Param1%)
	If Item <> Null Then Result% = Item\Item\Value
Return Result%
End Function

Function BVM_ITEMMASS%(Param1%)
	Item.ItemInstance = Object.ItemInstance(Param1%)
	If Item <> Null Then Result% = Item\Item\Mass
Return Result%
End Function

Function BVM_ITEMRANGE#(Param1%)
	Item.ItemInstance = Object.ItemInstance(Param1%)
	If Item <> Null Then Result# = Item\Item\Range#
Return Result#
End Function

Function BVM_ITEMDAMAGE%(Param1%)
	Item.ItemInstance = Object.ItemInstance(Param1%)
	If Item <> Null Then Result% = Item\Item\WeaponDamage
Return Result%
End Function

Function BVM_ITEMDAMAGETYPE$(Param1%)
	Item.ItemInstance = Object.ItemInstance(Param1%)
	If Item <> Null Then Result$ = DamageTypes$(Item\Item\WeaponDamageType)
Return Result$
End Function

Function BVM_ITEMWEAPONTYPE%(Param1%)
	Item.ItemInstance = Object.ItemInstance(Param1%)
	If Item <> Null Then Result% = Item\Item\WeaponType
Return Result%
End Function

Function BVM_ITEMARMOR%(Param1%)
	Item.ItemInstance = Object.ItemInstance(Param1%)
	If Item <> Null Then Result% = Item\Item\ArmourLevel
Return Result%
End Function

Function BVM_ITEMMISCDATA$(Param1%)
	Item.ItemInstance = Object.ItemInstance(Param1%)
	If Item <> Null Then Result$ = Item\Item\MiscData$
Return Result$
End Function

Function BVM_ITEMHEALTH%(Param1%)
	Item.ItemInstance = Object.ItemInstance(Param1%)
	If Item <> Null Then Result% = Item\ItemHealth
Return Result%
End Function

Function BVM_SETITEMHEALTH(Param1%, Param2%)
	Item.ItemInstance = Object.ItemInstance(Param1%)
	If Item <> Null
		Item\ItemHealth = Param2%
		; If item belongs to a human player, tell them the new health
		Done = False
		For AI.ActorInstance = Each ActorInstance
			If AI\RNID > 0
				For i = 0 To Slots_Inventory
					If AI\Inventory\Items[i] = Item
						Pa$ = "H" + RCE_StrFromInt$(i, 1) + RCE_StrFromInt$(Item\ItemHealth, 1)
						RCE_Send(Host, AI\RNID, P_InventoryUpdate, Pa$, True)
						Done = True
						Exit
					EndIf
				Next
			EndIf
			If Done = True Then Exit
		Next
	EndIf
End Function

Function BVM_ITEMATTRIBUTE%(Param1%, Param2$)
	Item.ItemInstance = Object.ItemInstance(Param1%)
	If Item <> Null
		Attribute = FindAttribute(Param2$)
		If Attribute > -1 Then Result% = Item\Attributes\Value[Attribute]
	EndIf
Return Result%
End Function

Function BVM_PLAYERINGAME%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\RNID > 0 Then Result% = 1
	EndIf
Return Result%
End Function

Function BVM_ACTORISHUMAN%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\RNID > -1 Then Result% = 1
	EndIf
Return Result%
End Function

Function BVM_SETLEADER(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\RNID = -1
			; Remove current leader
			If Actor\Leader <> Null
				Actor\Leader\NumberOfSlaves = Actor\Leader\NumberOfSlaves - 1
				Actor\Leader = Null
				Actor\AIMode = AI_Wait
			EndIf
			; Set new one, if any
			Leader.ActorInstance = Object.ActorInstance(Param2%)
			If Leader <> Null
				Actor\Leader = Leader
				Actor\Leader\NumberOfSlaves = Actor\Leader\NumberOfSlaves + 1
				; Make sure it no longer belongs to any spawn point
				If Actor\SourceSP > -1
					AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
					AInstance\Spawned[Actor\SourceSP] = AInstance\Spawned[Actor\SourceSP] - 1
					Actor\SourceSP = -1
				EndIf
				Actor\AIMode = AI_Pet
			; No leader!
			Else
				; Assign to first available waypoint
				AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
				Found = False
				For i = 0 To 249
					If AInstance\Area\PrevWaypoint[i] <> 255
						Actor\OldX# = Actor\X#
						Actor\OldZ# = Actor\Z#
						Actor\AIMode = AI_Patrol
						Actor\DestX# = AInstance\Area\WaypointX#[i] + Rnd#(-5.0, 5.0)
						Actor\DestZ# = AInstance\Area\WaypointZ#[i] + Rnd#(-5.0, 5.0)
						Actor\CurrentWaypoint = i
						Found = True
						Exit
					EndIf
				Next
				; Die
				If Found = False Then KillActor(Actor, Null)
			EndIf
		EndIf
	EndIf
End Function

Function BVM_ACTORLEADER%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\Leader <> Null Then Result% = Handle(Actor\Leader)
	EndIf
Return Result%
End Function

Function BVM_ACTORPETS%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Actor\NumberOfSlaves
Return Result%
End Function

Function BVM_ACTORDESTINATIONX#(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result# = Actor\X#
Return Result#
End Function

Function BVM_ACTORDESTINATIONZ#(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result# = Actor\Z#
Return Result#
End Function

Function BVM_ACTORUNDERWATER%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\Underwater <> 0 Then Result% = 1
	EndIf
Return Result%
End Function

Function BVM_ACTORGENDER%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\Gender = 0
			If Actor\Actor\Genders = 3
				Result% = 3
			Else
				Result% = 1
			EndIf
		Else
			Result% = 2
		EndIf
	EndIf
Return Result%
End Function

;Function BVM_SETOWNER(Param1%, Param2$, Param3%, Param4% = 0) {##}
;	Actor.ActorInstance = Object.ActorInstance(Param1%)
;	Zone.Area = FindArea(Param2$)
;	If Zone <> Null
;		SceneryID = Param3%
;		If Zone\Instances[Param4%] <> Null
;			If SceneryID >= 0 And SceneryID < 500
;				If Actor <> Null
;					A.Account = Object.Account(Actor\Account)
;					Zone\Instances[Param4%]\OwnedScenery[SceneryID]\AccountName$ = A\User$
;					Zone\Instances[Param4%]\OwnedScenery[SceneryID]\CharNumber = A\LoggedOn
;				Else
;					Zone\Instances[Param4%]\OwnedScenery[SceneryID]\AccountName$ = ""
;					Zone\Instances[Param4%]\OwnedScenery[SceneryID]\CharNumber = 0
;				End If
;			EndIf
;		Else
;			WriteLog(MainLog, "Error: Cannot set owner in instance #" + Str$(Param4%) + " of " + Zone\Name$ + " as the instance does not exist")
;		EndIf
;	Else
;		WriteLog(MainLog, "Error: Zone " + Param2$ + " does not exist in SetOwner command.")
;	EndIf

;End Function

;Function BVM_SCENERYOWNER%(Param1$, Param2%, Param3%=0) {##}
;	Zone.Area = FindArea(Param1$)
;	If Zone <> Null
;		SceneryID = Param2%
;		Instance = Param3%
;		If SceneryID >= 0 And SceneryID < 500
;			If Zone\Instances[Instance] <> Null
;				For A.Account = Each Account
;					If A\User$ = Zone\Instances[Instance]\OwnedScenery[SceneryID]\AccountName$
;						Actor.ActorInstance = A\Character[Zone\Instances[Instance]\OwnedScenery[SceneryID]\CharNumber]
;						If Actor <> Null Then Result% = Handle(Actor)
;					EndIf
;				Next
;			Else
;				WriteLog(MainLog, "Error: Cannot get owner in instance #" + Str$(Instance) + " of " + Zone\Name$ + " as the instance does not exist")
;			EndIf
;		EndIf
;	Else
;		WriteLog(MainLog, "Error: Zone " + Param1$ + " does not exist in SceneryOwner command.")
;	EndIf
;Return Result%
;End Function

Function BVM_ACTORID%(Param1$, Param2$)
	Race$ = Upper$(Param1$)
	Class$ = Upper$(Param2$)
	Result% = -1
	For Ac.Actor = Each Actor
		If Upper$(Ac\Race$) = Race$
			If Upper$(Ac\Class$) = Class$
				Result% = Ac\ID
				Exit
			EndIf
		EndIf
	Next
Return Result%
End Function

Function BVM_ACTORIDFROMINSTANCE%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Result% = Actor\Actor\ID
	Else
		Result% = -1
	EndIf
Return Result%
End Function

Function BVM_ACTORCLOTHES%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Actor\BodyTex + 1
Return Result%
End Function

Function BVM_SETACTORCLOTHES(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Actor\BodyTex = Param2% - 1
		Pa$ = "B" + RCE_StrFromInt$(Actor\RuntimeID, 2) + Chr$(Actor\BodyTex)
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		A2.ActorInstance = AInstance\FirstInZone
		While A2 <> Null
			If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_AppearanceUpdate, Pa$, True)
			A2 = A2\NextInZone
		Wend
	EndIf
End Function

Function BVM_ACTORFACE%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Actor\FaceTex + 1
Return Result%
End Function

Function BVM_SETACTORFACE(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Actor\FaceTex = Param2% - 1
		Pa$ = "F" + RCE_StrFromInt$(Actor\RuntimeID, 2) + Chr$(Actor\FaceTex)
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		A2.ActorInstance = AInstance\FirstInZone
		While A2 <> Null
			If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_AppearanceUpdate, Pa$, True)
			A2 = A2\NextInZone
		Wend
	EndIf
End Function

Function BVM_ITEMNAME$(Param1%)
	Item.ItemInstance = Object.ItemInstance(Param1%)
	If Item <> Null
		Result$ = Item\Item\Name$
	Else
		Result$ = ""
	EndIf
Return Result$
End Function

Function BVM_ACTORBACKPACK%(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1)
	If Actor <> Null
		Num = Param2 - 1
		Result% = Handle(Actor\Inventory\Items[SlotI_Backpack + Num])
	EndIf
Return Result
End Function

Function BVM_BACKPACKCOUNT%(Param1%, Param2%)
	Result% = 0
	Actor.ActorInstance = Object.ActorInstance(Param1)
	If Actor <> Null
		Num = Param2 - 1
		If SlotI_Backpack + Num >= SlotI_Backpack And SlotI_Backpack + Num <= Slots_Inventory Then
			If Actor\Inventory\Items[SlotI_Backpack + Num] <> Null
				Result = Actor\Inventory\Amounts[SlotI_Backpack + Num]
			EndIf
		Else
			WriteLog(MainLog, "Error: Backpack Slot out of bounds")
			Result = 0
		EndIf
	EndIf
Return Result%
End Function


Function BVM_ACTORHAT%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Handle(Actor\Inventory\Items[SlotI_Hat])
Return Result%
End Function

Function BVM_ACTORWEAPON%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Handle(Actor\Inventory\Items[SlotI_Weapon])
Return Result%
End Function

Function BVM_ACTORSHIELD%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Handle(Actor\Inventory\Items[SlotI_Shield])
Return Result%
End Function

Function BVM_ACTORCHEST%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Handle(Actor\Inventory\Items[SlotI_Chest])
Return Result%
End Function

Function BVM_ACTORHANDS%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Handle(Actor\Inventory\Items[SlotI_Hand])
Return Result%
End Function

Function BVM_ACTORBELT%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Handle(Actor\Inventory\Items[SlotI_Belt])
Return Result%
End Function

Function BVM_ACTORFEET%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Handle(Actor\Inventory\Items[SlotI_Feet])
Return Result%
End Function

Function BVM_ACTORLEGS%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Handle(Actor\Inventory\Items[SlotI_Legs])
Return Result%
End Function

Function BVM_ACTORRING%(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Num = Param2% - 1
		Result% = Handle(Actor\Inventory\Items[SlotI_Ring1 + Num])
	EndIf
Return Result%
End Function

Function BVM_ACTORAMULET%(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Num = Param2% - 1
		Result% = Handle(Actor\Inventory\Items[SlotI_Amulet1 + Num])
	EndIf
Return Result%
End Function

Function BVM_ACTORGROUP%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Actor\TeamID
Return Result%
End Function

Function BVM_SETACTORGROUP(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Actor\TeamID = Param2%
End Function

Function BVM_FIREPROJECTILE(Param1%, Param2%, Param3$)
	PID = FindProjectile(Param3$)
	If PID > -1
		A1.ActorInstance = Object.ActorInstance(Param1%)
		If A1 <> Null
			A2.ActorInstance = Object.ActorInstance(Param2%)
			If A2 <> Null Then FireProjectile(ProjectileList(PID), A1, A2)
		EndIf
	EndIf
End Function

Function BVM_ACTOROUTDOORS%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		If AInstance <> Null Then Result% = AInstance\Area\Outdoors
	EndIf
Return Result%
End Function

Function BVM_ZONEOUTDOORS%(Param1$)
	Name$ = Upper$(Param1$)
	For Ar.Area = Each Area
		If Ar\Name$ = Name$
			Result% = Ar\Outdoors
			Exit
		EndIf
	Next
Return Result%
End Function

Function BVM_ADDACTOREFFECT(Param1%, Param2$, Param3$, Param4%, Param5%, Param6%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		EffectName$ = Upper$(Param2$)
		Found = False
		For AE.ActorEffect = Each ActorEffect
			If AE\Owner = Actor
				If Upper$(AE\Name$) = EffectName$
					FoundAE.ActorEffect = AE
					Found = True
					Exit
				EndIf
			EndIf
		Next
		If Found = False
			FoundAE = New ActorEffect
			FoundAE\Attributes = New Attributes
			FoundAE\Name$ = Param2$
			FoundAE\Owner = Actor
			FoundAE\IconTexID = Param6%
			If FoundAE\Owner\RNID > 0
				Pa$ = RCE_StrFromInt$(Handle(FoundAE), 4) + RCE_StrFromInt$(FoundAE\IconTexID, 2) + FoundAE\Name$
				RCE_Send(Host, FoundAE\Owner\RNID, P_ActorEffect, "A" + Pa$, True)
			EndIf
		EndIf
		FoundAE\CreatedTime = MilliSecs()
		FoundAE\Length = Param5% * 1000
		Att = FindAttribute(Param3$)
		If Att > -1
			Old = FoundAE\Attributes\Value[Att]
			FoundAE\Attributes\Value[Att] = Param4%
;Fix from RC Standard to Setting Actor Effects on NPC's
			FoundAE\Owner\Attributes\Value[Att] = FoundAE\Owner\Attributes\Value[Att] + (FoundAE\Attributes\Value[Att] - Old)
			If FoundAE\Owner\RNID > 0
				Pa$ = RCE_StrFromInt$(Att, 1) + RCE_StrFromInt$(FoundAE\Attributes\Value[Att] - Old, 4)
				RCE_Send(Host, FoundAE\Owner\RNID, P_ActorEffect, "E" + Pa$, True)
			EndIf
;End Fix
;Old Code	;Pa$ = RCE_StrFromInt$(Att, 1) + RCE_StrFromInt$(FoundAE\Attributes\Value[Att] - Old, 4)
			;FoundAE\Owner\Attributes\Value[Att] = FoundAE\Owner\Attributes\Value[Att] + (FoundAE\Attributes\Value[Att] - Old)
			;RCE_Send(Host, FoundAE\Owner\RNID, P_ActorEffect, "E" + Pa$, True)
		EndIf
	EndIf
End Function

Function BVM_DELETEACTOREFFECT(Param1%, Param2$)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		EffectName$ = Upper$(Param2$)
		For AE.ActorEffect = Each ActorEffect
			If AE\Owner = Actor
				If Upper$(AE\Name$) = EffectName$
					If AE\Owner\RNID > 0
						Pa$ = RCE_StrFromInt$(Handle(AE), 4)
						For i = 0 To 39
							Pa$ = Pa$ + RCE_StrFromInt$(AE\Attributes\Value[i], 4)
						Next
						RCE_Send(Host, AE\Owner\RNID, P_ActorEffect, "R" + Pa$, True)
					EndIf

					For i = 0 To 39
						AE\Owner\Attributes\Value[i] = AE\Owner\Attributes\Value[i] - AE\Attributes\Value[i]
					Next
					Delete AE\Attributes
					Delete AE
					Exit
				EndIf
			EndIf
		Next
	EndIf
End Function

Function BVM_ACTORHASEFFECT%(Param1%, Param2$)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		EffectName$ = Upper$(Param2$)
		For AE.ActorEffect = Each ActorEffect
			If AE\Owner = Actor
				If Upper$(AE\Name$) = EffectName$ Then Result% = 1 : Exit
			EndIf
		Next
	EndIf
Return Result%
End Function

Function BVM_SCREENFLASH(Param1%, Param2%, Param3%, Param4%, Param5%, Param6%, Param7%=0)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\RNID > 0
			R = Param2%
			G = Param3%
			B = Param4%
			Alpha = Param5%
			Length = Param6%
			TexID = Param7%
			Pa$ = RCE_StrFromInt$(R, 1) + RCE_StrFromInt$(G, 1) + RCE_StrFromInt$(B, 1) + RCE_StrFromInt$(Alpha, 1) + RCE_StrFromInt$(Length, 4)
			RCE_Send(Host, Actor\RNID, P_ScreenFlash, Pa$ + RCE_StrFromInt$(TexID, 2), True)
		EndIf
	EndIf
End Function

Function BVM_ADDABILITY(Param1%, Param2$, Param3%=1)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		SpellName$ = Upper$(Param2$)
		Lvl = Param3%
		If Lvl <= 0 Then Lvl = 1
		; Check it's not already known
		Known = False
		For i = 0 To 999
			If Actor\SpellLevels[i] > 0
				If Upper$(SpellsList(Actor\KnownSpells[i])\Name$) = SpellName$ Then Known = True : Exit
			EndIf
		Next
		If Known = False
			For Sp.Spell = Each Spell
				If Upper$(Sp\Name$) = SpellName$ Then AddSpell(Actor, Sp\ID, Lvl) : Exit
			Next
		EndIf
	EndIf
End Function

Function BVM_DELETEABILITY(Param1%, Param2$)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		SpellName$ = Upper$(Param2$)
		For i = 0 To 999
			If Actor\SpellLevels[i] > 0
				If Upper$(SpellsList(Actor\KnownSpells[i])\Name$) = SpellName$ Then DeleteSpell(Actor, i)
			EndIf
		Next
	EndIf
End Function

Function BVM_ABILITYKNOWN%(Param1%, Param2$)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		SpellName$ = Upper$(Param2$)
		For i = 0 To 999
			If Actor\SpellLevels[i] > 0
				If Upper$(SpellsList(Actor\KnownSpells[i])\Name$) = SpellName$ Then Result% = 1 : Exit
			EndIf
		Next
	EndIf
Return Result%
End Function

Function BVM_ABILITYMEMORISED%(Param1%, Param2$)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		SpellName$ = Upper$(Param2$)
		For i = 0 To 9
			If Actor\MemorisedSpells[i] <> 5000
				ID = Actor\KnownSpells[Actor\MemorisedSpells[i]]
				If Upper$(SpellsList(ID)\Name$) = SpellName$ Then Result% = 1 : Exit
			EndIf
		Next
	EndIf
Return Result%
End Function

Function BVM_ABILITYLEVEL%(Param1%, Param2$)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		SpellName$ = Upper$(Param2$)
		For i = 0 To 999
			If Actor\SpellLevels[i] > 0
				If Upper$(SpellsList(Actor\KnownSpells[i])\Name$) = SpellName$ Then Result% = Actor\SpellLevels[i] : Exit
			EndIf
		Next
	EndIf
Return Result%
End Function

Function BVM_SETABILITYLEVEL(Param1%, Param2$, Param3%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		SpellName$ = Upper$(Param2$)
		Lvl = Param3%
		For i = 0 To 999
			If Actor\SpellLevels[i] > 0
				If Upper$(SpellsList(Actor\KnownSpells[i])\Name$) = SpellName$
					Actor\SpellLevels[i] = Lvl
					If Actor\RNID > 0
						Pa$ = RCE_StrFromInt$(Lvl, 4) + SpellsList(Actor\KnownSpells[i])\Name$
						RCE_Send(Host, Actor\RNID, P_KnownSpellUpdate, "L" + Pa$, True)
					EndIf
					Exit
				EndIf
			EndIf
		Next
	EndIf
End Function

Function BVM_ANIMATEACTOR(Param1%, Param2$, Param3#, Param4%=0)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Pa$ = RCE_StrFromInt$(Actor\RuntimeID, 2) + RCE_StrFromInt$(Param4%, 1)
		Pa$ = Pa$ + RCE_StrFromFloat$(Param3#) + Param2$
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		A2.ActorInstance = AInstance\FirstInZone
		While A2 <> Null
			If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_AnimateActor, Pa$, True)
			A2 = A2\NextInZone
		Wend
	EndIf
End Function

Function BVM_PLAYMUSIC(Param1%, Param2%, Param3%=0)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		ID = Param2%
		Pa$ = RCE_StrFromInt$(ID, 2)
		; Play to all
		If Param3% = True
			AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
			A2.ActorInstance = AInstance\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_Music, Pa$, True)
				A2 = A2\NextInZone
			Wend
		; Play to single person only
		ElseIf Actor\RNID > 0
			RCE_Send(Host, Actor\RNID, P_Music, Pa$, True)
		EndIf
	EndIf
End Function

Function BVM_PLAYSOUND(Param1%, Param2%, Param3%=0)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		ID = Param2%
		Pa$ = RCE_StrFromInt$(ID, 2) + RCE_StrFromInt$(Actor\RuntimeID, 2)
		; Play to all
		If Param3% = True
			AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
			A2.ActorInstance = AInstance\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_Sound, Pa$, True)
				A2 = A2\NextInZone
			Wend
		; Play to single person only
		ElseIf Actor\RNID > 0
			RCE_Send(Host, Actor\RNID, P_Sound, Pa$, True)
		EndIf
	EndIf
End Function

Function BVM_PLAYSPEECH(Param1%, Param2%=0)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		ID = Param2%
		Pa$ = RCE_StrFromInt$(ID, 2) + RCE_StrFromInt$(Actor\RuntimeID, 2)
		; Play to all
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		A2.ActorInstance = AInstance\FirstInZone
		While A2 <> Null
			If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_Speech, Pa$, True)
			A2 = A2\NextInZone
		Wend
	EndIf
End Function

Function BVM_CREATEEMITTER(Param1%, Param2$, Param3%, Param4%, Param5#=0, Param6#=0, Param7#=0, Param8%=0)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	S.ScriptInstance = Object.ScriptInstance(hSI%)
	If Actor <> Null Then RuntimeID = Actor\RuntimeID Else RuntimeID = 0
	Name$ = Param2$
	TexID = Param3%
	Time = Param4%
	OffsetX# = Param5#
	OffsetY# = Param6#
	OffsetZ# = Param7#
	Pa$ = RCE_StrFromInt$(TexID, 2) + RCE_StrFromInt$(Time, 4) + RCE_StrFromInt(RuntimeID, 2)
	Pa$ = Pa$ + RCE_StrFromFloat$(OffsetX#) + RCE_StrFromFloat$(OffsetY#) + RCE_StrFromFloat$(OffsetZ#) + Name$
	Actor2.ActorInstance = Object.ActorInstance(Param8%)
	; Display to all actors in zone
	If Actor2 = Null
		; Send to actors in the same zone as specified actor (or the zone of the script actor if none specified)
		If Actor = Null Then Actor = Object.ActorInstance(S\AI)
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		A2.ActorInstance = AInstance\FirstInZone
		While A2 <> Null
			If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_CreateEmitter, Pa$, True)
			A2 = A2\NextInZone
		Wend
	; Display to specific actor
	Else
		RCE_Send(Host, Actor2\RNID, P_CreateEmitter, Pa$, True)
	EndIf
End Function

Function BVM_SETFACTIONRATING(Param1%, Param2$, Param3%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Faction$ = Upper$(Param2$)
		For i = 0 To 99
			If Upper$(FactionNames$(i)) = Faction$
				Actor\FactionRatings[i] = Param3% + 100
				If Actor\FactionRatings[i] < 0
					Actor\FactionRatings[i] = 0
				ElseIf Actor\FactionRatings[i] > 200
					Actor\FactionRatings[i] = 200
				EndIf
				Exit
			EndIf
		Next
	EndIf
End Function

Function BVM_CHANGEFACTIONRATING(Param1%, Param2$, Param3%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Faction$ = Upper$(Param2$)
		For i = 0 To 99
			If Upper$(FactionNames$(i)) = Faction$
				Actor\FactionRatings[i] = Actor\FactionRatings[i] + Param3%
				If Actor\FactionRatings[i] < 0
					Actor\FactionRatings[i] = 0
				ElseIf Actor\FactionRatings[i] > 200
					Actor\FactionRatings[i] = 200
				EndIf
				Exit
			EndIf
		Next
	EndIf
End Function

Function BVM_SETHOMEFACTION(Param1%, Param2$)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Faction$ = Upper$(Param2$)
		For i = 0 To 99
			If Upper$(FactionNames$(i)) = Faction$
				Actor\HomeFaction = i
				Exit
			EndIf
		Next
	EndIf
End Function

Function BVM_HOMEFACTION$(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Result$ = FactionNames$(Actor\HomeFaction)
	Else
		Result$ = ""
	EndIf
Return Result$
End Function

Function BVM_FACTIONRATING%(Param1%, Param2$)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Faction$ = Upper$(Param2$)
		For i = 0 To 99
			If Upper$(FactionNames$(i)) = Faction$
				Result% = Actor\FactionRatings[i] - 100
				Exit
			EndIf
		Next
	EndIf
Return Result%
End Function

Function BVM_GETFACTION$(Param1%)
		For i = 0 To Param1%
			Result$ = FactionNames$(i)
		Next
Return Result$
End Function

Function BVM_DEFAULTFACTIONRATING%(Param1$, Param2$)
	Faction1$ = Upper$(Param1$)
	Faction2$ = Upper$(Param2$)
	For i = 0 To 99
		If Upper$(FactionNames$(i)) = Faction1$
			For j = 0 To 99
				If Upper$(FactionNames$(i)) = Faction1$
					Result% = FactionDefaultRatings(i, j)
					Exit
				EndIf
			Next
			Exit
		EndIf
	Next
Return Result%
End Function

Function BVM_SPLIT$(Param1$, Param2%, Param3$=",")
	Num = Param2%
	Delimiter$ = Param3$
	Result$ = Split$(Param1$, Num, Delimiter)
Return Result$
End Function

Function BVM_FULLTRIM$(Param1$)
	Result$ = FullTrim$(Param1$)
Return Result$
End Function

Function BVM_DELETEFILE(Param1$)
	DeleteFile(RCScriptFiles$ + Param1$)
End Function

Function BVM_READFILE%(Param1$)
	Result% = ReadFile(RCScriptFiles$ + Param1$)
Return Result%
End Function

Function BVM_WRITEFILE%(Param1$)
	Result% = WriteFile(RCScriptFiles$ + Param1$)
Return Result%
End Function

Function BVM_OPENFILE%(Param1$)
	Result% = OpenFile(RCScriptFiles$ + Param1$)
Return Result%
End Function

Function BVM_APPENDFILE%(Param1$)
	Filename$ = RCScriptFiles$ + Param1$
	F = OpenFile(Filename$)
	SeekFile(F, FileSize(Filename$))
	Result% = F
Return Result%
End Function

Function BVM_CREATEDIR%(Param1$)
	CreateDir(RCScriptFiles$ + Param1$)
Return Result%
End Function

Function BVM_FILESIZE%(Param1$)
	Result% = FileSize(RCScriptFiles$ + Param1$)
Return Result%
End Function

Function BVM_FILETYPE%(Param1$)
	Result% = FileType(RCScriptFiles$ + Param1$)
Return Result%
End Function

Function BVM_WARP(Param1%, Param2$, Param3$, Param4%=0)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Name$ = Upper$(Param2$)
		PortalName$ = Upper$(Param3$)
		Instance = Param4%
		For Ar.Area = Each Area
			If Upper$(Ar\Name$) = Name$
				Portal = 0
				For i = 0 To 99
					If Upper$(Ar\PortalName$[i]) = PortalName$ Then Portal = i : Exit
				Next
				SetArea(Actor, Ar, Instance, -1, Portal)
				Exit
			EndIf
		Next
	EndIf
End Function

Function BVM_ACTORZONE$(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Result$ = Actor\Area$
	EndIf
Return Result$
End Function

Function BVM_ACTORZONEINSTANCE%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		If AInstance <> Null Then Result% = AInstance\ID
	EndIf
Return Result%
End Function

Function BVM_UPDATEXPBAR(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Actor\XPBarLevel = Param2%
		If Actor\RNID > 0
			RCE_Send(Host, Actor\RNID, P_XPUpdate, "B" + RCE_StrFromInt$(Actor\XPBarLevel), True)
		EndIf
	EndIf
End Function

Function BVM_GIVEXP(Param1%, Param2%, Param3%=0)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		GiveXP(Actor, Param2%, Param3%)
	EndIf
End Function

Function BVM_ACTORXP%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Actor\XP
Return Result%
End Function

Function BVM_ACTORXPMULTIPLIER%(Param1%)
	ID = Param1%
	If ActorList(ID) <> Null
		Result% = ActorList(ID)\XPMultiplier
	EndIf
Return Result%
End Function

Function BVM_SETACTORLEVEL(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Actor\XP = 0
		Actor\Level = Param2%

		; Tell this player if actor is human
		If Actor\RNID > 0 Then RCE_Send(Host, Actor\RNID, P_XPUpdate, "U" + RCE_StrFromInt$(Actor\Level, 2), True)

		; Tell all other players
		Pa$ = RCE_StrFromInt$(Actor\RuntimeID, 2) + RCE_StrFromInt$(Actor\Level, 2)
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		A2.ActorInstance = AInstance\FirstInZone
		While A2 <> Null
			If A2\RNID > 0
				If A2 <> Actor Then RCE_Send(Host, A2\RNID, P_XPUpdate, "L" + Pa$, True)
			EndIf
			A2 = A2\NextInZone
		Wend
	EndIf
End Function

Function BVM_ACTORLEVEL%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Actor\Level
Return Result%
End Function

Function BVM_ACTORDISTANCE#(Param1%, Param2%)
	Actor1.ActorInstance = Object.ActorInstance(Param1%)
	Actor2.ActorInstance = Object.ActorInstance(Param2%)
	If Actor1 <> Null And Actor2 <> Null
		XDist# = Actor1\X# - Actor2\X#
		XDist# = XDist# * XDist#
		YDist# = Actor1\Y# - Actor2\Y#
		YDist# = YDist# * YDist#
		ZDist# = Actor1\Z# - Actor2\Z#
		ZDist# = ZDist# * ZDist#
		Result# = Sqr#(XDist# + YDist# + ZDist#)
	EndIf
Return Result#
End Function

Function BVM_SCRIPTLOG(Param1$="")
	WriteLog(MainLog, "Script log: " + Param1$)
End Function

Function BVM_RUNTIMEERROR(Param1$="")
	Shutdown()
	RuntimeError(Param1$)
End Function

Function BVM_NEWQUEST(Param1%, Param2$, Param3$, Param4%=255, Param5%=255, Param6%=255)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\RNID > 0
			A.Account = Object.Account(Actor\Account)
			Name$ = Param2$
			; Check it doesn't already exist
			FreeSpace = -1
			AlreadyExists = False
			For i = 0 To 499
				If Len(A\QuestLog[A\LoggedOn]\EntryName$[i]) = 0
					FreeSpace = i
				ElseIf Upper$(A\QuestLog[A\LoggedOn]\EntryName$[i]) = Upper$(Name$)
					AlreadyExists = True
					Exit
				EndIf
			Next
			If AlreadyExists = False And FreeSpace > -1
				Status$ = RCE_StrFromInt$(Param4%, 1)
				Status$ = Status$ + RCE_StrFromInt$(Param5%, 1)
				Status$ = Status$ + RCE_StrFromInt$(Param6%, 1)
				Status$ = Status$ + Param3$
				A\QuestLog[A\LoggedOn]\EntryName$[FreeSpace] = Name$
				A\QuestLog[A\LoggedOn]\EntryStatus$[FreeSpace] = Status$
				Pa$ = RCE_StrFromInt$(Len(Name$), 1) + Name$
				Pa$ = Pa$ + RCE_StrFromInt$(Len(Status$), 2) + Status$
				RCE_Send(Host, Actor\RNID, P_QuestLog, "N" + Pa$, True)
			EndIf
		EndIf
	EndIf
End Function

Function BVM_UPDATEQUEST(Param1%, Param2$, Param3$, Param4%=255, Param5%=255, Param6%=255)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\RNID > 0
			A.Account = Object.Account(Actor\Account)
			Name$ = Upper$(Param2$)
			Status$ = RCE_StrFromInt$(Param4%, 1)
			Status$ = Status$ + RCE_StrFromInt$(Param5%, 1)
			Status$ = Status$ + RCE_StrFromInt$(Param6%, 1)
			Status$ = Status$ + Param3$
			For i = 0 To 499
				If Upper$(A\QuestLog[A\LoggedOn]\EntryName$[i]) = Name$
					A\QuestLog[A\LoggedOn]\EntryStatus$[i] = Status$
					Pa$ = RCE_StrFromInt$(Len(Name$), 1) + Name$
					Pa$ = Pa$ + RCE_StrFromInt$(Len(Status$), 2) + Status$
					RCE_Send(Host, Actor\RNID, P_QuestLog, "U" + Pa$, True)
					Result$ = "1"
					Exit
				EndIf
			Next
		EndIf
	EndIf
End Function

Function BVM_COMPLETEQUEST(Param1%, Param2$)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\RNID > 0
			A.Account = Object.Account(Actor\Account)
			Name$ = Upper$(Param2$)
			Status$ = Chr$(255) + Chr$(225) + Chr$(100) + Chr$(254)
			For i = 0 To 499
				If Upper$(A\QuestLog[A\LoggedOn]\EntryName$[i]) = Name$
					A\QuestLog[A\LoggedOn]\EntryStatus$[i] = Status$
					Pa$ = RCE_StrFromInt$(Len(Name$), 1) + Name$
					Pa$ = Pa$ + RCE_StrFromInt$(Len(Status$), 2) + Status$
					RCE_Send(Host, Actor\RNID, P_QuestLog, "U" + Pa$, True)
					Exit
				EndIf
			Next
		EndIf
	EndIf
End Function

Function BVM_DELETEQUEST(Param1%, Param2$)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\RNID > 0
			A.Account = Object.Account(Actor\Account)
			Name$ = Upper$(Param2$)
			For i = 0 To 499
				If Upper$(A\QuestLog[A\LoggedOn]\EntryName$[i]) = Name$
					A\QuestLog[A\LoggedOn]\EntryName$[i] = ""
					A\QuestLog[A\LoggedOn]\EntryStatus$[i] = ""
					RCE_Send(Host, Actor\RNID, P_QuestLog, "D" + Name$, True)
					Exit
				EndIf
			Next
		EndIf
	EndIf
End Function

Function BVM_QUESTSTATUS$(Param1%, Param2$)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\RNID > 0
			A.Account = Object.Account(Actor\Account)
			Name$ = Upper$(Param2$)
			For i = 0 To 499
				If Upper$(A\QuestLog[A\LoggedOn]\EntryName$[i]) = Name$
					Result$ = Mid$(A\QuestLog[A\LoggedOn]\EntryStatus$[i], 4)
					Exit
				EndIf
			Next
		EndIf
	EndIf
Return Result$
End Function

Function BVM_QUESTCOMPLETE%(Param1%, Param2$)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\RNID > 0
			A.Account = Object.Account(Actor\Account)
			Name$ = Upper$(Param2$)
			For i = 0 To 499
				If Upper$(A\QuestLog[A\LoggedOn]\EntryName$[i]) = Name$
					If A\QuestLog[A\LoggedOn]\EntryStatus$[i] = Chr$(255) + Chr$(225) + Chr$(100) + Chr$(254)
						Result% = 1
					Else
						Result% = 0
					EndIf
					Exit
				EndIf
			Next
		EndIf
	EndIf
Return Result%
End Function

Function BVM_SETREPUTATION(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then UpdateReputation(Actor, Param2%)
End Function

Function BVM_REPUTATION%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Actor\Reputation
Return Result%
End Function

Function BVM_SETRESISTANCE(Param1%, Param2$, Param3%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Attribute = FindDamageType(Param2$)
		If Attribute > -1
			Actor\Resistances[Attribute] = Param3%
		EndIf
	EndIf
End Function

Function BVM_RESISTANCE%(Param1%, Param2$)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Attribute = FindDamageType(Param2$)
		If Attribute > -1 Then Result% = Actor\Resistances[Attribute]
	EndIf
	Return Result%
End Function

Function BVM_SETATTRIBUTE(Param1%, Param2$, Param3%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Attribute = FindAttribute(Param2$)
		If Attribute > -1
			; Important attribute, tell everyone
			If Attribute = HealthStat Or Attribute = SpeedStat Or Attribute = EnergyStat
				UpdateAttribute(Actor, Attribute, Param3%)
					; Death
				If Actor\Attributes\Value[HealthStat] <= 0 Then KillActor(Actor, Null)
			; Unimportant attribute, only tell specific player (if it is a human player)
			Else
				Actor\Attributes\Value[Attribute] = Param3%
				If Actor\Attributes\Value[Attribute] > Actor\Attributes\Maximum[Attribute]
					Actor\Attributes\Value[Attribute] = Actor\Attributes\Maximum[Attribute]
				EndIf
				If Actor\RNID > 0
					Pa$ = RCE_StrFromInt$(Actor\RuntimeID, 2) + RCE_StrFromInt$(Attribute, 1) + RCE_StrFromInt$(Actor\Attributes\Value[Attribute], 2)
					RCE_Send(Host, Actor\RNID, P_StatUpdate, "A" + Pa$, True)
				EndIf
			EndIf
		EndIf
	EndIf
End Function

Function BVM_CHANGEATTRIBUTE(Param1%, Param2$, Param3%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Attribute = FindAttribute(Param2$)
		If Attribute > -1 Then Result% = Actor\Attributes\Value[Attribute]
		Result = Result + Param3
		; Important attribute, tell everyone
		If Attribute = HealthStat Or Attribute = SpeedStat Or Attribute = EnergyStat
			UpdateAttribute(Actor, Attribute, Result)
				; Death
			If Actor\Attributes\Value[HealthStat] <= 0 Then KillActor(Actor, Null)
		; Unimportant attribute, only tell specific player (if it is a human player)
		Else
			Actor\Attributes\Value[Attribute] = Result%
			If Actor\Attributes\Value[Attribute] > Actor\Attributes\Maximum[Attribute]
				Actor\Attributes\Value[Attribute] = Actor\Attributes\Maximum[Attribute]
			EndIf
			If Actor\RNID > 0
				Pa$ = RCE_StrFromInt$(Actor\RuntimeID, 2) + RCE_StrFromInt$(Attribute, 1) + RCE_StrFromInt$(Actor\Attributes\Value[Attribute], 2)
				RCE_Send(Host, Actor\RNID, P_StatUpdate, "A" + Pa$, True)
			EndIf
		EndIf
	EndIf
End Function

Function BVM_ATTRIBUTE%(Param1%, Param2$)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Attribute = FindAttribute(Param2$)
		If Attribute > -1 Then Result% = Actor\Attributes\Value[Attribute]
	EndIf
Return Result%
End Function

Function BVM_SETMAXATTRIBUTE(Param1%, Param2$, Param3%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Attribute = FindAttribute(Param2$)
		If Attribute > -1
			; Important attribute, tell everyone
			If Attribute = HealthStat Or Attribute = SpeedStat Or Attribute = EnergyStat
				UpdateAttributeMax(Actor, Attribute, Param3%)
			; Unimportant attribute, only tell specific player (if it is a human player)
			Else
				Actor\Attributes\Maximum[Attribute] = Param3%
				If Actor\RNID > 0
					Pa$ = RCE_StrFromInt$(Actor\RuntimeID, 2) + RCE_StrFromInt$(Attribute, 1) + RCE_StrFromInt$(Actor\Attributes\Maximum[Attribute], 2)
					RCE_Send(Host, Actor\RNID, P_StatUpdate, "M" + Pa$, True)
				EndIf
			EndIf
		EndIf
	EndIf
End Function

Function BVM_CHANGEMAXATTRIBUTE(Param1%, Param2$, Param3%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Attribute = FindAttribute(Param2$)
		If Attribute > -1 Then Result% = Actor\Attributes\Maximum[Attribute]
		Result = Result + Param3
		; Important attribute, tell everyone
		If Attribute = HealthStat Or Attribute = SpeedStat Or Attribute = EnergyStat
			UpdateAttributeMax(Actor, Attribute, Result%)
		; Unimportant attribute, only tell specific player (if it is a human player)
		Else
			Actor\Attributes\Maximum[Attribute] = Result%
			If Actor\RNID > 0
				Pa$ = RCE_StrFromInt$(Actor\RuntimeID, 2) + RCE_StrFromInt$(Attribute, 1) + RCE_StrFromInt$(Actor\Attributes\Maximum[Attribute], 2)
				RCE_Send(Host, Actor\RNID, P_StatUpdate, "M" + Pa$, True)
			EndIf
		EndIf
	EndIf
End Function

Function BVM_MAXATTRIBUTE%(Param1%, Param2$)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Attribute = FindAttribute(Param2$)
		If Attribute > -1 Then Result% = Actor\Attributes\Maximum[Attribute]
	EndIf
	Return Result%
End Function

Function BVM_RACE$(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Result$ = Actor\Actor\Race$
	Else
		Result$ = ""
	EndIf
Return Result$
End Function

Function BVM_CLASS$(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Result$ = Actor\Actor\Class$
	Else
		Result$ = ""
	EndIf
Return Result$
End Function

Function BVM_SETNAME(Param1%, Param2$)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Actor\Name$ = BVM_DEQUOTE(Param2$)
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		If AInstance <> Null
			Pa$ = RCE_StrFromInt$(Actor\RuntimeID, 2) + RCE_StrFromInt$(Len(Actor\Name$), 1) + Actor\Name$ + Actor\Tag$
			A2.ActorInstance = AInstance\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_NameChange, Pa$, True)
				A2 = A2\NextInZone
			Wend
		EndIf
	EndIf
End Function

Function BVM_NAME$(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Result$ = Actor\Name$
	Else
		Result$ = ""
	EndIf
Return Result$
End Function

Function BVM_SETTAG(Param1%, Param2$)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Actor\Tag$ = Param2$
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		If AInstance <> Null
			Pa$ = RCE_StrFromInt$(Actor\RuntimeID, 2) + RCE_StrFromInt$(Len(Actor\Name$), 1) + Actor\Name$ + Actor\Tag$
			A2.ActorInstance = AInstance\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_NameChange, Pa$, True)
				A2 = A2\NextInZone
			Wend
		EndIf
	EndIf
End Function

Function BVM_TAG$(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Result$ = Actor\Tag$
	Else
		Result$ = ""
	EndIf
Return Result$
End Function

Function BVM_GOLD%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Actor\Gold
Return Result%
End Function

Function BVM_MONEY%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Actor\Gold
Return Result%
End Function

Function BVM_CHANGEGOLD(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Change = Param2%
		Actor\Gold = Actor\Gold + Change
		If Actor\Gold < 0 Then Actor\Gold = 0
		If Actor\RNID > 0
			If Change > 0
				Pa$ = "U" + RCE_StrFromInt$(Change, 4)
			Else
				Pa$ = "D" + RCE_StrFromInt$(Abs(Change), 4)
			EndIf
			RCE_Send(Host, Actor\RNID, P_GoldChange, Pa$, True)
		EndIf
	EndIf
End Function

Function BVM_CHANGEMONEY(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Change = Param2%
		Actor\Gold = Actor\Gold + Change
		If Actor\Gold < 0 Then Actor\Gold = 0
		If Actor\RNID > 0
			If Change > 0
				Pa$ = "U" + RCE_StrFromInt$(Change, 4)
			Else
				Pa$ = "D" + RCE_StrFromInt$(Abs(Change), 4)
			EndIf
			RCE_Send(Host, Actor\RNID, P_GoldChange, Pa$, True)
		EndIf
	EndIf
End Function


Function BVM_SETGOLD(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Amount = Param2%
		Change = Amount - Actor\Gold
		Actor\Gold = Amount
		If Actor\RNID > 0
			If Change > 0
				Pa$ = "U" + RCE_StrFromInt$(Change, 4)
			Else
				Pa$ = "D" + RCE_StrFromInt$(Abs(Change), 4)
			EndIf
			RCE_Send(Host, Actor\RNID, P_GoldChange, Pa$, True)
		EndIf
	EndIf
End Function

Function BVM_SETMONEY(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Amount = Param2%
		Change = Amount - Actor\Gold
		Actor\Gold = Amount
		If Actor\RNID > 0
			If Change > 0
				Pa$ = "U" + RCE_StrFromInt$(Change, 4)
			Else
				Pa$ = "D" + RCE_StrFromInt$(Abs(Change), 4)
			EndIf
			RCE_Send(Host, Actor\RNID, P_GoldChange, Pa$, True)
		EndIf
	EndIf
End Function

Function BVM_YEAR%()
	Result% = Year
Return Result%
End Function

Function BVM_SEASON$()
	Result$ = SeasonName$(GetSeason())
Return Result$
End Function

Function BVM_DAY%()
	Result% = Day
Return Result%
End Function

Function BVM_MONTH$()
	Result$ = MonthName$(GetMonth())
Return Result$
End Function

Function BVM_HOUR%()
	Result% = TimeH
Return Result%
End Function

Function BVM_MINUTE%()
	Result% = TimeM
Return Result%
End Function

Function BVM_NEXTACTOR%(Param1%=0)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor = Null
		Actor = First ActorInstance
	Else
		Actor = After Actor
	EndIf
	If Actor <> Null
		While Actor\RNID = 0
			Actor = After Actor
			If Actor = Null Then Exit
		Wend
		If Actor <> Null Then Result% = Handle(Actor)
	EndIf
Return Result%
End Function

Function BVM_FIRSTACTORINZONE%(Param1$, Param2% = 0)
	ZoneName$ = Upper$(Param1$)
	For Ar.Area = Each Area
		If Upper$(Ar\Name$) = ZoneName$
			Instance = Param2%
			Actor.ActorInstance = Ar\Instances[Instance]\FirstInZone
			If Actor <> Null Then Result% = Handle(Actor)
			Exit
		EndIf
	Next
Return Result%
End Function

Function BVM_NEXTACTORINZONE%(Param1%)
	StartActor.ActorInstance = Object.ActorInstance(Param1%)
	If StartActor <> Null
		Actor.ActorInstance = StartActor\NextInZone
		If Actor = Null
			AInstance.AreaInstance = Object.AreaInstance(StartActor\ServerArea)
			Actor.ActorInstance = AInstance\FirstInZone
		EndIf
		Result% = Handle(Actor)
	EndIf
Return Result%
End Function

Function BVM_OPENTRADING(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	CActor.ActorInstance = Object.ActorInstance(Param2%)
	If Actor <> Null And CActor <> Null
		If Actor\RNID > 0 And Actor\IsTrading = 0
			; Player -> player trading
			If CActor\RNID > 0 And CActor\IsTrading = 0
				Actor\IsTrading = 3
				CActor\IsTrading = 3
				Actor\TradingActor = CActor
				CActor\TradingActor = Actor
				Pa$ = LanguageString$(LS_TradeInviteInstruction)
				RCE_Send(Host, Actor\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_TradeInvite) + " " + CActor\Name$ + Pa$, True)
				RCE_Send(Host, CActor\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_TradeInvite) + " " + Actor\Name$ + Pa$, True)
			; Player -> NPC trading
			ElseIf CActor\RNID = -1
				Actor\IsTrading = 1
				Pa$ = "N"
				For i = SlotI_Backpack To SlotI_Backpack + 31
					If CActor\Inventory\Amounts[i] > 0 And CActor\Inventory\Items[i] <> Null
						CopiedII.ItemInstance = CopyItemInstance(CActor\Inventory\Items[i])
						CopiedII\Assignment = CActor\Inventory\Amounts[i]
						CopiedII\AssignTo = Actor
						Pa$ = Pa$ + ItemInstanceToString$(CActor\Inventory\Items[i])
						Pa$ = Pa$ + RCE_StrFromInt$(CActor\Inventory\Amounts[i], 2) + RCE_StrFromInt$(Handle(CopiedII), 4)
					EndIf
				Next
				If Len(Pa$) < 1000
					RCE_Send(Host, Actor\RNID, P_OpenTrading, "11" + Pa$, True)
				ElseIf Len(Pa$) < 2000
					SendQueued(Host, Actor\RNID, P_OpenTrading, "12" + Left$(Pa$, 999), True)
					SendQueued(Host, Actor\RNID, P_OpenTrading, "22" + Mid$(Pa$, 1000), True)
				Else
					SendQueued(Host, Actor\RNID, P_OpenTrading, "13" + Left$(Pa$, 999), True)
					SendQueued(Host, Actor\RNID, P_OpenTrading, "23" + Mid$(Pa$, 1000, 1000), True)
					SendQueued(Host, Actor\RNID, P_OpenTrading, "33" + Mid$(Pa$, 2000), True)
				EndIf
			EndIf
		EndIf
	EndIf
End Function

Function BVM_SETACTORGLOBAL(Param1%, Param2%, Param3$)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Actor\ScriptGlobals$[Param2%] = Param3$
	EndIf
End Function

Function BVM_ACTORGLOBAL$(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Result$ = Actor\ScriptGlobals$[Param2%]
	Else
		Result$ = ""
	EndIf
Return Result$
End Function

Function BVM_GIVEITEM(Param1%, Param2$, Param3%=1)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		ItemName$ = Upper$(Param2$)
		Amount = Param3%
		; Find the requested item
		For It.Item = Each Item
			If Upper$(It\Name$) = ItemName$
				; Give
				If Amount > 0
					; Check if Actor can use this slot
					If( ActorHasSlot(Actor\Actor, It\SlotType, It ) )
						; Human
						If Actor\RNID > 0
							; Create the item
							II.ItemInstance = CreateItemInstance(It)
							II\Assignment = Amount
							II\AssignTo = Actor
							; Ask client to specify a slot to put it in
							Pa$ = RCE_StrFromInt$(It\ID, 2) + RCE_StrFromInt$(II\Assignment, 2)
							RCE_Send(Host, Actor\RNID, P_InventoryUpdate, "G" + RCE_StrFromInt$(Handle(II), 4) + Pa$, True)
						; AI
						Else
							II.ItemInstance = CreateItemInstance(It)
							For i = 0 To Slots_Inventory
								If Actor\Inventory\Items[i] = Null Or (ItemInstancesIdentical(II, Actor\Inventory\Items[i]) And II\Item\Stackable = True And i >= SlotI_Backpack)
									If SlotsMatch(It, i)
										; Only put one item in this slot if it is an equipped slot
										ThisAmount = Amount
										If i < SlotI_Backpack Then ThisAmount = 1
									; Put in slot
										If Actor\Inventory\Items[i] <> Null
											FreeItemInstance(Actor\Inventory\Items[i])
										Else
											Actor\Inventory\Amounts[i] = 0
										EndIf
										Actor\Inventory\Items[i] = II
										Actor\Inventory\Amounts[i] = Actor\Inventory\Amounts[i] + ThisAmount

										; Visual stuff
										If i = SlotI_Weapon Or i = SlotI_Shield Or i = SlotI_Hat Or i = SlotI_Chest
											SendEquippedUpdate(Actor)
										EndIf

										; If all items have been placed, exit loop
										Amount = Amount - ThisAmount
										If Amount = 0 Then Exit
									EndIf
								EndIf
							Next
						EndIf
					EndIf
				; Take
				Else
					Amount = Abs(Amount)
					For i = 0 To Slots_Inventory
						If Actor\Inventory\Items[i] <> Null
							If Actor\Inventory\Items[i]\Item = It
								AmountTaken = 0

								; Delete item
								If Actor\Inventory\Amounts[i] <= Amount
									AmountTaken = Actor\Inventory\Amounts[i]
									Amount = Amount - Actor\Inventory\Amounts[i]
									FreeItemInstance(Actor\Inventory\Items[i])
									Actor\Inventory\Amounts[i] = 0
								Else
									Actor\Inventory\Amounts[i] = Actor\Inventory\Amounts[i] - Amount
									AmountTaken = Amount
									Amount = 0
								EndIf

								; Tell player if required
								If Actor\RNID > 0
									Pa$ = RCE_StrFromInt$(i, 1) + RCE_StrFromInt$(AmountTaken, 2)
									RCE_Send(Host, Actor\RNID, P_InventoryUpdate, "T" + Pa$, True)
								EndIf

								; Update equipment if required
								If i = SlotI_Weapon Or i = SlotI_Shield Or i = SlotI_Hat
									SendEquippedUpdate(Actor)
								EndIf

								If Amount = 0 Then Exit
							EndIf
						EndIf
					Next
				EndIf
				Exit
			EndIf
		Next
	EndIf
End Function

Function BVM_HASITEM%(Param1%, Param2$, Param3$ = 1)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		ItemName$ = Param2$
		Result% = InventoryHasItem(Actor\Inventory, ItemName$, Param3$)
	EndIf
Return Result%
End Function

Function BVM_BUBBLEOUTPUT(Param1%, Param2$, Param3%=255, Param4%=255, Param5%=255)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		If AInstance <> Null
			R = Param3%
			G = Param4%
			B = Param5%
			Pa$ = RCE_StrFromInt$(Actor\RuntimeID, 2) + Chr$(R) + Chr$(G) + Chr$(B) + Param2$
			A2.ActorInstance = AInstance\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_BubbleMessage, Pa$, True)
				A2 = A2\NextInZone
			Wend
		EndIf
	EndIf
End Function

Function BVM_OUTPUT(Param1%, Param2$, Param3%=0, Param4%=255, Param5%=255)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\RNID > 0
			Message$ = Param2$
			R = Param3%
			G = Param4%
			B = Param5%
			RCE_Send(Host, Actor\RNID, P_ChatMessage, Chr$(250) + Chr$(R) + Chr$(G) + Chr$(B) + Message$, True)
		EndIf
	EndIf
End Function

Function BVM_MYSQLQUERY$(Param1$)
	If MySQL = True Then Result$ = SQLQuery(hSQL, Param1$)
Return Result$
End Function

Function BVM_MYSQLNUMROWS%(Param1%)
	If MySQL = True Then Result% = SQLRowCount(Param1%)
Return Result%
End Function

Function BVM_MYSQLFETCHROW$(Param1%)
	If MySQL = True Then Result$ = SQLFetchRow(Param1%)
Return Result$
End Function

Function BVM_MYSQLGETVAR$(Param1%,Param2$)
	If MySQL = True Then Result$ = ReadSQLField(Param1%, Param2$)
	Return Result$
End Function

Function BVM_MYSQLFREEQUERY(Param1%)
	If MySQL = True Then FreeSQLQuery(Param1%)
End Function

Function BVM_MYSQLFREEROW(Param1%)
	If MySQL = True Then FreeSQLRow(Param1%)
End Function

Function BVM_SQLACCOUNTID%(Param1%)
Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If MySQL = True
			Result% = Actor\Account_ID
		EndIf
	Else
		WriteLog(MainLog, "Error: SQLAccountID Failed, No Valid Actor")
	EndIf
Return Result%
End Function

Function BVM_SQLACTORID%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If MySQL = True
			Result% = Actor\My_ID
		EndIf
	Else
		WriteLog(MainLog, "Error: SQLActorID Failed, No Valid Actor")
	EndIf
Return Result%
End Function

;------------------------------------------------------
;-Misc Setters and Getters 
;------------------------------------------------------

Function BVM_GETRUNTIMEID%(Param1%)
	AI.ActorInstance = Object.ActorInstance(Param1%)
	If AI <> Null
		Result% = AI\RunTimeID
	Else
		Result% = 0
	EndIf
Return Result
End Function

Function BVM_GETRNID%(Param1%)
	AI.ActorInstance = Object.ActorInstance(Param1%)
	If AI <> Null
		Result% = AI\RNID
	Else
		Result% = 0
	EndIf
Return Result
End Function

Function BVM_SETWAITING(x%)
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	SI\WaitResult$ = ""
	SI\Waiting = x%
End Function

Function BVM_SETWAITSPEAK(Param1%, Param2%)
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	Actor.ActorInstance = Object.ActorInstance(Param1)
	If Actor <> Null
		CActor.ActorInstance = Object.ActorInstance(Param2)
		If CActor <> Null
			PS.PausedScript = New PausedScript
			PS\S = SI
			PS\Reason = 4
			PS\ReasonActor = Actor
			PS\ReasonContextActor = CActor
		EndIf
	EndIf
End Function

Function BVM_SETWAITITEM(Param1%, Param2$, Param3%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	SI.ScriptInstance = Object.ScriptInstance(hSI%)
	If Actor <> Null
		PS.PausedScript = New PausedScript
		PS\S = SI
		PS\Reason = 3
		PS\ReasonActor = Actor
		PS\ReasonItem$ = Param2$
		PS\ReasonAmount = Param3%
	EndIf
End Function

Function BVM_SETWAITKILL(Param1%, Param2%, Param3%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	SI.ScriptInstance = Object.ScriptInstance(hSI%)
	If Actor <> Null
		KillActor = Param2%
		If ActorList(KillActor) <> Null
			PS.PausedScript = New PausedScript
			PS\S = SI
			PS\Reason = 2
			PS\ReasonActor = Actor
			PS\ReasonKillActor = ActorList(KillActor)
			PS\ReasonAmount = Param3%
		EndIf
	EndIf
End Function

Function BVM_SETWAITINFO(Param1%, Param2%)
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	SI\WaitHour% = Param1%
	SI\WaitMinute% = Param2%
End Function

Function BVM_SETWAITTIME(Param1%)
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	SI\WaitTime = Param1%
End Function

Function BVM_SETWAITSTART(Param1%)
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	SI\WaitStart = Param1%
End Function

Function BVM_SETWAITRESULT(PARAM1$)
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	SI\WaitResult$ = PARAM1$
End Function

Function BVM_GETWAITRESULT$()
	SI.ScriptInstance = Object.ScriptInstance(hSI)
Return SI\WaitResult$
End Function

Function BVM_SETSUPERGLOBAL(Param1%, Param2$)
	SuperGlobals$(Param1%) = Param2$
End Function

Function BVM_GETSUPERGLOBAL$(Param1%)
Return  SuperGlobals$(Param1%)
End Function

;-Dialog Helper Functions---------------------------
Function RCE_SendOpenDialog(Host%, ARNID%, CARuntimeID%, BackgroundTexID%, Title$)
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	SI\WaitResult$ = ""
	Pa$ = "N" + RCE_StrFromInt$(hSI, 4) + RCE_StrFromInt$(CARuntimeID, 2) + RCE_StrFromInt$(BackgroundTexID, 2) + Title$
	RCE_Send(Host, ARNID, P_Dialog, Pa$, True)
End Function

Function RCE_SendCloseDialog(Host%, ARNID%, dhandle%)
	Pa$ = "C" + RCE_StrFromInt$(dhandle)
	RCE_Send(Host, ARNID, P_Dialog, Pa$, True)
End Function

Function RCE_SendDialogOutput(Host%, ARNID%, Red%, Green%, Blue%, dhandle%, Message$)
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	SI\WaitResult$ = ""
	Pa$ = "T" + RCE_StrFromInt$(Red, 1) + RCE_StrFromInt$(Green, 1) + RCE_StrFromInt$(Blue, 1) + RCE_StrFromInt$(dhandle) + Message$
	RCE_Send(Host, ARNID, P_Dialog, Pa$, True)
End Function

Function RCE_SendDialogInput(Host%, ARNID%, dhandle%, Options$, Delim$ = ",")
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	SI\WaitResult$ = ""
	Pa$ = RCE_StrFromInt$(dhandle)
	For Opt = 1 To 9
		Option$ = SafeSplit$(Options$, Opt, Delim$)
		If Option$ = "" Then Exit
		Pa$ = Pa$ + RCE_StrFromInt$(Len(Option$), 1) + Option$
	Next
	RCE_Send(Host, ARNID, P_Dialog, "O" + Pa$, True)
End Function

Function RCE_SendInput(Host%, ARNID%, iType%, Title$, Prompt$)
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	SI\WaitResult$ = ""
	Pa$ = RCE_StrFromInt$(hSI, 4) + RCE_StrFromInt$(iType, 1) + RCE_StrFromInt$(Len(Title$), 2) + Title$ + Prompt$
	RCE_Send(Host, ARNID, P_ScriptInput, Pa$, True)
End Function

;-Progress Bar Helper Functions---------------------------

Function RCE_SendCreateProgressBar(Host%, ARNID%, R%, G%, B%, X#, Y#, W#, H#, Maximum%, Value%, Label$)
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	SI\WaitResult$ = ""
	Pa$ = RCE_StrFromInt$(R, 1) + RCE_StrFromInt$(G, 1) + RCE_StrFromInt$(B, 1)
	Pa$ = Pa$ + RCE_StrFromFloat$(X#) + RCE_StrFromFloat$(Y#) + RCE_StrFromFloat$(W#) + RCE_StrFromFloat$(H#)
	Pa$ = Pa$ + RCE_StrFromInt$(hSI%) + RCE_StrFromInt$(Maximum%, 2)
	Pa$ = Pa$ + RCE_StrFromInt$(Value%, 2) + Label$
	RCE_Send(Host, ARNID, P_ProgressBar, "C" + Pa$, True)
End Function

Function RCE_SendDeleteProgressBar(Host%, ARNID%, PBar%)
	RCE_Send(Host, ARNID, P_ProgressBar, "D" + RCE_StrFromInt$(PBar), True)
End Function

Function RCE_SendUpdateProgressBar(Host%, ARNID%, PBar%, Val%)
	RCE_Send(Host, ARNID, P_ProgressBar, "U" + RCE_StrFromInt$(PBar) + RCE_StrFromInt$(Val, 2), True)
End Function

;-Misc Functions---------------------------
; Goto and GotoIf replacement functions
Function BVM_GOTO(Param$)
	BVM_ScriptLog("The GoTo command is no longer supported")
End Function

Function BVM_GOTOIF(Param$)
	BVM_ScriptLog("The GoTo command is no longer supported")
End Function

;-Removes quotes from a string
Function BVM_DEQUOTE$(Param1$)
	Return Replace$(Param1$, Chr$(34), "")
End Function

;-Replace the Mod function from rcscript
Function BVM_MOD#(Param1#, Param2#)
	Result# = Param1 Mod Param2
	Return Result
End Function

Function BVM_REFRESHSCRIPTS()
	WriteLog(MainLog, "Refreshing scripts...")
	WriteLog(MainLog, "Halting running scripts...")
	For SI.ScriptInstance = Each ScriptInstance
		SI\Ended = True
	Next
	UpdateScripts()

	For SS.ScriptSource = Each ScriptSource
		BVM_ReleaseModule(SS\hModule)
		Delete SS
	Next

	WriteLog(MainLog, "Deleted all loaded scripts")
	Number = LoadScripts() : WriteLog(MainLog, "Loaded " + Str$(Number) + " scripts.")
	Number = CompileModules() : WriteLog(MainLog, "Compiled " + Str$(Number) + " modules.")
End Function

;-UDP Networking commands
Function BVM_CreateUDPStream%(Param1%=0)
	Port = Param1%
	If Port > 0
		Result% = CreateUDPStream(Port)
	Else
		Result% = CreateUDPStream()
	EndIf
Return Result%
End Function

Function BVM_CloseUDPStream(Param1%)
	CloseUDPStream(Param1%)
End Function

Function BVM_SendUDPMsg(Param1%, Param2%, Param3%)
	Port% = Param3%
	If Port > 0
		SendUDPMsg(Param1%, Param2%, Port%)
	Else
		SendUDPMsg(Param1%, Param2%)
	EndIf
End Function

Function BVM_RecvUDPMsg$(Param1%)
	Result$ = RecvUDPMsg(Param1%)
Return Result$
End Function

Function BVM_UDPStreamIP%(Param1%)
	Result% = UDPStreamIP(Param1%)
Return Result%
End Function

Function BVM_UDPStreamPort%(Param1%)
	Result% = UDPStreamPort(Param1%)
Return Result%
End Function

Function BVM_UDPMsgIP%(Param1%)
	Result% = UDPMsgIP(Param1%)
Return Result%
End Function

Function BVM_UDPMsgPort%(Param1%)
	Result% = UDPMsgPort(Param1%)
Return Result%
End Function

Function BVM_UDPTimeouts(Param1%)
	UDPTimeouts(Param1%)
End Function

Function BVM_CountHostIPs%(Param1%)
	Result% = CountHostIPs(Param1%)
Return Result%
End Function

Function BVM_HostIP%(Param1%)
	Result% = HostIP(Param1%)
Return Result%
End Function

Function BVM_DottedIP$(Param1%)
	Result$ = DottedIP$(Param1%)
Return Result$
End Function