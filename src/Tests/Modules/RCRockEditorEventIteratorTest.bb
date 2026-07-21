Strict
EnableGC

; The RC Rock Editor is renderer-heavy, so this bounded source contract keeps
; BUTTONCHECK's F-UI event drain safe without loading the tool dependency graph.
; Dialog handlers can consume queue entries, so the cursor must restart from
; First Event after each delete instead of advancing a stale iterator.
Function RCRockEditorEventDrainRestartsFromFirst%()
	Local F.BBStream = ReadFile("Tools\\RC Rock Editor.bb")
	Local InFunction%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\\Tools\\RC Rock Editor.bb")
	If F = Null Then F = ReadFile("..\\..\\Tools\\RC Rock Editor.bb")
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function BUTTONCHECK()") > 0 Then InFunction = True
		If InFunction = True
			If Instr(Line$, "For e.Event = Each event") > 0 Then
				CloseFile F
				Return False
			EndIf
			If Stage < 6 And Instr(Line$, "Delete E") > 0 Then
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "Local E.Event = First Event") > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, "While E <> Null") > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, "Select E\EventId") > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, "Case GUI_MENUFILE_Exit") > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, "Case GUI_HELP_ABOUT") > 0 Then Stage = 5
			If Stage = 5 And Instr(Line$, "Case GUI_RIGHTWIN_GENERATE") > 0 Then Stage = 6
			If Stage = 6 And Instr(Line$, "Case GUI_MENUFILE_EXPORT") > 0 Then Stage = 7
			If Stage = 7 And Instr(Line$, "Case gui_rightwin_changetexture") > 0 Then Stage = 8
			If Stage = 8 And Instr(Line$, "Delete E") > 0 Then Stage = 9
			If Stage = 9 And Instr(Line$, "E = First Event") > 0 Then Stage = 10
			If Stage = 10 And Instr(Line$, "Wend") > 0 Then Stage = 11
			If Instr(Line$, "End Function") > 0
				CloseFile F
				Return Stage = 11
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Test testRCRockEditorEventDrainRestartsFromFirstAfterDelete()
	Assert(RCRockEditorEventDrainRestartsFromFirst%() = True)
End Test
