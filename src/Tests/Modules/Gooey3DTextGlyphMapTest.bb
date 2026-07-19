Strict
EnableGC

Function GooeyGlyphSourceContains%(Needle$)
	Local F.BBStream = ReadFile("Modules\Gooey_3D_Text.bb")
	Local Line$
	If F = Null Then Return False
	While Not Eof(F)
		Line = ReadLine$(F)
		If Instr(Line, Needle) > 0
			CloseFile F
			Return True
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Function ExpectedGooeyGlyphs$()
	Return " ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789,./\'#?<>[]();:!" + Chr$(163) + "$%^&*+-@~=_|"
End Function

Test testSpecialGlyphSlotsMatchTheHistoricalFontAtlas()
	Local Glyphs$ = ExpectedGooeyGlyphs$()
	Local PoundSlot% = Instr(Glyphs, "$") - 1
	Assert(Asc(Mid$(Glyphs, PoundSlot, 1)) = 163)
	Assert(Instr(Glyphs, "@") = Instr(Glyphs, "*") + 3)
	Assert(Instr(Glyphs, "=") = Instr(Glyphs, "@") + 2)
End Test

Test testGlyphSourceBuildsPoundAsOneByte()
	Local ReplacementBytes$ = Chr$(239) + Chr$(191) + Chr$(189)
	Assert(GooeyGlyphSourceContains%("Chr$(163)") = True)
	Assert(GooeyGlyphSourceContains%(ReplacementBytes) = False)
End Test
