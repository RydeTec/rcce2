
;##############################################################################################################################
; Realm Crafter version 1.10																									
; Copyright (C) 2007 Solstar Games, LLC. All rights reserved																	
; contact@solstargames.com																																																		
;																																																																#
; Programmer: Rob Williams																										
; Program: Realm Crafter Game Server module
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
;HISTORY:
;
;Actor moves/change areas standard update timing fix. 8/16/2007 Rofar.  set IgnoreUpdate flag in change area function
;
;##############################################################################################################################

Type GameWindow
	Field Window
	Field LTime, LDate
	Field MessageText, MessageButton
	Field SendMessageText, SendMessageButton
	Field AreaCombo, PlayersList, BootButton
	Field ChatText, ChatLogMode, ChatLogFlushButton
	Field RefreshScriptsButton
	Field LScripts
End Type

Global Game.GameWindow
Global LastSpellRecharge
Global GameArea.Area
Global LoginMessage$

; Creates the Game window
Function CreateGameWindow.GameWindow()

	G.GameWindow = New GameWindow
	G\Window = CreateWindow("Game", 10, 470, 500, 580, Desktop(), 1)
	G\LTime = CreateLabel("Game time: ", 10, 10, 350, 20, G\Window)
	G\LDate = CreateLabel("Game date: ", 10, 30, 350, 20, G\Window)
	G\RefreshScriptsButton = CreateButton("Refresh scripts", 400, 10, 90, 25, G\Window)
	G\LScripts = CreateLabel("Scripts:", 410, 40, 90, 20, G\Window)
	CreateLabel("Login message:", 10, 60, 150, 20, G\Window)
	G\MessageText = CreateTextField(10, 82, 350, 23, G\Window)
	G\MessageButton = CreateButton("Update", 370, 80, 100, 25, G\Window)
	CreateLabel("Global message:", 10, 110, 150, 20, G\Window)
	G\SendMessageText = CreateTextField(10, 132, 350, 23, G\Window)
	G\SendMessageButton = CreateButton("Send", 370, 130, 100, 25, G\Window)
	CreateLabel("View zone:", 10, 182, 150, 20, G\Window)
	G\AreaCombo = CreateComboBox(65, 180, 320, 20, G\Window)
	CreateLabel("Players in zone:", 10, 220, 150, 20, G\Window)
	G\PlayersList = CreateListBox(10, 240, 200, 100, G\Window)
	CreateLabel("Zone chat:", 10, 350, 150, 20, G\Window)
	G\ChatText = CreateTextArea(10, 370, 440, 150, G\Window, 1)
	CreateLabel("Log chat messages:", 10, 527, 100, 20, G\Window)
	G\ChatLogMode = CreateComboBox(110, 525, 90, 20, G\Window)
	AddGadgetItem(G\ChatLogMode, "Never")
	AddGadgetItem(G\ChatLogMode, "This zone only")
	AddGadgetItem(G\ChatLogMode, "Always", True)
	G\ChatLogFlushButton = CreateButton("Flush chat log", 250, 525, 90, 20, G\Window)
	G\BootButton = CreateButton("Boot player", 230, 240, 100, 25, G\Window)
	Return G

End Function

; Gives XP points to an actor instance
Function GiveXP(A.ActorInstance, XP, IgnoreParty = 0)
	; Give the points to the leader, if this actor has a leader
	If A\Leader <> Null
		GiveXP(A\Leader, XP, IgnoreParty)
		Return
	EndIf

	; Share with other party members in same area, if any
	If IgnoreParty = 0
		Party.Party = Object.Party(A\PartyID)
		If Party <> Null
			Members = 0
			For i = 0 To 7
				If Party\Player[i] <> Null
					If Party\Player[i]\ServerArea = A\ServerArea Then Members = Members + 1
				EndIf
			Next
			PartyXP = XP / Members
			For i = 0 To 7
				If Party\Player[i] <> Null And Party\Player[i] <> A
					If Party\Player[i]\ServerArea = A\ServerArea Then GiveXP(Party\Player[i], PartyXP, True)
				EndIf
			Next
			XP = PartyXP + (XP Mod Party\Members)
		EndIf
	EndIf

	; Add gain to character
	A\XP = A\XP + XP

	; Call script and tell player if it's a human character
	If A\RNID > 0
		ThreadScript("LevelUp", "Main", Handle(A), 0)
		RCE_Send(Host, A\RNID, P_XPUpdate, "M" + RCE_StrFromInt$(XP, 4), True)
	EndIf

End Function

; Kills off an actor instance
Function KillActor(A.ActorInstance, Killer.ActorInstance)

	; Tell players in the same area if it was an AI actor dying
	If A\RNID < 0
		Pa$ = RCE_StrFromInt$(A\RuntimeID, 2)
		If Killer <> Null Then Pa$ = Pa$ + RCE_StrFromInt$(Killer\RuntimeID, 2)
		AInstance.AreaInstance = Object.AreaInstance(A\ServerArea)
		A2.ActorInstance = AInstance\FirstInZone
		While A2 <> Null
			If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_ActorDead, Pa$, True)
			A2 = A2\NextInZone
		Wend
	EndIf

	If Killer <> Null
		; Reduce faction rating if it isn't already at -100%
		Killer\FactionRatings[A\HomeFaction] = Killer\FactionRatings[A\HomeFaction] - CombatRatingAdjust
		If Killer\FactionRatings[A\HomeFaction] < 0 Then Killer\FactionRatings[A\HomeFaction] = 0

		; Give XP to the killer
		Diff = A\Level - Killer\Level
		If Diff < 1 Then Diff = 1
		XP = (Diff * A\Actor\XPMultiplier) + Rand(0, 20)
		GiveXP(Killer, XP)
	EndIf

	; Continue any paused scripts waiting for this event
	For PS.PausedScript = Each PausedScript
		If PS\Reason = 2
			If PS\ReasonActor = Killer And PS\ReasonKillActor = A\Actor
				PS\ReasonCount = PS\ReasonCount + 1
				If PS\ReasonCount >= PS\ReasonAmount
					PS\S\WaitResult$ = "1"
					Delete PS
				EndIf
			EndIf
		EndIf
	Next

	; Human death
	If A\RNID > 0
		; Run script
		ThreadScript("Death", "Main", Handle(A), Handle(Killer))

		; Any AI actors targeting this player should stop
		For A2.ActorInstance = Each ActorInstance
			If A2\AITarget = A Then A2\AITarget = Null
		Next
	; Remove AI actors from game
	Else
		; Optional AI death script
		Params$ = A\Actor\Race$ + "," + A\Actor\Class$ + ", " + A\X# + "," + A\Y# + "," + A\Z#
		For i = 0 To 9
			Params$ = Params$ + "," + A\ScriptGlobals$[i]
		Next
		If A\DeathScript$ <> "" Then ThreadScript(A\DeathScript$, "Main", Handle(Killer), 0, Params$)
		; Remove from zone linked list
		If A\NextInZone <> Null
			AInstance.AreaInstance = Object.AreaInstance(A\ServerArea)
			A2.ActorInstance = AInstance\FirstInZone
			If A2 = A
				AInstance\FirstInZone = A\NextInZone
			Else
				While A2\NextInZone <> A
					A2 = A2\NextInZone
				Wend
				A2\NextInZone = A\NextInZone
			EndIf
		EndIf
		; Remove from spawn point if attached to one
		If A\SourceSP > -1
			AInstance.AreaInstance = Object.AreaInstance(A\ServerArea)
			AInstance\Spawned[A\SourceSP] = AInstance\Spawned[A\SourceSP] - 1
		EndIf
		FreeActorScripts(A)
		FreeActorInstance(A)
	EndIf

