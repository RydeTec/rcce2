Strict
EnableGC

; Source-contract regression for ClientNet's server-to-client inventory paths.
; ClientNet includes the complete renderer/network graph, so this test binds
; the receive-side validation order without pulling that graph into a unit test.

Function ClientNetSource$()
	Local Paths$[2]
	Paths[0] = "Modules\\ClientNet.bb"
	Paths[1] = "..\\Modules\\ClientNet.bb"
	Local i
	For i = 0 To 1
		Local F.BBStream = ReadFile(Paths[i])
		If F <> Null
			Local Source$ = ""
			While Not Eof(F)
				Source$ = Source$ + ReadLine$(F) + Chr$(10)
			Wend
			CloseFile F
			Return Source$
		EndIf
	Next
	Return ""
End Function

Function Between$(Source$, StartNeedle$, EndNeedle$)
	Local Start = Instr(Source$, StartNeedle$)
	If Start = 0 Then Return ""
	Local EndAt = Instr(Mid$(Source$, Start + Len(StartNeedle$)), EndNeedle$)
	If EndAt = 0 Then Return Mid$(Source$, Start)
	Return Mid$(Source$, Start, Len(StartNeedle$) + EndAt - 1)
End Function

Function ContainsInOrder%(Source$, FirstNeedle$, SecondNeedle$, ThirdNeedle$, FourthNeedle$)
	Local At = Instr(Source$, FirstNeedle$)
	If At = 0 Then Return False
	At = At + Len(FirstNeedle$)
	Local NextAt = Instr(Mid$(Source$, At), SecondNeedle$)
	If NextAt = 0 Then Return False
	At = At + NextAt - 1
	At = At + Len(SecondNeedle$)
	NextAt = Instr(Mid$(Source$, At), ThirdNeedle$)
	If NextAt = 0 Then Return False
	At = At + NextAt - 1
	At = At + Len(ThirdNeedle$)
	Return Instr(Mid$(Source$, At), FourthNeedle$) > 0
End Function

Function ExpectedSlotUsable%(SlotI, HasPlayer, HasInventory)
	If HasPlayer = False Then Return False
	If HasInventory = False Then Return False
	If SlotI < 0 Or SlotI > 45 Then Return False
	Return True
End Function

Function InventoryMember$(Member$)
	Return "Me" + Chr$(92) + "Inventory" + Chr$(92) + Member$
End Function

Function PacketLengthGuard$(Length)
	Return "If Len(M" + Chr$(92) + "MessageData$) < " + Length
End Function

Function UsableSlotGuard$(SlotName$)
	Return "If ClientInventorySlotUsable" + Chr$(40) + SlotName$ + Chr$(41)
End Function

Function ButtonSlot$(SlotName$)
	Return "BSlots" + Chr$(40) + SlotName$ + Chr$(41)
End Function

Const EquipmentVisualPayloadBytes% = 17

Function EquipmentVisualPayloadExact%(PayloadLen%)
	Return PayloadLen = EquipmentVisualPayloadBytes
End Function

Function EquipmentVisualLengthGuard$()
	Return "If Len(M" + Chr$(92) + "MessageData$) <> " + EquipmentVisualPayloadBytes
End Function

Test testClientInventorySlotGuardRejectsMissingPlayerAndOutOfRangeSlots()
	Local Source$ = ClientNetSource$()
	Local Guard$ = Between$(Source$, "Function ClientInventorySlotUsable", "End Function")
	Assert(Guard$ <> "")
	Assert(ContainsInOrder%(Guard$, "If Me = Null Then Return False", "If Me" + Chr$(92) + "Inventory = Null Then Return False", "If SlotI < 0 Or SlotI > Slots_Inventory Then Return False", "Return True") = True)
End Test

Test testClientInventorySlotBoundaries()
	Assert(ExpectedSlotUsable%(0, True, True) = True)
	Assert(ExpectedSlotUsable%(45, True, True) = True)
	Assert(ExpectedSlotUsable%(46, True, True) = False)
	Assert(ExpectedSlotUsable%(255, True, True) = False)
	Assert(ExpectedSlotUsable%(0, False, True) = False)
	Assert(ExpectedSlotUsable%(0, True, False) = False)
End Test

Test testItemHealthValidatesCompletePacketAndSlotBeforeInventoryDereference()
	Local Section$ = Between$(ClientNetSource$(), "Case P_ItemHealth", "Case P_SelectScenery")
	Assert(ContainsInOrder%(Section$, PacketLengthGuard$(3), "SlotI = RCE_IntFromStr", UsableSlotGuard$("SlotI"), InventoryMember$("Items[SlotI]")) = True)
End Test

Test testInventoryUpdateHAndTValidateBeforeInventoryOrButtonSlots()
	Local InventoryUpdate$ = Between$(ClientNetSource$(), "Case P_InventoryUpdate", "Case P_StandardUpdate")
	Local Health$ = Between$(InventoryUpdate$, "Case " + Chr$(34) + "H" + Chr$(34), "Case " + Chr$(34) + "T" + Chr$(34))
	Local Taken$ = Between$(InventoryUpdate$, "Case " + Chr$(34) + "T" + Chr$(34), "Case " + Chr$(34) + "R" + Chr$(34))
	Assert(ContainsInOrder%(Health$, PacketLengthGuard$(3), "SlotI = RCE_IntFromStr", UsableSlotGuard$("SlotI"), InventoryMember$("Items[SlotI]")) = True)
	Assert(ContainsInOrder%(Taken$, PacketLengthGuard$(4), "SlotI = RCE_IntFromStr", UsableSlotGuard$("SlotI"), ButtonSlot$("SlotI")) = True)
End Test

Test testInventoryUpdateReceiveValidatesSlotBeforeDroppedItemMutation()
	Local InventoryUpdate$ = Between$(ClientNetSource$(), "Case P_InventoryUpdate", "Case P_StandardUpdate")
	Local Received$ = Between$(InventoryUpdate$, "Case " + Chr$(34) + "R" + Chr$(34), "Case " + Chr$(34) + "P" + Chr$(34))
	Assert(ContainsInOrder%(Received$, PacketLengthGuard$(6), "i = RCE_IntFromStr", UsableSlotGuard$("i"), "For DItem.DroppedItem = Each DroppedItem") = True)
End Test

Test testEquipmentVisualPayloadRequiresExactly17Bytes()
	Assert(EquipmentVisualPayloadExact%(0) = False)
	Assert(EquipmentVisualPayloadExact%(16) = False)
	Assert(EquipmentVisualPayloadExact%(17) = True)
	Assert(EquipmentVisualPayloadExact%(18) = False)
End Test

Test testEquipmentVisualLengthGuardPrecedesParseAndVisualMutation()
	Local InventoryUpdate$ = Between$(ClientNetSource$(), "Case P_InventoryUpdate", "Case P_StandardUpdate")
	Local Equipment$ = Between$(InventoryUpdate$, "Case " + Chr$(34) + "O" + Chr$(34), "Case " + Chr$(34) + "G" + Chr$(34))
	Assert(ContainsInOrder%(Equipment$, EquipmentVisualLengthGuard$(), "RuntimeID = RCE_IntFromStr", "FreeItemInstance(A" + Chr$(92) + "Inventory" + Chr$(92) + "Items[SlotI_Weapon])", "UpdateActorItems(A)") = True)
End Test
