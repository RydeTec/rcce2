Strict
EnableGC

; Source-contract regression for ClientNet's server-to-client trade updates.
; The full network/UI graph is not standalone-testable, so bind the receive
; frame lengths and ordering before item/UI mutation.

Function ClientNetTradingSource$()
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

Function AppearsAfter%(Source$, FirstNeedle$, SecondNeedle$)
	Local At = Instr(Source$, FirstNeedle$)
	If At = 0 Then Return False
	At = At + Len(FirstNeedle$)
	Return Instr(Mid$(Source$, At), SecondNeedle$) > 0
End Function

Function TradeFrameIsUsable%(PayloadLen, Amount)
	If PayloadLen = 3 And Amount = 0 Then Return True
	If PayloadLen = 86 And Amount > 0 Then Return True
	Return False
End Function

Function PacketLength$()
	Return "Len(M" + Chr$(92) + "MessageData$)"
End Function

Test testTradeUpdateFrameBoundaries()
	Assert(TradeFrameIsUsable%(0, 0) = False)
	Assert(TradeFrameIsUsable%(2, 0) = False)
	Assert(TradeFrameIsUsable%(3, 0) = True)
	Assert(TradeFrameIsUsable%(4, 0) = False)
	Assert(TradeFrameIsUsable%(85, 1) = False)
	Assert(TradeFrameIsUsable%(86, 1) = True)
	Assert(TradeFrameIsUsable%(87, 1) = False)
	Assert(TradeFrameIsUsable%(86, 0) = False)
End Test

Test testTradeUpdateValidatesLengthBeforeDecodingOrUiMutation()
	Local Section$ = Between$(ClientNetTradingSource$(), "Case P_UpdateTrading", "Case P_CloseTrading")
	Local OuterGuard$ = "If TradingVisible = True And (" + PacketLength$() + " = 3 Or " + PacketLength$() + " = 86)"
	Local RemovalGuard$ = "If Amount = 0 And " + PacketLength$() + " = 3"
	Local AddGuard$ = "ElseIf Amount > 0 And " + PacketLength$() + " = 86"
	Local Decode$ = "II.ItemInstance = ItemInstanceFromString(Mid$(M" + Chr$(92) + "MessageData$, 4))"
	Local ParsedItemGuard$ = "If II <> Null"
	Local SlotMutation$ = "TradeItems(i) = II"
	Local UiMutation$ = "EntityTexture GYB" + Chr$(92) + "Gadget" + Chr$(92) + "EN, GetTexture(TradeItems(i)" + Chr$(92) + "Item" + Chr$(92) + "ThumbnailTexID)"
	Assert(AppearsAfter%(Section$, OuterGuard$, "Slot = RCE_IntFromStr") = True)
	Assert(AppearsAfter%(Section$, "Slot = RCE_IntFromStr", "Amount = RCE_IntFromStr") = True)
	Assert(AppearsAfter%(Section$, "Amount = RCE_IntFromStr", RemovalGuard$) = True)
	Assert(AppearsAfter%(Section$, RemovalGuard$, AddGuard$) = True)
	Assert(AppearsAfter%(Section$, AddGuard$, Decode$) = True)
	Assert(AppearsAfter%(Section$, Decode$, ParsedItemGuard$) = True)
	Assert(AppearsAfter%(Section$, ParsedItemGuard$, SlotMutation$) = True)
	Assert(AppearsAfter%(Section$, SlotMutation$, UiMutation$) = True)
End Test
