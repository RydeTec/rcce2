<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**Inventories.bb**

This module defines the per-actor `Inventory` layout — a 46-slot array of [ItemInstance](items.md#TItemInstance) handles split into named equipped slots (weapon, shield, armour pieces, rings, amulets) plus a 31-slot backpack — and the transfer primitives (`InventoryDrop`, `InventoryAdd`, `InventorySwap`) that move items between slots while honouring the equip-eligibility rules. Both the client (predictive UI updates) and the server (authoritative state) call the same primitives; the `TellServer` parameter idiom is what tells them apart. The item catalog and instance lifecycle live in [Items.bb](items.md).

This module contains the following constants:

*   `Slot_Weapon`, `Slot_Shield`, `Slot_Hat`, `Slot_Chest`, `Slot_Hand`, `Slot_Belt`, `Slot_Legs`, `Slot_Feet`, `Slot_Ring`, `Slot_Amulet`, `Slot_Backpack` — protocol slot names (1..11) referenced by `Item\SlotType` and by `ActorHasSlot` when checking per-actor enable flags.
*   `SlotI_Weapon`, `SlotI_Shield`, `SlotI_Hat`, `SlotI_Chest`, `SlotI_Hand`, `SlotI_Belt`, `SlotI_Legs`, `SlotI_Feet`, `SlotI_Ring1..SlotI_Ring4`, `SlotI_Amulet1`, `SlotI_Amulet2`, `SlotI_Backpack` — array indices (0..14) into `Inventory\Items[]`. Indices `0..13` are the equipped slots; `14..45` is the backpack.
*   `Slots_Inventory = 45` — inclusive upper bound on the `Items[]` and `Amounts[]` arrays. `Dim` semantics: the arrays carry `Slots_Inventory + 1 = 46` slots.

This module contains the following types:

*   [Inventory](#TInventory)

This module contains the following functions:

*   [GetArmourLevel](#FGetArmourLevel)
*   [InventoryMass](#FInventoryMass)
*   [InventoryDrop](#FInventoryDrop)
*   [InventoryAdd](#FInventoryAdd)
*   [InventorySwap](#FInventorySwap)
*   [InventoryHasItem](#FInventoryHasItem)
*   [ActorHasSlot](#FActorHasSlot)
*   [SlotsMatch](#FSlotsMatch)

  

* * *

  

**Inventory (type)** <a id="TInventory"></a>

The per-actor inventory record. Owned by `ActorInstance\Inventory`. Fields:

*   `Items.ItemInstance[Slots_Inventory]` — 46-slot array of item handles. Equipped slots (indices `0..13`) follow the `SlotI_*` layout above; the backpack occupies `14..45`. A `Null` slot is empty.
*   `Amounts[Slots_Inventory]` — parallel 46-slot array of stack counts. Always `0` for `Null` slots and `>= 1` for occupied slots.
*   `My_AttrID`, `My_ID` — MySQL persistence keys; unused when the server runs against the flat-file save backend.

The 46-slot allocation reflects Blitz3D's `Field arr[N]` semantics — `[Slots_Inventory]` allocates `N + 1 = 46` slots indexed `0..45` inclusive. Loops over the full inventory use `For j = 0 To Slots_Inventory` (visits all 46), not `0 To Slots_Inventory - 1` (which would skip slot 45).

  

* * *

  

**GetArmourLevel(I.Inventory)** <a id="FGetArmourLevel"></a>

Return value: Integer sum of `ArmourLevel` across all currently-equipped armour pieces with `ItemHealth > 0`.

Parameters:

*   _I.Inventory_ — the inventory to query.

Iterates the equipped armour-slot range (`SlotI_Shield` through `SlotI_Feet`), skipping non-armour items and broken pieces (`ItemHealth = 0` armour stops contributing until repaired).

  

**InventoryMass(I.Inventory)** <a id="FInventoryMass"></a>

Return value: Total weight = sum of `Item\Mass * Amount` across every occupied slot.

Parameters:

*   _I.Inventory_ — the inventory to weigh.

Counts both equipped and backpack slots.

  

**InventoryDrop(A.ActorInstance, SlotFrom, Amount, TellServer = True)** <a id="FInventoryDrop"></a>

Return value: A `Handle()` to the dropped [ItemInstance](items.md#TItemInstance) on success, or `False` on any validation failure (bad slot index, insufficient stack, empty source slot).

Parameters:

*   _A.ActorInstance_ — the actor whose inventory is losing the items.
*   _SlotFrom_ — source slot index (`0..Slots_Inventory`).
*   _Amount_ — quantity to drop.
*   _TellServer_ — when called client-side as a predictive UI update, set to `True` to also emit `P_InventoryUpdate "D"` so the server applies the same mutation. When called server-side (the authoritative mutation), set to `False` to suppress the duplicate broadcast.

Updates `Items[]` / `Amounts[]` and (if `TellServer = True`) notifies the server. The actual world-space drop record ([DroppedItem](items.md#TDroppedItem)) is created on the server side.

  

**InventoryAdd(A.ActorInstance, SlotFrom, SlotTo, Amount, TellServer = True)** <a id="FInventoryAdd"></a>

Return value: `True` on success, `False` on any validation failure.

Parameters:

*   _A.ActorInstance_ — the actor.
*   _SlotFrom_ — source slot.
*   _SlotTo_ — destination slot, must be `>= SlotI_Backpack` (cannot merge into an equipped slot).
*   _Amount_ — quantity to move.
*   _TellServer_ — same dual-purpose flag as [InventoryDrop](#FInventoryDrop).

Merges items between two backpack stacks of the same template. Validates: both slots are non-empty, the actor has the source and destination slots ([ActorHasSlot](#FActorHasSlot)), and the two `ItemInstance`s are byte-identical ([ItemInstancesIdentical](items.md#FItemInstancesIdentical)) — otherwise the merge would silently lose per-instance state (durability, attribute rolls). Empties the source slot via [FreeItemInstance](items.md#FFreeItemInstance) if the move drained it to zero.

  

**InventorySwap(A.ActorInstance, SlotA, SlotB, Amount = 0, TellServer = True)** <a id="FInventorySwap"></a>

Return value: `True` on success, `False` on any validation failure.

Parameters:

*   _A.ActorInstance_ — the actor.
*   _SlotA_, _SlotB_ — slot indices to swap; either may be an equipped slot.
*   _Amount_ — `0` means swap whole stacks; `>= 1` means transfer that many from `SlotA` to `SlotB` (the source remainder stays in `SlotA`).
*   _TellServer_ — same dual-purpose flag as [InventoryDrop](#FInventoryDrop).

The primary equip / unequip / re-arrange primitive. Validates:

*   Both slots in range and the source is occupied.
*   The actor owns both slots ([ActorHasSlot](#FActorHasSlot)) — race / class restrictions and per-actor disabled slots are enforced here.
*   The item in each slot fits the other slot's type ([SlotsMatch](#FSlotsMatch)) — e.g., a sword can't end up in an amulet slot.
*   Stack rule: equipped slots (`< SlotI_Backpack`) can never hold more than one item.

The partial-amount path also guards against client-supplied `Amount > A\Inventory\Amounts[SlotA]` (which previously created an item duplication: the original `ItemInstance` migrated to `SlotB` while `Amounts[SlotB] = Amount`, conjuring stacks out of thin air). Now rejects any `Amount < 1 Or Amount > available` outright.

  

**InventoryHasItem(I.Inventory, Item$, Amount)** <a id="FInventoryHasItem"></a>

Return value: `True` iff the inventory contains at least `Amount` items matching `Item$` (across all slots, equipped or otherwise).

Parameters:

*   _I.Inventory_ — the inventory to search.
*   _Item$_ — case-insensitive item name.
*   _Amount_ — minimum total quantity.

  

**ActorHasSlot(A.Actor, SlotI, I.Item)** <a id="FActorHasSlot"></a>

Return value: `True` if the actor's race / class restrictions and per-slot enable flags permit the item to occupy the slot.

Parameters:

*   _A.Actor_ — the actor template (not the instance).
*   _SlotI_ — slot array index.
*   _I.Item_ — the item being equipped.

The race / class gates first: an item with a non-empty `ExclusiveRace$` is allowed ONLY in equipped slots of an actor of that race (regardless of per-slot enable flags); items exclusive to a class are rejected for any other class. Otherwise, the per-actor `InventorySlots` flag mask (set in the actor editor) decides which equipped slots are even available; the backpack is gated by `Slot_Backpack`.

  

**SlotsMatch(It.Item, SlotI)** <a id="FSlotsMatch"></a>

Return value: `True` iff the item's `ItemType` and `SlotType` are compatible with the slot index.

Parameters:

*   _It.Item_ — the item template.
*   _SlotI_ — slot array index.

The fine-grained equip rule. Backpack slots (`>= SlotI_Backpack`) accept anything. Equipped slots match: weapons → `SlotI_Weapon`; armour by `SlotType` (shield / hat / chest / etc.) → corresponding slot; rings → any of `SlotI_Ring1..Ring4`; amulets (rings whose `SlotType <> Slot_Ring`) → `SlotI_Amulet1` or `SlotI_Amulet2`.

  

* * *

  

**See also**

*   [Items.bb](items.md) — `Item` / `ItemInstance` types referenced by every slot.
*   [Actors.bb](actors.md) — `Actor\InventorySlots` flag mask consumed by [ActorHasSlot](#FActorHasSlot).
*   [ServerNet.bb](servernet.md) — `P_InventoryUpdate` packet handler that mirrors these primitives on the server side and emits the `"D"` / `"A"` / `"S"` subcommands the client-side calls produce.
