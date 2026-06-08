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

Const InteractDist = 400 ; radius of 20

; Upper bound on RottNet connection IDs (RCE_StartHost cap at
; Server.bb:309 and 513 -- 5000 simultaneous players). RNID = 0 means
; "not in game"; RNID = -1 means "AI actor"; positive RNIDs in
; [1, MaxRNID] are online players. ActorByRNID is the O(1)
; sender-resolution index for inbound packets -- see FindActorInstanceFromRNID
; below and the maintenance hooks at:
;   * P_StartGame login (ServerNet.bb ~line 2088) -- populate slot.
;   * P_Disconnect logout (ServerNet.bb ~line 1960) -- clear slot.
;   * FreeActorInstance (below)                    -- clear slot.
; The pre-index implementation walked the entire global ActorInstance
; list on every inbound packet; with hundreds of NPCs + spawned mobs
; per loaded zone in a typical project, that was the dominant per-tick
; cost on the server.
Const MaxRNID = 5000
Dim ActorByRNID.ActorInstance(MaxRNID)

; Head of the global online-player linked list (see
; ActorInstance.NextOnlinePlayer). Walked by every chat-broadcast
; loop and the per-tick standard-update broadcast in place of the
; old `For Each ActorInstance / If A2\RNID > 0` filter.
Global FirstOnlinePlayer.ActorInstance = Null

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
	; Linked list containing every CURRENTLY-online player (RNID > 0).
	; Walked by the 7 broadcast loops in ServerNet.bb (chat: /yell /gm
	; /g /pm /allplayers /warpother) and the per-tick standard-update
	; broadcast in GameServer.bb's UpdateActorInstances. Replaces a
	; `For Each ActorInstance / If A2\RNID > 0` walk that scaled with
	; total actor count (NPCs + spawned mobs + pets + mounts + offline
	; characters) -- the per-tick site was the dominant cost. Mirrors
	; the FirstInZone / NextInZone pattern used per-AreaInstance.
	; Maintained at the same three lifecycle hooks as ActorByRNID
	; (login / logout / FreeActorInstance); see Actors.bb's helper
	; functions and ServerNet.bb's P_StartGame / P_Disconnect handlers.
	Field NextOnlinePlayer.ActorInstance
	; Linked list of this actor's slaves (pets / mounts / summons).
	; Head is FirstSlave; chained via Slave\NextSlave on each slave.
	; Replaces `For Each ActorInstance / If X\Leader = this` walks
	; (Actors.bb's WriteActorInstance + FreeActorInstanceSlaves,
	; GameServer.bb's pet-aggro broadcast, MySQL.bb's My_SaveActorInstance,
	; ServerNet.bb's /pet command + inventory pet-validation walk).
	; Maintained by SlaveLink / SlaveUnlink helpers and at the three
	; sites that mutate \Leader: load-from-stream (Actors.bb's
	; ReadActorInstance), load-from-DB (MySQL.bb's
	; My_LoadActorInstance), and BVM_SETLEADER (handles both
	; assignment and clearing). FreeActorInstance unlinks defensively.
	Field FirstSlave.ActorInstance
	Field NextSlave.ActorInstance
	Field X#, Y#, Z#
	Field OldX#, OldZ#
	Field DestX#, DestZ#
	; Timestamp (MilliSecs) of the last P_StandardUpdate the server
	; accepted from this actor. Used to bound per-packet movement
	; deltas against actor Speed so the client can't teleport-hack.
	Field LastPosUpdateMs%
	Field Yaw#
	Field WalkingBackward
	Field Area$, ServerArea, Account
	Field Name$, Tag$
	; LastPortal is a portal index 0..99 within an Area's portal list, so
	; it only makes sense paired with the area it was set in. LastPortalArea
	; stores Handle(Ar) of that area; the portal-lock check in Server.bb
	; compares both (Ar, i) and the time window. Without the area component,
	; an actor who entered Area B via waypoint/script while holding a stale
	; LastPortal=5 from Area A would be falsely locked out of Area B's
	; portal #5 (different physical portal), and conversely the AI spawn
	; path's deliberate LastPortal=0 stamp would bleed across areas.
	Field LastPortal, LastTrigger, LastPortalTime, LastPortalArea
	; Transient companion of LastPortalArea: the area's name string,
	; persisted across save/load (Handle() is process-local and cannot
	; survive a server restart). When non-empty AND LastPortalArea = 0,
	; the portal-walk in Server.bb resolves the name to a live Handle
	; once at first opportunity. Cleared back to "" when LastPortal
	; gets reset to -1.
	Field LastPortalAreaName$
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
	Field FootstepPlayedThisCycle   ; To prevent too many footstep noises! See UpdateActorInstances() in Client.bb
	Field ScriptGlobals$[9]
	Field KnownSpells[999]
	Field SpellLevels[999]
	Field MemorisedSpells[9]
	Field SpellCharge[999] ; How long until the spell is usable -- indexed by spell ID (matches SpellsList)
	; Server-side cooldown floor: timestamp of the last P_SpellUpdate "F"
	; this actor was allowed to process. Prevents same-tick spell spam
	; against zero-RechargeTime spells (a legal but cheese-prone setting
	; in the spell data).
	Field LastSpellFireMs
	Field IsTrading ; 0 for not trading, 1 for trading with NPC, 2 for trading with pet, 3 for trading with player, 4/5 for accepted trading with player
	Field TradingActor.ActorInstance
	Field TradeResult$
	; Server-authoritative per-slot trade offer state. Populated by
	; each P_UpdateTrading packet from this actor (slot index 0..31,
	; value = amount of Inventory[SlotI_Backpack + i] offered). Used
	; on accept to ignore the client-supplied accept-packet TradeResult$
	; amounts and only swap what was actually shown via UpdateTrading
	; -- prevents the dupe where the accept packet swaps a different
	; stack than what the trade UI displayed.
	Field TradeOfferedAmount[31]
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

; Finds an actor instance based on their RottNet ID. O(1) via the
; ActorByRNID index maintained at the three lifecycle hooks (login,
; logout, FreeActorInstance). Pre-index implementation walked every
; ActorInstance on every inbound packet -- the dominant per-tick
; cost on a server with hundreds of NPCs / spawned mobs across loaded
; zones.
;
; RNID = 0 (not in game) and RNID = -1 (AI actor) are NOT indexed --
; only positive RNIDs in [1, MaxRNID] are real connection IDs. Out-of-
; range or non-positive callers get Null, matching the previous walk's
; behavior for those values (the walk would match a 0 or -1 only if
; an ActorInstance happened to also be at that RNID, which only
; happens for never-logged-in player characters and NPCs -- callers
; that want those use FindActorInstanceFromName instead).
Function FindActorInstanceFromRNID.ActorInstance(RNID)

	If RNID < 1 Then Return Null
	; Small connection ids resolve through the O(1) ActorByRNID index.
	If RNID <= MaxRNID Then Return ActorByRNID(RNID)
	; RCEnet's RCE_GetMessageConnection returns iSender = (int)Event.peer — a
	; heap POINTER (> MaxRNID) — so M\FromID (and the \RNID set from it at
	; StartGame) overflows the O(1) index and the <=MaxRNID guard skips the
	; ActorByRNID population. The pointer is stable per connection, so fall
	; back to an O(n) scan of the unconditionally-set \RNID field. Without
	; this every client P_StandardUpdate / gameplay packet that resolves the
	; sender via FindActorInstanceFromRNID is silently dropped.
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
	For i = 0 To Slots_Inventory
		WriteItemInstance(Stream, A\Inventory\Items[i])
		WriteShort Stream, A\Inventory\Amounts[i]
	Next
	WriteString Stream, A\Script$
	WriteString Stream, A\DeathScript$
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

	; v1: LastPortal triad. Persisting these closes the bypass where
	; a logout/login cycle resets the portal-lock anti-cheat (Track
	; TT) to zero -- a returning player can immediately re-trigger
	; the portal they were placed at by their previous session.
	; LastPortalAreaName$ is the persistable companion of
	; LastPortalArea (Handle, process-local). Don't reference the
	; Area type here: Actors.bb is included by Client.bb too, and the
	; Area type only exists on the server. Writing the name string
	; and re-resolving lazily in Server.bb's portal walk keeps the
	; serializer pure.
	WriteString Stream, A\LastPortalAreaName$
	WriteShort Stream, A\LastPortal
	WriteInt Stream, A\LastPortalTime

	; Data for any slaves. Walk this leader's FirstSlave chain
	; instead of the global ActorInstance list. The chain replaces
	; the previous O(global_actors) walk filtered by `Leader = A`.
	Local Slave.ActorInstance = A\FirstSlave
	While Slave <> Null
		WriteActorInstance(Stream, Slave)
		Slave = Slave\NextSlave
	Wend

End Function

; Reads in actor instance data from a stream and returns a new instance
Function ReadActorInstance.ActorInstance(Stream)

	; This actor instance
	ActorID = ReadShort(Stream)
	; Bound ActorID before indexing ActorList — ReadShort is signed, and
	; ActorList is Dim'd 0..65535. A corrupted or tampered Accounts.dat
	; with a negative ID would otherwise write through a wild pointer
	; on every server boot. Track A bounded the same pattern in
	; LoadItems/Spells/Projectiles; round 3 caught this missed per-character
	; site. Treat out-of-range as "actor no longer exists" — the existing
	; placeholder path keeps the stream offset correct so subsequent
	; characters still parse.
	If ActorID < 0 Or ActorID > 65535
		A.ActorInstance = New ActorInstance
		A\Attributes = New Attributes
		A\Inventory = New Inventory
	; Actor no longer exists, read in data to keep offset correct, then return nothing
	ElseIf ActorList(ActorID) = Null
		A.ActorInstance = New ActorInstance
		A\Attributes = New Attributes
		A\Inventory = New Inventory
	; Actor exists
	Else
		A.ActorInstance = CreateActorInstance(ActorList(ActorID))
	EndIf

	; Read in data. ReadBoundedString$ caps every length-prefixed
	; string at a reasonable maximum -- a corrupted/tampered
	; Accounts.dat with a wild length prefix on Area/Name/Tag/Script/
	; DeathScript/ScriptGlobals[] would otherwise trigger multi-GB
	; allocations and silent zero-padding past EOF. LoadAccounts
	; already does this at the Account level (Track DD); extend the
	; same defence to the per-character payload.
	;
	; Caps:
	;   Area$ -- area names are typically <32 chars
	;   Name$ -- character names are <50 chars (server-enforced at
	;            P_CreateCharacter time)
	;   Tag$  -- tags are short labels
	;   Script$/DeathScript$ -- relative paths into Data\Scripts;
	;            keep parity with the 1024-byte action-bar/quest cap
	;            in LoadAccounts.
	A\Area$      = ReadBoundedString$(Stream, 256)
	A\Name$      = ReadBoundedString$(Stream, 256)
	A\Tag$       = ReadBoundedString$(Stream, 256)
	A\TeamID     = ReadInt(Stream)
	; Sanitise loaded position floats the same way the wire / BVM entry
	; points do (ClampWorldCoord in BVM_MOVEACTOR, ServerNet P_InventoryUpdate
	; "D", etc. -- see RCEnet.bb and the "Float sanitisation" doctrine in
	; CLAUDE.md). A corrupted or tampered Accounts.dat row could carry a NaN
	; / Inf / absurd X/Y/Z; loaded raw, that value flows straight into the
	; broadcast actor state P_StandardUpdate replicates to every client and
	; poisons spatial code (collision, LOD culling, EntityDistance#) for the
	; whole zone on every server boot. Every other field in this loader is
	; already bounded (strings, appearance indices, slave count, HomeFaction)
	; -- the position floats were the one gap. Clamp-to-0 (world origin) is a
	; recoverable degraded state; NaN is not.
	A\X# = ClampWorldCoord#(ReadFloat#(Stream))
	A\Y# = ClampWorldCoord#(ReadFloat#(Stream))
	A\Z# = ClampWorldCoord#(ReadFloat#(Stream))
	A\Gender     = ReadByte(Stream)
	A\XP         = ReadInt(Stream)
	A\XPBarLevel = ReadByte(Stream)
	A\Level      = ReadShort(Stream)
	A\FaceTex    = ReadShort(Stream)
	A\Hair       = ReadShort(Stream)
	A\Beard      = ReadShort(Stream)
	A\BodyTex    = ReadShort(Stream)
	; Bound the appearance indices against the [4]-slot per-Actor ID
	; arrays. Saved characters from before PR #199's P_CreateCharacter
	; clamp (or characters created with a misbehaving client) may have
	; out-of-range values that would OOB on every later appearance
	; lookup. Match the same shape as the receive-time clamps.
	If A\Gender < 0 Or A\Gender > 1 Then A\Gender = 0
	If A\FaceTex < 0 Or A\FaceTex > 4 Then A\FaceTex = 0
	If A\Hair < 0 Or A\Hair > 4 Then A\Hair = 0
	If A\Beard < 0 Or A\Beard > 4 Then A\Beard = 0
	If A\BodyTex < 0 Or A\BodyTex > 4 Then A\BodyTex = 0
	For i = 0 To 39
		A\Attributes\Value[i]   = ReadShort(Stream)
		A\Attributes\Maximum[i] = ReadShort(Stream)
	Next
	For i = 0 To 19
		A\Resistances[i] = ReadShort(Stream)
	Next
	For i = 0 To Slots_Inventory
		A\Inventory\Items[i]   = ReadItemInstance(Stream)
		A\Inventory\Amounts[i] = ReadShort(Stream)
	Next
	A\Script$        = ReadBoundedString$(Stream, 1024)
	A\DeathScript$   = ReadBoundedString$(Stream, 1024)
	A\Reputation     = ReadShort(Stream)
	A\Gold           = ReadInt(Stream)
	A\NumberOfSlaves = ReadByte(Stream)
	; Bound slaves at a sane cap. Without this, a corrupted byte
	; (ReadByte returns 0..255 unsigned -- the field can also be
	; *signed* on the wire for legacy saves, but Blitz3D treats
	; ReadByte as 0..255) drives an unbounded recursive descent
	; into ReadActorInstance with no inner EOF check, allocating
	; ActorInstance + Attributes + Inventory per iteration until
	; the heap is exhausted. The runtime pet cap is ~10; 32 is
	; comfortable headroom.
	If A\NumberOfSlaves < 0 Or A\NumberOfSlaves > 32
		A\NumberOfSlaves = 0
	EndIf
	A\HomeFaction    = ReadByte(Stream)
	; FactionNames$ / FactionDefaultRatings are Dim'd (99) -> 0..99.
	; A byte-wide HomeFaction can hold 100..255 (corrupt or stale save).
	; That value flows into FactionRatings[A\HomeFaction] and
	; FactionNames$(Actor\HomeFaction) at runtime -- both Blitz Dim
	; reads, neither bounds-checked. Clamp at the load site so every
	; downstream consumer can deref freely.
	If A\HomeFaction < 0 Or A\HomeFaction > 99 Then A\HomeFaction = 0
	For i = 0 To 99
		A\FactionRatings[i] = ReadByte(Stream)
	Next
	For i = 0 To 9
		A\ScriptGlobals$[i] = ReadBoundedString$(Stream, 1024)
	Next
	For i = 0 To 999
		A\KnownSpells[i] = ReadShort(Stream)
		A\SpellLevels[i] = ReadShort(Stream)
	Next
	For i = 0 To 9
		A\MemorisedSpells[i] = ReadShort(Stream)
		; MemorisedSpells stores an index into KnownSpells (Field[999])
		; or the sentinel 5000 ("no spell memorised"). ReadShort returns
		; -32768..32767; a corrupt slot bypassed the `<> 5000` guards in
		; ClientNet / Interface3D and indexed KnownSpells[OOB] -- Blitz
		; field-array OOB crashes the client every frame an action-bar
		; redraw walks the memorised list.
		If A\MemorisedSpells[i] < 0 Then A\MemorisedSpells[i] = 5000
		If A\MemorisedSpells[i] > 999 And A\MemorisedSpells[i] <> 5000 Then A\MemorisedSpells[i] = 5000
	Next

	; v1: LastPortal triad. Older saves (no magic header in
	; Accounts.dat) skip this block; the fields default to the
	; New-actor sentinel. The version is exposed via the
	; ACCOUNTS_LOAD_VERSION global set in LoadAccounts before any
	; per-actor reads. Re-resolving the area-name string back into
	; a live Handle is deferred to Server.bb's portal walk (Actors.bb
	; is shared with Client.bb and cannot reference the Area type).
	If ACCOUNTS_LOAD_VERSION% >= 1
		A\LastPortalAreaName$ = ReadBoundedString$(Stream, 256)
		A\LastPortal = ReadShort(Stream)
		A\LastPortalTime = ReadInt(Stream)
		A\LastPortalArea = 0
	EndIf

	; Slaves
	;
	; SlaveLink maintains the FirstSlave chain + NumberOfSlaves count.
	; The load loop reads N records from disk where N was the
	; previously-saved NumberOfSlaves; SlaveLink will INCREMENT
	; NumberOfSlaves on each call. The post-load count must match the
	; pre-load count, so reset to 0 before the loop and let SlaveLink
	; restore it.
	Local SavedSlaveCount% = A\NumberOfSlaves
	A\NumberOfSlaves = 0
	For i = 1 To SavedSlaveCount
		Slave.ActorInstance = ReadActorInstance(Stream)
		If Slave <> Null
			SlaveLink(A, Slave)
			Slave\AIMode = AI_Pet
		EndIf
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

	; Soft-fail on Null Actor template. Previously RuntimeError'd, which
	; crashed the server (any thread) or client (UI preview thread)
	; if any caller forgot the upstream ActorList(ActorID) <> Null
	; guard. The production-server callers all guard upstream (PR
	; #138-#144 sweep): Actors.bb (PreLoadSpawns + ActorInstanceFromString)
	; ServerNet.bb (P_CreateCharacter), MySQL.bb (LoadCharacter). The
	; client-side preview callers in MainMenu.bb mostly guard too,
	; except the change-race path -- a combo-box pick of a race that
	; was deleted from the project would crash. Defense-in-depth: log
	; the unexpected Null and Return Null. Callers that already check
	; the return value handle this naturally; callers that don't will
	; deref Null on the next line which is at least a localized crash
	; (not a server-wide RuntimeError) the runtime traps cleanly.
	If Actor = Null
		WriteLog(MainLog, "CreateActorInstance: called with Null Actor template; returning Null instead of RuntimeError-ing the whole process")
		Return Null
	EndIf

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
	A\LastPortalArea = 0
	A\LastPortalAreaName$ = ""
	A\IgnoreUpdate = 0
	Return A

End Function

; Links Slave under Leader: sets Slave\Leader, head-inserts into
; Leader\FirstSlave chain, increments Leader\NumberOfSlaves. The
; canonical replacement for bare `Slave\Leader = Leader` (which left
; the chain inconsistent) — every leader-assignment site should call
; this. Safe no-op on Null Slave or Null Leader.
;
; If Slave was already linked to a different leader, unlinks from
; the old chain first to avoid being in two chains simultaneously.
Function SlaveLink(Leader.ActorInstance, Slave.ActorInstance)

	If Leader = Null Or Slave = Null Then Return
	If Slave\Leader = Leader Then Return
	; Detach from any current leader before re-attaching.
	If Slave\Leader <> Null Then SlaveUnlink(Slave)
	Slave\Leader = Leader
	Slave\NextSlave = Leader\FirstSlave
	Leader\FirstSlave = Slave
	Leader\NumberOfSlaves = Leader\NumberOfSlaves + 1

End Function

; Removes Slave from its current Leader's chain, decrements
; NumberOfSlaves, clears Slave\Leader. Safe no-op when Slave has no
; leader (NPCs without a master).
Function SlaveUnlink(Slave.ActorInstance)

	If Slave = Null Then Return
	Local Leader.ActorInstance = Slave\Leader
	If Leader = Null Then Return
	; Walk-to-find-predecessor splice on the leader's chain.
	If Leader\FirstSlave = Slave
		Leader\FirstSlave = Slave\NextSlave
	Else
		Local Prev.ActorInstance = Leader\FirstSlave
		While Prev <> Null And Prev\NextSlave <> Slave
			Prev = Prev\NextSlave
		Wend
		If Prev <> Null Then Prev\NextSlave = Slave\NextSlave
	EndIf
	Slave\NextSlave = Null
	Slave\Leader = Null
	Leader\NumberOfSlaves = Leader\NumberOfSlaves - 1

End Function

; Inserts A at the head of the FirstOnlinePlayer chain. Idempotent
; via a presence check (a double-insert from a buggy caller would
; create a cycle in the chain). Called at login completion in
; ServerNet.bb P_StartGame.
Function OnlinePlayerInsert(A.ActorInstance)

	If A = Null Then Return
	; Skip if already in the chain. Walking the chain to check is O(n)
	; in online-player count; for the host's 5000-player cap this is
	; cheap enough at the login site (which is human-rate, not per-tick).
	Local Cursor.ActorInstance = FirstOnlinePlayer
	While Cursor <> Null
		If Cursor = A Then Return
		Cursor = Cursor\NextOnlinePlayer
	Wend
	A\NextOnlinePlayer = FirstOnlinePlayer
	FirstOnlinePlayer = A

End Function

; Removes A from the FirstOnlinePlayer chain. Walk-to-find-predecessor
; pattern (mirrors AreaInstance\FirstInZone removal in GameServer.bb).
; Safe to call when A isn't in the chain (no-op).
Function OnlinePlayerRemove(A.ActorInstance)

	If A = Null Then Return
	If FirstOnlinePlayer = Null Then Return
	If FirstOnlinePlayer = A
		FirstOnlinePlayer = A\NextOnlinePlayer
		A\NextOnlinePlayer = Null
		Return
	EndIf
	Local Prev.ActorInstance = FirstOnlinePlayer
	While Prev\NextOnlinePlayer <> Null
		If Prev\NextOnlinePlayer = A
			Prev\NextOnlinePlayer = A\NextOnlinePlayer
			A\NextOnlinePlayer = Null
			Return
		EndIf
		Prev = Prev\NextOnlinePlayer
	Wend

End Function

; Frees an actor instance
Function FreeActorInstance(A.ActorInstance)

	If A\RuntimeID > -1
		If RuntimeIDList(A\RuntimeID) = A Then RuntimeIDList(A\RuntimeID) = Null
	EndIf
	; ActorByRNID index cleanup. Only positive RNIDs are indexed, and
	; we only clear if the slot currently points to us -- a defensive
	; check that matches the RuntimeIDList pattern above and avoids
	; clobbering a relogin that happened to recycle the same RNID
	; (RottNet may reuse connection IDs).
	If A\RNID > 0 And A\RNID <= MaxRNID
		If ActorByRNID(A\RNID) = A Then ActorByRNID(A\RNID) = Null
	EndIf
	; FirstOnlinePlayer chain cleanup -- safe no-op when A wasn't an
	; online player (NPCs, never-logged-in characters).
	OnlinePlayerRemove(A)
	; FirstSlave chain cleanup. SlaveUnlink handles the NumberOfSlaves
	; decrement and clears Slave\Leader; safe no-op when A had no
	; leader.
	If A\Leader <> Null Then SlaveUnlink(A)
	; Also free this actor's own slave chain (defensive — typically
	; FreeActorInstanceSlaves was called first by the caller, but if
	; not, leaving dangling NextSlave pointers from this freed actor's
	; FirstSlave would corrupt the children's traversal). Clear without
	; recursing into Delete -- the surviving children are simply
	; orphaned (Leader = Null).
	Local Child.ActorInstance = A\FirstSlave
	Local ChildNext.ActorInstance = Null
	While Child <> Null
		ChildNext = Child\NextSlave
		Child\Leader = Null
		Child\NextSlave = Null
		; A surviving child was a pet of A (AI_Pet / AI_PetChase); its
		; leader is now gone. Reset to idle so the server AI tick's pet
		; branches don't deref the now-Null Leader -- a crash in debug
		; builds, or (per the non-short-circuit / skipped-__bbNullObjEx
		; behaviour) a silent walk to world origin in release. Mirrors
		; the AI_Wait reset BVM_SETLEADER performs at its SlaveUnlink site.
		Child\AIMode = AI_Wait
		Child\AITarget = Null
		Child = ChildNext
	Wend
	A\FirstSlave = Null
	Delete(A)

End Function

; Frees all the slaves of an actor instance (RECURSIVE)
;
; Head-capture pattern: each iteration reads A\FirstSlave fresh,
; recursively frees the child's slaves, then calls FreeActorInstance
; which SlaveUnlinks the child from A's chain (mutating A\FirstSlave).
; The next iteration's read picks up the new head. Safe because slave
; chains are per-leader and disjoint: the recursive call into Child's
; own FreeActorInstanceSlaves can only mutate Child's chain, never A's.
;
; Replaces the earlier For-Each + Restart-on-Delete pattern, which was
; needed when the walk was over the global ActorInstance list filtered
; by Leader; the chain walk doesn't need restart because the chain
; mutation is the natural termination condition.
Function FreeActorInstanceSlaves(A.ActorInstance)

	; Walk A's FirstSlave chain. Body recursively frees nested
	; slaves first (their FirstSlave chains), then calls
	; FreeActorInstance which SlaveUnlinks from A's chain and
	; Delete()s the slave. The unlink mutates A\FirstSlave, so capture
	; the head before each step rather than walking with a cursor that
	; could point at freed memory.
	While A\FirstSlave <> Null
		Local Child.ActorInstance = A\FirstSlave
		FreeActorInstanceSlaves(Child)
		FreeActorInstance(Child)
	Wend

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
			; ActorList is Dim'd 0..65535. ReadShort returns -32768..32767
			; (signed); a negative or any other out-of-range A\ID OOB-writes
			; into adjacent globals. Stop loading the rest of the file on
			; the first bad ID -- the partial state is still consistent.
			If A\ID < 0 Or A\ID > 65535
				Delete A\Attributes : Delete A
				Exit
			EndIf
			ActorList(A\ID) = A
			; Bound every length-prefixed string against a corrupted
			; Actors.dat (same shape as the data-loader sweep in PR #149).
			; Race / Class / area names are short identifiers; Description
			; is editor-authored flavor text; StartArea/StartPortal are
			; area + portal names.
			A\Race$ = ReadBoundedString$(F, 256)
			A\Class$ = ReadBoundedString$(F, 256)
			A\Description$ = ReadBoundedString$(F, 4096)
			A\StartArea$ = ReadBoundedString$(F, 256)
			A\StartPortal$ = ReadBoundedString$(F, 256)
			A\MAnimationSet = ReadShort(F)
			A\FAnimationSet = ReadShort(F)
			; AnimList is Dim'd 0..999. A ReadShort returns -32768..32767;
			; either side of 0..999 is a Blitz Dim OOB at every PlayAnimation
			; call (Animations.bb:44, Actors3D.bb:210, ClientNet.bb:683).
			; A missing-set slot is already tolerated via `If A = Null Then
			; Return`, but only after the index is in-range; clamp at load
			; so the downstream Null check is reachable.
			If A\MAnimationSet < 0 Or A\MAnimationSet > 999 Then A\MAnimationSet = 0
			If A\FAnimationSet < 0 Or A\FAnimationSet > 999 Then A\FAnimationSet = 0
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
			; DefaultFaction propagates to ActorInstance\HomeFaction
			; (CreateActorInstance line ~510). FactionNames$ /
			; FactionDefaultRatings are Dim'd (99). Clamp at load
			; so a malformed Actors.dat can't poison every new
			; ActorInstance with an OOB HomeFaction.
			If A\DefaultFaction < 0 Or A\DefaultFaction > 99 Then A\DefaultFaction = 0
			A\XPMultiplier = ReadInt(F)
			A\PolyCollision = ReadByte(F)
			Actors = Actors + 1
		Wend

	CloseFile(F)
	Return Actors

End Function

; Saves all actors to file via SafeWriteOpen/Commit (atomic). A crash
; mid-write previously truncated Actors.dat, losing the entire actor
; template catalog -- same Track FF rationale as SaveItems.
Function SaveActors(Filename$)

	Local Temp$ = SafeWriteOpen$(Filename$)
	F = WriteFile(Temp$)
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

	Return SafeWriteCommit%(Temp$, Filename$, F)

End Function

; Loads attribute names from file
Function LoadAttributes(Filename$)

	F = ReadFile(Filename$)
	If F = 0 Then Return False

		AttributeAssignment = ReadByte(F)
		For i = 0 To 39
			; Bound attribute display names against a corrupted
			; Attributes.dat (same shape as the data-loader sweep).
			AttributeNames$(i) = ReadBoundedString$(F, 256)
			AttributeIsSkill(i) = ReadByte(F)
			AttributeHidden(i) = ReadByte(F)
		Next

	CloseFile(F)
	Return True

End Function

; Saves attribute names to file via SafeWriteOpen/Commit (atomic).
Function SaveAttributes(Filename$)

	Local Temp$ = SafeWriteOpen$(Filename$)
	F = WriteFile(Temp$)
	If F = 0 Then Return False

		WriteByte(F, AttributeAssignment)
		For i = 0 To 39
			WriteString(F, AttributeNames$(i))
			WriteByte(F, AttributeIsSkill(i))
			WriteByte(F, AttributeHidden(i))
		Next

	Return SafeWriteCommit%(Temp$, Filename$, F)

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
	; Race the server announced is unknown to this client. Same DoS
	; surface as P_NewActor's mesh-load failure (see PR #128) but one
	; step earlier: CreateActorInstance previously RuntimeError'd on
	; a Null Actor template, so any P_NewActor / P_FetchCharacter for
	; a race the client doesn't have loaded would crash the client.
	; Reachable when the client is running an older Actors.dat than
	; the server (update-channel skew), or from a hostile/buggy
	; server. The lone caller (P_NewActor at ClientNet.bb:1456)
	; already drops the actor on a Null return.
	If ActorID < 0 Or ActorID > 65535 Or ActorList(ActorID) = Null
		WriteLog(MainLog, "ActorInstanceFromString: unknown ActorID " + ActorID + " (RuntimeID=" + RuntimeID + "), dropping actor")
		Return Null
	EndIf
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
	A\Reputation = RCE_SignedShortFromStr(Mid$(Pa$, Offset, 2))  ; signed: reputation can be negative
	A\FaceTex = RCE_IntFromStr(Mid$(Pa$, Offset + 2, 2))
	A\Hair    = RCE_IntFromStr(Mid$(Pa$, Offset + 4, 2))
	A\BodyTex = RCE_IntFromStr(Mid$(Pa$, Offset + 6, 2))
	A\Beard   = RCE_IntFromStr(Mid$(Pa$, Offset + 8, 2))
	; Bound the wire-derived appearance indices. Per-Actor Face/Body/
	; Hair/Beard ID arrays are Field [4]; an out-of-range value (server
	; sent an unbounded byte or short, or local data drift) walks past
	; the array. Same shape as PRs #198 / #199 / #200 for the other
	; per-character receive sites.
	If A\Gender < 0 Or A\Gender > 1 Then A\Gender = 0
	If A\FaceTex < 0 Or A\FaceTex > 4 Then A\FaceTex = 0
	If A\Hair < 0 Or A\Hair > 4 Then A\Hair = 0
	If A\BodyTex < 0 Or A\BodyTex > 4 Then A\BodyTex = 0
	If A\Beard < 0 Or A\Beard > 4 Then A\Beard = 0
	A\Attributes\Value[SpeedStat] = RCE_IntFromStr(Mid$(Pa$, Offset + 10, 2))
	A\Attributes\Maximum[SpeedStat] = RCE_IntFromStr(Mid$(Pa$, Offset + 12, 2))
	A\Attributes\Value[HealthStat] = RCE_IntFromStr(Mid$(Pa$, Offset + 14, 2))
	A\Attributes\Maximum[HealthStat] = RCE_IntFromStr(Mid$(Pa$, Offset + 16, 2))
	WeaponID = RCE_IntFromStr(Mid$(Pa$, Offset + 18, 2))
	ShieldID = RCE_IntFromStr(Mid$(Pa$, Offset + 20, 2))
	HatID = RCE_IntFromStr(Mid$(Pa$, Offset + 22, 2))
	ChestID = RCE_IntFromStr(Mid$(Pa$, Offset + 24, 2))
	; ItemList is Dim'd 65534; 65535 is the "no item" sentinel. Beyond
	; the sentinel check, the slot itself must be non-Null -- a deleted
	; or never-created item ID would otherwise drive CreateItemInstance
	; with a Null Item, which faults inside the constructor on
	; `I\Item\Attributes` deref. Range-check + Null-check at every
	; equipped-slot rehydrate.
	If WeaponID >= 0 And WeaponID < 65535 And ItemList(WeaponID) <> Null Then A\Inventory\Items[SlotI_Weapon] = CreateItemInstance(ItemList(WeaponID))
	If ShieldID >= 0 And ShieldID < 65535 And ItemList(ShieldID) <> Null Then A\Inventory\Items[SlotI_Shield] = CreateItemInstance(ItemList(ShieldID))
	If HatID >= 0 And HatID < 65535 And ItemList(HatID) <> Null Then A\Inventory\Items[SlotI_Hat] = CreateItemInstance(ItemList(HatID))
	If ChestID >= 0 And ChestID < 65535 And ItemList(ChestID) <> Null Then A\Inventory\Items[SlotI_Chest] = CreateItemInstance(ItemList(ChestID))
	A\HomeFaction = RCE_IntFromStr(Mid$(Pa$, Offset + 26, 1))
	; FactionNames$ / FactionDefaultRatings are Dim'd (99) -> 0..99;
	; FactionRatings is Field[99]. A wire byte can carry 100..255.
	; Clamp before downstream readers index either array.
	If A\HomeFaction < 0 Or A\HomeFaction > 99 Then A\HomeFaction = 0
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
			; Bound faction names against a corrupted Factions.dat.
			FactionNames$(i) = ReadBoundedString$(F, 256)
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

; Saves faction data to file via SafeWriteOpen/Commit (atomic).
Function SaveFactions(Filename$)

	Local Temp$ = SafeWriteOpen$(Filename$)
	F = WriteFile(Temp$)
	If F = 0 Then Return False

		For i = 0 To 99
			WriteString(F, FactionNames$(i))
		Next

		For i = 0 To 99
			For j = 0 To 99
				WriteByte(F, FactionDefaultRatings(i, j))
			Next
		Next

	Return SafeWriteCommit%(Temp$, Filename$, F)

End Function

; Setter for the FactionNames$ global. Exists so Strict modules (Loom's
; Composer.bb) can rename a faction; Strict mode disallows direct writes
; to Dim'd globals from inside Functions / Methods (see CLAUDE.md
; "Strict-mode Dim array assignment" feedback memory). Bounds-checked so a
; bad index can't scribble outside the 0..99 slot range.
Function SetFactionName(Index, Name$)
	If Index < 0 Or Index > 99 Then Return
	FactionNames$(Index) = Name$
End Function

; Non-Strict setter for the 100x100 FactionDefaultRatings grid. Strict
; callers in Modules/Loom/Composer.bb route through here per the Dim-write-
; from-Strict trap (same shape as SetFactionName above).
Function SetFactionRelation(FromIdx, ToIdx, Rating)
	If FromIdx < 0 Or FromIdx > 99 Then Return
	If ToIdx   < 0 Or ToIdx   > 99 Then Return
	FactionDefaultRatings(FromIdx, ToIdx) = Rating
End Function

; Non-Strict setters for the AttributeNames$ / AttributeIsSkill /
; AttributeHidden arrays + AttributeAssignment global. Used by Loom's
; Settings catalog editor. Same dim-write-from-Strict trap rationale.
Function SetAttributeName(Index, Name$)
	If Index < 0 Or Index > 39 Then Return
	AttributeNames$(Index) = Name$
End Function

Function SetAttributeIsSkill(Index, IsSkill)
	If Index < 0 Or Index > 39 Then Return
	AttributeIsSkill(Index) = IsSkill
End Function

Function SetAttributeHidden(Index, Hidden)
	If Index < 0 Or Index > 39 Then Return
	AttributeHidden(Index) = Hidden
End Function

Function SetAttributeAssignment(Val)
	AttributeAssignment = Val
End Function

; Duplicate an Actor template -- allocate a new ID via CreateActor, copy
; every field including the Attributes side-instance (deep-copied so the
; clone has its own backing storage). Returns the new ID, or -1 if
; ActorList is full or the source doesn't exist.
;
; Race$ + Class$ get " (copy)" appended on the Class$ since that's the
; secondary display field; if both were copied verbatim the user could
; confuse the duplicate with the original.
Function DuplicateActorTemplate(srcID)
	If srcID < 0 Or srcID > 65535 Then Return -1
	Src.Actor = ActorList(srcID)
	If Src = Null Then Return -1

	Dst.Actor = CreateActor()
	If Dst = Null Then Return -1

	Dst\Race$        = Src\Race$
	Dst\Class$       = Src\Class$ + " (copy)"
	Dst\Description$ = Src\Description$
	Dst\StartArea$   = Src\StartArea$
	Dst\StartPortal$ = Src\StartPortal$
	Dst\Radius#      = Src\Radius#
	Dst\Scale#       = Src\Scale#

	For i = 0 To 7
		Dst\MeshIDs[i] = Src\MeshIDs[i]
	Next
	For i = 0 To 4
		Dst\BeardIDs[i]      = Src\BeardIDs[i]
		Dst\MaleHairIDs[i]   = Src\MaleHairIDs[i]
		Dst\FemaleHairIDs[i] = Src\FemaleHairIDs[i]
		Dst\MaleFaceIDs[i]   = Src\MaleFaceIDs[i]
		Dst\FemaleFaceIDs[i] = Src\FemaleFaceIDs[i]
		Dst\MaleBodyIDs[i]   = Src\MaleBodyIDs[i]
		Dst\FemaleBodyIDs[i] = Src\FemaleBodyIDs[i]
	Next
	For i = 0 To 15
		Dst\MSpeechIDs[i] = Src\MSpeechIDs[i]
		Dst\FSpeechIDs[i] = Src\FSpeechIDs[i]
		Dst\HairColours[i] = Src\HairColours[i]
	Next

	Dst\BloodTexID   = Src\BloodTexID
	Dst\Genders      = Src\Genders

	For i = 0 To 19
		Dst\Resistances[i] = Src\Resistances[i]
	Next

	Dst\MAnimationSet = Src\MAnimationSet
	Dst\FAnimationSet = Src\FAnimationSet
	Dst\Playable      = Src\Playable
	Dst\Rideable      = Src\Rideable
	Dst\Aggressiveness = Src\Aggressiveness
	Dst\AggressiveRange = Src\AggressiveRange
	Dst\TradeMode     = Src\TradeMode
	Dst\Environment   = Src\Environment
	Dst\InventorySlots = Src\InventorySlots
	Dst\DefaultDamageType = Src\DefaultDamageType
	Dst\DefaultFaction = Src\DefaultFaction
	Dst\XPMultiplier   = Src\XPMultiplier
	Dst\PolyCollision  = Src\PolyCollision

	; Attributes deep-copy. CreateActor already allocated Dst\Attributes.
	If Src\Attributes <> Null And Dst\Attributes <> Null
		For i = 0 To 39
			Dst\Attributes\Value[i]   = Src\Attributes\Value[i]
			Dst\Attributes\Maximum[i] = Src\Attributes\Maximum[i]
		Next
	EndIf

	Return Dst\ID
End Function

; Delete an Actor template (NOT an ActorInstance). Used by Loom's entity-
; delete path. Frees the Type instance and clears the ActorList slot so a
; subsequent CreateActor can reuse the ID. Strict callers can't write to
; ActorList directly per the Dim-inside-Method trap.
Function DeleteActorTemplate(ID)
	If ID < 0 Or ID > 65535 Then Return False
	A.Actor = ActorList(ID)
	If A = Null Then Return False
	; Drop the Attributes side-instance if any -- otherwise it leaks.
	If A\Attributes <> Null
		Delete A\Attributes
		A\Attributes = Null
	EndIf
	ActorList(ID) = Null
	Delete A
	Return True
End Function

; Gives a known spell (ability) to an actor instance (SERVER ONLY!)
Function AddSpell(AI.ActorInstance, SpellID, Lvl = 1)
	If Lvl < 1 Then Return
	
	Sp.Spell = SpellsList(SpellID)
	
	If Sp = Null Then Return
	; Find a free slot
	For i = 0 To 999
		If AI\SpellLevels[i] <= 0
			; Add the spell
			AI\KnownSpells[i] = SpellID
			AI\SpellLevels[i] = Lvl
			; If they are a player in game, tell them
			If AI\RNID > 0
				;Sp.Spell = SpellsList(SpellID)
				Pa$ = RCE_StrFromInt$(Lvl, 2) + RCE_StrFromInt$(SpellID,2) +  RCE_StrFromInt$(Sp\ThumbnailTexID, 2) + RCE_StrFromInt$(Sp\RechargeTime, 2)
				Pa$ = Pa$ + RCE_StrFromInt$(Len(Sp\Name$), 2) + Sp\Name$ + RCE_StrFromInt$(Len(Sp\Description$), 2) + Sp\Description$
				Pa$ = Pa$ + RCE_StrFromInt$(0, 1)
				RCE_Send(Host, AI\RNID, P_KnownSpellUpdate, "A" + Pa$, True)
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
		If AI\MemorisedSpells[i] = ID Then AI\MemorisedSpells[i] = 5000 : Exit
	Next

	; If they are a player in game, tell them
	If AI\RNID > 0 And Sp <> Null Then RCE_Send(Host, AI\RNID, P_KnownSpellUpdate, "D" + Sp\Name$, True)

End Function


Function CleanActorEffects()
	Local AE.ActorEffect
	For AE = Each ActorEffect
		DestroyActorEffect( AE )
	Next
	Delete Each ActorEffect

End Function

; Creates a new ActorEffect
; AI is the ActorInstance to apply the effect to
; Effects is an Attributes set that holds the differences
; EffectName$ is the name the effect is meant to have
; EffectLength is the length of the effect in milliseconds
; ThumbnailTexID is the texture ID the effect icon is meant to have
Function CreateActorEffect.ActorEffect( AI.ActorInstance, Effects.Attributes, EffectName$, EffectLength%, ThumbnailTexID% )
	Found = False
	For AE.ActorEffect = Each ActorEffect
		If AE\Owner = AI
			If Upper$(AE\Name$) = Upper$(EffectName$)
				FoundAE.ActorEffect = AE
				Found = True
				Exit
			EndIf
		EndIf
	Next
	If Found = False
		FoundAE = New ActorEffect
		FoundAE\Attributes = New Attributes
		FoundAE\Name$ = EffectName$
		FoundAE\Owner = AI
		Pa$ = RCE_StrFromInt$(Handle(FoundAE), 4) + RCE_StrFromInt$(ThumbnailTexID, 2) + FoundAE\Name$
		RCE_Send(Host, AI\RNID, P_ActorEffect, "A" + Pa$, True)
	EndIf
	FoundAE\CreatedTime = MilliSecs()
	FoundAE\Length = EffectLength%
	; Bug fix: previously read from `AI\Inventory\Items[Slot]` where
	; `Slot` is not a parameter of this function -- Blitz silently
	; resolved it to whatever module-global of that name existed (the
	; ServerNet P_EatItem handler's local, post-call, or zero). Every
	; script-applied buff therefore copied attributes from whatever
	; was in the actor's inventory slot 0, not from the `Effects`
	; table the caller passed in. P_EatItem's open-coded inventory
	; copy at ServerNet.bb:1131+ already does the right thing; this
	; function takes `Effects.Attributes` precisely so script callers
	; can pass arbitrary buff vectors -- honour that.
	For i = 0 To 39
		If Effects\Value[i] <> 0
			Old = FoundAE\Attributes\Value[i]
			FoundAE\Attributes\Value[i] = Effects\Value[i]
			Pa$ = RCE_StrFromInt$(i, 1) + RCE_StrFromInt$(FoundAE\Attributes\Value[i] - Old, 4)
			FoundAE\Owner\Attributes\Value[i] = FoundAE\Owner\Attributes\Value[i] + (FoundAE\Attributes\Value[i] - Old)
			RCE_Send(Host, FoundAE\Owner\RNID, P_ActorEffect, "E" + Pa$, True)
		EndIf
	Next
	Return FoundAE
End Function

Function DestroyActorEffect( AE.ActorEffect )
	; Owner has gone
	
	If AE\Owner = Null
		Delete AE\Attributes
		Delete AE
		Return True
		
	; Owner still alive and online
	Else;If AE\Owner\RNID <> 0
		DebugLog( "RNID: " + AE\Owner\RNID )
		; Tell client if applicable
		If AE\Owner\RNID > 0
			Pa$ = RCE_StrFromInt$(Handle(AE), 4)
			For i = 0 To 39
				Pa$ = Pa$ + RCE_StrFromInt$(AE\Attributes\Value[i], 4)
			Next
			RCE_Send(Host, AE\Owner\RNID, P_ActorEffect, "R" + Pa$, True)
		EndIf

		DebugLog("Fixing Actor Effect on " + AE\Owner\Name)
		; Remove effect
		For i = 0 To 39
			If AE\Attributes\Value[i] <> 0 Then DebugLog("Fixing Attribute " + i + " by " + (- AE\Attributes\Value[i]) )
			AE\Owner\Attributes\Value[i] = AE\Owner\Attributes\Value[i] - AE\Attributes\Value[i]
		Next
		Delete AE\Attributes
		Delete AE
		Return True
	EndIf
	Return False

End Function

Function RemoveActorEffectFromActor( AI.ActorInstance, EffectName$ )
	For AE.ActorEffect = Each ActorEffect
		If AE\Owner = AI
			If Upper$(AE\Name$) = Upper$(EffectName$)
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
				Return True
			EndIf
		EndIf
	Next
	Return False
End Function