Strict
EnableGC

; Regression test pinning the "auth before disclosure" contract on
; the P_ChangePassword handler in ServerNet.bb.
;
; Same enumeration-oracle threat model as PR #264 closed for
; P_VerifyAccount, applied to the sibling handler:
;
; Pre-fix: an unauthenticated peer could send P_ChangePassword with
; any username + any password and distinguish:
;   no such username                        -> "N"
;   account exists, wrong password / non-   -> "P"
;     session-owner
;   account exists, correct pwd, session    -> "Y"
;     owner (successful change)
;
; The session-owner gate (RequesterOwnsAccountSession) blocks the
; actual change but does NOT block the response-code probe, so
; usernames could still be harvested with no auth at all.
;
; Post-fix: every failure path collapses to "P"; "Y" is reachable
; only when the caller proves both (a) correct password AND
; (b) ownership of the current session for that account. This file
; pins that state machine.
;
; ServerNet.bb can't be Included into a test build; following the
; established ClampFloatTest / BVMPrivilegeGateTest / SafeWriteTest /
; AccountEnumerationTest pattern, the decision logic is replicated
; verbatim and exercised across the input states the handler
; discriminates. A behaviour change in production must update both
; copies.

; --- Replicated state machine -----------------------------------------
; Inputs match the post-collapse server branch order in
; ServerNet.bb::P_ChangePassword (around line 2260-2300):
;
;   FoundA       : True iff Username matches an Account record
;   PwdLen       : the supplied password byte count (0 = truncated packet)
;   PwdOk        : True iff stored hash verifies against supplied bytes
;                  AND the stored Pass$ is non-empty (production
;                  short-circuits an empty stored Pass$ via the same
;                  AND chain as the verify call)
;   SessionOwner : True iff RequesterOwnsAccountSession(A, M\FromID)
;
; Returns the single-byte wire code the handler would emit.

Function ChangePasswordResponse$(FoundA, PwdLen, PwdOk, SessionOwner)
	If FoundA = False Then Return "P"
	If PwdLen < 1 Then Return "P"
	If PwdOk = False Then Return "P"
	If SessionOwner = False Then Return "P"
	Return "Y"
End Function

; ======================================================================
; Pre-auth failure paths -- every probe the unauthenticated attacker
; can mount must collapse to "P".
; ======================================================================

Test testChangePasswordUnknownUsernameIsP()
	; The historical "N" leak: attacker scans usernames against any
	; throwaway password. Pre-fix yielded "N" for nonexistent and "P"
	; for everything else; post-fix the response is "P" either way.
	Assert(ChangePasswordResponse$(False, 16, False, False) = "P")
End Test

Test testChangePasswordTruncatedPacketIsP()
	Assert(ChangePasswordResponse$(True, 0, False, False) = "P")
End Test

Test testChangePasswordWrongPasswordIsP()
	; Account exists, wrong password.
	Assert(ChangePasswordResponse$(True, 16, False, False) = "P")
End Test

Test testChangePasswordWrongPasswordIsPEvenIfSessionOwner()
	; Pathological: session-owner replays with a bad password. Still
	; "P" so an attacker who hijacks a connection's RakNet id can't
	; distinguish "right session, wrong password" from any other failure.
	Assert(ChangePasswordResponse$(True, 16, False, True) = "P")
End Test

Test testChangePasswordCorrectPasswordNonSessionOwnerIsP()
	; The session-owner gate is the actual protection against account
	; takeover. Verifies it short-circuits to "P" without distinguishing
	; from a normal wrong-password failure on the wire.
	Assert(ChangePasswordResponse$(True, 16, True, False) = "P")
End Test

; ======================================================================
; Success path -- requires all four preconditions True.
; ======================================================================

Test testChangePasswordSuccessRequiresAllFour()
	Assert(ChangePasswordResponse$(True, 16, True, True) = "Y")
End Test

; ======================================================================
; Negative-space sweep -- every input combination short of full
; success MUST collapse to "P", and "N" must never appear in the
; output of the new handler.
; ======================================================================

Test testChangePasswordNeverEmitsLegacyN()
	; Brute-force the 16-cell input grid (4 booleans). Assert "N" is
	; never the answer post-collapse.
	Local foundA, pwdLen, pwdOk, sessionOwner
	For foundA = 0 To 1
		For pwdLen = 0 To 1
			For pwdOk = 0 To 1
				For sessionOwner = 0 To 1
					Local r$ = ChangePasswordResponse$(foundA, pwdLen, pwdOk, sessionOwner)
					Assert(r$ <> "N")
				Next
			Next
		Next
	Next
End Test

Test testChangePasswordYOnlyOnAllConditionsTrue()
	; "Y" must require ALL of (FoundA, PwdLen>=1, PwdOk, SessionOwner).
	; Any one False/zero input must produce "P", never "Y". This is the
	; structural guarantee that account takeover requires both
	; credential knowledge AND session ownership.
	Local foundA, pwdLen, pwdOk, sessionOwner
	For foundA = 0 To 1
		For pwdLen = 0 To 1
			For pwdOk = 0 To 1
				For sessionOwner = 0 To 1
					Local r$ = ChangePasswordResponse$(foundA, pwdLen, pwdOk, sessionOwner)
					; Only the (1, 1, 1, 1) corner is allowed to be "Y".
					If r$ = "Y"
						Assert(foundA = 1)
						Assert(pwdLen = 1)
						Assert(pwdOk = 1)
						Assert(sessionOwner = 1)
					EndIf
				Next
			Next
		Next
	Next
End Test
