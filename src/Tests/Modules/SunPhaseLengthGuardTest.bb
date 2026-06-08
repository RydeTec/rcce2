Strict
EnableGC

; Tests for the sun phase-update guard in Environment3D.bb (~:469).
;
; The phase-index calc at Environment3D.bb:471 is:
;     S\CurrentPhase = Int((Day Mod (8 * S\Phase_Length)) / S\Phase_Length - 0.05)
; an INTEGER `Mod (8 * Phase_Length)` and INTEGER `/ Phase_Length`. Phase_Length
; is read UNCLAMPED as a byte from the area .dat (Environment.bb:253),
; independently of ShowPhases. A sun with ShowPhases=1 and Phase_Length=0
; (hand-edited / placeholder content) would hit `Mod 0` and `/0`, which
; BlitzForge surfaces as a "Stack overflow!" crash on the sun-render tick
; (see feedback_blitz_intdiv_zero_stackoverflow). The fix adds `Phase_Length
; > 0` to the guard so the calc is never reached with a zero length -- the
; same class the adjacent PathLength block already guards.
;
; Environment3D.bb can't be Included into a test build (Graphics3D / world
; surface), so the guard predicate and the (positive-length) calc are
; replicated here per the established convention. The guard is what this
; iteration changed; the calc is replicated only to confirm valid data
; computes without a zero divisor. We never call the calc with Phase_Length
; = 0 -- that is precisely the crash the guard prevents (and would crash the
; test runner if invoked).

Const PHASES = 8

; Replicates the FIXED guard at Environment3D.bb:469: the phase-index calc
; runs only when ShowPhases AND Phase_Length > 0 AND the time matches.
Function ShouldUpdatePhase(ShowPhases, PhaseLength, TimeMatches)
	If ShowPhases = True And PhaseLength > 0 And TimeMatches = True Then Return True
	Return False
End Function

; Replicates Environment3D.bb:471 -- only valid (>0) lengths are ever passed,
; mirroring the guard. Integer Mod + integer divide, as in production.
Function PhaseIndex(Day, PhaseLength)
	Return Int((Day Mod (PHASES * PhaseLength)) / PhaseLength - 0.05)
End Function


; THE fix: a zero Phase_Length never triggers the (crash-prone) calc, no
; matter the other conditions.
Test testGuardBlocksZeroPhaseLength()
	Assert(ShouldUpdatePhase(True, 0, True)  = False)
	Assert(ShouldUpdatePhase(True, 0, False) = False)
	Assert(ShouldUpdatePhase(False, 0, True) = False)
End Test

; A valid phased sun at its start time still updates.
Test testGuardAllowsValidPhasedSun()
	Assert(ShouldUpdatePhase(True, 1, True) = True)
	Assert(ShouldUpdatePhase(True, 5, True) = True)
End Test

; All three conditions are required (no spurious update).
Test testGuardRequiresAllConditions()
	Assert(ShouldUpdatePhase(False, 5, True) = False)  ; phases disabled
	Assert(ShouldUpdatePhase(True, 5, False) = False)  ; wrong time
End Test

; For any positive Phase_Length the calc has a non-zero divisor (8*PL and PL)
; and yields an in-range phase index -- i.e. valid data computes safely. We
; deliberately never pass 0 here.
Test testPhaseIndexSafeForPositiveLength()
	Assert(PHASES * 1 > 0)
	Assert(PHASES * 5 > 0)
	; Index stays within 0..PHASES-1 across a spread of days/lengths.
	Local idx
	idx = PhaseIndex(0, 1)   : Assert(idx >= 0 And idx < PHASES)
	idx = PhaseIndex(100, 3) : Assert(idx >= 0 And idx < PHASES)
	idx = PhaseIndex(55, 8)  : Assert(idx >= 0 And idx < PHASES)
	idx = PhaseIndex(7, 1)   : Assert(idx >= 0 And idx < PHASES)
End Test
