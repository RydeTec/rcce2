<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**GameServer.bb**

This module contains the following globals:  

*   [Game.GameWindow](#GGame)
*   [LastSpellRecharge](#GLastSpellRecharge)
*   [GameArea.Area](#GGameArea)
*   [LoginMessage$](#GLoginMessage)

This module contains the following types:  

*   [GameWindow](#TGameWindow)

This module contains the following functions:  

*   [CreateGameWindow](#FCreateGameWindow)
*   [GiveXP](#FGiveXP)
*   [KillActor](#FKillActor)
*   [FireProjectile](#FFireProjectile)
*   [ActorAttack](#FActorAttack)
*   [UpdateActorInstances](#FUpdateActorInstances)
*   [SetArea](#FSetArea)
*   [LeaveParty](#FLeaveParty)
*   [CommandPet](#FCommandPet)
*   [AILookForTargets](#FAILookForTargets)
*   [AICallForHelp](#FAICallForHelp)

  

* * *

  

**Game.GameWindow (global)**  
  
This global contains the object representing the server's Game window, and is set when the window is created.

  

**LastSpellRecharge (global)**  
  
This global stores the time at which the last spell recharge step occured. This is timed to happen ten times per second in the [UpdateActorInstances](#FUpdateActorInstances) function.

  

**GameArea.Area (global)**  
  
This global contains the [ServerAreas->Area](serverareas.md#TArea) object representing the zone selected for viewing in the server's Game window by the user.  

**LoginMessage$ (global)**  
  
This global stores the current login message displayed to players when entering the game.

  

* * *

  

**GaneWindow (type)**  
  
This type represents a server Game window. It contains the handles of the window and all child gadgets.

  

* * *

  
  
  

**CreateGameWindow.GameWindow()**  
  
Return value: Handle of the newly created GameWindow object  
  
Parameters: None  
  
This function creates a new GameWindow and all gadgets, then returns the handle.

  
  
  

**GiveXP(A.ActorInstance, XP, IgnoreParty)**  
  
Return value: None  
  
Parameters:  

*   _A.ActorInstance_ - The actor instance to give XP points to
*   _XP_ - The number of XP points to give
*   _IgnoreParty_ - True/False flag for whether to skip sharing XP points with the actor instance's party (defaults to False)

  
This function gives XP points to a character, automatically sharing them with his party if he has one and the IgnoreParty parameter is set to False. If the actor instance has a leader, XP points automatically go to the leader instead. The LevelUp script is also called and a network message sent to the client, if the actor instance is an online player.

  
  
  

**KillActor(A.ActorInstance, Killer.ActorInstance)**  
  
Return value: None  
  
Parameters:  

*   _A.ActorInstance_ - The actor instance to kill
*   _Killer.ActorInstance_ - The actor instance who killed the other

  
This function kills an actor instance. The Killer parameter may be null. If the killer is a valid actor instance, he will receive XP points and a reduced rating with the dead actor's faction if the setting is enabled. Any scripts waiting on a WaitKill command for this death are updated. If the killed actor instance was a player character, the player death script is called. If it was an NPC, any players in the same zone are informed of the death, the death script is called if present, and the actor instance is removed entirely from the game.

  
  
  

**FireProjectile(P.Projectile, A1.ActorInstance, A2.ActorInstance)**  
  
Return value: None  
  
Parameters:  

*   _P.Projectile_ - The projectile to fire
*   _A1.ActorInstance_ - The actor instance firing the projectile
*   _A2.ActorInstance_ - The target actor instance

  
This function fires a projectile from an actor instance to a target actor instance. It will only run if neither actor instance is a non-combatant, and the target actor instance's rating with the firing actor instance's home faction is 50% or lower. First all players in the zone are sent network messages to display the projectile. Next the damage is calculated and applied to the target. If the target is an NPC set to "defensive" aggressiveness, it is made angry. Finally, if the target actor instance's health attribute has dropped to 0 or below after the damage, the [KillActor](#FKillActor) function is called.

  
  
  

**ActorAttack(A1.ActorInstance, A2.ActorInstance)**  
  
Return value: Success flag  
  
Parameters:  

*   _A1.ActorInstance_ - The actor instance performing the attack
*   _A2.ActorInstance_ - The actor instance being attacked

  
This function makes an actor instance attack another actor instance. If the attacking actor is using a ranged weapon, the attack is diverted through [FireProjectile](#FFireProjectile). Otherwise range, faction ratings and aggressiveness settings are checked to make sure that the attack is valid. If so, either damage is calculated according to one of 3 formulae, or the Attack script is called to handle the attack. If the Attack script is not used, weapon and armour damage are applied, and players in the zone are sent a network message telling them to display the attack. Finally, any pets of the attacking actor instance are also set to attack the target, and the target is killed with the [KillActor](#FKillActor) function if its health has dropped to 0 or below.

  
  
  

**UpdateActorInstances(Broadcast)**  
  
Return value: None  
  
Parameters:  

*   _Broadcast_ - True/False flag on whether to broadcast a network update to all players

  
This function is responsible to all updates to actor instances such as movement, AI, actor effects, etc. The first section loops through every active actor effect and checks if the actor instance it relates to is dead or deleted, or if the effect has expired. In either case the effect is removed from the game. Next the function decides whether a recharge cycle for spells (abilities) is required this frame. If so, the Recharge variable is set to True to ensure that it happens later in the function. The next section loops through every actor instance in the game and performs various updates. The first is to check whether the actor instance has entered a portal or a trigger. This only applies to online player characters (i.e. those with a RottNet ID above 0), and since checking every portal and trigger takes some time, it only applies to actor instances in the current UpdateArea.  
  
Next, if the Recharge variable is True, recharge times for all memorised spells are reduced if above 0. Then the actor instance is moved towards its destination. Actor instances carrying a rider are merely moved to the same position as the rider, rather than moving in the usual way. The next step is to check if the actor instance is underwater. If so, any relevant water damage is applied. The last stage of the loop is to update AI if the actor is an NPC. The AI consists of various modes defined by the [Actors->AI\_...](actors.md#CAI) constants.  
  
The final section runs the broadcast, if the Broadcast parameter is True. This involves looping through every online player character, and sending a StandardUpdate packet for every other actor instance in the same zone.

  
  
  

**SetArea(A.ActorInstance, Ar.Area, Instance, Waypoint, Portal, X#, Y#, Z#)**  
  
Return value: None  
  
Parameters:  

*   _A.ActorInstance_ - The actor instance to warp
*   _Ar.Area_ - The [ServerAreas->Area](serverareas.md#TArea) to send the actor instance to
*   _Instance_ - The instance of the zone to warp to
*   _Waypoint_ - The waypoint within the new zone to start the actor instance at (default -1)
*   _Portal_ - The portal within the new zone to start the actor instance at (default 0)
*   _X#_ - The X position within the new zone to start the actor instance at (default 0)
*   _Y#_ - The Y position within the new zone to start the actor instance at (default 0)
*   _Z#_ - The Z position within the new zone to start the actor instance at (default 0)

  
This function warps an actor instance to any zone or zone instance. The existence of the requested instance is checked, and if it is not present instance #0 will be quietly used instead. If the actor instance being warped is riding a mount, the mount is warped first using recursion. The next thing the function does it to update the players list on the server's Game window, if the actor instance is a human player, and if his old or new zone is the selected [GameArea](#GGameArea). Next, if the actor instance is not being warped back to the same zone he is already in, he is removed from the linked list of characters in the old zone, and added to the linked list of characters in the new zone. Next the actor instance's position is updated, either to a waypoint, a portal, or direct to a specific position. The final section of the function deals with telling player characters in the old and new zone that the actor instance has left or entered, and, if the actor instance is human, sending a network message telling his client about the new zone.

  
  
  

**LeaveParty(AI.ActorInstance)**  
  
Return value: None  
  
Parameters:  

*   _AI.ActorInstance_ - The actor instance to remove from its party

  
This function removes an actor instance from its party, if it is a member of one. Any other players in the party will be sent a network message informing them of the event.

  
  
  

**CommandPet(AI.ActorInstance, Command$, Params$)**  
  
Return value: None  
  
Parameters:  

*   _AI.ActorInstance_ - The pet (slave) actor instance to command
*   _Command$_ - The command itself
*   _Params$_ - Additional data for the command if required

  
This function gives an instruction to an actor instance who is the pet of another actor instance. Valid commands are wait/stay, follow/come, attack and name. The name command also requires the new name to be passed in the Params$ parameter.

  
  
  

**AILookForTargets(AI.ActorInstance)**  
  
Return value: None  
  
Parameters:  

*   _AI.ActorInstance_ - The actor instance who should look for targets

  
This function causes an actor instance to search its immediate area for any valid targets to attack, and if found, to start chasing and attacking them. It is used several times in the [UpdateActorInstances](#FUpdateActorInstances) function.

  
  
  

**AICallForHelp(AI.ActorInstance)**  
  
Return value: None  
  
Parameters:  

*   _AI.ActorInstance_ - The actor instance who should 'call' for help

  
This function finds all other NPCs who have a very high faction rating (90%+) with the specified actor instance and, if they are within their aggression range and not already involved in combat, causes them to come to the aid of the specified actor instance by helping to attack its current target.