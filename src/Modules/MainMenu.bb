; Character data storage
Dim CharButtons(9)
Dim CharNames$(9)
Dim CharActors(9)
Dim CharGender(9)
Dim CharFaceTex(9)
Dim CharHair(9)
Dim CharBeard(9)
Dim CharBodyTex(9)
Dim AttributeLabels(39)
Dim AttributeDecrease(39)
Dim AttributeIncrease(39)
Dim PointSpends(39)
Dim LDescription(9)
Dim LEULA(28)
Dim EULAText$(999)
Global CharList$, UName$, PWord$
Type UpdateFile
	Field Name$, Checksum
End Type

; Loads, runs and unloads the main game startup menu
Function RunMenu()

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

	ResolutionType = 0 ; !WideScreen Ratio 4:3 Ramoida 
	If GfxMode3DExists(Width, Height, 16) Or GfxMode3DExists(Width, Height, 32) 
		If (Width * 9) = (Height * 16) Then  ResolutionType = 1 ; WideScreen Ratio 16:9 Ramoida 
			
       Select FullScreen 
          Case 0 
             Graphics3D(Width, Height, Depth,2) 
				InitExt

          Case 1 
             Graphics3D(Width, Height, Depth,1) ;1
				InitExt
       End Select 
    Else 
       Select FullScreen 
          Case 0 
             Graphics3D(800, 600, 0,2) 
				InitExt
          Case 1 
             Graphics3D(800, 600, 0,1) 
				InitExt
       End Select 
    EndIf
				;multithreading cysis145 ???
				;Local pointer1 = FunctionPointer()
				;Goto skip1
				;Thread1()
				;.skip1
				;Local Thread1 = CreateThread(Pointer1, 0)


	
	HidePointer()
	TextureFilter("m_", 1 + 4)
	TextureFilter("a_", 1 + 2)
	PlayIntroMovie()
	Cam = CreateCamera()
	
	;WideScreen Adjust Camera Ramoida
	GYFovCam Cam, Width , Height


	GY_Load(Cam)
;InitExt
; Setfogrange Distance and colour
	CameraRange Cam, 1, 30000
	CameraFogMode Cam,1
	CameraFogRange Cam, 300, 5200
	CameraFogColor Cam, 0, 51, 102
	

;	
;	;CameraFogColor Cam, 128, 255, 0
;	;CameraFogRange Cam,1,100
	;-----
    Snd_Music = LoadSound("Data\Music\Menu.mp3")
	If Snd_Music = 0 Then Snd_Music = LoadSound("Data\Music\Menu.ogg")
	If Snd_Music = 0 Then Snd_Music = LoadSound("Data\Music\Menu.mid")
	If Snd_Music = 0 Then Snd_Music = LoadSound("Data\Music\Menu.mod")
	If Snd_Music
		LoopSound Snd_Music
		SndC_Music = PlaySound(Snd_Music)
	EndIf
	WriteLog(MainLog, "Gooey loaded for menu")
	MoveMouse(GraphicsWidth() / 2, GraphicsHeight() / 2)
	;GameOptionsMenu()
	;WriteLog(MainLog, "Options menu complete")
	
	EULAScreen()
			
	WriteLog(MainLog, "EULA complete")
	;If UpdateGame$ <> "Development Version"
	;	UpdateFiles()
	;	WriteLog(MainLog, "Files patched to latest version")
	;Else
	;	WriteLog(MainLog, "Using development version: skipped auto-update")
	;EndIf
	Result = LoadAnimSets("Data\Game Data\Animations.dat")
	If Result = -1 Then RuntimeError("Could not open Data\Game Data\Animations.dat!")
	WriteLog(MainLog, "Loaded animation sets")
	ChooseServer()
	WriteLog(MainLog, "Server chosen")
	LogIn()
	WriteLog(MainLog, "Logged in")
	CharSelect()
	WriteLog(MainLog, "Character selection complete")
	For i = 0 To 65534
		If LoadedMeshes(i) <> 0 Then FreeEntity LoadedMeshes(i) : LoadedMeshes(i) = 0
		If LoadedTextures(i) <> 0 Then FreeTexture LoadedTextures(i) : LoadedTextures(i) = 0
	Next
	GY_Unload()
	DeInitExt
	;multithreading ???
	;FreeThread(Thread1)
	EndGraphics()
	If ChannelPlaying(SndC_Music) Then StopChannel(SndC_Music) : FreeSound(Snd_Music)
	WriteLog(MainLog, "Menu finished - starting actual game")


End Function

; Auto-update system
Function UpdateFiles()

	; Background
