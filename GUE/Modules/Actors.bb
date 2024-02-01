; AI modes
Const AI_Wait        = 0
Const AI_Patrol      = 1
Const AI_Run         = 2
Const AI_Chase       = 3
Const AI_PatrolPause = 4
Const AI_Pet         = 5
Const AI_PetChase    = 6
Const AI_PetWait     = 7

; Speech sounds
Const Speech_Greet1       = 0
Const Speech_Greet2       = 1
Const Speech_Bye1         = 2
Const Speech_Bye2         = 3
Const Speech_Attack1      = 4
Const Speech_Attack2      = 5
Const Speech_Hit1         = 6
Const Speech_Hit2         = 7
Const Speech_RequestHelp  = 8
Const Speech_Death        = 9
Const Speech_FootstepDry  = 10
Const Speech_FootstepWet  = 11

; Environment types
Const Environment_Amphibious = 0
Const Environment_Swim       = 1
Const Environment_Fly        = 2
Const Environment_Walk       = 3

; Actor template
Dim ActorList.Actor(65535)
Type Actor
	Field ID
	Field Race$, Class$, Description$, StartArea$, StartPortal$
	Field Radius#          ; For server use, since the server is not aware of the details of the mesh itself
	Field Scale#           ; Actor scale, applied to the base mesh
	Field MeshIDs[7]       ; Two base meshes (for male/female) and six gubbins (items activate these when equipped)
	Field BeardIDs[4]      ; Beard meshes for males
	Field MaleHairIDs[4]   ; Allowed hair meshes for the male
	Field FemaleHairIDs[4] ; Allowed hair meshes for the female
	Field MaleFaceIDs[4]   ; Allowed face textures for male
	Field FemaleFaceIDs[4] ; Allowed face textures for female
	Field MaleBodyIDs[4]   ; Allowed body textures for the male
	Field FemaleBodyIDs[4] ; Allowed body textures for the female
	Field MSpeechIDs[15]   ; Male sound IDs for speech
	Field FSpeechIDs[15]   ; Female sound IDs for speech
	Field HairColours[15]  ; Values for hair vertex colouring
	Field BloodTexID       ; For blood particles
	Field Genders          ; 0 for normal (male and female), 1 for male only, 2 for female only, 3 for no genders
	Field Attributes.Attributes
	Field Resistances[19]    ; Damage type resistances
	Field MAnimationSet      ; The ID of the male animation set to use
	Field FAnimationSet      ; The ID of the female animation set to use
	Field Playable           ; Can a player be this actor?
	Field Rideable           ; Can this actor be ridden by another?
	Field Aggressiveness     ; Aggressiveness - 0 = passive, 1 = attack when provoked, 2 = attack on sight, 3 = no combat
	Field AggressiveRange    ; From how nearby will the actor detect targets?
	Field TradeMode          ; 0 = will not trade, 1 = trades for free (pack mules!), 2 = charges for trade (salesman)
	Field Environment        ; Whether actor walks, swims, flies, etc.
	Field InventorySlots     ; Short (up to 16 true/false flags) for the slots defined in Inventories.bb
	Field DefaultDamageType
	Field DefaultFaction     ; Initial home faction for instances of this actor
	Field XPMultiplier       ; How much experience another actor gets for killing an instance of this actor
	Field PolyCollision      ; True for polygonal collision instead of ellipsoid
End Type

