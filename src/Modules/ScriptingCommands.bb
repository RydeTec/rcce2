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
	; Propagate the caller's Privileged flag to the spawned script.
	; Previously hard-coded to 0, which (a) silently neutered GM
	; scripts that used ThreadExecute to call privileged helpers
	; and (b) left an obvious refactor trap where someone would
	; eventually add a `Privileged` arg and get the propagation wrong.
	Local CurrentSI.ScriptInstance = Object.ScriptInstance(hSI)
	Local CallerPriv% = 0
	If CurrentSI <> Null Then CallerPriv = CurrentSI\Privileged
	ThreadScript(Name$, Func$, AI, AIContext, Param$, CallerPriv)
End Function

Function BVM_SAVESTATE()
	If Not BVM_RequirePrivileged() Then Return
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

; Returns True iff the currently-executing script was spawned via a code
; path that has already verified the caller is a GM. Used to gate
; admin-only BVM commands (Ban/Kick/Warp/GiveItem/SetGold/SetActorLevel).
;
; Without this gate, any NPC's Examine / Trade / RightClick / ItemUse
; script ran with the clicker's actor handle and could invoke BanPlayer
; on the clicker. The only GM check in the entire scripting surface was
; the chat `/script` command itself.
Function BVM_RequirePrivileged%()
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	If SI = Null Then Return False
	If SI\Privileged <> 0 Then Return True
	BVM_ScriptLog("Privileged BVM call refused from non-privileged script: " + SI\Name)
	Return False
End Function

; Allow the call if the script is privileged OR the target is the
; script's own actor / context. Use this for state-mutating BVM
; commands that take an actor handle but are safe when the target
; is the script's own actor (e.g. an NPC's own movement, an actor's
; own attribute change). Without this distinction, the privilege
; gate either lets non-privileged NPC scripts mutate arbitrary
; actors (current behaviour for several commands) or breaks every
; NPC's ability to move itself.
Function BVM_RequireSelfOrPrivileged%(Param1%)
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	If SI = Null Then Return False
	If SI\Privileged <> 0 Then Return True
	If Param1% <> 0 And (Param1% = SI\AI Or Param1% = SI\AIContext) Then Return True
	BVM_ScriptLog("BVM call refused: target is neither script's actor nor context (" + SI\Name + ")")
	Return False
End Function

Function BVM_BANPLAYER(Param%)
	If Not BVM_RequirePrivileged() Then Return
	Actor.ActorInstance = Object.ActorInstance(Param)
	If Actor <> Null
		A.Account = Object.Account(Actor\Account)
		If A <> Null Then A\IsBanned = 1
	EndIf
End Function

Function BVM_KICKPLAYER(Param%)
	If Not BVM_RequirePrivileged() Then Return
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
		; TriggerScript$/TriggerSize#/etc. are Dim'd 0..149 on AreaInstance.
		; Reject any out-of-range index from the script before indexing.
		If TriggerID >= 0 And TriggerID <= 149
			AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
			If AInstance <> Null
				If Len(AInstance\Area\TriggerScript$[TriggerID]) > 0
					Size# = AInstance\Area\TriggerSize#[TriggerID] * AInstance\Area\TriggerSize#[TriggerID]
					DistX# = Abs(Actor\X# - AInstance\Area\TriggerX#[TriggerID])
					DistY# = Abs(Actor\Y# - AInstance\Area\TriggerY#[TriggerID])
					DistZ# = Abs(Actor\Z# - AInstance\Area\TriggerZ#[TriggerID])
					Dist# = (DistX# * DistX#) + (DistY# * DistY#) + (DistZ# * DistZ#)
					If Dist# < Size# Then Result% = 1
				EndIf
			EndIf
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
				; Bound the script-supplied Instance index. Instances
				; is Dim'd 0..99; a wild value would read past the array
				; (Blitz3D has no runtime Dim bounds check). Return 0 on
				; out-of-range -- the caller's count comparison naturally
				; handles "no actors found".
				If Instance >= 0 And Instance <= 99
					AInstance.AreaInstance = Ar\Instances[Instance]
					If AInstance <> Null
						A2.ActorInstance = AInstance\FirstInZone
						While A2 <> Null
							Count = Count + 1
							A2 = A2\NextInZone
						Wend
					EndIf
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
				; Same bounds guard as BVM_ACTORSINZONE above.
				If Instance >= 0 And Instance <= 99
					AInstance.AreaInstance = Ar\Instances[Instance]
					If AInstance <> Null
						A2.ActorInstance = AInstance\FirstInZone
						While A2 <> Null
							If A2\RNID > 0 Then Count = Count + 1
								A2 = A2\NextInZone
						Wend
					EndIf
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
		; Bound the script-supplied Instance index. Returning 0 on
		; out-of-range matches "instance does not exist".
		If Instance < 0 Or Instance > 99 Then Return 0
		If Zone\Instances[Instance] <> Null Then Result% = 1
	EndIf
Return Result%
End Function

Function BVM_CREATEZONEINSTANCE%(Param1$, Instance%=0)
	Zone.Area = FindArea(Param1$)
	If Zone <> Null
		; Script requests a specific ID. Bound to the (Dim 0..99)
		; Instances array; out-of-range returns 0 ("instance not
		; created") rather than a Dim OOB write.
		If Instance > 0 And Instance <= 99
			If Zone\Instances[Instance] = Null
				ServerCreateAreaInstance(Zone, Instance)
				Result% = Instance
			EndIf
		ElseIf Instance = 0
			; Use first free ID
			For i = 1 To 99
				If Zone\Instances[i] = Null
					ServerCreateAreaInstance(Zone, i)
					Result% = i
					Exit
				EndIf
			Next
		; Instance > 99 or negative: silently return 0 (caller's
		; if-instance-was-created check fires the same "no slot"
		; branch).
		EndIf
	Else
		WriteLog(MainLog, "Instance can not be created, Zone " + Param1$ + " does not exist.")
	EndIf
Return Result%
End Function

Function BVM_REMOVEZONEINSTANCE(Param1$, Instance%)
	; Admin-only: tears down an entire zone instance -- moves every
	; player out, frees every AI ActorInstance, deletes every dropped
	; item, removes the on-disk ownership file. Without this gate any
	; NPC's Examine / Trade / RightClick script could nuke an entire
	; zone the clicker happens to be in. Equivalent-effect peer:
	; there is no single gated BVM that does this, but every primitive
	; this composes (KillActor, FreeActorInstance, file deletion) is
	; gated or unreachable from a non-priv context. Closes the gap.
	If Not BVM_RequirePrivileged() Then Return
	Zone.Area = FindArea(Param1$)
	If Zone <> Null
		; Bound the script-supplied Instance index against Dim 0..99.
		If Instance > 0 And Instance <= 99
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
			; Delete dropped items. After-cursor walk: the body Deletes
			; D, which would corrupt the For-Each cursor on the next
			; iteration. Fires from BVM_REMOVEZONEINSTANCE -- a script
			; cleanup of a zone with multiple dropped items would
			; either skip past the corruption point (orphan items
			; leak) or crash the server on the freed-next-pointer
			; deref. Documented in CLAUDE.md (#247).
				Local Drz.DroppedItem = First DroppedItem
				Local DrzNext.DroppedItem = Null
				While Drz <> Null
					DrzNext = After Drz
					AInstance.AreaInstance = Object.AreaInstance(Drz\ServerHandle)
					If AInstance = Zone\Instances[Instance]
						FreeItemInstance(Drz\Item)
						Delete(Drz)
					EndIf
					Drz = DrzNext
				Wend
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
	; Without this gate any NPC Examine / Trade / RightClick script
	; could instantly kill any actor whose handle it could scan via
	; BVM_NEXTACTOR.
	If Not BVM_RequirePrivileged() Then Return
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Actor\Attributes\Value[HealthStat] = 0
		Killer.ActorInstance = Object.ActorInstance(Param2%)
		KillActor(Actor, Killer)
	EndIf
End Function

Function BVM_CHANGEACTOR(Param1%, Param2%)
	If Not BVM_RequirePrivileged() Then Return
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
				; Tell other players in the area. Skip the broadcast loop
				; if the actor's area lookup fails (mid-warp / freed zone)
				; -- the appearance change still applies to the actor's
				; in-memory state; only the network broadcast is dropped.
				Pa$ = "C" + RCE_StrFromInt$(Actor\RuntimeID, 2) + RCE_StrFromInt$(ID, 2)
				AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
				If AInstance <> Null
					A2.ActorInstance = AInstance\FirstInZone
					While A2 <> Null
						If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_AppearanceUpdate, Pa$, True)
						A2 = A2\NextInZone
					Wend
				EndIf
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
			; Sanitise drop coords -- NaN/Inf poisons spatial code on
			; every receiver that walks DroppedItem positions. Mirror
			; the P_InventoryUpdate "D" flow (ServerNet.bb ~1467) which
			; already clamps before persisting.
			D\X# = ClampWorldCoord#(Param4#)
			D\Y# = ClampWorldCoord#(Param5#)
			D\Z# = ClampWorldCoord#(Param6#)
			; Bound the script-supplied Instance index. Instances is
			; Dim'd 0..99; a wild value would walk past the array on the
			; first probe below. Clamp out-of-range to 0 (the default
			; instance) and fall through to the existing "instance is
			; Null" path which auto-falls back to 0 anyway.
			Instance = Param7%
			If Instance < 0 Or Instance > 99 Then Instance = 0
			If Zone\Instances[Instance] = Null
				Instance = 0
				WriteLog(MainLog, "BVM_SPAWNITEM: requested instance does not exist in " + Zone\Name$ + ", spawning in instance 0")
			EndIf
			; If even instance 0 is uninitialised, give up cleanly --
			; can't broadcast into a non-existent zone.
			If Zone\Instances[Instance] = Null
				WriteLog(MainLog, "BVM_SPAWNITEM: instance 0 also missing for " + Zone\Name$ + ", dropping spawn")
				FreeItemInstance(D\Item)
				Delete D
				Return
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
	; Cosmetic appearance setter -- broadcasts P_AppearanceUpdate to
	; every player in the area. Non-priv clicker exploit:
	; SetActorGender(anotherPlayer, ...) forces an appearance flip
	; on a victim, which is griefing rather than mechanical brick,
	; but still unwanted. Same threat shape as the SET-name/tag pair.
	; Quest reward scripts (race/gender change tokens) already run
	; privileged. Per-Actor body/head clamping below is unchanged.
	If Not BVM_RequirePrivileged() Then Return
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		; Param2% is 1-based on the script side (1=male, 2=female).
		; Clamp the resulting 0-based gender to 0..1 before storing so a
		; script can't drive Actor\Gender to arbitrary values that then
		; flow into Chr$()/array indexing downstream.
		Local NewGender = Param2% - 1
		If NewGender < 0 Or NewGender > 1 Then NewGender = 0
		Actor\Gender = NewGender
		If Actor\Actor\Genders = 2 And Actor\Gender <> 1 Then Actor\Gender = 1
		If (Actor\Actor\Genders = 1 Or Actor\Actor\Genders = 3) And Actor\Gender <> 0 Then Actor\Gender = 0
		Pa$ = "G" + RCE_StrFromInt$(Actor\RuntimeID, 2) + Chr$(Actor\Gender)
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		If AInstance <> Null
			A2.ActorInstance = AInstance\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_AppearanceUpdate, Pa$, True)
					A2 = A2\NextInZone
			Wend
		EndIf
	EndIf
