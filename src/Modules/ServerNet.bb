;##############################################################################################################################
;HISTORY:
;
;  by Rob W (rottbott), August 2004
;
;Actor moves/change areas standard update timing fix. 8/16/2007 Rofar.  added  handling messages for change area and position actor completion
;##############################################################################################################################



Type QueuedPacket
	Field Connection, Destination, PacketType, Pa$, ReliableFlag, PlayerFrom
	Field NextInQueue.QueuedPacket, PreviousInQueue.QueuedPacket
	Field PreviousSentTime
End Type

; Sends the in-game /help output to the requesting player. Groups the
; 30 built-in slash commands by category (Chat, Party, Social, Info,
; DM-only) and filters DM-only entries when the caller isn't a DM.
;
; The Description text is hard-coded English for the moment -- the
; existing LanguageString$ table is line-indexed and adding new IDs
; for help-text strings requires touching Language.txt. Localising
; the help payload is a follow-up; the layout below is designed so
; a single InitChatHelpStrings() call can replace the literals when
; the localization slots exist.
;
; Each line is sent as a separate P_ChatMessage with the Chr$(254)
; "system message" prefix the rest of the dispatcher uses for
; server-originated chat (matches /time, /season, etc.).
Function SendChatHelp(AI.ActorInstance, Topic$)
	Local IsDM% = False
	A.Account = Object.Account(AI\Account)
	If A <> Null And A\IsDM = True Then IsDM = True

	; Detail mode: /help <command>
	Local T$ = Upper$(Trim$(Topic$))
	If Len(T) > 0
		SendChatHelpDetail(AI, T, IsDM)
		Return
	EndIf

	; Header
	RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + "--- Available slash commands ---", True)
	RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + "Use /help <command> for details on a specific command.", True)

	; Chat
	RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + "Chat: /me /yell /pm /g /p", True)
	; Party
	RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + "Party: /invite /accept /leave /pet", True)
	; Social
	RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + "Social: /ignore /unignore /players /allplayers /trade", True)
	; Info
	RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + "Info: /time /date /season", True)

	; DM commands -- only listed for DMs
	If IsDM = True
		RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + "DM-only: /kick /xp /gold /setattribute /setattributemax /script /gm /warp /warpother /ability /give /weather /netdump", True)
	EndIf
End Function

; Per-command detail for /help <command>. Centralised here so the
; command-name registry is the only thing that needs touching when a
; command is added.
Function SendChatHelpDetail(AI.ActorInstance, T$, IsDM%)
	Local Line$ = ""
	; Built-in non-DM commands
	If T = "ME" Then Line = "/me <action> -- emote action in current area"
	If T = "YELL" Then Line = "/yell <text> -- shout to every online player on the server"
	If T = "PM" Then Line = "/pm <player>,<text> -- private message"
	If T = "G" Then Line = "/g <text> -- guild chat"
	If T = "P" Then Line = "/p <text> -- party chat"
	If T = "INVITE" Then Line = "/invite <player> -- invite player to party"
	If T = "ACCEPT" Then Line = "/accept -- accept a pending party invite"
	If T = "LEAVE" Then Line = "/leave -- leave current party"
	If T = "PET" Then Line = "/pet <name>,<command>[,<params>] -- command a pet (or 'all')"
	If T = "IGNORE" Then Line = "/ignore <player> -- silence a player's chat"
	If T = "UNIGNORE" Then Line = "/unignore <player> -- re-enable a player's chat"
	If T = "PLAYERS" Then Line = "/players -- list players in your area"
	If T = "ALLPLAYERS" Then Line = "/allplayers -- list all online players"
	If T = "TRADE" Then Line = "/trade <player> -- open trade with player"
	If T = "TIME" Then Line = "/time -- show in-world clock time"
	If T = "DATE" Then Line = "/date -- show in-world date"
	If T = "SEASON" Then Line = "/season -- show current season"
	If T = "HELP" Or T = "?" Then Line = "/help [<command>] -- list commands, or detail on one"

	; DM-only commands -- only describe when caller has DM rights.
	; All "grant" commands target self -- the handlers in this file
	; uniformly call GiveXP/AddSpell/etc. on AI, never on a Params-named
	; player. /kick and /warpother are the exceptions that take a target.
	If IsDM = True
		If T = "KICK" Then Line = "/kick <player> -- disconnect a player"
		If T = "XP" Then Line = "/xp <amount> -- grant XP to self"
		If T = "GOLD" Then Line = "/gold <amount> -- adjust own gold (negative to deduct)"
		If T = "SETATTRIBUTE" Then Line = "/setattribute <attr>,<value> -- set own attribute"
		If T = "SETATTRIBUTEMAX" Then Line = "/setattributemax <attr>,<value> -- set own max attribute"
		If T = "SCRIPT" Then Line = "/script <name>,<func> -- run script's function as self"
		If T = "GM" Then Line = "/gm <text> -- broadcast as GM to all DMs"
		If T = "WARP" Then Line = "/warp <area>[,<instance>] -- warp self to area's first portal"
		If T = "WARPOTHER" Then Line = "/warpother <player>,<area>[,<instance>] -- warp another player"
		If T = "ABILITY" Then Line = "/ability <ability>,<level> -- grant own ability at level"
		If T = "GIVE" Then Line = "/give <item> -- spawn item into own inventory"
		If T = "WEATHER" Then Line = "/weather <type> -- set weather in current area"
		If T = "NETDUMP" Then Line = "/netdump -- start a network packet log"
	EndIf

	If Len(Line) = 0
		RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + "No help available for /" + Lower$(T) + ".  Type /help for a list.", True)
	Else
		RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + Line, True)
	EndIf
End Function

; Queues a packet (queued packets are delayed so that for each destination, only one is sent per 12 milliseconds)
Function SendQueued(Connection, Destination, PacketType, Pa$, ReliableFlag = False, PlayerFrom = 0)

	; Create packet
	Q.QueuedPacket = New QueuedPacket
	Q\Connection = Connection
	Q\Destination = Destination
	Q\PacketType = PacketType
	Q\Pa$ = Pa$
	Q\ReliableFlag = ReliableFlag
	Q\PlayerFrom = PlayerFrom
	Q\PreviousSentTime = MilliSecs() - 8

	; Attempt to find previous packet in queue
	For Q2.QueuedPacket = Each QueuedPacket
		If Q2\NextInQueue = Null And Q2\Destination = Destination And Q2\Connection = Connection
			If Q2 <> Q
				Q2\NextInQueue = Q
				Q\PreviousInQueue = Q2
				Exit
			EndIf
		EndIf
	Next

End Function

; Processes all network messages
Function UpdateNetwork()

