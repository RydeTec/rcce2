Strict
EnableGC

; Tests for the P_CreateCharacter REJECT-path slot-cleanup invariant in
; ServerNet.bb (companion to DeleteCharacterCleanupTest.bb).
;
; An Account holds three parallel per-slot arrays (AccountsServer.bb):
;   Field Character.ActorInstance[9]
;   Field QuestLog.QuestLog[9]
;   Field ActionBar.ActionBarData[9]
; P_CreateCharacter picks FreeSlot (first slot with Character = Null) and
; allocates all three in lockstep before validating the request. Two reject
; paths run AFTER that allocation -- StartArea-not-found and the attribute-cheat
; check. They used to call only FreeActorInstance(A\Character[FreeSlot]), which:
;   1. never frees the QuestLog/ActionBar New'd in lockstep -> leak (mirror #447);
;   2. does NOT null A\Character[FreeSlot] (FreeActorInstance clears global
;      indexes + Delete, but never the Account's own slot) -> the slot is left
;      DANGLING. The FreeSlot/TotalChars scan then treats it as occupied
;      (phantom slot, lost until relog), and the P_GetCharacters list-send
;      derefs the freed instance -> use-after-free.
; The fix frees QuestLog+ActionBar and nulls all three slots, leaving the slot
; empty and reusable -- the state it had before the rejected creation.
;
; ServerNet.bb can't be Included into a test build (RakNet externs), so the
; slot model is replicated here on integer instance-ids with a release log,
; per the established DeleteCharacterCleanupTest / ClampFloatTest convention.

Type MockAcct
	Field ch[9]      ; character (ActorInstance) id; 0 = empty slot
	Field ql[9]      ; QuestLog instance id
	Field ab[9]      ; ActionBar instance id
	Field freed$     ; release log: "C<id>;" / "Q<id>;" / "A<id>;" per release
End Type

; Replicates the lockstep allocation into FreeSlot (ids derived from the slot).
Function AllocSlot(M.MockAcct, slot)
	M\ql[slot] = 200 + slot
	M\ab[slot] = 300 + slot
	M\ch[slot] = 100 + slot
End Function

; Replicates the FIXED reject cleanup: release whatever is present, then null
; all three slots so the slot is empty and reusable.
Function RejectSlot(M.MockAcct, slot)
	If M\ch[slot] <> 0 Then M\freed$ = M\freed$ + "C" + Str$(M\ch[slot]) + ";"
	If M\ql[slot] <> 0 Then M\freed$ = M\freed$ + "Q" + Str$(M\ql[slot]) + ";"
	If M\ab[slot] <> 0 Then M\freed$ = M\freed$ + "A" + Str$(M\ab[slot]) + ";"
	M\ch[slot] = 0
	M\ql[slot] = 0
	M\ab[slot] = 0
End Function

; Replicates the FreeSlot scan (ServerNet.bb:2795-2801): first slot with
; Character = 0, or -1 if full.
Function FindFreeSlot(M.MockAcct)
	Local i
	For i = 0 To 9
		If M\ch[i] = 0 Then Return i
	Next
	Return -1
End Function

; Replicates the TotalChars count (non-empty Character slots).
Function CountChars(M.MockAcct)
	Local i, total
	total = 0
	For i = 0 To 9
		If M\ch[i] <> 0 Then total = total + 1
	Next
	Return total
End Function

; Pre-seed N occupied slots (0..N-1) so FreeSlot lands on slot N.
Function Populate(M.MockAcct, N)
	Local i
	For i = 0 To N - 1
		M\ch[i] = 100 + i
		M\ql[i] = 200 + i
		M\ab[i] = 300 + i
	Next
End Function


; The reject releases ALL THREE -- crucially QuestLog + ActionBar, which the
; pre-fix handler leaked (it freed only the Character actor).
Test testRejectReleasesAllThree()
	Local m.MockAcct = New MockAcct()
	Populate(m, 2)              ; slots 0,1 occupied -> FreeSlot = 2
	AllocSlot(m, 2)             ; lockstep alloc into slot 2 (ids 102/202/302)
	RejectSlot(m, 2)
	Assert(Instr(m\freed$, "C102;") > 0)  ; actor freed
	Assert(Instr(m\freed$, "Q202;") > 0)  ; questlog freed (leak fix)
	Assert(Instr(m\freed$, "A302;") > 0)  ; actionbar freed (leak fix)
End Test

; The reject NULLS all three slots -- without this, Character[FreeSlot] dangles
; (the use-after-free / phantom-slot bug).
Test testRejectClearsAllSlots()
	Local m.MockAcct = New MockAcct()
	Populate(m, 2)
	AllocSlot(m, 2)
	RejectSlot(m, 2)
	Assert(m\ch[2] = 0)  ; Character nulled -- not left dangling
	Assert(m\ql[2] = 0)
	Assert(m\ab[2] = 0)
End Test

; After a reject the slot is reusable: the FreeSlot scan returns it again and
; the character count excludes it (no phantom occupancy that would push the
; account toward MaxAccountChars or be deref'd by the list-send).
Test testRejectedSlotIsReusable()
	Local m.MockAcct = New MockAcct()
	Populate(m, 2)             ; 2 real characters, slots 0,1
	AllocSlot(m, 2)            ; attempt creation in slot 2
	Assert(FindFreeSlot(m) = 3)   ; while allocated, next free is 3
	Assert(CountChars(m) = 3)     ; allocated slot counts
	RejectSlot(m, 2)
	Assert(FindFreeSlot(m) = 2)   ; slot 2 free again -- reusable, not phantom
	Assert(CountChars(m) = 2)     ; back to 2 real characters
End Test

; Rejecting a slot leaves OTHER slots untouched (no collateral clear).
Test testRejectLeavesOtherSlotsIntact()
	Local m.MockAcct = New MockAcct()
	Populate(m, 3)             ; slots 0,1,2 occupied
	AllocSlot(m, 3)            ; allocate slot 3
	RejectSlot(m, 3)
	Assert(m\ch[0] = 100) : Assert(m\ql[0] = 200) : Assert(m\ab[0] = 300)
	Assert(m\ch[2] = 102) : Assert(m\ab[2] = 302)
	Assert(m\ch[3] = 0)        ; only the rejected slot cleared
End Test
