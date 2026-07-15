Strict
EnableGC

; Regression test for the three Server.bb paths that resolve an actor's
; AreaInstance during a stale-handle window. Server.bb cannot be included in
; a standalone Strict test because it boots the complete server/UI graph, so
; this bounded source contract pins the required nested Null guards directly.
;
; BlitzForge evaluates `And` non-short-circuit. Therefore
; `AInstance <> Null And AInstance\Area = ...` still dereferences the Null
; instance. Each production section must first guard AInstance, then inspect
; its Area only inside that branch.

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

Function SectionUsesNestedAreaGuard%(Path$, StartMarker$, EndMarker$, AreaNeedle$)
	Local F.BBStream = ReadFile(Path$)
	Local InSection%, SawInstanceGuard%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, StartMarker$) > 0 Then InSection = True
		If InSection = True And Instr(Line$, EndMarker$) > 0 Then Exit
		If InSection = True
			If Instr(Line$, "If AInstance <> Null") > 0 Then SawInstanceGuard = True
			If SawInstanceGuard = True And Instr(Line$, AreaNeedle$) > 0
				CloseFile F
				Return True
			EndIf
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Test testBootPlayerUsesNestedAreaInstanceGuard()
	Assert(SectionUsesNestedAreaGuard%("Server.bb", "Case Game\BootButton", "Case Game\MessageButton", "If AInstance\Area = GameArea") = True)
	Assert(SectionContains%("Server.bb", "Case Game\BootButton", "Case Game\MessageButton", "AInstance <> Null And AInstance\Area") = False)
End Test

Test testPlayerListRefreshUsesNestedAreaInstanceGuard()
	Assert(SectionUsesNestedAreaGuard%("Server.bb", "Case Game\AreaCombo", "Case Game\ChatLogMode", "If AInstance\Area = GameArea") = True)
	Assert(SectionContains%("Server.bb", "Case Game\AreaCombo", "Case Game\ChatLogMode", "AInstance <> Null And AInstance\Area") = False)
End Test

Test testPortalAndTriggerTickUsesNestedAreaInstanceGuard()
	Assert(SectionUsesNestedAreaGuard%("Server.bb", "If AI\RuntimeID > -1 And AI\RNID > 0", "For i = 0 To 99", "If AInstance\Area = UpdateArea") = True)
	Assert(SectionContains%("Server.bb", "If AI\RuntimeID > -1 And AI\RNID > 0", "For i = 0 To 99", "AInstance <> Null And AInstance\Area") = False)
End Test
