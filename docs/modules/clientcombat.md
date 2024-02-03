<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**ClientCombat.bb**

This module contains the following globals:  

*   [LastAttack](#GLastAttack)
*   [AttackTarget](#GAttackTarget)
*   [CombatDelay](#GCombatDelay)
*   [DamageInfoStyle](#GDamageInfoStyle)

This module contains the following types:  

*   [BloodSpurt](#TBloodSpurt)
*   [FloatingNumber](#TFloatingNumber)

This module contains the following functions:  

*   [UpdateCombat](#FUpdateCombat)
*   [LoadCombat](#FLoadCombat)
*   [AnimateActorAttack](#FAnimateActorAttack)
*   [AnimateActorParry](#FAnimateActorParry)
*   [CombatDamageOutput](#FCombatDamageOutput)
*   [CreateFloatingNumber](#FCreateFloatingNumber)
*   [UpdateFloatingNumbers](#FUpdateFloatingNumbers)

  

* * *

  

**LastAttack (global)**  
  
This global stores the moment (from Blitz's MilliSecs command) at which the player character last performed an attack. It is used to ensure that an attack is not attempted sooner after a previous attack than allowed by the game.

  

**AttackTarget (global)**  
  
This global is set by the [Interface3D](interface3d.md) module to either True or False, depending on whether the player is trying to attack his target. If set to True, attacks will be attempted by [UpdateCombat](#FUpdateCombat).

  

**CombatDelay (global)**  
  
This global stores the minimum time in milliseconds between attacks. It is set once and not changed during the game.

  

**DamageInfoStyle (global)**  
  
This global stores the style in which combat damage should be displayed to the player. It is set once and not changed during the game.

  

* * *

  

**BloodSpurt (type)**  
  
This type represents a blood spurt emitter, created whenever an actor is damaged in an attack. It stores the entity handle of the emitter and the time at which it was created.

  

**FloatingNumber (type)**  
  
This type represents a floating number. It stores the entity handle for the text and the amount of lifespan used by the floating number so far. For more information see [CreateFloatingNumber](#FCreateFloatingNumber).

  

* * *

  
  
  

**UpdateCombat()**  
  
Return value: None  
  
Parameters: None  
  
This function updates combat for the player's actor instance. It checks if the player has a valid target and is able to attack (e.g. in range, not mounted), and handles the attack if one can be made. It loops through all blood spurt emitters current active and removes them once they have been active for a certain length of time, currently 600 milliseconds. It is called repeatedly from the client's main loop.

  
  
  

**LoadCombat()**  
  
Return value: None  
  
Parameters: None  
  
This function loads all combat related data at the start of the game. First it reads in settings from Combat.dat and assigns them to the approriate globals, then sets the time of the last attack to a default value. Finally it cycles through each actor, replacing the texture ID for blood with the handle of a loaded [RottParticles](rottparticles.md) blood emitter configuration. It is called once from [ClientLoaders->LoadGame](clientloaders.md#FLoadGame).

  
  
  

**AnimateActorAttack(A.ActorInstance)**  
  
Return value: None  
  
Parameters:  

*   _A.ActorInstance_ - The actor instance who is attacking

  
This function runs the appropriate attack animation for an actor instance. The animation chosen depends on what type of weapon, if any, the actor instance has equipped.

  
  
  

**AnimateActorParry(A.ActorInstance)**  
  
Return value: None  
  
Parameters:  

*   _A.ActorInstance_ - The actor instance who is parrying

  
This function runs the appropriate parry animation for an actor instance. The animation chosen depends on what type of weapon and shield, if any, the actor instance has equipped.

  
  
  

**CombatDamageOutput(AI.ActorInstance, Amount, DType$)**  
  
Return value: None  
  
Parameters:  

*   _AI.ActorInstance_ - The actor instance who has been damaged
*   _Amount_ - The amount of damage applied
*   _DType$_ - The name of the relevant damage type

  
This function gives an onscreen display when an actor is damaged in a fight. It uses the [DamageInfoStyle](#GDamageInfoStyle) global to choose which type of output to give, based on the setting in GE. For a DamageInfoStyle value of 2, the damage is displayed as a chat message using [Interface3D->Output](interface3d.bb#FOutput). For a DamageInfoStyle value of 3, it is displayed as a floating number using [CreateFloatingNumber](#FCreatFloatingNumber). Other values result in nothing happening.

  
  
  

**CreateFloatingNumber(AI.ActorInstance, Amount, R, G, B)**  
  
Return value: None  
  
Parameters:  

*   _AI.ActorInstance_ - The actor instance whom the number will appear above
*   _Amount_ - The number to display
*   _R_ - The red value of the colour
*   _G_ - The green value of the colour
*   _B_ - The blue value of the colour

  
This function creates a "floating number" -- coloured text above a character's head, usually representing an amount of damage which that character has received. The text is rendered using the [Gooey\_3D\_Text](gooey_3d_text.md) module.

  
  
  

**UpdateFloatingNumbers()**  
  
Return value: None  
  
Parameters: None  
  
This function loops through and updates all active floating numbers. It will move them upwards at a constant speed and remove them once their lifespan has expired.