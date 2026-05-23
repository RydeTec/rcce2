Type LogFile
	Field File
End Type

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

	For L.LogFile = Each LogFile
		If L\File = LogHandle Then Delete L
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