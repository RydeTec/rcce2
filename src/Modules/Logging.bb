Type LogFile
	Field File
End Type

; Starts a log and returns the handle
Function StartLog(Logname$, Append = True)

	If FileType(RootDir$ + "Data\Logs\") <> 2 Then CreateDir RootDir$ + "Data\Logs\"

	If Append = False Or FileType(RootDir$ + "Data\Logs\" + Logname$ + ".txt") <> 1
		F = WriteFile(RootDir$ + "Data\Logs\" + Logname$ + ".txt")
	Else
		F = OpenFile(RootDir$ + "Data\Logs\" + Logname$ + ".txt")
		If F <> 0 Then SeekFile(F, FileSize(RootDir$ + "Data\Logs\" + Logname$ + ".txt"))
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

	WriteLine(LogHandle, Dat$)

	if LogMode > 0
		DebugLog Dat$

		LOG = StartLog("DEBUG")
		WriteLine(LOG, Dat$)
		StopLog(LOG)
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

	For L.LogFile = Each LogFile
		CloseFile(L\File)
	Next
	Delete Each LogFile

End Function