#include "main.h"
#include <windows.h>
#include <time.h>

//--------------------------------------------------------------------------------------
// Global variables
//--------------------------------------------------------------------------------------
std::vector<ENetHost*> Hosts;
std::vector<ENetPeer*> Peers;

int GetHostPeerIndex()
{
    static int LastPeerIndex = -1;
    ++LastPeerIndex;

    if (LastPeerIndex == 0)
    {
        ++LastPeerIndex;
        Hosts.push_back(NULL);
        Peers.push_back(NULL);
    }
    Hosts.push_back(NULL);
    Peers.push_back(NULL);

    return LastPeerIndex;
}

int LastDisconnectedPeer = -1;

list<RCE_Message*> lpMessages;
RCE_Message* pFirstMessage(NULL);

HANDLE hThread = 0;
CRITICAL_SECTION hCriticalSection;
bool CriticalSectionCreated = false;

// Removed debug macro and associated code

BBDECL int RCE_LastDisconnectedPeer()
{
    return LastDisconnectedPeer;
}

BBDECL int RCE_MoveToFirstMessage()
{
    bool bEmpty = lpMessages.empty();
    if (!bEmpty) pFirstMessage = lpMessages.front();
    else pFirstMessage = NULL;

    if (bEmpty) return 0;

    return 1;
}

BBDECL int RCE_AreMoreMessage()
{
    if (lpMessages.empty())
    {
        return 0;
    }

    delete lpMessages.front();
    lpMessages.pop_front();

    bool bEmpty = lpMessages.empty();
    if (!bEmpty) pFirstMessage = lpMessages.front();
    else pFirstMessage = NULL;

    if (bEmpty) return 0;
    return 1;
}

BBDECL int RCE_GetMessageConnection()
{
    return pFirstMessage->iSender;
}

BBDECL void RCE_GetMessageData(char* MessageData)
{
    if (!pFirstMessage->MessageData.empty())
        memcpy(MessageData, &pFirstMessage->MessageData[0], pFirstMessage->MessageData.size());
}

BBDECL int RCE_MessageLength()
{
    return pFirstMessage->MessageData.size();
}

BBDECL int RCE_GetMessageType()
{
    return pFirstMessage->MessageType;
}

BBDECL int RCE_GetConnectionID()
{
    return pFirstMessage->connectionID;
}

BBDECL int RCE_StartHost(int LocalPort, const char* GameData, int MaximumPlayers, const char* LogFile, bool Append)
{
    ENetAddress Address;
    ENetHost* pHost;

    enet_initialize();

    Address.host = ENET_HOST_ANY;
    Address.port = LocalPort;

    pHost = enet_host_create(&Address, MaximumPlayers, 0, 0, 0);
    if (pHost == NULL)
    {
        fprintf(stderr,
            "An error occurred while trying to create an ENet server host check your port or maximum players.\n");
        exit(EXIT_FAILURE);
    }
    if (!pHost) return 0;

    int Idx = GetHostPeerIndex();
    Hosts[Idx] = pHost;

    return Idx;
}

BBDECL int RCE_Connect(const char* HostName, int HostPort, int MyPort, const char* MyName, const char* MyData, const char* LogFile, bool Append)
{
    ENetAddress Address;
    ENetEvent Event;
    ENetHost* pHost;
    ENetPeer* pPeer;

    enet_initialize();

    pHost = enet_host_create(NULL, 1, 0, 0, 0);

    if (!pHost) return -1;

    enet_address_set_host(&Address, HostName);
    Address.port = HostPort;

    pPeer = enet_host_connect(pHost, &Address, 254, NULL);

    if (!pPeer) return -4;

    if (enet_host_service(pHost, &Event, 5000) > 0 && Event.type == ENET_EVENT_TYPE_CONNECT)
    {
        int Idx = GetHostPeerIndex();
        Hosts[Idx] = pHost;
        Peers[Idx] = pPeer;

        RCE_FSend((int)Peers[Idx], 0, 0, true, 0); // NewClient

        return (int)pPeer;
    }

    return -2;
}

BBDECL void RCE_Disconnect(int peerHandle)
{
    int index = 0;

    for (int i = 0; i < Peers.size(); ++i)
    {
        if ((int)Peers[i] == peerHandle)
        {
            index = i;
            break;
        }
    }

    ENetHost* pHost = Hosts[index];
    ENetPeer* pPeer = Peers[index];

    if (!pHost) return;

    if (pPeer)
    {
        ENetEvent Event;
        enet_peer_disconnect(pPeer, 0);

        while (enet_host_service(pHost, &Event, 0) > 0)
        {
            switch (Event.type)
            {
            case ENET_EVENT_TYPE_RECEIVE:
                enet_packet_destroy(Event.packet);
                break;

            case ENET_EVENT_TYPE_DISCONNECT:;
            }
        }

        enet_peer_reset(pPeer);
        enet_host_destroy(pHost);
        pHost = NULL;
        pPeer = NULL;
    }
    else
    {
        enet_host_destroy(pHost);
        pHost = NULL;
        pPeer = NULL;
    }

    Hosts[index] = NULL;
    Peers[index] = NULL;
}

BBDECL void RCE_Update()
{
    ENetEvent Event;
    for (int i = 0; i < Hosts.size(); ++i)
    {
        ENetHost* pHost = Hosts[i];
        ENetPeer* pPeer = Peers[i];

        if (pHost == NULL)
            continue;

        while (enet_host_service(pHost, &Event, 0) > 0)
        {
            switch (Event.type)
            {
            case ENET_EVENT_TYPE_CONNECT:
                break;

            case ENET_EVENT_TYPE_RECEIVE:
            {
                RCE_Message* pMessage = new RCE_Message;
                pMessage->MessageType = Event.packet->data[0];
                pMessage->MessageData.resize(Event.packet->dataLength - 1);
                if (Event.packet->dataLength > 1)
                    memcpy(&pMessage->MessageData[0], &Event.packet->data[1], Event.packet->dataLength - 1);
                pMessage->iSender = (int)Event.peer;
                pMessage->connectionID = i;
                lpMessages.push_back(pMessage);

                enet_packet_destroy(Event.packet);
            }

            case ENET_EVENT_TYPE_DISCONNECT:
                LastDisconnectedPeer = (int)Event.peer;
                RCE_FSend(0, 201, (const char*)&LastDisconnectedPeer, true, 4);
                lpMessages.back()->connectionID = i;
            }
        }

        enet_host_flush(pHost);
    }
}

BBDECL void RCE_FSend(int Destination, int MessageType, const char* MessageData, int ReliableFlag, int mLength)
{
    if (Destination == 0)
    {
        RCE_Message* pMessage = new RCE_Message;
        pMessage->MessageType = MessageType;
        pMessage->MessageData.resize(mLength);
        if (mLength > 0)
            memcpy(&pMessage->MessageData[0], MessageData, mLength);

        pMessage->iSender = 0;
        pMessage->connectionID = 0;

        lpMessages.push_back(pMessage);
        return;
    }

    ENetPeer* pDestPeer = (ENetPeer*)Destination;
    Byte* Data = new Byte[mLength + 1];
    Data[0] = MessageType;
    if (mLength > 0)
        memcpy(&Data[1], MessageData, mLength);

    ENetPacket* packet = enet_packet_create(Data, mLength + 1, (ReliableFlag ? ENET_PACKET_FLAG_RELIABLE : 0));

    if (ReliableFlag) enet_peer_send(pDestPeer, 1, packet);
    else enet_peer_send(pDestPeer, 2, packet);

    delete Data;
}