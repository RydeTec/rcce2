Strict
EnableGC

; RestoreLanguage belongs to the GUE include graph, so keep this focused
; source contract independent from the editor/runtime dependencies.

Function RestoreLanguageUsesAtomicSave%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local InFunction%, Stage%
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = Trim$(ReadLine$(F))
		If Line$ = "Function RestoreLanguage(Filename$)" Then InFunction = True
		If InFunction
			If Instr(Line$, "WriteFile(Filename$)") > 0
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "LoadLanguage(") > 0 Then Stage = 1
			If Stage = 1 And Line$ = "Local Temp$ = SafeWriteOpen$(Filename$)" Then Stage = 2
			If Stage = 2 And Line$ = "Local F% = WriteFile(Temp$)" Then Stage = 3
			If Stage = 3 And Line$ = "If F = 0 Then Return False" Then Stage = 4
			If Stage = 4 And Line$ = "For i = 0 To MaxLanguageString" Then Stage = 5
			If Stage = 5 And Line$ = "WriteLine( F, LanguageString$(i) )" Then Stage = 6
			If Stage = 6 And Line$ = "Return SafeWriteCommit%(Temp$, Filename$, F)"
				CloseFile F
				Return True
			EndIf
			If Line$ = "End Function" Then Exit
		EndIf
	Wend

	CloseFile F
	Return False

End Function

Test testRestoreLanguageStagesAndCommitsTheCompleteLocaleAtomically()
	Assert(RestoreLanguageUsesAtomicSave%("Modules\Language.bb") = True)
End Test
