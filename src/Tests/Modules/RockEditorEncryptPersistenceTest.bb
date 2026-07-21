Strict
EnableGC

; RC Rock Editor is a full legacy tool, so keep this focused contract
; standalone rather than pulling its UI and media dependency graph into a
; Strict test. The contract binds the encrypted-export promotion order.

Function OpenRockEditorSource.BBStream()
	Local F.BBStream = ReadFile("Tools\RC Rock Editor.bb")
	If F = Null Then F = ReadFile("..\Tools\RC Rock Editor.bb")
	If F = Null Then F = ReadFile("..\..\Tools\RC Rock Editor.bb")
	If F = Null Then F = ReadFile("src\Tools\RC Rock Editor.bb")
	Return F
End Function

Function EncryptB3DUsesVerifiedPromotion%()
	Local F.BBStream = OpenRockEditorSource()
	Local Line$
	Local Stage = 0
	Local InEncrypt = False
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function Encrypt_B3D$(") > 0 Then InEncrypt = True
		If InEncrypt
			If Stage = 0 And Instr(Line$, "TmpPath$ = fname$ + " + Chr$(34) + ".tmp" + Chr$(34)) > 0
				Stage = 1
			ElseIf Stage = 1 And Instr(Line$, "If FileSize(TmpPath$) <> offset") > 0
				Stage = 2
			ElseIf Stage = 2 And Instr(Line$, "If SafeWriteCommit%(TmpPath$, fname$, 0) = False") > 0
				Stage = 3
			ElseIf Stage = 3 And Trim$(Line$) = "FreeBank thisbank"
				Stage = 4
			ElseIf Stage = 4 And Trim$(Line$) = "Return -1"
				Stage = 5
			ElseIf Stage = 5 And Trim$(Line$) = "Return newn$"
				CloseFile F
				Return True
			EndIf
			If Instr(Line$, "End Function") > 0 Then Exit
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Function EncryptB3DContainsDirectPromotion%()
	Local F.BBStream = OpenRockEditorSource()
	Local Line$
	Local InEncrypt = False
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function Encrypt_B3D$(") > 0 Then InEncrypt = True
		If InEncrypt
			If Instr(Line$, "DeleteFile(fname$)") > 0 Or Instr(Line$, "CopyFile TmpPath$, fname$") > 0
				CloseFile F
				Return True
			EndIf
			If Instr(Line$, "End Function") > 0 Then Exit
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Test testEncryptB3DUsesRollbackSafePromotion()
	Assert(EncryptB3DUsesVerifiedPromotion%() = True)
	Assert(EncryptB3DContainsDirectPromotion%() = False)
End Test
