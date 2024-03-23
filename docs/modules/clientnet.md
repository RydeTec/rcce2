<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**ClientNet.bb**

This module contains the following globals:  

*   [TradePackets](#GTradePackets)
*   [TradeMsg1$, TradeMsg2$, TradeMsg3$](#GTradeMsg1)

This module contains the following functions:  

*   [Connect](#FConnect)
*   [UpdateNetwork](#FUpdateNetwork)

  

* * *

  

**TradePackets (global)**  
  
This global variable is used to temporarily store the total amount of packets of a trade message received so far. This allows the [UpdateNetwork](#UpdateNetwork) function to track the number of packets received even though they do not necessarily arrive at once or in order.

  

**TradeMsg1$, TradeMsg2$, TradeMsg3$ (global)**  
  
These global variables are used to temporarily store the data from the packets of a trade message received so far.  

* * *

  
  
  

**Connect()**  
  
Return value: None  
  
Parameters: None  
  
This function attempts to open a RottNet connection with the server. If this succeeds, it then waits for the replies from the server containing the player's action bar data and the runtime ID of the player character's actor instance. When contacting the server, the SelectedCharacter global variable -- the value of which is set in [MainMenu->CharSelect](mainmenu.md#FCharSelect) -- is used to tell the server which character is being used.

  
  
  

**UpdateNetwork()**  
  
Return value: None  
  
Parameters: None  
  
This function handles all network communication updates for the client. It may seem very long and complicated, but is actually very simple. It is divided into two parts. The first, which makes up the bulk of the function, loops through each network message received, taking the appropriate action at each one. The second, which is the small section right at the end of the function, sends periodic update packets back to the server with the player's position, destination, running state and walking backwards state. The first looping section uses a Select/Case structure to process different types of network message. Each type is defined by a constant from the [Packets](packets.md) module. Refer to the documentation on the Packets module for more detailed information on each type of message.