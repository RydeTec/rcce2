Strict
EnableGC

; Regression test pinning the atomic-save contract on the Gubbin Tool
; SaveRotation / DecryptMesh paths (issue #43).
;
; Pre-fix bug shape:
;
;   ; SaveRotation opened the production .b3d directly for in-place
;   ; read+write of vertex chunks.
;   F = OpenFile("Data\Meshes\Foo.b3d")
;   While Not Eof(F)
;       ... seek, ReadFloat, transform, SeekFile back, WriteFloat ...
;   Wend
;   CloseFile(F)
;
; Any failure during the walk (process kill, RuntimeError downstream,
; the user-reported VRTS-walk desync that issue #43 describes) left
; the production file truncated or zero-length. The mesh was
; permanently destroyed; no recovery path existed.
;
; Post-fix posture: CopyFile the source to FinalPath$ + ".tmp", mutate
; the temp in place using the same VRTS walker, then SafeWriteCommit
; atomic-promotes the temp into production. SafeWriteCommit refuses
; to promote an empty temp and demotes the prior production to .bak.
; Any failure leaves the original intact (with a .bak even on success
; for one-cycle history).
;
; The Gubbin Tool pulls F-UI, media, and GubbinScene globals and can't
; be Included into a Strict test build. Following the established
; SafeWriteTest pattern, this file exercises the SafeWrite contract
; with the same shape SaveRotation uses (CopyFile -> in-place mutate
; -> SafeWriteCommit) and validates the failure modes that closing
; issue #43 depends on.

Global LogMode = 0
Global MainLog = 0

Include "Modules\Logging.bb"

Global TestRoot$ = CurrentDir$()
Global FinalPath$ = TestRoot$ + "gubbin_save_atomic_test.b3d"
Global TempPath$ = FinalPath$ + ".tmp"
Global BakPath$ = FinalPath$ + ".bak"

Function CleanupTestFiles()
	If FileType(FinalPath$) = 1 Then DeleteFile(FinalPath$)
	If FileType(TempPath$) = 1 Then DeleteFile(TempPath$)
	If FileType(BakPath$) = 1 Then DeleteFile(BakPath$)
End Function

; Writes a 48-byte file with a recognizable signature so we can verify
; round-trip integrity without parsing real B3D format.
Function WriteSignatureFile(Path$, Sig%)
	Local Out.BBStream = WriteFile(Path$)
	If Out = Null Then Return
	WriteInt(Out, Sig)
	WriteInt(Out, Sig + 1)
	WriteInt(Out, Sig + 2)
	WriteInt(Out, Sig + 3)
	WriteInt(Out, Sig + 4)
	WriteInt(Out, Sig + 5)
	WriteInt(Out, Sig + 6)
	WriteInt(Out, Sig + 7)
	WriteInt(Out, Sig + 8)
	WriteInt(Out, Sig + 9)
	WriteInt(Out, Sig + 10)
	WriteInt(Out, Sig + 11)
	CloseFile(Out)
End Function

Function ReadFirstInt%(Path$)
	Local In.BBStream = ReadFile(Path$)
	If In = Null Then Return -1
	Local V% = ReadInt(In)
	CloseFile(In)
	Return V
End Function

; --- Successful round-trip -----------------------------------------------

Test testCopyFileThenSafeWriteCommitPromotesTemp()
	CleanupTestFiles()
	; Pre-save state: original file with signature 1000.
	WriteSignatureFile(FinalPath$, 1000)
	Assert(FileType(FinalPath$) = 1)
	Local Tmp$ = SafeWriteOpen$(FinalPath$)
	Assert(Tmp$ = TempPath$)
	; Stage the working copy. Mirrors the production SaveRotation flow:
	; the source is copied to .tmp, then the .tmp is mutated in place.
	CopyFile(FinalPath$, Tmp$)
	Assert(FileType(Tmp$) = 1)
	; Mutate the temp in place: rewrite the first int to a new signature.
	Local F.BBStream = OpenFile(Tmp$)
	Assert(F <> Null)
	SeekFile(F, 0)
	WriteInt(F, 2000)
	CloseFile(F)
	; F=0 sentinel tells SafeWriteCommit "I closed it already" -- Strict
	; can't bridge a BBStream local to the untyped Int parameter.
	Assert(SafeWriteCommit%(Tmp$, FinalPath$, 0) = True)
	; Production now reads the mutated signature.
	Assert(ReadFirstInt%(FinalPath$) = 2000)
	; Prior production is preserved as .bak.
	Assert(FileType(BakPath$) = 1)
	Assert(ReadFirstInt%(BakPath$) = 1000)
	; Temp has been cleaned up.
	Assert(FileType(Tmp$) <> 1)
	CleanupTestFiles()
End Test

; --- Aborted save preserves original -------------------------------------

Test testSafeWriteAbortLeavesOriginalUntouched()
	CleanupTestFiles()
	WriteSignatureFile(FinalPath$, 3000)
	Assert(FileType(FinalPath$) = 1)
	Local Tmp$ = SafeWriteOpen$(FinalPath$)
	CopyFile(FinalPath$, Tmp$)
	Local F.BBStream = OpenFile(Tmp$)
	Assert(F <> Null)
	; Simulate mid-mutation crash: corrupt the temp, then abort.
	SeekFile(F, 0)
	WriteInt(F, 9999)
	CloseFile(F)
	SafeWriteAbort(Tmp$)
	; The original is unchanged because we never committed.
	Assert(FileType(FinalPath$) = 1)
	Assert(ReadFirstInt%(FinalPath$) = 3000)
	; No leftover temp.
	Assert(FileType(Tmp$) <> 1)
	; No .bak was created (commit never ran).
	Assert(FileType(BakPath$) <> 1)
	CleanupTestFiles()
End Test

; --- Empty temp refusal --------------------------------------------------

Test testSafeWriteCommitRefusesEmptyTemp()
	CleanupTestFiles()
	WriteSignatureFile(FinalPath$, 4000)
	Assert(FileType(FinalPath$) = 1)
	Local Tmp$ = SafeWriteOpen$(FinalPath$)
	; Create a zero-length temp -- the WriteFile-then-CloseFile case
	; with no payload. Production SaveRotation would never reach this
	; on a real file (CopyFile leaves either full content or nothing),
	; but the SafeWriteCommit contract MUST refuse to promote a
	; zero-length file regardless.
	Local F.BBStream = WriteFile(Tmp$)
	Assert(F <> Null)
	CloseFile(F)
	Assert(FileType(Tmp$) = 1)
	Assert(FileSize(Tmp$) = 0)
	; SafeWriteCommit returns False on empty-temp and cleans it up;
	; original is untouched.
	Assert(SafeWriteCommit%(Tmp$, FinalPath$, 0) = False)
	Assert(FileType(FinalPath$) = 1)
	Assert(ReadFirstInt%(FinalPath$) = 4000)
	Assert(FileType(Tmp$) <> 1)
	CleanupTestFiles()
End Test

; --- Source-missing tolerance --------------------------------------------

Test testCopyFileFailureLeavesNoCommitState()
	; If CopyFile fails (source missing), the temp won't exist, and
	; SaveRotation must bail without touching the original. This pins
	; the gate `If FileType(TempPath$) <> 1 Then Return` in the
	; production code.
	CleanupTestFiles()
	Local Tmp$ = SafeWriteOpen$(FinalPath$)
	; No source file -> CopyFile is a no-op silently in Blitz3D.
	CopyFile(FinalPath$, Tmp$)
	; Temp should not exist; SaveRotation's gate catches this and
	; returns without calling SafeWriteCommit.
	Assert(FileType(Tmp$) <> 1)
	; And the original (also non-existent in this scenario) wasn't
	; created either.
	Assert(FileType(FinalPath$) <> 1)
	CleanupTestFiles()
End Test
