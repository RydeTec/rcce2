Strict
EnableGC

; ServerNet.bb cannot be included in the standalone test harness because its
; packet dispatcher depends on the live network and world graph. This bounded
; source contract pins the existing P_ChangePassword field cursor instead.
;
; Wire layout: [username length][username][current password length]
; [current password][new password length][new password]. After consuming the
; current password bytes, the cursor must advance from its existing position;
; recomputing it from only PwdLen loses the username block.

Function ChangePasswordUsesForwardPasswordCursor%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InCase%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Case P_ChangePassword") > 0 Then InCase = True
		If InCase = True And Instr(Line$, "Case P_FetchCharacter") > 0 Then Exit
		If InCase = True
			; The legacy recomputation ignores UsernameLen and reads the new
			; password length from the wrong byte for every non-empty username.
			If Instr(Line$, "Offset = 2 + PwdLen") > 0
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "Offset = 2 + UsernameLen") > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, "PwdLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))") > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, "If PwdLen >= 1") > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, "Offset = Offset + 1 + PwdLen") > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, "PwdLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))") > 0 Then Stage = 5
			If Stage = 5 And Instr(Line$, "A\Pass$ = HashPassword$(Mid$(M\MessageData$, Offset + 1, PwdLen))") > 0
				CloseFile F
				Return True
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False

End Function

Test testChangePasswordAdvancesPastTheCurrentPasswordBlock()
	Assert(ChangePasswordUsesForwardPasswordCursor%("Modules\ServerNet.bb") = True)
End Test
