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
				If Instr(Line$, "set /a TOTAL+=1") > 0 Then Stage = 2
			ElseIf Stage = 2
				If Instr(Line$, "%BLITZPATH%") > 0 And Instr(Line$, "blitzcc.exe") > 0 And Instr(Line$, " -t -w ") > 0 And Instr(Line$, "%ROOTDIR%") > 0 And Instr(Line$, "%%f") > 0 Then Stage = 3
			ElseIf Stage = 3
				If Instr(Line$, "if !errorlevel! equ 0 (") > 0 Then Stage = 4
			ElseIf Stage = 4
				; The no-match condition must remain outside the execution loop;
				; otherwise an empty match set no longer has its established exit.
				If ExecutionLoopClosed And Instr(Line$, "if !TOTAL! equ 0 (") > 0
					CloseFile F
					Return True
				EndIf
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Test testWindowsRunnerSortsDiscoveryBeforeCompilerExecution()
	Assert(WindowsRunnerUsesSortedDiscovery() = True)
End Test