; Actor instance
Dim RuntimeIDList.ActorInstance(65535)
Global LastRuntimeID = 0
Type ActorInstance
	Field Actor.Actor
	Field NextInZone.ActorInstance ; Linked list containing all actors in zone
	Field X#, Y#, Z#
	Field OldX#, OldZ#
	Field DestX#, DestZ#
	Field Yaw#
	Field WalkingBackward
	Field Area$, ServerArea, Account
	Field Name$, Tag$
	Field LastPortal, LastTrigger, LastPortalTime
	Field TeamID ; Used to allow scripting to put people together in teams
	Field PartyID, AcceptPending ; Holds the handle of a Party object (or 0 if the actor is not in a party)
	Field Gender ; 0 for male, 1 for female
	Field EN, CollisionEN, HatEN, ChestEN, WeaponEN, ShieldEN, ShadowEN, NametagEN, GubbinEN[5] ; HatEN will store the hair entity if a hat is not worn
	Field FaceTex, Hair, Beard, BodyTex ; Fixed throughout a character's life unless altered by scripting
	Field Level, XP, XPBarLevel
	Field HomeFaction               ; Faction this actor belongs to (0-100 with 0 meaning no faction)
	Field FactionRatings[99]        ; Individual ratings with each faction for this actor - start off as home faction defaults
	Field Attributes.Attributes     ; Replaces Actor\Attributes which is merely the default actor attributes
	Field Resistances[19]           ; Resistances against damage types
	Field Script$                   ; Script which executes when character is selected (for traders mainly)
	Field DeathScript$              ; Script which executes when actor is killed (NPCs only)
	Field Inventory.Inventory       ; The actor's inventory slots!
	Field Leader.ActorInstance      ; For slaves, pets, etc.
	Field NumberOfSlaves            ; Whether this actor owns any slaves (to speed up saving actor instances)
	Field Reputation
	Field Gold
	Field RNID                      ; RottNet ID (-1 for AI actors, 0 for not-in-game)
	Field RuntimeID                 ; Assigned by server
	Field AnimSeqs[149]             ; Animation sequences
	Field SourceSP, CurrentWaypoint, AIMode, AITarget.ActorInstance ; AI stuff
	Field Rider.ActorInstance, Mount.ActorInstance ; Mount riding
	Field IsRunning, LastAttack
	Field FootstepPlayedThisCycle   ; To prevent too many footstep noises! See UpdateActorInstances() in Default Project.bb
	Field ScriptGlobals$[9]
	Field KnownSpells[999]
	Field SpellLevels[999]
	Field MemorisedSpells[9]
	Field SpellCharge[999] ; How long until the spell is usable
	Field IsTrading ; 0 for not trading, 1 for trading with NPC, 2 for trading with pet, 3 for trading with player, 4/5 for accepted trading with player
	Field TradingActor.ActorInstance
	Field TradeResult$
	Field Underwater
	Field IgnoreUpdate   ;used to ignore standard update while waiting for client to complete actor moves
	
	;Strafing
	Field WalkingRight
	
	Field Active		; used for visibility update handling
	; Additional variables used when saving field in MySQL
	; These store the integer of the first field in each table
	Field Faction_ID
	Field Script_ID
	Field Spell_ID
	Field My_ID
	Field Memorised_ID
	Field Attribute_ID
	Field Resistance_ID	
	Field Account_ID
End Type

Type Party
	Field Members
	Field Player.ActorInstance[7]
End Type

Type QuestLog
	Field EntryName$[499]
	Field EntryStatus$[499]
	Field My_ID ; Required for MySQL
End Type

; Actor attributes (strength, dexterity, health, armour, whatever the user decides)
Global AttributeAssignment
Dim AttributeNames$(39)
Dim AttributeIsSkill(39) ; False for a stat (health, strength, armour), True for a skill (fishing, riding)
Dim AttributeHidden(39)
Type Attributes
	Field Value[39]
	Field Maximum[39]
	Field My_ID ; Required for MySQL
End Type

; Actor effect (buff)
Type ActorEffect
	Field Name$
	Field Owner.ActorInstance
	Field Attributes.Attributes
	Field CreatedTime, Length ; Time created and time it lasts in milliseconds (Length = 0 for infinite)
	Field IconTexID
End Type

; Factions
Dim FactionNames$(99)
Dim FactionDefaultRatings(99, 99)

; Finds an actor instance based on their RottNet ID
Function FindActorInstanceFromRNID.ActorInstance(RNID)

	For A.ActorInstance = Each ActorInstance
		If A\RNID = RNID Then Return A
	Next
	Return Null

End Function

; Finds an actor instance based on their name
Function FindActorInstanceFromName.ActorInstance(Name$)

	Name$ = Upper$(Name$)
	For A.ActorInstance = Each ActorInstance
		If Upper$(A\Name$) = Name$ Then Return A
	Next
	Return Null

End Function


; Finds a human actor instance based on their name
Function FindPlayerFromName.ActorInstance(Name$)

	Name$ = Upper$(Name$)
	For A.ActorInstance = Each ActorInstance
		If A\RNID > -1
			If Upper$(A\Name$) = Name$ Then Return A
		EndIf
	Next
	Return Null

End Function

