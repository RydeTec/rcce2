Strict
EnableGC

; RottParticles is renderer-heavy, so this bounded source contract protects
; hard emitter teardown without loading its graphics dependency graph.

Function RottParticlesFreeEmitterUsesAfterCursor%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InFunction%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function RP_FreeEmitter(ID, FreeConfig = False, FreeTex = False)") > 0 Then InFunction = True
		If InFunction = True
			If Instr(Line$, "For P.RP_Particle = Each RP_Particle") > 0
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "Local P.RP_Particle = First RP_Particle") > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, "Local PNext.RP_Particle = Null") > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, "While P <> Null") > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, "PNext = After P") > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, "If P\E = E Then Delete P") > 0 Then Stage = 5
			If Stage = 5 And Instr(Line$, Chr$(9) + Chr$(9) + Chr$(9) + "P = PNext") = 1 Then Stage = 6
			If Stage = 6 And Instr(Line$, "Wend") > 0 Then Stage = 7
			If Instr(Line$, "End Function") > 0
				CloseFile F
				Return Stage = 7
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False

End Function

Test testFreeEmitterDeletesOwnedParticlesWithAfterCursor()
	Assert(RottParticlesFreeEmitterUsesAfterCursor%("Modules\RottParticles.bb") = True)
End Test
