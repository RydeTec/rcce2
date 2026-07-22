Strict
EnableGC

; A visual-area terrain grid comes directly from disk. It must be bounded
; before the native terrain allocation or any height reads, and a rejected
; record must leave the loader in its normal failed-load state.
Function AreaLoaderRejectsInvalidTerrainGridBeforeAllocation%()
	Local F.BBStream = ReadFile("Modules\\AreaLoader.bb")
	Local Line$
	Local MaxGridBound%
	Local Stage%

	If F = Null Then F = ReadFile("..\\Modules\\AreaLoader.bb")
	If F = Null Then F = ReadFile("..\\..\\Modules\\AreaLoader.bb")
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)

		If Instr(Line$, "Const MaxTerrainGrid = 4096") > 0 Then MaxGridBound = True
		If Stage = 0 And Instr(Line$, "GridSize = ReadInt(F)") > 0 Then Stage = 1
		If Stage = 1 And Instr(Line$, "T\\EN = CreateTerrain(GridSize)") > 0
			CloseFile F
			Return False
		EndIf
		If Stage = 1 And Instr(Line$, "ReadFloat#(F)") > 0
			CloseFile F
			Return False
		EndIf
		If Stage = 1 And Instr(Line$, "If GridSize < 0 Or GridSize > MaxTerrainGrid") > 0 Then Stage = 2
		If Stage = 2 And Instr(Line$, "Delete T") > 0 Then Stage = 3
		If Stage = 3 And Instr(Line$, "CloseFile(F)") > 0 Then Stage = 4
		If Stage = 4 And Instr(Line$, "UnlockMeshes()") > 0 Then Stage = 5
		If Stage = 5 And Instr(Line$, "UnlockTextures()") > 0 Then Stage = 6
		If Stage = 6 And Instr(Line$, "AreaLoadEnd()") > 0 Then Stage = 7
		If Stage = 7 And Instr(Line$, "UnloadArea()") > 0 Then Stage = 8
		If Stage = 8 And Instr(Line$, "Return False") > 0
			CloseFile F
			Return MaxGridBound
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Test testTerrainGridBoundsRejectBeforeAllocationAndCleanUp()
	Assert(AreaLoaderRejectsInvalidTerrainGridBeforeAllocation%() = True)
End Test
