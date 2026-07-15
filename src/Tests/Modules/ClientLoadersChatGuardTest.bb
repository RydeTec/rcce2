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
	Local SawChatBar%, SawTextureCopy%
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
		If Stage > 0 And Instr(Line$, "Chat\Texture") > 0
			If Stage = 2
				If Instr(Line$, "If Chat\Texture <> 65535") = 0 Or LeadingTabs(Line$) <> NullIndent + 1
					CloseFile F
					Return False
				EndIf
			ElseIf Stage = 3
				If LeadingTabs(Line$) <> TextureIndent + 1
					CloseFile F
					Return False
				EndIf
				SawTextureCopy = True
			Else
				CloseFile F
				Return False
			EndIf
		EndIf
		If Stage > 0 And Instr(Line$, "ChatBar") > 0
			If Stage <> 3 Or LeadingTabs(Line$) <> TextureIndent + 1
				CloseFile F
				Return False
			EndIf
			If Instr(Line$, "ChatBar = New InterfaceComponent") > 0 Then SawChatBar = True
		EndIf
		If Stage = 2 And Instr(Line$, "If Chat\Texture <> 65535") > 0
			TextureIndent = LeadingTabs(Line$)
			Stage = 3
		EndIf
		If Stage = 3 And LeadingTabs(Line$) = TextureIndent
			If Instr(Line$, "EndIf") > 0 Or Instr(Line$, "End If") > 0 Then Stage = 4
		EndIf
		If Stage = 4 And LeadingTabs(Line$) = NullIndent
			If Instr(Line$, "EndIf") > 0 Or Instr(Line$, "End If") > 0
				Stage = 5
			EndIf
		EndIf
		If Stage > 0 And Instr(Line$, "CreateInterface()") > 0
			CloseFile F
			Return Stage = 5 And SawChatBar And SawTextureCopy
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Test testChatTextureReadIsNestedUnderNullGuard()
	Assert(ChatTextureReadIsNested%("Modules\ClientLoaders.bb") = True)
End Test
