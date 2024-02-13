; This file must be compiled with BlitzRC

;##############################################################################
; RCCE 2 PROJECT MANAGER v3.0
; This piece of software was desgined and built by Zaven Boyrazian
; Copyright (C) 2024 Gajatix Studios All rights reserved																	
; Support : RCCE Forums : https://realmcrafter.boards.net/
; 
; i) By using/editing this source code you agree Not To remove the copyright protection
;    marker of RydeTec and Gajatix Studios!
; ii) This code is property of Gajatix Studios, the user may edit it in anyway
;     but CANNOT redistribute without authorisation!
;##############################################################################
Strict

AppTitle "Project Manager"

;Include Modules
Include "Modules\F-UI.bb"

Global GUE_width  = 550
Global GUE_height = 300

Global Version$   = "v3.0"
Global Team$ 	 = "Cysis145, Terrier, Corey 'Ryan' Dean"

Global RootDir$ = ".\"
Global LogMode = 1; (0 = standard logging, 1 = debug mode)

ChangeDir RootDir$

FUI_Initialise(GUE_width, GUE_height, 0, 2, True, True, "RCCE 2 Project Manager")
SetBuffer(BackBuffer())

;Images
LogoTex.BBImage = LoadImage("Resources\RCCE Banner.jpg")
LogoTex2.BBImage = LoadImage("Resources\Gajatix.jpg")
Splash.BBImage = LoadImage("Resources\Logo_Splash.bmp")

;Resize Images
ResizeImage LogoTex, 380, 112
ResizeImage LogoTex2, 100, 67
ResizeImage Splash, 550, 300

;Folders
OMF$ = Chr$(34) + "Data\Meshes" + Chr$(34)
OTF$ = Chr$(34) + "Data\Textures" + Chr$(34)
OSF$ = Chr$(34) + "Data\Sounds" + Chr$(34)
OMuF$ = Chr$(34) + "Data\Music" + Chr$(34)

OPF$ = Chr$(34) + "" + Chr$(34)

;Executables
SCT$ = Chr$(34) + "Community ToolKit\Script Crafters Workshop.exe" + Chr$(34)
RSW$ = Chr$(34) + "Community ToolKit\RC Spell Wizard\Application Files\RC Spell Wizard_1_0_0_1\RC Spell Wizard.exe" + Chr$(34)

PMH$ = Chr$(34) + "Resources\Help.txt" + Chr$(34)
SWH$ = Chr$(34) + "Community ToolKit\RC Spell Wizard\RC Spell Wizard Documentation.pdf" + Chr$(34)

GUE$ = Chr$(34) + "GUE.exe" + Chr$(34)
GUM$ = Chr$(34) + "GUE_Max.exe" + Chr$(34)
CLI$ = Chr$(34) + "Client.exe" + Chr$(34)
SER$ = Chr$(34) + "Server.exe" + Chr$(34)

LCL$ = Chr$(34) + "Data\Logs\Client Log.txt" + Chr$(34)
LSL$ = Chr$(34) + "Data\Logs\Server Log.txt" + Chr$(34)

SRP$ = Chr$(34) + "RC_Scriptorama\RC Scriptorama.exe - DONT DELETE" + Chr$(34)
GUB$ = Chr$(34) + "Gubbin Tool.exe" + Chr$(34)
FNT$ = Chr$(34) + "ToolKit\FontGen\Font Generator.exe" + Chr$(34)
TER$ = Chr$(34) + "RC_Terrain_Editor.exe" + Chr$(34)

B3D$ = Chr$(34) + "ToolKit\Blitz3D\Blitz3D.exe" + Chr$(34)
BPS$ = Chr$(34) + "ToolKit\BlitzPlus\BlitzPlus.exe" + Chr$(34)

RPM$ = Chr$(34) + "Project Manager.exe" + Chr$(34)

; Misc options
F.BBStream = ReadFile("Data\Game Data\Misc.dat")
If F = Null Then RuntimeError("Could not open Data\Game Data\Misc.dat!")
	GameName$ = ReadLine$(F)
CloseFile(F)

