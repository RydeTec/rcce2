Strict
EnableGC

; Regression test pinning the wire-parameter hardening in:
;
;   1. P_Examine     (ServerNet.bb ~line 1333)
;   2. P_Trade       (ServerNet.bb ~line 1355)
;   3. P_ItemScript  (ServerNet.bb ~line 1231, A2 path)
;   4. InventoryAdd  (Inventories.bb ~line 105, "A" arrival branch)
;
; Pre-fix posture:
;   - P_Examine, P_Trade, P_ItemScript(A2) accepted ANY actor anywhere
;     in RuntimeIDList -- no range check, no same-area check. P_RightClick
;     and P_AttackActor had the sibling gates (range, area) but the
;     other three Default-script entry points did not. Combined with
;     the BVM clicker-handle trap (SI\AI = Handle(clicker) for these
;     script spawns), a wire-injecting client could trigger the script
;     against a cross-area target and any BVM that's "safe in
;     isolation" runs at attacker authority against a victim the
;     attacker can't even see.
;   - InventoryAdd treated Amount as unchecked: ServerNet's outer
;     `Amount <= Amounts[SlotA]` check passes any negative value
;     (negative <= non-negative), and the function then did
;     Amounts[SlotTo] += Amount / Amounts[SlotFrom] -= Amount with
;     no internal check, inflating SlotFrom and deflating SlotTo
;     past zero -- an unbounded duplication path. InventorySwap
;     already had the matching guard at line 152.
;
; Post-fix posture: all four sites validate the wire parameter
; before any mutation/script-spawn. ServerNet.bb pulls the entire
; network/actor/item graph and can't be included into a Strict
; test build, so the gate predicates are replicated below following
; the established pattern (AccountEnumerationTest, BVMPrivilegeGateTest,
; ClampFloatTest). A behaviour change in production MUST update
; both copies; the duplication is the trigger to refresh the test.

; --- Replicated gate predicates -------------------------------------

; Const InteractDist = 400 from Actors.bb (radius ~20, squared).
; Tests use the same constant so the boundary cases align with prod.
Const TestInteractDist% = 400

; Returns True iff the same-area + InteractDist gate at the top of
; P_Examine / P_Trade / P_ItemScript(A2) would allow the script to
; spawn. Mirrors the production check.
;
;   SameArea  : True iff AInstance <> Null And AInstance = TInstance
;   DistSq    : XDist*XDist + ZDist*ZDist (server units, squared)
Function ScriptTriggerGateOk%(SameArea%, DistSq#)
	If SameArea = False Then Return False
	If DistSq >= TestInteractDist Then Return False
	Return True
End Function

; Returns True iff InventoryAdd's new Amount bounds-check at
; Inventories.bb ~line 105 would allow the transfer.
;
;   Amount       : the wire-supplied transfer count
;   AmountsFrom  : the source slot's current stack count
Function InventoryAddAmountOk%(Amount%, AmountsFrom%)
	If Amount < 1 Or Amount > AmountsFrom Then Return False
	Return True
End Function

; ====================================================================
; Script-trigger gate -- positive cases (in-area + in-range)
; ====================================================================

Test testInAreaInRangeAllowed()
	; Same area, distance 10^2 + 10^2 = 200, well under 400.
	Assert(ScriptTriggerGateOk%(True, 200) = True)
End Test

Test testInAreaZeroDistanceAllowed()
	; Standing on top of the target -- still in range.
	Assert(ScriptTriggerGateOk%(True, 0) = True)
End Test

Test testInAreaJustInsideBoundaryAllowed()
	; Distance squared = 399, immediately under the 400 cap.
	Assert(ScriptTriggerGateOk%(True, 399) = True)
End Test

; ====================================================================
; Script-trigger gate -- negative cases (cross-area or out-of-range)
; ====================================================================

Test testCrossAreaRejected()
	; Different AreaInstance even at distance zero -- portal exploit.
	Assert(ScriptTriggerGateOk%(False, 0) = False)
End Test

Test testCrossAreaLargeDistanceRejected()
	; The fully-attacker-controlled case: target is in another
	; area entirely AND not in range.
	Assert(ScriptTriggerGateOk%(False, 1000000) = False)
End Test

Test testSameAreaTooFarRejected()
	; Same area but well out of interaction range -- e.g. shooting
	; an Examine packet at an NPC on the far side of the same map.
	Assert(ScriptTriggerGateOk%(True, 10000) = False)
End Test

Test testSameAreaExactlyAtBoundaryRejected()
	; DistSq = InteractDist exactly: the production check is
	; `< InteractDist`, so equality rejects. Pin this boundary so
	; a future refactor doesn't silently flip to `<=`.
	Assert(ScriptTriggerGateOk%(True, 400) = False)
End Test

Test testStaleServerAreaRejected()
	; AInstance = Null path (actor mid-portal / freed AreaInstance).
	; Same as cross-area for gate purposes -- SameArea is False.
	Assert(ScriptTriggerGateOk%(False, 50) = False)
End Test

; ====================================================================
; InventoryAdd Amount bounds-check -- positive cases
; ====================================================================

Test testInventoryAddPositiveAmountAllowed()
	; Standard case: move 3 of 10 in source slot.
	Assert(InventoryAddAmountOk%(3, 10) = True)
End Test

Test testInventoryAddAmountEqualToStackAllowed()
	; Move the whole stack -- valid.
	Assert(InventoryAddAmountOk%(10, 10) = True)
End Test

Test testInventoryAddMinimumAmountAllowed()
	; Move exactly one item.
	Assert(InventoryAddAmountOk%(1, 1) = True)
End Test

; ====================================================================
; InventoryAdd Amount bounds-check -- negative cases (exploit paths)
; ====================================================================

Test testInventoryAddZeroAmountRejected()
	; Amount = 0 was a no-op pre-fix (Amounts[SlotTo] += 0). The
	; new check rejects it -- callers should send a positive count
	; or not send the packet at all. Pinned to surface UI bugs.
	Assert(InventoryAddAmountOk%(0, 10) = False)
End Test

Test testInventoryAddNegativeOneRejected()
	; The minimum-impact dupe: Amount = -1. Pre-fix this transferred
	; -1 from SlotTo into SlotFrom, inflating the source by 1.
	Assert(InventoryAddAmountOk%(-1, 5) = False)
End Test

Test testInventoryAddLargeNegativeRejected()
	; The "give me 1000 items" exploit -- Amount = -1000.
	; Pre-fix this passed (negative is <= AmountsFrom = 5 always)
	; and inflated SlotFrom by 1000.
	Assert(InventoryAddAmountOk%(-1000, 5) = False)
End Test

Test testInventoryAddOversizedAmountRejected()
	; Amount > AmountsFrom. Pre-fix this drained SlotFrom into
	; negative territory while inflating SlotTo past the actual
	; available count.
	Assert(InventoryAddAmountOk%(50, 10) = False)
End Test

Test testInventoryAddIntMinRejected()
	; Worst case: Amount = -32768 (the minimum a 2-byte signed
	; wire value can carry). Pre-fix: inflates SlotFrom by 32768.
	Assert(InventoryAddAmountOk%(-32768, 1) = False)
End Test
