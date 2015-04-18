; General game options (host name, etc. - NOT stuff set by player)
Function LoadOptions()

	Result = LoadLanguage("Data\Game Data\Language.txt")
	If Result = False Then RuntimeError("Could not open Data\Game Data\Language.txt!")
	F = ReadFile("Data\Game Data\Hosts.dat")
	If F = 0 Then RuntimeError("Could not open Data\Game Data\Hosts.dat!")
		ServerHost$ = ReadLine$(F)
		UpdateHost$ = ReadLine$(F)
	CloseFile(F)
	If Right$(UpdateHost$, 1) <> "/" Then UpdateHost$ = UpdateHost$ + "/"
	NewHost$ = Trim$(CommandLine$())
	If NewHost$ <> ""
		ServerHost$ = NewHost$
		WriteLog(MainLog, "Changed server host to " + NewHost$ + " from command line")
	EndIf
	F = ReadFile("Data\Game Data\Misc.dat")
	If F = 0 Then RuntimeError("Could not open Data\Game Data\Misc.dat!")
		GameName$ = ReadLine$(F)
		UpdateGame$ = ReadLine$(F)
		UpdateMusic = ReadLine$(F)
	CloseFile(F)
	AppTitle(GameName$)

	Result = LoadControlBindings("Data\Controls.dat")
	If Result = False Then RuntimeError("Could not open Data\Controls.dat!")

	F = ReadFile("Data\Game Data\Other.dat")
	If F = 0 Then RuntimeError("Could not open Data\Game Data\Other.dat!")
		HideNametags = ReadByte(F)
		DisableCollisions = ReadByte(F)
		ViewMode = ReadByte(F)
		ServerPort = ReadInt(F)
		If ServerPort = 0 Then ServerPort = 25000
		RequireMemorise = ReadByte(F)
		UseBubbles = ReadByte(F)
		BubblesR = ReadByte(F)
		BubblesG = ReadByte(F)
		BubblesB = ReadByte(F)
	CloseFile(F)
	If ViewMode = 1 Then CamMode = 1

	WriteLog(MainLog, "Loaded options from file")

End Function

; Loads general game media
Function LoadGame()

	; Game options
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

	; Money settings
	F = ReadFile("Data\Game Data\Money.dat")
	If F = 0 Then RuntimeError("Could not open Data\Game Data\Money.dat!")
		Money1$ = ReadString$(F)
		Money2$ = ReadString$(F)
		Money2x = ReadShort(F)
		Money3$ = ReadString$(F)
		Money3x = ReadShort(F)
		Money4$ = ReadString$(F)
		Money4x = ReadShort(F)
	CloseFile(F)

	; Main screen turn on
	Select FullScreen
		Case 0
			Graphics3D(Width, Height, Depth,2)
			Initext
		Case 1
			Graphics3D(Width, Height, Depth,1)
			Initext
	End Select
			;multithreading cysis145 ???
				;Local pointer1 = FunctionPointer()
				;Goto skip1
				;Thread1()
				;.skip1
				;Local Thread1 = CreateThread(Pointer1, 0)
			
	;AntiAlias(AA)

	; Enable anisotropic filtering
	
;	If FileType("dx7test.dll") = 1 And AnisotropyLevel > 0
;		d3d = SystemProperty$("Direct3D7")
;		dev7 = SystemProperty$("Direct3DDevice7")
;		draw7 = SystemProperty$("DirectDraw7")
;		hwnd = SystemProperty$("AppHWND")
;		instance = SystemProperty$("AppHINSTANCE")
		;SetSystemProperties(d3d, dev7, draw7, hwnd, instance)
		;SetTextureStageState(21, 8, AnisotropyLevel)
		;SetTextureStageState(16, 8, 5)
		;SetTextureStageState(17, 8, 3)
		;SetTextureStageState(18, 8, 3)
		
		F = ReadFile("Data\Options.dat")
;	If F = 0 Then RuntimeError("Could not open Data\Options.dat!")
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
	
		anisotropyLevels = GfxDriverCapsEx\AnisotropyMax
		;enable = 1
