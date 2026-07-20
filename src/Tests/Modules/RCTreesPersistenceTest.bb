Strict
EnableGC

; RCTrees is a legacy editor module with a large rendering dependency graph.
; Keep this regression focused on the RCTE.dat persistence boundary instead of
; pulling the whole editor into a unit-test compile.
Function RCTreesSeasonSettingsUseAtomicPersistence%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InFunction%, Stage%
	Local Line$, Trimmed$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		Trimmed$ = Trim$(Line$)
		If Instr(Line$, "Function tree_setvalues()") > 0 Then InFunction = True
		If InFunction = True
			If Instr(Line$, "WriteFile(seasoncolor_file$)") > 0
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Trimmed$ = "If FileType(seasoncolor_file$)<>1 Or FileSize(seasoncolor_file$)<>144 Then" Then Stage = 1
			If Stage = 1 And Trimmed$ = "TempPath$=SafeWriteOpen$(seasoncolor_file$)" Then Stage = 2
			If Stage = 2 And Trimmed$ = "cfile=WriteFile(TempPath$)" Then Stage = 3
			If Stage = 3 And Trimmed$ = "For i=0 To 11" Then Stage = 4
			If Stage = 4 And Trimmed$ = "Next" Then Stage = 5
			If Stage = 5 And Trimmed$ = "CloseFile cfile" Then Stage = 6
			If Stage = 6 And Trimmed$ = "If FileSize(TempPath$)<>144" Then Stage = 7
			If Stage = 7 And Trimmed$ = "SafeWriteAbort(TempPath$)" Then Stage = 8
			If Stage = 8 And Trimmed$ = "EndIf" Then Stage = 9
			If Stage = 9 And Trimmed$ = "If SafeWriteCommit(TempPath$,seasoncolor_file$,0)=False Then Return" Then Stage = 10
			If Stage = 10 And Trimmed$ = "Else"
				CloseFile F
				Return True
			EndIf
			If Trimmed$ = "End Function" Then Exit
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Global RCTreesPersistenceFixture$ = CurrentDir$() + "rctrees_persistence_unsafe.tmp"

Test testRCTreesSeasonSettingsUseAtomicPersistence()
	Assert(RCTreesSeasonSettingsUseAtomicPersistence%("Modules\RCTrees.bb") = True)
End Test

Test testRCTreesPersistenceScannerRejectsDirectFinalWrite()
	If FileType(RCTreesPersistenceFixture$) = 1 Then DeleteFile(RCTreesPersistenceFixture$)
	Local F.BBStream = WriteFile(RCTreesPersistenceFixture$)
	Assert(F <> Null)
	If F <> Null
		WriteLine F, "Function tree_setvalues()"
		WriteLine F, "If FileType(seasoncolor_file$)<>1 Or FileSize(seasoncolor_file$)<>144 Then"
		WriteLine F, "TempPath$=SafeWriteOpen$(seasoncolor_file$)"
		WriteLine F, "cfile=WriteFile(seasoncolor_file$)"
		WriteLine F, "For i=0 To 11"
		WriteLine F, "Next"
		WriteLine F, "CloseFile cfile"
		WriteLine F, "If FileSize(TempPath$)<>144"
		WriteLine F, "SafeWriteAbort(TempPath$)"
		WriteLine F, "EndIf"
		WriteLine F, "If SafeWriteCommit(TempPath$,seasoncolor_file$,0)=False Then Return"
		WriteLine F, "Else"
		WriteLine F, "EndIf"
		WriteLine F, "End Function"
		CloseFile F
		Assert(RCTreesSeasonSettingsUseAtomicPersistence%(RCTreesPersistenceFixture$) = False)
	EndIf
	If FileType(RCTreesPersistenceFixture$) = 1 Then DeleteFile(RCTreesPersistenceFixture$)
End Test
