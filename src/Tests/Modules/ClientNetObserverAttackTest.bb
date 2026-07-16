Strict
EnableGC

; P_AttackActor's observer frame carries only two RuntimeIDs. This bounded
; source contract prevents the legacy fallback from reusing Damage left by an
; earlier H/Y frame to alter an observer's remote combat state.

Function ObserverAttackIsAnimationOnly%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InObserver%, SawAttackerAnimation%, SawAttackerSound%, SawFacing%, SawRotation%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "; Someone else attacked someone else") > 0 Then InObserver = True
		If InObserver = True And Instr(Line$, "If A = CharInteract Then") > 0 Then Exit
		If InObserver = True
			If Instr(Line$, "Damage") > 0
				CloseFile F
				Return False
			EndIf
			If Instr(Line$, "A2\Attributes\Value[HealthStat]") > 0
				CloseFile F
				Return False
			EndIf
			If Instr(Line$, "PlayAnimation(A2, 3, 0.035, Rand(Anim_FirstHit, Anim_LastHit))") > 0
				CloseFile F
				Return False
			EndIf
			If Instr(Line$, "PlayActorSound(A2, Rand(Speech_Hit1, Speech_Hit2))") > 0
				CloseFile F
				Return False
			EndIf
			If Instr(Line$, "AnimateActorParry(A2)") > 0
				CloseFile F
				Return False
			EndIf
			If Instr(Line$, "B.BloodSpurt = New BloodSpurt") > 0
				CloseFile F
				Return False
			EndIf
			If Instr(Line$, "AnimateActorAttack(A)") > 0 Then SawAttackerAnimation = True
			If Instr(Line$, "PlayActorSound(A, Rand(Speech_Attack1, Speech_Attack2))") > 0 Then SawAttackerSound = True
			If Instr(Line$, "PointEntity A\CollisionEN, A2\CollisionEN") > 0 Then SawFacing = True
			If Instr(Line$, "RotateEntity A\CollisionEN, 0.0, EntityYaw#(A\CollisionEN) + 180.0, 0.0") > 0 Then SawRotation = True
		EndIf
	Wend

	CloseFile F
	Return SawAttackerAnimation And SawAttackerSound And SawFacing And SawRotation
End Function

Test testObserverAttackDoesNotReuseStaleDamage()
	Assert(ObserverAttackIsAnimationOnly%("Modules\ClientNet.bb") = True)
End Test