;Main Window
	WMain = FUI_Window(0, 0, GUE_width, GUE_height, "", "", 0, 0)

	;Splash Screen
	Color 255, 255, 255 : DrawImage(Splash, 0, 0) : Text (ImageWidth(Splash)/2), 200, "Loading Project: " + GameName$, True : Flip()
	Delay 2000
	FreeImage(Splash)
	
	;Title Menu Bar
		;Folders Tab
		M_PFol = FUI_MenuTitle(WMain, "Project Folders")
		M_Meshes = FUI_MenuItem(M_PFol, "Meshes")
		M_textures = FUI_MenuItem(M_PFol, "Textures")
		M_Sounds = FUI_MenuItem(M_PFol, "Sound")
		M_Music = FUI_MenuItem(M_PFol, "Music")
		
		;Wizards Tab
		M_CTol = FUI_MenuTitle(WMain, "Wizards") 
		M_SC = FUI_MenuItem(M_CTol, "Script Crafter")
		M_SW = FUI_MenuItem(M_CTol, "Spell Wizard")
		
		;Help Tab
		M_Help = FUI_MenuTitle(WMain, "Help")
		M_SWH = FUI_MenuItem(M_Help, "Spell Wizard Help")
		M_HF = FUI_MenuItem(M_Help, "Help File")

	;Function Buttons
	BMINI = FUI_Button(WMain, 509, 1, 18, 18, "_")
	BCLOSE = FUI_Button(WMain, 529, 1, 18, 18, "X")
	
	FUI_Label(WMain, 430, 22, "OverSeer Platform v3.0")
	

	;Game Name
	Global ProName = FUI_TextBox(WMain, 180, 1, 320, 18, 50)
	FUI_SendMessage(ProName, M_SETCAPTION, GameName$)

	;Tabs
	TabMain = FUI_Tab(WMain, 0, 20, GUE_width, GUE_height)
	Global TProject = FUI_TabPage(TabMain, "Project")
	Global TNews    = FUI_TabPage(TabMain, "News")
	Global TSupport = FUI_TabPage(TabMain, "Support")

;Project Tab
	;Logo
	FUI_ImageBox(TProject, 160, 27, 380, 112, Ptr LogoTex)
	
	;Buttons
	LET = FUI_GroupBox(TProject, 5, 20, 150, 120, "Engine Tools")
	BGUE = FUI_Button(TProject, 15, 40, 62.5, 25, "GUE") 
	BGUM = FUI_Button(TProject, 82.5, 40, 62.5, 25, "GUE Max") : FUI_ToolTip(BGUM, "Only works for 1920*1080 resolutions")
	BCLI = FUI_Button(TProject, 15, 70, 62.5, 25, "Client")
	BSER = FUI_Button(TProject, 82.5, 70, 62.5, 25, "Server")
	BOPF = FUI_Button(TProject, 15, 100, 130, 25, "Project Folder")
	
	LLG = FUI_GroupBox(TProject, 5, 140, 150, 100, "Logs")
	BLC = FUI_Button(TProject, 15, 165, 130, 25, "Client Log")
	BLS = FUI_Button(TProject, 15, 200, 130, 25, "Server Log")
	
	LTK = FUI_GroupBox(TProject, 160, 140, 240, 100, "Tool Kit")
	BSCR = FUI_Button(TProject, 170, 165, 110, 25, "Scripts Editor")
	BGUB = FUI_Button(TProject, 170, 200, 110, 25, "Gubbins Editor")
	BFNT = FUI_Button(TProject, 285, 165, 110, 25, "Font Editor")
	BTER = FUI_Button(TProject, 285, 200, 110, 25, "Terrain Editor")
	
	LTK = FUI_GroupBox(TProject, 405, 140, 135, 100, "Source Tools")	
	BB3D = FUI_Button(TProject, 410, 165, 125, 25, "Blitz3D")
	BBPS = FUI_Button(TProject, 410, 200, 125, 25, "BlitzPlus")

	;Copyright Marker
	FUI_Label(TProject, 5, 242, "RealmCrafter Community Edition 2 � 2024 RydeTec, Gajatix Studios")
	;Version Marker
	FUI_Label(TProject, 520, 242, Version$)
		
;News Tab
	;Text Field
	LPNS = FUI_GroupBox(TNews, 5, 5, 535, 235, "Patch Notes")
	
	FUI_Label(TNews, 10, 20, "18/03/2015 - Cysis145")
	FUI_Label(TNews, 10, 35, "- Compatible with RCCE2")
	FUI_Label(TNews, 10, 50, "- Project Manager v3.0 released!")
	
	FUI_Label(TNews, 10, 70, "22/01/2015 - Cysis145")
	FUI_Label(TNews, 10, 85, " - Compatible with RCCE_FE v1.104!")
	FUI_Label(TNews, 10, 100, " - Project Manager v2.0 released!")
	
	FUI_Label(TNews, 10, 120, "10/12/2014 - Cysis145")
	FUI_Label(TNews, 10, 135, " - Terrain Editor can now be launched from the Project Manager!")
	FUI_Label(TNews, 10, 150, " - Now Compatible with 32 Bit Systems!")
	
	FUI_Label(TNews, 10, 165, "22/11/2014 - Cysis145")
	FUI_Label(TNews, 10, 180, " - New Project Manager for RCCE_FE availiable for versions v1.103 or higher!")
	
	;Buttons
	BANO = FUI_Button(TNews, 10, 210, 130, 25, "RCCE Annoucements")
	BFANO = FUI_Button(TNews, 150, 210, 130, 25, "Forum Annoucements")
	
	;Copyright Marker
	FUI_Label(TNews, 5, 242, "RealmCrafter Community Edition 2 � 2024 RydeTec, Gajatix Studios")
	;Version Marker
	FUI_Label(TNews, 520, 242, Version$)

