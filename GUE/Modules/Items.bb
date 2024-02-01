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
	Field Script$, Method$      ; Called when the item is right clicked
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

	I.ItemInstance = CreateItemInstance(ItemList(RCE_IntFromStr(Left$(Pa$, 2))))
	Offset = 3
	For j = 0 To 39
		I\Attributes\Value[j] = RCE_IntFromStr(Mid$(Pa$, Offset, 2)) - 5000
		Offset = Offset + 2
	Next
	I\ItemHealth = RCE_IntFromStr(Mid$(Pa$, Offset, 1))

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

	I.ItemInstance = CreateItemInstance(ItemList(ID))
	For j = 0 To 39
		I\Attributes\Value[j] = ReadShort(Stream) - 5000
	Next
	I\ItemHealth = ReadByte(Stream)

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
			ItemList(I\ID) = I
			I\Name$            = ReadString$(F)
			I\ExclusiveRace$   = ReadString$(F)
			I\ExclusiveClass$  = ReadString$(F)
			I\Script$          = ReadString$(F)
			I\Method$          = ReadString$(F)
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
					I\WeaponType       = ReadShort(F)
					I\RangedProjectile = ReadShort(F)
					I\Range#           = ReadFloat#(F)
					I\RangedAnimation$ = ReadString$(F)
				Case I_Armour
					I\ArmourLevel      = ReadShort(F)
				Case I_Potion, I_Ingredient
					I\EatEffectsLength = ReadShort(F)
				Case I_Image
					I\ImageID          = ReadShort(F)
				Case I_Other
					I\MiscData$        = ReadString$(F)
			End Select
			Items = Items + 1
		Wend

	CloseFile(F)
	Return Items

End Function

; Saves all loaded items to a file
Function SaveItems(Filename$)

	F = WriteFile(Filename$)
	If F = 0 Then Return False

		For I.Item = Each Item
			WriteShort F, I\ID
			WriteString F, I\Name$
			WriteString F, I\ExclusiveRace$
			WriteString F, I\ExclusiveClass$
			WriteString F, I\Script$
			WriteString F, I\Method$
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
				Case I_Other
					WriteString F, I\MiscData$
			End Select
		Next

	CloseFile(F)
	Return True

End Function

; Loads attribute names from file
Function LoadDamageTypes(Filename$)

	F = ReadFile(Filename$)
	If F = 0 Then Return False
		For i = 0 To 19
			DamageTypes$(i) = ReadString$(F)
		Next
	CloseFile(F)
	Return True

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