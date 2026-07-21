Strict
EnableGC

; Source-contract regression for the Linux CI syntax check over the
; contributor-facing Unix shell entry points. The workflow is not a Blitz
; module, so this standalone test reads its YAML directly.

Function FileContains%(Path$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	If F = Null Then F = ReadFile("../" + Path$)
	If F = Null Then F = ReadFile("../../" + Path$)
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

Function FileContainsOrderedNonComment%(Path$, FirstNeedle$, SecondNeedle$, ThirdNeedle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Stage = 0
	If F = Null Then F = ReadFile("../" + Path$)
	If F = Null Then F = ReadFile("../../" + Path$)
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then F = ReadFile("..\\..\\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = Trim$(ReadLine$(F))
		If Left$(Line$, 1) <> "#"
			If Stage = 0
				If Instr(Line$, FirstNeedle$) > 0 Then Stage = 1
			ElseIf Stage = 1
				If Instr(Line$, SecondNeedle$) > 0 Then Stage = 2
			ElseIf Stage = 2
				If Instr(Line$, ThirdNeedle$) > 0
					CloseFile F
					Return True
				EndIf
			EndIf
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Test testLinuxCISyntaxGateCoversUnixEntrypoints()
	Assert(FileContains%(".github/workflows/ci.yml", "bash -n compile.sh test.sh scripts/*.sh") = True)
End Test

Test testLinuxCISyntaxGateRunsBeforeRustSetup()
	Assert(FileContainsOrderedNonComment%(".github/workflows/ci.yml", "- name: Validate Unix shell syntax", "bash -n compile.sh test.sh scripts/*.sh", "- name: Set up Rust 1.85.0 (server)") = True)
End Test
