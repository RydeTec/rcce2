; ============================================================================
; Server-side password hashing with on-disk migration.
;
; Background
; ----------
; The wire protocol hands the server an MD5 of the user-typed password
; (the client does `MD5Pass$ = MD5$(Pass$)` before send). The server
; used to store that MD5 verbatim in Accounts.dat and compare incoming
; vs. stored as plaintext: anyone who reads the file gets the
; wire-equivalent of every account's password, and anyone who sees one
; login packet on the wire can replay it forever.
;
; This module keeps the wire format intact (the MD5 is still what the
; client sends) but wraps a salted SHA-256 around the at-rest copy, so
; theft of Accounts.dat no longer hands an attacker working
; credentials. Wire sniffing is still trivially replayable -- that
; requires TLS or a challenge/response protocol, both bigger projects.
;
; Storage format
; --------------
; Legacy:   <32 lowercase hex chars>           ; raw MD5 from client
; v1:       $1$<salt-16-chars>$<sha256-64-hex> ; SHA-256(salt + MD5)
;
; Verify accepts both; HashPassword$ always emits v1.
;
; Migration is automatic: on the first successful login of an account
; whose Accounts.dat entry is still legacy MD5, the server replaces
; A\Pass$ with a freshly salted v1 hash. The next SaveAccounts() then
; persists the upgrade.
; ============================================================================

Const PWHASH_VERSION_TAG$ = "$1$"
Const PWHASH_SALT_LEN%    = 16

; ----------------------------------------------------------------------------
; SHA-256 (FIPS 180-4) — pure Blitz3D implementation.
; Compute SHA-256 of an arbitrary 8-bit-clean string. Returns a 64-character
; lowercase hex string.
;
; Blitz uses signed 32-bit Int. Two-complement signed addition wraps the
; same way as unsigned at the bit level, so plain `+` is fine for the
; SHA-256 word arithmetic. `Shr` is logical right shift (zero fill) and
; `Shl` is left shift -- both correct for the round/schedule math here.
; ----------------------------------------------------------------------------

; Round constants K[0..63] (first 32 bits of fractional parts of cube roots
; of the first 64 primes). Initialised lazily on first use because Blitz3D
; doesn't take large hex literals in array initialisers.
Global PWHASH_KInit% = False
Dim PWHASH_K%(63)

; Message schedule array, shared across calls to keep allocation off the
; hot path. SHA256Hex re-fills this from scratch each call so prior
; contents don't matter.
Dim PWHASH_W%(63)

Function PWHASH_InitK()
	If PWHASH_KInit Then Return
	PWHASH_K(0)  = $428a2f98 : PWHASH_K(1)  = $71374491 : PWHASH_K(2)  = $b5c0fbcf : PWHASH_K(3)  = $e9b5dba5
	PWHASH_K(4)  = $3956c25b : PWHASH_K(5)  = $59f111f1 : PWHASH_K(6)  = $923f82a4 : PWHASH_K(7)  = $ab1c5ed5
	PWHASH_K(8)  = $d807aa98 : PWHASH_K(9)  = $12835b01 : PWHASH_K(10) = $243185be : PWHASH_K(11) = $550c7dc3
	PWHASH_K(12) = $72be5d74 : PWHASH_K(13) = $80deb1fe : PWHASH_K(14) = $9bdc06a7 : PWHASH_K(15) = $c19bf174
	PWHASH_K(16) = $e49b69c1 : PWHASH_K(17) = $efbe4786 : PWHASH_K(18) = $0fc19dc6 : PWHASH_K(19) = $240ca1cc
	PWHASH_K(20) = $2de92c6f : PWHASH_K(21) = $4a7484aa : PWHASH_K(22) = $5cb0a9dc : PWHASH_K(23) = $76f988da
	PWHASH_K(24) = $983e5152 : PWHASH_K(25) = $a831c66d : PWHASH_K(26) = $b00327c8 : PWHASH_K(27) = $bf597fc7
	PWHASH_K(28) = $c6e00bf3 : PWHASH_K(29) = $d5a79147 : PWHASH_K(30) = $06ca6351 : PWHASH_K(31) = $14292967
	PWHASH_K(32) = $27b70a85 : PWHASH_K(33) = $2e1b2138 : PWHASH_K(34) = $4d2c6dfc : PWHASH_K(35) = $53380d13
	PWHASH_K(36) = $650a7354 : PWHASH_K(37) = $766a0abb : PWHASH_K(38) = $81c2c92e : PWHASH_K(39) = $92722c85
	PWHASH_K(40) = $a2bfe8a1 : PWHASH_K(41) = $a81a664b : PWHASH_K(42) = $c24b8b70 : PWHASH_K(43) = $c76c51a3
	PWHASH_K(44) = $d192e819 : PWHASH_K(45) = $d6990624 : PWHASH_K(46) = $f40e3585 : PWHASH_K(47) = $106aa070
	PWHASH_K(48) = $19a4c116 : PWHASH_K(49) = $1e376c08 : PWHASH_K(50) = $2748774c : PWHASH_K(51) = $34b0bcb5
	PWHASH_K(52) = $391c0cb3 : PWHASH_K(53) = $4ed8aa4a : PWHASH_K(54) = $5b9cca4f : PWHASH_K(55) = $682e6ff3
	PWHASH_K(56) = $748f82ee : PWHASH_K(57) = $78a5636f : PWHASH_K(58) = $84c87814 : PWHASH_K(59) = $8cc70208
	PWHASH_K(60) = $90befffa : PWHASH_K(61) = $a4506ceb : PWHASH_K(62) = $bef9a3f7 : PWHASH_K(63) = $c67178f2
	PWHASH_KInit = True
