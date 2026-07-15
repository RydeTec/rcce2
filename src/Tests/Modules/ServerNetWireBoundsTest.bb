Strict
EnableGC

; Regression test pinning the ServerNet.bb wire-handler hardening in:
;
;   1. RuntimeActorFromWire        (Actors.bb -- bounded RuntimeIDList lookup
;      used by P_SpellUpdate "F", P_ItemScript, P_RightClick, P_Examine,
;      P_Trade, P_AttackActor, and P_InventoryUpdate "S"/"A").
;   2. P_InventoryUpdate "S"/"A"   (ServerNet.bb ~1741 -- payload length guard
;      before the 7-byte swap/add Mid$ reads).
;
; Threat model:
;   - RuntimeIDList is Dim(65535) (Actors.bb:93). Wire RuntimeIDs are read
;     with RCE_IntFromStr, which zero-fills a 4-byte bank and writes only the
;     bytes present, so a 2-byte field always decodes 0..65535 (RCEnet.bb:85)
;     -- i.e. already within range. The bounds guard in RuntimeActorFromWire
;     is therefore the documented bounds-before-index convention (CLAUDE.md)
;     applied at the wire boundary: it is defense in depth against a future
;     read-width change (or a non-2-byte caller) that would otherwise index
;     the Dim out of bounds and crash the shared server process. The active
;     protection for an in-range-but-empty slot is the caller's Null-check.
;     This test pins the full `< 0 Or > 65535 Or slot = Null` contract.
;   - P_InventoryUpdate "S"/"A" had no length guard before its four Mid$
;     reads. The payload is opcode(1)+RuntimeID(2)+SlotA(1)+SlotB(1)+
;     Amount(2) = 7 bytes; a short packet silently decoded RuntimeID/slots
;     to 0. The fix gates the action on the full payload length.
;
; ServerNet.bb / Actors.bb pull the entire network/world graph and can't be
; Included into a Strict offline test build, so the guard predicates are
; replicated here following the established RCEnet/ClampFloat/
; WireParameterHardening convention. A production behaviour change MUST
; update both copies; the duplication is the trigger to refresh the test.

; --- Replicated RuntimeActorFromWire guard --------------------------------

Const RuntimeIDMax% = 65535

; Mirrors RuntimeActorFromWire's range guard: True iff the ID is a valid
; index into RuntimeIDList's Dim(65535). An out-of-range ID makes the
; production helper return Null BEFORE indexing the array.
Function RuntimeIDInRange%(RuntimeID%)
	If RuntimeID < 0 Or RuntimeID > RuntimeIDMax Then Return False
	Return True
End Function

; Mirrors RuntimeActorFromWire + the caller's Null-check together: the
; resolved actor is usable iff the ID is in range AND the slot is occupied.
; This is the full `< 0 Or > 65535 Or slot = Null` contract the handlers
; rely on.
Function WireActorUsable%(RuntimeID%, SlotOccupied%)
	If RuntimeIDInRange%(RuntimeID) = False Then Return False
	If SlotOccupied = False Then Return False
	Return True
End Function

; --- Replicated "S"/"A" payload length guard ------------------------------

Const SwapAddPayloadBytes% = 7

Function SwapAddPacketLenOk%(PayloadLen%)
	If PayloadLen < SwapAddPayloadBytes Then Return False
	Return True
End Function

; --- Replicated nested AreaInstance\Area guard ---------------------------

; Mirrors the P_AttackActor / BVM_ACTORINTRIGGER / BVM_ACTOROUTDOORS gates.
; A live AreaInstance may briefly lose its backing Area during teardown, so
; both references must exist before the production code reads PvP, triggers,
; or Outdoors from the nested Area. The production implementation must use
; nested If branches: BlitzForge And is non-short-circuit.
Function NestedAreaUsable%(InstancePresent%, AreaPresent%)
	If InstancePresent = False Then Return False
	If AreaPresent = False Then Return False
	Return True
