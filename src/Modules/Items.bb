Const I_Weapon     = 1 ; Item types
Const I_Armour     = 2
Const I_Ring       = 3
Const I_Potion     = 4
Const I_Ingredient = 5
Const I_Image      = 6
Const I_Other      = 7

Const A_Hat      = 0 ; Armour types
Const A_Shirt    = 1
Const A_Trousers = 2
Const A_Gloves   = 3
Const A_Boots    = 4
Const A_Shield   = 5

Const W_OneHand = 1 ; Weapon types
Const W_TwoHand = 2
Const W_Ranged  = 3

Dim DamageTypes$(19)

Global WeaponDamage, ArmourDamage

; Describes an item
Dim ItemList.Item(65534)
Type Item
	Field ID
	Field Name$
	Field ExclusiveRace$, ExclusiveClass$ ; If this item can only be used by a certain race and/or class
	Field Script$, SMethod$      ; Called when the item is right clicked
	Field ItemType              ; Should be one of the constants above
	Field Value, Mass           ; Average monetary value, and item weight
	Field ThumbnailTexID        ; The texture ID for the image seen in the inventory system
	Field MMeshID, FMeshID      ; Weapon/hat/shield/chest/forearm/shin mesh IDs
	Field Gubbins[5]            ; Flags to activate gubbins when item is equipped
	Field Attributes.Attributes ; An actor attributes object (for extra weapon damage effects, armour use, food eating, etc.)
	Field TakesDamage           ; True if using this item reduces its health, False for it to be indestructable
	Field SlotType              ; Should be set to one of the slot type constants in Inventories.bb
	Field WeaponDamage, WeaponDamageType, WeaponType ; Weapon specific
	Field RangedProjectile, RangedAnimation$, Range# ; Ranged weapon specific
	Field ArmourLevel                                ; Armour specific
	Field EatEffectsLength                           ; Potion or ingredients specific
	Field ImageID                                    ; Image item specific (Texture ID)
	Field MiscData$                                  ; General use for misc items
	Field Stackable                                  ; Item can be stacked up
End Type

