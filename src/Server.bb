; This application should be compiled with BlitzForge
Global componentName$ = "server"
Global RootDir$ = "..\"

if FileType("bin") = 2
    RootDir$ = CurrentDir()
EndIf

ChangeDir RootDir$

; Debug log mode
Global LogMode = 1; (0 = standard logging, 1 = debug mode)
Global LogCounter = 0
Dim DBLog$(10, 1);
; Includes --------------------------------------------------------------------------------------------------------------------------

Include "Modules\Language.bb"             ; Language module (some other modules depend on this)
Include "Modules\RCEnet.bb"               ; Network module
Include "Modules\Environment.bb"          ; Environment module
Include "Modules\Items.bb"                ; Items module
Include "Modules\Projectiles.bb"          ; Projectiles module
Include "Modules\Spells.bb"               ; Spells (abilities) module
Include "Modules\briskvm.bb"
Include "Modules\Actors.bb"               ; Actors module
Include "Modules\Inventories.bb"          ; Inventory module
Include "Modules\ServerAreas.bb"          ; Areas module
Include "Modules\SpawnTracking.bb"        ; Spawn bookkeeping helpers
Include "Modules\Scripting.bb"            ; Script language module
Include "Modules\Logging.bb"              ; Logging module
Include "Modules\PasswordHash.bb"         ; Password hashing (SHA-256 + salted v1 format)
Include "Modules\AccountsServer.bb"       ; Accounts server module
Include "Modules\GameServer.bb"           ; Game server module
Include "Modules\UpdatesServer.bb"        ; Updates server module
Include "Modules\Packets.bb"              ; Packet types module
Include "Modules\ServerNet.bb"            ; Server specific network module
//Include "Modules\MySQL.bb"				  ; MySQL functions

Include "Modules\Framework\RCCEApp.bb"
Include "Modules\Graphics\RCCEGraphics.bb"
Include "Modules\Graphics\UI\Components\Component.bb"
Include "Modules\Graphics\UI\Components\TextComponent.bb"
Include "Modules\F-UI.bb"
Include "Modules\Graphics\UI\BPWrapper.bb"
Include "Modules\IO\Image.bb"

; Variables -------------------------------------------------------------------------------------------------------------------------

