; Tests for My_Escape$ from Modules/MySQL.bb.
;
; NOT Strict. Mirrors the production function verbatim (which is in
; non-Strict MySQL.bb). The function does in-loop string concatenation
; (`out = out + "..."`), which Strict mode rejects mid-function -- see
; the ClampFloatTest.bb header for the same precedent (its production
; counterpart uses bare-float typing). The test pins behavior; a future
; refactor must update both the production copy and this duplicate.
;
; Regression test pinning the SQL-injection defense in MySQL.bb's
; My_Escape$. Every player- and script-controlled string that travels
; through this module's queries depends on this function being correct
; (the audit-comment block at the top of MySQL.bb enumerates the reach
; -- username / password / email at account creation; character name
; at character creation; actor Area / Tag / Script / DeathScript;
; ScriptGlobals via BVM_SETGLOBAL; quest entries via BVM_ADDQUESTENTRY;
; action-bar slot text via BVM_SETACTIONBARSLOT). One wrong character
; class in this function is a server-wide SQL injection vulnerability.
;
; Pre-PR-#326 the function had ZERO test coverage. A future contributor
; refactoring the escape branches (or accidentally dropping one) would
; have had no automated guard.
;
; My_Escape$ is included from MySQL.bb via the test workspace, but
; pulling MySQL.bb in directly pulls in the entire SQL DLL surface +
; the network/world graph (Account / ActorInstance / etc.). Replicate
; the function locally using the established replicated-gate pattern
; -- any production change to the escape branches MUST update this
; file (the duplication is the trigger to refresh the test rationale).

Function MyEscapeRef$(s$)
	Local out$ = ""
	Local i, c
	For i = 1 To Len(s$)
		c = Asc(Mid$(s$, i, 1))
		If c = 0
			out$ = out$ + "\0"
		ElseIf c = 10
			out$ = out$ + "\n"
		ElseIf c = 13
			out$ = out$ + "\r"
		ElseIf c = 26
			out$ = out$ + "\Z"
		ElseIf c = 34
			out$ = out$ + "\" + Chr$(34)
		ElseIf c = 39
			out$ = out$ + "\" + Chr$(39)
		ElseIf c = 92
			out$ = out$ + "\\"
		Else
			out$ = out$ + Chr$(c)
		EndIf
	Next
	Return out$
End Function

; ====================================================================
; Per-character escape transformations. Each test pins one of the 7
; special-character branches against the production contract.
; ====================================================================

Test testSingleQuoteEscapes()
	; The classic SQL-injection terminator. Without this branch a
	; player-supplied username `'); DROP TABLE rc_accounts; --` lands
	; in the query unescaped.
	Assert(MyEscapeRef$("'") = "\'")
End Test

