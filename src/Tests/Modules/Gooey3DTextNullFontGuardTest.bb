Strict
EnableGC

Function Gooey3DTextSourceContains%(Needle$)
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

Test test3DTextNullFontGuardsDoNotUseEagerBooleanDereference()
	Assert(Gooey3DTextSourceContains%("If T\Font = Null Then Return 0.0") = True)
	Assert(Gooey3DTextSourceContains%("If T\Font = Null Then Return False") = True)
	Assert(Gooey3DTextSourceContains%("If T\Font = Null Or T\Font\Font_Width# = 0.0") = False)
End Test
