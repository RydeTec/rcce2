Strict
EnableGC

; Tests for the pet-orphaning invariant enforced in Actors.bb
; (FreeActorInstance's slave-orphan loop) and GameServer.bb (the AI tick's
; orphaned-pet backstop branch).
;
; Contract: a pet NPC has AIMode AI_Pet (5) or AI_PetChase (6) and a non-Null
; \Leader. The server AI tick's pet branches deref AI\Leader\X# / \AITarget /
; \Actor\Radius# with no Null guard. If a leader is freed while a pet still
; references it, the pet must be reverted to AI_Wait (0) with a cleared
; \Leader, or the next tick derefs a Null Leader -- a crash in debug builds,
; or a silent walk to world origin in release (skipped __bbNullObjEx). The
; fix resets AIMode at the source (FreeActorInstance orphan loop; BVM_SETLEADER
; already did) and adds a tick-side backstop that demotes any pet whose
; Leader has gone Null.
;
; Actors.bb / GameServer.bb can't be Included into a test build (they pull the
; Items / world graph / network externs), so the orphan loop and the tick
; demotion predicate are replicated verbatim here, per the established
; ClampFloatTest / SpellCooldownIndexTest convention. A change to either
; production copy must update this duplicate.

Const AI_Wait     = 0
Const AI_Pet      = 5
Const AI_PetChase = 6

Type MockActor
	Field Leader.MockActor
	Field FirstSlave.MockActor
	Field NextSlave.MockActor
	Field AITarget.MockActor
	Field AIMode
End Type

; Mirror of Actors.bb FreeActorInstance's orphan loop: walk the freed
; leader's slave chain, detach each surviving child and revert it to idle.
Function OrphanLeaderChildren(L.MockActor)
	Local Child.MockActor = L\FirstSlave
	Local ChildNext.MockActor = Null
	While Child <> Null
		ChildNext = Child\NextSlave
		Child\Leader = Null
		Child\NextSlave = Null
		Child\AIMode = AI_Wait
		Child\AITarget = Null
		Child = ChildNext
	Wend
	L\FirstSlave = Null
End Function

; Mirror of the GameServer.bb AI tick orphaned-pet backstop branch:
; (AIMode = AI_Pet Or AIMode = AI_PetChase) And Leader = Null -> demote.
Function TickDemoteIfOrphaned(M.MockActor)
	If (M\AIMode = AI_Pet Or M\AIMode = AI_PetChase) And M\Leader = Null
		M\AIMode = AI_Wait
		M\AITarget = Null
	EndIf
End Function


; Freeing a leader reverts every surviving pet child to AI_Wait with a
; cleared Leader -- the root-cause fix for the orphan-on-leader-free path.
Test testOrphanLoopResetsChildrenToWait()
	Local lead.MockActor = New MockActor()
	Local foe.MockActor = New MockActor()
	Local c1.MockActor = New MockActor()
	Local c2.MockActor = New MockActor()
	c1\AIMode = AI_Pet      : c1\Leader = lead : c1\AITarget = foe
	c2\AIMode = AI_PetChase : c2\Leader = lead : c2\AITarget = foe
	; Head-insert chain as SlaveLink builds it: FirstSlave -> c2 -> c1.
	lead\FirstSlave = c2 : c2\NextSlave = c1

	OrphanLeaderChildren(lead)

	Assert(lead\FirstSlave = Null)
	Assert(c1\Leader = Null) : Assert(c1\AIMode = AI_Wait) : Assert(c1\AITarget = Null)
	Assert(c2\Leader = Null) : Assert(c2\AIMode = AI_Wait) : Assert(c2\AITarget = Null)
End Test

; Tick backstop: an AI_Pet whose leader is gone is demoted to idle.
Test testTickDemotesOrphanedPet()
	Local p.MockActor = New MockActor()
	p\AIMode = AI_Pet : p\Leader = Null
	TickDemoteIfOrphaned(p)
	Assert(p\AIMode = AI_Wait)
End Test

; Tick backstop covers AI_PetChase too, and clears the stale target.
Test testTickDemotesOrphanedPetChase()
	Local p.MockActor = New MockActor()
	Local foe.MockActor = New MockActor()
	p\AIMode = AI_PetChase : p\Leader = Null : p\AITarget = foe
	TickDemoteIfOrphaned(p)
	Assert(p\AIMode = AI_Wait)
	Assert(p\AITarget = Null)
End Test

; A pet WITH a live leader is left untouched (no spurious demotion).
Test testTickLeavesAttendedPet()
	Local lead.MockActor = New MockActor()
	Local p.MockActor = New MockActor()
	p\AIMode = AI_Pet : p\Leader = lead
	TickDemoteIfOrphaned(p)
	Assert(p\AIMode = AI_Pet)
	Assert(p\Leader = lead)
End Test

; A non-pet actor with a Null leader is NOT touched -- the backstop only
; fires for pet modes, so leaderless plain NPCs keep their AIMode.
Test testTickLeavesNonPetWithNullLeader()
	Local n.MockActor = New MockActor()
	n\AIMode = AI_Wait : n\Leader = Null
	TickDemoteIfOrphaned(n)
	Assert(n\AIMode = AI_Wait)
End Test
