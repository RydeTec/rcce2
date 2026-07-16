Strict
EnableGC

; Regression contract for UpdateCombat's BloodSpurt expiry sweep. ClientCombat
; cannot be included by a standalone Strict test without the full client and
; renderer graph, so this pins the bounded source shape instead. The loop
; deletes live BloodSpurt instances; it must capture After B before cleanup so
; simultaneous expirations cannot advance through a deleted iterator node.

Function BloodSpurtCleanupUsesAfterCursor%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "; BloodSpurts can expire together") > 0 Then Stage = 1
		If Stage = 1 And Instr(Line$, "For B.BloodSpurt = Each BloodSpurt") > 0
			CloseFile F
			Return False
		EndIf
		If Stage = 1 And Instr(Line$, "Local B.BloodSpurt = First BloodSpurt") > 0 Then Stage = 2
		If Stage = 2 And Instr(Line$, "Local BNext.BloodSpurt = Null") > 0 Then Stage = 3
		If Stage = 3 And Instr(Line$, "While B <> Null") > 0 Then Stage = 4
		If Stage = 4 And Instr(Line$, "BNext = After B") > 0 Then Stage = 5
		If Stage = 5 And Instr(Line$, "RP_KillEmitter(B\EmitterEN, False, False)") > 0 Then Stage = 6
		If Stage = 6 And Instr(Line$, "Delete(B)") > 0 Then Stage = 7
		If Stage = 7 And Instr(Line$, "B = BNext") > 0
			CloseFile F
			Return True
		EndIf
		If Stage > 0 And Instr(Line$, "End Function") > 0 Then Exit
	Wend
	CloseFile F
	Return False

End Function

Test testBloodSpurtCleanupCapturesNextBeforeDelete()
	Assert(BloodSpurtCleanupUsesAfterCursor%("Modules\ClientCombat.bb") = True)
End Test
