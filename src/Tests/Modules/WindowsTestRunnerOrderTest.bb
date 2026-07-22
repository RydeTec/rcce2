Strict
EnableGC

; Source-contract regression for the Windows test runner. The runner is a
; batch entry point, so this focused test reads its ordering contract instead
; of including a runtime module.

Function WindowsRunnerUsesSortedDiscovery%()
	Local F.BBStream = ReadFile("test.bat")
	Local Line$
	Local Stage%
	Local LoopDepth%
	Local ExecutionLoopClosed%
	Local SawFilteredGlob%
	Local SawUnfilteredGlob%
	Local SawRunMarker%
	Local SawPassMarker%
	Local SawFailMarker%
	Local SawPassedCounter%
	Local SawFailedCounter%
	Local SawFailureSummary%
	Local SawFilteredNoMatch%
	Local SawUnfilteredNoMatch%
	Local SawNoMatchGuard%
	If F = Null Then F = ReadFile("..\\test.bat")
	If F = Null Then F = ReadFile("..\\..\\test.bat")
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = Trim$(ReadLine$(F))
		If Instr(Line$, "GLOB=*!FILTER!*.bb") > 0 Then SawFilteredGlob = True
		If Instr(Line$, "GLOB=*.bb") > 0 Then SawUnfilteredGlob = True
		If Instr(Line$, "echo [RUN ] %%~nxf") > 0 Then SawRunMarker = True
		If Instr(Line$, "echo [PASS] %%~nxf") > 0 Then SawPassMarker = True
		If Instr(Line$, "echo [FAIL] %%~nxf") > 0 Then SawFailMarker = True
		If Instr(Line$, "set /a PASSED+=1") > 0 Then SawPassedCounter = True
		If Instr(Line$, "set /a FAILED+=1") > 0 Then SawFailedCounter = True
		If Instr(Line$, "Failed files:") > 0 And Instr(Line$, "echo " + Chr$(34) + "Tests failed" + Chr$(34)) = 0 Then SawFailureSummary = True
		If Instr(Line$, "echo " + Chr$(34) + "Tests failed" + Chr$(34)) > 0 And SawFailureSummary Then SawFailureSummary = 2
		If Instr(Line$, "No test files matched filter") > 0 Then SawFilteredNoMatch = True
		If Instr(Line$, "No test files found") > 0 Then SawUnfilteredNoMatch = True

		; Direct for /R execution leaves the run order up to filesystem
		; enumeration and must not return after this migration.
		If Instr(Line$, "for /R %%f in (!GLOB!) do (") > 0
			CloseFile F
			Return False
		EndIf

		If Stage = 0
			If Instr(Line$, "for /F") > 0 And Instr(Line$, "dir /B /S /A-D") > 0 And Instr(Line$, "!TESTDIR!") > 0 And Instr(Line$, "!GLOB!") > 0 And Instr(Line$, "^| sort") > 0
				Stage = 1
				LoopDepth = 1
			EndIf
		Else
			If Line$ = ")"
				If LoopDepth = 1 Then ExecutionLoopClosed = True
				LoopDepth = LoopDepth - 1
			ElseIf Line$ <> ") else (" And Right$(Line$, 1) = "("
				LoopDepth = LoopDepth + 1
			EndIf

			If Stage = 1
				If LoopDepth > 0 And Instr(Line$, "set /a TOTAL+=1") > 0 Then Stage = 2
			ElseIf Stage = 2
				If LoopDepth > 0 And Instr(Line$, "%BLITZPATH%") > 0 And Instr(Line$, "blitzcc.exe") > 0 And Instr(Line$, " -t -w ") > 0 And Instr(Line$, "%ROOTDIR%") > 0 And Instr(Line$, "%%f") > 0 Then Stage = 3
			ElseIf Stage = 3
				If LoopDepth > 0 And Instr(Line$, "if !errorlevel! equ 0 (") > 0 Then Stage = 4
			ElseIf Stage = 4
				; The no-match condition must remain outside the execution loop;
				; otherwise an empty match set no longer has its established exit.
				If ExecutionLoopClosed And Instr(Line$, "if !TOTAL! equ 0 (") > 0 Then SawNoMatchGuard = True
			EndIf
		EndIf
	Wend

	CloseFile F
	If Stage <> 4 Or Not SawNoMatchGuard Then Return False
	If Not SawFilteredGlob Or Not SawUnfilteredGlob Then Return False
	If Not SawRunMarker Or Not SawPassMarker Or Not SawFailMarker Then Return False
	If Not SawPassedCounter Or Not SawFailedCounter Then Return False
	If SawFailureSummary <> 2 Then Return False
	If Not SawFilteredNoMatch Or Not SawUnfilteredNoMatch Then Return False
	Return True
End Function

Test testWindowsRunnerSortsDiscoveryBeforeCompilerExecution()
	Assert(WindowsRunnerUsesSortedDiscovery() = True)
End Test