End Function

Function BVM_ACTORBEARD%(Param1%)
	; Typo: was Param% (undeclared, silently 0) instead of Param1%.
	; Every call resolved Object.ActorInstance(0) -> Null -> Result=0.
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Actor\Beard + 1
Return Result%
End Function

Function BVM_SETACTORBEARD(Param1%, Param2%)
	; Cosmetic appearance setter -- same threat shape as
	; SETACTORGENDER. Clicker-griefing only, but consistent gating
	; across the appearance cluster.
	If Not BVM_RequirePrivileged() Then Return
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\Gender = 0
			; Bound Param2% to the 5-slot BeardIDs array. Param2% is
			; 1-based on the script side; clamp the resulting 0-based
			; index to 0..4 before storing.
			Local NewBeard = Param2% - 1
			If NewBeard < 0 Or NewBeard > 4 Then NewBeard = 0
			Actor\Beard = NewBeard
			Pa$ = "D" + RCE_StrFromInt$(Actor\RuntimeID, 2) + Chr$(Actor\Beard)
			AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
			If AInstance <> Null
				A2.ActorInstance = AInstance\FirstInZone
				While A2 <> Null
					If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_AppearanceUpdate, Pa$, True)
					A2 = A2\NextInZone
				Wend
			EndIf
		EndIf
	EndIf
End Function

Function BVM_ACTORHAIR%(Param1%)
	; Typo: was Param% (undeclared, silently 0) instead of Param1%.
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Actor\Hair + 1
Return Result%
End Function

Function BVM_SETACTORHAIR(Param1%, Param2%)
	; Cosmetic appearance setter -- same gating rationale as
	; SETACTORGENDER / SETACTORBEARD.
	If Not BVM_RequirePrivileged() Then Return
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\Gender = 0
			; Bound Param2% to the 5-slot Hair-IDs arrays (same shape
			; as SETACTORBEARD). 1-based -> 0-based clamp.
			Local NewHair = Param2% - 1
			If NewHair < 0 Or NewHair > 4 Then NewHair = 0
			Actor\Hair = NewHair
			Pa$ = "D" + RCE_StrFromInt$(Actor\RuntimeID, 2) + Chr$(Actor\Hair)
			AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
			If AInstance <> Null
				A2.ActorInstance = AInstance\FirstInZone
				While A2 <> Null
					If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_AppearanceUpdate, Pa$, True)
					A2 = A2\NextInZone
				Wend
			EndIf
		EndIf
	EndIf
End Function

