
Global rcceVersion$ = "2.0.0"
Global componentName$ = "gue"
Global RootDir$ = "..\"
Global LogMode = 1; (0 = standard logging, 1 = debug mode)

ChangeDir RootDir$

; Includes --------------------------------------------------------------------------------------------------------------------------

Include "Modules\RCEnet.bb"
Include "Modules\Media.bb"
Include "Modules\MediaDialogs.bb"
Include "Modules\Projectiles.bb"
Include "Modules\Language.bb"
Include "Modules\Items.bb"
Include "Modules\Inventories.bb"
Include "Modules\Animations.bb"
Include "Modules\Spells.bb"
Include "Modules\Actors.bb"
Include "Modules\CharacterEditorLoader.bb" ; RifRaf's character editor loading function
Include "Modules\Actors3D.bb"
Include "Modules\Environment.bb"
Include "Modules\Interface.bb"
Include "Modules\RCTrees.bb"
Include "Modules\ClientAreas.bb"
Include "Modules\ServerAreas.bb"
Include "Modules\RottParticles.bb"
Include "Modules\Packets.bb"
Include "Modules\F-UI.bb"
Include "Modules\Logging.bb"

; Globals ---------------------------------------------------------------------------------------------------------------------------

Global SpawnNum ;checks track of warning dialogs cysis145

Global LabelBuildFullInstallCalled = False
Global PreviousTab = 1
Global GameName$
; Delta timing
Dim DeltaBuffer(5)
For i = 0 To 5 : DeltaBuffer(i) = 35 : Next
Global FPS#, Delta#
Const BaseFramerate# = 30.0
; Saving
Global ItemsSaved = True, ActorsSaved = True, FactionsSaved = True, ParticlesSaved = True, DamageTypesSaved = True, ZoneSaved = True, AnimsSaved = True, StatsSaved = True
Global SpellsSaved = True, InterfaceSaved = True, ProjectilesSaved = True, EnvironmentSaved = True
Global ActorsChanged = False, AttributesChanged = False, FactionsChanged = False, AnimsChanged = False, ProjectilesChanged = False, ParticlesChanged = False
Global GubbinNamesChanged = True
; Zone entities
Dim PortalEN(99)
Dim TriggerEN(149)
Dim WaypointEN(1999)
Dim WaypointALink(1999)
Dim WaypointBLink(1999)

Type UpdateFile
	Field Name$, Checksum
End Type

; Undo/Redo for zones editor
Type Undo
	Field Action$
	Field Info, Info2
	Field InfoX#, InfoY#, InfoZ#
	Field WPS.UndoWaypointState
	Field ExtraInfo
End Type
Type UndoWaypointState
	Field PrevWaypoint[1999], NextWaypointA[1999], NextWaypointB[1999]
End Type

; Date
POMonth$ = Upper$(Mid$(CurrentDate$(), 4, 3))
POYear = Mid$(CurrentDate$(), 8)

; GUE startup log -------------------------------------------------------------------------------------------------------------------

Global GUELog = StartLog("GUE Log", False)
WriteLog(GUELog, "** GUE startup log begins **", True, True)

; Graphics mode ---------------------------------------------------------------------------------------------------------------------

;If (GetSystemMetrics(0) > 1024 And GetSystemMetrics(1) > 768) And Instr(Upper$(CommandLine$()), "-F") = 0
;	WriteLog(GUELog, "Initialising windowed graphics mode")
;    FUI_Initialise(1024, 768, 0, 2, False, True, "OverSeer")
;Else
;	WriteLog(GUELog, "Initialising fullscreen graphics mode")
;	FUI_Initialise(1024, 768, 0, 0, False, True, "OverSeer")
;EndIf

;Window or fullscreen for editor
;############################
Local GUE_width# = GetSystemMetrics(0) * 0.9
Local GUE_height# = GetSystemMetrics(1) * 0.8
If (GUE_width < 1280 And GUE_height< 960)
	GUE_width = 1280
	GUE_height = 960
EndIf
WriteLog(GUELog, "Initialising windowed graphics mode")
Graphics3D(GUE_width, GUE_height, 0, 2)
FUI_Initialise(GUE_width, GUE_height, 0, 2, True, True, "Realm Crafter Community Edition -" + rcceVersion)
SetBuffer(BackBuffer())

;FUI_RemoveBorder()

AppTitle("RCCE 2")
Global DefaultLight = CreateLight()
RotateEntity(DefaultLight, 30, 0, 0)
AmbientLight(100, 100, 100)

WriteLog(GUELog, "Initialising encryption system")

; Load data -------------------------------------------------------------------------------------------------------------------------

; Splash screen
WriteLog(GUELog, "Loading splash screen")
Global SplashImg = LoadImage("Data\GUE\Loading.PNG")
If SplashImg = 0 Then RuntimeError("Could not open Data\GUE\Loading.PNG!")
ResizeImage SplashImg, GUE_width, GUE_height

Function updateSplashScreen(loadingText$)
	WriteLog(GUELog, loadingText$)
	Color 255, 255, 255
	DrawImage(SplashImg, 0, 0)
	Text (ImageWidth(SplashImg)/2), (ImageHeight(SplashImg)/1.965), loadingText$, True, False
	Flip()
End Function

; Media dialogs
SetFont(app\fntWindow)
updateSplashScreen("Creating media dialogs")
InitMediaDialogs()

; Interface
updateSplashScreen("Loaded game interface settings")
ClearTextureFilters()
Result = LoadInterfaceSettings("Data\Game Data\Interface.dat")
If Result = False Then RuntimeError("Could not open Data\Game Data\Interface.dat!")
; Main screen components
Tex = LoadTexture("Data\GUE\Attribute Bar.bmp")
For i = 0 To 39
	AttributeDisplays(i)\Component = CreateQuad()
	EntityTexture(AttributeDisplays(i)\Component, Tex)
Next
FreeTexture(Tex)
Chat\Component = CreateQuad()
Tex = LoadTexture("Data\GUE\Chat Area.bmp") : EntityTexture(Chat\Component, Tex) : FreeTexture(Tex)
ChatEntry\Component = CreateQuad()
Tex = LoadTexture("Data\GUE\Chat Entry.bmp") : EntityTexture(ChatEntry\Component, Tex) : FreeTexture(Tex)
BuffsArea\Component = CreateQuad()
Tex = LoadTexture("Data\GUE\Buffs Area.bmp") : EntityTexture(BuffsArea\Component, Tex) : FreeTexture(Tex)
Radar\Component = CreateQuad()
Tex = LoadTexture("Data\GUE\Radar.bmp") : EntityTexture(Radar\Component, Tex) : FreeTexture(Tex)
Compass\Component = CreateQuad()
Tex = LoadTexture("Data\GUE\Compass.bmp") : EntityTexture(Compass\Component, Tex) : FreeTexture(Tex)

; Inventory components
InventoryWindow\Component = CreateQuad()
EntityOrder(InventoryWindow\Component, 1)
Tex = LoadTexture("Data\Textures\GUI\InventoryBG.png")
If Tex = 0 Then Tex = LoadTexture("Data\UI\Window.bmp")
EntityTexture(InventoryWindow\Component, Tex) : FreeTexture(Tex)
InventoryDrop\Component = CreateQuad()
Tex = LoadTexture("Data\GUE\Drop Button.bmp") : EntityTexture(InventoryDrop\Component, Tex) : FreeTexture(Tex)
InventoryEat\Component = CreateQuad()
Tex = LoadTexture("Data\GUE\Eat Button.bmp") : EntityTexture(InventoryEat\Component, Tex) : FreeTexture(Tex)
InventoryGold\Component = CreateQuad()
Tex = LoadTexture("Data\GUE\Gold.bmp") : EntityTexture(InventoryGold\Component, Tex) : FreeTexture(Tex)
InventoryButtons(0)\Component = CreateQuad()
For i = 1 To Slots_Inventory
	InventoryButtons(i)\Component = CopyEntity(InventoryButtons(0)\Component)
Next
Tex = LoadTexture("Data\Textures\GUI\Weapon.bmp", 4) : AddBorders(Tex) : EntityTexture InventoryButtons(SlotI_Weapon)\Component, Tex : FreeTexture Tex
Tex = LoadTexture("Data\Textures\GUI\Shield.bmp", 4) : AddBorders(Tex) : EntityTexture InventoryButtons(SlotI_Shield)\Component, Tex : FreeTexture Tex
Tex = LoadTexture("Data\Textures\GUI\Hat.bmp", 4)    : AddBorders(Tex) : EntityTexture InventoryButtons(SlotI_Hat)\Component, Tex    : FreeTexture Tex
Tex = LoadTexture("Data\Textures\GUI\Chest.bmp", 4)  : AddBorders(Tex) : EntityTexture InventoryButtons(SlotI_Chest)\Component, Tex  : FreeTexture Tex
Tex = LoadTexture("Data\Textures\GUI\Hand.bmp", 4)   : AddBorders(Tex) : EntityTexture InventoryButtons(SlotI_Hand)\Component, Tex   : FreeTexture Tex
Tex = LoadTexture("Data\Textures\GUI\Belt.bmp", 4)   : AddBorders(Tex) : EntityTexture InventoryButtons(SlotI_Belt)\Component, Tex   : FreeTexture Tex
Tex = LoadTexture("Data\Textures\GUI\Legs.bmp", 4)   : AddBorders(Tex) : EntityTexture InventoryButtons(SlotI_Legs)\Component, Tex   : FreeTexture Tex
Tex = LoadTexture("Data\Textures\GUI\Feet.bmp", 4)   : AddBorders(Tex) : EntityTexture InventoryButtons(SlotI_Feet)\Component, Tex   : FreeTexture Tex
Tex = LoadTexture("Data\Textures\GUI\Ring.bmp", 4)
AddBorders(Tex)
EntityTexture InventoryButtons(SlotI_Ring1)\Component, Tex
EntityTexture InventoryButtons(SlotI_Ring2)\Component, Tex
EntityTexture InventoryButtons(SlotI_Ring3)\Component, Tex
EntityTexture InventoryButtons(SlotI_Ring4)\Component, Tex
FreeTexture(Tex)
Tex = LoadTexture("Data\Textures\GUI\Amulet.bmp", 4)
AddBorders(Tex)
EntityTexture InventoryButtons(SlotI_Amulet1)\Component, Tex
EntityTexture InventoryButtons(SlotI_Amulet2)\Component, Tex
FreeTexture(Tex)
Tex = LoadTexture("Data\Textures\GUI\Backpack.bmp", 4)
AddBorders(Tex)
For i = SlotI_Backpack To Slots_Inventory
	EntityTexture InventoryButtons(i)\Component, Tex
Next
FreeTexture(Tex)
TextureFilter("", 1 + 8)

;Login Screen Components [255]
;LoginWindow\Component = CreateQuad()
;EntityOrder(LoginWindow\Component, 1)
;Tex = LoadTexture("Data\Textures\Menu\Login.PNG") : EntityTexture(LoginWindow\Component, Tex) : FreeTexture(Tex)
;LoginUsername\Component = CreateQuad()
;Tex = LoadTexture("Data\Textures\Menu\BCharBoxD.PNG") : EntityTexture(LoginUsername\Component, Tex) : FreeTexture(Tex)
;LoginPassword\Component = CreateQuad()
;Tex = LoadTexture("Data\Textures\Menu\BCharBoxD.PNG") : EntityTexture(LoginPassword\Component, Tex) : FreeTexture(Tex)
;LoginNewAccount\Component = CreateQuad()
;Tex = LoadTexture("Data\Textures\Menu\BNewAccountD.PNG") : EntityTexture(LoginNewAccount\Component, Tex) : FreeTexture(Tex)
;LoginLogIn\Component = CreateQuad()
;Tex = LoadTexture("Data\Textures\Menu\BLoginD.PNG") : EntityTexture(LoginLogIn\Component, Tex) : FreeTexture(Tex)
;LoginGraphic\Component = CreateQuad()
;Tex = LoadTexture("Data\Textures\Menu\BGraphicsD.PNG") : EntityTexture(LoginGraphic\Component, Tex) : FreeTexture(Tex)
;LoginControl\Component = CreateQuad()
;Tex = LoadTexture("Data\Textures\Menu\BControlD.PNG") : EntityTexture(LoginControl\Component, Tex) : FreeTexture(Tex)
;LoginSound\Component = CreateQuad()
;Tex = LoadTexture("Data\Textures\Menu\BOtherD.PNG") : EntityTexture(LoginSound\Component, Tex) : FreeTexture(Tex)
;LoginClose\Component = CreateQuad()
;Tex = LoadTexture("Data\Textures\Menu\BQuitD.PNG") : EntityTexture(LoginClose\Component, Tex) : FreeTexture(Tex)
;LoginServerStat\Component = CreateQuad()
;Tex = LoadTexture("Data\Textures\Menu\BCharBoxD.PNG") : EntityTexture(LoginServerStat\Component, Tex) : FreeTexture(Tex)

; All emitter configs
updateSplashScreen("Loading emitter configurations")
DefaultTex = LoadTexture("Data\DefaultParticle.bmp", 4 + 16 + 32)
D = ReadDir("Data\Emitter Configs")
 File$ = NextFile$(D)
 While File$ <> ""
  If FileType("Data\Emitter Configs\" + File$) = 1
   ConfigID = RP_LoadEmitterConfig("Data\Emitter Configs\" + File$, DefaultTex, app\Cam)
   C.RP_EmitterConfig = Object.RP_EmitterConfig(ConfigID)
   C\ConfigID = ConfigID
   If C\DefaultTextureID < 65535
    Tex = GetTexture(C\DefaultTextureID, True)
    UnloadTexture(C\DefaultTextureID)
    If Tex <> 0 Then C\Texture = Tex
   EndIf
  EndIf
  File$ = NextFile$(D)
 Wend
CloseDir(D)

; Misc options
F = ReadFile("Data\Game Data\Misc.dat")
If F = 0 Then RuntimeError("Could not open Data\Game Data\Misc.dat!")
	GameName$ = ReadLine$(F)
CloseFile(F)

;Title +Project name
;#########
FUI_AppTitle("RealmCrafter: Community Edition" + " - " + GameName$)
;#########

F = ReadFile("Data\Game Data\Hosts.dat")
If F = 0 Then RuntimeError("Could not open Data\Game Data\Hosts.dat!")
	ServerHost$ = ReadLine$(F)
	UpdatesHost$ = ReadLine$(F)
CloseFile(F)

; Load actors, items, etc.
updateSplashScreen("Loading damage types")
Result = LoadDamageTypes("Data\Server Data\Damage.dat")
If Result = False Then RuntimeError("Could not open Data\Server Data\Damage.dat!")
updateSplashScreen("Loading attributes")
Result = LoadAttributes("Data\Server Data\Attributes.dat")
If Result = False Then RuntimeError("Could not open Data\Server Data\Attributes.dat!")
updateSplashScreen("Loading factions")
Result = LoadFactions("Data\Server Data\Factions.dat")
If Result = -1 Then RuntimeError("Could not open Data\Server Data\Factions.dat!")
updateSplashScreen("Loading animations")
Result = LoadAnimSets("Data\Game Data\Animations.dat")
If Result = -1 Then RuntimeError("Could not open Data\Game Data\Animations.dat!")
updateSplashScreen("Loading projectiles")
Global TotalProjectiles = LoadProjectiles("Data\Server Data\Projectiles.dat")
If TotalProjectiles = -1 Then RuntimeError("Could not open Data\Server Data\Projectiles.dat!")
updateSplashScreen("Loading items")
Global TotalItems = LoadItems("Data\Server Data\Items.dat")
If TotalItems = -1 Then RuntimeError("Could not open Data\Server Data\Items.dat!")
updateSplashScreen("Loading actors")
Global TotalActors = LoadActors("Data\Server Data\Actors.dat")
If TotalActors = -1 Then RuntimeError("Could not open Data\Server Data\Actors.dat!")
updateSplashScreen("Loading abilities")
Global TotalSpells = LoadSpells("Data\Server Data\Spells.dat")
If TotalSpells = -1 Then RuntimeError("Could not open Data\Server Data\Spells.dat!")

; Load zones (just the server side bits, client side parts are loaded on request to save video memory)
updateSplashScreen("Loading server-side zones")
Global TotalZones = 0
D = ReadDir("Data\Server Data\Areas")
File$ = NextFile$(D)
While File$ <> ""
	If FileType("Data\Server Data\Areas\" + File$) = 1 And Len(File$) > 4
		ServerLoadArea(Left$(File$, Len(File$) - 4))
		TotalZones = TotalZones + 1
	EndIf
	File$ = NextFile$(D)
Wend
CloseDir(D)

; Load environment
WriteLog(GUELog, "Loading environment settings")
Result = LoadEnvironment()
If Result = False Then RuntimeError("Could not open Data\Server Data\Environment.dat!")
WriteLog(GUELog, "Loading suns/moons")
LoadSuns()

; Initialise GUI --------------------------------------------------------------------------------------------------------------------

updateSplashScreen("Created GUI")

; Texture filters
TextureFilter "m_", 1 + 4
TextureFilter "a_", 1 + 2

; Borders
;I removed these since they were horrible... Cysis
;WriteLog(GUELog, "Creating border meshes")
;TopEN = CreateQuad(app\Cam)
;PositionEntity(TopEN, -10.0, 7.5, 10.0) : ScaleEntity(TopEN, 20.0, 0.9765625, 1.0)
;Tex = LoadTexture("Data\GUE\Top.PNG", 16 + 32) : EntityTexture TopEN, Tex : FreeTexture Tex
;BottomEN = CreateQuad(app\Cam)
;PositionEntity(BottomEN, -10.0, -7.01171875, 10.0)
;ScaleEntity(BottomEN, 20.0, 0.48828125, 1.0)
;Tex = LoadTexture("Data\GUE\Bottom.PNG", 16 + 32) : EntityTexture BottomEN, Tex : FreeTexture Tex
;LeftEN = CreateQuad(app\Cam)
;PositionEntity(LeftEN, -10.0, 6.5234375, 10.0)
;ScaleEntity(LeftEN, 0.48828125, 13.53515625, 1.0)
;Tex = LoadTexture("Data\GUE\Left.PNG", 16 + 32) : EntityTexture LeftEN, Tex : FreeTexture Tex
;RightEN = CreateQuad(app\Cam)
;PositionEntity(RightEN, 9.51171875, 6.5234375, 10.0)
;ScaleEntity(RightEN, 0.48828125, 13.53515625, 1.0)
;Tex = LoadTexture("Data\GUE\Right.PNG", 16 + 32) : EntityTexture RightEN, Tex : FreeTexture Tex

; Main tab strip
WriteLog(GUELog, "Creating main window")

WMain = FUI_Window(0, 0, GUE_width, GUE_height, "", "", 0, 0)
; Top Menu Bar Edit Cysis145
mnuFile							= FUI_MenuTitle( WMain, "File" )
mnuFileSaveAll					= FUI_MenuItem( mnuFile, "Save All", "Ctrl + S")
FUI_MenuBar mnuFile
mnuFileExit						= FUI_MenuItem( mnuFile, "Exit" )

TabMain = FUI_Tab(WMain, 0, 20, GUE_width, GUE_height)
helpMenu					= FUI_MenuTitle(WMain, "Help")
helpForum					= FUI_MenuItem(helpMenu, "RCCE Forum")

;End Top Menu Bar.

Global TProject     = FUI_TabPage(TabMain, "Project")
Global TMedia       = FUI_TabPage(TabMain, "Media")
Global TParticles   = FUI_TabPage(TabMain, "Particles")
Global TDamageTypes = FUI_TabPage(TabMain, "Combat")
Global TProjectiles = FUI_TabPage(TabMain, "Projectiles")
Global TFactions    = FUI_TabPage(TabMain, "Factions")
Global TAnimSets    = FUI_TabPage(TabMain, "Animations")
Global TAttributes  = FUI_TabPage(TabMain, "Attributes")
Global TActors      = FUI_TabPage(TabMain, "Actors")
Global TItems       = FUI_TabPage(TabMain, "Items")
Global TSeasons     = FUI_TabPage(TabMain, "Days & seasons")
Global TZones       = FUI_TabPage(TabMain, "Zones")
Global TSpells      = FUI_TabPage(TabMain, "Abilities")
Global TInterface   = FUI_TabPage(TabMain, "Interface")
Global TOther       = FUI_TabPage(TabMain, "Other")

; Project gadgets -------------------------------------------------------------------------------------------------------------------

updateSplashScreen("Creating project tab")

FUI_Label(TProject, GUE_width/2, 20, "Welcome to Realm Crafter Community Edition!", ALIGN_CENTER)
FUI_Label(TProject, GUE_width/2, 60, GameName$, ALIGN_CENTER)

;BRestoreLanguageFile = FUI_Button(TProject, 10, 240, 130, 25, "Restore Language File")

; Media gadgets ---------------------------------------------------------------------------------------------------------------------

updateSplashScreen("Creating media tab")

Global CMediaType = FUI_ComboBox(TMedia, 20, 20, 250, 20)
FUI_ComboBoxItem(CMediaType, "View 3D Meshes")
FUI_ComboBoxItem(CMediaType, "View Textures")
FUI_ComboBoxItem(CMediaType, "View Sounds")
FUI_ComboBoxItem(CMediaType, "View Music")
BMediaAdd = FUI_Button(TMedia, 20, 50, 100, 20, "Add New File")
BMediaDelete = FUI_Button(TMedia, 160, 50, 100, 20, "Remove File")

FUI_GroupBox(TMedia, 290, 20, GUE_width - 310, GUE_height - 76 - 20, "Media Preview")
Global LMediaPreview = FUI_Label(TMedia, GUE_width/2 + 135, GUE_height - 100, "Media ID: 0", ALIGN_CENTER)

Global LMediaFolder = FUI_ListBox(TMedia, 20, 80, 250, 150)

Global LMedia = FUI_ListBox(TMedia, 20, 235, 250, GUE_height - 291 - 20)

FillMeshesList(LMedia, "", MeshDialog_All)
FillMeshesFolderList(LMediaFolder, "")
Global MediaFolder$

; Preview gadgets
Global VMediaPreview = FUI_View(TMedia, 300, 40, GUE_width - 330, GUE_height - 150, 0, 0, 0)
Global TMediaMeshScale, LMediaMeshScale
Global MediaPreviewChannel, MediaPreviewSoundID
Global BMediaPreviewPlay = FUI_Button(TMedia, 310, 50, 60, 20, "Play")
Global BMediaPreviewStop = FUI_Button(TMedia, 380, 50, 60, 20, "Stop")
Global LMediaPreviewVolume = FUI_Label(TMedia, 310, 82, "Volume:")
Global SMediaPreviewVolume = FUI_Slider(TMedia, 360, 80, 350, 20, 0.0, 1.0, 0.75)
Global MediaPreviewMesh
Global MediaPreviewQuad = CreateQuad()
ScaleMesh(MediaPreviewQuad, 20, 20, 1)
PositionMesh(MediaPreviewQuad, -10, 10, 0)
PositionEntity(MediaPreviewQuad, 0, 0, 10)
EntityFX(MediaPreviewQuad, 1)
HideEntity(MediaPreviewQuad)
; Start on meshes
SetMediaType(1)

; Particles gadgets -----------------------------------------------------------------------------------------------------------------

updateSplashScreen("Creating particles tab")

; Preview view
View = FUI_View(TParticles, 20, 20, GUE_width - 424, GUE_height - 318, 0, 0, 0)

Global ParticlesCam = FUI_SendMessage(View, M_GETCAMERA)
CameraRange ParticlesCam, 0.5, 800.0
Global ParticlesCamBGR = 0
Global ParticlesCamBGG = 0
Global ParticlesCamBGB = 0

; Set camera clear color using red, green, blue values
CameraClsColor ParticlesCam, ParticlesCamBGR, ParticlesCamBGG, ParticlesCamBGB

;new
Global LActiveParticles = FUI_Label(TParticles, 20, GUE_height - 293, "Active particles: 0")
; Buttons
BParticlesNew  = FUI_Button(TParticles, GUE_width - 394, 170, 90, 20, "New emitter")
BParticlesSave = FUI_Button(TParticles, GUE_width - 298, 170, 90, 20, "Save emitters")
BParticlesDelete = FUI_Button(TParticles, GUE_width - 174, 170, 90, 20, "Delete emitter")

; Configs list
FUI_Label(TParticles, GUE_width - 394, 127, "Current emitter:")
Global CParticleConfigs = FUI_ComboBox(TParticles, GUE_width - 394, 145, 310, 20, 20)

;###^

For C.RP_EmitterConfig = Each RP_EmitterConfig
	Item = FUI_ComboBoxItem(CParticleConfigs, C\Name$) : FUI_SendMessage(Item, M_SETDATA, Handle(C))
Next
FUI_SendMessage(CParticleConfigs, M_SETINDEX, 1)

; Preview buttons
BParticlesTex = FUI_Button(TParticles, GUE_width - 374, 20, 100, 20, "Preview texture")
BParticlesPreviewReset = FUI_Button(TParticles, GUE_width - 224, 20, 100, 20, "Reset preview")
BParticlesPreviewL = FUI_Button(TParticles, GUE_width - 374, 75, 20, 20, "", "Data\GUE\L.png")
BParticlesPreviewR = FUI_Button(TParticles, GUE_width - 314, 75, 20, 20, "", "Data\GUE\R.png")
BParticlesPreviewU = FUI_Button(TParticles, GUE_width - 344, 55, 20, 20, "", "Data\GUE\U.png")
BParticlesPreviewD = FUI_Button(TParticles, GUE_width - 344, 95, 20, 20, "", "Data\GUE\D.png")
BParticlesPreviewIn = FUI_Button(TParticles, GUE_width - 274, 55, 20, 20, "", "Data\GUE\In.png")
BParticlesPreviewOut = FUI_Button(TParticles, GUE_width - 274, 95, 20, 20, "", "Data\GUE\Out.png")

FUI_Label(TParticles, GUE_width - 225, 50, "Preview Window Color:")
FUI_Label(TParticles, GUE_width - 225, 70, "R:")
FUI_Label(TParticles, GUE_width - 225, 95, "G:")
FUI_Label(TParticles, GUE_width - 225, 120, "B:")
Global SParticlesCamBGR = FUI_Slider(TParticles, GUE_width - 200, 68, 100, 15, 0, 255, 0)
Global SParticlesCamBGG = FUI_Slider(TParticles, GUE_width - 200, 93, 100, 15, 0, 255, 0)
Global SParticlesCamBGB = FUI_Slider(TParticles, GUE_width - 200, 118, 100, 15, 0, 255, 0)
CameraClsColor ParticlesCam, FUI_SendMessage(SParticlesCamBGR, M_GETVALUE), FUI_SendMessage(SParticlesCamBGG, M_GETVALUE), FUI_SendMessage(SParticlesCamBGB, M_GETVALUE)

Global ParticlesPreviewPitch#, ParticlesPreviewYaw#, ParticlesPreviewDistance# = 20.0
; General options
;old
;TabParticles = FUI_Tab(TParticles, 630, 200, 330, 270)
;new
;TabParticles = FUI_Tab(TParticles, GUE_width - 394, 200, 330, 270)

;TParticlesGeneral = FUI_TabPage(TabParticles, "General settings")
;TParticlesColours = FUI_TabPage(TabParticles, "Colouring")

TParticlesGeneral = FUI_GroupBox(TParticles, GUE_width - 394, 200, 310, 270, "General settings")
FUI_Label(TParticlesGeneral, 10, 12, "Maximum particles:")
Global SParticlesMax = FUI_Spinner(TParticlesGeneral, 210, 10, 90, 20, 1, 10000, 200, 1, DTYPE_INTEGER)
FUI_Label(TParticlesGeneral, 10, 42, "Spawn rate:")
Global SParticlesRate = FUI_Spinner(TParticlesGeneral, 210, 40, 90, 20, 1, 200, 2, 1, DTYPE_INTEGER)
FUI_Label(TParticlesGeneral, 10, 72, "Particle lifespan:")
Global SParticlesLife = FUI_Spinner(TParticlesGeneral, 210, 70, 90, 20, 1, 1000, 100, 1, DTYPE_INTEGER)
FUI_Label(TParticlesGeneral, 10, 102, "Initial size:")
Global SParticlesSize = FUI_Spinner(TParticlesGeneral, 210, 100, 90, 20, 0.01, 100.0, 1.0, 0.01, DTYPE_FLOAT)
FUI_Label(TParticlesGeneral, 10, 132, "Size change:")
Global SParticlesSizeChange = FUI_Spinner(TParticlesGeneral, 210, 130, 90, 20, -10.0, 10.0, 0.0, 0.01, DTYPE_FLOAT)
FUI_Label(TParticlesGeneral, 10, 162, "Blend mode:")
Global CParticlesBlend = FUI_ComboBox(TParticlesGeneral, 210, 160, 90, 20)
FUI_ComboBoxItem(CParticlesBlend, "Normal")
FUI_ComboBoxItem(CParticlesBlend, "Multiply")
FUI_ComboBoxItem(CParticlesBlend, "Add")
FUI_Label(TParticlesGeneral, 10, 192, "Initial alpha:")
Global SParticlesAlpha = FUI_Spinner(TParticlesGeneral, 210, 190, 90, 20, 0.0, 1.0, 1.0, 0.001, DTYPE_FLOAT)
FUI_Label(TParticlesGeneral, 10, 222, "Alpha change:")
Global SParticlesAlphaChange = FUI_Spinner(TParticlesGeneral, 210, 220, 90, 20, -1.0, 1.0, 0.0, 0.001, DTYPE_FLOAT)

TParticlesColours = FUI_GroupBox(TParticles, GUE_width - 394, 480, 310, 210, "Colouring")
FUI_Label(TParticlesColours, 10, 12, "Initial red:")
Global SParticlesStartR = FUI_Spinner(TParticlesColours, 210, 10, 90, 20, 0, 255, 0, 1, DTYPE_INTEGER)
FUI_Label(TParticlesColours, 10, 42, "Initial green:")
Global SParticlesStartG = FUI_Spinner(TParticlesColours, 210, 40, 90, 20, 0, 255, 0, 1, DTYPE_INTEGER)
FUI_Label(TParticlesColours, 10, 72, "Initial blue:")
Global SParticlesStartB = FUI_Spinner(TParticlesColours, 210, 70, 90, 20, 0, 255, 0, 1, DTYPE_INTEGER)
FUI_Label(TParticlesColours, 10, 102, "Red change:")
Global SParticlesChangeR = FUI_Spinner(TParticlesColours, 210, 100, 90, 20, -50.0, 50.0, 0, 0.5, DTYPE_FLOAT)
FUI_Label(TParticlesColours, 10, 132, "Green change:")
Global SParticlesChangeG = FUI_Spinner(TParticlesColours, 210, 130, 90, 20, -50.0, 50.0, 0, 0.5, DTYPE_FLOAT)
FUI_Label(TParticlesColours, 10, 162, "Blue change:")
Global SParticlesChangeB = FUI_Spinner(TParticlesColours, 210, 160, 90, 20, -50.0, 50.0, 0, 0.5, DTYPE_FLOAT)

; Texture options
GTexture = FUI_GroupBox(TParticles, 10, GUE_height - 273, 170, 190, "Animated texture options")

FUI_Label(GTexture, 10, 12, "Frames across:")
Global SParticlesTexAcross = FUI_Spinner(GTexture, 100, 10, 60, 20, 1, 50, 1, 1, DTYPE_INTEGER)
FUI_Label(GTexture, 10, 42, "Frames down:")
Global SParticlesTexDown = FUI_Spinner(GTexture, 100, 40, 60, 20, 1, 50, 1, 1, DTYPE_INTEGER)
FUI_Label(GTexture, 10, 72, "Animation speed:")
Global SParticlesTexSpeed = FUI_Spinner(GTexture, 100, 70, 60, 20, 0, 100, 1, 1, DTYPE_INTEGER, "%")
Global BParticlesRandFrame = FUI_CheckBox(GTexture, 10, 102, "Start on random frame")

; Shape options
GShape = FUI_GroupBox(TParticles, 190, GUE_height - 273, 310, 190, "Shape options")

FUI_Label(GShape, 10, 12, "Emitter shape:")
Global CParticlesShape = FUI_ComboBox(GShape, 150, 10, 150, 20)
FUI_ComboBoxItem(CParticlesShape, "Sphere")
FUI_ComboBoxItem(CParticlesShape, "Cylinder")
FUI_ComboBoxItem(CParticlesShape, "Box")
FUI_Label(GShape, 10, 42, "Cylinder axis:")
Global CParticlesAxis = FUI_ComboBox(GShape, 150, 40, 150, 20)
FUI_ComboBoxItem(CParticlesAxis, "X axis")
FUI_ComboBoxItem(CParticlesAxis, "Y axis")
FUI_ComboBoxItem(CParticlesAxis, "Z axis")
FUI_Label(GShape, 10, 72, "Width:")
Global SParticlesWidth = FUI_Spinner(GShape, 50, 70, 60, 20, 0.0, 500.0, 0.0, 0.01, DTYPE_FLOAT)
FUI_Label(GShape, 10, 102, "Height:")
Global SParticlesHeight = FUI_Spinner(GShape, 50, 100, 60, 20, 0.0, 500.0, 0.0, 0.01, DTYPE_FLOAT)
FUI_Label(GShape, 10, 132, "Depth:")
Global SParticlesDepth = FUI_Spinner(GShape, 50, 130, 60, 20, 0.0, 500.0, 0.0, 0.01, DTYPE_FLOAT)
FUI_Label(GShape, 170, 72, "Inner radius:")
Global SParticlesMinRadius = FUI_Spinner(GShape, 240, 70, 60, 20, 0.0, 500.0, 0.0, 0.01, DTYPE_FLOAT)
FUI_Label(GShape, 170, 102, "Outer radius:")
Global SParticlesMaxRadius = FUI_Spinner(GShape, 240, 100, 60, 20, 0.1, 500.0, 10.0, 0.01, DTYPE_FLOAT)

; Velocity/force options
;old
;TabParticles = FUI_Tab(TParticles, 510, 480, 450, 185)
;new
;TabParticles = FUI_Tab(TParticles, 510, GUE_height - 288, 450, 185)

;TParticlesVelocity = FUI_TabPage(TabParticles, "Velocity")
;TParticlesForce = FUI_TabPage(TabParticles, "Forces")

TParticlesVelocity = FUI_GroupBox(TParticles, 510, GUE_height - 273, 330, 190, "Velocity")

FUI_Label(TParticlesVelocity, 10, 22, "Velocity shaping:")
Global CParticlesVelocityShape = FUI_ComboBox(TParticlesVelocity, 100, 20, 150, 20)
FUI_ComboBoxItem(CParticlesVelocityShape, "None")
FUI_ComboBoxItem(CParticlesVelocityShape, "Shaped")
FUI_ComboBoxItem(CParticlesVelocityShape, "Strictly shaped")
FUI_Label(TParticlesVelocity, 10, 62, "X velocity:")
Global SParticlesVelocityX = FUI_Spinner(TParticlesVelocity, 70, 60, 60, 20, -500.0, 500.0, 0.0, 0.01, DTYPE_FLOAT)
FUI_Label(TParticlesVelocity, 10, 92, "Y velocity:")
Global SParticlesVelocityY = FUI_Spinner(TParticlesVelocity, 70, 90, 60, 20, -500.0, 500.0, 0.0, 0.01, DTYPE_FLOAT)
FUI_Label(TParticlesVelocity, 10, 122, "Z velocity:")
Global SParticlesVelocityZ = FUI_Spinner(TParticlesVelocity, 70, 120, 60, 20, -500.0, 500.0, 0.0, 0.01, DTYPE_FLOAT)
FUI_Label(TParticlesVelocity, 170, 62, "X randomisation:")
Global SParticlesVelocityRX = FUI_Spinner(TParticlesVelocity, 260, 60, 60, 20, -500.0, 500.0, 0.0, 0.01, DTYPE_FLOAT)
FUI_Label(TParticlesVelocity, 170, 92, "Y randomisation:")
Global SParticlesVelocityRY = FUI_Spinner(TParticlesVelocity, 260, 90, 60, 20, -500.0, 500.0, 0.0, 0.01, DTYPE_FLOAT)
FUI_Label(TParticlesVelocity, 170, 122, "Z randomisation:")
Global SParticlesVelocityRZ = FUI_Spinner(TParticlesVelocity, 260, 120, 60, 20, -500.0, 500.0, 0.0, 0.01, DTYPE_FLOAT)

TParticlesForce = FUI_GroupBox(TParticles, 850, GUE_height - 273, 400, 190, "Forces")

FUI_Label(TParticlesForce, 10, 22, "Force shaping:")
Global CParticlesForceShape = FUI_ComboBox(TParticlesForce, 100, 20, 150, 20)
FUI_ComboBoxItem(CParticlesForceShape, "Linear")
FUI_ComboBoxItem(CParticlesForceShape, "Spherical")
FUI_Label(TParticlesForce, 10, 62, "X force:")
Global SParticlesForceX = FUI_Spinner(TParticlesForce, 70, 60, 90, 20, -500.0, 500.0, 0.0, 0.001, DTYPE_FLOAT)
FUI_Label(TParticlesForce, 10, 92, "Y force:")
Global SParticlesForceY = FUI_Spinner(TParticlesForce, 70, 90, 90, 20, -500.0, 500.0, 0.0, 0.001, DTYPE_FLOAT)
FUI_Label(TParticlesForce, 10, 122, "Z force:")
Global SParticlesForceZ = FUI_Spinner(TParticlesForce, 70, 120, 90, 20, -500.0, 500.0, 0.0, 0.001, DTYPE_FLOAT)
FUI_Label(TParticlesForce, 200, 62, "X force modifier:")
Global SParticlesForceModX = FUI_Spinner(TParticlesForce, 290, 60, 90, 20, -500.0, 500.0, 0.0, 0.001, DTYPE_FLOAT)
FUI_Label(TParticlesForce, 200, 92, "Y force modifier:")
Global SParticlesForceModY = FUI_Spinner(TParticlesForce, 290, 90, 90, 20, -500.0, 500.0, 0.0, 0.001, DTYPE_FLOAT)
FUI_Label(TParticlesForce, 200, 122, "Z force modifier:")
Global SParticlesForceModZ = FUI_Spinner(TParticlesForce, 290, 120, 90, 20, -500.0, 500.0, 0.0, 0.001, DTYPE_FLOAT)

; Preview emitter
Global ParticlesEmitter, ParticlesConfig
UpdateParticlesPreview()
UpdateParticlesPreviewCam()
If ParticlesEmitter <> 0 Then RP_HideEmitter(ParticlesEmitter)

; Combat gadgets --------------------------------------------------------------------------------------------------------------------

updateSplashScreen("Creating combat tab")

BDamageTypesSave = FUI_Button(TDamageTypes, 20, 20, 110, 20, "Save damage types")
FUI_Label(TDamageTypes, 20, 70, "You may specify up to 20 damage types below:")
Dim TDamageType(19)
For i = 0 To 19
	TDamageType(i) = FUI_TextBox(TDamageTypes, 20, 110 + (i * 25), 250, 20)
	If DamageTypes$(i) <> ""
		FUI_SendMessage(TDamageType(i), M_SETCAPTION, DamageTypes$(i))
	EndIf
Next

F = ReadFile("Data\Server Data\Misc.dat")
If F = 0 Then RuntimeError("Could not open Data\Server Data\Misc.dat!")
	SeekFile F, 9
	CombatDelay = ReadShort(F)
	CombatFormula = ReadByte(F)
	WeaponDamage = ReadByte(F)
	ArmourDamage = ReadByte(F)
	CombatRatingAdjust = ReadByte(F)
CloseFile(F)
G = FUI_GroupBox(TDamageTypes, 350, 70, 460, 220, "Combat Options")
FUI_Label(G, 10, 17, "Combat delay: ")
SCombatDelay = FUI_Spinner(G, 90, 15, 140, 20, 0, 20000, CombatDelay, 1, DTYPE_INTEGER, " milliseconds")
BDamageWeapon = FUI_CheckBox(G, 10, 47, "Damage weapons when used")
FUI_SendMessage(BDamageWeapon, M_SETCHECKED, WeaponDamage)
BDamageArmour = FUI_CheckBox(G, 10, 77, "Damage armour when hit")
FUI_SendMessage(BDamageArmour, M_SETCHECKED, ArmourDamage)
FUI_Label(G, 10, 107, "Combat formula:")
CCombatFormula = FUI_ComboBox(G, 100, 105, 350, 20)
FUI_ComboBoxItem(CCombatFormula, "Normal formula")
FUI_ComboBoxItem(CCombatFormula, "No strength bonus or penalty")
FUI_ComboBoxItem(CCombatFormula, "High damage, high defence")
FUI_ComboBoxItem(CCombatFormula, "Use the Attack script (Advanced)")
FUI_SendMessage(CCombatFormula, M_SETINDEX, CombatFormula)
FUI_Label(G, 10, 142, "Damage information:")
CCombatInfoStyle = FUI_ComboBox(G, 120, 140, 110, 20)
FUI_ComboBoxItem(CCombatInfoStyle, "None")
FUI_ComboBoxItem(CCombatInfoStyle, "Chat message")
FUI_ComboBoxItem(CCombatInfoStyle, "Floating number")
F = ReadFile("Data\Game Data\Combat.dat")
If F = 0 Then RuntimeError("Could not open Data\Game Data\Combat.dat!")
	SeekFile F, 2
	CombatInfoStyle = ReadShort(F)
CloseFile(F)
FUI_SendMessage(CCombatInfoStyle, M_SETINDEX, CombatInfoStyle)
FUI_Label(G, 10, 172, "Faction rating hit after kill:")
SCombatRatingAdjust = FUI_Spinner(G, 150, 170, 70, 20, 0, 5, CombatRatingAdjust, 1, DTYPE_INTEGER, "%")

; Projectiles gadgets ---------------------------------------------------------------------------------------------------------------

updateSplashScreen("Creating projectiles tab")

; Main
BProjNew    = FUI_Button(TProjectiles, 20, 20, 100, 20, "New projectile")
BProjCopy   = FUI_Button(TProjectiles, 150, 20, 100, 20, "Copy projectile")
BProjDelete = FUI_Button(TProjectiles, 280, 20, 100, 20, "Delete projectile")
BProjSave   = FUI_Button(TProjectiles, 410, 20, 100, 20, "Save projectiles")
Global SelectedProj.Projectile
; Selection
FUI_Label(TProjectiles, 20, 62, "Current projectile:")
Global CProjSelected = FUI_ComboBox(TProjectiles, 120, 60, 330, 20, 10)
BProjPrev = FUI_Button(TProjectiles, 540, 60, 25, 20, "<<") : FUI_ToolTip(BProjPrev, "Previous projectile")
BProjNext = FUI_Button(TProjectiles, 600, 60, 25, 20, ">>") : FUI_ToolTip(BProjNext, "Next projectile")
For P.Projectile = Each Projectile
	Item = FUI_ComboBoxItem(CProjSelected, P\Name$)
	FUI_SendMessage(Item, M_SETDATA, P\ID)
Next
If TotalProjectiles = 0
	FUI_ComboBoxItem(CProjSelected, "No projectiles created...")
	FUI_SendMessage(CProjSelected, M_SETINDEX, 1)
	FUI_SendMessage(CProjSelected, M_DELETEINDEX, 1)
EndIf
; Properties
G = FUI_GroupBox(TProjectiles, 20, 100, 610, 340, "Projectile properties")
FUI_Label(G, 10, 22, "Projectile name:")
Global TProjName = FUI_TextBox(G, 100, 20, 320, 20)
Global LProjMesh = FUI_Label(G, 10, 52, "Projectile mesh: [NONE]")
Global BProjMesh = FUI_Button(G, 350, 50, 90, 20, "Change")
Global BProjMeshN = FUI_Button(G, 450, 50, 90, 20, "None")
FUI_Label(G, 10, 82, "Emitter 1:")
Global CProjEmitter1 = FUI_ComboBox(G, 75, 80, 200, 20, 10)
Global LProjTex1 = FUI_Label(G, 10, 102, "Emitter texture: [NONE]")
Global BProjTex1 = FUI_Button(G, 350, 100, 90, 20, "Change")
FUI_Label(G, 10, 132, "Emitter 2:")
Global CProjEmitter2 = FUI_ComboBox(G, 75, 130, 200, 20, 10)
Global LProjTex2 = FUI_Label(G, 10, 152, "Emitter texture: [NONE]")
Global BProjTex2 = FUI_Button(G, 350, 150, 90, 20, "Change")
FUI_ComboBoxItem(CProjEmitter1, "None")
FUI_ComboBoxItem(CProjEmitter2, "None")
For C.RP_EmitterConfig = Each RP_EmitterConfig
	Item = FUI_ComboBoxItem(CProjEmitter1, C\Name$) : FUI_SendMessage(Item, M_SETDATA, Handle(C))
	Item = FUI_ComboBoxItem(CProjEmitter2, C\Name$) : FUI_SendMessage(Item, M_SETDATA, Handle(C))
Next
Global BProjHoming = FUI_CheckBox(G, 10, 180, "Projectile homes in on target")
FUI_Label(G, 10, 202, "Chance to hit:")
Global SProjHitChance = FUI_Spinner(G, 100, 200, 90, 20, 1, 100, 0, 1, DTYPE_INTEGER, "%")
FUI_Label(G, 10, 232, "Damage:")
Global SProjDamage = FUI_Spinner(G, 100, 230, 90, 20, 0, 5000, 0, 1, DTYPE_INTEGER)
FUI_Label(G, 10, 262, "Damage type:")
Global CProjDamageType = FUI_ComboBox(G, 100, 260, 200, 20, 6)
For i = 0 To 19
	If DamageTypes$(i) <> ""
		Item = FUI_ComboBoxItem(CProjDamageType, DamageTypes$(i))
		FUI_SendMessage(Item, M_SETDATA, i)
	EndIf
Next
FUI_Label(G, 10, 292, "Movement speed:")
Global SProjSpeed = FUI_Spinner(G, 100, 290, 90, 20, 1, 100, 0, 1, DTYPE_INTEGER, "%")

; Init display
FUI_SendMessage(CProjSelected, M_SETINDEX, 1)
UpdateProjectileDisplay()

; Factions gadgets ------------------------------------------------------------------------------------------------------------------

updateSplashScreen("Creating factions tab")

BFactionSave = FUI_Button(TFactions, 20, 20, 100, 20, "Save factions")
FUI_Label(TFactions, 20, 70, "You may add up to 100 factions below:")
Global LFactions = FUI_ListBox(TFactions, 20, 100, 250, 310)
BFactionAdd = FUI_Button(TFactions, 20, 430, 90, 20, "New faction")
BFactionDelete = FUI_Button(TFactions, 130, 430, 90, 20, "Remove faction")
FUI_Label(TFactions, 350, 112, "Rename this faction:")
Global TFactionName = FUI_TextBox(TFactions, 455, 110, 250, 20, 30)
FUI_Label(TFactions, 350, 162, "Adjust this faction's rating with:")
Global CFactionRating = FUI_ComboBox(TFactions, 510, 160, 250, 20, 10)
FUI_Label(TFactions, 350, 192, "This faction's rating with that faction is:")
Global SFactionRating = FUI_Spinner(TFactions, 550, 190, 90, 20, -100, 100, 0, 1, DTYPE_INTEGER, "%")
Global LFactionRatingInverse = FUI_Label(TFactions, 350, 222, "That faction's rating with this faction is 0%")
For i = 0 To 99
	If FactionNames$(i) <> ""
		Item = FUI_ComboBoxItem(CFactionRating, FactionNames$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
		Item = FUI_ListBoxItem(LFactions, FactionNames$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
	EndIf
Next

; Animation set gadgets -------------------------------------------------------------------------------------------------------------

updateSplashScreen("Creating animations tab")

BAnimsSave = FUI_Button(TAnimSets, 20, 20, 120, 20, "Save animation sets")
FUI_Label(TAnimSets, 20, 70, "You may add any number of animation sets below:")
LAnimSets = FUI_ListBox(TAnimSets, 20, 100, 250, 310)
For AS.AnimSet = Each AnimSet
	Item = FUI_ListBoxItem(LAnimSets, AS\Name$) : FUI_SendMessage(Item, M_SETDATA, AS\ID)
Next
BAnimSetAdd = FUI_Button(TAnimSets, 20, 430, 110, 20, "New animation set")
BAnimSetDelete = FUI_Button(TAnimSets, 150, 430, 120, 20, "Remove animation set")
BAnimSetCopy = FUI_Button(TAnimSets, 20, 460, 110, 20, "Copy animation set")
FUI_Label(TAnimSets, 350, 112, "Rename set:")
TAnimSetName = FUI_TextBox(TAnimSets, 425, 110, 250, 20, 30)
FUI_Label(TAnimSets, 350, 142, "Animations in set:")
LAnims = FUI_ListBox(TAnimSets, 350, 160, 250, 250)
BAnimAdd = FUI_Button(TAnimSets, 350, 430, 110, 20, "Add animation")
BAnimDelete = FUI_Button(TAnimSets, 480, 430, 110, 20, "Remove animation")
FUI_Label(TAnimSets, 640, 172, "Rename animation:")
TAnimName = FUI_TextBox(TAnimSets, 640, 190, 200, 20, 30)
FUI_Label(TAnimSets, 640, 220, "Animation start frame:")
TAnimStart = FUI_TextBox(TAnimSets, 650, 240, 90, 20, 5)
FUI_Label(TAnimSets, 640, 270, "Animation end frame:")
TAnimEnd = FUI_TextBox(TAnimSets, 650, 290, 90, 20, 5)
FUI_Label(TAnimSets, 640, 320, "Animation speed:")
SAnimSpeed = FUI_Spinner(TAnimSets, 650, 340, 90, 20, 1, 1000, 0, 1, DTYPE_INTEGER, "%")

; Attributes gadgets ----------------------------------------------------------------------------------------------------------------
updateSplashScreen("Creating attributes tab")

BAttributeSave = FUI_Button(TAttributes, 20, 20, 100, 20, "Save attributes")
BSetFixedAttributes = FUI_Button(TAttributes, 150, 20, 120, 20, "Set fixed attributes")
FUI_Label(TAttributes, 20, 70, "You may add up to 40 attributes below:")
LAttribute = FUI_ListBox(TAttributes, 20, 100, 250, 310)
For i = 0 To 39
	If AttributeNames$(i) <> ""
		Item = FUI_ListBoxItem(LAttribute, AttributeNames$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
	EndIf
Next
BAttributeAdd = FUI_Button(TAttributes, 20, 430, 90, 20, "New attribute")
BAttributeDelete = FUI_Button(TAttributes, 130, 430, 100, 20, "Remove attribute")
FUI_Label(TAttributes, 350, 112, "Rename attribute:")
TAttributeName = FUI_TextBox(TAttributes, 440, 110, 250, 20, 20)
BAttributeSkill = FUI_CheckBox(TAttributes, 350, 142, "Attribute is a skill")
BAttributeHidden = FUI_CheckBox(TAttributes, 350, 172, "Hide attribute from players")
FUI_Label(TAttributes, 20, 472, "Assignable attribute points available at character creation:")
SAttributeAssignment = FUI_Spinner(TAttributes, 320, 470, 90, 20, 0, 100, AttributeAssignment, 1, DTYPE_INTEGER)

; Actors gadgets --------------------------------------------------------------------------------------------------------------------

updateSplashScreen("Creating actors tab")

; Main
BActorNew    = FUI_Button(TActors, 20, 20, 100, 20, "New actor")
BActorCopy   = FUI_Button(TActors, 150, 20, 100, 20, "Copy actor")
BActorDelete = FUI_Button(TActors, 280, 20, 100, 20, "Delete actor")
BActorSave   = FUI_Button(TActors, 410, 20, 100, 20, "Save actors")
Global SelectedActor.Actor
; Selection
FUI_Label(TActors, 20, 62, "Current actor:")
Global CActorSelected = FUI_ComboBox(TActors, 100, 60, 400, 20, 25)
BActorPrev = FUI_Button(TActors, 520, 60, 25, 20, "<<") : FUI_ToolTip(BActorPrev, "Previous actor")
BActorNext = FUI_Button(TActors, 580, 60, 25, 20, ">>") : FUI_ToolTip(BActorNext, "Next actor")

TabActors = FUI_Tab(TActors, 20, 100, GUE_width - 60, 543)
TActorsDescription = FUI_TabPage(TabActors, "Description")
TActorsGeneral     = FUI_TabPage(TabActors, "General")
TActorsAppearance  = FUI_TabPage(TabActors, "Appearance")
;TActorsAttributes  = FUI_TabPage(TabActors, "Attributes")
;TActorsPreview     = FUI_TabPage(TabActors, "Preview")
For At.Actor = Each Actor
	Item = FUI_ComboBoxItem(CActorSelected, At\Race$ + " [" + At\Class$ + "]")
	FUI_SendMessage(Item, M_SETDATA, At\ID)
Next
If TotalActors = 0
	FUI_ComboBoxItem(CActorSelected, "No actors created...")
	FUI_SendMessage(CActorSelected, M_SETINDEX, 1)
	FUI_SendMessage(CActorSelected, M_DELETEINDEX, 1)
EndIf
; Description
Global LActorID = FUI_Label(TActorsDescription, 20, 22, "Actor ID: Nothing selected")
FUI_Label(TActorsDescription, 20, 52, "Actor race:")
Global TActorRace = FUI_TextBox(TActorsDescription, 120, 50, 200, 20, 35)
FUI_Label(TActorsDescription, 20, 82, "Actor class:")
Global TActorClass = FUI_TextBox(TActorsDescription, 120, 80, 200, 20, 35)
FUI_Label(TActorsDescription, 20, 112, "Actor description:")
Global TActorBlurb = FUI_TextBox(TActorsDescription, 120, 110, 600, 20, 100)
FUI_Label(TActorsDescription, 20, 142, "Genders:")
Global CActorGenders = FUI_ComboBox(TActorsDescription, 120, 140, 120, 20)
FUI_ComboBoxItem(CActorGenders, "Male and female")
FUI_ComboBoxItem(CActorGenders, "Male only")
FUI_ComboBoxItem(CActorGenders, "Female only")
FUI_ComboBoxItem(CActorGenders, "No gender")
FUI_Label(TActorsDescription, 20, 172, "Home faction:")
Global CActorFaction = FUI_ComboBox(TActorsDescription, 120, 170, 250, 20, 10)
For i = 0 To 99
	If FactionNames$(i) <> ""
		Item = FUI_ComboBoxItem(CActorFaction, FactionNames$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
	EndIf
Next
; General
FUI_Label(TActorsGeneral, 20, 22, "Aggressiveness:")
Global CActorAttacks = FUI_ComboBox(TActorsGeneral, 120, 20, 110, 20)
FUI_ComboBoxItem(CActorAttacks, "Passive")
FUI_ComboBoxItem(CActorAttacks, "Defensive")
FUI_ComboBoxItem(CActorAttacks, "Always attacks")
FUI_ComboBoxItem(CActorAttacks, "Non-combatant")
FUI_Label(TActorsGeneral, 260, 22, "Attack range:")
Global SActorAttackRange = FUI_Spinner(TActorsGeneral, 340, 20, 90, 20, 0, 5000, 1, 1, DTYPE_INTEGER)
FUI_Label(TActorsGeneral, 20, 52, "Trade Mode:")
Global CActorTrades = FUI_ComboBox(TActorsGeneral, 120, 50, 110, 20)
FUI_ComboBoxItem(CActorTrades, "No trading")
FUI_ComboBoxItem(CActorTrades, "Pack animal")
FUI_ComboBoxItem(CActorTrades, "Salesman")
FUI_Label(TActorsGeneral, 20, 82, "Environment type:")
Global CActorEnviro = FUI_ComboBox(TActorsGeneral, 120, 80, 110, 20)
FUI_ComboBoxItem(CActorEnviro, "Normal")
FUI_ComboBoxItem(CActorEnviro, "Swimming only")
FUI_ComboBoxItem(CActorEnviro, "Flying")
FUI_ComboBoxItem(CActorEnviro, "Walking only")
FUI_Label(TActorsGeneral, 20, 112, "Start area:")
Global CActorStartArea = FUI_ComboBox(TActorsGeneral, 120, 110, 110, 20, 10)
For Ar.Area = Each Area
	FUI_ComboBoxItem(CActorStartArea, Ar\Name$)
Next
FUI_Label(TActorsGeneral, 260, 112, "Start portal:")
Global TActorStartPortal = FUI_TextBox(TActorsGeneral, 340, 110, 90, 20)
FUI_Label(TActorsGeneral, 20, 142, "XP multiplier:")
Global SActorXPMultiplier = FUI_Spinner(TActorsGeneral, 120, 140, 90, 20, 1, 1000, 1, 1, DTYPE_INTEGER)
Global BActorPlayable = FUI_CheckBox(TActorsGeneral, 20, 172, "Actor is playable")
Global BActorRideable = FUI_CheckBox(TActorsGeneral, 20, 192, "Actor can be ridden")
FUI_Label(TActorsGeneral, 20, 222, "Male animation set:")
Global CActorMAnim = FUI_ComboBox(TActorsGeneral, 140, 220, 150, 20, 8)
FUI_Label(TActorsGeneral, 20, 252, "Female animation set:")
Global CActorFAnim = FUI_ComboBox(TActorsGeneral, 140, 250, 150, 20, 8)
For AS.AnimSet = Each AnimSet
	Item = FUI_ComboBoxItem(CActorMAnim, AS\Name$) : FUI_SendMessage(Item, M_SETDATA, AS\ID)
	Item = FUI_ComboBoxItem(CActorFAnim, AS\Name$) : FUI_SendMessage(Item, M_SETDATA, AS\ID)
Next
FUI_Label(TActorsGeneral, 20, 302, "Male sounds:")

Global LActorMSpeech = FUI_Label(TActorsGeneral, 200, 332, "[NONE]", ALIGN_CENTER)
Global CActorMSpeech = FUI_ComboBox(TActorsGeneral, 100, 300, 100, 20)
Global BActorMSpeech = FUI_Button(TActorsGeneral, 205, 300, 75, 20, "Change")
Global BActorMSpeechN = FUI_Button(TActorsGeneral, 285, 300, 75, 20, "None")

FUI_Label(TActorsGeneral, 20, 362, "Female sounds:")

Global LActorFSpeech = FUI_Label(TActorsGeneral, 200, 392, "[NONE]", ALIGN_CENTER)
Global CActorFSpeech = FUI_ComboBox(TActorsGeneral, 100, 360, 100, 20)
Global BActorFSpeech = FUI_Button(TActorsGeneral, 205, 360, 75, 20, "Change")
Global BActorFSpeechN = FUI_Button(TActorsGeneral, 285, 360, 75, 20, "None")

FUI_ComboBoxItem(CActorMSpeech, "Greeting 1") : FUI_ComboBoxItem(CActorFSpeech, "Greeting 1")
FUI_ComboBoxItem(CActorMSpeech, "Greeting 2") : FUI_ComboBoxItem(CActorFSpeech, "Greeting 2")
FUI_ComboBoxItem(CActorMSpeech, "Goodbye 1") : FUI_ComboBoxItem(CActorFSpeech, "Goodbye 1")
FUI_ComboBoxItem(CActorMSpeech, "Goodbye 2") : FUI_ComboBoxItem(CActorFSpeech, "Goodbye 2")
FUI_ComboBoxItem(CActorMSpeech, "Attack 1") : FUI_ComboBoxItem(CActorFSpeech, "Attack 1")
FUI_ComboBoxItem(CActorMSpeech, "Attack 2") : FUI_ComboBoxItem(CActorFSpeech, "Attack 2")
FUI_ComboBoxItem(CActorMSpeech, "Ouch 1") : FUI_ComboBoxItem(CActorFSpeech, "Ouch 1")
FUI_ComboBoxItem(CActorMSpeech, "Ouch 2") : FUI_ComboBoxItem(CActorFSpeech, "Ouch 2")
FUI_ComboBoxItem(CActorMSpeech, "Help!") : FUI_ComboBoxItem(CActorFSpeech, "Help!")
FUI_ComboBoxItem(CActorMSpeech, "Death") : FUI_ComboBoxItem(CActorFSpeech, "Death")
FUI_ComboBoxItem(CActorMSpeech, "Dry Footstep") : FUI_ComboBoxItem(CActorFSpeech, "Dry Footstep")
FUI_ComboBoxItem(CActorMSpeech, "Wet Footstep") : FUI_ComboBoxItem(CActorFSpeech, "Wet Footstep")
FUI_SendMessage(CActorMSpeech, M_SETINDEX, 1) : FUI_SendMessage(CActorFSpeech, M_SETINDEX, 1)

FUI_Label(TActorsGeneral, 20, 422, "Inventory slot:")
Global CActorSlotAllowed = FUI_ComboBox(TActorsGeneral, 100, 420, 150, 20, 8)
Global BActorSlotAllowed = FUI_CheckBox(TActorsGeneral, 260, 422, "Disabled")
FUI_ComboBoxItem(CActorSlotAllowed, "Weapon")
FUI_ComboBoxItem(CActorSlotAllowed, "Shield")
FUI_ComboBoxItem(CActorSlotAllowed, "Hat")
FUI_ComboBoxItem(CActorSlotAllowed, "Chest")
FUI_ComboBoxItem(CActorSlotAllowed, "Hand")
FUI_ComboBoxItem(CActorSlotAllowed, "Belt")
FUI_ComboBoxItem(CActorSlotAllowed, "Legs")
FUI_ComboBoxItem(CActorSlotAllowed, "Feet")
FUI_ComboBoxItem(CActorSlotAllowed, "Ring")
FUI_ComboBoxItem(CActorSlotAllowed, "Amulet")
FUI_ComboBoxItem(CActorSlotAllowed, "Backpack")
FUI_SendMessage(CActorSlotAllowed, M_SETINDEX, 1)

; Appearance
Global LActorMBodyMesh = FUI_Label(TActorsAppearance, 20, 22, "Male Body Mesh: ")
Global BActorMBodyMesh = FUI_Button(TActorsAppearance, 350, 20, 75, 20, "Change")
Global LActorFBodyMesh = FUI_Label(TActorsAppearance, 20, 312, "Female Body Mesh: ")
Global BActorFBodyMesh = FUI_Button(TActorsAppearance, 350, 310, 75, 20, "Change")
;Male Hair
Global CActorMHairMesh = FUI_ComboBox(TActorsAppearance, 20, 50, 50, 20)
FUI_ComboBoxItem(CActorMHairMesh, "1st") : FUI_ComboBoxItem(CActorMHairMesh, "2nd") : FUI_ComboBoxItem(CActorMHairMesh, "3rd")
FUI_ComboBoxItem(CActorMHairMesh, "4th") : FUI_ComboBoxItem(CActorMHairMesh, "5th")
FUI_SendMessage(CActorMHairMesh, M_SETINDEX, 1)
Global LActorMHairMesh = FUI_Label(TActorsAppearance, 80, 52, "Male Hair Mesh: ")
Global BActorMHairMesh = FUI_Button(TActorsAppearance, 330, 50, 75, 20, "Change")
Global BActorMHairMeshN = FUI_Button(TActorsAppearance, 410, 50, 20, 20, "N")
;Female Hair
Global CActorFHairMesh = FUI_ComboBox(TActorsAppearance, 20, 340, 50, 20)
FUI_ComboBoxItem(CActorFHairMesh, "1st") : FUI_ComboBoxItem(CActorFHairMesh, "2nd") : FUI_ComboBoxItem(CActorFHairMesh, "3rd")
FUI_ComboBoxItem(CActorFHairMesh, "4th") : FUI_ComboBoxItem(CActorFHairMesh, "5th")
FUI_SendMessage(CActorFHairMesh, M_SETINDEX, 1)
Global LActorFHairMesh = FUI_Label(TActorsAppearance, 80, 342, "Female Hair Mesh: ")
Global BActorFHairMesh = FUI_Button(TActorsAppearance, 330, 340, 75, 20, "Change")
Global BActorFHairMeshN = FUI_Button(TActorsAppearance, 410, 340, 20, 20, "N")
;Male Face
Global CActorMFaceTex = FUI_ComboBox(TActorsAppearance, 20, 80, 50, 20)
FUI_ComboBoxItem(CActorMFaceTex, "1st") : FUI_ComboBoxItem(CActorMFaceTex, "2nd") : FUI_ComboBoxItem(CActorMFaceTex, "3rd")
FUI_ComboBoxItem(CActorMFaceTex, "4th") : FUI_ComboBoxItem(CActorMFaceTex, "5th")
FUI_SendMessage(CActorMFaceTex, M_SETINDEX, 1)
Global LActorMFaceTex = FUI_Label(TActorsAppearance, 80, 82, "Male Face Texture: ")
Global BActorMFaceTex = FUI_Button(TActorsAppearance, 330, 80, 75, 20, "Change")
Global BActorMFaceTexN = FUI_Button(TActorsAppearance, 410, 80, 20, 20, "N")
;Female Face
Global CActorFFaceTex = FUI_ComboBox(TActorsAppearance, 20, 370, 50, 20)
FUI_ComboBoxItem(CActorFFaceTex, "1st") : FUI_ComboBoxItem(CActorFFaceTex, "2nd") : FUI_ComboBoxItem(CActorFFaceTex, "3rd")
FUI_ComboBoxItem(CActorFFaceTex, "4th") : FUI_ComboBoxItem(CActorFFaceTex, "5th")
FUI_SendMessage(CActorFFaceTex, M_SETINDEX, 1)
Global LActorFFaceTex = FUI_Label(TActorsAppearance, 80, 372, "Female Face Texture: ")
Global BActorFFaceTex = FUI_Button(TActorsAppearance, 330, 370, 75, 20, "Change")
Global BActorFFaceTexN = FUI_Button(TActorsAppearance, 410, 370, 20, 20, "N")
;Male body
Global CActorMBodyTex = FUI_ComboBox(TActorsAppearance, 20, 110, 50, 20)
FUI_ComboBoxItem(CActorMBodyTex, "1st") : FUI_ComboBoxItem(CActorMBodyTex, "2nd") : FUI_ComboBoxItem(CActorMBodyTex, "3rd")
FUI_ComboBoxItem(CActorMBodyTex, "4th") : FUI_ComboBoxItem(CActorMBodyTex, "5th")
FUI_SendMessage(CActorMBodyTex, M_SETINDEX, 1)
Global LActorMBodyTex = FUI_Label(TActorsAppearance, 80, 112, "Male Body Texture: ")
Global BActorMBodyTex = FUI_Button(TActorsAppearance, 330, 110, 75, 20, "Change")
Global BActorMBodyTexN = FUI_Button(TActorsAppearance, 410, 110, 20, 20, "N")
;Female Body
Global CActorFBodyTex = FUI_ComboBox(TActorsAppearance, 20, 400, 50, 20)
FUI_ComboBoxItem(CActorFBodyTex, "1st") : FUI_ComboBoxItem(CActorFBodyTex, "2nd") : FUI_ComboBoxItem(CActorFBodyTex, "3rd")
FUI_ComboBoxItem(CActorFBodyTex, "4th") : FUI_ComboBoxItem(CActorFBodyTex, "5th")
FUI_SendMessage(CActorFBodyTex, M_SETINDEX, 1)
Global LActorFBodyTex = FUI_Label(TActorsAppearance, 80, 402, "Female Body Texture: ")
Global BActorFBodyTex = FUI_Button(TActorsAppearance, 330, 400, 75, 20, "Change")
Global BActorFBodyTexN = FUI_Button(TActorsAppearance, 410, 400, 20, 20, "N")
;Male beard
Global CActorBeardMesh = FUI_ComboBox(TActorsAppearance, 20, 140, 50, 20)
FUI_ComboBoxItem(CActorBeardMesh, "1st") : FUI_ComboBoxItem(CActorBeardMesh, "2nd") : FUI_ComboBoxItem(CActorBeardMesh, "3rd")
FUI_ComboBoxItem(CActorBeardMesh, "4th") : FUI_ComboBoxItem(CActorBeardMesh, "5th")
FUI_SendMessage(CActorBeardMesh, M_SETINDEX, 1)
Global LActorBeardMesh = FUI_Label(TActorsAppearance, 80, 142, "Beard Mesh: ")
Global BActorBeardMesh = FUI_Button(TActorsAppearance, 330, 140, 75, 20, "Change")
Global BActorBeardMeshN = FUI_Button(TActorsAppearance, 410, 140, 20, 20, "N")
;Gubbins
Global CActorGubbinMesh = FUI_ComboBox(TActorsAppearance, 20, 190, 50, 20)
FUI_ComboBoxItem(CActorGubbinMesh, "1st") : FUI_ComboBoxItem(CActorGubbinMesh, "2nd") : FUI_ComboBoxItem(CActorGubbinMesh, "3rd")
FUI_ComboBoxItem(CActorGubbinMesh, "4th") : FUI_ComboBoxItem(CActorGubbinMesh, "5th") : FUI_ComboBoxItem(CActorGubbinMesh, "6th")
FUI_SendMessage(CActorGubbinMesh, M_SETINDEX, 1)
Global LActorGubbinMesh = FUI_Label(TActorsAppearance, 80, 192, "Gubbin Mesh: ")
Global BActorGubbinMesh = FUI_Button(TActorsAppearance, 330, 190, 75, 20, "Change")
Global BActorGubbinMeshN = FUI_Button(TActorsAppearance, 410, 190, 20, 20, "N")
Global LActorBlood = FUI_Label(TActorsAppearance, 20, 222, "Blood texture: ")
Global BActorBlood = FUI_Button(TActorsAppearance, 330, 220, 75, 20, "Change")
FUI_Label(TActorsAppearance, 20, 252, "Actor scale:")
Global SActorScale = FUI_Spinner(TActorsAppearance, 90, 250, 90, 20, 1, 1000, 0, 1, DTYPE_INTEGER, "%")
Global BActorPolyCollision = FUI_CheckBox(TActorsAppearance, 20, 282, "Use polygonal collision (Using this on moving actors will disable collisions entirely)")

; Attributes / resistances
;FUI_Label(TActorsAttributes, 120, 22, "Attributes:", ALIGN_CENTER)
;Global LActorAttributes = FUI_ListBox(TActorsAttributes, 20, 50, 200, 450)
;For i = 0 To 39
;	If AttributeNames$(i) <> ""
;		Item = FUI_ListBoxItem(LActorAttributes, AttributeNames$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
;	EndIf
;Next
;FUI_Label(TActorsAttributes, 260, 22, "Attribute value:")
;Global SActorAttribute = FUI_Spinner(TActorsAttributes, 260, 50, 100, 20, 0, 65535, 0, 1, DTYPE_INTEGER)
;FUI_Label(TActorsAttributes, 260, 102, "Attribute maximum:")
;Global SActorAttributeMax = FUI_Spinner(TActorsAttributes, 260, 130, 100, 20, 0, 65535, 0, 1, DTYPE_INTEGER)
;FUI_Label(TActorsAttributes, 540, 22, "Resistances:", ALIGN_CENTER)
;Global LActorResistances = FUI_ListBox(TActorsAttributes, 440, 50, 200, 450)
;For i = 0 To 19
;	If DamageTypes$(i) <> ""
;		Item = FUI_ListBoxItem(LActorResistances, DamageTypes$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
;	EndIf
;Next
;FUI_Label(TActorsAttributes, 680, 22, "Resistance value:")
;Global SActorResistance = FUI_Spinner(TActorsAttributes, 680, 50, 100, 20, 0, 65535, 0, 1, DTYPE_INTEGER)
;---------------------


FUI_Label(TActorsGeneral, 440, 22, "Attributes:")
Global LActorAttributes = FUI_ListBox(TActorsGeneral, 440, 50, 150, 450)
For i = 0 To 39
	If AttributeNames$(i) <> ""
		Item = FUI_ListBoxItem(LActorAttributes, AttributeNames$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
	EndIf
Next
FUI_Label(TActorsGeneral, 600, 22, "Attribute value:")
Global SActorAttribute = FUI_Spinner(TActorsGeneral, 600, 50, 100, 20, 0, 65535, 0, 1, DTYPE_INTEGER)
FUI_Label(TActorsGeneral, 600, 102, "Attribute maximum:")
Global SActorAttributeMax = FUI_Spinner(TActorsGeneral, 600, 130, 100, 20, 0, 65535, 0, 1, DTYPE_INTEGER)
FUI_Label(TActorsGeneral, 705, 22, "Resistances:")
Global LActorResistances = FUI_ListBox(TActorsGeneral, 705, 50, 150, 450)
For i = 0 To 19
	If DamageTypes$(i) <> ""
		Item = FUI_ListBoxItem(LActorResistances, DamageTypes$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
	EndIf
Next
FUI_Label(TActorsGeneral, 860, 22, "Resistance value:")
Global SActorResistance = FUI_Spinner(TActorsGeneral, 860, 50, 100, 20, 0, 65535, 0, 1, DTYPE_INTEGER)

; Preview
;VActorPreview = FUI_View(TActorsPreview, 146, 30, 640, 480, 0, 0, 0)
VActorPreview = FUI_View(TActorsAppearance, 550, 20, 640, 480, 0, 0, 0)

Global ActorPreview.ActorInstance

; Set up initial actor display
FUI_SendMessage(CActorSelected, M_SETINDEX, 1)
UpdateActorDisplay()

; Items gadgets ---------------------------------------------------------------------------------------------------------------------

updateSplashScreen("Creating items tab")

; Main
BItemNew    = FUI_Button(TItems, 20, 20, 100, 20, "New item")
BItemDelete = FUI_Button(TItems, 150, 20, 100, 20, "Delete item")
BItemSave   = FUI_Button(TItems, 280, 20, 100, 20, "Save items")
Global SelectedItem.Item
; Selection
FUI_Label(TItems, 20, 62, "Current item:")
Global CItemSelected = FUI_ComboBox(TItems, 100, 60, 400, 20, 25)
BItemPrev = FUI_Button(TItems, 520, 60, 25, 20, "<<") : FUI_ToolTip(BItemPrev, "Previous item")
BItemNext = FUI_Button(TItems, 580, 60, 25, 20, ">>") : FUI_ToolTip(BItemNext, "Next item")

TabItems = FUI_Tab(TItems, 20, 100, GUE_width - 60, 543)
TItemsGeneral    = FUI_TabPage(TabItems, "General")
;TItemsSpecific   = FUI_TabPage(TabItems, "Specific")
TItemsAppearance = FUI_TabPage(TabItems, "Appearance")
;TItemsAttributes = FUI_TabPage(TabItems, "Attributes")
;TItemsOther      = FUI_TabPage(TabItems, "Other")
For It.Item = Each Item
	Item = FUI_ComboBoxItem(CItemSelected, It\Name$)
	FUI_SendMessage(Item, M_SETDATA, It\ID)
Next
If TotalItems = 0
	FUI_ComboBoxItem(CItemSelected, "No items created...")
	FUI_SendMessage(CItemSelected, M_SETINDEX, 1)
	FUI_SendMessage(CItemSelected, M_DELETEINDEX, 1)
EndIf
; General
Global LItemID = FUI_Label(TItemsGeneral, 20, 22, "Item ID: Nothing selected")
FUI_Label(TItemsGeneral, 20, 52, "Item name:")
Global TItemName = FUI_TextBox(TItemsGeneral, 95, 50, 400, 20)
FUI_Label(TItemsGeneral, 20, 82, "Item type:")
Global CItemType = FUI_ComboBox(TItemsGeneral, 95, 80, 150, 20)
FUI_ComboBoxItem(CItemType, "Weapon")
FUI_ComboBoxItem(CItemType, "Armour")
FUI_ComboBoxItem(CItemType, "Ring")
FUI_ComboBoxItem(CItemType, "Potion")
FUI_ComboBoxItem(CItemType, "Food")
FUI_ComboBoxItem(CItemType, "Image")
FUI_ComboBoxItem(CItemType, "Other")
FUI_Label(TItemsGeneral, 20, 112, "Inventory slot:")
Global CSlotType = FUI_ComboBox(TItemsGeneral, 95, 110, 150, 20)
FUI_Label(TItemsGeneral, 20, 152, "Value:")
Global SItemValue = FUI_Spinner(TItemsGeneral, 95, 150, 80, 20, 0, 10000000, 2, 1, DTYPE_INTEGER)
FUI_Label(TItemsGeneral, 20, 182, "Mass:")
Global SItemMass = FUI_Spinner(TItemsGeneral, 95, 180, 80, 20, 0, 100000, 2, 1, DTYPE_INTEGER, " kg")

; Specific
;Global LItemWeaponDamage = FUI_Label(TItemsSpecific, 20, 22, "Damage:")
;Global SItemWeaponDamage = FUI_Spinner(TItemsSpecific, 110, 20, 50, 20, 1, 5000, 0, 1, DTYPE_INTEGER)
;Global LItemWeaponType = FUI_Label(TItemsSpecific, 20, 52, "Weapon type:")
;Global CItemWeaponType = FUI_ComboBox(TItemsSpecific, 110, 50, 100, 20)
;FUI_ComboBoxItem(CItemWeaponType, "One Handed")
;FUI_ComboBoxItem(CItemWeaponType, "Two Handed")
;FUI_ComboBoxItem(CItemWeaponType, "Ranged")
;Global LItemDamageType = FUI_Label(TItemsSpecific, 20, 82, "Damage type:")
;Global CItemDamageType = FUI_ComboBox(TItemsSpecific, 110, 80, 200, 20, 6)
;For i = 0 To 19
;	If DamageTypes$(i) <> ""
;		Item = FUI_ComboBoxItem(CItemDamageType, DamageTypes$(i))
;		FUI_SendMessage(Item, M_SETDATA, i)
;	EndIf
;Next
;Global LItemRangedProjectile = FUI_Label(TItemsSpecific, 20, 112, "Projectile:")
;Global CItemRangedProjectile = FUI_ComboBox(TItemsSpecific, 110, 110, 200, 20, 10)
;For P.Projectile = Each Projectile
;	Item = FUI_ComboBoxItem(CItemRangedProjectile, P\Name$)
;	FUI_SendMessage(Item, M_SETDATA, P\ID)
;Next
;Global LItemRangedAnimation = FUI_Label(TItemsSpecific, 20, 142, "Ranged animation:")
;Global TItemRangedAnimation = FUI_TextBox(TItemsSpecific, 110, 140, 100, 20)
;Global LItemRange = FUI_Label(TItemsSpecific, 20, 172, "Maximum range:")
;Global SItemRange = FUI_Spinner(TItemsSpecific, 110, 170, 80, 20, 0.1, 200.0, 0.0, 0.1, DTYPE_FLOAT)
;Global LItemArmourLevel = FUI_Label(TItemsSpecific, 20, 22, "Armour level:")
;Global SItemArmourLevel = FUI_Spinner(TItemsSpecific, 100, 20, 100, 20, 0, 5000, 0, 1, DTYPE_INTEGER)
;Global LItemEatEffects = FUI_Label(TItemsSpecific, 20, 22, "Effects duration:")
;Global SItemEatEffects = FUI_Spinner(TItemsSpecific, 110, 20, 100, 20, 1, 100000, 0, 1, DTYPE_INTEGER, " seconds")
;Global LItemImageID = FUI_Label(TItemsSpecific, 20, 22, "Display image: [NONE]")
;Global BItemImageID = FUI_Button(TItemsSpecific, 20, 45, 90, 20, "Change")
;Global LItemMiscData = FUI_Label(TItemsSpecific, 20, 22, "Miscellaneous data:")
;Global TItemMiscData = FUI_TextBox(TItemsSpecific, 125, 20, 300, 20)
;------------------------------------------------------
Global LItemWeaponDamage = FUI_Label(TItemsGeneral, 20, 212, "Damage:")
Global SItemWeaponDamage = FUI_Spinner(TItemsGeneral, 110, 210, 50, 20, 1, 5000, 0, 1, DTYPE_INTEGER)
Global LItemWeaponType = FUI_Label(TItemsGeneral, 20, 242, "Weapon type:")
Global CItemWeaponType = FUI_ComboBox(TItemsGeneral, 110, 240, 100, 20)
FUI_ComboBoxItem(CItemWeaponType, "One Handed")
FUI_ComboBoxItem(CItemWeaponType, "Two Handed")
FUI_ComboBoxItem(CItemWeaponType, "Ranged")
Global LItemDamageType = FUI_Label(TItemsGeneral, 20, 272, "Damage type:")
Global CItemDamageType = FUI_ComboBox(TItemsGeneral, 110, 270, 200, 20, 6)
For i = 0 To 19
	If DamageTypes$(i) <> ""
		Item = FUI_ComboBoxItem(CItemDamageType, DamageTypes$(i))
		FUI_SendMessage(Item, M_SETDATA, i)
	EndIf
Next
Global LItemRangedProjectile = FUI_Label(TItemsGeneral, 20, 302, "Projectile:")
Global CItemRangedProjectile = FUI_ComboBox(TItemsGeneral, 110, 300, 200, 20, 10)
For P.Projectile = Each Projectile
	Item = FUI_ComboBoxItem(CItemRangedProjectile, P\Name$)
	FUI_SendMessage(Item, M_SETDATA, P\ID)
Next
Global LItemRangedAnimation = FUI_Label(TItemsGeneral, 20, 332, "Ranged animation:")
Global TItemRangedAnimation = FUI_TextBox(TItemsGeneral, 110, 330, 100, 20)
Global LItemRange = FUI_Label(TItemsGeneral, 20, 362, "Maximum range:")
Global SItemRange = FUI_Spinner(TItemsGeneral, 110, 360, 80, 20, 0.1, 200.0, 0.0, 0.1, DTYPE_FLOAT)

Global LItemArmourLevel = FUI_Label(TItemsGeneral, 20, 212, "Armour level:")
Global SItemArmourLevel = FUI_Spinner(TItemsGeneral, 110, 210, 100, 20, 0, 5000, 0, 1, DTYPE_INTEGER)

Global LItemEatEffects = FUI_Label(TItemsGeneral, 20, 212, "Effects duration:")
Global SItemEatEffects = FUI_Spinner(TItemsGeneral, 110, 210, 100, 20, 1, 100000, 0, 1, DTYPE_INTEGER, " seconds")

Global LItemImageID = FUI_Label(TItemsGeneral, 20, 212, "Display image: [NONE]")
Global BItemImageID = FUI_Button(TItemsGeneral, 20, 240, 90, 20, "Change")
Global LItemMiscData = FUI_Label(TItemsGeneral, 20, 212, "Miscellaneous data:")
Global TItemMiscData = FUI_TextBox(TItemsGeneral, 120, 210, 300, 20)


; Appearance
Global LItemThumb = FUI_Label(TItemsAppearance, 20, 22, "Item thumbnail texture: [NONE]")
BItemThumb = FUI_Button(TItemsAppearance, 20, 45, 100, 20, "Change")
Global LItemMeshM = FUI_Label(TItemsAppearance, 20, 107, "Item mesh (male): [NONE]")
BItemMeshM = FUI_Button(TItemsAppearance, 20, 130, 100, 20, "Change")
BItemMeshMN = FUI_Button(TItemsAppearance, 150, 130, 100, 20, "None")
Global LItemMeshF = FUI_Label(TItemsAppearance, 20, 167, "Item mesh (female): [NONE]")
BItemMeshF = FUI_Button(TItemsAppearance, 20, 190, 100, 20, "Change")
BItemMeshFN = FUI_Button(TItemsAppearance, 150, 190, 100, 20, "None")
Global CItemGubbin = FUI_ComboBox(TItemsAppearance, 20, 240, 110, 20)
FUI_ComboBoxItem(CItemGubbin, "Gubbin 1") : FUI_ComboBoxItem(CItemGubbin, "Gubbin 2") : FUI_ComboBoxItem(CItemGubbin, "Gubbin 3")
FUI_ComboBoxItem(CItemGubbin, "Gubbin 4") : FUI_ComboBoxItem(CItemGubbin, "Gubbin 5") : FUI_ComboBoxItem(CItemGubbin, "Gubbin 6")
FUI_SendMessage(CItemGubbin, M_SETINDEX, 1)
Global BItemGubbin = FUI_CheckBox(TItemsAppearance, 150, 242, "Show this gubbin when item is equipped")

; Attributes
;FUI_Label(TItemsAttributes, 160, 22, "Attributes:", ALIGN_CENTER)
;Global LItemAttributes = FUI_ListBox(TItemsAttributes, 20, 50, 300, 450)
;For i = 0 To 39
;	If AttributeNames$(i) <> ""
;		Item = FUI_ListBoxItem(LItemAttributes, AttributeNames$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
;	EndIf
;Next
;FUI_Label(TItemsAttributes, 400, 22, "Attribute value:")
;Global SItemAttribute = FUI_Spinner(TItemsAttributes, 400, 50, 100, 20, -5000, 5000, 0, 1, DTYPE_INTEGER)
;------------------------------------
FUI_Label(TItemsAppearance, 660, 22, "Attributes:", ALIGN_CENTER)
Global LItemAttributes = FUI_ListBox(TItemsAppearance, 520, 50, 300, 450)
For i = 0 To 39
	If AttributeNames$(i) <> ""
		Item = FUI_ListBoxItem(LItemAttributes, AttributeNames$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
	EndIf
Next
FUI_Label(TItemsAppearance, 900, 22, "Attribute value:")
Global SItemAttribute = FUI_Spinner(TItemsAppearance, 900, 50, 100, 20, -5000, 5000, 0, 1, DTYPE_INTEGER)

; Other
;Global BItemStackable = FUI_CheckBox(TItemsOther, 20, 20, "Item can be stacked")
;Global BItemTakesDamage = FUI_CheckBox(TItemsOther, 20, 50, "Item takes damage")
;FUI_Label(TItemsOther, 20, 102, "Item is exclusive to this race:")
;Global CItemExclusiveRace = FUI_ComboBox(TItemsOther, 250, 100, 350, 20, 10)
;FUI_ComboBoxItem(CItemExclusiveRace, "None (can be used by any race)")
;FUI_Label(TItemsOther, 20, 132, "Item is exclusive to this class:")
;Global CItemExclusiveClass = FUI_ComboBox(TItemsOther, 250, 130, 350, 20, 10)
;FUI_ComboBoxItem(CItemExclusiveClass, "None (can be used by any class)")
;FUI_Label(TItemsOther, 20, 182, "Item runs this script on right-click:")
;
;Global CItemScript = FUI_ComboBox(TItemsOther, 250, 180, 350, 20, 10)
;FUI_ToolTip(CItemScript, "Tip: Scripts must start with  Item_  to be seen here.")
;
;FUI_ComboBoxItem(CItemScript, "None")
;FUI_Label(TItemsOther, 20, 212, "Function to start script in:")
;Global CItemMethod = FUI_ComboBox(TItemsOther, 250, 210, 350, 20, 10)
;--------------------------------------

Global BItemStackable = FUI_CheckBox(TItemsGeneral, 510, 20, "Item can be stacked")
Global BItemTakesDamage = FUI_CheckBox(TItemsGeneral, 510, 50, "Item takes damage")
FUI_Label(TItemsGeneral, 510, 102, "Item is exclusive to this race:")
Global CItemExclusiveRace = FUI_ComboBox(TItemsGeneral, 740, 100, 350, 20, 10)
FUI_ComboBoxItem(CItemExclusiveRace, "None (can be used by any race)")
FUI_Label(TItemsGeneral, 510, 132, "Item is exclusive to this class:")
Global CItemExclusiveClass = FUI_ComboBox(TItemsGeneral, 740, 130, 350, 20, 10)
FUI_ComboBoxItem(CItemExclusiveClass, "None (can be used by any class)")
FUI_Label(TItemsGeneral, 510, 182, "Item runs this script on right-click:")

Global CItemScript = FUI_ComboBox(TItemsGeneral, 740, 180, 350, 20, 10)
FUI_ToolTip(CItemScript, "Tip: Scripts must start with  Item_  to be seen here.")

FUI_ComboBoxItem(CItemScript, "None")
FUI_Label(TItemsGeneral, 510, 212, "Function to start script in:")
Global CItemMethod = FUI_ComboBox(TItemsGeneral, 740, 210, 350, 20, 10)

; Set up initial item display
FUI_SendMessage(LItemAttributes, M_SETINDEX, 1)
FUI_SendMessage(CItemSelected, M_SETINDEX, 1)
UpdateItemDisplay()

; Seasons gadgets -------------------------------------------------------------------------------------------------------------------

updateSplashScreen("Creating seasons tab")

BSeasonSave = FUI_Button(TSeasons, 10, 20, 100, 20, "Save settings")
FUI_GroupBox(TSeasons, 10, 60, 480, 80, "General")
FUI_GroupBox(TSeasons, 10, 150, 480, 80, "Months")
FUI_GroupBox(TSeasons, 10, 240, 480, 140, "Seasons")

;FUI_GroupBox(TSeasons, 510, 60, 450, 360, "Suns & Moons")
FUI_GroupBox(TSeasons, 510, 60, 450, GUE_height - 20 - 148, "Suns & Moons")

; Season settings
FUI_Label(TSeasons, 20, 82, "Year length:")
SYearLength = FUI_Spinner(TSeasons, 100, 80, 100, 20, 25, 10000, MonthStartDay(0), 1, DTYPE_INTEGER, " days")
FUI_Label(TSeasons, 270, 82, "Time compression:")
STimeFactor = FUI_Spinner(TSeasons, 380, 80, 70, 20, 1, 255, TimeFactor, 1, DTYPE_INTEGER, "x")
FUI_Label(TSeasons, 20, 112, "Current year:")
SYear = FUI_Spinner(TSeasons, 100, 110, 70, 20, -100000, 1000000, Year, 1, DTYPE_INTEGER)
FUI_Label(TSeasons, 270, 112, "Current day:")
SDay = FUI_Spinner(TSeasons, 380, 110, 70, 20, 1, 10000, Day + 1, 1, DTYPE_INTEGER)
FUI_Label(TSeasons, 20, 172, "Month:")
CMonthNum = FUI_ComboBox(TSeasons, 70, 170, 70, 20)
FUI_ComboBoxItem(CMonthNum, "1st") : FUI_ComboBoxItem(CMonthNum, "2nd") : FUI_ComboBoxItem(CMonthNum, "3rd")
FUI_ComboBoxItem(CMonthNum, "4th") : FUI_ComboBoxItem(CMonthNum, "5th") : FUI_ComboBoxItem(CMonthNum, "6th")
FUI_ComboBoxItem(CMonthNum, "7th") : FUI_ComboBoxItem(CMonthNum, "8th") : FUI_ComboBoxItem(CMonthNum, "9th")
FUI_ComboBoxItem(CMonthNum, "10th") : FUI_ComboBoxItem(CMonthNum, "11th") : FUI_ComboBoxItem(CMonthNum, "12th")
FUI_ComboBoxItem(CMonthNum, "13th") : FUI_ComboBoxItem(CMonthNum, "14th") : FUI_ComboBoxItem(CMonthNum, "15th")
FUI_ComboBoxItem(CMonthNum, "16th") : FUI_ComboBoxItem(CMonthNum, "17th") : FUI_ComboBoxItem(CMonthNum, "18th")
FUI_ComboBoxItem(CMonthNum, "19th") : FUI_ComboBoxItem(CMonthNum, "20th")
FUI_SendMessage(CMonthNum, M_SETINDEX, 1)
FUI_Label(TSeasons, 250, 172, "Name:")
TMonthName = FUI_TextBox(TSeasons, 290, 170, 190, 20, 50)
FUI_SendMessage(TMonthName, M_SETCAPTION, MonthName$(0))
FUI_Label(TSeasons, 250, 202, "Month length:")
SMonthStart = FUI_Spinner(TSeasons, 325, 200, 100, 20, 1, 10000, 0, 1, DTYPE_INTEGER, " days")
FUI_SendMessage(SMonthStart, M_SETVALUE, MonthStartDay(1))
FUI_Label(TSeasons, 20, 262, "Season:")
CSeasonNum = FUI_ComboBox(TSeasons, 70, 260, 70, 20)
FUI_ComboBoxItem(CSeasonNum, "1st") : FUI_ComboBoxItem(CSeasonNum, "2nd") : FUI_ComboBoxItem(CSeasonNum, "3rd")
FUI_ComboBoxItem(CSeasonNum, "4th") : FUI_ComboBoxItem(CSeasonNum, "5th") : FUI_ComboBoxItem(CSeasonNum, "6th")
FUI_ComboBoxItem(CSeasonNum, "7th") : FUI_ComboBoxItem(CSeasonNum, "8th") : FUI_ComboBoxItem(CSeasonNum, "9th")
FUI_ComboBoxItem(CSeasonNum, "10th") : FUI_ComboBoxItem(CSeasonNum, "11th") : FUI_ComboBoxItem(CSeasonNum, "12th")
FUI_SendMessage(CSeasonNum, M_SETINDEX, 1)
FUI_Label(TSeasons, 250, 262, "Name:")
TSeasonName = FUI_TextBox(TSeasons, 290, 260, 190, 20, 50)
FUI_SendMessage(TSeasonName, M_SETCAPTION, SeasonName$(0))
FUI_Label(TSeasons, 250, 292, "Season length:")
SSeasonStart = FUI_Spinner(TSeasons, 325, 290, 100, 20, 1, 10000, 0, 1, DTYPE_INTEGER, " days")
FUI_SendMessage(SSeasonStart, M_SETVALUE, SeasonStartDay(1))
FUI_Label(TSeasons, 250, 322, "Dawn hour:")
SDawn = FUI_Spinner(TSeasons, 325, 320, 100, 20, 0, 23, SeasonDawnH(0), 1, DTYPE_INTEGER, ":00")
FUI_Label(TSeasons, 250, 352, "Dusk hour:")
SDusk = FUI_Spinner(TSeasons, 325, 350, 100, 20, 0, 23, SeasonDuskH(0), 1, DTYPE_INTEGER, ":00")

; Sun settings
BSunNew    = FUI_Button(TSeasons, 520, 80, 90, 20, "New sun")
BSunDelete = FUI_Button(TSeasons, 615, 80, 90, 20, "Delete sun")
BSunPrev   = FUI_Button(TSeasons, 735, 80, 25, 20, "<<")
BSunNext   = FUI_Button(TSeasons, 855, 80, 25, 20, ">>")
Global LSunNumber = FUI_Label(TSeasons, 808, 82, "Sun 0 of 0", ALIGN_CENTER)
Global LSunTex  = FUI_Label(TSeasons, 520, 122, "Sun texture: [NONE]")
BSunTex  = FUI_Button(TSeasons, 800, 120, 100, 20, "Change")
FUI_Label(TSeasons, 520, 152, "Sun size:")
Global SSunSize = FUI_Spinner(TSeasons, 580, 150, 90, 20, 1, 10, 5, 0.1, DTYPE_FLOAT)
FUI_Label(TSeasons, 720, 152, "Sun path angle:")
Global SSunAngle = FUI_Spinner(TSeasons, 810, 150, 90, 20, 0, 360, 0, 1, DTYPE_INTEGER, " degrees")
FUI_Label(TSeasons, 520, 192, "Editing rise/set times for season:")
Global CSunSeason = FUI_ComboBox(TSeasons, 690, 190, 40, 20)
FUI_ComboBoxItem(CSunSeason, "1") : FUI_ComboBoxItem(CSunSeason, "2") : FUI_ComboBoxItem(CSunSeason, "3")
FUI_ComboBoxItem(CSunSeason, "4") : FUI_ComboBoxItem(CSunSeason, "5") : FUI_ComboBoxItem(CSunSeason, "6")
FUI_ComboBoxItem(CSunSeason, "7") : FUI_ComboBoxItem(CSunSeason, "8") : FUI_ComboBoxItem(CSunSeason, "9")
FUI_ComboBoxItem(CSunSeason, "10") : FUI_ComboBoxItem(CSunSeason, "11") : FUI_ComboBoxItem(CSunSeason, "12")
FUI_SendMessage(CSunSeason, M_SETINDEX, 1)
Global LSunSeasonName = FUI_Label(TSeasons, 740, 192, "(Season Name)")
FUI_Label(TSeasons, 570, 222, "Rise hour:")
Global SSunRiseH = FUI_Spinner(TSeasons, 630, 220, 100, 20, 0, 23, 0, 1, DTYPE_INTEGER)
FUI_Label(TSeasons, 770, 222, "Rise minute:")
Global SSunRiseM = FUI_Spinner(TSeasons, 840, 220, 100, 20, 0, 59, 0, 1, DTYPE_INTEGER)
FUI_Label(TSeasons, 570, 252, "Set hour:")
Global SSunSetH = FUI_Spinner(TSeasons, 630, 250, 100, 20, 0, 23, 0, 1, DTYPE_INTEGER)
FUI_Label(TSeasons, 770, 252, "Set minute:")
Global SSunSetM = FUI_Spinner(TSeasons, 840, 250, 100, 20, 0, 59, 0, 1, DTYPE_INTEGER)
FUI_Label(TSeasons, 520, 290, "Light colour:")
FUI_Label(TSeasons, 520, 312, "Red:")
Global SLSunR = FUI_Slider(TSeasons, 560, 310, 110, 20, 0, 255, 0)
FUI_Label(TSeasons, 520, 332, "Green:")
Global SLSunG = FUI_Slider(TSeasons, 560, 330, 110, 20, 0, 255, 0)
FUI_Label(TSeasons, 520, 352, "Blue:")
Global SLSunB = FUI_Slider(TSeasons, 560, 350, 110, 20, 0, 255, 0)
Global BSunShowFlares = FUI_CheckBox(TSeasons, 520, 390, "Show lens flare from this sun")

;LUNAR PHASES BITCH....
Global BSunShowPhases = FUI_CheckBox(TSeasons, 520, 410, "Show phases from this 'sun'")

;	Create combobox to show phase textures
FUI_Label(TSeasons, 520, 452, "Phase:")
Global CSunPhase = FUI_ComboBox(TSeasons, 690, 450, 40, 20)
For i = 0 To 7
	FUI_ComboBoxItem(CSunPhase, Str(i+1))
Next
FUI_SendMessage(CSunPhase, M_SETINDEX, 1)

;	Length of phase in days
FUI_Label(TSeasons, 520, 492, "Phase length:")
Global SSunPhase_Length = FUI_Spinner(TSeasons, 690, 492, 90, 20, 1, 200, 0, 1, DTYPE_INTEGER, " days")



; Set up initial sun display
Global SelectedSun.Sun = First Sun
UpdateSunDisplay()

; Zones gadgets ---------------------------------------------------------------------------------------------------------------------

updateSplashScreen("Creating zones tab")

; General
Global CurrentArea.Area
Global ZoneView, ZoneMode = 1
Global ZoneCam
Global ZoneSceneryMeshID
Global SelectedEN
Global SelectionBoxEN = CreateCube()
EntityColor SelectionBoxEN, 30, 144, 255 ;[565]
EntityAlpha SelectionBoxEN, 0.4
EntityFX SelectionBoxEN, 1 + 8 + 16
HideEntity SelectionBoxEN
; Toolbar
BZoneNew    = FUI_Button(TZones, 20, 4, 80, 20, "New zone")
BZoneSave   = FUI_Button(TZones, 20, 27, 80, 20, "Save zone")
BZoneCopy   = FUI_Button(TZones, 105, 4, 80, 20, "Copy zone")
BZoneDelete = FUI_Button(TZones, 105, 27, 80, 20, "Delete zone")

BZoneUndo   = FUI_Button(TZones, 190, 27, 20, 20, "", "Data\GUE\L.png") : FUI_ToolTip(BZoneUndo, "Undo, Ctrl + Z")

Global BZoneScenery   = FUI_Button(TZones, 240, 27, 20, 20, "", "Data\GUE\612-home.png", 1) : FUI_ToolTip(BZoneScenery, "Scenery Mode")
Global BZoneTerrain   = FUI_Button(TZones, 265, 27, 20, 20, "", "Data\GUE\128-status.png", 1) : FUI_ToolTip(BZoneTerrain, "Terrain Mode")
Global BZoneEmitters  = FUI_Button(TZones, 290, 27, 20, 20, "", "Data\GUE\212-thunderbolt.png", 1) : FUI_ToolTip(BZoneEmitters, "Emitters Mode")
Global BZoneWater     = FUI_Button(TZones, 315, 27, 20, 20, "", "Data\GUE\608-funnel.png", 1) : FUI_ToolTip(BZoneWater, "Water Mode")
Global BZoneColBox    = FUI_Button(TZones, 340, 27, 20, 20, "", "Data\GUE\614-lock.png", 1) : FUI_ToolTip(BZoneColBox, "Collision box Mode")
Global BZoneSoundZone = FUI_Button(TZones, 365, 27, 20, 20, "", "Data\GUE\084-music.png", 1) : FUI_ToolTip(BZoneSoundZone, "Sound zone Mode")
Global BZoneTriggers  = FUI_Button(TZones, 390, 27, 20, 20, "", "Data\GUE\609-gift.png", 1) : FUI_ToolTip(BZoneTriggers, "Trigger Mode")
Global BZoneWaypoints = FUI_Button(TZones, 415, 27, 20, 20, "", "Data\GUE\409-left_right.png", 1) : FUI_ToolTip(BZoneWaypoints, "Waypoint Mode")
Global BZonePortals   = FUI_Button(TZones, 440, 27, 20, 20, "", "Data\GUE\029-app.png", 1) : FUI_ToolTip(BZonePortals, "Portal Mode")
Global BZoneEnviro    = FUI_Button(TZones, 465, 27, 20, 20, "", "Data\GUE\089-star.png", 1) : FUI_ToolTip(BZoneEnviro, "Environment Options")
Global BZoneOther     = FUI_Button(TZones, 490, 27, 20, 20, "", "Data\GUE\103-options2.png", 1) : FUI_ToolTip(BZoneOther, "Other Options")
Global BZoneLights    = FUI_Button(TZones, 515, 27, 20, 20, "", "Data\GUE\001-light.png", 1) : FUI_ToolTip(BZoneLights, "Dynamic Lights")


;FUI_Label(TZones, 720, 22, "Current zone:")
;Global CZone = FUI_ComboBox(TZones, 800, 20, 160, 20, 10)
FUI_Label(TZones, GUE_width - 224, 10, "Current zone:")
Global CZone = FUI_ComboBox(TZones, GUE_width - 224, 27, 210, 20, 10)


FUI_ComboBoxItem(CZone, "New zone")
For Ar.Area = Each Area
	Item = FUI_ComboBoxItem(CZone, Ar\Name$)
	FUI_SendMessage(Item, M_SETDATA, Handle(Ar))
Next
FUI_SendMessage(CZone, M_SETINDEX, 1)

;OLD
; Helpful labels
;FUI_Label(TZones, 750, 637, "Hold the SPACE key for mouse-look mode")
;LZonePos = FUI_Label(TZones, 750, 652, "X/Y/Z position...")
; Mode buttons
;Global BZoneSelect = FUI_Button(TZones, 10, 640, 100, 20, "Select", 0, 2)
;Global BZoneMove   = FUI_Button(TZones, 120, 640, 100, 20, "Move", 0, 2)
;Global BZoneRotate = FUI_Button(TZones, 230, 640, 100, 20, "Rotate", 0, 2)
;Global BZoneScale  = FUI_Button(TZones, 340, 640, 100, 20, "Scale", 0, 2)
;FUI_SendMessage(BZoneSelect, M_SETSELECTED, True)
;BZonePrecise = FUI_Button(TZones, 450, 640, 100, 20, "Precise")
;NEW
FUI_Label(TZones, 20, GUE_height - 80, "Hold the SPACE key for mouse-look mode")
LZonePos = FUI_Label(TZones, 300, GUE_height - 80, "X/Y/Z position...")

; Mode buttons
Global BZoneSelect = FUI_Button(TZones, 540, 27, 70, 20, "Select", 0, 2) : FUI_ToolTip(BZoneSelect, "Select, F1")
Global BZoneMove   = FUI_Button(TZones, 615, 27, 70, 20, "Move", 0, 2) : FUI_ToolTip(BZoneMove, "Move, F2")
Global BZoneRotate = FUI_Button(TZones, 690, 27, 70, 20, "Rotate", 0, 2) : FUI_ToolTip(BZoneRotate, "Rotate, F3")
Global BZoneScale  = FUI_Button(TZones, 765, 27, 70, 20, "Scale", 0, 2) : FUI_ToolTip(BZoneScale, "Scale, F4")
FUI_SendMessage(BZoneSelect, M_SETSELECTED, True)
BZonePrecise = FUI_Button(TZones, 840, 27, 70, 20, "Precise")

; Camera speed
Global CamSpeed# = 2.5

;OLD
;FUI_Label(TZones, 565, 642, "Camera speed:")
;SCameraSpeed = FUI_Spinner(TZones, 645, 640, 70, 20, 1, 100, 20, 1, DTYPE_INTEGER, "%")
; 3D view
;VZone = FUI_View(TZones, 20, 50, 773, 580, 0, 0, 0)
;NEW
FUI_Label(TZones, 930, 10, "Camera speed:")
SCameraSpeed = FUI_Spinner(TZones, 930, 27, 70, 20, 1, 100, 20, 1, DTYPE_INTEGER, "%")

; 3D view
VZone = FUI_View(TZones, 20, 50, GUE_width - 251, GUE_height - 130, 0, 0, 0)

ZoneCam = FUI_SendMessage(VZone, M_GETCAMERA)
CameraFogMode(ZoneCam, 1)

;Dynamic Lights
Global GDynamicC = FUI_GroupBox(TZones, GUE_width - 224, 47, 210, GUE_height - 130, "Dynamic Light Placement")
;FUI_Label(GDynamicC, 5, 10, "Right click 3D view to place a light.")
FUI_Label(GDynamicC, 5, 10, "Dynamic Lights are handled via models.")
FUI_Label(GDynamicC, 5, 30, "Inside the Data\Meshes folder there")
FUI_Label(GDynamicC, 5, 45, "is a folder called 'LightModels'.")
FUI_Label(GDynamicC, 5, 60, "These are all the light models and")
FUI_Label(GDynamicC, 5, 75, "you can make as many as you like.")
FUI_Label(GDynamicC, 5, 90, "To add them to the zone simply select")
FUI_Label(GDynamicC, 5, 105, "the light you want with the meshes")
FUI_Label(GDynamicC, 5, 120, "tool and place them into the zone.")

FUI_Label(GDynamicC, 5, 150, "The colour and range of the light is")
FUI_Label(GDynamicC, 5, 165, "set in the models name. For example:")
FUI_Label(GDynamicC, 5, 185, "light_1.0_250_160_25")
FUI_Label(GDynamicC, 5, 205, "The model must start with 'light_', the")
FUI_Label(GDynamicC, 5, 220, "range is 1.0 and the R G B code is")
FUI_Label(GDynamicC, 5, 235, "250, 160, 25. All light models must be")
FUI_Label(GDynamicC, 5, 250, "imported before use.")


; Scenery placement
;Global GScenery = FUI_GroupBox(TZones, 800, 47, 165, 580, "Scenery placement")
Global GScenery = FUI_GroupBox(TZones, GUE_width - 224, 47, 210, GUE_height - 130, "Scenery placement")


	BSceneryAlign = FUI_CheckBox(GScenery, 5, 10, "Align to ground")
	FUI_Label(GScenery, 100, 40, "Selected mesh:", ALIGN_CENTER)
	Label$ = EditorMeshName$(0)
	For i = Len(Label$) To 1 Step -1
		If Mid$(Label$, i, 1) = "/" Or Mid$(Label$, i, 1) = "\" Then Label$ = Mid$(Label$, i + 1) : Exit
	Next
	LScenery = FUI_Label(GScenery, 100, 60, Label$, ALIGN_CENTER)
	BScenery = FUI_Button(GScenery, 50, 90, 100, 20, "Change")
	ZoneSceneryMeshID = 0

; Scenery options
;Global GSceneryOptions = FUI_GroupBox(TZones, 800, 47, 165, 580, "Scenery options")
Global GSceneryOptions = FUI_GroupBox(TZones, GUE_width - 224, 47, 210, GUE_height - 130, "Scenery options")


	FUI_Label(GSceneryOptions, 5, 10, "Animation mode:")
	Global CSceneryAnim = FUI_ComboBox(GSceneryOptions, 5, 30, 155, 20)
	FUI_ComboBoxItem(CSceneryAnim, "None")
	FUI_ComboBoxItem(CSceneryAnim, "Constant loop")
	FUI_ComboBoxItem(CSceneryAnim, "Constant ping-pong")
	FUI_ComboBoxItem(CSceneryAnim, "When selected")
	FUI_Label(GSceneryOptions, 5, 70, "Inventory size:")
	Global SSceneryInvenSize = FUI_Spinner(GSceneryOptions, 5, 90, 100, 20, 0, 50, 0, 1, DTYPE_INTEGER)
	FUI_Label(GSceneryOptions, 5, 130, "Collision mode:")
	Global CSceneryCollision = FUI_ComboBox(GSceneryOptions, 5, 150, 100, 20)
	FUI_ComboBoxItem(CSceneryCollision, "None")
	FUI_ComboBoxItem(CSceneryCollision, "Sphere")
	FUI_ComboBoxItem(CSceneryCollision, "Box")
	FUI_ComboBoxItem(CSceneryCollision, "Polygon")
	
FUI_Label(GSceneryOptions, 5, 190, "Render Range:")
Global CSceneryLodType = FUI_ComboBox(GSceneryOptions, 5, 210, 100, 20)
FUI_ComboBoxItem(CSceneryLodType, "Always Render")
FUI_ComboBoxItem(CSceneryLodType, "Short")
FUI_ComboBoxItem(CSceneryLodType, "Long")

Global BSceneryOwnable = FUI_CheckBox(GSceneryOptions, 5, 270, "Scenery can be owned")
Global BSceneryCatchRain = FUI_CheckBox(GSceneryOptions, 5, 300, "Catch rain/snow particles")
Global BSceneryLocked = FUI_CheckBox(GSceneryOptions, 5, 400, "Lock this object")
BSceneryDuplicate = FUI_Button(GSceneryOptions, 30, 450, 100, 20, "Duplicate") : FUI_ToolTip(BSceneryDuplicate, "Duplicate selected Mesh, Ctrl + D")
Global LSceneryID = FUI_Label(GSceneryOptions, 5, 500, "Ownership ID: N/A")
;v1.104 options cysis145 [010] 
Global BSceneryCast = FUI_CheckBox(GSceneryOptions, 5, 330, "Object Casts Shadows")
Global BSceneryReceive = FUI_CheckBox(GSceneryOptions, 5, 360, "Object Receives Shadows")
	

; Terrain placement
;Global GTerrain = FUI_GroupBox(TZones, 800, 47, 165, 580, "Terrain placement")
Global GTerrain = FUI_GroupBox(TZones, GUE_width - 224, 47, 210, GUE_height - 130, "Terrain placement")

	FUI_Label(GTerrain, 5, 10, "Current heightmap:")
	Global LTerrain = FUI_Label(GTerrain, 100, 30, "None (flat terrain)", ALIGN_CENTER)
	BTerrainHM = FUI_Button(GTerrain, 25, 60, 144, 20, "Change heightmap")
	BTerrainFlat = FUI_Button(GTerrain, 25, 90, 144, 20, "Reset")
	FUI_Label(GTerrain, 5, 130, "Select a heightmap, then right")
	FUI_Label(GTerrain, 5, 145, "click 3D view to place terrain")
	TerrainHMFilename$ = ""

; Terrain options
;Global GTerrainOptions = FUI_GroupBox(TZones, 800, 47, 165, 580, "Terrain options")
Global GTerrainOptions = FUI_GroupBox(TZones, GUE_width - 224, 47, 210, GUE_height - 130, "Terrain options")

	BTerrainCM = FUI_Button(GTerrainOptions, 5, 10, 155, 20, "Change colour map")
	BTerrainDM = FUI_Button(GTerrainOptions, 5, 40, 155, 20, "Change detail map")
	FUI_Label(GTerrainOptions, 5, 72, "Detail map scale:")
	Global STerrainDetailScale = FUI_Spinner(GTerrainOptions, 5, 90, 90, 20, 0.1, 100.0, 15.0, 0.1, DTYPE_FLOAT)
	FUI_Label(GTerrainOptions, 5, 122, "Maximum triangles:")
	Global STerrainDetail = FUI_Spinner(GTerrainOptions, 5, 140, 90, 20, 500, 50000, 2500, 1, DTYPE_INTEGER)
	Global BTerrainMorph = FUI_CheckBox(GTerrainOptions, 5, 170, "Enable morphing")
	Global BTerrainShade = FUI_CheckBox(GTerrainOptions, 5, 190, "Enable shading")

; Emitter placement panel
;Global GEmitters = FUI_GroupBox(TZones, 800, 47, 165, 580, "Emitter placement")
Global GEmitters = FUI_GroupBox(TZones, GUE_width - 224, 47, 210, GUE_height - 130, "Emitter placement")

	Global LEmitter = FUI_ListBox(GEmitters, 5, 10, 195, GUE_height - 165)
	For C.RP_EmitterConfig = Each RP_EmitterConfig
		Item = FUI_ListBoxItem(LEmitter, C\Name$) : FUI_SendMessage(Item, M_SETDATA, Handle(C))
	Next
	FUI_SendMessage(LEmitter, M_SETINDEX, 1)

; Emitter options panel
;Global GEmitterOptions = FUI_GroupBox(TZones, 800, 47, 165, 580, "Emitter options")
Global GEmitterOptions = FUI_GroupBox(TZones, GUE_width - 224, 47, 210, GUE_height - 130, "Emitter options")

	Global BEmitterTex = FUI_Button(GEmitterOptions, 5, 10, 144, 20, "Change texture")

; Water placement panel
;Global GWater = FUI_GroupBox(TZones, 800, 47, 165, 580, "Water placement")
Global GWater = FUI_GroupBox(TZones, GUE_width - 224, 47, 210, GUE_height - 130, "Water placement")

	FUI_Label(GWater, 5, 10, "Right click to place a water plane")

; Water options panel
;Global GWaterOptions = FUI_GroupBox(TZones, 800, 47, 165, 580, "Water options")
Global GWaterOptions = FUI_GroupBox(TZones, GUE_width - 224, 47, 210, GUE_height - 130, "Water options")

	FUI_Label(GWaterOptions, 5, 10, "Water colour:")
	FUI_Label(GWaterOptions, 5, 32, "Red:")
	Global SLWaterR = FUI_Slider(GWaterOptions, 45, 30, 155, 20, 0, 255, 0)
	FUI_Label(GWaterOptions, 5, 52, "Green:")
	Global SLWaterG = FUI_Slider(GWaterOptions, 45, 50, 155, 20, 0, 255, 0)
	FUI_Label(GWaterOptions, 5, 72, "Blue:")
	Global SLWaterB = FUI_Slider(GWaterOptions, 45, 70, 155, 20, 0, 255, 0)
	Global BWaterTex = FUI_Button(GWaterOptions, 30, 100, 155, 20, "Change surface texture")
	FUI_Label(GWaterOptions, 5, 130, "Texture scale:")
	Global SWaterTexScale = FUI_Spinner(GWaterOptions, 5, 150, 195, 20, 0.1, 100.0, 15.0, 0.1, DTYPE_FLOAT)
	FUI_Label(GWaterOptions, 5, 180, "Opacity:")
	Global SLWaterOpacity = FUI_Slider(GWaterOptions, 5, 200, 195, 20, 1, 100, 50)
	FUI_Label(GWaterOptions, 5, 230, "Damage per second:")
	Global SWaterDamage = FUI_Spinner(GWaterOptions, 5, 250, 195, 20, 0, 1000, 0, 1, DTYPE_INTEGER)
	FUI_Label(GWaterOptions, 5, 280, "Damage type:")
	Global CWaterDamageType = FUI_ComboBox(GWaterOptions, 5, 300, 195, 20, 8)
	For i = 0 To 19
		If DamageTypes$(i) <> ""
			Item = FUI_ComboBoxItem(CWaterDamageType, DamageTypes$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
		EndIf
	Next

; Sound placement panel
;Global GSound = FUI_GroupBox(TZones, 800, 47, 165, 580, "Sound zone placement")
Global GSound = FUI_GroupBox(TZones, GUE_width - 224, 47, 210, GUE_height - 130, "Sound zone placement")

	FUI_Label(GSound, 100, 10, "Selected sound:", ALIGN_CENTER)
	LSound = FUI_Label(GSound, 100, 30, "[NONE]", ALIGN_CENTER)
	BZoneSound = FUI_Button(GSound, 50, 60, 100, 20, "Choose sound")
	BZoneMusic = FUI_Button(GSound, 50, 90, 100, 20, "Choose music")
	ZoneSoundID = -1
	ZoneMusicID = -1

; Sound options panel
;Global GSoundOptions = FUI_GroupBox(TZones, 800, 47, 165, 580, "Sound zone options")
Global GSoundOptions = FUI_GroupBox(TZones, GUE_width - 224, 47, 210, GUE_height - 130, "Sound zone options")

	FUI_Label(GSoundOptions, 5, 10, "Sound name:")
	Global LSoundName = FUI_Label(GSoundOptions, 100, 30, "", ALIGN_CENTER)
	FUI_Label(GSoundOptions, 5, 60, "Repeat time:")
	Global SSoundRepeat = FUI_Spinner(GSoundOptions, 5, 80, 195, 20, -1, 1000, 0, 1, DTYPE_INTEGER)
	FUI_Label(GSoundOptions, 5, 110, "Volume:")
	Global SSoundVolume = FUI_Spinner(GSoundOptions, 5, 130, 195, 20, 1, 100, 0, 1, DTYPE_INTEGER)

; Trigger placement panel
;Global GTrigger = FUI_GroupBox(TZones, 800, 47, 165, 580, "Trigger placement")
Global GTrigger = FUI_GroupBox(TZones, GUE_width - 224, 47, 210, GUE_height - 130, "Trigger placement")

	FUI_Label(GTrigger, 5, 10, "Trigger script:")
	Global CZoneTriggerScript = FUI_ComboBox(GTrigger, 5, 30, 195, 20, 10)
	FUI_ToolTip(CZoneTriggerScript, "Tip: Scripts must start with  Trigger_  to be seen here.")
	FUI_Label(GTrigger, 5, 60, "Script function:")
	Global CZoneTriggerMethod = FUI_ComboBox(GTrigger, 5, 80, 195, 20, 10)

; Trigger options panel
;Global GTriggerOptions = FUI_GroupBox(TZones, 800, 47, 165, 580, "Selected trigger")
Global GTriggerOptions = FUI_GroupBox(TZones, GUE_width - 224, 47, 210, GUE_height - 130, "Selected trigger")

	FUI_Label(GTriggerOptions, 5, 10, "Trigger script:")
	Global LTriggerScript = FUI_Label(GTriggerOptions, 100, 30, "", ALIGN_CENTER)
	FUI_Label(GTriggerOptions, 5, 60, "Script function:")
	Global LTriggerMethod = FUI_Label(GTriggerOptions, 100, 80, "", ALIGN_CENTER)
	FUI_Label(GTriggerOptions, 5, 110, "Trigger ID:")
	Global LZoneTriggerID = FUI_Label(GTriggerOptions, 100, 130, "", ALIGN_CENTER)

; Waypoint placement panel
;Global GWaypoint = FUI_GroupBox(TZones, 800, 47, 165, 580, "Waypoint placement")
Global GWaypoint = FUI_GroupBox(TZones, GUE_width - 224, 47, 210, GUE_height - 130, "Waypoint placement")

	FUI_Label(GWaypoint, 5, 10, "Right click 3D view to place a waypoint")

; Waypoint options panel
;Global GWaypointOptions = FUI_GroupBox(TZones, 800, 47, 165, 580, "Waypoint options")
Global GWaypointOptions = FUI_GroupBox(TZones, GUE_width - 224, 47, 210, GUE_height - 130, "Waypoint options")

	Global BWaypointA = FUI_Button(GWaypointOptions, 5, 10, 170, 20, "Set next waypoint A")
	Global BWaypointB = FUI_Button(GWaypointOptions, 5, 40, 170, 20, "Set next waypoint B")
	Global BWaypointAN = FUI_Button(GWaypointOptions, 180, 10, 20, 20, "N")
	Global BWaypointBN = FUI_Button(GWaypointOptions, 180, 40, 20, 20, "N")
	FUI_Label(GWaypointOptions, 5, 70, "Pause here for:")
	Global SWaypointPause = FUI_Spinner(GWaypointOptions, 5, 90, 195, 20, 0, 120, 0, 1, DTYPE_INTEGER, " seconds")
	FUI_Label(GWaypointOptions, 5, 120, "Spawn this actor:")
	Global CSpawnActor = FUI_ComboBox(GWaypointOptions, 5, 140, 195, 20, 15)
	FUI_ComboBoxItem(CSpawnActor, "None")
	FUI_Label(GWaypointOptions, 5, 170, "Spawn script:")
	Global CSpawnScript = FUI_ComboBox(GWaypointOptions, 5, 190, 195, 20, 10)
	FUI_ToolTip(CSpawnScript, "Tip: Scripts must start with  Spawn_  to be seen here.")
	FUI_ComboBoxItem(CSpawnScript, "None")
	FUI_Label(GWaypointOptions, 5, 220, "Right-click script:")
	Global CSpawnActorScript = FUI_ComboBox(GWaypointOptions, 5, 240, 195, 20, 10)
	FUI_ToolTip(CSpawnActorScript, "Tip: Scripts must start with  Click_  to be seen here.")
	FUI_ComboBoxItem(CSpawnActorScript, "None")
	FUI_Label(GWaypointOptions, 5, 270, "Death script:")
	Global CSpawnDeathScript = FUI_ComboBox(GWaypointOptions, 5, 290, 195, 20, 10)
	FUI_ToolTip(CSpawnDeathScript, "Tip: Scripts must start with  Death_  to be seen here.")
	FUI_ComboBoxItem(CSpawnDeathScript, "None")
	FUI_Label(GWaypointOptions, 5, 320, "Spawn delay:")
	Global SSpawnFrequency = FUI_Spinner(GWaypointOptions, 5, 340, 195, 20, 0, 10000, 5, 1, DTYPE_INTEGER, " seconds")
	FUI_Label(GWaypointOptions, 5, 370, "Number to spawn:")
	Global SSpawnMax = FUI_Spinner(GWaypointOptions, 5, 390, 195, 20, 1, 100, 5, 1, DTYPE_INTEGER)
	FUI_Label(GWaypointOptions, 5, 420, "Auto-movement range:")
	Global SSpawnRange = FUI_Spinner(GWaypointOptions, 5, 440, 195, 20, 0, 10000, 0, 1, DTYPE_FLOAT)
	For A.Actor = Each Actor
		ActorName$ = A\Race$ + " [" + A\Class$ + "]"
		If Len(ActorName$) > 20 Then ActorName$ = Left$(ActorName$, 17) + "..."
		Item = FUI_ComboBoxItem(CSpawnActor, ActorName$) : FUI_SendMessage(Item, M_SETDATA, A\ID)
	Next
	FUI_SendMessage(CSpawnActor, M_SETINDEX, 1)

; Portal placement panel
;Global GPortal = FUI_GroupBox(TZones, 800, 47, 165, 580, "Portal placement")
Global GPortal = FUI_GroupBox(TZones, GUE_width - 224, 47, 210, GUE_height - 130, "Portal placement")

	FUI_Label(GPortal, 5, 10, "Portal name:")
	Global TPortalName = FUI_TextBox(GPortal, 5, 30, 195, 20)
	FUI_Label(GPortal, 5, 60, "Linked area:")
	Global CPortalLinkArea = FUI_ComboBox(GPortal, 5, 80, 195, 20, 10)
	FUI_ComboBoxItem(CPortalLinkArea, "None")
	FUI_Label(GPortal, 5, 110, "Linked portal name:")
	Global CPortalLinkName = FUI_ComboBox(GPortal, 5, 130, 195, 20, 10)

; Portal options panel
;Global GPortalOptions = FUI_GroupBox(TZones, 800, 47, 165, 580, "Selected portal")
Global GPortalOptions = FUI_GroupBox(TZones, GUE_width - 224, 47, 210, GUE_height - 130, "Selected portal")

	FUI_Label(GPortalOptions, 5, 10, "Portal name:")
	Global LPortalName = FUI_Label(GPortalOptions, 100, 30, "", ALIGN_CENTER)
	FUI_Label(GPortalOptions, 5, 60, "Linked area:")
	Global CPortalLinkAreaO = FUI_ComboBox(GPortalOptions, 5, 80, 195, 20, 10)
	FUI_ComboBoxItem(CPortalLinkAreaO, "None")
	FUI_Label(GPortalOptions, 5, 110, "Linked portal name:")
	Global CPortalLinkNameO = FUI_ComboBox(GPortalOptions, 5, 130, 195, 20, 10)

; Environment options panel
Dim SWeatherChance(4)

;Global GEnviroOptions = FUI_GroupBox(TZones, 800, 47, 165, 580, "Environment options")
Global GEnviroOptions = FUI_GroupBox(TZones, GUE_width - 224, 47, 210, GUE_height - 130, "Environment options")

	TabEnviroOptions = FUI_Tab(GEnviroOptions, 5, 5, 195, GUE_height - 165)
	TEnviroVisual  = FUI_TabPage(TabEnviroOptions, "FX")
	TEnviroWeather = FUI_TabPage(TabEnviroOptions, "Weather")
	TEnviroOther   = FUI_TabPage(TabEnviroOptions, "Other")

	Global BOutdoors = FUI_CheckBox(TEnviroVisual, 5, 10, "Zone is outdoors")
	Global BSkyTex        = FUI_Button(TEnviroVisual, 5, 35, 85, 20, "Sky")
	Global BStarTex       = FUI_Button(TEnviroVisual, 100, 35, 85, 20, "Stars")
	Global BCloudTex      = FUI_Button(TEnviroVisual, 5, 60, 85, 20, "Clouds")
	Global BStormCloudTex = FUI_Button(TEnviroVisual, 100, 60, 85, 20, "Stormclouds")
	FUI_Label(TEnviroVisual, 5, 102, "Minimum fog range:")
	Global SLFogNear = FUI_Slider(TEnviroVisual, 5, 120, 180, 20, -100.0, MaxFogFar# - 100.0, 300.0)
	FUI_Label(TEnviroVisual, 5, 152, "Maximum fog range:")
	Global SLFogFar = FUI_Slider(TEnviroVisual, 5, 170, 180, 20, 50.0, MaxFogFar#, 500.0)
	FUI_Label(TEnviroVisual, 5, 200, "Fog colour:")
	FUI_Label(TEnviroVisual, 5, 222, "Red:")
	Global SLFogR = FUI_Slider(TEnviroVisual, 45, 220, 140, 20, 0, 255, 0)
	FUI_Label(TEnviroVisual, 5, 242, "Green:")
	Global SLFogG = FUI_Slider(TEnviroVisual, 45, 240, 140, 20, 0, 255, 0)
	FUI_Label(TEnviroVisual, 5, 262, "Blue:")
	Global SLFogB = FUI_Slider(TEnviroVisual, 45, 260, 140, 20, 0, 255, 0)
	FUI_Label(TEnviroVisual, 5, 290, "Ambient light colour:")
	FUI_Label(TEnviroVisual, 5, 312, "Red:")
	Global SLAmbientR = FUI_Slider(TEnviroVisual, 45, 310, 140, 20, 0, 255, 0)
	FUI_Label(TEnviroVisual, 5, 332, "Green:")
	Global SLAmbientG = FUI_Slider(TEnviroVisual, 45, 330, 140, 20, 0, 255, 0)
	FUI_Label(TEnviroVisual, 5, 352, "Blue:")
	Global SLAmbientB = FUI_Slider(TEnviroVisual, 45, 350, 140, 20, 0, 255, 0)
	FUI_Label(TEnviroVisual, 5, 392, "Default light pitch:")
	Global SDefaultLightPitch = FUI_Spinner(TEnviroVisual, 5, 410, 100, 20, -90, 90, 0, 1.0, DTYPE_FLOAT)
	FUI_Label(TEnviroVisual, 5, 432, "Default light yaw:")
	Global SDefaultLightYaw = FUI_Spinner(TEnviroVisual, 5, 450, 100, 20, -180, 180, 0, 1.0, DTYPE_FLOAT)

	FUI_Label(TEnviroWeather, 5, 10, "Weather probabilities:")
	FUI_Label(TEnviroWeather, 5, 32, "Rain:")
	SWeatherChance(W_Rain - 1) = FUI_Spinner(TEnviroWeather, 115, 30, 70, 20, 0, 100, 0, 1, DTYPE_INTEGER, "%")
	FUI_Label(TEnviroWeather, 5, 52, "Snow:")
	SWeatherChance(W_Snow - 1) = FUI_Spinner(TEnviroWeather, 115, 50, 70, 20, 0, 100, 0, 1, DTYPE_INTEGER, "%")
	FUI_Label(TEnviroWeather, 5, 72, "Fog:")
	SWeatherChance(W_Fog - 1) = FUI_Spinner(TEnviroWeather, 115, 70, 70, 20, 0, 100, 0, 1, DTYPE_INTEGER, "%")
	FUI_Label(TEnviroWeather, 5, 92, "Storm:")
	SWeatherChance(W_Storm - 1) = FUI_Spinner(TEnviroWeather, 115, 90, 70, 20, 0, 100, 0, 1, DTYPE_INTEGER, "%")
	FUI_Label(TEnviroWeather, 5, 112, "Wind:")
	SWeatherChance(W_Wind - 1) = FUI_Spinner(TEnviroWeather, 115, 110, 70, 20, 0, 100, 0, 1, DTYPE_INTEGER, "%")
	FUI_Label(TEnviroWeather, 5, 142, "Link weather to this area:")
	Global CWeatherLink = FUI_ComboBox(TEnviroWeather, 5, 160, 180, 20, 10)
	FUI_ComboBoxItem(CWeatherLink, "None")

	FUI_Label(TEnviroOther, 5, 12, "Gravity:")
	Global SGravity = FUI_Spinner(TEnviroOther, 50, 10, 80, 20, -200, 800, 100, 1, DTYPE_INTEGER, "%")
	FUI_Label(TEnviroOther, 5, 42, "Slope restriction:")
	Global SSlopeRestrict = FUI_Spinner(TEnviroOther, 10, 70, 80, 20, 0.0, 1.0, 0.6, 0.01, DTYPE_FLOAT)

; Other options panel
;Global GOtherOptions = FUI_GroupBox(TZones, 800, 47, 165, 580, "Other options")
Global GOtherOptions = FUI_GroupBox(TZones, GUE_width - 224, 47, 210, GUE_height - 130, "Other options")

	FUI_Label(GOtherOptions, 5, 10, "Entry script:")
	Global CEntryScript = FUI_ComboBox(GOtherOptions, 5, 30, 195, 20, 10)
	FUI_ToolTip(CEntryScript, "Tip: Scripts must start with  Entry_  to be seen here.")
	
	FUI_ComboBoxItem(CEntryScript, "None")
	FUI_Label(GOtherOptions, 5, 60, "Exit script:")
	Global CExitScript = FUI_ComboBox(GOtherOptions, 5, 80, 195, 20, 10)
	FUI_ToolTip(CExitScript, "Tip: Scripts must start with  Exit_  to be seen here.")
	
	FUI_ComboBoxItem(CExitScript, "None")
	Global BPvP = FUI_CheckBox(GOtherOptions, 5, 120, "PvP enabled")
	Global LLoadingTex = FUI_Label(GOtherOptions, 5, 150, "Load image: [NONE]")
	BLoadingTex = FUI_Button(GOtherOptions, 5, 170, 70, 20, "Change")
	BLoadingTexN = FUI_Button(GOtherOptions, 90, 170, 60, 20, "None")
	Global LLoadingMusic = FUI_Label(GOtherOptions, 5, 200, "Load music: [NONE]")
	BLoadingMusic = FUI_Button(GOtherOptions, 5, 220, 70, 20, "Change")
	BLoadingMusicN = FUI_Button(GOtherOptions, 90, 220, 60, 20, "None")
	Global LLargeMapTex = FUI_Label(GOtherOptions, 5, 250, "Map texture: [NONE]")
	BLargeMapTex = FUI_Button(GOtherOptions, 5, 270, 70, 20, "Change")
	BScaleEntireZone = FUI_Button(GOtherOptions, 50, 320, 110, 25, "Scale entire zone")

; Spells gadgets --------------------------------------------------------------------------------------------------------------------

updateSplashScreen("Creating abilities tab")

; Main
BSpellNew    = FUI_Button(TSpells, 20, 20, 100, 20, "New ability")
BSpellDelete = FUI_Button(TSpells, 150, 20, 100, 20, "Delete ability")
BSpellSave   = FUI_Button(TSpells, 280, 20, 100, 20, "Save abilities")
Global SelectedSpell.Spell
; Selection
FUI_Label(TSpells, 20, 62, "Current ability:")
Global CSpellSelected = FUI_ComboBox(TSpells, 100, 60, 400, 20, 20)
BSpellPrev = FUI_Button(TSpells, 520, 60, 25, 20, "<<") : FUI_ToolTip(BSpellPrev, "Previous ability")
BSpellNext = FUI_Button(TSpells, 580, 60, 25, 20, ">>") : FUI_ToolTip(BSpellNext, "Next ability")
For Sp.Spell = Each Spell
	Item = FUI_ComboBoxItem(CSpellSelected, Sp\Name$) : FUI_SendMessage(Item, M_SETDATA, Sp\ID)
Next
If TotalSpells = 0
	FUI_ComboBoxItem(CSpellSelected, "No abilities created...")
	FUI_SendMessage(CSpellSelected, M_SETINDEX, 1)
	FUI_SendMessage(CSpellSelected, M_DELETEINDEX, 1)
EndIf
; Properties
FUI_Label(TSpells, 20, 112, "Ability name:")
Global TSpellName = FUI_TextBox(TSpells, 120, 110, 250, 20)
FUI_Label(TSpells, 20, 142, "Ability description:")
Global TSpellDesc = FUI_TextBox(TSpells, 120, 140, 700, 20)
Global LSpellImageID = FUI_Label(TSpells, 20, 182, "Display icon: [NONE]")
BSpellImageID = FUI_Button(TSpells, 220, 180, 90, 20, "Change")
FUI_Label(TSpells, 20, 237, "Recharge time:")
Global SSpellCharge = FUI_Spinner(TSpells, 120, 235, 100, 20, 0, 60, 0, 1, DTYPE_INTEGER, " seconds")
FUI_Label(TSpells, 20, 282, "Ability is exclusive to this race:")
Global CSpellExclusiveRace = FUI_ComboBox(TSpells, 250, 280, 350, 20, 10)
FUI_ComboBoxItem(CSpellExclusiveRace, "None (can be used by any race)")
FUI_Label(TSpells, 20, 312, "Ability is exclusive to this class:")
Global CSpellExclusiveClass = FUI_ComboBox(TSpells, 250, 310, 350, 20, 10)
FUI_ComboBoxItem(CSpellExclusiveClass, "None (can be used by any class)")
FUI_Label(TSpells, 20, 362, "Script to run for this ability:")
Global CSpellScript = FUI_ComboBox(TSpells, 250, 360, 350, 20, 10)
FUI_ToolTip(CSpellScript, "Tip: Scripts must start with  Spell_  to be seen here.")
FUI_ComboBoxItem(CSpellScript, "None")
FUI_Label(TSpells, 20, 392, "Function to start script in:")
Global CSpellMethod = FUI_ComboBox(TSpells, 250, 390, 350, 20, 10)

; Init display
FUI_SendMessage(CSpellSelected, M_SETINDEX, 1)
UpdateSpellDisplay()

; Interface gadgets -----------------------------------------------------------------------------------------------------------------

updateSplashScreen("Creating interface tab")

BInterfaceSave = FUI_Button(TInterface, 850, 20, 130, 20, "Save interface layout")
VInterface = FUI_View(TInterface, 10, 20, 800, 600, 0, 0, 0)
FUI_Label(TInterface, 1000, 52, "Interface components")
Global RInterfaceMain = FUI_Radio(TInterface, 850, 70, "Game screen", True, 1)
Global RInterfaceInventory = FUI_Radio(TInterface, 850, 100, "Inventory", False, 1)

;Global RInterfaceLogin = FUI_Radio(Tinterface, 1300, 130, "Login", False, 1) ;[255]
;Global RInterfaceCharS = FUI_Radio(Tinterface, 1300, 160, "Character Select", False, 1)
;Global RInterfaceCharC = FUI_Radio(Tinterface, 1300, 190, "Character Creation", False, 1)

Global LInterfaceComponents = FUI_ListBox(TInterface, 1000, 70, 200, 600)
G = FUI_GroupBox(TInterface, 800, 700, 500, 150, "Component settings")
FUI_Label(G, 10, 12, "X position:")
FUI_Label(G, 10, 42, "Y position:")
FUI_Label(G, 10, 72, "Width:")
FUI_Label(G, 10, 102, "Height:")
Global SInterfaceX      = FUI_Spinner(G, 80, 10,  90, 20, 0, 100, 0, 0.1, DTYPE_FLOAT, "%")
Global SInterfaceY      = FUI_Spinner(G, 80, 40,  90, 20, 0, 100, 0, 0.1, DTYPE_FLOAT, "%")
Global SInterfaceWidth  = FUI_Spinner(G, 80, 70,  90, 20, 0, 100, 0, 0.1, DTYPE_FLOAT, "%")
Global SInterfaceHeight = FUI_Spinner(G, 80, 100, 90, 20, 0, 100, 0, 0.1, DTYPE_FLOAT, "%")
FUI_Label(G, 260, 12, "Red:")
FUI_Label(G, 260, 42, "Green:")
FUI_Label(G, 260, 72, "Blue:")
FUI_Label(G, 260, 102, "Alpha:")
Global SInterfaceR = FUI_Slider(G, 330, 10, 110, 20, 0, 255, 0)
Global SInterfaceG = FUI_Slider(G, 330, 40, 110, 20, 0, 255, 0)
Global SInterfaceB = FUI_Slider(G, 330, 70, 110, 20, 0, 255, 0)
Global SInterfaceA = FUI_Slider(G, 330, 100, 110, 20, 0, 255, 0)

FUI_Label(TInterface, 850, 510, "Chat text area background:");, ALIGN_CENTER)
LInterfaceChatTex = FUI_Label(TInterface, 920, 540, EditorTexName$(Chat\Texture), ALIGN_CENTER)
BInterfaceChatTex = FUI_Button(TInterface, 875, 570, 90, 20, "Change")
BInterfaceChatTexN = FUI_Button(TInterface, 875, 600, 90, 20, "None")

Global SelectedIC.InterfaceComponent = First InterfaceComponent
For IC.InterfaceComponent = Each InterfaceComponent
	UpdateInterfaceComponent(IC)
Next
FUI_SendMessage(RInterfaceInventory, M_SETCHECKED, True) ;true

FUI_SendMessage(RInterfaceMain, M_SETCHECKED, False) ;[255]
;FUI_SendMessage(RInterfaceLogin, M_SETCHECKED, False)

UpdateInterfaceComponent(InventoryDrop)
UpdateInterfaceComponent(InventoryEat)
UpdateInterfaceComponent(InventoryGold)
For i = 0 To Slots_Inventory
	UpdateInterfaceComponent(InventoryButtons(i))
Next
FUI_SendMessage(RInterfaceMain, M_SETCHECKED, True) ;[255] true
FUI_SendMessage(RInterfaceInventory, M_SETCHECKED, False)

;FUI_SendMessage(RInterfaceLogin, M_SETCHECKED, False)

UpdateInterfaceComponentsList() ;[255]

For IC.InterfaceComponent = Each InterfaceComponent
	HideEntity(IC\Component)
Next

; Other options gadgets -------------------------------------------------------------------------------------------------------------

; Host options
WriteLog(GUELog, "Creating host options")
F = ReadFile("Data\Game Data\Hosts.dat")
If F = 0 Then RuntimeError("Could not open Data\Game Data\Hosts.dat!")
	ReadLine$(F)
	ReadLine$(F)
	AccountsEnabled = ReadLine$(F)
CloseFile(F)
F = ReadFile("Data\Server Data\Misc.dat")
If F = 0 Then RuntimeError("Could not open Data\Server Data\Misc.dat!")
	SeekFile F, 16
	MaxAccountChars = ReadByte(F)
	ServerPort = ReadInt(F)
	If ServerPort = 0 Then ServerPort = 25000
CloseFile(F)
G = FUI_GroupBox(TOther, 10, 20, 620, 200, "Hosts")
FUI_Label(G, 10, 5, "URL for the auto-update system to download game updates from (optional):")
TUpdatesHost = FUI_TextBox(G, 10, 21, 600, 20)
FUI_SendMessage(TUpdatesHost, M_SETCAPTION, UpdatesHost$)
FUI_Label(G, 10, 52, "Host name or IP address for the game server:")
Global TServerHost = FUI_TextBox(G, 10, 68, 600, 20)
FUI_ToolTip(TServerHost, "Tip: Enter localhost to run the game client on the same computer as the server for testing purposes")
FUI_SendMessage(TServerHost, M_SETCAPTION, ServerHost$)
FUI_Label(G, 10, 92, "Port number for the game server:")
Global TServerPort = FUI_TextBox(G, 10, 108, 600, 20)
FUI_SendMessage(TServerPort, M_SETCAPTION, ServerPort)
BNewAccounts = FUI_CheckBox(G, 10, 137, "Allow account creation from game client")
FUI_SendMessage(BNewAccounts, M_SETCHECKED, AccountsEnabled)
FUI_Label(G, 10, 162, "Maximum characters per account:")
SMaxAccountChars = FUI_Spinner(G, 185, 160, 50, 20, 1, 10, MaxAccountChars, 1, DTYPE_INTEGER)

; Game options
WriteLog(GUELog, "Creating game options")
F = ReadFile("Data\Server Data\Misc.dat")
If F = 0 Then RuntimeError("Could not open Data\Server Data\Misc.dat!")
	StartGold = ReadInt(F)
	StartReputation = ReadInt(F)
	ForcePortals = ReadByte(F)
CloseFile(F)
F = ReadFile("Data\Game Data\Other.dat")
If F = 0 Then RuntimeError("Could not open Data\Game Data\Other.dat!")
	HideNames = ReadByte(F)
	DisableCollisions = ReadByte(F)
	ViewMode = ReadByte(F)
	ReadInt(F) ; (Server port)
	RequireMemorise = ReadByte(F)
	UseBubbles = ReadByte(F)
	BubblesR = ReadByte(F)
	BubblesG = ReadByte(F)
	BubblesB = ReadByte(F)
CloseFile(F)
G = FUI_GroupBox(TOther, 10, 230, 230, 350, "Game")
FUI_Label(G, 10, 7, "Initial player money: ")
SStartGold = FUI_Spinner(G, 130, 5, 90, 20, 0, 5000, StartGold, 1, DTYPE_INTEGER)
FUI_Label(G, 10, 37, "Initial player reputation: ")
SStartReputation = FUI_Spinner(G, 130, 35, 90, 20, 0, 5000, StartReputation, 1, DTYPE_INTEGER)
BForcePortals = FUI_CheckBox(G, 10, 67, "Force portal transfers")
FUI_SendMessage(BForcePortals, M_SETCHECKED, ForcePortals)
FUI_Label(G, 10, 97, "Show nametags:")
CHideNametags = FUI_ComboBox(G, 100, 95, 120, 20)
FUI_ComboBoxItem(CHideNametags, "Always")
FUI_ComboBoxItem(CHideNametags, "Never")
FUI_ComboBoxItem(CHideNametags, "Only on selected")
FUI_SendMessage(CHideNametags, M_SETINDEX, HideNames + 1)
BDisableCollisions = FUI_CheckBox(G, 10, 127, "Disable actor -> actor collisions")
FUI_SendMessage(BDisableCollisions, M_SETCHECKED, DisableCollisions)
FUI_Label(G, 10, 157, "Valid view modes:")
CViewMode = FUI_ComboBox(G, 110, 155, 110, 20)
FUI_ComboBoxItem(CViewMode, "First person")
FUI_ComboBoxItem(CViewMode, "Third person")
FUI_ComboBoxItem(CViewMode, "Both")
If ViewMode = 1
	FUI_SendMessage(CViewMode, M_SETINDEX, 1)
ElseIf ViewMode = 2
	FUI_SendMessage(CViewMode, M_SETINDEX, 3)
ElseIf ViewMode = 3
	FUI_SendMessage(CViewMode, M_SETINDEX, 2)
EndIf
BRequireMemorise = FUI_CheckBox(G, 10, 187, "Require abilities to be memorised")
FUI_SendMessage(BRequireMemorise, M_SETCHECKED, RequireMemorise)
FUI_Label(G, 10, 212, "Use chat bubbles:")
CUseBubbles = FUI_ComboBox(G, 110, 210, 110, 20)
FUI_ComboBoxItem(CUseBubbles, "Never")
FUI_ComboBoxItem(CUseBubbles, "With text")
FUI_ComboBoxItem(CUseBubbles, "Exclusively")
FUI_SendMessage(CUseBubbles, M_SETINDEX, UseBubbles)
FUI_Label(G, 10, 237, "Chat bubble text colour:")
FUI_Label(G, 30, 257, "Red:")
FUI_Label(G, 30, 282, "Green:")
FUI_Label(G, 30, 307, "Blue:")
Global SBubblesR = FUI_Slider(G, 80, 255, 130, 20, 0, 255, 0)
Global SBubblesG = FUI_Slider(G, 80, 280, 130, 20, 0, 255, 0)
Global SBubblesB = FUI_Slider(G, 80, 305, 130, 20, 0, 255, 0)
FUI_SendMessage(SBubblesR, M_SETVALUE, BubblesR)
FUI_SendMessage(SBubblesG, M_SETVALUE, BubblesG)
FUI_SendMessage(SBubblesB, M_SETVALUE, BubblesB)

; Money options
WriteLog(GUELog, "Creating money options")
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
G = FUI_GroupBox(TOther, 250, 230, 380, 140, "Money")
FUI_Label(G, 10, 7, "Tier 1 (base units): ")
TMoney1Name = FUI_TextBox(G, 110, 5, 100, 20, 12)
FUI_SendMessage(TMoney1Name, M_SETTEXT, Money1$)
FUI_Label(G, 10, 37, "Tier 2: ")
TMoney2Name = FUI_TextBox(G, 110, 35, 100, 20, 12)
FUI_SendMessage(TMoney2Name, M_SETTEXT, Money2$)
SMoney2x = FUI_Spinner(G, 250, 35, 90, 20, 2, 10000, Money2x, 1, DTYPE_INTEGER, "x")
FUI_Label(G, 10, 67, "Tier 3: ")
TMoney3Name = FUI_TextBox(G, 110, 65, 100, 20, 12)
FUI_SendMessage(TMoney3Name, M_SETTEXT, Money3$)
SMoney3x = FUI_Spinner(G, 250, 65, 90, 20, 2, 10000, Money3x, 1, DTYPE_INTEGER, "x")
FUI_Label(G, 10, 97, "Tier 4: ")
TMoney4Name = FUI_TextBox(G, 110, 95, 100, 20, 12)
FUI_SendMessage(TMoney4Name, M_SETTEXT, Money4$)
SMoney4x = FUI_Spinner(G, 250, 95, 90, 20, 2, 10000, Money4x, 1, DTYPE_INTEGER, "x")

; Gubbin remapping
WriteLog(GUELog, "Creating gubbin remapping options")
LoadGubbinNames()
G = FUI_GroupBox(TOther, 250, 380, 380, 200, "Gubbin remapping")
FUI_Label(G, 10, 7, "You may specify the bone names to use for the six gubbins:")
FUI_Label(G, 10, 37, "Gubbin 1:")
FUI_Label(G, 10, 62, "Gubbin 2:")
FUI_Label(G, 10, 87, "Gubbin 3:")
FUI_Label(G, 10, 112, "Gubbin 4:")
FUI_Label(G, 10, 137, "Gubbin 5:")
FUI_Label(G, 10, 162, "Gubbin 6:")
TGubbin1 = FUI_TextBox(G, 110, 35, 150, 20, 40) : FUI_SendMessage(TGubbin1, M_SETTEXT, GubbinJoints$(0))
TGubbin2 = FUI_TextBox(G, 110, 60, 150, 20, 40) : FUI_SendMessage(TGubbin2, M_SETTEXT, GubbinJoints$(1))
TGubbin3 = FUI_TextBox(G, 110, 85, 150, 20, 40) : FUI_SendMessage(TGubbin3, M_SETTEXT, GubbinJoints$(2))
TGubbin4 = FUI_TextBox(G, 110, 110, 150, 20, 40) : FUI_SendMessage(TGubbin4, M_SETTEXT, GubbinJoints$(3))
TGubbin5 = FUI_TextBox(G, 110, 135, 150, 20, 40) : FUI_SendMessage(TGubbin5, M_SETTEXT, GubbinJoints$(4))
TGubbin6 = FUI_TextBox(G, 110, 160, 150, 20, 40) : FUI_SendMessage(TGubbin6, M_SETTEXT, GubbinJoints$(5))

; Init created gadgets --------------------------------------------------------------------------------------------------------------

updateSplashScreen("Initialising gadgets")

; Fill zone name lists
For Ar.Area = Each Area
	FUI_ComboBoxItem(CPortalLinkArea, Ar\Name$)
	FUI_ComboBoxItem(CPortalLinkAreaO, Ar\Name$)
	FUI_ComboBoxItem(CWeatherLink, Ar\Name$)
Next

; Portal/waypoint meshes
ZonePortalEN = LoadMesh("Data\GUE\Portal.b3d")
FitMesh(ZonePortalEN, -1, -1, -1, 2, 2, 2, True)
HideEntity(ZonePortalEN)
ZoneWaypointEN = LoadMesh("Data\GUE\Waypoint.b3d")
FitMesh(ZoneWaypointEN, -1, -1, -1, 2, 2, 2, True)
HideEntity(ZoneWaypointEN)
Global WPRangeEN = CreateSphere(16)
EntityAlpha(WPRangeEN, 0.5)
EntityColor(WPRangeEN, 0, 120, 255)
HideEntity(WPRangeEN)

; Script lists
Global LoadedScripts = 0
D = ReadDir("Data\Server Data\Scripts")
	File$ = NextFile$(D)
	While Len(File$) > 0
		If FileType("Data\Server Data\Scripts\" + File$) = 1
			If Len(File$) > 4
				If Right$(file$,4) = ".rsl" ; or Right$(File$, 9) = ".rcscript"
;					If Right$(File$, 9) = ".rcscript"
;						Name$ = Left$(File$, Len(File$) - 9)
;					Else
						Name$ = Left$(File$, Len(File$) - 4)
;					EndIf

					;FUI_ComboBoxItem(CZoneTriggerScript, Name$)
					;FUI_ComboBoxItem(CSpawnScript, Name$)
					;FUI_ComboBoxItem(CSpawnActorScript, Name$)
					;FUI_ComboBoxItem(CSpawnDeathScript, Name$)
					;FUI_ComboBoxItem(CEntryScript, Name$)
					;FUI_ComboBoxItem(CExitScript, Name$)
					;FUI_ComboBoxItem(CItemScript, Name$)
					;FUI_ComboBoxItem(CSpellScript, Name$)
					;LoadedScripts = LoadedScripts + 1
					
					If Left$(Name$, 8) = "Trigger_" Then FUI_ComboBoxItem(CZoneTriggerScript, Name$) 
					If Left$(Name$, 6) = "Spawn_" Then FUI_ComboBoxItem(CSpawnScript, Name$) 
					If Left$(Name$, 6) = "Click_" Then FUI_ComboBoxItem(CSpawnActorScript, Name$) 
					If Left$(Name$, 6) = "Death_" Then FUI_ComboBoxItem(CSpawnDeathScript, Name$) 
					If Left$(Name$, 6) = "Entry_" Then FUI_ComboBoxItem(CEntryScript, Name$) 
					If Left$(Name$, 5) = "Exit_" Then FUI_ComboBoxItem(CExitScript, Name$) 
					If Left$(Name$, 5) = "Item_" Then FUI_ComboBoxItem(CItemScript, Name$) 
					If Left$(Name$, 6) = "Spell_" Then FUI_ComboBoxItem(CSpellScript, Name$)
					LoadedScripts = LoadedScripts + 1
					
				EndIf
			EndIf
		EndIf
		File$ = NextFile$(D)
	Wend
CloseDir D
FUI_SendMessage(CZoneTriggerScript, M_SETINDEX, 1)
FUI_SendMessage(CEntryScript, M_SETINDEX, 1)
FUI_SendMessage(CExitScript, M_SETINDEX, 1)
UpdateScriptMethodsList(CZoneTriggerMethod, FUI_SendMessage(CZoneTriggerScript, M_GETCAPTION), "")

; Fill race/class lists
Global TotalRaces, TotalClasses
UpdateRaceClassLists()

; Skysphere
updateSplashScreen("Creating skyspheres")
SkyEN = LoadMesh("Data\Meshes\Sky Sphere.b3d")
MMV.MeshMinMaxVertices = MeshMinMaxVertices(SkyEN)
XScale# = 2.0 / (MMV\MaxX# - MMV\MinX#)
YScale# = 2.0 / (MMV\MaxY# - MMV\MinY#)
ZScale# = 2.0 / (MMV\MaxZ# - MMV\MinZ#)
Delete MMV
If YScale# < XScale# Then XScale# = YScale#
If ZScale# < XScale# Then XScale# = ZScale#
ScaleMesh(SkyEN, XScale#, XScale#, XScale#)
EntityFX SkyEN, 1 + 8
EntityOrder SkyEN, 2
CloudEN = CopyEntity(SkyEN)
EntityFX CloudEN, 1 + 8
EntityOrder CloudEN, 1
StarsEN = CreateMesh()
HideEntity SkyEN
HideEntity CloudEN
HideEntity StarsEN

; Initialise
WriteLog(GUELog, "Performing final initialisation")
FreeImage(Img)

;HidePointer()
FUI_HideMouse()

SetZoneDefaults()
CurrentArea = ServerCreateArea()
UpdateZoneDisplay(1)
WriteLog(GUELog, "** GUE loading complete **")
CloseAllLogs()

; Event loop ------------------------------------------------------------------------------------------------------------------------

DeltaTime = MilliSecs()
Repeat

Cls
	; PO code
	;If POYear <> 2007 Or POMonth$ = "JUL" Or POMonth$ = "AUG" Or POMonth$ = "SEP" Or POMonth$ = "OCT" Or POMonth$ = "NOV" Or POMonth$ = "DEC"
	If POYear < 2007 Or POYear > 2008 Then
		If POYear = 2007 And POMonth <> "DEC" Then PO()
		If POYear = 2008 Then
			If POMonth = "APR" Or POMonth$ = "MAY"  Or POMonth$ = "JUN" Or POMonth$ = "JUL" Or POMonth$ = "AUG" Or POMonth$ = "SEP" Or POMonth$ = "OCT" Or POMonth$ = "NOV" Or POMonth$ = "DEC" Then PO()
		End If
	EndIf

	; Zones update
	If ZoneTabSelected = True
	;Dynamic Lights
	For Sc.Scenery = Each Scenery
		If Left$(Sc\ENLight$, 6) = "light_" And Sc\LightID=0 And EntityDistance# (Sc\EN,ZoneCam)<=40.0
					
			TypeLength% = Len(Sc\ENLight$)
			newTypeName$ = Left(Sc\ENLight$,TypeLength% - 4)
		
			entTypeLoc% = (Instr(newTypeName$, "_", 1) - 1) ;This gets how long the word of the type is

			numLen% = Instr(newTypeName$, "_",1) + 1
			
			;Get Setting 1
			tempLength1% = (Instr(newTypeName$, "_", numLen%))
			realLength% = (Instr(newTypeName$, "_", numLen%)-numLen%)
			setting1$ = Mid$(newTypeName$, entTypeLoc%+2, realLength%)
			setting1Num# = setting1$ ;Convert setting to a number (float)

			;Get Setting 2
			realLength2% = (Instr(newTypeName$, "_", ((entTypeLoc%+2) + realLength%+2))-tempLength1%)
			setting2$ = Mid$(newTypeName$, (tempLength1%+1), (realLength2%-1))
			setting2Num# = setting2$ ;Convert setting to a number (float)

			;Get Setting 3
			realLength3% = Instr(newTypeName$, "_", (tempLength1%+realLength2% + 1))
			tempL% = realLength3% - (tempLength1% + realLength2%+1)
			setting3$ = Mid$(newTypeName$, (tempLength1%+realLength2% + 1), tempL%)
			setting3Num# = setting3$ ;Convert setting to a number (float)

			;Get Setting 4
			realLength4% = (tempLength1%+realLength2% + 1) + tempL%
			rLength% = Len(newTypeName$) - realLength4%
			setting4$ = Right(newTypeName$, rLength%)
			setting4Num# = setting4$ ;Convert setting to a number (float)
			
				Sc\LightID = CreateLight(2) 
            	LightColor(Sc\LightID, setting2Num#, setting3Num#, setting4Num#) 
            	LightRange Sc\LightID,setting1Num#
				PositionEntity Sc\LightID, EntityX(Sc\EN), EntityY(Sc\EN), EntityZ(Sc\EN)
		
		ElseIf Sc\LightID>0 And EntityDistance# (Sc\EN,ZoneCam)>40.0
        	FreeEntity(Sc\LightID)
			Sc\LightID=0	
		EndIf
	Next

		; Update camera position label
		Pos$ = "X: " + Str$(EntityX#(ZoneCam)) + "    Y: " + Str$(EntityY#(ZoneCam)) + "    Z: " + Str$(EntityZ#(ZoneCam))
		FUI_SendMessage(LZonePos, M_SETTEXT, Pos$)

		; Duplicate model shortcut
		If FUI_ShortCut("Ctrl", "D") = True Then DuplicateSelected()

		; Undo shortcut
		If FUI_ShortCut("Ctrl", "Z") = True Then PerformUndo()

		; Delete selected object
		If KeyHit(211) And SelectedEN <> 0 Then ZoneDeleteEntity(SelectedEN)

		; Mode shortcuts
		If FUI_ShortCut("", "F1")
			FUI_SendMessage(BZoneSelect, M_SETSELECTED, True)
			ZoneMode = 1
			FlushMouse()
			FlushKeys()
		ElseIf FUI_ShortCut("", "F2")
			FUI_SendMessage(BZoneMove, M_SETSELECTED, True)
			ZoneMode = 2
			FlushMouse()
			FlushKeys()
		ElseIf FUI_ShortCut("", "F3")
			FUI_SendMessage(BZoneRotate, M_SETSELECTED, True)
			ZoneMode = 3
			FlushMouse()
			FlushKeys()
		ElseIf FUI_ShortCut("", "F4")
			FUI_SendMessage(BZoneScale, M_SETSELECTED, True)
			ZoneMode = 4
			FlushMouse()
			FlushKeys()
		EndIf

		; Move camera with numpad keys
		If KeyDown(72)
			MoveEntity ZoneCam, 0, 0, 2
		ElseIf KeyDown(80)
			MoveEntity ZoneCam, 0, 0, -2
		ElseIf KeyDown(75)
			MoveEntity ZoneCam, -2, 0, 0
		ElseIf KeyDown(77)
			MoveEntity ZoneCam, 2, 0, 0
		ElseIf KeyDown(73)
			MoveEntity ZoneCam, 0, 2, 0
		ElseIf KeyDown(71)
			MoveEntity ZoneCam, 0, -2, 0
		ElseIf KeyDown(81)
			TurnEntity ZoneCam, 0, -1.5, 0
		ElseIf KeyDown(79)
			TurnEntity ZoneCam, 0, 1.5, 0
		EndIf

		; Cycle through objects with the Tab key
		If KeyHit(15)
			FUI_CloseComboBoxes()
			Select ZoneView
				Case 1, 2
					If SelectedEN <> 0 Then Sc.Scenery = Object.Scenery(EntityName$(SelectedEN))
					If Sc = Null
						Sc = First Scenery
					Else
						Sc = After Sc
						If Sc = Null Then Sc = First Scenery
					EndIf
					If Sc <> Null Then ZoneSelectEntity(Sc\EN)
				Case 3, 4
					If SelectedEN <> 0 Then T.Terrain = Object.Terrain(EntityName$(SelectedEN))
					If T = Null
						T = First Terrain
					Else
						T = After T
						If T = Null Then T = First Terrain
					EndIf
					If T <> Null Then ZoneSelectEntity(T\EN)
				Case 5, 6
					If SelectedEN <> 0 Then Emi.Emitter = Object.Emitter(EntityName$(SelectedEN))
					If Emi = Null
						Emi = First Emitter
					Else
						Emi = After Emi
						If Emi = Null Then Emi = First Emitter
					EndIf
					If Emi <> Null Then ZoneSelectEntity(Emi\EN)
				Case 7, 8
					If SelectedEN <> 0 Then W.Water = Object.Water(EntityName$(SelectedEN))
					If W = Null
						W = First Water
					Else
						W = After W
						If W = Null Then W = First Water
					EndIf
					If W <> Null Then ZoneSelectEntity(W\EN)
				Case 9, 10
					If SelectedEN <> 0 Then CB.ColBox = Object.ColBox(EntityName$(SelectedEN))
					If CB = Null
						CB = First ColBox
					Else
						CB = After CB
						If CB = Null Then CB = First ColBox
					EndIf
					If CB <> Null Then ZoneSelectEntity(CB\EN)
				Case 11, 12
					If SelectedEN <> 0 Then SZ.SoundZone = Object.SoundZone(EntityName$(SelectedEN))
					If SZ = Null
						SZ = First SoundZone
					Else
						SZ = After SZ
						If SZ = Null Then SZ = First SoundZone
					EndIf
					If SZ <> Null Then ZoneSelectEntity(SZ\EN)
				Case 13, 14
					If SelectedEN <> 0
						TriggerID = Int(Mid$(EntityName$(SelectedEN), 2)) + 1
						While TriggerEN(TriggerID) = 0
							TriggerID = TriggerID + 1
							If TriggerID > 149 Then TriggerID = 0
						Wend
						ZoneSelectEntity(TriggerEN(TriggerID))
					Else
						For i = 0 To 149
							If TriggerEN(i) <> 0
								ZoneSelectEntity(TriggerEN(TriggerID))
								Exit
							EndIf
						Next
					EndIf
				Case 15, 16
					If SelectedEN <> 0
						WaypointID = Int(Mid$(EntityName$(SelectedEN), 2)) + 1
						While WaypointEN(WaypointID) = 0
							WaypointID = WaypointID + 1
							If WaypointID > 1999 Then WaypointID = 0
						Wend
						ZoneSelectEntity(WaypointEN(WaypointID))
					Else
						For i = 0 To 1999
							If WaypointEN(i) <> 0
								ZoneSelectEntity(WaypointEN(WaypointID))
								Exit
							EndIf
						Next
					EndIf
				Case 17, 18
					If SelectedEN <> 0
						PortalID = Int(Mid$(EntityName$(SelectedEN), 2)) + 1
						While PortalEN(PortalID) = 0
							PortalID = PortalID + 1
							If PortalID > 99 Then PortalID = 0
						Wend
						ZoneSelectEntity(PortalEN(PortalID))
					Else
						For i = 0 To 99
							If PortalEN(i) <> 0
								ZoneSelectEntity(PortalEN(i))
								Exit
							EndIf
						Next
					EndIf
			End Select
		EndIf

		; Mouselook
		If KeyDown(57)
			If MouseDown(1)
				MoveEntity ZoneCam, 0, 0, CamSpeed#
			ElseIf MouseDown(2)
				MoveEntity ZoneCam, 0, 0, -CamSpeed#
			EndIf
		    DesiredX# = DesiredX# + MouseYSpeed() : DesiredY# = DesiredY# - MouseXSpeed()
		    If DesiredX# > 89 Then DesiredX# = 89 ElseIf DesiredX# < -89 Then DesiredX# = -89
		    ActualX# = CurveValue(ActualX#, DesiredX#, 1.5)
		    ActualY# = CurveValue(ActualY#, DesiredY#, 1.5)
		    RotateEntity ZoneCam, ActualX#, ActualY#, 0
			MoveMouse GraphicsWidth() / 2, GraphicsHeight() / 2
			FlushMouse()
		; Not mouselook
		Else
			; Select mode
			If ZoneMode = 1
				If MouseHit(1) And ZoneView < 19 And FUI_OverGadget(VZone)
					UpdateZonePickModes(ZoneView)
					Result = CameraPick(ZoneCam, MouseX() - 45, MouseY() - 100)
					If Result <> 0 Then ZoneSelectEntity(Result)
				EndIf
			; Move mode
			ElseIf ZoneMode = 2
				If SelectedEN <> 0
					Allowed = False
					Sc.Scenery = Object.Scenery(EntityName$(SelectedEN))
					If Sc = Null
						Allowed = True
					ElseIf Sc\Locked = False
						Allowed = True
					EndIf
					If Allowed = True
						If Left$(EntityName$(SelectedEN), 1) = "W" Then RepositionWaypointLinks(Mid$(EntityName$(SelectedEN), 2))

						; Keyboard shortcuts
						If KeyDown(29) = False And KeyDown(157) = False
							If KeyDown(205)
								CreateTransformUndo("K_Move", SelectedEN, EntityX#(SelectedEN), EntityY#(SelectedEN), EntityZ#(SelectedEN))
								TranslateEntity SelectedEN, Delta#, 0, 0 : ZoneSaved = False
							ElseIf KeyDown(203)
								CreateTransformUndo("K_Move", SelectedEN, EntityX#(SelectedEN), EntityY#(SelectedEN), EntityZ#(SelectedEN))
								TranslateEntity SelectedEN, -Delta#, 0, 0 : ZoneSaved = False
							ElseIf KeyDown(200)
								CreateTransformUndo("K_Move", SelectedEN, EntityX#(SelectedEN), EntityY#(SelectedEN), EntityZ#(SelectedEN))
								TranslateEntity SelectedEN, 0, 0, Delta# : ZoneSaved = False
							ElseIf KeyDown(208)
								CreateTransformUndo("K_Move", SelectedEN, EntityX#(SelectedEN), EntityY#(SelectedEN), EntityZ#(SelectedEN))
								TranslateEntity SelectedEN, 0, 0, -Delta# : ZoneSaved = False
							ElseIf KeyDown(30)
								CreateTransformUndo("K_Move", SelectedEN, EntityX#(SelectedEN), EntityY#(SelectedEN), EntityZ#(SelectedEN))
								TranslateEntity SelectedEN, 0, Delta# / 2.0, 0 : ZoneSaved = False
							ElseIf KeyDown(44)
								CreateTransformUndo("K_Move", SelectedEN, EntityX#(SelectedEN), EntityY#(SelectedEN), EntityZ#(SelectedEN))
								TranslateEntity SelectedEN, 0, -Delta# / 2.0, 0 : ZoneSaved = False
							EndIf
						EndIf

						; Mouse movement
						If MouseDown(1) And FUI_OverGadget(VZone)
							UpdateZonePickModes(0) : EntityPickMode SelectedEN, 0
							Result = CameraPick(ZoneCam, MouseX() - 45, MouseY() - 100)
							If Result <> 0
								CreateTransformUndo("M_Move", SelectedEN, EntityX#(SelectedEN), EntityY#(SelectedEN), EntityZ#(SelectedEN))
								PositionEntity SelectedEN, PickedX#(), PickedY#(), PickedZ#()
								If Object.ColBox(EntityName$(SelectedEN)) <> Null
									CB.ColBox = Object.ColBox(EntityName$(SelectedEN))
									TranslateEntity SelectedEN, 0, CB\ScaleY# / 2.0, 0
								ElseIf Left$(EntityName$(SelectedEN), 1) = "P"
									PortalID = Mid$(EntityName$(SelectedEN), 2)
									TranslateEntity SelectedEN, 0, CurrentArea\PortalSize#[PortalID] / 2.0, 0
								ElseIf Object.Terrain(EntityName$(SelectedEN)) <> Null
									T.Terrain = Object.Terrain(EntityName$(SelectedEN))
									Size# = TerrainSize(SelectedEN)
									TranslateEntity SelectedEN, (Size# * T\ScaleX#) / -2.0, 0.0, (Size# * T\ScaleZ#) / -2.0
								EndIf
								ZoneSaved = False
							EndIf
						EndIf

						; Reset
						If KeyHit(28)
							CreateUndo("Move", SelectedEN, 0, EntityX#(SelectedEN), EntityY#(SelectedEN), EntityZ#(SelectedEN))
							TFormPoint 0.0, 0.0, 10.0, ZoneCam, 0
							PositionEntity SelectedEN, TFormedX#(), TFormedY#(), TFormedZ#()
							ZoneSaved = False
						EndIf
					EndIf
				EndIf
			; Rotate mode
			ElseIf ZoneMode = 3
				If SelectedEN <> 0
					Allowed = False
					Sc.Scenery = Object.Scenery(EntityName$(SelectedEN))
					If Sc = Null
						Allowed = True
					ElseIf Sc\Locked = False
						Allowed = True
					EndIf
					If Allowed = True
						If Left$(EntityName$(SelectedEN), 1) <> "W"
							; Keyboard shortcuts
							If KeyDown(29) = False And KeyDown(157) = False
								If KeyDown(205)
									CreateTransformUndo("K_Rotate", SelectedEN, EntityPitch#(SelectedEN), EntityYaw#(SelectedEN), EntityRoll#(SelectedEN))
									TurnEntity SelectedEN, 0, -1, 0, True : ZoneSaved = False
								ElseIf KeyDown(203)
									CreateTransformUndo("K_Rotate", SelectedEN, EntityPitch#(SelectedEN), EntityYaw#(SelectedEN), EntityRoll#(SelectedEN))
									TurnEntity SelectedEN, 0, 1, 0, True : ZoneSaved = False
								ElseIf KeyDown(200) And Left$(EntityName$(SelectedEN), 1) <> "P"
									CreateTransformUndo("K_Rotate", SelectedEN, EntityPitch#(SelectedEN), EntityYaw#(SelectedEN), EntityRoll#(SelectedEN))
									TurnEntity SelectedEN, 1, 0, 0, False : ZoneSaved = False
								ElseIf KeyDown(208) And Left$(EntityName$(SelectedEN), 1) <> "P"
									CreateTransformUndo("K_Rotate", SelectedEN, EntityPitch#(SelectedEN), EntityYaw#(SelectedEN), EntityRoll#(SelectedEN))
									TurnEntity SelectedEN, -1, 0, 0, False : ZoneSaved = False
								ElseIf KeyDown(30) And Left$(EntityName$(SelectedEN), 1) <> "P"
									CreateTransformUndo("K_Rotate", SelectedEN, EntityPitch#(SelectedEN), EntityYaw#(SelectedEN), EntityRoll#(SelectedEN))
									TurnEntity SelectedEN, 0, 0, 1, False : ZoneSaved = False
								ElseIf KeyDown(44) And Left$(EntityName$(SelectedEN), 1) <> "P"
									CreateTransformUndo("K_Rotate", SelectedEN, EntityPitch#(SelectedEN), EntityYaw#(SelectedEN), EntityRoll#(SelectedEN))
									TurnEntity SelectedEN, 0, 0, -1, False : ZoneSaved = False
								EndIf
							EndIf

							; Mouse movement
							If MouseDown(1) And FUI_OverGadget(VZone)
								CreateTransformUndo("M_Rotate", SelectedEN, EntityPitch#(SelectedEN), EntityYaw#(SelectedEN), EntityRoll#(SelectedEN))
								TurnEntity SelectedEN, 0, MouseXSpeed(), 0
								ZoneSaved = False
							EndIf

							; Reset
							If KeyHit(28)
								CreateUndo("Rotate", SelectedEN, 0, EntityPitch#(SelectedEN), EntityYaw#(SelectedEN), EntityRoll#(SelectedEN))
								RotateEntity SelectedEN, 0, 0, 0
								ZoneSaved = False
							EndIf
						EndIf
					EndIf
				EndIf
			; Scale mode
			ElseIf ZoneMode = 4
				If SelectedEN <> 0
					PerformScale = False
					XScaleChange# = 1.0 : YScaleChange# = 1.0 : ZScaleChange# = 1.0

					; Keyboard shortcuts
					If KeyDown(29) = False And KeyDown(157) = False
						If KeyDown(205)
							XScaleChange# = 1.01
							PerformScale = True
						ElseIf KeyDown(203)
							XScaleChange# = 0.99
							PerformScale = True
						ElseIf KeyDown(200)
							ZScaleChange# = 1.01
							PerformScale = True
						ElseIf KeyDown(208)
							ZScaleChange# = 0.99
							PerformScale = True
						ElseIf KeyDown(30)
							YScaleChange# = 1.01
							PerformScale = True
						ElseIf KeyDown(44)
							YScaleChange# = 0.99
							PerformScale = True
						EndIf
					EndIf

					; Mouse movement
					If MouseDown(1) And FUI_OverGadget(VZone)
						XScaleChange# = 1.0 + (Float#(MouseXSpeed()) / 15.0)
						YScaleChange# = XScaleChange#
						ZScaleChange# = XScaleChange#
						PerformScale = True
					EndIf

					; Reset
					ReturnHit = KeyHit(28)

					; Is this a locked scenery object?
					Allowed = False
					Sc.Scenery = Object.Scenery(EntityName$(SelectedEN))
					If Sc = Null
						Allowed = True
					ElseIf Sc\Locked = False
						Allowed = True
					EndIf

					If (PerformScale Or ReturnHit) And Allowed
						; Find object and set new scale
						TriggerID = -1
						PortalID = -1
						WaypointID = -1
						Sc = Object.Scenery(EntityName$(SelectedEN))
						T.Terrain = Object.Terrain(EntityName$(SelectedEN))
						W.Water = Object.Water(EntityName$(SelectedEN))
						CB.ColBox = Object.ColBox(EntityName$(SelectedEN))
						SZ.SoundZone = Object.SoundZone(EntityName$(SelectedEN))
						If Left$(EntityName$(SelectedEN), 1) = "T"
							TriggerID = Mid$(EntityName$(SelectedEN), 2)
						ElseIf Left$(EntityName$(SelectedEN), 1) = "P"
							PortalID = Mid$(EntityName$(SelectedEN), 2)
						ElseIf Left$(EntityName$(SelectedEN), 1) = "W"
							WaypointID = Mid$(EntityName$(SelectedEN), 2)
						EndIf
						If Sc <> Null
							CreateTransformUndo("K_Scale", SelectedEN, Sc\ScaleX#, Sc\ScaleY#, Sc\ScaleZ#)
							Sc\ScaleX# = Sc\ScaleX# * XScaleChange#
							Sc\ScaleY# = Sc\ScaleY# * YScaleChange#
							Sc\ScaleZ# = Sc\ScaleZ# * ZScaleChange#
							If ReturnHit
								CreateUndo("Scale", SelectedEN, 0, Sc\ScaleX#, Sc\ScaleY#, Sc\ScaleZ#)
								Sc\ScaleX# = LoadedMeshScales#(Sc\MeshID)
								Sc\ScaleY# = Sc\ScaleX# : Sc\ScaleZ# = Sc\ScaleX#
								ZoneSaved = False
							EndIf
							ScaleEntity Sc\EN, Sc\ScaleX#, Sc\ScaleY#, Sc\ScaleZ#
						ElseIf T <> Null
							CreateTransformUndo("K_Scale", SelectedEN, T\ScaleX#, T\ScaleY#, T\ScaleZ#)
							OldSX# = T\ScaleX#
							OldSZ# = T\ScaleZ#
							T\ScaleX# = T\ScaleX# * XScaleChange#
							T\ScaleY# = T\ScaleY# * YScaleChange#
							T\ScaleZ# = T\ScaleZ# * ZScaleChange#
							If ReturnHit
								CreateUndo("Scale", SelectedEN, 0, T\ScaleX#, T\ScaleY#, T\ScaleZ#)
								T\ScaleX# = 1.0
								T\ScaleY# = 10.0
								T\ScaleZ# = 1.0
								ZoneSaved = False
							EndIf
							ScaleEntity T\EN, T\ScaleX#, T\ScaleY#, T\ScaleZ#
							TranslateEntity T\EN, (OldSX# - T\ScaleX#) * (Float#(TerrainSize(T\EN)) / 2.0), 0.0, (OldSZ# - T\ScaleZ#) * (Float#(TerrainSize(T\EN)) / 2.0)
						ElseIf W <> Null
							CreateTransformUndo("K_Scale", SelectedEN, W\ScaleX#, 0.0, W\ScaleZ#)
							W\ScaleX# = W\ScaleX# * XScaleChange#
							W\ScaleZ# = W\ScaleZ# * ZScaleChange#
							If ReturnHit
								CreateUndo("Scale", SelectedEN, 0, W\ScaleX#, 0.0, W\ScaleZ#)
								W\ScaleX# = 16.0
								W\ScaleZ# = 16.0
								ZoneSaved = False
							EndIf
							ScaleWater(W)
						ElseIf CB <> Null
							CreateTransformUndo("K_Scale", SelectedEN, CB\ScaleX#, CB\ScaleY#, CB\ScaleZ#)
							CB\ScaleX# = CB\ScaleX# * XScaleChange#
							CB\ScaleY# = CB\ScaleY# * YScaleChange#
							CB\ScaleZ# = CB\ScaleZ# * ZScaleChange#
							If ReturnHit
								CreateUndo("Scale", SelectedEN, 0, CB\ScaleX#, CB\ScaleY#, CB\ScaleZ#)
								CB\ScaleX# = 5.0
								CB\ScaleY# = 2.0
								CB\ScaleZ# = 5.0
								ZoneSaved = False
							EndIf
							ScaleEntity CB\EN, CB\ScaleX#, CB\ScaleY#, CB\ScaleZ#
						ElseIf SZ <> Null
							CreateTransformUndo("K_Scale", SelectedEN, SZ\Radius#, SZ\Radius#, SZ\Radius#)
							If Abs(XScaleChange# - 1.0) > 0.0
								SZ\Radius# = SZ\Radius# * XScaleChange#
							ElseIf Abs(YScaleChange# - 1.0) > 0.0
								SZ\Radius# = SZ\Radius# * YScaleChange#
							ElseIf Abs(ZScaleChange# - 1.0) > 0.0
								SZ\Radius# = SZ\Radius# * ZScaleChange#
							EndIf
							If ReturnHit
								CreateUndo("Scale", SelectedEN, SZ\Radius#, SZ\Radius#, SZ\Radius#)
								SZ\Radius# = 15.0
								ZoneSaved = False
							EndIf
							ScaleEntity SZ\EN, SZ\Radius#, SZ\Radius#, SZ\Radius#
						ElseIf TriggerID > -1
							Size# = CurrentArea\TriggerSize#[TriggerID]
							CreateTransformUndo("K_Scale", SelectedEN, Size#, Size#, Size#)
							If Abs(XScaleChange# - 1.0) > 0.0
								CurrentArea\TriggerSize#[TriggerID] = Size# * XScaleChange#
							ElseIf Abs(YScaleChange# - 1.0) > 0.0
								CurrentArea\TriggerSize#[TriggerID] = Size# * YScaleChange#
							ElseIf Abs(ZScaleChange# - 1.0) > 0.0
								CurrentArea\TriggerSize#[TriggerID] = Size# * ZScaleChange#
							EndIf
							If ReturnHit
								CreateUndo("Scale", SelectedEN, Size#, Size#, Size#)
								CurrentArea\TriggerSize#[TriggerID] = 5.0
								ZoneSaved = False
							EndIf
							ScaleEntity SelectedEN, CurrentArea\TriggerSize#[TriggerID], CurrentArea\TriggerSize#[TriggerID], CurrentArea\TriggerSize#[TriggerID]
						ElseIf WaypointID > -1
							SpawnNum = GetSpawnPoint(WaypointID)
							If SpawnNum > -1
								Size# = CurrentArea\SpawnSize#[SpawnNum]
								CreateTransformUndo("K_Scale", SelectedEN, Size#, Size#, Size#)
								If Abs(XScaleChange# - 1.0) > 0.0
									CurrentArea\SpawnSize#[SpawnNum] = Size# * XScaleChange#
								ElseIf Abs(YScaleChange# - 1.0) > 0.0
									CurrentArea\SpawnSize#[SpawnNum] = Size# * YScaleChange#
								ElseIf Abs(ZScaleChange# - 1.0) > 0.0
									CurrentArea\SpawnSize#[SpawnNum] = Size# * ZScaleChange#
								EndIf
								If ReturnHit
									CreateUndo("Scale", SelectedEN, Size#, Size#, Size#)
									CurrentArea\SpawnSize#[SpawnNum] = 5.0
									ZoneSaved = False
								EndIf
								Size# = CurrentArea\SpawnSize#[SpawnNum]
								ScaleEntity SelectedEN, Size#, Size#, Size#
								EntityRadius SelectedEN, Size#
								If WaypointALink(WaypointID) <> 0
									Dist# = EntityDistance#(WaypointEN(WaypointID), WaypointEN(CurrentArea\NextWaypointA[WaypointID]))
									ScaleEntity WaypointALink(WaypointID), 1, 1, Dist#
								EndIf
								If WaypointBLink(WaypointID) <> 0
									Dist# = EntityDistance#(WaypointEN(WaypointID), WaypointEN(CurrentArea\NextWaypointB[WaypointID]))
									ScaleEntity WaypointBLink(WaypointID), 1, 1, Dist#, True
								EndIf
							EndIf
						ElseIf PortalID > -1
							Size# = CurrentArea\PortalSize#[PortalID]
							CreateTransformUndo("K_Scale", SelectedEN, Size#, Size#, Size#)
							If Abs(XScaleChange# - 1.0) > 0.0
								CurrentArea\PortalSize#[PortalID] = Size# * XScaleChange#
							ElseIf Abs(YScaleChange# - 1.0) > 0.0
								CurrentArea\PortalSize#[PortalID] = Size# * YScaleChange#
							ElseIf Abs(ZScaleChange# - 1.0) > 0.0
								CurrentArea\PortalSize#[PortalID] = Size# * ZScaleChange#
							EndIf
							If ReturnHit
								CreateUndo("Scale", SelectedEN, Size#, Size#, Size#)
								CurrentArea\PortalSize#[PortalID] = 5.0
								ZoneSaved = False
							EndIf
							ScaleEntity SelectedEN, CurrentArea\PortalSize#[PortalID], CurrentArea\PortalSize#[PortalID], CurrentArea\PortalSize#[PortalID]
						EndIf
						If PerformScale Then ZoneSaved = False
					EndIf
				EndIf
			; Waypoint link modes
			ElseIf ZoneMode = 6 Or ZoneMode = 7
				If MouseHit(1) And FUI_OverGadget(VZone)
					UpdateZonePickModes(15)
					Result = CameraPick(ZoneCam, MouseX() - 45, MouseY() - 100)
					If Result <> 0
						FromWP = Mid$(EntityName$(SelectedEN), 2)
						ToWP = Mid$(EntityName$(Result), 2)
						EN = CreateCube()
						ScaleMesh EN, 0.1, 0.1, 0.5
						PositionMesh EN, 0, 0, 0.5
						ScaleEntity EN, 1, 1, EntityDistance#(WaypointEN(FromWP), WaypointEN(ToWP)), True
						PositionEntity EN, EntityX#(WaypointEN(FromWP)), EntityY#(WaypointEN(FromWP)), EntityZ#(WaypointEN(FromWP))
						PointEntity EN, WaypointEN(ToWP)
						If ZoneMode = 6
							CurrentArea\NextWaypointA[FromWP] = ToWP
							EntityColor EN, 0, 100, 255
							WaypointALink(FromWP) = EN
						Else
							CurrentArea\NextWaypointB[FromWP] = ToWP
							EntityColor EN, 255, 100, 0
							WaypointBLink(FromWP) = EN
						EndIf
						CurrentArea\PrevWaypoint[ToWP] = FromWP
						ZoneMode = 1
						FUI_SendMessage(BZoneSelect, M_SETSELECTED, True)
						ZoneSaved = False
					EndIf
				EndIf
			EndIf

			; Right click (unselect/place object)
			If MouseHit(2)
				; Unselect object
				If (ZoneView Mod 2) = 0 And ZoneView < 19 And ZoneView > 0
					CreateUndo("Unselect", SelectedEN)
					UpdateWaypointRange(0)
					HideEntity(SelectionBoxEN)
					EntityParent(SelectionBoxEN, 0)
					SelectedEN = 0
					FUI_CloseComboBoxes()
					FUI_SendMessage(BZoneSelect, M_SETSELECTED, True)
					ZoneMode = 1
					FlushMouse()
					FlushKeys()
					UpdateZoneDisplay(ZoneView - 1)
				; Place object
				ElseIf FUI_OverGadget(VZone)
					ZoneSaved = False
					Select ZoneView
						; Scenery
						Case 1
							If EditorMeshName$(ZoneSceneryMeshID) <> "[NONE]"
								Sc.Scenery = New Scenery
								Sc\TextureID = 65535
								Sc\MeshID = ZoneSceneryMeshID
								Sc\EN = GetMesh(ZoneSceneryMeshID)
								Sc\ScaleX# = LoadedMeshScales#(ZoneSceneryMeshID) * 0.05
								Sc\ScaleY# = Sc\ScaleX# : Sc\ScaleZ# = Sc\ScaleX#
								NameEntity(Sc\EN, Handle(Sc))
								ScaleEntity Sc\EN, Sc\ScaleX#, Sc\ScaleY#, Sc\ScaleZ#
								If KeyDown(29) Or KeyDown(157)
									TFormPoint(0.0, 0.0, 10.0, ZoneCam, 0)
									PositionEntity(Sc\EN, TFormedX#(), TFormedY#(), TFormedZ#())
								Else
									UpdateZonePickModes(0)
									Result = CameraPick(ZoneCam, MouseX() - 45, MouseY() - 100)
									If Result <> 0
										PositionEntity Sc\EN, PickedX#(), PickedY#(), PickedZ#()
										If FUI_SendMessage(BSceneryAlign, M_GETCHECKED) = True
											AlignToVector(Sc\EN, PickedNX#(), PickedNY#(), PickedNZ#(), 2)
										EndIf
									Else
										TFormPoint 0.0, 0.0, 10.0, ZoneCam, 0
										PositionEntity Sc\EN, TFormedX#(), TFormedY#(), TFormedZ#()
									EndIf
								EndIf
								CreateUndo("Create", Sc\EN)
								ZoneSelectEntity(Sc\EN, True)
							EndIf
						; Terrain
						Case 3
							T.Terrain = New Terrain
							T\Morph = True
							T\Detail = 2000
							T\DetailTexID = 65535
							T\DetailTexScale# = 15.0
							T\ScaleX# = 1.0 : T\ScaleY# = 10.0 : T\ScaleZ# = T\ScaleX#
							If TerrainHMFilename$ = ""
								T\EN = CreateTerrain(256)
							Else
								; Load heightmap
								Img = LoadImage(TerrainHMFilename$)
								; Make sure we are using the smallest image dimension and that it's a power of two
								Smallest = ImageWidth(Img)
								If ImageHeight(Img) < Smallest Then Smallest = ImageHeight(Img)
								Smallest = PowerOfTwo(Smallest)
								; Create terrain
								T\EN = CreateTerrain(Smallest)
								For x = 0 To Smallest
									For z = 0 To Smallest
										Colour = ReadPixel(x, Smallest - z, ImageBuffer(Img)) And $000000FF
										ModifyTerrain(T\EN, x, z, Float#(Colour) / 255.0)
									Next
								Next
								; Unload heightmap
								FreeImage(Img)
							EndIf
							NameEntity T\EN, Handle(T)
							TerrainDetail T\EN, 5000, True
							ScaleEntity T\EN, T\ScaleX#, T\ScaleY#, T\ScaleZ#
							PositionEntity T\EN, EntityX#(ZoneCam), EntityY#(ZoneCam) - 10.0, EntityZ#(ZoneCam)
							CreateUndo("Create", T\EN)
							ZoneSelectEntity(T\EN, True)
						; Emitter
						Case 5
							If FUI_SendMessage(LEmitter, M_GETSELECTED) > 0
								Emi.Emitter = New Emitter
								Emi\EN = CreateCone()
								ScaleMesh Emi\EN, 3, 3, 3
								EntityAlpha Emi\EN, 0.5
								ConfigID = FUI_SendMessage(FUI_SendMessage(LEmitter, M_GETINDEX), M_GETDATA)
								Emi\Config = RP_CopyEmitterConfig(ConfigID)
								C.RP_EmitterConfig = Object.RP_EmitterConfig(Emi\Config)
								C2.RP_EmitterConfig = Object.RP_EmitterConfig(ConfigID)
								Emi\TexID = C\DefaultTextureID
								C\Name$ = C2\Name$
								If Emi\TexID < 65535
									If GetTexture(Emi\TexID, True) <> 0 Then 
										C\Texture = GetTexture(Emi\TexID, True)
										UnloadTexture(Emi\TexID)
									EndIf
								Else
									If DefaultTex <> 0 Then C\Texture = DefaultTex
								EndIf
								Emi\ConfigName$ = C\Name$
								EmitterEN = RP_CreateEmitter(Emi\Config)
								EntityParent EmitterEN, Emi\EN, False
								NameEntity Emi\EN, Handle(Emi)
								If KeyDown(29) Or KeyDown(157)
									TFormPoint 0.0, 0.0, 10.0, ZoneCam, 0
									PositionEntity Emi\EN, TFormedX#(), TFormedY#(), TFormedZ#()
								Else
									UpdateZonePickModes(0)
									Result = CameraPick(ZoneCam, MouseX() - 45, MouseY() - 100)
									If Result <> 0
										PositionEntity Emi\EN, PickedX#(), PickedY#(), PickedZ#()
									Else
										TFormPoint 0.0, 0.0, 10.0, ZoneCam, 0
										PositionEntity Emi\EN, TFormedX#(), TFormedY#(), TFormedZ#()
									EndIf
								EndIf
								CreateUndo("Create", Emi\EN)
								ZoneSelectEntity(Emi\EN, True)
							EndIf
						; Water
						Case 7
							; Find a texture
							NewID = -1
							W.Water = First Water
							If W <> Null
								NewID = W\TexID
								If GetTexture(NewID) = 0 Then NewID = -1
							EndIf
							If NewID = -1
								For i = 0 To 65534
									If EditorTexName$(i) <> ""
										If FileType("Data\Textures\" + EditorTexName$(i)) = 1
											NewID = i
											Exit
										EndIf
									EndIf
								Next
							EndIf
							If NewID = -1
								FUI_CustomMessageBox("Could not find a texture, please add one in the Media tab.", "Error", MB_OK)
							ElseIf GetTexture(NewID) = 0
								FUI_CustomMessageBox("Could not find a texture, please add one in the Media tab.", "Error", MB_OK)
							Else
								W.Water = New Water
								SW.ServerWater = New ServerWater
								SW\Area = CurrentArea
								W\ServerWater = Handle(SW)
								W\TexHandle = GetTexture(NewID, True)
								W\TexID = NewID
								W\TexScale# = 1.0
								W\ScaleX# = 16.0 : W\ScaleZ# = W\ScaleX#
								W\EN = CreateSubdividedPlane(Ceil(W\ScaleX# / 8.0), Ceil(W\ScaleZ# / 8.0), W\ScaleX#, W\ScaleZ#)
								ScaleEntity W\EN, W\ScaleX#, 1.0, W\ScaleZ#
								EntityTexture W\EN, W\TexHandle
								W\Blue = 150
								W\Opacity = 50
								Alpha# = Float#(W\Opacity) / 100.0
								If Alpha# > 1.0 Then Alpha# = 1.0
								EntityAlpha(W\EN, Alpha#)
								EntityBox W\EN, W\ScaleX# / -2.0, -1.0, W\ScaleZ# / -2.0, W\ScaleX#, 2.0, W\ScaleZ#
								NameEntity W\EN, Handle(W)
								If KeyDown(29) Or KeyDown(157)
									TFormPoint 0.0, 0.0, 10.0, ZoneCam, 0
									PositionEntity W\EN, TFormedX#(), TFormedY#(), TFormedZ#()
								Else
									UpdateZonePickModes(0)
									Result = CameraPick(ZoneCam, MouseX() - 45, MouseY() - 100)
									If Result <> 0
										PositionEntity W\EN, PickedX#(), PickedY#() + 1.0, PickedZ#()
									Else
										TFormPoint 0.0, 0.0, 10.0, ZoneCam, 0
										PositionEntity W\EN, TFormedX#(), TFormedY#(), TFormedZ#()
									EndIf
								EndIf
								CreateUndo("Create", W\EN)
								ZoneSelectEntity(W\EN, True)
							EndIf
						; Collision box
						Case 9
							CB.ColBox = New ColBox
							CB\ScaleX# = 5.0 : CB\ScaleY# = 2.0 : CB\ScaleZ# = 5.0
							CB\EN = CreateCube()
							EntityAlpha CB\EN, 0.4
							EntityColor CB\EN, 255, 0, 0
							ScaleEntity CB\EN, CB\ScaleX#, CB\ScaleY#, CB\ScaleZ#
							EntityBox CB\EN, -CB\ScaleX#, -CB\ScaleY#, -CB\ScaleZ#, CB\ScaleX# * 2.0, CB\ScaleY# * 2.0, CB\ScaleZ# * 2.0
							NameEntity CB\EN, Handle(CB)
							If KeyDown(29) Or KeyDown(157)
								TFormPoint 0.0, 0.0, 10.0, ZoneCam, 0
								PositionEntity CB\EN, TFormedX#(), TFormedY#(), TFormedZ#()
							Else
								UpdateZonePickModes(0)
								Result = CameraPick(ZoneCam, MouseX() - 45, MouseY() - 100)
								If Result <> 0
									PositionEntity CB\EN, PickedX#(), PickedY#() + (CB\ScaleY# / 2.0), PickedZ#()
								Else
									TFormPoint 0.0, 0.0, 10.0, ZoneCam, 0
									PositionEntity CB\EN, TFormedX#(), TFormedY#(), TFormedZ#()
								EndIf
							EndIf
							CreateUndo("Create", CB\EN)
							ZoneSelectEntity(CB\EN, True)
						; Sound Zone
						Case 11
							If ZoneSoundID > -1 Or ZoneMusicID > -1
								SZ.SoundZone = New SoundZone
								SZ\SoundID = 65535
								SZ\MusicID = 65535
								SZ\Radius# = 15.0
								SZ\Volume = 100
								SZ\EN = CreateSphere()
								EntityAlpha SZ\EN, 0.5
								EntityColor SZ\EN, 255, 255, 0
								ScaleEntity SZ\EN, SZ\Radius#, SZ\Radius#, SZ\Radius#
								NameEntity SZ\EN, Handle(SZ)
								If KeyDown(29) Or KeyDown(157)
									TFormPoint 0.0, 0.0, 10.0, ZoneCam, 0
									PositionEntity SZ\EN, TFormedX#(), TFormedY#(), TFormedZ#()
								Else
									UpdateZonePickModes(0)
									Result = CameraPick(ZoneCam, MouseX() - 45, MouseY() - 100)
									If Result <> 0
										PositionEntity SZ\EN, PickedX#(), PickedY#(), PickedZ#()
									Else
										TFormPoint 0.0, 0.0, 10.0, ZoneCam, 0
										PositionEntity SZ\EN, TFormedX#(), TFormedY#(), TFormedZ#()
									EndIf
								EndIf
								If ZoneSoundID > -1
									SZ\SoundID = ZoneSoundID
									SZ\LoadedSound = GetSound(SZ\SoundID)
								Else
									SZ\MusicID = ZoneMusicID
								EndIf
								CreateUndo("Create", SZ\EN)
								ZoneSelectEntity(SZ\EN, True)
							EndIf
						; Trigger
						Case 13
							For i = 0 To 149
								; Found a free one
								Found = False
								If CurrentArea\TriggerScript$[i] = ""
									CurrentArea\TriggerScript$[i] = FUI_SendMessage(CZoneTriggerScript, M_GETCAPTION)
									CurrentArea\TriggerMethod$[i] = FUI_SendMessage(CZoneTriggerMethod, M_GETCAPTION)
									CurrentArea\TriggerSize#[i] = 5.0
									TriggerEN(i) = CreateSphere()
									EntityColor TriggerEN(i), 0, 0, 255
									EntityAlpha TriggerEN(i), 0.5
									ScaleEntity TriggerEN(i), 5.0, 5.0, 5.0
									NameEntity TriggerEN(i), "T" + Str$(i)
									If KeyDown(29) Or KeyDown(157)
										TFormPoint 0.0, 0.0, 10.0, ZoneCam, 0
										PositionEntity TriggerEN(i), TFormedX#(), TFormedY#(), TFormedZ#()
									Else
										UpdateZonePickModes(0)
										Result = CameraPick(ZoneCam, MouseX() - 45, MouseY() - 100)
										If Result <> 0
											PositionEntity TriggerEN(i), PickedX#(), PickedY#(), PickedZ#()
										Else
											TFormPoint 0.0, 0.0, 10.0, ZoneCam, 0
											PositionEntity TriggerEN(i), TFormedX#(), TFormedY#(), TFormedZ#()
										EndIf
									EndIf
									CreateUndo("Create", TriggerEN(i))
									ZoneSelectEntity(TriggerEN(i), True)
									Found = True
									Exit
								EndIf
							Next
							; No spare trigger - tell user
							If Found = False Then FUI_CustomMessageBox("Maximum triggers already placed!", "Error", MB_OK)
						; Waypoint
						Case 15
							Found = False
							For i = 0 To 1999
								; Found a free one
								If CurrentArea\PrevWaypoint[i] = 2005
									CurrentArea\PrevWaypoint[i] = 2000
									CurrentArea\NextWaypointA[i] = 2000
									CurrentArea\NextWaypointB[i] = 2000
									WaypointEN(i) = CopyEntity(ZoneWaypointEN)
									EntityRadius WaypointEN(i), 3.0
									ScaleEntity WaypointEN(i), 3.0, 3.0, 3.0
									NameEntity WaypointEN(i), "W" + Str$(i)
									If KeyDown(29) Or KeyDown(157)
										TFormPoint 0.0, 0.0, 10.0, ZoneCam, 0
										PositionEntity WaypointEN(i), TFormedX#(), TFormedY#(), TFormedZ#()
									Else
										UpdateZonePickModes(0)
										Result = CameraPick(ZoneCam, MouseX() - 45, MouseY() - 100)
										If Result <> 0
										;Places waypoints above the ground to stop actors falling cysis145
											PositionEntity WaypointEN(i), PickedX#(), PickedY#() + 3, PickedZ#()
											AlignToVector(WaypointEN(i), PickedNX#(), PickedNY#(), PickedNZ#(), 2)
										Else
											TFormPoint 0.0, 0.0, 10.0, ZoneCam, 0
											PositionEntity WaypointEN(i), TFormedX#(), TFormedY#(), TFormedZ#()
										EndIf
									EndIf
									CreateUndo("Create", WaypointEN(i))
									ZoneSelectEntity(WaypointEN(i), True)
									Found = True
									Exit
								EndIf
							Next
							; No spare waypoint - tell user
							If Found = False Then FUI_CustomMessageBox("Maximum waypoints already placed!", "Error", MB_OK)
						; Portal
						Case 17
							If FUI_SendMessage(TPortalName, M_GETCAPTION) <> ""
								For i = 0 To 99
									; Found a free one
									Found = False
									If CurrentArea\PortalName$[i] = ""
										CurrentArea\PortalName$[i] = FUI_SendMessage(TPortalName, M_GETCAPTION)
										If FUI_SendMessage(CPortalLinkArea, M_GETINDEX) > 1
											CurrentArea\PortalLinkArea$[i] = FUI_SendMessage(CPortalLinkArea, M_GETCAPTION)
											CurrentArea\PortalLinkName$[i] = FUI_SendMessage(CPortalLinkName, M_GETCAPTION)
										Else
											CurrentArea\PortalLinkArea$[i] = ""
											CurrentArea\PortalLinkName$[i] = ""
										EndIf
										CurrentArea\PortalSize#[i] = 5.0
										PortalEN(i) = CopyEntity(ZonePortalEN)
										EntityAlpha PortalEN(i), 0.5
										ScaleEntity PortalEN(i), 5.0, 5.0, 5.0
										NameEntity PortalEN(i), "P" + Str$(i)
										If KeyDown(29) Or KeyDown(157)
											TFormPoint 0.0, 0.0, 10.0, ZoneCam, 0
											PositionEntity PortalEN(i), TFormedX#(), TFormedY#(), TFormedZ#()
										Else
											UpdateZonePickModes(0)
											Result = CameraPick(ZoneCam, MouseX() - 45, MouseY() - 100)
											If Result <> 0
												PositionEntity PortalEN(i), PickedX#(), PickedY#() + 2.5, PickedZ#()
											Else
												TFormPoint 0.0, 0.0, 10.0, ZoneCam, 0
												PositionEntity PortalEN(i), TFormedX#(), TFormedY#(), TFormedZ#()
											EndIf
										EndIf
										CreateUndo("Create", PortalEN(i))
										ZoneSelectEntity(PortalEN(i), True)
										Found = True
										Exit
									EndIf
								Next
								; No spare portals - tell user
								If Found = False Then FUI_CustomMessageBox("Maximum portals already placed!", "Error", MB_OK)
							; Portal needs a name
							Else
								FUI_CustomMessageBox("Please enter a name for the portal", "Error", MB_OK)
							EndIf
					End Select
				EndIf
			EndIf
		EndIf

		; Update skysphere position
		PositionEntity SkyEN, EntityX#(ZoneCam), EntityY#(ZoneCam), EntityZ#(ZoneCam)
		PositionEntity CloudEN, EntityX#(ZoneCam), EntityY#(ZoneCam), EntityZ#(ZoneCam)
		TurnEntity CloudEN, 0.0, 0.05 * Delta#, 0.0
	EndIf

	; Update screen
	RP_Update(Delta#)
	FUI_Update()
	
	;Update after Event checks cysis145
	;Flip(0)

	Local E.Event
	; Process events
	For E.Event = Each Event
		Select E\EventID

			;- Tab switched ----------------------------------------------------------------------------------------------------------
			Case TabMain

				; Close all tab specific stuff
				HideEntity MediaPreviewQuad
				If MediaPreviewMesh <> 0 Then HideEntity MediaPreviewMesh
				If ParticlesEmitter <> 0 Then RP_HideEmitter(ParticlesEmitter)
				If ActorPreview <> Null
					HideActorEntity(ActorPreview\EN)
				EndIf
				HideEntity(SkyEN)
				HideEntity(CloudEN)
				HideEntity(SelectionBoxEN)
				For Sc.Scenery = Each Scenery : HideEntity Sc\EN : Next
				For T.Terrain = Each Terrain : HideEntity T\EN : Next
				For W.Water = Each Water : HideEntity W\EN : Next
				For CB.ColBox = Each ColBox : HideEntity CB\EN : Next
				For SZ.SoundZone = Each SoundZone : HideEntity SZ\EN : Next
				For Emi.Emitter = Each Emitter : HideEntity(Emi\EN) : RP_HideEmitter(GetChild(Emi\EN, 1)) : Next
				For i = 0 To 149
					If TriggerEN(i) <> 0 Then HideEntity(TriggerEN(i))
				Next
				For i = 0 To 99
					If PortalEN(i) <> 0 Then HideEntity(PortalEN(i))
				Next
				For i = 0 To 1999
					If WaypointEN(i) <> 0 Then HideEntity(WaypointEN(i))
					If WaypointALink(i) <> 0 Then HideEntity(WaypointALink(i))
					If WaypointBLink(i) <> 0 Then HideEntity(WaypointBLink(i))
				Next
				RotateEntity(DefaultLight, 30, 0, 0)
				AmbientLight(100, 100, 100)
				ZoneTabSelected = False
				For IC.InterfaceComponent = Each InterfaceComponent : HideEntity IC\Component : Next

				; Update anything relying on the tab we just left
				Select PreviousTab
					; Particles
					Case 3
						If ParticlesChanged = True
							FUI_SendMessage(CProjEmitter1, M_RESET)
							FUI_SendMessage(CProjEmitter2, M_RESET)
							FUI_SendMessage(LEmitter, M_RESET)
							For C.RP_EmitterConfig = Each RP_EmitterConfig
								Item = FUI_ComboBoxItem(CProjEmitter1, C\Name$) : FUI_SendMessage(Item, M_SETDATA, Handle(C))
								Item = FUI_ComboBoxItem(CProjEmitter2, C\Name$) : FUI_SendMessage(Item, M_SETDATA, Handle(C))
								Item = FUI_ListBoxItem(LEmitter, C\Name$) : FUI_SendMessage(Item, M_SETDATA, Handle(C))
							Next
							UpdateProjectileDisplay()
							ParticlesChanged = False
						EndIf
					; Projectiles
					Case 5
						If ProjectilesChanged = True
							FUI_SendMessage(CItemRangedProjectile, M_RESET)
							For P.Projectile = Each Projectile
								Item = FUI_ComboBoxItem(CItemRangedProjectile, P\Name$)
								FUI_SendMessage(Item, M_SETDATA, P\ID)
							Next
							ProjectilesChanged = False
						EndIf
					; Factions
					Case 6
						If FactionsChanged = True
							FUI_SendMessage(CActorFaction, M_RESET)
							For i = 0 To 99
								If FactionNames$(i) <> ""
									Item = FUI_ComboBoxItem(CActorFaction, FactionNames$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
								EndIf
							Next
							If SelectedActor <> Null
								Idx = 0
								For i = 0 To SelectedActor\DefaultFaction
									If FactionNames$(i) <> "" Then Idx = Idx + 1
								Next
								FUI_SendMessage(CActorFaction, M_SETINDEX, Idx)
							Else
								FUI_SendMessage(CActorFaction, M_SETINDEX, 1)
							EndIf
							FactionsChanged = False
						EndIf
					; Animation sets
					Case 7
						If AnimsChanged = True
							FUI_SendMessage(CActorMAnim, M_RESET)
							FUI_SendMessage(CActorFAnim, M_RESET)
							For AS.AnimSet = Each AnimSet
								Item = FUI_ComboBoxItem(CActorMAnim, AS\Name$) : FUI_SendMessage(Item, M_SETDATA, AS\ID)
								Item = FUI_ComboBoxItem(CActorFAnim, AS\Name$) : FUI_SendMessage(Item, M_SETDATA, AS\ID)
							Next
							AnimsChanged = False
						EndIf
					; Attributes
					Case 8
						If AttributesChanged = True
							FUI_SendMessage(LItemAttributes, M_RESET)
							FUI_SendMessage(LActorAttributes, M_RESET)
							For i = 0 To 39
								If AttributeNames$(i) <> ""
									Item = FUI_ListBoxItem(LItemAttributes, AttributeNames$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
									Item = FUI_ListBoxItem(LActorAttributes, AttributeNames$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
								EndIf
							Next
							FUI_SendMessage(LItemAttributes, M_SETINDEX, 1)
							FUI_SendMessage(LActorAttributes, M_SETINDEX, 1)
							UpdateInterfaceComponentsList()
							AttributesChanged = False
						EndIf
					; Actors
					Case 9
						If ActorsChanged = True Then UpdateRaceClassLists()
				End Select

				; Open new tab specific stuff
				Select E\EventData
					; Media
					Case 2
						SetMediaType(FUI_SendMessage(CMediaType, M_GETINDEX))
					; Particles
					Case 3
						For C.RP_EmitterConfig = Each RP_EmitterConfig
							C\FaceEntity = ParticlesCam
						Next
						If ParticlesEmitter <> 0 Then RP_ShowEmitter(ParticlesEmitter)
					; Actors
					Case 9
						If ActorPreview <> Null
							If GubbinNamesChanged Then UpdateActorDisplay() : GubbinNamesChanged = False
							ShowActorEntity(ActorPreview\EN)
						EndIf
					; Zones
					Case 12
					
						;Update particles instantly edit cysis145 [not finished]
						;UpdateZoneParticles()
						
						For C.RP_EmitterConfig = Each RP_EmitterConfig
							C\FaceEntity = ZoneCam
						Next
						
						
						ShowEntity SkyEN
						ShowEntity CloudEN
						If SelectedEN <> 0 Then ShowEntity(SelectionBoxEN)
						For Sc.Scenery = Each Scenery : ShowEntity Sc\EN : Next
						For T.Terrain = Each Terrain : ShowEntity T\EN : Next
						For W.Water = Each Water : ShowEntity W\EN : Next
						For CB.ColBox = Each ColBox : ShowEntity CB\EN : Next
						For SZ.SoundZone = Each SoundZone : ShowEntity SZ\EN : Next
						
						For Emi.Emitter = Each Emitter : ShowEntity(Emi\EN) : RP_ShowEmitter(GetChild(Emi\EN, 1)) : Next
						
						For i = 0 To 149
							If TriggerEN(i) <> 0 Then ShowEntity(TriggerEN(i))
						Next
						For i = 0 To 99
							If PortalEN(i) <> 0 Then ShowEntity(PortalEN(i))
						Next
						For i = 0 To 1999
							If WaypointEN(i) <> 0 Then ShowEntity(WaypointEN(i))
							If WaypointALink(i) <> 0 Then ShowEntity(WaypointALink(i))
							If WaypointBLink(i) <> 0 Then ShowEntity(WaypointBLink(i))
						Next
						AmbientLight(AmbientR, AmbientG, AmbientB)
						RotateEntity(DefaultLight, DefaultLightPitch#, DefaultLightYaw#, 0)
						ZoneTabSelected = True
						; Hide grass to increase rendering speed
						Tree_HideAll(1)
					; Interface
					Case 14 ;hiding all the unselected stuff for the list box in the tab
						For IC.InterfaceComponent = Each InterfaceComponent : ShowEntity IC\Component : Next
						
						; Main UI
						If FUI_SendMessage(RInterfaceMain, M_GETCHECKED) ;[255]
							;Hide Inventory
							HideEntity InventoryWindow\Component
							HideEntity InventoryDrop\Component
							HideEntity InventoryEat\Component
							HideEntity InventoryGold\Component
							For i = 0 To Slots_Inventory
								HideEntity(InventoryButtons(i)\Component)
							Next
							
							;Hide Login Screen
							;HideEntity LoginWindow\Component
							;HideEntity LoginUsername\Component
							;HideEntity LoginPassword\Component
							;HideEntity LoginNewAccount\Component
							;HideEntity LoginLogIn\Component
							;HideEntity LoginGraphic\Component
							;HideEntity LoginControl\Component
							;HideEntity LoginSound\Component
							;HideEntity LoginServerStat\Component
						
						;Inventory
						Else
						
							;Hide Main UI
							HideEntity Chat\Component
							HideEntity ChatEntry\Component
							HideEntity Radar\Component
							HideEntity BuffsArea\Component
							HideEntity Compass\Component
							For i = 0 To 39
								HideEntity(AttributeDisplays(i)\Component)
							Next
							
					;		;Hide Login Screen
					;		HideEntity LoginWindow\Component
					;		HideEntity LoginUsername\Component
					;		HideEntity LoginPassword\Component
					;		HideEntity LoginNewAccount\Component
					;		HideEntity LoginLogIn\Component
					;		HideEntity LoginGraphic\Component
					;		HideEntity LoginControl\Component
					;		HideEntity LoginSound\Component
					;		HideEntity LoginServerStat\Component
						
						;Login Screen
					;	ElseIf FUI_SendMessage(RInterfaceLogin, M_GETCHECKED)
					;	
					;		;Hide Main UI
					;		HideEntity Chat\Component
					;		HideEntity ChatEntry\Component
					;		HideEntity Radar\Component
					;;		HideEntity BuffsArea\Component
					;		HideEntity Compass\Component
					;		For i = 0 To 39
					;			HideEntity(AttributeDisplays(i)\Component)
					;		Next
					;		
					;		;Hide Login Screen
					;		HideEntity LoginWindow\Component
					;		HideEntity LoginUsername\Component
					;		HideEntity LoginPassword\Component
					;		HideEntity LoginNewAccount\Component
					;		HideEntity LoginLogIn\Component
					;		HideEntity LoginGraphic\Component
					;		HideEntity LoginControl\Component
					;		HideEntity LoginSound\Component
					;		HideEntity LoginServerStat\Component													
					;	
						EndIf
				End Select

				PreviousTab = E\EventData

			; Project tab events ----------------------------------------------------------------------------------------------------
		;Runs Save All function cysis145
		Case helpForum
		ExecFile("https://realmcrafter.boards.net/")
		
		Case mnuFileSaveAll

				menuSaveAll()
				
			Case BRestoreLanguageFile
				If Not RestoreLanguage("Data\Game Data\Language Restore.txt") Then RuntimeError "Language Restore could not be written"
				
				;Allows Exit via File Menu cysis145
			Case mnuFileExit
				app\Quit = True

			; Other tab events ------------------------------------------------------------------------------------------------------

			; Hosts
			Case TServerHost
				F = WriteFile("Data\Game Data\Hosts.dat")
				If F = 0 Then RuntimeError("Could not open Data\Game Data\Hosts.dat!")
					WriteLine F, FUI_SendMessage(TServerHost, M_GETCAPTION)
					WriteLine F, FUI_SendMessage(TUpdatesHost, M_GETCAPTION)
					WriteLine F, FUI_SendMessage(BNewAccounts, M_GETCHECKED)
				CloseFile(F)
			Case TUpdatesHost
				F = WriteFile("Data\Game Data\Hosts.dat")
				If F = 0 Then RuntimeError("Could not open Data\Game Data\Hosts.dat!")
					WriteLine F, FUI_SendMessage(TServerHost, M_GETCAPTION)
					WriteLine F, FUI_SendMessage(TUpdatesHost, M_GETCAPTION)
					WriteLine F, FUI_SendMessage(BNewAccounts, M_GETCHECKED)
				CloseFile(F)
			Case TServerPort
				ServerPort = E\EventData
				F = OpenFile("Data\Server Data\Misc.dat")
				If F = 0 Then RuntimeError("Could not open Data\Server Data\Misc.dat!")
					SeekFile(F, 17)
					WriteInt(F, ServerPort)
				CloseFile(F)
				F = OpenFile("Data\Game Data\Other.dat")
				If F = 0 Then RuntimeError("Could not open Data\Game Data\Other.dat!")
					SeekFile(F, 3)
					WriteInt(F, ServerPort)
				CloseFile(F)

			; Allow new account creation from client
			Case BNewAccounts
				F = WriteFile("Data\Game Data\Hosts.dat")
					WriteLine F, FUI_SendMessage(TServerHost, M_GETCAPTION)
					WriteLine F, FUI_SendMessage(TUpdatesHost, M_GETCAPTION)
					WriteLine F, FUI_SendMessage(BNewAccounts, M_GETCHECKED)
				CloseFile(F)
				F = OpenFile("Data\Server Data\Misc.dat")
				If F = 0 Then RuntimeError("Could not open Data\Server Data\Misc.dat!")
					SeekFile F, 15
					WriteByte F, FUI_SendMessage(BNewAccounts, M_GETCHECKED)
				CloseFile(F)

			; Maximum number of characters per account
			Case SMaxAccountChars
				F = OpenFile("Data\Server Data\Misc.dat")
				If F = 0 Then RuntimeError("Could not open Data\Server Data\Misc.dat!")
					SeekFile F, 16
					WriteByte F, E\EventData
				CloseFile(F)

			; Game options
			Case SStartGold
				F = OpenFile("Data\Server Data\Misc.dat")
				If F = 0 Then RuntimeError("Could not open Data\Server Data\Misc.dat!")
					WriteInt F, E\EventData
				CloseFile(F)
			Case SStartReputation
				F = OpenFile("Data\Server Data\Misc.dat")
				If F = 0 Then RuntimeError("Could not open Data\Server Data\Misc.dat!")
					SeekFile F, 4
					WriteInt F, E\EventData
				CloseFile(F)
			Case BForcePortals
				F = OpenFile("Data\Server Data\Misc.dat")
				If F = 0 Then RuntimeError("Could not open Data\Server Data\Misc.dat!")
					SeekFile F, 8
					WriteByte F, E\EventData
				CloseFile(F)
			Case SCombatDelay
				F = OpenFile("Data\Server Data\Misc.dat")
				If F = 0 Then RuntimeError("Could not open Data\Server Data\Misc.dat!")
					SeekFile F, 9
					WriteShort F, E\EventData
				CloseFile(F)
				F = OpenFile("Data\Game Data\Combat.dat")
				If F = 0 Then RuntimeError("Could not open Data\Game Data\Combat.dat!")
					WriteShort F, E\EventData
				CloseFile(F)
			Case CCombatInfoStyle
				F = OpenFile("Data\Game Data\Combat.dat")
				If F = 0 Then RuntimeError("Could not open Data\Game Data\Combat.dat!")
					SeekFile F, 2
					WriteShort F, E\EventData
				CloseFile(F)
			Case CCombatFormula
				F = OpenFile("Data\Server Data\Misc.dat")
				If F = 0 Then RuntimeError("Could not open Data\Server Data\Misc.dat!")
					SeekFile F, 11
					WriteByte F, E\EventData
				CloseFile(F)
			Case BDamageWeapon
				F = OpenFile("Data\Server Data\Misc.dat")
				If F = 0 Then RuntimeError("Could not open Data\Server Data\Misc.dat!")
					SeekFile F, 12
					WriteByte F, E\EventData
				CloseFile(F)
			Case BDamageArmour
				F = OpenFile("Data\Server Data\Misc.dat")
				If F = 0 Then RuntimeError("Could not open Data\Server Data\Misc.dat!")
					SeekFile F, 13
					WriteByte F, E\EventData
				CloseFile(F)
			Case SCombatRatingAdjust
				F = OpenFile("Data\Server Data\Misc.dat")
				If F = 0 Then RuntimeError("Could not open Data\Server Data\Misc.dat!")
					SeekFile F, 14
					WriteByte F, E\EventData
				CloseFile(F)
			Case CHideNametags
				F = OpenFile("Data\Game Data\Other.dat")
				If F = 0 Then RuntimeError("Could not open Data\Game Data\Other.dat!")
					WriteByte F, Int(E\EventData) - 1
				CloseFile(F)
			Case BDisableCollisions
				F = OpenFile("Data\Game Data\Other.dat")
				If F = 0 Then RuntimeError("Could not open Data\Game Data\Other.dat!")
					SeekFile F, 1
					WriteByte F, E\EventData
				CloseFile(F)
			Case CViewMode
				If Int(E\EventData) = 1
					ViewMode = 1
				ElseIf Int(E\EventData) = 2
					ViewMode = 3
				ElseIf Int(E\EventData) = 3
					ViewMode = 2
				EndIf
				F = OpenFile("Data\Game Data\Other.dat")
				If F = 0 Then RuntimeError("Could not open Data\Game Data\Other.dat!")
					SeekFile F, 2
					WriteByte F, ViewMode
				CloseFile(F)
			Case BRequireMemorise
				F = OpenFile("Data\Game Data\Other.dat")
				If F = 0 Then RuntimeError("Could not open Data\Game Data\Other.dat!")
					SeekFile F, 7
					WriteByte F, E\EventData
				CloseFile(F)
				F = OpenFile("Data\Server Data\Misc.dat")
				If F = 0 Then RuntimeError("Could not open Data\Server Data\Misc.dat!")
					SeekFile F, 21
					WriteByte F, E\EventData
				CloseFile(F)
			Case CUseBubbles
				F = OpenFile("Data\Game Data\Other.dat")
				If F = 0 Then RuntimeError("Could not open Data\Game Data\Other.dat!")
					SeekFile F, 8
					WriteByte F, E\EventData
				CloseFile(F)
			Case SBubblesR
				F = OpenFile("Data\Game Data\Other.dat")
				If F = 0 Then RuntimeError("Could not open Data\Game Data\Other.dat!")
					SeekFile F, 9
					WriteByte F, E\EventData
				CloseFile(F)
			Case SBubblesG
				F = OpenFile("Data\Game Data\Other.dat")
				If F = 0 Then RuntimeError("Could not open Data\Game Data\Other.dat!")
					SeekFile F, 10
					WriteByte F, E\EventData
				CloseFile(F)
			Case SBubblesB
				F = OpenFile("Data\Game Data\Other.dat")
				If F = 0 Then RuntimeError("Could not open Data\Game Data\Other.dat!")
					SeekFile F, 11
					WriteByte F, E\EventData
				CloseFile(F)

			; Money options
			Case TMoney1Name, TMoney2Name, TMoney3Name, TMoney4Name, SMoney2x, SMoney3x, SMoney4x
				F = WriteFile("Data\Game Data\Money.dat")
					WriteString F, FUI_SendMessage(TMoney1Name, M_GETTEXT)
					WriteString F, FUI_SendMessage(TMoney2Name, M_GETTEXT)
					WriteShort F, FUI_SendMessage(SMoney2x, M_GETVALUE)
					WriteString F, FUI_SendMessage(TMoney3Name, M_GETTEXT)
					WriteShort F, FUI_SendMessage(SMoney3x, M_GETVALUE)
					WriteString F, FUI_SendMessage(TMoney4Name, M_GETTEXT)
					WriteShort F, FUI_SendMessage(SMoney4x, M_GETVALUE)
				CloseFile(F)

			; Gubbin remapping
			Case TGubbin1, TGubbin2, TGubbin3, TGubbin4, TGubbin5, TGubbin6
				GubbinNamesChanged = True
				F = WriteFile("Data\Game Data\Gubbins.dat")
					WriteString(F, FUI_SendMessage(TGubbin1, M_GETTEXT))
					WriteString(F, FUI_SendMessage(TGubbin2, M_GETTEXT))
					WriteString(F, FUI_SendMessage(TGubbin3, M_GETTEXT))
					WriteString(F, FUI_SendMessage(TGubbin4, M_GETTEXT))
					WriteString(F, FUI_SendMessage(TGubbin5, M_GETTEXT))
					WriteString(F, FUI_SendMessage(TGubbin6, M_GETTEXT))
				CloseFile(F)
				LoadGubbinNames()

			; Seasons tab events ----------------------------------------------------------------------------------------------------

			; Dawn hour changed
			Case SDawn
				SeasonDawnH(FUI_SendMessageI(CSeasonNum, M_GETINDEX) - 1) = E\EventData : EnvironmentSaved = False
			; Dusk hour changed
			Case SDusk
				SeasonDuskH(FUI_SendMessageI(CSeasonNum, M_GETINDEX) - 1) = E\EventData : EnvironmentSaved = False
			; Season length changed
			Case SSeasonStart
				SelectedSeason = FUI_SendMessage(CSeasonNum, M_GETINDEX)
				If SelectedSeason < 11
					Change = SeasonStartDay(SelectedSeason)
					If SelectedSeason > 1
						SeasonStartDay(SelectedSeason) = SeasonStartDay(SelectedSeason - 1) + Int(E\EventData)
					Else
						SeasonStartDay(SelectedSeason) = Int(E\EventData)
					EndIf
					Change = SeasonStartDay(SelectedSeason) - Change
				EndIf
				; Move future seasons
				For i = SelectedSeason + 1 To 11
					SeasonStartDay(i) = SeasonStartDay(i) + Change
					If SeasonStartDay(i) <= SeasonStartDay(i - 1) Then SeasonStartDay(i) = SeasonStartDay(i - 1) + 1
				Next
				EnvironmentSaved = False
			; Selected season changed
			Case CSeasonNum
				Cls
				FUI_SendMessage(TSeasonName, M_SETCAPTION, SeasonName$(Int(E\EventData) - 1))
				If Int(E\EventData) < 19
					FUI_EnableGadget(SSeasonStart)
					If Int(E\EventData) > 1
						Val = SeasonStartDay(E\EventData) - SeasonStartDay(Int(E\EventData) - 1)
						FUI_SendMessage(SSeasonStart, M_SETVALUE, Val)
					Else
						FUI_SendMessage(SSeasonStart, M_SETVALUE, SeasonStartDay(Int(E\EventData)))
					EndIf
				Else
					FUI_SendMessage(SSeasonStart, M_SETVALUE, SeasonStartDay(0) - SeasonStartDay(19))
					FUI_DisableGadget(SSeasonStart)
				EndIf
				FUI_SendMessage(SDawn, M_SETVALUE, SeasonDawnH(Int(E\EventData) - 1))
				FUI_SendMessage(SDusk, M_SETVALUE, SeasonDuskH(Int(E\EventData) - 1))
			; Month length changed
			Case SMonthStart
				SelectedMonth = FUI_SendMessage(CMonthNum, M_GETINDEX)
				If SelectedMonth < 19
					Change = MonthStartDay(SelectedMonth)
					If SelectedMonth > 1
						MonthStartDay(SelectedMonth) = MonthStartDay(SelectedMonth - 1) + Int(E\EventData)
					Else
						MonthStartDay(SelectedMonth) = Int(E\EventData)
					EndIf
					Change = MonthStartDay(SelectedMonth) - Change
				EndIf
				; Move future months
				For i = SelectedMonth + 1 To 19
					MonthStartDay(i) = MonthStartDay(i) + Change
					If MonthStartDay(i) <= MonthStartDay(i - 1) Then MonthStartDay(i) = MonthStartDay(i - 1) + 1
				Next
				EnvironmentSaved = False
			; Selected month changed
			Case CMonthNum
				FUI_SendMessage(TMonthName, M_SETCAPTION, MonthName$(Int(E\EventData) - 1))
				If Int(E\EventData) < 19
					FUI_EnableGadget(SMonthStart)
					If Int(E\EventData) > 1
						FUI_SendMessage(SMonthStart, M_SETVALUE, MonthStartDay(E\EventData) - MonthStartDay(Int(E\EventData) - 1))
					Else
						FUI_SendMessage(SMonthStart, M_SETVALUE, MonthStartDay(Int(E\EventData)))
					EndIf
				Else
					FUI_SendMessage(SMonthStart, M_SETVALUE, MonthStartDay(0) - MonthStartDay(19))
					FUI_DisableGadget(SMonthStart)
				EndIf
			; Current year changed
			Case SYear
				Year = E\EventData : EnvironmentSaved = False
			; Current day changed
			Case SDay
				Day = Int(E\EventData) - 1
				If Day > MonthStartDay(0)
					Day = MonthStartDay(0)
					FUI_SendMessage(SDay, M_SETVALUE, Day)
				EndIf
				EnvironmentSaved = False
			; Year length changed
			Case SYearLength
				MonthStartDay(0) = E\EventData
				SeasonStartDay(0) = E\EventData
				EnvironmentSaved = False
			; Time factor changed
			Case STimeFactor
				TimeFactor = E\EventData : EnvironmentSaved = False
			; Month name changed
			Case TMonthName
				MonthName$(FUI_SendMessageI(CMonthNum, M_GETINDEX) - 1) = E\EventData : EnvironmentSaved = False
			; Season name changed
			Case TSeasonName
				SeasonName$(FUI_SendMessageI(CSeasonNum, M_GETINDEX) - 1) = E\EventData : EnvironmentSaved = False
			; New sun created
			Case BSunNew
				SelectedSun = New Sun
				SelectedSun\Size# = 1.0
				For i = 0 To 11
					SelectedSun\StartH[i] = 5
					SelectedSun\EndH[i] = 22
				Next
				SelectedSun\LightR = 255
				SelectedSun\LightG = 255
				SelectedSun\LightB = 255
				UpdateSunDisplay()
				EnvironmentSaved = False
			; Sun deleted
			Case BSunDelete
				If SelectedSun <> Null
					Delete SelectedSun
					SelectedSun = First Sun
					UpdateSunDisplay()
					EnvironmentSaved = False
				EndIf
			; Next sun
			Case BSunNext
				If SelectedSun <> Null
					SelectedSun = After SelectedSun
					If SelectedSun = Null Then SelectedSun = First Sun
					UpdateSunDisplay()
				EndIf
			; Previous sun
			Case BSunPrev
				If SelectedSun <> Null
					SelectedSun = Before SelectedSun
					If SelectedSun = Null Then SelectedSun = Last Sun
					UpdateSunDisplay()
				EndIf
			; Sun texture changed
			Case BSunTex
				If SelectedSun <> Null
					NewTex = ChooseTextureDialog()
					If NewTex > -1
					
						;SelectedSun\TexID = NewTex
						;FUI_SendMessage(LSunTex, M_SETTEXT, "Sun texture: " + GetFilename$(EditorTexName$(SelectedSun\TexID)))
						;Lunar Phase Textures
						If SelectedSun\ShowPhases = False
							SelectedSun\TexID[0] = NewTex
							FUI_SendMessage(LSunTex, M_SETTEXT, "Sun texture: " + GetFilename$(EditorTexName$(SelectedSun\TexID[0])))
						Else
							SelectedSun\TexID[FUI_SendMessageI(CSunPhase, M_GETINDEX) - 1] = NewTex
							FUI_SendMessage(LSunTex, M_SETTEXT, "Phase " + (FUI_SendMessageI(CSunPhase, M_GETINDEX)) + " texture: " + GetFilename$(EditorTexName$(SelectedSun\TexID[FUI_SendMessageI(CSunPhase, M_GETINDEX) - 1])))
						EndIf
						
						EnvironmentSaved = False
					EndIf
				EndIf
				
				;Lunar Phase
				Case CSunPhase
				UpdateSunDisplay()
				
			; Phase length changed
			Case SSunPhase_Length
				If SelectedSun <> Null
					SelectedSun\Phase_Length = E\EventData
					EnvironmentSaved = False
				EndIf

			; Change saved status when checkbox has been checked
			Case BSunShowPhases
				;##########
				; Moved UpdateSunDisplay() by clan_fd on Mar 24, 2014
				;UpdateSunDisplay()
				If SelectedSun <> Null
					SelectedSun\ShowPhases = Not SelectedSun\ShowPhases
					EnvironmentSaved = False
				EndIf
				UpdateSunDisplay()
				
			; Sun size changed
			Case SSunSize
				If SelectedSun <> Null
					SelectedSun\Size# = E\EventData
					EnvironmentSaved = False
				EndIf
			; Sun rise/set times changed
			Case CSunSeason
				UpdateSunDisplay()
			Case SSunRiseH
				If SelectedSun <> Null
					SelectedSun\StartH[FUI_SendMessageI(CSunSeason, M_GETINDEX) - 1] = E\EventData
					EnvironmentSaved = False
				EndIf
			Case SSunRiseM
				If SelectedSun <> Null
					SelectedSun\StartM[FUI_SendMessageI(CSunSeason, M_GETINDEX) - 1] = E\EventData
					EnvironmentSaved = False
				EndIf
			Case SSunSetH
				If SelectedSun <> Null
					SelectedSun\EndH[FUI_SendMessageI(CSunSeason, M_GETINDEX) - 1] = E\EventData
					EnvironmentSaved = False
				EndIf
			Case SSunSetM
				If SelectedSun <> Null
					SelectedSun\EndM[FUI_SendMessageI(CSunSeason, M_GETINDEX) - 1] = E\EventData
					EnvironmentSaved = False
				EndIf
			; Sun path angle changed
			Case SSunAngle
				If SelectedSun <> Null
					SelectedSun\PathAngle# = E\EventData
					EnvironmentSaved = False
				EndIf
			; Sun light colour changed
			Case SLSunR
				If SelectedSun <> Null
					SelectedSun\LightR = E\EventData
					EnvironmentSaved = False
				EndIf
			Case SLSunG
				If SelectedSun <> Null
					SelectedSun\LightG = E\EventData
					EnvironmentSaved = False
				EndIf
			Case SLSunB
				If SelectedSun <> Null
					SelectedSun\LightB = E\EventData
					EnvironmentSaved = False
				EndIf
			Case BSunShowFlares
				If SelectedSun <> Null
					SelectedSun\ShowFlares = Not SelectedSun\ShowFlares
					EnvironmentSaved = False
				EndIf
			; Save clicked
			Case BSeasonSave
				SaveEnvironment(True)
				SaveSuns()
				EnvironmentSaved = True

			; Projectiles tab events ------------------------------------------------------------------------------------------------

			; Save projectile
			Case BProjSave
				SaveProjectiles("Data\Server Data\Projectiles.dat")
				ProjectilesSaved = True
			; New projectile
			Case BProjNew
				P.Projectile = CreateProjectile()
				P\Name$ = "New projectile"
				Item = FUI_ComboBoxItem(CProjSelected, P\Name$)
				FUI_SendMessage(Item, M_SETDATA, P\ID)
				FUI_SendMessage(CProjSelected, M_SETINDEX, TotalProjectiles + 1)
				TotalProjectiles = TotalProjectiles + 1
				UpdateProjectileDisplay()
				ProjectilesSaved = False
				ProjectilesChanged = True
			; Copy projectile
			Case BProjCopy
				If SelectedProj <> Null
					P.Projectile = CreateProjectile()
					P\Name$ = "Copy of " + SelectedProj\Name$
					P\MeshID = SelectedProj\MeshID
					P\Emitter1$ = SelectedProj\Emitter1$
					P\Emitter2$ = SelectedProj\Emitter2$
					P\Emitter1TexID = SelectedProj\Emitter1TexID
					P\Emitter2TexID = SelectedProj\Emitter2TexID
					P\Homing = SelectedProj\Homing
					P\HitChance = SelectedProj\HitChance
					P\Damage = SelectedProj\Damage
					P\DamageType = SelectedProj\DamageType
					P\Speed = SelectedProj\Speed
					Item = FUI_ComboBoxItem(CProjSelected, P\Name$)
					FUI_SendMessage(Item, M_SETDATA, P\ID)
					FUI_SendMessage(CProjSelected, M_SETINDEX, TotalProjectiles + 1)
					TotalProjectiles = TotalProjectiles + 1
					UpdateProjectileDisplay()
					ProjectilesSaved = False
					ProjectilesChanged = True
				EndIf
			; Delete projectile
			Case BProjDelete
				If SelectedProj <> Null
					FUI_SendMessage(CProjSelected, M_DELETEINDEX, FUI_SendMessage(CProjSelected, M_GETINDEX))
					FUI_SendMessage(CProjSelected, M_SETINDEX, 1)
					Delete SelectedProj
					TotalProjectiles = TotalProjectiles - 1
					UpdateProjectileDisplay()
					; Workaround for FUI not removing the combobox text when all items are deleted
					If TotalProjectiles = 0
						FUI_ComboBoxItem(CProjSelected, "No projectiles created...")
						FUI_SendMessage(CProjSelected, M_SETINDEX, 1)
						FUI_SendMessage(CProjSelected, M_DELETEINDEX, 1)
					EndIf
					ProjectilesSaved = False
					ProjectilesChanged = True
				EndIf

			; Projectile properties
			Case TProjName
				If SelectedProj <> Null
					SelectedProj\Name$ = E\EventData
					FUI_SendMessage(FUI_SendMessage(CProjSelected, M_GETSELECTED), M_SETTEXT, SelectedProj\Name$)
					ProjectilesSaved = False
					ProjectilesChanged = True
				EndIf
			Case BProjMesh
				If SelectedProj <> Null
					NewMesh = ChooseMeshDialog(MeshDialog_Static)
					If NewMesh > -1
						SelectedProj\MeshID = NewMesh
						FUI_SendMessage(LProjMesh, M_SETTEXT, "Projectile mesh: " + GetFilename$(EditorMeshName$(SelectedProj\MeshID)))
						ProjectilesSaved = False
					EndIf
				EndIf
			Case BProjMeshN
				If SelectedProj <> Null
					SelectedProj\MeshID = 65535
					FUI_SendMessage(LProjMesh, M_SETTEXT, "Projectile mesh: [NONE]")
					ProjectilesSaved = False
				EndIf
			Case CProjEmitter1
				If SelectedProj <> Null
					If Int(E\EventData) = 1
						SelectedProj\Emitter1$ = ""
					Else
						ID = FUI_SendMessage(FUI_SendMessage(CProjEmitter1, M_GETSELECTED), M_GETDATA)
						C.RP_EmitterConfig = Object.RP_EmitterConfig(ID)
						If C <> Null Then SelectedProj\Emitter1$ = C\Name$
					EndIf
					ProjectilesSaved = False
				EndIf
			Case CProjEmitter2
				If SelectedProj <> Null
					If Int(E\EventData) = 1
						SelectedProj\Emitter2$ = ""
					Else
						ID = FUI_SendMessage(FUI_SendMessage(CProjEmitter2, M_GETSELECTED), M_GETDATA)
						C.RP_EmitterConfig = Object.RP_EmitterConfig(ID)
						If C <> Null Then SelectedProj\Emitter2$ = C\Name$
					EndIf
					ProjectilesSaved = False
				EndIf
			Case BProjTex1
				If SelectedProj <> Null
					NewTex = ChooseTextureDialog()
					If NewTex > -1
						SelectedProj\Emitter1TexID = NewTex
						FUI_SendMessage(LProjTex1, M_SETTEXT, "Emitter texture: " + GetFilename$(EditorTexName$(SelectedProj\Emitter1TexID)))
						ProjectilesSaved = False
					EndIf
				EndIf
			Case BProjTex2
				If SelectedProj <> Null
					NewTex = ChooseTextureDialog()
					If NewTex > -1
						SelectedProj\Emitter2TexID = NewTex
						FUI_SendMessage(LProjTex2, M_SETTEXT, "Emitter texture: " + GetFilename$(EditorTexName$(SelectedProj\Emitter2TexID)))
						ProjectilesSaved = False
					EndIf
				EndIf
			Case BProjHoming
				If SelectedProj <> Null Then SelectedProj\Homing = E\EventData : ProjectilesSaved = False
			Case SProjHitChance
				If SelectedProj <> Null Then SelectedProj\HitChance = E\EventData : ProjectilesSaved = False
			Case SProjDamage
				If SelectedProj <> Null Then SelectedProj\Damage = E\EventData : ProjectilesSaved = False
			Case CProjDamageType
				If SelectedProj <> Null
					SelectedProj\DamageType = FUI_SendMessage(FUI_SendMessage(CProjDamageType, M_GETSELECTED), M_GETDATA)
					ProjectilesSaved = False
				EndIf
			Case SProjSpeed
				If SelectedProj <> Null Then SelectedProj\Speed = E\EventData : ProjectilesSaved = False

			; Projectile navigation
			Case CProjSelected
				UpdateProjectileDisplay()
			Case BProjPrev
				Idx = FUI_SendMessage(CProjSelected, M_GETINDEX)
				Idx = Idx - 1
				If Idx < 1 Then Idx = TotalProjectiles
				FUI_SendMessage(CProjSelected, M_SETINDEX, Idx)
				UpdateProjectileDisplay()
			Case BProjNext
				Idx = FUI_SendMessage(CProjSelected, M_GETINDEX)
				Idx = Idx + 1
				If Idx > TotalProjectiles Then Idx = 1
				FUI_SendMessage(CProjSelected, M_SETINDEX, Idx)
				UpdateProjectileDisplay()

			; Interface tab events --------------------------------------------------------------------------------------------------

			Case BInterfaceSave
				SaveInterfaceSettings("Data\Game Data\Interface.dat")
				InterfaceSaved = True
			Case RInterfaceMain, RInterfaceInventory ;, RInterfaceLogin ;[255]
				UpdateInterfaceComponentsList() ;[255]
				
			Case LInterfaceComponents
				Idx = FUI_SendMessage(LInterfaceComponents, M_GETSELECTED)
				ID = FUI_SendMessage(FUI_SendMessage(LInterfaceComponents, M_GETINDEX), M_GETDATA)
				If Idx > 0
					If FUI_SendMessage(RInterfaceMain, M_GETCHECKED) And Idx >= 6 ;[255]
						UpdateInterfaceComponent(AttributeDisplays(ID))
					Else
						UpdateInterfaceComponent(Object.InterfaceComponent(ID))
					EndIf
				Else
					UpdateInterfaceComponent(SelectedIC)
				EndIf
				
			Case SInterfaceX
				SelectedIC\X# = Float#(E\EventData) / 100.0
				UpdateInterfaceComponent(SelectedIC)
				InterfaceSaved = False
			Case SInterfaceY
				SelectedIC\Y# = Float#(E\EventData) / 100.0
				UpdateInterfaceComponent(SelectedIC)
				InterfaceSaved = False
			Case SInterfaceWidth
				SelectedIC\Width# = Float#(E\EventData) / 100.0
				UpdateInterfaceComponent(SelectedIC)
				InterfaceSaved = False
			Case SInterfaceHeight
				SelectedIC\Height# = Float#(E\EventData) / 100.0
				UpdateInterfaceComponent(SelectedIC)
				InterfaceSaved = False
			Case SInterfaceR
				SelectedIC\R = E\EventData
				InterfaceSaved = False
			Case SInterfaceG
				SelectedIC\G = E\EventData
				InterfaceSaved = False
			Case SInterfaceB
				SelectedIC\B = E\EventData
				InterfaceSaved = False
			Case SInterfaceA
				SelectedIC\Alpha# = Float#(E\EventData) / 255.0
				UpdateInterfaceComponent(SelectedIC)
				InterfaceSaved = False
			Case BInterfaceChatTex
				NewID = ChooseTextureDialog()
				If NewID > -1
					Chat\Texture = NewID
					FUI_SendMessage(LInterfaceChatTex, M_SETTEXT, EditorTexName$(NewID))
					InterfaceSaved = False
				EndIf
			Case BInterfaceChatTexN
					Chat\Texture = 65535
					FUI_SendMessage(LInterfaceChatTex, M_SETTEXT, "[NONE]")
					InterfaceSaved = False

			; Attributes tab events -------------------------------------------------------------------------------------------------

			Case BAttributeSave
				SaveAttributes("Data\Server Data\Attributes.dat")
				StatsSaved = True
			Case BSetFixedAttributes
				FixedAttributeDialog()
			Case LAttribute
				ID = FUI_SendMessage(FUI_SendMessage(LAttribute, M_GETINDEX), M_GETDATA)
				FUI_SendMessage(TAttributeName, M_SETCAPTION, AttributeNames$(ID))
				FUI_SendMessage(BAttributeSkill, M_SETCHECKED, AttributeIsSkill(ID))
				FUI_SendMessage(BAttributeHidden, M_SETCHECKED, AttributeHidden(ID))
			Case SAttributeAssignment
				AttributeAssignment = E\EventData
				StatsSaved = False
				AttributesChanged = True
			Case BAttributeAdd
				Found = False
				For i = 0 To 39
					If AttributeNames$(i) = ""
						AttributeNames$(i) = "New attribute"
						Item = FUI_ListBoxItem(LAttribute, AttributeNames$(i))
						FUI_SendMessage(Item, M_SETDATA, i)
						StatsSaved = False
						AttributesChanged = True
						Found = True
						Exit
					EndIf
				Next
				If Found = False Then FUI_CustomMessageBox("Limit of 40 attributes reached!", "Error", MB_OK)
			Case BAttributeDelete
				If FUI_SendMessage(LAttribute, M_GETSELECTED) > 0
					ID = FUI_SendMessage(FUI_SendMessage(LAttribute, M_GETINDEX), M_GETDATA)
					AttributeNames$(ID) = ""
					AttributeIsSkill(ID) = False
					FUI_SendMessage(LAttribute, M_DELETEINDEX, FUI_SendMessage(LAttribute, M_GETSELECTED))
					FUI_SendMessage(LAttribute, M_SETINDEX, 1)
					FUI_CreateEvent(LAttribute, 1)
					StatsSaved = False
					AttributesChanged = True
				EndIf
			Case TAttributeName
				If FUI_SendMessage(LAttribute, M_GETSELECTED) > 0
					ID = FUI_SendMessage(FUI_SendMessage(LAttribute, M_GETINDEX), M_GETDATA)
					AttributeNames$(ID) = E\EventData
					FUI_SendMessage(FUI_SendMessage(LAttribute, M_GETINDEX), M_SETTEXT, E\EventData)
					StatsSaved = False
					AttributesChanged = True
				EndIf
			Case BAttributeSkill
				If FUI_SendMessage(LAttribute, M_GETSELECTED) > 0
					ID = FUI_SendMessage(FUI_SendMessage(LAttribute, M_GETINDEX), M_GETDATA)
					AttributeIsSkill(ID) = E\EventData
					StatsSaved = False
				EndIf
			Case BAttributeHidden
				If FUI_SendMessage(LAttribute, M_GETSELECTED) > 0
					ID = FUI_SendMessage(FUI_SendMessage(LAttribute, M_GETINDEX), M_GETDATA)
					AttributeHidden(ID) = E\EventData
					StatsSaved = False
				EndIf

			; Zones tab events ------------------------------------------------------------------------------------------------------

			Case SCameraSpeed
				CamSpeed# = Float#(E\EventData) / 8.0

			; Other options changed
			Case CEntryScript
				If FUI_SendMessage(CEntryScript, M_GETINDEX) = 1
					CurrentArea\EntryScript$ = ""
				Else
					CurrentArea\EntryScript$ = FUI_SendMessage(CEntryScript, M_GETCAPTION)
				EndIf
				ZoneSaved = False
			Case CExitScript
				If FUI_SendMessage(CExitScript, M_GETINDEX) = 1
					CurrentArea\ExitScript$ = ""
				Else
					CurrentArea\ExitScript$ = FUI_SendMessage(CExitScript, M_GETCAPTION)
				EndIf
				ZoneSaved = False
			Case BPvP
				CurrentArea\PvP = E\EventData
				ZoneSaved = False
			Case BLoadingTex
				NewID = ChooseTextureDialog()
				If NewID > -1
					LoadingTexID = NewID
					FUI_SendMessage(LLoadingTex, M_SETTEXT, "Load image: " + GetFilename$(EditorTexName$(NewID)))
					ZoneSaved = False
				EndIf
			Case BLoadingTexN
				LoadingTexID = 65535
				FUI_SendMessage(LLoadingTex, M_SETTEXT, "Load image: [NONE]")
				ZoneSaved = False
			Case BLoadingMusic
				NewID = ChooseMusicDialog()
				If NewID > -1
					LoadingMusicID = NewID
					FUI_SendMessage(LLoadingMusic, M_SETTEXT, "Load music: " + GetFilename$(EditorMusicName$(NewID)))
					ZoneSaved = False
				EndIf
			Case BLoadingMusicN
				LoadingMusicID = 65535
				FUI_SendMessage(LLoadingMusic, M_SETTEXT, "Load music: [NONE]")
				ZoneSaved = False
			Case BLargeMapTex
				NewID = ChooseTextureDialog()
				If NewID > -1
					MapTexID = NewID
					FUI_SendMessage(LLargeMapTex, M_SETTEXT, "Map texture: " + GetFilename$(EditorTexName$(NewID)))
					ZoneSaved = False
				EndIf

			; Environment options changed
			Case SGravity
				CurrentArea\Gravity = Int(E\EventData) + 200
				ZoneSaved = False
			Case SSlopeRestrict
				SlopeRestrict# = E\EventData
				ZoneSaved = False
			Case BOutdoors
				Outdoors = E\EventData
				ZoneSaved = False
			Case BSkyTex
				NewID = ChooseTextureDialog()
				If NewID > -1
					UnloadTexture(SkyTexID)
					SkyTexID = NewID : ZoneSaved = False
					EntityTexture SkyEN, GetTexture(SkyTexID)
					EntityAlpha SkyEN, 1.0
				EndIf
			Case BCloudTex
				NewID = ChooseTextureDialog()
				If NewID > -1
					UnloadTexture(CloudTexID)
					CloudTexID = NewID : ZoneSaved = False
					EntityTexture CloudEN, GetTexture(CloudTexID)
					EntityAlpha CloudEN, 0.4
				EndIf
			Case BStormCloudTex
				NewID = ChooseTextureDialog()
				If NewID > -1
					UnloadTexture(StormCloudTexID)
					StormCloudTexID = NewID : ZoneSaved = False
					EntityTexture CloudEN, GetTexture(StormCloudTexID)
					EntityAlpha CloudEN, 0.4
				EndIf
			Case BStarTex
				NewID = ChooseTextureDialog()
				If NewID > -1
					UnloadTexture(StarsTexID)
					StarsTexID = NewID : ZoneSaved = False
					EntityTexture SkyEN, GetTexture(StarsTexID)
					EntityAlpha SkyEN, 1.0
				EndIf
			Case SWeatherChance(W_Rain - 1)
				CurrentArea\WeatherChance[W_Rain - 1] = E\EventData
				Total = 0
				For i = 0 To 4 : Total = Total + CurrentArea\WeatherChance[i] : Next
				If Total > 100
					CurrentArea\WeatherChance[W_Rain - 1] = 100 - (Total - CurrentArea\WeatherChance[W_Rain - 1])
					FUI_SendMessage(SWeatherChance(W_Rain - 1), M_SETVALUE, CurrentArea\WeatherChance[W_Rain - 1])
				EndIf
				ZoneSaved = False
			Case SWeatherChance(W_Snow - 1)
				CurrentArea\WeatherChance[W_Snow - 1] = E\EventData
				Total = 0
				For i = 0 To 4 : Total = Total + CurrentArea\WeatherChance[i] : Next
				If Total > 100
					CurrentArea\WeatherChance[W_Snow - 1] = 100 - (Total - CurrentArea\WeatherChance[W_Snow - 1])
					FUI_SendMessage(SWeatherChance(W_Snow - 1), M_SETVALUE, CurrentArea\WeatherChance[W_Snow - 1])
				EndIf
				ZoneSaved = False
			Case SWeatherChance(W_Fog - 1)
				CurrentArea\WeatherChance[W_Fog - 1] = E\EventData
				Total = 0
				For i = 0 To 4 : Total = Total + CurrentArea\WeatherChance[i] : Next
				If Total > 100
					CurrentArea\WeatherChance[W_Fog - 1] = 100 - (Total - CurrentArea\WeatherChance[W_Fog - 1])
					FUI_SendMessage(SWeatherChance(W_Fog - 1), M_SETVALUE, CurrentArea\WeatherChance[W_Fog - 1])
				EndIf
				ZoneSaved = False
			Case SWeatherChance(W_Storm - 1)
				CurrentArea\WeatherChance[W_Storm - 1] = E\EventData
				Total = 0
				For i = 0 To 4 : Total = Total + CurrentArea\WeatherChance[i] : Next
				If Total > 100
					CurrentArea\WeatherChance[W_Storm - 1] = 100 - (Total - CurrentArea\WeatherChance[W_Storm - 1])
					FUI_SendMessage(SWeatherChance(W_Storm - 1), M_SETVALUE, CurrentArea\WeatherChance[W_Storm - 1])
				EndIf
				ZoneSaved = False
			Case SWeatherChance(W_Wind - 1)
				CurrentArea\WeatherChance[W_Wind - 1] = E\EventData
				Total = 0
				For i = 0 To 4 : Total = Total + CurrentArea\WeatherChance[i] : Next
				If Total > 100
					CurrentArea\WeatherChance[W_Wind - 1] = 100 - (Total - CurrentArea\WeatherChance[W_Wind - 1])
					FUI_SendMessage(SWeatherChance(W_Wind - 1), M_SETVALUE, CurrentArea\WeatherChance[W_Wind - 1])
				EndIf
				ZoneSaved = False
			Case CWeatherLink
				If E\EventData = 1
					CurrentArea\WeatherLink$ = ""
				Else
					CurrentArea\WeatherLink$ = FUI_SendMessage(CWeatherLink, M_GETCAPTION)
				EndIf
				ZoneSaved = False
			Case SLFogNear
				FogNear# = E\EventData
				SetViewDistance(ZoneCam, FogNear#, FogFar#)
				ZoneSaved = False
			Case SLFogFar
				FogFar# = E\EventData
				SetViewDistance(ZoneCam, FogNear#, FogFar#)
				ZoneSaved = False
			Case SLFogR
				FogR = E\EventData
				CameraFogColor ZoneCam, FogR, FogG, FogB : CameraClsColor ZoneCam, FogR, FogG, FogB
				ZoneSaved = False
			Case SLFogG
				FogG = E\EventData
				CameraFogColor ZoneCam, FogR, FogG, FogB : CameraClsColor ZoneCam, FogR, FogG, FogB
				ZoneSaved = False
			Case SLFogB
				FogB = E\EventData
				CameraFogColor ZoneCam, FogR, FogG, FogB : CameraClsColor ZoneCam, FogR, FogG, FogB
				ZoneSaved = False
			Case SLAmbientR
				AmbientR = E\EventData
				AmbientLight AmbientR, AmbientG, AmbientB
				ZoneSaved = False
			Case SLAmbientG
				AmbientG = E\EventData
				AmbientLight AmbientR, AmbientG, AmbientB
				ZoneSaved = False
			Case SLAmbientB
				AmbientB = E\EventData
				AmbientLight AmbientR, AmbientG, AmbientB
				ZoneSaved = False
			Case SDefaultLightPitch
				DefaultLightPitch# = E\EventData
				RotateEntity(DefaultLight, DefaultLightPitch#, DefaultLightYaw#, 0)
				ZoneSaved = False
			Case SDefaultLightYaw
				DefaultLightYaw# = E\EventData
				RotateEntity(DefaultLight, DefaultLightPitch#, DefaultLightYaw#, 0)
				ZoneSaved = False

			; Waypoint linking buttons
			Case BWaypointA
				CreateWPUndo("WP Link")
				WP = Mid$(EntityName$(SelectedEN), 2)
				If CurrentArea\NextWaypointA[WP] < 2000
					NextWP = CurrentArea\NextWaypointA[WP]
					CurrentArea\NextWaypointA[WP] = 2000
					CurrentArea\PrevWaypoint[NextWP] = 2000
					FreeEntity WaypointALink(WP) : WaypointALink(WP) = 0
					ZoneSaved = False
				EndIf
				ZoneMode = 6 : FlushMouse : FlushKeys
				FUI_SendMessage(BZoneSelect, M_SETSELECTED, True)
				FUI_SendMessage(BZoneSelect, M_SETSELECTED, False)
			Case BWaypointB
				CreateWPUndo("WP Link")
				WP = Mid$(EntityName$(SelectedEN), 2)
				If CurrentArea\NextWaypointB[WP] < 2000
					NextWP = CurrentArea\NextWaypointB[WP]
					CurrentArea\NextWaypointB[WP] = 2000
					CurrentArea\PrevWaypoint[NextWP] = 2000
					FreeEntity WaypointBLink(WP) : WaypointBLink(WP) = 0
					ZoneSaved = False
				EndIf
				ZoneMode = 7 : FlushMouse : FlushKeys
				FUI_SendMessage(BZoneSelect, M_SETSELECTED, True)
				FUI_SendMessage(BZoneSelect, M_SETSELECTED, False)
			Case BWaypointAN
				CreateWPUndo("WP Link")
				WP = Mid$(EntityName$(SelectedEN), 2)
				If CurrentArea\NextWaypointA[WP] < 2000
					NextWP = CurrentArea\NextWaypointA[WP]
					CurrentArea\NextWaypointA[WP] = 2000
					CurrentArea\PrevWaypoint[NextWP] = 2000
					FreeEntity WaypointALink(WP) : WaypointALink(WP) = 0
					ZoneSaved = False
				EndIf
			Case BWaypointBN
				CreateWPUndo("WP Link")
				WP = Mid$(EntityName$(SelectedEN), 2)
				If CurrentArea\NextWaypointB[WP] < 2000
					NextWP = CurrentArea\NextWaypointB[WP]
					CurrentArea\NextWaypointB[WP] = 2000
					CurrentArea\PrevWaypoint[NextWP] = 2000
					FreeEntity WaypointBLink(WP) : WaypointBLink(WP) = 0
					ZoneSaved = False
				EndIf

			; Waypoint options changed
			Case CSpawnActor
				WP = Mid$(EntityName$(SelectedEN), 2)
				SpawnNum = GetSpawnPoint(WP)
				If FUI_SendMessage(CSpawnActor, M_GETINDEX) = 1
					If SpawnNum > -1 Then CurrentArea\SpawnMax[SpawnNum] = 0
					FUI_DisableGadget(CSpawnScript) : FUI_DisableGadget(CSpawnActorScript) : FUI_DisableGadget(CSpawnDeathScript)
					FUI_DisableGadget(SSpawnFrequency) : FUI_DisableGadget(SSpawnMax) : FUI_DisableGadget(SSpawnRange)
				Else
					If SpawnNum = -1
						; Find free spawn number
						For i = 0 To 999
							If CurrentArea\SpawnMax[i] = 0 Then SpawnNum = i : Exit
						Next
						If SpawnNum > -1
							FUI_EnableGadget(CSpawnScript) : FUI_EnableGadget(CSpawnActorScript) : FUI_EnableGadget(CSpawnDeathScript)
							FUI_EnableGadget(SSpawnFrequency) : FUI_EnableGadget(SSpawnMax) : FUI_EnableGadget(SSpawnRange)
							FUI_SendMessage(CSpawnScript, M_SETINDEX, 1)
							FUI_SendMessage(CSpawnActorScript, M_SETINDEX, 1)
							FUI_SendMessage(CSpawnDeathScript, M_SETINDEX, 1)
							FUI_SendMessage(SSpawnMax, M_SETVALUE, 1)
							FUI_SendMessage(SSpawnRange, M_SETVALUE, 0)
							FUI_SendMessage(SSpawnFrequency, M_SETVALUE, 10)
							CurrentArea\SpawnScript$[SpawnNum] = ""
							CurrentArea\SpawnMax[SpawnNum] = 1
							CurrentArea\SpawnRange#[SpawnNum] = 0.0
							CurrentArea\SpawnFrequency[SpawnNum] = 10
							CurrentArea\SpawnWaypoint[SpawnNum] = WP
							CurrentArea\SpawnActor[SpawnNum] = FUI_SendMessage(FUI_SendMessage(CSpawnActor, M_GETSELECTED), M_GETDATA)
							CurrentArea\SpawnSize#[SpawnNum] = 5.0
							ScaleEntity(SelectedEN, 5.0, 5.0, 5.0)
							EntityRadius(SelectedEN, 3.0)
							
							;Checks health and displays warning if needed cysis145
							CheckActorHealth() 
						; Not found - tell user
						Else
							FUI_SendMessage(CSpawnActor, M_SETINDEX, 1)
							FUI_CustomMessageBox("Maximum spawn points already created!", "Error", MB_OK)
						EndIf
					Else
						CurrentArea\SpawnActor[SpawnNum] = FUI_SendMessage(FUI_SendMessage(CSpawnActor, M_GETSELECTED), M_GETDATA)
					;cysis145
					CheckActorHealth() 
					
					EndIf
				EndIf
				ZoneSaved = False
			Case CSpawnDeathScript
				WP = Mid$(EntityName$(SelectedEN), 2)
				SpawnNum = GetSpawnPoint(WP)
				If FUI_SendMessage(CSpawnDeathScript, M_GETINDEX) = 1
					CurrentArea\SpawnDeathScript$[SpawnNum] = ""
				Else
					CurrentArea\SpawnDeathScript$[SpawnNum] = FUI_SendMessage(CSpawnDeathScript, M_GETCAPTION)
				EndIf
				ZoneSaved = False
			Case CSpawnActorScript
				WP = Mid$(EntityName$(SelectedEN), 2)
				SpawnNum = GetSpawnPoint(WP)
				If FUI_SendMessage(CSpawnActorScript, M_GETINDEX) = 1
					CurrentArea\SpawnActorScript$[SpawnNum] = ""
				Else
					CurrentArea\SpawnActorScript$[SpawnNum] = FUI_SendMessage(CSpawnActorScript, M_GETCAPTION)
				EndIf
				ZoneSaved = False
			Case CSpawnScript
				WP = Mid$(EntityName$(SelectedEN), 2)
				SpawnNum = GetSpawnPoint(WP)
				If FUI_SendMessage(CSpawnScript, M_GETINDEX) = 1
					CurrentArea\SpawnScript$[SpawnNum] = ""
				Else
					CurrentArea\SpawnScript$[SpawnNum] = FUI_SendMessage(CSpawnScript, M_GETCAPTION)
				EndIf
				ZoneSaved = False
			Case SSpawnFrequency
				WP = Mid$(EntityName$(SelectedEN), 2)
				SpawnNum = GetSpawnPoint(WP)
				CurrentArea\SpawnFrequency[SpawnNum] = E\EventData
				ZoneSaved = False
			Case SSpawnMax
				WP = Mid$(EntityName$(SelectedEN), 2)
				SpawnNum = GetSpawnPoint(WP)
				CurrentArea\SpawnMax[SpawnNum] = E\EventData
				ZoneSaved = False
			Case SSpawnRange
				WP = Mid$(EntityName$(SelectedEN), 2)
				SpawnNum = GetSpawnPoint(WP)
				CurrentArea\SpawnRange#[SpawnNum] = E\EventData
				UpdateWaypointRange(SelectedEN)
				ZoneSaved = False
			Case SWaypointPause
				WP = Mid$(EntityName$(SelectedEN), 2)
				CurrentArea\WaypointPause[WP] = E\EventData
				ZoneSaved = False

			; Portals
			Case CPortalLinkArea
				UpdatePortalNameList()
			Case CPortalLinkAreaO
				FUI_SendMessage(CPortalLinkArea, M_SETINDEX, E\EventData)
				UpdatePortalNameList()
				PortalID = Mid$(EntityName$(SelectedEN), 2)
				If E\EventData = 1
					CurrentArea\PortalLinkArea$[PortalID] = ""
				Else
					CurrentArea\PortalLinkArea$[PortalID] = FUI_SendMessage(CPortalLinkAreaO, M_GETCAPTION)
				EndIf
			Case CPortalLinkNameO
				PortalID = Mid$(EntityName$(SelectedEN), 2)
				CurrentArea\PortalLinkName$[PortalID] = FUI_SendMessage(CPortalLinkNameO, M_GETCAPTION)

			; Triggers
			Case CZoneTriggerScript
				UpdateScriptMethodsList(CZoneTriggerMethod, FUI_SendMessage(CZoneTriggerScript, M_GETCAPTION), "")

			; Sound zones
			Case SSoundVolume
				SZ.SoundZone = Object.SoundZone(EntityName$(SelectedEN))
				SZ\Volume = E\EventData
				ZoneSaved = False
			Case SSoundRepeat
				SZ.SoundZone = Object.SoundZone(EntityName$(SelectedEN))
				SZ\RepeatTime = E\EventData
				ZoneSaved = False
			Case BZoneSound
				NewID = ChooseSoundDialog(SoundDialog_All)
				If NewID > -1
					FUI_SendMessage(LSound, M_SETCAPTION, EditorSoundName$(NewID))
					ZoneMusicID = -1
					ZoneSoundID = NewID
				EndIf
			Case BZoneMusic
				NewID = ChooseMusicDialog()
				If NewID > -1
					FUI_SendMessage(LSound, M_SETCAPTION, EditorMusicName$(NewID))
					ZoneMusicID = NewID
					ZoneSoundID = -1
				EndIf

			; Water
			Case SLWaterR
				W.Water = Object.Water(EntityName$(SelectedEN))
				If W <> Null
					W\Red = E\EventData
					ZoneSaved = False
				EndIf
			Case SLWaterG
				W.Water = Object.Water(EntityName$(SelectedEN))
				If W <> Null
					W\Green = E\EventData
					ZoneSaved = False
				EndIf
			Case SLWaterB
				W.Water = Object.Water(EntityName$(SelectedEN))
				If W <> Null
					W\Blue = E\EventData
					ZoneSaved = False
				EndIf
			Case SWaterTexScale
				W.Water = Object.Water(EntityName$(SelectedEN))
				If W <> Null
					W\TexScale# = E\EventData
					ScaleTexture W\TexHandle, W\TexScale#, W\TexScale#
					ZoneSaved = False
				EndIf
			Case BWaterTex
				NewID = ChooseTextureDialog()
				If NewID > -1
					W.Water = Object.Water(EntityName$(SelectedEN))
					UnloadTexture(W\TexID)
					W\TexID = NewID
					W\TexHandle = GetTexture(W\TexID)
					EntityTexture W\EN, W\TexHandle
					ZoneSaved = False
				EndIf
			Case SLWaterOpacity
				W.Water = Object.Water(EntityName$(SelectedEN))
				If W <> Null
					W\Opacity = E\EventData
					If W\Opacity >= 100
						EntityFX(W\EN, 1 + 16)
					Else
						EntityFX(W\EN, 16)
					EndIf
					Alpha# = Float#(W\Opacity) / 100.0
					If Alpha# > 1.0 Then Alpha# = 1.0
					EntityAlpha(W\EN, Alpha#)
					ZoneSaved = False
				EndIf
			Case SWaterDamage
				W.Water = Object.Water(EntityName$(SelectedEN))
				If W <> Null
					SW.ServerWater = Object.ServerWater(W\ServerWater)
					SW\Damage = E\EventData
					ZoneSaved = False
				EndIf
			Case CWaterDamageType
				W.Water = Object.Water(EntityName$(SelectedEN))
				SW.ServerWater = Object.ServerWater(W\ServerWater)
				SW\DamageType = FUI_SendMessage(FUI_SendMessage(CWaterDamageType, M_GETSELECTED), M_GETDATA)
				ZoneSaved = False

			; Emitters
			Case BEmitterTex
				NewID = ChooseTextureDialog("Particles")
				If NewID > -1
					Emi.Emitter = Object.Emitter(EntityName$(SelectedEN))
					UnloadTexture(Emi\TexID)
					Emi\TexID = NewID
					RP_ConfigTexture(Emi\Config, GetTexture(Emi\TexID), 1, 1, False)
					ZoneSaved = False
				EndIf

			; Terrains
			Case STerrainDetail
				T.Terrain = Object.Terrain(EntityName$(SelectedEN))
				T\Detail = E\EventData
				TerrainDetail(T\EN, T\Detail, T\Morph)
				ZoneSaved = False
			Case BTerrainMorph
				T.Terrain = Object.Terrain(EntityName$(SelectedEN))
				T\Morph = E\EventData
				TerrainDetail(T\EN, T\Detail, T\Morph)
				ZoneSaved = False
			Case BTerrainShade
				T.Terrain = Object.Terrain(EntityName$(SelectedEN))
				T\Shading = E\EventData
				TerrainShading(T\EN, T\Shading)
				ZoneSaved = False
			Case STerrainDetailScale
				T.Terrain = Object.Terrain(EntityName$(SelectedEN))
				T\DetailTexScale# = E\EventData
				If T\DetailTex <> 0 Then ScaleTexture(T\DetailTex, T\DetailTexScale#, T\DetailTexScale#)
				ZoneSaved = False
			Case BTerrainCM
				NewID = ChooseTextureDialog()
				If NewID > -1
					T.Terrain = Object.Terrain(EntityName$(SelectedEN))
					T\BaseTexID = NewID
					Tex = GetTexture(T\BaseTexID, True)
					ScaleTexture(Tex, TerrainSize(T\EN), TerrainSize(T\EN))
					EntityTexture(T\EN, Tex, 0, 0)
					FreeTexture(Tex)
					ZoneSaved = False
				EndIf
			Case BTerrainDM
				NewID = ChooseTextureDialog()
				If NewID > -1
					T.Terrain = Object.Terrain(EntityName$(SelectedEN))
					T\DetailTexID = NewID
					T\DetailTex = GetTexture(T\DetailTexID, True)
					ScaleTexture(T\DetailTex, T\DetailTexScale#, T\DetailTexScale#)
					EntityTexture(T\EN, T\DetailTex, 0, 1)
					ZoneSaved = False
				EndIf
			Case BTerrainHM
				FileTypes$ = "Bitmap (*.bmp)|*.bmp|JPEG (*.jpg)|*.jpg|PNG (*.png)|*.png|"
				Result = FUI_CustomOpenDialog("Choose file to add...", "", FileTypes$, False)
				If Result = True
					TerrainHMFilename$ = app\CurrentFile$
					Name$ = TerrainHMFilename$
					If Len(Name$) > 30 Then Name$ = "..." + Right$(Name$, 27)
					FUI_SendMessage(LTerrain, M_SETCAPTION, Name$)
					ZoneSaved = False
				EndIf
			Case BTerrainFlat
				TerrainHMFilename$ = ""
				FUI_SendMessage(LTerrain, M_SETCAPTION, "None (flat terrain)")
				ZoneSaved = False

			; Scenery
			Case BSceneryLocked
				Sc.Scenery = Object.Scenery(EntityName$(SelectedEN))
				Sc\Locked = Not Sc\Locked
				CreateUndo("Lock", Handle(Sc))
			Case BSceneryDuplicate
				DuplicateSelected()
			Case CSceneryCollision
				EntityType SelectedEN, Int(E\EventData) - 1
				ZoneSaved = False
			Case CSceneryAnim
				Sc.Scenery = Object.Scenery(EntityName$(SelectedEN))
				Name$ = MeshNames$(Sc\MeshID)
				If Name$ <> ""
					IsAnim = Asc(Right$(Name$, 1))
					If IsAnim = True
						Sc\AnimationMode = Int(E\EventData) - 1
						ZoneSaved = False
					Else
						FUI_CustomMessageBox("This mesh is not animated", "Error", MB_OK)
					EndIf
				EndIf
			Case SSceneryInvenSize
;				Sc.Scenery = Object.Scenery(EntityName$(SelectedEN))
;				If E\EventData > 0
;					; If this scenery doesn't have an ownership ID, find one For it
;					If Sc\SceneryID = 0
;						For i = 0 To 499
;							If CurrentArea\Instances[0]\OwnedScenery[i] = Null
;								CurrentArea\Instances[0]\OwnedScenery[i] = New OwnedScenery
;								Sc\SceneryID = i + 1
;								FUI_SendMessage(BSceneryOwnable, M_SETCHECKED, True)
;								FUI_SendMessage(LSceneryID, M_SETCAPTION, "Ownership ID: " + Str$(Sc\SceneryID - 1))
;								Exit
;							EndIf
;						Next
;					EndIf
;					; Apply inventory size If an ID was found, Or already present
;					If Sc\SceneryID > 0
;						CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1]\InventorySize = E\EventData
;						If CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1]\InventorySize = 0
;							If CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1]\Inventory <> Null
;								Delete CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1]\Inventory
;							EndIf
;						Else
;							If CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1]\Inventory = Null
;								CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1]\Inventory = New Inventory
;							EndIf
;						EndIf
;						ZoneSaved = False
;					Else
;						FUI_CustomMessageBox("Maximum ownable scenery objects already set!", "Error", MB_OK)
;					EndIf
;				Else
;					If Sc\SceneryID > 0
;						If CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1] <> Null
;							CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1]\InventorySize = 0
;						EndIf
;						ZoneSaved = False
;					EndIf
;				EndIf
			Case BSceneryOwnable
;				Sc.Scenery = Object.Scenery(EntityName$(SelectedEN))
;				If E\EventData = False
;					If Sc\SceneryID > 0
;						If CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1] <> Null
;							If CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1]\Inventory <> Null
;								Delete CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1]\Inventory
;							EndIf
;							Delete CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1]
;						EndIf
;						Sc\SceneryID = 0
;						FUI_SendMessage(LSceneryID, M_SETCAPTION, "Ownership ID: N/A")
;						ZoneSaved = False
;					EndIf
;				Else
;					If Sc\SceneryID = 0
;						Found = False
;						For i = 0 To 499
;							If CurrentArea\Instances[0]\OwnedScenery[i] = Null
;								CurrentArea\Instances[0]\OwnedScenery[i] = New OwnedScenery
;								Sc\SceneryID = i + 1
;								FUI_SendMessage(LSceneryID, M_SETCAPTION, "Ownership ID: " + Str$(Sc\SceneryID - 1))
;								Found = True
;								ZoneSaved = False
;								Exit
;							EndIf
;						Next
;						If Found = False Then FUI_CustomMessageBox("Maximum ownable scenery objects already set!", "Error", MB_OK)
;					EndIf
;				EndIf
			Case BSceneryCatchRain
				Sc.Scenery = Object.Scenery(EntityName$(SelectedEN))
				Sc\CatchRain = E\EventData
				
				
			;v1.104 buttons
			Case BSceneryCast ;[010]
				Sc.Scenery = Object.Scenery(EntityName$(SelectedEN))
				Sc\CastShadow =  E\EventData
				
			Case BSceneryReceive
				Sc.Scenery = Object.Scenery(EntityName$(SelectedEN))
				Sc\ReceiveShadow =  E\EventData
			
			Case BScenery
				NewMesh = ChooseMeshDialog(MeshDialog_All)
				If NewMesh > -1
					ZoneSceneryMeshID = NewMesh
					FUI_SendMessage(LScenery, M_SETCAPTION, GetFilename$(EditorMeshName$(NewMesh)))
				EndIf

			; Mode changed
			Case BZoneSelect
				FUI_SendMessage(BZoneSelect, M_SETSELECTED, True)
				ZoneMode = 1
				FlushMouse()
				FlushKeys()
			Case BZoneMove
				FUI_SendMessage(BZoneMove, M_SETSELECTED, True)
				ZoneMode = 2
				FlushMouse()
				FlushKeys()
			Case BZoneRotate
				FUI_SendMessage(BZoneRotate, M_SETSELECTED, True)
				ZoneMode = 3
				FlushMouse()
				FlushKeys()
			Case BZoneScale
				FUI_SendMessage(BZoneScale, M_SETSELECTED, True)
				ZoneMode = 4
				FlushMouse()
				FlushKeys()

			; Scale entire zone
			Case BScaleEntireZone
				ScaleEntireZoneDialog()

			; Precise movement mode
			Case BZonePrecise
				If SelectedEN <> 0 Then PreciseEditSelected()

			; View changed
			Case BZoneUndo      : PerformUndo()
			Case BZoneScenery   : CreateUndo("Mode", ZoneView, SelectedEN) : UpdateZoneDisplay(1)
			Case BZoneTerrain   : CreateUndo("Mode", ZoneView, SelectedEN) : UpdateZoneDisplay(3)
			Case BZoneEmitters  : CreateUndo("Mode", ZoneView, SelectedEN) : UpdateZoneDisplay(5)
			Case BZoneWater     : CreateUndo("Mode", ZoneView, SelectedEN) : UpdateZoneDisplay(7)
			Case BZoneColBox    : CreateUndo("Mode", ZoneView, SelectedEN) : UpdateZoneDisplay(9)
			Case BZoneSoundZone : CreateUndo("Mode", ZoneView, SelectedEN) : UpdateZoneDisplay(11)
			Case BZoneTriggers  : CreateUndo("Mode", ZoneView, SelectedEN) : UpdateZoneDisplay(13)
			Case BZoneWaypoints : CreateUndo("Mode", ZoneView, SelectedEN) : UpdateZoneDisplay(15)
			Case BZonePortals   : CreateUndo("Mode", ZoneView, SelectedEN) : UpdateZoneDisplay(17)
			Case BZoneEnviro    : CreateUndo("Mode", ZoneView, SelectedEN) : UpdateZoneDisplay(19)
			Case BZoneOther     : CreateUndo("Mode", ZoneView, SelectedEN) : UpdateZoneDisplay(20)
			Case BZoneLights    : CreateUndo("Mode", ZoneView, SelectedEN) : UpdateZoneDisplay(21)

			; New zone
			Case BZoneNew
				; Save changes
				If ZoneSaved = False
					Result = FUI_CustomMessageBox("Save changes to current zone first?", "New zone", MB_YESNO)
					If Result = IDYES Then ZoneSave()
				EndIf

				; New zone
				ZoneUnload()
				FUI_SendMessage(CZone, M_SETINDEX, 1)
				CurrentArea = ServerCreateArea()
				UpdateZoneDisplay(ZoneView)
				ZoneSaved = True

			; Save zone
			Case BZoneSave
				ZoneSave()

			; Copy zone
			Case BZoneCopy
				OldName$ = CurrentArea\Name$
				NewName$ = AreaNameDialog()
				If NewName$ <> ""
					CurrentArea\Name$ = NewName$
					ZoneSave()
					NewArea.Area = ServerCopyArea(CurrentArea)
					NewArea\Name$ = NewName$
					TotalZones = TotalZones + 1
					Item = FUI_ComboBoxItem(CZone, NewName$)
					FUI_SendMessage(Item, M_SETDATA, Handle(NewArea))
					FUI_ComboBoxItem(CPortalLinkArea, NewName$)
					FUI_ComboBoxItem(CPortalLinkAreaO, NewName$)
					FUI_ComboBoxItem(CActorStartArea, NewName$)
					FUI_ComboBoxItem(CWeatherLink, NewName$)
					CurrentArea\Name$ = OldName$
				EndIf

			; Load zone
			Case CZone
				; Save changes
				If ZoneSaved = False
					Result = FUI_CustomMessageBox("Save changes to current zone first?", "Load zone", MB_YESNO)
					If Result = IDYES Then ZoneSave()
				EndIf

				; Unload current zone
				ZoneUnload()

				; New zone
				If FUI_SendMessage(CZone, M_GETINDEX) = 1
					CurrentArea = ServerCreateArea()
				; Load zone
				Else
					; Find area
					CurrentArea = Object.Area(FUI_SendMessage(FUI_SendMessage(CZone, M_GETSELECTED), M_GETDATA))
					If CurrentArea = Null Then RuntimeError("Invalid zone!")

					; Load client side stuff
					TextureFilter("", 1 + 8)
					LoadArea(CurrentArea\Name$, ZoneCam, True)
					ClearTextureFilters() : TextureFilter("m_", 1 + 4) : TextureFilter("a_", 1 + 2)

					; Match client water with server water
					For W.Water = Each Water
						For SW.ServerWater = Each ServerWater
							If SW\Area = CurrentArea
								If SW\Width# = W\ScaleX# And SW\Depth# = W\ScaleZ#
									If SW\X# = EntityX#(W\EN) - (W\ScaleX# / 2.0) And SW\Z# = EntityZ#(W\EN) - (W\ScaleZ# / 2.0)
										If SW\Y# = EntityY#(W\EN)
											W\ServerWater = Handle(SW)
											Exit
										EndIf
									EndIf
								EndIf
							EndIf
						Next

						; A match could not be found - delete this water area
						If W\ServerWater = 0
							UnloadTexture(W\TexID)
							FreeTexture(W\TexHandle)
							FreeEntity(W\EN)
							Delete(W)
						EndIf
					Next

					; Load meshes for server side parts
					For i = 0 To 149
						If CurrentArea\TriggerScript$[i] <> ""
							TriggerEN(i) = CreateSphere()
							EntityColor TriggerEN(i), 0, 0, 255
							EntityAlpha TriggerEN(i), 0.5
							NameEntity TriggerEN(i), "T" + Str$(i)
							PositionEntity TriggerEN(i), CurrentArea\TriggerX#[i], CurrentArea\TriggerY#[i], CurrentArea\TriggerZ#[i]
							ScaleEntity TriggerEN(i), CurrentArea\TriggerSize#[i], CurrentArea\TriggerSize#[i], CurrentArea\TriggerSize#[i]
						EndIf
					Next
					For i = 0 To 99
						If CurrentArea\PortalName$[i] <> ""
							PortalEN(i) = CopyEntity(ZonePortalEN)
							EntityAlpha PortalEN(i), 0.5
							NameEntity PortalEN(i), "P" + Str$(i)
							PositionEntity PortalEN(i), CurrentArea\PortalX#[i], CurrentArea\PortalY#[i], CurrentArea\PortalZ#[i]
							RotateEntity PortalEN(i), 0, CurrentArea\PortalYaw#[i], 0
							ScaleEntity PortalEN(i), CurrentArea\PortalSize#[i], CurrentArea\PortalSize#[i], CurrentArea\PortalSize#[i]
						EndIf
					Next
					For i = 0 To 1999
						If CurrentArea\PrevWaypoint[i] <> 2005
							WaypointEN(i) = CopyEntity(ZoneWaypointEN)
							ScaleEntity WaypointEN(i), 3.0, 3.0, 3.0
							EntityRadius WaypointEN(i), 3.0
							PositionEntity WaypointEN(i), CurrentArea\WaypointX#[i], CurrentArea\WaypointY#[i], CurrentArea\WaypointZ#[i]
							NameEntity WaypointEN(i), "W" + Str$(i)
							SpawnNum = GetSpawnPoint(i)
							If SpawnNum > -1
								Size# = CurrentArea\SpawnSize#[SpawnNum]
								ScaleEntity WaypointEN(i), Size#, Size#, Size#
								EntityRadius WaypointEN(i), Size#
							EndIf
						EndIf
					Next
					For i = 0 To 1999
						If CurrentArea\NextWaypointA[i] < 2000
							NextWP = CurrentArea\NextWaypointA[i]
							WaypointALink(i) = CreateCube()
							EntityColor WaypointALink(i), 0, 100, 255
							ScaleMesh WaypointALink(i), 0.1, 0.1, 0.5
							PositionMesh WaypointALink(i), 0, 0, 0.5
							ScaleEntity WaypointALink(i), 1, 1, EntityDistance#(WaypointEN(i), WaypointEN(NextWP)), True
							PositionEntity WaypointALink(i), EntityX#(WaypointEN(i)), EntityY#(WaypointEN(i)), EntityZ#(WaypointEN(i))
							PointEntity WaypointALink(i), WaypointEN(NextWP)
						EndIf
						If CurrentArea\NextWaypointB[i] < 2000
							NextWP = CurrentArea\NextWaypointB[i]
							WaypointBLink(i) = CreateCube()
							EntityColor WaypointBLink(i), 255, 100, 0
							ScaleMesh WaypointBLink(i), 0.1, 0.1, 0.5
							PositionMesh WaypointBLink(i), 0, 0, 0.5
							ScaleEntity WaypointBLink(i), 1, 1, EntityDistance#(WaypointEN(i), WaypointEN(NextWP)), True
							PositionEntity WaypointBLink(i), EntityX#(WaypointEN(i)), EntityY#(WaypointEN(i)), EntityZ#(WaypointEN(i))
							PointEntity WaypointBLink(i), WaypointEN(NextWP)
						EndIf
					Next

					; Use server default light
					RotateEntity(DefaultLight, DefaultLightPitch#, DefaultLightYaw#, 0)

					; Hide grass to increase rendering speed
					Tree_HideAll(1)

					; Initialise
					PositionEntity ZoneCam, 0.0, 5.0, 0.0
					RotateEntity ZoneCam, 0, 0, 0
					FlushMouse()

				EndIf
				UpdateZoneDisplay(ZoneView)
				ZoneSaved = True

			; Delete zone
			Case BZoneDelete
				Result = FUI_CustomMessageBox("Really delete this zone?", "Delete zone", MB_YESNO)
				If Result = IDYES
					; Delete zone
					If CurrentArea\Name$ <> ""
						DeleteFile("Data\Areas\" + CurrentArea\Name$ + ".dat")
						DeleteFile("Data\Server Data\Areas\" + CurrentArea\Name$ + ".dat")
						FUI_SendMessage(CZone, M_DELETEINDEX, FUI_SendMessage(CZone, M_GETINDEX))
						FUI_SendMessage(CActorStartArea, M_RESET)
						FUI_SendMessage(CWeatherLink, M_RESET)
						FUI_SendMessage(CPortalLinkArea, M_RESET)
						FUI_SendMessage(CPortalLinkAreaO, M_RESET)
						FUI_ComboBoxItem(CWeatherLink, "None")
						FUI_ComboBoxItem(CPortalLinkArea, "None")
						FUI_ComboBoxItem(CPortalLinkAreaO, "None")
						For Ar.Area = Each Area
							If Ar <> CurrentArea
								FUI_ComboBoxItem(CActorStartArea, Ar\Name$)
								FUI_ComboBoxItem(CWeatherLink, Ar\Name$)
								FUI_ComboBoxItem(CPortalLinkArea, Ar\Name$)
								FUI_ComboBoxItem(CPortalLinkAreaO, Ar\Name$)
							EndIf
						Next
					EndIf

					; Unload zone
					ZoneUnload()
					Delete CurrentArea
					TotalZones = TotalZones - 1

					; New zone
					FUI_SendMessage(CZone, M_SETINDEX, 1)
					CurrentArea = ServerCreateArea()
					UpdateZoneDisplay(ZoneView)
					ZoneSaved = True
				EndIf

			; Actors tab events -----------------------------------------------------------------------------------------------------

			; Save actors
			Case BActorSave
				UpdateActorPreview()
				SaveActors("Data\Server Data\Actors.dat")
				ActorsSaved = True
			; New actor
			Case BActorNew
				At.Actor = CreateActor()
				At\Race$ = "New actor"
				Item = FUI_ComboBoxItem(CActorSelected, At\Race$ + " [" + At\Class$ + "]")
				FUI_SendMessage(Item, M_SETDATA, At\ID)
				FUI_SendMessage(CActorSelected, M_SETINDEX, TotalActors + 1)
				TotalActors = TotalActors + 1
				UpdateActorDisplay()
				ActorsSaved = False
				ActorsChanged = True
			; Copy actor
			Case BActorCopy
				If SelectedActor <> Null
					At.Actor = CreateActor()
					At\Race$ = SelectedActor\Race$ + " (Copy)"
					At\Class$ = SelectedActor\Class$
					At\Description$ = SelectedActor\Description$
					At\StartArea$ = SelectedActor\StartArea$
					At\StartPortal$ = SelectedActor\StartPortal$
					At\Radius# = SelectedActor\Radius#
					At\Scale# = SelectedActor\Scale#
					For i = 0 To 7 : At\MeshIDs[i] = SelectedActor\MeshIDs[i] : Next
					For i = 0 To 4 : At\BeardIDs[i] = SelectedActor\BeardIDs[i] : Next
					For i = 0 To 4 : At\MaleHairIDs[i] = SelectedActor\MaleHairIDs[i] : Next
					For i = 0 To 4 : At\FemaleHairIDs[i] = SelectedActor\FemaleHairIDs[i] : Next
					For i = 0 To 4 : At\MaleFaceIDs[i] = SelectedActor\MaleFaceIDs[i] : Next
					For i = 0 To 4 : At\FemaleFaceIDs[i] = SelectedActor\FemaleFaceIDs[i] : Next
					For i = 0 To 4 : At\MaleBodyIDs[i] = SelectedActor\MaleBodyIDs[i] : Next
					For i = 0 To 4 : At\FemaleBodyIDs[i] = SelectedActor\FemaleBodyIDs[i] : Next
					For i = 0 To 15 : At\MSpeechIDs[i] = SelectedActor\MSpeechIDs[i] : Next
					For i = 0 To 15 : At\FSpeechIDs[i] = SelectedActor\FSpeechIDs[i] : Next
					At\BloodTexID = SelectedActor\BloodTexID
					At\Genders = SelectedActor\Genders
					For i = 0 To 39 : At\Attributes\Value[i] = SelectedActor\Attributes\Value[i] : Next
					For i = 0 To 39 : At\Attributes\Maximum[i] = SelectedActor\Attributes\Maximum[i] : Next
					For i = 0 To 19 : At\Resistances[i] = SelectedActor\Resistances[i] : Next
					At\MAnimationSet = SelectedActor\MAnimationSet
					At\FAnimationSet = SelectedActor\FAnimationSet
					At\Playable = SelectedActor\Playable
					At\Rideable = SelectedActor\Rideable
					At\Aggressiveness = SelectedActor\Aggressiveness
					At\AggressiveRange = SelectedActor\AggressiveRange
					At\TradeMode = SelectedActor\TradeMode
					At\Environment = SelectedActor\Environment
					At\InventorySlots = SelectedActor\InventorySlots
					At\DefaultDamageType = SelectedActor\DefaultDamageType
					At\DefaultFaction = SelectedActor\DefaultFaction
					At\XPMultiplier = SelectedActor\XPMultiplier
					At\PolyCollision = SelectedActor\PolyCollision
					Item = FUI_ComboBoxItem(CActorSelected, At\Race$ + " [" + At\Class$ + "]")
					FUI_SendMessage(Item, M_SETDATA, At\ID)
					FUI_SendMessage(CActorSelected, M_SETINDEX, TotalActors + 1)
					TotalActors = TotalActors + 1
					UpdateActorDisplay()
					ActorsSaved = False
					ActorsChanged = True
				EndIf
			; Delete actor
			Case BActorDelete
				If SelectedActor <> Null
					If FUI_MessageBox("Do you want to delete this actor?","Delete Actor",MB_OKCANCEL) = 1
					
						FUI_SendMessage(CActorSelected, M_DELETEINDEX, FUI_SendMessage(CActorSelected, M_GETINDEX))
						FUI_SendMessage(CActorSelected, M_SETINDEX, 1)
						At.Actor = SelectedActor
						SelectedActor = Null
						UpdateActorPreview()
						Local tempID% = At\ID
						Delete At\Attributes
						Delete At
						
						Local tempZone.Area
						For tempZone = Each Area
							For i = 0 To 999
								If tempZone\SpawnActor[i] = tempID
									tempZone\SpawnScript$[i] = ""
									tempZone\SpawnMax[i] = 0
									tempZone\SpawnRange#[i] = 0.0
									tempZone\SpawnFrequency[i] = 10
									tempZone\SpawnWaypoint[i] = 0
									tempZone\SpawnActor[i] = 0
									tempZone\SpawnSize#[i] = 5.0
									ZoneSaved = False
								EndIf
							Next
						Next
						
						TotalActors = TotalActors - 1
						UpdateActorDisplay()
						; Workaround for FUI not removing the combobox text when all items are deleted
						If TotalActors = 0
							FUI_ComboBoxItem(CActorSelected, "No actors created...")
							FUI_SendMessage(CActorSelected, M_SETINDEX, 1)
							FUI_SendMessage(CActorSelected, M_DELETEINDEX, 1)
						EndIf
						ActorsSaved = False
						ActorsChanged = True
					EndIf
				EndIf

			; Actor description
			Case TActorRace
				If SelectedActor <> Null
					SelectedActor\Race$ = FilterChars$(E\EventData)
					FUI_SendMessage(FUI_SendMessage(CActorSelected, M_GETSELECTED), M_SETTEXT, SelectedActor\Race$ + " [" + SelectedActor\Class$ + "]")
					ActorsSaved = False
					ActorsChanged = True
				EndIf
			Case TActorClass
				If SelectedActor <> Null
					SelectedActor\Class$ = FilterChars$(E\EventData)
					FUI_SendMessage(FUI_SendMessage(CActorSelected, M_GETSELECTED), M_SETTEXT, SelectedActor\Race$ + " [" + SelectedActor\Class$ + "]")
					ActorsSaved = False
					ActorsChanged = True
				EndIf
			Case TActorBlurb
				If SelectedActor <> Null Then SelectedActor\Description$ = E\EventData : ActorsSaved = False
			Case CActorGenders
				If SelectedActor <> Null
					SelectedActor\Genders = Int(E\EventData) - 1
					UpdateActorGenderGadgets()
					ActorsSaved = False
				EndIf
			Case CActorFaction
				If SelectedActor <> Null
					SelectedActor\DefaultFaction = FUI_SendMessage(FUI_SendMessage(CActorFaction, M_GETSELECTED), M_GETDATA)
					ActorsSaved = False
				EndIf

			; Actor general
			Case SActorAttackRange
				If SelectedActor <> Null Then SelectedActor\AggressiveRange = E\EventData : ActorsSaved = False
			Case CActorAttacks
				If SelectedActor <> Null Then SelectedActor\Aggressiveness = Int(E\EventData) - 1 : ActorsSaved = False
			Case CActorTrades
				If SelectedActor <> Null Then SelectedActor\TradeMode = Int(E\EventData) - 1 : ActorsSaved = False
			Case CActorEnviro
				If SelectedActor <> Null Then SelectedActor\Environment = Int(E\EventData) - 1 : ActorsSaved = False
			Case CActorStartArea
				If SelectedActor <> Null Then SelectedActor\StartArea$ = FUI_SendMessage(CActorStartArea, M_GETCAPTION) : ActorsSaved = False
			Case TActorStartPortal
				If SelectedActor <> Null Then SelectedActor\StartPortal$ = E\EventData : ActorsSaved = False
			Case SActorXPMultiplier
				If SelectedActor <> Null Then SelectedActor\XPMultiplier = E\EventData : ActorsSaved = False			
			Case BActorPlayable
				If SelectedActor <> Null Then SelectedActor\Playable = E\EventData : ActorsSaved = False
			Case BActorRideable
				If SelectedActor <> Null Then SelectedActor\Rideable = E\EventData : ActorsSaved = False
			Case CActorMAnim
				If SelectedActor <> Null
					SelectedActor\MAnimationSet = FUI_SendMessage(FUI_SendMessage(CActorMAnim, M_GETSELECTED), M_GETDATA)
					ActorsSaved = False
				EndIf
			Case CActorFAnim
				If SelectedActor <> Null
					SelectedActor\FAnimationSet = FUI_SendMessage(FUI_SendMessage(CActorFAnim, M_GETSELECTED), M_GETDATA)
					ActorsSaved = False
				EndIf
			Case CActorMSpeech
				Name$ = EditorSoundName$(SelectedActor\MSpeechIDs[Int(FUI_SendMessage(CActorMSpeech, M_GETINDEX)) - 1])
				If Len(Name$) > 70 Then Name$ = "..." + Right$(Name$, 67)
				FUI_SendMessage(LActorMSpeech, M_SETCAPTION, Name$)
			Case BActorMSpeech
				Selected = Int(FUI_SendMessage(CActorMSpeech, M_GETINDEX)) - 1
				NewSound = ChooseSoundDialog(SoundDialog_3D)
				If NewSound > -1
					SelectedActor\MSpeechIDs[Selected] = NewSound
					FUI_SendMessage(LActorMSpeech, M_SETCAPTION, EditorSoundName$(SelectedActor\MSpeechIDs[Selected]))
					ActorsSaved = False
				EndIf
			Case BActorMSpeechN
				If SelectedActor <> Null
					SelectedActor\MSpeechIDs[Int(FUI_SendMessage(CActorMSpeech, M_GETINDEX)) - 1] = 65535
					FUI_SendMessage(LActorMSpeech, M_SETCAPTION, "[NONE]")
					ActorsSaved = False
				EndIf
			Case CActorFSpeech
				Name$ = EditorSoundName$(SelectedActor\FSpeechIDs[Int(FUI_SendMessage(CActorFSpeech, M_GETINDEX)) - 1])
				If Len(Name$) > 70 Then Name$ = "..." + Right$(Name$, 67)
				FUI_SendMessage(LActorFSpeech, M_SETCAPTION, Name$)
			Case BActorFSpeech
				Selected = Int(FUI_SendMessage(CActorFSpeech, M_GETINDEX)) - 1
				NewSound = ChooseSoundDialog(SoundDialog_3D)
				If NewSound > -1
					SelectedActor\FSpeechIDs[Selected] = NewSound
					FUI_SendMessage(LActorFSpeech, M_SETCAPTION, EditorSoundName$(SelectedActor\FSpeechIDs[Selected]))
					ActorsSaved = False
				EndIf
			Case BActorFSpeechN
				If SelectedActor <> Null
					SelectedActor\FSpeechIDs[Int(FUI_SendMessage(CActorFSpeech, M_GETINDEX)) - 1] = 65535
					FUI_SendMessage(LActorFSpeech, M_SETCAPTION, "[NONE]")
					ActorsSaved = False
				EndIf
			Case CActorSlotAllowed
				Flag = FUI_SendMessageI(CActorSlotAllowed, M_GETINDEX) - 1
				CheckInvetorySlotAllowed( flag )
				
			Case BActorSlotAllowed
				Flag = FUI_SendMessageI(CActorSlotAllowed, M_GETINDEX) - 1
				If E\EventData = False
					; Make slot available
					SelectedActor\InventorySlots = SelectedActor\InventorySlots Or (1 Shl Flag)
				Else
					SelectedActor\InventorySlots = SelectedActor\InventorySlots And ($FFFFFFFF Xor (1 Shl Flag))
				EndIf
				ActorsSaved = False

			; Actor appearance
			Case BActorPolyCollision
				If SelectedActor <> Null
					SelectedActor\PolyCollision = E\EventData
					ActorsSaved = False
				EndIf
			Case SActorScale
				If SelectedActor <> Null
					NewScale# = Float#(E\EventData) / 100.0
					SelectedActor\Radius# = SelectedActor\Radius# * (NewScale# / SelectedActor\Scale#)
					SelectedActor\Scale# = NewScale#
					ActorsSaved = False
				EndIf
			Case BActorBlood
				If SelectedActor <> Null
					NewMesh = ChooseTextureDialog()
					If NewMesh > -1
						SelectedActor\BloodTexID = NewMesh
						FUI_SendMessage(LActorBlood, M_SETCAPTION, "Blood texture: " + GetFilename$(EditorTexName$(SelectedActor\BloodTexID)))
						ActorsSaved = False
					EndIf
				EndIf
			Case BActorMBodyMesh
				If SelectedActor <> Null
					NewMesh = ChooseMeshDialog(MeshDialog_Animated, "Actors")
					If NewMesh > -1
						SelectedActor\MeshIDs[0] = NewMesh
						FUI_SendMessage(LActorMBodyMesh, M_SETCAPTION, "Male body mesh: " + GetFilename$(EditorMeshName$(SelectedActor\MeshIDs[0])))
						UpdateActorPreview()
						ActorsSaved = False
					EndIf
				EndIf
			Case BActorFBodyMesh
				If SelectedActor <> Null
					NewMesh = ChooseMeshDialog(MeshDialog_Animated, "Actors")
					If NewMesh > -1
						SelectedActor\MeshIDs[1] = NewMesh
						FUI_SendMessage(LActorFBodyMesh, M_SETCAPTION, "Female body mesh: " + GetFilename$(EditorMeshName$(SelectedActor\MeshIDs[1])))
						UpdateActorPreview()
						ActorsSaved = False
					EndIf
				EndIf
			Case CActorMHairMesh
				If SelectedActor <> Null
					FUI_SendMessage(LActorMHairMesh, M_SETCAPTION, "Male hair mesh: " + GetFilename$(EditorMeshName$(SelectedActor\MaleHairIDs[Int(E\EventData) - 1])))
				EndIf
			Case BActorMHairMesh
				If SelectedActor <> Null
					NewMesh = ChooseMeshDialog(MeshDialog_All, "Hair")
					If NewMesh > -1
						Selected = FUI_SendMessageI(CActorMHairMesh, M_GETINDEX) - 1
						SelectedActor\MaleHairIDs[Selected] = NewMesh
						FUI_SendMessage(LActorMHairMesh, M_SETCAPTION, "Male hair mesh: " + GetFilename$(EditorMeshName$(SelectedActor\MaleHairIDs[Selected])))
						If Selected = 0 Then UpdateActorPreview()
						ActorsSaved = False
					EndIf
				EndIf
			Case BActorMHairMeshN
				If SelectedActor <> Null
					Selected = FUI_SendMessageI(CActorMHairMesh, M_GETINDEX) - 1
					SelectedActor\MaleHairIDs[Selected] = 65535
					FUI_SendMessage(LActorMHairMesh, M_SETCAPTION, "Male hair mesh: [NONE]")
					If Selected = 0 Then UpdateActorPreview()
					ActorsSaved = False
				EndIf
			Case CActorFHairMesh
				If SelectedActor <> Null
					FUI_SendMessage(LActorFHairMesh, M_SETCAPTION, "Female hair mesh: " + GetFilename$(EditorMeshName$(SelectedActor\FemaleHairIDs[Int(E\EventData) - 1])))
				EndIf
			Case BActorFHairMesh
				If SelectedActor <> Null
					NewMesh = ChooseMeshDialog(MeshDialog_All, "Hair")
					If NewMesh > -1
						Selected = FUI_SendMessageI(CActorFHairMesh, M_GETINDEX) - 1
						SelectedActor\FemaleHairIDs[Selected] = NewMesh
						FUI_SendMessage(LActorFHairMesh, M_SETCAPTION, "Female hair mesh: " + GetFilename$(EditorMeshName$(SelectedActor\FemaleHairIDs[Selected])))
						If Selected = 0 Then UpdateActorPreview()
						ActorsSaved = False
					EndIf
				EndIf
			Case BActorFHairMeshN
				If SelectedActor <> Null
					Selected = FUI_SendMessageI(CActorFHairMesh, M_GETINDEX) - 1
					SelectedActor\FemaleHairIDs[Selected] = 65535
					FUI_SendMessage(LActorFHairMesh, M_SETCAPTION, "Female hair mesh: [NONE]")
					If Selected = 0 Then UpdateActorPreview()
					ActorsSaved = False
				EndIf
			Case CActorMFaceTex
				If SelectedActor <> Null
					FUI_SendMessage(LActorMFaceTex, M_SETCAPTION, "Male face texture: " + GetFilename$(EditorTexName$(SelectedActor\MaleFaceIDs[Int(E\EventData) - 1])))
				EndIf
			Case BActorMFaceTex
				If SelectedActor <> Null
					NewMesh = ChooseTextureDialog("Actors")
					If NewMesh > -1
						Selected = FUI_SendMessageI(CActorMFaceTex, M_GETINDEX) - 1
						SelectedActor\MaleFaceIDs[Selected] = NewMesh
						FUI_SendMessage(LActorMFaceTex, M_SETCAPTION, "Male face texture: " + GetFilename$(EditorTexName$(SelectedActor\MaleFaceIDs[Selected])))
						If Selected = 0 Then UpdateActorPreview()
						ActorsSaved = False
					EndIf
				EndIf
			Case BActorMFaceTexN
				If SelectedActor <> Null
					SelectedActor\MaleFaceIDs[FUI_SendMessageI(CActorMFaceTex, M_GETINDEX) - 1] = 65535
					FUI_SendMessage(LActorMFaceTex, M_SETCAPTION, "Male face texture: [NONE]")
					ActorsSaved = False
				EndIf
			Case CActorFFaceTex
				If SelectedActor <> Null
					FUI_SendMessage(LActorFFaceTex, M_SETCAPTION, "Female face texture: " + GetFilename$(EditorTexName$(SelectedActor\FemaleFaceIDs[Int(E\EventData) - 1])))
				EndIf
			Case BActorFFaceTex
				If SelectedActor <> Null
					NewMesh = ChooseTextureDialog("Actors")
					If NewMesh > -1
						Selected = FUI_SendMessageI(CActorFFaceTex, M_GETINDEX) - 1
						SelectedActor\FemaleFaceIDs[Selected] = NewMesh
						FUI_SendMessage(LActorFFaceTex, M_SETCAPTION, "Female face texture: " + GetFilename$(EditorTexName$(SelectedActor\FemaleFaceIDs[Selected])))
						If Selected = 0 Then UpdateActorPreview()
						ActorsSaved = False
					EndIf
				EndIf
			Case BActorFFaceTexN
				If SelectedActor <> Null
					SelectedActor\FemaleFaceIDs[FUI_SendMessageI(CActorFFaceTex, M_GETINDEX) - 1] = 65535
					FUI_SendMessage(LActorFFaceTex, M_SETCAPTION, "Female face texture: [NONE]")
					ActorsSaved = False
				EndIf
			Case CActorMBodyTex
				If SelectedActor <> Null
					FUI_SendMessage(LActorMBodyTex, M_SETCAPTION, "Male body texture: " + GetFilename$(EditorTexName$(SelectedActor\MaleBodyIDs[Int(E\EventData) - 1])))
				EndIf
			Case BActorMBodyTex
				If SelectedActor <> Null
					NewMesh = ChooseTextureDialog("Actors")
					If NewMesh > -1
						Selected = FUI_SendMessageI(CActorMBodyTex, M_GETINDEX) - 1
						SelectedActor\MaleBodyIDs[Selected] = NewMesh
						FUI_SendMessage(LActorMBodyTex, M_SETCAPTION, "Male body texture: " + GetFilename$(EditorTexName$(SelectedActor\MaleBodyIDs[Selected])))
						If Selected = 0 Then UpdateActorPreview()
						ActorsSaved = False
					EndIf
				EndIf
			Case BActorMBodyTexN
				If SelectedActor <> Null
					Selected = FUI_SendMessageI(CActorMBodyTex, M_GETINDEX) - 1
					If Selected > 0
						SelectedActor\MaleBodyIDs[Selected] = 65535
						FUI_SendMessage(LActorMBodyTex, M_SETCAPTION, "Male body texture: [NONE]")
						ActorsSaved = False
					EndIf
				EndIf
			Case CActorFBodyTex
				If SelectedActor <> Null
					FUI_SendMessage(LActorFBodyTex, M_SETCAPTION, "Female body texture: " + GetFilename$(EditorTexName$(SelectedActor\FemaleBodyIDs[Int(E\EventData) - 1])))
				EndIf
			Case BActorFBodyTex
				If SelectedActor <> Null
					NewMesh = ChooseTextureDialog("Actors")
					If NewMesh > -1
						Selected = FUI_SendMessageI(CActorFBodyTex, M_GETINDEX) - 1
						SelectedActor\FemaleBodyIDs[Selected] = NewMesh
						FUI_SendMessage(LActorFBodyTex, M_SETCAPTION, "Female body texture: " + GetFilename$(EditorTexName$(SelectedActor\FemaleBodyIDs[Selected])))
						If Selected = 0 Then UpdateActorPreview()
						ActorsSaved = False
					EndIf
				EndIf
			Case BActorFBodyTexN
				If SelectedActor <> Null
					Selected = FUI_SendMessageI(CActorFBodyTex, M_GETINDEX) - 1
					If Selected > 0
						SelectedActor\FemaleBodyIDs[Selected] = 65535
						FUI_SendMessage(LActorFBodyTex, M_SETCAPTION, "Female body texture: [NONE]")
						ActorsSaved = False
					EndIf
				EndIf
			Case CActorBeardMesh
				If SelectedActor <> Null
					FUI_SendMessage(LActorBeardMesh, M_SETCAPTION, "Beard mesh: " + GetFilename$(EditorMeshName$(SelectedActor\BeardIDs[Int(E\EventData) - 1])))
				EndIf
			Case BActorBeardMesh
				If SelectedActor <> Null
					NewMesh = ChooseMeshDialog(MeshDialog_All, "Beards")
					If NewMesh > -1
						Selected = FUI_SendMessageI(CActorBeardMesh, M_GETINDEX) - 1
						SelectedActor\BeardIDs[Selected] = NewMesh
						FUI_SendMessage(LActorBeardMesh, M_SETCAPTION, "Beard mesh: " + GetFilename$(EditorMeshName$(SelectedActor\BeardIDs[Selected])))
						If Selected = 0 Then UpdateActorPreview()
						ActorsSaved = False
					EndIf
				EndIf
			Case BActorBeardMeshN
				If SelectedActor <> Null
					Selected = FUI_SendMessageI(CActorBeardMesh, M_GETINDEX) - 1
					SelectedActor\BeardIDs[Selected] = 65535
					FUI_SendMessage(LActorBeardMesh, M_SETCAPTION, "Beard mesh: [NONE]")
					If Selected = 0 Then UpdateActorPreview()
					ActorsSaved = False
				EndIf
			Case CActorGubbinMesh
				If SelectedActor <> Null
					FUI_SendMessage(LActorGubbinMesh, M_SETCAPTION, "Gubbin mesh: " + GetFilename$(EditorMeshName$(SelectedActor\MeshIDs[Int(E\EventData) + 1])))
				EndIf
			Case BActorGubbinMesh
				If SelectedActor <> Null
					NewMesh = ChooseMeshDialog(MeshDialog_All, "Gubbins")
					If NewMesh > -1
						Selected = FUI_SendMessageI(CActorGubbinMesh, M_GETINDEX) + 1
						SelectedActor\MeshIDs[Selected] = NewMesh
						FUI_SendMessage(LActorGubbinMesh, M_SETCAPTION, "Gubbin mesh: " + GetFilename$(EditorMeshName$(SelectedActor\MeshIDs[Selected])))
						UpdateActorPreview()
						ActorsSaved = False
					EndIf
				EndIf
			Case BActorGubbinMeshN
				If SelectedActor <> Null
					SelectedActor\MeshIDs[FUI_SendMessageI(CActorGubbinMesh, M_GETINDEX) + 1] = 65535
					FUI_SendMessage(LActorGubbinMesh, M_SETCAPTION, "Gubbin mesh: [NONE]")
					UpdateActorPreview()
					ActorsSaved = False
				EndIf

			; Attributes
			Case LActorAttributes
				If SelectedActor <> Null
					ID = FUI_SendMessage(FUI_SendMessage(LActorAttributes, M_GETINDEX), M_GETDATA)
					FUI_SendMessage(SActorAttribute, M_SETVALUE, SelectedActor\Attributes\Value[ID])
					FUI_SendMessage(SActorAttributeMax, M_SETVALUE, SelectedActor\Attributes\Maximum[ID])
				EndIf
			Case SActorAttribute
				If SelectedActor <> Null
					ID = FUI_SendMessage(FUI_SendMessage(LActorAttributes, M_GETINDEX), M_GETDATA)
					SelectedActor\Attributes\Value[ID] = E\EventData
					ActorsSaved = False
				EndIf
			Case SActorAttributeMax
				If SelectedActor <> Null
					ID = FUI_SendMessage(FUI_SendMessage(LActorAttributes, M_GETINDEX), M_GETDATA)
					SelectedActor\Attributes\Maximum[ID] = E\EventData
					ActorsSaved = False
				EndIf
			Case LActorResistances
				If SelectedActor <> Null
					ID = FUI_SendMessage(FUI_SendMessage(LActorResistances, M_GETINDEX), M_GETDATA)
					FUI_SendMessage(SActorResistance, M_SETVALUE, SelectedActor\Resistances[ID])
				EndIf
			Case SActorResistance
				If SelectedActor <> Null
					ID = FUI_SendMessage(FUI_SendMessage(LActorResistances, M_GETINDEX), M_GETDATA)
					SelectedActor\Resistances[ID] = E\EventData
					ActorsSaved = False
				EndIf

			; Actor navigation
			Case CActorSelected
				UpdateActorDisplay()
			Case BActorPrev
				Idx = FUI_SendMessage(CActorSelected, M_GETINDEX)
				Idx = Idx - 1
				If Idx < 1 Then Idx = TotalActors
				FUI_SendMessage(CActorSelected, M_SETINDEX, Idx)
				If SelectedActor <> Null
					AttIdx = FUI_SendMessage(LActorAttributes, M_GETINDEX)
					ResIdx = FUI_SendMessage(LActorResistances, M_GETINDEX)
					ID = FUI_SendMessage(AttIdx, M_GETDATA)
					FUI_SendMessage(SActorAttribute, M_SETVALUE, SelectedActor\Attributes\Value[ID])
					FUI_SendMessage(SActorAttributeMax, M_SETVALUE, SelectedActor\Attributes\Maximum[ID])
					ID = FUI_SendMessage(ResIdx, M_GETDATA)
					FUI_SendMessage(SActorResistance, M_SETVALUE, SelectedActor\Resistances[ID])
				EndIf
				UpdateActorDisplay()
			Case BActorNext
				Idx = FUI_SendMessage(CActorSelected, M_GETINDEX)
				Idx = Idx + 1
				If Idx > TotalActors Then Idx = 1
				FUI_SendMessage(CActorSelected, M_SETINDEX, Idx)
				If SelectedActor <> Null
					AttIdx = FUI_SendMessage(LActorAttributes, M_GETINDEX)
					ResIdx = FUI_SendMessage(LActorResistances, M_GETINDEX)
					ID = FUI_SendMessage(AttIdx, M_GETDATA)
					FUI_SendMessage(SActorAttribute, M_SETVALUE, SelectedActor\Attributes\Value[ID])
					FUI_SendMessage(SActorAttributeMax, M_SETVALUE, SelectedActor\Attributes\Maximum[ID])
					ID = FUI_SendMessage(ResIdx, M_GETDATA)
					FUI_SendMessage(SActorResistance, M_SETVALUE, SelectedActor\Resistances[ID])
				EndIf
				UpdateActorDisplay()

			; Animation sets tab events ---------------------------------------------------------------------------------------------
			Case BAnimsSave
				SaveAnimSets("Data\Game Data\Animations.dat")
				AnimsSaved = True
			Case LAnimSets
				If FUI_SendMessage(LAnimSets, M_GETSELECTED) <> 0
					ID = FUI_SendMessage(FUI_SendMessage(LAnimSets, M_GETINDEX), M_GETDATA)
					FUI_SendMessage(TAnimSetName, M_SETCAPTION, AnimList(ID)\Name$)
					FUI_SendMessage(LAnims, M_RESET)
					For i = 149 To 0 Step -1
						If AnimList(ID)\AnimName$[i] <> ""
							Item = FUI_ListBoxItem(LAnims, AnimList(ID)\AnimName$[i]) : FUI_SendMessage(Item, M_SETDATA, i)
						EndIf
					Next
				EndIf
			Case BAnimSetAdd
				ID = CreateAnimSet()
				If ID = -1
					FUI_CustomMessageBox("Limit of 1000 animation sets reached!", "Error", MB_OK)
				Else
					AS.AnimSet = AnimList(ID)
					AS\Name$ = "New set"
					Item = FUI_ListBoxItem(LAnimSets, AS\Name$) : FUI_SendMessage(Item, M_SETDATA, ID)
					AnimsSaved = False
					AnimsChanged = True
				EndIf
			Case BAnimSetCopy
				If FUI_SendMessage(LAnimSets, M_GETSELECTED) <> 0
					ID = CreateAnimSet()
					If ID = -1
						FUI_CustomMessageBox("Limit of 1000 animation sets reached!", "Error", MB_OK)
					Else
						CurrentID = FUI_SendMessage(FUI_SendMessage(LAnimSets, M_GETINDEX), M_GETDATA)
						AS.AnimSet = AnimList(ID)
						AS\Name$ = AnimList(CurrentID)\Name$ + " (copy)"
						For i = 0 To 149
							AS\AnimName$[i] = AnimList(CurrentID)\AnimName$[i]
							AS\AnimStart[i] = AnimList(CurrentID)\AnimStart[i]
							AS\AnimEnd[i] = AnimList(CurrentID)\AnimEnd[i]
						Next
						Item = FUI_ListBoxItem(LAnimSets, AS\Name$) : FUI_SendMessage(Item, M_SETDATA, ID)
						AnimsSaved = False
						AnimsChanged = True
					EndIf
				EndIf
			Case BAnimSetDelete
				If FUI_SendMessage(LAnimSets, M_GETSELECTED) <> 0
					ID = FUI_SendMessage(FUI_SendMessage(LAnimSets, M_GETINDEX), M_GETDATA)
					Delete AnimList(ID)
					FUI_SendMessage(LAnimSets, M_DELETEINDEX, FUI_SendMessage(LAnimSets, M_GETSELECTED))
					FUI_SendMessage(LAnimSets, M_SETINDEX, 1)
					FUI_CreateEvent(LAnimSets, 1)
					AnimsSaved = False
					AnimsChanged = True
				EndIf
			Case TAnimSetName
				If FUI_SendMessage(LAnimSets, M_GETSELECTED) <> 0
					ID = FUI_SendMessage(FUI_SendMessage(LAnimSets, M_GETINDEX), M_GETDATA)
					AnimList(ID)\Name$ = E\EventData
					FUI_SendMessage(FUI_SendMessage(LAnimSets, M_GETINDEX), M_SETTEXT, E\EventData)
					AnimsSaved = False
					AnimsChanged = True
				EndIf
			Case LAnims
				If FUI_SendMessage(LAnimSets, M_GETSELECTED) <> 0
					ID = FUI_SendMessage(FUI_SendMessage(LAnimSets, M_GETINDEX), M_GETDATA)
					AnimID = FUI_SendMessage(FUI_SendMessage(LAnims, M_GETINDEX), M_GETDATA)
					FUI_SendMessage(TAnimName, M_SETCAPTION, AnimList(ID)\AnimName$[AnimID])
					FUI_SendMessage(TAnimStart, M_SETCAPTION, AnimList(ID)\AnimStart[AnimID])
					FUI_SendMessage(TAnimEnd, M_SETCAPTION, AnimList(ID)\AnimEnd[AnimID])
					FUI_SendMessage(SAnimSpeed, M_SETVALUE, Int(AnimList(ID)\AnimSpeed#[AnimID] * 100.0))
				EndIf
			Case TAnimName
				If FUI_SendMessage(LAnimSets, M_GETSELECTED) <> 0 And FUI_SendMessage(LAnims, M_GETSELECTED) <> 0
					ID = FUI_SendMessage(FUI_SendMessage(LAnimSets, M_GETINDEX), M_GETDATA)
					AnimID = FUI_SendMessage(FUI_SendMessage(LAnims, M_GETINDEX), M_GETDATA)
					AnimList(ID)\AnimName$[AnimID] = E\EventData
					FUI_SendMessage(FUI_SendMessage(LAnims, M_GETINDEX), M_SETTEXT, E\EventData)
					AnimsSaved = False
				EndIf
			Case TAnimStart
				If FUI_SendMessage(LAnimSets, M_GETSELECTED) <> 0 And FUI_SendMessage(LAnims, M_GETSELECTED) <> 0
					ID = FUI_SendMessage(FUI_SendMessage(LAnimSets, M_GETINDEX), M_GETDATA)
					AnimID = FUI_SendMessage(FUI_SendMessage(LAnims, M_GETINDEX), M_GETDATA)
					If AnimList(ID)\AnimName$[AnimID] <> ""
						AnimList(ID)\AnimStart[AnimID] = E\EventData
						FUI_SendMessage(TAnimStart, M_SETCAPTION, AnimList(ID)\AnimStart[AnimID])
						AnimsSaved = False
					EndIf
				EndIf
			Case TAnimEnd
				If FUI_SendMessage(LAnimSets, M_GETSELECTED) <> 0 And FUI_SendMessage(LAnims, M_GETSELECTED) <> 0
					ID = FUI_SendMessage(FUI_SendMessage(LAnimSets, M_GETINDEX), M_GETDATA)
					AnimID = FUI_SendMessage(FUI_SendMessage(LAnims, M_GETINDEX), M_GETDATA)
					If AnimList(ID)\AnimName$[AnimID] <> ""
						AnimList(ID)\AnimEnd[AnimID] = E\EventData
						FUI_SendMessage(TAnimEnd, M_SETCAPTION, AnimList(ID)\AnimEnd[AnimID])
						AnimsSaved = False
					EndIf
				EndIf
			Case SAnimSpeed
				If FUI_SendMessage(LAnimSets, M_GETSELECTED) <> 0 And FUI_SendMessage(LAnims, M_GETSELECTED) <> 0
					ID = FUI_SendMessage(FUI_SendMessage(LAnimSets, M_GETINDEX), M_GETDATA)
					AnimID = FUI_SendMessage(FUI_SendMessage(LAnims, M_GETINDEX), M_GETDATA)
					If AnimList(ID)\AnimName$[AnimID] <> ""
						AnimList(ID)\AnimSpeed#[AnimID] = Float#(E\EventData) / 100.0
						AnimsSaved = False
					EndIf
				EndIf
			Case BAnimAdd
				If FUI_SendMessage(LAnimSets, M_GETSELECTED) <> 0
					ID = FUI_SendMessage(FUI_SendMessage(LAnimSets, M_GETINDEX), M_GETDATA)
					Found = False
					For i = 149 To 0 Step -1
						If AnimList(ID)\AnimName$[i] = ""
							AnimList(ID)\AnimName$[i] = "New anim"
							Item = FUI_ListBoxItem(LAnims, AnimList(ID)\AnimName$[i]) : FUI_SendMessage(Item, M_SETDATA, i)
							AnimsSaved = False
							Found = True
							Exit
						EndIf
					Next
					If Found = False Then FUI_CustomMessageBox("Limit of 150 animations reached!", "Error", MB_OK)
				EndIf
			Case BAnimDelete
				If FUI_SendMessage(LAnimSets, M_GETSELECTED) <> 0 And FUI_SendMessage(LAnims, M_GETSELECTED) <> 0
					ID = FUI_SendMessage(FUI_SendMessage(LAnimSets, M_GETINDEX), M_GETDATA)
					AnimID = FUI_SendMessage(FUI_SendMessage(LAnims, M_GETINDEX), M_GETDATA)
					If AnimList(ID)\AnimName$[AnimID] <> ""
						AnimList(ID)\AnimName$[AnimID] = ""
						AnimList(ID)\AnimStart[AnimID] = 0
						AnimList(ID)\AnimEnd[AnimID] = 0
						FUI_SendMessage(LAnims, M_DELETEINDEX, FUI_SendMessage(LAnims, M_GETSELECTED))
						FUI_SendMessage(LAnims, M_SETINDEX, 1)
						FUI_CreateEvent(LAnims, 1)
						AnimsSaved = False
					EndIf
				EndIf

			; Factions tab events ---------------------------------------------------------------------------------------------------

			; Save factions
			Case BFactionSave
				SaveFactions("Data\Server Data\Factions.dat")
				FactionsSaved = True
			; New faction
			Case BFactionAdd
				; Find free ID
				Found = False
				For i = 0 To 99
					If FactionNames$(i) = ""
						FactionNames$(i) = "New faction"
						FUI_SendMessage(LFactions, M_RESET)
						FUI_SendMessage(CFactionRating, M_RESET)
						Idx = 1
						For j = 0 To 99
							FactionDefaultRatings(i, j) = 100
							FactionDefaultRatings(j, i) = 100
							If FactionNames$(j) <> ""
								If j < i Then Idx = Idx + 1
								Item = FUI_ComboBoxItem(CFactionRating, FactionNames$(j)) : FUI_SendMessage(Item, M_SETDATA, j)
								Item = FUI_ListBoxItem(LFactions, FactionNames$(j)) : FUI_SendMessage(Item, M_SETDATA, j)
							EndIf
						Next
						FUI_SendMessage(LFactions, M_SETINDEX, Idx)
						FUI_SendMessage(CFactionRating, M_SETINDEX, 1)
						FUI_SendMessage(TFactionName, M_SETCAPTION, FactionNames$(i))
						FUI_SendMessage(SFactionRating, M_SETVALUE, 0)
						FUI_SendMessage(LFactionRatingInverse, M_SETCAPTION, "That faction's rating with this faction is 0%")
						FactionsChanged = True
						FactionsSaved = False
						Found = True
						Exit
					EndIf
				Next
				If Found = False Then FUI_CustomMessageBox("Limit of 50 factions reached!", "Error", MB_OK)
			; Delete faction
			Case BFactionDelete
				ID = FUI_SendMessage(FUI_SendMessage(LFactions, M_GETINDEX), M_GETDATA)
				FactionNames$(ID) = ""
				For i = 0 To 99
					FactionDefaultRatings(ID, i) = 0
					FactionDefaultRatings(i, ID) = 0
				Next
				Idx = FUI_SendMessage(LFactions, M_GETSELECTED)
				FUI_SendMessage(LFactions, M_DELETEINDEX, Idx)
				FUI_SendMessage(CFactionRating, M_DELETEINDEX, Idx)
				FUI_SendMessage(LFactions, M_SETINDEX, Idx)
				If FUI_SendMessage(LFactions, M_GETINDEX) = Idx Then FUI_SendMessage(CFactionRating, M_SETINDEX, 1)
				FUI_CreateEvent(LFactions)
				FactionsChanged = True
				FactionsSaved = False
			; Faction renamed
			Case TFactionName
				Idx = FUI_SendMessage(LFactions, M_GETSELECTED)
				If Idx > 0
					ID = FUI_SendMessage(FUI_SendMessage(LFactions, M_GETINDEX), M_GETDATA)
					FactionNames$(ID) = E\EventData
					FUI_SendMessage(FUI_SendMessage(LFactions, M_GETINDEX), M_SETTEXT, E\EventData)
					RatingIdx = FUI_SendMessage(CFactionRating, M_GETINDEX)
					FUI_SendMessage(CFactionRating, M_SETINDEX, Idx)
					FUI_SendMessage(FUI_SendMessage(CFactionRating, M_GETSELECTED), M_SETTEXT, E\EventData)
					FUI_SendMessage(CFactionRating, M_SETINDEX, RatingIdx)
					FactionsChanged = True
					FactionsSaved = False
				EndIf
			; Selected faction changed
			Case LFactions
				ID = FUI_SendMessage(FUI_SendMessage(LFactions, M_GETINDEX), M_GETDATA)
				WithID = FUI_SendMessage(FUI_SendMessage(CFactionRating, M_GETSELECTED), M_GETDATA)
				FUI_SendMessage(TFactionName, M_SETCAPTION, FactionNames$(ID))
				FUI_SendMessage(SFactionRating, M_SETVALUE, FactionDefaultRatings(ID, WithID) - 100)
				InvRating = FactionDefaultRatings(WithID, ID) - 100
				FUI_SendMessage(LFactionRatingInverse, M_SETCAPTION, "That faction's rating with this faction is " + Str$(InvRating) + "%")
			; Faction to adjust rating with changed
			Case CFactionRating
				ID = FUI_SendMessage(FUI_SendMessage(LFactions, M_GETINDEX), M_GETDATA)
				WithID = FUI_SendMessage(FUI_SendMessage(CFactionRating, M_GETSELECTED), M_GETDATA)
				FUI_SendMessage(SFactionRating, M_SETVALUE, FactionDefaultRatings(ID, WithID) - 100)
				InvRating = FactionDefaultRatings(WithID, ID) - 100
				FUI_SendMessage(LFactionRatingInverse, M_SETCAPTION, "That faction's rating with this faction is " + Str$(InvRating) + "%")
			; Rating changed
			Case SFactionRating
				Idx = FUI_SendMessage(LFactions, M_GETSELECTED)
				If Idx > 0
					ID = FUI_SendMessage(FUI_SendMessage(LFactions, M_GETINDEX), M_GETDATA)
					WithID = FUI_SendMessage(FUI_SendMessage(CFactionRating, M_GETSELECTED), M_GETDATA)
					FactionDefaultRatings(ID, WithID) = Int(E\EventData) + 100
					FactionsSaved = False
				EndIf

			; Spells tab events -----------------------------------------------------------------------------------------------------

			; Save spells
			Case BSpellSave
				SaveSpells("Data\Server Data\Spells.dat")
				SpellsSaved = True
			; New spell
			Case BSpellNew
				SelectedSpell = CreateSpell()
				TotalSpells = TotalSpells + 1
				Item = FUI_ComboBoxItem(CSpellSelected, "New ability")
				FUI_SendMessage(Item, M_SETDATA, SelectedSpell\ID)
				FUI_SendMessage(CSpellSelected, M_SETINDEX, TotalSpells)
				UpdateSpellDisplay()
				SpellsSaved = False
			; Delete spell
			Case BSpellDelete
				ID = FUI_SendMessage(FUI_SendMessage(CSpellSelected, M_GETSELECTED), M_GETDATA)
				If SpellsList(ID) <> Null
					Delete SpellsList(ID)
					TotalSpells = TotalSpells - 1
					FUI_SendMessage(CSpellSelected, M_DELETEINDEX, FUI_SendMessage(CSpellSelected, M_GETINDEX))
					FUI_SendMessage(CSpellSelected, M_SETINDEX, 1)
					UpdateSpellDisplay()
					; Workaround for FUI not removing the combobox text when all spells are deleted
					If TotalSpells = 0
						FUI_ComboBoxItem(CSpellSelected, "No abilities created...")
						FUI_SendMessage(CSpellSelected, M_SETINDEX, 1)
						FUI_SendMessage(CSpellSelected, M_DELETEINDEX, 1)
					EndIf
					SpellsSaved = False
				EndIf

			; Properties
			Case TSpellName
				If SelectedSpell <> Null
					FUI_SendMessage(FUI_SendMessage(CSpellSelected, M_GETSELECTED), M_SETTEXT, E\EventData)
					SelectedSpell\Name$ = E\EventData
					SpellsSaved = False
				EndIf
			Case TSpellDesc
				If SelectedSpell <> Null
					SelectedSpell\Description$ = E\EventData
					SpellsSaved = False
				EndIf
			Case BSpellImageID
				If SelectedSpell <> Null
					NewTex = ChooseTextureDialog()
					If NewTex > -1
						SelectedSpell\ThumbnailTexID = NewTex
						FUI_SendMessage(LSpellImageID, M_SETTEXT, "Display icon: " + GetFilename$(EditorTexName$(SelectedSpell\ThumbnailTexID)))
						SpellsSaved = False
					EndIf
				EndIf
			Case SSpellCharge
				If SelectedSpell <> Null
					SelectedSpell\RechargeTime = Int(E\EventData) * 1000
					SpellsSaved = False
				EndIf
			Case CSpellExclusiveRace
				If SelectedSpell <> Null
					If FUI_SendMessage(CSpellExclusiveRace, M_GETINDEX) = 1
						SelectedSpell\ExclusiveRace$ = ""
					Else
						SelectedSpell\ExclusiveRace$ = FUI_SendMessage(CSpellExclusiveRace, M_GETCAPTION)
					EndIf
					SpellsSaved = False
				EndIf
			Case CSpellExclusiveClass
				If SelectedSpell <> Null
					If FUI_SendMessage(CSpellExclusiveClass, M_GETINDEX) = 1
						SelectedSpell\ExclusiveClass$ = ""
					Else
						SelectedSpell\ExclusiveClass$ = FUI_SendMessage(CSpellExclusiveClass, M_GETCAPTION)
					EndIf
					SpellsSaved = False
				EndIf
			Case CSpellScript
				If SelectedSpell <> Null
					If FUI_SendMessage(CSpellScript, M_GETINDEX) = 1
						SelectedSpell\Script$ = ""
					Else
						SelectedSpell\Script$ = FUI_SendMessage(CSpellScript, M_GETCAPTION)
					EndIf
					Result = UpdateScriptMethodsList(CSpellMethod, SelectedSpell\Script$, SelectedSpell\SMethod$)
					If Result = False Then SelectedSpell\SMethod$ = FUI_SendMessage(CSpellMethod, M_GETCAPTION)
					SpellsSaved = False
				EndIf
			Case CSpellMethod
				If SelectedSpell <> Null Then SelectedSpell\SMethod$ = FUI_SendMessage(CSpellMethod, M_GETCAPTION) : SpellsSaved = False

			; Spells navigation
			Case CSpellSelected
				UpdateSpellDisplay()
			Case BSpellPrev
				Idx = FUI_SendMessageI(CSpellSelected, M_GETINDEX) - 1
				If Idx < 1 Then Idx = TotalSpells
				FUI_SendMessage(CSpellSelected, M_SETINDEX, Idx)
				UpdateSpellDisplay()
			Case BSpellNext
				Idx = FUI_SendMessageI(CSpellSelected, M_GETINDEX) + 1
				If Idx > TotalSpells Then Idx = 1
				FUI_SendMessage(CSpellSelected, M_SETINDEX, Idx)
				UpdateSpellDisplay()

			; Items tab events ------------------------------------------------------------------------------------------------------

			; Save items
			Case BItemSave
				SaveItems("Data\Server Data\Items.dat")
				ItemsSaved = True
			; New item
			Case BItemNew
				SelectedItem = CreateItem()
				TotalItems = TotalItems + 1
				SelectedItem\Name$ = "New item"
				Item = FUI_ComboBoxItem(CItemSelected, "New item")
				FUI_SendMessage(Item, M_SETDATA, SelectedItem\ID)
				FUI_SendMessage(CItemSelected, M_SETINDEX, TotalItems)
				UpdateItemDisplay()
				ItemsSaved = False
			; Delete item
			Case BItemDelete
				ID = FUI_SendMessage(FUI_SendMessage(CItemSelected, M_GETSELECTED), M_GETDATA)
				If ItemList(ID) <> Null
					Delete ItemList(ID)
					TotalItems = TotalItems - 1
					FUI_SendMessage(CItemSelected, M_DELETEINDEX, FUI_SendMessage(CItemSelected, M_GETINDEX))
					FUI_SendMessage(CItemSelected, M_SETINDEX, 1)
					UpdateItemDisplay()
					; Workaround for FUI not removing the combobox text when all items are deleted
					If TotalItems = 0
						FUI_ComboBoxItem(CItemSelected, "No items created...")
						FUI_SendMessage(CItemSelected, M_SETINDEX, 1)
						FUI_SendMessage(CItemSelected, M_DELETEINDEX, 1)
					EndIf
					ItemsSaved = False
				EndIf

			; General properties
			Case TItemName
				If SelectedItem <> Null
					Name$ = FilterChars$(E\EventData)
					FUI_SendMessage(FUI_SendMessage(CItemSelected, M_GETSELECTED), M_SETTEXT, Name$)
					SelectedItem\Name$ = Name$
					ItemsSaved = False
				EndIf
			Case CItemType
				If SelectedItem <> Null
					SelectedItem\ItemType = E\EventData
					UpdateItemDisplay()
					ItemsSaved = False
				EndIf
			Case CSlotType
				If SelectedItem <> Null
					If SelectedItem\ItemType = I_Armour
						SelectedItem\SlotType = Int(FUI_SendMessage(CSlotType, M_GETINDEX)) + 1
					ElseIf SelectedItem\ItemType = I_Ring
						SelectedItem\SlotType = Int(FUI_SendMessage(CSlotType, M_GETINDEX)) + 8
					EndIf
					ItemsSaved = False
				EndIf
			Case SItemValue
				If SelectedItem <> Null Then SelectedItem\Value = E\EventData : ItemsSaved = False
			Case SItemMass
				If SelectedItem <> Null Then SelectedItem\Mass = E\EventData : ItemsSaved = False

			; Specific properties
			Case SItemWeaponDamage
				If SelectedItem <> Null Then SelectedItem\WeaponDamage = E\EventData : ItemsSaved = False
			Case CItemWeaponType
				If SelectedItem <> Null Then SelectedItem\WeaponType = E\EventData : ItemsSaved = False
			Case CItemDamageType
				If SelectedItem <> Null
					SelectedItem\WeaponDamageType = FUI_SendMessage(FUI_SendMessage(CItemDamageType, M_GETSELECTED), M_GETDATA)
					ItemsSaved = False
				EndIf
			Case CItemRangedProjectile
				If SelectedItem <> Null
					SelectedItem\RangedProjectile = FUI_SendMessage(FUI_SendMessage(CItemRangedProjectile, M_GETSELECTED), M_GETDATA)
					ItemsSaved = False
				EndIf
			Case TItemRangedAnimation
				If SelectedItem <> Null Then SelectedItem\RangedAnimation$ = E\EventData : ItemsSaved = False
			Case SItemRange
				If SelectedItem <> Null Then SelectedItem\Range# = E\EventData : ItemsSaved = False
			Case SItemArmourLevel
				If SelectedItem <> Null Then SelectedItem\ArmourLevel = E\EventData : ItemsSaved = False
			Case SItemEatEffects
				If SelectedItem <> Null Then SelectedItem\EatEffectsLength = E\EventData : ItemsSaved = False
			Case BItemImageID
				If SelectedItem <> Null
					NewTex = ChooseTextureDialog()
					If NewTex > -1
						SelectedItem\ImageID = NewTex
						FUI_SendMessage(LItemImageID, M_SETTEXT, "Display image: " + GetFilename$(EditorTexName$(SelectedItem\ImageID)))
						ItemsSaved = False
					EndIf
				EndIf
			Case TItemMiscData
				If SelectedItem <> Null Then SelectedItem\MiscData$ = E\EventData : ItemsSaved = False

			; Appearance
			Case CItemGubbin
				If SelectedItem <> Null Then FUI_SendMessage(BItemGubbin, M_SETCHECKED, SelectedItem\Gubbins[FUI_SendMessageI(CItemGubbin, M_GETINDEX) - 1])
			Case BItemGubbin
				If SelectedItem <> Null Then SelectedItem\Gubbins[FUI_SendMessageI(CItemGubbin, M_GETINDEX) - 1] = E\EventData : ItemsSaved = False
			Case BItemThumb
				If SelectedItem <> Null
					NewTex = ChooseTextureDialog("Items")
					If NewTex > -1
						SelectedItem\ThumbnailTexID = NewTex
						FUI_SendMessage(LItemThumb, M_SETCAPTION, "Item thumbnail texture: " + GetFilename$(EditorTexName$(SelectedItem\ThumbnailTexID)))
						ItemsSaved = False
					EndIf
				EndIf
			Case BItemMeshM
				If SelectedItem <> Null
					NewMesh = ChooseMeshDialog(MeshDialog_All, "Items")
					If NewMesh > -1
						SelectedItem\MMeshID = NewMesh
						FUI_SendMessage(LItemMeshM, M_SETCAPTION, "Item mesh (male): " + GetFilename$(EditorMeshName$(SelectedItem\MMeshID)))
						ItemsSaved = False
					EndIf
				EndIf
			Case BItemMeshMN
				If SelectedItem <> Null
					SelectedItem\MMeshID = 65535
					FUI_SendMessage(LItemMeshM, M_SETCAPTION, "Item mesh (male): [NONE]")
					ItemsSaved = False
				EndIf
			Case BItemMeshF
				If SelectedItem <> Null
					NewMesh = ChooseMeshDialog(MeshDialog_All, "Items")
					If NewMesh > -1
						SelectedItem\FMeshID = NewMesh
						FUI_SendMessage(LItemMeshF, M_SETCAPTION, "Item mesh (female): " + GetFilename$(EditorMeshName$(SelectedItem\FMeshID)))
						ItemsSaved = False
					EndIf
				EndIf
			Case BItemMeshFN
				If SelectedItem <> Null
					SelectedItem\FMeshID = 65535
					FUI_SendMessage(LItemMeshF, M_SETCAPTION, "Item mesh (female): [NONE]")
					ItemsSaved = False
				EndIf

			; Attributes
			Case LItemAttributes
				If SelectedItem <> Null
					ID = FUI_SendMessage(FUI_SendMessage(LItemAttributes, M_GETINDEX), M_GETDATA)
					FUI_SendMessage(SItemAttribute, M_SETVALUE, SelectedItem\Attributes\Value[ID])
				EndIf
			Case SItemAttribute
				If SelectedItem <> Null
					ID = FUI_SendMessage(FUI_SendMessage(LItemAttributes, M_GETINDEX), M_GETDATA)
					SelectedItem\Attributes\Value[ID] = E\EventData
					ItemsSaved = False
				EndIf

			; Other properties
			Case BItemStackable
				If SelectedItem <> Null Then SelectedItem\Stackable = E\EventData : ItemsSaved = False
			Case BItemTakesDamage
				If SelectedItem <> Null Then SelectedItem\TakesDamage = E\EventData : ItemsSaved = False
			Case CItemExclusiveRace
				If SelectedItem <> Null
					If FUI_SendMessage(CItemExclusiveRace, M_GETINDEX) = 1
						SelectedItem\ExclusiveRace$ = ""
					Else
						SelectedItem\ExclusiveRace$ = FUI_SendMessage(CItemExclusiveRace, M_GETCAPTION)
					EndIf
					ItemsSaved = False
				EndIf
			Case CItemExclusiveClass
				If SelectedItem <> Null
					If FUI_SendMessage(CItemExclusiveClass, M_GETINDEX) = 1
						SelectedItem\ExclusiveClass$ = ""
					Else
						SelectedItem\ExclusiveClass$ = FUI_SendMessage(CItemExclusiveClass, M_GETCAPTION)
					EndIf
					ItemsSaved = False
				EndIf
			Case CItemScript
				If SelectedItem <> Null
					If FUI_SendMessage(CItemScript, M_GETINDEX) = 1
						SelectedItem\Script$ = ""
					Else
						SelectedItem\Script$ = FUI_SendMessage(CItemScript, M_GETCAPTION)
					EndIf
					Result = UpdateScriptMethodsList(CItemMethod, SelectedItem\Script$, SelectedItem\SMethod$)
					If Result = False Then SelectedItem\SMethod$ = FUI_SendMessage(CItemMethod, M_GETCAPTION)
					ItemsSaved = False
				EndIf
			Case CItemMethod
				If SelectedItem <> Null Then SelectedItem\SMethod$ = FUI_SendMessage(CItemMethod, M_GETCAPTION) : ItemsSaved = False

			; Item navigation
			Case CItemSelected
				UpdateItemDisplay()
			Case BItemPrev
				Idx = FUI_SendMessage(CItemSelected, M_GETINDEX)
				Idx = Idx - 1
				If Idx < 1 Then Idx = TotalItems
				FUI_SendMessage(CItemSelected, M_SETINDEX, Idx)
				If SelectedActor <> Null
					AttIdx = FUI_SendMessage(LItemAttributes, M_GETINDEX)
					ID = FUI_SendMessage(AttIdx, M_GETDATA)
					FUI_SendMessage(SItemAttribute, M_SETVALUE, SelectedItem\Attributes\Value[ID])
				EndIf
				UpdateItemDisplay()
			Case BItemNext
				Idx = FUI_SendMessage(CItemSelected, M_GETINDEX)
				Idx = Idx + 1
				If Idx > TotalItems Then Idx = 1
				FUI_SendMessage(CItemSelected, M_SETINDEX, Idx)
				If SelectedActor <> Null
					AttIdx = FUI_SendMessage(LItemAttributes, M_GETINDEX)
					ID = FUI_SendMessage(AttIdx, M_GETDATA)
					FUI_SendMessage(SItemAttribute, M_SETVALUE, SelectedItem\Attributes\Value[ID])
				EndIf
				UpdateItemDisplay()

			; Particles tab events --------------------------------------------------------------------------------------------------

			; New emitter
			Case BParticlesNew
				Name$ = EmitterNameDialog$()
				If Name$ <> ""
					Tex = LoadTexture("Data\DefaultParticle.bmp", 4 + 16 + 32)
					ID = RP_CreateEmitterConfig(200, 1, ParticlesCam, Tex, 1, 1, Name$)
					Item = FUI_ComboBoxItem(CParticleConfigs, Name$) : FUI_SendMessage(Item, M_SETDATA, ID)
					Idx = 0
					For EmC.RP_EmitterConfig = Each RP_EmitterConfig
						Idx = Idx + 1
					Next
					FUI_SendMessage(CParticleConfigs, M_SETINDEX, Idx)
					UpdateParticlesPreview()
					ParticlesChanged = True
					ParticlesSaved = False
				EndIf
			; Save emitters
			Case BParticlesSave
				For EmC.RP_EmitterConfig = Each RP_EmitterConfig
					RP_SaveEmitterConfig(Handle(EmC), "Data\Emitter Configs\" + EmC\Name$ + ".rpc")
				Next
				ParticlesSaved = True
			; Delete emitter
			Case BParticlesDelete
				If ParticlesConfig <> 0
					Result = FUI_CustomMessageBox("Really delete this emitter?", "Delete emitter", MB_YESNO)
					If Result = IDYES
						C.RP_EmitterConfig = Object.RP_EmitterConfig(ParticlesConfig)
						DeleteFile("Data\Emitter Configs\" + C\Name$ + ".rpc")
						RP_FreeEmitterConfig(ParticlesConfig, False)
						ParticlesConfig = 0
						FUI_SendMessage(CParticleConfigs, M_DELETEINDEX, FUI_SendMessage(CParticleConfigs, M_GETINDEX))
						FUI_SendMessage(CParticleConfigs, M_SETINDEX, 1)
						UpdateParticlesPreview()
						ParticlesChanged = True
						ParticlesSaved = False
					EndIf
				EndIf
			; Change selected emitter
			Case CParticleConfigs
				UpdateParticlesPreview()
			; Change preview texture
			Case BParticlesTex
				ID = ChooseTextureDialog("Particles")
				If ID > -1
					C.RP_EmitterConfig = Object.RP_EmitterConfig(ParticlesConfig)
					If C <> Null
						FreeTexture C\Texture
						Tex = GetTexture(ID, True)
						UnloadTexture(ID)
						If Tex = 0 Then Tex = LoadTexture("Data\DefaultParticle.bmp", 4 + 16 + 32)
						C\DefaultTextureID = ID
						C\Texture = Tex
						For Em.RP_Emitter = Each RP_Emitter
							If Em\Config = C Then EntityTexture Em\MeshEN, Tex
						Next
					EndIf
				EndIf
			; Reset preview positioning
			Case BParticlesPreviewReset
				ParticlesPreviewPitch# = 0.0
				ParticlesPreviewYaw# = 0.0
				ParticlesPreviewDistance# = 20.0
				UpdateParticlesPreviewCam()
			
			;Particle veiw colour
			Case SParticlesCamBGR
				CameraClsColor ParticlesCam, FUI_SendMessage(SParticlesCamBGR, M_GETVALUE), FUI_SendMessage(SParticlesCamBGG, M_GETVALUE), FUI_SendMessage(SParticlesCamBGB, M_GETVALUE)

 			Case SParticlesCamBGG 
				CameraClsColor ParticlesCam, FUI_SendMessage(SParticlesCamBGR, M_GETVALUE), FUI_SendMessage(SParticlesCamBGG, M_GETVALUE), FUI_SendMessage(SParticlesCamBGB, M_GETVALUE)

 			Case SParticlesCamBGB
				CameraClsColor ParticlesCam, FUI_SendMessage(SParticlesCamBGR, M_GETVALUE), FUI_SendMessage(SParticlesCamBGG, M_GETVALUE), FUI_SendMessage(SParticlesCamBGB, M_GETVALUE)	
			
			; Max particles
			Case SParticlesMax
				RP_ConfigMaxParticles(ParticlesConfig, E\EventData)
				ParticlesSaved = False
			; Spawn rate
			Case SParticlesRate
				RP_ConfigSpawnRate(ParticlesConfig, E\EventData)
				ParticlesSaved = False
			; Lifespan
			Case SParticlesLife
				RP_ConfigLifespan(ParticlesConfig, E\EventData)
				ParticlesSaved = False
			; Initial size
			Case SParticlesSize
				RP_ConfigInitialScale(ParticlesConfig, E\EventData)
				ParticlesSaved = False
			; Size change
			Case SParticlesSizeChange
				RP_ConfigScaleChange(ParticlesConfig, E\EventData)
				ParticlesSaved = False
			; Blend mode
			Case CParticlesBlend
				RP_ConfigBlendMode(ParticlesConfig, E\EventData)
				ParticlesSaved = False
			; Colours
			Case SParticlesStartR
				RP_ConfigInitialRed(ParticlesConfig, E\EventData) : ParticlesSaved = False
			Case SParticlesStartG
				RP_ConfigInitialGreen(ParticlesConfig, E\EventData) : ParticlesSaved = False
			Case SParticlesStartB
				RP_ConfigInitialBlue(ParticlesConfig, E\EventData) : ParticlesSaved = False
			Case SParticlesChangeR
				RP_ConfigRedChange(ParticlesConfig, E\EventData) : ParticlesSaved = False
			Case SParticlesChangeG
				RP_ConfigGreenChange(ParticlesConfig, E\EventData) : ParticlesSaved = False
			Case SParticlesChangeB
				RP_ConfigBlueChange(ParticlesConfig, E\EventData) : ParticlesSaved = False
			; Initial alpha
			Case SParticlesAlpha
				RP_ConfigInitialAlpha(ParticlesConfig, E\EventData)
				ParticlesSaved = False
			; Alpha change
			Case SParticlesAlphaChange
				RP_ConfigAlphaChange(ParticlesConfig, E\EventData)
				ParticlesSaved = False
			; Texture frames across
			Case SParticlesTexAcross
				If ParticlesConfig <> 0
					C.RP_EmitterConfig = Object.RP_EmitterConfig(ParticlesConfig)
					RP_ConfigTexture(ParticlesConfig, C\Texture, E\EventData, C\TexDown, False)
					ParticlesSaved = False
				EndIf
			; Texture frames down
			Case SParticlesTexDown
				If ParticlesConfig <> 0
					C.RP_EmitterConfig = Object.RP_EmitterConfig(ParticlesConfig)
					RP_ConfigTexture(ParticlesConfig, C\Texture, C\TexAcross, E\EventData, False)
					ParticlesSaved = False
				EndIf
			; Texture animation speed
			Case SParticlesTexSpeed
				If Int(E\EventData) > 0
					RP_ConfigTextureAnimSpeed(ParticlesConfig, 101 - Int(E\EventData))
				Else
					RP_ConfigTextureAnimSpeed(ParticlesConfig, 0)
				EndIf
				ParticlesSaved = False
			; Texture random start frame
			Case BParticlesRandFrame
				RP_ConfigTextureRandomStartFrame(ParticlesConfig, E\EventData)
				ParticlesSaved = False
			; Shape
			Case CParticlesShape
				If ParticlesConfig <> 0
					C.RP_EmitterConfig = Object.RP_EmitterConfig(ParticlesConfig)
					C\Shape = Int(E\EventData)
					UpdateParticlesPreview()
					ParticlesSaved = False
				EndIf
			; Axis
			Case CParticlesAxis
				If ParticlesConfig <> 0
					C.RP_EmitterConfig = Object.RP_EmitterConfig(ParticlesConfig)
					C\ShapeAxis = Int(E\EventData)
					ParticlesSaved = False
				EndIf
			; Inner radius
			Case SParticlesMinRadius
				If ParticlesConfig <> 0
					C.RP_EmitterConfig = Object.RP_EmitterConfig(ParticlesConfig)
					C\MinRadius# = E\EventData
					If C\MinRadius# > C\MaxRadius#
						C\MinRadius# = C\MaxRadius#
						FUI_SendMessage(SParticlesMinRadius, M_SETVALUE, C\MinRadius#)
					EndIf
					ParticlesSaved = False
				EndIf
			; Outer radius
			Case SParticlesMaxRadius
				If ParticlesConfig <> 0
					C.RP_EmitterConfig = Object.RP_EmitterConfig(ParticlesConfig)
					C\MaxRadius# = E\EventData
					ParticlesSaved = False
				EndIf
			; Width
			Case SParticlesWidth
				If ParticlesConfig <> 0
					C.RP_EmitterConfig = Object.RP_EmitterConfig(ParticlesConfig)
					C\Width# = E\EventData
					ParticlesSaved = False
				EndIf
			; Height
			Case SParticlesHeight
				If ParticlesConfig <> 0
					C.RP_EmitterConfig = Object.RP_EmitterConfig(ParticlesConfig)
					C\Height# = E\EventData
					ParticlesSaved = False
				EndIf
			; Depth
			Case SParticlesDepth
				If ParticlesConfig <> 0
					C.RP_EmitterConfig = Object.RP_EmitterConfig(ParticlesConfig)
					C\Depth# = E\EventData
					ParticlesSaved = False
				EndIf
			; Velocity shaping
			Case CParticlesVelocityShape
				RP_ConfigVelocityMode(ParticlesConfig, E\EventData) : ParticlesSaved = False
			; Velocities
			Case SParticlesVelocityX
				RP_ConfigVelocityX(ParticlesConfig, E\EventData) : ParticlesSaved = False
			Case SParticlesVelocityY
				RP_ConfigVelocityY(ParticlesConfig, E\EventData) : ParticlesSaved = False
			Case SParticlesVelocityZ
				RP_ConfigVelocityZ(ParticlesConfig, E\EventData) : ParticlesSaved = False
			; Random velocities
			Case SParticlesVelocityRX
				RP_ConfigVelocityRndX(ParticlesConfig, E\EventData) : ParticlesSaved = False
			Case SParticlesVelocityRY
				RP_ConfigVelocityRndY(ParticlesConfig, E\EventData) : ParticlesSaved = False
			Case SParticlesVelocityRZ
				RP_ConfigVelocityRndZ(ParticlesConfig, E\EventData) : ParticlesSaved = False
			; Force shaping
			Case CParticlesForceShape
				RP_ConfigForceModMode(ParticlesConfig, E\EventData) : ParticlesSaved = False
			; Forces
			Case SParticlesForceX
				RP_ConfigForceX(ParticlesConfig, E\EventData) : ParticlesSaved = False
			Case SParticlesForceY
				RP_ConfigForceY(ParticlesConfig, E\EventData) : ParticlesSaved = False
			Case SParticlesForceZ
				RP_ConfigForceZ(ParticlesConfig, E\EventData) : ParticlesSaved = False
			; Force modifiers
			Case SParticlesForceModX
				RP_ConfigForceModX(ParticlesConfig, E\EventData) : ParticlesSaved = False
			Case SParticlesForceModY
				RP_ConfigForceModY(ParticlesConfig, E\EventData) : ParticlesSaved = False
			Case SParticlesForceModZ
				RP_ConfigForceModZ(ParticlesConfig, E\EventData) : ParticlesSaved = False

			; Media tab events ------------------------------------------------------------------------------------------------------

			; Add a new file
			Case BMediaAdd
				MType = FUI_SendMessage(CMediaType, M_GETINDEX)
				; Meshes
				;Adding .obj models to import Cysis145
				If MType = 1
					FileTypes$ = "Blitz3D (*.b3d)|*.b3d|DirectX (*.x)|*.x|3DS (*.3ds)|*.3ds|Wavefront (*.obj)|*.obj|"
					Result = FUI_CustomOpenDialog("Choose file to add...", "Data\Meshes\", FileTypes$, False, True)
					If Result = True
						; Get extra options
						Flags = MeshDialog()
						If (Flags And 1) <> False Then IsAnim = True Else IsAnim = False
						IsEncrypted = False

						; Copy file/folder if required
						If Instr(App\CurrentFile$, CurrentDir$() + "Data\Meshes\") = 0 ;TODO: If RootDir is changed this may break
							Filename$ = App\CurrentFile$
							If MediaFolder$ <> "" Then Filename$ = MediaFolder$ + "\" + Filename$
							For i = Len(App\CurrentFile$) To 1 Step -1
								If Mid$(App\CurrentFile$, i, 1) = "\" Or Mid$(App\CurrentFile$, i, 1) = "/"
									Filename$ = Mid$(App\CurrentFile$, i + 1)
									If MediaFolder$ <> "" Then Filename$ = MediaFolder$ + "\" + Filename$
									Exit
								EndIf
							Next
							If FileType(App\CurrentFile$) = 1
								CopyFile(App\CurrentFile$, "Data\Meshes\" + Filename$)
							ElseIf FileType(App\CurrentFile$) = 2
								CopyTree(App\CurrentFile$, "Data\Meshes\" + Filename$)
							EndIf
						Else
							Filename$ = Right$(App\CurrentFile$, Len(App\CurrentFile$) - Len(CurrentDir$() + "Data\Meshes\")) ;TODO: If RootDir is changed this may break
						EndIf

						; Single file
						If FileType("Data\Meshes\" + Filename$) = 1
							AddMeshToManager(Filename$, IsAnim, IsEncrypted)
						; Folder
						ElseIf FileType("Data\Meshes\" + Filename$) = 2
							Result = FUI_CustomMessageBox("Also add subfolders to database?", "Add folder", MB_YESNO)
							If Result = IDYES Then SubFolders = True Else SubFolders = False
							FUI_CustomMessageBox("Adding an entire folder may take some time. Please be patient.", "Warning", MB_OK)
							AddMeshFolderToManager(Filename$, IsAnim, IsEncrypted, SubFolders)
						EndIf
						SetMediaType(1)
					EndIf
				; Textures
				ElseIf MType = 2
					FileTypes$ = "Bitmap (*.bmp)|*.bmp|JPEG (*.jpg)|*.jpg|PNG (*.png)|*.png|Targa (*.tga)|*.tga|Compressed Textures (*.dds)|"
					Result = FUI_CustomOpenDialog("Choose file to add...", "Data\Textures\", FileTypes$, False, True)
					If Result = True
						; Get extra options
						Flags = TextureDialog()

						; Copy file/folder if required
						If Instr(App\CurrentFile$, CurrentDir$() + "Data\Textures\") = 0 ;TODO: If RootDir is changed this may break
							Filename$ = App\CurrentFile$
							If MediaFolder$ <> "" Then Filename$ = MediaFolder$ + "\" + Filename$
							For i = Len(App\CurrentFile$) To 1 Step -1
								If Mid$(App\CurrentFile$, i, 1) = "\" Or Mid$(App\CurrentFile$, i, 1) = "/"
									Filename$ = Mid$(App\CurrentFile$, i + 1)
									If MediaFolder$ <> "" Then Filename$ = MediaFolder$ + "\" + Filename$
									Exit
								EndIf
							Next
							If FileType(App\CurrentFile$) = 1
								CopyFile(App\CurrentFile$, "Data\Textures\" + Filename$)
							ElseIf FileType(App\CurrentFile$) = 2
								CopyTree(App\CurrentFile$, "Data\Textures\" + Filename$)
							EndIf
						Else
							Filename$ = Right$(App\CurrentFile$, Len(App\CurrentFile$) - Len(CurrentDir$() + "Data\Textures\")) ;TODO: If RootDir is changed this may break
						EndIf

						; Single file
						If FileType("Data\Textures\" + Filename$) = 1
							ID = AddTextureToDatabase(Filename$, Flags)
							If ID > -1 Then TextureNames$(ID) = GetTextureName$(ID)
						; Folder
						ElseIf FileType("Data\Textures\" + Filename$) = 2
							Result = FUI_CustomMessageBox("Also add subfolders to database?", "Add folder", MB_YESNO)
							If Result = IDYES Then SubFolders = True Else SubFolders = False
							FUI_CustomMessageBox("Adding an entire folder may take some time. Please be patient.", "Warning", MB_OK)
							AddTextureFolderToManager(Filename$, Flags, SubFolders)
						EndIf
						SetMediaType(2)
					EndIf
				; Sounds
				ElseIf MType = 3
					FileTypes$ = "OGG (*.ogg)|*.ogg|"
					Result = FUI_CustomOpenDialog("Choose file to add...", "Data\Sounds\", FileTypes$, False, True)
					If Result = True
						; Get extra options
						Is3D = SoundDialog()

						; Copy file/folder if required
						If Instr(App\CurrentFile$, CurrentDir$() + "Data\Sounds\") = 0 ;TODO: If RootDir is changed this may break
							Filename$ = App\CurrentFile$
							If MediaFolder$ <> "" Then Filename$ = MediaFolder$ + "\" + Filename$
							For i = Len(App\CurrentFile$) To 1 Step -1
								If Mid$(App\CurrentFile$, i, 1) = "\" Or Mid$(App\CurrentFile$, i, 1) = "/"
									Filename$ = Mid$(App\CurrentFile$, i + 1)
									If MediaFolder$ <> "" Then Filename$ = MediaFolder$ + "\" + Filename$
									Exit
								EndIf
							Next
							If FileType(App\CurrentFile$) = 1
								CopyFile(App\CurrentFile$, "Data\Sounds\" + Filename$)
							ElseIf FileType(App\CurrentFile$) = 2
								CopyTree(App\CurrentFile$, "Data\Sounds\" + Filename$)
							EndIf
						Else
							Filename$ = Right$(App\CurrentFile$, Len(App\CurrentFile$) - Len(CurrentDir$() + "Data\Sounds\")) ;TODO: If RootDir is changed this may break
						EndIf

						; Single file
						If FileType("Data\Sounds\" + Filename$) = 1
							ID = AddSoundToDatabase(Filename$, Is3D)
							If ID > -1 Then SoundNames$(ID) = GetSoundName$(ID)
						; Folder
						ElseIf FileType("Data\Sounds\" + Filename$) = 2
							Result = FUI_CustomMessageBox("Also add subfolders to database?", "Add folder", MB_YESNO)
							If Result = IDYES Then SubFolders = True Else SubFolders = False
							FUI_CustomMessageBox("Adding an entire folder may take some time. Please be patient.", "Warning", MB_OK)
							AddSoundFolderToManager(Filename$, Is3D, SubFolders)
						EndIf
						SetMediaType(3)
					EndIf
				; Music
				ElseIf MType = 4
					FileTypes$ = "OGG (*.ogg)|*.ogg|"
					;FileTypes$ = FileTypes$ + "S3M (*.s3m)|*.s3m|XM (*.xm)|*.xm|IT (*.it)|*.it|"
					Result = FUI_CustomOpenDialog("Choose file to add...", "Data\Music\", FileTypes$, False, True)
					If Result = True

						; Copy file/folder if required
						If Instr(App\CurrentFile$, CurrentDir$() + "Data\Music\") = 0 ;TODO: If RootDir is changed this may break
							Filename$ = App\CurrentFile$
							If MediaFolder$ <> "" Then Filename$ = MediaFolder$ + "\" + Filename$
							For i = Len(App\CurrentFile$) To 1 Step -1
								If Mid$(App\CurrentFile$, i, 1) = "\" Or Mid$(App\CurrentFile$, i, 1) = "/"
									Filename$ = Mid$(App\CurrentFile$, i + 1)
									If MediaFolder$ <> "" Then Filename$ = MediaFolder$ + "\" + Filename$
									Exit
								EndIf
							Next
							If FileType(App\CurrentFile$) = 1
								CopyFile(App\CurrentFile$, "Data\Music\" + Filename$)
							ElseIf FileType(App\CurrentFile$) = 2
								CopyTree(App\CurrentFile$, "Data\Music\" + Filename$)
							EndIf
						Else
							Filename$ = Right$(App\CurrentFile$, Len(App\CurrentFile$) - Len(CurrentDir$() + "Data\Music\")) ;TODO: If RootDir is changed this may break
						EndIf

						; Single file
						If FileType("Data\Music\" + Filename$) = 1
							ID = AddMusicToDatabase(Filename$)
							If ID > -1 Then MusicNames$(ID) = GetMusicName$(ID)
						; Folder
						ElseIf FileType("Data\Music\" + Filename$) = 2
							Result = FUI_CustomMessageBox("Also add subfolders to database?", "Add folder", MB_YESNO)
							If Result = IDYES Then SubFolders = True Else SubFolders = False
							FUI_CustomMessageBox("Adding an entire folder may take some time. Please be patient.", "Warning", MB_OK)
							AddMusicFolderToManager(Filename$, SubFolders)
						EndIf
						SetMediaType(4)
					EndIf
				EndIf
			; Remove a file
			Case BMediaDelete
				Result = FUI_CustomMessageBox("Also delete file from disk?", "Remove Media", MB_YESNO Or MB_ICONQUESTION)
				MType = FUI_SendMessage(CMediaType, M_GETINDEX)
				ID = FUI_SendMessage(FUI_SendMessage(LMedia, M_GETINDEX), M_GETDATA)
				; Meshes
				If MType = 1
					Name$ = MeshNames$(ID)
					If Len(Name$) > 1 And Result = IDYES Then DeleteFile("Data\Meshes\" + Left$(Name$, Len(Name$) - 1))
					RemoveMeshFromDatabase(ID)
					MeshNames$(ID) = ""
				; Textures
				ElseIf MType = 2
					Name$ = TextureNames$(ID)
					If Len(Name$) > 1 And Result = IDYES Then DeleteFile("Data\Textures\" + Left$(Name$, Len(Name$) - 1))
					RemoveTextureFromDatabase(ID)
					TextureNames$(ID) = ""
				; Sounds
				ElseIf MType = 3
					Name$ = SoundNames$(ID)
					If Len(Name$) > 1 And Result = IDYES Then DeleteFile("Data\Sounds\" + Left$(Name$, Len(Name$) - 1))
					RemoveSoundFromDatabase(ID)
					SoundNames$(ID) = ""
				; Music
				ElseIf MType = 4
					If Result = IDYES Then DeleteFile("Data\Music\" + MusicNames$(ID))
					RemoveMusicFromDatabase(ID)
					MusicNames$(ID) = ""
				EndIf
				SetMediaType(MType)
			; Media type
			Case CMediaType
				MediaFolder$ = ""
				SetMediaType(FUI_SendMessage(CMediaType, M_GETINDEX))
			; Media folder
			Case LMediaFolder
				Name$ = FUI_SendMessage(LMediaFolder, M_GETTEXT)
				MediaFolder$ = FolderChangeHandler$(Name$, MediaFolder$)
				MType = FUI_SendMessage(CMediaType, M_GETINDEX)
				If MType = 1
					FillMeshesList(LMedia, MediaFolder$, MeshDialog_All)
					FillMeshesFolderList(LMediaFolder, MediaFolder$)
				ElseIf MType = 2
					FillTexturesList(LMedia, MediaFolder$)
					FillTexturesFolderList(LMediaFolder, MediaFolder$)
				ElseIf MType = 3
					FillSoundsList(LMedia, MediaFolder$, SoundDialog_All)
					FillSoundsFolderList(LMediaFolder, MediaFolder$)
				ElseIf MType = 4
					FillMusicList(LMedia, MediaFolder$)
					FillMusicFolderList(LMediaFolder, MediaFolder$)
				EndIf
				SetMediaPreview(1)
			; Media preview
			Case LMedia
				SetMediaPreview(E\EventData)
			; Mesh scale
			Case TMediaMeshScale
				MType = FUI_SendMessage(CMediaType, M_GETINDEX)
				ID = FUI_SendMessage(FUI_SendMessage(LMedia, M_GETINDEX), M_GETDATA)
				SetMeshScale(ID, Float#(E\EventData) / 100.0)
			; Play sound/music
			Case BMediaPreviewPlay
				MType = FUI_SendMessage(CMediaType, M_GETINDEX)
				If MType = 3
					S = GetSound(MediaPreviewSoundID)
					SoundVolume(S, FUI_SendMessage(SMediaPreviewVolume, M_GETVALUE))
					MediaPreviewChannel = PlaySound(S)
				ElseIf MType = 4
					Loaded = LoadSound("Data\Music\" + MusicNames$(MediaPreviewSoundID), False)
					LoopSound Loaded
					MediaPreviewChannel = PlaySound(Loaded)
					ChannelVolume(MediaPreviewChannel, FUI_SendMessage(SMediaPreviewVolume, M_GETVALUE))
				EndIf
			; Stop sound/music
			Case BMediaPreviewStop
				If MediaPreviewChannel <> 0
					StopChannel(MediaPreviewChannel) : MediaPreviewChannel = 0
					UnloadSound(MediaPreviewSoundID)
				EndIf
			; Volume
			Case SMediaPreviewVolume
				If MediaPreviewChannel <> 0
					If ChannelPlaying(MediaPreviewChannel) = True
						ChannelVolume(MediaPreviewChannel, FUI_SendMessage(SMediaPreviewVolume, M_GETVALUE))
					EndIf
				EndIf

			; Damage types ----------------------------------------------------------------------------------------------------------

			; Save damage types
			Case BDamageTypesSave
				F = WriteFile("Data\Server Data\Damage.dat")
					For i = 0 To 19
						WriteString F, DamageTypes$(i)
					Next
				CloseFile(F)
				DamageTypesSaved = True

			Default
				; Damage type names
				For i = 0 To 19
					If E\EventID = TDamageType(i)
						DamageTypes$(i) = E\EventData
						FUI_SendMessage(CItemDamageType, M_RESET)
						FUI_SendMessage(CProjDamageType, M_RESET)
						FUI_SendMessage(CWaterDamageType, M_RESET)
						For i = 0 To 19
							If DamageTypes$(i) <> ""
								Item = FUI_ComboBoxItem(CItemDamageType, DamageTypes$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
								Item = FUI_ComboBoxItem(CProjDamageType, DamageTypes$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
								Item = FUI_ComboBoxItem(CWaterDamageType, DamageTypes$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
							EndIf
						Next
						UpdateItemDisplay()
						DamageTypesSaved = False
						Exit
					EndIf
				Next
		End Select
		Delete E
	Next
	
	;Flip(0) added here after events cysis145
	RenderWorld
	Flip(0)

	; Move particles preview camera
	If MouseDown(1)
		If FUI_OverGadget(BParticlesPreviewIn)
			ParticlesPreviewDistance# = ParticlesPreviewDistance# - (2.5 * Delta#)
			If ParticlesPreviewDistance# < 3.0 Then ParticlesPreviewDistance# = 3.0
			UpdateParticlesPreviewCam()
		ElseIf FUI_OverGadget(BParticlesPreviewOut)
			ParticlesPreviewDistance# = ParticlesPreviewDistance# + (2.5 * Delta#)
			If ParticlesPreviewDistance# > 700.0 Then ParticlesPreviewDistance# = 700.0
			UpdateParticlesPreviewCam()
		ElseIf FUI_OverGadget(BParticlesPreviewR)
			ParticlesPreviewYaw# = ParticlesPreviewYaw# + (5.0 * Delta#)
			If ParticlesPreviewYaw# > 180.0 Then ParticlesPreviewYaw# = ParticlesPreviewYaw# - 360.0
			UpdateParticlesPreviewCam()
		ElseIf FUI_OverGadget(BParticlesPreviewL)
			ParticlesPreviewYaw# = ParticlesPreviewYaw# - (5.0 * Delta#)
			If ParticlesPreviewYaw# < -180.0 Then ParticlesPreviewYaw# = ParticlesPreviewYaw# + 360.0
			UpdateParticlesPreviewCam()
		ElseIf FUI_OverGadget(BParticlesPreviewD)
			ParticlesPreviewPitch# = ParticlesPreviewPitch# - (5.0 * Delta#)
			If ParticlesPreviewPitch# < -85.0 Then ParticlesPreviewPitch# = -85.0
			UpdateParticlesPreviewCam()
		ElseIf FUI_OverGadget(BParticlesPreviewU)
			ParticlesPreviewPitch# = ParticlesPreviewPitch# + (5.0 * Delta#)
			If ParticlesPreviewPitch# > 85.0 Then ParticlesPreviewPitch# = 85.0
			UpdateParticlesPreviewCam()
		EndIf
	EndIf

	; Actor preview
	If ActorPreview <> Null
		If FUI_OverGadget(VActorPreview)
			If MouseDown(1) And ActorPreview\CollisionEN <> 0
				TurnEntity ActorPreview\CollisionEN, Float#(MouseYSpeed()) * -Delta#, Float#(MouseXSpeed()) * Delta#, 0, True
			EndIf
		EndIf
	EndIf
	
	; Model preview [015]
	If MediaPreviewMesh <> 0
		If FUI_OverGadget(VMediaPreview)
			If MouseDown(1) And MediaPreviewMesh <> 0
				TurnEntity MediaPreviewMesh, Float#(MouseYSpeed()) * -Delta# * 2, Float#(MouseXSpeed()) * Delta# * 2, 0, True
			EndIf
		EndIf
	EndIf

	; Quit
	If KeyHit(1)
		;Changed Exiting protocol to make it run faster.
		; Saving window
		;Result = SaveDialog()

		; Quit
		;If Result = True
		;	FUI_Destroy()
		;	End
		;EndIf
		app\Quit = True

	EndIf
	;Added Short Cut for Save All Cysis145
	If FUI_ShortCut("Ctrl","S") = True
	menuSaveAll()
	EndIf

	; Delta timing bits
	DeltaTime = MilliSecs() - DeltaTime
	DeltaBuffer(DeltaBufferIndex) = DeltaTime
	DeltaBufferIndex = DeltaBufferIndex + 1
	If DeltaBufferIndex > 5 Then DeltaBufferIndex = 0

	; Take average of last 6 frames to get delta time coefficient
	Time# = 0.0
	For i = 0 To 5
		Time# = Time# + DeltaBuffer(i)
	Next
	Time# = Time# / 6.0
	FPS# = 1000.0 / Time#
	Delta# = BaseFramerate# / FPS#
	DeltaTime = MilliSecs()

	; Rotate media preview mesh [015]
	;If MediaPreviewMesh <> 0 Then TurnEntity MediaPreviewMesh, 0, 2.0 * Delta#, 0

	; Update active particles
	If ParticlesEmitter <> 0 Then FUI_SendMessage(LActiveParticles, M_SETTEXT, "Active particles: " + Str$(RP_EmitterActiveParticles(ParticlesEmitter)))

;Forever

;New Closing Protocol
Until app\Quit = True
	SaveDialog()
	FUI_Destroy()
End


; Functions -------------------------------------------------------------------------------------------------------------------------

; Gets a mesh name, strips the information byte, and replaces it with [NONE] if it's invalid
Function EditorMeshName$(ID)

	If ID < 0 Or ID > 65534 Then Return "[NONE]"
	Name$ = MeshNames$(ID)
	If Len(Name$) > 1 Then Return Left$(Name$, Len(Name$) - 1) Else Return "[NONE]" 

End Function

; Gets a texture name, strips the information byte, and replaces it with [NONE] if it's invalid
Function EditorTexName$(ID)

	If ID < 0 Or ID > 65534 Then Return "[NONE]"
	Name$ = TextureNames$(ID)
	If Len(Name$) > 1 Then Return Left$(Name$, Len(Name$) - 1) Else Return "[NONE]"

End Function

; Gets a sound name, strips the information byte, and replaces it with [NONE] if it's invalid
Function EditorSoundName$(ID)

	If ID < 0 Or ID > 65534 Then Return "[NONE]"
	Name$ = SoundNames$(ID)
	If Len(Name$) > 1 Then Return Left$(Name$, Len(Name$) - 1) Else Return "[NONE]"

End Function

; Gets a music name, and replaces it with [NONE] if it's invalid
Function EditorMusicName$(ID)

	If ID < 0 Or ID > 65534 Then Return "[NONE]"
	Name$ = MusicNames$(ID)
	If Len(Name$) > 1 Then Return Name$ Else Return "[NONE]"

End Function

; Interpolates a variable smoothly between two values
Function CurveValue#(Current#, Destination#, Curve#)

	Return Current# + ((Destination# - Current#) / Curve#)

End Function

; Animated mesh flag dialog
Function MeshDialog()

	W = FUI_Window(0, 0, 170, 95, "Mesh Options", "", 1, 1)
	BAnimated = FUI_CheckBox(W, 10, 5, "Mesh is animated")
	;BEncrypted = FUI_CheckBox(W, 10, 25, "Encrypt where possible")
	BDone = FUI_Button(W, 130, 45, 30, 20, "OK")

	FUI_ModalWindow(W)
	FUI_CenterWindow(W)

	; Event loop
	Closed = False
	Repeat
		FUI_Update()
		Flip(0)

		For E.Event = Each Event
			Select E\EventID
				; Window closed
				Case W
					If Lower$(E\EventData$) = "closed"
						IsAnim = FUI_SendMessage(BAnimated, M_GETCHECKED)
						IsEncrypted = False;FUI_SendMessage(BEncrypted, M_GETCHECKED)
						Result = IsAnim Or (IsEncrypted * 2)
						Closed = True
					EndIf
				; OK clicked
				Case BDone
					IsAnim = FUI_SendMessage(BAnimated, M_GETCHECKED)
					IsEncrypted = False;FUI_SendMessage(BEncrypted, M_GETCHECKED)
					Result = IsAnim Or (IsEncrypted * 2)
					Closed = True
			End Select
			Delete E
		Next
	Until Closed = True

	FUI_DeleteGadget(W)
	Return Result

End Function

; Texture flags dialog
Function TextureDialog()

	W = FUI_Window(0, 0, 120, 175, "Texture Options", "", 1, 1)
	BColour = FUI_CheckBox(W, 10, 5, "Colour", True)
	BAlpha = FUI_CheckBox(W, 10, 20, "Alpha")
	BMasked = FUI_CheckBox(W, 10, 35, "Masked")
	BMipmap = FUI_CheckBox(W, 10, 50, "Mipmapped", True)
	BClampU = FUI_CheckBox(W, 10, 65, "Clamp U")
	BClampV = FUI_CheckBox(W, 10, 80, "Clamp V")
	BSphere = FUI_CheckBox(W, 10, 95, "Sphere map")
	BCube = FUI_CheckBox(W, 10, 110, "Cube map")
	BDone = FUI_Button(W, 80, 125, 30, 20, "OK")

	FUI_ModalWindow(W)
	FUI_CenterWindow(W)

	; Event loop
	Closed = False
	Repeat
		FUI_Update()
		Flip(0)

		For E.Event = Each Event
			Select E\EventID
				; Window closed
				Case W
					If Lower$(E\EventData$) = "closed"
						Result = FUI_SendMessageI(BColour, M_GETCHECKED) + (FUI_SendMessageI(BAlpha, M_GETCHECKED) * 2)
						Result = Result + (FUI_SendMessageI(BMasked, M_GETCHECKED) * 4)
						Result = Result + (FUI_SendMessageI(BMipmap, M_GETCHECKED) * 8)
						Result = Result + (FUI_SendMessageI(BClampU, M_GETCHECKED) * 16)
						Result = Result + (FUI_SendMessageI(BClampV, M_GETCHECKED) * 32)
						Result = Result + (FUI_SendMessageI(BSphere, M_GETCHECKED) * 64)
						Result = Result + (FUI_SendMessageI(BCube, M_GETCHECKED) * 128)
						Closed = True
					EndIf
				; OK clicked
				Case BDone
					Result = FUI_SendMessageI(BColour, M_GETCHECKED) + (FUI_SendMessageI(BAlpha, M_GETCHECKED) * 2)
					Result = Result + (FUI_SendMessageI(BMasked, M_GETCHECKED) * 4) + (FUI_SendMessageI(BMipmap, M_GETCHECKED) * 8)
					Result = Result + (FUI_SendMessageI(BClampU, M_GETCHECKED) * 16) + (FUI_SendMessageI(BClampV, M_GETCHECKED) * 32)
					Result = Result + (FUI_SendMessageI(BSphere, M_GETCHECKED) * 64) + (FUI_SendMessageI(BCube, M_GETCHECKED) * 128)
					Closed = True
			End Select
			Delete E
		Next
	Until Closed = True

	FUI_DeleteGadget(W)
	Return Result

End Function

; 3D sound flag dialog
Function SoundDialog()

	W = FUI_Window(0, 0, 120, 75, "Sound Options", "", 1, 1)
	B3D = FUI_CheckBox(W, 10, 5, "Sound is 3D")
	BDone = FUI_Button(W, 80, 25, 30, 20, "OK")

	FUI_ModalWindow(W)
	FUI_CenterWindow(W)

	; Event loop
	Closed = False
	Repeat
		FUI_Update()
		Flip(0)

		For E.Event = Each Event
			Select E\EventID
				; Window closed
				Case W
					If Lower$(E\EventData$) = "closed"
						Result = FUI_SendMessage(B3D, M_GETCHECKED)
						Closed = True
					EndIf
				; OK clicked
				Case BDone
					Result = FUI_SendMessage(B3D, M_GETCHECKED)
					Closed = True
			End Select
			Delete E
		Next
	Until Closed = True

	FUI_DeleteGadget(W)
	Return Result

End Function

; Scales a mesh entity so the biggest dimension is Size# units
Function FitEntity(EN, Size#)

	If MeshWidth#(EN) > MeshHeight#(EN) Then Biggest# = MeshWidth#(EN) Else Biggest# = MeshHeight#(EN)
	If MeshDepth#(EN) > Biggest# Then Biggest# = MeshDepth#(EN)
	Scale# = Size# / Biggest#
	ScaleEntity EN, Scale#, Scale#, Scale#

End Function

; Creates a quad mesh
Function CreateQuad(P = 0)

	EN = CreateMesh()
	s = CreateSurface(EN)
	v1 = AddVertex(s, 0.0, -1.0, 0.0, 0.0, 1.0)
	v2 = AddVertex(s, 1.0, -1.0, 0.0, 1.0, 1.0)
	v3 = AddVertex(s, 1.0,  0.0, 0.0, 1.0, 0.0)
	v4 = AddVertex(s, 0.0,  0.0, 0.0, 0.0, 0.0)
	AddTriangle s, v3, v2, v1
	AddTriangle s, v4, v3, v1
	EntityParent(EN, P)
	EntityFX(EN, 1 + 8)
	Return EN

End Function

; Sets the media type to meshes, textures, sounds or music
Function SetMediaType(Num = -1)

	; Use current
	If Num = -1 Then Num = FUI_SendMessage(CMediaType, M_GETINDEX)

	; Set the media type combo box
	FUI_SendMessage(CMediaType, M_SETINDEX, Num)

	; Fill listbox
	If Num = 1
		FillMeshesList(LMedia, MediaFolder$, MeshDialog_All)
		FillMeshesFolderList(LMediaFolder, MediaFolder$)
	ElseIf Num = 2
		FillTexturesList(LMedia, MediaFolder$)
		FillTexturesFolderList(LMediaFolder, MediaFolder$)
	ElseIf Num = 3
		FillSoundsList(LMedia, MediaFolder$, SoundDialog_All)
		FillSoundsFolderList(LMediaFolder, MediaFolder$)
	ElseIf Num = 4
		FillMusicList(LMedia, MediaFolder$)
		FillMusicFolderList(LMediaFolder, MediaFolder$)
	EndIf

	; Set the preview to the first entry, if it exists
	SetMediaPreview(1)

End Function

; Sets the preview to a given entry in the list
Function SetMediaPreview(Num)

	; Clear preview
	HideEntity MediaPreviewQuad
	If MediaPreviewMesh <> 0 Then FreeEntity MediaPreviewMesh : MediaPreviewMesh = 0
	FUI_HideGadget(VMediaPreview)
	FUI_HideGadget(BMediaPreviewPlay)
	FUI_HideGadget(BMediaPreviewStop)
	FUI_HideGadget(SMediaPreviewVolume)
	FUI_HideGadget(LMediaPreviewVolume)
	If TMediaMeshScale <> 0
		FUI_DeleteGadget(TMediaMeshScale) : TMediaMeshScale = 0
		FUI_DeleteGadget(LMediaMeshScale) : LMediaMeshScale = 0
	EndIf
	If MediaPreviewChannel <> 0
		StopChannel(MediaPreviewChannel) : MediaPreviewChannel = 0
		UnloadSound(MediaPreviewSoundID)
	EndIf

	; Get the media type
	MType = FUI_SendMessage(CMediaType, M_GETINDEX)

	; Set the selection in the list
	FUI_SendMessage(LMedia, M_SETINDEX, 0) ; Workaround for an obscure FUI bug which allows multiple listbox selections
	Result = FUI_SendMessage(LMedia, M_SETINDEX, Num)

	; If entry doesn't exist, return
	If Result = False
		FUI_SendMessage(LMediaPreview, M_SETCAPTION, "Media ID: Nothing selected")
		Return
	EndIf

	; Get the media ID
	ID = FUI_SendMessage(FUI_SendMessage(LMedia, M_GETINDEX), M_GETDATA)

	; Display preview
	Select MType
		; Meshes
		Case 1
			MediaPreviewMesh = GetMesh(ID)
			If MediaPreviewMesh <> 0
				FUI_ShowGadget(VMediaPreview)
				UnloadMesh(ID)
				SizeEntity(MediaPreviewMesh, 50.0, 50.0, 500.0, True)
				PositionEntity MediaPreviewMesh, 0, -20, 70
				LMediaMeshScale = FUI_Label(TMedia, 300, 622, "Initial scale:")
				TMediaMeshScale = FUI_Spinner(TMedia, 365, 620, 110, 20, 1, 5000, Int(LoadedMeshScales#(ID) * 100.0), 1, DTYPE_INTEGER, "%")
			EndIf
		; Textures
		Case 2
			Tex = GetTexture(ID)
			If Tex <> 0
				FUI_ShowGadget(VMediaPreview)
				ShowEntity MediaPreviewQuad
				EntityTexture MediaPreviewQuad, Tex
				UnloadTexture(ID)
			EndIf
		; Sounds
		Case 3, 4
			FUI_ShowGadget(BMediaPreviewPlay)
			FUI_ShowGadget(BMediaPreviewStop)
			FUI_ShowGadget(SMediaPreviewVolume)
			FUI_ShowGadget(LMediaPreviewVolume)
			MediaPreviewSoundID = ID
	End Select

	; Set ID label
	FUI_SendMessage(LMediaPreview, M_SETCAPTION, "Media ID: " + ID)

End Function

; Update interface components list
Function UpdateInterfaceComponentsList() ;[255]

	FUI_SendMessage(LInterfaceComponents, M_RESET)
	
	;Main UI
	If FUI_SendMessage(RInterfaceMain, M_GETCHECKED) ;[255]
		Item = FUI_ListBoxItem(LInterfaceComponents, "Chat text area") : FUI_SendMessage(Item, M_SETDATA, Handle(Chat))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Chat entry box") : FUI_SendMessage(Item, M_SETDATA, Handle(ChatEntry))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Radar map") : FUI_SendMessage(Item, M_SETDATA, Handle(Radar))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Compass") : FUI_SendMessage(Item, M_SETDATA, Handle(Compass))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Buff icons area") : FUI_SendMessage(Item, M_SETDATA, Handle(BuffsArea))
		For i = 0 To 39
			If AttributeNames$(i) <> ""
				Item = FUI_ListBoxItem(LInterfaceComponents, AttributeNames$(i) + " bar") : FUI_SendMessage(Item, M_SETDATA, i)
			Else
				AttributeDisplays(i)\Width# = 0.0
				AttributeDisplays(i)\Height# = 0.0
			EndIf
		Next

		; Hide inventory components
		For IC.InterfaceComponent = Each InterfaceComponent
			ShowEntity(IC\Component)
		Next
		HideEntity(InventoryWindow\Component)
		HideEntity(InventoryDrop\Component)
		HideEntity(InventoryEat\Component)
		HideEntity(InventoryGold\Component)
		For i = 0 To Slots_Inventory
			HideEntity(InventoryButtons(i)\Component)
		Next
		; Update display
		UpdateInterfaceComponent(Chat)
		
	;	;Hide Login Screen
	;	HideEntity LoginWindow\Component
	;	HideEntity LoginUsername\Component
	;	HideEntity LoginPassword\Component
	;	HideEntity LoginNewAccount\Component
	;	HideEntity LoginLogIn\Component
	;	HideEntity LoginGraphic\Component
	;	HideEntity LoginControl\Component
	;	HideEntity LoginSound\Component
	;	HideEntity LoginServerStat\Component
		
	;Inventory interface
	Else			
		;FUI_SendMessage(RInterfaceInventory, M_SETCHECKED, True)

		Item = FUI_ListBoxItem(LInterfaceComponents, "Window") : FUI_SendMessage(Item, M_SETDATA, Handle(InventoryWindow))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Drop button") : FUI_SendMessage(Item, M_SETDATA, Handle(InventoryDrop))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Use button") : FUI_SendMessage(Item, M_SETDATA, Handle(InventoryEat))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Money display") : FUI_SendMessage(Item, M_SETDATA, Handle(InventoryGold))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Weapon slot") : FUI_SendMessage(Item, M_SETDATA, Handle(InventoryButtons(0)))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Shield slot") : FUI_SendMessage(Item, M_SETDATA, Handle(InventoryButtons(1)))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Head slot") : FUI_SendMessage(Item, M_SETDATA, Handle(InventoryButtons(2)))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Chest slot") : FUI_SendMessage(Item, M_SETDATA, Handle(InventoryButtons(3)))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Hands slot") : FUI_SendMessage(Item, M_SETDATA, Handle(InventoryButtons(4)))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Belt slot") : FUI_SendMessage(Item, M_SETDATA, Handle(InventoryButtons(5)))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Legs slot") : FUI_SendMessage(Item, M_SETDATA, Handle(InventoryButtons(6)))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Feet slot") : FUI_SendMessage(Item, M_SETDATA, Handle(InventoryButtons(7)))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Ring slot 1") : FUI_SendMessage(Item, M_SETDATA, Handle(InventoryButtons(8)))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Ring slot 2") : FUI_SendMessage(Item, M_SETDATA, Handle(InventoryButtons(9)))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Ring slot 3") : FUI_SendMessage(Item, M_SETDATA, Handle(InventoryButtons(10)))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Ring slot 4") : FUI_SendMessage(Item, M_SETDATA, Handle(InventoryButtons(11)))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Amulet slot 1") : FUI_SendMessage(Item, M_SETDATA, Handle(InventoryButtons(12)))
		Item = FUI_ListBoxItem(LInterfaceComponents, "Amulet slot 2") : FUI_SendMessage(Item, M_SETDATA, Handle(InventoryButtons(13)))
		For i = 14 To Slots_Inventory
			Item = FUI_ListBoxItem(LInterfaceComponents, "Backpack slot " + (i - 13)) : FUI_SendMessage(Item, M_SETDATA, Handle(InventoryButtons(i)))
		Next
		
		
		; Hide main screen components
		For IC.InterfaceComponent = Each InterfaceComponent
			ShowEntity(IC\Component)
		Next
		HideEntity(Chat\Component)
		HideEntity(ChatEntry\Component)
		HideEntity(Radar\Component)
		HideEntity(Compass\Component)
		HideEntity(BuffsArea\Component)
		For i = 0 To 39
			HideEntity(AttributeDisplays(i)\Component)
		Next
		; Update display
		UpdateInterfaceComponent(InventoryWindow)
		
;		;Hide Login Screen
;		HideEntity LoginWindow\Component
;		HideEntity LoginUsername\Component
;		HideEntity LoginPassword\Component
;		HideEntity LoginNewAccount\Component
;		HideEntity LoginLogIn\Component
;		HideEntity LoginGraphic\Component
;		HideEntity LoginControl\Component
;		HideEntity LoginSound\Component
;		HideEntity LoginServerStat\Component
		
	;Login screen
;	ElseIf FUI_SendMessage(RInterfaceLogin, M_SETCHECKED)
;		
;		;FUI_SendMessage(RInterfaceInventory, M_SETCHECKED, False)
;		;FUI_SendMessage(RInterfaceLogin, M_SETCHECKED, True)
;	
;		Item = FUI_ListBoxItem(LInterfaceComponents, "Window") : FUI_SendMessage(Item, M_SETDATA, Handle(LoginWindow))
;		Item = FUI_ListBoxItem(LInterfaceComponents, "Username Field") : FUI_SendMessage(Item, M_SETDATA, Handle(LoginUsername))
;		Item = FUI_ListBoxItem(LInterfaceComponents, "Password Field") : FUI_SendMessage(Item, M_SETDATA, Handle(LoginPassword))
;		Item = FUI_ListBoxItem(LInterfaceComponents, "New Account Button") : FUI_SendMessage(Item, M_SETDATA, Handle(LoginNewAccount))
;		Item = FUI_ListBoxItem(LInterfaceComponents, "Log-In Button") : FUI_SendMessage(Item, M_SETDATA, Handle(LoginLogIn))
;		Item = FUI_ListBoxItem(LInterfaceComponents, "Graphic Options Button") : FUI_SendMessage(Item, M_SETDATA, Handle(LoginGraphic))
;		Item = FUI_ListBoxItem(LInterfaceComponents, "Control Options Button") : FUI_SendMessage(Item, M_SETDATA, Handle(LoginControl))
;		Item = FUI_ListBoxItem(LInterfaceComponents, "Sound Options Button") : FUI_SendMessage(Item, M_SETDATA, Handle(LoginSound))
;		Item = FUI_ListBoxItem(LInterfaceComponents, "Close Button") : FUI_SendMessage(Item, M_SETDATA, Handle(LoginClose))
;		Item = FUI_ListBoxItem(LInterfaceComponents, "Server Status") : FUI_SendMessage(Item, M_SETDATA, Handle(LoginServerStat))
;		
;		; Hide main screen components
;;		For IC.InterfaceComponent = Each InterfaceComponent
;			ShowEntity(IC\Component)
;		Next
;		HideEntity(Chat\Component)
;		HideEntity(ChatEntry\Component)
;		HideEntity(Radar\Component)
;		HideEntity(Compass\Component)
;		HideEntity(BuffsArea\Component)
;		For i = 0 To 39
;			HideEntity(AttributeDisplays(i)\Component)
;		Next
;		
;		; Hide inventory components
;		For IC.InterfaceComponent = Each InterfaceComponent
;			ShowEntity(IC\Component)
;		Next
;		HideEntity(InventoryWindow\Component)
;		HideEntity(InventoryDrop\Component)
;		HideEntity(InventoryEat\Component)
;		HideEntity(InventoryGold\Component)
;		For i = 0 To Slots_Inventory
;			HideEntity(InventoryButtons(i)\Component)
;		Next

				
		; Update display
		;UpdateInterfaceComponent(LoginWindow)

	EndIf
	
End Function

; Selects and updates an interface component
Function UpdateInterfaceComponent(IC.InterfaceComponent, PositionOnly = False)

	; Select in list
	If PositionOnly = False
		Select Handle(IC)
			Case Handle(Chat)
				FUI_SendMessage(LInterfaceComponents, M_SETINDEX, 1)
			Case Handle(ChatEntry)
				FUI_SendMessage(LInterfaceComponents, M_SETINDEX, 2)
			Default
				For i = 0 To 39
					If IC = AttributeDisplays(i)
						Idx = 5
						For j = 0 To i
							If AttributeNames$(j) <> "" Then Idx = Idx + 1
						Next
						FUI_SendMessage(LInterfaceComponents, M_SETINDEX, Idx)
						Exit
					EndIf
				Next
		End Select

		; Set up adjustment gadgets
		FUI_SendMessage(SInterfaceX, M_SETVALUE, IC\X# * 100.0)
		FUI_SendMessage(SInterfaceY, M_SETVALUE, IC\Y# * 100.0)
		FUI_SendMessage(SInterfaceWidth, M_SETVALUE, IC\Width# * 100.0)
		FUI_SendMessage(SInterfaceHeight, M_SETVALUE, IC\Height# * 100.0)
		FUI_SendMessage(SInterfaceR, M_SETVALUE, IC\R)
		FUI_SendMessage(SInterfaceG, M_SETVALUE, IC\G)
		FUI_SendMessage(SInterfaceB, M_SETVALUE, IC\B)
		FUI_SendMessage(SInterfaceA, M_SETVALUE, IC\Alpha# * 255.0)

		; Deselect old 3D object
		EntityColor(SelectedIC\Component, 255, 255, 255)

		; Select new 3D object
		EntityColor(IC\Component, 255, 50, 50)
		EntityAlpha(IC\Component, IC\Alpha#)
	EndIf

	; Update new 3D object position/scale
	If FUI_SendMessage(RInterfaceMain, M_GETCHECKED) Or IC = InventoryWindow ;[255]
		PositionEntity IC\Component, (IC\X# * 20.0) - 10.0, (IC\Y# * -15.0) + 7.5, 10.0
		ScaleEntity IC\Component, IC\Width# * 20.0, IC\Height# * 15.0, 1.0
		
	ElseIf FUI_SendMessage(RInterfaceInventory, M_GETCHECKED)
		X# = InventoryWindow\X# + (IC\X# * InventoryWindow\Width#)
		Y# = InventoryWindow\Y# + (IC\Y# * InventoryWindow\Height#)
		Width# = IC\Width# * InventoryWindow\Width#
		Height# = IC\Height# * InventoryWindow\Height#
		PositionEntity IC\Component, (X# * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0
		ScaleEntity IC\Component, Width# * 20.0, Height# * 15.0, 1.0
	EndIf

	; If the inventory window was updated, update all its children
	If IC = InventoryWindow
		UpdateInterfaceComponent(InventoryDrop, True)
		UpdateInterfaceComponent(InventoryEat, True)
		UpdateInterfaceComponent(InventoryGold, True)
		For i = 0 To Slots_Inventory
			UpdateInterfaceComponent(InventoryButtons(i), True)
		Next
	EndIf

	; Done
	SelectedIC = IC

End Function

; Updates list with all methods for a given script
Function UpdateScriptMethodsList(List, Script$, SMethod$)

	FUI_SendMessage(List, M_RESET)
	FUI_DisableGadget(List)

	If Script$ = "" Then Return

	Found = False
	F = ReadFile("Data\Server Data\Scripts\" + Script$ + ".rsl")
	If F <> 0
		FUI_EnableGadget(List)
		Num = 0
		While Eof(F) = False
			NewLine$ = ReadLine$(F)
			Pos = Instr(Upper$(NewLine$), "FUNCTION")
			If Pos > 0
				EndPos = Instr(Upper$(NewLine$), "(", Pos)
				If EndPos > 0
					NewLine$ = Left$(NewLine$, EndPos - 1)
					NewLine$ = Right$(NewLine$, Len(NewLine$) - 9)
					FUI_ComboBoxItem(List, NewLine$)
					Num = Num + 1
					If Upper$(NewLine$) = Upper$(SMethod$)
						FUI_SendMessage(List, M_SETINDEX, Num)
						Found = True
					EndIf
				EndIf
			EndIf
		Wend
		CloseFile F
		If Found = False Then FUI_SendMessage(List, M_SETINDEX, 1)
	EndIf


	Return Found

End Function

; Sets the particles preview
Function UpdateParticlesPreview()

	If ParticlesEmitter <> 0 Then RP_FreeEmitter(ParticlesEmitter, False) : ParticlesEmitter = 0

	ParticlesConfig = FUI_SendMessage(FUI_SendMessage(CParticleConfigs, M_GETSELECTED), M_GETDATA)
	If ParticlesConfig <> 0
		; Preview
		ParticlesEmitter = RP_CreateEmitter(ParticlesConfig)
		PositionEntity ParticlesEmitter, 0, 0, 50

		; Gadget values
		C.RP_EmitterConfig = Object.RP_EmitterConfig(ParticlesConfig)
		FUI_SendMessage(SParticlesMax, M_SETVALUE, C\MaxParticles)
		FUI_SendMessage(SParticlesRate, M_SETVALUE, C\ParticlesPerFrame)
		FUI_SendMessage(SParticlesLife, M_SETVALUE, C\Lifespan)
		FUI_SendMessage(SParticlesSize, M_SETVALUE, C\ScaleStart#)
		FUI_SendMessage(SParticlesSizeChange, M_SETVALUE, C\ScaleChange#)
		FUI_SendMessage(CParticlesBlend, M_SETINDEX, C\BlendMode)
		FUI_SendMessage(SParticlesStartR, M_SETVALUE, C\RStart)
		FUI_SendMessage(SParticlesStartG, M_SETVALUE, C\GStart)
		FUI_SendMessage(SParticlesStartB, M_SETVALUE, C\BStart)
		FUI_SendMessage(SParticlesChangeR, M_SETVALUE, C\RChange#)
		FUI_SendMessage(SParticlesChangeG, M_SETVALUE, C\GChange#)
		FUI_SendMessage(SParticlesChangeB, M_SETVALUE, C\BChange#)
		FUI_SendMessage(SParticlesAlpha, M_SETVALUE, C\AlphaStart#)
		FUI_SendMessage(SParticlesAlphaChange, M_SETVALUE, C\AlphaChange#)
		FUI_SendMessage(SParticlesTexAcross, M_SETVALUE, C\TexAcross)
		FUI_SendMessage(SParticlesTexDown, M_SETVALUE, C\TexDown)
		If C\TexAnimSpeed > 0
			FUI_SendMessage(SParticlesTexSpeed, M_SETVALUE, 101 - C\TexAnimSpeed)
		Else
			FUI_SendMessage(SParticlesTexSpeed, M_SETVALUE, 0)
		EndIf
		FUI_SendMessage(BParticlesRandFrame, M_SETCHECKED, C\RndStartFrame)
		FUI_SendMessage(CParticlesShape, M_SETINDEX, C\Shape)
		FUI_SendMessage(CParticlesAxis, M_SETINDEX, C\ShapeAxis)
		FUI_SendMessage(SParticlesMinRadius, M_SETVALUE, C\MinRadius#)
		FUI_SendMessage(SParticlesMaxRadius, M_SETVALUE, C\MaxRadius#)
		FUI_SendMessage(SParticlesWidth, M_SETVALUE, C\Width#)
		FUI_SendMessage(SParticlesHeight, M_SETVALUE, C\Height#)
		FUI_SendMessage(SParticlesDepth, M_SETVALUE, C\Depth#)
		FUI_SendMessage(CParticlesVelocityShape, M_SETINDEX, C\VShapeBased)
		FUI_SendMessage(CParticlesForceShape, M_SETINDEX, C\ForceShaping)
		FUI_SendMessage(SParticlesVelocityX, M_SETVALUE, C\VelocityX#)
		FUI_SendMessage(SParticlesVelocityY, M_SETVALUE, C\VelocityY#)
		FUI_SendMessage(SParticlesVelocityZ, M_SETVALUE, C\VelocityZ#)
		FUI_SendMessage(SParticlesVelocityRX, M_SETVALUE, C\VelocityRndX#)
		FUI_SendMessage(SParticlesVelocityRY, M_SETVALUE, C\VelocityRndY#)
		FUI_SendMessage(SParticlesVelocityRZ, M_SETVALUE, C\VelocityRndZ#)
		FUI_SendMessage(SParticlesForceX, M_SETVALUE, C\ForceX#)
		FUI_SendMessage(SParticlesForceY, M_SETVALUE, C\ForceY#)
		FUI_SendMessage(SParticlesForceZ, M_SETVALUE, C\ForceZ#)
		FUI_SendMessage(SParticlesForceModX, M_SETVALUE, C\ForceModX#)
		FUI_SendMessage(SParticlesForceModY, M_SETVALUE, C\ForceModY#)
		FUI_SendMessage(SParticlesForceModZ, M_SETVALUE, C\ForceModZ#)

		; Enable/disable gadgets
		FUI_EnableGadget(CParticlesAxis)
		FUI_EnableGadget(SParticlesMinRadius) : FUI_EnableGadget(SParticlesMaxRadius)
		FUI_EnableGadget(SParticlesWidth) : FUI_EnableGadget(SParticlesHeight) : FUI_EnableGadget(SParticlesDepth)
		If C\Shape = RP_Sphere
			FUI_DisableGadget(CParticlesAxis)
			FUI_DisableGadget(SParticlesWidth) : FUI_DisableGadget(SParticlesHeight) : FUI_DisableGadget(SParticlesDepth)
		ElseIf C\Shape = RP_Cylinder
			FUI_DisableGadget(SParticlesWidth) : FUI_DisableGadget(SParticlesHeight)
		ElseIf C\Shape = RP_Box
			FUI_DisableGadget(CParticlesAxis)
			FUI_DisableGadget(SParticlesMinRadius) : FUI_DisableGadget(SParticlesMaxRadius)
		EndIf
	EndIf

End Function

; Updates the position of the particles preview camera
Function UpdateParticlesPreviewCam()

	PositionEntity ParticlesCam, 0.0, 0.0, 50.0
	RotateEntity ParticlesCam, ParticlesPreviewPitch#, ParticlesPreviewYaw#, 0.0
	MoveEntity ParticlesCam, 0.0, 0.0, -ParticlesPreviewDistance#

End Function

; Sets the currently displayed projectile
Function UpdateProjectileDisplay()

	ID = FUI_SendMessage(FUI_SendMessage(CProjSelected, M_GETSELECTED), M_GETDATA)
	SelectedProj = ProjectileList(ID)

	; No projectile, blank everything
	If SelectedProj = Null
		FUI_SendMessage(TProjName, M_SETCAPTION, "")
		FUI_SendMessage(LProjMesh, M_SETTEXT, "Projectile mesh: [NONE]")
		FUI_SendMessage(CProjEmitter1, M_SETINDEX, 1)
		FUI_SendMessage(LProjTex1, M_SETTEXT, "Emitter texture: [NONE]")
		FUI_SendMessage(CProjEmitter2, M_SETINDEX, 1)
		FUI_SendMessage(LProjTex2, M_SETTEXT, "Emitter texture: [NONE]")
		FUI_SendMessage(BProjHoming, M_SETCHECKED, False)
		FUI_SendMessage(SProjHitChance, M_SETVALUE, 1)
		FUI_SendMessage(SProjDamage, M_SETVALUE, 1)
		FUI_SendMessage(CProjDamageType, M_SETINDEX, 1)
		FUI_SendMessage(SProjSpeed, M_SETVALUE, 1)
	; Projectile selected, fill in relevant information
	Else
		FUI_SendMessage(TProjName, M_SETCAPTION, SelectedProj\Name$)
		FUI_SendMessage(LProjMesh, M_SETTEXT, "Projectile mesh: " + GetFilename$(EditorMeshName$(SelectedProj\MeshID)))
		Found = False
		Idx = 1
		For C.RP_EmitterConfig = Each RP_EmitterConfig
			Idx = Idx + 1
			If C\Name$ = SelectedProj\Emitter1$ Then Found = True : Exit
		Next
		If Found = True
			FUI_SendMessage(CProjEmitter1, M_SETINDEX, Idx)
		Else
			FUI_SendMessage(CProjEmitter1, M_SETINDEX, 1)
		EndIf
		FUI_SendMessage(LProjTex1, M_SETTEXT, "Emitter texture: " + GetFilename$(EditorTexName$(SelectedProj\Emitter1TexID)))
		FUI_SendMessage(CProjEmitter2, M_SETINDEX, 1)
		Found = False
		Idx = 1
		For C.RP_EmitterConfig = Each RP_EmitterConfig
			Idx = Idx + 1
			If C\Name$ = SelectedProj\Emitter2$ Then Found = True : Exit
		Next
		If Found = True
			FUI_SendMessage(CProjEmitter2, M_SETINDEX, Idx)
		Else
			FUI_SendMessage(CProjEmitter2, M_SETINDEX, 1)
		EndIf
		FUI_SendMessage(LProjTex2, M_SETTEXT, "Emitter texture: " + GetFilename$(EditorTexName$(SelectedProj\Emitter2TexID)))
		FUI_SendMessage(BProjHoming, M_SETCHECKED, SelectedProj\Homing)
		FUI_SendMessage(SProjHitChance, M_SETVALUE, SelectedProj\HitChance)
		FUI_SendMessage(SProjDamage, M_SETVALUE, SelectedProj\Damage)
		Idx = 0
		For i = 0 To 19
			If DamageTypes$(i) <> ""
				Idx = Idx + 1
				If i = SelectedProj\DamageType
					FUI_SendMessage(CProjDamageType, M_SETINDEX, Idx)
					Exit
				EndIf
			EndIf
		Next
		FUI_SendMessage(SProjSpeed, M_SETVALUE, SelectedProj\Speed)
	EndIf

End Function

; Sets the currently displayed spell
Function UpdateSpellDisplay()

	ID = FUI_SendMessage(FUI_SendMessage(CSpellSelected, M_GETSELECTED), M_GETDATA)
	SelectedSpell = SpellsList(ID)

	; No spell, blank everything
	If SelectedSpell = Null
		FUI_SendMessage(TSpellName, M_SETCAPTION, "")
		FUI_SendMessage(TSpellDesc, M_SETCAPTION, "")
		FUI_SendMessage(LSpellImageID, M_SETTEXT, "Display icon: [NONE]")
		FUI_SendMessage(SSpellCharge, M_SETVALUE, 0)
		FUI_SendMessage(CSpellExclusiveRace, M_SETINDEX, 1)
		FUI_SendMessage(CSpellExclusiveClass, M_SETINDEX, 1)
		FUI_SendMessage(CSpellScript, M_SETINDEX, 1)
		FUI_SendMessage(CSpellMethod, M_RESET)
	; Spell selected, fill in relevant information
	Else
		FUI_SendMessage(TSpellName, M_SETCAPTION, SelectedSpell\Name$)
		FUI_SendMessage(TSpellDesc, M_SETCAPTION, SelectedSpell\Description$)
		FUI_SendMessage(LSpellImageID, M_SETTEXT, "Display icon: " + GetFilename$(EditorTexName$(SelectedSpell\ThumbnailTexID)))
		FUI_SendMessage(SSpellCharge, M_SETVALUE, SelectedSpell\RechargeTime / 1000)

		; Exclusive race/class
		FUI_SendMessage(CSpellExclusiveRace, M_SETINDEX, 1)
		If SelectedSpell\ExclusiveRace$ <> ""
			For i = 2 To TotalRaces + 1
				FUI_SendMessage(CSpellExclusiveRace, M_SETINDEX, i)
				If Upper$(FUI_SendMessage(CSpellExclusiveRace, M_GETCAPTION)) = Upper$(SelectedSpell\ExclusiveRace$) Then Exit
			Next
		EndIf
		FUI_SendMessage(CSpellExclusiveClass, M_SETINDEX, 1)
		If SelectedSpell\ExclusiveClass$ <> ""
			For i = 2 To TotalClasses + 1
				FUI_SendMessage(CSpellExclusiveClass, M_SETINDEX, i)
				If Upper$(FUI_SendMessage(CSpellExclusiveClass, M_GETCAPTION)) = Upper$(SelectedSpell\ExclusiveClass$) Then Exit
			Next
		EndIf

		; Script
		FUI_SendMessage(CSpellScript, M_SETINDEX, 1)
		If SelectedSpell\Script$ <> ""
			For i = 2 To LoadedScripts + 1
				FUI_SendMessage(CSpellScript, M_SETINDEX, i)
				If Upper$(FUI_SendMessage(CSpellScript, M_GETCAPTION)) = Upper$(SelectedSpell\Script$) Then Exit
			Next
		EndIf
		Result = UpdateScriptMethodsList(CSpellMethod, SelectedSpell\Script$, SelectedSpell\SMethod$)
		If Result = False Then SelectedSpell\SMethod$ = FUI_SendMessage(CSpellMethod, M_GETCAPTION)
	EndIf

End Function

; Sets the currently displayed item
Function UpdateItemDisplay()

	ID = FUI_SendMessage(FUI_SendMessage(CItemSelected, M_GETSELECTED), M_GETDATA)
	SelectedItem = ItemList(ID)

	; Hide item type specific gadgets
	FUI_HideGadget(LItemWeaponDamage) : FUI_HideGadget(SItemWeaponDamage)
	FUI_HideGadget(LItemWeaponType) : FUI_HideGadget(CItemWeaponType)
	FUI_HideGadget(LItemDamageType) : FUI_HideGadget(CItemDamageType)
	FUI_HideGadget(LItemArmourLevel) : FUI_HideGadget(SItemArmourLevel)
	FUI_HideGadget(LItemEatEffects) : FUI_HideGadget(SItemEatEffects)
	FUI_HideGadget(LItemImageID) : FUI_HideGadget(BItemImageID)
	FUI_HideGadget(LItemMiscData) : FUI_HideGadget(TItemMiscData)
	FUI_HideGadget(LItemRangedProjectile) : FUI_HideGadget(CItemRangedProjectile)
	FUI_HideGadget(LItemRangedAnimation) : FUI_HideGadget(TItemRangedAnimation)
	FUI_HideGadget(LItemRange) : FUI_HideGadget(SItemRange)

	; No item, blank everything
	If SelectedItem = Null
		FUI_SendMessage(LItemID, M_SETTEXT, "Item ID: Nothing selected")
		FUI_SendMessage(TItemName, M_SETTEXT, "")
		FUI_SendMessage(SItemValue, M_SETVALUE, 0)
		FUI_SendMessage(SItemMass, M_SETVALUE, 0)
		FUI_SendMessage(CItemType, M_SETINDEX, 1)
		FUI_SendMessage(CSlotType, M_RESET)
		FUI_SendMessage(SItemAttribute, M_SETVALUE, 0)
		FUI_SendMessage(BItemStackable, M_SETCHECKED, False)
		FUI_SendMessage(BItemTakesDamage, M_SETCHECKED, False)
		FUI_SendMessage(CItemExclusiveRace, M_SETINDEX, 1)
		FUI_SendMessage(CItemExclusiveClass, M_SETINDEX, 1)
		FUI_SendMessage(CItemScript, M_SETINDEX, 1)
		FUI_SendMessage(CItemMethod, M_RESET)
		FUI_SendMessage(LItemThumb, M_SETCAPTION, "Item thumbnail texture: [NONE]")
		FUI_SendMessage(LItemMeshM, M_SETCAPTION, "Item mesh (male): [NONE]")
		FUI_SendMessage(LItemMeshF, M_SETCAPTION, "Item mesh (female): [NONE]")
		FUI_SendMessage(BItemGubbin, M_SETCHECKED, False)
	; Item selected, fill in relevant information
	Else
		FUI_SendMessage(LItemID, M_SETTEXT, "Item ID: " + ID)
		FUI_SendMessage(TItemName, M_SETTEXT, SelectedItem\Name$)
		FUI_SendMessage(SItemValue, M_SETVALUE, SelectedItem\Value)
		FUI_SendMessage(SItemMass, M_SETVALUE, SelectedItem\Mass)
		FUI_SendMessage(CItemType, M_SETINDEX, SelectedItem\ItemType)
		FUI_SendMessage(SItemAttribute, M_SETVALUE, SelectedItem\Attributes\Value[FUI_SendMessage(LItemAttributes, M_GETSELECTED)])
		FUI_SendMessage(BItemStackable, M_SETCHECKED, SelectedItem\Stackable)
		FUI_SendMessage(BItemTakesDamage, M_SETCHECKED, SelectedItem\TakesDamage)
		FUI_SendMessage(LItemThumb, M_SETCAPTION, "Item thumbnail texture: " + GetFilename$(EditorTexName$(SelectedItem\ThumbnailTexID)))
		FUI_SendMessage(LItemMeshM, M_SETCAPTION, "Item mesh (male): " + GetFilename$(EditorMeshName$(SelectedItem\MMeshID)))
		FUI_SendMessage(LItemMeshF, M_SETCAPTION, "Item mesh (female): " + GetFilename$(EditorMeshName$(SelectedItem\FMeshID)))
		FUI_SendMessage(BItemGubbin, M_SETCHECKED, SelectedItem\Gubbins[FUI_SendMessageI(CItemGubbin, M_GETINDEX) - 1])

		; Inventory slot type
		FUI_SendMessage(CSlotType, M_RESET)
		If SelectedItem\ItemType = I_Weapon
			SelectedItem\SlotType = Slot_Weapon
			FUI_ComboBoxItem(CSlotType, "Weapon")
			FUI_SendMessage(CSlotType, M_SETINDEX, 1)
			FUI_DisableGadget(CSlotType)
		ElseIf SelectedItem\ItemType = I_Armour
			If SelectedItem\SlotType < Slot_Shield Or SelectedItem\SlotType > Slot_Feet Then SelectedItem\SlotType = Slot_Chest
			FUI_ComboBoxItem(CSlotType, "Shield")
			FUI_ComboBoxItem(CSlotType, "Hat")
			FUI_ComboBoxItem(CSlotType, "Chest")
			FUI_ComboBoxItem(CSlotType, "Hands")
			FUI_ComboBoxItem(CSlotType, "Belt")
			FUI_ComboBoxItem(CSlotType, "Legs")
			FUI_ComboBoxItem(CSlotType, "Feet")
			FUI_SendMessage(CSlotType, M_SETINDEX, SelectedItem\SlotType - 1)
			FUI_EnableGadget(CSlotType)
		ElseIf SelectedItem\ItemType = I_Ring
			If SelectedItem\SlotType <> Slot_Ring And SelectedItem\SlotType <> Slot_Amulet Then SelectedItem\SlotType = Slot_Ring
			FUI_ComboBoxItem(CSlotType, "Ring")
			FUI_ComboBoxItem(CSlotType, "Amulet")
			FUI_SendMessage(CSlotType, M_SETINDEX, SelectedItem\SlotType - 8)
			FUI_EnableGadget(CSlotType)
		Else
			SelectedItem\SlotType = Slot_Backpack
			FUI_ComboBoxItem(CSlotType, "Backpack")
			FUI_SendMessage(CSlotType, M_SETINDEX, 1)
			FUI_DisableGadget(CSlotType)
		EndIf

		; Exclusive race/class
		FUI_SendMessage(CItemExclusiveRace, M_SETINDEX, 1)
		If SelectedItem\ExclusiveRace$ <> ""
			For i = 2 To TotalRaces + 1
				FUI_SendMessage(CItemExclusiveRace, M_SETINDEX, i)
				If Upper$(FUI_SendMessage(CItemExclusiveRace, M_GETCAPTION)) = Upper$(SelectedItem\ExclusiveRace$) Then Exit
			Next
		EndIf
		FUI_SendMessage(CItemExclusiveClass, M_SETINDEX, 1)
		If SelectedItem\ExclusiveClass$ <> ""
			For i = 2 To TotalClasses + 1
				FUI_SendMessage(CItemExclusiveClass, M_SETINDEX, i)
				If Upper$(FUI_SendMessage(CItemExclusiveClass, M_GETCAPTION)) = Upper$(SelectedItem\ExclusiveClass$) Then Exit
			Next
		EndIf

		; Script
		FUI_SendMessage(CItemScript, M_SETINDEX, 1)
		If SelectedItem\Script$ <> ""
			For i = 2 To LoadedScripts + 1
				FUI_SendMessage(CItemScript, M_SETINDEX, i)
				If Upper$(FUI_SendMessage(CItemScript, M_GETCAPTION)) = Upper$(SelectedItem\Script$) Then Exit
			Next
		EndIf
		Result = UpdateScriptMethodsList(CItemMethod, SelectedItem\Script$, SelectedItem\SMethod$)
		If Result = False Then SelectedItem\SMethod$ = FUI_SendMessage(CItemMethod, M_GETCAPTION)

		; Item type specific gadgets
		Select SelectedItem\ItemType
			; Weapon
			Case I_Weapon
				FUI_ShowGadget(LItemWeaponDamage) : FUI_ShowGadget(SItemWeaponDamage)
				FUI_ShowGadget(LItemWeaponType) : FUI_ShowGadget(CItemWeaponType)
				FUI_ShowGadget(LItemDamageType) : FUI_ShowGadget(CItemDamageType)
				FUI_SendMessage(SItemWeaponDamage, M_SETVALUE, SelectedItem\WeaponDamage)
				FUI_SendMessage(CItemWeaponType, M_SETINDEX, SelectedItem\WeaponType)
				Idx = 0
				For i = 0 To 19
					If DamageTypes$(i) <> ""
						Idx = Idx + 1
						If i = SelectedItem\WeaponDamageType
							FUI_SendMessage(CItemDamageType, M_SETINDEX, Idx)
							Exit
						EndIf
					EndIf
				Next
				FUI_ShowGadget(LItemRangedProjectile) : FUI_ShowGadget(CItemRangedProjectile)
				FUI_ShowGadget(LItemRangedAnimation) : FUI_ShowGadget(TItemRangedAnimation)
				FUI_ShowGadget(LItemRange) : FUI_ShowGadget(SItemRange)
				FUI_SendMessage(TItemRangedAnimation, M_SETTEXT, SelectedItem\RangedAnimation$)
				FUI_SendMessage(SItemRange, M_SETVALUE, SelectedItem\Range#)
				For i = 1 To TotalProjectiles
					FUI_SendMessage(CItemRangedProjectile, M_SETINDEX, 1)
					ID = FUI_SendMessage(FUI_SendMessage(CItemRangedProjectile, M_GETSELECTED), M_GETDATA)
					If ID = SelectedItem\RangedProjectile Then Exit
				Next
			; Armour
			Case I_Armour
				FUI_ShowGadget(LItemArmourLevel) : FUI_ShowGadget(SItemArmourLevel)
				FUI_SendMessage(SItemArmourLevel, M_SETVALUE, SelectedItem\ArmourLevel)
			; Potion/ingredient
			Case I_Potion, I_Ingredient
				FUI_ShowGadget(LItemEatEffects) : FUI_ShowGadget(SItemEatEffects)
				FUI_SendMessage(SItemEatEffects, M_SETVALUE, SelectedItem\EatEffectsLength)
			; Image
			Case I_Image
				FUI_ShowGadget(LItemImageID) : FUI_ShowGadget(BItemImageID)
				FUI_SendMessage(LItemImageID, M_SETTEXT, "Display image: " + GetFilename$(EditorTexName$(SelectedItem\ImageID)))
			; Other
			Case I_Other
				FUI_ShowGadget(LItemMiscData) : FUI_ShowGadget(TItemMiscData)
				FUI_SendMessage(TItemMiscData, M_SETCAPTION, SelectedItem\MiscData$)
		End Select
	EndIf

End Function

; Updates all lists of existing races/classes
Function UpdateRaceClassLists()

	; Clear lists
	FUI_SendMessage(CSpellExclusiveRace, M_RESET)
	FUI_ComboBoxItem(CSpellExclusiveRace, "None (can be used by any race)")
	FUI_SendMessage(CSpellExclusiveClass, M_RESET)
	FUI_ComboBoxItem(CSpellExclusiveClass, "None (can be used by any class)")
	FUI_SendMessage(CItemExclusiveRace, M_RESET)
	FUI_ComboBoxItem(CItemExclusiveRace, "None (can be used by any race)")
	FUI_SendMessage(CItemExclusiveClass, M_RESET)
	FUI_ComboBoxItem(CItemExclusiveClass, "None (can be used by any class)")
	FUI_SendMessage(CSpawnActor, M_RESET)
	FUI_ComboBoxItem(CSpawnActor, "None")

	TotalRaces = 0
	TotalClasses = 0

	ActorsChanged = False

	; Fill lists
	For Ac.Actor = Each Actor
		ActorName$ = Ac\Race$ + " [" + Ac\Class$ + "]"
		If Len(ActorName$) > 20 Then ActorName$ = Left$(ActorName$, 17) + "..."
		Item = FUI_ComboBoxItem(CSpawnActor, ActorName$) : FUI_SendMessage(Item, M_SETDATA, Ac\ID)

		Done = False
		For i = 2 To TotalRaces + 1
			FUI_SendMessage(CItemExclusiveRace, M_SETINDEX, i)
			If Upper$(FUI_SendMessage(CItemExclusiveRace, M_GETCAPTION)) = Upper$(Ac\Race$) Then Done = True : Exit
		Next
		If Done = False
			FUI_ComboBoxItem(CItemExclusiveRace, Ac\Race$)
			FUI_ComboBoxItem(CSpellExclusiveRace, Ac\Race$)
			TotalRaces = TotalRaces + 1
		EndIf
	Next
	For Ac.Actor = Each Actor
		Done = False
		For i = 2 To TotalClasses + 1
			FUI_SendMessage(CItemExclusiveClass, M_SETINDEX, i)
			If Upper$(FUI_SendMessage(CItemExclusiveClass, M_GETCAPTION)) = Upper$(Ac\Class$) Then Done = True : Exit
		Next
		If Done = False
			FUI_ComboBoxItem(CItemExclusiveClass, Ac\Class$)
			FUI_ComboBoxItem(CSpellExclusiveClass, Ac\Class$)
			TotalClasses = TotalClasses + 1
		EndIf
	Next

	; Reset selections
	FUI_SendMessage(CSpawnActor, M_SETINDEX, 1)
	UpdateItemDisplay()
	UpdateSpellDisplay()

End Function

; Sets the currently displayed actor
Function UpdateActorDisplay()
	Local ID% = FUI_SendMessage(FUI_SendMessage(CActorSelected, M_GETSELECTED), M_GETDATA)
	SelectedActor = ActorList(ID)

	; Unload existing preview actor
	If ActorPreview <> Null
		If ActorPreview\Gender = 0
			UnloadMesh(ActorPreview\Actor\MeshIDs[0])
			If ActorPreview\Actor\BeardIDs[ActorPreview\Beard] > -1 And ActorPreview\Actor\BeardIDs[ActorPreview\Beard] < 65535
				UnloadMesh(ActorPreview\Actor\BeardIDs[ActorPreview\Beard])
			EndIf
			If ActorPreview\Actor\MaleHairIDs[ActorPreview\Hair] > -1 And ActorPreview\Actor\MaleHairIDs[ActorPreview\Hair] < 65535
				UnloadMesh(ActorPreview\Actor\MaleHairIDs[ActorPreview\Hair])
			EndIf
		Else
			UnloadMesh(ActorPreview\Actor\MeshIDs[1])
			If ActorPreview\Actor\FemaleHairIDs[ActorPreview\Hair] > -1 And ActorPreview\Actor\FemaleHairIDs[ActorPreview\Hair] < 65535
				UnloadMesh(ActorPreview\Actor\FemaleHairIDs[ActorPreview\Hair])
			EndIf
		EndIf
		SafeFreeActorInstance(ActorPreview)
	EndIf

	; No actor, blank everything
	If SelectedActor = Null
		FUI_SendMessage(LActorID, M_SETCAPTION, "Actor ID: Nothing selected")
		FUI_SendMessage(TActorRace, M_SETCAPTION, "")
		FUI_SendMessage(TActorClass, M_SETCAPTION, "")
		FUI_SendMessage(TActorBlurb, M_SETCAPTION, "")
		FUI_SendMessage(CActorGenders, M_SETINDEX, 1)
		FUI_SendMessage(CActorFaction, M_SETINDEX, 1)
		FUI_SendMessage(CActorAttacks, M_SETINDEX, 1)
		FUI_SendMessage(SActorAttackRange, M_SETVALUE, 1)
		FUI_SendMessage(CActorTrades, M_SETINDEX, 1)
		FUI_SendMessage(SActorXPMultiplier, M_SETVALUE, 1)
		FUI_SendMessage(BActorPlayable, M_SETCHECKED, False)
		FUI_SendMessage(BActorRideable, M_SETCHECKED, False)
		FUI_SendMessage(CActorEnviro, M_SETINDEX, 1)
		FUI_SendMessage(CActorStartArea, M_SETINDEX, 1)
		FUI_SendMessage(TActorStartPortal, M_SETTEXT, "")
		FUI_SendMessage(CActorMAnim, M_SETINDEX, 1)
		FUI_SendMessage(CActorFAnim, M_SETINDEX, 1)
		FUI_SendMessage(LActorMSpeech, M_SETCAPTION, "[NONE]")
		FUI_SendMessage(LActorFSpeech, M_SETCAPTION, "[NONE]")
		FUI_SendMessage(BActorSlotAllowed, M_SETCHECKED, False)
		FUI_SendMessage(LActorMBodyMesh, M_SETCAPTION, "Male body mesh: [NONE]")
		FUI_SendMessage(LActorFBodyMesh, M_SETCAPTION, "Female body mesh: [NONE]")
		FUI_SendMessage(LActorMHairMesh, M_SETCAPTION, "Male hair mesh: [NONE]")
		FUI_SendMessage(LActorFHairMesh, M_SETCAPTION, "Female hair mesh: [NONE]")
		FUI_SendMessage(LActorBeardMesh, M_SETCAPTION, "Beard mesh: [NONE]")
		FUI_SendMessage(LActorGubbinMesh, M_SETCAPTION, "Gubbin mesh: [NONE]")
		FUI_SendMessage(LActorMFaceTex, M_SETCAPTION, "Male face texture: [NONE]")
		FUI_SendMessage(LActorFFaceTex, M_SETCAPTION, "Female face texture: [NONE]")
		FUI_SendMessage(LActorMBodyTex, M_SETCAPTION, "Male body texture: [NONE]")
		FUI_SendMessage(LActorFBodyTex, M_SETCAPTION, "Female body texture: [NONE]")
		FUI_SendMessage(LActorBlood, M_SETCAPTION, "Blood texture: [NONE]")
		FUI_SendMessage(SActorScale, M_SETVALUE, 100)
		FUI_SendMessage(BActorPolyCollision, M_SETCHECKED, False)
		FUI_SendMessage(SActorAttribute, M_SETVALUE, 0)
		FUI_SendMessage(SActorAttributeMax, M_SETVALUE, 0)
		FUI_SendMessage(SActorResistance, M_SETVALUE, 0)
	; Actor selected, fill in relevant information
	Else
		FUI_SendMessage(LActorID, M_SETCAPTION, "Actor ID: " + ID)
		FUI_SendMessage(TActorRace, M_SETCAPTION, SelectedActor\Race$)
		FUI_SendMessage(TActorClass, M_SETCAPTION, SelectedActor\Class$)
		FUI_SendMessage(TActorBlurb, M_SETCAPTION, SelectedActor\Description$)
		FUI_SendMessage(CActorGenders, M_SETINDEX, SelectedActor\Genders + 1)
		FUI_SendMessage(CActorAttacks, M_SETINDEX, SelectedActor\Aggressiveness + 1)
		FUI_SendMessage(SActorAttackRange, M_SETVALUE, SelectedActor\AggressiveRange)
		FUI_SendMessage(CActorTrades, M_SETINDEX, SelectedActor\TradeMode + 1)
		FUI_SendMessage(SActorXPMultiplier, M_SETVALUE, SelectedActor\XPMultiplier)
		FUI_SendMessage(BActorPlayable, M_SETCHECKED, SelectedActor\Playable)
		FUI_SendMessage(BActorRideable, M_SETCHECKED, SelectedActor\Rideable)
		FUI_SendMessage(CActorEnviro, M_SETINDEX, SelectedActor\Environment + 1)
		Idx = 0
		For i = 0 To SelectedActor\DefaultFaction
			If FactionNames$(i) <> "" Then Idx = Idx + 1
		Next
		FUI_SendMessage(CActorFaction, M_SETINDEX, Idx)
		Found = False
		For i = 1 To TotalZones
			FUI_SendMessage(CActorStartArea, M_SETINDEX, i)
			If Upper$(FUI_SendMessage(CActorStartArea, M_GETCAPTION)) = Upper$(SelectedActor\StartArea$)
				Found = True
				Exit
			EndIf
		Next
		If Found = False
			FUI_SendMessage(CActorStartArea, M_SETINDEX, 1)
			SelectedActor\StartArea$ = FUI_SendMessage(CActorStartArea, M_GETCAPTION)
		EndIf
		FUI_SendMessage(TActorStartPortal, M_SETTEXT, SelectedActor\StartPortal$)
		Number = 0 : MFound = False : FFound = False
		For AS.AnimSet = Each AnimSet
			Number = Number + 1
			If AS\ID = SelectedActor\MAnimationSet Then FUI_SendMessage(CActorMAnim, M_SETINDEX, Number) : MFound = True
			If AS\ID = SelectedActor\FAnimationSet Then FUI_SendMessage(CActorFAnim, M_SETINDEX, Number) : FFound = True
			If FFound = True And MFound = True Then Exit
		Next
		If MFound = False
			FUI_SendMessage(CMAnim, M_SETINDEX, 1)
			SelectedActor\MAnimationSet = 0
		EndIf
		If FFound = False
			FUI_SendMessage(CFAnim, M_SETINDEX, 1)
			SelectedActor\FAnimationSet = 0
		EndIf
		Name$ = EditorSoundName$(SelectedActor\MSpeechIDs[Int(FUI_SendMessage(CActorMSpeech, M_GETINDEX)) - 1])
		If Len(Name$) > 70 Then Name$ = "..." + Right$(Name$, 67)
		FUI_SendMessage(LActorMSpeech, M_SETCAPTION, Name$)
		Name$ = EditorSoundName$(SelectedActor\FSpeechIDs[Int(FUI_SendMessage(CActorFSpeech, M_GETINDEX)) - 1])
		If Len(Name$) > 70 Then Name$ = "..." + Right$(Name$, 67)
		FUI_SendMessage(LActorFSpeech, M_SETCAPTION, Name$)
		
		FUI_SendMessage( BActorSlotAllowed, M_SETCHECKED, Not GetFlag(SelectedActor\InventorySlots, FUI_SendMessageI( CActorSlotAllowed, M_GETINDEX) - 1 ) )
		FUI_SendMessage(LActorMBodyMesh, M_SETCAPTION, "Male body mesh: " + GetFilename$(EditorMeshName$(SelectedActor\MeshIDs[0])))
		FUI_SendMessage(LActorFBodyMesh, M_SETCAPTION, "Female body mesh: " + GetFilename$(EditorMeshName$(SelectedActor\MeshIDs[1])))
		FUI_SendMessage(LActorMHairMesh, M_SETCAPTION, "Male hair mesh: " + GetFilename$(EditorMeshName$(SelectedActor\MaleHairIDs[FUI_SendMessageI(CActorMHairMesh, M_GETINDEX) - 1])))
		FUI_SendMessage(LActorFHairMesh, M_SETCAPTION, "Female hair mesh: " + GetFilename$(EditorMeshName$(SelectedActor\FemaleHairIDs[FUI_SendMessageI(CActorFHairMesh, M_GETINDEX) - 1])))
		FUI_SendMessage(LActorBeardMesh, M_SETCAPTION, "Beard mesh: " + GetFilename$(EditorMeshName$(SelectedActor\BeardIDs[FUI_SendMessageI(CActorBeardMesh, M_GETINDEX) - 1])))
		FUI_SendMessage(LActorGubbinMesh, M_SETCAPTION, "Gubbin mesh: " + GetFilename$(EditorMeshName$(SelectedActor\MeshIDs[FUI_SendMessageI(CActorGubbinMesh, M_GETINDEX) + 1])))
		FUI_SendMessage(LActorMFaceTex, M_SETCAPTION, "Male face texture: " + GetFilename$(EditorTexName$(SelectedActor\MaleFaceIDs[FUI_SendMessageI(CActorMFaceTex, M_GETINDEX) - 1])))
		FUI_SendMessage(LActorFFaceTex, M_SETCAPTION, "Female face texture: " + GetFilename$(EditorTexName$(SelectedActor\FemaleFaceIDs[FUI_SendMessageI(CActorFFaceTex, M_GETINDEX) - 1])))
		FUI_SendMessage(LActorMBodyTex, M_SETCAPTION, "Male body texture: " + GetFilename$(EditorTexName$(SelectedActor\MaleBodyIDs[FUI_SendMessageI(CActorMBodyTex, M_GETINDEX) - 1])))
		FUI_SendMessage(LActorFBodyTex, M_SETCAPTION, "Female body texture: " + GetFilename$(EditorTexName$(SelectedActor\FemaleBodyIDs[FUI_SendMessageI(CActorFBodyTex, M_GETINDEX) - 1])))
		FUI_SendMessage(LActorBlood, M_SETCAPTION, "Blood texture: " + GetFilename$(EditorTexName$(SelectedActor\BloodTexID)))
		FUI_SendMessage(SActorScale, M_SETVALUE, Int(SelectedActor\Scale# * 100.0))
		FUI_SendMessage(BActorPolyCollision, M_SETCHECKED, SelectedActor\PolyCollision)
		FUI_SendMessage(SActorAttribute, M_SETVALUE, SelectedActor\Attributes\Value[FUI_SendMessage(FUI_SendMessage(LActorAttributes, M_GETSELECTED), M_GETDATA)])
		FUI_SendMessage(SActorAttributeMax, M_SETVALUE, SelectedActor\Attributes\Maximum[FUI_SendMessage(FUI_SendMessage(LActorAttributes, M_GETSELECTED), M_GETDATA)])
		FUI_SendMessage(SActorResistance, M_SETVALUE, SelectedActor\Resistances[FUI_SendMessage(FUI_SendMessage(LActorResistances, M_GETSELECTED), M_GETDATA)])

		; Enable/disable certain gadgets based on gender
		UpdateActorGenderGadgets()
	EndIf

	UpdateActorPreview()

End Function

Function CheckInvetorySlotAllowed(slot%)
	;- FIXME Handling for Actor Slots is too spread around
	If FindChild(ActorPreview\EN,"Head") = 0
		FUI_SendMessage(BActorSlotAllowed, M_DISABLE)
		FUI_SendMessage(BActorSlotAllowed, M_SETCHECKED, True)
		Return
	EndIf
	Select flag
		Case 1
			If FindChild(ActorPreview\EN,"R_Hand") = 0
				FUI_SendMessage(BActorSlotAllowed, M_DISABLE)
				FUI_SendMessage(BActorSlotAllowed, M_SETCHECKED, True)
				Return
			EndIf
			
		Case 2
			If FindChild(ActorPreview\EN,"L_Hand") = 0
				FUI_SendMessage(BActorSlotAllowed, M_DISABLE)
				FUI_SendMessage(BActorSlotAllowed, M_SETCHECKED, True)
				Return
			EndIf
			
		Case 3
			If FindChild(ActorPreview\EN,"Head") = 0
				FUI_SendMessage(BActorSlotAllowed, M_DISABLE)
				FUI_SendMessage(BActorSlotAllowed, M_SETCHECKED, True)
				Return
			EndIf
			
		Case 4
			If FindChild(ActorPreview\EN,"Chest") = 0
				FUI_SendMessage(BActorSlotAllowed, M_DISABLE)
				FUI_SendMessage(BActorSlotAllowed, M_SETCHECKED, True)
				Return
			EndIf
		
		Default	
			FUI_SendMessage(BActorSlotAllowed, M_ENABLE)
	End Select
	
	FUI_SendMessage( BActorSlotAllowed, M_SETCHECKED, Not GetFlag(SelectedActor\InventorySlots, slot) )
End Function
; Updates the actor preview entity
Function UpdateActorPreview()
	LoadGubbinNames()
	;- FIXME Handling for Actor Slots is too spread around
	If ActorPreview <> Null
		If ActorPreview\Gender = 0
			UnloadMesh(ActorPreview\Actor\MeshIDs[0])
			If ActorPreview\Actor\BeardIDs[ActorPreview\Beard] > -1 And ActorPreview\Actor\BeardIDs[ActorPreview\Beard] < 65535
				UnloadMesh(ActorPreview\Actor\BeardIDs[ActorPreview\Beard])
			EndIf
			If ActorPreview\Actor\MaleHairIDs[ActorPreview\Hair] > -1 And ActorPreview\Actor\MaleHairIDs[ActorPreview\Hair] < 65535
				UnloadMesh(ActorPreview\Actor\MaleHairIDs[ActorPreview\Hair])
			EndIf
		Else
			UnloadMesh(ActorPreview\Actor\MeshIDs[1])
			If ActorPreview\Actor\FemaleHairIDs[ActorPreview\Hair] > -1 And ActorPreview\Actor\FemaleHairIDs[ActorPreview\Hair] < 65535
				UnloadMesh(ActorPreview\Actor\FemaleHairIDs[ActorPreview\Hair])
			EndIf
		EndIf
		SafeFreeActorInstance(ActorPreview)
	EndIf

	If SelectedActor <> Null
		ActorPreview = CreateActorInstance(SelectedActor)
		Temp = HideNametags
		HideNametags = True
		Result = LoadActorInstance3D(ActorPreview, 0.5 / SelectedActor\Scale#, False, False)
		HideNametags = Temp
		If Result = False Then Delete ActorPreview : Return
		If ActorPreview\ShadowEN <> 0 Then FreeEntity ActorPreview\ShadowEN : ActorPreview\ShadowEN = 0
		If ActorPreview\NametagEN <> 0 Then FreeEntity ActorPreview\NametagEN : ActorPreview\NametagEN = 0
		PositionEntity ActorPreview\CollisionEN, 0, 0, 40
		For i = 0 To 5 : ShowGubbin(ActorPreview, i, True) : Next
		
		; if Actor is no B3D type mesh, disable the option to make it playable
		Local name$ = GetMeshName(SelectedActor\MeshIDs[0])
		If Instr( name$, "b3d", Len(name$) - 5) = 0
			FUI_SendMessage(BActorRideable, M_SETCHECKED, False)
			FUI_SendMessage(BActorRideable, M_DISABLE)
			SelectedActor\Rideable = False
		Else
			FUI_SendMessage(BActorRideable, M_ENABLE)
			If SelectedActor\Rideable Then FUI_SendMessage(BActorRideable, M_SETCHECKED, True)
		EndIf
		
		; If Actor has no head, disable the corresponding things automatically
		Local ActorSlot% = FUI_SendMessageI(CActorSlotAllowed, M_GETINDEX ) -1
		; 1 weapon, 2 shield, 3 hat, 4 Hand, 5 Belt, 6 Legs, 7 Feet
		; 1 R_Hand, 2 L_Hand, 3 Head, 
		
		If FindChild(ActorPreview\EN,"R_Hand") = 0
			; No right hand found so no weapon!
			SelectedActor\InventorySlots = SelectedActor\InventorySlots And ($FFFFFFFF Xor 1)
		EndIf
		
		If FindChild(ActorPreview\EN,"L_Hand") = 0
			; No left hand found so no shield
			SelectedActor\InventorySlots = SelectedActor\InventorySlots And ($FFFFFFFF Xor (1 Shl 1))
		EndIf
		
		
		If FindChild(ActorPreview\EN,"Head") = 0
			; No head found so disable the related mechanisms
			FUI_SendMessage(BActorPlayable, M_SETCHECKED, False)
			FUI_SendMessage(BActorPlayable, M_DISABLE)
			SelectedActor\Playable = False
			
			FUI_SendMessage(BActorFHairMesh, M_DISABLE)
			FUI_SendMessage(BActorFHairMeshN, M_DISABLE)
			FUI_SendMessage(CActorFHairMesh, M_DISABLE)
			FUI_SendMessage(BActorMHairMesh, M_DISABLE)
			FUI_SendMessage(BActorMHairMeshN, M_DISABLE)
			FUI_SendMessage(CActorMHairMesh, M_DISABLE)
			FUI_SendMessage(BActorBeardMesh, M_DISABLE)
			FUI_SendMessage(BActorBeardMeshN, M_DISABLE)
			
			SelectedActor\InventorySlots = SelectedActor\InventorySlots And ($FFFFFFFF Xor (1 Shl 2))
		Else
			FUI_SendMessage(BActorPlayable, M_ENABLE)
			FUI_SendMessage(BActorPlayable, M_SETCHECKED, SelectedActor\Playable)
			
			FUI_SendMessage(BActorFHairMesh, M_ENABLE)
			FUI_SendMessage(BActorFHairMeshN, M_ENABLE)
			FUI_SendMessage(CActorFHairMesh, M_ENABLE)
			FUI_SendMessage(CActorFHairMesh, M_SETINDEX, 1)
			FUI_SendMessage(BActorMHairMesh, M_ENABLE)
			FUI_SendMessage(BActorMHairMeshN, M_ENABLE)
			FUI_SendMessage(CActorMHairMesh, M_ENABLE)
			FUI_SendMessage(CActorMHairMesh, M_SETINDEX, 1)
			FUI_SendMessage(BActorBeardMesh, M_ENABLE)
			FUI_SendMessage(BActorBeardMeshN, M_ENABLE)
			
		EndIf
		
		If FindChild(ActorPreview\EN,"Chest") = 0
			; No chest so disable the related mechanisms
			SelectedActor\InventorySlots = SelectedActor\InventorySlots And ($FFFFFFFF Xor (1 Shl 3))	EndIf
		
		CheckInvetorySlotAllowed(ActorSlot)
		
	EndIf

End Function

; Enable/disable certain gadgets based on gender
Function UpdateActorGenderGadgets()

	; Enable all gadgets
	FUI_EnableGadget(CActorMAnim) : FUI_EnableGadget(CActorFAnim)
	FUI_EnableGadget(BActorMSpeech) : FUI_EnableGadget(BActorMSpeechN)
	FUI_EnableGadget(BActorFSpeech) : FUI_EnableGadget(BActorFSpeechN)
	FUI_EnableGadget(BActorMBodyMesh)
	FUI_EnableGadget(CActorMHairMesh) : FUI_EnableGadget(BActorMHairMesh) : FUI_EnableGadget(BActorMHairMeshN)
	FUI_EnableGadget(CActorMFaceTex) : FUI_EnableGadget(BActorMFaceTex) : FUI_EnableGadget(BActorMFaceTexN)
	FUI_EnableGadget(CActorMBodyTex) : FUI_EnableGadget(BActorMBodyTex) : FUI_EnableGadget(BActorMBodyTexN)
	FUI_EnableGadget(CActorBeardMesh) : FUI_EnableGadget(BActorBeardMesh) : FUI_EnableGadget(BActorBeardMeshN)
	FUI_EnableGadget(BActorFBodyMesh)
	FUI_EnableGadget(CActorFHairMesh) : FUI_EnableGadget(BActorFHairMesh) : FUI_EnableGadget(BActorFHairMeshN)
	FUI_EnableGadget(CActorFFaceTex) : FUI_EnableGadget(BActorFFaceTex) : FUI_EnableGadget(BActorFFaceTexN)
	FUI_EnableGadget(CActorFBodyTex) : FUI_EnableGadget(BActorFBodyTex) : FUI_EnableGadget(BActorFBodyTexN)

	; Disable all female gadgets
	If SelectedActor\Genders = 1 Or SelectedActor\Genders = 3
		FUI_DisableGadget(CActorFAnim)
		FUI_DisableGadget(BActorFSpeech) : FUI_DisableGadget(BActorFSpeechN)
		FUI_DisableGadget(BActorFBodyMesh)
		FUI_DisableGadget(CActorFHairMesh) : FUI_DisableGadget(BActorFHairMesh) : FUI_DisableGadget(BActorFHairMeshN)
		FUI_DisableGadget(CActorFFaceTex) : FUI_DisableGadget(BActorFFaceTex) : FUI_DisableGadget(BActorFFaceTexN)
		FUI_DisableGadget(CActorFBodyTex) : FUI_DisableGadget(BActorFBodyTex) : FUI_DisableGadget(BActorFBodyTexN)
	; Disable all male gadgets
	ElseIf SelectedActor\Genders = 2
		FUI_DisableGadget(CActorMAnim)
		FUI_DisableGadget(BActorMSpeech) : FUI_DisableGadget(BActorMSpeechN)
		FUI_DisableGadget(BActorMBodyMesh)
		FUI_DisableGadget(CActorMHairMesh) : FUI_DisableGadget(BActorMHairMesh) : FUI_DisableGadget(BActorMHairMeshN)
		FUI_DisableGadget(CActorMFaceTex) : FUI_DisableGadget(BActorMFaceTex) : FUI_DisableGadget(BActorMFaceTexN)
		FUI_DisableGadget(CActorMBodyTex) : FUI_DisableGadget(BActorMBodyTex) : FUI_DisableGadget(BActorMBodyTexN)
		FUI_DisableGadget(CActorBeardMesh) : FUI_DisableGadget(BActorBeardMesh) : FUI_DisableGadget(BActorBeardMeshN)
	EndIf

End Function

; Updates sun gadgets
Function UpdateSunDisplay()

;Suns... very fun to code stupid bloody things...

	; No sun, blank everything
	If SelectedSun = Null
		FUI_SendMessage(LSunSeasonName, M_SETCAPTION, "")
		FUI_SendMessage(LSunNumber, M_SETCAPTION, "Sun 0 of 0")
		FUI_SendMessage(LSunTex, M_SETCAPTION, "Sun texture: [NONE]")
		FUI_SendMessage(SSunSize, M_SETVALUE, 1.0)
		FUI_SendMessage(SSunRiseH, M_SETVALUE, 0)
		FUI_SendMessage(SSunRiseM, M_SETVALUE, 0)
		FUI_SendMessage(SSunSetH, M_SETVALUE, 0)
		FUI_SendMessage(SSunSetM, M_SETVALUE, 0)
		FUI_SendMessage(SSunAngle, M_SETVALUE, 0)
		FUI_SendMessage(SLSunR, M_SETVALUE, 0)
		FUI_SendMessage(SLSunG, M_SETVALUE, 0)
		FUI_SendMessage(SLSunB, M_SETVALUE, 0)
		FUI_SendMessage(BSunShowFlares, M_SETCHECKED, False)
		;Phase
		FUI_SendMessage(BSunShowPhases, M_SETCHECKED, False)
		FUI_SendMessage(SSunPhase_Length, M_SETVALUE, 1)
		FUI_DisableGadget(CSunPhase)
		FUI_DisableGadget(SSunPhase_Length)
		
	; A sun is selected, fill in relevant information
	Else
		; Count total suns
		Local Count = 0, ThisSun = 0
		For Sn.Sun = Each Sun
			Count = Count + 1
			If Sn = SelectedSun Then ThisSun = Count
		Next

		; Update gadgets
		Season = FUI_SendMessageI(CSunSeason, M_GETINDEX) - 1
		FUI_SendMessage(LSunSeasonName, M_SETCAPTION, "(" + SeasonName$(Season) + ")")
		FUI_SendMessage(LSunNumber, M_SETCAPTION, "Sun " + Str$(ThisSun) + " of " + Str$(Count))
		
		FUI_SendMessage(BSunShowPhases, M_SETCHECKED, SelectedSun\ShowPhases)
		;FUI_SendMessage(LSunTex, M_SETCAPTION, "Sun texture: " + GetFilename$(EditorTexName$(SelectedSun\TexID)))
		If FUI_SendMessage(BSunShowPhases, M_GETCHECKED) = False
			FUI_SendMessage(LSunTex, M_SETCAPTION, "Sun texture: " + GetFilename$(EditorTexName$(SelectedSun\TexID[0])))
			FUI_DisableGadget(CSunPhase)
			FUI_DisableGadget(SSunPhase_Length)
		Else
			FUI_SendMessage(LSunTex, M_SETCAPTION, "Phase " + FUI_SendMessageI(CSunPhase, M_GETINDEX) + " texture: " + GetFilename$(EditorTexName$(SelectedSun\TexID[FUI_SendMessageI(CSunPhase, M_GETINDEX) - 1])))
			FUI_EnableGadget(CSunPhase)
			FUI_EnableGadget(SSunPhase_Length)
		EndIf
		
		
		FUI_SendMessage(SSunPhase_Length, M_SETVALUE, Int(SelectedSun\Phase_Length))

		
		FUI_SendMessage(SSunSize, M_SETVALUE, SelectedSun\Size#)
		FUI_SendMessage(SSunRiseH, M_SETVALUE, SelectedSun\StartH[Season])
		FUI_SendMessage(SSunRiseM, M_SETVALUE, SelectedSun\StartM[Season])
		FUI_SendMessage(SSunSetH, M_SETVALUE, SelectedSun\EndH[Season])
		FUI_SendMessage(SSunSetM, M_SETVALUE, SelectedSun\EndM[Season])
		FUI_SendMessage(SSunAngle, M_SETVALUE, Int(SelectedSun\PathAngle#))
		FUI_SendMessage(SLSunR, M_SETVALUE, SelectedSun\LightR)
		FUI_SendMessage(SLSunG, M_SETVALUE, SelectedSun\LightG)
		FUI_SendMessage(SLSunB, M_SETVALUE, SelectedSun\LightB)
		FUI_SendMessage(BSunShowFlares, M_SETCHECKED, SelectedSun\ShowFlares)
	EndIf

End Function

; Updates zone gadgets
Function UpdateZoneDisplay(View)

	; Hide all panels
	FUI_HideGadget(GDynamicC) 
	FUI_HideGadget(GScenery) : FUI_HideGadget(GSceneryOptions) : FUI_HideGadget(GTerrain) : FUI_HideGadget(GTerrainOptions)
	FUI_HideGadget(GEmitters) : FUI_HideGadget(GEmitterOptions) : FUI_HideGadget(GWater) : FUI_HideGadget(GWaterOptions)
	FUI_HideGadget(GSound) : FUI_HideGadget(GSoundOptions) : FUI_HideGadget(GTrigger) : FUI_HideGadget(GTriggerOptions) : FUI_HideGadget(GPortal)
	FUI_HideGadget(GPortalOptions) : FUI_HideGadget(GEnviroOptions) : FUI_HideGadget(GOtherOptions) : FUI_HideGadget(GWaypoint) : FUI_HideGadget(GWaypointOptions)

	; Unselect selected if we're not in a selection view mode
	If ((View Mod 2) <> 0 Or View > 18) And SelectedEN <> 0
		UpdateWaypointRange(0)
		EntityParent(SelectionBoxEN, 0)
		HideEntity(SelectionBoxEN)
		SelectedEN = 0
		FUI_SendMessage(BZoneSelect, M_SETSELECTED, True)
		ZoneMode = 1
		FlushMouse()
		FlushKeys()
	ElseIf SelectedEN = 0 And (View Mod 2) = 0 And View <= 18
		View = View - 1
	EndIf

	ZoneView = View

	; Select correct toolbar button and show panel
	Select View
		; Scenery placement
		Case 1 : FUI_SendMessage(BZoneScenery, M_SETSELECTED, True) : FUI_ShowGadget(GScenery)
		; Scenery options
		Case 2
			FUI_SendMessage(BZoneScenery, M_SETSELECTED, True) : FUI_ShowGadget(GSceneryOptions)
			S.Scenery = Object.Scenery(EntityName$(SelectedEN))
			FUI_SendMessage(CSceneryAnim, M_SETINDEX, S\AnimationMode + 1)
			If S\SceneryID > 0
;				FUI_SendMessage(SSceneryInvenSize, M_SETVALUE, CurrentArea\Instances[0]\OwnedScenery[S\SceneryID - 1]\InventorySize)
				FUI_SendMessage(BSceneryOwnable, M_SETCHECKED, True)
				FUI_SendMessage(LSceneryID, M_SETCAPTION, "Ownership ID: " + Str$(S\SceneryID - 1))
			Else
				FUI_SendMessage(SSceneryInvenSize, M_SETVALUE, 0)
				FUI_SendMessage(BSceneryOwnable, M_SETCHECKED, False)
				FUI_SendMessage(LSceneryID, M_SETCAPTION, "Ownership ID: N/A")
			EndIf
			FUI_SendMessage(BSceneryCatchRain, M_SETCHECKED, S\CatchRain)
			FUI_SendMessage(CSceneryCollision, M_SETINDEX, GetEntityType(S\EN) + 1)
			FUI_SendMessage(BSceneryLocked, M_SETCHECKED, S\Locked)
			
			;v1.104 options cysis145 [010]
			FUI_SendMessage(BSceneryCast, M_SETCHECKED, S\CastShadow)
			FUI_SendMessage(BSceneryReceive, M_SETCHECKED, S\ReceiveShadow) 
			
			FUI_SendMessage(CSceneryLodType, M_SETINDEX, S\RenderRange + 1)	;[011]
			
		; Terrain placement
		Case 3 : FUI_SendMessage(BZoneTerrain, M_SETSELECTED, True) : FUI_ShowGadget(GTerrain)
		; Terrain options
		Case 4
			FUI_SendMessage(BZoneTerrain, M_SETSELECTED, True) : FUI_ShowGadget(GTerrainOptions)
			T.Terrain = Object.Terrain(EntityName$(SelectedEN))
			FUI_SendMessage(STerrainDetailScale, M_SETVALUE, T\DetailTexScale#)
			FUI_SendMessage(STerrainDetail, M_SETVALUE, T\Detail)
			FUI_SendMessage(BTerrainMorph, M_SETCHECKED, T\Morph)
			FUI_SendMessage(BTerrainShade, M_SETCHECKED, T\Shading)
		; Emitter placement
		Case 5 : FUI_SendMessage(BZoneEmitters, M_SETSELECTED, True) : FUI_ShowGadget(GEmitters)
		; Emitter options
		Case 6 : FUI_SendMessage(BZoneEmitters, M_SETSELECTED, True) : FUI_ShowGadget(GEmitterOptions)
		; Water placement
		Case 7 : FUI_SendMessage(BZoneWater, M_SETSELECTED, True) : FUI_ShowGadget(GWater)
		; Water options
		Case 8
			FUI_SendMessage(BZoneWater, M_SETSELECTED, True) : FUI_ShowGadget(GWaterOptions)
			W.Water = Object.Water(EntityName$(SelectedEN))
			SW.ServerWater = Object.ServerWater(W\ServerWater)
			FUI_SendMessage(SLWaterR, M_SETVALUE, W\Red)
			FUI_SendMessage(SLWaterG, M_SETVALUE, W\Green)
			FUI_SendMessage(SLWaterB, M_SETVALUE, W\Blue)
			FUI_SendMessage(SWaterTexScale, M_SETVALUE, W\TexScale#)
			FUI_SendMessage(SLWaterOpacity, M_SETVALUE, W\Opacity)
			FUI_SendMessage(SWaterDamage, M_SETVALUE, SW\Damage)
			Found = False
			For i = 1 To 20
				FUI_SendMessage(CWaterDamageType, M_SETINDEX, i)
				If FUI_SendMessage(FUI_SendMessage(CWaterDamageType, M_GETSELECTED), M_GETDATA) = SW\DamageType Then Found = True : Exit
			Next
			If Found = False
				FUI_SendMessage(CWaterDamageType, M_SETINDEX, 1)
				SW\DamageType = FUI_SendMessage(FUI_SendMessage(CWaterDamageType, M_GETSELECTED), M_GETDATA)
			EndIf
		; Collision boxes
		Case 9, 10 : FUI_SendMessage(BZoneColBox, M_SETSELECTED, True)
		; Sound zone placement
		Case 11 : FUI_SendMessage(BZoneSoundZone, M_SETSELECTED, True) : FUI_ShowGadget(GSound)
		; Sound zone options
		Case 12
			FUI_SendMessage(BZoneSoundZone, M_SETSELECTED, True) : FUI_ShowGadget(GSoundOptions)
			SZ.SoundZone = Object.SoundZone(EntityName$(SelectedEN))
			If SZ\SoundID > -1 And SZ\SoundID < 65535
				FUI_SendMessage(LSoundName, M_SETCAPTION, EditorSoundName$(SZ\SoundID))
			Else
				FUI_SendMessage(LSoundName, M_SETCAPTION, EditorMusicName$(SZ\MusicID))
			EndIf
			FUI_SendMessage(SSoundRepeat, M_SETVALUE, SZ\RepeatTime)
			FUI_SendMessage(SSoundVolume, M_SETVALUE, SZ\Volume)
		; Trigger placement
		Case 13 : FUI_SendMessage(BZoneTriggers, M_SETSELECTED, True) : FUI_ShowGadget(GTrigger)
		; Trigger options
		Case 14
			FUI_SendMessage(BZoneTriggers, M_SETSELECTED, True) : FUI_ShowGadget(GTriggerOptions)
			TriggerID = Mid$(EntityName$(SelectedEN), 2)
			FUI_SendMessage(LTriggerScript, M_SETCAPTION, CurrentArea\TriggerScript$[TriggerID])
			FUI_SendMessage(LTriggerMethod, M_SETCAPTION, CurrentArea\TriggerMethod$[TriggerID])
			FUI_SendMessage(LZoneTriggerID,M_SETCAPTION,TriggerID)
		; Waypoint placement
		Case 15 : FUI_SendMessage(BZoneWaypoints, M_SETSELECTED, True) : FUI_ShowGadget(GWaypoint)
		; Waypoint options
		Case 16
			FUI_SendMessage(BZoneWaypoints, M_SETSELECTED, True) : FUI_ShowGadget(GWaypointOptions)
			; Default disabled gadgets state
			FUI_SendMessage(SWaypointPause, M_SETVALUE, 0)
			FUI_SendMessage(CSpawnActor, M_SETINDEX, 1)
			FUI_SendMessage(CSpawnScript, M_SETINDEX, 1)
			FUI_SendMessage(CSpawnActorScript, M_SETINDEX, 1)
			FUI_SendMessage(CSpawnDeathScript, M_SETINDEX, 1)
			FUI_SendMessage(SSpawnMax, M_SETVALUE, 1)
			FUI_SendMessage(SSpawnRange, M_SETVALUE, 0)
			FUI_SendMessage(SSpawnFrequency, M_SETVALUE, 0)
			FUI_DisableGadget(CSpawnScript) : FUI_DisableGadget(CSpawnActorScript) : FUI_DisableGadget(CSpawnDeathScript)
			FUI_DisableGadget(SSpawnFrequency) : FUI_DisableGadget(SSpawnMax) : FUI_DisableGadget(SSpawnRange)

			; Find attached spawn point if there is one
			WaypointID = Mid$(EntityName$(SelectedEN), 2)
			FUI_SendMessage(SWaypointPause, M_SETVALUE, CurrentArea\WaypointPause[WaypointID])
			SpawnNumber = GetSpawnPoint(WaypointID)
			If SpawnNumber > -1
				FUI_EnableGadget(CSpawnScript) : FUI_EnableGadget(CSpawnActorScript) : FUI_EnableGadget(CSpawnDeathScript)
				FUI_EnableGadget(SSpawnFrequency) : FUI_EnableGadget(SSpawnMax) : FUI_EnableGadget(SSpawnRange)
				For i = 2 To TotalActors + 1
					FUI_SendMessage(CSpawnActor, M_SETINDEX, i)
					If FUI_SendMessage(FUI_SendMessage(CSpawnActor, M_GETSELECTED), M_GETDATA) = CurrentArea\SpawnActor[SpawnNumber] Then Exit
				Next
				If CurrentArea\SpawnScript$[SpawnNumber] = ""
					FUI_SendMessage(CSpawnScript, M_SETINDEX, 1)
				Else
					Found = False
					For i = 1 To LoadedScripts + 1
						FUI_SendMessage(CSpawnScript, M_SETINDEX, i)
						Name$ = FUI_SendMessage(CSpawnScript, M_GETCAPTION)
						If Name$ = CurrentArea\SpawnScript$[SpawnNumber] Then Found = True : Exit
					Next
					If Found = False
						CurrentArea\SpawnScript$[SpawnNumber] = ""
						FUI_SendMessage(CSpawnScript, M_SETINDEX, 1)
					EndIf
				EndIf
				If CurrentArea\SpawnActorScript$[SpawnNumber] = ""
					FUI_SendMessage(CSpawnActorScript, M_SETINDEX, 1)
				Else
					Found = False
					For i = 1 To LoadedScripts + 1
						FUI_SendMessage(CSpawnActorScript, M_SETINDEX, i)
						Name$ = FUI_SendMessage(CSpawnActorScript, M_GETCAPTION)
						If Name$ = CurrentArea\SpawnActorScript$[SpawnNumber] Then Found = True : Exit
					Next
					If Found = False
						CurrentArea\SpawnActorScript$[SpawnNumber] = ""
						FUI_SendMessage(CSpawnActorScript, M_SETINDEX, 1)
					EndIf
				EndIf
				If CurrentArea\SpawnDeathScript$[SpawnNumber] = ""
					FUI_SendMessage(CSpawnDeathScript, M_SETINDEX, 1)
				Else
					Found = False
					For i = 1 To LoadedScripts + 1
						FUI_SendMessage(CSpawnDeathScript, M_SETINDEX, i)
						Name$ = FUI_SendMessage(CSpawnDeathScript, M_GETCAPTION)
						If Name$ = CurrentArea\SpawnDeathScript$[SpawnNumber] Then Found = True : Exit
					Next
					If Found = False
						CurrentArea\SpawnDeathScript$[SpawnNumber] = ""
						FUI_SendMessage(CSpawnDeathScript, M_SETINDEX, 1)
					EndIf
				EndIf
				FUI_SendMessage(SSpawnFrequency, M_SETVALUE, CurrentArea\SpawnFrequency[SpawnNumber])
				FUI_SendMessage(SSpawnMax, M_SETVALUE, CurrentArea\SpawnMax[SpawnNumber])
				FUI_SendMessage(SSpawnRange, M_SETVALUE, CurrentArea\SpawnRange#[SpawnNumber])
			EndIf
		; Portal placement
		Case 17
			FUI_SendMessage(BZonePortals, M_SETSELECTED, True) : FUI_ShowGadget(GPortal)
			FUI_SendMessage(TPortalName, M_SETCAPTION, "")
			FUI_SendMessage(CPortalLinkArea, M_SETINDEX, 1)
		; Portal options
		Case 18
			FUI_SendMessage(BZonePortals, M_SETSELECTED, True) : FUI_ShowGadget(GPortalOptions)
			PortalID = Mid$(EntityName$(SelectedEN), 2)
			FUI_SendMessage(LPortalName, M_SETCAPTION, CurrentArea\PortalName$[PortalID])
			Found = False
			For i = 1 To TotalZones + 1
				FUI_SendMessage(CPortalLinkAreaO, M_SETINDEX, i)
				Name$ = FUI_SendMessage(CPortalLinkAreaO, M_GETCAPTION)
				If Name$ = CurrentArea\PortalLinkArea$[PortalID] Then Found = True : Exit
			Next
			If Found = False
				FUI_SendMessage(CPortalLinkAreaO, M_SETINDEX, 1)
				CurrentArea\PortalLinkArea$[PortalID] = ""
			EndIf
			FUI_SendMessage(CPortalLinkArea, M_SETINDEX, FUI_SendMessage(CPortalLinkAreaO, M_GETINDEX))
			Result = UpdatePortalNameList()
			Found = False
			For i = 1 To Result
				FUI_SendMessage(CPortalLinkNameO, M_SETINDEX, i)
				Name$ = FUI_SendMessage(CPortalLinkNameO, M_GETCAPTION)
				If Name$ = CurrentArea\PortalLinkName$[PortalID] Then Found = True : Exit
			Next
			If Found = False
				FUI_SendMessage(CPortalLinkNameO, M_SETINDEX, 1)
				CurrentArea\PortalLinkName$[PortalID] = ""
			EndIf
		; Environment options
		Case 19
			FUI_SendMessage(BZoneEnviro, M_SETSELECTED, True) : FUI_ShowGadget(GEnviroOptions)
			FUI_SendMessage(BOutdoors, M_SETCHECKED, Outdoors)
			FUI_SendMessage(SWeatherChance(W_Rain - 1), M_SETVALUE, CurrentArea\WeatherChance[W_Rain - 1])
			FUI_SendMessage(SWeatherChance(W_Snow - 1), M_SETVALUE, CurrentArea\WeatherChance[W_Snow - 1])
			FUI_SendMessage(SWeatherChance(W_Fog - 1), M_SETVALUE, CurrentArea\WeatherChance[W_Fog - 1])
			FUI_SendMessage(SWeatherChance(W_Storm - 1), M_SETVALUE, CurrentArea\WeatherChance[W_Storm - 1])
			FUI_SendMessage(SWeatherChance(W_Wind - 1), M_SETVALUE, CurrentArea\WeatherChance[W_Wind - 1])
			Found = False
			For i = 1 To TotalZones + 1
				FUI_SendMessage(CWeatherLink, M_SETINDEX, i)
				Name$ = FUI_SendMessage(CWeatherLink, M_GETCAPTION)
				If Name$ = CurrentArea\WeatherLink$ Then Found = True : Exit
			Next
			If Found = False
				FUI_SendMessage(CWeatherLink, M_SETINDEX, 1)
				CurrentArea\WeatherLink$ = ""
			EndIf
			FUI_SendMessage(SLFogNear, M_SETVALUE, Int(FogNear#))
			FUI_SendMessage(SLFogFar, M_SETVALUE, Int(FogFar#))
			FUI_SendMessage(SLFogR, M_SETVALUE, FogR)
			FUI_SendMessage(SLFogG, M_SETVALUE, FogG)
			FUI_SendMessage(SLFogB, M_SETVALUE, FogB)
			FUI_SendMessage(SLAmbientR, M_SETVALUE, AmbientR)
			FUI_SendMessage(SLAmbientG, M_SETVALUE, AmbientG)
			FUI_SendMessage(SLAmbientB, M_SETVALUE, AmbientB)
			FUI_SendMessage(SDefaultLightPitch, M_SETVALUE, DefaultLightPitch#)
			FUI_SendMessage(SDefaultLightYaw, M_SETVALUE, DefaultLightYaw#)
			FUI_SendMessage(SGravity, M_SETVALUE, CurrentArea\Gravity - 200)
			FUI_SendMessage(SSlopeRestrict, M_SETVALUE, SlopeRestrict#)
		; Other options
		Case 20
			FUI_SendMessage(BZoneOther, M_SETSELECTED, True) : FUI_ShowGadget(GOtherOptions)
			FUI_SendMessage(BPvP, M_SETCHECKED, CurrentArea\PvP)
			FUI_SendMessage(LLoadingTex, M_SETTEXT, "Load image: " + GetFilename$(EditorTexName$(LoadingTexID)))
			FUI_SendMessage(LLoadingMusic, M_SETTEXT, "Load music: " + GetFilename$(EditorMusicName$(LoadingMusicID)))
			FUI_SendMessage(LLargeMapTex, M_SETTEXT, "Map texture: " + GetFilename$(EditorTexName$(MapTexID)))

			If CurrentArea\EntryScript$ = ""
				FUI_SendMessage(CEntryScript, M_SETINDEX, 1)
			Else
				Found = False
				For i = 1 To LoadedScripts + 1
					FUI_SendMessage(CEntryScript, M_SETINDEX, i)
					Name$ = FUI_SendMessage(CEntryScript, M_GETCAPTION)
					If Name$ = CurrentArea\EntryScript$ Then Found = True : Exit
				Next
				If Found = False Then CurrentArea\EntryScript$ = "" : FUI_SendMessage(CEntryScript, M_SETINDEX, 1)
			EndIf
			If CurrentArea\ExitScript$ = ""
				FUI_SendMessage(CExitScript, M_SETINDEX, 1)
			Else
				Found = False
				For i = 1 To LoadedScripts + 1
					FUI_SendMessage(CExitScript, M_SETINDEX, i)
					Name$ = FUI_SendMessage(CExitScript, M_GETCAPTION)
					If Name$ = CurrentArea\ExitScript$ Then Found = True : Exit
				Next
				If Found = False Then CurrentArea\ExitScript$ = "" : FUI_SendMessage(CExitScript, M_SETINDEX, 1)
			EndIf
			
		Case 21 : FUI_SendMessage(BZoneLights, M_SETSELECTED, True) : FUI_ShowGadget(GDynamicC) 
			
	End Select

End Function

; Sets the picking mode for zone meshes
Function UpdateZonePickModes(View)

	If View = 0
		For S.Scenery = Each Scenery : EntityPickMode S\EN, 2 : Next
		For T.Terrain = Each Terrain : EntityPickMode T\EN, 2 : Next
		For W.Water = Each Water : EntityPickMode W\EN, 2 : Next
		For C.ColBox = Each ColBox : EntityPickMode C\EN, 2 : Next
		For SZ.SoundZone = Each SoundZone : EntityPickMode SZ\EN, 2 : Next
		For E.Emitter = Each Emitter : EntityPickMode E\EN, 2 : Next
		For i = 0 To 149
			If TriggerEN(i) <> 0 Then EntityPickMode TriggerEN(i), 2
		Next
		For i = 0 To 99
			If PortalEN(i) <> 0 Then EntityPickMode PortalEN(i), 2
		Next
		For i = 0 To 1999
			If WaypointEN(i) <> 0 Then EntityPickMode WaypointEN(i), 1
		Next
	Else
		For S.Scenery = Each Scenery : EntityPickMode S\EN, 0 : Next
		For T.Terrain = Each Terrain : EntityPickMode T\EN, 0 : Next
		For W.Water = Each Water : EntityPickMode W\EN, 0 : Next
		For C.ColBox = Each ColBox : EntityPickMode C\EN, 0 : Next
		For SZ.SoundZone = Each SoundZone : EntityPickMode SZ\EN, 0 : Next
		For E.Emitter = Each Emitter : EntityPickMode E\EN, 0 : Next
		For i = 0 To 149
			If TriggerEN(i) <> 0 Then EntityPickMode TriggerEN(i), 0
		Next
		For i = 0 To 99
			If PortalEN(i) <> 0 Then EntityPickMode PortalEN(i), 0
		Next
		For i = 0 To 1999
			If WaypointEN(i) <> 0 Then EntityPickMode WaypointEN(i), 0
		Next
		Select View
			Case 1, 2 : For S.Scenery = Each Scenery : EntityPickMode S\EN, 2 : Next
			Case 3, 4 : For T.Terrain = Each Terrain : EntityPickMode T\EN, 2 : Next
			Case 5, 6 : For E.Emitter = Each Emitter : EntityPickMode E\EN, 2 : Next
			Case 7, 8 : For W.Water = Each Water : EntityPickMode W\EN, 2 : Next
			Case 9, 10 : For C.ColBox = Each ColBox : EntityPickMode C\EN, 2 : Next
			Case 11, 12 : For SZ.SoundZone = Each SoundZone : EntityPickMode SZ\EN, 2 : Next
			Case 13, 14
				For i = 0 To 149
					If TriggerEN(i) <> 0 Then EntityPickMode TriggerEN(i), 2
				Next
			Case 15, 16
				For i = 0 To 1999
					If WaypointEN(i) <> 0 Then EntityPickMode WaypointEN(i), 1
				Next
			Case 17, 18
				For i = 0 To 99
					If PortalEN(i) <> 0 Then EntityPickMode PortalEN(i), 2
				Next
		End Select
	EndIf

End Function

; Fills the portal name list for the selected portal area
Function UpdatePortalNameList()

	Number = 0
	FUI_SendMessage(CPortalLinkName, M_RESET)
	FUI_SendMessage(CPortalLinkNameO, M_RESET)
	Area$ = FUI_SendMessage(CPortalLinkArea, M_GETCAPTION)
	If Area$ <> "" And FUI_SendMessage(CPortalLinkArea, M_GETINDEX) > 1
		SA.Area = FindArea(Area$)
		If SA <> Null
			For i = 0 To 99
				If SA\PortalName$[i] <> ""
					FUI_ComboBoxItem(CPortalLinkName, SA\PortalName$[i])
					FUI_ComboBoxItem(CPortalLinkNameO, SA\PortalName$[i])
					Number = Number + 1
				EndIf
			Next
			FUI_SendMessage(CPortalLinkName, M_SETINDEX, 1)
			FUI_SendMessage(CPortalLinkNameO, M_SETINDEX, 1)
		EndIf
	EndIf
	Return Number

End Function

; Allows precise editing of the selected entity
Function PreciseEditSelected()

	TriggerID = -1
	PortalID = -1
	WaypointID = -1
	Sc.Scenery = Object.Scenery(EntityName$(SelectedEN))
	T.Terrain = Object.Terrain(EntityName$(SelectedEN))
	Wa.Water = Object.Water(EntityName$(SelectedEN))
	CB.ColBox = Object.ColBox(EntityName$(SelectedEN))
	SZ.SoundZone = Object.SoundZone(EntityName$(SelectedEN))
	If Left$(EntityName$(SelectedEN), 1) = "T"
		TriggerID = Mid$(EntityName$(SelectedEN), 2)
	ElseIf Left$(EntityName$(SelectedEN), 1) = "P"
		PortalID = Mid$(EntityName$(SelectedEN), 2)
	ElseIf Left$(EntityName$(SelectedEN), 1) = "W"
		WaypointID = Mid$(EntityName$(SelectedEN), 2)
	EndIf
	If Sc <> Null
		ScaleX# = Sc\ScaleX# : ScaleY# = Sc\ScaleY# : ScaleZ# = Sc\ScaleZ#
	ElseIf T <> Null
		ScaleX# = T\ScaleX# : ScaleY# = T\ScaleY# : ScaleZ# = T\ScaleZ#
	ElseIf Wa <> Null
		ScaleX# = Wa\ScaleX# : ScaleY# = 0.0 : ScaleZ# = Wa\ScaleZ#
	ElseIf CB <> Null
		ScaleX# = CB\ScaleX# : ScaleY# = CB\ScaleY# : ScaleZ# = CB\ScaleZ#
	ElseIf SZ <> Null
		ScaleX# = SZ\Radius# : ScaleY# = SZ\Radius# : ScaleZ# = SZ\Radius#
	ElseIf TriggerID > -1
		ScaleX# = CurrentArea\TriggerSize#[TriggerID] : ScaleY# = CurrentArea\TriggerSize#[TriggerID] : ScaleZ# = CurrentArea\TriggerSize#[TriggerID]
	ElseIf WaypointID > -1
		SpawnNum = GetSpawnPoint(WaypointID)
		If SpawnNum > -1
			ScaleX# = CurrentArea\SpawnSize#[SpawnNum] : ScaleY# = CurrentArea\SpawnSize#[SpawnNum] : ScaleZ# = CurrentArea\SpawnSize#[SpawnNum]
		EndIf
	ElseIf PortalID > -1
		ScaleX# = CurrentArea\PortalSize#[PortalID] : ScaleY# = CurrentArea\PortalSize#[PortalID] : ScaleZ# = CurrentArea\PortalSize#[PortalID]
	EndIf

	W = FUI_Window(70, 150, 200, 340, "Precise Object Editing", "", 1, 0)
	FUI_Label(W, 10, 12, "Position X:")
	FUI_Label(W, 10, 42, "Position Y:")
	FUI_Label(W, 10, 72, "Position Z:")
	FUI_Label(W, 10, 102, "Pitch:")
	FUI_Label(W, 10, 132, "Yaw:")
	FUI_Label(W, 10, 162, "Roll:")
	FUI_Label(W, 10, 192, "Scale X:")
	FUI_Label(W, 10, 222, "Scale Y:")
	FUI_Label(W, 10, 252, "Scale Z:")
	SPosX = FUI_Spinner(W, 100, 10,  90, 20, -10000, 10000,  EntityX#(SelectedEN),     0.1,  DTYPE_FLOAT)
	SPosY = FUI_Spinner(W, 100, 40,  90, 20, -10000, 10000,  EntityY#(SelectedEN),     0.1,  DTYPE_FLOAT)
	SPosZ = FUI_Spinner(W, 100, 70,  90, 20, -10000, 10000,  EntityZ#(SelectedEN),     0.1,  DTYPE_FLOAT)
	SRotX = FUI_Spinner(W, 100, 100, 90, 20, -90,    90,     EntityPitch#(SelectedEN), 0.05, DTYPE_FLOAT)
	SRotY = FUI_Spinner(W, 100, 130, 90, 20, -180,   180,    EntityYaw#(SelectedEN),   0.05, DTYPE_FLOAT)
	SRotZ = FUI_Spinner(W, 100, 160, 90, 20, -90,    90,     EntityRoll#(SelectedEN),  0.05, DTYPE_FLOAT)
	SSclX = FUI_Spinner(W, 100, 190, 90, 20, 0.05,   5000.0, ScaleX#,                  0.05, DTYPE_FLOAT)
	SSclY = FUI_Spinner(W, 100, 220, 90, 20, 0.05,   5000.0, ScaleY#,                  0.05, DTYPE_FLOAT)
	SSclZ = FUI_Spinner(W, 100, 250, 90, 20, 0.05,   5000.0, ScaleZ#,                  0.05, DTYPE_FLOAT)
	BDone = FUI_Button(W, 160, 280, 30, 20, "OK")

	FUI_ModalWindow(W)

	; Event loop
	Done = False
	Repeat

		; Events
		For E.Event = Each Event
			Select E\EventID
				; Position
				Case SPosX
					CreateTransformUndo("K_Move", SelectedEN, EntityX#(SelectedEN), EntityY#(SelectedEN), EntityZ#(SelectedEN))
					PositionEntity SelectedEN, E\EventData, EntityY#(SelectedEN), EntityZ#(SelectedEN) : ZoneSaved = False
				Case SPosY
					CreateTransformUndo("K_Move", SelectedEN, EntityX#(SelectedEN), EntityY#(SelectedEN), EntityZ#(SelectedEN))
					PositionEntity SelectedEN, EntityX#(SelectedEN), E\EventData, EntityZ#(SelectedEN) : ZoneSaved = False
				Case SPosZ
					CreateTransformUndo("K_Move", SelectedEN, EntityX#(SelectedEN), EntityY#(SelectedEN), EntityZ#(SelectedEN))
					PositionEntity SelectedEN, EntityX#(SelectedEN), EntityY#(SelectedEN), E\EventData : ZoneSaved = False
				; Rotation
				Case SRotX
					CreateTransformUndo("K_Rotate", SelectedEN, EntityPitch#(SelectedEN), EntityYaw#(SelectedEN), EntityRoll#(SelectedEN))
					RotateEntity SelectedEN, E\EventData, EntityYaw#(SelectedEN), EntityRoll#(SelectedEN) : ZoneSaved = False
				Case SRotY
					CreateTransformUndo("K_Rotate", SelectedEN, EntityPitch#(SelectedEN), EntityYaw#(SelectedEN), EntityRoll#(SelectedEN))
					RotateEntity SelectedEN, EntityPitch#(SelectedEN), E\EventData, EntityRoll#(SelectedEN) : ZoneSaved = False
				Case SRotZ
					CreateTransformUndo("K_Rotate", SelectedEN, EntityPitch#(SelectedEN), EntityYaw#(SelectedEN), EntityRoll#(SelectedEN))
					RotateEntity SelectedEN, EntityPitch#(SelectedEN), EntityYaw#(SelectedEN), E\EventData : ZoneSaved = False
				; Scale
				Case SSclX, SSclY, SSclZ
					ScaleX# = FUI_SendMessageF(SSclX, M_GETVALUE)
					ScaleY# = FUI_SendMessageF(SSclY, M_GETVALUE)
					ScaleZ# = FUI_SendMessageF(SSclZ, M_GETVALUE)
					ZoneScaleEntity(SelectedEN, ScaleX#, ScaleY#, ScaleZ#)
					ZoneSaved = False
				; OK clicked
				Case BDone
					Done = True
			End Select
			Delete E
		Next

		; Render
		FUI_Update()
		Flip()

	Until Done = True

	FlushKeys()
	FUI_DeleteGadget(W)
	SetBuffer(BackBuffer())

End Function

; Duplicates the selected scenery entity
Function DuplicateSelected()

	If SelectedEN <> 0
		SOld.Scenery = Object.Scenery(EntityName$(SelectedEN))
		If SOld <> Null
			SelectedEN = 0
			HideEntity(SelectionBoxEN)
			EntityParent(SelectionBoxEN, 0)
			Sc.Scenery = New Scenery
			Sc\TextureID = SOld\TextureID
			Sc\MeshID = SOld\MeshID
			Sc\EN = CopyEntity(SOld\EN)
			Sc\ScaleX# = SOld\ScaleX#
			Sc\ScaleY# = SOld\ScaleY#
			Sc\ScaleZ# = SOld\ScaleZ#
			NameEntity(Sc\EN, Handle(Sc))
			PositionEntity Sc\EN, EntityX#(SOld\EN), EntityY#(SOld\EN), EntityZ#(SOld\EN)
			RotateEntity Sc\EN, EntityPitch#(SOld\EN), EntityYaw#(SOld\EN), EntityRoll#(SOld\EN)
			ScaleEntity Sc\EN, Sc\ScaleX#, Sc\ScaleY#, Sc\ScaleZ#
			ZoneSelectEntity(Sc\EN, True)
			ZoneSaved = False
			CreateUndo("Duplicate", Handle(Sc), Handle(SOld))
		EndIf
	EndIf

End Function

; Sets the selected entity
Function ZoneSelectEntity(EN, NoUndo = False)

	; Show/hide spawn point range displays
	If SelectedEN <> 0 Then UpdateWaypointRange(SelectedEN)
	UpdateWaypointRange(EN)

	; Create undo point
	If NoUndo = False Then CreateUndo("Select", SelectedEN)

	; Select
	SelectedEN = EN
	ShowEntity(SelectionBoxEN)
	EntityParent(SelectionBoxEN, 0)

	; Find edges of mesh (in case origin is off-centre)
	If Object.Terrain(EntityName$(SelectedEN)) <> Null
		ScaleX# = TerrainSize(SelectedEN) / 2
		ScaleY# = 1.0
		ScaleZ# = TerrainSize(SelectedEN) / 2
	Else
		Result.MeshMinMaxVertices = MeshMinMaxVertices(SelectedEN)
		MinX# = Result\MinX#
		MinY# = Result\MinY#
		MinZ# = Result\MinZ#
		ScaleX# = (Result\MaxX# - Result\MinX#) / 2.0
		ScaleY# = (Result\MaxY# - Result\MinY#) / 2.0
		ScaleZ# = (Result\MaxZ# - Result\MinZ#) / 2.0
		Delete Result
	EndIf

	; Adjust position and scale accordingly
	EntityParent(SelectionBoxEN, EN, False)
	PositionEntity(SelectionBoxEN, MinX# + ScaleX#, MinY# + ScaleY#, MinZ# + ScaleZ#)
	RotateEntity(SelectionBoxEN, 0, 0, 0)
	If ScaleX# < 0.1 Then ScaleX# = 0.1
	If ScaleY# < 0.1 Then ScaleY# = 0.1
	If ScaleZ# < 0.1 Then ScaleZ# = 0.1
	ScaleEntity(SelectionBoxEN, ScaleX#, ScaleY#, ScaleZ#)

	; Update gadgets
	If (ZoneView Mod 2) <> 0 Then UpdateZoneDisplay(ZoneView + 1) Else UpdateZoneDisplay(ZoneView)

End Function

; Deletes an entity
Function ZoneDeleteEntity(EN, NoUndo = False)

	; Do not delete locked scenery objects
	Sc.Scenery = Object.Scenery(EntityName$(EN))
	If Sc <> Null
		If Sc\Locked = True Then Return
	EndIf

	; Unselect if this entity is selected
	If EN = SelectedEN
		UpdateWaypointRange(0)
		SelectedEN = 0
		HideEntity(SelectionBoxEN)
		EntityParent(SelectionBoxEN, 0)
	EndIf

	; Find which sort of object it is
	TriggerID = -1
	PortalID = -1
	WaypointID = -1
	Sc.Scenery = Object.Scenery(EntityName$(EN))
	T.Terrain = Object.Terrain(EntityName$(EN))
	Emi.Emitter = Object.Emitter(EntityName$(EN))
	W.Water = Object.Water(EntityName$(EN))
	CB.ColBox = Object.ColBox(EntityName$(EN))
	SZ.SoundZone = Object.SoundZone(EntityName$(EN))
	If Left$(EntityName$(EN), 1) = "T"
		TriggerID = Mid$(EntityName$(EN), 2)
	ElseIf Left$(EntityName$(EN), 1) = "P"
		PortalID = Mid$(EntityName$(EN), 2)
	ElseIf Left$(EntityName$(EN), 1) = "W"
		WaypointID = Mid$(EntityName$(EN), 2)
	EndIf
	If Sc <> Null
		If NoUndo = False
			CreateUndo("Delete", 1)
			B = CreateUndoBank(51 + Len(Sc\Lightmap$) + Len(Sc\RCTE$))
			PokeByte(B, 0, Sc\SceneryID)
			PokeShort(B, 1, Sc\MeshID)
			PokeByte(B, 3, Sc\AnimationMode)
			PokeFloat(B, 4, EntityX#(Sc\EN, True))
			PokeFloat(B, 8, EntityY#(Sc\EN, True))
			PokeFloat(B, 12, EntityZ#(Sc\EN, True))
			PokeFloat(B, 16, EntityPitch#(Sc\EN, True))
			PokeFloat(B, 20, EntityYaw#(Sc\EN, True))
			PokeFloat(B, 24, EntityRoll#(Sc\EN, True))
			PokeFloat(B, 28, Sc\ScaleX#)
			PokeFloat(B, 32, Sc\ScaleY#)
			PokeFloat(B, 36, Sc\ScaleZ#)
			PokeShort(B, 40, Sc\TextureID)
			PokeByte(B, 42, GetEntityType(Sc\EN))
			If Sc\SceneryID > 0
;				If CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1] <> Null
;					PokeInt(B, 43, CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1]\InventorySize)
;				Else
					PokeInt(B, 43, 0)
;				EndIf
			Else
				PokeInt(B, 43, 0)
			EndIf
			PokeString(B, 47, Sc\Lightmap$)
			PokeString(B, 49 + Len(Sc\Lightmap$), Sc\RCTE$)
		EndIf
		If Sc\SceneryID > 0
;			If CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1] <> Null
;				If CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1]\Inventory <> Null
;					Delete CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1]\Inventory
;				EndIf
;				Delete CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1]
;			EndIf
		EndIf
		UnloadMesh(Sc\MeshID)
		FreeEntity(Sc\EN)
		Delete(Sc)
	ElseIf T <> Null
		If NoUndo = False
			CreateUndo("Delete", 2)
			B = CreateUndoBank(58 + ((TerrainSize(T\EN) + 1) * (TerrainSize(T\EN) + 1) * 4))
			PokeShort(B, 0, T\BaseTexID)
			PokeShort(B, 2, T\DetailTexID)
			PokeFloat(B, 4, EntityX#(T\EN, True))
			PokeFloat(B, 8, EntityY#(T\EN, True))
			PokeFloat(B, 12, EntityZ#(T\EN, True))
			PokeFloat(B, 16, EntityPitch#(T\EN, True))
			PokeFloat(B, 20, EntityYaw#(T\EN, True))
			PokeFloat(B, 24, EntityRoll#(T\EN, True))
			PokeFloat(B, 28, T\ScaleX#)
			PokeFloat(B, 32, T\ScaleY#)
			PokeFloat(B, 36, T\ScaleZ#)
			PokeFloat(B, 40, T\DetailTexScale#)
			PokeInt(B, 44, T\Detail)
			PokeByte(B, 48, T\Morph)
			PokeByte(B, 49, T\Shading)
			PokeInt(B, 50, TerrainSize(T\EN))
			Offset = 54
			For X = 0 To TerrainSize(T\EN)
				For Z = 0 To TerrainSize(T\EN)
					PokeFloat(B, Offset, TerrainHeight#(T\EN, X, Z))
					Offset = Offset + 4
				Next
			Next
		EndIf
		FreeEntity(T\EN)
		UnloadTexture(T\BaseTexID)
		If T\DetailTexID < 65535 Then UnloadTexture(T\DetailTexID)
		Delete(T)
	ElseIf Emi <> Null
		If NoUndo = False
			CreateUndo("Delete", 3)
			B = CreateUndoBank(28 + Len(Emi\ConfigName$))
			PokeShort(B, 0, Emi\TexID)
			PokeFloat(B, 2, EntityX#(Emi\EN, True))
			PokeFloat(B, 6, EntityY#(Emi\EN, True))
			PokeFloat(B, 10, EntityZ#(Emi\EN, True))
			PokeFloat(B, 14, EntityPitch#(Emi\EN, True))
			PokeFloat(B, 18, EntityYaw#(Emi\EN, True))
			PokeFloat(B, 22, EntityRoll#(Emi\EN, True))
			PokeString(B, 26, Emi\ConfigName$)
		EndIf
		RP_FreeEmitter(GetChild(Emi\EN, 1), False, False)
		UnloadTexture(Emi\TexID)
		FreeEntity(Emi\EN)
		Delete(Emi)
	ElseIf W <> Null
		SW.ServerWater = Object.ServerWater(W\ServerWater)
		If NoUndo = False
			CreateUndo("Delete", 4)
			B = CreateUndoBank(38)
			PokeFloat(B, 0, W\TexScale#)
			PokeFloat(B, 4, EntityX#(W\EN, True))
			PokeFloat(B, 8, EntityY#(W\EN, True))
			PokeFloat(B, 12, EntityZ#(W\EN, True))
			PokeFloat(B, 16, W\ScaleX#)
			PokeFloat(B, 20, W\ScaleZ#)
			PokeShort(B, 24, W\TexID)
			PokeByte(B, 26, W\Red)
			PokeByte(B, 27, W\Green)
			PokeByte(B, 28, W\Blue)
			PokeByte(B, 29, W\Opacity)
			PokeInt(B, 30, SW\Damage)
			PokeInt(B, 34, SW\DamageType)
		EndIf
		Delete(SW)
		FreeTexture(W\TexHandle)
		UnloadTexture(W\TexID)
		FreeEntity(W\EN)
		Delete(W)
	ElseIf CB <> Null
		If NoUndo = False
			CreateUndo("Delete", 5)
			B = CreateUndoBank(36)
			PokeFloat(B, 0, EntityX#(CB\EN, True))
			PokeFloat(B, 4, EntityY#(CB\EN, True))
			PokeFloat(B, 8, EntityZ#(CB\EN, True))
			PokeFloat(B, 12, EntityPitch#(CB\EN, True))
			PokeFloat(B, 16, EntityYaw#(CB\EN, True))
			PokeFloat(B, 20, EntityRoll#(CB\EN, True))
			PokeFloat(B, 24, CB\ScaleX#)
			PokeFloat(B, 28, CB\ScaleY#)
			PokeFloat(B, 32, CB\ScaleZ#)
		EndIf
		FreeEntity(CB\EN)
		Delete(CB)
	ElseIf SZ <> Null
		If NoUndo = False
			CreateUndo("Delete", 6)
			B = CreateUndoBank(25)
			PokeFloat(B, 0, EntityX#(SZ\EN, True))
			PokeFloat(B, 4, EntityY#(SZ\EN, True))
			PokeFloat(B, 8, EntityZ#(SZ\EN, True))
			PokeFloat(B, 12, SZ\Radius#)
			PokeShort(B, 16, SZ\SoundID)
			PokeShort(B, 18, SZ\MusicID)
			PokeInt(B, 20, SZ\RepeatTime)
			PokeByte(B, 24, SZ\Volume)
		EndIf
		If SZ\SoundID > 0 And SZ\SoundID < 65535 Then UnloadSound(SZ\SoundID)
		FreeEntity(SZ\EN)
		Delete(SZ)
	ElseIf TriggerID > -1
		If NoUndo = False
			CreateUndo("Delete", 7)



		EndIf
		FreeEntity(TriggerEN(TriggerID)) : TriggerEN(TriggerID) = 0
		CurrentArea\TriggerScript$[TriggerID] = ""
		CurrentArea\TriggerMethod$[TriggerID] = ""
	ElseIf PortalID > -1
		If NoUndo = False
			CreateUndo("Delete", 8)



		EndIf
		FreeEntity(PortalEN(PortalID)) : PortalEN(PortalID) = 0
		CurrentArea\PortalName$[PortalID] = ""
		CurrentArea\PortalLinkArea$[PortalID] = ""
		CurrentArea\PortalLinkName$[PortalID] = ""
	ElseIf WaypointID > -1
		If NoUndo = False
			CreateUndo("Delete", 9)



		EndIf
		; Find waypoints connected to this one, and remove the connections
		For i = 0 To 1999
			If CurrentArea\NextWaypointA[i] = WaypointID
				FreeEntity(WaypointALink(i)) : WaypointALink(i) = 0
				CurrentArea\NextWaypointA[i] = 2000
			EndIf
			If CurrentArea\NextWaypointB[i] = WaypointID
				FreeEntity WaypointBLink(i) : WaypointBLink(i) = 0
				CurrentArea\NextWaypointB[i] = 2000
			EndIf
		Next
		; Remove this waypoint
		SpawnNum = GetSpawnPoint(WaypointID)
		If SpawnNum > -1
			CurrentArea\SpawnMax[SpawnNum] = 0
			CurrentArea\SpawnWaypoint[SpawnNum] = 0
		EndIf
		FreeEntity WaypointEN(WaypointID) : WaypointEN(WaypointID) = 0
		CurrentArea\PrevWaypoint[WaypointID] = 2005
		CurrentArea\NextWaypointA[WaypointID] = 2005
		CurrentArea\NextWaypointB[WaypointID] = 2005
		If WaypointALink(WaypointID) <> 0 Then FreeEntity WaypointALink(WaypointID) : WaypointALink(WaypointID) = 0
		If WaypointBLink(WaypointID) <> 0 Then FreeEntity WaypointBLink(WaypointID) : WaypointBLink(WaypointID) = 0
	EndIf

	; Done
	ZoneSaved = False
	UpdateZoneDisplay(ZoneView - 1)

End Function

; Sets the scale for a zone entity
Function ZoneScaleEntity(EN, ScaleX#, ScaleY#, ScaleZ#, NoUndo = False)

	TriggerID = -1
	PortalID = -1
	WaypointID = -1
	Sc.Scenery = Object.Scenery(EntityName$(EN))
	T.Terrain = Object.Terrain(EntityName$(EN))
	Wa.Water = Object.Water(EntityName$(EN))
	CB.ColBox = Object.ColBox(EntityName$(EN))
	SZ.SoundZone = Object.SoundZone(EntityName$(EN))
	If Left$(EntityName$(EN), 1) = "T"
		TriggerID = Mid$(EntityName$(EN), 2)
	ElseIf Left$(EntityName$(EN), 1) = "P"
		PortalID = Mid$(EntityName$(EN), 2)
	ElseIf Left$(EntityName$(EN), 1) = "W"
		WaypointID = Mid$(EntityName$(EN), 2)
	EndIf
	If Sc <> Null
		If Sc\Locked = False
			If NoUndo = False Then CreateTransformUndo("K_Scale", EN, Sc\ScaleX#, Sc\ScaleY#, Sc\ScaleZ#)
			Sc\ScaleX# = ScaleX# : Sc\ScaleY# = ScaleY# : Sc\ScaleZ# = ScaleZ#
			ScaleEntity Sc\EN, Sc\ScaleX#, Sc\ScaleY#, Sc\ScaleZ#
		EndIf
	ElseIf T <> Null
		If NoUndo = False Then CreateTransformUndo("K_Scale", EN, T\ScaleX#, T\ScaleY#, T\ScaleZ#)
		OldSX# = T\ScaleX#
		OldSZ# = T\ScaleZ#
		T\ScaleX# = ScaleX# : T\ScaleY# = ScaleY# : T\ScaleZ# = ScaleZ#
		ScaleEntity T\EN, T\ScaleX#, T\ScaleY#, T\ScaleZ#
		TranslateEntity T\EN, (OldSX# - T\ScaleX#) * (Float#(TerrainSize(T\EN)) / 2.0), 0.0, (OldSZ# - T\ScaleZ#) * (Float#(TerrainSize(T\EN)) / 2.0)
	ElseIf Wa <> Null
		If NoUndo = False Then CreateTransformUndo("K_Scale", EN, Wa\ScaleX#, 0.0, Wa\ScaleZ#)
		Wa\ScaleX# = ScaleX# : Wa\ScaleZ# = ScaleZ#
		ScaleWater(Wa)
	ElseIf CB <> Null
		If NoUndo = False Then CreateTransformUndo("K_Scale", EN, CB\ScaleX#, CB\ScaleY#, CB\ScaleZ#)
		CB\ScaleX# = ScaleX# : CB\ScaleY# = ScaleY# : CB\ScaleZ# = ScaleZ#
		ScaleEntity CB\EN, CB\ScaleX#, CB\ScaleY#, CB\ScaleZ#
	ElseIf SZ <> Null
		If NoUndo = False Then CreateTransformUndo("K_Scale", EN, SZ\Radius#, SZ\Radius#, SZ\Radius#)
		If ScaleX# = ScaleY#
			ScaleX# = ScaleZ#
		ElseIf ScaleX# = ScaleZ#
			ScaleX# = ScaleY#
		EndIf
		SZ\Radius# = ScaleX#
		ScaleEntity SZ\EN, SZ\Radius#, SZ\Radius#, SZ\Radius#
	ElseIf TriggerID > -1
		If NoUndo = False Then CreateTransformUndo("K_Scale", EN, CurrentArea\TriggerSize#[TriggerID], CurrentArea\TriggerSize#[TriggerID], CurrentArea\TriggerSize#[TriggerID])
		If ScaleX# = ScaleY#
			ScaleX# = ScaleZ#
		ElseIf ScaleX# = ScaleZ#
			ScaleX# = ScaleY#
		EndIf
		CurrentArea\TriggerSize#[TriggerID] = ScaleX#
		ScaleEntity EN, CurrentArea\TriggerSize#[TriggerID], CurrentArea\TriggerSize#[TriggerID], CurrentArea\TriggerSize#[TriggerID]
	ElseIf WaypointID > -1
		If ScaleX# = ScaleY#
			ScaleX# = ScaleZ#
		ElseIf ScaleX# = ScaleZ#
			ScaleX# = ScaleY#
		EndIf
		SpawnNum = GetSpawnPoint(WaypointID)
		If SpawnNum > -1
			If NoUndo = False Then CreateTransformUndo("K_Scale", EN, CurrentArea\SpawnSize#[SpawnNum], CurrentArea\SpawnSize#[SpawnNum], CurrentArea\SpawnSize#[SpawnNum])
			CurrentArea\SpawnSize#[SpawnNum] = ScaleX#
			ScaleEntity EN, CurrentArea\SpawnSize#[SpawnNum], CurrentArea\SpawnSize#[SpawnNum], CurrentArea\SpawnSize#[SpawnNum]
			EntityRadius EN, CurrentArea\SpawnSize#[SpawnNum]
			If WaypointALink(WaypointID) <> 0
				Dist# = EntityDistance#(WaypointEN(WaypointID), WaypointEN(CurrentArea\NextWaypointA[WaypointID]))
				ScaleEntity WaypointALink(WaypointID), 1, 1, Dist#
			EndIf
			If WaypointBLink(WaypointID) <> 0
				Dist# = EntityDistance#(WaypointEN(WaypointID), WaypointEN(CurrentArea\NextWaypointB[WaypointID]))
				ScaleEntity WaypointBLink(WaypointID), 1, 1, Dist#, True
			EndIf
		EndIf
	ElseIf PortalID > -1
		If NoUndo = False Then CreateTransformUndo("K_Scale", EN, CurrentArea\PortalSize#[PortalID], CurrentArea\PortalSize#[PortalID], CurrentArea\PortalSize#[PortalID])
		If ScaleX# = ScaleY#
			ScaleX# = ScaleZ#
		ElseIf ScaleX# = ScaleZ#
			ScaleX# = ScaleY#
		EndIf
		CurrentArea\PortalSize#[PortalID] = ScaleX#
		ScaleEntity(EN, CurrentArea\PortalSize#[PortalID], CurrentArea\PortalSize#[PortalID], CurrentArea\PortalSize#[PortalID])
	EndIf

End Function

; Allows the entire zone and all contents to be scaled
Function ScaleEntireZoneDialog()

	; Create window
	W = FUI_Window(412, 150, 200, 95, "Entire Zone Scaling", "", 1, 0)
	FUI_Label(W, 5, 12, "Scaling factor:")
	SEntireZoneScale = FUI_Spinner(W, 90, 10, 90, 20, 1, 1000, 100, 1, DTYPE_INTEGER, "%")
	BCancel = FUI_Button(W, 80, 40, 50, 20, "Cancel")
	BDone = FUI_Button(W, 140, 40, 40, 20, "OK")
	FUI_ModalWindow(W)

	; Event loop
	Done = 0
	Repeat

		; Events
		For E.Event = Each Event
			Select E\EventID
				Case BCancel
					Done = 2
				Case BDone
					Done = 1
			End Select
			Delete(E)
		Next

		; Render
		FUI_Update()
		Flip()

	Until Done > 0

	; Perform scale
	If Done = 1
		Result = FUI_CustomMessageBox("This operation cannot be undone. Are you sure?", "Scale zone", MB_YESNO)
		If Result = IDYES
			ScaleEntireZone(Float#(FUI_SendMessage(SEntireZoneScale, M_GETVALUE)) / 100.0)
		EndIf
	EndIf

	; Free window
	FlushKeys()
	FUI_DeleteGadget(W)
	SetBuffer(BackBuffer())

End Function

; Does the actual work of scaling a zone and its contents
Function ScaleEntireZone(F#)

	For S.Scenery = Each Scenery
		S\ScaleX# = S\ScaleX# * F#
		S\ScaleY# = S\ScaleY# * F#
		S\ScaleZ# = S\ScaleZ# * F#
		ScaleEntity(S\EN, S\ScaleX#, S\ScaleY#, S\ScaleZ#)
		PositionEntity(S\EN, EntityX#(S\EN) * F#, EntityY#(S\EN) * F#, EntityZ#(S\EN) * F#)
	Next

	For W.Water = Each Water
		W\ScaleX# = W\ScaleX# * F#
		W\ScaleZ# = W\ScaleZ# * F#
		ScaleWater(W)
		PositionEntity(W\EN, EntityX#(W\EN) * F#, EntityY#(W\EN) * F#, EntityZ#(W\EN) * F#)
	Next

	For C.ColBox = Each ColBox
		C\ScaleX# = C\ScaleX# * F#
		C\ScaleY# = C\ScaleY# * F#
		C\ScaleZ# = C\ScaleZ# * F#
		ScaleEntity(C\EN, C\ScaleX#, C\ScaleY#, C\ScaleZ#)
		PositionEntity(C\EN, EntityX#(C\EN) * F#, EntityY#(C\EN) * F#, EntityZ#(C\EN) * F#)
	Next

	For E.Emitter = Each Emitter
		PositionEntity(E\EN, EntityX#(E\EN) * F#, EntityY#(E\EN) * F#, EntityZ#(E\EN) * F#)
	Next

	For SZ.SoundZone = Each SoundZone
		SZ\Radius# = SZ\Radius# * F#
		ScaleEntity(SZ\EN, SZ\Radius#, SZ\Radius#, SZ\Radius#)
		PositionEntity(SZ\EN, EntityX#(SZ\EN) * F#, EntityY#(SZ\EN) * F#, EntityZ#(SZ\EN) * F#)
	Next

	For T.Terrain = Each Terrain
		T\ScaleX# = T\ScaleX# * F#
		T\ScaleY# = T\ScaleY# * F#
		T\ScaleZ# = T\ScaleZ# * F#
		ScaleEntity(T\EN, T\ScaleX#, T\ScaleY#, T\ScaleZ#)
		PositionEntity(T\EN, EntityX#(T\EN) * F#, EntityY#(T\EN) * F#, EntityZ#(T\EN) * F#)
	Next

	For i = 0 To 149
		If CurrentArea\TriggerScript$[i] <> ""
			CurrentArea\TriggerX#[i] = CurrentArea\TriggerX#[i] * F#
			CurrentArea\TriggerY#[i] = CurrentArea\TriggerY#[i] * F#
			CurrentArea\TriggerZ#[i] = CurrentArea\TriggerZ#[i] * F#
			CurrentArea\TriggerSize#[i] = CurrentArea\TriggerSize#[i] * F#
			PositionEntity(TriggerEN(i), CurrentArea\TriggerX#[i], CurrentArea\TriggerY#[i], CurrentArea\TriggerZ#[i])
			ScaleEntity(TriggerEN(i), CurrentArea\TriggerSize#[i], CurrentArea\TriggerSize#[i], CurrentArea\TriggerSize#[i])
		EndIf
	Next

	For i = 0 To 999
		CurrentArea\SpawnSize#[i] = CurrentArea\SpawnSize#[i] * F#
	Next

	For i = 0 To 1999
		If CurrentArea\PrevWaypoint[i] <> 2005
			CurrentArea\WaypointX#[i] = CurrentArea\WaypointX#[i] * F#
			CurrentArea\WaypointY#[i] = CurrentArea\WaypointY#[i] * F#
			CurrentArea\WaypointZ#[i] = CurrentArea\WaypointZ#[i] * F#
			PositionEntity(WaypointEN(i), CurrentArea\WaypointX#[i], CurrentArea\WaypointY#[i], CurrentArea\WaypointZ#[i])
			SpawnNum = GetSpawnPoint(i)
			If SpawnNum > -1
				Size# = CurrentArea\SpawnSize#[SpawnNum]
				ScaleEntity(WaypointEN(i), Size#, Size#, Size#)
				EntityRadius(WaypointEN(i), Size#)
			EndIf
		EndIf
	Next

	For i = 0 To 1999
		If CurrentArea\NextWaypointA[i] < 2000
			NextWP = CurrentArea\NextWaypointA[i]
			ScaleEntity(WaypointALink(i), 1, 1, EntityDistance#(WaypointEN(i), WaypointEN(NextWP)), True)
			PositionEntity(WaypointALink(i), EntityX#(WaypointEN(i)), EntityY#(WaypointEN(i)), EntityZ#(WaypointEN(i)))
			PointEntity(WaypointALink(i), WaypointEN(NextWP))
		EndIf
		If CurrentArea\NextWaypointB[i] < 2000
			NextWP = CurrentArea\NextWaypointB[i]
			ScaleEntity(WaypointBLink(i), 1, 1, EntityDistance#(WaypointEN(i), WaypointEN(NextWP)), True)
			PositionEntity(WaypointBLink(i), EntityX#(WaypointEN(i)), EntityY#(WaypointEN(i)), EntityZ#(WaypointEN(i)))
			PointEntity(WaypointBLink(i), WaypointEN(NextWP))
		EndIf
	Next

	For i = 0 To 99
		If CurrentArea\PortalName$[i] <> ""
			CurrentArea\PortalX#[i] = CurrentArea\PortalX#[i] * F#
			CurrentArea\PortalY#[i] = CurrentArea\PortalY#[i] * F#
			CurrentArea\PortalZ#[i] = CurrentArea\PortalZ#[i] * F#
			CurrentArea\PortalSize#[i] = CurrentArea\PortalSize#[i] * F#
			PositionEntity(PortalEN(i), CurrentArea\PortalX#[i], CurrentArea\PortalY#[i], CurrentArea\PortalZ#[i])
			ScaleEntity(PortalEN(i), CurrentArea\PortalSize#[i], CurrentArea\PortalSize#[i], CurrentArea\PortalSize#[i])
		EndIf
	Next

	ZoneSaved = False

End Function

; Saves the current zone
Function ZoneSave()

	; Get name
	If CurrentArea\Name$ = ""
		CurrentArea\Name$ = AreaNameDialog$()
		If CurrentArea\Name$ = "" Then Return
		TotalZones = TotalZones + 1
		Item = FUI_ComboBoxItem(CZone, CurrentArea\Name$)
		FUI_SendMessage(Item, M_SETDATA, Handle(CurrentArea))
		FUI_SendMessage(CZone, M_SETINDEX, TotalZones + 1)
		FUI_ComboBoxItem(CPortalLinkArea, CurrentArea\Name$)
		FUI_ComboBoxItem(CPortalLinkAreaO, CurrentArea\Name$)
		FUI_ComboBoxItem(CActorStartArea, CurrentArea\Name$)
		FUI_ComboBoxItem(CWeatherLink, CurrentArea\Name$)
	EndIf

	; Update server position/rotation for portals, waypoints, and triggers
	For i = 0 To 1999
		If WaypointEN(i) <> 0
			CurrentArea\WaypointX#[i] = EntityX#(WaypointEN(i))
			CurrentArea\WaypointY#[i] = EntityY#(WaypointEN(i))
			CurrentArea\WaypointZ#[i] = EntityZ#(WaypointEN(i))
		EndIf
	Next
	For i = 0 To 149
		If TriggerEN(i) <> 0
			CurrentArea\TriggerX#[i] = EntityX#(TriggerEN(i))
			CurrentArea\TriggerY#[i] = EntityY#(TriggerEN(i))
			CurrentArea\TriggerZ#[i] = EntityZ#(TriggerEN(i))
		EndIf
	Next
	For i = 0 To 99
		If PortalEN(i) <> 0
			CurrentArea\PortalX#[i] = EntityX#(PortalEN(i))
			CurrentArea\PortalY#[i] = EntityY#(PortalEN(i))
			CurrentArea\PortalZ#[i] = EntityZ#(PortalEN(i))
			CurrentArea\PortalYaw#[i] = EntityYaw#(PortalEN(i))
		EndIf
	Next

	; Update server side water positions
	For W.Water = Each Water
		SW.ServerWater = Object.ServerWater(W\ServerWater)
		SW\X# = EntityX#(W\EN) - (W\ScaleX# / 2.0)
		SW\Y# = EntityY#(W\EN)
		SW\Z# = EntityZ#(W\EN) - (W\ScaleZ# / 2.0)
		SW\Width# = W\ScaleX#
		SW\Depth# = W\ScaleZ#
	Next

	; Save client stuff
	SaveArea(CurrentArea\Name$)

	; Save server stuff
	CurrentArea\Outdoors = Outdoors
	ServerSaveArea(CurrentArea)

	; Done
	ZoneSaved = True

End Function

; Unloads the current zone
Function ZoneUnload()

	; Unload client side stuff
	If SelectedEN <> 0 Then UpdateWaypointRange(SelectedEN)
	EntityParent(SelectionBoxEN, 0)
	UnloadArea()

	; Unload 3D representations of server stuff
	For i = 0 To 149
		If TriggerEN(i) <> 0 Then FreeEntity TriggerEN(i) : TriggerEN(i) = 0
	Next
	For i = 0 To 99
		If PortalEN(i) <> 0 Then FreeEntity PortalEN(i) : PortalEN(i) = 0
	Next
	For i = 0 To 1999
		If WaypointEN(i) <> 0 Then FreeEntity WaypointEN(i) : WaypointEN(i) = 0
		If WaypointALink(i) <> 0 Then FreeEntity WaypointALink(i) : WaypointALink(i) = 0
		If WaypointBLink(i) <> 0 Then FreeEntity WaypointBLink(i) : WaypointBLink(i) = 0
	Next

	; Revert server area object to saved version
	Name$ = CurrentArea\Name$
	If Name$ = ""
		Delete CurrentArea
	Else
		Delete CurrentArea
		Ar.Area = ServerLoadArea(Name$)
		Idx = FUI_SendMessage(CZone, M_GETINDEX)
		For i = 2 To TotalZones + 1
			FUI_SendMessage(CZone, M_SETINDEX, i)
			If Upper$(FUI_SendMessage(CZone, M_GETTEXT)) = Upper$(Name$)
				FUI_SendMessage(FUI_SendMessage(CZone, M_GETSELECTED), M_SETDATA, Handle(Ar))
				Exit
			EndIf
		Next
		FUI_SendMessage(CZone, M_SETINDEX, Idx)
	EndIf

	; Set up as new blank zone
	SetZoneDefaults()

End Function

; Restore default zone settings
Function SetZoneDefaults()

	AmbientR = 100 : AmbientG = 100 : AmbientB = 100
	DefaultLightPitch# = 30.0
	DefaultLightYaw# = 0.0
	Outdoors = True
	SlopeRestrict# = 0.6
	FogNear# = 300.0 : FogFar# = 500.0
	FogR = 0 : FogG = 0 : FogB = 0
	SkyTexID = -1
	StarsTexID = -1
	CloudTexID = -1
	StormCloudTexID = -1
	LoadingTexID = 65535
	LoadingMusicID = 65535
	CameraRange(ZoneCam, 0.1, FogFar# + 10.0)
	CameraFogRange(ZoneCam, FogNear#, FogFar#)
	CameraFogColor(ZoneCam, FogR, FogG, FogB)
	CameraClsColor(ZoneCam, FogR, FogG, FogB)
	EntityAlpha(SkyEN, 0.0)
	EntityAlpha(CloudEN, 0.0)
	ScaleEntity(SkyEN, FogFar#, FogFar#, FogFar#)
	ScaleEntity(CloudEN, FogFar# - 10.0, FogFar# - 10.0, FogFar# - 10.0)
	AmbientLight(AmbientR, AmbientG, AmbientB)
	RotateEntity(DefaultLight, DefaultLightPitch#, DefaultLightYaw#, 0)
	SetViewDistance(ZoneCam, FogNear#, FogFar#)

	; Clear undo list
	Delete Each Undo
	Delete Each UndoWaypointState

	; Unselect
	EntityParent(SelectionBoxEN, 0)
	HideEntity(SelectionBoxEN)
	SelectedEN = 0

End Function

; Creates a range display entity for a spawn point if one is required
Function UpdateWaypointRange(EN)

	EntityParent(WPRangeEN, 0)
	HideEntity(WPRangeEN)
	If EN > 0
		If Left$(EntityName$(EN), 1) = "W"
			SP = GetSpawnPoint(Mid$(EntityName$(EN), 2))
			If SP > -1
				If CurrentArea\SpawnRange#[SP] >= 5.0
					EntityParent(WPRangeEN, EN)
					ShowEntity(WPRangeEN)
					PositionEntity(WPRangeEN, 0, 0, 0)
					ScaleEntity(WPRangeEN, CurrentArea\SpawnRange#[SP], CurrentArea\SpawnRange#[SP], CurrentArea\SpawnRange#[SP], True)
				EndIf
			EndIf
		EndIf
	EndIf

End Function

; Creates an entry in the undo list
Function CreateUndo(N$, I, I2 = 0, X# = 0.0, Y# = 0.0, Z# = 0.0)

	U.Undo = New Undo
	U\Action$ = N$
	U\Info = I
	U\Info2 = I2
	U\InfoX# = X#
	U\InfoY# = Y#
	U\InfoZ# = Z#

End Function

; Creates an undo point for continual movement/rotation/scaling
Function CreateTransformUndo(N$, I, X#, Y#, Z#)

	; If previous undo is of the same type, do not create another
	Done = False
	U.Undo = Last Undo
	If U <> Null
		If U\Action$ = N$
			If U\Info = I Then Done = True
		EndIf
	EndIf

	; If a valid previous undo was not found, create a new one
	If Done = False Then CreateUndo(N$, I, 0, X#, Y#, Z#)

End Function

; Creates an undo point which stores the current waypoint links state
Function CreateWPUndo(N$, I = 0, I2 = 0)

	U.Undo = New Undo
	U\Action$ = N$
	U\Info = I
	U\Info2 = I2
	U\WPS = New UndoWaypointState
	For i = 0 To 1999
		U\WPS\PrevWaypoint[i] = CurrentArea\PrevWaypoint[i]
		U\WPS\NextWaypointA[i] = CurrentArea\NextWaypointA[i]
		U\WPS\NextWaypointB[i] = CurrentArea\NextWaypointB[i]
	Next

End Function

; Creates a bank to contain extra data for the latest undo point
Function CreateUndoBank(Size)

	U.Undo = Last Undo
	If U = Null Then Return 0

	U\ExtraInfo = CreateBank(Size)
	Return U\ExtraInfo

End Function

; Performs one level of undo
Function PerformUndo()

	; Find undo info
	U.Undo = Last Undo
	If U = Null Then Return

	; Undo action type
	Select U\Action$
		; Lock/unlock scenery
		Case "Lock"
			Sc.Scenery = Object.Scenery(U\Info)
			If Sc <> Null
				Sc\Locked = Not Sc\Locked
				UpdateZoneDisplay(ZoneView)
			EndIf
		; Return to previous waypoint links state
		Case "WP Link"
			PerformWPUndo(U\WPS)
		; Return to previous zone mode and selected object
		Case "Mode"
			UpdateZoneDisplay(U\Info)
			If U\Info2 <> 0 Then ZoneSelectEntity(U\Info2, True)
		; Return object to previous position
		Case "Move", "M_Move", "K_Move"
			PositionEntity(U\Info, U\InfoX#, U\InfoY#, U\InfoZ#)
		; Return object to previous rotation
		Case "Rotate", "M_Rotate", "K_Rotate"
			RotateEntity(U\Info, U\InfoX#, U\InfoY#, U\InfoZ#)
		; Return object to previous scale
		Case "Scale", "K_Scale"
			ZoneScaleEntity(U\Info, U\InfoX#, U\InfoY#, U\InfoZ#, True)
		; Undo selection of an object
		Case "Select"
			; Nothing was selected before
			If U\Info = 0
				UpdateWaypointRange(0)
				EntityParent(SelectionBoxEN, 0)
				HideEntity(SelectionBoxEN)
				SelectedEN = 0
			; An entity was selected before
			Else
				ZoneSelectEntity(U\Info, True)
			EndIf
		; Redo selection of an unselected object
		Case "Unselect"
			ZoneSelectEntity(U\Info, True)
		; Remove a created object
		Case "Create"
			ZoneDeleteEntity(U\Info, True)
		; Remove a duplicated object
		Case "Duplicate"
			; Delete duplicate object
			Sc.Scenery = Object.Scenery(U\Info)
			ZoneDeleteEntity(Sc\EN, True)
			; Reselect original object
			Sc.Scenery = Object.Scenery(U\Info2)
			ZoneSelectEntity(Sc\EN, True)
		; Recreate a deleted object
		Case "Delete"
			Select U\Info
				; Scenery object
				Case 1
					; Recover information from extra info bank
					Sc.Scenery = New Scenery
					Sc\SceneryID = PeekByte(U\ExtraInfo, 0)
					Sc\MeshID = PeekShort(U\ExtraInfo, 1)
					Sc\AnimationMode = PeekByte(U\ExtraInfo, 3)
					X# = PeekFloat#(U\ExtraInfo, 4)
					Y# = PeekFloat#(U\ExtraInfo, 8)
					Z# = PeekFloat#(U\ExtraInfo, 12)
					Pitch# = PeekFloat#(U\ExtraInfo, 16)
					Yaw# = PeekFloat#(U\ExtraInfo, 20)
					Roll# = PeekFloat#(U\ExtraInfo, 24)
					Sc\ScaleX# = PeekFloat#(U\ExtraInfo, 28)
					Sc\ScaleY# = PeekFloat#(U\ExtraInfo, 32)
					Sc\ScaleZ# = PeekFloat#(U\ExtraInfo, 36)
					Sc\TextureID = PeekShort(U\ExtraInfo, 40)
					Collides = PeekByte(U\ExtraInfo, 42)
					InventorySize = PeekInt(U\ExtraInfo, 43)
					Sc\Lightmap$ = PeekString$(U\ExtraInfo, 47)
					Sc\RCTE$ = PeekString$(U\ExtraInfo, 49 + Len(Sc\Lightmap$))

					; Load the mesh
					Sc\EN = GetMesh(Sc\MeshID, False)

					; Set up entity
					If Len(Sc\RCTE$) > 5
						Select Left$(Sc\RCTE$, 5)
							Case "_TREE"
								swingsty = Int(Mid$(Sc\RCTE$, 6, 1))
								evergrn = Int(Mid$(Sc\RCTE$, 7, 1))
								Sc\EN = LoadTree("", evergrn, Sc\EN, swingsty)
							Case "_GRSS"
								swingsty = Int(Mid$(Sc\RCTE$, 6, 1))
								evergrn = Int(Mid$(Sc\RCTE$, 7, 1))
								Sc\EN = LoadGrass("", evergrn, Sc\EN, swingsty)
						End Select
					EndIf
					PositionEntity Sc\EN, X#, Y#, Z# : RotateEntity Sc\EN, Pitch#, Yaw#, Roll#
					ScaleEntity Sc\EN, Sc\ScaleX#, Sc\ScaleY#, Sc\ScaleZ#
					If Sc\SceneryID > 0
;						CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1] = New OwnedScenery
;						If InventorySize > 0
;							CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1]\InventorySize = InventorySize
;							CurrentArea\Instances[0]\OwnedScenery[Sc\SceneryID - 1]\Inventory = New Inventory
;						EndIf
					EndIf
					If Sc\Lightmap$ <> ""
						LMap = LoadTexture("Data\Textures\Lightmaps\" + Sc\Lightmap$)
						TextureCoords(LMap, 1)
						EntityTexture(Sc\EN, LMap, 0, 1)
						FreeTexture(LMap)
					EndIf
					If Sc\TextureID < 65535 Then EntityTexture Sc\EN, GetTexture(Sc\TextureID)
					NameEntity Sc\EN, Handle(Sc)
					EntityType(Sc\EN, Collides)
					If Collides = C_Sphere
						EntityPickMode(Sc\EN, 1)
						MaxLength# = MeshWidth#(Sc\EN) * Sc\ScaleX#
						If MeshDepth#(Sc\EN) * Sc\ScaleZ# > MaxLength# Then MaxLength# = MeshDepth#(Sc\EN) * Sc\ScaleZ#
						EntityRadius(Sc\EN, MaxLength# / 2.0, (MeshHeight#(Sc\EN) * Sc\ScaleY#) / 2.0)
					ElseIf Collides = C_Triangle
						EntityPickMode(Sc\EN, 2)
					ElseIf Collides = C_Box
						EntityPickMode(Sc\EN, 3)
						Width# = MeshWidth#(Sc\EN) * Sc\ScaleX#
						Height# = MeshHeight#(Sc\EN) * Sc\ScaleY#
						Depth# = MeshDepth#(Sc\EN) * Sc\ScaleZ#
						EntityBox(Sc\EN, Width# / -2.0, Height# / -2.0, Depth# / -2.0, Width#, Height#, Depth#)
					EndIf
					Tree_HideAll(1)

					ZoneSelectEntity(Sc\EN, True)
				; Terrain
				Case 2
					; Recover information from extra info bank
					T.Terrain = New Terrain
					T\BaseTexID = PeekShort(U\ExtraInfo, 0)
					T\DetailTexID = PeekShort(U\ExtraInfo, 2)
					X# = PeekFloat#(U\ExtraInfo, 4)
					Y# = PeekFloat#(U\ExtraInfo, 8)
					Z# = PeekFloat#(U\ExtraInfo, 12)
					Pitch# = PeekFloat#(U\ExtraInfo, 16)
					Yaw# = PeekFloat#(U\ExtraInfo, 20)
					Roll# = PeekFloat#(U\ExtraInfo, 24)
					T\ScaleX# = PeekFloat#(U\ExtraInfo, 28)
					T\ScaleY# = PeekFloat#(U\ExtraInfo, 32)
					T\ScaleZ# = PeekFloat#(U\ExtraInfo, 36)
					T\DetailTexScale# = PeekFloat#(U\ExtraInfo, 40)
					T\Detail = PeekInt(U\ExtraInfo, 44)
					T\Morph = PeekByte(U\ExtraInfo, 48)
					T\Shading = PeekByte(U\ExtraInfo, 49)
					GridSize = PeekInt(U\ExtraInfo, 50)
					Offset = 54

					; Set up entity
					T\EN = CreateTerrain(GridSize)
					For LoopX = 0 To GridSize
						For LoopZ = 0 To GridSize
							ModifyTerrain(T\EN, LoopX, LoopZ, PeekFloat#(U\ExtraInfo, Offset), False)
							Offset = Offset + 4
						Next
					Next
					PositionEntity T\EN, X#, Y#, Z# : RotateEntity T\EN, Pitch#, Yaw#, Roll#
					ScaleEntity T\EN, T\ScaleX#, T\ScaleY#, T\ScaleZ#
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
					EndIf
					TerrainDetail(T\EN, T\Detail, T\Morph)
					TerrainShading(T\EN, T\Shading)
					EntityType T\EN, C_Triangle
					EntityPickMode T\EN, 2
					NameEntity T\EN, Handle(T)

					ZoneSelectEntity(T\EN, True)
				; Emitter
				Case 3
					; Recover information from extra info bank
					Emi.Emitter = New Emitter
					Emi\TexID = PeekShort(U\ExtraInfo, 0)
					X# = PeekFloat#(U\ExtraInfo, 2)
					Y# = PeekFloat#(U\ExtraInfo, 6)
					Z# = PeekFloat#(U\ExtraInfo, 10)
					Pitch# = PeekFloat#(U\ExtraInfo, 14)
					Yaw# = PeekFloat#(U\ExtraInfo, 18)
					Roll# = PeekFloat#(U\ExtraInfo, 22)
					Emi\ConfigName$ = PeekString$(U\ExtraInfo, 26)

					; Set up entity
					Emi\EN = CreateCone() : ScaleMesh Emi\EN, 3, 3, 3 : EntityAlpha Emi\EN, 0.5
					Texture = GetTexture(Emi\TexID)
					Emi\Config = RP_LoadEmitterConfig("Data\Emitter Configs\" + Emi\ConfigName$ + ".rpc", Texture, ZoneCam)
					EmitterEN = RP_CreateEmitter(Emi\Config)
					EntityParent EmitterEN, Emi\EN, False
					PositionEntity Emi\EN, X#, Y#, Z# : RotateEntity Emi\EN, Pitch#, Yaw#, Roll#
					EntityPickMode Emi\EN, 2
					NameEntity Emi\EN, Handle(Emi)

					ZoneSelectEntity(Emi\EN, True)
				; Water
				Case 4
					; Recover information from extra info bank
					W.Water = New Water
					SW.ServerWater = New ServerWater
					W\TexScale# = PeekFloat#(U\ExtraInfo, 0)
					X# = PeekFloat#(U\ExtraInfo, 4)
					Y# = PeekFloat#(U\ExtraInfo, 8)
					Z# = PeekFloat#(U\ExtraInfo, 12)
					W\ScaleX# = PeekFloat#(U\ExtraInfo, 16)
					W\ScaleZ# = PeekFloat#(U\ExtraInfo, 20)
					W\TexID = PeekShort(U\ExtraInfo, 24)
					W\Red = PeekByte(U\ExtraInfo, 26)
					W\Green = PeekByte(U\ExtraInfo, 27)
					W\Blue = PeekByte(U\ExtraInfo, 28)
					W\Opacity = PeekByte(U\ExtraInfo, 29)
					SW\Damage = PeekInt(U\ExtraInfo, 30)
					SW\DamageType = PeekInt(U\ExtraInfo, 34)
					W\ServerWater = Handle(SW)

					; Set up entity
					W\TexHandle = GetTexture(W\TexID, True)
					XDivs = Ceil(W\ScaleX# / 15.0)
					ZDivs = Ceil(W\ScaleZ# / 15.0)
					If XDivs > 100 Then XDivs = 100
					If ZDivs > 100 Then ZDivs = 100
					W\EN = CreateSubdividedPlane(XDivs, ZDivs, W\ScaleX#, W\ScaleZ#)
					ScaleEntity W\EN, W\ScaleX#, 1.0, W\ScaleZ#
					PositionEntity W\EN, X#, Y#, Z#
					ScaleTexture W\TexHandle, W\TexScale#, W\TexScale#
					EntityTexture W\EN, W\TexHandle
					If W\Opacity >= 100
						EntityFX(W\EN, 1 + 16)
					Else
						EntityFX(W\EN, 16)
					EndIf
					Alpha# = Float#(W\Opacity) / 100.0
					If Alpha# > 1.0 Then Alpha# = 1.0
					EntityAlpha(W\EN, Alpha#)
					EntityBox W\EN, W\ScaleX# / -2.0, -1.0, W\ScaleZ# / -2.0, W\ScaleX#, 2.0, W\ScaleZ#
					NameEntity W\EN, Handle(W)

					ZoneSelectEntity(W\EN, True)
				; Collision box
				Case 5
					; Recover information from extra info bank
					CB.ColBox = New ColBox
					X# = PeekFloat#(U\ExtraInfo, 0)
					Y# = PeekFloat#(U\ExtraInfo, 4)
					Z# = PeekFloat#(U\ExtraInfo, 8)
					Pitch# = PeekFloat#(U\ExtraInfo, 12)
					Yaw# = PeekFloat#(U\ExtraInfo, 16)
					Roll# = PeekFloat#(U\ExtraInfo, 20)
					CB\ScaleX# = PeekFloat#(U\ExtraInfo, 24)
					CB\ScaleY# = PeekFloat#(U\ExtraInfo, 28)
					CB\ScaleZ# = PeekFloat#(U\ExtraInfo, 32)

					; Set up entity
					CB\EN = CreateCube()
					EntityAlpha CB\EN, 0.4
					EntityColor CB\EN, 255, 0, 0
					PositionEntity CB\EN, X#, Y#, Z#
					RotateEntity CB\EN, Pitch#, Yaw#, Roll#
					ScaleEntity CB\EN, CB\ScaleX#, CB\ScaleY#, CB\ScaleZ#
					EntityBox CB\EN, -CB\ScaleX#, -CB\ScaleY#, -CB\ScaleZ#, CB\ScaleX# * 2.0, CB\ScaleY# * 2.0, CB\ScaleZ# * 2.0
					EntityType CB\EN, C_Box
					NameEntity CB\EN, Handle(CB)

					ZoneSelectEntity(CB\EN, True)
				; Sound zone
				Case 6
					; Recover information from extra info bank
					SZ.SoundZone = New SoundZone
					X# = PeekFloat#(U\ExtraInfo, 0)
					Y# = PeekFloat#(U\ExtraInfo, 4)
					Z# = PeekFloat#(U\ExtraInfo, 8)
					SZ\Radius# = PeekFloat#(U\ExtraInfo, 12)
					SZ\SoundID = PeekShort(U\ExtraInfo, 16)
					SZ\MusicID = PeekShort(U\ExtraInfo, 18)
					SZ\RepeatTime = PeekInt(U\ExtraInfo, 20)
					SZ\Volume = PeekByte(U\ExtraInfo, 24)

					; Set up entity
					SZ\EN = CreateSphere()
					EntityAlpha SZ\EN, 0.5
					EntityColor SZ\EN, 255, 255, 0
					ScaleEntity SZ\EN, SZ\Radius#, SZ\Radius#, SZ\Radius#
					PositionEntity SZ\EN, X#, Y#, Z#
					If SZ\SoundID <> 65535
						SZ\LoadedSound = GetSound(SZ\SoundID)
						SZ\Is3D = Asc(Right$(GetSoundName$(SZ\SoundID), 1))
					Else
						SZ\MusicFilename$ = "Data\Music\" + GetMusicName$(SZ\MusicID)
					EndIf
					NameEntity SZ\EN, Handle(SZ)

					ZoneSelectEntity(SZ\EN, True)
			End Select
	End Select

	; Remove entry from list
	If U\ExtraInfo <> 0 Then FreeBank(U\ExtraInfo)
	If U\WPS <> Null Then Delete(U\WPS)
	Delete(U)

End Function

; Restores a to a saved waypoint links state
Function PerformWPUndo(WPS.UndoWaypointState)

	; Restore links in server area data
	For i = 0 To 1999
		CurrentArea\PrevWaypoint[i] = WPS\PrevWaypoint[i]
		CurrentArea\NextWaypointA[i] = WPS\NextWaypointA[i]
		CurrentArea\NextWaypointB[i] = WPS\NextWaypointB[i]
	Next

	; Restore 3D link display
	For i = 0 To 1999
		If WaypointALink(i) <> 0 Then FreeEntity WaypointALink(i) : WaypointALink(i) = 0
		If WaypointBLink(i) <> 0 Then FreeEntity WaypointBLink(i) : WaypointBLink(i) = 0
		If CurrentArea\NextWaypointA[i] < 2000
			NextWP = CurrentArea\NextWaypointA[i]
			WaypointALink(i) = CreateCube()
			EntityColor WaypointALink(i), 0, 100, 255
			ScaleMesh WaypointALink(i), 0.1, 0.1, 0.5
			PositionMesh WaypointALink(i), 0, 0, 0.5
			ScaleEntity WaypointALink(i), 1, 1, EntityDistance#(WaypointEN(i), WaypointEN(NextWP)), True
			PositionEntity WaypointALink(i), EntityX#(WaypointEN(i)), EntityY#(WaypointEN(i)), EntityZ#(WaypointEN(i))
			PointEntity WaypointALink(i), WaypointEN(NextWP)
		EndIf
		If CurrentArea\NextWaypointB[i] < 2000
			NextWP = CurrentArea\NextWaypointB[i]
			WaypointBLink(i) = CreateCube()
			EntityColor WaypointBLink(i), 255, 100, 0
			ScaleMesh WaypointBLink(i), 0.1, 0.1, 0.5
			PositionMesh WaypointBLink(i), 0, 0, 0.5
			ScaleEntity WaypointBLink(i), 1, 1, EntityDistance#(WaypointEN(i), WaypointEN(NextWP)), True
			PositionEntity WaypointBLink(i), EntityX#(WaypointEN(i)), EntityY#(WaypointEN(i)), EntityZ#(WaypointEN(i))
			PointEntity WaypointBLink(i), WaypointEN(NextWP)
		EndIf
	Next

End Function

; Gets the first (and hopefully only) spawn point which is connected with a given waypoint
Function GetSpawnPoint(Waypoint)

	For i = 0 To 999
		If CurrentArea\SpawnWaypoint[i] = Waypoint And CurrentArea\SpawnMax[i] > 0 Then Return i
	Next
	Return -1

End Function

; Repositions the links between a waypoint and its branches (RECURSIVE)
Function RepositionWaypointLinks(WP, Parent = True)

	WPA = CurrentArea\NextWaypointA[WP]
	WPB = CurrentArea\NextWaypointB[WP]
	If WPA < 2000
		PositionEntity WaypointALink(WP), EntityX#(WaypointEN(WP)), EntityY#(WaypointEN(WP)), EntityZ#(WaypointEN(WP))
		ScaleEntity WaypointALink(WP), 1, 1, EntityDistance#(WaypointEN(WP), WaypointEN(WPA)), True
		PointEntity WaypointALink(WP), WaypointEN(WPA)
	EndIf
	If WPB < 2000
		PositionEntity WaypointBLink(WP), EntityX#(WaypointEN(WP)), EntityY#(WaypointEN(WP)), EntityZ#(WaypointEN(WP))
		ScaleEntity WaypointBLink(WP), 1, 1, EntityDistance#(WaypointEN(WP), WaypointEN(WPB)), True
		PointEntity WaypointBLink(WP), WaypointEN(WPB)
	EndIf
	If Parent = True
		For i = 0 To 1999
			If CurrentArea\NextWaypointA[i] = WP Or CurrentArea\NextWaypointB[i] = WP Then RepositionWaypointLinks(i, False)
		Next
	EndIf

End Function

; Gets the stripped filename from a path
Function GetFilename$(Path$)

	For i = Len(Path$) To 1 Step -1
		If Mid$(Path$, i, 1) = "\" Or Mid$(Path$, i, 1) = "/" Then Return Mid$(Path$, i + 1)
	Next
	Return Path$

End Function

; Displays the saving dialog
Function SaveDialog()

	If ItemsSaved = True And ActorsSaved = True And FactionsSaved = True And ParticlesSaved = True And DamageTypesSaved = True And ZoneSaved = True And AnimsSaved = True
		If StatsSaved = True And SpellsSaved = True And InterfaceSaved = True And ProjectilesSaved = True And EnvironmentSaved = True
			Return True
		EndIf
	EndIf

	; Create window
	W = FUI_Window((GraphicsWidth() / 2) - 200, (GraphicsHeight() / 2) - 200, 400, 400, "Save Changes?", 0, WS_TITLEBAR, 1)
	BSaveSelected = FUI_Button(W, 290, 250, 90, 20, "Save selected")
	BSaveAll      = FUI_Button(W, 290, 280, 90, 20, "Save all")
	BQuit         = FUI_Button(W, 290, 310, 90, 20, "Quit")
	BCancel       = FUI_Button(W, 290, 340, 90, 20, "Cancel")
	List = FUI_ListBox(W, 20, 20, 250, 340)

	; Add items to list
	If ItemsSaved       = False Then FUI_ListBoxItem(List, "Items")
	If ActorsSaved      = False Then FUI_ListBoxItem(List, "Actors")
	If ProjectilesSaved = False Then FUI_ListBoxItem(List, "Projectiles")
	If FactionsSaved    = False Then FUI_ListBoxItem(List, "Factions")
	If AnimsSaved       = False Then FUI_ListBoxItem(List, "Animation sets")
	If StatsSaved       = False Then FUI_ListBoxItem(List, "Attributes")
	If ParticlesSaved   = False Then FUI_ListBoxItem(List, "Particles")
	If DamageTypesSaved = False Then FUI_ListBoxItem(List, "Damage types")
	If EnvironmentSaved = False Then FUI_ListBoxItem(List, "Days & seasons")
	If ZoneSaved        = False Then FUI_ListBoxItem(List, "Current zone")
	If SpellsSaved      = False Then FUI_ListBoxItem(List, "Abilities")
	If InterfaceSaved   = False Then FUI_ListBoxItem(List, "Interface")

	; Event loop
	Result = -1
	While Result < 0

		For E.Event = Each Event
			Select E\EventID

				; Window closed
				Case W
					If Lower$(E\EventData$) = "closed" Then Result = False
				; Cancel hit
				Case BCancel
					Result = False
				; Quit hit :-)
				Case BQuit
					Result = True
				; Save selected hit
				Case BSaveSelected
					Select FUI_SendMessage(List, M_GETCAPTION)
						Case "Items"
							SaveItems("Data\Server Data\Items.dat") : ItemsSaved = True
						Case "Actors"
							SaveActors("Data\Server Data\Actors.dat") : ActorsSaved = True
						Case "Projectiles"
							SaveProjectiles("Data\Server Data\Projectiles.dat") : ProjectilesSaved = True
						Case "Factions"
							SaveFactions("Data\Server Data\Factions.dat") : FactionsSaved = True
						Case "Animation sets"
							SaveAnimSets("Data\Game Data\Animations.dat") : AnimsSaved = True
						Case "Attributes"
							SaveAttributes("Data\Server Data\Attributes.dat") : StatsSaved = True
						Case "Particles"
							For EmC.RP_EmitterConfig = Each RP_EmitterConfig
								RP_SaveEmitterConfig(Handle(EmC), "Data\Emitter Configs\" + EmC\Name$ + ".rpc")
							Next
							ParticlesSaved = True
						Case "Damage types"
							F = WriteFile("Data\Server Data\Damage.dat")
								For i = 0 To 19
									WriteString F, DamageTypes$(i)
								Next
							CloseFile(F)
							DamageTypesSaved = True
						Case "Days & seasons"
							SaveEnvironment(True)
							SaveSuns()
							EnvironmentSaved = True
						Case "Current zone"
							ZoneSave()
						Case "Abilities"
							SaveSpells("Data\Server Data\Spells.dat") : SpellsSaved = True
						Case "Interface"
							If Chat\Texture <> 65535
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
							EndIf
							SaveInterfaceSettings("Data\Game Data\Interface.dat")
							If ChatBar <> Null Then Delete ChatBar
							InterfaceSaved = True
					End Select
					FUI_SendMessage(List, M_DELETEINDEX, FUI_SendMessage(List, M_GETSELECTED))
					FUI_SendMessage(List, M_SETINDEX, 1)
				; Save all hit
				Case BSaveAll
					If ItemsSaved = False Then SaveItems("Data\Server Data\Items.dat")
					If ActorsSaved = False Then SaveActors("Data\Server Data\Actors.dat")
					If ProjectilesSaved = False Then SaveProjectiles("Data\Server Data\Projectiles.dat")
					If FactionsSaved = False Then SaveFactions("Data\Server Data\Factions.dat")
					If AnimsSaved = False Then SaveAnimSets("Data\Game Data\Animations.dat")
					If StatsSaved = False Then SaveAttributes("Data\Server Data\Attributes.dat")
					If ParticlesSaved = False
						For EmC.RP_EmitterConfig = Each RP_EmitterConfig
							RP_SaveEmitterConfig(Handle(EmC), "Data\Emitter Configs\" + EmC\Name$ + ".rpc")
						Next
					EndIf
					If DamageTypesSaved = False
						F = WriteFile("Data\Server Data\Damage.dat")
							For i = 0 To 19
								WriteString F, DamageTypes$(i)
							Next
						CloseFile(F)
					EndIf
					If EnvironmentSaved = False
						SaveEnvironment(True)
						SaveSuns()
					EndIf
					If ZoneSaved = False Then ZoneSave()
					If SpellsSaved = False Then SaveSpells("Data\Server Data\Spells.dat")
					If InterfaceSaved = False
						If Chat\Texture <> 65535
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
						EndIf
						SaveInterfaceSettings("Data\Game Data\Interface.dat")
						If ChatBar <> Null Then Delete ChatBar
					EndIf
					Result = True
			End Select
			Delete E
		Next

		FUI_Update()
		Flip(0)

	Wend

	; Return result
	FUI_DeleteGadget(W)
	Return Result

End Function

; Area save as name dialog
Function AreaNameDialog$()

	W = FUI_Window(0, 0, 150, 80, "Enter Zone Name", "", 1, 0)
	TAreaName = FUI_TextBox(W, 10, 5, 130, 20)
	BDone = FUI_Button(W, 110, 30, 30, 20, "OK")

	FUI_ModalWindow(W)
	FUI_CenterWindow(W)

	; Event loop
	Done = False
	Repeat

		If FUI_SendMessage(TAreaName, M_GETCAPTION) = ""
			FUI_DisableGadget(BDone)
		Else
			FUI_EnableGadget(BDone)
		EndIf

		; Events
		For E.Event = Each Event
			Select E\EventID
				; OK clicked
				Case BDone
					Result$ = FUI_SendMessage(TAreaName, M_GETCAPTION)
					AlreadyExists = False
					For S.Area = Each Area
						If Upper$(S\Name$) = Upper$(Result$)
							FUI_CustomMessageBox("A zone with that name already exists!", "Error", MB_OK)
							AlreadyExists = True
							Exit
						EndIf
					Next
					If AlreadyExists = False Then Done = True
			End Select
			Delete E
		Next

		; Render
		FUI_Update()
		Flip()

	Until Done = True

	FlushKeys
	FUI_DeleteGadget(W)
	Return Result$

End Function

; Emitter creation name dialog
Function EmitterNameDialog$()

	W = FUI_Window(0, 0, 150, 80, "Enter Emitter Name", "", 1, 0)
	TEmitterName = FUI_TextBox(W, 10, 5, 130, 20)
	BDone = FUI_Button(W, 110, 30, 30, 20, "OK")

	FUI_ModalWindow(W)
	FUI_CenterWindow(W)

	; Event loop
	Done = False
	Repeat

		If FUI_SendMessage(TEmitterName, M_GETCAPTION) = ""
			FUI_DisableGadget(BDone)
		Else
			FUI_EnableGadget(BDone)
		EndIf

		; Events
		For E.Event = Each Event
			Select E\EventID
				; OK clicked
				Case BDone
					Result$ = FUI_SendMessage(TEmitterName, M_GETCAPTION)
					Done = True
			End Select
			Delete E
		Next

		; Render
		FUI_Update()
		Flip()

	Until Done = True

	FlushKeys()
	FUI_DeleteGadget(W)
	Return Result$

End Function

; Allows the user to change the fixed attributes used by the engine
Function FixedAttributeDialog()

	; Create window
	W = FUI_Window(0, 0, 250, 250, "Set Fixed Attributes", "", 1, 0)
	FUI_Label(W, 10, 12, "Health:")
	FUI_Label(W, 10, 42, "Energy:")
	FUI_Label(W, 10, 72, "Breath:")
	FUI_Label(W, 10, 102, "Toughness:")
	FUI_Label(W, 10, 132, "Strength:")
	FUI_Label(W, 10, 162, "Speed:")
	CHealth = FUI_ComboBox(W, 90, 10, 120, 20, 20)
	CEnergy = FUI_ComboBox(W, 90, 40, 120, 20, 20)
	CBreath = FUI_ComboBox(W, 90, 70, 120, 20, 20)
	CToughness = FUI_ComboBox(W, 90, 100, 120, 20, 20)
	CStrength = FUI_ComboBox(W, 90, 130, 120, 20, 20)
	CSpeed = FUI_ComboBox(W, 90, 160, 120, 20, 20)
	BCancel = FUI_Button(W, 100, 190, 60, 25, "Cancel")
	BSave = FUI_Button(W, 180, 190, 60, 25, "Save")
	FUI_ModalWindow(W)
	FUI_CenterWindow(W)

	; Load current fixed attributes
	F = ReadFile("Data\Server Data\Fixed Attributes.dat")
	If F = 0
		FUI_CustomMessageBox("Could not open Data\Server Data\Fixed Attributes.dat!", "Error", MB_OK)
		Return
	EndIf
	HealthStat = ReadShort(F)
	EnergyStat = ReadShort(F)
	BreathStat = ReadShort(F)
	ToughnessStat = ReadShort(F)
	StrengthStat = ReadShort(F)
	SpeedStat = ReadShort(F)
	CloseFile(F)

	; Fill list boxes
	Item = FUI_ComboBoxItem(CEnergy, "None") : FUI_SendMessage(Item, M_SETDATA, 65535)
	Item = FUI_ComboBoxItem(CBreath, "None") : FUI_SendMessage(Item, M_SETDATA, 65535)
	Idx = 1
	HealthIdx = 1 : EnergyIdx = 1 : BreathIdx = 1
	ToughnessIdx = 1 : StrengthIdx = 1 : SpeedIdx = 1
	For i = 0 To 39
		If AttributeNames$(i) <> ""
			Item = FUI_ComboBoxItem(CHealth, AttributeNames$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
			Item = FUI_ComboBoxItem(CEnergy, AttributeNames$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
			Item = FUI_ComboBoxItem(CBreath, AttributeNames$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
			Item = FUI_ComboBoxItem(CToughness, AttributeNames$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
			Item = FUI_ComboBoxItem(CStrength, AttributeNames$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
			Item = FUI_ComboBoxItem(CSpeed, AttributeNames$(i)) : FUI_SendMessage(Item, M_SETDATA, i)
			If i = HealthStat Then HealthIdx = Idx
			If i = EnergyStat Then EnergyIdx = Idx + 1
			If i = BreathStat Then BreathIdx = Idx + 1
			If i = ToughnessStat Then ToughnessIdx = Idx
			If i = StrengthStat Then StrengthIdx = Idx
			If i = SpeedStat Then SpeedIdx = Idx
			Idx = Idx + 1
		EndIf
	Next
	FUI_SendMessage(CHealth, M_SETINDEX, HealthIdx)
	FUI_SendMessage(CEnergy, M_SETINDEX, EnergyIdx)
	FUI_SendMessage(CBreath, M_SETINDEX, BreathIdx)
	FUI_SendMessage(CToughness, M_SETINDEX, ToughnessIdx)
	FUI_SendMessage(CStrength, M_SETINDEX, StrengthIdx)
	FUI_SendMessage(CSpeed, M_SETINDEX, SpeedIdx)

	; Event loop
	Done = False
	Repeat

		; Events
		For E.Event = Each Event
			Select E\EventID
				; Cancel
				Case BCancel
					Done = True
				; Save
				Case BSave
					F = WriteFile("Data\Server Data\Fixed Attributes.dat")
						WriteShort(F, HealthStat)
						WriteShort(F, EnergyStat)
						WriteShort(F, BreathStat)
						WriteShort(F, ToughnessStat)
						WriteShort(F, StrengthStat)
						WriteShort(F, SpeedStat)
					CloseFile(F)
					F = WriteFile("Data\Game Data\Fixed Attributes.dat")
						WriteShort(F, HealthStat)
						WriteShort(F, EnergyStat)
						WriteShort(F, BreathStat)
						WriteShort(F, StrengthStat)
						WriteShort(F, SpeedStat)
					CloseFile(F)
					Done = True
				; Change selected attributes
				Case CHealth
					HealthStat = FUI_SendMessage(FUI_SendMessage(CHealth, M_GETSELECTED), M_GETDATA)
				Case CEnergy
					EnergyStat = FUI_SendMessage(FUI_SendMessage(CEnergy, M_GETSELECTED), M_GETDATA)
				Case CBreath
					BreathStat = FUI_SendMessage(FUI_SendMessage(CBreath, M_GETSELECTED), M_GETDATA)
				Case CToughness
					ToughnessStat = FUI_SendMessage(FUI_SendMessage(CToughness, M_GETSELECTED), M_GETDATA)
				Case CStrength
					StrengthStat = FUI_SendMessage(FUI_SendMessage(CStrength, M_GETSELECTED), M_GETDATA)
				Case CSpeed
					SpeedStat = FUI_SendMessage(FUI_SendMessage(CSpeed, M_GETSELECTED), M_GETDATA)
			End Select
			Delete E
		Next

		; Render
		FUI_Update()
		Flip()

	Until Done = True

	; Done
	FlushKeys()
	FUI_DeleteGadget(W)
	SetBuffer BackBuffer()

End Function

; Returns the next power of two down from a positive integer
Function PowerOfTwo(N)

	; Optimise for smaller numbers
	If (N And $FFFF0000) <> 0
		Start = 31
	Else
		Start = 15
	EndIf

	; Find power
	For i = Start To 0 Step -1
		Number = 1 Shl i
		If (N And Number) = Number Then Return Number
	Next

	; N is 0 or less
	Return 0

End Function

; Adds a mesh file to the MM
Function AddMeshToManager(Filename$, IsAnim, IsEncrypted, B3DWarn = True)

	; Add to database
	ID = AddMeshToDatabase(Filename$, IsAnim)
	If ID > -1 Then MeshNames$(ID) = GetMeshName$(ID)

End Function

; Adds a meshes folder to the MM (RECURSIVE)
Function AddMeshFolderToManager(Filename$, IsAnim, IsEncrypted, SubFolders = True)

	D = ReadDir("Data\Meshes\" + Filename$)
		Name$ = NextFile$(D)
		While Name$ <> ""
			If Name$ <> "." And Name$ <> ".."
				If FileType("Data\Meshes\" + Filename$ + "\" + Name$) = 1
					If Instr(Upper$(Name$), ".B3D") Or Instr(Upper$(Name$), ".X") Or Instr(Upper$(Name$), ".3DS") Or Instr(Upper$(Name$), ".OBJ") ;.obj cysis145
						AddMeshToManager(Filename$ + "\" + Name$, IsAnim, IsEncrypted, False)
					EndIf
				ElseIf FileType("Data\Meshes\" + Filename$ + "\" + Name$) = 2 And SubFolders = True
					AddMeshFolderToManager(Filename$ + "\" + Name$, IsAnim, IsEncrypted, SubFolders)
				EndIf
			EndIf
			FUI_UpdateMouse()
			Name$ = NextFile$(D)
		Wend
	CloseDir(D)

End Function

; Adds a textures folder to the MM (RECURSIVE)
Function AddTextureFolderToManager(Filename$, Flags, SubFolders = True)

	D = ReadDir("Data\Textures\" + Filename$)
		Name$ = NextFile$(D)
		While Name$ <> ""
			If Name$ <> "." And Name$ <> ".."
				If FileType("Data\Textures\" + Filename$ + "\" + Name$) = 1
					If Instr(Upper$(Name$), ".BMP") Or Instr(Upper$(Name$), ".JPG") Or Instr(Upper$(Name$), ".PNG") Or Instr(Upper$(Name$), ".TGA")
						ID = AddTextureToDatabase(Filename$ + "\" + Name$, Flags)
						If ID > -1 Then TextureNames$(ID) = GetTextureName$(ID)
					EndIf
				ElseIf FileType("Data\Textures\" + Filename$ + "\" + Name$) = 2 And SubFolders = True
					AddTextureFolderToManager(Filename$ + "\" + Name$, Flags, SubFolders)
				EndIf
			EndIf
			FUI_UpdateMouse()
			Name$ = NextFile$(D)
		Wend
	CloseDir(D)

End Function
;BLA DI BLA DI BLA I EAT CHEESECAKE...

; Adds a sounds folder to the MM (RECURSIVE)
Function AddSoundFolderToManager(Filename$, Is3D, SubFolders = True)

	D = ReadDir("Data\Sounds\" + Filename$)
		Name$ = NextFile$(D)
		While Name$ <> ""
			If Name$ <> "." And Name$ <> ".."
				If FileType("Data\Sounds\" + Filename$ + "\" + Name$) = 1
					If Instr(Upper$(Name$), ".OGG")
						ID = AddSoundToDatabase(Filename$ + "\" + Name$, Is3D)
						If ID > -1 Then SoundNames$(ID) = GetSoundName$(ID)
					EndIf
				ElseIf FileType("Data\Sounds\" + Filename$ + "\" + Name$) = 2 And SubFolders = True
					AddSoundFolderToManager(Filename$ + "\" + Name$, Is3D, SubFolders)
				EndIf
			EndIf
			FUI_UpdateMouse()
			Name$ = NextFile$(D)
		Wend
	CloseDir(D)

End Function

; Adds a music folder to the MM (RECURSIVE)
Function AddMusicFolderToManager(Filename$, SubFolders = True)

	D = ReadDir("Data\Music\" + Filename$)
		Name$ = NextFile$(D)
		While Name$ <> ""
			If Name$ <> "." And Name$ <> ".."
				If FileType("Data\Music\" + Filename$ + "\" + Name$) = 1
					UN$ = Upper$(Name$)
					If Instr(UN$, ".OGG")
						ID = AddMusicToDatabase(Filename$ + "\" + Name$)
						If ID > -1 Then MusicNames$(ID) = GetMusicName$(ID)
					EndIf
				ElseIf FileType("Data\Music\" + Filename$ + "\" + Name$) = 2 And SubFolders = True
					AddMusicFolderToManager(Filename$ + "\" + Name$, SubFolders)
				EndIf
			EndIf
			FUI_UpdateMouse()
			Name$ = NextFile$(D)
		Wend
	CloseDir(D)

End Function

; Deletes a directory and all its subdirectories (RECURSIVE)
Function DelTree(Dir$)

	If FileType(Dir$) <> 2 Then Return

	D = ReadDir(Dir$)
	Path$ = NextFile$(D)
	While Len(Path$) > 0
		If Path$ <> "." And Path$ <> ".."
			If FileType(Dir$ + "\" + Path$) = 2
				DelTree(Dir$ + "\" + Path$)
			Else
				DeleteFile(Dir$ + "\" + Path$)
			EndIf
		EndIf
		Path$ = NextFile$(D)
	Wend
	CloseDir(D)
	DeleteDir(Dir$)

End Function

; Copies a directory and all its subdirectories (RECURSIVE)
Function CopyTree(Dir$, DestinationDir$)

	If FileType(DestinationDir$) = 0 Then CreateDir(DestinationDir$)

	D = ReadDir(Dir$)
	If D = 0 Then Return
	Path$ = NextFile$(D)
	While Len(Path$) > 0
		If Path$ <> "." And Path$ <> ".."
			If FileType(Dir$ + "\" + Path$) = 2
				CopyTree(Dir$ + "\" + Path$, DestinationDir$ + "\" + Path$)
			Else
				CopyFile(Dir$ + "\" + Path$, DestinationDir$ + "\" + Path$)
			EndIf
		EndIf
		Path$ = NextFile$(D)
	Wend
	CloseDir(D)

End Function

; Copies a file, but deletes the destination first if it already exists
Function SafeCopyFile(FromFile$, ToFile$)

	If FileType(FromFile$) = 1
		If FileType(ToFile$) = 1 Then DeleteFile(ToFile$)
		CopyFile(FromFile$, ToFile$)
	EndIf

End Function

; Cleans a directory list
Function CleanDirList()
	Delete Each UpdateFile
End Function

; Reads a directory tree into the UpdateFile type
Function SearchDir(Search$)

	; Open directory for reading
	Dir = ReadDir(Search$)
	
	; Read
	Repeat
	
		; Get filename and check it exists
		File$ = NextFile(Dir)
		If File$ = "" Then Exit
		
		; If its not "Current" or "Ascend"
		If File$ <> "." And File$ <> ".." Then
			
			; If its a DIR, then recurse
			If FileType(Search$ + File$) = 2 Then
				SearchDir(Search$ + File$ + "\")
			Else ; If its a file then add it
				If Lower$(File$) <> Lower$(GameName$) + ".exe" And Lower$(File$) <> "libbz2w.dll" Then
					U.UpdateFile = New UpdateFile
					U\Name = Search$ + File$
				End If
			End If
		End If
	Forever
	
	; Clean up
	CloseDir(Dir)
	
End Function

; Adds a file to the update list
Function AddFile(Filename$)
	
	U.UpdateFile = New UpdateFile
	U\Name$ = Filename$
	
End Function

; Generates a patch to the game
Function GenerateGamePatch()

	Result = FUI_CustomMessageBox("Realm Crafter must build a full client to generate an update. Press yes to build a new client", "Update", MB_YESNO)
		
	If Result = IDYES Then
		;GenerateFullInstall()
	End If
	
	LabelBuildFullInstallCalled = False
	If Result = IDNO Then Return


	; Create window
	W = FUI_Window(0, 0, 430, 200, "Generating Updates", "", 1, 1)
	LStatus = FUI_Label(W, 10, 155, "")
	FUI_ModalWindow(W)
	FUI_CenterWindow(W)

	; Update user
	FUI_SendMessage(LStatus, M_SETCAPTION, "Building updates list...") : FUI_Update() : Flip(0)

	; Clean lists and create DIRs
	CleanDirList()
	DelTree("Patches")
	Delay 1000
	CreateDir("Patches")
	Delay 1000
	CreateDir("Patches\Files")
	
	; Add Updateable files
	SearchDir("Game\")

	; Reinform
	FUI_SendMessage(LStatus, M_SETCAPTION, "Building patch files...") : FUI_Update() : Flip(0)
	
	; Loop through each file and write its compressed version
	For U.UpdateFile = Each UpdateFile
		
;		bz2(0, 9, U\Name, "Temp.dat")
		;CopyFile U\Name, "Temp.dat
		TName$ = Right(U\Name$, Len(U\Name$) - Len("Game\"))
		SafeFileName$ = Replace$(TName$, "\", "!!")
		SafeFileName$ = Replace$(SafeFileName$, " ", "!!!")
		SafeFileName$ = Upper$(SafeFileName$)
		CopyFile(U\Name$, "Patches\Files\" + SafeFileName$ + ".DAT")
		;DeleteFile("Temp.dat")
	
	Next
	
	
	; Copy PHP file
	CopyFile("UpdateServer.php", "Patches\Files\UpdateServer.php")
	
	;If this PHP file shit doesnt't work i might just murder a child... a child of cheesecake that is muhaha ha ha ..... ha...

	; Instructions to user
	FUI_SendMessage(LStatus, M_SETCAPTION, "Complete") : FUI_Update() : Flip(0)
	FUI_Label(W, 10, 10, "Please follow these steps:")
	FUI_Label(W, 10, 45, "Copy the files from \Patches\Files to your webserver making sure that")
	FUI_Label(W, 10, 65, "you overwrite any newer files (Unchanged files can be left to save upload time).")
	FUI_Label(W, 10, 105, "When your clients next launch your game patcher, they will download the latest files")
	;"" FUI_Label(W, 40, 105, "4. Unlock your updates server again")
	BDone = FUI_Button(W, 360, 145, 60, 20, "Done")

	; Wait for quit
	Repeat
		FUI_Update()
		Flip(0)
		For E.Event = Each Event
			Select E\EventID
				; Window closed
				Case W
					If Lower$(E\EventData$) = "closed"
						FUI_DeleteGadget(W)
						Return
					EndIf
				; Done clicked
				Case BDone
					FUI_DeleteGadget(W)
					Return
			End Select
			Delete(E)
		Next
	Forever

End Function

; Goes through a folder checking whether the files need updating (RECURSIVE)
Function DoUpdates(Folder$, ListStream, StatusLabel)

	D = ReadDir(Folder$)

		File$ = NextFile$(D)
		While Len(File$) > 0
			; It's a folder - call this function recursively
			If FileType(Folder$ + "\" + File$) = 2 And File$ <> "." And File$ <> ".."
				DoUpdates(Folder$ + "\" + File$, ListStream, StatusLabel)
			; It's a file, process it
			ElseIf FileType(Folder$ + "\" + File$) = 1 And Upper$(File$) <> "THUMBS.DB" And Upper$(File$) <> "MISC.DAT"
				FUI_SendMessage(StatusLabel, M_SETCAPTION, "Checking file: " + File$ + "...") : FUI_Update() : Flip(0)

				; Get the checksum
				Checksum = CountChecksum(Folder$ + "\" + File$)

				; Check if it's already in the list
				Found = False
				For U.UpdateFile = Each UpdateFile
					If Upper$(Folder$ + "\" + File$) = U\Name$
						; Compare checksums - if different, leave it as unfound to patch it anyway
						If U\Checksum = Checksum Then Found = True
						Exit
					EndIf
				Next

				; If unfound, create a patch for it
				If Found = False
					bz2(0, 9, Folder$ + "\" + File$, "Temp.dat")
					SafeFileName$ = Replace$(Folder$ + "\" + File$, "\", "!!")
					SafeFileName$ = Replace$(SafeFileName$, " ", "!!!")
					SafeFileName$ = Upper$(SafeFileName$)
					CopyFile("Temp.dat", "Patches\Files\" + SafeFileName$ + ".DAT")
					DeleteFile("Temp.dat")
				EndIf

				; Write it out to files list
				WriteString ListStream, Folder$ + "\" + File$
				WriteInt ListStream, Checksum
			EndIf
			File$ = NextFile$(D)
		Wend

	CloseDir D

End Function

; Counts a file checksum
Function CountChecksum(File$)

	Local Result, F
	F = ReadFile(File$)
	If F = 0 Then Return(0)
		While Eof(F) = False
			Result = Result + ReadInt(F)
		Wend
	CloseFile(F)
	Return(Result)

End Function

; Filters out characters from a string which are not compatible with MySQL
Function FilterChars$(S$)

	S$ = Replace$(S$, Chr$(34), "")
	S$ = Replace$(S$, ";", "")
	S$ = Replace$(S$, "'", "")
	S$ = Replace$(S$, "`", "")
	Return S$

End Function

; Adds red borders around a texture
Function AddBorders(T)

	Color 255, 0, 0
	SetBuffer TextureBuffer(T)
		Rect 0, 0, TextureWidth(T), 4
		Rect 0, 0, 4, TextureHeight(T)
		Rect TextureWidth(T) - 4, 0, 4, TextureHeight(T) - 4
		Rect 0, TextureHeight(T) - 4, TextureWidth(T) - 4, 4
	SetBuffer BackBuffer()

End Function

; Sets the scale for a water entity (including texture scaling)
Function ScaleWater(W.Water)

	ScaleEntity W\EN, W\ScaleX#, 1.0, W\ScaleZ#
	S = GetSurface(W\EN, 1)
	For i = 0 To CountVertices(S) - 1
		X# = VertexX#(S, i)
		Z# = VertexZ#(S, i)
		VertexTexCoords(S, i, X# * W\ScaleX#, Z# * W\ScaleZ#)
	Next

End Function

; Writes a string to a bank
Function PokeString(B, Offset, S$)

	Local i = 0
	PokeShort(B, Offset, Len(S$))
	For i = 1 To Len(S$)
		PokeByte(B, Offset + 1 + i, Asc(Mid$(S$, i, 1)))
	Next

End Function

; Reads a string from a bank
Function PeekString$(B, Offset)

	Local S$ = "", i = 0, Length = 0
	Length = PeekShort(B, Offset)
	For i = 1 To Length
		S$ = S$ + Chr$(PeekByte(B, Offset + 1 + i))
	Next
	Return S$

End Function

; Hides an entity and all its children
Function HideActorEntity(EN)

	If Object.RP_Emitter(EntityName$(EN)) <> Null
		RP_HideEmitter(EN)
	Else
		HideEntity(EN)
	EndIf
	For i = 1 To CountChildren(EN)
		HideActorEntity(GetChild(EN, i))
	Next

End Function

; Shows an entity and all its children
Function ShowActorEntity(EN)

	If Object.RP_Emitter(EntityName$(EN)) <> Null
		RP_ShowEmitter(EN)
	Else
		ShowEntity(EN)
	EndIf
	For i = 1 To CountChildren(EN)
		ShowActorEntity(GetChild(EN, i))
	Next

End Function

; PO code
Function PO()

	If Rand(10) = 1
		U.Undo = Null
		U\Action$ = "PO"
	EndIf

End Function

;	Function to declare a warning if actor health is set at 0 when spawning

Function CheckActorHealth()
	
	; Store the ActorID to compare with
	SelectedActorID = CurrentArea\SpawnActor[SpawnNum]
	
	; Loop through ActorInstance->Actor->Attributes->Health
	For SActor.ActorInstance = Each ActorInstance
		; Actors
		For SAc.Actor = Each Actor
			; Compare ActorIDs with SelectedID
			If SAc\ID = SelectedActorID
				; Loop through all attributes
				For i = 0 To 39
					; Hard coded 'Health' Stat - check if this stat is <= 0
					If(AttributeNames$(i) = "Health" And SAc\Attributes\Value[i] <= 0)
						FUI_CustomMessageBox("Health is set at 0, therefore it will not spawn!", "Warning", MB_OK)
					EndIf
				Next
			EndIf
		Next
	Next
End Function

;	Function to update particle changes in the zone tab automatically cysis 145 [NOT FINISHED]
;Function UpdateZoneParticles()
	
;	; Only update this if we changed something in particles tab..
;	If ParticlesChanged Or ParticlesSaved = False
		
;		ParticlesSaved = True
;		ParticlesChanged = False
		
;		; Loop through all active emitters in the zone tab
;		For E.Emitter = Each Emitter
			
;			; Unload emitters
;			RP_FreeEmitter(GetChild(E\EN, 1))
;			; UnloadTexture(E\TexID)
;			E\Config = 0
			
;			; Read in emitter data
;			; Texture = GetTexture(E\TexID)
			
;			; Find ConfigID
;			E\Config = RP_CopyEmitterConfig(E\ConfigID)
;			;E\Config = RP_CopyEmitterConfig(E\Config\ConfigID)
			
;			; Release Selected Entity
;			SelectedEN = 0
			
;			; Loaded successfully, create the emitter
;			If E\Config <> 0
;				EmitterEN = RP_CreateEmitter(E\Config)
;				EntityParent EmitterEN, E\EN, False
;			EndIf
;		Next
		
;	EndIf
	
;	; Show emitters
;	For E.Emitter = Each Emitter
;		ShowEntity(E\EN) 
;		RP_ShowEmitter(GetChild(E\EN, 1)) 
;	Next
	
;	; Particles - Update them if they've been changed in the particles tab
;	For C.RP_EmitterConfig = Each RP_EmitterConfig
;		C\FaceEntity = ZoneCam
;	Next
	
	
;End Function

;Menu Savew All Function Cysis145
Function menuSaveAll()
	; Saving message
				FUI_CustomMessageBox( "Saving all data", "Save All", 0 )
				
				; Save emitters
				For EmC.RP_EmitterConfig = Each RP_EmitterConfig
					RP_SaveEmitterConfig(Handle(EmC), "Data\Emitter Configs\" + EmC\Name$ + ".rpc")
				Next
				ParticlesSaved = True
				
				; Save combat
				F = WriteFile("Data\Server Data\Damage.dat")
					For i = 0 To 19
						WriteString F, DamageTypes$(i)
					Next
				CloseFile(F)
				DamageTypesSaved = True
				
				; Save projectiles
				SaveProjectiles("Data\Server Data\Projectiles.dat")
				ProjectilesSaved = True
				
				; Save factions
				SaveFactions("Data\Server Data\Factions.dat")
				FactionsSaved = True
				
				; Save animations
				SaveAnimSets("Data\Game Data\Animations.dat")
				AnimsSaved = True
				
				; Save attributes
				SaveAttributes("Data\Server Data\Attributes.dat")
				StatsSaved = True

				; Save actors
				;UpdateActorPreview()
				SaveActors("Data\Server Data\Actors.dat")
				ActorsSaved = True
				
				; Save items
				SaveItems("Data\Server Data\Items.dat")
				ItemsSaved = True
				
				; Save environment
				SaveEnvironment(True)
				SaveSuns()
				EnvironmentSaved = True
				
				; Save zone
				ZoneSave()
				
				; Save spells
				SaveSpells("Data\Server Data\Spells.dat")
				SpellsSaved = True
				
				; Save interface
				SaveInterfaceSettings("Data\Game Data\Interface.dat")
				InterfaceSaved = True
End Function