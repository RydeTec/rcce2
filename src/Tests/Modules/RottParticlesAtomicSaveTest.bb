Strict
EnableGC

; RottParticles is renderer-heavy, so this bounded source contract protects
; the .rpc persistence handoff without loading the graphics dependency graph.

Function RottParticlesSaveUsesSafeWrite%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InFunction%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function RP_SaveEmitterConfig(ID, File$)") > 0 Then InFunction = True
		If InFunction = True
			If Instr(Line$, "WriteFile(File$)") > 0
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "Local Temp$ = SafeWriteOpen$(File$)") > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, "F = WriteFile(Temp$)") > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, "If F = 0 Then Return False") > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, "WriteInt F, C\MaxParticles") > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, "WriteInt F, C\ParticlesPerFrame") > 0 Then Stage = 5
			If Stage = 5 And Instr(Line$, "WriteInt F, C\TexAcross") > 0 Then Stage = 6
			If Stage = 6 And Instr(Line$, "WriteInt F, C\TexDown") > 0 Then Stage = 7
			If Stage = 7 And Instr(Line$, "WriteInt F, C\RndStartFrame") > 0 Then Stage = 8
			If Stage = 8 And Instr(Line$, "WriteInt F, C\TexAnimSpeed") > 0 Then Stage = 9
			If Stage = 9 And Instr(Line$, "WriteInt F, C\VShapeBased") > 0 Then Stage = 10
			If Stage = 10 And Instr(Line$, "WriteFloat F, C\VelocityX#") > 0 Then Stage = 11
			If Stage = 11 And Instr(Line$, "WriteFloat F, C\VelocityY#") > 0 Then Stage = 12
			If Stage = 12 And Instr(Line$, "WriteFloat F, C\VelocityZ#") > 0 Then Stage = 13
			If Stage = 13 And Instr(Line$, "WriteFloat F, C\VelocityRndX#") > 0 Then Stage = 14
			If Stage = 14 And Instr(Line$, "WriteFloat F, C\VelocityRndY#") > 0 Then Stage = 15
			If Stage = 15 And Instr(Line$, "WriteFloat F, C\VelocityRndZ#") > 0 Then Stage = 16
			If Stage = 16 And Instr(Line$, "WriteFloat F, C\ForceX#") > 0 Then Stage = 17
			If Stage = 17 And Instr(Line$, "WriteFloat F, C\ForceY#") > 0 Then Stage = 18
			If Stage = 18 And Instr(Line$, "WriteFloat F, C\ForceZ#") > 0 Then Stage = 19
			If Stage = 19 And Instr(Line$, "WriteFloat F, C\ScaleStart#") > 0 Then Stage = 20
			If Stage = 20 And Instr(Line$, "WriteFloat F, C\ScaleChange#") > 0 Then Stage = 21
			If Stage = 21 And Instr(Line$, "WriteInt F, C\Lifespan") > 0 Then Stage = 22
			If Stage = 22 And Instr(Line$, "WriteFloat F, C\AlphaStart#") > 0 Then Stage = 23
			If Stage = 23 And Instr(Line$, "WriteFloat F, C\AlphaChange#") > 0 Then Stage = 24
			If Stage = 24 And Instr(Line$, "WriteInt F, C\BlendMode") > 0 Then Stage = 25
			If Stage = 25 And Instr(Line$, "WriteInt F, C\Shape") > 0 Then Stage = 26
			If Stage = 26 And Instr(Line$, "WriteFloat F, C\MinRadius#") > 0 Then Stage = 27
			If Stage = 27 And Instr(Line$, "WriteFloat F, C\MaxRadius#") > 0 Then Stage = 28
			If Stage = 28 And Instr(Line$, "WriteFloat F, C\Width#") > 0 Then Stage = 29
			If Stage = 29 And Instr(Line$, "WriteFloat F, C\Height#") > 0 Then Stage = 30
			If Stage = 30 And Instr(Line$, "WriteFloat F, C\Depth#") > 0 Then Stage = 31
			If Stage = 31 And Instr(Line$, "WriteInt F, C\ShapeAxis") > 0 Then Stage = 32
			If Stage = 32 And Instr(Line$, "WriteShort F, C\DefaultTextureID") > 0 Then Stage = 33
			If Stage = 33 And Instr(Line$, "WriteFloat F, C\ForceModX#") > 0 Then Stage = 34
			If Stage = 34 And Instr(Line$, "WriteFloat F, C\ForceModY#") > 0 Then Stage = 35
			If Stage = 35 And Instr(Line$, "WriteFloat F, C\ForceModZ#") > 0 Then Stage = 36
			If Stage = 36 And Instr(Line$, "WriteInt F, C\ForceShaping") > 0 Then Stage = 37
			If Stage = 37 And Instr(Line$, "WriteByte F, C\RStart") > 0 Then Stage = 38
			If Stage = 38 And Instr(Line$, "WriteByte F, C\GStart") > 0 Then Stage = 39
			If Stage = 39 And Instr(Line$, "WriteByte F, C\BStart") > 0 Then Stage = 40
			If Stage = 40 And Instr(Line$, "WriteFloat F, C\RChange#") > 0 Then Stage = 41
			If Stage = 41 And Instr(Line$, "WriteFloat F, C\GChange#") > 0 Then Stage = 42
			If Stage = 42 And Instr(Line$, "WriteFloat F, C\BChange#") > 0 Then Stage = 43
			If Stage = 43 And Instr(Line$, "Return SafeWriteCommit%(Temp$, File$, F)") > 0
					CloseFile F
					Return True
				EndIf
			If Instr(Line$, "End Function") > 0 Then Exit
		EndIf
	Wend

	CloseFile F
	Return False

End Function

Test testEmitterConfigSaveKeepsAllFieldsAndUsesSafeWrite()
	Assert(RottParticlesSaveUsesSafeWrite%("Modules\RottParticles.bb") = True)
End Test