Type Server.RCCEApp
	Field window%
	Field gfx.RCCEGraphics
	Field assetList.BBList
	Field componentList.BBList

	Method create.Server()
		self = Recast.Server(RCCEApp::create(self, "Realm Crafter Engine BVM Server", ".\"))

		return self
	End Method

	Method init()
		If FileType(self\rootDir + "bin") <> 2
			self\rootDir = "..\"
		End If

		RCCEApp::init(self, 1024, 768)

		self\gfx = new RCCEGraphics(self\width, self\height, 0, 2)

		RCCEGraphics::init(self\gfx)

		Local loadingText.TextComponent = new TextComponent("Starting server...", True)
		TextComponent::place(loadingText, (self\width / 2), (self\height / 2))
		TextComponent::setColor(loadingText, 255,255,255)
		TextComponent::draw(loadingText)

		RCCEGraphics::push(self\gfx)

		FUI_Initialise(self\width, self\height, 0, 2, False, True, self\title, RCCEApp::version(self))

		Server::loadAssets(self)
		Server::loadComponents(self)
	End Method

	Method loadAssets()
	End Method

	Method loadComponents()
	End Method

	Method addComponent(comp.Component)
		if self\componentList = Null
			self\componentList = CreateList()
		end if

		ListAdd(self\componentList, comp)
	End Method

	Method getComponent.Component(index%)
		local comp.Component = ListAt(self\componentList, index)
		return comp
	End Method
End Type

Local serv.Server = new Server()

Server::init(serv)

; MySQL variables
Const MySQL = False ; Used to toggle a MySQL server build
Global hSQL = 0
Global MY_Reason
Const MY_WRONGLOGIN = 2
Const MY_BANNED = 3
Const MY_NOACCOUNT = 4
Const MY_ACCOUNTLOGGEDIN = 5

; Initialise MySQL if set
If MySQL
	F = ReadFile("Data\Server Data\MySQL.dat")
	If Not F Then RuntimeError("No MySQL configuration file was found!")
	
	My_Host$ = ReadLine$(F)
	My_User$ = ReadLine$(F)
	My_Pass$ = ReadLine$(F)
	My_Data$ = ReadLine$(F)
	My_Port% = ReadLine$(F)
	
	hSQL = OpenSQLStream(My_Host$, My_Port%, My_User$, My_Pass$, my_Data$, 1)
	If Not hSQL Then RuntimeError("MySQL could not connect to the server, please check configuration")
	; Connect in Threaded Program
	hEXT = SQLStart(My_Host$, My_User$, My_PAss$, My_Data$, My_Port%)
	If Not hEXT Then RuntimeError("MySQL - Threads could not connect to the server, please check configuration")
EndIf

Global SpeedStat, StrengthStat, HealthStat, EnergyStat = -1, ToughnessStat = -1, BreathStat = -1

Global ChatLoggingMode = 2 ; Log all chat
Global ServerLocked = True
Global Host = 0
Global ServerPort

Const PortalLockTime = 35000

Global MaxAccountChars
Global StartArea$, StartGold, StartReputation
Global ForcePortals = True
Global CombatDelay, CombatFormula
Global CombatRatingAdjust ; How much is an actor's rating with a faction reduced after killing a member?
Global AllowAccountCreation ; Is the client allowed to create new accounts?
Global RequireMemorise ; Must actors memorise abilities before using them?

;*****************************************************************************
; Client Cam Range is 500
Const UpdateDistance = 250000; 90000 ;Square Distance Range for near position update - radius of 500
Const UpdateFarDistance = 1000000 ;302500 ;Actors farther than this squared distance don't receive any information - radius of 1000
;*****************************************************************************

Const WorldUpdateMS = 1000 / 30   	; Change here from 30 to 10
Global LastWorldUpdate = MilliSecs()
Const BroadcastMS = 1000 / 5
Global LastBroadcast = MilliSecs()
Global LogNetwork = 0, LogNetworkTime, LogNetworkBytesIn, LogNetworkBytesOut ; Network usage logging
Global LastCompleteUpdate = MilliSecs();
Const CompleteUpdateMS = 1000
Global LastRegenTime = MilliSecs() 
 Global RegenTime = 16000      ; Time between regen cycles (16sec) 
 Global RegenAmountValue# = 0.02   ; Percent from MaxAttribute will be used towards regen increments, Higher value = more regen amount.

Global UpdateArea.Area

; Start server ----------------------------------------------------------------------------------------------------------------------

; General
;AppTitle("Realm Crafter Engine BVM Server")
;AutoSuspend(False)
SeedRnd(MilliSecs())
; Log files
Global MainLog = StartLog("Server Log", False)
Global ChatLog = StartLog("Chat Log", True)
If MainLog = 0 Or ChatLog = 0 Then RuntimeError("Could not write to log file!")
WriteLog(MainLog, "** Server started **", True, True)
WriteLog(ChatLog, "** Server started **", True, True)

; Windows
Accounts.AccountsWindow = CreateAccountsWindow()
Game.GameWindow         = CreateGameWindow()
Updates.UpdatesWindow   = CreateUpdatesWindow()
WriteLog(MainLog, "Created server windows...")
; Taskbar notification area icon
;ggTrayCreate(QueryObject(Accounts\Window, 1))
;ggTraySetIconFromFile("Data\Server Data\TaskbarIcon.ico")
;ggTraySetToolTip("Realm Crafter Server is running")

;ggTrayShowIcon()
ServerMinimised = False
; Game data
Result = LoadLanguage("Data\Server Data\Language.txt")
If Result = False Then RuntimeError("Could not open Data\Server Data\Language.txt!")
Result = LoadAttributes("Data\Server Data\Attributes.dat") : WriteLog(MainLog, "Loaded actor attributes...")
If Result = False Then RuntimeError("Could not open Data\Server Data\Attributes.dat!")
Result = LoadDamageTypes("Data\Server Data\Damage.dat") : WriteLog(MainLog, "Loaded damage types...")
If Result = False Then RuntimeError("Could not open Data\Server Data\Damage.dat!")
Number = LoadSpells("Data\Server Data\Spells.dat") : WriteLog(MainLog, "Loaded " + Str$(Number) + " abilities...")
If Number = -1 Then RuntimeError("Could not open Data\Server Data\Spells.dat!")
Number = LoadFactions("Data\Server Data\Factions.dat") : WriteLog(MainLog, "Loaded " + Str$(Number) + " factions...")
If Number = -1 Then RuntimeError("Could not open Data\Server Data\Factions.dat!")
Number = LoadActors("Data\Server Data\Actors.dat") : WriteLog(MainLog, "Loaded " + Str$(Number) + " actors...")
If Number = -1 Then RuntimeError("Could not open Data\Server Data\Actors.dat!")
Number = LoadItems("Data\Server Data\Items.dat") : WriteLog(MainLog, "Loaded " + Str$(Number) + " items...")
If Number = -1 Then RuntimeError("Could not open Data\Server Data\Items.dat!")
Number = LoadProjectiles("Data\Server Data\Projectiles.dat") : WriteLog(MainLog, "Loaded " + Str$(Number) + " projectiles...")
If Number = -1 Then RuntimeError("Could not open Data\Server Data\Projectiles.dat!")
Number = LoadAccounts() : WriteLog(MainLog, "Loaded " + Str$(Number) + " accounts...")
Number = LoadScripts() : WriteLog(MainLog, "Loaded " + Str$(Number) + " scripts...")
LoadPrivilegedScripts() ; Server-controlled allowlist; gates BVM priv elevation at ThreadScript boundary. See Scripting.bb's `LoadPrivilegedScripts` header for the trust model.
Number = CompileModules(): WriteLog(MainLog, "Compiled " + Str$(Number) + " Modules...")
Result = LoadSuperGlobals("Data\Server Data\Superglobals.dat") : WriteLog(MainLog, "Loaded superglobal variables...")
; Load zones
Dir = ReadDir("Data\Server Data\Areas")
File$ = NextFile$(Dir)
Number = 0
While File$ <> ""
	If FileType("Data\Server Data\Areas\" + File$) = 1
		File$ = Replace$(File$, ".dat", "") : File$ = Replace$(File$, ".DAT", "") : File$ = Replace$(File$, ".Dat", "")
		If ServerLoadArea(File$) <> Null Then Number = Number + 1
	EndIf
	File$ = NextFile$(Dir)
Wend
CloseDir Dir
If First Area = Null Then RuntimeError("You must create at least one zone to run the server!")
WriteLog(MainLog, "Loaded " + Str$(Number) + " zones...")

; Load dropped items
Number = LoadDroppedItems("Data\Server Data\Dropped Items.dat") : WriteLog(MainLog, "Loaded " + Str$(Number) + " dropped items...")

; Process zones
For Ar.Area = Each Area
	; Add to zones combo box
	AddGadgetItem(Game\AreaCombo, Ar\Name$)
	; Match linked weather zones
	If Ar\WeatherLink$ <> ""
		For Ar2.Area = Each Area
			If Upper$(Ar2\Name$) = Upper$(Ar\WeatherLink$) Then Ar\WeatherLinkArea = Ar2 : Exit
		Next
	EndIf
Next

; Misc settings
F = ReadFile("Data\Server Data\Misc.dat")
If F = 0 Then RuntimeError("Could not open Data\Server Data\Misc.dat!")
	StartGold = ReadInt(F)
	StartReputation = ReadInt(F)
	ForcePortals = ReadByte(F)
	CombatDelay = ReadShort(F)
	CombatFormula = ReadByte(F)
	WeaponDamage = ReadByte(F)
	ArmourDamage = ReadByte(F)
	CombatRatingAdjust = ReadByte(F)
	AllowAccountCreation = ReadByte(F)
	MaxAccountChars = ReadByte(F)
	ServerPort = ReadInt(F)
	If ServerPort = 0 Then ServerPort = 25000
	RequireMemorise = ReadByte(F)
CloseFile(F)
WriteLog(MainLog, "Loaded misc options...")
Result = LoadEnvironment()
If Result = False Then RuntimeError("Could not open Data\Server Data\Environment.dat!")
WriteLog(MainLog, "Loaded environment settings...")

; Fixed attributes needed by engine
F = ReadFile("Data\Server Data\Fixed Attributes.dat")
If F = 0 Then RuntimeError("Could not open Data\Server Data\Fixed Attributes.dat!")
HealthStat = ReadShort(F)
EnergyStat = ReadShort(F)
BreathStat = ReadShort(F)
ToughnessStat = ReadShort(F)
StrengthStat = ReadShort(F)
SpeedStat = ReadShort(F)
CloseFile(F)
If HealthStat = 65535 Then HealthStat = -1 ;RuntimeError("A valid Health attribute must be selected!")
If EnergyStat = 65535 Then EnergyStat = -1
If BreathStat = 65535 Then BreathStat = -1
If ToughnessStat = 65535 Then ToughnessStat = -1 ;RuntimeError("A valid Toughness attribute must be selected!")
If StrengthStat = 65535 Then RuntimeError("A valid Strength attribute must be selected!")
If SpeedStat = 65535 Then RuntimeError("A valid Speed attribute must be selected!")

; Precalculate actor radii
For Actor.Actor = Each Actor
	Actor\Radius# = Actor\Radius# * 0.05
Next

; Startup script if present
ThreadScript("Startup", "Main", 0, 0)

; Unlock server if commandline argument present
If Instr(Upper$(CommandLine$()), "-UNLOCK") > 0
	ServerLocked = False

	WriteLog(MainLog, "Updates Server Unlocked", True, True)

	; Update window
	HideGadget Updates\LockLabel
	SetGadgetText Updates\LockButton, "Lock Updates Server"
	SetPanelImage(Updates\ImageBox, CurrentDir() + "Data\Server Data\GreenLight.bmp")

	; Reload files list
	Number = LoadUpdateFiles() : WriteLog(MainLog, "Loaded " + Str$(Number) + " files for update system...")

	; Open network
	Host = RCE_StartHost(ServerPort, "", 5000, "Data\Logs\Server Connection.txt", False)
	If Host = False
		WriteLog(MainLog, "** Could not open port " + ServerPort + " - server shut down **", True, True)
		Shutdown()
		RuntimeError "Could not open port " + ServerPort + "!"
	EndIf
EndIf

; Done
UpdateArea = First Area
WriteLog(MainLog, "Loading complete, server running.", True, True)


; Main loop -------------------------------------------------------------------------------------------------------------------------
Repeat
	Cls
	RCCEGraphics::resetBuffer(serv\gfx)
	FUI_Update()
	Flip(0)

	; Process taskbar notification area icon events
	/*If ggTrayPeekLeftDblClick() > 0
		ServerMinimised = Not ServerMinimised
		If ServerMinimised
			HideGadget(Accounts\Window)
			HideGadget(Game\Window)
			HideGadget(Updates\Window)
		Else
			ShowGadget(Accounts\Window)
			ShowGadget(Game\Window)
			ShowGadget(Updates\Window)
		EndIf
	EndIf
	ggTrayClearEvents()*/

	; Process window events
	Local E.Event	
	For E.Event = Each Event
		Select E\EventID
			Case Game\RefreshScriptsButton
				Bvm_RefreshScripts()
			; Boot player
			Case Game\BootButton
				ID = SelectedGadgetItem(Game\PlayersList)
				If ID > -1
					Name$ = GadgetItemText(Game\PlayersList, ID)
					For AI.ActorInstance = Each ActorInstance
						If AI\RNID > 0
							AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
							If AInstance <> Null And AInstance\Area = GameArea
								If AI\Name$ + " (" + AInstance\ID + ")" = Name$
									DataAux$ = RCE_StrFromInt(AI\RNID)
									RCE_FSend(0, RCE_PlayerKicked, DataAux$, True, Len(DataAux$))
									RCE_FSend(AI\RNID, P_KickedPlayer, "", True, 0)
									Exit
								EndIf
							EndIf
						EndIf
					Next
				EndIf
			; Change login message
			Case Game\MessageButton
				LoginMessage$ = TextFieldText$(Game\MessageText)
				WriteLog(MainLog, "Login message set to: " + LoginMessage$)
			; Send global message to all players
			Case Game\SendMessageButton
				Msg$ = Chr$(253) + "<< SERVER MESSAGE >>  " + TextFieldText$(Game\SendMessageText)
				For AI.ActorInstance = Each ActorInstance
					If AI\RNID > 0
						RCE_Send(Host, AI\RNID, P_ChatMessage, Msg$, True)
					EndIf
				Next
				SetGadgetText(Game\SendMessageText, "")
				ActivateGadget(Game\SendMessageText)
			; Change selected area
			Case Game\AreaCombo
				; Select new area
				Name$ = GadgetItemText$(Game\AreaCombo, E\EventData)
				For Ar.Area = Each Area
					If Ar\Name$ = Name$
						GameArea = Ar
						Exit
					EndIf
				Next
				; Clear
				SetTextAreaText(Game\ChatText, "")
				ClearGadgetItems(Game\PlayersList)
				; Fill players list
				For AI.ActorInstance = Each ActorInstance
					If AI\RNID > 0
						AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
						If AInstance <> Null And AInstance\Area = GameArea
							AddListBoxItem(Game\PlayersList, AI\Name$ + " (" + AInstance\ID + ")")
						EndIf
					EndIf
				Next
			; Change chat logging mode
			Case Game\ChatLogMode
				ChatLoggingMode = E\EventData
			; Flush chat log
			Case Game\ChatLogFlushButton
				StopLog(ChatLog)
				DeleteFile("Data\Logs\Chat Log.txt")
				ChatLog = StartLog("Chat Log", True)
				WriteLog(ChatLog, "** Log flushed **", True, True)
			; Toggle DM status
			Case Accounts\DMButton
				A.Account = FindAccountByListID(SelectedGadgetItem(Accounts\List))
				If A <> Null
					SetAccountDMStatus(A, Not A\IsDM)
					WriteLog(MainLog, "Toggled DM Status: " + A\User$ + " it is now " + Str(A\IsDM))
				EndIf
			; Toggle ban status
			Case Accounts\BanButton
				A.Account = FindAccountByListID(SelectedGadgetItem(Accounts\List))
				If A <> Null
					SetAccountBanStatus(A, Not A\IsBanned)
					WriteLog(MainLog, "Toggled Ban Status: " + A\User$ + " it is now " + Str(A\IsBanned))
				EndIf
			; Remove account
			Case Accounts\DeleteButton
				If Confirm("Really remove account?") = True
					A.Account = FindAccountByListID(SelectedGadgetItem(Accounts\List))
					If A <> Null
						; Remove from counters
						Accounts\TotalAccounts = Accounts\TotalAccounts - 1
						If A\IsDM = True Then Accounts\TotalDMs = Accounts\TotalDMs - 1
						If A\IsBanned = True Then Accounts\TotalBanned = Accounts\TotalBanned - 1
						SetGadgetText(Accounts\AccountsLabel, "Total accounts: " + Str(Accounts\TotalAccounts))
						SetGadgetText(Accounts\DMLabel, "GM accounts: " + Str(Accounts\TotalDMs))
						SetGadgetText(Accounts\BannedLabel, "Banned accounts: " + Str(Accounts\TotalBanned))
						; Move all others up in the list
						Ac2.Account = After A
						While Ac2 <> Null
							Ac2\ListID = Ac2\ListID - 1
							Ac2 = After Ac2
						Wend
						; Delete it
						WriteLog(MainLog, "Deleted account: " + A\User$)
						Delete A
						RemoveGadgetItem Accounts\List, SelectedGadgetItem(Accounts\List)
						SaveAccounts()
					EndIf
				EndIf
			; Lock/unlock updates server
			Case Updates\LockButton
				ServerLocked = Not ServerLocked
				If ServerLocked = True
					WriteLog(MainLog, "Updates Server Locked", True, True)

					; Update window
					ShowGadget Updates\LockLabel
					SetGadgetText Updates\LockButton, "Unlock Updates Server"
					SetPanelImage(Updates\ImageBox, CurrentDir() + "Data\Server Data\RedLight.bmp")

					; Boot all players
					For AI.ActorInstance = Each ActorInstance
						If AI\RNID > 0 Then RCE_FSend(AI\RNID, P_KickedPlayer, "", True, 0)
					Next

					; Save everything
					SaveAccounts()
					WriteLog(MainLog, "Saved accounts...")
					SaveSuperGlobals("Data\Server Data\Superglobals.dat")
					WriteLog(MainLog, "Saved superglobal variables...")
					;For Ar.Area = Each Area : ServerSaveAreaOwnerships(Ar) : Next ;{##}
					WriteLog(MainLog, "Saved zone ownerships...")
					SaveEnvironment()
					WriteLog(MainLog, "Saved environment settings...")
					SaveDroppedItems("Data\Server Data\Dropped Items.dat")
					WriteLog(MainLog, "Saved dropped items...")

					; Sit for a little while to make sure everybody gets the boot message
					T = MilliSecs()
					While MilliSecs() - T < 1000
;							Delay 5					
						RCE_Update() 
						RCE_CreateMessages()						
					Wend

					; Disconnect
					RCE_Disconnect()

					; Set every account to logged off
					For A.Account = Each Account
						SetLoginStatus(A, -1)
					Next
				Else
					WriteLog(MainLog, "Updates Server Unlocked", True, True)

					; Update window
					HideGadget Updates\LockLabel
					SetGadgetText Updates\LockButton, "Lock Updates Server"
					SetPanelImage(Updates\ImageBox, CurrentDir() + "Data\Server Data\GreenLight.bmp")

					; Rebuild spawn occupancy from the actors that actually survived the
					; lock cycle so stale counts cannot block future respawns.
					SyncAreaSpawnCounts()

					; Reload files list
					Delete Each UpdateFile
					Number = LoadUpdateFiles() : WriteLog(MainLog, "Loaded " + Str$(Number) + " files for update system...")

					; Open network
					Host = RCE_StartHost(ServerPort, "", 5000, "Data\Logs\Server Connection.txt", False)
					If Host = False
						WriteLog(MainLog, "** Could not open port " + ServerPort + " - server shut down **", True, True)
						Shutdown()
						RuntimeError "Could not open port " + ServerPort + "!"
					EndIf
				EndIf
		End Select
		Delete E
	Next


	; Things to do only if the server is unlocked
	If ServerLocked = False
		; Process network messages
		RCE_Update()
		RCE_CreateMessages()

		UpdateNetwork()
		//My_UpdateThreads()

		; Update one zone per loop to save CPU time
		UpdateArea = After UpdateArea
		If UpdateArea = Null Then UpdateArea = First Area
		For j = 0 To 99
			If UpdateArea\Instances[j] <> Null
				; Weather
				UpdateWeather(UpdateArea\Instances[j])
				; Spawn points
				For i = 0 To 999
					If UpdateArea\Instances[j]\Spawned[i] < UpdateArea\SpawnMax[i]
						If UpdateArea\Instances[j]\SpawnLast[i] = 0
							UpdateArea\Instances[j]\SpawnLast[i] = MilliSecs()
						ElseIf MilliSecs() - UpdateArea\Instances[j]\SpawnLast[i] > UpdateArea\SpawnFrequency[i] * 1000
							; ActorList is Dim'd 0..65535. SpawnActor[i] is
							; loaded as ReadShort (signed) -- a corrupt area
							; file would drive ActorList(SpawnActor[i]) OOB
							; before the existing `<> Null` guard runs. Skip
							; the spawn slot rather than crash the live world.
							If UpdateArea\SpawnActor[i] >= 0 And UpdateArea\SpawnActor[i] <= 65535 And ActorList(UpdateArea\SpawnActor[i]) <> Null
							WriteLog(MainLog, "Spawning AI actor: " + ActorList(UpdateArea\SpawnActor[i])\Race$ + " in zone: " + UpdateArea\Name$)
							AI.ActorInstance = CreateActorInstance.ActorInstance(ActorList(UpdateArea\SpawnActor[i]))
							AI\RNID = -1
							AssignRuntimeID(AI)
							SetArea(AI, UpdateArea, j, UpdateArea\SpawnWaypoint[i])
							AI\X# = AI\X# + Rnd#(UpdateArea\SpawnSize#[i] / -2.0, UpdateArea\SpawnSize#[i] / 2.0)
							AI\Z# = AI\Z# + Rnd#(UpdateArea\SpawnSize#[i] / -2.0, UpdateArea\SpawnSize#[i] / 2.0)
							UpdateArea\Instances[j]\Spawned[i] = UpdateArea\Instances[j]\Spawned[i] + 1
							UpdateArea\Instances[j]\SpawnLast[i] = MilliSecs()
							AI\SourceSP = i
							AI\AIMode = AI_Patrol
							AI\CurrentWaypoint = UpdateArea\SpawnWaypoint[i]
							AI\Script$ = UpdateArea\SpawnActorScript$[i]
							AI\DeathScript$ = UpdateArea\SpawnDeathScript$[i]
							If Len(UpdateArea\SpawnScript$[i]) > 0 Then ThreadScript(UpdateArea\SpawnScript$[i], "Main", Handle(AI), 0)
						EndIf
					EndIf
				Else
						UpdateArea\Instances[j]\SpawnLast[i] = 0
					EndIf
				Next

				For AI.ActorInstance = Each ActorInstance
					If AI\RuntimeID > -1 And AI\RNID > 0
						AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
						If AInstance <> Null And AInstance\Area = UpdateArea
							; Portals
							For i = 0 To 99
								If Len(UpdateArea\PortalName$[i]) > 0
									If Len(UpdateArea\PortalLinkArea$[i]) > 0
										; Calculate distance
										Size# = UpdateArea\PortalSize#[i] * UpdateArea\PortalSize#[i]
										DistX# = Abs(AI\X# - UpdateArea\PortalX#[i])
										DistY# = Abs(AI\Y# - UpdateArea\PortalY#[i])
										DistZ# = Abs(AI\Z# - UpdateArea\PortalZ#[i])
										Dist# = (DistX# * DistX#) + (DistY# * DistY#) + (DistZ# * DistZ#)
										; Inside portal area
										; Lock keys on the (Area, index) pair: a stale
										; LastPortal index left over from a different
										; area must not block a same-index portal in the
										; current one, and the time floor still bars
										; immediate re-entry of the exact portal the
										; actor was just placed on.
										;
										; Lazy resolve LastPortalAreaName$ -> Handle.
										; The persisted form is the area NAME (Handles
										; are process-local); first time we see this
										; actor in a portal area after login, re-bind
										; the area handle. If the area was renamed /
										; deleted between sessions, leave the handle
										; at 0 and the lock just stays clear -- safe
										; direction.
										If AI\LastPortalArea = 0 And AI\LastPortalAreaName$ <> ""
											Local LpAr.Area = FindArea(AI\LastPortalAreaName$)
											If LpAr <> Null Then AI\LastPortalArea = Handle(LpAr)
										EndIf
										If Dist# < Size# And (AI\LastPortal <> i Or AI\LastPortalArea <> Handle(UpdateArea) Or (MilliSecs() - AI\LastPortalTime > PortalLockTime))
											InPortal = False
											Name$ = Upper$(UpdateArea\PortalLinkArea$[i])
											For Ar.Area = Each Area
												If Upper$(Ar\Name$) = Name$
													Name$ = Upper$(UpdateArea\PortalLinkName$[i])
													Port = 0
													If Len(Name$) > 0
														For j = 0 To 99
															If Upper$(Ar\PortalName$[j]) = Name$ Then Port = j : Exit
														Next
													EndIf
													SetArea(AI, Ar, 0, -1, Port)
													InPortal = True
													Exit
												EndIf
											Next
											If InPortal = True Then Exit
										EndIf
									EndIf
								EndIf
							Next

							; Triggers
							InTrigger = -1
							For i = 0 To 149
								If Len(UpdateArea\TriggerScript$[i]) > 0
									; Calculate distance
									Size# = UpdateArea\TriggerSize#[i] * UpdateArea\TriggerSize#[i]
									DistX# = Abs(AI\X# - UpdateArea\TriggerX#[i])
									DistY# = Abs(AI\Y# - UpdateArea\TriggerY#[i])
									DistZ# = Abs(AI\Z# - UpdateArea\TriggerZ#[i])
									Dist# = (DistX# * DistX#) + (DistY# * DistY#) + (DistZ# * DistZ#)
									; Inside trigger area
									If Dist# < Size#
										InTrigger = i
										; Only execute script if this trigger was not already executed
										If AI\LastTrigger <> i
											ThreadScript(UpdateArea\TriggerScript$[i], UpdateArea\TriggerMethod$[i], Handle(AI), 0, Str$(i))
										EndIf
									EndIf
								EndIf
							Next
							AI\LastTrigger = InTrigger
						EndIf
					EndIf
				Next
			EndIf
		Next

		; Update actor instances
		If MilliSecs() - LastWorldUpdate > WorldUpdateMS
			; Update actors with network broadcast
			If MilliSecs() - LastBroadcast > BroadcastMS
				UpdateActorInstances(True)
				LastBroadcast = MilliSecs()
			; Update actors without network broadcast
			Else
				UpdateActorInstances(False)
			EndIf
			LastWorldUpdate = MilliSecs()
		EndIf
	EndIf

	; Update time of day etc.
	If MinuteChanged = True
		Hour$ = Str$(TimeH)
		If Len(Hour$) = 1 Then Hour$ = "0" + Hour$
		Minute$ = Str$(TimeM)
		If Len(Minute$) = 1 Then Minute$ = "0" + Minute$
		SetGadgetText(Game\LTime, "Game time: " + Hour$ + ":" + Minute$)
	EndIf
	If HourChanged = True
		Month = GetMonth()
		If Month = 0
			Date$ = Str$(Day + 1)
		Else
			Date$ = Str$((Day - MonthStartDay(Month)) + 1)
		EndIf
		If Right$(Date$, 1) = "1" And Date$ <> "11"
			Date$ = Date$ + "st"
		ElseIf Right$(Date$, 1) = "2" And Date$ <> "12"
			Date$ = Date$ + "nd"
		ElseIf Right$(Date$, 1) = "3" And Date$ <> "13"
			Date$ = Date$ + "rd"
		Else
			Date$ = Date$ + "th"
		EndIf
		SetGadgetText(Game\LDate, "Game date: " + MonthName$(Month) + " " + Date$ + ", " + Str$(Year) + "  (" + SeasonName$(CurrentSeason) + ")")
	EndIf
	UpdateEnvironment()

	; Update spell memorisation progress.
	;
	; Bug fix: SpellCharge[] is keyed by spell ID 0..999 across the rest
	; of the codebase (see Actors.bb field comment and the unified
	; cooldown handling in ServerNet.bb's P_SpellUpdate "F" branch).
	; The old line `MS\AI\SpellCharge[i] = 0` indexed by the
	; memorise-slot 0..9, so completing any memorisation zeroed the
	; cooldown for spell IDs 0..9 -- making low-ID spells instantly
	; recastable. Zero the cooldown for the spell that was actually
	; learned instead.
	;
	; Also guard against the owning actor having been freed mid-
	; memorise (e.g. logout race): MemorisingSpell records were not
	; previously reaped on FreeActorInstance, so a stale MS\AI here
	; would null-deref on SpellLevels.
	For MS.MemorisingSpell = Each MemorisingSpell
		If MilliSecs() - MS\CreatedTime > 6000
			If MS\AI <> Null And MS\KnownNum >= 0 And MS\KnownNum <= 999
				If MS\AI\SpellLevels[MS\KnownNum] > 0
					For i = 0 To 9
						If MS\AI\MemorisedSpells[i] = 5000
							MS\AI\MemorisedSpells[i] = MS\KnownNum
							MS\AI\SpellCharge[MS\KnownNum] = 0
							Exit
						EndIf
					Next
				EndIf
			EndIf
			Delete MS
		EndIf
	Next

	; Scripts
		UpdateScripts()
Forever


; Functions -------------------------------------------------------------------------------------------------------------------------

; Preload all spawns on server startup, making loads in game faster
Function PreLoadSpawns()
Local Count = 0
For ua.Area = Each Area
For j = 0 To 99
If ua\Instances[j] <> Null
For i = 0 To 999
If ua\Instances[j]\Spawned[i] < ua\SpawnMax[i]
; Skip spawn points whose actor race no longer exists in Actors.dat.
; CreateActorInstance(Null) RuntimeError's; the live spawner at the
; main game loop already guards this (Server.bb:547) but PreLoadSpawns
; -- called once at startup as an optimisation -- skipped the check.
; A single misconfigured area (admin deleted the race but left
; SpawnActor pointing at it) prevented the server from booting.
; Bound-check SpawnActor[i] before the ActorList read -- ReadShort
; can carry a negative or >65535 value from a corrupt area file,
; which would Dim-OOB before the `= Null` branch is even reached.
If ua\SpawnActor[i] < 0 Or ua\SpawnActor[i] > 65535
WriteLog(MainLog, "PreLoadSpawns: skipping spawn point " + i + " in '" + ua\Name$ + "' (instance " + j + "): ActorID " + ua\SpawnActor[i] + " out of range")
ElseIf ActorList(ua\SpawnActor[i]) = Null
WriteLog(MainLog, "PreLoadSpawns: skipping spawn point " + i + " in '" + ua\Name$ + "' (instance " + j + "): ActorID " + ua\SpawnActor[i] + " not in Actors.dat")
Else
For h = 0 To ua\SpawnMax[i] - 1
Delay 10
AI.ActorInstance = CreateActorInstance.ActorInstance(ActorList(ua\SpawnActor[i]))
AI\RNID = -1
AssignRuntimeID(AI)
SetArea(AI, ua, j, ua\SpawnWaypoint[i])
AI\X# = AI\X# + Rnd#(ua\SpawnSize#[i] / -2.0, ua\SpawnSize#[i] / 2.0)
AI\Z# = AI\Z# + Rnd#(ua\SpawnSize#[i] / -2.0, ua\SpawnSize#[i] / 2.0)
ua\Instances[j]\Spawned[i] = ua\Instances[j]\Spawned[i] + 1
ua\Instances[j]\SpawnLast[i] = MilliSecs()
AI\SourceSP = i
AI\AIMode = AI_Patrol
AI\CurrentWaypoint = ua\SpawnWaypoint[i]
AI\Script$ = ua\SpawnActorScript$[i]
AI\DeathScript$ = ua\SpawnDeathScript$[i]
If Len(ua\SpawnScript$[i]) > 0 Then ThreadScript(ua\SpawnScript$[i], "Main", Handle(AI), 0)
Count = Count + 1
Next
EndIf
EndIf
Next
EndIf
Next
Next
WriteLog(MainLog, "Loaded " + Count + " Spawns...")
End Function 



; Safely shuts down the server
Function Shutdown()
	WriteLog(MainLog, "Shutting down...")
	;Shutdown Script execution
	WriteLog(MainLog, "Beginning Shutdown script...")
	If FileType("Shutdown.rsl") = 1
		If BVM_InterpretString("Shutdown", "Main") = 1
			WriteLog(MainLog, "Shutdown script finished execution...")
		EndIf
	Else
		WriteLog(MainLog, "Shutdown script not present...")
	EndIf
	ggTrayDestroy()
	WriteLog(MainLog, "Taskbar notification area icon removed...")
	
	

	CleanActorEffects()
	WriteLog(MainLog, "Actor Effects cleaned...")
	
	SaveAccounts()
	WriteLog(MainLog, "Saved accounts...")
	SaveSuperGlobals("Data\Server Data\Superglobals.dat")
	WriteLog(MainLog, "Saved superglobal variables...")
	;For A.Area = Each Area : ServerSaveAreaOwnerships(A) : Next ;{##}
	WriteLog(MainLog, "Saved zone ownerships...")
	SaveEnvironment()
	WriteLog(MainLog, "Saved environment settings...")
	SaveDroppedItems("Data\Server Data\Dropped Items.dat")
	WriteLog(MainLog, "Saved dropped items...")

	WriteLog(MainLog, "Network port scheduled for closing...")
	WriteLog(MainLog, "** Server shut down **", True, True)
	CloseAllLogs()
	RCE_Disconnect()

End Function

; Puts a gadget in the centre of its group
Function CentreGadget(G)

	CX = ClientWidth(GadgetGroup(G)) / 2 : CY = ClientHeight(GadgetGroup(G)) / 2
	SetGadgetShape G, CX - (GadgetWidth(G) / 2), CY - (GadgetHeight(G) / 2), GadgetWidth(G), GadgetHeight(G)

End Function

; Encrypts/decrypts a string (crappily but well enough to negate casual packet sniffing)
Function Encrypt$(S$, Reverse = -1)
  O$ = ""
  For i = 1 To Len(S$)
    O$ = Chr$(Asc(Mid$(S$, i, 1)) + (26 * Reverse)) + O$
  Next
  Return O$
End Function

; Loads dropped items from file
Function LoadDroppedItems(Filename$)

	Items = 0

	F = ReadFile(Filename$)
	If F = 0 Then Return 0

		While Eof(F) = False
			D.DroppedItem = New DroppedItem
			; Bound Zone$ (area name) -- corrupted DroppedItems.dat with
			; a wild length prefix would hang the server at boot. Same
			; shape as the broader data-loader sweep.
			Zone$ = ReadBoundedString$(F, 256)
			Instance = ReadByte(F)
			; AreaInstance Instances is Dim'd 0..99. ReadByte returns 0..255;
			; a corrupted DroppedItems.dat with Instance >= 100 triggers OOB
			; on every server boot. Treat out-of-range as instance 0 (the
			; default instance that always exists).
			If Instance < 0 Or Instance > 99 Then Instance = 0
			For Ar.Area = Each Area
				If Ar\Name$ = Zone$
					If Ar\Instances[Instance] = Null Then ServerCreateAreaInstance(Ar, Instance)
					D\ServerHandle = Handle(Ar\Instances[Instance])
					Exit
				EndIf
			Next
			; Item-instance binary is 83 bytes today (ItemInstanceStringLength);
			; cap to 256 to cover the current format with comfortable
			; headroom and still reject a wild length prefix.
			D\Item = ItemInstanceFromString(ReadBoundedString$(F, 256))
			D\Amount = ReadShort(F)
			D\X# = ReadFloat#(F)
			D\Y# = ReadFloat#(F)
			D\Z# = ReadFloat#(F)
			; Drop entries with no resolvable item (item ID removed from
			; data files between sessions). The next pickup would otherwise
			; null-deref D\Item\Item\Stackable.
			If D\Item = Null
				Delete D
			Else
				Items = Items + 1
			EndIf
		Wend

	CloseFile F
	Return Items

End Function

; Saves dropped items to file atomically.
; See SafeWriteOpen / SafeWriteCommit in Logging.bb.
Function SaveDroppedItems(Filename$)

	Local TempPath$ = SafeWriteOpen(Filename$)
	F = WriteFile(TempPath$)
	If F = 0
		WriteLog(MainLog, "SaveDroppedItems: cannot open " + TempPath$ + " for write")
		Return False
	EndIf

		For D.DroppedItem = Each DroppedItem
			AInstance.AreaInstance = Object.AreaInstance(D\ServerHandle)
			; Skip dropped items whose host area instance is gone (zone
			; unload race, freed area). Writing AInstance\Area\Name$ on
			; a Null handle previously crashed the server during the
			; periodic SaveDroppedItems flush -- the entire save aborts
			; and dropped item state is lost.
			If AInstance = Null Then Continue
			If AInstance\Area = Null Then Continue
			If D\Item = Null Then Continue
			WriteString F, AInstance\Area\Name$
			WriteByte F, AInstance\ID
			WriteString F, ItemInstanceToString$(D\Item)
			WriteShort F, D\Amount
			WriteFloat F, D\X#
			WriteFloat F, D\Y#
			WriteFloat F, D\Z#
		Next

	Return SafeWriteCommit(TempPath$, Filename$, F)

End Function

; Assigns a new runtime ID to an actor instance.
;
; RuntimeIDList is Dim'd 0..65535. The original implementation
; auto-incremented LastRuntimeID and wrapped to 0 after 65534
; without checking whether the candidate slot was already in use --
; so on a long-running server (one that's churned more than 65535
; actors across its lifetime), the wrap reassigns the RuntimeID
; of a still-live actor, and every subsequent wire packet
; referencing the old ID hits the new actor instead. This shows up
; as cross-target damage, mis-targeted spells, or apparent state
; corruption that's actually packet routing.
;
; Scan forward from LastRuntimeID to find the next genuinely-free
; slot. The 65536-slot table makes a worst-case full sweep cheap
; (the body is a Null comparison); we early-exit on the first hit.
; If the table is somehow full, log + reuse the original slot --
; the wrap-clobber bug above, but at least it's now visible.
Function AssignRuntimeID(A.ActorInstance)

	Local Start = LastRuntimeID
	Local Tried = 0
	While RuntimeIDList(LastRuntimeID) <> Null
		LastRuntimeID = LastRuntimeID + 1
		If LastRuntimeID > 65534 Then LastRuntimeID = 0
		Tried = Tried + 1
		If Tried > 65535
			WriteLog(MainLog, "AssignRuntimeID: RuntimeIDList full (65536 actors); reusing slot " + Start)
			LastRuntimeID = Start
			Exit
		EndIf
	Wend
	A\RuntimeID = LastRuntimeID
	RuntimeIDList(LastRuntimeID) = A
	LastRuntimeID = LastRuntimeID + 1
	If LastRuntimeID > 65534 Then LastRuntimeID = 0

End Function

; Updates an attribute and tells all players in zone about it
Function UpdateAttribute(AI.ActorInstance, Att, Value)
	AI\Attributes\Value[Att] = Value
	If AI\Attributes\Value[Att] > AI\Attributes\Maximum[Att]
		AI\Attributes\Value[Att] = AI\Attributes\Maximum[Att]
	EndIf
	If AI\RNID > 0 Or AI\RNID = -1
		Pa$ = "A" + RCE_StrFromInt$(AI\RuntimeID, 2) + RCE_StrFromInt$(Att, 1) + RCE_StrFromInt$(AI\Attributes\Value[Att], 2)
		AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
		If AInstance <> Null
			A2.ActorInstance = AInstance\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_StatUpdate, Pa$, True)
				A2 = A2\NextInZone
			Wend
		EndIf
	EndIf
End Function

; Updates an attribute maximum and tells all players in zone about it
Function UpdateAttributeMax(AI.ActorInstance, Att, Value)
	AI\Attributes\Maximum[Att] = Value

	If AI\RNID > 0 Or AI\RNID = -1
		Pa$ = "M" + RCE_StrFromInt$(AI\RuntimeID, 2) + RCE_StrFromInt$(Att, 1) + RCE_StrFromInt$(Value, 2)
		AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
		If AInstance <> Null
			A2.ActorInstance = AInstance\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_StatUpdate, Pa$, True)
				A2 = A2\NextInZone
			Wend
		EndIf
	EndIf
End Function

; Updates reputation and tells all players in zone about it
Function UpdateReputation(AI.ActorInstance, Value)

	AI\Reputation = Value
	If AI\RNID > 0
		Pa$ = "R" + RCE_StrFromInt$(AI\RuntimeID, 2) + RCE_StrFromInt$(Value, 2)
		AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
		If AInstance <> Null
			A2.ActorInstance = AInstance\FirstInZone
			While A2 <> Null
				If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_StatUpdate, Pa$, True)
				A2 = A2\NextInZone
			Wend
		EndIf
	EndIf
End Function
