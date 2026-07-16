Strict
EnableGC

; The packet dispatcher and screen renderer pull in the live client graph, so
; this bounded source contract protects the fixed packet shape and its local
; divisor guard without including either production module in the test harness.

Function ScreenFlashHandlerChecksPayloadBeforeDecode%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InCase%, Guarded%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Case P_ScreenFlash") > 0 Then InCase = True
		If InCase = True And Instr(Line$, "Case P_XPUpdate") > 0 Then Exit
		If InCase = True
			If Instr(Line$, "If Len(M\MessageData$) < 11 Then") > 0 Then Guarded = True
			If Instr(Line$, "RCE_IntFromStr(Mid$(M\MessageData$") > 0 And Guarded = False
				CloseFile F
				Return False
			EndIf
			If Guarded = True And Instr(Line$, "ScreenFlash(Red, Green, Blue, TexID, Length, Alpha#)") > 0
				CloseFile F
				Return True
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False

End Function

Function ScreenFlashRejectsInvalidDivisors%(Path$)

	Local F.BBStream = ReadFile(Path$)
	Local InFunction%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function ScreenFlash(R, G, B, TextureID, Length, InitialAlpha# = 1.0)") > 0 Then InFunction = True
		If InFunction = True And Instr(Line$, "FlashLength = Float#(Length) / InitialAlpha#") > 0
			CloseFile F
			Return Stage = 4
		EndIf
		If InFunction = True
			If Stage = 0 And Instr(Line$, "If InitialAlpha# <= 0.0 Or Length <= 0") > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, "Flashing = False") > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, "EntityAlpha(FlashEN, 0.0)") > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, "Return") > 0 Then Stage = 4
		EndIf
	Wend

	CloseFile F
	Return False

End Function

Test testScreenFlashPacketHasAnElevenByteBoundaryGuard()
	Assert(ScreenFlashHandlerChecksPayloadBeforeDecode%("Modules\ClientNet.bb") = True)
End Test

Test testScreenFlashRejectsZeroOrNegativeDivisorsBeforeDivision()
	Assert(ScreenFlashRejectsInvalidDivisors%("Client.bb") = True)
End Test
