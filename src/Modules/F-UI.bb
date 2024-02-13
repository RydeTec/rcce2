; This file must be compiled with BlitzRC

;#Region ---------- Notes ----------------------
;The minimum value for the camera range of [app\Cam] should be
;less than 1.0.
;
;To use 3D Viewports you MUST set RENDER_MODE to 1.
;
;If the mouse is over a 3D Viewport and nothing else then
;app\overView will contain the handle of that 3D Viewport
;
;If you have problems with the draw order of windows or gadgets
;you will most likely need to increase ORDER_GAP (Found in the
;---- Globals ---- region)
;
;
;All dialogs return either True or False when they are closed.
;The values chosen from the dialogs are then available from the
;Application type and include the following:
;
; - app\currentFile
; - app\currentRGB
; - app\currentFont.FONT
;
;When you choose a font, all the attributes are stored in the
;FONT type. Values of particular interest will be:
;
; - app\currentFont\FaceName
; - app\currentFont\Height
; - app\currentFont\Weight
; - app\currentFont\Italic
; - app\currentFont\Underline
;
;app\currentIndex will contain the index of the last selected
;filter from the open or save dialog box.
;#End Region

Strict

;#Region ---------- Types ----------------------
Type CHOOSEFONT
	
	Field lStructSize, hwndOwner, hDC, lpLogFont, iPointSize, Flags, rgbColors, lCustData, lpfnHook
	Field lpTemplateName, hInstance, lpszStyle, nFontType, nSizeMin, nSizeMax
	
End Type

Type Font
	
	Field Name$
	Field Height
	Field Bold
	Field Italic
	Field Underline
	
End Type

Type RecentFile
	
	Field filename$
	Field displayname$
		
End Type

Type ToolTip
	
	Field Parent
	Field Caption$
		
End Type

Type Application
	
	;Application
	Field hWnd, Caption$, Version$, X, Y, W, H, D, M
	Field OldW, OldH, OW, OH, SW, SH, Border, Resizing, ResizingX, ResizingY, OffX, OffY, OverBorder
	
	;GUI Framework
	Field Buffer.BBBuffer, R, G, B, A#, R2, G2, B2, A2#, NR, NG, NB, NR2, NG2, NB2, GradDir, Cam.BBEntity, Pivot.BBEntity, Aspect#, Scale#, FrameTimer
	Field dirStart$
	
	;Frame Rate
	Field FPeriod, FTime, FRate, FElapsed, FTicks, FTween#, FLimit, FPS#
	
	;Fonts
	Field fntWindow.BBFont, fntMenu.BBFont, fntGadget.BBFont
	
	;Mouse
	Field Mouse.BBEntity, Win32Mouse, MX, MY, MZ, MXS#, MYS#, MZS#, MB1, MB2, MB3
	
	;Window
	Field overWin.Window, modalWin.Window
	
	;Tool Tip
	Field ToolTipMesh.BBEntity, ToolTip$, TempTip$, ToolTipX, ToolTipY, ToolTipW, ToolTipH, ToolTipTimer, ToolTipDelay
	
	;Gadgets
	Field actMenuTitle.MenuTitle, actContextMenu.ContextMenu, actComboBox.ComboBox, actInputBox, overView, actView.View
	Field Picked.BBEntity
	
	;Recent Files
	Field recentFile$
	
	;Dialogs
	Field currentIndex, currentRGB, currentFile$, currentFont.Font
	
	;Multi-Lingual
	Field BaseLang.BBStream, NewLang.BBStream, BaseLangs$, NewLangs$, OldLang.BBStream
	
	;Events
	Field Quit, ResetEvents, Idle
	
	;Options
	Field Wire
	
End Type

Type Event
	
	Field EventID, EventData$, EventHandle, EventX, EventY
	
End Type

Type Mesh
	
	Field ID, Mesh.BBEntity, Wire, Hidden
	
End Type

Type Window
	
	;Arguments
	Field X, Y, W, H, Caption$, StatusCaption$, Icon.BBImage, Flags
	
	;Anchor
	Field OX, OY, OW, OH
	Field Anchor, AX, AY, AW, AH
	
	;Properties
	Field MX
	Field DragX, DragY, DragW, DragH
	Field Modal, Locked, Minimised, Closed
	Field Dragging, Resizing, ResizingX, ResizingY, OffsetX, OffsetY
	Field overGadget, fntGadget
	
	Field FX, FY, FW, FH
	
	;GUI
	Field Mesh.BBEntity, MinMesh.BBEntity, TitleBar.BBEntity, TitleBarText.BBEntity, MenuBar.BBEntity, StatusBar.BBEntity, StatusBarText.BBEntity, Order, IconMesh.BBEntity
	
	;Buttons
	Field MinBtn.BBEntity, CloseBtn.BBEntity
	Field MinBtnActive, MinBtnState, CloseBtnActive, CloseBtnState
	
End Type

Type MenuTitle
	
	;Owner
	Field Owner.Window
	
	;Arguments
	Field X, Y, W, H, Caption$
	Field overMenuItem, actMenuItem.MenuItem
	
	;Properties
	Field State
	Field CY, DX, DY, DW, DH
	Field overGadget, fntGadget
	Field Disabled, Hidden
	
	;GUI
	Field Mesh.BBEntity, DropDown.BBEntity, Order
	
End Type

Type MenuItem
	
	;Owner
	Field Owner.MenuTitle, Parent.MenuItem
	
	;Arguments
	Field X, Y, W, H, Caption$, ShortCut$, Icon.BBImage, Checkable, Checked, ID
	Field overMenuItem.MenuItem, actMenuItem.MenuItem
	
	;Properties
	Field rf.RecentFile
	Field CY, DX, DY, DW, DH
	Field HasChildren
	Field overGadget, fntGadget
	
	Field State
	Field Disabled, Hidden
	
	;GUI
	Field Mesh.BBEntity, DropDown.BBEntity, Order
	
End Type

Type Region
	
	;Owner
	Field Owner.Window, Parent
	
	;Arguments
	Field X, Y, W, H
	
	;Anchor
	Field OX, OY, OW, OH
	Field Anchor, AX, AY, AW, AH
	
	Field FX, FY, FW, FH
	
	;Misc
	Field State
	Field Hidden, Disabled
	Field Mesh.BBEntity, Order
	
End Type

Type Tab
	
	;Owner
	Field Owner.Window, Parent
	
	;Arguments
	Field X, Y, W, H
	
	;Anchor
	Field OX, OY, OW, OH
	Field Anchor, AX, AY, AW, AH
	
	;Properties
	Field DX
	
	Field FX, FY, FW, FH
	Field actTabPage.TabPage
	
	;Misc
	Field State
	Field Hidden, Disabled
	Field Mesh.BBEntity, Order
	
End Type

Type TabPage
	
	;Owner
	Field Owner.Tab
	
	;Arguments
	Field Caption$, Icon.BBImage
	
	;Properties
	Field X, Y, W, H
	
	Field State
	Field Disabled, Hidden
	
	;Misc
	Field Mesh.BBEntity, IconMesh.BBEntity
	
End Type

Type Panel
	
	;Owner
	Field Owner.Window, Parent
	
	;Arguments
	Field X, Y, W, H, Caption$
	
	;Anchor
	Field OX, OY, OW, OH
	Field Anchor, AX, AY, AW, AH
	
	;Properties
	Field FX, FY, FW, FH
	Field Active, State
	Field Hidden, Disabled
	
	;Misc
	Field Mesh.BBEntity, DropDown.BBEntity, Order
	
End Type

Type Button
	
	;Owner
	Field Owner.Window, Parent
	
	;Arguments
	Field X, Y, W, H, Caption$, Icon.BBImage, Flags, ID
	
	;Anchor
	Field OX, OY, OW, OH
	Field Anchor, AX, AY, AW, AH
	
	;Properties
	Field Active, State
	Field Hidden, Disabled
	
	Field Over
	
	;Misc
	Field Mesh.BBEntity
	
End Type

Type CheckBox
	
	;Owner
	Field Owner.Window, Parent
	
	;Arguments
	Field X, Y, W, H, Caption$, Checked
	
	;Properties
	Field Active, State
	Field Disabled, Hidden
	
	;Anchor
	Field OX, OY, OW, OH
	Field Anchor, AX, AY, AW, AH
	
	;GUI
	Field Mesh.BBEntity
	
End Type

Type ComboBox
	
	;Owner
	Field Owner.Window, Parent
	
	;Arguments
	Field X, Y, W, H, DispItems
	
	;Properties
	Field DX, DY, DW, DH, CY, CW, CH
	Field Active, State, actItem.ComboBoxItem, selItem.ComboBoxItem
	Field Disabled, Hidden
	
	;Scroll Bars
	Field VScroll.VScrollBar, SOY
	
	;Anchor
	Field OX, OY, OW, OH
	Field Anchor, AX, AY, AW, AH
	
	;GUI
	Field Mesh.BBEntity, DropDown.BBEntity, Order
	
End Type

Type ComboBoxItem
	
	;Owner
	Field Owner.ComboBox
	
	;Arguments
	Field Caption$, Icon.BBImage
	
	;Properties
	Field CData$
	Field Y, Active, State
	Field Disabled, Hidden
	
	;GUI
	Field Mesh.BBEntity, IconMesh.BBEntity
	
End Type

Type GroupBox
	
	;Owner
	Field Owner.Window, Parent
	
	;Arguments
	Field X, Y, W, H, Caption$
	
	;Anchor
	Field OX, OY, OW, OH
	Field Anchor, AX, AY, AW, AH
	
	;Properties
	Field FX, FY, FW, FH
	
	Field State
	Field Hidden, Disabled
	
	;Misc
	Field Mesh.BBEntity, MeshText.BBEntity, Order
	
End Type

Type ImageBox
	
	;Owner
	Field Owner.Window, Parent
	
	;Arguments
	Field X, Y, W, H, Image.BBImage, Flags
	
	;Properties
	Field State
	Field Disabled, Hidden
	
	;Anchor
	Field OX, OY, OW, OH
	Field Anchor, AX, AY, AW, AH
	
	;GUI
	Field Mesh.BBEntity
	
End Type

Type Label
	
	;Owner
	Field Owner.Window, Parent
	
	;Arguments
	Field X, Y, W, H, Caption$, Align
	
	;Properties
	Field State
	Field Disabled, Hidden
	
	;Anchor
	Field OX, OY, OW, OH
	Field Anchor, AX, AY, AW, AH
	
	;GUI
	Field Mesh.BBEntity
	
End Type

Type ListBox
	
	;Owner
	Field Owner.Window, Parent
	
	;Arguments
	Field X, Y, W, H
	
	;Properties
	Field overItem.ListBoxItem, CX, CY, DW, DH, MultiSel, ForceSel
	
	Field State
	Field Disabled, Hidden
	
	;Scroll Bars
	Field VScroll.VScrollBar, HScroll.HScrollBar, SOX, SOY
	
	;Anchor
	Field OX, OY, OW, OH
	Field Anchor, AX, AY, AW, AH
	
	;GUI
	Field Mesh.BBEntity, Order
	
End Type

Type ListBoxItem
	
	;Owner
	Field Owner.ListBox
	
	;Arguments
	Field Caption$, Icon.BBImage
	
	;Properties
	Field CData$
	Field Y, Active, State
	Field Disabled, Hidden
	
	;GUI
	Field Mesh.BBEntity, IconMesh.BBEntity
	
End Type

Type ProgressBar
	
	;Owner
	Field Owner.Window, Parent
	
	;Arguments
	Field X, Y, W, H, Min#, Max#, Value#, DType
	
	;Properties
	Field PW, Caption$
	
	Field State
	Field Disabled, Hidden
	
	;Anchor
	Field OX, OY, OW, OH
	Field Anchor, AX, AY, AW, AH
	
	;GUI
	Field Mesh.BBEntity, MeshText.BBEntity, Bar.BBEntity
	
End Type

Type Radio
	
	;Owner
	Field Owner.Window, Parent
	
	;Arguments
	Field X, Y, W, H, Caption$, Checked, ID
	
	;Properties
	Field Active, State
	Field Disabled, Hidden
	
	;Anchor
	Field OX, OY, OW, OH
	Field Anchor, AX, AY, AW, AH
	
	;GUI
	Field Mesh.BBEntity
	
End Type

Type ScrollBar
	
	;Owner
	Field Owner.Window, Parent
	
	;Arguments
	Field X, Y, W, H, Min#, Max#, Value#, Direction
	
	;Properties
	Field SX, SY, SW, SH, OSW
	Field B1Active, B1State, B2Active, B2State, SActive, OffX, OffY
	
	Field State
	Field Disabled, Hidden
	
	;Anchor
	Field OX, OY, OW, OH
	Field Anchor, AX, AY, AW, AH
	
	;GUI
	Field Mesh.BBEntity, Btn1.BBEntity, Btn2.BBEntity, ScrollBar.BBEntity
	
End Type

Type Slider
	
	;Owner
	Field Owner.Window, Parent
	
	;Arguments
	Field X, Y, W, H, Min#, Max#, Value#, Direction
	
	;Properties
	Field SX, SY, SW, SH, SActive, OffX, OffY
	
	Field State
	Field Disabled, Hidden
	
	;Color
	Field RGB1, RGB2
	Field GradientDir
	
	;Anchor
	Field OX, OY, OW, OH
	Field Anchor, AX, AY, AW, AH
	
	;GUI
	Field Mesh.BBEntity, ScrollBar.BBEntity
	
End Type

Type Spinner
	
	;Owner
	Field Owner.Window, Parent
	
	;Arguments
	Field X, Y, W, H, Min#, Max#, Value#, Inc#, DType, Append$
	
	;Properties
	Field Active
	Field B1Active, B1State, B2Active, B2State, BDelay, BTimer
	Field Caption$
	
	Field State
	Field Disabled, Hidden
	
	;Anchor
	Field OX, OY, OW, OH
	Field Anchor, AX, AY, AW, AH
	
	;Input
	Field OffX
	Field Key
	Field k71, k72, k73, k75, k76, k77, k79, k80, k81, k82, k83
	Field Timer, CTimer, selStart, selEnd
	
	;GUI
	Field Mesh.BBEntity, MeshText.BBEntity, Caret.BBEntity, Btn1.BBEntity, Btn2.BBEntity
	
End Type

Type TextBox
	
	;Owner
	Field Owner.Window, Parent
	
	;Arguments
	Field X, Y, W, H, MaxLength
	
	;Properties
	Field Active
	Field Caption$
	
	Field State
	Field Disabled, Hidden
	
	;Input
	Field OffX
	Field Key
	Field k71, k72, k73, k75, k76, k77, k79, k80, k81, k82, k83
	Field Timer, CTimer, selStart, selEnd
	
	;Anchor
	Field OX, OY, OW, OH
	Field Anchor, AX, AY, AW, AH
	
	;GUI
	Field Mesh.BBEntity, MeshText.BBEntity, Caret.BBEntity
	
End Type

Type TreeView
	
	;Owner
	Field Owner.Window, Parent
	
	;Arguments
	Field X, Y, W, H
	
	;Properties
	Field DW, DH, CY, overNode.Node
	
	Field State
	Field Disabled, Hidden
	
	;Scroll Bars
	Field VScroll.VScrollBar, HScroll.HScrollBar, SOX, SOY
	
	;Anchor
	Field OX, OY, OW, OH
	Field Anchor, AX, AY, AW, AH
	
	;GUI
	Field Mesh.BBEntity, Order
	
End Type

Type Node
	
	;Owner
	Field Owner.TreeView, Parent.Node
	
	;Arguments
	Field Y, Caption$, Icon
	
	;Properties
	Field CData$
	Field Open, Active, State, Layer, Valid
	Field HasChildren, ChildCount
	Field OY, CY, OH, overNode.Node
	Field Disabled, Hidden
	
	;GUI
	Field Mesh.BBEntity, DropDown.BBEntity
	
End Type

Type View
	
	;Owner
	Field Owner.Window, Parent
	
	;Arguments
	Field X, Y, W, H, Caption$, Flags
	
	;Properties
	Field State
	
	Field Cam.BBEntity, R, G, B, Zoom, Wire, ProjMode
	Field Disabled, Hidden
	
	;Anchor
	Field OX, OY, OW, OH
	Field Anchor, AX, AY, AW, AH
	
	;GUI
	Field Mesh.BBEntity, MeshText.BBEntity
	
End Type

;Context Menus
Type ContextMenu
	
	;Owner
	Field Owner.Window
	
	;Arguments
	Field X, Y
	Field overMenuItem.ContextMenuItem, actMenuItem.ContextMenuItem
	
	;Properties
	Field CY, DX, DY, DW, DH
	Field overGadget, fntGadget
	Field Disabled, Hidden
	
	;GUI
	Field DropDown.BBEntity, Order
	
End Type

Type ContextMenuItem
	
	;Owner
	Field Owner.ContextMenu, Parent.ContextMenuItem
	
	;Arguments
	Field X, Y, W, H, Caption$, ShortCut$, Icon.BBImage, Checkable, Checked, ID
	Field overMenuItem.ContextMenuItem, actMenuItem.ContextMenuItem
	
	;Properties
	Field rf.RecentFile
	Field CY, DX, DY, DW, DH
	Field HasChildren
	Field overGadget, fntGadget
	Field Disabled, Hidden
	
	;GUI
	Field Mesh.BBEntity, DropDown.BBEntity, Order
	
End Type

;Used by gadgets internally
Type VScrollBar
	
	;Owner
	Field Parent
	
	;Arguments
	Field X, Y, W, H, Min#, Max#, Value#
	
	;Properties
	Field SX, SY, SW, SH
	Field B1Active, B1State, B2Active, B2State, SActive, OffX, OffY
	Field Disabled, Hidden
	
	;GUI
	Field Mesh.BBEntity, Btn1.BBEntity, Btn2.BBEntity, ScrollBar.BBEntity
	
End Type

Type HScrollBar
	
	;Owner
	Field Parent
	
	;Arguments
	Field X, Y, W, H, Min#, Max#, Value#
	
	;Properties
	Field SX, SY, SW, SH
	Field B1Active, B1State, B2Active, B2State, SActive, OffX, OffY
	Field Disabled, Hidden
	
	;GUI
	Field Mesh.BBEntity, Btn1.BBEntity, Btn2.BBEntity, ScrollBar.BBEntity
	
End Type
;#End Region

;#Region ---------- Globals --------------------
Global app.Application = Null

;Only used with FUI_LoadResolution( fName$ )
Global resW				= 0
Global resH				= 0
Global resD				= 0
Global resM				= 0

;Mouse Cursors
Global IDCArrow						= FUI_LoadCursor( IDC_ARROW )
Global IDCIBeam						= FUI_LoadCursor( IDC_IBEAM )
Global IDCCross						= FUI_LoadCursor( IDC_CROSS )
Global IDCSizeNW					= FUI_LoadCursor( IDC_SIZENW )
Global IDCSizeNE					= FUI_LoadCursor( IDC_SIZENE )
Global IDCSizeWE					= FUI_LoadCursor( IDC_SIZEWE )
Global IDCSizeNS					= FUI_LoadCursor( IDC_SIZENS )
Global IDCSizeAll					= FUI_LoadCursor( IDC_SIZEALL )
Global IDCHand						= FUI_LoadCursor( IDC_HAND )
Global IDCVScroll					= FUI_LoadCursor( IDC_VSCROLL )

;Window Resize Method
; 0 - The Blitz Window will be made resizable.
;     Using this method, the GUI will only
;     update after you have finished resizing.
; 1 - The Blitz Window will be manually resized
;     internally and the GUI will resize in
;     real-time. Using this method, the "border"
;     to use for resizing will be just inside
;     the actual window border.
Global WINDOW_RESIZE_METHOD			= 1

;For use with recent file lists
Global RECENT_COUNT					= 0

;Filters for custom dialogs
Global FILTER_CURRENT$				= ""
Global FILTER_CURRENT_INDEX			= 0

;Render Modes
; 0 - This mode is optimised for applications that DO NOT require
;3d viewport gadgets.
; 1 - If your application has 3D viewports you will need to set the
;render mode to 1 to make them work correctly.
Global RENDER_MODE = 1

;Gadget EntityOrder will start from this offset.
Global ORDER_START =-2

;Initial spacing between Window EntityOrders.
;A few gaps are needed inbetween windows for the gadgets.
;Unless you have lots of nested gadgets (Tabs within tabs
;within panels within tabs within panels etc) you shouldn't need
;to increase this value.
Global ORDER_GAP = 10

;Text will be given this EntityOrder. Make it higher than ORDER_START
;to make it appear below the GUI or much lower to make it appear
;above the GUI (eg -100)
Global ORDER_TEXT =-1

;Context menus will appear on this layer.
Global ORDER_CONTEXTMENU =-100

;Tool tips will appear on this layer.
Global ORDER_TOOLTIP =-101

;The mouse will appear on this layer. It should be the lowest value.
Global ORDER_MOUSE =-102

;If this is False, gadgets created with a width and height less than
;0 will be created to the specified percentage of its parent. If it's True
;the width and height of the gadget will be set to the percentage of its
;parent while taking the gadgets X and Y location into consideration. By using
;this method you can easily create gadgets with an even border around each
;of its sides. See the "percentage.bb" example for more.
Global PERCENT_CLIENT = True

;This should be left alone unless you have graphical glitches with
;your text. If the text appears as solid blocks try increasing
;this value. This is mostly used to counteract the slight inaccuracies
;that occur in 16 bit mode.
Global RGB_ACCURACY = 20

;Icons
Global ICON_NEW.BBImage = Null
Global ICON_OPEN.BBImage = Null
Global ICON_SAVE.BBImage = Null
;#End Region

;#Region ---------- Constants ------------------
;Mouse Cursors
Const IDC_ARROW						= 32512
Const IDC_IBEAM						= 32513
Const IDC_CROSS						= 32515
Const IDC_SIZENW					= 32642
Const IDC_SIZENE					= 32643
Const IDC_SIZEWE					= 32644
Const IDC_SIZENS					= 32645
Const IDC_SIZEALL					= 32646
Const IDC_HAND						= 32649
Const IDC_VSCROLL					= 32652
Const IDC_HVSCROLL					= 32654

;Minimum Window Size
Const MIN_WINDOW_WIDTH				= 80
Const MIN_WINDOW_HEIGHT				= 60

;Anchors
Const ANCHOR_LOCK					= 1
Const ANCHOR_FLOAT					= 2

;Determines if the recent file list will be numbered
Const RECENT_DISPLAYNUM				= False

;Used for gradients and scroll bar, slider directions
Const DIR_VERTICAL					= 0
Const DIR_HORIZONTAL				= 1

;Used for spinners etc.
Const DTYPE_INTEGER					= 0
Const DTYPE_FLOAT					= 1

;Alignments (for labels)
Const ALIGN_LEFT					= 0
Const ALIGN_CENTER					= 1
Const ALIGN_RIGHT					= 2

;WINDOW STYLE FLAGS
Const WS_TITLEBAR			= 1
Const WS_MENUBAR			= 2
Const WS_STATUSBAR			= 4
Const WS_CLOSEBUTTON		= 8
Const WS_RESTOREBUTTON		= 16
Const WS_MINBUTTON			= 32
Const WS_ALLBUTTONS			= WS_CLOSEBUTTON Or WS_RESTOREBUTTON Or WS_MINBUTTON
Const WS_ANCHORED			= 64
Const WS_RESIZABLE			= 128
Const WS_BORDER				= 256
Const WS_DEFAULT			= WS_TITLEBAR Or WS_ALLBUTTONS

;CONTROL STYLE FLAGS
Const CS_ICONSTRIP			= 1
Const CS_CHECKABLE			= 2
Const CS_CHECKED			= 4
Const CS_BORDER				= 8
Const CS_POPUP				= 16
Const CS_RESIZE				= 32

;SendMessage Message Constants
Const M_SETPOS = 0
Const M_SETPOS_X = 1
Const M_SETPOS_Y = 2
Const M_SETPOS_Z = 3
Const M_SETSIZE = 4
Const M_SETSIZE_W = 5
Const M_SETSIZE_H = 6
Const M_SETROT_X = 7
Const M_SETROT_Y = 8
Const M_SETROT_Z = 9
Const M_SETCAPTION = 10, M_SETTEXT = 10
Const M_SETVALUE = 11
Const M_SETCHECKED = 12
Const M_SETINDEX = 13
Const M_SETSELECTED = 14
Const M_SETCOLOR = 15, M_SETCOLOUR = 15
Const M_SETMODE = 16
Const M_SETIMAGE = 17
Const M_SETDATA = 18
Const M_SETID = 19
Const M_SETSTATE = 20

Const M_GETPOS = 1000
Const M_GETPOS_X = 1001
Const M_GETPOS_Y = 1002
Const M_GETPOS_Z = 1003
Const M_GETSIZE = 1004
Const M_GETSIZE_W = 1005
Const M_GETSIZE_H = 1006
Const M_GETROT_X = 1007
Const M_GETROT_Y = 1008
Const M_GETROT_Z = 1009
Const M_GETCAPTION = 1010, M_GETTEXT = 1010
Const M_GETVALUE = 1011
Const M_GETCHECKED = 1012
Const M_GETINDEX = 1013
Const M_GETSELECTED = 1014
Const M_GETCOLOR = 1015, M_GETCOLOUR = 1015
Const M_GETMODE = 1016
Const M_GETIMAGE = 1017
Const M_GETDATA = 1018
Const M_GETID = 1019
Const M_GETSTATE = 1020
Const M_COUNTITEMS = 1021

Const M_CHECK = 2000
Const M_UNCHECK = 2001
Const M_OPEN = 2002
Const M_CLOSE = 2003
Const M_SELECT = 2004
Const M_UNSELECT = 2005
Const M_ENABLE = 2006
Const M_DISABLE = 2007
Const M_SHOW = 2008
Const M_HIDE = 2009
Const M_RESET = 2010
Const M_DELETE = 2011
Const M_DELETEINDEX = 2012
Const M_VISIBLE = 2013

;Window specific
Const M_SETMODAL = 3000
Const M_GETMODAL = 3001
Const M_BRINGTOFRONT = 3002
Const M_SENDTOBACK = 3003
Const M_MINIMISE = 3004, M_MINIMIZE = 3004
Const M_MAXIMISE = 3005, M_MAXIMIZE = 3005
Const M_RESTORE = 3006

;Menu Specific
Const M_SETSHORTCUT = 4000
Const M_GETSHORTCUT = 4001

;View Specific
Const M_GETCAMERA = 5000

;Any of the below Const values may conflict with other
;blitz libs such as BlitzSys. If they do then comment
;out one set of them.
;
;Open File Dialog
Const OFN_ALLOWMULTISELECT			= 512
Const OFN_CREATEPROMPT				= $2000
Const OFN_FILEMUSTEXIST				= $1000
Const OFN_HIDEREADONLY				= 4
Const OFN_NOCHANGEDIR				= 8
Const OFN_NONETWORKBUTTON			= $20000
Const OFN_NOREADONLYRETURN			= $8000
Const OFN_NOVALIDATE				= 256
Const OFN_OVERWRITEPROMPT			= 2
Const OFN_PATHMUSTEXIST				= $800
Const OFN_READONLY					= 1
Const OFN_LONGNAMES					= $200000
Const OFN_NOLONGNAMES				= $40000
Const OFN_EXPLORER					= $80000

;Message Box
Const MB_USERICON					= 128
Const MB_ICONASTERISK				= 64
Const MB_ICONEXCLAMATION			= $30
Const MB_ICONWARNING				= $30
Const MB_ICONERROR					= 16
Const MB_ICONHAND					= 16
Const MB_ICONQUESTION				= 32
Const MB_OK							= 0
Const MB_ABORTRETRYIGNORE			= 2
Const MB_APPLMODAL					= 0
Const MB_DEFAULT_DESKTOP_ONLY		= $20000
Const MB_HELP						= $4000
Const MB_RIGHT						= $80000
Const MB_RTLREADING					= $100000
Const MB_TOPMOST					= $40000
Const MB_DEFBUTTON1					= 0
Const MB_DEFBUTTON2					= 256
Const MB_DEFBUTTON3					= 512
Const MB_DEFBUTTON4					= $300
Const MB_ICONINFORMATION			= 64
Const MB_ICONSTOP					= 16
Const MB_OKCANCEL					= 1
Const MB_RETRYCANCEL				= $5
Const MB_SERVICE_NOTIFICATION		= $40000
Const MB_SETFOREGROUND				= $10000
Const MB_SYSTEMMODAL				= 4096
Const MB_TASKMODAL					= $2000
Const MB_YESNO						= 4
Const MB_YESNOCANCEL				= 3

;Values returned from the message box
Const IDOK							= 1
Const IDCANCEL						= 2
Const IDABORT						= 3
Const IDRETRY						= 4
Const IDIGNORE						= 5
Const IDYES							= 6
Const IDNO							= 7
Const IDCLOSE						= 8
Const IDHELP						= 9

;Font Dialog
Const CF_BOTH						= 3
Const CF_EFFECTS					= 256
Const CF_FIXEDPITCHONLY				= $4000
Const CF_FORCEFONTEXIST				= $10000
Const CF_INITTOLOGFONTSTRUCT		= 64
Const CF_LIMITSIZE					= $2000
Const CF_NOOEMFONTS					= $800
Const CF_NOFACESEL					= $80000
Const CF_NOSCRIPTSEL				= $800000
Const CF_NOSTYLESEL					= $100000
Const CF_NOSIZESEL					= $200000
Const CF_NOSIMULATIONS				= 4096
Const CF_NOVECTORFONTS				= $800
Const CF_NOVERTFONTS				= $1000000
Const CF_PRINTERFONTS				= 2
Const CF_SCALABLEONLY				= $20000
Const CF_SCREENFONTS				= 1
Const CF_SCRIPTSONLY				= $400
Const CF_SELECTSCRIPT				= $400000
Const CF_TTONLY						= $40000
Const CF_WYSIWYG					= $8000
;#End Region

;#Region ---------- Colours --------------------
Global SC_BLACK											= FUI_RGBToInt( 0, 0, 0 )
Global SC_WHITE											= FUI_RGBToInt( 255, 255, 255 )

Global SC_MASK											= FUI_RGBToInt( 255, 0, 0 )

Global SC_FORM											= 0
Global SC_FORM_BORDER									= 0
Global SC_TITLEBAR										= 0
Global SC_TITLEBAR_TEXT									= 0
Global SC_MENUBAR										= 0
Global SC_MENUBAR_BORDER								= 0
Global SC_STATUSBAR										= 0
Global SC_STATUSBAR_TEXT								= 0
Global SC_STATUSBAR_BORDER								= 0

Global SC_MENUTITLE										= 0
Global SC_MENUTITLE_OVER								= 0
Global SC_MENUTITLE_SEL									= 0
Global SC_MENUTITLE_TEXT								= 0
Global SC_MENUTITLE_TEXT_OVER							= 0
Global SC_MENUTITLE_TEXT_SEL							= 0
Global SC_MENUTITLE_BORDER								= 0
Global SC_MENUTITLE_BORDER_OVER							= 0
Global SC_MENUTITLE_BORDER_SEL							= 0

Global SC_MENUITEM										= 0
Global SC_MENUITEM_OVER									= 0
Global SC_MENUITEM_SEL									= 0
Global SC_MENUITEM_TEXT									= 0
Global SC_MENUITEM_TEXT_OVER							= 0
Global SC_MENUITEM_TEXT_SEL								= 0
Global SC_MENUITEM_BORDER								= 0
Global SC_MENUITEM_BORDER_OVER							= 0
Global SC_MENUITEM_BORDER_SEL							= 0

Global SC_MENUDROPDOWN									= 0
Global SC_MENUDROPDOWN_BORDER							= 0
Global SC_MENUDROPDOWN_STRIP							= 0

Global SC_TOOLTIP										= 0
Global SC_TOOLTIP_BORDER								= 0
Global SC_TOOLTIP_TEXT									= 0

Global SC_GADGET										= 0
Global SC_GADGET_TEXT									= 0
Global SC_GADGET_COLOR									= 0
Global SC_GADGET_COLOR_TEXT								= 0
Global SC_GADGET_BORDER									= 0

Global SC_INPUT											= 0
Global SC_INPUT_TEXT									= 0
Global SC_INPUT_COLOR									= 0
Global SC_INPUT_COLOR_TEXT								= 0
Global SC_INPUT_BORDER									= 0

Global SC_VIEW											= 0
Global SC_VIEW_BORDER									= 0
Global SC_VIEW_BORDER_SEL								= 0
;#End Region

;#Region ---------- Skins ----------------------
Global SKIN_ENABLED									= False
Global SKIN_PATH$									= "skins\default\"

Global FONT_WINDOW_FACE$							= "Arial"
Global FONT_WINDOW_SIZE								= 14
Global FONT_MENU_FACE$								= "Arial"
Global FONT_MENU_SIZE								= 14
Global FONT_GADGET_FACE$							= "Arial"
Global FONT_GADGET_SIZE								= 14

Global WINDOW_BORDER								= True

Global TITLEBAR_HEIGHT								= 24
Global TITLEBAR_TEXT_X								= 0
Global TITLEBAR_TEXT_Y								= 0
Global TITLEBAR_BUTTON_X							= 2
Global TITLEBAR_BUTTON_Y							= 2

Global MENUBAR_HEIGHT								= 20
Global MENUBAR_SPACING								= 0
Global MENUBAR_X									= 1
Global MENUBAR_Y									= 1

Global STATUSBAR_HEIGHT								= 20

Global MENUTITLE_HEIGHT								= 18
Global MENUTITLE_DROPDOWN_X							= 0
Global MENUTITLE_DROPDOWN_Y							=-1

Global MENUITEM_HEIGHT								= 18
Global MENUITEM_TEXT_X								= 23
Global MENUITEM_DROPDOWN_X							= 1
Global MENUITEM_DROPDOWN_Y							= 0

Global MENUDROPDOWN_PADDING							= 2
Global MENUDROPDOWN_SPACING							= 1
Global MENUDROPDOWN_STRIP							= True
Global MENUDROPDOWN_STRIP_W							= 20

Global TABPAGE_HEIGHT								= 20

Global PANEL_HEIGHT									= 18

Global COMBOBOX_PADDING								= 2
Global COMBOBOX_SPACING								= 0
Global COMBOBOX_STRIP								= True
Global COMBOBOX_STRIP_W								= 18

Global COMBOBOXITEM_HEIGHT							= 14

Global LISTBOX_PADDING								= 2
Global LISTBOX_SPACING								= 0
Global LISTBOX_STRIP								= True
Global LISTBOX_STRIP_W								= 18

Global LISTBOXITEM_HEIGHT							= 18

Global TREEVIEW_PADDING								= 2

Global TREEVIEWITEM_HEIGHT							= 17
;#End Region

;#Region ---------- Application Functions ------
Function FUI_AppTitle( Caption$, Version$="" )
	
	app\Caption = Caption$
	If Version$ > "" app\Version$ = Version$
	
	AppTitle app\Caption
	
End Function

Function FUI_IsAppIdle(  )
	
	If app\MXS <> 0 Or app\MYS <> 0 Or app\MZS <> 0
		Return False
	EndIf
	If app\MB1 > 0 Or app\MB2 > 0 Or app\MB3 > 0
		Return False
	EndIf
	
	If app\actInputBox <> 0
		Return False
	EndIf
	
	Return True
	
End Function

Function FUI_LoadCursor( cursor )
	
	;Load Mouse Cursor
	Return FUI_API_LoadCursor( 0, cursor )
	
End Function

Function FUI_LoadResolution( fName$ )	
	
	file.BBStream = ReadFile( fName$ )
	If file <> Null
		Repeat
			tline$	= ReadLine( file )
			var$	= FUI_Parse( tline$, 0, " " )
			value$	= Trim( Mid( tline$, Len( var$ ) + 1 ) )
			
			Select Lower( var$ )
				Case "width"
					resW = Int( value$ )
				Case "height"
					resH = Int( value$ )
				Case "depth"
					resD = Int( value$ )
				Case "mode"
					If value$ = 0 Or value$ = 1 Or value$ = 2 Or value$ = 3
						resM = Int( value$ )
					Else
						If value$ = "fullscreen" Or value$ = "full screen"
							resM = 1
						Else
							resM = 2
						EndIf
					EndIf
			End Select
		Until Eof( file )
		CloseFile file
	EndIf
	
End Function

Function FUI_MinimiseApp(  )
	
	FUI_API_ShowWindow( app\hWnd, 6 )
	
End Function

Function FUI_MoveApp( X, Y )
	
	bnkRECT.BBBank = CreateBank( 16 )
	FUI_API_GetWindowRect( app\hWnd, bnkRECT )
	FUI_API_MoveWindow( app\hWnd, X, Y, PeekInt( bnkRECT, 8 ) - PeekInt( bnkRECT, 0 ), PeekInt( bnkRECT, 12 ) - PeekInt( bnkRECT, 4 ), True )
	FreeBank bnkRECT
	bnkRECT = Null
	
End Function

Function FUI_ResizeApp( W, H )
	
	If W < MIN_WINDOW_WIDTH
		W = MIN_WINDOW_WIDTH
	EndIf
	If H < MIN_WINDOW_HEIGHT
		H = MIN_WINDOW_HEIGHT
	EndIf
	app\OldW = app\W
	app\OldH = app\H
	app\W = W
	app\H = H
	
	;Normal window border
	If (FUI_API_GetWindowLong( app\hWnd,-16 ) And $40000) = $40000
		W = W + FUI_API_GetSystemMetrics( 32 )*2
		H = H + FUI_API_GetSystemMetrics( 33 )*2 + FUI_API_GetSystemMetrics( $04 )
	Else
		W = W + FUI_API_GetSystemMetrics( $07 )*2
		H = H + FUI_API_GetSystemMetrics( $08 )*2 + FUI_API_GetSystemMetrics( $04 )
	EndIf
	
	bnkRECT.BBBank = CreateBank( 16 )
	FUI_API_GetWindowRect( app\hWnd, bnkRECT )
	FUI_API_MoveWindow( app\hWnd, PeekInt( bnkRECT, 0 ), PeekInt( bnkRECT, 4 ), W, H, True )
	FreeBank bnkRECT
	bnkRECT = Null
	
End Function

Function FUI_CenterApp(  )
	
	bnkRECT.BBBank = CreateBank( 16 )
	FUI_API_GetWindowRect( app\hWnd, bnkRECT )
	W = PeekInt( bnkRECT, 8 ) - PeekInt( bnkRECT, 0 )
	H = PeekInt( bnkRECT, 12 ) - PeekInt( bnkRECT, 4 )
	FUI_API_MoveWindow( app\hWnd, app\SW / 2 - W / 2, app\SH / 2 - H / 2, W, H, True )
	FreeBank bnkRECT
	bnkRECT = Null
	
End Function

Function FUI_RemoveBorder(  )
	
	bnkRECT.BBBank = CreateBank( 16 )
	FUI_API_GetWindowRect( app\hWnd, bnkRECT )
	winX = PeekInt( bnkRECT, 0 )
	winY = PeekInt( bnkRECT, 4 )
	
	FUI_API_GetClientRect( app\hWnd, bnkRECT )
	winW = PeekInt( bnkRECT, 8 ) - PeekInt( bnkRECT, 0 )
	winH = PeekInt( bnkRECT, 12 ) - PeekInt( bnkRECT, 4 )
	
	FreeBank bnkRECT
	bnkRECT = Null
	
	If (FUI_API_GetWindowLong( app\hWnd,-16 ) And $40000) = $40000
		;Sizable window border
		x = FUI_API_GetSystemMetrics( 32 )
		y = FUI_API_GetSystemMetrics( 33 ) + FUI_API_GetSystemMetrics($04)
	Else
		x = FUI_API_GetSystemMetrics($07)
		y = FUI_API_GetSystemMetrics($08) + FUI_API_GetSystemMetrics($04)	
	EndIf
	
	region	= FUI_API_CreateRectRgn( X, Y, X + winW, Y + winH )
	FUI_API_SetWindowRgn( app\hWnd, region, True )
	FUI_API_DeleteObject region
	
End Function

Function FUI_AddBorder(  )
	
	bnkRECT.BBBank = CreateBank( 16 )
	FUI_API_GetWindowRect( app\hWnd, bnkRECT )
	winX = PeekInt( bnkRECT, 0 )
	winY = PeekInt( bnkRECT, 4 )
	winW = PeekInt( bnkRECT, 8 ) - PeekInt( bnkRECT, 0 )
	winH = PeekInt( bnkRECT, 12 ) - PeekInt( bnkRECT, 4 )
	
	FreeBank bnkRECT
	bnkRECT = Null
	
	region	= FUI_API_CreateRectRgn( 0, 0, winW, winH )
	FUI_API_SetWindowRgn( app\hWnd, region, True )
	FUI_API_DeleteObject region
	
End Function

Function FUI_MakeAppResizable(  )
	
	If WINDOW_RESIZE_METHOD = 0
		Style = FUI_API_GetWindowLong( app\hWnd,-16 ) Or $10000 Or $40000
	Else
		Style = FUI_API_GetWindowLong( app\hWnd,-16 ) Or $10000
	EndIf
	FUI_API_SetWindowLong( app\hWnd,-16, Style )
	
End Function

Function FUI_SetCursor( cursor )
	
	;Initialise
	FUI_API_SetClassLong( app\hWnd,-12, 0 )
	
	;Set Mouse Cursor
	FUI_API_SetCursor( cursor )
	
End Function

Function FUI_SetIcon( file$ )
	
	;Internal Icon
	Icon = FUI_API_LoadIcon( app\hWnd, file$, 0 )
	FUI_API_SetClassLong( app\hWnd,-14, Icon )
	
End Function

Function FUI_AppMouseOver( X = 0, Y = 0, W = 0, H = 0 )
	
	bnkMouse.BBBank = CreateBank( 8 )
	FUI_API_GetCursorPos( bnkMouse )
	MX = PeekInt( bnkMouse, 0 )
	MY = PeekInt( bnkMouse, 4 )
	If MX >= X And MX < X + W And MY >= Y And MY < Y + H
		Return True
	Else
		Return False
	EndIf
	FreeBank bnkMouse
	bnkMouse = Null
	
End Function
;#End Region

;#Region ---------- Custom Dialogs -------------
Function FUI_CustomColorDialog( R=0, G=0, B=0 )
	
	
	
End Function

Function FUI_CustomFontDialog(  )
	
	;Not yet supported
	
End Function

Function FUI_CustomMessageBox( msg$, title$="", style=0 )
	
	If title$ = "" title$ = app\Caption
	
	NumLines = FUI_CountItems( msg$, Chr(10) )
	
	winW = 90
	winH = 60
	SetFont app\fntGadget
	For A = 0 To NumLines - 1
		W = StringWidth( FUI_Parse( msg$, A, Chr(10) ) ) + 20
		If W > winW
			winW = W
		EndIf
	Next

	If NumLines * FontHeight(  ) + 20 > winH
		winH = NumLines * FontHeight(  ) + 20
	EndIf
	
	btnW = 0
	If (style And MB_OKCANCEL) = MB_OKCANCEL Or (style And MB_RETRYCANCEL) = MB_RETRYCANCEL Or (style And MB_YESNO ) = MB_YESNO
		btnW = 140 + 5 + 20
	ElseIf (style And MB_ABORTRETRYIGNORE) = MB_ABORTRETRYIGNORE Or (style And MB_YESNOCANCEL ) = MB_YESNOCANCEL
		btnW = 210 + 10 + 20
	ElseIf (style And MB_OK) = MB_OK
		btnW = 70 + 20
	EndIf
	
	If btnW > winW
		winW = btnW
	EndIf
	winH = winH + 32
	
	btnX = winW / 2 - (btnW-20) / 2
	
	If (style And MB_OKCANCEL) = MB_OKCANCEL
		btn1c$ = "Ok"
		btn2c$ = "Cancel"
	ElseIf (style And MB_RETRYCANCEL) = MB_RETRYCANCEL
		btn1c$ = "Retry"
		btn2c$ = "Cancel"
	ElseIf (style And MB_YESNO ) = MB_YESNO
		btn1c$ = "Yes"
		btn2c$ = "No"
	ElseIf (style And MB_ABORTRETRYIGNORE) = MB_ABORTRETRYIGNORE
		btn1c$ = "Abort"
		btn2c$ = "Retry"
		btn3c$ = "Ignore"
	ElseIf (style And MB_YESNOCANCEL ) = MB_YESNOCANCEL
		btn1c$ = "Yes"
		btn2c$ = "No"
		btn3c$ = "Cancel"
	ElseIf (style And MB_OK) = MB_OK
		btn1c$ = "Ok"
	EndIf
	
	win		= FUI_Window( 0, 0, winW, winH, title$, 0, 1, 1 )
	lbl		= FUI_Label( win, winW / 2, 10, msg$, ALIGN_CENTER )
	
	btn1	= FUI_Button( win, btnX, winH - 54, 70, 20, btn1c$ )
	btn2	= FUI_Button( win, btnX + 75, winH - 54, 70, 20, btn2c$ )
	btn3	= FUI_Button( win, btnX + 150, winH - 54, 70, 20, btn3c$ )
	
	If (style And MB_OKCANCEL) = MB_OKCANCEL Or (style And MB_RETRYCANCEL) = MB_RETRYCANCEL Or (style And MB_YESNO ) = MB_YESNO
		FUI_HideGadget btn3
	ElseIf (style And MB_ABORTRETRYIGNORE) = MB_ABORTRETRYIGNORE Or (style And MB_YESNOCANCEL ) = MB_YESNOCANCEL
	ElseIf (style And MB_OK) = MB_OK
		FUI_HideGadget btn2
		FUI_HideGadget btn3
	EndIf
	
	FUI_ModalWindow win
	FUI_CenterWindow win

	Repeat

		FUI_Update
		
		For e.Event = Each Event
			Select e\EventID
				;Window Events
				Case win
					Select e\EventData
						Case "Closed"
							DlgResult = 0
							DlgClose = True
					End Select
				;Menu Events
				;Gadget Events
				Case btn1
					If (style And MB_OKCANCEL) = MB_OKCANCEL
						DlgResult = IDOK
					ElseIf (style And MB_RETRYCANCEL) = MB_RETRYCANCEL
						DlgResult = IDRETRY
					ElseIf (style And MB_YESNO ) = MB_YESNO
						DlgResult = IDYES
					ElseIf (style And MB_ABORTRETRYIGNORE) = MB_ABORTRETRYIGNORE
						DlgResult = IDABORT
					ElseIf (style And MB_YESNOCANCEL ) = MB_YESNOCANCEL
						DlgResult = IDYES
					ElseIf (style And MB_OK) = MB_OK
						DlgResult = IDOK
					EndIf
					DlgClose = True
				Case btn2
					If (style And MB_OKCANCEL) = MB_OKCANCEL
						DlgResult = IDCANCEL
					ElseIf (style And MB_RETRYCANCEL) = MB_RETRYCANCEL
						DlgResult = IDCANCEL
					ElseIf (style And MB_YESNO ) = MB_YESNO
						DlgResult = IDNO
					ElseIf (style And MB_ABORTRETRYIGNORE) = MB_ABORTRETRYIGNORE
						DlgResult = IDRETRY
					ElseIf (style And MB_YESNOCANCEL ) = MB_YESNOCANCEL
						DlgResult = IDNO
					ElseIf (style And MB_OK) = MB_OK
						DlgResult = IDOK
					EndIf
					DlgClose = True
				Case btn3
					If (style And MB_OKCANCEL) = MB_OKCANCEL
						DlgResult = IDCANCEL
					ElseIf (style And MB_RETRYCANCEL) = MB_RETRYCANCEL
						DlgResult = IDCANCEL
					ElseIf (style And MB_YESNO ) = MB_YESNO
						DlgResult = IDNO
					ElseIf (style And MB_ABORTRETRYIGNORE) = MB_ABORTRETRYIGNORE
						DlgResult = IDIGNORE
					ElseIf (style And MB_YESNOCANCEL ) = MB_YESNOCANCEL
						DlgResult = IDCANCEL
					ElseIf (style And MB_OK) = MB_OK
						DlgResult = IDOK
					EndIf
					DlgClose = True
			End Select
			
			;Remove event from queue
			Delete e
		Next
		
		RenderWorld
		Flip
		
	Until DlgClose = True
	DlgClose = False
	
;	FUI_SetCursor IDCArrow
	
	FUI_DeleteGadget win
	Return DlgResult
	
End Function

Function FUI_CustomOpenDialog( title$ = "", initdir$ = "", filter$ = "", BackHack = True, FolderHack = False)
	
	If title$ = "" title$ = "Select a File to Open..."
	If filter$ = "" filter$ = "All Files (*.*)|*.*"
	
	If Instr(initdir$, CurrentDir$()) = 0 Then initdir$ = CurrentDir() + initdir$
;	If initdir$ = "" initdir$ = CurrentDir()
	
	If Right( initdir$, 1 ) <> "\"
		initdir$ = initdir$ + "\"
	EndIf
	
	win		= FUI_Window( 0, 0, 300, 216, title$, 0, 1, 1 )
	lbl		= FUI_Label( win, 70, 5, "Drive letter:", ALIGN_RIGHT )
	drive	= FUI_ComboBox( win, 75, 2, 225-4-22, 20, 5 )
	up		= FUI_Button( win, 300-4-20, 2, 20, 20, "", Ptr ICON_OPEN )
	index = 1
	
	driveFound = False
	For A = 1 To 26
		dirtype = FUI_API_GetDriveType( Chr( A + 96 ) + ":" )
		If dirtype > 1 And dirtype < 7
			FUI_ComboBoxItem drive, Upper( Chr( A + 96 ) ) + ":\"
			
			If driveFound = False And dirtype = 3
				FUI_SendMessage drive, M_SETINDEX, index
				driveFound = True
			EndIf
			
			index = index + 1
		EndIf
	Next
	If BackHack = True Then FUI_DisableGadget(drive)

	lst		= FUI_ListBox( win, 2, 24, 298-4, 120 )
	lbl		= FUI_Label( win, 70, 149, "File name:", ALIGN_RIGHT )
	fname	= FUI_TextBox( win, 75, 146, 153-4, 20 )
	lbl		= FUI_Label( win, 70, 171, "Files of type:", ALIGN_RIGHT )
	ftype	= FUI_ComboBox( win, 75, 168, 153-4, 20 )
	
	ok		= FUI_Button( win, 300-4-70, 146, 70, 20, "Open" )
	cancel	= FUI_Button( win, 300-4-70, 168, 70, 20, "Cancel" )
	
	FUI_ModalWindow win
	FUI_CenterWindow win
	
	FUI_GetFilters ftype, filter$
	FUI_GetFiles lst, initdir$
	
	FUI_SendMessage ftype, M_SETINDEX, 1

	ClickedTime = MilliSecs()
	DlgDir$ = initdir$
	DlgFile$ = ""
	Repeat
		
		FUI_Update

		For e.Event = Each Event
			Select e\EventID
				;Window Events
				Case win
					Select e\EventData
						Case "Closed"
							DlgResult = False
							DlgClose = True
					End Select
				;Menu Events
				;Gadget Events
				Case drive
					Select e\EventData
						Case 0
							;Do nothing
						Default
							DlgDir$ = FUI_SendMessage( drive, M_GETTEXT, e\EventData )
							If Right( DlgDir$, 1 ) <> "\"
								DlgDir$ = DlgDir$ + "\"
							EndIf
							FUI_GetFiles lst, FUI_SendMessage( drive, M_GETTEXT, e\EventData )
					End Select
				Case lst
					FileName$ = DlgDir$ + FUI_SendMessage( lst, M_GETTEXT, e\EventData )
					If FileType( FileName$ ) = 2
						; Enter folder
						If FolderHack = False
							DlgDir$ = FileName$
							If Right( DlgDir$, 1 ) <> "\"
								DlgDir$ = DlgDir$ + "\"
							EndIf
							FUI_GetFiles lst, DlgDir$
							FUI_SendMessage fname, M_SETTEXT, ""
						Else
							; Select folder
							If MilliSecs() - ClickedTime > 600
								DlgFile$ = FUI_SendMessage( lst, M_GETTEXT, e\EventData )
								FUI_SendMessage fname, M_SETTEXT, FUI_SendMessage( lst, M_GETTEXT, e\EventData )
								ClickedTime = MilliSecs()
							; Enter folder
							Else
								DlgDir$ = FileName$
								If Right( DlgDir$, 1 ) <> "\"
									DlgDir$ = DlgDir$ + "\"
								EndIf
								FUI_GetFiles lst, DlgDir$
								FUI_SendMessage fname, M_SETTEXT, ""
								ClickedTime = MilliSecs() - 600
							EndIf
						EndIf
					Else
						DlgFile$ = FUI_SendMessage( lst, M_GETTEXT, e\EventData )
						FUI_SendMessage fname, M_SETTEXT, FUI_SendMessage( lst, M_GETTEXT, e\EventData )
					EndIf
				Case up
					If Len(DlgDir$) > Len(initdir$) Or BackHack = False
						For A = Len( DlgDir$ ) - 1 To 1 Step -1
							If Mid( DlgDir$, A, 1 ) = "\"
								DlgDir$ = Left( DlgDir$, A )
								Exit
							EndIf
						Next
						If Right( DlgDir$, 1 ) <> "\"
							DlgDir$ = DlgDir$ + "\"
						EndIf
						FUI_GetFiles lst, DlgDir$
						FUI_SendMessage fname, M_SETTEXT, ""
					EndIf
				Case ftype
					If e\EventData > 0
						FILTER_CURRENT_INDEX = Int( e\EventData ) - 1
						FUI_GetFiles lst, DlgDir$
					EndIf
					
				Case ok
					If DlgFile$ <> ""
						app\currentFile = DlgDir$ + DlgFile$
						DlgResult = True
						DlgClose = True
					Else
						DlgResult = False
						DlgClose = True
					EndIf
				Case cancel
					DlgResult = False
					DlgClose = True
			End Select
			
			;Remove event from queue
			Delete e
		Next

		RenderWorld
		Flip

;		FUI_SetCursor app\Win32Mouse
		
	Until DlgClose = True
	DlgClose = False
	
;	FUI_SetCursor IDCArrow
	
	FUI_DeleteGadget win
	Return DlgResult
	
End Function

Function FUI_CustomPrintDialog(  )
	
	;Not yet supported
	
End Function

Function FUI_CustomSaveDialog( title$ = "", initdir$ = "", filter$ = "", index = 1 )
	
	If title$ = "" title$ = "Select a File to Save..."
	If filter$ = "" filter$ = "All Files (*.*)|*.*"
	
	If initdir$ = "" initdir$ = CurrentDir()
	
	If Right( initdir$, 1 ) <> "\"
		initdir$ = initdir$ + "\"
	EndIf
	
	win		= FUI_Window( 0, 0, 300, 216, title$, 0, 1, 1 )
	lbl		= FUI_Label( win, 70, 5, "Drive letter:", ALIGN_RIGHT )
	drive	= FUI_ComboBox( win, 75, 2, 225-4-22, 20, 5 )
	up		= FUI_Button( win, 300-4-20, 2, 20, 20, "", Ptr ICON_OPEN )
	index = 1
	
	driveFound = False
	For A = 1 To 26
		dirtype = FUI_API_GetDriveType( Chr( A + 96 ) + ":" )
		If dirtype > 1 And dirtype < 7
			FUI_ComboBoxItem drive, Upper( Chr( A + 96 ) ) + ":\"
			
			If driveFound = False And dirtype = 3
				FUI_SendMessage drive, M_SETINDEX, index
				driveFound = True
			EndIf
			
			index = index + 1
		EndIf
	Next
	
	lst		= FUI_ListBox( win, 2, 24, 298-4, 120 )
	lbl		= FUI_Label( win, 70, 149, "File name:", ALIGN_RIGHT )
	fname	= FUI_TextBox( win, 75, 146, 153-4, 20 )
	lbl		= FUI_Label( win, 70, 171, "Files of type:", ALIGN_RIGHT )
	ftype	= FUI_ComboBox( win, 75, 168, 153-4, 20, 5 )
	
	ok		= FUI_Button( win, 300-4-70, 146, 70, 20, "Save" )
	cancel	= FUI_Button( win, 300-4-70, 168, 70, 20, "Cancel" )
	
	FUI_ModalWindow win
	FUI_CenterWindow win
	
	FUI_GetFilters ftype, filter$
	FUI_GetFiles lst, initdir$
	
	FUI_SendMessage ftype, M_SETINDEX, 1
	
	DlgDir$ = initdir$
	DlgFile$ = ""
	Repeat
		
		FUI_Update
		
		For e.Event = Each Event
			Select e\EventID
				;Window Events
				Case win
					Select e\EventData
						Case "Closed"
							DlgResult = False
							DlgClose = True
					End Select
				;Menu Events
				;Gadget Events
				Case drive
					Select e\EventData
						Case 0
							;Do nothing
						Default
							DlgDir$ = FUI_SendMessage( drive, M_GETTEXT, e\EventData )
							If Right( DlgDir$, 1 ) <> "\"
								DlgDir$ = DlgDir$ + "\"
							EndIf
							FUI_GetFiles lst, FUI_SendMessage( drive, M_GETTEXT, e\EventData )
					End Select
				Case lst
					FileName$ = DlgDir$ + FUI_SendMessage( lst, M_GETTEXT, e\EventData )
					If FileType( FileName$ ) = 2
						DlgDir$ = FileName$
						If Right( DlgDir$, 1 ) <> "\"
							DlgDir$ = DlgDir$ + "\"
						EndIf
						FUI_GetFiles lst, DlgDir$
						FUI_SendMessage fname, M_SETTEXT, ""
					Else
						DlgFile$ = FUI_SendMessage( lst, M_GETTEXT, e\EventData )
						FUI_SendMessage fname, M_SETTEXT, FUI_SendMessage( lst, M_GETTEXT, e\EventData )
					EndIf
				Case up
					For A = Len( DlgDir$ ) - 1 To 1 Step -1
						If Mid( DlgDir$, A, 1 ) = "\"
							DlgDir$ = Left( DlgDir$, A )
							Exit
						EndIf
					Next
					If Right( DlgDir$, 1 ) <> "\"
						DlgDir$ = DlgDir$ + "\"
					EndIf
					FUI_GetFiles lst, DlgDir$
					FUI_SendMessage fname, M_SETTEXT, ""
				Case ftype
					If e\EventData > 0
						FILTER_CURRENT_INDEX = Int( e\EventData ) - 1
						FUI_GetFiles lst, DlgDir$
					EndIf
					
				Case ok
					app\currentFile = DlgDir$ + DlgFile$
					DlgResult = True
					DlgClose = True
				Case cancel
					DlgResult = False
					DlgClose = True
			End Select
			
			;Remove event from queue
			Delete e
		Next
		
		RenderWorld
		Flip
		
;		FUI_SetCursor app\Win32Mouse
		
	Until DlgClose = True
	DlgClose = False
	
;	FUI_SetCursor IDCArrow
	
	FUI_DeleteGadget win
	Return DlgResult
	
End Function

Function FUI_GetFiles( lst, dName$ )
	
	FUI_SendMessage lst, M_RESET
	
	If Right( dName$, 1 ) <> "\"
		dName$ = dName$ + "\"
	EndIf
	
	;Directories first
	dir.BBDir = ReadDir( dName$ )
	If dir <> Null
		Repeat
			fName$ = NextFile( dir )
			
			If FileType( dName$ + fName$ ) = 2 And fName$ > ".."
				FUI_ListBoxItem lst, FUI_StripPath( fName$ ), Ptr ICON_OPEN
			EndIf
		Until fName$ = ""
		
		CloseDir dir
	EndIf
	
	;Files
	dir = ReadDir( dName$ )
	If dir <> Null
		Repeat
			fName$ = NextFile( dir )
			
			ext$ = Lower( FUI_Parse( FILTER_CURRENT, FILTER_CURRENT_INDEX, "|" ) )
			If Lower( Right( fName$, Len( ext$ ) ) ) = ext$ Or ext$ = "."
				If FileType( dName$ + fName$ ) = 1
					FUI_ListBoxItem lst, FUI_StripPath( fName$ ), Ptr ICON_NEW
				EndIf
			EndIf
		Until fName$ = ""
		
		CloseDir dir
	EndIf
	
End Function

Function FUI_GetFilters( cbo, filter$ )
	
	FUI_SendMessage cbo, M_RESET
	
	mode = 0
	filtername$ = ""
	
	FILTER_CURRENT$ = ""
	FILTER_CURRENT_INDEX = 0
	
	For A = 1 To Len( filter$ )
		char$ = Mid( filter$, A, 1 )
		
		;Filter Name
		If mode = 0
			If char$ = "|"
				mode = 1
				FUI_ComboBoxItem cbo, filtername$
				filtername$ = ""
			Else
				filtername$ = filtername$ + char$
			EndIf
		;Filter Extensions
		ElseIf mode = 1
			If char$ = "|"
				FILTER_CURRENT$ = FILTER_CURRENT$ + "|"
				mode = 0
			Else
				FILTER_CURRENT$ = FILTER_CURRENT$ + char$
			EndIf
		EndIf
	Next
	FILTER_CURRENT$ = Replace( FILTER_CURRENT$, "*", "" )
	FILTER_CURRENT$ = Replace( FILTER_CURRENT$, " ", "" )
	FILTER_CURRENT$ = Left( FILTER_CURRENT$, Len( FILTER_CURRENT$ ) - 1 )
	
End Function

Function FUI_StripPath$( file$ )

	If Len( file$ ) > 0
	
		For i = Len( file$ ) To 1 Step -1
		
			mi$ = Mid( file$, i, 1 )
			If mi$ = "\"
				Return name$
			Else
				name$=mi$+name$
			EndIf
		
		Next
	
	EndIf
	
	Return name$

End Function
;#End Region

;#Region ---------- Dialogs --------------------
Function FUI_OpenDialog( title$ = "", initdir$ = "", filter$ = "", flags = 0, index = 1 )
	
	If title$ = "" title$ = "Select a File to Open..."
	If filter$ = "" filter$ = "All Files (*.*)|*.*"
	
		lpofn.BBBank = CreateBank(19*4)
	
	PokeInt lpofn, 0, BankSize( lpofn )
	
	bnkStart.BBBank = CreateBank( Len( initdir$ ) + 1 )
	strStart = FUI_API_lstrcpy( bnkStart, initdir$ )
	
	bnkFilter.BBBank = CreateBank( Len( filter$ ) + 1 )
	strFilter = FUI_API_lstrcpy( bnkFilter, filter$ )
	
	For i = 0 To BankSize( bnkFilter) - 1
		If PeekByte( bnkFilter, i ) = Asc("|")
			PokeByte bnkFilter, i, 0
		EndIf
	Next
	
	bnkTitle.BBBank = CreateBank( Len( title$ ) + 1 )
	strTitle = FUI_API_lstrcpy( bnkTitle, title$ )
	
	PokeInt lpofn, 4, app\hWnd
	PokeInt lpofn, 12, strFilter
	
	fname$ = String( " ", 5120 )
	bnkFile.BBBank = CreateBank( Len( fname$ ) + 1 )
	strFile = FUI_API_lstrcpy( bnkFile, fname$)
	
	PokeInt lpofn, 24, index
	PokeInt lpofn, 28, strFile
	PokeInt lpofn, 32, BankSize( bnkFile )
	PokeInt lpofn, 44, strStart
	PokeInt lpofn, 48, strTitle
	PokeInt lpofn, 52, OFN_EXPLORER Or OFN_FILEMUSTEXIST Or flags
	
	Result = FUI_API_OpenDialog( lpofn )
	If Result <> 0
		app\currentFile = FUI_GetFilename( bnkFile, flags )
		app\currentIndex = PeekInt( lpofn, 24 )
		FreeBank bnkStart
		FreeBank bnkFilter
		FreeBank bnkFile
		FreeBank lpofn
		
		Return True
	Else
		FreeBank bnkStart
		FreeBank bnkFilter
		FreeBank bnkFile
		FreeBank lpofn
		
		Return False
	EndIf
	
End Function

Function FUI_SaveDialog( title$ = "", initdir$ = "", filter$ = "", index = 1 )
	
	If title$ = "" title$ = "Select a File to Open..."
	If filter$ = "" filter$ = "All Files (*.*)|*.*"
	
		lpofn.BBBank = CreateBank(19*4)
	
	PokeInt lpofn, 0, BankSize( lpofn )
	
	bnkStart.BBBank = CreateBank( Len( initdir$ ) + 1 )
	strStart = FUI_API_lstrcpy( bnkStart, initdir$ )
	
	bnkFilter.BBBank = CreateBank( Len( filter$ ) + 1 )
	strFilter = FUI_API_lstrcpy( bnkFilter, filter$ )
	
	For i = 0 To BankSize( bnkFilter) - 1
		If PeekByte( bnkFilter, i ) = Asc("|")
			PokeByte bnkFilter, i, 0
		EndIf
	Next
	
	bnkTitle.BBBank = CreateBank( Len( title$ ) + 1 )
	strTitle = FUI_API_lstrcpy( bnkTitle, title$ )
	
	PokeInt lpofn, 4, app\hWnd
	PokeInt lpofn, 12, strFilter
	
	fname$ = String( " ", 2048 )
	bnkFile.BBBank = CreateBank( Len( fname$ ) + 1 )
	strFile = FUI_API_lstrcpy( bnkFile, fname$)
	
	PokeInt lpofn, 24, index
	PokeInt lpofn, 28, strFile
	PokeInt lpofn, 32, BankSize( bnkFile)
	PokeInt lpofn, 44, strStart
	PokeInt lpofn, 48, strTitle
	PokeInt lpofn, 52, OFN_EXPLORER Or OFN_OVERWRITEPROMPT
	
	Result = FUI_API_SaveDialog( lpofn )
	If Result <> 0
		app\currentFile = FUI_GetFilename( bnkFile )
		app\currentIndex = PeekInt( lpofn, 24 )
		FreeBank bnkStart
		FreeBank bnkFilter
		FreeBank bnkFile
		FreeBank lpofn
		Return True
	Else
		Return False
	EndIf

End Function

Function FUI_ColorDialog( R=0, G=0, B=0 )
	
	lpofn.BBBank = CreateBank(9*4)
	
	PokeInt lpofn, 0, BankSize( lpofn )
	PokeInt lpofn, 4, app\hWnd
	
	bnkStart.BBBank = CreateBank( 64 )
	
	Address.BBBank = CreateBank( 4 )
	CopyBank bnkStart, 4, Address, 0, 4
	
	rgbResult = FUI_RGBToInt( B, G, R,-1 )
	PokeInt lpofn, 12, rgbResult
	PokeInt lpofn, 16, PeekInt( Address, 0 )
	PokeInt lpofn, 20, 2 Or $1
	
	Result = FUI_API_ColorDialog( lpofn )
	
	rgbResult = PeekInt( lpofn, 12 )
	
	;RGB format is reversed
	app\currentRGB = FUI_GetRed( rgbResult ) Or (FUI_GetGreen( rgbResult ) Shl 8) Or (FUI_GetBlue( rgbResult ) Shl 16)
	
	FreeBank Address
	FreeBank bnkStart
	FreeBank lpofn
	
	If Result <> 0
		Return True
	Else
		Return False
	EndIf
	
End Function

Function FUI_FontDialog( flags = CF_SCREENFONTS Or CF_EFFECTS )
	
	lp.BBBank = CreateBank( 5*4 + 8 + 32 )
	PokeInt lp, 0, 14
	PokeByte lp, 5*4 + 3, 1
	
	Address.BBBank = CreateBank(4) 
	CopyBank lp, 4, Address, 0, 4
	
	lpcf.CHOOSEFONT			= New CHOOSEFONT
	lpcf\lStructSize		= 15*4
	lpcf\hwndOwner			= app\hWnd
	lpcf\lpLogFont			= PeekInt(Address, 0)
	FreeBank Address
	lpcf\Flags				= flags
	
	Result = FUI_API_FontDialog( lpcf )
	
	If Result <> 0
		app\currentFont\Height		= PeekInt( lp, 0 )
		app\currentFont\Bold		= PeekInt( lp, 16 )
		If app\currentFont\Bold > 400
			app\currentFont\Bold = True
		Else
			app\currentFont\Bold = 0
		EndIf
		app\currentFont\Italic = PeekByte( lp, 20 )
		If app\currentFont\Italic > 0
			app\currentFont\Italic = True
		Else
			app\currentFont\Italic = False
		EndIf
		app\currentFont\Underline	= PeekByte( lp, 21 )
		app\currentFont\Name		= FUI_GetFontName( lp )
		
		FreeBank Address
		FreeBank lp
		Delete lpcf
		Return True
	Else
		FreeBank Address
		FreeBank lp
		Delete lpcf
		Return False
	EndIf
	
End Function

Function FUI_PrintDialog(  )
	
	lppd.BBBank = CreateBank( 18*4 )
	PokeInt lppd, 0, BankSize( lppd )
	PokeInt lppd, 4, app\hWnd
	
	;Flags
	PokeInt lppd, 20, $100 Or $40000
	;From/To Page
	PokeInt lppd, 24, 1
	PokeInt lppd, 28, 10
	;Min/Max
	PokeInt lppd, 32, 1
	PokeInt lppd, 36, 10
	;Copies
	PokeInt lppd, 40, 1
	
	Result = FUI_API_PrintDialog( lppd )
	FreeBank lppd
	
	If Result = True
		Return True
	Else
		Return False
	EndIf
	
End Function

Function FUI_MessageBox( msg$, title$="", style=0 )
	
	If title$ = "" title$ = app\Caption
	Return FUI_API_MessageBox( app\hWnd, msg$, title$, style )
	
End Function

;Internal Functions
Function FUI_GetFilename$( bnkIn.BBBank, flags=0 )
	
	fname$ = ""
	For i = 0 To BankSize( bnkIn ) - 1
		byte = PeekByte( bnkIn, i )
		If flags = OFN_ALLOWMULTISELECT
			If byte = 0
				If byte = lbyte And i > 0
					Exit
				Else
					byte = Asc( "|"	)
				EndIf
			EndIf
			If byte > 0
				fname$ = fname$ + Chr( byte )
			EndIf
			If byte = Asc( "|" )
				lbyte = 0
			Else
				lbyte = byte
			EndIf
		Else
			If byte = 0 Exit
			fname$ = fname$ + Chr( byte )
		EndIf
	Next
	Return fname$
	
End Function

Function FUI_GetFontName$( bnkIn.BBBank )
	
	fname$ = ""
	For i = 5*4 + 8 To BankSize( bnkIn ) - 1
		byte = PeekByte( bnkIn, i )
		If byte = 0 Exit
		fname$ = fname$ + Chr( byte )
	Next
	Return fname$
	
End Function
;#End Region

;#Region ---------- Event System ---------------
Function FUI_CreateEvent( ID, EventData$ = "", X = 0, Y = 0, EventHandle = 0 )
	
	e.Event			= New Event
	e\EventID		= ID
	e\EventData		= EventData$
	e\EventX		= X
	e\EventY		= Y
	e\EventHandle	= EventHandle
	
	app\Idle		= False
	
End Function

Function FUI_ResetEvents(  )
	
	Delete Each Event
	
End Function

Function FUI_SendMessage$( ID, Message, Param1$="", Param2$="" )
	
	win.Window = Object.Window( ID )
	If win <> Null
		Select Message
			Case M_SETPOS
				If Param1 > "" win\X = Int(Param1)
					If Param2 > "" win\Y = Int(Param2)
				FUI_SetPosition win\Mesh, win\X, win\Y
			Case M_SETPOS_X
				win\X = Int(Param1)
				FUI_SetPosition win\Mesh, win\X, win\Y
			Case M_SETPOS_Y
				win\Y = Int(Param1)
				FUI_SetPosition win\Mesh, win\X, win\Y
			Case M_SETSIZE
				If Param1 > "" win\W = Int(Param1)
					If Param2 > "" win\H = Int(Param2)
				FUI_BuildWindow win
				FUI_UpdateAnchors Handle( win )
			Case M_SETSIZE_W
				win\W = Int(Param1)
				FUI_BuildWindow win
				FUI_UpdateAnchors Handle( win )
			Case M_SETSIZE_H
				win\H = Int(Param1)
				FUI_BuildWindow win
				FUI_UpdateAnchors Handle( win )
			Case M_SETTEXT
				FUI_SetTitleBarText Handle( win ), Param1
			Case M_SETMODAL
				win\Modal = True
				
			Case M_GETPOS
				If Param1 = True
					Return win\W
				ElseIf Param2 = True
					Return win\H
				EndIf
			Case M_GETPOS_X
				Return FUI_GadgetX( win\Mesh )
			Case M_GETPOS_Y
				Return FUI_GadgetY( win\Mesh )
			Case M_GETSIZE
				If Param1 = True
					Return win\W
				ElseIf Param2 = True
					Return win\H
				EndIf
			Case M_GETSIZE_W
				Return win\W
			Case M_GETSIZE_H
				Return win\H
			Case M_GETTEXT
				Return win\Caption
			Case M_GETMODAL
				Return win\Modal
				
			Case M_OPEN
				win\Closed = False
				If win\Minimised = False
					ShowEntity win\Mesh
				Else
					If win\MinMesh <> Null
						ShowEntity win\MinMesh
					EndIf
				EndIf
				
				Insert win Before First Window
			Case M_CLOSE
				win\Closed = True
				If win\Minimised = False
					HideEntity win\Mesh
				Else
					HideEntity win\MinMesh
				EndIf
			Case M_VISIBLE
				Return 1 - win\Closed
			Case M_BRINGTOFRONT
				Insert win Before First Window
			Case M_SENDTOBACK
				Insert win After Last Window
			Case M_MINIMISE
				win\Minimised = True
				HideEntity win\Mesh
				ShowEntity win\MinMesh
			Case M_MAXIMISE
				win\Minimised = False
				ShowEntity win\Mesh
				HideEntity win\MinMesh
			Case M_RESTORE
				win\Minimised = 1 - win\Minimised
				If win\Minimised = True
					If win\TitleBar <> Null
						EntityParent win\TitleBar, win\MinMesh
					EndIf
					HideEntity win\Mesh
					ShowEntity win\MinMesh
				Else
					If win\TitleBar <> Null
						EntityParent win\TitleBar, win\Mesh
					EndIf
					ShowEntity win\Mesh
					HideEntity win\MinMesh
				EndIf
		End Select
		
		Return True
	EndIf
	mnut.MenuTitle = Object.MenuTitle( ID )
	If mnut <> Null
		Select Message
			Case M_SETTEXT
				mnut\Caption = Param1
				
				SetFont app\fntMenu
				mnut\W = StringWidth( mnut\Caption ) + 10
				
				mnut\Owner\MX = 0;mnut\Owner\MX + mnut\W + MENUBAR_SPACING
				
				For mnut2.MenuTitle = Each MenuTitle
					If mnut2\Owner = mnut\Owner
						mnut2\X = WINDOW_BORDER + MENUBAR_X + mnut2\Owner\MX
						mnut2\Owner\MX = mnut2\Owner\MX + mnut2\W + MENUBAR_SPACING
						
						FUI_SetChildPosition mnut2\Mesh, mnut2\X, mnut2\Y
					EndIf
				Next
				FUI_BuildMenuTitle mnut
				
			Case M_GETTEXT
				Return mnut\Caption
				
			Case M_OPEN
				app\actMenuTitle = mnut
			Case M_CLOSE
				If app\actMenuTitle = mnut
					app\actMenuTitle = Null
				EndIf
			Case M_ENABLE
				FUI_EnableGadget Handle( mnut )
			Case M_DISABLE
				FUI_DisableGadget Handle( mnut )
		End Select
		
		Return True
	EndIf
	mnui.MenuItem = Object.MenuItem( ID )
	If mnui <> Null
		Select Message
			Case M_SETTEXT
				mnui\Caption = Param1
				
				SetFont app\fntMenu
				W = StringWidth( mnui\Caption ) + 20
				W = W + MENUDROPDOWN_STRIP_W
				If mnui\ShortCut > ""
					W = W + 20 + StringWidth( mnui\ShortCut )
				EndIf
				
				If mnui\Parent <> Null
					If W > mnui\Parent\DW
						mnui\Parent\DW = W
						For mnui2.MenuItem = Each MenuItem
							If mnui2\Owner = mnui\Owner And mnui2\Parent = mnui\Parent
								mnui2\W = mnui2\Parent\DW
								
								FUI_BuildMenuItem mnui2
							EndIf
						Next
						FUI_BuildMenuItem mnui\Parent, 1
					EndIf
				Else
					If W > mnui\Owner\DW
						mnui\Owner\DW = W
						For mnui2.MenuItem = Each MenuItem
							If mnui2\Owner = mnui\Owner And mnui2\Parent = mnui\Parent
								mnui2\W = mnui2\Owner\DW
								
								FUI_BuildMenuItem mnui2
							EndIf
						Next
						FUI_BuildMenuTitle mnui\Owner, 1
					EndIf
				EndIf
				
			Case M_SETSHORTCUT
				mnui\ShortCut = Param1
				FUI_BuildMenuItem mnui
			Case M_SETCHECKED
				mnui\Checked = Int(Param1)
			Case M_SETID
				mnui\ID = Int(Param1)
				
			Case M_GETTEXT
				Return mnui\Caption
			Case M_GETSHORTCUT
				Return mnui\ShortCut
			Case M_GETCHECKED
				Return mnui\Checked
			Case M_GETID
				Return mnui\ID
				
			Case M_CHECK
				mnui\Checked = True
			Case M_UNCHECK
				mnui\Checked = False
			Case M_OPEN
				If mnui\HasChildren = True
					If mnui\Parent <> Null
						mnui\Parent\actMenuItem = mnui
					Else
						mnui\Owner\actMenuItem = mnui
					EndIf
				EndIf
			Case M_CLOSE
				If mnui\HasChildren = True
					If mnui\Parent <> Null
						If mnui\Parent\actMenuItem = mnui
							mnui\Parent\actMenuItem = Null
						EndIf
					Else
						If mnui\Owner\actMenuItem = mnui
							mnui\Owner\actMenuItem = Null
						EndIf
					EndIf
				EndIf
			Case M_ENABLE
				FUI_EnableGadget Handle( mnui )
			Case M_DISABLE
				FUI_DisableGadget Handle( mnui )
		End Select
		
		Return True
	EndIf
	cmnu.ContextMenu = Object.ContextMenu( ID )
	If cmnu <> Null
		Select Message
			Case M_OPEN
				app\actContextMenu = cmnu
			Case M_CLOSE
				If app\actContextMenu = cmnu
					app\actContextMenu = Null
				EndIf
			Case M_ENABLE
				FUI_EnableGadget Handle( cmnu )
			Case M_DISABLE
				FUI_DisableGadget Handle( cmnu )
		End Select
		
		Return True
	EndIf
	cmnui.ContextMenuItem = Object.ContextMenuItem( ID )
	If cmnui <> Null
		Select Message
			Case M_SETTEXT
				cmnui\Caption = Param1
				FUI_BuildContextMenuItem cmnui
			Case M_SETSHORTCUT
				cmnui\ShortCut = Param1
				FUI_BuildContextMenuItem cmnui
			Case M_SETCHECKED
				cmnui\Checked = Int(Param1)
			Case M_SETID
				cmnui\ID = Int(Param1)
				
			Case M_GETTEXT
				Return cmnui\Caption
			Case M_GETSHORTCUT
				Return cmnui\ShortCut
			Case M_GETCHECKED
				Return cmnui\Checked
			Case M_GETID
				Return cmnui\ID
				
			Case M_CHECK
				cmnui\Checked = True
			Case M_UNCHECK
				cmnui\Checked = False
			Case M_OPEN
				If cmnui\HasChildren = True
					If cmnui\Parent <> Null
						cmnui\Parent\actMenuItem = cmnui
					Else
						cmnui\Owner\actMenuItem = cmnui
					EndIf
				EndIf
			Case M_CLOSE
				If cmnui\HasChildren = True
					If cmnui\Parent <> Null
						If cmnui\Parent\actMenuItem = cmnui
							cmnui\Parent\actMenuItem = Null
						EndIf
					Else
						If cmnui\Owner\actMenuItem = cmnui
							cmnui\Owner\actMenuItem = Null
						EndIf
					EndIf
				EndIf
			Case M_ENABLE
				FUI_EnableGadget Handle( cmnui )
			Case M_DISABLE
				FUI_DisableGadget Handle( cmnui )
		End Select
		
		Return True
	EndIf
	Tab.Tab = Object.Tab( ID )
	If Tab <> Null
		Select Message
			Case M_HIDE
				FUI_HideGadget(ID)
			Case M_SHOW
				FUI_ShowGadget(ID)
			Case M_SETPOS
				If Param1 > ""
					FUI_SetGadgetX Handle( Tab ), Int(Param1)
				EndIf
				If Param2 > ""
					FUI_SetGadgetY Handle( Tab ), Int(Param2)
				EndIf
			Case M_SETPOS_X
				FUI_SetGadgetX Handle( Tab ), Int(Param1)
			Case M_SETPOS_Y
				FUI_SetGadgetY Handle( Tab ), Int(Param1)
			Case M_SETSIZE
				If Param1 > ""
					Tab\W = Int(Param1)
				EndIf
				If Param2 > ""
					Tab\H = Int(Param2)
				EndIf
				FUI_BuildTab Tab
				FUI_UpdateAnchors Handle( Tab )
			Case M_SETSIZE_W
				Tab\W = Int(Param1)
				FUI_BuildTab Tab
				FUI_UpdateAnchors Handle( Tab )
			Case M_SETSIZE_H
				Tab\H = Int(Param1)
				FUI_BuildTab Tab
				FUI_UpdateAnchors Handle( Tab )
			Case M_SETINDEX
				Count = 1
				For tabp.TabPage = Each TabPage
					If tabp\Owner = Tab
						If Count = Param1
							Tab\actTabPage = tabp
							;xmlspy
							For tabp.TabPage = Each TabPage
								If tabp\Owner = Tab
									FUI_BuildTabPage tabp
								EndIf
							Next
							Exit
						Else
							Count = Count + 1
						EndIf
					EndIf
				Next
				
			Case M_GETPOS_X
				Return Tab\X
			Case M_GETPOS_Y
				Return Tab\Y
			Case M_GETSIZE_W
				Return Tab\W
			Case M_GETSIZE_H
				Return Tab\H
			Case M_GETINDEX
				Count = 1
				For tabp.TabPage = Each TabPage
					If tabp\Owner = Tab
						If tabp\Owner\actTabPage = tabp
							Return Count
						Else
							Count = Count + 1
						EndIf
					EndIf
				Next
				Return 0
				
			Case M_ENABLE
				FUI_EnableGadget Handle( Tab )
			Case M_DISABLE
				FUI_DisableGadget Handle( Tab )
		End Select
		
		Return True
	EndIf
	tabp.TabPage = Object.TabPage( ID )
	If tabp <> Null
		Select Message
			Case M_SETSIZE
				tabp\W = Int(Param1)
				FUI_BuildTabPage tabp
			Case M_SETTEXT
				tabp\Caption = Param1
				FUI_BuildTabPage tabp
				FUI_BuildTab tabp\Owner
			Case M_SETSELECTED
				If Param1 = True
					tabp\Owner\actTabPage = tabp
				Else
					If tabp\Owner\actTabPage = tabp
						tabp\Owner\actTabPage = Null
					EndIf
				EndIf
			Case M_SETIMAGE
				If FUI_IsImageSource( Param1$ ) = True
					tabp\Icon = LoadImage( Param1$ )
				Else
					tabp\Icon = Null
				EndIf
				FUI_BuildTabPage tabp
				
			Case M_GETSIZE
				If Param1 = True
					Return tabp\W
				ElseIf Param2 = True
					Return TABPAGE_HEIGHT
				EndIf
			Case M_GETTEXT
				Return tabp\Caption
			Case M_GETSELECTED
				If tabp\Owner\actTabPage = tabp
					Return True
				Else
					Return False
				EndIf
			Case M_GETIMAGE
				;;;Return tabp\Icon\Source
				
			Case M_SELECT
				tabp\Owner\actTabPage = tabp
			Case M_UNSELECT
				If tabp\Owner\actTabPage = tabp
					tabp\Owner\actTabPage = Null
				EndIf
			Case M_ENABLE
				FUI_EnableGadget Handle( tabp )
			Case M_DISABLE
				FUI_DisableGadget Handle( tabp )
		End Select
		
		Return True
	EndIf
	pan.Panel = Object.Panel( ID )
	If pan <> Null
		Select Message
			Case M_HIDE
				FUI_HideGadget(ID)
			Case M_SHOW
				FUI_ShowGadget(ID)
			Case M_SETPOS
				If Param1 > ""
					FUI_SetGadgetX Handle( pan ), Int(Param1)
				EndIf
				If Param2 > ""
					FUI_SetGadgetY Handle( pan ), Int(Param2)
				EndIf
			Case M_SETPOS_X
				FUI_SetGadgetX Handle( pan ), Int(Param1)
			Case M_SETPOS_Y
				FUI_SetGadgetY Handle( pan ), Int(Param1)
			Case M_SETSIZE
				If Param1 > ""
					pan\W = Int(Param1)
				EndIf
				If Param2 > ""
					pan\H = Int(Param2)
				EndIf
				FUI_BuildPanel pan
				FUI_UpdateAnchors Handle( pan )
			Case M_SETSIZE_W
				pan\W = Int(Param1)
				FUI_BuildPanel pan
				FUI_UpdateAnchors Handle( pan )
			Case M_SETSIZE_H
				pan\H = Int(Param1)
				FUI_BuildPanel pan
				FUI_UpdateAnchors Handle( pan )
			Case M_SETTEXT
				pan\Caption = Param1
				FUI_BuildPanel pan
				
			Case M_GETPOS_X
				Return pan\X
			Case M_GETPOS_Y
				Return pan\Y
			Case M_GETSIZE_W
				Return pan\W
			Case M_GETSIZE_H
				Return pan\H
			Case M_GETTEXT
				Return pan\Caption
				
			Case M_ENABLE
				FUI_EnableGadget Handle( pan )
			Case M_DISABLE
				FUI_DisableGadget Handle( pan )
			Case M_OPEN
				FUI_OpenGadget Handle( pan )
			Case M_CLOSE
				FUI_CloseGadget Handle( pan )
		End Select
		
		Return True
	EndIf
	btn.Button = Object.Button( ID )
	If btn <> Null
		Select Message
			Case M_HIDE
				FUI_HideGadget(ID)
			Case M_SHOW
				FUI_ShowGadget(ID)
			Case M_SETPOS
				If Param1 > ""
					FUI_SetGadgetX Handle( btn ), Int(Param1)
				EndIf
				If Param2 > ""
					FUI_SetGadgetY Handle( btn ), Int(Param2)
				EndIf
			Case M_SETPOS_X
				FUI_SetGadgetX Handle( btn ), Int(Param1)
			Case M_SETPOS_Y
				FUI_SetGadgetY Handle( btn ), Int(Param1)
			Case M_SETSIZE
				If Param1 > ""
					btn\W = Int(Param1)
				EndIf
				If Param2 > ""
					btn\H = Int(Param2)
				EndIf
				FUI_BuildButton btn
			Case M_SETSIZE_W
				btn\W = Int(Param1)
				FUI_BuildButton btn
			Case M_SETSIZE_H
				btn\H = Int(Param1)
				FUI_BuildButton btn
			Case M_SETTEXT
				btn\Caption = Param1
				FUI_BuildButton btn
			Case M_SETSELECTED
				If btn\ID > 0
					btn\Active = Int(Param1)
					If btn\Active = True
						If btn\Over = True Then btn\State = 1
						
						tex.BBTexture = FUI_GadgetTexture( btn\Mesh )
						EntityTexture btn\Mesh, tex, 2
						FreeTexture tex
						For btn2.Button = Each Button
							If btn2\ID = btn\ID And btn2 <> btn
								btn2\Active = False
								tex = FUI_GadgetTexture( btn2\Mesh )
								EntityTexture btn2\Mesh, tex, 0
								FreeTexture tex
							EndIf
						Next
					Else
						btn\State = 0
						
						tex = FUI_GadgetTexture( btn\Mesh )
						EntityTexture btn\Mesh, tex, 0
						FreeTexture tex
					EndIf
				EndIf
			Case M_SETINDEX
				If btn\ID > 0
					Count = 1
					For btn2.Button = Each Button
						If btn2\ID = btn\ID
							If Count = Param1
								btn2\Active = True
								tex = FUI_GadgetTexture( btn2\Mesh )
								EntityTexture btn2\Mesh, tex, 2
								FreeTexture tex
							Else
								Count = Count + 1
								btn2\Active = False
								tex = FUI_GadgetTexture( btn2\Mesh )
								EntityTexture btn2\Mesh, tex, 0
								FreeTexture tex
							EndIf
						EndIf
					Next
				EndIf
			Case M_SETID
				btn\ID = Int(Param1)
			Case M_SETIMAGE
				If FUI_IsImageSource( Param1$ ) = True
					btn\Icon = LoadImage( Param1$ )
				Else
					btn\Icon = Null
				EndIf
				FUI_BuildButton btn
				
			Case M_GETPOS_X
				Return btn\X
			Case M_GETPOS_Y
				Return btn\Y
			Case M_GETSIZE_W
				Return btn\W
			Case M_GETSIZE_H
				Return btn\H
			Case M_GETTEXT
				Return btn\Caption
			Case M_GETSELECTED
				If btn\ID > 0
					Return btn\Active
				EndIf
			Case M_GETINDEX
				If btn\ID > 0
					Count = 1
					For btn2.Button = Each Button
						If btn2\ID = btn\ID
							If btn2\Active = True
								Return Count
							Else
								Count = Count + 1
							EndIf
						EndIf
					Next
				EndIf
				Return 0
			Case M_GETID
				Return btn\ID
			Case M_GETIMAGE
				;xmlspy
				If btn\Icon <> Null Then
					Return Ptr btn\Icon
				EndIf
				;;;Return btn\Icon\Source
				
			Case M_SELECT
				If btn\ID > 0
					btn\Active = True
					For btn2.Button = Each Button
						If btn2\ID = btn\ID
							btn2\Active = False
						EndIf
					Next
				EndIf
			Case M_UNSELECT
				If btn\ID > 0
					btn\Active = False
				EndIf
			Case M_ENABLE
				FUI_EnableGadget Handle( btn )
			Case M_DISABLE
				FUI_DisableGadget Handle( btn )
		End Select
		
		Return True
	EndIf
	chk.CheckBox = Object.CheckBox( ID )
	If chk <> Null
		Select Message
			Case M_HIDE
				FUI_HideGadget(ID)
			Case M_SHOW
				FUI_ShowGadget(ID)
			Case M_SETPOS
				If Param1 > ""
					FUI_SetGadgetX Handle( chk ), Int(Param1)
				EndIf
				If Param2 > ""
					FUI_SetGadgetY Handle( chk ), Int(Param2)
				EndIf
			Case M_SETPOS_X
				FUI_SetGadgetX Handle( chk ), Int(Param1)
			Case M_SETPOS_Y
				FUI_SetGadgetY Handle( chk ), Int(Param1)
			Case M_SETTEXT
				chk\Caption = Param1
				FUI_BuildCheckBox chk
			Case M_SETCHECKED
				chk\Checked = Int Param1
				If chk\Disabled = True
					FUI_SetTexture chk\Mesh, 3 + chk\Disabled*4
				EndIf
				
			Case M_GETPOS
				If Param1 = True
					Return chk\X
				ElseIf Param2 = True
					Return chk\Y
				EndIf
			Case M_GETPOS_X
				Return chk\X
			Case M_GETPOS_Y
				Return chk\Y
			Case M_GETTEXT
				Return chk\Caption
			Case M_GETCHECKED
				Return chk\Checked
				
			Case M_CHECK
				chk\Checked = True
			Case M_UNCHECK
				chk\Checked = False
			Case M_ENABLE
				FUI_EnableGadget Handle( chk )
			Case M_DISABLE
				FUI_DisableGadget Handle( chk )
		End Select
		
		Return True
	EndIf
	cbo.ComboBox = Object.ComboBox( ID )
	If cbo <> Null
		Select Message
			Case M_HIDE
				FUI_HideGadget(ID)
			Case M_SHOW
				FUI_ShowGadget(ID)
			Case M_SETPOS
				If Param1 > ""
					FUI_SetGadgetX Handle( cbo ), Int Param1
				EndIf
				If Param2 > ""
					FUI_SetGadgetY Handle( cbo ), Int Param2
				EndIf
			Case M_SETPOS_X
				FUI_SetGadgetX Handle( cbo ), Int Param1
			Case M_SETPOS_Y
				FUI_SetGadgetY Handle( cbo ), Int Param1
			Case M_SETSIZE
				If Param1 > ""
					cbo\W = Int Param1
				EndIf
				If Param2 > ""
					cbo\H = Int Param2
				EndIf
				FUI_BuildComboBox cbo
			Case M_SETSIZE_W
				cbo\W = Int Param1
				FUI_BuildComboBox cbo
			Case M_SETSIZE_H
				cbo\H = Int Param1
				FUI_BuildComboBox cbo
			Case M_SETINDEX
				Count = 1
				For cboi.ComboBoxItem = Each ComboBoxItem
					If cboi\Owner = cbo
						If Count = Param1
							cbo\actItem = cboi
							FUI_BuildComboBox cbo
							Exit
						Else
							Count = Count + 1
						EndIf
					EndIf
				Next
			Case M_SETTEXT
				If Param1 > ""
					Count = 1
					For cboi.ComboBoxItem = Each ComboBoxItem
						If cboi\Owner = cbo
							If Count = Param1
								cboi\Caption = Param2
								FUI_BuildComboBoxItem cboi
								Exit
							Else
								Count = Count + 1
							EndIf
						EndIf
					Next
				Else
					For cboi.ComboBoxItem = Each ComboBoxItem
						If cboi\Owner = cbo
							If cboi\Owner\actItem = cboi
								cboi\Caption = Param2
								FUI_BuildComboBoxItem cboi
								Exit
							EndIf
						EndIf
					Next
				EndIf
				
			Case M_GETPOS
				If Param1 = True
					Return cbo\X
				ElseIf Param2 = True
					Return cbo\Y
				EndIf
			Case M_GETPOS_X
				Return cbo\X
			Case M_GETPOS_Y
				Return cbo\Y
			Case M_GETSIZE
				If Param1 = True
					Return cbo\W
				ElseIf Param2 = True
					Return cbo\H
				EndIf
			Case M_GETSIZE_W
				Return cbo\W
			Case M_GETSIZE_H
				Return cbo\H
			Case M_GETSELECTED
				For cboi.ComboBoxItem = Each ComboBoxItem
					If cboi\Owner = cbo
						If cboi\Owner\actItem = cboi Then Return Handle(cboi)
					EndIf
				Next
				Return 0
			Case M_GETINDEX
				Count = 1
				For cboi.ComboBoxItem = Each ComboBoxItem
					If cboi\Owner = cbo
						If cboi\Owner\actItem = cboi
							Return Count
						Else
							Count = Count + 1
						EndIf
					EndIf
				Next
				Return 0
			Case M_GETTEXT
				If Param1 > ""
					Count = 1
					For cboi.ComboBoxItem = Each ComboBoxItem
						If cboi\Owner = cbo
							If Count = Param1
								Return cboi\Caption
							Else
								Count = Count + 1
							EndIf
						EndIf
					Next
				Else
					For cboi.ComboBoxItem = Each ComboBoxItem
						If cboi\Owner = cbo
							If cboi\Owner\actItem = cboi
								Return cboi\Caption
							EndIf
						EndIf
					Next
				EndIf
				
			Case M_RESET
				For cboi.ComboBoxItem = Each ComboBoxItem
					If cboi\Owner = cbo
						FUI_DeleteGadget Handle( cboi )
					EndIf
				Next
				cbo\CY = 0
				cbo\DH = cbo\DispItems * COMBOBOXITEM_HEIGHT
				FUI_BuildComboBox cbo, 2
			Case M_DELETEINDEX
				Count = 1
				For cboi.ComboBoxItem = Each ComboBoxItem
					If cboi\Owner = cbo
						If Count = Param1
							FUI_DeleteGadget Handle( cboi )
							Exit
						Else
							Count = Count + 1
						EndIf
					EndIf
				Next
			Case M_ENABLE
				FUI_EnableGadget Handle( cbo )
			Case M_DISABLE
				FUI_DisableGadget Handle( cbo )
		End Select
		
		Return True
	EndIf
	cboi.ComboBoxItem = Object.ComboBoxItem( ID )
	If cboi <> Null
		Select Message
			Case M_SETTEXT
				cboi\Caption = Param1
				FUI_BuildComboBoxItem cboi
				FUI_BuildComboBox cboi\Owner
			Case M_SETDATA
				cboi\CData = Param1
				
			Case M_GETTEXT
				Return cboi\Caption
			Case M_GETDATA
				Return cboi\CData
			Case M_ENABLE
				FUI_EnableGadget Handle( cboi )
			Case M_DISABLE
				FUI_DisableGadget Handle( cboi )
		End Select
		
		Return True
	EndIf
	grp.GroupBox = Object.GroupBox( ID )
	If grp <> Null
		Select Message
			Case M_HIDE
				FUI_HideGadget(ID)
			Case M_SHOW
				FUI_ShowGadget(ID)
			Case M_SETPOS
				If Param1 > ""
					FUI_SetGadgetX Handle( grp ), Int Param1
				EndIf
				If Param2 > ""
					FUI_SetGadgetY Handle( grp ), Int Param2
				EndIf
			Case M_SETPOS_X
				FUI_SetGadgetX Handle( grp ), Int Param1
			Case M_SETPOS_Y
				FUI_SetGadgetY Handle( grp ), Int Param1
			Case M_SETSIZE
				If Param1 > ""
					grp\W = Int Param1
				EndIf
				If Param2 > ""
					grp\H = Int Param2
				EndIf
				FUI_BuildGroupBox grp
			Case M_SETSIZE_W
				grp\W = Int Param1
				FUI_BuildGroupBox grp
			Case M_SETSIZE_H
				grp\H = Int Param1
				FUI_BuildGroupBox grp
			Case M_SETTEXT
				grp\Caption = Param1
				FUI_BuildGroupBox grp
				
			Case M_GETPOS
				If Param1 = True
					Return grp\X
				ElseIf Param2 = True
					Return grp\Y
				EndIf
			Case M_GETPOS_X
				Return grp\X
			Case M_GETPOS_Y
				Return grp\Y
			Case M_GETSIZE
				If Param1 = True
					Return grp\W
				ElseIf Param2 = True
					Return grp\H
				EndIf
			Case M_GETSIZE_W
				Return grp\W
			Case M_GETSIZE_H
				Return grp\H
			Case M_GETTEXT
				Return grp\Caption
				
			Case M_ENABLE
				FUI_EnableGadget Handle( grp )
			Case M_DISABLE
				FUI_DisableGadget Handle( grp )
		End Select
		
		Return True
	EndIf
	img.ImageBox = Object.ImageBox( ID )
	If img <> Null
		Select Message
			Case M_HIDE
				FUI_HideGadget(ID)
			Case M_SHOW
				FUI_ShowGadget(ID)
			Case M_SETPOS
				If Param1 > ""
					FUI_SetGadgetX Handle( img ), Int Param1
				EndIf
				If Param2 > ""
					FUI_SetGadgetY Handle( img ), Int Param2
				EndIf
			Case M_SETPOS_X
				FUI_SetGadgetX Handle( img ), Int Param1
			Case M_SETPOS_Y
				FUI_SetGadgetY Handle( img ), Int Param1
			Case M_SETSIZE
				If Param1 > ""
					img\W = Int Param1
				EndIf
				If Param2 > ""
					img\H = Int Param2
				EndIf
				FUI_BuildImageBox img
			Case M_SETSIZE_W
				img\W = Int Param1
				FUI_BuildImageBox img
			Case M_SETSIZE_H
				img\H = Int Param1
				FUI_BuildImageBox img
			Case M_SETIMAGE 
				img\Image = LoadImage(Param1)
				If (img\Flags And CS_RESIZE) = CS_RESIZE 
				   ResizeImage img\Image, img\W, img\H 
				EndIf 
				FUI_BuildImageBox img
				
			Case M_GETPOS
				If Param1 = True
					Return img\X
				ElseIf Param2 = True
					Return img\Y
				EndIf
			Case M_GETPOS_X
				Return img\X
			Case M_GETPOS_Y
				Return img\Y
			Case M_GETSIZE
				If Param1 = True
					Return img\W
				ElseIf Param2 = True
					Return img\H
				EndIf
			Case M_GETSIZE_W
				Return img\W
			Case M_GETSIZE_H
				Return img\H
		End Select
		
		Return True
	EndIf
	lbl.Label = Object.Label( ID )
	If lbl <> Null
		Select Message
			Case M_HIDE
				FUI_HideGadget(ID)
			Case M_SHOW
				FUI_ShowGadget(ID)
			Case M_SETPOS
				If Param1 > ""
					FUI_SetGadgetX Handle( lbl ), Int Param1
				EndIf
				If Param2 > ""
					FUI_SetGadgetY Handle( lbl ), Int Param2
				EndIf
			Case M_SETPOS_X
				FUI_SetGadgetX Handle( lbl ), Int Param1
			Case M_SETPOS_Y
				FUI_SetGadgetY Handle( lbl ), Int Param1
			Case M_SETTEXT
				lbl\Caption = Param1
				FUI_BuildLabel lbl
				
			Case M_GETPOS_X
				Return lbl\X
			Case M_GETPOS_Y
				Return lbl\Y
			Case M_GETTEXT
				Return lbl\Caption
				
			Case M_ENABLE
				FUI_EnableGadget Handle( lbl )
			Case M_DISABLE
				FUI_DisableGadget Handle( lbl )
		End Select
		
		Return True
	EndIf
	lst.ListBox = Object.ListBox( ID )
	If lst <> Null
		Select Message
			Case M_HIDE
				FUI_HideGadget(ID)
			Case M_SHOW
				FUI_ShowGadget(ID)
			Case M_SETPOS
				If Param1 > ""
					FUI_SetGadgetX Handle( lst ), Int Param1
				EndIf
				If Param2 > ""
					FUI_SetGadgetY Handle( lst ), Int Param2
				EndIf
			Case M_SETPOS_X
				FUI_SetGadgetX Handle( lst ), Int Param1
			Case M_SETPOS_Y
				FUI_SetGadgetY Handle( lst ), Int Param1
			Case M_SETSIZE
				If Param1 > ""
					lst\W = Int Param1
				EndIf
				If Param2 > ""
					lst\H = Int Param2
				EndIf
				FUI_BuildListBox lst
			Case M_SETSIZE_W
				lst\W = Int Param1
				FUI_BuildListBox lst
			Case M_SETSIZE_H
				lst\H = Int Param1
				FUI_BuildListBox lst
			Case M_SETINDEX
				Count = 1
				Result = False
				For lsti.ListBoxItem = Each ListBoxItem
					If lsti\Owner = lst
						If Count = Param1
							lsti\Active = True
							tex = FUI_GadgetTexture( lsti\Mesh )
							EntityTexture lsti\Mesh, tex, lsti\Active*2
							Result = True
						Else
							lsti\Active = False
							tex = FUI_GadgetTexture( lsti\Mesh )
							EntityTexture lsti\Mesh, tex, lsti\Active*2
						EndIf
						Count = Count + 1
					EndIf
				Next
				Return Result

			Case M_COUNTITEMS
				Result = 0
				For lsti.ListBoxItem = Each ListBoxItem
					If lsti\Owner = lst Then Result = Result + 1
				Next
				Return Result
			Case M_GETPOS
				If Param1 = True
					Return lst\X
				ElseIf Param2 = True
					Return lst\Y
				EndIf
			Case M_GETPOS_X
				Return lst\X
			Case M_GETPOS_Y
				Return lst\Y
			Case M_GETSIZE
				If Param1 = True
					Return lst\W
				ElseIf Param2 = True
					Return lst\H
				EndIf
			Case M_GETSIZE_W
				Return lst\W
			Case M_GETSIZE_H
				Return lst\H
			Case M_GETINDEX
				Count = 1
				For lsti.ListBoxItem = Each ListBoxItem
					If lsti\Owner = lst
						If lsti\active = True
							Return Handle( lsti )
						Else
							Count = Count + 1
						EndIf
					EndIf
				Next
			Case M_GETSELECTED
				Count = 1
				For lsti.ListBoxItem = Each ListBoxItem
					If lsti\Owner = lst
						If lsti\active = True
							Return Count
						Else
							Count = Count + 1
						EndIf
					EndIf
				Next
				Return 0
			Case M_GETTEXT
				Count = 1
				For lsti.ListBoxItem = Each ListBoxItem
					If lsti\Owner = lst
						If Param1 = ""
							If lsti\Active = True
								Return lsti\Caption
							EndIf
						Else
							If Count = Param1
								Return lsti\Caption
							Else
								Count = Count + 1
							EndIf
						EndIf
					EndIf
				Next

			Case M_DELETEINDEX
				Count = 1
				For lsti.ListBoxItem = Each ListBoxItem
					If lsti\Owner = lst
						If Count = Param1
							FUI_DeleteGadget Handle( lsti )
							Exit
						Else
							Count = Count + 1
						EndIf
					EndIf
				Next
			Case M_RESET
				For lsti.ListBoxItem = Each ListBoxItem
					If lsti\Owner = lst
						FreeEntity lsti\IconMesh
						FreeEntity lsti\Mesh
						Delete lsti
;						FUI_DeleteGadget Handle( lsti )
					EndIf
				Next
				lst\CY = 0
			Case M_ENABLE
				FUI_EnableGadget Handle( lst )
			Case M_DISABLE
				FUI_DisableGadget Handle( lst )
		End Select
		
		Return True
	EndIf
	lsti.ListBoxItem = Object.ListBoxItem( ID )
	If lsti <> Null
		Select Message
			Case M_HIDE
				FUI_HideGadget(ID)
			Case M_SHOW
				FUI_ShowGadget(ID)
			Case M_SETTEXT
				lsti\Caption = Param1
				FUI_BuildListBoxItem lsti
			Case M_SETDATA
				lsti\CData = Param1
			Case M_SETSELECTED
				lsti\Active = Int Param1
				
			Case M_GETTEXT
				Return lsti\Caption
			Case M_GETDATA
				Return lsti\CData
			Case M_GETSELECTED
				Return lsti\Active
				
			Case M_ENABLE
				FUI_EnableGadget Handle( lsti )
			Case M_DISABLE
				FUI_DisableGadget Handle( lsti )
		End Select
		
		Return True
	EndIf
	prg.ProgressBar = Object.ProgressBar( ID )
	If prg <> Null
		Select Message
			Case M_SETPOS
				If Param1 > ""
					FUI_SetGadgetX Handle( prg ), Int Param1
				EndIf
				If Param2 > ""
					FUI_SetGadgetY Handle( prg ), Int Param2
				EndIf
			Case M_SETPOS_X
				FUI_SetGadgetX Handle( prg ), Int Param1
			Case M_SETPOS_Y
				FUI_SetGadgetY Handle( prg ), Int Param1
			Case M_SETSIZE
				If Param1 > ""
					prg\W = Int Param1
				EndIf
				If Param2 > ""
					prg\H = Int Param2
				EndIf
				FUI_BuildProgressBar prg
			Case M_SETSIZE_W
				prg\W = Int Param1
				FUI_BuildProgressBar prg
			Case M_SETSIZE_H
				prg\H = Int Param1
				FUI_BuildProgressBar prg
			Case M_SETVALUE
				prg\Value = Param1
				FUI_BuildProgressBar prg
				
			Case M_GETPOS
				If Param1 = True
					Return prg\X
				ElseIf Param2 = True
					Return prg\Y
				EndIf
			Case M_GETPOS_X
				Return prg\X
			Case M_GETPOS_Y
				Return prg\Y
			Case M_GETSIZE
				If Param1 = True
					Return prg\W
				ElseIf Param2 = True
					Return prg\H
				EndIf
			Case M_GETSIZE_W
				Return prg\W
			Case M_GETSIZE_H
				Return prg\H
			Case M_GETVALUE
				Return prg\Value
				
			Case M_ENABLE
				FUI_EnableGadget Handle( prg )
			Case M_DISABLE
				FUI_DisableGadget Handle( prg )
		End Select
		
		Return True
	EndIf
	rad.Radio = Object.Radio( ID )
	If rad <> Null
		Select Message
			Case M_HIDE
				FUI_HideGadget(ID)
			Case M_SHOW
				FUI_ShowGadget(ID)
			Case M_SETPOS
				If Param1 > ""
					FUI_SetGadgetX Handle( rad ), Int Param1
				EndIf
				If Param2 > ""
					FUI_SetGadgetY Handle( rad ), Int Param2
				EndIf
			Case M_SETPOS_X
				FUI_SetGadgetX Handle( rad ), Int Param1
			Case M_SETPOS_Y
				FUI_SetGadgetY Handle( rad ), Int Param1
			Case M_SETTEXT
				rad\Caption = Param1
				FUI_BuildRadio rad
			Case M_SETCHECKED
				rad\Checked = Int Param1
			Case M_SETID
				rad\ID = Int Param1
				
			Case M_GETPOS
				If Param1 = True
					Return rad\X
				ElseIf Param2 = True
					Return rad\Y
				EndIf
			Case M_GETPOS_X
				Return rad\X
			Case M_GETPOS_Y
				Return rad\Y
			Case M_GETTEXT
				Return rad\Caption
			Case M_GETCHECKED
				Return rad\Checked
			Case M_GETID
				Return rad\ID
				
			Case M_CHECK
				rad\Checked = True
			Case M_UNCHECK
				rad\Checked = False
			Case M_ENABLE
				FUI_EnableGadget Handle( rad )
			Case M_DISABLE
				FUI_DisableGadget Handle( rad )
		End Select
		
		Return True
	EndIf
	scroll.ScrollBar = Object.ScrollBar( ID )
	If scroll <> Null
		Select Message
			Case M_SETPOS
				If Param1 > ""
					FUI_SetGadgetX Handle( scroll ), Int Param1
				EndIf
				If Param2 > ""
					FUI_SetGadgetY Handle( scroll ), Int Param2
				EndIf
			Case M_SETPOS_X
				FUI_SetGadgetX Handle( scroll ), Int Param1
			Case M_SETPOS_Y
				FUI_SetGadgetY Handle( scroll ), Int Param1
			Case M_SETSIZE
				If Param1 > ""
					scroll\W = Int Param1
				EndIf
				If Param2 > ""
					scroll\H = Int Param2
				EndIf
				FUI_BuildScrollBar scroll
			Case M_SETSIZE_W
				scroll\W = Int Param1
				FUI_BuildScrollBar scroll
			Case M_SETSIZE_H
				scroll\H = Int Param1
				FUI_BuildScrollBar scroll
			Case M_SETVALUE
				scroll\Value = Param1
				scroll\SX = FUI_Interpolate( scroll\Value, scroll\Min, scroll\Max, 0, scroll\W - scroll\SW - (scroll\H-2)*2 - 4 )
				
			Case M_GETPOS
				If Param1 = True
					Return scroll\X
				ElseIf Param2 = True
					Return scroll\Y
				EndIf
			Case M_GETPOS_X
				Return scroll\X
			Case M_GETPOS_Y
				Return scroll\Y
			Case M_GETSIZE
				If Param1 = True
					Return scroll\W
				ElseIf Param2 = True
					Return scroll\H
				EndIf
			Case M_GETSIZE_W
				Return scroll\W
			Case M_GETSIZE_H
				Return scroll\H
			Case M_GETVALUE
				Return scroll\Value
				
			Case M_ENABLE
				FUI_EnableGadget Handle( scroll )
			Case M_DISABLE
				FUI_DisableGadget Handle( scroll )
		End Select
		
		Return True
	EndIf
	sld.Slider = Object.Slider( ID )
	If sld <> Null
		Select Message
			Case M_HIDE
				FUI_HideGadget(ID)
			Case M_SHOW
				FUI_ShowGadget(ID)
			Case M_SETPOS
				If Param1 > ""
					FUI_SetGadgetX Handle( sld ), Int Param1
				EndIf
				If Param2 > ""
					FUI_SetGadgetY Handle( sld ), Int Param2
				EndIf
			Case M_SETPOS_X
				FUI_SetGadgetX Handle( sld ), Int Param1
			Case M_SETPOS_Y
				FUI_SetGadgetY Handle( sld ), Int Param1
			Case M_SETSIZE
				If Param1 > ""
					sld\W = Int Param1
				EndIf
				If Param2 > ""
					sld\H = Int Param2
				EndIf
				FUI_BuildSlider sld
			Case M_SETSIZE_W
				sld\W = Int Param1
				FUI_BuildSlider sld
			Case M_SETSIZE_H
				sld\H = Int Param1
				FUI_BuildSlider sld
			Case M_SETVALUE
				sld\Value = Param1
				sld\SX = FUI_Interpolate( sld\Value, sld\Min, sld\Max, 0, sld\W - sld\SW - 2 )
				
			Case M_GETPOS
				If Param1 = True
					Return sld\X
				ElseIf Param2 = True
					Return sld\Y
				EndIf
			Case M_GETPOS_X
				Return sld\X
			Case M_GETPOS_Y
				Return sld\Y
			Case M_GETSIZE
				If Param1 = True
					Return sld\W
				ElseIf Param2 = True
					Return sld\H
				EndIf
			Case M_GETSIZE_W
				Return sld\W
			Case M_GETSIZE_H
				Return sld\H
			Case M_GETVALUE
				Return sld\Value
				
			Case M_ENABLE
				FUI_EnableGadget Handle( sld )
			Case M_DISABLE
				FUI_DisableGadget Handle( sld )
		End Select
		
		Return True
	EndIf
	spn.Spinner = Object.Spinner( ID )
	If spn <> Null
		Select Message
			Case M_HIDE
				FUI_HideGadget(ID)
			Case M_SHOW
				FUI_ShowGadget(ID)			
			Case M_SETPOS
				If Param1 > ""
					FUI_SetGadgetX Handle( spn ), Int Param1
				EndIf
				If Param2 > ""
					FUI_SetGadgetY Handle( spn ), Int Param2
				EndIf
			Case M_SETPOS_X
				FUI_SetGadgetX Handle( spn ), Int Param1
			Case M_SETPOS_Y
				FUI_SetGadgetY Handle( spn ), Int Param1
			Case M_SETSIZE
				If Param1 > ""
					spn\W = Int Param1
				EndIf
				If Param2 > ""
					spn\H = Int Param2
				EndIf
				FUI_BuildSpinner spn
			Case M_SETSIZE_W
				spn\W = Int Param1
				FUI_BuildSpinner spn
			Case M_SETSIZE_H
				spn\H = Int Param1
				FUI_BuildSpinner spn
			Case M_SETVALUE
				spn\Value = Param1
				If spn\DType = DTYPE_INTEGER
					If spn\Value > spn\Max
						spn\Value = spn\Max
					ElseIf spn\Value < spn\Min
						spn\Value = spn\Min
					EndIf
					spn\Caption = Int( spn\Value ) + spn\Append
				Else
					If spn\Value > spn\Max
						spn\Value = spn\Max
					ElseIf spn\Value < spn\Min
						spn\Value = spn\Min
					EndIf
					spn\Caption = spn\Value + spn\Append
				EndIf
				FUI_BuildSpinner spn, 2
				
			Case M_GETPOS
				If Param1 = True
					Return spn\X
				ElseIf Param2 = True
					Return spn\Y
				EndIf
			Case M_GETPOS_X
				Return spn\X
			Case M_GETPOS_Y
				Return spn\Y
			Case M_GETSIZE
				If Param1 = True
					Return spn\W
				ElseIf Param2 = True
					Return spn\H
				EndIf
			Case M_GETSIZE_W
				Return spn\W
			Case M_GETSIZE_H
				Return spn\H
			Case M_GETVALUE
				Return spn\Value
				
			Case M_ENABLE
				FUI_EnableGadget Handle( spn )
			Case M_DISABLE
				FUI_DisableGadget Handle( spn )
		End Select
		
		Return True
	EndIf
	txt.TextBox = Object.TextBox( ID )
	If txt <> Null
		Select Message
			Case M_HIDE
				FUI_HideGadget(ID)
			Case M_SHOW
				FUI_ShowGadget(ID)
			Case M_SETPOS
				If Param1 > ""
					FUI_SetGadgetX Handle( txt ), Int Param1
				EndIf
				If Param2 > ""
					FUI_SetGadgetY Handle( txt ), Int Param2
				EndIf
			Case M_SETPOS_X
				FUI_SetGadgetX Handle( txt ), Int Param1
			Case M_SETPOS_Y
				FUI_SetGadgetY Handle( txt ), Int Param1
			Case M_SETSIZE
				If Param1 > ""
					txt\W = Int Param1
				EndIf
				If Param2 > ""
					txt\H = Int Param2
				EndIf
				FUI_BuildTextBox txt
			Case M_SETSIZE_W
				txt\W = Int Param1
				FUI_BuildTextBox txt
			Case M_SETSIZE_H
				txt\H = Int Param1
				FUI_BuildTextBox txt
			Case M_SETTEXT
				txt\Caption = Param1
				FUI_BuildTextBox txt, 2
				
			Case M_GETPOS
				If Param1 = True
					Return txt\X
				ElseIf Param2 = True
					Return txt\Y
				EndIf
			Case M_GETPOS_X
				Return txt\X
			Case M_GETPOS_Y
				Return txt\Y
			Case M_GETSIZE
				If Param1 = True
					Return txt\W
				ElseIf Param2 = True
					Return txt\H
				EndIf
			Case M_GETSIZE_W
				Return txt\W
			Case M_GETSIZE_H
				Return txt\H
			Case M_GETTEXT
				Return txt\Caption
				
			Case M_ENABLE
				FUI_EnableGadget Handle( txt )
			Case M_DISABLE
				FUI_DisableGadget Handle( txt )
		End Select
		
		Return True
	EndIf
	tree.TreeView = Object.TreeView( ID )
	If tree <> Null
		Select Message
			Case M_SETPOS
				If Param1 > ""
					FUI_SetGadgetX Handle( tree ), Int Param1
				EndIf
				If Param2 > ""
					FUI_SetGadgetY Handle( tree ), Int Param2
				EndIf
			Case M_SETPOS_X
				FUI_SetGadgetX Handle( tree ), Int Param1
			Case M_SETPOS_Y
				FUI_SetGadgetY Handle( tree ), Int Param1
			Case M_SETSIZE
				If Param1 > ""
					tree\W = Int Param1
				EndIf
				If Param2 > ""
					tree\H = Int Param2
				EndIf
				FUI_BuildTreeView tree
			Case M_SETSIZE_W
				tree\W = Int Param1
				FUI_BuildTreeView tree
			Case M_SETSIZE_H
				tree\H = Int Param1
				FUI_BuildTreeView tree
				
			Case M_GETPOS
				If Param1 = True
					Return tree\X
				ElseIf Param2 = True
					Return tree\Y
				EndIf
			Case M_GETPOS_X
				Return tree\X
			Case M_GETPOS_Y
				Return tree\Y
			Case M_GETSIZE
				If Param1 = True
					Return tree\W
				ElseIf Param2 = True
					Return tree\H
				EndIf
			Case M_GETSIZE_W
				Return tree\W
			Case M_GETSIZE_H
				Return tree\H
			Case M_GETSELECTED
				For node.Node = Each Node
					If node\Owner = tree
						If node\Active = True
							Return Handle( node )
						EndIf
					EndIf
				Next
				Return 0
				
			Case M_RESET
				For node.Node = Each Node
					If node\Owner = tree
						FUI_DeleteGadget Handle( node )
					EndIf
				Next
				tree\CY = 0
			Case M_ENABLE
				FUI_EnableGadget Handle( tree )
			Case M_DISABLE
				FUI_DisableGadget Handle( tree )
		End Select
		
		Return True
	EndIf
	node.Node = Object.Node( ID )
	If node <> Null
		Select Message
			Case M_SETSTATE
				node\Open = Int Param1
				If node\Open = True
					For node2.Node = Each Node
						If node2\Owner = node\Owner And (node2\Layer <= node\Layer) And node2 <> node And node2\Y > node\Y
							node2\Y = node2\Y + node\OH
						EndIf
					Next
				Else
					For node2.Node = Each Node
						If node2\Owner = node\Owner And (node2\Layer <= node\Layer) And node2 <> node And node2\Y > node\Y
							node2\Y = node2\Y - node\OH
						EndIf
					Next
				EndIf
				tex = FUI_GadgetTexture( node\Mesh )
				EntityTexture node\Mesh, tex, node\Active*2 + node\Open*3
				FreeTexture tex
			Case M_SETSELECTED
				node\Active = Int Param1
				If node\Active = True
					For node2.Node = Each Node
						If node2\Owner = node\Owner And node2 <> node
							node2\Active = False
							tex = FUI_GadgetTexture( node2\Mesh )
							EntityTexture node2\Mesh, tex, node2\Active*2 + node2\Open*3
							FreeTexture tex
						EndIf
					Next
				EndIf
				tex = FUI_GadgetTexture( node\Mesh )
				EntityTexture node\Mesh, tex, node\Active*2 + node\Open*3
				FreeTexture tex
			Case M_SETTEXT
				node\Caption = Param1
				FUI_BuildTreeViewNode node
				
			Case M_GETSTATE
				Return node\Open
			Case M_GETSELECTED
				Return node\Active
			Case M_GETTEXT
				Return node\Caption
				
			Case M_OPEN
				node\Open = True
				For node2.Node = Each Node
					If node2\Owner = node\Owner And (node2\Layer <= node\Layer) And node2 <> node And node2\Y > node\Y
						node2\Y = node2\Y + node\OH
					EndIf
				Next
				tex = FUI_GadgetTexture( node\Mesh )
				EntityTexture node\Mesh, tex, node\Active*2 + node\Open*3
				FreeTexture tex
			Case M_CLOSE
				node\Open = False
				For node2.Node = Each Node
					If node2\Owner = node\Owner And (node2\Layer <= node\Layer) And node2 <> node And node2\Y > node\Y
						node2\Y = node2\Y - node\OH
					EndIf
				Next
				tex = FUI_GadgetTexture( node\Mesh )
				EntityTexture node\Mesh, tex, node\Active*2 + node\Open*3
				FreeTexture tex
			Case M_SELECT
				node\Active = True
				For node2.Node = Each Node
					If node2\Owner = node\Owner And node2 <> node
						node2\Active = False
						tex = FUI_GadgetTexture( node2\Mesh )
						EntityTexture node2\Mesh, tex, node2\Active*2 + node2\Open*3
						FreeTexture tex
					EndIf
				Next
				tex = FUI_GadgetTexture( node\Mesh )
				EntityTexture node\Mesh, tex, node\Active*2 + node\Open*3
				FreeTexture tex
			Case M_UNSELECT
				node\Active = False
				tex = FUI_GadgetTexture( node\Mesh )
				EntityTexture node\Mesh, tex, node\Active*2 + node\Open*3
				FreeTexture tex
				
			Case M_ENABLE
				FUI_EnableGadget Handle( node )
			Case M_DISABLE
				FUI_DisableGadget Handle( node )
		End Select
		
		Return True
	EndIf
	view.View = Object.View( ID )
	If view <> Null
		Select Message
			Case M_HIDE
				FUI_HideGadget(ID)
			Case M_SHOW
				FUI_ShowGadget(ID)
			Case M_SETPOS_X
				PositionEntity view\Cam, Param1, EntityY( view\Cam ), EntityZ( view\Cam )
			Case M_SETPOS_Y
				PositionEntity view\Cam, EntityX( view\Cam ), Param1, EntityZ( view\Cam )
			Case M_SETPOS_Z
				PositionEntity view\Cam, EntityX( view\Cam ), EntityY( view\Cam ), Param1
			Case M_SETROT_X
				RotateEntity view\Cam, Param1, EntityYaw( view\Cam ), EntityRoll( view\Cam )
			Case M_SETROT_Y
				RotateEntity view\Cam, EntityPitch( view\Cam ), Param1, EntityRoll( view\Cam )
			Case M_SETROT_Z
				RotateEntity view\Cam, EntityPitch( view\Cam ), EntityYaw( view\Cam ), Param1
			Case M_SETCOLOR
				CameraClsColor view\Cam, FUI_GetRed( Int Param1 ), FUI_GetGreen( Int Param1 ), FUI_GetBlue( Int Param1 )
			Case M_SETMODE
				Select Param1
					;Wireframe
					Case 1
						view\Wire = Int Param2
					;Projection Mode
					Case 2
						view\ProjMode = Int Param2
						CameraProjMode view\Cam, view\ProjMode
				End Select
				
			Case M_SETPOS
				If Param1 > ""
					FUI_SetGadgetX Handle( view ), Int Param1
				EndIf
				If Param2 > ""
					FUI_SetGadgetY Handle( view ), Int Param2
				EndIf
			Case M_SETPOS_X
				FUI_SetGadgetX Handle( view ), Int Param1
			Case M_SETPOS_Y
				FUI_SetGadgetY Handle( view ), Int Param1
			Case M_SETSIZE
				If Param1 > ""
					view\W = Int Param1
				EndIf
				If Param2 > ""
					view\H = Int Param2
				EndIf
				FUI_BuildView view
			Case M_SETSIZE_W
				view\W = Int Param1
				FUI_BuildView view
			Case M_SETSIZE_H
				view\H = Int Param1
				FUI_BuildView view
				
			Case M_GETPOS
				If Param1 = True
					Return view\X
				ElseIf Param2 = True
					Return view\Y
				EndIf
			Case M_GETSIZE
				If Param1 = True
					Return view\W
				ElseIf Param2 = True
					Return view\H
				EndIf
			Case M_GETPOS_X
				Return EntityX( view\Cam )
			Case M_GETPOS_Y
				Return EntityY( view\Cam )
			Case M_GETPOS_Z
				Return EntityZ( view\Cam )
			Case M_GETROT_X
				Return EntityPitch( view\Cam )
			Case M_GETROT_Y
				Return EntityYaw( view\Cam )
			Case M_GETROT_Z
				Return EntityRoll( view\Cam )
			Case M_GETMODE
				Select Param1
					;Wireframe
					Case 1
						Return view\Wire
					;Projection Mode
					Case 2
						Return view\ProjMode
				End Select
			Case M_GETCAMERA
				Return Ptr view\Cam
		End Select
		
		Return True
	EndIf
	
	Return False
	
End Function

Function FUI_SendMessageI( ID, Message, Param1$="", Param2$="" )
	
	Return Int( FUI_SendMessage( ID, Message, Param1$, Param2$ ) )
	
End Function

Function FUI_SendMessageF#( ID, Message, Param1$="", Param2$="" )
	
	Return Float( FUI_SendMessage( ID, Message, Param1$, Param2$ ) )
	
End Function
;#End Region

;#Region ---------- Create Gadgets -------------
Function FUI_Window( X, Y, W, H, Caption$="Window", Icon$=0, Flags=WS_TITLEBAR Or WS_ALLBUTTONS, Buttons=0 )
	
	gad.Window				= New Window
	
	;Arguments
	gad\X					= X
	gad\Y					= Y
	gad\W					= W
	gad\H					= H
	gad\Caption				= Caption$
	gad\Flags				= Flags
	
	;Backwards compatibility
	If (Buttons And 1) = 1
		gad\Flags = gad\Flags Or WS_CLOSEBUTTON
	EndIf
	If (Buttons And 2) = 2
		gad\Flags = gad\Flags Or WS_MINBUTTON
	EndIf
	
	;Icon
	If FUI_IsImageSource( Icon$ ) = True
		gad\Icon = LoadImage( Icon$ )
	Else
		If Int( Icon$ ) <> 0
			gad\Icon = Null
		EndIf
	EndIf
	
	app\Buffer				= GraphicsBuffer(  )
	
	Y = WINDOW_BORDER
	H = gad\H
	
	gad\FX = 1
	gad\FY = Y
	
	gad\OX					= X
	gad\OY					= Y
	gad\OW					= W - WINDOW_BORDER * 2
	gad\OH					= H - WINDOW_BORDER * 2
	
	OffY					= WINDOW_BORDER
	
	gad\Mesh				= FUI_CreateControl(  )
	If (gad\Flags And WS_TITLEBAR) = WS_TITLEBAR
		gad\TitleBar		= FUI_CreateControl( gad\Mesh )
		
		If (gad\Flags And WS_CLOSEBUTTON) = WS_CLOSEBUTTON
			gad\CloseBtn	= FUI_CreateControl( gad\TitleBar )
		EndIf
		If (gad\Flags And WS_MINBUTTON) = WS_MINBUTTON
			gad\MinBtn		= FUI_CreateControl( gad\TitleBar )
		EndIf
		
		gad\IconMesh			= FUI_CreateControl( gad\TitleBar )
		FUI_SetChildPosition gad\IconMesh, 0, 0
		
		gad\FY				= gad\FY + (TITLEBAR_HEIGHT - OffY)
		gad\OH				= gad\OH - (TITLEBAR_HEIGHT - OffY)
		
		OffY = 0
	EndIf
	If (gad\Flags And WS_MENUBAR) = WS_MENUBAR
		gad\MenuBar			= FUI_CreateControl( gad\Mesh )
		
		gad\FY				= gad\FY + (MENUBAR_HEIGHT - OffY)
		gad\OH				= gad\OH - (MENUBAR_HEIGHT - OffY)
		
		OffY = 0
	EndIf
	If (gad\Flags And WS_STATUSBAR) = WS_STATUSBAR
		gad\StatusBar		= FUI_CreateControl( gad\Mesh )
		gad\StatusBarText	= FUI_CreateControl( gad\StatusBar )
		
		gad\OH				= gad\OH - STATUSBAR_HEIGHT
	EndIf
	
	gad\MinMesh				= FUI_CreateControl(  )
	HideEntity gad\MinMesh
	
	;Build Window
	FUI_SetPosition gad\Mesh, gad\X, gad\Y
	FUI_SetPosition gad\MinMesh, gad\X, gad\Y
	FUI_BuildWindow gad, 1
	
	If RENDER_MODE = 1
		HideEntity gad\Mesh
	EndIf
	
	For win.Window = Each Window
		If win\Modal = False
			Insert gad Before win
			Exit
		EndIf
	Next
	Return Handle( gad )
	
End Function

Function FUI_MenuTitle( Owner, Caption$="Menu Title", W=0 )
	
	gad.MenuTitle			= New MenuTitle
	
	gad\Owner				= Object.Window( Owner )
	
	;Arguments
	gad\W					= W
	gad\H					= MENUTITLE_HEIGHT
	gad\Caption				= Caption$
	
	;Properties
	gad\X					= WINDOW_BORDER + MENUBAR_X + gad\Owner\MX
	gad\Y					= WINDOW_BORDER + MENUBAR_Y
	
	If (gad\Owner\Flags And WS_TITLEBAR) = WS_TITLEBAR
		gad\Y = gad\Y + TITLEBAR_HEIGHT - WINDOW_BORDER
	EndIf
	
	gad\DW					= 0
	gad\DH					= 0
	
	If gad\W = 0
		SetFont app\fntMenu
		gad\W = StringWidth( gad\Caption ) + 10
	EndIf
	
	gad\Owner\MX = gad\Owner\MX + gad\W + MENUBAR_SPACING
	
	gad\Mesh				= FUI_CreateControl( gad\Owner\Mesh )
	FUI_SetChildPosition gad\Mesh, gad\X, gad\Y
	
	gad\DropDown			= FUI_CreateControl( gad\Mesh )
	FUI_SetChildPosition gad\DropDown, MENUTITLE_DROPDOWN_X, gad\H + MENUTITLE_DROPDOWN_Y
	
	FUI_BuildMenuTitle gad
	
	Return Handle( gad )
	
End Function

Function FUI_MenuItem( Owner, Caption$="Menu Item", ShortCut$="", Icon$=0, Checkable=False, Checked=False, ID=0 )
	
	If Lower( Caption$ ) = "[recent]"
		Count = 0
		For rec.RecentFile = Each RecentFile
			If Count = RECENT_COUNT
				Caption$ = rec\displayname
				RECENT_COUNT = RECENT_COUNT + 1
				Exit
			Else
				Count = Count + 1
			EndIf
		Next
		If Lower( Caption$ ) = "[recent]"
			Return 0
		EndIf
	EndIf
	
	gad.MenuItem			= New MenuItem
	
	gad\Owner				= Object.MenuTitle( Owner )
	If gad\Owner = Null
		gad\Parent = Object.MenuItem( Owner )
		gad\Parent\HasChildren = True
		gad\Owner = gad\Parent\Owner
	EndIf
	
	;Arguments
	gad\Caption				= Caption$
	gad\ShortCut			= ShortCut$
	gad\Checkable			= Checkable
	gad\Checked				= Checked
	gad\ID					= ID
	
	If FUI_IsImageSource( Icon$ ) = True
		gad\Icon = LoadImage( Icon$ )
	Else
		If Int( Icon$ ) <> 0
			gad\Icon = Ptr Int( Icon$ )
		Else
			gad\Icon = Null
		EndIf
	EndIf
	
	gad\rf					= rec
	
	gad\X					= MENUDROPDOWN_PADDING
	gad\Y					= MENUDROPDOWN_PADDING
	
	If gad\Parent = Null
		;Arguments
		gad\Y					= gad\Y + gad\Owner\CY
		gad\W					= gad\Owner\DW
		
		gad\Mesh				= FUI_CreateControl( gad\Owner\DropDown )
		gad\DropDown			= FUI_CreateControl( gad\Mesh )
		
		If gad\Caption = "-"
			gad\H = MENUITEM_HEIGHT / 2
			gad\Owner\CY = gad\Owner\CY + MENUITEM_HEIGHT/2 + MENUDROPDOWN_SPACING
		Else
			gad\H = MENUITEM_HEIGHT
			gad\Owner\CY = gad\Owner\CY + MENUITEM_HEIGHT + MENUDROPDOWN_SPACING
			SetFont app\fntMenu
			W = StringWidth( gad\Caption ) + 20
			W = W + MENUDROPDOWN_STRIP_W
			If gad\ShortCut > ""
				W = W + 20 + StringWidth( gad\ShortCut )
			EndIf
			If W > gad\Owner\DW
				gad\Owner\DW = W
				gad\W = gad\Owner\DW
				For mnui.MenuItem = Each MenuItem
					If mnui\Owner = gad\Owner And mnui\Parent = Null And mnui <> gad
						mnui\W = gad\W
						FUI_BuildMenuItem mnui
					EndIf
				Next
			EndIf
		EndIf
		If gad\Owner\DH = 0
			gad\Owner\DH = gad\Owner\DH + gad\H
		Else
			gad\Owner\DH = gad\Owner\DH + gad\H + MENUDROPDOWN_SPACING
		EndIf
		FUI_BuildMenuTitle gad\Owner, 1
	Else
		;Arguments
		gad\Y					= gad\Y + gad\Parent\CY
		gad\W					= gad\Parent\DW
		
		gad\Mesh				= FUI_CreateControl( gad\Parent\DropDown )
		gad\DropDown			= FUI_CreateControl( gad\Mesh )
		
		If gad\Caption = "-"
			gad\H = MENUITEM_HEIGHT / 2
			gad\Parent\CY = gad\Parent\CY + MENUITEM_HEIGHT/2 + MENUDROPDOWN_SPACING
		Else
			gad\H = MENUITEM_HEIGHT
			gad\Parent\CY = gad\Parent\CY + MENUITEM_HEIGHT + MENUDROPDOWN_SPACING
			SetFont app\fntMenu
			W = StringWidth( gad\Caption ) + 20
			W = W + MENUDROPDOWN_STRIP_W
			If gad\ShortCut > ""
				W = W + 20 + StringWidth( gad\ShortCut )
			EndIf
			If W > gad\Parent\DW
				gad\Parent\DW = W
				gad\W = gad\Parent\DW
				For mnui.MenuItem = Each MenuItem
					If mnui\Parent = gad\Parent And mnui <> gad
						mnui\W = gad\W
						FUI_BuildMenuItem mnui
					EndIf
				Next
			EndIf
		EndIf
		If gad\Parent\DH = 0
			gad\Parent\DH = gad\Parent\DH + gad\H
		Else
			gad\Parent\DH = gad\Parent\DH + gad\H + MENUDROPDOWN_SPACING
		EndIf
		FUI_BuildMenuItem gad\Parent
	EndIf
	
	FUI_SetChildPosition gad\Mesh, gad\X, gad\Y
	FUI_BuildMenuItem gad
	
	If Lower( gad\Caption ) = "[recent]"
		FUI_DisableGadget Handle( gad )
	EndIf
	
	Return Handle( gad )
	
End Function

Function FUI_MenuBar( Owner )
	
	Return FUI_MenuItem( Owner, "-" )
	
End Function

Function FUI_ContextMenu(  )
	
	gad.ContextMenu			= New ContextMenu
	
	;Properties
	gad\DW					= 0
	gad\DH					= 0
	
	gad\DropDown			= FUI_CreateControl(  )
	HideEntity gad\DropDown
	
	FUI_BuildContextMenu gad
	
	Return Handle( gad )
	
End Function

Function FUI_ContextMenuItem( Owner, Caption$="Menu Item", ShortCut$="", Icon$=0, Checkable=False, Checked=False, ID=0 )
	
	If Lower( Caption$ ) = "[recent]"
		Count = 0
		For rec.RecentFile = Each RecentFile
			If Count = RECENT_COUNT
				Caption$ = rec\displayname
				RECENT_COUNT = RECENT_COUNT + 1
				Exit
			Else
				Count = Count + 1
			EndIf
		Next
		If Lower( Caption$ ) = "[recent]"
			Return 0
		EndIf
	EndIf
	
	gad.ContextMenuItem		= New ContextMenuItem
	
	gad\Owner				= Object.ContextMenu( Owner )
	If gad\Owner = Null
		gad\Parent = Object.ContextMenuItem( Owner )
		gad\Parent\HasChildren = True
		gad\Owner = gad\Parent\Owner
	EndIf
	
	;Arguments
	gad\Caption				= Caption$
	gad\ShortCut			= ShortCut$
	gad\Checkable			= Checkable
	gad\Checked				= Checked
	gad\ID					= ID
	
	If FUI_IsImageSource( Icon$ ) = True
		gad\Icon = LoadImage( Icon$ )
	Else
		If Int( Icon$ ) <> 0
			gad\Icon = Ptr Int( Icon$ )
		EndIf
	EndIf
	
	gad\rf					= rec
	
	gad\X					= MENUDROPDOWN_PADDING
	gad\Y					= MENUDROPDOWN_PADDING
	
	If gad\Parent = Null
		;Arguments
		gad\Y					= gad\Y + gad\Owner\CY
		gad\W					= gad\Owner\DW
		
		gad\Mesh				= FUI_CreateControl( gad\Owner\DropDown )
		gad\DropDown			= FUI_CreateControl( gad\Mesh )
		
		If gad\Caption = "-"
			gad\H = MENUITEM_HEIGHT / 2
			gad\Owner\CY = gad\Owner\CY + MENUITEM_HEIGHT/2 + MENUDROPDOWN_SPACING
		Else
			gad\H = MENUITEM_HEIGHT
			gad\Owner\CY = gad\Owner\CY + MENUITEM_HEIGHT + MENUDROPDOWN_SPACING
			SetFont app\fntMenu
			W = StringWidth( gad\Caption ) + 20
			W = W + MENUDROPDOWN_STRIP_W
			If gad\ShortCut > ""
				W = W + 20 + StringWidth( gad\ShortCut )
			EndIf
			If W > gad\Owner\DW
				gad\Owner\DW = W
				gad\W = gad\Owner\DW
				For mnui.ContextMenuItem = Each ContextMenuItem
					If mnui\Owner = gad\Owner And mnui\Parent = Null And mnui <> gad
						mnui\W = gad\W
						FUI_BuildContextMenuItem mnui
					EndIf
				Next
			EndIf
		EndIf
		If gad\Owner\DH = 0
			gad\Owner\DH = gad\Owner\DH + gad\H
		Else
			gad\Owner\DH = gad\Owner\DH + gad\H + MENUDROPDOWN_SPACING
		EndIf
		FUI_BuildContextMenu gad\Owner
	Else
		;Arguments
		gad\Y					= gad\Y + gad\Parent\CY
		gad\W					= gad\Parent\DW
		
		gad\Mesh				= FUI_CreateControl( gad\Parent\DropDown )
		gad\DropDown			= FUI_CreateControl( gad\Mesh )
		
		If gad\Caption = "-"
			gad\H = MENUITEM_HEIGHT / 2
			gad\Parent\CY = gad\Parent\CY + MENUITEM_HEIGHT/2 + MENUDROPDOWN_SPACING
		Else
			gad\H = MENUITEM_HEIGHT
			gad\Parent\CY = gad\Parent\CY + MENUITEM_HEIGHT + MENUDROPDOWN_SPACING
			
			SetFont app\fntMenu
			W = StringWidth( gad\Caption ) + 20
			W = W + MENUDROPDOWN_STRIP_W
			If gad\ShortCut > ""
				W = W + 20 + StringWidth( gad\ShortCut )
			EndIf
			If W > gad\Parent\DW
				gad\Parent\DW = W
				gad\W = gad\Parent\DW
				For mnui.ContextMenuItem = Each ContextMenuItem
					If mnui\Parent <> Null
						PositionEntity mnui\Mesh, MENUDROPDOWN_PADDING, EntityY( mnui\Mesh ), 0.0
					EndIf
					If mnui\Parent = gad\Parent And mnui <> gad
						mnui\W = gad\W
						FUI_BuildContextMenuItem mnui
					EndIf
				Next
			EndIf
		EndIf
		If gad\Parent\DH = 0
			gad\Parent\DH = gad\Parent\DH + gad\H
		Else
			gad\Parent\DH = gad\Parent\DH + gad\H + MENUDROPDOWN_SPACING
		EndIf
		FUI_BuildContextMenuItem gad\Parent
	EndIf
	
	FUI_SetChildPosition gad\Mesh, gad\X, gad\Y
	FUI_BuildContextMenuItem gad
	
	If Lower( gad\Caption ) = "[recent]"
		FUI_DisableGadget Handle( gad )
	EndIf
	
	Return Handle( gad )
	
End Function

Function FUI_ContextMenuBar( Owner )
	
	Return FUI_ContextMenuItem( Owner, "-" )
	
End Function

Function FUI_Region( Owner, X, Y, W, H )
	
	gad.Region				= New Region
	
	gad\Owner				= Object.Window( Owner )
	If gad\Owner = Null
		gad\Parent = Owner
		
		reg.Region = Object.Region( gad\Parent )
		tabp.TabPage = Object.TabPage( gad\Parent )
		pan.Panel = Object.Panel( gad\Parent )
		grp.GroupBox = Object.GroupBox( gad\Parent )
		If reg <> Null
			OX = reg\FX
			OY = reg\FY
			OW = reg\FW
			OH = reg\FH
			
			Parent.BBEntity = reg\Mesh
			gad\Owner = reg\Owner
		ElseIf tabp <> Null
			OX = tabp\Owner\FX
			OY = tabp\Owner\FY
			OW = tabp\Owner\FW
			OH = tabp\Owner\FH
			
			Parent = tabp\Owner\Mesh
			gad\Owner = tabp\Owner\Owner
		ElseIf pan <> Null
			OX = pan\FX
			OY = pan\FY
			OW = pan\FW
			OH = pan\FH
			
			Parent = pan\Mesh
			gad\Owner = pan\Owner
		ElseIf grp <> Null
			OX = grp\FX
			OY = grp\FY
			OW = grp\FW
			OH = grp\FH
			
			Parent = grp\Mesh
			gad\Owner = grp\Owner
		EndIf
	Else
		OX = gad\Owner\FX
		OY = gad\Owner\FY
		OW = gad\Owner\FW
		OH = gad\Owner\FH
		
		Parent.BBEntity = gad\Owner\Mesh
	EndIf
	
	;Arguments
	gad\X					= X
	gad\Y					= Y
	gad\W					= W
	gad\H					= H
	
	If gad\X < 0
		gad\X = OW / Float(100.0 / Abs( gad\X ))
	EndIf
	If gad\Y < 0
		gad\Y = OH / Float(100.0 / Abs( gad\Y ))
	EndIf
	If PERCENT_CLIENT = True
		WOW = OW - gad\X * 2
		WOH = OH - gad\Y * 2
	Else
		WOW = OW
		WOH = OH
	EndIf
	If gad\W < 0
		gad\W = WOW / Float(100.0 / Abs( gad\W ))
	EndIf
	If gad\H < 0
		gad\H = WOH / Float(100.0 / Abs( gad\H ))
	EndIf
	
	gad\FX					= 0
	gad\FY					= 0
	
	gad\OX					= gad\X + OX
	gad\OY					= gad\Y + OY
	gad\OW					= W
	gad\OH					= H
	
	gad\Mesh				= FUI_CreateControl( Parent )
	FUI_SetChildPosition gad\Mesh, OX + gad\X, OY + gad\Y
	
	FUI_BuildRegion gad
	
	Return Handle( gad )
	
End Function

Function FUI_Tab( Owner, X, Y, W, H )
	
	gad.Tab					= New Tab
	
	gad\Owner				= Object.Window( Owner )
	If gad\Owner = Null
		gad\Parent = Owner
		
		reg.Region = Object.Region( gad\Parent )
		tabp.TabPage = Object.TabPage( gad\Parent )
		pan.Panel = Object.Panel( gad\Parent )
		grp.GroupBox = Object.GroupBox( gad\Parent )
		If reg <> Null
			OX = reg\FX
			OY = reg\FY
			OW = reg\FW
			OH = reg\FH
			
			Parent.BBEntity = reg\Mesh
			gad\Owner = reg\Owner
		ElseIf tabp <> Null
			OX = tabp\Owner\FX
			OY = tabp\Owner\FY
			OW = tabp\Owner\FW
			OH = tabp\Owner\FH
			
			Parent = tabp\Owner\Mesh
			gad\Owner = tabp\Owner\Owner
		ElseIf pan <> Null
			OX = pan\FX
			OY = pan\FY
			OW = pan\FW
			OH = pan\FH
			
			Parent = pan\Mesh
			gad\Owner = pan\Owner
		ElseIf grp <> Null
			OX = grp\FX
			OY = grp\FY
			OW = grp\FW
			OH = grp\FH
			
			Parent = grp\Mesh
			gad\Owner = grp\Owner
		EndIf
	Else
		OX = gad\Owner\FX
		OY = gad\Owner\FY
		OW = gad\Owner\FW
		OH = gad\Owner\FH
		
		Parent = gad\Owner\Mesh
	EndIf
	
	;Arguments
	gad\X					= X
	gad\Y					= Y
	gad\W					= W
	gad\H					= H
	
	If gad\X < 0
		gad\X = OW / Float(100.0 / Abs( gad\X ))
	EndIf
	If gad\Y < 0
		gad\Y = OH / Float(100.0 / Abs( gad\Y ))
	EndIf
	If PERCENT_CLIENT = True
		WOW = OW - gad\X * 2
		WOH = OH - gad\Y * 2
	Else
		WOW = OW
		WOH = OH
	EndIf
	If gad\W < 0
		gad\W = WOW / Float(100.0 / Abs( gad\W ))
	EndIf
	If gad\H < 0
		gad\H = WOH / Float(100.0 / Abs( gad\H ))
	EndIf
	
	gad\FX					= 1
	gad\FY					= TABPAGE_HEIGHT + 1
	
	gad\OX					= gad\X + OX
	gad\OY					= gad\Y + OY
	gad\OW					= W
	gad\OH					= H
	
	;Properties
	gad\DX					= 0
	
	gad\Mesh				= FUI_CreateControl( Parent )
	FUI_SetChildPosition gad\Mesh, gad\X + OX, gad\Y + OY
	
	FUI_BuildTab gad
	
	Return Handle( gad )
	
End Function

Function FUI_TabPage( Owner, Caption$="Tab Page", Icon$=0 )
	
	gad.TabPage				= New TabPage
	
	gad\Owner				= Object.Tab( Owner )
	
	;Arguments
	gad\Caption				= Caption
	
	If FUI_IsImageSource( Icon$ ) = True
		gad\Icon = LoadImage( Icon$ )
	Else
		If Int( Icon$ ) <> 0
			gad\Icon = Ptr Int( Icon$ )
		EndIf
	EndIf
	
	If gad\Icon <> Null
		gad\W = ImageWidth( gad\Icon ) + 10
	Else
		SetFont app\fntGadget
		gad\W = StringWidth( gad\Caption ) + 20
	EndIf
	gad\H					= TABPAGE_HEIGHT
	
	gad\X = gad\Owner\DX
	If gad\Owner\actTabPage = Null
		gad\Owner\actTabPage = gad
	EndIf
	gad\Owner\DX = gad\Owner\DX + gad\W
	
	gad\Mesh				= FUI_CreateControl( gad\Owner\Mesh )
	FUI_SetChildPosition gad\Mesh, gad\X, 0
	
	gad\IconMesh			= FUI_CreateControl( gad\Mesh )
	FUI_SetChildPosition gad\IconMesh, 0, 0
	
	FUI_BuildTabPage gad
	
	Return Handle( gad )
	
End Function

Function FUI_Panel( Owner, X, Y, W, H, Caption$="Panel" )
	
	gad.Panel				= New Panel
	
	gad\Owner				= Object.Window( Owner )
	If gad\Owner = Null
		gad\Parent = Owner
		
		reg.Region = Object.Region( gad\Parent )
		tabp.TabPage = Object.TabPage( gad\Parent )
		pan.Panel = Object.Panel( gad\Parent )
		grp.GroupBox = Object.GroupBox( gad\Parent )
		If reg <> Null
			OX = reg\FX
			OY = reg\FY
			OW = reg\FW
			OH = reg\FH
			
			Parent.BBEntity = reg\Mesh
			gad\Owner = reg\Owner
		ElseIf tabp <> Null
			OX = tabp\Owner\FX
			OY = tabp\Owner\FY
			OW = tabp\Owner\FW
			OH = tabp\Owner\FH
			
			Parent = tabp\Owner\Mesh
			gad\Owner = tabp\Owner\Owner
		ElseIf pan <> Null
			OX = pan\FX
			OY = pan\FY
			OW = pan\FW
			OH = pan\FH
			
			Parent = pan\Mesh
			gad\Owner = pan\Owner
		ElseIf grp <> Null
			OX = grp\FX
			OY = grp\FY
			OW = grp\FW
			OH = grp\FH
			
			Parent = grp\Mesh
			gad\Owner = grp\Owner
		EndIf
	Else
		OX = gad\Owner\FX
		OY = gad\Owner\FY
		OW = gad\Owner\FW
		OH = gad\Owner\FH
		
		Parent = gad\Owner\Mesh
	EndIf
	
	;Arguments
	gad\X					= X
	gad\Y					= Y
	gad\W					= W
	gad\H					= H
	gad\Caption				= Caption$
	
	If gad\X < 0
		gad\X = OW / Float(100.0 / Abs( gad\X ))
	EndIf
	If gad\Y < 0
		gad\Y = OH / Float(100.0 / Abs( gad\Y ))
	EndIf
	If PERCENT_CLIENT = True
		WOW = OW - gad\X * 2
		WOH = OH - gad\Y * 2
	Else
		WOW = OW
		WOH = OH
	EndIf
	If gad\W < 0
		gad\W = WOW / Float(100.0 / Abs( gad\W ))
	EndIf
	If gad\H < 0
		gad\H = WOH / Float(100.0 / Abs( gad\H ))
	EndIf
	
	gad\FX					= 1
	gad\FY					= PANEL_HEIGHT
	
	gad\OX					= gad\X + OX
	gad\OY					= gad\Y + OY
	gad\OW					= W
	gad\OH					= H
	
	SetFont app\fntGadget
	
	gad\Mesh				= FUI_CreateControl( Parent )
	FUI_SetChildPosition gad\Mesh, OX + gad\X, OY + gad\Y
	
	gad\DropDown			= FUI_CreateControl( gad\Mesh )
	FUI_SetChildPosition gad\DropDown, 0, PANEL_HEIGHT - 1
	
	FUI_BuildPanel gad
	
	Return Handle( gad )
	
End Function

Function FUI_Button( Owner, X, Y, W, H, Caption$="Button", Icon$=0, ID=0, Flags=CS_BORDER )
	
	gad.Button				= New Button
	
	gad\Owner				= Object.Window( Owner )
	If gad\Owner = Null
		gad\Parent = Owner
		
		reg.Region = Object.Region( gad\Parent )
		tabp.TabPage = Object.TabPage( gad\Parent )
		pan.Panel = Object.Panel( gad\Parent )
		grp.GroupBox = Object.GroupBox( gad\Parent )
		If reg <> Null
			OX = reg\FX
			OY = reg\FY
			OW = reg\FW
			OH = reg\FH
			
			Parent.BBEntity = reg\Mesh
			gad\Owner = reg\Owner
		ElseIf tabp <> Null
			OX = tabp\Owner\FX
			OY = tabp\Owner\FY
			OW = tabp\Owner\FW
			OH = tabp\Owner\FH
			
			Parent = tabp\Owner\Mesh
			gad\Owner = tabp\Owner\Owner
		ElseIf pan <> Null
			OX = pan\FX
			OY = pan\FY
			OW = pan\FW
			OH = pan\FH
			
			Parent = pan\Mesh
			gad\Owner = pan\Owner
		ElseIf grp <> Null
			OX = grp\FX
			OY = grp\FY
			OW = grp\FW
			OH = grp\FH
			
			Parent = grp\Mesh
			gad\Owner = grp\Owner
		EndIf
	Else
		OX = gad\Owner\FX
		OY = gad\Owner\FY
		OW = gad\Owner\FW
		OH = gad\Owner\FH
		
		Parent = gad\Owner\Mesh
	EndIf
	
	;Arguments
	gad\X					= X
	gad\Y					= Y
	gad\W					= W
	gad\H					= H
	gad\Caption				= Caption$
	
	If gad\X < 0
		gad\X = OW / Float(100.0 / Abs( gad\X ))
	EndIf
	If gad\Y < 0
		gad\Y = OH / Float(100.0 / Abs( gad\Y ))
	EndIf
	If PERCENT_CLIENT = True
		WOW = OW - gad\X * 2
		WOH = OH - gad\Y * 2
	Else
		WOW = OW
		WOH = OH
	EndIf
	If gad\W < 0
		gad\W = WOW / Float(100.0 / Abs( gad\W ))
	EndIf
	If gad\H < 0
		gad\H = WOH / Float(100.0 / Abs( gad\H ))
	EndIf
	
	If FUI_IsImageSource( Icon$ ) = True
		gad\Icon = LoadImage( Icon$ )
	Else
		If Int( Icon$ ) <> 0
			gad\Icon = Ptr Int( Icon$ )
		EndIf
	EndIf
	
	gad\ID					= ID
	gad\Flags				= Flags
	
	;Anchor
	gad\OX					= gad\X + OX
	gad\OY					= gad\Y + OY
	gad\OW					= gad\W
	gad\OH					= gad\H
	
	gad\Mesh				= FUI_CreateControl( Parent )
	FUI_SetChildPosition gad\Mesh, OX + gad\X, OY + gad\Y
	
	FUI_BuildButton gad
	
	Return Handle( gad )
	
End Function

Function FUI_CheckBox( Owner, X, Y, Caption$="Check Box", Checked=False )
	
	gad.CheckBox			= New CheckBox
	
	gad\Owner				= Object.Window( Owner )
	If gad\Owner = Null
		gad\Parent = Owner
		
		reg.Region = Object.Region( gad\Parent )
		tabp.TabPage = Object.TabPage( gad\Parent )
		pan.Panel = Object.Panel( gad\Parent )
		grp.GroupBox = Object.GroupBox( gad\Parent )
		If reg <> Null
			OX = reg\FX
			OY = reg\FY
			
			Parent.BBEntity = reg\Mesh
			gad\Owner = reg\Owner
		ElseIf tabp <> Null
			OX = tabp\Owner\FX
			OY = tabp\Owner\FY
			
			Parent = tabp\Owner\Mesh
			gad\Owner = tabp\Owner\Owner
		ElseIf pan <> Null
			OX = pan\FX
			OY = pan\FY
			
			Parent = pan\Mesh
			gad\Owner = pan\Owner
		ElseIf grp <> Null
			OX = grp\FX
			OY = grp\FY
			
			Parent = grp\Mesh
			gad\Owner = grp\Owner
		EndIf
	Else
		OX = gad\Owner\FX
		OY = gad\Owner\FY
		
		Parent = gad\Owner\Mesh
	EndIf
	
	;Arguments
	gad\X					= X
	gad\Y					= Y
	gad\Caption				= Caption
	gad\Checked				= Checked
	
	If gad\X < 0
		gad\X = OW / Float(100.0 / Abs( gad\X ))
	EndIf
	If gad\Y < 0
		gad\Y = OH / Float(100.0 / Abs( gad\Y ))
	EndIf
	
	SetFont app\fntGadget
	gad\H					= FontHeight(  )
	gad\W					= StringWidth( gad\Caption ) + gad\H + 3
	
	;Anchor
	gad\OX					= gad\X + OX
	gad\OY					= gad\Y + OY
	
	gad\Mesh				= FUI_CreateControl( Parent )
	FUI_SetChildPosition gad\Mesh, OX + gad\X, OY + gad\Y
	
	FUI_BuildCheckBox gad

	Return Handle( gad )
	
End Function

Function FUI_ComboBox( Owner, X, Y, W, H, DispItems=0 )
	
	gad.ComboBox			= New ComboBox
	
	gad\Owner				= Object.Window( Owner )
	If gad\Owner = Null
		gad\Parent = Owner
		
		reg.Region = Object.Region( gad\Parent )
		tabp.TabPage = Object.TabPage( gad\Parent )
		pan.Panel = Object.Panel( gad\Parent )
		grp.GroupBox = Object.GroupBox( gad\Parent )
		If reg <> Null
			OX = reg\FX
			OY = reg\FY
			OW = reg\FW
			OH = reg\FH
			
			Parent.BBEntity = reg\Mesh
			gad\Owner = reg\Owner
		ElseIf tabp <> Null
			OX = tabp\Owner\FX
			OY = tabp\Owner\FY
			OW = tabp\Owner\FW
			OH = tabp\Owner\FH
			
			Parent = tabp\Owner\Mesh
			gad\Owner = tabp\Owner\Owner
		ElseIf pan <> Null
			OX = pan\FX
			OY = pan\FY
			OW = pan\FW
			OH = pan\FH
			
			Parent = pan\Mesh
			gad\Owner = pan\Owner
		ElseIf grp <> Null
			OX = grp\FX
			OY = grp\FY
			OW = grp\FW
			OH = grp\FH
			
			Parent = grp\Mesh
			gad\Owner = grp\Owner
		EndIf
	Else
		OX = gad\Owner\FX
		OY = gad\Owner\FY
		OW = gad\Owner\FW
		OH = gad\Owner\FH
		
		Parent = gad\Owner\Mesh
	EndIf
	
	;Arguments
	gad\X					= X
	gad\Y					= Y
	gad\W					= W
	gad\H					= H
	gad\DispItems			= DispItems
	
	If gad\X < 0
		gad\X = OW / Float(100.0 / Abs( gad\X ))
	EndIf
	If gad\Y < 0
		gad\Y = OH / Float(100.0 / Abs( gad\Y ))
	EndIf
	If PERCENT_CLIENT = True
		WOW = OW - gad\X * 2
		WOH = OH - gad\Y * 2
	Else
		WOW = OW
		WOH = OH
	EndIf
	If gad\W < 0
		gad\W = WOW / Float(100.0 / Abs( gad\W ))
	EndIf
	If gad\H < 0
		gad\H = WOH / Float(100.0 / Abs( gad\H ))
	EndIf
	
	;Anchor
	gad\OX					= gad\X + OX
	gad\OY					= gad\Y + OY
	gad\OW					= W
	gad\OH					= H
	
	gad\DX					= 0
	gad\DY					= gad\H - 1
	
	gad\DW					= gad\W
	
	gad\CW					= gad\DW
	If gad\DispItems = 0
		gad\DH				= 0
	Else
		gad\DH				= gad\DispItems * COMBOBOXITEM_HEIGHT
	EndIf
	
	gad\Mesh				= FUI_CreateControl( Parent )
	FUI_SetChildPosition gad\Mesh, OX + gad\X, OY + gad\Y
	
	gad\DropDown			= FUI_CreateControl( gad\Mesh )
	FUI_SetChildPosition gad\DropDown, gad\DX, gad\DY
	
	HideEntity gad\DropDown
	
	;Scroll Bars
	gad\VScroll				= New VScrollBar
	gad\VScroll\W			= gad\DH + COMBOBOX_PADDING*2 - 2
	gad\VScroll\H			= 14
	gad\VScroll\SW			= gad\VScroll\W / 2
	gad\VScroll\SH			= gad\VScroll\H - 1
	
	gad\VScroll\Mesh		= FUI_CreateControl( gad\DropDown )
	
	gad\VScroll\Btn1		= FUI_CreateControl( gad\VScroll\Mesh )
	FUI_SetChildPosition gad\VScroll\Btn1, 1, 0
	gad\VScroll\Btn2		= FUI_CreateControl( gad\VScroll\Mesh )
	FUI_SetChildPosition gad\VScroll\Btn2, 1, gad\VScroll\W - (gad\VScroll\H-1)
	gad\VScroll\ScrollBar	= FUI_CreateControl( gad\VScroll\Mesh )
	FUI_SetChildPosition gad\VScroll\ScrollBar, 1, (gad\VScroll\H-1) + 1
	
	FUI_BuildComboBox gad, 0
	FUI_BuildVScrollBar gad\VScroll
	
	Return Handle( gad )
	
End Function

Function FUI_ComboBoxItem( Owner, Caption$="Combo Box Item", Icon$=0)
	
	gad.ComboBoxItem		= New ComboBoxItem
	
	gad\Owner				= Object.ComboBox( Owner )
	
	;Arguments
	gad\Caption				= Caption$
	
	If FUI_IsImageSource( Icon$ ) = True
		gad\Icon = LoadImage( Icon$ )
	Else
		If Int( Icon$ ) <> 0
			gad\Icon = Ptr Int( Icon$ )
		EndIf
	EndIf

	gad\Y = gad\Owner\CY
	gad\Mesh				= FUI_CreateControl( gad\Owner\DropDown )
	FUI_SetChildPosition gad\Mesh, COMBOBOX_PADDING, gad\Owner\CY + COMBOBOX_PADDING
	
	gad\IconMesh			= FUI_CreateControl( gad\Mesh )
	FUI_SetChildPosition gad\IconMesh, 0, 0
	
	If gad\Caption <> "-"
		SetFont app\fntGadget
		If COMBOBOX_STRIP*COMBOBOX_STRIP_W + StringWidth( gad\Caption ) + 10 > gad\Owner\DW
			gad\Owner\DW = COMBOBOX_STRIP*COMBOBOX_STRIP_W + StringWidth( gad\Caption ) + 10
			
			For cboi.ComboBoxItem = Each ComboBoxItem
				If cboi\Owner = gad\Owner And cboi <> gad
					FUI_BuildComboBoxItem cboi
				EndIf
			Next
		EndIf
	EndIf
	
	If gad\Caption = "-"
		gad\Owner\CY = gad\Owner\CY + COMBOBOXITEM_HEIGHT / 2 + COMBOBOX_SPACING
		gad\Owner\CH = gad\Owner\CH + COMBOBOXITEM_HEIGHT / 2 + COMBOBOX_SPACING
	Else
		gad\Owner\CY = gad\Owner\CY + COMBOBOXITEM_HEIGHT + COMBOBOX_SPACING
		gad\Owner\CH = gad\Owner\CH + COMBOBOXITEM_HEIGHT + COMBOBOX_SPACING
	EndIf
	
	If gad\Owner\DispItems = 0
		If gad\Caption = "-"
			gad\Owner\DH = gad\Owner\DH + COMBOBOXITEM_HEIGHT / 2 + COMBOBOX_SPACING
		Else
			gad\Owner\DH = gad\Owner\DH + COMBOBOXITEM_HEIGHT + COMBOBOX_SPACING
		EndIf
	EndIf
	
	FUI_BuildComboBoxItem gad
	FUI_BuildComboBox gad\Owner, 1
	
	Return Handle( gad )
	
End Function

Function FUI_GroupBox( Owner, X, Y, W, H, Caption$="Group Box" )
	
	gad.GroupBox			= New GroupBox
	
	gad\Owner				= Object.Window( Owner )
	If gad\Owner = Null
		gad\Parent = Owner
		
		reg.Region = Object.Region( gad\Parent )
		tabp.TabPage = Object.TabPage( gad\Parent )
		pan.Panel = Object.Panel( gad\Parent )
		grp.GroupBox = Object.GroupBox( gad\Parent )
		If reg <> Null
			OX = reg\FX
			OY = reg\FY
			OW = reg\FW
			OH = reg\FH
			
			Parent.BBEntity = reg\Mesh
			gad\Owner = reg\Owner
		ElseIf tabp <> Null
			OX = tabp\Owner\FX
			OY = tabp\Owner\FY
			OW = tabp\Owner\FW
			OH = tabp\Owner\FH
			
			Parent = tabp\Owner\Mesh
			gad\Owner = tabp\Owner\Owner
		ElseIf pan <> Null
			OX = pan\FX
			OY = pan\FY
			OW = pan\FW
			OH = pan\FH
			
			Parent = pan\Mesh
			gad\Owner = pan\Owner
		ElseIf grp <> Null
			OX = grp\FX
			OY = grp\FY
			OW = grp\FW
			OH = grp\FH
			
			Parent = grp\Mesh
			gad\Owner = grp\Owner
		EndIf
	Else
		OX = gad\Owner\FX
		OY = gad\Owner\FY
		OW = gad\Owner\FW
		OH = gad\Owner\FH
		
		Parent = gad\Owner\Mesh
	EndIf
	
	;Arguments
	gad\X					= X + OX
	gad\Y					= Y + OY
	gad\W					= W
	gad\H					= H
	gad\Caption				= Caption$
	
	If gad\X < 0
		gad\X = OW / Float(100.0 / Abs( gad\X ))
	EndIf
	If gad\Y < 0
		gad\Y = OH / Float(100.0 / Abs( gad\Y ))
	EndIf
	If PERCENT_CLIENT = True
		WOW = OW - gad\X * 2
		WOH = OH - gad\Y * 2
	Else
		WOW = OW
		WOH = OH
	EndIf
	If gad\W < 0
		gad\W = WOW / Float(100.0 / Abs( gad\W ))
	EndIf
	If gad\H < 0
		gad\H = WOH / Float(100.0 / Abs( gad\H ))
	EndIf
	
	SetFont app\fntGadget
	gad\FX					= 2
	gad\FY					= FontHeight()
	
	;Anchor
	gad\OX					= gad\X + OX
	gad\OY					= gad\Y + OY
	gad\OW					= W
	gad\OH					= H
	
	;Properties
	gad\Mesh				= FUI_CreateControl( Parent )
	FUI_SetChildPosition gad\Mesh, gad\X, gad\Y
	
	gad\MeshText			= FUI_CreateControl( gad\Mesh )
	FUI_SetChildPosition gad\MeshText, 8, 0
	
	FUI_BuildGroupBox gad
	
	Return Handle( gad )
	
End Function

Function FUI_ImageBox( Owner, X, Y, W, H, Image$=0, Flags=CS_BORDER)
	
	gad.ImageBox			= New ImageBox
	
	gad\Owner				= Object.Window( Owner )
	If gad\Owner = Null
		gad\Parent = Owner
		
		reg.Region = Object.Region( gad\Parent )
		tabp.TabPage = Object.TabPage( gad\Parent )
		pan.Panel = Object.Panel( gad\Parent )
		grp.GroupBox = Object.GroupBox( gad\Parent )
		If reg <> Null
			OX = reg\FX
			OY = reg\FY
			OW = reg\FW
			OH = reg\FH
			
			Parent.BBEntity = reg\Mesh
			gad\Owner = reg\Owner
		ElseIf tabp <> Null
			OX = tabp\Owner\FX
			OY = tabp\Owner\FY
			OW = tabp\Owner\FW
			OH = tabp\Owner\FH
			
			Parent = tabp\Owner\Mesh
			gad\Owner = tabp\Owner\Owner
		ElseIf pan <> Null
			OX = pan\FX
			OY = pan\FY
			OW = pan\FW
			OH = pan\FH
			
			Parent = pan\Mesh
			gad\Owner = pan\Owner
		ElseIf grp <> Null
			OX = grp\FX
			OY = grp\FY
			OW = grp\FW
			OH = grp\FH
			
			Parent = grp\Mesh
			gad\Owner = grp\Owner
		EndIf
	Else
		OX = gad\Owner\FX
		OY = gad\Owner\FY
		OW = gad\Owner\FW
		OH = gad\Owner\FH
		
		Parent = gad\Owner\Mesh
	EndIf
	
	;Arguments
	gad\X					= X
	gad\Y					= Y
	gad\W					= W
	gad\H					= H
	gad\Flags				= Flags
	
	If gad\X < 0
		gad\X = OW / Float(100.0 / Abs( gad\X ))
	EndIf
	If gad\Y < 0
		gad\Y = OH / Float(100.0 / Abs( gad\Y ))
	EndIf
	If PERCENT_CLIENT = True
		WOW = OW - gad\X * 2
		WOH = OH - gad\Y * 2
	Else
		WOW = OW
		WOH = OH
	EndIf
	If gad\W < 0
		gad\W = WOW / Float(100.0 / Abs( gad\W ))
	EndIf
	If gad\H < 0
		gad\H = WOH / Float(100.0 / Abs( gad\H ))
	EndIf
	
	;Anchor
	gad\OX					= gad\X + OX
	gad\OY					= gad\Y + OY
	gad\OW					= W
	gad\OH					= H
	
	If FileType( Image$ ) = 1
		gad\Image = LoadImage( Image$ )
		
		If (gad\Flags And CS_RESIZE) = CS_RESIZE
			ResizeImage gad\Image, gad\W, gad\H
		EndIf
	ElseIf Image$ <> 0
		gad\Image = Ptr Int( Image$ )
		
		If (gad\Flags And CS_RESIZE) = CS_RESIZE
			ResizeImage gad\Image, gad\W, gad\H
		EndIf
	EndIf
	
	gad\Mesh				= FUI_CreateControl( Parent )
	FUI_SetChildPosition gad\Mesh, OX + gad\X, OY + gad\Y
	
	FUI_BuildImageBox gad
	
	Return Handle( gad )
	
End Function

Function FUI_Label( Owner, X, Y, Caption$="Label", Align=ALIGN_LEFT )
	
	gad.Label				= New Label
	
	gad\Owner				= Object.Window( Owner )
	If gad\Owner = Null
		gad\Parent = Owner
		
		reg.Region = Object.Region( gad\Parent )
		tabp.TabPage = Object.TabPage( gad\Parent )
		pan.Panel = Object.Panel( gad\Parent )
		grp.GroupBox = Object.GroupBox( gad\Parent )
		If reg <> Null
			OX = reg\FX
			OY = reg\FY
			
			Parent.BBEntity = reg\Mesh
			gad\Owner = reg\Owner
		ElseIf tabp <> Null
			OX = tabp\Owner\FX
			OY = tabp\Owner\FY
			
			Parent = tabp\Owner\Mesh
			gad\Owner = tabp\Owner\Owner
		ElseIf pan <> Null
			OX = pan\FX
			OY = pan\FY
			
			Parent = pan\Mesh
			gad\Owner = pan\Owner
		ElseIf grp <> Null
			OX = grp\FX
			OY = grp\FY
			
			Parent = grp\Mesh
			gad\Owner = grp\Owner
		EndIf
	Else
		OX = gad\Owner\FX
		OY = gad\Owner\FY
		
		Parent = gad\Owner\Mesh
	EndIf
	
	;Arguments
	gad\X					= X
	gad\Y					= Y
	gad\Caption				= Caption
	gad\Align				= Align
	
	If gad\X < 0
		gad\X = OW / Float(100.0 / Abs( gad\X ))
	EndIf
	If gad\Y < 0
		gad\Y = OH / Float(100.0 / Abs( gad\Y ))
	EndIf
	
	;Anchor
	gad\OX					= gad\X + OX
	gad\OY					= gad\Y + OY
	
	gad\Mesh				= FUI_CreateControl( Parent )
	FUI_SetChildPosition gad\Mesh, OX + gad\X, OY + gad\Y
	
	FUI_BuildLabel gad
	
	Return Handle( gad )
	
End Function

Function FUI_ListBox( Owner, X, Y, W, H, MultiSel=False, ForceSel=False )
	
	gad.ListBox				= New ListBox
	
	gad\Owner				= Object.Window( Owner )
	If gad\Owner = Null
		gad\Parent = Owner
		
		reg.Region = Object.Region( gad\Parent )
		tabp.TabPage = Object.TabPage( gad\Parent )
		pan.Panel = Object.Panel( gad\Parent )
		grp.GroupBox = Object.GroupBox( gad\Parent )
		If reg <> Null
			OX = reg\FX
			OY = reg\FY
			OW = reg\FW
			OH = reg\FH
			
			Parent.BBEntity = reg\Mesh
			gad\Owner = reg\Owner
		ElseIf tabp <> Null
			OX = tabp\Owner\FX
			OY = tabp\Owner\FY
			OW = tabp\Owner\FW
			OH = tabp\Owner\FH
			
			Parent = tabp\Owner\Mesh
			gad\Owner = tabp\Owner\Owner
		ElseIf pan <> Null
			OX = pan\FX
			OY = pan\FY
			OW = pan\FW
			OH = pan\FH
			
			Parent = pan\Mesh
			gad\Owner = pan\Owner
		ElseIf grp <> Null
			OX = grp\FX
			OY = grp\FY
			OW = grp\FW
			OH = grp\FH
			
			Parent = grp\Mesh
			gad\Owner = grp\Owner
		EndIf
	Else
		OX = gad\Owner\FX
		OY = gad\Owner\FY
		OW = gad\Owner\FW
		OH = gad\Owner\FH
		
		Parent = gad\Owner\Mesh
	EndIf
	
	;Arguments
	gad\X					= X
	gad\Y					= Y
	gad\W					= W
	gad\H					= H
	gad\MultiSel			= MultiSel
	gad\ForceSel			= ForceSel
	
	If gad\X < 0
		gad\X = OW / Float(100.0 / Abs( gad\X ))
	EndIf
	If gad\Y < 0
		gad\Y = OH / Float(100.0 / Abs( gad\Y ))
	EndIf
	If PERCENT_CLIENT = True
		WOW = OW - gad\X * 2
		WOH = OH - gad\Y * 2
	Else
		WOW = OW
		WOH = OH
	EndIf
	If gad\W < 0
		gad\W = WOW / Float(100.0 / Abs( gad\W ))
	EndIf
	If gad\H < 0
		gad\H = WOH / Float(100.0 / Abs( gad\H ))
	EndIf
	
	;Anchor
	gad\OX					= gad\X + OX
	gad\OY					= gad\Y + OY
	gad\OW					= W
	gad\OH					= H
	
	gad\Mesh				= FUI_CreateControl( Parent )
	FUI_SetChildPosition gad\Mesh, OX + gad\X, OY + gad\Y
	
	FUI_BuildListBox gad
	
	;Scroll Bars
	gad\VScroll				= New VScrollBar
	gad\VScroll\W			= gad\H - 2
	gad\VScroll\H			= 14
	gad\VScroll\SW			= gad\VScroll\W / 2
	gad\VScroll\SH			= gad\VScroll\H - 1
	
	gad\VScroll\Mesh		= FUI_CreateControl( gad\Mesh )
	FUI_SetChildPosition gad\VScroll\Mesh, gad\W - 1 - gad\VScroll\H, 1
	gad\VScroll\Btn1		= FUI_CreateControl( gad\VScroll\Mesh )
	FUI_SetChildPosition gad\VScroll\Btn1, 1, 0
	gad\VScroll\Btn2		= FUI_CreateControl( gad\VScroll\Mesh )
	FUI_SetChildPosition gad\VScroll\Btn2, 1, gad\VScroll\W - (gad\VScroll\H-1)
	gad\VScroll\ScrollBar	= FUI_CreateControl( gad\VScroll\Mesh )
	FUI_SetChildPosition gad\VScroll\ScrollBar, 1, (gad\VScroll\H-1) + 1
	
	FUI_BuildVScrollBar gad\VScroll
	
	gad\HScroll				= New HScrollBar
	gad\HScroll\W			= gad\W - 2
	gad\HScroll\H			= 14
	gad\HScroll\SW			= gad\HScroll\W / 2
	gad\HScroll\SH			= gad\HScroll\H - 2
	
	gad\HScroll\Mesh		= FUI_CreateControl( gad\Mesh )
	gad\HScroll\Btn1		= FUI_CreateControl( gad\HScroll\Mesh )
	gad\HScroll\Btn2		= FUI_CreateControl( gad\HScroll\Mesh )
	gad\HScroll\ScrollBar	= FUI_CreateControl( gad\HScroll\Mesh )
	;;;FUI_Build gad\HScroll
	
	Return Handle( gad )
	
End Function

Function FUI_ListBoxItem( Owner, Caption$="List Box Item", Icon$=0, SortAlphabetically = False)
	
	gad.ListBoxItem			= New ListBoxItem
	
	gad\Owner				= Object.ListBox( Owner )
	
	;Arguments
	gad\Caption				= Caption$


	If FUI_IsImageSource( Icon$ ) = True
		gad\Icon = LoadImage( Icon$ )
	Else
		If Int( Icon$ ) <> 0
			gad\Icon = Ptr Int( Icon$ )
		EndIf
	EndIf

	; Nasty alphabetical sorting hack
	If SortAlphabetically = True
		gad\Y = gad\Owner\CY
		gad\Owner\CY = gad\Owner\CY + LISTBOXITEM_HEIGHT
		gad\Mesh = FUI_CreateControl( gad\Owner\Mesh )
		FUI_SetChildPosition gad\Mesh, LISTBOX_PADDING, LISTBOX_PADDING + gad\Y
		Done = False
		For gad2.ListBoxItem = Each ListBoxItem
			If gad2\Owner = gad\Owner And gad2 <> gad
				If Done = False
					If FUI_HighAlphabetical(gad\Caption, gad2\Caption)
						Insert gad Before gad2
						Done = True
						gad\Y = gad2\Y
						FUI_SetChildPosition gad\Mesh, LISTBOX_PADDING, LISTBOX_PADDING + gad\Y
						gad2\Y = gad2\Y + LISTBOXITEM_HEIGHT
						FUI_SetChildPosition gad2\Mesh, LISTBOX_PADDING, LISTBOX_PADDING + gad2\Y
					EndIf
				Else
					gad2\Y = gad2\Y + LISTBOXITEM_HEIGHT
					FUI_SetChildPosition gad2\Mesh, LISTBOX_PADDING, LISTBOX_PADDING + gad2\Y
				EndIf
			EndIf
		Next
	Else
		gad\Y					= gad\Owner\CY
		gad\Owner\CY = gad\Owner\CY + LISTBOXITEM_HEIGHT
		gad\Mesh				= FUI_CreateControl( gad\Owner\Mesh )
		FUI_SetChildPosition gad\Mesh, LISTBOX_PADDING, LISTBOX_PADDING + gad\Y
	EndIf
	HideEntity gad\Mesh
	
	gad\IconMesh			= FUI_CreateControl( gad\Mesh )
	FUI_SetChildPosition gad\IconMesh, 0, 0
	
	FUI_BuildListBoxItem gad
	
	If gad\Y = 0 And gad\Owner\ForceSel = True
		gad\Active = True
		FUI_SetTexture gad\Mesh, 2
	EndIf
	
	Return Handle( gad )
	
End Function

Function FUI_ProgressBar( Owner, X, Y, W, H, Min#=0, Max#=100, Value#=0, DType=DTYPE_INTEGER )
	
	gad.ProgressBar			= New ProgressBar
	
	gad\Owner				= Object.Window( Owner )
	If gad\Owner = Null
		gad\Parent = Owner
		
		reg.Region = Object.Region( gad\Parent )
		tabp.TabPage = Object.TabPage( gad\Parent )
		pan.Panel = Object.Panel( gad\Parent )
		grp.GroupBox = Object.GroupBox( gad\Parent )
		If reg <> Null
			OX = reg\FX
			OY = reg\FY
			OW = reg\FW
			OH = reg\FH
			
			Parent.BBEntity = reg\Mesh
			gad\Owner = reg\Owner
		ElseIf tabp <> Null
			OX = tabp\Owner\FX
			OY = tabp\Owner\FY
			OW = tabp\Owner\FW
			OH = tabp\Owner\FH
			
			Parent = tabp\Owner\Mesh
			gad\Owner = tabp\Owner\Owner
		ElseIf pan <> Null
			OX = pan\FX
			OY = pan\FY
			OW = pan\FW
			OH = pan\FH
			
			Parent = pan\Mesh
			gad\Owner = pan\Owner
		ElseIf grp <> Null
			OX = grp\FX
			OY = grp\FY
			OW = grp\FW
			OH = grp\FH
			
			Parent = grp\Mesh
			gad\Owner = grp\Owner
		EndIf
	Else
		OX = gad\Owner\FX
		OY = gad\Owner\FY
		OW = gad\Owner\FW
		OH = gad\Owner\FH
		
		Parent = gad\Owner\Mesh
	EndIf
	
	;Arguments
	gad\X					= X
	gad\Y					= Y
	gad\W					= W
	gad\H					= H
	gad\Min					= Min#
	gad\Max					= Max#
	gad\Value				= Value#
	gad\DType				= DType
	
	If gad\DType = DTYPE_INTEGER
		gad\Min = Int( gad\Min )
		gad\Max = Int( gad\Max )
		gad\Value = Int( gad\Value )
	EndIf
	
	If gad\X < 0
		gad\X = OW / Float(100.0 / Abs( gad\X ))
	EndIf
	If gad\Y < 0
		gad\Y = OH / Float(100.0 / Abs( gad\Y ))
	EndIf
	If PERCENT_CLIENT = True
		WOW = OW - gad\X * 2
		WOH = OH - gad\Y * 2
	Else
		WOW = OW
		WOH = OH
	EndIf
	If gad\W < 0
		gad\W = WOW / Float(100.0 / Abs( gad\W ))
	EndIf
	If gad\H < 0
		gad\H = WOH / Float(100.0 / Abs( gad\H ))
	EndIf
	
	;Anchor
	gad\OX					= gad\X + OX
	gad\OY					= gad\Y + OY
	gad\OW					= W
	gad\OH					= H
	
	SetFont app\fntGadget
	
	gad\Mesh				= FUI_CreateControl( Parent )
	FUI_SetChildPosition gad\Mesh, OX + gad\X, OY + gad\Y
	
	gad\MeshText			= FUI_CreateControl( gad\Mesh )
	FUI_SetChildPosition gad\MeshText, gad\W / 2 - StringWidth( gad\Value ) / 2, gad\H / 2 - FontHeight() / 2
	
	gad\Bar					= FUI_CreateControl( gad\Mesh )
	FUI_SetChildPosition gad\Bar, 1, 1
	
	FUI_BuildProgressBar gad
	
	Return Handle( gad )
	
End Function

Function FUI_Radio( Owner, X, Y, Caption$="Radio", Checked=False, ID=0 )
	
	gad.Radio				= New Radio
	
	gad\Owner				= Object.Window( Owner )
	If gad\Owner = Null
		gad\Parent = Owner
		
		reg.Region = Object.Region( gad\Parent )
		tabp.TabPage = Object.TabPage( gad\Parent )
		pan.Panel = Object.Panel( gad\Parent )
		grp.GroupBox = Object.GroupBox( gad\Parent )
		If reg <> Null
			OX = reg\FX
			OY = reg\FY
			
			Parent.BBEntity = reg\Mesh
			gad\Owner = reg\Owner
		ElseIf tabp <> Null
			OX = tabp\Owner\FX
			OY = tabp\Owner\FY
			
			Parent = tabp\Owner\Mesh
			gad\Owner = tabp\Owner\Owner
		ElseIf pan <> Null
			OX = pan\FX
			OY = pan\FY
			
			Parent = pan\Mesh
			gad\Owner = pan\Owner
		ElseIf grp <> Null
			OX = grp\FX
			OY = grp\FY
			
			Parent = grp\Mesh
			gad\Owner = grp\Owner
		EndIf
	Else
		OX = gad\Owner\FX
		OY = gad\Owner\FY
		
		Parent = gad\Owner\Mesh
	EndIf
	
	;Arguments
	gad\X					= X
	gad\Y					= Y
	gad\Caption				= Caption
	gad\Checked				= Checked
	gad\ID					= ID
	
	SetFont app\fntGadget
	gad\H					= FontHeight(  )
	gad\W					= StringWidth( gad\Caption ) + gad\H + 3
	
	If gad\X < 0
		gad\X = OW / Float(100.0 / Abs( gad\X ))
	EndIf
	If gad\Y < 0
		gad\Y = OH / Float(100.0 / Abs( gad\Y ))
	EndIf
	
	;Anchor
	gad\OX					= gad\X + OX
	gad\OY					= gad\Y + OY
	
	gad\Mesh				= FUI_CreateControl( Parent )
	FUI_SetChildPosition gad\Mesh, OX + gad\X, OY + gad\Y
	
	FUI_BuildRadio gad
	
	Return Handle( gad )
	
End Function

Function FUI_ScrollBar( Owner, X, Y, W, H, Min#=0, Max#=100, Value#=0, ScrollW=20, Direction=DIR_HORIZONTAL )
	
	gad.ScrollBar			= New ScrollBar
	
	gad\Owner				= Object.Window( Owner )
	If gad\Owner = Null
		gad\Parent = Owner
		
		reg.Region = Object.Region( gad\Parent )
		tabp.TabPage = Object.TabPage( gad\Parent )
		pan.Panel = Object.Panel( gad\Parent )
		grp.GroupBox = Object.GroupBox( gad\Parent )
		If reg <> Null
			OX = reg\FX
			OY = reg\FY
			OW = reg\FW
			OH = reg\FH
			
			Parent.BBEntity = reg\Mesh
			gad\Owner = reg\Owner
		ElseIf tabp <> Null
			OX = tabp\Owner\FX
			OY = tabp\Owner\FY
			OW = tabp\Owner\FW
			OH = tabp\Owner\FH
			
			Parent = tabp\Owner\Mesh
			gad\Owner = tabp\Owner\Owner
		ElseIf pan <> Null
			OX = pan\FX
			OY = pan\FY
			OW = pan\FW
			OH = pan\FH
			
			Parent = pan\Mesh
			gad\Owner = pan\Owner
		ElseIf grp <> Null
			OX = grp\FX
			OY = grp\FY
			OW = grp\FW
			OH = grp\FH
			
			Parent = grp\Mesh
			gad\Owner = grp\Owner
		EndIf
	Else
		OX = gad\Owner\FX
		OY = gad\Owner\FY
		OW = gad\Owner\FW
		OH = gad\Owner\FH
		
		Parent = gad\Owner\Mesh
	EndIf
	
	;Arguments
	gad\X					= X
	gad\Y					= Y
	gad\W					= W
	gad\H					= H
	gad\Min					= Min#
	gad\Max					= Max#
	gad\Value				= Value#
	gad\SW					= ScrollW
	gad\OSW					= gad\SW
	gad\Direction			= Direction
	
	If gad\X < 0
		gad\X = OW / Float(100.0 / Abs( gad\X ))
	EndIf
	If gad\Y < 0
		gad\Y = OH / Float(100.0 / Abs( gad\Y ))
	EndIf
	If PERCENT_CLIENT = True
		WOW = OW - gad\X * 2
		WOH = OH - gad\Y * 2
	Else
		WOW = OW
		WOH = OH
	EndIf
	If gad\W < 0
		gad\W = WOH / Float(100.0 / Abs( gad\W ))
	EndIf
	If gad\H < 0
		gad\H = WOH / Float(100.0 / Abs( gad\H ))
	EndIf
	
	gad\SX					= FUI_Interpolate( gad\Value, gad\Min, gad\Max, 0, gad\W - (gad\H-2)*2 - 4 - gad\SW )
	gad\SH					= gad\H - 2
	
	;Anchor
	gad\OX					= gad\X + OX
	gad\OY					= gad\Y + OY
	gad\OW					= W
	gad\OH					= H
	
	gad\Mesh				= FUI_CreateControl( Parent )
	FUI_SetChildPosition gad\Mesh, OX + gad\X, OY + gad\Y
	gad\Btn1		= FUI_CreateControl( gad\Mesh )
	FUI_SetChildPosition gad\Btn1, 1, 1
	gad\Btn2		= FUI_CreateControl( gad\Mesh )
	If gad\Direction = DIR_VERTICAL
		FUI_SetChildPosition gad\Btn2, 1, gad\W - (gad\H-1)
	Else
		FUI_SetChildPosition gad\Btn2, gad\W - (gad\H-1), 1
	EndIf
	gad\ScrollBar	= FUI_CreateControl( gad\Mesh )
	FUI_SetChildPosition gad\ScrollBar, 1, (gad\H-1) + 1
	
	FUI_BuildScrollBar gad
	
	Return Handle( gad )
	
End Function

Function FUI_Slider( Owner, X, Y, W, H, Min#=0, Max#=100, Value#=0, ScrollW=0, Direction=DIR_HORIZONTAL )
	
	gad.Slider				= New Slider
	
	gad\Owner				= Object.Window( Owner )
	If gad\Owner = Null
		gad\Parent = Owner
		
		reg.Region = Object.Region( gad\Parent )
		tabp.TabPage = Object.TabPage( gad\Parent )
		pan.Panel = Object.Panel( gad\Parent )
		grp.GroupBox = Object.GroupBox( gad\Parent )
		If reg <> Null
			OX = reg\FX
			OY = reg\FY
			OW = reg\FW
			OH = reg\FH
			
			Parent.BBEntity = reg\Mesh
			gad\Owner = reg\Owner
		ElseIf tabp <> Null
			OX = tabp\Owner\FX
			OY = tabp\Owner\FY
			OW = tabp\Owner\FW
			OH = tabp\Owner\FH
			
			Parent = tabp\Owner\Mesh
			gad\Owner = tabp\Owner\Owner
		ElseIf pan <> Null
			OX = pan\FX
			OY = pan\FY
			OW = pan\FW
			OH = pan\FH
			
			Parent = pan\Mesh
			gad\Owner = pan\Owner
		ElseIf grp <> Null
			OX = grp\FX
			OY = grp\FY
			OW = grp\FW
			OH = grp\FH
			
			Parent = grp\Mesh
			gad\Owner = grp\Owner
		EndIf
	Else
		OX = gad\Owner\FX
		OY = gad\Owner\FY
		OW = gad\Owner\FW
		OH = gad\Owner\FH
		
		Parent = gad\Owner\Mesh
	EndIf
	
	;Arguments
	gad\X					= X
	gad\Y					= Y
	gad\W					= W
	gad\H					= H
	gad\Min					= Min#
	gad\Max					= Max#
	gad\Value				= Value#
	gad\Direction			= Direction
	
	If gad\X < 0
		gad\X = OW / Float(100.0 / Abs( gad\X ))
	EndIf
	If gad\Y < 0
		gad\Y = OH / Float(100.0 / Abs( gad\Y ))
	EndIf
	If PERCENT_CLIENT = True
		WOW = OW - gad\X * 2
		WOH = OH - gad\Y * 2
	Else
		WOW = OW
		WOH = OH
	EndIf
	If gad\W < 0
		gad\W = WOW / Float(100.0 / Abs( gad\W ))
	EndIf
	If gad\H < 0
		gad\H = WOH / Float(100.0 / Abs( gad\H ))
	EndIf
	
	If ScrollW = 0
		gad\SW				= gad\H - 2
	Else
		gad\SW				= ScrollW
	EndIf
	gad\SH					= gad\H - 2
	gad\SX					= FUI_Interpolate( gad\Value, gad\Min, gad\Max, 0, gad\W - 2 - gad\SW )
	
	;Anchor
	gad\OX					= gad\X + OX
	gad\OY					= gad\Y + OY
	gad\OW					= W
	gad\OH					= H
	
	gad\Mesh				= FUI_CreateControl( Parent )
	FUI_SetChildPosition gad\Mesh, OX + gad\X, OY + gad\Y
	
	gad\ScrollBar			= FUI_CreateControl( gad\Mesh )
	FUI_SetChildPosition gad\ScrollBar, 1, 1
	
	FUI_BuildSlider gad
	
	Return Handle( gad )
	
End Function

Function FUI_Spinner( Owner, X, Y, W, H, Min#=0, Max#=100, Value#=0, Inc#=1, DType=DTYPE_INTEGER, Append$="" )
	
	gad.Spinner				= New Spinner
	
	gad\Owner				= Object.Window( Owner )
	If gad\Owner = Null
		gad\Parent = Owner
		
		reg.Region = Object.Region( gad\Parent )
		tabp.TabPage = Object.TabPage( gad\Parent )
		pan.Panel = Object.Panel( gad\Parent )
		grp.GroupBox = Object.GroupBox( gad\Parent )
		If reg <> Null
			OX = reg\FX
			OY = reg\FY
			OW = reg\FW
			OH = reg\FH
			
			Parent.BBEntity = reg\Mesh
			gad\Owner = reg\Owner
		ElseIf tabp <> Null
			OX = tabp\Owner\FX
			OY = tabp\Owner\FY
			OW = tabp\Owner\FW
			OH = tabp\Owner\FH
			
			Parent = tabp\Owner\Mesh
			gad\Owner = tabp\Owner\Owner
		ElseIf pan <> Null
			OX = pan\FX
			OY = pan\FY
			OW = pan\FW
			OH = pan\FH
			
			Parent = pan\Mesh
			gad\Owner = pan\Owner
		ElseIf grp <> Null
			OX = grp\FX
			OY = grp\FY
			OW = grp\FW
			OH = grp\FH
			
			Parent = grp\Mesh
			gad\Owner = grp\Owner
		EndIf
	Else
		OX = gad\Owner\FX
		OY = gad\Owner\FY
		OW = gad\Owner\FW
		OH = gad\Owner\FH
		
		Parent = gad\Owner\Mesh
	EndIf
	
	;Arguments
	gad\X					= X
	gad\Y					= Y
	gad\W					= W
	gad\H					= H
	gad\Min					= Min#
	gad\Max					= Max#
	gad\Value				= Value#
	gad\Inc					= Inc#
	gad\DType				= DType
	gad\Append				= Append$
	
	If gad\DType = DTYPE_INTEGER
		gad\Min = Int( gad\Min )
		gad\Max = Int( gad\Max )
		gad\Value = Int( gad\Value )
		gad\Inc = Int( gad\Inc )
	EndIf
	
	If gad\DType = DTYPE_INTEGER
		gad\Caption = Int( gad\Value ) + gad\Append
	Else
		gad\Caption = Float( gad\Value ) + gad\Append
	EndIf
	
	If gad\X < 0
		gad\X = OW / Float(100.0 / Abs( gad\X ))
	EndIf
	If gad\Y < 0
		gad\Y = OH / Float(100.0 / Abs( gad\Y ))
	EndIf
	If PERCENT_CLIENT = True
		WOW = OW - gad\X * 2
		WOH = OH - gad\Y * 2
	Else
		WOW = OW
		WOH = OH
	EndIf
	If gad\W < 0
		gad\W = WOW / Float(100.0 / Abs( gad\W ))
	EndIf
	If gad\H < 0
		gad\H = WOH / Float(100.0 / Abs( gad\H ))
	EndIf
	
	;Anchor
	gad\OX					= gad\X + OX
	gad\OY					= gad\Y + OY
	gad\OW					= W
	gad\OH					= H
	
	SetFont app\fntGadget
	
	BW = gad\H - 1
	BH = (gad\H-2) / 2
	
	gad\Mesh				= FUI_CreateControl( Parent )
	FUI_SetChildPosition gad\Mesh, OX + gad\X, OY + gad\Y
	
	gad\MeshText			= FUI_CreateControl( gad\Mesh )
	FUI_SetChildPosition gad\MeshText, 1, 1
	
	gad\Caret				= FUI_CreateControl( gad\Mesh )
	FUI_SetChildPosition gad\Caret, 4, gad\H / 2 - FontHeight() / 2
	HideEntity gad\Caret
	
	gad\Btn1				= FUI_CreateControl( gad\Mesh )
	FUI_SetChildPosition gad\Btn1, gad\W - 1 - BW, 1
	
	gad\Btn2				= FUI_CreateControl( gad\Mesh )
	FUI_SetChildPosition gad\Btn2, gad\W - 1 - BW, 1 + BH
	
	FUI_BuildSpinner gad
	
	Return Handle( gad )
	
End Function

Function FUI_TextBox( Owner, X, Y, W, H, MaxLength=0 )
	
	gad.TextBox				= New TextBox
	
	gad\Owner				= Object.Window( Owner )
	If gad\Owner = Null
		gad\Parent = Owner
		
		reg.Region = Object.Region( gad\Parent )
		tabp.TabPage = Object.TabPage( gad\Parent )
		pan.Panel = Object.Panel( gad\Parent )
		grp.GroupBox = Object.GroupBox( gad\Parent )
		If reg <> Null
			OX = reg\FX
			OY = reg\FY
			OW = reg\FW
			OH = reg\FH
			
			Parent.BBEntity = reg\Mesh
			gad\Owner = reg\Owner
		ElseIf tabp <> Null
			OX = tabp\Owner\FX
			OY = tabp\Owner\FY
			OW = tabp\Owner\FW
			OH = tabp\Owner\FH
			
			Parent = tabp\Owner\Mesh
			gad\Owner = tabp\Owner\Owner
		ElseIf pan <> Null
			OX = pan\FX
			OY = pan\FY
			OW = pan\FW
			OH = pan\FH
			
			Parent = pan\Mesh
			gad\Owner = pan\Owner
		ElseIf grp <> Null
			OX = grp\FX
			OY = grp\FY
			OW = grp\FW
			OH = grp\FH
			
			Parent = grp\Mesh
			gad\Owner = grp\Owner
		EndIf
	Else
		OX = gad\Owner\FX
		OY = gad\Owner\FY
		OW = gad\Owner\FW
		OH = gad\Owner\FH
		
		Parent = gad\Owner\Mesh
	EndIf
	
	;Arguments
	gad\X					= X
	gad\Y					= Y
	gad\W					= W
	gad\H					= H
	gad\MaxLength			= MaxLength
	
	If gad\X < 0
		gad\X = OW / Float(100.0 / Abs( gad\X ))
	EndIf
	If gad\Y < 0
		gad\Y = OH / Float(100.0 / Abs( gad\Y ))
	EndIf
	If PERCENT_CLIENT = True
		WOW = OW - gad\X * 2
		WOH = OH - gad\Y * 2
	Else
		WOW = OW
		WOH = OH
	EndIf
	If gad\W < 0
		gad\W = WOW / Float(100.0 / Abs( gad\W ))
	EndIf
	If gad\H < 0
		gad\H = WOH / Float(100.0 / Abs( gad\H ))
	EndIf
	
	;Anchor
	gad\OX					= gad\X + OX
	gad\OY					= gad\Y + OY
	gad\OW					= W
	gad\OH					= H
	
	gad\Mesh				= FUI_CreateControl( Parent )
	FUI_SetChildPosition gad\Mesh, OX + gad\X, OY + gad\Y
	
	gad\MeshText			= FUI_CreateControl( gad\Mesh )
	FUI_SetChildPosition gad\MeshText, 1, 1
	
	gad\Caret				= FUI_CreateControl( gad\Mesh )
	HideEntity gad\Caret
	
	FUI_BuildTextBox gad
	
	Return Handle( gad )
	
End Function

Function FUI_TreeView( Owner, X, Y, W, H )
	
	gad.TreeView			= New TreeView
	
	gad\Owner				= Object.Window( Owner )
	If gad\Owner = Null
		gad\Parent = Owner
		
		reg.Region = Object.Region( gad\Parent )
		tabp.TabPage = Object.TabPage( gad\Parent )
		pan.Panel = Object.Panel( gad\Parent )
		grp.GroupBox = Object.GroupBox( gad\Parent )
		If reg <> Null
			OX = reg\FX
			OY = reg\FY
			OW = reg\FW
			OH = reg\FH
			
			Parent.BBEntity = reg\Mesh
			gad\Owner = reg\Owner
		ElseIf tabp <> Null
			OX = tabp\Owner\FX
			OY = tabp\Owner\FY
			OW = tabp\Owner\FW
			OH = tabp\Owner\FH
			
			Parent = tabp\Owner\Mesh
			gad\Owner = tabp\Owner\Owner
		ElseIf pan <> Null
			OX = pan\FX
			OY = pan\FY
			OW = pan\FW
			OH = pan\FH
			
			Parent = pan\Mesh
			gad\Owner = pan\Owner
		ElseIf grp <> Null
			OX = grp\FX
			OY = grp\FY
			OW = grp\FW
			OH = grp\FH
			
			Parent = grp\Mesh
			gad\Owner = grp\Owner
		EndIf
	Else
		OX = gad\Owner\FX
		OY = gad\Owner\FY
		OW = gad\Owner\FW
		OH = gad\Owner\FH
		
		Parent = gad\Owner\Mesh
	EndIf
	
	;Arguments
	gad\X					= X
	gad\Y					= Y
	gad\W					= W
	gad\H					= H
	
	If gad\X < 0
		gad\X = OW / Float(100.0 / Abs( gad\X ))
	EndIf
	If gad\Y < 0
		gad\Y = OH / Float(100.0 / Abs( gad\Y ))
	EndIf
	If PERCENT_CLIENT = True
		WOW = OW - gad\X * 2
		WOH = OH - gad\Y * 2
	Else
		WOW = OW
		WOH = OH
	EndIf
	If gad\W < 0
		gad\W = WOW / Float(100.0 / Abs( gad\W ))
	EndIf
	If gad\H < 0
		gad\H = WOH / Float(100.0 / Abs( gad\H ))
	EndIf
	
	;Anchor
	gad\OX					= gad\X + OX
	gad\OY					= gad\Y + OY
	gad\OW					= W
	gad\OH					= H
	
	gad\Mesh				= FUI_CreateControl( Parent )
	FUI_SetChildPosition gad\Mesh, OX + gad\X, OY + gad\Y
	
	;Scroll Bars
	gad\VScroll				= New VScrollBar
	gad\VScroll\W			= gad\H - 2
	gad\VScroll\H			= 14
	gad\VScroll\SW			= gad\VScroll\W / 2
	gad\VScroll\SH			= gad\VScroll\H - 1
	
	gad\VScroll\Mesh		= FUI_CreateControl( gad\Mesh )
	FUI_SetChildPosition gad\VScroll\Mesh, gad\W - 1 - gad\VScroll\H, 1
	gad\VScroll\Btn1		= FUI_CreateControl( gad\VScroll\Mesh )
	FUI_SetChildPosition gad\VScroll\Btn1, 1, 0
	gad\VScroll\Btn2		= FUI_CreateControl( gad\VScroll\Mesh )
	FUI_SetChildPosition gad\VScroll\Btn2, 1, gad\VScroll\W - (gad\VScroll\H-1)
	gad\VScroll\ScrollBar	= FUI_CreateControl( gad\VScroll\Mesh )
	FUI_SetChildPosition gad\VScroll\ScrollBar, 1, (gad\VScroll\H-1) + 1
	
	FUI_BuildVScrollBar gad\VScroll
	
	FUI_BuildTreeView gad
	
	Return Handle( gad )
	
End Function

Function FUI_TreeViewNode( Owner, Caption$="Node" )
	
	gad.Node				= New Node
	
	gad\Owner				= Object.TreeView( Owner )
	If gad\Owner = Null
		gad\Parent = Object.Node( Owner )
		gad\Parent\HasChildren = True
		gad\Parent\ChildCount = gad\Parent\ChildCount + 1
		gad\Owner = gad\Parent\Owner
	EndIf
	
	;Arguments
	gad\Caption				= Caption$
	
	If gad\Parent = Null
		gad\Mesh				= FUI_CreateControl( gad\Owner\Mesh )
		gad\DropDown			= FUI_CreateControl( gad\Owner\Mesh )
		
		gad\Y					= gad\Owner\CY
		gad\Owner\CY = gad\Owner\CY + TREEVIEWITEM_HEIGHT
		
		gad\Layer = 1
	Else
		gad\Mesh				= FUI_CreateControl( gad\Parent\DropDown )
		gad\DropDown			= FUI_CreateControl( gad\Parent\DropDown )
		FUI_SetChildPosition gad\Mesh, 0, gad\Parent\CY
		
		gad\Y					= gad\Parent\CY
		gad\Parent\CY = gad\Parent\CY + TREEVIEWITEM_HEIGHT
		gad\Parent\OH = gad\Parent\OH + TREEVIEWITEM_HEIGHT
		
		gad\Layer = gad\Parent\Layer + 1
		
		FUI_BuildTreeViewNode gad\Parent
	EndIf
	gad\CY					= 0
	
	HideEntity gad\DropDown
	
	FUI_BuildTreeViewNode gad
	
	Return Handle( gad )
	
End Function

Function FUI_View( Owner, X, Y, W, H, R=150, G=150, B=150, Flags=CS_BORDER )
	
	gad.View				= New View
	
	gad\Owner				= Object.Window( Owner )
	If gad\Owner = Null
		gad\Parent = Owner
		
		reg.Region = Object.Region( gad\Parent )
		tabp.TabPage = Object.TabPage( gad\Parent )
		pan.Panel = Object.Panel( gad\Parent )
		grp.GroupBox = Object.GroupBox( gad\Parent )
		If reg <> Null
			OX = reg\FX
			OY = reg\FY
			OW = reg\FW
			OH = reg\FH
			
			Parent.BBEntity = reg\Mesh
			gad\Owner = reg\Owner
		ElseIf tabp <> Null
			OX = tabp\Owner\FX
			OY = tabp\Owner\FY
			OW = tabp\Owner\FW
			OH = tabp\Owner\FH
			
			Parent = tabp\Owner\Mesh
			gad\Owner = tabp\Owner\Owner
		ElseIf pan <> Null
			OX = pan\FX
			OY = pan\FY
			OW = pan\FW
			OH = pan\FH
			
			Parent = pan\Mesh
			gad\Owner = pan\Owner
		ElseIf grp <> Null
			OX = grp\FX
			OY = grp\FY
			OW = grp\FW
			OH = grp\FH
			
			Parent = grp\Mesh
			gad\Owner = grp\Owner
		EndIf
	Else
		OX = gad\Owner\FX
		OY = gad\Owner\FY
		OW = gad\Owner\FW
		OH = gad\Owner\FH
		
		Parent = gad\Owner\Mesh
	EndIf
	
	;Arguments
	gad\X					= X
	gad\Y					= Y
	gad\W					= W
	gad\H					= H
	
	gad\R					= R
	gad\G					= G
	gad\B					= B
	
	gad\Flags				= Flags
	
	gad\Zoom				= 1.0
	gad\ProjMode			= 1
	
	If gad\X < 0
		gad\X = OW / Float(100.0 / Abs( gad\X ))
	EndIf
	If gad\Y < 0
		gad\Y = OH / Float(100.0 / Abs( gad\Y ))
	EndIf
	If PERCENT_CLIENT = True
		WOW = OW - gad\X * 2
		WOH = OH - gad\Y * 2
	Else
		WOW = OW
		WOH = OH
	EndIf
	If gad\W < 0
		gad\W = WOW / Float(100.0 / Abs( gad\W ))
	EndIf
	If gad\H < 0
		gad\H = WOH / Float(100.0 / Abs( gad\H ))
	EndIf
	
	gad\OX					= gad\X + OX
	gad\OY					= gad\Y + OY
	gad\OW					= W
	gad\OH					= H
	
	;Properties
	gad\Cam					= CreateCamera(  )
	CameraClsColor gad\Cam, gad\R, gad\G, gad\B
	HideEntity gad\Cam
	
	gad\Mesh				= FUI_CreateControl( Parent )
	FUI_SetChildPosition gad\Mesh, OX + gad\X, OY + gad\Y
	gad\MeshText			= FUI_CreateControl( gad\Mesh )
	
	FUI_BuildView gad
	
	If RENDER_MODE = 1
		HideEntity gad\Cam
	EndIf
	
	Return Handle( gad )
	
End Function
;#End Region

;#Region ---------- Build Gadgets --------------
Function FUI_BuildWindow( gad.Window, Mode=0 )
	
	FY = WINDOW_BORDER
	FH = gad\H - WINDOW_BORDER*2
	OffY = WINDOW_BORDER
	
	gad\FW = gad\W - WINDOW_BORDER * 2
	gad\FH = gad\H - WINDOW_BORDER * 2
	
	;Buttons
	BW = TITLEBAR_HEIGHT - 6
	BH = BW
	BX = gad\W - 3 - BW
	BY = 4
	
	;Title Bar
	If (gad\Flags And WS_TITLEBAR) = WS_TITLEBAR
		SetFont app\fntWindow
		gad\DragX = 0
		gad\DragY = 0
		gad\DragW = gad\W
		gad\DragH = TITLEBAR_HEIGHT + 1
		
		surf.BBSurface = GetSurface( gad\TitleBar, 1 )
		ClearSurface surf
		
		FUI_SetGradientDir( DIR_VERTICAL )
		
		useSkin.BBTexture = Null
		If SKIN_ENABLED = True
			useSkin = FUI_SkinWindow( gad, 1 )
		EndIf
		
		FUI_SetColor SC_TITLEBAR
		FUI_AdjustColor-40, True
		
		FUI_Rect WINDOW_BORDER, FY, gad\W-WINDOW_BORDER*2, TITLEBAR_HEIGHT-OffY, surf
		If useSkin <> Null
			EntityTexture gad\TitleBar, useSkin
			
			FreeTexture useSkin
		EndIf
		
		If gad\Icon <> Null
			iw = ImageWidth( gad\Icon )
			ih = ImageHeight( gad\Icon )
			
			FUI_SetChildPosition gad\IconMesh, WINDOW_BORDER + TITLEBAR_HEIGHT / 2 - IW / 2, WINDOW_BORDER + TITLEBAR_HEIGHT / 2 - IH / 2
			
			surf = GetSurface( gad\IconMesh, 1 )
			ClearSurface surf
			
			FUI_SetColor SC_WHITE
			FUI_Rect 0, 0, iw, ih, surf
			
			tex.BBTexture = FUI_CreateTexture( iw, ih, 1+2, 1 )
			EntityTexture gad\IconMesh, tex
			
			SetBuffer TextureBuffer( tex )
			
			LockBuffer ImageBuffer( gad\Icon )
			LockBuffer
			IX = 0;gad\W / 2 - IW / 2
			IY = 0;gad\H / 2 - IH / 2
			For A = IX To IX + IW - 1
				For B = IY To IY + IH - 1
					RGB = ReadPixelFast( A - IX, B - IY, ImageBuffer( gad\Icon ) ) And $FFFFFFFF
					
					cr = FUI_GetRed( RGB )
					cg = FUI_GetGreen( RGB )
					cb = FUI_GetBlue( RGB )
					
					If Not cr = 255 And cg = 0 And cb = 0
						WritePixelFast A, B, RGB
					EndIf
				Next
			Next
			UnlockBuffer ImageBuffer( gad\Icon )
			UnlockBuffer
			
			FreeTexture tex
			tex = Null
		EndIf
		
		FY = FY + (TITLEBAR_HEIGHT-OffY)
		FH = FH - (TITLEBAR_HEIGHT-OffY)
		gad\FH = gad\FH - (TITLEBAR_HEIGHT-OffY)
		
		OffY = 0
	EndIf
	
	;Menu Bar
	If (gad\Flags And WS_MENUBAR) = WS_MENUBAR
		surf = GetSurface( gad\MenuBar, 1 )
		ClearSurface surf
		
		If (gad\Flags And WS_TITLEBAR) = WS_TITLEBAR
			Y = TITLEBAR_HEIGHT
		Else
			Y = WINDOW_BORDER
		EndIf
		
		useSkin = Null
		If SKIN_ENABLED = True
			useSkin = FUI_SkinWindow( gad, 2 )
		EndIf
		
		If useSkin = Null
			FUI_SetColor SC_MENUBAR
			FUI_Rect WINDOW_BORDER, Y, gad\W-WINDOW_BORDER*2, MENUBAR_HEIGHT - 1, surf
			
			FUI_SetColor SC_MENUBAR_BORDER
			FUI_Rect WINDOW_BORDER, Y + MENUBAR_HEIGHT - 1, gad\W-WINDOW_BORDER*2, 1, surf
		Else
			FUI_SetColor SC_WHITE
			FUI_Rect WINDOW_BORDER, Y, gad\W-WINDOW_BORDER*2, MENUBAR_HEIGHT, surf
		EndIf
		
		FY = FY + (MENUBAR_HEIGHT-OffY)
		FH = FH - (MENUBAR_HEIGHT-OffY)
		gad\FH = gad\FH - (MENUBAR_HEIGHT-OffY)
		
		OffY = 0
	EndIf
	
	;Status Bar
	If (gad\Flags And WS_STATUSBAR) = WS_STATUSBAR
		surf = GetSurface( gad\StatusBar, 1 )
		ClearSurface surf
		
		useSkin = Null
		If SKIN_ENABLED = True
			useSkin = FUI_SkinWindow( gad, 3 )
		EndIf
		
		If useSkin = Null
			FUI_SetGradientDir DIR_VERTICAL
			
			FUI_SetColor( SC_STATUSBAR )
			FUI_Rect WINDOW_BORDER, gad\H - (STATUSBAR_HEIGHT - 1) - WINDOW_BORDER, gad\W-WINDOW_BORDER*2, STATUSBAR_HEIGHT - 1, surf
			
			FUI_SetColor( SC_STATUSBAR_BORDER )
			FUI_Rect WINDOW_BORDER, gad\H - STATUSBAR_HEIGHT - WINDOW_BORDER, gad\W-WINDOW_BORDER*2, 1, surf, 0
		Else
			FUI_SetGradientDir DIR_VERTICAL
			
			FUI_SetColor( SC_WHITE )
			FUI_Rect WINDOW_BORDER, gad\H - STATUSBAR_HEIGHT - WINDOW_BORDER, gad\W-WINDOW_BORDER*2, STATUSBAR_HEIGHT, surf
		EndIf
		
		FH = FH - (MENUBAR_HEIGHT-WINDOW_BORDER)
		gad\FH = gad\FH - (MENUBAR_HEIGHT-WINDOW_BORDER)
	EndIf
	
	;Form
	surf = GetSurface( gad\Mesh, 1 )
	ClearSurface surf
	
	FUI_SetGradientDir( DIR_VERTICAL )
	
	useSkin = Null
	If SKIN_ENABLED = True
		useSkin = FUI_SkinWindow( gad )
	EndIf
	
	If useSkin = Null
		FUI_SetColor SC_FORM
		FUI_AdjustColor-20, True
		FUI_Rect WINDOW_BORDER, FY, gad\W-WINDOW_BORDER*2, FH, surf
	Else
		FUI_SetColor SC_WHITE
		FUI_Rect WINDOW_BORDER, FY, gad\W-WINDOW_BORDER*2, FH, surf
	EndIf
	
	If WINDOW_BORDER = True
		FUI_SetColor( SC_FORM_BORDER )
		FUI_Rect 0, 0, gad\W, gad\H, surf, 0
	EndIf
	
	;Minimised Form
	surf = GetSurface( gad\MinMesh, 1 )
	ClearSurface surf
	
	FUI_SetGradientDir DIR_HORIZONTAL
	
	If WINDOW_BORDER = True
		FUI_SetColor SC_FORM_BORDER
		FUI_Rect 0, 0, gad\W, TITLEBAR_HEIGHT+1, surf, 0
	EndIf
	
	If Mode = 1
		If (gad\Flags And WS_TITLEBAR) = WS_TITLEBAR
			FUI_SetTitleBarText Handle( gad ), gad\Caption
			tex.BBTexture = FUI_GadgetTexture( gad\TitleBarText )
			
			SetBuffer TextureBuffer( tex )
;			If gad\Icon <> 0
;				IW = TextureWidth( tex )
;				IH = TextureHeight( tex )
;				If ImageWidth( gad\Icon ) < IW
;					IW = ImageWidth( gad\Icon )
;				EndIf
;				If ImageHeight( gad\Icon ) < IH
;					IH = ImageHeight( gad\Icon )
;				EndIf
;				LockBuffer
;				LockBuffer ImageBuffer( gad\Icon )
;				DX = WINDOW_BORDER + TITLEBAR_HEIGHT / 2 - ImageWidth( gad\Icon ) / 2
;				DY = WINDOW_BORDER + TITLEBAR_HEIGHT / 2 - ImageHeight( gad\Icon ) / 2
;				For X = DX To DX + IW - 1
;					For Y = DY To DY + IH - 1
;						rgb = ReadPixelFast( X - DX, Y - DY, ImageBuffer( gad\Icon ) ) And $FFFFFFFF
;						If rgb <> -65536
;							If X >= 0 And Y >= 0 And X < TextureWidth( tex ) And Y < TextureHeight( tex )
;								WritePixelFast X, Y, rgb
;							EndIf
;						EndIf
;					Next
;				Next
;				UnlockBuffer
;				UnlockBuffer ImageBuffer( gad\Icon )
;			EndIf
			
			FreeTexture tex
			
			;Close Button
			If (gad\Flags And WS_CLOSEBUTTON) = WS_CLOSEBUTTON
				surf = GetSurface( gad\CloseBtn, 1 )
				ClearSurface surf
				
				FUI_SetColor( SC_WHITE )
				FUI_Rect 0, 0, BW, BH, surf
				
				useSkin = Null
				If SKIN_ENABLED = True
					tex = FUI_LoadTexture( SKIN_PATH$ + "window\close.bmp", 1, 0, 0, 3 )
					If tex <> Null
						EntityTexture gad\CloseBtn, tex
						FreeTexture tex
						
						useSkin = tex
					EndIf
				EndIf
				If useSkin = Null
					tex = FUI_CreateTexture( BW, BH, 1+2, 3 )
					
					;Up Image
					SetBuffer TextureBuffer( tex, 0 )
					FUI_SetColor( SC_TITLEBAR )
					Rect 0, 0, BW, BH
					FUI_SetColor( SC_BLACK )
					Line 5, 5, BW - 6, BH - 6
					Line 5, BH - 6, BW - 6, 5
					Rect 0, 0, BW, BH, 0
					
					;Over Image
					SetBuffer TextureBuffer( tex, 1 )
					FUI_SetColor( SC_TITLEBAR )
					Rect 0, 0, BW, BH
					FUI_AdjustColor( -40 )
					Rect 1, 1, BW-2, BH-2, 0
					FUI_AdjustColor( 40 )
					Rect 1, 1, BW-2, 1
					Rect 1, 1, 1, BH-2
					FUI_SetColor( SC_BLACK )
					Line 5, 5, BW - 6, BH - 6
					Line 5, BH - 6, BW - 6, 5
					Rect 0, 0, BW, BH, 0
					
					;Down Image
					SetBuffer TextureBuffer( tex, 2 )
					FUI_SetColor( SC_TITLEBAR )
					Rect 0, 0, BW, BH
					FUI_AdjustColor( 40 )
					Rect 1, 1, BW-2, BH-2, 0
					FUI_AdjustColor(-40 )
					Rect 1, 1, BW-2, 1
					Rect 1, 1, 1, BH-2
					FUI_SetColor( SC_BLACK )
					Line 5, 5, BW - 6, BH - 6
					Line 5, BH - 6, BW - 6, 5
					Rect 0, 0, BW, BH, 0
					
					EntityTexture gad\CloseBtn, tex
					FreeTexture tex
				EndIf
				SetBuffer app\Buffer
			EndIf
			;Min Button
			If (gad\Flags And WS_MINBUTTON) = WS_MINBUTTON
				surf = GetSurface( gad\MinBtn, 1 )
				ClearSurface surf
				
				FUI_SetColor( SC_WHITE )
				FUI_Rect 0, 0, BW, BH, surf
				
				useSkin = Null
				If SKIN_ENABLED = True
					tex = FUI_LoadTexture( SKIN_PATH$ + "window\min.bmp", 1, 0, 0, 6 )
					If tex <> Null
						EntityTexture gad\MinBtn, tex
						FreeTexture tex
						
						useSkin = tex
					EndIf
				EndIf
				
				If useSkin = Null
					tex = FUI_CreateTexture( BW, BH, 1+2, 6, 0 )
					
					;Up Image
					SetBuffer TextureBuffer( tex, 0 )
					FUI_SetColor( SC_TITLEBAR )
					Rect 0, 0, BW, BH
					FUI_SetColor( SC_BLACK )
					Rect 5, BH - 7, BW - 6 - 5, 2
					Rect 0, 0, BW, BH, 0
					
					;Over Image
					SetBuffer TextureBuffer( tex, 1 )
					FUI_SetColor( SC_TITLEBAR )
					Rect 0, 0, BW, BH
					FUI_AdjustColor( -40 )
					Rect 1, 1, BW-2, BH-2, 0
					FUI_AdjustColor( 40 )
					Rect 1, 1, BW-2, 1
					Rect 1, 1, 1, BH-2
					FUI_SetColor( SC_BLACK )
					Rect 5, BH - 7, BW - 6 - 5, 2
					Rect 0, 0, BW, BH, 0
					
					;Down Image
					SetBuffer TextureBuffer( tex, 2 )
					FUI_SetColor( SC_TITLEBAR )
					Rect 0, 0, BW, BH
					FUI_AdjustColor( 40 )
					Rect 1, 1, BW-2, BH-2, 0
					FUI_AdjustColor(-40 )
					Rect 1, 1, BW-2, 1
					Rect 1, 1, 1, BH-2
					FUI_SetColor( SC_BLACK )
					Rect 5, BH - 7, BW - 6 - 5, 2
					Rect 0, 0, BW, BH, 0
					
					;Up Image
					SetBuffer TextureBuffer( tex, 3 )
					FUI_SetColor( SC_TITLEBAR )
					Rect 0, 0, BW, BH
					FUI_SetColor( SC_BLACK )
					Rect 5, BH - 7, BW - 6 - 5, 2
					Rect 0, 0, BW, BH, 0
					
					;Over Image
					SetBuffer TextureBuffer( tex, 4 )
					FUI_SetColor( SC_TITLEBAR )
					Rect 0, 0, BW, BH
					FUI_AdjustColor( -40 )
					Rect 1, 1, BW-2, BH-2, 0
					FUI_AdjustColor( 40 )
					Rect 1, 1, BW-2, 1
					Rect 1, 1, 1, BH-2
					FUI_SetColor( SC_BLACK )
					Rect 5, BH - 7, BW - 6 - 5, 2
					Rect 0, 0, BW, BH, 0
					
					;Down Image
					SetBuffer TextureBuffer( tex, 5 )
					FUI_SetColor( SC_TITLEBAR )
					Rect 0, 0, BW, BH
					FUI_AdjustColor( 40 )
					Rect 1, 1, BW-2, BH-2, 0
					FUI_AdjustColor(-40 )
					Rect 1, 1, BW-2, 1
					Rect 1, 1, 1, BH-2
					FUI_SetColor( SC_BLACK )
					Rect 5, BH - 7, BW - 6 - 5, 2
					Rect 0, 0, BW, BH, 0
					
					EntityTexture gad\MinBtn, tex
					FreeTexture tex
				EndIf
				
				SetBuffer app\Buffer
			EndIf
		EndIf
	EndIf
	
	If gad\CloseBtn <> Null
		FUI_SetChildPosition gad\CloseBtn, BX, BY
		
		BX = BX - BW - 1
	EndIf
	If gad\MinBtn <> Null
		FUI_SetChildPosition gad\MinBtn, BX, BY
	EndIf
	
	If SKIN_ENABLED = True
		FUI_SkinWindow gad
	EndIf
	
End Function

Function FUI_BuildMenuTitle( gad.MenuTitle, Mode=0 )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	If Mode = 0
		;Menu Title
		SetFont app\fntMenu
		
		surf.BBSurface = GetSurface( gad\Mesh, 1 )
		ClearSurface surf
		
		FUI_SetGradientDir DIR_VERTICAL
		
		FUI_SetColor SC_WHITE
		FUI_AdjustColor 20
		FUI_AdjustColor 0, True
		
		FUI_Rect 0, 0, gad\W, gad\H, surf
		
		tex.BBTexture = FUI_CreateTexture( gad\W, gad\H, 1+2, 4 )
		EntityTexture gad\Mesh, tex
		
		;Up Image
		SetBuffer TextureBuffer( tex, 0 )
		
		FUI_SetColor SC_MENUTITLE_TEXT
		FUI_Text tex, gad\W / 2, gad\H / 2, gad\Caption, 1, 1
		
		;Over Image
		SetBuffer TextureBuffer( tex, 1 )
		
		FUI_SetColor SC_MENUTITLE_OVER
		Rect 0, 0, gad\W, gad\H
		FUI_SetColor SC_MENUTITLE_BORDER_OVER
		Rect 0, 0, gad\W, gad\H, 0
		
		FUI_SetColor SC_MENUTITLE_TEXT_OVER
		FUI_Text tex, gad\W / 2, gad\H / 2, gad\Caption, 1, 1
		
		;Down Image
		SetBuffer TextureBuffer( tex, 2 )
		
		FUI_SetColor SC_MENUTITLE_SEL
		Rect 0, 0, gad\W, gad\H
		FUI_SetColor SC_MENUTITLE_BORDER_SEL
		Rect 0, 0, gad\W, gad\H, 0
		
		FUI_SetColor SC_MENUTITLE_TEXT_SEL
		FUI_Text tex, gad\W / 2, gad\H / 2, gad\Caption, 1, 1
		
		;Disabled Image
		SetBuffer TextureBuffer( tex, 3 )
		
		FUI_SetColor SC_MENUBAR
		FUI_AdjustColor-60
		FUI_Text tex, gad\W / 2, gad\H / 2, gad\Caption, 1, 1
		
		FreeTexture tex
	EndIf
	
	;DropDown
	surf = GetSurface( gad\DropDown, 1 )
	ClearSurface surf
	
	If MENUDROPDOWN_STRIP = False
		FUI_SetGradientDir DIR_HORIZONTAL
		
		FUI_SetColor SC_MENUDROPDOWN
		FUI_AdjustColor-20, True
		FUI_Rect 1, 1, gad\DW + MENUDROPDOWN_PADDING*2 - 2, gad\DH + MENUDROPDOWN_PADDING*2 - 2, surf
	Else
		FUI_SetGradientDir DIR_VERTICAL
		
		FUI_SetColor SC_MENUDROPDOWN_STRIP
		FUI_AdjustColor-20, True
		FUI_Rect 1, 1, MENUDROPDOWN_STRIP_W, gad\DH + MENUDROPDOWN_PADDING*2 - 2, surf
		
		FUI_SetGradientDir DIR_HORIZONTAL
		
		FUI_SetColor SC_MENUDROPDOWN
		FUI_AdjustColor-20, True
		FUI_Rect 1 + MENUDROPDOWN_STRIP_W, 1, gad\DW + MENUDROPDOWN_PADDING*2 - 2 - MENUDROPDOWN_STRIP_W, gad\DH + MENUDROPDOWN_PADDING*2 - 2, surf
	EndIf
	
	FUI_SetColor SC_MENUDROPDOWN_BORDER
	FUI_Rect 0, 0, gad\DW + MENUDROPDOWN_PADDING*2, 1, surf
	FUI_Rect 0, 0, 1, gad\DH + MENUDROPDOWN_PADDING*2, surf
	FUI_Rect 0, gad\DH + MENUDROPDOWN_PADDING*2 - 1, gad\DW + MENUDROPDOWN_PADDING*2, 1, surf
	FUI_Rect gad\DW + MENUDROPDOWN_PADDING*2 - 1, 0, 1, gad\DH + MENUDROPDOWN_PADDING*2, surf
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildMenuItem( gad.MenuItem, Mode=0 )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	If gad\Checkable = True; And gad\Icon =-1; Or gad\Icon = 0
		cnt = 8
	Else
		cnt = 4
	EndIf
	
	If Mode = 0
		;Menu Item
		SetFont app\fntMenu
		
		surf.BBSurface = GetSurface( gad\Mesh, 1 )
		ClearSurface surf
		
		If gad\Caption <> "-"
			FUI_SetGradientDir DIR_VERTICAL
			
			FUI_SetColor SC_WHITE
			FUI_Rect 0, 0, gad\W, gad\H, surf
			
			tex.BBTexture = FUI_CreateTexture( gad\W, gad\H, 1+2, cnt )
			EntityTexture gad\Mesh, tex
			
			If gad\Owner <> Null
				DW = gad\Owner\DW
			ElseIf gad\Parent <> Null
				DW = gad\Parent\DW
			EndIf
			
			For A = 0 To cnt - 1 Step 4
				;Up Image
				SetBuffer TextureBuffer( tex, A )
				
				FUI_SetColor SC_MENUITEM_TEXT
				FUI_Text tex, 4 + MENUDROPDOWN_STRIP_W, gad\H / 2, gad\Caption, 0, 1
				FUI_Text tex, gad\W - 10 - StringWidth( gad\ShortCut ), gad\H / 2, gad\ShortCut, 0, 1
				
				;Over Image
				SetBuffer TextureBuffer( tex, A+1 )
				
				FUI_SetColor SC_MENUITEM_OVER
				Rect 0, 0, gad\W, gad\H
				FUI_SetColor SC_MENUITEM_BORDER_OVER
				Rect 0, 0, gad\W, gad\H, 0
				
				FUI_SetColor SC_MENUITEM_TEXT_OVER
				FUI_Text tex, 4 + MENUDROPDOWN_STRIP_W, gad\H / 2, gad\Caption, 0, 1
				FUI_Text tex, gad\W - 10 - StringWidth( gad\ShortCut ), gad\H / 2, gad\ShortCut, 0, 1
				
				;Down Image
				SetBuffer TextureBuffer( tex, A+2 )
				
				FUI_SetColor SC_MENUITEM_SEL
				Rect 0, 0, gad\W, gad\H
				FUI_SetColor SC_MENUITEM_BORDER_SEL
				Rect 0, 0, gad\W, gad\H, 0
				
				FUI_SetColor SC_MENUITEM_TEXT_SEL
				FUI_Text tex, 4 + MENUDROPDOWN_STRIP_W, gad\H / 2, gad\Caption, 0, 1
				FUI_Text tex, gad\W - 10 - StringWidth( gad\ShortCut ), gad\H / 2, gad\ShortCut, 0, 1
				
				;Disabled Image
				SetBuffer TextureBuffer( tex, A+3 )
				
				FUI_SetColor SC_MENUITEM
				FUI_AdjustColor-60
				FUI_Text tex, 4 + MENUDROPDOWN_STRIP_W, gad\H / 2, gad\Caption, 0, 1
				FUI_Text tex, gad\W - 10 - StringWidth( gad\ShortCut ), gad\H / 2, gad\ShortCut, 0, 1
			Next
			
			If gad\HasChildren = True
				For A = 0 To cnt - 1
					SetBuffer TextureBuffer( tex, A )
					
					FUI_SetColor SC_MENUITEM_TEXT
					Rect gad\W - 7, gad\H / 2 - 2, 1, 5
					Rect gad\W - 6, gad\H / 2 - 1, 1, 3
					Rect gad\W - 5, gad\H / 2, 1, 1
				Next
			EndIf
			
			If gad\Checkable = True
				;Check Box
				For A = 0 To cnt - 1
					SetBuffer TextureBuffer( tex, A )
					
					BW = MENUDROPDOWN_STRIP_W - MENUDROPDOWN_PADDING*4
					BH = BW
					BX = MENUDROPDOWN_PADDING+1
					BY = (MENUDROPDOWN_STRIP_W - BW) / 2 - MENUDROPDOWN_PADDING + 1
					
					FUI_SetColor SC_MENUITEM
					Rect BX, BY, BW, BH
					FUI_AdjustColor 40
					Rect BX, BY, BW, BH, False
					FUI_AdjustColor-40
					Rect BX, BY, BW, 1
					Rect BX, BY, 1, BH
					
					If A > 3
						FUI_SetColor SC_MENUITEMTEXT
						Line BX+3, BY+6, BX+5, BY+8
						Line BX+5, BY+8, BX+BW-3, BY+4
						Line BX+3, BY+7, BX+5, BY+9
						Line BX+5, BY+9, BX+BW-3, BY+5
					EndIf
				Next
			EndIf
			
			FreeTexture tex
			
			FUI_SetChildPosition gad\DropDown, gad\W + MENUITEM_DROPDOWN_X, MENUITEM_DROPDOWN_Y
		Else
			FUI_SetColor SC_MENUITEM
			FUI_AdjustColor-60
			FUI_Rect 4 + (MENUDROPDOWN_STRIP*MENUDROPDOWN_STRIP_W), gad\H / 2, gad\W - (MENUDROPDOWN_STRIP*MENUDROPDOWN_STRIP_W) - 8, 1, surf
		EndIf
	EndIf
	
	;Initial Build
	If gad\Icon <> Null
		
		tex = FUI_GadgetTexture( gad\Mesh )
		
		IW = ImageWidth( gad\Icon )
		IH = ImageHeight( gad\Icon )
		IX = MENUDROPDOWN_STRIP_W / 2 - IW / 2
		IY = MENUITEM_HEIGHT / 2 - IH / 2
		LockBuffer ImageBuffer( gad\Icon )
		
		For A = 0 To cnt - 1
			SetBuffer TextureBuffer( tex, A )
			LockBuffer
			
			For X = IX To IX + IW - 1
				For Y = IY To IY + IH - 1
					RGB = ReadPixelFast( X - IX, Y - IY, ImageBuffer( gad\Icon ) ) And $FFFFFFFF
					
					cr = FUI_GetRed( RGB )
					cg = FUI_GetGreen( RGB )
					cb = FUI_GetBlue( RGB )
					
					If A = 1 Or A = 5
						FUI_SetColor SC_MENUITEM_OVER
						FUI_AdjustColor-60
					ElseIf A = 2 Or A = 6
						FUI_SetColor SC_MENUITEM_SEL
						FUI_AdjustColor-60
					EndIf
					shadow = app\NB Or (app\NG Shl 8) Or (app\NR Shl 16) Or (255 Shl 24)
					
					If Not cr = FUI_GetRed( SC_MASK ) And cg = FUI_GetGreen( SC_MASK ) And cb = FUI_GetBlue( SC_MASK )
						If A = 0 Or A = 4
							If X >= 0 And Y >= 0 And X < TextureWidth( tex ) And Y < TextureHeight( tex )
								WritePixelFast X, Y, RGB
							EndIf
						ElseIf A = 3 Or A = 7
							If X >= 0 And Y >= 0 And X < TextureWidth( tex ) And Y < TextureHeight( tex )
								WritePixelFast X, Y, shadow
							EndIf
						Else
							If X >= 0 And Y >= 0 And X < TextureWidth( tex ) And Y < TextureHeight( tex )
								WritePixelFast X, Y, shadow
							EndIf
							If X-2 >= 0 And Y-2 >= 0 And X-2 < TextureWidth( tex ) And Y-2 < TextureHeight( tex )
								WritePixelFast X-2, Y-2, RGB
							EndIf
						EndIf
					EndIf
				Next
			Next
			UnlockBuffer
		Next
		UnlockBuffer ImageBuffer( gad\Icon )
	EndIf
	
	;DropDown
	If gad\HasChildren = True
		surf = GetSurface( gad\DropDown, 1 )
		ClearSurface surf
		
		If MENUDROPDOWN_STRIP = False
			FUI_SetGradientDir DIR_HORIZONTAL
			
			FUI_SetColor SC_MENUDROPDOWN
			FUI_AdjustColor-20, True
			FUI_Rect 1, 1, gad\DW + MENUDROPDOWN_PADDING*2 - 2, gad\DH + MENUDROPDOWN_PADDING*2 - 2, surf
		Else
			FUI_SetGradientDir DIR_VERTICAL
			
			FUI_SetColor SC_MENUDROPDOWN_STRIP
			FUI_AdjustColor-20, True
			FUI_Rect 1, 1, MENUDROPDOWN_STRIP_W, gad\DH + MENUDROPDOWN_PADDING*2 - 2, surf
			
			FUI_SetGradientDir DIR_HORIZONTAL
			
			FUI_SetColor SC_MENUDROPDOWN
			FUI_AdjustColor-20, True
			FUI_Rect 1 + MENUDROPDOWN_STRIP_W, 1, gad\DW + MENUDROPDOWN_PADDING*2 - 2 - MENUDROPDOWN_STRIP_W, gad\DH + MENUDROPDOWN_PADDING*2 - 2, surf
		EndIf
		
		FUI_SetColor SC_MENUDROPDOWN_BORDER
		FUI_Rect 0, 0, gad\DW + MENUDROPDOWN_PADDING*2, 1, surf
		FUI_Rect 0, 0, 1, gad\DH + MENUDROPDOWN_PADDING*2, surf
		FUI_Rect 0, gad\DH + MENUDROPDOWN_PADDING*2 - 1, gad\DW + MENUDROPDOWN_PADDING*2, 1, surf
		FUI_Rect gad\DW + MENUDROPDOWN_PADDING*2 - 1, 0, 1, gad\DH + MENUDROPDOWN_PADDING*2, surf
	EndIf
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildContextMenu( gad.ContextMenu, Mode=0 )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	;DropDown
	surf.BBSurface = GetSurface( gad\DropDown, 1 )
	ClearSurface surf
	
	If MENUDROPDOWN_STRIP = False
		FUI_SetGradientDir DIR_HORIZONTAL
		
		FUI_SetColor SC_MENUDROPDOWN
		FUI_AdjustColor-20, True
		FUI_Rect 1, 1, gad\DW + MENUDROPDOWN_PADDING*2 - 2, gad\DH + MENUDROPDOWN_PADDING*2 - 2, surf
	Else
		FUI_SetGradientDir DIR_VERTICAL
		
		FUI_SetColor SC_MENUDROPDOWN_STRIP
		FUI_AdjustColor-20, True
		FUI_Rect 1, 1, MENUDROPDOWN_STRIP_W, gad\DH + MENUDROPDOWN_PADDING*2 - 2, surf
		
		FUI_SetGradientDir DIR_HORIZONTAL
		
		FUI_SetColor SC_MENUDROPDOWN
		FUI_AdjustColor-20, True
		FUI_Rect 1 + MENUDROPDOWN_STRIP_W, 1, gad\DW + MENUDROPDOWN_PADDING*2 - 2 - MENUDROPDOWN_STRIP_W, gad\DH + MENUDROPDOWN_PADDING*2 - 2, surf
	EndIf
	
	FUI_SetColor SC_MENUDROPDOWN_BORDER
	FUI_Rect 0, 0, gad\DW + MENUDROPDOWN_PADDING*2, 1, surf
	FUI_Rect 0, 0, 1, gad\DH + MENUDROPDOWN_PADDING*2, surf
	FUI_Rect 0, gad\DH + MENUDROPDOWN_PADDING*2 - 1, gad\DW + MENUDROPDOWN_PADDING*2, 1, surf
	FUI_Rect gad\DW + MENUDROPDOWN_PADDING*2 - 1, 0, 1, gad\DH + MENUDROPDOWN_PADDING*2, surf
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildContextMenuItem( gad.ContextMenuItem, Mode=0 )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	If Mode = 0
		;Menu Item
		SetFont app\fntMenu
		
		surf.BBSurface = GetSurface( gad\Mesh, 1 )
		ClearSurface surf
		
		If gad\Caption <> "-"
			FUI_SetGradientDir DIR_VERTICAL
			
			FUI_SetColor SC_WHITE
			FUI_Rect 0, 0, gad\W, gad\H, surf
			
			If gad\Checkable = True
				cnt = 8
			Else
				cnt = 4
			EndIf
			
			tex.BBTexture = FUI_CreateTexture( gad\W, gad\H, 1+2, cnt )
			EntityTexture gad\Mesh, tex
			
			If gad\Owner <> Null
				DW = gad\Owner\DW
			ElseIf gad\Parent <> Null
				DW = gad\Parent\DW
			EndIf
			
			For A = 0 To cnt - 1 Step 4
				;Up Image
				SetBuffer TextureBuffer( tex, A )
				
				FUI_SetColor SC_MENUITEM_TEXT
				FUI_Text tex, 4 + MENUDROPDOWN_STRIP_W, gad\H / 2, gad\Caption, 0, 1
				FUI_Text tex, gad\W - 10 - StringWidth( gad\ShortCut ), gad\H / 2, gad\ShortCut, 0, 1
				
				;Over Image
				SetBuffer TextureBuffer( tex, A+1 )
				
				FUI_SetColor SC_MENUITEM_OVER
				Rect 0, 0, gad\W, gad\H
				FUI_SetColor SC_MENUITEM_BORDER_OVER
				Rect 0, 0, gad\W, gad\H, 0
				
				FUI_SetColor SC_MENUITEM_TEXT_OVER
				FUI_Text tex, 4 + MENUDROPDOWN_STRIP_W, gad\H / 2, gad\Caption, 0, 1
				FUI_Text tex, gad\W - 10 - StringWidth( gad\ShortCut ), gad\H / 2, gad\ShortCut, 0, 1
				
				;Down Image
				SetBuffer TextureBuffer( tex, A+2 )
				
				FUI_SetColor SC_MENUITEM_SEL
				Rect 0, 0, gad\W, gad\H
				FUI_SetColor SC_MENUITEM_BORDER_SEL
				Rect 0, 0, gad\W, gad\H, 0
				
				FUI_SetColor SC_MENUITEM_TEXT_SEL
				FUI_Text tex, 4 + MENUDROPDOWN_STRIP_W, gad\H / 2, gad\Caption, 0, 1
				FUI_Text tex, gad\W - 10 - StringWidth( gad\ShortCut ), gad\H / 2, gad\ShortCut, 0, 1
				
				;Disabled Image
				SetBuffer TextureBuffer( tex, A+3 )
				
				FUI_SetColor SC_MENUITEM
				FUI_AdjustColor-60
				FUI_Text tex, 4 + MENUDROPDOWN_STRIP_W, gad\H / 2, gad\Caption, 0, 1
				FUI_Text tex, gad\W - 10 - StringWidth( gad\ShortCut ), gad\H / 2, gad\ShortCut, 0, 1
			Next
			
			If gad\HasChildren = True
				For A = 0 To cnt - 1
					SetBuffer TextureBuffer( tex, A )
					
					FUI_SetColor SC_MENUITEM_TEXT
					Rect gad\W - 7, gad\H / 2 - 2, 1, 5
					Rect gad\W - 6, gad\H / 2 - 1, 1, 3
					Rect gad\W - 5, gad\H / 2, 1, 1
				Next
			EndIf
			
			If gad\Checkable = True
				;Check Box
				For A = 0 To cnt - 1
					SetBuffer TextureBuffer( tex, A )
					
					BW = MENUDROPDOWN_STRIP_W - MENUDROPDOWN_PADDING*4
					BH = BW
					BX = MENUDROPDOWN_PADDING+1
					BY = (MENUDROPDOWN_STRIP_W - BW) / 2 - MENUDROPDOWN_PADDING + 1
					
					FUI_SetColor SC_MENUITEM
					Rect BX, BY, BW, BH
					FUI_AdjustColor 40
					Rect BX, BY, BW, BH, False
					FUI_AdjustColor-40
					Rect BX, BY, BW, 1
					Rect BX, BY, 1, BH
					
					If A > 3
						FUI_SetColor SC_MENUITEMTEXT
						Line BX+3, BY+6, BX+5, BY+8
						Line BX+5, BY+8, BX+BW-3, BY+4
						Line BX+3, BY+7, BX+5, BY+9
						Line BX+5, BY+9, BX+BW-3, BY+5
					EndIf
				Next
			EndIf
			
			FreeTexture tex
			
			FUI_SetChildPosition gad\DropDown, gad\W + MENUITEM_DROPDOWN_X, MENUITEM_DROPDOWN_Y
		Else
			FUI_SetColor SC_MENUITEM
			FUI_AdjustColor-60
			FUI_Rect 4 + (MENUDROPDOWN_STRIP*MENUDROPDOWN_STRIP_W), gad\H / 2, gad\W - (MENUDROPDOWN_STRIP*MENUDROPDOWN_STRIP_W) - 8, 1, surf
		EndIf
	EndIf
	
	;DropDown
	If gad\HasChildren = True
		surf = GetSurface( gad\DropDown, 1 )
		ClearSurface surf
		
		If MENUDROPDOWN_STRIP = False
			FUI_SetGradientDir DIR_HORIZONTAL
			
			FUI_SetColor SC_MENUDROPDOWN
			FUI_AdjustColor-20, True
			FUI_Rect 1, 1, gad\DW + MENUDROPDOWN_PADDING*2 - 2, gad\DH + MENUDROPDOWN_PADDING*2 - 2, surf
		Else
			FUI_SetGradientDir DIR_VERTICAL
			
			FUI_SetColor SC_MENUDROPDOWN_STRIP
			FUI_AdjustColor-20, True
			FUI_Rect 1, 1, MENUDROPDOWN_STRIP_W, gad\DH + MENUDROPDOWN_PADDING*2 - 2, surf
			
			FUI_SetGradientDir DIR_HORIZONTAL
			
			FUI_SetColor SC_MENUDROPDOWN
			FUI_AdjustColor-20, True
			FUI_Rect 1 + MENUDROPDOWN_STRIP_W, 1, gad\DW + MENUDROPDOWN_PADDING*2 - 2 - MENUDROPDOWN_STRIP_W, gad\DH + MENUDROPDOWN_PADDING*2 - 2, surf
		EndIf
		
		FUI_SetColor SC_MENUDROPDOWN_BORDER
		FUI_Rect 0, 0, gad\DW + MENUDROPDOWN_PADDING*2, 1, surf
		FUI_Rect 0, 0, 1, gad\DH + MENUDROPDOWN_PADDING*2, surf
		FUI_Rect 0, gad\DH + MENUDROPDOWN_PADDING*2 - 1, gad\DW + MENUDROPDOWN_PADDING*2, 1, surf
		FUI_Rect gad\DW + MENUDROPDOWN_PADDING*2 - 1, 0, 1, gad\DH + MENUDROPDOWN_PADDING*2, surf
	EndIf
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildRegion( gad.Region )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	gad\FW = gad\W
	gad\FH = gad\H
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildTab( gad.Tab )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	;Tab Form
	surf.BBSurface = GetSurface( gad\Mesh, 1 )
	ClearSurface surf
	
	If gad\W <= 0 Or gad\H - TABPAGE_HEIGHT - 2 <= 0
		Return
	EndIf
	
	FUI_SetGradientDir DIR_VERTICAL
	
	useSkin.BBTexture = Null
	If SKIN_ENABLED = True
		useSkin = FUI_SkinTab( gad )
	EndIf
	
	If useSkin = Null
		FUI_SetColor SC_GADGET
		FUI_AdjustColor-20, True
		FUI_Rect 2, TABPAGE_HEIGHT, gad\W - 4, gad\H - TABPAGE_HEIGHT - 2, surf
		
		FUI_SetColor SC_GADGET_BORDER
		FUI_Rect 0, TABPAGE_HEIGHT, gad\W, gad\H - TABPAGE_HEIGHT, surf, 0
		
		FUI_SetColor SC_GADGET
		FUI_AdjustColor-40
		FUI_Rect gad\W - 2, TABPAGE_HEIGHT + 2, 1, gad\H - TABPAGE_HEIGHT - 3, surf
		FUI_Rect 1, gad\H - 2, gad\W - 2, 1, surf
		FUI_AdjustColor 40
		FUI_Rect 1, TABPAGE_HEIGHT + 1, gad\W - 2, 1, surf
		FUI_Rect 1, TABPAGE_HEIGHT + 2, 1, gad\H - TABPAGE_HEIGHT - 3, surf
	Else
		FUI_SetColor SC_WHITE
		FUI_Rect 0, TABPAGE_HEIGHT, gad\W, gad\H - TABPAGE_HEIGHT, surf
		
		EntityTexture gad\Mesh, useSkin
		
		FreeTexture useSkin
		useSkin = Null
	EndIf
	
	gad\FW = gad\W
	gad\FH = gad\H
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildTabPage( gad.TabPage )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	;Tab Page
	SetFont app\fntGadget
	gad\W = StringWidth( gad\Caption ) + 20
	
	surf.BBSurface = GetSurface( gad\Mesh, 1 )
	ClearSurface surf
	
	FUI_SetGradientDir DIR_VERTICAL
	
	FUI_SetColor SC_WHITE
	FUI_AdjustColor 20
	FUI_AdjustColor 0, True
	
	FUI_Rect 0, 0, gad\W, gad\H+2, surf
	
	useSkin.BBTexture = Null
	If SKIN_ENABLED = True
		useSkin = FUI_SkinTabPage( gad )
	EndIf
	
	If useSkin = Null
		tex.BBTexture = FUI_CreateTexture( gad\W, gad\H+2, 1+2, 1 )
		EntityTexture gad\Mesh, tex
	
		If gad\Owner\actTabPage = gad
			If gad\Disabled = False
				;Selected Image
				SetBuffer TextureBuffer( tex )
				
				FUI_SetColor SC_GADGET_BORDER
				Rect 0, 0, gad\W, gad\H, 0
				FUI_SetColor SC_GADGET
				Rect 1, 1, gad\W - 2, gad\H + 1
				FUI_AdjustColor-40
				Rect gad\W - 2, 1, 1, gad\H + 1
				FUI_AdjustColor 40
				Rect 1, 1, gad\W - 2, 1
				Rect 1, 1, 1, gad\H + 1
				
				FUI_SetColor SC_GADGET_TEXT
				FUI_Text tex, gad\W / 2, gad\H / 2, gad\Caption, 1, 1
			Else
				;Selected Image - Disabled
				SetBuffer TextureBuffer( tex )
				
				FUI_SetColor SC_GADGET_BORDER
				Rect 0, 0, gad\W, gad\H, 0
				FUI_SetColor SC_GADGET
				Rect 1, 1, gad\W - 2, gad\H + 1
				FUI_AdjustColor-40
				Rect gad\W - 2, 1, 1, gad\H + 1
				FUI_AdjustColor 40
				Rect 1, 1, gad\W - 2, 1
				Rect 1, 1, 1, gad\H + 1
				
				FUI_SetColor SC_GADGET
				FUI_AdjustColor-60
				FUI_Text tex, gad\W / 2, gad\H / 2, gad\Caption, 1, 1
			EndIf
		Else
			If gad\Disabled = False
				;Unselected Image
				SetBuffer TextureBuffer( tex )
				
				FUI_SetColor SC_GADGET_BORDER
				Rect 0, 2, gad\W, gad\H - 2, 0
				FUI_SetColor SC_GADGET
				FUI_AdjustColor-20
				Rect 1, 3, gad\W - 2, gad\H - 3
				FUI_AdjustColor-40
				Rect gad\W - 2, 3, 1, gad\H - 3
				FUI_AdjustColor 40
				Rect 1, 3, gad\W - 2, 1
				Rect 1, 3, 1, gad\H - 3
				
				FUI_SetColor SC_GADGET_TEXT
				FUI_Text tex, gad\W / 2, gad\H / 2 + 2, gad\Caption, 1, 1
			Else
				;Unselected Image - Disabled
				SetBuffer TextureBuffer( tex )
				
				FUI_SetColor SC_GADGET_BORDER
				Rect 0, 2, gad\W, gad\H - 2, 0
				FUI_SetColor SC_GADGET
				FUI_AdjustColor-20
				Rect 1, 3, gad\W - 2, gad\H - 3
				FUI_AdjustColor-40
				Rect gad\W - 2, 3, 1, gad\H - 3
				FUI_AdjustColor 40
				Rect 1, 3, gad\W - 2, 1
				Rect 1, 3, 1, gad\H - 3
				
				FUI_SetColor SC_GADGET
				FUI_AdjustColor-60
				FUI_Text tex, gad\W / 2, gad\H / 2 + 2, gad\Caption, 1, 1
			EndIf
		EndIf
		
		FreeTexture tex
	Else
		If gad\Disabled = False
			If gad\Owner\actTabPage = gad
				EntityTexture gad\Mesh, useSkin, 0
				
				SetBuffer TextureBuffer( useSkin, 0 )
				FUI_SetColor SC_GADGET_TEXT
				FUI_Text useSkin, gad\W / 2, gad\H / 2, gad\Caption, 1, 1
			Else
				EntityTexture gad\Mesh, useSkin, 1
				
				SetBuffer TextureBuffer( useSkin, 1 )
				FUI_SetColor SC_GADGET_TEXT
				FUI_Text useSkin, gad\W / 2, gad\H / 2 + 2, gad\Caption, 1, 1
			EndIf
		Else
			EntityTexture gad\Mesh, useSkin, 2
			
			SetBuffer TextureBuffer( useSkin, 2 )
			FUI_SetColor SC_GADGET
			FUI_AdjustColor-60
			FUI_Text useSkin, gad\W / 2, gad\H / 2, gad\Caption, 1, 1
		EndIf
		
		FreeTexture useSkin
		useSkin = Null
	EndIf
	
	If gad\Icon <> Null
		iw = ImageWidth( gad\Icon )
		ih = ImageHeight( gad\Icon )
		
		If gad\Owner\actTabPage = gad
			FUI_SetChildPosition gad\IconMesh, gad\W / 2 - iw / 2, gad\H / 2 - ih / 2
		Else
			FUI_SetChildPosition gad\IconMesh, gad\W / 2 - iw / 2, gad\H / 2 - ih / 2 + 2
		EndIf
		
		surf = GetSurface( gad\IconMesh, 1 )
		ClearSurface surf
		
		FUI_SetColor SC_WHITE
		FUI_Rect 0, 0, iw, ih, surf
		
		tex = FUI_CreateTexture( iw, ih, 1+2, 1 )
		EntityTexture gad\IconMesh, tex
		
		SetBuffer TextureBuffer( tex )
		
		LockBuffer ImageBuffer( gad\Icon )
		LockBuffer
		IX = 0;gad\W / 2 - IW / 2
		IY = 0;gad\H / 2 - IH / 2
		For A = IX To IX + IW - 1
			For B = IY To IY + IH - 1
				RGB = ReadPixelFast( A - IX, B - IY, ImageBuffer( gad\Icon ) ) And $FFFFFFFF
				
				cr = FUI_GetRed( RGB )
				cg = FUI_GetGreen( RGB )
				cb = FUI_GetBlue( RGB )
				
				If Not cr = 255 And cg = 0 And cb = 0
					WritePixelFast A, B, RGB
				EndIf
			Next
		Next
		UnlockBuffer ImageBuffer( gad\Icon )
		UnlockBuffer
		
		FreeTexture tex
		tex = Null
	EndIf
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildPanel( gad.Panel, Mode=0 )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	If Mode = 0
		;Drop Down
		surf.BBSurface = GetSurface( gad\DropDown, 1 )
		ClearSurface surf
		
		If gad\H <= PANEL_HEIGHT Or gad\W <= 0
			Return
		EndIf
		
		FUI_SetColor SC_TOOLBAR_BORDER_SEL
		FUI_Rect 0, 0, gad\W, gad\H + 1, surf, 0
		
		gad\FW = gad\W
		gad\FH = gad\H
	EndIf
	
	;Panel
	surf = GetSurface( gad\Mesh, 1 )
	ClearSurface surf
	
	If gad\W <= 0
		Return
	EndIf
	
	FUI_SetGradientDir DIR_VERTICAL
	
	FUI_SetColor SC_WHITE
	FUI_Rect 0, 0, gad\W, PANEL_HEIGHT, surf
	
	If SKIN_ENABLED = True
		useSkin.BBTexture = FUI_SkinPanel( gad )
	EndIf
	
	If useSkin = Null
		tex.BBTexture = FUI_CreateTexture( gad\W, PANEL_HEIGHT, 1+2, 6 )
		EntityTexture gad\Mesh, tex, gad\Active*3
	EndIf
	
	For A = 0 To 5
		If useSkin <> Null
			tex = useSkin
		EndIf
		SetBuffer TextureBuffer( tex, A )
		
		;Up Image
		If A = 0 Or A = 3
			If useSkin = Null
				FUI_SetColor SC_GADGET
				Rect 0, 0, gad\W, gad\H
				
				FUI_AdjustColor-40
				Rect 1, 1, gad\W-2, PANEL_HEIGHT-2, 0
				FUI_AdjustColor 40
				Rect 1, 1, gad\W-2, 1
				Rect 1, 1, 1, PANEL_HEIGHT-2
				
				FUI_SetColor SC_GADGET_BORDER
				Rect 0, 0, gad\W, PANEL_HEIGHT, 0
				
				FUI_SetColor SC_GADGET_TEXT
				FUI_Text tex, gad\W / 2, PANEL_HEIGHT / 2, gad\Caption, 1, 1
			EndIf
			
			FUI_SetColor SC_GADGET_TEXT
			FUI_Text tex, gad\W / 2, PANEL_HEIGHT / 2, gad\Caption, 1, 1
		;Over Image
		ElseIf A = 1 Or A = 4
			If useSkin = Null
				FUI_SetColor SC_GADGET
				Rect 0, 0, gad\W, gad\H
				
				FUI_AdjustColor-40
				Rect 1, 1, gad\W-2, PANEL_HEIGHT-2, 0
				FUI_AdjustColor 40
				Rect 1, 1, gad\W-2, 1
				Rect 1, 1, 1, PANEL_HEIGHT-2
				
				FUI_SetColor SC_GADGET_BORDER
				Rect 0, 0, gad\W, PANEL_HEIGHT, 0
			EndIf
			FUI_SetColor SC_GADGET_TEXT
			FUI_Text tex, gad\W / 2, PANEL_HEIGHT / 2, gad\Caption, 1, 1
		;Disabled Image
		ElseIf A = 2 Or A = 5
			If useSkin = Null
				FUI_SetColor SC_GADGET
				Rect 0, 0, gad\W, gad\H
				
				FUI_AdjustColor-40
				Rect 1, 1, gad\W-2, PANEL_HEIGHT-2, 0
				FUI_AdjustColor 40
				Rect 1, 1, gad\W-2, 1
				Rect 1, 1, 1, PANEL_HEIGHT-2
				
				FUI_SetColor SC_GADGET
				FUI_AdjustColor-60
				Rect 0, 0, gad\W, PANEL_HEIGHT, 0
			EndIf
			
			FUI_SetColor SC_GADGET
			FUI_AdjustColor-60
			FUI_Text tex, gad\W / 2, PANEL_HEIGHT / 2, gad\Caption, 1, 1
		EndIf
		
		If useSkin = Null
			If A = 2 Or A = 5
				FUI_SetColor SC_GADGET
				FUI_AdjustColor-60
			Else
				FUI_SetColor SC_GADGET_TEXT
			EndIf
			Rect 4, PANEL_HEIGHT / 2, 9, 1
			
			If A < 3
				Rect 4 + 4, PANEL_HEIGHT / 2 - 4, 1, 9
			EndIf
		EndIf
	Next
	
	If useSkin = Null
		FreeTexture tex
	Else
		EntityTexture gad\Mesh, useSkin
		
		FreeTexture useSkin
		useSkin = Null
	EndIf
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildButton( gad.Button )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	If gad\W <= 0 Or gad\H <= 0
		surf.BBSurface = GetSurface( gad\Mesh, 1 )
		ClearSurface surf
		
		Return
	EndIf
	
	useSkin.BBTexture = Null
	If SKIN_ENABLED = True
		useSkin = FUI_SkinButton( gad )
	EndIf

	;Button
	SetFont app\fntGadget
	
	surf = GetSurface( gad\Mesh, 1 )
	ClearSurface surf
	
	FUI_SetGradientDir DIR_VERTICAL
	
	FUI_SetColor SC_WHITE
	If useSkin = Null
		FUI_AdjustColor 20
		FUI_AdjustColor 0, True
	EndIf
	FUI_Rect 0, 0, gad\W, gad\H, surf
	
	If useSkin = Null
		tex.BBTexture = FUI_CreateTexture( gad\W, gad\H, 1+2, 4 )
	Else
		tex = useSkin
	EndIf
	EntityTexture gad\Mesh, tex
	
	;Up Image
	SetBuffer TextureBuffer( tex, 0 )
	
	If useSkin = Null
		FUI_SetColor SC_GADGET
		Rect 0, 0, gad\W, gad\H
		
		If (gad\Flags And CS_BORDER) = CS_BORDER And (gad\Flags And CS_POPUP) <> CS_POPUP
			FUI_AdjustColor-40
			Rect 1, 1, gad\W-2, gad\H-2, 0
			FUI_AdjustColor 40
			Rect 1, 1, gad\W-2, 1
			Rect 1, 1, 1, gad\H-2
			
			FUI_SetColor SC_GADGET_BORDER
			Rect 0, 0, gad\W, gad\H, 0
		EndIf
	EndIf
	
	If gad\Icon <> Null
		IW = ImageWidth( gad\Icon )
		IH = ImageHeight( gad\Icon )
		
		LockBuffer ImageBuffer( gad\Icon )
		LockBuffer
		IX = gad\W / 2 - IW / 2
		IY = gad\H / 2 - IH / 2
		For A = IX To IX + IW - 1
			For B = IY To IY + IH - 1
				RGB = ReadPixelFast( A - IX, B - IY, ImageBuffer( gad\Icon ) ) And $FFFFFFFF
				
				cr = FUI_GetRed( RGB )
				cg = FUI_GetGreen( RGB )
				cb = FUI_GetBlue( RGB )
				
				If Not cr = 255 And cg = 0 And cb = 0
					WritePixelFast A, B, RGB
				EndIf
			Next
		Next
		UnlockBuffer ImageBuffer( gad\Icon )
		UnlockBuffer
	EndIf
	
	FUI_SetColor SC_GADGET_TEXT
	FUI_Text tex, gad\W / 2, gad\H / 2, gad\Caption, 1, 1
	
	;Over Image
	SetBuffer TextureBuffer( tex, 1 )
	
	If useSkin = Null
		FUI_SetColor SC_GADGET
		Rect 0, 0, gad\W, gad\H
		
		If (gad\Flags And CS_BORDER) = CS_BORDER Or (gad\Flags And CS_POPUP) = CS_POPUP
			FUI_AdjustColor-40
			Rect 1, 1, gad\W-2, gad\H-2, 0
			FUI_AdjustColor 40
			Rect 1, 1, gad\W-2, 1
			Rect 1, 1, 1, gad\H-2
			
			FUI_SetColor SC_GADGET_BORDER
			Rect 0, 0, gad\W, gad\H, 0
		EndIf
	EndIf
	
	If gad\Icon <> Null
		IW = ImageWidth( gad\Icon )
		IH = ImageHeight( gad\Icon )
		
		LockBuffer ImageBuffer( gad\Icon )
		LockBuffer
		IX = gad\W / 2 - IW / 2 - 2
		IY = gad\H / 2 - IH / 2 - 2
		For A = IX To IX + IW - 1
			For B = IY To IY + IH - 1
				RGB = ReadPixelFast( A - IX, B - IY, ImageBuffer( gad\Icon ) ) And $FFFFFFFF
				
				cr = FUI_GetRed( RGB )
				cg = FUI_GetGreen( RGB )
				cb = FUI_GetBlue( RGB )
				
				FUI_SetColor SC_GADGET
				FUI_AdjustColor-60
				
				shadow = app\NB Or (app\NG Shl 8) Or (app\NR Shl 16) Or (255 Shl 24)
				
				If Not cr = 255 And cg = 0 And cb = 0
					WritePixelFast A+2, B+2, shadow
					WritePixelFast A, B, RGB
				EndIf
			Next
		Next
		UnlockBuffer ImageBuffer( gad\Icon )
		UnlockBuffer
	EndIf
	
	FUI_SetColor SC_GADGET_TEXT
	FUI_Text tex, gad\W / 2, gad\H / 2, gad\Caption, 1, 1
	
	;Down Image
	SetBuffer TextureBuffer( tex, 2 )
	
	If useSkin = Null
		FUI_SetColor SC_GADGET
		FUI_AdjustColor 5
		Rect 0, 0, gad\W, gad\H
		
		If (gad\Flags And CS_BORDER) = CS_BORDER Or (gad\Flags And CS_POPUP) = CS_POPUP
			FUI_AdjustColor 40
			Rect 1, 1, gad\W-2, gad\H-2, 0
			FUI_AdjustColor-40
			Rect 1, 1, gad\W-2, 1
			Rect 1, 1, 1, gad\H-2
			
			FUI_SetColor SC_GADGET_BORDER
			Rect 0, 0, gad\W, gad\H, 0
		EndIf
	EndIf
	
	If gad\Icon <> Null
		IW = ImageWidth( gad\Icon )
		IH = ImageHeight( gad\Icon )
		
		LockBuffer ImageBuffer( gad\Icon )
		LockBuffer
		IX = gad\W / 2 - IW / 2
		IY = gad\H / 2 - IH / 2
		For A = IX To IX + IW - 1
			For B = IY To IY + IH - 1
				RGB = ReadPixelFast( A - IX, B - IY, ImageBuffer( gad\Icon ) ) And $FFFFFFFF
				
				cr = FUI_GetRed( RGB )
				cg = FUI_GetGreen( RGB )
				cb = FUI_GetBlue( RGB )
				
				If Not cr = 255 And cg = 0 And cb = 0
					WritePixelFast A, B, RGB
				EndIf
			Next
		Next
		UnlockBuffer ImageBuffer( gad\Icon )
		UnlockBuffer
	EndIf
	
	FUI_SetColor SC_GADGET_TEXT
	FUI_Text tex, gad\W / 2 + 1, gad\H / 2 + 1, gad\Caption, 1, 1
	
	;Disabled Image
	SetBuffer TextureBuffer( tex, 3 )
	
	If useSkin = Null
		FUI_SetColor SC_GADGET
		Rect 0, 0, gad\W, gad\H
		
		If (gad\Flags And CS_BORDER) = CS_BORDER
			FUI_AdjustColor-60
			Rect 0, 0, gad\W, gad\H, 0
		EndIf
	EndIf
	
	If gad\Icon <> Null
		IW = ImageWidth( gad\Icon )
		IH = ImageHeight( gad\Icon )
		
		LockBuffer ImageBuffer( gad\Icon )
		LockBuffer
		IX = gad\W / 2 - IW / 2
		IY = gad\H / 2 - IH / 2
		For A = IX To IX + IW - 1
			For B = IY To IY + IH - 1
				RGB = ReadPixelFast( A - IX, B - IY, ImageBuffer( gad\Icon ) ) And $FFFFFFFF
				
				cr = FUI_GetRed( RGB )
				cg = FUI_GetGreen( RGB )
				cb = FUI_GetBlue( RGB )
				
				FUI_SetColor SC_GADGET
				FUI_AdjustColor-60
				shadow = app\NB Or (app\NG Shl 8) Or (app\NR Shl 16) Or (255 Shl 24)
				
				If Not cr = 255 And cg = 0 And cb = 0
					WritePixelFast A, B, shadow
				EndIf
			Next
		Next
		UnlockBuffer ImageBuffer( gad\Icon )
		UnlockBuffer
	EndIf
	
	FUI_SetColor SC_GADGET
	FUI_AdjustColor-60
	FUI_Text tex, gad\W / 2, gad\H / 2, gad\Caption, 1, 1
	
	FreeTexture tex
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildCheckBox( gad.CheckBox )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	;Button
	SetFont app\fntGadget
	
	surf.BBSurface = GetSurface( gad\Mesh, 1 )
	ClearSurface surf
	
	gad\W = FontHeight() + 4 + StringWidth( gad\Caption )
	gad\H = FontHeight()
	
	FUI_SetColor SC_WHITE
	FUI_Rect 0, 0, gad\W, gad\H, surf
	
	useSkin.BBTexture = Null
	If SKIN_ENABLED = True
		useSkin = FUI_SkinCheckBox( gad )
	EndIf
	
	If useSkin = Null
		tex.BBTexture = FUI_CreateTexture( gad\W, gad\H, 1+2, 8 )
	Else
		tex = useSkin
	EndIf
	EntityTexture gad\Mesh, tex
	
	;Up - Unchecked
	SetBuffer TextureBuffer( tex, 0 )
	
	For A = 0 To 7
		SetBuffer TextureBuffer( tex, A )
		
		If useSkin = Null
			;CheckBox
			If A <> 3 And A <> 7
				FUI_SetColor SC_GADGET_COLOR
				Rect 0, 0, gad\H, gad\H
				
				FUI_SetColor SC_GADGET_BORDER
			Else
				FUI_SetColor SC_GADGET
				FUI_AdjustColor-60
			EndIf
			Rect 0, 0, gad\H, gad\H, 0
			
			If A = 1 Or A = 5
				FUI_SetColor SC_GADGET_COLOR
				FUI_AdjustColor-40
				Rect 1, 1, gad\H-2, gad\H-2, 0
				FUI_AdjustColor 40
				Rect 1, 1, gad\H-2, 1
				Rect 1, 1, 1, gad\H-2
			ElseIf A = 2 Or A = 6
				FUI_SetColor SC_GADGET_COLOR
				FUI_AdjustColor 40
				Rect 1, 1, gad\H-2, gad\H-2, 0
				FUI_AdjustColor-40
				Rect 1, 1, gad\H-2, 1
				Rect 1, 1, 1, gad\H-2
			EndIf
			
			If A > 3
				;Checked
				BX = 0
				BY = 0
				BW = gad\H
				BH = BW
				
				If A < 7
					FUI_SetColor SC_GADGET_COLOR_TEXT
				Else
					FUI_SetColor SC_GADGET
					FUI_AdjustColor-60
				EndIf
				Line BX + BW / 2 - 1, BY + BH / 2 + 2, BX + BW / 2 - 3, BY + BH / 2
				Line BX + BW / 2 - 1, BY + BH / 2 + 3, BX + BW / 2 - 3, BY + BH / 2 + 1
				Line BX + BW / 2 - 1, BY + BH / 2 + 2, BX + BW / 2 + 3, BY + BH / 2 - 2
				Line BX + BW / 2 - 1, BY + BH / 2 + 3, BX + BW / 2 + 3, BY + BH / 2 - 1
			EndIf
		EndIf
		
		;Text
		If A <> 3 And A <> 7
			FUI_SetColor SC_GADGET_TEXT
		Else
			FUI_SetColor SC_GADGET
			FUI_AdjustColor-60
		EndIf
		FUI_Text tex, gad\H + 4, 0, gad\Caption
	Next
	
	FreeTexture tex
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildComboBox( gad.ComboBox, Mode=0 )

	pBuffer.BBBuffer = GraphicsBuffer(  )

	SetFont app\fntGadget

	If Mode = 0 Or Mode = 2
		;ComboBox
		surf.BBSurface = GetSurface( gad\Mesh, 1 )
		ClearSurface surf

		FUI_SetGradientDir DIR_VERTICAL

		FUI_SetColor SC_WHITE
		FUI_AdjustColor 20
		FUI_AdjustColor 0, True

		FUI_Rect 0, 0, gad\W, gad\H, surf

		tex.BBTexture = FUI_CreateTexture( gad\W, gad\H, 1+2, 4 )
		EntityTexture gad\Mesh, tex

		;Button Dimensions
		BW = gad\H * 2 / 3
		BH = gad\H - 2
		BX = gad\W - BW - 1
		BY = 1

		;Up Image
		SetBuffer TextureBuffer( tex, 0 )

		FUI_SetColor SC_INPUT
		Rect 0, 0, gad\W, gad\H

		FUI_SetColor SC_INPUT_BORDER
		Rect 0, 0, gad\W, gad\H, 0
		Rect BX - 1, 0, 1, gad\H

		;Button
		FUI_SetColor SC_GADGET
		Rect BX, BY, BW, BH
		FUI_AdjustColor-60
		Rect BX, BY, BW, BH, 0
		FUI_AdjustColor 60
		Rect BX, BY, BW, 1
		Rect BX, BY, 1, BH

		FUI_SetColor SC_GADGET_TEXT
		Rect BX + BW / 2 - 2, BY + BH / 2 - 1, 5, 1
		Rect BX + BW / 2 - 1, BY + BH / 2, 3, 1
		Rect BX + BW / 2, BY + BH / 2 + 1, 1, 1

		;Over Image
		SetBuffer TextureBuffer( tex, 1 )

		FUI_SetColor SC_INPUT
		Rect 0, 0, gad\W, gad\H

		FUI_SetColor SC_INPUT_BORDER
		Rect 0, 0, gad\W, gad\H, 0
		Rect BX - 1, 0, 1, gad\H

		;Button
		FUI_SetColor SC_GADGET
		Rect BX, BY, BW, BH
		FUI_AdjustColor-60
		Rect BX, BY, BW, BH, 0
		FUI_AdjustColor 60
		Rect BX, BY, BW, 1
		Rect BX, BY, 1, BH

		FUI_SetColor SC_GADGET_TEXT
		Rect BX + BW / 2 - 2, BY + BH / 2 - 1, 5, 1
		Rect BX + BW / 2 - 1, BY + BH / 2, 3, 1
		Rect BX + BW / 2, BY + BH / 2 + 1, 1, 1
		
		;Down Image
		SetBuffer TextureBuffer( tex, 2 )
		
		FUI_SetColor SC_INPUT
		Rect 0, 0, gad\W, gad\H
		
		FUI_SetColor SC_INPUT_BORDER
		Rect 0, 0, gad\W, gad\H, 0
		Rect BX - 1, 0, 1, gad\H
		
		;Button
		FUI_SetColor SC_GADGET
		Rect BX, BY, BW, BH
		FUI_AdjustColor 60
		Rect BX, BY, BW, BH, 0
		FUI_AdjustColor-60
		Rect BX, BY, BW, 1
		Rect BX, BY, 1, BH
		
		FUI_SetColor SC_GADGET_TEXT
		Rect BX + BW / 2 - 2 + 1, BY + BH / 2 - 1 + 1, 5, 1
		Rect BX + BW / 2 - 1 + 1, BY + BH / 2 + 1, 3, 1
		Rect BX + BW / 2 + 1, BY + BH / 2 + 1 + 1, 1, 1
		
		;Disabled Image
		SetBuffer TextureBuffer( tex, 3 )
		
		FUI_SetColor SC_INPUT
		Rect 0, 0, gad\W, gad\H
		
		FUI_SetColor SC_INPUT_BORDER
		Rect 0, 0, gad\W, gad\H, 0
		Rect BX - 1, 0, 1, gad\H
		
		;Button
		FUI_SetColor SC_GADGET
		Rect BX, BY, BW, BH
		FUI_AdjustColor-60
		Rect BX, BY, BW, BH, 0
		FUI_AdjustColor 60
		Rect BX, BY, BW, 1
		Rect BX, BY, 1, BH
		
		FUI_SetColor SC_GADGET
		FUI_AdjustColor 60
		Rect BX + BW / 2 - 2 + 1, BY + BH / 2 - 1 + 1, 5, 1
		Rect BX + BW / 2 - 1 + 1, BY + BH / 2 + 1, 3, 1
		Rect BX + BW / 2 + 1, BY + BH / 2 + 1 + 1, 1, 1
		FUI_AdjustColor-60
		Rect BX + BW / 2 - 2, BY + BH / 2 - 1, 5, 1
		Rect BX + BW / 2 - 1, BY + BH / 2, 3, 1
		Rect BX + BW / 2, BY + BH / 2 + 1, 1, 1
		
		;Caption
		If gad\actItem <> Null
			For A = 0 To 3
				SetBuffer TextureBuffer( tex, A )
				
				If A < 3
					FUI_SetColor SC_INPUT_TEXT
				Else
					FUI_SetColor SC_INPUT
					FUI_AdjustColor-60
				EndIf
				FUI_Text tex, 4, gad\H / 2, gad\actItem\Caption, 0, 1
			Next
		EndIf
		
		FreeTexture tex
	EndIf
	
	If Mode = 1 Or Mode = 2
		;DropDown
		surf = GetSurface( gad\DropDown, 1 )
		ClearSurface surf
		
		FUI_SetGradientDir DIR_VERTICAL
		
		If COMBOBOX_STRIP = True
			FUI_SetColor SC_INPUT
			FUI_Rect 1 + COMBOBOX_STRIP_W, 1, gad\DW - 2 - COMBOBOX_STRIP_W, gad\DH + COMBOBOX_PADDING*2 - 2, surf
			
			FUI_SetColor SC_INPUT_COLOR
			FUI_Rect 1, 1, COMBOBOX_STRIP_W, gad\DH + COMBOBOX_PADDING*2 - 2, surf
		Else
			FUI_SetColor SC_INPUT
			FUI_Rect 1, 1, gad\DW - 2, gad\DH + COMBOBOX_PADDING*2 - 2, surf
		EndIf
		
		FUI_SetColor SC_INPUT_BORDER
		FUI_Rect 0, 0, gad\DW, gad\DH + COMBOBOX_PADDING*2, surf, 0
		
		FUI_SetChildPosition gad\VScroll\Mesh, gad\DW - 1 - gad\VScroll\H, 1
	EndIf
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildComboBoxItem( gad.ComboBoxItem )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	;ComboBox Item
	surf.BBSurface = GetSurface( gad\Mesh, 1 )
	ClearSurface surf
	
	FUI_SetGradientDir DIR_VERTICAL
	
	SetFont app\fntGadget
	
	If gad\Caption = "-"
		H = COMBOBOXITEM_HEIGHT / 2
		
		FUI_SetColor SC_INPUT
		FUI_AdjustColor-60
		FUI_Rect 4, H / 2, gad\Owner\DW - COMBOBOX_PADDING*2 - 8, 1, surf
	Else
		H = COMBOBOXITEM_HEIGHT
		
		FUI_SetColor SC_WHITE
		FUI_AdjustColor 20
		FUI_AdjustColor 0, True
		
		FUI_Rect 0, 0, gad\Owner\DW - COMBOBOX_PADDING*2, H, surf
		
		tex.BBTexture = FUI_CreateTexture( gad\Owner\DW - 2, H, 1+2, 4 )
		EntityTexture gad\Mesh, tex
		
		;Up Image
		SetBuffer TextureBuffer( tex, 0 )
		
		FUI_SetColor SC_INPUT_TEXT
		FUI_Text tex, COMBOBOX_STRIP*COMBOBOX_STRIP_W + 4, H / 2, gad\Caption, 0, 1
		
		;Over Image
		SetBuffer TextureBuffer( tex, 1 )
		
		FUI_SetColor SC_INPUT_COLOR
		Rect 0, 0, gad\Owner\DW - COMBOBOX_PADDING*2, H
		
		FUI_SetColor SC_INPUT_COLOR_TEXT
		FUI_Text tex, COMBOBOX_STRIP*COMBOBOX_STRIP_W + 4, H / 2, gad\Caption, 0, 1
		
		;Down Image
		SetBuffer TextureBuffer( tex, 2 )
		
		FUI_SetColor SC_INPUT_COLOR
		Rect 0, 0, gad\Owner\DW - COMBOBOX_PADDING*2, H
		
		FUI_SetColor SC_INPUT_COLOR_TEXT
		FUI_Text tex, COMBOBOX_STRIP*COMBOBOX_STRIP_W + 4, H / 2, gad\Caption, 0, 1
		
		;Disabled Image
		SetBuffer TextureBuffer( tex, 3 )
		
		FUI_SetColor SC_INPUT
		Rect 0, 0, gad\Owner\DW - COMBOBOX_PADDING*2, H
		
		FUI_AdjustColor-60
		FUI_Text tex, COMBOBOX_STRIP*COMBOBOX_STRIP_W + 4, H / 2, gad\Caption, 0, 1
		
		FreeTexture tex
	EndIf
	
	;Initial Build
	If gad\Icon <> Null
		
		tex = FUI_GadgetTexture( gad\Mesh )
		
		IW = ImageWidth( gad\Icon )
		IH = ImageHeight( gad\Icon )
		IX = COMBOBOX_STRIP_W / 2 - IW / 2
		IY = COMBOBOXITEM_HEIGHT / 2 - IH / 2
		LockBuffer ImageBuffer( gad\Icon )
		
		For A = 0 To 4
			SetBuffer TextureBuffer( tex, A )
			LockBuffer
			
			For X = IX To IX + IW - 1
				For Y = IY To IY + IH - 1
					RGB = ReadPixelFast( X - IX, Y - IY, ImageBuffer( gad\Icon ) ) And $FFFFFFFF
					
					cr = FUI_GetRed( RGB )
					cg = FUI_GetGreen( RGB )
					cb = FUI_GetBlue( RGB )
					
					If A = 1
						FUI_SetColor SC_COMBOBOXITEM_OVER
						FUI_AdjustColor-60
					ElseIf A = 2
						FUI_SetColor SC_COMBOBOXITEM_SEL
						FUI_AdjustColor-60
					EndIf
					shadow = app\NB Or (app\NG Shl 8) Or (app\NR Shl 16) Or (255 Shl 24)
					
					If Not cr = FUI_GetRed( SC_MASK ) And cg = FUI_GetGreen( SC_MASK ) And cb = FUI_GetBlue( SC_MASK )
						If A = 0
							If X >= 0 And Y >= 0 And X < TextureWidth( tex ) And Y < TextureHeight( tex )
								WritePixelFast X, Y, RGB
							EndIf
						ElseIf A = 3
							If X >= 0 And Y >= 0 And X < TextureWidth( tex ) And Y < TextureHeight( tex )
								WritePixelFast X, Y, shadow
							EndIf
						Else
							If X >= 0 And Y >= 0 And X < TextureWidth( tex ) And Y < TextureHeight( tex )
								WritePixelFast X, Y, shadow
							EndIf
							If X-2 >= 0 And Y-2 >= 0 And X-2 < TextureWidth( tex ) And Y-2 < TextureHeight( tex )
								WritePixelFast X-2, Y-2, RGB
							EndIf
						EndIf
					EndIf
				Next
			Next
			UnlockBuffer
		Next
		UnlockBuffer ImageBuffer( gad\Icon )
	EndIf
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildGroupBox( gad.GroupBox )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	;Group Box
	SetFont app\fntGadget
	
	FH = FontHeight() / 2
	
	surf.BBSurface = GetSurface( gad\Mesh, 1 )
	ClearSurface surf
	
	If gad\W <= 0 Or gad\H < 0
		Return
	EndIf
	
	FUI_SetGradientDir DIR_VERTICAL
	
	FUI_SetColor SC_GADGET
	FUI_AdjustColor 60
	If gad\Caption > ""
		FUI_Rect 0+1, FH+1, 4, 1, surf
		FUI_Rect 12 + StringWidth( gad\Caption )+1, FH+1, gad\W - StringWidth( gad\Caption ) - 12, 1, surf
	Else
		FUI_Rect 0+1, FH+1, gad\W, 1, surf
	EndIf
	If gad\H > 0
		FUI_Rect 0+1, FH + 1+1, 1, gad\H - FH - 1, surf
		FUI_Rect gad\W - 1+1, FH + 1+1, 1, gad\H - FH - 1, surf
		FUI_Rect 0+1, gad\H - 1+1, gad\W, 1, surf
	EndIf
	
	FUI_AdjustColor-60
	If gad\Caption > ""
		FUI_Rect 0, FH, 4, 1, surf
		FUI_Rect 12 + StringWidth( gad\Caption ), FH, gad\W - StringWidth( gad\Caption ) - 12, 1, surf
	Else
		FUI_Rect 0, FH, gad\W, 1, surf
	EndIf
	If gad\H > 0
		FUI_Rect 0, FH + 1, 1, gad\H - FH - 1, surf
		FUI_Rect gad\W - 1, FH + 1, 1, gad\H - FH - 1, surf
		FUI_Rect 0, gad\H - 1, gad\W, 1, surf
	EndIf
	
	surf = GetSurface( gad\MeshText, 1 )
	ClearSurface surf
	
	FUI_SetColor SC_WHITE
	FUI_Rect 0, 0, StringWidth( gad\Caption ), FontHeight(  ), surf
	
	tex.BBTexture = FUI_CreateTexture( StringWidth( gad\Caption ), FontHeight(  ), 1+2 )
	EntityTexture gad\MeshText, tex
	
	SetBuffer TextureBuffer( tex )
	
	FUI_SetColor SC_GADGET_TEXT
	FUI_Text tex, 0, 0, gad\Caption
	
	FreeTexture tex
	
	SetBuffer pBuffer
	
	gad\FW = gad\W
	gad\FH = gad\H
	
End Function

Function FUI_BuildImageBox( gad.ImageBox )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	;Image Box
	surf.BBSurface = GetSurface( gad\Mesh, 1 )
	ClearSurface surf
	
	If gad\W <= 0 Or gad\H <= 0
		Return
	EndIf
	
	FUI_SetColor SC_WHITE
	FUI_Rect 0, 0, gad\W, gad\H, surf
	
	tex.BBTexture = FUI_CreateTexture( gad\W, gad\H, 1 )
	EntityTexture gad\Mesh, tex
	
	SetBuffer TextureBuffer( tex )
	
	If gad\Image <> Null
		IW = ImageWidth( gad\Image )
		IH = ImageHeight( gad\Image )
		
		If IW > gad\W
			IW = gad\W
		EndIf
		If IH > gad\H
			IH = gad\H
		EndIf
		
		LockBuffer
		LockBuffer ImageBuffer( gad\Image )
		For A = 0 To ImageWidth( gad\Image ) - 1
			For B = 0 To ImageHeight( gad\Image ) - 1
				RGB = ReadPixelFast( A, B, ImageBuffer( gad\Image ) ) And $FFFFFFFF
				WritePixelFast A, B, RGB
			Next
		Next
		UnlockBuffer
		UnlockBuffer ImageBuffer( gad\Image )
	EndIf
	
	If (gad\Flags And CS_BORDER) = CS_BORDER
		FUI_SetColor SC_GADGET_BORDER
		Rect 0, 0, gad\W, gad\H, 0
	EndIf
	
	FreeTexture tex
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildLabel( gad.Label )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	;Label
	SetFont app\fntGadget
	
	surf.BBSurface = GetSurface( gad\Mesh, 1 )
	ClearSurface surf
	
	NumLines = FUI_CountItems( gad\Caption, Chr(10) )
	
	gad\W = 0
	gad\H = NumLines * FontHeight()
	
	For A = 0 To NumLines - 1
		SW = StringWidth( FUI_Parse( gad\Caption, A, Chr(10) ) )
		
		If SW > gad\W
			gad\W = SW
		EndIf
	Next
	
	If gad\W <= 0 Or gad\H <= 0
		Return
	EndIf
	
	FUI_SetColor SC_WHITE
	FUI_Rect 0, 0, gad\W, gad\H, surf
	
	tex.BBTexture = FUI_CreateTexture( gad\W, gad\H, 1+2 )
	EntityTexture gad\Mesh, tex
	
	SetBuffer TextureBuffer( tex )
	
	FUI_SetColor SC_GADGET_TEXT
	
	For A = 0 To NumLines - 1
		If gad\Align = ALIGN_RIGHT
			FUI_Text tex, gad\W - StringWidth( FUI_Parse( gad\Caption, A, Chr(10) ) ), A * FontHeight(), FUI_Parse( gad\Caption, A, Chr(10) )
		ElseIf gad\Align = ALIGN_CENTER
			FUI_Text tex, gad\W / 2, A * FontHeight(), FUI_Parse( gad\Caption, A, Chr(10) ), 1
		Else
			FUI_Text tex, 0, A * FontHeight(), FUI_Parse( gad\Caption, A, Chr(10) )
		EndIf
	Next
	
	If gad\Align = ALIGN_RIGHT
		PositionMesh gad\Mesh,-gad\W, 0.0, 0.0
	ElseIf gad\Align = ALIGN_CENTER
		PositionMesh gad\Mesh,-gad\W/2, 0.0, 0.0
	EndIf
	
	FreeTexture tex
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildListBox( gad.ListBox )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	;List Box
	surf.BBSurface = GetSurface( gad\Mesh, 1 )
	ClearSurface surf
	
	If gad\W <= 0 Or gad\H <= 0
		Return
	EndIf
	
	FUI_SetColor SC_INPUT
	FUI_Rect 1, 1, gad\W-2, gad\H-2, surf
	FUI_SetColor SC_INPUT_BORDER
	FUI_Rect 0, 0, gad\W, gad\H, surf, 0
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildListBoxItem( gad.ListBoxItem )

	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	;List Box Item
	surf.BBSurface = GetSurface( gad\Mesh, 1 )
	ClearSurface surf
	
	W = gad\Owner\W - LISTBOX_PADDING*2
	
	FUI_SetColor SC_WHITE
	FUI_Rect 0, 0, W, LISTBOXITEM_HEIGHT, surf
	
;	tex = FUI_CreateTexture( W, LISTBOXITEM_HEIGHT, 1+2, 8 )
	tex.BBTexture = FUI_CreateTexture( W, LISTBOXITEM_HEIGHT, 1+2, 4 )
	EntityTexture gad\Mesh, tex, gad\Active*2
	
	;Over Image
	SetBuffer TextureBuffer( tex, 1 )
	
	FUI_SetColor SC_INPUT_COLOR
	Rect 0, 0, W, LISTBOXITEM_HEIGHT
	
	;Down Image
	SetBuffer TextureBuffer( tex, 2 )
	
	FUI_SetColor SC_INPUT_COLOR
	Rect 0, 0, W, LISTBOXITEM_HEIGHT
	
	;Text
;	For A = 0 To 7
	For A = 0 To 3
		SetBuffer TextureBuffer( tex, A )
		
		If A = 0 Or A = 3; Or A = 4 Or A = 7
			FUI_SetColor SC_INPUT_TEXT
		Else
			FUI_SetColor SC_INPUT_COLOR_TEXT
		EndIf
		FUI_Text tex, LISTBOX_STRIP_W + 4, LISTBOXITEM_HEIGHT / 2, gad\Caption, 0, 1
	Next
	
	;Initial Build
	If gad\Icon <> Null
		tex = FUI_GadgetTexture( gad\Mesh )
		
		IW = ImageWidth( gad\Icon )
		IH = ImageHeight( gad\Icon )
		IX = LISTBOX_STRIP_W / 2 - IW / 2
		IY = LISTBOXITEM_HEIGHT / 2 - IH / 2
		LockBuffer ImageBuffer( gad\Icon )
		
;		For A = 0 To 7
		For A = 0 To 3
			SetBuffer TextureBuffer( tex, A )
			LockBuffer
			
			For X = IX To IX + IW - 1
				For Y = IY To IY + IH - 1
					RGB = ReadPixelFast( X - IX, Y - IY, ImageBuffer( gad\Icon ) ) And $FFFFFFFF
					
					cr = FUI_GetRed( RGB )
					cg = FUI_GetGreen( RGB )
					cb = FUI_GetBlue( RGB )
					
					If A = 1; Or A = 5
						FUI_SetColor SC_LISTBOXITEM_OVER
						FUI_AdjustColor-60
					ElseIf A = 2; Or A = 6
						FUI_SetColor SC_LISTBOXITEM_SEL
						FUI_AdjustColor-60
					EndIf
					shadow = app\NB Or (app\NG Shl 8) Or (app\NR Shl 16) Or (255 Shl 24)
					
					If Not cr = FUI_GetRed( SC_MASK ) And cg = FUI_GetGreen( SC_MASK ) And cb = FUI_GetBlue( SC_MASK )
						If A = 0; Or A = 4
							If X >= 0 And Y >= 0 And X < TextureWidth( tex ) And Y < TextureHeight( tex )
								WritePixelFast X, Y, RGB
							EndIf
						ElseIf A = 3; Or A = 7
							If X >= 0 And Y >= 0 And X < TextureWidth( tex ) And Y < TextureHeight( tex )
								WritePixelFast X, Y, shadow
							EndIf
						Else
							If X >= 0 And Y >= 0 And X < TextureWidth( tex ) And Y < TextureHeight( tex )
								WritePixelFast X, Y, shadow
							EndIf
							If X-2 >= 0 And Y-2 >= 0 And X-2 < TextureWidth( tex ) And Y-2 < TextureHeight( tex )
								WritePixelFast X-2, Y-2, RGB
							EndIf
						EndIf
					EndIf
				Next
			Next
			UnlockBuffer
		Next
		UnlockBuffer ImageBuffer( gad\Icon )
	EndIf
	
	FreeTexture tex
	
	SetBuffer pBuffer

End Function

Function FUI_BuildProgressBar( gad.ProgressBar )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	SetFont app\fntGadget
	
	;Progress Bar
	surf.BBSurface = GetSurface( gad\Mesh, 1 )
	ClearSurface surf
	
	If gad\W <= 0 Or gad\H <= 0
		Return
	EndIf
	
	FUI_SetColor SC_GADGET_BORDER
	FUI_Rect 0, 0, gad\W, gad\H, surf, 0
	
	;Bar
	surf = GetSurface( gad\Bar, 1 )
	ClearSurface surf
	
	FUI_SetColor SC_GADGET_COLOR
	FUI_Rect 0, 0, FUI_Interpolate( gad\Value, gad\Min, gad\Max, 0, gad\W - 2 ), gad\H - 2, surf
	
	;Text
	surf = GetSurface( gad\MeshText, 1 )
	ClearSurface surf
	
	FUI_SetColor SC_WHITE
	FUI_Rect 0, 0, StringWidth( gad\Value + "%" ), FontHeight(), surf
	
	tex.BBTexture = FUI_CreateTexture( StringWidth( FUI_Interpolate( gad\Value, gad\Min, gad\Max, 0, 100 ) + "%" ), FontHeight(), 1+2 )
	EntityTexture gad\MeshText, tex
	
	SetBuffer TextureBuffer( tex )
	
	FUI_SetColor SC_GADGET_TEXT
	FUI_Text tex, 0, 0, Int( FUI_Interpolate( gad\Value, gad\Min, gad\Max, 0, 100 ) ) + "%"
	
	FreeTexture tex
	
	FUI_SetChildPosition gad\MeshText, gad\W / 2 - StringWidth( FUI_Interpolate( gad\Value, gad\Min, gad\Max, 0, 100 ) ) / 2, gad\H / 2 - FontHeight() / 2
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildRadio( gad.Radio )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	;Radio
	SetFont app\fntGadget
	
	surf.BBSurface = GetSurface( gad\Mesh, 1 )
	ClearSurface surf
	
	gad\H = FontHeight()
	gad\W = gad\H + 4 + StringWidth( gad\Caption )
	
	If gad\W <= 0 Or gad\H <= 0
		Return
	EndIf
	
	FUI_SetColor SC_WHITE
	FUI_Rect 0, 0, gad\W, gad\H, surf
	
	tex.BBTexture = FUI_CreateTexture( gad\W, gad\H, 1+2, 8 )
	EntityTexture gad\Mesh, tex
	
	For A = 0 To 7
		SetBuffer TextureBuffer( tex, A )
		
		FUI_SetColor SC_GADGET_COLOR
		Rect 0, 0, gad\H, gad\H
		FUI_SetColor SC_GADGET_BORDER
		Rect 0, 0, gad\H, gad\H, 0
		
		If A = 1 Or A = 5
			FUI_SetColor SC_GADGET_COLOR
			FUI_AdjustColor-40
			Rect 1, 1, gad\H-2, gad\H-2, 0
			FUI_AdjustColor 40
			Rect 1, 1, gad\H-2, 1
			Rect 1, 1, 1, gad\H-2
		ElseIf A = 2 Or A = 6
			FUI_SetColor SC_GADGET_COLOR
			FUI_AdjustColor 40
			Rect 1, 1, gad\H-2, gad\H-2, 0
			FUI_AdjustColor-40
			Rect 1, 1, gad\H-2, 1
			Rect 1, 1, 1, gad\H-2
		EndIf
		
		If A > 3
			BX = 0
			BY = 0
			BW = gad\H
			BH = BW
			
			;Tick Box
			FUI_SetColor SC_GADGET_TEXT
			Line BX + BW / 2 - 1, BY + BH / 2 + 2, BX + BW / 2 - 3, BY + BH / 2
			Line BX + BW / 2 - 1, BY + BH / 2 + 3, BX + BW / 2 - 3, BY + BH / 2 + 1
			Line BX + BW / 2 - 1, BY + BH / 2 + 2, BX + BW / 2 + 3, BY + BH / 2 - 2
			Line BX + BW / 2 - 1, BY + BH / 2 + 3, BX + BW / 2 + 3, BY + BH / 2 - 1
		EndIf
		
		If A = 3 Or A = 7
			FUI_SetColor SC_GADGET
			FUI_AdjustColor-60
		Else
			FUI_SetColor SC_GADGET_TEXT
		EndIf
		FUI_Text tex, gad\H + 4, 0, gad\Caption
	Next
	
	FreeTexture tex
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildScrollBar( gad.ScrollBar )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	;Label
	SetFont app\fntGadget
	
	surf.BBSurface = GetSurface( gad\Mesh, 1 )
	ClearSurface surf
	
	If gad\W <= 0 Or gad\H <= 0
		Return
	EndIf
	
	If gad\OSW > gad\W - gad\H*2
		gad\SW = (gad\W - gad\H*2) / 2
	Else
		gad\SW = gad\OSW
	EndIf
	
	If gad\Direction = DIR_VERTICAL
		W = gad\H
		H = gad\W
		
		SW = gad\SH
		SH = gad\SW
	Else
		W = gad\W
		H = gad\H
		
		SW = gad\SW
		SH = gad\SH
	EndIf
	
	BW = gad\H - 2
	BH = BW
	
	If gad\Direction = DIR_VERTICAL
		FUI_SetColor SC_GADGET
		FUI_Rect 1, 1 + BH + 1, W - 2, H - 4 - BH*2, surf
		
		FUI_SetColor SC_GADGET_BORDER
		FUI_Rect 0, 0, W, H, surf, 0
		FUI_Rect 1, 1 + BH, W - 2, 1, surf
		FUI_Rect 1, H - 1 - BH - 1, W - 2, 1, surf
	Else
		FUI_SetColor SC_GADGET
		FUI_Rect 1 + BW + 1, 1, W - 4 - BW*2, H - 2, surf
		
		FUI_SetColor SC_GADGET_BORDER
		FUI_Rect 0, 0, W, H, surf, 0
		FUI_Rect 1 + BW, 1, 1, H - 2, surf
		FUI_Rect W - 1 - BW - 1, 1, 1, H - 2, surf
	EndIf
	
	;Button One
	surf = GetSurface( gad\Btn1, 1 )
	ClearSurface surf
	
	FUI_SetColor SC_WHITE
	FUI_Rect 0, 0, BW, BH, surf
	
	tex.BBTexture = FUI_CreateTexture( BW, BH, 1+2, 4 )
	EntityTexture gad\Btn1, tex
	
	;Up Image
	SetBuffer TextureBuffer( tex, 0 )
	
	FUI_SetColor SC_GADGET
	Rect 0, 0, BW, BH
	FUI_AdjustColor-40
	Rect 0, 0, BW, BH, 0
	FUI_AdjustColor 40
	Rect 0, 0, BW, 1
	Rect 0, 0, 1, BH
	
	;Over Image
	SetBuffer TextureBuffer( tex, 1 )
	
	FUI_SetColor SC_GADGET
	Rect 0, 0, BW, BH
	FUI_AdjustColor-40
	Rect 0, 0, BW, BH, 0
	FUI_AdjustColor 40
	Rect 0, 0, BW, 1
	Rect 0, 0, 1, BH
	
	;Down Image
	SetBuffer TextureBuffer( tex, 2 )
	
	FUI_SetColor SC_GADGET
	Rect 0, 0, BW, BH
	FUI_AdjustColor 40
	Rect 0, 0, BW, BH, 0
	FUI_AdjustColor-40
	Rect 0, 0, BW, 1
	Rect 0, 0, 1, BH
	
	;Disabled Image
	SetBuffer TextureBuffer( tex, 3 )
	
	FUI_SetColor SC_GADGET
	Rect 0, 0, BW, BH
	FUI_AdjustColor-40
	Rect 0, 0, BW, BH, 0
	FUI_AdjustColor 40
	Rect 0, 0, BW, 1
	Rect 0, 0, 1, BH
	
	For A = 0 To 3
		SetBuffer TextureBuffer( tex, A )
		
		FUI_SetColor SC_GADGET_TEXT
		If A = 2
			Origin 1, 1
		ElseIf A = 3
			Origin 1, 1
			FUI_SetColor SC_GADGET
			FUI_AdjustColor 60
		EndIf
		
		If gad\Direction = DIR_VERTICAL
			Rect BW / 2 - 2, BH / 2 + 1, 5, 1
			Rect BW / 2 - 1, BH / 2, 3, 1
			Rect BW / 2, BH / 2 - 1, 1, 1
		Else
			Rect BW / 2 + 1, BH / 2 - 2, 1, 5
			Rect BW / 2, BH / 2 - 1, 1, 3
			Rect BW / 2 - 1, BH / 2, 1, 1
		EndIf
		
		Origin 0, 0
		If A = 3
			FUI_AdjustColor-60
			If gad\Direction = DIR_VERTICAL
				Rect BW / 2 - 2, BH / 2 + 1, 5, 1
				Rect BW / 2 - 1, BH / 2, 3, 1
				Rect BW / 2, BH / 2 - 1, 1, 1
			Else
				Rect BW / 2 + 1, BH / 2 - 2, 1, 5
				Rect BW / 2, BH / 2 - 1, 1, 3
				Rect BW / 2 - 1, BH / 2, 1, 1
			EndIf
		EndIf
	Next
	
	FreeTexture tex
	
	;Button Two
	surf = GetSurface( gad\Btn2, 1 )
	ClearSurface surf
	
	FUI_SetColor SC_WHITE
	FUI_Rect 0, 0, BW, BH, surf
	
	tex = FUI_CreateTexture( BW, BH, 1+2, 4 )
	EntityTexture gad\Btn2, tex
	
	;Up Image
	SetBuffer TextureBuffer( tex, 0 )
	
	FUI_SetColor SC_GADGET
	Rect 0, 0, BW, BH
	FUI_AdjustColor-40
	Rect 0, 0, BW, BH, 0
	FUI_AdjustColor 40
	Rect 0, 0, BW, 1
	Rect 0, 0, 1, BH
	
	;Over Image
	SetBuffer TextureBuffer( tex, 1 )
	
	FUI_SetColor SC_GADGET
	Rect 0, 0, BW, BH
	FUI_AdjustColor-40
	Rect 0, 0, BW, BH, 0
	FUI_AdjustColor 40
	Rect 0, 0, BW, 1
	Rect 0, 0, 1, BH
	
	;Down Image
	SetBuffer TextureBuffer( tex, 2 )
	
	FUI_SetColor SC_GADGET
	Rect 0, 0, BW, BH
	FUI_AdjustColor 40
	Rect 0, 0, BW, BH, 0
	FUI_AdjustColor-40
	Rect 0, 0, BW, 1
	Rect 0, 0, 1, BH
	
	;Disabled Image
	SetBuffer TextureBuffer( tex, 3 )
	
	FUI_SetColor SC_GADGET
	Rect 0, 0, BW, BH
	FUI_AdjustColor-40
	Rect 0, 0, BW, BH, 0
	FUI_AdjustColor 40
	Rect 0, 0, BW, 1
	Rect 0, 0, 1, BH
	
	For A = 0 To 3
		SetBuffer TextureBuffer( tex, A )
		
		FUI_SetColor SC_GADGET_TEXT
		If A = 2
			Origin 1, 1
		ElseIf A = 3
			Origin 1, 1
			FUI_SetColor SC_GADGET
			FUI_AdjustColor 60
		EndIf
		
		If gad\Direction = DIR_VERTICAL
			Rect BW / 2 - 2, BH / 2 - 1, 5, 1
			Rect BW / 2 - 1, BH / 2, 3, 1
			Rect BW / 2, BH / 2 + 1, 1, 1
		Else
			Rect BW / 2 - 1, BH / 2 - 2, 1, 5
			Rect BW / 2, BH / 2 - 1, 1, 3
			Rect BW / 2 + 1, BH / 2, 1, 1
		EndIf
		
		Origin 0, 0
		If A = 3
			FUI_AdjustColor-60
			If gad\Direction = DIR_VERTICAL
				Rect BW / 2 - 2, BH / 2 - 1, 5, 1
				Rect BW / 2 - 1, BH / 2, 3, 1
				Rect BW / 2, BH / 2 + 1, 1, 1
			Else
				Rect BW / 2 - 1, BH / 2 - 2, 1, 5
				Rect BW / 2, BH / 2 - 1, 1, 3
				Rect BW / 2 + 1, BH / 2, 1, 1
			EndIf
		EndIf
	Next
	
	FreeTexture tex
	
	;Scroll Bar
	surf = GetSurface( gad\ScrollBar, 1 )
	ClearSurface surf
	
	FUI_SetColor SC_GADGET
	FUI_Rect 1, 1, SW-2, SH-2, surf
	FUI_AdjustColor 40
	FUI_Rect 0, 0, SW, 1, surf
	FUI_Rect 0, 1, 1, SH - 2, surf
	FUI_AdjustColor-40
	FUI_Rect 0, SH - 1, SW, 1, surf
	FUI_Rect SW - 1, 1, 1, SH - 2, surf
	
	If gad\Direction = DIR_VERTICAL
		FUI_SetChildPosition gad\Btn2, 1, gad\W - (gad\H-1)
	Else
		FUI_SetChildPosition gad\Btn2, gad\W - (gad\H-1), 1
	EndIf
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildSlider( gad.Slider )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	;Slider
	SetFont app\fntGadget
	
	surf.BBSurface = GetSurface( gad\Mesh, 1 )
	ClearSurface surf
	
	If gad\W <= 0 Or gad\H <= 0
		Return
	EndIf
	
	If gad\Direction = DIR_VERTICAL
		W = gad\H
		H = gad\W
		
		SW = W - 2
		SH = gad\SW
	Else
		W = gad\W
		H = gad\H
		
		SW = gad\SW
		SH = H - 2
	EndIf
	
	FUI_SetColor SC_GADGET
	FUI_Rect 1, 1, W-2, H-2, surf
	FUI_SetColor SC_GADGET_BORDER
	FUI_Rect 0, 0, W, H, surf, 0
	
	;Slider
	surf = GetSurface( gad\ScrollBar, 1 )
	ClearSurface surf
	
	FUI_SetColor SC_GADGET_COLOR
	FUI_Rect 1, 1, SW-2, SH-2, surf
	FUI_AdjustColor 40
	FUI_Rect 0, 0, SW, 1, surf
	FUI_Rect 0, 1, 1, SH-1, surf
	FUI_AdjustColor-40
	FUI_Rect 0, SH-1, SW, 1, surf
	FUI_Rect SW-1, 1, 1, SH-2, surf
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildSpinner( gad.Spinner, Mode = 0 )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	SetFont app\fntGadget
	
	BW = gad\H - 1
	BH = (gad\H-2) / 2
	
	If Mode = 0
		;Spinner
		surf.BBSurface = GetSurface( gad\Mesh, 1 )
		ClearSurface surf
		
		If gad\W <= 0 Or gad\H <= 0
			Return
		EndIf
		
		FUI_SetColor SC_INPUT
		FUI_Rect 1, 1, gad\W - 2 - BW - 1, gad\H - 2, surf
		FUI_SetColor SC_INPUT_BORDER
		FUI_Rect 0, 0, gad\W, gad\H, surf, 0
		FUI_Rect gad\W - 1 - BW - 1, 1, 1, gad\H - 2, surf
		
		;Caret
		surf = GetSurface( gad\Caret, 1 )
		ClearSurface surf
		
		FUI_SetColor SC_INPUT_TEXT
		FUI_Rect 0, 0, 1, FontHeight(), surf
	EndIf
	
	;Text
	surf = GetSurface( gad\MeshText, 1 )
	ClearSurface surf
	
	FUI_SetColor SC_WHITE
	FUI_Rect 0, 0, gad\W - 2 - BW - 1, gad\H-2, surf
	
	tex.BBTexture = FUI_CreateTexture( gad\W - 2 - BW - 1, gad\H-2, 1+2 )
	EntityTexture gad\MeshText, tex
	
	SetBuffer TextureBuffer( tex )
	
	FUI_SetColor SC_INPUT_TEXT
	FUI_Text tex, 4, (gad\H-2)/2, gad\Caption, 0, 1
	
	FreeTexture tex
	
	If Mode = 0
		;Button One
		surf = GetSurface( gad\Btn1, 1 )
		ClearSurface surf
		
		FUI_SetColor SC_WHITE
		FUI_Rect 0, 0, BW, BH, surf
		
		tex = FUI_CreateTexture( BW, BH, 1+2, 4 )
		EntityTexture gad\Btn1, tex
		
		;Up Image
		SetBuffer TextureBuffer( tex, 0 )
		
		FUI_SetColor SC_GADGET
		Rect 0, 0, BW, BH
		FUI_AdjustColor-40
		Rect 0, 0, BW, BH, 0
		FUI_AdjustColor 40
		Rect 0, 0, BW, 1
		Rect 0, 0, 1, BH
		
		;Over Image
		SetBuffer TextureBuffer( tex, 1 )
		
		FUI_SetColor SC_GADGET
		Rect 0, 0, BW, BH
		FUI_AdjustColor-40
		Rect 0, 0, BW, BH, 0
		FUI_AdjustColor 40
		Rect 0, 0, BW, 1
		Rect 0, 0, 1, BH
		
		;Down Image
		SetBuffer TextureBuffer( tex, 2 )
		
		FUI_SetColor SC_GADGET
		Rect 0, 0, BW, BH
		FUI_AdjustColor 40
		Rect 0, 0, BW, BH, 0
		FUI_AdjustColor-40
		Rect 0, 0, BW, 1
		Rect 0, 0, 1, BH
		
		;Disabled Image
		SetBuffer TextureBuffer( tex, 3 )
		
		FUI_SetColor SC_GADGET
		Rect 0, 0, BW, BH
		FUI_AdjustColor-40
		Rect 0, 0, BW, BH, 0
		FUI_AdjustColor 40
		Rect 0, 0, BW, 1
		Rect 0, 0, 1, BH
		
		For A = 0 To 3
			SetBuffer TextureBuffer( tex, A )
			
			If A = 3
				FUI_SetColor SC_GADGET
				FUI_AdjustColor 60
			Else
				FUI_SetColor SC_GADGET_TEXT
			EndIf
			
			If A >= 2
				Origin 1, 1
			Else
				Origin 0, 0
			EndIf
			
			Rect BW / 2 - 2, BH / 2 + 1, 5, 1
			Rect BW / 2 - 1, BH / 2, 3, 1
			Rect BW / 2, BH / 2 - 1, 1, 1
			
			Origin 0, 0
			
			If A = 3
				FUI_AdjustColor-60
				Rect BW / 2 - 2, BH / 2 + 1, 5, 1
				Rect BW / 2 - 1, BH / 2, 3, 1
				Rect BW / 2, BH / 2 - 1, 1, 1
			EndIf
		Next
		
		FreeTexture tex
		
		;Button Two
		surf = GetSurface( gad\Btn2, 1 )
		ClearSurface surf
		
		FUI_SetColor SC_WHITE
		FUI_Rect 0, 0, BW, BH, surf
		
		tex = FUI_CreateTexture( BW, BH, 1+2, 4 )
		EntityTexture gad\Btn2, tex
		
		;Up Image
		SetBuffer TextureBuffer( tex, 0 )
		
		FUI_SetColor SC_GADGET
		Rect 0, 0, BW, BH
		FUI_AdjustColor-40
		Rect 0, 0, BW, BH, 0
		FUI_AdjustColor 40
		Rect 0, 0, BW, 1
		Rect 0, 0, 1, BH
		
		;Over Image
		SetBuffer TextureBuffer( tex, 1 )
		
		FUI_SetColor SC_GADGET
		Rect 0, 0, BW, BH
		FUI_AdjustColor-40
		Rect 0, 0, BW, BH, 0
		FUI_AdjustColor 40
		Rect 0, 0, BW, 1
		Rect 0, 0, 1, BH
		
		;Down Image
		SetBuffer TextureBuffer( tex, 2 )
		
		FUI_SetColor SC_GADGET
		Rect 0, 0, BW, BH
		FUI_AdjustColor 40
		Rect 0, 0, BW, BH, 0
		FUI_AdjustColor-40
		Rect 0, 0, BW, 1
		Rect 0, 0, 1, BH
		
		;Disabled Image
		SetBuffer TextureBuffer( tex, 3 )
		
		FUI_SetColor SC_GADGET
		Rect 0, 0, BW, BH
		FUI_AdjustColor-40
		Rect 0, 0, BW, BH, 0
		FUI_AdjustColor 40
		Rect 0, 0, BW, 1
		Rect 0, 0, 1, BH
		
		For A = 0 To 3
			SetBuffer TextureBuffer( tex, A )
			
			If A = 3
				FUI_SetColor SC_GADGET
				FUI_AdjustColor 60
			Else
				FUI_SetColor SC_GADGET_TEXT
			EndIf
			
			If A >= 2
				Origin 1, 1
			Else
				Origin 0, 0
			EndIf
			
			Rect BW / 2 - 2, BH / 2 - 1, 5, 1
			Rect BW / 2 - 1, BH / 2, 3, 1
			Rect BW / 2, BH / 2 + 1, 1, 1
			
			Origin 0, 0
			
			If A = 3
				FUI_AdjustColor-60
				Rect BW / 2 - 2, BH / 2 - 1, 5, 1
				Rect BW / 2 - 1, BH / 2, 3, 1
				Rect BW / 2, BH / 2 + 1, 1, 1
			EndIf
		Next
		
		FreeTexture tex
	EndIf
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildTextBox( gad.TextBox, Mode = 0 )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	;TextBox
	SetFont app\fntGadget
	
	If Mode = 0
		surf.BBSurface = GetSurface( gad\Mesh, 1 )
		ClearSurface surf
		
		If gad\W <= 0 Or gad\H <= 0
			Return
		EndIf
		
		FUI_SetColor SC_INPUT
		FUI_Rect 1, 1, gad\W - 2, gad\H - 2, surf
		FUI_SetColor SC_INPUT_BORDER
		FUI_Rect 0, 0, gad\W, gad\H, surf, 0
		
		;Caret
		surf = GetSurface( gad\Caret, 1 )
		ClearSurface surf
		
		FUI_SetColor SC_INPUT_TEXT
		FUI_Rect 0, 0, 1, FontHeight(), surf
	EndIf
	
	;Text
	surf = GetSurface( gad\MeshText, 1 )
	ClearSurface surf
	
	FUI_SetColor SC_WHITE
	FUI_Rect 1, 1, gad\W-2, gad\H-2, surf
	
	tex.BBTexture = FUI_CreateTexture( gad\W-2, gad\H-2, 1+2 )
	EntityTexture gad\MeshText, tex
	
	SetBuffer TextureBuffer( tex )
	
	FUI_SetColor SC_INPUT_TEXT
	FUI_Text tex, 4, (gad\H-2)/2, gad\Caption, 0, 1
	
	FreeTexture tex
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildTreeView( gad.TreeView )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	;TreeView
	surf.BBSurface = GetSurface( gad\Mesh, 1 )
	ClearSurface surf
	
	If gad\W <= 0 Or gad\H <= 0
		Return
	EndIf
	
	FUI_SetColor SC_INPUT
	FUI_Rect 1, 1, gad\W-2, gad\H-2, surf
	
	FUI_SetColor SC_INPUT_BORDER
	FUI_Rect 0, 0, gad\W, gad\H, surf, 0
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildTreeViewNode( gad.Node, Mode = 0 )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	;Node
	SetFont app\fntGadget
	
	surf.BBSurface = GetSurface( gad\Mesh, 1 )
	ClearSurface surf
	
	FUI_SetColor SC_WHITE
	FUI_Rect 0, 0, gad\Owner\W-TREEVIEW_PADDING*2, TREEVIEWITEM_HEIGHT, surf
	
	tex.BBTexture = FUI_CreateTexture( gad\Owner\W-TREEVIEW_PADDING*2, TREEVIEWITEM_HEIGHT, 1+2, 6 )
	EntityTexture gad\Mesh, tex
	
	For A = 0 To 5
		SetBuffer TextureBuffer( tex, A )
		
		;Up Image
		If A = 0 Or A = 3
			FUI_SetColor SC_INPUT_TEXT
			FUI_Text tex, 4 + gad\Layer*TREEVIEWITEM_HEIGHT, TREEVIEWITEM_HEIGHT / 2, gad\Caption, 0, 1
		;Over Image
		ElseIf A = 1 Or A = 4
			FUI_SetColor SC_INPUT_TEXT
			FUI_Text tex, 4 + gad\Layer*TREEVIEWITEM_HEIGHT, TREEVIEWITEM_HEIGHT / 2, gad\Caption, 0, 1
		;Down Image
		ElseIf A = 2 Or A = 5
			FUI_SetColor SC_INPUT_COLOR
			If surf <> Null 
				solid = True 
			Else 
				solid = False
			EndIf
			
			Rect 0, 0, gad\Owner\W-TREEVIEW_PADDING*2, TREEVIEWITEM_HEIGHT, solid
			
			FUI_SetColor SC_INPUT_COLOR_TEXT
			FUI_Text tex, 4 + gad\Layer*TREEVIEWITEM_HEIGHT, TREEVIEWITEM_HEIGHT / 2, gad\Caption, 0, 1
		EndIf
		
		If gad\HasChildren = True
			BX = (gad\Layer-1)*TREEVIEWITEM_HEIGHT + 5
			BY = 5
			BW = TREEVIEWITEM_HEIGHT - 10
			BH = BW
			
			;Box
			FUI_SetColor SC_INPUT
			Rect BX, BY, BW, BH
			FUI_SetColor SC_INPUT_BORDER
			Rect BX, BY, BW, BH, 0
			
			FUI_SetColor SC_INPUT_TEXT
			If BH Mod 2 = 0
				Rect BX+1, BY+BH/2-1, BW-2, 2
			Else
				Rect BX+1, BY+BH/2, BW-2, 1
			EndIf
			
			If A < 3
				If BW Mod 2 = 0
					Rect BX+BW/2-1, BY+1, 2, BH-2
				Else
					Rect BX+BW/2, BY+1, 1, BH-2
				EndIf
			EndIf
		EndIf
	Next
	
	FreeTexture tex
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildView( gad.View )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	;View
	surf.BBSurface = GetSurface( gad\Mesh, 1 )
	ClearSurface surf
	
	If gad\W <= 2 Or gad\H <= 2
		Return
	EndIf
	
	If (gad\Flags And CS_BORDER) = CS_BORDER
		;Selected
		If app\actView = gad
			FUI_SetColor SC_VIEW_BORDER_SEL
			FUI_Rect 0, 0, gad\W, gad\H, surf, 0
		Else
			FUI_SetColor SC_VIEW_BORDER
		EndIf
		FUI_Rect 1, 1, gad\W-2, gad\H-2, surf, 0
	EndIf
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildVScrollBar( gad.VScrollBar, Mode = 0 )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	;ScrollBar
	surf.BBSurface = GetSurface( gad\Mesh, 1 )
	ClearSurface surf
	
	If gad\H <= 0
		Return
	EndIf
	
	BW = gad\H - 1
	BH = gad\H - 1
	
	FUI_SetColor SC_GADGET
	FUI_Rect 1, 1 + BH, gad\H - 1, gad\W - 2 - BH*2, surf
	
	FUI_SetColor SC_INPUT
	FUI_Rect -LISTBOX_PADDING + 1, 0, LISTBOX_PADDING - 1, gad\W, surf
	
	FUI_SetColor SC_GADGET_BORDER
	FUI_Rect 0, 0, 1, gad\W, surf
	FUI_Rect 0, BH, gad\H, 1, surf
	FUI_Rect 0, gad\W - BH - 1, gad\H, 1, surf
	
	If Mode = 0
		;Button One
		surf = GetSurface( gad\Btn1, 1 )
		ClearSurface surf
		
		BX = 0
		BY = 0
		
		FUI_SetColor SC_WHITE
		FUI_Rect BX, BY, BW, BH, surf
		
		tex.BBTexture = FUI_CreateTexture( BW, BH, 1+2, 4 )
		EntityTexture gad\Btn1, tex
		
		;Up Image
		SetBuffer TextureBuffer( tex, 0 )
		
		FUI_SetColor SC_GADGET
		Rect 0, 0, BW, BH
		FUI_AdjustColor-40
		Rect 0, 0, BW, BH, 0
		FUI_AdjustColor 40
		Rect 0, 0, BW, 1
		Rect 0, 0, 1, BH
		
		FUI_SetColor SC_GADGET_TEXT
		Rect BW / 2 - 2, BH / 2 + 1, 5, 1
		Rect BW / 2 - 1, BH / 2, 3, 1
		Rect BW / 2, BH / 2 - 1, 1, 1
		
		;Over Image
		SetBuffer TextureBuffer( tex, 1 )
		
		FUI_SetColor SC_GADGET
		Rect 0, 0, BW, BH
		FUI_AdjustColor-40
		Rect 0, 0, BW, BH, 0
		FUI_AdjustColor 40
		Rect 0, 0, BW, 1
		Rect 0, 0, 1, BH
		
		FUI_SetColor SC_GADGET_TEXT
		Rect BW / 2 - 2, BH / 2 + 1, 5, 1
		Rect BW / 2 - 1, BH / 2, 3, 1
		Rect BW / 2, BH / 2 - 1, 1, 1
		
		;Down Image
		SetBuffer TextureBuffer( tex, 2 )
		
		FUI_SetColor SC_GADGET
		Rect 0, 0, BW, BH
		FUI_AdjustColor 40
		Rect 0, 0, BW, BH, 0
		FUI_AdjustColor-40
		Rect 0, 0, BW, 1
		Rect 0, 0, 1, BH
		
		Origin 1, 1
		
		FUI_SetColor SC_GADGET_TEXT
		Rect BW / 2 - 2, BH / 2 + 1, 5, 1
		Rect BW / 2 - 1, BH / 2, 3, 1
		Rect BW / 2, BH / 2 - 1, 1, 1
		
		Origin 0, 0
		
		;Disabled Image
		SetBuffer TextureBuffer( tex, 3 )
		
		FUI_SetColor SC_GADGET
		Rect 0, 0, BW, BH
		FUI_AdjustColor-40
		Rect 0, 0, BW, BH, 0
		FUI_AdjustColor 40
		Rect 0, 0, BW, 1
		Rect 0, 0, 1, BH
		
		Origin 1, 1
		FUI_AdjustColor 60
		Rect BW / 2 - 2, BH / 2 + 1, 5, 1
		Rect BW / 2 - 1, BH / 2, 3, 1
		Rect BW / 2, BH / 2 - 1, 1, 1
		Origin 0, 0
		FUI_AdjustColor-60
		Rect BW / 2 - 2, BH / 2 + 1, 5, 1
		Rect BW / 2 - 1, BH / 2, 3, 1
		Rect BW / 2, BH / 2 - 1, 1, 1
		
		FreeTexture tex
		
		;Button Two
		surf = GetSurface( gad\Btn2, 1 )
		ClearSurface surf
		
		BX = 0
		BY = 0
		
		FUI_SetColor SC_WHITE
		FUI_Rect BX, BY, BW, BH, surf
		
		tex = FUI_CreateTexture( BW, BH, 1+2, 4 )
		EntityTexture gad\Btn2, tex
		
		;Up Image
		SetBuffer TextureBuffer( tex, 0 )
		
		FUI_SetColor SC_GADGET
		Rect 0, 0, BW, BH
		FUI_AdjustColor-40
		Rect 0, 0, BW, BH, 0
		FUI_AdjustColor 40
		Rect 0, 0, BW, 1
		Rect 0, 0, 1, BH
		
		FUI_SetColor SC_GADGET_TEXT
		Rect BW / 2 - 2, BH / 2 - 1, 5, 1
		Rect BW / 2 - 1, BH / 2, 3, 1
		Rect BW / 2, BH / 2 + 1, 1, 1
		
		;Over Image
		SetBuffer TextureBuffer( tex, 1 )
		
		FUI_SetColor SC_GADGET
		Rect 0, 0, BW, BH
		FUI_AdjustColor-40
		Rect 0, 0, BW, BH, 0
		FUI_AdjustColor 40
		Rect 0, 0, BW, 1
		Rect 0, 0, 1, BH
		
		FUI_SetColor SC_GADGET_TEXT
		Rect BW / 2 - 2, BH / 2 - 1, 5, 1
		Rect BW / 2 - 1, BH / 2, 3, 1
		Rect BW / 2, BH / 2 + 1, 1, 1
		
		;Down Image
		SetBuffer TextureBuffer( tex, 2 )
		
		FUI_SetColor SC_GADGET
		Rect 0, 0, BW, BH
		FUI_AdjustColor 40
		Rect 0, 0, BW, BH, 0
		FUI_AdjustColor-40
		Rect 0, 0, BW, 1
		Rect 0, 0, 1, BH
		
		Origin 1, 1
		
		FUI_SetColor SC_GADGET_TEXT
		Rect BW / 2 - 2, BH / 2 - 1, 5, 1
		Rect BW / 2 - 1, BH / 2, 3, 1
		Rect BW / 2, BH / 2 + 1, 1, 1
		
		Origin 0, 0
		
		;Disabled Image
		SetBuffer TextureBuffer( tex, 3 )
		
		FUI_SetColor SC_GADGET
		Rect 0, 0, BW, BH
		FUI_AdjustColor-40
		Rect 0, 0, BW, BH, 0
		FUI_AdjustColor 40
		Rect 0, 0, BW, 1
		Rect 0, 0, 1, BH
		
		Origin 1, 1
		FUI_AdjustColor 60
		Rect BW / 2 - 2, BH / 2 - 1, 5, 1
		Rect BW / 2 - 1, BH / 2, 3, 1
		Rect BW / 2, BH / 2 + 1, 1, 1
		Origin 0, 0
		FUI_AdjustColor-60
		Rect BW / 2 - 2, BH / 2 - 1, 5, 1
		Rect BW / 2 - 1, BH / 2, 3, 1
		Rect BW / 2, BH / 2 + 1, 1, 1
		
		FreeTexture tex
		
		;ScrollBar
		surf = GetSurface( gad\ScrollBar, 1 )
		ClearSurface surf
		
		FUI_SetColor SC_GADGET
		FUI_Rect 1, 1, gad\SH - 2, gad\SW - 2, surf
		FUI_AdjustColor 40
		FUI_Rect 0, 0, gad\SH, 1, surf
		FUI_Rect 0, 1, 1, gad\SW - 1, surf
		FUI_AdjustColor-40
		FUI_Rect gad\SH - 1, 1, 1, gad\SW - 2, surf
		FUI_Rect 1, gad\SW - 1, gad\SH - 1, 1, surf
	EndIf
	
	SetBuffer pBuffer
	
End Function

Function FUI_BuildToolTip(  )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	;Tool Tip
	surf.BBSurface = GetSurface( app\ToolTipMesh, 1 )
	ClearSurface surf
	
	SetFont app\fntGadget
	tex.BBTexture = FUI_CreateTexture( StringWidth( app\ToolTip ) + 12, FontHeight(  ) + 10, 1+2, 1 )
	EntityTexture app\ToolTipMesh, tex
	
	SetBuffer TextureBuffer( tex )
	
	FUI_SetColor SC_WHITE
	FUI_Rect 0, 0, StringWidth( app\ToolTip ) + 12, FontHeight(  ) + 10, surf
	
	For A = 4 To 0 Step - 1
		shadow = 0 Or (0 Shl 8) Or (0 Shl 16) Or (A*16 Shl 24)
		
		For B = 3 To FontHeight(  ) + 5 + (4-A)
			WritePixelFast StringWidth( app\ToolTip ) + 10 - A, B, shadow
		Next
		For B = 3 To StringWidth( app\ToolTip ) + 7 + (4-A)
			WritePixelFast B, FontHeight(  ) + 5 + (4-A), shadow
		Next
	Next
	
	FUI_SetColor SC_TOOLTIP
	Rect 0, 0, StringWidth( app\ToolTip ) + 7, FontHeight(  ) + 5
	FUI_SetColor SC_TOOLTIP_BORDER
	Rect 0, 0, StringWidth( app\ToolTip ) + 7, FontHeight(  ) + 5, 0
	
	FUI_SetColor SC_TOOLTIP_TEXT
	FUI_Text tex, 3, 2, app\ToolTip
	
	FreeTexture tex
	
	SetBuffer pBuffer
	
End Function
;#End Region

;#Region ---------- Update Gadgets -------------
Function FUI_UpdateGadgets( ID )
	
	FUI_UpdateRegion( ID )
	FUI_UpdateTab( ID )
	FUI_UpdatePanel( ID )
	
	FUI_UpdateButton( ID )
	FUI_UpdateCheckBox( ID )
	FUI_UpdateComboBox( ID )
	FUI_UpdateGroupBox( ID )
	FUI_UpdateImageBox( ID )
	FUI_UpdateLabel( ID )
	FUI_UpdateListBox( ID )
	FUI_UpdateProgressBar( ID )
	FUI_UpdateRadio( ID )
	FUI_UpdateScrollBar( ID )
	FUI_UpdateSlider( ID )
	FUI_UpdateSpinner( ID )
	FUI_UpdateTextBox( ID )
	FUI_UpdateTreeView( ID )
	
	If RENDER_MODE = 0
		FUI_UpdateView( ID )
	EndIf
	
End Function

Function FUI_UpdateAnchors( ID )
	
	win.Window = Object.Window( ID )
	reg2.Region = Object.Region( ID )
	tabp.TabPage = Object.TabPage( ID )
	If tabp <> Null
		tab2.Tab = tabp\Owner
	Else
		tab2.Tab = Object.Tab( ID )
	EndIf
	pan2.Panel = Object.Panel( ID )
	grp2.GroupBox = Object.GroupBox( ID )
	
	If Handle( win ) = ID
		W = win\FW
		H = win\FH
		
		OX = win\OX
		OY = win\OY
		OW = win\OW
		OH = win\OH
	ElseIf Handle( reg2 ) = ID
		W = reg2\FW
		H = reg2\FH
		
		OX = reg2\OX
		OY = reg2\OY
		OW = reg2\OW
		OH = reg2\OH
	ElseIf Handle( tab2 ) = ID
		W = tab2\FW
		H = tab2\FH
		
		OX = tab2\OX
		OY = tab2\OY
		OW = tab2\OW
		OH = tab2\OH
	ElseIf Handle( pan2 ) = ID
		W = pan2\FW
		H = pan2\FH
		
		OX = pan2\OX
		OY = pan2\OY
		OW = pan2\OW
		OH = pan2\OH
	ElseIf Handle( grp2 ) = ID
		W = grp2\FW
		H = grp2\FH
		
		OX = grp2\OX
		OY = grp2\OY
		OW = grp2\OW
		OH = grp2\OH
	EndIf
	
	For reg.Region = Each Region
		If reg\Anchor = ID
			If reg\AX = 1
				reg\X = W - (OW - reg\OX)
				FUI_SetChildPosition reg\Mesh, reg\X,-EntityY( reg\Mesh )
			ElseIf reg\AX = 2
				reg\X = W / (Float(OW) / reg\OX)
				FUI_SetChildPosition reg\Mesh, reg\X,-EntityY( reg\Mesh )
			EndIf
			If reg\AY = 1
				reg\Y = H - (OH - reg\OY)
				FUI_SetChildPosition reg\Mesh, EntityX( reg\Mesh ), reg\Y
			ElseIf reg\AY = 2
				reg\Y = H / (Float(OH) / reg\OY)
				FUI_SetChildPosition reg\Mesh, EntityX( reg\Mesh ), reg\Y
			EndIf
			If reg\AW = 1
				reg\W = W - (OW - reg\OW)
			ElseIf reg\AW = 2
				reg\W = W / (Float(OW) / reg\OW)
			EndIf
			If reg\AH = 1
				reg\H = H - (OH - reg\OH)
			ElseIf reg\AH = 2
				reg\H = H / (Float(OH) / reg\OH)
			EndIf
			
			If reg\AW > 0 Or reg\AH > 0
				FUI_BuildRegion reg
			EndIf
			
			FUI_UpdateAnchors Handle( reg )
		EndIf
	Next
	For Tab.Tab = Each Tab
		If Tab\Anchor = ID
			If Tab\AX = 1
				Tab\X = W - (OW - Tab\OX)
				FUI_SetChildPosition Tab\Mesh, Tab\X,-EntityY( Tab\Mesh )
			ElseIf Tab\AX = 2
				Tab\X = W / (Float(OW) / Tab\OX)
				FUI_SetChildPosition Tab\Mesh, Tab\X,-EntityY( Tab\Mesh )
			EndIf
			If Tab\AY = 1
				Tab\Y = H - (OH - Tab\OY)
				FUI_SetChildPosition Tab\Mesh, EntityX( Tab\Mesh ), Tab\Y
			ElseIf Tab\AY = 2
				Tab\Y = H / (Float(OH) / Tab\OY)
				FUI_SetChildPosition Tab\Mesh, EntityX( Tab\Mesh ), Tab\Y
			EndIf
			If Tab\AW = 1
				Tab\W = W - (OW - Tab\OW)
			ElseIf Tab\AW = 2
				Tab\W = W / (Float(OW) / Tab\OW)
			EndIf
			If Tab\AH = 1
				Tab\H = H - (OH - Tab\OH)
			ElseIf Tab\AH = 2
				Tab\H = H / (Float(OH) / Tab\OH)
			EndIf
			
			If Tab\AW > 0 Or Tab\AH > 0
				FUI_BuildTab Tab
			EndIf
			
			FUI_UpdateAnchors Handle( Tab )
		EndIf
	Next
	For pan.Panel = Each Panel
		If pan\Anchor = ID
			If pan\AX = 1
				pan\X = W - (OW - pan\OX)
				FUI_SetChildPosition pan\Mesh, pan\X,-EntityY( pan\Mesh )
			ElseIf pan\AX = 2
				pan\X = W / (Float(OW) / pan\OX)
				FUI_SetChildPosition pan\Mesh, pan\X,-EntityY( pan\Mesh )
			EndIf
			If pan\AY = 1
				pan\Y = H - (OH - pan\OY)
				FUI_SetChildPosition pan\Mesh, EntityX( pan\Mesh ), pan\Y
			ElseIf pan\AY = 2
				pan\Y = H / (Float(OH) / pan\OY)
				FUI_SetChildPosition pan\Mesh, EntityX( pan\Mesh ), pan\Y
			EndIf
			If pan\AW = 1
				pan\W = W - (OW - pan\OW)
			ElseIf pan\AW = 2
				pan\W = W / (Float(OW) / pan\OW)
			EndIf
			If pan\AH = 1
				pan\H = H - (OH - pan\OH)
			ElseIf pan\AH = 2
				pan\H = H / (Float(OH) / pan\OH)
			EndIf
			
			If pan\AW > 0 Or pan\AH > 0
				FUI_BuildPanel pan
			EndIf
			
			FUI_UpdateAnchors Handle( pan )
		EndIf
	Next
	For btn.Button = Each Button
		If btn\Anchor = ID
			If btn\AX = 1
				btn\X = W - (OW - btn\OX)
				FUI_SetChildPosition btn\Mesh, btn\X,-EntityY( btn\Mesh )
			ElseIf btn\AX = 2
				btn\X = W / (Float(OW) / btn\OX)
				FUI_SetChildPosition btn\Mesh, btn\X,-EntityY( btn\Mesh )
			EndIf
			If btn\AY = 1
				btn\Y = H - (OH - btn\OY)
				FUI_SetChildPosition btn\Mesh, EntityX( btn\Mesh ), btn\Y
			ElseIf btn\AY = 2
				btn\Y = H / (Float(OH) / btn\OY)
				FUI_SetChildPosition btn\Mesh, EntityX( btn\Mesh ), btn\Y
			EndIf
			If btn\AW = 1
				btn\W = W - (OW - btn\OW)
			ElseIf btn\AW = 2
				btn\W = W / (Float(OW) / btn\OW)
			EndIf
			If btn\AH = 1
				btn\H = H - (OH - btn\OH)
			ElseIf btn\AH = 2
				btn\H = H / (Float(OH) / btn\OH)
			EndIf
			
			If btn\AW > 0 Or btn\AH > 0
				FUI_BuildButton btn
			EndIf
		EndIf
	Next
	For chk.CheckBox = Each CheckBox
		If chk\Anchor = ID
			If chk\AX = 1
				chk\X = W - (OW - chk\OX)
				FUI_SetChildPosition chk\Mesh, chk\X,-EntityY( chk\Mesh )
			ElseIf chk\AX = 2
				chk\X = W / (Float(OW) / chk\OX)
				FUI_SetChildPosition chk\Mesh, chk\X,-EntityY( chk\Mesh )
			EndIf
			If chk\AY = 1
				chk\Y = H - (OH - chk\OY)
				FUI_SetChildPosition chk\Mesh, EntityX( chk\Mesh ), chk\Y
			ElseIf chk\AY = 2
				chk\Y = H / (Float(OH) / chk\OY)
				FUI_SetChildPosition chk\Mesh, EntityX( chk\Mesh ), chk\Y
			EndIf
		EndIf
	Next
	For cbo.ComboBox = Each ComboBox
		If cbo\Anchor = ID
			If cbo\AX = 1
				cbo\X = W - (OW - cbo\OX)
				FUI_SetChildPosition cbo\Mesh, cbo\X,-EntityY( cbo\Mesh )
			ElseIf cbo\AX = 2
				cbo\X = W / (Float(OW) / cbo\OX)
				FUI_SetChildPosition cbo\Mesh, cbo\X,-EntityY( cbo\Mesh )
			EndIf
			If cbo\AY = 1
				cbo\Y = H - (OH - cbo\OY)
				FUI_SetChildPosition cbo\Mesh, EntityX( cbo\Mesh ), cbo\Y
			ElseIf cbo\AY = 2
				cbo\Y = H / (Float(OH) / cbo\OY)
				FUI_SetChildPosition cbo\Mesh, EntityX( cbo\Mesh ), cbo\Y
			EndIf
			If cbo\AW = 1
				cbo\W = W - (OW - cbo\OW)
			ElseIf cbo\AW = 2
				cbo\W = W / (Float(OW) / cbo\OW)
			EndIf
			If cbo\AH = 1
				cbo\H = H - (OH - cbo\OH)
			ElseIf cbo\AH = 2
				cbo\H = H / (Float(OH) / cbo\OH)
			EndIf
			
			If cbo\AW > 0 Or cbo\AH > 0
				;;;FUI_BuildComboBox cbo
			EndIf
		EndIf
	Next
	For grp.GroupBox = Each GroupBox
		If grp\Anchor = ID
			If grp\AX = 1
				grp\X = W - (OW - grp\OX)
				FUI_SetChildPosition grp\Mesh, grp\X,-EntityY( grp\Mesh )
			ElseIf grp\AX = 2
				grp\X = W / (Float(OW) / grp\OX)
				FUI_SetChildPosition grp\Mesh, grp\X,-EntityY( grp\Mesh )
			EndIf
			If grp\AY = 1
				grp\Y = H - (OH - grp\OY)
				FUI_SetChildPosition grp\Mesh, EntityX( grp\Mesh ), grp\Y
			ElseIf grp\AY = 2
				grp\Y = H / (Float(OH) / grp\OY)
				FUI_SetChildPosition grp\Mesh, EntityX( grp\Mesh ), grp\Y
			EndIf
			If grp\AW = 1
				grp\W = W - (OW - grp\OW)
			ElseIf grp\AW = 2
				grp\W = W / (Float(OW) / grp\OW)
			EndIf
			If grp\AH = 1
				grp\H = H - (OH - grp\OH)
			ElseIf grp\AH = 2
				grp\H = H / (Float(OH) / grp\OH)
			EndIf
			
			If grp\AW > 0 Or grp\AH > 0
				FUI_BuildGroupBox grp
			EndIf
			
			FUI_UpdateAnchors Handle( grp )
		EndIf
	Next
	For img.ImageBox = Each ImageBox
		If img\Anchor = ID
			If img\AX = 1
				img\X = W - (OW - img\OX)
				FUI_SetChildPosition img\Mesh, img\X,-EntityY( img\Mesh )
			ElseIf img\AX = 2
				img\X = W / (Float(OW) / img\OX)
				FUI_SetChildPosition img\Mesh, img\X,-EntityY( img\Mesh )
			EndIf
			If img\AY = 1
				img\Y = H - (OH - img\OY)
				FUI_SetChildPosition img\Mesh, EntityX( img\Mesh ), img\Y
			ElseIf img\AY = 2
				img\Y = H / (Float(OH) / img\OY)
				FUI_SetChildPosition img\Mesh, EntityX( img\Mesh ), img\Y
			EndIf
			If img\AW = 1
				img\W = W - (OW - img\OW)
			ElseIf img\AW = 2
				img\W = W / (Float(OW) / img\OW)
			EndIf
			If img\AH = 1
				img\H = H - (OH - img\OH)
			ElseIf img\AH = 2
				img\H = H / (Float(OH) / img\OH)
			EndIf
			
			If img\AW > 0 Or img\AH > 0
				FUI_BuildImageBox img
			EndIf
		EndIf
	Next
	For lbl.Label = Each Label
		If lbl\Anchor = ID
			If lbl\AX = 1
				lbl\X = W - (OW - lbl\OX)
				FUI_SetChildPosition lbl\Mesh, lbl\X,-EntityY( lbl\Mesh )
			ElseIf lbl\AX = 2
				lbl\X = W / (Float(OW) / lbl\OX)
				FUI_SetChildPosition lbl\Mesh, lbl\X,-EntityY( lbl\Mesh )
			EndIf
			If lbl\AY = 1
				lbl\Y = H - (OH - lbl\OY)
				FUI_SetChildPosition lbl\Mesh, EntityX( lbl\Mesh ), lbl\Y
			ElseIf lbl\AY = 2
				lbl\Y = H / (Float(OH) / lbl\OY)
				FUI_SetChildPosition lbl\Mesh, EntityX( lbl\Mesh ), lbl\Y
			EndIf
		EndIf
	Next
	For lst.ListBox = Each ListBox
		If lst\Anchor = ID
			If lst\AX = 1
				lst\X = W - (OW - lst\OX)
				FUI_SetChildPosition lst\Mesh, lst\X,-EntityY( lst\Mesh )
			ElseIf lst\AX = 2
				lst\X = W / (Float(OW) / lst\OX)
				FUI_SetChildPosition lst\Mesh, lst\X,-EntityY( lst\Mesh )
			EndIf
			If lst\AY = 1
				lst\Y = H - (OH - lst\OY)
				FUI_SetChildPosition lst\Mesh, EntityX( lst\Mesh ), lst\Y
			ElseIf lst\AY = 2
				lst\Y = H / (Float(OH) / lst\OY)
				FUI_SetChildPosition lst\Mesh, EntityX( lst\Mesh ), lst\Y
			EndIf
			If lst\AW = 1
				lst\W = W - (OW - lst\OW)
			ElseIf lst\AW = 2
				lst\W = W / (Float(OW) / lst\OW)
			EndIf
			If lst\AH = 1
				lst\H = H - (OH - lst\OH)
			ElseIf lst\AH = 2
				lst\H = H / (Float(OH) / lst\OH)
			EndIf
			
			If lst\AW > 0 Or lst\AH > 0
				FUI_BuildListBox lst
			EndIf
		EndIf
	Next
	For prg.ProgressBar = Each ProgressBar
		If prg\Anchor = ID
			If prg\AX = 1
				prg\X = W - (OW - prg\OX)
				FUI_SetChildPosition prg\Mesh, prg\X,-EntityY( prg\Mesh )
			ElseIf prg\AX = 2
				prg\X = W / (Float(OW) / prg\OX)
				FUI_SetChildPosition prg\Mesh, prg\X,-EntityY( prg\Mesh )
			EndIf
			If prg\AY = 1
				prg\Y = H - (OH - prg\OY)
				FUI_SetChildPosition prg\Mesh, EntityX( prg\Mesh ), prg\Y
			ElseIf prg\AY = 2
				prg\Y = H / (Float(OH) / prg\OY)
				FUI_SetChildPosition prg\Mesh, EntityX( prg\Mesh ), prg\Y
			EndIf
			If prg\AW = 1
				prg\W = W - (OW - prg\OW)
			ElseIf prg\AW = 2
				prg\W = W / (Float(OW) / prg\OW)
			EndIf
			If prg\AH = 1
				prg\H = H - (OH - prg\OH)
			ElseIf prg\AH = 2
				prg\H = H / (Float(OH) / prg\OH)
			EndIf
			
			If prg\AW > 0 Or prg\AH > 0
				FUI_BuildProgressBar prg
			EndIf
		EndIf
	Next
	For rad.Radio = Each Radio
		If rad\Anchor = ID
			If rad\AX = 1
				rad\X = W - (OW - rad\OX)
				FUI_SetChildPosition rad\Mesh, rad\X,-EntityY( rad\Mesh )
			ElseIf rad\AX = 2
				rad\X = W / (Float(OW) / rad\OX)
				FUI_SetChildPosition rad\Mesh, rad\X,-EntityY( rad\Mesh )
			EndIf
			If rad\AY = 1
				rad\Y = H - (OH - rad\OY)
				FUI_SetChildPosition rad\Mesh, EntityX( rad\Mesh ), rad\Y
			ElseIf rad\AY = 2
				rad\Y = H / (Float(OH) / rad\OY)
				FUI_SetChildPosition rad\Mesh, EntityX( rad\Mesh ), rad\Y
			EndIf
		EndIf
	Next
	For scroll.ScrollBar = Each ScrollBar
		If scroll\Anchor = ID
			If scroll\AX = 1
				scroll\X = W - (OW - scroll\OX)
				FUI_SetChildPosition scroll\Mesh, scroll\X,-EntityY( scroll\Mesh )
			ElseIf scroll\AX = 2
				scroll\X = W / (Float(OW) / scroll\OX)
				FUI_SetChildPosition scroll\Mesh, scroll\X,-EntityY( scroll\Mesh )
			EndIf
			If scroll\AY = 1
				scroll\Y = H - (OH - scroll\OY)
				FUI_SetChildPosition scroll\Mesh, EntityX( scroll\Mesh ), scroll\Y
			ElseIf scroll\AY = 2
				scroll\Y = H / (Float(OH) / scroll\OY)
				FUI_SetChildPosition scroll\Mesh, EntityX( scroll\Mesh ), scroll\Y
			EndIf
			If scroll\AW = 1
				scroll\W = H - (OH - scroll\OW)
			ElseIf scroll\AW = 2
				scroll\W = H / (Float(OH) / scroll\OW)
			EndIf
			If scroll\AH = 1
				scroll\H = W - (OW - scroll\OH)
			ElseIf scroll\AH = 2
				scroll\H = W / (Float(OW) / scroll\OH)
			EndIf
			
			If scroll\AW > 0 Or scroll\AH > 0
				FUI_BuildScrollBar scroll
			EndIf
		EndIf
	Next
	For sld.Slider = Each Slider
		If sld\Anchor = ID
			If sld\AX = 1
				sld\X = W - (OW - sld\OX)
				FUI_SetChildPosition sld\Mesh, sld\X,-EntityY( sld\Mesh )
			ElseIf sld\AX = 2
				sld\X = W / (Float(OW) / sld\OX)
				FUI_SetChildPosition sld\Mesh, sld\X,-EntityY( sld\Mesh )
			EndIf
			If sld\AY = 1
				sld\Y = H - (OH - sld\OY)
				FUI_SetChildPosition sld\Mesh, EntityX( sld\Mesh ), sld\Y
			ElseIf sld\AY = 2
				sld\Y = H / (Float(OH) / sld\OY)
				FUI_SetChildPosition sld\Mesh, EntityX( sld\Mesh ), sld\Y
			EndIf
			If sld\AW = 1
				sld\W = W - (OW - sld\OW)
			ElseIf sld\AW = 2
				sld\W = W / (Float(OW) / sld\OW)
			EndIf
			If sld\AH = 1
				sld\H = H - (OH - sld\OH)
			ElseIf sld\AH = 2
				sld\H = H / (Float(OH) / sld\OH)
			EndIf
			
			If sld\AW > 0 Or sld\AH > 0
				FUI_BuildSlider sld
			EndIf
		EndIf
	Next
	For spn.Spinner = Each Spinner
		If spn\Anchor = ID
			If spn\AX = 1
				spn\X = W - (OW - spn\OX)
				FUI_SetChildPosition spn\Mesh, spn\X,-EntityY( spn\Mesh )
			ElseIf spn\AX = 2
				spn\X = W / (Float(OW) / spn\OX)
				FUI_SetChildPosition spn\Mesh, spn\X,-EntityY( spn\Mesh )
			EndIf
			If spn\AY = 1
				spn\Y = H - (OH - spn\OY)
				FUI_SetChildPosition spn\Mesh, EntityX( spn\Mesh ), spn\Y
			ElseIf spn\AY = 2
				spn\Y = H / (Float(OH) / spn\OY)
				FUI_SetChildPosition spn\Mesh, EntityX( spn\Mesh ), spn\Y
			EndIf
			If spn\AW = 1
				spn\W = W - (OW - spn\OW)
			ElseIf spn\AW = 2
				spn\W = W / (Float(OW) / spn\OW)
			EndIf
			If spn\AH = 1
				spn\H = H - (OH - spn\OH)
			ElseIf spn\AH = 2
				spn\H = H / (Float(OH) / spn\OH)
			EndIf
			
			If spn\AW > 0 Or spn\AH > 0
				FUI_BuildSpinner spn
			EndIf
		EndIf
	Next
	For tree.TreeView = Each TreeView
		If tree\Anchor = ID
			If tree\AX = 1
				tree\X = W - (OW - tree\OX)
				FUI_SetChildPosition tree\Mesh, tree\X,-EntityY( tree\Mesh )
			ElseIf tree\AX = 2
				tree\X = W / (Float(OW) / tree\OX)
				FUI_SetChildPosition tree\Mesh, tree\X,-EntityY( tree\Mesh )
			EndIf
			If tree\AY = 1
				tree\Y = H - (OH - tree\OY)
				FUI_SetChildPosition tree\Mesh, EntityX( tree\Mesh ), tree\Y
			ElseIf tree\AY = 2
				tree\Y = H / (Float(OH) / tree\OY)
				FUI_SetChildPosition tree\Mesh, EntityX( tree\Mesh ), tree\Y
			EndIf
			If tree\AW = 1
				tree\W = W - (OW - tree\OW)
			ElseIf tree\AW = 2
				tree\W = W / (Float(OW) / tree\OW)
			EndIf
			If tree\AH = 1
				tree\H = H - (OH - tree\OH)
			ElseIf tree\AH = 2
				tree\H = H / (Float(OH) / tree\OH)
			EndIf
			
			If tree\AW > 0 Or tree\AH > 0
				FUI_BuildTreeView tree
			EndIf
		EndIf
	Next
	For txt.TextBox = Each TextBox
		If txt\Anchor = ID
			If txt\AX = 1
				txt\X = W - (OW - txt\OX)
				FUI_SetChildPosition txt\Mesh, txt\X,-EntityY( txt\Mesh )
			ElseIf txt\AX = 2
				txt\X = W / (Float(OW) / txt\OX)
				FUI_SetChildPosition txt\Mesh, txt\X,-EntityY( txt\Mesh )
			EndIf
			If txt\AY = 1
				txt\Y = H - (OH - txt\OY)
				FUI_SetChildPosition txt\Mesh, EntityX( txt\Mesh ), txt\Y
			ElseIf txt\AY = 2
				txt\Y = H / (Float(OH) / txt\OY)
				FUI_SetChildPosition txt\Mesh, EntityX( txt\Mesh ), txt\Y
			EndIf
			If txt\AW = 1
				txt\W = W - (OW - txt\OW)
			ElseIf txt\AW = 2
				txt\W = W / (Float(OW) / txt\OW)
			EndIf
			If txt\AH = 1
				txt\H = H - (OH - txt\OH)
			ElseIf txt\AH = 2
				txt\H = H / (Float(OH) / txt\OH)
			EndIf
			
			If txt\AW > 0 Or txt\AH > 0
				FUI_BuildTextBox txt
			EndIf
		EndIf
	Next
	For view.View = Each View
		If view\Anchor = ID
			If view\AX = 1
				view\X = W - (OW - view\OX)
				FUI_SetChildPosition view\Mesh, view\X,-EntityY( view\Mesh )
			ElseIf view\AX = 2
				view\X = W / (Float(OW) / view\OX)
				FUI_SetChildPosition view\Mesh, view\X,-EntityY( view\Mesh )
			EndIf
			If view\AY = 1
				view\Y = H - (OH - view\OY)
				FUI_SetChildPosition view\Mesh, EntityX( view\Mesh ), view\Y
			ElseIf view\AY = 2
				view\Y = H / (Float(OH) / view\OY)
				FUI_SetChildPosition view\Mesh, EntityX( view\Mesh ), view\Y
			EndIf
			If view\AW = 1
				view\W = W - (OW - view\OW)
			ElseIf view\AW = 2
				view\W = W / (Float(OW) / view\OW)
			EndIf
			If view\AH = 1
				view\H = H - (OH - view\OH)
			ElseIf view\AH = 2
				view\H = H / (Float(OH) / view\OH)
			EndIf
			
			If view\AW > 0 Or view\AH > 0
				FUI_BuildView view
			EndIf
		EndIf
	Next
	
End Function

Function FUI_UpdateWindow(  )
	
	;Z-Order
	app\modalWin = Null
	app\overWin = Null
	win.Window = First Window
	For gad.Window = Each Window
		If gad\Closed = False
			H = gad\H
			If gad\Minimised = True H = TITLEBAR_HEIGHT
			
			If gad\Modal = True And app\modalWin = Null
				app\modalWin = gad
			EndIf
			
			If FUI_MouseOver( gad\X, gad\Y, gad\W, H ) = True
				app\overWin		= gad
				gad\overGadget	= True
				If FUI_NoActiveGadgets() = True
					If app\MB1 = 1
						If win\Modal = False Or win\Closed = True
							Insert gad Before First Window
						EndIf
					EndIf
				EndIf
				Exit
			Else
				gad\overGadget = False
			EndIf
		EndIf
	Next
	
	;Update
	Count = ORDER_START
	gad.Window = Last Window
	While gad <> Null
		gad\Order = Count
		EntityOrder gad\Mesh, Count
		If gad\MinMesh <> Null
			EntityOrder gad\MinMesh, Count
		EndIf
		If gad\TitleBar <> Null
			EntityOrder gad\TitleBar, Count
			EntityOrder gad\TitleBarText, Count-1
			EntityOrder gad\IconMesh, Count-1
		EndIf
		If gad\MenuBar <> Null
			EntityOrder gad\MenuBar, Count
		EndIf
		If gad\StatusBar <> Null
			EntityOrder gad\StatusBar, Count
			EntityOrder gad\StatusBarText, Count-1
		EndIf
		If gad\Closed = False
			If RENDER_MODE = 1
				If gad\Minimised = False
					ShowEntity gad\Mesh
				Else
					ShowEntity gad\MinMesh
				EndIf
			EndIf
			
			;Titlebar
			If (gad\Flags And 1) = 1
				
				If gad\Minimised = False
					EntityParent gad\TitleBar, gad\Mesh
				Else
					EntityParent gad\TitleBar, gad\MinMesh
				EndIf
				
				;Buttons
				BH = TITLEBAR_HEIGHT - 6
				
				Valid = False
				If gad = app\overWin And (gad = app\modalWin Or app\modalWin = Null)
					Valid = True
				EndIf
				
				RBX = gad\W - 4 - BH
				;Close Button
				If (gad\Flags And WS_CLOSEBUTTON) = WS_CLOSEBUTTON
					BX = FUI_InternalX( gad\CloseBtn )
					BY = FUI_InternalY( gad\CloseBtn )
					EntityOrder gad\CloseBtn, Count-1
					
					If FUI_MouseOver( BX, BY, BH, BH ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
						gad\CloseBtnState = 1
						If app\MB1 = 1
							gad\CloseBtnActive = True
						ElseIf app\MB1 = 2
							If gad\CloseBtnActive = True
								gad\CloseBtnState =-1
							EndIf
						Else
							If gad\CloseBtnActive = True
								gad\CloseBtnActive = False
								gad\Closed = True
								Insert gad After Last Window
;								HideEntity gad\Mesh
;								HideEntity gad\MinMesh
								FUI_CreateEvent Handle( gad ), "Closed"
							EndIf
						EndIf
					Else
						gad\CloseBtnState = 0
						If app\MB1 = 0 Or app\MB1 = 3
							gad\CloseBtnActive = False
						EndIf
					EndIf
					
					tex.BBTexture = FUI_GadgetTexture( gad\CloseBtn )
					Select gad\CloseBtnState
						Case 0
							EntityTexture gad\CloseBtn, tex, 0;+(gad\Minimised*3)
						Case 1
							EntityTexture gad\CloseBtn, tex, 1;+(gad\Minimised*3)
						Case -1
							EntityTexture gad\CloseBtn, tex, 2;+(gad\Minimised*3)
					End Select
					FreeTexture tex
					
					RBX = RBX - BH - 1
				EndIf
				
				;Minimise Button
				If (gad\Flags And WS_MINBUTTON) = WS_MINBUTTON
					BX = FUI_InternalX( gad\MinBtn )
					BY = FUI_InternalY( gad\MinBtn )
					EntityOrder gad\MinBtn, Count-1
					
					If FUI_MouseOver( BX, BY, BH, BH ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
						gad\MinBtnState = 1
						If app\MB1 = 1
							gad\MinBtnActive = True
						ElseIf app\MB1 = 2
							If gad\MinBtnActive = True
								gad\MinBtnState =-1
							EndIf
						Else
							If gad\MinBtnActive = True
								gad\MinBtnActive = False
								If gad\Minimised = False
									gad\Minimised = True
									EntityParent gad\TitleBar, gad\MinMesh
									HideEntity gad\Mesh
									ShowEntity gad\MinMesh
									tex = FUI_GadgetTexture( gad\TitleBar )
									If tex <> Null
										EntityTexture gad\TitleBar, tex, 1
										FreeTexture tex
									EndIf
									FUI_CreateEvent Handle( gad ), "Minimised"
								Else
									gad\Minimised = False
									EntityParent gad\TitleBar, gad\Mesh
									ShowEntity gad\Mesh
									HideEntity gad\MinMesh
									tex = FUI_GadgetTexture( gad\TitleBar )
									If tex <> Null
										EntityTexture gad\TitleBar, tex, 0
										FreeTexture tex
									EndIf
									FUI_CreateEvent Handle( gad ), "Maximised"
								EndIf
							EndIf
						EndIf
					Else
						gad\MinBtnState = 0
						If app\MB1 = 0 Or app\MB1 = 3
							gad\MinBtnActive = False
						EndIf
					EndIf
					
					tex = FUI_GadgetTexture( gad\MinBtn )
					Select gad\MinBtnState
						Case 0
							EntityTexture gad\MinBtn, tex, 0+(gad\Minimised*3)
						Case 1
							EntityTexture gad\MinBtn, tex, 1+(gad\Minimised*3)
						Case -1
							EntityTexture gad\MinBtn, tex, 2+(gad\Minimised*3)
					End Select
					FreeTexture tex
				EndIf
				
				;Dragging
				If gad\Locked = False
					If app\MB1 = 1
						If FUI_MouseOver( gad\X + gad\DragX, gad\Y + gad\DragY, gad\DragW, gad\DragH ) = True And gad = First Window
							If gad\CloseBtnActive = False And gad\MinBtnActive = False
								gad\Dragging = True
								gad\OffsetX = gad\X - app\MX
								gad\OffsetY = gad\Y - app\MY
								FUI_CreateEvent Handle( gad ), "Started Dragging", gad\X, gad\Y
							EndIf
						EndIf
					ElseIf app\MB1 = 2
						If gad\Dragging = True
							gad\X = app\MX + gad\OffsetX
							gad\Y = app\MY + gad\OffsetY
							
							FUI_SetPosition gad\Mesh, gad\X, gad\Y
							FUI_SetPosition gad\MinMesh, gad\X, gad\Y
							
							FUI_CreateEvent Handle( gad ), "Dragging", gad\X, gad\Y
						EndIf
					Else
						gad\Dragging = False
						FUI_CreateEvent Handle( gad ), "Stopped Dragging", gad\X, gad\Y
					EndIf
				EndIf
			EndIf
			
			If (gad\Flags And WS_RESIZABLE) = WS_RESIZABLE
				;Resizing
				If gad\Locked = False And gad\Dragging = False
					If overButton = False And overTitleBar = False
						If FUI_MouseOver( gad\X + gad\W - STATUSBAR_HEIGHT, gad\Y + gad\H - STATUSBAR_HEIGHT, STATUSBAR_HEIGHT - 1, STATUSBAR_HEIGHT - 1 ) = True And Valid = True
							app\Win32Mouse = IDCSizeNW
							If app\MB1 = 1 And gad = First Window
								gad\Resizing = True
								gad\OffsetX = gad\W - app\MX
								gad\OffsetY = gad\H - app\MY
							EndIf
						ElseIf FUI_MouseOver( gad\X + gad\W - 5, gad\Y, 5, gad\H ) = True
							app\Win32Mouse = IDCSizeWE
							If app\MB1 = 1 And gad = First Window
								gad\ResizingX = True
								gad\OffsetX = gad\W - app\MX
							EndIf
						ElseIf FUI_MouseOver( gad\X, gad\Y + gad\H - 5, gad\W, 5 ) = True
							app\Win32Mouse = IDCSizeNS
							If app\MB1 = 1 And gad = First Window
								gad\ResizingY = True
								gad\OffsetY = gad\H - app\MY
							EndIf
						EndIf
					EndIf
					If app\MB1 = 2
						If gad\Resizing = True
							app\Win32Mouse = IDCSizeNW
							
							gad\W = gad\OffsetX + app\MX
							gad\H = gad\OffsetY + app\MY
							If gad\W < MIN_WINDOW_WIDTH gad\W = MIN_WINDOW_WIDTH
							If gad\H < MIN_WINDOW_HEIGHT gad\H = MIN_WINDOW_HEIGHT
								
							FUI_BuildWindow gad
							FUI_UpdateAnchors Handle( gad )
						ElseIf gad\ResizingX = True
							app\Win32Mouse = IDCSizeWE
							
							gad\W = gad\OffsetX + app\MX
							If gad\W < MIN_WINDOW_WIDTH gad\W = MIN_WINDOW_WIDTH
							
							FUI_BuildWindow gad
							FUI_UpdateAnchors Handle( gad )
						ElseIf gad\ResizingY = True
							app\Win32Mouse = IDCSizeNS
							
							gad\H = gad\OffsetY + app\MY
							If gad\H < MIN_WINDOW_HEIGHT gad\H = MIN_WINDOW_HEIGHT
							
							FUI_BuildWindow gad
							FUI_UpdateAnchors Handle( gad )
						EndIf
					ElseIf app\MB1 = 0 Or app\MB1 = 3
						gad\Resizing = False
						gad\ResizingX = False
						gad\ResizingY = False
					EndIf
				EndIf
			EndIf
			
			If RENDER_MODE = 0
				;If gad\Minimised = False
					FUI_UpdateGadgets Handle( gad )
					FUI_UpdateMenuTitle gad
				;EndIf
			ElseIf RENDER_MODE = 1
				If gad\Minimised = False
					FUI_UpdateGadgets Handle( gad )
				EndIf
				
				For mnut.MenuTitle = Each MenuTitle
					If mnut\Owner = gad
						EntityParent mnut\Mesh, Null
						HideEntity mnut\Mesh
					EndIf
				Next
				
				RenderWorld
				HideEntity gad\Mesh
				HideEntity gad\MinMesh

				If gad\Minimised = False
					FUI_UpdateMenuTitle( gad )

					HideEntity app\Cam
					FUI_UpdateView( Handle( gad ) )
					ShowEntity app\Cam

					For mnut.MenuTitle = Each MenuTitle
						If mnut\Owner = gad
							ShowEntity mnut\Mesh
						EndIf
					Next
					RenderWorld
				EndIf

				For mnut.MenuTitle = Each MenuTitle
					If mnut\Owner = gad
						EntityParent mnut\Mesh, gad\MenuBar
					EndIf
				Next
			EndIf
		EndIf
		
		Count = Count - ORDER_GAP
		gad = Before gad
	Wend

End Function

Function FUI_UpdateMenuTitle( win.Window )
	
	X = win\X + WINDOW_BORDER + MENUBAR_X
	For gad.MenuTitle = Each MenuTitle
		If gad\Owner = win And win <> Null
			HideEntity gad\Mesh
			If win\Closed = False And win\Minimised = False
				
				Valid = True
				If gad\Hidden = True
					Valid = False
				EndIf
				
				If Valid = True
					gad\Order = win\Order-ORDER_GAP+1
					
					X = FUI_InternalX( gad\Mesh )
					Y = FUI_InternalY( gad\Mesh )
					
					ShowEntity gad\Mesh
					EntityOrder gad\Mesh, gad\Order
					
					gad\DX = X + MENUTITLE_DROPDOWN_X
					gad\DY = Y + gad\H + MENUTITLE_DROPDOWN_Y
					
					Valid = False
					If win = app\overWin And gad\Disabled = False And (win = app\modalWin Or app\modalWin = Null) And (win = app\modalWin Or app\modalWin = Null)
						Valid = True
					EndIf
					
					If FUI_MouseOver( X, Y, gad\W, gad\H ) = True And app\actComboBox = Null And Valid = True
						gad\State = 1
						If app\MB1 = 1
							If app\actMenuTitle = gad
								app\actMenuTitle = Null
								FUI_CreateEvent Handle( gad ), 0
							Else
								app\actMenuTitle = gad
								FUI_CreateEvent Handle( gad ), 1
							EndIf
						Else
							If app\actMenuTitle <> Null And app\actMenuTitle <> gad
								app\actMenuTitle = gad
							EndIf
						EndIf
					Else
						gad\State = 0
						If app\MB1 = 1
							If FUI_MouseOver( gad\DX, gad\DY, gad\DW + MENUDROPDOWN_PADDING*2, gad\DH + MENUDROPDOWN_PADDING*2 ) = False
								If app\actMenuTitle = gad And gad\overMenuItem = 0
									app\actMenuTitle = Null
									FUI_CreateEvent Handle( gad ), 0
								EndIf
							EndIf
						EndIf
					EndIf
					If app\actMenuTitle = gad
						ShowEntity gad\DropDown
						EntityOrder gad\DropDown, gad\Order-1
						If gad\Disabled = False
							tex.BBTexture = FUI_GadgetTexture( gad\Mesh )
							EntityTexture gad\Mesh, tex, 2
							FreeTexture tex
						EndIf
					Else
						gad\actMenuItem = Null
						HideEntity gad\DropDown
						If gad\Disabled = False
							tex = FUI_GadgetTexture( gad\Mesh )
							Select gad\State
								Case 0
									EntityTexture gad\Mesh, tex, 0
								Case 1
									EntityTexture gad\Mesh, tex, 1
							End Select
							FreeTexture tex
						EndIf
					EndIf
					gad\overMenuItem = 0
					FUI_UpdateMenuItem( Handle( gad ) )
					
					X = X + gad\W + MENUBAR_SPACING
				EndIf
			EndIf
		EndIf
	Next
	
End Function

Function FUI_UpdateMenuItem( ID )

	mnut.MenuTitle = Object.MenuTitle( ID )
	mnui.MenuItem = Object.MenuItem( ID )
	
	If mnut <> Null
		win.Window = mnut\Owner
		For gad.MenuItem = Each MenuItem
			If gad\Owner = mnut And mnut <> Null And gad\Parent = Null
				HideEntity gad\Mesh
				If gad\DropDown <> Null HideEntity gad\DropDown
				If app\actMenuTitle = mnut And gad\Hidden = False
					gad\Order = mnut\Order-2
					EntityOrder gad\Mesh, gad\Order
					ShowEntity gad\Mesh
					
					X = FUI_InternalX( gad\Mesh )
					Y = FUI_InternalY( gad\Mesh )
					
					gad\DX = X + gad\W + MENUITEM_DROPDOWN_X
					gad\DY = Y + MENUITEM_DROPDOWN_Y
						
					If FUI_MouseOver( X, Y, gad\W, gad\H ) = True And gad\Disabled = False
						mnut\overMenuItem = True
						If gad\Caption <> "-"
							If app\MB1 = 1
								mnut\actMenuItem = gad
								If gad\HasChildren = True
									FUI_CreateEvent Handle( gad ), 1
								EndIf
							ElseIf app\MB1 = 2
								mnut\actMenuItem = gad
							Else
								If mnut\actMenuItem <> gad
									mnut\actMenuItem = Null
								EndIf
								If gad\HasChildren = False
									If mnut\actMenuItem = gad
										mnut\actMenuItem = Null
										app\actMenuTitle = Null
										If gad\Checkable = True
											If gad\ID = 0
												gad\Checked = 1 - gad\Checked
												FUI_CreateEvent Handle( gad ), gad\Checked
											Else
												For mnui.MenuItem = Each MenuItem
													If mnui\ID = gad\ID And mnui <> gad
														mnui\Checked = False
													EndIf
												Next
												gad\Checked = True
												FUI_CreateEvent Handle( gad ), 1
											EndIf
										Else
											If gad\rf <> Null
												app\recentFile = gad\rf\FileName
											EndIf
											FUI_CreateEvent Handle( gad ), 1
										EndIf
									EndIf
								EndIf
							EndIf
						EndIf
					Else
						If FUI_MouseOver( gad\DX, gad\DY, gad\DW + MENUDROPDOWN_PADDING*2, gad\DH + MENUDROPDOWN_PADDING*2 ) = False
							If mnut\actMenuItem = gad And gad\HasChildren = False
								mnut\actMenuItem = Null
							EndIf
						Else
							mnut\overMenuItem = True
						EndIf
					EndIf
					If gad\Caption <> "-"
						If mnut\actMenuItem = gad
							If gad\DropDown <> Null
								ShowEntity gad\DropDown
								EntityOrder gad\DropDown, gad\Order-1
							EndIf
							If gad\Disabled = False
								FUI_SetTexture gad\Mesh, 2 + gad\Checked * 4
							EndIf
						Else
							gad\actMenuItem = Null
							If gad\DropDown <> Null
								HideEntity gad\DropDown
							EndIf
							If gad\Disabled = False
								tex.BBTexture = FUI_GadgetTexture( gad\Mesh )
								If FUI_MouseOver( X, Y, gad\W, gad\H ) = True And gad\Disabled = False
									EntityTexture gad\Mesh, tex, 1 + gad\Checked*4
								Else
									EntityTexture gad\Mesh, tex, 0 + gad\Checked*4
								EndIf
								FreeTexture tex
							EndIf
						EndIf
					EndIf
				Else
					gad\actMenuItem = Null
				EndIf
				gad\overMenuItem = Null
				FUI_UpdateMenuItem( Handle( gad ) )
			EndIf
		Next
	ElseIf mnui <> Null
		mnut.MenuTitle = mnui\Owner
		
		X = mnui\DX + MENUDROPDOWN_PADDING
		Y = mnui\DY + MENUDROPDOWN_PADDING
		win.Window = mnut\Owner
		For gad.MenuItem = Each MenuItem
			If gad\Parent = mnui And mnui <> Null
				If gad\DropDown <> Null HideEntity gad\DropDown
				HideEntity gad\Mesh
				Valid = False
				If mnui\Parent = Null
					If mnut\actMenuItem = mnui
						Valid = True
					EndIf
				Else
					If mnui\Parent\actMenuItem = mnui
						Valid = True
					EndIf
				EndIf
				If Valid = True
					gad\Order = mnui\Order-2
					EntityOrder gad\Mesh, gad\Order
					ShowEntity gad\Mesh
					
					If FUI_MouseOver( X, Y, gad\W, gad\H ) = True And gad\Disabled = False
						mnut\overMenuItem = True
						mnui\overMenuItem = gad
						If gad\Caption <> "-"
							If app\MB1 = 1
								If mnui\overMenuItem = gad
									mnui\actMenuItem = gad
									If gad\HasChildren = True
										FUI_CreateEvent Handle( gad ), "opened"
									EndIf
								EndIf
							ElseIf app\MB1 = 2
								mnui\actMenuItem = gad
							Else
								If mnui\actMenuItem <> gad
									mnui\actMenuItem = Null
								EndIf
								If gad\HasChildren = False
									If mnui\actMenuItem = gad
										mnut\actMenuItem = Null
										mnui\actMenuItem = Null
										app\actMenuTitle = Null
										If gad\Checkable = True
											If gad\ID = 0
												gad\Checked = 1 - gad\Checked
												FUI_CreateEvent Handle( gad ), gad\Checked
											Else
												For mnui2.MenuItem = Each MenuItem
													If mnui2\ID = gad\ID And mnui2 <> gad
														mnui2\Checked = False
													EndIf
												Next
												gad\Checked = True
												FUI_CreateEvent Handle( gad ), 1
											EndIf
										Else
											If gad\rf <> Null
												app\recentFile = gad\rf\FileName
											EndIf
											FUI_CreateEvent Handle( gad ), 1
										EndIf
									EndIf
								EndIf
							EndIf
						EndIf
					Else
						If FUI_MouseOver( gad\DX, gad\DY, gad\DW + MENUDROPDOWN_PADDING*2, gad\DH + MENUDROPDOWN_PADDING*2 ) = False
							If mnui\actMenuItem = gad And gad\HasChildren = False
								mnui\actMenuItem = Null
							EndIf
						Else
							mnut\overMenuItem = True
						EndIf
					EndIf
					If gad\Caption <> "-"
						gad\DX = X + gad\W + MENUITEM_DROPDOWN_X
						gad\DY = Y + MENUITEM_DROPDOWN_Y
						If mnui\actMenuItem = gad
							If gad\DropDown <> Null
								ShowEntity gad\DropDown
								EntityOrder gad\DropDown, gad\Order-1
							EndIf
							If gad\Disabled = False
								FUI_SetTexture gad\Mesh, 2 + gad\Checked * 4
							EndIf
						Else
							If gad\DropDown <> Null
								HideEntity gad\DropDown
							EndIf
							If gad\Disabled = False
								tex = FUI_GadgetTexture( gad\Mesh )
								If mnui\overMenuItem = gad
									EntityTexture gad\Mesh, tex, 1 + gad\Checked*4
								Else
									EntityTexture gad\Mesh, tex, 0 + gad\Checked*4
								EndIf
								FreeTexture tex
							EndIf
						EndIf
					EndIf
					
					Y = Y + gad\H + MENUDROPDOWN_SPACING
				Else
					gad\actMenuItem = Null
				EndIf
				gad\overMenuItem = Null
				FUI_UpdateMenuItem( Handle( gad ) )
			EndIf
		Next
	EndIf
	
End Function

Function FUI_UpdateContextMenu(  )

	For gad.ContextMenu = Each ContextMenu
		
		If app\MB2 = 1
			HideEntity gad\DropDown
			app\actContextMenu = Null
			gad\DX = app\MX
			gad\DY = app\MY
		EndIf
		
		If app\actContextMenu = gad
			
        	If gad\overMenuItem = Null
        		If app\MB1 = 1
        			HideEntity gad\DropDown
        			app\actContextMenu = Null
        		EndIf
        	EndIf
			
			X = gad\DX
			Y = gad\Y
			gad\Order = ORDER_CONTEXTMENU
			EntityOrder gad\DropDown, gad\Order
			
			gad\X = X
			gad\Y = Y
			
			gad\overMenuItem = Null
			FUI_UpdateContextMenuItem( Handle( gad ) )
		EndIf
	Next
	
End Function

Function FUI_UpdateContextMenuItem( ID )
	
	mnut.ContextMenu = Object.ContextMenu( ID )
	mnui.ContextMenuItem = Object.ContextMenuItem( ID )
	
	If mnut <> Null
		For gad.ContextMenuItem = Each ContextMenuItem
			If gad\Owner = mnut And mnut <> Null And gad\Parent = Null
				HideEntity gad\Mesh
				If gad\DropDown <> Null HideEntity gad\DropDown
				If app\actContextMenu = mnut And gad\Hidden = False
					gad\Order = mnut\Order-2
					EntityOrder gad\Mesh, gad\Order
					ShowEntity gad\Mesh
					
					X = FUI_InternalX( gad\Mesh )
					Y = FUI_InternalY( gad\Mesh )
					
					gad\DX = X + gad\W + MENUITEM_DROPDOWN_X
					gad\DY = Y + MENUITEM_DROPDOWN_Y
					
					If FUI_MouseOver( X, Y, gad\W, gad\H ) = True And gad\Disabled = False
						mnut\overMenuItem = gad
						If gad\Caption <> "-"
							If app\MB1 = 1
								If mnut\overMenuItem = gad
									mnut\actMenuItem = gad
									If gad\HasChildren = True
										FUI_CreateEvent Handle( gad ), 1
									EndIf
								EndIf
							ElseIf app\MB1 = 2
								mnut\actMenuItem = gad
							Else
								If mnut\actMenuItem <> gad
									mnut\actMenuItem = Null
								EndIf
								If gad\HasChildren = False
									If mnut\actMenuItem = gad
										mnut\actMenuItem = Null
										app\actContextMenu = Null
										HideEntity gad\Owner\DropDown
										If gad\Checkable = True
											If gad\ID = 0
												gad\Checked = 1 - gad\Checked
												FUI_CreateEvent Handle( gad ), gad\Checked
											Else
												For mnui.ContextMenuItem = Each ContextMenuItem
													If mnui\ID = gad\ID And mnui <> gad
														mnui\Checked = False
													EndIf
												Next
												gad\Checked = True
												FUI_CreateEvent Handle( gad ), 1
											EndIf
										Else
											If gad\rf <> Null
												app\recentFile = gad\rf\FileName
											EndIf
											FUI_CreateEvent Handle( gad ), 1
										EndIf
									EndIf
								EndIf
							EndIf
						EndIf
					Else
						If FUI_MouseOver( gad\DX, gad\DY, gad\DW + MENUDROPDOWN_PADDING*2, gad\DH + MENUDROPDOWN_PADDING*2 ) = False
							If mnut\actMenuItem = gad And gad\HasChildren = False
								mnut\actMenuItem = Null
							EndIf
						EndIf
					EndIf
					If gad\Caption <> "-"
						If mnut\actMenuItem = gad
							If gad\DropDown <> Null
								ShowEntity gad\DropDown
								EntityOrder gad\DropDown, gad\Order-1
							EndIf
							If gad\Disabled = False
								FUI_SetTexture gad\Mesh, 2 + gad\Checked * 4
							EndIf
						Else
							gad\actMenuItem = Null
							If gad\DropDown <> Null
								HideEntity gad\DropDown
							EndIf
							If gad\Disabled = False
								tex.BBTexture = FUI_GadgetTexture( gad\Mesh )
								If mnut\overMenuItem = gad
									EntityTexture gad\Mesh, tex, 1 + gad\Checked*4
								Else
									EntityTexture gad\Mesh, tex, 0 + gad\Checked*4
								EndIf
								FreeTexture tex
							EndIf
						EndIf
					EndIf
				Else
					gad\actMenuItem = Null
				EndIf
				gad\overMenuItem = Null
				FUI_UpdateContextMenuItem( Handle( gad ) )
			EndIf
		Next
	ElseIf mnui <> Null
		mnut.ContextMenu = mnui\Owner
		
		For gad.ContextMenuItem = Each ContextMenuItem
			If gad\Parent = mnui And mnui <> Null
				If gad\DropDown <> Null HideEntity gad\DropDown
				HideEntity gad\Mesh
				Valid = False
				If mnui\Parent = Null
					If mnut\actMenuItem = mnui
						Valid = True
					EndIf
				Else
					If mnui\Parent\actMenuItem = mnui
						Valid = True
					EndIf
				EndIf
				If Valid = True
					gad\Order = mnui\Order-2
					EntityOrder gad\Mesh, gad\Order
					ShowEntity gad\Mesh
					
					X = FUI_InternalX( gad\Mesh )
					Y = FUI_InternalY( gad\Mesh )
					
					gad\DX = X + gad\W + MENUITEM_DROPDOWN_X
					gad\DY = Y + MENUITEM_DROPDOWN_Y
					
					If FUI_MouseOver( X, Y, gad\W, gad\H ) = True And gad\Disabled = False
						mnut\overMenuItem = gad
						mnui\overMenuItem = gad
						If gad\Caption <> "-"
							If app\MB1 = 1
								If mnui\overMenuItem = gad
									mnui\actMenuItem = gad
									If gad\HasChildren = True
										FUI_CreateEvent Handle( gad ), "opened"
									EndIf
								EndIf
							ElseIf app\MB1 = 2
								mnui\actMenuItem = gad
							Else
								If mnui\actMenuItem <> gad
									mnui\actMenuItem = Null
								EndIf
								If gad\HasChildren = False
									If mnui\actMenuItem = gad
										mnut\actMenuItem = Null
										mnui\actMenuItem = Null
										app\actContextMenu = Null
										HideEntity gad\Owner\DropDown
										HideEntity mnui\DropDown
										FUI_SetTexture mnui\Mesh, 0
										If gad\Checkable = True
											If gad\ID = 0
												gad\Checked = 1 - gad\Checked
												FUI_CreateEvent Handle( gad ), gad\Checked
											Else
												For mnui2.ContextMenuItem = Each ContextMenuItem
													If mnui2\ID = gad\ID And mnui2 <> gad
														mnui2\Checked = False
													EndIf
												Next
												gad\Checked = True
												FUI_CreateEvent Handle( gad ), 1
											EndIf
										Else
											If gad\rf <> Null
												app\recentFile = gad\rf\FileName
											EndIf
											FUI_CreateEvent Handle( gad ), 1
										EndIf
									EndIf
								EndIf
							EndIf
						EndIf
					Else
						If mnui\actMenuItem = gad And gad\HasChildren = False
							mnui\actMenuItem = Null
						EndIf
					EndIf
					If gad\Caption <> "-"
						gad\DX = X + gad\W + MENUITEM_DROPDOWN_X
						gad\DY = Y + MENUITEM_DROPDOWN_Y
						If mnui\actMenuItem = gad
							If gad\DropDown <> Null
								ShowEntity gad\DropDown
								EntityOrder gad\DropDown, gad\Order-1
							EndIf
							If gad\Disabled = False
								FUI_SetTexture gad\Mesh, 2 + gad\Checked * 4
							EndIf
						Else
							If gad\DropDown <> Null
								HideEntity gad\DropDown
							EndIf
							If gad\Disabled = False
								tex = FUI_GadgetTexture( gad\Mesh )
								If mnui\overMenuItem = gad
									EntityTexture gad\Mesh, tex, 1 + gad\Checked*4
								Else
									EntityTexture gad\Mesh, tex, 0 + gad\Checked*4
								EndIf
								FreeTexture tex
							EndIf
						EndIf
					EndIf
					
					Y = Y + gad\H + MENUDROPDOWN_SPACING
				Else
					gad\actMenuItem = Null
				EndIf
				gad\overMenuItem = Null
				FUI_UpdateContextMenuItem( Handle( gad ) )
			EndIf
		Next
	EndIf
	
End Function

Function FUI_UpdateRegion( ID )
	
	win.Window = Object.Window( ID )
	reg.Region = Object.Region( ID )
	tabp.TabPage = Object.TabPage( ID )
	pan.Panel = Object.Panel( ID )
	grp.GroupBox = Object.GroupBox( ID )
	If reg <> Null
		win.Window = reg\Owner
		Order = reg\Order - 1
	ElseIf tabp <> Null
		win.Window = tabp\Owner\Owner
		Order = tabp\Owner\Order - 3
	ElseIf pan <> Null
		win.Window = pan\Owner
		Order = pan\Order - 2
	ElseIf grp <> Null
		win.Window = grp\Owner
		Order = grp\Order - 1
	ElseIf win <> Null
		Order = win\Order - 2
	EndIf
	
	For gad.Region = Each Region
		If win\Closed = False
			
			Valid = False
			If reg <> Null
				If gad\Parent = ID
					Valid = True
				EndIf
			ElseIf tabp <> Null
				If gad\Parent = ID
					If tabp\Owner\actTabPage = tabp
						Valid = True
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf pan <> Null
				If gad\Parent = ID
					If pan\Active = True
						Valid = True
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf grp <> Null
				If gad\Parent = ID
					Valid = True
					ShowEntity gad\Mesh
				EndIf
			Else
				If gad\Owner = win And gad\Parent = 0
					Valid = True
				EndIf
			EndIf
			
			If gad\Hidden = True
				Valid = False
			EndIf
			
			If Valid = True
				ShowEntity gad\Mesh
				
				gad\Order = Order
				EntityOrder gad\Mesh, gad\Order
				
				Valid = False
				If win = app\overWin
					Valid = True
				EndIf
				
				FUI_UpdateGadgets Handle( gad )
			EndIf
		EndIf
	Next
	
End Function

Function FUI_UpdateTab( ID )
	
	win.Window = Object.Window( ID )
	reg.Region = Object.Region( ID )
	tabp.TabPage = Object.TabPage( ID )
	pan.Panel = Object.Panel( ID )
	grp.GroupBox = Object.GroupBox( ID )
	If reg <> Null
		win.Window = reg\Owner
		Order = reg\Order - 1
	ElseIf tabp <> Null
		win.Window = tabp\Owner\Owner
		Order = tabp\Owner\Order - 3
	ElseIf pan <> Null
		win.Window = pan\Owner
		Order = pan\Order - 2
	ElseIf grp <> Null
		win.Window = grp\Owner
		Order = grp\Order - 1
	ElseIf win <> Null
		Order = win\Order - 2
	EndIf
	
	For gad.Tab = Each Tab
		If win\Closed = False
			
			Valid = False
			If reg <> Null
				If gad\Parent = ID
					Valid = True
				EndIf
			ElseIf tabp <> Null
				If gad\Parent = ID
					If tabp\Owner\actTabPage = tabp
						Valid = True
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf pan <> Null
				If gad\Parent = ID
					If pan\Active = True
						Valid = True
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf grp <> Null
				If gad\Parent = ID
					Valid = True
					ShowEntity gad\Mesh
				EndIf
			Else
				If gad\Owner = win And gad\Parent = 0
					Valid = True
				EndIf
			EndIf
			
			If gad\Hidden = True
				Valid = False
			EndIf
			
			If Valid = True
				ShowEntity gad\Mesh
				
				gad\Order = Order
				EntityOrder gad\Mesh, gad\Order
				
				Valid = False
				If win = app\overWin
					Valid = True
				EndIf
				
				X = FUI_InternalX( gad\Mesh )
				Y = FUI_InternalY( gad\Mesh )
				
				FUI_UpdateTabPage( Handle( gad ) )
				
				If FUI_MouseOver( X, Y, gad\W, gad\H ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					gad\State = 1
				Else
					gad\State = 0
				EndIf
			EndIf
		EndIf
	Next
	
End Function

Function FUI_UpdateTabPage( ID )
	
	Tab.Tab = Object.Tab( ID )
	If Tab <> Null
		win.Window = Tab\Owner
	EndIf
	
	Count = 1
	For gad.TabPage = Each TabPage
		If gad\Owner = Tab
			
			Valid = True
			If gad\Hidden = True
				Valid = False
			EndIf
			
			If Valid = True
				If gad = Tab\actTabPage
					EntityOrder gad\Mesh, Tab\Order-2
					EntityOrder gad\IconMesh, Tab\Order-3
				Else
					EntityOrder gad\Mesh, Tab\Order-1
					EntityOrder gad\IconMesh, Tab\Order-2
				EndIf
				
				Valid = False
				If win = app\overWin And gad\Disabled = False And (win = app\modalWin Or app\modalWin = Null)
					Valid = True
				EndIf
				
				X = FUI_InternalX( gad\Owner\Mesh ) + gad\X
				Y = FUI_InternalY( gad\Owner\Mesh )
				
				If FUI_MouseOver( X, Y, gad\W, TABPAGE_HEIGHT ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					gad\State = 1
					If app\MB1 = 1
						If Tab\actTabPage <> gad
							Tab\actTabPage = gad
							
							For tabp.TabPage = Each TabPage
								If tabp\Owner = Tab
									FUI_BuildTabPage tabp
								EndIf
							Next
							
							FUI_CreateEvent Handle( gad\Owner ), Count, Handle( gad )
							FUI_CreateEvent Handle( gad ), 1
						EndIf
					EndIf
				Else
					gad\State = 0
				EndIf			
				FUI_UpdateGadgets( Handle( gad ) )

				Count = Count + 1
			EndIf
		EndIf
	Next
	
End Function

Function FUI_UpdatePanel( ID )
	
	win.Window = Object.Window( ID )
	reg.Region = Object.Region( ID )
	tabp.TabPage = Object.TabPage( ID )
	pan.Panel = Object.Panel( ID )
	grp.GroupBox = Object.GroupBox( ID )
	If reg <> Null
		win.Window = reg\Owner
		Order = reg\Order - 1
	ElseIf tabp <> Null
		win.Window = tabp\Owner\Owner
		Order = tabp\Owner\Order - 3
	ElseIf pan <> Null
		win.Window = pan\Owner
		Order = pan\Order - 2
	ElseIf grp <> Null
		win.Window = grp\Owner
		Order = grp\Order - 1
	ElseIf win <> Null
		Order = win\Order - 2
	EndIf
	
	For gad.Panel = Each Panel
		If win\Closed = False And win\Minimised = False
			
			Valid = False
			If reg <> Null
				If gad\Parent = ID
					Valid = True
				EndIf
			ElseIf tabp <> Null
				If gad\Parent = ID
					If tabp\Owner\actTabPage = tabp
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf pan <> Null
				If gad\Parent = ID
					If pan\Active = True
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf grp <> Null
				If gad\Parent = ID
					Valid = True
					ShowEntity gad\Mesh
				EndIf
			Else
				If gad\Owner = win And gad\Parent = 0
					Valid = True
				EndIf
			EndIf
			
			If gad\Hidden = True
				HideEntity gad\Mesh
				Valid = False
			EndIf
			
			If Valid = True
				gad\Order = Order
				EntityOrder gad\Mesh, gad\Order
				EntityOrder gad\DropDown, gad\Order
				
				If gad\Active = True
					ShowEntity gad\DropDown
				Else
					HideEntity gad\DropDown
				EndIf
				
				Valid = False
				If win = app\overWin And gad\Disabled = False And (win = app\modalWin Or app\modalWin = Null)
					Valid = True
				EndIf
				
				X = FUI_InternalX( gad\Mesh )
				Y = FUI_InternalY( gad\Mesh )
				
				If FUI_MouseOver( X, Y, gad\W, PANEL_HEIGHT ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					If gad\State = 0
						gad\State = 1
						FUI_SetTexture gad\Mesh, 1+(gad\Active*3)
					EndIf
					If app\MB1 = 1
						If gad\Active = False
							;Open Panel
							FUI_OpenGadget Handle( gad )
						Else
							;Close Panel
							FUI_CloseGadget Handle( gad )
						EndIf
						FUI_SetTexture gad\Mesh, 1+(gad\Active*3)
						
						FUI_CreateEvent Handle( gad ), gad\Active
					EndIf
				Else
					If gad\State = 1
						gad\State = 0
						FUI_SetTexture gad\Mesh, 0+(gad\Active*3)
					EndIf
				EndIf
				
				FUI_UpdateGadgets( Handle( gad ) )
			EndIf
		EndIf
	Next
	
End Function

Function FUI_UpdateButton( ID )
	
	win.Window = Object.Window( ID )
	reg.Region = Object.Region( ID )
	tabp.TabPage = Object.TabPage( ID )
	pan.Panel = Object.Panel( ID )
	grp.GroupBox = Object.GroupBox( ID )
	If reg <> Null
		win.Window = reg\Owner
		Order = reg\Order - 1
	ElseIf tabp <> Null
		win.Window = tabp\Owner\Owner
		Order = tabp\Owner\Order - 3
	ElseIf pan <> Null
		win.Window = pan\Owner
		Order = pan\Order - 2
	ElseIf grp <> Null
		win.Window = grp\Owner
		Order = grp\Order - 1
	ElseIf win <> Null
		Order = win\Order - 2
	EndIf
	
	For gad.Button = Each Button
		If win\Closed = False And win\Minimised = False
			
			Valid = False
			If reg <> Null
				If gad\Parent = ID
					Valid = True
				EndIf
			ElseIf tabp <> Null
				If gad\Parent = ID
					If tabp\Owner\actTabPage = tabp
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf pan <> Null
				If gad\Parent = ID
					If pan\Active = True
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf grp <> Null
				If gad\Parent = ID
					Valid = True
					ShowEntity gad\Mesh
				EndIf
			Else
				If gad\Owner = win And gad\Parent = 0
					Valid = True
				EndIf
			EndIf
			
			If gad\Hidden = True
				HideEntity gad\Mesh
				Valid = False
			EndIf
			
			If Valid = True
				EntityOrder gad\Mesh, Order
				
				Valid = False
				If win = app\overWin And gad\Disabled = False And (win = app\modalWin Or app\modalWin = Null)
					Valid = True
				EndIf
				
				X = FUI_InternalX( gad\Mesh )
				Y = FUI_InternalY( gad\Mesh )
				
				If FUI_MouseOver( X, Y, gad\W, gad\H ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					If gad\State = 0
						If gad\ID = 0 Or gad\Active = False
							gad\State = 1
							FUI_SetTexture gad\Mesh, 1
						Else
							gad\Over = True
						EndIf
					EndIf
					If app\MB1 = 1
						If gad\ID = 0
							gad\Active = True
						Else
							If gad\Active = False
								gad\Active = True
								FUI_SetTexture gad\Mesh, 2
								
								For btn.Button = Each Button
									If btn\ID = gad\ID And btn <> gad
										btn\Active = False
										FUI_SetTexture btn\Mesh, 0
									EndIf
								Next
							Else
								gad\Active = False
								FUI_SetTexture gad\Mesh, 1
							EndIf
							
							FUI_CreateEvent Handle( gad ), gad\Active
						EndIf
					ElseIf app\MB1 = 2
						If gad\Active = True And gad\ID = 0
							If gad\State = 1
								FUI_SetTexture gad\Mesh, 2
							EndIf
							gad\State = 2
						EndIf
					Else
						If gad\Active = True And gad\ID = 0
							gad\Active = False
							gad\State = 1
							FUI_SetTexture gad\Mesh, 1
							FUI_CreateEvent Handle( gad ), True
						EndIf
					EndIf
				Else
					gad\Over = False
					
					If gad\ID = 0
						If gad\State > 0
							gad\State = 0
							FUI_SetTexture gad\Mesh, 0
						EndIf
						If app\MB1 = 0 Or app\MB1 = 3
							gad\Active = False
						EndIf
					Else
						gad\State = 0
						If gad\Active = False
							FUI_SetTexture gad\Mesh, 0
						EndIf
					EndIf
				EndIf
			EndIf
		EndIf
	Next
	
End Function

Function FUI_UpdateCheckBox( ID )
	
	win.Window = Object.Window( ID )
	reg.Region = Object.Region( ID )
	tabp.TabPage = Object.TabPage( ID )
	pan.Panel = Object.Panel( ID )
	grp.GroupBox = Object.GroupBox( ID )
	If reg <> Null
		win.Window = reg\Owner
		Order = reg\Order - 1
	ElseIf tabp <> Null
		win.Window = tabp\Owner\Owner
		Order = tabp\Owner\Order - 3
	ElseIf pan <> Null
		win.Window = pan\Owner
		Order = pan\Order - 2
	ElseIf grp <> Null
		win.Window = grp\Owner
		Order = grp\Order - 1
	ElseIf win <> Null
		Order = win\Order - 2
	EndIf
	
	For gad.CheckBox = Each CheckBox
		If win\Closed = False And win\Minimised = False
			
			Valid = False
			If reg <> Null
				If gad\Parent = ID
					Valid = True
				EndIf
			ElseIf tabp <> Null
				If gad\Parent = ID
					If tabp\Owner\actTabPage = tabp
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf pan <> Null
				If gad\Parent = ID
					If pan\Active = True
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf grp <> Null
				If gad\Parent = ID
					Valid = True
					ShowEntity gad\Mesh
				EndIf
			Else
				If gad\Owner = win And gad\Parent = 0
					Valid = True
				EndIf
			EndIf
			
			If gad\Hidden = True
				HideEntity gad\Mesh
				Valid = False
			EndIf
			
			If Valid = True
				EntityOrder gad\Mesh, Order
				
				Valid = False
				If win = app\overWin And gad\Disabled = False And (win = app\modalWin Or app\modalWin = Null)
					Valid = True
				EndIf
				
				X = FUI_InternalX( gad\Mesh )
				Y = FUI_InternalY( gad\Mesh )
				
				If FUI_MouseOver( X, Y, gad\W, gad\H ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					gad\State = 1
					If app\MB1 = 1
						gad\Active = True
					ElseIf app\MB1 = 2
						If gad\Active = True
							gad\State = 2
						EndIf
					Else
						If gad\Active = True
							gad\Active = False
							gad\Checked = 1 - gad\Checked
							FUI_CreateEvent Handle( gad ), gad\Checked
						EndIf
					EndIf
				Else
					gad\State = 0
					If app\MB1 = 0 Or app\MB1 = 3
						gad\Active = False
					EndIf
				EndIf
				If gad\Disabled = False
					FUI_SetTexture gad\Mesh, gad\State + gad\Checked * 4
				EndIf
			EndIf
		EndIf
	Next
	
End Function

Function FUI_UpdateComboBox( ID )
	
	win.Window = Object.Window( ID )
	reg.Region = Object.Region( ID )
	tabp.TabPage = Object.TabPage( ID )
	pan.Panel = Object.Panel( ID )
	grp.GroupBox = Object.GroupBox( ID )
	If reg <> Null
		win.Window = reg\Owner
		Order = reg\Order - 1
	ElseIf tabp <> Null
		win.Window = tabp\Owner\Owner
		Order = tabp\Owner\Order - 3
	ElseIf pan <> Null
		win.Window = pan\Owner
		Order = pan\Order - 2
	ElseIf grp <> Null
		win.Window = grp\Owner
		Order = grp\Order - 1
	ElseIf win <> Null
		Order = win\Order - 2
	EndIf
	
	For gad.ComboBox = Each ComboBox
		If win\Closed = False And win\Minimised = False
			
			Valid = False
			If reg <> Null
				If gad\Parent = ID
					Valid = True
				EndIf
			ElseIf tabp <> Null
				If gad\Parent = ID
					If tabp\Owner\actTabPage = tabp
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf pan <> Null
				If gad\Parent = ID
					If pan\Active = True
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf grp <> Null
				If gad\Parent = ID
					Valid = True
					ShowEntity gad\Mesh
				EndIf
			Else
				If gad\Owner = win And gad\Parent = 0
					Valid = True
				EndIf
			EndIf
			
			If gad\Hidden = True
				HideEntity gad\Mesh
				Valid = False
			EndIf
			
			If Valid = True
				gad\Order = win\Order-ORDER_GAP+1
				
				EntityOrder gad\Mesh, gad\Order
				EntityOrder gad\DropDown, gad\Order-1
				
				Valid = False
				If win = app\overWin And gad\Disabled = False And (win = app\modalWin Or app\modalWin = Null)
					Valid = True
				EndIf
				
				X = FUI_InternalX( gad\Mesh )
				Y = FUI_InternalY( gad\Mesh )
				
				If FUI_MouseOver( X, Y, gad\W, gad\H ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					If gad\State = 0
						gad\State = 1
						If app\actComboBox <> gad
							FUI_SetTexture gad\Mesh, 1
						EndIf
					EndIf
					If app\MB1 = 1
						If app\actComboBox <> gad
							app\actComboBox = gad
							FUI_SetTexture gad\Mesh, 2
							
							ShowEntity gad\DropDown
						Else
							app\actComboBox = Null
							FUI_SetTexture gad\Mesh, 1
							
							HideEntity gad\DropDown
						EndIf
					EndIf
				Else
					If gad\State = 1
						gad\State = 0
						If app\actComboBox <> gad
							FUI_SetTexture gad\Mesh, 0
						EndIf
					EndIf
					
					If FUI_MouseOver( FUI_InternalX( gad\DropDown ), FUI_InternalY( gad\DropDown ), gad\DW, gad\DH+COMBOBOX_PADDING*2 ) = False And Valid = True
						If app\MB1 = 1
							If app\actComboBox = gad
								app\actComboBox = Null
								FUI_SetTexture gad\Mesh, 0
								
								HideEntity gad\DropDown
							EndIf
						EndIf
					EndIf
				EndIf
				
				CW = gad\DW
				
				If app\actComboBox = gad
					If gad\DispItems > 0
						gad\DW = gad\DW - gad\VScroll\H
						gad\VScroll\Max = gad\CH - gad\DH
						gad\SOY = -FUI_UpdateGadgetVScroll( gad\VScroll, gad\Order - 3 )
					Else
						gad\SOY = 0
					EndIf
					
					FUI_UpdateComboBoxItem gad
				EndIf
				
				gad\DW = CW
			EndIf
		EndIf
	Next
	
End Function

Function FUI_UpdateComboBoxItem( cbo.ComboBox )
	
	Count = 1
	For gad.ComboBoxItem = Each ComboBoxItem
		If gad\Owner = cbo
			Valid = True
			If gad\Hidden = True Or gad\Y + cbo\SOY + COMBOBOXITEM_HEIGHT > cbo\DH Or gad\Y + cbo\SOY < 0
				HideEntity gad\Mesh
				Valid = False
			EndIf
			
			If Valid = True
				ShowEntity gad\Mesh
				EntityOrder gad\Mesh, cbo\Order-2
				
				Valid = False
				If gad\Disabled = False
					Valid = True
				EndIf
				
				FUI_SetChildPosition gad\Mesh, COMBOBOX_PADDING, COMBOBOX_PADDING + gad\Y + cbo\SOY
				
				X = FUI_InternalX( gad\Mesh )
				Y = FUI_InternalY( gad\Mesh )
				
				If gad\Caption <> "-"
					If FUI_MouseOver( X, Y, cbo\DW-COMBOBOX_PADDING*2, COMBOBOXITEM_HEIGHT ) = True And app\actMenuTitle = Null And Valid = True
						If gad\State = 0
							gad\State = 1
							FUI_SetTexture gad\Mesh, 1
						EndIf
						If app\MB1 = 1
							cbo\selItem = gad
							FUI_SetTexture gad\Mesh, 2
						ElseIf app\MB1 = 2
							If cbo\selItem = gad
								gad\State = 2
							ElseIf cbo\selItem <> Null
								cbo\selItem = gad
								
								gad\State = 2
							EndIf
						Else
							If cbo\selItem = gad
								cbo\selItem = Null
								gad\State = 1
								
								FUI_SetTexture gad\Mesh, 1
								
								app\actComboBox = Null
								
								FUI_SetTexture gad\Owner\Mesh, 0
								HideEntity gad\Owner\DropDown
								
								gad\Owner\actItem = gad
								FUI_BuildComboBox gad\Owner, 0
								
								FUI_CreateEvent Handle( gad\Owner ), Count, Handle( gad )
								FUI_CreateEvent Handle( gad ), True
							EndIf
						EndIf
					Else
						If gad\State > 0
							gad\State = 0
							FUI_SetTexture gad\Mesh, 0
						EndIf
						If app\MB1 = 0 Or app\MB1 = 3
							If cbo\selItem = gad
								cbo\selItem = Null
								app\actComboBox = Null
								
								FUI_SetTexture gad\Owner\Mesh, 0
								HideEntity gad\Owner\DropDown
							EndIf
						EndIf
					EndIf
					
					Count = Count + 1
				EndIf
			EndIf
		EndIf
	Next
	
End Function

Function FUI_UpdateGroupBox( ID )
	
	win.Window = Object.Window( ID )
	reg.Region = Object.Region( ID )
	tabp.TabPage = Object.TabPage( ID )
	pan.Panel = Object.Panel( ID )
	grp.GroupBox = Object.GroupBox( ID )
	If reg <> Null
		win.Window = reg\Owner
		Order = reg\Order - 1
	ElseIf tabp <> Null
		win.Window = tabp\Owner\Owner
		Order = tabp\Owner\Order - 3
	ElseIf pan <> Null
		win.Window = pan\Owner
		Order = pan\Order - 2
	ElseIf grp <> Null
		win.Window = grp\Owner
		Order = grp\Order - 1
	ElseIf win <> Null
		Order = win\Order - 2
	EndIf
	
	For gad.GroupBox = Each GroupBox
		If win\Closed = False
			
			Valid = False
			If reg <> Null
				If gad\Parent = ID
					Valid = True
				EndIf
			ElseIf tabp <> Null
				If gad\Parent = ID
					If tabp\Owner\actTabPage = tabp
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf pan <> Null
				If gad\Parent = ID
					If pan\Active = True
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf grp <> Null
				If gad\Parent = ID
					Valid = True
					ShowEntity gad\Mesh
				EndIf
			Else
				If gad\Owner = win And gad\Parent = 0
					Valid = True
				EndIf
			EndIf
			
			If gad\Hidden = True
				HideEntity gad\Mesh
				Valid = False
			EndIf
			
			If Valid = True
				gad\Order = Order
				
				EntityOrder gad\Mesh, gad\Order
				EntityOrder gad\MeshText, gad\Order
				
				Valid = False
				If win = app\overWin And gad\Disabled = False And (win = app\modalWin Or app\modalWin = Null)
					Valid = True
				EndIf
				
				X = FUI_InternalX( gad\Mesh )
				Y = FUI_InternalY( gad\Mesh )
				
				If FUI_MouseOver( X, Y, gad\W, gad\H ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					gad\State = 1
				Else
					gad\State = 0
				EndIf
				
				FUI_UpdateGadgets Handle( gad )
			EndIf
		EndIf
	Next
	
End Function

Function FUI_UpdateImageBox( ID )
	
	win.Window = Object.Window( ID )
	reg.Region = Object.Region( ID )
	tabp.TabPage = Object.TabPage( ID )
	pan.Panel = Object.Panel( ID )
	grp.GroupBox = Object.GroupBox( ID )
	If reg <> Null
		win.Window = reg\Owner
		Order = reg\Order - 1
	ElseIf tabp <> Null
		win.Window = tabp\Owner\Owner
		Order = tabp\Owner\Order - 3
	ElseIf pan <> Null
		win.Window = pan\Owner
		Order = pan\Order - 2
	ElseIf grp <> Null
		win.Window = grp\Owner
		Order = grp\Order - 1
	ElseIf win <> Null
		Order = win\Order - 2
	EndIf
	
	For gad.ImageBox = Each ImageBox
		If win\Closed = False And win\Minimised = False
			
			Valid = False
			If reg <> Null
				If gad\Parent = ID
					Valid = True
				EndIf
			ElseIf tabp <> Null
				If gad\Parent = ID
					If tabp\Owner\actTabPage = tabp
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf pan <> Null
				If gad\Parent = ID
					If pan\Active = True
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf grp <> Null
				If gad\Parent = ID
					Valid = True
					ShowEntity gad\Mesh
				EndIf
			Else
				If gad\Owner = win And gad\Parent = 0
					Valid = True
				EndIf
			EndIf
			
			If gad\Hidden = True
				HideEntity gad\Mesh
				Valid = False
			EndIf
			
			If Valid = True
				EntityOrder gad\Mesh, Order
				
				Valid = False
				If win = app\overWin
					Valid = True
				EndIf
				
				X = FUI_InternalX( gad\Mesh )
				Y = FUI_InternalY( gad\Mesh )
				
				If FUI_MouseOver( X, Y, gad\W, gad\H ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					gad\State = 1
				Else
					gad\State = 0
				EndIf
			EndIf
		EndIf
	Next
	
End Function

Function FUI_UpdateLabel( ID )
	
	win.Window = Object.Window( ID )
	reg.Region = Object.Region( ID )
	tabp.TabPage = Object.TabPage( ID )
	pan.Panel = Object.Panel( ID )
	grp.GroupBox = Object.GroupBox( ID )
	If reg <> Null
		win.Window = reg\Owner
		Order = reg\Order - 1
	ElseIf tabp <> Null
		win.Window = tabp\Owner\Owner
		Order = tabp\Owner\Order - 3
	ElseIf pan <> Null
		win.Window = pan\Owner
		Order = pan\Order - 2
	ElseIf grp <> Null
		win.Window = grp\Owner
		Order = grp\Order - 1
	ElseIf win <> Null
		Order = win\Order - 2
	EndIf
	
	SetFont app\fntGadget
	For gad.Label = Each Label
		If win\Closed = False And win\Minimised = False
			
			Valid = False
			If reg <> Null
				If gad\Parent = ID
					Valid = True
				EndIf
			ElseIf tabp <> Null
				If gad\Parent = ID
					If tabp\Owner\actTabPage = tabp
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf pan <> Null
				If gad\Parent = ID
					If pan\Active = True
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf grp <> Null
				If gad\Parent = ID
					Valid = True
					ShowEntity gad\Mesh
				EndIf
			Else
				If gad\Owner = win And gad\Parent = 0
					Valid = True
				EndIf
			EndIf
			
			If gad\Hidden = True
				HideEntity gad\Mesh
				Valid = False
			EndIf
			
			If Valid = True
				EntityOrder gad\Mesh, Order
				
				Valid = False
				If win = app\overWin
					Valid = True
				EndIf
				
				X = FUI_InternalX( gad\Mesh )
				Y = FUI_InternalY( gad\Mesh )
				
				If gad\Align = ALIGN_RIGHT
					X = X - gad\W
				ElseIf gad\Align = ALIGN_CENTER
					X = X - gad\W / 2
				EndIf
				
				If FUI_MouseOver( X, Y, gad\W, gad\H ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					gad\State = 1
				Else
					gad\State = 0
				EndIf
			EndIf
		EndIf
	Next
	
End Function

Function FUI_UpdateListBox( ID )
	
	win.Window = Object.Window( ID )
	reg.Region = Object.Region( ID )
	tabp.TabPage = Object.TabPage( ID )
	pan.Panel = Object.Panel( ID )
	grp.GroupBox = Object.GroupBox( ID )
	If reg <> Null
		win.Window = reg\Owner
		Order = reg\Order - 1
	ElseIf tabp <> Null
		win.Window = tabp\Owner\Owner
		Order = tabp\Owner\Order - 3
	ElseIf pan <> Null
		win.Window = pan\Owner
		Order = pan\Order - 2
	ElseIf grp <> Null
		win.Window = grp\Owner
		Order = grp\Order - 1
	ElseIf win <> Null
		Order = win\Order - 2
	EndIf
	
	For gad.ListBox = Each ListBox
		If win\Closed = False And win\Minimised = False
			
			Valid = False
			If reg <> Null
				If gad\Parent = ID
					Valid = True
				EndIf
			ElseIf tabp <> Null
				If gad\Parent = ID
					If tabp\Owner\actTabPage = tabp
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf pan <> Null
				If gad\Parent = ID
					If pan\Active = True
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf grp <> Null
				If gad\Parent = ID
					Valid = True
					ShowEntity gad\Mesh
				EndIf
			Else
				If gad\Owner = win And gad\Parent = 0
					Valid = True
				EndIf
			EndIf
			
			If gad\Hidden = True
				HideEntity gad\Mesh
				Valid = False
			EndIf
			
			X = FUI_InternalX( gad\Mesh )
			Y = FUI_InternalY( gad\Mesh )
			
			If Valid = True
				HideEntity gad\VScroll\Mesh
				
				gad\Order = Order
				EntityOrder gad\Mesh, gad\Order
				
				Valid = False
				If win = app\overWin And gad\Disabled = False And (win = app\modalWin Or app\modalWin = Null)
					Valid = True
				EndIf
				
				gad\overItem = Null
				Count = FUI_UpdateListBoxItem( Handle( gad ) )
				
				gad\DW = gad\W - LISTBOX_PADDING*2
				gad\DH = gad\H - LISTBOX_PADDING*2
				
				If Count * LISTBOXITEM_HEIGHT > gad\DH
					ShowEntity gad\VScroll\Mesh
					
					gad\DW = gad\DW - gad\VScroll\H
					;gad\VScroll\X = gad\W - 1 - gad\VScroll\H
					;gad\VScroll\Y = 1
					gad\VScroll\Max = Count * LISTBOXITEM_HEIGHT - gad\DH
					gad\SOY = -FUI_UpdateGadgetVScroll( gad\VScroll, gad\Order - 3 )
				Else
					gad\SOY = 0
				EndIf
				
				If FUI_MouseOver( X, Y, gad\DW, gad\DH ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					gad\State = 1
					If app\MB1 = 1
						If gad\overItem = Null
							If gad\ForceSel = False
								If gad\MultiSel = False
									For lsti.ListBoxItem = Each ListBoxItem
										If lsti\Owner = gad
											lsti\Active = False
											tex.BBTexture = FUI_GadgetTexture( lsti\Mesh )
											EntityTexture lsti\Mesh, tex, lsti\Active*2
											FreeTexture tex
										EndIf
									Next
								Else
									If KeyDown( 29 ) = False And KeyDown( 157 ) = False And KeyDown( 56 ) = False And KeyDown( 184 ) = False
										For lsti.ListBoxItem = Each ListBoxItem
											If lsti\Owner = gad
												lsti\Active = False
												tex = FUI_GadgetTexture( lsti\Mesh )
												EntityTexture lsti\Mesh, tex, lsti\Active*2
												FreeTexture tex
											EndIf
										Next
									EndIf
								EndIf
							EndIf
						EndIf
					EndIf
				Else
					gad\State = 0
				EndIf
			EndIf
		Else
			HideEntity gad\VScroll\Mesh
		EndIf
	Next
	
End Function

Function FUI_UpdateListBoxItem( ID )
	
	lst.ListBox = Object.ListBox( ID )
	If lst <> Null
		win.Window = lst\Owner
	EndIf
	
	Index = 0
	For gad.ListBoxItem = Each ListBoxItem
		If gad\Owner = lst
			
			HideEntity gad\Mesh
			
			Valid = True
			If gad\Hidden = True
				Valid = False
			EndIf
			
			FUI_SetChildPosition gad\Mesh, LISTBOX_PADDING, LISTBOX_PADDING + gad\Y + lst\SOY
			
			X = FUI_InternalX( gad\Mesh )
			Y = FUI_InternalY( gad\Mesh )
			
			If gad\Y + LISTBOXITEM_HEIGHT + lst\SOY > lst\H Or gad\Y + lst\SOY < 0
				If Valid = True
					Valid = False
					Index = Index + 1
				EndIf
			Else
				Index = Index + 1
			EndIf
			
			If Valid = True
				
				ShowEntity gad\Mesh
				EntityOrder gad\Mesh, lst\Order-2
				
				Valid = False
				If win = app\overWin And gad\Disabled = False And gad\Owner\Disabled = False And (win = app\modalWin Or app\modalWin = Null)
					Valid = True
				EndIf
				
				If FUI_MouseOver( X, Y, lst\DW, LISTBOXITEM_HEIGHT ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					gad\State = 1
					lst\overItem = gad
					If app\MB1 = 1
						If lst\MultiSel = False
							If lst\ForceSel = True
								gad\Active = True
							Else
								gad\Active = 1 - gad\Active
							EndIf
							For lsti.ListBoxItem = Each ListBoxItem
								If lsti\Owner = lst And lsti <> gad
									lsti\Active = False
									tex.BBTexture = FUI_GadgetTexture( lsti\Mesh )
									EntityTexture lsti\Mesh, tex, lsti\Active*2
									FreeTexture tex
								EndIf
							Next
							tex = FUI_GadgetTexture( gad\Mesh )
							EntityTexture gad\Mesh, tex, gad\Active*2
							FreeTexture tex
							
							FUI_CreateEvent Handle( lst ), Index, 0, 0, Handle( gad )
							FUI_CreateEvent Handle( gad ), gad\Active
						Else
							If KeyDown( 56 ) = True Or KeyDown( 184 ) = True
								If lst\ForceSel = False
									gad\Active = False
									FUI_CreateEvent Handle( lst ), Index, 0, 0, Handle( gad )
									FUI_CreateEvent Handle( gad ), gad\Active
								Else
									Alt = False
									For lsti.ListBoxItem = Each ListBoxItem
										If lsti\Owner = lst And lsti <> gad
											If lsti\Active = True
												Alt = True
												Exit
											EndIf
										EndIf
									Next
									If Alt = True
										gad\Active = False
										FUI_CreateEvent Handle( lst ), Index, 0, 0, Handle( gad )
										FUI_CreateEvent Handle( gad ), gad\Active
									EndIf
								EndIf
							ElseIf KeyDown( 29 ) = True Or KeyDown( 157 ) = True
								If gad\Active = False
									gad\Active = True
									FUI_CreateEvent Handle( lst ), Index, 0, 0, Handle( gad )
									FUI_CreateEvent Handle( gad ), gad\Active
								Else
									If lst\ForceSel = False
										gad\Active = False
										FUI_CreateEvent Handle( lst ), Index, 0, 0, Handle( gad )
										FUI_CreateEvent Handle( gad ), gad\Active
									Else
										Ctrl = False
										For lsti.ListBoxItem = Each ListBoxItem
											If lsti\Owner = lst And lsti <> gad
												If lsti\Active = True
													Ctrl = True
													Exit
												EndIf
											EndIf
										Next
										If Ctrl = True
											gad\Active = False
											FUI_CreateEvent Handle( lst ), Index, 0, 0, Handle( gad )
											FUI_CreateEvent Handle( gad ), gad\Active
										EndIf
									EndIf
								EndIf
							Else
								If lst\ForceSel = True
									gad\Active = True
								Else
									gad\Active = 1 - gad\Active
								EndIf
								FUI_CreateEvent Handle( lst ), Index, 0, 0, Handle( gad )
								FUI_CreateEvent Handle( gad ), gad\Active
							EndIf
							If KeyDown( 29 ) = False And KeyDown( 157 ) = False And KeyDown( 56 ) = False And KeyDown( 184 ) = False
								For lsti.ListBoxItem = Each ListBoxItem
									If lsti\Owner = lst And lsti <> gad
										lsti\Active = False
										tex = FUI_GadgetTexture( lsti\Mesh )
										EntityTexture lsti\Mesh, tex, lsti\Active*2
										FreeTexture tex
									EndIf
								Next
							EndIf
							tex = FUI_GadgetTexture( gad\Mesh )
							EntityTexture gad\Mesh, tex, gad\Active*2
							FreeTexture tex
						EndIf
					EndIf
				Else
					gad\State = 0
				EndIf
			EndIf
		EndIf
	Next
	
	Return Index
	
End Function

Function FUI_UpdateProgressBar( ID )
	
	win.Window = Object.Window( ID )
	reg.Region = Object.Region( ID )
	tabp.TabPage = Object.TabPage( ID )
	pan.Panel = Object.Panel( ID )
	grp.GroupBox = Object.GroupBox( ID )
	If reg <> Null
		win.Window = reg\Owner
		Order = reg\Order - 1
	ElseIf tabp <> Null
		win.Window = tabp\Owner\Owner
		Order = tabp\Owner\Order - 3
	ElseIf pan <> Null
		win.Window = pan\Owner
		Order = pan\Order - 2
	ElseIf grp <> Null
		win.Window = grp\Owner
		Order = grp\Order - 1
	ElseIf win <> Null
		Order = win\Order - 2
	EndIf
	
	For gad.ProgressBar = Each ProgressBar
		If win\Closed = False And win\Minimised = False
			
			Valid = False
			If reg <> Null
				If gad\Parent = ID
					Valid = True
				EndIf
			ElseIf tabp <> Null
				If gad\Parent = ID
					If tabp\Owner\actTabPage = tabp
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf pan <> Null
				If gad\Parent = ID
					If pan\Active = True
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf grp <> Null
				If gad\Parent = ID
					Valid = True
					ShowEntity gad\Mesh
				EndIf
			Else
				If gad\Owner = win And gad\Parent = 0
					Valid = True
				EndIf
			EndIf
			
			If gad\Hidden = True
				HideEntity gad\Mesh
				Valid = False
			EndIf
			
			If Valid = True
				EntityOrder gad\Mesh, Order
				EntityOrder gad\Bar, Order-1
				EntityOrder gad\MeshText, Order-2
				
				X = FUI_InternalX( gad\Mesh )
				Y = FUI_InternalY( gad\Mesh )
				
				If FUI_MouseOver( X, Y, gad\W, gad\H ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					gad\State = 1
				Else
					gad\State = 0
				EndIf
			EndIf
		EndIf
	Next
	
End Function

Function FUI_UpdateRadio( ID )
	
	win.Window = Object.Window( ID )
	reg.Region = Object.Region( ID )
	tabp.TabPage = Object.TabPage( ID )
	pan.Panel = Object.Panel( ID )
	grp.GroupBox = Object.GroupBox( ID )
	If reg <> Null
		win.Window = reg\Owner
		Order = reg\Order - 1
	ElseIf tabp <> Null
		win.Window = tabp\Owner\Owner
		Order = tabp\Owner\Order - 3
	ElseIf pan <> Null
		win.Window = pan\Owner
		Order = pan\Order - 2
	ElseIf grp <> Null
		win.Window = grp\Owner
		Order = grp\Order - 1
	ElseIf win <> Null
		Order = win\Order - 2
	EndIf
	
	For gad.Radio = Each Radio
		If win\Closed = False And win\Minimised = False
			
			Valid = False
			If reg <> Null
				If gad\Parent = ID
					Valid = True
				EndIf
			ElseIf tabp <> Null
				If gad\Parent = ID
					If tabp\Owner\actTabPage = tabp
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf pan <> Null
				If gad\Parent = ID
					If pan\Active = True
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf grp <> Null
				If gad\Parent = ID
					Valid = True
					ShowEntity gad\Mesh
				EndIf
			Else
				If gad\Owner = win And gad\Parent = 0
					Valid = True
				EndIf
			EndIf
			
			If gad\Hidden = True
				HideEntity gad\Mesh
				Valid = False
			EndIf
			
			If Valid = True
				EntityOrder gad\Mesh, Order
				
				Valid = False
				If win = app\overWin And gad\Disabled = False And (win = app\modalWin Or app\modalWin = Null)
					Valid = True
				EndIf
				
				X = FUI_InternalX( gad\Mesh )
				Y = FUI_InternalY( gad\Mesh )
				
				If FUI_MouseOver( X, Y, gad\W, gad\H ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					gad\State = 1
					If app\MB1 = 1
						gad\Active = True
					ElseIf app\MB1 = 2
						If gad\Active = True
							gad\State = 2
						EndIf
					Else
						If gad\Active = True
							gad\Active = False
							gad\Checked = True
							For rad.Radio = Each Radio
								If rad\ID = gad\ID And rad <> gad
									rad\Checked = False
									If rad\Disabled = True
										tex.BBtexture = FUI_GadgetTexture( rad\Mesh )
										EntityTexture rad\Mesh, tex, 3 + rad\Checked * 4
										FreeTexture tex
									EndIf
								EndIf
							Next
							FUI_CreateEvent Handle( gad ), gad\Checked
						EndIf
					EndIf
				Else
					gad\State = 0
					If app\MB1 = 0 Or app\MB1 = 3
						gad\Active = False
					EndIf
				EndIf
				
				If gad\Disabled = False
					FUI_SetTexture gad\Mesh, gad\State + gad\Checked * 4
				EndIf
			EndIf
		EndIf
	Next
	
End Function

Function FUI_UpdateScrollBar( ID )
	
	win.Window = Object.Window( ID )
	reg.Region = Object.Region( ID )
	tabp.TabPage = Object.TabPage( ID )
	pan.Panel = Object.Panel( ID )
	grp.GroupBox = Object.GroupBox( ID )
	If reg <> Null
		win.Window = reg\Owner
		Order = reg\Order - 1
	ElseIf tabp <> Null
		win.Window = tabp\Owner\Owner
		Order = tabp\Owner\Order - 3
	ElseIf pan <> Null
		win.Window = pan\Owner
		Order = pan\Order - 2
	ElseIf grp <> Null
		win.Window = grp\Owner
		Order = grp\Order - 1
	ElseIf win <> Null
		Order = win\Order - 2
	EndIf
	
	For gad.ScrollBar = Each ScrollBar
		If win\Closed = False And win\Minimised = False
			
			Valid = False
			If reg <> Null
				If gad\Parent = ID
					Valid = True
				EndIf
			ElseIf tabp <> Null
				If gad\Parent = ID
					If tabp\Owner\actTabPage = tabp
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf pan <> Null
				If gad\Parent = ID
					If pan\Active = True
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf grp <> Null
				If gad\Parent = ID
					Valid = True
					ShowEntity gad\Mesh
				EndIf
			Else
				If gad\Owner = win And gad\Parent = 0
					Valid = True
				EndIf
			EndIf
			
			If gad\Hidden = True
				HideEntity gad\Mesh
				Valid = False
			EndIf
			
			If Valid = True
				EntityOrder gad\Mesh, Order
				EntityOrder gad\Btn1, Order-1
				EntityOrder gad\Btn2, Order-1
				EntityOrder gad\ScrollBar, Order-1
				
				Valid = False
				If win = app\overWin And gad\Disabled = False And (win = app\modalWin Or app\modalWin = Null)
					Valid = True
				EndIf
				
				X = FUI_InternalX( gad\Mesh )
				Y = FUI_InternalY( gad\Mesh )
				
				If gad\Direction = DIR_VERTICAL
					W = gad\H
					H = gad\W
				Else
					W = gad\W
					H = gad\H
				EndIf
				
				If FUI_MouseOver( X, Y, W, H ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					gad\State = 1
				Else
					gad\State = 0
				EndIf
				
				;Button One
				BX = X + 1
				BY = Y + 1
				BW = gad\H - 2
				BH = gad\H - 2
				If FUI_MouseOver( BX, BY, BW, BH ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					gad\B1State = 1
					If app\MB1 = 1
						gad\B1Active = True
					ElseIf app\MB1 = 2
						If gad\B1Active = True
							gad\B1State = 2
							If gad\SX > 0
								gad\SX = gad\SX - 1
							EndIf
						EndIf
					Else
						If gad\B1Active = True
							gad\B1Active = False
						EndIf
					EndIf
				Else
					gad\B1State = 0
					If app\MB1 = 0 Or app\MB1 = 3
						gad\B1Active = False
					EndIf
				EndIf
				
				If gad\Disabled = False
					tex.BBtexture = FUI_GadgetTexture( gad\Btn1 )
					EntityTexture gad\Btn1, tex, gad\B1State
					FreeTexture tex
				EndIf
				
				;Scroll Bar
				If gad\Direction = DIR_VERTICAL
					SX = X + 1
					SY = Y + 1 + BH + 1 + gad\SX
					SW = gad\SH
					SH = gad\SW
				Else
					SX = X + 1 + BW + 1 + gad\SX
					SY = Y + 1
					SW = gad\SW
					SH = gad\SH
				EndIf
				
				If app\MB1 = 1
					If FUI_MouseOver( SX, SY, SW, SH ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
						gad\SActive = True
						gad\OffX = gad\SX - app\MX
						gad\OffY = gad\SX - app\MY
					EndIf
				ElseIf app\MB1 = 2
					If gad\SActive = True
						If gad\Direction = DIR_VERTICAL
							gad\SX = app\MY + gad\OffY
						Else
							gad\SX = app\MX + gad\OffX
						EndIf
						If gad\SX < 0 gad\SX = 0
						If gad\SX > gad\W - BW*2 - 4 - gad\SW gad\SX = gad\W - BW*2 - 4 - gad\SW
						
						gad\Value = FUI_Interpolate( gad\SX, 0, gad\W - BW*2 - 4 - gad\SW, gad\Min, gad\Max )
						FUI_CreateEvent Handle( gad ), gad\Value
					EndIf
				Else
					gad\SActive = False
				EndIf
				
				;Button Two
				If gad\Direction = DIR_VERTICAL
					BX = X + 1
					BY = Y + gad\W - 1 - BH
				Else
					BX = X + gad\W - 1 - BW
					BY = Y + 1
				EndIf
				If FUI_MouseOver( BX, BY, BW, BH ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					gad\B2State = 1
					If app\MB1 = 1
						gad\B2Active = True
					ElseIf app\MB1 = 2
						If gad\B2Active = True
							gad\B2State = 2
							If gad\SX < gad\W - BW*2 - 4 - gad\SW
								gad\SX = gad\SX + 1
							EndIf
						EndIf
					Else
						If gad\B2Active = True
							gad\B2Active = False
						EndIf
					EndIf
				Else
					gad\B2State = 0
					If app\MB1 = 0 Or app\MB1 = 3
						gad\B2Active = False
					EndIf
				EndIf
				
				If gad\Disabled = False
					tex = FUI_GadgetTexture( gad\Btn2 )
					EntityTexture gad\Btn2, tex, gad\B2State
					FreeTexture tex
				EndIf
				
				If gad\Direction = DIR_VERTICAL
					PositionEntity gad\ScrollBar, 1, - 2 - BW - gad\SX, 0.0
				Else
					PositionEntity gad\ScrollBar, 2 + BW + gad\SX, - 1, 0.0
				EndIf
			EndIf
		EndIf
	Next
	
End Function

Function FUI_UpdateSlider( ID )
	
	win.Window = Object.Window( ID )
	reg.Region = Object.Region( ID )
	tabp.TabPage = Object.TabPage( ID )
	pan.Panel = Object.Panel( ID )
	grp.GroupBox = Object.GroupBox( ID )
	If reg <> Null
		win.Window = reg\Owner
		Order = reg\Order - 1
	ElseIf tabp <> Null
		win.Window = tabp\Owner\Owner
		Order = tabp\Owner\Order - 3
	ElseIf pan <> Null
		win.Window = pan\Owner
		Order = pan\Order - 2
	ElseIf grp <> Null
		win.Window = grp\Owner
		Order = grp\Order - 1
	ElseIf win <> Null
		Order = win\Order - 2
	EndIf
	
	For gad.Slider = Each Slider
		If win\Closed = False And win\Minimised = False
			
			Valid = False
			If reg <> Null
				If gad\Parent = ID
					Valid = True
				EndIf
			ElseIf tabp <> Null
				If gad\Parent = ID
					If tabp\Owner\actTabPage = tabp
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf pan <> Null
				If gad\Parent = ID
					If pan\Active = True
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf grp <> Null
				If gad\Parent = ID
					Valid = True
					ShowEntity gad\Mesh
				EndIf
			Else
				If gad\Owner = win And gad\Parent = 0
					Valid = True
				EndIf
			EndIf
			
			If gad\Hidden = True
				HideEntity gad\Mesh
				Valid = False
			EndIf
			
			If Valid = True
				EntityOrder gad\Mesh, Order
				EntityOrder gad\ScrollBar, Order-1
				
				Valid = False
				If win = app\overWin And gad\Disabled = False And (win = app\modalWin Or app\modalWin = Null)
					Valid = True
				EndIf
				
				X = FUI_InternalX( gad\Mesh )
				Y = FUI_InternalY( gad\Mesh )
				
				;Scroll Bar
				If gad\Direction = DIR_VERTICAL
					SX = X + 1
					SY = Y + 1 + gad\SX
					SW = gad\SH
					SH = gad\SW
					
					W = gad\H
					H = gad\W
				Else
					SX = X + 1 + gad\SX
					SY = Y + 1
					SW = gad\SW
					SH = gad\SH
					
					W = gad\W
					H = gad\H
				EndIf
				
				If FUI_MouseOver( X, Y, W, H ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					gad\State = 1
				Else
					gad\State = 0
				EndIf
					
				If app\MB1 = 1
					If FUI_MouseOver( SX, SY, SW, SH ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
						gad\SActive = True
						gad\OffX = gad\SX - app\MX
						gad\OffY = gad\SX - app\MY
					EndIf
				ElseIf app\MB1 = 2
					If gad\SActive = True
						If gad\Direction = DIR_VERTICAL
							gad\SX = app\MY + gad\OffY
						Else
							gad\SX = app\MX + gad\OffX
						EndIf
						If gad\SX < 0 gad\SX = 0
						If gad\SX > gad\W - 2 - gad\SW gad\SX = gad\W - 2 - gad\SW
							
						gad\Value = FUI_Interpolate( gad\SX, 0, gad\W - 2 - gad\SW, gad\Min, gad\Max )
						FUI_CreateEvent Handle( gad ), gad\Value
					EndIf
				Else
					gad\SActive = False
				EndIf
				
				If gad\Direction = DIR_VERTICAL
					PositionEntity gad\ScrollBar, 1, - 1 - gad\SX, 0.0
				Else
					PositionEntity gad\ScrollBar, 1 + gad\SX, - 1, 0.0
				EndIf
			EndIf
		EndIf
	Next
	
End Function

Function FUI_UpdateSpinner( ID )
	
	win.Window = Object.Window( ID )
	reg.Region = Object.Region( ID )
	tabp.TabPage = Object.TabPage( ID )
	pan.Panel = Object.Panel( ID )
	grp.GroupBox = Object.GroupBox( ID )
	If reg <> Null
		win.Window = reg\Owner
		Order = reg\Order - 1
	ElseIf tabp <> Null
		win.Window = tabp\Owner\Owner
		Order = tabp\Owner\Order - 3
	ElseIf pan <> Null
		win.Window = pan\Owner
		Order = pan\Order - 2
	ElseIf grp <> Null
		win.Window = grp\Owner
		Order = grp\Order - 1
	ElseIf win <> Null
		Order = win\Order - 2
	EndIf
	
	For gad.Spinner = Each Spinner
		If win\Closed = False And win\Minimised = False
			
			Valid = False
			If reg <> Null
				If gad\Parent = ID
					Valid = True
				EndIf
			ElseIf tabp <> Null
				If gad\Parent = ID
					If tabp\Owner\actTabPage = tabp
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf pan <> Null
				If gad\Parent = ID
					If pan\Active = True
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf grp <> Null
				If gad\Parent = ID
					Valid = True
					ShowEntity gad\Mesh
				EndIf
			Else
				If gad\Owner = win And gad\Parent = 0
					Valid = True
				EndIf
			EndIf
			
			If gad\Hidden = True
				HideEntity gad\Mesh
				Valid = False
			EndIf
			
			If Valid = True
				EntityOrder gad\Mesh, Order
				EntityOrder gad\MeshText, Order-1
				EntityOrder gad\Btn1, Order
				EntityOrder gad\Btn2, Order
				EntityOrder gad\Caret, Order-2
				Valid = False
				If win = app\overWin And gad\Disabled = False And (win = app\modalWin Or app\modalWin = Null)
					Valid = True
				EndIf
				
				X = FUI_InternalX( gad\Mesh )
				Y = FUI_InternalY( gad\Mesh )
				
				If app\actInputBox = Handle( gad )
					
					gad\CTimer = gad\CTimer + 1
					If gad\CTimer > 60
						gad\CTimer = 0
					ElseIf gad\CTimer > 30
						HideEntity gad\Caret
					ElseIf gad\CTimer > 0
						ShowEntity gad\Caret
					EndIf
					
					If gad\SelStart > gad\SelEnd
						SX = gad\SelEnd
						EX = gad\SelStart
					Else
						SX = gad\SelStart
						EX = gad\SelEnd
					EndIf
					
					;Input
					gad\Key = 0
					If app\actInputBox = Handle( gad )
						ClsMode = 0
						
						Key = GetKey()
						If Key <> 0
							gad\Key = Key
						Else
							;Numeric Keys not picked up by GetKey()
							If KeyDown(71)
								If gad\k71 = False
									gad\k71 = True
									gad\Key = 55
								EndIf
							Else
								gad\k71 = False
							EndIf
							If KeyDown(72)
								If gad\k72 = False
									gad\k72 = True
									gad\Key = 56
								EndIf
							Else
								gad\k72 = False
							EndIf
							If KeyDown(73)
								If gad\k73 = False
									gad\k73 = True
									gad\Key = 57
								EndIf
							Else
								gad\k73 = False
							EndIf
							If KeyDown(75)
								If gad\k75 = False
									gad\k75 = True
									gad\Key = 52
								EndIf
							Else
								gad\k75 = False
							EndIf
							If KeyDown(76)
								If gad\k76 = False
									gad\k76 = True
									gad\Key = 53
								EndIf
							Else
								gad\k76 = False
							EndIf
							If KeyDown(77)
								If gad\k77 = False
									gad\k77 = True
									gad\Key = 54
								EndIf
							Else
								gad\k77 = False
							EndIf
							If KeyDown(79)
								If gad\k79 = False
									gad\k79 = True
									gad\Key = 49
								EndIf
							Else
								gad\k79 = False
							EndIf
							If KeyDown(80)
								If gad\k80 = False
									gad\k80 = True
									gad\Key = 50
								EndIf
							Else
								gad\k80 = False
							EndIf
							If KeyDown(81)
								If gad\k81 = False
									gad\k81 = True
									gad\Key = 51
								EndIf
							Else
								gad\k81 = False
							EndIf
							If KeyDown(82)
								If gad\k82 = False
									gad\k82 = True
									gad\Key = 48
								EndIf
							Else
								gad\k82 = False
							EndIf
							If KeyDown(83)
								If gad\k83 = False
									gad\k83 = True
									gad\Key = 46
								EndIf
							Else
								gad\k83 = False
							EndIf
						EndIf
						
						If gad\Key <> 0
							;Numeric
							If Chr( gad\Key ) >= 0 And Chr( gad\Key ) <= 9
								Caption$ = Mid( gad\Caption, 1, SX ) + Chr( gad\Key ) + Mid( gad\Caption, EX + 1 )
								gad\Caption = Caption
								If gad\DType = DTYPE_INTEGER
									gad\Value = Int( gad\Caption )
									If gad\Value > gad\Max
										gad\Value = gad\Max
									ElseIf gad\Value < gad\Min
										gad\Value = gad\Min
									EndIf
									gad\Caption = Int( gad\Value ) + gad\Append
								Else
									gad\Value = Float( gad\Caption )
									If gad\Value > gad\Max
										gad\Value = gad\Max
									ElseIf gad\Value < gad\Min
										gad\Value = gad\Min
									EndIf
									gad\Caption = gad\Value + gad\Append
								EndIf
								gad\SelStart = SX + 1
								FUI_CreateEvent( Handle( gad ), gad\Caption )

								If gad\SelStart = Len( gad\Caption )
									ClsMode = 1
								Else
									ClsMode = 2
								EndIf
							EndIf

							;Misc
							If gad\Key = 45 Or gad\Key = 46
								Caption$ = Mid( gad\Caption, 1, SX ) + Chr( gad\Key ) + Mid( gad\Caption, EX + 1 )
								gad\Caption = Caption
								gad\SelStart = SX + 1
								FUI_CreateEvent( Handle( gad ), gad\Caption )

								If gad\SelStart = Len( gad\Caption )
									ClsMode = 1
								Else
									ClsMode = 2
								EndIf
							EndIf
							
							;Arrows
							If gad\Key = 31
								If gad\SelStart > 0
									gad\SelStart = SX - 1
								EndIf
							EndIf
							If gad\Key = 30
								If gad\SelStart < Len( gad\Caption )
									gad\SelStart = SX + 1
								EndIf
							EndIf
							
							;Backspace
							If gad\Key = 8
								If SX <> EX
									Caption$ = Mid( gad\Caption, 1, SX ) + Mid( gad\Caption, EX + 1 )
									gad\Caption = Caption
									gad\SelStart = SX
									ClsMode = 2
								Else
									If SX > 0
										Caption$ = Mid( gad\Caption, 1, SX - 1 ) + Mid( gad\Caption, EX + 1 )
										gad\Caption = Caption
										gad\SelStart = SX - 1
										ClsMode = 2
									EndIf
								EndIf
							EndIf
							
							;Delete
							If gad\Key = 4
								If SX <> EX
									Caption$ = Mid( gad\Caption, 1, SX ) + Mid( gad\Caption, EX + 1 )
									gad\Caption = Caption
									gad\SelStart = SX
									FUI_CreateEvent( Handle( gad ), gad\Caption )
									ClsMode = 2
								Else
									If SX < Len( gad\Caption )
										Caption$ = Mid( gad\Caption, 1, SX ) + Mid( gad\Caption, EX + 2 )
										gad\Caption = Caption
										gad\SelStart = SX
										FUI_CreateEvent( Handle( gad ), gad\Caption )
										ClsMode = 2
									EndIf
								EndIf
							EndIf
							
							;Enter
							If gad\Key = 13
								FlushKeys
								app\actInputBox = 0
								HideEntity gad\Caret
								
								If gad\DType = DTYPE_INTEGER
									gad\Value = Int( gad\Caption )
									If gad\Value > gad\Max
										gad\Value = gad\Max
									ElseIf gad\Value < gad\Min
										gad\Value = gad\Min
									EndIf
									gad\Caption = Int( gad\Value ) + gad\Append
								Else
									gad\Value = Float( gad\Caption )
									If gad\Value > gad\Max
										gad\Value = gad\Max
									ElseIf gad\Value < gad\Min
										gad\Value = gad\Min
									EndIf
									gad\Caption = gad\Value + gad\Append
								EndIf
								FUI_BuildSpinner gad, 2
								
								FUI_CreateEvent( Handle( gad ), gad\Caption )
							EndIf
							
							;Home
							If gad\Key = 1
								gad\SelStart = 0
							EndIf
							
							;End
							If gad\Key = 2
								gad\SelStart = Len( gad\Caption )
							EndIf
							
							SX = gad\SelStart
							gad\SelEnd = gad\SelStart
							EX = SX
							gad\Timer = 0
							
							SW = 4 + StringWidth( Left( gad\Caption, SX ) )
							If SW > gad\W - 5 - (gad\H - 2)
								ClsMode = 2
								gad\OffX = SW - ((gad\W - 5) - (gad\H - 2))
							Else
								If gad\OffX > 0
									gad\OffX = 0
									ClsMode = 2
								EndIf
							EndIf
							
							If ClsMode > 0
								FUI_BuildSpinner gad, ClsMode
							EndIf
						EndIf
					EndIf
					
					If gad\SelStart < gad\SelEnd
						SX = gad\SelStart
						EX = gad\SelEnd
					Else
						SX = gad\SelEnd
						EX = gad\SelStart
					EndIf
					
					PositionEntity gad\Caret, 6 + StringWidth( Left( gad\Caption, SX ) ) - gad\OffX - 0.5, - gad\H / 2 + FontHeight() / 2 - 0.5, 0.0
				EndIf
				
				If FUI_MouseOver( X, Y, gad\W, gad\H ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					gad\State = 1
				Else
					gad\State = 0
				EndIf
				
				;Button One
				BW = (gad\H-1)
				BH = (gad\H-2) / 2
				BX = X + gad\W - 1 - BW
				BY = Y + 1
				If FUI_MouseOver( BX, BY, BW, BH ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					gad\B1State = 1
					If app\MB1 = 1
						gad\B1Active = True
						gad\BDelay = 30
						gad\BTimer = 30
					ElseIf app\MB1 = 2
						If gad\B1Active = True
							gad\B1State = 2
							If gad\BTimer >= gad\BDelay
								If gad\Value + gad\Inc < gad\Max
									gad\Value = gad\Value + gad\Inc
								Else
									gad\Value = gad\Max
								EndIf
								
								If gad\DType = 0
									gad\Caption = Int( gad\Value ) + gad\Append
								Else
									gad\Caption = gad\Value + gad\Append
								EndIf
								
								gad\OffX = 0
								FUI_BuildSpinner gad, 2
								
								gad\BTimer = 0
								If gad\BDelay > 0
									If gad\BDelay = 29
										gad\BDelay = 10
									Else
										gad\BDelay = gad\BDelay - 1
									EndIf
								EndIf
								FUI_CreateEvent Handle( gad ), gad\Value
							EndIf
							gad\BTimer = gad\BTimer + 1
						EndIf
					Else
						If gad\B1Active = True
							gad\B1Active = False
						EndIf
					EndIf
				Else
					gad\B1State = 0
					If app\MB1 = 0 Or app\MB1 = 3
						gad\B1Active = False
					EndIf
				EndIf
				
				If gad\Disabled = False
					tex.BBTexture = FUI_GadgetTexture( gad\Btn1 )
					EntityTexture gad\Btn1, tex, gad\B1State
					FreeTexture tex
				EndIf
				
				;Button Two
				BH = (gad\H-2) - BH
				BY = Y + gad\H - 1 - BH
				If FUI_MouseOver( BX, BY, BW, BH ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					gad\B2State = 1
					If app\MB1 = 1
						gad\B2Active = True
						gad\BDelay = 30
						gad\BTimer = 30
					ElseIf app\MB1 = 2
						If gad\B2Active = True
							gad\B2State = 2
							If gad\BTimer >= gad\BDelay
								If gad\Value - gad\Inc > gad\Min
									gad\Value = gad\Value - gad\Inc
								Else
									gad\Value = gad\Min
								EndIf
								
								If gad\DType = 0
									gad\Caption = Int( gad\Value ) + gad\Append
								Else
									gad\Caption = gad\Value + gad\Append
								EndIf
								
								gad\OffX = 0
								FUI_BuildSpinner gad, 2
								
								gad\BTimer = 0
								If gad\BDelay > 0
									If gad\BDelay = 29
										gad\BDelay = 10
									Else
										gad\BDelay = gad\BDelay - 1
									EndIf
								EndIf
								FUI_CreateEvent Handle( gad ), gad\Value
							EndIf
							gad\BTimer = gad\BTimer + 1
						EndIf
					Else
						If gad\B2Active = True
							gad\B2Active = False
						EndIf
					EndIf
				Else
					gad\B2State = 0
					If app\MB1 = 0 Or app\MB1 = 3
						gad\B2Active = False
					EndIf
				EndIf
				
				If FUI_MouseOver( X, Y, gad\W - BW - 2, gad\H ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					app\Win32Mouse = IDCIBeam
					If app\MB1 = 1
						FlushKeys
						
						app\actInputBox = Handle( gad )
						gad\CTimer = 0
						
						OffX = app\MX - X
						If OffX < 4
							gad\SelEnd = 0
						ElseIf OffX > 4 + StringWidth( gad\Caption )
							gad\SelEnd = Len( gad\Caption )
						Else
							For A = 0 To Len( gad\Caption ) - 1
								SW = StringWidth( Left( gad\Caption, A ) ) - 4
								If Abs( OffX - SW ) < FontWidth()/2
									gad\SelEnd = A + 1
									Exit
								EndIf
							Next
						EndIf
						gad\SelStart = gad\SelEnd
					EndIf
				Else
					If app\MB1 = 1
						If app\actInputBox = Handle( gad )
							FlushKeys
							
							If gad\DType = DTYPE_INTEGER
								gad\Value = Int( gad\Caption )
								If gad\Value > gad\Max
									gad\Value = gad\Max
								ElseIf gad\Value < gad\Min
									gad\Value = gad\Min
								EndIf
								gad\Caption = Int( gad\Value ) + gad\Append
							Else
								gad\Value = Float( gad\Caption )
								If gad\Value > gad\Max
									gad\Value = gad\Max
								ElseIf gad\Value < gad\Min
									gad\Value = gad\Min
								EndIf
								gad\Caption = gad\Value + gad\Append
							EndIf
							
							FUI_BuildSpinner gad, 2
							
							app\actInputBox = 0
						EndIf
						HideEntity gad\Caret
					EndIf
				EndIf
				
				If gad\Disabled = False
					tex = FUI_GadgetTexture( gad\Btn2 )
					EntityTexture gad\Btn2, tex, gad\B2State
					FreeTexture tex
				EndIf
			EndIf
		EndIf
	Next
	
End Function

Function FUI_UpdateTextBox( ID )
	
	win.Window = Object.Window( ID )
	reg.Region = Object.Region( ID )
	tabp.TabPage = Object.TabPage( ID )
	pan.Panel = Object.Panel( ID )
	grp.GroupBox = Object.GroupBox( ID )
	If reg <> Null
		win.Window = reg\Owner
		Order = reg\Order - 1
	ElseIf tabp <> Null
		win.Window = tabp\Owner\Owner
		Order = tabp\Owner\Order - 3
	ElseIf pan <> Null
		win.Window = pan\Owner
		Order = pan\Order - 2
	ElseIf grp <> Null
		win.Window = grp\Owner
		Order = grp\Order - 1
	ElseIf win <> Null
		Order = win\Order - 2
	EndIf
	
	For gad.TextBox = Each TextBox
		If win\Closed = False And win\Minimised = False
			
			Valid = False
			If reg <> Null
				If gad\Parent = ID
					Valid = True
				EndIf
			ElseIf tabp <> Null
				If gad\Parent = ID
					If tabp\Owner\actTabPage = tabp
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf pan <> Null
				If gad\Parent = ID
					If pan\Active = True
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf grp <> Null
				If gad\Parent = ID
					Valid = True
					ShowEntity gad\Mesh
				EndIf
			Else
				If gad\Owner = win And gad\Parent = 0
					Valid = True
				EndIf
			EndIf
			
			If gad\Hidden = True
				HideEntity gad\Mesh
				Valid = False
			EndIf
			
			If Valid = True
				SetFont app\fntGadget
				
				EntityOrder gad\Mesh, Order
				EntityOrder gad\MeshText, Order-1
				EntityOrder gad\Caret, Order-2
				
				Valid = False
				If win = app\overWin And gad\Disabled = False And (win = app\modalWin Or app\modalWin = Null)
					Valid = True
				EndIf
				
				X = FUI_InternalX( gad\Mesh )
				Y = FUI_InternalY( gad\Mesh )
				
				If app\actInputBox = Handle( gad )
					ClsMode = 0
					
					If gad\SelStart > gad\SelEnd
						SX = gad\SelEnd
						EX = gad\SelStart
					Else
						SX = gad\SelStart
						EX = gad\SelEnd
					EndIf
					
					;Input
					gad\Key = 0
					If app\actInputBox = Handle( gad )
						Key = GetKey()
						If Key <> 0
							gad\Key = Key
						Else
							;Numeric Keys not picked up by GetKey()
							If KeyDown(71)
								If gad\k71 = False
									gad\k71 = True
									gad\Key = 55
								EndIf
							Else
								gad\k71 = False
							EndIf
							If KeyDown(72)
								If gad\k72 = False
									gad\k72 = True
									gad\Key = 56
								EndIf
							Else
								gad\k72 = False
							EndIf
							If KeyDown(73)
								If gad\k73 = False
									gad\k73 = True
									gad\Key = 57
								EndIf
							Else
								gad\k73 = False
							EndIf
							If KeyDown(75)
								If gad\k75 = False
									gad\k75 = True
									gad\Key = 52
								EndIf
							Else
								gad\k75 = False
							EndIf
							If KeyDown(76)
								If gad\k76 = False
									gad\k76 = True
									gad\Key = 53
								EndIf
							Else
								gad\k76 = False
							EndIf
							If KeyDown(77)
								If gad\k77 = False
									gad\k77 = True
									gad\Key = 54
								EndIf
							Else
								gad\k77 = False
							EndIf
							If KeyDown(79)
								If gad\k79 = False
									gad\k79 = True
									gad\Key = 49
								EndIf
							Else
								gad\k79 = False
							EndIf
							If KeyDown(80)
								If gad\k80 = False
									gad\k80 = True
									gad\Key = 50
								EndIf
							Else
								gad\k80 = False
							EndIf
							If KeyDown(81)
								If gad\k81 = False
									gad\k81 = True
									gad\Key = 51
								EndIf
							Else
								gad\k81 = False
							EndIf
							If KeyDown(82)
								If gad\k82 = False
									gad\k82 = True
									gad\Key = 48
								EndIf
							Else
								gad\k82 = False
							EndIf
							If KeyDown(83)
								If gad\k83 = False
									gad\k83 = True
									gad\Key = 46
								EndIf
							Else
								gad\k83 = False
							EndIf
						EndIf
						
						If gad\Key <> 0
							;Alpha
							If ( Chr( gad\Key ) >= "A" And Chr( gad\Key ) <= "Z" ) Or ( Chr( gad\Key ) >= "a" And Chr( gad\Key ) <= "z" ) Or gad\Key = 32 Or gad\Key > 191
								Caption$ = Mid( gad\Caption, 1, SX ) + Chr( gad\Key ) + Mid( gad\Caption, EX + 1 )
								If Len( Caption$ ) <= gad\MaxLength Or gad\MaxLength = 0
									gad\Caption = Caption
									gad\SelStart = SX + 1
									FUI_CreateEvent( Handle( gad ), gad\Caption )
									If gad\SelStart = Len( gad\Caption )
										ClsMode = 1
									Else
										ClsMode = 2
									EndIf
								EndIf
							EndIf
							
							;Numeric
							If Chr( gad\Key ) >= 0 And Chr( gad\Key ) <= 9
								Caption$ = Mid( gad\Caption, 1, SX ) + Chr( gad\Key ) + Mid( gad\Caption, EX + 1 )
								If Len( Caption$ ) <= gad\MaxLength Or gad\MaxLength = 0
									gad\Caption = Caption
									gad\SelStart = SX + 1
									FUI_CreateEvent( Handle( gad ), gad\Caption )
									If gad\SelStart = Len( gad\Caption )
										ClsMode = 1
									Else
										ClsMode = 2
									EndIf
								EndIf
							EndIf
							
							;Misc
							If ( gad\Key >= 33 And gad\Key <= 47 ) Or ( gad\Key >= 58 And gad\Key <= 64 ) Or ( gad\Key >= 91 And gad\Key <= 96 ) Or ( gad\Key >= 123 And gad\Key <= 126 ) Or gad\Key = 163 Or gad\Key = 166 Or gad\Key = 172
								Caption$ = Mid( gad\Caption, 1, SX ) + Chr( gad\Key ) + Mid( gad\Caption, EX + 1 )
								If Len( Caption$ ) <= gad\MaxLength Or gad\MaxLength = 0
									gad\Caption = Caption
									gad\SelStart = SX + 1
									FUI_CreateEvent( Handle( gad ), gad\Caption )
									If gad\SelStart = Len( gad\Caption )
										ClsMode = 1
									Else
										ClsMode = 2
									EndIf
								EndIf
							EndIf
							
							;Arrows
							If gad\Key = 31
								If gad\SelStart > 0
									gad\SelStart = SX - 1
								EndIf
							EndIf
							If gad\Key = 30
								If gad\SelStart < Len( gad\Caption )
									gad\SelStart = SX + 1
								EndIf
							EndIf
							
							;Backspace
							If gad\Key = 8
								If SX <> EX
									Caption$ = Mid( gad\Caption, 1, SX ) + Mid( gad\Caption, EX + 1 )
									If Len( Caption$ ) <= gad\MaxLength Or gad\MaxLength = 0
										gad\Caption = Caption
										gad\SelStart = SX
										FUI_CreateEvent( Handle( gad ), gad\Caption )
										ClsMode = 2
									EndIf
								Else
									If SX > 0
										Caption$ = Mid( gad\Caption, 1, SX - 1 ) + Mid( gad\Caption, EX + 1 )
										If Len( Caption$ ) <= gad\MaxLength Or gad\MaxLength = 0
											gad\Caption = Caption
											gad\SelStart = SX - 1
											FUI_CreateEvent( Handle( gad ), gad\Caption )
											ClsMode = 2
										EndIf
									EndIf
								EndIf
							EndIf
							
							;Delete
							If gad\Key = 4
								If SX <> EX
									Caption$ = Mid( gad\Caption, 1, SX ) + Mid( gad\Caption, EX + 1 )
									If Len( Caption$ ) <= gad\MaxLength Or gad\MaxLength = 0
										gad\Caption = Caption
										gad\SelStart = SX
										FUI_CreateEvent( Handle( gad ), gad\Caption )
										ClsMode = 2
									EndIf
								Else
									If SX < Len( gad\Caption )
										Caption$ = Mid( gad\Caption, 1, SX ) + Mid( gad\Caption, EX + 2 )
										If Len( Caption$ ) <= gad\MaxLength Or gad\MaxLength = 0
											gad\Caption = Caption
											gad\SelStart = SX
											FUI_CreateEvent( Handle( gad ), gad\Caption )
											ClsMode = 2
										EndIf
									EndIf
								EndIf
							EndIf
							
							;Enter
							If gad\Key = 13
								FlushKeys
								app\actInputBox = 0
								HideEntity gad\Caret
								FUI_CreateEvent( Handle( gad ), gad\Caption )
							EndIf
							
							;Home
							If gad\Key = 1
								gad\SelStart = 0
							EndIf
							
							;End
							If gad\Key = 2
								gad\SelStart = Len( gad\Caption )
							EndIf
							
							SX = gad\SelStart
							gad\SelEnd = gad\SelStart
							EX = SX
							gad\Timer = 0
							
							SW = 4 + StringWidth( Left( gad\Caption, SX ) )
							If SW > gad\W - 4
								ClsMode = 2
								gad\OffX = SW - (gad\W - 4)
							Else
								If gad\OffX > 0
									gad\OffX = 0
									ClsMode = 2
								EndIf
							EndIf
							
							If ClsMode > 0
								FUI_BuildTextBox gad, ClsMode
							EndIf
						EndIf
					EndIf
					
					If gad\SelStart < gad\SelEnd
						SX = gad\SelStart
						EX = gad\SelEnd
					Else
						SX = gad\SelEnd
						EX = gad\SelStart
					EndIf
					
					FUI_SetChildPosition gad\Caret, 6 + StringWidth( Left( gad\Caption, SX ) ) - gad\OffX, gad\H / 2 - FontHeight() / 2
					
					If app\actInputBox = Handle( gad )
						gad\CTimer = gad\CTimer + 1
						If gad\CTimer > 60
							gad\CTimer = 0
						ElseIf gad\CTimer > 30
							HideEntity gad\Caret
						ElseIf gad\CTimer > 0
							ShowEntity gad\Caret
						EndIf
					EndIf
				EndIf
				
				If FUI_MouseOver( X, Y, gad\W, gad\H ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					gad\State = 1
					app\Win32Mouse = IDCIBeam
					If app\MB1 = 1
						FlushKeys
						
						app\actInputBox = Handle( gad )
						gad\CTimer = 0
						
						OffX = app\MX - X
						If OffX < 4
							gad\SelEnd = 0
						ElseIf OffX > 4 + StringWidth( gad\Caption )
							gad\SelEnd = Len( gad\Caption )
						Else
							For A = 0 To Len( gad\Caption ) - 1
								SW = StringWidth( Left( gad\Caption, A ) ) - 4
								If Abs( OffX - SW ) < FontWidth()/2
									gad\SelEnd = A + 1
									Exit
								EndIf
							Next
						EndIf
						gad\SelStart = gad\SelEnd
					EndIf
				Else
					gad\State = 0
					If app\MB1 = 1
						If app\actInputBox = Handle( gad )
							FlushKeys
							
							app\actInputBox = 0
							HideEntity gad\Caret
						EndIf
					EndIf
				EndIf
			EndIf
		EndIf
	Next
	
End Function

Function FUI_UpdateTreeView( ID )
	
	win.Window = Object.Window( ID )
	reg.Region = Object.Region( ID )
	tabp.TabPage = Object.TabPage( ID )
	pan.Panel = Object.Panel( ID )
	grp.GroupBox = Object.GroupBox( ID )
	If reg <> Null
		win.Window = reg\Owner
		Order = reg\Order - 1
	ElseIf tabp <> Null
		win.Window = tabp\Owner\Owner
		Order = tabp\Owner\Order - 3
	ElseIf pan <> Null
		win.Window = pan\Owner
		Order = pan\Order - 2
	ElseIf grp <> Null
		win.Window = grp\Owner
		Order = grp\Order - 1
	ElseIf win <> Null
		Order = win\Order - 2
	EndIf
	
	For gad.TreeView = Each TreeView
		If win\Closed = False And win\Minimised = False
			
			Valid = False
			If reg <> Null
				If gad\Parent = ID
					Valid = True
				EndIf
			ElseIf tabp <> Null
				If gad\Parent = ID
					If tabp\Owner\actTabPage = tabp
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf pan <> Null
				If gad\Parent = ID
					If pan\Active = True
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf grp <> Null
				If gad\Parent = ID
					Valid = True
					ShowEntity gad\Mesh
				EndIf
			Else
				If gad\Owner = win And gad\Parent = 0
					Valid = True
				EndIf
			EndIf
			
			If gad\Hidden = True
				HideEntity gad\Mesh
				Valid = False
			EndIf
			
			If Valid = True
				HideEntity gad\VScroll\Mesh
				
				gad\Order = Order
				EntityOrder gad\Mesh, gad\Order
				
				Valid = False
				If win = app\overWin And gad\Disabled = False And (win = app\modalWin Or app\modalWin = Null)
					Valid = True
				EndIf
				
				X = FUI_InternalX( gad\Mesh )
				Y = FUI_InternalY( gad\Mesh )
				
				If FUI_MouseOver( X, Y, gad\W, gad\H ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					gad\State = 1
				Else
					gad\State = 0
				EndIf
				
				Count = FUI_UpdateNode( Handle( gad ) )
				
				gad\DW = gad\W - TREEVIEW_PADDING*2
				gad\DH = gad\H - TREEVIEW_PADDING*2
				If Count * TREEVIEWITEM_HEIGHT > gad\DH
					ShowEntity gad\VScroll\Mesh
					gad\DW = gad\DW - gad\VScroll\H
					gad\VScroll\Max = Count * TREEVIEWITEM_HEIGHT - gad\DH
					gad\SOY = -FUI_UpdateGadgetVScroll( gad\VScroll, gad\Order - 3 )
				Else
					gad\SOY = 0
				EndIf
			EndIf
		Else
			HideEntity gad\VScroll\Mesh
		EndIf
	Next
	
End Function

Function FUI_UpdateNode( ID, PrevIndex=0 )
	
	tree.TreeView = Object.TreeView( ID )
	node.Node = Object.Node( ID )
	
	Index = 0
	If tree <> Null
		win.Window = tree\Owner
		For gad.Node = Each Node
			If gad\Owner = tree And gad\Parent = Null
				HideEntity gad\Mesh
				
				Valid = True
				gad\Valid = True
				If gad\Hidden = True
					gad\Valid = False
					Valid = False
				EndIf
				
				FUI_SetChildPosition gad\Mesh, TREEVIEW_PADDING, TREEVIEW_PADDING + gad\Y + tree\SOY
				
				X = FUI_InternalX( gad\Mesh )
				Y = FUI_InternalY( gad\Mesh )
				
				Parent.BBEntity = GetParent( gad\DropDown )
				EntityParent gad\DropDown, gad\Mesh
				FUI_SetChildPosition gad\DropDown, 0, TREEVIEWITEM_HEIGHT
				EntityParent gad\DropDown, Parent
				
				If Y - FUI_InternalY( gad\Owner\Mesh ) + TREEVIEWITEM_HEIGHT > tree\H - TREEVIEW_PADDING Or Y - FUI_InternalY( gad\Owner\Mesh ) < TREEVIEW_PADDING
					If Valid = True
						Valid = False
						Index = Index + 1
					EndIf
				Else
					Index = Index + 1
				EndIf
				
				If gad\Valid = True
					
					If Valid = True
						ShowEntity gad\Mesh
						EntityOrder gad\Mesh, tree\Order-2
						
						Valid = False
						If win = app\overWin And gad\Disabled = False And gad\Owner\Disabled = False And (win = app\modalWin Or app\modalWin = Null)
							Valid = True
						EndIf
						
						If FUI_MouseOver( X, Y, tree\DW, TREEVIEWITEM_HEIGHT ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
							gad\State = 1
							tree\overNode = gad
							If app\MB1 = 1
								If FUI_MouseOver( X + 5, Y + 5, TREEVIEWITEM_HEIGHT - 10, TREEVIEWITEM_HEIGHT - 10 ) = True And gad\HasChildren = True
									gad\Open = 1 - gad\Open
									tex.BBTexture = FUI_GadgetTexture( gad\Mesh )
									EntityTexture gad\Mesh, tex, gad\Active*2 + gad\Open*3
									FreeTexture tex
									
									If gad\Open = True
										FUI_OpenGadget Handle( gad )
										
										ShowEntity gad\DropDown
									Else
										FUI_CloseGadget Handle( gad )
										
										HideEntity gad\DropDown
									EndIf
								Else
									gad\Active = True
									For node2.Node = Each Node
										If node2\Owner = gad\Owner And node2 <> gad
											node2\Active = False
											tex = FUI_GadgetTexture( node2\Mesh )
											EntityTexture node2\Mesh, tex, node2\Active*2 + node2\Open*3
											FreeTexture tex
										EndIf
									Next
									FUI_CreateEvent Handle( gad\Owner ), PrevIndex + Index, 0, 0, Handle( gad )
									FUI_CreateEvent Handle( gad ), gad\Active
									tex = FUI_GadgetTexture( gad\Mesh )
									EntityTexture gad\Mesh, tex, gad\Active*2 + gad\Open*3
									FreeTexture tex
								EndIf
							EndIf
						Else
							gad\State = 0
						EndIf
					EndIf
					Index = Index + FUI_UpdateNode( Handle( gad ), Index )
				EndIf
			EndIf
		Next
	ElseIf node <> Null
		tree.TreeView = node\Owner
		win.Window = node\Owner\Owner
		For gad.Node = Each Node
			If gad\Parent = node
				HideEntity gad\Mesh
				
				Valid = True
				gad\Valid = True
				If gad\Hidden = True Or node\Open = False Or node\Valid = False
					gad\Valid = False
					Valid = False
				EndIf
				
				X = FUI_InternalX( gad\Mesh )
				Y = FUI_InternalY( gad\Mesh )
				
				Parent = GetParent( gad\DropDown )
				EntityParent gad\DropDown, gad\Mesh
				FUI_SetChildPosition gad\DropDown, 0, TREEVIEWITEM_HEIGHT
				EntityParent gad\DropDown, Parent
				
				If Y - FUI_InternalY( gad\Owner\Mesh ) + TREEVIEWITEM_HEIGHT > tree\H - TREEVIEW_PADDING Or Y - FUI_InternalY( gad\Owner\Mesh ) < TREEVIEW_PADDING
					If Valid = True
						Valid = False
						Index = Index + 1
					EndIf
				Else
					If node\Valid = True And node\Open = True
						Index = Index + 1
					EndIf
				EndIf
				
				If gad\Valid = True And Valid = True
					ShowEntity gad\Mesh
					EntityOrder gad\Mesh, tree\Order-2
					
					Valid = False
					If win = app\overWin And gad\Disabled = False And (win = app\modalWin Or app\modalWin = Null)
						Valid = True
					EndIf
					
					If FUI_MouseOver( X, Y, tree\DW, TREEVIEWITEM_HEIGHT ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
						gad\State = 1
						tree\overNode = gad
						If app\MB1 = 1
							If FUI_MouseOver( X + (gad\Layer-1)*TREEVIEWITEM_HEIGHT + 5, Y + 5, TREEVIEWITEM_HEIGHT - 10, TREEVIEWITEM_HEIGHT - 10 ) = True And gad\HasChildren = True
								gad\Open = 1 - gad\Open
								tex = FUI_GadgetTexture( gad\Mesh )
								EntityTexture gad\Mesh, tex, gad\Active*2 + gad\Open*3
								FreeTexture tex
								
								If gad\Open = True
									node3.Node = gad
									Repeat
										If node3\Parent <> Null
											node3\Parent\OH = node3\Parent\OH + gad\OH
										EndIf
										node3 = node3\Parent
									Until node3 = Null
									For node2.Node = Each Node
										If node2\Owner = gad\Owner And (node2\Layer <= gad\Layer) And node2 <> gad
											node3.Node = node2
											
											If node2\Y > gad\Y
												node2\Y = node2\Y + gad\OH
												MoveEntity node2\Mesh, 0.0,-gad\OH, 0.0
											EndIf
										EndIf
									Next
									ShowEntity gad\DropDown
								Else
									node3.Node = gad
									Repeat
										If node3\Parent <> Null
											node3\Parent\OH = node3\Parent\OH - gad\OH
										EndIf
										node3 = node3\Parent
									Until node3 = Null
									For node2.Node = Each Node
										If node2\Owner = gad\Owner And (node2\Layer <= gad\Layer) And node2 <> gad
											If node2\Y > gad\Y
												node2\Y = node2\Y - gad\OH
												MoveEntity node2\Mesh, 0.0, gad\OH, 0.0
											EndIf
										EndIf
									Next
									HideEntity gad\DropDown
								EndIf
							Else
								gad\Active = True
								For node2.Node = Each Node
									If node2\Owner = gad\Owner And node2 <> gad
										node2\Active = False
										tex = FUI_GadgetTexture( node2\Mesh )
										EntityTexture node2\Mesh, tex, node2\Active*2 + node2\Open*3
										FreeTexture tex
									EndIf
								Next
								FUI_CreateEvent Handle( gad\Owner ), PrevIndex + Index, 0, 0, Handle( gad )
								FUI_CreateEvent Handle( gad ), gad\Active
								tex = FUI_GadgetTexture( gad\Mesh )
								EntityTexture gad\Mesh, tex, gad\Active*2 + gad\Open*3
								FreeTexture tex
							EndIf
						EndIf
					Else
						gad\State = 0
					EndIf
				EndIf
				
				Index = Index + FUI_UpdateNode( Handle( gad ), Index )
			EndIf
		Next
	EndIf
	
	Return Index
	
End Function

Function FUI_UpdateView( ID )
	
	win.Window = Object.Window( ID )
	reg.Region = Object.Region( ID )
	tabp.TabPage = Object.TabPage( ID )
	pan.Panel = Object.Panel( ID )
	grp.GroupBox = Object.GroupBox( ID )
	If reg <> Null
		win.Window = reg\Owner
		Order = reg\Order - 1
	ElseIf tabp <> Null
		win.Window = tabp\Owner\Owner
		Order = tabp\Owner\Order - 3
	ElseIf pan <> Null
		win.Window = pan\Owner
		Order = pan\Order - 2
	ElseIf grp <> Null
		win.Window = grp\Owner
		Order = grp\Order - 1
	ElseIf win <> Null
		Order = win\Order - 2
	EndIf

	For gad.View = Each View
		If win\Closed = False And win\Minimised = False

			Valid = False
			If reg <> Null
				If gad\Parent = ID
					Valid = True
				EndIf
			ElseIf tabp <> Null
				If gad\Parent = ID
					If tabp\Owner\actTabPage = tabp
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf pan <> Null
				If gad\Parent = ID
					If pan\Active = True
						Valid = True
						ShowEntity gad\Mesh
					Else
						HideEntity gad\Mesh
					EndIf
				EndIf
			ElseIf grp <> Null
				If gad\Parent = ID
					Valid = True
					ShowEntity gad\Mesh
				EndIf
			ElseIf gad\Owner = win; And gad\Parent = 0
				tabp.TabPage = Object.TabPage(gad\Parent)
				If tabp <> Null
					If tabp\Owner\actTabPage = tabp
						If tabp\Owner\Parent = Handle(win)
							Valid = True
						Else
							tabp2.TabPage = Object.TabPage(tabp\Owner\Parent)
							If tabp2 <> Null
								If tabp2\Owner\actTabPage = tabp2 Then Valid = True
							Else
								Valid = True
							EndIf
						EndIf
					EndIf
					tabp = Null
				ElseIf gad\Parent = 0
					Valid = True
				EndIf
			EndIf

			If gad\Hidden = True
				HideEntity gad\Mesh
				Valid = False
			EndIf

			If Valid = True
				ShowEntity gad\Mesh
				EntityOrder gad\Mesh, Order
				EntityOrder gad\MeshText, Order-1
				
				X = FUI_InternalX( gad\Mesh )
				Y = FUI_InternalY( gad\Mesh )
				
				If X >= 0 And Y >= 0 And X + gad\W <= app\W And Y + gad\H <= app\H
					ShowEntity gad\Cam

					If gad\W > 4 And gad\H > 4
						CameraViewport gad\Cam, X+2, Y+2, gad\W-4, gad\H-4
					Else
						CameraViewport gad\Cam, X+2, Y+2, 0, 0
					EndIf
					
					If gad\Wire = False
						For m.Mesh = Each Mesh
							If m\Wire = False And m\Hidden = False
								ShowEntity m\Mesh
							Else
								HideEntity m\Mesh
							EndIf
						Next
						CameraClsMode gad\Cam, 1, 1
						WireFrame False
						RenderWorld

						CameraClsMode gad\Cam, 0, 0
						For m.Mesh = Each Mesh
							If m\Wire = False Or m\Hidden = True
								HideEntity m\Mesh
							Else
								ShowEntity m\Mesh
							EndIf
						Next
;						WireFrame True
					Else
						CameraClsMode gad\Cam, 1, 1
						For m.Mesh = Each Mesh
							If m\Hidden = False
								ShowEntity m\Mesh
							Else
								HideEntity m\Mesh
							EndIf
						Next
						WireFrame True
					EndIf
;					RenderWorld

					For m.Mesh = Each Mesh
						If m\Hidden = False
							ShowEntity m\Mesh
						EndIf
					Next
					HideEntity gad\Cam
					
					WireFrame False
				EndIf
				
				Valid = False
				If win = app\overWin And gad\Disabled = False And (win = app\modalWin Or app\modalWin = Null)
					Valid = True
				EndIf

				If FUI_MouseOver( X, Y, gad\W, gad\H ) = True And FUI_NoActiveGadgets(  ) = True And Valid = True
					gad\State = 1
					app\overView = Handle( gad )
				Else
					gad\State = 0
				EndIf
			Else
				HideEntity gad\Mesh
			EndIf
		EndIf
	Next
	
End Function

Function FUI_UpdateGadgetVScroll( gad.VScrollBar, Order )
	
	EntityOrder gad\Mesh, Order
	EntityOrder gad\Btn1, Order-1
	EntityOrder gad\Btn2, Order-1
	EntityOrder gad\ScrollBar, Order-1
	
	;Button One
	BX = FUI_InternalX( gad\Btn1 )
	BY = FUI_InternalY( gad\Btn1 )
	BW = gad\H - 1
	BH = gad\H - 1
	If FUI_MouseOver( BX, BY, BW, BH ) = True
		gad\B1State = 1
		If app\MB1 = 1
			gad\B1Active = True
		ElseIf app\MB1 = 2
			If gad\B1Active = True
				gad\B1State = 2
				If gad\SX > 0
					gad\SX = gad\SX - 1
				EndIf
			EndIf
		Else
			If gad\B1Active = True
				gad\B1Active = False
			EndIf
		EndIf
	Else
		gad\B1State = 0
		If app\MB1 = 0 Or app\MB1 = 3
			gad\B1Active = False
		EndIf
	EndIf
	
	tex.BBTexture = FUI_GadgetTexture( gad\Btn1 )
	EntityTexture gad\Btn1, tex, gad\B1State
	FreeTexture tex
	
	;Scroll Bar
	SX = FUI_InternalX( gad\ScrollBar )
	SY = FUI_InternalY( gad\ScrollBar )
	SW = gad\SH
	SH = gad\SW
	
	If app\MB1 = 1
		If FUI_MouseOver( SX, SY, SW, SH ) = True
			gad\SActive = True
			gad\OffX = gad\SX - app\MX
			gad\OffY = gad\SX - app\MY
		EndIf
	ElseIf app\MB1 = 2
		If gad\SActive = True
			gad\SX = app\MY + gad\OffY
			If gad\SX < 0 gad\SX = 0
			If gad\SX > gad\W - BW*2 - 2 - gad\SW gad\SX = gad\W - BW*2 - 2 - gad\SW
		EndIf
	Else
		gad\SActive = False
	EndIf
	
	;Button Two
	BX = FUI_InternalX( gad\Btn2 )
	BY = FUI_InternalY( gad\Btn2 )
	If FUI_MouseOver( BX, BY, BW, BH ) = True
		gad\B2State = 1
		If app\MB1 = 1
			gad\B2Active = True
		ElseIf app\MB1 = 2
			If gad\B2Active = True
				gad\B2State = 2
				If gad\SX < gad\W - BW*2 - 2 - gad\SW
					gad\SX = gad\SX + 1
				EndIf
			EndIf
		Else
			If gad\B2Active = True
				gad\B2Active = False
			EndIf
		EndIf
	Else
		gad\B2State = 0
		If app\MB1 = 0 Or app\MB1 = 3
			gad\B2Active = False
		EndIf
	EndIf
	
	gad\SX = gad\SX - app\MZS
	If gad\SX <= 0
		gad\SX = 0
	EndIf
	If gad\SX >= gad\W - BW*2 - 2 - gad\SW
		gad\SX = gad\W - BW*2 - 2 - gad\SW
	EndIf
	
	tex = FUI_GadgetTexture( gad\Btn2 )
	EntityTexture gad\Btn2, tex, gad\B2State
	FreeTexture tex
	
	FUI_SetChildPosition gad\ScrollBar, 1, 1 + BW + gad\SX
	
	gad\Value = FUI_Interpolate( gad\SX, 0, gad\W - BW*2 - 2 - gad\SW, gad\Min, gad\Max )
	Return gad\Value
	
End Function
;#End Region

;#Region ---------- Skin Gadgets ---------------
Function FUI_SkinWindow.BBTexture( gad.Window, Mode=0 )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	fName$ = SKIN_PATH$
	If Right( fName$, 1 ) <> "\"
		fName$ = fName$ + "\"
	EndIf
	
	ARGB	= (0 Or (0 Shl 8) Or (0 Shl 16) Or (Alpha Shl 24))
	
	W		= gad\W - WINDOW_BORDER*2
	TW		= FUI_NextPower( W )
	H		= gad\H - WINDOW_BORDER
	
	If Mode = 0
		If (gad\Flags And WS_TITLEBAR) = WS_TITLEBAR
			H = H - TITLEBAR_HEIGHT
		EndIf
		If (gad\Flags And WS_MENUBAR) = WS_MENUBAR
			H = H - MENUBAR_HEIGHT
		EndIf
		If (gad\Flags And WS_STATUSBAR) = WS_STATUSBAR
			H = H - STATUSBAR_HEIGHT
		EndIf
		
		TH		= FUI_NextPower( H )
		
		img.BBImage		= LoadImage( fName$ + "window\form.bmp" )
		If img <> Null
			SW = ImageWidth( img ) / 3
			SH = ImageHeight( img ) / 3
			LockBuffer ImageBuffer( img )
			
			tex.BBTexture = FUI_CreateTexture( TW, TH, 1+2, 1 )
			EntityTexture gad\Mesh, tex
			
			LockBuffer TextureBuffer( tex )
			
			IY = 0
			If (gad\Flags And WS_TITLEBAR) = WS_TITLEBAR Or (gad\Flags And WS_MENUBAR) = WS_MENUBAR
				IY = SH
			EndIf
			
			;Top Left
			For X = 0 To SW - 1
				For Y = 0 To SH - 1
					RGB = ReadPixelFast( X, Y + IY, ImageBuffer( img ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = 255
						G = 255
						B = 255
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast X, Y, RGB, TextureBuffer( tex )
				Next
			Next
			
			;Top Middle
			For X = SW To W - SW - 1
				For Y = 0 To SH - 1
					IX = X Mod SW
					RGB = ReadPixelFast( IX + SW, Y + IY, ImageBuffer( img ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = 255
						G = 255
						B = 255
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast X, Y, RGB, TextureBuffer( tex )
				Next
			Next
			
			;Top Right
			For X = 0 To SW - 1
				For Y = 0 To SH - 1
					RGB = ReadPixelFast( X + SW * 2, Y + IY, ImageBuffer( img ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = 255
						G = 255
						B = 255
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast W - SW + X, Y, RGB, TextureBuffer( tex )
				Next
			Next
			
			IY = SH * 2
			If (gad\Flags And WS_STATUSBAR) = WS_STATUSBAR
				IY = SH
			EndIf
			
			;Bottom Left
			For X = 0 To SW - 1
				For Y = 0 To SH - 1
					RGB = ReadPixelFast( X, Y + IY, ImageBuffer( img ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = 255
						G = 255
						B = 255
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast X, H - SH + Y, RGB, TextureBuffer( tex )
				Next
			Next
			
			;Bottom Middle
			For X = SW To W - SW - 1
				For Y = 0 To SH - 1
					IX = X Mod SW
					RGB = ReadPixelFast( IX + SW, Y + IY, ImageBuffer( img ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = 255
						G = 255
						B = 255
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast X, H - SH + Y, RGB, TextureBuffer( tex )
				Next
			Next
			
			;Bottom Right
			For X = 0 To SW - 1
				For Y = 0 To SH - 1
					RGB = ReadPixelFast( X + SW * 2, Y + IY, ImageBuffer( img ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = 255
						G = 255
						B = 255
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast W - SW + X, H - SH + Y, RGB, TextureBuffer( tex )
				Next
			Next
			
			;Left Middle
			For X = 0 To SW
				For Y = SH To H - SH - 1
					IY = Y Mod SH
					RGB = ReadPixelFast( X, IY + SH, ImageBuffer( img ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = 255
						G = 255
						B = 255
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast X, Y, RGB, TextureBuffer( tex )
				Next
			Next
			
			;Middle
			For X = SW To W - SW - 1
				For Y = SH To H - SH - 1
					IX = X Mod SW
					IY = Y Mod SH
					RGB = ReadPixelFast( IX + SW, IY + SH, ImageBuffer( img ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = 255
						G = 255
						B = 255
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast X, Y, RGB, TextureBuffer( tex )
				Next
			Next
			
			;Right Middle
			For X = 0 To SW
				For Y = SH To H - SH - 1
					IY = Y Mod SH
					RGB = ReadPixelFast( X + SW*2, IY + SH, ImageBuffer( img ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = 255
						G = 255
						B = 255
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast W - SW + X, Y, RGB, TextureBuffer( tex )
				Next
			Next
			
			UnlockBuffer TextureBuffer( tex )
			UnlockBuffer ImageBuffer( img )
			
			FreeTexture tex
			FreeImage img
		EndIf
	EndIf
	
	If Mode = 1
		If (gad\Flags And WS_TITLEBAR) = WS_TITLEBAR
			Local dimg.BBImage[2]
			dimg[0]	= LoadImage( fName$ + "window\titlebar.bmp" )
			dimg[1] = LoadImage( fName$ + "window\titlebar_min.bmp" )
			
			H		= TITLEBAR_HEIGHT
			TH		= FUI_NextPower( H )
			
			tex		= FUI_CreateTexture( TW, TH, 1+2, 2 )
			
			Result = True
			For LOOP = 0 To 1
				If dimg[LOOP] <> Null And tex <> Null
					SW = ImageWidth( dimg[LOOP] ) / 3
					LockBuffer ImageBuffer( dimg[LOOP] )
					LockBuffer TextureBuffer( tex, LOOP )
					
					;Left Edge
					For X = 0 To SW - 1
						For Y = 0 To H - 1
							RGB = ReadPixelFast( X, Y, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = 255
								G = 255
								B = 255
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					;Middle
					For X = SW To W - SW - 1
						For Y = 0 To H - 1
							IX = X Mod SW
							RGB = ReadPixelFast( IX + SW, Y, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = 255
								G = 255
								B = 255
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					;Right Edge
					For X = 0 To SW - 1
						For Y = 0 To H - 1
							RGB = ReadPixelFast( X + SW * 2, Y, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = 255
								G = 255
								B = 255
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast W - SW + X, Y, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					UnlockBuffer TextureBuffer( tex, LOOP )
					UnlockBuffer ImageBuffer( dimg[LOOP] )
					FreeImage dimg[LOOP]
				EndIf
			Next
			
			Return tex
		EndIf
	EndIf
	
	If Mode = 2
		If (gad\Flags And WS_MENUBAR) = WS_MENUBAR
			img		= LoadImage( fName$ + "window\menubar.bmp" )
			
			H		= MENUBAR_HEIGHT
			TH		= FUI_NextPower( H )
			
			If img <> Null
				SW = ImageWidth( img ) / 3
				LockBuffer ImageBuffer( img )
				
				tex = FUI_CreateTexture( TW, TH, 1+2, 1 )
				EntityTexture gad\MenuBar, tex
				
				LockBuffer TextureBuffer( tex )
				
				;Left Edge
				For X = 0 To SW - 1
					For Y = 0 To H - 1
						RGB = ReadPixelFast( X, Y, ImageBuffer( img ) ) And $FFFFFF
						R = (RGB Shr 16) And $FF
						G = (RGB Shr 8) And $FF
						B = RGB And $FF
						
						If R > 0 And G = 0 And B = 0
							A = R
							R = FUI_GetRed( SC_MENUBAR )
							G = FUI_GetGreen( SC_MENUBAR )
							B = FUI_GetBlue( SC_MENUBAR )
						ElseIf R = 255 And G = 0 And B = 255
							A = 0
						Else
							A = 255
						EndIf
						RGB = FUI_RGBToInt( R, G, B, A )
						
						WritePixelFast X, Y, RGB, TextureBuffer( tex )
					Next
				Next
				
				;Middle
				For X = SW To W - SW - 1
					For Y = 0 To H - 1
						IX = X Mod SW
						RGB = ReadPixelFast( IX + SW, Y, ImageBuffer( img ) ) And $FFFFFF
						R = (RGB Shr 16) And $FF
						G = (RGB Shr 8) And $FF
						B = RGB And $FF
						
						If R > 0 And G = 0 And B = 0
							A = R
							R = FUI_GetRed( SC_MENUBAR )
							G = FUI_GetGreen( SC_MENUBAR )
							B = FUI_GetBlue( SC_MENUBAR )
						ElseIf R = 255 And G = 0 And B = 255
							A = 0
						Else
							A = 255
						EndIf
						RGB = FUI_RGBToInt( R, G, B, A )
						
						WritePixelFast X, Y, RGB, TextureBuffer( tex )
					Next
				Next
				
				;Right Edge
				For X = 0 To SW - 1
					For Y = 0 To H - 1
						RGB = ReadPixelFast( X + SW * 2, Y, ImageBuffer( img ) ) And $FFFFFF
						R = (RGB Shr 16) And $FF
						G = (RGB Shr 8) And $FF
						B = RGB And $FF
						
						If R > 0 And G = 0 And B = 0
							A = R
							R = FUI_GetRed( SC_MENUBAR )
							G = FUI_GetGreen( SC_MENUBAR )
							B = FUI_GetBlue( SC_MENUBAR )
						ElseIf R = 255 And G = 0 And B = 255
							A = 0
						Else
							A = 255
						EndIf
						RGB = FUI_RGBToInt( R, G, B, A )
						
						WritePixelFast W - SW + X, Y, RGB, TextureBuffer( tex )
					Next
				Next
				
				UnlockBuffer TextureBuffer( tex )
				UnlockBuffer ImageBuffer( img )
				
				FreeTexture tex
				FreeImage img
				
				Return tex
			EndIf
		EndIf
	EndIf
	
	If Mode = 3
		If (gad\Flags And WS_STATUSBAR) = WS_STATUSBAR
			img		= LoadImage( fName$ + "window\statusbar.bmp" )
			
			H		= STATUSBAR_HEIGHT
			TH		= FUI_NextPower( H )
			
			If img <> Null
				SW = ImageWidth( img ) / 3
				LockBuffer ImageBuffer( img )
				
				tex = FUI_CreateTexture( TW, TH, 1+2, 1 )
				EntityTexture gad\StatusBar, tex
				
				LockBuffer TextureBuffer( tex )
				
				;Left Edge
				For X = 0 To SW - 1
					For Y = 0 To H - 1
						RGB = ReadPixelFast( X, Y, ImageBuffer( img ) ) And $FFFFFF
						R = (RGB Shr 16) And $FF
						G = (RGB Shr 8) And $FF
						B = RGB And $FF
						
						If R > 0 And G = 0 And B = 0
							A = R
							R = FUI_GetRed( SC_STATUSBAR )
							G = FUI_GetGreen( SC_STATUSBAR )
							B = FUI_GetBlue( SC_STATUSBAR )
						ElseIf R = 255 And G = 0 And B = 255
							A = 0
						Else
							A = 255
						EndIf
						RGB = FUI_RGBToInt( R, G, B, A )
						
						WritePixelFast X, Y, RGB, TextureBuffer( tex )
					Next
				Next
				
				;Middle
				For X = SW To W - SW - 1
					For Y = 0 To H - 1
						IX = X Mod SW
						RGB = ReadPixelFast( IX + SW, Y, ImageBuffer( img ) ) And $FFFFFF
						R = (RGB Shr 16) And $FF
						G = (RGB Shr 8) And $FF
						B = RGB And $FF
						
						If R > 0 And G = 0 And B = 0
							A = R
							R = FUI_GetRed( SC_STATUSBAR )
							G = FUI_GetGreen( SC_STATUSBAR )
							B = FUI_GetBlue( SC_STATUSBAR )
						ElseIf R = 255 And G = 0 And B = 255
							A = 0
						Else
							A = 255
						EndIf
						RGB = FUI_RGBToInt( R, G, B, A )
						
						WritePixelFast X, Y, RGB, TextureBuffer( tex )
					Next
				Next
				
				;Right Edge
				For X = 0 To SW - 1
					For Y = 0 To H - 1
						RGB = ReadPixelFast( X + SW * 2, Y, ImageBuffer( img ) ) And $FFFFFF
						R = (RGB Shr 16) And $FF
						G = (RGB Shr 8) And $FF
						B = RGB And $FF
						
						If R > 0 And G = 0 And B = 0
							A = R
							R = FUI_GetRed( SC_STATUSBAR )
							G = FUI_GetGreen( SC_STATUSBAR )
							B = FUI_GetBlue( SC_STATUSBAR )
						ElseIf R = 255 And G = 0 And B = 255
							A = 0
						Else
							A = 255
						EndIf
						RGB = FUI_RGBToInt( R, G, B, A )
						
						WritePixelFast W - SW + X, Y, RGB, TextureBuffer( tex )
					Next
				Next
				
				UnlockBuffer TextureBuffer( tex )
				UnlockBuffer ImageBuffer( img )
				
				FreeTexture tex
				FreeImage img
				
				Return tex
			EndIf
		EndIf
	EndIf
	
	SetBuffer pBuffer
	
	Return Null
	
End Function

Function FUI_SkinTab.BBTexture( gad.Tab )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	fName$ = SKIN_PATH$
	If Right( fName$, 1 ) <> "\"
		fName$ = fName$ + "\"
	EndIf
	
	ARGB	= (0 Or (0 Shl 8) Or (0 Shl 16) Or (Alpha Shl 24))
	
	W		= gad\W
	TW		= FUI_NextPower( W )
	H		= gad\H - TABPAGE_HEIGHT
	TH		= FUI_NextPower( H )
	
	Local dimg.BBImage[1]
	dimg[0]		= LoadImage( fName$ + "tab\form.bmp" )
	
	tex.BBTexture = FUI_CreateTexture( TW, TH, 1+2, 1 )
	
	If dimg[LOOP] <> Null
		SetBuffer TextureBuffer( tex, LOOP )
		;FUI_SetColor SC_GADGET
		;Rect 0, 0, W, H
		
		SW = ImageWidth( dimg[LOOP] ) / 3
		SH = ImageHeight( dimg[LOOP] ) / 3
		LockBuffer ImageBuffer( dimg[LOOP] )
		
		LockBuffer TextureBuffer( tex, LOOP )
		
		IY = 0
		
		;Top Left
		For X = 0 To SW - 1
			For Y = 0 To SH - 1
				RGB = ReadPixelFast( X, Y + IY, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
				R = (RGB Shr 16) And $FF
				G = (RGB Shr 8) And $FF
				B = RGB And $FF
				
				If R > 0 And G = 0 And B = 0
					A = R
					R = FUI_GetRed( SC_GADGET )
					G = FUI_GetGreen( SC_GADGET )
					B = FUI_GetBlue( SC_GADGET )
				ElseIf R = 255 And G = 0 And B = 255
					A = 0
				Else
					A = 255
				EndIf
				RGB = FUI_RGBToInt( R, G, B, A )
				
				WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
			Next
		Next
		
		;Top Middle
		For X = SW To W - SW - 1
			For Y = 0 To SH - 1
				IX = X Mod SW
				RGB = ReadPixelFast( IX + SW, Y + IY, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
				R = (RGB Shr 16) And $FF
				G = (RGB Shr 8) And $FF
				B = RGB And $FF
				
				If R > 0 And G = 0 And B = 0
					A = R
					R = FUI_GetRed( SC_GADGET )
					G = FUI_GetGreen( SC_GADGET )
					B = FUI_GetBlue( SC_GADGET )
				ElseIf R = 255 And G = 0 And B = 255
					A = 0
				Else
					A = 255
				EndIf
				RGB = FUI_RGBToInt( R, G, B, A )
				
				WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
			Next
		Next
		
		;Top Right
		For X = 0 To SW - 1
			For Y = 0 To SH - 1
				RGB = ReadPixelFast( X + SW * 2, Y + IY, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
				R = (RGB Shr 16) And $FF
				G = (RGB Shr 8) And $FF
				B = RGB And $FF
				
				If R > 0 And G = 0 And B = 0
					A = R
					R = FUI_GetRed( SC_GADGET )
					G = FUI_GetGreen( SC_GADGET )
					B = FUI_GetBlue( SC_GADGET )
				ElseIf R = 255 And G = 0 And B = 255
					A = 0
				Else
					A = 255
				EndIf
				RGB = FUI_RGBToInt( R, G, B, A )
				
				WritePixelFast W - SW + X, Y, RGB, TextureBuffer( tex, LOOP )
			Next
		Next
		
		IY = SH * 2
		
		;Bottom Left
		For X = 0 To SW - 1
			For Y = 0 To SH - 1
				RGB = ReadPixelFast( X, Y + IY, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
				R = (RGB Shr 16) And $FF
				G = (RGB Shr 8) And $FF
				B = RGB And $FF
				
				If R > 0 And G = 0 And B = 0
					A = R
					R = FUI_GetRed( SC_GADGET )
					G = FUI_GetGreen( SC_GADGET )
					B = FUI_GetBlue( SC_GADGET )
				ElseIf R = 255 And G = 0 And B = 255
					A = 0
				Else
					A = 255
				EndIf
				RGB = FUI_RGBToInt( R, G, B, A )
				
				WritePixelFast X, H - SH + Y, RGB, TextureBuffer( tex, LOOP )
			Next
		Next
		
		;Bottom Middle
		For X = SW To W - SW - 1
			For Y = 0 To SH - 1
				IX = X Mod SW
				RGB = ReadPixelFast( IX + SW, Y + IY, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
				R = (RGB Shr 16) And $FF
				G = (RGB Shr 8) And $FF
				B = RGB And $FF
				
				If R > 0 And G = 0 And B = 0
					A = R
					R = FUI_GetRed( SC_GADGET )
					G = FUI_GetGreen( SC_GADGET )
					B = FUI_GetBlue( SC_GADGET )
				ElseIf R = 255 And G = 0 And B = 255
					A = 0
				Else
					A = 255
				EndIf
				RGB = FUI_RGBToInt( R, G, B, A )
				
				WritePixelFast X, H - SH + Y, RGB, TextureBuffer( tex, LOOP )
			Next
		Next
		
		;Bottom Right
		For X = 0 To SW - 1
			For Y = 0 To SH - 1
				RGB = ReadPixelFast( X + SW * 2, Y + IY, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
				R = (RGB Shr 16) And $FF
				G = (RGB Shr 8) And $FF
				B = RGB And $FF
				
				If R > 0 And G = 0 And B = 0
					A = R
					R = FUI_GetRed( SC_GADGET )
					G = FUI_GetGreen( SC_GADGET )
					B = FUI_GetBlue( SC_GADGET )
				ElseIf R = 255 And G = 0 And B = 255
					A = 0
				Else
					A = 255
				EndIf
				RGB = FUI_RGBToInt( R, G, B, A )
				
				WritePixelFast W - SW + X, H - SH + Y, RGB, TextureBuffer( tex, LOOP )
			Next
		Next
		
		;Left Middle
		For X = 0 To SW
			For Y = SH To H - SH - 1
				IY = Y Mod SH
				RGB = ReadPixelFast( X, IY + SH, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
				R = (RGB Shr 16) And $FF
				G = (RGB Shr 8) And $FF
				B = RGB And $FF
				
				If R > 0 And G = 0 And B = 0
					A = R
					R = FUI_GetRed( SC_GADGET )
					G = FUI_GetGreen( SC_GADGET )
					B = FUI_GetBlue( SC_GADGET )
				ElseIf R = 255 And G = 0 And B = 255
					A = 0
				Else
					A = 255
				EndIf
				RGB = FUI_RGBToInt( R, G, B, A )
				
				WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
			Next
		Next
		
		;Middle
		For X = SW To W - SW - 1
			For Y = SH To H - SH - 1
				IX = X Mod SW
				IY = Y Mod SH
				RGB = ReadPixelFast( IX + SW, IY + SH, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
				R = (RGB Shr 16) And $FF
				G = (RGB Shr 8) And $FF
				B = RGB And $FF
				
				If R > 0 And G = 0 And B = 0
					A = R
					R = FUI_GetRed( SC_GADGET )
					G = FUI_GetGreen( SC_GADGET )
					B = FUI_GetBlue( SC_GADGET )
				ElseIf R = 255 And G = 0 And B = 255
					A = 0
				Else
					A = 255
				EndIf
				RGB = FUI_RGBToInt( R, G, B, A )
				
				WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
			Next
		Next
		
		;Right Middle
		For X = 0 To SW
			For Y = SH To H - SH - 1
				IY = Y Mod SH
				RGB = ReadPixelFast( X + SW*2, IY + SH, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
				R = (RGB Shr 16) And $FF
				G = (RGB Shr 8) And $FF
				B = RGB And $FF
				
				If R > 0 And G = 0 And B = 0
					A = R
					R = FUI_GetRed( SC_GADGET )
					G = FUI_GetGreen( SC_GADGET )
					B = FUI_GetBlue( SC_GADGET )
				ElseIf R = 255 And G = 0 And B = 255
					A = 0
				Else
					A = 255
				EndIf
				RGB = FUI_RGBToInt( R, G, B, A )
				
				WritePixelFast W - SW + X, Y, RGB, TextureBuffer( tex, LOOP )
			Next
		Next
		
		UnlockBuffer TextureBuffer( tex, LOOP )
		UnlockBuffer ImageBuffer( dimg[LOOP] )
		FreeImage dimg[LOOP]
	Else
		FreeTexture tex
		tex = Null
	EndIf
	
	SetBuffer pBuffer
	
	Return tex
	
End Function

Function FUI_SkinTabPage.BBTexture( gad.TabPage )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	fName$ = SKIN_PATH$
	If Right( fName$, 1 ) <> "\"
		fName$ = fName$ + "\"
	EndIf
	
	ARGB	= (0 Or (0 Shl 8) Or (0 Shl 16) Or (Alpha Shl 24))
	
	W		= gad\W
	TW		= FUI_NextPower( W )
	H		= gad\H + 2
	TH		= FUI_NextPower( H )
	
	Local dimg.BBImage[3]
	dimg[0]		= LoadImage( fName$ + "tabpage\selected.bmp" )
	dimg[1]		= LoadImage( fName$ + "tabpage\unselected.bmp" )
	dimg[2]		= LoadImage( fName$ + "tabpage\disabled.bmp" )
	
	tex.BBTexture = FUI_CreateTexture( TW, TH, 1+2, 3 )
	
	For LOOP = 0 To 2
		If dimg[LOOP] <> Null
			SetBuffer TextureBuffer( tex, LOOP )
			
			SW = ImageWidth( dimg[LOOP] ) / 3
			SH = H
			LockBuffer ImageBuffer( dimg[LOOP] )
			
			LockBuffer TextureBuffer( tex, LOOP )
			
			IY = 0
			
			;Left
			For X = 0 To SW - 1
				For Y = 0 To SH - 1
					RGB = ReadPixelFast( X, Y + IY, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = FUI_GetRed( SC_GADGET )
						G = FUI_GetGreen( SC_GADGET )
						B = FUI_GetBlue( SC_GADGET )
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
				Next
			Next
			
			;Middle
			For X = SW To W - SW - 1
				For Y = 0 To SH - 1
					IX = X Mod SW
					RGB = ReadPixelFast( IX + SW, Y + IY, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = FUI_GetRed( SC_GADGET )
						G = FUI_GetGreen( SC_GADGET )
						B = FUI_GetBlue( SC_GADGET )
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
				Next
			Next
			
			;Right
			For X = 0 To SW - 1
				For Y = 0 To SH - 1
					RGB = ReadPixelFast( X + SW * 2, Y + IY, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = FUI_GetRed( SC_GADGET )
						G = FUI_GetGreen( SC_GADGET )
						B = FUI_GetBlue( SC_GADGET )
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast W - SW + X, Y, RGB, TextureBuffer( tex, LOOP )
				Next
			Next
			
			UnlockBuffer TextureBuffer( tex, LOOP )
			UnlockBuffer ImageBuffer( dimg[LOOP] )
			FreeImage dimg[LOOP]
		Else
			FreeTexture tex
			tex = Null
		EndIf
	Next
	
	SetBuffer pBuffer
	
	Return tex
	
End Function

Function FUI_SkinPanel.BBTexture( gad.Panel )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	fName$ = SKIN_PATH$
	If Right( fName$, 1 ) <> "\"
		fName$ = fName$ + "\"
	EndIf
	
	ARGB	= (0 Or (0 Shl 8) Or (0 Shl 16) Or (Alpha Shl 24))
	
	W		= gad\W
	TW		= FUI_NextPower( W )
	H		= PANEL_HEIGHT
	TH		= FUI_NextPower( H )
	
	Local dimg.BBImage[6]
	dimg[0]		= LoadImage( fName$ + "panel\up.bmp" )
	dimg[1]		= LoadImage( fName$ + "panel\over.bmp" )
	dimg[2]		= LoadImage( fName$ + "panel\disabled.bmp" )
	dimg[3]		= LoadImage( fName$ + "panel\up_active.bmp" )
	dimg[4]		= LoadImage( fName$ + "panel\over_active.bmp" )
	dimg[5]		= LoadImage( fName$ + "panel\disabled_active.bmp" )
	
	tex.BBTexture = FUI_CreateTexture( TW, TH, 1+2, 6 )
	
	For LOOP = 0 To 5
		If dimg[LOOP] <> Null
			SetBuffer TextureBuffer( tex, LOOP )
			
			SW = ImageWidth( dimg[LOOP] ) / 3
			SH = H
			LockBuffer ImageBuffer( dimg[LOOP] )
			
			LockBuffer TextureBuffer( tex, LOOP )
			
			IY = 0
			
			;Left
			For X = 0 To SW - 1
				For Y = 0 To SH - 1
					RGB = ReadPixelFast( X, Y + IY, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = FUI_GetRed( SC_GADGET )
						G = FUI_GetGreen( SC_GADGET )
						B = FUI_GetBlue( SC_GADGET )
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
				Next
			Next
			
			;Middle
			For X = SW To W - SW - 1
				For Y = 0 To SH - 1
					IX = X Mod SW
					RGB = ReadPixelFast( IX + SW, Y + IY, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = FUI_GetRed( SC_GADGET )
						G = FUI_GetGreen( SC_GADGET )
						B = FUI_GetBlue( SC_GADGET )
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
				Next
			Next
			
			;Right
			For X = 0 To SW - 1
				For Y = 0 To SH - 1
					RGB = ReadPixelFast( X + SW * 2, Y + IY, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = FUI_GetRed( SC_GADGET )
						G = FUI_GetGreen( SC_GADGET )
						B = FUI_GetBlue( SC_GADGET )
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast W - SW + X, Y, RGB, TextureBuffer( tex, LOOP )
				Next
			Next
			
			UnlockBuffer TextureBuffer( tex, LOOP )
			UnlockBuffer ImageBuffer( dimg[LOOP] )
			FreeImage dimg[LOOP]
		Else
			FreeTexture tex
			tex = Null
		EndIf
	Next
	
	SetBuffer pBuffer
	
	Return tex
	
End Function

Function FUI_SkinButton.BBtexture( gad.Button )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	fName$ = SKIN_PATH$
	If Right( fName$, 1 ) <> "\"
		fName$ = fName$ + "\"
	EndIf
	
	ARGB	= (0 Or (0 Shl 8) Or (0 Shl 16) Or (Alpha Shl 24))
	
	W		= gad\W
	TW		= FUI_NextPower( W )
	H		= PANEL_HEIGHT
	TH		= FUI_NextPower( H )
	
	Local dimg.BBImage[4]
	dimg[0]		= LoadImage( fName$ + "button\up.bmp" )
	dimg[1]		= LoadImage( fName$ + "button\over.bmp" )
	dimg[2]		= LoadImage( fName$ + "button\down.bmp" )
	dimg[3]		= LoadImage( fName$ + "button\disabled.bmp" )
	
	tex.BBTexture = FUI_CreateTexture( TW, TH, 1+2, 4 )
	
	For LOOP = 0 To 3
		If dimg[LOOP] <> Null
			SetBuffer TextureBuffer( tex, LOOP )
			
			SW = ImageWidth( dimg[LOOP] ) / 3
			SH = ImageHeight( dimg[LOOP] ) / 3
			LockBuffer ImageBuffer( dimg[LOOP] )
			
			LockBuffer TextureBuffer( tex, LOOP )
			
			IY = 0
			
			;Top Left
			For X = 0 To SW - 1
				For Y = 0 To SH - 1
					RGB = ReadPixelFast( X, Y + IY, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = FUI_GetRed( SC_GADGET )
						G = FUI_GetGreen( SC_GADGET )
						B = FUI_GetBlue( SC_GADGET )
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
				Next
			Next
			
			;Top Middle
			For X = SW To W - SW - 1
				For Y = 0 To SH - 1
					IX = X Mod SW
					RGB = ReadPixelFast( IX + SW, Y + IY, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = FUI_GetRed( SC_GADGET )
						G = FUI_GetGreen( SC_GADGET )
						B = FUI_GetBlue( SC_GADGET )
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
				Next
			Next
			
			;Top Right
			For X = 0 To SW - 1
				For Y = 0 To SH - 1
					RGB = ReadPixelFast( X + SW * 2, Y + IY, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = FUI_GetRed( SC_GADGET )
						G = FUI_GetGreen( SC_GADGET )
						B = FUI_GetBlue( SC_GADGET )
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast W - SW + X, Y, RGB, TextureBuffer( tex, LOOP )
				Next
			Next
			
			IY = SH * 2
			
			;Bottom Left
			For X = 0 To SW - 1
				For Y = 0 To SH - 1
					RGB = ReadPixelFast( X, Y + IY, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = FUI_GetRed( SC_GADGET )
						G = FUI_GetGreen( SC_GADGET )
						B = FUI_GetBlue( SC_GADGET )
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast X, H - SH + Y, RGB, TextureBuffer( tex, LOOP )
				Next
			Next
			
			;Bottom Middle
			For X = SW To W - SW - 1
				For Y = 0 To SH - 1
					IX = X Mod SW
					RGB = ReadPixelFast( IX + SW, Y + IY, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = FUI_GetRed( SC_GADGET )
						G = FUI_GetGreen( SC_GADGET )
						B = FUI_GetBlue( SC_GADGET )
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast X, H - SH + Y, RGB, TextureBuffer( tex, LOOP )
				Next
			Next
			
			;Bottom Right
			For X = 0 To SW - 1
				For Y = 0 To SH - 1
					RGB = ReadPixelFast( X + SW * 2, Y + IY, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = FUI_GetRed( SC_GADGET )
						G = FUI_GetGreen( SC_GADGET )
						B = FUI_GetBlue( SC_GADGET )
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast W - SW + X, H - SH + Y, RGB, TextureBuffer( tex, LOOP )
				Next
			Next
			
			;Left Middle
			For X = 0 To SW
				For Y = SH To H - SH - 1
					IY = Y Mod SH
					RGB = ReadPixelFast( X, IY + SH, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = FUI_GetRed( SC_GADGET )
						G = FUI_GetGreen( SC_GADGET )
						B = FUI_GetBlue( SC_GADGET )
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
				Next
			Next
			
			;Middle
			For X = SW To W - SW - 1
				For Y = SH To H - SH - 1
					IX = X Mod SW
					IY = Y Mod SH
					RGB = ReadPixelFast( IX + SW, IY + SH, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = FUI_GetRed( SC_GADGET )
						G = FUI_GetGreen( SC_GADGET )
						B = FUI_GetBlue( SC_GADGET )
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
				Next
			Next
			
			;Right Middle
			For X = 0 To SW
				For Y = SH To H - SH - 1
					IY = Y Mod SH
					RGB = ReadPixelFast( X + SW*2, IY + SH, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = FUI_GetRed( SC_GADGET )
						G = FUI_GetGreen( SC_GADGET )
						B = FUI_GetBlue( SC_GADGET )
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast W - SW + X, Y, RGB, TextureBuffer( tex, LOOP )
				Next
			Next
			
			UnlockBuffer TextureBuffer( tex, LOOP )
			UnlockBuffer ImageBuffer( dimg[LOOP] )
			FreeImage dimg[LOOP]
		Else
			FreeTexture tex
			tex = Null
		EndIf
	Next
	
	SetBuffer pBuffer
	
	Return tex
	
End Function

Function FUI_SkinCheckBox.BBTexture( gad.CheckBox )
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	fName$ = SKIN_PATH$
	If Right( fName$, 1 ) <> "\"
		fName$ = fName$ + "\"
	EndIf
	
	ARGB	= (0 Or (0 Shl 8) Or (0 Shl 16) Or (Alpha Shl 24))
	
	W		= gad\W
	TW		= FUI_NextPower( W )
	H		= gad\H
	TH		= FUI_NextPower( H )
	
	Local dimg.BBImage[8]
	dimg[0]		= LoadImage( fName$ + "checkbox\up.bmp" )
	dimg[1]		= LoadImage( fName$ + "checkbox\over.bmp" )
	dimg[2]		= LoadImage( fName$ + "checkbox\down.bmp" )
	dimg[3]		= LoadImage( fName$ + "checkbox\disabled.bmp" )
	dimg[4]		= LoadImage( fName$ + "checkbox\up_checked.bmp" )
	dimg[5]		= LoadImage( fName$ + "checkbox\over_checked.bmp" )
	dimg[6]		= LoadImage( fName$ + "checkbox\down_checked.bmp" )
	dimg[7]		= LoadImage( fName$ + "checkbox\disabled_checked.bmp" )
	
	tex.BBTexture = FUI_CreateTexture( TW, TH, 1+2, 8 )
	
	For LOOP = 0 To 7
		If dimg[LOOP] <> Null
			SetBuffer TextureBuffer( tex, LOOP )
			
			SW = ImageWidth( dimg[LOOP] )
			SH = ImageHeight( dimg[LOOP] )
			LockBuffer ImageBuffer( dimg[LOOP] )
			
			LockBuffer TextureBuffer( tex, LOOP )
			
			For X = 0 To SW - 1
				For Y = 0 To SH - 1
					RGB = ReadPixelFast( X, Y, ImageBuffer( dimg[LOOP] ) ) And $FFFFFF
					R = (RGB Shr 16) And $FF
					G = (RGB Shr 8) And $FF
					B = RGB And $FF
					
					If R > 0 And G = 0 And B = 0
						A = R
						R = FUI_GetRed( SC_GADGET )
						G = FUI_GetGreen( SC_GADGET )
						B = FUI_GetBlue( SC_GADGET )
					ElseIf R = 255 And G = 0 And B = 255
						A = 0
					Else
						A = 255
					EndIf
					RGB = FUI_RGBToInt( R, G, B, A )
					
					WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
				Next
			Next
			
			UnlockBuffer TextureBuffer( tex, LOOP )
			UnlockBuffer ImageBuffer( dimg[LOOP] )
			FreeImage dimg[LOOP]
		Else
			FreeTexture tex
			tex = Null
		EndIf
	Next
	
	SetBuffer pBuffer
	
	Return tex
	
End Function
;#End Region

;#Region ---------- Main F-UI Functions --------
Function FUI_Initialise( W, H, D, M, Resizable=False, Border=True, Caption$="", Version$="" )
	
	app				= New Application
	
	TW = W
	TH = H
	app\OW = W
	app\OH = H
	
	;Resizable
	If Resizable = True
		W = FUI_API_GetSystemMetrics( 0 )
		H = FUI_API_GetSystemMetrics( 1 )
	EndIf
	
	app\dirStart = CurrentDir()
	
	;Graphics
	Graphics3D W, H, D, M
	app\W = TW
	app\H = TH
	app\D = D
	app\M = M
	
	;GUI Framework
	FUI_AppTitle Caption$, Version$
	app\hWnd		= FUI_API_FindWindow( "Blitz Runtime Class", app\Caption )
	
	;Resizable
	If Resizable = True
		app\SW = W
		app\SH = H
		
		FUI_MakeAppResizable
		FUI_ResizeApp TW, TH
		FUI_CenterApp
	EndIf
	
	;Border
	app\Border = Border
	If Border = False
		FUI_RemoveBorder(  )
	EndIf
	
	app\Cam			= CreateCamera(  )
	;Sharpen GUE text cysis145
	;MoveEntity	(App\Cam, 0, 0, -100000)
	MoveEntity	(app\Cam, 0, 0, -500)
	
	app\Pivot		= CreatePivot( app\Cam )
	app\Aspect		= Float( H ) / W
	app\Scale		= 2.0 / W
	
	app\currentFont	= New Font
	;Fixed camera bug in zone editor cysis145
	CameraRange app\Cam, 0.9, 10
	
	CameraClsColor app\Cam, 100, 120, 200
	ClearTextureFilters
	AmbientLight 255, 255, 255
	
	PositionEntity app\Pivot,-1.0, app\Aspect, 1.0
	ScaleEntity app\Pivot, app\Scale,-app\Scale,-app\Scale
	
	app\A = 255
	app\A2 = 255
	
	;Skin Defaults
	FUI_SkinDefaults(  )
	
	;Mouse
	FUI_SetColor( SC_WHITE )
	app\Mouse		= FUI_CreateControl(  )
	surf.BBSurface = GetSurface( app\Mouse, 1 )
	ClearSurface surf
	
	FUI_Rect 0, 0, 32, 32, surf
	tex.BBTexture				= FUI_CreateTexture( 32, 32, 1+2+16+32, 1, 0 )
	EntityOrder app\Mouse, ORDER_MOUSE

	app\Buffer = GraphicsBuffer(  )
	
	SetBuffer TextureBuffer( tex )
	
	Restore FUI_MOUSE_ARROW
	LockBuffer TextureBuffer( tex )
	For A = 0 To 22
		For B = 0 To 22
			Read RGB
			cr = (RGB Shr 16) And $FF
			cg = (RGB Shr 8) And $FF
			cb = RGB And $FF
			If cr > 0 And cg = 0 And cb = 0
				alp = 155-cr
				RGB = 0
			Else
				alp = 255
			EndIf
			WritePixelFast B, A, RGB Or (alp Shl 24)
		Next
	Next
	UnlockBuffer TextureBuffer( tex )
	
	Restore FUI_ICON_NEW
	ICON_NEW = CreateImage( 16, 16 )
	LockBuffer ImageBuffer( ICON_NEW )
	For A = 0 To 15
		For B = 0 To 15
			Read RGB
			WritePixelFast B, A, RGB Or (alp Shl 24), ImageBuffer( ICON_NEW )
		Next
	Next
	UnlockBuffer ImageBuffer( ICON_NEW )
	
	Restore FUI_ICON_OPEN
	ICON_OPEN = CreateImage( 16, 16 )
	LockBuffer ImageBuffer( ICON_OPEN )
	For A = 0 To 15
		For B = 0 To 15
			Read RGB
			WritePixelFast B, A, RGB Or (alp Shl 24), ImageBuffer( ICON_OPEN )
		Next
	Next
	UnlockBuffer ImageBuffer( ICON_OPEN )
	
	Restore FUI_ICON_SAVE
	ICON_SAVE = CreateImage( 16, 16 )
	LockBuffer ImageBuffer( ICON_SAVE )
	For A = 0 To 15
		For B = 0 To 15
			Read RGB
			WritePixelFast B, A, RGB Or (alp Shl 24), ImageBuffer( ICON_SAVE )
		Next
	Next
	UnlockBuffer ImageBuffer( ICON_SAVE )
	
	;Tool Tip
	app\ToolTipDelay		= 30
	app\ToolTipMesh			= FUI_CreateControl( app\Pivot )
	EntityOrder app\ToolTipMesh, ORDER_TOOLTIP
	
	SetBuffer app\Buffer
	
	EntityTexture app\Mouse, tex
	FreeTexture tex
	
	;3D FrameWork
	app\FRate = 60
	app\FPeriod = 1000 / app\FRate
	app\FTime = MilliSecs(  ) - app\FPeriod
	
End Function

Function FUI_Update( Idle = False )
	
	FUI_UpdateMouse()
	FUI_UpdateToolTip app\TempTip
	
	If WINDOW_RESIZE_METHOD = 0
		If (FUI_API_GetWindowLong( app\hWnd,-16 ) And $40000) = $40000
			;Sizable window border
			W = FUI_API_GetSystemMetrics( 32 )
			H = FUI_API_GetSystemMetrics( 33 )
			
			bnkRECT.BBBank = CreateBank( 16 )
			FUI_API_GetWindowRect( app\hWnd, bnkRECT )
			winX = PeekInt( bnkRECT, 0 )
			winY = PeekInt( bnkRECT, 4 )
			winW = PeekInt( bnkRECT, 8 ) - PeekInt( bnkRECT, 0 )
			winH = PeekInt( bnkRECT, 12 ) - PeekInt( bnkRECT, 4 )
			
			FUI_API_GetClientRect( app\hWnd, bnkRECT )
			winCW = PeekInt( bnkRECT, 8 ) - PeekInt( bnkRECT, 0 )
			winCH = PeekInt( bnkRECT, 12 ) - PeekInt( bnkRECT, 4 )
	
			If winCW <> app\W Or winCH <> app\H
				app\W = winCW
				app\H = winCH
			EndIf
			
			FreeBank bnkRECT
			bnkRECT = Null
			
			If FUI_AppMouseOver( winX + winW - W, winY, W, winH ) = True
				If FUI_AppMouseOver( winX, winY + winH - H, winW, H ) = True
					app\Win32Mouse = IDCSizeNW
				Else
					app\Win32Mouse = IDCSizeWE
				EndIf
			Else
				If FUI_AppMouseOver( winX, winY + winH - H, winW, H ) = True
					app\Win32Mouse = IDCSizeNS
				EndIf
			EndIf
		EndIf
	Else
		If (FUI_API_GetWindowLong( app\hWnd,-16 ) And $10000) = $10000
			;Sizable window border
			W = FUI_API_GetSystemMetrics( 32 )
			H = FUI_API_GetSystemMetrics( 33 )
			
			bnkRECT = CreateBank( 16 )
			FUI_API_GetWindowRect( app\hWnd, bnkRECT )
			winX = PeekInt( bnkRECT, 0 )
			winY = PeekInt( bnkRECT, 4 )
			winW = PeekInt( bnkRECT, 8 ) - PeekInt( bnkRECT, 0 )
			winH = PeekInt( bnkRECT, 12 ) - PeekInt( bnkRECT, 4 )
			
			FUI_API_GetClientRect( app\hWnd, bnkRECT )
			winCW = PeekInt( bnkRECT, 8 ) - PeekInt( bnkRECT, 0 )
			winCH = PeekInt( bnkRECT, 12 ) - PeekInt( bnkRECT, 4 )
			
			FreeBank bnkRECT
			bnkRECT = Null
			
			If FUI_AppMouseOver( winX + winW - W*5, winY + winH - H*5, W*4, H*4 ) = True
				app\Win32Mouse = IDCSizeNW
				If app\MB1 = 1
					app\Resizing = True
					app\OffX = winCW - app\MX
					app\OffY = winCH - app\MY
				EndIf
				app\OverBorder = False
			Else
				If FUI_AppMouseOver( winX + winW - W*2, winY, W, winH ) = True
					app\Win32Mouse = IDCSizeWE
					If app\MB1 = 1
						app\ResizingX = True
						app\OffX = winCW - app\MX
						app\OffY = winCH - app\MY
					EndIf
				ElseIf FUI_AppMouseOver( winX, winY + winH - H*2, winW, H ) = True
					app\Win32Mouse = IDCSizeNS
					If app\MB1 = 1
						app\ResizingY = True
						app\OffX = winCW - app\MX
						app\OffY = winCH - app\MY
					EndIf
				Else
					app\OverBorder = False
				EndIf
			EndIf
			If app\MB1 = 2
				If app\Resizing = True
					app\Win32Mouse = IDCSizeNW
					FUI_ResizeApp( app\MX + app\OffX, app\MY + app\OffY )
				ElseIf app\ResizingX = True
					app\Win32Mouse = IDCSizeWE
					FUI_ResizeApp( app\MX + app\OffX, winCH )
				ElseIf app\ResizingY = True
					app\Win32Mouse = IDCSizeNS
					FUI_ResizeApp( winCW, app\MY + app\OffY )
				EndIf
			ElseIf app\MB1 = 0 Or app\MB1 = 3
				app\Resizing = False
				app\ResizingX = False
				app\ResizingY = False
				
				FUI_ResizeApp( winCW, winCH )
			EndIf
		EndIf
	EndIf
	
	If app\Border = False
		FUI_RemoveBorder(  )
	EndIf
	
	If FUI_IsAppIdle(  ) = False
		app\Idle = False
	EndIf
	
	If app\Idle = False Or Idle = False
		If RENDER_MODE = 1
			CameraClsMode app\Cam, 0, 1
			RenderWorld
		EndIf
		CameraClsMode app\Cam, 0, 1
		app\overView = 0
		FUI_UpdateWindow(  )
		
		FUI_UpdateContextMenu(  )
	EndIf
	If FUI_IsAppIdle(  ) = True
		app\Idle = True
	EndIf
	
End Function

Function FUI_Destroy(  )
	
	;Delete all gadgets from memory in just one line of code
	FreeEntity app\Pivot
	FreeEntity app\Cam
	
	Delete Each Window
	Delete Each MenuTitle
	Delete Each MenuItem
	Delete Each ContextMenu
	Delete Each ContextMenuItem
	Delete Each Tab
	Delete Each TabPage
	Delete Each Panel
	Delete Each Button
	Delete Each CheckBox
	Delete Each ComboBox
	Delete Each ComboBoxItem
	Delete Each GroupBox
	For img.ImageBox = Each ImageBox
		If img\Image <> Null
			FreeImage img\Image
			img\Image = Null
		EndIf
		Delete img
	Next
	Delete Each Label
	Delete Each ListBox
	Delete Each ListBoxItem
	Delete Each ProgressBar
	Delete Each Radio
	Delete Each ScrollBar
	Delete Each Slider
	Delete Each Spinner
	Delete Each TextBox
	Delete Each TreeView
	Delete Each Node
	For view.View = Each View
		FreeEntity view\Cam
		
		Delete view
	Next
	For m.Mesh = Each Mesh
		If m\Mesh <> Null FreeEntity m\Mesh
			m\Mesh = Null
		
		Delete m
	Next
	
	If ICON_NEW <> Null
		FreeImage ICON_NEW
		ICON_NEW = Null
	EndIf
	If ICON_OPEN <> Null
		FreeImage ICON_OPEN
		ICON_OPEN = Null
	EndIf
	If ICON_SAVE <> Null
		FreeImage ICON_SAVE
		ICON_SAVE = Null
	EndIf
	
	Delete Each Event
	
	Delete Each CHOOSEFONT
	Delete Each Font
	Delete Each RecentFile
	
	Delete Each Application
	app = Null
	
End Function

Function FUI_HideGUI(  )

	HideEntity app\Pivot
	
End Function

Function FUI_ShowGUI(  )

	ShowEntity app\Pivot
	
End Function
;#End Region

;#Region ---------- General F-UI Functions -----
;Function FUI_3DText( X, Y, Caption$, W=0, H=0 )
;
;	NumLines = FUI_CountItems( Caption$, Chr(10) )
;	If W = 0
;		For A = 0 To NumLines - 1
;			If StringWidth( FUI_Parse( Caption$, A, Chr(10) ) ) > W
;				W = StringWidth( FUI_Parse( Caption$, A, Chr(10) ) )
;			EndIf
;		Next
;	EndIf
;	If H = 0
;		H = FontHeight(  ) * NumLines
;	EndIf
;	
;	surf.BBSurface = GetSurface( app\Pivot, 1 )
;	mesh.BBEntity = FUI_Rect( X, Y, W, H, surf )
;	tex.BBTexture = FUI_CreateTexture( W, H, 1+2, 1 )
;	
;	app\Buffer = GraphicsBuffer(  )
;	
;	SetBuffer TextureBuffer( tex )
;	
;	For A = 0 To NumLines - 1
;		FUI_Text tex, 0, A * FontHeight(), FUI_Parse( Caption$, A, Chr(10) )
;	Next
;	
;	EntityTexture mesh, tex
;	FreeTexture tex
;	
;	EntityFX mesh, 1
;	EntityOrder mesh, ORDER_TEXT
;	
;	SetBuffer app\Buffer
;	
;	Return mesh
;
;End Function

Function FUI_EditText.BBEntity( mesh.BBEntity, Caption$ )

	tex.BBTexture = FUI_GadgetTexture( mesh )
	FUI_ClearTexture tex
	
	app\Buffer = GraphicsBuffer(  )
	
	SetBuffer TextureBuffer( tex )
	FUI_Text tex, 0, 0, Caption$
	
	EntityTexture mesh, tex
	FreeTexture tex
	
	SetBuffer app\Buffer
	
	Return mesh

End Function

Function FUI_ToolTip( Parent, Caption$="Tool Tip" )
	
	gad.ToolTip		= New ToolTip

	gad\Parent		= Parent
	gad\Caption$	= Caption$

	Return Handle( gad )
	
End Function

Function FUI_SetToolTip( Caption$ )

	app\TempTip = Caption$

End Function

Function FUI_ExecFile( fName$ )
	
	ExecFile Chr(34) + fName$ + Chr(34)
	
End Function

Function FUI_RGBToInt( Red, Green, Blue, Alpha=255 )
	
	If Alpha >-1
		Return (Alpha Shl 24) Or (Red Shl 16) Or (Green Shl 8) Or Blue
	Else
		Return (Red Shl 16) Or (Green Shl 8) Or Blue
	EndIf
	
End Function

Function FUI_SetMask( R=255, G=0, B=0 )
	
	SC_MASK = FUI_RGBToInt( R, G, B )
	
End Function

Function FUI_GetAlpha( RGB )

	Return (RGB Shr 24) And $FF
	
End Function

Function FUI_GetRed( RGB )

	Return (RGB Shr 16) And $FF
	
End Function

Function FUI_GetGreen( RGB )

	Return (RGB Shr 8) And $FF
	
End Function

Function FUI_GetBlue( RGB )

	Return RGB And $FF
	
End Function

Function FUI_GetColor(  )
	
	Return (255 Shl 24) Or (ColorRed() Shl 16) Or (ColorGreen() Shl 8) Or ColorBlue()

End Function

Function FUI_HideMouse(  )
	
	HideEntity app\Mouse
	
End Function

Function FUI_ShowMouse(  )
	
	ShowEntity app\Mouse
	
End Function

Function FUI_LoadSkin( fName$ )
	
	file.BBStream = ReadFile( fName$ )
	If file <> Null
		
		;Colors
		SC_FORM										= ReadInt( file )
		SC_FORM_BORDER								= ReadInt( file )
		SC_TITLEBAR									= ReadInt( file )
		SC_TITLEBAR_TEXT							= ReadInt( file )
		SC_MENUBAR									= ReadInt( file )
		SC_MENUBAR_BORDER							= ReadInt( file )
		SC_STATUSBAR								= ReadInt( file )
		SC_STATUSBAR_TEXT							= ReadInt( file )
		SC_STATUSBAR_BORDER							= ReadInt( file )
		
		SC_MENUTITLE								= ReadInt( file )
		SC_MENUTITLE_OVER							= ReadInt( file )
		SC_MENUTITLE_SEL							= ReadInt( file )
		SC_MENUTITLE_TEXT							= ReadInt( file )
		SC_MENUTITLE_TEXT_OVER						= ReadInt( file )
		SC_MENUTITLE_TEXT_SEL						= ReadInt( file )
		SC_MENUTITLE_BORDER							= ReadInt( file )
		SC_MENUTITLE_BORDER_OVER					= ReadInt( file )
		SC_MENUTITLE_BORDER_SEL						= ReadInt( file )
		
		SC_MENUITEM									= ReadInt( file )
		SC_MENUITEM_OVER							= ReadInt( file )
		SC_MENUITEM_SEL								= ReadInt( file )
		SC_MENUITEM_TEXT							= ReadInt( file )
		SC_MENUITEM_TEXT_OVER						= ReadInt( file )
		SC_MENUITEM_TEXT_SEL						= ReadInt( file )
		SC_MENUITEM_BORDER							= ReadInt( file )
		SC_MENUITEM_BORDER_OVER						= ReadInt( file )
		SC_MENUITEM_BORDER_SEL						= ReadInt( file )
		
		SC_MENUDROPDOWN								= ReadInt( file )
		SC_MENUDROPDOWN_BORDER						= ReadInt( file )
		SC_MENUDROPDOWN_STRIP						= ReadInt( file )
		
		SC_GADGET									= ReadInt( file )
		SC_GADGET_TEXT								= ReadInt( file )
		SC_GADGET_COLOR								= ReadInt( file )
		SC_GADGET_COLOR_TEXT						= ReadInt( file )
		SC_GADGET_BORDER							= ReadInt( file )
		
		SC_INPUT									= ReadInt( file )
		SC_INPUT_TEXT								= ReadInt( file )
		SC_INPUT_COLOR								= ReadInt( file )
		SC_INPUT_COLOR_TEXT							= ReadInt( file )
		SC_INPUT_BORDER								= ReadInt( file )
		
		CloseFile file
	EndIf
	
End Function

Function FUI_SaveSkin( fName$ )
	
	file.BBStream = WriteFile( fName$ )
	If file <> Null
		
		;Colors
		WriteInt file, SC_FORM
		WriteInt file, SC_FORM_BORDER
		WriteInt file, SC_TITLEBAR
		WriteInt file, SC_TITLEBAR_TEXT
		WriteInt file, SC_MENUBAR
		WriteInt file, SC_MENUBAR_BORDER
		WriteInt file, SC_STATUSBAR
		WriteInt file, SC_STATUSBAR_TEXT
		WriteInt file, SC_STATUSBAR_BORDER
		
		WriteInt file, SC_MENUTITLE
		WriteInt file, SC_MENUTITLE_OVER
		WriteInt file, SC_MENUTITLE_SEL
		WriteInt file, SC_MENUTITLE_TEXT
		WriteInt file, SC_MENUTITLE_TEXT_OVER
		WriteInt file, SC_MENUTITLE_TEXT_SEL
		WriteInt file, SC_MENUTITLE_BORDER
		WriteInt file, SC_MENUTITLE_BORDER_OVER
		WriteInt file, SC_MENUTITLE_BORDER_SEL
		
		WriteInt file, SC_MENUITEM
		WriteInt file, SC_MENUITEM_OVER
		WriteInt file, SC_MENUITEM_SEL
		WriteInt file, SC_MENUITEM_TEXT
		WriteInt file, SC_MENUITEM_TEXT_OVER
		WriteInt file, SC_MENUITEM_TEXT_SEL
		WriteInt file, SC_MENUITEM_BORDER
		WriteInt file, SC_MENUITEM_BORDER_OVER
		WriteInt file, SC_MENUITEM_BORDER_SEL
		
		WriteInt file, SC_MENUDROPDOWN
		WriteInt file, SC_MENUDROPDOWN_BORDER
		WriteInt file, SC_MENUDROPDOWN_STRIP

		WriteInt file, SC_GADGET
		WriteInt file, SC_GADGET_TEXT
		WriteInt file, SC_GADGET_COLOR
		WriteInt file, SC_GADGET_COLOR_TEXT
		WriteInt file, SC_GADGET_BORDER
		
		WriteInt file, SC_INPUT
		WriteInt file, SC_INPUT_TEXT
		WriteInt file, SC_INPUT_COLOR
		WriteInt file, SC_INPUT_COLOR_TEXT
		WriteInt file, SC_INPUT_BORDER
		
		CloseFile file
	EndIf
	
End Function

Function FUI_RecentFile( FileName$, displayname$="" )	
	
	rec.RecentFile		= New RecentFile
	rec\FileName		= FileName$
	If displayname$ = "" displayname$ = FileName$
	rec\displayname	= displayname$
	
End Function
;#End Region

;#Region ---------- Window Functions -----------
Function FUI_SetDragRegion( ID, X, Y, W, H )
	
	win.Window = Object.Window( ID )
	If win <> Null
		win\DragX = X
		win\DragY = Y
		win\DragW = W
		win\DragH = H
	EndIf
	
End Function

Function FUI_LockWindow( ID, Lock=True )
	
	win.Window = Object.Window( ID )
	If win <> Null
		win\Locked = Lock
		Return True
	Else
		Return False
	EndIf
	
End Function

Function FUI_ModalWindow( ID, Modal=True )
	
	win.Window = Object.Window( ID )
	If win <> Null
		win\Modal = Modal
		
		If Modal = True
			Insert win Before First Window
		EndIf
		Return True
	Else
		Return False
	EndIf
	
End Function

Function FUI_CenterWindow( ID )
	
	win.Window = Object.Window( ID )
	If win <> Null
		win\X = app\W / 2 - win\W / 2
		win\Y = app\H / 2 - win\H / 2
		PositionEntity win\Mesh, win\X - 0.5, win\Y - 0.5, 0.0
		
		Return True
	Else
		Return False
	EndIf
	
End Function

Function FUI_MaximiseWindow( ID )
	
	win.Window = Object.Window( ID )
	If win <> Null
		win\Minimised = False
		ShowEntity win\Mesh
		HideEntity win\MinMesh
		Return True
	Else
		Return False
	EndIf
	
End Function

Function FUI_MinimiseWindow( ID )
	
	win.Window = Object.Window( ID )
	If win <> Null
		win\Minimised = False
		HideEntity win\Mesh
		ShowEntity win\MinMesh
		Return True
	Else
		Return False
	EndIf
	
End Function

Function FUI_SetTitleBarText( ID, Caption$ )
	
	win.Window = Object.Window( ID )
	If win <> Null
		win\Caption = Caption$
		
		app\Buffer = GraphicsBuffer(  )
		
		If win\TitleBarText <> Null FreeEntity win\TitleBarText
			win\TitleBarText = Null
		
		win\TitleBarText = FUI_CreateControl( win\TitleBar )
		surf.BBSurface = GetSurface( win\TitleBarText, 1 )
		
		SetFont app\fntWindow
		FUI_SetColor( SC_WHITE )
		W = TITLEBAR_HEIGHT + StringWidth( win\Caption )
		TW = 0
		If (win\Flags And WS_CLOSEBUTTON) = WS_CLOSEBUTTON
			TW = TITLEBAR_HEIGHT
		EndIf
		If (win\Flags And WS_MINBUTTON) = WS_MINBUTTON
			TW = TW + TITLEBAR_HEIGHT
		EndIf
		If W > win\W - WINDOW_BORDER*2 - TW
			W = win\W - WINDOW_BORDER*2 - TW
		EndIf
		FUI_Rect WINDOW_BORDER, WINDOW_BORDER, W, TITLEBAR_HEIGHT, surf
		tex.BBTexture = FUI_CreateTexture( W, TITLEBAR_HEIGHT, 1+2, 1, 0 )
		SetBuffer TextureBuffer( tex )
		
		If win\Icon <> Null
			X = TITLEBAR_HEIGHT
		Else
			X = 5
		EndIf
		FUI_SetColor( SC_TITLEBAR_TEXT )
		FUI_Text tex, X, TITLEBAR_HEIGHT/2, win\Caption, 0, 1
		
		EntityTexture win\TitleBarText, tex
		FreeTexture tex
		
		EntityOrder win\TitleBarText, win\Order-1
		
		SetBuffer app\Buffer
		
		Return True
	Else
		Return False
	EndIf
	
End Function

Function FUI_SetStatusBarText( ID, Caption$ )
	
	win.Window = Object.Window( ID )
	If win <> Null
		win\StatusCaption = Caption$
		
		pBuffer.BBBuffer = GraphicsBuffer(  )
		
		If win\StatusBarText <> Null
			surf.BBSurface = GetSurface( win\StatusBarText, 1 )
			
			FUI_SetColor( SC_WHITE )
			SetFont app\fntWindow
			
			If StringWidth( win\StatusCaption ) < win\W - 5 - 2
				W = StringWidth( win\StatusCaption )
			Else
				W = win\W - 5 - 2
			EndIf
			
			FUI_Rect 1+5, win\H - STATUSBAR_HEIGHT, W, STATUSBAR_HEIGHT, surf
			tex.BBTexture = FUI_CreateTexture( W, STATUSBAR_HEIGHT, 1+2, 1, 0 )
			SetBuffer TextureBuffer( tex )
			
			FUI_SetColor( SC_STATUSBAR_TEXT )
			FUI_Text tex, 0, STATUSBAR_HEIGHT/2, win\StatusCaption, 0, 1
			
			EntityTexture win\StatusBarText, tex
			FreeTexture tex
			
			EntityOrder win\StatusBarText, win\Order-1
			
			SetBuffer pBuffer
		EndIf
		
		Return True
	Else
		Return False
	EndIf
	
End Function
;#End Region

;#Region ---------- Gadget Functions -----------
Function FUI_OpenContextMenu( ID )
	
	cmnu.ContextMenu = Object.ContextMenu( ID )
	If cmnu <> Null
		cmnu\DX = app\MX
		cmnu\DY = app\MY
		
		ShowEntity cmnu\DropDown
		PositionEntity cmnu\DropDown, cmnu\DX - 0.5, cmnu\DY - 0.5, 0.0
		
		app\actContextMenu = cmnu
	EndIf
	
End Function

Function FUI_DeleteGadget( ID )
	
	win.Window = Object.Window( ID )
	If win <> Null
		For reg.Region = Each Region
			If reg\Owner = win
				FUI_DeleteGadget( Handle( reg ) )
			EndIf
		Next
		For Tab.Tab = Each Tab
			If Tab\Owner = win
				FUI_DeleteGadget( Handle( Tab ) )
			EndIf
		Next
		For pan.Panel = Each Panel
			If pan\Owner = win
				FUI_DeleteGadget( Handle( pan ) )
			EndIf
		Next
		For btn.Button = Each Button
			If btn\Owner = win
				FUI_DeleteGadget( Handle( btn ) )
			EndIf
		Next
		For chk.CheckBox = Each CheckBox
			If chk\Owner = win
				FUI_DeleteGadget( Handle( chk ) )
			EndIf
		Next
		For cbo.ComboBox = Each ComboBox
			If cbo\Owner = win
				FUI_DeleteGadget( Handle( cbo ) )
			EndIf
		Next
		For grp.GroupBox = Each GroupBox
			If grp\Owner = win
				FUI_DeleteGadget( Handle( grp ) )
			EndIf
		Next
		For img.ImageBox = Each ImageBox
			If img\Owner = win
				FUI_DeleteGadget( Handle( img ) )
			EndIf
		Next
		For lbl.Label = Each Label
			If lbl\Owner = win
				FUI_DeleteGadget( Handle( lbl ) )
			EndIf
		Next
		For lst.ListBox = Each ListBox
			If lst\Owner = win
				FUI_DeleteGadget( Handle( lst ) )
			EndIf
		Next
		For prg.ProgressBar = Each ProgressBar
			If prg\Owner = win
				FUI_DeleteGadget( Handle( prg ) )
			EndIf
		Next
		For rad.Radio = Each Radio
			If rad\Owner = win
				FUI_DeleteGadget( Handle( rad ) )
			EndIf
		Next
		For scroll.ScrollBar = Each ScrollBar
			If scroll\Owner = win
				FUI_DeleteGadget( Handle( scroll ) )
			EndIf
		Next
		For sld.Slider = Each Slider
			If sld\Owner = win
				FUI_DeleteGadget( Handle( sld ) )
			EndIf
		Next
		For spn.Spinner = Each Spinner
			If spn\Owner = win
				FUI_DeleteGadget( Handle( spn ) )
			EndIf
		Next
		For txt.TextBox = Each TextBox
			If txt\Owner = win
				FUI_DeleteGadget( Handle( txt ) )
			EndIf
		Next
		For tree.TreeView = Each TreeView
			If tree\Owner = win
				FUI_DeleteGadget( Handle( tree ) )
			EndIf
		Next
		For view.View = Each View
			If view\Owner = win
				FUI_DeleteGadget( Handle( view ) )
			EndIf
		Next
		
		FreeEntity win\Mesh
		Delete win
		
		Return True
	EndIf
	mnut.MenuTitle = Object.MenuTitle( ID )
	If mnut <> Null
		For mnui.MenuItem = Each MenuItem
			If mnui\Owner = mnut
				FUI_DeleteGadget( Handle( mnui ) )
			EndIf
		Next
		
		FreeEntity mnut\Mesh
		Delete mnut
		
		Return True
	EndIf
	mnui.MenuItem = Object.MenuItem( ID )
	If mnui <> Null
		For mnui2.MenuItem = Each MenuItem
			If mnui2\Parent = mnui
				FUI_DeleteGadget( Handle( mnui2 ) )
			EndIf
		Next
		
		FreeEntity mnui\Mesh
		Delete mnui
		
		Return True
	EndIf
	cmnu.ContextMenu = Object.ContextMenu( ID )
	If cmnu <> Null
		For cmnui.ContextMenuItem = Each ContextMenuItem
			If cmnui\Owner = cmnu
				FUI_DeleteGadget( Handle( cmnui ) )
			EndIf
		Next
		
		FreeEntity cmnu\DropDown
		Delete cmnu
		
		Return True
	EndIf
	cmnui.ContextMenuItem = Object.ContextMenuItem( ID )
	If cmnui <> Null
		For cmnui2.ContextMenuItem = Each ContextMenuItem
			If cmnui2\Parent = cmnui
				FUI_DeleteGadget( Handle( cmnui2 ) )
			EndIf
		Next
		
		FreeEntity cmnui\Mesh
		Delete cmnui
		
		Return True
	EndIf
	reg.Region = Object.Region( ID )
	If reg <> Null
		For reg2.Region = Each Region
			If reg2\Parent = ID
				FUI_DeleteGadget( Handle( reg2 ) )
			EndIf
		Next
		For Tab.Tab = Each Tab
			If Tab\Parent = ID
				FUI_DeleteGadget( Handle( Tab ) )
			EndIf
		Next
		For pan.Panel = Each Panel
			If pan\Parent = ID
				FUI_DeleteGadget( Handle( pan ) )
			EndIf
		Next
		For btn.Button = Each Button
			If btn\Parent = ID
				FUI_DeleteGadget( Handle( btn ) )
			EndIf
		Next
		For chk.CheckBox = Each CheckBox
			If chk\Parent = ID
				FUI_DeleteGadget( Handle( chk ) )
			EndIf
		Next
		For cbo.ComboBox = Each ComboBox
			If cbo\Parent = ID
				FUI_DeleteGadget( Handle( cbo ) )
			EndIf
		Next
		For grp.GroupBox = Each GroupBox
			If grp\Parent = ID
				FUI_DeleteGadget( Handle( grp ) )
			EndIf
		Next
		For img.ImageBox = Each ImageBox
			If img\Parent = ID
				FUI_DeleteGadget( Handle( img ) )
			EndIf
		Next
		For lbl.Label = Each Label
			If lbl\Parent = ID
				FUI_DeleteGadget( Handle( lbl ) )
			EndIf
		Next
		For lst.ListBox = Each ListBox
			If lst\Parent = ID
				FUI_DeleteGadget( Handle( lst ) )
			EndIf
		Next
		For prg.ProgressBar = Each ProgressBar
			If prg\Parent = ID
				FUI_DeleteGadget( Handle( prg ) )
			EndIf
		Next
		For rad.Radio = Each Radio
			If rad\Parent = ID
				FUI_DeleteGadget( Handle( rad ) )
			EndIf
		Next
		For scroll.ScrollBar = Each ScrollBar
			If scroll\Parent = ID
				FUI_DeleteGadget( Handle( scroll ) )
			EndIf
		Next
		For sld.Slider = Each Slider
			If sld\Parent = ID
				FUI_DeleteGadget( Handle( sld ) )
			EndIf
		Next
		For spn.Spinner = Each Spinner
			If spn\Parent = ID
				FUI_DeleteGadget( Handle( spn ) )
			EndIf
		Next
		For txt.TextBox = Each TextBox
			If txt\Parent = ID
				FUI_DeleteGadget( Handle( txt ) )
			EndIf
		Next
		For tree.TreeView = Each TreeView
			If tree\Parent = ID
				FUI_DeleteGadget( Handle( tree ) )
			EndIf
		Next
		For view.View = Each View
			If view\Parent = ID
				FUI_DeleteGadget( Handle( view ) )
			EndIf
		Next
		FreeEntity reg\Mesh
		Delete reg
		
		Return True
	EndIf
	Tab.Tab = Object.Tab( ID )
	If Tab <> Null
		For tabp.TabPage = Each TabPage
			If tabp\Owner = Tab
				FUI_DeleteGadget Handle( tabp )
			EndIf
		Next
		FreeEntity Tab\Mesh
		Delete Tab
		
		Return True
	EndIf
	tabp.TabPage = Object.TabPage( ID )
	If tabp <> Null
		For reg.Region = Each Region
			If reg\Parent = ID
				FUI_DeleteGadget( Handle( reg ) )
			EndIf
		Next
		For Tab.Tab = Each Tab
			If Tab\Parent = ID
				FUI_DeleteGadget( Handle( Tab ) )
			EndIf
		Next
		For pan.Panel = Each Panel
			If pan\Parent = ID
				FUI_DeleteGadget( Handle( pan ) )
			EndIf
		Next
		For btn.Button = Each Button
			If btn\Parent = ID
				FUI_DeleteGadget( Handle( btn ) )
			EndIf
		Next
		For chk.CheckBox = Each CheckBox
			If chk\Parent = ID
				FUI_DeleteGadget( Handle( chk ) )
			EndIf
		Next
		For cbo.ComboBox = Each ComboBox
			If cbo\Parent = ID
				FUI_DeleteGadget( Handle( cbo ) )
			EndIf
		Next
		For grp.GroupBox = Each GroupBox
			If grp\Parent = ID
				FUI_DeleteGadget( Handle( grp ) )
			EndIf
		Next
		For img.ImageBox = Each ImageBox
			If img\Parent = ID
				FUI_DeleteGadget( Handle( img ) )
			EndIf
		Next
		For lbl.Label = Each Label
			If lbl\Parent = ID
				FUI_DeleteGadget( Handle( lbl ) )
			EndIf
		Next
		For lst.ListBox = Each ListBox
			If lst\Parent = ID
				FUI_DeleteGadget( Handle( lst ) )
			EndIf
		Next
		For prg.ProgressBar = Each ProgressBar
			If prg\Parent = ID
				FUI_DeleteGadget( Handle( prg ) )
			EndIf
		Next
		For rad.Radio = Each Radio
			If rad\Parent = ID
				FUI_DeleteGadget( Handle( rad ) )
			EndIf
		Next
		For scroll.ScrollBar = Each ScrollBar
			If scroll\Parent = ID
				FUI_DeleteGadget( Handle( scroll ) )
			EndIf
		Next
		For sld.Slider = Each Slider
			If sld\Parent = ID
				FUI_DeleteGadget( Handle( sld ) )
			EndIf
		Next
		For spn.Spinner = Each Spinner
			If spn\Parent = ID
				FUI_DeleteGadget( Handle( spn ) )
			EndIf
		Next
		For txt.TextBox = Each TextBox
			If txt\Parent = ID
				FUI_DeleteGadget( Handle( txt ) )
			EndIf
		Next
		For tree.TreeView = Each TreeView
			If tree\Parent = ID
				FUI_DeleteGadget( Handle( tree ) )
			EndIf
		Next
		For view.View = Each View
			If view\Parent = ID
				FUI_DeleteGadget( Handle( view ) )
			EndIf
		Next
		FreeEntity tabp\Mesh
		Delete tabp
		
		Return True
	EndIf
	pan.Panel = Object.Panel( ID )
	If pan <> Null
		For Tab.Tab = Each Tab
			If Tab\Parent = ID
				FUI_DeleteGadget( Handle( Tab ) )
			EndIf
		Next
		For pan2.Panel = Each Panel
			If pan2\Parent = ID
				FUI_DeleteGadget( Handle( pan2 ) )
			EndIf
		Next
		For btn.Button = Each Button
			If btn\Parent = ID
				FUI_DeleteGadget( Handle( btn ) )
			EndIf
		Next
		For chk.CheckBox = Each CheckBox
			If chk\Parent = ID
				FUI_DeleteGadget( Handle( chk ) )
			EndIf
		Next
		For cbo.ComboBox = Each ComboBox
			If cbo\Parent = ID
				FUI_DeleteGadget( Handle( cbo ) )
			EndIf
		Next
		For grp.GroupBox = Each GroupBox
			If grp\Parent = ID
				FUI_DeleteGadget( Handle( grp ) )
			EndIf
		Next
		For img.ImageBox = Each ImageBox
			If img\Parent = ID
				FUI_DeleteGadget( Handle( img ) )
			EndIf
		Next
		For lbl.Label = Each Label
			If lbl\Parent = ID
				FUI_DeleteGadget( Handle( lbl ) )
			EndIf
		Next
		For lst.ListBox = Each ListBox
			If lst\Parent = ID
				FUI_DeleteGadget( Handle( lst ) )
			EndIf
		Next
		For prg.ProgressBar = Each ProgressBar
			If prg\Parent = ID
				FUI_DeleteGadget( Handle( prg ) )
			EndIf
		Next
		For rad.Radio = Each Radio
			If rad\Parent = ID
				FUI_DeleteGadget( Handle( rad ) )
			EndIf
		Next
		For scroll.ScrollBar = Each ScrollBar
			If scroll\Parent = ID
				FUI_DeleteGadget( Handle( scroll ) )
			EndIf
		Next
		For sld.Slider = Each Slider
			If sld\Parent = ID
				FUI_DeleteGadget( Handle( sld ) )
			EndIf
		Next
		For spn.Spinner = Each Spinner
			If spn\Parent = ID
				FUI_DeleteGadget( Handle( spn ) )
			EndIf
		Next
		For txt.TextBox = Each TextBox
			If txt\Parent = ID
				FUI_DeleteGadget( Handle( txt ) )
			EndIf
		Next
		For tree.TreeView = Each TreeView
			If tree\Parent = ID
				FUI_DeleteGadget( Handle( tree ) )
			EndIf
		Next
		For view.View = Each View
			If view\Parent = ID
				FUI_DeleteGadget( Handle( view ) )
			EndIf
		Next
		
		FreeEntity pan\Mesh
		Delete pan
		
		Return True
	EndIf
	btn.Button = Object.Button( ID )
	If btn <> Null
		FreeEntity btn\Mesh
		Delete btn
		
		Return True
	EndIf
	chk.CheckBox = Object.CheckBox( ID )
	If chk <> Null
		FreeEntity chk\Mesh
		Delete chk
		
		Return True
	EndIf
	cbo.ComboBox = Object.ComboBox( ID )
	If cbo <> Null
		For cboi.ComboBoxItem = Each ComboBoxItem
			If cboi\Owner = cbo
				FUI_DeleteGadget Handle( cboi )
			EndIf
		Next
		FreeEntity cbo\VScroll\Mesh
		Delete cbo\VScroll
		FreeEntity cbo\Mesh
		Delete cbo
		
		Return True
	EndIf
	cboi.ComboBoxItem = Object.ComboBoxItem( ID )
	If cboi <> Null
		For cboi2.ComboBoxItem = Each ComboBoxItem
			If cboi2\Owner = cboi\Owner And cboi2\Y > cboi\Y
				If cboi2\Caption = "-"
					cboi2\Y = cboi2\Y - COMBOBOXITEM_HEIGHT / 2
				Else
					cboi2\Y = cboi2\Y - COMBOBOXITEM_HEIGHT
				EndIf
				FUI_SetChildPosition cboi2\Mesh, COMBOBOX_PADDING, COMBOBOX_PADDING + cboi2\Y
			EndIf
		Next
		
		If cboi\Caption = "-"
			cboi\Owner\CY = cboi\Owner\CY - COMBOBOXITEM_HEIGHT / 2
			cboi\Owner\CH = cboi\Owner\CH - COMBOBOXITEM_HEIGHT / 2
		Else
			cboi\Owner\CY = cboi\Owner\CY - COMBOBOXITEM_HEIGHT
			cboi\Owner\CH = cboi\Owner\CH - COMBOBOXITEM_HEIGHT
		EndIf
		
		If cboi\Owner\DispItems = 0
			If cboi\Caption = "-"
				cboi\Owner\DH = cboi\Owner\DH - COMBOBOXITEM_HEIGHT / 2
			Else
				cboi\Owner\DH = cboi\Owner\DH - COMBOBOXITEM_HEIGHT
			EndIf
			FUI_BuildComboBox cboi\Owner, 2
		EndIf
		
		FreeEntity cboi\Mesh
		Delete cboi
		
		Return True
	EndIf
	grp.GroupBox = Object.GroupBox( ID )
	If grp <> Null
		For reg.region = Each Region
			If reg\Parent = ID
				FUI_DeleteGadget( Handle( reg ) )
			EndIf
		Next
		For Tab.Tab = Each Tab
			If Tab\Parent = ID
				FUI_DeleteGadget( Handle( Tab ) )
			EndIf
		Next
		For pan.Panel = Each Panel
			If pan\Parent = ID
				FUI_DeleteGadget( Handle( pan ) )
			EndIf
		Next
		For btn.Button = Each Button
			If btn\Parent = ID
				FUI_DeleteGadget( Handle( btn ) )
			EndIf
		Next
		For chk.CheckBox = Each CheckBox
			If chk\Parent = ID
				FUI_DeleteGadget( Handle( chk ) )
			EndIf
		Next
		For cbo.ComboBox = Each ComboBox
			If cbo\Parent = ID
				FUI_DeleteGadget( Handle( cbo ) )
			EndIf
		Next
		For grp2.GroupBox = Each GroupBox
			If grp2\Parent = ID
				FUI_DeleteGadget( Handle( grp2 ) )
			EndIf
		Next
		For img.ImageBox = Each ImageBox
			If img\Parent = ID
				FUI_DeleteGadget( Handle( img ) )
			EndIf
		Next
		For lbl.Label = Each Label
			If lbl\Parent = ID
				FUI_DeleteGadget( Handle( lbl ) )
			EndIf
		Next
		For lst.ListBox = Each ListBox
			If lst\Parent = ID
				FUI_DeleteGadget( Handle( lst ) )
			EndIf
		Next
		For prg.ProgressBar = Each ProgressBar
			If prg\Parent = ID
				FUI_DeleteGadget( Handle( prg ) )
			EndIf
		Next
		For rad.Radio = Each Radio
			If rad\Parent = ID
				FUI_DeleteGadget( Handle( rad ) )
			EndIf
		Next
		For scroll.ScrollBar = Each ScrollBar
			If scroll\Parent = ID
				FUI_DeleteGadget( Handle( scroll ) )
			EndIf
		Next
		For sld.Slider = Each Slider
			If sld\Parent = ID
				FUI_DeleteGadget( Handle( sld ) )
			EndIf
		Next
		For spn.Spinner = Each Spinner
			If spn\Parent = ID
				FUI_DeleteGadget( Handle( spn ) )
			EndIf
		Next
		For txt.TextBox = Each TextBox
			If txt\Parent = ID
				FUI_DeleteGadget( Handle( txt ) )
			EndIf
		Next
		For tree.TreeView = Each TreeView
			If tree\Parent = ID
				FUI_DeleteGadget( Handle( tree ) )
			EndIf
		Next
		For view.View = Each View
			If view\Parent = ID
				FUI_DeleteGadget( Handle( view ) )
			EndIf
		Next
		
		FreeEntity grp\Mesh
		Delete grp
		
		Return True
	EndIf
	img.ImageBox = Object.ImageBox( ID )
	If img <> Null
		FreeEntity img\Mesh
		Delete img
		
		Return True
	EndIf
	lbl.Label = Object.Label( ID )
	If lbl <> Null
		FreeEntity lbl\Mesh
		Delete lbl
		
		Return True
	EndIf
	lst.ListBox = Object.ListBox( ID )
	If lst <> Null
		FreeEntity lst\VScroll\Mesh
		Delete lst\VScroll
		FreeEntity lst\HScroll\Mesh
		Delete lst\HScroll
		
		For lsti.ListBoxItem = Each ListBoxItem
			If lsti\Owner = lst
				FUI_DeleteGadget Handle( lsti )
			EndIf
		Next
		
		FreeEntity lst\Mesh
		Delete lst
		
		Return True
	EndIf
	lsti.ListBoxItem = Object.ListBoxItem( ID )
	If lsti <> Null
		For lsti2.ListBoxItem = Each ListBoxItem
			If lsti2\Owner = lsti\Owner And lsti2\Y > lsti\Y
				lsti2\Y = lsti2\Y - LISTBOXITEM_HEIGHT
			EndIf
		Next
		
		lsti\Owner\CY = lsti\Owner\CY - LISTBOXITEM_HEIGHT
		
		FreeEntity lsti\Mesh
		Delete lsti
		
		Return True
	EndIf
	prg.ProgressBar = Object.ProgressBar( ID )
	If prg <> Null
		FreeEntity prg\Mesh
		Delete prg
		
		Return True
	EndIf
	rad.Radio = Object.Radio( ID )
	If rad <> Null
		FreeEntity rad\Mesh
		Delete rad
		
		Return True
	EndIf
	scroll.ScrollBar = Object.ScrollBar( ID )
	If scroll <> Null
		FreeEntity scroll\Mesh
		Delete scroll
		
		Return True
	EndIf
	sld.Slider = Object.Slider( ID )
	If sld <> Null
		FreeEntity sld\Mesh
		Delete sld
		
		Return True
	EndIf
	spn.Spinner = Object.Spinner( ID )
	If spn <> Null
		FreeEntity spn\Mesh
		Delete spn
		
		Return True
	EndIf
	txt.TextBox = Object.TextBox( ID )
	If txt <> Null
		FreeEntity txt\Mesh
		Delete txt
		
		Return True
	EndIf
	tree.TreeView = Object.TreeView( ID )
	If tree <> Null
		FreeEntity tree\VScroll\Mesh
		Delete tree\VScroll
		FreeEntity tree\HScroll\Mesh
		Delete tree\HScroll
		
		For node.Node = Each Node
			If node\Owner = tree
				FUI_DeleteGadget Handle( node )
			EndIf
		Next
		FreeEntity tree\Mesh
		Delete tree
		
		Return True
	EndIf
	node.Node = Object.Node( ID )
	If node <> Null
		FUI_CloseGadget Handle( node )
		
		If node\Parent <> Null
			node\Parent\OH = node\Parent\OH - TREEVIEWITEM_HEIGHT
		EndIf
		For node2.Node = Each Node
			If node2\Owner = node\Owner And node2\Parent = node\Parent And node2\Y > node\Y
				node2\Y = node2\Y - TREEVIEWITEM_HEIGHT
				FUI_SetChildPosition node2\Mesh, EntityX( node2\Mesh ), node2\Y
			EndIf
		Next
		For node2.Node = Each Node
			If node2\Owner = node\Owner And node2\Parent = node
				FUI_DeleteGadget Handle( node2 )
			EndIf
		Next
		
		FreeEntity node\Mesh
		Delete node
		
		Return True
	EndIf
	view.View = Object.View( ID )
	If view <> Null
		FreeEntity view\Mesh
		Delete view
		
		Return True
	EndIf
	Return False
	
End Function

Function FUI_DisableGadget( ID )
	
	mnut.MenuTitle = Object.MenuTitle( ID )
	If mnut <> Null
		mnut\Disabled = True
		tex.BBTexture = FUI_GadgetTexture( mnut\Mesh )
		EntityTexture mnut\Mesh, tex, 3
		FreeTexture tex
		
		Return True
	EndIf
	mnui.MenuItem = Object.MenuItem( ID )
	If mnui <> Null
		mnui\Disabled = True
		tex = FUI_GadgetTexture( mnui\Mesh )
		EntityTexture mnui\Mesh, tex, 3 + mnui\Checked*4
		FreeTexture tex
		
		Return True
	EndIf
	cmnui.ContextMenuItem = Object.ContextMenuItem( ID )
	If cmnui <> Null
		cmnui\Disabled = True
		tex = FUI_GadgetTexture( cmnui\Mesh )
		EntityTexture cmnui\Mesh, tex, 3 + cmnui\Checked*4
		FreeTexture tex
		
		Return True
	EndIf
	Tab.Tab = Object.Tab( ID )
	If Tab <> Null
		Tab\Disabled = True
		For tabp.TabPage = Each TabPage
			If tabp\Owner = Tab
				FUI_DisableGadget Handle( tabp )
			EndIf
		Next
		
		Return True
	EndIf
	tabp.TabPage = Object.TabPage( ID )
	If tabp <> Null
		tabp\Disabled = True
		tex = FUI_GadgetTexture( tabp\Mesh )
		EntityTexture tabp\Mesh, tex, 2
		FreeTexture tex
		
		Return True
	EndIf
	pan.Panel = Object.Panel( ID )
	If pan <> Null
		pan\Disabled = True
		tex = FUI_GadgetTexture( pan\Mesh )
		EntityTexture pan\Mesh, tex, 2 + (pan\Active*3)
		FreeTexture tex
		
		Return True
	EndIf
	btn.Button = Object.Button( ID )
	If btn <> Null
		btn\Disabled = True
		tex = FUI_GadgetTexture( btn\Mesh )
		EntityTexture btn\Mesh, tex, 3
		FreeTexture tex
		
		Return True
	EndIf
	chk.CheckBox = Object.CheckBox( ID )
	If chk <> Null
		chk\Disabled = True
		FUI_SetTexture chk\Mesh, 3 + chk\Checked * 4
		
		Return True
	EndIf
	cbo.ComboBox = Object.ComboBox( ID )
	If cbo <> Null
		cbo\Disabled = True
		FUI_SetTexture cbo\Mesh, 3
		
		Return True
	EndIf
	cboi.ComboBoxItem = Object.ComboBoxItem( ID )
	If cboi <> Null
		cboi\Disabled = True
		
		Return True
	EndIf
	grp.GroupBox = Object.GroupBox( ID )
	If grp <> Null
		grp\Disabled = True
		tex = FUI_GadgetTexture( grp\MeshText )
		EntityTexture grp\MeshText, tex, 1
		FreeTexture tex
		
		Return True
	EndIf
	img.ImageBox = Object.ImageBox( ID )
	If img <> Null
		img\Disabled = True
		
		Return True
	EndIf
	lbl.Label = Object.Label( ID )
	If lbl <> Null
		lbl\Disabled = True
		tex = FUI_GadgetTexture( lbl\Mesh )
		EntityTexture lbl\Mesh, tex, 1
		FreeTexture tex
		
		Return True
	EndIf
	lst.ListBox = Object.ListBox( ID )
	If lst <> Null
		lst\Disabled = True
		
		Return True
	EndIf
	lsti.ListBoxItem = Object.ListBoxItem( ID )
	If lsti <> Null
		lsti\Disabled = True
		
		Return True
	EndIf
	prg.ProgressBar = Object.ProgressBar( ID )
	If prg <> Null
		prg\Disabled = True
		
		Return True
	EndIf
	rad.Radio = Object.Radio( ID )
	If rad <> Null
		rad\Disabled = True
		FUI_SetTexture rad\Mesh, 3 + rad\Checked * 4
		
		Return True
	EndIf
	scroll.ScrollBar = Object.ScrollBar( ID )
	If scroll <> Null
		scroll\Disabled = True
		tex = FUI_GadgetTexture( scroll\Btn1 )
		EntityTexture scroll\Btn1, tex, 3
		FreeTexture tex
		
		tex = FUI_GadgetTexture( scroll\Btn2 )
		EntityTexture scroll\Btn2, tex, 3
		FreeTexture tex
		
		Return True
	EndIf
	sld.Slider = Object.Slider( ID )
	If sld <> Null
		sld\Disabled = True
		
		Return True
	EndIf
	spn.Spinner = Object.Spinner( ID )
	If spn <> Null
		spn\Disabled = True
		tex = FUI_GadgetTexture( spn\Btn1 )
		EntityTexture spn\Btn1, tex, 3
		FreeTexture tex
		
		tex = FUI_GadgetTexture( spn\Btn2 )
		EntityTexture spn\Btn2, tex, 3
		FreeTexture tex
		
		Return True
	EndIf
	txt.TextBox = Object.TextBox( ID )
	If txt <> Null
		txt\Disabled = True
		
		Return True
	EndIf
	tree.TreeView = Object.TreeView( ID )
	If tree <> Null
		tree\Disabled = True
		
		Return True
	EndIf
	node.Node = Object.Node( ID )
	If node <> Null
		node\Disabled = True
		
		Return True
	EndIf
	view.View = Object.View( ID )
	If view <> Null
		view\Disabled = True
		
		Return True
	EndIf
	Return False
	
End Function

Function FUI_EnableGadget( ID )
	
	mnut.MenuTitle = Object.MenuTitle( ID )
	If mnut <> Null
		mnut\Disabled = False
		
		Return True
	EndIf
	mnui.MenuItem = Object.MenuItem( ID )
	If mnui <> Null
		mnui\Disabled = False
		
		Return True
	EndIf
	Tab.Tab = Object.Tab( ID )
	If Tab <> Null
		Tab\Disabled = False
		
		Return True
	EndIf
	tabp.TabPage = Object.TabPage( ID )
	If tabp <> Null
		tabp\Disabled = False
		
		Return True
	EndIf
	pan.Panel = Object.Panel( ID )
	If pan <> Null
		pan\Disabled = False
		tex.BBTexture = FUI_GadgetTexture( pan\Mesh )
		EntityTexture pan\Mesh, tex, 0
		FreeTexture tex
		
		Return True
	EndIf
	btn.Button = Object.Button( ID )
	If btn <> Null
		btn\Disabled = False
		tex = FUI_GadgetTexture( btn\Mesh ) 
		EntityTexture btn\Mesh, tex, 0 
		FreeTexture tex 

		Return True
	EndIf
	chk.CheckBox = Object.CheckBox( ID )
	If chk <> Null
		chk\Disabled = False
		
		Return True
	EndIf
	cbo.ComboBox = Object.ComboBox( ID )
	If cbo <> Null
		cbo\Disabled = False
		
		Return True
	EndIf
	cboi.ComboBoxItem = Object.ComboBoxItem( ID )
	If cboi <> Null
		cboi\Disabled = False
		
		Return True
	EndIf
	grp.GroupBox = Object.GroupBox( ID )
	If grp <> Null
		grp\Disabled = False
		tex = FUI_GadgetTexture( grp\Mesh )
		EntityTexture grp\Mesh, tex, 0
		FreeTexture tex
		
		Return True
	EndIf
	img.ImageBox = Object.ImageBox( ID )
	If img <> Null
		img\Disabled = False
		
		Return True
	EndIf
	lbl.Label = Object.Label( ID )
	If lbl <> Null
		lbl\Disabled = False
		tex = FUI_GadgetTexture( lbl\Mesh )
		EntityTexture lbl\Mesh, tex, 0
		FreeTexture tex
		
		Return True
	EndIf
	lst.ListBox = Object.ListBox( ID )
	If lst <> Null
		lst\Disabled = False
		
		Return True
	EndIf
	lsti.ListBoxItem = Object.ListBoxItem( ID )
	If lsti <> Null
		lsti\Disabled = False
		
		Return True
	EndIf
	prg.ProgressBar = Object.ProgressBar( ID )
	If prg <> Null
		prg\Disabled = False
		
		Return True
	EndIf
	rad.Radio = Object.Radio( ID )
	If rad <> Null
		rad\Disabled = False
		
		Return True
	EndIf
	scroll.ScrollBar = Object.ScrollBar( ID )
	If scroll <> Null
		scroll\Disabled = False
		
		Return True
	EndIf
	sld.Slider = Object.Slider( ID )
	If sld <> Null
		sld\Disabled = False
		
		Return True
	EndIf
	spn.Spinner = Object.Spinner( ID )
	If spn <> Null
		spn\Disabled = False
		
		Return True
	EndIf
	txt.TextBox = Object.TextBox( ID )
	If txt <> Null
		txt\Disabled = False
		
		Return True
	EndIf
	tree.TreeView = Object.TreeView( ID )
	If tree <> Null
		tree\Disabled = False
		
		Return True
	EndIf
	node.Node = Object.Node( ID )
	If node <> Null
		node\Disabled = False
		
		Return True
	EndIf
	view.View = Object.View( ID )
	If view <> Null
		view\Disabled = False
		
		Return True
	EndIf
	Return False
	
End Function

Function FUI_GadgetX#( mesh.BBEntity, parent=0 )
	
	Return EntityX( mesh ) + 0.5
	
End Function

Function FUI_GadgetY#( mesh.BBEntity, parent=0 )

	Return EntityY( mesh ) + 0.5
	
End Function

Function FUI_HideGadget( ID )

	mnut.MenuTitle = Object.MenuTitle( ID )
	If mnut <> Null
		mnut\Hidden = True
		HideEntity mnut\Mesh
		
		Return True
	EndIf
	mnui.MenuItem = Object.MenuItem( ID )
	If mnui <> Null
		mnui\Hidden = True
		HideEntity mnui\Mesh
		
		Return True
	EndIf
	Tab.Tab = Object.Tab( ID )
	If Tab <> Null
		Tab\Hidden = True
		HideEntity Tab\Mesh
		
		Return True
	EndIf
	tabp.TabPage = Object.TabPage( ID )
	If tabp <> Null
		tabp\Hidden = True
		HideEntity tabp\Mesh
		
		Return True
	EndIf
	pan.Panel = Object.Panel( ID )
	If pan <> Null
		pan\Hidden = True
		HideEntity pan\Mesh
		
		Return True
	EndIf
	btn.Button = Object.Button( ID )
	If btn <> Null
		btn\Hidden = True
		HideEntity btn\Mesh
		
		Return True
	EndIf
	chk.CheckBox = Object.CheckBox( ID )
	If chk <> Null
		chk\Hidden = True
		HideEntity chk\Mesh
		
		Return True
	EndIf
	cbo.ComboBox = Object.ComboBox( ID )
	If cbo <> Null
		cbo\Hidden = True
		HideEntity cbo\Mesh
		
		Return True
	EndIf
	cboi.ComboBoxItem = Object.ComboBoxItem( ID )
	If cboi <> Null
		cboi\Hidden = True
		HideEntity cboi\Mesh
		
		Return True
	EndIf
	grp.GroupBox = Object.GroupBox( ID )
	If grp <> Null
		grp\Hidden = True
		HideEntity grp\Mesh
		
		Return True
	EndIf
	img.ImageBox = Object.ImageBox( ID )
	If img <> Null
		img\Hidden = True
		HideEntity img\Mesh
		
		Return True
	EndIf
	lbl.Label = Object.Label( ID )
	If lbl <> Null
		lbl\Hidden = True
		HideEntity lbl\Mesh
		
		Return True
	EndIf
	lst.ListBox = Object.ListBox( ID )
	If lst <> Null
		lst\Hidden = True
		HideEntity lst\Mesh
		
		Return True
	EndIf
	lsti.ListBoxItem = Object.ListBoxItem( ID )
	If lsti <> Null
		lsti\Hidden = True
		HideEntity lsti\Mesh
		
		Return True
	EndIf
	prg.ProgressBar = Object.ProgressBar( ID )
	If prg <> Null
		prg\Hidden = True
		HideEntity prg\Mesh
		
		Return True
	EndIf
	rad.Radio = Object.Radio( ID )
	If rad <> Null
		rad\Hidden = True
		HideEntity rad\Mesh
		
		Return True
	EndIf
	scroll.ScrollBar = Object.ScrollBar( ID )
	If scroll <> Null
		scroll\Hidden = True
		HideEntity scroll\Mesh
		
		Return True
	EndIf
	sld.Slider = Object.Slider( ID )
	If sld <> Null
		sld\Hidden = True
		HideEntity sld\Mesh
		
		Return True
	EndIf
	spn.Spinner = Object.Spinner( ID )
	If spn <> Null
		spn\Hidden = True
		HideEntity spn\Mesh
		
		Return True
	EndIf
	txt.TextBox = Object.TextBox( ID )
	If txt <> Null
		txt\Hidden = True
		HideEntity txt\Mesh
		
		Return True
	EndIf
	tree.TreeView = Object.TreeView( ID )
	If tree <> Null
		tree\Hidden = True
		HideEntity tree\Mesh
		
		Return True
	EndIf
	node.Node = Object.Node( ID )
	If node <> Null
		node\Hidden = True
		HideEntity node\Mesh
		
		Return True
	EndIf
	view.View = Object.View( ID )
	If view <> Null
		view\Hidden = True
		HideEntity view\Mesh
		
		Return True
	EndIf
	Return False
	
End Function

Function FUI_ShowGadget( ID )

	mnut.MenuTitle = Object.MenuTitle( ID )
	If mnut <> Null
		mnut\Hidden = False
		ShowEntity mnut\Mesh
		
		Return True
	EndIf
	mnui.MenuItem = Object.MenuItem( ID )
	If mnui <> Null
		mnui\Hidden = False
		ShowEntity mnui\Mesh
		
		Return True
	EndIf
	Tab.Tab = Object.Tab( ID )
	If Tab <> Null
		Tab\Hidden = False
		ShowEntity Tab\Mesh
		
		Return True
	EndIf
	tabp.TabPage = Object.TabPage( ID )
	If tabp <> Null
		tabp\Hidden = False
		ShowEntity tabp\Mesh
		
		Return True
	EndIf
	pan.Panel = Object.Panel( ID )
	If pan <> Null
		pan\Hidden = False
		ShowEntity pan\Mesh
		
		Return True
	EndIf
	btn.Button = Object.Button( ID )
	If btn <> Null
		btn\Hidden = False
		ShowEntity btn\Mesh
		
		Return True
	EndIf
	chk.CheckBox = Object.CheckBox( ID )
	If chk <> Null
		chk\Hidden = False
		ShowEntity chk\Mesh
		
		Return True
	EndIf
	cbo.ComboBox = Object.ComboBox( ID )
	If cbo <> Null
		cbo\Hidden = False
		ShowEntity cbo\Mesh
		
		Return True
	EndIf
	cboi.ComboBoxItem = Object.ComboBoxItem( ID )
	If cboi <> Null
		cboi\Hidden = False
		ShowEntity cboi\Mesh
		
		Return True
	EndIf
	grp.GroupBox = Object.GroupBox( ID )
	If grp <> Null
		grp\Hidden = False
		ShowEntity grp\Mesh
		
		Return True
	EndIf
	img.ImageBox = Object.ImageBox( ID )
	If img <> Null
		img\Hidden = False
		ShowEntity img\Mesh
		
		Return True
	EndIf
	lbl.Label = Object.Label( ID )
	If lbl <> Null
		lbl\Hidden = False
		ShowEntity lbl\Mesh
		
		Return True
	EndIf
	lst.ListBox = Object.ListBox( ID )
	If lst <> Null
		lst\Hidden = False
		ShowEntity lst\Mesh
		
		Return True
	EndIf
	lsti.ListBoxItem = Object.ListBoxItem( ID )
	If lsti <> Null
		lsti\Hidden = False
		ShowEntity lsti\Mesh
		
		Return True
	EndIf
	prg.ProgressBar = Object.ProgressBar( ID )
	If prg <> Null
		prg\Hidden = False
		ShowEntity prg\Mesh
		
		Return True
	EndIf
	rad.Radio = Object.Radio( ID )
	If rad <> Null
		rad\Hidden = False
		ShowEntity rad\Mesh
		
		Return True
	EndIf
	scroll.ScrollBar = Object.ScrollBar( ID )
	If scroll <> Null
		scroll\Hidden = False
		ShowEntity scroll\Mesh
		
		Return True
	EndIf
	sld.Slider = Object.Slider( ID )
	If sld <> Null
		sld\Hidden = False
		ShowEntity sld\Mesh
		
		Return True
	EndIf
	spn.Spinner = Object.Spinner( ID )
	If spn <> Null
		spn\Hidden = False
		ShowEntity spn\Mesh
		
		Return True
	EndIf
	txt.TextBox = Object.TextBox( ID )
	If txt <> Null
		txt\Hidden = False
		ShowEntity txt\Mesh
		
		Return True
	EndIf
	tree.TreeView = Object.TreeView( ID )
	If tree <> Null
		tree\Hidden = False
		ShowEntity tree\Mesh
		
		Return True
	EndIf
	node.Node = Object.Node( ID )
	If node <> Null
		node\Hidden = False
		ShowEntity node\Mesh
		
		Return True
	EndIf
	view.View = Object.View( ID )
	If view <> Null
		view\Hidden = False
		ShowEntity view\Mesh
		
		Return True
	EndIf
	Return False
	
End Function

Function FUI_OpenGadget( ID )
	
	mnut.MenuTitle = Object.MenuTitle( ID )
	If mnut <> Null
		
		
		Return
	EndIf
	mnui.MenuItem = Object.MenuItem( ID )
	If mnui <> Null
		
		
		Return
	EndIf
	pan.Panel = Object.Panel( ID )
	If pan <> Null
		If pan\Active = False
			pan\Active = True
			FUI_SetTexture pan\Mesh, 0+(pan\Active*3)+(pan\Disabled*2)
			
			OffY = pan\H
			For Tab.Tab = Each Tab
				If Tab\Owner = pan\Owner And Tab\Parent = pan\Parent
					If Tab\X + Tab\W >= pan\X And Tab\X <= pan\X + pan\W And Tab\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							Tab\Y = Tab\Y + OffY
							MoveEntity Tab\Mesh, 0.0,-OffY, 0.0
						Else
							Tab\Y = Tab\Y - OffY
							MoveEntity Tab\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For pan2.Panel = Each Panel
				If pan2\Owner = pan\Owner And pan2\Parent = pan\Parent And pan2 <> pan
					If pan2\X + pan2\W >= pan\X And pan2\X <= pan\X + pan\W And pan2\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							pan2\Y = pan2\Y + OffY
							MoveEntity pan2\Mesh, 0.0,-OffY, 0.0
						Else
							pan2\Y = pan2\Y - OffY
							MoveEntity pan2\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For btn.Button = Each Button
				If btn\Owner = pan\Owner And btn\Parent = pan\Parent
					If btn\X + btn\W >= pan\X And btn\X <= pan\X + pan\W And btn\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							btn\Y = btn\Y + OffY
							MoveEntity btn\Mesh, 0.0,-OffY, 0.0
						Else
							btn\Y = btn\Y - OffY
							MoveEntity btn\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For chk.CheckBox = Each CheckBox
				If chk\Owner = pan\Owner And chk\Parent = pan\Parent
					If chk\X + chk\W >= pan\X And chk\X <= pan\X + pan\W And chk\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							chk\Y = chk\Y + OffY
							MoveEntity chk\Mesh, 0.0,-OffY, 0.0
						Else
							chk\Y = chk\Y - OffY
							MoveEntity chk\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For cbo.ComboBox = Each ComboBox
				If cbo\Owner = pan\Owner And cbo\Parent = pan\Parent
					If cbo\X + cbo\W >= pan\X And cbo\X <= pan\X + pan\W And cbo\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							cbo\Y = cbo\Y + OffY
							MoveEntity cbo\Mesh, 0.0,-OffY, 0.0
						Else
							cbo\Y = cbo\Y - OffY
							MoveEntity cbo\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For grp.GroupBox = Each GroupBox
				If grp\Owner = pan\Owner And grp\Parent = pan\Parent
					If grp\X + grp\W >= pan\X And grp\X <= pan\X + pan\W And grp\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							grp\Y = grp\Y + OffY
							MoveEntity grp\Mesh, 0.0,-OffY, 0.0
						Else
							grp\Y = grp\Y - OffY
							MoveEntity grp\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For img.ImageBox = Each ImageBox
				If img\Owner = pan\Owner And img\Parent = pan\Parent
					If img\X + img\W >= pan\X And img\X <= pan\X + pan\W And img\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							img\Y = img\Y + OffY
							MoveEntity img\Mesh, 0.0,-OffY, 0.0
						Else
							img\Y = img\Y - OffY
							MoveEntity img\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For lbl.Label = Each Label
				If lbl\Owner = pan\Owner And lbl\Parent = pan\Parent
					If lbl\X + lbl\W >= pan\X And lbl\X <= pan\X + pan\W And lbl\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							lbl\Y = lbl\Y + OffY
							MoveEntity lbl\Mesh, 0.0,-OffY, 0.0
						Else
							lbl\Y = lbl\Y - OffY
							MoveEntity lbl\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For lst.ListBox = Each ListBox
				If lst\Owner = pan\Owner And lst\Parent = pan\Parent
					If lst\X + lst\W >= pan\X And lst\X <= pan\X + pan\W And lst\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							lst\Y = lst\Y + OffY
							MoveEntity lst\Mesh, 0.0,-OffY, 0.0
						Else
							lst\Y = lst\Y - OffY
							MoveEntity lst\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For prg.ProgressBar = Each ProgressBar
				If prg\Owner = pan\Owner And prg\Parent = pan\Parent
					If prg\X + prg\W >= pan\X And prg\X <= pan\X + pan\W And prg\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							prg\Y = prg\Y + OffY
							MoveEntity prg\Mesh, 0.0,-OffY, 0.0
						Else
							prg\Y = prg\Y - OffY
							MoveEntity prg\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For rad.Radio = Each Radio
				If rad\Owner = pan\Owner And rad\Parent = pan\Parent
					If rad\X + rad\W >= pan\X And rad\X <= pan\X + pan\W And rad\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							rad\Y = rad\Y + OffY
							MoveEntity rad\Mesh, 0.0,-OffY, 0.0
						Else
							rad\Y = rad\Y - OffY
							MoveEntity rad\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For scroll.ScrollBar = Each ScrollBar
				If scroll\Owner = pan\Owner And scroll\Parent = pan\Parent
					If scroll\X + scroll\W >= pan\X And scroll\X <= pan\X + pan\W And scroll\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							scroll\Y = scroll\Y + OffY
							MoveEntity scroll\Mesh, 0.0,-OffY, 0.0
						Else
							scroll\Y = scroll\Y - OffY
							MoveEntity scroll\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For sld.Slider = Each Slider
				If sld\Owner = pan\Owner And sld\Parent = pan\Parent
					If sld\X + sld\W >= pan\X And sld\X <= pan\X + pan\W And sld\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							sld\Y = sld\Y + OffY
							MoveEntity sld\Mesh, 0.0,-OffY, 0.0
						Else
							sld\Y = sld\Y - OffY
							MoveEntity sld\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For spn.Spinner = Each Spinner
				If spn\Owner = pan\Owner And spn\Parent = pan\Parent
					If spn\X + spn\W >= pan\X And spn\X <= pan\X + pan\W And spn\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							spn\Y = spn\Y + OffY
							MoveEntity spn\Mesh, 0.0,-OffY, 0.0
						Else
							spn\Y = spn\Y - OffY
							MoveEntity spn\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For tree.TreeView = Each TreeView
				If tree\Owner = pan\Owner And tree\Parent = pan\Parent
					If tree\X + tree\W >= pan\X And tree\X <= pan\X + pan\W And tree\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							tree\Y = tree\Y + OffY
							MoveEntity tree\Mesh, 0.0,-OffY, 0.0
						Else
							tree\Y = tree\Y - OffY
							MoveEntity tree\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For txt.TextBox = Each TextBox
				If txt\Owner = pan\Owner And txt\Parent = pan\Parent
					If txt\X + txt\W >= pan\X And txt\X <= pan\X + pan\W And txt\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							txt\Y = txt\Y + OffY
							MoveEntity txt\Mesh, 0.0,-OffY, 0.0
						Else
							txt\Y = txt\Y - OffY
							MoveEntity txt\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For view.View = Each View
				If view\Owner = pan\Owner And view\Parent = pan\Parent
					If view\X + view\W >= pan\X And view\X <= pan\X + pan\W And view\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							view\Y = view\Y + OffY
							MoveEntity view\Mesh, 0.0,-OffY, 0.0
						Else
							view\Y = view\Y - OffY
							MoveEntity view\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
		EndIf
	EndIf
	node.Node = Object.Node( ID )
	If node <> Null
		For node2.Node = Each Node
			If node2\Owner = node\Owner And (node2\Layer <= node\Layer) And node2 <> node And node2\Y > node\Y
				node2\Y = node2\Y + node\OH
			EndIf
		Next
	EndIf
	
End Function

Function FUI_CloseGadget( ID )
	
	mnut.MenuTitle = Object.MenuTitle( ID )
	If mnut <> Null
		
		
		Return
	EndIf
	mnui.MenuItem = Object.MenuItem( ID )
	If mnui <> Null
		
		
		Return
	EndIf
	pan.Panel = Object.Panel( ID )
	If pan <> Null
		If pan\Active = True
			pan\Active = False
			FUI_SetTexture pan\Mesh, 0+(pan\Active*3)+(pan\Disabled*2)
			
			OffY = pan\H
			For Tab.Tab = Each Tab
				If Tab\Owner = pan\Owner And Tab\Parent = pan\Parent
					If Tab\X + Tab\W >= pan\X And Tab\X <= pan\X + pan\W And Tab\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							Tab\Y = Tab\Y + OffY
							MoveEntity Tab\Mesh, 0.0,-OffY, 0.0
						Else
							Tab\Y = Tab\Y - OffY
							MoveEntity Tab\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For pan2.Panel = Each Panel
				If pan2\Owner = pan\Owner And pan2\Parent = pan\Parent And pan2 <> pan
					If pan2\X + pan2\W >= pan\X And pan2\X <= pan\X + pan\W And pan2\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							pan2\Y = pan2\Y + OffY
							MoveEntity pan2\Mesh, 0.0,-OffY, 0.0
						Else
							pan2\Y = pan2\Y - OffY
							MoveEntity pan2\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For btn.Button = Each Button
				If btn\Owner = pan\Owner And btn\Parent = pan\Parent
					If btn\X + btn\W >= pan\X And btn\X <= pan\X + pan\W And btn\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							btn\Y = btn\Y + OffY
							MoveEntity btn\Mesh, 0.0,-OffY, 0.0
						Else
							btn\Y = btn\Y - OffY
							MoveEntity btn\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For chk.CheckBox = Each CheckBox
				If chk\Owner = pan\Owner And chk\Parent = pan\Parent
					If chk\X + chk\W >= pan\X And chk\X <= pan\X + pan\W And chk\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							chk\Y = chk\Y + OffY
							MoveEntity chk\Mesh, 0.0,-OffY, 0.0
						Else
							chk\Y = chk\Y - OffY
							MoveEntity chk\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For cbo.ComboBox = Each ComboBox
				If cbo\Owner = pan\Owner And cbo\Parent = pan\Parent
					If cbo\X + cbo\W >= pan\X And cbo\X <= pan\X + pan\W And cbo\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							cbo\Y = cbo\Y + OffY
							MoveEntity cbo\Mesh, 0.0,-OffY, 0.0
						Else
							cbo\Y = cbo\Y - OffY
							MoveEntity cbo\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For grp.GroupBox = Each GroupBox
				If grp\Owner = pan\Owner And grp\Parent = pan\Parent
					If grp\X + grp\W >= pan\X And grp\X <= pan\X + pan\W And grp\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							grp\Y = grp\Y + OffY
							MoveEntity grp\Mesh, 0.0,-OffY, 0.0
						Else
							grp\Y = grp\Y - OffY
							MoveEntity grp\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For img.ImageBox = Each ImageBox
				If img\Owner = pan\Owner And img\Parent = pan\Parent
					If img\X + img\W >= pan\X And img\X <= pan\X + pan\W And img\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							img\Y = img\Y + OffY
							MoveEntity img\Mesh, 0.0,-OffY, 0.0
						Else
							img\Y = img\Y - OffY
							MoveEntity img\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For lbl.Label = Each Label
				If lbl\Owner = pan\Owner And lbl\Parent = pan\Parent
					If lbl\X + lbl\W >= pan\X And lbl\X <= pan\X + pan\W And lbl\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							lbl\Y = lbl\Y + OffY
							MoveEntity lbl\Mesh, 0.0,-OffY, 0.0
						Else
							lbl\Y = lbl\Y - OffY
							MoveEntity lbl\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For lst.ListBox = Each ListBox
				If lst\Owner = pan\Owner And lst\Parent = pan\Parent
					If lst\X + lst\W >= pan\X And lst\X <= pan\X + pan\W And lst\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							lst\Y = lst\Y + OffY
							MoveEntity lst\Mesh, 0.0,-OffY, 0.0
						Else
							lst\Y = lst\Y - OffY
							MoveEntity lst\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For prg.ProgressBar = Each ProgressBar
				If prg\Owner = pan\Owner And prg\Parent = pan\Parent
					If prg\X + prg\W >= pan\X And prg\X <= pan\X + pan\W And prg\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							prg\Y = prg\Y + OffY
							MoveEntity prg\Mesh, 0.0,-OffY, 0.0
						Else
							prg\Y = prg\Y - OffY
							MoveEntity prg\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For rad.Radio = Each Radio
				If rad\Owner = pan\Owner And rad\Parent = pan\Parent
					If rad\X + rad\W >= pan\X And rad\X <= pan\X + pan\W And rad\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							rad\Y = rad\Y + OffY
							MoveEntity rad\Mesh, 0.0,-OffY, 0.0
						Else
							rad\Y = rad\Y - OffY
							MoveEntity rad\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For scroll.ScrollBar = Each ScrollBar
				If scroll\Owner = pan\Owner And scroll\Parent = pan\Parent
					If scroll\X + scroll\W >= pan\X And scroll\X <= pan\X + pan\W And scroll\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							scroll\Y = scroll\Y + OffY
							MoveEntity scroll\Mesh, 0.0,-OffY, 0.0
						Else
							scroll\Y = scroll\Y - OffY
							MoveEntity scroll\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For sld.Slider = Each Slider
				If sld\Owner = pan\Owner And sld\Parent = pan\Parent
					If sld\X + sld\W >= pan\X And sld\X <= pan\X + pan\W And sld\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							sld\Y = sld\Y + OffY
							MoveEntity sld\Mesh, 0.0,-OffY, 0.0
						Else
							sld\Y = sld\Y - OffY
							MoveEntity sld\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For spn.Spinner = Each Spinner
				If spn\Owner = pan\Owner And spn\Parent = pan\Parent
					If spn\X + spn\W >= pan\X And spn\X <= pan\X + pan\W And spn\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							spn\Y = spn\Y + OffY
							MoveEntity spn\Mesh, 0.0,-OffY, 0.0
						Else
							spn\Y = spn\Y - OffY
							MoveEntity spn\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For tree.TreeView = Each TreeView
				If tree\Owner = pan\Owner And tree\Parent = pan\Parent
					If tree\X + tree\W >= pan\X And tree\X <= pan\X + pan\W And tree\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							tree\Y = tree\Y + OffY
							MoveEntity tree\Mesh, 0.0,-OffY, 0.0
						Else
							tree\Y = tree\Y - OffY
							MoveEntity tree\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For txt.TextBox = Each TextBox
				If txt\Owner = pan\Owner And txt\Parent = pan\Parent
					If txt\X + txt\W >= pan\X And txt\X <= pan\X + pan\W And txt\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							txt\Y = txt\Y + OffY
							MoveEntity txt\Mesh, 0.0,-OffY, 0.0
						Else
							txt\Y = txt\Y - OffY
							MoveEntity txt\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
			For view.View = Each View
				If view\Owner = pan\Owner And view\Parent = pan\Parent
					If view\X + view\W >= pan\X And view\X <= pan\X + pan\W And view\Y > pan\Y + PANEL_HEIGHT
						If pan\Active = True
							view\Y = view\Y + OffY
							MoveEntity view\Mesh, 0.0,-OffY, 0.0
						Else
							view\Y = view\Y - OffY
							MoveEntity view\Mesh, 0.0, OffY, 0.0
						EndIf
					EndIf
				EndIf
			Next
		EndIf
	EndIf
	node.Node = Object.Node( ID )
	If node <> Null
		For node2.Node = Each Node
			If node2\Owner = node\Owner And (node2\Layer <= node\Layer) And node2 <> node And node2\Y > node\Y
				node2\Y = node2\Y - node\OH
			EndIf
		Next
	EndIf
	
End Function

Function FUI_OverGadget( ID )
	
	mnut.MenuTitle = Object.MenuTitle( ID )
	If mnut <> Null
		If mnut\State > 0
			Return True
		EndIf
	EndIf
	mnui.MenuItem = Object.MenuItem( ID )
	If mnui <> Null
		If mnui\State > 0
			Return True
		EndIf
	EndIf
	reg.Region = Object.Region( ID )
	If reg <> Null
		If reg\State > 0
			Return True
		EndIf
	EndIf
	Tab.Tab = Object.Tab( ID )
	If Tab <> Null
		If Tab\State > 0
			Return True
		EndIf
	EndIf
	tabp.TabPage = Object.TabPage( ID )
	If tabp <> Null
		If tabp\State > 0
			Return True
		EndIf
	EndIf
	pan.Panel = Object.Panel( ID )
	If pan <> Null
		If pan\State > 0
			Return True
		EndIf
	EndIf
	btn.Button = Object.Button( ID )
	If btn <> Null
		If btn\State > 0 Or btn\Over = True
			Return True
		EndIf
	EndIf
	chk.CheckBox = Object.CheckBox( ID )
	If chk <> Null
		If chk\State > 0
			Return True
		EndIf
	EndIf
	cbo.ComboBox = Object.ComboBox( ID )
	If cbo <> Null
		If cbo\State > 0
			Return True
		EndIf
	EndIf
	grp.GroupBox = Object.GroupBox( ID )
	If grp <> Null
		If grp\State > 0
			Return True
		EndIf
	EndIf
	img.ImageBox = Object.ImageBox( ID )
	If img <> Null
		If img\State > 0
			Return True
		EndIf
	EndIf
	lbl.Label = Object.Label( ID )
	If lbl <> Null
		If lbl\State > 0
			Return True
		EndIf
	EndIf
	lst.ListBox = Object.ListBox( ID )
	If lst <> Null
		If lst\State > 0
			Return True
		EndIf
	EndIf
	prg.ProgressBar = Object.ProgressBar( ID )
	If prg <> Null
		If prg\State > 0
			Return True
		EndIf
	EndIf
	rad.Radio = Object.Radio( ID )
	If rad <> Null
		If rad\State > 0
			Return True
		EndIf
	EndIf
	scroll.ScrollBar = Object.ScrollBar( ID )
	If scroll <> Null
		If scroll\State > 0
			Return True
		EndIf
	EndIf
	sld.Slider = Object.Slider( ID )
	If sld <> Null
		If sld\State > 0
			Return True
		EndIf
	EndIf
	spn.Spinner = Object.Spinner( ID )
	If spn <> Null
		If spn\State > 0
			Return True
		EndIf
	EndIf
	tree.TreeView = Object.TreeView( ID )
	If tree <> Null
		If tree\State > 0
			Return True
		EndIf
	EndIf
	txt.TextBox = Object.TextBox( ID )
	If txt <> Null
		If txt\State > 0
			Return True
		EndIf
	EndIf
	view.View = Object.View( ID )
	If view <> Null
		If view\State > 0
			Return True
		EndIf
	EndIf
	Return False
	
End Function

Function FUI_AnchorGadget( ID, Parent, X, Y, W, H )
	
	reg.Region = Object.Region( ID )
	If reg <> Null
		reg\Anchor	= Parent
		reg\AX		= X
		reg\AY		= Y
		reg\AW		= W
		reg\AH		= H
		
		Return
	EndIf
	Tab.Tab = Object.Tab( ID )
	If Tab <> Null
		Tab\Anchor	= Parent
		Tab\AX		= X
		Tab\AY		= Y
		Tab\AW		= W
		Tab\AH		= H
		
		Return
	EndIf
	pan.Panel = Object.Panel( ID )
	If pan <> Null
		pan\Anchor	= Parent
		pan\AX		= X
		pan\AY		= Y
		pan\AW		= W
		pan\AH		= H
		
		Return
	EndIf
	btn.Button = Object.Button( ID )
	If btn <> Null
		btn\Anchor	= Parent
		btn\AX		= X
		btn\AY		= Y
		btn\AW		= W
		btn\AH		= H
		
		Return
	EndIf
	chk.CheckBox = Object.CheckBox( ID )
	If chk <> Null
		chk\Anchor	= Parent
		chk\AX		= X
		chk\AY		= Y
		
		Return
	EndIf
	cbo.ComboBox = Object.ComboBox( ID )
	If cbo <> Null
		cbo\Anchor	= Parent
		cbo\AX		= X
		cbo\AY		= Y
		cbo\AW		= W
		cbo\AH		= H
		
		Return
	EndIf
	grp.GroupBox = Object.GroupBox( ID )
	If grp <> Null
		grp\Anchor	= Parent
		grp\AX		= X
		grp\AY		= Y
		grp\AW		= W
		grp\AH		= H
		
		Return
	EndIf
	img.ImageBox = Object.ImageBox( ID )
	If img <> Null
		img\Anchor	= Parent
		img\AX		= X
		img\AY		= Y
		img\AW		= W
		img\AH		= H
		
		Return
	EndIf
	lbl.Label = Object.Label( ID )
	If lbl <> Null
		lbl\Anchor	= Parent
		lbl\AX		= X
		lbl\AY		= Y
		
		Return
	EndIf
	prg.ProgressBar = Object.ProgressBar( ID )
	If prg <> Null
		prg\Anchor	= Parent
		prg\AX		= X
		prg\AY		= Y
		prg\AW		= W
		prg\AH		= H
		
		Return
	EndIf
	rad.Radio = Object.Radio( ID )
	If rad <> Null
		rad\Anchor	= Parent
		rad\AX		= X
		rad\AY		= Y
		
		Return
	EndIf
	scroll.ScrollBar = Object.ScrollBar( ID )
	If scroll <> Null
		scroll\Anchor	= Parent
		scroll\AX		= X
		scroll\AY		= Y
		scroll\AW		= W
		scroll\AH		= H
		
		Return
	EndIf
	sld.Slider = Object.Slider( ID )
	If sld <> Null
		sld\Anchor	= Parent
		sld\AX		= X
		sld\AY		= Y
		sld\AW		= W
		sld\AH		= H
		
		Return
	EndIf
	spn.Spinner = Object.Spinner( ID )
	If spn <> Null
		spn\Anchor	= Parent
		spn\AX		= X
		spn\AY		= Y
		spn\AW		= W
		spn\AH		= H
		
		Return
	EndIf
	tree.TreeView = Object.TreeView( ID )
	If tree <> Null
		tree\Anchor	= Parent
		tree\AX		= X
		tree\AY		= Y
		tree\AW		= W
		tree\AH		= H
		
		Return
	EndIf
	txt.TextBox = Object.TextBox( ID )
	If txt <> Null
		txt\Anchor	= Parent
		txt\AX		= X
		txt\AY		= Y
		txt\AW		= W
		txt\AH		= H
		
		Return
	EndIf
	view.View = Object.View( ID )
	If view <> Null
		view\Anchor	= Parent
		view\AX		= X
		view\AY		= Y
		view\AW		= W
		view\AH		= H
		
		Return
	EndIf
	
End Function

Function FUI_SetGadgetX( ID, Value )
	
	OldValue = Value
	
	win.Window = Object.Window( ID )
	If win <> Null
		win\X = Value
		
		FUI_SetPosition win\Mesh, win\X, win\Y
	EndIf
	reg.Region = Object.Region( ID )
	If reg <> Null
		Parent = reg\Parent
		
		If Parent <> 0
			reg2.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg2 <> Null
				Value = Value + reg2\FX
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FX
			ElseIf pan <> Null
				Value = Value + pan\FX
			ElseIf grp <> Null
				Value = Value + grp\FX
			EndIf
		Else
			Value = Value + reg\Owner\FX
		EndIf
		
		reg\X = OldValue
		reg\OX = Value
		FUI_SetChildPosition reg\Mesh, Value,-EntityY( reg\Mesh )
	EndIf
	Tab.Tab = Object.Tab( ID )
	If Tab <> Null
		Parent = Tab\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FX
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FX
			ElseIf pan <> Null
				Value = Value + pan\FX
			ElseIf grp <> Null
				Value = Value + grp\FX
			EndIf
		Else
			Value = Value + Tab\Owner\FX
		EndIf
		
		Tab\X = OldValue
		Tab\OX = Value
		FUI_SetChildPosition Tab\Mesh, Value,-EntityY( Tab\Mesh )
	EndIf
	pan.Panel = Object.Panel( ID )
	If pan <> Null
		Parent = pan\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan2.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FX
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FX
			ElseIf pan2 <> Null
				Value = Value + pan2\FX
			ElseIf grp <> Null
				Value = Value + grp\FX
			EndIf
		Else
			Value = Value + pan\Owner\FX
		EndIf
		
		pan\X = OldValue
		pan\OX = Value
		FUI_SetChildPosition pan\Mesh, Value,-EntityY( pan\Mesh )
	EndIf
	btn.Button = Object.Button( ID )
	If btn <> Null
		Parent = btn\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FX
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FX
			ElseIf pan <> Null
				Value = Value + pan\FX
			ElseIf grp <> Null
				Value = Value + grp\FX
			EndIf
		Else
			Value = Value + btn\Owner\FX
		EndIf
		
		btn\X = OldValue
		btn\OX = Value
		FUI_SetChildPosition btn\Mesh, Value,-EntityY( btn\Mesh )
	EndIf
	chk.CheckBox = Object.CheckBox( ID )
	If chk <> Null
		Parent = chk\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FX
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FX
			ElseIf pan <> Null
				Value = Value + pan\FX
			ElseIf grp <> Null
				Value = Value + grp\FX
			EndIf
		Else
			Value = Value + chk\Owner\FX
		EndIf
		
		chk\X = OldValue
		chk\OX = Value
		FUI_SetChildPosition chk\Mesh, Value,-EntityY( chk\Mesh )
	EndIf
	cbo.ComboBox = Object.ComboBox( ID )
	If cbo <> Null
		Parent = cbo\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FX
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FX
			ElseIf pan <> Null
				Value = Value + pan\FX
			ElseIf grp <> Null
				Value = Value + grp\FX
			EndIf
		Else
			Value = Value + cbo\Owner\FX
		EndIf
		
		cbo\X = OldValue
		cbo\OX = Value
		FUI_SetChildPosition cbo\Mesh, Value,-EntityY( cbo\Mesh )
	EndIf
	grp.GroupBox = Object.GroupBox( ID )
	If grp <> Null
		Parent = grp\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp2.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FX
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FX
			ElseIf pan <> Null
				Value = Value + pan\FX
			ElseIf grp2 <> Null
				Value = Value + grp2\FX
			EndIf
		Else
			Value = Value + grp\Owner\FX
		EndIf
		
		grp\X = OldValue
		grp\OX = Value
		FUI_SetChildPosition grp\Mesh, Value,-EntityY( grp\Mesh )
	EndIf
	img.ImageBox = Object.ImageBox( ID )
	If img <> Null
		Parent = img\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FX
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FX
			ElseIf pan <> Null
				Value = Value + pan\FX
			ElseIf grp <> Null
				Value = Value + grp\FX
			EndIf
		Else
			Value = Value + img\Owner\FX
		EndIf
		
		img\X = OldValue
		img\OX = Value
		FUI_SetChildPosition img\Mesh, Value,-EntityY( img\Mesh )
	EndIf
	lbl.Label = Object.Label( ID )
	If lbl <> Null
		Parent = lbl\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FX
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FX
			ElseIf pan <> Null
				Value = Value + pan\FX
			ElseIf grp <> Null
				Value = Value + grp\FX
			EndIf
		Else
			Value = Value + lbl\Owner\FX
		EndIf
		
		lbl\X = OldValue
		lbl\OX = Value
		FUI_SetChildPosition lbl\Mesh, Value,-EntityY( lbl\Mesh )
	EndIf
	lst.ListBox = Object.ListBox( ID )
	If lst <> Null
		Parent = lst\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FX
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FX
			ElseIf pan <> Null
				Value = Value + pan\FX
			ElseIf grp <> Null
				Value = Value + grp\FX
			EndIf
		Else
			Value = Value + lst\Owner\FX
		EndIf
		
		lst\X = OldValue
		lst\OX = Value
		FUI_SetChildPosition lst\Mesh, Value,-EntityY( lst\Mesh )
	EndIf
	prg.ProgressBar = Object.ProgressBar( ID )
	If prg <> Null
		Parent = prg\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FX
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FX
			ElseIf pan <> Null
				Value = Value + pan\FX
			ElseIf grp <> Null
				Value = Value + grp\FX
			EndIf
		Else
			Value = Value + prg\Owner\FX
		EndIf
		
		prg\X = OldValue
		prg\OX = Value
		FUI_SetChildPosition prg\Mesh, Value,-EntityY( prg\Mesh )
	EndIf
	rad.Radio = Object.Radio( ID )
	If rad <> Null
		Parent = rad\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FX
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FX
			ElseIf pan <> Null
				Value = Value + pan\FX
			ElseIf grp <> Null
				Value = Value + grp\FX
			EndIf
		Else
			Value = Value + rad\Owner\FX
		EndIf
		
		rad\X = OldValue
		rad\OX = Value
		FUI_SetChildPosition rad\Mesh, Value,-EntityY( rad\Mesh )
	EndIf
	scroll.ScrollBar = Object.ScrollBar( ID )
	If scroll <> Null
		Parent = scroll\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FX
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FX
			ElseIf pan <> Null
				Value = Value + pan\FX
			ElseIf grp <> Null
				Value = Value + grp\FX
			EndIf
		Else
			Value = Value + scroll\Owner\FX
		EndIf
		
		scroll\X = OldValue
		scroll\OX = Value
		FUI_SetChildPosition scroll\Mesh, Value,-EntityY( scroll\Mesh )
	EndIf
	sld.Slider = Object.Slider( ID )
	If sld <> Null
		Parent = sld\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FX
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FX
			ElseIf pan <> Null
				Value = Value + pan\FX
			ElseIf grp <> Null
				Value = Value + grp\FX
			EndIf
		Else
			Value = Value + sld\Owner\FX
		EndIf
		
		sld\X = OldValue
		sld\OX = Value
		FUI_SetChildPosition sld\Mesh, Value,-EntityY( sld\Mesh )
	EndIf
	spn.Spinner = Object.Spinner( ID )
	If spn <> Null
		Parent = spn\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FX
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FX
			ElseIf pan <> Null
				Value = Value + pan\FX
			ElseIf grp <> Null
				Value = Value + grp\FX
			EndIf
		Else
			Value = Value + spn\Owner\FX
		EndIf
		
		spn\X = OldValue
		spn\OX = Value
		FUI_SetChildPosition spn\Mesh, Value,-EntityY( spn\Mesh )
	EndIf
	tree.TreeView = Object.TreeView( ID )
	If tree <> Null
		Parent = tree\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FX
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FX
			ElseIf pan <> Null
				Value = Value + pan\FX
			ElseIf grp <> Null
				Value = Value + grp\FX
			EndIf
		Else
			Value = Value + tree\Owner\FX
		EndIf
		
		tree\X = OldValue
		tree\OX = Value
		FUI_SetChildPosition tree\Mesh, Value,-EntityY( tree\Mesh )
	EndIf
	txt.TextBox = Object.TextBox( ID )
	If txt <> Null
		Parent = txt\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FX
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FX
			ElseIf pan <> Null
				Value = Value + pan\FX
			ElseIf grp <> Null
				Value = Value + grp\FX
			EndIf
		Else
			Value = Value + txt\Owner\FX
		EndIf
		
		txt\X = OldValue
		txt\OX = Value
		FUI_SetChildPosition txt\Mesh, Value,-EntityY( txt\Mesh )
	EndIf
	view.View = Object.View( ID )
	If view <> Null
		Parent = view\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FX
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FX
			ElseIf pan <> Null
				Value = Value + pan\FX
			ElseIf grp <> Null
				Value = Value + grp\FX
			EndIf
		Else
			Value = Value + view\Owner\FX
		EndIf
		
		view\X = OldValue
		view\OX = Value
		FUI_SetChildPosition view\Mesh, Value,-EntityY( view\Mesh )
	EndIf
	
End Function

Function FUI_SetGadgetY( ID, Value )
	
	OldValue = Value
	
	win.Window = Object.Window( ID )
	If win <> Null
		win\Y = Value
		
		FUI_SetPosition win\Mesh, win\X, win\Y
	EndIf
	reg.Region = Object.Region( ID )
	If reg <> Null
		Parent = reg\Parent
		
		If Parent <> 0
			reg2.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg2 <> Null
				Value = Value + reg2\FY
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FY
			ElseIf pan <> Null
				Value = Value + pan\FY
			ElseIf grp <> Null
				Value = Value + grp\FY
			EndIf
		Else
			Value = Value + reg\Owner\FY
		EndIf
		
		reg\Y = OldValue
		reg\OY = Value
		FUI_SetChildPosition reg\Mesh, EntityX( reg\Mesh ), Value
	EndIf
	Tab.Tab = Object.Tab( ID )
	If Tab <> Null
		Parent = Tab\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FY
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FY
			ElseIf pan <> Null
				Value = Value + pan\FY
			ElseIf grp <> Null
				Value = Value + grp\FY
			EndIf
		Else
			Value = Value + Tab\Owner\FY
		EndIf
		
		Tab\Y = OldValue
		Tab\OY = Value
		FUI_SetChildPosition Tab\Mesh, EntityX( Tab\Mesh ), Value
	EndIf
	pan.Panel = Object.Panel( ID )
	If pan <> Null
		Parent = pan\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan2.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FY
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FY
			ElseIf pan2 <> Null
				Value = Value + pan2\FY
			ElseIf grp <> Null
				Value = Value + grp\FY
			EndIf
		Else
			Value = Value + pan\Owner\FY
		EndIf
		
		pan\Y = OldValue
		pan\OY = Value
		FUI_SetChildPosition pan\Mesh, EntityX( pan\Mesh ), Value
	EndIf
	btn.Button = Object.Button( ID )
	If btn <> Null
		Parent = btn\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FY
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FY
			ElseIf pan <> Null
				Value = Value + pan\FY
			ElseIf grp <> Null
				Value = Value + grp\FY
			EndIf
		Else
			Value = Value + btn\Owner\FY
		EndIf
		
		btn\Y = OldValue
		btn\OY = Value
		FUI_SetChildPosition btn\Mesh, EntityX( btn\Mesh ), Value
	EndIf
	chk.CheckBox = Object.CheckBox( ID )
	If chk <> Null
		Parent = chk\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FY
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FY
			ElseIf pan <> Null
				Value = Value + pan\FY
			ElseIf grp <> Null
				Value = Value + grp\FY
			EndIf
		Else
			Value = Value + chk\Owner\FY
		EndIf
		
		chk\Y = OldValue
		chk\OY = Value
		FUI_SetChildPosition chk\Mesh, EntityX( chk\Mesh ), Value
	EndIf
	cbo.ComboBox = Object.ComboBox( ID )
	If cbo <> Null
		Parent = cbo\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FY
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FY
			ElseIf pan <> Null
				Value = Value + pan\FY
			ElseIf grp <> Null
				Value = Value + grp\FY
			EndIf
		Else
			Value = Value + cbo\Owner\FY
		EndIf
		
		cbo\Y = OldValue
		cbo\OY = Value
		FUI_SetChildPosition cbo\Mesh, EntityX( cbo\Mesh ), Value
	EndIf
	grp.GroupBox = Object.GroupBox( ID )
	If grp <> Null
		Parent = grp\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp2.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FY
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FY
			ElseIf pan <> Null
				Value = Value + pan\FY
			ElseIf grp2 <> Null
				Value = Value + grp2\FY
			EndIf
		Else
			Value = Value + grp\Owner\FY
		EndIf
		
		grp\Y = OldValue
		grp\OY = Value
		FUI_SetChildPosition grp\Mesh, EntityX( grp\Mesh ), Value
	EndIf
	img.ImageBox = Object.ImageBox( ID )
	If img <> Null
		Parent = img\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FY
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FY
			ElseIf pan <> Null
				Value = Value + pan\FY
			ElseIf grp <> Null
				Value = Value + grp\FY
			EndIf
		Else
			Value = Value + img\Owner\FY
		EndIf
		
		img\Y = OldValue
		img\OY = Value
		FUI_SetChildPosition img\Mesh, EntityX( img\Mesh ), Value
	EndIf
	lbl.Label = Object.Label( ID )
	If lbl <> Null
		Parent = lbl\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FY
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FY
			ElseIf pan <> Null
				Value = Value + pan\FY
			ElseIf grp <> Null
				Value = Value + grp\FY
			EndIf
		Else
			Value = Value + lbl\Owner\FY
		EndIf
		
		lbl\Y = OldValue
		lbl\OY = Value
		FUI_SetChildPosition lbl\Mesh, EntityX( lbl\Mesh ), Value
	EndIf
	lst.ListBox = Object.ListBox( ID )
	If lst <> Null
		Parent = lst\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FY
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FY
			ElseIf pan <> Null
				Value = Value + pan\FY
			ElseIf grp <> Null
				Value = Value + grp\FY
			EndIf
		Else
			Value = Value + lst\Owner\FY
		EndIf
		
		lst\Y = OldValue
		lst\OY = Value
		FUI_SetChildPosition lst\Mesh, EntityX( lst\Mesh ), Value
	EndIf
	prg.ProgressBar = Object.ProgressBar( ID )
	If prg <> Null
		Parent = prg\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FY
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FY
			ElseIf pan <> Null
				Value = Value + pan\FY
			ElseIf grp <> Null
				Value = Value + grp\FY
			EndIf
		Else
			Value = Value + prg\Owner\FY
		EndIf
		
		prg\Y = OldValue
		prg\OY = Value
		FUI_SetChildPosition prg\Mesh, EntityX( prg\Mesh ), Value
	EndIf
	rad.Radio = Object.Radio( ID )
	If rad <> Null
		Parent = rad\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FY
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FY
			ElseIf pan <> Null
				Value = Value + pan\FY
			ElseIf grp <> Null
				Value = Value + grp\FY
			EndIf
		Else
			Value = Value + rad\Owner\FY
		EndIf
		
		rad\Y = OldValue
		rad\OY = Value
		FUI_SetChildPosition rad\Mesh, EntityX( rad\Mesh ), Value
	EndIf
	scroll.ScrollBar = Object.ScrollBar( ID )
	If scroll <> Null
		Parent = scroll\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FY
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FY
			ElseIf pan <> Null
				Value = Value + pan\FY
			ElseIf grp <> Null
				Value = Value + grp\FY
			EndIf
		Else
			Value = Value + scroll\Owner\FY
		EndIf
		
		scroll\Y = OldValue
		scroll\OY = Value
		FUI_SetChildPosition scroll\Mesh, EntityX( scroll\Mesh ), Value
	EndIf
	sld.Slider = Object.Slider( ID )
	If sld <> Null
		Parent = sld\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FY
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FY
			ElseIf pan <> Null
				Value = Value + pan\FY
			ElseIf grp <> Null
				Value = Value + grp\FY
			EndIf
		Else
			Value = Value + sld\Owner\FY
		EndIf
		
		sld\Y = OldValue
		sld\OY = Value
		FUI_SetChildPosition sld\Mesh, EntityX( sld\Mesh ), Value
	EndIf
	spn.Spinner = Object.Spinner( ID )
	If spn <> Null
		Parent = spn\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FY
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FY
			ElseIf pan <> Null
				Value = Value + pan\FY
			ElseIf grp <> Null
				Value = Value + grp\FY
			EndIf
		Else
			Value = Value + spn\Owner\FY
		EndIf
		
		spn\Y = OldValue
		spn\OY = Value
		FUI_SetChildPosition spn\Mesh, EntityX( spn\Mesh ), Value
	EndIf
	tree.TreeView = Object.TreeView( ID )
	If tree <> Null
		Parent = tree\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FY
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FY
			ElseIf pan <> Null
				Value = Value + pan\FY
			ElseIf grp <> Null
				Value = Value + grp\FY
			EndIf
		Else
			Value = Value + tree\Owner\FY
		EndIf
		
		tree\Y = OldValue
		tree\OY = Value
		FUI_SetChildPosition tree\Mesh, EntityX( tree\Mesh ), Value
	EndIf
	txt.TextBox = Object.TextBox( ID )
	If txt <> Null
		Parent = txt\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FY
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FY
			ElseIf pan <> Null
				Value = Value + pan\FY
			ElseIf grp <> Null
				Value = Value + grp\FY
			EndIf
		Else
			Value = Value + txt\Owner\FY
		EndIf
		
		txt\Y = OldValue
		txt\OY = Value
		FUI_SetChildPosition txt\Mesh, EntityX( txt\Mesh ), Value
	EndIf
	view.View = Object.View( ID )
	If view <> Null
		Parent = view\Parent
		
		If Parent <> 0
			reg.Region = Object.Region( Parent )
			tabp.TabPage = Object.TabPage( Parent )
			pan.Panel = Object.Panel( Parent )
			grp.GroupBox = Object.GroupBox( Parent )
			If reg <> Null
				Value = Value + reg\FY
			ElseIf tabp <> Null
				Value = Value + tabp\Owner\FY
			ElseIf pan <> Null
				Value = Value + pan\FY
			ElseIf grp <> Null
				Value = Value + grp\FY
			EndIf
		Else
			Value = Value + view\Owner\FY
		EndIf
		
		view\Y = OldValue
		view\OY = Value
		FUI_SetChildPosition view\Mesh, EntityX( view\Mesh ), Value
	EndIf
	
End Function

Function FUI_SetGadgetColor( ID, Value, Mode=0 )
	
	sld.Slider = Object.Slider( ID )
	If sld <> Null
		If Mode = 0
			sld\RGB1 = Value
			sld\RGB2 = Value
		ElseIf Mode = 1
			sld\RGB1 = Value
		ElseIf Mode = 2
			sld\RGB2 = Value
		EndIf
		surf.BBSurface = GetSurface( sld\Mesh, 1 )
		If sld\GradientDir = DIR_VERTICAL
			If Mode = 0 Or Mode = 1 Or Mode = 3
				VertexColor surf, 0, FUI_GetRed( sld\RGB1 ), FUI_GetGreen( sld\RGB1 ), FUI_GetBlue( sld\RGB1 ), FUI_GetAlpha( sld\RGB1 )
				VertexColor surf, 1, FUI_GetRed( sld\RGB1 ), FUI_GetGreen( sld\RGB1 ), FUI_GetBlue( sld\RGB1 ), FUI_GetAlpha( sld\RGB1 )
			EndIf
			If Mode = 0 Or Mode = 2 Or Mode = 3
				VertexColor surf, 2, FUI_GetRed( sld\RGB2 ), FUI_GetGreen( sld\RGB2 ), FUI_GetBlue( sld\RGB2 ), FUI_GetAlpha( sld\RGB2 )
				VertexColor surf, 3, FUI_GetRed( sld\RGB2 ), FUI_GetGreen( sld\RGB2 ), FUI_GetBlue( sld\RGB2 ), FUI_GetAlpha( sld\RGB2 )
			EndIf
		Else
			If Mode = 0 Or Mode = 1 Or Mode = 3
				VertexColor surf, 0, FUI_GetRed( sld\RGB1 ), FUI_GetGreen( sld\RGB1 ), FUI_GetBlue( sld\RGB1 ), FUI_GetAlpha( sld\RGB1 )
				VertexColor surf, 3, FUI_GetRed( sld\RGB1 ), FUI_GetGreen( sld\RGB1 ), FUI_GetBlue( sld\RGB1 ), FUI_GetAlpha( sld\RGB1 )
			EndIf
			If Mode = 0 Or Mode = 2 Or Mode = 3
				VertexColor surf, 1, FUI_GetRed( sld\RGB2 ), FUI_GetGreen( sld\RGB2 ), FUI_GetBlue( sld\RGB2 ), FUI_GetAlpha( sld\RGB2 )
				VertexColor surf, 2, FUI_GetRed( sld\RGB2 ), FUI_GetGreen( sld\RGB2 ), FUI_GetBlue( sld\RGB2 ), FUI_GetAlpha( sld\RGB2 )
			EndIf
		EndIf
	EndIf
	
End Function

Function FUI_SetGadgetGradientDir( ID, Value )
	
	sld.Slider = Object.Slider( ID )
	If sld <> Null
		sld\GradientDir = Value
		
		FUI_SetGadgetColor ID, 0, 3
	EndIf
	
End Function

Function FUI_SetGadgetAlpha( ID, Value#, AffectChildren=True )
	
	win.Window = Object.Window( ID )
	If win <> Null
		EntityAlpha win\Mesh, Value#
		For A = 1 To CountSurfaces( win\Mesh )
			surf.BBSurface = GetSurface( win\Mesh, A )
			
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		If win\MinMesh <> Null
			EntityAlpha win\MinMesh, Value#
			For A = 1 To CountSurfaces( win\MinMesh )
				surf = GetSurface( win\MinMesh, A )
				For B = 0 To CountVertices( surf ) - 1
					VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
				Next
			Next
		EndIf
		If win\TitleBar <> Null
			EntityAlpha win\TitleBar, Value#
			For A = 1 To CountSurfaces( win\TitleBar )
				surf = GetSurface( win\TitleBar, A )
				For B = 0 To CountVertices( surf ) - 1
					VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
				Next
			Next
			If win\CloseBtn <> Null
				EntityAlpha win\CloseBtn, Value#
				For A = 1 To CountSurfaces( win\CloseBtn )
					surf = GetSurface( win\CloseBtn, A )
					For B = 0 To CountVertices( surf ) - 1
						VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
					Next
				Next
			EndIf
			If win\MinBtn <> Null
				EntityAlpha win\MinBtn, Value#
				For A = 1 To CountSurfaces( win\MinBtn )
					surf = GetSurface( win\MinBtn, A )
					For B = 0 To CountVertices( surf ) - 1
						VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
					Next
				Next
			EndIf
		EndIf
		If win\MenuBar <> Null
			EntityAlpha win\MenuBar, Value#
			For A = 1 To CountSurfaces( win\MenuBar )
				surf = GetSurface( win\MenuBar, A )
				For B = 0 To CountVertices( surf ) - 1
					VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
				Next
			Next
		EndIf
		If win\StatusBar <> Null
			EntityAlpha win\StatusBar, Value#
			For A = 1 To CountSurfaces( win\StatusBar )
				surf = GetSurface( win\StatusBar, A )
				For B = 0 To CountVertices( surf ) - 1
					VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
				Next
			Next
		EndIf
		
		If AffectChildren = True
			For mnut.MenuTitle = Each MenuTitle
				If mnut\Owner = win
					FUI_SetGadgetAlpha( Handle( mnut ), Value# )
				EndIf
			Next
			For Tab.Tab = Each Tab
				If Tab\Owner = win
					FUI_SetGadgetAlpha( Handle( Tab ), Value# )
				EndIf
			Next
			For pan.Panel = Each Panel
				If pan\Owner = win
					FUI_SetGadgetAlpha( Handle( pan ), Value# )
				EndIf
			Next
			For btn.Button = Each Button
				If btn\Owner = win
					FUI_SetGadgetAlpha( Handle( btn ), Value# )
				EndIf
			Next
			For chk.CheckBox = Each CheckBox
				If chk\Owner = win
					FUI_SetGadgetAlpha( Handle( chk ), Value# )
				EndIf
			Next
			For cbo.ComboBox = Each ComboBox
				If cbo\Owner = win
					FUI_SetGadgetAlpha( Handle( cbo ), Value# )
				EndIf
			Next
			For grp.GroupBox = Each GroupBox
				If grp\Owner = win
					FUI_SetGadgetAlpha( Handle( grp ), Value# )
				EndIf
			Next
			For img.ImageBox = Each ImageBox
				If img\Owner = win
					FUI_SetGadgetAlpha( Handle( img ), Value# )
				EndIf
			Next
			For lbl.Label = Each Label
				If lbl\Owner = win
					FUI_SetGadgetAlpha( Handle( lbl ), Value# )
				EndIf
			Next
			For lst.ListBox = Each ListBox
				If lst\Owner = win
					FUI_SetGadgetAlpha( Handle( lst ), Value# )
				EndIf
			Next
			For prg.ProgressBar = Each ProgressBar
				If prg\Owner = win
					FUI_SetGadgetAlpha( Handle( prg ), Value# )
				EndIf
			Next
			For rad.Radio = Each Radio
				If rad\Owner = win
					FUI_SetGadgetAlpha( Handle( rad ), Value# )
				EndIf
			Next
			For scroll.ScrollBar = Each ScrollBar
				If scroll\Owner = win
					FUI_SetGadgetAlpha( Handle( scroll ), Value# )
				EndIf
			Next
			For sld.Slider = Each Slider
				If sld\Owner = win
					FUI_SetGadgetAlpha( Handle( sld ), Value# )
				EndIf
			Next
			For spn.Spinner = Each Spinner
				If spn\Owner = win
					FUI_SetGadgetAlpha( Handle( spn ), Value# )
				EndIf
			Next
			For txt.TextBox = Each TextBox
				If txt\Owner = win
					FUI_SetGadgetAlpha( Handle( txt ), Value# )
				EndIf
			Next
			For tree.TreeView = Each TreeView
				If tree\Owner = win
					FUI_SetGadgetAlpha( Handle( tree ), Value# )
				EndIf
			Next
			For view.View = Each View
				If view\Owner = win
					FUI_SetGadgetAlpha( Handle( view ), Value# )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	mnut.MenuTitle = Object.MenuTitle( ID )
	If mnut <> Null
		EntityAlpha mnut\Mesh, Value#
		For A = 1 To CountSurfaces( mnut\Mesh )
			surf = GetSurface( mnut\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		EntityAlpha mnut\DropDown, Value#
		For A = 1 To CountSurfaces( mnut\DropDown )
			surf = GetSurface( mnut\DropDown, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		If AffectChildren = True
			For mnui.MenuItem = Each MenuItem
				If mnui\Owner = mnut
					FUI_SetGadgetAlpha( Handle( mnui ), Value# )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	mnui.MenuItem = Object.MenuItem( ID )
	If mnui <> Null
		EntityAlpha mnui\Mesh, Value#
		For A = 1 To CountSurfaces( mnui\Mesh )
			surf = GetSurface( mnui\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		If mnui\DropDown <> Null
			EntityAlpha mnui\DropDown, Value#
			For A = 1 To CountSurfaces( mnui\DropDown )
				surf = GetSurface( mnui\DropDown, A )
				For B = 0 To CountVertices( surf ) - 1
					VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
				Next
			Next
		EndIf
		
		If AffectChildren = True
			For mnui2.MenuItem = Each MenuItem
				If mnui2\Parent = mnui
					FUI_SetGadgetAlpha( Handle( mnui2 ), Value# )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	Tab.Tab = Object.Tab( ID )
	If Tab <> Null
		EntityAlpha Tab\Mesh, Value#
		For A = 1 To CountSurfaces( Tab\Mesh )
			surf = GetSurface( Tab\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		If AffectChildren = True
			For tabp.TabPage = Each TabPage
				If tabp\Owner = Tab
					FUI_SetGadgetAlpha( Handle( tabp ), Value# )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	tabp.TabPage = Object.TabPage( ID )
	If tabp <> Null
		EntityAlpha tabp\Mesh, Value
		For A = 1 To CountSurfaces( tabp\Mesh )
			surf = GetSurface( tabp\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		If AffectChildren = True
			For Tab.Tab = Each Tab
				If Tab\Parent = ID
					FUI_SetGadgetAlpha( Handle( Tab ), Value# )
				EndIf
			Next
			For pan.Panel = Each Panel
				If pan\Parent = ID
					FUI_SetGadgetAlpha( Handle( pan ), Value# )
				EndIf
			Next
			For btn.Button = Each Button
				If btn\Parent = ID
					FUI_SetGadgetAlpha( Handle( btn ), Value# )
				EndIf
			Next
			For chk.CheckBox = Each CheckBox
				If chk\Parent = ID
					FUI_SetGadgetAlpha( Handle( chk ), Value# )
				EndIf
			Next
			For cbo.ComboBox = Each ComboBox
				If cbo\Parent = ID
					FUI_SetGadgetAlpha( Handle( cbo ), Value# )
				EndIf
			Next
			For grp.GroupBox = Each GroupBox
				If grp\Parent = ID
					FUI_SetGadgetAlpha( Handle( grp ), Value# )
				EndIf
			Next
			For img.ImageBox = Each ImageBox
				If img\Parent = ID
					FUI_SetGadgetAlpha( Handle( img ), Value# )
				EndIf
			Next
			For lbl.Label = Each Label
				If lbl\Parent = ID
					FUI_SetGadgetAlpha( Handle( lbl ), Value# )
				EndIf
			Next
			For lst.ListBox = Each ListBox
				If lst\Parent = ID
					FUI_SetGadgetAlpha( Handle( lst ), Value# )
				EndIf
			Next
			For prg.ProgressBar = Each ProgressBar
				If prg\Parent = ID
					FUI_SetGadgetAlpha( Handle( prg ), Value# )
				EndIf
			Next
			For rad.Radio = Each Radio
				If rad\Parent = ID
					FUI_SetGadgetAlpha( Handle( rad ), Value# )
				EndIf
			Next
			For scroll.ScrollBar = Each ScrollBar
				If scroll\Parent = ID
					FUI_SetGadgetAlpha( Handle( scroll ), Value# )
				EndIf
			Next
			For sld.Slider = Each Slider
				If sld\Parent = ID
					FUI_SetGadgetAlpha( Handle( sld ), Value# )
				EndIf
			Next
			For spn.Spinner = Each Spinner
				If spn\Parent = ID
					FUI_SetGadgetAlpha( Handle( spn ), Value# )
				EndIf
			Next
			For txt.TextBox = Each TextBox
				If txt\Parent = ID
					FUI_SetGadgetAlpha( Handle( txt ), Value# )
				EndIf
			Next
			For tree.TreeView = Each TreeView
				If tree\Parent = ID
					FUI_SetGadgetAlpha( Handle( tree ), Value# )
				EndIf
			Next
			For view.View = Each View
				If view\Parent = ID
					FUI_SetGadgetAlpha( Handle( view ), Value# )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	pan.Panel = Object.Panel( ID )
	If pan <> Null
		EntityAlpha pan\Mesh, Value#
		For A = 1 To CountSurfaces( pan\Mesh )
			surf = GetSurface( pan\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		EntityAlpha pan\DropDown, Value#
		For A = 1 To CountSurfaces( pan\DropDown )
			surf = GetSurface( pan\DropDown, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		If AffectChildren = True
			For Tab.Tab = Each Tab
				If Tab\Parent = ID
					FUI_SetGadgetAlpha( Handle( Tab ), Value# )
				EndIf
			Next
			For pan2.Panel = Each Panel
				If pan2\Parent = ID
					FUI_SetGadgetAlpha( Handle( pan2 ), Value# )
				EndIf
			Next
			For btn.Button = Each Button
				If btn\Parent = ID
					FUI_SetGadgetAlpha( Handle( btn ), Value# )
				EndIf
			Next
			For chk.CheckBox = Each CheckBox
				If chk\Parent = ID
					FUI_SetGadgetAlpha( Handle( chk ), Value# )
				EndIf
			Next
			For cbo.ComboBox = Each ComboBox
				If cbo\Parent = ID
					FUI_SetGadgetAlpha( Handle( cbo ), Value# )
				EndIf
			Next
			For grp.GroupBox = Each GroupBox
				If grp\Parent = ID
					FUI_SetGadgetAlpha( Handle( grp ), Value# )
				EndIf
			Next
			For img.ImageBox = Each ImageBox
				If img\Parent = ID
					FUI_SetGadgetAlpha( Handle( img ), Value# )
				EndIf
			Next
			For lbl.Label = Each Label
				If lbl\Parent = ID
					FUI_SetGadgetAlpha( Handle( lbl ), Value# )
				EndIf
			Next
			For lst.ListBox = Each ListBox
				If lst\Parent = ID
					FUI_SetGadgetAlpha( Handle( lst ), Value )
				EndIf
			Next
			For prg.ProgressBar = Each ProgressBar
				If prg\Parent = ID
					FUI_SetGadgetAlpha( Handle( prg ), Value# )
				EndIf
			Next
			For rad.Radio = Each Radio
				If rad\Parent = ID
					FUI_SetGadgetAlpha( Handle( rad ), Value# )
				EndIf
			Next
			For scroll.ScrollBar = Each ScrollBar
				If scroll\Parent = ID
					FUI_SetGadgetAlpha( Handle( scroll ), Value# )
				EndIf
			Next
			For sld.Slider = Each Slider
				If sld\Parent = ID
					FUI_SetGadgetAlpha( Handle( sld ), Value# )
				EndIf
			Next
			For spn.Spinner = Each Spinner
				If spn\Parent = ID
					FUI_SetGadgetAlpha( Handle( spn ), Value# )
				EndIf
			Next
			For txt.TextBox = Each TextBox
				If txt\Parent = ID
					FUI_SetGadgetAlpha( Handle( txt ), Value# )
				EndIf
			Next
			For tree.TreeView = Each TreeView
				If tree\Parent = ID
					FUI_SetGadgetAlpha( Handle( tree ), Value# )
				EndIf
			Next
			For view.View = Each View
				If view\Parent = ID
					FUI_SetGadgetAlpha( Handle( view ), Value# )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	btn.Button = Object.Button( ID )
	If btn <> Null
		EntityAlpha btn\Mesh, Value#
		For A = 1 To CountSurfaces( btn\Mesh )
			surf = GetSurface( btn\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		Return True
	EndIf
	chk.CheckBox = Object.CheckBox( ID )
	If chk <> Null
		EntityAlpha chk\Mesh, Value#
		For A = 1 To CountSurfaces( chk\Mesh )
			surf = GetSurface( chk\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		Return True
	EndIf
	cbo.ComboBox = Object.ComboBox( ID )
	If cbo <> Null
		EntityAlpha cbo\Mesh, Value#
		For A = 1 To CountSurfaces( cbo\Mesh )
			surf = GetSurface( cbo\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		EntityAlpha cbo\DropDown, Value#
		For A = 1 To CountSurfaces( cbo\DropDown )
			surf = GetSurface( cbo\DropDown, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		If AffectChildren = True
			For cboi.ComboBoxItem = Each ComboBoxItem
				If cboi\Owner = cbo
					FUI_SetGadgetAlpha( Handle( cboi ), Value# )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	cboi.ComboBoxItem = Object.ComboBoxItem( ID )
	If cboi <> Null
		EntityAlpha cboi\Mesh, Value#
		For A = 1 To CountSurfaces( cboi\Mesh )
			surf = GetSurface( cboi\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		Return True
	EndIf
	grp.GroupBox = Object.GroupBox( ID )
	If grp <> Null
		EntityAlpha grp\Mesh, Value#
		For A = 1 To CountSurfaces( grp\Mesh )
			surf = GetSurface( grp\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		EntityAlpha grp\MeshText, Value#
		For A = 1 To CountSurfaces( grp\MeshText )
			surf = GetSurface( grp\MeshText, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		Return True
	EndIf
	img.ImageBox = Object.ImageBox( ID )
	If img <> Null
		EntityAlpha img\Mesh, Value#
		For A = 1 To CountSurfaces( img\Mesh )
			surf = GetSurface( img\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		Return True
	EndIf
	lbl.Label = Object.Label( ID )
	If lbl <> Null
		EntityAlpha lbl\Mesh, Value#
		For A = 1 To CountSurfaces( lbl\Mesh )
			surf = GetSurface( lbl\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		Return True
	EndIf
	lst.ListBox = Object.ListBox( ID )
	If lst <> Null
		EntityAlpha lst\Mesh, Value#
		For A = 1 To CountSurfaces( lst\Mesh )
			surf = GetSurface( lst\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		If AffectChildren = True
			For lsti.ListBoxItem = Each ListBoxItem
				If lsti\Owner = lst
					FUI_SetGadgetAlpha( Handle( lsti ), Value# )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	lsti.ListBoxItem = Object.ListBoxItem( ID )
	If lsti <> Null
		EntityAlpha lsti\Mesh, Value#
		For A = 1 To CountSurfaces( lsti\Mesh )
			surf = GetSurface( lsti\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		Return True
	EndIf
	prg.ProgressBar = Object.ProgressBar( ID )
	If prg <> Null
		EntityAlpha prg\Mesh, Value#
		For A = 1 To CountSurfaces( prg\Mesh )
			surf = GetSurface( prg\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		EntityAlpha prg\MeshText, Value#
		For A = 1 To CountSurfaces( prg\MeshText )
			surf = GetSurface( prg\MeshText, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		EntityAlpha prg\Bar, Value#
		For A = 1 To CountSurfaces( prg\Bar )
			surf = GetSurface( prg\Bar, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		Return True
	EndIf
	rad.Radio = Object.Radio( ID )
	If rad <> Null
		EntityAlpha rad\Mesh, Value#
		For A = 1 To CountSurfaces( rad\Mesh )
			surf = GetSurface( rad\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		Return True
	EndIf
	scroll.ScrollBar = Object.ScrollBar( ID )
	If scroll <> Null
		EntityAlpha scroll\Mesh, Value#
		For A = 1 To CountSurfaces( scroll\Mesh )
			surf = GetSurface( scroll\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		EntityAlpha scroll\ScrollBar, Value#
		For A = 1 To CountSurfaces( scroll\ScrollBar )
			surf = GetSurface( scroll\ScrollBar, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		EntityAlpha scroll\Btn1, Value#
		For A = 1 To CountSurfaces( scroll\Btn1 )
			surf = GetSurface( scroll\Btn1, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		EntityAlpha scroll\Btn2, Value#
		For A = 1 To CountSurfaces( scroll\Btn2 )
			surf = GetSurface( scroll\Btn2, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		Return True
	EndIf
	sld.Slider = Object.Slider( ID )
	If sld <> Null
		EntityAlpha sld\Mesh, Value#
		For A = 1 To CountSurfaces( sld\Mesh )
			surf = GetSurface( sld\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		EntityAlpha sld\ScrollBar, Value#
		For A = 1 To CountSurfaces( sld\ScrollBar )
			surf = GetSurface( sld\ScrollBar, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		Return True
	EndIf
	spn.Spinner = Object.Spinner( ID )
	If spn <> Null
		EntityAlpha spn\Mesh, Value#
		For A = 1 To CountSurfaces( spn\Mesh )
			surf = GetSurface( spn\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		EntityAlpha spn\MeshText, Value#
		For A = 1 To CountSurfaces( spn\MeshText )
			surf = GetSurface( spn\MeshText, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		EntityAlpha spn\Btn1, Value#
		For A = 1 To CountSurfaces( spn\Btn1 )
			surf = GetSurface( spn\Btn1, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		EntityAlpha spn\Btn2, Value#
		For A = 1 To CountSurfaces( spn\Btn2 )
			surf = GetSurface( spn\Btn2, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		Return True
	EndIf
	txt.TextBox = Object.TextBox( ID )
	If txt <> Null
		EntityAlpha txt\Mesh, Value#
		For A = 1 To CountSurfaces( txt\Mesh )
			surf = GetSurface( txt\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		EntityAlpha txt\MeshText, Value#
		For A = 1 To CountSurfaces( txt\MeshText )
			surf = GetSurface( txt\MeshText, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		Return True
	EndIf
	tree.TreeView = Object.TreeView( ID )
	If tree <> Null
		EntityAlpha tree\Mesh, Value#
		For A = 1 To CountSurfaces( tree\Mesh )
			surf = GetSurface( tree\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		Return True
	EndIf
	view.View = Object.View( ID )
	If view <> Null
		EntityAlpha view\Mesh, Value#
		For A = 1 To CountSurfaces( view\Mesh )
			surf = GetSurface( view\Mesh, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		EntityAlpha view\MeshText, Value#
		For A = 1 To CountSurfaces( view\MeshText )
			surf = GetSurface( view\MeshText, A )
			For B = 0 To CountVertices( surf ) - 1
				VertexColor surf, B, VertexRed( surf, B ), VertexGreen( surf, B ), VertexBlue( surf, B ), Value#
			Next
		Next
		
		Return True
	EndIf
	
	Return False
	
End Function

Function FUI_SetGadgetBlend( ID, Value, AffectChildren=True )
	
	win.Window = Object.Window( ID )
	If win <> Null
		EntityBlend win\Mesh, Value
		If win\MinMesh <> Null
			EntityBlend win\MinMesh, Value
		EndIf
		If win\TitleBar <> Null
			EntityBlend win\TitleBar, Value
			If win\CloseBtn <> Null
				EntityBlend win\CloseBtn, Value
			EndIf
			If win\MinBtn <> Null
				EntityBlend win\MinBtn, Value
			EndIf
		EndIf
		If win\MenuBar <> Null
			EntityBlend win\MenuBar, Value
		EndIf
		If win\StatusBar <> Null
			EntityBlend win\StatusBar, Value
		EndIf
		
		If AffectChildren = True
			For mnut.MenuTitle = Each MenuTitle
				If mnut\Owner = win
					FUI_SetGadgetBlend( Handle( mnut ), Value )
				EndIf
			Next
			For Tab.Tab = Each Tab
				If Tab\Owner = win
					FUI_SetGadgetBlend( Handle( Tab ), Value )
				EndIf
			Next
			For pan.Panel = Each Panel
				If pan\Owner = win
					FUI_SetGadgetBlend( Handle( pan ), Value )
				EndIf
			Next
			For btn.Button = Each Button
				If btn\Owner = win
					FUI_SetGadgetBlend( Handle( btn ), Value )
				EndIf
			Next
			For chk.CheckBox = Each CheckBox
				If chk\Owner = win
					FUI_SetGadgetBlend( Handle( chk ), Value )
				EndIf
			Next
			For cbo.ComboBox = Each ComboBox
				If cbo\Owner = win
					FUI_SetGadgetBlend( Handle( cbo ), Value )
				EndIf
			Next
			For grp.GroupBox = Each GroupBox
				If grp\Owner = win
					FUI_SetGadgetBlend( Handle( grp ), Value )
				EndIf
			Next
			For img.ImageBox = Each ImageBox
				If img\Owner = win
					FUI_SetGadgetBlend( Handle( img ), Value )
				EndIf
			Next
			For lbl.Label = Each Label
				If lbl\Owner = win
					FUI_SetGadgetBlend( Handle( lbl ), Value )
				EndIf
			Next
			For lst.ListBox = Each ListBox
				If lst\Owner = win
					FUI_SetGadgetBlend( Handle( lst ), Value )
				EndIf
			Next
			For prg.ProgressBar = Each ProgressBar
				If prg\Owner = win
					FUI_SetGadgetBlend( Handle( prg ), Value )
				EndIf
			Next
			For rad.Radio = Each Radio
				If rad\Owner = win
					FUI_SetGadgetBlend( Handle( rad ), Value )
				EndIf
			Next
			For scroll.ScrollBar = Each ScrollBar
				If scroll\Owner = win
					FUI_SetGadgetBlend( Handle( scroll ), Value )
				EndIf
			Next
			For sld.Slider = Each Slider
				If sld\Owner = win
					FUI_SetGadgetBlend( Handle( sld ), Value )
				EndIf
			Next
			For spn.Spinner = Each Spinner
				If spn\Owner = win
					FUI_SetGadgetBlend( Handle( spn ), Value )
				EndIf
			Next
			For txt.TextBox = Each TextBox
				If txt\Owner = win
					FUI_SetGadgetBlend( Handle( txt ), Value )
				EndIf
			Next
			For tree.TreeView = Each TreeView
				If tree\Owner = win
					FUI_SetGadgetBlend( Handle( tree ), Value )
				EndIf
			Next
			For view.View = Each View
				If view\Owner = win
					FUI_SetGadgetBlend( Handle( view ), Value )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	mnut.MenuTitle = Object.MenuTitle( ID )
	If mnut <> Null
		EntityBlend mnut\Mesh, Value
		EntityBlend mnut\DropDown, Value
		
		If AffectChildren = True
			For mnui.MenuItem = Each MenuItem
				If mnui\Owner = mnut
					FUI_SetGadgetBlend( Handle( mnui ), Value )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	mnui.MenuItem = Object.MenuItem( ID )
	If mnui <> Null
		EntityBlend mnui\Mesh, Value
		If mnui\DropDown <> Null
			EntityBlend mnui\DropDown, Value
		EndIf
		
		If AffectChildren = True
			For mnui2.MenuItem = Each MenuItem
				If mnui2\Parent = mnui
					FUI_SetGadgetBlend( Handle( mnui2 ), Value )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	Tab.Tab = Object.Tab( ID )
	If Tab <> Null
		EntityBlend Tab\Mesh, Value
		
		If AffectChildren = True
			For tabp.TabPage = Each TabPage
				If tabp\Owner = Tab
					FUI_SetGadgetBlend( Handle( tabp ), Value )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	tabp.TabPage = Object.TabPage( ID )
	If tabp <> Null
		EntityBlend tabp\Mesh, Value
		
		If AffectChildren = True
			For Tab.Tab = Each Tab
				If Tab\Parent = ID
					FUI_SetGadgetBlend( Handle( Tab ), Value )
				EndIf
			Next
			For pan.Panel = Each Panel
				If pan\Parent = ID
					FUI_SetGadgetBlend( Handle( pan ), Value )
				EndIf
			Next
			For btn.Button = Each Button
				If btn\Parent = ID
					FUI_SetGadgetBlend( Handle( btn ), Value )
				EndIf
			Next
			For chk.CheckBox = Each CheckBox
				If chk\Parent = ID
					FUI_SetGadgetBlend( Handle( chk ), Value )
				EndIf
			Next
			For cbo.ComboBox = Each ComboBox
				If cbo\Parent = ID
					FUI_SetGadgetBlend( Handle( cbo ), Value )
				EndIf
			Next
			For grp.GroupBox = Each GroupBox
				If grp\Parent = ID
					FUI_SetGadgetBlend( Handle( grp ), Value )
				EndIf
			Next
			For img.ImageBox = Each ImageBox
				If img\Parent = ID
					FUI_SetGadgetBlend( Handle( img ), Value )
				EndIf
			Next
			For lbl.Label = Each Label
				If lbl\Parent = ID
					FUI_SetGadgetBlend( Handle( lbl ), Value )
				EndIf
			Next
			For lst.ListBox = Each ListBox
				If lst\Parent = ID
					FUI_SetGadgetBlend( Handle( lst ), Value )
				EndIf
			Next
			For prg.ProgressBar = Each ProgressBar
				If prg\Parent = ID
					FUI_SetGadgetBlend( Handle( prg ), Value )
				EndIf
			Next
			For rad.Radio = Each Radio
				If rad\Parent = ID
					FUI_SetGadgetBlend( Handle( rad ), Value )
				EndIf
			Next
			For scroll.ScrollBar = Each ScrollBar
				If scroll\Parent = ID
					FUI_SetGadgetBlend( Handle( scroll ), Value )
				EndIf
			Next
			For sld.Slider = Each Slider
				If sld\Parent = ID
					FUI_SetGadgetBlend( Handle( sld ), Value )
				EndIf
			Next
			For spn.Spinner = Each Spinner
				If spn\Parent = ID
					FUI_SetGadgetBlend( Handle( spn ), Value )
				EndIf
			Next
			For txt.TextBox = Each TextBox
				If txt\Parent = ID
					FUI_SetGadgetBlend( Handle( txt ), Value )
				EndIf
			Next
			For tree.TreeView = Each TreeView
				If tree\Parent = ID
					FUI_SetGadgetBlend( Handle( tree ), Value )
				EndIf
			Next
			For view.View = Each View
				If view\Parent = ID
					FUI_SetGadgetBlend( Handle( view ), Value )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	pan.Panel = Object.Panel( ID )
	If pan <> Null
		EntityBlend pan\Mesh, Value
		EntityBlend pan\DropDown, Value
		
		If AffectChildren = True
			For Tab.Tab = Each Tab
				If Tab\Parent = ID
					FUI_SetGadgetBlend( Handle( Tab ), Value )
				EndIf
			Next
			For pan.Panel = Each Panel
				If pan\Parent = ID
					FUI_SetGadgetBlend( Handle( pan ), Value )
				EndIf
			Next
			For btn.Button = Each Button
				If btn\Parent = ID
					FUI_SetGadgetBlend( Handle( btn ), Value )
				EndIf
			Next
			For chk.CheckBox = Each CheckBox
				If chk\Parent = ID
					FUI_SetGadgetBlend( Handle( chk ), Value )
				EndIf
			Next
			For cbo.ComboBox = Each ComboBox
				If cbo\Parent = ID
					FUI_SetGadgetBlend( Handle( cbo ), Value )
				EndIf
			Next
			For grp.GroupBox = Each GroupBox
				If grp\Parent = ID
					FUI_SetGadgetBlend( Handle( grp ), Value )
				EndIf
			Next
			For img.ImageBox = Each ImageBox
				If img\Parent = ID
					FUI_SetGadgetBlend( Handle( img ), Value )
				EndIf
			Next
			For lbl.Label = Each Label
				If lbl\Parent = ID
					FUI_SetGadgetBlend( Handle( lbl ), Value )
				EndIf
			Next
			For lst.ListBox = Each ListBox
				If lst\Parent = ID
					FUI_SetGadgetBlend( Handle( lst ), Value )
				EndIf
			Next
			For prg.ProgressBar = Each ProgressBar
				If prg\Parent = ID
					FUI_SetGadgetBlend( Handle( prg ), Value )
				EndIf
			Next
			For rad.Radio = Each Radio
				If rad\Parent = ID
					FUI_SetGadgetBlend( Handle( rad ), Value )
				EndIf
			Next
			For scroll.ScrollBar = Each ScrollBar
				If scroll\Parent = ID
					FUI_SetGadgetBlend( Handle( scroll ), Value )
				EndIf
			Next
			For sld.Slider = Each Slider
				If sld\Parent = ID
					FUI_SetGadgetBlend( Handle( sld ), Value )
				EndIf
			Next
			For spn.Spinner = Each Spinner
				If spn\Parent = ID
					FUI_SetGadgetBlend( Handle( spn ), Value )
				EndIf
			Next
			For txt.TextBox = Each TextBox
				If txt\Parent = ID
					FUI_SetGadgetBlend( Handle( txt ), Value )
				EndIf
			Next
			For tree.TreeView = Each TreeView
				If tree\Parent = ID
					FUI_SetGadgetBlend( Handle( tree ), Value )
				EndIf
			Next
			For view.View = Each View
				If view\Parent = ID
					FUI_SetGadgetBlend( Handle( view ), Value )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	btn.Button = Object.Button( ID )
	If btn <> Null
		EntityBlend btn\Mesh, Value
		
		Return True
	EndIf
	chk.CheckBox = Object.CheckBox( ID )
	If chk <> Null
		EntityBlend chk\Mesh, Value
		
		Return True
	EndIf
	cbo.ComboBox = Object.ComboBox( ID )
	If cbo <> Null
		EntityBlend cbo\Mesh, Value
		EntityBlend cbo\DropDown, Value
		
		If AffectChildren = True
			For cboi.ComboBoxItem = Each ComboBoxItem
				If cboi\Owner = cbo
					FUI_SetGadgetBlend( Handle( cboi ), Value )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	cboi.ComboBoxItem = Object.ComboBoxItem( ID )
	If cboi <> Null
		EntityBlend cboi\Mesh, Value
		
		Return True
	EndIf
	grp.GroupBox = Object.GroupBox( ID )
	If grp <> Null
		EntityBlend grp\Mesh, Value
		EntityBlend grp\MeshText, Value
		
		Return True
	EndIf
	img.ImageBox = Object.ImageBox( ID )
	If img <> Null
		EntityBlend img\Mesh, Value
		
		Return True
	EndIf
	lbl.Label = Object.Label( ID )
	If lbl <> Null
		EntityBlend lbl\Mesh, Value
		
		Return True
	EndIf
	lst.ListBox = Object.ListBox( ID )
	If lst <> Null
		EntityFX lst\Mesh, Value
		
		If AffectChildren = True
			For lsti.ListBoxItem = Each ListBoxItem
				If lsti\Owner = lst
					FUI_SetGadgetBlend( Handle( lsti ), Value )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	lsti.ListBoxItem = Object.ListBoxItem( ID )
	If lsti <> Null
		EntityBlend lsti\Mesh, Value
		
		Return True
	EndIf
	prg.ProgressBar = Object.ProgressBar( ID )
	If prg <> Null
		EntityBlend prg\Mesh, Value
		EntityBlend prg\MeshText, Value
		EntityBlend prg\Bar, Value
		
		Return True
	EndIf
	rad.Radio = Object.Radio( ID )
	If rad <> Null
		EntityBlend rad\Mesh, Value
		
		Return True
	EndIf
	scroll.ScrollBar = Object.ScrollBar( ID )
	If scroll <> Null
		EntityBlend scroll\Mesh, Value
		EntityBlend scroll\ScrollBar, Value
		EntityBlend scroll\Btn1, Value
		EntityBlend scroll\Btn2, Value
		
		Return True
	EndIf
	sld.Slider = Object.Slider( ID )
	If sld <> Null
		EntityBlend sld\Mesh, Value
		EntityBlend sld\ScrollBar, Value
		
		Return True
	EndIf
	spn.Spinner = Object.Spinner( ID )
	If spn <> Null
		EntityBlend spn\Mesh, Value
		EntityBlend spn\MeshText, Value
		EntityBlend spn\Btn1, Value
		EntityBlend spn\Btn2, Value
		
		Return True
	EndIf
	txt.TextBox = Object.TextBox( ID )
	If txt <> Null
		EntityBlend txt\Mesh, Value
		EntityBlend txt\MeshText, Value
		
		Return True
	EndIf
	tree.TreeView = Object.TreeView( ID )
	If tree <> Null
		EntityBlend tree\Mesh, Value
		
		Return True
	EndIf
	view.View = Object.View( ID )
	If view <> Null
		EntityBlend view\Mesh, Value
		EntityBlend view\MeshText, Value
		
		Return True
	EndIf
	
	Return False
	
End Function

Function FUI_SetGadgetFX( ID, Value, AffectChildren=True )
	
	win.Window = Object.Window( ID )
	If win <> Null
		EntityFX win\Mesh, Value
		If win\MinMesh <> Null
			EntityFX win\MinMesh, Value
		EndIf
		If win\TitleBar <> Null
			EntityFX win\TitleBar, Value
			If win\CloseBtn <> Null
				EntityFX win\CloseBtn, Value
			EndIf
			If win\MinBtn <> Null
				EntityFX win\MinBtn, Value
			EndIf
		EndIf
		If win\MenuBar <> Null
			EntityFX win\MenuBar, Value
		EndIf
		If win\StatusBar <> Null
			EntityFX win\StatusBar, Value
		EndIf
		
		If AffectChildren = True
			For mnut.MenuTitle = Each MenuTitle
				If mnut\Owner = win
					FUI_SetGadgetFX( Handle( mnut ), Value )
				EndIf
			Next
			For Tab.Tab = Each Tab
				If Tab\Owner = win
					FUI_SetGadgetFX( Handle( Tab ), Value )
				EndIf
			Next
			For pan.Panel = Each Panel
				If pan\Owner = win
					FUI_SetGadgetFX( Handle( pan ), Value )
				EndIf
			Next
			For btn.Button = Each Button
				If btn\Owner = win
					FUI_SetGadgetFX( Handle( btn ), Value )
				EndIf
			Next
			For chk.CheckBox = Each CheckBox
				If chk\Owner = win
					FUI_SetGadgetFX( Handle( chk ), Value )
				EndIf
			Next
			For cbo.ComboBox = Each ComboBox
				If cbo\Owner = win
					FUI_SetGadgetFX( Handle( cbo ), Value )
				EndIf
			Next
			For grp.GroupBox = Each GroupBox
				If grp\Owner = win
					FUI_SetGadgetFX( Handle( grp ), Value )
				EndIf
			Next
			For img.ImageBox = Each ImageBox
				If img\Owner = win
					FUI_SetGadgetFX( Handle( img ), Value )
				EndIf
			Next
			For lbl.Label = Each Label
				If lbl\Owner = win
					FUI_SetGadgetFX( Handle( lbl ), Value )
				EndIf
			Next
			For lst.ListBox = Each ListBox
				If lst\Owner = win
					FUI_SetGadgetFX( Handle( lst ), Value )
				EndIf
			Next
			For prg.ProgressBar = Each ProgressBar
				If prg\Owner = win
					FUI_SetGadgetFX( Handle( prg ), Value )
				EndIf
			Next
			For rad.Radio = Each Radio
				If rad\Owner = win
					FUI_SetGadgetFX( Handle( rad ), Value )
				EndIf
			Next
			For scroll.ScrollBar = Each ScrollBar
				If scroll\Owner = win
					FUI_SetGadgetFX( Handle( scroll ), Value )
				EndIf
			Next
			For sld.Slider = Each Slider
				If sld\Owner = win
					FUI_SetGadgetFX( Handle( sld ), Value )
				EndIf
			Next
			For spn.Spinner = Each Spinner
				If spn\Owner = win
					FUI_SetGadgetFX( Handle( spn ), Value )
				EndIf
			Next
			For txt.TextBox = Each TextBox
				If txt\Owner = win
					FUI_SetGadgetFX( Handle( txt ), Value )
				EndIf
			Next
			For tree.TreeView = Each TreeView
				If tree\Owner = win
					FUI_SetGadgetFX( Handle( tree ), Value )
				EndIf
			Next
			For view.View = Each View
				If view\Owner = win
					FUI_SetGadgetFX( Handle( view ), Value )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	mnut.MenuTitle = Object.MenuTitle( ID )
	If mnut <> Null
		EntityFX mnut\Mesh, Value
		EntityFX mnut\DropDown, Value
		
		If AffectChildren = True
			For mnui.MenuItem = Each MenuItem
				If mnui\Owner = mnut
					FUI_SetGadgetFX( Handle( mnui ), Value )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	mnui.MenuItem = Object.MenuItem( ID )
	If mnui <> Null
		EntityFX mnui\Mesh, Value
		If mnui\DropDown <> Null
			EntityFX mnui\DropDown, Value
		EndIf
		
		If AffectChildren = True
			For mnui2.MenuItem = Each MenuItem
				If mnui2\Parent = mnui
					FUI_SetGadgetFX( Handle( mnui2 ), Value )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	Tab.Tab = Object.Tab( ID )
	If Tab <> Null
		If AffectChildren = True
			For tabp.TabPage = Each TabPage
				If tabp\Owner = Tab
					FUI_SetGadgetFX( Handle( tabp ), Value )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	tabp.TabPage = Object.TabPage( ID )
	If tabp <> Null
		EntityFX tabp\Mesh, Value
		
		If AffectChildren = True
			For Tab.Tab = Each Tab
				If Tab\Parent = ID
					FUI_SetGadgetFX( Handle( Tab ), Value )
				EndIf
			Next
			For pan.Panel = Each Panel
				If pan\Parent = ID
					FUI_SetGadgetFX( Handle( pan ), Value )
				EndIf
			Next
			For btn.Button = Each Button
				If btn\Parent = ID
					FUI_SetGadgetFX( Handle( btn ), Value )
				EndIf
			Next
			For chk.CheckBox = Each CheckBox
				If chk\Parent = ID
					FUI_SetGadgetFX( Handle( chk ), Value )
				EndIf
			Next
			For cbo.ComboBox = Each ComboBox
				If cbo\Parent = ID
					FUI_SetGadgetFX( Handle( cbo ), Value )
				EndIf
			Next
			For grp.GroupBox = Each GroupBox
				If grp\Parent = ID
					FUI_SetGadgetFX( Handle( grp ), Value )
				EndIf
			Next
			For img.ImageBox = Each ImageBox
				If img\Parent = ID
					FUI_SetGadgetFX( Handle( img ), Value )
				EndIf
			Next
			For lbl.Label = Each Label
				If lbl\Parent = ID
					FUI_SetGadgetFX( Handle( lbl ), Value )
				EndIf
			Next
			For lst.ListBox = Each ListBox
				If lst\Parent = ID
					FUI_SetGadgetFX( Handle( lst ), Value )
				EndIf
			Next
			For prg.ProgressBar = Each ProgressBar
				If prg\Parent = ID
					FUI_SetGadgetFX( Handle( prg ), Value )
				EndIf
			Next
			For rad.Radio = Each Radio
				If rad\Parent = ID
					FUI_SetGadgetFX( Handle( rad ), Value )
				EndIf
			Next
			For scroll.ScrollBar = Each ScrollBar
				If scroll\Parent = ID
					FUI_SetGadgetFX( Handle( scroll ), Value )
				EndIf
			Next
			For sld.Slider = Each Slider
				If sld\Parent = ID
					FUI_SetGadgetFX( Handle( sld ), Value )
				EndIf
			Next
			For spn.Spinner = Each Spinner
				If spn\Parent = ID
					FUI_SetGadgetFX( Handle( spn ), Value )
				EndIf
			Next
			For txt.TextBox = Each TextBox
				If txt\Parent = ID
					FUI_SetGadgetFX( Handle( txt ), Value )
				EndIf
			Next
			For tree.TreeView = Each TreeView
				If tree\Parent = ID
					FUI_SetGadgetFX( Handle( tree ), Value )
				EndIf
			Next
			For view.View = Each View
				If view\Parent = ID
					FUI_SetGadgetFX( Handle( view ), Value )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	pan.Panel = Object.Panel( ID )
	If pan <> Null
		EntityFX pan\Mesh, Value
		EntityFX pan\DropDown, Value
		
		If AffectChildren = True
			For Tab.Tab = Each Tab
				If Tab\Parent = ID
					FUI_SetGadgetFX( Handle( Tab ), Value )
				EndIf
			Next
			For pan2.Panel = Each Panel
				If pan2\Parent = ID
					FUI_SetGadgetFX( Handle( pan2 ), Value )
				EndIf
			Next
			For btn.Button = Each Button
				If btn\Parent = ID
					FUI_SetGadgetFX( Handle( btn ), Value )
				EndIf
			Next
			For chk.CheckBox = Each CheckBox
				If chk\Parent = ID
					FUI_SetGadgetFX( Handle( chk ), Value )
				EndIf
			Next
			For cbo.ComboBox = Each ComboBox
				If cbo\Parent = ID
					FUI_SetGadgetFX( Handle( cbo ), Value )
				EndIf
			Next
			For grp.GroupBox = Each GroupBox
				If grp\Parent = ID
					FUI_SetGadgetFX( Handle( grp ), Value )
				EndIf
			Next
			For img.ImageBox = Each ImageBox
				If img\Parent = ID
					FUI_SetGadgetFX( Handle( img ), Value )
				EndIf
			Next
			For lbl.Label = Each Label
				If lbl\Parent = ID
					FUI_SetGadgetFX( Handle( lbl ), Value )
				EndIf
			Next
			For lst.ListBox = Each ListBox
				If lst\Parent = ID
					FUI_SetGadgetFX( Handle( lst ), Value )
				EndIf
			Next
			For prg.ProgressBar = Each ProgressBar
				If prg\Parent = ID
					FUI_SetGadgetFX( Handle( prg ), Value )
				EndIf
			Next
			For rad.Radio = Each Radio
				If rad\Parent = ID
					FUI_SetGadgetFX( Handle( rad ), Value )
				EndIf
			Next
			For scroll.ScrollBar = Each ScrollBar
				If scroll\Parent = ID
					FUI_SetGadgetFX( Handle( scroll ), Value )
				EndIf
			Next
			For sld.Slider = Each Slider
				If sld\Parent = ID
					FUI_SetGadgetFX( Handle( sld ), Value )
				EndIf
			Next
			For spn.Spinner = Each Spinner
				If spn\Parent = ID
					FUI_SetGadgetFX( Handle( spn ), Value )
				EndIf
			Next
			For txt.TextBox = Each TextBox
				If txt\Parent = ID
					FUI_SetGadgetFX( Handle( txt ), Value )
				EndIf
			Next
			For tree.TreeView = Each TreeView
				If tree\Parent = ID
					FUI_SetGadgetFX( Handle( tree ), Value )
				EndIf
			Next
			For view.View = Each View
				If view\Parent = ID
					FUI_SetGadgetFX( Handle( view ), Value )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	btn.Button = Object.Button( ID )
	If btn <> Null
		EntityFX btn\Mesh, Value
		
		Return True
	EndIf
	chk.CheckBox = Object.CheckBox( ID )
	If chk <> Null
		EntityFX chk\Mesh, Value
		
		Return True
	EndIf
	cbo.ComboBox = Object.ComboBox( ID )
	If cbo <> Null
		EntityFX cbo\Mesh, Value
		EntityFX cbo\DropDown, Value
		
		If AffectChildren = True
			For cboi.ComboBoxItem = Each ComboBoxItem
				If cboi\Owner = cbo
					FUI_SetGadgetFX( Handle( cboi ), Value )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	cboi.ComboBoxItem = Object.ComboBoxItem( ID )
	If cboi <> Null
		EntityFX cboi\Mesh, Value
		
		Return True
	EndIf
	grp.GroupBox = Object.GroupBox( ID )
	If grp <> Null
		EntityFX grp\Mesh, Value
		EntityFX grp\MeshText, Value
		
		Return True
	EndIf
	img.ImageBox = Object.ImageBox( ID )
	If img <> Null
		EntityFX img\Mesh, Value
		
		Return True
	EndIf
	lbl.Label = Object.Label( ID )
	If lbl <> Null
		EntityFX lbl\Mesh, Value
		
		Return True
	EndIf
	lst.ListBox = Object.ListBox( ID )
	If lst <> Null
		EntityFX lst\Mesh, Value
		
		If AffectChildren = True
			For lsti.ListBoxItem = Each ListBoxItem
				If lsti\Owner = lst
					FUI_SetGadgetFX( Handle( lsti ), Value )
				EndIf
			Next
		EndIf
		
		Return True
	EndIf
	lsti.ListBoxItem = Object.ListBoxItem( ID )
	If lsti <> Null
		EntityFX lsti\Mesh, Value
		
		Return True
	EndIf
	prg.ProgressBar = Object.ProgressBar( ID )
	If prg <> Null
		EntityFX prg\Mesh, Value
		EntityFX prg\MeshText, Value
		EntityFX prg\Bar, Value
		
		Return True
	EndIf
	rad.Radio = Object.Radio( ID )
	If rad <> Null
		EntityFX rad\Mesh, Value
		
		Return True
	EndIf
	scroll.ScrollBar = Object.ScrollBar( ID )
	If scroll <> Null
		EntityFX scroll\Mesh, Value
		EntityFX scroll\ScrollBar, Value
		EntityFX scroll\Btn1, Value
		EntityFX scroll\Btn2, Value
		
		Return True
	EndIf
	sld.Slider = Object.Slider( ID )
	If sld <> Null
		EntityFX sld\Mesh, Value
		EntityFX sld\ScrollBar, Value
		
		Return True
	EndIf
	spn.Spinner = Object.Spinner( ID )
	If spn <> Null
		EntityFX spn\Mesh, Value
		EntityFX spn\MeshText, Value
		EntityFX spn\Btn1, Value
		EntityFX spn\Btn2, Value
		
		Return True
	EndIf
	txt.TextBox = Object.TextBox( ID )
	If txt <> Null
		EntityFX txt\Mesh, Value
		EntityFX txt\MeshText, Value
		
		Return True
	EndIf
	tree.TreeView = Object.TreeView( ID )
	If tree <> Null
		EntityFX tree\Mesh, Value
		
		Return True
	EndIf
	view.View = Object.View( ID )
	If view <> Null
		EntityFX view\Mesh, Value
		EntityFX view\MeshText, Value
		
		Return True
	EndIf
	
	Return False
	
End Function

Function FUI_ViewPick.BBEntity( ID, X, Y )
	
	view.View = Object.View( ID )
	If view <> Null
		app\Picked = CameraPick( view\Cam, X - FUI_InternalX( view\Mesh ) - 2, Y - FUI_InternalY( view\Mesh ) - 2 )
		
		Return app\Picked
	EndIf
	Return Null
	
End Function

Function FUI_NoActiveGadgets(  )
	
	If app\actMenuTitle <> Null
		Return False
	EndIf
	If app\actContextMenu <> Null
		Return False
	EndIf
	If app\actComboBox <> Null
		Return False
	EndIf
	Return True
	
End Function
;#End Region

;#Region ---------- Internal F-UI Functions ----
Function FUI_MouseOver( X = 0, Y = 0, W = 0, H = 0 )
	
	If app\MX >= X And app\MX < X + W And app\MY >= Y And app\MY < Y + H
		Return True
	Else
		Return False
	EndIf
	
End Function

Function FUI_UpdateMouse( MX=0, MY=0 )
	
	app\MX		= MouseX() - MX
	app\MY		= MouseY() - MY
	app\MZ		= MouseZ()
	
	app\MXS		= MouseXSpeed()
	app\MYS		= MouseYSpeed()
	app\MZS		= MouseZSpeed()
	
	;Mouse Button One
	If app\MB1 = 0
		If MouseDown(1)
			app\MB1 = 1
		EndIf
	ElseIf app\MB1 = 1
		If MouseDown(1)
			app\MB1 = 2
		Else
			app\MB1 = 0
		EndIf
	ElseIf app\MB1 = 2
		If MouseDown(1)=False
			app\MB1 = 3
		EndIf
	ElseIf app\MB1 = 3
		app\MB1 = 0
	EndIf
	
	;Mouse Button Two
	If app\MB2 = 0
		If MouseDown(2)
			app\MB2 = 1
		EndIf
	ElseIf app\MB2 = 1
		If MouseDown(2)
			app\MB2 = 2
		Else
			app\MB2 = 0
		EndIf
	ElseIf app\MB2 = 2
		If MouseDown(2)=False
			app\MB2 = 3
		EndIf
	ElseIf app\MB2 = 3
		app\MB2 = 0
	EndIf
	
	;Mouse Button Three
	If app\MB3 = 0
		If MouseDown(3)
			app\MB3 = 1
		EndIf
	ElseIf app\MB3 = 1
		If MouseDown(3)
			app\MB3 = 2
		Else
			app\MB3 = 0
		EndIf
	ElseIf app\MB3 = 2
		If MouseDown(3)=False
			app\MB3 = 3
		EndIf
	ElseIf app\MB3 = 3
		app\MB3 = 0
	EndIf
	
	app\Win32Mouse = IDCArrow
	PositionEntity app\Mouse, MouseX(  ) - 0.5, MouseY(  ) - 0.5, 0.0
	
End Function

Function FUI_UpdateToolTip( tip$ )
	
	If tip$ <> app\ToolTip
		app\ToolTipTimer = app\ToolTipTimer + 1
		If app\ToolTipTimer >= app\ToolTipDelay
			app\ToolTip = tip$
			app\ToolTipX = app\MX + 10
			app\ToolTipY = app\MY + 25
			app\ToolTipTimer = 0
		EndIf
	Else
		app\ToolTipTimer = 0
	EndIf
	If app\ToolTip > ""
		SetFont app\fntGadget
		
		app\ToolTipW = StringWidth( app\ToolTip ) + 7
		app\ToolTipH = FontHeight() + 5
		
		If app\ToolTipX + app\ToolTipW + 5 > app\W app\ToolTipX = app\W - app\ToolTipW - 5
		If app\ToolTipY + app\ToolTipH + 5 > app\H app\ToolTipY = app\H - app\ToolTipH - 5
		
		FUI_SetPosition app\ToolTipMesh, app\ToolTipX, app\ToolTipY
		FUI_BuildToolTip
		
		ShowEntity app\ToolTipMesh
	Else
		HideEntity app\ToolTipMesh
	EndIf
	
	For gad.ToolTip = Each ToolTip
		If FUI_OverGadget( gad\Parent ) = True
			app\TempTip = gad\Caption
			
			Return
		EndIf
	Next
	app\TempTip = ""
	
End Function

Function FUI_ShortCut( Key1$, Key2$="", Hit=True )

	Key1$ = Lower( Key1$ )
	Key2$ = Lower( Key2$ )
	
	Select Key1$
		Case "esc"
			Key1 = 1
		Case "lctrl", "ctrl"
			Key1 = 29
		Case "lshift", "shift"
			Key1 = 42
		Case "lalt", "alt"
			Key1 = 56
		Case "rctrl"
			Key1 = 157
		Case "rshift"
			Key1 = 54
		Case "ralt"
			Key1 = 184
		Case "f1"
			Key1 = 59
		Case "f2"
			Key1 = 60
		Case "f3"
			Key1 = 61
		Case "f4"
			Key1 = 62
		Case "f5"
			Key1 = 63
		Case "f6"
			Key1 = 64
		Case "f7"
			Key1 = 65
		Case "f8"
			Key1 = 66
		Case "f9"
			Key1 = 67
		Case "f10"
			Key1 = 68
	End Select
	
	Select Key2$
		Case "lctrl", "ctrl"
			Key2 = 29
		Case "lshift", "shift"
			Key2 = 42
		Case "lalt", "alt"
			Key2 = 56
		Case "rctrl"
			Key2 = 157
		Case "rshift"
			Key2 = 54
		Case "ralt"
			Key2 = 184
		Case "f1"
			Key2 = 59
		Case "f2"
			Key2 = 60
		Case "f3"
			Key2 = 61
		Case "f4"
			Key2 = 62
		Case "f5"
			Key2 = 63
		Case "f6"
			Key2 = 64
		Case "f7"
			Key2 = 65
		Case "f8"
			Key2 = 66
		Case "f9"
			Key2 = 67
		Case "f10"
			Key2 = 68
		Case "a"
			Key2 = 30
		Case "b"
			Key2 = 48
		Case "c"
			Key2 = 46
		Case "d"
			Key2 = 32
		Case "e"
			Key2 = 18
		Case "f"
			Key2 = 33
		Case "g"
			Key2 = 34
		Case "h"
			Key2 = 35
		Case "i"
			Key2 = 23
		Case "j"
			Key2 = 36
		Case "k"
			Key2 = 37
		Case "l"
			Key2 = 38
		Case "m"
			Key2 = 50
		Case "n"
			Key2 = 49
		Case "o"
			Key2 = 24
		Case "p"
			Key2 = 25
		Case "q"
			Key2 = 16
		Case "r"
			Key2 = 19
		Case "s"
			Key2 = 31
		Case "t"
			Key2 = 20
		Case "u"
			Key2 = 22
		Case "v"
			Key2 = 47
		Case "w"
			Key2 = 17
		Case "x"
			Key2 = 45
		Case "y"
			Key2 = 21
		Case "z"
			Key2 = 44
	End Select
	
	If KeyDown( Int Key1 ) = True Or Key1 = "" 
		If Key2 > ""
			If Hit = False
				If KeyDown( Int Key2 )
					app\Idle = False
					Return True
				Else
					Return False
				EndIf
			Else
				If KeyHit( Int Key2 )
					app\Idle = False
					Return True
				Else
					Return False
				EndIf
			EndIf
		Else
			app\Idle = False
			Return True
		EndIf
	Else
		Return False
	EndIf

End Function

Function FUI_IsImageSource( Source$ )

	ext$ = Lower( Right( Source$, 4 ) )
	Select ext$
		Case ".bmp", ".jpg", ".png", ".pcx", ".iff"
			Return True
	End Select
	Return False

End Function

Function FUI_SetColor( RGB, Gradient=False )

	If Gradient = False
		app\R = (RGB Shr 16) And $FF
		app\G = (RGB Shr 8) And $FF
		app\B = RGB And $FF
		
		app\R2 = app\R
		app\G2 = app\G
		app\B2 = app\B
		
		Color app\R, app\G, app\B
	Else
		app\R2 = (RGB Shr 16) And $FF
		app\G2 = (RGB Shr 8) And $FF
		app\B2 = RGB And $FF
	EndIf
	
	app\NR = app\R
	app\NG = app\G
	app\NB = app\B
	
	app\NR2 = app\R2
	app\NG2 = app\G2
	app\NB2 = app\B2
	
End Function

Function FUI_AdjustColor( Offset=0, Gradient=False )
	
	If Gradient = False
		R = app\R + Offset
		G = app\G + Offset
		B = app\B + Offset
	Else
		R = app\R2 + Offset
		G = app\G2 + Offset
		B = app\B2 + Offset
	EndIf
	
	If R > 255 R = 255
	If R < 0 R = 0
	
	If G > 255 G = 255
	If G < 0 G = 0
	
	If B > 255 B = 255
	If B < 0 B = 0
	
	Color R, G, B
	
	If Gradient = False
		app\NR = R
		app\NG = G
		app\NB = B
		
		app\NR2 = R
		app\NG2 = G
		app\NB2 = B
	Else
		app\NR2 = R
		app\NG2 = G
		app\NB2 = B
	EndIf
	
End Function

Function FUI_SetAlpha( A#, Gradient=False )
	
	If Gradient = False
		app\A = A#
		app\A2 = A#
	Else
		app\A2 = A#
	EndIf
	
End Function

Function FUI_SetGradientDir( Dir=DIR_HORIZONTAL )

	app\GradDir = Dir
	
End Function

Function FUI_Parse$( Message$, Item, Sep$ = "," )
	Local fas, count, spos, epos
	Repeat
		fas = Instr(Message$,Sep$,fas+1)
		count = count + 1
	Until fas = 0 Or count = Item
	If fas = 0 And Item > 0;item > count
		Return ""
	EndIf
	spos = fas+1
	epos = Instr(Message$+Sep$,Sep$,fas+1)
	Return Mid$(Message$,spos,(epos-spos))
End Function

Function FUI_CountItems( Message$, Sep$ )
	Local fas, count
	Repeat
		fas = Instr(Message$,Sep$,fas+1)
		If fas<>0 Then count = count + 1
	Until fas = 0
	Return count+1
End Function

Function FUI_Interpolate#(value#, vMin#, vMax#, retMin#, retMax#)

	Return retMin + ((value-vMin) * (retMax - retMin)) / (vMax-vMin)
	
End Function

Function FUI_SetTexture( Mesh.BBEntity, Frame )
	
	tex.BBTexture = FUI_GadgetTexture( Mesh )
	EntityTexture Mesh, tex, Frame
	FreeTexture tex
	
End Function

Function FUI_SetPosition( mesh.BBEntity, X, Y )
	
	PositionEntity mesh, X-0.5, Y-0.5, 0.0
	
End Function

Function FUI_SetChildPosition( mesh.BBEntity, X, Y )
	
	PositionEntity mesh, X,-Y, 0.0
	
End Function

Function FUI_InternalX#( Mesh.BBEntity )
	
	TFormPoint EntityX( Mesh, 1 ), EntityY( Mesh, 1 ), EntityZ( Mesh, 1 ), Null, app\Pivot
	Return TFormedX(  ) + 0.5
	
End Function

Function FUI_InternalY#( Mesh.BBEntity )
	
	TFormPoint EntityX( Mesh, 1 ), EntityY( Mesh, 1 ), EntityZ( Mesh, 1 ), Null, app\Pivot
	Return TFormedY(  ) + 0.5
	
End Function
;#End Region

;#Region ---------- Skin Functions -------------
Function FUI_SkinDefaults(  )

	col = FUI_RGBToInt( 34, 108, 170 )
	
	;Colors
	SC_FORM										= FUI_RGBToInt( 51, 51, 51 ) ;Title Bar [230, 230, 230]
	SC_FORM_BORDER								= FUI_RGBToInt( 0, 0, 0 ) ; Main Window Border
	SC_TITLEBAR									= col
	SC_TITLEBAR_TEXT							= FUI_RGBToInt( 200, 200, 200 ) ;Title Bar Text in Pop-Up windows [255, 255, 255]
	SC_MENUBAR									= FUI_RGBToInt( 230, 230, 230 ) 
	SC_MENUBAR_BORDER							= FUI_RGBToInt( 255, 255, 255 ) 
	SC_STATUSBAR								= FUI_RGBToInt( 210, 210, 210 ) 
	SC_STATUSBAR_TEXT							= FUI_RGBToInt( 190, 190, 190 ) ;[0, 0, 0]
	SC_STATUSBAR_BORDER							= FUI_RGBToInt( 190, 190, 190 )
	
	SC_MENUTITLE								= FUI_RGBToInt( 230, 230, 230 )
	SC_MENUTITLE_OVER							= col
	SC_MENUTITLE_SEL							= col
	SC_MENUTITLE_TEXT							= FUI_RGBToInt( 190, 190, 190 ) ; Title Bar Text [0, 0, 0]
	SC_MENUTITLE_TEXT_OVER						= FUI_RGBToInt( 255, 255, 255 ) 
	SC_MENUTITLE_TEXT_SEL						= FUI_RGBToInt( 255, 255, 255 )
	SC_MENUTITLE_BORDER							= FUI_RGBToInt( 230, 230, 230 )
	SC_MENUTITLE_BORDER_OVER					= FUI_RGBToInt( 0, 0, 0 ) ; Title bar border button text hover
	SC_MENUTITLE_BORDER_SEL						= FUI_RGBToInt( 0, 0, 0 ) ; Title bar border button text selected
	
	SC_MENUITEM									= FUI_RGBToInt( 230, 230, 230 )
	SC_MENUITEM_OVER							= col
	SC_MENUITEM_SEL								= col
	SC_MENUITEM_TEXT							= FUI_RGBToInt( 200, 200, 200 ) ; Main Menu drop down text [0,0,0]
	SC_MENUITEM_TEXT_OVER						= FUI_RGBToInt( 255, 255, 255 ) 
	SC_MENUITEM_TEXT_SEL						= FUI_RGBToInt( 255, 255, 255 ) 
	SC_MENUITEM_BORDER							= FUI_RGBToInt( 230, 230, 230 )
	SC_MENUITEM_BORDER_OVER						= FUI_RGBToInt( 0, 0, 0 )
	SC_MENUITEM_BORDER_SEL						= FUI_RGBToInt( 0, 0, 0 )
	
	SC_MENUDROPDOWN								= FUI_RGBToInt( 110, 110, 110 ) ; Drop down box title bar [230,230,230]
	SC_MENUDROPDOWN_BORDER						= FUI_RGBToInt( 0, 0, 0 )
	SC_MENUDROPDOWN_STRIP						= col
	
	SC_TOOLTIP									= FUI_RGBToInt( 210, 210,210 ) ; Tool tip background [255,255,255]
	SC_TOOLTIP_BORDER							= FUI_RGBToInt( 0, 0, 0 ) ;Tool Tip Border 
	SC_TOOLTIP_TEXT								= FUI_RGBToInt( 0, 0, 0 ) ;Tool Tip Text
	
	SC_GADGET									= FUI_RGBToInt( 110, 110, 110 ) ; Main Background of tabs [210, 210, 210]
	SC_GADGET_TEXT								= FUI_RGBToInt( 200, 200, 200 ) ; Main Text of tabs [0,0,0]
	SC_GADGET_COLOR								= col
	SC_GADGET_COLOR_TEXT						= FUI_RGBToInt( 0, 0, 0 ) 
	SC_GADGET_BORDER							= FUI_RGBToInt( 0, 0, 0 ) 
	
	SC_INPUT									= FUI_RGBToInt( 70, 70, 70 ) ; Input fields [255,255,255]
	SC_INPUT_TEXT								= FUI_RGBToInt( 200, 200, 200 ) ; Input Field Text [0,0,0]
	SC_INPUT_COLOR								= col
	SC_INPUT_COLOR_TEXT							= FUI_RGBToInt( 200, 200, 200 ) ; Input Field Text Hover [0,0,0] 
	SC_INPUT_BORDER								= FUI_RGBToInt( 130, 130, 130 ) ; Input Field Borders [0,0,0]
	
	SC_VIEW										= FUI_RGBToInt( 184, 183, 187 )
	SC_VIEW_BORDER								= FUI_RGBToInt( 0, 0, 0 )
	SC_VIEW_BORDER_SEL							= FUI_RGBToInt( 0, 0, 0 )
	
	If app\fntWindow <> Null
		FreeFont app\fntWindow
		app\fntWindow = Null
	EndIf
	app\fntWindow = LoadFont( FONT_WINDOW_FACE, FONT_WINDOW_SIZE )
	
	If app\fntMenu <> Null
		FreeFont app\fntMenu
		app\fntMenu = Null
	EndIf
	app\fntMenu = LoadFont( FONT_MENU_FACE, FONT_MENU_SIZE )
	
	If app\fntGadget <> Null
		FreeFont app\fntGadget
		app\fntGadget = Null
	EndIf
	app\fntGadget = LoadFont( FONT_GADGET_FACE, FONT_GADGET_SIZE )
	
End Function

Function FUI_Rect( X, Y, W, H, surf.BBSurface, fill=True )
	
	If W = 0 Or H = 0 Or surf = Null
		Return
	EndIf
	
	If fill = True
		v0 = AddVertex( surf, X + 0,-Y + 0, 0, 0, 0 )
		v1 = AddVertex( surf, X + W,-Y + 0, 0, 1, 0 )
		v2 = AddVertex( surf, X + W,-Y - H, 0, 1, 1 )
		v3 = AddVertex( surf, X + 0,-Y - H, 0, 0, 1 )
		AddTriangle surf, v0, v1, v3
		AddTriangle surf, v3, v1, v2
		
		If app\GradDir = DIR_HORIZONTAL
			VertexColor surf, v0, app\NR, app\NG, app\NB
			VertexColor surf, v1, app\NR2, app\NG2, app\NB2
			VertexColor surf, v2, app\NR2, app\NG2, app\NB2
			VertexColor surf, v3, app\NR, app\NG, app\NB
		Else
			VertexColor surf, v0, app\NR, app\NG, app\NB
			VertexColor surf, v1, app\NR, app\NG, app\NB
			VertexColor surf, v2, app\NR2, app\NG2, app\NB2
			VertexColor surf, v3, app\NR2, app\NG2, app\NB2
		EndIf
		
		TW = FUI_NextPower( W )
		TH = FUI_NextPower( H )
		
		U# = Float( W ) / TW
		V# = Float( H ) / TH
		
		VertexTexCoords surf, v1, U#, 0.0
		VertexTexCoords surf, v2, U#, V#
		VertexTexCoords surf, v3, 0.0, V#
	Else
		FUI_Rect X, Y, W, 1, surf
		FUI_Rect X, Y+1, 1, H-2, surf
		FUI_Rect X, Y+H-1, W, 1, surf
		FUI_Rect X+W-1, Y+1, 1, H-2, surf
	EndIf
	
End Function

Function FUI_CreateControl.BBEntity( Parent.BBEntity=Null )
	
	If Parent = Null
		Parent = app\Pivot
	EndIf
	
	Mesh.BBEntity = CreateMesh( Parent )
	EntityFX Mesh, 1+2+8
	ScaleEntity Mesh, 1.0, 1.0, 1.0
	
	Surf.BBSurface = CreateSurface( Mesh )
	
;	Brush = CreateBrush(  )
;	PaintSurface Surf, Brush
;	FreeBrush Brush
	
	If Parent = app\Pivot
		RotateEntity Mesh, 0.0, 180.0, 180.0
	EndIf
	
	Return Mesh
	
End Function

Function FUI_CreateTexture.BBTexture( W, H, Flags=1+2, Frames=1, Alpha=0 )
	
	TW		= FUI_NextPower( W )
	TH		= FUI_NextPower( H )
	ARGB	= (0 Or (0 Shl 8) Or (0 Shl 16) Or (Alpha Shl 24))
	
	tex.BBTexture = CreateTexture( TW, TH, Flags, Frames )
	
	For A = 1 To Frames
		LockBuffer TextureBuffer( tex, A-1 )
		For X = 0 To TW - 1
			For Y = 0 To TH - 1
				WritePixelFast X, Y, ARGB, TextureBuffer( tex, A-1 )
			Next
		Next
		UnlockBuffer TextureBuffer( tex, A-1 )
	Next
	
	Return tex
	
End Function

Function FUI_LoadTexture.BBTexture( fName$, flags=1, W=0, H=0, frames=3 )
	
	img.BBImage		= LoadImage( fName$ )
	
	If img = Null
		Return Null
	EndIf
	
	If W = 0 And H = 0
		W = ImageHeight( img )
		H = W
	EndIf
	
	TW		= FUI_NextPower( W )
	TH		= FUI_NextPower( H )
	ARGB	= (0 Or (0 Shl 8) Or (0 Shl 16) Or (Alpha Shl 24))
	
	tex.BBTexture = CreateTexture( TW, TH, flags, frames )
	
	LockBuffer ImageBuffer( img )
	For A = 0 To frames - 1
		LockBuffer TextureBuffer( tex, A )
		For X = 0 To W - 1
			For Y = 0 To H - 1
				RGB = ReadPixelFast( X + (A*W), Y, ImageBuffer( img ) ) And $FFFFFFFF
				WritePixelFast X, Y, RGB, TextureBuffer( tex, A )
			Next
		Next
		UnlockBuffer TextureBuffer( tex, A )
	Next
	UnlockBuffer ImageBuffer( img )
	
	FreeImage img
	
	Return tex
	
End Function

Function FUI_SkinTexture.BBTexture( id$, W=0, H=0 )
	
	app\Buffer = GraphicsBuffer(  )
	
	fName$ = SKIN_PATH$
	If Right( fName$, 1 ) <> "\"
		fName$ = fName$ + "\"
	EndIf
	
	ARGB	= (0 Or (0 Shl 8) Or (0 Shl 16) Or (Alpha Shl 24))
	
	TW		= FUI_NextPower( W )
	TH		= FUI_NextPower( H )
	
	id$ = Lower( id$ )
	Select id$
		Case "tabpage"
			Local dimg1.BBImage[3]
			dimg1[0]		= LoadImage( fName$ + "tabpage\selected.bmp" )
			dimg1[1]		= LoadImage( fName$ + "tabpage\unselected.bmp" )
			dimg1[2]		= LoadImage( fName$ + "tabpage\disabled.bmp" )
			tex.BBTexture = CreateTexture( TW, TH, 1+2, 3 )
			
			For LOOP = 0 To 2
				If dimg1[LOOP] <> Null
					
					SetBuffer TextureBuffer( tex, LOOP )
					FUI_SetColor SC_GADGET
					Rect 0, 0, W, H
					
					SW = ImageWidth( dimg1[LOOP] ) / 3
					SH = ImageHeight( dimg1[LOOP] ) / 3
					LockBuffer ImageBuffer( dimg1[LOOP] )
					
					LockBuffer TextureBuffer( tex, LOOP )
					
					IY = 0
					If LOOP = 1
						OffY = 2
					Else
						OffY = 0
					EndIf
					
					;Top Left
					For X = 0 To SW - 1
						For Y = 0 To SH - 1
							RGB = ReadPixelFast( X, Y + IY, ImageBuffer( dimg1[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast X, Y + OffY, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					;Top Middle
					For X = SW To W - SW - 1
						For Y = 0 To SH - 1
							IX = X Mod SW
							RGB = ReadPixelFast( IX + SW, Y + IY, ImageBuffer( dimg1[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast X, Y + OffY, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					;Top Right
					For X = 0 To SW - 1
						For Y = 0 To SH - 1
							RGB = ReadPixelFast( X + SW * 2, Y + IY, ImageBuffer( dimg1[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast W - SW + X, Y + OffY, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					IY = SH * 2
					
					;Bottom Left
					For X = 0 To SW - 1
						For Y = 0 To SH - 1
							RGB = ReadPixelFast( X, Y + IY, ImageBuffer( dimg1[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast X, H - SH + Y, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					;Bottom Middle
					For X = SW To W - SW - 1
						For Y = 0 To SH - 1
							IX = X Mod SW
							RGB = ReadPixelFast( IX + SW, Y + IY, ImageBuffer( dimg1[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast X, H - SH + Y, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					;Bottom Right
					For X = 0 To SW - 1
						For Y = 0 To SH - 1
							RGB = ReadPixelFast( X + SW * 2, Y + IY, ImageBuffer( dimg1[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast W - SW + X, H - SH + Y, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					;Left Middle
					For X = 0 To SW
						For Y = SH To H - SH - 1
							IY = Y Mod SH
							RGB = ReadPixelFast( X, IY + SH, ImageBuffer( dimg1[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast X, Y + OffY, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					;Middle
					For X = SW To W - SW - 1
						For Y = SH To H - SH - 1
							IX = X Mod SW
							IY = Y Mod SH
							RGB = ReadPixelFast( IX + SW, IY + SH, ImageBuffer( dimg1[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast X, Y + OffY, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					;Right Middle
					For X = 0 To SW
						For Y = SH To H - SH - 1
							IY = Y Mod SH
							RGB = ReadPixelFast( X + SW*2, IY + SH, ImageBuffer( dimg1[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast W - SW + X, Y + OffY, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					UnlockBuffer TextureBuffer( tex, LOOP )
					UnlockBuffer ImageBuffer( dimg1[LOOP] )
					FreeImage dimg1[LOOP]
				Else
					FreeTexture tex
					Return Null
				EndIf
			Next
			
			SetBuffer app\Buffer
			Return tex
		Case "panel"
			Local dimg2.BBImage[3]
			dimg2[0]	= LoadImage( fName$ + "panel\up.bmp" )
			dimg2[1] = LoadImage( fName$ + "panel\down.bmp" )
			dimg2[2] = LoadImage( fName$ + "panel\disabled.bmp" )
			
			tex = CreateTexture( TW, TH, 1+2, 3 )
			For LOOP = 0 To 2
				If dimg2[LOOP] <> Null
					SW = ImageWidth( dimg2[LOOP] ) / 3
					LockBuffer ImageBuffer( dimg2[LOOP] )
					LockBuffer TextureBuffer( tex, LOOP )
					
					;Left Edge
					For X = 0 To SW - 1
						For Y = 0 To H - 1
							RGB = ReadPixelFast( X, Y, ImageBuffer( dimg2[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					;Middle
					For X = SW To W - SW - 1
						For Y = 0 To H - 1
							IX = X Mod SW
							RGB = ReadPixelFast( IX + SW, Y, ImageBuffer( dimg2[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					;Right Edge
					For X = 0 To SW - 1
						For Y = 0 To H - 1
							RGB = ReadPixelFast( X + SW * 2, Y, ImageBuffer( dimg2[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast W - SW + X, Y, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					UnlockBuffer TextureBuffer( tex, LOOP )
					UnlockBuffer ImageBuffer( dimg2[LOOP] )
					FreeImage dimg2[LOOP]
				Else
					FreeTexture tex
					Return Null
				EndIf
			Next
			
			Return tex
		Case "button"
			Local dimg3.BBImage[4]
			dimg3[0]		= LoadImage( fName$ + "button\up.bmp" )
			dimg3[1]		= LoadImage( fName$ + "button\over.bmp" )
			dimg3[2]		= LoadImage( fName$ + "button\down.bmp" )
			dimg3[3]		= LoadImage( fName$ + "button\disabled.bmp" )
			
			tex = CreateTexture( TW, TH, 1+2, 4 )
			
			For LOOP = 0 To 3
				If dimg3[LOOP] <> Null
					
					SetBuffer TextureBuffer( tex, LOOP )
					FUI_SetColor SC_GADGET
					Rect 0, 0, W, H
					
					SW = ImageWidth( dimg3[LOOP] ) / 3
					SH = ImageHeight( dimg3[LOOP] ) / 3
					LockBuffer ImageBuffer( dimg3[LOOP] )
					
					LockBuffer TextureBuffer( tex, LOOP )
					
					IY = 0
					
					;Top Left
					For X = 0 To SW - 1
						For Y = 0 To SH - 1
							RGB = ReadPixelFast( X, Y + IY, ImageBuffer( dimg3[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					;Top Middle
					For X = SW To W - SW - 1
						For Y = 0 To SH - 1
							IX = X Mod SW
							RGB = ReadPixelFast( IX + SW, Y + IY, ImageBuffer( dimg3[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					;Top Right
					For X = 0 To SW - 1
						For Y = 0 To SH - 1
							RGB = ReadPixelFast( X + SW * 2, Y + IY, ImageBuffer( dimg3[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast W - SW + X, Y, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					IY = SH * 2
					
					;Bottom Left
					For X = 0 To SW - 1
						For Y = 0 To SH - 1
							RGB = ReadPixelFast( X, Y + IY, ImageBuffer( dimg3[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast X, H - SH + Y, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					;Bottom Middle
					For X = SW To W - SW - 1
						For Y = 0 To SH - 1
							IX = X Mod SW
							RGB = ReadPixelFast( IX + SW, Y + IY, ImageBuffer( dimg3[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast X, H - SH + Y, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					;Bottom Right
					For X = 0 To SW - 1
						For Y = 0 To SH - 1
							RGB = ReadPixelFast( X + SW * 2, Y + IY, ImageBuffer( dimg3[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast W - SW + X, H - SH + Y, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					;Left Middle
					For X = 0 To SW
						For Y = SH To H - SH - 1
							IY = Y Mod SH
							RGB = ReadPixelFast( X, IY + SH, ImageBuffer( dimg3[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					;Middle
					For X = SW To W - SW - 1
						For Y = SH To H - SH - 1
							IX = X Mod SW
							IY = Y Mod SH
							RGB = ReadPixelFast( IX + SW, IY + SH, ImageBuffer( dimg3[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast X, Y, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					;Right Middle
					For X = 0 To SW
						For Y = SH To H - SH - 1
							IY = Y Mod SH
							RGB = ReadPixelFast( X + SW*2, IY + SH, ImageBuffer( dimg3[LOOP] ) ) And $FFFFFF
							R = (RGB Shr 16) And $FF
							G = (RGB Shr 8) And $FF
							B = RGB And $FF
							
							If R > 0 And G = 0 And B = 0
								A = R
								R = FUI_GetRed( SC_GADGET )
								G = FUI_GetGreen( SC_GADGET )
								B = FUI_GetBlue( SC_GADGET )
							ElseIf R = 255 And G = 0 And B = 255
								A = 0
							Else
								A = 255
							EndIf
							RGB = FUI_RGBToInt( R, G, B, A )
							
							WritePixelFast W - SW + X, Y, RGB, TextureBuffer( tex, LOOP )
						Next
					Next
					
					UnlockBuffer TextureBuffer( tex, LOOP )
					UnlockBuffer ImageBuffer( dimg3[LOOP] )
					FreeImage dimg3[LOOP]
				Else
					FreeTexture tex
					Return Null
				EndIf
			Next
			
			SetBuffer app\Buffer
			Return tex
	End Select
	
	Return Null
	
End Function

Function FUI_ClearTexture( tex.BBTexture, Alpha=0, W=0, H=0 )
	
	If W = 0
		TW		= TextureWidth( tex )
	Else
		TW		= W
	EndIf
	If H = 0
		TH		= TextureHeight( tex )
	Else
		TH		= H
	EndIf
	
	ARGB	= (0 Or (0 Shl 8) Or (0 Shl 16) Or (Alpha Shl 24))
	
	LockBuffer TextureBuffer( tex )
	For X = 0 To TW - 1
		For Y = 0 To TH - 1
			WritePixelFast X, Y, ARGB, TextureBuffer( tex )
		Next
	Next
	UnlockBuffer TextureBuffer( tex )
	
End Function

Function FUI_Text( tex.BBTexture, X, Y, txt$, CenterX=0, CenterY=0 )
	
	If txt$ = ""
		Return
	EndIf
	
	RGB = FUI_GetColor(  )
	R = FUI_GetRed( RGB )
	G = FUI_GetGreen( RGB )
	B = FUI_GetBlue( RGB )
	
	If R < 128 R = 255 Else R = 0
	If G < 128 G = 255 Else G = 0
	If B < 128 B = 255 Else B = 0
	RGB = FUI_RGBToInt( R, G, B )
	
	If CenterX = True
		X = X - StringWidth( txt$ ) / 2
	EndIf
	If CenterY = True
		Y = Y - FontHeight() / 2
	EndIf
	
	W = TextureWidth( tex )
	
	If StringWidth( txt$ ) < W
		W = StringWidth( txt$ )
	EndIf
	
	pBuffer.BBBuffer = GraphicsBuffer(  )
	
	img.BBImage = CreateImage( W, FontHeight() )
	LockBuffer ImageBuffer( img )
	For PX = 0 To W - 1
		For PY = 0 To FontHeight() - 1
			WritePixelFast PX, PY, RGB, ImageBuffer( img )
		Next
	Next
	UnlockBuffer ImageBuffer( img )
	
	SetBuffer ImageBuffer( img )
	Text 0, 0, txt$
	SetBuffer pBuffer
	
	LockBuffer ImageBuffer( img )
	LockBuffer
	
	For PX = X To X + W - 1
		For PY = Y To Y + FontHeight() - 1
			If PX-X < ImageWidth( img ) And PX-X >= 0 And PY-Y < ImageHeight( img ) And PY-Y >= 0
				ARGB = ReadPixelFast( PX-X, PY-Y, ImageBuffer( img ) ) And $ffffff
				NR = FUI_GetRed( ARGB )
				NG = FUI_GetGreen( ARGB )
				NB = FUI_GetBlue( ARGB )
				
				If PX >= 0 And PX < TextureWidth( tex ) And PY >= 0 And PY < TextureHeight( tex )
					If (NR > R+RGB_ACCURACY Or NR < R-RGB_ACCURACY) And (NG > G+RGB_ACCURACY Or NG < G-RGB_ACCURACY) And (NB > B+RGB_ACCURACY Or NB < B-RGB_ACCURACY)
						WritePixelFast PX, PY, FUI_GetColor(  )
					EndIf
				EndIf
			EndIf
		Next
	Next
	UnlockBuffer pBuffer
	UnlockBuffer

	FreeImage img
	img = Null
	
End Function

Function FUI_GadgetTexture.BBTexture( mesh.BBEntity )
	
	brush.BBBrush = GetEntityBrush( mesh )
	If brush <> Null
		tex.BBTexture = GetBrushTexture( brush )
		FreeBrush brush
	Else
		tex = Null
	EndIf
	
	Return tex
	
End Function

Function FUI_NextPower(n)
	Return 2 Shl (Len(Int(Bin(n-1)))-1)
End Function

Function FUI_GenerateIcons(  )

	Restore FUI_ICON_NEW
	ICON_NEW = CreateImage( 16, 16 )
	LockBuffer ImageBuffer( ICON_NEW )
	For X = 0 To 15
		For Y = 0 To 15
			Read RGB
			WritePixelFast Y, X, RGB, ImageBuffer( ICON_NEW )
		Next
	Next
	UnlockBuffer ImageBuffer( ICON_NEW )
	
	Restore FUI_ICON_OPEN
	ICON_OPEN = CreateImage( 16, 16 )
	LockBuffer ImageBuffer( ICON_OPEN )
	For X = 0 To 15
		For Y = 0 To 15
			Read RGB
			WritePixelFast Y, X, RGB, ImageBuffer( ICON_OPEN )
		Next
	Next
	UnlockBuffer ImageBuffer( ICON_OPEN )
	
	Restore FUI_ICON_SAVE
	ICON_SAVE = CreateImage( 16, 16 )
	LockBuffer ImageBuffer( ICON_SAVE )
	For X = 0 To 15
		For Y = 0 To 15
			Read RGB
			WritePixelFast Y, X, RGB, ImageBuffer( ICON_SAVE )
		Next
	Next
	UnlockBuffer ImageBuffer( ICON_SAVE )
	
End Function
;#End Region

;#Region ---------- 3D Mesh Functions ----------
Function FUI_CreateCone.BBEntity( Segments = 8, Solid = 1, Parent.BBEntity = Null )
	
	m.Mesh = New Mesh
	m\ID = 2
	m\Mesh = CreateCone( Segments, Solid, Parent )
	
	Return m\Mesh
	
End Function

Function FUI_CreateCube.BBEntity( Parent.BBEntity = Null )
	
	m.Mesh = New Mesh
	m\ID = 2
	m\Mesh = CreateCube( Parent )
	
	Return m\Mesh
	
End Function

Function FUI_CreateCylinder.BBEntity( Segments = 8, Solid = 1, Parent.BBEntity = Null )
	
	m.Mesh = New Mesh
	m\ID = 2
	m\Mesh = CreateCylinder( Segments, Solid, Parent )
	
	Return m\Mesh
	
End Function

Function FUI_CreateSphere.BBEntity( Segments = 8, Parent.BBEntity = Null )
	
	m.Mesh = New Mesh
	m\ID = 2
	m\Mesh = CreateSphere( Segments, Parent )
	
	Return m\Mesh
	
End Function

Function FUI_LoadMesh.BBEntity( fName$ )
	
	m.Mesh = New Mesh
	m\ID = 2
	m\Mesh = LoadMesh( fName$ )
	HideEntity m\Mesh
	
	Return m\Mesh
	
End Function

Function FUI_LoadAnimMesh.BBEntity( fName$ )
	
	m.Mesh = New Mesh
	m\ID = 2
	m\Mesh = LoadAnimMesh( fName$ )
	HideEntity m\Mesh
	
	Return m\Mesh
	
End Function

Function FUI_FreeEntity( mesh.BBEntity )
	
	For m.Mesh = Each Mesh
		If m\Mesh = mesh
			FreeEntity m\Mesh
			Delete m
			
			Exit
		EndIf
	Next
	
End Function

Function FUI_FreeAllEntities( ID=2 )
	
	For m.Mesh = Each Mesh
		If m\ID = ID Or ID = 0
			FreeEntity m\Mesh
			Delete m
		EndIf
	Next
	
End Function

Function FUI_WireFrame.BBEntity( mesh.BBEntity, Value=True )
	
	For m.Mesh = Each Mesh
		If m\Mesh = mesh
			m\Wire = Value
			Exit
		EndIf
	Next
	
	Return m\Mesh
	
End Function

Function FUI_HideEntity.BBEntity( mesh.BBEntity )
	
	For m.Mesh = Each Mesh
		If m\Mesh = mesh
			m\Hidden = True
			Exit
		EndIf
	Next
	
	Return m\Mesh
	
End Function

Function FUI_ShowEntity.BBEntity( mesh.BBEntity )
	
	For m.Mesh = Each Mesh
		If m\Mesh = mesh
			m\Hidden = False
			Exit
		EndIf
	Next
	
	Return m\Mesh
	
End Function
;#End Region

;#Region ---------- 3D Line Functions ----------
Function FUI_3DLine( surf.BBSurface, x1#, y1#, z1#, x2#, y2#, z2#, r=255, g=255, b=255 )
	
	v0 = AddVertex( surf,x1#,y1#,z1# )
	v1 = AddVertex( surf,x2#,y2#,z2# )
	v2 = AddVertex( surf,(x1#+x2#)/2,(y1#+y2#)/2,(z1#+z2#)/2 )
	
	VertexColor surf, v0, r, g, b
	VertexColor surf, v1, r, g, b
	VertexColor surf, v2, r, g, b
	
	AddTriangle surf, v0, v1, v2
	
End Function

Function FUI_CreateGrid.BBEntity( Units=20, UnitSize#=1.0, Major=10, Parent=0 )
	
	m.Mesh = New Mesh
	m\ID = 1
	m\mesh = CreateMesh(  )
	surf.BBSurface = CreateSurface( m\mesh )
	
	HUnits# = Float( Units ) / 2.0
	If Major = 0 Major = 1
		
	For A = 0 To HUnits#
		If A Mod Major = 0
			r = 0
			g = 0
			b = 0
		Else
			r = 100
			g = 100
			b = 100
		EndIf
		FUI_3DLine surf, A*UnitSize#, 0.0, -(UnitSize#*HUnits#), A*UnitSize#, 0.0, (UnitSize#*HUnits#), r, g, b
		If A > 0
			FUI_3DLine surf,-A*UnitSize#, 0.0, -(UnitSize#*HUnits#),-A*UnitSize#, 0.0, (UnitSize#*HUnits#), r, g, b
		EndIf
	Next
	
	For A = 0 To HUnits#
		If A Mod Major = 0
			r = 0
			g = 0
			b = 0
		Else
			r = 100
			g = 100
			b = 100
		EndIf
		FUI_3DLine surf, -(UnitSize#*HUnits#), 0.0, A*UnitSize#, (UnitSize#*HUnits#), 0.0, A*UnitSize#, r, g, b
		If A > 0
			FUI_3DLine surf, -(UnitSize#*HUnits#), 0.0,-A*UnitSize#, (UnitSize#*HUnits#), 0.0,-A*UnitSize#, r, g, b
		EndIf
	Next
	
	m\Wire = True
	EntityFX m\mesh, 1+2+16
	Return m\mesh
	
End Function

Function FUI_CreateWireCone.BBEntity( Sides=8, Parent=0 )
	
	m.Mesh = New Mesh
	m\ID = 2
	m\mesh = CreateMesh(  )
	surf.BBSurface = CreateSurface( m\mesh )
	
	Radius# = 1.0
	MSides = 360/Sides
	For A = 0 To 360 - MSides
		If (A Mod MSides) = 0
			FUI_3DLine surf, Sin( A ) * Radius#,-1.0, Cos( A ) * Radius#, Sin( A + MSides ) * Radius#,-1.0, Cos( A + MSides ) * Radius#
			FUI_3DLine surf, Sin( A ) * Radius#,-1.0, Cos( A ) * Radius#, 0.0, 1.0, 0.0
		EndIf
	Next
	
	m\Wire = True
	EntityFX m\mesh, 1+16
	Return m\mesh
	
End Function

Function FUI_CreateWireCube.BBEntity( Parent=0 )
	
	m.Mesh = New Mesh
	m\ID = 2
	m\mesh = CreateMesh(  )
	surf.BBSurface = CreateSurface( m\mesh )
	
	;Sides
	FUI_3DLine( surf,-1.0,-1.0,-1.0,-1.0, 1.0,-1.0 )
	FUI_3DLine( surf,-1.0,-1.0, 1.0,-1.0, 1.0, 1.0 )
	FUI_3DLine( surf, 1.0,-1.0, 1.0, 1.0, 1.0, 1.0 )
	FUI_3DLine( surf, 1.0,-1.0,-1.0, 1.0, 1.0,-1.0 )
	
	;Bottom
	FUI_3DLine( surf,-1.0,-1.0,-1.0,-1.0,-1.0, 1.0 )
	FUI_3DLine( surf,-1.0,-1.0, 1.0, 1.0,-1.0, 1.0 )
	FUI_3DLine( surf, 1.0,-1.0, 1.0, 1.0,-1.0,-1.0 )
	FUI_3DLine( surf, 1.0,-1.0,-1.0,-1.0,-1.0,-1.0 )
	
	;Top
	FUI_3DLine( surf,-1.0, 1.0,-1.0,-1.0, 1.0, 1.0 )
	FUI_3DLine( surf,-1.0, 1.0, 1.0, 1.0, 1.0, 1.0 )
	FUI_3DLine( surf, 1.0, 1.0, 1.0, 1.0, 1.0,-1.0 )
	FUI_3DLine( surf, 1.0, 1.0,-1.0,-1.0, 1.0,-1.0 )
	
	m\Wire = True
	EntityFX m\mesh, 1+16
	Return m\mesh
	
End Function

Function FUI_CreateWireCylinder.BBEntity( Sides=8, Parent=0 )
	
	m.Mesh = New Mesh
	m\ID = 2
	m\mesh = CreateMesh(  )
	surf.BBSurface = CreateSurface( m\mesh )
	
	Radius# = 1.0
	MSides = 360/Sides
	For A = 0 To 360 - MSides
		If (A Mod MSides) = 0
			FUI_3DLine surf, Sin( A ) * Radius#,-1.0, Cos( A ) * Radius#, Sin( A + MSides ) * Radius#,-1.0, Cos( A + MSides ) * Radius#
			FUI_3DLine surf, Sin( A ) * Radius#, 1.0, Cos( A ) * Radius#, Sin( A + MSides ) * Radius#, 1.0, Cos( A + MSides ) * Radius#
			FUI_3DLine surf, Sin( A ) * Radius#,-1.0, Cos( A ) * Radius#, Sin( A ) * Radius#, 1.0, Cos( A ) * Radius#
		EndIf
	Next
	
	m\Wire = True
	EntityFX m\mesh, 1+16
	Return m\mesh
	
End Function

Function FUI_CreateWireSphere.BBEntity( Sides=8, Parent=0 )
	
	m.Mesh = New Mesh
	m\ID = 2
	m\mesh = CreateMesh(  )
	surf.BBSurface = CreateSurface( m\mesh )
	
	Radius# = 1.0
	MSides = 360/Sides
	For A = 0 To 360 - MSides
		If (A Mod MSides) = 0
			FUI_3DLine surf, Sin( A ) * Radius#, 0.0, Cos( A ) * Radius#, Sin( A + MSides ) * Radius#, 0.0, Cos( A + MSides ) * Radius#
			FUI_3DLine surf, 0.0, Sin( A ) * Radius#, Cos( A ) * Radius#, 0.0, Sin( A + MSides ) * Radius#, Cos( A + MSides ) * Radius#
			FUI_3DLine surf, Sin( A ) * Radius#, Cos( A ) * Radius#, 0.0, Sin( A + MSides ) * Radius#, Cos( A + MSides ) * Radius#, 0.0
		EndIf
	Next
	
	m\Wire = True
	EntityFX m\mesh, 1+16
	Return m\mesh
	
End Function
;#End Region

;#Region ---------- Multi-Lingual Functions ----
;(By Bot Builder)
Function FUI_SetBaseLanguage(Language$)
	app\BaseLang=ReadFile(Language$)
	app\NewLang=app\BaseLang
	app\BaseLangs$=Language$
	app\NewLangs$=Language$
End Function

Function FUI_SetLanguage(Language$)
	If app\BaseLang=Null Then
		RuntimeError "Call FUI_SetBaseLanguage before FUI_SetLanguage"
	EndIf

	app\OldLang=app\NewLang
	SeekFile app\OldLang,0
	app\NewLang=ReadFile(Language$)
	app\NewLangs$=Language$

	num$=ReadLine$(app\NewLang)
    n2$=ReadLine$(app\OldLang)
	For a=1 To Int num
		l$=Lower$(ReadLine$(app\OldLang))
		nl$=ReadLine$(app\NewLang)
		
		If nl$ > ""
			For mi.MenuItem=Each MenuItem
				If Lower$(mi\Caption$)=l$ Then
					FUI_SendMessage Handle(mi),M_SETCAPTION,nl$
					
					Exit
				EndIf
			Next
			
			For mt.MenuTitle=Each MenuTitle
				If Lower$(mt\Caption$)=l$ Then
					FUI_SendMessage Handle(mt),M_SETCAPTION,nl$
					
					Exit
				EndIf
			Next
	
			For w.Window=Each Window
				If Lower$(w\Caption$)=l$ Then
					FUI_SendMessage Handle(w),M_SETCAPTION,nl$
					
					Exit
				EndIf
				If Lower$(w\statuscaption$)=l$ Then
					FUI_SetStatusBarText Handle(w),nl$
					
					Exit
				EndIf
			Next
	
			For tp.TabPage=Each TabPage 
				If Lower$(tp\Caption$)=l$ Then
					FUI_SendMessage Handle(tp),M_SETCAPTION,nl$
					
					Exit
				EndIf
			Next
	
			For p.Panel=Each Panel
				If Lower$(p\Caption$)=l$ Then
					FUI_SendMessage Handle(p),M_SETCAPTION,nl$
					
					Exit
				EndIf
			Next
	
			For b.Button=Each Button
				If Lower$(b\Caption$)=l$ Then
					FUI_SendMessage Handle(b),M_SETCAPTION,nl$
					
					Exit
				EndIf
			Next
	
			For cb.CheckBox=Each CheckBox
				If Lower$(cb\Caption$)=l$ Then
					FUI_SendMessage Handle(cb),M_SETCAPTION,nl$
					
					Exit
				EndIf
			Next
	
			For cbi.ComboBoxItem=Each ComboBoxItem
				If Lower$(cbi\Caption$)=l$ Then
					FUI_SendMessage Handle(cbi),M_SETCAPTION,nl$
					
					Exit
				EndIf
			Next
	
			For gb.GroupBox=Each GroupBox
				If Lower$(gb\Caption$)=l$ Then
					FUI_SendMessage Handle(gb),M_SETCAPTION,nl$
					
					Exit
				EndIf
			Next
	
			For la.Label=Each Label
				If Lower$(la\Caption$)=l$ Then
					FUI_SendMessage Handle(la),M_SETCAPTION,nl$
					
					Exit
				EndIf
			Next
	
			For lbi.listboxItem=Each ListBoxItem
				If Lower$(lbi\Caption$)=l$ Then
					FUI_SendMessage Handle(lbi),M_SETCAPTION,nl$
					
					Exit
				EndIf
			Next
	
			For r.Radio=Each Radio
				If Lower$(r\Caption$)=l$ Then
					FUI_SendMessage Handle(r),M_SETCAPTION,nl$
					
					Exit
				EndIf
			Next
	
			For s.Spinner=Each Spinner
				If Lower$(s\Append$)=l$ Then
					s\Append$=nl$
					
					Exit
				EndIf
			Next
	
			For tb.TextBox=Each TextBox
				If Lower$(tb\Caption$)=l$ Then
					FUI_SendMessage Handle(tb),M_SETCAPTION,nl$
					
					Exit
				EndIf
			Next
	
			For n.node=Each Node
				If Lower$(n\Caption$)=l$ Then
					FUI_SendMessage Handle(n),M_SETCAPTION,nl$
					
					Exit
				EndIf
			Next
	
			For v.view=Each View
				If Lower$(v\Caption$)=l$ Then
					FUI_SendMessage Handle(v),M_SETCAPTION,nl$
					
					Exit
				EndIf
			Next
	
			For cmi.ContextMenuItem=Each ContextMenuItem
				If Lower$(cmi\Caption$)=l$ Then
					FUI_SendMessage Handle(cmi),M_SETCAPTION,nl$
					
					Exit
				EndIf
			Next
	
			For tt.tooltip=Each ToolTip
				If Lower$(tt\Caption$)=l$ Then
					tt\Caption$=nl$
					Exit
				EndIf
			Next
		EndIf
	Next
	
End Function

Function FUI_UpdateLanguage( ID )
	If app\NewLangs$ = app\BaseLangs$ Then Return
	
	SeekFile app\NewLang, 0
	SeekFile app\BaseLang, 0

	num$		= ReadLine( app\NewLang )
    n2$		= ReadLine( app\BaseLang )
	
	Caption$ = FUI_SendMessage( ID, M_GETTEXT )
	For A = 1 To Int num
		nl$ = ReadLine( app\NewLang )
		If nl$=""
			ReadLine app\BaseLang
		Else
			If Lower( Caption$ ) = Lower( ReadLine( app\BaseLang ) )
				FUI_SendMessage ID, M_SETCAPTION, nl$
				Exit
			EndIf
		EndIf
	Next
End Function

; Returns true if A is before B alphabetically
Function FUI_HighAlphabetical(A$, B$)

	If Len(A$) = 0 Then Return True
	If Len(B$) = 0 Then Return False

	A$ = Lower$(A$)
	B$ = Lower$(B$)

	Length = Len(B$)
	If Len(A$) > Length Then Length = Len(A$)

	For i = 1 To Length
		If i <= Len(A$) Then CharA$ = Mid$(A$, i, 1) Else CharA$ = " "
		If i <= Len(B$) Then CharB$ = Mid$(B$, i, 1) Else CharB$ = " "
		If Asc(CharA$) < Asc(CharB$) Then Return True
		If Asc(CharA$) > Asc(CharB$) Then Return False
	Next

End Function

Function FUI_CloseComboBoxes()

	app\actComboBox = Null
	For gad.ComboBox = Each ComboBox
		If gad\State = 1
			gad\State = 0
			FUI_SetTexture gad\Mesh, 0
			HideEntity gad\DropDown
		EndIf
	Next
End Function

;#End Region

;#Region ---------- Data -----------------------
.FUI_MOUSE_ARROW
Data -16777216,-7077888,-7274496,-7274496,-6946816,-6750208,-6684672,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -16777216,-16777216,-8060928,-8060928,-7602176,-7077888,-6750208,-6684672,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -16777216,-1,-16777216,-9043968,-8519680,-7667712,-7077888,-6750208,-6684672,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -16777216,-1,-1,-16777216,-9568256,-8585216,-7667712,-7077888,-6750208,-6684672,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -16777216,-1,-1,-1,-16777216,-9699328,-8585216,-7667712,-7077888,-6750208,-6684672,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -16777216,-1,-1,-1,-1,-16777216,-9699328,-8585216,-7667712,-7077888,-6750208,-6684672,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -16777216,-1,-1,-1,-1,-1,-16777216,-9699328,-8585216,-7667712,-7077888,-6750208,-6684672,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -16777216,-1,-1,-1,-1,-1,-1,-16777216,-9699328,-8585216,-7667712,-7077888,-6750208,-6684672,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -16777216,-1,-1,-1,-1,-1,-1,-1,-16777216,-9699328,-8585216,-7667712,-7077888,-6750208,-6684672,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -16777216,-1,-1,-1,-1,-1,-1,-1,-1,-16777216,-9568256,-8519680,-7536640,-6946816,-6684672,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -16777216,-1,-1,-1,-1,-1,-16777216,-16777216,-16777216,-16777216,-16777216,-9109504,-8060928,-7208960,-6750208,-6684672,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -16777216,-1,-1,-16777216,-1,-1,-16777216,-11468800,-11141120,-10551296,-9830400,-8978432,-8060928,-7274496,-6815744,-6684672,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -16777216,-1,-16777216,-10682368,-16777216,-1,-1,-16777216,-10878976,-10092544,-9109504,-8323072,-7602176,-7077888,-6750208,-6684672,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -16777216,-16777216,-9437184,-10092544,-16777216,-1,-1,-16777216,-10813440,-9895936,-8716288,-7667712,-7143424,-6815744,-6684672,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -16777216,-7995392,-8912896,-9175040,-8912896,-16777216,-1,-1,-16777216,-10158080,-8912896,-7733248,-7012352,-6750208,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -6946816,-7536640,-8060928,-8060928,-7864320,-16777216,-1,-1,-16777216,-10551296,-9502720,-8192000,-7208960,-6750208,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -6815744,-7077888,-7274496,-7274496,-7143424,-7340032,-16777216,-1,-1,-16777216,-10027008,-8716288,-7536640,-6881280,-6684672,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -6684672,-6750208,-6881280,-6815744,-6750208,-6946816,-16777216,-1,-1,-16777216,-10223616,-9109504,-7798784,-6946816,-6684672,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -6619136,-6684672,-6684672,-6684672,-6684672,-6750208,-7208960,-16777216,-16777216,-10027008,-9895936,-8912896,-7798784,-6946816,-6684672,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -6619136,-6619136,-6619136,-6619136,-6619136,-6684672,-6881280,-7471104,-8323072,-8912896,-8912896,-8192000,-7405568,-6881280,-6684672,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6750208,-6946816,-7405568,-7798784,-7733248,-7405568,-6946816,-6750208,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6684672,-6750208,-6881280,-6946816,-6946816,-6881280,-6750208,-6684672,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136
Data -6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6684672,-6684672,-6684672,-6684672,-6684672,-6684672,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136,-6619136

.FUI_ICON_NEW
Data -65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536
Data -65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536
Data -65536,-65536,-65536,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-65536,-65536,-65536,-65536,-65536
Data -65536,-65536,-65536,-16777216,-1,-1,-1,-1,-1,-16777216,-2039584,-16777216,-65536,-65536,-65536,-65536
Data -65536,-65536,-65536,-16777216,-1,-1,-1513240,-1513240,-1513240,-16777216,-2039584,-2039584,-16777216,-65536,-65536,-65536
Data -65536,-65536,-65536,-16777216,-1,-1513240,-1513240,-1513240,-1513240,-16777216,-16777216,-16777216,-16777216,-65536,-65536,-65536
Data -65536,-65536,-65536,-16777216,-1,-1513240,-1513240,-2039584,-2039584,-2039584,-2039584,-2039584,-16777216,-65536,-65536,-65536
Data -65536,-65536,-65536,-16777216,-1,-1513240,-2039584,-2039584,-2039584,-2039584,-2039584,-2039584,-16777216,-65536,-65536,-65536
Data -65536,-65536,-65536,-16777216,-1,-1513240,-2039584,-2039584,-2039584,-2039584,-2039584,-2039584,-16777216,-65536,-65536,-65536
Data -65536,-65536,-65536,-16777216,-1,-1513240,-2039584,-2039584,-2039584,-2039584,-2039584,-2039584,-16777216,-65536,-65536,-65536
Data -65536,-65536,-65536,-16777216,-1,-1513240,-2039584,-2039584,-2039584,-2039584,-2039584,-2039584,-16777216,-65536,-65536,-65536
Data -65536,-65536,-65536,-16777216,-1,-1513240,-2039584,-2039584,-2039584,-2039584,-2039584,-2039584,-16777216,-65536,-65536,-65536
Data -65536,-65536,-65536,-16777216,-1,-1513240,-2039584,-2039584,-2039584,-2039584,-2039584,-2039584,-16777216,-65536,-65536,-65536
Data -65536,-65536,-65536,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-65536,-65536,-65536
Data -65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536
Data -65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536

.FUI_ICON_OPEN
Data -65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536
Data -65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536
Data -65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536
Data -65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536
Data -65536,-16777216,-16777216,-16777216,-16777216,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536
Data -65536,-16777216,-5658873,-5658873,-5658873,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-65536,-65536,-65536,-65536
Data -65536,-16777216,-5658873,-5658873,-5658873,-5658873,-5658873,-5658873,-5658873,-5658873,-5658873,-16777216,-65536,-65536,-65536,-65536
Data -65536,-16777216,-7040498,-5658873,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-65536
Data -65536,-16777216,-7040498,-7040498,-16777216,-1974513,-1974513,-1974513,-1974513,-1974513,-1974513,-1974513,-1974513,-1974513,-16777216,-65536
Data -65536,-16777216,-7040498,-16777216,-1974513,-1974513,-3355648,-3355648,-3355648,-3355648,-3355648,-3355648,-3355648,-16777216,-65536,-65536
Data -65536,-16777216,-7040498,-16777216,-1974513,-3355648,-3355648,-3355648,-3355648,-3355648,-3355648,-3355648,-3355648,-16777216,-65536,-65536
Data -65536,-16777216,-16777216,-1974513,-3355648,-3355648,-3355648,-3355648,-3355648,-3355648,-3355648,-3355648,-16777216,-65536,-65536,-65536
Data -65536,-16777216,-16777216,-3355648,-3355648,-3355648,-3355648,-3355648,-3355648,-3355648,-3355648,-3355648,-16777216,-65536,-65536,-65536
Data -65536,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-65536,-65536,-65536,-65536
Data -65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536
Data -65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536

.FUI_ICON_SAVE
Data -65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536
Data -65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536
Data -65536,-65536,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-65536,-65536
Data -65536,-65536,-16777216,-12368499,-16777216,-1,-1,-1,-1,-1,-1,-16777216,-12828732,-16777216,-65536,-65536
Data -65536,-65536,-16777216,-12368499,-16777216,-1,-1,-1,-1,-1,-1,-16777216,-12828732,-16777216,-65536,-65536
Data -65536,-65536,-16777216,-12368499,-16777216,-1,-1,-1,-1,-1,-1,-16777216,-12828732,-16777216,-65536,-65536
Data -65536,-65536,-16777216,-12368499,-16777216,-1,-1,-1,-1,-1,-1,-16777216,-12828732,-16777216,-65536,-65536
Data -65536,-65536,-16777216,-12368499,-12368499,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-12828732,-12828732,-16777216,-65536,-65536
Data -65536,-65536,-16777216,-12368499,-12368499,-12631647,-12631647,-12828732,-12828732,-12828732,-12828732,-12828732,-12828732,-16777216,-65536,-65536
Data -65536,-65536,-16777216,-12368499,-12368499,-12631647,-12631647,-12631647,-12828732,-12828732,-12828732,-12828732,-12828732,-16777216,-65536,-65536
Data -65536,-65536,-16777216,-12368499,-12368499,-12368499,-12631647,-12631647,-12631647,-12631647,-12631647,-12631647,-12631647,-16777216,-65536,-65536
Data -65536,-65536,-16777216,-12368499,-12368499,-12368499,-12368499,-12368499,-12368499,-12368499,-12368499,-12368499,-12368499,-16777216,-65536,-65536
Data -65536,-65536,-16777216,-12368499,-12368499,-12368499,-12368499,-12368499,-12368499,-12368499,-12368499,-12368499,-12368499,-16777216,-65536,-65536
Data -65536,-65536,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-65536,-65536
Data -65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536
Data -65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536,-65536
;#End Region