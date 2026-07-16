Strict
EnableGC

; GUE owns the editor-side Water object, while ServerAreas owns the
; authoritative per-Area ServerWater chain used by saving and unloading. This
; focused source contract keeps all three GUE lifecycle paths bound to that
; shared contract without loading the graphical editor into the test runner.

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

Function CountFileMatches%(Path$, Needle$)

	Local F.BBStream = ReadFile(Path$)
	Local Line$, Matches%
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return 0

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, Needle$) > 0 Then Matches = Matches + 1
	Wend

	CloseFile F
	Return Matches

End Function

Function WaterDeleteDetachesBeforeDelete%(Path$)

	Local F.BBStream = ReadFile(Path$)
	Local Line$, Stage%
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = Trim$(ReadLine$(F))
		If Line$ = "Function ZoneDeleteEntity(EN, NoUndo = False)" Then Stage = 1
		If Stage = 1 And Line$ = "ElseIf W <> Null" Then Stage = 2
		If Stage = 2 And Line$ = "ServerWaterDetach(SW)" Then Stage = 3
		If Stage = 3 And Line$ = "Delete(SW)"
			CloseFile F
			Return True
		EndIf
		If Stage > 1 And Line$ = "ElseIf CB <> Null" Then Exit
	Wend

	CloseFile F
	Return False

End Function

Function ServerSaveAreaWalksWaterChain%(Path$)

	Local F.BBStream = ReadFile(Path$)
	Local InSave%, Line$, Stage%
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = Trim$(ReadLine$(F))
		If Line$ = "Function ServerSaveArea(A.Area)" Then InSave = True
		If InSave = True
			If Stage = 0 And Line$ = "W = A\FirstWater" Then Stage = 1
			If Stage = 1 And Line$ = "While W <> Null" Then Stage = 2
			If Stage = 2 And Line$ = "W = W\NextWater" Then Stage = 3
			If Line$ = "End Function"
				CloseFile F
				Return Stage = 3
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False

End Function

Test testServerWaterLifecycleHelpersMaintainTheAreaChain()
	Assert(FileContains%("Modules\ServerAreas.bb", "Function ServerWaterAttach(W.ServerWater, A.Area)") = True)
	Assert(FileContains%("Modules\ServerAreas.bb", "Function ServerWaterDetach(W.ServerWater)") = True)
	Assert(FileContains%("Modules\ServerAreas.bb", "W\NextWater = A\FirstWater") = True)
	Assert(FileContains%("Modules\ServerAreas.bb", "A\FirstWater = W") = True)
End Test

Test testGUECreationAndUndoAttachWaterToTheCurrentArea()
	Assert(CountFileMatches%("GUE.bb", "ServerWaterAttach(SW, CurrentArea)") = 2)
End Test

Test testGUEWaterDeletionDetachesBeforeDeletingTheServerRecord()
	Assert(WaterDeleteDetachesBeforeDelete%("GUE.bb") = True)
End Test

Test testServerSaveAreaUsesTheAuthoritativeWaterChain()
	Assert(ServerSaveAreaWalksWaterChain%("Modules\ServerAreas.bb") = True)
End Test
