Strict
EnableGC

; Client teardown is graphics-heavy, so bind the bounded production cursor
; contract without pulling its renderer dependency graph into this test.
Function UnloadGameTeardownUsesAfterCursors%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InUnload%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function UnloadGame") > 0 Then InUnload = True
		If InUnload = True
			If Instr(Line$, "For L.Light = Each Light") > 0 Or Instr(Line$, "For AI.ActorInstance = Each Actorinstance") > 0
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "Local L.Light = First Light") > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, "Local LNext.Light = Null") > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, "While L <> Null") > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, "LNext = After L") > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, "FreeEntity(L\EN)") > 0 Then Stage = 5
			If Stage = 5 And Instr(Line$, "Delete L") > 0 Then Stage = 6
			If Stage = 6 And Instr(Line$, "L = LNext") > 0 Then Stage = 7
			If Stage = 7 And Instr(Line$, "Wend") > 0 Then Stage = 8
			If Stage = 8 And Instr(Line$, "Local AI.ActorInstance = First ActorInstance") > 0 Then Stage = 9
			If Stage = 9 And Instr(Line$, "Local AINext.ActorInstance = Null") > 0 Then Stage = 10
			If Stage = 10 And Instr(Line$, "While AI <> Null") > 0 Then Stage = 11
			If Stage = 11 And Instr(Line$, "AINext = After AI") > 0 Then Stage = 12
			If Stage = 12 And Instr(Line$, "FreeActorInstance3D(AI)") > 0 Then Stage = 13
			If Stage = 13 And Instr(Line$, "Delete(AI)") > 0 Then Stage = 14
			If Stage = 14 And Instr(Line$, "AI = AINext") > 0 Then Stage = 15
			If Stage = 15 And Instr(Line$, "Wend") > 0 Then Stage = 16
			If Instr(Line$, "End Function") > 0
				CloseFile F
				Return Stage = 16
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False

End Function

Test testUnloadGameTeardownUsesAfterCursors()
	Assert(UnloadGameTeardownUsesAfterCursors%("Modules\ClientLoaders.bb") = True)
End Test
