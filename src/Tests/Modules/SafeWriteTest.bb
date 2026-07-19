Strict
EnableGC

; Exercise the real SafeWriteOpen / SafeWriteCommit / SafeWriteAbort in
; Logging.bb against the working directory's filesystem. WriteLog needs
; LogMode + MainLog globals to exist; default LogMode=0 keeps it quiet
; so no Data\Logs directory is required.
Global LogMode = 0
Global MainLog = 0

Include "Modules\Logging.bb"

Global testDir$ = CurrentDir$()
Global ProductionPath$ = testDir$ + "safewrite_test.dat"
Global TempPathExpected$ = ProductionPath$ + ".tmp"
Global BakPath$ = ProductionPath$ + ".bak"
Global BakTempPath$ = BakPath$ + ".tmp"

Function CleanupTestFiles()
	If FileType(ProductionPath$) = 1 Then DeleteFile(ProductionPath$)
	If FileType(TempPathExpected$) = 1 Then DeleteFile(TempPathExpected$)
	If FileType(BakPath$) = 1 Then DeleteFile(BakPath$)
	If FileType(BakTempPath$) = 1 Then DeleteFile(BakTempPath$)
End Function

; Helper: write payload to path and close the handle. We close locally
; (rather than letting SafeWriteCommit close) because Strict mode can't
; bridge a BBStream local to SafeWriteCommit's untyped Int parameter --
; passing F=0 tells the helper "I already closed it".
Function SeedFile(path$, payload$)
	Local s.BBStream = WriteFile(path$)
	If s = Null Then Return
	WriteString(s, payload$)
	CloseFile(s)
End Function

Function SeedEmptyFile(path$)
	Local s.BBStream = WriteFile(path$)
	If s = Null Then Return
	CloseFile(s)
End Function

Function ReadFileString$(path$)
	Local s.BBStream = ReadFile(path$)
	If s = Null Then Return ""
	Local payload$ = ReadString(s)
	CloseFile(s)
	Return payload$
End Function

; Filesystem copy failures are difficult to inject portably in a Blitz test,
; so bind the fail-closed ordering in the real helper. This must keep the
; verified backup ahead of production deletion and the verified promotion
; ahead of temp cleanup.
Function SafeWriteCommitUsesVerifiedBackupAndPromotion%()
	Local F.BBStream = ReadFile("Modules\Logging.bb")
	Local Line$
	Local Stage%
	If F = Null Then F = ReadFile("..\Modules\Logging.bb")
	If F = Null Then F = ReadFile("..\..\Modules\Logging.bb")
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function SafeWriteCommit%(TempPath$, FinalPath$, F)") > 0 Then Stage = 1
		If Stage = 0 Then Continue
		If Stage = 1 And Instr(Line$, "Local TempSize = FileSize(TempPath$)") > 0 Then Stage = 2
		If Stage = 2 And Instr(Line$, "Local FinalSize = FileSize(FinalPath$)") > 0 Then Stage = 3
		If Stage = 3 And Instr(Line$, "Local BakTemp$ = Bak$ + " + Chr$(34) + ".tmp" + Chr$(34)) > 0 Then Stage = 4
		If Stage = 4 And Instr(Line$, "CopyFile(FinalPath$, BakTemp$)") > 0 Then Stage = 5
		If Stage = 5 And Instr(Line$, "If FileType(BakTemp$) <> 1 Or FileSize(BakTemp$) <> FinalSize") > 0 Then Stage = 6
		If Stage = 6 And Instr(Line$, "CopyFile(BakTemp$, Bak$)") > 0 Then Stage = 7
		If Stage = 7 And Instr(Line$, "If FileType(Bak$) <> 1 Or FileSize(Bak$) <> FinalSize") > 0 Then Stage = 8
		If Stage = 8 And Instr(Line$, "DeleteFile(FinalPath$)") > 0 Then Stage = 9
		If Stage = 9 And Instr(Line$, "CopyFile(TempPath$, FinalPath$)") > 0 Then Stage = 10
		If Stage = 10 And Instr(Line$, "If FileType(FinalPath$) <> 1 Or FileSize(FinalPath$) <> TempSize") > 0 Then Stage = 11
		If Stage = 11 And Instr(Line$, "DeleteFile(TempPath$)") > 0 Then Stage = 12
		If Instr(Line$, "End Function") > 0 Then Exit
	Wend

	CloseFile(F)
	Return Stage = 12
End Function

; SafeWriteOpen is a pure helper: it just appends .tmp to the final path.
; Pin that contract -- callers rely on the temp name being deterministic
; so cleanup paths (SafeWriteAbort) can target it.
Test testSafeWriteOpenReturnsTempSuffixedPath()
	Local temp$ = SafeWriteOpen$(ProductionPath$)
	Assert(temp$ = ProductionPath$ + ".tmp")
End Test

; Happy path: write to temp, commit promotes the temp into production and
; deletes the temp. No prior production file exists, so no .bak is made.
Test testSafeWriteCommitPromotesTempToProductionOnFirstSave()
	CleanupTestFiles()

	Local temp$ = SafeWriteOpen$(ProductionPath$)
	SeedFile(temp$, "first save payload")

	Local ok% = SafeWriteCommit%(temp$, ProductionPath$, 0)

	Assert(ok = True)
	Assert(FileType(ProductionPath$) = 1)
	Assert(FileType(temp$) <> 1) ; temp consumed
	Assert(FileType(BakPath$) <> 1) ; no prior file -> no .bak

	CleanupTestFiles()
