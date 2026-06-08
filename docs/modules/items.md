<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**Items.bb**

This module defines the item catalog (`Item` templates loaded from `Items.dat`) and the per-world-instance state every concrete item carries (`ItemInstance`), plus the floor-drop record (`DroppedItem`) used for items lying in a zone. Templates are immutable post-load; instances are mutated through their lifecycle (attribute rolls, damage, stacking) and serialise across the wire and to character save files. The inventory side of the gameplay loop (slot conventions, equip rules, transfer functions) lives in [Inventories.bb](inventories.md); the 3D side of dropped items lives in [Environment3D.bb](environment3d.md).

This module contains the following constants:

*   `I_Weapon`, `I_Armour`, `I_Ring`, `I_Potion`, `I_Ingredient`, `I_Image`, `I_Other` — `Item\ItemType` values (1..7).
*   `A_Hat`, `A_Shirt`, `A_Trousers`, `A_Gloves`, `A_Boots`, `A_Shield` — armour subtype indices (0..5).
*   `W_OneHand`, `W_TwoHand`, `W_Ranged` — `Item\WeaponType` values (1..3).

This module contains the following globals:

*   [ItemList.Item(65534)](#GItemList)
*   [DamageTypes$(19)](#GDamageTypes)
*   [WeaponDamage, ArmourDamage](#GWeaponArmourDamage)

This module contains the following types:

*   [Item](#TItem)
*   [ItemInstance](#TItemInstance)
*   [DroppedItem](#TDroppedItem)

This module contains the following functions:

*   [ItemInstanceStringLength](#FItemInstanceStringLength)
*   [ItemInstanceToString$](#FItemInstanceToString)
*   [ItemInstanceFromString.ItemInstance](#FItemInstanceFromString)
*   [WriteItemInstance](#FWriteItemInstance)
*   [ReadItemInstance.ItemInstance](#FReadItemInstance)
*   [ItemInstancesIdentical](#FItemInstancesIdentical)
*   [CreateItem.Item](#FCreateItem)
*   [FindItem.Item](#FFindItem)
*   [CreateItemInstance.ItemInstance](#FCreateItemInstance)
*   [CopyItemInstance.ItemInstance](#FCopyItemInstance)
*   [FreeItemInstance](#FFreeItemInstance)
*   [LoadItems](#FLoadItems)
*   [SaveItems](#FSaveItems)
*   [LoadDamageTypes](#FLoadDamageTypes)
*   [FindDamageType](#FFindDamageType)
*   [GetItemType$](#FGetItemType)
*   [GetWeaponType$](#FGetWeaponType)

  

* * *

  

**ItemList.Item(65534) (global)** <a id="GItemList"></a>

The item catalog. Indices `0..65534` map to loaded `Item` templates; the index is also the wire / save ID. `65535` is reserved as the "no item" sentinel that [WriteItemInstance](#FWriteItemInstance) emits for a Null `ItemInstance`. Out-of-range IDs are rejected at load time ([LoadItems](#FLoadItems)) and on every wire-deserialisation site ([ItemInstanceFromString](#FItemInstanceFromString)) so a corrupt save or crafted packet can't trip an OOB Dim access.

  

**DamageTypes$(19) (global)** <a id="GDamageTypes"></a>

Index → damage-type-name table loaded from `DamageTypes.dat` by [LoadDamageTypes](#FLoadDamageTypes). Indexed by `Item\WeaponDamageType` and similar resistance lookups. Sized 0..19 (20 slots) and bounded at every read site to keep a corrupt file from producing OOB indices in combat resolution.

  

**WeaponDamage, ArmourDamage (globals)** <a id="GWeaponArmourDamage"></a>

Per-attack durability cost applied to the attacking weapon and the defending armour piece respectively. Set from server config; consulted by the combat-resolution path in [GameServer.bb](gameserver.md).

  

* * *

  

**Item (type)** <a id="TItem"></a>

The template for one entry in the item catalog. Immutable after `LoadItems`. Fields:

*   `ID` — array index into `ItemList`; also the wire / save identifier.
*   `Name$` — display name; matched by [FindItem](#FFindItem).
*   `ExclusiveRace$`, `ExclusiveClass$` — restriction strings; empty for unrestricted. Checked by [Inventories.bb::ActorHasSlot](inventories.md#FActorHasSlot) on equip.
*   `Script$`, `SMethod$` — BVM script + method invoked when the item is right-clicked (P_ItemScript path in [ServerNet.bb](servernet.md)).
*   `ItemType` — one of the `I_*` constants above.
*   `Value`, `Mass` — monetary worth and per-unit weight.
*   `ThumbnailTexID` — UI icon for the inventory grid.
*   `MMeshID`, `FMeshID` — gendered mesh IDs (male / female model variants).
*   `Gubbins[5]` — six per-actor "gubbin" flags activated when this item is equipped (set via the Gubbin Tool under [src/Tools/](../../src/Tools/)).
*   `Attributes.Attributes` — default attribute deltas applied when equipped; cloned into each `ItemInstance` on create.
*   `TakesDamage` — True if using this item decrements `ItemInstance\ItemHealth`; False = indestructible.
*   `SlotType` — one of the `Slot_*` constants from [Inventories.bb](inventories.md); the equip slot this item lives in.
*   `WeaponDamage`, `WeaponDamageType`, `WeaponType` — weapon-only. `WeaponDamageType` indexes [DamageTypes$](#GDamageTypes); `WeaponType` is one of the `W_*` constants.
*   `RangedProjectile`, `RangedAnimation$`, `Range#` — ranged-weapon-only. `RangedProjectile` indexes [ProjectileList](projectiles.md#GProjectileList); load-time clamped to `0..5000`.
*   `ArmourLevel` — armour-only.
*   `EatEffectsLength` — potion / ingredient consumption duration.
*   `ImageID` — image-item-only; texture ID.
*   `MiscData$` — free-form payload for content-author use.
*   `Stackable` — True if multiple instances can share a single inventory slot.

  

**ItemInstance (type)** <a id="TItemInstance"></a>

A concrete in-world copy of an `Item`. Lives in inventory slots, on the floor (inside a [DroppedItem](#TDroppedItem)), or transient on the server during a trade or pickup. Fields:

*   `Item.Item` — pointer back to the template.
*   `Attributes.Attributes` — per-instance attribute deltas (replaces `Item\Attributes`, which is just the template default). Cloned from the template on `CreateItemInstance`.
*   `ItemHealth` — percentage of remaining durability (`0..100`). Decremented on use when `Item\TakesDamage` is True; an item at 0 is broken and (for armour) no longer contributes to [GetArmourLevel](inventories.md#FGetArmourLevel).
*   `Assignment`, `AssignTo.ActorInstance` — server-only bookkeeping for instances that have been created but not yet bound to a slot (e.g., during a multi-step trade or quest reward).

  

**DroppedItem (type)** <a id="TDroppedItem"></a>

A floor-dropped item record, owned by the server, broadcast to the clients that see it. Fields:

*   `EN` — client-side entity handle for the 3D mesh.
*   `ServerHandle` — the [AreaInstance](serverareas.md) the drop belongs to.
*   `X#`, `Y#`, `Z#` — world position.
*   `Item.ItemInstance` — the dropped instance.
*   `Amount` — stack count (for stackable items).

`DroppedItem` cleanup discipline is hardened: every iterator-during-iteration path that frees a `DroppedItem` uses the After-cursor walk pattern (PRs #251–#259 closed the relevant sites).

  

* * *

  

**ItemInstanceStringLength()** <a id="FItemInstanceStringLength"></a>

Return value: The byte length of an `ItemInstance` in wire / string form (currently 83).

Parameters: None.

The single source of truth for the paired-`Mid$` contract: sender and receiver of any wire field carrying a serialised `ItemInstance` must use this length. The value covers `2` (item ID, big-endian short) `+ 40*2` (per-attribute deltas, big-endian shorts offset by `+5000` to stay in unsigned range) `+ 1` (item health). If the wire layout ever changes, this number changes with it.

  

**ItemInstanceToString$(I.ItemInstance)** <a id="FItemInstanceToString"></a>

Return value: A wire-format string of length [`ItemInstanceStringLength`](#FItemInstanceStringLength), or `""` if `I = Null`.

Parameters:

*   _I.ItemInstance_ — the instance to serialise.

Encodes the item ID, 40 attribute values, and durability into a flat byte string. Pair with [ItemInstanceFromString](#FItemInstanceFromString) on the receiver. Attribute values are shifted by `+5000` before encoding to keep the unsigned-short range usable for typical `-5000..+60000` deltas.

  

**ItemInstanceFromString.ItemInstance(Pa$)** <a id="FItemInstanceFromString"></a>

Return value: A new `ItemInstance` reference, or `Null` for any malformed / sentinel input.

Parameters:

*   _Pa$_ — wire-format string of length [`ItemInstanceStringLength`](#FItemInstanceStringLength).

Reconstructs an instance from the wire form. Validates the item ID against `0..65534` and returns `Null` (still consuming the trailing bytes so the caller's offset arithmetic stays in sync) on either an out-of-range ID or an ID whose `ItemList` slot is unpopulated. The Null-but-consume behaviour matches the receive-side guards in [ServerNet.bb](servernet.md) `P_InventoryUpdate` handlers.

  

**WriteItemInstance(Stream, I.ItemInstance)** <a id="FWriteItemInstance"></a>

Return value: `True` on a real instance; falls through (no return value) when `I = Null` after emitting the `65535` sentinel.

Parameters:

*   _Stream_ — a writable file handle.
*   _I.ItemInstance_ — the instance to persist; `Null` emits a 2-byte `65535` sentinel.

Persists an instance to a save stream (typically `Accounts.dat` per-character inventory). Always wrap the calling Save function in [SafeWriteOpen / SafeWriteCommit](logging.md) so a crash mid-write doesn't truncate the file.

  

**ReadItemInstance.ItemInstance(Stream)** <a id="FReadItemInstance"></a>

Return value: A new `ItemInstance`, or `Null` if the next record is the `65535` sentinel or the item ID is no longer in `ItemList`.

Parameters:

*   _Stream_ — a readable file handle.

Reads the next instance record. On an unknown / removed item ID, logs to `MainLog` and consumes the remaining attribute + health bytes one read at a time so EOF stops the load cleanly rather than a SeekFile silently moving past the file end.

  

**ItemInstancesIdentical(A.ItemInstance, B.ItemInstance)** <a id="FItemInstancesIdentical"></a>

Return value: `True` iff `A` and `B` reference the same template AND have identical `ItemHealth` AND identical attribute deltas.

Parameters:

*   _A.ItemInstance_, _B.ItemInstance_ — instances to compare. `Null` either side returns `False`.

Used by [InventoryAdd](inventories.md#FInventoryAdd) to decide whether two stacks can merge.

  

**CreateItem.Item()** <a id="FCreateItem"></a>

Return value: A new `Item` template with `ID` assigned to the next free `ItemList` slot, or `Null` if `ItemList` is full.

Parameters: None.

Used by the editor when authoring a new item.

  

**FindItem.Item(Name$)** <a id="FFindItem"></a>

Return value: The matching `Item` template, or `Null` if no item with that name exists.

Parameters:

*   _Name$_ — case-insensitive name match.

  

**CreateItemInstance.ItemInstance(Item.Item)** <a id="FCreateItemInstance"></a>

Return value: A fresh `ItemInstance` cloned from the given template, with `ItemHealth = 100` and attribute values copied from the template.

Parameters:

*   _Item.Item_ — template to instantiate.

  

**CopyItemInstance.ItemInstance(A.ItemInstance)** <a id="FCopyItemInstance"></a>

Return value: A new `ItemInstance` byte-equivalent to `A`. Used by [InventorySwap](inventories.md#FInventorySwap) when splitting a stack.

Parameters:

*   _A.ItemInstance_ — instance to copy.

  

**FreeItemInstance(I.ItemInstance)** <a id="FFreeItemInstance"></a>

Return value: None.

Parameters:

*   _I.ItemInstance_ — instance to delete. Frees the attached `Attributes` first.

Always use this rather than `Delete` directly so the `Attributes` sub-object is also released.

  

**LoadItems(Filename$)** <a id="FLoadItems"></a>

Return value: Number of items loaded, or `-1` if the file could not be opened.

Parameters:

*   _Filename$_ — path to the item catalog (typically `Data\Server Data\Items.dat`).

Reads the catalog, populating `ItemList`. Defensively bounds every length-prefixed string via [ReadBoundedString$](logging.md) and clamps every Dim-indexing field (`ID`, `RangedProjectile`, `WeaponDamageType`) at the load site so a corrupt or crafted `Items.dat` can't drive an OOB write on server boot.

  

**SaveItems(Filename$)** <a id="FSaveItems"></a>

Return value: `True` on a fully-flushed save; `False` on `WriteFile` open failure or [SafeWriteCommit](logging.md) failure.

Parameters:

*   _Filename$_ — destination path.

Atomic-write through [SafeWriteOpen / SafeWriteCommit](logging.md) so a crash mid-flush leaves the previous (good) `Items.dat` recoverable as `.bak`. Also writes a non-critical debug dump (`Items_debug.txt`) via a direct `WriteFile` — that file is regenerable and intentionally not atomic.

  

**LoadDamageTypes(Filename$)** <a id="FLoadDamageTypes"></a>

Return value: `True` on success, `False` if the file could not be opened.

Parameters:

*   _Filename$_ — path to `DamageTypes.dat`.

Loads 20 damage-type names into [DamageTypes$](#GDamageTypes). Each name is read via [ReadBoundedString$](logging.md) with a 256-byte cap.

  

**FindDamageType(Name$)** <a id="FFindDamageType"></a>

Return value: The damage-type index, or `-1` if no match.

Parameters:

*   _Name$_ — exact-match damage type name.

  

**GetItemType$(I.Item)** <a id="FGetItemType"></a>

Return value: A localised string describing the item's category (uses [LanguageString$](language.md) to resolve `LS_Weapon`, `LS_Armour`, `LS_Ring`, `LS_Amulet`, `LS_Potion`, `LS_Ingredient`, `LS_Image`, `LS_Miscellaneous`).

Parameters:

*   _I.Item_ — the item template.

Disambiguates rings vs amulets by `SlotType` (`Slot_Ring` → ring, otherwise → amulet).

  

**GetWeaponType$(I.Item)** <a id="FGetWeaponType"></a>

Return value: A localised string for the weapon grip style (`LS_OneHanded`, `LS_TwoHanded`, `LS_Ranged`, or `LS_Unknown`).

Parameters:

*   _I.Item_ — the item template (only meaningful for `I_Weapon` items).

  

* * *

  

**See also**

*   [Inventories.bb](inventories.md) — slot conventions and item-transfer primitives.
*   [Logging.bb](logging.md) — `SafeWriteOpen / SafeWriteCommit / ReadBoundedString$` used throughout the load and save paths.
*   [RCEnet.bb](../../src/Modules/RCEnet.bb) — `RCE_StrFromInt$` / `RCE_IntFromStr` wire-encoding helpers used by the `ItemInstance` string format.
*   [ServerNet.bb](servernet.md) — `P_InventoryUpdate` / `P_ItemScript` handlers that read and write `ItemInstance` records on the wire.
*   [Actors.bb](actors.md) — `Attributes` type referenced by `Item\Attributes` and `ItemInstance\Attributes`.
*   [src/Tests/Modules/ItemsTest.bb](../../src/Tests/Modules/ItemsTest.bb) — round-trip serialisation tests and the canonical inline-stub pattern for testing modules that include `Items.bb`.
