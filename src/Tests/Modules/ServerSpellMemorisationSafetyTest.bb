Strict
EnableGC

; Server.bb owns the complete server/UI graph, so this focused source contract
; protects the spell-memorisation completion sweep without loading that graph.
; A MemorisingSpell can outlive its actor after logout: the actor guard must be
; a standalone branch, and the sweep must capture its successor before Delete.

Function LeadingTabs%(Line$)
	Local Count%, Pos% = 1
	While Pos <= Len(Line$)
		If Mid$(Line$, Pos, 1) <> Chr$(9) Then Exit
		Count = Count + 1
		Pos = Pos + 1
	Wend
	Return Count
End Function

Function SpellMemorisationCompletionIsSafe%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Stage%, GuardIndent%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Stage = 0 And Instr(Line$, "; Update spell memorisation progress.") > 0 Then Stage = 1
		If Stage = 1 And Instr(Line$, "For MS.MemorisingSpell = Each MemorisingSpell") > 0
			CloseFile F
			Return False
		EndIf
		Select Stage
			Case 1
				If Instr(Line$, "Local MS.MemorisingSpell = First MemorisingSpell") > 0 Then Stage = 2
			Case 2
				If Instr(Line$, "Local MSNext.MemorisingSpell = Null") > 0 Then Stage = 3
			Case 3
				If Instr(Line$, "While MS <> Null") > 0 Then Stage = 4
			Case 4
				If Instr(Line$, "MSNext = After MS") > 0 Then Stage = 5
			Case 5
				If Instr(Line$, "If MilliSecs() - MS\CreatedTime > 6000") > 0 Then Stage = 6
			Case 6
				If Trim$(Line$) <> "If MS\AI <> Null"
					CloseFile F
					Return False
				EndIf
				GuardIndent = LeadingTabs(Line$)
				Stage = 7
			Case 7
				If Instr(Line$, "If MS\KnownNum >= 0 And MS\KnownNum <= 999") > 0
					If LeadingTabs(Line$) <> GuardIndent + 1 Then Return False
					Stage = 8
				EndIf
			Case 8
				If Instr(Line$, "If MS\AI\SpellLevels[MS\KnownNum] > 0") > 0
					If LeadingTabs(Line$) <= GuardIndent Then Return False
					Stage = 9
				EndIf
			Case 9
				If Instr(Line$, "EndIf") > 0 And LeadingTabs(Line$) = GuardIndent Then Stage = 10
			Case 10
				If Instr(Line$, "Delete MS") > 0
					If LeadingTabs(Line$) <> GuardIndent Then Return False
					Stage = 11
				EndIf
			Case 11
				If Instr(Line$, "MS = MSNext") > 0
					CloseFile F
					Return LeadingTabs(Line$) = GuardIndent - 1
				EndIf
		End Select
		If Stage > 0 And Instr(Line$, "; Scripts") > 0 Then Exit
	Wend

	CloseFile F
	Return False
End Function

Test testSpellMemorisationCompletionHandlesStaleActorsAndCursorDeletes()
	Assert(SpellMemorisationCompletionIsSafe%("Server.bb") = True)
End Test
