Strict
EnableGC

; FUI_DeleteGadget recursively tears down every child owned by a Window. A
; recursive delete can remove nodes beyond the current Type-list cursor, so
; each bounded walk must restart after delete and use After only when it keeps
; the current node.

Function WindowChildTeardownUsesRestartWalk%(Path$, LegacyFor$, FirstCursor$, WhileCursor$, OwnerCheck$, DeleteChild$, Restart$, Advance$)
	Local F.BBStream = ReadFile(Path$)
	Local InWindow%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function FUI_DeleteGadget( ID )") > 0 Then InWindow = True
		If InWindow = True
			If Instr(Line$, "mnut.MenuTitle = Object.MenuTitle( ID )") > 0 Then Exit
			If Instr(Line$, LegacyFor$) > 0
				CloseFile F
				Return False
			EndIf
			If Stage < 6 And Instr(Line$, Advance$) > 0
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, FirstCursor$) > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, WhileCursor$) > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, OwnerCheck$) > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, DeleteChild$) > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, Restart$) > 0 Then Stage = 5
			If Stage = 5 And Instr(Line$, "Else") > 0 Then Stage = 6
			If Stage = 6 And Instr(Line$, Advance$) > 0 Then Stage = 7
			If Stage = 7 And Instr(Line$, "EndIf") > 0 Then Stage = 8
			If Stage = 8 And Instr(Line$, "Wend") > 0
				CloseFile F
				Return True
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Function AssertWindowChildTeardown%(LegacyFor$, FirstCursor$, WhileCursor$, OwnerCheck$, DeleteChild$, Restart$, Advance$)
	Return WindowChildTeardownUsesRestartWalk%("Modules\F-UI.bb", LegacyFor$, FirstCursor$, WhileCursor$, OwnerCheck$, DeleteChild$, Restart$, Advance$)
End Function

Test testFUIWindowChildTeardownsRestartAfterRecursiveDelete()
	Assert(AssertWindowChildTeardown%("For reg.Region = Each Region", "Local reg.Region = First Region", "While reg <> Null", "If reg\Owner = win", "FUI_DeleteGadget( Handle( reg ) )", "reg = First Region", "reg = After reg") = True)
	Assert(AssertWindowChildTeardown%("For Tab.Tab = Each Tab", "Local Tab.Tab = First Tab", "While Tab <> Null", "If Tab\Owner = win", "FUI_DeleteGadget( Handle( Tab ) )", "Tab = First Tab", "Tab = After Tab") = True)
	Assert(AssertWindowChildTeardown%("For pan.Panel = Each Panel", "Local pan.Panel = First Panel", "While pan <> Null", "If pan\Owner = win", "FUI_DeleteGadget( Handle( pan ) )", "pan = First Panel", "pan = After pan") = True)
	Assert(AssertWindowChildTeardown%("For btn.Button = Each Button", "Local btn.Button = First Button", "While btn <> Null", "If btn\Owner = win", "FUI_DeleteGadget( Handle( btn ) )", "btn = First Button", "btn = After btn") = True)
	Assert(AssertWindowChildTeardown%("For chk.CheckBox = Each CheckBox", "Local chk.CheckBox = First CheckBox", "While chk <> Null", "If chk\Owner = win", "FUI_DeleteGadget( Handle( chk ) )", "chk = First CheckBox", "chk = After chk") = True)
	Assert(AssertWindowChildTeardown%("For cbo.ComboBox = Each ComboBox", "Local cbo.ComboBox = First ComboBox", "While cbo <> Null", "If cbo\Owner = win", "FUI_DeleteGadget( Handle( cbo ) )", "cbo = First ComboBox", "cbo = After cbo") = True)
	Assert(AssertWindowChildTeardown%("For grp.GroupBox = Each GroupBox", "Local grp.GroupBox = First GroupBox", "While grp <> Null", "If grp\Owner = win", "FUI_DeleteGadget( Handle( grp ) )", "grp = First GroupBox", "grp = After grp") = True)
	Assert(AssertWindowChildTeardown%("For img.ImageBox = Each ImageBox", "Local img.ImageBox = First ImageBox", "While img <> Null", "If img\Owner = win", "FUI_DeleteGadget( Handle( img ) )", "img = First ImageBox", "img = After img") = True)
	Assert(AssertWindowChildTeardown%("For lbl.Label = Each Label", "Local lbl.Label = First Label", "While lbl <> Null", "If lbl\Owner = win", "FUI_DeleteGadget( Handle( lbl ) )", "lbl = First Label", "lbl = After lbl") = True)
	Assert(AssertWindowChildTeardown%("For lst.ListBox = Each ListBox", "Local lst.ListBox = First ListBox", "While lst <> Null", "If lst\Owner = win", "FUI_DeleteGadget( Handle( lst ) )", "lst = First ListBox", "lst = After lst") = True)
	Assert(AssertWindowChildTeardown%("For prg.ProgressBar = Each ProgressBar", "Local prg.ProgressBar = First ProgressBar", "While prg <> Null", "If prg\Owner = win", "FUI_DeleteGadget( Handle( prg ) )", "prg = First ProgressBar", "prg = After prg") = True)
	Assert(AssertWindowChildTeardown%("For rad.Radio = Each Radio", "Local rad.Radio = First Radio", "While rad <> Null", "If rad\Owner = win", "FUI_DeleteGadget( Handle( rad ) )", "rad = First Radio", "rad = After rad") = True)
	Assert(AssertWindowChildTeardown%("For scroll.ScrollBar = Each ScrollBar", "Local scroll.ScrollBar = First ScrollBar", "While scroll <> Null", "If scroll\Owner = win", "FUI_DeleteGadget( Handle( scroll ) )", "scroll = First ScrollBar", "scroll = After scroll") = True)
	Assert(AssertWindowChildTeardown%("For sld.Slider = Each Slider", "Local sld.Slider = First Slider", "While sld <> Null", "If sld\Owner = win", "FUI_DeleteGadget( Handle( sld ) )", "sld = First Slider", "sld = After sld") = True)
	Assert(AssertWindowChildTeardown%("For spn.Spinner = Each Spinner", "Local spn.Spinner = First Spinner", "While spn <> Null", "If spn\Owner = win", "FUI_DeleteGadget( Handle( spn ) )", "spn = First Spinner", "spn = After spn") = True)
	Assert(AssertWindowChildTeardown%("For txt.TextBox = Each TextBox", "Local txt.TextBox = First TextBox", "While txt <> Null", "If txt\Owner = win", "FUI_DeleteGadget( Handle( txt ) )", "txt = First TextBox", "txt = After txt") = True)
	Assert(AssertWindowChildTeardown%("For tree.TreeView = Each TreeView", "Local tree.TreeView = First TreeView", "While tree <> Null", "If tree\Owner = win", "FUI_DeleteGadget( Handle( tree ) )", "tree = First TreeView", "tree = After tree") = True)
	Assert(AssertWindowChildTeardown%("For view.View = Each View", "Local view.View = First View", "While view <> Null", "If view\Owner = win", "FUI_DeleteGadget( Handle( view ) )", "view = First View", "view = After view") = True)
End Test
