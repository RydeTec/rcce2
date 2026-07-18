Strict
EnableGC

; Source-contract regression for Loom's Ctrl+F result cleanup. ScriptSearch
; lives in the editor graph, so bind the bounded cleanup order from source.

Function ClearHitsUsesAfterCursor%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$, Trimmed$
	Local InMethod%, Stage%, SawHitCount%, SawScrollOffset%
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		Trimmed$ = Trim$(Line$)
		If Trimmed$ = "Method clearHits()" Then InMethod = True
		If InMethod = False Then Continue

		If Trimmed$ = "For h = Each ScriptSearchHit"
			CloseFile F
			Return False
		EndIf
		If Trimmed$ = "Delete h" And Stage < 4
			CloseFile F
			Return False
		EndIf

		Select Stage
			Case 0
				If Trimmed$ = "Local h.ScriptSearchHit = First ScriptSearchHit" Then Stage = 1
			Case 1
				If Trimmed$ = "Local nextHit.ScriptSearchHit = Null" Then Stage = 2
			Case 2
				If Trimmed$ = "While h <> Null" Then Stage = 3
			Case 3
				If Trimmed$ = "nextHit = After h" Then Stage = 4
			Case 4
				If Trimmed$ = "Delete h" Then Stage = 5
			Case 5
				If Trimmed$ = "h = nextHit" Then Stage = 6
		End Select

		If Trimmed$ = "self\hitCount = 0" Then SawHitCount = True
		If Trimmed$ = "self\scrollOffset = 0" Then SawScrollOffset = True
		If Trimmed$ = "End Method"
			CloseFile F
			Return Stage = 6 And SawHitCount And SawScrollOffset
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Test testScriptSearchClearHitsCapturesSuccessorBeforeDeleting()
	Assert(ClearHitsUsesAfterCursor%("Modules\Loom\ScriptSearch.bb") = True)
End Test
