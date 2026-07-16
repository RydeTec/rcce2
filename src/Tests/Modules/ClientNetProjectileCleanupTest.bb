Strict
EnableGC

; ClientNet's actor-departure and area-transition paths both delete live
; ProjectileInstance records. A For Each cursor cannot advance safely after
; FreeProjectileInstance deletes its current record, so each bounded section
; must capture After ProjI before the possible delete and advance to it after.

Function ActorGoneProjectileCleanupUsesAfterCursor%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InCase%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Case P_ActorGone") > 0 Then InCase = True
		If InCase = True
			If Instr(Line$, "For ProjI.ProjectileInstance = Each ProjectileInstance") > 0
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "Local ActorProjI.ProjectileInstance = First ProjectileInstance") > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, "Local ActorProjINext.ProjectileInstance = Null") > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, "While ActorProjI <> Null") > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, "ActorProjINext = After ActorProjI") > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, "If ActorProjI\Target = A") > 0 Then Stage = 5
			If Stage = 5 And Instr(Line$, "FreeProjectileInstance(ActorProjI)") > 0 Then Stage = 6
			If Stage = 6 And Instr(Line$, "EndIf") > 0 Then Stage = 7
			If Stage = 7 And Instr(Line$, "ActorProjI = ActorProjINext") > 0 Then Stage = 8
			If Instr(Line$, ";Actor shadows") > 0
				CloseFile F
				Return Stage = 8
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False

End Function

Function AreaTransitionProjectileCleanupUsesAfterCursor%(Path$)

	Local F.BBStream = ReadFile(Path$)
	Local InCleanup%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "; Remove all in-flight projectiles") > 0 Then InCleanup = True
		If InCleanup = True
			If Instr(Line$, "For ProjI.ProjectileInstance = Each ProjectileInstance") > 0
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "Local AreaProjI.ProjectileInstance = First ProjectileInstance") > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, "Local AreaProjINext.ProjectileInstance = Null") > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, "While AreaProjI <> Null") > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, "AreaProjINext = After AreaProjI") > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, "FreeProjectileInstance(AreaProjI)") > 0 Then Stage = 5
			If Stage = 5 And Instr(Line$, "AreaProjI = AreaProjINext") > 0 Then Stage = 6
			If Instr(Line$, "; Save radar state") > 0
				CloseFile F
				Return Stage = 6
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False

End Function

Test testActorGoneProjectileCleanupCapturesNextBeforeDelete()
	Assert(ActorGoneProjectileCleanupUsesAfterCursor%("Modules\ClientNet.bb") = True)
End Test

Test testAreaTransitionProjectileCleanupCapturesNextBeforeDelete()
	Assert(AreaTransitionProjectileCleanupUsesAfterCursor%("Modules\ClientNet.bb") = True)
End Test
