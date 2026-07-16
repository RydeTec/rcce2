Strict
EnableGC

; The packet dispatcher and screen renderer pull in the live client graph, so
; this bounded source contract protects the fixed packet shape and its local
; divisor guard without including either production module in the test harness.

Function LeadingTabs%(Line$)
	Local Count%, Pos% = 1
	While Pos <= Len(Line$)
		If Mid$(Line$, Pos, 1) <> Chr$(9) Then Exit
		Count = Count + 1
		Pos = Pos + 1
	Wend
	Return Count
End Function

Function ScreenFlashHandlerChecksPayloadBeforeDecode%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local GuardIndent%, InCase%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Case P_ScreenFlash") > 0 Then InCase = True
		If InCase = True And Instr(Line$, "Case P_XPUpdate") > 0 Then Exit
		If InCase = True
			If Stage = 0 And Instr(Line$, "If Len(M\MessageData$) < 11 Then") > 0
				GuardIndent = LeadingTabs(Line$)
				Stage = 1
			EndIf
			If Stage = 1 And Instr(Line$, "Else") > 0 And LeadingTabs(Line$) = GuardIndent Then Stage = 2
			If Instr(Line$, "RCE_IntFromStr(Mid$(M\MessageData$") > 0
				If Stage <> 2 Or LeadingTabs(Line$) <> GuardIndent + 1
					CloseFile F
					Return False
				EndIf
			EndIf
			If Stage = 2 And Instr(Line$, "ScreenFlash(Red, Green, Blue, TexID, Length, Alpha#)") > 0
				If LeadingTabs(Line$) <> GuardIndent + 1
					CloseFile F
					Return False
				EndIf
				Stage = 3
			EndIf
			If Stage = 3 And Instr(Line$, "EndIf") > 0 And LeadingTabs(Line$) = GuardIndent
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
	Local GuardIndent%, InFunction%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function ScreenFlash(R, G, B, TextureID, Length, InitialAlpha# = 1.0)") > 0 Then InFunction = True
		If InFunction = True And Instr(Line$, "FlashLength = Float#(Length) / InitialAlpha#") > 0
			CloseFile F
			Return Stage = 6
		EndIf
		If InFunction = True
			If Stage = 0 And Instr(Line$, "If InitialAlpha# <= 0.0 Or Length <= 0") > 0
				GuardIndent = LeadingTabs(Line$)
				Stage = 1
			EndIf
			If Stage = 1 And Instr(Line$, "Flashing = False") > 0 And LeadingTabs(Line$) = GuardIndent + 1 Then Stage = 2
			If Stage = 2 And Instr(Line$, "EntityAlpha(FlashEN, 0.0)") > 0 And LeadingTabs(Line$) = GuardIndent + 1 Then Stage = 3
			If Stage = 3 And Instr(Line$, "Return") > 0 And LeadingTabs(Line$) = GuardIndent + 1 Then Stage = 4
			If Stage = 4 And Instr(Line$, "EndIf") > 0 And LeadingTabs(Line$) = GuardIndent Then Stage = 5
			If Stage = 5 And Instr(Line$, "If TextureID < 65535") > 0 Then Stage = 6
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
