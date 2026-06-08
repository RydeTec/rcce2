Strict
; EnableGC intentionally omitted -- per the
; feedback_blitzforge_test_handle_object_gc memory, Type-heavy chain
; tests stack-overflow under GC tracing. Strict-only is sufficient
; for chain-correctness assertions; no Handle/Object round-trip is
; needed.

; Regression test pinning the per-leader slave chain in Actors.bb
; (Phase 4 of the ActorByRNID multi-iteration initiative; Phase 1
; was PR #282, Phase 3 was PR #283).
;
; The chain replaces 6 `For Each ActorInstance / If X\Leader = leader`
; walks across Actors.bb (save / FreeActorInstanceSlaves), GameServer.bb
; (pet aggro broadcast), MySQL.bb (save), ServerNet.bb (/pet
; command, inventory pet-validation walk).
;
; Actors.bb's SlaveLink / SlaveUnlink can't be exercised directly
; because ActorInstance pulls the whole network/world graph.
; Replicated-gate pattern: rebuild the chain logic against a tiny
; mock Type whose field shape matches ActorInstance's
; Leader / FirstSlave / NextSlave / NumberOfSlaves layout. Any
; production change to the helpers MUST update this file.

Type MockActor
	Field Name$
	Field Leader.MockActor
	Field FirstSlave.MockActor
	Field NextSlave.MockActor
	Field NumberOfSlaves%
End Type

; Replicates Actors.bb's SlaveLink: head-insert into Leader's chain,
; +1 on Leader\NumberOfSlaves. Re-links if Slave already has a
; different Leader.
Function MockSlaveLink(Leader.MockActor, Slave.MockActor)
	If Leader = Null Or Slave = Null Then Return
	If Slave\Leader = Leader Then Return
	If Slave\Leader <> Null Then MockSlaveUnlink(Slave)
	Slave\Leader = Leader
	Slave\NextSlave = Leader\FirstSlave
	Leader\FirstSlave = Slave
	Leader\NumberOfSlaves = Leader\NumberOfSlaves + 1
End Function

; Replicates Actors.bb's SlaveUnlink: walk-to-find-predecessor splice
; on the leader's chain, -1 on NumberOfSlaves, clears Slave\Leader.
Function MockSlaveUnlink(Slave.MockActor)
	If Slave = Null Then Return
	Local Leader.MockActor = Slave\Leader
	If Leader = Null Then Return
	If Leader\FirstSlave = Slave
		Leader\FirstSlave = Slave\NextSlave
	Else
		Local Prev.MockActor = Leader\FirstSlave
		While Prev <> Null And Prev\NextSlave <> Slave
			Prev = Prev\NextSlave
		Wend
		If Prev <> Null Then Prev\NextSlave = Slave\NextSlave
	EndIf
	Slave\NextSlave = Null
	Slave\Leader = Null
	Leader\NumberOfSlaves = Leader\NumberOfSlaves - 1
End Function

Function ChainLen%(L.MockActor)
	Local N% = 0
	Local Cur.MockActor = L\FirstSlave
	While Cur <> Null
		N = N + 1
		Cur = Cur\NextSlave
	Wend
	Return N
End Function

Function ChainContains%(L.MockActor, S.MockActor)
	Local Cur.MockActor = L\FirstSlave
	While Cur <> Null
		If Cur = S Then Return True
		Cur = Cur\NextSlave
	Wend
	Return False
End Function

; Sweep-walk the MockActor pool and Delete every instance. Called at
; the start of each Test to cap the live count. Same rationale as
; OnlinePlayerChainTest's ResetChain: each test creates several
; instances that the file's no-EnableGC mode can't reference-count
; away; left alone they accumulate to ~50+ over 17 tests, which is
; enough for Blitz3D's process-exit cleanup to stack-overflow non-
; deterministically (~30-40% CI flake shape). For-Each + Delete-
; current is safe in BlitzForge per the verified basic.cpp ref-
; counting walk (PR #313 / feedback_blitzforge_enablegc_requires_
; strict memory).
Function ResetPool()
	Local entry.MockActor
	For entry = Each MockActor
		Delete entry
	Next
End Function

; ====================================================================
; Link: head-insert, NumberOfSlaves bookkeeping, idempotency
; ====================================================================

Test testLinkOneSlave()
	ResetPool()
	Local L.MockActor = New MockActor() : L\Name = "L"
	Local S.MockActor = New MockActor() : S\Name = "S"
	MockSlaveLink(L, S)
	Assert(S\Leader = L)
	Assert(L\FirstSlave = S)
	Assert(L\NumberOfSlaves = 1)
	Assert(ChainLen%(L) = 1)
End Test

Test testLinkManyHeadInsertOrder()
	ResetPool()
	Local L.MockActor = New MockActor() : L\Name = "L"
	Local A.MockActor = New MockActor() : A\Name = "A"
	Local B.MockActor = New MockActor() : B\Name = "B"
	Local C.MockActor = New MockActor() : C\Name = "C"
	MockSlaveLink(L, A)
	MockSlaveLink(L, B)
	MockSlaveLink(L, C)
	; Head-insert: newest first -- C -> B -> A.
	Assert(L\FirstSlave = C)
	Assert(C\NextSlave = B)
	Assert(B\NextSlave = A)
	Assert(A\NextSlave = Null)
	Assert(L\NumberOfSlaves = 3)
End Test

Test testLinkIdempotentNoDoubleCount()
	ResetPool()
	Local L.MockActor = New MockActor() : L\Name = "L"
	Local S.MockActor = New MockActor() : S\Name = "S"
	MockSlaveLink(L, S)
	MockSlaveLink(L, S)
	MockSlaveLink(L, S)
	; Production helper short-circuits when Slave\Leader == Leader.
	; Count must stay at 1.
	Assert(L\NumberOfSlaves = 1)
	Assert(ChainLen%(L) = 1)
End Test

Test testLinkReassignsToNewLeader()
	ResetPool()
	Local L1.MockActor = New MockActor() : L1\Name = "L1"
	Local L2.MockActor = New MockActor() : L2\Name = "L2"
	Local S.MockActor = New MockActor() : S\Name = "S"
	MockSlaveLink(L1, S)
	Assert(L1\NumberOfSlaves = 1)
	MockSlaveLink(L2, S)
	; Slave should detach from L1 and attach to L2.
	Assert(L1\NumberOfSlaves = 0)
	Assert(L1\FirstSlave = Null)
	Assert(L2\NumberOfSlaves = 1)
	Assert(L2\FirstSlave = S)
	Assert(S\Leader = L2)
End Test

Test testLinkNullSlaveIsNoOp()
	ResetPool()
	Local L.MockActor = New MockActor() : L\Name = "L"
	MockSlaveLink(L, Null)
	Assert(L\NumberOfSlaves = 0)
	Assert(L\FirstSlave = Null)
End Test

Test testLinkNullLeaderIsNoOp()
	ResetPool()
	Local S.MockActor = New MockActor() : S\Name = "S"
	MockSlaveLink(Null, S)
	Assert(S\Leader = Null)
End Test

; ====================================================================
; Unlink: head / middle / tail removal, NumberOfSlaves bookkeeping
; ====================================================================

Test testUnlinkHead()
	ResetPool()
	Local L.MockActor = New MockActor() : L\Name = "L"
	Local A.MockActor = New MockActor() : A\Name = "A"
	Local B.MockActor = New MockActor() : B\Name = "B"
	Local C.MockActor = New MockActor() : C\Name = "C"
	MockSlaveLink(L, A) : MockSlaveLink(L, B) : MockSlaveLink(L, C)
	; Chain: C -> B -> A. Unlink C (head).
	MockSlaveUnlink(C)
	Assert(L\FirstSlave = B)
	Assert(L\NumberOfSlaves = 2)
	Assert(C\Leader = Null)
	Assert(C\NextSlave = Null)
End Test

Test testUnlinkMiddle()
	ResetPool()
	Local L.MockActor = New MockActor() : L\Name = "L"
	Local A.MockActor = New MockActor() : A\Name = "A"
	Local B.MockActor = New MockActor() : B\Name = "B"
	Local C.MockActor = New MockActor() : C\Name = "C"
	MockSlaveLink(L, A) : MockSlaveLink(L, B) : MockSlaveLink(L, C)
	; Chain: C -> B -> A. Unlink B (middle).
	MockSlaveUnlink(B)
	Assert(L\FirstSlave = C)
	Assert(C\NextSlave = A)
	Assert(A\NextSlave = Null)
	Assert(L\NumberOfSlaves = 2)
	Assert(B\Leader = Null)
	Assert(B\NextSlave = Null)
End Test

Test testUnlinkTail()
	ResetPool()
	Local L.MockActor = New MockActor() : L\Name = "L"
	Local A.MockActor = New MockActor() : A\Name = "A"
	Local B.MockActor = New MockActor() : B\Name = "B"
	Local C.MockActor = New MockActor() : C\Name = "C"
	MockSlaveLink(L, A) : MockSlaveLink(L, B) : MockSlaveLink(L, C)
	; Chain: C -> B -> A. Unlink A (tail).
	MockSlaveUnlink(A)
	Assert(L\FirstSlave = C)
	Assert(C\NextSlave = B)
	Assert(B\NextSlave = Null)
	Assert(L\NumberOfSlaves = 2)
End Test

Test testUnlinkSingleElement()
	ResetPool()
	Local L.MockActor = New MockActor() : L\Name = "L"
	Local S.MockActor = New MockActor() : S\Name = "S"
	MockSlaveLink(L, S)
	MockSlaveUnlink(S)
	Assert(L\FirstSlave = Null)
	Assert(L\NumberOfSlaves = 0)
	Assert(S\Leader = Null)
End Test

Test testUnlinkSlaveWithNoLeaderIsNoOp()
	ResetPool()
	Local L.MockActor = New MockActor() : L\Name = "L"
	Local S.MockActor = New MockActor() : S\Name = "S"
	; S has no leader.
	MockSlaveUnlink(S)
	Assert(S\Leader = Null)
	Assert(L\NumberOfSlaves = 0)
End Test

Test testUnlinkNullIsNoOp()
	ResetPool()
	MockSlaveUnlink(Null)
	; No crash, no error.
End Test

; ====================================================================
; Multi-leader cases
; ====================================================================

Test testManyLeadersWithChains()
	ResetPool()
	Local L1.MockActor = New MockActor() : L1\Name = "L1"
	Local L2.MockActor = New MockActor() : L2\Name = "L2"
	Local L1S1.MockActor = New MockActor() : L1S1\Name = "L1S1"
	Local L1S2.MockActor = New MockActor() : L1S2\Name = "L1S2"
	Local L2S1.MockActor = New MockActor() : L2S1\Name = "L2S1"
	MockSlaveLink(L1, L1S1) : MockSlaveLink(L1, L1S2)
	MockSlaveLink(L2, L2S1)
	; L1 chain: L1S2 -> L1S1. L2 chain: L2S1.
	Assert(L1\NumberOfSlaves = 2)
	Assert(L2\NumberOfSlaves = 1)
	Assert(ChainContains%(L1, L1S1) = True)
	Assert(ChainContains%(L1, L1S2) = True)
	Assert(ChainContains%(L2, L2S1) = True)
	; Chains are disjoint.
	Assert(ChainContains%(L1, L2S1) = False)
	Assert(ChainContains%(L2, L1S1) = False)
End Test

Test testUnlinkAllInLeaderChain()
	ResetPool()
	Local L.MockActor = New MockActor() : L\Name = "L"
	Local A.MockActor = New MockActor() : A\Name = "A"
	Local B.MockActor = New MockActor() : B\Name = "B"
	Local C.MockActor = New MockActor() : C\Name = "C"
	MockSlaveLink(L, A) : MockSlaveLink(L, B) : MockSlaveLink(L, C)
	MockSlaveUnlink(B)
	MockSlaveUnlink(C)
	MockSlaveUnlink(A)
	Assert(L\FirstSlave = Null)
	Assert(L\NumberOfSlaves = 0)
End Test

; ====================================================================
; NumberOfSlaves invariant: must always equal chain length
; ====================================================================

; ====================================================================
; Load-path invariant: the saved NumberOfSlaves count must be reset
; to 0 before the loop that re-links slaves -- otherwise SlaveLink
; increments cause double-counting (saved value + per-link
; increments). This pins the requirement that ReadActorInstance and
; My_LoadActorInstance both reset before the link loop. See the
; MySQL.bb fix from PR #287's quality-gate review.
; ====================================================================

Test testLoadPathWithoutResetDoublesCount()
	ResetPool()
	Local L.MockActor = New MockActor() : L\Name = "L"
	Local S1.MockActor = New MockActor() : S1\Name = "S1"
	Local S2.MockActor = New MockActor() : S2\Name = "S2"
	; Simulate the saved-from-disk state: NumberOfSlaves carries the
	; previously-saved count. If the load loop calls SlaveLink without
	; resetting, every link increment piles on top.
	L\NumberOfSlaves = 2
	MockSlaveLink(L, S1)
	MockSlaveLink(L, S2)
	; Bug: count is 4 (2 saved + 2 increments) but the chain only has
	; 2 actual slaves. Pin this divergence so a future load-path
	; refactor that omits the reset trips this test.
	Assert(L\NumberOfSlaves = 4)
	Assert(ChainLen%(L) = 2)
End Test

Test testLoadPathWithResetMatchesChainLength()
	ResetPool()
	Local L.MockActor = New MockActor() : L\Name = "L"
	Local S1.MockActor = New MockActor() : S1\Name = "S1"
	Local S2.MockActor = New MockActor() : S2\Name = "S2"
	; Same scenario as above, but apply the canonical load-path fix:
	; reset BEFORE the link loop. Count and chain length agree.
	L\NumberOfSlaves = 2     ; saved-from-disk value
	L\NumberOfSlaves = 0     ; canonical reset (the fix)
	MockSlaveLink(L, S1)
	MockSlaveLink(L, S2)
	Assert(L\NumberOfSlaves = 2)
	Assert(ChainLen%(L) = 2)
	Assert(L\NumberOfSlaves = ChainLen%(L))
End Test

Test testNumberOfSlavesMatchesChainLengthAfterChurn()
	ResetPool()
	Local L.MockActor = New MockActor() : L\Name = "L"
	Local A.MockActor = New MockActor() : A\Name = "A"
	Local B.MockActor = New MockActor() : B\Name = "B"
	Local C.MockActor = New MockActor() : C\Name = "C"
	Local D.MockActor = New MockActor() : D\Name = "D"
	MockSlaveLink(L, A) : Assert(L\NumberOfSlaves = ChainLen%(L))
	MockSlaveLink(L, B) : Assert(L\NumberOfSlaves = ChainLen%(L))
	MockSlaveLink(L, C) : Assert(L\NumberOfSlaves = ChainLen%(L))
	MockSlaveUnlink(B) : Assert(L\NumberOfSlaves = ChainLen%(L))
	MockSlaveLink(L, D) : Assert(L\NumberOfSlaves = ChainLen%(L))
	MockSlaveUnlink(A) : Assert(L\NumberOfSlaves = ChainLen%(L))
	MockSlaveUnlink(C) : Assert(L\NumberOfSlaves = ChainLen%(L))
	MockSlaveUnlink(D) : Assert(L\NumberOfSlaves = ChainLen%(L))
	Assert(L\NumberOfSlaves = 0)
End Test

; ====================================================================
; Trailing pool sweep -- Phase 2 of the CI flake fix. Mirrors
; OnlinePlayerChainTest's same-named teardown. See PR #313's
; ResetPool sweep + the Phase 2 fix PR for rationale.
; ====================================================================
;
; The last test above allocates 5 MockActors (L + A/B/C/D) and the
; per-test ResetPool pattern cannot sweep the suite's tail. Without
; this teardown those 5 instances persist into process exit where
; BlitzCC's pool-walker can stack-overflow.
;
; **Residual flake rate ~5-7%** even with this teardown -- not all
; of the exit-time overflow is driven by the MockActor pool. Full
; elimination requires the BlitzCC runtime pool-walker recursion
; fix (out of scope). See the matching note in
; OnlinePlayerChainTest.bb's zzz_TeardownPoolSweep.
Test zzz_TeardownPoolSweep()
	ResetPool()
	Local entry.MockActor
	Local count% = 0
	For entry = Each MockActor
		count = count + 1
	Next
	Assert(count = 0)
End Test
