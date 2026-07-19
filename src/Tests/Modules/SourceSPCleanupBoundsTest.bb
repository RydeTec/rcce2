Strict
EnableGC

; Regression contract for SourceSP cleanup paths. SourceSP originates in
; project data and scripts, so every fixed AreaInstance\Spawned[999] access
; must retain the existing non-negative check and also reject values above 999.
; The server and BVM modules cannot be included in a standalone Strict test;
; bind the bounded source shape instead.

Function SourceSPCleanupGuardsAreBounded%(Path$, Subject$, ExpectedCount%)
	Local F.BBStream = ReadFile(Path$)
	Local GuardPrefix$ = "If " + Subject$ + "\SourceSP > -1"
	Local BoundNeedle$ = "And " + Subject$ + "\SourceSP <= 999"
	Local Count%, Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, GuardPrefix$) > 0
			If Instr(Line$, BoundNeedle$) = 0
				CloseFile F
				Return False
			EndIf
			Count = Count + 1
		EndIf
	Wend
	CloseFile F
	Return Count = ExpectedCount
End Function

Function SourceSPCleanupDetachCount%(Path$, DetachNeedle$)
	Local F.BBStream = ReadFile(Path$)
	Local Count%, Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return -1
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, DetachNeedle$) > 0 Then Count = Count + 1
	Wend
	CloseFile F
	Return Count
End Function

Test testGameServerBoundsEverySourceSpawnCleanupGuard()
	Assert(SourceSPCleanupGuardsAreBounded%("Modules\GameServer.bb", "A", 2) = True)
	; The death path frees the actor; the cross-area path must still detach it.
	Assert(SourceSPCleanupDetachCount%("Modules\GameServer.bb", "A\SourceSP = -1") = 1)
End Test

Test testSetLeaderBoundsSourceSpawnCleanupGuard()
	Assert(SourceSPCleanupGuardsAreBounded%("Modules\ScriptingCommands.bb", "Actor", 1) = True)
	Assert(SourceSPCleanupDetachCount%("Modules\ScriptingCommands.bb", "Actor\SourceSP = -1") = 1)
End Test
