Strict
EnableGC

; Regression test pinning the "auth before disclosure" contract on
; the P_VerifyAccount handler in ServerNet.bb.
;
; Pre-fix: distinct response codes were emitted before the password
; was verified, giving an unauthenticated attacker an enumeration
; oracle for usernames, ban state, and online state:
;
;   no such username                       -> "N"
;   existing username, already logged on   -> "L"  (sent before pwd check!)
;   existing username, wrong password      -> "P"
;   existing username, wrong pwd, banned   -> "B"  (sent before pwd check!)
;   existing username, correct password    -> "Y"
;
; Post-fix: the only response on any auth failure is "P", and the
; existence-revealing codes ("L", "B") are emitted ONLY after the
; supplied password verifies. This file pins that state machine so a
; future refactor can't re-open the oracle.
;
; ServerNet.bb pulls the entire wire / actor / item graph and can't be
; Included into a test build. Following the established
; ClampFloatTest / BVMPrivilegeGateTest / SafeWriteTest pattern, the
; decision logic is replicated verbatim below and exercised across
; the 5 input states the handler discriminates. A behaviour change
; in production must update both copies; the duplication is the
; trigger to refresh the test rationale.

; --- Replicated state machine -----------------------------------------
; Inputs match the post-collapse server branch order in
; ServerNet.bb::P_VerifyAccount (around line 2180-2255). The integer
; sentinel values mirror production exactly so a future contributor
; can diff the helper against the handler without translating
; abstractions:
;
;   FoundA     : True iff Username matches an Account record
;   PwdLen     : the supplied password byte count (0 = truncated packet)
;   PwdOk      : True iff stored hash verifies against the supplied bytes
;   IsBanned   : Account\IsBanned int field (production: <> False -> banned)
;   LoggedOn   : Account\LoggedOn int field (production: -1 = logged out,
;                anything else = active session)
;
; Returns the single-byte wire code the handler would emit.

Function VerifyAccountResponse$(FoundA, PwdLen, PwdOk, IsBanned, LoggedOn)
	If FoundA = False Or PwdLen < 1 Then Return "P"
	If PwdOk = False Then Return "P"
	If IsBanned <> False Then Return "B"
	If LoggedOn <> -1 Then Return "L"
	Return "Y"
End Function

; ======================================================================
; Pre-auth failure paths -- the unauthenticated attacker probes.
; All MUST collapse to the same "P" response so the wire stream
; reveals nothing about which precondition failed.
; ======================================================================

Test testResponseUnknownUsernameIsP()
	; The historical "N" leak. Attacker scans usernames against a
	; throwaway password; must get "P" indistinguishable from a real
	; account they happened to guess wrong on.
	Assert(VerifyAccountResponse$(False, 16, False, False, -1) = "P")
End Test

Test testResponseTruncatedPasswordPacketIsP()
	; A 1-byte packet leaves PwdLen=0 and Mid$ returns "" -- the empty
	; string would match any account whose Pass$ was historically
	; stored empty. Collapse to "P" so the response doesn't betray
	; this special case.
	Assert(VerifyAccountResponse$(True, 0, False, False, -1) = "P")
End Test

Test testResponseWrongPasswordIsP()
	Assert(VerifyAccountResponse$(True, 16, False, False, -1) = "P")
End Test

Test testResponseWrongPasswordOnBannedAccountIsP()
	; The historical "B" leak. Attacker discovers ban status without
	; ever proving they own the account. Must collapse to "P".
	Assert(VerifyAccountResponse$(True, 16, False, True, -1) = "P")
End Test

Test testResponseWrongPasswordOnLoggedInAccountIsP()
	; The historical "L" leak. Attacker probes for active sessions
	; ("is this user online right now?") without auth. Must collapse
	; to "P".
	Assert(VerifyAccountResponse$(True, 16, False, False, 0) = "P")
End Test

