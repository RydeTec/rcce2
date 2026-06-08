<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**Actors.bb**

The actor-data substrate. Holds the `Actor` (template) and `ActorInstance` (live entity) Types, the global registries (`ActorList[]`, `RuntimeIDList[]`), the chain-walk infrastructure for high-traffic broadcast paths (`FirstOnlinePlayer`, `FirstSlave`, `FirstInZone`), the canonical lifecycle helpers (`CreateActorInstance`, `FreeActorInstance`, `SafeFreeActorInstance`), the 40-attribute schema, the AI-mode constants, and the 100-slot faction system.

[`GameServer.bb`](gameserver.md) owns the per-tick simulation that consumes this data; [`Server.bb`](../../src/Server.bb) hosts the `Update*` broadcast helpers (`UpdateAttribute` / `UpdateAttributeMax` / `UpdateReputation`) that emit `P_StatUpdate` from this data; [`ServerAreas.bb`](serverareas.md) owns `Area` / `AreaInstance` (which holds the per-Area `FirstInZone` chain head).

This page interleaves a **modern conceptual overview** with the legacy function-by-function reference below.

## Conceptual overview

### Actor vs. ActorInstance — the key Type distinction

This is the most confused thing about the codebase for new contributors:

- **`Actor`** (Type) — the **template**. One per race/class combination (e.g. "Goblin Shaman"). Defines default attributes, mesh IDs per gender, scripts, animations, sounds, faction defaults. Loaded from `Data\Server Data\Actors.dat` at server boot into the global `ActorList[0..65535]` array, keyed by `ID`. **Static data** — never mutated after load.
- **`ActorInstance`** (Type) — the **live entity**. One per spawned NPC, one per logged-in player character. Holds its current X/Y/Z, runtime attributes (live HP, energy, etc.), AI state, inventory, faction ratings (per-instance, modifiable), `Account` link if a player, etc. Created via `CreateActorInstance(template)` which copies template defaults into the new instance. **Mutable** — every per-tick update writes to fields here.

Fields on `ActorInstance` that link back:
- `Actor.Actor` — the template this instance was made from. NPC variants of the same race/class share one template; each instance has its own runtime state.
- `Account` — Handle to the logged-in `Account` (Null for NPCs and unloaded characters).
- `ServerArea` — Handle to the current `AreaInstance`. **Stale-mid-warp** can be Null; use `Object.AreaInstance(AI\ServerArea) <> Null` discipline (CLAUDE.md → "Handle-lookup Null discipline").

### Registries

| Global | Type | Purpose |
|---|---|---|
| `ActorList(65535)` | `Actor` | Template registry. Index by `Actor\ID` (assigned at template load). |
| `RuntimeIDList(65535)` | `ActorInstance` | Wire-address registry. Index by `RuntimeID` (assigned at instance create; player RNIDs are `> 0`, NPC RuntimeIDs are usually `0`-ish). The packet protocol's `RuntimeID` field bottoms out in `RuntimeIDList(rid)`. |
| `LastRuntimeID` | int | Monotonic counter for next RuntimeID assignment. |
| `FactionNames$(99)` | string | 100-slot faction name table (`""` = unused slot). |
| `FactionDefaultRatings(99, 99)` | int | Default cross-faction rating matrix. `Actor\HomeFaction` indexes the first dimension; rating against any faction `i` is `FactionDefaultRatings(HomeFaction, i)`. |

### Chain-walk infrastructure

Three per-purpose linked lists replace `For Each ActorInstance` walks with `O(N-filtered)` walks in broadcast hot paths. See [`gameserver.md`](gameserver.md) → "Chain-walk patterns" for the broadcast-loop replacement story.

| Chain head | Stored where | Invariant | Insertion / removal |
|---|---|---|---|
| `FirstOnlinePlayer` | Global in `Actors.bb`; `NextOnlinePlayer` Field on `ActorInstance` | Only players with `RNID > 0` (enforced at call sites, not in the helper) | `OnlinePlayerInsert(AI)` at login / spawn; `OnlinePlayerRemove(AI)` at `FreeActorInstance` |
| `FirstSlave` | Field on the leader `ActorInstance`; `NextSlave` Field on each slave | Only actors with `Leader = leader_handle` | `SlaveLink(leader, slave)` at pet recruitment; `SlaveUnlink(slave)` at pet release / leader death |
| `FirstInZone` | Field on `AreaInstance` (in [`ServerAreas.bb`](serverareas.md)); `NextInZone` Field on `ActorInstance` | All actors currently in that `AreaInstance` | Maintained by `SetArea` (engine-tick rebinder) and `CreateActorInstance` / `FreeActorInstance` |

Regression tests for the chain semantics: [`src/Tests/Modules/OnlinePlayerChainTest.bb`](../../src/Tests/Modules/OnlinePlayerChainTest.bb) and [`SlaveChainTest.bb`](../../src/Tests/Modules/SlaveChainTest.bb). Strict-only (no EnableGC) because Type-heavy chain walks hit a runtime stack overflow under GC.

### Lifecycle: `CreateActorInstance` / `FreeActorInstance` / `SafeFreeActorInstance`

- **`CreateActorInstance(Actor.Actor) -> ActorInstance`** — allocates `New ActorInstance`, copies template fields, picks a `RuntimeID`, registers in `RuntimeIDList`. Per PR #306, returns `Null` on `Actor = Null` input (was `RuntimeError`) — production callers all guard upstream with `If ActorList(...) = Null` from the PR #138-#144 soft-fail sweep; the Null return is defense-in-depth for future callers that forget the guard.
- **`FreeActorInstance(AI)`** — the canonical actor-free path. Cleans up:
  - `OnlinePlayerRemove(AI)` (always — safe on never-online actors)
  - `SlaveUnlink(AI)` (always — safe on never-linked actors)
  - `FirstSlave` chain orphan handling (any actors leading this one transfer to the orphan chain)
  - `RuntimeIDList(AI\RuntimeID) = Null`
  - `Delete(AI)`

  Callers that need `FirstInZone` unlinking are expected to call `SetArea(AI, Null)` *before* `FreeActorInstance` (the per-tick lifecycle path does this). `FreeActorInstance` itself does not call `SetArea` — the `NextInZone` pointer is left in place and orphaned on the in-list `Delete`.
