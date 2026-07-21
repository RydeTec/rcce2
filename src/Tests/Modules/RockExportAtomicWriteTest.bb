Strict
EnableGC

; RC Rock export is a legacy Tool path, so this standalone source contract
; avoids pulling in its F-UI and 3D-media dependency graph. It binds the
; BB3D file promotion boundary that protects an existing exported mesh.

Function OpenRockExportSource.BBStream()
	Local F.BBStream = ReadFile("Modules\\Rock_export.bb")
	If F = Null Then F = ReadFile("..\\Modules\\Rock_export.bb")
	If F = Null Then F = ReadFile("..\\..\\Modules\\Rock_export.bb")
	If F = Null Then F = ReadFile("src\\Modules\\Rock_export.bb")
	Return F
End Function

Function WriteBB3DUsesSafeWritePromotion%()
	Local F.BBStream = OpenRockExportSource()
	Local Line$
	Local Stage = 0
	Local InWrite = False
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function WriteBB3D(") > 0 Then InWrite = True
		If InWrite
			If Stage = 0 And Instr(Line$, "TempPath$ = SafeWriteOpen$(f_name$)") > 0
				Stage = 1
			ElseIf Stage = 1 And Instr(Line$, "file = WriteFile(TempPath$)") > 0
				Stage = 2
			ElseIf Stage = 2 And Trim$(Line$) = "If file = 0"
				Stage = 3
			ElseIf Stage = 3 And Instr(Line$, "SafeWriteAbort(TempPath$)") > 0
				Stage = 4
			ElseIf Stage = 4 And Trim$(Line$) = "Return"
				Stage = 5
			ElseIf Stage = 5 And Instr(Line$, "b3dSetFile( file )") > 0
				Stage = 6
			ElseIf Stage = 6 And Instr(Line$, "b3dEndChunk();end of BB3D chunk") > 0
				Stage = 7
			ElseIf Stage = 7 And Instr(Line$, "If SafeWriteCommit%(TempPath$, f_name$, file) = False") > 0
				CloseFile F
				Return True
			EndIf
			If Instr(Line$, "End Function") > 0 Then Exit
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Function WriteBB3DContainsDirectFinalWrite%()
	Local F.BBStream = OpenRockExportSource()
	Local Line$
	Local InWrite = False
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function WriteBB3D(") > 0 Then InWrite = True
		If InWrite
			If Instr(Line$, "WriteFile( f_name$ )") > 0 Or Instr(Line$, "CloseFile file") > 0
				CloseFile F
				Return True
			EndIf
			If Instr(Line$, "End Function") > 0 Then Exit
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Test testWriteBB3DUsesSafeWritePromotion()
	Assert(WriteBB3DUsesSafeWritePromotion%() = True)
	Assert(WriteBB3DContainsDirectFinalWrite%() = False)
End Test
