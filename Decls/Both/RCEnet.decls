.lib "RCEnet.dll"

RCE_StartHost%(LocalPort%, GameData$, MaximumPlayers%, LogFile$, Append%)
RCE_Connect%(HostName$, HostPort%, MyPort%, MyName$, MyData$, LogFile$, Append%)
RCE_Disconnect()

RCE_FSend(Destination%, MessageType%, MessageData$, ReliableFlag%, Length%)
RCE_Update()

RCE_MoveToFirstMessage%()
RCE_AreMoreMessage%()

RCE_GetMessageConnection%()
RCE_GetMessageType%()
RCE_GetMessageData(MessageData*)
RCE_MessageLength%()

RCE_LastDisconnectedPeer%()