; Write the data for an actor instance to a stream
Function WriteActorInstance(Stream, A.ActorInstance)

	; Actor instance data
	WriteShort Stream, A\Actor\ID
	WriteString Stream, A\Area$
	WriteString Stream, A\Name$
	WriteString Stream, A\Tag$
	WriteInt Stream, A\TeamID
	WriteFloat Stream, A\X#
	WriteFloat Stream, A\Y#
	WriteFloat Stream, A\Z#
	WriteByte Stream, A\Gender
	WriteInt Stream, A\XP
	WriteByte Stream, A\XPBarLevel
	WriteShort Stream, A\Level
	WriteShort Stream, A\FaceTex
	WriteShort Stream, A\Hair
	WriteShort Stream, A\Beard
	WriteShort Stream, A\BodyTex
	For i = 0 To 39
		WriteShort Stream, A\Attributes\Value[i]
		WriteShort Stream, A\Attributes\Maximum[i]
	Next
	For i = 0 To 19
		WriteShort Stream, A\Resistances[i]
	Next
	For i = 0 To 49
		WriteItemInstance(Stream, A\Inventory\Items[i])
		WriteShort Stream, A\Inventory\Amounts[i]
	Next
	WriteString Stream, A\Script$
	WriteString Stream, A\Script$
	WriteShort Stream, A\Reputation
	WriteInt Stream, A\Gold
	WriteByte Stream, A\NumberOfSlaves
	WriteByte Stream, A\HomeFaction
	For i = 0 To 99
		WriteByte Stream, A\FactionRatings[i]
	Next
	For i = 0 To 9
		WriteString Stream, A\ScriptGlobals$[i]
	Next
	For i = 0 To 999
		WriteShort Stream, A\KnownSpells[i]
		WriteShort Stream, A\SpellLevels[i]
	Next
	For i = 0 To 9
		WriteShort Stream, A\MemorisedSpells[i]
	Next

	; Data for any slaves
	Slaves = A\NumberOfSlaves
	While Slaves > 0
		For Slave.ActorInstance = Each ActorInstance
			If Slave\Leader = A
				WriteActorInstance(Stream, Slave)
				Slaves = Slaves - 1
			EndIf
		Next
	Wend

End Function

; Reads in actor instance data from a stream and returns a new instance
Function ReadActorInstance.ActorInstance(Stream)

	; This actor instance
	ActorID = ReadShort(Stream)
	; Actor no longer exists, read in data to keep offset correct, then return nothing
	If ActorList(ActorID) = Null
		A.ActorInstance = New ActorInstance
		A\Attributes = New Attributes
		A\Inventory = New Inventory
	; Actor exists
	Else
		A.ActorInstance = CreateActorInstance(ActorList(ActorID))
	EndIf

	; Read in data
	A\Area$      = ReadString$(Stream)
	A\Name$      = ReadString$(Stream)
	A\Tag$       = ReadString$(Stream)
	A\TeamID     = ReadInt(Stream)
	A\X# = ReadFloat#(Stream)
	A\Y# = ReadFloat#(Stream)
	A\Z# = ReadFloat#(Stream)
	A\Gender     = ReadByte(Stream)
	A\XP         = ReadInt(Stream)
	A\XPBarLevel = ReadByte(Stream)
	A\Level      = ReadShort(Stream)
	A\FaceTex    = ReadShort(Stream)
	A\Hair       = ReadShort(Stream)
	A\Beard      = ReadShort(Stream)
	A\BodyTex    = ReadShort(Stream)
	For i = 0 To 39
		A\Attributes\Value[i]   = ReadShort(Stream)
		A\Attributes\Maximum[i] = ReadShort(Stream)
	Next
	For i = 0 To 19
		A\Resistances[i] = ReadShort(Stream)
	Next
	For i = 0 To 49
		A\Inventory\Items[i]   = ReadItemInstance(Stream)
		A\Inventory\Amounts[i] = ReadShort(Stream)
	Next
	A\Script$        = ReadString$(Stream)
	A\DeathScript$   = ReadString$(Stream)
	A\Reputation     = ReadShort(Stream)
	A\Gold           = ReadInt(Stream)
	A\NumberOfSlaves = ReadByte(Stream)
	A\HomeFaction    = ReadByte(Stream)
	For i = 0 To 99
		A\FactionRatings[i] = ReadByte(Stream)
	Next
	For i = 0 To 9
		A\ScriptGlobals$[i] = ReadString$(Stream)
	Next
	For i = 0 To 999
		A\KnownSpells[i] = ReadShort(Stream)
		A\SpellLevels[i] = ReadShort(Stream)
	Next
	For i = 0 To 9
		A\MemorisedSpells[i] = ReadShort(Stream)
	Next

	; Slaves
	For i = 1 To A\NumberOfSlaves
		Slave.ActorInstance = ReadActorInstance(Stream)
		Slave\Leader = A
		Slave\AIMode = AI_Pet
	Next

	; If actor didn't exist, delete all slaves and return nothing
	If ActorList(ActorID) = Null
		FreeActorInstanceSlaves(A)
		Delete(A)
		Return Null
	; Return successfully created actor
	Else
		Return A
	EndIf