End Function

; --- GM /weather stale AreaInstance guard ---------------------------------

; Mirrors the `/weather` command's outer area-instance lookup. A GM can issue
; the command while their actor is moving through teardown, so the command
; must do nothing when Object.AreaInstance(AI\ServerArea) is Null.
Function WeatherAreaUsable%(InstancePresent%)
	If InstancePresent = False Then Return False
	Return True
End Function

; The network/world graph cannot be Included into this standalone harness, so
; also pin the source-level safety contract. This catches a future regression
; that removes the production nested guard while leaving this model unchanged.
Function FunctionBodyContains%(Path$, FunctionMarker$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local InFunction%
	Local Line$
	; test.sh runs each test from src\Tests, whereas an IDE may run it from
	; src. Support both working directories without touching production paths.
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False
	InFunction = False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, FunctionMarker$) > 0 Then InFunction = True
		If InFunction = True And Instr(Line$, Needle$) > 0
			CloseFile F
			Return True
		EndIf
		If InFunction = True And Instr(Line$, "End Function") > 0 Then Exit
	Wend
	CloseFile F
	Return False
End Function

; Checks a bounded dispatch section rather than the rest of the containing
; packet-handler function. This keeps a guard in a later Case from masking a
; missing guard in the Case under test.
Function SectionContains%(Path$, StartMarker$, EndMarker$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local InSection%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False
	InSection = False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, StartMarker$) > 0 Then InSection = True
		If InSection = True And Instr(Line$, EndMarker$) > 0 Then Exit
		If InSection = True And Instr(Line$, Needle$) > 0
			CloseFile F
			Return True
		EndIf
	Wend
	CloseFile F
	Return False
End Function

; ====================================================================
; RuntimeActorFromWire -- rejection: out of range
; ====================================================================

Test testRuntimeIDAboveMaxRejected()
	; 70000 > 65535: a future 4-byte read could produce this. The guard
	; rejects it (returns Null before indexing the Dim).
	Assert(RuntimeIDInRange%(70000) = False)
	Assert(WireActorUsable%(70000, True) = False)
End Test

Test testRuntimeIDNegativeRejected()
	; A 4-byte signed decode could be negative; the guard rejects it.
	Assert(RuntimeIDInRange%(-1) = False)
	Assert(RuntimeIDInRange%(-32768) = False)
	Assert(WireActorUsable%(-1, True) = False)
End Test

Test testRuntimeIDJustAboveMaxRejected()
	; Boundary: 65536 is the first invalid index of a Dim(65535).
	Assert(RuntimeIDInRange%(RuntimeIDMax + 1) = False)
End Test

; ====================================================================
; RuntimeActorFromWire -- rejection: in range but empty slot
; ====================================================================

Test testRuntimeIDEmptySlotRejected()
	; In range, slot empty -> Null. This is the case the callers'
	; existing `<> Null` check protects against.
	Assert(WireActorUsable%(500, False) = False)
	Assert(WireActorUsable%(0, False) = False)
	Assert(WireActorUsable%(RuntimeIDMax, False) = False)
End Test

; ====================================================================
; RuntimeActorFromWire -- acceptance: in range AND occupied slot
; ====================================================================

Test testRuntimeIDOccupiedSlotAccepted()
	Assert(WireActorUsable%(42, True) = True)
End Test

Test testRuntimeIDBoundarySlotsAccepted()
	; The inclusive endpoints 0 and 65535 are both valid Dim indices.
	Assert(RuntimeIDInRange%(0) = True)
	Assert(RuntimeIDInRange%(RuntimeIDMax) = True)
	Assert(WireActorUsable%(0, True) = True)
	Assert(WireActorUsable%(RuntimeIDMax, True) = True)
End Test

; ====================================================================
; P_InventoryUpdate "S"/"A" length guard
; ====================================================================

