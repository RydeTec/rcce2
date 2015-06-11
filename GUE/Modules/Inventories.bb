;##############################################################################################################################
; Realm Crafter version 1.10																									
; Copyright (C) 2007 Solstar Games, LLC. All rights reserved																	
; contact@solstargames.com																																																		
;																																																																#
; Programmer: Rob Williams																										
; Program: Realm Crafter Actors module
;																																
;This is a licensed product:
;BY USING THIS SOURCECODE, YOU ARE CONFIRMING YOUR ACCEPTANCE OF THE SOFTWARE AND AGREEING TO BECOME BOUND BY THE TERMS OF 
;THIS AGREEMENT. IF YOU DO NOT AGREE TO BE BOUND BY THESE TERMS, THEN DO NOT USE THE SOFTWARE.
;																		
;Licensee may NOT: 
; (i)   create any derivative works of the Engine, including translations Or localizations, other than Games;
; (ii)  redistribute, encumber, sell, rent, lease, sublicense, Or otherwise transfer rights To the Engine; or
; (iii) remove Or alter any trademark, logo, copyright Or other proprietary notices, legends, symbols Or labels in the Engine.
; (iv)   licensee may Not distribute the source code Or documentation To the engine in any manner, unless recipient also has a 
;       license To the Engine.													
; (v)  use the Software to develop any software or other technology having the same primary function as the Software, 
;       including but not limited to using the Software in any development or test procedure that seeks to develop like 
;       software or other technology, or to determine if such software or other technology performs in a similar manner as the
;       Software																																
;##############################################################################################################################
; Realm Crafter Inventories module by Rob W (rottbott@hotmail.com), August 2004

; Slot names
Const Slot_Weapon   = 1
Const Slot_Shield   = 2
Const Slot_Hat      = 3
Const Slot_Chest    = 4
Const Slot_Hand     = 5
Const Slot_Belt     = 6
Const Slot_Legs     = 7
Const Slot_Feet     = 8
Const Slot_Ring     = 9
Const Slot_Amulet   = 10
Const Slot_Backpack = 11

; Slot array indices
Const SlotI_Weapon   = 0
Const SlotI_Shield   = 1
Const SlotI_Hat      = 2
Const SlotI_Chest    = 3
Const SlotI_Hand     = 4
Const SlotI_Belt     = 5
Const SlotI_Legs     = 6
Const SlotI_Feet     = 7
Const SlotI_Ring1    = 8
Const SlotI_Ring2    = 9
Const SlotI_Ring3    = 10
Const SlotI_Ring4    = 11
Const SlotI_Amulet1  = 12
Const SlotI_Amulet2  = 13
Const SlotI_Backpack = 14
Const Slots_Inventory = 45

; Inventory object
Type Inventory
	Field Items.ItemInstance[Slots_Inventory]
	Field Amounts[Slots_Inventory]
	Field My_AttrID				; Two dimensional array for Attributes
	Field My_ID					; Required for MySQL
End Type

; Gets the overall armour level for an inventory
Function GetArmourLevel(I.Inventory)

	Local AP = 0
	For j = SlotI_Shield To SlotI_Feet
		If I\Items[j] <> Null
			If I\Items[j]\Item\ItemType = I_Armour
				If I\Items[j]\ItemHealth > 0 Then AP = AP + I\Items[j]\Item\ArmourLevel
			EndIf
		EndIf
	Next
	Return AP

End Function

; Gets the total mass contained in an inventory
Function InventoryMass(I.Inventory)

	Local Mass = 0
	For j = 0 To Slots_Inventory
		If I\Items[j] <> Null
			Mass = Mass + (I\Items[j]\Item\Mass * I\Amounts[j])
		EndIf
	Next
	Return Mass

End Function

; Drops an item from an inventory to the floor
Function InventoryDrop(A.ActorInstance, SlotFrom, Amount, TellServer = True)

	; Check the drop is legal
	If SlotFrom < 0 Or SlotFrom > Slots_Inventory Then Return False
	If A\Inventory\Amounts[SlotFrom] < Amount Or A\Inventory\Items[SlotFrom] = Null Then Return False

	; Remove from inventory
	I.ItemInstance = A\Inventory\Items[SlotFrom]
	A\Inventory\Amounts[SlotFrom] = A\Inventory\Amounts[SlotFrom] - Amount
	If A\Inventory\Amounts[SlotFrom] <= 0 Then A\Inventory\Items[SlotFrom] = Null

	; Tell server
	If TellServer = True
		RCE_Send(Connection, PeerToHost, P_InventoryUpdate, "D" + RCE_StrFromInt$(SlotFrom, 1) + RCE_StrFromInt$(Amount, 2), True)
	EndIf

	Return Handle(I)

