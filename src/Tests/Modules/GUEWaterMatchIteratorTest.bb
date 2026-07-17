Strict
EnableGC

; Source-contract regression for the zone-load water reconciliation loop. GUE
; pulls the full editor graph, so pin the bounded cleanup ordering directly.

Function SectionLine%(Path$, SectionStart$, SectionEnd$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local LineNumber% = 0
	Local InSection% = False
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return 0
	While Not Eof(F)
		LineNumber = LineNumber + 1
		Line = ReadLine$(F)
		If InSection = False
			If Instr(Line$, SectionStart$) > 0 Then InSection = True
		Else
			If Instr(Line$, SectionEnd$) > 0
				CloseFile F
				Return 0
			EndIf
			If Instr(Line$, Needle$) > 0
				CloseFile F
				Return LineNumber
			EndIf
		EndIf
	Wend
	CloseFile F
	Return 0
End Function

Test testGUEWaterReconciliationCapturesTheSuccessorBeforeCleanup()
	Local SectionStart$ = "; Match client water with server water"
	Local SectionEnd$ = "; Load meshes for server side parts"
	Assert(SectionLine%("GUE.bb", SectionStart$, SectionEnd$, "For W.Water = Each Water") = 0)

	Local FirstLine% = SectionLine%("GUE.bb", SectionStart$, SectionEnd$, "W = First Water")
	Local CaptureLine% = SectionLine%("GUE.bb", SectionStart$, SectionEnd$, "WNext = After W")
	Local UnloadLine% = SectionLine%("GUE.bb", SectionStart$, SectionEnd$, "UnloadTexture(W\TexID)")
	Local FreeTextureLine% = SectionLine%("GUE.bb", SectionStart$, SectionEnd$, "FreeTexture(W\TexHandle)")
	Local FreeEntityLine% = SectionLine%("GUE.bb", SectionStart$, SectionEnd$, "FreeEntity(W\EN)")
	Local DeleteLine% = SectionLine%("GUE.bb", SectionStart$, SectionEnd$, "Delete(W)")
	Local AdvanceLine% = SectionLine%("GUE.bb", SectionStart$, SectionEnd$, "W = WNext")

	Assert(FirstLine > 0)
	Assert(CaptureLine > FirstLine)
	Assert(UnloadLine > CaptureLine)
	Assert(FreeTextureLine > UnloadLine)
	Assert(FreeEntityLine > FreeTextureLine)
	Assert(DeleteLine > FreeEntityLine)
	Assert(AdvanceLine > DeleteLine)
End Test
