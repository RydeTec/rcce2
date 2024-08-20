; Weather globals
Global CurrentWeather
Global RainEmitter, SnowEmitter
Global FogNearDest#, FogFarDest#, FogNearNow#, FogFarNow#
Global SkyAlpha#, StarsAlpha#, CloudAlpha#
Global SkyChange = 0, CloudChange = 0, FogChange = False
Global LightningToDo = 0
Global Snd_Rain, Snd_Wind
Global SndC_Rain, SndC_Wind
Dim Snd_Thunder(2)
Global CameraUnderwater = False
Global TotalFlares
Dim Flares(10)

; Emitter effects (scripted)
Type ScriptedEmitter
	Field EN
	Field Length, StartTime
	Field AttachedToPlayer
End Type

; Light (for fade purposes)
Type Light
	Field EN
	Field R#, G#, B#
	Field DestR, DestG, DestB
End Type

; Lens flare sizes
Dim LensFlareSize#(10)
LensFlareSize#(0) = 3.0
LensFlareSize#(1) = 0.8
LensFlareSize#(2) = 1.8
LensFlareSize#(3) = 1.0
LensFlareSize#(4) = 1.3
LensFlareSize#(5) = 2.5
LensFlareSize#(6) = 1.5
LensFlareSize#(7) = 1.2
LensFlareSize#(8) = 0.8
LensFlareSize#(9) = 1.1
LensFlareSize#(10) = 2.5

