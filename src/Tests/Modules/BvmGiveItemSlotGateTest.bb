Strict
EnableGC

; Regression pins for the BVM_GIVEITEM "can the actor use this slot" gate in
; src/Modules/ScriptingCommands.bb (~line 2908).
;
; The bug: BVM_GIVEITEM passed It\SlotType -- a slot NAME constant
; (Slot_* = 1..11, Inventories.bb "Slot names") -- straight into
; ActorHasSlot's second parameter, which is a slot INDEX (SlotI_* = 0-based,
; equip 0..13, backpack 14+). Every item kind therefore checked the NEXT
; slot's enable flag (weapon -> Shield flag, shield -> Hat, ..., feet ->
; Ring), amulet items (10 = SlotI_Ring3) and backpack items
; (11 = SlotI_Ring4) both checked the Ring flag, and backpack items were
; run through the equip race/class-exclusivity fork even though carrying
; is never exclusivity-gated.
;
; ScriptingCommands.bb can't be Included into a test build (network/world
; deps), so the SlotType -> SlotI translation is replicated here on the real
; Inventories.bb constants, per the established BvmInventoryAccessorBoundsTest
; / BVMPrivilegeGateTest pattern. ActorHasSlot itself IS the real one
; (Inventories.bb is Included), so the end-to-end gate assertions exercise
; the genuine flag/exclusivity logic.

; --- External type stubs ----------------------------------------------------
; Same stub set as InventoryEquipRulesTest.bb: Items.bb references
; Attributes / ActorInstance (Actors.bb); Inventories.bb needs Actor
; (Race$/Class$/InventorySlots) and an ActorInstance shaped with
; Inventory/Actor/RuntimeID.
Type Attributes
	Field Value[39]
	Field Maximum[39]
	Field My_ID
End Type

Type Actor
	Field Race$
	Field Class$
	Field InventorySlots
End Type

Type ActorInstance
	Field Account               ; referenced by Items.bb
	Field Actor.Actor           ; referenced by Inventories.bb
	Field Inventory.Inventory
	Field RuntimeID
End Type

; --- RCE wire-format helpers ------------------------------------------------
; Verbatim from RCEnet.bb with a private Bank (same as ItemsTest.bb).
Global GiveGateTest_ConvertBank.BBBank = CreateBank(8)

Function RCE_IntFromStr(Dat$)
	PokeInt GiveGateTest_ConvertBank, 0, 0
	Local i
	For i = 1 To Len(Dat$)
		PokeByte GiveGateTest_ConvertBank, i - 1, Asc(Mid$(Dat$, i, 1))
	Next
	Return PeekInt(GiveGateTest_ConvertBank, 0)
End Function

Function RCE_StrFromInt$(Num, Length = 4)
	PokeInt GiveGateTest_ConvertBank, 0, Num
	Local Dat$ = ""
	Local i
	For i = Length - 1 To 0 Step -1
		Dat$ = Chr$(PeekByte(GiveGateTest_ConvertBank, i)) + Dat$
	Next
	Return Dat$
End Function

; --- Network stubs ----------------------------------------------------------
Global Connection = 0
Global PeerToHost = 0
Const P_InventoryUpdate = 0

Function RCE_Send(Conn, Destination, MessageType, MessageData$, ReliableFlag = 0, PlayerFrom = 0, DoNotUse = 0, ConfirmID = -1)
End Function

; --- GetFlag (Actors.bb) ----------------------------------------------------
; Replicated verbatim -- one expression; pulling Actors.bb in would drag the
; world graph.
Function GetFlag(TheInt, Flag)
	Return (TheInt Shr Flag) And 1
End Function

; --- Logging stub -----------------------------------------------------------
Global MainLog = 0

Function WriteLog(LogID%, Message$, Timestamp% = True, Datestamp% = False)
End Function

; --- SafeWrite stubs (Items.bb save path, not exercised) ---------------------
Function SafeWriteOpen$(FinalPath$)
	Return FinalPath$
End Function

Function SafeWriteCommit%(TempPath$, FinalPath$, F)
	Return True
End Function

; --- Language helper stub (Items.bb GetItemType$/GetWeaponType$) -------------
Function LanguageString$(key$)
	Return key
