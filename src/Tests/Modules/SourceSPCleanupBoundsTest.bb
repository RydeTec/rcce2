Strict
EnableGC

; Regression contract for SourceSP cleanup paths. SourceSP originates in
; project data and scripts, so every fixed AreaInstance\Spawned[999] access
; must retain the existing non-negative check and also reject values above 999.
; The server and BVM modules cannot be included in a standalone Strict test;
; bind the bounded source shape instead.

Function LeadingTabs%(Line$)
	Local Count%, Pos% = 1
	While Pos <= Len(Line$)
		If Mid$(Line$, Pos, 1) <> Chr$(9) Then Exit
		Count = Count + 1
		Pos = Pos + 1
	Wend
	Return Count
End Function

Function SourceSPPositiveCleanupGuardCount%(Path$, Subject$)
	Local F.BBStream = ReadFile(Path$)
	Local GuardPrefix$ = "If " + Subject$ + "\SourceSP > -1"
	Local Count%, Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, GuardPrefix$) > 0 Then Count = Count + 1
	Wend
	CloseFile F
	Return Count
End Function

Function SourceSPCleanupDecrementsAreBounded%(Path$, Subject$, ExpectedCount%)
	Local F.BBStream = ReadFile(Path$)
	Local BoundIndent% = -1, Count%, Line$
	Local BoundNeedle$ = "If " + Subject$ + "\SourceSP <= 999"
	Local DecrementNeedle$ = "\Spawned[" + Subject$ + "\SourceSP] ="
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, BoundNeedle$) > 0 Then BoundIndent = LeadingTabs(Line$)
		If Instr(Line$, DecrementNeedle$) > 0
			If BoundIndent < 0 Or LeadingTabs(Line$) <= BoundIndent
				CloseFile F
				Return False
			EndIf
			Count = Count + 1
		EndIf
		If BoundIndent >= 0 And Instr(Line$, "EndIf") > 0 And LeadingTabs(Line$) <= BoundIndent Then BoundIndent = -1
	Wend
	CloseFile F
	Return Count = ExpectedCount
End Function

Function SourceSPCleanupDetachesAfterBound%(Path$, Subject$, ExpectedCount%)
	Local F.BBStream = ReadFile(Path$)
	Local OuterIndent% = -1, BoundIndent% = -1, BoundClosed%, Count%, Line$
	Local OuterNeedle$ = "If " + Subject$ + "\SourceSP > -1"
	Local BoundNeedle$ = "If " + Subject$ + "\SourceSP <= 999"
	Local DetachNeedle$ = Subject$ + "\SourceSP = -1"
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, OuterNeedle$) > 0
			OuterIndent = LeadingTabs(Line$)
			BoundIndent = -1
			BoundClosed = False
		EndIf
		If OuterIndent >= 0 And Instr(Line$, BoundNeedle$) > 0 Then BoundIndent = LeadingTabs(Line$)
		If BoundIndent >= 0 And Instr(Line$, "EndIf") > 0 And LeadingTabs(Line$) <= BoundIndent
			BoundIndent = -1
			BoundClosed = True
		EndIf
		If Instr(Line$, DetachNeedle$) > 0
			If OuterIndent < 0 Or BoundClosed = False Or LeadingTabs(Line$) <= OuterIndent
				CloseFile F
				Return False
			EndIf
			Count = Count + 1
			OuterIndent = -1
		EndIf
	Wend
	CloseFile F
	Return Count = ExpectedCount
End Function

Test testGameServerBoundsEverySourceSpawnCleanupGuard()
	Assert(SourceSPPositiveCleanupGuardCount%("Modules\GameServer.bb", "A") = 2)
	Assert(SourceSPCleanupDecrementsAreBounded%("Modules\GameServer.bb", "A", 2) = True)
	; The death path frees the actor; the cross-area path must detach after its bound ends.
	Assert(SourceSPCleanupDetachesAfterBound%("Modules\GameServer.bb", "A", 1) = True)
End Test

Test testSetLeaderBoundsSourceSpawnCleanupGuard()
	Assert(SourceSPPositiveCleanupGuardCount%("Modules\ScriptingCommands.bb", "Actor") = 1)
	Assert(SourceSPCleanupDecrementsAreBounded%("Modules\ScriptingCommands.bb", "Actor", 1) = True)
	Assert(SourceSPCleanupDetachesAfterBound%("Modules\ScriptingCommands.bb", "Actor", 1) = True)
End Test
