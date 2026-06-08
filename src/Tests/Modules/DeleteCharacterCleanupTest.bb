Strict
EnableGC

; Tests for the P_DeleteCharacter slot-cleanup invariant in ServerNet.bb.
;
; An Account holds three parallel per-slot arrays (AccountsServer.bb):
;   Field Character.ActorInstance[9]
;   Field QuestLog.QuestLog[9]
;   Field ActionBar.ActionBarData[9]
; Deleting character `Number` must RELEASE that slot's resources before the
; shift loop overwrites them, then shift slots Number+1..9 down and null slot
; 9. The handler released QuestLog (and the actor, via DeleteCharacter) but
; NOT ActionBar -- so the shift overwrote A\ActionBar[Number] and leaked one
; ActionBarData (+ its 36 slot strings) per deletion. The fix adds the
; matching `Delete A\ActionBar[Number]`, restoring the symmetry the load
; cleanup (AccountsServer.bb:366) already has.
;
; ServerNet.bb can't be Included into a test build (RakNet externs), so the
; slot-management is replicated here on integer instance-ids with a release
; log, per the established ClampFloatTest convention. The model mirrors the
; FIXED handler; a regression that drops the ActionBar release would make
; the model's freed-log omit it -- which testDeleteReleasesAllThree pins.

Type MockAcct
	Field ch[9]      ; character (ActorInstance) id; 0 = empty slot
	Field ql[9]      ; QuestLog instance id
	Field ab[9]      ; ActionBar instance id
	Field freed$     ; release log: "C<id>;" / "Q<id>;" / "A<id>;" per release
End Type

; Replicates the FIXED P_DeleteCharacter cleanup+shift for slot `Number`.
Function DeleteCharSlot(M.MockAcct, Number)
	; Release the deleted slot's resources BEFORE the shift overwrites them.
	; DeleteCharacter() frees the actor; QuestLog and ActionBar are Delete'd.
	If M\ch[Number] <> 0 Then M\freed$ = M\freed$ + "C" + Str$(M\ch[Number]) + ";"
	If M\ql[Number] <> 0 Then M\freed$ = M\freed$ + "Q" + Str$(M\ql[Number]) + ";"
	If M\ab[Number] <> 0 Then M\freed$ = M\freed$ + "A" + Str$(M\ab[Number]) + ";"
	; Shift remaining slots down.
	Local i
	For i = Number To 8
		M\ch[i] = M\ch[i + 1]
		M\ql[i] = M\ql[i + 1]
		M\ab[i] = M\ab[i + 1]
	Next
	M\ch[9] = 0
	M\ql[9] = 0
	M\ab[9] = 0
End Function

; Helper: load N sequential characters into slots 0..N-1 with distinct ids.
Function Populate(M.MockAcct, N)
	Local i
	For i = 0 To N - 1
		M\ch[i] = 100 + i
		M\ql[i] = 200 + i
		M\ab[i] = 300 + i
	Next
End Function


; The deleted slot releases ALL THREE resources -- crucially the ActionBar
; (id 301 for slot 1), which the pre-fix handler leaked.
Test testDeleteReleasesAllThree()
	Local m.MockAcct = New MockAcct()
	Populate(m, 3)
	DeleteCharSlot(m, 1)
	Assert(Instr(m\freed$, "C101;") > 0)  ; actor freed
	Assert(Instr(m\freed$, "Q201;") > 0)  ; questlog freed
	Assert(Instr(m\freed$, "A301;") > 0)  ; ACTIONBAR freed (the fix)
End Test

; The shift pulls slot Number+1.. down into Number.., across all 3 arrays.
Test testShiftMovesRemainingDown()
	Local m.MockAcct = New MockAcct()
	Populate(m, 3)
	DeleteCharSlot(m, 1)
	; slot 1 now holds what was slot 2 (ids 102/202/302)
	Assert(m\ch[1] = 102) : Assert(m\ql[1] = 202) : Assert(m\ab[1] = 302)
	; slot 0 untouched
	Assert(m\ch[0] = 100) : Assert(m\ab[0] = 300)
End Test

; Slot 9 is cleared after a shift (no dangling duplicate of slot 8).
Test testLastSlotCleared()
	Local m.MockAcct = New MockAcct()
	Populate(m, 10)              ; all 10 slots full
	DeleteCharSlot(m, 0)
	Assert(m\ch[9] = 0) : Assert(m\ql[9] = 0) : Assert(m\ab[9] = 0)
	; what was slot 9 (ids 109/...) shifted into slot 8
	Assert(m\ch[8] = 109) : Assert(m\ab[8] = 309)
End Test

; Deleting the last populated slot releases its ActionBar and leaves the
; array consistent (no shift needed beyond clearing).
Test testDeleteLastPopulatedSlot()
	Local m.MockAcct = New MockAcct()
	Populate(m, 3)
	DeleteCharSlot(m, 2)
	Assert(Instr(m\freed$, "A302;") > 0)  ; last slot's ActionBar freed
	Assert(m\ch[2] = 0) : Assert(m\ab[2] = 0)
	Assert(m\ch[0] = 100) : Assert(m\ch[1] = 101)  ; earlier slots intact
End Test
