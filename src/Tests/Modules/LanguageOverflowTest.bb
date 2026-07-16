Strict
EnableGC

; Regression coverage for corrupt locale files with more effective rows than
; the fixed LanguageString$ registry can hold. LoadLanguage must reject the
; overflow through its existing False result instead of terminating startup.

Include "Modules\Language.bb"

Global OverflowLanguageFile$ = CurrentDir$() + "language_overflow_test.txt"

Function WriteOverflowLanguageFile%()
	Local F.BBStream = WriteFile(OverflowLanguageFile$)
	If F = 0 Then Return False

	Local ID%
	For ID = 0 To MaxLanguageString + 1
		WriteLine(F, "overflow entry")
	Next

	CloseFile(F)
	Return True
End Function

Test testLoadLanguageRejectsOverflowWithoutTerminating()
	Assert(WriteOverflowLanguageFile() = True)
	Assert(LoadLanguage(OverflowLanguageFile$) = False)
	DeleteFile(OverflowLanguageFile$)
End Test
