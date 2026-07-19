Strict
EnableGC

; RC Terrain Editor clears several Type lists during startup, load, reset, and
; save paths.  These are standalone source contracts because the tool pulls in
; the editor and renderer graph; each owned direct cleanup must save After
; before Delete so a multi-record list cannot advance through a freed node.
Function DirectCleanupUsesAfterCursor%(Path$, Start$, EndMarker$, LegacyFor$, FirstCursor$, NextCursor$, WhileCursor$, Capture$, DeleteNode$, Advance$)
	Local F.BBStream = ReadFile(Path$)
	Local InSection%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then F = ReadFile("..\\..\\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If InSection = False And Instr(Line$, Start$) > 0 Then InSection = True
		If InSection = True
			If Instr(Line$, LegacyFor$) > 0 Then
				CloseFile F
				Return False
			EndIf
			If Stage < 4 And Instr(Line$, DeleteNode$) > 0 Then
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, FirstCursor$) > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, NextCursor$) > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, WhileCursor$) > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, Capture$) > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, DeleteNode$) > 0 Then Stage = 5
			If Stage = 5 And Instr(Line$, Advance$) > 0 Then Stage = 6
			If Instr(Line$, EndMarker$) > 0 Then
				CloseFile F
				Return Stage = 6
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Function ModelBrushCleanupUsesAfterCursor%(Start$, EndMarker$)
	Return DirectCleanupUsesAfterCursor%("Tools\\RC Terrain Editor.bb", Start$, EndMarker$, "For mbr.modelbrush=Each ModelBrush", "mbr.modelbrush=First ModelBrush", "mbrNext.modelbrush=Null", "While mbr<>Null", "mbrNext=After mbr", "Delete mbr", "mbr=mbrNext")
End Function

Function AutoUndoCleanupUsesAfterCursor%(Start$, EndMarker$)
	Return DirectCleanupUsesAfterCursor%("Tools\\RC Terrain Editor.bb", Start$, EndMarker$, "For au.autoundo=Each autoundo", "au.autoundo=First autoundo", "auNext.autoundo=Null", "While au<>Null", "auNext=After au", "Delete au", "au=auNext")
End Function

Function SceneryCleanupUsesAfterCursor%(Start$, EndMarker$)
	Return DirectCleanupUsesAfterCursor%("Tools\\RC Terrain Editor.bb", Start$, EndMarker$, "For scn.scenery=Each scenery", "scn.scenery=First scenery", "scnNext.scenery=Null", "While scn<>Null", "scnNext=After scn", "Delete scn", "scn=scnNext")
End Function

Test testStartupModelBrushCleanupCapturesNextBeforeDelete()
	Assert(ModelBrushCleanupUsesAfterCursor%("Global totaldrops", "updatemodelbrushlist()") = True)
End Test

Test testModelBrushAddCleanupCapturesNextBeforeDelete()
	Assert(ModelBrushCleanupUsesAfterCursor%("Case GUI_MBWIN_ADD", "Case GUI_MBWIN_LOAD") = True)
End Test

Test testImportedAreaUndoCleanupCapturesNextBeforeDelete()
	Assert(AutoUndoCleanupUsesAfterCursor%("If Instr( Lower$(importmap$), " + Chr$(34) + ".dat" + Chr$(34) + ", 1 ) > 1", "ElseIf Instr( Lower$(importmap$), " + Chr$(34) + ".rct" + Chr$(34) + ", 1 ) > 1") = True)
End Test

Test testTerrainLoadUndoCleanupCapturesNextBeforeDelete()
	Assert(AutoUndoCleanupUsesAfterCursor%("If sf$<>" + Chr$(34) + Chr$(34), "unloadtrees(False)") = True)
End Test

Test testNewMapUndoCleanupCapturesNextBeforeDelete()
	Assert(AutoUndoCleanupUsesAfterCursor%("Function newmap(sg%,flagit=0,ask=1)", "UNLOADAREA()") = True)
End Test

Test testRemoveUndoDataCapturesNextBeforeDelete()
	Assert(AutoUndoCleanupUsesAfterCursor%("Function RemoveUndoData()", "End Function") = True)
End Test

Test testAreaSaveSceneryCleanupCapturesNextBeforeDelete()
	Assert(SceneryCleanupUsesAfterCursor%("Function SaveAreaRCTE(Name$)", "For dm.dropmodel=Each dropmodel") = True)
End Test

Test testAreaSaveFinalSceneryCleanupCapturesNextBeforeDelete()
	Assert(SceneryCleanupUsesAfterCursor%("Delete Each area", "End Function") = True)
End Test
