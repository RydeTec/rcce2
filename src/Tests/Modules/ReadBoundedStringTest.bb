; Tests for ReadBoundedString$ in Modules/Logging.bb.
;
; ReadBoundedString$ is the bounded sibling of Blitz's ReadString$. Every
; admin-editable data loader in the engine routes its filename / name /
; script-path reads through it so that a corrupted .dat file with a wild
; 4-byte length prefix cannot hang the server allocating gigabytes
; (PRs #147 / #148 / #149 / #156 / #160 / #161 / #162 / #163 / #164 /
; #170 / #171). Pin the contract directly so future refactors of the
; helper can't quietly regress the DoS hardening surface.
;
; Note: this file is intentionally NOT Strict. ReadBoundedString$ takes
; its file-handle parameter untyped (defaults to Int), which Strict can't
; auto-convert from the BBStream returned by ReadFile / WriteFile.
; Non-Strict tests sidestep the type gap cleanly.

; LogMode=0 keeps Logging.bb's WriteLog silent (no DebugLog spam in CI).
; MainLog=0 means WriteLog writes nothing to a file handle either, so
; ReadBoundedString$'s "out of range" log calls are no-ops.
Global LogMode = 0
Global MainLog = 0

Include "Modules\Logging.bb"

Global testDir$ = CurrentDir$()
Global testPath$ = testDir$ + "readbounded_test.dat"

Function Cleanup()
	If FileType(testPath$) = 1 Then DeleteFile(testPath$)
End Function

; Helper: write the on-disk encoding ReadBoundedString$ expects --
; WriteInt L + L raw bytes (matches Blitz's WriteString format).
Function SeedLenPrefixed(lengthPrefix, body$)
	Cleanup()
	F = WriteFile(testPath$)
	WriteInt F, lengthPrefix
	For i = 1 To Len(body$)
		WriteByte F, Asc(Mid$(body$, i, 1))
	Next
	CloseFile F
End Function

Function ReadBack$(maxLen)
	F = ReadFile(testPath$)
	If F = 0 Then Return ""
	result$ = ReadBoundedString$(F, maxLen)
	CloseFile F
	Return result$
End Function


; Normal case: round-trip a string within the cap.
Test testReadBoundedStringRoundTripsWithinCap()
	SeedLenPrefixed(Len("hello world"), "hello world")
	Assert(ReadBack$(256) = "hello world")
	Cleanup()
End Test

; Length prefix above MaxLen returns "" and consumes no body bytes.
; (Protects against the multi-gigabyte allocation DoS the helper exists
; for -- a hostile or corrupted data file can't make us walk gigabytes
; of zeros.)
Test testReadBoundedStringRefusesLengthAboveCap()
	; Claim 999999 bytes of payload but only write 5.
	SeedLenPrefixed(999999, "short")
	Assert(ReadBack$(256) = "")
	Cleanup()
End Test

; Negative length prefix (signed-ReadInt of a corrupted byte sequence)
; also returns "".
Test testReadBoundedStringRefusesNegativeLength()
	SeedLenPrefixed(-1, "")
	Assert(ReadBack$(256) = "")
	Cleanup()
End Test

; Zero length returns "" without consuming any body bytes.
Test testReadBoundedStringZeroLengthReturnsEmpty()
	SeedLenPrefixed(0, "")
	Assert(ReadBack$(256) = "")
	Cleanup()
End Test

; EOF reached before the claimed length -- function returns what it
; managed to read rather than blocking or padding past EOF.
Test testReadBoundedStringStopsAtEof()
	; Claim 10 bytes, write only 5.
	SeedLenPrefixed(10, "abcde")
	Assert(ReadBack$(256) = "abcde")
	Cleanup()
End Test

; A null file handle (F=0) returns "" cleanly without crashing. Models
; "ReadFile returned 0 and the caller didn't bail yet".
Test testReadBoundedStringNullHandleReturnsEmpty()
	Assert(ReadBoundedString$(0, 256) = "")
End Test

; Length right at the cap boundary is accepted (inclusive upper bound).
Test testReadBoundedStringAcceptsExactCapBoundary()
	SeedLenPrefixed(5, "exact")
	Assert(ReadBack$(5) = "exact")
	Cleanup()
End Test

; Length one over the cap is rejected (exclusive upper bound is L > MaxLen).
Test testReadBoundedStringRejectsOneOverCap()
	SeedLenPrefixed(6, "sixxxx")
	Assert(ReadBack$(5) = "")
	Cleanup()
End Test
