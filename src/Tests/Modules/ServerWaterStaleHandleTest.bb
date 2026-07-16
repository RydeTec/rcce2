Strict
EnableGC

; GameServer owns the server actor/world graph, so this focused source
; contract protects the delayed ServerWater re-lookup without loading that
; graph into the headless test harness. BlitzForge And is non-short-circuit:
; the Null branch and the SW field read must therefore be separate nested Ifs.

Function LeadingTabs%(Line$)
	Local Count%, Pos% = 1
	While Pos <= Len(Line$)
		If Mid$(Line$, Pos, 1) <> Chr$(9) Then Exit
		Count = Count + 1
		Pos = Pos + 1
	Wend
	Return Count
End Function

Function HasSafeDelayedWaterDamageGuard%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$, Trimmed$
	Local Stage%, GuardIndent%
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		Trimmed$ = Trim$(Line$)
		If Instr(Trimmed$, "If SW <> Null And") > 0
			CloseFile F
			Return False
		EndIf
		Select Stage
			Case 0
				If Instr(Line$, "SW = Object.ServerWater(Underwater)") > 0 Then Stage = 1
			Case 1
				If Trimmed$ = "If SW <> Null"
					GuardIndent = LeadingTabs(Line$)
					Stage = 2
				ElseIf Left$(Trimmed$, 1) <> ";" And Instr(Line$, "SW\Damage") > 0
					CloseFile F
					Return False
				EndIf
			Case 2
				If Trimmed$ = "If SW\Damage > 0"
					If LeadingTabs(Line$) <> GuardIndent + 1
						CloseFile F
						Return False
					EndIf
					Stage = 3
				ElseIf Left$(Trimmed$, 1) <> ";" And Instr(Line$, "SW\Damage") > 0
					CloseFile F
					Return False
				EndIf
			Case 3
				If Instr(Line$, "Damage = SW\Damage - (AI\Resistances[SW\DamageType] - 100)") > 0
					If LeadingTabs(Line$) <> GuardIndent + 2
						CloseFile F
						Return False
					EndIf
					Stage = 4
				EndIf
			Case 4
				If Trimmed$ = "EndIf" And LeadingTabs(Line$) = GuardIndent + 1 Then Stage = 5
			Case 5
				If Trimmed$ = "EndIf" And LeadingTabs(Line$) = GuardIndent
					CloseFile F
					Return True
				EndIf
		End Select
	Wend

	CloseFile F
	Return False
End Function

Test testDelayedWaterDamageGuardsStaleServerWater()
	Assert(HasSafeDelayedWaterDamageGuard%("src\Modules\GameServer.bb") = True)
End Test
