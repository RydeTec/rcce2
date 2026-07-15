Strict
EnableGC

; Interface.bb pulls in the client UI graph, so this bounded source contract
; protects the on-disk Controls.dat handoff without loading renderer state.
; The persisted sequence is intentionally pinned: startup treats a missing
; control-binding file as fatal, so a remap must never truncate it in place.

Function SaveControlBindingsUsesAtomicSequence%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InFunction%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function SaveControlBindings(Filename$)") > 0 Then InFunction = True
		If InFunction = True
			If Instr(Line$, "WriteFile(Filename$)") > 0
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "Local Temp$ = SafeWriteOpen$(Filename$)") > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, "F = WriteFile(Temp$)") > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, "If F = 0 Then Return False") > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, "WriteInt(F, Key_Forward)") > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, "WriteInt(F, Key_Back)") > 0 Then Stage = 5
			If Stage = 5 And Instr(Line$, "WriteInt(F, Key_TurnRight)") > 0 Then Stage = 6
			If Stage = 6 And Instr(Line$, "WriteInt(F, Key_TurnLeft)") > 0 Then Stage = 7
			If Stage = 7 And Instr(Line$, "WriteInt(F, Key_FlyUp)") > 0 Then Stage = 8
			If Stage = 8 And Instr(Line$, "WriteInt(F, Key_FlyDown)") > 0 Then Stage = 9
			If Stage = 9 And Instr(Line$, "WriteInt(F, Key_Run)") > 0 Then Stage = 10
			If Stage = 10 And Instr(Line$, "WriteInt(F, Key_ChangeViewMode)") > 0 Then Stage = 11
			If Stage = 11 And Instr(Line$, "WriteInt(F, Key_CameraRight)") > 0 Then Stage = 12
			If Stage = 12 And Instr(Line$, "WriteInt(F, Key_CameraLeft)") > 0 Then Stage = 13
			If Stage = 13 And Instr(Line$, "WriteInt(F, Key_CameraIn)") > 0 Then Stage = 14
			If Stage = 14 And Instr(Line$, "WriteInt(F, Key_CameraOut)") > 0 Then Stage = 15
			If Stage = 15 And Instr(Line$, "WriteInt(F, Key_Jump)") > 0 Then Stage = 16
			If Stage = 16 And Instr(Line$, "WriteByte(F, InvertAxis1 + 1)") > 0 Then Stage = 17
			If Stage = 17 And Instr(Line$, "WriteByte(F, InvertAxis3 + 1)") > 0 Then Stage = 18
			If Stage = 18 And Instr(Line$, "WriteInt(F, Key_Attack)") > 0 Then Stage = 19
			If Stage = 19 And Instr(Line$, "WriteInt(F, Key_AlwaysRun)") > 0 Then Stage = 20
			If Stage = 20 And Instr(Line$, "WriteInt(F, Key_CycleTarget)") > 0 Then Stage = 21
			If Stage = 21 And Instr(Line$, "WriteInt(F, Key_MoveTo)") > 0 Then Stage = 22
			If Stage = 22 And Instr(Line$, "WriteInt(F, Key_TalkTo)") > 0 Then Stage = 23
			If Stage = 23 And Instr(Line$, "WriteInt(F, Key_Select)") > 0 Then Stage = 24
			If Stage = 24 And Instr(Line$, "Return SafeWriteCommit%(Temp$, Filename$, F)") > 0
				CloseFile F
				Return True
			EndIf
			If Instr(Line$, "End Function") > 0 Then Exit
		EndIf
	Wend

	CloseFile F
	Return False

End Function

Test testControlBindingsSaveKeepsThePayloadAndCommitsAtomically()
	Assert(SaveControlBindingsUsesAtomicSequence%("Modules\Interface.bb") = True)
End Test