Test testResponseWrongPasswordOnBannedAndLoggedInIsP()
	; Both side-channels at once; collapse to "P" regardless.
	Assert(VerifyAccountResponse$(True, 16, False, True, 0) = "P")
End Test

; ======================================================================
; Post-auth disclosure paths -- only emitted after PwdOk=True, so
; only the legitimate account owner can reach them.
; ======================================================================

Test testResponseCorrectPasswordBannedIsB()
	; Banned takes precedence over LoggedOn because the engine refuses
	; the session entirely; no point telling the user "you're already
	; logged on" when the ban will block the new session anyway.
	Assert(VerifyAccountResponse$(True, 16, True, True, -1) = "B")
End Test

Test testResponseCorrectPasswordBannedAndLoggedInIsB()
	; Banned still takes precedence even with a stale-looking active
	; session.
	Assert(VerifyAccountResponse$(True, 16, True, True, 0) = "B")
End Test

Test testResponseCorrectPasswordLoggedInIsL()
	; Not banned, but a session exists -- legitimate user gets the
	; "already logged on elsewhere" hint they can act on.
	Assert(VerifyAccountResponse$(True, 16, True, False, 0) = "L")
End Test

Test testResponseCorrectPasswordSuccessIsY()
	; The happy path.
	Assert(VerifyAccountResponse$(True, 16, True, False, -1) = "Y")
End Test

; ======================================================================
; Negative-space sanity checks -- ensure NO input combination produces
; the historical "N" code (which the new server never emits), and
; "L" / "B" only ever fire on the PwdOk=True path.
; ======================================================================

Test testResponseNeverEmitsLegacyN()
	; Brute-force the 32-cell logical input grid (5 inputs x 2 states).
	; LoggedOn iterates {-1, 0}: -1 = logged out, 0 = active session,
	; matching the production `<> -1` test (any non-(-1) value branches
	; the same way). Asserts "N" is never the answer.
	Local foundA, pwdLen, pwdOk, banned, loggedOn
	For foundA = 0 To 1
		For pwdLen = 0 To 1
			For pwdOk = 0 To 1
				For banned = 0 To 1
					For loggedOn = -1 To 0
						Local r$ = VerifyAccountResponse$(foundA, pwdLen, pwdOk, banned, loggedOn)
						Assert(r$ <> "N")
					Next
				Next
			Next
		Next
	Next
End Test

Test testResponseLOnlyOnSuccessfulAuth()
	; "L" must require PwdOk=True. Any False/PwdOk combination must
	; not be able to produce "L".
	Local foundA, pwdLen, banned, loggedOn
	For foundA = 0 To 1
		For pwdLen = 0 To 1
			For banned = 0 To 1
				For loggedOn = -1 To 0
					Local r$ = VerifyAccountResponse$(foundA, pwdLen, False, banned, loggedOn)
					Assert(r$ <> "L")
				Next
			Next
		Next
	Next
End Test

Test testResponseBOnlyOnSuccessfulAuth()
	; Same for "B" -- ban disclosure requires the user to have proven
	; ownership first.
	Local foundA, pwdLen, banned, loggedOn
	For foundA = 0 To 1
		For pwdLen = 0 To 1
			For banned = 0 To 1
				For loggedOn = -1 To 0
					Local r$ = VerifyAccountResponse$(foundA, pwdLen, False, banned, loggedOn)
					Assert(r$ <> "B")
				Next
			Next
		Next
	Next
End Test

Test testResponseYOnlyOnSuccessfulAuth()
	; "Y" requires PwdOk=True AND not banned AND not logged on.
	Local foundA, pwdLen, banned, loggedOn
	For foundA = 0 To 1
		For pwdLen = 0 To 1
			For banned = 0 To 1
				For loggedOn = -1 To 0
					Local r$ = VerifyAccountResponse$(foundA, pwdLen, False, banned, loggedOn)
					Assert(r$ <> "Y")
				Next
			Next
		Next
	Next
End Test
