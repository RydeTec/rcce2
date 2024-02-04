;##############################################################################################################################
; Realm Crafter version 1.10																									
; Copyright (C) 2007 Solstar Games, LLC. All rights reserved																	
; contact@solstargames.com																																																		
;																																																																#
; Programmer: Rob Williams																										
; Program: Realm Crafter Actors module
;																																
;This is a licensed product:
;BY USING THIS SOURCECODE, YOU ARE CONFIRMING YOUR ACCEPTANCE OF THE SOFTWARE AND AGREEING TO BECOME BOUND BY THE TERMS OF 
;THIS AGREEMENT. IF YOU DO NOT AGREE TO BE BOUND BY THESE TERMS, THEN DO NOT USE THE SOFTWARE.
;																		
;Licensee may NOT: 
; (i)   create any derivative works of the Engine, including translations Or localizations, other than Games;
; (ii)  redistribute, encumber, sell, rent, lease, sublicense, Or otherwise transfer rights To the Engine; or
; (iii) remove Or alter any trademark, logo, copyright Or other proprietary notices, legends, symbols Or labels in the Engine.
; (iv)   licensee may Not distribute the source code Or documentation To the engine in any manner, unless recipient also has a 
;       license To the Engine.													
; (v)  use the Software to develop any software or other technology having the same primary function as the Software, 
;       including but not limited to using the Software in any development or test procedure that seeks to develop like 
;       software or other technology, or to determine if such software or other technology performs in a similar manner as the
;       Software																																
;##############################################################################################################################
; Realm Crafter Areas module by Rob W (rottbott@hotmail.com), August 2004

Const MaxFogFar# = 2000.0

Global SkyEN, CloudEN, StarsEN
Global SkyTexID = -1, CloudTexID = -1, StormCloudTexID = 65535, StarsTexID = -1
Global FogR=200, FogG=200, FogB=200, FogNear#=0.0, FogFar#=2000.0
Global Outdoors
Global AmbientR = 100, AmbientG = 100, AmbientB = 100
Global DefaultLightPitch#, DefaultLightYaw#
Global LoadingTexID = 65535, LoadingMusicID = 65535
Global MapTexID
Global SlopeRestrict# = 0.6

Type Remove_Surf
	Field ID
End Type
Type Cluster
	Field XC#, YC#, ZC#
	Field Mesh, Surf[200]
End Type

Type Scenery
	Field SceneryID ; Set by user, used for scenery ownerships
	Field EN, MeshID
	Field AnimationMode ; 0 = no animation, 1 = constant animation (loops), 2 = constant (ping-pongs), 3 = animate when selected
	Field ScaleX#, ScaleY#, ScaleZ#
	Field Lightmap$     ; Lightmap filename
	Field RCTE$         ; Used by toolbox editors only
	Field TextureID    ; To alter the texture loaded automatically with the model, if required (65535 to ignore)
	Field CatchRain
	Field Locked
	Field Collides
End Type

Type Water
	Field EN, TexID, Opacity
	Field Red, Green, Blue
	Field ScaleX#, ScaleZ#
	Field TexHandle, TexScale#, U#, V#
	Field ServerWater ; Used by editor only
	Field X#, Y#, Z#
End Type

Type ColBox
	Field EN
	Field ScaleX#, ScaleY#, ScaleZ#
	Field X#, Y#, Z#
	Field Pitch#, Yaw#, Roll#
End Type

Type Emitter
	Field Config, ConfigName$, EN, TexID
	Field X#, Y#, Z#
	Field Pitch#, Yaw#, Roll#
End Type

Type Terrain
	Field EN, DetailTex
	Field BaseTexID, DetailTexID
	Field DetailTexScale#
	Field ScaleX#, ScaleY#, ScaleZ#
	Field Detail, Morph, Shading
End Type

Type SoundZone
	Field EN, Radius#
	Field SoundID, MusicID ; Can be one or the other
	Field RepeatTime ; Number of seconds to wait before repeating the sound, or -1 to play it once only
	Field Volume ; Volume (1% - 100%)
	Field LoadedSound, MusicFilename$
	Field Is3D
	Field Channel, Timer, Fade# ; Variables for updating the sound zone in the client
	Field X#, Y#, Z#
End Type

; Generated from scenery objects to prevent rain/snow particles falling through
Type CatchPlane
	Field MinX#, MinZ#, MaxX#, MaxZ#, Y#
End Type

