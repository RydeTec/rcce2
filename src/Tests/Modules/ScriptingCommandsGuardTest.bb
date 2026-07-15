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
	If F = Null Then F = ReadFile("..\" + Path$)
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

Function LeadingTabs%(Line$)
	Local Count%, Pos% = 1
	While Pos <= Len(Line$)
		If Mid$(Line$, Pos, 1) <> Chr$(9) Then Exit
		Count = Count + 1
		Pos = Pos + 1
	Wend
	Return Count
End Function

Function SectionHasOrderedPatrolGuard%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Stage%, InstanceIndent%, AreaIndent%
	Local SawPrev%, SawX%, SawZ%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		; Anchor after the no-leader branch initializes Found. An earlier
		; AreaInstance guard in the set-pet branch must not satisfy this test.
		If Instr(Line$, "Found = False") > 0 Then Stage = 1
		If Stage = 1 And Instr(Line$, "If AInstance <> Null") > 0
			InstanceIndent = LeadingTabs(Line$)
			Stage = 2
		EndIf
		If Stage = 2 And Instr(Line$, "If AInstance\Area <> Null") > 0
			AreaIndent = LeadingTabs(Line$)
			If AreaIndent <= InstanceIndent Then Return False
			Stage = 3
		EndIf
		If Stage = 3 And Instr(Line$, "AInstance\Area\PrevWaypoint") > 0
			If LeadingTabs(Line$) <= AreaIndent Then Return False
			SawPrev = True
		EndIf
		If Stage = 3 And Instr(Line$, "AInstance\Area\WaypointX#") > 0
			If LeadingTabs(Line$) <= AreaIndent Then Return False
			SawX = True
		EndIf
		If Stage = 3 And Instr(Line$, "AInstance\Area\WaypointZ#") > 0
			If LeadingTabs(Line$) <= AreaIndent Then Return False
			SawZ = True
		EndIf
		If SawPrev = True And SawX = True And SawZ = True
			CloseFile F
			Return True
		EndIf
		If Stage = 3 And Instr(Line$, "EndIf") > 0 And LeadingTabs(Line$) <= AreaIndent Then Return False
		If Stage > 0 And Instr(Line$, "; Die if no waypoint available") > 0 Then Exit
	Wend
	CloseFile F
	Return False
End Function

Test testSetLeaderRejectsUnsafeCombinedNestedAreaGuard()
	Assert(SectionContains%("Modules\ScriptingCommands.bb", "Function BVM_SETLEADER", "Function BVM_ACTORLEADER", "AInstance <> Null And AInstance\Area") = False)
End Test

Test testSetLeaderPatrolReadsAreaOnlyAfterNestedGuards()
	Assert(SectionHasOrderedPatrolGuard%("Modules\ScriptingCommands.bb") = True)
End Test
