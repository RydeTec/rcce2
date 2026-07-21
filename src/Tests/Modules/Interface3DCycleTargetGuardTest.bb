Strict
EnableGC

; UpdateInterface needs the interactive graphics runtime, so protect the
; empty actor-list boundary with a bounded source contract instead.

Function HasCycleTargetEmptyListGuard%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Stage = 0
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = Trim$(ReadLine$(F))
		Select Stage
			Case 0
				If Line$ = "If ControlHit(Key_CycleTarget)" Then Stage = 1
			Case 1
				If Line$ = "StartAI.ActorInstance = Object.ActorInstance(PlayerTarget)" Then Stage = 2
			Case 2
				If Line$ = "If StartAI = Null" Then Stage = 3
			Case 3
				If Line$ = "PlayerTarget = 0" Then Stage = 4
			Case 4
				If Line$ = "StartAI = First ActorInstance" Then Stage = 5
			Case 5
				If Line$ = "If StartAI = Null Then Return" Then Stage = 6
			Case 6
				If Line$ = "AI.ActorInstance = StartAI" Then Stage = 7
			Case 7
				If Line$ = "AI = After AI" Then
					CloseFile F
					Return True
				EndIf
		End Select
		If Stage < 7 And (Line$ = "AI = After AI" Or Instr(Line$, "AI\Actor\Aggressiveness") > 0)
			CloseFile F
			Return False
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Test testCycleTargetClearsStaleTargetBeforeEmptyActorTraversal()
	Assert(HasCycleTargetEmptyListGuard%("src\Modules\Interface3D.bb") = True)
End Test