End Function

; Creates a new actor template
Function CreateActor.Actor()

	For i = 0 To 65535
		If ActorList(i) = Null
			A.Actor = New Actor
			A\ID = i
			ActorList(A\ID) = A
			A\Attributes = New Attributes
			For i = 0 To 39 : A\Attributes\Maximum[i] = 100 : Next
			For i = 0 To 7
				A\MeshIDs[i] = 65535
				If i <= 4
					A\BeardIDs[i]      = 65535
					A\MaleHairIDs[i]   = 65535
					A\FemaleHairIDs[i] = 65535
					A\MaleFaceIDs[i]   = 65535
					A\FemaleFaceIDs[i] = 65535
					A\MaleBodyIDs[i]   = 65535
					A\FemaleBodyIDs[i] = 65535
				EndIf
			Next
			For i = 0 To 11
				A\MSpeechIDs[i] = 65535
				A\FSpeechIDs[i] = 65535
			Next
			A\InventorySlots = $FFFFFFFFFFFFFFFF
			A\MaleBodyIDs[0] = 0
			A\FemaleBodyIDs[0] = 0
			A\MeshIDs[0] = 0
			A\MeshIDs[1] = 0
			A\Scale# = 1.0
			A\AggressiveRange = 50
			Return A
		EndIf
	Next
	Return Null

End Function

; Creates a new instance of an actor
Function CreateActorInstance.ActorInstance(Actor.Actor)

	If Actor = Null Then RuntimeError("Could not create actor instance - actor does not exist!")

	A.ActorInstance = New ActorInstance
	A\Attributes = New Attributes
	A\Inventory = New Inventory
	A\Actor = Actor
	A\Name$ = A\Actor\Race$
	A\HomeFaction = A\Actor\DefaultFaction
	For i = 0 To 99
		A\FactionRatings[i] = FactionDefaultRatings(A\HomeFaction, i)
	Next
	For i = 0 To 39
		A\Attributes\Value[i] = A\Actor\Attributes\Value[i]
		A\Attributes\Maximum[i] = A\Actor\Attributes\Maximum[i]
	Next
	For i = 0 To 19
		A\Resistances[i] = A\Actor\Resistances[i]
	Next
	For i = 0 To 9
		A\MemorisedSpells[i] = 5000 ; No spell memorised
	Next
	If A\Actor\Genders = 2 Then A\Gender = 1
	A\Level = 1
	A\RuntimeID = -1
	A\LastAttack = MilliSecs()
	A\SourceSP = -1
	A\LastTrigger = -1
	A\LastPortal = -1
	A\IgnoreUpdate = 0
	Return A

End Function

; Frees an actor instance
Function FreeActorInstance(A.ActorInstance)

	If A\RuntimeID > -1
		If RuntimeIDList(A\RuntimeID) = A Then RuntimeIDList(A\RuntimeID) = Null
	EndIf
	If A\Leader <> Null Then A\Leader\NumberOfSlaves = A\Leader\NumberOfSlaves - 1
	Delete(A)

End Function

; Frees all the slaves of an actor instance (RECURSIVE)
Function FreeActorInstanceSlaves(A.ActorInstance)

	For A2.ActorInstance = Each ActorInstance
		If A\NumberOfSlaves = 0 Then Exit
		If A2\Leader = A
			FreeActorInstanceSlaves(A2)
			FreeActorInstance(A2)
		EndIf
	Next

End Function

; Returns whether a specified actor has any allowed face textures or not (gender should be 1 for male, 2 for female, or 0 for either)
Function ActorHasFace(A.Actor, Gender = 0)

	For i = 0 To 4
		If Gender <> 2 And A\MaleFaceIDs[i] >= 0 And A\MaleFaceIDs[i] < 65535 Then Return True
		If Gender <> 1 And A\FemaleFaceIDs[i] >= 0 And A\FemaleFaceIDs[i] < 65535 Then Return True
	Next
	Return False

End Function

; Returns whether a specified actor has any allowed hair meshes or not (gender should be 1 for male, 2 for female, or 0 for either)
Function ActorHasHair(A.Actor, Gender = 0)

	For i = 0 To 4
		If Gender <> 2 And A\MaleHairIDs[i] >= 0 And A\MaleHairIDs[i] < 65535 Then Return True
		If Gender <> 1 And A\FemaleHairIDs[i] >= 0 And A\FemaleHairIDs[i] < 65535 Then Return True
	Next
	Return False

End Function

