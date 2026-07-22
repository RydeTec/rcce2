Strict
EnableGC

; The updater removes each announced UpdateFile after applying or skipping it.
; A live For Each cursor advances through the deleted object, so a multi-file
; update can lose its successor. Keep the cursor order explicit in source.
Function UpdateFileApplyUsesAfterCursor%()
	Local F.BBStream = ReadFile("Modules\\MainMenu.bb")
	Local Line$
	Local Stage%
	If F = Null Then F = ReadFile("..\\Modules\\MainMenu.bb")
	If F = Null Then F = ReadFile("..\\..\\Modules\\MainMenu.bb")
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Stage = 0 And Instr(Line$, "For U.UpdateFile = Each UpdateFile") > 0
			CloseFile F
			Return False
		EndIf
		If Stage = 0 And Instr(Line$, "CurrentUpdate.UpdateFile = First UpdateFile") > 0 Then Stage = 1
		If Stage = 1 And Instr(Line$, "NextUpdate.UpdateFile = Null") > 0 Then Stage = 2
		If Stage = 2 And Instr(Line$, "While CurrentUpdate <> Null") > 0 Then Stage = 3
		If Stage = 3 And Instr(Line$, "NextUpdate = After CurrentUpdate") > 0 Then Stage = 4
		If Stage < 4 And Instr(Line$, "Delete(CurrentUpdate)") > 0
			CloseFile F
			Return False
		EndIf
		If Stage = 4 And Instr(Line$, "DownloadFile(") > 0 Then Stage = 5
		If Stage = 5 And Instr(Line$, "Delete(CurrentUpdate)") > 0 Then Stage = 6
		If Stage = 6 And Instr(Line$, "CurrentUpdate = NextUpdate") > 0
			CloseFile F
			Return True
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Test testUpdateFileApplyCapturesSuccessorBeforeDelete()
	Assert(UpdateFileApplyUsesAfterCursor%() = True)
End Test