; Is used when an actual instance of an item is created in the world (on the floor, in someone's inventory, etc.)
Type ItemInstance
	Field Item.Item
	Field Attributes.Attributes ; Replaces Item\Attributes which is merely the default item attributes
	Field ItemHealth            ; The amount of damage (percentage) the item has left before breaking
	Field Assignment, AssignTo.ActorInstance  ; Server use only - Assignment is > 0 if item instance is created but not assigned an inventory slot yet
End Type

; Item dropped on the floor
Type DroppedItem
	Field EN
	Field ServerHandle
	Field X#, Y#, Z#
	Field Item.ItemInstance
	Field Amount
End Type

; Returns the correct length in bytes of an item instance in string form
Function ItemInstanceStringLength()

	Return 83

End Function

; Converts an item instance to a string
Function ItemInstanceToString$(I.ItemInstance)

	If I = Null Then Return ""

	Pa$ = RCE_StrFromInt$(I\Item\ID, 2)
	For j = 0 To 39
		Pa$ = Pa$ + RCE_StrFromInt$(I\Attributes\Value[j] + 5000, 2)
	Next
	Pa$ = Pa$ + RCE_StrFromInt$(I\ItemHealth, 1)

	Return Pa$

End Function

; Reconstructs an item instance from a string
Function ItemInstanceFromString.ItemInstance(Pa$)

	If Len(Pa$) < ItemInstanceStringLength() Then Return Null

	Local I.ItemInstance = Null
	Local id% = RCE_IntFromStr(Left$(Pa$, 2))
	; ItemList is Dim'd 0..65534; 65535 is the "no item" sentinel
	; that WriteItemInstance emits for Null items. A 2-byte wire
	; field carries 0..65535 -- without the upper bound, id=65535
	; Dim-OOB reads ItemList(65535) before the `<> Null` branch
	; can run. Same shape as the read-side guard in #209.
	If id < 0 Or id > 65534
		; Still consume the trailing bytes so the caller's offset
		; math stays in sync (matches the existing Null-fallback
		; branch below).
		Offset = 3
		For j = 0 To 39
			RCE_IntFromStr(Mid$(Pa$, Offset, 2))
			Offset = Offset + 2
		Next
		RCE_IntFromStr(Mid$(Pa$, Offset, 1))
		Return Null
	EndIf
	If ItemList(id) <> Null
		I.ItemInstance = CreateItemInstance(ItemList(id))
		Offset = 3
		For j = 0 To 39
			I\Attributes\Value[j] = RCE_IntFromStr(Mid$(Pa$, Offset, 2)) - 5000
			Offset = Offset + 2
		Next
		I\ItemHealth = RCE_IntFromStr(Mid$(Pa$, Offset, 1))
	Else
		WriteLog(MainLog, "Item Removal: Item with ID " + ID + " has been removed from actor as it is no longer existant!")
		Offset = 3
		For j = 0 To 39
			RCE_IntFromStr(Mid$(Pa$, Offset, 2))
			Offset = Offset + 2
		Next
		RCE_IntFromStr(Mid$(Pa$, Offset, 1))
	EndIf

	Return I

End Function

; Writes an item instance to a stream
Function WriteItemInstance(Stream, I.ItemInstance)

	If I = Null Then WriteShort Stream, 65535 : Return

	WriteShort Stream, I\Item\ID
	For j = 0 To 39
		WriteShort Stream, I\Attributes\Value[j] + 5000
	Next
	WriteByte Stream, I\ItemHealth

	Return True

End Function

; Reads an item instance from a stream
Function ReadItemInstance.ItemInstance(Stream)

	ID = ReadShort(Stream)
	If ID = 65535 Then Return

	Local I.ItemInstance = Null
	If ItemList(ID) <> Null
		I.ItemInstance = CreateItemInstance(ItemList(ID))
		For j = 0 To 39
			I\Attributes\Value[j] = ReadShort(Stream) - 5000
		Next
		I\ItemHealth = ReadByte(Stream)
	Else
		WriteLog(MainLog, "Item not found: Item with ID " + ID + " has been found during the character loading.!")
		; Consume the 40 attribute shorts + 1 health byte one read at a
		; time so EOF stops us rather than the SeekFile silently moving
		; the cursor past the end of the file (which would then surface
		; on the next ReadShort as a silent zero).
		Local k
		For k = 0 To 39
			If Eof(Stream) Then Exit
			ReadShort(Stream)
		Next
		If Not Eof(Stream) Then ReadByte(Stream)
	EndIf
	Return I

End Function

; Compares two item instances and returns true if they are the same
Function ItemInstancesIdentical(A.ItemInstance, B.ItemInstance)

	If A = Null Or B = Null Then Return False
	If A\Item <> B\Item Then Return False
	If A\ItemHealth <> B\ItemHealth Then Return False
	For i = 0 To 39
		If A\Attributes\Value[i] <> B\Attributes\Value[i] Then Return False
	Next

	Return True

End Function

; Creates a new item template
Function CreateItem.Item()

	For ID = 0 To 65534
		If ItemList(ID) = Null
			I.Item = New Item
			I\ID = ID
			ItemList(I\ID) = I
			I\Attributes = New Attributes
			I\MMeshID = 65535
			I\FMeshID = 65535
			I\ItemType = 1
			I\SlotType = 1
			I\Value = 1
			I\Mass = 1
			I\ImageID = 65535
			Exit
		EndIf
	Next

	Return I

End Function

; Duplicate an Item template -- allocate a new ID, copy every field.
; Used by Loom's EntityFactory_Duplicate. Returns the new ID, or -1 if
; the ItemList is full or the source doesn't exist.
;
; The Attributes side-instance is deep-copied so the duplicate has its
; own backing storage (modifying duplicate.Attributes\Value[0] mustn't
; affect the source). Same for the Gubbins / Attributes arrays.
Function DuplicateItemTemplate(srcID)
	If srcID < 0 Or srcID > 65534 Then Return -1
	Src.Item = ItemList(srcID)
	If Src = Null Then Return -1

	Dst.Item = CreateItem()
	If Dst = Null Then Return -1   ; ItemList full
	; CreateItem allocates a fresh Attributes; reuse it.

	Dst\Name$           = Src\Name$ + " (copy)"
	Dst\ExclusiveRace$  = Src\ExclusiveRace$
	Dst\ExclusiveClass$ = Src\ExclusiveClass$
	Dst\Script$         = Src\Script$
	Dst\SMethod$        = Src\SMethod$
	Dst\ItemType        = Src\ItemType
	Dst\Value           = Src\Value
	Dst\Mass            = Src\Mass
	Dst\ThumbnailTexID  = Src\ThumbnailTexID
	Dst\MMeshID         = Src\MMeshID
	Dst\FMeshID         = Src\FMeshID
	For i = 0 To 5
		Dst\Gubbins[i] = Src\Gubbins[i]
	Next
	Dst\TakesDamage     = Src\TakesDamage
	Dst\SlotType        = Src\SlotType
	Dst\WeaponDamage    = Src\WeaponDamage
	Dst\WeaponDamageType = Src\WeaponDamageType
	Dst\WeaponType      = Src\WeaponType
	Dst\RangedProjectile = Src\RangedProjectile
	Dst\RangedAnimation$ = Src\RangedAnimation$
	Dst\Range#          = Src\Range#
	Dst\ArmourLevel     = Src\ArmourLevel
	Dst\EatEffectsLength = Src\EatEffectsLength
	Dst\ImageID         = Src\ImageID
	Dst\MiscData$       = Src\MiscData$
	Dst\Stackable       = Src\Stackable

	; Attributes deep-copy
	If Src\Attributes <> Null And Dst\Attributes <> Null
		For i = 0 To 39
			Dst\Attributes\Value[i]   = Src\Attributes\Value[i]
			Dst\Attributes\Maximum[i] = Src\Attributes\Maximum[i]
		Next
	EndIf

	Return Dst\ID
End Function

; Delete an Item template. Used by Loom's entity-delete path. Strict
; callers can't write to ItemList directly per the Dim-inside-Method trap,
; so this lives here in the non-Strict module.
Function DeleteItemTemplate(ID)
	If ID < 0 Or ID > 65534 Then Return False
	I.Item = ItemList(ID)
	If I = Null Then Return False
	If I\Attributes <> Null
		Delete I\Attributes
		I\Attributes = Null
	EndIf
	ItemList(ID) = Null
	Delete I
	Return True
End Function

; Finds an item by name
Function FindItem.Item(Name$)

	Name$ = Upper$(Name$)

	For I.Item = Each Item
		If Upper$(I\Name$) = Name$ Then Return I
	Next
	Return Null

End Function

; Creates a new instance of an item
Function CreateItemInstance.ItemInstance(Item.Item)

	I.ItemInstance = New ItemInstance
	I\Item = Item
	I\ItemHealth = 100
	I\Attributes = New Attributes
	For j = 0 To 39
		I\Attributes\Value[j] = I\Item\Attributes\Value[j]
	Next

	Return I

End Function

; Copies an item instance exactly
Function CopyItemInstance.ItemInstance(A.ItemInstance)

	I.ItemInstance = New ItemInstance
	I\Attributes = New Attributes
	I\Item = A\Item
	I\ItemHealth = A\ItemHealth
	For j = 0 To 39
		I\Attributes\Value[j] = A\Attributes\Value[j]
	Next

	Return I

End Function

; Frees an item instance
Function FreeItemInstance(I.ItemInstance)

	Delete I\Attributes
	Delete I

End Function

; Loads all items from a file and returns how many were loaded
Function LoadItems(Filename$)

	Local Items = 0

	F = ReadFile(Filename$)
	If F = 0 Then Return -1

		While Not Eof(F)
			I.Item = New Item
			I\Attributes = New Attributes
			I\ID = ReadShort(F)
			; ReadShort is signed 16-bit, so a malformed or truncated Items.dat can
			; surface a negative ID. ItemList is dimensioned 0..65534, and Blitz Dim
			; with a negative index writes outside the array → arbitrary memory
			; corruption on every server boot from a crafted save. Skip and stop
			; loading; the partial state we already built is consistent.
			If I\ID < 0 Or I\ID > 65534
				Delete I\Attributes : Delete I
				Exit
			EndIf
			ItemList(I\ID) = I
			; Bound every length-prefixed string against corrupted Items.dat.
			; 256 for display/restriction fields; 1024 for script paths
			; (matches ReadActorInstance's per-character Script$ cap).
			I\Name$            = ReadBoundedString$(F, 256)
			I\ExclusiveRace$   = ReadBoundedString$(F, 256)
			I\ExclusiveClass$  = ReadBoundedString$(F, 256)
			I\Script$          = ReadBoundedString$(F, 1024)
			I\SMethod$          = ReadBoundedString$(F, 1024)
			I\ItemType         = ReadByte(F)
			I\Value            = ReadInt(F)
			I\Mass             = ReadShort(F)
			I\TakesDamage      = ReadByte(F)
			I\ThumbnailTexID   = ReadShort(F)
			For j = 0 To 5 : I\Gubbins[j] = ReadShort(F) : Next
			I\MMeshID           = ReadShort(F)
			I\FMeshID           = ReadShort(F)
			I\SlotType         = ReadShort(F)
			I\Stackable        = ReadByte(F)
			For j = 0 To 39 : I\Attributes\Value[j] = ReadShort(F) - 5000 : Next
			Select I\ItemType
				Case I_Weapon
					I\WeaponDamage     = ReadShort(F)
					I\WeaponDamageType = ReadShort(F)
					; DamageTypes$ is Dim'd (19). WeaponDamageType is
					; the index used to render damage in combat output
					; (ClientNet P_AttackUpdate) and BVM_DAMAGETYPE$.
					; ReadShort can carry -32768..32767; clamp at load.
					If I\WeaponDamageType < 0 Or I\WeaponDamageType > 19 Then I\WeaponDamageType = 0
					I\WeaponType       = ReadShort(F)
					I\RangedProjectile = ReadShort(F)
					; ProjectileList is Dim'd 0..5000. ReadShort can carry
					; -32768..32767; a corrupt Items.dat would otherwise
					; drive ProjectileList(I\RangedProjectile) OOB in the
					; combat path (GameServer.bb ~335). Clamp to a safe
					; sentinel (0) so the existing `If P <> Null` guard
					; downstream catches the missing slot.
					If I\RangedProjectile < 0 Or I\RangedProjectile > 5000 Then I\RangedProjectile = 0
					I\Range#           = ReadFloat#(F)
					I\RangedAnimation$ = ReadBoundedString$(F, 256)
				Case I_Armour
					I\ArmourLevel      = ReadShort(F)
				Case I_Potion, I_Ingredient
					I\EatEffectsLength = ReadShort(F)
				Case I_Image
					I\ImageID          = ReadShort(F)
			End Select
			; MiscData$ can carry user-defined item data (free-form strings
			; from item scripts). Generous cap so legitimate content isn't
			; truncated; still bounded so a corrupt file can't DoS the load.
			I\MiscData$        = ReadBoundedString$(F, 4096)
			Items = Items + 1
		Wend

	CloseFile(F)
	Return Items

End Function

; Saves all loaded items to a file
Function SaveItems(Filename$)

	; Atomic-save through SafeWriteOpen/Commit so a crash, power loss,
	; or disk-full mid-write doesn't truncate Items.dat (the entire
	; item catalog). The previous direct WriteFile was the unfinished
	; half of Track FF; the editor's "Save All" path blasted through
	; nine such direct-write savers in sequence, any one of which
	; could leave the catalog half-written.
	Local Temp$ = SafeWriteOpen$(Filename$)
	F = WriteFile(Temp$)
	If F = 0 Then Return False

		For I.Item = Each Item
			WriteShort F, I\ID
			WriteString F, I\Name$
			WriteString F, I\ExclusiveRace$
			WriteString F, I\ExclusiveClass$
			WriteString F, I\Script$
			WriteString F, I\SMethod$
			WriteByte F, I\ItemType
			WriteInt F, I\Value
			WriteShort F, I\Mass
			WriteByte F, I\TakesDamage
			WriteShort F, I\ThumbnailTexID
			For j = 0 To 5 : WriteShort F, I\Gubbins[j] : Next
			WriteShort F, I\MMeshID
			WriteShort F, I\FMeshID
			WriteShort F, I\SlotType
			WriteByte F, I\Stackable
			For j = 0 To 39 : WriteShort F, I\Attributes\Value[j] + 5000 : Next
			Select I\ItemType
				Case I_Weapon
					WriteShort F, I\WeaponDamage
					WriteShort F, I\WeaponDamageType
					WriteShort F, I\WeaponType
					WriteShort F, I\RangedProjectile
					WriteFloat F, I\Range#
					WriteString F, I\RangedAnimation$
				Case I_Armour
					WriteShort F, I\ArmourLevel
				Case I_Potion, I_Ingredient
					WriteShort F, I\EatEffectsLength
				Case I_Image
					WriteShort F, I\ImageID
			End Select
			WriteString F, I\MiscData$
		Next

	If Not SafeWriteCommit%(Temp$, Filename$, F) Then Return False

	; Small edit, allows to quickly find IMPORTANT item values, cysis145
	; The debug dump is not load-critical; keep it as a direct write
	; (overwriting Items_debug.txt with a partial copy is harmless).
	G = WriteFile("Data\Server Data\Items_debug.txt")
	If G = 0 Then Return True
		For I.Item = Each Item
			WriteLine(G, "Item ID: " + I\ID)
			WriteLine(G, "Item Name: " + I\Name$)
			WriteLine(G, "")
		Next
	CloseFile(G)

	Return True

End Function

; Loads damage type names from file. Bound each name against a corrupted
; DamageTypes.dat (same shape as the rest of the data-loader sweep).
Function LoadDamageTypes(Filename$)

	F = ReadFile(Filename$)
	If F = 0 Then Return False
		For i = 0 To 19
			DamageTypes$(i) = ReadBoundedString$(F, 256)
		Next
	CloseFile(F)
	Return True

End Function

; Saves DamageTypes$ to a .dat file via SafeWriteOpen/Commit (atomic).
; Mirrors LoadDamageTypes' on-disk format: 20 bounded strings.
; Added for Loom's catalog editor (Settings tab); GUE has no editor for
; this file so it was previously hand-edited as binary.
Function SaveDamageTypes(Filename$)

	Local Temp$ = SafeWriteOpen$(Filename$)
	F = WriteFile(Temp$)
	If F = 0 Then Return False

		For i = 0 To 19
			WriteString(F, DamageTypes$(i))
		Next

	Return SafeWriteCommit%(Temp$, Filename$, F)

End Function

; Non-Strict setter for DamageTypes$ -- Strict callers in Loom can't write
; to the Dim'd array directly. Same shape as SetFactionName / SetFactionRelation.
Function SetDamageTypeName(Index, Name$)
	If Index < 0 Or Index > 19 Then Return
	DamageTypes$(Index) = Name$
End Function

; Looks up a damage type number from the name
Function FindDamageType(Name$)

	For i = 0 To 19
		If DamageTypes$(i) = Name$ Then Return i
	Next
	Return -1

End Function

; Gets the item type in text form
Function GetItemType$(I.Item)

	Select I\ItemType
		Case I_Weapon : Return LanguageString$(LS_Weapon)
		Case I_Armour : Return LanguageString$(LS_Armour)
		Case I_Ring
			If I\SlotType = Slot_Ring Then Return LanguageString$(LS_Ring) Else Return LanguageString$(LS_Amulet)
		Case I_Potion : Return LanguageString$(LS_Potion)
		Case I_Ingredient : Return LanguageString$(LS_Ingredient)
		Case I_Image : Return LanguageString$(LS_Image)
		Case I_Other : Return LanguageString$(LS_Miscellaneous)
	End Select
	Return LanguageString$(LS_Unknown)

End Function

; Gets the weapon type in text form
Function GetWeaponType$(I.Item)

	Select I\WeaponType
		Case W_OneHand : Return LanguageString$(LS_OneHanded)
		Case W_TwoHand : Return LanguageString$(LS_TwoHanded)
		Case W_Ranged : Return LanguageString$(LS_Ranged)
	End Select
	Return LanguageString$(LS_Unknown)

End Function