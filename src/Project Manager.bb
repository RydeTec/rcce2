Strict

AppTitle "RealmCrafter: Community Edition"

;Include Modules
Include "Modules\F-UI.bb"
Include "Modules\Project Manager\Variables.bb"
Include "Modules\Project Manager\Types.bb"
Include "Modules\Project Manager\Functions.bb"

Global GUE_width  = 560
Global GUE_height = 310

Global Version$   = "2.0.0"

Global RootDir$ = ".\"
Global LogMode = 1; (0 = standard logging, 1 = debug mode)

Global GameDir$ = ""
Global GameName$ = ""

If FileType(RootDir$ + "res") <> 2
	RootDir$ = "..\"
End If

ChangeDir RootDir$
RootDir$ = CurrentDir()
GameDir$ = RootDir$

FUI_Initialise(GUE_width, GUE_height, 0, 2, False, True, "RealmCrafter: Community Edition", Version$)
SetBuffer(BackBuffer())

;Images
LogoTex.BBImage = LoadImage(RootDir$ + "res\RCCE Banner.jpg")
LogoTex2.BBImage = LoadImage(RootDir$ + "res\Gajatix.jpg")

;Resize Images
ResizeImage LogoTex, 380, 112
ResizeImage LogoTex2, 100, 67

;Folders
OMF$ = "Data\Meshes"
OTF$ = "Data\Textures"
OSF$ = "Data\Sounds"
OMuF$ = "Data\Music"

;Executables
SCT$ = RootDir$ + "bin\tools\Script Crafters Workshop\Script Crafters Workshop.exe"
RSW$ = RootDir$ + "bin\tools\RC Spell Wizard\RC Spell Wizard.exe"

PMH$ = RootDir$ + "res\Help.txt"
SWH$ = RootDir$ + "bin\tools\RC Spell Wizard\RC Spell Wizard Documentation.pdf"

GUE$ = RootDir$ + "bin\GUE.exe"
CLI$ = RootDir$ + "bin\Client.exe"
SER$ = RootDir$ + "bin\Server.exe"

LCL$ = "Data\Logs\Client Log.txt"
LSL$ = "Data\Logs\Server Log.txt"

SRP$ = RootDir$ + "bin\tools\RC Scriptorama\RC Scriptorama.exe"
FNT$ = RootDir$ + "bin\tools\FontGen\Font Generator.exe"

GUB$ = RootDir$ + "bin\tools\Gubbin Tool.exe"
ARC$ = RootDir$ + "bin\tools\RC Architect.exe"
CAV$ = RootDir$ + "bin\tools\RC Caves Editor.exe"
ROC$ = RootDir$ + "bin\tools\RC Rock Editor.exe"
TER$ = RootDir$ + "bin\tools\RC Terrain Editor.exe"
TRE$ = RootDir$ + "bin\tools\RC Tree Editor.exe"

B3D$ = RootDir$ + "compiler\BlitzRC\BlitzRC.exe"
BPS$ = RootDir$ + "compiler\BlitzPlus\BlitzPlus.exe"

;Main Window
WMain = FUI_Window(0, 0, GUE_width, GUE_height, "", "", 0, 0)

;Game Name
Global ProName = FUI_TextBox(WMain, 230, 1, 270, 18, 50)

;Title Menu Bar
;Projects Tab
M_Projects = FUI_MenuTitle(WMain, "Projects")
M_ProjectsNew = FUI_MenuItem(M_Projects, "New Project")
M_ProjectsOpen = FUI_MenuItem(M_Projects, "Open Project")
Global M_ProjectsRecent = FUI_MenuItem(M_Projects, "Recent Projects")

;Set Project
setProject(GameDir$)

;Folders Tab
M_PFol = FUI_MenuTitle(WMain, "Project Folders")
M_Meshes = FUI_MenuItem(M_PFol, "Meshes")
M_textures = FUI_MenuItem(M_PFol, "Textures")
M_Sounds = FUI_MenuItem(M_PFol, "Sound")
M_Music = FUI_MenuItem(M_PFol, "Music")

;Wizards Tab
M_CTol = FUI_MenuTitle(WMain, "3rd Party") 
M_SC = FUI_MenuItem(M_CTol, "Script Crafter")
M_SW = FUI_MenuItem(M_CTol, "Spell Wizard")

;Help Tab
M_Help = FUI_MenuTitle(WMain, "Help")
M_SWH = FUI_MenuItem(M_Help, "Spell Wizard Help")
M_HF = FUI_MenuItem(M_Help, "Help File")

