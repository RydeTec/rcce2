Strict
EnableGC

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

Test testMarkerDragKeepsVisualLiftOutOfStoredY()
	Local Source$ = "Modules\Loom\ZoneViewport.bb"
	Assert(FileContains%(Source$, "Field VisualLift#") = True)
	Assert(FileContains%(Source$, "pm\VisualLift# = VP_MARKER_SIZE#") = True)
	Assert(FileContains%(Source$, "Local spawnVisualLift# = 0.0") = True)
	Assert(FileContains%(Source$, "spawnVisualLift# = VP_MARKER_SIZE#") = True)
	Assert(FileContains%(Source$, "sm\VisualLift# = spawnVisualLift#") = True)
	Assert(FileContains%(Source$, "tm\VisualLift# = VP_MARKER_SIZE#") = True)
	Assert(FileContains%(Source$, "VPMarkerDragVisualLift# = pm\VisualLift#") = True)
	Assert(FileContains%(Source$, "Local curSemanticY# = EntityY#(VPMarkerDragEN) - VPSceneYOff# - VPMarkerDragVisualLift#") = True)
	Assert(FileContains%(Source$, "Local newY# = VPSceneYOff# + newSemanticY# + VPMarkerDragVisualLift#") = True)
	Assert(FileContains%(Source$, "Loom_CommitMarkerY(VPMarkerDragArH, VPMarkerDragKind$, VPMarkerDragIdx, newSemanticY#)") = True)
	Assert(FileContains%(Source$, "Loom_CommitMarkerY(VPMarkerDragArH, VPMarkerDragKind$, VPMarkerDragIdx, newY# - VPSceneYOff#)") = False)
End Test