End Function

; Fires a projectile from one actor at another
Function FireProjectile(P.Projectile, A1.ActorInstance, A2.ActorInstance)

	; Check both actors are allowed to engage in combat
	If A1\Actor\Aggressiveness = 3 Or A2\Actor\Aggressiveness = 3 Then Return

	; Check faction ratings
	If A1\FactionRatings[A2\HomeFaction] > 150 Then Return

	; Tell all players about the projectile so they can display it
	Pa$ = RCE_StrFromInt$(A1\RuntimeID, 2) + RCE_StrFromInt$(A2\RuntimeID, 2) + RCE_StrFromInt$(P\MeshID, 2)
	Pa$ = Pa$ + RCE_StrFromInt$(P\Emitter1TexID, 2) + RCE_StrFromInt$(P\Emitter2TexID, 2) + RCE_StrFromInt$(P\Homing, 1) + RCE_StrFromInt$(P\Speed, 1)
	Pa$ = Pa$ + RCE_StrFromInt$(Len(P\Emitter1$), 1) + P\Emitter1$ + P\Emitter2$
	AInstance.AreaInstance = Object.AreaInstance(A1\ServerArea)
	A3.ActorInstance = AInstance\FirstInZone
	While A3 <> Null
		If A3\RNID > 0 Then RCE_Send(Host, A3\RNID, P_Projectile, Pa$, True)
		A3 = A3\NextInZone
	Wend

	; Does the projectile hit the target?
	ToHit = Rand(100)
	If ToHit <= P\HitChance
		; Calculate damage
		AP = GetArmourLevel(A2\Inventory) + (A2\Resistances[P\DamageType] - 100)
		Damage = (P\Damage + Rand(-5, 5)) - AP
		If Damage < 1 Then Damage = 1

		; Apply damage
		A2\Attributes\Value[HealthStat] = A2\Attributes\Value[HealthStat] - Damage

		; Tell player(s) if applicable
		Pa$ = RCE_StrFromInt$(Damage + 1, 2) + RCE_StrFromInt$(P\DamageType, 1)
		If A1\RNID > 0
			RCE_Send(Host, A1\RNID, P_AttackActor, "H" + RCE_StrFromInt$(A2\RuntimeID, 2) + Pa$, True)
		EndIf
		If A2\RNID > 0
			RCE_Send(Host, A2\RNID, P_AttackActor, "Y" + RCE_StrFromInt$(A1\RuntimeID, 2) + Pa$, True)
		EndIf

		; Make attacked actor angry if it's defensive
		If A2\RNID = -1
			If A2\Actor\Aggressiveness = 1
				A2\AITarget = A1
				A2\AIMode = AI_Chase
				AICallForHelp(A2)
			; Or if it's aggressive and has no target...
			ElseIf A2\Actor\Aggressiveness = 2 And A2\AITarget = Null
				A2\AITarget = A1
				A2\AIMode = AI_Chase
				AICallForHelp(A2)
			EndIf
		EndIf

		; Death
		If A2\Attributes\Value[HealthStat] <= 0 Then KillActor(A2, A1)
	EndIf

End Function

