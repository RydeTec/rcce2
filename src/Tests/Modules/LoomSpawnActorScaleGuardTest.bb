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

Test testSpawnMarkerScaleGuardsStaleActorMetadata()
	Local Source$ = "Modules\Loom\ZoneViewport.bb"

	Assert(FileContains%(Source$, "Local spawnScale# = VP_MARKER_SIZE#") = True)
	Assert(FileContains%(Source$, "Local A2.Actor = ActorList(Ar\SpawnActor[i])") = True)
	Assert(FileContains%(Source$, "                    If A2 <> Null Then") = True)
	Assert(FileContains%(Source$, "                        If A2\Scale# > 0.0 Then spawnScale# = A2\Scale#") = True)
	Assert(FileContains%(Source$, "If A2 <> Null And A2\Scale# > 0.0 Then spawnScale# = A2\Scale#") = False)
End Test