; Returns whether a specified actor has any allowed beard meshes or not
Function ActorHasBeard(A.Actor)

	If A\Genders = 2 Then Return False
	For i = 0 To 4
		If A\BeardIDs[i] >= 0 And A\BeardIDs[i] < 65535 Then Return True
	Next
	Return False

End Function

; Returns whether a specified actor has multiple possible body or head textures
Function ActorHasMultipleTextures(A.Actor, Gender)

	FoundBody = False
	; Male
	If Gender = 0
		For i = 0 To 4
			If A\MaleFaceIDs[i] >= 0 And A\MaleFaceIDs[i] < 65535
				Return True
			EndIf
			If A\MaleBodyIDs[i] >= 0 And A\MaleBodyIDs[i] < 65535
				If FoundBody = True
					Return True
				Else
					FoundBody = True
				EndIf
			EndIf
		Next
	; Female
	Else
		For i = 0 To 4
			If A\FemaleFaceIDs[i] >= 0 And A\FemaleFaceIDs[i] < 65535
				Return True
			EndIf
			If A\FemaleBodyIDs[i] >= 0 And A\FemaleBodyIDs[i] < 65535
				If FoundBody = True
					Return True
				Else
					FoundBody = True
				EndIf
			EndIf
		Next
	EndIf
	Return False

End Function

; Loads all actors from file
Function LoadActors(Filename$)

	Local Actors = 0

	F = ReadFile(Filename$)
	If F = 0 Then Return -1

		While Not Eof(F)
			A.Actor = New Actor
			A\Attributes = New Attributes
			A\ID = ReadShort(F)
			ActorList(A\ID) = A
			A\Race$ = ReadString$(F)
			A\Class$ = ReadString$(F)
			A\Description$ = ReadString$(F)
			A\StartArea$ = ReadString$(F)
			A\StartPortal$ = ReadString$(F)
			A\MAnimationSet = ReadShort(F)
			A\FAnimationSet = ReadShort(F)
			A\Scale# = ReadFloat(F)
			A\Radius# = ReadFloat(F)
			For i = 0 To 7  : A\MeshIDs[i] = ReadShort(F) : Next
			For i = 0 To 4  : A\BeardIDs[i] = ReadShort(F) : Next
			For i = 0 To 4  : A\MaleHairIDs[i] = ReadShort(F) : Next
			For i = 0 To 4  : A\FemaleHairIDs[i] = ReadShort(F) : Next
			For i = 0 To 4  : A\MaleFaceIDs[i] = ReadShort(F) : Next
			For i = 0 To 4  : A\FemaleFaceIDs[i] = ReadShort(F) : Next
			For i = 0 To 4  : A\MaleBodyIDs[i] = ReadShort(F) : Next
			For i = 0 To 4  : A\FemaleBodyIDs[i] = ReadShort(F) : Next
			For i = 0 To 15 : A\MSpeechIDs[i] = ReadShort(F) : Next
			For i = 0 To 15 : A\FSpeechIDs[i] = ReadShort(F) : Next
;			For i = 0 To 5  : A\HairColours[i] = ReadInt(F) : Next
			A\BloodTexID = ReadShort(F)
			For i = 0 To 39
				A\Attributes\Value[i] = ReadShort(F)
				A\Attributes\Maximum[i] = ReadShort(F)
			Next
			For i = 0 To 19
				A\Resistances[i] = ReadShort(F)
			Next
			A\Genders = ReadByte(F)
			A\Playable = ReadByte(F)
			A\Rideable = ReadByte(F)
			A\Aggressiveness = ReadByte(F)
			A\AggressiveRange = ReadInt(F)
			A\TradeMode = ReadByte(F)
			A\Environment = ReadByte(F)
			A\InventorySlots = ReadInt(F)
			A\DefaultDamageType = ReadByte(F)
			A\DefaultFaction = ReadByte(F)
			A\XPMultiplier = ReadInt(F)
			A\PolyCollision = ReadByte(F)
			Actors = Actors + 1
		Wend

	CloseFile(F)
	Return Actors

End Function

