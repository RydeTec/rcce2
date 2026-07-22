Strict
EnableGC

; Source-contract regression for the Windows CI parse-only gate over the
; contributor-facing graphics diagnostic. The workflow itself is not a Blitz
; module, so keep this focused test independent by reading the YAML directly.

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

Function FileContainsOrderedNonComment%(Path$, StartNeedle$, FirstNeedle$, SecondNeedle$, ThirdNeedle$, FourthNeedle$)
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
				If Instr(Line$, StartNeedle$) > 0 Then Stage = 1
			ElseIf Stage = 1
				If Instr(Line$, FirstNeedle$) > 0 Then Stage = 2
			ElseIf Stage = 2
				If Instr(Line$, SecondNeedle$) > 0 Then Stage = 3
			ElseIf Stage = 3
				If Instr(Line$, ThirdNeedle$) > 0 Then Stage = 4
			ElseIf Stage = 4
				If Instr(Line$, FourthNeedle$) > 0
					CloseFile F
					Return True
				EndIf
			EndIf
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Test testWindowsCIParsesTheGfxProbeWithoutExecutingIt()
	Local Workflow$ = ".github/workflows/ci.yml"
	Assert(FileContains%(Workflow$, "- name: Validate PowerShell syntax") = True)
	Assert(FileContains%(Workflow$, "shell: pwsh") = True)
	Assert(FileContains%(Workflow$, "[System.Management.Automation.Language.Parser]::ParseFile") = True)
	Assert(FileContains%(Workflow$, "scripts/gfxprobe_loop.ps1") = True)
	Assert(FileContains%(Workflow$, "if ($parseErrors.Count -gt 0)") = True)
	Assert(FileContains%(Workflow$, "exit 1") = True)
	Assert(FileContains%(Workflow$, "Start-Process scripts/gfxprobe_loop.ps1") = False)
End Test

Test testWindowsCIParsesGfxProbeBeforeCompilingTheProject()
	Assert(FileContainsOrderedNonComment%(".github/workflows/ci.yml", "build-and-test:", "- name: Validate PowerShell syntax", "shell: pwsh", "scripts/gfxprobe_loop.ps1", "- name: Compile project") = True)
End Test
