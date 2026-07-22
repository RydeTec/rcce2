Strict
EnableGC

; CI actions are an external dependency boundary. Keep their reviewed commit
; identities explicit so a moved tag cannot silently change build behavior.

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
	Local Count = 0
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

Function JobHasImmediateTimeout%(Path$, Job$, Runner$, Timeout$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Line$ = "  " + Job$ + ":"
			While Not Eof(F)
				Line$ = ReadLine$(F)
				If Line$ = "    " + Runner$
					If Eof(F)
						CloseFile F
						Return False
					EndIf
					Line$ = ReadLine$(F)
					CloseFile F
					Return Line$ = "    " + Timeout$
				EndIf
				If Left$(Line$, 2) = "  " And Left$(Line$, 4) <> "    " Then Exit
			Wend
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Test testCIActionReferencesUseReviewedImmutablePins()
	Assert(FileOccurrenceCount%(".github\workflows\ci.yml", "uses:") = 8)
	Assert(FileOccurrenceCount%(".github\workflows\ci.yml", "uses: actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5") = 2)
	Assert(FileOccurrenceCount%(".github\workflows\ci.yml", "uses: actions/cache@0057852bfaa89a56745cba8c7296529d2fc39830") = 3)
	Assert(FileOccurrenceCount%(".github\workflows\ci.yml", "uses: microsoft/setup-msbuild@6fb02220983dee41ce7ae257b6f4d8f9bf5ed4ce") = 1)
	Assert(FileOccurrenceCount%(".github\workflows\ci.yml", "uses: dtolnay/rust-toolchain@98effd2fc0b766278e30ab86762dd5e9a8531399") = 2)
	Assert(FileContains%(".github\workflows\ci.yml", "uses: actions/checkout@v4") = False)
	Assert(FileContains%(".github\workflows\ci.yml", "uses: actions/cache@v4") = False)
	Assert(FileContains%(".github\workflows\ci.yml", "uses: microsoft/setup-msbuild@v2") = False)
	Assert(FileContains%(".github\workflows\ci.yml", "uses: dtolnay/rust-toolchain@1.85.0") = False)
End Test

Test testCIJobsCapDurationImmediatelyAfterRunner()
	Assert(FileOccurrenceCount%(".github\workflows\ci.yml", "timeout-minutes: 30") = 2)
	Assert(JobHasImmediateTimeout%(".github\workflows\ci.yml", "build-and-test", "runs-on: windows-latest", "timeout-minutes: 30"))
	Assert(JobHasImmediateTimeout%(".github\workflows\ci.yml", "rust-server", "runs-on: ubuntu-latest", "timeout-minutes: 30"))
End Test
