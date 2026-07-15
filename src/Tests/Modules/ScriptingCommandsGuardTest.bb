Strict
EnableGC

; Regression contract for BVM_SETLEADER's no-leader patrol fallback.
; ScriptingCommands.bb cannot be included by a standalone Strict test because
; it pulls in the server, world, and BVM graphs. Pin the bounded source shape
; instead: BlitzForge evaluates And without short-circuiting, so a combined
; AInstance/Area condition still dereferences a stale AreaInstance.

Function SectionContains%(Path$, StartMarker$, EndMarker$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local InSection%
	Local Line$
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, StartMarker$) > 0 Then InSection = True
		If InSection = True And Instr(Line$, EndMarker$) > 0 Then Exit
		If InSection = True And Instr(Line$, Needle$) > 0
			CloseFile F
			Return True
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Function SectionHasOrderedPatrolGuard%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		; Anchor after the no-leader branch initializes Found. An earlier
		; AreaInstance guard in the set-pet branch must not satisfy this test.
		If Instr(Line$, "Found = False") > 0 Then Stage = 1
		If Stage = 1 And Instr(Line$, "If AInstance <> Null") > 0 Then Stage = 2
		If Stage = 2 And Instr(Line$, "If AInstance\\Area <> Null") > 0 Then Stage = 3
		If Stage = 3 And Instr(Line$, "For i = 0 To 249") > 0 Then Stage = 4
		If Stage = 4 And Instr(Line$, "AInstance\\Area\\PrevWaypoint") > 0 Then Stage = 5
		If Stage = 5 And Instr(Line$, "AInstance\\Area\\WaypointX#") > 0 Then Stage = 6
		If Stage = 6 And Instr(Line$, "AInstance\\Area\\WaypointZ#") > 0
			CloseFile F
			Return True
		EndIf
		If Stage > 0 And Instr(Line$, "; Die if no waypoint available") > 0 Then Exit
	Wend
	CloseFile F
	Return False
End Function

Test testSetLeaderRejectsUnsafeCombinedNestedAreaGuard()
	Assert(SectionContains%("Modules\\ScriptingCommands.bb", "Function BVM_SETLEADER", "Function BVM_ACTORLEADER", "AInstance <> Null And AInstance\\Area") = False)
End Test

Test testSetLeaderPatrolReadsAreaOnlyAfterNestedGuards()
	Assert(SectionHasOrderedPatrolGuard%("Modules\\ScriptingCommands.bb") = True)
End Test