End Function

; Moves items from one pile to another of the same item type
Function InventoryAdd(A.ActorInstance, SlotFrom, SlotTo, Amount, TellServer = True)

	; Check the addition is legal
	If SlotFrom < 0 Or SlotFrom > Slots_Inventory Or SlotTo < SlotI_Backpack Or SlotTo > Slots_Inventory Then Return False
	If A\Inventory\Amounts[SlotFrom] < 1 Or A\Inventory\Items[SlotFrom] = Null Then Return False
	If A\Inventory\Items[SlotTo] = Null Then Return False
	If ActorHasSlot(A\Actor, SlotFrom, A\Inventory\Items[SlotFrom]\Item) = False
		Return False
	ElseIf ActorHasSlot(A\Actor, SlotTo, A\Inventory\Items[SlotTo]\Item) = False
		Return False
	EndIf
	If ItemInstancesIdentical(A\Inventory\Items[SlotFrom], A\Inventory\Items[SlotTo]) = False Then Return False

	; Do it
	A\Inventory\Amounts[SlotTo] = A\Inventory\Amounts[SlotTo] + Amount
	A\Inventory\Amounts[SlotFrom] = A\Inventory\Amounts[SlotFrom] - Amount
	If A\Inventory\Amounts[SlotFrom] <= 0 Then FreeItemInstance(A\Inventory\Items[SlotFrom])

 	; Tell server
	If TellServer = True
		Pa$ = RCE_StrFromInt$(A\RuntimeID, 2) + RCE_StrFromInt$(SlotFrom, 1) + RCE_StrFromInt$(SlotTo, 1) + RCE_StrFromInt$(Amount, 2)
		RCE_Send(Connection, PeerToHost, P_InventoryUpdate, "A" + Pa$, True)
	EndIf

	Return True

End Function

; Moves items between inventory slots
Function InventorySwap(A.ActorInstance, SlotA, SlotB, Amount = 0, TellServer = True)

	; Check the swap is legal
	If SlotB < SlotI_Backpack And Amount > 1 Then Return False
	If SlotA < 0 Or SlotA > Slots_Inventory Or SlotB < 0 Or SlotB > Slots_Inventory Then Return False
	If A\Inventory\Items[SlotA] = Null Then Return False
	I.Item = A\Inventory\Items[SlotA]\Item
	If ActorHasSlot(A\Actor, SlotA, I) = False Or ActorHasSlot(A\Actor, SlotB, I) = False Then Return False
	If SlotsMatch(A\Inventory\Items[SlotA]\Item, SlotB) = False Then Return False
	If A\Inventory\Items[SlotB] <> Null
		If SlotsMatch(A\Inventory\Items[SlotB]\Item, SlotA) = False Then Return False
	EndIf

	; Swap them
	If Amount = 0 Or A\Inventory\Items[SlotB] <> Null
		; Do not allow multiple stacked items to go into a non backpack slot
		If (SlotA < SlotI_Backpack And A\Inventory\Amounts[SlotB] > 1) Or (SlotB < SlotI_Backpack And A\Inventory\Amounts[SlotA] > 1)
			Return False
		EndIf
		ItemA.ItemInstance = A\Inventory\Items[SlotA]
		AmountA = A\Inventory\Amounts[SlotA]
		A\Inventory\Items[SlotA] = A\Inventory\Items[SlotB]
		A\Inventory\Amounts[SlotA] = A\Inventory\Amounts[SlotB]
		A\Inventory\Items[SlotB] = ItemA
		A\Inventory\Amounts[SlotB] = AmountA
	; Move a certain amount only
	Else
		A\Inventory\Amounts[SlotB] = Amount
		A\Inventory\Amounts[SlotA] = A\Inventory\Amounts[SlotA] - Amount
		If A\Inventory\Amounts[SlotA] < 1
			A\Inventory\Items[SlotB] = A\Inventory\Items[SlotA]
			A\Inventory\Items[SlotA] = Null
		Else
			A\Inventory\Items[SlotB] = CopyItemInstance(A\Inventory\Items[SlotA])
		EndIf
	EndIf

 	; Tell server
	If TellServer = True
		Pa$ = RCE_StrFromInt$(A\RuntimeID, 2) + RCE_StrFromInt$(SlotA, 1) + RCE_StrFromInt$(SlotB, 1) + RCE_StrFromInt$(Amount, 2)
		RCE_Send(Connection, PeerToHost, P_InventoryUpdate, "S" + Pa$, True)
	EndIf

	Return True

