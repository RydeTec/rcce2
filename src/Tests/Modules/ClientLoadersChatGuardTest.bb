Strict
EnableGC

; LoadGame can run with no persisted Chat component. BlitzForge evaluates And
; eagerly, so the optional component must be checked before its texture field.

Function LeadingTabs%(Line$)
	Local Count% = 0
	While Count < Len(Line$)
		If Mid$(Line$, Count + 1, 1) <> Chr$(9) Then Exit
		Count = Count + 1
	Wend
	Return Count
End Function

Function ChatTextureReadIsNested%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Stage%, NullIndent%, TextureIndent%
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "; User interface") > 0 Then Stage = 1
		If Stage = 1 And Instr(Line$, "If Chat <> Null And Chat\Texture") > 0
			CloseFile F
			Return False
		EndIf
		If Stage = 1 And Instr(Line$, "If Chat <> Null") > 0
			If Instr(Line$, "And") > 0 Or Instr(Line$, "Or") > 0
				CloseFile F
				Return False
			EndIf
			NullIndent = LeadingTabs(Line$)
			Stage = 2
		EndIf
		If Stage = 2 And Instr(Line$, "If Chat\Texture <> 65535") > 0
			TextureIndent = LeadingTabs(Line$)
			If TextureIndent <> NullIndent + 1
				CloseFile F
				Return False
			EndIf
			Stage = 3
		EndIf
		If Stage = 3 And Instr(Line$, "ChatBar = New InterfaceComponent") > 0
			If LeadingTabs(Line$) <> TextureIndent + 1
				CloseFile F
				Return False
			EndIf
			CloseFile F
			Return True
		EndIf
		If Stage > 0 And Instr(Line$, "CreateInterface()") > 0 Then Exit
	Wend

	CloseFile F
	Return False
End Function

Test testChatTextureReadIsNestedUnderNullGuard()
	Assert(ChatTextureReadIsNested%("Modules\ClientLoaders.bb") = True)
End Test
