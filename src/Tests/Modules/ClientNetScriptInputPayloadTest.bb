Strict
EnableGC

; P_ScriptInput is [4B script handle][1B input type][2B title length]
; [title][prompt]. The variable prompt may be empty, but the fixed header and
; the title declared by its two-byte length must be present before UI state is
; allocated.
Function ScriptInputPayloadGuard%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InHandler%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Case P_ScriptInput") > 0 Then InHandler = True
		If InHandler = True And Instr(Line$, "Case P_Dialog") > 0 Then Exit
		If InHandler = True
			; Header-short frames must not decode or allocate anything.
			If Stage < 2 And (Instr(Line$, "Mid$(M\\MessageData$") > 0 Or Instr(Line$, "CreateTextInput") > 0)
				CloseFile F
				Return False
			EndIf
			; A complete header permits NameLen decoding, but title, prompt, and
			; allocation still require the declared title to fit.
			If Stage < 7 And (Instr(Line$, "Title$ = Mid$(M\\MessageData$") > 0 Or Instr(Line$, "Prompt$ = Mid$(M\\MessageData$") > 0 Or Instr(Line$, "CreateTextInput") > 0)
				CloseFile F
				Return False
			EndIf
			; Both valid branches must retain their sensitive work before their
			; matching EndIf. Token order alone would permit an escaped guard.
			If (Stage = 3 Or Stage = 7) And Trim$(Line$) = "EndIf"
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "If Len(M\\MessageData$) < 7") > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, "P_ScriptInput: malformed header, dropping") > 0 Then Stage = 2
			If Stage = 2 And Trim$(Line$) = "Else" Then Stage = 3
			If Stage = 3 And Instr(Line$, "NameLen = RCE_IntFromStr(Mid$(M\\MessageData$, 6, 2))") > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, "If NameLen > Len(M\\MessageData$) - 7") > 0 Then Stage = 5
			If Stage = 5 And Instr(Line$, "P_ScriptInput: truncated title, dropping") > 0 Then Stage = 6
			If Stage = 6 And Trim$(Line$) = "Else" Then Stage = 7
			If Stage >= 7 And Stage < 10
				If Stage = 7 And Instr(Line$, "Title$ = Mid$(M\\MessageData$, 8, NameLen)") > 0 Then Stage = 8
				If Stage = 8 And Instr(Line$, "Prompt$ = Mid$(M\\MessageData$, 8 + NameLen)") > 0 Then Stage = 9
				If Stage = 9 And Instr(Line$, "CreateTextInput(Title$, Prompt$") > 0 Then Stage = 10
			EndIf
		EndIf
	Wend

	CloseFile F
	Return Stage = 10
End Function

Test testScriptInputRejectsTruncatedHeaderAndTitleBeforeUiAllocation()
	Assert(ScriptInputPayloadGuard%("Modules\\ClientNet.bb") = True)
End Test