;Function Buttons
BMINI = FUI_Button(WMain, 509, 1, 18, 18, "_")
BCLOSE = FUI_Button(WMain, 529, 1, 18, 18, "X")

FUI_Label(WMain, 529+18, 22, Version$, ALIGN_RIGHT)

;Tabs
TabMain = FUI_Tab(WMain, 0, 20, GUE_width, GUE_height)
Global TProject = FUI_TabPage(TabMain, "Project")
Global TSupport = FUI_TabPage(TabMain, "Support")

;Project Tab
;Logo
FUI_ImageBox(TProject, 160, 27, 380, 112, Ptr LogoTex)

;Buttons
LET = FUI_GroupBox(TProject, 5, 20, 150, 120, "Engine Tools")
BGUE = FUI_Button(TProject, 15, 40, 130, 25, "Game Unified Editor") 
BCLI = FUI_Button(TProject, 15, 70, 62.5, 25, "Client")
BSER = FUI_Button(TProject, 82.5, 70, 62.5, 25, "Server")
BOPF = FUI_Button(TProject, 15, 100, 130, 25, "Project Folder")

LLG = FUI_GroupBox(TProject, 5, 140, 150, 100, "Logs")
BLC = FUI_Button(TProject, 15, 165, 130, 25, "Client Log")
BLS = FUI_Button(TProject, 15, 200, 130, 25, "Server Log")

LTK = FUI_GroupBox(TProject, 160, 140, 240, 100, "Tool Kit")
TOOL1 = FUI_Button(TProject, 170, 165, 70.5, 25, "Gubbin")
TOOL2 = FUI_Button(TProject, 170, 200, 70.5, 25, "Architect")
TOOL3 = FUI_Button(TProject, 170 + 75.5, 165, 70.5, 25, "Caves")
TOOL4 = FUI_Button(TProject, 170 + 75.5, 200, 70.5, 25, "Rock")
TOOL5 = FUI_Button(TProject, 170 + 75.5 + 75.5, 165, 70.5, 25, "Terrain")
TOOL6 = FUI_Button(TProject, 170 + 75.5 + 75.5, 200, 70.5, 25, "Tree")

LTK = FUI_GroupBox(TProject, 405, 140, 135, 100, "Source Tools")	
BB3D = FUI_Button(TProject, 410, 165, 125, 25, "BlitzRC")
BBPS = FUI_Button(TProject, 410, 200, 125, 25, "BlitzPlus")

if FileType(B3D$) = 0
	FUI_DisableGadget(BB3D)
EndIf

if FileType(BPS$) = 0
	FUI_DisableGadget(BBPS)
EndIf

Global GDIR = FUI_Label(TProject, 405+135, 242, GameDir$, ALIGN_RIGHT)

;Support Tab
;Logo
FUI_ImageBox(TSupport, 440, 173, 100, 67, Ptr LogoTex2)

;Buttons
LFRMS = FUI_GroupBox(TSupport, 5, 20, 150, 220, "Forum Links")
OBEC = FUI_Button(TSupport, 15, 40, 130, 25, "General")
ODIS = FUI_Button(TSupport, 15, 73, 130, 25, "Discussion & Help")
OSCR = FUI_Button(TSupport, 15, 106, 130, 25, "Scripting Discussion")
OTUT = FUI_Button(TSupport, 15, 139, 130, 25, "Tutorials")
OSRD = FUI_Button(TSupport, 15, 172, 130, 25, "Source Discussion")
OSTU = FUI_Button(TSupport, 15, 205, 130, 25, "Showcase")

LFRM = FUI_GroupBox(TSupport, 160, 110, 380, 55, "RCCE Websites")
ORYD = FUI_Button(TSupport, 175, 130, 110, 25, "RCCE Source")
OFRM = FUI_Button(TSupport, 295, 130, 110, 25, "RCCE Forum")
OWIK = FUI_Button(TSupport, 415, 130, 110, 25, "RCCE Wiki")

LADD = FUI_GroupBox(TSupport, 160, 166, 275, 74, "Addtional Support")

;Buttons
BANO = FUI_Button(TSupport, 170, 180, 125, 25, "RCCE Updates")
BFANO = FUI_Button(TSupport, 170 + 130, 180, 125, 25, "Forum Annoucements")
DISC = FUI_Button(TSupport, 170, 210, 125, 25, "RCCE Discord")