Function BVM_ACTORCALLFORHELP(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then AICallForHelp(Actor)
End Function

Function BVM_SETACTORAISTATE(Param1%, Param2%)
	; Gated. Clicker brick risk: SetActorAIState(SomeGuard, AI_Wait)
	; from a non-priv NPC right-click script disables a hostile guard's
	; AI -- the player who clicks the NPC walks away with a free
	; sandbag. Pre-PR-#329 this stayed ungated because shipped content
	; (AOE Damage Spell Template.rsl) called it from a non-priv
	; spell-cast spawn to make targets aggressive on impact -- gating
	; would have no-op'd the spell.
	;
	; Closed by the privileged-script allowlist (Scripting.bb's
	; LoadPrivilegedScripts + the elevation point in ThreadScript). The
	; AOE template's name is in Data\Server Data\Privileged Scripts.dat;
	; spell-cast spawns that target it now get the elevation. Other
	; non-priv callers still refuse, closing the brick vector.
	If Not BVM_RequirePrivileged() Then Return
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
	; Gated. Clicker-brick risk: SetActorTarget(SomeGuard,
	; anotherPlayer) from a non-priv NPC right-click script weaponizes
	; the guard against an arbitrary victim. Pre-PR-#329 this stayed
	; ungated because shipped content needed it from non-priv spawns:
	;   - /Assist chat command (In-game Commands.rsl) targets the
	;     assisted player's current target.
	;   - AOE Damage Spell Template.rsl targets the player on spell
	;     impact (aggro-pull).
	;
	; Closed by the privileged-script allowlist. Both script names are
	; in Data\Server Data\Privileged Scripts.dat; their ThreadScript
	; spawns now get the elevation. Other non-priv callers refuse,
	; closing the weaponization vector.
	;
	; The existing partial safety (Aggressiveness=3 non-combatants and
	; friendly-faction targets rejected) is preserved as defense-in-
	; depth -- privileged callers still hit those checks.
	If Not BVM_RequirePrivileged() Then Return
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
	If Not BVM_RequireSelfOrPrivileged(Param1%) Then Return
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		; Sanitise -- NaN destination poisons the AI patrol move
		; vector (XDist/ZDist) and propagates to clients via every
		; broadcast that quotes Actor\DestX#/DestZ#.
		Actor\DestX# = ClampWorldCoord#(Param2#)
		Actor\DestZ# = ClampWorldCoord#(Param3#)
	EndIf
End Function

Function BVM_GIVEKILLXP(Param1%, Param2%)
	; Equivalent-effect bypass of gated BVM_SETACTORLEVEL: a flood of
	; XP triggers the LevelUp script path (GiveXP -> ThreadScript
	; "LevelUp") which can advance Level arbitrarily. Without this
	; gate, any NPC's Examine / Trade / RightClick script could grant
	; or deny arbitrary progression to the clicker. Match the gate on
	; BVM_GIVEXP below and on BVM_SETACTORLEVEL.
	If Not BVM_RequirePrivileged() Then Return
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
	; ActorList is Dim'd 0..65535. Bound-check both sides before the
	; Null check -- Blitz3D does not bounds-check Dim accesses, so a
	; script-supplied ID of 99999 or -1 would read off the end of the
	; array and crash the server. Same shape as P_KillActor handler.
	If ID >= 0 And ID <= 65535
		If ActorList(ID) <> Null
			; Find zone
			Name$ = Upper$(Param2$)
			For Ar.Area = Each Area
				If Upper$(Ar\Name$) = Name$
					AI.ActorInstance = CreateActorInstance.ActorInstance(ActorList(ID))
					AI\RNID = -1
					AssignRuntimeID(AI)
					Instance = Param8%
					; Sanitise spawn coords -- SetArea writes them directly
					; to A\X#/Y#/Z# which then broadcast on every update.
					SetArea(AI, Ar, Instance, -1, -1, ClampWorldCoord#(Param3#), ClampWorldCoord#(Param4#), ClampWorldCoord#(Param5#))
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
	; Null-S guard: hSI is the per-call script-instance handle the
	; VM populates before invoking a BVM_* command. If the command
	; runs without a live ScriptInstance (BVM reentry, host-side
	; invocation, a still-running command on a script that was
	; just FreeScriptInstance'd), Object.ScriptInstance(0) returns
	; Null and the bare S\Param$ deref faults.
	Local S.ScriptInstance = Object.ScriptInstance(hSI)
	If S = Null Then Return ""
	Local Result$ = ""
	If S\Param$ <> ""
		Result$ = SafeSplit(S\Param$, Param1%, ",")
	EndIf
	Return Result$
End Function

Function BVM_ROTATEACTOR(Param1%, Param2#)
	If Not BVM_RequireSelfOrPrivileged(Param1%) Then Return
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		; ClampSaneFloat catches NaN/Inf/extreme magnitudes -- a
		; script-supplied NaN yaw poisons rotation matrices on
		; every receiver.
		Actor\Yaw# = ClampSaneFloat#(Param2#)
		Pa$ = "R" + RCE_StrFromInt$(Actor\RuntimeID, 2) + RCE_StrFromFloat$(Actor\Yaw#)
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		If AInstance <> Null
			A2.ActorInstance = AInstance\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_RepositionActor, Pa$, True)
				A2 = A2\NextInZone
			Wend
		EndIf
	EndIf
End Function

Function BVM_MOVEACTOR(Param1%, Param2#, Param3#, Param4#, Param5%=0, Param6%=0)
	If Not BVM_RequireSelfOrPrivileged(Param1%) Then Return
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		; Sanitise positions before they're persisted into the actor
		; record and broadcast. A script supplying NaN/Inf would
		; poison every receiving client's spatial code (collision,
		; LOD culling, EntityDistance#). Mirrors the P_InventoryUpdate
		; "D" drop-item flow (ServerNet.bb ~1467).
		Actor\X# = ClampWorldCoord#(Param2#)
		Actor\Y# = ClampWorldCoord#(Param3#)
		Actor\Z# = ClampWorldCoord#(Param4#)
		Actor\DestX# = Actor\X#
		Actor\DestZ# = Actor\Z#
		Pa$ = "M" + RCE_StrFromInt$(Actor\RuntimeID, 2) + RCE_StrFromFloat$(Actor\X#) + RCE_StrFromFloat$(Actor\Y#) + RCE_StrFromFloat$(Actor\Z#)
		Pa$ = Pa$ + RCE_StrFromInt$(Param5%, 1) + RCE_StrFromInt$(Param6%, 1)
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		If AInstance <> Null
			A2.ActorInstance = AInstance\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_RepositionActor, Pa$, True)
				A2 = A2\NextInZone
			Wend
		EndIf
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
		If AInstance <> Null
			A2.ActorInstance = AInstance\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_FloatingNumber, Pa$, True)
				A2 = A2\NextInZone
			Wend
		EndIf
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
	; ItemHealth is the durability field; zeroing it bricks a player's
	; equipped weapon / armour on next use (Items.bb breaks items at
	; ItemHealth <= 0). A non-priv Examine / Trade / RightClick script
	; could iterate the clicker's Inventory\Items[] and zero the
	; ItemHealth on each, gutting all gear in one click. Note that
	; Param1 is an ItemInstance handle (not an ActorInstance), so the
	; self-or-priv shortcut doesn't apply -- there is no SI\AI for an
	; item. RequirePrivileged is the only sensible gate. Quest reward
	; scripts that legitimately repair / damage items run privileged.
	If Not BVM_RequirePrivileged() Then Return
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
	; The function-body guard `If Actor\RNID = -1` restricts Param1 to
	; NPCs (a clicker can't make a player a pet), but Param2 (the new
	; leader) can be any actor handle including the clicker itself --
	; so a non-priv Examine / Trade / RightClick script could call
	; SetLeader(SomeWorldGuard, clicker) to recruit world NPCs as
	; private pets. Guards belong to the world, not whoever clicks an
	; NPC. Quest reward scripts that legitimately bind pets already
	; run privileged (Privileged=1 spawn).
	If Not BVM_RequirePrivileged() Then Return
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\RNID = -1
			; Remove current leader. SlaveUnlink maintains the
			; FirstSlave chain + NumberOfSlaves on the old leader.
			If Actor\Leader <> Null
				SlaveUnlink(Actor)
				Actor\AIMode = AI_Wait
			EndIf
			; Set new one, if any. SlaveLink does the symmetric
			; insert into Leader\FirstSlave + NumberOfSlaves increment.
			Leader.ActorInstance = Object.ActorInstance(Param2%)
			If Leader <> Null
				SlaveLink(Leader, Actor)
				; Make sure it no longer belongs to any spawn point.
				; Skip the spawn-count decrement if the actor's area
				; lookup is Null (mid-warp / freed zone) -- the counter
				; is already orphaned in that case.
				If Actor\SourceSP > -1
					AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
					If AInstance <> Null
						AInstance\Spawned[Actor\SourceSP] = AInstance\Spawned[Actor\SourceSP] - 1
					EndIf
					Actor\SourceSP = -1
				EndIf
				Actor\AIMode = AI_Pet
			; No leader!
			Else
				; Assign to first available waypoint. If the actor's
				; area is gone, fall through to the kill path -- there's
				; no zone to patrol in.
				AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
				Found = False
				If AInstance <> Null And AInstance\Area <> Null
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
				EndIf
				; Die if no waypoint available (or area is gone)
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
	; Bug fix: previously returned Actor\X# (current position), making
	; this command a duplicate of BVM_ACTORX. Mirror BVM_SETACTORDESTINATION
	; which writes to DestX#/DestZ#.
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result# = Actor\DestX#
Return Result#
End Function

Function BVM_ACTORDESTINATIONZ#(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result# = Actor\DestZ#
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

; DEAD-API: BVM_SETOWNER permanently disabled. The underlying
; OwnedScenery type was removed from ServerAreas.bb; the public
; contract entry in RC_Standard_Invoker.bb (grep "DEAD-API") stays
; alive for opcode stability (removing it would renumber every BVM
; alphabetically after SCENERYOWNER/SETOWNER and break the fixed-Case
; dispatch). Dispatch case for SETOWNER (Case 530) is a silent no-op.
; Sibling BVM_SCENERYOWNER below has the same treatment but with a
; 0-sentinel push to keep its return-value caller stack-balanced.
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

; DEAD-API: BVM_SCENERYOWNER permanently disabled. See the audit
; comment above BVM_SETOWNER for the OwnedScenery feature removal and
; the opcode-stability rationale. The dispatch case for SCENERYOWNER
; (Case 501 in RC_Standard_Invoker.bb) now pushes 0 (sentinel
; "no owner") so the caller's stack stays balanced -- before that fix,
; the case popped 3 args and pushed nothing, corrupting every
; subsequent BVM operation in the calling expression.
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
	; Cosmetic appearance setter -- same gating rationale as the
	; rest of the SET_ACTOR_(GENDER|BEARD|HAIR|FACE|CLOTHES) cluster.
	If Not BVM_RequirePrivileged() Then Return
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		; Bound Param2% to the 5-slot Body-IDs arrays. 1-based -> 0-based clamp.
		Local NewBody = Param2% - 1
		If NewBody < 0 Or NewBody > 4 Then NewBody = 0
		Actor\BodyTex = NewBody
		Pa$ = "B" + RCE_StrFromInt$(Actor\RuntimeID, 2) + Chr$(Actor\BodyTex)
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		If AInstance <> Null
			A2.ActorInstance = AInstance\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_AppearanceUpdate, Pa$, True)
				A2 = A2\NextInZone
			Wend
		EndIf
	EndIf
End Function

Function BVM_ACTORFACE%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Actor\FaceTex + 1
Return Result%
End Function

Function BVM_SETACTORFACE(Param1%, Param2%)
	; Cosmetic appearance setter -- same gating rationale as the
	; rest of the SET_ACTOR_(GENDER|BEARD|HAIR|FACE|CLOTHES) cluster.
	If Not BVM_RequirePrivileged() Then Return
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		; Bound Param2% to the 5-slot Face-IDs arrays. 1-based -> 0-based clamp.
		Local NewFace = Param2% - 1
		If NewFace < 0 Or NewFace > 4 Then NewFace = 0
		Actor\FaceTex = NewFace
		Pa$ = "F" + RCE_StrFromInt$(Actor\RuntimeID, 2) + Chr$(Actor\FaceTex)
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		If AInstance <> Null
			A2.ActorInstance = AInstance\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_AppearanceUpdate, Pa$, True)
				A2 = A2\NextInZone
			Wend
		EndIf
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
		; Param2 is a raw script-supplied int. Items is Field [Slots_Inventory];
		; bound the computed slot before indexing, mirroring BVM_BACKPACKCOUNT.
		; Without it an out-of-range slot is an OOB Field read whose garbage flows
		; into Handle() -> server crash / type-confused handle. Out of range => 0
		; ("no item"), the sentinel callers already expect.
		If SlotI_Backpack + Num >= SlotI_Backpack And SlotI_Backpack + Num <= Slots_Inventory
			Result% = Handle(Actor\Inventory\Items[SlotI_Backpack + Num])
		EndIf
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
		; Bound the script-supplied ring slot (8..11) before indexing Items[].
		If SlotI_Ring1 + Num >= SlotI_Ring1 And SlotI_Ring1 + Num <= SlotI_Ring4
			Result% = Handle(Actor\Inventory\Items[SlotI_Ring1 + Num])
		EndIf
	EndIf
Return Result%
End Function

Function BVM_ACTORAMULET%(Param1%, Param2%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Num = Param2% - 1
		; Bound the script-supplied amulet slot (12..13) before indexing Items[].
		If SlotI_Amulet1 + Num >= SlotI_Amulet1 And SlotI_Amulet1 + Num <= SlotI_Amulet2
			Result% = Handle(Actor\Inventory\Items[SlotI_Amulet1 + Num])
		EndIf
	EndIf
Return Result%
End Function

Function BVM_ACTORGROUP%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Actor\TeamID
Return Result%
End Function

Function BVM_SETACTORGROUP(Param1%, Param2%)
	; TeamID is the team / party / faction identifier consumed by chat
	; routing (`/g` guild chat at ServerNet.bb's chat dispatch keys off
	; `A2\TeamID = AI\TeamID`) and combat friendly-fire / aggression
	; gating. Flipping TeamID via a clicker script lets a non-priv NPC
	; reassign the clicker's team -- friendly-fire flip, faction griefing,
	; guild-chat exfiltration vector ("listen in on this team's chat by
	; flipping a target into it"). The fix path was deferred at PR #311
	; pending an audit of shipped content scripts in `data/Server Data/
	; Scripts/`; PR #325's recon confirmed ZERO callers (no grep hits in
	; data/), so a full RequirePrivileged gate is safe to land without
	; content rewrites. Sibling-asymmetry with the 12 already-gated
	; mutators in this file; see CLAUDE.md "Pairs to keep in lockstep".
	If Not BVM_RequirePrivileged() Then Return
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Actor\TeamID = Param2%
End Function

Function BVM_FIREPROJECTILE(Param1%, Param2%, Param3$)
	; Source actor must be the script's own actor (NPCs can fire from
	; themselves) or the script must be privileged. Otherwise any NPC
	; script could fire any projectile from any actor at any target.
	If Not BVM_RequireSelfOrPrivileged(Param1%) Then Return
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

; Helper: returns True if SpellsList(SpellID) exists and its upper-cased
; Name$ matches MatchUpper$. Used to guard the BVM_ABILITY*/ABILITYLEVEL
; family against stale character KnownSpells slots -- the SpellsList
; slot can be Null if a spell was deleted from Spells.dat between
; sessions, and the previous unguarded `Upper$(SpellsList(...)\Name$)`
; check crashed the server on the first iteration that hit a stale slot.
Function SpellNameMatches%(SpellID, MatchUpper$)
	If SpellID < 0 Or SpellID > 65534 Then Return False
	If SpellsList(SpellID) = Null Then Return False
	If Upper$(SpellsList(SpellID)\Name$) = MatchUpper$ Then Return True
	Return False
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
				If SpellNameMatches%(Actor\KnownSpells[i], SpellName$) Then Known = True : Exit
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
				If SpellNameMatches%(Actor\KnownSpells[i], SpellName$) Then DeleteSpell(Actor, i)
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
				If SpellNameMatches%(Actor\KnownSpells[i], SpellName$) Then Result% = 1 : Exit
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
				If SpellNameMatches%(ID, SpellName$) Then Result% = 1 : Exit
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
				If SpellNameMatches%(Actor\KnownSpells[i], SpellName$) Then Result% = Actor\SpellLevels[i] : Exit
			EndIf
		Next
	EndIf
Return Result%
End Function

Function BVM_SETABILITYLEVEL(Param1%, Param2$, Param3%)
	; SpellLevels[] gates ability damage / healing / utility scaling.
	; SetAbilityLevel(clicker, "<spell>", 0) zeros out the chosen
	; ability; combined with iteration over the spell list it bricks
	; the player's entire combat toolkit. Same clicker brick-vector
	; class as SETMAXATTRIBUTE / SETREPUTATION. Quest reward scripts
	; that legitimately grant ability levels already run privileged.
	If Not BVM_RequirePrivileged() Then Return
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		SpellName$ = Upper$(Param2$)
		Lvl = Param3%
		For i = 0 To 999
			If Actor\SpellLevels[i] > 0
				If SpellNameMatches%(Actor\KnownSpells[i], SpellName$)
					Actor\SpellLevels[i] = Lvl
					If Actor\RNID > 0
						; SpellNameMatches already verified the slot is non-Null;
						; re-grab the Spell handle so we can pull its current name
						; for the broadcast.
						Local Sp.Spell = SpellsList(Actor\KnownSpells[i])
						Pa$ = RCE_StrFromInt$(Lvl, 4) + Sp\Name$
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
		; Sanitise animation speed -- NaN broadcast to every receiving
		; client locks up the animation timer for that actor on each
		; receiver. ClampSaneFloat tolerates any legitimate playback
		; rate while rejecting NaN/Inf/extreme magnitudes.
		Local AnimSpeed# = ClampSaneFloat#(Param3#)
		Pa$ = RCE_StrFromInt$(Actor\RuntimeID, 2) + RCE_StrFromInt$(Param4%, 1)
		Pa$ = Pa$ + RCE_StrFromFloat$(AnimSpeed#) + Param2$
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		If AInstance <> Null
			A2.ActorInstance = AInstance\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_AnimateActor, Pa$, True)
				A2 = A2\NextInZone
			Wend
		EndIf
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
			If AInstance <> Null
				A2.ActorInstance = AInstance\FirstInZone
				While A2 <> Null
					If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_Music, Pa$, True)
					A2 = A2\NextInZone
				Wend
			EndIf
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
			If AInstance <> Null
				A2.ActorInstance = AInstance\FirstInZone
				While A2 <> Null
					If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_Sound, Pa$, True)
					A2 = A2\NextInZone
				Wend
			EndIf
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
		If AInstance <> Null
			A2.ActorInstance = AInstance\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_Speech, Pa$, True)
				A2 = A2\NextInZone
			Wend
		EndIf
	EndIf
