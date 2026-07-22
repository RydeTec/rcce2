Strict
EnableGC

; Source-contract regression for GameServer's deferred death queue. The module
; is server-graph heavy, so this test pins queue ordering without including it.

Function FileSectionContainsSequence3%(Path$, StartNeedle$, EndNeedle$, FirstNeedle$, SecondNeedle$, ThirdNeedle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local InSection = False
	Local Stage = 0
	If F = Null Then F = ReadFile("../" + Path$)
	If F = Null Then F = ReadFile("../../" + Path$)
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If InSection = False
			If Instr(Line$, StartNeedle$) > 0 Then InSection = True
		ElseIf Instr(Line$, EndNeedle$) > 0
			Exit
		ElseIf Stage = 0 And Instr(Line$, FirstNeedle$) > 0
			Stage = 1
		ElseIf Stage = 1 And Instr(Line$, SecondNeedle$) > 0
			Stage = 2
		ElseIf Stage = 2 And Instr(Line$, ThirdNeedle$) > 0
			CloseFile F
			Return True
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Function FileSectionCount%(Path$, StartNeedle$, EndNeedle$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Count = 0
	Local InSection = False
	If F = Null Then F = ReadFile("../" + Path$)
	If F = Null Then F = ReadFile("../../" + Path$)
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return -1
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If InSection = False
			If Instr(Line$, StartNeedle$) > 0 Then InSection = True
		ElseIf Instr(Line$, EndNeedle$) > 0
			Exit
		ElseIf Instr(Line$, Needle$) > 0
			Count = Count + 1
		EndIf
	Wend
	CloseFile F
	Return Count
End Function

Test testDeferredKillQueueRejectsDuplicateActorBeforeAllocation()
	Assert(FileSectionContainsSequence3%("Modules\GameServer.bb", "Function DeferKillActor", "Function ProcessPendingKills", "For PK.PendingKill = Each PendingKill", "If PK\Actor = A Then Return", "PK.PendingKill = New PendingKill") = True)
End Test

Test testUnderwaterDeathsContinueUsingBothDeferredKillCallSites()
	Assert(FileSectionCount%("Modules\GameServer.bb", "Function UpdateActorInstances", "; Fires a projectile from one actor at another", "DeferKillActor(AI, Null)") = 2)
End Test
