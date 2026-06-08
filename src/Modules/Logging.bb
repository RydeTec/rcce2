Type LogFile
	Field File
End Type

; --- Atomic save helper ----------------------------------------------------
;
; Direct `WriteFile(path)` truncates immediately. A crash, power loss, or
; disk-full between the truncate and the matching CloseFile leaves the file
; empty or partially written. Round 3's audit found this pattern in
; SaveAccounts, SaveSuperGlobals, SaveDroppedItems, and SaveEnvironment —
; every one of them risks total account / world-state loss on a single
; mistimed crash.
;
; Blitz3D doesn't expose MoveFile, so we approximate atomicity with a
; sequence that preserves a recovery copy:
;
;   1. caller writes to a .tmp file via SafeWriteOpen(...).
;   2. caller calls SafeWriteCommit(...) which closes the temp, demotes the
;      current production file to .bak (if it exists), then promotes .tmp
;      to production. The .tmp file is only deleted after the production
;      copy is in place, so any crash mid-commit leaves either the .bak
;      (recovery) or the .tmp (manual promote) usable.
;
; Returns the temp path SafeWriteOpen produced, plus a file handle the
; caller writes into. SafeWriteCommit closes the handle internally.
; SafeWriteAbort cleans up the temp on error paths.

Function SafeWriteOpen$(FinalPath$)
	Local Temp$ = FinalPath$ + ".tmp"
	Return Temp$
End Function

; Returns True on success, False on any failure (in which case the caller
; should treat the save as not having happened — the production file is
; still the previous version, and the temp has been cleaned up).
Function SafeWriteCommit%(TempPath$, FinalPath$, F)
	If F <> 0 Then CloseFile(F)

	; Make sure the temp actually got written. Zero-length temps mean the
	; write path failed silently; refuse to promote.
	If FileType(TempPath$) <> 1
		WriteLog(MainLog, "SafeWriteCommit: temp missing for " + FinalPath$)
		Return False
	EndIf
	If FileSize(TempPath$) = 0
		WriteLog(MainLog, "SafeWriteCommit: temp empty for " + FinalPath$ + ", refusing to promote")
		DeleteFile(TempPath$)
		Return False
	EndIf

	; Demote the current production file to .bak (one cycle of backup).
	Local Bak$ = FinalPath$ + ".bak"
	If FileType(FinalPath$) = 1
		If FileType(Bak$) = 1 Then DeleteFile(Bak$)
		CopyFile(FinalPath$, Bak$)
		DeleteFile(FinalPath$)
	EndIf

	; Promote the temp into production.
	CopyFile(TempPath$, FinalPath$)
	If FileType(FinalPath$) <> 1
		; Promotion failed catastrophically — try to roll back from .bak.
		WriteLog(MainLog, "SafeWriteCommit: promote failed for " + FinalPath$ + ", rolling back from " + Bak$)
		If FileType(Bak$) = 1 Then CopyFile(Bak$, FinalPath$)
		Return False
	EndIf
	DeleteFile(TempPath$)
	Return True
End Function

Function SafeWriteAbort(TempPath$)
	If FileType(TempPath$) = 1 Then DeleteFile(TempPath$)
End Function

; --- Bounded ReadString --------------------------------------------------
;
; Blitz3D's ReadString$ reads a 4-byte length prefix and then that many
; bytes. A truncated, corrupted, or hostile save file with a wild length
; (negative, or e.g. 0x7FFFFFFF) makes the runtime try to allocate gigs
; of memory and read past EOF -- which Blitz silently zero-fills,
; producing huge empty-padding strings that then propagate through the
; rest of the load.
;
; ReadBoundedString$ peeks the length prefix as an Int, refuses lengths
; outside [0, MaxLen], and reads the bytes manually. On a bad length it
; logs once via MainLog (caller is expected to bail the surrounding load
; if they care about partial-state corruption -- the function itself
; just returns "" so the caller can detect the failure).
Function ReadBoundedString$(F, MaxLen)
	If F = 0 Then Return ""
	Local L = ReadInt(F)
	If L < 0 Or L > MaxLen
		WriteLog(MainLog, "ReadBoundedString: length " + L + " out of range (cap " + MaxLen + "), refusing to read")
		Return ""
	EndIf
	If L = 0 Then Return ""
	Local s$ = ""
	Local i
	For i = 1 To L
		If Eof(F) Then Exit
		s$ = s$ + Chr$(ReadByte(F))
	Next
	Return s$
End Function

; Cached debug-log handle. WriteLog used to call StartLog+StopLog every
; entry under LogMode>0, which opens, writes one line, and closes the
; DEBUG log per call — a serious IO load. Hold the handle for the life
; of the process instead. 0 = not yet opened, -1 = open failed (don't retry).
Global DebugLogHandle = 0

; Starts a log and returns the handle
Function StartLog(Logname$, Append = True)

	If FileType("Data\Logs\") <> 2 Then CreateDir "Data\Logs\"

	If Append = False Or FileType("Data\Logs\" + Logname$ + ".txt") <> 1
		F = WriteFile("Data\Logs\" + Logname$ + ".txt")
	Else
		F = OpenFile("Data\Logs\" + Logname$ + ".txt")
		If F <> 0 Then SeekFile(F, FileSize("Data\Logs\" + Logname$ + ".txt"))
	EndIf
	If F <> 0
		L.LogFile = New LogFile
		L\File = F
	EndIf
	Return(F)

End Function

; Adds an entry to a log file
Function WriteLog(LogHandle, Dat$, Timestamp = True, Datestamp = False)

	If Timestamp = True Then Dat$ = "[" + LSet$(CurrentTime$(), 8) + "]  " + Dat$
	If Datestamp = True Then Dat$ = "[" + LSet$(CurrentDate$(), 11) + "]  " + Dat$

	If LogHandle <> 0 Then WriteLine(LogHandle, Dat$)

	if LogMode > 0
		DebugLog Dat$

		; Reuse a cached DEBUG log handle. -1 means a previous StartLog
		; failed (e.g. disk full, Data\Logs read-only) — don't keep
		; retrying every WriteLog call. 0 means we haven't tried yet.
		If DebugLogHandle = 0 Then DebugLogHandle = StartLog("DEBUG")
		If DebugLogHandle = 0 Then DebugLogHandle = -1
		If DebugLogHandle > 0 Then WriteLine(DebugLogHandle, Dat$)
	EndIf

End Function

; Closes a log file
Function StopLog(LogHandle)

	; Exit immediately after the matching Delete -- For-Each
	; iteration with a Delete in the body corrupts the cursor
	; (documented in CLAUDE.md, #247). Each LogHandle has at
	; most one matching LogFile, so the loop should never need
	; to continue after the Delete; the original `Next` was a
	; latent crash waiting for a future caller that might
	; re-enter the loop body before the Delete settled.
	For L.LogFile = Each LogFile
		If L\File = LogHandle
			Delete L
			Exit
		EndIf
	Next
	CloseFile(LogHandle)

End Function

; Closes all open log files
Function CloseAllLogs()

	If DebugLogHandle > 0
		CloseFile(DebugLogHandle)
		DebugLogHandle = 0
	EndIf

	For L.LogFile = Each LogFile
		CloseFile(L\File)
	Next
	Delete Each LogFile

End Function