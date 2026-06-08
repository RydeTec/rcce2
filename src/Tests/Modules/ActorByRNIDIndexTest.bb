Strict
EnableGC

; Regression test pinning the ActorByRNID O(1) sender-resolution index
; in Actors.bb (the Dim ActorByRNID.ActorInstance(MaxRNID), the
; FindActorInstanceFromRNID rewrite, and the maintenance hooks at
; login / logout / FreeActorInstance).
;
; The index replaces a `For Each ActorInstance` walk that ran on every
; inbound packet -- 27 callers in ServerNet.bb plus one in Scripting.bb.
; With hundreds of NPCs + spawned mobs in a typical loaded zone, the
; walk was the dominant per-tick cost; the index makes it O(1) +
; bounds check.
;
; Actors.bb pulls the network/world graph and can't be Included into a
; Strict test build. Strict mode also rejects module-level Dim arrays
; being assigned to inside Functions (the parser flags
; `ShadowArr(X) = Y` as needing local/global/const modifier even when
; the Dim is in scope). Per the GC-race memory, we sidestep both by
; replicating only the lookup + maintenance hook PREDICATES on a tiny
; fixed set of named slots that cover the boundary cases (1, 2, 100,
; 500, 4999, 5000). Any production change to the lookup or maintenance
; hooks MUST update this file; the duplication is the trigger to
; refresh the test rationale.

; --- Replicated state machine ---------------------------------------

Const TestMaxRNID% = 5000

; Six named slots covering boundary cases the production code's bounds
; check needs to handle:
;   Slot1     = lower boundary (lowest valid RNID)
;   Slot2     = adjacent to lower boundary
;   Slot100   = middle-of-range typical usage
;   Slot500   = sparse middle-of-range
;   Slot4999  = adjacent to upper boundary
;   Slot5000  = upper boundary (= MaxRNID)
Global Slot1%      = 0
Global Slot2%      = 0
Global Slot100%    = 0
Global Slot500%    = 0
Global Slot4999%   = 0
Global Slot5000%   = 0

; Read a slot by RNID. Returns 0 for any RNID not in our fixed set --
; that exactly mirrors the production array semantics for empty slots
; (unindexed RNIDs read as Null/0).
Function SlotGet%(RNID%)
	If RNID = 1 Then Return Slot1
	If RNID = 2 Then Return Slot2
	If RNID = 100 Then Return Slot100
	If RNID = 500 Then Return Slot500
	If RNID = 4999 Then Return Slot4999
	If RNID = TestMaxRNID Then Return Slot5000
	Return 0
End Function

; Write a slot by RNID. Returns True if the slot was a known test
; boundary, False otherwise (those RNIDs simulate the "unindexed"
; cases the production code also handles harmlessly).
Function SlotSet%(RNID%, Value%)
	If RNID = 1 Then Slot1 = Value : Return True
	If RNID = 2 Then Slot2 = Value : Return True
	If RNID = 100 Then Slot100 = Value : Return True
	If RNID = 500 Then Slot500 = Value : Return True
	If RNID = 4999 Then Slot4999 = Value : Return True
	If RNID = TestMaxRNID Then Slot5000 = Value : Return True
	Return False
End Function

; Replicates Actors.bb's bounds-checked lookup:
;   If RNID < 1 Or RNID > MaxRNID Then Return Null
;   Return ActorByRNID(RNID)
Function LookupRNID%(RNID%)
	If RNID < 1 Or RNID > TestMaxRNID Then Return 0
	Return SlotGet(RNID)
End Function

; Replicates the login maintenance hook (ServerNet.bb P_StartGame):
;   If M\FromID > 0 And M\FromID <= MaxRNID
;       ActorByRNID(M\FromID) = A\Character[Number]
;   EndIf
Function LoginHook(RNID%, ActorIdent%)
	If RNID < 1 Or RNID > TestMaxRNID Then Return
	SlotSet(RNID, ActorIdent)
End Function

; Replicates the logout / free maintenance hook (ServerNet.bb / Actors.bb):
;   If A\RNID > 0 And A\RNID <= MaxRNID
;       If ActorByRNID(A\RNID) = A Then ActorByRNID(A\RNID) = Null
;   EndIf
;
; The defensive `= AI` check matters because RottNet may reuse a
; connection ID. If a relogin has already populated the slot with a
; new actor when this fires, we don't want to clobber it.
Function LogoutOrFreeHook(RNID%, ActorIdent%)
	If RNID < 1 Or RNID > TestMaxRNID Then Return
	If SlotGet(RNID) = ActorIdent Then SlotSet(RNID, 0)
End Function

; Helper: reset the shadow between tests.
Function ResetShadow()
	Slot1 = 0
	Slot2 = 0
	Slot100 = 0
	Slot500 = 0
	Slot4999 = 0
	Slot5000 = 0
End Function

