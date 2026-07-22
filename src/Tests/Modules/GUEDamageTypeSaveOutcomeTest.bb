Strict
EnableGC

// GUE's full editor graph is not safe for the standalone test harness. These
// bounded source contracts pin the Damage.dat save-outcome handoff instead.

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

Function HasDamageSaveHelper%(Path$)
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
				If Line$ = "Function SaveDamageTypes%()" Then Stage = 1
			Case 1
				If Line$ = "Local DamageFinal$ = " + Chr$(34) + "Data\\Server Data\\Damage.dat" + Chr$(34) Then Stage = 2
			Case 2
				If Line$ = "Local DamageTemp$ = SafeWriteOpen(DamageFinal$)" Then Stage = 3
			Case 3
				If Line$ = "F = WriteFile(DamageTemp$)" Then Stage = 4
			Case 4
				If Line$ = "If F = 0 Then Return False" Then Stage = 5
			Case 5
				If Line$ = "For i = 0 To 19" Then Stage = 6
			Case 6
				If Line$ = "WriteString F, DamageTypes$(i)" Then Stage = 7
			Case 7
				If Line$ = "Next" Then Stage = 8
			Case 8
				If Line$ = "Return SafeWriteCommit(DamageTemp$, DamageFinal$, F)" Then
					CloseFile F
					Return True
				EndIf
		End Select
	Wend

	CloseFile F
	Return False
End Function

Test testGUEDamageTypeSaveUsesOneOutcomeHelper()
	Local Source$ = "GUE.bb"
	Assert(HasDamageSaveHelper%(Source$) = True)
	Assert(CountLinesContaining%(Source$, "WriteString F, DamageTypes$(i)") = 1)
	Assert(CountLinesContaining%(Source$, "SafeWriteCommit(DamageTemp$, DamageFinal$, F)") = 1)
End Test

Test testGUEDamageTypeSaveRoutesKeepDirtyStateOnFailure()
	Local Source$ = "GUE.bb"
	Assert(CountLinesContaining%(Source$, "DamageTypesSaved = SaveDamageTypes()") = 4)
	Assert(FileContains%(Source$, "If FUI_SendMessage(List, M_GETCAPTION) <> " + Chr$(34) + "Damage types" + Chr$(34) + " Or DamageTypesSaved = True") = True)
	Assert(FileContains%(Source$, "If ParticlesSaved = False Or DamageTypesSaved = False Then Result = False") = True)
	Assert(FileContains%(Source$, "If ParticlesSaved = True And DamageTypesSaved = True Then Result = True") = True)
End Test
