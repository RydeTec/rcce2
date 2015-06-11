Type LogFile
	Field File
End Type

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

	WriteLine(LogHandle, Dat$)

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