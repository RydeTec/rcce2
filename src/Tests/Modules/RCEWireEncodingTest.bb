; Tests for the wire-encoding primitives in Modules/RCEnet.bb:
;
;   RCE_StrFromInt$(Num, Length = 4)
;   RCE_IntFromStr(Dat$)
;   RCE_StrFromFloat$(Num#)
;   RCE_FloatFromStr#(Dat$)
;
; These four functions are the foundation of every packet read and
; every packet write in the engine -- hundreds of call sites. A
; regression in any of them silently corrupts wire-level state across
; the entire client/server protocol.
;
; Pre-PR-#327 they had ZERO dedicated test coverage. ItemsTest.bb
; replicates them as a side-helper for its serialization tests, but
; the primitives themselves were untested. A refactor that swapped
; the endianness (the byte-order loop is the only thing protecting
; correct cross-platform wire layout), or changed the Bank size, or
; broke Bank lifecycle, would silently land.
;
; NOT Strict. The production functions use bare-int/float typing and
; Bank globals; replicating them under Strict triggers the same
; in-function-string-concatenation restriction that ClampFloatTest
; and MyEscapeTest sidestep by leaving the file non-Strict. The
; replicated-gate pattern is: any production change to RCEnet.bb's
; wire primitives MUST update this file. The duplication is the
; trigger to refresh the rationale.
;
; RCEnet.bb itself can't be Included into a test build because it
; pulls in RakNet externs that aren't available offline (same
; precedent as ClampFloatTest.bb).

; --- Replicated state --------------------------------------------------
;
; Production uses a single module-scope `Global RCE_ConvertBank =
; CreateBank(4)`. The replication uses the same 4-byte Bank lifetime
; so the byte-layout properties (endianness, low/high-byte ordering)
; match exactly.

Global TestConvertBank = CreateBank(4)

; --- Replicated primitives ---------------------------------------------

Function TestIntFromStr(Dat$)
	PokeInt TestConvertBank, 0, 0
	For i = 1 To Len(Dat$)
		PokeByte TestConvertBank, i - 1, Asc(Mid$(Dat$, i, 1))
	Next
	Return PeekInt(TestConvertBank, 0)
End Function

Function TestStrFromInt$(Num, Length = 4)
	PokeInt TestConvertBank, 0, Num
	Dat$ = ""
	For i = Length - 1 To 0 Step -1
		Dat$ = Chr$(PeekByte(TestConvertBank, i)) + Dat$
	Next
	Return Dat$
End Function

