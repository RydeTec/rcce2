Strict
EnableGC

; UpdateInterface depends on the legacy graphics runtime, so this focused
; source-contract test protects the stale water-handle boundary without loading
; the client module in the headless test harness.

Function HasSafeSwimmingClamp%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Step = 0
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		Select Step
			Case 0
				If Instr(Line$, "W.Water = Object.Water(Me\Underwater)") > 0 Then Step = 1
			Case 1
				If Instr(Line$, "If W <> Null") > 0 Then Step = 2
			Case 2
				If Instr(Line$, "If EntityY#(Me\CollisionEN) > EntityY#(W\EN) - 0.5") > 0 Then Step = 3
			Case 3
				If Instr(Line$, "PositionEntity(Me\CollisionEN, EntityX#(Me\CollisionEN), EntityY#(W\EN) - 0.505, EntityZ#(Me\CollisionEN))") > 0
					CloseFile F
					Return True
				EndIf
		End Select
	Wend
	CloseFile F
	Return False
End Function

Function FileContains%(Path$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, Needle$) > 0
			CloseFile F
			Return True
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Test testSwimmingAscentGuardsAStaleWaterHandle()
	Assert(HasSafeSwimmingClamp%("src\Modules\Interface3D.bb") = True)
	Assert(FileContains%("src\Modules\Interface3D.bb", "If W <> Null And EntityY#(Me\CollisionEN)") = False)
End Test