Test testSwapAddFullPayloadAccepted()
	; Exactly 7 bytes -- the minimum complete payload.
	Assert(SwapAddPacketLenOk%(7) = True)
End Test

Test testSwapAddOversizedPayloadAccepted()
	; Longer than 7 is still fine; the handler reads fixed offsets.
	Assert(SwapAddPacketLenOk%(8) = True)
	Assert(SwapAddPacketLenOk%(64) = True)
End Test

Test testSwapAddTruncatedPayloadsRejected()
	; Every length short of the full 7-byte payload must be dropped --
	; pre-fix these decoded RuntimeID/slots to 0 via empty-slice Mid$.
	Assert(SwapAddPacketLenOk%(0) = False)
	Assert(SwapAddPacketLenOk%(1) = False)   ; opcode only
	Assert(SwapAddPacketLenOk%(2) = False)
	Assert(SwapAddPacketLenOk%(3) = False)   ; opcode + full RuntimeID, no slots
	Assert(SwapAddPacketLenOk%(6) = False)   ; one byte short of Amount
End Test

Test testSwapAddBoundaryAtSeven()
	; Pin the boundary: the production check is `>= 7`, so 6 rejects and
	; 7 accepts. A future refactor that widens a field must update both.
	Assert(SwapAddPacketLenOk%(6) = False)
	Assert(SwapAddPacketLenOk%(7) = True)
End Test

; ====================================================================
; Nested AreaInstance\Area -- stale-area rejection
; ====================================================================

Test testMissingAreaRejectsPacketAndBVMPaths()
	; Outer lookup succeeds but the backing Area has been torn down. The
	; production paths must return their existing no-op/default instead of
	; dereferencing AInstance\Area.
	Assert(NestedAreaUsable%(True, False) = False)
End Test

Test testLiveNestedAreaRemainsUsable()
	Assert(NestedAreaUsable%(True, True) = True)
	Assert(NestedAreaUsable%(False, True) = False)
End Test

Test testProductionGuardsUseSafeNestedIfBranches()
	Assert(FunctionBodyContains%("Modules\ServerNet.bb", "Case P_AttackActor", "If AInstance\Area <> Null") = True)
	Assert(FunctionBodyContains%("Modules\ServerNet.bb", "Case P_AttackActor", "AInstance <> Null And AInstance\Area <> Null") = False)
	Assert(FunctionBodyContains%("Modules\ScriptingCommands.bb", "Function BVM_ACTORINTRIGGER", "If AInstance\Area <> Null") = True)
	Assert(FunctionBodyContains%("Modules\ScriptingCommands.bb", "Function BVM_ACTORINTRIGGER", "AInstance <> Null And AInstance\Area <> Null") = False)
	Assert(FunctionBodyContains%("Modules\ScriptingCommands.bb", "Function BVM_ACTOROUTDOORS", "If AInstance\Area <> Null") = True)
	Assert(FunctionBodyContains%("Modules\ScriptingCommands.bb", "Function BVM_ACTOROUTDOORS", "AInstance <> Null And AInstance\Area <> Null") = False)
End Test

; ====================================================================
; GM /weather -- stale AreaInstance rejection
; ====================================================================

Test testMissingWeatherAreaRejectsCommand()
	Assert(WeatherAreaUsable%(False) = False)
End Test

Test testLiveWeatherAreaRemainsUsable()
	Assert(WeatherAreaUsable%(True) = True)
End Test

Test testWeatherCommandUsesExplicitAreaInstanceGuard()
	Assert(SectionContains%("Modules\ServerNet.bb", "Case LanguageString$(LS_SCWeather)", "Case LanguageString$(LS_SCTime)", "If AInstance <> Null") = True)
	Assert(SectionContains%("Modules\ServerNet.bb", "Case LanguageString$(LS_SCWeather)", "Case LanguageString$(LS_SCTime)", "AInstance <> Null And") = False)
End Test