End Function

; --- ReadBoundedString$ stub (Items.bb load path, not exercised) -------------
Function ReadBoundedString$(F, MaxLen)
	Return ""
End Function

Include "Modules\Items.bb"
Include "Modules\Inventories.bb"

; --- Replicated logic under test ---------------------------------------------
; Semantically identical to the translation in BVM_GIVEITEM
; (ScriptingCommands.bb), which reads:
;
;     GiveSlotI = It\SlotType - 1
;     If It\SlotType = Slot_Amulet Then GiveSlotI = SlotI_Amulet1
;     If It\SlotType = Slot_Backpack Then GiveSlotI = SlotI_Backpack
;
; (re-shaped as early Returns because Strict mode rejects reassigning a
; function-scope Local from inside an If branch).
Function GiveItemGateSlotI(slotType)
	If slotType = Slot_Amulet Then Return SlotI_Amulet1
	If slotType = Slot_Backpack Then Return SlotI_Backpack
	Return slotType - 1
End Function

; --- Test helpers -----------------------------------------------------------

; Bit mask with every inventory-slot flag enabled (bits 0..10:
; Weapon, Shield, Hat, Chest, Hand, Belt, Legs, Feet, Ring, Amulet, Backpack).
Const MaskAll = 2047            ; $7FF

Function MakeActor.ActorInstance(slotsMask, race$ = "", cls$ = "")
	Local A.ActorInstance = New ActorInstance()
	A\Actor = New Actor()
	A\Actor\InventorySlots = slotsMask
	A\Actor\Race$ = race
	A\Actor\Class$ = cls
	A\Inventory = New Inventory()
	Return A
End Function

Function SeedTypedItem.Item(name$, itemType, slotType)
	Local It.Item = CreateItem()
	It\Name$ = name
	It\ItemType = itemType
	It\SlotType = slotType
	Return It
End Function

Function ClearAll()
	Delete Each ActorInstance
	Delete Each Actor
	Delete Each Inventory
	Delete Each ItemInstance
	Delete Each Item
	Delete Each Attributes      ; CreateItem allocates one per template
End Function

; ---------------------------------------------------------------------------
; SlotType (name) -> SlotI (index) translation pins
; ---------------------------------------------------------------------------

