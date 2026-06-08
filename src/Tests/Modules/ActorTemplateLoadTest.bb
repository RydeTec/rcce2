Strict
; EnableGC intentionally omitted -- per the
; feedback_blitzforge_test_handle_object_gc memory, Type-heavy tests
; stack-overflow under GC tracing. Strict-only is sufficient for the
; bounds-check assertions here.
;
; Strict-mode `Dim X(N)` assignment inside Functions errors per the
; feedback_strict_mode_dim_array_assignment memory. The replicated
; gate below factors the ActorList lookup into a bool parameter, so
; the test asserts the bounds-check + Null-slot dispatch logic without
; needing to populate a Dim'd array.

; Regression test pinning the missing-template Null-return contract
; in MySQL.bb's My_LoadActorInstance.
;
; Production My_LoadActorInstance pre-fix would allocate a fresh
; empty ActorInstance and return it when the saved row's `actorid`
; column referenced a deleted (or OOB) template -- the function's
; own audit comment at MySQL.bb:645-653 documented the *intended*
; behavior ("My_LoadActorInstance returns Null") that the code
; violated. The slave-load `If Slave <> Null` guard was dead.
;
; Replicated-gate pattern: MySQL.bb can't be included directly
; (pulls in SQL DLL + Actors.bb's full ActorInstance graph + world
; state), so rebuild just the template-lookup branch logic here.
;
; Any production change to the My_LoadActorInstance template
; lookup MUST update this file. The duplication is the trigger
; to refresh the test rationale.

; Replicates the new branch logic in MySQL.bb's My_LoadActorInstance
; introduced by this iteration. Returns True if the template lookup
; would succeed (caller proceeds to allocation), False if the load
; should soft-fail and return Null.
;
; Production shape:
;   If ActorID < 0 Or ActorID > 65535 Or ActorList(ActorID) = Null Then
;       WriteLog(MainLog, ...)
;       FreeSQLRow(Row)
;       FreeSQLQuery(Result)
;       Return Null
;   EndIf
;
; The `SlotPopulated` bool parameter abstracts the ActorList(ActorID)
; lookup -- True = registry has a non-Null entry at that slot,
; False = empty slot. This sidesteps the Strict-mode Dim-assignment
; restriction while pinning the exact same gate semantics.
Function MockTemplateAvailable%(ActorID, SlotPopulated)
	If ActorID < 0 Or ActorID > 65535 Then Return False
	If SlotPopulated = False Then Return False
	Return True
End Function

; ====================================================================
; Bounds checks: out-of-range ActorID returns False (soft-fail)
; regardless of registry state.
; ====================================================================

Test testNegativeActorIDFailsClosed()
	Assert(MockTemplateAvailable%(-1, True) = False)
	Assert(MockTemplateAvailable%(-100, True) = False)
	Assert(MockTemplateAvailable%(-32768, True) = False)
	; And False with empty slot too.
	Assert(MockTemplateAvailable%(-1, False) = False)
End Test

Test testPositiveOOBActorIDFailsClosed()
	; Dim ActorList(65535) is 0..65535 inclusive. Anything > 65535
	; would SeekFile past the index table in the production code.
	Assert(MockTemplateAvailable%(65536, True) = False)
	Assert(MockTemplateAvailable%(70000, True) = False)
	Assert(MockTemplateAvailable%(2147483647, True) = False)
	; And with empty slot.
	Assert(MockTemplateAvailable%(65536, False) = False)
End Test

; ====================================================================
; Empty-slot check: in-range ActorID with Null registry slot returns
; False. This is the post-fix path for "template was deleted between
; server restarts" -- the saved row references a slot that's been
; nulled.
; ====================================================================

Test testEmptySlotFailsClosed()
	Assert(MockTemplateAvailable%(0, False) = False)
	Assert(MockTemplateAvailable%(1, False) = False)
	Assert(MockTemplateAvailable%(32768, False) = False)
	Assert(MockTemplateAvailable%(65535, False) = False)
End Test

; ====================================================================
; Happy path: in-range ActorID with a populated registry slot returns
; True. The production code then allocates via CreateActorInstance.
; ====================================================================

Test testOccupiedSlotSucceeds()
	Assert(MockTemplateAvailable%(42, True) = True)
	Assert(MockTemplateAvailable%(0, True) = True)
	Assert(MockTemplateAvailable%(65535, True) = True)
End Test

; ====================================================================
; Boundary: exact-edge ActorID values (0 and 65535) work both ways.
; ====================================================================

Test testZeroActorIDBranchesOnSlot()
	; ActorID = 0 is in range; result depends on the slot.
	Assert(MockTemplateAvailable%(0, False) = False)
	Assert(MockTemplateAvailable%(0, True) = True)
End Test

Test testMaxInRangeActorIDBranchesOnSlot()
	Assert(MockTemplateAvailable%(65535, False) = False)
	Assert(MockTemplateAvailable%(65535, True) = True)
End Test

Test testOneAboveMaxFailsRegardlessOfSlot()
	; 65536 is the first OOB value above the registry's inclusive max.
	; Returns False whether the (notional) slot would be populated or not,
	; because the bounds check fires first.
	Assert(MockTemplateAvailable%(65536, False) = False)
	Assert(MockTemplateAvailable%(65536, True) = False)
End Test

Test testOneBelowZeroFailsRegardlessOfSlot()
	Assert(MockTemplateAvailable%(-1, False) = False)
	Assert(MockTemplateAvailable%(-1, True) = False)
End Test
