Strict
EnableGC

; Tests for the client spell-cooldown indexing contract enforced by the
; SpellChargeReady / SetSpellCharge helpers in Modules/Interface3D.bb.
;
; SpellCharge[] is keyed by spell ID (0..999) -- see the field comment in
; Actors.bb and the authoritative server: ServerNet.bb's P_SpellUpdate "F"
; gates on AI\SpellCharge[SpellID] and GameServer.bb decrements 0..999 by
; spell ID. Before the fix, the client action bar keyed SpellCharge by a
; KnownSpells index (or a memorise slot in memorise mode) and the spellbook
; keyed it by a different KnownSpells index, so the same physical spell had
; up to three independent client cooldown slots -- none matching the
; server. Casting from the action bar then reading the spellbook (or vice
; versa) showed an incoherent cooldown.
;
; The regression this pins: BOTH client surfaces now resolve the spell ID
; and key SpellCharge by it, so the same spell ID always maps to the same
; slot. The helpers also bound-check (SpellCharge is Field[999]); a spell
; ID outside 0..999 is uncastable server-side and is treated as "ready"
; without an out-of-bounds access. The bound lives in a nested If because
; BlitzForge `And` is non-short-circuit.
;
; Interface3D.bb can't be Included into a test build (it pulls F-UI / Gooey
; / world graph / RakNet externs unavailable offline), so the two helpers
; are replicated verbatim here, per the established ClampFloatTest /
; RCEWireEncodingTest convention. A refactor that changes either helper has
; to update the production copy and this duplicate.

Type MockActor
	Field SpellCharge[999]   ; mirrors ActorInstance\SpellCharge[999]
End Type

Function SpellChargeReady(M.MockActor, SpellID)
	If SpellID < 0 Or SpellID > 999 Then Return True
	If M\SpellCharge[SpellID] <= 0 Then Return True
	Return False
End Function

Function SetSpellCharge(M.MockActor, SpellID, Charge)
	If SpellID < 0 Or SpellID > 999 Then Return
	M\SpellCharge[SpellID] = Charge
End Function


; A freshly-loaded actor (all slots 0) reports every trackable spell ready.
Test testFreshActorAllReady()
	Local m.MockActor = New MockActor()
	Assert(SpellChargeReady(m, 0) = True)
	Assert(SpellChargeReady(m, 42) = True)
	Assert(SpellChargeReady(m, 999) = True)
End Test

; Setting a cooldown by spell ID makes exactly that spell not-ready, and
; leaves other spell IDs untouched (no cross-slot bleed).
Test testSetMakesOnlyThatSpellNotReady()
	Local m.MockActor = New MockActor()
	SetSpellCharge(m, 42, 5000)
	Assert(SpellChargeReady(m, 42) = False)
	Assert(SpellChargeReady(m, 41) = True)
	Assert(SpellChargeReady(m, 43) = True)
End Test

; THE core regression: store and gate use the SAME slot for the SAME spell
; ID. The pre-fix bug stored at one index space (action bar) and read at
; another (spellbook); here the round-trip on a single spell ID is coherent.
Test testSameSpellIdSameSlot()
	Local m.MockActor = New MockActor()
	; "Action bar" cast stores by spell ID...
	SetSpellCharge(m, 314, 4000)
	; ..."spellbook" gate reads by the same spell ID and sees not-ready.
	Assert(SpellChargeReady(m, 314) = False)
End Test

; Out-of-range spell IDs are treated as ready and never index the array
; out of bounds (read or write).
Test testOutOfRangeIdsAreSafeAndReady()
	Local m.MockActor = New MockActor()
	Assert(SpellChargeReady(m, -1) = True)
	Assert(SpellChargeReady(m, 1000) = True)
	Assert(SpellChargeReady(m, 65534) = True)
	; A write past the trackable range is a silent no-op (no OOB, no crash).
	SetSpellCharge(m, 1500, 9999)
	SetSpellCharge(m, -5, 9999)
	Assert(SpellChargeReady(m, 1500) = True)
End Test

; The uniform 0..999 decrement (Interface3D recharge loop) eventually
; returns a charged spell to ready, keyed by spell ID.
Test testDecrementReturnsToReady()
	Local m.MockActor = New MockActor()
	SetSpellCharge(m, 7, 200)
	Assert(SpellChargeReady(m, 7) = False)
	; Two 100ms recharge ticks over the spell-ID slot.
	Local tick
	For tick = 1 To 2
		If m\SpellCharge[7] > 0 Then m\SpellCharge[7] = m\SpellCharge[7] - 100
	Next
	Assert(SpellChargeReady(m, 7) = True)
End Test
