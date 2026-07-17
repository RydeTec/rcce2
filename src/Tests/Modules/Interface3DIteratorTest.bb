Strict
EnableGC

; Source-contract regression for UpdateInterface cleanup loops. Blitz3D's
; For Each cursor cannot safely advance after its current node is deleted, so
; both bounded sections must capture After before cleanup and advance via it.

Function CleanupSectionUsesAfterCursor%(Path$, StartMarker$, EndMarker$, LegacyFor$, FirstCursor$, NextDeclaration$, WhileCursor$, Capture$, FirstCleanup$, Cleanup$, Advance$)
	Local F.BBStream = ReadFile(Path$)
	Local Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Stage = 0 And Instr(Line$, StartMarker$) > 0 Then Stage = 1
		If Stage > 0 And Instr(Line$, LegacyFor$) > 0 Then
			CloseFile F
			Return False
		EndIf
		If Stage = 1
			If Instr(Line$, FirstCursor$) > 0 Then Stage = 2
		ElseIf Stage = 2
			If Instr(Line$, NextDeclaration$) > 0 Then Stage = 3
		ElseIf Stage = 3
			If Instr(Line$, WhileCursor$) > 0 Then Stage = 4
		ElseIf Stage = 4
			If Instr(Line$, Capture$) > 0 Then Stage = 5
		ElseIf Stage = 5
			If FirstCleanup$ = "" Then Stage = 6
			If Instr(Line$, FirstCleanup$) > 0 Then Stage = 6
		ElseIf Stage = 6
			If Instr(Line$, Cleanup$) > 0 Then Stage = 7
		ElseIf Stage = 7
			If Instr(Line$, Advance$) > 0 Then
				CloseFile F
				Return True
			EndIf
		EndIf
		If Stage > 0 And Instr(Line$, EndMarker$) > 0 Then Exit
	Wend
	CloseFile F
	Return False
End Function

Test testBubbleCleanupCapturesNextBeforeDeletingBubble()
	Assert(CleanupSectionUsesAfterCursor%("Modules\Interface3D.bb", "Update chat bubbles", "; Update quest log window", "For Bubble.Bubble = Each Bubble", "Local Bubble.Bubble = First Bubble", "Local BNext.Bubble = Null", "While Bubble <> Null", "BNext = After Bubble", "FreeEntity(Bubble\EN)", "Delete(Bubble)", "Bubble = BNext") = True)
End Test

Test testCurrentChatCleanupCapturesNextBeforeDeletingCurrentChat()
	Assert(CleanupSectionUsesAfterCursor%("Modules\Interface3D.bb", "; Make current chat text disappear after 15 seconds", ";If ( ControlDown", "For CC.CurrentChat = Each CurrentChat", "Local CC.CurrentChat = First CurrentChat", "Local CCNext.CurrentChat = Null", "While CC <> Null", "CCNext = After CC", "", "Delete(CC)", "CC = CCNext") = True)
End Test