; Saves all actors to file
Function SaveActors(Filename$)

	F = WriteFile(Filename$)
	If F = 0 Then Return False

		For A.Actor = Each Actor
			WriteShort(F, A\ID)
			WriteString(F, A\Race$)
			WriteString(F, A\Class$)
			WriteString(F, A\Description$)
			WriteString(F, A\StartArea$)
			WriteString(F, A\StartPortal$)
			WriteShort(F, A\MAnimationSet)
			WriteShort(F, A\FAnimationSet)
			WriteFloat(F, A\Scale#)
			WriteFloat(F, A\Radius#)
			For i = 0 To 7  : WriteShort(F, A\MeshIDs[i]) : Next
			For i = 0 To 4  : WriteShort(F, A\BeardIDs[i]) : Next
			For i = 0 To 4  : WriteShort(F, A\MaleHairIDs[i]) : Next
			For i = 0 To 4  : WriteShort(F, A\FemaleHairIDs[i]) : Next
			For i = 0 To 4  : WriteShort(F, A\MaleFaceIDs[i]) : Next
			For i = 0 To 4  : WriteShort(F, A\FemaleFaceIDs[i]) : Next
			For i = 0 To 4  : WriteShort(F, A\MaleBodyIDs[i]) : Next
			For i = 0 To 4  : WriteShort(F, A\FemaleBodyIDs[i]) : Next
			For i = 0 To 15 : WriteShort(F, A\MSpeechIDs[i]) : Next
			For i = 0 To 15 : WriteShort(F, A\FSpeechIDs[i]) : Next
;			For i = 0 To 5  : WriteInt(F, A\HairColours[i]) : Next
			WriteShort(F, A\BloodTexID)
			For i = 0 To 39
				WriteShort(F, A\Attributes\Value[i])
				WriteShort(F, A\Attributes\Maximum[i])
			Next
			For i = 0 To 19
				WriteShort(F, A\Resistances[i])
			Next
			WriteByte(F, A\Genders)
			WriteByte(F, A\Playable)
			WriteByte(F, A\Rideable)
			WriteByte(F, A\Aggressiveness)
			WriteInt(F, A\AggressiveRange)
			WriteByte(F, A\TradeMode)
			WriteByte(F, A\Environment)
			WriteInt(F, A\InventorySlots)
			WriteByte(F, A\DefaultDamageType)
			WriteByte(F, A\DefaultFaction)
			WriteInt(F, A\XPMultiplier)
			WriteByte(F, A\PolyCollision)
		Next

	CloseFile F
	Return True

End Function

; Loads attribute names from file
Function LoadAttributes(Filename$)

	F = ReadFile(Filename$)
	If F = 0 Then Return False

		AttributeAssignment = ReadByte(F)
		For i = 0 To 39
			AttributeNames$(i) = ReadString$(F)
			AttributeIsSkill(i) = ReadByte(F)
			AttributeHidden(i) = ReadByte(F)
		Next

	CloseFile(F)
	Return True

End Function

; Saves attribute names to file
Function SaveAttributes(Filename$)

	F = WriteFile(Filename$)
	If F = 0 Then Return False

		WriteByte(F, AttributeAssignment)
		For i = 0 To 39
			WriteString(F, AttributeNames$(i))
			WriteByte(F, AttributeIsSkill(i))
			WriteByte(F, AttributeHidden(i))
		Next

	CloseFile(F)
	Return True

End Function

; Looks up an attribute number from the name
Function FindAttribute(Name$)

	Name$ = Upper$(Name$)
	For i = 0 To 39
		If Upper$(AttributeNames$(i)) = Name$ Then Return i
	Next
	Return -1

End Function

; Converts the important parts of an actor instance to a string to be sent over the network
Function ActorInstanceToString$(A.ActorInstance)

	Pa$ = RCE_StrFromInt$(A\ServerArea, 4) + RCE_StrFromInt$(A\RuntimeID, 2) + RCE_StrFromInt$(A\Level, 2) + RCE_StrFromInt$(A\XP, 4)
	Pa$ = Pa$ + RCE_StrFromInt$(A\Actor\ID, 2) + RCE_StrFromFloat$(A\X#) + RCE_StrFromFloat$(A\Y#) + RCE_StrFromFloat$(A\Z#) + RCE_StrFromFloat$(A\Yaw#)
	If A\RNID = -1 Then Pa$ = Pa$ + RCE_StrFromInt$(0, 1) Else Pa$ = Pa$ + RCE_StrFromInt$(1, 1)
	Pa$ = Pa$ + RCE_StrFromInt$(Len(A\Name$), 1) + A\Name$
	Pa$ = Pa$ + RCE_StrFromInt$(Len(A\Tag$), 1) + A\Tag$
	If A\Actor\Genders = 0 Then Pa$ = Pa$ + RCE_StrFromInt$(A\Gender, 1)
	Pa$ = Pa$ + RCE_StrFromInt$(A\Reputation, 2)
	Pa$ = Pa$ + RCE_StrFromInt$(A\FaceTex, 2) + RCE_StrFromInt$(A\Hair, 2) + RCE_StrFromInt$(A\BodyTex, 2) + RCE_StrFromInt$(A\Beard, 2)
	Pa$ = Pa$ + RCE_StrFromInt$(A\Attributes\Value[SpeedStat], 2) + RCE_StrFromInt$(A\Attributes\Maximum[SpeedStat], 2)
	Pa$ = Pa$ + RCE_StrFromInt$(A\Attributes\Value[HealthStat], 2) + RCE_StrFromInt$(A\Attributes\Maximum[HealthStat], 2)
	If A\Inventory\Items[SlotI_Weapon] <> Null
		Pa$ = Pa$ + RCE_StrFromInt$(A\Inventory\Items[SlotI_Weapon]\Item\ID, 2)
	Else
		Pa$ = Pa$ + RCE_StrFromInt$(65535, 2)
	EndIf
	If A\Inventory\Items[SlotI_Shield] <> Null
		Pa$ = Pa$ + RCE_StrFromInt$(A\Inventory\Items[SlotI_Shield]\Item\ID, 2)
	Else
		Pa$ = Pa$ + RCE_StrFromInt$(65535, 2)
	EndIf
	If A\Inventory\Items[SlotI_Hat] <> Null
		Pa$ = Pa$ + RCE_StrFromInt$(A\Inventory\Items[SlotI_Hat]\Item\ID, 2)
	Else
		Pa$ = Pa$ + RCE_StrFromInt$(65535, 2)
	EndIf
	If A\Inventory\Items[SlotI_Chest] <> Null
		Pa$ = Pa$ + RCE_StrFromInt$(A\Inventory\Items[SlotI_Chest]\Item\ID, 2)
	Else
		Pa$ = Pa$ + RCE_StrFromInt$(65535, 2)
	EndIf
	Pa$ = Pa$ + RCE_StrFromInt$(A\HomeFaction, 1)
	For i = 0 To 99
		Pa$ = Pa$ + RCE_StrFromInt$(A\FactionRatings[i], 1)
	Next

	Return Pa$

End Function

; Converts a string back into an actor instance after network transmission
Function ActorInstanceFromString.ActorInstance(Pa$)

	Local ServerArea = RCE_IntFromStr(Mid$(Pa$, 1, 4))
	If ServerArea <> CurrentAreaID Then Return Null

	RuntimeID = RCE_IntFromStr(Mid$(Pa$, 5, 2))
	ActorID = RCE_IntFromStr(Mid$(Pa$, 13, 2))
	A.ActorInstance = CreateActorInstance(ActorList(ActorID))
	A\RuntimeID = RuntimeID
	RuntimeIDList(RuntimeID) = A
	A\Level = RCE_IntFromStr(Mid$(Pa$, 7, 2))
	A\XP = RCE_IntFromStr(Mid$(Pa$, 9, 4))
	A\X# = RCE_FloatFromStr#(Mid$(Pa$, 15, 4))
	A\Y# = RCE_FloatFromStr#(Mid$(Pa$, 19, 4))
	A\Z# = RCE_FloatFromStr#(Mid$(Pa$, 23, 4))
	A\Yaw# = RCE_FloatFromStr#(Mid$(Pa$, 27, 4))
	A\DestX# = A\X#
	A\DestZ# = A\Z#
	A\RNID = RCE_IntFromStr(Mid$(Pa$, 31, 1)) ; 1 if human, 0 if AI
	NameLen = RCE_IntFromStr(Mid$(Pa$, 32, 1))
	A\Name$ = Mid$(Pa$, 33, NameLen)
	Offset = 33 + NameLen
	NameLen = RCE_IntFromStr(Mid$(Pa$, Offset, 1))
	A\Tag$ = Mid$(Pa$, Offset + 1, NameLen)
	Offset = Offset + 1 + NameLen
	If A\Actor\Genders = 0 Then A\Gender = RCE_IntFromStr(Mid$(Pa$, Offset, 1)) : Offset = Offset + 1
	A\Reputation = RCE_IntFromStr(Mid$(Pa$, Offset, 2))
	A\FaceTex = RCE_IntFromStr(Mid$(Pa$, Offset + 2, 2))
	A\Hair    = RCE_IntFromStr(Mid$(Pa$, Offset + 4, 2))
	A\BodyTex = RCE_IntFromStr(Mid$(Pa$, Offset + 6, 2))
	A\Beard   = RCE_IntFromStr(Mid$(Pa$, Offset + 8, 2))
	A\Attributes\Value[SpeedStat] = RCE_IntFromStr(Mid$(Pa$, Offset + 10, 2))
	A\Attributes\Maximum[SpeedStat] = RCE_IntFromStr(Mid$(Pa$, Offset + 12, 2))
	A\Attributes\Value[HealthStat] = RCE_IntFromStr(Mid$(Pa$, Offset + 14, 2))
	A\Attributes\Maximum[HealthStat] = RCE_IntFromStr(Mid$(Pa$, Offset + 16, 2))
	WeaponID = RCE_IntFromStr(Mid$(Pa$, Offset + 18, 2))
	ShieldID = RCE_IntFromStr(Mid$(Pa$, Offset + 20, 2))
	HatID = RCE_IntFromStr(Mid$(Pa$, Offset + 22, 2))
	ChestID = RCE_IntFromStr(Mid$(Pa$, Offset + 24, 2))
	If WeaponID < 65535 Then A\Inventory\Items[SlotI_Weapon] = CreateItemInstance(ItemList(WeaponID))
	If ShieldID < 65535 Then A\Inventory\Items[SlotI_Shield] = CreateItemInstance(ItemList(ShieldID))
	If HatID < 65535 Then A\Inventory\Items[SlotI_Hat] = CreateItemInstance(ItemList(HatID))
	If ChestID < 65535 Then A\Inventory\Items[SlotI_Chest] = CreateItemInstance(ItemList(ChestID))
	A\HomeFaction = RCE_IntFromStr(Mid$(Pa$, Offset + 26, 1))
	Offset = Offset + 27
	For i = 0 To 99
		A\FactionRatings[i] = RCE_IntFromStr(Mid$(Pa$, Offset + i, 1))
	Next

	Return A

End Function

; Returns True/False for a single bit in an int (numbered from 0)
Function GetFlag(TheInt, Flag)

	Return (TheInt Shr Flag) And 1

End Function

; Returns the number of used slots in a quest log
Function CountQuests(Q.QuestLog)

	Num = 0
	For i = 0 To 499
		If Q\EntryName$[i] <> "" Then Num = Num + 1
	Next
	Return Num

End Function

; Loads faction data from file
Function LoadFactions(Filename$)

	Factions = 0

	F = ReadFile(Filename$)
	If F = 0 Then Return -1

		For i = 0 To 99
			FactionNames$(i) = ReadString$(F)
			If Len(FactionNames$(i)) > 0 Then Factions = Factions + 1
		Next

		For i = 0 To 99
			For j = 0 To 99
				FactionDefaultRatings(i, j) = ReadByte(F)
			Next
		Next

	CloseFile(F)
	Return Factions

End Function

; Saves faction data to file
Function SaveFactions(Filename$)

	F = WriteFile(Filename$)
	If F = 0 Then Return False

		For i = 0 To 99
			WriteString(F, FactionNames$(i))
		Next

		For i = 0 To 99
			For j = 0 To 99
				WriteByte(F, FactionDefaultRatings(i, j))
			Next
		Next

	CloseFile(F)
	Return True

End Function

; Gives a known spell (ability) to an actor instance (SERVER ONLY!)
Function AddSpell(AI.ActorInstance, SpellID, Lvl = 1)

	; Find a free slot
	For i = 0 To 999
		If AI\SpellLevels[i] <= 0
			; Add the spell
			AI\KnownSpells[i] = SpellID
			AI\SpellLevels[i] = Lvl
			; If they are a player in game, tell them
			If AI\RNID > 0
				Sp.Spell = SpellsList(SpellID)
				Pa$ = RCE_StrFromInt$(Lvl, 2) + RCE_StrFromInt$(Sp\ThumbnailTexID, 2) + RCE_StrFromInt$(Sp\RechargeTime, 2)
				Pa$ = Pa$ + RCE_StrFromInt$(Len(Sp\Name$), 2) + Sp\Name$ + RCE_StrFromInt$(Len(Sp\Description$), 2) + Sp\Description$
				RCE_Send(Host, PeerToHost, P_KnownSpellUpdate, "A" + Pa$, True)
			EndIf
			; Done
			Exit
		EndIf
	Next

End Function

; Removes a known spell (ability) from an actor instance (SERVER ONLY!)
Function DeleteSpell(AI.ActorInstance, ID)

	; Remove
	Sp.Spell = SpellsList(AI\KnownSpells[ID])
	AI\KnownSpells[ID] = 0
	AI\SpellLevels[ID] = 0
	For i = 0 To 9
		If AI\MemorisedSpells[i] = ID Then AI\MemorisedSpells[i] = 5000
	Next

	; If they are a player in game, tell them
	If AI\RNID > 0 And Sp <> Null Then RCE_Send(Host, PeerToHost, P_KnownSpellUpdate, "D" + Sp\Name$, True)
End Function