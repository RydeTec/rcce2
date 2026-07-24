Strict
EnableGC

; UnloadTrees clears three Type lists during an area transition. Each direct
; deletion must save the successor before it frees an entity or deletes its
; current record, otherwise multi-record unloads can corrupt the cursor.
Function UnloadTreesCursorContract%(LegacyFor$, FirstCursor$, NextCursor$, WhileCursor$, Capture$, OptionalFree$, DeleteNode$, Advance$)
	Local F.BBStream = ReadFile("Modules\RCTrees.bb")
	Local Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\Modules\RCTrees.bb")
	If F = Null Then F = ReadFile("..\..\Modules\RCTrees.bb")
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function UnloadTrees(deltree=True)") > 0 Then Exit
	Wend
	If Eof(F)
		CloseFile F
		Return False
	EndIf

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, LegacyFor$) > 0
			CloseFile F
			Return False
		EndIf
		If Stage < 5
			If Instr(Line$, DeleteNode$) > 0
				CloseFile F
				Return False
			EndIf
		EndIf
		If Stage = 0
			If Instr(Line$, FirstCursor$) > 0 Then Stage = 1
		ElseIf Stage = 1
			If Instr(Line$, NextCursor$) > 0 Then Stage = 2
		ElseIf Stage = 2
			If Instr(Line$, WhileCursor$) > 0 Then Stage = 3
		ElseIf Stage = 3
			If Instr(Line$, Capture$) > 0 Then Stage = 4
		ElseIf Stage = 4
			If OptionalFree$ = "" Then Stage = 5
			If OptionalFree$ <> ""
				If Instr(Line$, OptionalFree$) > 0 Then Stage = 5
			EndIf
		ElseIf Stage = 5
			If Instr(Line$, DeleteNode$) > 0 Then Stage = 6
		ElseIf Stage = 6
			If Instr(Line$, Advance$) > 0 Then Stage = 7
		EndIf
		If Instr(Line$, "End Function") > 0
			CloseFile F
			Return Stage = 7
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Function TreeUnloadUsesAfterCursor%()
	Return UnloadTreesCursorContract%("For Rt.Tree=Each tree", "Local Rt.Tree=First tree", "Local RtNext.Tree=Null", "While Rt<>Null", "RtNext=After Rt", "FreeEntity RT\MainEnt", "Delete Rt", "Rt=RtNext")
End Function

Function GrassUnloadUsesAfterCursor%()
	Return UnloadTreesCursorContract%("For RtG.RCGRASS=Each rcgrass", "Local RtG.RCGRASS=First rcgrass", "Local RtGNext.RCGRASS=Null", "While RtG<>Null", "RtGNext=After RtG", "FreeEntity RTg\ent", "Delete RtG", "RtG=RtGNext")
End Function

Function GrassTextureUnloadUsesAfterCursor%()
	Return UnloadTreesCursorContract%("For gt.GrassTextures=Each grasstextures", "Local gt.GrassTextures=First grasstextures", "Local gtNext.GrassTextures=Null", "While gt<>Null", "gtNext=After gt", "", "Delete gt", "gt=gtNext")
End Function

Test testTreeUnloadCapturesNextBeforeEntityFreeAndDelete()
	Assert(TreeUnloadUsesAfterCursor%() = True)
End Test

Test testGrassUnloadCapturesNextBeforeEntityFreeAndDelete()
	Assert(GrassUnloadUsesAfterCursor%() = True)
End Test

Test testGrassTextureUnloadCapturesNextBeforeDelete()
	Assert(GrassTextureUnloadUsesAfterCursor%() = True)
End Test
