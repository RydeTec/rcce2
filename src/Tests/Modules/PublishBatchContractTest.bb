Strict
EnableGC

; publish.bat is a committed contributor/release interface. Keep this source
; contract standalone so it does not require the compiler or runtime graph.

Function FileContains%(Path$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, Needle$) > 0
			CloseFile F
			Return True
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Function FileOccurrenceCount%(Path$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Count% = 0
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return 0
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, Needle$) > 0 Then Count = Count + 1
	Wend
	CloseFile F
	Return Count
End Function

Function RecentCleanupFollowsRequiredCopies%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local CopyCount% = 0
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "call :CopyRequired ") > 0 Then CopyCount = CopyCount + 1
		If Instr(Line$, "if exist " + Chr$(34) + "%ROOTDIR%\release\res\Recent.dat" + Chr$(34) + " del ") > 0
			CloseFile F
			Return CopyCount = 7
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Test testPublishForwardsRequestedBuildFlags()
	Assert(FileContains%("publish.bat", "call " + Chr$(34) + "%ROOTDIR%\compile.bat" + Chr$(34) + " %* || (") = True)
End Test

Test testPublishFailsWhenARequiredPayloadCopyFails()
	Assert(FileOccurrenceCount%("publish.bat", "call :CopyRequired ") = 7)
	Assert(FileOccurrenceCount%("publish.bat", " || exit /b 1") = 7)
	Assert(FileContains%("publish.bat", ":CopyRequired") = True)
	Assert(FileContains%("publish.bat", "xcopy %*") = True)
	Assert(FileContains%("publish.bat", "if errorlevel 1 (") = True)
	Assert(FileContains%("publish.bat", "Copy failed; aborting publish.") = True)
End Test

Test testPublishKeepsRecentProjectStateOutOfTheRelease()
	Assert(FileContains%("publish.bat", "if exist " + Chr$(34) + "%ROOTDIR%\release\res\Recent.dat" + Chr$(34) + " del " + Chr$(34) + "%ROOTDIR%\release\res\Recent.dat" + Chr$(34)) = True)
	Assert(RecentCleanupFollowsRequiredCopies%("publish.bat") = True)
End Test