; ====================================================================
; Bounds checks on the lookup -- match the new function's contract
; ====================================================================

Test testLookupBelowRange()
	ResetShadow()
	; RNID = 0 means "not in game" -- never indexed. Lookup returns Null.
	Assert(LookupRNID%(0) = 0)
End Test

Test testLookupNegative()
	ResetShadow()
	; RNID = -1 means "AI actor" -- never indexed. Out-of-bounds rejected.
	Assert(LookupRNID%(-1) = 0)
End Test

Test testLookupNegativeLarge()
	ResetShadow()
	; A wire-injected negative value must not index into the array.
	; (Blitz3D would crash on negative array index without the bounds
	; check.)
	Assert(LookupRNID%(-32768) = 0)
End Test

Test testLookupAboveRange()
	ResetShadow()
	; RNID 5001 is past the host's 5000-player cap.
	Assert(LookupRNID%(TestMaxRNID + 1) = 0)
End Test

Test testLookupFarAbove()
	ResetShadow()
	; A wire-injected large value must not index past the array end.
	Assert(LookupRNID%(2147483647) = 0)
End Test

Test testLookupBoundaryUpper()
	ResetShadow()
	; The exact MaxRNID slot is valid -- it's in [1, MaxRNID] inclusive.
	LoginHook(TestMaxRNID, 42)
	Assert(LookupRNID%(TestMaxRNID) = 42)
End Test

Test testLookupBoundaryLower()
	ResetShadow()
	; RNID = 1 is the lowest valid connection ID.
	LoginHook(1, 42)
	Assert(LookupRNID%(1) = 42)
End Test

Test testLookupEmptySlot()
	ResetShadow()
	; A valid-range RNID whose slot has never been populated returns
	; Null (not a stale value from a prior lifecycle).
	Assert(LookupRNID%(100) = 0)
End Test

; ====================================================================
; Login + logout lifecycle -- the load-bearing test
; ====================================================================

Test testLoginPopulatesIndex()
	ResetShadow()
	LoginHook(100, 7)
	Assert(LookupRNID%(100) = 7)
End Test

Test testLogoutClearsIndex()
	ResetShadow()
	LoginHook(100, 7)
	LogoutOrFreeHook(100, 7)
	Assert(LookupRNID%(100) = 0)
End Test

Test testReloginPopulatesSameSlot()
	ResetShadow()
	; Logout / relogin with the same RNID (RottNet may reuse connection
	; IDs). The slot is cleared on logout, then populated again on
	; relogin.
	LoginHook(100, 7)
	LogoutOrFreeHook(100, 7)
	LoginHook(100, 8)
	Assert(LookupRNID%(100) = 8)
End Test

Test testReloginDifferentRNIDDoesntAffectOld()
	ResetShadow()
	LoginHook(100, 7)
	LogoutOrFreeHook(100, 7)
	LoginHook(500, 8)
	Assert(LookupRNID%(100) = 0)
	Assert(LookupRNID%(500) = 8)
End Test

; ====================================================================
; Defensive `= AI` check on logout / free -- pins the production
; pattern that protects against RottNet RNID reuse races.
; ====================================================================

Test testLogoutDoesNotClobberReusedSlot()
	ResetShadow()
	; Pre-condition: actor 7 was at RNID 100.
	LoginHook(100, 7)
	; Race: a different actor (8) wins the same RNID slot before the
	; logout cleanup for actor 7 runs. This can happen if RottNet
	; recycles the connection ID and the relogin completes before
	; the old session's disconnect handler executes.
	SlotSet(100, 8)
	; Now actor 7's logout fires. The defensive `= AI` check (the
	; `If SlotGet(RNID) = ActorIdent` guard) prevents it from
	; clobbering actor 8's freshly-populated slot.
	LogoutOrFreeHook(100, 7)
	Assert(LookupRNID%(100) = 8)
End Test

; ====================================================================
; Multiple actors -- spot-check that distinct RNIDs don't collide
; ====================================================================

Test testManyActorsCoexist()
	ResetShadow()
	LoginHook(1, 100)
	LoginHook(2, 200)
	LoginHook(500, 300)
	LoginHook(4999, 400)
	LoginHook(TestMaxRNID, 500)
	Assert(LookupRNID%(1) = 100)
	Assert(LookupRNID%(2) = 200)
	Assert(LookupRNID%(500) = 300)
	Assert(LookupRNID%(4999) = 400)
	Assert(LookupRNID%(TestMaxRNID) = 500)
End Test

Test testLogoutOneDoesNotAffectOthers()
	ResetShadow()
	LoginHook(1, 100)
	LoginHook(2, 200)
	LoginHook(500, 300)
	LogoutOrFreeHook(2, 200)
	Assert(LookupRNID%(1) = 100)
	Assert(LookupRNID%(2) = 0)
	Assert(LookupRNID%(500) = 300)
End Test
