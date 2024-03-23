<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**Packets.bb**

This module contains the following constants:  

*   [P\_...](#CP)

  

* * *

  

**P\_... (constant)**  
  
This list of constants gives IDs for each type of network packet sent or received within the engine. Packet uses are as follows:  

*   P\_CreateAccount - Request from the client to create a new account
*   P\_VerifyAccount - Request from the client to verify an account username/password and return available characters
*   P\_FetchCharacter - Request from the client to retrieve full information for a particular character
*   P\_CreateCharacter - Request from the client to create a new character
*   P\_DeleteCharacter - Request from the client to delete an existing character
*   P\_ChangePassword - Request from the client to change the password on an account (not implemented)
*   P\_FetchActors - Request from the client to retrieve details of all actors in the game (also retrieves items etc.)
*   P\_FetchItems - Request from the client to retrieve details of all items in the game (unused)
*   P\_ChangeArea - Request from the client to move to a different zone
*   P\_FetchUpdateFiles - Request from the client for a list of the latest game data files
*   P\_NewActor - Sent to the client whenever a new actor instance enters the zone
*   P\_StartGame - Request from the client to enter the game with a specified character
*   P\_ActorGone - Sent to the client whenever an actor instance leaves the zone
*   P\_StandardUpdate - Standard actor instance update packet, sent in both directions
*   P\_InventoryUpdate - Item added to/removed from inventory, or being picked up/dropped, sent in both directions
*   P\_ChatMessage - Sent to the client to display chat text, usually either from another player or a script
*   P\_WeatherChange - Sent to the client whenever the current weather for the zone changes
*   P\_AttackActor - Sent to the server to request an attack against a target, or to the client to display an attack between any two actor instances
*   P\_ActorDead - Sent to the client when an actor dies but does not leave the zone (only used for NPC death)
*   P\_RightClick - Sent to the server when the player attempts to interact with an actor instance
*   P\_Dialog - Dialog creation/deletion/update instruction sent to the client, or a reply sent back to the server
*   P\_StatUpdate - Sent to the client with a new value for one of the player's attributes
*   P\_QuestLog - Sent to the client when the player's quest log is updated
*   P\_GoldChange - Sent to the client to update the player's money
*   P\_NameChange - Sent to the client to update any character's name
*   P\_KnownSpellUpdate - Sent to the client when the player learns or loses an ability
*   P\_SpellUpdate - Sent to the server to request memorisation or firing of an ability
*   P\_CreateEmitter - Sent to the client to create an emitter, usually from a script
*   P\_Sound - Sent to the client to play a sound, usually from a script
*   P\_AnimateActor - Sent to the client to play an animation on any character
*   P\_ActionBarUpdate - Sent to the client at login with the current action bar contents, and to the server when they change during play
*   P\_XPUpdate - Sent to the client when the player's XP level changes
*   P\_ScreenFlash - Sent to the client to display a fullscreen effect
*   P\_Music - Sent to the client to play a music file, usually from a script
*   P\_OpenTrading - Sent to the client to initiate trade mode, and back to the server to signal completion
*   P\_ActorEffect - Sent to the client when an actor effect is added, removed or updated
*   P\_Projectile - Sent to the client when a new projectile is created
*   P\_PartyUpdate - Sent to the client when the members of the player's party change
*   P\_AppearanceUpdate - Sent to the client when a character changes clothes, face etc.
*   P\_CloseTrading - Sent to the client when trading mode is completed (player-player trading)
*   P\_UpdateTrading - Sent to the client when trading mode is updated (player-player trading)
*   P\_SelectScenery - Sent to the server to request 'trading' with an ownable scenery object
*   P\_ItemScript - Sent to the server when a player attempts to use an item instance
*   P\_EatItem - Sent to the server when a player attempts to eat an item instance
*   P\_ItemHealth - Sent to the client when the health of an item instance changes
*   P\_Jump - Sent to the server when the player jumps, and from the server to all clients when any character in the zone jumps
*   P\_Dismount - Sent to the server to request to dismount from a rideable actor instance
*   P\_FloatingNumber - Sent to the client to create a new [ClientCombat->FloatingNumber](clientcombat.md#TFloatingNumber)
*   P\_RepositionActor - Sent to the client when an actor teleports to a new spot, usually from a script
*   P\_Speech - Sent to the client to play an actor speech sound effect
*   P\_ProgressBar - Sent to the client when a progress bar is created, deleted or updated
*   P\_BubbleMessage - Sent to the client to create a new [Interface->Bubble](interface.md#TBubble)
*   P\_ScriptInput - Sent to the client when a text input dialog is created, and back to the server with the player's text