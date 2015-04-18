;##############################################################################################################################
; Realm Crafter version 1.10																									
; Copyright (C) 2007 Solstar Games, LLC. All rights reserved																	
; contact@solstargames.com																																																		
;																																																																#
; Programmer: Rob Williams																										
; Program: Realm Crafter Actors module
;																																
;This is a licensed product:
;BY USING THIS SOURCECODE, YOU ARE CONFIRMING YOUR ACCEPTANCE OF THE SOFTWARE AND AGREEING TO BECOME BOUND BY THE TERMS OF 
;THIS AGREEMENT. IF YOU DO NOT AGREE TO BE BOUND BY THESE TERMS, THEN DO NOT USE THE SOFTWARE.
;																		
;Licensee may NOT: 
; (i)   create any derivative works of the Engine, including translations Or localizations, other than Games;
; (ii)  redistribute, encumber, sell, rent, lease, sublicense, Or otherwise transfer rights To the Engine; or
; (iii) remove Or alter any trademark, logo, copyright Or other proprietary notices, legends, symbols Or labels in the Engine.
; (iv)   licensee may Not distribute the source code Or documentation To the engine in any manner, unless recipient also has a 
;       license To the Engine.													
; (v)  use the Software to develop any software or other technology having the same primary function as the Software, 
;       including but not limited to using the Software in any development or test procedure that seeks to develop like 
;       software or other technology, or to determine if such software or other technology performs in a similar manner as the
;       Software																																
;##############################################################################################################################
; Realm Crafter Logging module by Rob W (rottbott@hotmail.com), August 2004

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