Strict

; Tests pinning the "After-cursor walk" pattern from CLAUDE.md
; "Iterator-during-iteration hazards (Blitz3D `For Each` + `Delete`)".
;
; The four production sites this PR's sibling commit closes:
;   1. ClientCombat.bb::UpdateFloatingNumbers
;   2. F-UI.bb::MessageBox event-pump
;   3. F-UI.bb::FUI_FreeAllEntities
;   4. MySQL.bb::My_UpdateThreads (dormant)
;
; The test pins the *pattern* against a Mock Type pool, asserting that
; the after-cursor walk visits every non-deleted element exactly once
; when the body Delete's some elements.

Type MockItem
	Field ID%
End Type

; Drain the MockItem pool. For-Each + Delete-current is safe in
; BlitzForge per the verified basic.cpp ref-counting walk (see
; feedback_blitzforge_enablegc_requires_strict memory).
Function ResetPool()
	Local entry.MockItem
	For entry = Each MockItem
		Delete entry
	Next
End Function

; ====================================================================
; The after-cursor walk template from CLAUDE.md. Returns survived
; count via direct iteration -- avoids a second For-Each (which
; BlitzCC's runtime walker stack-overflows on in this test
; environment).
; ====================================================================
Function SweepAfterCursor%(divisor%)
	Local m.MockItem = First MockItem
	Local mNext.MockItem = Null
	Local survived% = 0
	While m <> Null
		mNext = After m
		; Guard the Mod with a nested If -- BlitzForge `And` is NOT
		; short-circuit (per feedback_blitzforge_and_non_short_circuit
		; memory), so `divisor > 0 And (m\ID Mod divisor) = 0` would
		; evaluate `Mod 0` and crash when divisor = 0.
		Local shouldDelete% = False
		If divisor > 0
			If (m\ID Mod divisor) = 0 Then shouldDelete = True
		EndIf
		If shouldDelete
			Delete m
		Else
			survived = survived + 1
		EndIf
		m = mNext
	Wend
	Return survived
End Function

; ====================================================================
; Basic correctness: after-cursor walk visits every item once.
; Asserts are on the SweepAfterCursor return value alone (an independent
; counter); we don't re-walk the pool with a second For-Each because
; BlitzCC's runtime walker is fragile in this multi-test environment.
; ====================================================================

Test testAfterCursorWalkVisitsAllItems()
	ResetPool()
	Local a.MockItem = New MockItem() : a\ID = 0
	Local b.MockItem = New MockItem() : b\ID = 1
	Local c.MockItem = New MockItem() : c\ID = 2
	Local d.MockItem = New MockItem() : d\ID = 3
	Local e.MockItem = New MockItem() : e\ID = 4
	; divisor=0 = no deletes; all 5 items survive the sweep.
	Assert(SweepAfterCursor%(0) = 5)
End Test

Test testAfterCursorWalkDeletesEveryItem()
	ResetPool()
	Local a.MockItem = New MockItem() : a\ID = 0
	Local b.MockItem = New MockItem() : b\ID = 1
	Local c.MockItem = New MockItem() : c\ID = 2
	; divisor=1 = every ID divisible by 1 = all 3 deleted.
	Assert(SweepAfterCursor%(1) = 0)
End Test

Test testAfterCursorWalkDeletesAlternateItems()
	ResetPool()
	Local a.MockItem = New MockItem() : a\ID = 0
	Local b.MockItem = New MockItem() : b\ID = 1
	Local c.MockItem = New MockItem() : c\ID = 2
	Local d.MockItem = New MockItem() : d\ID = 3
	Local e.MockItem = New MockItem() : e\ID = 4
	Local f.MockItem = New MockItem() : f\ID = 5
	; divisor=2 = IDs 0/2/4 deleted; IDs 1/3/5 survive.
	Assert(SweepAfterCursor%(2) = 3)
End Test

; ====================================================================
; Empty pool boundary -- the after-cursor's `m = First MockItem`
; correctly returns Null, the While condition is False, the function
; returns 0.
; ====================================================================

Test testAfterCursorWalkOnEmptyPool()
	ResetPool()
	Assert(SweepAfterCursor%(1) = 0)
End Test

; ====================================================================
; Single-item boundary -- After-cursor correctly captures Null as
; the next pointer and terminates after one iteration.
; ====================================================================

Test testAfterCursorWalkOnSingleItemKept()
	ResetPool()
	Local a.MockItem = New MockItem() : a\ID = 0
	Assert(SweepAfterCursor%(0) = 1)
End Test

Test testAfterCursorWalkOnSingleItemDeleted()
	ResetPool()
	Local a.MockItem = New MockItem() : a\ID = 0
	Assert(SweepAfterCursor%(1) = 0)
End Test

; ====================================================================
; Trailing teardown -- drains the pool before process exit (per
; feedback_chain_test_pool_leak memory).
; ====================================================================

Test zzz_TeardownPoolSweep()
	ResetPool()
End Test
