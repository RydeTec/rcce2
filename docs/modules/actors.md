<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**Actors.bb**

This module contains the following constants:  

*   [AI\_...](#CAI)
*   [Speech\_...](#CSpeech)
*   [Environment\_...](#CEnvironment)

This module contains the following globals:  

*   [ActorList.Actor(65535)](#GActorList)
*   [RuntimeIDList.ActorInstance(65535)](#GRuntimeIDList)
*   [LastRuntimeID](#GLastRuntimeID)
*   [AttributeAssignment](#GAttributeAssignment)
*   [AttributeNames$(39)](#GAttributeNames)
*   [AttributeIsSkill(39)](#GAttributeIsSkill)
*   [AttributeHidden(39)](#GAttributeHidden)
*   [FactionNames$(99)](#GFactionNames)
*   [FactionDefaultRatings(99, 99)](#GFactionDefaultRatings)

This module contains the following types:  

*   [Actor](#TActor)
*   [ActorInstance](#TActorInstance)
*   [Party](#TParty)
*   [QuestLog](#TQuestLog)
*   [Attributes](#TAttributes)
*   [ActorEffect](#TActorEffect)

This module contains the following functions:  

*   [FindActorInstanceFromRNID](#FFindActorInstanceFromRNID)
*   [FindActorInstanceFromName](#FFindActorInstanceFromName)
*   [FindPlayerFromName](#FFindPlayerFromName)
*   [WriteActorInstance](#FWriteActorInstance)
*   [ReadActorInstance](#FReadActorInstance)
*   [CreateActor](#FCreateActor)
*   [CreateActorInstance](#FCreateActorInstance)
*   [FreeActorInstance](#FFreeActorInstance)
*   [FreeActorInstanceSlaves](#FFreeActorInstanceSlaves)
*   [ActorHasFace](#FActorHasFace)
*   [ActorHasHair](#FActorHasHair)
*   [ActorHasBeard](#FActorHasBeard)
*   [ActorHasMultipleTextures](#FActorHasMultipleTextures)
*   [LoadActors](#FLoadActors)
*   [SaveActors](#FSaveActors)
*   [LoadAttributes](#FLoadAttributes)
*   [SaveAttributes](#FSaveAttributes)
*   [FindAttribute](#FFindAttribute)
*   [ActorInstanceToString](#FActorInstanceToString)
*   [ActorInstanceFromString](#FActorInstanceFromString)
*   [GetFlag](#FGetFlag)
*   [CountQuests](#FCountQuests)
*   [LoadFactions](#FLoadFactions)
*   [SaveFactions](#FSaveFactions)
*   [AddSpell](#FAddSpell)
*   [DeleteSpell](#FDeleteSpell)

  

* * *

  

**AI\_... (constant)**  
  
This list of constants specifies AI states for NPC actor instances.

  

**Speech\_... (constant)**  
  
This list of constants specifies IDs for actor speech sounds.

  

**Environment\_... (constant)**  
  
This list of constants specifies the available actor environment types.

  

* * *

  

**ActorList.Actor(65535) (global)**  
  
This global array indexes every Actor object, with the array index being the ID for that object. It thus provides fast non-sequential access to any Actor object.

  

**RuntimeIDList.ActorInstance(65535) (global)**  
  
This global array indexes every ActorInstance object, with the array index being the RuntimeID for that object. It thus provides fast non-sequential access to any ActorInstance object.

  

**LastRuntimeID (global)**  
  
This global stores the last RuntimeID assigned to an actor instance, and is used when creating any new actor instance on the server. Running a search for the first free ID while the server is running the game would be too slow, so this system is used instead which will simply use all IDs up to 65535 before cycling back to 0.

  

**AttributeAssignment (global)**  
  
This global stores the number of attribute points a player is allowed to spend when creating a new character. It is loaded by the server and sent to each client at login.

  

**AttributeNames$(39) (global)**  
  
This global array stores the name of each attribute in the game. An empty string means that an attribute is not used. It is loaded by the server and sent to each client at login.

  

**AttributeIsSkill(39) (global)**  
  
This global array stores flags for whether each attribute in the game is actually a skill rather than a stat. It is loaded by the server and sent to each client at login.

  

**AttributeHidden(39) (global)**  
  
This global array stores flags for whether each attribute in the game is invisible to players. It is loaded by the server and sent to each client at login.

  

**FactionNames$(99) (global)**  
  
This global array stores the name of each faction in the game. An empty string means that a faction is not used. It is loaded by the server and sent to each client at login.

  

**FactionDefaultRatings(99, 99) (global)**  
  
This global array stores the default faction ratings between every faction and every other faction. It is used to set initial rating values when creating a new actor instance, and can also be accessed from scripts.

  

* * *

  

**Actor (type)**  
  
This type represents an actor. An actor is not an actual character in the game, but just a template for characters (actor instances).

  

**ActorInstance (type)**  
  
This type represents an instance of an actor, meaning an actual character (whether player or AI controlled) in the game. It stores all character-specific settings such as name, position, faction ratings, attributes, and many others.

  

**Party (type)**  
  
This type represents a party of player characters. It stores the total number of members and the ActorInstance object for each. A party can hold up to 8 player characters.

  

**QuestLog (type)**  
  
This type represents the quest log of a player character. It stores the name and status strings for up to 500 quests. It also stores an ID for each quest for MySQL use only.

  

**Attributes (type)**  
  
This type represents a set of attributes, used by many things including actors, actor instances, items and actor effects. It stores a value, maximum value, and MySQL ID for each attribute.

  

**ActorEffect (type)**  
  
This type represents an actor effect, or buff, which is active on an actor instance. These can be created through using ingredients or potions, or from the AddActorEffect scripting command.

  

* * *

  
  
  

**FindActorInstanceFromRNID.ActorInstance(RNID)**  
  
Return value: The actor instance found, if any  
  
Parameters:  

*   _RNID_ - The RottNet ID to search for

  
This function searches through all actor instances to find one with a specific RottNet ID, and if present returns its handle. No two actor instances may have the same RottNet ID, unless it is -1 or 0 (meaning NPC or offline, respectively).

  
  
  

**FindActorInstanceFromName.ActorInstance(Name$)**  
  
Return value: The actor instance found, if any  
  
Parameters:  

*   _Name$_ - The name to search for

  
This function searches through all actor instances to find one with a specific name, and if found returns its handle. The search is not case sensitive.

  
  
  

**FindPlayerFromName.ActorInstance(Name$)**  
  
Return value: The actor instance found, if any  
  
Parameters:  

*   _Name$_ - The name to search for

  
This function searches through all player character actor instances to find one with a specific name, and if found returns its handle. The search is not case sensitive.

  
  
  

**WriteActorInstance(Stream, A.ActorInstance)**  
  
Return value: None  
  
Parameters:  

*   _Stream_ - The stream to write the actor instance to
*   _A.ActorInstance_ - The actor instance to write

  
This function writes all data describing an actor instance to an open stream (usually a file). Slaves are also written, using recursion. It is used by the server to save player characters to the Accounts.dat file. Written actor instances may be read back in using [ReadActorInstance](#FReadActorInstance).

  
  
  

**ReadActorInstance.ActorInstance(Stream)**  
  
Return value: The actor instance loaded, if any  
  
Parameters:  

*   _Stream_ - The stream to read the actor instance from

  
This function reads an actor instance previously written using [WriteActorInstance](#FWriteActorInstance) from an open stream (usually a file). Slaves are also read in, using recursion. It is used by the server to load player characters from the Accounts.dat file.

  
  
  

**CreateActor.Actor()**  
  
Return value: The newly created actor  
  
Parameters: None  
  
This function creates a new Actor object, sets all required default values, and returns it. If a free actor ID was not found, it returns null. This should **always** be used in preference to creating an actor manually.

  
  
  

**CreateActorInstance.ActorInstance(Actor.Actor)**  
  
Return value: The newly created actor instance  
  
Parameters:  

*   _Actor.Actor_ - The actor to create an instance of

  
This function creates a new ActorInstance object, sets all required default values, and returns it. This should **always** be used in preference to creating an actor instance manually. It does not set a RuntimeID for the new actor instance.

  
  
  

**FreeActorInstance(A.ActorInstance)**  
  
Return value: None  
  
Parameters:  

*   _A.ActorInstance_ - The actor instance to free

  
This function frees an existing actor instance. On the client, SafeFreeActorInstance should usually be used instead. On the server, this function is safe to call directly. This should **always** be used in preference to freeing an actor instance manually.

  
  
  

**FreeActorInstanceSlaves(A.ActorInstance)**  
  
Return value: None  
  
Parameters:  

*   _A.ActorInstance_ - The actor instance to free the slaves of

  
This function frees any and all slaves of an existing actor instance. Do not call this function on the client.

  
  
  

**ActorHasFace(A.Actor, Gender)**  
  
Return value: True or False flag  
  
Parameters:  

*   _A.Actor_ - The actor to check
*   _Gender_ - The gender to use for the check (default value 0)

  
This function returns True if the specified actor has any face textures set, or False if not. It can check for the male, female, or both genders by setting the Gender parameter to 1, 2 or 0 respectively. It is used by the client to choose a texturing method in [Actors3D->LoadActorInstance3D](actors3d.md#FLoadActorInstance3D).

  
  
  

**ActorHasHair(A.Actor, Gender)**  
  
Return value: True or False flag  
  
Parameters:  

*   _A.Actor_ - The actor to check
*   _Gender_ - The gender to use for the check (default value 0)

  
This function returns True if the specified actor has any hair meshes set, or False if not. It can check for the male, female, or both genders by setting the Gender parameter to 1, 2 or 0 respectively. It is used by the client to choose whether to grey out hair selection buttons in the character creation screen (see the [MainMenu](mainmenu.md) module.

  
  
  

**ActorHasBeard(A.Actor)**  
  
Return value: True or False flag  
  
Parameters:  

*   _A.Actor_ - The actor to check

  
This function returns True if the specified actor has any beard meshes set, or False if not. It is used by the client to choose whether to grey out beard selection buttons in the character creation screen (see the [MainMenu](mainmenu.md) module.

  
  
  

**ActorHasMultipleTextures(A.Actor, Gender)**  
  
Return value: True or False flag  
  
Parameters:  

*   _A.Actor_ - The actor to check
*   _Gender_ - The gender to use for the check (default value 0)

  
This function returns True if the specified actor has more than one texture available, i.e. two or more body textures, or at least one face texture (a minimum of one body texture is mandatory).

  
  
  

**LoadActors(Filename$)**  
  
Return value: The total number of actors loaded  
  
Parameters:  

*   _Filename$_ - The path/file to load actors from

  
This function loads a set of actors from a file. If loading failed, -1 is returned.

  
  
  

**SaveActors(Filename$)**  
  
Return value: Success flag  
  
Parameters:  

*   _Filename$_ - The path/file to save actors to

  
This function saves a set of actors to a file. If saving failed, False is returned.

  
  
  

**LoadAttributes(Filename$)**  
  
Return value: Success flag  
  
Parameters:  

*   _Filename$_ - The path/file to load attributes from

  
This function loads all attribute settings from a file. If loading failed, False is returned.

  
  
  

**SaveAttributes(Filename$)**  
  
Return value: Success flag  
  
Parameters:  

*   _Filename$_ - The path/file to save attributes to

  
This function saves all attribute settings from a file. If saving failed, False is returned.

  
  
  

**FindAttribute(Name$)**  
  
Return value: Attribute number  
  
Parameters:  

*   _Name$_ - The attribute to search for

  
This function finds the number of an attribute from its name. If no such attribute exists, -1 is returned. The search is not case sensitive.

  
  
  

**ActorInstanceToString$(A.ActorInstance)**  
  
Return value: String representation of the actor instance  
  
Parameters:  

*   _A.ActorInstance_ - The actor instance to encode

  
This function encodes information about an actor instance in the form of a string, which can then be restored to an actor instance object by [ActorInstanceFromString](#FActorInstanceFromString). This is useful for network transmission of an actor instance.

  
  
  

**ActorInstanceFromString.ActorInstance(Pa$)**  
  
Return value: New actor instance  
  
Parameters:  

*   _Pa$_ - String representation of an actor instance

  
This function decodes information about an actor instance from a string previously encoded with [ActorInstanceToString](#FActorInstanceToString), and returns the newly created actor instance object. This is useful for network transmission of an actor instance.

  
  
  

**GetFlag(TheInt, Flag)**  
  
Return value: True/False flag  
  
Parameters:  

*   _TheInt_ - Any valid integer
*   _Flag_ - Number of bit to retrieve

  
This function retrieves the value of a single bit from an integer. The Flag parameter is the number of the bit, where 0 is the least significant bit of the integer.

  
  
  

**CountQuests(Q.QuestLog)**  
  
Return value: Number of quests in log  
  
Parameters:  

*   _Q.QuestLog_ - QuestLog to count the entries of

  
This function returns the total number of entries in a quest log.

  
  
  

**LoadFactions(Filename$)**  
  
Return value: Total number of factions loaded  
  
Parameters:  

*   _Filename$_ - The path/file to load factions from

  
This function loads all factions and their default ratings from a file. If loading failed, -1 is returned.

  
  
  

**SaveFactions(Filename$)**  
  
Return value: Success flag  
  
Parameters:  

*   _Filename$_ - The path/file to save factions to

  
This function saves all factions and their default ratings to a file. If saving failed, False is returned.

  
  
  

**AddSpell(AI.ActorInstance, SpellID, Lvl)**  
  
Return value: None  
  
Parameters:  

*   _AI.ActorInstance_ - The actor instance to give the spell to
*   _SpellID_ - The ID of the spell to add
*   _Lvl_ - The initial level of the spell (defaults to 1)

  
This function gives a new spell (ability) to an actor instance. If the actor instance is an online player, a network message is sent to inform the client of the new spell. It should only ever be called from the server. Note that the new spell will not be memorised by the actor instance, merely known.

  
  
  

**DeleteSpell(AI.ActorInstance, ID)**  
  
Return value: None  
  
Parameters:  

*   _AI.ActorInstance_ - The actor instance to remove the spell from
*   _ID_ - The ID of the known spell to remove

  
This function removes a known spell (ability) from an actor instance. If the actor instance is an online player, a network message is sent to inform the client of the removal. It should only ever be called from the server. If the deleted spell was memorised, it is unmemorised and the slot made blank.