End Test

; When a production file already exists, commit demotes it to .bak before
; promoting the new temp. Lets a crash mid-promote recover the previous
; version.
Test testSafeWriteCommitDemotesPriorProductionToBak()
	CleanupTestFiles()

	SeedFile(ProductionPath$, "previous save")

	Local temp$ = SafeWriteOpen$(ProductionPath$)
	SeedFile(temp$, "newer save")

	Local ok% = SafeWriteCommit%(temp$, ProductionPath$, 0)

	Assert(ok = True)
	Assert(FileType(ProductionPath$) = 1)
	Assert(FileType(BakPath$) = 1) ; previous version preserved
	Assert(FileType(temp$) <> 1)
	Assert(ReadFileString$(BakPath$) = "previous save")
	Assert(ReadFileString$(ProductionPath$) = "newer save")

	CleanupTestFiles()
End Test

; Empty temp = silent WriteFile failure. SafeWriteCommit must refuse to
; promote an empty file, otherwise production would be replaced with 0
; bytes (which is the whole bug the helper exists to prevent).
Test testSafeWriteCommitRefusesEmptyTemp()
	CleanupTestFiles()

	SeedFile(ProductionPath$, "must survive")

	Local temp$ = SafeWriteOpen$(ProductionPath$)
	SeedEmptyFile(temp$)

	Local ok% = SafeWriteCommit%(temp$, ProductionPath$, 0)

	Assert(ok = False)
	Assert(FileType(ProductionPath$) = 1) ; production untouched
	Assert(FileType(temp$) <> 1) ; empty temp deleted by commit
	Assert(ReadFileString$(ProductionPath$) = "must survive")

	CleanupTestFiles()
End Test

; A temp that was never created (WriteFile returned 0, so no file exists at
; the temp path at all) is refused too. This is a DISTINCT branch from the
; empty-temp case above: SafeWriteCommit bails at the `FileType(TempPath$)
; <> 1` check (Logging.bb:41) before ever reaching the FileSize check, and
; must leave the production file untouched. It's the branch a save path hits
; when the temp WriteFile fails to open at all (disk full, bad dir, perms).
Test testSafeWriteCommitRefusesMissingTemp()
	CleanupTestFiles()

	SeedFile(ProductionPath$, "must survive")

	; Never write the temp -- pass its expected path with handle 0.
	Assert(FileType(TempPathExpected$) <> 1) ; precondition: no temp on disk
	Local ok% = SafeWriteCommit%(TempPathExpected$, ProductionPath$, 0)

	Assert(ok = False)
	Assert(FileType(ProductionPath$) = 1) ; production untouched
	Assert(ReadFileString$(ProductionPath$) = "must survive")

	CleanupTestFiles()
End Test

; Successive commits cycle the .bak: after three saves A -> B -> C,
; .bak must hold B (the immediately previous version), not A. This pins
; the behaviour at Logging.bb's `If FileType(Bak$) = 1 Then DeleteFile(Bak$)`
; line -- without that delete, the second CopyFile into an existing
; .bak target either fails silently or appends, and the .bak content
; diverges from the most-recent-pre-save state. The RC Terrain Editor
; SaveAreaTE migration (and the GUE SaveArea before it) saves on every
; build-cycle hotkey; an author hitting Save twice in a row must be able
; to recover the *previous* save, not the original empty area.
Test testSafeWriteCommitCyclesBakOnSuccessiveSaves()
	CleanupTestFiles()

	; Save A (no prior file -> no .bak)
	Local tempA$ = SafeWriteOpen$(ProductionPath$)
	SeedFile(tempA$, "save A")
	Assert(SafeWriteCommit%(tempA$, ProductionPath$, 0) = True)
	Assert(FileType(BakPath$) <> 1)

	; Save B (A demoted to .bak)
	Local tempB$ = SafeWriteOpen$(ProductionPath$)
	SeedFile(tempB$, "save B")
	Assert(SafeWriteCommit%(tempB$, ProductionPath$, 0) = True)
	Assert(ReadFileString$(ProductionPath$) = "save B")
	Assert(ReadFileString$(BakPath$) = "save A")

	; Save C (B demoted to .bak, displacing A)
	Local tempC$ = SafeWriteOpen$(ProductionPath$)
	SeedFile(tempC$, "save C")
	Assert(SafeWriteCommit%(tempC$, ProductionPath$, 0) = True)
	Assert(ReadFileString$(ProductionPath$) = "save C")
	Assert(ReadFileString$(BakPath$) = "save B")

	CleanupTestFiles()
End Test

; SafeWriteAbort cleans up the temp when the caller decides not to commit
; (e.g. a serialization error mid-write).
Test testSafeWriteAbortRemovesTemp()
	CleanupTestFiles()

	Local temp$ = SafeWriteOpen$(ProductionPath$)
	SeedFile(temp$, "abandoned")

	Assert(FileType(temp$) = 1)
	SafeWriteAbort(temp$)
	Assert(FileType(temp$) <> 1)

	CleanupTestFiles()
End Test

Test testSafeWriteCommitVerifiesBackupAndPromotionCopies()
	Assert(SafeWriteCommitUsesVerifiedBackupAndPromotion() = True)
End Test
