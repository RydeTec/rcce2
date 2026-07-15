Strict
EnableGC

; UpdateCombat depends on the full client and renderer graph, so this bounded
; source contract protects the stale PlayerTarget guard without importing it.
; BlitzForge evaluates Or eagerly: a missing ActorInstance must return before
; the target health field is read.

Function StalePlayerTargetReturnsBeforeHealthRead%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function UpdateCombat()") > 0 Then Stage = 1
		If Stage = 1 And Instr(Line$, "If A = Null Or A\Attributes\Value[HealthStat] < 1") > 0
			CloseFile F
			Return False
		EndIf
		If Stage = 1 And Instr(Line$, "If A = Null") > 0 Then Stage = 2
		If Stage = 2 And Instr(Line$, "PlayerTarget = 0") > 0 Then Stage = 3
		If Stage = 3 And Instr(Line$, "HideEntity(ActorSelectEN)") > 0 Then Stage = 4
		If Stage = 4 And Instr(Line$, "DestroyCharInteractionWindow()") > 0 Then Stage = 5
		If Stage = 5 And Instr(Line$, "Return") > 0 Then Stage = 6
		If Stage = 6 And Instr(Line$, "EndIf") > 0 Then Stage = 7
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