; Creates a subdivided plane (used for water)
Function CreateSubdividedPlane(XDivs, ZDivs, UScale# = 1.0, VScale# = 1.0, Parent = 0)

	EN = CreateMesh(Parent)
	Surf = CreateSurface(EN)

	For x = 0 To XDivs - 1
		For z = 0 To ZDivs - 1
			XPos# = Float#(x) / Float#(XDivs - 1)
			ZPos# = Float#(z) / Float#(ZDivs - 1)
			V = AddVertex(Surf, XPos#, 0.0, ZPos#, XPos# * UScale#, ZPos# * VScale#)
			VertexNormal(Surf, V, 0.0, 1.0, 0.0)
			If x > 0 And z > 0
				v1 = ((x - 1) * ZDivs) + (z - 1)
				v2 = ((x - 1)* ZDivs) + z
				v3 = (x * ZDivs) + (z - 1)
				v4 = (x * ZDivs) + z
				AddTriangle(Surf, v1, v2, v4)
				AddTriangle(Surf, v1, v4, v3)
			EndIf
		Next
	Next
	PositionMesh(EN, -0.5, 0.0, -0.5)
	Return EN

End Function

; Loads the client (3D) data for an area
Function LoadArea(Name$, CameraEN, DisplayItems = False, UpdateRottNet = False)

	; RottNet update
	RNUpdateTime = MilliSecs()

	LockMeshes()
	LockTextures()

	; Open file
	F = ReadFile("Data\Areas\" + Name$ + ".dat")
	If F = 0 Then Return False

		; Loading screen
		LoadingTexID = ReadShort(F)
		LoadingMusicID = ReadShort(F)
		If DisplayItems = False
			; Progress bar
			LoadProgressBar = GY_CreateProgressBar(0, 0.3, 0.9, 0.4, 0.03, 0, 100, 255, 255, 255, -3012)
			; Preset image
			LoadScreen = CreateMesh(GY_Cam)
			Surf = CreateSurface(LoadScreen)
			v1 = AddVertex(Surf, 0.0, -1.0, 0.0, 0.0, 1.0)
			v2 = AddVertex(Surf, 1.0, -1.0, 0.0, 1.0, 1.0)
			v3 = AddVertex(Surf, 1.0, 0.0, 0.0, 1.0, 0.0)
			v4 = AddVertex(Surf, 0.0, 0.0, 0.0, 0.0, 0.0)
			AddTriangle Surf, v3, v2, v1
			AddTriangle Surf, v4, v3, v1
			ScaleMesh LoadScreen, 20.0, 15.0, 1.0
			PositionEntity LoadScreen, -10.0, 7.5, 10.0
			EntityOrder LoadScreen, -3011
			EntityFX LoadScreen, 1 + 8
			If LoadingTexID < 65535
				Tex = GetTexture(LoadingTexID)
				If Tex <> 0
					EntityTexture(LoadScreen, Tex)
					UnloadTexture(LoadingTexID)
				EndIf
			; Random image
			ElseIf RandomImages > 0
				D = ReadDir("Data\Textures\Random")
				If D = 0
					EntityColor(LoadScreen, 0, 0, 0)
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
							EntityColor(LoadScreen, 0, 0, 0)
						Else
							EntityTexture(LoadScreen, Tex)
							FreeTexture(Tex)
						EndIf
					Else
						EntityColor(LoadScreen, 0, 0, 0)
					EndIf
					CloseDir(D)
				EndIf
			; No image
			Else
				EntityColor(LoadScreen, 0, 0, 0)
			EndIf
			; Music
			If LoadingMusicID < 65535 Then CLoadMusic = PlayMusic(GetMusicName$(LoadingMusicID))
		EndIf

		; Loading bar update
		If LoadScreen <> 0
			GY_UpdateProgressBar(LoadProgressBar, 0)
			RenderWorld()
			Flip()
		EndIf

		; Environment
		SkyTexID = ReadShort(F)
		CloudTexID = ReadShort(F)
		StormCloudTexID = ReadShort(F)
		StarsTexID = ReadShort(F)

		FogR = ReadByte(F)
		FogG = ReadByte(F)
		FogB = ReadByte(F)
		FogNear# = ReadFloat#(F)
		FogFar#  = ReadFloat#(F)
		If FogFar# > MaxFogFar# Then FogFar# = MaxFogFar#
		FogNearNow# = FogNear#
		FogFarNow# = FogFar#

		; Sky
		If SkyTexID > -1 And SkyTexID < 65535
			EntityTexture(SkyEN, GetTexture(SkyTexID))
			EntityAlpha(SkyEN, 1.0)
		Else
			EntityAlpha(SkyEN, 0.0)
		EndIf
		If CloudTexID > -1 And CloudTexID < 65535
			EntityTexture(CloudEN, GetTexture(CloudTexID))
			EntityAlpha(CloudEN, 0.4)
		ElseIf StormCloudTexID > -1 And StormCloudTexID < 65535
			EntityTexture(CloudEN, GetTexture(StormCloudTexID))
			EntityAlpha(CloudEN, 0.4)
		Else
			EntityAlpha(CloudEN, 0.0)
		EndIf
		If StarsTexID > -1 And StarsTexID < 65535
			EntityTexture(StarsEN, GetTexture(StarsTexID))
			EntityAlpha(StarsEN, 1.0)
		Else
			EntityAlpha(StarsEN, 0.0)
		EndIf

		; Camera
		If CameraEN <> 0
			SetViewDistance(CameraEN, FogNear#, FogFar#)
			CameraFogColor(CameraEN, FogR, FogG, FogB)
			CameraClsColor(CameraEN, FogR, FogG, FogB)
		EndIf

		MapTexID = ReadShort(F)
		Outdoors = ReadByte(F)
		AmbientR = ReadByte(F)
		AmbientG = ReadByte(F)
		AmbientB = ReadByte(F)
		DefaultLightPitch# = ReadFloat#(F)
		DefaultLightYaw# = ReadFloat#(F)
		SlopeRestrict# = ReadFloat#(F)
		AmbientLight(AmbientR, AmbientG, AmbientB)

		; RottNet update
		If UpdateRottNet = True And MilliSecs() - RNUpdateTime > 500 Then RCE_Update() : RCE_CreateMessages() : RNUpdateTime = MilliSecs()

		; Loading bar update
		If LoadScreen <> 0
			GY_UpdateProgressBar(LoadProgressBar, 5)
			RenderWorld()
			Flip()
		EndIf

		; Scenery
		Sceneries = ReadShort(F)
		For i = 1 To Sceneries
			S.Scenery = New Scenery
			; Mesh (from media database ID)
			S\MeshID = ReadShort(F)

			; Nasty hack to disable decryption on RCTE terrains in a subfolder
			NoDecrypt = False
			Name$ = Upper$(GetMeshName$(S\MeshID))
			If Instr(Name$, "RCTE\") = 1 Or Instr(Name$, "RCTE/") = 1
				If Instr(Name$, "\", 6) > 0 Or Instr(Name$, "/", 6) > 0 Then NoDecrypt = True
			EndIf
			; Load the mesh
			S\EN = GetMesh(S\MeshID, False)

			; Read position/rotation/scale
			X# = ReadFloat#(F) : Y# = ReadFloat#(F) : Z# = ReadFloat#(F)
			Pitch# = ReadFloat#(F) : Yaw# = ReadFloat#(F) : Roll# = ReadFloat#(F)
			S\ScaleX# = ReadFloat#(F) : S\ScaleY# = ReadFloat#(F) : S\ScaleZ# = ReadFloat#(F)
			; Animation mode and ownership ID
			S\AnimationMode = ReadByte(F)
			S\SceneryID = ReadByte(F)
			; Retexturing
			S\TextureID = ReadShort(F)
			; Collision/picking
			S\CatchRain = ReadByte(F)
			Collides = ReadByte(F)
			; Lightmap information and RCTE data
			S\Lightmap$ = ReadString$(F)
			S\RCTE$ = ReadString$(F)

			If S\EN <> 0
				; Toolbox extras
				If Len(S\RCTE$) > 5
					Select Left$(S\RCTE$, 5)
						Case "_TREE"
							If DisplayItems = False
								swingsty = Int(Mid$(S\RCTE$, 6, 1))
								evergrn = Int(Mid$(S\RCTE$, 7, 1))
								S\EN = LoadTree("", evergrn, S\EN, swingsty)
							EndIf
						Case "_GRSS"
							swingsty = Int(Mid$(S\RCTE$, 6, 1))
							evergrn = Int(Mid$(S\RCTE$, 7, 1))
							S\EN = LoadGrass("", evergrn, S\EN, swingsty)
						Case "_TRRN", "_RCDN"
							If DisplayItems = False
								RotateMesh(S\EN, Pitch#, Yaw#, Roll#)
								ScaleMesh(S\EN, S\ScaleX#, S\ScaleY#, S\ScaleZ#)
								ChunkTerrain(S\EN, 3, 3, 3, X#, Y#, Z#)
								Delete S
								Goto CancelScenery
							EndIf
					End Select
				EndIf

				; Set position/rotation
				PositionEntity S\EN, X#, Y#, Z# : RotateEntity S\EN, Pitch#, Yaw#, Roll#
				ScaleEntity S\EN, S\ScaleX#, S\ScaleY#, S\ScaleZ#

				; Chunking for dungeons etc.
				If DisplayItems = False
					Name$ = Upper$(GetMeshName$(S\MeshID))
					If Instr(Name$, "RCDUNGEON\") Or Instr(Name$, "CUSTOMCHUNK\")
						RotateMesh(S\EN, Pitch#, Yaw#, Roll#)
						ScaleMesh(S\EN, S\ScaleX#, S\ScaleY#, S\ScaleZ#)
						ChunkTerrain(S\EN, 3, 3, 3, X#, Y#, Z#)
						Delete S
						Goto CancelScenery
					EndIf
				EndIf

				; Lightmap
				If S\Lightmap$ <> ""
					LMap = LoadTexture("Data\Textures\Lightmaps\" + S\Lightmap$)
					TextureCoords(LMap, 1)
					EntityTexture(S\EN, LMap, 0, 1)
					FreeTexture(LMap)
				EndIf

				; Retexturing
				If S\TextureID < 65535 Then EntityTexture S\EN, GetTexture(S\TextureID)

				; Type handle
				NameEntity S\EN, Handle(S)

				; Animation
				If DisplayItems = False
					If S\AnimationMode = 1
						Animate(S\EN, 1)
					ElseIf S\AnimationMode = 2
						Animate(S\EN, 2)
					EndIf
				EndIf

				; Set collision/picking
				EntityType(S\EN, Collides)
				If Collides = C_Sphere
					EntityPickMode(S\EN, 1)
					MaxLength# = MeshWidth#(S\EN) * S\ScaleX#
					If MeshDepth#(S\EN) * S\ScaleZ# > MaxLength# Then MaxLength# = MeshDepth#(S\EN) * S\ScaleZ#
					EntityRadius(S\EN, MaxLength# / 2.0, (MeshHeight#(S\EN) * S\ScaleY#) / 2.0)
				ElseIf Collides = C_Triangle
					EntityPickMode(S\EN, 2)
				ElseIf Collides = C_Box
					EntityPickMode(S\EN, 3)
					Width# = MeshWidth#(S\EN) * S\ScaleX#
					Height# = MeshHeight#(S\EN) * S\ScaleY#
					Depth# = MeshDepth#(S\EN) * S\ScaleZ#
					EntityBox(S\EN, Width# / -2.0, Height# / -2.0, Depth# / -2.0, Width#, Height#, Depth#)
				EndIf
				ResetEntity(S\EN)

				; Create catch plane if required
				If S\CatchRain And DisplayItems = False
					MMV.MeshMinMaxVertices = MeshMinMaxVerticesTransformed(S\EN, Pitch#, Yaw#, Roll#, S\ScaleX#, S\ScaleY#, S\ScaleZ#)
					CP.CatchPlane = New CatchPlane
					CP\Y# = MMV\MaxY# + Y#
					CP\MinX# = MMV\MinX# + X#
					CP\MinZ# = MMV\MinZ# + Z#
					CP\MaxX# = MMV\MaxX# + X#
					CP\MaxZ# = MMV\MaxZ# + Z#
				EndIf
			; Mesh has been deleted or removed from the database!
			Else
				If DisplayItems = True
					Delete(S)
				Else
					RuntimeError("Could not find model with ID " + S\MeshID)
				EndIf
			EndIf
			.CancelScenery

			; RottNet update
			If UpdateRottNet = True And MilliSecs() - RNUpdateTime > 500 Then RCE_Update() : RCE_CreateMessages() : RNUpdateTime = MilliSecs()

			; Loading bar update every alternate object
			If LoadScreen <> 0 And (i Mod 2) = 0
				GY_UpdateProgressBar(LoadProgressBar, 5 + (40 * i) / Sceneries)
				RenderWorld()
				Flip()
			EndIf
		Next

		; Water
		Waters = ReadShort(F)
		For i = 1 To Waters
			W.Water = New Water
			; Entity/Texture
			W\TexID = ReadShort(F)
			W\TexHandle = GetTexture(W\TexID, True)
			W\TexScale# = ReadFloat#(F)
			; Position/size
			X# = ReadFloat#(F) : Y# = ReadFloat#(F) : Z# = ReadFloat#(F)
			W\ScaleX# = ReadFloat#(F) : W\ScaleZ# = ReadFloat#(F)
			XDivs = Ceil(W\ScaleX# / 15.0)
			ZDivs = Ceil(W\ScaleZ# / 15.0)
			If XDivs > 70 Then XDivs = 70
			If ZDivs > 70 Then ZDivs = 70
			W\EN = CreateSubdividedPlane(XDivs, ZDivs, W\ScaleX#, W\ScaleZ#)
			ScaleEntity W\EN, W\ScaleX#, 1.0, W\ScaleZ#
			PositionEntity W\EN, X#, Y#, Z#
			ScaleTexture W\TexHandle, W\TexScale#, W\TexScale#
			EntityTexture W\EN, W\TexHandle
			; Colour
			W\Red = ReadByte(F)
			W\Green = ReadByte(F)
			W\Blue = ReadByte(F)
			; Opacity
			W\Opacity = ReadByte(F)
			If W\Opacity >= 100
				EntityFX(W\EN, 1 + 16)
			Else
				EntityFX(W\EN, 16)
			EndIf
			Alpha# = Float#(W\Opacity) / 100.0
			If Alpha# > 1.0 Then Alpha# = 1.0
			EntityAlpha(W\EN, Alpha#)
			; Picking
			EntityBox W\EN, W\ScaleX# / -2.0, -1.0, W\ScaleZ# / -2.0, W\ScaleX#, 2.0, W\ScaleZ#
			; Type handle
			NameEntity W\EN, Handle(W)
			; If I am a walking only character, create a collision box here
			If DisplayItems = False And Me.ActorInstance <> Null
				If Me\Actor\Environment = Environment_Walk
					C.ColBox = New ColBox
					C\EN = CreateCube()
					EntityAlpha C\EN, 0.0
					; Position/rotation/size
					Y# = Y# - 1000.0
					C\ScaleX# = Abs(W\ScaleX# / 2.0) : C\ScaleY# = 1000.0 : C\ScaleZ# = Abs(W\ScaleZ# / 2.0)
					PositionEntity C\EN, X#, Y#, Z#
					ScaleEntity C\EN, C\ScaleX#, C\ScaleY#, C\ScaleZ#
					; Collisions
					EntityBox C\EN, -C\ScaleX#, -C\ScaleY#, -C\ScaleZ#, C\ScaleX# * 2.0, C\ScaleY# * 2.0, C\ScaleZ# * 2.0
					EntityType C\EN, C_Box
					; Type handle
					NameEntity C\EN, Handle(C)
				EndIf
			EndIf
			; RottNet update
			If UpdateRottNet = True And MilliSecs() - RNUpdateTime > 500 Then RCE_Update() : RCE_CreateMessages() : RNUpdateTime = MilliSecs()
		Next

		; Loading bar update
		If LoadScreen <> 0
			GY_UpdateProgressBar(LoadProgressBar, 60)
			RenderWorld()
			Flip()
		EndIf

		; Collision zones
		ColBoxes = ReadShort(F)
		For i = 1 To ColBoxes
			C.ColBox = New ColBox
			C\EN = CreateCube()
			If DisplayItems = True Then EntityAlpha C\EN, 0.4 Else EntityAlpha C\EN, 0.0
			; Position/rotation/size
			X# = ReadFloat#(F) : Y# = ReadFloat#(F) : Z# = ReadFloat#(F)
			Pitch# = ReadFloat#(F) : Yaw# = ReadFloat#(F) : Roll# = ReadFloat#(F)
			C\ScaleX# = ReadFloat#(F) : C\ScaleY# = ReadFloat#(F) : C\ScaleZ# = ReadFloat#(F)
			PositionEntity C\EN, X#, Y#, Z#
			RotateEntity C\EN, Pitch#, Yaw#, Roll#
			ScaleEntity C\EN, C\ScaleX#, C\ScaleY#, C\ScaleZ#
			; Collisions
			EntityBox C\EN, -C\ScaleX#, -C\ScaleY#, -C\ScaleZ#, C\ScaleX# * 2.0, C\ScaleY# * 2.0, C\ScaleZ# * 2.0
			EntityType C\EN, C_Box
			; Type handle
			NameEntity C\EN, Handle(C)
		Next

		; Loading bar update
		If LoadScreen <> 0
			GY_UpdateProgressBar(LoadProgressBar, 65)
			RenderWorld()
			Flip()
		EndIf

		; Emitters
		Emitters = ReadShort(F)
		For i = 1 To Emitters
			; Create emitter parent entity
			E.Emitter = New Emitter
			If DisplayItems = True
				E\EN = CreateCone() : ScaleMesh E\EN, 3, 3, 3 : EntityAlpha E\EN, 0.5
			Else
				E\EN = CreatePivot()
			EndIf
			; Read in emitter data
			E\ConfigName$ = ReadString$(F)
			E\TexID = ReadShort(F)
			Texture = GetTexture(E\TexID)
			X# = ReadFloat#(F) : Y# = ReadFloat#(F) : Z# = ReadFloat#(F)
			Pitch# = ReadFloat#(F) : Yaw# = ReadFloat#(F) : Roll# = ReadFloat#(F)
			; Load config
			E\Config = RP_LoadEmitterConfig("Data\Emitter Configs\" + E\ConfigName$ + ".rpc", Texture, CameraEN)
			; Loaded successfully, create the emitter
			If E\Config <> 0
				EmitterEN = RP_CreateEmitter(E\Config)
				EntityParent EmitterEN, E\EN, False
				EntityPickMode E\EN, 2
				NameEntity E\EN, Handle(E)
				; Position/rotation
				PositionEntity E\EN, X#, Y#, Z#
				RotateEntity E\EN, Pitch#, Yaw#, Roll#
			; Failed to load config, remove the emitter and display an error message if running on client
			Else
				If DisplayItems = False Then RuntimeError("Could not load emitter: " + E\ConfigName$)
				FreeEntity(E\EN)
				Delete(E)
			EndIf

			; RottNet update
			If UpdateRottNet = True And MilliSecs() - RNUpdateTime > 500 Then RCE_Update() : RCE_CreateMessages() : RNUpdateTime = MilliSecs()
		Next

		; Loading bar update
		If LoadScreen <> 0
			GY_UpdateProgressBar(LoadProgressBar, 80)
			RenderWorld()
			Flip()
		EndIf

		; Blitz LOD terrains
		Terrains = ReadShort(F)
		For i = 1 To Terrains
			T.Terrain = New Terrain
			; Textures
			T\BaseTexID = ReadShort(F)
			T\DetailTexID = ReadShort(F)
			; Terrain heights
			GridSize = ReadInt(F)
			T\EN = CreateTerrain(GridSize)
			For X = 0 To TerrainSize(T\EN)
				For Z = 0 To TerrainSize(T\EN)
					ModifyTerrain(T\EN, X, Z, ReadFloat#(F), False)
				Next
			Next
			; Position/rotation/size
			X# = ReadFloat#(F) : Y# = ReadFloat#(F) : Z# = ReadFloat#(F)
			Pitch# = ReadFloat#(F) : Yaw# = ReadFloat#(F) : Roll# = ReadFloat#(F)
			T\ScaleX# = ReadFloat#(F)
			T\ScaleY# = ReadFloat#(F)
			T\ScaleZ# = ReadFloat#(F)
			PositionEntity T\EN, X#, Y#, Z# : RotateEntity T\EN, Pitch#, Yaw#, Roll#
			ScaleEntity T\EN, T\ScaleX#, T\ScaleY#, T\ScaleZ#
			; Texture scale
			T\DetailTexScale# = ReadFloat#(F)
			; Apply textures
			Tex = GetTexture(T\BaseTexID, True)
			If Tex <> 0
				ScaleTexture(Tex, TerrainSize(T\EN), TerrainSize(T\EN))
				EntityTexture(T\EN, Tex, 0, 0)
				FreeTexture(Tex)
			EndIf
			If T\DetailTexID > 0 And T\DetailTexID < 65535
				T\DetailTex = GetTexture(T\DetailTexID, True)
				ScaleTexture(T\DetailTex, T\DetailTexScale#, T\DetailTexScale#)
				EntityTexture(T\EN, T\DetailTex, 0, 1)
				If DisplayItems = False
					FreeTexture(T\DetailTex)
					T\DetailTex = 0
				EndIf
			EndIf
			; Detail etc.
			T\Detail = ReadInt(F)
			T\Morph = ReadByte(F)
			T\Shading = ReadByte(F)
			TerrainDetail(T\EN, T\Detail, T\Morph)
			TerrainShading(T\EN, T\Shading)
			; Collisions
			EntityType T\EN, C_Triangle
			EntityPickMode T\EN, 2
			; Type handle
			NameEntity T\EN, Handle(T)

			; RottNet update
			If UpdateRottNet = True And MilliSecs() - RNUpdateTime > 500 Then RCE_Update() : RCE_CreateMessages() : RNUpdateTime = MilliSecs()

			; Loading bar update
			If LoadScreen <> 0
				GY_UpdateProgressBar(LoadProgressBar, 80 + ((15 * i) / Terrains))
				RenderWorld()
				Flip()
			EndIf
		Next

		; Loading bar update
		If LoadScreen <> 0
			GY_UpdateProgressBar(LoadProgressBar, 95)
			RenderWorld()
			Flip()
		EndIf

		; Sound zones
		Sounds = ReadShort(F)
		For i = 1 To Sounds
			SZ.SoundZone = New SoundZone
			If DisplayItems = True
				SZ\EN = CreateSphere()
				EntityAlpha SZ\EN, 0.5
				EntityColor SZ\EN, 255, 255, 0
			Else
				SZ\EN = CreatePivot()
			EndIf
			; Position/size
			X# = ReadFloat#(F) : Y# = ReadFloat#(F) : Z# = ReadFloat#(F)
			SZ\Radius# = ReadFloat#(F)
			ScaleEntity SZ\EN, SZ\Radius#, SZ\Radius#, SZ\Radius#
			PositionEntity SZ\EN, X#, Y#, Z#
			; Sound options
			SZ\SoundID = ReadShort(F)
			SZ\MusicID = ReadShort(F)
			SZ\RepeatTime = ReadInt(F)
			SZ\Volume = ReadByte(F)
			; Load sound
			If SZ\SoundID <> 65535
				SZ\LoadedSound = GetSound(SZ\SoundID)
				SZ\Is3D = Asc(Right$(GetSoundName$(SZ\SoundID), 1))
			Else
				SZ\MusicFilename$ = "Data\Music\" + GetMusicName$(SZ\MusicID)
			EndIf
			; Type handle
			NameEntity SZ\EN, Handle(SZ)
			; RottNet update
			If UpdateRottNet = True And MilliSecs() - RNUpdateTime > 500 Then RCE_Update() : RCE_CreateMessages() : RNUpdateTime = MilliSecs()
		Next

	CloseFile(F)

	UnlockMeshes()
	UnlockTextures()

	; End loading screen
	If LoadScreen <> 0
		FreeEntity(LoadScreen)
		GY_FreeGadget(LoadProgressBar)
	EndIf
	If ChannelPlaying(CLoadMusic) = True Then StopChannel(CLoadMusic)

	Return True

End Function

; Loads the client (3D) data for an area
Function LoadAreaTE(Name$)
	
	; Open file
	F = ReadFile(Name$)
	If F = 0 Then Return False
	
		; Loading screen
	LoadingTexID = ReadShort(F)
	LoadingMusicID = ReadShort(F)
	
		; Environment
	SkyTexID = ReadShort(F)
	CloudTexID = ReadShort(F)
	StormCloudTexID = ReadShort(F)
	StarsTexID = ReadShort(F)
	
	FogR = ReadByte(F)
	FogG = ReadByte(F)
	FogB = ReadByte(F)
	FogNear# = ReadFloat#(F)
	FogFar#  = ReadFloat#(F)
	
	MapTexID = ReadShort(F)
	Outdoors = ReadByte(F)
	AmbientR = ReadByte(F)
	AmbientG = ReadByte(F)
	AmbientB = ReadByte(F)
	DefaultLightPitch# = ReadFloat#(F)
	DefaultLightYaw# = ReadFloat#(F)
	SlopeRestrict# = ReadFloat#(F)
	
		; Scenery
	Sceneries = ReadShort(F)
	For i = 1 To Sceneries
		dm.dropmodel = New dropmodel
		dm\id = ReadShort(F)
		dm\x# = ReadFloat#(F) : dm\y# = ReadFloat#(F) : dm\z# = ReadFloat#(F)
		dm\pitch# = ReadFloat#(F) : dm\yaw# = ReadFloat#(F) : dm\roll# = ReadFloat#(F)
		dm\sx# = ReadFloat#(F) : dm\sy# = ReadFloat#(F) : dm\sz# = ReadFloat#(F)
		dm\AnimationMode = ReadByte(F)
		dm\SceneryID = ReadByte(F)
		dm\TextureID = ReadShort(F)
		dm\CatchRain = ReadByte(F)
		dm\Collides = ReadByte(F)
		dm\Lightmap$ = ReadString$(F)
		dm\rcTe$ = ReadString$(F)
		
		If Left$(dm\rcTe$,5)<>"_TRRN"
			ChangeDir thispath$
			dm\ent = GetMesh(dm\id)
			dm\ModelFileName$=getmeshname$(dm\id)
			dm\modelfilepath$=dm\ModelFileName$
			dm\Alpha# = 1.0
			If dm\ent <> 0
				PositionEntity dm\ent,dm\x,dm\y,dm\z
				ScaleEntity dm\ent,dm\sx,dm\sy,dm\sz
				RotateEntity dm\ent,dm\pitch,dm\yaw,dm\roll
				EntityFX dm\ent,2
			Else
				RuntimeError("Could not find model with ID " + dm\id)
			EndIf
		Else
			loadworkpath$ = dm\rcTe$
			Delete dm
		EndIf
	Next
	
	
		; Water
	Waters = ReadShort(F)
	For i = 1 To Waters
		W.Water = New Water
		W\TexID = ReadShort(F)
		W\TexHandle = GetTexture(W\TexID, True)
		W\TexScale# = ReadFloat#(F)
		W\X# = ReadFloat#(F) : W\Y# = ReadFloat#(F) : W\Z# = ReadFloat#(F)
		W\ScaleX# = ReadFloat#(F) : W\ScaleZ# = ReadFloat#(F)
		W\Red = ReadByte(F)
		W\Green = ReadByte(F)
		W\Blue = ReadByte(F)
		W\Opacity = ReadByte(F)
	Next
	
		; Collision zones
	ColBoxes = ReadShort(F)
	For i = 1 To ColBoxes
		C.ColBox = New ColBox
		C\X# = ReadFloat#(F) : C\Y# = ReadFloat#(F) : C\Z# = ReadFloat#(F)
		C\Pitch# = ReadFloat#(F) : C\Yaw# = ReadFloat#(F) : C\Roll# = ReadFloat#(F)
		C\ScaleX# = ReadFloat#(F) : C\ScaleY# = ReadFloat#(F) : C\ScaleZ# = ReadFloat#(F)
	Next
	
	
		; Emitters
	Emitters = ReadShort(F)
	For i = 1 To Emitters
		E.Emitter = New Emitter
		E\ConfigName$ = ReadString$(F)
		E\TexID = ReadShort(F)
		E\X# = ReadFloat#(F) : E\Y# = ReadFloat#(F) : E\Z# = ReadFloat#(F)
		E\Pitch# = ReadFloat#(F) : E\Yaw# = ReadFloat#(F) : E\Roll# = ReadFloat#(F)
	Next
	
		; Blitz LOD terrains
	Terrains = ReadShort(F)
	If Terrains = 0 ; Test if Blitz LOD Terrains are being used
			; Sound zones
		Sounds = ReadShort(F)
		For i = 1 To Sounds
			SZ.SoundZone = New SoundZone
			SZ\X# = ReadFloat#(F) : SZ\Y# = ReadFloat#(F) : SZ\Z# = ReadFloat#(F)
			SZ\Radius# = ReadFloat#(F)
			SZ\SoundID = ReadShort(F)
			SZ\MusicID = ReadShort(F)
			SZ\RepeatTime = ReadInt(F)
			SZ\Volume = ReadByte(F)
		Next
	Else
		RuntimeError("Blitz Terrain already in use!")
	EndIf	
	
	CloseFile(F)
	
End Function

; Saves the current area back to file
Function SaveAreaTE(Name$)
	
	F = WriteFile("Data\Areas\" + Name$ + ".dat")
	If F = 0 Then Return False
	
		; Loading screen
	WriteShort F, LoadingTexID
	WriteShort F, LoadingMusicID
	
		; Environment
	WriteShort F, SkyTexID
	WriteShort F, CloudTexID
	WriteShort F, StormCloudTexID
	WriteShort F, StarsTexID
	
	WriteByte F, FogR
	WriteByte F, FogG
	WriteByte F, FogB
	WriteFloat F, FogNear#
	WriteFloat F, FogFar#
	
	WriteShort F, MapTexID
	WriteByte F, Outdoors
	WriteByte F, AmbientR
	WriteByte F, AmbientG
	WriteByte F, AmbientB
	WriteFloat F, DefaultLightPitch#
	WriteFloat F, DefaultLightYaw#
	WriteFloat F, SlopeRestrict#
	
		; Scenery
	Count = 0
	For S.Scenery = Each Scenery : Count = Count + 1 : Next
	WriteShort F, Count
	For S.Scenery = Each Scenery
		WriteShort F, S\MeshID
		WriteFloat F, EntityX#(S\EN, True)
		WriteFloat F, EntityY#(S\EN, True)
		WriteFloat F, EntityZ#(S\EN, True)
		WriteFloat F, EntityPitch#(S\EN, True)
		WriteFloat F, EntityYaw#(S\EN, True)
		WriteFloat F, EntityRoll#(S\EN, True)
		WriteFloat F, S\ScaleX#
		WriteFloat F, S\ScaleY#
		WriteFloat F, S\ScaleZ#
		WriteByte F, S\AnimationMode
		WriteByte F, S\SceneryID
		WriteShort F, S\TextureID
		WriteByte F, S\CatchRain
		WriteByte F, S\Collides
		WriteString F, S\Lightmap$
		WriteString F, S\RCTE$ ; Extra data for RTCE
	Next
	
		; Water
	Count = 0
	For W.Water = Each Water : Count = Count + 1 : Next
	WriteShort F, Count
	For W.Water = Each Water
		WriteShort F, W\TexID
		WriteFloat F, W\TexScale#
		WriteFloat F, W\X#
		WriteFloat F, W\Y#
		WriteFloat F, W\Z#
		WriteFloat F, W\ScaleX#
		WriteFloat F, W\ScaleZ#
		WriteByte F, W\Red
		WriteByte F, W\Green
		WriteByte F, W\Blue
		WriteByte F, W\Opacity
	Next
	
		; Collision boxes
	Count = 0
	For C.ColBox = Each ColBox : Count = Count + 1 : Next
	WriteShort F, Count
	For C.ColBox = Each ColBox
		WriteFloat F, C\X#
		WriteFloat F, C\Y#
		WriteFloat F, C\Z#
		WriteFloat F, C\Pitch#
		WriteFloat F, C\Yaw#
		WriteFloat F, C\Roll#
		WriteFloat F, C\ScaleX#
		WriteFloat F, C\ScaleY#
		WriteFloat F, C\ScaleZ#
	Next
	
		; Emitters
	Count = 0
	For E.Emitter = Each Emitter : Count = Count + 1 : Next
	WriteShort F, Count
	For E.Emitter = Each Emitter
		WriteString F, E\ConfigName$
		WriteShort F, E\TexID
		WriteFloat F, E\X#
		WriteFloat F, E\Y#
		WriteFloat F, E\Z#
		WriteFloat F, E\Pitch#
		WriteFloat F, E\Yaw#
		WriteFloat F, E\Roll#
	Next
	
		; Blitz Terrains
	Count = 0
	WriteShort F, Count
	
	
		; Sound zones
	Count = 0
	For SZ.SoundZone = Each SoundZone : Count = Count + 1 : Next
	WriteShort F, Count
	For SZ.SoundZone = Each SoundZone
		WriteFloat F, SZ\X#
		WriteFloat F, SZ\Y#
		WriteFloat F, SZ\Z#
		WriteFloat F, SZ\Radius#
		WriteShort F, SZ\SoundID
		WriteShort F, SZ\MusicID
		WriteInt F, SZ\RepeatTime
		WriteByte F, SZ\Volume
	Next
	
	CloseFile(F)
	Return True
	
End Function


; Saves the current area back to file
Function SaveArea(Name$)

	F = WriteFile("Data\Areas\" + Name$ + ".dat")
	If F = 0 Then Return False

		; Loading screen
		WriteShort F, LoadingTexID
		WriteShort F, LoadingMusicID

		; Environment
		WriteShort F, SkyTexID
		WriteShort F, CloudTexID
		WriteShort F, StormCloudTexID
		WriteShort F, StarsTexID

		WriteByte F, FogR
		WriteByte F, FogG
		WriteByte F, FogB
		WriteFloat F, FogNear#
		WriteFloat F, FogFar#

		WriteShort F, MapTexID
		WriteByte F, Outdoors
		WriteByte F, AmbientR
		WriteByte F, AmbientG
		WriteByte F, AmbientB
		WriteFloat F, DefaultLightPitch#
		WriteFloat F, DefaultLightYaw#
		WriteFloat F, SlopeRestrict#

		; Scenery
		Count = 0
		For S.Scenery = Each Scenery : Count = Count + 1 : Next
		WriteShort F, Count
		For S.Scenery = Each Scenery
			WriteShort F, S\MeshID
			WriteFloat F, EntityX#(S\EN, True)
			WriteFloat F, EntityY#(S\EN, True)
			WriteFloat F, EntityZ#(S\EN, True)
			WriteFloat F, EntityPitch#(S\EN, True)
			WriteFloat F, EntityYaw#(S\EN, True)
			WriteFloat F, EntityRoll#(S\EN, True)
			WriteFloat F, S\ScaleX#
			WriteFloat F, S\ScaleY#
			WriteFloat F, S\ScaleZ#
			WriteByte F, S\AnimationMode
			WriteByte F, S\SceneryID
			WriteShort F, S\TextureID
			WriteByte F, S\CatchRain
			WriteByte F, GetEntityType(S\EN)
			WriteString F, S\Lightmap$
			WriteString F, S\RCTE$ ; Extra data for RTCE
		Next

		; Water
		Count = 0
		For W.Water = Each Water : Count = Count + 1 : Next
		WriteShort F, Count
		For W.Water = Each Water
			WriteShort F, W\TexID
			WriteFloat F, W\TexScale#
			WriteFloat F, EntityX#(W\EN, True)
			WriteFloat F, EntityY#(W\EN, True)
			WriteFloat F, EntityZ#(W\EN, True)
			WriteFloat F, W\ScaleX#
			WriteFloat F, W\ScaleZ#
			WriteByte F, W\Red
			WriteByte F, W\Green
			WriteByte F, W\Blue
			WriteByte F, W\Opacity
		Next

		; Collision boxes
		Count = 0
		For C.ColBox = Each ColBox : Count = Count + 1 : Next
		WriteShort F, Count
		For C.ColBox = Each ColBox
			WriteFloat F, EntityX#(C\EN, True)
			WriteFloat F, EntityY#(C\EN, True)
			WriteFloat F, EntityZ#(C\EN, True)
			WriteFloat F, EntityPitch#(C\EN, True)
			WriteFloat F, EntityYaw#(C\EN, True)
			WriteFloat F, EntityRoll#(C\EN, True)
			WriteFloat F, C\ScaleX#
			WriteFloat F, C\ScaleY#
			WriteFloat F, C\ScaleZ#
		Next

		; Emitters
		Count = 0
		For E.Emitter = Each Emitter : Count = Count + 1 : Next
		WriteShort F, Count
		For E.Emitter = Each Emitter
			WriteString F, E\ConfigName$
			WriteShort F, E\TexID
			WriteFloat F, EntityX#(E\EN, True)
			WriteFloat F, EntityY#(E\EN, True)
			WriteFloat F, EntityZ#(E\EN, True)
			WriteFloat F, EntityPitch#(E\EN, True)
			WriteFloat F, EntityYaw#(E\EN, True)
			WriteFloat F, EntityRoll#(E\EN, True)
		Next

		; Terrains
		Count = 0
		For T.Terrain = Each Terrain :  Count = Count + 1 : Next
		WriteShort F, Count
		For T.Terrain = Each Terrain
			WriteShort F, T\BaseTexID
			WriteShort F, T\DetailTexID
			WriteInt F, TerrainSize(T\EN)
			For X = 0 To TerrainSize(T\EN)
				For Z = 0 To TerrainSize(T\EN)
					WriteFloat F, TerrainHeight#(T\EN, X, Z)
				Next
			Next
			WriteFloat F, EntityX#(T\EN, True)
			WriteFloat F, EntityY#(T\EN, True)
			WriteFloat F, EntityZ#(T\EN, True)
			WriteFloat F, EntityPitch#(T\EN, True)
			WriteFloat F, EntityYaw#(T\EN, True)
			WriteFloat F, EntityRoll#(T\EN, True)
			WriteFloat F, T\ScaleX#
			WriteFloat F, T\ScaleY#
			WriteFloat F, T\ScaleZ#
			WriteFloat F, T\DetailTexScale#
			WriteInt   F, T\Detail
			WriteByte  F, T\Morph
			WriteByte  F, T\Shading
		Next

		; Sound zones
		Count = 0
		For SZ.SoundZone = Each SoundZone : Count = Count + 1 : Next
		WriteShort F, Count
		For SZ.SoundZone = Each SoundZone
			WriteFloat F, EntityX#(SZ\EN, True)
			WriteFloat F, EntityY#(SZ\EN, True)
			WriteFloat F, EntityZ#(SZ\EN, True)
			WriteFloat F, SZ\Radius#
			WriteShort F, SZ\SoundID
			WriteShort F, SZ\MusicID
			WriteInt F, SZ\RepeatTime
			WriteByte F, SZ\Volume
		Next

	CloseFile(F)
	Return True

End Function

; Unloads the current area from memory
Function UnloadArea()

	If SkyTexID > -1 And SkyTexID < 65535 Then UnloadTexture(SkyTexID)
	If CloudTexID > -1 And CloudTexID < 65535 Then UnloadTexture(CloudTexID)
	If StormCloudTexID > -1 And StormCloudTexID < 65535 Then UnloadTexture(StormCloudTexID)
	If StarsTexID > -1 And StarsTexID < 65535 Then UnloadTexture(StarsTexID)

	UnloadTrees(False)

	For S.Scenery = Each Scenery
		If S\TextureID < 65535 Then UnloadTexture(S\TextureID)
		UnloadMesh(S\MeshID)
		FreeEntity(S\EN)
		Delete(S)
	Next

	For W.Water = Each Water
		UnloadTexture(W\TexID)
		FreeTexture(W\TexHandle)
		FreeEntity(W\EN)
		Delete(W)
	Next

	For C.ColBox = Each ColBox
		FreeEntity(C\EN)
		Delete(C)
	Next

	For E.Emitter = Each Emitter
		RP_FreeEmitter(GetChild(E\EN, 1), True, False)
		UnloadTexture(E\TexID)
		FreeEntity(E\EN)
		Delete(E)
	Next

	For SZ.SoundZone = Each SoundZone
		If SZ\Channel <> 0 Then StopChannel(SZ\Channel)
		If SZ\SoundID > 0 And SZ\SoundID < 65535 Then UnloadSound(SZ\SoundID)
		FreeEntity(SZ\EN)
		Delete(SZ)
	Next

	For T.Terrain = Each Terrain
		FreeEntity T\EN
		UnloadTexture(T\BaseTexID)
		If T\DetailTex <> 0 Then FreeTexture(T\DetailTex)
		If T\DetailTexID < 65535 Then UnloadTexture(T\DetailTexID)
		Delete(T)
	Next

	Delete Each CatchPlane

End Function

; Sets the view distance
Function SetViewDistance(CameraEN, Near#, Far#)

	CameraRange CameraEN, 0.8, Far# + 10.0
	CameraFogRange CameraEN, Near#, Far#
	ScaleEntity SkyEN, Far# - 10.0, Far# - 10.0, Far# - 10.0
	ScaleEntity StarsEN, Far# - 10.0, Far# - 10.0, Far# - 10.0
	ScaleEntity CloudEN, Far# - 15.0, Far# - 15.0, Far# - 15.0

End Function

; Splits terrain into smaller segments
Function ChunkTerrain(Mesh, chx# = 10, chy# = 10, chz# = 10, XPos# = 0.0, YPos# = 0.0, ZPos# = 0.0)

	; Clear existing chunks
	Delete Each Cluster

	; First we'll need to get the original terrain scale for matching scale after the chunking
    vx# = GetMatElement#(Mesh, 0, 0)
	vy# = GetMatElement#(Mesh, 0, 1)
	vz# = GetMatElement#(Mesh, 0, 2)
	XScale# = Sqr#(vx# * vx# + vy# * vy# + vz# * vz#)
	vx# = GetMatElement#(Mesh, 1, 0)
	vy# = GetMatElement#(Mesh, 1, 1)
	vz# = GetMatElement#(Mesh, 1, 2)
	YScale# = Sqr#(vx# * vx# + vy# * vy# + vz# * vz#)
	vx# = GetMatElement#(Mesh, 2, 0)
	vy# = GetMatElement#(Mesh, 2, 1)
	vz# = GetMatElement#(Mesh, 2, 2)
	ZScale# = Sqr#(vx# * vx# + vy# * vy# + vz# * vz#)

	; The default values will give you about 23 chunks per terrain
	; Raising the divided by number will give you more chunks
	cx# = Int((MeshWidth#(Mesh)) / chx#)
	cy# = Int((MeshHeight#(Mesh)) / chy#)
	cz# = Int((MeshDepth#(Mesh)) / chz#)

	; Let the chunking begin
	sos = CountSurfaces(Mesh)
	For s = 1 To sos
		surf = GetSurface(Mesh, s)
		brush = GetSurfaceBrush(surf)

		For t = 0 To CountTriangles(surf) - 1
			x0#  = VertexX#(surf, TriangleVertex(surf, t, 0))
			y0#  = VertexY#(surf, TriangleVertex(surf, t, 0))
			z0#  = VertexZ#(surf, TriangleVertex(surf, t, 0))
			nx0# = VertexNX#(surf, TriangleVertex(surf, t, 0))
			ny0# = VertexNY#(surf, TriangleVertex(surf, t, 0))
			nz0# = VertexNZ#(surf, TriangleVertex(surf, t, 0))
			al0# = VertexAlpha#(surf, TriangleVertex(surf, t, 0))
			cr0# = VertexRed#(surf, TriangleVertex(surf, t, 0))
			cg0# = VertexGreen#(surf, TriangleVertex(surf, t, 0))
			cb0# = VertexBlue#(surf, TriangleVertex(surf, t, 0))
			x1#  = VertexX#(surf, TriangleVertex(surf, t, 1))
			y1#  = VertexY#(surf, TriangleVertex(surf, t, 1))
			z1#  = VertexZ#(surf, TriangleVertex(surf, t, 1))
			nx1# = VertexNX#(surf, TriangleVertex(surf, t, 1))
			ny1# = VertexNY#(surf, TriangleVertex(surf, t, 1))
			nz1# = VertexNZ#(surf, TriangleVertex(surf, t, 1))
			al1# = VertexAlpha#(surf, TriangleVertex(surf, t, 1))
			cr1# = VertexRed#(surf, TriangleVertex(surf, t, 1))
			cg1# = VertexGreen#(surf, TriangleVertex(surf, t, 1))
			cb1# = VertexBlue#(surf, TriangleVertex(surf, t, 1))
			x2#  = VertexX#(surf, TriangleVertex(surf, t, 2))
			y2#  = VertexY#(surf, TriangleVertex(surf, t, 2))
			z2#  = VertexZ#(surf, TriangleVertex(surf, t, 2))
			u0a# = VertexU#(surf, TriangleVertex(surf, t, 0), 0)
			v0a# = VertexV#(surf, TriangleVertex(surf, t, 0), 0)
			u1a# = VertexU#(surf, TriangleVertex(surf, t, 1), 0)
			v1a# = VertexV#(surf, TriangleVertex(surf, t, 1), 0)
			u2a# = VertexU#(surf, TriangleVertex(surf, t, 2), 0)
			v2a# = VertexV#(surf, TriangleVertex(surf, t, 2), 0)
			nx2# = VertexNX#(surf, TriangleVertex(surf, t, 2))
			ny2# = VertexNY#(surf, TriangleVertex(surf, t, 2))
			nz2# = VertexNZ#(surf, TriangleVertex(surf, t, 2))
			al2# = VertexAlpha#(surf, TriangleVertex(surf, t, 2))
			cr2# = VertexRed#(surf, TriangleVertex(surf, t, 2))
			cg2# = VertexGreen#(surf, TriangleVertex(surf, t, 2))
			cb2# = VertexBlue#(surf, TriangleVertex(surf, t, 2))

			; Let's see which chunk we'll assign this vert to
			x_c# = NearestPower(VertexX#(surf, TriangleVertex(surf, t, 0)), cx#)
			y_c# = NearestPower(VertexY#(surf, TriangleVertex(surf, t, 0)), cy#)
			z_c# = NearestPower(VertexZ#(surf, TriangleVertex(surf, t, 0)), cz#)
			Found = False
			For Cl.Cluster = Each Cluster
				If x_c = Cl\xc And y_c = Cl\yc And z_c = Cl\zc
					If Cl\Surf[s] <> 0
						Found = True
						v0 = AddVertex(Cl\Surf[s], x0, y0, z0)
						VertexTexCoords Cl\Surf[s], v0, u0a, v0a, 0
						VertexColor Cl\Surf[s], v0, cr0, cg0, cb0, al0
						VertexNormal Cl\Surf[s], v0, nx0, ny0, nz0
						v1 = AddVertex(Cl\Surf[s], x1, y1, z1)
						VertexTexCoords Cl\Surf[s], v1, u1a, v1a, 0
						VertexColor Cl\Surf[s], v1, cr1, cg1, cb1, al1
						VertexNormal Cl\Surf[s], v1, nx1, ny1, nz1
						v2 = AddVertex(Cl\Surf[s], x2, y2, z2)
						VertexTexCoords Cl\Surf[s], v2, u2a, v2a, 0
						VertexColor Cl\Surf[s], v2, cr2, cg2, cb2, al2
						VertexNormal Cl\Surf[s], v2, nx2, ny2, nz2
						nope = AddTriangle(Cl\Surf[s], v0, v1, v2)
						Exit
					EndIf
				EndIf
			Next

			; If there was no chunk for that area, we'll make it here
			If Found = False
				Cl.Cluster = New Cluster
				nsegs = nsegs + 1
				Cl\xc# = x_c
				Cl\yc# = y_c
				Cl\zc# = z_c
				Cl\Mesh = CreateMesh()
				For ss = 1 To sos
					Cl\Surf[ss] = CreateSurface(Cl\Mesh)
					surf2 = GetSurface(Mesh, ss) 
					brush = GetSurfaceBrush(surf2)
                    PaintSurface Cl\Surf[ss], brush
				Next
				v0 = AddVertex(Cl\Surf[s], x0, y0, z0)
				VertexTexCoords Cl\Surf[s], v0, u0a, v0a, 0
				VertexColor Cl\Surf[s], v0, cr0, cg0, cb0, al0
				VertexNormal Cl\Surf[s], v0, nx0, ny0, nz0
				v1 = AddVertex(Cl\Surf[s], x1, y1, z1)
				VertexTexCoords Cl\Surf[s], v1, u1a, v1a, 0
				VertexColor Cl\Surf[s], v1, cr1, cg1, cb1, al1
				VertexNormal Cl\Surf[s], v1, nx1, ny1, nz1
				v2 = AddVertex(Cl\Surf[s], x2, y2, z2)
				VertexTexCoords Cl\Surf[s], v2, u2a, v2a, 0
				VertexColor Cl\Surf[s], v2, cr2, cg2, cb2, al2
				VertexNormal Cl\Surf[s], v2, nx2, ny2, nz2
				nope = AddTriangle(Cl\Surf[s], v0, v1, v2)
			EndIf
		Next
	Next

	; Finalise chunks by removing blank surfaces and creating the scenery object
	For Cl.Cluster = Each Cluster
		Delete Each Remove_Surf
	    For i = 1 To CountSurfaces(Cl\Mesh)
			If CountVertices(GetSurface(Cl\Mesh, i)) <= 0
				RemS.Remove_Surf = New Remove_Surf
				RemS\ID = i
			EndIf
		Next
		Cl\Mesh = RemoveSurface(Cl\Mesh)
		EntityFX Cl\Mesh, 1 + 2
		PositionEntity Cl\Mesh, XPos#, YPos#, ZPos#
		ScaleEntity Cl\Mesh, XScale#, YScale#, ZScale#
		EntityType Cl\Mesh, C_Triangle
		EntityPickMode Cl\Mesh, 2
		ResetEntity Cl\Mesh
		Sc.Scenery = New Scenery
		Sc\EN = Cl\Mesh
		Sc\ScaleX# = XScale#
		Sc\ScaleY# = YScale#
		Sc\ScaleZ# = ZScale#
		Sc\TextureID = 65535
		NameEntity Sc\EN, Handle(Sc)
	Next

	FreeEntity Mesh
	Delete Each Cluster
         
End Function

Function NearestPower(N#, Snapper#)

	Return Float#(Int(N# / Snapper#)) * Snapper#

End Function

Function RemoveSurface(Ent)

	; Rebuild the mesh
	newmesh = CreateMesh()
	ns = CountSurfaces(Ent)

	For i = 1 To ns
		nogo = False
		For RemS.Remove_Surf = Each Remove_Surf
			If i = RemS\ID Then nogo = True
		Next
		If nogo = False
			surf = GetSurface(Ent, i)	
			newsurf = CreateSurface(newmesh)
			brush = GetSurfaceBrush(surf)
			tc = CountTriangles(surf)

			For tri = 0 To tc - 1
				v_r1# = VertexRed(surf, TriangleVertex(Surf, tri, 0) )
				v_g1# = VertexGreen(surf, TriangleVertex(Surf, tri, 0)) 
				v_b1# = VertexBlue(surf, TriangleVertex(Surf, tri, 0) )
				v_r2# = VertexRed(surf, TriangleVertex(Surf, tri, 1) )
				v_g2# = VertexGreen(surf, TriangleVertex(Surf, tri, 1)) 
				v_b2# = VertexBlue(surf, TriangleVertex(Surf, tri, 1) )
				v_r3# = VertexRed(surf, TriangleVertex(Surf, tri, 2) )
				v_g3# = VertexGreen(surf, TriangleVertex(Surf, tri, 2)) 
				v_b3# = VertexBlue(surf, TriangleVertex(Surf, tri, 2) )

				v_x0# = VertexX#(surf, TriangleVertex(surf, tri, 0))
				v_x1# = VertexX#(surf, TriangleVertex(surf, tri, 1))
				v_x2# = VertexX#(surf, TriangleVertex(surf, tri, 2))

				v_y0# = VertexY#(surf, TriangleVertex(surf, tri, 0))
				v_y1# = VertexY#(surf, TriangleVertex(surf, tri, 1))
				v_y2# = VertexY#(surf, TriangleVertex(surf, tri, 2))

				v_z0# = VertexZ#(surf, TriangleVertex(surf, tri, 0))
				v_z1# = VertexZ#(surf, TriangleVertex(surf, tri, 1))
				v_z2# = VertexZ#(surf, TriangleVertex(surf, tri, 2))

				v_u0# = VertexU#(surf, TriangleVertex(surf, tri, 0))
				v_u1# = VertexU#(surf, TriangleVertex(surf, tri, 1))
				v_u2# = VertexU#(surf, TriangleVertex(surf, tri, 2))

				v_v0# = VertexV(surf, TriangleVertex(surf, tri, 0))
				v_v1# = VertexV(surf, TriangleVertex(surf, tri, 1))
				v_v2# = VertexV(surf, TriangleVertex(surf, tri, 2))

				v_a0# = VertexAlpha#(surf, TriangleVertex(surf, tri, 0))
				v_a1# = VertexAlpha#(surf, TriangleVertex(surf, tri, 1))
				v_a2# = VertexAlpha#(surf, TriangleVertex(surf, tri, 2))

				v0 = AddVertex(newsurf, v_x0, v_y0, v_z0, v_u0, v_v0)
				v1 = AddVertex(newsurf, v_x1, v_y1, v_z1, v_u1, v_v1)
				v2 = AddVertex(newsurf, v_x2, v_y2, v_z2, v_u2, v_v2)
				AddTriangle(newsurf, v0, v1, v2)

				VertexColor newsurf, v0, v_r1, v_g1, v_b1, v_a0
				VertexColor newsurf, v1, v_r2, v_g2, v_b2, v_a1
				VertexColor newsurf, v2, v_r3, v_g3, v_b3, v_a2
			Next

			PaintSurface newsurf, brush
			UpdateNormals newmesh
       EndIf 
	Next

	FreeEntity Ent
	Return newmesh

End Function