; Pin the full name->index map on the real constants. Slot_Weapon..Slot_Ring
; (1..9) are name-1; Slot_Amulet and Slot_Backpack jump because the index
; space has four Ring and two Amulet slots.
Test testSlotTypeToIndexTranslation()
	Assert(GiveItemGateSlotI(Slot_Weapon)   = SlotI_Weapon)
	Assert(GiveItemGateSlotI(Slot_Shield)   = SlotI_Shield)
	Assert(GiveItemGateSlotI(Slot_Hat)      = SlotI_Hat)
	Assert(GiveItemGateSlotI(Slot_Chest)    = SlotI_Chest)
	Assert(GiveItemGateSlotI(Slot_Hand)     = SlotI_Hand)
	Assert(GiveItemGateSlotI(Slot_Belt)     = SlotI_Belt)
	Assert(GiveItemGateSlotI(Slot_Legs)     = SlotI_Legs)
	Assert(GiveItemGateSlotI(Slot_Feet)     = SlotI_Feet)
	Assert(GiveItemGateSlotI(Slot_Ring)     = SlotI_Ring1)
	Assert(GiveItemGateSlotI(Slot_Amulet)   = SlotI_Amulet1)
	Assert(GiveItemGateSlotI(Slot_Backpack) = SlotI_Backpack)

	; Every equip name lands strictly inside ActorHasSlot's equip range
	; (so the exclusivity fork applies) and the backpack name lands at the
	; first backpack index (so it doesn't).
	Local s%
	For s = Slot_Weapon To Slot_Amulet
		Assert(GiveItemGateSlotI(s) < SlotI_Backpack)
	Next
	Assert(GiveItemGateSlotI(Slot_Backpack) >= SlotI_Backpack)
End Test

; ---------------------------------------------------------------------------
; End-to-end gate behavior through the REAL ActorHasSlot
; ---------------------------------------------------------------------------

; Backpack items (potions, quest rewards -- the dominant GiveItem use in
; data/Server Data/Scripts/) must gate on the Backpack flag. The pre-fix
; code checked index 11 = SlotI_Ring4 -> the RING flag, so a race with the
; Ring slot disabled silently lost every scripted potion/reward grant.
Test testBackpackItemGatesOnBackpackFlagNotRingFlag()
	Local potion.Item = SeedTypedItem("Potion", I_Potion, Slot_Backpack)

	; Ring disabled, Backpack enabled: gate must pass (old code: failed).
	Local A.ActorInstance = MakeActor(MaskAll Xor (1 Shl (Slot_Ring - 1)))
	Assert(ActorHasSlot(A\Actor, GiveItemGateSlotI(potion\SlotType), potion) = True)

	; Backpack disabled, Ring enabled: gate must fail (old code: passed).
	Local B.ActorInstance = MakeActor(MaskAll Xor (1 Shl (Slot_Backpack - 1)))
	Assert(ActorHasSlot(B\Actor, GiveItemGateSlotI(potion\SlotType), potion) = False)

	ClearAll()
End Test

; Weapons must gate on the Weapon flag. The pre-fix code checked index 1 =
; SlotI_Shield -> the SHIELD flag (the generic off-by-one shape that hits
; every equip kind: shield checked Hat, ..., feet checked Ring).
Test testWeaponGatesOnWeaponFlagNotShieldFlag()
	Local sword.Item = SeedTypedItem("Sword", I_Weapon, Slot_Weapon)

	; Weapon enabled, Shield disabled: must pass (old code: failed).
	Local A.ActorInstance = MakeActor(MaskAll Xor (1 Shl (Slot_Shield - 1)))
	Assert(ActorHasSlot(A\Actor, GiveItemGateSlotI(sword\SlotType), sword) = True)

	; Weapon disabled, Shield enabled: must fail (old code: passed).
	Local B.ActorInstance = MakeActor(MaskAll Xor (1 Shl (Slot_Weapon - 1)))
	Assert(ActorHasSlot(B\Actor, GiveItemGateSlotI(sword\SlotType), sword) = False)

	ClearAll()
End Test

; Amulets must gate on the Amulet flag. The pre-fix code checked index 10 =
; SlotI_Ring3 -> the RING flag.
Test testAmuletGatesOnAmuletFlagNotRingFlag()
	Local charm.Item = SeedTypedItem("Charm", I_Ring, Slot_Amulet)

	; Amulet enabled, Ring disabled: must pass (old code: failed).
	Local A.ActorInstance = MakeActor(MaskAll Xor (1 Shl (Slot_Ring - 1)))
	Assert(ActorHasSlot(A\Actor, GiveItemGateSlotI(charm\SlotType), charm) = True)

	; Amulet disabled, Ring enabled: must fail (old code: passed).
	Local B.ActorInstance = MakeActor(MaskAll Xor (1 Shl (Slot_Amulet - 1)))
	Assert(ActorHasSlot(B\Actor, GiveItemGateSlotI(charm\SlotType), charm) = False)

	ClearAll()
End Test

; Carrying is never exclusivity-gated: a race/class-exclusive BACKPACK item
; must still be givable to a non-matching actor (matches the pickup handler,
; whose backpack indices hit ActorHasSlot's Default branch and skip the
; exclusivity fork). The pre-fix code routed backpack items through equip
; index 11 (Ring4), which since the equip-exclusivity fix also applied the
; race/class checks to them.
Test testExclusiveBackpackItemStillGivableToOtherRace()
	Local relic.Item = SeedTypedItem("Elf Relic", I_Other, Slot_Backpack)
	relic\ExclusiveRace$ = "Elf"

	Local A.ActorInstance = MakeActor(MaskAll, "Orc")
	Assert(ActorHasSlot(A\Actor, GiveItemGateSlotI(relic\SlotType), relic) = True)

	; The same exclusivity on an EQUIP item is still enforced by the gate.
	Local blade.Item = SeedTypedItem("Elf Blade", I_Weapon, Slot_Weapon)
	blade\ExclusiveRace$ = "Elf"
	Assert(ActorHasSlot(A\Actor, GiveItemGateSlotI(blade\SlotType), blade) = False)

	ClearAll()
End Test
