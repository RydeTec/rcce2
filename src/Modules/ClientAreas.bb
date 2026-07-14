; ClientAreas.bb -- GUE-side area UI + editor save path. The zone-load DATA
; path (area types, LoadAreaData, UnloadArea, SetViewDistance, ChunkTerrain)
; lives in Modules\AreaLoader.bb since ADR-004 Phase B. This file keeps:
;   - GUE's Gooey implementations of the AreaLoad* hooks,
;   - the signature-compatible LoadArea wrapper,
;   - SaveArea (editor-only write path).
; AreaLoader.bb must be Included before this file (GUE.bb does).

; Loading-screen state shared between the AreaLoad* hooks. These were locals
; of the pre-carve LoadArea; AreaLoadBegin resets them on every load to keep
; the fresh-locals-per-call semantics (a stale AreaLoadScreen from a previous
; load would otherwise reach GY_UpdateProgressBar after being freed).
Global AreaLoadProgressBar, AreaLoadScreen
Global AreaLoadPMusic, AreaLoadCMusic

; UI hook called by LoadAreaData (Modules\AreaLoader.bb) right after the
; loading-screen texture/music IDs are read from the area file: starts the
; loading music and (when not in display-items mode) builds the progress bar
; and the loading-screen quad. Body moved verbatim from the pre-carve
; LoadArea, with the four locals promoted to the AreaLoad* globals above.
Function AreaLoadBegin(DisplayItems)

	AreaLoadProgressBar = 0
	AreaLoadScreen = 0
	AreaLoadPMusic = 0
	AreaLoadCMusic = 0

		; Music
		If LoadingMusicID < 65535 Then 
			AreaLoadPMusic = LoadSound("Data\Music\" + GetMusicName$(LoadingMusicID), False)
			LoopSound AreaLoadPMusic
			AreaLoadCMusic = PlaySound(AreaLoadPMusic)
		EndIf		
		If DisplayItems = False
			; Progress bar
			AreaLoadProgressBar = GY_CreateProgressBar(0, 0.3, 0.9, 0.4, 0.035, 0, 100, 255, 255, 255, -3012)
			; Preset image
			AreaLoadScreen = CreateMesh(GY_Cam)
			Surf = CreateSurface(AreaLoadScreen)
			v1 = AddVertex(Surf, 0.0, -1.0, 0.0, 0.0, 1.0)
			v2 = AddVertex(Surf, 1.0, -1.0, 0.0, 1.0, 1.0)
			v3 = AddVertex(Surf, 1.0, 0.0, 0.0, 1.0, 0.0)
			v4 = AddVertex(Surf, 0.0, 0.0, 0.0, 0.0, 0.0)
			AddTriangle Surf, v3, v2, v1
			AddTriangle Surf, v4, v3, v1
			
			
			;Widescreen Ramoida
			If ResolutionType = 1 ; 16:9 ratio
				ScaleMesh AreaLoadScreen, 27.0, 15.05, 1.0 ;x,y,z
				PositionEntity AreaLoadScreen, -13.5, 7.55, 10.0
			Else  ;;4:3 ratio
				ScaleMesh AreaLoadScreen, 20.5, 15.5, 1.0
				PositionEntity AreaLoadScreen, -10.07, 7.55, 10.0
			EndIf
			
			EntityOrder AreaLoadScreen,-3011
			EntityFX AreaLoadScreen, 1 + 8
						
			If LoadingTexID < 65535
				Tex = GetTexture(LoadingTexID)
				If Tex <> 0
					EntityTexture(AreaLoadScreen, Tex)
					UnloadTexture(LoadingTexID)
				EndIf
			; Random image
			ElseIf RandomImages > 0
				D = ReadDir("Data\Textures\Random")
				If D = 0
					EntityColor(AreaLoadScreen, 0, 0, 0)
				Else
					For i = 1 To Rand(1, RandomImages)
						Repeat
							File$ = NextFile$(D)
						Until FileType("Data\Textures\Random\" + File$) = 1 Or File$ = ""
						If File$ = "" Then Exit
					Next
					If FileType("Data\Textures\Random\" + File$) = 1
						Tex = LoadTexture("Data\Textures\Random\" + File$)
						If Tex = 0
							EntityColor(AreaLoadScreen, 0, 0, 0)
						Else
							EntityTexture(AreaLoadScreen, Tex)
							FreeTexture(Tex)
						EndIf
					Else
						EntityColor(AreaLoadScreen, 0, 0, 0)
					EndIf
					CloseDir(D)
				EndIf
			; No image
			Else
				EntityColor(AreaLoadScreen, 0, 0, 0)
			EndIf
		EndIf

End Function

; UI hook: progress-bar milestone. The AreaLoadScreen gate replicates the
; pre-carve `If LoadScreen <> 0` check around every update site.
Function AreaLoadProgress(Pct)

	If AreaLoadScreen <> 0
		GY_UpdateProgressBar(AreaLoadProgressBar, Pct)
		RenderWorld()
		Flip()
	EndIf

End Function

; UI hook: tear down the loading screen and stop the loading music.
Function AreaLoadEnd()

	; End loading screen
	If AreaLoadScreen <> 0
		FreeEntity(AreaLoadScreen)
		;FreeEntity(LoadLabel)
		GY_FreeGadget(AreaLoadProgressBar)
	EndIf
	If ChannelPlaying(AreaLoadCMusic) = True Then StopChannel(AreaLoadCMusic)
	FreeSound AreaLoadPMusic

	AreaLoadProgressBar = 0
	AreaLoadScreen = 0
	AreaLoadPMusic = 0
	AreaLoadCMusic = 0

End Function

; Loads the client (3D) data for an area. Thin wrapper kept so existing GUE
; call sites are untouched -- the data path is LoadAreaData (AreaLoader.bb),
; and the loading-screen presentation comes back in via the hooks above.
Function LoadArea(Name$, CameraEN, DisplayItems = False, UpdateRottNet = False)

	Return LoadAreaData(Name$, CameraEN, DisplayItems, UpdateRottNet)

End Function


; ADR-004 Phase D: SaveArea (the whole-visual-area serializer) was RELOCATED
; from here into AreaLoader.bb -- the shared data-only module -- so that Loom
; (which cannot Include ClientAreas.bb, per ADR "Why no ClientAreas.bb
; Include") can reach it without pulling in the Gooey/F-UI stack. GUE still
; Includes AreaLoader.bb BEFORE this file, so its SaveArea(...) calls resolve
; to the relocated definition unchanged. No behavior change.
