Strict
EnableGC

; Source-contract regression for ClientNet's fixed P_StatUpdate frames.
; The live client pulls in the renderer/network graph, so bind the receive
; boundary directly without requiring that graph in this focused test.

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

Function ContainsInOrder5%(Source$, FirstNeedle$, SecondNeedle$, ThirdNeedle$, FourthNeedle$, FifthNeedle$)
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
	NextAt = Instr(Mid$(Source$, At), FourthNeedle$)
	If NextAt = 0 Then Return False
	At = At + NextAt - 1
	At = At + Len(FourthNeedle$)
	Return Instr(Mid$(Source$, At), FifthNeedle$) > 0
End Function

Function StatPayloadExact%(SubCode$, PayloadLen%)
	Select SubCode$
		Case "A", "M"
			Return PayloadLen = 6
		Case "R"
			Return PayloadLen = 5
	End Select
	Return False
End Function

Function StatLengthGuard$(PayloadLen%)
	Return "If Len(M" + Chr$(92) + "MessageData$) <> " + PayloadLen
End Function

Function AttributeValueMutation$()
	Return "A" + Chr$(92) + "Attributes" + Chr$(92) + "Value[Attribute]"
End Function

Function AttributeMaximumMutation$()
	Return "A" + Chr$(92) + "Attributes" + Chr$(92) + "Maximum[Attribute]"
End Function

Function ReputationMutation$()
	Return "A" + Chr$(92) + "Reputation = RCE_SignedShortFromStr"
End Function

Test testStatUpdatePayloadLengthsAreSubcodeSpecific()
	Assert(StatPayloadExact%("A", 5) = False)
	Assert(StatPayloadExact%("A", 6) = True)
	Assert(StatPayloadExact%("A", 7) = False)
	Assert(StatPayloadExact%("M", 5) = False)
	Assert(StatPayloadExact%("M", 6) = True)
	Assert(StatPayloadExact%("R", 4) = False)
	Assert(StatPayloadExact%("R", 5) = True)
	Assert(StatPayloadExact%("R", 6) = False)
	Assert(StatPayloadExact%("X", 6) = False)
End Test

Test testStatUpdateGuardsBeforeDecodeAndMutation()
	Local Section$ = Between$(ClientNetSource$(), "Case P_StatUpdate", "Case P_ScriptInput")
	Assert(ContainsInOrder5%(Section$, "Case " + Chr$(34) + "A" + Chr$(34), StatLengthGuard$(6), "Else", "RuntimeIDList", AttributeValueMutation$()) = True)
	Assert(ContainsInOrder5%(Section$, "Case " + Chr$(34) + "M" + Chr$(34), StatLengthGuard$(6), "Else", "RuntimeIDList", AttributeMaximumMutation$()) = True)
	Assert(ContainsInOrder5%(Section$, "Case " + Chr$(34) + "R" + Chr$(34), StatLengthGuard$(5), "Else", "RuntimeIDList", ReputationMutation$()) = True)
End Test

Test testStatUpdateUnknownSubcodesRemainNoOps()
	Local Section$ = Between$(ClientNetSource$(), "Case P_StatUpdate", "Case P_ScriptInput")
	Assert(Instr(Section$, "Case Else") = 0)
End Test