End Function

Function BVM_CREATEEMITTER(Param1%, Param2$, Param3%, Param4%, Param5#=0, Param6#=0, Param7#=0, Param8%=0)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	S.ScriptInstance = Object.ScriptInstance(hSI%)
	If Actor <> Null Then RuntimeID = Actor\RuntimeID Else RuntimeID = 0
	Name$ = Param2$
	TexID = Param3%
	Time = Param4%
	; Sanitise offset floats -- NaN broadcast to receiving clients
	; poisons emitter positioning on each receiver. Emitter offsets
	; are actor-relative and usually small; ClampSaneFloat is the
	; right tool (ClampWorldCoord would also work but is sized for
	; world-space).
	OffsetX# = ClampSaneFloat#(Param5#)
	OffsetY# = ClampSaneFloat#(Param6#)
	OffsetZ# = ClampSaneFloat#(Param7#)
	Pa$ = RCE_StrFromInt$(TexID, 2) + RCE_StrFromInt$(Time, 4) + RCE_StrFromInt(RuntimeID, 2)
	Pa$ = Pa$ + RCE_StrFromFloat$(OffsetX#) + RCE_StrFromFloat$(OffsetY#) + RCE_StrFromFloat$(OffsetZ#) + Name$
	Actor2.ActorInstance = Object.ActorInstance(Param8%)
	; Display to all actors in zone
	If Actor2 = Null
		; Send to actors in the same zone as specified actor (or the zone of the script actor if none specified)
		If Actor = Null
			If S = Null Then Return
			Actor = Object.ActorInstance(S\AI)
			If Actor = Null Then Return
		EndIf
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		If AInstance <> Null
			A2.ActorInstance = AInstance\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_CreateEmitter, Pa$, True)
				A2 = A2\NextInZone
			Wend
		EndIf
	; Display to specific actor
	Else
		RCE_Send(Host, Actor2\RNID, P_CreateEmitter, Pa$, True)
	EndIf
End Function

Function BVM_SETFACTIONRATING(Param1%, Param2$, Param3%)
	; Faction rating drives the combat-engagement gate in GameServer.bb
	; (an unprivileged script that flips a guard's faction rating > 150
	; against the player makes the guard friendly). Restrict the
	; mutator the same way Round 5's privilege sweep restricted the
	; admin family.
	If Not BVM_RequirePrivileged() Then Return
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
	; See BVM_SETFACTIONRATING above -- same privilege story.
	If Not BVM_RequirePrivileged() Then Return
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
	; HomeFaction is the rating-table key against which NPC
	; engagement is decided; unprivileged scripts that flip it
	; can make the player invisible to any aggressive faction.
	If Not BVM_RequirePrivileged() Then Return
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
	; Bug fix: previously a degenerate `For i = 0 To Param1%` loop that
	; overwrote Result$ every iteration. The intent is a direct index
	; into FactionNames$; the loop did the right thing only when
	; Param1=99 (the last walked index) and was an off-by-one mirage
	; for every other input. Add a range check too -- the array is
	; sized 0..99.
	If Param1% >= 0 And Param1% <= 99
		Return FactionNames$(Param1%)
	EndIf
	Return ""
End Function

Function BVM_DEFAULTFACTIONRATING%(Param1$, Param2$)
	Faction1$ = Upper$(Param1$)
	Faction2$ = Upper$(Param2$)
	For i = 0 To 99
		If Upper$(FactionNames$(i)) = Faction1$
			For j = 0 To 99
				; Inner loop must compare against Faction2$, not
				; Faction1$ again -- the typo made this return
				; FactionDefaultRatings(i, 0) for any Faction2 pair.
				If Upper$(FactionNames$(j)) = Faction2$
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

; --- Script file-system access -----------------------------------------
;
; Every Script-file BVM_* command below prefixes the operand with the
; configured RCScriptFiles$ directory, but does NOT reject ".."
; segments -- so a non-privileged NPC script could trivially navigate
; out of the script-files sandbox to delete arbitrary files, overwrite
; the running executable, or read system configs.
;
; BVM_ScriptPathIsSafe$ rejects names containing ".." or absolute
; path indicators ("\foo", "C:" etc.); all script FS calls now route
; through it. The privilege gate is layered on top: most callers
; should be GM-only anyway, but the path check is the floor.
Function BVM_ScriptPathIsSafe%(Name$)
	If Name$ = "" Then Return False
	If Instr(Name$, "..") > 0 Then Return False
	If Left$(Name$, 1) = "\" Or Left$(Name$, 1) = "/" Then Return False
	; Drive letter "C:..." or any colon at position 2.
	If Len(Name$) >= 2 And Mid$(Name$, 2, 1) = ":" Then Return False
	; Reject control bytes / non-printable.
	Local i, c
	For i = 1 To Len(Name$)
		c = Asc(Mid$(Name$, i, 1))
		If c < 32 Or c = 127 Then Return False
	Next
	Return True
End Function

Function BVM_DELETEFILE(Param1$)
	If Not BVM_RequirePrivileged() Then Return
	If Not BVM_ScriptPathIsSafe(Param1$) Then Return
	DeleteFile(RCScriptFiles$ + Param1$)
End Function

Function BVM_READFILE%(Param1$)
	; Read is the only non-mutating FS op so we leave the privilege
	; gate off, but the path-traversal check still applies -- a
	; non-priv script shouldn't get to slurp arbitrary host files.
	If Not BVM_ScriptPathIsSafe(Param1$) Then Return 0
	Result% = ReadFile(RCScriptFiles$ + Param1$)
Return Result%
End Function

Function BVM_WRITEFILE%(Param1$)
	If Not BVM_RequirePrivileged() Then Return 0
	If Not BVM_ScriptPathIsSafe(Param1$) Then Return 0
	Result% = WriteFile(RCScriptFiles$ + Param1$)
Return Result%
End Function

Function BVM_OPENFILE%(Param1$)
	If Not BVM_RequirePrivileged() Then Return 0
	If Not BVM_ScriptPathIsSafe(Param1$) Then Return 0
	Result% = OpenFile(RCScriptFiles$ + Param1$)
Return Result%
End Function

Function BVM_APPENDFILE%(Param1$)
	If Not BVM_RequirePrivileged() Then Return 0
	If Not BVM_ScriptPathIsSafe(Param1$) Then Return 0
	Filename$ = RCScriptFiles$ + Param1$
	F = OpenFile(Filename$)
	; OpenFile returns 0 on missing-file/permission-denied; SeekFile
	; against a null handle is undefined behaviour (Blitz dereferences
	; internal pointers). Bail cleanly so the script gets 0 back and
	; can detect failure, matching the OpenFile error convention used
	; elsewhere in this module.
	If F = 0 Then Return 0
	SeekFile(F, FileSize(Filename$))
	Return F
End Function

Function BVM_CREATEDIR%(Param1$)
	If Not BVM_RequirePrivileged() Then Return 0
	If Not BVM_ScriptPathIsSafe(Param1$) Then Return 0
	CreateDir(RCScriptFiles$ + Param1$)
Return Result%
End Function

Function BVM_FILESIZE%(Param1$)
	; Stat is non-mutating, same posture as BVM_READFILE -- leave the
	; privilege gate off but enforce path safety. Without the
	; traversal check a non-priv script could probe metadata on host
	; files outside RCScriptFiles$ (recon for further attacks even if
	; no read access).
	If Not BVM_ScriptPathIsSafe(Param1$) Then Return 0
	Result% = FileSize(RCScriptFiles$ + Param1$)
Return Result%
End Function

Function BVM_FILETYPE%(Param1$)
	If Not BVM_ScriptPathIsSafe(Param1$) Then Return 0
	Result% = FileType(RCScriptFiles$ + Param1$)
Return Result%
End Function

Function BVM_WARP(Param1%, Param2$, Param3$, Param4%=0)
	If Not BVM_RequirePrivileged() Then Return
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
	; Equivalent-effect bypass of gated BVM_SETACTORLEVEL. GiveXP
	; mutates Actor\XP unbounded and fires the LevelUp script via
	; ThreadScript, which can call BVM_SETACTORLEVEL from a server-
	; spawned (privileged) context. A non-priv NPC script calling
	; BVM_GIVEXP(player, 999999999) drives the clicker's progression
	; without ever needing the gated SETACTORLEVEL itself. Gate parity
	; with BVM_SETACTORLEVEL.
	If Not BVM_RequirePrivileged() Then Return
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
	; ActorList is Dim'd 0..65535. Bound-check before the Null check;
	; otherwise a script-supplied ID of -1 / 99999 would read off the
	; end of the Dim and crash. Same shape as P_KillActor / BVM_SPAWN.
	If ID >= 0 And ID <= 65535
		If ActorList(ID) <> Null
			Result% = ActorList(ID)\XPMultiplier
		EndIf
	EndIf
Return Result%
End Function

Function BVM_SETACTORLEVEL(Param1%, Param2%)
	If Not BVM_RequirePrivileged() Then Return
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Actor\XP = 0
		Actor\Level = Param2%

		; Tell this player if actor is human
		If Actor\RNID > 0 Then RCE_Send(Host, Actor\RNID, P_XPUpdate, "U" + RCE_StrFromInt$(Actor\Level, 2), True)

		; Tell all other players
		Pa$ = RCE_StrFromInt$(Actor\RuntimeID, 2) + RCE_StrFromInt$(Actor\Level, 2)
		AInstance.AreaInstance = Object.AreaInstance(Actor\ServerArea)
		If AInstance <> Null
			A2.ActorInstance = AInstance\FirstInZone
			While A2 <> Null
				If A2\RNID > 0
					If A2 <> Actor Then RCE_Send(Host, A2\RNID, P_XPUpdate, "L" + Pa$, True)
				EndIf
				A2 = A2\NextInZone
			Wend
		EndIf
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
	; A non-privileged script could otherwise shut down the entire
	; server with one line ("RuntimeError(...)") by calling this
	; command -- a trivial DoS from any NPC script. Non-priv callers
	; log + return; only GM-grade scripts can force a fatal exit.
	If Not BVM_RequirePrivileged()
		WriteLog(MainLog, "Script log: BVM_RUNTIMEERROR (non-priv): " + Param1$)
		Return
	EndIf
	Shutdown()
	RuntimeError(Param1$)
End Function

Function BVM_NEWQUEST(Param1%, Param2$, Param3$, Param4%=255, Param5%=255, Param6%=255)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		If Actor\RNID > 0
			A.Account = Object.Account(Actor\Account)
			; Object.Account returns Null for stale handles; A\LoggedOn
			; is -1 between login states and 0..9 once logged in.
			; QuestLog is Field[9]; either condition would crash the
			; server with a Blitz Field OOB or Null deref during a
			; script-triggered quest mutation.
			If A = Null Or A\LoggedOn < 0 Or A\LoggedOn > 9 Then Return
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
			If A = Null Or A\LoggedOn < 0 Or A\LoggedOn > 9 Then Return
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
			If A = Null Or A\LoggedOn < 0 Or A\LoggedOn > 9 Then Return
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
			If A = Null Or A\LoggedOn < 0 Or A\LoggedOn > 9 Then Return
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
			If A = Null Or A\LoggedOn < 0 Or A\LoggedOn > 9 Then Return ""
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
			If A = Null Or A\LoggedOn < 0 Or A\LoggedOn > 9 Then Return 0
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
	; Reputation drives faction-interaction gating, vendor / quest /
	; zone access. A non-priv clicker script calling
	; SetReputation(clicker, -10000) bricks the player out of every
	; reputation-gated content surface -- same brick-vector shape as
	; SETMAXATTRIBUTE. Full-priv (not self-or-priv) for the same
	; clicker-handle reason: SI\AI = Handle(clicker) for Examine /
	; Trade / RightClick / ItemScript spawns.
	If Not BVM_RequirePrivileged() Then Return
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then UpdateReputation(Actor, Param2%)
End Function

Function BVM_REPUTATION%(Param1%)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null Then Result% = Actor\Reputation
Return Result%
End Function

Function BVM_SETRESISTANCE(Param1%, Param2$, Param3%)
	; Resistances[] is consumed by the combat damage formula in
	; GameServer.bb -- the same role FactionRatings[] plays for
	; engagement. A non-priv clicker script calling
	; SetResistance(clicker, "Fire", -100) makes the player take
	; catastrophic damage from every fire source; (clicker, "Fire",
	; 100) makes them invulnerable in PvE. Same brick-vector class
	; as SETFACTIONRATING (already gated) and the four sibling
	; gates in this PR. Full-priv for the same clicker-handle reason.
	If Not BVM_RequirePrivileged() Then Return
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
	; Equivalent-effect bypass of gated BVM_KILLACTOR. The HealthStat
	; branch below calls KillActor(Actor, Null) whenever Value[Health]
	; falls to <= 0, so a non-priv NPC's Examine / Trade / RightClick
	; script could call SetAttribute(player, "Health", 0) and one-shot
	; the clicker -- defeating the BVM_KILLACTOR gate.
	;
	; Full-priv gate (not self-or-priv): for Examine/Trade/RightClick/
	; ItemScript spawns, ThreadScript is called with `Handle(clicker)`
	; as Actor% (see ServerNet.bb P_Examine/Trade/RightClick/ItemScript
	; spawn sites), so `SI\AI = Handle(clicker)`. A self-or-priv gate
	; on Param1=clicker_handle would match SI\AI and let the kill
	; through. Match the BVM_KILLACTOR peer (RequirePrivileged) exactly.
	; Legitimate NPC self-attribute mutation belongs in privileged
	; engine-spawned scripts (combat / damage events) rather than
	; user-authored interaction scripts.
	If Not BVM_RequirePrivileged() Then Return
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
	; Equivalent-effect bypass of gated BVM_KILLACTOR -- same path as
	; BVM_SETATTRIBUTE above. ChangeAttribute(player, "Health", -big%)
	; drives Value[Health] negative and the line below calls
	; KillActor(Actor, Null). Same full-priv reasoning: clicker-driven
	; scripts have SI\AI = clicker handle, so self-or-priv on Param1
	; would not block the documented one-shot exploit.
	If Not BVM_RequirePrivileged() Then Return
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Attribute = FindAttribute(Param2$)
		; Bail if the attribute name is unknown -- without this, the
		; "Else" branch below ran with Attribute = -1 and indexed
		; Actor\Attributes\Value[-1], an OOB Blitz Dim access.
		If Attribute > -1
			Result% = Actor\Attributes\Value[Attribute]
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
	; Sibling-asymmetry security gap fixed: BVM_SETATTRIBUTE /
	; BVM_CHANGEATTRIBUTE were gated full-priv because their HealthStat
	; branch falls through to KillActor and a clicker exploit could
	; one-shot the player. The MAX-counterparts were left ungated --
	; that's still a one-shot-brick vector: a non-priv NPC's
	; Examine/Trade/RightClick/ItemScript can call
	; SetMaxAttribute(player, "Health", 1) to permanently nerf the
	; player's max HP to 1, after which the next damage tick kills
	; them. SetMaxAttribute(player, "Speed", 0) locks them in place;
	; SetMaxAttribute(player, "Energy", 0) disables spells.
	;
	; Full-priv gate (not self-or-priv): clicker-driven scripts
	; have SI\AI = clicker handle, so self-or-priv on Param1 would
	; let the clicker brick themselves -- and more importantly, any
	; *other* player the script names via FindActor. CLAUDE.md "BVM
	; clicker-handle trap" + memory feedback_sibling_protection_asymmetry.
	If Not BVM_RequirePrivileged() Then Return
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
	; Sibling-asymmetry gate -- same shape as BVM_SETMAXATTRIBUTE
	; above. ChangeMaxAttribute(player, "Health", -big%) drives
	; Maximum[Health] toward zero, brick vector identical to the
	; absolute-set form. Full-priv (not self-or-priv) -- clicker-
	; driven scripts have SI\AI = clicker handle.
	If Not BVM_RequirePrivileged() Then Return
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Attribute = FindAttribute(Param2$)
		; Bug fix: previously the `If Attribute > -1` guard only
		; protected the read; the subsequent write to
		; Actor\Attributes\Maximum[Attribute] and UpdateAttributeMax
		; ran unconditionally, so a typo in Param2$ produced an OOB
		; write at Maximum[-1] (no Blitz bounds check on Dim). Mirror
		; the bail-on-unknown-name pattern from BVM_CHANGEATTRIBUTE.
		If Attribute > -1
			Result% = Actor\Attributes\Maximum[Attribute] + Param3
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
	; Gated. Clicker-griefing risk: SetName(target, "<slur>") from a
	; non-priv NPC right-click rebrands the clicker with broadcast.
	; Pre-PR-#329 this stayed ungated because shipped content needed it
	; from non-priv RightClick spawns:
	;   - marriage.rsl + Click_marriage.rsl append a surname to both
	;     players on marriage.
	;   - Spawn_Test.rsl labels test NPCs.
	;
	; Closed by the privileged-script allowlist. All three script names
	; are in Data\Server Data\Privileged Scripts.dat; their ThreadScript
	; spawns get the elevation. Other non-priv callers refuse.
	If Not BVM_RequirePrivileged() Then Return
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
	; Gated. Same shape + clicker-griefing risk as SETNAME above
	; (SetTag(target, "<slur>") rebrands the clicker's nameplate
	; suffix). Pre-PR-#329 this stayed ungated because Spawn_Test.rsl
	; uses it from a non-priv NPC-spawn script to label test NPCs.
	; Closed by the privileged-script allowlist (Spawn_Test is in
	; Data\Server Data\Privileged Scripts.dat). Other non-priv callers
	; refuse.
	If Not BVM_RequirePrivileged() Then Return
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
	; Equivalent-effect bypass of gated BVM_SETGOLD / BVM_SETMONEY
	; below. ChangeGold(player, +N) and ChangeGold(player, -N) yield
	; exactly the same on-wallet outcome as SetGold; the gate on
	; SetGold is meaningless while this path is open. A non-priv NPC's
	; RightClick / Examine script could call ChangeGold(clicker, -big%)
	; to drain a player's wallet, or +big% to mint currency. Match the
	; gate on BVM_SETGOLD below.
	If Not BVM_RequirePrivileged() Then Return
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
	; Equivalent-effect bypass of gated BVM_SETMONEY -- alias of
	; BVM_CHANGEGOLD above (identical body). Same gate.
	If Not BVM_RequirePrivileged() Then Return
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
	If Not BVM_RequirePrivileged() Then Return
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
	; Privilege gate parity with BVM_SETGOLD above. Without this any NPC
	; Examine / Trade / RightClick script could call SetMoney(player, N)
	; and set arbitrary gold values on an arbitrary actor.
	If Not BVM_RequirePrivileged() Then Return
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
			; Bound the instance index. Ar\Instances is Dim'd 0..99;
			; a script-supplied out-of-range value would read past the
			; array (Blitz3D's Dim has no runtime check). Also skip if
			; the requested instance hasn't been created -- returning
			; 0 lets the caller's iteration terminate cleanly.
			If Instance < 0 Or Instance > 99 Then Exit
			If Ar\Instances[Instance] = Null Then Exit
			Actor.ActorInstance = Ar\Instances[Instance]\FirstInZone
			If Actor <> Null Then Result% = Handle(Actor)
			Exit
		EndIf
	Next
Return Result%
End Function

Function BVM_NEXTACTORINZONE%(Param1%)
	; Bug fix: the original implementation wrapped to FirstInZone
	; when reaching end-of-list, which made every AOE / "for each
	; actor in zone" script using the `Repeat ... Until Player =
	; Target` pattern (see AOE Damage Spell Template) loop forever
	; if the player wasn't the very first actor in the zone. Return
	; 0 at end-of-list instead so the standard `Until iter = 0`
	; termination idiom works. Scripts that genuinely want the
	; wrapping behaviour can call BVM_FIRSTACTORINZONE explicitly.
	StartActor.ActorInstance = Object.ActorInstance(Param1%)
	If StartActor <> Null
		Actor.ActorInstance = StartActor\NextInZone
		If Actor <> Null Then Result% = Handle(Actor)
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
	; ScriptGlobals$ is Field [9] (10 slots); without this bound a
	; script could write past the actor record into adjacent fields.
	If Param2% < 0 Or Param2% > 9 Then Return
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Actor\ScriptGlobals$[Param2%] = Param3$
	EndIf
End Function

Function BVM_ACTORGLOBAL$(Param1%, Param2%)
	If Param2% < 0 Or Param2% > 9 Then Return ""
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		Result$ = Actor\ScriptGlobals$[Param2%]
	Else
		Result$ = ""
	EndIf
Return Result$
End Function

Function BVM_GIVEITEM(Param1%, Param2$, Param3%=1)
	If Not BVM_RequirePrivileged() Then Return
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
										; Clamp to the 16-bit save/wire ceiling (see ClampStackAmount
										; in Inventories.bb) so a BVM GiveItem can't push the slot past
										; what the save format represents (which would lose the whole
										; stack on save->load). Excess above the cap is dropped here;
										; non-lossy residual is a deferred follow-up.
										Actor\Inventory\Amounts[i] = ClampStackAmount(Actor\Inventory\Amounts[i] + ThisAmount)

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

; Bug fix: Param3 is the required Amount (int); the invoker contract
; in RC_Standard_Invoker.bb declares it as Param3%=1. The original
; type-tagged it as Param3$ and passed the string to InventoryHasItem
; whose third arg is an integer count -- Blitz coerced silently for
; literal counts but rounded through string for any computed amount
; like `HasItem(p, "Coin", Gold(p))`.
Function BVM_HASITEM%(Param1%, Param2$, Param3% = 1)
	Actor.ActorInstance = Object.ActorInstance(Param1%)
	If Actor <> Null
		ItemName$ = Param2$
		Result% = InventoryHasItem(Actor\Inventory, ItemName$, Param3%)
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

; Bug fix: default R was 0 instead of 255. The invoker contract in
; RC_Standard_Invoker.bb declares all three RGB defaults as 255 so
; `Output(player, "hi")` produces white text -- but the impl
; defaulted red to 0, making any 2-arg call print black-on-black-ish
; messages. Restore the documented default.
Function BVM_OUTPUT(Param1%, Param2$, Param3%=255, Param4%=255, Param5%=255)
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
	; Lets a script issue arbitrary SQL against the rcce2 database
	; using the server's connection -- including SELECT on
	; rc_accounts (password hashes), DROP TABLE, UPDATE arbitrary
	; rows. Privileged-only, no exceptions.
	If Not BVM_RequirePrivileged() Then Return ""
	If MySQL = True Then Result$ = SQLQuery(hSQL, Param1$)
Return Result$
End Function

; MySQL row/query walkers -- privileged-only, matching BVM_MYSQLQUERY.
; Param1 is a server-side SQL query handle. Without the gate, a
; non-privileged script could:
;   - Walk rows of a SQL handle obtained via SCRIPTGLOBAL passing from
;     a privileged script (read e.g. account data the privileged script
;     queried but didn't want to expose to the calling NPC script).
;   - Free SQL queries the server itself is using (LoadCharacter,
;     SaveActor) by guessing handle values -- the underlying SQL
;     library doesn't validate "this query belongs to this caller".
; Privileged scripts retain full access for legitimate admin queries.
Function BVM_MYSQLNUMROWS%(Param1%)
	If Not BVM_RequirePrivileged() Then Return 0
	If MySQL = True Then Result% = SQLRowCount(Param1%)
Return Result%
End Function

Function BVM_MYSQLFETCHROW$(Param1%)
	If Not BVM_RequirePrivileged() Then Return ""
	If MySQL = True Then Result$ = SQLFetchRow(Param1%)
Return Result$
End Function

Function BVM_MYSQLGETVAR$(Param1%,Param2$)
	If Not BVM_RequirePrivileged() Then Return ""
	If MySQL = True Then Result$ = ReadSQLField(Param1%, Param2$)
	Return Result$
End Function

Function BVM_MYSQLFREEQUERY(Param1%)
	If Not BVM_RequirePrivileged() Then Return
	If MySQL = True Then FreeSQLQuery(Param1%)
End Function

Function BVM_MYSQLFREEROW(Param1%)
	If Not BVM_RequirePrivileged() Then Return
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
	; Null-SI guard: see BVM_PARAMETER for context. Every BVM_SET*
	; below the wait family writes through SI\ -- a dead hSI faults.
	If SI = Null Then Return
	SI\WaitResult$ = ""
	SI\Waiting = x%
End Function

Function BVM_SETWAITSPEAK(Param1%, Param2%)
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	; Null-SI guard: storing PS\S = Null queues a PausedScript the
	; Scripting loop later derefs as PS\S\WaitResult$ -- a script
	; freed between the command call and the resume crashes the
	; server. Skip the PausedScript allocation entirely; the wait
	; never completes (script is already dead) and that's correct.
	If SI = Null Then Return
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
	; See BVM_SETWAITSPEAK above -- same Null-SI hazard.
	If SI = Null Then Return
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
	; See BVM_SETWAITSPEAK above -- same Null-SI hazard.
	If SI = Null Then Return
	If Actor <> Null
		KillActor = Param2%
		; ActorList is Dim'd 0..65535. A script supplying a negative or
		; out-of-range KillActor (BVM Param2 is signed-int from the bytecode)
		; would index outside the Dim — Blitz3D doesn't bounds-check Dim
		; accesses and writes through the resulting wild pointer, so this
		; corrupts adjacent globals and crashes the server unpredictably.
		If KillActor >= 0 And KillActor <= 65535
			If ActorList(KillActor) <> Null
				PS.PausedScript = New PausedScript
				PS\S = SI
				PS\Reason = 2
				PS\ReasonActor = Actor
				PS\ReasonKillActor = ActorList(KillActor)
				PS\ReasonAmount = Param3%
			EndIf
		EndIf
	EndIf
End Function

Function BVM_SETWAITINFO(Param1%, Param2%)
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	If SI = Null Then Return
	SI\WaitHour% = Param1%
	SI\WaitMinute% = Param2%
End Function

Function BVM_SETWAITTIME(Param1%)
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	If SI = Null Then Return
	SI\WaitTime = Param1%
End Function

Function BVM_SETWAITSTART(Param1%)
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	If SI = Null Then Return
	SI\WaitStart = Param1%
End Function

Function BVM_SETWAITRESULT(PARAM1$)
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	If SI = Null Then Return
	SI\WaitResult$ = PARAM1$
End Function

Function BVM_GETWAITRESULT$()
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	If SI = Null Then Return ""
	Return SI\WaitResult$
End Function

Function BVM_SETSUPERGLOBAL(Param1%, Param2$)
	; SuperGlobals$ is Dim'd (99) -> 100 slots, indices 0..99.
	; Without this bound a script's bad index is a Blitz Dim OOB write
	; (no runtime check) and corrupts adjacent globals or crashes.
	If Param1% < 0 Or Param1% > 99 Then Return
	SuperGlobals$(Param1%) = Param2$
End Function

Function BVM_GETSUPERGLOBAL$(Param1%)
	If Param1% < 0 Or Param1% > 99 Then Return ""
Return  SuperGlobals$(Param1%)
End Function

;-Dialog Helper Functions---------------------------
Function RCE_SendOpenDialog(Host%, ARNID%, CARuntimeID%, BackgroundTexID%, Title$)
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	; Null-SI guard: hSI can be dead between dispatch and command body
	; (script Free'd mid-flight, host-side invocation, BVM reentry).
	; The bare SI\WaitResult$ deref below crashes the server.
	If SI = Null Then Return
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
	If SI = Null Then Return
	SI\WaitResult$ = ""
	Pa$ = "T" + RCE_StrFromInt$(Red, 1) + RCE_StrFromInt$(Green, 1) + RCE_StrFromInt$(Blue, 1) + RCE_StrFromInt$(dhandle) + Message$
	RCE_Send(Host, ARNID, P_Dialog, Pa$, True)
End Function

Function RCE_SendDialogInput(Host%, ARNID%, dhandle%, Options$, Delim$ = ",")
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	If SI = Null Then Return
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
	If SI = Null Then Return
	SI\WaitResult$ = ""
	Pa$ = RCE_StrFromInt$(hSI, 4) + RCE_StrFromInt$(iType, 1) + RCE_StrFromInt$(Len(Title$), 2) + Title$ + Prompt$
	RCE_Send(Host, ARNID, P_ScriptInput, Pa$, True)
End Function

;-Progress Bar Helper Functions---------------------------

Function RCE_SendCreateProgressBar(Host%, ARNID%, R%, G%, B%, X#, Y#, W#, H#, Maximum%, Value%, Label$)
	SI.ScriptInstance = Object.ScriptInstance(hSI)
	If SI = Null Then Return
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
	; Invoking from inside a script frees the running module's
	; bytecode underneath it (use-after-free) AND lets any non-priv
	; script reload the entire script tree as a griefing / DoS
	; primitive. Privileged callers only.
	If Not BVM_RequirePrivileged() Then Return
	WriteLog(MainLog, "Refreshing scripts...")
	WriteLog(MainLog, "Halting running scripts...")
	For SI.ScriptInstance = Each ScriptInstance
		SI\Ended = True
	Next
	UpdateScripts()

	; After-cursor walk: the body Deletes SS, which would corrupt
	; the For-Each cursor on the next iteration step (Blitz3D
	; advances via the deleted element's "next" pointer). Established
	; pattern from PausedScript / ThreadScript sweeps; documented in
	; CLAUDE.md ("Iterator-during-iteration hazards", #247).
	Local SS.ScriptSource = First ScriptSource
	Local SSNext.ScriptSource = Null
	While SS <> Null
		SSNext = After SS
		BVM_ReleaseModule(SS\hModule)
		Delete SS
		SS = SSNext
	Wend

	WriteLog(MainLog, "Deleted all loaded scripts")
	Number = LoadScripts() : WriteLog(MainLog, "Loaded " + Str$(Number) + " scripts.")
	Number = CompileModules() : WriteLog(MainLog, "Compiled " + Str$(Number) + " modules.")
End Function

;-UDP Networking commands
; UDP networking surface -- the entire family is privileged-only.
; Without this gate, any NPC's right-click script could:
;   - Open a UDP socket on the server's interface
;   - Send arbitrary UDP datagrams to any IP/port (DNS amplification,
;     internal-network scanning, firewall-bypass via the server's
;     outbound socket)
;   - Block on receive against a hostile target
; Privileged scripts (admin/GM-spawned via /script chat command or
; the privileged BVM_THREADEXECUTE caller-propagation path) keep
; full access -- this is the same gate the file-system family
; (BVM_DELETEFILE, BVM_WRITEFILE, BVM_OPENFILE, BVM_APPENDFILE,
; BVM_CREATEDIR) already uses.

Function BVM_CreateUDPStream%(Param1%=0)
	If Not BVM_RequirePrivileged() Then Return 0
	Port = Param1%
	If Port > 0
		Result% = CreateUDPStream(Port)
	Else
		Result% = CreateUDPStream()
	EndIf
Return Result%
End Function

Function BVM_CloseUDPStream(Param1%)
	If Not BVM_RequirePrivileged() Then Return
	CloseUDPStream(Param1%)
End Function

Function BVM_SendUDPMsg(Param1%, Param2%, Param3%)
	If Not BVM_RequirePrivileged() Then Return
	Port% = Param3%
	If Port > 0
		SendUDPMsg(Param1%, Param2%, Port%)
	Else
		SendUDPMsg(Param1%, Param2%)
	EndIf
End Function

Function BVM_RecvUDPMsg$(Param1%)
	If Not BVM_RequirePrivileged() Then Return ""
	Result$ = RecvUDPMsg(Param1%)
Return Result$
End Function

Function BVM_UDPStreamIP%(Param1%)
	If Not BVM_RequirePrivileged() Then Return 0
	Result% = UDPStreamIP(Param1%)
Return Result%
End Function

Function BVM_UDPStreamPort%(Param1%)
	If Not BVM_RequirePrivileged() Then Return 0
	Result% = UDPStreamPort(Param1%)
Return Result%
End Function

Function BVM_UDPMsgIP%(Param1%)
	If Not BVM_RequirePrivileged() Then Return 0
	Result% = UDPMsgIP(Param1%)
Return Result%
End Function

Function BVM_UDPMsgPort%(Param1%)
	If Not BVM_RequirePrivileged() Then Return 0
	Result% = UDPMsgPort(Param1%)
Return Result%
End Function

Function BVM_UDPTimeouts(Param1%)
	If Not BVM_RequirePrivileged() Then Return
	UDPTimeouts(Param1%)
End Function

Function BVM_CountHostIPs%(Param1%)
	If Not BVM_RequirePrivileged() Then Return 0
	Result% = CountHostIPs(Param1%)
Return Result%
End Function

Function BVM_HostIP%(Param1%)
	If Not BVM_RequirePrivileged() Then Return 0
	Result% = HostIP(Param1%)
Return Result%
End Function

Function BVM_DottedIP$(Param1%)
	If Not BVM_RequirePrivileged() Then Return ""
	Result$ = DottedIP$(Param1%)
Return Result$
End Function