;	; Network data logging
;	If LogNetwork > 0
;		; Write to file
;		If MilliSecs() - LogNetworkTime >= 5000
;			Players = 0
;			For AI.ActorInstance = Each ActorInstance
;				If AI\RNID > 0 Then Players = Players + 1
;			Next
;			TrafficIn = (RN_BytesReceived(Host) - LogNetworkBytesIn) / 5
;			TrafficOut = (RN_BytesSent(Host) - LogNetworkBytesOut) / 5
;			L = StartLog("Network Data Dump")
;				WriteLog(L, "Players in game: " + Players)
;				WriteLog(L, "Average traffic in: " + TrafficIn + " bytes/second", False)
;				WriteLog(L, "Average traffic out: " + TrafficOut + " bytes/second", False)
;			StopLog(L)
;			LogNetworkBytesIn = RN_BytesReceived(Host)
;			LogNetworkBytesOut = RN_BytesSent(Host)
;			LogNetwork = LogNetwork - 1
;			LogNetworkTime = MilliSecs()
;		EndIf
;	EndIf

	; Send off any queued messages
	For Q.QueuedPacket = Each QueuedPacket
		If Q\PreviousInQueue = Null
			If MilliSecs() - Q\PreviousSentTime >= 12
				; Send it
				RCE_Send(Q\Connection, Q\Destination, Q\PacketType, Q\Pa$, Q\ReliableFlag, Q\PlayerFrom)
				
				; Tell next in queue when this one was sent
				If Q\NextInQueue <> Null Then Q\NextInQueue\PreviousSentTime = MilliSecs()

				; Remove from queue
				Delete(Q)
			EndIf
		EndIf
	Next

	; Incoming messages
	For M.RCE_Message = Each RCE_Message
		Select M\MessageType

			; Chat message
			Case P_ChatMessage
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If Len(M\MessageData$) > 0 And AI <> Null
					; Command
					If Left$(M\MessageData$, 1) = "/" Or Left$(M\MessageData$, 1) = "\"
						Command$ = Mid$(M\MessageData$, 2)
						SpacePos = Instr(Command$, " ")
						If SpacePos > 0
							Params$ = Trim$(Mid$(Command$, SpacePos + 1))
							Command$ = Upper$(Left$(Command$, SpacePos - 1))
						Else
							Command$ = Upper$(Command$)
							Params$ = ""
						EndIf
						Select Command$
							Case LanguageString$(LS_SCKick)
								A.Account = Object.Account(AI\Account)
								; Stale Account handle (mid-logout, freed account) returns
								; Null from Object.Account -- bare A\IsDM crashes the server
								; from a chat command. Guard every /command's DM gate.
								If A <> Null And A\IsDM = True
									A2.ActorInstance = FindActorInstanceFromName(Params$)
									If A2 <> Null 
										If A2\RNID > 0
											DataAux$ = RCE_StrFromInt(A2\RNID)
											RCE_FSend(0, RCE_PlayerKicked, DataAux$, True, Len(DataAux$))
											RCE_FSend(A2\RNID, P_KickedPlayer, "", True, 0)
										EndIf
									EndIf
								EndIf
							Case LanguageString$(LS_SCUnIgnore)
								A2.ActorInstance = FindActorInstanceFromName(Params$)
								If A2 <> Null And A2 <> AI
									If A2\RNID >= 0
										Pos = PlayerIgnoring(AI, A2)
										If Pos > 0
											Ac1.Account = Object.Account(AI\Account)
											; Stale Account handle returns Null; bare
											; \Ignore$ crashes the server.
											If Ac1 <> Null
												EndPos = Instr(Ac1\Ignore$, ",", Pos)
												Ac1\Ignore$ = Left$(Ac1\Ignore$, Pos - 1) + Mid$(Ac1\Ignore$, EndPos + 1)
												RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(253) + LanguageString$(LS_UnIgnoring) + " " + Params$, True)
											EndIf
										EndIf
									EndIf
								EndIf
							Case LanguageString$(LS_SCIgnore)
								A2.ActorInstance = FindActorInstanceFromName(Params$)
								If A2 <> Null And A2 <> AI
									If A2\RNID >= 0
										If PlayerIgnoring(AI, A2) = 0
											Ac1.Account = Object.Account(AI\Account)
											Ac2.Account = Object.Account(A2\Account)
											; Either side's Account can be stale.
											If Ac1 <> Null And Ac2 <> Null
												Ac1\Ignore$ = Ac1\Ignore$ + Ac2\User$ + ","
											EndIf
										EndIf
										RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(253) + LanguageString$(LS_Ignoring) + " " + Params$, True)
									EndIf
								EndIf
							Case LanguageString$(LS_SCNetDump)
								A.Account = Object.Account(AI\Account)
								If A <> Null And LogNetwork = False And A\IsDM = True
									RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + "Starting new net dump...", True)
									L = StartLog("Network Data Dump")
										WriteLog(L, "Starting new net dump...", True, True)
									StopLog(L)
									LogNetwork = 6
									LogNetworkTime = MilliSecs()
									;LogNetworkBytesIn = RCE_BytesReceived(Host)
									;LogNetworkBytesOut = RCE_BytesSent(Host)
								EndIf
							Case LanguageString$(LS_SCPet)
								If AI\NumberOfSlaves > 0
									Name$ = Upper$(Trim$(Split$(Params$, 1, ",")))
									Command$ = Trim$(Split$(Params$, 2, ","))
									PetParams$ = Trim$(Split$(Params$, 3, ","))
									; Walk AI's FirstSlave chain. The chain
									; contains only AI's pets, so the explicit
									; Leader filter and the NumberOfSlaves
									; early-exit are no longer needed.
									Local AI2.ActorInstance = AI\FirstSlave
									While AI2 <> Null
										If Upper$(AI2\Name$) = Name$ Or Name$ = "ALL"
											CommandPet(AI2, Command$, PetParams$)
											If Name$ <> "ALL" Then Exit
										EndIf
										AI2 = AI2\NextSlave
									Wend
								EndIf
							Case LanguageString$(LS_SCLeave)
								LeaveParty(AI)
							Case LanguageString$(LS_SCOk)
								Party.Party = Object.Party(AI\AcceptPending)
								If Party <> Null
									; Check there's a free space in the party
									If Party\Members < 8
										; Remove from old party if required
										LeaveParty(AI)
										; Add to new party and tell players
										For i = 0 To 7
											If Party\Player[i] <> Null
												RCE_Send(Host, Party\Player[i]\RNID, P_ChatMessage, Chr$(254) + AI\Name$ + " " + LanguageString$(LS_XHasJoinedParty), True)
											ElseIf AI\AcceptPending <> 0
												RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_YouHaveJoinedParty), True)
												AI\PartyID = Handle(Party)
												AI\AcceptPending = 0
												Party\Player[i] = AI
												Party\Members = Party\Members + 1
											EndIf
										Next
										For i = 0 To 7
											If Party\Player[i] <> Null Then SendPartyUpdate(Party\Player[i])
										Next
										; Run script
										ThreadScript("Party", "Join", Handle(AI), 0)
									; Party is full
									Else
										RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_CouldNotJoinParty), True)
										AI\AcceptPending = 0
									EndIf
								EndIf
							Case LanguageString$(LS_SCInvite)
								A2.ActorInstance = FindActorInstanceFromName(Params$)
								If A2 <> Null And A2 <> AI
									If A2\RNID > 0
										If PlayerIgnoring(A2, AI) = 0
											Party.Party = Object.Party(AI\PartyID)
											; Create new party if required
											If Party = Null
												Party = New Party
												AI\PartyID = Handle(Party)
												Party\Members = 1
												Party\Player[0] = AI
												ThreadScript("Party", "Join", Handle(AI), 0)
											EndIf
											; Check there's a free space in the party
											If Party\Members < 8
												A2\AcceptPending = Handle(Party)
												RCE_Send(Host, A2\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_PartyInvite) + " " + AI\Name$, True)
												RCE_Send(Host, A2\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_PartyInviteInstruction), True)
											; Party is full
											Else
												RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_CouldNotInviteParty), True)
											EndIf
										EndIf
									EndIf
								EndIf
							Case LanguageString$(LS_SCXP)
								A.Account = Object.Account(AI\Account)
								If A <> Null And A\IsDM = True Then GiveXP(AI, Int(Params$))
							Case LanguageString$(LS_SCGold)
								A.Account = Object.Account(AI\Account)
								If A <> Null And A\IsDM = True
									Change = Int(Params$)
									AI\Gold = AI\Gold + Change
									If Change > 0
										Pa$ = "U" + RCE_StrFromInt$(Change, 4)
									Else
										Pa$ = "D" + RCE_StrFromInt$(Abs(Change), 4)
									EndIf
									RCE_Send(Host, AI\RNID, P_GoldChange, Pa$, True)
								EndIf
							Case LanguageString$(LS_SCSetAttribute)
								A.Account = Object.Account(AI\Account)
								If A <> Null And A\IsDM = True
									Attribute = FindAttribute(Split$(Params$, 1, ","))
									If Attribute > -1
										If Attribute = HealthStat Or Attribute = SpeedStat Or Attribute = EnergyStat
											UpdateAttribute(AI, Attribute, Int(Split$(Params$, 2, ",")))
										Else
											AI\Attributes\Value[Attribute] = Int(Split$(Params$, 2, ","))
											Pa$ = RCE_StrFromInt$(AI\RuntimeID, 2) + RCE_StrFromInt$(Attribute, 1) + RCE_StrFromInt$(AI\Attributes\Value[Attribute], 2)
											RCE_Send(Host, AI\RNID, P_StatUpdate, "A" + Pa$, True)
										EndIf
									EndIf
								EndIf
							Case LanguageString$(LS_SCSetAttributeMax)
								A.Account = Object.Account(AI\Account)
								If A <> Null And A\IsDM = True
									Attribute = FindAttribute(Split$(Params$, 1, ","))
									If Attribute > -1
										If Attribute = HealthStat Or Attribute = SpeedStat Or Attribute = EnergyStat
											UpdateAttributeMax(AI, Attribute, Int(Split$(Params$, 2, ",")))
										Else
											AI\Attributes\Maximum[Attribute] = Int(Split$(Params$, 2, ","))
											Pa$ = RCE_StrFromInt$(AI\RuntimeID, 2) + RCE_StrFromInt$(Attribute, 1) + RCE_StrFromInt$(AI\Attributes\Maximum[Attribute], 2)
											RCE_Send(Host, AI\RNID, P_StatUpdate, "M" + Pa$, True)
										EndIf
									EndIf
								EndIf
							Case LanguageString$(LS_SCScript)
								A.Account = Object.Account(AI\Account)
								If A <> Null And A\IsDM = True
									Name$ = Trim$(Split$(Params$, 1, ","))
									Func$ = Trim$(Split$(Params$, 2, ","))
									; Privileged=1: this code path has verified
									; the invoker is a GM, so the spawned script
									; is allowed to call Ban/Kick/Warp/etc. via
									; BVM_RequirePrivileged().
									ThreadScript(Name$, Func$, Handle(AI), 0, "", 1)
								EndIf
							Case LanguageString$(LS_SCMe)
								Pa$ = Chr$(252) + "* " + AI\Name$ + " " + Params$
								AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
								If AInstance <> Null
									A2.ActorInstance = AInstance\FirstInZone
									While A2 <> Null
										If A2\RNID > 0
											If PlayerIgnoring(A2, AI) = 0
												RCE_Send(Host, A2\RNID, P_ChatMessage, Pa$, True)
											EndIf
										EndIf
										A2 = A2\NextInZone
									Wend
									If AInstance\Area = GameArea Then AddListBoxItem(Game\ChatText, Pa$ + Chr$(13))
								EndIf
							Case LanguageString$(LS_SCYell)
								Pa$ = Chr$(253) + "<" + AI\Name$ + "> " + Params$
								; Walk the FirstOnlinePlayer chain instead of
								; every ActorInstance. The chain only contains
								; RNID > 0 players, so the inner RNID filter is
								; gone.
								A2.ActorInstance = FirstOnlinePlayer
								While A2 <> Null
									If PlayerIgnoring(A2, AI) = 0
										RCE_Send(Host, A2\RNID, P_ChatMessage, Pa$, True)
									EndIf
									A2 = A2\NextOnlinePlayer
								Wend
								AddListBoxItem(Game\ChatText, Pa$ + Chr$(13))
							; The constant is `LS_SCGMSay` (= 205, "GM" in the
							; defaults block at Language.bb:256), not `LS_SCGM`.
							; Pre-fix the typo'd identifier read as 0 in this
							; non-Strict file, so the case matched
							; LanguageString$(0) (LS_ConnectingToServer) instead of
							; the slash-command "GM" -- the /gm DM-broadcast chat
							; command was unreachable. Renaming to the real
							; constant restores the dispatch the rest of the case
							; body was already coded against.
							Case LanguageString$(LS_SCGMSay)
								A.Account = Object.Account(AI\Account)
								If A <> Null And A\IsDM = True
									Pa$ = Chr$(254) + "<GM> <" + AI\Name$ + "> " + Params$
									; Online-player chain walk; DM filter still
									; needs the Account lookup.
									A2.ActorInstance = FirstOnlinePlayer
									While A2 <> Null
										A.Account = Object.Account(A2\Account)
										If A <> Null And A\IsDM = True Then RCE_Send(Host, A2\RNID, P_ChatMessage, Pa$, True)
										A2 = A2\NextOnlinePlayer
									Wend
								EndIf
							Case LanguageString$(LS_SCGuildSay)
								If AI\TeamID > 0
									Pa$ = Chr$(251) + "<G> <" + AI\Name$ + "> " + Params$
									; Online-player chain walk; team filter still
									; needed.
									A2.ActorInstance = FirstOnlinePlayer
									While A2 <> Null
										If A2\TeamID = AI\TeamID Then RCE_Send(Host, A2\RNID, P_ChatMessage, Pa$, True)
										A2 = A2\NextOnlinePlayer
									Wend
								EndIf
							Case LanguageString$(LS_SCPartySay)
								Party.Party = Object.Party(AI\PartyID)
								If Party <> Null
									Pa$ = Chr$(251) + "<PARTY> <" + AI\Name$ + "> " + Params$
									For i = 0 To 7
										If Party\Player[i] <> Null
											If Party\Player[i] <> AI Then RCE_Send(Host, Party\Player[i]\RNID, P_ChatMessage, Pa$, True)
										EndIf
									Next
								EndIf
							Case LanguageString$(LS_SCPMSay)
								Name$ = Upper$(Split$(Params$, 1, ","))
								Params$ = Split$(Params$, 2, ",")
								; Online-player chain walk for /pm target lookup.
								A2.ActorInstance = FirstOnlinePlayer
								While A2 <> Null
									If Upper$(A2\Name$) = Name$
										If PlayerIgnoring(A2, AI) = 0
											RCE_Send(Host, A2\RNID, P_ChatMessage, Chr$(252) + AI\Name$ + ": " + Params$, True)
										EndIf
										Exit
									EndIf
									A2 = A2\NextOnlinePlayer
								Wend
							Case LanguageString$(LS_SCTrade)
								; Player has been offered a trade and is accepting
								If AI\IsTrading = 3
									AI\IsTrading = 4
									; Character to trade with has been deleted
									If AI\TradingActor = Null
										AI\IsTrading = 0
										Goto OfferTrade
									; Or left the game
									ElseIf AI\TradingActor\RNID < 1
										AI\IsTrading = 0
										Goto OfferTrade
									; Both players are here and have accepted
									ElseIf AI\TradingActor\IsTrading = 4
										RCE_Send(Host, AI\RNID, P_OpenTrading, "11P", True)
										RCE_Send(Host, AI\TradingActor\RNID, P_OpenTrading, "11P", True)
									EndIf
								; Not currently trading, offer a trade
								ElseIf AI\IsTrading = 0
									.OfferTrade
									If Params$ <> ""
										A2.ActorInstance = FindPlayerFromName.ActorInstance(Params$)
										If A2 <> Null
											If A2\RNID > 0
												If A2\IsTrading = 0
													If PlayerIgnoring(A2, AI) = 0
														AI\IsTrading = 4
														A2\IsTrading = 3
														AI\TradingActor = A2
														A2\TradingActor = AI
														Pa$ = LanguageString$(LS_TradeInviteInstruction)
														ml = Len(Chr$(254) + LanguageString$(LS_TradeInvite) + " " + AI\Name$ + Pa$)
														RCE_Send(Host, A2\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_TradeInvite) + " " + AI\Name$ + Pa$, True)
													EndIf
												EndIf
											Else
												RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + Params$ + " " + LanguageString$(LS_XIsOffline), True)
											EndIf
										Else
											RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_PlayerNotFound) + " " + Params$, True)
										EndIf
									EndIf
								EndIf
							Case LanguageString$(LS_SCAllPlayers)
								; Count via online-player chain walk. The
								; chain only contains RNID > 0 players, so the
								; previous `If A2\RNID > 0` filter is redundant.
								Players = 0
								A2.ActorInstance = FirstOnlinePlayer
								While A2 <> Null
									Players = Players + 1
									A2 = A2\NextOnlinePlayer
								Wend
								RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_PlayersInGame) + " " + Str$(Players - 1), True)
							Case LanguageString$(LS_SCPlayers)
								Players = 0
								AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
								If AInstance <> Null
									A2.ActorInstance = AInstance\FirstInZone
									While A2 <> Null
										If A2\RNID > 0 Then Players = Players + 1
										A2 = A2\NextInZone
									Wend
								EndIf
								RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_PlayersInZone) + " " + Str$(Players - 1), True)
							Case LanguageString$(LS_SCWarp)
								A.Account = Object.Account(AI\Account)
								If A <> Null And A\IsDM = True
									Ar.Area = FindArea(Trim$(Split$(Params$, 1, ",")))
									If Ar <> Null
										Instance = Split$(Params$, 2, ",")
										For i = 0 To 99
											If Ar\PortalName$[i] <> ""
												SetArea(AI, Ar, Instance, -1, i)
												Exit
											EndIf
										Next
									EndIf
								EndIf
							Case LanguageString$(LS_SCWarpOther)
								A.Account = Object.Account(AI\Account)
								If A <> Null And A\IsDM = True
									Name$ = Upper$(Trim$(Split$(Params$, 1, ",")))
									; Online-player chain walk for /warpother
									; target lookup.
									A2.ActorInstance = FirstOnlinePlayer
									While A2 <> Null
										If Upper$(A2\Name$) = Name$
											Ar.Area = FindArea(Trim$(Split$(Params$, 2, ",")))
											; Sibling LS_SCWarp branch above
											; checks `Ar <> Null` before the
											; portal loop; this one didn't,
											; so a DM typo (/warpother Alice,
											; "Bad Name") crashed the entire
											; server.
											If Ar <> Null
												Instance = Split$(Params$, 3, ",")
												For i = 0 To 99
													If Ar\PortalName$[i] <> ""
														SetArea(A2, Ar, Instance, -1, i)
														Exit
													EndIf
												Next
											EndIf
											Exit
										EndIf
										A2 = A2\NextOnlinePlayer
									Wend
								EndIf
							Case LanguageString$(LS_SCAbility)
								A.Account = Object.Account(AI\Account)
								If A <> Null And A\IsDM = True
									Params$ = Upper$(Params$)
									Name$ = Trim$(SafeSplit$(Params$, 1, ","))
									Level = Trim$(SafeSplit$(Params$, 2, ","))
									For Sp.Spell = Each Spell
										If Upper$(Sp\Name$) = Name$ Then AddSpell(AI, Sp\ID, Level) : Exit
									Next
								EndIf
							Case LanguageString$(LS_SCGive)
								; Make sure it's a GM account
								A.Account = Object.Account(AI\Account)
								If A <> Null And A\IsDM = True
									; Find the requested item
									Params$ = Upper$(Params$)
									For It.Item = Each Item
										If Upper$(It\Name$) = Params$
											; Create the item
											II.ItemInstance = CreateItemInstance(It)
											II\Assignment = 1
											II\AssignTo = AI
											; Ask client to specify a slot to put it in
											Pa$ = RCE_StrFromInt$(It\ID, 2) + RCE_StrFromInt$(II\Assignment, 2)
											ml = Len("G" + RCE_StrFromInt$(Handle(II), 4) + Pa$)
											RCE_Send(Host, AI\RNID, P_InventoryUpdate, "G" + RCE_StrFromInt$(Handle(II), 4) + Pa$, True)
											Exit
										EndIf
									Next
								EndIf
							Case LanguageString$(LS_SCWeather)
								Params$ = Trim$(Upper$(Params$))
								; Make sure it's a GM account
								A.Account = Object.Account(AI\Account)
								If A <> Null And A\IsDM = True
									AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
									; Choose new weather
									Select Params$
										Case "SUN", "SUNNY", "NORMAL"
											AInstance\CurrentWeather = W_Sun
										Case "RAIN", "RAINY"
											AInstance\CurrentWeather = W_Rain
										Case "SNOW", "SNOWY"
											AInstance\CurrentWeather = W_Snow
										Case "FOG", "FOGGY"
											AInstance\CurrentWeather = W_Fog
										Case "WIND", "WINDY"
											AInstance\CurrentWeather = W_Wind
										Case "STORM", "STORMY", "THUNDER", "LIGHTNING"
											AInstance\CurrentWeather = W_Storm
									End Select
									AInstance\CurrentWeatherTime = Rand(2500, 10000)

									; Tell players in this area
									Pa$ = RCE_StrFromInt$(Handle(AInstance), 4) + RCE_StrFromInt$(AInstance\CurrentWeather, 1)
									AI.ActorInstance = AInstance\FirstInZone
									While AI <> Null
										If AI\RNID > 0 Then RCE_Send(Host, AI\RNID, P_WeatherChange, Pa$, True)
										AI = AI\NextInZone
									Wend

									; Force an update for all areas with weather linked to this area
									If AInstance\ID = 0
										For Ar.Area = Each Area
											If Ar\WeatherLinkArea = AInstance\Area
												For i = 0 To 99
													If Ar\Instances[i] <> Null
														Ar\Instances[i]\CurrentWeatherTime = 0
													EndIf
												Next
											EndIf
										Next
									EndIf
								EndIf
							Case LanguageString$(LS_SCTime)
								Hour$ = Str$(TimeH)
								If Len(Hour$) = 1 Then Hour$ = "0" + Hour$
								Minute$ = Str$(TimeM)
								If Len(Minute$) = 1 Then Minute$ = "0" + Minute$
								RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + "Time: " + Hour$ + ":" + Minute$, True)
							Case LanguageString$(LS_SCDate)
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
								Date$ = Chr$(254) + MonthName$(Month) + " " + Date$ + ", " + Str$(Year)
								RCE_Send(Host, AI\RNID, P_ChatMessage, Date$, True)
							Case LanguageString$(LS_SCSeason)
								ml = Len(Chr$(254) + LanguageString$(LS_Season) + " " + SeasonName$(GetSeason()))
								RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + LanguageString$(LS_Season) + " " + SeasonName$(GetSeason()), True)
							; In-game discoverability for the 30-command slash
							; system. English literals ("HELP" and "?") are
							; intentional pending the LS_SCHelp localization
							; entry -- the existing LanguageString$ table is
							; line-numbered and adding a new ID requires
							; touching Language.txt; deferred to a follow-up.
							Case "HELP", "?"
								SendChatHelp(AI, Params$)
							Default
								; Unknown command. Hand off to the project's
								; "In-game Commands" user script if it exists;
								; otherwise tell the player the command isn't
								; recognised. Without this, an unknown command
								; silently no-op'd and players had no signal.
								If ScriptExists%("In-game Commands") = True
									ThreadScript("In-game Commands", Command$, Handle(AI), 0, Params$)
								Else
									RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(254) + "Unknown command: /" + Lower$(Command$) + ".  Type /help for a list.", True)
								EndIf
						End Select
					; General chat - forward to other people in same area
					Else
						Pa$ = "<" + AI\Name$ + "> " + M\MessageData$
						AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
						If AInstance <> Null
							A2.ActorInstance = AInstance\FirstInZone
							While A2 <> Null
								If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_ChatMessage, Pa$, True)
								A2 = A2\NextInZone
							Wend
						EndIf
						If AInstance <> Null And AInstance\Area = GameArea
							AddListBoxItem(Game\ChatText, Pa$ + Chr$(13))
							If ChatLoggingMode > 0 Then WriteLog(ChatLog, Pa$, True, True)
						ElseIf ChatLoggingMode = 2
							WriteLog(ChatLog, Pa$, True, True)
						EndIf
					EndIf
				EndIf

			; Repositioning an actor (client has completed)
			Case P_RepositionActor
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null
					AI\IgnoreUpdate = 0
				EndIf
			
			; Client has completed zoning for a player
			Case P_ChangeArea
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null
					AI\IgnoreUpdate = 0
				EndIf

			; Scenery item selected {##}
			;Case P_SelectScenery
			;	AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
			;	If AI <> Null
			;		ID = RCE_IntFromStr(Left$(M\MessageData$, 2))
			;		If ID > 0 And ID <= 500
			;			AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
			;			If AInstance\OwnedScenery[ID - 1] <> Null
			;				A.Account = Object.Account(AI\Account)
			;				If AInstance\OwnedScenery[ID - 1]\AccountName$ = A\User$
			;					If AInstance\OwnedScenery[ID - 1]\CharNumber = A\LoggedOn
			;						; Allow scenery animation
			;						RCE_Send(Host, AI\RNID, P_SelectScenery, Right$(M\MessageData$, 4), True)
;
;									; Open trading window
;									If AI\IsTrading = 0 And AInstance\OwnedScenery[ID - 1]\InventorySize > 0
;										AI\IsTrading = 1
;										AI\TradeResult$ = RCE_StrFromInt$(ID - 1, 2) + RCE_StrFromInt$(Handle(Ar), 4)
;										Pa$ = "S"
;										For i = 0 To AInstance\OwnedScenery[ID - 1]\InventorySize - 1
;											If AInstance\OwnedScenery[ID - 1]\Inventory\Amounts[i] > 0 And AInstance\OwnedScenery[ID - 1]\Inventory\Items[i] <> Null
;												CopiedII.ItemInstance = CopyItemInstance(AInstance\OwnedScenery[ID - 1]\Inventory\Items[i])
;												CopiedII\Assignment = AInstance\OwnedScenery[ID - 1]\Inventory\Amounts[i]
;												CopiedII\AssignTo = AI
;												Pa$ = Pa$ + ItemInstanceToString$(AInstance\OwnedScenery[ID - 1]\Inventory\Items[i])
;												Pa$ = Pa$ + RCE_StrFromInt$(AInstance\OwnedScenery[ID - 1]\Inventory\Amounts[i], 2) + RCE_StrFromInt$(Handle(CopiedII), 4)
;											EndIf
;										Next
;										If Len(Pa$) < 999
;											RCE_Send(Host, AI\RNID, P_OpenTrading, "11" + Pa$, True)
;										ElseIf Len(Pa$) < 1998
;											SendQueued(Host, AI\RNID, P_OpenTrading, "12" + Left$(Pa$, 998), True)
;											SendQueued(Host, AI\RNID, P_OpenTrading, "22" + Mid$(Pa$, 999), True)
;										Else
;											SendQueued(Host, AI\RNID, P_OpenTrading, "13" + Left$(Pa$, 998), True)
;											SendQueued(Host, AI\RNID, P_OpenTrading, "23" + Mid$(Pa$, 999, 998), True)
;											SendQueued(Host, AI\RNID, P_OpenTrading, "33" + Mid$(Pa$, 1998), True)
;										EndIf
;									EndIf
;								EndIf
;							EndIf
;						EndIf
;					EndIf
;				EndIf

			; Update player -> player trading
			Case P_UpdateTrading
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null
					; Reject self-trade: if AI\TradingActor somehow points
					; back at AI (script bug, logout race, or wire injection)
					; the slot loop below aliases source = destination and
					; the accept path later tries to consume the same slot
					; twice. Also require reciprocal binding: A's partner
					; must claim A as its own partner -- otherwise an
					; injection that sets only one side's TradingActor
					; would let A drain inventory into a one-way channel.
					If AI\IsTrading = 4 And AI\TradingActor <> Null And AI\TradingActor <> AI And AI\TradingActor\TradingActor = AI
						Slot = RCE_IntFromStr(Left$(M\MessageData$, 1))
						Amount = RCE_IntFromStr(Mid$(M\MessageData$, 2, 2))
						; Slot is a backpack-relative offset (0..31). Reject anything that
						; would push the array index past the inventory bounds; without this,
						; a crafted Slot reads (and the ItemInstanceToString$ helper writes)
						; from adjacent ActorInstance fields.
						If Slot >= 0 And Slot <= 31 And Slot + SlotI_Backpack <= Slots_Inventory
							; Record the server-authoritative offer state.
							; The accept-side swap reads from this rather than
							; from the client-controlled accept-packet bytes;
							; that closes the dupe where the client's accept
							; payload claims a different stack than what the
							; trade UI showed via P_UpdateTrading.
							If Amount > AI\Inventory\Amounts[Slot + SlotI_Backpack]
								Amount = AI\Inventory\Amounts[Slot + SlotI_Backpack]
							EndIf
							If Amount < 0 Then Amount = 0
							AI\TradeOfferedAmount[Slot] = Amount
							Pa$ = M\MessageData$
							If Amount > 0 Then Pa$ = Pa$ + ItemInstanceToString$(AI\Inventory\Items[Slot + SlotI_Backpack])
							RCE_Send(Host, AI\TradingActor\RNID, P_UpdateTrading, Pa$, True)
						EndIf
					EndIf
				EndIf
			; Trading complete
			Case P_OpenTrading
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null
					; Accept trading
					If Len(M\MessageData$) > 0
						; Player -> NPC or player -> scenery trading {##}
						If AI\IsTrading = 1
							AI\IsTrading = 0
							Change = 0
							If AI\TradeResult$ <> ""
								ID = RCE_IntFromStr(Left$(AI\TradeResult$, 2))
								AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
								;Owned.OwnedScenery = AInstance\OwnedScenery[ID]
							EndIf

							;Sold items
							Offset = 193
							For i = 0 To 31
								SlotID = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))
								Amount = RCE_IntFromStr(Mid$(M\MessageData$, Offset + 1, 2))
								Offset = Offset + 3
								; Upper-bound SlotID against the inventory size — original check
								; only rejected SlotID <= 0, so a crafted shop-sell packet with
								; SlotID in the byte range past Slots_Inventory wrote into
								; whatever ActorInstance fields follow the inventory array.
								If SlotID > 0 And SlotID <= Slots_Inventory
									If Amount > AI\Inventory\Amounts[SlotID] Then Amount = AI\Inventory\Amounts[SlotID]
									If AI\Inventory\Items[SlotID] <> Null And Amount > 0
										; Alter cost {##}
										;If Owned = Null
											Change = Change + (AI\Inventory\Items[SlotID]\Item\Value * Amount)
										; Add item to scenery inventory if applicable
										; Else
										; 	For j = 0 To Owned\InventorySize - 1
										; 		If Owned\Inventory\Items[j] = Null
										; 			Owned\Inventory\Items[j] = CopyItemInstance(AI\Inventory\Items[SlotID])
										; 			Owned\Inventory\Amounts[j] = Amount
										; 			Exit
										; 		ElseIf ItemInstancesIdentical(AI\Inventory\Items[SlotID], Owned\Inventory\Items[j])
										; 			Owned\Inventory\Amounts[j] = Owned\Inventory\Amounts[j] + Amount
										; 			Exit
										; 		EndIf
										; 	Next
										; EndIf
										; Remove item
										AI\Inventory\Amounts[SlotID] = AI\Inventory\Amounts[SlotID] - Amount
										If AI\Inventory\Amounts[SlotID] <= 0 Then FreeItemInstance(AI\Inventory\Items[SlotID])
										; Tell player if required
										If AI\RNID > 0
											Pa$ = RCE_StrFromInt$(SlotID, 1) + RCE_StrFromInt$(Amount, 2)
											RCE_Send(Host, AI\RNID, P_InventoryUpdate, "T" + Pa$, True)
										EndIf
									EndIf
								EndIf
							Next
						;	; Bought items
							Offset = 1
							For i = 0 To 31
								II.ItemInstance = Object.ItemInstance(RCE_IntFromStr(Mid$(M\MessageData$, Offset, 4)))
								Amount = RCE_IntFromStr(Mid$(M\MessageData$, Offset + 4, 2))
								Offset = Offset + 6
								If II <> Null And Amount > 0
									If II\Assignment > 0 And II\AssignTo = AI
										If Amount < II\Assignment Then II\Assignment = Amount
										; Alter cost {##}
										; If Owned = Null
											OldChange = Change
											Change = Change - (II\Item\Value * II\Assignment)
										;Remove from scenery inventory if applicable
										; Else
										; 	RemoveAmount = II\Assignment
										; 	For j = 0 To Owned\InventorySize - 1
										; 		If Owned\Inventory\Items[j] <> Null
										; 			If ItemInstancesIdentical(II, Owned\Inventory\Items[j])
										; 				If Owned\Inventory\Amounts[j] >= RemoveAmount
										; 					Owned\Inventory\Amounts[j] = Owned\Inventory\Amounts[j] - RemoveAmount
										; 					RemoveAmount = 0
										; 					If Owned\Inventory\Amounts[j] = 0 Then FreeItemInstance(Owned\Inventory\Items[j])
										; 				Else
										; 					RemoveAmount = RemoveAmount - Owned\Inventory\Amounts[j]
										; 					Owned\Inventory\Amounts[j] = 0
										; 					FreeItemInstance(Owned\Inventory\Items[j])
										; 				EndIf
										; 				If RemoveAmount = 0 Then Exit
										; 			EndIf
										; 		EndIf
										;	Next
										;EndIf
										; Prevent cheating
										If AI\Gold + Change >= 0
											;Ask client to specify a slot to put it in
											Pa$ = RCE_StrFromInt$(II\Item\ID, 2) + RCE_StrFromInt$(II\Assignment, 2)
											RCE_Send(Host, AI\RNID, P_InventoryUpdate, "G" + RCE_StrFromInt$(Handle(II), 4) + Pa$, True)
										Else
											Change = OldChange
											FreeItemInstance(II)
											Exit
										EndIf
									EndIf
								EndIf
							Next

							; Adjust gold level
							;If Owned = Null
								If Change <> 0
									AI\Gold = AI\Gold + Change
									If Change > 0
										Pa$ = "U" + RCE_StrFromInt$(Change, 4)
									Else
										Pa$ = "D" + RCE_StrFromInt$(Abs(Change), 4)
									EndIf
									RCE_Send(Host, AI\RNID, P_GoldChange, Pa$, True)
								EndIf
							;EndIf
						; Player -> player trading
						ElseIf AI\IsTrading = 4
							AI\IsTrading = 5
							AI\TradeResult$ = M\MessageData$
							; Character to trade with has been deleted
							If AI\TradingActor = Null
								AI\TradeResult$ = ""
								AI\IsTrading = 0
								RCE_Send(Host, AI\RNID, P_CloseTrading, "", True)
							; Or left the game
							ElseIf AI\TradingActor\RNID < 1
								AI\TradeResult$ = ""
								AI\IsTrading = 0
								AI\TradingActor = Null
								RCE_Send(Host, AI\RNID, P_CloseTrading, "", True)
							; Both players are here and have accepted
							ElseIf AI\TradingActor\IsTrading = 5
								A2.ActorInstance = AI\TradingActor

								; Server-authoritative cost check: both clients must report the same gold
								; flow magnitude in their accept packet. The historical per-item validation
								; block (still commented below) produced false negatives in practice; the
								; per-slot swap loop further down already clamps Amount to the actual
								; source-side inventory so item dupe via Amount inflation is blocked there.
								; This cost check closes the gold-side scam where one party lied about the
								; agreed amount.
								Valid = True
								A1Cost = RCE_IntFromStr(Left$(AI\TradeResult$, 4)) * -1
								A2Cost = RCE_IntFromStr(Left$(A2\TradeResult$, 4))
								If A1Cost <> A2Cost Then Valid = False
								If A2Cost > 0 And A2Cost > A2\Gold Then Valid = False
								If A2Cost < 0 And ( - A2Cost ) > AI\Gold Then Valid = False

								; Historical per-item validation (kept commented for future repair —
								; produced false negatives because the slot/amount packet offsets it
								; assumed don't match what the live client sends).
								; For i = 0 To 31
								; 	A1SoldAmount = RCE_IntFromStr(Mid$(AI\TradeResult$, 101 + (i * 2), 2))
								; 	If A1SoldAmount > 0
								; 		For j = 0 To 31
								; 			If RCE_IntFromStr(Mid$(A2\TradeResult$, 5 + (j * 3), 1)) = i
								; 				Amount = RCE_IntFromStr(Mid$(A2\TradeResult$, 6 + (j * 3), 2))
								; 				If Amount <> A1SoldAmount Then Valid = False
								; 				Exit
								; 			EndIf
								; 		Next
								; 	EndIf
								; 	A2SoldAmount = RCE_IntFromStr(Mid$(A2\TradeResult$, 101 + (i * 2), 2))
								; 	If A2SoldAmount > 0
								; 		For j = 0 To 31
								; 			If RCE_IntFromStr(Mid$(AI\TradeResult$, 5 + (j * 3), 1)) = i
								; 				Amount = RCE_IntFromStr(Mid$(AI\TradeResult$, 6 + (j * 3), 2))
								; 				If Amount <> A2SoldAmount Then Valid = False
								; 				Exit
								; 			EndIf
								; 		Next
								; 	EndIf
								; Next

								If Valid = True
									; Swap money
									AI\Gold = AI\Gold + A2Cost
									A2\Gold = A2\Gold - A2Cost
									If A2Cost > 0
										RCE_Send(Host, AI\RNID, P_GoldChange, "U" + RCE_StrFromInt$(A2Cost, 4), True)
										RCE_Send(Host, A2\RNID, P_GoldChange, "D" + RCE_StrFromInt$(A2Cost, 4), True)
									Else
										RCE_Send(Host, AI\RNID, P_GoldChange, "D" + RCE_StrFromInt$(A2Cost, 4), True)
										RCE_Send(Host, A2\RNID, P_GoldChange, "U" + RCE_StrFromInt$(A2Cost, 4), True)
									EndIf

									; Swap items. Read amounts from the server-tracked
									; TradeOfferedAmount[i] (populated by each
									; P_UpdateTrading) rather than the client's
									; accept-packet TradeResult$ bytes; otherwise
									; either party could craft an accept packet
									; that promises to give a different stack
									; (smaller, less valuable, or a stack they
									; don't have at all) than the trade UI
									; displayed during negotiation.
									For i = 0 To 31
										SlotID = i + SlotI_Backpack
										; Actor 1
										Amount = AI\TradeOfferedAmount[i]
										If Amount > AI\Inventory\Amounts[SlotID] Then Amount = AI\Inventory\Amounts[SlotID]
										If AI\Inventory\Items[SlotID] <> Null And Amount > 0
											GiveItem.ItemInstance = CopyItemInstance(AI\Inventory\Items[SlotID])
											; Remove item
											AI\Inventory\Amounts[SlotID] = AI\Inventory\Amounts[SlotID] - Amount
											If AI\Inventory\Amounts[SlotID] <= 0 Then FreeItemInstance(AI\Inventory\Items[SlotID])
											RCE_Send(Host, AI\RNID, P_InventoryUpdate, "T" + RCE_StrFromInt$(SlotID, 1) + RCE_StrFromInt$(Amount, 2), True)
											; Give to new player
											GiveItem\Assignment = Amount
											GiveItem\AssignTo = A2
											Pa$ = RCE_StrFromInt$(GiveItem\Item\ID, 2) + RCE_StrFromInt$(GiveItem\Assignment, 2)
											RCE_Send(Host, A2\RNID, P_InventoryUpdate, "G" + RCE_StrFromInt$(Handle(GiveItem), 4) + Pa$, True)
										EndIf
										; Actor 2
										Amount = A2\TradeOfferedAmount[i]
										If Amount > A2\Inventory\Amounts[SlotID] Then Amount = A2\Inventory\Amounts[SlotID]
										If A2\Inventory\Items[SlotID] <> Null And Amount > 0
											GiveItem.ItemInstance = CopyItemInstance(A2\Inventory\Items[SlotID])
											; Remove item
											A2\Inventory\Amounts[SlotID] = A2\Inventory\Amounts[SlotID] - Amount
											If A2\Inventory\Amounts[SlotID] <= 0 Then FreeItemInstance(A2\Inventory\Items[SlotID])
											RCE_Send(Host, A2\RNID, P_InventoryUpdate, "T" + RCE_StrFromInt$(SlotID, 1) + RCE_StrFromInt$(Amount, 2), True)
											; Give to new player
											GiveItem\Assignment = Amount
											GiveItem\AssignTo = AI
											Pa$ = RCE_StrFromInt$(GiveItem\Item\ID, 2) + RCE_StrFromInt$(GiveItem\Assignment, 2)
											RCE_Send(Host, AI\RNID, P_InventoryUpdate, "G" + RCE_StrFromInt$(Handle(GiveItem), 4) + Pa$, True)
										EndIf
									Next
								 EndIf

								; End trading mode for both players. Clear the
								; per-slot offer state so it can't carry stale
								; amounts into the next trade either side
								; opens.
								For ResetI = 0 To 31
									AI\TradeOfferedAmount[ResetI] = 0
									A2\TradeOfferedAmount[ResetI] = 0
								Next
								AI\TradeResult$ = ""
								AI\IsTrading = 0
								AI\TradingActor = Null
								RCE_Send(Host, AI\RNID, P_CloseTrading, "", True)
								A2\TradeResult$ = ""
								A2\IsTrading = 0
								A2\TradingActor = Null
								RCE_Send(Host, A2\RNID, P_CloseTrading, "", True)
							EndIf
						EndIf
					; Cancel trading
					Else
						If AI\IsTrading = 5 And AI\TradingActor <> Null
							If AI\TradingActor\RNID > 0 Then RCE_Send(Host, AI\TradingActor\RNID, P_CloseTrading, "", True)
						EndIf
						AI\IsTrading = 0
					EndIf
				EndIf

			; Jump
			Case P_Jump
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null
					If AI\Mount = Null
						; Tell other players in the same area
						Pa$ = RCE_StrFromInt$(AI\RuntimeID, 2)
						AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
						If AInstance <> Null
							A2.ActorInstance = AInstance\FirstInZone
							While A2 <> Null
								If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_Jump, Pa$, True)
								A2 = A2\NextInZone
							Wend
						EndIf
					EndIf
				EndIf

			; Action bar update
			Case P_ActionBarUpdate
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null
					A.Account = Object.Account(AI\Account)
					Num = RCE_IntFromStr(Mid$(M\MessageData$, 2, 1))
					If Num >= 0 And Num < 36 And A <> Null
						If A\LoggedOn > -1
							If Left$(M\MessageData$, 1) = "S"
								; Cap the spell name to keep the stored slot
								; string within the 256-byte bound that the
								; save loader (ReadBoundedString$ in
								; AccountsServer.bb) imposes on Slots$. Without
								; this, a client can stuff arbitrary bytes
								; (up to the packet limit) into one of 36
								; slots, bloating the per-character save and,
								; more importantly, the broadcast packet that
								; re-encodes the action bar with a 2-byte
								; length prefix per slot. 255 leaves room for
								; the "S" prefix byte.
								Local SpellName$ = Mid$(M\MessageData$, 3)
								If Len(SpellName$) > 255 Then SpellName$ = Left$(SpellName$, 255)
								A\ActionBar[A\LoggedOn]\Slots$[Num] = "S" + SpellName$
							ElseIf Left$(M\MessageData$, 1) = "I"
								A\ActionBar[A\LoggedOn]\Slots$[Num] = "I" + Mid$(M\MessageData$, 3, 2)
							ElseIf Left$(M\MessageData$, 1) = "N"
								A\ActionBar[A\LoggedOn]\Slots$[Num] = ""
							EndIf
						EndIf
					EndIf
				EndIf

			; Spell update
			Case P_SpellUpdate
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null
					Select Left$(M\MessageData$, 1)
						; Player has unmemorised a spell
						Case "U"
							If RequireMemorise
								Num = RCE_IntFromStr(Mid$(M\MessageData$, 2, 2))
								For i = 0 To 9
									If AI\MemorisedSpells[i] = Num Then AI\MemorisedSpells[i] = 5000 : Exit
								Next
							EndIf
						; Player is memorising a spell
						Case "M"
							If RequireMemorise
								MS.MemorisingSpell = New MemorisingSpell
								MS\AI = AI
								MS\KnownNum = RCE_IntFromStr(Mid$(M\MessageData$, 2, 2))
								MS\CreatedTime = MilliSecs()
								If MS\KnownNum < 0 Or MS\KnownNum > 999 Then Delete MS
							EndIf
						; Player is firing a spell
						Case "F"
							; Spell ID and target
							Num = RCE_IntFromStr(Mid$(M\MessageData$, 2, 2))
							Context.ActorInstance = Null
							; Bug fix: previously `Len > 3` admitted 4-byte
							; packets that then read `Mid$(...,4,2)` past
							; end of string -- Blitz pads short Mid$ with
							; empty bytes and RCE_IntFromStr returns a
							; truncated/zero RuntimeID, aliasing Context
							; to actor 0. Require both target-RuntimeID
							; bytes to be present.
							If Len(M\MessageData$) >= 5
								RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 4, 2))
								Context = RuntimeIDList(RuntimeID)
								; Reject targets that are dead or in a different
								; area. Without this, the spell script fires
								; against an actor whose FreeActorInstance is
								; queued in PendingKill (use-after-free), and a
								; cross-area target can be hit despite the
								; client's view being area-local.
								If Context <> Null
									If Context\Attributes\Value[HealthStat] <= 0 Then Context = Null
								EndIf
								If Context <> Null
									If Context\ServerArea <> AI\ServerArea Then Context = Null
								EndIf
							EndIf
							; Convert ID into known spell number
							Found = False
							For i = 0 To 999
								If AI\KnownSpells[i] = Num
									Num = i
									Found = True
									Exit
								EndIf
							Next
							If Found = True
								; Both cooldown paths now index AI\SpellCharge by
								; the underlying spell ID (KnownSpells[Num])
								; rather than two different slot-index spaces.
								; Previously, RequireMemorise=True stored at the
								; memorise-slot index 0..9 while
								; RequireMemorise=False stored at the known-
								; spell-index 0..999 -- same physical spell had
								; two independent cooldowns, and toggling the
								; global RequireMemorise or re-memorising into a
								; different slot let the player double-cast.
								; Also enforce a per-actor 100ms floor so
								; zero-RechargeTime spells can't be spammed
								; every UpdateNetwork tick.
								Local SpellID = AI\KnownSpells[Num]
								If SpellID >= 0 And SpellID <= 999
									Local NowMs = MilliSecs()
									Local Memorised = False
									If RequireMemorise
										For i = 0 To 9
											If AI\MemorisedSpells[i] = Num Then Memorised = True : Exit
										Next
									Else
										Memorised = True
									EndIf
									If Memorised
										If NowMs - AI\LastSpellFireMs < 100
											; Too fast; drop silently to avoid
											; chat-spamming the user.
										ElseIf AI\SpellCharge[SpellID] > 0
											RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(253) + LanguageString$(LS_AbilityNotRecharged), True)
										Else
											Sp.Spell = SpellsList(SpellID)
											; SpellsList is sparse; a stale character save
											; (admin deleted the spell between sessions)
											; or a corrupted KnownSpells slot would otherwise
											; deref Null below. P_FetchCharacter already
											; prunes stale entries at character-select but
											; defense-in-depth at the cast site means a
											; race or future code path that bypasses the
											; prune can't take down the server.
											If Sp = Null
												WriteLog(MainLog, "P_SpellUpdate F: SpellID " + SpellID + " in KnownSpells but not in SpellsList (stale slot); dropping cast")
												AI\KnownSpells[Num] = 0
												AI\SpellLevels[Num] = 0
												Goto SkipSpellCast
											EndIf
											; Race / class restriction check.
											; The Spell type has ExclusiveRace$/ExclusiveClass$
											; (persisted by SaveSpells / loaded by
											; LoadSpells, editor-exposed in GUE), but
											; the cast path never enforced them -- so
											; a paladin-only Smite taught to a thief
											; via script or recovered from a stale save
											; would fire normally. The check mirrors
											; the item-eat gate in P_EatItem.
											Local SpellAllowed = True
											If Len(Sp\ExclusiveRace$) > 0
												If Upper$(AI\Actor\Race$) <> Upper$(Sp\ExclusiveRace$) Then SpellAllowed = False
											EndIf
											If SpellAllowed And Len(Sp\ExclusiveClass$) > 0
												If Upper$(AI\Actor\Class$) <> Upper$(Sp\ExclusiveClass$) Then SpellAllowed = False
											EndIf
											If SpellAllowed = False
												; Race / class restriction failed -- emit a
												; reason-specific message rather than the
												; misleading LS_AbilityNotRecharged the pre-fix
												; code used at this same site. Reuses the
												; existing LS_RaceOnly / LS_ClassOnly tooltip
												; strings (already used by item tooltips at
												; Interface3D.bb:1938+) so no new LS index is
												; needed. The earlier `SpellAllowed` evaluation
												; sets the flag False on the FIRST failed check
												; (race first, then class), so the branch order
												; here mirrors that priority.
												Local ReasonMsg$
												If Len(Sp\ExclusiveRace$) > 0 And Upper$(AI\Actor\Race$) <> Upper$(Sp\ExclusiveRace$)
													ReasonMsg$ = Sp\ExclusiveRace$ + " " + LanguageString$(LS_RaceOnly)
												ElseIf Len(Sp\ExclusiveClass$) > 0 And Upper$(AI\Actor\Class$) <> Upper$(Sp\ExclusiveClass$)
													ReasonMsg$ = Sp\ExclusiveClass$ + " " + LanguageString$(LS_ClassOnly)
												Else
													; Defensive fallback -- shouldn't reach here
													; since SpellAllowed=False implies one of the
													; above mismatched.
													ReasonMsg$ = LanguageString$(LS_AbilityNotRecharged)
												EndIf
												RCE_Send(Host, AI\RNID, P_ChatMessage, Chr$(253) + ReasonMsg$, True)
											Else
												ThreadScript(Sp\Script$, Sp\SMethod$, Handle(AI), Handle(Context), AI\SpellLevels[Num])
												AI\SpellCharge[SpellID] = Sp\RechargeTime
												AI\LastSpellFireMs = NowMs
											EndIf
										EndIf
									EndIf
								EndIf
							EndIf
							.SkipSpellCast
					End Select
				EndIf

			; Progress bar message
			Case P_ProgressBar
				If Left$(M\MessageData$, 1) = "C"
					S.ScriptInstance = Object.ScriptInstance(RCE_IntFromStr(Mid$(M\MessageData$, 2, 4)))
					If S <> Null And ScriptHandleBelongsTo(S, M\FromID)
						S\WaitResult$ = Str$(RCE_IntFromStr(Mid$(M\MessageData$, 6)))
					ElseIf S = Null
						RCE_Send(Host, M\FromID, P_ProgressBar, "D" + Mid$(M\MessageData$, 6), True)
					EndIf
				EndIf

			; Dialog message
			Case P_Dialog
				Select Left$(M\MessageData$, 1)
					; New dialog created
					Case "N"
						S.ScriptInstance = Object.ScriptInstance(RCE_IntFromStr(Mid$(M\MessageData$, 2, 4)))
						If S <> Null And ScriptHandleBelongsTo(S, M\FromID)
							S\WaitResult$ = Str$(RCE_IntFromStr(Mid$(M\MessageData$, 6)))
						ElseIf S = Null
							RCE_Send(Host, M\FromID, P_Dialog, "C" + Mid$(M\MessageData$, 6), True)
						EndIf
					; Dialog text received
					Case "T"
						S.ScriptInstance = Object.ScriptInstance(RCE_IntFromStr(Mid$(M\MessageData$, 2, 4)))
						If S <> Null And ScriptHandleBelongsTo(S, M\FromID) Then S\WaitResult$ = "0"
					; Dialog option picked
					Case "O"
						S.ScriptInstance = Object.ScriptInstance(RCE_IntFromStr(Mid$(M\MessageData$, 2, 4)))
						If S <> Null And ScriptHandleBelongsTo(S, M\FromID) Then S\WaitResult$ = RCE_IntFromStr(Mid$(M\MessageData$, 6, 1))
				End Select

			; Text input reply
			Case P_ScriptInput
				S.ScriptInstance = Object.ScriptInstance(RCE_IntFromStr(Mid$(M\MessageData$, 1, 4)))
				If S <> Null And ScriptHandleBelongsTo(S, M\FromID) Then S\WaitResult$ = Mid$(M\MessageData$, 5)

			; A player ate an item
			Case P_EatItem
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null
					Slot = RCE_IntFromStr(Mid$(M\MessageData$, 1, 1))
					Amount = RCE_IntFromStr(Mid$(M\MessageData$, 2, 2))
					; Tighten upper bound to the actual inventory size. Previous limit was
					; an unrelated literal (50) larger than Slots_Inventory (45), so the
					; intervening slots 46..49 read past the Items array.
					If Slot >= 0 And Slot <= Slots_Inventory And Amount > 0
						If AI\Inventory\Items[Slot] <> Null And AI\Inventory\Amounts[Slot] >= Amount
							If AI\Inventory\Items[Slot]\Item\ItemType = I_Potion Or AI\Inventory\Items[Slot]\Item\ItemType = I_Ingredient
								If Upper$(AI\Actor\Class$) = Upper$(AI\Inventory\Items[Slot]\Item\ExclusiveClass$) Or Len(AI\Inventory\Items[Slot]\Item\ExclusiveClass$) = 0
									If Upper$(AI\Actor\Race$) = Upper$(AI\Inventory\Items[Slot]\Item\ExclusiveRace$) Or Len(AI\Inventory\Items[Slot]\Item\ExclusiveRace$) = 0
										; Create buff
										EffectName$ = AI\Inventory\Items[Slot]\Item\Name$
										Found = False
										For AE.ActorEffect = Each ActorEffect
											If AE\Owner = AI
												If Upper$(AE\Name$) = Upper$(EffectName$)
													FoundAE.ActorEffect = AE
													Found = True
													Exit
												EndIf
											EndIf
										Next
										If Found = False
											FoundAE = New ActorEffect
											FoundAE\Attributes = New Attributes
											FoundAE\Name$ = EffectName$
											FoundAE\Owner = AI
											Pa$ = RCE_StrFromInt$(Handle(FoundAE), 4) + RCE_StrFromInt$(AI\Inventory\Items[Slot]\Item\ThumbnailTexID, 2) + FoundAE\Name$
											RCE_Send(Host, AI\RNID, P_ActorEffect, "A" + Pa$, True)
										EndIf
										FoundAE\CreatedTime = MilliSecs()
										FoundAE\Length = AI\Inventory\Items[Slot]\Item\EatEffectsLength * 1000
										For i = 0 To 39
											If AI\Inventory\Items[Slot]\Attributes\Value[i] <> 0
												Old = FoundAE\Attributes\Value[i]
												FoundAE\Attributes\Value[i] = AI\Inventory\Items[Slot]\Attributes\Value[i]
												Pa$ = RCE_StrFromInt$(i, 1) + RCE_StrFromInt$(FoundAE\Attributes\Value[i] - Old, 4)
												FoundAE\Owner\Attributes\Value[i] = FoundAE\Owner\Attributes\Value[i] + (FoundAE\Attributes\Value[i] - Old)
												RCE_Send(Host, FoundAE\Owner\RNID, P_ActorEffect, "E" + Pa$, True)
											EndIf
										Next

										; Execute Item Script
										If AI\Inventory\Items[Slot]\Item\Script$ <> ""
											If AI\Inventory\Amounts[Slot] > 0
												If AI\Inventory\Items[Slot]\Item\SMethod$ = ""
													ThreadScript(AI\Inventory\Items[Slot]\Item\Script$, "Main", Handle(AI), Handle(Null))
												Else
													ThreadScript(AI\Inventory\Items[Slot]\Item\Script$, AI\Inventory\Items[Slot]\Item\SMethod$, Handle(AI), Handle(Null))
												EndIf
											EndIf
										EndIf
										
										; Remove item
										AI\Inventory\Amounts[Slot] = AI\Inventory\Amounts[Slot] - Amount
										If AI\Inventory\Amounts[Slot] <= 0 Then FreeItemInstance(AI\Inventory\Items[Slot])
									EndIf
								EndIf
							EndIf
						EndIf
					EndIf
				EndIf

			; A player used an item
			Case P_ItemScript
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null And (Len(M\MessageData$) = 1 Or Len(M\MessageData$) = 3)
					SlotIndex = RCE_IntFromStr(Left$(M\MessageData$, 1))
					Local ItemScriptGateOk% = True
					If Len(M\MessageData$) = 3
						A2.ActorInstance = RuntimeIDList(RCE_IntFromStr(Mid$(M\MessageData$, 2)))
						; Same-area + InteractDist gate when the item
						; targets another actor. Mirrors P_RightClick
						; (range) and P_AttackActor (area). Items used
						; on self (Len = 1, A2 = Null) skip this. On
						; cross-area or out-of-range A2, the whole
						; handler is dropped silently (matches
						; P_Examine / P_Trade reject semantics rather
						; than degrading to self-cast, which could
						; misfire an offensive item back on the user).
						If A2 <> Null
							AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
							TInstance.AreaInstance = Object.AreaInstance(A2\ServerArea)
							If AInstance = Null Or AInstance <> TInstance
								ItemScriptGateOk = False
							Else
								XDist# = Abs(AI\X# - A2\X#)
								ZDist# = Abs(AI\Z# - A2\Z#)
								Dist# = (XDist# * XDist#) + (ZDist# * ZDist#)
								If Dist# >= InteractDist Then ItemScriptGateOk = False
							EndIf
						EndIf
					Else
						A2 = Null
					EndIf
					; Tighten upper bound to the actual inventory size.
					; Previous limit was the unrelated literal 50 -- larger
					; than Slots_Inventory (45), so a crafted P_ItemScript
					; with SlotIndex 46..49 read past the Items array
					; (same shape as the P_EatItem fix at ~1172).
					If ItemScriptGateOk = True And SlotIndex >= 0 And SlotIndex <= Slots_Inventory
						If AI\Inventory\Items[SlotIndex] <> Null
							; Bug fix: the ExclusiveClass gate originally
							; compared Actor\Race$ against ExclusiveClass$
							; (copy-paste typo from the line below). Compare
							; Class$ against ExclusiveClass$, mirroring
							; P_EatItem at line ~1107.
							If AI\Inventory\Items[SlotIndex]\Item\ExclusiveClass$ = "" Or Upper$(AI\Actor\Class$) = Upper$(AI\Inventory\Items[SlotIndex]\Item\ExclusiveClass$)
								If AI\Inventory\Items[SlotIndex]\Item\ExclusiveRace$ = "" Or Upper$(AI\Actor\Race$) = Upper$(AI\Inventory\Items[SlotIndex]\Item\ExclusiveRace$)
									If AI\Inventory\Items[SlotIndex]\Item\Script$ <> ""
										If AI\Inventory\Amounts[SlotIndex] > 0
											If AI\Inventory\Items[SlotIndex]\Item\SMethod$ = ""
												ThreadScript(AI\Inventory\Items[SlotIndex]\Item\Script$, "Main", Handle(AI), Handle(A2))
											Else
												ThreadScript(AI\Inventory\Items[SlotIndex]\Item\Script$, AI\Inventory\Items[SlotIndex]\Item\SMethod$, Handle(AI), Handle(A2))
											EndIf
										EndIf
									EndIf
								EndIf
							EndIf
						EndIf
					EndIf
				EndIf

			; A player right clicked on an actor
			Case P_RightClick
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null And Len(M\MessageData$) = 2
					A2.ActorInstance = RuntimeIDList(RCE_IntFromStr(M\MessageData$))
					If A2 <> Null
						XDist# = Abs(AI\X# - A2\X#)
						ZDist# = Abs(AI\Z# - A2\Z#)
						Dist# = (XDist# * XDist#) + (ZDist# * ZDist#)
						If Dist# < InteractDist
							; Start right click script
							If A2\Script$ <> ""
								Running = False
								;Script.Script = FindScript(A2\Script$)
								For Si.ScriptInstance = Each ScriptInstance
									If Si\Name = A2\Script
										If Si\AI = Handle(AI) And Si\AIContext = Handle(A2) Then Running = True : Exit
									EndIf
								Next
								If Running = False
									ThreadScript(A2\Script$, "Main", Handle(AI), Handle(A2))
								EndIf
							; No script to run, if actor can be ridden, mount it
							ElseIf A2\Actor\Rideable = True
								If (A2\Leader = Null Or A2\Leader = AI) And AI\Mount = Null
									AI\Mount = A2
									A2\Rider = AI
									; Bug fix: AI_None was never declared in
									; Actors.bb (only AI_Wait..AI_PetWait
									; exist). Blitz silently coerced it to 0
									; (= AI_Wait), so the AILookForTargets
									; sweep still ran every 10 ticks against
									; the mount's own rider. Clear the AI
									; target and use the existing AI_Wait
									; sentinel.
									A2\AIMode = AI_Wait
									A2\AITarget = Null
									ThreadScript("Mount", "Mount", Handle(AI), Handle(A2))
								EndIf
							EndIf
							; Continue any paused scripts waiting for this conversation.
							; After-cursor walk: the body Deletes PS, which would
							; corrupt a For-Each cursor when two WaitSpeak scripts
							; resume on the same right-click.
							Local PS.PausedScript = First PausedScript
							Local PSNext.PausedScript = Null
							While PS <> Null
								PSNext = After PS
								; Defense in depth: drop Null-S PausedScripts.
								; See Scripting.bb / GameServer.bb sibling guards.
								If PS\S = Null
									Delete PS
								ElseIf PS\Reason = 4
									If PS\ReasonActor = AI And PS\ReasonContextActor = A2
										PS\S\WaitResult$ = "1"
										Delete PS
									EndIf
								EndIf
								PS = PSNext
							Wend
						EndIf
					EndIf
				EndIf
			
			Case P_Examine
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null And Len(M\MessageData$) = 2
					A2.ActorInstance = RuntimeIDList(RCE_IntFromStr(M\MessageData$))
					If A2 <> Null
						; Same-area + InteractDist gate. Mirrors
						; P_AttackActor (area) and P_RightClick (range).
						; Without these, a wire-injecting client could
						; trigger Examine scripts (default disclosure
						; dialog, vendor inventory, quest state) on
						; any actor anywhere on the map, defeating the
						; implicit "you can only examine what you can
						; see" UX contract and giving the BVM
						; clicker-handle trap (SI\AI = Handle(clicker))
						; a cross-area attack surface.
						AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
						TInstance.AreaInstance = Object.AreaInstance(A2\ServerArea)
						If AInstance <> Null And AInstance = TInstance
							XDist# = Abs(AI\X# - A2\X#)
							ZDist# = Abs(AI\Z# - A2\Z#)
							Dist# = (XDist# * XDist#) + (ZDist# * ZDist#)
							If Dist# < InteractDist
								If A2\Script$ <> ""
									Running = False

									For Si.ScriptInstance = Each ScriptInstance
										If Si\Name = A2\Script
											If Si\AI = Handle(AI) And Si\AIContext = Handle(A2) Then Running = True : Exit
										EndIf
									Next
									If Running = False
										ThreadScript(A2\Script$, "Examine", Handle(AI), Handle(A2))
									EndIf
								Else
									ThreadScript("Default", "Examine", Handle(AI), Handle(A2))
								EndIf
							EndIf
						EndIf
					EndIf
				EndIf

				Case P_Trade
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null And Len(M\MessageData$) = 2
					A2.ActorInstance = RuntimeIDList(RCE_IntFromStr(M\MessageData$))
					If A2 <> Null
						; Same-area + InteractDist gate. Without it, a
						; wire-injecting client could pin any remote
						; actor's IsTrading state via the Default Trade
						; script (BVM_OPENTRADING), or trigger the script
						; against a target in another area entirely.
						AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
						TInstance.AreaInstance = Object.AreaInstance(A2\ServerArea)
						If AInstance <> Null And AInstance = TInstance
							XDist# = Abs(AI\X# - A2\X#)
							ZDist# = Abs(AI\Z# - A2\Z#)
							Dist# = (XDist# * XDist#) + (ZDist# * ZDist#)
							If Dist# < InteractDist
								ThreadScript("Default", "Trade", Handle(AI), Handle(A2))
							EndIf
						EndIf
					EndIf
				EndIf

			; A player has attacked something
			Case P_AttackActor
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null And Len(M\MessageData$) = 2
					; Check combat delay and whether actor is riding a mount, to prevent cheating
					If MilliSecs() - AI\LastAttack >= CombatDelay And AI\Mount = Null
						A2.ActorInstance = RuntimeIDList(RCE_IntFromStr(M\MessageData$))
						If A2 <> Null
							; Require attacker and target to be in the same
							; AreaInstance. Without this, a target mid-portal
							; (target's ServerArea hasn't ack'd P_ChangeArea
							; yet) could be hit from the attacker's old area,
							; and friendly-fire / PvP rules from the wrong
							; area apply.
							AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
							TInstance.AreaInstance = Object.AreaInstance(A2\ServerArea)
							If AInstance <> Null And AInstance = TInstance
								If A2\RNID < 0 Or AInstance\Area\PvP = True
									ActorAttack(AI, A2)
									AI\AITarget = A2
								EndIf
							EndIf
						EndIf
					EndIf
				EndIf

			; Inventory update
			Case P_InventoryUpdate
				Select Left$(M\MessageData$, 1)
					; Request to pick up a dropped item
					Case "P"
						D.DroppedItem = Object.DroppedItem(RCE_IntFromStr(Mid$(M\MessageData$, 2, 4)))
						If D <> Null
							AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
							; Require pickup to come from an actor in the same
							; AreaInstance as the dropped item AND within a
							; sane distance. Without these, anyone holding (or
							; guessing -- handles are sequential) the 4-byte
							; DroppedItem handle could pick it up from another
							; area or across the map. Distance limit allows a
							; little slack over the right-click InteractDist.
							PickupAllowed = False
							If AI <> Null
								If D\ServerHandle = AI\ServerArea
									Local PdX# = AI\X# - D\X#
									Local PdZ# = AI\Z# - D\Z#
									Local PickupDist# = Sqr#(PdX# * PdX# + PdZ# * PdZ#)
									If PickupDist# <= InteractDist + 50.0 Then PickupAllowed = True
								EndIf
							EndIf
							If AI <> Null And PickupAllowed
								SlotI = RCE_IntFromStr(Mid$(M\MessageData$, 6, 1))
								; SlotI comes straight off the wire — must be bounded before
								; it's used as an index into the Items / Amounts arrays.
								If SlotI < 0 Or SlotI > Slots_Inventory Then SlotI = -1
								If SlotI >= 0 And (AI\Inventory\Items[SlotI] = Null Or (ItemInstancesIdentical(D\Item, AI\Inventory\Items[SlotI]) And D\Item\Item\Stackable = True And SlotI >= SlotI_Backpack))
									If SlotsMatch(D\Item\Item, SlotI) And ActorHasSlot(AI\Actor, SlotI, D\Item\Item)
										; Put into player's inventory
										If AI\Inventory\Items[SlotI] <> Null
											Delete AI\Inventory\Items[SlotI]
										Else
											AI\Inventory\Amounts[SlotI] = 0
										EndIf
										AI\Inventory\Items[SlotI] = D\Item
										AI\Inventory\Amounts[SlotI] = ClampStackAmount(AI\Inventory\Amounts[SlotI] + D\Amount)  ; cap to 16-bit save/wire ceiling
										If SlotI < SlotI_Backpack Then SendEquippedUpdate(AI)

										; Tell this player he got it
										RCE_Send(Host, AI\RNID, P_InventoryUpdate, "R" + RCE_StrFromInt$(Handle(D)) + RCE_StrFromInt$(SlotI, 1), True)

										; Tell other players it's gone
										Pa$ = "P" + RCE_StrFromInt$(Handle(D))
										AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
										If AInstance <> Null
											A2.ActorInstance = AInstance\FirstInZone
											While A2 <> Null
												If A2\RNID > 0
													If A2 <> AI Then RCE_Send(Host, A2\RNID, P_InventoryUpdate, Pa$, True)
												EndIf
												A2 = A2\NextInZone
											Wend
										EndIf

										; Delete it
										Delete D
									EndIf
								EndIf
							EndIf
						EndIf
					; Item dropped
					Case "D"
						AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
						If AI <> Null
							Slot = RCE_IntFromStr(Mid$(M\MessageData$, 2, 1))
							Amount = RCE_IntFromStr(Mid$(M\MessageData$, 3, 2))
							Result = InventoryDrop(AI, Slot, Amount, False)
							If Result <> 0
								SendEquippedUpdate(AI)
								; Create item on floor. Sanitise position floats
								; before they're persisted into a DroppedItem and
								; broadcast. AI\X#/Y#/Z# can carry a NaN/Inf from
								; an unvalidated upstream packet (P_StandardUpdate
								; only clamps Y; X and Z accept anything within
								; magnitude limits but not NaN). A NaN dropped-item
								; position poisons spatial code on every receiver.
								D.DroppedItem = New DroppedItem
								D\Item = Object.ItemInstance(Result)
								D\Amount = Amount
								D\X# = ClampWorldCoord#(AI\X#)
								D\Y# = ClampWorldCoord#(AI\Y#)
								D\Z# = ClampWorldCoord#(AI\Z#)
								D\ServerHandle = AI\ServerArea
								; Tell other players in the area
								Pa$ = "D" + RCE_StrFromInt$(Amount, 2) + RCE_StrFromFloat$(D\X#) + RCE_StrFromFloat$(D\Y#) + RCE_StrFromFloat$(D\Z#)
								Pa$ = Pa$ + RCE_StrFromInt$(Handle(D), 4) + ItemInstanceToString$(D\Item)
								AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
								If AInstance <> Null
									A2.ActorInstance = AInstance\FirstInZone
									While A2 <> Null
										If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_InventoryUpdate, Pa$, True)
										A2 = A2\NextInZone
									Wend
								EndIf
							EndIf
						EndIf
					; Reply to a given item message
					Case "G"
						AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
						II.ItemInstance = Object.ItemInstance(RCE_IntFromStr(Mid$(M\MessageData$, 3, 4)))
						; II\Assignment > 0 + II\AssignTo = AI together mean "this
						; item was assigned to this actor and not already claimed".
						; Without the AssignTo check, anyone holding (or guessing)
						; the 4-byte handle could claim items intended for another
						; player -- the P_OpenTrading buy path uses the same pair.
						If II <> Null And AI <> Null
							If II\Assignment > 0 And II\AssignTo = AI
								If Mid$(M\MessageData$, 2, 1) = "Y"
									SlotI = RCE_IntFromStr(Right$(M\MessageData$, 1))
									; Bound SlotI before any inventory array access (same as the "P"
									; pickup path above).
									If SlotI < 0 Or SlotI > Slots_Inventory Then SlotI = -1
									If SlotI >= 0 And (AI\Inventory\Items[SlotI] = Null Or (ItemInstancesIdentical(II, AI\Inventory\Items[SlotI]) And II\Item\Stackable = True And SlotI >= SlotI_Backpack))
										If SlotsMatch(II\Item, SlotI) And ActorHasSlot(AI\Actor, SlotI, II\Item)
											If AI\Inventory\Items[SlotI] <> Null
												Delete AI\Inventory\Items[SlotI]
											Else
												AI\Inventory\Amounts[SlotI] = 0
											EndIf
											AI\Inventory\Items[SlotI] = II
											AI\Inventory\Amounts[SlotI] = ClampStackAmount(AI\Inventory\Amounts[SlotI] + II\Assignment)  ; cap to 16-bit save/wire ceiling
											II\Assignment = 0
											If SlotI < SlotI_Backpack Then SendEquippedUpdate(AI)
										EndIf
									EndIf
								Else
									Delete II
								EndIf
							EndIf
						EndIf
					; Swap/add
					Case "S", "A"
						RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 2, 2))
						SlotA = RCE_IntFromStr(Mid$(M\MessageData$, 4, 1))
						SlotB = RCE_IntFromStr(Mid$(M\MessageData$, 5, 1))
						Amount = RCE_IntFromStr(Mid$(M\MessageData$, 6, 2))
						; Slot indices come straight off the wire as 1-byte
						; values (0..255). Subsequent array reads below
						; (Amounts[SlotA], Items[SlotA]) would walk past the
						; Inventory record into the adjacent ActorInstance
						; fields if SlotA / SlotB exceed Slots_Inventory.
						; Bound BEFORE the Amount comparison at line 1346.
						If SlotA < 0 Or SlotA > Slots_Inventory Then SlotA = -1
						If SlotB < 0 Or SlotB > Slots_Inventory Then SlotB = -1
						AI.ActorInstance = RuntimeIDList(RuntimeID)
						AIFrom.ActorInstance = FindActorInstanceFromRNID(M\FromID)
						; Check that actor instance is valid (e.g. it isn't trying to change someone else's inventory)
						If AI <> Null And AIFrom <> Null And SlotA >= 0 And SlotB >= 0
							; Walk AIFrom's FirstSlave chain to check if the
							; target actor (AI) is one of the sender's pets.
							; Replaces a global ActorInstance walk filtered
							; by `Leader = AIFrom`.
							IsPet = False
							Local Slave.ActorInstance = AIFrom\FirstSlave
							While Slave <> Null
								If Slave = AI Then IsPet = True : Exit
								Slave = Slave\NextSlave
							Wend
							If (AI = AIFrom Or IsPet = True) And (Amount = 0 Or Amount <= AI\Inventory\Amounts[SlotA])
								If Left$(M\MessageData$, 1) = "S"
									InventorySwap(AI, SlotA, SlotB, Amount, False)
									If SlotA < SlotI_Backpack Or SlotB < SlotI_Backpack Then SendEquippedUpdate(AI)
								Else
									InventoryAdd(AI, SlotA, SlotB, Amount, False)
									If SlotB < SlotI_Backpack Then SendEquippedUpdate(AI)
								EndIf
							EndIf
						EndIf
				End Select

			; Player dismounted
			Case P_Dismount
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null
					If AI\Mount <> Null
						ThreadScript("Mount", "Dismount", Handle(AI), Handle(AI\Mount))
						If AI\Mount\RNID < 0 Then AI\Mount\AIMode = AI_Patrol
						AI\Mount\Rider = Null
						AI\Mount\WalkingBackward = False
						AI\Mount\DestX# = AI\Mount\X#
						AI\Mount\DestZ# = AI\Mount\Z#
						AI\Mount = Null
					EndIf
				EndIf

			; A standard update
			Case P_StandardUpdate ; :)
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null
					;only process when client is not currently moving the actor for change area or reposition actor
					If AI\IgnoreUpdate = 0
					; Player cannot move himself if he is a mount
					If AI\Rider = Null
						; All four position floats are clamped through
						; ClampWorldCoord which rejects NaN/Inf and out-of-world
						; magnitudes by falling back to 0. Track A's original
						; Y-only filter is now uniform across X/Y/Z, and DestX/Z
						; (which were previously raw) also sanitised.
						AI\DestX#    = ClampWorldCoord#(RCE_FloatFromStr#(Mid$(M\MessageData$, 1, 4)))
						AI\DestZ#    = ClampWorldCoord#(RCE_FloatFromStr#(Mid$(M\MessageData$, 5, 4)))
						NewY#        = ClampWorldCoord#(RCE_FloatFromStr#(Mid$(M\MessageData$, 9, 4)))
						AI\Y#        = NewY#
						NewX#        = ClampWorldCoord#(RCE_FloatFromStr#(Mid$(M\MessageData$, 13, 4)))
						NewZ#        = ClampWorldCoord#(RCE_FloatFromStr#(Mid$(M\MessageData$, 17, 4)))
						AI\IsRunning = RCE_IntFromStr(Mid$(M\MessageData$, 21, 1))
						AI\WalkingBackward = RCE_IntFromStr(Mid$(M\MessageData$, 22, 1))

						
						; Players cannot run backwards, this will prevent cheaters from doing so
						If AI\WalkingBackward = True Then AI\IsRunning = False

						; Speed-hack clamp: bound the per-packet position delta
						; against actor's Speed attribute scaled by elapsed time
						; since the previous accepted update. Without this the
						; client can teleport arbitrarily within ClampWorldCoord
						; bounds between updates, defeating PvP positioning
						; checks and movement-based triggers. Tolerance factor
						; covers lag spikes, collision shove-back, and the
						; 1.5x base unit-per-tick coefficient that GameServer
						; uses for server-driven movement.
						Local PosNowMs% = MilliSecs()
						Local ElapsedMs% = PosNowMs - AI\LastPosUpdateMs
						If AI\LastPosUpdateMs = 0 Or ElapsedMs < 0 Or ElapsedMs > 5000
							; First update for this actor, or clock skew, or
							; a lag spike longer than 5s: accept the position
							; this time and re-baseline.
							AI\X# = NewX#
							AI\Z# = NewZ#
						Else
							Local SpeedAttr# = 1.0
							If AI\Attributes\Maximum[SpeedStat] > 0
								SpeedAttr# = Float#(AI\Attributes\Value[SpeedStat]) / Float#(AI\Attributes\Maximum[SpeedStat])
							EndIf
							; Base unit/ms (matches GameServer's 1.5 base unit/tick
							; for AI movement; assume ~50ms tick i.e. /50 -> /33 with
							; a 1.5x running and 3x lag tolerance fold-in).
							Local MaxUnitsPerMs# = 0.15 * (SpeedAttr# + 0.5)
							Local MaxDelta# = MaxUnitsPerMs# * Float#(ElapsedMs)
							If MaxDelta# < 2.0 Then MaxDelta# = 2.0
							Local DX# = NewX# - AI\X#
							Local DZ# = NewZ# - AI\Z#
							Local MoveDist# = Sqr#(DX# * DX# + DZ# * DZ#)
							If MoveDist# > MaxDelta#
								; Too fast: hold the client at the prior position.
								; The next update will look like the client agreed.
								; Client will re-sync to server state on next
								; reposition broadcast.
							Else
								AI\X# = NewX#
								AI\Z# = NewZ#
							EndIf
						EndIf
						AI\LastPosUpdateMs = PosNowMs
						AI\OldX# = AI\X#
						AI\OldZ# = AI\Z#

						; Move mount to same position if the player is riding one
						If AI\Mount <> Null
							AI\Mount\X# = AI\X#
							AI\Mount\Y# = AI\Y#
							AI\Mount\Z# = AI\Z#
							AI\Mount\OldX# = AI\X#
							AI\Mount\OldZ# = AI\Z#
							AI\Mount\DestX# = AI\DestX#
							AI\Mount\DestZ# = AI\DestZ#
							AI\Mount\IsRunning = AI\IsRunning
							AI\Mount\WalkingBackward = AI\WalkingBackward
							EndIf
						EndIf
					EndIf
				EndIf

			; A player left or timed out
			Case RCE_PlayerTimedOut, RCE_PlayerHasLeft, RCE_PlayerKicked
				If (M\MessageType = RCE_PlayerKicked) 
					M\FromID = RCE_IntFromStr(M\MessageData$)
				Else
					M\FromID = RCE_LastDisconnectedPeer()	
				EndIf
				; Find which actor instance he was
				AI.ActorInstance = FindActorInstanceFromRNID(M\FromID)
				If AI <> Null
					; Reset target
					AI\AITarget = Null

					; Tell other players in the same zone and remove this player from the linked list
					Pa$ = RCE_StrFromInt$(AI\RuntimeID, 2)
					AInstance.AreaInstance = Object.AreaInstance(AI\ServerArea)
					If AInstance <> Null
						A2.ActorInstance = AInstance\FirstInZone
						If A2 = AI
							AInstance\FirstInZone = AI\NextInZone
							A2 = AI\NextInZone
						EndIf
						While A2 <> Null
							If A2\RNID > 0 Then RCE_Send(Host, A2\RNID, P_ActorGone, Pa$, True)
							If A2\NextInZone = AI Then A2\NextInZone = AI\NextInZone
							A2 = A2\NextInZone
						Wend
					EndIf
					AI\ServerArea = 0

					; Cancel trading if he was trading with another player.
					;
					; Two-phase cleanup: the partner side and the disconnecting
					; side both carry trade state (IsTrading, TradingActor,
					; TradeResult$, TradeOfferedAmount[]). The original handler
					; only zeroed the partner's IsTrading and TradingActor, and
					; only sent P_CloseTrading when the partner had already
					; clicked accept (IsTrading >= 4). That left:
					;   - The partner's TradeOfferedAmount[] populated with
					;     amounts from this dead trade. The next P_OpenTrading
					;     accept against a fresh partner would read those
					;     server-tracked offers (NN wave 2's authoritative-
					;     swap source) and let the partner duplicate items
					;     that were never offered in the new trade.
					;   - A partner at IsTrading = 3 (window open, not yet
					;     accepted) with no CloseTrading notification: their
					;     trade UI stays open and they can still send
					;     P_UpdateTrading slots into a Null TradingActor.
					;   - AI's own trade fields still populated, which only
					;     matters when the ActorInstance survives logout (no
					;     MySQL branch); a subsequent re-login that reuses the
					;     same instance would pick up the stale state.
					If AI\IsTrading >= 3
						If AI\TradingActor <> Null
							Local Partner.ActorInstance = AI\TradingActor
							If Partner\RNID > 0 Then RCE_Send(Host, Partner\RNID, P_CloseTrading, "", True)
							Partner\IsTrading = 0
							Partner\TradingActor = Null
							Partner\TradeResult$ = ""
							For TradeSlot = 0 To 31
								Partner\TradeOfferedAmount[TradeSlot] = 0
							Next
						EndIf
						AI\TradingActor = Null
						AI\TradeResult$ = ""
						For TradeSlot = 0 To 31
							AI\TradeOfferedAmount[TradeSlot] = 0
						Next
					EndIf

					; Dehorsify
					If AI\Rider <> Null
						AI\Rider\Mount = Null
						AI\Rider = Null
					EndIf
					If AI\Mount <> Null
						AI\Mount\Rider = Null
						AI\Mount = Null
					EndIf

					; Update timers on active actor effects
					For AE.ActorEffect = Each ActorEffect
						If AE\Owner = AI
							AE\Length = AE\Length - (MilliSecs() - AE\CreatedTime)
						EndIf
					Next

					; Delete him
					A.Account = Object.Account(AI\Account)
					If A <> Null Then SetLoginStatus(A, -1)
					; Pause any persistent scripts and stop others.
					;
					; The original non-persistent branch called FreeActorScripts(AI)
					; on every iteration. That function sweeps *every* ScriptInstance
					; for AI — including the persistent SI we just paused above and
					; whose handle is now sitting in a fresh PausedScript. The sweep
					; flipped SI\Ended = True; the next UpdateScripts pass Deleted
					; the SI; later resumes touched PausedScript\S\WaitResult\$ on a
					; dangling pointer. Reliable use-after-free on any logout that
					; happened while a quest script was in WaitSpeak / WaitKill.
					;
					; Fix: each iteration cleans only its own SI plus the
					; PausedScript records that referenced exactly it, via an
					; After-cursor walk (the body Deletes PS).
					For SI.ScriptInstance = Each ScriptInstance
						If SI\AI = Handle(AI) Or SI\AIContext = Handle(AI)
							; Persistent
							If SI\Persistent = True
								If SI\Waiting = 0
									PS.PausedScript = New PausedScript
									PS\S = SI
									PS\ReasonActor = AI
									PS\Reason = 1
									SI\Waiting = 1
								EndIf
							; Not persistent
							Else
								Local LPS.PausedScript = First PausedScript
								Local LPSNext.PausedScript = Null
								While LPS <> Null
									LPSNext = After LPS
									If LPS\Reason <> 1 And LPS\S = SI Then Delete LPS
									LPS = LPSNext
								Wend
								FreeScriptInstance(SI)
							EndIf
						EndIf
					Next
					LeaveParty(AI)
					; Clear the ActorByRNID index BEFORE zeroing AI\RNID so
					; we can still read the slot we need to clear. Same
					; defensive `= AI` check FreeActorInstance uses.
					If AI\RNID > 0 And AI\RNID <= MaxRNID
						If ActorByRNID(AI\RNID) = AI Then ActorByRNID(AI\RNID) = Null
					EndIf
					; Remove from the online-player chain. Order independent
					; of the RNID zero -- OnlinePlayerRemove walks by identity,
					; not by RNID.
					OnlinePlayerRemove(AI)
					AI\RNID = 0
					If AI\RuntimeID > -1
						If RuntimeIDList(AI\RuntimeID) = AI Then RuntimeIDList(AI\RuntimeID) = Null
					EndIf
					AI\RuntimeID = -1
					AI\IsTrading = 0
					; Mark-and-sweep to avoid deleting the current iterator.
					; The original loop called FreeItemInstance(II) while II
					; was still the For-Each cursor; Blitz then used II's
					; freed next pointer for the following iteration,
					; intermittently skipping items or crashing the server
					; on logout. First pass tags candidates; we then sweep
					; via a fresh enumeration that never reaches a tagged
					; item by its own next pointer.
					For II.ItemInstance = Each ItemInstance
						If II\AssignTo = AI And II\Assignment > 0 Then II\Assignment = -1
					Next
					Local IINext.ItemInstance = Null
					II.ItemInstance = First ItemInstance
					While II <> Null
						IINext = After II
						If II\Assignment = -1 And II\AssignTo = AI Then FreeItemInstance(II)
						II = IINext
					Wend
					
					; Remove from server display
					If AInstance <> Null
						If AInstance\Area = GameArea
							For i = 0 To CountGadgetItems(Game\PlayersList) - 1
								Name$ = GadgetItemText$(Game\PlayersList, i)
								If Name$ = AI\Name$ + " (" + Str$(AInstance\ID) + ")" Then RemoveGadgetItem(Game\PlayersList, i) : Exit
							Next
						EndIf
					EndIf

					; Run logout script
					ThreadScript("Logout", "Main", Handle(AI), 0)
					
					; Unload account (only if using MySQL server version)
					/*If MySQL = True
						My_SaveAccount(A, True)
						
						; Free all data
						For j = 0 To 9
							If A\Character[j] <> Null
								FreeActorInstanceSlaves(A\Character[j])
								FreeActorInstance(A\Character[j])
								
								If A\QuestLog[j] <> Null Then Delete(A\QuestLog[j])
								If A\ActionBar[j] <> Null Then Delete(A\ActionBar[j])
							EndIf
						Next
						
						RemoveGadgetItem(Accounts\List, A\ListID)
						
						For AA.Account = Each Account
							If AA\ListID > A\ListID
								AA\ListID = AA\ListID -1
								If AA\IsDM = False
									If AA\IsBanned = False
										ModifyGadgetItem Accounts\List, AA\ListID, "* " + AA\User$ + "  (" + AA\Email$ + ")"
									Else
										ModifyGadgetItem Accounts\List, AA\ListID, "* [BAN] " + AA\User$ + "  (" + AA\Email$ + ")"
									EndIf
								Else
									If AA\IsBanned = False
										ModifyGadgetItem Accounts\List, AA\ListID, "* [GM] " + AA\User$ + "  (" + AA\Email$ + ")"
									Else
										ModifyGadgetItem Accounts\List, AA\ListID, "* [BAN][GM] " + AA\User$ + "  (" + AA\Email$ + ")"
									EndIf
								EndIf
							EndIf
						Next
						
						Delete(A)
					EndIf*/
				EndIf

			; Start game request
			Case P_StartGame ; :)
				; Throttle: extends the LoginAttemptOk gate from
				; P_VerifyAccount to this sibling auth handler. Without it,
				; an attacker can pump the SHA-256 dummy-hash cost added in
				; PR #267 at line-rate by sending bogus P_StartGame packets,
				; dodging the per-FromID throttle. Same canonical "N"
				; failure code on the throttled path so the throttle isn't
				; itself an oracle for "is this username registered".
				If Not LoginAttemptOk(M\FromID)
					RCE_Send(Host, M\FromID, P_StartGame, "N", True)
				Else
				UsernameLen = RCE_IntFromStr(Left$(M\MessageData$, 1))
				Username$ = Mid$(M\MessageData$, 2, UsernameLen)
				; Find account
				Exists = False
				For A.Account = Each Account
					If Upper$(A\User$) = Upper$(Username$) And A\LoggedOn = -1
						Offset = 2 + UsernameLen
						PwdLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))
						; PwdLen >= 1 + A\Pass$ <> "" reject truncated packets
						; and accounts with empty stored passwords (same guard
						; as P_VerifyAccount).
						Local IncomingPwd$ = Mid$(M\MessageData$, Offset + 1, PwdLen)
						If PwdLen >= 1 And A\Pass$ <> "" And VerifyPassword%(A\Pass$, IncomingPwd$) And A\IsBanned = False
							; Lazy-migrate the on-disk record to the salted v1
							; format once we know this MD5 is valid. The next
							; SaveAccounts() persists the upgrade.
							A\Pass$ = UpgradePasswordIfLegacy$(A\Pass$, IncomingPwd$)
							Offset = Offset + 1 + PwdLen
							Number = Asc(Mid$(M\MessageData$, Offset, 1))

							If Number > -1 And Number < 10 And A\Character[Number] <> Null
								; Resolve saved area BEFORE flipping login state.
								; If the saved area was deleted from data files,
								; SetArea would silently no-op (PR adding Null
								; guard) and leave the player in limbo with no
								; ServerArea -- they'd see a black screen and
								; the next packet would dereference Null. Refuse
								; the login cleanly instead so the user can pick
								; a different character.
								Ar.Area = FindArea(A\Character[Number]\Area$)
								If Ar = Null
									WriteLog(MainLog, "P_StartGame: character '" + A\Character[Number]\Name$ + "' saved area '" + A\Character[Number]\Area$ + "' not found, refusing login")
									LoginAttemptRecord(M\FromID, False)
									RCE_Send(Host, M\FromID, P_StartGame, "N", True)
									Exists = True : Exit
								EndIf
								; Set his status to in game and put him in his area
								SetLoginStatus(A, Number)
								A\Character[Number]\RNID = M\FromID
								; Populate the O(1) sender-resolution index.
								; Bounds-checked because M\FromID came off the
								; wire (technically already validated by
								; RCE_StartHost's 5000-player cap, but defensive
								; here too -- belt and suspenders for a
								; long-lived global array).
								If M\FromID > 0 And M\FromID <= MaxRNID
									ActorByRNID(M\FromID) = A\Character[Number]
								EndIf
								; Add to the FirstOnlinePlayer chain so the 7
								; broadcast loops (chat / per-tick update) can
								; walk just online players instead of every
								; ActorInstance. See Actors.bb's
								; OnlinePlayerInsert / OnlinePlayerRemove.
								OnlinePlayerInsert(A\Character[Number])
								AssignRuntimeID(A\Character[Number])
								SetArea(A\Character[Number], Ar, 0, -1, -1, A\Character[Number]\X#, A\Character[Number]\Y#, A\Character[Number]\Z#)
								; Run login script
								ThreadScript("Login", "Main", Handle(A\Character[Number]), 0)
								; Send action bar data, runtime ID and XP bar level
								Pa$ = ""
								For i = 0 To 35
									Pa$ = Pa$ + RCE_StrFromInt$(Len(A\ActionBar[Number]\Slots$[i]), 2) + A\ActionBar[Number]\Slots$[i]
									If (i + 1) Mod 3 = 0 And i > 0
										SendQueued(Host, M\FromID, P_StartGame, Chr$(i - 2) + Pa$, True)
										Pa$ = ""
									EndIf
								Next
								LoginAttemptRecord(M\FromID, True)
								RCE_Send(Host, M\FromID, P_StartGame, RCE_StrFromInt$(A\Character[Number]\RuntimeID, 2), True)
								RCE_Send(Host, M\FromID, P_ChatMessage, Chr$(254) + LoginMessage$, True)
								RCE_Send(Host, M\FromID, P_XPUpdate, "B" + RCE_StrFromInt$(A\Character[Number]\XPBarLevel, 1), True)
								; Send active actor effects
								For AE.ActorEffect = Each ActorEffect
									If AE\Owner = A\Character[Number]
										AE\CreatedTime = MilliSecs()
										Pa$ = RCE_StrFromInt$(Handle(AE), 4) + RCE_StrFromInt$(AE\IconTexID, 2) + AE\Name$
										RCE_Send(Host, M\FromID, P_ActorEffect, "A" + Pa$, True)
									EndIf
								Next
							Else
						        LoginAttemptRecord(M\FromID, False)
						        RCE_Send(Host, M\FromID, P_StartGame, "N", True)
							EndIf
						; Otherwise return failure
						Else
					        LoginAttemptRecord(M\FromID, False)
					        RCE_Send(Host, M\FromID, P_StartGame, "N", True)
						EndIf
						Exists = True
						Exit
					EndIf
				Next
		        ; If account was not found or was already logged on, return failure.
		        ; Pay SHA-256 cost so the no-account / already-logged-on path
		        ; is timing-indistinguishable from the wrong-password path inside
		        ; the For loop above (which calls VerifyPassword).
		        If Exists = False
		            Offset = 2 + UsernameLen
		            PwdLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))
		            VerifyPassword%("", Mid$(M\MessageData$, Offset + 1, PwdLen))
		            LoginAttemptRecord(M\FromID, False)
		            RCE_Send(Host, M\FromID, P_StartGame, "N", True)
		        EndIf
				EndIf  ; close LoginAttemptOk Else
				; (removed) `If (M\FromID = 2) Stop` — a leftover debug trap that
				; halted the entire server process the first time the peer whose
				; RakNet connection ID is 2 sent P_StartGame. Trivial remote DoS
				; for the second connecting client.

			; Fetch update files list request
			Case P_FetchUpdateFiles ; :)
				Pa$ = "" : TotalFiles = 0
				For U.UpdateFile = Each UpdateFile
					TotalFiles = TotalFiles + 1
					Add$ = RCE_StrFromInt$(U\Checksum, 4) + RCE_StrFromInt$(Len(U\Name$), 1) + U\Name$
					If Len(Pa$ + Add$) >= 950
						SendQueued(Host, M\FromID, P_FetchUpdateFiles, Pa$, True)
						Pa$ = Add$
					Else
						Pa$ = Pa$ + Add$
					EndIf
				Next
				SendQueued(Host, M\FromID, P_FetchUpdateFiles, Pa$ + RCE_StrFromInt$(TotalFiles, 2), True)

			; Fetch actor list request
			Case P_FetchActors ; :)
				; Attributes block
				Pa$ = "A" + RCE_StrFromInt$(AttributeAssignment, 1)
				For i = 0 To 39
					Pa$ = Pa$ + RCE_StrFromInt$(AttributeIsSkill(i), 1) + RCE_StrFromInt$(AttributeHidden(i), 1)
					Pa$ = Pa$ + RCE_StrFromInt$(Len(AttributeNames$(i)), 1) + AttributeNames$(i)
				Next
				SendQueued(Host, M\FromID, P_FetchActors, Pa$, True)
				Pa$ = "D"
				For i = 0 To 19
					Pa$ = Pa$ + RCE_StrFromInt$(Len(DamageTypes$(i)), 1) + DamageTypes$(i)
				Next
				SendQueued(Host, M\FromID, P_FetchActors, Pa$, True)
				; Environment block
				Pa$ = RCE_StrFromInt$(Year, 4) + RCE_StrFromInt$(Day, 2)
				Pa$ = Pa$ + RCE_StrFromInt$(TimeH, 1) + RCE_StrFromInt$(TimeM, 1) + RCE_StrFromInt$(TimeFactor, 1)
				For i = 0 To 11
					Pa$ = Pa$ + RCE_StrFromInt$(Len(SeasonName$(i)), 1) + SeasonName$(i)
					Pa$ = Pa$ + RCE_StrFromInt$(SeasonStartDay(i), 2)
					Pa$ = Pa$ + RCE_StrFromInt$(SeasonDuskH(i), 1)
					Pa$ = Pa$ + RCE_StrFromInt$(SeasonDawnH(i), 1)
				Next
				For i = 0 To 19
					Pa$ = Pa$ + RCE_StrFromInt$(Len(MonthName$(i)), 1) + MonthName$(i)
					Pa$ = Pa$ + RCE_StrFromInt$(MonthStartDay(i), 2)
				Next
				SendQueued(Host, M\FromID, P_FetchActors, "E" + Pa$, True)
				; Item blocks
				Pa$ = "" : ItemsSent = 0 : TotalItems = 0
				For It.Item = Each Item
					TotalItems = TotalItems + 1
					ItemsSent = ItemsSent + 1
					Pa$ = Pa$ + RCE_StrFromInt$(It\ID, 2) + RCE_StrFromInt$(It\ItemType, 1) + RCE_StrFromInt$(It\TakesDamage, 1)
					Pa$ = Pa$ + RCE_StrFromInt$(It\Value, 4) + RCE_StrFromInt$(It\Mass, 2) + RCE_StrFromInt$(It\ThumbnailTexID, 2)
					For j = 0 To 5 : Pa$ = Pa$ + RCE_StrFromInt$(It\Gubbins[j], 1) : Next
					Pa$ = Pa$ + RCE_StrFromInt$(It\MMeshID, 2) + RCE_StrFromInt$(It\FMeshID, 2) + RCE_StrFromInt$(It\SlotType, 2)
					Pa$ = Pa$ + RCE_StrFromInt$(It\Stackable, 1)
					For j = 0 To 39 : Pa$ = Pa$ + RCE_StrFromInt$(It\Attributes\Value[j] + 5000, 2) : Next
					Pa$ = Pa$ + RCE_StrFromInt$(Len(It\Name$), 1) + It\Name$
					Pa$ = Pa$ + RCE_StrFromInt$(Len(It\ExclusiveRace$), 1) + It\ExclusiveRace$
					Pa$ = Pa$ + RCE_StrFromInt$(Len(It\ExclusiveClass$), 1) + It\ExclusiveClass$
					Select It\ItemType
						Case I_Weapon
							Pa$ = Pa$ + RCE_StrFromInt$(It\WeaponDamage, 2) + RCE_StrFromInt$(It\WeaponDamageType, 2)
							Pa$ = Pa$ + RCE_StrFromInt$(It\WeaponType, 2) + RCE_StrFromFloat$(It\Range#)
						Case I_Armour
							Pa$ = Pa$ + RCE_StrFromInt$(It\ArmourLevel, 2)
						Case I_Potion, I_Ingredient
							Pa$ = Pa$ + RCE_StrFromInt$(It\EatEffectsLength, 2)
						Case I_Image
							Pa$ = Pa$ + RCE_StrFromInt$(It\ImageID, 2)							
					End Select
					Pa$ = Pa$ + RCE_StrFromInt$(Len(It\MiscData$), 1) + It\MiscData$
					If ItemsSent >= 6 And It <> Last Item
						SendQueued(Host, M\FromID, P_FetchActors, "IN" + Pa$, True)
						ItemsSent = 0
						Pa$ = ""
					EndIf
				Next
				SendQueued(Host, M\FromID, P_FetchActors, "IY" + RCE_StrFromInt$(TotalItems, 2) + Pa$, True)
				; Faction blocks
				Pa$ = ""
				For i = 0 To 99
					Pa$ = Pa$ + RCE_StrFromInt$(Len(FactionNames$(i)), 1) + RCE_StrFromInt$(i, 1) + FactionNames$(i)
					If Len(Pa$) >= 800
						SendQueued(Host, M\FromID, P_FetchActors, "F" + Pa$, True)
						Pa$ = ""
					EndIf
				Next
				If Pa$ <> "" Then SendQueued(Host, M\FromID, P_FetchActors, "F" + Pa$, True)
				; Actor blocks
				Pa$ = "" : ActorsSent = 0 : TotalActors = 0
				For Ac.Actor = Each Actor
					TotalActors = TotalActors + 1
					ActorsSent = ActorsSent + 1
					Pa$ = Pa$ + RCE_StrFromInt$(Ac\ID, 2) + RCE_StrFromInt$(Ac\Playable, 1) + RCE_StrFromInt$(Ac\PolyCollision, 1)
					For i = 0 To 7 : Pa$ = Pa$ + RCE_StrFromInt$(Ac\MeshIDs[i], 2) : Next
					For i = 0 To 4 : Pa$ = Pa$ + RCE_StrFromInt$(Ac\BeardIDs[i], 2) : Next
					For i = 0 To 4 : Pa$ = Pa$ + RCE_StrFromInt$(Ac\MaleHairIDs[i], 2) : Next
					For i = 0 To 4 : Pa$ = Pa$ + RCE_StrFromInt$(Ac\FemaleHairIDs[i], 2) : Next
					For i = 0 To 4 : Pa$ = Pa$ + RCE_StrFromInt$(Ac\MaleFaceIDs[i], 2) : Next
					For i = 0 To 4 : Pa$ = Pa$ + RCE_StrFromInt$(Ac\FemaleFaceIDs[i], 2) : Next
					For i = 0 To 4 : Pa$ = Pa$ + RCE_StrFromInt$(Ac\MaleBodyIDs[i], 2) : Next
					For i = 0 To 4 : Pa$ = Pa$ + RCE_StrFromInt$(Ac\FemaleBodyIDs[i], 2) : Next
					For i = 0 To 15 : Pa$ = Pa$ + RCE_StrFromInt$(Ac\MSpeechIDs[i], 2) : Next
					For i = 0 To 15 : Pa$ = Pa$ + RCE_StrFromInt$(Ac\FSpeechIDs[i], 2) : Next
					Pa$ = Pa$ + RCE_StrFromInt$(Ac\Rideable, 1) + RCE_StrFromInt$(Ac\TradeMode, 1) + RCE_StrFromInt$(Ac\BloodTexID, 2)
					Pa$ = Pa$ + RCE_StrFromInt$(Ac\Aggressiveness, 1) + RCE_StrFromInt$(Ac\Genders, 1)
					Pa$ = Pa$ + RCE_StrFromInt$(Ac\Environment, 1) + RCE_StrFromInt$(Ac\InventorySlots, 2)
					Pa$ = Pa$ + RCE_StrFromInt$(Ac\MAnimationSet, 2) + RCE_StrFromInt$(Ac\FAnimationSet, 2)
					Pa$ = Pa$ + RCE_StrFromFloat$(Ac\Scale#)
					Pa$ = Pa$ + RCE_StrFromInt$(Ac\DefaultFaction, 1)
					For i = 0 To 39
						Pa$ = Pa$ + RCE_StrFromInt$(Ac\Attributes\Value[i], 2)
						Pa$ = Pa$ + RCE_StrFromInt$(Ac\Attributes\Maximum[i], 2)
					Next
					Pa$ = Pa$ + RCE_StrFromInt$(Len(Ac\Race$), 1) + Ac\Race$
					Pa$ = Pa$ + RCE_StrFromInt$(Len(Ac\Class$), 1) + Ac\Class$
					Pa$ = Pa$ + RCE_StrFromInt$(Len(Ac\Description$), 2) + Ac\Description$
					If ActorsSent >= 2 And Ac <> Last Actor
						SendQueued(Host, M\FromID, P_FetchActors, "N" + Pa$, True)
						ActorsSent = 0
						Pa$ = ""
					EndIf
				Next
				SendQueued(Host, M\FromID, P_FetchActors, "Y" + RCE_StrFromInt$(TotalActors, 2) + Pa$, True)

			; New account creation request
			Case P_CreateAccount ; :)
				If AllowAccountCreation = True
					UsernameLen = RCE_IntFromStr(Left$(M\MessageData$, 1))
					Username$ = Mid$(M\MessageData$, 2, UsernameLen)
					; Check that username does not already exist
					Exists = False
					If MySQL = True
						//Exists = My_AccountExists(Username$)
					Else
						For A.Account = Each Account
							If Upper$(A\User$) = Upper$(Username$) Then Exists = True : Exit
						Next
					EndIf

					If Exists = False
						Offset = 2 + UsernameLen
						PwdLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))
						Password$ = Mid$(M\MessageData$, Offset + 1, PwdLen)
						Offset = Offset + 1 + PwdLen
						EmailLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))
						Email$ = Encrypt$(Mid$(M\MessageData$, Offset + 1, EmailLen), 1)
						; Check that all characters are valid
						Valid = True
						For i = 1 To Len(Username$)
							Char = Asc(Mid$(Username$, i, 1))
							If (Char < 48 Or Char > 57) And (Char < 65 Or Char > 90) And (Char < 97 Or Char > 122) And Char <> 95 And Char < 192 Then Valid = False : Exit
						Next
						
						For i = 1 To Len(Password$)
							Char = Asc(Mid$(Password$, i, 1))
							If (Char < 48 Or Char > 57) And (Char < 65 Or Char > 90) And (Char < 97 Or Char > 122) And Char <> 46 And Char <> 95 And Char < 192 Then Valid = False : Exit
						Next

						For i = 1 To Len(Email$)
							Char = Asc(Mid$(Email$, i, 1))
							If (Char < 48 Or Char > 57) And (Char < 64 Or Char > 90) And (Char < 97 Or Char > 122) And Char <> 42 And Char <> 43 And Char <> 45 And Char <> 46 And Char <> 61 And Char <> 95 And Char < 192 Then Valid = False : Exit
						Next
						If Len(Username$) > 50 Or Len(Password$) > 50 Or Len(Email$) > 200 Then Valid = False
						If Valid = True
							If MySQL = True
								//My_AddAccount(Username$, Password$, Email$)
							Else
								AddAccount(Username$, Password$, Email$)
							EndIf
							RCE_Send(Host, M\FromID, P_CreateAccount, "Y", True)
						Else
							RCE_Send(Host, M\FromID, P_CreateAccount, "N", True)
						EndIf
					Else
						RCE_Send(Host, M\FromID, P_CreateAccount, "N", True)
					EndIf
				EndIf

			; Account verification request
			Case P_VerifyAccount ; :)
				UsernameLen = RCE_IntFromStr(Left$(M\MessageData$, 1))
				Username$ = Mid$(M\MessageData$, 2, UsernameLen)

				; MySQL version
				/*If MySQL = True
					; Get password
					Offset = 2 + UsernameLen
					PwdLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))
					Password$ = Mid$(M\MessageData$, Offset + 1, PwdLen)

					; Load account, don't force password skip
					A.Account = My_LoadAccount(Username$, Password$, False)


					If A = Null
						If My_Reason = MY_WRONGLOGIN
							RCE_Send(Host, M\FromID, P_VerifyAccount, "P", True)
						ElseIf My_Reason = MY_BANNED
							RCE_Send(Host, M\FromID, P_VerifyAccount, "B", True)
						ElseIf My_Reason = MY_NOACCOUNT
							RCE_Send(Host, M\FromID, P_VerifyAccount, "N", True)
						ElseIf My_Reason = MY_ACCOUNTLOGGEDIN
							RCE_Send(Host, M\FromID, P_VerifyAccount, "L", True)
						EndIf
					Else	
						Pa$ = "Y"
						For i = 0 To 9
							If A\Character[i] <> Null
								Pa$ = Pa$ + RCE_StrFromInt$(Len(A\Character[i]\Name$), 1) + A\Character[i]\Name$
								Pa$ = Pa$ + RCE_StrFromInt$(A\Character[i]\Actor\ID, 2) + RCE_StrFromInt$(A\Character[i]\Gender, 1)
								Pa$ = Pa$ + RCE_StrFromInt$(A\Character[i]\FaceTex, 1) + RCE_StrFromInt$(A\Character[i]\Hair, 1)
								Pa$ = Pa$ + RCE_StrFromInt$(A\Character[i]\Beard, 1) + RCE_StrFromInt$(A\Character[i]\BodyTex, 1)
							EndIf
						Next
						RCE_Send(Host, M\FromID, P_VerifyAccount, Pa$, True)
					EndIf
				; Non-MySQL version
				Else*/

					; Rate-limit failed verifications. Without this the lack of any
					; throttle made dictionary / credential-stuffing attacks free.
					; Below the throttle threshold the rest of this handler now
					; uses the "auth before disclosure" pattern: the same "P"
					; reply for nonexistent / wrong-password / banned-without-
					; correct-password, and the existence-revealing "L" (online)
					; and "B" (banned) codes are sent ONLY after the password
					; verifies. That removes the username-enumeration / presence
					; / ban-status oracles a patient attacker could previously
					; mine below the throttle. The throttled case keeps the same
					; collapse (now "P", matching the canonical failure code).
					If Not LoginAttemptOk(M\FromID)
						RCE_Send(Host, M\FromID, P_VerifyAccount, "P", True)
					Else
					; Find account (without disclosing the result yet)
					Local FoundA.Account = Null
					For A.Account = Each Account
						If Upper$(A\User$) = Upper$(Username$)
							FoundA = A
							Exit
						EndIf
					Next

					Offset = 2 + UsernameLen
					PwdLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))

					; Reject truncated / empty-password packets. Without this
					; guard a 1-byte packet leaves PwdLen=0 and Mid$ returns ""
					; -- which matches any account whose Pass$ was ever stored
					; as the empty string. Same "P" as wrong-password so the
					; response doesn't betray the cause.
					If FoundA = Null Or PwdLen < 1
						; Pay SHA-256 cost so the no-account / truncated-packet
						; path is timing-indistinguishable from the wrong-password
						; path below (which calls VerifyPassword on the real
						; stored hash). VerifyPassword runs a dummy hash on
						; empty Stored. Closes the timing oracle PR #264
						; deferred as a follow-up.
						VerifyPassword%("", Mid$(M\MessageData$, Offset + 1, PwdLen))
						LoginAttemptRecord(M\FromID, False)
						RCE_Send(Host, M\FromID, P_VerifyAccount, "P", True)
					Else
					; Hoist password verification to a local so we can branch on
					; it without tripping BlitzForge's parser rejection of
					; `Or Not <call>` in an ElseIf condition. Also makes the
					; happy-path branches read straight (no negation).
					Local PwdOk%
					If FoundA\Pass$ = ""
						PwdOk = False
					Else
						PwdOk = VerifyPassword%(FoundA\Pass$, Mid$(M\MessageData$, Offset + 1, PwdLen))
					EndIf

					If PwdOk = False
						; Wrong password (or stored hash is empty). Collapse to
						; "P" regardless of banned / online status so a
						; pre-auth attacker can't probe either.
						LoginAttemptRecord(M\FromID, False)
						RCE_Send(Host, M\FromID, P_VerifyAccount, "P", True)
					ElseIf FoundA\IsBanned <> False
						; Password verified AND account is banned -- only now
						; is it safe to tell this caller they're banned, because
						; only the legitimate owner could have reached this
						; branch. Count as a failed attempt for rate-limit
						; bookkeeping so a banned user can't keep the throttle
						; window open.
						LoginAttemptRecord(M\FromID, False)
						RCE_Send(Host, M\FromID, P_VerifyAccount, "B", True)
					ElseIf FoundA\LoggedOn <> -1
						; Password verified AND a session is already active --
						; same "auth before disclosure" reasoning: only the
						; legitimate owner sees the "already logged in" hint.
						LoginAttemptRecord(M\FromID, False)
						RCE_Send(Host, M\FromID, P_VerifyAccount, "L", True)
					Else
						; Success: send back character list.
						LoginAttemptRecord(M\FromID, True)
						; Lazy-migrate to v1 on first successful login.
						FoundA\Pass$ = UpgradePasswordIfLegacy$(FoundA\Pass$, Mid$(M\MessageData$, Offset + 1, PwdLen))
						Pa$ = "Y"
						For i = 0 To 9
							If FoundA\Character[i] <> Null
								Pa$ = Pa$ + RCE_StrFromInt$(Len(FoundA\Character[i]\Name$), 1) + FoundA\Character[i]\Name$
								Pa$ = Pa$ + RCE_StrFromInt$(FoundA\Character[i]\Actor\ID, 2) + RCE_StrFromInt$(FoundA\Character[i]\Gender, 1)
								Pa$ = Pa$ + RCE_StrFromInt$(FoundA\Character[i]\FaceTex, 1) + RCE_StrFromInt$(FoundA\Character[i]\Hair, 1)
								Pa$ = Pa$ + RCE_StrFromInt$(FoundA\Character[i]\Beard, 1) + RCE_StrFromInt$(FoundA\Character[i]\BodyTex, 1)
							EndIf
						Next
						RCE_Send(Host, M\FromID, P_VerifyAccount, Pa$, True)
					EndIf
					EndIf
					EndIf
				//EndIf

			; Change account password request
			Case P_ChangePassword
				UsernameLen = RCE_IntFromStr(Left$(M\MessageData$, 1))
				Username$ = Mid$(M\MessageData$, 2, UsernameLen)
				; Find account
				Exists = False
				For A.Account = Each Account
					If Upper$(A\User$) = Upper$(Username$)
						Offset = 2 + UsernameLen
						PwdLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))
						; PwdLen >= 1 + A\Pass$ <> "" reject truncated packets
						; and accounts with empty stored passwords. Without
						; this guard, a 1-byte packet would match any account
						; whose Pass$ was ever stored as the empty string.
						;
						; If password is correct AND the requester is the
						; currently-logged-in session for this account, change
						; it. Without the session check, a captured/replayed
						; P_ChangePassword lets any party with the (broken-
						; MD5) hash steal the account permanently.
						If PwdLen >= 1 And A\Pass$ <> "" And VerifyPassword%(A\Pass$, Mid$(M\MessageData$, Offset + 1, PwdLen)) And RequesterOwnsAccountSession(A, M\FromID)
							Offset = 2 + PwdLen
							PwdLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))
							; Store the new password in the v1 salted format
							; immediately, not the raw client MD5.
							A\Pass$ = HashPassword$(Mid$(M\MessageData$, Offset + 1, PwdLen))
							RCE_Send(Host, M\FromID, P_ChangePassword, "Y", True)
							//If MySQL = True Then My_SaveAccount(A, False)
						; Otherwise return password failure
						Else
							RCE_Send(Host, M\FromID, P_ChangePassword, "P", True)
						EndIf
						Exists = True
						Exit
					EndIf
				Next

				; If account was not found, return failure.
				; Auth-before-disclosure: emit the same "P" code as the
				; "found-account-but-fail" path on line ~2289 so the wire
				; response can't be used to enumerate registered usernames.
				; Same shape PR #264 closed for P_VerifyAccount; the same
				; "patient attacker sends throwaway-password probes to map
				; the account list" oracle exists here. RequesterOwns-
				; AccountSession() makes the legitimate path session-gated,
				; but an unauthenticated peer could still send P_Change-
				; Password with any username + any password and (pre-fix)
				; observe "N" vs "P" to discriminate existence.
				; Also pay SHA-256 cost so timing matches the wrong-password
				; path in the For loop (closes the no-account timing oracle).
				If Exists = False
					Offset = 2 + UsernameLen
					PwdLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))
					VerifyPassword%("", Mid$(M\MessageData$, Offset + 1, PwdLen))
					RCE_Send(Host, M\FromID, P_ChangePassword, "P", True)
				EndIf

			; Request to fetch character data
			Case P_FetchCharacter ; :)
				; Throttle (see P_StartGame above for rationale).
				If Not LoginAttemptOk(M\FromID)
					RCE_Send(Host, M\FromID, P_FetchCharacter, "N", True)
				Else
				UsernameLen = RCE_IntFromStr(Left$(M\MessageData$, 1))
				Username$ = Mid$(M\MessageData$, 2, UsernameLen)
				; Find account
				Exists = False
				For A.Account = Each Account
					If Upper$(A\User$) = Upper$(Username$)
						Offset = 2 + UsernameLen
						PwdLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))
						; PwdLen >= 1 + A\Pass$ <> "" reject truncated packets
						; and accounts with empty stored passwords.
						If PwdLen >= 1 And A\Pass$ <> "" And VerifyPassword%(A\Pass$, Mid$(M\MessageData$, Offset + 1, PwdLen)) And A\IsBanned = False
							Offset = Offset + 1 + PwdLen
							Number = Asc(Mid$(M\MessageData$, Offset, 1))
							; Mirror P_StartGame's guard (line ~1848): an
							; authenticated client can still send a Number for
							; an empty slot, and dereferencing A\Character[Number]
							; without the Null check below was a one-packet
							; server crash. Refuse cleanly with "N" instead.
							If Number > -1 And Number < 10 And A\Character[Number] <> Null
								; Send character data
								Pa$ = RCE_StrFromInt$(A\Character[Number]\Gold, 4) + RCE_StrFromInt$(A\Character[Number]\Reputation, 2)
								Pa$ = Pa$ + RCE_StrFromInt$(A\Character[Number]\Level, 2) + RCE_StrFromInt$(A\Character[Number]\XP, 4)
								Pa$ = Pa$ + RCE_StrFromInt$(A\Character[Number]\HomeFaction, 1)
								For i = 0 To 39
									Pa$ = Pa$ + RCE_StrFromInt$(A\Character[Number]\Attributes\Value[i], 2)
									Pa$ = Pa$ + RCE_StrFromInt$(A\Character[Number]\Attributes\Maximum[i], 2)
								Next
								SendQueued(Host, M\FromID, P_FetchCharacter, "C1" + Pa$, True)
								Pa$ = ""
								For i = 0 To Slots_Inventory
									If A\Character[Number]\Inventory\Items[i] <> Null
										Pa$ = Pa$ + Chr$(i) + ItemInstanceToString$(A\Character[Number]\Inventory\Items[i])
										Pa$ = Pa$ + RCE_StrFromInt$(A\Character[Number]\Inventory\Amounts[i], 2)
									Else
										Pa$ = Pa$ + Chr$(99)
									EndIf
									If Len(Pa$) > 999 - ItemInstanceStringLength()
										SendQueued(Host, M\FromID, P_FetchCharacter, "C3" + Pa$, True)
										Pa$ = ""
									EndIf
								Next
								If Pa$ <> "" Then SendQueued(Host, M\FromID, P_FetchCharacter, "C3" + Pa$, True)
								; Send known spells
								Pa$ = ""
								SpellsDone = 0
								For i = 0 To 999
									If A\Character[Number]\SpellLevels[i] > 0
										OldPa$ = OldPa$ + Pa$
										Pa$ = ""
										Sp.Spell = SpellsList(A\Character[Number]\KnownSpells[i])
										If( Sp <> Null )
											Pa$ = Pa$ + RCE_StrFromInt$(A\Character[Number]\SpellLevels[i], 2) + RCE_StrFromInt$(Sp\ID, 2)
											Pa$ = Pa$ + RCE_StrFromInt$(Sp\ThumbnailTexID, 2) + RCE_StrFromInt$(Sp\RechargeTime, 2)
											Pa$ = Pa$ + RCE_StrFromInt$(Len(Sp\Name$), 2) + Sp\Name$ + RCE_StrFromInt$(Len(Sp\Description$), 2) + Sp\Description$
											Pa$ = Pa$ + RCE_StrFromInt$(0, 1)
											For j = 0 To 9
												If A\Character[Number]\MemorisedSpells[j] = i
													Pa$ = Left$(Pa$, Len(Pa$) - 1) + RCE_StrFromInt$(1, 1)
													Exit
												EndIf
											Next
											SpellsDone = SpellsDone + 1
										Else
											A\Character[Number]\SpellLevels[i] = 0
											A\Character[Number]\KnownSpells[i] = 0
										EndIf
										
										If Len(OldPa$ + Pa$) > 1000
											SendQueued(Host, M\FromID, P_FetchCharacter, "S" + OldPa$, True)
											OldPa$ = ""
										EndIf
									EndIf
								Next
								OldPa$ = OldPa$ + Pa$
								If Pa$ <> "" Then SendQueued(Host, M\FromID, P_FetchCharacter, "S" + OldPa$, True)
								; Send quest log
								Pa$ = "" : Num = 0
								For i = 0 To 499
									If A\QuestLog[Number]\EntryName$[i] <> ""
										Num = Num + 1
										Pa$ = Pa$ + RCE_StrFromInt$(Len(A\QuestLog[Number]\EntryName$[i]), 1)
										Pa$ = Pa$ + A\QuestLog[Number]\EntryName$[i]
										Pa$ = Pa$ + RCE_StrFromInt$(Len(A\QuestLog[Number]\EntryStatus$[i]), 2)
										Pa$ = Pa$ + A\QuestLog[Number]\EntryStatus$[i]
										If Len(Pa$) > 700
											SendQueued(Host, M\FromID, P_FetchCharacter, "Q" + Pa$, True)
											Pa$ = ""
										EndIf
									EndIf
								Next
								If Len(Pa$) > 0 Then SendQueued(Host, M\FromID, P_FetchCharacter, "Q" + Pa$, True)
								SendQueued(Host, M\FromID, P_FetchCharacter, "F" + RCE_StrFromInt$(Num, 2) + RCE_StrFromInt$(SpellsDone, 2), True)
								LoginAttemptRecord(M\FromID, True)
							Else
						        LoginAttemptRecord(M\FromID, False)
						        RCE_Send(Host, M\FromID, P_FetchCharacter, "N", True)
							EndIf
						; Otherwise return failure
						Else
							LoginAttemptRecord(M\FromID, False)
							RCE_Send(Host, M\FromID, P_FetchCharacter, "N", True)
						EndIf
						Exists = True
						Exit
					EndIf
				Next
		        ; If account was not found, return failure.
		        ; Pay SHA-256 cost so timing matches the wrong-password path
		        ; in the For loop (closes the no-account timing oracle).
		        If Exists = False
		            Offset = 2 + UsernameLen
		            PwdLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))
		            VerifyPassword%("", Mid$(M\MessageData$, Offset + 1, PwdLen))
		            LoginAttemptRecord(M\FromID, False)
		            RCE_Send(Host, M\FromID, P_FetchCharacter, "N", True)
		        EndIf
				EndIf  ; close LoginAttemptOk Else

			; New charater creation request
			Case P_CreateCharacter ; :)
				; Throttle (see P_StartGame above for rationale).
				If Not LoginAttemptOk(M\FromID)
					RCE_Send(Host, M\FromID, P_CreateCharacter, "N", True)
				Else
				UsernameLen = RCE_IntFromStr(Left$(M\MessageData$, 1))
				Username$ = Mid$(M\MessageData$, 2, UsernameLen)
				; Find account
				Exists = False
				For A.Account = Each Account
					If Upper$(A\User$) = Upper$(Username$)
						Offset = 2 + UsernameLen
						PwdLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))
						; If password is correct (PwdLen >= 1 + A\Pass$ <> ""
						; reject truncated packets and empty stored passwords)
						If PwdLen >= 1 And A\Pass$ <> "" And VerifyPassword%(A\Pass$, Mid$(M\MessageData$, Offset + 1, PwdLen))
							Offset = Offset + 1 + PwdLen

							; Check character name is valid. Bound length and reject
							; control bytes / non-printable characters before the
							; name is persisted into Accounts.dat and broadcast
							; into chat/nametag widgets. Without the cap a single
							; CreateCharacter packet could write a multi-hundred-
							; character name with embedded newlines into account
							; data; clients that render nametags / list-box rows
							; with raw bytes would then display garbage.
							NameValid = True
							Name$ = Upper$(Mid$(M\MessageData$, Offset + 47))
							If Len(Name$) < 1 Or Len(Name$) > 32 Then NameValid = False
							If NameValid
								For nameI = 1 To Len(Name$)
									Local nameCh = Asc(Mid$(Name$, nameI, 1))
									; Allow printable ASCII (space..~) only.
									If nameCh < 32 Or nameCh > 126
										NameValid = False
										Exit
									EndIf
								Next
							EndIf
							F = ReadFile("Data\Server Data\Names Filter.txt")
							; Missing filter file used to RuntimeError-crash the
							; server here (during P_CreateCharacter handler -- so
							; the first new player after server boot disconnected
							; every other player). Soft-fail: log and accept the
							; name without filtering. The fix is to restore the
							; file; failing-open is the right default during the
							; window between the file disappearing and an admin
							; noticing.
							If F = 0
								WriteLog(MainLog, "P_CreateCharacter: Names Filter.txt missing or unreadable; accepting all names without filtering until file is restored")
							Else
								While Eof(F) = False
									Banned$ = Trim$(ReadLine$(F))
									If Banned$ <> ""
										If Left$(Banned$, 1) <> ";"
											If Instr(Name$, Upper$(Banned$)) > 0 Then NameValid = False : Exit
										EndIf
									EndIf
								Wend
								CloseFile(F)
							EndIf

							; Check character name is not already in use
							If MySQL
								//NameValid = Not My_ActorExists(Name$)
							Else
								For AI.ActorInstance = Each ActorInstance
									If AI\RNID >= 0
										If Upper$(AI\Name$) = Name$
											NameValid = False
											Exit
										EndIf
									EndIf
								Next
							EndIf

							; Name was valid
							If NameValid = True
								; Find free slot
								FreeSlot = -1
								TotalChars = 0
								For i = 0 To 9
									If A\Character[i] = Null
										If FreeSlot = -1 Then FreeSlot = i
									Else
										TotalChars = TotalChars + 1
									EndIf
								Next

								; If we have a free slot and haven't exceeded the maximum allowed characters
								If FreeSlot > -1 And TotalChars < MaxAccountChars
									ActorID = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 2))
									; Validate ActorID before passing to
									; CreateActorInstance. The client controls
									; this 2-byte value, and ActorList() returns
									; Null for any race that doesn't exist (or
									; was deleted). CreateActorInstance previously
									; RuntimeError'd on Null, so any client could
									; crash the entire server with one crafted
									; P_CreateCharacter packet.
									If ActorID < 0 Or ActorID > 65535 Or ActorList(ActorID) = Null
										WriteLog(MainLog, "P_CreateCharacter: rejecting invalid ActorID " + ActorID + " from account '" + A\User$ + "'")
										RCE_Send(Host, M\FromID, P_CreateCharacter, "N", True)
										Exists = True : Exit
									EndIf
									A\QuestLog[FreeSlot] = New QuestLog
									A\ActionBar[FreeSlot] = New ActionBarData
									A\Character[FreeSlot] = CreateActorInstance(ActorList(ActorID))
									A\Character[FreeSlot]\Account = Handle(A)
									C.ActorInstance = A\Character[FreeSlot]
									C\Gender = RCE_IntFromStr(Mid$(M\MessageData$, Offset + 2, 1))
									C\FaceTex = RCE_IntFromStr(Mid$(M\MessageData$, Offset + 3, 1))
									C\Hair = RCE_IntFromStr(Mid$(M\MessageData$, Offset + 4, 1))
									C\Beard = RCE_IntFromStr(Mid$(M\MessageData$, Offset + 5, 1))
									C\BodyTex = RCE_IntFromStr(Mid$(M\MessageData$, Offset + 6, 1))
									; Bound the appearance indices at the receive
									; site. Wire bytes are 0..255 but the per-Actor
									; Face/Body/Hair/Beard ID arrays are Field [4].
									; Persisting out-of-range values is the long-
									; tail bug -- the client clamps on receipt
									; (PR #198) but storing the bad value means
									; every login round-trip re-emits it. Clamp
									; here so the persisted character has a valid
									; appearance from creation. Gender clamps to
									; 0/1; Face/Body/Hair/Beard clamp to 0..4.
									If C\Gender < 0 Or C\Gender > 1 Then C\Gender = 0
									If C\FaceTex < 0 Or C\FaceTex > 4 Then C\FaceTex = 0
									If C\Hair < 0 Or C\Hair > 4 Then C\Hair = 0
									If C\Beard < 0 Or C\Beard > 4 Then C\Beard = 0
									If C\BodyTex < 0 Or C\BodyTex > 4 Then C\BodyTex = 0
									C\Area$ = C\Actor\StartArea$
									C\Gold = StartGold
									C\Reputation = StartReputation
									Ar.Area = FindArea(C\Area$)
									If Ar = Null
										; Misconfigured race (StartArea references
										; a deleted area). Previously RuntimeError
										; here killed the entire server -- and with
										; it every other connected player -- the
										; moment any user tried to create a
										; character on a broken race. Reject the
										; creation cleanly and log instead.
										WriteLog(MainLog, "P_CreateCharacter: race '" + C\Actor\Race$ + "' StartArea '" + C\Area$ + "' not found, rejecting")
										FreeActorInstance(A\Character[FreeSlot])
										; Reject must leave the slot fully empty. FreeActorInstance does NOT
										; null A\Character[FreeSlot], so without this the slot is left dangling
										; -- the FreeSlot/TotalChars scan (2796/2799) then counts it as occupied,
										; and the P_GetCharacters list-send (2426) derefs the freed instance
										; (use-after-free). The QuestLog/ActionBar New'd in lockstep above also
										; leak (mirror of #447). Clear all three.
										If A\QuestLog[FreeSlot] <> Null Then Delete(A\QuestLog[FreeSlot])
										If A\ActionBar[FreeSlot] <> Null Then Delete(A\ActionBar[FreeSlot])
										A\Character[FreeSlot] = Null
										A\QuestLog[FreeSlot] = Null
										A\ActionBar[FreeSlot] = Null
										RCE_Send(Host, M\FromID, P_CreateCharacter, "N", True)
										Exists = True : Exit
									EndIf
									For i = 0 To 99
										If Upper$(Ar\PortalName$[i]) = Upper$(C\Actor\StartPortal$)
											C\LastPortal = i
											C\LastPortalArea = Handle(Ar)
											C\LastPortalAreaName$ = Ar\Name$
											C\X# = Ar\PortalX#[i]
											C\Y# = Ar\PortalY#[i]
											C\Z# = Ar\PortalZ#[i]
											Exit
										EndIf
									Next
									Offset = Offset + 7
									If AttributeAssignment > 0
										TotalAmount = 0
										For i = 0 To 39
											Amount = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))
											TotalAmount = TotalAmount + Amount
											C\Attributes\Value[i] = C\Attributes\Value[i] + Amount
											Offset = Offset + 1
										Next
										; Check for cheating
										If TotalAmount > AttributeAssignment
											FreeActorInstance(A\Character[FreeSlot])
										; Reject must leave the slot fully empty. FreeActorInstance does NOT
										; null A\Character[FreeSlot], so without this the slot is left dangling
										; -- the FreeSlot/TotalChars scan (2796/2799) then counts it as occupied,
										; and the P_GetCharacters list-send (2426) derefs the freed instance
										; (use-after-free). The QuestLog/ActionBar New'd in lockstep above also
										; leak (mirror of #447). Clear all three.
										If A\QuestLog[FreeSlot] <> Null Then Delete(A\QuestLog[FreeSlot])
										If A\ActionBar[FreeSlot] <> Null Then Delete(A\ActionBar[FreeSlot])
										A\Character[FreeSlot] = Null
										A\QuestLog[FreeSlot] = Null
										A\ActionBar[FreeSlot] = Null
									        RCE_Send(Host, M\FromID, P_CreateCharacter, "N", True)
											Exists = True : Exit
										EndIf
									EndIf
									C\Name$ = Right$(M\MessageData$, Len(M\MessageData$) - (Offset - 1))
									; If MySQL is enabled, then save individual account (fast)
									If MySQL = True
										; Similar to old command, however, takes no file/stream argument
										//My_NewActorInstance(C, A\QuestLog[FreeSlot], A\ActionBar[FreeSlot], False, A\My_ID)
									; Otherwise save all accounts
									Else
										SaveAccounts()
							        EndIf
							        LoginAttemptRecord(M\FromID, True)
							        RCE_Send(Host, M\FromID, P_CreateCharacter, "Y", True)
								; If there are no free slots, reply with failure
								Else
							        RCE_Send(Host, M\FromID, P_CreateCharacter, "N", True)
								EndIf
							; Character name invalid, tell client
							Else
						        RCE_Send(Host, M\FromID, P_CreateCharacter, "I", True)
							EndIf
						; If password is incorrect, reply with failure
						Else
					        LoginAttemptRecord(M\FromID, False)
					        RCE_Send(Host, M\FromID, P_CreateCharacter, "N", True)
						EndIf
						Exists = True
						Exit
					EndIf
				Next
		        ; If account was not found, return failure
		        ; Pay SHA-256 cost on no-account path so timing matches the
		        ; wrong-password path in the For loop (closes the timing oracle).
		        If Exists = False
		            Offset = 2 + UsernameLen
		            PwdLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))
		            VerifyPassword%("", Mid$(M\MessageData$, Offset + 1, PwdLen))
		            LoginAttemptRecord(M\FromID, False)
		            RCE_Send(Host, M\FromID, P_CreateCharacter, "N", True)
		        EndIf
				EndIf  ; close LoginAttemptOk Else

			; Request to delete an existing character
			Case P_DeleteCharacter ; :)
				; Throttle (see P_StartGame above for rationale).
				If Not LoginAttemptOk(M\FromID)
					RCE_Send(Host, M\FromID, P_DeleteCharacter, "N", True)
				Else
				UsernameLen = RCE_IntFromStr(Left$(M\MessageData$, 1))
				Username$ = Mid$(M\MessageData$, 2, UsernameLen)
				; Find account
				Exists = False
				For A.Account = Each Account
					If Upper$(A\User$) = Upper$(Username$)
						Offset = 2 + UsernameLen
						PwdLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))
						; If password is correct (PwdLen >= 1 + A\Pass$ <> ""
						; reject truncated packets and empty stored passwords)
						; AND the requester is the currently-logged-in session
						; for this account. A replayed P_DeleteCharacter would
						; otherwise let anyone with the hash permanently
						; destroy character slots on the account.
						If PwdLen >= 1 And A\Pass$ <> "" And VerifyPassword%(A\Pass$, Mid$(M\MessageData$, Offset + 1, PwdLen)) And RequesterOwnsAccountSession(A, M\FromID)
							Offset = Offset + 1 + PwdLen
							Number = Asc(Mid$(M\MessageData$, Offset, 1))
							If Number > -1 And Number < 10
								; Delete the character
								If A\QuestLog[Number] <> Null Then Delete A\QuestLog[Number]
								; Free the per-character ActionBar too: the shift loop below
								; overwrites A\ActionBar[Number] without releasing it, leaking one
								; ActionBarData (+ its 36 slot strings) per character deletion.
								; DeleteCharacter() only frees the ActorInstance, and the load
								; cleanup at AccountsServer.bb:366 frees QuestLog AND ActionBar
								; together -- restore that symmetry here. No double-free.
								If A\ActionBar[Number] <> Null Then Delete A\ActionBar[Number]
								If MySQL = True
									//My_DeleteCharacter(A, Number)
								Else
									DeleteCharacter(A, Number)
								EndIf
								For i = Number To 8
									A\Character[i] = A\Character[i + 1]
									A\QuestLog[i] = A\QuestLog[i + 1]
									A\ActionBar[i] = A\ActionBar[i + 1]
								Next
								A\Character[9] = Null
								A\QuestLog[9] = Null
								A\ActionBar[9] = Null

								; Send back new character list
								Pa$ = ""
								For i = 0 To 9
									If A\Character[i] <> Null
										Pa$ = Pa$ + RCE_StrFromInt$(Len(A\Character[i]\Name$), 1) + A\Character[i]\Name$
										Pa$ = Pa$ + RCE_StrFromInt$(A\Character[i]\Actor\ID, 2)
										Pa$ = Pa$ + RCE_StrFromInt$(A\Character[i]\Gender, 1)
										Pa$ = Pa$ + RCE_StrFromInt$(A\Character[i]\FaceTex, 1) + RCE_StrFromInt$(A\Character[i]\Hair, 1)
										Pa$ = Pa$ + RCE_StrFromInt$(A\Character[i]\Beard, 1)
										Pa$ = Pa$ + RCE_StrFromInt$(A\Character[i]\BodyTex, 1)
									EndIf
								Next
								LoginAttemptRecord(M\FromID, True)
								RCE_Send(Host, M\FromID, P_DeleteCharacter, Pa$, True)
							Else
						        RCE_Send(Host, M\FromID, P_DeleteCharacter, "N", True)
							EndIf
						; Otherwise return failure
						Else
					        LoginAttemptRecord(M\FromID, False)
					        RCE_Send(Host, M\FromID, P_DeleteCharacter, "N", True)
						EndIf
						Exists = True
						Exit
					EndIf
				Next
		        ; If account was not found, return failure
		        ; Pay SHA-256 cost on no-account path so timing matches the
		        ; wrong-password path in the For loop (closes the timing oracle).
		        If Exists = False
		            Offset = 2 + UsernameLen
		            PwdLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))
		            VerifyPassword%("", Mid$(M\MessageData$, Offset + 1, PwdLen))
		            LoginAttemptRecord(M\FromID, False)
		            RCE_Send(Host, M\FromID, P_DeleteCharacter, "N", True)
		        EndIf
				EndIf  ; close LoginAttemptOk Else

		End Select
		Delete M
	Next

