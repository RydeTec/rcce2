Strict
EnableGC

; FUI_Destroy owns resource pools whose current nodes are freed during cleanup.
; Each bounded loop must save After before freeing/deleting its current node.

Function FUIDestroySectionUsesAfterCursor%(Path$, LegacyFor$, FirstCursor$, NextDeclaration$, WhileCursor$, Capture$, FirstCleanup$, DeleteNode$, Advance$)
	Local F.BBStream = ReadFile(Path$)
	Local InDestroy%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function FUI_Destroy(  )") > 0 Then InDestroy = True
		If InDestroy = True
			If Instr(Line$, LegacyFor$) > 0 Then
				CloseFile F
				Return False
			EndIf
			If Stage < 4 And (Instr(Line$, FirstCleanup$) > 0 Or Instr(Line$, DeleteNode$) > 0)
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, FirstCursor$) > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, NextDeclaration$) > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, WhileCursor$) > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, Capture$) > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, FirstCleanup$) > 0 Then Stage = 5
			If Stage = 5 And Instr(Line$, DeleteNode$) > 0 Then Stage = 6
			If Stage = 6 And Instr(Line$, Advance$) > 0 Then
				CloseFile F
				Return True
			EndIf
			If Instr(Line$, "End Function") > 0 Then Exit
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Test testFUIDestroyImageBoxesCaptureNextBeforeFreeAndDelete()
	Assert(FUIDestroySectionUsesAfterCursor%("Modules\F-UI.bb", "For img.ImageBox = Each ImageBox", "Local img.ImageBox = First ImageBox", "Local imgNext.ImageBox = Null", "While img <> Null", "imgNext = After img", "FreeImage img\Image", "Delete img", "img = imgNext") = True)
End Test

Test testFUIDestroyViewsCaptureNextBeforeFreeAndDelete()
	Assert(FUIDestroySectionUsesAfterCursor%("Modules\F-UI.bb", "For view.View = Each View", "Local view.View = First View", "Local viewNext.View = Null", "While view <> Null", "viewNext = After view", "FreeEntity view\Cam", "Delete view", "view = viewNext") = True)
End Test

Test testFUIDestroyMeshesCaptureNextBeforeFreeAndDelete()
	Assert(FUIDestroySectionUsesAfterCursor%("Modules\F-UI.bb", "For m.Mesh = Each Mesh", "Local m.Mesh = First Mesh", "Local mNext.Mesh = Null", "While m <> Null", "mNext = After m", "FreeEntity m\Mesh", "Delete m", "m = mNext") = True)
End Test
