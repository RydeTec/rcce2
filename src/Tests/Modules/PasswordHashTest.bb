; Tests for Modules/PasswordHash.bb.
;
; Includes RFC 6234 / FIPS 180-4 known-answer SHA-256 vectors plus
; round-trip tests for the salted v1 format and legacy-MD5 acceptance.

Include "Modules\PasswordHash.bb"

Test sha256_empty()
	Assert(SHA256Hex$("") = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
End Test

Test sha256_abc()
	Assert(SHA256Hex$("abc") = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
End Test

Test sha256_quickbrownfox()
	; SHA256("The quick brown fox jumps over the lazy dog")
	Assert(SHA256Hex$("The quick brown fox jumps over the lazy dog") = "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592")
End Test

Test sha256_56byte_boundary()
	; 56-byte input forces a padded length of 128 (two 64-byte blocks)
	; because the trailing 0x80 + 8-byte length need 9 bytes that won't
	; fit in the remaining 8 of the first block.
	Local Msg$ = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"
	Assert(SHA256Hex$(Msg) = "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1")
End Test

Test sha256_one_block_long()
	; A 100-character input ("a" * 100) -- exercises padding into a
	; second block where the first is fully consumed by message bytes.
	Local Msg$ = ""
	Local i%
	For i = 1 To 100
		Msg = Msg + "a"
	Next
	Assert(SHA256Hex$(Msg) = "2816597888e4a0d3a36b82b83316ab32680eb8f00f8cd3b904d681246d285a0e")
End Test

Test hashpassword_roundtrip()
	; HashPassword always produces a v1 record that VerifyPassword
	; accepts for the same MD5, and rejects for a different one.
	Local MD5$ = "5d41402abc4b2a76b9719d911017c592"   ; md5("hello")
	Local Stored$ = HashPassword$(MD5)
	Assert(Left$(Stored, 3) = "$1$")
	Assert(Len(Stored) = 3 + 16 + 1 + 64)
	Assert(VerifyPassword%(Stored, MD5) = True)
	Assert(VerifyPassword%(Stored, "00000000000000000000000000000000") = False)
End Test

Test hashpassword_unique_salt()
	; Two consecutive hashes of the same MD5 should produce different
	; records because each picks a fresh random salt. Statistically the
	; chance of two 16-char salts colliding is effectively zero.
	Local MD5$ = "5d41402abc4b2a76b9719d911017c592"
	SeedRnd MilliSecs()
	Local A$ = HashPassword$(MD5)
	Local B$ = HashPassword$(MD5)
	Assert(A <> B)
End Test

Test verify_accepts_legacy_md5()
	; Existing accounts on disk still have raw 32-char MD5 in A\Pass$.
	; Verify must compare those as plaintext until migration runs.
	Local MD5$ = "5d41402abc4b2a76b9719d911017c592"
	Assert(VerifyPassword%(MD5, MD5) = True)
	Assert(VerifyPassword%(MD5, "5d41402abc4b2a76b9719d911017c593") = False)
End Test

Test verify_rejects_empty()
	Assert(VerifyPassword%("", "5d41402abc4b2a76b9719d911017c592") = False)
	Assert(VerifyPassword%("5d41402abc4b2a76b9719d911017c592", "") = False)
End Test

Test verify_rejects_malformed_v1()
	; Truncated v1 record must not crash and must not accept.
	Assert(VerifyPassword%("$1$short", "5d41402abc4b2a76b9719d911017c592") = False)
	; Wrong separator after salt.
	Assert(VerifyPassword%("$1$abcdefghijklmnopXffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", "5d41402abc4b2a76b9719d911017c592") = False)
End Test

Test test_passwordislegacy()
	Assert(PasswordIsLegacy%("5d41402abc4b2a76b9719d911017c592") = True)
	Assert(PasswordIsLegacy%(HashPassword$("5d41402abc4b2a76b9719d911017c592")) = False)
	Assert(PasswordIsLegacy%("") = False)
End Test

; --- ConstantTimeStrEq -------------------------------------------------
;
; The byte-level invariant we can actually pin in a Strict test (without
; flaky wall-clock measurement): ConstantTimeStrEq returns the correct
; boolean regardless of WHERE the first mismatch falls, and never
; conflates length-mismatch with byte-mismatch. The "constant time" part
; is enforced by structure (no early Return inside the loop) and is
; verified by inspection -- a regression that re-introduces an early
; exit changes the function's loop count, not its return value, so a
; result-only test can't catch it. The compensating discipline is to
; document the invariant inline and gate refactors via review.
;
; The cases below cover: shared-prefix length mismatch (the case a
; short-circuit compare gets right "for free" but at the cost of
; leaking timing), one-byte difference at each of {start, middle, end},
; identical, and empty-on-both-sides.

Test consttime_identical()
	Assert(ConstantTimeStrEq%("", "") = True)
	Assert(ConstantTimeStrEq%("a", "a") = True)
	Assert(ConstantTimeStrEq%("hello", "hello") = True)
	Local Hex$ = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
	Assert(ConstantTimeStrEq%(Hex, Hex) = True)
End Test

Test consttime_length_mismatch()
	; Shared prefix but one string is longer. Length-leaking compares
	; would return False quickly here; the timing-uniform helper must
	; still return False but for the right reason.
	Assert(ConstantTimeStrEq%("abc", "abcd") = False)
	Assert(ConstantTimeStrEq%("abcd", "abc") = False)
	Assert(ConstantTimeStrEq%("", "x") = False)
	Assert(ConstantTimeStrEq%("x", "") = False)
End Test

Test consttime_diff_at_first_byte()
	Assert(ConstantTimeStrEq%("aaaaaaaaaaaaaaaa", "Xaaaaaaaaaaaaaaa") = False)
End Test

Test consttime_diff_at_middle_byte()
	Assert(ConstantTimeStrEq%("aaaaaaaaaaaaaaaa", "aaaaaaaXaaaaaaaa") = False)
End Test

Test consttime_diff_at_last_byte()
	; The case a short-circuit compare returns LAST, leaking the most
	; timing. Result must match the diff-at-first-byte case structurally.
	Assert(ConstantTimeStrEq%("aaaaaaaaaaaaaaaa", "aaaaaaaaaaaaaaaX") = False)
End Test

Test consttime_realistic_hex_diff()
	; Two SHA-256-shaped 64-char hex strings differing only in the
	; final nibble -- the exact shape an attacker would exploit if
	; the v1 compare site short-circuited on the first matching byte.
	Local A$ = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
	Local B$ = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ae"
	Assert(ConstantTimeStrEq%(A, B) = False)
	Assert(ConstantTimeStrEq%(A, A) = True)
End Test

; --- VerifyPassword% timing-uniformity contract -----------------------
;
; The behaviour-observable invariants from the timing-oracle fix:
;   - empty Stored still returns False (existing contract, preserved)
;   - empty ClientMD5 still returns False
;   - malformed v1 records still return False
;   - successful verify still works for both v1 and legacy formats
; Wall-clock timing equivalence between these paths is NOT asserted
; because Blitz3D test timing is too noisy to be meaningful in CI; the
; structural guarantee is documented in PasswordHash.bb's contract
; block and enforced by code review.

Test verify_dummy_hash_path_on_empty_stored_returns_false()
	; The dummy-hash path runs SHA-256 internally for timing uniformity
	; and discards the result. The observable behaviour is still
	; "returns False, no crash, no side effect on the input strings".
	Assert(VerifyPassword%("", "5d41402abc4b2a76b9719d911017c592") = False)
	; And with both empty -- corner case to make sure the dummy hash
	; handles an empty ClientMD5 too.
	Assert(VerifyPassword%("", "") = False)
End Test

Test verify_dummy_hash_path_on_malformed_v1_returns_false()
	; Malformed v1 records (wrong length, wrong separator) take the
	; dummy-hash path internally (the v1 length check fails AFTER the
	; dummy hash runs). Result is still False.
	Assert(VerifyPassword%("$1$short", "5d41402abc4b2a76b9719d911017c592") = False)
	Assert(VerifyPassword%("$1$abcdefghijklmnopXffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", "5d41402abc4b2a76b9719d911017c592") = False)
End Test