End Function

; Updates other players in area on what an actor is wearing
Dim ActivateGubbins(5)
Function SendEquippedUpdate(A.ActorInstance)

	; Call script
	ThreadScript("Equip Change", "Main", Handle(A), 0)

	; Create packet with item IDs
	Pa$ = "O" + RCE_StrFromInt$(A\RuntimeID, 2)
	If A\Inventory\Items[SlotI_Weapon] <> Null
		Pa$ = Pa$ + RCE_StrFromInt$(A\Inventory\Items[SlotI_Weapon]\Item\ID, 2)
	Else
		Pa$ = Pa$ + RCE_StrFromInt$(65535, 2)
	EndIf
	If A\Inventory\Items[SlotI_Shield] <> Null
		Pa$ = Pa$ + RCE_StrFromInt$(A\Inventory\Items[SlotI_Shield]\Item\ID, 2)
	Else
		Pa$ = Pa$ + RCE_StrFromInt$(65535, 2)
	EndIf
	If A\Inventory\Items[SlotI_Chest] <> Null
		Pa$ = Pa$ + RCE_StrFromInt$(A\Inventory\Items[SlotI_Chest]\Item\ID, 2)
	Else
		Pa$ = Pa$ + RCE_StrFromInt$(65535, 2)
	EndIf
	If A\Inventory\Items[SlotI_Hat] <> Null
		Pa$ = Pa$ + RCE_StrFromInt$(A\Inventory\Items[SlotI_Hat]\Item\ID, 2)
	Else
		Pa$ = Pa$ + RCE_StrFromInt$(65535, 2)
	EndIf

	; Find out which gubbins to activate
	For i = 0 To 5 : ActivateGubbins(i) = False : Next
	For i = 0 To SlotI_Backpack - 1
		If A\Inventory\Items[i] <> Null
			For j = 0 To 5
				If A\Inventory\Items[i]\Item\Gubbins[j] = True Then ActivateGubbins(j) = True
			Next
		EndIf
	Next
	For i = 0 To 5
		Pa$ = Pa$ + RCE_StrFromInt$(ActivateGubbins(i), 1)
	Next

	; Send to all players in the same area. Skip if the actor's area
	; lookup is Null -- the equipment change still applies in-memory.
	AInstance.AreaInstance = Object.AreaInstance(A\ServerArea)
	If AInstance <> Null
		A2.ActorInstance = AInstance\FirstInZone
		While A2 <> Null
			If A2\RNID > 0
				If A2 <> A Then RCE_Send(Host, A2\RNID, P_InventoryUpdate, Pa$, True)
			EndIf
			A2 = A2\NextInZone
		Wend
	EndIf

End Function

; Updates a player on who is in his party
Function SendPartyUpdate(AI.ActorInstance)

	P.Party = Object.Party(AI\PartyID)
	If P <> Null
		For i = 0 To 7
			If P\Player[i] <> Null
				Pa$ = ""
				For j = 0 To 7
					If P\Player[j] <> Null And j <> i Then Pa$ = Pa$ + RCE_StrFromInt$(Len(P\Player[j]\Name$), 1) + P\Player[j]\Name$
				Next
				RCE_Send(Host, P\Player[i]\RNID, P_PartyUpdate, Pa$, True)
			EndIf
		Next
	EndIf

End Function