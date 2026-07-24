Strict
EnableGC

; Source-contract regression for the legacy P_ChangePassword references. These
; pages describe a live server handler with no shipping-client UI, so inspect
; their bounded wording without pulling the ServerNet runtime graph into a test.

Function FileContains%(Path$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then F = ReadFile("..\\..\\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, Needle$) > 0
			CloseFile F
			Return True
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Test testLegacyModuleDocsDescribeTheLivePasswordChangeHandler()
	Assert(FileContains%("docs\\modules\\packets.md", "P\_ChangePassword - Request from the client to change an account password; the server handler is live, but the shipping client has no sender/UI") = True)
	Assert(FileContains%("docs\\modules\\packets.md", "(not implemented)") = False)
	Assert(FileContains%("docs\\modules\\servernet.md", "| `P_ChangePassword` (2657) | Password rotation |") = True)
	Assert(FileContains%("docs\\protocol\\packets\\P_ChangePassword.md", "**Server handler:** [ServerNet.bb:2657]") = True)
	Assert(FileContains%("docs\\protocol\\packets\\P_ChangePassword.md", "ServerNet.bb:260") = False)
	Assert(FileContains%("docs\\protocol\\packets\\P_ChangePassword.md", "ServerNet.bb:2665-2691") = True)
End Test

Test testMD5DocsListOnlyCurrentMainMenuPasswordSenders()
	Assert(FileContains%("docs\\modules\\md5.md", "P_VerifyAccount` / `P_CreateAccount`; it does not currently send `P_ChangePassword`") = True)
	Assert(FileContains%("docs\\modules\\md5.md", "P_VerifyAccount` / `P_CreateAccount` / `P_ChangePassword`") = False)
	Assert(FileContains%("Modules\\MainMenu.bb", "P_ChangePassword") = False)
	Assert(FileContains%("Modules\\ServerNet.bb", "Case P_ChangePassword") = True)
End Test

Test testAuthenticationReferenceUsesCurrentAnchorsAndThrottleHistory()
	Assert(FileContains%("docs\\modules\\servernet.md", "| `P_CreateAccount` (2466) |") = True)
	Assert(FileContains%("docs\\modules\\servernet.md", "| `P_VerifyAccount` (2522) |") = True)
	Assert(FileContains%("docs\\modules\\servernet.md", "| `P_ChangePassword` (2657) |") = True)
	Assert(FileContains%("docs\\modules\\servernet.md", "| `P_FetchCharacter` (2733) |") = True)
	Assert(FileContains%("docs\\modules\\servernet.md", "| `P_CreateCharacter` (2857) |") = True)
	Assert(FileContains%("docs\\modules\\servernet.md", "| `P_DeleteCharacter` (3073) |") = True)
	Assert(FileContains%("docs\\protocol\\packets\\P_VerifyAccount.md", "P_ChangePassword` was subsequently rate-limited by PR [#687]") = True)
	Assert(FileContains%("docs\\protocol\\packets\\P_VerifyAccount.md", "P_ChangePassword` was **not** included — see") = False)
	Assert(FileContains%("Modules\\ServerNet.bb", "Case P_ChangePassword") = True)
	Assert(FileContains%("Modules\\ServerNet.bb", "LoginAttemptOk(M\\FromID)") = True)
End Test
