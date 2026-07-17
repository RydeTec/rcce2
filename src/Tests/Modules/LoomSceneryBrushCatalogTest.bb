Strict
EnableGC

; Loom's mesh picker stores an engine mesh ID, but media deletion rebuilds the
; catalog. A stale brush must be rejected before placement asks GetMesh for it
; or creates a Scenery record. BlitzForge And is eager, so scale access also
; needs a separate guard after the catalog entry is known live.

Function StaleSceneryBrushIsRejected%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$, Trimmed$
	Local Stage%, SawBrushIDReset%, SawBrushNameReset%
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		Trimmed$ = Trim$(Line$)
		If Instr(Trimmed$, "If mEnt <> Null And") > 0
			CloseFile F
			Return False
		EndIf
		Select Stage
			Case 0
				If Instr(Trimmed$, "Local mEnt.MeshEntry = Meshes_GetByID(ScnBrushMeshID)") > 0 Then Stage = 1
			Case 1
				If Trimmed$ = "If mEnt = Null"
					Stage = 2
				ElseIf Instr(Trimmed$, "GetMesh(") > 0 Or Instr(Trimmed$, "New Scenery") > 0
					CloseFile F
					Return False
				EndIf
			Case 2
				If Trimmed$ = "ScnBrushMeshID = 0" Then SawBrushIDReset = True
				If Trimmed$ = "ScnBrushName$ = " + Chr$(34) + Chr$(34) Then SawBrushNameReset = True
				If Trimmed$ = "Return"
					If SawBrushIDReset = False Or SawBrushNameReset = False
						CloseFile F
						Return False
					EndIf
					Stage = 3
				ElseIf Instr(Trimmed$, "GetMesh(") > 0 Or Instr(Trimmed$, "New Scenery") > 0
					CloseFile F
					Return False
				EndIf
			Case 3
				If Instr(Trimmed$, "Local en = GetMesh(ScnBrushMeshID, False)") > 0 Then Stage = 4
			Case 4
				If Trimmed$ = "If mEnt\Scale# > 0.0 Then sc# = mEnt\Scale# * 0.05"
					CloseFile F
					Return True
				EndIf
		End Select
	Wend

	CloseFile F
	Return False
End Function

Test testStaleSceneryBrushIsRejectedBeforePlacement()
	Assert(StaleSceneryBrushIsRejected%("Modules\Loom\ZoneViewport.bb") = True)
End Test