Test testDoubleQuoteEscapes()
	; MySQL accepts both " and ' as string delimiters depending on
	; SQL_MODE. The audit comment cites the same single-quoted-literal
	; reach in MySQL.bb, but escaping " too is defensive against
	; reconfigured deployments.
	Assert(MyEscapeRef$(Chr$(34)) = "\" + Chr$(34))
End Test

Test testBackslashEscapes()
	; Backslash must double itself, otherwise the inserted backslashes
	; from the OTHER escapes get re-interpreted by MySQL.
	Assert(MyEscapeRef$("\") = "\\")
End Test

Test testNulByteEscapes()
	; NUL byte (Chr$(0)) is a string-terminator boundary for many SQL
	; parsers / drivers. Escape to `\0` literal.
	Assert(MyEscapeRef$(Chr$(0)) = "\0")
End Test

Test testNewlineEscapes()
	; LF (Chr$(10)) escapes to `\n`. Defends against parsers that
	; tokenize on newline before quote-counting.
	Assert(MyEscapeRef$(Chr$(10)) = "\n")
End Test

Test testCarriageReturnEscapes()
	; CR (Chr$(13)) escapes to `\r`. Pair with newline defense.
	Assert(MyEscapeRef$(Chr$(13)) = "\r")
End Test

Test testCtrlZEscapes()
	; Ctrl-Z (Chr$(26)) is a soft-EOF marker on Windows file streams
	; and some MySQL connector configurations treat it as
	; statement-terminating. Escape to `\Z`.
	Assert(MyEscapeRef$(Chr$(26)) = "\Z")
End Test

; ====================================================================
; Non-special characters pass through unchanged. These pin the
; "everything outside the 7 special chars is untouched" branch.
; ====================================================================

Test testAsciiLettersPassthrough()
	Assert(MyEscapeRef$("Alice") = "Alice")
	Assert(MyEscapeRef$("abcdefghijklmnopqrstuvwxyz") = "abcdefghijklmnopqrstuvwxyz")
	Assert(MyEscapeRef$("ABCDEFGHIJKLMNOPQRSTUVWXYZ") = "ABCDEFGHIJKLMNOPQRSTUVWXYZ")
End Test

Test testAsciiDigitsPassthrough()
	Assert(MyEscapeRef$("0123456789") = "0123456789")
End Test

Test testSafeAsciiPunctuationPassthrough()
	; These chars are NOT in the special set; they MUST pass through
	; or queries with legitimate punctuation would corrupt.
	Assert(MyEscapeRef$("user@example.com") = "user@example.com")
	Assert(MyEscapeRef$("Name (Title)") = "Name (Title)")
	Assert(MyEscapeRef$("a-b_c.d") = "a-b_c.d")
	Assert(MyEscapeRef$("key=value&other=thing") = "key=value&other=thing")
End Test

Test testEmptyStringPassthrough()
	; Edge case: zero-length input returns zero-length output.
	Assert(MyEscapeRef$("") = "")
End Test

; ====================================================================
; Combination cases: multiple special characters in one string,
; mixed with safe characters. Pins the per-character iteration
; pattern (no greedy / multi-char branching).
; ====================================================================

Test testMultipleSingleQuotesAllEscaped()
	; A real injection attempt would chain multiple quote-terminator
	; sequences; every one must escape.
	Assert(MyEscapeRef$("'OR'1'='1") = "\'OR\'1\'=\'1")
End Test

Test testMixedSpecialAndSafe()
	; "O'Brien's quote: \n in the middle"
	Local in$ = "O" + Chr$(39) + "Brien" + Chr$(39) + "s quote: " + Chr$(10) + " in middle"
	Local exp$ = "O\" + Chr$(39) + "Brien\" + Chr$(39) + "s quote: \n in middle"
	Assert(MyEscapeRef$(in$) = exp$)
End Test

Test testBackslashFirstThenQuoteOrder()
	; Backslash + single-quote together. The backslash must escape
	; to \\ FIRST (per character iteration), then the quote escapes
	; to \'. Combined output: \\\' (two backslashes for the backslash,
	; one backslash for the quote, then the quote). This is the
	; correct shape -- the inserted backslashes from the per-char
	; transforms are append-only, never re-scanned, so order of
	; If/ElseIf branches in the function doesn't matter.
	Assert(MyEscapeRef$("\'") = "\\\'")
End Test

Test testEveryEscapeCharInOneString()
	; All 7 special chars in one input. If any branch is missed in a
	; future refactor, this assert points at it.
	Local in$ = Chr$(0) + Chr$(10) + Chr$(13) + Chr$(26) + Chr$(34) + Chr$(39) + Chr$(92)
	Local exp$ = "\0" + "\n" + "\r" + "\Z" + "\" + Chr$(34) + "\" + Chr$(39) + "\\"
	Assert(MyEscapeRef$(in$) = exp$)
End Test

; ====================================================================
; Adversarial inputs from the audit-comment block at MySQL.bb:1-22.
; These pin the realistic exploit shapes the function must defend
; against (player-supplied username, character name, ScriptGlobals,
; quest entries, action-bar slot text).
; ====================================================================

Test testClassicSQLInjectionPayloadEscapes()
	; The canonical "drop table" payload. Post-escape it's just a
	; weird-looking string literal -- the surrounding query's quote
	; structure stays intact.
	Local payload$ = "'; DROP TABLE rc_accounts; --"
	Local escaped$ = MyEscapeRef$(payload$)
	; The leading ' must be escaped (not left bare to terminate the
	; query's open quote).
	Assert(Left$(escaped$, 2) = "\'")
	; The original ; ` ` characters pass through.
	Assert(Instr(escaped$, "DROP TABLE rc_accounts") > 0)
End Test

Test testStackedQueriesPayloadEscapes()
	; A payload that tries to inject a second statement via a quote
	; terminator + semicolon. Same defense: quote gets escaped, the
	; semicolon stays inert inside the now-properly-quoted literal.
	Local payload$ = "x" + Chr$(39) + ";INSERT INTO rc_accounts VALUES (...)"
	Local escaped$ = MyEscapeRef$(payload$)
	Assert(Instr(escaped$, "\" + Chr$(39)) > 0)
End Test

Test testBackslashSmuggleAttackEscapes()
	; The "smuggle a backslash to escape the escape" attack: input is
	; `\'` -- if the function escapes the quote first to `\'` and then
	; the existing backslash gets dropped, the resulting `\'` would be
	; mis-parsed by MySQL as an escaped quote, breaking the injection
	; defense. The per-character iteration in My_Escape$ avoids this
	; -- backslash hits its own branch (-> \\) independently of the
	; subsequent quote (-> \'), so input `\'` -> `\\\'` (escape both).
	Assert(MyEscapeRef$("\" + Chr$(39)) = "\\\" + Chr$(39))
End Test

; ====================================================================
; Idempotence-like property: re-escaping an already-escaped string
; further escapes the inserted backslashes. This is the correct
; behavior -- My_Escape$ is NOT idempotent because it's a
; one-direction encode, not a canonicalization. The audit comment in
; the production function notes this property indirectly via the
; "Backslash must be done first" framing.
; ====================================================================

Test testDoubleEscapeProducesAdditionalBackslashes()
	Local once$ = MyEscapeRef$("'")     ; -> \'
	Local twice$ = MyEscapeRef$(once$)  ; -> \\\'
	Assert(once$ = "\'")
	Assert(twice$ = "\\\'")
End Test
