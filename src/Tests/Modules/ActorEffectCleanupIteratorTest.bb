Strict
EnableGC

; CleanActorEffects delegates deletion to DestroyActorEffect. The production
; function must capture After before that call, otherwise a For Each cursor can
; advance through a freed ActorEffect and skip later cleanup records.
Function CleanActorEffectsUsesAfterCursor%()
	Local F.BBStream = ReadFile("Modules\\Actors.bb")
	Local InFunction%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\\Modules\\Actors.bb")
	If F = Null Then F = ReadFile("..\\..\\Modules\\Actors.bb")
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If InFunction = False And Instr(Line$, "Function CleanActorEffects()") > 0 Then InFunction = True
		If InFunction = True
			If Instr(Line$, "For AE = Each ActorEffect") > 0 Or Instr(Line$, "Delete Each ActorEffect") > 0
				CloseFile F
				Return False
			EndIf
			If Stage < 4 And Instr(Line$, "DestroyActorEffect( AE )") > 0
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "Local AE.ActorEffect = First ActorEffect") > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, "Local AENext.ActorEffect = Null") > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, "While AE <> Null") > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, "AENext = After AE") > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, "DestroyActorEffect( AE )") > 0 Then Stage = 5
			If Stage = 5 And Instr(Line$, "AE = AENext") > 0 Then Stage = 6
			If Instr(Line$, "End Function") > 0
				CloseFile F
				Return Stage = 6
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Test testCleanActorEffectsCapturesSuccessorBeforeDestroy()
	Assert(CleanActorEffectsUsesAfterCursor%() = True)
End Test
