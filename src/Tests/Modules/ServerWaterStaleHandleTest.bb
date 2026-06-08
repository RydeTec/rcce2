Strict
EnableGC

; Regression test pinning the stale-handle Null discipline at the
; underwater-damage re-lookup site in GameServer.bb's
; UpdateActorInstances loop (~line 772-820).
;
; Pre-fix bug shape:
;
;   ; initial scan captures Handle to the matched ServerWater
;   Underwater = Handle(SW)
;   ; ... breath/damage logic ticks once per second ...
;   ElseIf MilliSecs() - AI\Underwater >= 1000
;       AI\Underwater = AI\Underwater + 1000
;       SW = Object.ServerWater(Underwater)    ; could return Null!
;       ...
;       If SW\Damage > 0                       ; <-- crash here
;
; Window: the SW was live at initial scan, but >=1s elapsed before
; the per-second damage branch fires. If the owning Area was
; ServerUnloadArea'd, or the water was explicitly Deleted, in that
; window, Object.ServerWater returns Null and the unguarded deref
; takes down the server -- every actor underwater is at risk every
; tick.
;
; Post-fix posture: `SW <> Null` guard on the damage branch. Breath
; loss still runs (AI-state-only, doesn't read SW); only the damage
; block (which reads SW\Damage / SW\DamageType) is gated. The next
; tick either re-picks a new water via the initial scan or clears
; AI\Underwater via the no-hit path.
;
; GameServer.bb pulls actor / packet / world graph and can't be
; Included into a Strict test build. Following the established
; replicated-gate pattern (AccountEnumerationTest, BVMPrivilegeGateTest,
; WireParameterHardeningTest), the gate predicate is replicated
; below. A production change MUST update both copies; the duplication
; is the trigger to refresh the test rationale.

; --- Replicated gate predicate --------------------------------------

; Returns True iff the damage branch should run -- i.e., the
; re-resolved ServerWater is still live AND damages.
;
; SW_IsNull     : True iff Object.ServerWater(Underwater) returned Null
; SW_Damage     : the SW\Damage field value (0 means no damage)
Function UnderwaterDamageBranchShouldRun%(SW_IsNull%, SW_Damage%)
	If SW_IsNull = True Then Return False
	If SW_Damage <= 0 Then Return False
	Return True
End Function

; ====================================================================
; Positive cases -- live water with damage
; ====================================================================

Test testLiveDamagingWaterRuns()
	; Standard hazard water: live handle, positive damage.
	Assert(UnderwaterDamageBranchShouldRun%(False, 5) = True)
End Test

Test testLiveDamagingWaterMinimumDamageRuns()
	; Damage = 1 -- the minimum positive value still triggers.
	Assert(UnderwaterDamageBranchShouldRun%(False, 1) = True)
End Test

; ====================================================================
; Negative cases -- live but non-damaging
; ====================================================================

Test testLiveZeroDamageDoesNotRun()
	; Live water but Damage = 0. Pre-fix and post-fix: branch is
	; skipped (the SW\Damage > 0 inner check rejects). Pinned so a
	; future refactor doesn't accidentally fire damage on every
	; benign water tile.
	Assert(UnderwaterDamageBranchShouldRun%(False, 0) = False)
End Test

Test testLiveNegativeDamageDoesNotRun()
	; Hypothetical: a SW\Damage = -3 (corrupted area file, future
	; admin tooling). The branch must not fire -- the original
	; `If SW\Damage > 0` check is what filters this.
	Assert(UnderwaterDamageBranchShouldRun%(False, -1) = False)
End Test

; ====================================================================
; Negative cases -- STALE HANDLE (the load-bearing test)
; ====================================================================

Test testStaleHandleNullDoesNotRun()
	; The exact pre-fix crash shape: Object.ServerWater returned
	; Null because the water was Deleted in the 1s breath window.
	; Pre-fix: branch ran anyway and `SW\Damage` Null-deref'd the
	; server. Post-fix: `SW <> Null` rejects before any field read.
	Assert(UnderwaterDamageBranchShouldRun%(True, 5) = False)
End Test

Test testStaleHandleZeroDamageDoesNotRun()
	; SW_Damage value here is meaningless (SW is Null, can't read
	; a field on it). But pin that the Null branch short-circuits
	; before the damage check even runs.
	Assert(UnderwaterDamageBranchShouldRun%(True, 0) = False)
End Test

Test testStaleHandleHighDamageDoesNotRun()
	; Stress: even a high reported damage value can't override the
	; Null gate. This case is unreachable in production (SW is Null
	; means SW\Damage can't be read), but the predicate's Null gate
	; must be unconditional.
	Assert(UnderwaterDamageBranchShouldRun%(True, 9999) = False)
End Test
