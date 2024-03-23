#pragma once
#include <stdio.h>

#include <string>
#include <list>
#include <vector>
using namespace std;

#include "enet/enet.h"

#define Byte unsigned char

// A received message
struct RCE_Message
{
	Byte MessageType;			//Packet type
	vector<char> MessageData;	//Packet data
	int iSender;
	int connectionID;
};
