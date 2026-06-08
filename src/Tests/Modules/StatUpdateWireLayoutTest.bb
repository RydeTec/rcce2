; Tests for the P_StatUpdate (packet const 22) wire byte-layout contract.
;
; P_StatUpdate is BUILT in nine server-side places across four modules and
; READ in exactly one client handler -- the offsets are coupled only by
; convention, nothing pins them. The layout has two shapes:
;
;   "A" (attribute value) / "M" (attribute maximum):
;       subcode(1) + RuntimeID(2) + Attribute(1) + Value(2)   = 6 bytes
;       reader: ClientNet.bb -- RID @ Mid$(,2,2), Att @ Mid$(,4,1), Val @ Mid$(,5,2)
;   "R" (reputation):
;       subcode(1) + RuntimeID(2) + Reputation(2)             = 5 bytes
;       reader: ClientNet.bb -- RID @ Mid$(,2,2), Rep @ Mid$(,4,2)   (NO Attribute byte)
;
; The "R" form is asymmetric: its value sits at offset 4, while "A"/"M" put
; Attribute at 4 and the value at 5. A refactor that (a) widens any field,
; (b) "unifies" "R" by inserting an Attribute byte, (c) reorders
; RuntimeID/Attribute, or (d) drifts one of the nine builders out of step,
; silently shifts every downstream Mid$ slice -> wrong HP/mana/reputation
; displayed, or an out-of-band Attribute that the reader's `0..39` guard
; drops (stat update silently vanishes). No crash, no compile error.
;
; Builders (verified): ServerNet.bb:360/374 (A/M), GameServer.bb:769/804 (A),
; ScriptingCommands.bb:2350/2387/2433/2466 (A/M), Server.bb:1010 (R via
; UpdateReputation). Reader: ClientNet.bb:990-1010.
;
; ClientNet.bb / ServerNet.bb can't be Included offline (RakNet externs), so
; the wire primitive and both layouts are replicated here per the established
; RCEWireEncodingTest.bb / ClampFloatTest.bb convention. The test reconstructs
; each field via the EXACT reader Mid$ offsets, independently of the builder's
; concatenation order, so it is not tautological: a builder/reader offset
; disagreement fails an assert. NOT Strict (bare-int + Bank primitive).

Global StatBank = CreateBank(4)

Function StatStrFromInt$(Num, Length = 4)
	PokeInt StatBank, 0, Num
	Dat$ = ""
	For i = Length - 1 To 0 Step -1
		Dat$ = Chr$(PeekByte(StatBank, i)) + Dat$
	Next
	Return Dat$
End Function

Function StatIntFromStr(Dat$)
	PokeInt StatBank, 0, 0
	For i = 1 To Len(Dat$)
		PokeByte StatBank, i - 1, Asc(Mid$(Dat$, i, 1))
	Next
	Return PeekInt(StatBank, 0)
End Function

; --- Builders: byte-for-byte mirrors of the production senders ---------
Function BuildStatA$(rid, att, val)
	Return "A" + StatStrFromInt$(rid, 2) + StatStrFromInt$(att, 1) + StatStrFromInt$(val, 2)
End Function
Function BuildStatM$(rid, att, mx)
	Return "M" + StatStrFromInt$(rid, 2) + StatStrFromInt$(att, 1) + StatStrFromInt$(mx, 2)
End Function
Function BuildStatR$(rid, rep)
	Return "R" + StatStrFromInt$(rid, 2) + StatStrFromInt$(rep, 2)
End Function


; "A" round-trips through the reader's offsets, and is exactly 6 bytes.
Test testAttributeUpdateRoundTrip()
	Local p$ = BuildStatA$(1234, 7, 500)
	Assert(Len(p$) = 6)
	Assert(Left$(p$, 1) = "A")
	Assert(StatIntFromStr(Mid$(p$, 2, 2)) = 1234)   ; RuntimeID
	Assert(StatIntFromStr(Mid$(p$, 4, 1)) = 7)      ; Attribute
	Assert(StatIntFromStr(Mid$(p$, 5, 2)) = 500)    ; Value
End Test

; "M" uses the identical offsets as "A"; boundary RuntimeID + boundary
; Attribute (39, the highest the reader's `< 40` guard accepts).
Test testMaximumUpdateRoundTrip()
	Local p$ = BuildStatM$(65535, 39, 30000)
	Assert(Len(p$) = 6)
	Assert(Left$(p$, 1) = "M")
	Assert(StatIntFromStr(Mid$(p$, 2, 2)) = 65535)
	Assert(StatIntFromStr(Mid$(p$, 4, 1)) = 39)
	Assert(StatIntFromStr(Mid$(p$, 5, 2)) = 30000)
End Test

; "R" round-trips: reputation lives at offset 4 (width 2), and the packet is
; 5 bytes -- there is NO Attribute byte.
Test testReputationRoundTrip()
	Local p$ = BuildStatR$(42, 12345)
	Assert(Len(p$) = 5)
	Assert(Left$(p$, 1) = "R")
	Assert(StatIntFromStr(Mid$(p$, 2, 2)) = 42)     ; RuntimeID
	Assert(StatIntFromStr(Mid$(p$, 4, 2)) = 12345)  ; Reputation @ offset 4
End Test

; The asymmetry guard: "R" value is at offset 4 and the packet is 5 bytes,
; whereas "A"/"M" value is at offset 5 and 6 bytes. A future "uniformity"
; refactor that inserts an Attribute byte into "R" (making it 6 bytes and
; moving reputation to offset 5) breaks these asserts -- which is the point.
Test testRvsAMOffsetAsymmetryPinned()
	Local r$ = BuildStatR$(1, 1000)
	Local a$ = BuildStatA$(1, 9, 1000)
	Assert(Len(r$) = 5)
	Assert(Len(a$) = 6)
	; same value, different offset per subcode:
	Assert(StatIntFromStr(Mid$(r$, 4, 2)) = 1000)   ; R: value at 4
	Assert(StatIntFromStr(Mid$(a$, 5, 2)) = 1000)   ; A: value at 5
End Test

; RuntimeID is at the same offset (2, width 2) for ALL subcodes -- the reader
; decodes it before branching on subcode.
Test testRuntimeIDOffsetSharedAcrossSubcodes()
	Assert(StatIntFromStr(Mid$(BuildStatA$(777, 0, 0), 2, 2)) = 777)
	Assert(StatIntFromStr(Mid$(BuildStatM$(777, 0, 0), 2, 2)) = 777)
	Assert(StatIntFromStr(Mid$(BuildStatR$(777, 0), 2, 2)) = 777)
End Test

; Boundary values survive the 2-byte fields (0 and 65535).
Test testTwoByteFieldBoundaries()
	Assert(StatIntFromStr(Mid$(BuildStatA$(0, 0, 0), 5, 2)) = 0)
	Assert(StatIntFromStr(Mid$(BuildStatA$(0, 0, 65535), 5, 2)) = 65535)
	Assert(StatIntFromStr(Mid$(BuildStatR$(0, 65535), 4, 2)) = 65535)
End Test
