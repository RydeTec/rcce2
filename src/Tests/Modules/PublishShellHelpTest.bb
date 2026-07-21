Strict
EnableGC

; Source-contract regression for the Unix release packager. A help request is
; an inspection action and must return before release/ is replaced.

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

Function HelpGuardPrecedesCleanup%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Stage = 0
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then F = ReadFile("..\\..\\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Stage = 0
			If Instr(Line$, "for arg in " + Chr$(34) + "$@" + Chr$(34) + "; do") > 0 Then Stage = 1
		ElseIf Stage = 1
			If Instr(Line$, "-h|--help)") > 0 Then Stage = 2
		ElseIf Stage = 2
			If Instr(Line$, "exit 0") > 0 Then Stage = 3
		ElseIf Stage = 3
			If Instr(Line$, Chr$(34) + "${ROOTDIR}/compile.sh" + Chr$(34) + " " + Chr$(34) + "$@" + Chr$(34)) > 0 Then Stage = 4
		ElseIf Stage = 4
			If Instr(Line$, "rm -rf " + Chr$(34) + "${RELEASE_DIR}" + Chr$(34)) > 0
				CloseFile F
				Return True
			EndIf
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Test testPublishHelpExitsBeforeReleaseCleanup()
	Assert(HelpGuardPrecedesCleanup%("publish.sh") = True)
End Test

Test testPublishRetainsNormalArgumentForwarding()
	Assert(FileContains%("publish.sh", Chr$(34) + "${ROOTDIR}/compile.sh" + Chr$(34) + " " + Chr$(34) + "$@" + Chr$(34)) = True)
End Test