;Support Tab
	;Logo
	FUI_ImageBox(TSupport, 440, 173, 100, 67, Ptr LogoTex2)

	;Buttons
	LFRMS = FUI_GroupBox(TSupport, 5, 20, 150, 220, "Forum Links")
	OBEC = FUI_Button(TSupport, 15, 40, 130, 25, "Beginner's Corner")
	ODIS = FUI_Button(TSupport, 15, 73, 130, 25, "Discussion & Help")
	OSCR = FUI_Button(TSupport, 15, 106, 130, 25, "Scripting Discussion")
	OTUT = FUI_Button(TSupport, 15, 139, 130, 25, "Tutorials")
	OSRD = FUI_Button(TSupport, 15, 172, 130, 25, "Source Discussion")
	OSTU = FUI_Button(TSupport, 15, 205, 130, 25, "Source Tutorials")
	
	LFRM = FUI_GroupBox(TSupport, 160, 110, 380, 55, "RCCE Websites")
	ORYD = FUI_Button(TSupport, 175, 130, 110, 25, "RCCE Home")
	OFRM = FUI_Button(TSupport, 295, 130, 110, 25, "RCCE Forum")
	OWIK = FUI_Button(TSupport, 415, 130, 110, 25, "RCCE Wiki")
	
	LADD = FUI_GroupBox(TSupport, 160, 166, 275, 74, "Addtional Support")
	
	FUI_Label(TSupport, 170, 210, "rccesupport@rydesoftware.com")
	FUI_Label(TSupport, 160, 25, "The current Development team: " + Team$)
	FUI_Label(TSupport, 160, 60, "The Project Manager was coded by Zaven Boyrazian and is property") 
	FUI_Label(TSupport, 160, 75, "of Gajatix Studios.")
	
	;Copyright Marker
	FUI_Label(TSupport, 5, 242, "RealmCrafter Community Edition 2 � 2024 RydeTec, Gajatix Studios")
	;Version Marker
	FUI_Label(TSupport, 520, 242, Version$)
	
;Finish up before loop
DeltaTime = MilliSecs()
FUI_HideMouse()

;Start Loop
Repeat
	Cls
	FUI_Update()
	Flip(0)
	
;Activate Buttons
Local E.Event	
	For E.Event = Each Event
		Select E\EventID
		
	;Main Window
		Case BMINI
			FUI_MinimiseApp
		Case BCLOSE
			app\Quit = True
		Case ProName
			F = WriteFile("Data\Game Data\Misc.dat")
				If F = Null Then RuntimeError("Could not open Data\Game Data\Misc.dat!")
					WriteLine F, FUI_SendMessage(ProName, M_GETCAPTION)
				CloseFile(F)
		Case M_Meshes
			ExecFile(OMF$)
		Case M_Textures
			ExecFile(OTF$)
		Case M_Sounds
			ExecFile(OSF$)
		Case M_Music
			ExecFile(OMuF$)
		Case M_SC
			app\Quit = True
			ExecFile(RPM$)
			ExecFile(SCT$)
		Case M_SW
			ExecFile(RSW$)
		Case M_SWH 
			ExecFile(SWH$)
		Case M_HF
			ExecFile(PMH$)		
						
	;Project Tab
		Case BGUE
			ExecFile(GUE$)
		Case BGUM
			ExecFile(GUM$)
		Case BCLI
			ExecFile(CLI$)
		Case BSER
			ExecFile(SER$)
		Case BOPF
			ExecFile(OPF$)
		Case BLC
			ExecFile(LCL$)
		Case BLS
			ExecFile(LSL$)
		Case BSCR
			ExecFile(SRP$)
		Case BGUB
			ExecFile(GUB$)
		Case BFNT
			ExecFile(FNT$)
		Case BTER
			ExecFile(TER$)	
		Case BB3D
			ExecFile(B3D$)
		Case BBPS
			ExecFile(BPS$)
				
	;News Tab
		Case BANO
			ExecFile("http://realmcrafterce.com/forum/viewforum.php?f=8")
		Case BFANO
			ExecFile("http://realmcrafterce.com/forum/viewforum.php?f=9")
	
	;Support Tab
		Case OBEC
			ExecFile("http://realmcrafterce.com/forum/viewforum.php?f=15")
		Case ODIS
			ExecFile("http://realmcrafterce.com/forum/viewforum.php?f=16")
		Case OSCR
			ExecFile("http://realmcrafterce.com/forum/viewforum.php?f=11")
		Case OTUT
			ExecFile("http://realmcrafterce.com/forum/viewforum.php?f=18")
		Case OSRD
			ExecFile("http://realmcrafterce.com/forum/viewforum.php?f=10")
		Case OSTU
			ExecFile("http://realmcrafterce.com/forum/viewforum.php?f=29")
		Case ORYD
			ExecFile("http://realmcrafterce.com/home/")
		Case OFRM
			ExecFile("http://www.realmcrafterce.com/forum/")
		Case OWIK
			ExecFile("http://www.realmcrafterce.com/wiki/")
		End Select
		Delete E
	Next
Until app\Quit = True
	FUI_Destroy()
End

