; Tests for the position-float sanitisation in ReadActorInstance
; (Modules/Actors.bb). The character load path reads X/Y/Z off disk and,
; as of the position-float-clamp fix, routes each through ClampWorldCoord#
; before storing it on the ActorInstance. A corrupted or tampered
; Accounts.dat row could otherwise carry a NaN / Inf / out-of-range
; position that, loaded raw, flows into the broadcast actor state
; P_StandardUpdate replicates to every client and poisons spatial code
; (collision, LOD culling, EntityDistance#) for the whole zone.
;
; What this pins that ClampFloatTest.bb does NOT:
;   ClampFloatTest exercises ClampWorldCoord# on float *literals*. This
;   file exercises the *load-path contract*: a poisoned float written to a
;   Bank and read back (the same 4-byte IEEE round-trip WriteFloat/ReadFloat
;   perform against the save stream) still arrives poisoned, and the
;   read-then-clamp pattern at the load site neutralises it -- while a
;   legitimate saved position survives the round-trip and passes through
;   unchanged. This is the regression that would re-open if someone dropped
;   the ClampWorldCoord# wrapper from the ReadFloat# calls in
;   ReadActorInstance.
;
; Actors.bb itself can't be Included into a test build (it pulls in the
; Items / world graph). Per the established pattern (see ClampFloatTest.bb
; and RCEWireEncodingTest.bb), replicate the helper verbatim and the
; load-site pattern (PeekFloat after a PokeFloat = ReadFloat after a
; WriteFloat). A refactor that changes ClampWorldCoord# must update the
; production copy and this duplicate -- the trigger to refresh this test.
;
; NOT Strict: ClampWorldCoord#'s param is a bare float in production and
; can't take a Local Strict typing; matches ClampFloatTest.bb.

Const WorldCoordMax# = 100000.0
Function ClampWorldCoord#(v#)
	If v# > -WorldCoordMax# And v# < WorldCoordMax# Then Return v#
	Return 0.0
End Function

; Round-trip a float through a 4-byte Bank exactly as WriteFloat/ReadFloat
; do against the save stream, returning the read-back value. NaN and Inf
; bit patterns survive a PokeFloat/PeekFloat round-trip unchanged, which is
; precisely why the clamp has to happen AFTER the read, not be assumed away
; by the serializer.
Function RoundTripFloat#(v#)
	Local b = CreateBank(4)
	PokeFloat(b, 0, v#)
	Local out# = PeekFloat(b, 0)
	FreeBank(b)
	Return out#
End Function


; A NaN saved to disk survives the Bank round-trip (still NaN) and the
; load-site clamp collapses it to 0. Without the clamp this NaN would reach
; broadcast actor state. NaN is constructed via runtime vars so the
; compiler can't constant-fold it.
Test testLoadedNaNPositionIsClamped()
	Local zero# = 0.0
	Local nan# = zero# / zero#
	; Sanity: a NaN really does survive the serialise/deserialise round-trip.
	Local loaded# = RoundTripFloat#(nan#)
	; A surviving NaN fails every ordered comparison, so it is NOT in range.
	Assert(Not (loaded# > -WorldCoordMax# And loaded# < WorldCoordMax#))
	; The load-site pattern neutralises it.
	Assert(ClampWorldCoord#(loaded#) = 0.0)
End Test

; An out-of-range magnitude (e.g. a corrupted exponent byte) survives the
; round-trip and is clamped to 0 at the load site.
Test testLoadedOutOfRangePositionIsClamped()
	; BlitzForge has no scientific-notation float literal -- use a plain
	; decimal well outside the +/-WorldCoordMax (100000) window.
	Local huge# = 1000000.0
	Assert(ClampWorldCoord#(RoundTripFloat#(huge#)) = 0.0)
	Assert(ClampWorldCoord#(RoundTripFloat#(-huge#)) = 0.0)
End Test

; A legitimate saved position survives the round-trip and passes through
; the clamp unchanged -- the fix is non-destructive for real save data.
Test testLoadedValidPositionPassesThrough()
	Assert(ClampWorldCoord#(RoundTripFloat#(4096.5)) = 4096.5)
	Assert(ClampWorldCoord#(RoundTripFloat#(-2048.25)) = -2048.25)
	Assert(ClampWorldCoord#(RoundTripFloat#(0.0)) = 0.0)
End Test