- **`SafeFreeActorInstance(AI)`** (in [`Actors3D.bb`](../../src/Modules/Actors3D.bb), not this file) — every-frame wrapper that ALSO clears the global `PlayerTarget` if it pointed at `AI`. The client renders `PlayerTarget` every frame; a stale handle there crashes the renderer. Always call this from per-tick code paths (`UpdateActorInstances`, `P_ActorGone`, etc.); the bare `FreeActorInstance` is for paths that don't risk stale `PlayerTarget`.

### 40-attribute schema

`ActorInstance\Attributes` is a sub-Type with `Field Value[39]` and `Field Maximum[39]` — **40 slots** indexed `0..39` (Blitz3D `Field[N]` is inclusive — see CLAUDE.md "Gotchas"). Per-attribute metadata in the global `AttributeNames$(39)` / `AttributeIsSkill(39)` / `AttributeHidden(39)` arrays (same 40-slot space).

The three "important" attribute indices that drive broadcast routing in [`P_StatUpdate`](../protocol/packets/P_StatUpdate.md):

- `HealthStat` — every change broadcasts via `UpdateAttribute` (area-wide).
- `SpeedStat` — same.
- `EnergyStat` — same.

All other attributes broadcast single-recipient (only the target's HUD needs them). See [`P_StatUpdate.md`](../protocol/packets/P_StatUpdate.md) → "Two emit patterns".

### AI mode constants

`Const AI_Wait / AI_Patrol / AI_Engage / ...` define `ActorInstance\AIMode` values consumed by [`GameServer.bb`](gameserver.md)'s `UpdateActorInstances` per-tick AI loop. NPCs use these; player actors ignore them (no AI loop runs for `RNID > 0` actors).

### Faction system

100-slot `FactionNames$(99)`; per-instance `Actor\FactionRatings[99]` Field. `FactionRatings[faction_id] > 150` = friendly; `< 150` = neutral/hostile (combat gate at [`GameServer.bb`](gameserver.md)'s `ActorAttack` uses this threshold). Defaults come from the 2D `FactionDefaultRatings` matrix at instance create.

`HomeFaction` is the actor's "team" for AI engagement decisions. Note: `TeamID` (separate field) is the guild/party identifier used by [`P_ChatMessage`](../protocol/packets/P_ChatMessage.md)'s `/g` (guild chat) routing.

## Conventions for new code touching this module

- **Always call `SafeFreeActorInstance(AI)` in per-tick code, not `FreeActorInstance(AI)`** — the `PlayerTarget` clear is non-optional in render paths.
- **Bounds-check before `ActorList(N)` / `RuntimeIDList(N)` / `Attributes\Value[N]`** — the arrays are `Dim`ed at the upper bound and Blitz3D doesn't bounds-check `Dim` access.
- **`Object.ActorInstance(handle) <> Null` guard before deref** — stale handles return Null without erroring (CLAUDE.md → "Handle-lookup Null discipline").
- **New chain — add insertion/removal helpers, call them from `FreeActorInstance`** — every chain that doesn't have its head pointer cleared at free time leaks references through the actor's afterlife.
- **Strict tests, no EnableGC** — Type-heavy chain tests hit a runtime stack overflow under GC. Strict-only is sufficient for chain-correctness assertions.

## Related modules

- [`Server.bb`](../../src/Server.bb) — hosts `UpdateAttribute` / `UpdateAttributeMax` / `UpdateReputation` (the canonical broadcast helpers that emit `P_StatUpdate` from `ActorInstance\Attributes` mutations).
- [`GameServer.bb`](gameserver.md) — per-tick consumer of this module's data. AI loop, combat engine, water tick, spawn machinery.
- [`ServerAreas.bb`](serverareas.md) — owns `Area` / `AreaInstance` Types; `AreaInstance` holds the per-area `FirstInZone` chain head Field and the `Spawned[]` / `SpawnMax[]` arrays the spawn machinery touches.
- [`ServerNet.bb`](servernet.md) — packet handlers that mutate `ActorInstance` (chat-driven `/setattribute`, `/warpother`, etc.).
- [`ScriptingCommands.bb`](scriptingcommands.md) — BVM functions that mutate actor state from script context (`BVM_SETATTRIBUTE`, `BVM_KILLACTOR`, etc.). The privilege-gating sweep at PR #300 / #301 / #311 lives here.
- [`MySQL.bb`](mysql.md) — character load/save persistence; `LoadCharacter` is one of the soft-fail-discipline callers of `CreateActorInstance`.

## See also

- [`P_AttackActor` detail](../protocol/packets/P_AttackActor.md) — combat that reads / writes this module's state.
- [`P_StatUpdate` detail](../protocol/packets/P_StatUpdate.md) — broadcast channel for attribute changes.
- [`P_ChatMessage` detail](../protocol/packets/P_ChatMessage.md) — `/g` routing uses `TeamID`; per-tick chat broadcast walks `FirstOnlinePlayer`.
- CLAUDE.md → "Handle-lookup Null discipline" — the canonical pattern.
- CLAUDE.md → "Iterator-during-iteration hazards" — relevant when `FreeActorInstance` is called from inside a `For Each ActorInstance` walk (use `DeferKillActor` instead).

* * *

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