Strict
; EnableGC intentionally omitted -- this test creates many small
; Type instances and chains them via Field references. With GC on,
; the runtime stack-overflows during chain walks (see
; feedback_blitzforge_test_handle_object_gc memory). Strict-only is
; sufficient for the chain-correctness assertions; no Handle/Object
; round-trip is needed.

; Regression test pinning the FirstOnlinePlayer / NextOnlinePlayer
; chain in Actors.bb (Phase 3 of the ActorByRNID multi-iteration
; initiative -- see PR #282 for Phase 1).
;
; The chain replaces a `For Each ActorInstance / If A2\RNID > 0` walk
; that ran on:
;   * The per-tick standard-update broadcast in GameServer.bb's
;     UpdateActorInstances (the dominant per-frame cost on a server
;     with many NPCs / spawned mobs).
;   * Six chat-broadcast loops in ServerNet.bb (/yell /gm /g /pm
;     /allplayers /warpother).
;
; Actors.bb's chain helpers (OnlinePlayerInsert / OnlinePlayerRemove)
; can't be exercised directly because ActorInstance pulls the whole
; network/world graph. Following the established replicated-gate
; pattern, this file rebuilds the chain logic against a tiny mock
; Type whose field shape matches ActorInstance's NextOnlinePlayer
; layout. Any production change to the helpers MUST update this file;
; the duplication is the trigger to refresh the test rationale.

; Mock actor used in place of ActorInstance. Only field we need is
; the chain-next link.
Type MockActor
	Field Name$
	Field NextOnline.MockActor
End Type

Global MockHead.MockActor = Null

; Replicates Actors.bb's OnlinePlayerInsert -- head-insert with
; presence dedup.
Function MockInsert(A.MockActor)
	If A = Null Then Return
	Local Cursor.MockActor = MockHead
	While Cursor <> Null
		If Cursor = A Then Return
		Cursor = Cursor\NextOnline
	Wend
	A\NextOnline = MockHead
	MockHead = A
End Function

; Replicates Actors.bb's OnlinePlayerRemove -- walk-to-find-predecessor.
Function MockRemove(A.MockActor)
	If A = Null Then Return
	If MockHead = Null Then Return
	If MockHead = A
		MockHead = A\NextOnline
		A\NextOnline = Null
		Return
	EndIf
	Local Prev.MockActor = MockHead
	While Prev\NextOnline <> Null
		If Prev\NextOnline = A
			Prev\NextOnline = A\NextOnline
			A\NextOnline = Null
			Return
		EndIf
		Prev = Prev\NextOnline
	Wend
End Function

; Helper: count chain length.
Function ChainLen%()
	Local Count% = 0
	Local Cursor.MockActor = MockHead
	While Cursor <> Null
		Count = Count + 1
		Cursor = Cursor\NextOnline
	Wend
	Return Count
End Function

; Helper: True if A is in the chain.
Function ChainContains%(A.MockActor)
	Local Cursor.MockActor = MockHead
	While Cursor <> Null
		If Cursor = A Then Return True
		Cursor = Cursor\NextOnline
	Wend
	Return False
End Function

Function ResetChain()
	; Sweep-walk the entire MockActor global type pool and Delete every
	; instance. The earlier shape only detached NextOnline pointers along
	; MockHead's chain, leaving the underlying instances pinned in the
	; type pool. Across 14 tests that's ~36 instances accumulated by the
	; suite's end -- enough for Blitz3D's process-exit cleanup to blow
	; the stack ("Error: Stack overflow!" after testRemoveAllOneByOne).
	; This was the long-running CI flake at ~30-40% rate.
	;
	; For-Each + Delete-current is safe in BlitzForge per the verified
	; basic.cpp ref-counting walk (see feedback_blitzforge_enablegc_
	; requires_strict memory + PR #302 reviewer's basic.cpp dig). The
	; iterator holds a BBObj-level ref on the current element, so
	; Delete decrements 2->1 and the obj stays linked in the used-list
	; until obj->next has been consumed.
	Local entry.MockActor
	For entry = Each MockActor
		Delete entry
	Next
	MockHead = Null
End Function

; ====================================================================
; Insert: head-insert + dedup
; ====================================================================

Test testInsertOnEmptyChain()
	ResetChain()
	Local A.MockActor = New MockActor() : A\Name = "A"
	MockInsert(A)
	Assert(ChainLen%() = 1)
	Assert(MockHead = A)
End Test

Test testInsertManyHeadInserts()
	ResetChain()
	Local A.MockActor = New MockActor() : A\Name = "A"
	Local B.MockActor = New MockActor() : B\Name = "B"
	Local C.MockActor = New MockActor() : C\Name = "C"
	MockInsert(A)
	MockInsert(B)
	MockInsert(C)
	; Head-insert puts newest at front: C -> B -> A.
	Assert(MockHead = C)
	Assert(C\NextOnline = B)
	Assert(B\NextOnline = A)
	Assert(A\NextOnline = Null)
	Assert(ChainLen%() = 3)
End Test

Test testInsertDedupSilent()
	; Production OnlinePlayerInsert is idempotent -- a buggy caller
	; that inserts the same actor twice should not create a cycle.
	ResetChain()
	Local A.MockActor = New MockActor() : A\Name = "A"
	MockInsert(A)
	MockInsert(A)
	MockInsert(A)
	Assert(ChainLen%() = 1)
End Test

Test testInsertNullIsNoOp()
	ResetChain()
	MockInsert(Null)
	Assert(ChainLen%() = 0)
	Assert(MockHead = Null)
End Test

; ====================================================================
; Remove: head removal, middle removal, tail removal
; ====================================================================

Test testRemoveHead()
	ResetChain()
	Local A.MockActor = New MockActor() : A\Name = "A"
	Local B.MockActor = New MockActor() : B\Name = "B"
	Local C.MockActor = New MockActor() : C\Name = "C"
	MockInsert(A) : MockInsert(B) : MockInsert(C)
	; Chain: C -> B -> A.
	MockRemove(C)
	Assert(MockHead = B)
	Assert(ChainLen%() = 2)
	Assert(C\NextOnline = Null)
End Test

Test testRemoveMiddle()
	ResetChain()
	Local A.MockActor = New MockActor() : A\Name = "A"
	Local B.MockActor = New MockActor() : B\Name = "B"
	Local C.MockActor = New MockActor() : C\Name = "C"
	MockInsert(A) : MockInsert(B) : MockInsert(C)
	; Chain: C -> B -> A. Remove B.
	MockRemove(B)
	Assert(MockHead = C)
	Assert(C\NextOnline = A)
	Assert(A\NextOnline = Null)
	Assert(ChainLen%() = 2)
	Assert(B\NextOnline = Null)
End Test

Test testRemoveTail()
	ResetChain()
	Local A.MockActor = New MockActor() : A\Name = "A"
	Local B.MockActor = New MockActor() : B\Name = "B"
	Local C.MockActor = New MockActor() : C\Name = "C"
	MockInsert(A) : MockInsert(B) : MockInsert(C)
	; Chain: C -> B -> A. Remove A (tail).
	MockRemove(A)
	Assert(MockHead = C)
	Assert(C\NextOnline = B)
	Assert(B\NextOnline = Null)
	Assert(ChainLen%() = 2)
End Test

Test testRemoveSingleElement()
	ResetChain()
	Local A.MockActor = New MockActor() : A\Name = "A"
	MockInsert(A)
	MockRemove(A)
	Assert(MockHead = Null)
	Assert(ChainLen%() = 0)
End Test

Test testRemoveAbsentIsNoOp()
	; Production FreeActorInstance calls OnlinePlayerRemove on every
	; actor (NPCs, never-logged-in characters). The remove must be
	; safe when A isn't in the chain.
	ResetChain()
	Local A.MockActor = New MockActor() : A\Name = "A"
	Local B.MockActor = New MockActor() : B\Name = "B"
	MockInsert(A)
	; B was never inserted.
	MockRemove(B)
	Assert(MockHead = A)
	Assert(ChainLen%() = 1)
End Test

Test testRemoveFromEmptyChainIsNoOp()
	ResetChain()
	Local A.MockActor = New MockActor() : A\Name = "A"
	MockRemove(A)
	Assert(MockHead = Null)
	Assert(ChainLen%() = 0)
End Test

Test testRemoveNullIsNoOp()
	ResetChain()
	Local A.MockActor = New MockActor() : A\Name = "A"
	MockInsert(A)
	MockRemove(Null)
	Assert(ChainLen%() = 1)
End Test

; ====================================================================
; Lifecycle: insert + remove + re-insert (relogin pattern)
; ====================================================================

Test testReloginPattern()
	ResetChain()
	Local A.MockActor = New MockActor() : A\Name = "A"
	MockInsert(A)
	MockRemove(A)
	Assert(ChainLen%() = 0)
	MockInsert(A)
	Assert(ChainLen%() = 1)
	Assert(MockHead = A)
End Test

Test testManyLoginsLogoutsInterleaved()
	ResetChain()
	Local A.MockActor = New MockActor() : A\Name = "A"
	Local B.MockActor = New MockActor() : B\Name = "B"
	Local C.MockActor = New MockActor() : C\Name = "C"
	MockInsert(A)
	MockInsert(B)
	MockRemove(A)
	MockInsert(C)
	; Chain should be: C -> B (A removed).
	Assert(ChainLen%() = 2)
	Assert(ChainContains%(A) = False)
	Assert(ChainContains%(B) = True)
	Assert(ChainContains%(C) = True)
End Test

Test testRemoveAllOneByOne()
	ResetChain()
	Local A.MockActor = New MockActor() : A\Name = "A"
	Local B.MockActor = New MockActor() : B\Name = "B"
	Local C.MockActor = New MockActor() : C\Name = "C"
	MockInsert(A) : MockInsert(B) : MockInsert(C)
	MockRemove(B)
	MockRemove(C)
	MockRemove(A)
	Assert(MockHead = Null)
	Assert(ChainLen%() = 0)
End Test

; ====================================================================
; Trailing pool sweep -- Phase 2 of the CI flake fix.
; ====================================================================
;
; PR #313 added ResetChain() at the START of every Test. That capped
; the live MockActor count per-test but left the LAST test's
; instances pinned in the type pool through process exit --
; testRemoveAllOneByOne above allocates 3 MockActors and the per-test
; ResetChain pattern cannot sweep the suite's tail. BlitzCC's
; process-exit pool walk is recursive in some shapes; 3 trailing
; instances are enough to trip `Error: Stack overflow! [FAIL]
; OnlinePlayerChainTest.bb` non-deterministically under CI scheduling
; (~10-15% post-PR#313 rate; close+reopen retry was the workaround).
;
; This Test runs LAST because BlitzForge's test runner walks `Test`
; declarations in source order (verified by reading
; compiler/BlitzForge/src/parser.cpp `case TEST`). Its body sweeps
; everything testRemoveAllOneByOne left behind, bringing the
; MockActor count to zero before BlitzCC's exit-time pool walker
; fires.
;
; **Residual flake rate: ~5-7%** (50-run reviewer sample on PR
; #322). Draining the MockActor pool is a contributor but not the
; sole driver of the exit-time stack-overflow -- the remaining
; failures occur with `Active objects: 0` after this sweep. Full
; elimination requires the BlitzCC runtime pool-walker recursion
; fix (compiler submodule work, out of scope here). The documented
; `gh pr close N && reopen` retry remains the workaround for the
; residual.
;
; Belt-and-suspenders against future additions: any new Test inserted
; between testRemoveAllOneByOne and this teardown still gets swept
; because its own start-of-test ResetChain catches its predecessor's
; leftovers, and this teardown remains the file's last source
; position.
Test zzz_TeardownPoolSweep()
	ResetChain()
	; Verify the sweep actually drained the pool. If a future change
	; ever leaves entries behind here, the assert fires and points at
	; the new leak rather than letting it accumulate into a stack
	; overflow at process exit.
	Local entry.MockActor
	Local count% = 0
	For entry = Each MockActor
		count = count + 1
	Next
	Assert(count = 0)
End Test
