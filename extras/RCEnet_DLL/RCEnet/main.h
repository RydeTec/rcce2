#pragma once
#include "RCEnetTypes.h"

#define BBDECL extern "C" __declspec(dllexport)

// Server & Client Connection
BBDECL int	 RCE_StartHost(int LocalPort,const char* GameData, int MaximumPlayers, const char* LogFile, bool Append);
BBDECL int   RCE_Connect(const char* HostName, int HostPort, int MyPort, const char* MyName, const char* MyData, const char* LogFile, bool Append);
BBDECL void	 RCE_Disconnect(int index);

BBDECL void RCE_Update();
BBDECL void RCE_FSend(int Destination, int MessageType, const char* MessageData, int ReliableFlag, int mLength);

// Auxiliary Functions
BBDECL int RCE_MoveToFirstMessage();
BBDECL int RCE_AreMoreMessage();

BBDECL int RCE_GetMessageConnection();
BBDECL int RCE_GetMessageType();
BBDECL void RCE_GetMessageData(char *MessageData);
BBDECL int RCE_MessageLength();

BBDECL int RCE_LastDisconnectedPeer();
