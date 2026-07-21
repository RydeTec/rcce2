Strict
EnableGC

; GUE consumes F-UI Event objects in several modal loops. The current event must
; save its successor before Delete so a burst of queued events is fully drained.

Function GUEEventDrainUsesAfterCursor%(Path$, FunctionMarker$, LegacyFor$, FirstCursor$, NextDeclaration$, WhileCursor$, Capture$, DeleteEvent$, Advance$)
	Local F.BBStream = ReadFile(Path$)
	Local Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then F = ReadFile("..\\..\\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Stage = 0 And Instr(Line$, FunctionMarker$) > 0 Then Stage = 1
		If Stage > 0 And Instr(Line$, LegacyFor$) > 0 Then
			CloseFile F
			Return False
		EndIf
		If Stage > 0 And Instr(Line$, "E\\Event") > 0 Then
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

; The editor's top-level queue spans the main loop rather than a bounded modal
; function. Keep its current Event cursor, but require its successor to be
; captured before the dispatch can delete the current object.
Function GUEMainQueueUsesAfterCursor%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then F = ReadFile("..\\..\\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Stage > 0 And Instr(Line$, "For E.Event = Each Event") > 0
			CloseFile F
			Return False
		EndIf
		If Stage > 0 And Stage < 5 And Instr(Line$, "Delete E") > 0
			CloseFile F
			Return False
		EndIf
		If Stage = 0
			If Instr(Line$, "; Process events") > 0 Then Stage = 1
		ElseIf Stage = 1
			If Instr(Line$, "Local E.Event") > 0 Then Stage = 2
		ElseIf Stage = 2
			If Instr(Line$, "Local ENext.Event") > 0 Then Stage = 3
		ElseIf Stage = 3
			If Instr(Line$, "E = First Event") > 0 Then Stage = 4
		ElseIf Stage = 4
			If Instr(Line$, "While E <> Null") > 0 Then Stage = 5
		ElseIf Stage = 5
			If Instr(Line$, "ENext = After E") > 0 Then Stage = 6
		ElseIf Stage = 6
			If Instr(Line$, "Delete E") > 0 Then Stage = 7
		ElseIf Stage = 7
			If Instr(Line$, "E = ENext") > 0
				CloseFile F
				Return True
			EndIf
		EndIf
		If Stage > 0 And Instr(Line$, "RenderWorld") > 0 Then Exit
	Wend

	CloseFile F
	Return False
End Function

Test testScaleEntireZoneDialogCapturesNextEventBeforeDelete()
	Assert(GUEEventDrainUsesAfterCursor%("GUE.bb", "Function ScaleEntireZoneDialog()", "For E.Event = Each Event", "Local ScaleEvent.Event = First Event", "Local NextScaleEvent.Event = Null", "While ScaleEvent <> Null", "NextScaleEvent = After ScaleEvent", "Delete(ScaleEvent)", "ScaleEvent = NextScaleEvent") = True)
End Test

Test testGenerateGamePatchCapturesNextEventBeforeDelete()
	Assert(GUEEventDrainUsesAfterCursor%("GUE.bb", "Function GenerateGamePatch()", "For E.Event = Each Event", "Local QuitEvent.Event = First Event", "Local NextQuitEvent.Event = Null", "While QuitEvent <> Null", "NextQuitEvent = After QuitEvent", "Delete(QuitEvent)", "QuitEvent = NextQuitEvent") = True)
End Test

Test testPreciseEditSelectedCapturesNextEventBeforeDelete()
	Assert(GUEEventDrainUsesAfterCursor%("GUE.bb", "Function PreciseEditSelected()", "For E.Event = Each Event", "Local PreciseEvent.Event = First Event", "Local NextPreciseEvent.Event = Null", "While PreciseEvent <> Null", "NextPreciseEvent = After PreciseEvent", "Delete(PreciseEvent)", "PreciseEvent = NextPreciseEvent") = True)
End Test

Test testEmitterNameDialogCapturesNextEventBeforeDelete()
	Assert(GUEEventDrainUsesAfterCursor%("GUE.bb", "Function EmitterNameDialog$()", "For E.Event = Each Event", "Local EmitterEvent.Event = First Event", "Local NextEmitterEvent.Event = Null", "While EmitterEvent <> Null", "NextEmitterEvent = After EmitterEvent", "Delete(EmitterEvent)", "EmitterEvent = NextEmitterEvent") = True)
End Test

Test testFixedAttributeDialogCapturesNextEventBeforeDelete()
	Assert(GUEEventDrainUsesAfterCursor%("GUE.bb", "Function FixedAttributeDialog()", "For E.Event = Each Event", "Local FixedAttributeEvent.Event = First Event", "Local NextFixedAttributeEvent.Event = Null", "While FixedAttributeEvent <> Null", "NextFixedAttributeEvent = After FixedAttributeEvent", "Delete(FixedAttributeEvent)", "FixedAttributeEvent = NextFixedAttributeEvent") = True)
End Test

Test testSaveDialogCapturesNextEventBeforeDelete()
	Assert(GUEEventDrainUsesAfterCursor%("GUE.bb", "Function SaveDialog()", "For E.Event = Each Event", "Local SaveEvent.Event = First Event", "Local NextSaveEvent.Event = Null", "While SaveEvent <> Null", "NextSaveEvent = After SaveEvent", "Delete(SaveEvent)", "SaveEvent = NextSaveEvent") = True)
End Test

Test testAreaNameDialogCapturesNextEventBeforeDelete()
	Assert(GUEEventDrainUsesAfterCursor%("GUE.bb", "Function AreaNameDialog$()", "For E.Event = Each Event", "Local AreaNameEvent.Event = First Event", "Local NextAreaNameEvent.Event = Null", "While AreaNameEvent <> Null", "NextAreaNameEvent = After AreaNameEvent", "Delete(AreaNameEvent)", "AreaNameEvent = NextAreaNameEvent") = True)
End Test

Test testMeshDialogCapturesNextEventBeforeDelete()
	Assert(GUEEventDrainUsesAfterCursor%("GUE.bb", "Function MeshDialog()", "For E.Event = Each Event", "Local MeshEvent.Event = First Event", "Local NextMeshEvent.Event = Null", "While MeshEvent <> Null", "NextMeshEvent = After MeshEvent", "Delete MeshEvent", "MeshEvent = NextMeshEvent") = True)
End Test

Test testTextureDialogCapturesNextEventBeforeDelete()
	Assert(GUEEventDrainUsesAfterCursor%("GUE.bb", "Function TextureDialog()", "For E.Event = Each Event", "Local TextureEvent.Event = First Event", "Local NextTextureEvent.Event = Null", "While TextureEvent <> Null", "NextTextureEvent = After TextureEvent", "Delete TextureEvent", "TextureEvent = NextTextureEvent") = True)
End Test

Test testSoundDialogCapturesNextEventBeforeDelete()
	Assert(GUEEventDrainUsesAfterCursor%("GUE.bb", "Function SoundDialog()", "For E.Event = Each Event", "Local SoundEvent.Event = First Event", "Local NextSoundEvent.Event = Null", "While SoundEvent <> Null", "NextSoundEvent = After SoundEvent", "Delete SoundEvent", "SoundEvent = NextSoundEvent") = True)
End Test

Test testMainQueueCapturesNextEventBeforeDelete()
	Assert(GUEMainQueueUsesAfterCursor%("GUE.bb") = True)
End Test