End Function

; Returns true if the items specified are present
Function InventoryHasItem(I.Inventory, Item$, Amount)

	Item$ = Upper$(Item$)
	FoundAmount = 0
	For j = 0 To Slots_Inventory
		If I\Items[j] <> Null
			If Upper$(I\Items[j]\Item\Name$) = Item$
				FoundAmount = FoundAmount + I\Amounts[j]
				If FoundAmount >= Amount Then Return True
			EndIf
		EndIf
	Next
	Return False

End Function

; Checks if an actor has a particular slot index
Function ActorHasSlot(A.Actor, SlotI, I.Item)

	; If it's an equipped slot
	If SlotI < Slot_Backpack
		If I\ExclusiveRace$ <> ""
			; Allow even disabled equipment slots to be used if the item is exclusive to this race
			If Upper$(I\ExclusiveRace$) = Upper$(A\Race$)
				Return True
			; Never allow the slot if the item is exclusive to another race
			Else
				Return False
			EndIf
		EndIf

		; Never allow the slot if the item is exclusive to another race
		If I\ExclusiveClass$ <> ""
			If Upper$(I\ExclusiveClass$) <> Upper$(A\Class$) Then Return False
		EndIf
	EndIf

	; Check whether the slot is disabled
	Select SlotI
		Case SlotI_Weapon
			Return GetFlag(A\InventorySlots, Slot_Weapon - 1)
		Case SlotI_Shield
			Return GetFlag(A\InventorySlots, Slot_Shield - 1)
		Case SlotI_Hat
			Return GetFlag(A\InventorySlots, Slot_Hat - 1)
		Case SlotI_Chest
			Return GetFlag(A\InventorySlots, Slot_Chest - 1)
		Case SlotI_Hand
			Return GetFlag(A\InventorySlots, Slot_Hand - 1)
		Case SlotI_Belt
			Return GetFlag(A\InventorySlots, Slot_Belt - 1)
		Case SlotI_Legs
			Return GetFlag(A\InventorySlots, Slot_Legs - 1)
		Case SlotI_Feet
			Return GetFlag(A\InventorySlots, Slot_Feet - 1)
		Case SlotI_Ring1, SlotI_Ring2, SlotI_Ring3, SlotI_Ring4
			Return GetFlag(A\InventorySlots, Slot_Ring - 1)
		Case SlotI_Amulet1, SlotI_Amulet2
			Return GetFlag(A\InventorySlots, Slot_Amulet - 1)
		Default
			Return GetFlag(A\InventorySlots, Slot_Backpack - 1)
	End Select

End Function

; Checks an item matches a particular slot type
Function SlotsMatch(It.Item, SlotI)

	If SlotI >= SlotI_Backpack Then Return True
	Select It\ItemType
		Case I_Weapon
			If SlotI = SlotI_Weapon Then Return True
		Case I_Armour
			Select It\SlotType
				Case Slot_Shield
					If SlotI = SlotI_Shield Then Return True
				Case Slot_Hat
					If SlotI = SlotI_Hat Then Return True
				Case Slot_Chest
					If SlotI = SlotI_Chest Then Return True
				Case Slot_Hand
					If SlotI = SlotI_Hand Then Return True
				Case Slot_Belt
					If SlotI = SlotI_Belt Then Return True
				Case Slot_Legs
					If SlotI = SlotI_Legs Then Return True
				Case Slot_Feet
					If SlotI = SlotI_Feet Then Return True
			End Select
		Case I_Ring
			If It\SlotType = Slot_Ring
				If SlotI >= SlotI_Ring1 And SlotI <= SlotI_Ring4 Then Return True
			Else
				If SlotI >= SlotI_Amulet1 And SlotI <= SlotI_Amulet2 Then Return True
			EndIf
	End Select
	Return False

End Function