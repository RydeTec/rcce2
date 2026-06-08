; Tests for ClampWorldCoord# and ClampSaneFloat# from Modules/RCEnet.bb.
;
; Both helpers reject NaN/Inf via the "If v > -MAX And v < MAX" comparison
; trick (NaN is unordered against all values, so both comparisons fail and
; the function returns 0). Every BVM command that broadcasts a script-
; supplied float (#237-#239) and the wire-receive sanitisation in
; ServerNet.bb P_InventoryUpdate "D" depend on this contract.
;
; The RCEnet.bb module itself can't be Included into a test build because
; it pulls in RakNet externs that aren't available offline. Replicate the
; two helpers verbatim here -- the test pins the *behaviour*; a refactor
; that changes either helper has to update both the production copy and
; this duplicate, which is the trigger to refresh the test rationale.
;
; Note: NOT Strict. Function param "v#" is bare-float in production and
; can't take a Local Strict typing; keeping this file non-Strict matches
; the production callsite.

Const WorldCoordMax# = 100000.0
Function ClampWorldCoord#(v#)
	If v# > -WorldCoordMax# And v# < WorldCoordMax# Then Return v#
	Return 0.0
End Function

Const FloatSanityMax# = 1000000000.0
Function ClampSaneFloat#(v#)
	If v# > -FloatSanityMax# And v# < FloatSanityMax# Then Return v#
	Return 0.0
End Function


; Normal in-range values pass through unchanged.
Test testClampWorldCoordPassesInRange()
	Assert(ClampWorldCoord#(0.0) = 0.0)
	Assert(ClampWorldCoord#(1234.5) = 1234.5)
	Assert(ClampWorldCoord#(-9876.5) = -9876.5)
	Assert(ClampWorldCoord#(99999.0) = 99999.0)
	Assert(ClampWorldCoord#(-99999.0) = -99999.0)
End Test

; Exactly at the boundary fails (strict less-than) -- this is intentional.
; Production sites that need inclusive bounds should clamp via the
; existing `If v >= -MAX And v <= MAX` pattern in the call site, not the
; helper.
Test testClampWorldCoordRejectsBoundary()
	Assert(ClampWorldCoord#(WorldCoordMax#) = 0.0)
	Assert(ClampWorldCoord#(-WorldCoordMax#) = 0.0)
End Test

; Out-of-range positive/negative magnitudes collapse to 0.
Test testClampWorldCoordRejectsOutOfRange()
	Assert(ClampWorldCoord#(1000000.0) = 0.0)
	Assert(ClampWorldCoord#(-1000000.0) = 0.0)
End Test

; The whole reason ClampWorldCoord exists: NaN propagates through normal
; float math but fails every comparison. Construct a NaN via 0/0.
Test testClampWorldCoordRejectsNaN()
	; Blitz3D doesn't have a NaN literal; 0.0 / 0.0 is the canonical way.
	; Some Blitz interpreters trap div-by-zero in debug builds, but the
	; published BlitzForge runtime returns IEEE NaN.
	; Construct NaN via runtime variables so the compiler can't fold it.
	Local zero# = 0.0
	Local nan# = zero# / zero#
	Assert(ClampWorldCoord#(nan#) = 0.0)
End Test

; ClampSaneFloat# has a larger cap but the same NaN-rejecting shape.
Test testClampSaneFloatPassesInRange()
	Assert(ClampSaneFloat#(0.0) = 0.0)
	Assert(ClampSaneFloat#(1234567.0) = 1234567.0)
	Assert(ClampSaneFloat#(-1234567.0) = -1234567.0)
End Test

Test testClampSaneFloatRejectsOutOfRange()
	; FloatSanityMax = 1e9; anything outside the +/-1e9 window collapses.
	Assert(ClampSaneFloat#(10000000000.0) = 0.0)
	Assert(ClampSaneFloat#(-10000000000.0) = 0.0)
End Test

Test testClampSaneFloatRejectsNaN()
	; Construct NaN via runtime variables so the compiler can't fold it.
	Local zero# = 0.0
	Local nan# = zero# / zero#
	Assert(ClampSaneFloat#(nan#) = 0.0)
End Test

; Same value that ClampWorldCoord clamps (e.g. 200000) passes through
; ClampSaneFloat because the larger cap accommodates it. Pins the
; intended split: ClampWorldCoord for world-space positions,
; ClampSaneFloat for everything else (yaw, anim speed, emitter offsets).
Test testHelperSplitIsPreserved()
	Assert(ClampWorldCoord#(200000.0) = 0.0)
	Assert(ClampSaneFloat#(200000.0) = 200000.0)
End Test
