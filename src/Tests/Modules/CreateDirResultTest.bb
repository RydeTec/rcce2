Strict
EnableGC

; ScriptingCommands.bb pulls in the server/world/BVM graph, so this standalone
; regression test pins the bounded CREATEDIR source contract instead. The BVM
; has always advertised an Int result: scripts need a stable success value
; after the existing privileged, path-safe directory operation.

Function FunctionBodyContains%(Path$, FunctionMarker$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local InFunction%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, FunctionMarker$) > 0 Then InFunction = True
		If InFunction = True And Instr(Line$, Needle$) > 0
			CloseFile F
			Return True
		EndIf
		If InFunction = True And Instr(Line$, "End Function") > 0 Then Exit
	Wend
	CloseFile F
	Return False
End Function

Function CreateDirResultHasSafeOrder%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InFunction%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function BVM_CREATEDIR") > 0 Then InFunction = True
		If InFunction = True And Instr(Line$, "Return Result%") > 0
			CloseFile F
			Return False
		EndIf
		If InFunction = True And Instr(Line$, "CreateDir(") > 0 And Stage < 3
			CloseFile F
			Return False
		EndIf
		If InFunction = True And Instr(Line$, "Return FileType(Path$) = 2") > 0 And Stage < 4
			CloseFile F
			Return False
		EndIf
		If InFunction = True And Instr(Line$, "If Not BVM_RequirePrivileged() Then Return 0") > 0 Then Stage = 1
		If InFunction = True And Stage = 1 And Instr(Line$, "If Not BVM_ScriptPathIsSafe(Param1$) Then Return 0") > 0 Then Stage = 2
		If InFunction = True And Stage = 2 And Instr(Line$, "Local Path$ = RCScriptFiles$ + Param1$") > 0 Then Stage = 3
		If InFunction = True And Stage = 3 And Instr(Line$, "CreateDir(Path$)") > 0 Then Stage = 4
		If InFunction = True And Stage = 4 And Instr(Line$, "Return FileType(Path$) = 2") > 0
			CloseFile F
			Return True
		EndIf
		If InFunction = True And Instr(Line$, "End Function") > 0 Then Exit
	Wend
	CloseFile F
	Return False
End Function

Test testCreateDirReportsWhetherTheSafeTargetExists()
	Assert(FunctionBodyContains%("Modules\ScriptingCommands.bb", "Function BVM_CREATEDIR", "If Not BVM_RequirePrivileged() Then Return 0") = True)
	Assert(FunctionBodyContains%("Modules\ScriptingCommands.bb", "Function BVM_CREATEDIR", "If Not BVM_ScriptPathIsSafe(Param1$) Then Return 0") = True)
	Assert(FunctionBodyContains%("Modules\ScriptingCommands.bb", "Function BVM_CREATEDIR", "Local Path$ = RCScriptFiles$ + Param1$") = True)
	Assert(FunctionBodyContains%("Modules\ScriptingCommands.bb", "Function BVM_CREATEDIR", "CreateDir(Path$)") = True)
	Assert(FunctionBodyContains%("Modules\ScriptingCommands.bb", "Function BVM_CREATEDIR", "Return FileType(Path$) = 2") = True)
End Test

Test testCreateDirDoesNotReturnTheUnassignedLegacyResult()
	Assert(FunctionBodyContains%("Modules\ScriptingCommands.bb", "Function BVM_CREATEDIR", "Return Result%") = False)
End Test

Test testCreateDirGuardsTheSafePathBeforeReportingItsResult()
	Assert(CreateDirResultHasSafeOrder%("Modules\ScriptingCommands.bb") = True)
End Test
