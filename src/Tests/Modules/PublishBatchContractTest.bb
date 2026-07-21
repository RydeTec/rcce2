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

Function CompileForwardingFailsClosed%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Stage% = 0
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Stage = 0 And Instr(Line$, "call " + Chr$(34) + "%ROOTDIR%\compile.bat" + Chr$(34) + " %* || (") > 0 Then Stage = 1
		If Stage = 1 And Instr(Line$, "echo Compilation failed; aborting publish.") > 0 Then Stage = 2
		If Stage = 2 And Instr(Line$, "endlocal") > 0 Then Stage = 3
		If Stage = 3 And Instr(Line$, "exit /b 1") > 0
			CloseFile F
			Return True
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Function CopyRequiredFailsClosed%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Stage% = 0
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Stage = 0 And Instr(Line$, ":CopyRequired") > 0 Then Stage = 1
		If Stage = 1 And Instr(Line$, "xcopy %*") > 0 Then Stage = 2
		If Stage = 2 And Instr(Line$, "if errorlevel 1 (") > 0 Then Stage = 3
		If Stage = 3 And Instr(Line$, "Copy failed; aborting publish.") > 0 Then Stage = 4
		If Stage = 4 And Instr(Line$, "exit /b 1") > 0
			CloseFile F
			Return True
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Function HelpGuardPrecedesReleaseCleanup%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Stage% = 0
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then F = ReadFile("..\\..\\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Stage = 0 And Instr(Line$, "for %%A in (%*) do (") > 0 Then Stage = 1
		If Stage = 1 And Instr(Line$, "if " + Chr$(34) + "%%~A" + Chr$(34) + "==" + Chr$(34) + "-h" + Chr$(34) + " set " + Chr$(34) + "HELP_ARG=%%~A" + Chr$(34)) > 0 Then Stage = 2
		If Stage = 2 And Instr(Line$, "if " + Chr$(34) + "%%~A" + Chr$(34) + "==" + Chr$(34) + "--help" + Chr$(34) + " set " + Chr$(34) + "HELP_ARG=%%~A" + Chr$(34)) > 0 Then Stage = 3
		If Stage = 3 And Instr(Line$, "if defined HELP_ARG goto help") > 0 Then Stage = 4
		If Stage = 4 And Instr(Line$, "goto publish") > 0 Then Stage = 5
		If Stage = 5 And Instr(Line$, ":help") > 0 Then Stage = 6
		If Stage = 6 And Instr(Line$, "call " + Chr$(34) + "%ROOTDIR%\\compile.bat" + Chr$(34) + " %HELP_ARG%") > 0 Then Stage = 7
		If Stage = 7 And Instr(Line$, "set " + Chr$(34) + "HELP_STATUS=%ERRORLEVEL%" + Chr$(34)) > 0 Then Stage = 8
		If Stage = 8 And Instr(Line$, "endlocal & exit /b %HELP_STATUS%") > 0 Then Stage = 9
		If Stage = 9 And Instr(Line$, "if exist " + Chr$(34) + "%ROOTDIR%\\release" + Chr$(34) + " rmdir /S /Q ") > 0
			CloseFile F
			Return True
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Test testPublishForwardsRequestedBuildFlags()
	Assert(CompileForwardingFailsClosed%("publish.bat") = True)
End Test

Test testPublishHelpExitsBeforeReleaseCleanup()
	Assert(HelpGuardPrecedesReleaseCleanup%("publish.bat") = True)
End Test

Test testPublishFailsWhenARequiredPayloadCopyFails()
	Assert(FileOccurrenceCount%("publish.bat", "call :CopyRequired ") = 7)
	Assert(FileContains%("publish.bat", "call :CopyRequired /E /Y /I " + Chr$(34) + "%ROOTDIR%\bin" + Chr$(34) + " " + Chr$(34) + "%ROOTDIR%\release\bin" + Chr$(34) + " || exit /b 1") = True)
	Assert(FileContains%("publish.bat", "call :CopyRequired /E /Y /I " + Chr$(34) + "%ROOTDIR%\bin\ReShade.ini.example" + Chr$(34) + " " + Chr$(34) + "%ROOTDIR%\release\bin\ReShade.ini" + Chr$(34) + " || exit /b 1") = True)
	Assert(FileContains%("publish.bat", "call :CopyRequired /Y " + Chr$(34) + "%ROOTDIR%\Project Manager.exe" + Chr$(34) + " " + Chr$(34) + "%ROOTDIR%\release\" + Chr$(34) + " || exit /b 1") = True)
	Assert(FileContains%("publish.bat", "call :CopyRequired /E /Y /I " + Chr$(34) + "%ROOTDIR%\data" + Chr$(34) + " " + Chr$(34) + "%ROOTDIR%\release\data" + Chr$(34) + " || exit /b 1") = True)
	Assert(FileContains%("publish.bat", "call :CopyRequired /E /Y /I " + Chr$(34) + "%ROOTDIR%\res" + Chr$(34) + " " + Chr$(34) + "%ROOTDIR%\release\res" + Chr$(34) + " || exit /b 1") = True)
	Assert(FileContains%("publish.bat", "call :CopyRequired /E /Y /I " + Chr$(34) + "%ROOTDIR%\docs" + Chr$(34) + " " + Chr$(34) + "%ROOTDIR%\release\docs" + Chr$(34) + " || exit /b 1") = True)
	Assert(FileContains%("publish.bat", "call :CopyRequired /E /Y /I " + Chr$(34) + "%ROOTDIR%\extras\Freemake" + Chr$(34) + " " + Chr$(34) + "%ROOTDIR%\release\extras\Freemake" + Chr$(34) + " || exit /b 1") = True)
	Assert(CopyRequiredFailsClosed%("publish.bat") = True)
End Test

Test testPublishKeepsRecentProjectStateOutOfTheRelease()
	Assert(FileContains%("publish.bat", "if exist " + Chr$(34) + "%ROOTDIR%\release\res\Recent.dat" + Chr$(34) + " del " + Chr$(34) + "%ROOTDIR%\release\res\Recent.dat" + Chr$(34)) = True)
	Assert(RecentCleanupFollowsRequiredCopies%("publish.bat") = True)
End Test