;		
		;TextureAnisotropy
	
		Select AnisotropyLevel
			Case 0
				anisotropyLevels = anisotropyLevels - 16
			Case 4
				anisotropyLevels = anisotropyLevels - 12
			Case 8
				 anisotropyLevels = anisotropyLevels - 8
			Case 16
				 anisotropyLevels = anisotropyLevels
		End Select 
		
		TextureAnisotropy anisotropyLevels
	;EndIf

	; Camera and lighting
	Cam = CreateCamera()
	CameraRange(Cam, 1.0, CameraViewRange)
	CameraFogMode(Cam, 1)
	EntityRadius(Cam, 1.8)
	EntityType(Cam, C_Cam)
	DefaultLight = New Light
	DefaultLight\EN = CreateLight()
	LightColor(DefaultLight\EN, 0, 0, 0)
	HideEntity(DefaultLight\EN)
	NameEntity(DefaultLight\EN, Handle(DefaultLight))
	AmbientLight(90, 90, 90)

	; GUI
	Cam2 = CreateCamera()
	CameraRange(Cam2, 5.0, 15.0)
	CameraClsMode(Cam2, False, False)
	PositionEntity(Cam2, 0, -5000, 0)
	GY_Load(Cam2)
	
	;WideScreen Adjust Camera Ramoida
	If (Width * 9) = (Height * 16)
		 ResolutionType = 1
	ElseIf (Width * 3) = (Height * 4)
		 ResolutionType = 0
	EndIf 
	GYFovCam Cam2, Width , Height
	
	
	; Debug Banner [#@#]
	;LoadDebugBanner()

	; Texture filters
	TextureFilter("m_", 1 + 4)
	TextureFilter("a_", 1 + 2)

	; 3D sound
	CreateListener(Cam, 0.1, 1.0, 1.0)
	; Gubbin joint names
	LoadGubbinNames()
	; My mesh/textures etc.
	Result = LoadActorInstance3D(Me, 0.05)
	If Result = False Then RuntimeError("Could not load actor mesh for " + Me\Actor\Race$ + "!")
	Me\RNID = 1
	EntityType(Me\CollisionEN, C_Player)
	FreeEntity(Me\NametagEN)
	Me\NametagEN = 0
	Bonce = FindChild(Me\EN, "Head")
	If Bonce = 0 Then RuntimeError(Me\Actor\Race$ + " actor mesh is missing a 'Head' joint!")
	CamHeight# = EntityDistance#(Bonce, Me\CollisionEN)
	
	;&&&&& Refractive water  PROBLEM affecte loading page, placé ailleurs = MAV
	CreateRefractTextures(TextureSize,TextureSize)
	
	; Loot bag mesh
	LootBagEN = LoadMesh("Data\Meshes\Loot Bag.b3d")
	If LootBagEN = 0 Then RuntimeError("File not found: Data\Meshes\Loot Bag.b3d")
	ScaleMesh LootBagEN, 0.075, 0.075, 0.075
	EntityRadius LootBagEN, MeshWidth#(LootBagEN) * 0.6
	HideEntity LootBagEN

	; General purpose pivot
	GPP = CreatePivot()

	; User interface
	If Not LoadInterfaceSettings("Data\Game Data\Interface.dat") Then RuntimeError("Could not load interface!")
	If Chat <> Null And Chat\Texture <> 65535
		ChatBar = New InterfaceComponent
		ChatBar\Texture = Chat\Texture
		ChatBar\X# = Chat\X#
		ChatBar\Y# = Chat\Y#
		ChatBar\Width# = Chat\Width#
		ChatBar\Height# = Chat\Height#
		ChatBar\Alpha# = Chat\Alpha#
		ChatBar\R = Chat\R
		ChatBar\G = Chat\G
		ChatBar\B = Chat\B
	End If
	
	CreateInterface()
	
	; Combat settings
	LoadCombat()
	
	; Environment
	LoadSuns()
	CreateSuns()
	LoadWeather3D()
	SkyEN = LoadMesh("Data\Meshes\Sky Sphere.b3d")
	If SkyEN = 0 Then RuntimeError("File not found: Data\Meshes\Sky Sphere.b3d")
	MMV.MeshMinMaxVertices = MeshMinMaxVertices(SkyEN)
	XScale# = 2.0 / (MMV\MaxX# - MMV\MinX#)
	YScale# = 2.0 / (MMV\MaxY# - MMV\MinY#)
	ZScale# = 2.0 / (MMV\MaxZ# - MMV\MinZ#)
	Delete(MMV)
	If YScale# < XScale# Then XScale# = YScale#
	If ZScale# < XScale# Then XScale# = ZScale#
	ScaleMesh(SkyEN, XScale#, XScale#, XScale#)
	EntityOrder(SkyEN, 3)
	EntityFX(SkyEN, 1 + 8)
	StarsEN = CopyEntity(SkyEN)
	EntityFX(StarsEN, 1 + 8)
	EntityOrder(StarsEN, 4)
	CloudEN = CopyEntity(SkyEN)
	EntityFX(CloudEN, 1 + 8)
	EntityOrder(CloudEN, 1)
	
	; Screen flash quad
	RecreateScreenFlashEN()

	; Enable collisions
	If DisableCollisions = False
		Collisions(C_Player, C_Actor,     3, 3) ; Actor->Actor
		Collisions(C_Actor,  C_Player,    3, 3)
		Collisions(C_Actor,  C_Actor,     3, 3)
		Collisions(C_Player, C_ActorTri2, 2, 3)
		Collisions(C_Actor,  C_ActorTri2, 2, 3)
	EndIf

	Collisions(C_Player,    C_Sphere,   1, 3) ; Actor->Scenery
	Collisions(C_Player,    C_Box,      3, 3)
	Collisions(C_Player,    C_Triangle, 2, 3)
	Collisions(C_Actor,     C_Sphere,   1, 3)
	Collisions(C_Actor,     C_Box,      3, 3)
	Collisions(C_Actor,     C_Triangle, 2, 3)
	Collisions(C_ActorTri1, C_Sphere,   1, 3)
	Collisions(C_ActorTri1, C_Box,      3, 3)
	Collisions(C_ActorTri1, C_Triangle, 2, 3)

	; Selected actor highlight
	ActorSelectEN = CreateMesh()
	s = CreateSurface(ActorSelectEN)
	v1 = AddVertex(s, -1.0, 0.0, -1.0, 0.0, 0.0)
	v2 = AddVertex(s, -1.0, 0.0, 1.0,  0.0, 1.0)
	v3 = AddVertex(s, 1.0,  0.0, 1.0,  1.0, 1.0)
	v4 = AddVertex(s, 1.0,  0.0, -1.0, 1.0, 0.0)
	AddTriangle(s, v1, v2, v3)
	AddTriangle(s, v1, v3, v4)
	EntityFX(ActorSelectEN, 1 + 8)
	EntityBlend(ActorSelectEN, 3)
	Tex = LoadTexture("Data\Textures\Select.bmp")
	If Tex = 0 Then RuntimeError("File not found: Data\Textures\Select.bmp!")
	EntityTexture(ActorSelectEN, Tex)
	HideEntity(ActorSelectEN)

	; Movement click marker
	ClickMarkerEN = CopyEntity(ActorSelectEN)
	Tex = LoadTexture("Data\Textures\Marker.PNG")
	If Tex = 0 Then RuntimeError("File not found: Data\Textures\Marker.PNG!")
	EntityTexture(ClickMarkerEN, Tex)
	HideEntity(ClickMarkerEN)
	ClickMarkerTimer = MilliSecs()

	; Count random images available for zone loading screens
	If FileType("Data\Textures\Random") = 2
		D = ReadDir("Data\Textures\Random")
		Repeat
			File$ = NextFile$(D)
			If File$ = "" Then Exit
			If FileType("Data\Textures\Random\" + File$) = 1
				RandomImages = RandomImages + 1
			EndIf
		Forever
		CloseDir(D)
	Else
		RandomImages = 0
	EndIf

	; Sort player known spells alphabetically
	SortSpells()

	; Done
	WriteLog(MainLog, "Loaded general game media")

End Function

;&&&&&&&&&&
;Added may 12
Function UnloadGame()
	
	; Camera and lighting
	FreeEntity(Cam)
	Cam = 0
	FreeEntity(DefaultLight\EN)
	Delete DefaultLight
	FreeEntity(Cam2)
	Cam2 = 0
	
	;FreeShadows
	

	; My mesh/textures etc
	FreeActorInstance3D(Me)
	Me = Null
	
	; General purpose pivot
	FreeEntity(GPP)
	GPP = 0
	
	; User interface
	Delete ChatBar
	ChatBar = Null
	
	; Environment
	For i = 0 To 10
		FreeEntity(Flares(i))
	Next
	RP_FreeEmitter(Rainemitter,True,True)
	RainEmitter = 0
	RP_FreeEmitter(SnowEmitter,True,True)
	SnowEmitter = 0
	FreeSound(Snd_Thunder(0))
	FreeSound(Snd_Thunder(1))
	FreeSound(Snd_Thunder(2))
	Snd_Thunder(0) = 0
	Snd_Thunder(1) = 0
	Snd_Thunder(2) = 0
	
	FreeEntity(SkyEN)
	SkyEN = 0
	
	FreeEntity(StarsEN)
	StarsEN = 0
	
	FreeEntity(CloudEN)
	CoudEN = 0
	
	; Screen flash quad
	FlashEN = 0
	
	; Collisions
	FreeEntity(ActorSelectEN)
	ActorSelectEN = 0
	
	; Movement click marker
	FreeEntity(ClickMarkerEN)
	ClickMarkerEN = 0
	Unload_Radar()
	GY_Unload()
	
	For L.Light = Each Light
		FreeEntity(L\EN)
		Delete L
	Next
	
	For i = 0 To 65534
		UnloadTexture(i)
		UnloadMesh(i)
	Next
	
	; Clear all actor 3D instances
	For AI.ActorInstance = Each Actorinstance
		FreeActorInstance3D(AI)
		Delete(AI)
	Next
	AreaName$ = ""
	OldAreaName$ = ""
	OldAreaID = 0
	CurrentAreaID = 0
	WriteLog(MainLog, "Unloaded general game media")
	radar_lastx# = 0
	radar_lasty# = 0
	
End Function