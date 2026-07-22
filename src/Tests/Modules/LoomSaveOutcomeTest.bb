Strict
EnableGC

// Loom's full UI graph is not safe for the standalone test harness. These
// bounded source contracts pin the persistence outcome handoff instead.

Function FileContains%(Path$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
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

Function HasZoneSaveFailureGuard%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Stage% = 0
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = Trim$(ReadLine$(F))
		Select Stage
			Case 0
				If Line$ = "If kind = " + Chr$(34) + "zone" + Chr$(34) Then Stage = 1
			Case 1
				If Line$ = "Local okZ% = ServerSaveArea(Ar)" Then Stage = 2
			Case 2
				If Line$ = "If okZ = False" Then Stage = 3
			Case 3
				If Line$ = "ZoneSaved = True" Then
					CloseFile F
					Return False
				EndIf
				If Line$ = "Return" Then Stage = 4
			Case 4
				If Line$ = "ZoneSaved = True" Then
					CloseFile F
					Return False
				EndIf
				If Line$ = "EndIf" Then Stage = 5
			Case 5
				If Line$ = "ZoneSaved = True" Then
					CloseFile F
					Return True
				EndIf
		End Select
	Wend

	CloseFile F
	Return False
End Function

Function HasParticleSaveAggregateContract%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Stage% = 0
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = Trim$(ReadLine$(F))
		Select Stage
			Case 0
				If Line$ = "Function Particles_SaveAll%()" Then Stage = 1
			Case 1
				If Line$ = "Local SavedAll% = True" Then Stage = 2
			Case 2
				If Left$(Line$, Len("For C.RP_EmitterConfig = Each RP_EmitterConfig")) = "For C.RP_EmitterConfig = Each RP_EmitterConfig" Then Stage = 3
			Case 3
				If Line$ = "Return True" Or Line$ = "Return False" Then
					CloseFile F
					Return False
				EndIf
				If Instr(Line$, "If RP_SaveEmitterConfig") = 1 And Instr(Line$, "= False Then SavedAll = False") > 0 Then Stage = 4
			Case 4
				If Line$ = "Next" Then Stage = 5
			Case 5
				If Line$ = "If SavedAll = False Then Return False" Then Stage = 6
			Case 6
				If Line$ = "Emitters_Rebuild()" Then Stage = 7
			Case 7
				If Line$ = "Return True" Then
					CloseFile F
					Return True
				EndIf
		End Select
	Wend

	CloseFile F
	Return False
End Function

Function HasParticleSaveFailureGuard%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Stage% = 0
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = Trim$(ReadLine$(F))
		Select Stage
			Case 0
				If Line$ = "If kind = " + Chr$(34) + "particle" + Chr$(34) Then Stage = 1
			Case 1
				If Line$ = "Local okParticleSave% = Particles_SaveAll()" Then Stage = 2
			Case 2
				If Line$ = "If okParticleSave = False" Then Stage = 3
			Case 3
				If Line$ = "ParticlesSaved = True" Then
					CloseFile F
					Return False
				EndIf
				If Line$ = "ParticlesSaved = False" Then Stage = 4
			Case 4
				If Line$ = "ParticlesSaved = True" Then
					CloseFile F
					Return False
				EndIf
				If Line$ = "Return" Then Stage = 5
			Case 5
				If Line$ = "ParticlesSaved = True" Then
					CloseFile F
					Return False
				EndIf
				If Line$ = "EndIf" Then Stage = 6
			Case 6
				If Line$ = "ParticlesSaved = True" Then
					CloseFile F
					Return True
				EndIf
		End Select
	Wend

	CloseFile F
	Return False
End Function

Function HasExitSaveFailureGuard%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Stage% = 0
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = Trim$(ReadLine$(F))
		Select Stage
			Case 0
				If Line$ = "If action = " + Chr$(34) + "save" + Chr$(34) Then Stage = 1
			Case 1
				If Line$ = "If SaveAll_Persist(self\composer) = True" Then Stage = 2
			Case 2
				If Line$ = "self\exitConfirmed = True" Then Stage = 3
			Case 3
				If Line$ = "ExitPrompt::closeModal(self)" Then
					CloseFile F
					Return True
				EndIf
		End Select
	Wend

	CloseFile F
	Return False
End Function

Test testZoneSaveFailureKeepsTheZoneDirty()
	Local Source$ = "Modules\Loom\Composer.bb"
	Assert(HasZoneSaveFailureGuard%(Source$) = True)
	Assert(FileContains%(Source$, "Save Zone FAILED") = True)
End Test

Test testParticleSaveFailureKeepsParticlesDirty()
	Local EditorSource$ = "Modules\Loom\ParticleEditor.bb"
	Local ComposerSource$ = "Modules\Loom\Composer.bb"
	Assert(FileContains%(EditorSource$, "Local SavedAll% = True") = True)
	Assert(FileContains%(EditorSource$, "If SavedAll = False Then Return False") = True)
	Assert(HasParticleSaveAggregateContract%(EditorSource$) = True)
	Assert(HasParticleSaveFailureGuard%(ComposerSource$) = True)
	Assert(FileContains%(ComposerSource$, "Save emitter configs FAILED") = True)
End Test

Test testSaveAllCountsOnlyConfirmedSaves()
	Local Source$ = "Modules\Loom\SaveAll.bb"
	Assert(FileContains%(Source$, "Function SaveAll_Persist%(composer.Composer)") = True)
	Assert(FileContains%(Source$, "If ActorsSaved = True Then count = count + 1") = True)
	Assert(FileContains%(Source$, "If ItemsSaved = True Then count = count + 1") = True)
	Assert(FileContains%(Source$, "If SpellsSaved = True Then count = count + 1") = True)
	Assert(FileContains%(Source$, "If FactionsSaved = True Then count = count + 1") = True)
	Assert(FileContains%(Source$, "If AnimsSaved = True Then count = count + 1") = True)
	Assert(FileContains%(Source$, "If ProjectilesSaved = True Then count = count + 1") = True)
	Assert(FileContains%(Source$, "If ParticlesSaved = True Then count = count + 1") = True)
	Assert(FileContains%(Source$, "If ZoneSaved = True Then count = count + 1") = True)
	Assert(FileContains%(Source$, "If SettingsSaved = True Then count = count + 1") = True)
	Assert(FileContains%(Source$, "If EnvironmentSaved = True Then count = count + 1") = True)
	Assert(FileContains%(Source$, "If InterfaceSaved = True Then count = count + 1") = True)
	Assert(FileContains%(Source$, "Save All: persistence failed") = True)
End Test

Test testExitSaveAllStaysOpenWhenPersistenceFails()
	Assert(HasExitSaveFailureGuard%("Modules\Loom\SaveAll.bb") = True)
End Test
