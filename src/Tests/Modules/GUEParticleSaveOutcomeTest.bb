Strict
EnableGC

// GUE's full editor graph is not safe for the standalone test harness. These
// bounded source contracts pin the save-outcome handoff instead.

Function FileContains%(Path$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then F = ReadFile("..\\..\\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, Needle$) > 0
			CloseFile F
			Return True
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Function CountLinesContaining%(Path$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Count% = 0
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then F = ReadFile("..\\..\\" + Path$)
	If F = Null Then Return 0

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, Needle$) > 0 Then Count = Count + 1
	Wend

	CloseFile F
	Return Count
End Function

Function HasParticleSaveAggregate%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Stage% = 0
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then F = ReadFile("..\\..\\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = Trim$(ReadLine$(F))
		Select Stage
			Case 0
				If Line$ = "Function SaveParticleEmitters%()" Then Stage = 1
			Case 1
				If Line$ = "Local SavedAll% = True" Then Stage = 2
			Case 2
				If Left$(Line$, Len("For EmC.RP_EmitterConfig = Each RP_EmitterConfig")) = "For EmC.RP_EmitterConfig = Each RP_EmitterConfig" Then Stage = 3
			Case 3
				If Instr(Line$, "If RP_SaveEmitterConfig") = 1 And Instr(Line$, "= False Then SavedAll = False") > 0 Then Stage = 4
			Case 4
				If Line$ = "Next" Then Stage = 5
			Case 5
				If Line$ = "Return SavedAll" Then
					CloseFile F
					Return True
				EndIf
		End Select
	Wend

	CloseFile F
	Return False
End Function

Test testGUEParticleSavesAggregateEveryEmitterOutcome()
	Assert(HasParticleSaveAggregate%("GUE.bb") = True)
End Test

Test testGUEParticleSaveRoutesKeepDirtyStateOnFailure()
	Local Source$ = "GUE.bb"
	Assert(CountLinesContaining%(Source$, "ParticlesSaved = SaveParticleEmitters()") = 4)
	Assert(CountLinesContaining%(Source$, "RP_SaveEmitterConfig(Handle(EmC)") = 1)
	Assert(FileContains%(Source$, "If ParticlesSaved = False") = True)
	Assert(FileContains%(Source$, "If ParticlesSaved = False Then Result = False") = True)
	Assert(FileContains%(Source$, "If FUI_SendMessage(List, M_GETCAPTION) <> " + Chr$(34) + "Particles" + Chr$(34) + " Or ParticlesSaved = True") = True)
End Test
