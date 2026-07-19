Strict
EnableGC

; MediaDialogs owns four modal loops that delete each handled F-UI Event. Each
; loop must capture the successor first so a queued follow-up action is not
; skipped after the current Event is deleted.
Function MediaDialogsEventDrainUsesAfterCursor%(FunctionMarker$, LegacyFor$, FirstCursor$, NextDeclaration$, WhileCursor$, Capture$, DeleteEvent$, Advance$)
	Local F.BBStream = ReadFile("Modules\\MediaDialogs.bb")
	Local Line$
	Local Stage%
	If F = Null Then F = ReadFile("..\\Modules\\MediaDialogs.bb")
	If F = Null Then F = ReadFile("..\\..\\Modules\\MediaDialogs.bb")
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Stage = 0 And Instr(Line$, FunctionMarker$) > 0 Then Stage = 1
		If Stage > 0 And Instr(Line$, LegacyFor$) > 0 Then
			CloseFile F
			Return False
		EndIf
		If Stage = 1 And Instr(Line$, FirstCursor$) > 0 Then Stage = 2
		If Stage = 2 And Instr(Line$, NextDeclaration$) > 0 Then Stage = 3
		If Stage = 3 And Instr(Line$, WhileCursor$) > 0 Then Stage = 4
		If Stage = 4 And Instr(Line$, Capture$) > 0 Then Stage = 5
		If Stage < 5 And Instr(Line$, DeleteEvent$) > 0 Then
			CloseFile F
			Return False
		EndIf
		If Stage = 5 And Instr(Line$, DeleteEvent$) > 0 Then Stage = 6
		If Stage = 6 And Instr(Line$, Advance$) > 0 Then
			CloseFile F
			Return True
		EndIf
		If Stage > 0 And Instr(Line$, "End Function") > 0 Then Exit
	Wend

	CloseFile F
	Return False
End Function

Test testChooseMeshDialogCapturesNextEventBeforeDelete()
	Assert(MediaDialogsEventDrainUsesAfterCursor%("Function ChooseMeshDialog", "For E.Event = Each Event", "Local MeshDialogEvent.Event = First Event", "Local NextMeshDialogEvent.Event = Null", "While MeshDialogEvent <> Null", "NextMeshDialogEvent = After MeshDialogEvent", "Delete MeshDialogEvent", "MeshDialogEvent = NextMeshDialogEvent") = True)
End Test

Test testChooseTextureDialogCapturesNextEventBeforeDelete()
	Assert(MediaDialogsEventDrainUsesAfterCursor%("Function ChooseTextureDialog", "For E.Event = Each Event", "Local TextureDialogEvent.Event = First Event", "Local NextTextureDialogEvent.Event = Null", "While TextureDialogEvent <> Null", "NextTextureDialogEvent = After TextureDialogEvent", "Delete TextureDialogEvent", "TextureDialogEvent = NextTextureDialogEvent") = True)
End Test

Test testChooseSoundDialogCapturesNextEventBeforeDelete()
	Assert(MediaDialogsEventDrainUsesAfterCursor%("Function ChooseSoundDialog", "For E.Event = Each Event", "Local SoundDialogEvent.Event = First Event", "Local NextSoundDialogEvent.Event = Null", "While SoundDialogEvent <> Null", "NextSoundDialogEvent = After SoundDialogEvent", "Delete SoundDialogEvent", "SoundDialogEvent = NextSoundDialogEvent") = True)
End Test

Test testChooseMusicDialogCapturesNextEventBeforeDelete()
	Assert(MediaDialogsEventDrainUsesAfterCursor%("Function ChooseMusicDialog", "For E.Event = Each Event", "Local MusicDialogEvent.Event = First Event", "Local NextMusicDialogEvent.Event = Null", "While MusicDialogEvent <> Null", "NextMusicDialogEvent = After MusicDialogEvent", "Delete MusicDialogEvent", "MusicDialogEvent = NextMusicDialogEvent") = True)
End Test
