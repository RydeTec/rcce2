Strict
EnableGC

; Tests for the inventory stack-amount ceiling enforced in Inventories.bb
; (ClampStackAmount + the non-lossy cap in InventoryAdd) and applied at the
; server accumulation sites (ServerNet.bb pickup / give-to-slot,
; ScriptingCommands.bb BVM give).
;
; Why a ceiling exists: a slot's Amounts[] is an authoritative 32-bit Int on
; the server, but it is serialised as SIGNED 16-bit in the save file
; (Actors.bb WriteShort/ReadShort) and as a 2-byte field on the wire. A stack
; pushed past 32767 corrupts on save->load -- WriteShort 40000 round-trips
; through ReadShort to -25536, which the `<= 0` slot cleanup treats as empty
; and DELETES the whole stack. The dupe-guards added earlier cap the move
; against the SOURCE amount but never the DESTINATION ceiling; this closes
; that gap.
;
; Inventories.bb can't be Included into a test build (it pulls ItemInstance /
; the world graph), so ClampStackAmount and the InventoryAdd capped-move are
; replicated verbatim here, per the established ClampFloatTest convention. A
; change to either production copy must update this duplicate.

Const MaxStackAmount = 32767

Function ClampStackAmount(Total)
	If Total > MaxStackAmount Then Return MaxStackAmount
	Return Total
End Function

; Two-slot stand-in for an Inventory: aTo = destination, aFrom = source.
Type MockInv
	Field aTo
	Field aFrom
End Type

; Mirror of InventoryAdd's capped, non-lossy move. The production code has
; already guaranteed `requested >= 1 And requested <= aFrom` before this
; point (the negative/oversize dupe guard). Returns True if anything moved.
Function MergeCapped(M.MockInv, requested)
	Local Movable = MaxStackAmount - M\aTo
	If Movable < 0 Then Movable = 0
	Local amt = requested
	If amt > Movable Then amt = Movable
	If amt < 1 Then Return False
	M\aTo = M\aTo + amt
	M\aFrom = M\aFrom - amt
	Return True
End Function


; The clamp caps at the 16-bit ceiling and is a pass-through below it.
Test testClampCapsAtCeiling()
	Assert(ClampStackAmount(40000) = MaxStackAmount)
	Assert(ClampStackAmount(65535) = MaxStackAmount)
	Assert(ClampStackAmount(32768) = MaxStackAmount)
	Assert(ClampStackAmount(MaxStackAmount) = MaxStackAmount)
	Assert(ClampStackAmount(1000) = 1000)
	Assert(ClampStackAmount(0) = 0)
End Test

; The accumulation sites never store a value the save format can't hold.
Test testClampedAddNeverExceedsCeiling()
	; Simulates ServerNet pickup: existing 32000 + picked-up 5000.
	Assert(ClampStackAmount(32000 + 5000) <= MaxStackAmount)
	Assert(ClampStackAmount(32000 + 5000) = MaxStackAmount)
End Test

; Merging into a near-full destination moves only what fits; the remainder
; stays in the source -- nothing is lost, and the destination stays <= cap.
Test testMergeIsNonLossyAtCeiling()
	Local m.MockInv = New MockInv()
	m\aTo = 32000 : m\aFrom = 5000
	Local startTotal = m\aTo + m\aFrom
	Assert(MergeCapped(m, 5000) = True)
	Assert(m\aTo = MaxStackAmount)        ; 32000 + 767
	Assert(m\aFrom = 4233)                ; 5000 - 767 remainder retained
	Assert(m\aTo + m\aFrom = startTotal)  ; total conserved (non-lossy)
	Assert(m\aTo <= MaxStackAmount)
End Test

; A full destination accepts nothing and leaves both slots untouched.
Test testMergeFullDestMovesNothing()
	Local m.MockInv = New MockInv()
	m\aTo = MaxStackAmount : m\aFrom = 100
	Assert(MergeCapped(m, 100) = False)
	Assert(m\aTo = MaxStackAmount)
	Assert(m\aFrom = 100)
End Test

; A normal sub-ceiling merge is unaffected by the cap.
Test testMergeNormalUnaffected()
	Local m.MockInv = New MockInv()
	m\aTo = 10 : m\aFrom = 5
	Assert(MergeCapped(m, 5) = True)
	Assert(m\aTo = 15)
	Assert(m\aFrom = 0)
End Test
