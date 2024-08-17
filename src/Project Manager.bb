Strict

;Include Modules
Include "Modules\Framework\RCCEApp.bb"
Include "Modules\F-UI.bb"
Include "Modules\Project Manager\Variables.bb"
Include "Modules\Project Manager\Types.bb"
Include "Modules\Project Manager\Functions.bb"
Include "Modules\Helpers\List\List.bb"
Include "Modules\IO\Image.bb"
Include "Modules\IO\File.bb"
Include "Modules\Graphics\UI\Components\Component.bb"
Include "Modules\Graphics\UI\Components\ImageComponent.bb"
Include "Modules\Graphics\UI\Components\TextComponent.bb"
Include "Modules\Graphics\UI\Components\MenuItemComponent.bb"
Include "Modules\Graphics\RCCEGraphics.bb"
Include "Modules\Framework\Project\Project.bb"

Type ProjectManager.RCCEApp
	Field window%
	Field assetList.List
	Field componentList.List
	Field splashComponent.ImageComponent
	Field gfx.RCCEGraphics
	Field prj.Project
	Field recentProjectFile.File
	Field recentProjectList.List

	; Need to be able to combine all components into a single list
	Field menuItemList.List

	Method create.ProjectManager()
		self = Recast.ProjectManager(RCCEApp::create(self, "RealmCrafter: Community Edition", ".\"))

		return self
	End Method

	Method init()
		If FileType(self\rootDir + "res") <> 2
			self\rootDir = "..\"
		End If

		RCCEApp::init(self, 560, 310)

		self\gfx = new RCCEGraphics(self\width, self\height, 0, 2)

		RCCEGraphics::init(self\gfx)

		ProjectManager::showSplash(self)

		ProjectManager::loadRecentProjects(self)
		ProjectManager::loadProject(self)

		FUI_Initialise(self\width, self\height, 0, 2, False, True, self\title, RCCEApp::version(self))
		
		RCCEGraphics::resetBuffer(self\gfx)

		self\window = FUI_Window(0, 0, self\width, self\height, "", "", 0, 0)

		

		ProjectManager::loadAssets(self)
		ProjectManager::loadComponents(self)
	End Method

	Method showSplash()
		if (self\splashComponent = Null)
			Local img.Image = new Image(self\rootDir + "res\Logo_Splash.bmp")

			self\splashComponent = new ImageComponent(img)
		end if

		ImageComponent::resize(self\splashComponent, self\width, self\height)
		ImageComponent::place(self\splashComponent, 0, 0)
		ImageComponent::draw(self\splashComponent)

		Local loadingText.TextComponent = new TextComponent("Loading default project", True)
		TextComponent::place(loadingText, (self\splashComponent\scale\x / 2), 200)
		TextComponent::setColor(loadingText, 255,255,255)
		TextComponent::draw(loadingText)

		RCCEGraphics::push(self\gfx)

		Image::unload(self\splashComponent\img)
	End Method

	Method loadAssets()
		self\assetList = new List()

		List::push(self\assetList, new Image(self\rootDir + "res\RCCE Banner.jpg"))
		List::push(self\assetList, new Image(self\rootDir + "res\Gajatix.jpg"))
	End Method

	Method getImage.BBImage(index%)
		img.Image = List::get(self\assetList, index)
		return img\img
	End Method

	Method loadComponents()
		ProjectManager::addComponent(self, new ImageComponent(List::get(self\assetList, 0)))
		ProjectManager::addComponent(self, new ImageComponent(List::get(self\assetList, 1)))

		;Resize Images
		ImageComponent::resize(Recast.ImageComponent(ProjectManager::getComponent(self, 0)), 380, 112)
		ImageComponent::resize(Recast.ImageComponent(ProjectManager::getComponent(self, 1)), 100, 67)
	End Method

	Method addComponent(comp.Component)
		if self\componentList = Null
			self\componentList = new List()
		end if

		List::push(self\componentList, comp)
	End Method

	Method getComponent.Component(index%)
		comp.Component = List::get(self\componentList, index)
		return comp
	End Method

	Method getProject.Project(dir$)
		Local count = List::getSize(self\recentProjectList) - 1
		for i = 0 to count
			Local prj.Project = List::get(self\recentProjectList, i)
			if (prj\rootDir = dir)
				return prj
			end if
		next
		return new Project(dir)
	End Method

	Method loadProject(prj.Project=Null)
		ProjectManager::showSplash(self)

		if (prj = Null)
			if ((NOT self\recentProjectList = Null) AND List::getSize(self\recentProjectList) > 0)
				prj = List::get(self\recentProjectList, 0)
			else
				prj = new Project(self\rootDir)
			end if
		end if

		if (NOT Project::verify(prj))
			if (NOT self\window = 0)
				FUI_CustomMessageBox("The selected directory does not contain a valid project.", "Error", MB_OK)
			else
				RuntimeError("Unable to start Project Manager. No valid project directories in recent.dat")
			end if
			return
		end if

		if (NOT self\prj = null)
			if(NOT self\recentProjectList = Null)
				self\recentProjectList = new List()
			end if

			if (List::getSize(self\recentProjectList) > 0)
				List::set(self\recentProjectList, 0, self\prj)
			else
				List::push(self\recentProjectList, self\prj)
			end if
		end if

		Project::load(prj)
		self\prj = prj
		
		if(NOT self\recentProjectList = Null)
			if (List::contains(self\recentProjectList, self\prj))
				List::remove(self\recentProjectList, self\prj)
			end if
		end if

		ProjectManager::saveRecentProjects(self)

		; Backwards compatibility
		if (NOT M_ProjectsRecent = 0) ProjectManager::buildRecentProjectMenu(self)
		if (NOT ProName = 0) FUI_SendMessage(ProName, M_SETCAPTION, self\prj\name)
		if (NOT GDIR = 0) FUI_SendMessage(GDIR, M_SETTEXT, self\prj\rootDir)
	End Method

	Method loadRecentProjects()
		if (self\recentProjectList = Null)
			self\recentProjectList = new List()
		else
			List::clear(self\recentProjectList)
		end if

		if (self\recentProjectFile = Null)
			self\recentProjectFile = FileSystem::safeGetFile(Null, self\rootDir + "res\Recent.dat")
		end if

		for i = 0 to 9
			Local dir$ = File::readLine(self\recentProjectFile)
			if (dir = "") Then Exit
			Local prj.Project = new Project(dir$)
			if (NOT Project::verify(prj)) 
				delete prj
			else
				List::push(self\recentProjectList, prj)
			end if
			if (File::isEnd(self\recentProjectFile)) Then Exit
		next

		File::close(self\recentProjectFile)
	End Method

	Method saveRecentProjects()
		if (self\recentProjectFile = Null)
			self\recentProjectFile = FileSystem::safeGetFile(Null, self\rootDir + "res\Recent.dat")
		end if

		File::writeLine(self\recentProjectFile, self\prj\rootDir)
		
		Local count = List::getSize(self\recentProjectList) - 1
		for i = 0 to count
			Local prj.Project = List::get(self\recentProjectList, i)
			File::writeLine(self\recentProjectFile, prj\rootDir)
		next

		File::close(self\recentProjectFile)
	End Method

	; Needs to be redone properly
	; Builds the old menu with the new list
	Method buildRecentProjectMenu()
		if (self\menuItemList = Null)
			self\menuItemList = new List()
		end if

		Local listCount = List::getSize(self\recentProjectList) - 1

		for i = 0 to listCount
			Local prj.Project = List::get(self\recentProjectList, i)

			if (i < List::getSize(self\menuItemList))
				Local comp.MenuItemComponent = List::get(self\menuItemList, i)
				MenuItemComponent::setText(comp, prj\rootDir)
			else
				List::push(self\menuItemList, new MenuItemComponent(M_ProjectsRecent, prj\rootDir))
			end if
		next
	End Method
End Type

pm.ProjectManager = new ProjectManager()

ProjectManager::init(pm)


; Backwards compatibility

Global Version$   = RCCEApp::version(pm)

Global LogMode = pm\debug



Global GUE_width  = pm\width
Global GUE_height = pm\height

Global RootDir$ = pm\rootDir


;Images
LogoTex.BBImage = ProjectManager::getImage(pm, 0)
LogoTex2.BBImage = ProjectManager::getImage(pm, 1)

;Folders
OMF$ = "Data\Meshes"
OTF$ = "Data\Textures"
OSF$ = "Data\Sounds"
OMuF$ = "Data\Music"
OSC$ = "Data\Server Data\Scripts"

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

B3D$ = RootDir$ + "compiler\BlitzForge\BlitzRC.exe"
BPS$ = RootDir$ + "compiler\BlitzPlus\BlitzPlus.exe"

;Main Window
WMain = pm\window

;Title Menu Bar
;Projects Tab
M_Projects = FUI_MenuTitle(WMain, "Projects")
M_ProjectsNew = FUI_MenuItem(M_Projects, "New Project")
M_ProjectsOpen = FUI_MenuItem(M_Projects, "Open Project")
Global M_ProjectsRecent = FUI_MenuItem(M_Projects, "Recent Projects")

ProjectManager::buildRecentProjectMenu(pm)

;Set Project
;setProject(GameDir$)

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
;BMINI = FUI_Button(WMain, 509, 1, 18, 18, "_")
;BCLOSE = FUI_Button(WMain, 529, 1, 18, 18, "X")

FUI_Label(WMain, GUE_width - 7, 22, Version$, ALIGN_RIGHT)

Global GDIR = FUI_Label(WMain, GUE_width - 7, GUE_height - 20, GameDir$, ALIGN_RIGHT)

;Tabs
TabMain = FUI_Tab(WMain, 0, 20, GUE_width, GUE_height - 20 - 20)
Global TProject = FUI_TabPage(TabMain, "Project")
Global TEngine = FUI_TabPage(TabMain, "Engine")
Global TSupport = FUI_TabPage(TabMain, "Support")

;Project Tab

;Global Project Properties
;Game Name
FUI_Label(TProject, 160 + 75, 32, "Project Name: ", ALIGN_RIGHT)
Global ProName = FUI_TextBox(TProject, 160 + 75, 27, 380 - 75, 25)

;Buttons
LET = FUI_GroupBox(TProject, 5, 20, 150, 120, "Game")
BCLI = FUI_Button(TProject, 15, 40, 62.5, 40, "Client")
BSER = FUI_Button(TProject, 82.5, 40, 62.5, 40, "Server")
BOPU = FUI_Button(TProject, 15, 85, 130, 45, "Publish")

LLG = FUI_GroupBox(TProject, 5, 140, 150, 100, "Logs")
BLC = FUI_Button(TProject, 15, 165, 130, 25, "Client Log")
BLS = FUI_Button(TProject, 15, 200, 130, 25, "Server Log")

LPF = FUI_GroupBox(TProject, 160, 140, 240, 100, "Project Folders")
BOPF = FUI_Button(TProject, 170, 165, 70.5, 25, "Project")
BOM = FUI_Button(TProject, 170, 200, 70.5, 25, "Meshes")
BOT = FUI_Button(TProject, 170 + 75.5, 165, 70.5, 25, "Textures")
BOS = FUI_Button(TProject, 170 + 75.5, 200, 70.5, 25, "Sound")
BOSM = FUI_Button(TProject, 170 + 75.5 + 75.5, 165, 70.5, 25, "Music")
BOSC = FUI_Button(TProject, 170 + 75.5 + 75.5, 200, 70.5, 25, "Scripts")

;Engine Tab
;Logo
FUI_ImageBox(TEngine, 160, 27, 380, 112, Ptr LogoTex)

;Editors
LED = FUI_GroupBox(TEngine, 5, 20, 150, 80, "Editors")
BGUE = FUI_Button(TEngine, 15, 40, 130, 50, "Game Unified Editor") 

LTK = FUI_GroupBox(TEngine, 160, 140, 240, 100, "Tool Kit")
TOOL1 = FUI_Button(TEngine, 170, 165, 70.5, 25, "Gubbin")
TOOL2 = FUI_Button(TEngine, 170, 200, 70.5, 25, "Architect")
TOOL3 = FUI_Button(TEngine, 170 + 75.5, 165, 70.5, 25, "Caves")
TOOL4 = FUI_Button(TEngine, 170 + 75.5, 200, 70.5, 25, "Rock")
TOOL5 = FUI_Button(TEngine, 170 + 75.5 + 75.5, 165, 70.5, 25, "Terrain")
TOOL6 = FUI_Button(TEngine, 170 + 75.5 + 75.5, 200, 70.5, 25, "Tree")

;Source
LTK = FUI_GroupBox(TEngine, 405, 140, 135, 100, "Source Tools")	
BB3D = FUI_Button(TEngine, 410, 165, 125, 25, "BlitzForge")
BBPS = FUI_Button(TEngine, 410, 200, 125, 25, "BlitzPlus")

if FileType(B3D$) = 0
	FUI_DisableGadget(BB3D)
EndIf

if FileType(BPS$) = 0
	FUI_DisableGadget(BBPS)
EndIf

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

; Setup dynamic values
FUI_SendMessage(ProName, M_SETCAPTION, GameName$)
	
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
			WriteLine F, UpdateGame$
			WriteLine F, UpdateMusic
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
				ProjectManager::loadProject(pm, ProjectManager::getProject(pm, app\currentFile))
				;setProject(app\currentFile)
			EndIf
		case M_ProjectsOpen
			If FileType(RootDir$ + "projects") <> 2
				CreateDir(RootDir$ + "projects")
			EndIf

			If FUI_CustomOpenDialog("Project Directory", RootDir$ + "projects", "Project Folder|\\", False, True)
				ProjectManager::loadProject(pm, ProjectManager::getProject(pm, app\currentFile))
				;setProject(app\currentFile)
			EndIf
	;Project Tab
		Case BGUE
			ExecFile(GUE$, "", GameDir$ + "Data\")
		Case BCLI
			ExecFile(CLI$, "", GameDir$ + "Data\")
		Case BSER
			ExecFile(SER$, "", GameDir$ + "Data\")
		Case BOPU
			GenerateFullInstall()
			GenerateServer()
		Case BOPF
			ExecFile(dq(GameDir$))
		Case BOM
			ExecFile(OMF$)
		Case BOT
			ExecFile(OTF$)
		Case BOS
			ExecFile(OSF$)
		Case BOSM
			ExecFile(OMuF$)
		Case BOSC
			ExecFile(dq(OSC$))
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
					ProjectManager::loadProject(pm, ProjectManager::getProject(pm, mi\caption$))
					;setProject(mi\caption$)
				EndIf
			EndIf
		EndIf

		Delete E
	Next
Until app\Quit = True

FUI_Destroy()
End