; Creates 3D stuff for all suns
Function CreateSuns()

	; Lens flare
	TotalFlares = -1
	For i = 0 To 10
		If FileType("Data\Textures\Flares\" + Str$(i + 1) + ".png")
			Tex = LoadTexture("Data\Textures\Flares\" + Str$(i + 1) + ".png")
			If Tex = 0 Then RuntimeError("Invalid file: " + "Data\Textures\Flares\" + Str$(i + 1) + ".png")
			If i = 0
				Flares(i) = CreateMesh()
				Surf = CreateSurface(Flares(i))
				v1 = AddVertex(Surf, -1.0, -1.0, 0.0, 1.0, 1.0)
				v2 = AddVertex(Surf,  1.0, -1.0, 0.0, 0.0, 1.0)
				v3 = AddVertex(Surf,  1.0,  1.0, 0.0, 0.0, 0.0)
				v4 = AddVertex(Surf, -1.0,  1.0, 0.0, 1.0, 0.0)
				AddTriangle(Surf, v3, v2, v1)
				AddTriangle(Surf, v4, v3, v1)
				EntityFX(Flares(i), 1 + 8)
				EntityBlend(Flares(i), 3)
				EntityOrder(Flares(i), -1)
			Else
				Flares(i) = CopyEntity(Flares(0))
			EndIf
			If i > 0
				EntityAlpha(Flares(i), 0.4)
			Else
				EntityAlpha(Flares(i), 0.65)
			EndIf
			ScaleEntity(Flares(i), LensFlareSize#(i), LensFlareSize#(i), 1.0)
			EntityTexture(Flares(i), Tex)
			HideEntity(Flares(i))
		Else
			TotalFlares = i
			Exit
		EndIf
	Next

	For S.Sun = Each Sun
		; Create sun quad
		S\EN = CreateMesh()
		Surf = CreateSurface(S\EN)
		v1 = AddVertex(Surf, -1.0, -1.0, 0.0, 1.0, 1.0)
		v2 = AddVertex(Surf,  1.0, -1.0, 0.0, 0.0, 1.0)
		v3 = AddVertex(Surf,  1.0,  1.0, 0.0, 0.0, 0.0)
		v4 = AddVertex(Surf, -1.0,  1.0, 0.0, 1.0, 0.0)
		AddTriangle(Surf, v1, v2, v3)
		AddTriangle(Surf, v1, v3, v4)
		ScaleMesh(S\EN, S\Size# * 2.5, S\Size# * 2.5, 1.0)
	
		;	Get default sun/moon texture
		
		;Tex = GetTexture(S\TexID)
		Tex = GetTexture(S\TexID[0])
		If Tex <> 0 Then EntityTexture(S\EN, Tex)
		
	
		;UnloadTexture(S\TexID)
		UnloadTexture(S\TexID[0])

		EntityFX(S\EN, 1 + 8)
		EntityBlend(S\EN, 3)
		EntityOrder(S\EN, 2)

		; Create light
		S\LightEN = CreateLight()
		LightColor(S\LightEN, 0, 0, 0)
		HideEntity(S\LightEN)
		L.Light = New Light
		NameEntity(S\LightEN, Handle(L))
		L\EN = S\LightEN

		; Create flares
		If S\ShowFlares
			For i = 0 To TotalFlares - 1
				S\Flares[i] = CopyEntity(Flares(i))
				EntityParent(S\Flares[i], Cam)
				HideEntity(S\Flares[i])
			Next
		EndIf
	Next

End Function


; Loads weather stuff
Function LoadWeather3D()

	; Emitters
	Tex = LoadTexture("Data\Textures\Particles\Rain.bmp", 1 + 4 + 8)
	If Tex = 0 Then RuntimeError("Could not find Data\Textures\Particles\Rain.bmp!")
	Config = RP_LoadEmitterConfig("Data\Emitter Configs\Rain.rpc", Tex, Cam)
	RainEmitter = RP_CreateEmitter(Config)
	Tex = LoadTexture("Data\Textures\Particles\Snow.bmp", 1 + 4 + 8)
	If Tex = 0 Then RuntimeError("Could not find Data\Textures\Particles\Snow.bmp!")
	Config = RP_LoadEmitterConfig("Data\Emitter Configs\Snow.rpc", Tex, Cam)
	SnowEmitter = RP_CreateEmitter(Config)
	RP_DisableEmitter(RainEmitter)
	RP_DisableEmitter(SnowEmitter)

	; Sounds
	Snd_Rain = LoadSound("Data\Sounds\Weather\Rain.ogg")
	If Snd_Rain = 0 Then Snd_Rain = LoadSound("Data\Sounds\Weather\Rain.ogg")
	SoundVolume(Snd_Rain, DefaultVolume#) : LoopSound(Snd_Rain)
	Snd_Wind = LoadSound("Data\Sounds\Weather\Wind.ogg")
	If Snd_Wind = 0 Then Snd_Wind = LoadSound("Data\Sounds\Weather\Wind.ogg")
	SoundVolume(Snd_Wind, DefaultVolume#) : LoopSound(Snd_Wind)
	Snd_Thunder(0) = LoadSound("Data\Sounds\Weather\Thunder1.ogg") : SoundVolume(Snd_Thunder(0), DefaultVolume#)
	Snd_Thunder(1) = LoadSound("Data\Sounds\Weather\Thunder2.ogg") : SoundVolume(Snd_Thunder(1), DefaultVolume#)
	Snd_Thunder(2) = LoadSound("Data\Sounds\Weather\Thunder3.ogg") : SoundVolume(Snd_Thunder(2), DefaultVolume#)

End Function

; Sets the current weather type
Function SetWeather(Num)

	RP_DisableEmitter(RainEmitter) : RP_DisableEmitter(SnowEmitter)
	If CameraUnderwater = False
		CameraClsColor Cam, FogR, FogG, FogB
		CameraFogColor Cam, FogR, FogG, FogB
	EndIf
	Select Num
		Case W_Sun
			FogNearDest# = FogNear# : FogFarDest# = FogFar#
			FogChange = True
			CloudChange = 3
			If CurrentWeather = W_Snow Or CurrentWeather = W_Fog Then SkyAlpha# = 0.0 : StarsAlpha# = 0.0 : SkyChange = 3
			If ChannelPlaying(SndC_Rain) Then StopChannel(SndC_Rain)
			If ChannelPlaying(SndC_Wind) Then StopChannel(SndC_Wind)
		Case W_Rain
			RP_EnableEmitter(RainEmitter)
			FogFarDest# = FogFar# - 50.0
			If FogNear# > 0.5 Then FogNearDest# = 0.5 Else FogNearDest# = -50.0
			If FogFarDest# < FogNearDest# + 10.0 Then FogFarDest# = FogNearDest# + 10.0
			FogChange = True
			CloudChange = 2
			Tex = GetTexture(CloudTexID)
			If Tex = 0 Then RuntimeError("Invalid clouds texture!")
			EntityTexture(CloudEN, Tex)
			If CurrentWeather = W_Snow Or CurrentWeather = W_Fog Then SkyAlpha# = 0.0 : StarsAlpha# = 0.0 : SkyChange = 3
			If ChannelPlaying(SndC_Rain) = False Then SndC_Rain = PlaySound(Snd_Rain)
			If ChannelPlaying(SndC_Wind) Then StopChannel(SndC_Wind)
		Case W_Snow
			RP_EnableEmitter(SnowEmitter)
			FogFarDest# = FogFar# - 125.0
			If FogNear# > 0.5 Then FogNearDest# = 0.5 Else FogNearDest# = -50.0
			If FogFarDest# < FogNearDest# + 10.0 Then FogFarDest# = FogNearDest# + 10.0
			FogChange = True
			If CameraUnderwater = False
				CameraClsColor Cam, 200, 200, 200
				CameraFogColor Cam, 200, 200, 200
			EndIf
			CloudChange = 3
			If CurrentWeather <> W_Snow And CurrentWeather <> W_Fog Then SkyAlpha# = 1.0 : StarsAlpha# = 1.0 : SkyChange = 2
			If ChannelPlaying(SndC_Rain) Then StopChannel(SndC_Rain)
			If ChannelPlaying(SndC_Wind) Then StopChannel(SndC_Wind)
		Case W_Fog
			FogFarDest# = FogFar# - 500.0
			If FogNear# > 0.5 Then FogNearDest# = 0.5 Else FogNearDest# = -50.0
			If FogFarDest# < FogNearDest# + 10.0 Then FogFarDest# = FogNearDest# + 10.0
			FogChange = True
			CloudChange = 3
			If CurrentWeather <> W_Snow And CurrentWeather <> W_Fog Then SkyAlpha# = 1.0 : StarsAlpha# = 1.0 : SkyChange = 2
			If ChannelPlaying(SndC_Rain) Then StopChannel(SndC_Rain)
			If ChannelPlaying(SndC_Wind) Then StopChannel(SndC_Wind)
		Case W_Storm
			RP_EnableEmitter(RainEmitter)
			FogFarDest# = FogFar# - 100.0
			If FogNear# > 0.5 Then FogNearDest# = 0.5 Else FogNearDest# = -50.0
			If FogFarDest# < FogNearDest# + 10.0 Then FogFarDest# = FogNearDest# + 10.0
			FogChange = True
			CloudChange = 2
			Tex = GetTexture(StormCloudTexID)
			If Tex = 0 Then RuntimeError("Invalid storm clouds texture!")
			EntityTexture(CloudEN, Tex)
			If CurrentWeather = W_Snow Or CurrentWeather = W_Fog Then SkyAlpha# = 0.0 : StarsAlpha# = 0.0 : SkyChange = 3
			If ChannelPlaying(SndC_Rain) = False Then SndC_Rain = PlaySound(Snd_Rain)
			If ChannelPlaying(SndC_Wind) Then StopChannel(SndC_Wind)
		Case W_Wind
			FogNearDest# = FogNear# : FogFarDest# = FogFar#
			FogChange = True
			CloudChange = 2
			Tex = GetTexture(CloudTexID)
			If Tex = 0 Then RuntimeError("Invalid clouds texture!")
			EntityTexture(CloudEN, Tex)
			If CurrentWeather = W_Snow Or CurrentWeather = W_Fog Then SkyAlpha# = 0.0 : StarsAlpha# = 0.0 : SkyChange = 3
			If ChannelPlaying(SndC_Rain) Then StopChannel(SndC_Rain)
			If ChannelPlaying(SndC_Wind) = False Then SndC_Wind = PlaySound(Snd_Wind)
	End Select
	If CameraUnderwater = True Then FogChange = False
	CurrentWeather = Num

End Function

; Updates the whole environment
Function UpdateEnvironment3D()

	; Scripted emitters
	For SEm.ScriptedEmitter = Each ScriptedEmitter
		If MilliSecs() - SEm\StartTime >= SEm\Length
			RP_KillEmitter(SEm\EN, True, False)
			Delete SEm
		EndIf
	Next

;Add Animated water to options menu Cysis145
		F = ReadFile("Data\Options.dat")
		If F = 0 Then RuntimeError("Could not open Data\Options.dat!")
		Width = ReadShort(F)
		Height = ReadShort(F)
		Depth = ReadByte(F)
		AA = ReadByte(F)
		DefaultVolume# = ReadFloat#(F)
		GrassEnabled = ReadByte(F)
		AnisotropyLevel = ReadByte(F)
		FullScreen = ReadByte(F)
		VSync = ReadByte(F)
		Bloom = ReadByte(F)
		Rays = ReadByte(F)
		AWater = ReadByte(F)
		ShadowC = ReadByte(F)
		ShadowQ = ReadByte(F)
		ShadowR = ReadByte(F)
		CloseFile(F)
		
	Select AWater
	Case 0
	; Water
	For W.Water = Each Water
			W\U# = W\U# + (Delta# * 0.00025)
			W\V# = W\V# + (Delta# * 0.0007)
			PositionTexture(W\TexHandle, W\U#, W\V#)
			Next
		
	Case 1
		For W.Water = Each Water
			W\U# = W\U# + (Delta# * 0.00025)
			W\V# = W\V# + (Delta# * 0.0007)
			PositionTexture(W\TexHandle, W\U#, W\V#)
			
			;&&& Water edit 
    		EntityTexture W\EN, BumpTexture, (frame Mod 32), 0 
     		PositionTexture(BumpTexture, dx, dx) 
			EntityTexture W\EN, FoamTexture, (frame Mod 32), 0 
     		PositionTexture(FoamTexture, dx, dx)

		FoamTextureDX=FoamTextureDX+0.0015*DeltaTimeK
		PositionTexture (FoamTexture,FoamTextureDX,FoamTextureDX)
	
		If (MilliSecs()-BumpTextureFrameF)>33 Then
			BumpTextureFrame = BumpTextureFrame + 1
			BumpTextureFrameF = MilliSecs()
		EndIf
Next
	End Select
	
	;Next


	; Sky
	PositionEntity SkyEN, EntityX#(Cam), EntityY#(Cam), EntityZ#(Cam)
	PositionEntity StarsEN, EntityX#(Cam), EntityY#(Cam), EntityZ#(Cam)
	PositionEntity CloudEN, EntityX#(Cam), EntityY#(Cam), EntityZ#(Cam)
	TurnEntity(CloudEN, 0.0, 0.05 * Delta#, 0.0)

	; Rain emitter
	If CurrentWeather = W_Rain Or CurrentWeather = W_Storm
		PositionEntity RainEmitter, EntityX#(Cam), EntityY#(Cam) + 25.0, EntityZ#(Cam)
		RemoveUnderwaterParticles(RainEmitter)
	; Snow emitter
	ElseIf CurrentWeather = W_Snow
		PositionEntity SnowEmitter, EntityX#(Cam), EntityY#(Cam) + 20.0, EntityZ#(Cam)
		RemoveUnderwaterParticles(SnowEmitter)
	EndIf

	; Lightning
	If CurrentWeather = W_Storm
		If LightningToDo = 0
			If Rand(1, Int(500.0 / Delta#)) = 1 Then LightningToDo = Rand(1, 3)
		ElseIf Rand(1, 50) = 1
			LightningToDo = LightningToDo - 1
			ScreenFlash(255, 255, 255, 65535, Rand(200, 1000), 0.9)
			If LightningToDo = 0 Then PlaySound(Snd_Thunder(Rand(0, 2)))
		EndIf
	EndIf

	; Fog
	If FogChange = True
		If FogNearNow# < FogNearDest#
			FogNearNow# = FogNearNow# + (3.0 * Delta#)
			If FogNearNow# > FogNearDest# Then FogNearNow# = FogNearDest#
		ElseIf FogNearNow# > FogNearDest#
			FogNearNow# = FogNearNow# - (3.0 * Delta#)
			If FogNearNow# < FogNearDest# Then FogNearNow# = FogNearDest#
		EndIf
		If FogFarNow# < FogFarDest#
			FogFarNow# = FogFarNow# + (3.0 * Delta#)
			If FogFarNow# > FogFarDest# Then FogFarNow# = FogFarDest#
		ElseIf FogFarNow# > FogFarDest#
			FogFarNow# = FogFarNow# - (3.0 * Delta#)
			If FogFarNow# < FogFarDest# Then FogFarNow# = FogFarDest#
		EndIf
		SetViewDistance(Cam, FogNearNow#, FogFarNow#)
		If Abs(FogFarNow# - FogFarDest#) < 0.1 And Abs(FogNearNow# - FogNearDest#) < 0.1 Then FogChange = False
	EndIf

	; Clouds
	If CloudChange = 1
		If CloudAlpha# < 0.5
			CloudAlpha# = CloudAlpha# + (Delta# / 20.0)
			If CloudAlpha# >= 0.5 Then CloudAlpha# = 0.5 : CloudChange = 0
			EntityAlpha CloudEN, CloudAlpha#
		ElseIf CloudAlpha# >= 0.5
			CloudAlpha# = CloudAlpha# - (Delta# / 20.0)
			If CloudAlpha# <= 0.5 Then CloudAlpha# = 0.5 : CloudChange = 0
			EntityAlpha CloudEN, CloudAlpha#
		EndIf
	ElseIf CloudChange = 2
		If CloudAlpha# < 1.0
			CloudAlpha# = CloudAlpha# + (Delta# / 20.0)
			EntityAlpha CloudEN, CloudAlpha#
		Else
			EntityAlpha CloudEN, 1.0
			CloudChange = 0
		EndIf
	ElseIf CloudChange = 3
		If CloudAlpha# > 0.0
			CloudAlpha# = CloudAlpha# - (Delta# / 20.0)
			EntityAlpha CloudEN, CloudAlpha#
		Else
			EntityAlpha CloudEN, 0.0
			CloudChange = 0
		EndIf
	EndIf

	; Day/night cycle
	If HourChanged = True And CurrentWeather <> W_Snow And CurrentWeather <> W_Fog
		Season = GetSeason()
		; Dusk
		If TimeH = SeasonDuskH(Season)
			StarsAlpha# = 0.0
			SkyAlpha# = 1.0
			SkyChange = 1
		; Dawn
		ElseIf TimeH = SeasonDawnH(Season)
			StarsAlpha# = 1.0
			SkyAlpha# = 0.0
			SkyChange = -1
		EndIf
	EndIf
	If SkyChange = 1
		StarsAlpha# = StarsAlpha# + (Delta# / 250.0)
		SkyAlpha# = SkyAlpha# - (Delta# / 250.0)
		EntityAlpha StarsEN, StarsAlpha# : EntityAlpha SkyEN, SkyAlpha#
		If StarsAlpha# >= 1.0 Then EntityAlpha StarsEN, 1.0 : EntityAlpha SkyEN, 0.0 : SkyChange = 0
	ElseIf SkyChange = -1
		StarsAlpha# = StarsAlpha# - (Delta# / 250.0)
		SkyAlpha# = SkyAlpha# + (Delta# / 250.0)
		EntityAlpha StarsEN, StarsAlpha# : EntityAlpha SkyEN, SkyAlpha#
		If SkyAlpha# >= 1.0 Then EntityAlpha StarsEN, 0.0 : EntityAlpha SkyEN, 1.0 : SkyChange = 0
	ElseIf SkyChange = 2
		If TimeH >= SeasonDuskH(GetSeason()) Or TimeH < SeasonDawnH(GetSeason())
			StarsAlpha# = StarsAlpha# - (Delta# / 25.0)
			EntityAlpha StarsEN, StarsAlpha#
			If StarsAlpha# <= 0.0 Then EntityAlpha StarsEN, 0.0 : SkyChange = 0
		Else
			SkyAlpha# = SkyAlpha# - (Delta# / 25.0)
			EntityAlpha SkyEN, SkyAlpha#
			If SkyAlpha# <= 0.0 Then EntityAlpha SkyEN, 0.0 : SkyChange = 0
		EndIf
	ElseIf SkyChange = 3
		If TimeH >= SeasonDuskH(GetSeason()) Or TimeH < SeasonDawnH(GetSeason())
			StarsAlpha# = StarsAlpha# + (Delta# / 25.0)
			EntityAlpha StarsEN, StarsAlpha#
			If StarsAlpha# >= 1.0 Then EntityAlpha StarsEN, 1.0 : SkyChange = 0
		Else
			SkyAlpha# = SkyAlpha# + (Delta# / 25.0)
			EntityAlpha SkyEN, SkyAlpha#
			If SkyAlpha# >= 1.0 Then EntityAlpha SkyEN, 1.0 : SkyChange = 0
		EndIf
	EndIf

	; Suns
	OneSunVisible = False
	For S.Sun = Each Sun
		; Is this sun visible?
		Visible = False
		If S\StartH[CurrentSeason] = S\EndH[CurrentSeason] ; Sun rises and sets in the same hour
			If TimeM >= S\StartM[CurrentSeason] And TimeM < S\EndM[CurrentSeason] Then Visible = True
		ElseIf S\StartH[CurrentSeason] < S\EndH[CurrentSeason] ; Sunrise hour is before sunset hour
			If TimeH > S\StartH[CurrentSeason]
				If TimeH < S\EndH[CurrentSeason]
					Visible = True
				ElseIf TimeH = S\EndH[CurrentSeason] And TimeM < S\EndM[CurrentSeason]
					Visible = True
				EndIf
			ElseIf TimeH = S\StartH[CurrentSeason]
				If TimeM >= S\StartM[CurrentSeason] Then Visible = True
			EndIf
		Else ; Sunrise hour is after sunset hour (i.e. it spans two days)
			If TimeH > S\StartH[CurrentSeason]
				Visible = True
			ElseIf TimeH = S\StartH[CurrentSeason]
				If TimeM >= S\StartM[CurrentSeason] Then Visible = True
			ElseIf TimeH < S\EndH[CurrentSeason]
				Visible = True
			ElseIf TimeH = S\EndH[CurrentSeason]
				If TimeM < S\EndM[CurrentSeason] Then Visible = True
			EndIf
		EndIf

		; Sun is not visible ; terrier adds Outddors = False Or
		If Visible = False Or Outdoors = False Or CurrentWeather = W_Snow Or CurrentWeather = W_Fog
			HideEntity(S\EN)
			L.Light = Object.Light(EntityName$(S\LightEN))
			L\DestR = 0
			L\DestG = 0
			L\DestB = 0
		; Sun is visible
		Else
		
			;	Updates phase texture

			If (S\ShowPhases = True) And (TimeH = S\StartH[CurrentSeason]) And (TimeM = S\StartM[CurrentSeason])
				; Convert day to phase index
				S\CurrentPhase = Int((Day Mod (8 * S\Phase_Length)) / S\Phase_Length - 0.05) ;8 = number of phases			

				Tex = GetTexture(S\TexID[S\CurrentPhase])			
				If Tex <> 0 Then EntityTexture(S\EN, Tex)
						
				UnloadTexture(S\TexID[S\CurrentPhase])	
			EndIf
					
			; Sun position
			ShowEntity(S\EN)
			PathLength = TimeDelta(S\StartH[CurrentSeason], S\StartM[CurrentSeason], S\EndH[CurrentSeason], S\EndM[CurrentSeason])
			DoneLengthM = TimeDelta(S\StartH[CurrentSeason], S\StartM[CurrentSeason], TimeH, TimeM)
			AmountOfMinuteDone# = Float#(MilliSecs() - TimeUpdate) / Float#(60000 / TimeFactor)
			DoneLength# = Float#(DoneLengthM) + AmountOfMinuteDone#
			Pitch# = (DoneLength# / Float#(PathLength)) * 220.0 ;180
			PositionEntity(S\EN, EntityX#(Cam), EntityY#(Cam) + 100, EntityZ#(Cam))
			RotateEntity(S\EN, -Pitch#, S\PathAngle#, 0)
			MoveEntity(S\EN, 0, 0, 100.0)
			PointEntity(S\EN, Cam)
			
			ShadowLight S\EN
			
			;light rays
			CameraProject Cam, EntityX(S\EN,1), EntityY(S\EN,1), EntityZ(S\EN,1) 
          If ProjectedZ()>0 Then 
             TFormVector 0, 0, 1, Cam, S\EN 
             pitch# =  Abs(Sin(VectorPitch (TFormedX(), TFormedY(), TFormedZ())))   :   pitch = pitch*pitch 
             yaw# = Abs(Sin(VectorYaw (TFormedX(), TFormedY(), TFormedZ())))   :   yaw = yaw*yaw 
             alpha# = (1.0 - pitch) * (1.0 - yaw) 
             x# = ProjectedX()/Float(GraphicsWidth()) 
             y# = ProjectedY()/Float(GraphicsHeight()) 
             CustomPostprocessRays x, y, 105, 1 * alpha, 4, 2, 2, 255, 178, 102      ; <<< Customize Rays postprocess FX 
             RaysFX = FE_Rays 
          Else 
             RaysFX = 0 
          EndIf

			; Light position/direction
			If Outdoors = True
				If S\LightR <> 0 Or S\LightG <> 0 Or S\LightB <> 0 Then OneSunVisible = True
				L.Light = Object.Light(EntityName$(S\LightEN))
				L\DestR = S\LightR
				L\DestG = S\LightG
				L\DestB = S\LightB
				PositionEntity(S\LightEN, EntityX#(S\EN), EntityY#(S\EN), EntityZ#(S\EN))
				PointEntity(S\LightEN, Cam)
			EndIf
		EndIf
	Next

	; Show default light if no suns are visible, or hide it if at least one is
	If OneSunVisible = True
		DefaultLight\DestR = 0
		DefaultLight\DestG = 0
		DefaultLight\DestB = 0
	Else
		DefaultLight\DestR = 255
		DefaultLight\DestG = 255
		DefaultLight\DestB = 255
	EndIf

End Function


; Updates all lens flares
Function UpdateLensFlare()

	If TotalFlares < 0 Then Return

	For S.Sun = Each Sun
		L.Light = Object.Light(EntityName$(S\LightEN))
		If S\ShowFlares
			; Hide lens flare
			If S\ShowFlares
				For i = 0 To TotalFlares - 1
					HideEntity(S\Flares[i])
				Next
			EndIf

			; Show flares if the sun is in view and is lighting the scene, and there is no weather
			If (L\DestR > 0 Or L\DestG > 0 Or L\DestB > 0) And CurrentWeather = W_Sun
				If EntityInView(S\EN, Cam)
					XDist# = (EntityX#(S\EN) - EntityX#(Cam)) * 5.0
					YDist# = (EntityY#(S\EN) - EntityY#(Cam)) * 5.0
					ZDist# = (EntityZ#(S\EN) - EntityZ#(Cam)) * 5.0
					SetPickModes()
					Result = LinePick(EntityX#(Cam), EntityY#(Cam), EntityZ#(Cam), XDist#, YDist#, ZDist#, 1.0)
					If Result = 0
						If CamMode = 0
							SetPickModes(-1, 4)
						Else
							SetPickModes(-1, 1)
						EndIf
						Result = LinePick(EntityX#(Cam), EntityY#(Cam), EntityZ#(Cam), XDist#, YDist#, ZDist#)
						If Result = 0
							CameraProject(Cam, EntityX#(S\EN), EntityY#(S\EN), EntityZ#(S\EN))
							ScreenX# = GraphicsWidth() / 2
							ScreenY# = GraphicsHeight() / 2
							vX# = ProjectedX#() - ScreenX#
							vY# = ProjectedY#() - ScreenY#
							Length# = Sqr#((vX# * vX#) + (vY# * vY#))
							vX# = vX# / Length#
							vY# = vY# / Length#
							Scale# = 1.0
							cR = S\LightR - (TotalFlares * 10)
							cG = S\LightG
							cB = S\LightB + (TotalFlares * 15)
							Size# = 1.1 - (Length# / ScreenX#)
							For i = 0 To TotalFlares - 1
								Dist# = Length# * Scale#
								X# = (ScreenX# + (vX# * Dist#)) / Float#(GraphicsWidth())
								Y# = (ScreenY# + (vY# * Dist#)) / Float#(GraphicsHeight())
								R = cR
								If R < 0
									R = 0
								ElseIf R > 255
									R = 255
								EndIf
								B = cB
								If B < 0
									B = 0
								ElseIf B > 255
									B = 255
								EndIf
								EntityColor(S\Flares[i], R, cG, B)
								PositionEntity(S\Flares[i], (X# * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
								ShowEntity(S\Flares[i])
								ScaleEntity(S\Flares[i], LensFlareSize#(i) * Size#, LensFlareSize#(i) * Size#, 1.0)
								Scale# = Scale# - 0.45
								cR = cR + 50
								cB = cB - 50
							Next
						EndIf
					EndIf
				EndIf
			EndIf
		EndIf
	Next

End Function

; Updates all lights
Function UpdateLights()

	For L.Light = Each Light
		; Check light actually needs updating
		If Abs(L\R# - Float#(L\DestR)) > 0.1 Or Abs(L\G# - Float#(L\DestG)) > 0.1 Or Abs(L\B# - Float#(L\DestB)) > 0.1
			; Make sure light is visible
			ShowEntity(L\EN)

			; Interpolate colour values
			C# = L\DestR
			If L\R# < C#
				L\R# = L\R# + (2.0 * Delta#)
				If L\R# > C# Then L\R# = C#
			Else
				L\R# = L\R# - (2.0 * Delta#)
				If L\R# < C# Then L\R# = C#
			EndIf
			C# = L\DestG
			If L\G# < C#
				L\G# = L\G# + (2.0 * Delta#)
				If L\G# > C# Then L\G# = C#
			Else
				L\G# = L\G# - (2.0 * Delta#)
				If L\G# < C# Then L\G# = C#
			EndIf
			C# = L\DestB
			If L\B# < C#
				L\B# = L\B# + (2.0 * Delta#)
				If L\B# > C# Then L\B# = C#
			Else
				L\B# = L\B# - (2.0 * Delta#)
				If L\B# < C# Then L\B# = C#
			EndIf
			LightColor(L\EN, L\R#, L\G#, L\B#)

			; Hide light if appropriate
			If Abs(L\R#) < 0.5 And Abs(L\G#) < 0.5 And Abs(L\B#) < 0.5 Then HideEntity(L\EN)
		EndIf
	Next

End Function

; Removes all particles from a given emitter which are beneath a water area or a catch plane
Function RemoveUnderwaterParticles(E)

	Em.RP_Emitter = Object.RP_Emitter(EntityName$(E))
	If Em <> Null
		; Collide with water
		For W.Water = Each Water
			MinX# = EntityX#(W\EN) - (W\ScaleX# / 2.0)
			MinZ# = EntityZ#(W\EN) - (W\ScaleZ# / 2.0)
			MaxX# = MinX# + W\ScaleX#
			MaxZ# = MinZ# + W\ScaleZ#
			Y# = EntityY#(W\EN)
			For P.RP_Particle = Each RP_Particle
				; Particle belongs to this emitter
				If P\E = Em
					; Below water level
					If P\Y# < Y#
						; Inside water boundaries
						If P\X# > MinX# And P\X# < MaxX#
							If P\Z# > MinZ# And P\Z# < MaxZ#
								; Remove particle
								P\TimeToLive# = 0.0
							EndIf
						EndIf
					EndIf
				EndIf
			Next
		Next

		; Collide with catch planes
		For CP.CatchPlane = Each CatchPlane
			For P.RP_Particle = Each RP_Particle
				; Particle belongs to this emitter
				If P\E = Em
					; Below water level
					If P\Y# < CP\Y#
						; Inside water boundaries
						If P\X# > CP\MinX# And P\X# < CP\MaxX#
							If P\Z# > CP\MinZ# And P\Z# < CP\MaxZ#
								; Remove particle
								P\TimeToLive# = 0.0
							EndIf
						EndIf
					EndIf
				EndIf
			Next
		Next
	EndIf

End Function