; Makes one actor instance attack another
Function ActorAttack(A1.ActorInstance, A2.ActorInstance)

	; Get distance between the actor instances
	XDist# = A1\X# - A2\X#
	ZDist# = A1\Z# - A2\Z#
	YDist# = (A1\Y# - A2\Y#) / 5.0
	Dist# = (XDist# * XDist#) + (ZDist# * ZDist#) + (YDist# * YDist#)

	; Check if this is actually a projectile attack
	If A1\Inventory\Items[SlotI_Weapon] <> Null
		If A1\Inventory\Items[SlotI_Weapon]\Item\WeaponType = W_Ranged
			If A1\Inventory\Items[SlotI_Weapon]\ItemHealth > 0
				; Fixed function
				If CombatFormula <> 4
					; In range?
					CheckDist# = A1\Inventory\Items[SlotI_Weapon]\Item\Range# + A1\Actor\Radius# + A2\Actor\Radius#
					If Dist# > CheckDist# * CheckDist# Then Return False

					; Tell other players in the same area
					Pa$ = "O" + RCE_StrFromInt$(A1\RuntimeID, 2) + RCE_StrFromInt$(A2\RuntimeID, 2)
					AInstance.AreaInstance = Object.AreaInstance(A1\ServerArea)
					A3.ActorInstance = AInstance\FirstInZone
					While A3 <> Null
						If A3\RNID > 0
							If A3 <> A1 And A3 <> A2 Then RCE_Send(Host, A3\RNID, P_AttackActor, Pa$, True)
						EndIf
						A3 = A3\NextInZone
					Wend

					; Launch projectile
					P.Projectile = ProjectileList(A1\Inventory\Items[SlotI_Weapon]\Item\RangedProjectile)
					If P <> Null
						FireProjectile(P, A1, A2)
						A1\LastAttack = MilliSecs()
					EndIf
				; Attack script
				Else
					; Check both actors are allowed to engage in combat
					If A1\Actor\Aggressiveness = 3 Or A2\Actor\Aggressiveness = 3 Then Return False
					; Check faction ratings
					If A1\FactionRatings[A2\HomeFaction] > 150 Then Return False
					; Store time of attack
					A1\LastAttack = MilliSecs()
					; Make attacked actor angry if it's defensive
					If A2\RNID = -1
						If A2\Actor\Aggressiveness = 1
							A2\AITarget = A1
							A2\AIMode = AI_Chase
						; Or if it's aggressive and has no target...
						ElseIf A2\Actor\Aggressiveness = 2 And A2\AITarget = Null
							A2\AITarget = A1
							A2\AIMode = AI_Chase
						EndIf
					EndIf
					ThreadScript("Attack", "Main", Handle(A1), Handle(A2))
				EndIf
				Return True
			Else
				If A1\RNID > 0 Then RCE_Send(Host, A1\RNID, P_ChatMessage, Chr$(253) + LanguageString$(LS_WeaponDamaged), True)
				Return False
			EndIf
		EndIf
	EndIf

	; Check both actors are allowed to engage in combat
	If A1\Actor\Aggressiveness = 3 Or A2\Actor\Aggressiveness = 3 Then Return False

	; Check faction ratings
	If A1\FactionRatings[A2\HomeFaction] > 150 Then Return False

	; Check distance is acceptable
	CheckDist# = 7.0 + A1\Actor\Radius# + A2\Actor\Radius#
	If Dist# > CheckDist# * CheckDist# Then Return False

	; Store time of attack
	A1\LastAttack = MilliSecs()

	; Make attacked actor angry if it's defensive
	If A2\RNID = -1
		If A2\Actor\Aggressiveness = 1
			A2\AITarget = A1
			A2\AIMode = AI_Chase
		; Or if it's aggressive and has no target...
		ElseIf A2\Actor\Aggressiveness = 2 And A2\AITarget = Null
			A2\AITarget = A1
			A2\AIMode = AI_Chase
		EndIf
	EndIf

	; Calculate damage
	; Normal formula
	If CombatFormula = 1
		AICallForHelp(A2)
		; 90% chance to hit
		ToHit = Rand(100)
		If ToHit > 10
			; Initial damage
			Strength = A1\Attributes\Value[StrengthStat]
			If A1\Inventory\Items[SlotI_Weapon] <> Null
				If A1\Inventory\Items[SlotI_Weapon]\ItemHealth > 0
					Damage = A1\Inventory\Items[SlotI_Weapon]\Item\WeaponDamage
					If Strength < Damage
						Damage = Damage - Rand(5, 8)
					ElseIf Strength > Damage
						Damage = Damage + Rand(5, 8)
					Else
						Damage = Damage + Rand(-5, 5)
					EndIf
					DamageType = A1\Inventory\Items[SlotI_Weapon]\Item\WeaponDamageType
				Else
					Damage = (Strength / 8) + Rand(-5, 5)
					DamageType = A1\Actor\DefaultDamageType
				EndIf
			Else
				Damage = (Strength / 8) + Rand(-5, 5)
				DamageType = A1\Actor\DefaultDamageType
			EndIf

			; Critical damage
			If Rand(1, 10) = 1
				Damage = Damage * 2
				If A1\RNID > 0 Then RCE_Send(Host, A1\RNID, P_ChatMessage, Chr$(250) + Chr$(255) + Chr$(225) + Chr$(100) + LanguageString$(LS_CriticalDamage), True)
			EndIf

			; Armour
			AP = GetArmourLevel(A2\Inventory) + (A2\Resistances[DamageType] - 100)
			If ToughnessStat > -1 Then AP = AP + (A2\Attributes\Value[ToughnessStat] / 8)
			Damage = Damage - AP

			; Minimum of 1
			If Damage < 1 Then Damage = 1
		; Miss!
		Else
			Damage = -1
		EndIf
	; No strength bonus or penalty
	ElseIf CombatFormula = 2
		AICallForHelp(A2)
		; 90% chance to hit
		ToHit = Rand(100)
		If ToHit > 10
			; Initial damage
			If A1\Inventory\Items[SlotI_Weapon] <> Null
				If A1\Inventory\Items[SlotI_Weapon]\ItemHealth > 0
					Damage = A1\Inventory\Items[SlotI_Weapon]\Item\WeaponDamage
					DamageType = A1\Inventory\Items[SlotI_Weapon]\Item\WeaponDamageType
				Else
					Damage = (A1\Attributes\Value[StrengthStat] / 8) + Rand(-5, 5)
					DamageType = A1\Actor\DefaultDamageType
				EndIf
			Else
				Damage = (A1\Attributes\Value[StrengthStat] / 8) + Rand(-5, 5)
				DamageType = A1\Actor\DefaultDamageType
			EndIf

			; Critical damage
			If Rand(1, 10) = 1
				Damage = Damage * 2
				If A1\RNID > 0 Then RCE_Send(Host, A1\RNID, P_ChatMessage, Chr$(250) + Chr$(255) + Chr$(225) + Chr$(100) + LanguageString$(LS_CriticalDamage), True)
			EndIf

			; Armour
			AP = GetArmourLevel(A2\Inventory) + (A2\Resistances[DamageType] - 100)
			If ToughnessStat > -1 Then AP = AP + (A2\Attributes\Value[ToughnessStat] / 8)
			Damage = Damage - AP

			; Minimum of 1
			If Damage < 1 Then Damage = 1
		; Miss!
		Else
			Damage = -1
		EndIf
	; Multiplied formula
	ElseIf CombatFormula = 3
		AICallForHelp(A2)
		; 90% chance to hit
		ToHit = Rand(100)
		If ToHit > 10
			; Initial damage
			Strength = A1\Attributes\Value[StrengthStat]
			If A1\Inventory\Items[SlotI_Weapon] <> Null
				If A1\Inventory\Items[SlotI_Weapon]\ItemHealth > 0
					Damage = A1\Inventory\Items[SlotI_Weapon]\Item\WeaponDamage * Strength
					DamageType = A1\Inventory\Items[SlotI_Weapon]\Item\WeaponDamageType
				Else
					Damage = Strength + Rand(-10, 10)
					DamageType = A1\Actor\DefaultDamageType
				EndIf
			Else
				Damage = Strength + Rand(-10, 10)
				DamageType = A1\Actor\DefaultDamageType
			EndIf

			; Critical damage
			If Rand(1, 10) = 1
				Damage = Damage * 2
				If A1\RNID > 0 Then RCE_Send(Host, A1\RNID, P_ChatMessage, Chr$(250) + Chr$(255) + Chr$(225) + Chr$(100) + LanguageString$(LS_CriticalDamage), True)
			EndIf

			; Armour
			AP = GetArmourLevel(A2\Inventory) + (A2\Resistances[DamageType] - 100)
			If ToughnessStat > -1 Then AP = AP * A2\Attributes\Value[ToughnessStat] Else AP = AP * AP
			Damage = Damage - AP

			; Minimum of 1
			If Damage < 1 Then Damage = 1
		; Miss!
		Else
			Damage = -1
		EndIf
	; Scripted
	ElseIf CombatFormula = 4
		ThreadScript("Attack", "Main", Handle(A1), Handle(A2))
		Goto SkipAttackNet
	EndIf

	; Damage weapon
	If WeaponDamage = True
		If A1\Inventory\Items[SlotI_Weapon] <> Null
			If A1\Inventory\Items[SlotI_Weapon]\ItemHealth > 0
				If Rand(1, 5) = 1
					A1\Inventory\Items[SlotI_Weapon]\ItemHealth = A1\Inventory\Items[SlotI_Weapon]\ItemHealth - 1
					If A1\RNID > 0
						Pa$ = RCE_StrFromInt$(SlotI_Weapon, 1) + RCE_StrFromInt$(A1\Inventory\Items[SlotI_Weapon]\ItemHealth, 2)
						RCE_Send(Host, A1\RNID, P_ItemHealth, Pa$, True)
					EndIf
				EndIf
			EndIf
		EndIf
	EndIf

	; Damage armour
	If ArmourDamage = True
		For i = SlotI_Shield To SlotI_Feet
			If A1\Inventory\Items[i] <> Null
				If A1\Inventory\Items[i]\ItemHealth > 0
					If Rand(1, 5) = 1
						A1\Inventory\Items[i]\ItemHealth = A1\Inventory\Items[i]\ItemHealth - 1
						If A1\RNID > 0
							Pa$ = RCE_StrFromInt$(i, 1) + RCE_StrFromInt$(A1\Inventory\Items[i]\ItemHealth, 2)
							RCE_Send(Host, A1\RNID, P_ItemHealth, Pa$, True)
						EndIf
					EndIf
				EndIf
			EndIf
		Next
	EndIf

	; Apply damage to target actor
	If Damage > 0 Then A2\Attributes\Value[HealthStat] = A2\Attributes\Value[HealthStat] - Damage

	; Tell player(s) if applicable
	Pa$ = RCE_StrFromInt$(Damage + 1, 2) + RCE_StrFromInt$(DamageType, 1)
	If A1\RNID > 0
		RCE_Send(Host, A1\RNID, P_AttackActor, "H" + RCE_StrFromInt$(A2\RuntimeID, 2) + Pa$, True)
	EndIf
	If A2\RNID > 0
		RCE_Send(Host, A2\RNID, P_AttackActor, "Y" + RCE_StrFromInt$(A1\RuntimeID, 2) + Pa$, True)
	EndIf

	; Tell other players in the same area
	Pa$ = "O" + RCE_StrFromInt$(A1\RuntimeID, 2) + RCE_StrFromInt$(A2\RuntimeID, 2)

	AInstance.AreaInstance = Object.AreaInstance(A1\ServerArea)
	A3.ActorInstance = AInstance\FirstInZone
	While A3 <> Null
		If A3\RNID > 0
			If A3 <> A1 And A3 <> A2 Then RCE_Send(Host, A3\RNID, P_AttackActor, Pa$, True)
		EndIf
		A3 = A3\NextInZone
	Wend

	.SkipAttackNet

	; If target was a player with pets, make pets attack too
	If A1\RNID > 0
		If A1\NumberOfSlaves > 0
			Found = 0
			For A3.ActorInstance = Each ActorInstance
				If A3\Leader = A1
					Found = Found + 1
					If A3\Actor\Aggressiveness < 3 And A3\AITarget = Null
						A3\AITarget = A2
						A3\AIMode = AI_PetChase
					EndIf
					If Found = A1\NumberOfSlaves Then Exit
				EndIf
			Next
		EndIf
	EndIf

	; Death
	If A2\Attributes\Value[HealthStat] <= 0 Then KillActor(A2, A1)

	Return True

