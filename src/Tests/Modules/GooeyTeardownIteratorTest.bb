Strict
EnableGC

; GY_FreeGadget recursively deletes both gadget and window Type instances.
; BlitzForge For Each cursors cannot advance after their current instance was
; deleted, so these bounded source contracts require restart-on-delete walks.

Function ClearGadgetsRestartsAfterDelete%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InFunction%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function GY_ClearGadgets()") > 0 Then InFunction = True
		If InFunction = True
			If Instr(Line$, "For gadget.GY_Gadget = Each GY_Gadget") > 0
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "Local Gadget.GY_Gadget = First GY_Gadget") > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, "While Gadget <> Null") > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, "GY_FreeGadget(Handle(Gadget))") > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, "Gadget = First GY_Gadget") > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, "Wend") > 0 Then Stage = 5
			If Instr(Line$, "End Function") > 0
				CloseFile F
				Return Stage = 5
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False

End Function

Function FreeGadgetChildrenRestartAfterDelete%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InChildren%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "; Free children") > 0 Then InChildren = True
		If InChildren = True
			If Instr(Line$, "For Child.GY_Gadget = Each GY_Gadget") > 0
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "Local Child.GY_Gadget = First GY_Gadget") > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, "While Child <> Null") > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, "If Child\Parent = G") > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, "GY_FreeGadget(Handle(Child))") > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, "Child = First GY_Gadget") > 0 Then Stage = 5
			If Stage = 5 And Instr(Line$, "Else") > 0 Then Stage = 6
			If Stage = 6 And Instr(Line$, "Child = After Child") > 0 Then Stage = 7
			If Stage = 7 And Instr(Line$, "EndIf") > 0 Then Stage = 8
			If Stage = 8 And Instr(Line$, "Wend") > 0 Then Stage = 9
			If Instr(Line$, "; If it's a window") > 0
				CloseFile F
				Return Stage = 9
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False

End Function

Function UnloadRestartsAfterDelete%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InFunction%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function GY_Unload()") > 0 Then InFunction = True
		If InFunction = True
			If Instr(Line$, "For W.GY_Window = Each GY_Window") > 0 Or Instr(Line$, "For G.GY_Gadget = Each GY_Gadget") > 0
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "Local W.GY_Window = First GY_Window") > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, "While W <> Null") > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, "GY_FreeGadget(Handle(W\Gadget))") > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, "W = First GY_Window") > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, "Wend") > 0 Then Stage = 5
			If Stage = 5 And Instr(Line$, "Local G.GY_Gadget = First GY_Gadget") > 0 Then Stage = 6
			If Stage = 6 And Instr(Line$, "While G <> Null") > 0 Then Stage = 7
			If Stage = 7 And Instr(Line$, "GY_FreeGadget(Handle(G))") > 0 Then Stage = 8
			If Stage = 8 And Instr(Line$, "G = First GY_Gadget") > 0 Then Stage = 9
			If Stage = 9 And Instr(Line$, "Wend") > 0 Then Stage = 10
			If Instr(Line$, "; Textures") > 0
				CloseFile F
				Return Stage = 10
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False

End Function

Test testGooeyTeardownRestartsAfterRecursiveDeletes()
	Assert(ClearGadgetsRestartsAfterDelete%("Modules\Gooey.bb") = True)
	Assert(FreeGadgetChildrenRestartAfterDelete%("Modules\Gooey.bb") = True)
	Assert(UnloadRestartsAfterDelete%("Modules\Gooey.bb") = True)
End Test
