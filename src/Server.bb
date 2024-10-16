; This application should be compiled with BlitzPlus
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
Include "Modules\Scripting.bb"            ; Script language module
Include "Modules\Logging.bb"              ; Logging module
Include "Modules\AccountsServer.bb"       ; Accounts server module
Include "Modules\GameServer.bb"           ; Game server module
Include "Modules\UpdatesServer.bb"        ; Updates server module
Include "Modules\Packets.bb"              ; Packet types module
Include "Modules\ServerNet.bb"            ; Server specific network module
Include "Modules\MySQL.bb"				  ; MySQL functions

; Variables -------------------------------------------------------------------------------------------------------------------------

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
AppTitle("Realm Crafter Engine BVM Server")
AutoSuspend(False)
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
ggTrayCreate(QueryObject(Accounts\Window, 1))
ggTraySetIconFromFile("Data\Server Data\TaskbarIcon.ico")
ggTraySetToolTip("Realm Crafter Server is running")

ggTrayShowIcon()
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
	SetPanelImage(Updates\LockPanel, "Data\Server Data\GreenLight.bmp")

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
	; Process taskbar notification area icon events
	If ggTrayPeekLeftDblClick() > 0
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
	ggTrayClearEvents()

	; Process window events
	Select WaitEvent(2)
		; One of the windows has been closed
		Case $803
			If Confirm("Really shut down server?", True) = True Then Shutdown() : End
		; Gadget event
		Case $401
			Select EventSource()
;				 Refresh scripts
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
								If AInstance\Area = GameArea
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
					Name$ = GadgetItemText$(Game\AreaCombo, EventData())
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
							If AInstance\Area = GameArea
								AddGadgetItem(Game\PlayersList, AI\Name$ + " (" + AInstance\ID + ")")
							EndIf
						EndIf
					Next
				; Change chat logging mode
				Case Game\ChatLogMode
					ChatLoggingMode = EventData()
				; Flush chat log
				Case Game\ChatLogFlushButton
					StopLog(ChatLog)
					DeleteFile("Data\Logs\Chat Log.txt")
					ChatLog = StartLog("Chat Log", True)
					WriteLog(ChatLog, "** Log flushed **", True, True)
				; Toggle DM status
				Case Accounts\DMButton
					A.Account = First Account
					For i = 1 To SelectedGadgetItem(Accounts\List) : A = After A : Next
					If A <> Null Then SetAccountDMStatus(A, Not A\IsDM)
				; Toggle ban status
				Case Accounts\BanButton
					A.Account = First Account
					For i = 1 To SelectedGadgetItem(Accounts\List) : A = After A : Next
					If A <> Null Then SetAccountBanStatus(A, Not A\IsBanned)
				; Remove account
				Case Accounts\DeleteButton
					If Confirm("Really remove account?") = True
						A.Account = First Account
						For i = 1 To SelectedGadgetItem(Accounts\List) : A = After A : Next
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
						SetPanelImage(Updates\LockPanel, "Data\Server Data\RedLight.bmp")

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
						SetPanelImage(Updates\LockPanel, "Data\Server Data\GreenLight.bmp")

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
	End Select


	; Things to do only if the server is unlocked
	If ServerLocked = False
		; Process network messages
		RCE_Update()
		RCE_CreateMessages()

		UpdateNetwork()
		My_UpdateThreads()

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
							 If ActorList(UpdateArea\SpawnActor[i]) <> Null
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
						If AInstance\Area = UpdateArea
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
										If Dist# < Size# And (AI\LastPortal <> i Or (MilliSecs() - AI\LastPortalTime > PortalLockTime))
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

	; Update spell memorisation progress
	For MS.MemorisingSpell = Each MemorisingSpell
		If MilliSecs() - MS\CreatedTime > 6000
			If MS\AI\SpellLevels[MS\KnownNum] > 0
				For i = 0 To 9
					If MS\AI\MemorisedSpells[i] = 5000
						MS\AI\MemorisedSpells[i] = MS\KnownNum
						MS\AI\SpellCharge[i] = 0
						Exit
					EndIf
				Next
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
			Zone$ = ReadString$(F)
			Instance = ReadByte(F)
			For Ar.Area = Each Area
				If Ar\Name$ = Zone$
					If Ar\Instances[Instance] = Null Then ServerCreateAreaInstance(Ar, Instance)
					D\ServerHandle = Handle(Ar\Instances[Instance])
					Exit
				EndIf
			Next
			D\Item = ItemInstanceFromString(ReadString$(F))
			D\Amount = ReadShort(F)
			D\X# = ReadFloat#(F)
			D\Y# = ReadFloat#(F)
			D\Z# = ReadFloat#(F)
			Items = Items + 1
		Wend

	CloseFile F
	Return Items

End Function

; Saves dropped items to file
Function SaveDroppedItems(Filename$)

	F = WriteFile(Filename$)
	If F = 0 Then Return False

		For D.DroppedItem = Each DroppedItem
			AInstance.AreaInstance = Object.AreaInstance(D\ServerHandle)
			WriteString F, AInstance\Area\Name$
			WriteByte F, AInstance\ID
			WriteString F, ItemInstanceToString$(D\Item)
			WriteShort F, D\Amount
			WriteFloat F, D\X#
			WriteFloat F, D\Y#
			WriteFloat F, D\Z#
		Next

	CloseFile(F)
	Return True

End Function

; Assigns a new runtime ID to an actor instance
Function AssignRuntimeID(A.ActorInstance)

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
		A2.ActorInstance = AInstance\FirstInZone
		While A2 <> Null
			If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_StatUpdate, Pa$, True)
			A2 = A2\NextInZone
		Wend
	EndIf
End Function

; Updates an attribute maximum and tells all players in zone about it
Function UpdateAttributeMax(AI.ActorInstance, Att, Value)
	AI\Attributes\Maximum[Att] = Value

	If AI\RNID > 0 Or AI\RNID = -1 
		Pa$ = "M" + RCE_StrFromInt$(AI\RuntimeID, 2) + RCE_StrFromInt$(Att, 1) + RCE_StrFromInt$(Value, 2)
		AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
		A2.ActorInstance = AInstance\FirstInZone
		While A2 <> Null
			If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_StatUpdate, Pa$, True)
			A2 = A2\NextInZone
		Wend
	EndIf
End Function

; Updates reputation and tells all players in zone about it
Function UpdateReputation(AI.ActorInstance, Value)

	AI\Reputation = Value
	If AI\RNID > 0
		Pa$ = "R" + RCE_StrFromInt$(AI\RuntimeID, 2) + RCE_StrFromInt$(Value, 2)
		AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
		A2.ActorInstance = AInstance\FirstInZone
		While A2 <> Null
			If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_StatUpdate, Pa$, True)
			A2 = A2\NextInZone
		Wend
	EndIf
End Function