End Function

; 32-bit logical right rotate.
Function PWHASH_ROTR%(X%, N%)
	; (X >>> N) | (X <<< (32 - N))
	Return (X Shr N) Or (X Shl (32 - N))
End Function

; Lowercase hex of one 32-bit word, big-endian byte order, 8 chars.
Function PWHASH_HexWord$(X%)
	Local HexChars$ = "0123456789abcdef"
	Local Out$ = ""
	Local i%
	For i = 7 To 0 Step -1
		Local Nyb% = (X Shr (i * 4)) And $f
		Out = Out + Mid$(HexChars, Nyb + 1, 1)
	Next
	Return Out
End Function

Function SHA256Hex$(Msg$)
	PWHASH_InitK()

	Local InputLen%    = Len(Msg)
	Local TotalBitLen% = InputLen * 8

	; Padding: append 0x80, then zero bytes, then the 64-bit big-endian
	; bit length, so total length is a multiple of 64 bytes. We always
	; need at least 1 (0x80) + 8 (length) = 9 trailing bytes, so the
	; padded length rounds up from (InputLen + 9) to the next 64.
	Local PaddedLen% = ((InputLen + 9 + 63) / 64) * 64
	Local hMsg = CreateBank(PaddedLen)
	; CreateBank zero-fills, so we only need to write the input bytes
	; and the 0x80 marker explicitly. The length field is written below.
	Local i%
	For i = 1 To InputLen
		PokeByte(hMsg, i - 1, Asc(Mid$(Msg, i, 1)))
	Next
	PokeByte(hMsg, InputLen, $80)

	; 64-bit length, big-endian. Blitz strings are at most ~2^31 bytes,
	; so the upper 32 bits of bit-length are always zero; we still write
	; them for the spec-compliant 8-byte field.
	PokeByte(hMsg, PaddedLen - 4, (TotalBitLen Shr 24) And $ff)
	PokeByte(hMsg, PaddedLen - 3, (TotalBitLen Shr 16) And $ff)
	PokeByte(hMsg, PaddedLen - 2, (TotalBitLen Shr 8)  And $ff)
	PokeByte(hMsg, PaddedLen - 1,  TotalBitLen         And $ff)

	; Initial hash state (FIPS 180-4 section 5.3.3).
	Local H0% = $6a09e667
	Local H1% = $bb67ae85
	Local H2% = $3c6ef372
	Local H3% = $a54ff53a
	Local H4% = $510e527f
	Local H5% = $9b05688c
	Local H6% = $1f83d9ab
	Local H7% = $5be0cd19

	; Process each 512-bit (64-byte) block.
	Local Block%
	For Block = 0 To PaddedLen - 1 Step 64
		Local t%
		For t = 0 To 15
			Local Off% = Block + t * 4
			Local B0% = PeekByte(hMsg, Off)     Shl 24
			Local B1% = PeekByte(hMsg, Off + 1) Shl 16
			Local B2% = PeekByte(hMsg, Off + 2) Shl  8
			Local B3% = PeekByte(hMsg, Off + 3)
			PWHASH_W(t) = B0 Or B1 Or B2 Or B3
		Next
		For t = 16 To 63
			Local W15% = PWHASH_W(t - 15)
			Local W2%  = PWHASH_W(t - 2)
			Local S0%  = PWHASH_ROTR(W15, 7)  Xor PWHASH_ROTR(W15, 18) Xor (W15 Shr 3)
			Local S1%  = PWHASH_ROTR(W2,  17) Xor PWHASH_ROTR(W2,  19) Xor (W2  Shr 10)
			PWHASH_W(t) = PWHASH_W(t - 16) + S0 + PWHASH_W(t - 7) + S1
		Next

		Local A% = H0
		Local B% = H1
		Local C% = H2
		Local D% = H3
		Local E% = H4
		Local F% = H5
		Local G% = H6
		Local H% = H7

		For t = 0 To 63
			Local BigS1%  = PWHASH_ROTR(E, 6) Xor PWHASH_ROTR(E, 11) Xor PWHASH_ROTR(E, 25)
			; `Not E` would be logical (0/1) in Blitz3D; we need bitwise
			; complement. XOR with -1 (= 0xffffffff in two's complement)
			; flips every bit -- equivalent to ~E in C.
			Local Ch%     = (E And F) Xor ((E Xor -1) And G)
			Local Temp1%  = H + BigS1 + Ch + PWHASH_K(t) + PWHASH_W(t)
			Local BigS0%  = PWHASH_ROTR(A, 2) Xor PWHASH_ROTR(A, 13) Xor PWHASH_ROTR(A, 22)
			Local Maj%    = (A And B) Xor (A And C) Xor (B And C)
			Local Temp2%  = BigS0 + Maj
			H = G : G = F : F = E : E = D + Temp1
			D = C : C = B : B = A : A = Temp1 + Temp2
		Next

		H0 = H0 + A : H1 = H1 + B : H2 = H2 + C : H3 = H3 + D
		H4 = H4 + E : H5 = H5 + F : H6 = H6 + G : H7 = H7 + H
	Next

	FreeBank hMsg

	Local Out$ = PWHASH_HexWord$(H0) + PWHASH_HexWord$(H1)
	Out = Out + PWHASH_HexWord$(H2) + PWHASH_HexWord$(H3)
	Out = Out + PWHASH_HexWord$(H4) + PWHASH_HexWord$(H5)
	Out = Out + PWHASH_HexWord$(H6) + PWHASH_HexWord$(H7)
	Return Out

End Function

; Generate a 16-character random salt from a URL-safe alphabet (no '$'
; or whitespace so the storage format stays parseable by a simple split).
Function GenerateSalt$()
	Local Alphabet$ = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
	Local AlphaLen% = Len(Alphabet)
	Local Out$ = ""
	Local i%
	For i = 1 To PWHASH_SALT_LEN
		Out = Out + Mid$(Alphabet, Rand(1, AlphaLen), 1)
	Next
	Return Out
End Function

; Produce a v1 storage record for the given client-supplied MD5 hex.
;
; Note: this hashes whatever bytes you hand it. It does not validate
; that ClientMD5 is in fact an MD5 -- that's the wire-format
; verification's job, and we want the function to be a pure helper.
Function HashPassword$(ClientMD5$)
	Local Salt$ = GenerateSalt$()
	Local Hash$ = SHA256Hex$(Salt + ClientMD5)
	Return PWHASH_VERSION_TAG + Salt + "$" + Hash
End Function

; Constant-time string equality. Iterates the full length of the longer
; string (no early exit on first differing byte) so the time-to-result
; does not depend on the position of the first mismatch -- the canonical
; mitigation for hash-comparison timing oracles. OR-accumulates each
; byte XOR into a running Diff so a single non-zero byte at any
; position dirties the result regardless of where it falls.
;
; Length mismatch is folded into Diff (set to 1 up front) so the result
; is also constant-time WRT length comparison.
Function ConstantTimeStrEq%(A$, B$)
	Local LenA% = Len(A)
	Local LenB% = Len(B)
	Local Diff% = 0
	If LenA <> LenB Then Diff = 1
	Local MaxLen% = LenA
	If LenB > MaxLen Then MaxLen = LenB
	Local i%
	For i = 1 To MaxLen
		Local AByte% = 0
		Local BByte% = 0
		If i <= LenA Then AByte = Asc(Mid$(A, i, 1))
		If i <= LenB Then BByte = Asc(Mid$(B, i, 1))
		Diff = Diff Or (AByte Xor BByte)
	Next
	Return Diff = 0
End Function

; Sentinel salt used by the no-account / malformed-record path so the
; dummy hash spends roughly the same SHA-256 cost as a real v1 verify.
; Value is deliberately a fixed string; it's only used to consume CPU
; cycles, never compared against a real stored record.
Const PWHASH_DUMMY_SALT$ = "rcce2_dummy_salt"

; Compare a stored record to an incoming client MD5. Returns True on match.
; Accepts both legacy (raw MD5) and v1 ($1$<salt>$<hash>) on-disk formats.
;
; Timing-uniformity contract: VerifyPassword% always pays the SHA-256
; cost regardless of input shape. The pre-fix early-out (`If Len(Stored)
; = 0 Then Return False`) was an unauthenticated-attacker timing oracle:
; an unknown-account login skipped the hash entirely and returned in
; ~microseconds while a known-account-wrong-password attempt paid the
; full hash. Combined with the hex-equality short-circuit at the v1
; compare site, that made byte-position-of-difference recoverable too.
;
; Post-fix:
;   - empty / malformed / wrong-length / non-v1-or-MD5-shaped Stored
;     still runs SHA256Hex$(PWHASH_DUMMY_SALT + ClientMD5) and discards
;     the result, so the attacker's wall-clock delta between
;     "account doesn't exist" and "wrong password" disappears.
;   - both compare paths (v1 SHA-256 hex, legacy MD5) use
;     ConstantTimeStrEq -- no first-differing-byte short-circuit.
;
; This closes the side-channel PR #264 ("P_VerifyAccount: close
; username/ban/presence enumeration oracle") deferred to a follow-up.
Function VerifyPassword%(Stored$, ClientMD5$)
	; Always pay the cost up front -- ClientMD5 may be "" too (truncated
	; packet), so use the empty string verbatim rather than gating on
	; ClientMD5 length. The dummy hash output is discarded; only the
	; CPU cost matters.
	; DO NOT remove or "optimize away" the DummyOut$ assignment below --
	; removing it re-opens the no-account / malformed-record timing
	; oracle this function exists to close.
	Local DummyOut$ = SHA256Hex$(PWHASH_DUMMY_SALT + ClientMD5)

	; v1 stored format: $1$<salt-16>$<hash-64>
	If Left$(Stored, Len(PWHASH_VERSION_TAG)) = PWHASH_VERSION_TAG
		If Len(Stored) <> Len(PWHASH_VERSION_TAG) + PWHASH_SALT_LEN + 1 + 64 Then Return False
		Local Salt$ = Mid$(Stored, Len(PWHASH_VERSION_TAG) + 1, PWHASH_SALT_LEN)
		Local Sep$  = Mid$(Stored, Len(PWHASH_VERSION_TAG) + PWHASH_SALT_LEN + 1, 1)
		If Sep <> "$" Then Return False
		Local StoredHash$ = Mid$(Stored, Len(PWHASH_VERSION_TAG) + PWHASH_SALT_LEN + 2)
		; Re-run SHA-256 with the real salt; the dummy hash already
		; warmed the path. Use ConstantTimeStrEq on the 64-char hex
		; output so a partially-correct hash isn't faster to reject.
		Return ConstantTimeStrEq%(SHA256Hex$(Salt + ClientMD5), StoredHash)
	EndIf

	; Legacy plain-MD5 record. The dummy SHA-256 above already paid the
	; v1-equivalent cost so a legacy account isn't timing-distinguishable
	; from a v1 account either. Use ConstantTimeStrEq for the byte-level
	; compare regardless of length.
	If Len(Stored) = 0 Then Return False
	Return ConstantTimeStrEq%(Stored, ClientMD5)
End Function

; True iff Stored is in the legacy plain-MD5 format and should be
; upgraded to v1 on next save.
Function PasswordIsLegacy%(Stored$)
	If Len(Stored) = 0 Then Return False
	If Left$(Stored, Len(PWHASH_VERSION_TAG)) = PWHASH_VERSION_TAG Then Return False
	Return True
End Function

; Migrate a verified legacy entry to the v1 format. Returns the new
; storage record, or Stored unchanged if it's already v1 / empty.
; Callers should only invoke this AFTER a successful VerifyPassword%
; against the same ClientMD5 -- otherwise we'd be re-stamping an
; unverified credential.
Function UpgradePasswordIfLegacy$(Stored$, ClientMD5$)
	If Not PasswordIsLegacy%(Stored) Then Return Stored
	Return HashPassword$(ClientMD5)
End Function
