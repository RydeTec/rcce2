Strict
EnableGC

; FUI_DeleteGadget recursively tears down every child owned by a Window. A
; recursive delete can remove nodes beyond the current Type-list cursor, so
; each bounded walk must restart after delete and use After only when it keeps
; the current node.

Function WindowChildTeardownUsesRestartWalk%(Path$, Start$, LegacyFor$, FirstCursor$, WhileCursor$, OwnerCheck$, DeleteChild$, Restart$, Advance$)
	Local F.BBStream = ReadFile(Path$)
	Local InWindow%, InWalk%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function FUI_DeleteGadget( ID )") > 0 Then InWindow = True
		If InWindow = True And InWalk = False And Instr(Line$, Start$) > 0 Then InWalk = True
		If InWalk = True
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

Function AssertWindowChildTeardown%(Start$, LegacyFor$, FirstCursor$, WhileCursor$, OwnerCheck$, DeleteChild$, Restart$, Advance$)
	Return WindowChildTeardownUsesRestartWalk%("Modules\F-UI.bb", Start$, LegacyFor$, FirstCursor$, WhileCursor$, OwnerCheck$, DeleteChild$, Restart$, Advance$)
End Function

Test testFUIWindowChildTeardownsRestartAfterRecursiveDelete()
	Assert(AssertWindowChildTeardown%("win.Window = Object.Window( ID )", "For reg.Region = Each Region", "Local reg.Region = First Region", "While reg <> Null", "If reg\Owner = win", "FUI_DeleteGadget( Handle( reg ) )", "reg = First Region", "reg = After reg") = True)
	Assert(AssertWindowChildTeardown%("win.Window = Object.Window( ID )", "For Tab.Tab = Each Tab", "Local Tab.Tab = First Tab", "While Tab <> Null", "If Tab\Owner = win", "FUI_DeleteGadget( Handle( Tab ) )", "Tab = First Tab", "Tab = After Tab") = True)
	Assert(AssertWindowChildTeardown%("win.Window = Object.Window( ID )", "For pan.Panel = Each Panel", "Local pan.Panel = First Panel", "While pan <> Null", "If pan\Owner = win", "FUI_DeleteGadget( Handle( pan ) )", "pan = First Panel", "pan = After pan") = True)
	Assert(AssertWindowChildTeardown%("win.Window = Object.Window( ID )", "For btn.Button = Each Button", "Local btn.Button = First Button", "While btn <> Null", "If btn\Owner = win", "FUI_DeleteGadget( Handle( btn ) )", "btn = First Button", "btn = After btn") = True)
	Assert(AssertWindowChildTeardown%("win.Window = Object.Window( ID )", "For chk.CheckBox = Each CheckBox", "Local chk.CheckBox = First CheckBox", "While chk <> Null", "If chk\Owner = win", "FUI_DeleteGadget( Handle( chk ) )", "chk = First CheckBox", "chk = After chk") = True)
	Assert(AssertWindowChildTeardown%("win.Window = Object.Window( ID )", "For cbo.ComboBox = Each ComboBox", "Local cbo.ComboBox = First ComboBox", "While cbo <> Null", "If cbo\Owner = win", "FUI_DeleteGadget( Handle( cbo ) )", "cbo = First ComboBox", "cbo = After cbo") = True)
	Assert(AssertWindowChildTeardown%("win.Window = Object.Window( ID )", "For grp.GroupBox = Each GroupBox", "Local grp.GroupBox = First GroupBox", "While grp <> Null", "If grp\Owner = win", "FUI_DeleteGadget( Handle( grp ) )", "grp = First GroupBox", "grp = After grp") = True)
	Assert(AssertWindowChildTeardown%("win.Window = Object.Window( ID )", "For img.ImageBox = Each ImageBox", "Local img.ImageBox = First ImageBox", "While img <> Null", "If img\Owner = win", "FUI_DeleteGadget( Handle( img ) )", "img = First ImageBox", "img = After img") = True)
	Assert(AssertWindowChildTeardown%("win.Window = Object.Window( ID )", "For lbl.Label = Each Label", "Local lbl.Label = First Label", "While lbl <> Null", "If lbl\Owner = win", "FUI_DeleteGadget( Handle( lbl ) )", "lbl = First Label", "lbl = After lbl") = True)
	Assert(AssertWindowChildTeardown%("win.Window = Object.Window( ID )", "For lst.ListBox = Each ListBox", "Local lst.ListBox = First ListBox", "While lst <> Null", "If lst\Owner = win", "FUI_DeleteGadget( Handle( lst ) )", "lst = First ListBox", "lst = After lst") = True)
	Assert(AssertWindowChildTeardown%("win.Window = Object.Window( ID )", "For prg.ProgressBar = Each ProgressBar", "Local prg.ProgressBar = First ProgressBar", "While prg <> Null", "If prg\Owner = win", "FUI_DeleteGadget( Handle( prg ) )", "prg = First ProgressBar", "prg = After prg") = True)
	Assert(AssertWindowChildTeardown%("win.Window = Object.Window( ID )", "For rad.Radio = Each Radio", "Local rad.Radio = First Radio", "While rad <> Null", "If rad\Owner = win", "FUI_DeleteGadget( Handle( rad ) )", "rad = First Radio", "rad = After rad") = True)
	Assert(AssertWindowChildTeardown%("win.Window = Object.Window( ID )", "For scroll.ScrollBar = Each ScrollBar", "Local scroll.ScrollBar = First ScrollBar", "While scroll <> Null", "If scroll\Owner = win", "FUI_DeleteGadget( Handle( scroll ) )", "scroll = First ScrollBar", "scroll = After scroll") = True)
	Assert(AssertWindowChildTeardown%("win.Window = Object.Window( ID )", "For sld.Slider = Each Slider", "Local sld.Slider = First Slider", "While sld <> Null", "If sld\Owner = win", "FUI_DeleteGadget( Handle( sld ) )", "sld = First Slider", "sld = After sld") = True)
	Assert(AssertWindowChildTeardown%("win.Window = Object.Window( ID )", "For spn.Spinner = Each Spinner", "Local spn.Spinner = First Spinner", "While spn <> Null", "If spn\Owner = win", "FUI_DeleteGadget( Handle( spn ) )", "spn = First Spinner", "spn = After spn") = True)
	Assert(AssertWindowChildTeardown%("win.Window = Object.Window( ID )", "For txt.TextBox = Each TextBox", "Local txt.TextBox = First TextBox", "While txt <> Null", "If txt\Owner = win", "FUI_DeleteGadget( Handle( txt ) )", "txt = First TextBox", "txt = After txt") = True)
	Assert(AssertWindowChildTeardown%("win.Window = Object.Window( ID )", "For tree.TreeView = Each TreeView", "Local tree.TreeView = First TreeView", "While tree <> Null", "If tree\Owner = win", "FUI_DeleteGadget( Handle( tree ) )", "tree = First TreeView", "tree = After tree") = True)
	Assert(AssertWindowChildTeardown%("win.Window = Object.Window( ID )", "For view.View = Each View", "Local view.View = First View", "While view <> Null", "If view\Owner = win", "FUI_DeleteGadget( Handle( view ) )", "view = First View", "view = After view") = True)
	Assert(AssertWindowChildTeardown%("mnut.MenuTitle = Object.MenuTitle( ID )", "For mnui.MenuItem = Each MenuItem", "Local mnui.MenuItem = First MenuItem", "While mnui <> Null", "If mnui\Owner = mnut", "FUI_DeleteGadget( Handle( mnui ) )", "mnui = First MenuItem", "mnui = After mnui") = True)
	Assert(AssertWindowChildTeardown%("mnui.MenuItem = Object.MenuItem( ID )", "For mnui2.MenuItem = Each MenuItem", "Local mnui2.MenuItem = First MenuItem", "While mnui2 <> Null", "If mnui2\Parent = mnui", "FUI_DeleteGadget( Handle( mnui2 ) )", "mnui2 = First MenuItem", "mnui2 = After mnui2") = True)
	Assert(AssertWindowChildTeardown%("cmnu.ContextMenu = Object.ContextMenu( ID )", "For cmnui.ContextMenuItem = Each ContextMenuItem", "Local cmnui.ContextMenuItem = First ContextMenuItem", "While cmnui <> Null", "If cmnui\Owner = cmnu", "FUI_DeleteGadget( Handle( cmnui ) )", "cmnui = First ContextMenuItem", "cmnui = After cmnui") = True)
	Assert(AssertWindowChildTeardown%("cmnui.ContextMenuItem = Object.ContextMenuItem( ID )", "For cmnui2.ContextMenuItem = Each ContextMenuItem", "Local cmnui2.ContextMenuItem = First ContextMenuItem", "While cmnui2 <> Null", "If cmnui2\Parent = cmnui", "FUI_DeleteGadget( Handle( cmnui2 ) )", "cmnui2 = First ContextMenuItem", "cmnui2 = After cmnui2") = True)
End Test
