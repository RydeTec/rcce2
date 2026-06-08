<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**Spells.bb**

This module defines the catalog of spells (general-purpose actor abilities, not just magic). A spell binds a name, icon, restrictions, recharge timer, and a script entry point that runs when the spell is cast. The cast dispatch lives in [ServerNet.bb](servernet.md) (`P_SpellUpdate`); this module owns the data model and the load/save format.

> **Naming note**: the original RealmCrafter codebase uses "Spells" and "Abilities" interchangeably. Engine code and data files use *Spell*; the user-facing scripting API ([ScriptingCommands.bb](scripting.md)) names the BVM commands `BVM_ADDABILITY`, `BVM_ABILITYKNOWN`, etc. The two terms refer to the same thing.

This module contains the following globals:

*   [SpellsList.Spell(65534)](#GSpellsList)
*   [Spells](#GSpells)

This module contains the following types:

*   [Spell](#TSpell)
*   [MemorisingSpell](#TMemorisingSpell)

This module contains the following functions:

*   [CreateSpell](#FCreateSpell)
*   [LoadSpells](#FLoadSpells)
*   [SaveSpells](#FSaveSpells)

  

* * *

  

**SpellsList.Spell(65534) (global)**

Sparse index of every loaded `Spell`, keyed by `ID`. Slots not yet assigned are `Null`; readers must guard with `If SpellsList(id) <> Null` before dereferencing, since admins can delete spells in the editor and stale references can persist in a character's `KnownSpells[]` save data. The [`P_FetchCharacter` handler](servernet.md) prunes stale entries on character-select to keep `Me\KnownSpells[]` consistent before the player enters the world.

  

* * *

  

**Spells (global)**

The number of currently-loaded spells. Set by `LoadSpells` and incremented by `CreateSpell`.

  

* * *

  

**Spell (type)**

A single ability definition:

*   `ID` — server-side identifier and index into `SpellsList`.
*   `Name$` — display name (used by the spellbook UI and the scripting `ABILITY*` BVM commands for lookups).
*   `Description$` — flavor text shown in the spellbook tooltip.
*   `ThumbnailTexID` — icon texture ID.
*   `ExclusiveRace$`, `ExclusiveClass$` — restriction strings. Empty = no restriction. The cast path in [ServerNet.bb's P_SpellUpdate "F"](servernet.md) enforces these (mirrors the item-eat gate in `P_EatItem`).
*   `RechargeTime` — cooldown between casts, in milliseconds.
*   `Script$`, `SMethod$` — script entry point. `Script$` is a path into `Data\Scripts`; `SMethod$` is the method name (empty = `Main`).

  

* * *

  

**MemorisingSpell (type)**

Tracks an in-progress memorisation when the server runs in `RequireMemorise` mode:

*   `AI.ActorInstance` — the actor learning the spell.
*   `KnownNum` — the spell slot being filled (0..9).
*   `CreatedTime` — `MilliSecs()` at start; used to compute progress against the memorise timer.

The server consumes the record once the memorise timer elapses; until then, the player can cancel.

  

* * *

  
  
  

**CreateSpell.Spell()**

Return value: A new `Spell` reference, or `Null` if `SpellsList` is full.

Parameters: None

Allocates the next free slot in `SpellsList`, assigns the slot's index as the new spell's `ID`, and returns the template seeded with `Name$ = "New ability"` and `RechargeTime = 2000`.

  

* * *

  

**LoadSpells(Filename$)**

Return value: Number of spells loaded, or `-1` if the file could not be opened.

Parameters:

*   _Filename$_ — Path to the spell data file (typically `Data\Server Data\Spells.dat`).

Reads spell templates from disk into `SpellsList`. Each record carries a 2-byte `ID` followed by the spell's fields. An out-of-range `ID` aborts the load defensively (preserving any spells already parsed) rather than corrupt memory via a `Dim` out-of-range write. String fields go through `ReadBoundedString$` ([Logging.bb](logging.md)): 256 bytes for `Name$` / `ExclusiveRace$` / `ExclusiveClass$`, 1024 for `Description$` and 1024 for `Script$` / `SMethod$`.

  

* * *

  

**SaveSpells(Filename$)**

Return value: `True` on success, `False` if the file could not be opened.

Parameters:

*   _Filename$_ — Path to write the spell data file.

Writes every loaded `Spell` to disk via the atomic temp+rename helper in [Logging.bb](logging.md), so a crash or power loss mid-write cannot truncate the catalog.

  

* * *

  
  
  
