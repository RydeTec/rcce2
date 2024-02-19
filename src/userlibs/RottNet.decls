.lib " "

RN_Connect%(Host$, HostPort, MyPort, MyName$, MyData$)
RN_StartHost%(LocalPort, GameData$, MaximumPlayers)
RN_Disconnect(Connection)

RN_GameData$(Connection)

RN_Update()

RN_Send%(Connection, Destination, MessageType, MessageData$, ReliableFlag)
RN_GroupSend(Connection, Destination, MessageType, MessageData$, ReliableFlag)

RN_SetTimeoutPeriod(Seconds)
RN_ChangeMaximumPlayers(Connection, Max)

RN_StrFromInt$(Num, Length)
RN_StrFromFloat$(Num#)
RN_IntFromStr%(Dat$)
RN_FloatFromStr#(Dat$)
RN_StringsEqual%(String1$, String2$)

RN_KickPlayer(Connection, PlayerID)

RN_PlayerID(Connection)

RN_CreateGroup(Connection, Name$)
RN_RenameGroup(Connection, GroupID, Name$)
RN_DeleteGroup(Connection, GroupID)
RN_CountGroups(Connection)
RN_GroupName$(Connection, GroupID)
RN_SetPlayerGroup(Connection, GroupID, PlayerID)
RN_CountPlayersInGroup(Connection)
RN_GetPlayerGroup(Connection, PlayerID)

RN_PlayerIsLocal(Connection, PlayerID)
RN_PlayerName$(Connection, PlayerID)
RN_PlayerData$(Connection, PlayerID)

RN_BytesReceived(Connection)
RN_BytesSent(Connection)

RN_NextPlayerID%(Connection, PlayerID)
RN_NextGroupID%(Connection, GroupID)

RN_StartLog(Connection, File$, Append)
RN_StopLog(Connection)