Global LastAttack, AttackTarget
Global CombatDelay
Global DamageInfoStyle

Type BloodSpurt
	Field EmitterEN
	Field Timer
End Type

Type FloatingNumber
	Field EN
	Field Lifespan#
End Type

; Attacks target if the player is able to
Function UpdateCombat()

	; If I have a human target and I'm not riding a mount
	If PlayerTarget > 0 And Me\Attributes\Value[HealthStat] > 0 And AttackTarget = True And Me\Mount = Null
		A.ActorInstance = Object.ActorInstance(PlayerTarget)
		
		If A\Attributes\Value[HealthStat] < 1
			PlayerTarget = 0
			HideEntity(ActorSelectEN)			; Replace through clean function
			DestroyCharInteractionWindow()
			If useClickMovement then ShowEntity(ClickMarkerEN)			; Replace through clean function
			Return
		EndIf
		
		; Get allowed range
		MaxRange# = 4.0
		If Me\Inventory\Items[SlotI_Weapon] <> Null
			If Me\Inventory\Items[SlotI_Weapon]\Item\WeaponType = W_Ranged
				If Me\Inventory\Items[SlotI_Weapon]\ItemHealth > 0 Then MaxRange# = Me\Inventory\Items[SlotI_Weapon]\Item\Range# - 0.5
			EndIf
		EndIf

		; If it's in range
		Dist# = EntityDistance#(Me\CollisionEN, A\CollisionEN)
		If Dist# < MaxRange# + ((A\Actor\Radius# + Me\Actor\Radius#) * 0.05)
			; Stop moving
			Me\DestX# = EntityX#(Me\CollisionEN)
			Me\DestZ# = EntityZ#(Me\CollisionEN)

			; Face target
			PointEntity Me\CollisionEN, A\CollisionEN
			RotateEntity Me\CollisionEN, 0.0, EntityYaw#(Me\CollisionEN) + 180.0, 0.0

			; Attack if enough time elapsed
			If MilliSecs() - LastAttack > CombatDelay
				; Tell server
				RCE_Send(Connection, PeerToHost, P_AttackActor, RCE_StrFromInt$(A\RuntimeID, 2), True)
				LastAttack = MilliSecs()
			EndIf
		EndIf

		; Chase it
		If Dist# > MaxRange# + ((A\Actor\Radius# + Me\Actor\Radius#) * 0.05) - 2.0
			If CurrentSeq(Me) < Anim_DefaultAttack Or Animating(Me\EN) = False
				SetDestination(Me, EntityX#(A\CollisionEN), EntityZ#(A\CollisionEN), EntityY#(A\CollisionEN))
			EndIf
		EndIf
	EndIf

	; Update blood spurts
	For B.BloodSpurt = Each BloodSpurt
		If MilliSecs() - B\Timer > 600
			RP_KillEmitter(B\EmitterEN, False, False)
			Delete(B)
		EndIf
	Next

End Function

; Loads combat settings from file
Function LoadCombat()

	F = ReadFile("Data\Game Data\Combat.dat")
	If F = 0 Then RuntimeError("Could not open Data\Game Data\Combat.dat!")
		CombatDelay = ReadShort(F)
		DamageInfoStyle = ReadByte(F)
	CloseFile(F)
	LastAttack = MilliSecs()

	; Replace blood texture IDs with RottParticles config handles
	For A.Actor = Each Actor
		Tex = GetTexture(A\BloodTexID, True)
		If Tex > 0
			A\BloodTexID = RP_LoadEmitterConfig("Data\Emitter Configs\Blood.rpc", Tex, Cam)
		Else
			A\BloodTexID = 0
		EndIf
	Next

End Function

; Plays an actor's attack animation
Function AnimateActorAttack(A.ActorInstance)

	; Choose animation and play it
	If A\Inventory\Items[SlotI_Weapon] = Null
		Anim = Anim_DefaultAttack
	Else
		Select A\Inventory\Items[SlotI_Weapon]\Item\WeaponType
			Case W_OneHand : Anim = Anim_RightAttack
			Case W_TwoHand : Anim = Anim_TwoHandAttack
			Case W_Ranged
				If A\Gender = 0
					AS.AnimSet = AnimList(A\Actor\MAnimationSet)
				Else
					AS.AnimSet = AnimList(A\Actor\FAnimationSet)
				EndIf
				Anim = FindAnimation(AS, A\Inventory\Items[SlotI_Weapon]\Item\RangedAnimation$)
				If Anim = -1 Then Anim = Anim_RangedAttack
		End Select
	EndIf
	PlayAnimation(A, 3, 0.5, Anim, False)

End Function

; Plays an actor's parry animation
Function AnimateActorParry(A.ActorInstance)

	; Choose animation and play it
	If A\Inventory\Items[SlotI_Shield] <> Null
		Anim = Anim_ShieldParry
	ElseIf A\Inventory\Items[SlotI_Weapon] = Null
		Anim = Anim_DefaultParry
	Else
		Select A\Inventory\Items[SlotI_Weapon]\Item\WeaponType
			Case W_OneHand : Anim = Anim_RightParry
			Case W_TwoHand : Anim = Anim_TwoHandParry
			Case W_Ranged : Anim = Anim_DefaultParry
		End Select
	EndIf
	PlayAnimation(A, 3, 0.5, Anim, False)

End Function

; Displays a combat damage message
Function CombatDamageOutput(AI.ActorInstance, Amount, DType$)

	; Chat message
	If DamageInfoStyle = 2
		Name$ = Trim$(AI\Name$)
		If Name$ = "" Then Name$ = AI\Actor\Race$
		; You hit him
		If Amount > 0
			Output(LanguageString$(LS_YouHit) + " " + Name$ + " " + LanguageString$(LS_For) + " " + Str$(Amount) + " " + DType$ + " " + LanguageString$(LS_DamageWow), 0, 255, 0)
		; He hit you
		ElseIf Amount < 0
			Output(Name$ + " " + LanguageString$(LS_HitsYou) + " " + Str$(-Amount) + " " + DType$ + " " + LanguageString$(LS_DamageWow), 255, 0, 0)
		; Miss
		Else
			; He missed
			If DType$ = "1"
				Output(Name$ + " " + LanguageString$(LS_AttacksYouMisses), 0, 0, 255)
			; You missed
			Else
				Output(LanguageString$(LS_YouAttack) + " " + Name$ + " " + LanguageString$(LS_AndMiss), 0, 0, 255)
			EndIf
		EndIf
	; Floating number
	ElseIf DamageInfoStyle = 3
		; He hit you
		If Amount < 0
			CreateFloatingNumber(Me, Amount, 255, 0, 0)
		; You hit him
		ElseIf Amount > 0
			CreateFloatingNumber(AI, -Amount, 50, 255, 0)
		EndIf
	EndIf

End Function

; Creates a floating number
Function CreateFloatingNumber(AI.ActorInstance, Amount, R, G, B)

	F.FloatingNumber = New FloatingNumber
	F\EN = GY_Create3DText(0.0, 0.0, 1.0, 1.0, Len(Str$(Amount)), GY_TitleFont, 0, 0)
	GY_Set3DText(F\EN, Str$(Amount))
	ScaleEntity(F\EN, -0.45 * Len(Str$(Amount)), 0.75, 1, True)
	EntityBlend(F\EN, 3)
	EntityColor(F\EN, R, G, B)
	If AI\NametagEN <> 0
		PositionEntity F\EN, EntityX#(AI\CollisionEN), EntityY#(AI\NametagEN, True), EntityZ#(AI\CollisionEN)
	Else
		PositionEntity F\EN, EntityX#(AI\CollisionEN), EntityY#(AI\CollisionEN) + (MeshHeight#(AI\EN) * 0.025), EntityZ#(AI\CollisionEN)
	EndIf

End Function

; Updates all floating numbers
Function UpdateFloatingNumbers()

	For F.FloatingNumber = Each FloatingNumber
		; Move
		TranslateEntity F\EN, 0, 0.1 * Delta#, 0
		PointEntity F\EN, Cam

		; Update lifespan
		F\Lifespan# = F\Lifespan# + Delta#
		If F\Lifespan# > 50.0
			FreeEntity F\EN
			Delete F
		EndIf
	Next

End Function