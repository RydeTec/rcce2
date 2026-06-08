Strict
; EnableGC intentionally omitted -- this test consumes Const values
; from Language.bb's non-Strict file; no Type/Handle round-trips
; happen here, so Strict alone is sufficient.

; Regression test pinning the LS_SC* slash-command range used by
; Language.bb:301's auto-uppercase rule and by ServerNet.bb's chat
; dispatch.
;
; Pre-fix bug (closed by this PR's sibling commit):
; - Language.bb:301 used `LS_SKick`/`LS_SSeason` (missing the "C"),
;   both undeclared identifiers reading as 0 in the non-Strict file.
;   The Upper-case rule collapsed to `If ID >= 0 And ID <= 0` --
;   slash-command names from a customized Language.txt locale stayed
;   in whatever case the file shipped with, breaking the
;   case-insensitive `/<command>` dispatch.
; - ServerNet.bb:419 used `LS_SCGM` instead of `LS_SCGMSay`. The
;   typo read as 0 and the `Case LanguageString$(0)` branch matched
;   `LS_ConnectingToServer` ("Connecting to server...") instead of
;   the slash-command "GM". The /gm DM-broadcast was unreachable.
;
; This file pins:
; 1. The constant *values* (LS_SCKick = 190, LS_SCSeason = 219,
;    LS_SCGMSay = 205) so a future renumber of LS_* in Language.bb
;    can't silently shift the range out from under the dispatch.
; 2. The range invariant (LS_SCGMSay falls inside the
;    LS_SCKick..LS_SCSeason auto-uppercase range, so its loaded value
;    is uppercased without needing a separate gate).
; 3. The replicated-gate predicate for the LoadLanguage upper-case
;    rule. A regression test that re-typos the gate identifiers would
;    surface as a Strict-mode "undeclared identifier" compile error
;    here (this file is Strict; the Language.bb file is not, which
;    is why the original bug went silent).

Include "Modules\Language.bb"

; Replicates the post-fix range guard from Language.bb:301. Any
; future regression to a typo'd identifier would not compile here
; because this file is Strict.
Function ShouldUppercase%(ID)
	If ID >= LS_SCKick And ID <= LS_SCSeason Then Return True
	Return False
End Function

; ====================================================================
; Constant-value pins. A renumber would shift these and break the
; test; if intentional, update both the test and Language.bb defaults
; block in lockstep.
; ====================================================================

Test testRangeBoundsAreStable()
	Assert(LS_SCKick = 190)
	Assert(LS_SCSeason = 219)
	Assert(LS_SCGMSay = 205)
End Test

Test testLSSCGMSayIsInsideTheAutoUppercaseRange()
	; The /gm dispatch in ServerNet.bb depends on LS_SCGMSay's loaded
	; value being uppercased ("GM"). That requires LS_SCGMSay to fall
	; inside the LoadLanguage auto-uppercase range.
	Assert(LS_SCGMSay >= LS_SCKick)
	Assert(LS_SCGMSay <= LS_SCSeason)
End Test

; ====================================================================
; Range-guard semantics. These are tautologies given the constant
; pins above, but they catch a future change that swaps the operator
; (`>` instead of `>=`, etc.) or accidentally restricts the range.
; ====================================================================

Test testRangeIncludesFirstSlashCommand()
	Assert(ShouldUppercase%(LS_SCKick) = True)
End Test

Test testRangeIncludesLastSlashCommand()
	Assert(ShouldUppercase%(LS_SCSeason) = True)
End Test

Test testRangeIncludesGMSay()
	Assert(ShouldUppercase%(LS_SCGMSay) = True)
End Test

Test testRangeExcludesBelowFirst()
	Assert(ShouldUppercase%(LS_SCKick - 1) = False)
End Test

Test testRangeExcludesAboveLast()
	Assert(ShouldUppercase%(LS_SCSeason + 1) = False)
End Test

Test testRangeExcludesArbitraryNonSlashIDs()
	; Sample a handful of IDs outside the slash-command range.
	; LS_ConnectingToServer = 0 (the historical false-positive for
	; the LS_SCGM typo), LS_Username = 11, LS_NewCharacter = 188,
	; LS_QuitToContinue = 220.
	Assert(ShouldUppercase%(0) = False)
	Assert(ShouldUppercase%(11) = False)
	Assert(ShouldUppercase%(188) = False)
	Assert(ShouldUppercase%(220) = False)
End Test