End Function

; Updates position, etc. of all actor instances
Function UpdateActorInstances(Broadcast)

	; Update actor effects
	T = MilliSecs()
	For AE.ActorEffect = Each ActorEffect
		; Owner has gone
		If AE\Owner = Null
			Delete AE\Attributes
			Delete AE
		; Owner still alive and online
		ElseIf AE\Owner\RNID <> 0
			; Remove effect when time is up
			If T - AE\CreatedTime > AE\Length
				If AE\Length > 0
					; Tell client if applicable
					If AE\Owner\RNID > 0
						Pa$ = RCE_StrFromInt$(Handle(AE), 4)
						For i = 0 To 39
							Pa$ = Pa$ + RCE_StrFromInt$(AE\Attributes\Value[i], 4)
						Next
						RCE_Send(Host, AE\Owner\RNID, P_ActorEffect, "R" + Pa$, True)
					EndIf

					; Remove effect
					For i = 0 To 39
						AE\Owner\Attributes\Value[i] = AE\Owner\Attributes\Value[i] - AE\Attributes\Value[i]
					Next
					Delete AE\Attributes
					Delete AE
				EndIf
			EndIf
		EndIf
	Next

	; Recharging this frame?
	Recharge = False
	If MilliSecs() - LastSpellRecharge > 100
		Recharge = True
		LastSpellRecharge = MilliSecs()
	EndIf

	For AI.ActorInstance = Each ActorInstance
		If AI\RuntimeID > -1
			AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)

			; Recharge spells
			If Recharge = True
				If RequireMemorise
					For i = 0 To 9
						If AI\SpellCharge[i] > 0 Then AI\SpellCharge[i] = AI\SpellCharge[i] - 100
					Next
				Else
					For i = 0 To 999
						If AI\SpellCharge[i] > 0 Then AI\SpellCharge[i] = AI\SpellCharge[i] - 100
					Next
				EndIf
			EndIf

			; Move (except mounts)
			If AI\Rider = Null
				If AI\Mount = Null
					Speed# = 1.5 * (Float#(AI\Attributes\Value[SpeedStat]) / Float#(AI\Attributes\Maximum[SpeedStat]))
				Else
					Speed# = 1.5 * (Float#(AI\Mount\Attributes\Value[SpeedStat]) / Float#(AI\Mount\Attributes\Maximum[SpeedStat]))
				EndIf
				If AI\WalkingBackward = True Then Speed# = Speed# / 2.0
				If AI\IsRunning = True
					Allowed = True
					If EnergyStat > -1 And AI\Mount = Null
						AI\Attributes\Value[EnergyStat] = AI\Attributes\Value[EnergyStat] - 1
						If AI\Attributes\Value[EnergyStat] <= 0
							Allowed = False
							AI\Attributes\Value[EnergyStat] = 0
							AI\IsRunning = False
						EndIf
					EndIf
					If Allowed = True Then Speed# = Speed# * 2.0
				EndIf
				XDist# = AI\DestX# - AI\X#
				ZDist# = AI\DestZ# - AI\Z#
				If Abs(XDist#) > 0.5 Or Abs(ZDist#) > 0.5
					AI\X# = AI\X# + ((XDist# / (Abs(XDist#) + Abs(ZDist#))) * Speed#)
					AI\Z# = AI\Z# + ((ZDist# / (Abs(XDist#) + Abs(ZDist#))) * Speed#)
				EndIf
			; Mounts stay with their rider
			Else
				AI\X# = AI\Rider\X#
				AI\Y# = AI\Rider\Y#
				AI\Z# = AI\Rider\Z#
			EndIf

			; Underwater damage
			If AI\Actor\Environment <> Environment_Swim
				Underwater = 0
				For SW.ServerWater = Each ServerWater
					If SW\Area = AInstance\Area
						If AI\Y# < SW\Y# + 0.5
							If AI\X# > SW\X# And AI\X# < SW\X# + SW\Width#
								If AI\Z# > SW\Z# And AI\Z# < SW\Z# + SW\Depth#
									If AI\Underwater = 0 Then AI\Underwater = MilliSecs()
									Underwater = Handle(SW)
									DistUnder# = SW\Y# - AI\Y#
									Exit
								EndIf
							EndIf
						EndIf
					EndIf
				Next
				If Underwater = 0
					AI\Underwater = 0
					; Restore breath
					If BreathStat > -1
						If AI\Attributes\Value[BreathStat] < AI\Attributes\Maximum[BreathStat] And Rand(1, 10) = 1
							AI\Attributes\Value[BreathStat] = AI\Attributes\Value[BreathStat] + 1
							If AI\RNID > 0
								Pa$ = RCE_StrFromInt$(AI\RuntimeID, 2) + RCE_StrFromInt$(BreathStat, 1) + RCE_StrFromInt$(AI\Attributes\Value[BreathStat], 2)
								RCE_Send(Host, AI\RNID, P_StatUpdate, "A" + Pa$, True)
							EndIf
						EndIf
					EndIf
				ElseIf MilliSecs() - AI\Underwater >= 1000
					AI\Underwater = AI\Underwater + 1000
					SW = Object.ServerWater(Underwater)
					; Remove breath, or health if none left
					If BreathStat > -1 And DistUnder# > 2.0
						AI\Attributes\Value[BreathStat] = AI\Attributes\Value[BreathStat] - 1
						If AI\Attributes\Value[BreathStat] < 0
							AI\Attributes\Value[BreathStat] = 0
							UpdateAttribute(AI, HealthStat, AI\Attributes\Value[HealthStat] - 1)
							If AI\Attributes\Value[HealthStat] <= 0
								AI\Attributes\Value[HealthStat] = 0
								KillActor(AI, Null)
							EndIf
						EndIf
						If AI\RNID > 0
							Pa$ = RCE_StrFromInt$(AI\RuntimeID, 2) + RCE_StrFromInt$(BreathStat, 1) + RCE_StrFromInt$(AI\Attributes\Value[BreathStat], 2)
							RCE_Send(Host, AI\RNID, P_StatUpdate, "A" + Pa$, True)
						EndIf
					EndIf
					; Water damage
					If SW\Damage > 0
						Damage = SW\Damage - (AI\Resistances[SW\DamageType] - 100)
						If Damage < 1 Then Damage = 1
						UpdateAttribute(AI, HealthStat, AI\Attributes\Value[HealthStat] - Damage)
						If AI\Attributes\Value[HealthStat] <= 0
							AI\Attributes\Value[HealthStat] = 0
							KillActor(AI, Null)
						EndIf
					EndIf
				EndIf
			Else
				AI\Underwater = 1
			EndIf

			; Update AI
			If AI\RNID = -1
				AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
				; Wait mode
				If AI\AIMode = AI_Wait
					; Look for targets now and then
					If Rand(1, 10) = 1 Then AILookForTargets(AI)
				; Patrol mode
				ElseIf AI\AIMode = AI_Patrol Or AI\AIMode = AI_Run
					; Arrived - choose next waypoint
					If XDist# <= 2.0 And ZDist# <= 2.0
						; Set running state
						If AI\AIMode = AI_Run
							AI\IsRunning = True
						Else
							AI\IsRunning = False
						EndIf
						; Find auto-movement range if there is one
						SpawnRange# = 0.0
						For i = 0 To 999
							If AInstance\Area\SpawnWaypoint[i] = AI\CurrentWaypoint
								SpawnRange# = AInstance\Area\SpawnRange#[i]
								Exit
							EndIf
						Next
						; Auto-move within an area
						If SpawnRange# >= 5.0
							AI\DestX# = AInstance\Area\WaypointX#[AI\CurrentWaypoint] + Rnd#(-SpawnRange#, SpawnRange#)
							AI\DestZ# = AInstance\Area\WaypointZ#[AI\CurrentWaypoint] + Rnd#(-SpawnRange#, SpawnRange#)
						; Follow waypoints
						Else
							AI\Y# = AInstance\Area\WaypointY#[AI\CurrentWaypoint] + Rnd#(-1.5, 1.5)
							AI\OldX# = AInstance\Area\WaypointX#[AI\CurrentWaypoint]
							AI\OldZ# = AInstance\Area\WaypointZ#[AI\CurrentWaypoint]
							If Rand(1, 2) = 1
								NextWP = AInstance\Area\NextWaypointA[AI\CurrentWaypoint]
								If NextWP > 1999 Then NextWP = AInstance\Area\NextWaypointB[AI\CurrentWaypoint]
							Else
								NextWP = AInstance\Area\NextWaypointB[AI\CurrentWaypoint]
								If NextWP > 1999 Then NextWP = AInstance\Area\NextWaypointA[AI\CurrentWaypoint]
							EndIf
							If NextWP > 1999 Then NextWP = AInstance\Area\PrevWaypoint[AI\CurrentWaypoint]
							If NextWP > 1999
								AI\AIMode = AI_Wait
							Else
								AI\DestX# = AInstance\Area\WaypointX#[NextWP] + Rnd#(-2.0, 2.0)
								AI\DestZ# = AInstance\Area\WaypointZ#[NextWP] + Rnd#(-2.0, 2.0)
								AI\CurrentWaypoint = NextWP
								; Waypoint pause
								If AInstance\Area\WaypointPause[NextWP] > 0
									AI\AIMode = AI_PatrolPause
									; I have decided to borrow an unrelated field of the actor instance
									; type, used only by the client, to hold the time at which the actor
									; reached the waypoint. This is to avoid the needless waste of memory
									; when the server has many thousands of active actors caused by adding
									; another field especially for this. The only other time this field
									; will be referenced by the server is below in the code for the
									; AI_PatrolPause mode.
									AI\FootstepPlayedThisCycle = MilliSecs()
								EndIf
							EndIf
						EndIf
					EndIf

					; Look for targets now and then
					If Rand(1, 10) = 1 Then AILookForTargets(AI)
				; Paused while on patrol mode
				ElseIf AI\AIMode = AI_PatrolPause
					If MilliSecs() - AI\FootstepPlayedThisCycle >= AInstance\Area\WaypointPause[AI\CurrentWaypoint] * 1000
						AI\AIMode = AI_Patrol
					Else
						; Look for targets now and then
						If Rand(1, 10) = 1 Then AILookForTargets(AI)
					EndIf
				; Attack mode
				ElseIf AI\AIMode = AI_Chase
					; Target dead
					If AI\AITarget <> Null
						If AI\AITarget\Attributes\Value[HealthStat] <= 0 Then AI\AITarget = Null
					EndIf

					; Lost target
					If AI\AITarget = Null
						AI\AIMode = AI_Patrol
						AI\DestX# = AInstance\Area\WaypointX#[AI\CurrentWaypoint]
						AI\DestZ# = AInstance\Area\WaypointZ#[AI\CurrentWaypoint]
						AI\IsRunning = False
					; Chase target
					Else
						; Target left game
						If AI\AITarget\RNID = 0 Or AI\AITarget\ServerArea <> AI\ServerArea
							AI\AITarget = Null
						; Target available - kick its arse!
						Else
							XDist# = AI\X# - AI\AITarget\X#
							ZDist# = AI\Z# - AI\AITarget\Z#
							Dist# = (XDist# * XDist#) + (ZDist# * ZDist#)
							CheckDist# = 4.0 + AI\Actor\Radius# + AI\AITarget\Actor\Radius#
							If Dist# > CheckDist# * CheckDist#
								AI\DestX# = AI\AITarget\X#
								AI\DestZ# = AI\AITarget\Z#
								AI\IsRunning = True
							Else
								AI\DestX# = AI\X#
								AI\DestZ# = AI\Z#
								AI\IsRunning = False
								; Attempt to hit target
								If MilliSecs() - AI\LastAttack >= CombatDelay
									Result = ActorAttack(AI, AI\AITarget)
									If Result = True Then AI\DestX# = AI\X# : AI\DestZ# = AI\Z#
								EndIf
							EndIf
						EndIf
					EndIf
				; Pet AI
				ElseIf AI\AIMode = AI_Pet
					; Move towards leader's position
					AI\DestX# = AI\Leader\X#
					AI\DestZ# = AI\Leader\Z#
					AI\Y# = AI\Y# + ((AI\Leader\Y# - AI\Y#) / 50.0)
					AI\IsRunning = AI\Leader\IsRunning
					; When close enough to leader, stop moving
					XDist# = AI\X# - AI\Leader\X#
					ZDist# = AI\Z# - AI\Leader\Z#
					Dist# = (XDist# * XDist#) + (ZDist# * ZDist#)
					CheckDist# = 5.0 + AI\Actor\Radius# + AI\Leader\Actor\Radius#
					If Dist# <= CheckDist# * CheckDist#
						AI\DestX# = AI\X#
						AI\DestZ# = AI\Z#
					EndIf

					; Keep updated with leader's target
					If AI\Actor\Aggressiveness < 3
						If AI\Leader\AITarget <> Null
							XDist# = AI\Leader\AITarget\X# - AI\Leader\X#
							ZDist# = AI\Leader\AITarget\Z# - AI\Leader\Z#
							Dist# = (XDist# * XDist#) + (ZDist# * ZDist#)
							If Dist# <= 1000.0
								AI\AITarget = AI\Leader\AITarget
								AI\AIMode = AI_PetChase
							EndIf
						EndIf
					EndIf
				; Pet AI attack mode
				ElseIf AI\AIMode = AI_PetChase
					; Keep updated with leader's target
					AI\AITarget = AI\Leader\AITarget
					If AI\AITarget <> Null Then AI\AIMode = AI_Pet

					; Check if leader is too far away
					XDist# = AI\X# - AI\Leader\X#
					ZDist# = AI\Z# - AI\Leader\Z#
					Dist# = (XDist# * XDist#) + (ZDist# * ZDist#)
					If Dist# > 3500.0 Then AI\AITarget = Null

					; Target dead
					If AI\AITarget <> Null
						If AI\AITarget\Attributes\Value[HealthStat] <= 0 Then AI\AITarget = Null
					EndIf

					; Lost target
					If AI\AITarget = Null
						AI\AIMode = AI_Pet
					; Chase target
					Else
						; Target left game
						If AI\AITarget\RNID = 0 Or AI\AITarget\ServerArea <> AI\ServerArea
							AI\AITarget = Null
						; Target available - attack it
						Else
							AI\DestX# = AI\AITarget\X#
							AI\DestZ# = AI\AITarget\Z#
							AI\IsRunning = True
							; Attempt to hit target
							If MilliSecs() - AI\LastAttack >= CombatDelay
								Result = ActorAttack(AI, AI\AITarget)
								If Result = True Then AI\DestX# = AI\X# : AI\DestZ# = AI\Z#
							EndIf
						EndIf
					EndIf
				EndIf
			EndIf
		EndIf
	Next

;****************************************************************
;****************************************************************
;****************************************************************
	; Tell all human actor instances about other actor instances in their area (not too frequently)
	
	AlsoUpdateMiddleRange% = 0
	If MilliSecs() - LastCompleteUpdate > CompleteUpdateMS
		AlsoUpdateMiddleRange% = 1
		LastCompleteUpdate = MilliSecs()
	EndIf;
				
	If Broadcast = True
		For AI.ActorInstance = Each ActorInstance
			If AI\RNID > 0
				AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
				A2.ActorInstance = AInstance\FirstInZone
				While A2 <> Null
								  
				  ;I changed these lines to verify the distance before make any position update
				  bUpdate% = 1;
				  ActorDistance# = (A2\X# - AI\X#)*(A2\X# - AI\X#) + (A2\Z# - AI\Z#)*(A2\Z# - AI\Z#)
				  If ActorDistance# > UpdateFarDistance    ; Don't update if Actor is Far Far Far away
					bUpdate% = 0
				  Else
				    If ActorDistance# < UpdateDistance  ; Update if is in view rage (small distance)
						bUpdate% = 1
					Else
					    bUpdate% = AlsoUpdateMiddleRange  ; If is in middle range then update only if timing is correct 
					EndIf
				  EndIf
				
  				  If A2\RuntimeID <= -1
					bUpdate% = 0;
				  EndIf

					If A2\RuntimeID > -1
					If bUpdate% = 1
						Pa$ = RCE_StrFromInt$(A2\RuntimeID, 2)
						Pa$ = Pa$ + RCE_StrFromFloat$(A2\X#) + RCE_StrFromFloat$(A2\Z#)
						Pa$ = Pa$ + RCE_StrFromInt$(A2\IsRunning, 1) + RCE_StrFromInt$(A2\WalkingBackward, 1)
						Pa$ = Pa$ + RCE_StrFromFloat$(A2\DestX#) + RCE_StrFromFloat$(A2\DestZ#)
						If A2\Mount <> Null
							Pa$ = Pa$ + RCE_StrFromInt$(A2\Mount\RuntimeID, 2)
						Else
							Pa$ = Pa$ + RCE_StrFromInt$(0, 2)
						EndIf
						If A2 = AI
							If EnergyStat > -1 Then Pa$ = Pa$ + RCE_StrFromInt$(A2\Attributes\Value[EnergyStat], 2)
						ElseIf A2\Actor\Environment = Environment_Fly
							If A2\AIMode < AI_Pet And A2\RNID < 1
								XDist# = Abs(A2\OldX# - A2\DestX#)
								ZDist# = Abs(A2\OldZ# - A2\DestZ#)
								TotalDist# = (XDist# * XDist#) + (ZDist# * ZDist#)
								XDist# = Abs(A2\X# - A2\DestX#)
								ZDist# = Abs(A2\Z# - A2\DestZ#)
								DoneDist# = (XDist# * XDist#) + (ZDist# * ZDist#)
								YPos# = A2\Y# + ((AInstance\Area\WaypointY#[A2\CurrentWaypoint] - A2\Y#) * (DoneDist# / TotalDist#))
							Else
								YPos# = A2\Y#
							EndIf
							Pa$ = Pa$ + RCE_StrFromFloat$(YPos#)
						EndIf
						RCE_Send(Host, AI\RNID, P_StandardUpdate, Pa$)
					EndIf
					EndIf
					A2 = A2\NextInZone
				Wend
			EndIf
		Next
	EndIf

End Function

; Changes the area of an actor instance
Function SetArea(A.ActorInstance, Ar.Area, Instance, Waypoint = -1, Portal = 0, X# = 0, Y# = 0, Z# = 0)

	; Check instance exists
	If Ar\Instances[Instance] = Null
		Instance = 0
		WriteLog(MainLog, "Error: Cannot put actor into instance #" + Str$(Instance) + " of " + Ar\Name$ + " as the instance does not exist")
	EndIf
	
	;set flag to ignore standard updates until client has notified us that it has completed the move
	;A\IgnoreUpdate = 1

	; Get old zone
	OldAr.AreaInstance = Object.AreaInstance(A\ServerArea)

	; Warp mount first
	If A\Mount <> Null Then SetArea(A\Mount, Ar, Instance, Waypoint, Portal)

	; Warp pets
	If OldAr <> Null
		Slaves = A\NumberOfSlaves
		Slave.ActorInstance = OldAr\FirstInZone
		While Slave <> Null And Slaves > 0
			If Slave\Leader = A
				SetArea(Slave, Ar, Instance, Waypoint, Portal)
				Slaves = Slaves - 1
			EndIf
			Slave = Slave\NextInZone
		Wend
	EndIf

	; Update players list if necessary
	If A\RNID > 0
		If OldAr <> Null
			If OldAr\Area = GameArea
				Name$ = A\Name$ + " (" + Str$(OldAr\ID) + ")"
				For i = 0 To CountGadgetItems(Game\PlayersList) - 1
					If GadgetItemText$(Game\PlayersList, i) = Name$
						RemoveGadgetItem(Game\PlayersList, i)
						Exit
					EndIf
				Next
			EndIf
		EndIf
		If Ar = GameArea Then AddGadgetItem(Game\PlayersList, A\Name$ + " (" + Str$(Instance) + ")")
	EndIf

	; If old and new zones are different
	; Added 'Or A\ServerArea = 0' to combat occasional non updating ServerArea when loggin in.
	If Ar\Instances[Instance] <> OldAr Or A\ServerArea = 0
		; Remove actor from old zone
		If OldAr <> Null
			If OldAr\FirstInZone = A
				OldAr\FirstInZone = A\NextInZone
			Else
				A2.ActorInstance = OldAr\FirstInZone
				While A2 <> Null
					If A2\NextInZone = A
						A2\NextInZone = A\NextInZone
						Exit
					EndIf
					A2 = A2\NextInZone
				Wend
			EndIf
			A\NextInZone = Null
		EndIf

		; Put actor into new zone
		A\ServerArea = Handle(Ar\Instances[Instance])
		A\NextInZone = Ar\Instances[Instance]\FirstInZone
		Ar\Instances[Instance]\FirstInZone = A
		A\Area$ = Ar\Name$
	EndIf

	; Set new position
	If Waypoint = -1
		; Portal
		If Portal > -1
			A\Yaw# = Ar\PortalYaw#[Portal]
			A\X# = Ar\PortalX#[Portal]
			A\Y# = Ar\PortalY#[Portal]
			A\Z# = Ar\PortalZ#[Portal]
			A\LastPortal = Portal
			A\LastPortalTime = MilliSecs()
		; Direct position
		Else
			A\X# = X#
			A\Y# = Y#
			A\Z# = Z#
		EndIf
	; Waypoint
	Else
		A\Yaw# = 0.0
		A\X# = Ar\WaypointX#[Waypoint]
		A\Y# = Ar\WaypointY#[Waypoint]
		A\Z# = Ar\WaypointZ#[Waypoint]
		A\CurrentWaypoint = Waypoint
		A\LastPortal = 0
	EndIf

	; Reset target
	A\AITarget = Null

	; Set new position
	A\DestX# = A\X#
	A\DestZ# = A\Z#
	A\OldX# = A\X#
	A\OldZ# = A\Z#

	; Actor is human
	If A\RNID > 0
		; Run entry/exit scripts
		If Ar\EntryScript$ <> "" Then ThreadScript(Ar\EntryScript$, "Main", Handle(A), 0)
		If OldAr <> Null
			If OldAr\Area\ExitScript$ <> "" Then ThreadScript(OldAr\Area\ExitScript$, "Main", Handle(A), 0)
		EndIf

		; Tell him he's changed zone
		Pa$ = RCE_StrFromFloat$(A\X#) + RCE_StrFromFloat$(A\Y#) + RCE_StrFromFloat$(A\Z#) + RCE_StrFromFloat$(A\Yaw#) + RCE_StrFromInt$(Ar\PvP, 1) + RCE_StrFromInt$(Ar\Gravity, 2)
		Pa$ = Pa$ + RCE_StrFromInt$(A\ServerArea, 4) + RCE_StrFromInt$(Ar\Instances[Instance]\CurrentWeather, 1) + RCE_StrFromInt$(Len(A\Area$), 1) + A\Area$
		RCE_Send(Host, A\RNID, P_ChangeArea, Pa$, True)

		; Tell him about any dropped items in this zone
		For D.DroppedItem = Each DroppedItem
			If D\ServerHandle = A\ServerArea
				Pa$ = RCE_StrFromInt$(D\Amount, 2) + RCE_StrFromFloat$(D\X#) + RCE_StrFromFloat$(D\Y#) + RCE_StrFromFloat$(D\Z#)
				Pa$ = Pa$ + RCE_StrFromInt$(Handle(D), 4) + ItemInstanceToString$(D\Item)
				RCE_Send(Host, A\RNID, P_InventoryUpdate, "D" + Pa$, True)
			EndIf
		Next
	EndIf

	; If the new area is different to the old
	If Ar\Instances[Instance] <> OldAr
		; If this actor still belongs to a spawnpoint, remove him
		If A\SourceSP > -1
			If OldAr <> Null Then OldAr\Spawned[A\SourceSP] = OldAr\Spawned[A\SourceSP] - 1
			A\SourceSP = -1
		EndIf

		; Tell others about him / tell him about others (in the new zone)
		NewGuy$ = ActorInstanceToString$(A)
		A2.ActorInstance = Ar\Instances[Instance]\FirstInZone
		While A2 <> Null
			If A2\RuntimeID > -1
				If A2 <> A
					; Message to existing player about new player
					If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_NewActor, NewGuy$, True)
					; Message to new player about existing player
					If A\RNID > 0 Then SendQueued(Host, A\RNID, P_NewActor, ActorInstanceToString$(A2), True)
				EndIf
			EndIf
			A2 = A2\NextInZone
		Wend

		; Tell players in his old area that he has now left
		If OldAr <> Null
			A2.ActorInstance = OldAr\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_ActorGone, RCE_StrFromInt$(A\RuntimeID), True)
				A2 = A2\NextInZone
			Wend
		EndIf
	; If he's warped to the same area he was already in, tell players he has changed position
	Else
		Pa$ = RCE_StrFromInt$(A\RuntimeID, 2) + RCE_StrFromFloat$(A\X#) + RCE_StrFromFloat$(A\Y#) + RCE_StrFromFloat$(A\Z#) + RCE_StrFromInt$(0, 1)
		A2.ActorInstance = Ar\Instances[Instance]\FirstInZone
		While A2 <> Null
			If A2\RNID > 0
				If A2 <> A Then RCE_Send(Host, A2\RNID, P_RepositionActor, Pa$, True)
			EndIf
			A2 = A2\NextInZone
		Wend
	EndIf

End Function

; Removes an actor instance from a party
Function LeaveParty(AI.ActorInstance)

	P.Party = Object.Party(AI\PartyID)
	If P <> Null
		If P\Members = 1
			Delete(P)
		Else
			For i = 0 To 7
				If P\Player[i] = AI
					P\Player[i] = Null
					P\Members = P\Members - 1
				ElseIf P\Player[i] <> Null
					RCE_Send(Host, P\Player[i]\RNID, P_ChatMessage, Chr$(254) + AI\Name$ + " left the party", True)
					SendPartyUpdate(P\Player[i])
				EndIf
			Next
		EndIf
		AI\PartyID = 0
		ThreadScript("Party", "Leave", Handle(AI), 0)
	EndIf

End Function

; Gives instructions to a pet
Function CommandPet(AI.ActorInstance, Command$, Params$)

	Select Upper$(Command$)
		; Wait at current position
		Case "WAIT", "STAY"
			AI\AIMode = AI_PetWait
			AI\DestX# = AI\X#
			AI\DestZ# = AI\Z#
			AI\AITarget = Null
		; Follow leader
		Case "FOLLOW", "COME"
			AI\AIMode = AI_Pet
			AI\AITarget = Null
		; Pet to attack leader's target
		Case "ATTACK"
			If AI\Actor\Aggressiveness < 3
				If AI\Leader\AITarget <> Null
					AI\AITarget = AI\Leader\AITarget
					AI\AIMode = AI_PetChase
				EndIf
			EndIf
		; Rename pet
		Case "NAME"
			; Check pet name is valid
			NameValid = True
			Name$ = Upper$(Params$)
			F = ReadFile("Data\Server Data\Names Filter.txt")
			If F = 0 Then RuntimeError("Could not open Names Filter.txt!")
				While Eof(F) = False
					Banned$ = Trim$(ReadLine$(F))
					If Banned$ <> ""
						If Left$(Banned$, 1) <> ";"
							If Instr(Name$, Upper$(Banned$)) > 0 Then NameValid = False : Exit
						EndIf
					EndIf
				Wend
			CloseFile(F)

			; Set pet name
			If NameValid = True
				AI\Name$ = Params$
				Pa$ = RCE_StrFromInt$(AI\RuntimeID, 2) + RCE_StrFromInt$(Len(AI\Name$), 1) + AI\Name$ + AI\Tag$
				AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
				A2.ActorInstance = AInstance\FirstInZone
				While A2 <> Null
					If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_NameChange, Pa$, True)
					A2 = A2\NextInZone
				Wend
			EndIf
	End Select

End Function

; Makes aggressive NPCs look for and target other actors
Function AILookForTargets(AI.ActorInstance)

	If AI\Actor\Aggressiveness = 2
		AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
		A2.ActorInstance = AInstance\FirstInZone
		While A2 <> Null
			; Must have a faction rating under 50% to be attacked
			If AI\FactionRatings[A2\HomeFaction] < 150
				If A2\Actor\Aggressiveness <> 3
					If A2 <> AI
						XDist# = Abs(AI\X# - A2\X#)
						YDist# = Abs(AI\Y# - A2\Y#)
						ZDist# = Abs(AI\Z# - A2\Z#)
						Dist# = (XDist# * XDist#) + (YDist# * YDist#) + (ZDist# * ZDist#)
						If Dist# < Float#(AI\Actor\AggressiveRange * AI\Actor\AggressiveRange)
							AI\AIMode = AI_Chase
							AI\AITarget = A2
						EndIf
					EndIf
				EndIf
			EndIf
			A2 = A2\NextInZone
		Wend
	EndIf

End Function

; Makes an NPC get help attacking its target from nearby NPCs with whom it has a good faction rating
Function AICallForHelp(AI.ActorInstance)

	If AI\AITarget <> Null
		AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
		A2.ActorInstance = AInstance\FirstInZone
		While A2 <> Null
			If A2\Actor\Aggressiveness <> 3 And A2\Actor\Aggressiveness <> 0
				If A2\AIMode <> AI_Chase
					; Must have a faction rating of 90% or more to help, and not be a pet
					If A2\FactionRatings[AI\HomeFaction] >= 190
						If A2 <> AI And A2\Leader = Null
							XDist# = Abs(AI\X# - A2\X#)
							YDist# = Abs(AI\Y# - A2\Y#)
							ZDist# = Abs(AI\Z# - A2\Z#)
							Dist# = (XDist# * XDist#) + (YDist# * YDist#) + (ZDist# * ZDist#)
							If Dist# < Float#(A2\Actor\AggressiveRange * A2\Actor\AggressiveRange)
								A2\AIMode = AI_Chase
								A2\AITarget = AI\AITarget
							EndIf
						EndIf
					EndIf
				EndIf
			EndIf
			A2 = A2\NextInZone
		Wend
	EndIf

End Function