FUI_Label(TSupport, 160, 25, "The RealmCrafter: Community Edition is a community driven project.")
FUI_Label(TSupport, 160, 60, "You can request to become a contributor by visiting the following link:") 
FUI_Label(TSupport, 160, 75, "https://github.com/orgs/RydeTec/teams/rcce-contributors")
	
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
			F.BBStream = WriteFile("Data\Game Data\Misc.dat")
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
			ExecFile(SCT$, "", GameDir$)
			FUI_MinimiseApp ; Necessary as FUI seems to freeze when the SCT is opened, this is a temporary fix
		Case M_SW
			ExecFile(RSW$, "", GameDir$)
		Case M_SWH 
			ExecFile(dq(SWH$))
		Case M_HF
			ExecFile(dq(PMH$))		

	;Projects Tab
		case M_ProjectsNew
			If FileType(RootDir$ + "projects") <> 2
				CreateDir(RootDir$ + "projects")
			EndIf

			If FUI_CustomOpenDialog("Project Directory", RootDir$ + "projects", "Project Folder|\\", False, True)
				CopyDir(RootDir$ + "Data", app\currentFile + "Data")
				setProject(app\currentFile)
			EndIf
		case M_ProjectsOpen
			If FileType(RootDir$ + "projects") <> 2
				CreateDir(RootDir$ + "projects")
			EndIf

			If FUI_CustomOpenDialog("Project Directory", RootDir$ + "projects", "Project Folder|\\", False, True)
				setProject(app\currentFile)
			EndIf
	;Project Tab
		Case BGUE
			ExecFile(GUE$, "", GameDir$ + "Data\")
		Case BCLI
			ExecFile(CLI$, "", GameDir$ + "Data\")
		Case BSER
			ExecFile(SER$, "", GameDir$ + "Data\")
		Case BOPF
			ExecFile(dq(GameDir$))
		Case BLC
			ExecFile(dq(LCL$))
		Case BLS
			ExecFile(dq(LSL$))

		Case TOOL1
			ExecFile(GUB$, "", GameDir$ + "Data\Game Data\")
		Case TOOL2
			ExecFile(ARC$, "", GameDir$ + "Data\Game Data\")
		Case TOOL3
			ExecFile(CAV$, "", GameDir$ + "Data\Game Data\")
		Case TOOL4
			ExecFile(ROC$, "", GameDir$ + "Data\Game Data\")	
		Case TOOL5
			ExecFile(TER$, "", GameDir$ + "Data\Game Data\")
		Case TOOL6
			ExecFile(TRE$, "", GameDir$ + "Data\Game Data\")

		Case BB3D
			ExecFile(dq(B3D$))
		Case BBPS
			ExecFile(dq(BPS$))
			
		Case BANO
			ExecFile("https://github.com/RydeTec/rcce2/releases")
		Case BFANO
			ExecFile("https://realmcrafter.boards.net/board/1/announcements")
	
	;Support Tab
		Case OBEC
			ExecFile("https://realmcrafter.boards.net/board/11/general")
		Case ODIS
			ExecFile("https://realmcrafter.boards.net/board/4/help")
		Case OSCR
			ExecFile("https://realmcrafter.boards.net/board/14/scripts")
		Case OTUT
			ExecFile("https://realmcrafter.boards.net/board/5/tutorials")
		Case OSRD
			ExecFile("https://realmcrafter.boards.net/board/7/source")
		Case OSTU
			ExecFile("https://realmcrafter.boards.net/board/9/showcase")
		Case ORYD
			ExecFile("https://github.com/RydeTec/rcce2")
		Case OFRM
			ExecFile("https://realmcrafter.boards.net/")
		Case OWIK
			ExecFile("https://realmcrafter.fandom.com/wiki/RealmCrafter_Wiki")
		Case DISC
			; Break up discord link so it can't be parsed by bots and spammed 
			Local disc1$ = "https://disco"
			Local disc3$ = "NHKzB"
			Local disc2$ = "rd.gg/"
			Local disc4$ = "9rdgA"
			Local discFull$ = disc1$ + disc2$ + disc3$ + disc4$
			ExecFile(discFull$)
		End Select

		if E <> Null
			mi.MenuItem = Object.MenuItem(E\EventID)
			if mi <> Null
				if Handle( mi\Parent ) = M_ProjectsRecent
					setProject(mi\caption$)
				EndIf
			EndIf
		EndIf

		Delete E
	Next
Until app\Quit = True

FUI_Destroy()
End