Function TestStrFromFloat$(Num#)
	PokeFloat TestConvertBank, 0, Num#
	Dat$ = ""
	For i = 3 To 0 Step -1
		Dat$ = Chr$(PeekByte(TestConvertBank, i)) + Dat$
	Next
	Return Dat$
End Function

Function TestFloatFromStr#(Dat$)
	PokeFloat TestConvertBank, 0, 0.0
	For i = 1 To 4
		PokeByte TestConvertBank, i - 1, Asc(Mid$(Dat$, i, 1))
	Next
	Return PeekFloat#(TestConvertBank, 0)
End Function

; ====================================================================
; Length-output invariant. Pre-fix any caller's `Mid$(MessageData$,
; offset, N)` after `RCE_StrFromInt$(x, M)` would silently slice the
; wrong bytes if M != N. Pin the output Length matches the input
; Length argument exactly.
; ====================================================================

Test testStrFromIntLengthOneByte()
	Assert(Len(TestStrFromInt$(0,   1)) = 1)
	Assert(Len(TestStrFromInt$(127, 1)) = 1)
	Assert(Len(TestStrFromInt$(255, 1)) = 1)
End Test

Test testStrFromIntLengthTwoBytes()
	Assert(Len(TestStrFromInt$(0,     2)) = 2)
	Assert(Len(TestStrFromInt$(32767, 2)) = 2)
	Assert(Len(TestStrFromInt$(65535, 2)) = 2)
End Test

Test testStrFromIntLengthThreeBytes()
	Assert(Len(TestStrFromInt$(0,        3)) = 3)
	Assert(Len(TestStrFromInt$(16777215, 3)) = 3)
End Test

Test testStrFromIntLengthFourBytes()
	Assert(Len(TestStrFromInt$(0,          4)) = 4)
	Assert(Len(TestStrFromInt$(2147483647, 4)) = 4)
	Assert(Len(TestStrFromInt$(-2147483648, 4)) = 4)
End Test

Test testStrFromIntDefaultLengthIsFour()
	; Default Length is 4 per the production signature
	; `RCE_StrFromInt$(Num, Length = 4)`. Callers that omit the
	; Length arg rely on this.
	Assert(Len(TestStrFromInt$(42)) = 4)
	Assert(Len(TestStrFromInt$(0))  = 4)
End Test

; ====================================================================
; Round-trip property: encoding then decoding must preserve the
; integer value. This is the load-bearing invariant for every packet
; field that uses these primitives.
; ====================================================================

Test testIntRoundTripZero()
	Assert(TestIntFromStr(TestStrFromInt$(0, 4)) = 0)
End Test

Test testIntRoundTripSmallPositive()
	Assert(TestIntFromStr(TestStrFromInt$(1, 4))   = 1)
	Assert(TestIntFromStr(TestStrFromInt$(42, 4))  = 42)
	Assert(TestIntFromStr(TestStrFromInt$(255, 4)) = 255)
End Test

Test testIntRoundTripByteBoundaries()
	; Just below and at each byte boundary -- the per-byte unpack
	; loop must handle carries cleanly.
	Assert(TestIntFromStr(TestStrFromInt$(256, 4))       = 256)
	Assert(TestIntFromStr(TestStrFromInt$(65535, 4))     = 65535)
	Assert(TestIntFromStr(TestStrFromInt$(65536, 4))     = 65536)
	Assert(TestIntFromStr(TestStrFromInt$(16777215, 4))  = 16777215)
	Assert(TestIntFromStr(TestStrFromInt$(16777216, 4))  = 16777216)
End Test

Test testIntRoundTripMaxPositive()
	; 2^31 - 1
	Assert(TestIntFromStr(TestStrFromInt$(2147483647, 4)) = 2147483647)
End Test

Test testIntRoundTripNegativeOne()
	; -1 = 0xFFFFFFFF in two's-complement. Sets every bit; pinning
	; this catches a sign-handling regression in either encode or
	; decode.
	Assert(TestIntFromStr(TestStrFromInt$(-1, 4)) = -1)
End Test

Test testIntRoundTripMinNegative()
	; 2^31 (= -2^31 in two's-complement). High bit set; pinning
	; this catches a high-bit truncation regression.
	Assert(TestIntFromStr(TestStrFromInt$(-2147483648, 4)) = -2147483648)
End Test

Test testIntRoundTripSmallNegative()
	Assert(TestIntFromStr(TestStrFromInt$(-1, 4))    = -1)
	Assert(TestIntFromStr(TestStrFromInt$(-256, 4))  = -256)
	Assert(TestIntFromStr(TestStrFromInt$(-1000, 4)) = -1000)
End Test

; ====================================================================
; Truncated-length round-trip. Writing N bytes then reading them back
; via RCE_IntFromStr (which always treats remaining bytes as 0) must
; preserve the low N bytes of the value. This is the canonical pattern
; for 2-byte (ActorID, RNID) and 1-byte (sub-code, flag) packet fields.
; ====================================================================

Test testTwoByteEncodingPreservesLowShortRange()
	; 0..65535 fits in 2 bytes; high 2 bytes zero on round-trip.
	Assert(TestIntFromStr(TestStrFromInt$(0,     2)) = 0)
	Assert(TestIntFromStr(TestStrFromInt$(1,     2)) = 1)
	Assert(TestIntFromStr(TestStrFromInt$(32767, 2)) = 32767)
	Assert(TestIntFromStr(TestStrFromInt$(65535, 2)) = 65535)
End Test

Test testOneByteEncodingPreservesLowByteRange()
	; 0..255 fits in 1 byte; high 3 bytes zero on round-trip.
	Assert(TestIntFromStr(TestStrFromInt$(0,   1)) = 0)
	Assert(TestIntFromStr(TestStrFromInt$(127, 1)) = 127)
	Assert(TestIntFromStr(TestStrFromInt$(255, 1)) = 255)
End Test

; ====================================================================
; Float round-trip. Every player position / velocity / angle that
; travels over the wire goes through RCE_StrFromFloat$ /
; RCE_FloatFromStr#. Bit-exact preservation is the contract; a
; conversion to a non-IEEE-754 intermediate would corrupt subnormals
; and lose precision on large magnitudes.
; ====================================================================

Test testFloatRoundTripZero()
	Assert(TestFloatFromStr#(TestStrFromFloat$(0.0)) = 0.0)
End Test

Test testFloatRoundTripUnit()
	Assert(TestFloatFromStr#(TestStrFromFloat$(1.0))  = 1.0)
	Assert(TestFloatFromStr#(TestStrFromFloat$(-1.0)) = -1.0)
End Test

Test testFloatRoundTripCommonValues()
	; Values that show up in actor X/Y/Z, animation frame, etc.
	Assert(TestFloatFromStr#(TestStrFromFloat$(0.5))    = 0.5)
	Assert(TestFloatFromStr#(TestStrFromFloat$(3.14159)) = 3.14159)
	Assert(TestFloatFromStr#(TestStrFromFloat$(-3.14159)) = -3.14159)
End Test

Test testFloatRoundTripLargeMagnitude()
	; Pin large-magnitude positions (WorldCoordMax is ~100000.0
	; per ClampFloatTest precedent).
	Assert(TestFloatFromStr#(TestStrFromFloat$(99999.0)) = 99999.0)
	Assert(TestFloatFromStr#(TestStrFromFloat$(-99999.0)) = -99999.0)
End Test

Test testFloatRoundTripSmallMagnitude()
	; Sub-unit values used for animation interpolation fractions.
	Assert(TestFloatFromStr#(TestStrFromFloat$(0.001))    = 0.001)
	Assert(TestFloatFromStr#(TestStrFromFloat$(0.0001))   = 0.0001)
End Test

Test testFloatStrLengthIsFour()
	; Float encoding is fixed-width 4 bytes (IEEE-754 single).
	Assert(Len(TestStrFromFloat$(0.0))     = 4)
	Assert(Len(TestStrFromFloat$(1.0))     = 4)
	Assert(Len(TestStrFromFloat$(-1.0))    = 4)
	Assert(Len(TestStrFromFloat$(99999.0)) = 4)
End Test

; ====================================================================
; Edge case: empty-string decode. RCE_IntFromStr("") on an empty
; input must NOT crash and must return 0 (the Bank starts zeroed via
; the prologue PokeInt). Some packet handlers slice payloads and may
; produce empty strings at the truncation boundary; the function has
; to soft-fail rather than read past the input.
; ====================================================================

Test testIntFromStrEmptyInputReturnsZero()
	Assert(TestIntFromStr("") = 0)
End Test

; ====================================================================
; Order property: byte-order of the produced string.
;
; RCE_StrFromInt$ writes the Bank then loops i = Length-1 down to 0,
; prepending each byte to Dat$:
;   i=3: Dat$ = Chr$(byte3) + ""        = "byte3"
;   i=2: Dat$ = Chr$(byte2) + "byte3"   = "byte2,byte3"
;   i=1: Dat$ = Chr$(byte1) + ...       = "byte1,byte2,byte3"
;   i=0: Dat$ = Chr$(byte0) + ...       = "byte0,byte1,byte2,byte3"
;
; So Bank-index 0 (the low byte of the int, since PokeInt is
; little-endian) ends up at string position 1; Bank-index Length-1
; (the high byte) ends up at position Length. The string scans
; left-to-right as low-to-high, matching the on-disk / on-wire
; little-endian convention. Pin this directly with known bit
; patterns so a swap of the loop direction breaks at least one test.
; ====================================================================

Test testByteOrderIsLittleEndianInString()
	; 0x01020304 = 16909060
	; Low byte = 0x04 (4), high byte = 0x01 (1)
	; Expected output: Chr$(4) + Chr$(3) + Chr$(2) + Chr$(1)
	Local encoded$ = TestStrFromInt$(16909060, 4)
	Assert(Asc(Mid$(encoded$, 1, 1)) = 4)
	Assert(Asc(Mid$(encoded$, 2, 1)) = 3)
	Assert(Asc(Mid$(encoded$, 3, 1)) = 2)
	Assert(Asc(Mid$(encoded$, 4, 1)) = 1)
End Test

Test testByteOrderSingleByteEncoding()
	; With Length=1 and value 0xAB (171), output is just Chr$(171).
	Local encoded$ = TestStrFromInt$(171, 1)
	Assert(Len(encoded$) = 1)
	Assert(Asc(encoded$) = 171)
End Test

Test testByteOrderTwoBytePreservesHighLowOrder()
	; 0x0102 = 258. Low byte = 2, high byte = 1.
	; Expected: Chr$(2) + Chr$(1) (low first in the string).
	Local encoded$ = TestStrFromInt$(258, 2)
	Assert(Len(encoded$) = 2)
	Assert(Asc(Mid$(encoded$, 1, 1)) = 2)
	Assert(Asc(Mid$(encoded$, 2, 1)) = 1)
End Test

; ====================================================================
; RCE_SignedShortFromStr: decode a 2-byte field as SIGNED 16-bit.
; RCE_IntFromStr alone decodes 0..65535 (unsigned) because it zero-fills
; the 4-byte bank and writes only the bytes present. A field carrying a
; signed value in a 2-byte slot (reputation -- stored signed by the save
; path via WriteShort/ReadShort, but historically read UNSIGNED by every
; wire reader) must sign-extend: decoded >= 32768 is negative. Replicated
; here per the same offline-Include constraint; mirrors RCEnet.bb.
; ====================================================================

Function TestSignedShortFromStr(Dat$)
	Local v = TestIntFromStr(Dat$)
	If v >= 32768 Then v = v - 65536
	Return v
End Function

Test testSignedShortNonNegativeUnchanged()
	; 0..32767 decode identically to the unsigned read.
	Assert(TestSignedShortFromStr(TestStrFromInt$(0, 2))     = 0)
	Assert(TestSignedShortFromStr(TestStrFromInt$(1, 2))     = 1)
	Assert(TestSignedShortFromStr(TestStrFromInt$(12345, 2)) = 12345)
	Assert(TestSignedShortFromStr(TestStrFromInt$(32767, 2)) = 32767)
End Test

Test testSignedShortNegativeRoundTrip()
	; A negative value sent through the 2-byte field (its low 2 bytes) is
	; recovered exactly by the sign-extend -- the reputation fix. Plain
	; RCE_IntFromStr would return these as large positives.
	Assert(TestSignedShortFromStr(TestStrFromInt$(-1, 2))     = -1)
	Assert(TestSignedShortFromStr(TestStrFromInt$(-100, 2))   = -100)
	Assert(TestSignedShortFromStr(TestStrFromInt$(-10000, 2)) = -10000)
	Assert(TestSignedShortFromStr(TestStrFromInt$(-32768, 2)) = -32768)
End Test

Test testSignedShortContrastWithUnsigned()
	; Wire bytes for -1 are 0xFFFF. Unsigned read = 65535; signed = -1.
	; Pins the exact behavioural difference the fix makes at the reputation
	; reader sites (ClientNet.bb, Actors.bb, MainMenu.bb).
	Local wire$ = TestStrFromInt$(-1, 2)
	Assert(TestIntFromStr(wire$)         = 65535)
	Assert(TestSignedShortFromStr(wire$) = -1)
End Test
