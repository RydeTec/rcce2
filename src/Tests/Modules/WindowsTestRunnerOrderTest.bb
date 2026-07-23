Strict
EnableGC

; Source-contract regression for the Windows test runner. The runner is a
; batch entry point, so this focused test reads its ordering contract instead
; of including a runtime module.

Function WindowsRunnerUsesSortedDiscovery%()
	Local F.BBStream = ReadFile("test.bat")
	Local Line$
	Local Stage%
	Local ExecutionDepth%
	Local ExecutionLoopClosed%
	Local NoMatchDepth%
	Local NoMatchExit%
	Local FailureDepth%
	Local FailureExit%
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

		; Direct for /R execution leaves the run order up to filesystem
		; enumeration and must not return after this migration.
		If Instr(Line$, "for /R %%f in (!GLOB!) do (") > 0
			CloseFile F
			Return False
		EndIf

		If Stage = 0
			If Instr(Line$, "GLOB=*!FILTER!*.bb") > 0 Then SawFilteredGlob = True
			If Instr(Line$, "GLOB=*.bb") > 0 Then SawUnfilteredGlob = True
			If Instr(Line$, "for /F") > 0 And Instr(Line$, "dir /B /S /A-D") > 0 And Instr(Line$, "!TESTDIR!") > 0 And Instr(Line$, "!GLOB!") > 0 And Instr(Line$, "^| sort") > 0
				Stage = 1
				ExecutionDepth = 1
			EndIf
		ElseIf Stage = 4 And ExecutionLoopClosed
			; Once the outer for /F closes, the next no-match branch must
			; begin outside it rather than being counted as another loop body.
			If Instr(Line$, "if !TOTAL! equ 0 (") > 0
				Stage = 5
				NoMatchDepth = 1
				SawNoMatchGuard = True
			EndIf
		ElseIf Stage >= 1 And Stage <= 4
			If Line$ = ")"
				If ExecutionDepth = 1 Then ExecutionLoopClosed = True
				ExecutionDepth = ExecutionDepth - 1
			ElseIf Line$ <> ") else (" And Right$(Line$, 1) = "("
				ExecutionDepth = ExecutionDepth + 1
			EndIf

			If ExecutionDepth > 0
				If Instr(Line$, "echo [RUN ] %%~nxf") > 0 Then SawRunMarker = True
				If Instr(Line$, "echo [PASS] %%~nxf") > 0 Then SawPassMarker = True
				If Instr(Line$, "echo [FAIL] %%~nxf") > 0 Then SawFailMarker = True
				If Instr(Line$, "set /a PASSED+=1") > 0 Then SawPassedCounter = True
				If Instr(Line$, "set /a FAILED+=1") > 0 Then SawFailedCounter = True

				If Stage = 1 And Instr(Line$, "set /a TOTAL+=1") > 0 Then Stage = 2
				If Stage = 2 And Instr(Line$, "%BLITZPATH%") > 0 And Instr(Line$, "blitzcc.exe") > 0 And Instr(Line$, " -t -w ") > 0 And Instr(Line$, "%ROOTDIR%") > 0 And Instr(Line$, "%%f") > 0 Then Stage = 3
				If Stage = 3 And Instr(Line$, "if !errorlevel! equ 0 (") > 0 Then Stage = 4
			EndIf
		ElseIf Stage = 5
			If Line$ = ")"
				NoMatchDepth = NoMatchDepth - 1
			ElseIf Line$ <> ") else (" And Right$(Line$, 1) = "("
				NoMatchDepth = NoMatchDepth + 1
			EndIf
			If NoMatchDepth > 0
				If Instr(Line$, "No test files matched filter") > 0 Then SawFilteredNoMatch = True
				If Instr(Line$, "No test files found") > 0 Then SawUnfilteredNoMatch = True
				If Instr(Line$, "exit /b 1") > 0 Then NoMatchExit = True
			EndIf
			If NoMatchDepth = 0 And NoMatchExit And SawFilteredNoMatch And SawUnfilteredNoMatch Then Stage = 6
		ElseIf Stage = 6
			If Instr(Line$, "if !FAILED! gtr 0 (") > 0
				Stage = 7
				FailureDepth = 1
			EndIf
		ElseIf Stage = 7
			If Line$ = ")"
				FailureDepth = FailureDepth - 1
			ElseIf Line$ <> ") else (" And Right$(Line$, 1) = "("
				FailureDepth = FailureDepth + 1
			EndIf
			If FailureDepth > 0
				If Instr(Line$, "Failed files:") > 0 Then SawFailureSummary = True
				If Instr(Line$, "echo " + Chr$(34) + "Tests failed" + Chr$(34)) > 0 And SawFailureSummary Then SawFailureSummary = 2
				If Instr(Line$, "exit /b 1") > 0 Then FailureExit = True
			EndIf
		EndIf
	Wend

	CloseFile F
	If Stage <> 7 Then Return False
	If SawNoMatchGuard = False Or NoMatchExit = False Then Return False
	If SawFilteredGlob = False Or SawUnfilteredGlob = False Then Return False
	If SawRunMarker = False Or SawPassMarker = False Or SawFailMarker = False Then Return False
	If SawPassedCounter = False Or SawFailedCounter = False Then Return False
	If SawFailureSummary <> 2 Then Return False
	If FailureExit = False Then Return False
	If SawFilteredNoMatch = False Or SawUnfilteredNoMatch = False Then Return False
	Return True
End Function

Test testWindowsRunnerSortsDiscoveryBeforeCompilerExecution()
	Assert(WindowsRunnerUsesSortedDiscovery() = True)
End Test
