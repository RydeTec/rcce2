Strict
EnableGC

; UpdateCombat depends on the full client and renderer graph, so this bounded
; source contract protects the stale PlayerTarget guard without importing it.
; BlitzForge evaluates Or eagerly: a missing ActorInstance must return before
; the target health field is read.

Function LeadingTabs%(Line$)
	Local Count%, Pos% = 1
	While Pos <= Len(Line$)
		If Mid$(Line$, Pos, 1) <> Chr$(9) Then Exit
		Count = Count + 1
		Pos = Pos + 1
	Wend
	Return Count
End Function

Function StalePlayerTargetReturnsBeforeHealthRead%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Stage%, GuardIndent%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function UpdateCombat()") > 0 Then Stage = 1
		If Stage > 0 And Stage < 7 And Instr(Line$, "A\Attributes\Value[HealthStat]") > 0
			CloseFile F
			Return False
		EndIf
		If Stage = 1 And Instr(Line$, "If A = Null") > 0
			GuardIndent = LeadingTabs(Line$)
			Stage = 2
		EndIf
		If Stage = 2 And Instr(Line$, "PlayerTarget = 0") > 0 Then Stage = 3
		If Stage = 3 And Instr(Line$, "HideEntity(ActorSelectEN)") > 0 Then Stage = 4
		If Stage = 4 And Instr(Line$, "DestroyCharInteractionWindow()") > 0 Then Stage = 5
		If Stage = 5 And Instr(Line$, "Return") > 0
			If LeadingTabs(Line$) <> GuardIndent + 1 Then Return False
			If Mid$(Line$, LeadingTabs(Line$) + 1, 6) <> "Return" Then Return False
			Stage = 6
		EndIf
		If Stage = 6 And Instr(Line$, "EndIf") > 0
			If LeadingTabs(Line$) < GuardIndent Then Return False
			If LeadingTabs(Line$) = GuardIndent Then Stage = 7
		EndIf
		If Stage = 7 And Instr(Line$, "If A\Attributes\Value[HealthStat] < 1") > 0
			CloseFile F
			Return True
		EndIf
		If Stage > 0 And Instr(Line$, "End Function") > 0 Then Exit
	Wend

	CloseFile F
	Return False

End Function

Test testStalePlayerTargetReturnsBeforeHealthRead()
	Assert(StalePlayerTargetReturnsBeforeHealthRead%("Modules\ClientCombat.bb") = True)
End Test
