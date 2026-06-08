Strict
EnableGC

; Pins the slot-bounds contract for the BVM inventory accessors
; BVM_ACTORBACKPACK / BVM_ACTORRING / BVM_ACTORAMULET (ScriptingCommands.bb).
;
; Each takes a raw script-supplied Param2 and computes a slot index into
; Actor\Inventory\Items[] -- a Field [Slots_Inventory] (46 slots, 0..45). The
; accessors used to index with NO bound check, while the sibling
; BVM_BACKPACKCOUNT bounds the identical expression. An out-of-range Param2
; (e.g. ActorRing(clicker, 9999) or a negative) was an out-of-bounds Field read
; whose garbage flowed into Handle() -> server crash / type-confused handle, one
; click from any hostile NPC script. The fix bounds each computed slot and
; returns 0 ("no item") when out of range.
;
; ScriptingCommands.bb can't be Included into a test build (RakNet/world deps),
; so the slot-resolution is replicated here on the real constants, per the
; established ClampFloatTest / *CleanupTest convention. -1 models "rejected"
; (the handler leaves Result = 0 and never indexes Items[]).

; Mirror of Inventories.bb:15-31.
Const SlotI_Ring1     = 8
Const SlotI_Ring4     = 11
Const SlotI_Amulet1   = 12
Const SlotI_Amulet2   = 13
Const SlotI_Backpack  = 14
Const Slots_Inventory = 45

; Mirrors the FIXED BVM_ACTORBACKPACK index resolution.
Function BackpackIndex%(Param2)
	Local Num = Param2 - 1
	If SlotI_Backpack + Num >= SlotI_Backpack And SlotI_Backpack + Num <= Slots_Inventory
		Return SlotI_Backpack + Num
	EndIf
	Return -1
End Function

; Mirrors the FIXED BVM_ACTORRING index resolution.
Function RingIndex%(Param2)
	Local Num = Param2 - 1
	If SlotI_Ring1 + Num >= SlotI_Ring1 And SlotI_Ring1 + Num <= SlotI_Ring4
		Return SlotI_Ring1 + Num
	EndIf
	Return -1
End Function

; Mirrors the FIXED BVM_ACTORAMULET index resolution.
Function AmuletIndex%(Param2)
	Local Num = Param2 - 1
	If SlotI_Amulet1 + Num >= SlotI_Amulet1 And SlotI_Amulet1 + Num <= SlotI_Amulet2
		Return SlotI_Amulet1 + Num
	EndIf
	Return -1
End Function


; Legit backpack slots resolve to in-bounds Items[] indices (14..45).
Test testBackpackInRangeResolves()
	Assert(BackpackIndex%(1) = 14)    ; first backpack slot
	Assert(BackpackIndex%(32) = 45)   ; last valid index (Slots_Inventory)
End Test

; Out-of-range backpack Param2 is rejected (no Items[] index) -- this is the
; crash/UAF guard. 33 -> 46 (past array), 0 -> 13 (before backpack), and the
; hostile huge/negative values.
Test testBackpackOutOfRangeRejected()
	Assert(BackpackIndex%(33) = -1)     ; 46, one past the array
	Assert(BackpackIndex%(0) = -1)      ; 13, below SlotI_Backpack
	Assert(BackpackIndex%(9999) = -1)   ; the DoS value
	Assert(BackpackIndex%(-100) = -1)   ; negative index
End Test

; Rings resolve for 1..4 (slots 8..11).
Test testRingInRangeResolves()
	Assert(RingIndex%(1) = 8)
	Assert(RingIndex%(4) = 11)
End Test

Test testRingOutOfRangeRejected()
	Assert(RingIndex%(0) = -1)      ; 7, below Ring1
	Assert(RingIndex%(5) = -1)      ; 12, above Ring4 (would hit amulet/other)
	Assert(RingIndex%(9999) = -1)
	Assert(RingIndex%(-50) = -1)
End Test

; Amulets resolve for 1..2 (slots 12..13).
Test testAmuletInRangeResolves()
	Assert(AmuletIndex%(1) = 12)
	Assert(AmuletIndex%(2) = 13)
End Test

Test testAmuletOutOfRangeRejected()
	Assert(AmuletIndex%(0) = -1)      ; 11, below Amulet1
	Assert(AmuletIndex%(3) = -1)      ; 14, above Amulet2
	Assert(AmuletIndex%(9999) = -1)
	Assert(AmuletIndex%(-1) = -1)
End Test
