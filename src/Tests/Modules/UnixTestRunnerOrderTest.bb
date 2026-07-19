Strict
EnableGC

; Source-contract regression for the Unix runner's contributor-visible test
; ordering. This stays standalone because the runner is a shell entry point,
; not a Blitz module that a Strict test can Include.

Function FileContains%(Path$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then F = ReadFile("..\\..\\" + Path$)
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

Function FileContainsSequence%(Path$, FirstNeedle$, SecondNeedle$, ThirdNeedle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Stage = 0
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then F = ReadFile("..\\..\\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Stage = 0 And Instr(Line$, FirstNeedle$) > 0 Then Stage = 1
		If Stage = 1 And Instr(Line$, SecondNeedle$) > 0 Then Stage = 2
		If Stage = 2 And Instr(Line$, ThirdNeedle$) > 0
			CloseFile F
			Return True
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Test testUnixRunnerSortsNulDelimitedDiscoveryBeforeExecution()
	Assert(FileContains%("test.sh", "export LC_ALL=C") = True)
	Assert(FileContainsSequence%("test.sh", "find " + Chr$(34) + "${TESTDIR}" + Chr$(34) + " -type f -name '*.bb' -print0", "for ((i = 1; i < ${#FILES[@]}; i++)); do", "for f in " + Chr$(34) + "${FILES[@]}" + Chr$(34) + "; do") = True)
End Test

Test testUnixRunnerRetainsTheExistingFileExecutionLoop()
	Assert(FileContains%("test.sh", "for f in " + Chr$(34) + "${FILES[@]}" + Chr$(34) + "; do") = True)
End Test
