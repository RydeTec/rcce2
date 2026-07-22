Strict
EnableGC

; P_Projectile carries a 13-byte fixed header followed by an optional first
; emitter name and an unbounded second name. Bind both receive bounds before
; any decoded value can select actors or allocate a projectile visual.

Function ProjectilePayloadGuards%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InHandler%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Case P_Projectile") > 0 Then InHandler = True
		If InHandler = True
			If Stage = 0 And (Instr(Line$, "RCE_IntFromStr") > 0 Or Instr(Line$, "RuntimeIDList") > 0 Or Instr(Line$, "CreateProjectile") > 0)
				CloseFile F
				Return False
			EndIf
			If Stage < 4 And (Instr(Line$, "Emitter1$ =") > 0 Or Instr(Line$, "Emitter2$ =") > 0 Or Instr(Line$, "CreateProjectile") > 0)
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "If Len(M\MessageData$) < 13") > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, "RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 1, 2))") > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, "NameLen = RCE_IntFromStr(Mid$(M\MessageData$, 13, 1))") > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, "If Len(M\MessageData$) < 13 + NameLen") > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, "Emitter1$ = " + Chr$(34) + Chr$(34)) > 0 Then Stage = 5
			If Stage = 5 And Instr(Line$, "If NameLen > 0 Then Emitter1$ = Mid$(M\MessageData$, 14, NameLen)") > 0 Then Stage = 6
			If Stage = 6 And Instr(Line$, "Emitter2$ = Mid$(M\MessageData$, 14 + NameLen)") > 0 Then Stage = 7
			If Stage = 7 And Instr(Line$, "CreateProjectile(AI, TargetAI, MeshID, Homing, Speed#, Emitter1$, Emitter2$, TexID1, TexID2)") > 0 Then Stage = 8
			If Instr(Line$, "Case P_Jump") > 0
				CloseFile F
				Return Stage = 8
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False

End Function

Test testProjectileRejectsTruncatedHeaderAndEmitterBeforeAllocation()
	Assert(ProjectilePayloadGuards%("Modules\ClientNet.bb") = True)
End Test