;--------------------------------------------
	Background = GY_CreateQuad(Cam)
	PositionEntity(Background, -10.0, 7.5, 10.0)
	ScaleEntity(Background, 20.0, 15.0, 1.0)
	EntityOrder(Background, 1)
	Tex = LoadTexture("Data\Textures\Menu\Updates.png")
	If Tex = 0 Then RuntimeError("File not found: Data\Textures\Menu\Updates.png!")
	EntityTexture(Background, Tex)
	FreeTexture(Tex)

	; Create window
	TStatus      = GY_CreateLabel(0, 0.23, 0.61, LSet$(LanguageString$(LS_ConnectingToServer), 60))
	LFileProg    = GY_CreateLabel(0, 0.5, 0.29, LanguageString$(LS_FileProgress), 255, 255, 255, Justify_Centre)
	LFile        = GY_CreateLabel(0, 0.5, 0.33, LanguageString$(LS_UpdateFileName) + String$("*", 100), 255, 255, 255, Justify_Centre)
	GDLBar       = GY_CreateProgressBar(0, 0.23, 0.37, 0.54, 0.04, 0, 100, 0, 0, 255)
	LOverallProg = GY_CreateLabel(0, 0.5, 0.48, LanguageString$(LS_UpdateProgress), 255, 255, 255, Justify_Centre)
	GOverall     = GY_CreateProgressBar(0, 0.23, 0.52, 0.54, 0.04, 0, 100, 255, 0, 0)
	GY_UpdateLabel(LFile, "")

	; Connect to server
	GY_Update() : UpdateWorld() : RenderWorld() : Flip()
    Port = 11000
	Repeat
		PeerToHost1 = RCE_Connect(ServerHost$, ServerPort, Port, "X", "", "Data\Logs\Client Connection.txt", False)
		Port = Port + 1
	Until PeerToHost1 <> RN_ConnectionInUse And PeerToHost1 <> RN_PortInUse
	Select PeerToHost1
		;Case RN_HostNotFound : RuntimeError(LanguageString$(LS_InvalidHost) + " (" + ServerHost$ + ")")
		;Case RN_TimedOut : RuntimeError(LanguageString$(LS_NoResponse))
		;Case RN_ServerFull : RuntimeError(LanguageString$(LS_TooManyPlayers))
	End Select

	; Request files list
	GY_UpdateLabel(TStatus, LanguageString$(LS_ReceivingFiles))
	RCE_Send(Connection, PeerToHost1, P_FetchUpdateFiles, "", True)

	; Wait for reply
	Done = False
	CreatedFiles = 0 : RequiredFiles = -1
	While Done = False
		Delay 10
		If KeyHit(1) Then RCE_Disconnect() : End
		For M.RCE_Message = Each RCE_Message
			If M\MessageType = P_FetchUpdateFiles
				Pa$ = M\MessageData$
				Offset = 1
				; If there are no files!
				If Len(Pa$) = 2 Then Offset = 10000 : RequiredFiles = 0
				While Offset < Len(Pa$)
					CreatedFiles = CreatedFiles + 1
					U.UpdateFile = New UpdateFile
					U\Checksum = RCE_IntFromStr(Mid$(Pa$, Offset, 4))
					NameLen = RCE_IntFromStr(Mid$(Pa$, Offset + 4, 1))
					U\Name$ = Mid$(Pa$, Offset + 5, NameLen)
					Offset = Offset + 5 + NameLen
					; It's the last packet with the total required files number in it
					If Len(Pa$) = Offset + 1
						RequiredFiles = RCE_IntFromStr(Mid$(Pa$, Offset, 2))
						Offset = Offset + 2
					EndIf
				Wend
				If CreatedFiles = RequiredFiles Then Delete M : Done = True : Exit
			ElseIf M\MessageType = RN_HostHasLeft Or M\MessageType = RN_Disconnected
				RuntimeError(LanguageString$(LS_LostConnection))
			EndIf
			Delete M
		Next
		RCE_Update()
		RCE_CreateMessages()
		
		GY_Update()
		UpdateWorld()
		RenderWorld()
		Flip()
	Wend

	; Disconnect
	RCE_Disconnect()

	; Go through each file to make sure that I have it
	ThisFile = 0
	GY_UpdateLabel(TStatus, LanguageString$(LS_CheckingFiles))
	For U.UpdateFile = Each UpdateFile
		NeedFile = False
		; I don't have it at all
		If FileType(U\Name$) = 0
			NeedFile = True
		Else
			; I have it but it's the wrong version
			If U\Checksum <> CountChecksum(U\Name$) Then NeedFile = True
		EndIf

		; If it's music, and the skip music option is enabled, then skip it
		If UpdateMusic = False
			If Instr(Upper$(U\Name$), "DATA\MUSIC") Then NeedFile = False
		EndIf

		; If file is required
		If NeedFile = True
			GY_UpdateLabel(LFile, LanguageString$(LS_UpdateFile) + " " + U\Name$)

			; Download it
			SafeFileName$ = Replace$(U\Name$, "\", "!!")
			SafeFileName$ = Replace$(SafeFileName$, " ", "!!!")
			Result = DownloadFile(UpdateHost$ + Upper$(SafeFileName$) + ".DAT", "Temp.dat", GDLBar)
			If Result = 0 Then RuntimeError(LanguageString$(LS_CouldNotDownload) + " " + UpdateHost$)

			; Unzip file
			bz2(1, 0, "Temp.dat", "Temp2.dat")
			DeleteFile("Temp.dat")

			; Ensure that the folder tree for the file actually exists
			For i = 2 To Len(U\Name$)
				If Mid$(U\Name$, i, 1) = "\"
					Folder$ = Left$(U\Name$, i - 1)
					If FileType(Folder$) <> 2 Then CreateDir(Folder$)
				EndIf
			Next
;---------------------------------------------
			; Special case for client executable as it is currently running!
			If Upper$(U\Name$) = Upper$(GameName$) + ".EXE"
				DeInitExt
				;multithreading ???
				;FreeThread(Thread1)
				EndGraphics()
				ExecFile("Data\Patch.exe " + GameName$)
				End
			; Copy file to overwrite old version
			Else
				If FileType(U\Name) = 1 Then DeleteFile(U\Name$)
				CopyFile("Temp2.dat", U\Name)
				Delay(40)
				DeleteFile("Temp2.dat")
			EndIf

			GY_UpdateProgressBar(GDLBar, 0)
		EndIf

		Delete(U)
		ThisFile = ThisFile + 1
		GY_UpdateProgressBar(GOverall, Int((Float#(ThisFile) / Float#(CreatedFiles)) * 100.0))
		GY_Update()
		RenderWorld()
		Flip()
		If KeyHit(1) Then End
	Next

	FreeEntity(Background)
	GY_FreeGadget(TStatus) : GY_FreeGadget(LFileProg) : GY_FreeGadget(LFile)
	GY_FreeGadget(GDLBar) : GY_FreeGadget(LOverallProg) : GY_FreeGadget(GOverall)

End Function

; Allows the user to choose a server to connect to from a list
Function ChooseServer()

	; Only display if a list is available
	If FileType("Data\Game Data\Server Selector.dat")
		; Get URL of server list
		F = ReadFile("Data\Game Data\Server Selector.dat")
		If F = 0 Then RuntimeError("Could not open file: Data\Game Data\Server Selector.dat!")
		ListURL$ = ReadLine$(F)
		CloseFile(F)
		If ListURL$ = "" Then RuntimeError("Server list URL not found in Data\Game Data\Server Selector.dat!")

		; Background
		Background = GY_CreateQuad(Cam)
		PositionEntity(Background, -10.0, 7.5, 10.0)
		ScaleEntity(Background, 20.0, 15.0, 1.0)
		EntityOrder(Background, 1)
		Tex = LoadTexture("Data\Textures\Menu\Server Selection.png")
		If Tex = 0 Then RuntimeError("File not found: Data\Textures\Menu\Server Selection.png!")
		EntityTexture(Background, Tex)
		FreeTexture(Tex)

		; Gadgets
		LStatus = GY_CreateLabel(0, 0.5, 0.32, LanguageString$(LS_RetrievingList), 255, 255, 255, Justify_Centre)
		LServers = GY_CreateListBox(0, 0.37, 0.37, 0.26, 0.24, True)
		BConnect = GY_CreateButton(0, 0.43, 0.65, 0.14, 0.03, LanguageString$(LS_Connect))
		GY_Update()
		RenderWorld()
		Flip(VSync)

		; Download server list
		Result = DownloadFile(ListURL$, "Data\Temp.dat")
		If Result = 0 Then RuntimeError("Could not retrieve list, please check that you are connected to the internet!")

		; Fill list
		F = ReadFile("Data\Temp.dat")
			While Eof(F) = False
				Name$ = ReadLine$(F)
				Host$ = ReadLine$(F)
				GY_AddListBoxItem(LServers, Name$, 255, 255, 255, Host$)
			Wend
		CloseFile(F)
		DeleteFile("Data\Temp.dat")
		GY_UpdateLabel(LStatus, LanguageString$(LS_AvailableServers))

		; Event loop
		Repeat
			If GY_ButtonHit(BConnect)
				ServerHost$ = GY_ListBoxItemData$(LServers)
				Local dpoint% = Instr(ServerHost, "://" )
				Local hostLength% = Len(ServerHost)
				
				; Filter http://, udp://, ...
				If dpoint > 0 Then dpoint = dpoint + 3 + Instr( Right(ServerHost,hostLength - dpoint - 3), ":" )
				
				If dpoint > 0
					ServerPort = Int( Right(ServerHost, Len(ServerHost) - dpoint) )
					ServerHost$ = Left( ServerHost, dpoint - 1 )
				EndIf
				WriteLog(MainLog, "Changed server host to " + ServerHost$+":"+ServerPort + " from selection list")
				Exit
			EndIf

			GY_Update()
			RenderWorld()
			Flip(VSync)
			If KeyHit(1) Then End
		Forever

		; Done
		FreeEntity(Background)
		GY_FreeGadget(BConnect) : GY_FreeGadget(LStatus) : GY_FreeGadget(LServers)
	EndIf

End Function

; Account login dialog
Function LogIn()

	; Read whether new account creation is enabled
	F = ReadFile("Data\Game Data\Hosts.dat")
	If F = 0 Then RuntimeError("Could not open Data\Game Data\Hosts.dat!")
		ReadLine$(F)
		ReadLine$(F)
		AccountsEnabled = ReadLine$(F)
	CloseFile(F)
			
	; Background
	Logo = LoadSprite("Data\Textures\Menu Logo.bmp",4) ; Logo opacity terrier
	ScaleSprite(Logo, 0.75, 0.375)
	PositionEntity(Logo, 0.0, 1.1, 2)
	EntityOrder(Logo, -1)
	EntityParent(Logo, Cam)
	
	Background = GY_CreateQuad(Cam)
	PositionEntity(Background, -4.5, 3.0, 10.0)
	ScaleEntity(Background, 10.0, 5.0, 1.0)
	Tex = LoadTexture("Data\Textures\Menu\Login.png")
	If Tex = 0 Then RuntimeError("File not found: Data\Textures\Menu\Login.png!")
	EntityTexture(Background, Tex)
	FreeTexture(Tex)

	; Create the window
	If ResolutionType = 1 ; 16:9 ratio
		TStatus = GY_CreateLabel(0, 0.0 - 0.15, 0.0, LSet$(LanguageString$(LS_ConnectingToServer), 60))
	Else
		TStatus = GY_CreateLabel(0, 0.0, 0.0, LSet$(LanguageString$(LS_ConnectingToServer), 60))
	EndIf
	
	LName   = GY_CreateLabel(0, 0.392, 0.35, LanguageString$(LS_Username), 255, 255, 255, Justify_Right)
	LPass   = GY_CreateLabel(0, 0.392, 0.41, LanguageString$(LS_Password), 255, 255, 255, Justify_Right)
	LEmail  = GY_CreateLabel(0, 0.392, 0.47, LanguageString$(LS_EmailAddr), 255, 255, 255, Justify_Right)
	TName   = GY_CreateTextField(0, 0.41, 0.35, 0.275, 4, 20) ; account - charname verification
	TPass   = GY_CreateTextField(0, 0.41, 0.41, 0.275, 5, 20, "", True)	; password verification
	TEmail  = GY_CreateTextField(0, 0.41, 0.47, 0.275, 6, 30)
	BLogin  = GY_CreateCustomButton(0, 0.560, 0.55, 0.188, 0.048, LoadButtonU("Login"), LoadButtonD("Login"), LoadButtonH("Login"))
	If AccountsEnabled = True
		BNew = GY_CreateCustomButton(0, 0.300, 0.55, 0.188, 0.048, LoadButtonU("NewAccount"), LoadButtonD("NewAccount"), LoadButtonH("NewAccount"))
		GY_DropGadget(BNew)
	EndIf
	GY_DropGadget(TStatus)
	GY_DropGadget(LName)
	GY_DropGadget(LPass)
	GY_DropGadget(LEmail)
	GY_DropGadget(TName)
	GY_DropGadget(TPass)
	GY_DropGadget(TEmail)
	GY_DropGadget(BLogin)
	
	;---------------------------
	;OPTIONS part1
	;---------------------------
	; Graphics options window 
    WGraphics = GY_CreateWindow("Graphics Options", 0.2, 0.3, 0.6, 0.4, True, True, False) ;LanguageString$(LS_GraphicsOptions)
    GY_CreateLabel(WGraphics, 0.02, 0.05, LanguageString$(LS_SelectResolution)) 
	GY_CreateLabel(WGraphics, 0.3, 0.01, "Client must be restarted for any changes to take effect.", 255, 0, 0)
    LResolution = GY_CreateListBox(WGraphics, 0.02, 0.1, 0.25, 0.75) 
    G.GY_Gadget = Object.GY_Gadget(LResolution) 
    L.GY_ListBox = Object.GY_ListBox(G\TypeHandle) 
    For i = 1 To CountGfxModes3D() 
       Width = GfxModeWidth(i) 
       Height = GfxModeHeight(i) 

		;WideScreen Ramoida
		If (Width * 9) = (Height * 16)  Or  (Width * 3) = (Height * 4)  
			ModeName$ = Str$(Width) + " x " + Str$(Height) 
        	Found = False 
          	For B.GY_ListItem = Each GY_ListItem 
             If B\Box = L 
                If B\Dat$ = ModeName$ 
                   Found = True 
                   Exit 
                EndIf 
             EndIf 
         	Next 
          	If Found = False Then GY_AddListBoxItem(LResolution, ModeName$) 
		EndIf

     ; If ((Width * 3) = (Height * 4)) 
     ;    ModeName$ = Str$(Width) + " x " + Str$(Height) 
     ;    Found = False 
     ;     For B.GY_ListItem = Each GY_ListItem 
     ;        If B\Box = L 
     ;           If B\Dat$ = ModeName$ 
     ;              Found = True 
     ;              Exit 
     ;           EndIf 
     ;        EndIf 
     ;     Next 
     ;     If Found = False Then GY_AddListBoxItem(LResolution, ModeName$) 
     ;  ElseIf ((Width * 9) = (Height * 16)) 
     ;     ModeName$ = Str$(Width) + " x " + Str$(Height) 
     ;     Found = False 
     ;     For B.GY_ListItem = Each GY_ListItem 
     ;        If B\Box = L 
     ;           If B\Dat$ = ModeName$ 
     ;              Found = True 
     ;              Exit 
     ;           EndIf 
     ;        EndIf 
     ;     Next 
     ;     If Found = False Then GY_AddListBoxItem(LResolution, ModeName$) 
     ;  EndIf 
    Next
	GY_CreateLabel(WGraphics, 0.6, 0.27, LanguageString$(LS_ColourDepth))
	CDepth = GY_CreateComboBoxS(WGraphics, 0.8, 0.27, 0.17, 0.39, "", 255, 255, 255, 14, True) ;LanguageString$(LS_SelectColourDepth)
	GY_AddComboBoxItem(CDepth, "16 bit")
	GY_AddComboBoxItem(CDepth, "32 bit")
	GY_AddComboBoxItem(CDepth, "Best") ;LanguageString$(LS_BestAvailable)
	BAntiAlias = GY_CreateCheckBox(WGraphics, 0.3, 0.27, LanguageString$(LS_EnableAA))
	;BEnableGrass = GY_CreateCheckBox(WGraphics, 0.5, 0.35,LanguageString$(LS_EnableGrass))
	BFullScreen = GY_CreateCheckBox(WGraphics, 0.3, 0.35,"FullScreen")
	BVSyncOption = GY_CreateCheckBox(WGraphics, 0.3, 0.43,"Vsync On")
	;FastExt Settings Cysis145
	BBloom = GY_CreateCheckBox(WGraphics, 0.3, 0.51,"Bloom")
	BRays = GY_CreateCheckBox(WGraphics, 0.3, 0.59,"Light Rays")
	BWater = GY_CreateCheckBox(WGraphics, 0.3, 0.67, "Animated Water")
	
	;Shadow Settings Cysis145
	GY_CreateLabel(WGraphics, 0.6, 0.37, "Shadows:")
	CShadowCom = GY_CreateComboBoxS(WGraphics, 0.8, 0.37, 0.17, 0.39, "", 255, 255, 255, 14, True)
	GY_AddComboBoxItem(CShadowCom, "Off")
	GY_AddComboBoxItem(CShadowCom, "On")
		
	GY_CreateLabel(WGraphics, 0.6, 0.47, "Shadow Quality:")
	CShadowQual = GY_CreateComboBoxS(WGraphics, 0.8, 0.47, 0.17, 0.39, "", 255, 255, 255, 14, True)
	GY_AddComboBoxItem(CShadowQual, "Low")
	GY_AddComboBoxItem(CShadowQual, "Medium")
	GY_AddComboBoxItem(CShadowQual, "High")
	
	GY_CreateLabel(WGraphics, 0.6, 0.57, "Shadow Range:")
	CShadowRan = GY_CreateComboBoxS(WGraphics, 0.8, 0.57, 0.17, 0.39, "", 255, 255, 255, 14, True)
	GY_AddComboBoxItem(CShadowRan, "Short")
	GY_AddComboBoxItem(CShadowRan, "Medium")
	GY_AddComboBoxItem(CShadowRan, "Far")
	GY_AddComboBoxItem(CShadowRan, "Very Far")
	
	
	GY_CreateLabel(WGraphics, 0.6, 0.67, LanguageString$(LS_AnisotropyLevel))
	CAnisotropy = GY_CreateComboBoxS(WGraphics, 0.8, 0.67, 0.17, 0.39, "", 255, 255, 255, 8, True)
	GY_AddComboBoxItem(CAnisotropy, LanguageString$(LS_Disabled))
	GY_AddComboBoxItem(CAnisotropy, "x4")
	GY_AddComboBoxItem(CAnisotropy, "x8")
	GY_AddComboBoxItem(CAnisotropy, "x16")
	BGFXDone = GY_CreateCustomButton(WGraphics, 0.7, 0.866, 0.258, 0.108, LoadButtonU("MenuDone"), LoadButtonD("MenuDone"), LoadButtonH("MenuDone")) ;X = 0.696

	; Set up initial graphics options
	F = ReadFile("Data\Options.dat")
	If F = 0 Then RuntimeError("Could not open Data\Options.dat!")
		Width = ReadShort(F)
		Height = ReadShort(F)
		Depth = ReadByte(F)
		AA = ReadByte(F)
		DefaultVolume# = ReadFloat#(F)
		GrassEnabled = ReadByte(F)
		Anisotropy = ReadByte(F)
		FullScreen = ReadByte(F)
		VSync = ReadByte(F)
		;FastExt Settings Cysis145
		Bloom = ReadByte(F)
		Rays = ReadByte(F)
		AWater = ReadByte(F)
		ShadowC = ReadByte(F)
		ShadowQ = ReadByte(F)
		ShadowR = ReadByte(F)
	CloseFile(F)
	GY_UpdateListBox(LResolution, "640 x 480")
	GY_UpdateListBox(LResolution, Str$(Width) + " x " + Str$(Height))
	If Depth = 0
		GY_UpdateComboBox(CDepth,"Best")
	ElseIf Depth = 16
		GY_UpdateComboBox(CDepth, "16 bit")
	ElseIf Depth = 32
		GY_UpdateComboBox(CDepth, "32 bit")
	EndIf
	GY_UpdateCheckBox(BAntiAlias, AA)
	GY_UpdateCheckBox(BEnableGrass, GrassEnabled)
	GY_UpdateCheckBox(BFullScreen,Fullscreen)
	GY_UpdateCheckBox(BVSyncOption,VSync)
;FastExt Settings Cysis145
	GY_UpdateCheckBox(BBloom,Bloom)
	GY_UpdateCheckBox(BRays, Rays)
	GY_UpdateCheckBox(BWater, AWater)

	
	CurrentRes$ = GY_ListBoxItem(LResolution)
	CurrentDepth$ = GY_ComboBoxItem(CDepth)
	If Anisotropy = 0
		GY_UpdateComboBox(CAnisotropy, LanguageString$(LS_Disabled))
	ElseIf Anisotropy = 4
		GY_UpdateComboBox(CAnisotropy, "x4")
	ElseIf Anisotropy = 8
		GY_UpdateComboBox(CAnisotropy, "x8")
	ElseIf Anisotropy = 16
		GY_UpdateComboBox(CAnisotropy, "x16")
	EndIf
	CurrentAnisotropy$ = GY_ComboBoxItem(CAnisotropy)
	
	;Shadow complexity Cysis145
	If ShadowC = 0
		GY_UpdateComboBox(CShadowCom, "Off")
	ElseIf ShadowC = 1
		GY_UpdateComboBox(CShadowCom, "On")
	EndIf
	CurrentShadowC$ = GY_ComboBoxItem(CShadowCom)
	
	;Shadow Quality
	If ShadowQ = 0
		GY_UpdateComboBox(CShadowQual, "Low")
	ElseIf ShadowQ = 1
		GY_UpdateComboBox(CShadowQual, "Medium")
	ElseIf ShadowQ = 2
		GY_UpdateComboBox(CShadowQual, "High")
	EndIf
	CurrentShadowQ$ = GY_ComboBoxItem(CShadowQual)
	
	;Shadow Range
	If ShadowR = 0
		GY_UpdateComboBox(CShadowRan, "Short")
	ElseIf ShadowR = 1
		GY_UpdateComboBox(CShadowRan, "Medium")
	ElseIf ShadowR = 2
		GY_UpdateComboBox(CShadowRan, "Far")
	ElseIf ShadowR = 3
		GY_UpdateComboBox(CShadowRan, "Very Far")
	EndIf
	CurrentShadowR$ = GY_ComboBoxItem(CShadowRan)

;Music
	; Misc options window
	WMisc = GY_CreateWindow("Sound options", 0.25, 0.4, 0.5, 0.1, True, True, False, LoadTexture("Data\Textures\GUI\window.bmp"))
	GY_CreateLabel(WMisc, 0.18, 0.02, "Client must be restarted for any changes to take effect.", 255, 0, 0)
	SVolume = GY_CreateSlider(WMisc, 0.05, 0.26, 0.9, 0.3, LanguageString$(LS_SndVolume), Int(DefaultVolume# * 100.0), 0, 100, False, 255, 255, 255)
	CurrentVolume = Int(DefaultVolume# * 100.0)
	BUpdateMusic = GY_CreateCheckBox(WMisc, 0.05, 0.7, LanguageString$(LS_SkipMusic))
	GY_UpdateCheckBox(BUpdateMusic, 1 - UpdateMusic)
	BMiscDone = GY_CreateCustomButton(WMisc, 0.72, 0.62, 0.23, 0.33, LoadButtonU("MenuDone"), LoadButtonD("MenuDone"), LoadButtonH("MenuDone"))

	; Control options window
	WControls = GY_CreateWindow(LanguageString$(LS_ControlOptions), 0.2, 0.15, 0.6, 0.25, True, True, False, LoadTexture("Data\Textures\GUI\window.bmp"))
	LForwardKey     = GY_CreateLabel(WControls, 0.05, 0.04,    LanguageString$(LS_CForward) + " " + ControlName$(Key_Forward) + String$(" ", 30))
	LBackwardKey    = GY_CreateLabel(WControls, 0.05, 0.11,    LanguageString$(LS_CStop) + " " + ControlName$(Key_Back) + String$(" ", 30))
	LRightKey       = GY_CreateLabel(WControls, 0.05, 0.18,    LanguageString$(LS_CTurnRight) + " " + ControlName$(Key_TurnRight) + String$(" ", 30))
	LLeftKey        = GY_CreateLabel(WControls, 0.05, 0.25,    LanguageString$(LS_CTurnLeft) + " " + ControlName$(Key_TurnLeft) + String$(" ", 30))
	LFlyUpKey       = GY_CreateLabel(WControls, 0.05, 0.32,    LanguageString$(LS_CFlyUp) + " " + ControlName$(Key_FlyUp) + String$(" ", 30))
	LFlyDownKey     = GY_CreateLabel(WControls, 0.05, 0.39,    LanguageString$(LS_CFlyDown) + " " + ControlName$(Key_FlyDown) + String$(" ", 30))
	LSelectKey      = GY_CreateLabel(WControls, 0.05, 0.46,    LanguageString$(LS_CSelect) + " " + ControlName$(Key_Select) + String$(" ", 30))
	;LMoveToKey      = GY_CreateLabel(WControls, 0.05, 0.53,    LanguageString$(LS_CMoveTo) + " " + ControlName$(Key_MoveTo) + String$(" ", 30))
	LTalkToKey      = GY_CreateLabel(WControls, 0.05, 0.60,    LanguageString$(LS_CTalkTo) + " " + ControlName$(Key_TalkTo) + String$(" ", 30))
	;9
	LRunKey         = GY_CreateLabel(WControls, 0.55, 0.04,    LanguageString$(LS_CRun) + " " + ControlName$(Key_Run) + String$(" ", 30))
	LAlwaysRunKey   = GY_CreateLabel(WControls, 0.55, 0.11,    LanguageString$(LS_CAlwaysRun) + " " + ControlName$(Key_AlwaysRun) + String$(" ", 30))
	LJumpKey        = GY_CreateLabel(WControls, 0.55, 0.18,    LanguageString$(LS_CJump) + " " + ControlName$(Key_Jump) + String$(" ", 30))
	LViewKey        = GY_CreateLabel(WControls, 0.55, 0.25,    LanguageString$(LS_CViewMode) + " " + ControlName$(Key_ChangeViewMode) + String$(" ", 30))
	LCamForwardKey  = GY_CreateLabel(WControls, 0.55, 0.32,    LanguageString$(LS_CZoomIn) + " " + ControlName$(Key_CameraIn) + String$(" ", 30))
	LCamBackwardKey = GY_CreateLabel(WControls, 0.55, 0.39,    LanguageString$(LS_CZoomOut) + " " + ControlName$(Key_CameraOut) + String$(" ", 30))
	LAttackKey      = GY_CreateLabel(WControls, 0.55, 0.46,    LanguageString$(LS_CAttackTarget) + " " + ControlName$(Key_Attack) + String$(" ", 30))
	LCycleTargetKey = GY_CreateLabel(WControls, 0.55, 0.53,    LanguageString$(LS_CCycleTarget) + " " + ControlName$(Key_CycleTarget) + String$(" ", 30))
	;Check
	BInvertAxis1    = GY_CreateCheckBox(WControls, 0.05, 0.73, LanguageString$(LS_InvertAxis1))
	BInvertAxis3    = GY_CreateCheckBox(WControls, 0.05, 0.85, LanguageString$(LS_InvertAxis3))
	BControlsDone   = GY_CreateCustomButton(WControls, 0.8, 0.8, 0.17, 0.145, LoadButtonU("MenuDone"), LoadButtonD("MenuDone"), LoadButtonH("MenuDone"))
	SelectedControl = 0
	If InvertAxis1 = -1 Then GY_UpdateCheckBox(BInvertAxis1, True)
	If InvertAxis3 = -1 Then GY_UpdateCheckBox(BInvertAxis3, True)
;----------------------------------------
	; Create options menu buttons
	
	If ResolutionType = 1 ; 16:9 ratio
		BGFXOpts = GY_CreateCustomButton(0, 0.9 + 0.05, 0.78, 0.15, 0.04, LoadButtonU("Graphics"), LoadButtonD("Graphics"), LoadButtonH("Graphics"))
		BCtrlOpts = GY_CreateCustomButton(0, 0.9 + 0.05, 0.835, 0.15, 0.04, LoadButtonU("Control"), LoadButtonD("Control"), LoadButtonH("Control"))
		BMiscOpts = GY_CreateCustomButton(0, 0.9 + 0.05, 0.89, 0.15, 0.04, LoadButtonU("Other"), LoadButtonD("Other"), LoadButtonH("Other"))
		BQuit = GY_CreateCustomButton(0, 0.9 + 0.05, 0.945, 0.15, 0.04, LoadButtonU("Quit"), LoadButtonD("Quit"), LoadButtonH("Quit"))
	Else
		BGFXOpts = GY_CreateCustomButton(0, 0.8, 0.78, 0.18, 0.04, LoadButtonU("Graphics"), LoadButtonD("Graphics"), LoadButtonH("Graphics"))
		BCtrlOpts = GY_CreateCustomButton(0, 0.8, 0.835, 0.18, 0.04, LoadButtonU("Control"), LoadButtonD("Control"), LoadButtonH("Control"))
		BMiscOpts = GY_CreateCustomButton(0, 0.8, 0.89, 0.18, 0.04, LoadButtonU("Other"), LoadButtonD("Other"), LoadButtonH("Other"))
		BQuit = GY_CreateCustomButton(0, 0.8, 0.945, 0.18, 0.04, LoadButtonU("Quit"), LoadButtonD("Quit"), LoadButtonH("Quit"))
	EndIf
	
	GY_DropGadget(BGFXOpts) : GY_DropGadget(BCtrlOpts) : GY_DropGadget(BMiscOpts) : GY_DropGadget(BQuit)
	
	; Hide all windows
	GY_GadgetAlpha(WGraphics, 0.0, True) : GY_LockGadget(WGraphics) : GraphicsVisible = False
	GY_GadgetAlpha(WSound, 0.0, True) : GY_LockGadget(WSound) : SoundVisible = False
	GY_GadgetAlpha(WMisc, 0.0, True) : GY_LockGadget(WMisc) : MiscVisible = False
	GY_GadgetAlpha(WControls, 0.0, True) : GY_LockGadget(WControls) : ControlsVisible = False

	; Menu loop
	Repeat
	;---------------------------
	;OPTIONS END part1
	;---------------------------

	; Read in last username/password
	F = ReadFile("Data\Last Username.dat")
	If F <> 0
		GY_UpdateTextField(TName, ReadLine$(F))
		GY_UpdateTextField(TPass, Encrypt$(ReadLine$(F), 1))
		CloseFile(F)
	EndIf
	
	; Split hostname and port if they are joined
	Colon = Instr(ServerHost$, ":")
	If Colon > 0 Then
		ServerPort% = Mid(ServerHost$, Colon + 1)
		ServerHost$ = Left(ServerHost$, Colon - 1)
	End If
	
	; Connect to server
	.Connect 
	GY_UpdateLabel(TStatus, LanguageString$(LS_ConnectingToServer))
	;.
	GY_Update() : UpdateWorld() : RenderWorld() : Flip()
    Port = 11001
	Repeat
		PeerToHost = RCE_Connect(ServerHost$, ServerPort, Port, "X", "", "Data\Logs\Client Connection.txt", False)
		Port = Port + 1
	Until PeerToHost <> RN_ConnectionInUse And PeerToHost <> RN_PortInUse
	
	Select PeerToHost 
 Case RCE_HostNotFound : GY_UpdateLabel(TStatus, LanguageString$(LS_InvalidHost)) 
 Case RCE_TimedOut : GY_UpdateLabel(TStatus, LanguageString$(LS_NoResponse)) 
 Case RCE_ServerFull : GY_UpdateLabel(TStatus, LanguageString$(LS_TooManyPlayers)) 
 Default : GY_UpdateLabel(TStatus, LanguageString$(LS_Connected)) 
 End Select


	; Event loop
	.Invld
	;GY_UpdateLabel(TStatus, LanguageString$(LS_Connected))
	FlushKeys()
	GY_ActivateTextField(TPass)
	Repeat
		; Escape pressed or window closed (quit)
		If KeyHit(1) Then RCE_Disconnect() : End

		; Enter pressed (log in if password box is in focus)
		If GY_TextFieldHit(TPass)
			Goto Login
		; Tab pressed 
			ElseIf KeyHit(15)
			; Select new focus
			If GY_TextFieldActive(TName)
				GY_ActivateTextField(TPass)
			ElseIf GY_TextFieldActive(TPass)
				GY_ActivateTextField(TEmail)
			Else
				GY_ActivateTextField(TName)
			EndIf
			FlushKeys()
		EndIf

		; Log in button pressed
		If GY_ButtonHit(BLogin) = True
			.Login
			
			Select PeerToHost 
 Case RCE_HostNotFound : GY_UpdateLabel(TStatus, LanguageString$(LS_InvalidHost)) : Goto Connect 
 Case RCE_TimedOut : GY_UpdateLabel(TStatus, LanguageString$(LS_NoResponse)) : Goto Connect 
 Case RCE_ServerFull : GY_UpdateLabel(TStatus, LanguageString$(LS_TooManyPlayers)) : Goto Connect 
 Default : GY_UpdateLabel(TStatus, LanguageString$(LS_Connected)) 
 End Select
			
			
			; Fire off verification message
		
			Name$ = GY_TextFieldText$(TName)
			Pass$ = GY_TextFieldText$(TPass)
			If Len(Name$) < 2 Then GY_MessageBox("Attention!", LanguageString$(LS_InvalidUsername)) : Goto Invld ;LanguageString$(LS_Error)
			If Len(Pass$) < 2 Then GY_MessageBox("Attention!", LanguageString$(LS_InvalidPassword)) : Goto Invld
			MD5Pass$ = MD5$(Pass$)
			Pa$ = RCE_StrFromInt$(Len(Name$), 1) + Name$ + RCE_StrFromInt$(Len(MD5Pass$), 1) + MD5Pass$
			RCE_Send(Connection, PeerToHost, P_VerifyAccount, Pa$, True)
		
			; Wait for reply
			GY_UpdateLabel(TStatus, LanguageString$(LS_WaitingForReply))
			Done = False
			While Done = False
				Delay 10
				If KeyHit(1) Then RCE_Disconnect() : End
				For M.RCE_Message = Each RCE_Message

					If M\MessageType = P_VerifyAccount
						If Left$(M\MessageData$, 1) = "N"
							Result = -2
						ElseIf Left$(M\MessageData$, 1) = "P"
							Result = -1
						ElseIf Left$(M\MessageData$, 1) = "B"
							Result = 0
						ElseIf Left$(M\MessageData$, 1) = "L"
							Result = -3
						Else
							Result = 1 : CharList$ = Right$(M\MessageData$, Len(M\MessageData$) - 1)
						EndIf
						Delete M : Done = True : Exit
					ElseIf M\MessageType = RN_HostHasLeft Or M\MessageType = RN_Disconnected
						RuntimeError(LanguageString$(LS_LostConnection))
					EndIf
					Delete M
				Next
				
			
				RCE_Update()
				RCE_CreateMessages()
				
				GY_Update()
				UpdateWorld()
				RenderWorld()
				Flip(VSync)
			Wend
			; If successful, download Actor/Attributes lists and go to character selection
			If Result = 1
				; Save username/password
				F = WriteFile("Data\Last Username.dat")
					WriteLine F, GY_TextFieldText$(TName)
					WriteLine F, Encrypt$(GY_TextFieldText$(TPass), -1)
				CloseFile F
				UName$ = GY_TextFieldText$(TName) : PWord$ = MD5$(GY_TextFieldText$(TPass))

				; Request actors list
				RCE_Send(Connection, PeerToHost, P_FetchActors, "", True)
				; Wait for reply
				GY_UpdateTextField(TStatus, LanguageString$(LS_DownloadingChars))
				Done = False
				HadAttributes = False : HadDamageTypes = False : HadEnvironment = False
				ActorsCreated = 0 : ActorsRequired = -1
				ItemsCreated = 0 : ItemsRequired = -1
				FactionsReceived = 0
				While Done = False
					Delay 10
					If KeyHit(1) Then RCE_Disconnect() : End
					For M.RCE_Message = Each RCE_Message
						If M\MessageType = P_FetchActors
							Pa$ = M\MessageData$
							; Attributes block
							If Left$(Pa$, 1) = "A"
								AttributeAssignment = RCE_IntFromStr(Mid$(Pa$, 2, 1))
								Offset = 3
								For i = 0 To 39
									AttributeIsSkill(i) = RCE_IntFromStr(Mid$(Pa$, Offset, 1))
									AttributeHidden(i) = RCE_IntFromStr(Mid$(Pa$, Offset + 1, 1))
									NameLen = RCE_IntFromStr(Mid$(Pa$, Offset + 2, 1))
									AttributeNames$(i) = Mid$(Pa$, Offset + 3, NameLen)
									Offset = Offset + 3 + NameLen
								Next
								HadAttributes = True
								Delete M
							; Damage types block
							ElseIf Left$(Pa$, 1) = "D"
								Offset = 2
								For i = 0 To 19
									NameLen = RCE_IntFromStr(Mid$(Pa$, Offset, 1))
									DamageTypes$(i) = Mid$(Pa$, Offset + 1, NameLen)
									Offset = Offset + 1 + NameLen
								Next
								HadDamageTypes = True
								Delete M
							; Environment block
							ElseIf Left$(Pa$, 1) = "E"
								Year = RCE_IntFromStr(Mid$(Pa$, 2, 4))
								Day = RCE_IntFromStr(Mid$(Pa$, 6, 2))
								TimeH = RCE_IntFromStr(Mid$(Pa$, 8, 1))
								TimeM = RCE_IntFromStr(Mid$(Pa$, 9, 1))
								TimeFactor = RCE_IntFromStr(Mid$(Pa$, 10, 1))
								Offset = 11
								For i = 0 To 11
									NameLen = RCE_IntFromStr(Mid$(Pa$, Offset, 1))
									SeasonName$(i) = Mid$(Pa$, Offset + 1, NameLen)
									Offset = Offset + 1 + NameLen
									SeasonStartDay(i) = RCE_IntFromStr(Mid$(Pa$, Offset, 2))
									SeasonDuskH(i) = RCE_IntFromStr(Mid$(Pa$, Offset + 2, 1))
									SeasonDawnH(i) = RCE_IntFromStr(Mid$(Pa$, Offset + 3, 1))
									Offset = Offset + 4
								Next
								For i = 0 To 19
									NameLen = RCE_IntFromStr(Mid$(Pa$, Offset, 1))
									MonthName$(i) = Mid$(Pa$, Offset + 1, NameLen)
									Offset = Offset + 1 + NameLen
									MonthStartDay(i) = RCE_IntFromStr(Mid$(Pa$, Offset, 2))
									Offset = Offset + 2
								Next
								CurrentSeason = GetSeason()
								
								; Game Server Sync terrier
							TimeUpdate = MilliSecs() ; for game time Sync, see also environment.bb 	
							
								HadEnvironment = True
								Delete M
							; Factions block
							ElseIf Left$(Pa$, 1) = "F"
								Offset = 2
								While Offset < Len(Pa$)
									NameLen = RCE_IntFromStr(Mid$(Pa$, Offset, 1))
									Num = RCE_IntFromStr(Mid$(Pa$, Offset + 1, 1))
									FactionNames$(Num) = Mid$(Pa$, Offset + 2, NameLen)
									FactionsReceived = FactionsReceived + 1
									Offset = Offset + 2 + NameLen
								Wend
								Delete M
							; Item block
							ElseIf Left$(Pa$, 1) = "I" And HadAttributes = True
								; If final item block, we get an extra handy total number of items
								If Mid$(Pa$, 2, 1) = "Y"
									ItemsRequired = RCE_IntFromStr(Mid$(Pa$, 3, 2))
									Offset = 5
								Else
									Offset = 3
								EndIf
								While Offset < Len(Pa$)
									ItemsCreated = ItemsCreated + 1
									It.Item = New Item
									It\Attributes = New Attributes
									It\ID = RCE_IntFromStr(Mid$(Pa$, Offset, 2))
									ItemList(It\ID) = It
									It\ItemType = RCE_IntFromStr(Mid$(Pa$, Offset + 2, 1))
									It\TakesDamage = RCE_IntFromStr(Mid$(Pa$, Offset + 3, 1))
									It\Value = RCE_IntFromStr(Mid$(Pa$, Offset + 4, 4))
									It\Mass = RCE_IntFromStr(Mid$(Pa$, Offset + 8, 2))
									It\ThumbnailTexID = RCE_IntFromStr(Mid$(Pa$, Offset + 10, 2))
									Offset = Offset + 12
									For j = 0 To 5
										It\Gubbins[j] = RCE_IntFromStr(Mid$(Pa$, Offset, 1))
										Offset = Offset + 1
									Next
									It\MMeshID = RCE_IntFromStr(Mid$(Pa$, Offset, 2))
									It\FMeshID = RCE_IntFromStr(Mid$(Pa$, Offset + 2, 2))
									It\SlotType = RCE_IntFromStr(Mid$(Pa$, Offset + 4, 2))
									It\Stackable = RCE_IntFromStr(Mid$(Pa$, Offset + 6, 1))
									Offset = Offset + 7
									For j = 0 To 39
										It\Attributes\Value[j] = RCE_IntFromStr(Mid$(Pa$, Offset, 2)) - 5000
										Offset = Offset + 2
									Next
									NameLen = RCE_IntFromStr(Mid$(Pa$, Offset, 1))
									It\Name$ = Mid$(Pa$, Offset + 1, NameLen)
									Offset = Offset + 1 + NameLen
									NameLen = RCE_IntFromStr(Mid$(Pa$, Offset, 1))
									It\ExclusiveRace$ = Mid$(Pa$, Offset + 1, NameLen)
									Offset = Offset + 1 + NameLen
									NameLen = RCE_IntFromStr(Mid$(Pa$, Offset, 1))
									It\ExclusiveClass$ = Mid$(Pa$, Offset + 1, NameLen)
									Offset = Offset + 1 + NameLen
									Select It\ItemType
										Case I_Weapon
											It\WeaponDamage = RCE_IntFromStr(Mid$(Pa$, Offset, 2))
											It\WeaponDamageType = RCE_IntFromStr(Mid$(Pa$, Offset + 2, 2))
											It\WeaponType = RCE_IntFromStr(Mid$(Pa$, Offset + 4, 2))
											It\Range# = RCE_FloatFromStr#(Mid$(Pa$, Offset + 6, 4))
											Offset = Offset + 10
										Case I_Armour
											It\ArmourLevel = RCE_IntFromStr(Mid$(Pa$, Offset, 2))
											Offset = Offset + 2
										Case I_Potion, I_Ingredient
											It\EatEffectsLength = RCE_IntFromStr(Mid$(Pa$, Offset, 2))
											Offset = Offset + 2
										Case I_Image
											It\ImageID = RCE_IntFromStr(Mid$(Pa$, Offset, 2))
											Offset = Offset + 2
										Case I_Other
											NameLen = RCE_IntFromStr(Mid$(Pa$, Offset, 1))
											It\MiscData$ = Mid$(Pa$, Offset + 1, NameLen)
											Offset = Offset + 1 + NameLen
									End Select
								Wend
								Delete M
							; Actor block
							ElseIf HadAttributes = True
								; If final actor block, we get an extra handy total number of actors
								If Left$(Pa$, 1) = "Y"
									ActorsRequired = RCE_IntFromStr(Mid$(Pa$, 2, 2))
									Offset = 4
								Else
									Offset = 2
								EndIf
								While Offset < Len(Pa$)
									ActorsCreated = ActorsCreated + 1
									A.Actor = New Actor
									A\Attributes = New Attributes
									A\ID = RCE_IntFromStr(Mid$(Pa$, Offset, 2))
									ActorList(A\ID) = A
									A\Playable = RCE_IntFromStr(Mid$(Pa$, Offset + 2, 1))
									A\PolyCollision = RCE_IntFromStr(Mid$(Pa$, Offset + 3, 1))
									For i = 0 To 7
										A\MeshIDs[i] = RCE_IntFromStr(Mid$(Pa$, Offset + 4 + (i * 2), 2))
									Next
									For i = 0 To 4
										A\BeardIDs[i] = RCE_IntFromStr(Mid$(Pa$, Offset + 20 + (i * 2), 2))
									Next
									For i = 0 To 4
										A\MaleHairIDs[i] = RCE_IntFromStr(Mid$(Pa$, Offset + 30 + (i * 2), 2))
									Next
									For i = 0 To 4
										A\FemaleHairIDs[i] = RCE_IntFromStr(Mid$(Pa$, Offset + 40 + (i * 2), 2))
									Next
									For i = 0 To 4
										A\MaleFaceIDs[i] = RCE_IntFromStr(Mid$(Pa$, Offset + 50 + (i * 2), 2))
									Next
									For i = 0 To 4
										A\FemaleFaceIDs[i] = RCE_IntFromStr(Mid$(Pa$, Offset + 60 + (i * 2), 2))
									Next
									For i = 0 To 4
										A\MaleBodyIDs[i] = RCE_IntFromStr(Mid$(Pa$, Offset + 70 + (i * 2), 2))
									Next
									For i = 0 To 4
										A\FemaleBodyIDs[i] = RCE_IntFromStr(Mid$(Pa$, Offset + 80 + (i * 2), 2))
									Next
									For i = 0 To 15
										A\MSpeechIDs[i] = RCE_IntFromStr(Mid$(Pa$, Offset + 90 + (i * 2), 2))
									Next
									For i = 0 To 15
										A\FSpeechIDs[i] = RCE_IntFromStr(Mid$(Pa$, Offset + 122 + (i * 2), 2))
									Next
									Offset = Offset + 154
									A\Rideable       = RCE_IntFromStr(Mid$(Pa$, Offset, 1))
									A\TradeMode      = RCE_IntFromStr(Mid$(Pa$, Offset + 1, 1))
									A\BloodTexID     = RCE_IntFromStr(Mid$(Pa$, Offset + 2, 2))
									A\Aggressiveness = RCE_IntFromStr(Mid$(Pa$, Offset + 4, 1))
									A\Genders        = RCE_IntFromStr(Mid$(Pa$, Offset + 5, 1))
									A\Environment    = RCE_IntFromStr(Mid$(Pa$, Offset + 6, 1))
									A\InventorySlots = RCE_IntFromStr(Mid$(Pa$, Offset + 7, 2))
									A\MAnimationSet  = RCE_IntFromStr(Mid$(Pa$, Offset + 9, 2))
									A\FAnimationSet  = RCE_IntFromStr(Mid$(Pa$, Offset + 11, 2))
									A\Scale#         = RCE_FloatFromStr#(Mid$(Pa$, Offset + 13, 4))
									A\DefaultFaction = RCE_IntFromStr(Mid$(Pa$, Offset + 17, 1))
									Offset = Offset + 18
									For i = 0 To 39
										A\Attributes\Value[i] = RCE_IntFromStr(Mid$(Pa$, Offset, 2))
										A\Attributes\Maximum[i] = RCE_IntFromStr(Mid$(Pa$, Offset + 2, 2))
										Offset = Offset + 4
									Next
									NameLen = RCE_IntFromStr(Mid$(Pa$, Offset, 1))
									A\Race$ = Mid$(Pa$, Offset + 1, NameLen)
									Offset = Offset + 1 + NameLen
									NameLen = RCE_IntFromStr(Mid$(Pa$, Offset, 1))
									A\Class$ = Mid$(Pa$, Offset + 1, NameLen)
									Offset = Offset + 1 + NameLen
									NameLen = RCE_IntFromStr(Mid$(Pa$, Offset, 2))
									A\Description$ = Mid$(Pa$, Offset + 2, NameLen)
									Offset = Offset + 2 + NameLen
								Wend
								Delete M
							EndIf

							; If we have all the required actors and items, we can continue
							If ActorsCreated = ActorsRequired And ItemsCreated = ItemsRequired
								If HadDamageTypes = True And HadEnvironment = True And HadAttributes = True And FactionsReceived = 100
									Delete M : Done = True : Exit
								EndIf
							EndIf
						ElseIf M\MessageType = RN_HostHasLeft Or M\MessageType = RN_Disconnected
							RuntimeError(LanguageString$(LS_LostConnection))
						EndIf
					Next
					
					RCE_Update()
					RCE_CreateMessages()
					
					GY_Update()
					UpdateWorld()
					RenderWorld()
					Flip(VSync)
				Wend
	
	
				; Clear window and exit
				
				FreeEntity(Background)
				FreeEntity(Background3)
				GY_FreeGadget(TStatus) : GY_FreeGadget(LName) : GY_FreeGadget(LPass)
				GY_FreeGadget(LEmail) : GY_FreeGadget(TName) : GY_FreeGadget(TPass)
				GY_FreeGadget(TEmail) : GY_FreeGadget(BLogin)
				GY_FreeGadget(WGraphics) : GY_FreeGadget(WControls) : GY_FreeGadget(WMisc)
			GY_FreeGadget(BGFXOpts) : GY_FreeGadget(BMiscOpts) : GY_FreeGadget(BCtrlOpts) : GY_FreeGadget(BQuit)
				
				If AccountsEnabled = True Then GY_FreeGadget(BNew)
				Return
			ElseIf Result = 0
				GY_MessageBox("Attention!", LanguageString$(LS_YouAreBanned)) : Goto Invld
			ElseIf Result = -1
				GY_MessageBox("Attention!", LanguageString$(LS_InvalidPassword)) : Goto Invld
			ElseIf Result = -2
				GY_MessageBox("Attention!", LanguageString$(LS_AccountDoesNotExist)) : Goto Invld
			Else
				GY_MessageBox("Attention!", LanguageString$(LS_AccountAlreadyConnected)) : Goto Invld
			EndIf
		EndIf

		; New account button pressed
		If GY_ButtonHit(BNew) = True
		Select PeerToHost 
 Case RCE_HostNotFound : GY_UpdateLabel(TStatus, LanguageString$(LS_InvalidHost)) : Goto Connect 
 Case RCE_TimedOut : GY_UpdateLabel(TStatus, LanguageString$(LS_NoResponse)) : Goto Connect 
 Case RCE_ServerFull : GY_UpdateLabel(TStatus, LanguageString$(LS_TooManyPlayers)) : Goto Connect 
 Default : GY_UpdateLabel(TStatus, LanguageString$(LS_Connected)) 
 End Select
			; Send new account message
			Name$ = GY_TextFieldText$(TName)
			Pass$ = GY_TextFieldText$(TPass)
			Email$ = GY_TextFieldText$(TEmail)
			If Len(Name$) < 2 Then GY_MessageBox("Attention!", LanguageString$(LS_InvalidUsername)) : Goto Invld
			If Len(Pass$) < 2 Then GY_MessageBox("Attention!", LanguageString$(LS_InvalidPassword)) : Goto Invld
			If Len(Email$) < 5 Or Instr(Email$, "@") = 0 Or Instr(Email$, ".") = 0
				GY_MessageBox("Attention!", LanguageString$(LS_InvalidEmailAddress)) : Goto Invld
			EndIf
			MD5Pass$ = MD5$(Pass$)
			Pa$ = RCE_StrFromInt$(Len(Name$), 1) + Name$ + RCE_StrFromInt$(Len(MD5Pass$), 1) + MD5Pass$
			Pa$ = Pa$ + RCE_StrFromInt$(Len(Email$), 1) + Encrypt$(Email$, -1)
			
			RCE_Send(Connection, PeerToHost, P_CreateAccount, Pa$, True)

			; Wait for reply
			Done = False
			GY_UpdateLabel(TStatus, LanguageString$(LS_WaitingForReply))
			While Done = False
				Delay 10
				If KeyHit(1) Then RCE_Disconnect() : End
				For M.RCE_Message = Each RCE_Message
					If M\MessageType = P_CreateAccount
						If M\MessageData$ = "Y" Then Result = True Else Result = False
						Delete M : Done = True : Exit
					ElseIf M\MessageType = RN_HostHasLeft Or M\MessageType = RN_Disconnected
						RuntimeError(LanguageString$(LS_LostConnection))
					EndIf
					Delete M
				Next
				
				RCE_Update()
				RCE_CreateMessages()
				
	
				GY_Update()
				UpdateWorld()
				RenderWorld()
				Flip(VSync)
			Wend

			; Display result
			If Result = False
				GY_MessageBox("Attention!", LanguageString$(LS_UsernameAlreadyExists)) : Goto Invld
			Else
				GY_MessageBox("Attention!", LanguageString$(LS_NewAccountCreated)) : Goto Invld
			EndIf
		EndIf

		; Check connection is still alive
		For M.RCE_Message = Each RCE_Message
			Select M\MessageType
				Case RN_Disconnected, RN_HostHasLeft
					RuntimeError(LanguageString$(LS_LostConnection))
			End Select
			Delete M
		Next
		

;-------------------------------
;OPTIONS
;-------------------------------
		; Options selected
		If GY_ButtonHit(BQuit) Or KeyHit(1)
			End
		ElseIf GY_ButtonHit(BGFXOpts)
			GraphicsVisible = Not GraphicsVisible
			If GraphicsVisible = True
				GY_GadgetAlpha(WGraphics, 1.0, True) : GY_LockGadget(WGraphics, False)
				GY_ActivateWindow(WGraphics)
			Else
				GY_GadgetAlpha(WGraphics, 0.0, True) : GY_LockGadget(WGraphics, True)
			EndIf
		ElseIf GY_ButtonHit(BMiscOpts)
			MiscVisible = Not MiscVisible
			If MiscVisible = True
				GY_GadgetAlpha(WMisc, 1.0, True) : GY_LockGadget(WMisc, False)
				GY_ActivateWindow(WMisc)
			Else
				GY_GadgetAlpha(WMisc, 0.0, True) : GY_LockGadget(WMisc, True)
			EndIf
		ElseIf GY_ButtonHit(BCtrlOpts)
			ControlsVisible = Not ControlsVisible
			If ControlsVisible = True
				GY_GadgetAlpha(WControls, 1.0, True) : GY_LockGadget(WControls, False)
				GY_ActivateWindow(WControls)
				SelectedControl = 0
				GY_LeftClick = False
			Else
				GY_GadgetAlpha(WControls, 0.0, True) : GY_LockGadget(WControls, True)
			EndIf
		EndIf

		; Update graphics window
		If GY_WindowActive(WGraphics) And GraphicsVisible = True
			ComboChanged = GY_ListBoxItem$(LResolution) <> CurrentRes$ Or GY_ComboBoxItem$(CDepth) <> CurrentDepth$ Or GY_ComboBoxItem$(CAnisotropy) <> CurrentAnisotropy$ Or GY_ComboBoxItem$(CShadowCom) <> CurrentShadowC$ Or GY_ComboBoxItem$(CShadowQual) <> CurrentShadowQ$ Or GY_ComboBoxItem$(CShadowRan) <> CurrentShadowR$

			
			If GY_CheckBoxHit(BAntiAlias) Or GY_CheckBoxHit(BEnableGrass) Or GY_CheckBoxHit(BFullScreen) Or GY_CheckBoxHit(BVSyncOption) Or GY_CheckBoxHit(BBloom) Or GY_CheckBoxHit(BRays) Or GY_CheckBoxHit(BWater) Or ComboChanged
				CurrentRes$ = GY_ListBoxItem(LResolution)
				CurrentDepth$ = GY_ComboBoxItem(CDepth)
				CurrentAnisotropy$ = GY_ComboBoxItem(CAnisotropy)
				CurrentShadowC$ = GY_ComboBoxItem(CShadowCom)
				CurrentShadowQ$ = GY_ComboBoxItem(CShadowQual)
				CurrentShadowR$ = GY_ComboBoxItem(CShadowRan)
				; Update file
				F = OpenFile("Data\Options.dat")
				If F = 0 Then RuntimeError("Could not open Data\Options.dat!")
					Divider = Instr(CurrentRes$, "x")
					If Divider = 0 Then RuntimeError("Invalid resolution format!")
					Width = Trim$(Left$(CurrentRes$, Divider - 1))
					Height = Trim$(Mid$(CurrentRes$, Divider + 1))
					WriteShort(F, Width)
					WriteShort(F, Height)
					If CurrentDepth$ = "16 bit"
						WriteByte(F, 16)
					ElseIf CurrentDepth$ = "32 bit"
						WriteByte(F, 32)
					ElseIf CurrentDepth$ = "Best"
						WriteByte(F, 0)
					EndIf
					WriteByte(F, GY_CheckBoxDown(BAntiAlias))
					SeekFile(F, 10)
					WriteByte(F, GY_CheckBoxDown(BEnableGrass))
					
					If CurrentAnisotropy$ = LanguageString$(LS_Disabled)
						WriteByte(F, 0)
					ElseIf CurrentAnisotropy$ = "x4"
						WriteByte(F, 4)
					ElseIf CurrentAnisotropy$ = "x8"
						WriteByte(F, 8)
					ElseIf CurrentAnisotropy$ = "x16"
						WriteByte(F, 16)
					EndIf

					WriteByte(F,GY_CheckBoxDown(BFullScreen))
					WriteByte(F,GY_CheckBoxDown(BVSyncOption))
					;FastExt Settings Cysis145
					WriteByte(F,GY_CheckBoxDown(BBloom))
					WriteByte(F,GY_CheckBoxDown(BRays))
					WriteByte(F,GY_CheckBoxDown(BWater))
					;Add shadow complexity to the options menu Cysis145
					If CurrentShadowC$ = "Off"
						WriteByte(F, 0)
					ElseIf CurrentShadowC$ = "On"
						WriteByte(F, 1)
					EndIf
					;Add shadow quality to the options menu Cysis145
					If CurrentShadowQ$ = "Low"
						WriteByte(F, 0)
					ElseIf CurrentShadowQ$ = "Medium"
						WriteByte(F, 1)
					ElseIf CurrentShadowQ$ = "High"
						WriteByte(F, 2)
					EndIf
					;Add shadow Range to the options menu Cysis145
					If CurrentShadowR$ = "Short"
						WriteByte(F, 0)
					ElseIf CurrentShadowR$ = "Medium"
						WriteByte(F, 1)
					ElseIf CurrentShadowR$ = "Far"
						WriteByte(F, 2)
					ElseIf CurrentShadowR$ = "Very Far"
						WriteByte(F, 3)					
					EndIf
				CloseFile(F)
			EndIf
		EndIf

		; Update controls window
		If GY_WindowActive(WControls) And ControlsVisible = True
			; Invert mouse options
			If GY_CheckBoxHit(BInvertAxis1)
				InvertAxis1 = 0 - InvertAxis1
				SaveControlBindings("Data\Controls.dat")
			ElseIf GY_CheckBoxHit(BInvertAxis3)
				InvertAxis3 = 0 - InvertAxis3
				SaveControlBindings("Data\Controls.dat")
			EndIf

			; Control remapping
			If SelectedControl = 0
				; Select a control to assign to
				If GY_LeftClick
					If GY_MouseHovering(LForwardKey)
						SelectedControl = 1 : GY_UpdateLabel(LForwardKey, LanguageString$(LS_CForward) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LBackwardKey)
						SelectedControl = 2 : GY_UpdateLabel(LBackwardKey, LanguageString$(LS_CStop) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LRightKey)
						SelectedControl = 3 : GY_UpdateLabel(LRightKey, LanguageString$(LS_CTurnRight) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LLeftKey)
						SelectedControl = 4 : GY_UpdateLabel(LLeftKey, LanguageString$(LS_CTurnLeft) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LFlyUpKey)
						SelectedControl = 5 : GY_UpdateLabel(LFlyUpKey, LanguageString$(LS_CFlyUp) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LFlyDownKey)
						SelectedControl = 6 : GY_UpdateLabel(LFlyDownKey, LanguageString$(LS_CFlyDown) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LRunKey)
						SelectedControl = 7 : GY_UpdateLabel(LRunKey, LanguageString$(LS_CRun) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LJumpKey)
						SelectedControl = 8 : GY_UpdateLabel(LJumpKey, LanguageString$(LS_CJump) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LViewKey)
						SelectedControl = 9 : GY_UpdateLabel(LViewKey, LanguageString$(LS_CViewMode) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LCamForwardKey)
						SelectedControl = 10 : GY_UpdateLabel(LCamForwardKey, LanguageString$(LS_CZoomIn) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LCamBackwardKey)
						SelectedControl = 11 : GY_UpdateLabel(LCamBackwardKey, LanguageString$(LS_CZoomOut) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LAttackKey)
						SelectedControl = 12 : GY_UpdateLabel(LAttackKey, LanguageString$(LS_CAttackTarget) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LAlwaysRunKey)
						SelectedControl = 13 : GY_UpdateLabel(LAlwaysRunKey, LanguageString$(LS_CAlwaysRun) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LCycleTargetKey)
						SelectedControl = 14 : GY_UpdateLabel(LCycleTargetKey, LanguageString$(LS_CCycleTarget) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					;ElseIf GY_MouseHovering(LMoveToKey)
						;SelectedControl = 15 : GY_UpdateLabel(LMoveToKey, LanguageString$(LS_CMoveTo) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LTalkToKey)
						SelectedControl = 16 : GY_UpdateLabel(LTalkToKey, LanguageString$(LS_CTalkTo) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LSelectKey)
						SelectedControl = 17 : GY_UpdateLabel(LSelectKey, LanguageString$(LS_CSelect) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					EndIf
					GY_LeftClick = False
				EndIf
			; Assignment
			Else
				; Keyboard
				Ctrl = -1
				For i = 0 To 211
					If ControlName$(i) <> LanguageString$(LS_Unknown)
						If KeyDown(i) Then Ctrl = i
					EndIf
				Next
				; Mouse
				MZSpeed = MouseZSpeed()
				JXSpeed = JoyXDir()
				JYSpeed = JoyYDir()
				If GY_LeftClick
					Ctrl = 501
				ElseIf GY_RightClick
					Ctrl = 502
				ElseIf MouseHit(3)
					Ctrl = 503
				ElseIf MZSpeed > 0
					Ctrl = 508
				ElseIf MZSpeed < 0
					Ctrl = 509
				EndIf
				; Joystick
				If JoyType() > 0
					If JYSpeed > 0
						Ctrl = 1014
					ElseIf JYSpeed < 0
						Ctrl = 1013
					ElseIf JXSpeed > 0
						Ctrl = 1015
					ElseIf JXSpeed < 0
						Ctrl = 1016
					EndIf
					For i = 1 To 8
						If JoyHit(i) Then Ctrl = 1000 + i
					Next
					If JoyHat() > -1
						If JoyHat() = 0 Or JoyHat() = 45 Or JoyHat() = 315
							Ctrl = 1009
						ElseIf JoyHat() = 270
							Ctrl = 1012
						ElseIf JoyHat() = 180 Or JoyHat() = 135 Or JoyHat() = 225
							Ctrl = 1010
						ElseIf JoyHat() = 90
							Ctrl = 1011
						EndIf
					EndIf
				EndIf

				; A control has been selected
				If Ctrl > -1
					Select SelectedControl
						Case 1 : Key_Forward = Ctrl : GY_UpdateLabel(LForwardKey, LanguageString$(LS_CForward) + " " + ControlName$(Key_Forward), 255, 255, 255)
						Case 2 : Key_Back = Ctrl : GY_UpdateLabel(LBackwardKey, LanguageString$(LS_CStop) + " " + ControlName$(Key_Back), 255, 255, 255)
						Case 3 : Key_TurnRight = Ctrl : GY_UpdateLabel(LRightKey, LanguageString$(LS_CTurnRight) + " " + ControlName$(Key_TurnRight), 255, 255, 255)
						Case 4 : Key_TurnLeft = Ctrl : GY_UpdateLabel(LLeftKey, LanguageString$(LS_CTurnLeft) + " " + ControlName$(Key_TurnLeft), 255, 255, 255)
						Case 5 : Key_FlyUp = Ctrl : GY_UpdateLabel(LFlyUpKey, LanguageString$(LS_CFlyUp) + " " + ControlName$(Key_FlyUp), 255, 255, 255)
						Case 6 : Key_FlyDown = Ctrl : GY_UpdateLabel(LFlyDownKey, LanguageString$(LS_CFlyDown) + " " + ControlName$(Key_FlyDown), 255, 255, 255)
						Case 7 : Key_Run = Ctrl : GY_UpdateLabel(LRunKey, LanguageString$(LS_CRun) + " " + ControlName$(Key_Run), 255, 255, 255)
						Case 8 : Key_Jump = Ctrl : GY_UpdateLabel(LJumpKey, LanguageString$(LS_CJump) + " " + ControlName$(Key_Jump), 255, 255, 255)
						Case 9 : Key_ChangeViewMode = Ctrl : GY_UpdateLabel(LViewKey, LanguageString$(LS_CViewMode) + " " + ControlName$(Key_ChangeViewMode), 255, 255, 255)
						Case 10 : Key_CameraIn = Ctrl : GY_UpdateLabel(LCamForwardKey, LanguageString$(LS_CZoomIn) + " " + ControlName$(Key_CameraIn), 255, 255, 255)
						Case 11 : Key_CameraOut = Ctrl : GY_UpdateLabel(LCamBackwardKey, LanguageString$(LS_CZoomOut) + " " + ControlName$(Key_CameraOut), 255, 255, 255)
						Case 12 : Key_Attack = Ctrl : GY_UpdateLabel(LAttackKey, LanguageString$(LS_CAttackTarget) + " " + ControlName$(Key_Attack), 255, 255, 255)
						Case 13 : Key_AlwaysRun = Ctrl : GY_UpdateLabel(LAlwaysRunKey, LanguageString$(LS_CAlwaysRun) + " " + ControlName$(Key_AlwaysRun), 255, 255, 255)
						Case 14 : Key_CycleTarget = Ctrl : GY_UpdateLabel(LCycleTargetKey, LanguageString$(LS_CCycleTarget) + " " + ControlName$(Key_CycleTarget), 255, 255, 255)
						Case 15 : Key_MoveTo = Ctrl : GY_UpdateLabel(LMoveToKey, LanguageString$(LS_CMoveTo) + " " + ControlName$(Key_MoveTo), 255, 255, 255)
						Case 16 : Key_TalkTo = Ctrl : GY_UpdateLabel(LTalkToKey, LanguageString$(LS_CTalkTo) + " " + ControlName$(Key_TalkTo), 255, 255, 255)
						Case 17 : Key_Select = Ctrl : GY_UpdateLabel(LSelectKey, LanguageString$(LS_CSelect) + " " + ControlName$(Key_Select), 255, 255, 255)
					End Select
					SelectedControl = 0
					SaveControlBindings("Data\Controls.dat")
				EndIf
			EndIf
		EndIf

		; Update misc window
		If GY_WindowActive(WMisc) And MiscVisible = True
			; Volume
			If Int(GY_GetSliderValue#(SVolume)) <> CurrentVolume
				CurrentVolume = Int(GY_GetSliderValue#(SVolume))
				DefaultVolume# = CurrentVolume / 100.0
				; Update file
				F = OpenFile("Data\Options.dat")
				If F = 0 Then RuntimeError("Could not open Data\Options.dat!")
					SeekFile F, 6
					WriteFloat F, DefaultVolume#
				CloseFile(F)
			EndIf
			; Music update
			If GY_CheckBoxDown(BUpdateMusic) <> 1 - UpdateMusic
				UpdateMusic = 1 - GY_CheckBoxDown(BUpdateMusic)
				; Update file
				F = WriteFile("Data\Game Data\Misc.dat")
					WriteLine F, GameName$
					WriteLine F, UpdateGame$
					WriteLine F, UpdateMusic
				CloseFile F
			EndIf
		EndIf
				
		; Windows being closed
		If GY_WindowClosed(WGraphics) Or GY_ButtonHit(BGFXDone)
			GraphicsVisible = False
			GY_GadgetAlpha(WGraphics, 0.0, True) : GY_LockGadget(WGraphics, True)
		ElseIf GY_WindowClosed(WMisc) Or GY_ButtonHit(BMiscDone)
			MiscVisible = False
			GY_GadgetAlpha(WMisc, 0.0, True) : GY_LockGadget(WMisc, True)
		ElseIf GY_WindowClosed(WControls) Or GY_ButtonHit(BControlsDone)
			ControlsVisible = False
			GY_GadgetAlpha(WControls, 0.0, True) : GY_LockGadget(WControls, True)
		EndIf
	
		; Update everything
		GY_Update()
		UpdateWorld()
		RenderWorld()
		Flip(VSync)
		
		Forever
;-------------------------------
;OPTIONS END
;-------------------------------
	Forever
	
	FreeEntity(Background)

End Function

; Character selection dialog
Function CharSelect()

	; Background
	;Logo = LoadSprite("Data\Textures\Menu Logo.bmp")
	;ScaleSprite(Logo, 0.75, 0.375)
	;PositionEntity(Logo, 0.4, 1.1, 2)
	;EntityOrder(Logo, -1)
	;EntityParent(Logo, Cam)

	;Character selection
	Background = GY_CreateQuad(Cam)
	
	If ResolutionType = 1 ; 16:9 ratio
		PositionEntity(Background, -9.4 - 2.5, 6.75, 10.0)
		ScaleEntity(Background, 6.0, 12.5, 1.0)
	Else
		PositionEntity(Background, -9.4, 6.75, 10.0)
		ScaleEntity(Background, 6.0, 12.5, 1.0)
	EndIf
	
	EntityOrder(Background, -1)
	Tex = LoadTexture("Data\Textures\Menu\Character Selection.png",4) ; opacity terrier
	EntityTexture(Background, Tex)
	FreeTexture(Tex)

	GPP = CreatePivot()
	Local PreviewA.ActorInstance = Null

	.RestartCharSelection
	If PreviewA <> Null Then SafeFreeActorInstance(PreviewA)

	; Create the window
	If ResolutionType = 1 ; 16:9 ratio
		BStart  = GY_CreateCustomButton(0, 0.07, 0.81, 0.1, 0.04, LoadButtonU("SelectCharacter"), LoadButtonD("SelectCharacter"), LoadButtonH("SelectCharacter"))
		BDelete = GY_CreateCustomButton(0, -0.075, 0.81, 0.1, 0.04, LoadButtonU("DeleteCharacter"), LoadButtonD("DeleteCharacter"), LoadButtonH("DeleteCharacter"))
	Else
		BStart  = GY_CreateCustomButton(0, 0.2, 0.81, 0.1, 0.04, LoadButtonU("SelectCharacter"), LoadButtonD("SelectCharacter"), LoadButtonH("SelectCharacter"))
		BDelete = GY_CreateCustomButton(0, 0.055, 0.81, 0.1, 0.04, LoadButtonU("DeleteCharacter"), LoadButtonD("DeleteCharacter"), LoadButtonH("DeleteCharacter"))
	EndIf
	
	;BLeft   = GY_CreateCustomButton(0, 0.54, 0.896, 0.035, 0.04, LoadButtonU("LargeLeft"), LoadButtonD("LargeLeft"), LoadButtonH("LargeLeft"))
	;BRight  = GY_CreateCustomButton(0, 0.7, 0.896, 0.035, 0.04, LoadButtonU("LargeRight"), LoadButtonD("LargeRight"), LoadButtonH("LargeRight"))
	GY_DropGadget(BStart)
	GY_DropGadget(BDelete)
	;GY_DropGadget(BLeft)
	;GY_DropGadget(BRight)

	; Character buttons (max of 10 characters)
	Offset = 1 : Number = 0
	While Offset < Len(CharList$)
		; Extract data
		Length = RCE_IntFromStr(Mid$(CharList$, Offset, 1))
		CharNames$(Number) = Mid$(CharList$, Offset + 1, Length)
		Offset = Offset + Length + 1
		CharActors(Number) = RCE_IntFromStr(Mid$(CharList$, Offset, 2))
		CharGender(Number) = RCE_IntFromStr(Mid$(CharList$, Offset + 2, 1))
		CharFaceTex(Number) = RCE_IntFromStr(Mid$(CharList$, Offset + 3, 1))
		CharHair(Number) = RCE_IntFromStr(Mid$(CharList$, Offset + 4, 1))
		CharBeard(Number) = RCE_IntFromStr(Mid$(CharList$, Offset + 5, 1))
		CharBodyTex(Number) = RCE_IntFromStr(Mid$(CharList$, Offset + 6, 1))

		; Move on
		Offset = Offset + 7
		Number = Number + 1
		
		; Create button
		If ResolutionType = 1 ; 16:9 ratio
			CharButtons(Number - 1) = GY_CreateButton(0, -0.07, 0.22 + (Float#(Number) * 0.05), 0.25, 0.04, CharNames$(Number - 1), 1, 255, 255, 255, LoadButtonU("CharBox"))
		Else
			CharButtons(Number - 1) = GY_CreateButton(0, 0.055, 0.22 + (Float#(Number) * 0.05), 0.25, 0.04, CharNames$(Number - 1), 1, 255, 255, 255, LoadButtonU("CharBox"))
		EndIf
		GY_DropGadget(CharButtons(Number - 1))
	Wend
	LastChar = Number
	; New character button
	If Number < 10
		If ResolutionType = 1 ; 16:9 ratio	
			CharButtons(Number) = GY_CreateCustomButton(0, -0.065, 0.25 + (Float#(Number + 1) * 0.05), 0.238, 0.05, LoadButtonU("NewCharacter"), LoadButtonD("NewCharacter"), LoadButtonH("NewCharacter"))
		Else
			CharButtons(Number) = GY_CreateCustomButton(0, 0.061, 0.25 + (Float#(Number + 1) * 0.05), 0.238, 0.05, LoadButtonU("NewCharacter"), LoadButtonD("NewCharacter"), LoadButtonH("NewCharacter"))
		EndIf
		GY_DropGadget(CharButtons(Number))
	EndIf
	SelectedChar = -1
	Local setup% = False
	
	PositionEntity GPP, 30, -35, 100

	lastFPS = MilliSecs()
	tfps# = 60
	fps# = 60
	; Event loop
	Repeat
		; Delta timing bits
		DeltaTime = MilliSecs() - DeltaTime
		DeltaBuffer(DeltaBufferIndex) = DeltaTime
		DeltaBufferIndex = DeltaBufferIndex + 1
		If DeltaBufferIndex >= DeltaFrames Then DeltaBufferIndex = 0
	
		; Take average of last N frames to get delta time coefficient
		Time# = 0.0
		For i = 0 To DeltaFrames - 1
			Time# = Time# + DeltaBuffer(i)
		Next
		Time# = Time# / Float#(DeltaFrames)
		FPS# = 1000.0 / Time#
		Delta# = BaseFramerate# / FPS#
		DeltaTime = MilliSecs()
		If Delta# > 3.5 Then Delta# = 3.5 ; Don't let delta go too OTT
	
		Local targetTime%
		Local lastUpdateTime%
		Local targetEntity%
		
		; Escape pressed (quit)
		If KeyHit(1) Then RCE_Disconnect() : End
		; Check for existing character buttons being pressed
		For i = 0 To LastChar - 1
			If GY_ButtonHit(CharButtons(i)) = True
				setup = True
				targetEntity = 0
								
				; Untoggle old button
				If SelectedChar > -1 Then GY_SetButtonState(CharButtons(SelectedChar), False)
				SelectedChar = i
				GY_SetButtonState(CharButtons(i), True)

				A.Actor = ActorList(CharActors(i))
				If PreviewA <> Null Then SafeFreeActorInstance(PreviewA)
				PreviewA = CreateActorInstance(A)
				PreviewA\Gender = CharGender(i)
				PreviewA\FaceTex = CharFaceTex(i)
				PreviewA\Hair = CharHair(i)
				PreviewA\Beard = CharBeard(i)
				PreviewA\BodyTex = CharBodyTex(i)
				
				Result = LoadActorInstance3D(PreviewA, 1, False, True)
				If Result = False Then RuntimeError("Could not load actor mesh for " + A\Race$ + "!")
				;If PreviewA\ShadowEN <> 0 Then HideEntity(PreviewA\ShadowEN) [###]
				;If PreviewA\NametagEN <> 0 Then HideEntity(PreviewA\NametagEN)
				PlayAnimation(PreviewA, 1, 0.001, Anim_Idle)
				PositionEntity PreviewA\CollisionEN, 30, -(35.0 + EntityY#(PreviewA\EN, True)), 100
				Exit
			EndIf
		Next
		
		; Create new character button
		If GY_ButtonHit(CharButtons(LastChar)) = True
			For i = 0 To 9
				If CharButtons(i) <> 0 Then GY_FreeGadget(CharButtons(i))
			Next
			GY_FreeGadget(BStart) : GY_FreeGadget(BDelete)
			;GY_FreeGadget(BLeft) : GY_FreeGadget(BRight)
			HideEntity(Background)
			;HideEntity(Logo)
			If PreviewA <> Null Then SafeFreeActorInstance(PreviewA)
			Result = CreateChar()
			If Result = 1 Then GY_MessageBox("Attention!", LanguageString$(LS_CannotCreateChar))
			;ShowEntity(Logo)
			ShowEntity(Background)
			Goto RestartCharSelection
		EndIf

		; Delete character button
		If GY_ButtonHit(BDelete) = True And SelectedChar > -1
		;Fix increased Animation speed bug when a error message appears cysis145 [234]
			Animate(PreviewA\EN, 0)
			PlayAnimation(PreviewA, 1, 0.0001, Anim_Idle)

			If GY_RequestBox("Warning!", LanguageString$(LS_ReallyDeleteChar)) ;LanguageString$(LS_Warning)
				Pa$ = RCE_StrFromInt$(Len(UName$), 1) + UName$ + RCE_StrFromInt$(Len(PWord$), 1) + PWord$
				RCE_Send(Connection, PeerToHost, P_DeleteCharacter, Pa$ + Chr$(SelectedChar), True)
			
				
				
				;PlayAnimation(PreviewA, 1, 0.003, Anim_Idle)
				
				
				; Wait for reply
				Done = False
				While Done = False
				;	Delay 10
					For M.RCE_Message = Each RCE_Message
						If M\MessageType = P_DeleteCharacter
							CharList$ = M\MessageData$
							Delete M
							For i = 0 To 9
								If CharButtons(i) <> 0 Then GY_FreeGadget(CharButtons(i))
							Next
							GY_FreeGadget(BStart) : GY_FreeGadget(BDelete)
						;	;GY_FreeGadget(BLeft) : GY_FreeGadget(BRight)
							If PreviewA <> Null Then SafeFreeActorInstance(PreviewA)
							
							
							
							Goto RestartCharSelection
						ElseIf M\MessageType = RN_HostHasLeft Or M\MessageType = RN_Disconnected
							RuntimeError(LanguageString$(LS_LostConnection))
						EndIf
						Delete M
					Next
										
					RCE_Update()
					RCE_CreateMessages()
					
					GY_Update()
					

					
					UpdateWorld()
					RenderWorld()
					Flip()
				Wend
			EndIf
			
			;[234]
			Animate(PreviewA\EN, 0)
			PlayAnimation(PreviewA, 1, 0.003, Anim_Idle)
			
		EndIf

		; Start game button
		If GY_ButtonHit(BStart) = True And SelectedChar > -1
		
		RP_Clear(SnowEmitter1)	
		RP_Clear(SnowEmitter2)
		RP_Clear(SnowEmitter3)
		RP_Clear(SnowEmitter4)
		RP_Clear(SnowEmitter5)
		RP_Clear(SnowEmitter6)
					
			GY_LockGadget(BStart) : GY_LockGadget(BDelete)
			For i = 0 To LastChar + 1 : GY_LockGadget(CharButtons(i)) : Next

			Pa$ = RCE_StrFromInt$(Len(UName$), 1) + UName$ + RCE_StrFromInt$(Len(PWord$), 1) + PWord$
			RCE_Send(Connection, PeerToHost, P_FetchCharacter, Pa$ + Chr$(SelectedChar), True)

			; Stuff I already know
			Me = CreateActorInstance(ActorList(CharActors(SelectedChar)))
			Me\Name$ = CharNames$(SelectedChar)
			Me\Gender = CharGender(SelectedChar)
			Me\FaceTex = CharFaceTex(SelectedChar)
			Me\Hair = CharHair(SelectedChar)
			Me\Beard = CharBeard(SelectedChar)
			Me\BodyTex = CharBodyTex(SelectedChar)

			; Wait for replies
			Quests = 0 : RequiredQuests = 1000
			Spells = 0 : RequiredSpells = 2000 : Memorised = 0
			ItemsDone = 0
			AttributesDone = 0
			Done = False
			While Done = False
				Delay 10
				For M.RCE_Message = Each RCE_Message
					If M\MessageType = P_FetchCharacter
						; Character information
						If Left$(M\MessageData$, 1) = "C"
							; Block 1
							If Mid$(M\MessageData$, 2, 1) = "1"
								Me\Gold = RCE_IntFromStr(Mid$(M\MessageData$, 3, 4))
								Me\Reputation = RCE_IntFromStr(Mid$(M\MessageData$, 7, 2))
								Me\Level = RCE_IntFromStr(Mid$(M\MessageData$, 9, 2))
								Me\XP = RCE_IntFromStr(Mid$(M\MessageData$, 11, 4))
								Me\HomeFaction = RCE_IntFromStr(Mid$(M\MessageData$, 15, 1))
								Offset = 16
								While Offset < Len(M\MessageData$)
									Me\Attributes\Value[AttributesDone] = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 2))
									Me\Attributes\Maximum[AttributesDone] = RCE_IntFromStr(Mid$(M\MessageData$, Offset + 2, 2))
									AttributesDone = AttributesDone + 1
									Offset = Offset + 4
								Wend
							; Block 2 <No longer exists, reserved for future use>
;							ElseIf Mid$(M\MessageData$, 2, 1) = "2"

							; Block 3
							ElseIf Mid$(M\MessageData$, 2, 1) = "3"
								Offset = 3
								While Offset <= Len(M\MessageData$)
									Position = Asc(Mid$(M\MessageData$, Offset, 1))
									If Position < Slots_Inventory+1
										Item$ = Mid$(M\MessageData$, Offset + 1, ItemInstanceStringLength())
										Me\Inventory\Items[Position] = ItemInstanceFromString(Item$)
										Offset = Offset + 1 + ItemInstanceStringLength()
										Me\Inventory\Amounts[Position] = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 2))
										Offset = Offset + 2
									Else
										Offset = Offset + 1									
									EndIf
									ItemsDone = ItemsDone + 1
								Wend
							EndIf
						; A known spells block
						ElseIf Left$(M\MessageData$, 1) = "S"
							Offset = 2
							While Offset < Len(M\MessageData$)
								Me\SpellLevels[Spells] = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 2))
								Sp.Spell = New Spell
								Sp\ID = RCE_IntFromStr(Mid$(M\MessageData$, Offset + 2, 2))
								SpellsList(Sp\ID) = Sp
								Me\KnownSpells[Spells] = Sp\ID
								Sp\ThumbnailTexID = RCE_IntFromStr(Mid$(M\MessageData$, Offset + 4, 2))
								Sp\RechargeTime = RCE_IntFromStr(Mid$(M\MessageData$, Offset + 6, 2))
								Offset = Offset + 8
								NameLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 2))
								Sp\Name$ = Mid$(M\MessageData$, Offset + 2, NameLen)
								Offset = Offset + 2 + NameLen
								NameLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 2))
								Sp\Description$ = Mid$(M\MessageData$, Offset + 2, NameLen)
								Offset = Offset + 2 + NameLen
								If RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1)) = True And Memorised < 10
									Me\MemorisedSpells[Memorised] = Spells
									Memorised = Memorised + 1
								EndIf
								Offset = Offset + 1
								Spells = Spells + 1
							Wend
						; A quest log block
						ElseIf Left$(M\MessageData$, 1) = "Q"
							Offset = 2
							While Offset < Len(M\MessageData$)
								NameLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))
								QuestLog\EntryName$[Quests] = Mid$(M\MessageData$, Offset + 1, NameLen)
								Offset = Offset + 1 + NameLen
								NameLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 2))
								QuestLog\EntryStatus$[Quests] = Mid$(M\MessageData$, Offset + 2, NameLen)
								Offset = Offset + 2 + NameLen
								Quests = Quests + 1
							Wend
						; Final block
						Else
							RequiredQuests = RCE_IntFromStr(Mid$(M\MessageData$, 2, 2))
							RequiredSpells = RCE_IntFromStr(Mid$(M\MessageData$, 4, 2))
						EndIf

						Delete M

						; Complete!
						If Quests >= RequiredQuests And Spells >= RequiredSpells And AttributesDone > 39 And ItemsDone = Slots_Inventory+1
							Done = True : Exit
						EndIf
					ElseIf M\MessageType = RN_HostHasLeft Or M\MessageType = RN_Disconnected
						RuntimeError(LanguageString$(LS_LostConnection))
						Delete M
					EndIf
				Next
				
				RCE_Update()
				RCE_CreateMessages()
				
				GY_Update()
				UpdateWorld(Delta#)
				RenderWorld()
				Flip(VSync)
			Wend

			; Start the game!
			RCE_Disconnect()
			SelectedCharacter = SelectedChar
			
			
			Exit
		EndIf
		
		; Camera
		;If GY_ButtonDown(BRight)
		;	CamAngle# = CamAngle# + 90/fps
		;ElseIf GY_ButtonDown(BLeft)
		;	CamAngle# = CamAngle# -  90/fps
		;EndIf
		If setup = False
			RotateEntity GPP, 0.0, CamAngle# , 0.0
			;CamAngle#
			TFormPoint 0.0, 0.0, -150.0, GPP, 0
			;TFormPoint 0.0, 120.0, -150.0, GPP, 0
			PositionEntity Cam, TFormedX#(), TFormedY#(), TFormedZ#()
			PointEntity Cam, GPP  ;GPP
			MoveEntity Cam, -40.0, 0.0, 0.0 
		EndIf
		Local dx#,dy#,dz#
		
		If setup = True
						
			If targetEntity = 0
				targetTime% = MilliSecs() + 500
				lastUpdateTime = MilliSecs()
				
				targetEntity = FindChild(PreviewA\EN,"CamTarget")
				If targetEntity = 0
					targetEntity = FindChild(PreviewA\EN,"Chest")
					If targetEntity > 0
						TFormPoint 0,0,0, targetEntity, GPP
						dx = TFormedX() : dy = TFormedY() : dz = TFormedZ()
					EndIf
				Else
					TFormPoint 0,0,0, targetEntity, GPP
					dx = TFormedX() : dy = TFormedY() : dz = TFormedZ()
				EndIf
				If targetEntity = 0
					targetEntity = FindChild(PreviewA\EN,"Head")
					TFormPoint 0,0,0, targetEntity, GPP
					dx = TFormedX() : dy = TFormedY() : dz = TFormedZ()
				EndIf
				
				If targetEntity = 0
					PositionEntity GPP, 30, -35, 100
					targetEntity = GPP
					Goto SetupExit
					setup = False
				EndIf
			EndIf
			
			If EntityDistance(targetEntity,GPP) < 0.5
				setup = False
				Goto SetupExit
			EndIf
						
			Local value#
			
			If MilliSecs() < targetTime
				value = (MilliSecs() - lastUpdateTime) / 500.0
			Else
				value = (targetTime - lastUpdateTime) / 500.0
			EndIf
			lastUpdateTime = MilliSecs()
			
			MoveEntity GPP, dx * value, dy*value, dz*value
			TFormPoint 0.0, 0.0, -150.0, GPP, 0
			;TFormPoint 0.0, 120.0, -150.0, GPP, 0
			PositionEntity Cam, TFormedX#(), TFormedY#(), TFormedZ#()
			PointEntity Cam, GPP
			MoveEntity Cam, -40.0, 0.0, 0.0
			
			If MilliSecs() > targetTime
				setup = False
			EndIf
			.SetupExit
		EndIf		

		; Check connection is still alive
		For M.RCE_Message = Each RCE_Message
			Select M\MessageType
				Case RN_Disconnected, RN_HostHasLeft
					RuntimeError(LanguageString$(LS_LostConnection))
			End Select
			Delete M
		Next

		; Update everything
		RCE_Update()
		RCE_CreateMessages()
		
		GY_Update()
		RP_Update()
		UpdateWorld(delta#)
		RenderWorld()
		Flip(VSync)

		tfps = tfps + 1
		If MilliSecs() - lastFPS > 999
			fps# = 0.5*fps + 0.5*tfps
			tfps = 0
			lastFPS = MilliSecs()
		EndIf
	Forever

	For i = 0 To 9
		If CharButtons(i) <> 0 Then GY_FreeGadget(CharButtons(i))
	Next
	GY_FreeGadget(BStart) : GY_FreeGadget(BDelete)
	;GY_FreeGadget(BLeft) : GY_FreeGadget(BRight)
	If PreviewA <> Null Then SafeFreeActorInstance(PreviewA)
	;FreeEntity(Logo)
	FreeEntity(Background)
	FreeEntity(Set)
	FreeEntity(GPP)
	
	
	

End Function

; Character creation dialog
Function CreateChar()

	;Local PreviewA.ActorInstance = Null

	; Clear point spends
	For i = 0 To 39 : PointSpends(i) = 0 : Next

	; Count assignable attributes
	TotalAttributes = 0
	For i = 0 To 39
		If AttributeNames$(i) <> "" And AttributeIsSkill(i) = False And AttributeHidden(i) = False
			TotalAttributes = TotalAttributes + 1
		EndIf
	Next

	; Create the windows
	;StatWindowHeight# = Float#(TotalAttributes + 1) * 0.05
	
	StatWindowHeight# = Float#(TotalAttributes + 1) * 0.05
	
	; Create the windows
	If ResolutionType = 1 ; 16:9 ratio [@@]
		WChar = GY_CreateWindow(LanguageString$(LS_CharacterTitle), 0.01 - 0.12, 0.05, 0.22, 0.4, True, False, False)
		WStat = GY_CreateWindow(LanguageString$(LS_AttributesTitle), 0.74 + 0.12, 0.05, 0.25, StatWindowHeight#, True, False, False)
	Else
		WChar = GY_CreateWindow(LanguageString$(LS_CharacterTitle), 0.01, 0.05, 0.22, 0.4, True, False, False)
		WStat = GY_CreateWindow(LanguageString$(LS_AttributesTitle), 0.74, 0.05, 0.25, StatWindowHeight#, True, False, False)
	EndIf
	
	; Race list Rebuilt by Cysis145 to fix Bug with selecting Race 
	CRace = GY_CreateComboBoxS(WChar, 0.05, 0.05, 0.9, 0.5, LanguageString$(LS_Race))
	For A.Actor = Each Actor
		If A\Playable = True
			; Check every previous actor to make sure this race hasn't already been added
			AlreadyAdded = False
			For A2.Actor = Each Actor
				If A2 = A Then Exit
				If A2\Playable = True
					If Upper$(A2\Race$) = Upper$(A\Race$) Then AlreadyAdded = True : Exit
				EndIf
			Next
			If AlreadyAdded = False Then GY_AddComboBoxItem(CRace, A\Race$)
		EndIf
	Next
	
	; Camera movement
	BLeft  = GY_CreateCustomButton(0, 0.37, 0.92, 0.035, 0.04, LoadButtonU("LargeLeft"), LoadButtonD("LargeLeft"), LoadButtonH("LargeLeft"))
	BRight = GY_CreateCustomButton(0, 0.55, 0.92, 0.035, 0.04, LoadButtonU("LargeRight"), LoadButtonD("LargeRight"), LoadButtonH("LargeRight"))
	GY_DropGadget(BLeft)
	GY_DropGadget(BRight)

	; Character options
	GY_CreateLabel(WChar, 0.5, 0.21, LanguageString$(LS_Gender), 255, 255, 255, Justify_Centre)
	BNextGender = GY_CreateCustomButton(WChar, 0.86, 0.2, 0.1, 0.07, LoadButtonU("SmallRight"), LoadButtonD("SmallRight"), LoadButtonH("SmallRight"))
	BPrevGender = GY_CreateCustomButton(WChar, 0.04, 0.2, 0.1, 0.07, LoadButtonU("SmallLeft"), LoadButtonD("SmallLeft"), LoadButtonH("SmallLeft"))
	;BNextGender = GY_CreateButton(WChar, 0.86, 0.2, 0.1, 0.07, ">")
	;BPrevGender = GY_CreateButton(WChar, 0.04, 0.2, 0.1, 0.07, "<")
	GY_CreateLabel(WChar, 0.5, 0.34, LanguageString$(LS_Class), 255, 255, 255, Justify_Centre)
	BNextClass = GY_CreateCustomButton(WChar, 0.86, 0.33, 0.1, 0.07, LoadButtonU("SmallRight"), LoadButtonD("SmallRight"), LoadButtonH("SmallRight"))
	BPrevClass = GY_CreateCustomButton(WChar, 0.04, 0.33, 0.1, 0.07, LoadButtonU("SmallLeft"), LoadButtonD("SmallLeft"), LoadButtonH("SmallLeft"))
	;BNextClass = GY_CreateButton(WChar, 0.86, 0.33, 0.1, 0.07, ">")
	;BPrevClass = GY_CreateButton(WChar, 0.04, 0.33, 0.1, 0.07, "<")
	GY_CreateLabel(WChar, 0.5, 0.47, LanguageString$(LS_Hair), 255, 255, 255, Justify_Centre)
	BNextHair = GY_CreateCustomButton(WChar, 0.86, 0.46, 0.1, 0.07, LoadButtonU("SmallRight"), LoadButtonD("SmallRight"), LoadButtonH("SmallRight"))
	BPrevHair = GY_CreateCustomButton(WChar, 0.04, 0.46, 0.1, 0.07, LoadButtonU("SmallLeft"), LoadButtonD("SmallLeft"), LoadButtonH("SmallLeft"))
	;BNextHair = GY_CreateButton(WChar, 0.86, 0.46, 0.1, 0.07, ">")
	;BPrevHair = GY_CreateButton(WChar, 0.04, 0.46, 0.1, 0.07, "<")
	GY_CreateLabel(WChar, 0.5, 0.60, LanguageString$(LS_Face), 255, 255, 255, Justify_Centre)
	BNextFace = GY_CreateCustomButton(WChar, 0.86, 0.59, 0.1, 0.07, LoadButtonU("SmallRight"), LoadButtonD("SmallRight"), LoadButtonH("SmallRight"))
	BPrevFace = GY_CreateCustomButton(WChar, 0.04, 0.59, 0.1, 0.07, LoadButtonU("SmallLeft"), LoadButtonD("SmallLeft"), LoadButtonH("SmallLeft"))
	;BNextFace = GY_CreateButton(WChar, 0.86, 0.59, 0.1, 0.07, ">")
	;BPrevFace = GY_CreateButton(WChar, 0.04, 0.59, 0.1, 0.07, "<")
	GY_CreateLabel(WChar, 0.5, 0.73, LanguageString$(LS_Beard), 255, 255, 255, Justify_Centre)
	BNextBeard = GY_CreateCustomButton(WChar, 0.86, 0.72, 0.1, 0.07, LoadButtonU("SmallRight"), LoadButtonD("SmallRight"), LoadButtonH("SmallRight"))
	BPrevBeard = GY_CreateCustomButton(WChar, 0.04, 0.72, 0.1, 0.07, LoadButtonU("SmallLeft"), LoadButtonD("SmallLeft"), LoadButtonH("SmallLeft"))
	;BNextBeard = GY_CreateButton(WChar, 0.86, 0.72, 0.1, 0.07, ">")
	;BPrevBeard = GY_CreateButton(WChar, 0.04, 0.72, 0.1, 0.07, "<")
	GY_CreateLabel(WChar, 0.5, 0.86, LanguageString$(LS_Clothes), 255, 255, 255, Justify_Centre)
	BNextBody = GY_CreateCustomButton(WChar, 0.86, 0.85, 0.1, 0.07, LoadButtonU("SmallRight"), LoadButtonD("SmallRight"), LoadButtonH("SmallRight"))
	BPrevBody = GY_CreateCustomButton(WChar, 0.04, 0.85, 0.1, 0.07, LoadButtonU("SmallLeft"), LoadButtonD("SmallLeft"), LoadButtonH("SmallLeft"))
	;BNextBody = GY_CreateButton(WChar, 0.86, 0.85, 0.1, 0.07, ">")
	;BPrevBody = GY_CreateButton(WChar, 0.04, 0.85, 0.1, 0.07, "<")
	
	; Class description
	Y# = 0.7
	For i = 0 To 9
	If ResolutionType = 1 ; 16:9 ratio [@@]
		LDescription(i) = GY_CreateLabel(0, 0.01 - 0.12, Y#, String$("W", 50), 255, 255, 255, Justify_Left) ;Justify_Center
	Else
		LDescription(i) = GY_CreateLabel(0, 0.01, Y#, String$("W", 50), 255, 255, 255, Justify_Left) ;Justify_Cente
	EndIf
		GY_DropGadget(LDescription(i))
		GY_UpdateLabel(LDescription(i), "")
		Y# = Y# + 0.025
	Next

	; Name box and Done button
	;LName = GY_CreateLabel(0, 0.35, 0.86, LanguageString$(LS_CharacterName), 255, 255, 255, Justify_Right)
	TName = GY_CreateTextField(0, 0.3, 0.86, 0.35, 4, 25)	; Verification Textfield
	
	If ResolutionType = 1 ; 16:9 ratio [@@]
		BDone = GY_CreateCustomButton(0, 0.836 + 0.12, 0.936, 0.158, 0.058, LoadButtonU("CreateChar"), LoadButtonD("CreateChar"), LoadButtonH("CreateChar"))
		BCancel = GY_CreateCustomButton(0, 0.636 + 0.12, 0.936, 0.158, 0.058, LoadButtonU("CancelChar"), LoadButtonD("CancelChar"), LoadButtonH("CancelChar"))
	Else
		BDone = GY_CreateCustomButton(0, 0.836, 0.936, 0.158, 0.058, LoadButtonU("CreateChar"), LoadButtonD("CreateChar"), LoadButtonH("CreateChar"))
		BCancel = GY_CreateCustomButton(0, 0.636, 0.936, 0.158, 0.058, LoadButtonU("CancelChar"), LoadButtonD("CancelChar"), LoadButtonH("CancelChar"))
	EndIf
	;GY_DropGadget(LName)
	GY_DropGadget(TName)
	GY_DropGadget(BDone)
	GY_DropGadget(BCancel)

	; Attributes assignment list
	If AttributeAssignment > 0
		PointsToSpend = AttributeAssignment
		RemainingLabel = GY_CreateLabel(WStat, 0.5, 0.02, LanguageString$(LS_AttributePoints) + " " + PointsToSpend, 255, 50, 50, Justify_Centre)
	EndIf
	;Y# = 0.2675
	Y# = 0.05 / StatWindowHeight#

	;Y2# = 0.284

	Count = 0
	For i = 0 To 39
		If AttributeNames$(i) <> "" And AttributeIsSkill(i) = False And AttributeHidden(i) = False
			
			GY_CreateLabel(WStat, 0.02, Y#, AttributeNames$(i) + ":")
			AttributeLabels(Count) = GY_CreateLabel(Wstat, 0.8, Y#, "00000", 255, 255, 255, Justify_Centre)
						
			GY_DropGadget(AttributeLabels(Count))
			GY_SetGadgetData(AttributeLabels(Count), i)
			If AttributeAssignment > 0
				AttributeDecrease(Count) = GY_CreateCustomButton(WStat, 0.64, Y# + 0.0052, 0.08, 0.028 / StatWindowHeight#, LoadButtonU("SmallLeft"), LoadButtonD("SmallLeft"), LoadButtonH("SmallLeft"))
				AttributeIncrease(Count) = GY_CreateCustomButton(WStat, 0.90, Y# + 0.0052, 0.08, 0.028 / StatWindowHeight#, LoadButtonU("SmallRight"), LoadButtonD("SmallRight"), LoadButtonH("SmallRight"))
			EndIf
			Y# = Y# + (0.05 / StatWindowHeight#)
			Count = Count + 1
		EndIf
	Next

	; Set preview to first playable actor
	A.Actor = First Actor
	If A = Null Then RuntimeError("No actors in project!")
	While A\Playable = False
		A = After A
		If A = Null Then RuntimeError("No playable actors in project!")
	Wend
	Preview.ActorInstance = CreateActorInstance(A)
	Result = LoadActorInstance3D(Preview)
	If Result = False Then RuntimeError("Could not load actor mesh for " + A\Race$ + "!")
	PlayAnimation(Preview, 1, 0.003, Anim_Idle)
	PositionEntity Preview\CollisionEN, 30, -(35.0 + EntityY#(Preview\EN, True)), 100
	;HideEntity Preview\ShadowEN [###]
	SetUpPreview(Preview\Actor)
	GY_LockGadget(BNextFace, Not ActorHasFace(Preview\Actor, Preview\Gender + 1)) : GY_LockGadget(BPrevFace, Not ActorHasFace(Preview\Actor, Preview\Gender + 1))
	GY_LockGadget(BNextHair, Not ActorHasHair(Preview\Actor, Preview\Gender + 1)) : GY_LockGadget(BPrevHair, Not ActorHasHair(Preview\Actor, Preview\Gender + 1))
	GY_LockGadget(BNextBeard, Not ActorHasBeard(Preview\Actor)) : GY_LockGadget(BPrevBeard, Not ActorHasBeard(Preview\Actor))
	GY_LockGadget(BNextGender, Preview\Actor\Genders) : GY_LockGadget(BPrevGender, Preview\Actor\Genders)
	GY_UpdateComboBox(CRace, Preview\Actor\Race$)

	; Event loop
	Repeat
		; Delta timing bits
		DeltaTime = MilliSecs() - DeltaTime
		DeltaBuffer(DeltaBufferIndex) = DeltaTime
		DeltaBufferIndex = DeltaBufferIndex + 1
		If DeltaBufferIndex >= DeltaFrames Then DeltaBufferIndex = 0
	
		; Take average of last N frames to get delta time coefficient
		Time# = 0.0
		For i = 0 To DeltaFrames - 1
			Time# = Time# + DeltaBuffer(i)
		Next
		Time# = Time# / Float#(DeltaFrames)
		FPS# = 1000.0 / Time#
		Delta# = BaseFramerate# / FPS#
		DeltaTime = MilliSecs()
		If Delta# > 3.5 Then Delta# = 3.5 ; Don't let delta go too OTT
	
		; Escape pressed or cancel pressed
		If KeyHit(1) Or GY_ButtonHit(BCancel)
			SafeFreeActorInstance(Preview)
			;GY_FreeGadget(CRace) 
		For i = 0 To 39 : GY_FreeGadget(AttributeIncrease(i)) : GY_FreeGadget(AttributeDecrease(i)) : GY_FreeGadget(AttributeLabels(i)) : Next
			GY_FreeGadget(WChar) : GY_FreeGadget(WPrev) : GY_FreeGadget(WStat)

			GY_FreeGadget(LName) : GY_FreeGadget(TName) : GY_FreeGadget(BDone) : GY_FreeGadget(BCancel)
			GY_FreeGadget(BLeft) : GY_FreeGadget(BRight)
			GY_FreeGadget(ClassLab)
			GY_FreeGadget(RemainingLabel)
	
			For i = 0 To 9 : GY_FreeGadget(LDescription(i)) : Next
			Return 0
		EndIf

		; Remove special characters from character names
		Name$ = GY_TextFieldText$(TName)
		Name$ = Replace$(Name$, Chr$(34), "")
		Name$ = Replace$(Name$, "/", "")
		Name$ = Replace$(Name$, "\", "")
		Name$ = Replace$(Name$, "%", "")
		Name$ = Replace$(Name$, "&", "")
		Name$ = Replace$(Name$, "|", "")
		Name$ = Replace$(Name$, "�", "")
		Name$ = Replace$(Name$, "?", "")
		Name$ = Replace$(Name$, "#", "")
		Name$ = Replace$(Name$, ",", "")
		GY_UpdateTextField(TName, Name$)

		; Create button
		If GY_ButtonHit(BDone)
		
			;Fix increased Animation speed bug when a error message appears cysis145 [234]
			Animate(Preview\EN, 0)
			PlayAnimation(Preview, 1, 0.0001, Anim_Idle)
					
			; Check new character name is valid
			If Len(GY_TextFieldText$(TName)) < 2
				GY_MessageBox("Attention!", LanguageString$(LS_InvalidCharName))
			Else
				If PointsToSpend > 0
					Result = GY_RequestBox("Warning!", LanguageString$(LS_UnusedPoints))
				Else
					Result = 1
				EndIf

				If Result = 1
					; Send character information to server
					Pa$ = RCE_StrFromInt$(Len(UName$), 1) + UName$ + RCE_StrFromInt$(Len(PWord$), 1) + PWord$
					Pa$ = Pa$ + RCE_StrFromInt$(Preview\Actor\ID, 2) + RCE_StrFromInt$(Preview\Gender, 1)
					Pa$ = Pa$ + RCE_StrFromInt$(Preview\FaceTex, 1) + RCE_StrFromInt$(Preview\Hair, 1)
					Pa$ = Pa$ + RCE_StrFromInt$(Preview\Beard, 1) + RCE_StrFromInt$(Preview\BodyTex, 1)
					If AttributeAssignment > 0
						For i = 0 To 39
							Pa$ = Pa$ + RCE_StrFromInt$(PointSpends(i), 1)
						Next
					EndIf
					Pa$ = Pa$ + GY_TextFieldText$(TName)
					RCE_Send(Connection, PeerToHost, P_CreateCharacter, Pa$, True)

					; Wait for reply
					Done = False : Result = 0
					While Done = False
						Delay 10
						For M.RCE_Message = Each RCE_Message
							If M\MessageType = P_CreateCharacter
								If M\MessageData$ = "Y"
									Result = 2
								ElseIf M\MessageData$ = "I"
									Result = 1
								EndIf
								Delete(M) : Done = True : Exit
							ElseIf M\MessageType = RN_HostHasLeft Or M\MessageType = RN_Disconnected
								RuntimeError(LanguageString$(LS_LostConnection))
							EndIf
							Delete(M)
						Next

						RCE_Update()
						RCE_CreateMessages()
						
						GY_Update()
						UpdateWorld()
						RenderWorld()
						Flip(VSync)
					Wend

					; Add to character list and return
					If Result = 2
						CharList$ = CharList$ + RCE_StrFromInt$(Len(GY_TextFieldText$(TName)), 1) + GY_TextFieldText$(TName)
						CharList$ = CharList$ + RCE_StrFromInt$(Preview\Actor\ID, 2) + RCE_StrFromInt$(Preview\Gender, 1)
						CharList$ = CharList$ + RCE_StrFromInt$(Preview\FaceTex, 1) + RCE_StrFromInt$(Preview\Hair, 1)
						CharList$ = CharList$ + RCE_StrFromInt$(Preview\Beard, 1) + RCE_StrFromInt$(Preview\BodyTex, 1)
						SafeFreeActorInstance(Preview)
						GY_FreeGadget(BNextGender)
						
						;GY_FreeGadget(WPrev)
						GY_FreeGadget(WChar) : GY_FreeGadget(WPrev) : GY_FreeGadget(WStat)

						
						GY_FreeGadget(LName) : GY_FreeGadget(TName) : GY_FreeGadget(BDone) : GY_FreeGadget(BCancel)
						GY_FreeGadget(BLeft) : GY_FreeGadget(BRight)
						GY_FreeGadget(ClassLab)
						;GY_FreeGadget(CRace) 
		For i = 0 To 39 : GY_FreeGadget(AttributeIncrease(i)) : GY_FreeGadget(AttributeDecrease(i))  : GY_FreeGadget(AttributeLabels(i)) : Next
			
						GY_FreeGadget(RemainingLabel)
						;Character Options
						;FreeEntity(GenderLabel)
						;GY_FreeGadget(BNextGender)
						For i = 0 To 9 : GY_FreeGadget(LDescription(i)) : Next
						Return 2
					ElseIf Result = 1
						GY_MessageBox("                   Attention!", LanguageString$(LS_InvalidCharName))
					Else
						SafeFreeActorInstance(Preview)
						GY_FreeGadget(BNextGender)
						GY_FreeGadget(WChar) : GY_FreeGadget(WPrev) : GY_FreeGadget(WStat)
						GY_FreeGadget(LName) : GY_FreeGadget(TName) : GY_FreeGadget(BDone) : GY_FreeGadget(BCancel)
						GY_FreeGadget(BLeft) : GY_FreeGadget(BRight)
							GY_FreeGadget(ClassLab)
							;GY_FreeGadget(CRace) 
		For i = 0 To 39 : GY_FreeGadget(AttributeIncrease(i)) : GY_FreeGadget(AttributeDecrease(i))  : GY_FreeGadget(AttributeLabels(i)) : Next
		
						GY_FreeGadget(RemainingLabel)
						;Character Options
						;FreeEntity(GenderLabel)
						;GY_FreeGadget(BNextGender)
						For i = 0 To 9 : GY_FreeGadget(LDescription(i)) : Next
						Return 1
					EndIf
				EndIf
			EndIf
			
			;[234]
			Animate(Preview\EN, 0)
			PlayAnimation(Preview, 1, 0.003, Anim_Idle)
		EndIf

		; Point spends
		If AttributeAssignment > 0
			For i = 0 To 39
				If GY_ButtonHit(AttributeDecrease(i)) = True
					Att = GY_GadgetData$(AttributeLabels(i))
					If PointSpends(Att) > 0
						PointsToSpend = PointsToSpend + 1
						PointSpends(Att) = PointSpends(Att) - 1
						GY_UpdateLabel(AttributeLabels(i), Preview\Attributes\Value[Att] + PointSpends(Att))
						GY_UpdateLabel(RemainingLabel, LanguageString$(LS_AttributePoints) + " " + PointsToSpend)
					EndIf
				ElseIf GY_ButtonHit(AttributeIncrease(i)) = True
					Att = GY_GadgetData$(AttributeLabels(i))
					If PointsToSpend > 0 And (Preview\Attributes\Value[Att] + PointSpends(Att)) < Preview\Attributes\Maximum[Att]
						PointsToSpend = PointsToSpend - 1
						PointSpends(Att) = PointSpends(Att) + 1
						GY_UpdateLabel(AttributeLabels(i), Preview\Attributes\Value[Att] + PointSpends(Att))
						GY_UpdateLabel(RemainingLabel, LanguageString$(LS_AttributePoints) + " " + PointsToSpend)
					EndIf
				EndIf
			Next
		EndIf

		; Changed character race [WIP] Cysis145
		If GY_ComboBoxItem$(CRace) <> Preview\Actor\Race$
			SafeFreeActorInstance(Preview)
			PointsToSpend = AttributeAssignment
			For i = 0 To 39 : PointSpends(i) = 0 : Next
			ChosenRace$ = Upper$(GY_ComboBoxItem$(CRace))
			For A.Actor = Each Actor
				If Upper$(A\Race$) = ChosenRace$ Then Chosen.Actor = A : Exit
			Next
			Preview.ActorInstance = CreateActorInstance(Chosen)
			Result = LoadActorInstance3D(Preview)
			If Result = False Then RuntimeError("Could not load actor mesh for " + Chosen\Race$ + "!")
			;PlayAnimation(Preview, 1, 0.003, Anim_Idle)
			PositionEntity Preview\CollisionEN, 30, -(35.0 + EntityY#(Preview\EN, True)), 100
			;If Preview\ShadowEN <> 0 Then HideEntity(Preview\ShadowEN) [###]
			If Preview\NametagEN <> 0 Then HideEntity(Preview\NametagEN)
			SetUpPreview(Preview\Actor)
			GY_UpdateLabel(RemainingLabel, LanguageString$(LS_AttributePoints) + " " + PointsToSpend)
			GY_LockGadget(BNextFace, Not ActorHasFace(Preview\Actor, Preview\Gender + 1)) : GY_LockGadget(BPrevFace, Not ActorHasFace(Preview\Actor, Preview\Gender + 1))
			GY_LockGadget(BNextHair, Not ActorHasHair(Preview\Actor, Preview\Gender + 1)) : GY_LockGadget(BPrevHair, Not ActorHasHair(Preview\Actor, Preview\Gender + 1))
			GY_LockGadget(BNextBeard, Not ActorHasBeard(Preview\Actor)) : GY_LockGadget(BPrevBeard, Not ActorHasBeard(Preview\Actor))
			GY_LockGadget(BNextGender, Preview\Actor\Genders) : GY_LockGadget(BPrevGender, Preview\Actor\Genders)
		EndIf

		; Next/Previous class
		If GY_ButtonHit(BNextClass) = True
			Gender = Preview\Gender
			A.Actor = Preview\Actor
			Repeat
				A = After A
				If A = Null Then A = First Actor
				If Upper$(A\Race$) = Upper$(Preview\Actor\Race$) And A\Playable = True
					SafeFreeActorInstance(Preview)
					Preview.ActorInstance = CreateActorInstance(A)
					If (Gender = 0 And A\Genders <> 2) Or (Gender = 1 And A\Genders <> 1 And A\Genders <> 3)
						Preview\Gender = Gender
					EndIf
					Result = LoadActorInstance3D(Preview)
					If Result = False Then RuntimeError("Could not load actor mesh for " + A\Race$ + "!")
					PlayAnimation(Preview, 1, 0.003, Anim_Idle)
					PositionEntity Preview\CollisionEN, 30, -(35.0 + EntityY#(Preview\EN, True)), 100
					;If Preview\ShadowEN <> 0 Then HideEntity(Preview\ShadowEN) [###]
					If Preview\NametagEN <> 0 Then HideEntity(Preview\NametagEN)
					PointsToSpend = AttributeAssignment
					For i = 0 To 39 : PointSpends(i) = 0 : Next
					SetUpPreview(Preview\Actor)
					GY_UpdateLabel(RemainingLabel, LanguageString$(LS_AttributePoints) + " " + PointsToSpend)
					AllowedFace = ActorHasFace(Preview\Actor, Preview\Gender + 1)
					GY_LockGadget(BNextFace, Not AllowedFace) : GY_LockGadget(BPrevFace, Not AllowedFace)
					AllowedHair = ActorHasHair(Preview\Actor, Preview\Gender + 1)
					GY_LockGadget(BNextHair, Not AllowedHair) : GY_LockGadget(BPrevHair, Not AllowedHair)
					GY_LockGadget(BNextBeard, Not ActorHasBeard(Preview\Actor))
					GY_LockGadget(BPrevBeard, Not ActorHasBeard(Preview\Actor))
					GY_LockGadget(BNextGender, Preview\Actor\Genders) : GY_LockGadget(BPrevGender, Preview\Actor\Genders)
					Exit
				EndIf
			Forever
		ElseIf GY_ButtonHit(BPrevClass) = True
			Gender = Preview\Gender
			A.Actor = Preview\Actor
			Repeat
				A = Before A
				If A = Null Then A = Last Actor
				If Upper$(A\Race$) = Upper$(Preview\Actor\Race$) And A\Playable = True
					SafeFreeActorInstance(Preview)
					Preview.ActorInstance = CreateActorInstance(A)
					If (Gender = 0 And A\Genders <> 2) Or (Gender = 1 And A\Genders <> 1 And A\Genders <> 3)
						Preview\Gender = Gender
					EndIf
					Result = LoadActorInstance3D(Preview)
					If Result = False Then RuntimeError("Could not load actor mesh for " + A\Race$ + "!")
					PlayAnimation(Preview, 1, 0.003, Anim_Idle)
					PositionEntity Preview\CollisionEN, 30, -(35.0 + EntityY#(Preview\EN, True)), 100
					;If Preview\ShadowEN <> 0 Then HideEntity(Preview\ShadowEN) [###]
					If Preview\NametagEN <> 0 Then HideEntity(Preview\NametagEN)
					PointsToSpend = AttributeAssignment
					For i = 0 To 39 : PointSpends(i) = 0 : Next
					SetUpPreview(Preview\Actor)
					GY_UpdateLabel(RemainingLabel, LanguageString$(LS_AttributePoints) + " " + PointsToSpend)
					AllowedFace = ActorHasFace(Preview\Actor, Preview\Gender + 1)
					GY_LockGadget(BNextFace, Not AllowedFace) : GY_LockGadget(BPrevFace, Not AllowedFace)
					AllowedHair = ActorHasHair(Preview\Actor, Preview\Gender + 1)
					GY_LockGadget(BNextHair, Not AllowedHair) : GY_LockGadget(BPrevHair, Not AllowedHair)
					GY_LockGadget(BNextBeard, Not ActorHasBeard(Preview\Actor))
					GY_LockGadget(BPrevBeard, Not ActorHasBeard(Preview\Actor))
					GY_LockGadget(BNextGender, Preview\Actor\Genders) : GY_LockGadget(BPrevGender, Preview\Actor\Genders)
					Exit
				EndIf
			Forever
		EndIf

		; Next/Previous gender
		If GY_ButtonHit(BNextGender) Or GY_ButtonHit(BPrevGender)
			If Preview\Actor\Genders = 0
				Preview\Gender = Not Preview\Gender
				Preview\BodyTex = 0
				FreeActorInstance3D(Preview)
				LoadActorInstance3D(Preview)
				PlayAnimation(Preview, 1, 0.003, Anim_Idle)
				PositionEntity Preview\CollisionEN, 30, -(35.0 + EntityY#(Preview\EN, True)), 100
				;If Preview\ShadowEN <> 0 Then HideEntity(Preview\ShadowEN) [###]
				If Preview\NametagEN <> 0 Then HideEntity(Preview\NametagEN)
			EndIf
		EndIf

		; Next/Previous beard
		If GY_ButtonHit(BNextBeard)
			Repeat
				Preview\Beard = Preview\Beard + 1
				If Preview\Beard > 4
					Preview\Beard = 0
					Exit
				EndIf
				NextMesh = Preview\Actor\BeardIDs[Preview\Beard]
			Until NextMesh <> 65535
			FreeActorInstance3D(Preview)
			LoadActorInstance3D(Preview)
			PlayAnimation(Preview, 1, 0.003, Anim_Idle)
			PositionEntity Preview\CollisionEN, 30, -(35.0 + EntityY#(Preview\EN, True)), 100
			;If Preview\ShadowEN <> 0 Then HideEntity(Preview\ShadowEN) [###]
			If Preview\NametagEN <> 0 Then HideEntity(Preview\NametagEN)
		ElseIf GY_ButtonHit(BPrevBeard)
			Repeat
				Preview\Beard = Preview\Beard - 1
				If Preview\Beard < 0
					Preview\Beard = 4
				ElseIf Preview\Beard = 0
					Exit
				EndIf
				NextMesh = Preview\Actor\BeardIDs[Preview\Beard]
			Until NextMesh <> 65535
			FreeActorInstance3D(Preview)
			LoadActorInstance3D(Preview)
			PlayAnimation(Preview, 1, 0.003, Anim_Idle)
			PositionEntity Preview\CollisionEN, 30, -(35.0 + EntityY#(Preview\EN, True)), 100
			;If Preview\ShadowEN <> 0 Then HideEntity(Preview\ShadowEN) [###]
			If Preview\NametagEN <> 0 Then HideEntity(Preview\NametagEN)
		EndIf

		; Next/Previous hair
		If GY_ButtonHit(BNextHair)
			Repeat
				Preview\Hair = Preview\Hair + 1
				If Preview\Hair > 4
					Preview\Hair = 0
					Exit
				EndIf
				If Preview\Gender = 0
					NextMesh = Preview\Actor\MaleHairIDs[Preview\Hair]
				Else
					NextMesh = Preview\Actor\FemaleHairIDs[Preview\Hair]
				EndIf
			Until NextMesh <> 65535
			FreeActorInstance3D(Preview)
			LoadActorInstance3D(Preview)
			PlayAnimation(Preview, 1, 0.003, Anim_Idle)
			PositionEntity Preview\CollisionEN, 30, -(35.0 + EntityY#(Preview\EN, True)), 100
			;If Preview\ShadowEN <> 0 Then HideEntity(Preview\ShadowEN) [###]
			If Preview\NametagEN <> 0 Then HideEntity(Preview\NametagEN)
		ElseIf GY_ButtonHit(BPrevHair)
			Repeat
				Preview\Hair = Preview\Hair - 1
				If Preview\Hair < 0
					Preview\Hair = 4
				ElseIf Preview\Hair = 0
					Exit
				EndIf
				If Preview\Gender = 0
					NextMesh = Preview\Actor\MaleHairIDs[Preview\Hair]
				Else
					NextMesh = Preview\Actor\FemaleHairIDs[Preview\Hair]
				EndIf
			Until NextMesh <> 65535
			FreeActorInstance3D(Preview)
			LoadActorInstance3D(Preview)
			PlayAnimation(Preview, 1, 0.003, Anim_Idle)
			PositionEntity Preview\CollisionEN, 30, -(35.0 + EntityY#(Preview\EN, True)), 100
			;If Preview\ShadowEN <> 0 Then HideEntity(Preview\ShadowEN) [###]
			If Preview\NametagEN <> 0 Then HideEntity(Preview\NametagEN)
		EndIf

		; Next/Previous body texture
		If GY_ButtonHit(BNextBody) = True
			Repeat
				Preview\BodyTex = Preview\BodyTex + 1
				If Preview\BodyTex > 4 Then Preview\BodyTex = 0
				If Preview\Gender = 0
					NextTex = Preview\Actor\MaleBodyIDs[Preview\BodyTex]
				Else
					NextTex = Preview\Actor\FemaleBodyIDs[Preview\BodyTex]
				EndIf
			Until NextTex <> 65535
			FreeActorInstance3D(Preview)
			LoadActorInstance3D(Preview)
			PlayAnimation(Preview, 1, 0.003, Anim_Idle)
			PositionEntity Preview\CollisionEN, 30, -(35.0 + EntityY#(Preview\EN, True)), 100
			;If Preview\ShadowEN <> 0 Then HideEntity(Preview\ShadowEN) [###]
			If Preview\NametagEN <> 0 Then HideEntity(Preview\NametagEN)
		ElseIf GY_ButtonHit(BPrevBody) = True
			Repeat
				Preview\BodyTex = Preview\BodyTex - 1
				If Preview\BodyTex < 0 Then Preview\BodyTex = 4
				If Preview\Gender = 0
					NextTex = Preview\Actor\MaleBodyIDs[Preview\BodyTex]
				Else
					NextTex = Preview\Actor\FemaleBodyIDs[Preview\BodyTex]
				EndIf
			Until NextTex <> 65535
			FreeActorInstance3D(Preview)
			LoadActorInstance3D(Preview)
			PlayAnimation(Preview, 1, 0.003, Anim_Idle)
			PositionEntity Preview\CollisionEN, 30, -(35.0 + EntityY#(Preview\EN, True)), 100
			;If Preview\ShadowEN <> 0 Then HideEntity(Preview\ShadowEN) [###]
			If Preview\NametagEN <> 0 Then HideEntity(Preview\NametagEN)
		EndIf

		; Next/Previous face texture
		If GY_ButtonHit(BNextFace) = True
			Repeat
				Preview\FaceTex = Preview\FaceTex + 1
				If Preview\FaceTex > 4 Then Preview\FaceTex = 0
				If Preview\Gender = 0
					NextTex = Preview\Actor\MaleFaceIDs[Preview\FaceTex]
				Else
					NextTex = Preview\Actor\FemaleFaceIDs[Preview\FaceTex]
				EndIf
			Until NextTex <> 65535
			FreeActorInstance3D(Preview)
			LoadActorInstance3D(Preview)
			PlayAnimation(Preview, 1, 0.003, Anim_Idle)
			PositionEntity Preview\CollisionEN, 30, -(35.0 + EntityY#(Preview\EN, True)), 100
			;If Preview\ShadowEN <> 0 Then HideEntity(Preview\ShadowEN) [###]
			If Preview\NametagEN <> 0 Then HideEntity(Preview\NametagEN)
		ElseIf GY_ButtonHit(BPrevFace) = True
			Repeat
				Preview\FaceTex = Preview\FaceTex - 1
				If Preview\FaceTex < 0 Then Preview\FaceTex = 4
				If Preview\Gender = 0
					NextTex = Preview\Actor\MaleFaceIDs[Preview\FaceTex]
				Else
					NextTex = Preview\Actor\FemaleFaceIDs[Preview\FaceTex]
				EndIf
			Until NextTex <> 65535
			FreeActorInstance3D(Preview)
			LoadActorInstance3D(Preview)
			PlayAnimation(Preview, 1, 0.003, Anim_Idle)
			PositionEntity Preview\CollisionEN, 30, -(35.0 + EntityY#(Preview\EN, True)), 100
			;If Preview\ShadowEN <> 0 Then HideEntity(Preview\ShadowEN) [###]
			If Preview\NametagEN <> 0 Then HideEntity(Preview\NametagEN)
		EndIf

		; Camera
      If GY_ButtonDown(BRight) 
          CamAngle# = CamAngle# + 0.80
          ;CamAngle# = CamAngle# + 2.0 
       ElseIf GY_ButtonDown(BLeft) 
          CamAngle# = CamAngle# - 0.80 
          ;CamAngle# = CamAngle# - 2.0 
       EndIf 
       PositionEntity GPP, 30, 0, 100 
       RotateEntity GPP, 0.0, CamAngle#, 0.0 
       TFormPoint 0.0, 30.0, -110.0, GPP, 0 
       ;TFormPoint -20.0, 5.0, -90.0, GPP, 0 
       PositionEntity Cam, TFormedX#(), TFormedY#(), TFormedZ#() 
       PointEntity Cam, GPP 
       MoveEntity Cam, 10.0, 0.0, 0.0 

       ; Check connection is still alive 
       For M.RCE_Message = Each RCE_Message 
          Select M\MessageType 
             Case RN_Disconnected, RN_HostHasLeft 
                RuntimeError(LanguageString$(LS_LostConnection)) 
          End Select 
          Delete M 
       Next

		; Update everything
		RCE_Update()
		RCE_CreateMessages()
		
		GY_Update()
		RP_Update()
		UpdateWorld(delta#)
		RenderWorld()
		Flip(VSync)
	Forever

End Function

; Sets up the character preview
Function SetUpPreview(Preview.Actor)

	; Attributes
	Count = 0
	For i = 0 To 39
		If AttributeNames$(i) <> "" And AttributeIsSkill(i) = False And AttributeHidden(i) = False
			GY_UpdateLabel(AttributeLabels(Count), Preview\Attributes\Value[i] + PointSpends(i))
			Count = Count + 1
		EndIf
	Next

	; Word wrap {{}}
	D$ = Preview\Race$ + " " + Preview\Class$ + ": " + Preview\Description$
	Gad.GY_Gadget = Object.GY_Gadget(LDescription(0))
	L = 0
	While GY_TextWidth#(Gad\EN, D$) > 0.18
		Found = False
		For i = Len(D$) To 1 Step -1
			If Mid$(D$, i, 1) = " "
				If GY_TextWidth#(Gad\EN, Left$(D$, i - 1)) <= 0.18
					GY_UpdateLabel(LDescription(L), Left$(D$, i - 1))
					D$ = Mid$(D$, i + 1)
					L = L + 1
					Found = True
					Exit
				EndIf
			EndIf
		Next
		If Found = False
			For i = Len(D$) To 1 Step -1
				If GY_TextWidth#(Gad\EN, Left$(D$, i - 1)) <= 0.18
					GY_UpdateLabel(LDescription(L), Left$(D$, i - 1))
					D$ = Mid$(D$, i)
					L = L + 1
					Exit
				EndIf
			Next
		EndIf
		If L = 9 Then Exit
	Wend
	GY_UpdateLabel(LDescription(L), D$)
	For i = L + 1 To 9
		GY_UpdateLabel(LDescription(i), "")
	Next

End Function

; Displays the optional EULA
Function EULAScreen()

	; Do not display if .txt file is empty
	If FileType("Data\Game Data\EULA.txt") <> 1 Or FileSize("Data\Game Data\EULA.txt") = 0
		Return
	EndIf
	
	; Background	
	;If FileType("Data\Meshes\Character Set\E_Set.eb3d") = 1
	;	Set = LoadMesh("Data\Meshes\Character Set\E_Set.eb3d")
	;	If Set = 0 Then RuntimeError("Could not load Data\Meshes\Character Set\E_Set.eb3d!")
	;Else
		Set = LoadMesh("Data\Meshes\Character Set\Set.b3d")
		If Set = 0 Then RuntimeError("Could not load Data\Meshes\Character Set\Set.b3d!")
	;EndIf
;Possition set
	PositionEntity Set, -210, -35, -145
	ScaleEntity Set, 30, 30, 30
	
		
	Background = GY_CreateQuad(Cam)
	PositionEntity(Background, -7.5, 5.5, 10.0)
	ScaleEntity(Background, 15, 12.0, 1.0)
	EntityOrder(Background, -1)
	Tex = LoadTexture("Data\Textures\Menu\EULA.png")
	EntityTexture(Background, Tex)
	FreeTexture(Tex)



	; EULA text
	SEULA = GY_CreateScrollBar(0, 0.817, 0.2, 0.023, 0.585, 0.5)
	For i = 0 To 25
		LEULA(i) = GY_CreateLabel(0, 0.16, 0.2 + (Float#(i) * 0.025), String$("*", 110), 255, 255, 255)
	Next
	CurrentLine = 0
	F = ReadFile("Data\Game Data\EULA.txt")
		While Eof(F) = False
			ThisLine$ = ReadLine$(F)
			Gad.GY_Gadget = Object.GY_Gadget(LEULA(0))
			While GY_TextWidth#(Gad\EN, ThisLine$) > 1.0
				Found = False
				For i = Len(ThisLine$) To 1 Step -1
					If Mid$(ThisLine$, i, 1) = " "
						If GY_TextWidth#(Gad\EN, Left$(ThisLine$, i - 1)) <= 1.0
							EULAText$(CurrentLine) = Left$(ThisLine$, i - 1)
							ThisLine$ = Mid$(ThisLine$, i + 1)
							CurrentLine = CurrentLine + 1
							Found = True
							Exit
						EndIf
					EndIf
				Next
				If Found = False
					For i = Len(ThisLine$) To 1 Step -1
						If GY_TextWidth#(Gad\EN, Left$(ThisLine$, i - 1)) <= 1.0
							EULAText$(CurrentLine) = Left$(ThisLine$, i - 1)
							ThisLine$ = Mid$(ThisLine$, i)
							CurrentLine = CurrentLine + 1
							Exit
						EndIf
					Next
				EndIf
				If CurrentLine = 999 Then Exit
			Wend
			EULAText$(CurrentLine) = ThisLine$
			CurrentLine = CurrentLine + 1
		Wend
	CloseFile(F)
	MaxEULALine = CurrentLine
	CurrentEULALine = 0
	If MaxEULALine > 25
		GY_SetScrollBarInterval(SEULA, 1.0 / Float#(MaxEULALine - 25))
	Else
		GY_GadgetAlpha(SEULA, 0.0)
		GY_LockGadget(SEULA, True)
	EndIf
	For i = 0 To 25
		GY_UpdateLabel(LEULA(i), EULAText$(i))
	Next
	BAgree = GY_CreateCustomButton(0, 0.67, 0.875, 0.1, 0.04, LoadButtonU("Accept"), LoadButtonD("Accept"), LoadButtonH("Accept"))
	BDisagree = GY_CreateCustomButton(0, 0.55, 0.875, 0.1, 0.04, LoadButtonU("Decline"), LoadButtonD("Decline"), LoadButtonH("Decline"))
	
	; Wait for user choice
	Repeat
		; Buttons pressed or escape hit
		If GY_ButtonHit(BDisagree) Or KeyHit(1)
			End
		ElseIf GY_ButtonHit(BAgree)
			Exit
		EndIf

		; Scrollbar moved
		If MaxEULALine > 22
			Pos# = GY_ScrollBarValue#(SEULA)
			TheLine = Pos# * Float#(MaxEULALine - 22)
			If TheLine <> CurrentEULALine
				CurrentEULALine = TheLine
				For i = 0 To 22
					GY_UpdateLabel(LEULA(i), EULAText$(i + CurrentEULALine))
				Next
			EndIf
		EndIf

		; Update everything
		GY_Update()
		UpdateWorld()
		RenderWorld()
		Flip(VSync)
	Forever

	; Done
	FreeEntity(Background)
	GY_FreeGadget(BAgree)
	GY_FreeGadget(BDisagree)
	GY_FreeGadget(SEULA)
	For i = 0 To 28
		GY_FreeGadget(LEULA(i))
	Next
	Dim EULAText$(0)

End Function

; Game options dialog
Function GameOptionsMenu()

	;#################################################################################################
	;	Remove background by clan_fd on Mar 19, 2014
	;; Background
	;Background = GY_CreateQuad(Cam)
	;PositionEntity(Background, -10.0, 7.5, 10.0)
	;ScaleEntity(Background, 20.0, 15.0, 1.0)
	;EntityOrder(Background, 1)
	;Tex = LoadTexture("Data\Textures\Menu\Menu.png")
	;If Tex = 0 Then RuntimeError("File not found: Data\Textures\Menu\Menu.png!")
	;EntityTexture(Background, Tex)
	;FreeTexture(Tex)
	;	End edit
	;#################################################################################################

	;#################################################################################################
	;	Changed game options menu by clan_fd on Mar 19, 2014
	WOptions = GY_CreateWindow(LanguageString$(LS_GameOptions), 0.2, 0.15, 0.6, 0.6, True, True, False)
	BOptionsDone = GY_CreateCustomButton(WOptions, 0.696, 0.9, 0.258, 0.072, LoadButtonU("MenuDone"), LoadButtonD("MenuDone"), LoadButtonH("MenuDone"))
	
	BGFXOpts = GY_CreateCustomButton(WOptions, 0.0, 0.0, 0.258, 0.05, LoadButtonU("Graphics"), LoadButtonD("Graphics"), LoadButtonH("Graphics"))
	BCtrlOpts = GY_CreateCustomButton(WOptions, 0.33, 0.0, 0.258, 0.05, LoadButtonU("Control"), LoadButtonD("Control"), LoadButtonH("Control"))
	BMiscOpts = GY_CreateCustomButton(WOptions, 0.66, 0.0, 0.258, 0.05, LoadButtonU("Other"), LoadButtonD("Other"), LoadButtonH("Other"))
	;	End edit
	;#################################################################################################
	
	; Graphics options window 
	;##################################################################################################
	;	Commented out separate graphics option window by clan_fd on Mar 19, 2014
	;WGraphics = GY_CreateWindow(LanguageString$(LS_GraphicsOptions), 0.2, 0.3, 0.6, 0.4, True, True, False) 
	;	End edit
	;##################################################################################################
	    
	;##################################################################################################
	;	Changed graphics menu options by clan_fd on Mar 19, 2014
	;GY_CreateLabel(WGraphics, 0.02, 0.05, LanguageString$(LS_SelectResolution)) 
    ;LResolution = GY_CreateListBox(WGraphics, 0.02, 0.15, 0.4, 0.75) 
    ;G.GY_Gadget = Object.GY_Gadget(LResolution) 
    ;L.GY_ListBox = Object.GY_ListBox(G\TypeHandle) 
    ;For i = 1 To CountGfxModes3D() 
	;      Width = GfxModeWidth(i) 
	;      Height = GfxModeHeight(i) 
	;      If ((Width * 3) = (Height * 4)) 
	;         ModeName$ = Str$(Width) + " x " + Str$(Height) 
	;         Found = False 
	;         For B.GY_ListItem = Each GY_ListItem 
	;            If B\Box = L 
	;               If B\Dat$ = ModeName$ 
	;                  Found = True 
	;                  Exit 
	;               EndIf 
	;            EndIf 
	;         Next 
	;         If Found = False Then GY_AddListBoxItem(LResolution, ModeName$) 
	;      ElseIf ((Width * 9) = (Height * 16)) 
	;         ModeName$ = Str$(Width) + " x " + Str$(Height) 
	;         Found = False 
	;         For B.GY_ListItem = Each GY_ListItem 
	;            If B\Box = L 
	;               If B\Dat$ = ModeName$ 
	;                  Found = True 
	;                  Exit 
	;               EndIf 
	;            EndIf 
    ;     		Next 
    ;    	If Found = False Then GY_AddListBoxItem(LResolution, ModeName$) 
    ;	EndIf 
	;Next  
	;GY_CreateLabel(WGraphics, 0.5, 0.05, LanguageString$(LS_ColourDepth))
	;CDepth = GY_CreateComboBox(WGraphics, 0.5, 0.15, 0.4, 0.5, LanguageString$(LS_SelectColourDepth), 255, 255, 255, 14, True)
	;GY_AddComboBoxItem(CDepth, "16 bit")
	;GY_AddComboBoxItem(CDepth, "32 bit")
	;GY_AddComboBoxItem(CDepth, LanguageString$(LS_BestAvailable))
	;BAntiAlias = GY_CreateCheckBox(WGraphics, 0.5, 0.27, LanguageString$(LS_EnableAA))
	;BEnableGrass = GY_CreateCheckBox(WGraphics, 0.5, 0.39,LanguageString$(LS_EnableGrass))
	;BFullScreen = GY_CreateCheckBox(WGraphics, 0.5, 0.51,"FullScreen")
	;BVSyncOption = GY_CreateCheckBox(WGraphics, 0.5, 0.63,"Vsync On")
	
	;GY_CreateLabel(WGraphics, 0.5, 0.75, LanguageString$(LS_AnisotropyLevel))
	;CAnisotropy = GY_CreateComboBox(WGraphics, 0.7, 0.75, 0.2, 0.5, "", 255, 255, 255, 8, True)
	;GY_AddComboBoxItem(CAnisotropy, LanguageString$(LS_Disabled))
	;GY_AddComboBoxItem(CAnisotropy, "x4")
	;GY_AddComboBoxItem(CAnisotropy, "x8")
	;GY_AddComboBoxItem(CAnisotropy, "x16")
	;BGFXDone = GY_CreateCustomButton(WGraphics, 0.696, 0.866, 0.258, 0.108, LoadButtonU("MenuDone"), LoadButtonD("MenuDone"), LoadButtonH("MenuDone"))
	LRes = GY_CreateLabel(WOptions, 0.02, 0.05, LanguageString$(LS_SelectResolution)) 
    LResolution = GY_CreateListBox(WOptions, 0.02, 0.15, 0.4, 0.75, True) 
    G.GY_Gadget = Object.GY_Gadget(LResolution) 
    L.GY_ListBox = Object.GY_ListBox(G\TypeHandle) 
    For i = 1 To CountGfxModes3D() 
	      Width = GfxModeWidth(i) 
	      Height = GfxModeHeight(i) 
	
	;Widescreen rebuild [+]
	If (Width * 9) = (Height * 16)  Or  (Width * 3) = (Height * 4)  
			ModeName$ = Str$(Width) + " x " + Str$(Height) 
        	Found = False 
          	For B.GY_ListItem = Each GY_ListItem 
             If B\Box = L 
                If B\Dat$ = ModeName$ 
                   Found = True 
                   Exit 
                EndIf 
             EndIf 
         	Next 
          	If Found = False Then GY_AddListBoxItem(LResolution, ModeName$) 
		EndIf

	
;	      If ((Width * 3) = (Height * 4)) 
;	         ModeName$ = Str$(Width) + " x " + Str$(Height) 
;	         Found = False 
;	         For B.GY_ListItem = Each GY_ListItem 
;	            If B\Box = L 
;	               If B\Dat$ = ModeName$ 
;	                  Found = True 
;	                  Exit 
;	               EndIf 
;	            EndIf 
;	         Next 
;	         If Found = False Then GY_AddListBoxItem(LResolution, ModeName$) 
;	      ElseIf ((Width * 9) = (Height * 16)) 
;	         ModeName$ = Str$(Width) + " x " + Str$(Height) 
;	         Found = False 
;	         For B.GY_ListItem = Each GY_ListItem 
;	            If B\Box = L 
;	               If B\Dat$ = ModeName$ 
;	                  Found = True 
;	                  Exit 
;	               EndIf 
;	            EndIf 
 ;        		Next 
  ;      	If Found = False Then GY_AddListBoxItem(LResolution, ModeName$) 
   ; 	EndIf 

	Next  
	LColorDepth = GY_CreateLabel(WOptions, 0.5, 0.05, LanguageString$(LS_ColourDepth))
	CDepth = GY_CreateComboBox(WOptions, 0.5, 0.15, 0.4, 0.5, LanguageString$(LS_SelectColourDepth), 255, 255, 255, 14, True)
	GY_AddComboBoxItem(CDepth, "16 bit")
	GY_AddComboBoxItem(CDepth, "32 bit")
	GY_AddComboBoxItem(CDepth, LanguageString$(LS_BestAvailable))
	BAntiAlias = GY_CreateCheckBox(WOptions, 0.5, 0.27, LanguageString$(LS_EnableAA))
	;BEnableGrass = GY_CreateCheckBox(WOptions, 0.5, 0.39,LanguageString$(LS_EnableGrass))
	BFullScreen = GY_CreateCheckBox(WOptions, 0.5, 0.33,"FullScreen")
	BVSyncOption = GY_CreateCheckBox(WOptions, 0.5, 0.39,"Vsync On")
	;Adding fastext to the options menu Cysis145
	BBloom = GY_CreateCheckBox(WOptions, 0.5, 0.45, "Bloom")
	BRays = GY_CreateCheckBox(WOptions, 0.5, 0.51, "Light Rays")
	BWater = GY_CreateCheckBox(WOptions, 0.5, 0.57, "Animated Water")
	;BDOF = GY_CreateCheckBox(WOptions, 0.5, 0.67, "Depth of Field")
	
	LAnisotropy = GY_CreateLabel(WOptions, 0.5, 0.77, LanguageString$(LS_AnisotropyLevel))
	CAnisotropy = GY_CreateComboBox(WOptions, 0.7, 0.77, 0.2, 0.5, "", 255, 255, 255, 8, True)
	GY_AddComboBoxItem(CAnisotropy, LanguageString$(LS_Disabled))
	GY_AddComboBoxItem(CAnisotropy, "x4")
	GY_AddComboBoxItem(CAnisotropy, "x8")
	GY_AddComboBoxItem(CAnisotropy, "x16")
	
	;	Graphics options are initially visible when options menu is open
	GraphicsVisible = True
	
	;	End edit
	;###############################################################################################################
	; Set up initial graphics options
	F = ReadFile("Data\Options.dat")
	If F = 0 Then RuntimeError("Could not open Data\Options.dat!")
		Width = ReadShort(F)
		Height = ReadShort(F)
		Depth = ReadByte(F)
		AA = ReadByte(F)
		DefaultVolume# = ReadFloat#(F)
		GrassEnabled = ReadByte(F)
		Anisotropy = ReadByte(F)
		FullScreen = ReadByte(F)
		VSync = ReadByte(F)
		;Adding Fastext to options window Cysis145
		Bloom =  ReadByte(F)
		Rays = ReadByte(F)
		AWater = ReadByte(F)
		DOF = ReadByte(F)
		
	CloseFile(F)
	GY_UpdateListBox(LResolution, "640 x 480")
	GY_UpdateListBox(LResolution, Str$(Width) + " x " + Str$(Height))
	If Depth = 0
		GY_UpdateComboBox(CDepth, LanguageString$(LS_BestAvailable))
	ElseIf Depth = 16
		GY_UpdateComboBox(CDepth, "16 bit")
	ElseIf Depth = 32
		GY_UpdateComboBox(CDepth, "32 bit")
	EndIf
	GY_UpdateCheckBox(BAntiAlias, AA)
	GY_UpdateCheckBox(BEnableGrass, GrassEnabled)
	GY_UpdateCheckBox(BFullScreen,Fullscreen)
	GY_UpdateCheckBox(BVSyncOption,VSync)
	
	;Adding FastExt to options Window Cysis145
	GY_UpdateCheckBox(BBloom, Bloom)
	GY_UpdateCheckBox(BRays, Rays)
	GY_UpdateCheckBox(BWater, AWater)
	;GY_UpdateCheckBox(BDOF, DOF)
	
	CurrentRes$ = GY_ListBoxItem(LResolution)
	CurrentDepth$ = GY_ComboBoxItem(CDepth)
	If Anisotropy = 0
		GY_UpdateComboBox(CAnisotropy, LanguageString$(LS_Disabled))
	ElseIf Anisotropy = 4
		GY_UpdateComboBox(CAnisotropy, "x4")
	ElseIf Anisotropy = 8
		GY_UpdateComboBox(CAnisotropy, "x8")
	ElseIf Anisotropy = 16
		GY_UpdateComboBox(CAnisotropy, "x16")
	EndIf
	CurrentAnisotropy$ = GY_ComboBoxItem(CAnisotropy)

	;####################################################################################################
	;	Changed miscellaneous options by clan_fd on Mar 19, 2014
	; Misc options window
	;WMisc = GY_CreateWindow(LanguageString$(LS_OtherOptions), 0.2, 0.3, 0.6, 0.4, True, True, False)
	;SVolume = GY_CreateSlider(WMisc, 0.05, 0.1, 0.9, 0.1, LanguageString$(LS_SndVolume), Int(DefaultVolume# * 100.0), 0, 100, False, 0, 0, 255)
	;CurrentVolume = Int(DefaultVolume# * 100.0)
	;BUpdateMusic = GY_CreateCheckBox(WMisc, 0.05, 0.25, LanguageString$(LS_SkipMusic))
	;GY_UpdateCheckBox(BUpdateMusic, 1 - UpdateMusic)
	;BMiscDone = GY_CreateCustomButton(WMisc, 0.696, 0.866, 0.258, 0.108, LoadButtonU("MenuDone"), LoadButtonD("MenuDone"), LoadButtonH("MenuDone"))
	SVolume = GY_CreateSlider(WOptions, 0.05, 0.1, 0.9, 0.1, LanguageString$(LS_SndVolume), Int(DefaultVolume# * 100.0), 0, 100, False, 0, 0, 255)
	CurrentVolume = Int(DefaultVolume# * 100.0)
	BUpdateMusic = GY_CreateCheckBox(WOptions, 0.05, 0.25, LanguageString$(LS_SkipMusic))
	GY_UpdateCheckBox(BUpdateMusic, 1 - UpdateMusic)
	
	;	Initially hidden
	MiscVisible = False
	
	;	End edit
	;####################################################################################################

	;##########################################################################################################################
	;	Changed control options menu by clan_fd on Mar 19, 2014
	;; Control options window
	;WControls = GY_CreateWindow(LanguageString$(LS_ControlOptions), 0.2, 0.15, 0.6, 0.6, True, True, False)
	;LForwardKey     = GY_CreateLabel(WControls, 0.05, 0.02,    LanguageString$(LS_CForward) + " " + ControlName$(Key_Forward) + String$(" ", 30))
	;LBackwardKey    = GY_CreateLabel(WControls, 0.05, 0.06,    LanguageString$(LS_CStop) + " " + ControlName$(Key_Back) + String$(" ", 30))
	;LRightKey       = GY_CreateLabel(WControls, 0.05, 0.10,    LanguageString$(LS_CTurnRight) + " " + ControlName$(Key_TurnRight) + String$(" ", 30))
	;LLeftKey        = GY_CreateLabel(WControls, 0.05, 0.14,    LanguageString$(LS_CTurnLeft) + " " + ControlName$(Key_TurnLeft) + String$(" ", 30))
	;LFlyUpKey       = GY_CreateLabel(WControls, 0.05, 0.18,    LanguageString$(LS_CFlyUp) + " " + ControlName$(Key_FlyUp) + String$(" ", 30))
	;LFlyDownKey     = GY_CreateLabel(WControls, 0.05, 0.22,    LanguageString$(LS_CFlyDown) + " " + ControlName$(Key_FlyDown) + String$(" ", 30))
	;LSelectKey      = GY_CreateLabel(WControls, 0.05, 0.26,    LanguageString$(LS_CSelect) + " " + ControlName$(Key_Select) + String$(" ", 30))
	;LMoveToKey      = GY_CreateLabel(WControls, 0.05, 0.30,    LanguageString$(LS_CMoveTo) + " " + ControlName$(Key_MoveTo) + String$(" ", 30))
	;LTalkToKey      = GY_CreateLabel(WControls, 0.05, 0.34,    LanguageString$(LS_CTalkTo) + " " + ControlName$(Key_TalkTo) + String$(" ", 30))
	;LRunKey         = GY_CreateLabel(WControls, 0.05, 0.38,    LanguageString$(LS_CRun) + " " + ControlName$(Key_Run) + String$(" ", 30))
	;LAlwaysRunKey   = GY_CreateLabel(WControls, 0.05, 0.42,    LanguageString$(LS_CAlwaysRun) + " " + ControlName$(Key_AlwaysRun) + String$(" ", 30))
	;LJumpKey        = GY_CreateLabel(WControls, 0.05, 0.46,    LanguageString$(LS_CJump) + " " + ControlName$(Key_Jump) + String$(" ", 30))
	;LViewKey        = GY_CreateLabel(WControls, 0.05, 0.50,    LanguageString$(LS_CViewMode) + " " + ControlName$(Key_ChangeViewMode) + String$(" ", 30))
	;LCamForwardKey  = GY_CreateLabel(WControls, 0.05, 0.54,    LanguageString$(LS_CZoomIn) + " " + ControlName$(Key_CameraIn) + String$(" ", 30))
	;LCamBackwardKey = GY_CreateLabel(WControls, 0.05, 0.58,    LanguageString$(LS_CZoomOut) + " " + ControlName$(Key_CameraOut) + String$(" ", 30))
	;LAttackKey      = GY_CreateLabel(WControls, 0.05, 0.62,    LanguageString$(LS_CAttackTarget) + " " + ControlName$(Key_Attack) + String$(" ", 30))
	;LCycleTargetKey = GY_CreateLabel(WControls, 0.05, 0.66,    LanguageString$(LS_CCycleTarget) + " " + ControlName$(Key_CycleTarget) + String$(" ", 30))
	;BInvertAxis1    = GY_CreateCheckBox(WControls, 0.05, 0.85, LanguageString$(LS_InvertAxis1))
	;BInvertAxis3    = GY_CreateCheckBox(WControls, 0.05, 0.92, LanguageString$(LS_InvertAxis3))
	;BControlsDone   = GY_CreateCustomButton(WControls, 0.696, 0.9, 0.258, 0.072, LoadButtonU("MenuDone"), LoadButtonD("MenuDone"), LoadButtonH("MenuDone"))
	;SelectedControl = 0
	;If InvertAxis1 = -1 Then GY_UpdateCheckBox(BInvertAxis1, True)
	;If InvertAxis3 = -1 Then GY_UpdateCheckBox(BInvertAxis3, True)
	LForwardKey     = GY_CreateLabel(WOptions, 0.05, 0.02 + 0.04,    LanguageString$(LS_CForward) + " " + ControlName$(Key_Forward) + String$(" ", 30))
	LBackwardKey    = GY_CreateLabel(WOptions, 0.05, 0.06 + 0.04,    LanguageString$(LS_CStop) + " " + ControlName$(Key_Back) + String$(" ", 30))
	LRightKey       = GY_CreateLabel(WOptions, 0.05, 0.10 + 0.04,    LanguageString$(LS_CTurnRight) + " " + ControlName$(Key_TurnRight) + String$(" ", 30))
	LLeftKey        = GY_CreateLabel(WOptions, 0.05, 0.14 + 0.04,    LanguageString$(LS_CTurnLeft) + " " + ControlName$(Key_TurnLeft) + String$(" ", 30))
	LFlyUpKey       = GY_CreateLabel(WOptions, 0.05, 0.18 + 0.04,    LanguageString$(LS_CFlyUp) + " " + ControlName$(Key_FlyUp) + String$(" ", 30))
	LFlyDownKey     = GY_CreateLabel(WOptions, 0.05, 0.22 + 0.04,    LanguageString$(LS_CFlyDown) + " " + ControlName$(Key_FlyDown) + String$(" ", 30))
	LSelectKey      = GY_CreateLabel(WOptions, 0.05, 0.26 + 0.04,    LanguageString$(LS_CSelect) + " " + ControlName$(Key_Select) + String$(" ", 30))
	LMoveToKey      = GY_CreateLabel(WOptions, 0.05, 0.30 + 0.04,    LanguageString$(LS_CMoveTo) + " " + ControlName$(Key_MoveTo) + String$(" ", 30))
	LTalkToKey      = GY_CreateLabel(WOptions, 0.05, 0.34 + 0.04,    LanguageString$(LS_CTalkTo) + " " + ControlName$(Key_TalkTo) + String$(" ", 30))
	LRunKey         = GY_CreateLabel(WOptions, 0.05, 0.38 + 0.04,    LanguageString$(LS_CRun) + " " + ControlName$(Key_Run) + String$(" ", 30))
	LAlwaysRunKey   = GY_CreateLabel(WOptions, 0.05, 0.42 + 0.04,    LanguageString$(LS_CAlwaysRun) + " " + ControlName$(Key_AlwaysRun) + String$(" ", 30))
	LJumpKey        = GY_CreateLabel(WOptions, 0.05, 0.46 + 0.04,    LanguageString$(LS_CJump) + " " + ControlName$(Key_Jump) + String$(" ", 30))
	LViewKey        = GY_CreateLabel(WOptions, 0.05, 0.50 + 0.04,    LanguageString$(LS_CViewMode) + " " + ControlName$(Key_ChangeViewMode) + String$(" ", 30))
	LCamForwardKey  = GY_CreateLabel(WOptions, 0.05, 0.54 + 0.04,    LanguageString$(LS_CZoomIn) + " " + ControlName$(Key_CameraIn) + String$(" ", 30))
	LCamBackwardKey = GY_CreateLabel(WOptions, 0.05, 0.58 + 0.04,    LanguageString$(LS_CZoomOut) + " " + ControlName$(Key_CameraOut) + String$(" ", 30))
	LAttackKey      = GY_CreateLabel(WOptions, 0.05, 0.62 + 0.04,    LanguageString$(LS_CAttackTarget) + " " + ControlName$(Key_Attack) + String$(" ", 30))
	LCycleTargetKey = GY_CreateLabel(WOptions, 0.05, 0.66 + 0.04,    LanguageString$(LS_CCycleTarget) + " " + ControlName$(Key_CycleTarget) + String$(" ", 30))
	BInvertAxis1    = GY_CreateCheckBox(WOptions, 0.05, 0.85, LanguageString$(LS_InvertAxis1))
	BInvertAxis3    = GY_CreateCheckBox(WOptions, 0.05, 0.92, LanguageString$(LS_InvertAxis3))
	SelectedControl = 0
	If InvertAxis1 = -1 Then GY_UpdateCheckBox(BInvertAxis1, True)
	If InvertAxis3 = -1 Then GY_UpdateCheckBox(BInvertAxis3, True)
	;	Initially hidden
	ControlsVisible = False
	;	End edit
	;#########################################################################################################################

	;#########################################################################################################################
	;	Commented out by clan_fd on Mar 19, 2014
	;; Create options menu buttons
	;BStart = GY_CreateCustomButton(0, 0.371, 0.296, 0.258, 0.043, LoadButtonU("Start"), LoadButtonD("Start"), LoadButtonH("Start"))
	;BGFXOpts = GY_CreateCustomButton(0, 0.371, 0.396, 0.258, 0.043, LoadButtonU("Graphics"), LoadButtonD("Graphics"), LoadButtonH("Graphics"))
	;BCtrlOpts = GY_CreateCustomButton(0, 0.371, 0.446, 0.258, 0.043, LoadButtonU("Control"), LoadButtonD("Control"), LoadButtonH("Control"))
	;BMiscOpts = GY_CreateCustomButton(0, 0.371, 0.496, 0.258, 0.043, LoadButtonU("Other"), LoadButtonD("Other"), LoadButtonH("Other"))
	;BQuit = GY_CreateCustomButton(0, 0.371, 0.596, 0.258, 0.043, LoadButtonU("Quit"), LoadButtonD("Quit"), LoadButtonH("Quit"))
	;GY_DropGadget(BStart) : GY_DropGadget(BGFXOpts) : GY_DropGadget(BCtrlOpts) : GY_DropGadget(BMiscOpts) : GY_DropGadget(BQuit)

	;; Hide all windows
	;GY_GadgetAlpha(WGraphics, 0.0, True) : GY_LockGadget(WGraphics) : GraphicsVisible = False
	;GY_GadgetAlpha(WSound, 0.0, True) : GY_LockGadget(WSound) : SoundVisible = False
	;GY_GadgetAlpha(WMisc, 0.0, True) : GY_LockGadget(WMisc) : MiscVisible = False
	;GY_GadgetAlpha(WControls, 0.0, True) : GY_LockGadget(WControls) : ControlsVisible = False
	;	End edit
	;#########################################################################################################################

	;########################################################################################
	;	Initialize options window by clan_fd on Mar 19, 2014
	Toggle = True
	GY_ActivateWindow(WOptions)
	;	End edit
	;########################################################################################

	; Menu loop
	Repeat
		;#######################################################################################################################
		;	Changed conditional check on buttons by clan_fd on Mar 19, 2014
		;; Options selected
		;If GY_ButtonHit(BStart)
		;	Exit
		;ElseIf GY_ButtonHit(BQuit) Or KeyHit(1)
		;	End
		;ElseIf GY_ButtonHit(BGFXOpts)
		;	GraphicsVisible = Not GraphicsVisible
		;	If GraphicsVisible = True
		;		GY_GadgetAlpha(WGraphics, 1.0, True) : GY_LockGadget(WGraphics, False)
		;		GY_ActivateWindow(WGraphics)
		;	Else
		;		GY_GadgetAlpha(WGraphics, 0.0, True) : GY_LockGadget(WGraphics, True)
		;	EndIf
		;ElseIf GY_ButtonHit(BMiscOpts)
		;	MiscVisible = Not MiscVisible
		;	If MiscVisible = True
		;		GY_GadgetAlpha(WMisc, 1.0, True) : GY_LockGadget(WMisc, False)
		;		GY_ActivateWindow(WMisc)
		;	Else
		;		GY_GadgetAlpha(WMisc, 0.0, True) : GY_LockGadget(WMisc, True)
		;	EndIf
		;ElseIf GY_ButtonHit(BCtrlOpts)
		;	ControlsVisible = Not ControlsVisible
		;	If ControlsVisible = True
		;		GY_GadgetAlpha(WControls, 1.0, True) : GY_LockGadget(WControls, False)
		;		GY_ActivateWindow(WControls)
		;		SelectedControl = 0
		;		GY_LeftClick = False
		;	Else
		;		GY_GadgetAlpha(WControls, 0.0, True) : GY_LockGadget(WControls, True)
		;	EndIf
		;EndIf
		
		If GY_ButtonHit(BOptionsDone) Or KeyHit(1)
			Exit
		ElseIf GY_ButtonHit(BGFXOpts)
			GraphicsVisible = True
			MiscVisible = False
			ControlsVisible = False
			Toggle = True
		ElseIf GY_ButtonHit(BCtrlOpts)
			GraphicsVisible = False
			ControlsVisible = True
			MiscVisible = False
			Toggle = True
		ElseIf GY_ButtonHit(BMiscOpts)
			MiscVisible = True
			GraphicsVisible = False
			ControlsVisible = False
			Toggle = True		
		EndIf
		
		If Toggle = True
			Toggle = False
			If GraphicsVisible = True
				GY_GadgetAlpha(LRes, 1.0, True)
				GY_LockGadget(LRes, False)
				GY_GadgetAlpha(LResolution, 1.0, True)
				GY_LockGadget(LResolution, False)
				GY_GadgetAlpha(LColorDepth, 1.0, True)
				GY_LockGadget(LColorDepth, False)
				GY_GadgetAlpha(CDepth, 1.0, True)
				GY_LockGadget(CDepth, False)
				GY_GadgetAlpha(BAntiAlias, 1.0, True)
				GY_LockGadget(BAntiAlias, False)
				GY_GadgetAlpha(BEnableGrass, 1.0, True)
				GY_LockGadget(BEnableGrass, False)
				GY_GadgetAlpha(BFullScreen, 1.0, True)
				GY_LockGadget(BFullScreen, False)
				GY_GadgetAlpha(BVSyncOption, 1.0, True)
				GY_LockGadget(BVSyncOption, False)
				GY_GadgetAlpha(LAnisotropy, 1.0, True)
				GY_LockGadget(LAnisotropy, False)
				GY_GadgetAlpha(CAnisotropy, 1.0, True)
				GY_LockGadget(CAnisotropy, False)
				;Adding FastExt to the options window Cysis145
				GY_GadgetAlpha(BBloom, 1.0, True)
				GY_LockGadget(BBloom, False)
				GY_GadgetAlpha(BRays, 1.0, True)
				GY_LockGadget(BRays, False)
				GY_GadgetAlpha(BWater, 1.0, True)
				GY_LockGadget(BWater, False)
				;GY_GadgetAlpha(BDOF, 1.0, True)
				;GY_LockGadget(BDOF, False)
			Else
				GY_GadgetAlpha(LRes, 0.0, True)
				GY_LockGadget(LRes, True)
				GY_GadgetAlpha(LResolution, 0.0, True)
				GY_LockGadget(LResolution, True)
				GY_GadgetAlpha(LColorDepth, 0.0, True)
				GY_LockGadget(LColorDepth, True)
				GY_GadgetAlpha(CDepth, 0.0, True)
				GY_LockGadget(CDepth, True)
				GY_GadgetAlpha(BAntiAlias, 0.0, True)
				GY_LockGadget(BAntiAlias, True)
				GY_GadgetAlpha(BEnableGrass, 0.0, True)
				GY_LockGadget(BEnableGrass, True)
				GY_GadgetAlpha(BFullScreen, 0.0, True)
				GY_LockGadget(BFullScreen, True)
				GY_GadgetAlpha(BVSyncOption, 0.0, True)
				GY_LockGadget(BVSyncOption, True)
				GY_GadgetAlpha(LAnisotropy, 0.0, True)
				GY_LockGadget(LAnisotropy, True)
				GY_GadgetAlpha(CAnisotropy, 0.0, True)
				GY_LockGadget(CAnisotropy, True)
				;Adding FastExt to the optins window Cysis145
				GY_GadgetAlpha(BBloom, 0.0, True)
				GY_LockGadget(BBloom, False)
				GY_GadgetAlpha(BRays, 0.0, True)
				GY_LockGadget(BRays, False)
				GY_GadgetAlpha(BWater, 0.0, True)
				GY_LockGadget(BWater, False)
				;GY_GadgetAlpha(BDOF, 1.0, True)
				;GY_LockGadget(BDOF, False)
			EndIf
			
			If MiscVisible = True
				GY_GadgetAlpha(SVolume, 1.0, True)
				GY_LockGadget(SVolume, False)
				GY_GadgetAlpha(BUpdateMusic, 1.0, True)
				GY_LockGadget(BUpdateMusic, False)
			Else
				GY_GadgetAlpha(SVolume, 0.0, True)
				GY_LockGadget(SVolume, True)
				GY_GadgetAlpha(BUpdateMusic, 0.0, True)
				GY_LockGadget(BUpdateMusic, True)
			EndIf
		
			If ControlsVisible = True
				GY_GadgetAlpha(LForwardKey, 1.0, True)
				GY_LockGadget(LForwardKey, False)
				GY_GadgetAlpha(LBackwardKey, 1.0, True)
				GY_LockGadget(LBackwardKey, False)
				GY_GadgetAlpha(LRightKey, 1.0, True)
				GY_LockGadget(LRightKey, False)
				GY_GadgetAlpha(LLeftKey, 1.0, True)
				GY_LockGadget(LLeftKey, False)
				GY_GadgetAlpha(LFlyUpKey, 1.0, True)
				GY_LockGadget(LFlyUpKey, False)
				GY_GadgetAlpha(LFlyDownKey, 1.0, True)
				GY_LockGadget(LFlyDownKey, False)
				GY_GadgetAlpha(LSelectKey, 1.0, True)
				GY_LockGadget(LSelectKey, False)
				GY_GadgetAlpha(LMoveToKey, 1.0, True)
				GY_LockGadget(LMoveToKey, False)
				GY_GadgetAlpha(LTalkToKey, 1.0, True)
				GY_LockGadget(LTalkToKey, False)
				GY_GadgetAlpha(LRunKey, 1.0, True)
				GY_LockGadget(LRunKey, False)
				GY_GadgetAlpha(LAlwaysRunKey, 1.0, True)
				GY_LockGadget(LAlwaysRunKey, False)
				GY_GadgetAlpha(LJumpKey, 1.0, True)
				GY_LockGadget(LJumpKey, False)
				GY_GadgetAlpha(LViewKey, 1.0, True)
				GY_LockGadget(LViewKey, False)
				GY_GadgetAlpha(LCamForwardKey, 1.0, True)
				GY_LockGadget(LCamForwardKey, False)
				GY_GadgetAlpha(LCamBackwardKey, 1.0, True)
				GY_LockGadget(LCamBackwardKey, False)
				GY_GadgetAlpha(LAttackKey, 1.0, True)
				GY_LockGadget(LAttackKey, False)
				GY_GadgetAlpha(LCycleTargetKey, 1.0, True)
				GY_LockGadget(LCycleTargetKey, False)
				GY_GadgetAlpha(BInvertAxis1, 1.0, True)
				GY_LockGadget(BInvertAxis1, False)
				GY_GadgetAlpha(BInvertAxis3, 1.0, True)
				GY_LockGadget(BInvertAxis3, False)
				SelectedControl = 0
				GY_LeftClick = False
			Else
				GY_GadgetAlpha(LForwardKey, 0.0, True)
				GY_LockGadget(LForwardKey, True)
				GY_GadgetAlpha(LBackwardKey, 0.0, True)
				GY_LockGadget(LBackwardKey, True)
				GY_GadgetAlpha(LRightKey, 0.0, True)
				GY_LockGadget(LRightKey, True)
				GY_GadgetAlpha(LLeftKey, 0.0, True)
				GY_LockGadget(LLeftKey, True)
				GY_GadgetAlpha(LFlyUpKey, 0.0, True)
				GY_LockGadget(LFlyUpKey, True)
				GY_GadgetAlpha(LFlyDownKey, 0.0, True)
				GY_LockGadget(LFlyDownKey, True)
				GY_GadgetAlpha(LSelectKey, 0.0, True)
				GY_LockGadget(LSelectKey, True)
				GY_GadgetAlpha(LMoveToKey, 0.0, True)
				GY_LockGadget(LMoveToKey, True)
				GY_GadgetAlpha(LTalkToKey, 0.0, True)
				GY_LockGadget(LTalkToKey, True)
				GY_GadgetAlpha(LRunKey, 0.0, True)
				GY_LockGadget(LRunKey, True)
				GY_GadgetAlpha(LAlwaysRunKey, 0.0, True)
				GY_LockGadget(LAlwaysRunKey, True)
				GY_GadgetAlpha(LJumpKey, 0.0, True)
				GY_LockGadget(LJumpKey, True)
				GY_GadgetAlpha(LViewKey, 0.0, True)
				GY_LockGadget(LViewKey, True)
				GY_GadgetAlpha(LCamForwardKey, 0.0, True)
				GY_LockGadget(LCamForwardKey, True)
				GY_GadgetAlpha(LCamBackwardKey, 0.0, True)
				GY_LockGadget(LCamBackwardKey, True)
				GY_GadgetAlpha(LAttackKey, 0.0, True)
				GY_LockGadget(LAttackKey, True)
				GY_GadgetAlpha(LCycleTargetKey, 0.0, True)
				GY_LockGadget(LCycleTargetKey, True)
				GY_GadgetAlpha(BInvertAxis1, 0.0, True)
				GY_LockGadget(BInvertAxis1, True)
				GY_GadgetAlpha(BInvertAxis3, 0.0, True)
				GY_LockGadget(BInvertAxis3, True)
			EndIf
		EndIf
		;	End edit
		;#############################################################################################################

		;#############################################################################################################
		;	Changed window handle by clan_fd on Mar 19, 2014
		; Update graphics window
		;If GY_WindowActive(WGraphics) And GraphicsVisible = True
		If GY_WindowActive(WOptions) And GraphicsVisible = True
		;	End edit
		;#############################################################################################################
			ComboChanged = GY_ListBoxItem$(LResolution) <> CurrentRes$ Or GY_ComboBoxItem$(CDepth) <> CurrentDepth$ Or GY_ComboBoxItem$(CAnisotropy) <> CurrentAnisotropy$
			
			;Adding FastExt to the options window Cysis145
			If GY_CheckBoxHit(BAntiAlias) Or GY_CheckBoxHit(BEnableGrass) Or GY_CheckBoxHit(BFullScreen) Or GY_CheckBoxHit(BVSyncOption) Or GY_CheckBoxHit(BBloom) Or GY_CheckBoxHit(BRays) Or GY_CheckBoxHit(BWater)  Or GY_CheckBoxHit(BDOF) Or ComboChanged 
				CurrentRes$ = GY_ListBoxItem(LResolution)
				CurrentDepth$ = GY_ComboBoxItem(CDepth)
				CurrentAnisotropy$ = GY_ComboBoxItem(CAnisotropy)
				; Update file
				F = OpenFile("Data\Options.dat")
				If F = 0 Then RuntimeError("Could not open Data\Options.dat!")
					Divider = Instr(CurrentRes$, "x")
					If Divider = 0 Then RuntimeError("Invalid resolution format!")
					Width = Trim$(Left$(CurrentRes$, Divider - 1))
					Height = Trim$(Mid$(CurrentRes$, Divider + 1))
					WriteShort(F, Width)
					WriteShort(F, Height)
					If CurrentDepth$ = "16 bit"
						WriteByte(F, 16)
					ElseIf CurrentDepth$ = "32 bit"
						WriteByte(F, 32)
					ElseIf CurrentDepth$ = LanguageString$(LS_BestAvailable)
						WriteByte(F, 0)
					EndIf
					WriteByte(F, GY_CheckBoxDown(BAntiAlias))
					SeekFile(F, 10)
					WriteByte(F, GY_CheckBoxDown(BEnableGrass))
					If CurrentAnisotropy$ = LanguageString$(LS_Disabled)
						WriteByte(F, 0)
					ElseIf CurrentAnisotropy$ = "x4"
						WriteByte(F, 4)
					ElseIf CurrentAnisotropy$ = "x8"
						WriteByte(F, 8)
					ElseIf CurrentAnisotropy$ = "x16"
						WriteByte(F, 16)
					EndIf
					WriteByte(F,GY_CheckBoxDown(BFullScreen))
					WriteByte(F,GY_CheckBoxDown(BVSyncOption))
					;Adding fastext to the options window Cysis145
					WriteByte(F,GY_CheckBoxDown(BBloom))
					WriteByte(F,GY_CheckBoxDown(BRays))
					WriteByte(F,GY_CheckBoxDown(BWater))
					WriteByte(F,GY_CheckBoxDown(BDOF))
				CloseFile(F)
			EndIf
		EndIf
		
		;######################################################################################################################
		;	Changed window handle by clan_fd on Mar 19, 2014
		; Update controls window
		;If GY_WindowActive(WControls) And ControlsVisible = True
		If GY_WindowActive(WOptions) And ControlsVisible = True
		;	End edit
		;######################################################################################################################
			; Invert mouse options
			If GY_CheckBoxHit(BInvertAxis1)
				InvertAxis1 = 0 - InvertAxis1
				SaveControlBindings("Data\Controls.dat")
			ElseIf GY_CheckBoxHit(BInvertAxis3)
				InvertAxis3 = 0 - InvertAxis3
				SaveControlBindings("Data\Controls.dat")
			EndIf

			; Control remapping
			If SelectedControl = 0
				; Select a control to assign to
				If GY_LeftClick
					If GY_MouseHovering(LForwardKey)
						SelectedControl = 1 : GY_UpdateLabel(LForwardKey, LanguageString$(LS_CForward) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LBackwardKey)
						SelectedControl = 2 : GY_UpdateLabel(LBackwardKey, LanguageString$(LS_CStop) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LRightKey)
						SelectedControl = 3 : GY_UpdateLabel(LRightKey, LanguageString$(LS_CTurnRight) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LLeftKey)
						SelectedControl = 4 : GY_UpdateLabel(LLeftKey, LanguageString$(LS_CTurnLeft) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LFlyUpKey)
						SelectedControl = 5 : GY_UpdateLabel(LFlyUpKey, LanguageString$(LS_CFlyUp) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LFlyDownKey)
						SelectedControl = 6 : GY_UpdateLabel(LFlyDownKey, LanguageString$(LS_CFlyDown) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LRunKey)
						SelectedControl = 7 : GY_UpdateLabel(LRunKey, LanguageString$(LS_CRun) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LJumpKey)
						SelectedControl = 8 : GY_UpdateLabel(LJumpKey, LanguageString$(LS_CJump) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LViewKey)
						SelectedControl = 9 : GY_UpdateLabel(LViewKey, LanguageString$(LS_CViewMode) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LCamForwardKey)
						SelectedControl = 10 : GY_UpdateLabel(LCamForwardKey, LanguageString$(LS_CZoomIn) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LCamBackwardKey)
						SelectedControl = 11 : GY_UpdateLabel(LCamBackwardKey, LanguageString$(LS_CZoomOut) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LAttackKey)
						SelectedControl = 12 : GY_UpdateLabel(LAttackKey, LanguageString$(LS_CAttackTarget) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LAlwaysRunKey)
						SelectedControl = 13 : GY_UpdateLabel(LAlwaysRunKey, LanguageString$(LS_CAlwaysRun) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LCycleTargetKey)
						SelectedControl = 14 : GY_UpdateLabel(LCycleTargetKey, LanguageString$(LS_CCycleTarget) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LMoveToKey)
						SelectedControl = 15 : GY_UpdateLabel(LMoveToKey, LanguageString$(LS_CMoveTo) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LTalkToKey)
						SelectedControl = 16 : GY_UpdateLabel(LTalkToKey, LanguageString$(LS_CTalkTo) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					ElseIf GY_MouseHovering(LSelectKey)
						SelectedControl = 17 : GY_UpdateLabel(LSelectKey, LanguageString$(LS_CSelect) + " " + LanguageString$(LS_CPressKey), 255, 50, 50)
					EndIf
					GY_LeftClick = False
				EndIf
			; Assignment
			Else
				; Keyboard
				Ctrl = -1
				For i = 0 To 211
					If ControlName$(i) <> LanguageString$(LS_Unknown)
						If KeyDown(i) Then Ctrl = i
					EndIf
				Next
				; Mouse
				MZSpeed = MouseZSpeed()
				JXSpeed = JoyXDir()
				JYSpeed = JoyYDir()
				If GY_LeftClick
					Ctrl = 501
				ElseIf GY_RightClick
					Ctrl = 502
				ElseIf MouseHit(3)
					Ctrl = 503
				ElseIf MZSpeed > 0
					Ctrl = 508
				ElseIf MZSpeed < 0
					Ctrl = 509
				EndIf
				; Joystick
				If JoyType() > 0
					If JYSpeed > 0
						Ctrl = 1014
					ElseIf JYSpeed < 0
						Ctrl = 1013
					ElseIf JXSpeed > 0
						Ctrl = 1015
					ElseIf JXSpeed < 0
						Ctrl = 1016
					EndIf
					For i = 1 To 8
						If JoyHit(i) Then Ctrl = 1000 + i
					Next
					If JoyHat() > -1
						If JoyHat() = 0 Or JoyHat() = 45 Or JoyHat() = 315
							Ctrl = 1009
						ElseIf JoyHat() = 270
							Ctrl = 1012
						ElseIf JoyHat() = 180 Or JoyHat() = 135 Or JoyHat() = 225
							Ctrl = 1010
						ElseIf JoyHat() = 90
							Ctrl = 1011
						EndIf
					EndIf
				EndIf

				; A control has been selected
				If Ctrl > -1
					Select SelectedControl
						Case 1 : Key_Forward = Ctrl : GY_UpdateLabel(LForwardKey, LanguageString$(LS_CForward) + " " + ControlName$(Key_Forward), 255, 255, 255)
						Case 2 : Key_Back = Ctrl : GY_UpdateLabel(LBackwardKey, LanguageString$(LS_CStop) + " " + ControlName$(Key_Back), 255, 255, 255)
						Case 3 : Key_TurnRight = Ctrl : GY_UpdateLabel(LRightKey, LanguageString$(LS_CTurnRight) + " " + ControlName$(Key_TurnRight), 255, 255, 255)
						Case 4 : Key_TurnLeft = Ctrl : GY_UpdateLabel(LLeftKey, LanguageString$(LS_CTurnLeft) + " " + ControlName$(Key_TurnLeft), 255, 255, 255)
						Case 5 : Key_FlyUp = Ctrl : GY_UpdateLabel(LFlyUpKey, LanguageString$(LS_CFlyUp) + " " + ControlName$(Key_FlyUp), 255, 255, 255)
						Case 6 : Key_FlyDown = Ctrl : GY_UpdateLabel(LFlyDownKey, LanguageString$(LS_CFlyDown) + " " + ControlName$(Key_FlyDown), 255, 255, 255)
						Case 7 : Key_Run = Ctrl : GY_UpdateLabel(LRunKey, LanguageString$(LS_CRun) + " " + ControlName$(Key_Run), 255, 255, 255)
						Case 8 : Key_Jump = Ctrl : GY_UpdateLabel(LJumpKey, LanguageString$(LS_CJump) + " " + ControlName$(Key_Jump), 255, 255, 255)
						Case 9 : Key_ChangeViewMode = Ctrl : GY_UpdateLabel(LViewKey, LanguageString$(LS_CViewMode) + " " + ControlName$(Key_ChangeViewMode), 255, 255, 255)
						Case 10 : Key_CameraIn = Ctrl : GY_UpdateLabel(LCamForwardKey, LanguageString$(LS_CZoomIn) + " " + ControlName$(Key_CameraIn), 255, 255, 255)
						Case 11 : Key_CameraOut = Ctrl : GY_UpdateLabel(LCamBackwardKey, LanguageString$(LS_CZoomOut) + " " + ControlName$(Key_CameraOut), 255, 255, 255)
						Case 12 : Key_Attack = Ctrl : GY_UpdateLabel(LAttackKey, LanguageString$(LS_CAttackTarget) + " " + ControlName$(Key_Attack), 255, 255, 255)
						Case 13 : Key_AlwaysRun = Ctrl : GY_UpdateLabel(LAlwaysRunKey, LanguageString$(LS_CAlwaysRun) + " " + ControlName$(Key_AlwaysRun), 255, 255, 255)
						Case 14 : Key_CycleTarget = Ctrl : GY_UpdateLabel(LCycleTargetKey, LanguageString$(LS_CCycleTarget) + " " + ControlName$(Key_CycleTarget), 255, 255, 255)
						Case 15 : Key_MoveTo = Ctrl : GY_UpdateLabel(LMoveToKey, LanguageString$(LS_CMoveTo) + " " + ControlName$(Key_MoveTo), 255, 255, 255)
						Case 16 : Key_TalkTo = Ctrl : GY_UpdateLabel(LTalkToKey, LanguageString$(LS_CTalkTo) + " " + ControlName$(Key_TalkTo), 255, 255, 255)
						Case 17 : Key_Select = Ctrl : GY_UpdateLabel(LSelectKey, LanguageString$(LS_CSelect) + " " + ControlName$(Key_Select), 255, 255, 255)
					End Select
					SelectedControl = 0
					SaveControlBindings("Data\Controls.dat")
				EndIf
			EndIf
		EndIf

		;##################################################################################################################
		;	Change window handle by clan_fd on Mar 19, 2014
		; Update misc window
		;If GY_WindowActive(WMisc) And MiscVisible = True
		If GY_WindowActive(WOptions) And MiscVisible = True
		;	End edit
		;##################################################################################################################
			; Volume
			If Int(GY_GetSliderValue#(SVolume)) <> CurrentVolume
				CurrentVolume = Int(GY_GetSliderValue#(SVolume))
				DefaultVolume# = CurrentVolume / 100.0
				; Update file
				F = OpenFile("Data\Options.dat")
				If F = 0 Then RuntimeError("Could not open Data\Options.dat!")
					SeekFile F, 6
					WriteFloat F, DefaultVolume#
				CloseFile(F)
			EndIf
			; Music update
			If GY_CheckBoxDown(BUpdateMusic) <> 1 - UpdateMusic
				UpdateMusic = 1 - GY_CheckBoxDown(BUpdateMusic)
				; Update file
				F = WriteFile("Data\Game Data\Misc.dat")
					WriteLine F, GameName$
					WriteLine F, UpdateGame$
					WriteLine F, UpdateMusic
				CloseFile F
			EndIf
		EndIf

		;##############################################################################################################
		;	Commented out by clan_fd on Mar 19, 2014
		;; Windows being closed
		;If GY_WindowClosed(WGraphics) Or GY_ButtonHit(BGFXDone)
		;	GraphicsVisible = False
		;	GY_GadgetAlpha(WGraphics, 0.0, True) : GY_LockGadget(WGraphics, True)
		;ElseIf GY_WindowClosed(WMisc) Or GY_ButtonHit(BMiscDone)
		;	MiscVisible = False
		;	GY_GadgetAlpha(WMisc, 0.0, True) : GY_LockGadget(WMisc, True)
		;ElseIf GY_WindowClosed(WControls) Or GY_ButtonHit(BControlsDone)
		;	ControlsVisible = False
		;	GY_GadgetAlpha(WControls, 0.0, True) : GY_LockGadget(WControls, True)
		;EndIf
		;	End edit
		;##############################################################################################################

		; Update everything
		GY_Update()
		UpdateWorld()
		RenderWorld()
		Flip(VSync)
	Forever

	;###############################################################################################
	;	Free new gadgets by clan_fd on Mar 19, 2014
	;FreeEntity(Background)
	;GY_FreeGadget(WGraphics) : GY_FreeGadget(WControls) : GY_FreeGadget(WMisc)
	;GY_FreeGadget(BStart) : GY_FreeGadget(BGFXOpts) : GY_FreeGadget(BMiscOpts) : GY_FreeGadget(BCtrlOpts) : GY_FreeGadget(BQuit)
	GY_FreeGadget(BGFXOpts)
	GY_FreeGadget(BMiscOpts)
	GY_FreeGadget(BCtrlOpts)
	GY_FreeGadget(BQuit)
	GY_FreeGadget(WOptions)
	GY_FreeGadget(BOptionsDone)
	;	End edit
	;###############################################################################################
End Function








; Plays the menu intro movie file, if present
Function PlayIntroMovie()

	Local MovieFile$ = "", M = 0, X, Y

	; Find a movie file
	If FileType("Data\Game Data\Menu.avi") = 1
		MovieFile$ = "Data\Game Data\Menu.avi"
	ElseIf FileType("Data\Game Data\Menu.mpg") = 1
		MovieFile$ = "Data\Game Data\Menu.mpg"
	ElseIf FileType("Data\Game Data\Menu.mpeg") = 1
		MovieFile$ = "Data\Game Data\Menu.mpeg"
	ElseIf FileType("Data\Game Data\Menu.wmv") = 1
		MovieFile$ = "Data\Game Data\Menu.wmv"
	EndIf

	; If the file is present, load and play the movie
	If MovieFile$ <> ""
		M = OpenMovie(MovieFile$)
		If M <> 0
			; Allow monitor time to switch resolutions
			Delay(750)
			; Play
			X = (GraphicsWidth() / 2) - (MovieWidth(M) / 2)
			Y = (GraphicsHeight() / 2) - (MovieHeight(M) / 2)
			While MoviePlaying(M)
				DrawMovie(M, X, Y)
				Flip(VSync)
				; Allowed player to skip the movie
				If MouseDown(1) Or KeyDown(1) Or KeyDown(57) Or KeyDown(28) Then Exit
			Wend
			CloseMovie(M)
		EndIf
	EndIf

	; Clear any user input which has been ignored
	FlushKeys()
	FlushMouse()

End Function

; Downloads a file
Function DownloadFile(URL$, SaveTo$ = "", GYProgress = 0)

	BytesIn = 0

	; Strip "http://" if provided
	If Len(URL$) > 7
		If Lower$(Left$(URL$, 7)) = "http://" Then URL$ = Mid$(URL$, 8)
	EndIf

	; Split into hostname and path/filename to download
	Slash = Instr(URL$, "/")
	If Slash
		WebHost$ = Left$(URL$, Slash - 1)
		WebFile$ = Right$(URL$, Len (URL$) - Slash + 1)
	Else
		WebHost$ = URL$
		WebFile$ = "/"
	EndIf

	; Save filename - get from URL$ if not provided
	If SaveTo$ = ""
		If WebFile$ = "/"
			SaveTo$ = "Unknown.dat"
		Else
			For i = Len(WebFile$) To 1 Step - 1
				If Mid$(WebFile$, i, 1) = "/" Then SaveTo$ = Right$(WebFile$, Len(WebFile$) - i) : Exit
			Next
			If SaveTo$ = "" Then SaveTo$ = "Unknown.dat"
		EndIf
	EndIf

	WWW = OpenTCPStream(WebHost$, 80)
	If WWW <> 0
		WriteLine(WWW, "GET " + WebFile$ + " HTTP/1.1")
		WriteLine(WWW, "Host: " + WebHost$)
		WriteLine(WWW, "User-Agent: RC-Updater")
		WriteLine(WWW, "Accept: */*")
		WriteLine(WWW, "")

		; Find blank line after header data, where the action begins...
		Repeat
			Delay(10)
			GY_UpdateProgressBar(GYProgress, 0)
			Header$ = ReadLine$(WWW)
			Reply$ = ""

			If Instr(Header$, ": ")
				Reply$ = Left$(Header$, Instr(Header$, ": ") + 1)
			EndIf

			If Lower$(Reply$) = "content-length: "
				BytesToRead = Right$(Header$, Len(Header$) - Len(Reply$))
			EndIf
		Until Header$ = "" Or Eof(WWW)

		If BytesToRead = 0 Then CloseTCPStream WWW : Return 0

		; Create new file to write downloaded bytes into
		Save = WriteFile(SaveTo$)
		If Save = 0 Then CloseTCPStream(WWW) : Return 0

		; Incredibly complex download-to-file routine...
		Repeat
			If Eof(WWW) Then BytesIn = 0 : Exit

			For i = 1 To ReadAvail(WWW)
				WriteByte(Save, ReadByte(WWW))
				BytesIn = BytesIn + 1

				; Update status bar
				If BytesIn Mod 1000 = 0
					Percentage = Int((Float#(BytesIn) / Float#(BytesToRead)) * 100.0)
					GY_UpdateProgressBar(GYProgress, Percentage)
					GY_Update()
					UpdateWorld()
					RenderWorld()
					Flip(vsync)
				EndIf
			Next

			If BytesIn = BytesToRead Then Exit
		Forever

		; Done
		CloseFile(Save)
		CloseTCPStream(WWW)
	EndIf

	Return BytesIn

End Function

; Counts a file checksum
Function CountChecksum(File$)

	Local Result, F
	F = ReadFile(File$)
		While Eof(F) = False
			Result = Result + ReadInt(F)
		Wend
	CloseFile F
	Return Result

End Function

; Loads an up texture for a menu button
Function LoadButtonU(Name$)

	ClearTextureFilters()
	Tex = LoadTexture("Data\Textures\Menu\B" + Name$ + "U.png", 1)
	TextureFilter("", 1 + 8)
	TextureFilter("m_", 1 + 4)
	TextureFilter("a_", 1 + 2)
	Return Tex

End Function

; Loads a down texture for a menu button
Function LoadButtonD(Name$)

	ClearTextureFilters()
	Tex = LoadTexture("Data\Textures\Menu\B" + Name$ + "D.png", 1)
	TextureFilter("", 1 + 8)
	TextureFilter("m_", 1 + 4)
	TextureFilter("a_", 1 + 2)
	Return Tex

End Function

; Loads a hover texture for a menu button
Function LoadButtonH(Name$)

	ClearTextureFilters()
	Tex = LoadTexture("Data\Textures\Menu\B" + Name$ + "H.png", 1)
	TextureFilter("", 1 + 8)
	TextureFilter("m_", 1 + 4)
	TextureFilter("a_", 1 + 2)
	Return Tex

End Function