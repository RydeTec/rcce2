Strict
EnableGC

; A Region owns child F-UI gadgets. FUI_DeleteGadget can recursively remove
; arbitrary later nodes, so Region teardown must restart its Type-list walk
; after deleting a child and advance with After only when the child survives.

Function RegionChildTeardownUsesRestartWalk%(Path$, Start$, LegacyFor$, FirstCursor$, WhileCursor$, ParentCheck$, DeleteChild$, Restart$, Advance$)
	Local F.BBStream = ReadFile(Path$)
	Local InRegion%, InWalk%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function FUI_DeleteGadget( ID )") > 0 Then InRegion = True
		If InRegion = True And InWalk = False And Instr(Line$, Start$) > 0 Then InWalk = True
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
			If Stage = 2 And Instr(Line$, ParentCheck$) > 0 Then Stage = 3
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

Function AssertRegionChildTeardown%(Start$, LegacyFor$, FirstCursor$, WhileCursor$, ParentCheck$, DeleteChild$, Restart$, Advance$)
	Return RegionChildTeardownUsesRestartWalk%("Modules\F-UI.bb", Start$, LegacyFor$, FirstCursor$, WhileCursor$, ParentCheck$, DeleteChild$, Restart$, Advance$)
End Function

Test testFUIRegionChildTeardownsRestartAfterRecursiveDelete()
	Assert(AssertRegionChildTeardown%("reg.Region = Object.Region( ID )", "For reg2.Region = Each Region", "Local regionChild.Region = First Region", "While regionChild <> Null", "If regionChild\Parent = ID", "FUI_DeleteGadget( Handle( regionChild ) )", "regionChild = First Region", "regionChild = After regionChild") = True)
	Assert(AssertRegionChildTeardown%("reg.Region = Object.Region( ID )", "For Tab.Tab = Each Tab", "Local regionTab.Tab = First Tab", "While regionTab <> Null", "If regionTab\Parent = ID", "FUI_DeleteGadget( Handle( regionTab ) )", "regionTab = First Tab", "regionTab = After regionTab") = True)
	Assert(AssertRegionChildTeardown%("reg.Region = Object.Region( ID )", "For pan.Panel = Each Panel", "Local regionPanel.Panel = First Panel", "While regionPanel <> Null", "If regionPanel\Parent = ID", "FUI_DeleteGadget( Handle( regionPanel ) )", "regionPanel = First Panel", "regionPanel = After regionPanel") = True)
	Assert(AssertRegionChildTeardown%("reg.Region = Object.Region( ID )", "For btn.Button = Each Button", "Local regionButton.Button = First Button", "While regionButton <> Null", "If regionButton\Parent = ID", "FUI_DeleteGadget( Handle( regionButton ) )", "regionButton = First Button", "regionButton = After regionButton") = True)
	Assert(AssertRegionChildTeardown%("reg.Region = Object.Region( ID )", "For chk.CheckBox = Each CheckBox", "Local regionCheckBox.CheckBox = First CheckBox", "While regionCheckBox <> Null", "If regionCheckBox\Parent = ID", "FUI_DeleteGadget( Handle( regionCheckBox ) )", "regionCheckBox = First CheckBox", "regionCheckBox = After regionCheckBox") = True)
	Assert(AssertRegionChildTeardown%("reg.Region = Object.Region( ID )", "For cbo.ComboBox = Each ComboBox", "Local regionComboBox.ComboBox = First ComboBox", "While regionComboBox <> Null", "If regionComboBox\Parent = ID", "FUI_DeleteGadget( Handle( regionComboBox ) )", "regionComboBox = First ComboBox", "regionComboBox = After regionComboBox") = True)
	Assert(AssertRegionChildTeardown%("reg.Region = Object.Region( ID )", "For grp.GroupBox = Each GroupBox", "Local regionGroupBox.GroupBox = First GroupBox", "While regionGroupBox <> Null", "If regionGroupBox\Parent = ID", "FUI_DeleteGadget( Handle( regionGroupBox ) )", "regionGroupBox = First GroupBox", "regionGroupBox = After regionGroupBox") = True)
	Assert(AssertRegionChildTeardown%("reg.Region = Object.Region( ID )", "For img.ImageBox = Each ImageBox", "Local regionImageBox.ImageBox = First ImageBox", "While regionImageBox <> Null", "If regionImageBox\Parent = ID", "FUI_DeleteGadget( Handle( regionImageBox ) )", "regionImageBox = First ImageBox", "regionImageBox = After regionImageBox") = True)
	Assert(AssertRegionChildTeardown%("reg.Region = Object.Region( ID )", "For lbl.Label = Each Label", "Local regionLabel.Label = First Label", "While regionLabel <> Null", "If regionLabel\Parent = ID", "FUI_DeleteGadget( Handle( regionLabel ) )", "regionLabel = First Label", "regionLabel = After regionLabel") = True)
	Assert(AssertRegionChildTeardown%("reg.Region = Object.Region( ID )", "For lst.ListBox = Each ListBox", "Local regionListBox.ListBox = First ListBox", "While regionListBox <> Null", "If regionListBox\Parent = ID", "FUI_DeleteGadget( Handle( regionListBox ) )", "regionListBox = First ListBox", "regionListBox = After regionListBox") = True)
	Assert(AssertRegionChildTeardown%("reg.Region = Object.Region( ID )", "For prg.ProgressBar = Each ProgressBar", "Local regionProgressBar.ProgressBar = First ProgressBar", "While regionProgressBar <> Null", "If regionProgressBar\Parent = ID", "FUI_DeleteGadget( Handle( regionProgressBar ) )", "regionProgressBar = First ProgressBar", "regionProgressBar = After regionProgressBar") = True)
	Assert(AssertRegionChildTeardown%("reg.Region = Object.Region( ID )", "For rad.Radio = Each Radio", "Local regionRadio.Radio = First Radio", "While regionRadio <> Null", "If regionRadio\Parent = ID", "FUI_DeleteGadget( Handle( regionRadio ) )", "regionRadio = First Radio", "regionRadio = After regionRadio") = True)
	Assert(AssertRegionChildTeardown%("reg.Region = Object.Region( ID )", "For scroll.ScrollBar = Each ScrollBar", "Local regionScrollBar.ScrollBar = First ScrollBar", "While regionScrollBar <> Null", "If regionScrollBar\Parent = ID", "FUI_DeleteGadget( Handle( regionScrollBar ) )", "regionScrollBar = First ScrollBar", "regionScrollBar = After regionScrollBar") = True)
	Assert(AssertRegionChildTeardown%("reg.Region = Object.Region( ID )", "For sld.Slider = Each Slider", "Local regionSlider.Slider = First Slider", "While regionSlider <> Null", "If regionSlider\Parent = ID", "FUI_DeleteGadget( Handle( regionSlider ) )", "regionSlider = First Slider", "regionSlider = After regionSlider") = True)
	Assert(AssertRegionChildTeardown%("reg.Region = Object.Region( ID )", "For spn.Spinner = Each Spinner", "Local regionSpinner.Spinner = First Spinner", "While regionSpinner <> Null", "If regionSpinner\Parent = ID", "FUI_DeleteGadget( Handle( regionSpinner ) )", "regionSpinner = First Spinner", "regionSpinner = After regionSpinner") = True)
	Assert(AssertRegionChildTeardown%("reg.Region = Object.Region( ID )", "For txt.TextBox = Each TextBox", "Local regionTextBox.TextBox = First TextBox", "While regionTextBox <> Null", "If regionTextBox\Parent = ID", "FUI_DeleteGadget( Handle( regionTextBox ) )", "regionTextBox = First TextBox", "regionTextBox = After regionTextBox") = True)
	Assert(AssertRegionChildTeardown%("reg.Region = Object.Region( ID )", "For tree.TreeView = Each TreeView", "Local regionTreeView.TreeView = First TreeView", "While regionTreeView <> Null", "If regionTreeView\Parent = ID", "FUI_DeleteGadget( Handle( regionTreeView ) )", "regionTreeView = First TreeView", "regionTreeView = After regionTreeView") = True)
	Assert(AssertRegionChildTeardown%("reg.Region = Object.Region( ID )", "For view.View = Each View", "Local regionView.View = First View", "While regionView <> Null", "If regionView\Parent = ID", "FUI_DeleteGadget( Handle( regionView ) )", "regionView = First View", "regionView = After regionView") = True)
End Test
