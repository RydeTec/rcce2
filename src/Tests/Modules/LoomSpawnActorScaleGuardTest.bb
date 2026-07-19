Strict
EnableGC

Function FileOccurrenceCount%(Path$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Count = 0
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return -1

	While Not Eof(F)
		Line$ = ReadLine$(F)
		Local Start = 1
		Local Found = Instr(Line$, Needle$, Start)
		While Found > 0
			Count = Count + 1
			Start = Found + Len(Needle$)
			Found = Instr(Line$, Needle$, Start)
		Wend
	Wend

	CloseFile F
	Return Count
End Function

Function HasSafeSpawnMarkerScaleGuard%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Stage = 0
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		Select Stage
			Case 0
				If Instr(Line$, "Local spawnScale# = VP_MARKER_SIZE#") > 0 Then Stage = 1
			Case 1
				If Instr(Line$, "Local A2.Actor = ActorList(Ar\SpawnActor[i])") > 0 Then Stage = 2
			Case 2
				If Trim$(Line$) <> "If A2 <> Null Then"
					CloseFile F
					Return False
				EndIf
				Stage = 3
			Case 3
				If Trim$(Line$) <> "If A2\Scale# > 0.0 Then spawnScale# = A2\Scale#"
					CloseFile F
					Return False
				EndIf
				Stage = 4
			Case 4
				CloseFile F
				Return Trim$(Line$) = "EndIf"
		End Select
	Wend

	CloseFile F
	Return False
End Function

Test testSpawnMarkerScaleGuardsStaleActorMetadata()
	Local Source$ = "Modules\Loom\ZoneViewport.bb"

	Assert(HasSafeSpawnMarkerScaleGuard%(Source$) = True)
	; The required nested assignment contains both references. Any additional
	; A2 scale access would be outside this bounded guard contract.
	Assert(FileOccurrenceCount%(Source$, "A2\Scale#") = 2)
	Assert(FileOccurrenceCount%(Source$, "If A2 <> Null And A2\Scale# > 0.0 Then spawnScale# = A2\Scale#") = 0)
End Test
