Strict
EnableGC

; ServerLoadArea cannot be included in a focused test without its complete
; world/server dependency graph. This contract therefore pins the persistence
; boundary directly, while the small mirror below exercises the count math.

Const WaterRecordBytes = 24

Function WaterRecordCountFits%(Waters%, RemainingBytes%)
	If Waters < 0 Then Return False
	If Waters > RemainingBytes / WaterRecordBytes Then Return False
	Return True
End Function

Function ServerLoadAreaUsesBoundedWaterCount%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InSection%, Stage%
	Local Line$

	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = Trim$(ReadLine$(F))
		If Line$ = "Function ServerLoadArea.Area(Name$)" Then InSection = True
		If InSection = True
			If Line$ = "End Function" Then Exit
			If Stage = 0 And Line$ = "Local AreaWaterRecordBytes = 24" Then Stage = 1
			ElseIf Stage = 1 And Instr(Line$, "Local AreaPath$ = ") = 1 Then Stage = 2
			ElseIf Stage = 2 And Line$ = "F = ReadFile(AreaPath$)" Then Stage = 3
			ElseIf Stage = 3 And Line$ = "Waters = ReadShort(F)" Then Stage = 4
			ElseIf Stage = 4 And Line$ = "If Waters < 0 Or Waters > (FileSize(AreaPath$) - FilePos(F)) / AreaWaterRecordBytes Then Waters = 0" Then Stage = 5
			ElseIf Stage = 5 And Line$ = "For i = 1 To Waters" Then Stage = 6
			ElseIf Stage = 6 And Line$ = "W.ServerWater = New ServerWater" Then Stage = 7
			ElseIf Stage = 7 And Line$ = "ServerWaterAttach(W, A)" Then Stage = 8
			EndIf
		EndIf
	Wend

	CloseFile F
	Return Stage = 8
End Function

Test testWaterRecordCountAllowsExactFixedWidthPayload()
	Assert(WaterRecordCountFits%(0, 0) = True)
	Assert(WaterRecordCountFits%(1, WaterRecordBytes) = True)
	Assert(WaterRecordCountFits%(2, WaterRecordBytes * 2) = True)
End Test

Test testWaterRecordCountRejectsNegativeAndTruncatedPayloads()
	Assert(WaterRecordCountFits%(-1, WaterRecordBytes) = False)
	Assert(WaterRecordCountFits%(1, WaterRecordBytes - 1) = False)
	Assert(WaterRecordCountFits%(2, WaterRecordBytes * 2 - 1) = False)
	Assert(WaterRecordCountFits%(32767, 0) = False)
End Test

Test testServerLoadAreaGuardsWaterCountBeforeAllocation()
	Assert(ServerLoadAreaUsesBoundedWaterCount%("Modules\\ServerAreas.bb") = True)
End Test
