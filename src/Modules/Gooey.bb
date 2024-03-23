; Misc ------------------------------------------------------------------------------------------------------------------------------

Const GY_Path$ = "Data\UI"  ; Change this if you move the Gooey folder
Const GY_ComboChar$ = "+"   ; The character displayed in the "open" button of a combo box
Include "Modules\Gooey_3D_Text.bb"

; Globals ---------------------------------------------------------------------------------------------------------------------------

Global GY_ButtonBorderSize# = 0.05
Global GY_BorderSize# = 0.05

; Camera
Global GY_Cam
Global GY_CamPX#, GY_CamPY#, GY_CamPZ#
Global GY_CamRX#, GY_CamRY#, GY_CamRZ#
Global GY_CamSX# = 1.0, GY_CamSY# = 1.0, GY_CamSZ# = 1.0
Global GY_CamAtZero

; Allow background windows to be activated?
Global GY_Modal = False

; Fonts
Global GY_TitleFont

; Mouse mesh
Global GY_Mouse
Global GY_MouseTex ; default texture

; Input
Global GY_EnterHit, GY_BackSpaceHit, GY_DeleteHit, GY_HomeHit, GY_EndHit
Global GY_UpHit, GY_DownHit, GY_LeftHit, GY_RightHit
Global GY_LeftClick, GY_RightClick
Global GY_LeftWasDown, GY_RightWasDown

Global GY_LeftTimer, GY_RightTimer, GY_BackTimer

Global GY_MouseX#, GY_MouseY#
Global GY_MouseXSpeed, GY_MouseYSpeed

Global GY_HeldGadget.GY_Gadget, GY_HeldData
Global GY_ActiveTextField.GY_TextField

Global GY_MouseOverGadget

; Colours
Global GY_ReadR, GY_ReadG, GY_ReadB
Global GY_TitleR, GY_TitleG, GY_TitleB
Global GY_BorderR, GY_BorderG, GY_BorderB
Global GY_InactiveBorderR, GY_InactiveBorderG, GY_InactiveBorderB
Global GY_ButtonUpR, GY_ButtonUpG, GY_ButtonUpB
Global GY_ButtonDownR, GY_ButtonDownG, GY_ButtonDownB
Global GY_ButtonHeldR, GY_ButtonHeldG, GY_ButtonHeldB
Global GY_ComboBackR, GY_ComboBackG, GY_ComboBackB
Global GY_ScrollR, GY_ScrollG, GY_ScrollB
Global GY_TextFieldForeR, GY_TextFieldForeG, GY_TextFieldForeB
Global GY_TextFieldBackR, GY_TextFieldBackG, GY_TextFieldBackB

; Textures
Global GY_Window, GY_Title, GY_InactiveTitle, GY_Close, GY_Minimise, GY_CloseHeld, GY_MinimiseHeld
Global GY_CheckBoxOn, GY_CheckBoxOff, GY_CheckBoxDwn
Global GY_ProgBarBackground, GY_ProgBarForeground
Global GY_ProgBarBackground2, GY_ProgBarForeground2
Global GY_ProgBarForeground3
Global GY_ButtonGradient
Global GY_BorderTop, GY_BorderBottom, GY_BorderRight, GY_BorderLeft
Global GY_BorderTL, GY_BorderTR, GY_BorderBR, GY_BorderBL
Global GY_BorderTopD, GY_BorderBottomD, GY_BorderRightD, GY_BorderLeftD
Global GY_BorderTLD, GY_BorderTRD, GY_BorderBRD, GY_BorderBLD
Global GY_BorderTopP, GY_BorderBottomP, GY_BorderRightP, GY_BorderLeftP
Global GY_BorderTLP, GY_BorderTRP, GY_BorderBRP, GY_BorderBLP
Global GY_Scroller


; Sounds
Global GY_SClick, GY_SBeep

; Constants -------------------------------------------------------------------------------------------------------------------------

Const Justify_Left   = 0
Const Justify_Right  = 1
Const Justify_Centre = 2

; Types -----------------------------------------------------------------------------------------------------------------------------

; Generic gadget - all other gadgets "inherit" from this via Object()/Handle()
Type GY_Gadget
	Field AbsolutePosition, AbsoluteSize
	Field X#, Y#, Width#, Height#
	Field TypeHandle
	Field Parent.GY_Gadget
	Field EN
	Field Caption$
	Field Alpha#
	Field ZPosition
	Field Locked
	Field OnBottom
	Field UserData$
End Type

Type GY_Label
	Field Gadget.GY_Gadget
	Field Justify
	Field OldWidth#
End Type

Type GY_Window
	Field TitleEN, CloseEN, MinEN, TopEN, LeftEN, LowerEN, RightEN
	Field TitleText
	Field UserTexture, AutoFreeUserTexture
	Field Closed, Minimised
	Field Gadget.GY_Gadget
End Type

;Interaction window cysis145
Type GY_Window2
	Field TitleEN, CloseEN, MinEN, TopEN, LeftEN, LowerEN, RightEN
	Field TitleText
	Field UserTexture, AutoFreeUserTexture
	Field Closed, Minimised
	Field Gadget.GY_Gadget
End Type

Type GY_CheckBox
	Field LabelText
	Field State, Hits
	Field Gadget.GY_Gadget
End Type

Type GY_ProgressBar
	Field BarEN
	Field Value, Max
	Field Gadget.GY_Gadget
End Type

Type GY_Slider
	Field LabelEN, BarEN
	Field Label$
	Field Value#, Min#, Max#
	Field IsFloat
	Field Gadget.GY_Gadget
End Type

Type GY_Button
	Field LabelEN
	Field TL, TR, BL, BR, T, B, R, L ; Border entities
	Field Toggle
	Field State
	Field Clicked, RightClicked
	Field ComboOwner
	Field Gadget.GY_Gadget
	Field UserTexture
End Type

Type GY_CustomButton
	Field UpTex, DownTex, HoverTex
	Field Toggle
	Field State
	Field Clicked, RightClicked
	Field Gadget.GY_Gadget
End Type

Type GY_ComboBox
	Field OpenStatus, FirstItem, TotalItems
	Field LabelEN, ListAreaEN
	Field TopLeftEN, LowerEN, RightEN
	Field ButtonGadget, ScrollGadget
	Field Selected.GY_ComboItem
	Field NoScrollbar, ScrollValue#
	Field MaxLength
	Field R, G, B
	Field Gadget.GY_Gadget
End Type

Type GY_ComboItem
	Field Dat$
	Field LabelEN
	Field Box.GY_ComboBox
	Field X#, Y#, Width#, Visible
End Type

Type GY_ScrollBar
	Field BarEN
	Field DecButton, IncButton
	Field Value#, Interval#
	Field Vertical
	Field ComboOwner
	Field Gadget.GY_Gadget
End Type

Type GY_TextField
	Field TextEN, CursorEN
	Field TopLeftEN, LowerEN, RightEN
	Field FirstChar, CursorChar
	Field MaxLen, Dat$
	Field Masked
	Field AllowedInput
	Field Gadget.GY_Gadget
	Field Hit
End Type

Type GY_ListBox
	Field FirstItem, TotalItems
	Field BorderEN
	Field ScrollGadget
	Field Selected.GY_ListItem
	Field ScrollValue#
	Field MaxLength
	Field Gadget.GY_Gadget
End Type

Type GY_ListItem
	Field Dat$, ExtraData$
	Field LabelEN
	Field R, G, B
	Field Box.GY_ListBox
End Type

; Functions -------------------------------------------------------------------------------------------------------------------------

; Removes all present gadgets
Function GY_ClearGadgets()
	For gadget.GY_Gadget = Each GY_Gadget
		GY_FreeGadget(Handle(gadget))
	Next
End Function



; Displays an OK only messagebox
Function GY_MessageBox(Title$, Message$)

	WasModal = GY_Modal
	GY_Modal = True
	W = GY_CreateWindow(Title$, 0.31, 0.4, 0.38, 0.12, True, True, False, LoadTexture("Data\UI\window.bmp"))
	GY_CreateLabel(W, 0.5, 0.32, Message$, 255, 255, 255, Justify_Centre)
	BOK = GY_CreateCustomButton(W, 0.35, 0.6, 0.25, 0.3, LoadButtonU("Okay"), LoadButtonD("Okay"), LoadButtonH("Okay"))

	Repeat
		If KeyHit(1) Or GY_WindowClosed(W) Or GY_ButtonHit(BOK) Then Exit
		GY_Update()
		UpdateWorld()
		RenderWorld()
		Flip(VSync)
	Forever

	GY_FreeGadget(W)
	GY_Modal = WasModal
	FlushKeys()

End Function

; Displays a YES/NO messagebox
Function GY_RequestBox(Title$, Message$)

	WasModal = GY_Modal
	GY_Modal = True
	W = GY_CreateWindow(Title$, 0.31, 0.4, 0.38, 0.12, True, True, False, LoadTexture("Data\UI\window.bmp"))
	GY_CreateLabel(W, 0.5, 0.32, Message$, 255, 255, 255, Justify_Centre)
	BYes = GY_CreateCustomButton(W, 0.55, 0.6, 0.25, 0.3, LoadButtonU("Yes"), LoadButtonD("Yes"), LoadButtonH("Yes"))
	BNo = GY_CreateCustomButton(W, 0.2, 0.6, 0.25, 0.3, LoadButtonU("No"), LoadButtonD("No"), LoadButtonH("No"))
	Result = False

	Repeat
		If KeyHit(1) Or GY_WindowClosed(W) Or GY_ButtonHit(BNo)
			Exit
		EndIf
		If GY_ButtonHit(BYes)
			Result = True
			Exit
		EndIf
		GY_Update()
		UpdateWorld()
		RenderWorld()
		Flip(VSync)
	Forever

	GY_FreeGadget(W)
	GY_Modal = WasModal
	FlushKeys()
	Return Result

End Function

; Creates a listbox gadget
Function GY_CreateListBox(Parent, X#, Y#, Width#, Height#, NoScrollbar = False)

	GY_ZeroCamera()

	; Listbox
	L.GY_ListBox = New GY_ListBox

	; Gadget
	G.GY_Gadget = New GY_Gadget
	G\TypeHandle = Handle(L)
	L\Gadget = G
	G\Alpha# = 1.0
	G\Parent = Object.GY_Gadget(Parent)

	; Main entity (box background)
	G\EN = GY_CreateQuad(GY_Cam)
	;EntityTexture(G\EN, GY_Window2)
	EntityColor(G\EN, GY_ComboBackR, GY_ComboBackG, GY_ComboBackB)
	EntityFX(G\EN, 1 + 8)
	GY_PositionGadget(Handle(G), X#, Y#)
	GY_ScaleGadget(Handle(G), Width#, Height#, False)

	; Scrollbar
	If NoScrollbar = False
		GY_ScaleGadget(Handle(G), GY_GadgetWidth#(Handle(G), True) - 0.035, GY_GadgetHeight#(Handle(G), True), True)
		ScrollW# = 0.02 / GY_GadgetWidth#(Parent, True) ;0.035
		ScrollX# = (X# + Width#) - ScrollW# + (GY_BorderSize# / GY_GadgetWidth#(Parent, True))
		ScrollH# = Height# - (GY_BorderSize# / GY_GadgetHeight#(Parent, True))
		ScrollW# = ScrollW# - (GY_BorderSize# / GY_GadgetWidth#(Parent, True))
		L\ScrollGadget = GY_CreateScrollBar(Parent, ScrollX#, Y#, ScrollW#, ScrollH#, 0.1)
	EndIf

	; Switch sizes to global
	If G\Parent <> Null
		Width# = Width# * GY_GadgetWidth#(Handle(G\Parent), True)
		Height# = Height# * GY_GadgetHeight#(Handle(G\Parent), True)
		X# = GY_GadgetX#(Handle(G), True)
		Y# = GY_GadgetY#(Handle(G), True)
	EndIf

	; Border
	L\BorderEN = GY_CreateQuad(GY_Cam)
	If NoScrollbar = False
		ScaleMesh(L\BorderEN, (Width# - (0.035 - (GY_BorderSize# * 2.0))) * 20.0, (Height# + (GY_BorderSize# * 2.0)) * 15.0, 1.0)
	Else
		ScaleMesh(L\BorderEN, (Width# + (GY_BorderSize# * 2.0)) * 20.0, (Height# + (GY_BorderSize# * 2.0)) * 15.0, 1.0)
	EndIf
	PositionEntity(L\BorderEN, ((X# - GY_BorderSize#) * 20.0) - 10.0, ((Y# - GY_BorderSize#) * -15.0) + 7.5, 10.0)
	EntityColor(L\BorderEN, 255, 255, 255)
	EntityParent(L\BorderEN, G\EN)
	EntityFX(L\BorderEN, 1 + 8)

	; Calculate maximum text characters
	If NoScrollbar = False
		L\MaxLength = Floor#((Width# - 0.035) * 100.0)
	Else
		L\MaxLength = Floor#(Width# * 100.0)
	EndIf

	; Z position
	If G\Parent <> Null
		G\ZPosition = G\Parent\ZPosition - 1
	Else
		G\ZPosition = -3000
	EndIf
	EntityOrder(G\EN, G\ZPosition - 1)
	EntityOrder(L\BorderEN, G\ZPosition)

	GY_RestoreCamera()

	Return Handle(G)

End Function

; Adds an item to a listbox
Function GY_AddListBoxItem(GHandle, Label$, cR = 255, cG = 255, cB = 255, Dat$ = "")

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	L.GY_ListBox = Object.GY_ListBox(G\TypeHandle)
	If L = Null Then Return False

	B.GY_ListItem = New GY_ListItem
	B\R = cR
	B\G = cG
	B\B = cB
	B\Box = L
	B\Dat$ = Label$
	B\ExtraData$ = Dat$
	Width# = 0.013 * Float#(L\MaxLength)
	B\LabelEN = GY_Create3DText(0.0, 0.0, Width#, 0.02, L\MaxLength, GY_TitleFont, GY_Cam)
	EntityColor B\LabelEN, cR, cG, cB
	L\TotalItems = L\TotalItems + 1
	GY_Set3DText(B\LabelEN, Label$)

	; Update scroll bar interval
	If L\ScrollGadget <> 0
		MaxItems = Floor#(GY_GadgetHeight#(GHandle, True) / 0.035)
		If MaxItems < L\TotalItems
			GY_SetScrollBarInterval(L\ScrollGadget, 1.0 / Float#((L\TotalItems - MaxItems) + 1))
		Else
			GY_SetScrollBarInterval(L\ScrollGadget, 0.9)
		EndIf
		GY_UpdateScrollBar(L\ScrollGadget, 0.0)
	EndIf
	GY_UpdateListBox(GHandle, "")

	Return True

End Function

; Retrieves the selected item from a listbox
Function GY_ListBoxItem$(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	L.GY_ListBox = Object.GY_ListBox(G\TypeHandle)
	If L = Null Then Return False

	If L\Selected <> Null Then Return L\Selected\Dat$ Else Return ""

End Function

; Retrieves the selected item's extra data from a listbox
Function GY_ListBoxItemData$(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	L.GY_ListBox = Object.GY_ListBox(G\TypeHandle)
	If L = Null Then Return False

	If L\Selected <> Null Then Return L\Selected\ExtraData$ Else Return ""

End Function

; Sets the selected item for a listbox
Function GY_UpdateListBox(GHandle, Item$)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	L.GY_ListBox = Object.GY_ListBox(G\TypeHandle)
	If L = Null Then Return False

	MaxItems = Floor#(GY_GadgetHeight#(GHandle, True) / 0.035)
	If L\ScrollGadget <> 0
		L\ScrollValue# = GY_ScrollBarValue#(L\ScrollGadget)
	Else
		L\FirstItem = 0
		L\ScrollValue# = 0.0
	EndIf

	; Position and show/hide all items
	Count = -1
	YPos# = GY_GadgetY#(GHandle, True)
	For B.GY_ListItem = Each GY_ListItem
		If B\Box = L
			Count = Count + 1

			; Selected item
			If B\Dat$ = Item$
				L\Selected = B
				EntityColor(B\LabelEN, 255 - B\R, 255 - B\G, 255 - B\B)
			; Not the selected item
			Else
				EntityColor(B\LabelEN, B\R, B\G, B\B)
			EndIf

			; Visible
			If Count >= L\FirstItem And Count <= (L\FirstItem + MaxItems) - 1
				ShowEntity(B\LabelEN)
				EntityParent(B\LabelEN, GY_Cam)
				PositionEntity(B\LabelEN, (GY_GadgetX#(GHandle, True) * 20.0) - 10.0, (YPos# * -15.0) + 7.5, 10.0)
				EntityParent(B\LabelEN, G\EN)
				EntityOrder(B\LabelEN, G\ZPosition - 2)
				YPos# = YPos# + 0.035
			; Not visible
			Else
				HideEntity(B\LabelEN)
			EndIf
		EndIf
	Next

	Return True

End Function

;CHAT ENTRY
; Creates a text field gadget (AllowedInput should be 0 for all, 1 for letters only, 2 for integer, 3 for float)
Function GY_CreateTextField(Parent, X#, Y#, Width#, AllowedInput, MaxLength, InitialText$ = "", Masked = False)

	GY_ZeroCamera()

	; Text field
	T.GY_TextField = New GY_TextField
	T\AllowedInput = AllowedInput
	T\MaxLen = MaxLength
	T\Masked = Masked

	; Gadget
	G.GY_Gadget = New GY_Gadget
	G\TypeHandle = Handle(T)
	T\Gadget = G
	G\Alpha# = 1.0
	G\Parent = Object.GY_Gadget(Parent)

	; Main entity (background)
	G\EN = GY_CreateQuad(GY_Cam)
	EntityColor(G\EN, GY_TextFieldBackR, GY_TextFieldBackG, GY_TextFieldBackB)
	EntityFX(G\EN, 1 + 8)
	EntityAlpha(G\EN, 0.0)
	GY_PositionGadget(Handle(G), X#, Y#)
	GY_ScaleGadget(Handle(G), Width#, 1.0)
	GY_ScaleGadget(Handle(G), GY_GadgetWidth#(Handle(G), True), 0.03, True)

	; Switch sizes to global
	If G\Parent <> Null
		Width# = Width# * GY_GadgetWidth#(Handle(G\Parent), True)
		X# = GY_GadgetX#(Handle(G), 1)
		Y# = GY_GadgetY#(Handle(G), 1)
	EndIf

	; Cursor
	T\CursorEN = GY_CreateQuad(GY_Cam)
	ScaleMesh(T\CursorEN, 0.003 * 20.0, 0.028 * 15.0, 1.0)
	PositionMesh(T\CursorEN, -0.025, -0.025, 0.0)
	PositionEntity(T\CursorEN, (X# * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
	EntityParent(T\CursorEN, G\EN)
	EntityColor(T\CursorEN, 180, 90, 10)
	EntityFX(T\CursorEN, 1 + 8)
	HideEntity(T\CursorEN)

	; Top and left borders
	T\TopLeftEN = GY_CreateQuad(GY_Cam)
	ScaleMesh(T\TopLeftEN, (Width# + (GY_BorderSize# * 2.0)) * 20.0, GY_BorderSize# * 15.0, 1.0)
	PositionEntity(T\TopLeftEN, ((X# - GY_BorderSize#) * 20.0) - 10.0, ((Y# - GY_BorderSize#) * -15.0) + 7.5, 10.0)
	EN = GY_CreateQuad(GY_Cam)
	ScaleMesh(EN, GY_BorderSize# * 20.0, (0.03 + GY_BorderSize#) * 15.0, 1.0)
	AddMesh(EN, T\TopLeftEN) : FreeEntity EN
	EntityColor(T\TopLeftEN, 255, 255, 255)
	EntityParent(T\TopLeftEN, G\EN)
	EntityFX(T\TopLeftEN, 1 + 8)

	; Bottom border
	T\LowerEN = GY_CreateQuad(GY_Cam)
	ScaleMesh(T\LowerEN, (Width# + (GY_BorderSize# * 2.0)) * 20.0, GY_BorderSize# * 15.0, 1.0)
	PositionEntity(T\LowerEN, ((X# - GY_BorderSize#) * 20.0) - 10.0, ((Y# + 0.03) * -15.0) + 7.5, 10.0)
	EntityColor(T\LowerEN, 255, 255, 255)
	EntityParent(T\LowerEN, G\EN)
	EntityFX(T\LowerEN, 1 + 8)

	; Right border
	T\RightEN = GY_CreateQuad(GY_Cam)
	ScaleMesh(T\RightEN, GY_BorderSize# * 20.0, 0.03 * 15.0, 1.0)
	PositionEntity(T\RightEN, ((X# + Width#) * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
	EntityColor(T\RightEN, 255, 255, 255)
	EntityParent(T\RightEN, G\EN)
	EntityFX(T\RightEN, 1 + 8)

	; Text fields (login stuff etc.)
	T\TextEN = GY_Create3DText(X#, Y#, 0.013 * T\MaxLen, 0.02, T\MaxLen, GY_TitleFont, GY_Cam)
	;T\TextEN = GY_Create3DText(X#, Y#, 0.013 * T\MaxLen, 0.02, T\MaxLen, GY_TitleFont, GY_Cam)
	EntityColor(T\TextEN, GY_TextFieldForeR, GY_TextFieldForeG, GY_TextFieldForeB)
	EntityParent(T\TextEN, G\EN)

	; Z position
	If G\Parent <> Null
		G\ZPosition = G\Parent\ZPosition - 1
	Else
		G\ZPosition = -3000
	EndIf
	EntityOrder(G\EN, G\ZPosition)
	EntityOrder(T\TextEN, G\ZPosition - 1)
	EntityOrder(T\LowerEN, G\ZPosition - 1)
	EntityOrder(T\TopLeftEN, G\ZPosition - 1)
	EntityOrder(T\RightEN, G\ZPosition - 1)
	EntityOrder(T\CursorEN, G\ZPosition - 2)

	; Initial text
	GY_UpdateTextField(Handle(G), InitialText$)

	GY_RestoreCamera()

	Return Handle(G)

End Function

; Creates a text field gadget (AllowedInput should be 0 for all, 1 for letters only, 2 for integer, 3 for float)
; Duplicate code with small tweaks for better character creation Cysis145
Function GY_CreateTextFieldC(Parent, X#, Y#, Width#, AllowedInput, MaxLength, InitialText$ = "", Masked = False)

	GY_ZeroCamera()

	; Text field
	T.GY_TextField = New GY_TextField
	T\AllowedInput = AllowedInput
	T\MaxLen = MaxLength
	T\Masked = Masked

	; Gadget
	G.GY_Gadget = New GY_Gadget
	G\TypeHandle = Handle(T)
	T\Gadget = G
	G\Alpha# = 1.0
	G\Parent = Object.GY_Gadget(Parent)

	; Main entity (background)
	G\EN = GY_CreateQuad(GY_Cam)
	EntityColor(G\EN, GY_TextFieldBackR, GY_TextFieldBackG, GY_TextFieldBackB)
	EntityFX(G\EN, 1 + 8)
	EntityAlpha(G\EN, 0.0)
	GY_PositionGadget(Handle(G), X#, Y#)
	GY_ScaleGadget(Handle(G), Width#, 1.0)
	GY_ScaleGadget(Handle(G), GY_GadgetWidth#(Handle(G), True), 0.03, True)

	; Switch sizes to global
	If G\Parent <> Null
		Width# = Width# * GY_GadgetWidth#(Handle(G\Parent), True)
		X# = GY_GadgetX#(Handle(G), 1)
		Y# = GY_GadgetY#(Handle(G), 1)
	EndIf

	; Cursor
	T\CursorEN = GY_CreateQuad(GY_Cam)
	ScaleMesh(T\CursorEN, 0.003 * 20.0, 0.028 * 15.0, 1.0)
	PositionMesh(T\CursorEN, -0.025, -0.025, 0.0)
	PositionEntity(T\CursorEN, (X# * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
	EntityParent(T\CursorEN, G\EN)
	EntityColor(T\CursorEN, 180, 90, 10)
	EntityFX(T\CursorEN, 1 + 8)
	HideEntity(T\CursorEN)

	; Top and left borders
	T\TopLeftEN = GY_CreateQuad(GY_Cam)
	ScaleMesh(T\TopLeftEN, (Width# + (GY_BorderSize# * 2.0)) * 20.0, GY_BorderSize# * 15.0, 1.0)
	PositionEntity(T\TopLeftEN, ((X# - GY_BorderSize#) * 20.0) - 10.0, ((Y# - GY_BorderSize#) * -15.0) + 7.5, 10.0)
	EN = GY_CreateQuad(GY_Cam)
	ScaleMesh(EN, GY_BorderSize# * 20.0, (0.03 + GY_BorderSize#) * 15.0, 1.0)
	AddMesh(EN, T\TopLeftEN) : FreeEntity EN
	EntityColor(T\TopLeftEN, 255, 255, 255)
	EntityParent(T\TopLeftEN, G\EN)
	EntityFX(T\TopLeftEN, 1 + 8)

	; Bottom border
	T\LowerEN = GY_CreateQuad(GY_Cam)
	ScaleMesh(T\LowerEN, (Width# + (GY_BorderSize# * 2.0)) * 20.0, GY_BorderSize# * 15.0, 1.0)
	PositionEntity(T\LowerEN, ((X# - GY_BorderSize#) * 20.0) - 10.0, ((Y# + 0.03) * -15.0) + 7.5, 10.0)
	EntityColor(T\LowerEN, 255, 255, 255)
	EntityParent(T\LowerEN, G\EN)
	EntityFX(T\LowerEN, 1 + 8)

	; Right border
	T\RightEN = GY_CreateQuad(GY_Cam)
	ScaleMesh(T\RightEN, GY_BorderSize# * 20.0, 0.03 * 15.0, 1.0)
	PositionEntity(T\RightEN, ((X# + Width#) * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
	EntityColor(T\RightEN, 255, 255, 255)
	EntityParent(T\RightEN, G\EN)
	EntityFX(T\RightEN, 1 + 8)

	; Text fields (login stuff etc.)
	T\TextEN = GY_Create3DText(X#, Y#, 0.013 * T\MaxLen, 0.02, T\MaxLen, GY_TitleFont, GY_Cam)
	;T\TextEN = GY_Create3DText(X#, Y#, 0.013 * T\MaxLen, 0.02, T\MaxLen, GY_TitleFont, GY_Cam)
	EntityColor(T\TextEN, GY_TextFieldForeR, GY_TextFieldForeG, GY_TextFieldForeB)
	EntityParent(T\TextEN, G\EN)

	; Z position
	If G\Parent <> Null
		G\ZPosition = G\Parent\ZPosition - 1
	Else
		G\ZPosition = -3000
	EndIf
	EntityOrder(G\EN, G\ZPosition)
	EntityOrder(T\TextEN, G\ZPosition - 1)
	EntityOrder(T\LowerEN, G\ZPosition - 1)
	EntityOrder(T\TopLeftEN, G\ZPosition - 1)
	EntityOrder(T\RightEN, G\ZPosition - 1)
	EntityOrder(T\CursorEN, G\ZPosition - 2)

	; Initial text
	GY_UpdateTextField(Handle(G), InitialText$)

	GY_RestoreCamera()

	Return Handle(G)

End Function


; Updates the text in a text field
Function GY_UpdateTextField(GHandle, Dat$)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	T.GY_TextField = Object.GY_TextField(G\TypeHandle)
	If T = Null Then Return False

	If Len(Dat$) > T\MaxLen Then Dat$ = Left$(Dat$, T\MaxLen) ;Left$

	T\Dat$ = Dat$
	If T\CursorChar > Len(Dat$) Then T\CursorChar = Len(Dat$)
	If T\Masked = True Then Dat$ = String$("*", Len(Dat$))

	; If text won't fit
	T\FirstChar = 0
	While GY_TextWidth#(T\TextEN, Dat$) > GY_GadgetWidth#(GHandle, True)
		If T\FirstChar = T\CursorChar Then Exit
		T\FirstChar = T\FirstChar + 1
		Dat$ = Mid$(Dat$, 2)
	Wend

	; If text still won't fit
	While GY_TextWidth#(T\TextEN, Dat$) > GY_GadgetWidth#(GHandle, True)
		Dat$ = Left$(Dat$, Len(Dat$) - 1)
	Wend

	; Set text
	GY_Set3DText(T\TextEN, Dat$)

	Return True

End Function

; Retrieves the text in a text field
Function GY_TextFieldText$(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return ""

	T.GY_TextField = Object.GY_TextField(G\TypeHandle)
	If T = Null Then Return ""

	Return T\Dat$

End Function

; Returns whether a text field has focus or not
Function GY_TextFieldActive(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	T.GY_TextField = Object.GY_TextField(G\TypeHandle)
	If T = GY_ActiveTextField Then Return True Else Return False

End Function

; Activates a text field
Function GY_ActivateTextField(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	T.GY_TextField = Object.GY_TextField(G\TypeHandle)
	If T = Null Then Return False

	If GY_ActiveTextField <> Null Then HideEntity(GY_ActiveTextField\CursorEN)
	GY_HeldGadget = Null
	GY_ActiveTextField = T
	ShowEntity(T\CursorEN)
	T\CursorChar = Len(T\Dat$)
	GY_UpdateTextField(GHandle, T\Dat$)

	Return True

End Function

; Returns whether a text field has had enter pressed in it
Function GY_TextFieldHit(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	T.GY_TextField = Object.GY_TextField(G\TypeHandle)
	If T = Null Then Return False

	Hit = T\Hit
	T\Hit = 0
	Return Hit

End Function

; Creates a scrollbar gadget
Function GY_CreateScrollBar(Parent, X#, Y#, Width#, Height#, Interval# = 0.1)

	GY_ZeroCamera()

	; Scroll bar
	S.GY_ScrollBar = New GY_ScrollBar

	; Gadget
	G.GY_Gadget = New GY_Gadget
	G\TypeHandle = Handle(S)
	S\Gadget = G
	G\X# = X# : G\Y# = Y#
	G\Width# = Width#
	G\Height# = Height#
	G\Alpha# = 1.0
	G\Parent = Object.GY_Gadget(Parent)

	If Height# * GY_GadgetHeight#(Handle(G\Parent), True) > Width# * GY_GadgetWidth#(Handle(G\Parent), True) Then S\Vertical = True

	; Main entity (box background)
	G\EN = GY_CreateQuad(GY_Cam)
	EntityColor(G\EN, GY_ScrollR, GY_ScrollG, GY_ScrollB)
	EntityFX(G\EN, 1 + 8)
	;Added Alpha Setting to create a merged Bar Cysis145
	EntityAlpha(G\EN, 0.3)

	; Indicator Re-built by Cysis145 Uses new texture and looks heck of a lot better.
	S\BarEN = GY_CreateQuad(G\EN)
	If S\Vertical = True ;False
		ScaleMesh(S\BarEN, 1.0, 1.0, 1.0) : PositionMesh(S\BarEN, 0.0, 0.0, 0.0)
	Else
		ScaleMesh(S\BarEN, 1.0, 1.0, 1.0) : PositionMesh(S\BarEN, -0.2, 0.0, 0.0)
	EndIf
	EntityTexture(S\BarEN, GY_Scroller) ;GY_ProgBarForeground
	EntityColor(S\BarEN, 255, 255, 255)
	EntityFX(S\BarEN, 1 + 8)

	GY_PositionGadget(Handle(G), X#, Y#)
	If S\Vertical = False
		GY_PositionGadget(Handle(G), GY_GadgetX#(Handle(G), True) + 0.035, GY_GadgetY#(Handle(G), True), True)
	Else
		GY_PositionGadget(Handle(G), GY_GadgetX#(Handle(G), True), GY_GadgetY#(Handle(G), True) + 0.035, True)
	EndIf
	GY_ScaleGadget(Handle(G), Width#, Height#)
	If S\Vertical = False
		GY_ScaleGadget(Handle(G), GY_GadgetWidth#(Handle(G), True) - 0.07, GY_GadgetHeight#(Handle(G), True), True)
	Else
		GY_ScaleGadget(Handle(G), GY_GadgetWidth#(Handle(G), True), GY_GadgetHeight#(Handle(G), True) - 0.07, True)
	EndIf

	; Buttons
	If G\Parent <> Null
		If S\Vertical = False
			ButtonW# = 0.035 / GY_GadgetWidth#(Parent, True) : ButtonH# = Height#
		Else
			ButtonH# = 0.035 / GY_GadgetHeight#(Parent, True) : ButtonW# = Width#
		EndIf
	Else
		If S\Vertical = False
			ButtonW# = 0.035 : ButtonH# = Height#
		Else
			ButtonH# = 0.035 : ButtonW# = Width#
		EndIf
	EndIf

	If S\Vertical = False
		ButtonX# = (X# + Width#) - ButtonW# : ButtonY# = Y#
	Else
		ButtonX# = X# : ButtonY# = (Y# + Height#) - ButtonH#
	EndIf
	
	;Changed to custom Button Cysis145
	S\DecButton = GY_CreateCustomButton(Parent, X#, Y#, ButtonW#, ButtonH#, LoadButtonU("SmallUp"), LoadButtonD("SmallUp"), LoadButtonH("SmallUp"), False)
	S\IncButton = GY_CreateCustomButton(Parent, ButtonX#, ButtonY#, ButtonW#, ButtonH#, LoadButtonU("SmallDown"), LoadButtonD("SmallDown"), LoadButtonH("SmallDown"), False)
	;S\DecButton = GY_CreateButton(Parent, X#, Y#, ButtonW#, ButtonH#, "-", False, 255, 255, 255)
	;S\IncButton = GY_CreateButton(Parent, ButtonX#, ButtonY#, ButtonW#, ButtonH#, "+", False, 255, 255, 255)

	; Z position
	If G\Parent <> Null
		G\ZPosition = G\Parent\ZPosition - 1
	Else
		G\ZPosition = -3000
	EndIf
	EntityOrder(G\EN, G\ZPosition)
	EntityOrder(S\BarEN, G\ZPosition - 1)

	; Initial value
	GY_SetScrollBarInterval(Handle(G), Interval#)
	GY_UpdateScrollBar(Handle(G), 0.0)

	GY_RestoreCamera()

	Return Handle(G)

End Function

; Sets the interval of a scrollbar
Function GY_SetScrollBarInterval(GHandle, Amount#)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	S.GY_ScrollBar = Object.GY_ScrollBar(G\TypeHandle)
	If S = Null Then Return False

	; Scale the bar
	If S\Vertical = False
		ScaleEntity S\BarEN, Amount#, 1.0, 1.0
	Else
		ScaleEntity S\BarEN, 1.0, Amount#, 1.0
	EndIf

	; Set interval
	S\Interval# = Amount#

	Return True

End Function

; Retrieves the interval of a scrollbar
Function GY_GetScrollBarInterval#(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return 0.0

	S.GY_ScrollBar = Object.GY_ScrollBar(G\TypeHandle)
	If S = Null Then Return 0.0

	Return S\Interval#

End Function

; Sets the value of a scrollbar
Function GY_UpdateScrollBar(GHandle, Value#)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	S.GY_ScrollBar = Object.GY_ScrollBar(G\TypeHandle)
	If S = Null Then Return False

	If Value# < 0.0
		Value# = 0.0
	ElseIf Value# > 1.0 - S\Interval#
		Value# = 1.0 - S\Interval#
	EndIf
	S\Value# = Value#

	If S\Vertical = False
		PositionEntity(S\BarEN, (Value# * 0.98) + 0.01, EntityY#(S\BarEN), 0.0)
	Else
		PositionEntity(S\BarEN, EntityX#(S\BarEN), (Value# * -0.98) - 0.01, 0.0)
	EndIf

	Return True

End Function

; Retrieves the value of a scrollbar
Function GY_ScrollBarValue#(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return -1.0

	S.GY_ScrollBar = Object.GY_ScrollBar(G\TypeHandle)
	If S = Null Then Return -1.0

	Return S\Value#

End Function

; Creates a combo box gadget
Function GY_CreateComboBox(Parent, X#, Y#, Width#, Height#, Label$, cR = 255, cG = 255, cB = 255, MaxItemLength = 50, NoScrollbar = False)

	GY_ZeroCamera()

	; Combo box
	S.GY_ComboBox = New GY_ComboBox
	S\R = cR
	S\G = cG
	S\B = cB
	S\NoScrollbar = NoScrollbar

	; Gadget
	G.GY_Gadget = New GY_Gadget
	G\TypeHandle = Handle(S)
	S\Gadget = G
	G\Width# = Width#
	G\Alpha# = 1.0
	G\Parent = Object.GY_Gadget(Parent)

	; Main entity (box background)
	G\EN = GY_CreateQuad(GY_Cam)
	EntityColor(G\EN, GY_ComboBackR, GY_ComboBackG, GY_ComboBackB)
	EntityFX(G\EN, 1 + 8)
	GY_PositionGadget(Handle(G), X#, Y#)
	GY_ScaleGadget(Handle(G), Width#, 1.0, False)
	GY_ScaleGadget(Handle(G), GY_GadgetWidth#(Handle(G), True) - 0.02, 0.03, True)

	; Fix height
	G\Height# = Height#

	; Button
	If G\Parent <> Null
		ButtonW# = 0.02 / GY_GadgetWidth#(Parent, True) : ButtonH# = 0.03 / GY_GadgetHeight#(Parent, True)
	Else
		ButtonW# = 0.02 : ButtonH# = 0.03
	EndIf
	S\ButtonGadget = GY_CreateButton(Parent, (X# + Width#) - ButtonW#, Y#, ButtonW#, ButtonH#, GY_ComboChar$, True, 255, 255, 255)

	; Scrollbar
	If NoScrollbar = False
		ButtonBorder# = (0.01 / GY_GadgetHeight#(Parent, True))
		ScrollX# = (X# + Width#) - ButtonW#
		ScrollY# = Y# + ButtonH# + ButtonBorder#
		S\ScrollGadget = GY_CreateScrollBar(Parent, ScrollX#, ScrollY#, ButtonW#, Height# - (ButtonH# + ButtonBorder#), 0.01)
		GY_GadgetAlpha(S\ScrollGadget, 0.0)
		Gad.GY_Gadget = Object.GY_Gadget(S\ScrollGadget)
		Scroll.GY_ScrollBar = Object.GY_ScrollBar(Gad\TypeHandle)
		Scroll\ComboOwner = True
		Gad = Object.GY_Gadget(Scroll\IncButton)
		Button.GY_Button = Object.GY_Button(Gad\TypeHandle)
		Button\ComboOwner = True
		Gad = Object.GY_Gadget(Scroll\DecButton)
		Button.GY_Button = Object.GY_Button(Gad\TypeHandle)
		Button\ComboOwner = True
		G\Width# = G\Width# - 0.02
	EndIf

	; Switch sizes to global
	If G\Parent <> Null
		Width# = Width# * GY_GadgetWidth#(Handle(G\Parent), True)
		Height# = Height# * GY_GadgetHeight#(Handle(G\Parent), True)
		X# = GY_GadgetX#(Handle(G), True)
		Y# = GY_GadgetY#(Handle(G), True)
	EndIf

	; Drop down list background
	S\ListAreaEN = GY_CreateQuad(GY_Cam)
	ScaleMesh(S\ListAreaEN, (Width# - 0.02) * 20.0, (Height# - 0.03) * 15.0, 1.0)
	PositionEntity(S\ListAreaEN, ((X# - 0.005) * 20.0) - 10.0, ((Y# + 0.035) * -15.0) + 7.5, 10.0)
	EntityColor(S\ListAreaEN, GY_ComboBackR, GY_ComboBackG, GY_ComboBackB)
	EntityParent(S\ListAreaEN, G\EN)
	EntityFX(S\ListAreaEN, 1 + 8)
	HideEntity(S\ListAreaEN)

	; Top and left borders
	S\TopLeftEN = GY_CreateQuad(GY_Cam)
	ScaleMesh(S\TopLeftEN, (Width# - 0.02) * 20.0, GY_BorderSize# * 15.0, 1.0)
	PositionEntity(S\TopLeftEN, ((X# - GY_BorderSize#) * 20.0) - 10.0, ((Y# - GY_BorderSize#) * -15.0) + 7.5, 10.0)
	EN = GY_CreateQuad(GY_Cam)
	ScaleMesh(EN, GY_BorderSize# * 20.0, (0.03 + GY_BorderSize#) * 15.0, 1.0)
	AddMesh(EN, S\TopLeftEN) : FreeEntity EN
	EntityColor(S\TopLeftEN, 255, 255, 255)
	EntityParent(S\TopLeftEN, G\EN)
	EntityFX(S\TopLeftEN, 1 + 8)

	; Bottom border
	S\LowerEN = GY_CreateQuad(GY_Cam)
	ScaleMesh(S\LowerEN, (Width# - 0.02) * 20.0, GY_BorderSize# * 15.0, 1.0)
	PositionEntity(S\LowerEN, ((X# - GY_BorderSize#) * 20.0) - 10.0, ((Y# + 0.03) * -15.0) + 7.5, 10.0)
	EntityColor(S\LowerEN, 255, 255, 255)
	EntityParent(S\LowerEN, G\EN)
	EntityFX(S\LowerEN, 1 + 8)

	; Right border
	S\RightEN = GY_CreateQuad(GY_Cam)
	ScaleMesh(S\RightEN, GY_BorderSize# * 20.0, 0.035 * 15.0, 1.0)
	PositionEntity(S\RightEN, (((X# + Width#) - (0.02 + (GY_BorderSize# * 2.0))) * 20.0) - 10.0, ((Y# - GY_BorderSize#) * -15.0) + 7.5, 10.0)
	EntityColor(S\RightEN, 255, 255, 255)
	EntityParent(S\RightEN, G\EN)
	EntityFX(S\RightEN, 1 + 8)

	; Label
	S\MaxLength = MaxItemLength
	S\LabelEN = GY_Create3DText(X#, Y#, 0.013 * Float#(S\MaxLength), 0.02, S\MaxLength, GY_TitleFont, GY_Cam)
	GY_Set3DText(S\LabelEN, Label$)
	EntityParent(S\LabelEN, G\EN)
	EntityColor(S\LabelEN, cR, cG, cB)

	; Z position
	If G\Parent <> Null
		G\ZPosition = G\Parent\ZPosition - 1
	Else
		G\ZPosition = -3000
	EndIf
	EntityOrder(G\EN, G\ZPosition)
	EntityOrder(S\ListAreaEN, G\ZPosition - 2)
	EntityOrder(S\LabelEN, G\ZPosition - 1)
	EntityOrder(S\LowerEN, G\ZPosition - 1)
	EntityOrder(S\TopLeftEN, G\ZPosition - 1)
	EntityOrder(S\RightEN, G\ZPosition - 1)

	GY_RestoreCamera()

	Return Handle(G)

End Function

; Creates a combo box gadget without the scroll Bar + Custom button cysis145 
Function GY_CreateComboBoxS(Parent, X#, Y#, Width#, Height#, Label$, cR = 255, cG = 255, cB = 255, MaxItemLength = 50, NoScrollbar = True)

	GY_ZeroCamera()

	; Combo box
	S.GY_ComboBox = New GY_ComboBox
	S\R = cR
	S\G = cG
	S\B = cB
	S\NoScrollbar = NoScrollbar

	; Gadget
	G.GY_Gadget = New GY_Gadget
	G\TypeHandle = Handle(S)
	S\Gadget = G
	G\Width# = Width#
	G\Alpha# = 1.0
	G\Parent = Object.GY_Gadget(Parent)

	; Main entity (box background)
	G\EN = GY_CreateQuad(GY_Cam)
	EntityColor(G\EN, GY_ComboBackR, GY_ComboBackG, GY_ComboBackB)
	EntityFX(G\EN, 1 + 8)
	EntityAlpha(G\EN, 0.0)
	GY_PositionGadget(Handle(G), X#, Y#)
	GY_ScaleGadget(Handle(G), Width#, 1.0, False)
	GY_ScaleGadget(Handle(G), GY_GadgetWidth#(Handle(G), True) - 0.02, 0.03, True)

	; Fix height
	G\Height# = Height#

	; Button
	If G\Parent <> Null
		ButtonW# = 0.02 / GY_GadgetWidth#(Parent, True) : ButtonH# = 0.03 / GY_GadgetHeight#(Parent, True)
	Else
		ButtonW# = 0.02 : ButtonH# = 0.03
	EndIf
	
	S\ButtonGadget = GY_CreateCustomButton(Parent, (X# + Width#) - ButtonW#, Y#, ButtonW#, ButtonH#,LoadButtonU("SmallDown"), LoadButtonD("SmallUp"), LoadButtonH("SmallLeft"), True)

	; Scrollbar
	If NoScrollbar = False
		ButtonBorder# = (0.01 / GY_GadgetHeight#(Parent, True))
		ScrollX# = (X# + Width#) - ButtonW#
		ScrollY# = Y# + ButtonH# + ButtonBorder#
		S\ScrollGadget = GY_CreateScrollBar(Parent, ScrollX#, ScrollY#, ButtonW#, Height# - (ButtonH# + ButtonBorder#), 0.01)
		GY_GadgetAlpha(S\ScrollGadget, 0.0)
		Gad.GY_Gadget = Object.GY_Gadget(S\ScrollGadget)
		Scroll.GY_ScrollBar = Object.GY_ScrollBar(Gad\TypeHandle)
		Scroll\ComboOwner = True
		Gad = Object.GY_Gadget(Scroll\IncButton)
		Button.GY_Button = Object.GY_Button(Gad\TypeHandle)
		Button\ComboOwner = True
		Gad = Object.GY_Gadget(Scroll\DecButton)
		Button.GY_Button = Object.GY_Button(Gad\TypeHandle)
		Button\ComboOwner = True
		G\Width# = G\Width# - 0.02
	EndIf

	; Switch sizes to global
	If G\Parent <> Null
		Width# = Width# * GY_GadgetWidth#(Handle(G\Parent), True)
		Height# = Height# * GY_GadgetHeight#(Handle(G\Parent), True)
		X# = GY_GadgetX#(Handle(G), True)
		Y# = GY_GadgetY#(Handle(G), True)
	EndIf

	; Drop down list background
	S\ListAreaEN = GY_CreateQuad(GY_Cam)
	ScaleMesh(S\ListAreaEN, (Width# - 0.02) * 20.0, (Height# - 0.03) * 15.0, 1.0)
	PositionEntity(S\ListAreaEN, ((X# - 0.005) * 20.0) - 10.0, ((Y# + 0.035) * -15.0) + 7.5, 10.0)
	EntityColor(S\ListAreaEN, GY_ComboBackR, GY_ComboBackG, GY_ComboBackB)
	EntityParent(S\ListAreaEN, G\EN)
	EntityFX(S\ListAreaEN, 1 + 8)
	HideEntity(S\ListAreaEN)

	; Top and left borders
	S\TopLeftEN = GY_CreateQuad(GY_Cam)
	ScaleMesh(S\TopLeftEN, (Width# - 0.02) * 20.0, GY_BorderSize# * 15.0, 1.0)
	PositionEntity(S\TopLeftEN, ((X# - GY_BorderSize#) * 20.0) - 10.0, ((Y# - GY_BorderSize#) * -15.0) + 7.5, 10.0)
	EN = GY_CreateQuad(GY_Cam)
	ScaleMesh(EN, GY_BorderSize# * 20.0, (0.03 + GY_BorderSize#) * 15.0, 1.0)
	AddMesh(EN, S\TopLeftEN) : FreeEntity EN
	EntityColor(S\TopLeftEN, 255, 255, 255)
	EntityParent(S\TopLeftEN, G\EN)
	EntityFX(S\TopLeftEN, 1 + 8)

	; Bottom border
	S\LowerEN = GY_CreateQuad(GY_Cam)
	ScaleMesh(S\LowerEN, (Width# - 0.02) * 20.0, GY_BorderSize# * 15.0, 1.0)
	PositionEntity(S\LowerEN, ((X# - GY_BorderSize#) * 20.0) - 10.0, ((Y# + 0.03) * -15.0) + 7.5, 10.0)
	EntityColor(S\LowerEN, 255, 255, 255)
	EntityParent(S\LowerEN, G\EN)
	EntityFX(S\LowerEN, 1 + 8)

	; Right border
	S\RightEN = GY_CreateQuad(GY_Cam)
	ScaleMesh(S\RightEN, GY_BorderSize# * 20.0, 0.035 * 15.0, 1.0)
	PositionEntity(S\RightEN, (((X# + Width#) - (0.02 + (GY_BorderSize# * 2.0))) * 20.0) - 10.0, ((Y# - GY_BorderSize#) * -15.0) + 7.5, 10.0)
	EntityColor(S\RightEN, 255, 255, 255)
	EntityParent(S\RightEN, G\EN)
	EntityFX(S\RightEN, 1 + 8)

	; Label
	S\MaxLength = MaxItemLength
	S\LabelEN = GY_Create3DText(X#, Y#, 0.013 * Float#(S\MaxLength), 0.02, S\MaxLength, GY_TitleFont, GY_Cam) 
	GY_Set3DText(S\LabelEN, Label$)
	EntityParent(S\LabelEN, G\EN)
	EntityColor(S\LabelEN, cR, cG, cB)

	; Z position
	If G\Parent <> Null
		G\ZPosition = G\Parent\ZPosition - 1
	Else
		G\ZPosition = -3000
	EndIf
	EntityOrder(G\EN, G\ZPosition)
	EntityOrder(S\ListAreaEN, G\ZPosition - 2)
	EntityOrder(S\LabelEN, G\ZPosition - 1)
	EntityOrder(S\LowerEN, G\ZPosition - 1)
	EntityOrder(S\TopLeftEN, G\ZPosition - 1)
	EntityOrder(S\RightEN, G\ZPosition - 1)

	GY_RestoreCamera()

	Return Handle(G)

End Function

; Adds a new combo box item
Function GY_AddComboBoxItem(GHandle, Label$)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	C.GY_ComboBox = Object.GY_ComboBox(G\TypeHandle)
	If C = Null Then Return False

	B.GY_ComboItem = New GY_ComboItem
	B\Box = C
	B\Dat$ = Label$
	B\Width# = GY_GadgetWidth#(GHandle, True)
	B\LabelEN = GY_Create3DText(0.0, 0.0, 0.013 * C\MaxLength, 0.02, C\MaxLength, GY_TitleFont, GY_Cam)
	C\TotalItems = C\TotalItems + 1
	While GY_TextWidth#(B\LabelEN, Label$) > GY_GadgetWidth#(GHandle, True)
		Label$ = Left$(Label$, Len(Label$) - 1)
	Wend
	GY_Set3DText(B\LabelEN, Label$)
	EntityColor(B\LabelEN, C\R, C\G, C\B)
	HideEntity B\LabelEN

	Return True

End Function

; Finds the currently selected combo box item
Function GY_ComboBoxItem$(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return ""

	C.GY_ComboBox = Object.GY_ComboBox(G\TypeHandle)
	If C = Null Then Return ""

	If C\Selected <> Null Then Return C\Selected\Dat$ Else Return ""

End Function

; Returns True if a combo box is currently opened
Function GY_ComboBoxIsOpen(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	C.GY_ComboBox = Object.GY_ComboBox(G\TypeHandle)
	If C = Null Then Return False

	Return C\OpenStatus

End Function

; Sets the selected item for a combo box
Function GY_UpdateComboBox(GHandle, Item$)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	C.GY_ComboBox = Object.GY_ComboBox(G\TypeHandle)
	If C = Null Then Return False

	C\OpenStatus = False
	C\FirstItem = 0
	ShowEntity(C\LabelEN)
	GY_GadgetAlpha(C\ScrollGadget, 0.0)
	HideEntity(C\ListAreaEN)

	Found = False
	For I.GY_ComboItem = Each GY_ComboItem
		If I\Box = C
			I\Visible = False

			If I\Dat$ = Item$
				C\Selected = I
				EntityParent(I\LabelEN, GY_Cam)
				X# = GY_GadgetX#(Handle(G), True)
				Y# = GY_GadgetY#(Handle(G), True)
				PositionEntity(I\LabelEN, (X# * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
				EntityColor(I\LabelEN, I\Box\R, I\Box\G, I\Box\B)
				EntityParent(I\LabelEN, G\EN)
				EntityOrder(I\LabelEN, G\ZPosition - 2)
				ShowEntity(I\LabelEN)
				HideEntity(C\LabelEN)
				Found = True
			Else
				HideEntity(I\LabelEN)
			EndIf
		EndIf
	Next

	Return Found

End Function

; Creates a slider gadget
Function GY_CreateSlider(Parent, X#, Y#, Width#, Height#, Label$, Value#, Min#, Max#, IsFloat = False, cR = 255, cG = 255, cB = 255)

	GY_ZeroCamera()

	; Slider
	S.GY_Slider = New GY_Slider
	S\Min# = Min#
	S\Max# = Max#

	; Gadget
	G.GY_Gadget = New GY_Gadget
	G\TypeHandle = Handle(S)
	S\Gadget = G
	G\Width# = Width#
	G\Height# = Height#
	G\Alpha# = 1.0
	G\Parent = Object.GY_Gadget(Parent)

	; Main entity (background)
	G\EN = GY_CreateQuad(GY_Cam)
	EntityFX(G\EN, 1 + 8)
	EntityTexture(G\EN, GY_ProgBarBackground)

;Updated code to work with new Progess bar System
	; Bar entity 
	S\BarEN = GY_CreateQuad(G\EN)
	ScaleMesh(S\BarEN, 1.0, 0.5, 1.0)
	PositionMesh(S\BarEN, 0.0, -0.43, 0.0)
	EntityTexture(S\BarEN, GY_ProgBarForeground)
	EntityColor(S\BarEN, cR, cG, cB)
	EntityFX(S\BarEN, 1 + 8)
	

	GY_PositionGadget(Handle(G), X#, Y#)
	GY_ScaleGadget(Handle(G), Width#, Height#)

	; Switch sizes to global
	If G\Parent <> Null
		Width# = Width# * GY_GadgetWidth#(Handle(G\Parent), True)
		Height# = Height# * GY_GadgetHeight#(Handle(G\Parent), True)
		X# = GY_GadgetX#(Handle(G), 1)
		Y# = GY_GadgetY#(Handle(G), 1)
	EndIf

	; Text
	S\Label$ = Label$
	If IsFloat = True
		If Len(Str$(Max#)) > Len(Str$(Min#)) Then Label$ = Label$ + " (" + Max# + ")" Else Label$ = Label$ + " (" + Min# + ")"
	Else
		MaxI = Int(Max#)
		MinI = Int(Min#)
		If Len(Str$(MaxI)) > Len(Str$(MinI)) Then Label$ = Label$ + " (" + MaxI + ")" Else Label$ = Label$ + " (" + MinI + ")"
	EndIf
	LabelWidth# = 0.011 * Float#(Len(Label$)) ;0.017
	LabelHeight# = 0.018					  ;0.035
	Y# = 0.42 ;(Y# + (Height# / 2.0)) - (LabelHeight# / 2.0)
	S\LabelEN = GY_Create3DText(X#, Y#, LabelWidth#, LabelHeight#, Len(Label$), GY_TitleFont, GY_Cam)
	X# = (X# + (Width# / 2.0)) - (GY_TextWidth#(S\LabelEN, Label$) / 2.0)
	GY_Position3DText(S\LabelEN, X#, Y#)
	GY_Set3DText(S\LabelEN, Label$)
	EntityParent(S\LabelEN, G\EN)

	; Z position
	If G\Parent <> Null
		G\ZPosition = G\Parent\ZPosition - 1
	Else
		G\ZPosition = -3000
	EndIf
	EntityOrder(G\EN, G\ZPosition)
	EntityOrder(S\BarEN, G\ZPosition - 1)
	EntityOrder(S\LabelEN, G\ZPosition - 2)

	; Initial state
	GY_UpdateSlider(Handle(G), Value#)

	GY_RestoreCamera()

	Return Handle(G)

End Function

; Sets the value of a slider
Function GY_UpdateSlider(GHandle, Value#)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	S.GY_Slider = Object.GY_Slider(G\TypeHandle)
	If S = Null Then Return False

	If Value# > S\Max# Then Value# = S\Max#
	If Value# < S\Min# Then Value# = S\Min#


	If S\IsFloat = True
		S\Value# = Value#
		Label$ = S\Label$ + " (" + Str$(Value#) + ")"
	Else
		S\Value# = Int(Value#)
		Label$ = S\Label$ + " (" + Str$(Int(Value#)) + ")"
	EndIf

	GY_Set3DText(S\LabelEN, Label$)
	ScaleEntity(S\BarEN, (Value# - S\Min#) / (S\Max# - S\Min#), 1.0, 1.0)

	Return True

End Function

; Retrives the value of a slider
Function GY_GetSliderValue#(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return 0.0

	S.GY_Slider = Object.GY_Slider(G\TypeHandle)
	If S = Null Then Return 0.0

	Return S\Value#

End Function

; Creates a button gadget
Function GY_CreateButton(Parent, X#, Y#, Width#, Height#, Label$, Toggle = False, cR = 255, cG = 255, cB = 255, Texture = 0)

	GY_ZeroCamera()

	; Button
	B.GY_Button = New GY_Button
	B\Toggle = Toggle
	B\UserTexture = Texture

	; Gadget
	G.GY_Gadget = New GY_Gadget
	G\TypeHandle = Handle(B)
	B\Gadget = G
	G\Width# = Width#
	G\Height# = Height#
	G\Alpha# = 1.0
	G\Parent = Object.GY_Gadget(Parent)

	; Main entity (button)
	G\EN = GY_CreateQuad(GY_Cam)
	If B\UserTexture = 0
		EntityTexture(G\EN, GY_ButtonGradient)
		EntityColor(G\EN, GY_ButtonUpR, GY_ButtonUpG, GY_ButtonUpB)
	Else
		EntityTexture(G\EN, B\UserTexture)
	EndIf
	GY_PositionGadget(Handle(G), X#, Y#)
	GY_ScaleGadget(Handle(G), Width#, Height#)

	; Switch sizes to global
	If G\Parent <> Null
		Width# = Width# * GY_GadgetWidth#(Handle(G\Parent), True)
		Height# = Height# * GY_GadgetHeight#(Handle(G\Parent), True)
		X# = GY_GadgetX#(Handle(G), 1)
		Y# = GY_GadgetY#(Handle(G), 1)
	EndIf

	; Borders
	B\TL = GY_CreateQuad(GY_Cam)
	EntityTexture(B\TL, GY_BorderTL)
	ScaleMesh(B\TL, GY_ButtonBorderSize# * 20.0, GY_ButtonBorderSize# * 15.0, 1.0)
	PositionEntity(B\TL, ((X# - GY_ButtonBorderSize#) * 20.0) - 10.0, ((Y# - GY_ButtonBorderSize#) * -15.0) + 7.5, 10.0)
	EntityParent(B\TL, G\EN)
	B\T = GY_CreateQuad(GY_Cam)
	EntityTexture(B\T, GY_BorderTop)
	ScaleMesh(B\T, Width# * 20.0, GY_ButtonBorderSize# * 15.0, 1.0)
	PositionEntity(B\T, (X# * 20.0) - 10.0, ((Y# - GY_ButtonBorderSize#) * -15.0) + 7.5, 10.0)
	EntityParent(B\T, G\EN)
	B\TR = GY_CreateQuad(GY_Cam)
	EntityTexture(B\TR, GY_BorderTR)
	ScaleMesh(B\TR, GY_ButtonBorderSize# * 20.0, GY_ButtonBorderSize# * 15.0, 1.0)
	PositionEntity(B\TR, ((X# + Width#) * 20.0) - 10.0, ((Y# - GY_ButtonBorderSize#) * -15.0) + 7.5, 10.0)
	EntityParent(B\TR, G\EN)
	B\R = GY_CreateQuad(GY_Cam)
	EntityTexture(B\R, GY_BorderRight)
	ScaleMesh(B\R, GY_ButtonBorderSize# * 20.0, Height# * 15.0, 1.0)
	PositionEntity(B\R, ((X# + Width#) * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
	EntityParent(B\R, G\EN)
	B\BR = GY_CreateQuad(GY_Cam)
	EntityTexture(B\BR, GY_BorderBR)
	ScaleMesh(B\BR, GY_ButtonBorderSize# * 20.0, GY_ButtonBorderSize# * 15.0, 1.0)
	PositionEntity(B\BR, ((X# + Width#) * 20.0) - 10.0, ((Y# + Height#) * -15.0) + 7.5, 10.0)
	EntityParent(B\BR, G\EN)
	B\B = GY_CreateQuad(GY_Cam)
	EntityTexture(B\B, GY_BorderBottom)
	ScaleMesh(B\B, Width# * 20.0, GY_ButtonBorderSize# * 15.0, 1.0)
	PositionEntity(B\B, (X# * 20.0) - 10.0, ((Y# + Height#) * -15.0) + 7.5, 10.0)
	EntityParent(B\B, G\EN)
	B\BL = GY_CreateQuad(GY_Cam)
	EntityTexture(B\BL, GY_BorderBL)
	ScaleMesh(B\BL, GY_ButtonBorderSize# * 20.0, GY_ButtonBorderSize# * 15.0, 1.0)
	PositionEntity(B\BL, ((X# - GY_ButtonBorderSize#) * 20.0) - 10.0, ((Y# + Height#) * -15.0) + 7.5, 10.0)
	EntityParent(B\BL, G\EN)
	B\L = GY_CreateQuad(GY_Cam)
	EntityTexture(B\L, GY_BorderLeft)
	ScaleMesh(B\L, GY_ButtonBorderSize# * 20.0, Height# * 15.0, 1.0)
	PositionEntity(B\L, ((X# - GY_ButtonBorderSize#) * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
	EntityParent(B\L, G\EN)

	; Label entity
	Y# = (Y# + (Height# / 2.0)) - (0.015)
	B\LabelEN = GY_Create3DText(X#, Y#, 0.013 * Len(Label$), 0.02, Len(Label$), GY_TitleFont, GY_Cam)
	;G\EN = GY_Create3DText(0.0, 0.0, 0.013 * Len(Label$), 0.02, Len(Label$), GY_TitleFont, GY_Cam)
	X# = (X# + (Width# / 2.0)) - (GY_TextWidth#(B\LabelEN, Label$) / 2.0)
	GY_Position3DText(B\LabelEN, X#, Y#)
	GY_Set3DText(B\LabelEN, Label$)
	EntityParent(B\LabelEN, G\EN)
	EntityColor(B\LabelEN, cR, cG, cB)

	; Z position
	If G\Parent <> Null
		G\ZPosition = G\Parent\ZPosition - 1
	Else
		G\ZPosition = -3000
	EndIf
	EntityOrder(G\EN, G\ZPosition)
	EntityOrder(B\LabelEN, G\ZPosition - 1)
	EntityOrder(B\T, G\ZPosition - 1)
	EntityOrder(B\B, G\ZPosition - 1)
	EntityOrder(B\R, G\ZPosition - 1)
	EntityOrder(B\L, G\ZPosition - 1)
	EntityOrder(B\TR, G\ZPosition - 1)
	EntityOrder(B\TL, G\ZPosition - 1)
	EntityOrder(B\BR, G\ZPosition - 1)
	EntityOrder(B\BL, G\ZPosition - 1)

	GY_RestoreCamera()

	Return Handle(G)

End Function

; Creates a Inventory button gadget
Function GY_CreateInventoryButton(Parent, X#, Y#, Width#, Height#, Label$, Toggle = False, cR = 255, cG = 255, cB = 255, Texture = 0)

	GY_ZeroCamera()

	; Button
	B.GY_Button = New GY_Button
	B\Toggle = Toggle
	B\UserTexture = Texture

	; Gadget
	G.GY_Gadget = New GY_Gadget
	G\TypeHandle = Handle(B)
	B\Gadget = G
	G\Width# = Width#
	G\Height# = Height#
	G\Alpha# = 1.0
	G\Parent = Object.GY_Gadget(Parent)

	; Main entity (button)
	G\EN = GY_CreateQuad(GY_Cam)
	If B\UserTexture = 0
		EntityTexture(G\EN, GY_ButtonGradient)
		EntityColor(G\EN, GY_ButtonUpR, GY_ButtonUpG, GY_ButtonUpB)
	Else
		EntityTexture(G\EN, B\UserTexture)
	EndIf
	GY_PositionGadget(Handle(G), X#, Y#)
	GY_ScaleGadget(Handle(G), Width#, Height#)

	; Switch sizes to global
	If G\Parent <> Null
		Width# = Width# * GY_GadgetWidth#(Handle(G\Parent), True)
		Height# = Height# * GY_GadgetHeight#(Handle(G\Parent), True)
		X# = GY_GadgetX#(Handle(G), 1)
		Y# = GY_GadgetY#(Handle(G), 1)
	EndIf

	; Borders
	B\TL = GY_CreateQuad(GY_Cam)
	EntityTexture(B\TL, GY_BorderTL)
	ScaleMesh(B\TL, GY_ButtonBorderSize# * 20.0, GY_ButtonBorderSize# * 15.0, 1.0)
	PositionEntity(B\TL, ((X# - GY_ButtonBorderSize#) * 20.0) - 10.0, ((Y# - GY_ButtonBorderSize#) * -15.0) + 7.5, 10.0)
	EntityParent(B\TL, G\EN)
	B\T = GY_CreateQuad(GY_Cam)
	EntityTexture(B\T, GY_BorderTop)
	ScaleMesh(B\T, Width# * 20.0, GY_ButtonBorderSize# * 15.0, 1.0)
	PositionEntity(B\T, (X# * 20.0) - 10.0, ((Y# - GY_ButtonBorderSize#) * -15.0) + 7.5, 10.0)
	EntityParent(B\T, G\EN)
	B\TR = GY_CreateQuad(GY_Cam)
	EntityTexture(B\TR, GY_BorderTR)
	ScaleMesh(B\TR, GY_ButtonBorderSize# * 20.0, GY_ButtonBorderSize# * 15.0, 1.0)
	PositionEntity(B\TR, ((X# + Width#) * 20.0) - 10.0, ((Y# - GY_ButtonBorderSize#) * -15.0) + 7.5, 10.0)
	EntityParent(B\TR, G\EN)
	B\R = GY_CreateQuad(GY_Cam)
	EntityTexture(B\R, GY_BorderRight)
	ScaleMesh(B\R, GY_ButtonBorderSize# * 20.0, Height# * 15.0, 1.0)
	PositionEntity(B\R, ((X# + Width#) * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
	EntityParent(B\R, G\EN)
	B\BR = GY_CreateQuad(GY_Cam)
	EntityTexture(B\BR, GY_BorderBR)
	ScaleMesh(B\BR, GY_ButtonBorderSize# * 20.0, GY_ButtonBorderSize# * 15.0, 1.0)
	PositionEntity(B\BR, ((X# + Width#) * 20.0) - 10.0, ((Y# + Height#) * -15.0) + 7.5, 10.0)
	EntityParent(B\BR, G\EN)
	B\B = GY_CreateQuad(GY_Cam)
	EntityTexture(B\B, GY_BorderBottom)
	ScaleMesh(B\B, Width# * 20.0, GY_ButtonBorderSize# * 15.0, 1.0)
	PositionEntity(B\B, (X# * 20.0) - 10.0, ((Y# + Height#) * -15.0) + 7.5, 10.0)
	EntityParent(B\B, G\EN)
	B\BL = GY_CreateQuad(GY_Cam)
	EntityTexture(B\BL, GY_BorderBL)
	ScaleMesh(B\BL, GY_ButtonBorderSize# * 20.0, GY_ButtonBorderSize# * 15.0, 1.0)
	PositionEntity(B\BL, ((X# - GY_ButtonBorderSize#) * 20.0) - 10.0, ((Y# + Height#) * -15.0) + 7.5, 10.0)
	EntityParent(B\BL, G\EN)
	B\L = GY_CreateQuad(GY_Cam)
	EntityTexture(B\L, GY_BorderLeft)
	ScaleMesh(B\L, GY_ButtonBorderSize# * 20.0, Height# * 15.0, 1.0)
	PositionEntity(B\L, ((X# - GY_ButtonBorderSize#) * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
	EntityParent(B\L, G\EN)

	; Label entity
	Y# = (Y# + (Height# / 2.0)) - (0.015)
	B\LabelEN = GY_Create3DText(X#, Y#, 0.013 * Len(Label$), 0.02, Len(Label$), GY_TitleFont, GY_Cam)
	;G\EN = GY_Create3DText(0.0, 0.0, 0.013 * Len(Label$), 0.02, Len(Label$), GY_TitleFont, GY_Cam)
	X# = (X# + (Width# / 2.0)) - (GY_TextWidth#(B\LabelEN, Label$) / 2.0)
	GY_Position3DText(B\LabelEN, X#, Y#)
	GY_Set3DText(B\LabelEN, Label$)
	EntityParent(B\LabelEN, G\EN)
	EntityColor(B\LabelEN, cR, cG, cB)

	; Z position
	If G\Parent <> Null
		G\ZPosition = G\Parent\ZPosition - 1
	Else
		G\ZPosition = -3000
	EndIf
	EntityOrder(G\EN, G\ZPosition)
	EntityOrder(B\LabelEN, G\ZPosition - 1)
	EntityOrder(B\T, G\ZPosition - 1)
	EntityOrder(B\B, G\ZPosition - 1)
	EntityOrder(B\R, G\ZPosition - 1)
	EntityOrder(B\L, G\ZPosition - 1)
	EntityOrder(B\TR, G\ZPosition - 1)
	EntityOrder(B\TL, G\ZPosition - 1)
	EntityOrder(B\BR, G\ZPosition - 1)
	EntityOrder(B\BL, G\ZPosition - 1)

	GY_RestoreCamera()

	Return Handle(G)

End Function


; Creates a custom button gadget
Function GY_CreateCustomButton(Parent, X#, Y#, Width#, Height#, UpTex, DownTex, HoverTex, Toggle = False)

	GY_ZeroCamera()

	; Button
	B.GY_CustomButton = New GY_CustomButton
	B\Toggle = Toggle
	B\UpTex = UpTex
	B\DownTex = DownTex
	B\HoverTex = HoverTex

	; Gadget
	G.GY_Gadget = New GY_Gadget
	G\TypeHandle = Handle(B)
	B\Gadget = G
	G\Width# = Width#
	G\Height# = Height#
	G\Alpha# = 1.0
	G\Parent = Object.GY_Gadget(Parent)

	; Main entity (button)
	G\EN = GY_CreateQuad(GY_Cam)
	EntityTexture(G\EN, B\UpTex)
	GY_PositionGadget(Handle(G), X#, Y#)
	GY_ScaleGadget(Handle(G), Width#, Height#)

	; Switch sizes to global
	If G\Parent <> Null
		Width# = Width# * GY_GadgetWidth#(Handle(G\Parent), True)
		Height# = Height# * GY_GadgetHeight#(Handle(G\Parent), True)
		X# = GY_GadgetX#(Handle(G), 1)
		Y# = GY_GadgetY#(Handle(G), 1)
	EndIf

	; Z position
	If G\Parent <> Null
		G\ZPosition = G\Parent\ZPosition - 1
	Else
		G\ZPosition = -3000
	EndIf
	EntityOrder(G\EN, G\ZPosition)

	GY_RestoreCamera()

	Return Handle(G)

End Function

; Changes the text of a button
Function GY_SetButtonLabel(GHandle, Label$, cR = 255, cG = 255, cB = 255, Corner = False)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	B.GY_Button = Object.GY_Button(G\TypeHandle)
	If B = Null Then Return False

	FreeEntity(B\LabelEN)

	X# = GY_GadgetX#(GHandle, True)
	Y# = GY_GadgetY#(GHandle, True)
	Width# = GY_GadgetWidth#(GHandle, True)
	Height# = GY_GadgetHeight#(GHandle, True)
	MaxLen = 3 ;Floor#(Width# / 0.017)
	If Len(Label$) > MaxLen Then Label$ = Left$(Label$, MaxLen)
	If Corner = True ;False
		;X# = (X# + Width#) - (0.1 * Len(Label$))
		;Y# = (Y# + Height#) - (0.8)
		X# = (X# + (Width# / 1.5)) - (0.0165) ;(Width# / 1.5)) - (0.0085 * Len(Label$))
		Y# = (Y# + (Height# / 2.0)) + (0.007)

	; Nasty little hack to place label in lower right corner
	Else
		X# = (X# + Width#) - (0.017 * Len(Label$))
		Y# = (Y# + Height#) - (0.8)
	EndIf
	B\LabelEN = GY_Create3DText(X#, Y#, 0.009 * Len(Label$), 0.015, Len(Label$), GY_TitleFont, GY_Cam)
	GY_Set3DText(B\LabelEN, Label$)
	EntityParent(B\LabelEN, G\EN)
	EntityColor(B\LabelEN, cR, cG, cB)
	EntityOrder(B\LabelEN, G\ZPosition - 1)

End Function

; Sets the state of a toggle button
Function GY_SetButtonState(GHandle, State)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	B.GY_Button = Object.GY_Button(G\TypeHandle)
	CB.GY_CustomButton = Object.GY_CustomButton(G\TypeHandle)

	; Normal button
	If B <> Null
		If B\Toggle = True
			B\State = State
			If B\State = True
				EntityTexture(B\T, GY_BorderTopD)
				EntityTexture(B\B, GY_BorderBottomD)
				EntityTexture(B\R, GY_BorderRightD)
				EntityTexture(B\L, GY_BorderLeftD)
				EntityTexture(B\TR, GY_BorderTRD)
				EntityTexture(B\TL, GY_BorderTLD)
				EntityTexture(B\BR, GY_BorderBRD)
				EntityTexture(B\BL, GY_BorderBLD)
				If B\UserTexture = 0
					EntityColor(G\EN, GY_ButtonDownR, GY_ButtonDownG, GY_ButtonDownB)
				Else
					EntityColor(G\EN, 200, 200, 200)
				EndIf
			Else
				EntityTexture(B\T, GY_BorderTop)
				EntityTexture(B\B, GY_BorderBottom)
				EntityTexture(B\R, GY_BorderRight)
				EntityTexture(B\L, GY_BorderLeft)
				EntityTexture(B\TR, GY_BorderTR)
				EntityTexture(B\TL, GY_BorderTL)
				EntityTexture(B\BR, GY_BorderBR)
				EntityTexture(B\BL, GY_BorderBL)
				If B\UserTexture = 0
					EntityColor(G\EN, GY_ButtonUpR, GY_ButtonUpG, GY_ButtonUpB)
				Else
					EntityColor(G\EN, 255, 255, 255)
				EndIf
			EndIf
		EndIf

		Return True
	; Custom button
	ElseIf CB <> Null
		If CB\Toggle = True
			CB\State = State
			If CB\State = True
				EntityTexture(G\EN, CB\DownTex)
			Else
				EntityTexture(G\EN, CB\UpTex)
			EndIf
		EndIf

		Return True
	EndIf

End Function

; Returns the True if a button is currently in the down state
Function GY_ButtonDown(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	B.GY_Button = Object.GY_Button(G\TypeHandle)
	CB.GY_CustomButton = Object.GY_CustomButton(G\TypeHandle)

	; Normal button
	If B <> Null
		Return B\State
	; Custom button
	ElseIf CB <> Null
		Return CB\State
	EndIf

End Function

; Returns if a button has been clicked
Function GY_ButtonHit(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	B.GY_Button = Object.GY_Button(G\TypeHandle)
	CB.GY_CustomButton = Object.GY_CustomButton(G\TypeHandle)

	; Normal button
	If B <> Null
		If B\Clicked = True Then B\Clicked = False : Return True
		Return False
	; Custom button
	ElseIf CB <> Null
		If CB\Clicked = True Then CB\Clicked = False : Return True
		Return False
	EndIf

End Function

; Returns if a button has been right clicked
Function GY_ButtonRightHit(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	B.GY_Button = Object.GY_Button(G\TypeHandle)
	CB.GY_CustomButton = Object.GY_CustomButton(G\TypeHandle)

	; Normal button
	If B <> Null
		If B\RightClicked = True Then B\RightClicked = False : Return True
		Return False
	; Custom button
	ElseIf CB <> Null
		If CB\RightClicked = True Then CB\RightClicked = False : Return True
		Return False
	EndIf

End Function

; Creates a progress bar gadget
Function GY_CreateProgressBar(Parent, X#, Y#, Width#, Height#, Value, Max, cR = 255, cG = 255, cB = 255, Z = -3000)

	GY_ZeroCamera()

	; Progress Bar
	P.GY_ProgressBar = New GY_ProgressBar
	P\Max = Max

	; Gadget
	G.GY_Gadget = New GY_Gadget
	G\TypeHandle = Handle(P)
	P\Gadget = G
	G\Width# = Width#
	G\Height# = Height#
	G\Alpha# = 1.0
	G\Parent = Object.GY_Gadget(Parent)

	; Main entity (background)
	G\EN = GY_CreateQuad(GY_Cam)
	EntityFX(G\EN, 1 + 8)
	EntityTexture(G\EN, GY_ProgBarBackground)

	; Bar entity
	P\BarEN = GY_CreateQuad(G\EN)
	ScaleMesh(P\BarEN, 1.0, 0.95, 1.0)
	PositionMesh(P\BarEN, 0.01, -0.05, 0.0)
	EntityTexture(P\BarEN, GY_ProgBarForeground)
	EntityColor(P\BarEN, cR, cG, cB)
	EntityFX(P\BarEN, 1 + 8)

	GY_PositionGadget(Handle(G), X#, Y#)
	GY_ScaleGadget(Handle(G), Width#, Height#)


	; Z position
	If G\Parent <> Null
		G\ZPosition = G\Parent\ZPosition - 1
		EntityOrder(G\EN, G\ZPosition)
		EntityOrder(P\BarEN, G\ZPosition - 1)
	Else
		G\ZPosition = Z
		EntityOrder(G\EN, Z)
		EntityOrder(P\BarEN, G\ZPosition - 1)
	EndIf

	; Initial state
	GY_UpdateProgressBar(Handle(G), Value)

	GY_RestoreCamera()

	Return Handle(G)

End Function

;Progress bar for health bars
Function GY_CreateProgressBar2(Parent, X#, Y#, Width#, Height#, Value, Max, cR = 255, cG = 255, cB = 255, Z = -4000)

	GY_ZeroCamera()

	; Progress Bar
	P.GY_ProgressBar = New GY_ProgressBar
	P\Max = Max

	; Gadget
	G.GY_Gadget = New GY_Gadget
	G\TypeHandle = Handle(P)
	P\Gadget = G
	G\Width# = Width#
	G\Height# = Height#
	G\Alpha# = 1.0
	G\Parent = Object.GY_Gadget(Parent)

	; Main entity (background)
	G\EN = GY_CreateQuad(GY_Cam)
	EntityFX(G\EN, 1 + 8)
	EntityTexture(G\EN, GY_ProgBarBackground2)

	; Bar entity
	P\BarEN = GY_CreateQuad(G\EN)
	ScaleMesh(P\BarEN, 1.0, 0.95, 1.0)
	PositionMesh(P\BarEN, 0.01, -0.05, 0.0)
	EntityTexture(P\BarEN, GY_ProgBarForeground2)
	EntityColor(P\BarEN, cR, cG, cB)
	EntityFX(P\BarEN, 1 + 8)

	GY_PositionGadget(Handle(G), X#, Y#)
	GY_ScaleGadget(Handle(G), Width#, Height#)

	; Z position
	If G\Parent <> Null
		G\ZPosition = G\Parent\ZPosition - 1
		EntityOrder(G\EN, G\ZPosition)
		EntityOrder(P\BarEN, G\ZPosition - 1)
	Else
		G\ZPosition = Z
		EntityOrder(G\EN, Z)
		EntityOrder(P\BarEN, G\ZPosition - 2)
	EndIf

	; Initial state
	GY_UpdateProgressBar(Handle(G), Value)

	GY_RestoreCamera()

	Return Handle(G)

End Function

Function GY_CreateProgressBar3(Parent, X#, Y#, Width#, Height#, Value, Max, cR = 255, cG = 255, cB = 255, Z = -4000)

	GY_ZeroCamera()

	; Progress Bar
	P.GY_ProgressBar = New GY_ProgressBar
	P\Max = Max

	; Gadget
	G.GY_Gadget = New GY_Gadget
	G\TypeHandle = Handle(P)
	P\Gadget = G
	G\Width# = Width#
	G\Height# = Height#
	G\Alpha# = 1.0
	G\Parent = Object.GY_Gadget(Parent)

	; Main entity (background)
	G\EN = GY_CreateQuad(GY_Cam)
	EntityFX(G\EN, 1 + 8)
	EntityTexture(G\EN, GY_ProgBarBackground2) ;##~

	; Bar entity
	P\BarEN = GY_CreateQuad(G\EN)
	ScaleMesh(P\BarEN, 1.0, 0.95, 1.0)
	PositionMesh(P\BarEN, 0.01, -0.05, 0.0)
	EntityTexture(P\BarEN, GY_ProgBarForeground3)
	EntityColor(P\BarEN, cR, cG, cB)
	EntityFX(P\BarEN, 1 + 8)

	GY_PositionGadget(Handle(G), X#, Y#)
	GY_ScaleGadget(Handle(G), Width#, Height#)

	; Z position
	If G\Parent <> Null
		G\ZPosition = G\Parent\ZPosition - 1
		EntityOrder(G\EN, G\ZPosition)
		EntityOrder(P\BarEN, G\ZPosition - 1)
	Else
		G\ZPosition = Z
		EntityOrder(G\EN, Z)
		EntityOrder(P\BarEN, G\ZPosition - 2)
	EndIf

	; Initial state
	GY_UpdateProgressBar(Handle(G), Value)

	GY_RestoreCamera()

	Return Handle(G)

End Function


; Sets a progress bar value
Function GY_UpdateProgressBar(GHandle, Value)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	P.GY_ProgressBar = Object.GY_ProgressBar(G\TypeHandle)
	If P = Null Then Return False

	If Value > P\Max Then Value = P\Max
	P\Value = Value
	ScaleEntity(P\BarEN, Float#(Value) / Float#(P\Max) - 0.02, 1.0, 1.0) ; DEFINITION OF STUPID.......

	Return True

End Function

; Creates a label gadget
Function GY_CreateLabel(Parent, X#, Y#, Label$, cR = 255, cG = 255, cB = 255, Just = Justify_Left)

	GY_ZeroCamera()

	; Width
	If Width# < 0.0 Then Width# = Abs(Width#) * Len(Label$)

	; Label
	L.GY_Label = New GY_Label
	L\Justify = Just

	; Gadget
	G.GY_Gadget = New GY_Gadget
	G\TypeHandle = Handle(L)
	L\Gadget = G
	G\Width# = 0.01 * Len(Label$)
	G\Height# = 0.017
	G\AbsoluteSize = True
	G\Alpha# = 1.0
	G\Caption$ = Label$
	G\Parent = Object.GY_Gadget(Parent)

;MAIN TEXT
	; Main entity
	G\EN = GY_Create3DText(0.0, 0.0, 0.01 * Len(Label$), 0.017, Len(Label$), GY_TitleFont, GY_Cam)
	;G\EN = GY_Create3DText(0.0, 0.0, 0.013 * Len(Label$), 0.02, Len(Label$), GY_TitleFont, GY_Cam)
	GY_Set3DText(G\EN, Label$)
	EntityColor(G\EN, cR, cG, cB)
	G\Width# = GY_TextWidth#(G\EN, Label$)

	If L\Justify = Justify_Left
		GY_PositionGadget(Handle(G), X#, Y#)
	ElseIf L\Justify = Justify_Right
		L\OldWidth# = G\Width# / GY_GadgetWidth#(Handle(G\Parent), True)
		GY_PositionGadget(Handle(G), X# - L\OldWidth#, Y#)
	Else
		L\OldWidth# = (G\Width# / GY_GadgetWidth#(Handle(G\Parent), True)) / 2.0
		GY_PositionGadget(Handle(G), X# - L\OldWidth#, Y#)
	EndIf

	; Z position
	If G\Parent <> Null
		G\ZPosition = G\Parent\ZPosition - 1
		EntityOrder(G\EN, G\ZPosition)
	Else
		G\ZPosition = -2999
		EntityOrder(G\EN, -2999)
	EndIf

	GY_RestoreCamera()

	Return Handle(G)

End Function


Function GY_CreateLabelBig(Parent, X#, Y#, Label$, cR = 255, cG = 255, cB = 255, Just = Justify_Left)

	GY_ZeroCamera()

	; Width
	If Width# < 0.0 Then Width# = Abs(Width#) * Len(Label$)

	; Label
	L.GY_Label = New GY_Label
	L\Justify = Just

	; Gadget
	G.GY_Gadget = New GY_Gadget
	G\TypeHandle = Handle(L)
	L\Gadget = G
	G\Width# = 0.01 * Len(Label$)
	G\Height# = 0.017
	G\AbsoluteSize = True
	G\Alpha# = 1.0
	G\Caption$ = Label$
	G\Parent = Object.GY_Gadget(Parent)

;MAIN TEXT
	; Main entity
	G\EN = GY_Create3DText(0.0, 0.0, 0.013 * Len(Label$), 0.02, Len(Label$), GY_TitleFont, GY_Cam)
	;G\EN = GY_Create3DText(0.0, 0.0, 0.013 * Len(Label$), 0.02, Len(Label$), GY_TitleFont, GY_Cam)
	GY_Set3DText(G\EN, Label$)
	;EntityColor(G\EN, cR, cG, cB)
	EntityColor(G\EN, 255, 255, 255)
	G\Width# = GY_TextWidth#(G\EN, Label$)

	If L\Justify = Justify_Left
		GY_PositionGadget(Handle(G), X#, Y#)
	ElseIf L\Justify = Justify_Right
		L\OldWidth# = G\Width# / GY_GadgetWidth#(Handle(G\Parent), True)
		GY_PositionGadget(Handle(G), X# - L\OldWidth#, Y#)
	Else
		L\OldWidth# = (G\Width# / GY_GadgetWidth#(Handle(G\Parent), True)) / 2.0
		GY_PositionGadget(Handle(G), X# - L\OldWidth#, Y#)
	EndIf

	; Z position
	If G\Parent <> Null
		G\ZPosition = G\Parent\ZPosition - 1
		EntityOrder(G\EN, G\ZPosition)
	Else
		G\ZPosition = -3000
		EntityOrder(G\EN, -3000)
	EndIf

	GY_RestoreCamera()

	Return Handle(G)

End Function

; Changes the text of a label gadget
Function GY_UpdateLabel(GHandle, Label$, cR = -1, cG = -1, cB = -1, Just = -1)

	Local G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	Local L.GY_Label = Object.GY_Label(G\TypeHandle)
	If L = Null Then Return False
	
	If cR > -1 Or cR > -1 Or cB > -1 Then EntityColor(G\EN, cR, cG, cB)
	
	GY_Set3DText(G\EN, Label$)
	G\Width# = GY_TextWidth#(G\EN, Label$)

	; Justify
	If Just > -1 Then L\Justify = Just
	X# = GY_GadgetX#(GHandle, False) + L\OldWidth#
	Y# = GY_GadgetY#(GHandle, False)
	If L\Justify = Justify_Left
		L\OldWidth# = 0.0
	ElseIf L\Justify = Justify_Right
		L\OldWidth# = G\Width# / GY_GadgetWidth#(Handle(G\Parent), True)
	Else
		L\OldWidth# = (G\Width# / GY_GadgetWidth#(Handle(G\Parent), True)) / 2.0
	EndIf
	GY_PositionGadget(GHandle, X# - L\OldWidth#, Y#)

	Return True

End Function

; Creates a checkbox gadget
Function GY_CreateCheckBox(Parent, X#, Y#, Label$, State = False, cR = 255, cG = 255, cB = 255)

	GY_ZeroCamera()

	; Checkbox
	C.GY_CheckBox = New GY_CheckBox

	; Gadget
	G.GY_Gadget = New GY_Gadget
	C\Gadget = G
	G\TypeHandle = Handle(C)
	G\Width# = 0.04 + (0.017 * Len(Label$))
	G\Alpha# = 1.0
	G\Caption$ = Label$
	G\Parent = Object.GY_Gadget(Parent)

	; Main entity
	G\EN = GY_CreateQuad(GY_Cam)
	EntityFX(G\EN, 1 + 8)
	G\AbsoluteSize = True
	GY_PositionGadget(Handle(G), X#, Y#)
	GY_ScaleGadget(Handle(G), 0.023, 0.023, True)

	; Label
	X# = GY_GadgetX#(Handle(G), 1) + 0.04
	C\LabelText = GY_Create3DText(X#, GY_GadgetY#(Handle(G), 1), 0.01 * Len(Label$), 0.017, Len(Label$), GY_TitleFont, GY_Cam)
	GY_Set3DText(C\LabelText, Label$)
	EntityParent(C\LabelText, G\EN)
	EntityColor(C\LabelText, cR, cG, cB)

	; Initial state
	GY_UpdateCheckBox(Handle(G), State)

	; Z position
	If G\Parent <> Null
		G\ZPosition = G\Parent\ZPosition - 1
	Else
		G\ZPosition = -3000
	EndIf
	EntityOrder(G\EN, G\ZPosition)
	EntityOrder(C\LabelText, G\ZPosition)

	GY_RestoreCamera()

	Return Handle(G)

End Function

; Sets the state (On or Off) for a checkbox
Function GY_UpdateCheckBox(GHandle, State)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	C.GY_CheckBox = Object.GY_CheckBox(G\TypeHandle)
	If C = Null Then Return False

	C\State = State
	If C\State = True
		EntityTexture(G\EN, GY_CheckBoxOn)
	Else
		EntityTexture(G\EN, GY_CheckBoxOff)
	EndIf

End Function

; Retrieves the state of a checkbox
Function GY_CheckBoxDown(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	C.GY_CheckBox = Object.GY_CheckBox(G\TypeHandle)
	If C = Null Then Return False

	Return C\State

End Function

; Returns the number of times the checkbox has been toggled since the last call
Function GY_CheckBoxHit(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	C.GY_CheckBox = Object.GY_CheckBox(G\TypeHandle)
	If C = Null Then Return False

	Hits = C\Hits
	C\Hits = 0
	Return Hits

End Function

; Creates a window gadget
Function GY_CreateWindow(Title$, X#, Y#, Width#, Height#, HasTitle = True, Close = True, Min = True, Tex = 0, AutoFreeTex = True)

	GY_ZeroCamera()

	; Window
	W.GY_Window = New GY_Window
	W\UserTexture = Tex
	W\AutoFreeUserTexture = AutoFreeTex
	Insert W Before First GY_Window

	; Gadget
	G.GY_Gadget = New GY_Gadget
	W\Gadget = G
	G\TypeHandle = Handle(W)
	G\Width# = Width#
	G\Height# = Height#
	G\Alpha# = 1.0
	G\Caption$ = Title$

	; Main entity
	G\EN = GY_CreateQuad(GY_Cam)
	If W\UserTexture = 0
		EntityTexture(G\EN, GY_Window)
		GY_ScaleQuad(G\EN, Width#, Height#)
	Else
		EntityTexture(G\EN, W\UserTexture)
		ScaleEntity(G\EN, Width# * 20.0, Height# * 15.0, 1.0)
	EndIf
	EntityFX(G\EN, 1 + 8)
	GY_PositionGadget(Handle(G), X#, Y#)

	LLRBorder = LoadTexture("Data\UI\WindowBorder.png", 4)
	
	; Borders
	W\LeftEN = GY_CreateQuad(GY_Cam)
	ScaleEntity(W\LeftEN, 0.005 * 20.0, Height# * 15.0, 1.0)
	PositionEntity(W\LeftEN, (X# * 20.0) - 10.1, (Y# * -15.0) + 7.5, 10.0)
	EntityFX(W\LeftEN, 1 + 8)
	
	EntityColor(W\LeftEN, GY_BorderR, GY_BorderG, GY_BorderB)
	
	EntityParent(W\LeftEN, G\EN)
	EntityTexture(W\LeftEN, LLRBorder)

	W\LowerEN = GY_CreateQuad(GY_Cam)
	ScaleEntity(W\LowerEN, (Width# + 0.01) * 20.0, 0.005 * 15.0, 1.0)
	PositionEntity(W\LowerEN, (X# * 20.0) - 10.1, ((Y# + Height#) * -15.0) + 7.5, 10.0)
	EntityFX(W\LowerEN, 1 + 8)
	
	EntityColor(W\LowerEN, GY_BorderR, GY_BorderG, GY_BorderB)
	
	EntityParent(W\LowerEN, G\EN)
	EntityTexture(W\LowerEN, LLRBorder)

	W\RightEN = GY_CreateQuad(GY_Cam)
	ScaleEntity(W\RightEN, 0.005 * 20.0, Height# * 15.0, 1.0)
	PositionEntity(W\RightEN, ((X# + Width#) * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
	EntityFX(W\RightEN, 1 + 8)
	
	EntityColor(W\RightEN, GY_BorderR, GY_BorderG, GY_BorderB)
	
	EntityParent(W\RightEN, G\EN)
	EntityTexture(W\RightEN, LLRBorder)

	If HasTitle = True
		; Title bar
		W\TitleEN = GY_CreateQuad(GY_Cam)
		ScaleEntity(W\TitleEN, (Width# + 0.0) * 20.0, 0.03 * 15.0, 1.0) ;(Width# + 0.01) * 20.0, 0.03 * 15.0, 1.0)
		EntityTexture(W\TitleEN, GY_Title)
		EntityFX(W\TitleEN, 1 + 8)
		PositionEntity(W\TitleEN, (X# * 20.0) - 10.0, (Y# * -15.0) + 7.95, 10.0) ; (X# * 20.0) - 10.1, (Y# * -15.0) + 7.95, 10.0)
		EntityParent(W\TitleEN, G\EN)

		; Text
		Length = 25 ;(Width# / 0.02) - 1
	;	W\TitleText = GY_Create3DText(X# + 0.004, Y# + 0.006, 0.013 * Float#(Length), 0.02, Length, GY_TitleFont, GY_Cam) ;X#, Y# - 0.0255, 0.013 * Float#(Length), 0.02, Length, GY_TitleFont, GY_Cam)
		W\TitleText = GY_Create3DText(X# + 0.01, Y# - 0.0255, 0.013 * Float#(Length), 0.02, Length, GY_TitleFont, GY_Cam)

	;	G\EN = GY_Create3DText(0.0, 0.0, 0.013 * Len(Label$), 0.02, Len(Label$), GY_TitleFont, GY_Cam)
		
		GY_Set3DText(W\TitleText, G\Caption$)
		EntityColor(W\TitleText, GY_TitleR, GY_TitleG, GY_TitleB)
		EntityParent(W\TitleText, G\EN)

		; Close button
		If Close = True
			W\CloseEN = GY_CreateQuad(GY_Cam)
			ScaleEntity(W\CloseEN, 0.02 * 20.0, 0.02 * 20.0, 1.0)
			EntityTexture(W\CloseEN, GY_Close)
			EntityFX(W\CloseEN, 1 + 8)
			PositionEntity(W\CloseEN, ((X# + Width#) * 20.0) - 10.4, (Y# * -15.0) + 7.925, 10.0) ;(X# + Width#) * 20.0) - 10.4, (Y# * -15.0) + 7.925, 10.0)
			EntityParent(W\CloseEN, G\EN)
		EndIf

		; Minimise button
		If Min = True
			W\MinEN = GY_CreateQuad(GY_Cam)
			ScaleEntity(W\MinEN, 0.02 * 20.0, 0.02 * 20.0, 1.0)
			EntityTexture(W\MinEN, GY_Minimise)
			EntityFX(W\MinEN, 1 + 8)
			If Close = True
				PositionEntity(W\MinEN, ((X# + Width#) * 20.0) - 11.1, (Y# * -15.0) + 7.35, 10.0)
			Else
				PositionEntity(W\MinEN, ((X# + Width#) * 20.0) - 10.6, (Y# * -15.0) + 7.35, 10.0)
			EndIf
			EntityParent(W\MinEN, G\EN)
		EndIf
	; Top border
	Else
		W\TopEN = GY_CreateQuad(GY_Cam)
		ScaleEntity(W\TopEN, (Width# + 0.01) * 20.0, 0.005 * 15.0, 1.0)
		PositionEntity(W\TopEN, (X# * 20.0) - 10.1, ((Y# - 0.005) * -15.0) + 7.5, 10.0)
		EntityFX(W\TopEN, 1 + 8)
		
		EntityColor(W\TopEN, GY_BorderR, GY_BorderG, GY_BorderB)
		
		EntityParent(W\TopEN, G\EN)
		EntityTexture(W\TopEN, LLRBorder)
	EndIf

	; Put in front
	GY_ActivateWindow(Handle(G))

	GY_RestoreCamera()

	Return Handle(G)

End Function

; Creates a Option window window gadget
Function GY_CreateWindowOptions(Title$, X#, Y#, Width#, Height#, HasTitle = True, Close = True, Min = True, Tex = 0, AutoFreeTex = True)

	GY_ZeroCamera()

	; Window
	W.GY_Window = New GY_Window
	W\UserTexture = Tex
	W\AutoFreeUserTexture = AutoFreeTex
	Insert W Before First GY_Window

	; Gadget
	G.GY_Gadget = New GY_Gadget
	W\Gadget = G
	G\TypeHandle = Handle(W)
	G\Width# = Width#
	G\Height# = Height#
	G\Alpha# = 1.0
	G\Caption$ = Title$

	; Main entity
	G\EN = GY_CreateQuad(GY_Cam)
	If W\UserTexture = 0
		EntityTexture(G\EN, GY_Window)
		GY_ScaleQuad(G\EN, Width#, Height#)
	Else
		EntityTexture(G\EN, W\UserTexture)
		ScaleEntity(G\EN, Width# * 20.0, Height# * 15.0, 1.0)
	EndIf
	EntityFX(G\EN, 1 + 8)
	GY_PositionGadget(Handle(G), X#, Y#)
	
	LLRBorder = LoadTexture("Data\UI\WindowBorder.png", 4)
	
	; Borders
	W\LeftEN = GY_CreateQuad(GY_Cam)
	ScaleEntity(W\LeftEN, 0.005 * 20.0, Height# * 15.0, 1.0)
	PositionEntity(W\LeftEN, (X# * 20.0) - 10.1, (Y# * -15.0) + 7.5, 10.0)
	EntityFX(W\LeftEN, 1 + 8)
	;EntityColor(W\LeftEN, GY_BorderR, GY_BorderG, GY_BorderB)
	EntityParent(W\LeftEN, G\EN)
	EntityTexture(W\LeftEN, LLRBorder)

	W\LowerEN = GY_CreateQuad(GY_Cam)
	ScaleEntity(W\LowerEN, (Width# + 0.01) * 20.0, 0.005 * 15.0, 1.0)
	PositionEntity(W\LowerEN, (X# * 20.0) - 10.1, ((Y# + Height#) * -15.0) + 7.5, 10.0)
	EntityFX(W\LowerEN, 1 + 8)
	;EntityColor(W\LowerEN, GY_BorderR, GY_BorderG, GY_BorderB)
	EntityParent(W\LowerEN, G\EN)
	EntityTexture(W\LowerEN, LLRBorder)

	W\RightEN = GY_CreateQuad(GY_Cam)
	ScaleEntity(W\RightEN, 0.005 * 20.0, Height# * 15.0, 1.0)
	PositionEntity(W\RightEN, ((X# + Width#) * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
	EntityFX(W\RightEN, 1 + 8)
	;EntityColor(W\RightEN, GY_BorderR, GY_BorderG, GY_BorderB)
	EntityParent(W\RightEN, G\EN)
	EntityTexture(W\RightEN, LLRBorder)

	If HasTitle = True
		; Title bar
		W\TitleEN = GY_CreateQuad(GY_Cam)
		ScaleEntity(W\TitleEN, (Width# + 0.01) * 20.0, 0.03 * 15.0, 1.0)
		EntityTexture(W\TitleEN, GY_Title)
		EntityFX(W\TitleEN, 1 + 8)
		PositionEntity(W\TitleEN, (X# * 20.0) - 10.1, (Y# * -15.0) + 7.95, 10.0)
		EntityParent(W\TitleEN, G\EN)

		; Text
		Length = 50
		W\TitleText = GY_Create3DText(X#, Y# - 0.0255, 0.013 * Float#(Length), 0.02, Length, GY_TitleFont, GY_Cam)
		;	G\EN = GY_Create3DText(0.0, 0.0, 0.013 * Len(Label$), 0.02, Len(Label$), GY_TitleFont, GY_Cam)
		GY_Set3DText(W\TitleText, G\Caption$)
		EntityColor(W\TitleText, GY_TitleR, GY_TitleG, GY_TitleB)
		EntityParent(W\TitleText, G\EN)

		; Close button
		If Close = True
			W\CloseEN = GY_CreateQuad(GY_Cam)
			ScaleEntity(W\CloseEN, 0.02 * 20.0, 0.02 * 20.0, 1.0)
			EntityTexture(W\CloseEN, GY_Close)
			EntityFX(W\CloseEN, 1 + 8)
			PositionEntity(W\CloseEN, ((X# + Width#) * 20.0) - 10.4, (Y# * -15.0) + 7.925, 10.0)
			EntityParent(W\CloseEN, G\EN)
		EndIf

		; Minimise button
		If Min = True
			W\MinEN = GY_CreateQuad(GY_Cam)
			ScaleEntity(W\MinEN, 0.02 * 20.0, 0.02 * 20.0, 1.0)
			EntityTexture(W\MinEN, GY_Minimise)
			EntityFX(W\MinEN, 1 + 8)
			If Close = True
				PositionEntity(W\MinEN, ((X# + Width#) * 20.0) - 10.9, (Y# * -15.0) + 7.925, 10.0)
			Else
				PositionEntity(W\MinEN, ((X# + Width#) * 20.0) - 10.4, (Y# * -15.0) + 7.925, 10.0)
			EndIf
			EntityParent(W\MinEN, G\EN)
		EndIf
	; Top border
	Else
		W\TopEN = GY_CreateQuad(GY_Cam)
		ScaleEntity(W\TopEN, (Width# + 0.01) * 20.0, 0.005 * 15.0, 1.0)
		PositionEntity(W\TopEN, (X# * 20.0) - 10.1, ((Y# - 0.005) * -15.0) + 7.5, 10.0)
		EntityFX(W\TopEN, 1 + 8)
		EntityColor(W\TopEN, GY_BorderR, GY_BorderG, GY_BorderB)
		EntityParent(W\TopEN, G\EN)
	EndIf

	; Put in front
	GY_ActivateWindow(Handle(G))

	GY_RestoreCamera()

	Return Handle(G)

End Function


;Creates attention window gaget Cysis145
Function GY_CreateWindowAtten(Title$, X#, Y#, Width#, Height#, HasTitle = True, Close = True, Min = True, Tex = 0, AutoFreeTex = True)

	GY_ZeroCamera()

	; Window
	W.GY_Window = New GY_Window
	W\UserTexture = Tex
	W\AutoFreeUserTexture = AutoFreeTex
	Insert W Before First GY_Window

	; Gadget
	G.GY_Gadget = New GY_Gadget
	W\Gadget = G
	G\TypeHandle = Handle(W)
	G\Width# = Width#
	G\Height# = Height#
	G\Alpha# = 1.0
	G\Caption$ = Title$

	; Main entity
	G\EN = GY_CreateQuad(GY_Cam)
	If W\UserTexture = 0
		EntityTexture(G\EN, GY_Window)
		GY_ScaleQuad(G\EN, Width#, Height#)
	Else
		EntityTexture(G\EN, W\UserTexture)
		ScaleEntity(G\EN, Width# * 20.0, Height# * 15.0, 1.0)
	EndIf
	EntityFX(G\EN, 1 + 8)
	GY_PositionGadget(Handle(G), X#, Y#)

	LLRBorder = LoadTexture("Data\UI\WindowBorder.png", 4)
	
	; Borders
	W\LeftEN = GY_CreateQuad(GY_Cam)
	ScaleEntity(W\LeftEN, 0.005 * 20.0, Height# * 15.0, 1.0)
	PositionEntity(W\LeftEN, (X# * 20.0) - 10.1, (Y# * -15.0) + 7.5, 10.0)
	EntityFX(W\LeftEN, 1 + 8)
	;EntityColor(W\LeftEN, GY_BorderR, GY_BorderG, GY_BorderB)
	EntityParent(W\LeftEN, G\EN)
	EntityTexture(W\LeftEN, LLRBorder)

	W\LowerEN = GY_CreateQuad(GY_Cam)
	ScaleEntity(W\LowerEN, (Width# + 0.01) * 20.0, 0.005 * 15.0, 1.0)
	PositionEntity(W\LowerEN, (X# * 20.0) - 10.1, ((Y# + Height#) * -15.0) + 7.5, 10.0)
	EntityFX(W\LowerEN, 1 + 8)
	;EntityColor(W\LowerEN, GY_BorderR, GY_BorderG, GY_BorderB)
	EntityParent(W\LowerEN, G\EN)
	EntityTexture(W\LowerEN, LLRBorder)

	W\RightEN = GY_CreateQuad(GY_Cam)
	ScaleEntity(W\RightEN, 0.005 * 20.0, Height# * 15.0, 1.0)
	PositionEntity(W\RightEN, ((X# + Width#) * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
	EntityFX(W\RightEN, 1 + 8)
	;EntityColor(W\RightEN, GY_BorderR, GY_BorderG, GY_BorderB)
	EntityParent(W\RightEN, G\EN)
	EntityTexture(W\RightEN, LLRBorder)


	If HasTitle = True
		; Title bar
		W\TitleEN = GY_CreateQuad(GY_Cam)
		ScaleEntity(W\TitleEN, (Width# + 0.01) * 20.0, 0.03 * 15.0, 1.0)
		EntityTexture(W\TitleEN, GY_Title)
		EntityFX(W\TitleEN, 1 + 8)
		PositionEntity(W\TitleEN, (X# * 20.0) - 10.1, (Y# * -15.0) + 7.95, 10.0)
		EntityParent(W\TitleEN, G\EN)

		; Text
		Length = 50
		W\TitleText = GY_Create3DText(X#, Y# - 0.0255, 0.013 * Float#(Length), 0.02, Length, GY_TitleFont, GY_Cam)
		;	G\EN = GY_Create3DText(0.0, 0.0, 0.013 * Len(Label$), 0.02, Len(Label$), GY_TitleFont, GY_Cam)
		GY_Set3DText(W\TitleText, G\Caption$)
		EntityColor(W\TitleText, GY_TitleR, GY_TitleG, GY_TitleB)
		EntityParent(W\TitleText, G\EN)

		; Close button
		;If Close = True
		;	W\CloseEN = GY_CreateQuad(GY_Cam)
		;	ScaleEntity(W\CloseEN, 0.02 * 20.0, 0.02 * 20.0, 1.0)
		;	EntityTexture(W\CloseEN, GY_Close)
		;	EntityFX(W\CloseEN, 1 + 8)
		;	PositionEntity(W\CloseEN, ((X# + Width#) * 20.0) - 10.4, (Y# * -15.0) + 7.925, 10.0)
		;	EntityParent(W\CloseEN, G\EN)
		;EndIf

		; Minimise button
		If Min = True
			W\MinEN = GY_CreateQuad(GY_Cam)
			ScaleEntity(W\MinEN, 0.02 * 20.0, 0.02 * 20.0, 1.0)
			EntityTexture(W\MinEN, GY_Minimise)
			EntityFX(W\MinEN, 1 + 8)
			If Close = True
				PositionEntity(W\MinEN, ((X# + Width#) * 20.0) - 10.9, (Y# * -15.0) + 7.925, 10.0)
			Else
				PositionEntity(W\MinEN, ((X# + Width#) * 20.0) - 10.4, (Y# * -15.0) + 7.925, 10.0)
			EndIf
			EntityParent(W\MinEN, G\EN)
		EndIf
	; Top border
	Else
		W\TopEN = GY_CreateQuad(GY_Cam)
		ScaleEntity(W\TopEN, (Width# + 0.01) * 20.0, 0.005 * 15.0, 1.0)
		PositionEntity(W\TopEN, (X# * 20.0) - 10.1, ((Y# - 0.005) * -15.0) + 7.5, 10.0)
		EntityFX(W\TopEN, 1 + 8)
		EntityColor(W\TopEN, GY_BorderR, GY_BorderG, GY_BorderB)
		EntityParent(W\TopEN, G\EN)
	EndIf

	; Put in front
	GY_ActivateWindow(Handle(G))

	GY_RestoreCamera()

	Return Handle(G)

End Function

;Creates Interaction window Gaget. cysis145 blan
Function GY_CreateWindowInter(Title$, X#, Y#, Width#, Height#, HasTitle = True, Close = True, Min = True, Tex = 0, AutoFreeTex = True)

	GY_ZeroCamera()

	; Window
	W.GY_Window2 = New GY_Window2
	W\UserTexture = Tex
	W\AutoFreeUserTexture = AutoFreeTex
	Insert W Before First GY_Window2

	; Gadget
	G.GY_Gadget = New GY_Gadget
	W\Gadget = G
	G\TypeHandle = Handle(W)
	G\Width# = Width#
	G\Height# = Height#
	G\Alpha# = 1.0
	G\Caption$ = Title$

	; Main entity
	G\EN = GY_CreateQuad(GY_Cam)
	If W\UserTexture = 0
		EntityTexture(G\EN, GY_Window2)
		GY_ScaleQuad(G\EN, Width#, Height#)
	Else
		EntityTexture(G\EN, W\UserTexture)
		ScaleEntity(G\EN, Width# * 20.0, Height# * 15.0, 1.0)
	EndIf
	EntityFX(G\EN, 1 + 8)
	GY_PositionGadget(Handle(G), X#, Y#)

	; Borders
	;W\LeftEN = GY_CreateQuad(GY_Cam)
	;ScaleEntity(W\LeftEN, 0.005 * 20.0, Height# * 15.0, 1.0)
	;PositionEntity(W\LeftEN, (X# * 20.0) - 10.1, (Y# * -15.0) + 7.5, 10.0)
	;EntityFX(W\LeftEN, 1 + 8)
	;EntityColor(W\LeftEN, GY_BorderR, GY_BorderG, GY_BorderB)
	;EntityParent(W\LeftEN, G\EN)


	;W\LowerEN = GY_CreateQuad(GY_Cam)
	;ScaleEntity(W\LowerEN, (Width# + 0.01) * 20.0, 0.005 * 15.0, 1.0)
	;PositionEntity(W\LowerEN, (X# * 20.0) - 10.1, ((Y# + Height#) * -15.0) + 7.5, 10.0)
	;EntityFX(W\LowerEN, 1 + 8)
	;EntityColor(W\LowerEN, GY_BorderR, GY_BorderG, GY_BorderB)
	;EntityParent(W\LowerEN, G\EN)

	;W\RightEN = GY_CreateQuad(GY_Cam)
	;ScaleEntity(W\RightEN, 0.005 * 20.0, Height# * 15.0, 1.0)
	;PositionEntity(W\RightEN, ((X# + Width#) * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
	;EntityColor(W\RightEN, GY_BorderR, GY_BorderG, GY_BorderB)
	;EntityFX(W\RightEN, 1 + 8)
	;EntityParent(W\RightEN, G\EN)

	If HasTitle = True
		; Title bar
		;W\TitleEN = GY_CreateQuad(GY_Cam)
		;ScaleEntity(W\TitleEN, (Width# + 0.01) * 20.0, 0.03 * 15.0, 1.0)
		;EntityTexture(W\TitleEN, GY_Title)
		;EntityFX(W\TitleEN, 1 + 8)
		;PositionEntity(W\TitleEN, (X# * 20.0) - 10.1, (Y# * -15.0) + 7.95, 10.0)
		;EntityParent(W\TitleEN, G\EN)

		; Text
		;Length = 20
		;W\TitleText = GY_Create3DText(X#, Y# - 0.0255, 0.013 * Float#(Length), 0.02, Length, GY_TitleFont, GY_Cam)
		;	G\EN = GY_Create3DText(0.0, 0.0, 0.013 * Len(Label$), 0.02, Len(Label$), GY_TitleFont, GY_Cam)
		;GY_Set3DText(W\TitleText, G\Caption$)
		;EntityColor(W\TitleText, GY_TitleR, GY_TitleG, GY_TitleB)
		;EntityParent(W\TitleText, G\EN)

		; Close button
		;If Close = True
		;	W\CloseEN = GY_CreateQuad(GY_Cam)
		;	ScaleEntity(W\CloseEN, 0.02 * 20.0, 0.02 * 20.0, 1.0)
		;	EntityTexture(W\CloseEN, GY_Close)
		;	EntityFX(W\CloseEN, 1 + 8)
		;	PositionEntity(W\CloseEN, ((X# + Width#) * 20.0) - 10.4, (Y# * -15.0) + 7.925, 10.0)
		;	EntityParent(W\CloseEN, G\EN)
		;EndIf

		; Minimise button
		If Min = True
			W\MinEN = GY_CreateQuad(GY_Cam)
			ScaleEntity(W\MinEN, 0.02 * 20.0, 0.02 * 20.0, 1.0)
			EntityTexture(W\MinEN, GY_Minimise)
			EntityFX(W\MinEN, 1 + 8)
			If Close = True
				PositionEntity(W\MinEN, ((X# + Width#) * 20.0) - 10.9, (Y# * -15.0) + 7.925, 10.0)
			Else
				PositionEntity(W\MinEN, ((X# + Width#) * 20.0) - 10.4, (Y# * -15.0) + 7.925, 10.0)
			EndIf
			EntityParent(W\MinEN, G\EN)
		EndIf
	; Top border
	Else
		W\TopEN = GY_CreateQuad(GY_Cam)
		ScaleEntity(W\TopEN, (Width# + 0.01) * 20.0, 0.005 * 15.0, 1.0)
		PositionEntity(W\TopEN, (X# * 20.0) - 10.1, ((Y# - 0.005) * -15.0) + 7.5, 10.0)
		EntityFX(W\TopEN, 1 + 8)
		EntityColor(W\TopEN, GY_BorderR, GY_BorderG, GY_BorderB)
		EntityParent(W\TopEN, G\EN)
	EndIf

	; Put in front
	GY_ActivateWindow(Handle(G))

	GY_RestoreCamera()

	Return Handle(G)

End Function

;Creates amoutn window gaget cysis145
Function GY_CreateWindowAmt(Title$, X#, Y#, Width#, Height#, HasTitle = True, Close = True, Min = True, Tex = 0, AutoFreeTex = True)

	GY_ZeroCamera()

	; Window
	W.GY_Window = New GY_Window
	W\UserTexture = Tex
	W\AutoFreeUserTexture = AutoFreeTex
	Insert W Before First GY_Window

	; Gadget
	G.GY_Gadget = New GY_Gadget
	W\Gadget = G
	G\TypeHandle = Handle(W)
	G\Width# = Width#
	G\Height# = Height#
	G\Alpha# = 1.0
	G\Caption$ = Title$

	; Main entity
	G\EN = GY_CreateQuad(GY_Cam)
	If W\UserTexture = 0
		EntityTexture(G\EN, GY_Window)
		GY_ScaleQuad(G\EN, Width#, Height#)
	Else
		EntityTexture(G\EN, W\UserTexture)
		ScaleEntity(G\EN, Width# * 20.0, Height# * 15.0, 1.0)
	EndIf
	EntityFX(G\EN, 1 + 8)
	GY_PositionGadget(Handle(G), X#, Y#)

	LLRBorder = LoadTexture("Data\UI\WindowBorder.png", 4)
	
	; Borders
	W\LeftEN = GY_CreateQuad(GY_Cam)
	ScaleEntity(W\LeftEN, 0.005 * 20.0, Height# * 15.0, 1.0)
	PositionEntity(W\LeftEN, (X# * 20.0) - 10.1, (Y# * -15.0) + 7.5, 10.0)
	EntityFX(W\LeftEN, 1 + 8)
	;EntityColor(W\LeftEN, GY_BorderR, GY_BorderG, GY_BorderB)
	EntityParent(W\LeftEN, G\EN)
	EntityTexture(W\LeftEN, LLRBorder)

	W\LowerEN = GY_CreateQuad(GY_Cam)
	ScaleEntity(W\LowerEN, (Width# + 0.01) * 20.0, 0.005 * 15.0, 1.0)
	PositionEntity(W\LowerEN, (X# * 20.0) - 10.1, ((Y# + Height#) * -15.0) + 7.5, 10.0)
	EntityFX(W\LowerEN, 1 + 8)
	;EntityColor(W\LowerEN, GY_BorderR, GY_BorderG, GY_BorderB)
	EntityParent(W\LowerEN, G\EN)
	EntityTexture(W\LowerEN, LLRBorder)

	W\RightEN = GY_CreateQuad(GY_Cam)
	ScaleEntity(W\RightEN, 0.005 * 20.0, Height# * 15.0, 1.0)
	PositionEntity(W\RightEN, ((X# + Width#) * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
	EntityFX(W\RightEN, 1 + 8)
	;EntityColor(W\RightEN, GY_BorderR, GY_BorderG, GY_BorderB)
	EntityParent(W\RightEN, G\EN)
	EntityTexture(W\RightEN, LLRBorder)


	If HasTitle = True
		; Title bar
		W\TitleEN = GY_CreateQuad(GY_Cam)
		ScaleEntity(W\TitleEN, (Width# + 0.01) * 20.0, 0.03 * 15.0, 1.0)
		EntityTexture(W\TitleEN, GY_Title)
		EntityFX(W\TitleEN, 1 + 8)
		PositionEntity(W\TitleEN, (X# * 20.0) - 10.1, (Y# * -15.0) + 7.95, 10.0)
		EntityParent(W\TitleEN, G\EN)

		; Text
		Length = 20
		W\TitleText = GY_Create3DText(X#, Y# - 0.0255, 0.013 * Float#(Length), 0.02, Length, GY_TitleFont, GY_Cam)
		;	G\EN = GY_Create3DText(0.0, 0.0, 0.013 * Len(Label$), 0.02, Len(Label$), GY_TitleFont, GY_Cam)
		GY_Set3DText(W\TitleText, G\Caption$)
		EntityColor(W\TitleText, GY_TitleR, GY_TitleG, GY_TitleB)
		EntityParent(W\TitleText, G\EN)

		; Close button
		If Close = True
			W\CloseEN = GY_CreateQuad(GY_Cam)
			ScaleEntity(W\CloseEN, 0.02 * 20.0, 0.02 * 20.0, 1.0)
			EntityTexture(W\CloseEN, GY_Close)
			EntityFX(W\CloseEN, 1 + 8)
			PositionEntity(W\CloseEN, ((X# + Width#) * 20.0) - 10.4, (Y# * -15.0) + 7.925, 10.0)
			EntityParent(W\CloseEN, G\EN)
		EndIf

		; Minimise button
		If Min = True
			W\MinEN = GY_CreateQuad(GY_Cam)
			ScaleEntity(W\MinEN, 0.02 * 20.0, 0.02 * 20.0, 1.0)
			EntityTexture(W\MinEN, GY_Minimise)
			EntityFX(W\MinEN, 1 + 8)
			If Close = True
				PositionEntity(W\MinEN, ((X# + Width#) * 20.0) - 10.9, (Y# * -15.0) + 7.925, 10.0)
			Else
				PositionEntity(W\MinEN, ((X# + Width#) * 20.0) - 10.4, (Y# * -15.0) + 7.925, 10.0)
			EndIf
			EntityParent(W\MinEN, G\EN)
		EndIf
	; Top border
	Else
		W\TopEN = GY_CreateQuad(GY_Cam)
		ScaleEntity(W\TopEN, (Width# + 0.01) * 20.0, 0.005 * 15.0, 1.0)
		PositionEntity(W\TopEN, (X# * 20.0) - 10.1, ((Y# - 0.005) * -15.0) + 7.5, 10.0)
		EntityFX(W\TopEN, 1 + 8)
		EntityColor(W\TopEN, GY_BorderR, GY_BorderG, GY_BorderB)
		EntityParent(W\TopEN, G\EN)
	EndIf

	; Put in front
	GY_ActivateWindow(Handle(G))

	GY_RestoreCamera()

	Return Handle(G)

End Function



; Creates a  Inventory window gadget
Function GY_CreateWindowInventory(Title$, X#, Y#, Width#, Height#, HasTitle = True, Close = True, Min = True, Tex = 0, AutoFreeTex = True)

	GY_ZeroCamera()

	; Window
	W.GY_Window = New GY_Window
	W\UserTexture = Tex
	W\AutoFreeUserTexture = AutoFreeTex
	Insert W Before First GY_Window

	; Gadget
	G.GY_Gadget = New GY_Gadget
	W\Gadget = G
	G\TypeHandle = Handle(W)
	G\Width# = Width#
	G\Height# = Height#
	G\Alpha# = 1.0
	G\Caption$ = Title$

	; Main entity
	G\EN = GY_CreateQuad(GY_Cam)
	If W\UserTexture = 0
		EntityTexture(G\EN, GY_Window)
		GY_ScaleQuad(G\EN, Width#, Height#)
	Else
		EntityTexture(G\EN, W\UserTexture)
		ScaleEntity(G\EN, Width# * 20.0, Height# * 15.0, 1.0)
	EndIf
	EntityFX(G\EN, 1 + 8)
	GY_PositionGadget(Handle(G), X#, Y#)

	LLRBorder = LoadTexture("Data\UI\WindowBorder.png", 4)
	
	; Borders
	W\LeftEN = GY_CreateQuad(GY_Cam)
	ScaleEntity(W\LeftEN, 0.005 * 20.0, Height# * 15.0, 1.0)
	PositionEntity(W\LeftEN, (X# * 20.0) - 10.1, (Y# * -15.0) + 7.5, 10.0)
	EntityFX(W\LeftEN, 1 + 8)
	;EntityColor(W\LeftEN, GY_BorderR, GY_BorderG, GY_BorderB)
	EntityParent(W\LeftEN, G\EN)
	EntityTexture(W\LeftEN, LLRBorder)

	W\LowerEN = GY_CreateQuad(GY_Cam)
	ScaleEntity(W\LowerEN, (Width# + 0.01) * 20.0, 0.005 * 15.0, 1.0)
	PositionEntity(W\LowerEN, (X# * 20.0) - 10.1, ((Y# + Height#) * -15.0) + 7.5, 10.0)
	EntityFX(W\LowerEN, 1 + 8)
	;EntityColor(W\LowerEN, GY_BorderR, GY_BorderG, GY_BorderB)
	EntityParent(W\LowerEN, G\EN)
	EntityTexture(W\LowerEN, LLRBorder)

	W\RightEN = GY_CreateQuad(GY_Cam)
	ScaleEntity(W\RightEN, 0.005 * 20.0, Height# * 15.0, 1.0)
	PositionEntity(W\RightEN, ((X# + Width#) * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
	EntityFX(W\RightEN, 1 + 8)
	;EntityColor(W\RightEN, GY_BorderR, GY_BorderG, GY_BorderB)
	EntityParent(W\RightEN, G\EN)
	EntityTexture(W\RightEN, LLRBorder)


	If HasTitle = True
		; Title bar
		W\TitleEN = GY_CreateQuad(GY_Cam)
		ScaleEntity(W\TitleEN, (Width# + 0.01) * 20.0, 0.03 * 15.0, 1.0)
		EntityTexture(W\TitleEN, GY_Title)
		EntityFX(W\TitleEN, 1 + 8)
		PositionEntity(W\TitleEN, (X# * 20.0) - 10.1, (Y# * -15.0) + 7.95, 10.0)
		EntityParent(W\TitleEN, G\EN)

		; Text
		Length = 20 ;(Width# / 0.02) - 1
		W\TitleText = GY_Create3DText(X#, Y# - 0.0255, 0.013 * Float#(Length), 0.02, Length, GY_TitleFont, GY_Cam) ;0.013* Float#(Length)
		;	G\EN = GY_Create3DText(0.0, 0.0, 0.013 * Len(Label$), 0.02, Len(Label$), GY_TitleFont, GY_Cam)
		GY_Set3DText(W\TitleText, G\Caption$)
		EntityColor(W\TitleText, GY_TitleR, GY_TitleG, GY_TitleB)
		EntityParent(W\TitleText, G\EN)

		; Close button
		If Close = True
			W\CloseEN = GY_CreateQuad(GY_Cam)
			ScaleEntity(W\CloseEN, 0.02 * 20.0, 0.02 * 20.0, 1.0)
			EntityTexture(W\CloseEN, GY_Close)
			EntityFX(W\CloseEN, 1 + 8)
			PositionEntity(W\CloseEN, ((X# + Width#) * 20.0) - 10.4, (Y# * -15.0) + 7.925, 10.0)
			EntityParent(W\CloseEN, G\EN)
		EndIf

		; Minimise button
		If Min = True
			W\MinEN = GY_CreateQuad(GY_Cam)
			ScaleEntity(W\MinEN, 0.02 * 20.0, 0.02 * 20.0, 1.0)
			EntityTexture(W\MinEN, GY_Minimise)
			EntityFX(W\MinEN, 1 + 8)
			If Close = True
				PositionEntity(W\MinEN, ((X# + Width#) * 20.0) - 10.9, (Y# * -15.0) + 7.925, 10.0)
			Else
				PositionEntity(W\MinEN, ((X# + Width#) * 20.0) - 10.4, (Y# * -15.0) + 7.925, 10.0)
			EndIf
			EntityParent(W\MinEN, G\EN)
		EndIf
	; Top border
	Else
		W\TopEN = GY_CreateQuad(GY_Cam)
		ScaleEntity(W\TopEN, (Width# + 0.01) * 20.0, 0.005 * 15.0, 1.0)
		PositionEntity(W\TopEN, (X# * 20.0) - 10.1, ((Y# - 0.005) * -15.0) + 7.5, 10.0)
		EntityFX(W\TopEN, 1 + 8)
		EntityColor(W\TopEN, GY_BorderR, GY_BorderG, GY_BorderB)
		EntityParent(W\TopEN, G\EN)
	EndIf

	; Put in front
	GY_ActivateWindow(Handle(G))

	GY_RestoreCamera()

	Return Handle(G)

End Function

; Creates a window gadget for Item Shop
Function GY_CreateWindowIntan(Title$, X#, Y#, Width#, Height#, HasTitle = True, Close = True, Min = True, Tex = 0, AutoFreeTex = True)

	GY_ZeroCamera()

	; Window
	W.GY_Window = New GY_Window
	W\UserTexture = Tex
	W\AutoFreeUserTexture = AutoFreeTex
	Insert W Before First GY_Window

	; Gadget
	G.GY_Gadget = New GY_Gadget
	W\Gadget = G
	G\TypeHandle = Handle(W)
	G\Width# = Width#
	G\Height# = Height#
	G\Alpha# = 1.0
	G\Caption$ = Title$

	; Main entity
	G\EN = GY_CreateQuad(GY_Cam)
	If W\UserTexture = 0
		EntityTexture(G\EN, GY_Window)
		GY_ScaleQuad(G\EN, Width#, Height#)
	Else
		EntityTexture(G\EN, W\UserTexture)
		ScaleEntity(G\EN, Width# * 20.0, Height# * 15.0, 1.0)
	EndIf
	EntityFX(G\EN, 1 + 8)
	GY_PositionGadget(Handle(G), X#, Y#)

	LLRBorder = LoadTexture("Data\UI\WindowBorder.png", 4)
	
	; Borders
	W\LeftEN = GY_CreateQuad(GY_Cam)
	ScaleEntity(W\LeftEN, 0.005 * 20.0, Height# * 15.0, 1.0)
	PositionEntity(W\LeftEN, (X# * 20.0) - 10.1, (Y# * -15.0) + 7.5, 10.0)
	EntityFX(W\LeftEN, 1 + 8)
	;EntityColor(W\LeftEN, GY_BorderR, GY_BorderG, GY_BorderB)
	EntityParent(W\LeftEN, G\EN)
	EntityTexture(W\LeftEN, LLRBorder)

	W\LowerEN = GY_CreateQuad(GY_Cam)
	ScaleEntity(W\LowerEN, (Width# + 0.01) * 20.0, 0.005 * 15.0, 1.0)
	PositionEntity(W\LowerEN, (X# * 20.0) - 10.1, ((Y# + Height#) * -15.0) + 7.5, 10.0)
	EntityFX(W\LowerEN, 1 + 8)
	;EntityColor(W\LowerEN, GY_BorderR, GY_BorderG, GY_BorderB)
	EntityParent(W\LowerEN, G\EN)
	EntityTexture(W\LowerEN, LLRBorder)

	W\RightEN = GY_CreateQuad(GY_Cam)
	ScaleEntity(W\RightEN, 0.005 * 20.0, Height# * 15.0, 1.0)
	PositionEntity(W\RightEN, ((X# + Width#) * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
	EntityFX(W\RightEN, 1 + 8)
	;EntityColor(W\RightEN, GY_BorderR, GY_BorderG, GY_BorderB)
	EntityParent(W\RightEN, G\EN)
	EntityTexture(W\RightEN, LLRBorder)


	If HasTitle = True
		; Title bar
		W\TitleEN = GY_CreateQuad(GY_Cam)
		ScaleEntity(W\TitleEN, (Width# + 0.01) * 20.0, 0.03 * 15.0, 1.0)
		EntityTexture(W\TitleEN, GY_Title)
		EntityFX(W\TitleEN, 1 + 8)
		PositionEntity(W\TitleEN, (X# * 20.0) - 10.1, (Y# * -15.0) + 7.95, 10.0)
		EntityParent(W\TitleEN, G\EN)

		; Text
		Length = 100
		W\TitleText = GY_Create3DText(X#, Y# - 0.0255, 0.013 * Float#(Length), 0.02, Length, GY_TitleFont, GY_Cam)
		;	G\EN = GY_Create3DText(0.0, 0.0, 0.013 * Len(Label$), 0.02, Len(Label$), GY_TitleFont, GY_Cam)
		GY_Set3DText(W\TitleText, G\Caption$)
		EntityColor(W\TitleText, GY_TitleR, GY_TitleG, GY_TitleB)
		EntityParent(W\TitleText, G\EN)

		; Close button
		If Close = True
			W\CloseEN = GY_CreateQuad(GY_Cam)
			ScaleEntity(W\CloseEN, 0.02 * 20.0, 0.02 * 20.0, 1.0)
			EntityTexture(W\CloseEN, GY_Close)
			EntityFX(W\CloseEN, 1 + 8)
			PositionEntity(W\CloseEN, ((X# + Width#) * 20.0) - 10.4, (Y# * -15.0) + 7.925, 10.0)
			EntityParent(W\CloseEN, G\EN)
		EndIf

		; Minimise button
		If Min = True
			W\MinEN = GY_CreateQuad(GY_Cam)
			ScaleEntity(W\MinEN, 0.02 * 20.0, 0.02 * 20.0, 1.0)
			EntityTexture(W\MinEN, GY_Minimise)
			EntityFX(W\MinEN, 1 + 8)
			If Close = True
				PositionEntity(W\MinEN, ((X# + Width#) * 20.0) - 10.9, (Y# * -15.0) + 7.925, 10.0)
			Else
				PositionEntity(W\MinEN, ((X# + Width#) * 20.0) - 10.4, (Y# * -15.0) + 7.925, 10.0)
			EndIf
			EntityParent(W\MinEN, G\EN)
		EndIf
	; Top border
	Else
		W\TopEN = GY_CreateQuad(GY_Cam)
		ScaleEntity(W\TopEN, (Width# + 0.01) * 20.0, 0.005 * 15.0, 1.0)
		PositionEntity(W\TopEN, (X# * 20.0) - 10.1, ((Y# - 0.005) * -15.0) + 7.5, 10.0)
		EntityFX(W\TopEN, 1 + 8)
		EntityColor(W\TopEN, GY_BorderR, GY_BorderG, GY_BorderB)
		EntityParent(W\TopEN, G\EN)
	EndIf

	; Put in front
	GY_ActivateWindow(Handle(G))

	GY_RestoreCamera()

	Return Handle(G)

End Function



; Changes the background texture for a window
Function GY_UpdateWindowBackground(GHandle, TexID)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	W.GY_Window = Object.GY_Window(G\TypeHandle)
	If W = Null Then Return False

	If TexID = W\UserTexture Then Return

	; Free old texture
	If W\UserTexture <> 0 And W\AutoFreeUserTexture = True Then FreeTexture W\UserTexture

	; Apply new texture
	W\UserTexture = TexID
	If W\UserTexture = 0
		EntityTexture(G\EN, GY_Window)
		GY_ScaleGadget(GHandle, G\Width#, G\Height#, False)
	Else
		EntityTexture(G\EN, W\UserTexture)
		Surf = GetSurface(G\EN, 1)
		VertexTexCoords(Surf, 0, 0.0, 1.0)
		VertexTexCoords(Surf, 1, 1.0, 1.0)
		VertexTexCoords(Surf, 2, 1.0, 0.0)
		VertexTexCoords(Surf, 3, 0.0, 0.0)
	EndIf

End Function

; Returns True if a window has been closed by the user
Function GY_WindowClosed(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	W.GY_Window = Object.GY_Window(G\TypeHandle)
	If W = Null Then Return False

	If W\Closed = True
		W\Closed = False
		Return True
	Else
		Return False
	EndIf

End Function

; Returns True if a window is currently minimised
Function GY_WindowMinimised(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	W.GY_Window = Object.GY_Window(G\TypeHandle)
	If W = Null Then Return False

	Return W\Minimised

End Function

; Returns true if the given window is the active window
Function GY_WindowActive(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	W.GY_Window = Object.GY_Window(G\TypeHandle)
	If W = Null Then Return False

	If W = Last GY_Window Then Return True

End Function

; Brings a window to the front of the Z order
Function GY_ActivateWindow(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	W.GY_Window = Object.GY_Window(G\TypeHandle)
	If W = Null Then Return False

	; Put it at the front
	Insert W After Last GY_Window
	If W\TitleEN <> 0 Then EntityTexture(W\TitleEN, GY_Title)
	If W\TopEN <> 0 Then EntityColor(W\TopEN, GY_BorderR, GY_BorderG, GY_BorderB)
	EntityColor(W\LeftEN, GY_BorderR, GY_BorderG, GY_BorderB)
	EntityColor(W\LowerEN, GY_BorderR, GY_BorderG, GY_BorderB)
	EntityColor(W\RightEN, GY_BorderR, GY_BorderG, GY_BorderB)

	; Reset Z position of every window
	Z = -10
	For Win.GY_Window = Each GY_Window
		Win\Gadget\ZPosition = Z
		EntityOrder(Win\Gadget\EN, Z)
		If Win\TitleEN <> 0
			EntityOrder(Win\TitleEN, Z)
			If Win <> W Then EntityTexture(Win\TitleEN, GY_InactiveTitle)
		EndIf
		If Win\TopEN <> 0 Then EntityOrder(Win\TopEN, Z)
		EntityOrder(Win\LeftEN, Z)
		EntityOrder(Win\LowerEN, Z)
		EntityOrder(Win\RightEN, Z)
		If Win <> W
			If Win\TopEN <> 0 Then EntityColor(Win\TopEN, GY_InactiveBorderR, GY_InactiveBorderG, GY_InactiveBorderB)
			EntityColor(Win\LeftEN, GY_InactiveBorderR, GY_InactiveBorderG, GY_InactiveBorderB)
			EntityColor(Win\LowerEN, GY_InactiveBorderR, GY_InactiveBorderG, GY_InactiveBorderB)
			EntityColor(Win\RightEN, GY_InactiveBorderR, GY_InactiveBorderG, GY_InactiveBorderB)
		EndIf
		If Win\CloseEN <> 0 Then EntityOrder(Win\CloseEN, Z - 1)
		If Win\MinEN <> 0 Then EntityOrder(Win\MinEN, Z - 1)
		If Win\TitleText <> 0 Then EntityOrder(Win\TitleText, Z - 1)
		Z = Z - 10
	Next

	; Reset Z position of every other gadget
	For G = Each GY_Gadget
		; If it's a child
		If G\Parent <> Null
			G\ZPosition = G\Parent\ZPosition - 5
			If G\EN <> 0 Then GY_RecursiveEntityOrder(G\EN, G\ZPosition)
		; If it's not a child, but not a window either, it always goes on top/bottom
		ElseIf Object.GY_Window(G\TypeHandle) = Null
			If G\OnBottom = True Then G\ZPosition = -2 Else G\ZPosition = -3000
			If G\EN <> 0 Then GY_RecursiveEntityOrder(G\EN, G\ZPosition)
		EndIf

		; If it's a progress bar
		If Object.GY_ProgressBar(G\TypeHandle) <> Null
			P.GY_ProgressBar = Object.GY_ProgressBar(G\TypeHandle)
			EntityOrder(P\BarEN, G\ZPosition - 1)
		; A checkbox
		ElseIf Object.GY_CheckBox(G\TypeHandle) <> Null
			Ch.GY_CheckBox = Object.GY_CheckBox(G\TypeHandle)
			EntityOrder(Ch\LabelText, G\ZPosition)
		; A button
		ElseIf Object.GY_Button(G\TypeHandle) <> Null
			B.GY_Button = Object.GY_Button(G\TypeHandle)
			If B\ComboOwner = True
				G\ZPosition = G\ZPosition - 3
				If G\EN <> 0 Then GY_RecursiveEntityOrder(G\EN, G\ZPosition)
			EndIf
			EntityOrder(B\LabelEN, G\ZPosition - 1)
			EntityOrder(B\T, G\ZPosition - 1)
			EntityOrder(B\B, G\ZPosition - 1)
			EntityOrder(B\R, G\ZPosition - 1)
			EntityOrder(B\L, G\ZPosition - 1)
			EntityOrder(B\TR, G\ZPosition - 1)
			EntityOrder(B\TL, G\ZPosition - 1)
			EntityOrder(B\BR, G\ZPosition - 1)
			EntityOrder(B\BL, G\ZPosition - 1)
		; A slider
		ElseIf Object.GY_Slider(G\TypeHandle) <> Null
			S.GY_Slider = Object.GY_Slider(G\TypeHandle)
			EntityOrder(S\BarEN, G\ZPosition - 1)
			EntityOrder(S\LabelEN, G\ZPosition - 2)
		; A combo box
		ElseIf Object.GY_ComboBox(G\TypeHandle) <> Null
			C.GY_ComboBox = Object.GY_ComboBox(G\TypeHandle)
			EntityOrder(C\ListAreaEN, G\ZPosition - 2)
			EntityOrder(C\LabelEN, G\ZPosition - 1)
			EntityOrder(C\LowerEN, G\ZPosition - 1)
			EntityOrder(C\TopLeftEN, G\ZPosition - 1)
			EntityOrder(C\RightEN, G\ZPosition - 1)
			If C\Selected <> Null Then EntityOrder(C\Selected\LabelEN, G\ZPosition - 1)

			; Items
			If C\OpenStatus = True
				For I.GY_ComboItem = Each GY_ComboItem
					If I\Box = C Then EntityOrder(I\LabelEN, G\ZPosition - 3)
				Next
			EndIf
		; A list box
		ElseIf Object.GY_ListBox(G\TypeHandle) <> Null
			L.GY_ListBox = Object.GY_ListBox(G\TypeHandle)
			EntityOrder(L\BorderEN, G\ZPosition)
			EntityOrder(L\Gadget\EN, G\ZPosition - 1)

			; Items
			For Li.GY_ListItem = Each GY_ListItem
				If Li\Box = L Then EntityOrder(Li\LabelEN, G\ZPosition - 2)
			Next
		; A scrollbar
		ElseIf Object.GY_ScrollBar(G\TypeHandle) <> Null
			Sc.GY_ScrollBar = Object.GY_ScrollBar(G\TypeHandle)
			If Sc\ComboOwner = True
				EntityOrder(Sc\Gadget\EN, G\ZPosition - 3)
				EntityOrder(Sc\BarEN, G\ZPosition - 4)
			Else
				EntityOrder(Sc\BarEN, G\ZPosition - 1)
			EndIf
		; Text field
		ElseIf Object.GY_TextField(G\TypeHandle) <> Null
			T.GY_TextField = Object.GY_TextField(G\TypeHandle)
			EntityOrder(T\TextEN, G\ZPosition - 1)
			EntityOrder(T\CursorEN, G\ZPosition - 2)
		EndIf
	Next

End Function


;Interaction Window
;-----------------------------
; Returns True if a window has been closed by the user
Function GY_Window2Closed(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	W.GY_Window2 = Object.GY_Window2(G\TypeHandle)
	If W = Null Then Return False

	If W\Closed = True
		W\Closed = False
		Return True
	Else
		Return False
	EndIf

End Function

; Returns True if a window is currently minimised
Function GY_Window2Minimised(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	W.GY_Window2 = Object.GY_Window2(G\TypeHandle)
	If W = Null Then Return False

	Return W\Minimised

End Function

; Returns true if the given window is the active window
Function GY_Window2Active(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	W.GY_Window2 = Object.GY_Window2(G\TypeHandle)
	If W = Null Then Return False

	If W = Last GY_Window2 Then Return True

End Function

; Brings a window to the front of the Z order
Function GY_ActivateWindow2(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	W.GY_Window2 = Object.GY_Window2(G\TypeHandle)
	If W = Null Then Return False

	; Put it at the front
	Insert W After Last GY_Window2
	;If W\TitleEN <> 0 Then EntityTexture(W\TitleEN, GY_Title)
	If W\TopEN <> 0 Then EntityColor(W\TopEN, GY_BorderR, GY_BorderG, GY_BorderB)
	;EntityColor(W\LeftEN, GY_BorderR, GY_BorderG, GY_BorderB)
	;EntityColor(W\LowerEN, GY_BorderR, GY_BorderG, GY_BorderB)
	;EntityColor(W\RightEN, GY_BorderR, GY_BorderG, GY_BorderB)

	; Reset Z position of every window
	Z = -10
	For Win.GY_Window2 = Each GY_Window2
		Win\Gadget\ZPosition = Z
		EntityOrder(Win\Gadget\EN, Z)
		;If Win\TitleEN <> 0
		;	EntityOrder(Win\TitleEN, Z)
		;	If Win <> W Then EntityTexture(Win\TitleEN, GY_InactiveTitle)
		;EndIf
		If Win\TopEN <> 0 Then EntityOrder(Win\TopEN, Z)
		;EntityOrder(Win\LeftEN, Z)
		;EntityOrder(Win\LowerEN, Z)
		;EntityOrder(Win\RightEN, Z)
		;If Win <> W
			If Win\TopEN <> 0 Then EntityColor(Win\TopEN, GY_InactiveBorderR, GY_InactiveBorderG, GY_InactiveBorderB)
		;	EntityColor(Win\LeftEN, GY_InactiveBorderR, GY_InactiveBorderG, GY_InactiveBorderB)
		;	EntityColor(Win\LowerEN, GY_InactiveBorderR, GY_InactiveBorderG, GY_InactiveBorderB)
		;	EntityColor(Win\RightEN, GY_InactiveBorderR, GY_InactiveBorderG, GY_InactiveBorderB)
		;EndIf
		;If Win\CloseEN <> 0 Then EntityOrder(Win\CloseEN, Z - 1)
		If Win\MinEN <> 0 Then EntityOrder(Win\MinEN, Z - 1)
		;If Win\TitleText <> 0 Then EntityOrder(Win\TitleText, Z - 1)
		Z = Z - 10
	Next

	; Reset Z position of every other gadget
	For G = Each GY_Gadget
		; If it's a child
		If G\Parent <> Null
			G\ZPosition = G\Parent\ZPosition - 5
			If G\EN <> 0 Then GY_RecursiveEntityOrder(G\EN, G\ZPosition)
		; If it's not a child, but not a window either, it always goes on top/bottom
		ElseIf Object.GY_Window(G\TypeHandle) = Null
			If G\OnBottom = True Then G\ZPosition = -2 Else G\ZPosition = -3000
			If G\EN <> 0 Then GY_RecursiveEntityOrder(G\EN, G\ZPosition)
		EndIf

		; If it's a progress bar
		If Object.GY_ProgressBar(G\TypeHandle) <> Null
			P.GY_ProgressBar = Object.GY_ProgressBar(G\TypeHandle)
			EntityOrder(P\BarEN, G\ZPosition - 1)
		; A checkbox
		ElseIf Object.GY_CheckBox(G\TypeHandle) <> Null
			Ch.GY_CheckBox = Object.GY_CheckBox(G\TypeHandle)
			EntityOrder(Ch\LabelText, G\ZPosition)
		; A button
		ElseIf Object.GY_Button(G\TypeHandle) <> Null
			B.GY_Button = Object.GY_Button(G\TypeHandle)
			If B\ComboOwner = True
				G\ZPosition = G\ZPosition - 3
				If G\EN <> 0 Then GY_RecursiveEntityOrder(G\EN, G\ZPosition)
			EndIf
			EntityOrder(B\LabelEN, G\ZPosition - 1)
			EntityOrder(B\T, G\ZPosition - 1)
			EntityOrder(B\B, G\ZPosition - 1)
			EntityOrder(B\R, G\ZPosition - 1)
			EntityOrder(B\L, G\ZPosition - 1)
			EntityOrder(B\TR, G\ZPosition - 1)
			EntityOrder(B\TL, G\ZPosition - 1)
			EntityOrder(B\BR, G\ZPosition - 1)
			EntityOrder(B\BL, G\ZPosition - 1)
		; A slider
		ElseIf Object.GY_Slider(G\TypeHandle) <> Null
			S.GY_Slider = Object.GY_Slider(G\TypeHandle)
			EntityOrder(S\BarEN, G\ZPosition - 1)
			EntityOrder(S\LabelEN, G\ZPosition - 2)
		; A combo box
		ElseIf Object.GY_ComboBox(G\TypeHandle) <> Null
			C.GY_ComboBox = Object.GY_ComboBox(G\TypeHandle)
			EntityOrder(C\ListAreaEN, G\ZPosition - 2)
			EntityOrder(C\LabelEN, G\ZPosition - 1)
			EntityOrder(C\LowerEN, G\ZPosition - 1)
			EntityOrder(C\TopLeftEN, G\ZPosition - 1)
			EntityOrder(C\RightEN, G\ZPosition - 1)
			If C\Selected <> Null Then EntityOrder(C\Selected\LabelEN, G\ZPosition - 1)

			; Items
			If C\OpenStatus = True
				For I.GY_ComboItem = Each GY_ComboItem
					If I\Box = C Then EntityOrder(I\LabelEN, G\ZPosition - 3)
				Next
			EndIf
		; A list box
		ElseIf Object.GY_ListBox(G\TypeHandle) <> Null
			L.GY_ListBox = Object.GY_ListBox(G\TypeHandle)
			EntityOrder(L\BorderEN, G\ZPosition)
			EntityOrder(L\Gadget\EN, G\ZPosition - 1)

			; Items
			For Li.GY_ListItem = Each GY_ListItem
				If Li\Box = L Then EntityOrder(Li\LabelEN, G\ZPosition - 2)
			Next
		; A scrollbar
		ElseIf Object.GY_ScrollBar(G\TypeHandle) <> Null
			Sc.GY_ScrollBar = Object.GY_ScrollBar(G\TypeHandle)
			If Sc\ComboOwner = True
				EntityOrder(Sc\Gadget\EN, G\ZPosition - 3)
				EntityOrder(Sc\BarEN, G\ZPosition - 4)
			Else
				EntityOrder(Sc\BarEN, G\ZPosition - 1)
			EndIf
		; Text field
		ElseIf Object.GY_TextField(G\TypeHandle) <> Null
			T.GY_TextField = Object.GY_TextField(G\TypeHandle)
			EntityOrder(T\TextEN, G\ZPosition - 1)
			EntityOrder(T\CursorEN, G\ZPosition - 2)
		EndIf
	Next

End Function

;----------------------------
;End Edit

; Sets a gadget without a parent to be on top or not
Function GY_DropGadget(Gad, Drop = True)

	G.GY_Gadget = Object.GY_Gadget(Gad)
	If G = Null Then Return False

	G\OnBottom = Drop

	W.GY_Window = First GY_Window
	If W <> Null Then GY_ActivateWindow(Handle(W\Gadget))

End Function

; Gets the X position of a gadget
Function GY_GadgetX#(Gad, GlobalFlag = False)

	G.GY_Gadget = Object.GY_Gadget(Gad)
	If G = Null Then Return 0.0

	X# = G\X#

	; Get actual position if this gadget has a parent
	If G\AbsolutePosition = False And GlobalFlag = True
		Parent.GY_Gadget = G\Parent
		While Parent <> Null
			X# = Parent\X# + (X# * Parent\Width#)
			If Parent\AbsolutePosition = True Then Exit
			Parent = Parent\Parent
		Wend
	EndIf

	Return X#

End Function

; Gets the Y position of a gadget
Function GY_GadgetY#(Gad, GlobalFlag = False)

	G.GY_Gadget = Object.GY_Gadget(Gad)
	If G = Null Then Return 0.0

	Y# = G\Y#

	; Get actual position if this gadget has a parent
	If G\AbsolutePosition = False And GlobalFlag = True
		Parent.GY_Gadget = G\Parent
		While Parent <> Null
			Y# = Parent\Y# + (Y# * Parent\Height#)
			If Parent\AbsolutePosition = True Then Exit
			Parent = Parent\Parent
		Wend
	EndIf

	Return Y#

End Function

; Gets the width of a gadget
Function GY_GadgetWidth#(Gad, GlobalFlag = False)

	G.GY_Gadget = Object.GY_Gadget(Gad)
	If G = Null Then Return 1.0

	W# = G\Width#

	; Get actual position if this gadget has a parent
	If G\AbsoluteSize = False And GlobalFlag = True
		Parent.GY_Gadget = G\Parent
		While Parent <> Null
			W# = W# * Parent\Width#
			If Parent\AbsoluteSize = True Then Exit
			Parent = Parent\Parent
		Wend
	EndIf

	Return W#

End Function

; Gets the height of a gadget
Function GY_GadgetHeight#(Gad, GlobalFlag = False)

	G.GY_Gadget = Object.GY_Gadget(Gad)
	If G = Null Then Return 1.0

	H# = G\Height#

	; Get actual position if this gadget has a parent
	If G\AbsoluteSize = False And GlobalFlag = True
		Parent.GY_Gadget = G\Parent
		While Parent <> Null
			H# = H# * Parent\Height#
			If Parent\AbsoluteSize = True Then Exit
			Parent = Parent\Parent
		Wend
	EndIf

	Return H#

End Function

; Sets the position of a gadget
Function GY_PositionGadget(Gad, X#, Y#, GlobalFlag = False)

	G.GY_Gadget = Object.GY_Gadget(Gad)
	If G = Null Then Return False

	G\X# = X#
	G\Y# = Y#

	If G\EN <> 0

		If G\AbsolutePosition = False And G\Parent <> Null
			; Get actual position if this gadget has a parent
			If GlobalFlag = False
				X# = (X# * GY_GadgetWidth#(Handle(G\Parent), True)) + GY_GadgetX#(Handle(G\Parent), True)
				Y# = (Y# * GY_GadgetHeight#(Handle(G\Parent), True)) + GY_GadgetY#(Handle(G\Parent), True)
			; Get local position if position supplied was global
			Else
				G\X# = (X# - GY_GadgetX#(Handle(G\Parent), True)) / GY_GadgetWidth#(Handle(G\Parent), True)
				G\Y# = (Y# - GY_GadgetY#(Handle(G\Parent), True)) / GY_GadgetHeight#(Handle(G\Parent), True)
			EndIf
		EndIf

		; Move to new position
		PositionEntity(G\EN, (X# * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0) ;(X# * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)


		; Update children
		For Child.GY_Gadget = Each GY_Gadget
			If Child\Parent = G Then GY_PositionGadget(Handle(Child), Child\X#, Child\Y#)
		Next

	EndIf

	Return True

End Function

; Scales a gadget
Function GY_ScaleGadget(GHandle, Width#, Height#, GlobalFlag = False)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	G\Width# = Width#
	G\Height# = Height#

	If G\EN <> 0

		If G\AbsoluteSize = False And G\Parent <> Null
			; Get actual size if this gadget has a parent
			If GlobalFlag = False
				Width# = Width# * GY_GadgetWidth#(Handle(G\Parent), True)
				Height# = Height# * GY_GadgetHeight#(Handle(G\Parent), True)
			; Get local size if size supplied was global
			Else
				G\Width# = Width# / GY_GadgetWidth#(Handle(G\Parent), True)
				G\Height# = Height# / GY_GadgetHeight#(Handle(G\Parent), True)
			EndIf
		EndIf

		; Scale to new size
		W.GY_Window = Object.GY_Window(G\TypeHandle)
		If W <> Null
			If W\UserTexture = 0
				GY_ScaleQuad(G\EN, Width#, Height#)
			Else
				ScaleEntity(G\EN, Width# * 20.0, Height# * 15.0, 1.0)
			EndIf
		Else
			ScaleEntity(G\EN, Width# * 20.0, Height# * 15.0, 1.0)
		EndIf

		; Update children
		For Child.GY_Gadget = Each GY_Gadget
			If Child\Parent = G Then GY_ScaleGadget(Handle(Child), Child\Width#, Child\Height#)
		Next

	EndIf

	Return True

End Function

; Locks or unlocks a gadget
Function GY_LockGadget(GHandle, Lock = True)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	G\Locked = Lock

	Return True

End Function

; Sets the alpha (transparency) of a gadget
Function GY_GadgetAlpha(GHandle, Alpha#, Children = False)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	G\Alpha# = Alpha#

	; Only set entity alpha if parent window is not minimised
	GadgetActive = True
	Parent.GY_Gadget = G
	While Parent <> Null
		If Object.GY_Window(Parent\TypeHandle) <> Null
			W.GY_Window = Object.GY_Window(Parent\TypeHandle)
			If W\Minimised = True Then GadgetActive = False : Exit
		EndIf
		Parent = Parent\Parent
	Wend
	If G\EN <> 0 And GadgetActive = True Then GY_RecursiveEntityAlpha(G\EN, Alpha#)

	; If it's a combo box, do the scrollbar and button
	If Object.GY_ComboBox(G\TypeHandle) <> Null
		C.GY_ComboBox = Object.GY_ComboBox(G\TypeHandle)
		GY_GadgetAlpha(C\ButtonGadget, Alpha#, Children)
		If C\OpenStatus = True Then GY_GadgetAlpha(C\ScrollGadget, Alpha#, Children)
	; If it's a scrollbar, do the buttons
	ElseIf Object.GY_ScrollBar(G\TypeHandle) <> Null
		S.GY_ScrollBar = Object.GY_ScrollBar(G\TypeHandle)
		GY_GadgetAlpha(S\IncButton, Alpha#, Children)
		GY_GadgetAlpha(S\DecButton, Alpha#, Children)
	EndIf

	If Children = True
		; Find any gadget children and do it to them too
		For Child.GY_Gadget = Each GY_Gadget
			If Child\Parent = G Then GY_GadgetAlpha(Handle(Child), Alpha#, True)
		Next
	EndIf

	Return True

End Function

; Sets the user data for a gadget
Function GY_SetGadgetData(GHandle, Dat$)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	G\UserData$ = Dat$

	Return True

End Function

; Retrieves the user data for a gadget
Function GY_GadgetData$(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return ""

	Return G\UserData$

End Function

; Frees a gadget and all its children (RECURSIVE)
Function GY_FreeGadget(GHandle)

	G.GY_Gadget = Object.GY_Gadget(GHandle)
	If G = Null Then Return False

	; Free children
	For Child.GY_Gadget = Each GY_Gadget
		If Child\Parent = G Then GY_FreeGadget(Handle(Child))
	Next

	; If it's a window
	If Object.GY_Window(G\TypeHandle) <> Null
		W.GY_Window = Object.GY_Window(G\TypeHandle)
		If W\LeftEN <> 0 Then FreeEntity(W\LeftEN)
		If W\LowerEN <> 0 Then FreeEntity(W\LowerEN)
		If W\RightEN <> 0 Then FreeEntity(W\RightEN)
		If W\TopEN <> 0 Then FreeEntity(W\TopEN)
		If W\TitleText <> 0 Then GY_Free3DText(W\TitleText)
		If W\MinEN <> 0 Then FreeEntity(W\MinEN)
		If W\CloseEN <> 0 Then FreeEntity(W\CloseEN)
		If W\TitleEN <> 0 Then FreeEntity(W\TitleEN)
		If W\UserTexture <> 0 And W\AutoFreeUserTexture = True Then FreeTexture(W\UserTexture)
		Delete W
	; A checkbox
	ElseIf Object.GY_CheckBox(G\TypeHandle) <> Null
		C.GY_CheckBox = Object.GY_CheckBox(G\TypeHandle)
		If C\LabelText <> 0 Then GY_Free3DText(C\LabelText)
		Delete C
	; A progress bar
	ElseIf Object.GY_ProgressBar(G\TypeHandle) <> Null
		P.GY_ProgressBar = Object.GY_ProgressBar(G\TypeHandle)
		If P\BarEN <> 0 Then FreeEntity(P\BarEN)
		Delete P
	; A button
	ElseIf Object.GY_Button(G\TypeHandle) <> Null
		B.GY_Button = Object.GY_Button(G\TypeHandle)
		If B\LabelEN <> 0 Then GY_Free3DText(B\LabelEN)
		If B\T <> 0 Then FreeEntity(B\T)
		If B\B <> 0 Then FreeEntity(B\B)
		If B\L <> 0 Then FreeEntity(B\L)
		If B\R <> 0 Then FreeEntity(B\R)
		If B\TR <> 0 Then FreeEntity(B\TR)
		If B\TL <> 0 Then FreeEntity(B\TL)
		If B\BR <> 0 Then FreeEntity(B\BR)
		If B\BL <> 0 Then FreeEntity(B\BL)
		If B\UserTexture <> 0 Then FreeTexture(B\UserTexture)
		Delete B
	; A custom button
	ElseIf Object.GY_CustomButton(G\TypeHandle) <> Null
		CB.GY_CustomButton = Object.GY_CustomButton(G\TypeHandle)
		If CB\UpTex <> 0 Then FreeTexture(CB\UpTex)
		If CB\DownTex <> 0 Then FreeTexture(CB\DownTex)
		If CB\HoverTex <> 0 Then FreeTexture(CB\HoverTex)
		Delete CB
	; A label
	ElseIf Object.GY_Label(G\TypeHandle) <> Null
		La.GY_Label = Object.GY_Label(G\TypeHandle)
		If G\EN <> 0 Then GY_Free3DText(G\EN) : G\EN = 0
		Delete La
	; A combo box
	ElseIf Object.GY_ComboBox(G\TypeHandle) <> Null
		Co.GY_ComboBox = Object.GY_ComboBox(G\TypeHandle)
		If Co\LabelEN <> 0 Then GY_Free3DText(Co\LabelEN)
		If Co\TopLeftEN <> 0 Then FreeEntity(Co\TopLeftEN)
		If Co\LowerEN <> 0 Then FreeEntity(Co\LowerEN)
		If Co\RightEN <> 0 Then FreeEntity(Co\RightEN)
		If Co\ButtonGadget <> 0 Then GY_FreeGadget(Co\ButtonGadget)
		; Free its items
		For I.GY_ComboItem = Each GY_ComboItem
			If I\Box = Co Then GY_Free3DText(I\LabelEN) : Delete I
		Next
		Delete Co
	; Listbox
	ElseIf Object.GY_ListBox(G\TypeHandle) <> Null
		L.GY_ListBox = Object.GY_ListBox(G\TypeHandle)
		If L\BorderEN <> 0 Then FreeEntity(L\BorderEN)
		If L\ScrollGadget <> 0 Then GY_FreeGadget(L\ScrollGadget)
		; Free its items
		For Li.GY_ListItem = Each GY_ListItem
			If Li\Box = L Then GY_Free3DText(Li\LabelEN) : Delete Li
		Next
		Delete L
	; A scrollbar
	ElseIf Object.GY_ScrollBar(G\TypeHandle) <> Null
		Sc.GY_ScrollBar = Object.GY_ScrollBar(G\TypeHandle)
		If Sc\BarEN <> 0 Then FreeEntity(Sc\BarEN)
		If Sc\DecButton <> 0 Then GY_FreeGadget(Sc\DecButton)
		If Sc\IncButton <> 0 Then GY_FreeGadget(Sc\IncButton)
		Delete Sc
	; Text field
	ElseIf Object.GY_TextField(G\TypeHandle) <> Null
		T.GY_TextField = Object.GY_TextField(G\TypeHandle)
		If T\TextEN <> 0 Then GY_Free3DText(T\TextEN)
		If T\CursorEN <> 0 Then FreeEntity(T\CursorEN)
		If T\TopLeftEN <> 0 Then FreeEntity(T\TopLeftEN)
		If T\LowerEN <> 0 Then FreeEntity(T\LowerEN)
		If T\RightEN <> 0 Then FreeEntity(T\RightEN)
		Delete T
	EndIf

	If G\EN <> 0 Then FreeEntity(G\EN)
	Delete G

	Return True

End Function

; Scales a GY_Quad mesh without altering the texture scale
Function GY_ScaleQuad(EN, Width#, Height#)

	ScaleEntity(EN, Width# * 20.0, Height# * 15.0, 1.0)
	Surf = GetSurface(EN, 1)
	VertexTexCoords(Surf, 0, 0.0, Height# * 3.0)
	VertexTexCoords(Surf, 1, Width# * 4.0, Height# * 3.0)
	VertexTexCoords(Surf, 2, Width# * 4.0, 0.0)
	VertexTexCoords(Surf, 3, 0.0, 0.0)

End Function

; Reads in a colour from the list
Function GY_ReadColour(F)

	TheLine$ = Replace$(ReadLine$(F), " ", "")
	FirstPos = Instr(TheLine$, ",")
	SecondPos = Instr(TheLine$, ",", FirstPos + 1)
	GY_ReadR = Left$(TheLine$, FirstPos - 1)
	GY_ReadG = Mid$(TheLine$, FirstPos + 1, SecondPos - FirstPos)
	GY_ReadB = Right$(TheLine$, Len(TheLine$) - SecondPos)

End Function

; Sets up all Gooey data
Function GY_Load(Camera)

	GY_Cam = Camera

	; Textures
	GY_Window = LoadTexture(GY_Path$ + "\Window.bmp")
	GY_Window2 = LoadTexture(GY_Path$ + "\Box.png")
	
	;GY_Title = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
	GY_Title = LoadTexture(GY_Path$ + "\WindowTitle.bmp")
	;GY_InactiveTitle = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
	GY_InactiveTitle = LoadTexture(GY_Path$ + "\WindowNoFocusTitle.bmp")
	
	GY_Close = LoadTexture(GY_Path$ + "\WindowClose.bmp", 16 + 32)
	GY_Minimise = LoadTexture(GY_Path$ + "\WindowMinimise.bmp", 16 + 32)
	GY_CloseHeld = LoadTexture(GY_Path$ + "\WindowCloseHeld.bmp", 16 + 32)
	GY_MinimiseHeld = LoadTexture(GY_Path$ + "\WindowMinimiseHeld.bmp", 16 + 32)
	GY_CheckBoxOn = LoadTexture(GY_Path$ + "\CheckBoxOn.bmp", 16 + 32)
	GY_CheckBoxOff = LoadTexture(GY_Path$ + "\CheckBoxOff.bmp", 16 + 32)
	GY_CheckBoxDwn = LoadTexture(GY_Path$ + "\CheckBoxPressed.bmp", 16 + 32)
	GY_ProgBarBackground = LoadTexture(GY_Path$ + "\ProgBarBackground.bmp", 4 + 16 + 32)
	GY_ProgBarForeground = LoadTexture(GY_Path$ + "\ProgBarForeground.bmp", 16 + 32)
	GY_ProgBarBackground2 = LoadTexture(GY_Path$ + "\ProgBarBackground2.bmp", 16 + 32)
	GY_ProgBarForeground2 = LoadTexture(GY_Path$ + "\ProgBarForeground2.bmp", 16 + 32)
	GY_ProgBarForeground3 = LoadTexture(GY_Path$ + "\ProgBarForeground3.bmp", 16 + 32)
	GY_ButtonGradient = LoadTexture(GY_Path$ + "\ButtonGradient.bmp", 16 + 32)
	
	;Button texture paths Cysis145
	GY_BorderTop = LoadTexture(GY_Path$ + "\WindowBorder.png", 4) ;+ 16 + 32
	GY_BorderBottom = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
	GY_BorderRight = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
	GY_BorderLeft = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
	
;	GY_BorderTL = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
;	GY_BorderTR = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
;	GY_BorderBL = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
;	GY_BorderBR = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
;	
;	GY_BorderTopD = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
;	GY_BorderBottomD = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
;	GY_BorderRightD = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
;	GY_BorderLeftD = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
;	
;	GY_BorderTLD = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
;	GY_BorderTRD = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
;	GY_BorderBLD = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
;	GY_BorderBRD = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
;	
;	GY_BorderTopP = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
;	GY_BorderBottomP = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
;	GY_BorderRightP = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
;	GY_BorderLeftP = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
;	
;	GY_BorderTLP = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
;	GY_BorderTRP = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
;	GY_BorderBLP = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
;	GY_BorderBRP = LoadTexture(GY_Path$ + "\WindowBorder.png", 4)
	
	;BackUp Cysis145
	GY_BorderTL = LoadTexture(GY_Path$ + "\ButtonCornerTL.bmp", 4 + 16 + 32)
	GY_BorderTR = LoadTexture(GY_Path$ + "\ButtonCornerTR.bmp", 4 + 16 + 32)
	GY_BorderBL = LoadTexture(GY_Path$ + "\ButtonCornerBL.bmp", 4 + 16 + 32)
	GY_BorderBR = LoadTexture(GY_Path$ + "\ButtonCornerBR.bmp", 4 + 16 + 32)
	GY_BorderTopD = LoadTexture(GY_Path$ + "\ButtonTopD.bmp", 4 + 16 + 32)
	GY_BorderBottomD = LoadTexture(GY_Path$ + "\ButtonBottomD.bmp", 4 + 16 + 32)
	GY_BorderRightD = LoadTexture(GY_Path$ + "\ButtonRightD.bmp", 4 + 16 + 32)
	GY_BorderLeftD = LoadTexture(GY_Path$ + "\ButtonLeftD.bmp", 4 + 16 + 32)
	GY_BorderTLD = LoadTexture(GY_Path$ + "\ButtonCornerTLD.bmp", 4 + 16 + 32)
	GY_BorderTRD = LoadTexture(GY_Path$ + "\ButtonCornerTRD.bmp", 4 + 16 + 32)
	GY_BorderBLD = LoadTexture(GY_Path$ + "\ButtonCornerBLD.bmp", 4 + 16 + 32)
	GY_BorderBRD = LoadTexture(GY_Path$ + "\ButtonCornerBRD.bmp", 4 + 16 + 32)
	GY_BorderTopP = LoadTexture(GY_Path$ + "\ButtonTopP.bmp", 4 + 16 + 32)
	GY_BorderBottomP = LoadTexture(GY_Path$ + "\ButtonBottomP.bmp", 4 + 16 + 32)
	GY_BorderRightP = LoadTexture(GY_Path$ + "\ButtonRightP.bmp", 4 + 16 + 32)
	GY_BorderLeftP = LoadTexture(GY_Path$ + "\ButtonLeftP.bmp", 4 + 16 + 32)
	GY_BorderTLP = LoadTexture(GY_Path$ + "\ButtonCornerTLP.bmp", 4 + 16 + 32)
	GY_BorderTRP = LoadTexture(GY_Path$ + "\ButtonCornerTRP.bmp", 4 + 16 + 32)
	GY_BorderBLP = LoadTexture(GY_Path$ + "\ButtonCornerBLP.bmp", 4 + 16 + 32)
	GY_BorderBRP = LoadTexture(GY_Path$ + "\ButtonCornerBRP.bmp", 4 + 16 + 32)

	
	GY_Scroller = LoadTexture(GY_Path$ + "\ScrollBarForeGround.bmp", 16 + 32)


	; Colours
	F = ReadFile(GY_Path$ + "\Colours.txt")
	If F = 0 Then RuntimeError "File "+Chr(34)+GY_Path$ + "\Colours.txt"+Chr(34) + " was not found!"
		GY_ReadColour(F)
		GY_TitleR          = GY_ReadR : GY_TitleG          = GY_ReadG : GY_TitleB          = GY_ReadB
		GY_ReadColour(F)
		GY_BorderR         = GY_ReadR : GY_BorderG         = GY_ReadG : GY_BorderB         = GY_ReadB
		GY_ReadColour(F)
		GY_InactiveBorderR = GY_ReadR : GY_InactiveBorderG = GY_ReadG : GY_InactiveBorderB = GY_ReadB
		GY_ReadColour(F)
		GY_ButtonUpR       = GY_ReadR : GY_ButtonUpG       = GY_ReadG : GY_ButtonUpB       = GY_ReadB
		GY_ReadColour(F)
		GY_ButtonDownR     = GY_ReadR : GY_ButtonDownG     = GY_ReadG : GY_ButtonDownB     = GY_ReadB
		GY_ReadColour(F)
		GY_ButtonHeldR     = GY_ReadR : GY_ButtonHeldG     = GY_ReadG : GY_ButtonHeldB     = GY_ReadB
		GY_ReadColour(F)
		GY_ComboBackR      = GY_ReadR : GY_ComboBackG      = GY_ReadG : GY_ComboBackB      = GY_ReadB
		GY_ReadColour(F)
		GY_ScrollR         = GY_ReadR : GY_ScrollG         = GY_ReadG : GY_ScrollB         = GY_ReadB
		GY_ReadColour(F)
		GY_TextFieldForeR  = GY_ReadR : GY_TextFieldForeG  = GY_ReadG : GY_TextFieldForeB  = GY_ReadB
		GY_ReadColour(F)
		GY_TextFieldBackR  = GY_ReadR : GY_TextFieldBackG  = GY_ReadG : GY_TextFieldBackB  = GY_ReadB
		GY_ButtonBorderSize# = ReadLine$(F)
		GY_BorderSize# = ReadLine$(F)
	CloseFile(F)

	; Mouse
	GY_Mouse = GY_CreateQuad(GY_Cam)
	GY_MouseTex = LoadTexture(GY_Path$ + "\Mouse.bmp", 1 + 4 + 16 + 32)
	EntityTexture(GY_Mouse, GY_MouseTex)
	EntityFX(GY_Mouse, 1 + 8)
	ScaleMesh(GY_Mouse, 0.5, 0.5, 1.0)
	EntityOrder(GY_Mouse, -3010)
	
	;If InventoryVisible = False And HelpVisible = False And PartyVisible = False And QuestLogVisible = False And TradingVisible = False And SpellsVisible = False And CharStatsVisible = False And LargeMapVisible = False
	;	HideEntity(GY_Mouse)
	;Else 
	;	ShowEntity(GY_Mouse)
	;EndIf
	
	; Fonts
	GY_TitleFont = GY_LoadFont(GY_Path$ + "\Fonts\Title")

	; Sounds
	GY_SClick = LoadSound(GY_Path$ + "\Click.wav")
	GY_SBeep = LoadSound(GY_Path$ + "\Beep.wav")
	SoundVolume( GY_SBeep, DefaultVolume )
	SoundVolume( GY_SClick, DefaultVolume )

End Function

; Unloads everything being used by Gooey
Function GY_Unload()

	; Free gadgets
	For W.GY_Window = Each GY_Window
		GY_FreeGadget(Handle(W\Gadget))
	Next
	For G.GY_Gadget = Each GY_Gadget
		GY_FreeGadget(Handle(G))
	Next

	; Textures
	FreeTexture(GY_Window)
	FreeTexture(GY_Window2)
	FreeTexture(GY_Title)
	FreeTexture(GY_InactiveTitle)
	FreeTexture(GY_Close)
	FreeTexture(GY_Minimise)
	FreeTexture(GY_CloseHeld)
	FreeTexture(GY_MinimiseHeld)
	FreeTexture(GY_CheckBoxOn)
	FreeTexture(GY_CheckBoxOff)
	FreeTexture(GY_CheckBoxDwn)
	FreeTexture(GY_ProgBarBackground)
	FreeTexture(GY_ProgBarForeground)
	FreeTexture(GY_ProgBarBackground2)
	FreeTexture(GY_ProgBarForeground2)
	FreeTexture(GY_ProgBarForeground3)
	FreeTexture(GY_ButtonGradient)
	FreeTexture(GY_BorderTop) : FreeTexture(GY_BorderBottom) : FreeTexture(GY_BorderRight) : FreeTexture(GY_BorderLeft)
	FreeTexture(GY_BorderTL) : FreeTexture(GY_BorderTR) : FreeTexture(GY_BorderBL) : FreeTexture(GY_BorderBR)
	FreeTexture(GY_BorderTopD) : FreeTexture(GY_BorderBottomD) : FreeTexture(GY_BorderRightD) : FreeTexture(GY_BorderLeftD)
	FreeTexture(GY_BorderTLD) : FreeTexture(GY_BorderTRD) : FreeTexture(GY_BorderBLD) : FreeTexture(GY_BorderBRD)
	FreeTexture(GY_BorderTopP) : FreeTexture(GY_BorderBottomP) : FreeTexture(GY_BorderRightP) : FreeTexture(GY_BorderLeftP)
	FreeTexture(GY_BorderTLP) : FreeTexture(GY_BorderTRP) : FreeTexture(GY_BorderBLP) : FreeTexture(GY_BorderBRP)
	FreeTexture(GY_Scroller)

	; Mouse
	FreeTexture(GY_MouseTex)
	FreeEntity(GY_Mouse)

	; Fonts
	GY_FreeFont(GY_TitleFont)

	; Sounds
	FreeSound(GY_SClick)
	FreeSound(GY_SBeep)

End Function

; Updates the whole Gooey GUI
Function GY_Update()

	; Update input
	GY_MouseOverGadget = False
	GY_EnterHit = KeyHit(28) Or KeyHit(156)
	GY_BackSpaceHit = KeyHit(14)
	GY_DeleteHit = KeyHit(211)
	GY_UpHit = KeyHit(200)
	GY_DownHit = KeyHit(208)
	GY_LeftHit = KeyHit(203)
	GY_RightHit = KeyHit(205)
	GY_EndHit = KeyHit(207)
	GY_HomeHit = KeyHit(199)

	If GY_LeftHit = True Then GY_LeftTimer = MilliSecs() + 400
	If GY_RightHit = True Then GY_RightTimer = MilliSecs() + 400
	If GY_BackSpaceHit = True Then GY_BackTimer = MilliSecs() + 600
	
;&&&&&&&&&& AJOUT PAVE NUMERIQUE terrier UUICEO

If KeyHit(79) > 0
  GY_Inkey = Asc("1")
 ElseIf KeyHit(80) > 0
  GY_Inkey = Asc("2")
 ElseIf KeyHit(81) > 0
  GY_Inkey = Asc("3")
 ElseIf KeyHit(82) > 0
  GY_Inkey = Asc("0")
 ElseIf KeyHit(71) > 0
  GY_Inkey = Asc("7")
 ElseIf KeyHit(72) > 0
  GY_Inkey = Asc("8")
 ElseIf KeyHit(73) > 0
  GY_Inkey = Asc("9")
 ElseIf KeyHit(75) > 0
  GY_Inkey = Asc("4")
 ElseIf KeyHit(76) > 0
  GY_Inkey = Asc("5")
 ElseIf KeyHit(77) > 0
  GY_Inkey = Asc("6")
 ElseIf KeyHit(181) > 0
  GY_Inkey = Asc("/")
 ElseIf KeyHit(83) > 0
  GY_Inkey = Asc(".")
 Else
  GY_InKey = GetKey()
 EndIf


	If MouseDown(1) = False And GY_LeftWasDown = True Then GY_LeftClick = True Else GY_LeftClick = False
	GY_LeftWasDown = MouseDown(1)
	If MouseDown(2) = False And GY_RightWasDown = True Then GY_RightClick = True Else GY_RightClick = False
	GY_RightWasDown = MouseDown(2)

	GY_MouseXSpeed = MouseXSpeed()
	GY_MouseYSpeed = MouseYSpeed()

	;WideScreen Ratio, 4:3, 5:3 Ramoida
    fix# = 0.0 
    mult2# =1.0 
    mult# = 1.0 
    ;Change variables If Ratio is 16:9
	If ResolutionType = 1
		fix# = 0.167
		mult2# = 0.167 
	mult# = 1.333 
	EndIf 
    ; Turn mouse position/speed into screen co-ordinates 
    GY_MouseX# = Float#(MouseX()) / Float#(GraphicsWidth()) * mult# - fix# 
    GY_MouseY# = Float#(MouseY()) / Float#(GraphicsHeight()) 
    GY_MXSpeed# = Float#(GY_MouseXSpeed) * mult2# / Float#(GraphicsWidth()) 
    GY_MYSpeed# = Float#(GY_MouseYSpeed) / Float#(GraphicsHeight())

	; Position mouse mesh
	PositionEntity(GY_Mouse, (GY_MouseX# * 20.0) - 10.0, (GY_MouseY# * -15.0) + 7.5, 10.0)

	; Update windows
	W.GY_Window = Last GY_Window
	LastWindow = False
	While W <> Null And LastWindow = False
	
;Move the Mouse Collision zone for window moving down. Cysis145 [#~]

		; If title bar is being held and mouse is still down, move the window
		If GY_HeldGadget = W\Gadget And GY_HeldData = 3
		If Resolutiontype = 1
			NewX# = GY_GadgetX#(Handle(W\Gadget)) + GY_MXSpeed# * 8.0
			NewY# = GY_GadgetY#(Handle(W\Gadget)) + GY_MYSpeed# * 1.0
		Else
			NewX# = GY_GadgetX#(Handle(W\Gadget)) + GY_MXSpeed#
			NewY# = GY_GadgetY#(Handle(W\Gadget)) + GY_MYSpeed#
		EndIf	
			GY_PositionGadget(Handle(W\Gadget), NewX#, NewY#)
		EndIf

		; Get window edges
		WindowX# = W\Gadget\X#
		WindowY# = W\Gadget\Y#
		WindowWidth# = W\Gadget\Width#
		WindowHeight# = W\Gadget\Height#
		If W\TitleEN <> 0
			WindowY# = WindowY# - 0.03 ;-0.03
			WindowHeight# = WindowHeight# + 0.03
		EndIf
		If W\Minimised = True Then WindowHeight# = 0.03

		; Check if mouse is within window and window is visible/unlocked
		If GY_MouseX# > WindowX# And GY_MouseY# > WindowY# And W\Gadget\Alpha# > 0.0001
			If GY_MouseX# < WindowX# + WindowWidth# And GY_MouseY# < WindowY# + WindowHeight# And W\Gadget\Locked = False

				GY_MouseOverGadget = True

				; Activate
				If MouseDown(1) And GY_HeldGadget = Null And GY_Modal = 0 Then GY_ActivateWindow(Handle(W\Gadget)) : LastWindow = True

				; Title bar buttons
				If W\TitleEN <> 0 And W\Gadget\Locked = False

					; Mouse over close
					If W\CloseEN <> 0
						EntityTexture(W\CloseEN, GY_Close)
						ClseX# = (WindowX# + WindowWidth#) - 0.02
						ClseY# = (WindowY# + 0.025) - 0.02
						If GY_MouseX# > ClseX# And GY_MouseY# > ClseY# And GY_MouseX# < ClseX# + 0.02 And GY_MouseY# < ClseY# + 0.02
							; Mouse down - switch to held texture
							If MouseDown(1) And (GY_HeldGadget = Null Or (GY_HeldGadget = W\Gadget And GY_HeldData = 1))
								GY_HeldGadget = W\Gadget
								GY_HeldData = 1
								EntityTexture(W\CloseEN, GY_CloseHeld)
							; Mouse lifted but was held here - close window
							ElseIf GY_HeldGadget = W\Gadget And GY_HeldData = 1
								W\Closed = True
							EndIf
						EndIf
					EndIf

					; Mouse over minimise
					If W\MinEN <> 0
						EntityTexture(W\MinEN, GY_Minimise)
						If W\CloseEN <> 0
							MinX# = (WindowX# + WindowWidth#) - 0.045
						Else
							MinX# = (WindowX# + WindowWidth#) - 0.02
						EndIf
						MinY# = (WindowY# + 0.025) - 0.02
						If GY_MouseX# > MinX# And GY_MouseY# > MinY# And GY_MouseX# < MinX# + 0.02 And GY_MouseY# < MinY# + 0.02
							; Mouse down - switch to held texture
							If MouseDown(1) And (GY_HeldGadget = Null Or (GY_HeldGadget = W\Gadget And GY_HeldData = 2))
								GY_HeldGadget = W\Gadget
								GY_HeldData = 2
								EntityTexture(W\MinEN, GY_MinimiseHeld)
							; Mouse lifted but was held here - minimise window
							ElseIf GY_HeldGadget = W\Gadget And GY_HeldData = 2
								W\Minimised = Not W\Minimised
								If W\Minimised = True
									If W\TopEN <> 0 Then EntityAlpha(W\TopEN, 0.0)
									EntityAlpha(W\LeftEN, 0.0)
									EntityAlpha(W\LowerEN, 0.0)
									EntityAlpha(W\RightEN, 0.0)
									GY_GadgetVisibility(W\Gadget, False)
									If W\TitleEN <> 0 Then EntityAlpha(W\TitleEN, W\Gadget\Alpha#)
									If W\CloseEN <> 0 Then EntityAlpha(W\CloseEN, W\Gadget\Alpha#)
									If W\MinEN <> 0 Then EntityAlpha(W\MinEN, W\Gadget\Alpha#)
									If W\TitleText <> 0 Then EntityAlpha(W\TitleText, W\Gadget\Alpha#)
								Else
									If W\TopEN <> 0 Then EntityAlpha(W\TopEN, W\Gadget\Alpha#)
									EntityAlpha(W\LeftEN, W\Gadget\Alpha#)
									EntityAlpha(W\LowerEN, W\Gadget\Alpha#)
									EntityAlpha(W\RightEN, W\Gadget\Alpha#)
									GY_GadgetVisibility(W\Gadget, True)
								EndIf
							EndIf
						EndIf
					EndIf

					; If the mouse is over the title bar and down, set it to being held
					If GY_MouseY# < WindowY# + 0.03 And MouseDown(1) And GY_HeldGadget = Null
						GY_HeldGadget = W\Gadget
						GY_HeldData = 3
					EndIf
				EndIf

			EndIf
		EndIf

		; Advance to next window
		W = Before W
	Wend

	; Check combo box items
	ComboBoxOpen.GY_Gadget = Null
	For I.GY_ComboItem = Each GY_ComboItem
		If I\Visible = True
			ComboBoxOpen = I\Box\Gadget
			EntityColor(I\LabelEN, I\Box\R, I\Box\G, I\Box\B)
			If GY_MouseX# > I\X# And GY_MouseX# < I\X# + I\Width#
				If GY_MouseY# > I\Y# And GY_MouseY# < I\Y# + 0.03
					GY_MouseOverGadget = True

					; Hover mouse
					EntityColor(I\LabelEN, 255 - I\Box\R, 255 - I\Box\G, 255 - I\Box\B)

					; Selected!
					If GY_LeftClick
						I\Box\Selected = I
						GY_SetButtonState(I\Box\ButtonGadget, False)
					EndIf
				EndIf
			EndIf
		EndIf
	Next

	; Process all other gadgets
	For G.GY_Gadget = Each GY_Gadget
		; Make sure it's not a window and it's visible
		If Object.GY_Window(G\TypeHandle) = Null And G\Alpha# > 0.0001
			; Check it's not a child of an inactive window
			GadgetActive = True
			Parent.GY_Gadget = G\Parent
			While Parent <> Null
				If Object.GY_Window(Parent\TypeHandle) <> Null
					W = Object.GY_Window(Parent\TypeHandle)
					If W <> Last GY_Window Or Parent\Locked = True Then GadgetActive = False : Exit
				EndIf
				Parent = Parent\Parent
			Wend

			If G\OnBottom = True And GY_MouseOverGadget = True Then GadgetActive = False

			; If a combo box is open and this gadget isn't it or its scrollbar, skip
			If ComboBoxOpen <> Null
				Co.GY_ComboBox = Object.GY_ComboBox(ComboBoxOpen\TypeHandle)
				If G <> ComboBoxOpen And G <> Object.GY_Gadget(Co\ScrollGadget) And G <> Object.GY_Gadget(Co\ButtonGadget)
					GadgetActive = False
				EndIf
			EndIf

			; If it's active, process it
			If GadgetActive = True
				; Get bounds
				MinX# = GY_GadgetX#(Handle(G), True)
				MaxX# = GY_GadgetX#(Handle(G), True) + GY_GadgetWidth#(Handle(G), True)
				MinY# = GY_GadgetY#(Handle(G), True)
				MaxY# = GY_GadgetY#(Handle(G), True) + GY_GadgetHeight#(Handle(G), True)

				; List box
				If Object.GY_ListBox(G\TypeHandle) <> Null
					L.GY_ListBox = Object.GY_ListBox(G\TypeHandle)

					MaxItems = Floor#(GY_GadgetHeight#(Handle(G), True) / 0.035)

					; Change visible items if scrollbar moved
					If L\ScrollGadget <> 0
						ScrollVal# = GY_ScrollBarValue#(L\ScrollGadget)
						If Abs(L\ScrollValue# - ScrollVal#) > 0.0001
							L\FirstItem = Int(ScrollVal# * Float#((L\TotalItems - MaxItems) + 1))
							If L\Selected <> Null
								GY_UpdateListBox(Handle(G), L\Selected\Dat$)
							Else
								GY_UpdateListBox(Handle(G), "")
							EndIf
						EndIf
					EndIf

					; Select items with mouse
					If GY_MouseX# > MinX# And GY_MouseY# > MinY# And GY_MouseX# < MaxX# And GY_MouseY# < MaxY#
						GY_MouseOverGadget = True
						If MouseDown(1) And (GY_HeldGadget = Null Or GY_HeldGadget = G) And G\Locked = False
							GY_HeldGadget = G : GY_HeldData = 1
							Item = Floor#((GY_MouseY# - MinY#) / 0.035)
							Count = -1
							For LBI.GY_ListItem = Each GY_ListItem
								If LBI\Box = L
									Count = Count + 1
									If Count = Item + L\FirstItem
										GY_UpdateListBox(Handle(G), LBI\Dat$)
										Exit
									EndIf
								EndIf
							Next
						EndIf
					EndIf

				; Combo Box
				ElseIf Object.GY_ComboBox(G\TypeHandle) <> Null
					Co.GY_ComboBox = Object.GY_ComboBox(G\TypeHandle)

					; Adjust size for main box only
					MaxY# = MinY# + 0.03

					; Close if another gadget held
					If GY_HeldGadget <> G And GY_HeldGadget <> Null And GY_HeldGadget <> Object.GY_Gadget(Co\ButtonGadget)
						G2.GY_Gadget = Object.GY_Gadget(Co\ScrollGadget)
						If GY_HeldGadget <> G2
							If Co\NoScrollbar = False
								Sc.GY_ScrollBar = Object.GY_ScrollBar(G2\TypeHandle)
								If GY_HeldGadget <> Object.GY_Gadget(Sc\DecButton) And GY_HeldGadget <> Object.GY_Gadget(Sc\IncButton)
									GY_SetButtonState(Co\ButtonGadget, False)
								EndIf
							Else
								GY_SetButtonState(Co\ButtonGadget, False)
							EndIf
						EndIf
					EndIf

					; Change visible items if scrollbar moved
					If Co\OpenStatus = True And Co\NoScrollbar = False
						ScrollVal# = GY_ScrollBarValue#(Co\ScrollGadget)
						If Co\ScrollValue# - ScrollVal# < -0.05
							Co\FirstItem = Co\FirstItem + 1
							Co\ScrollValue# = ScrollVal#
							MaxItems = Int((GY_GadgetHeight#(Handle(G), True) - 0.03) / 0.03) - 1
							If Co\FirstItem > Co\TotalItems - MaxItems
								Co\FirstItem = Co\TotalItems - MaxItems
							Else
								Co\OpenStatus = False
							EndIf
						ElseIf Co\ScrollValue# - ScrollVal# > 0.05
							Co\FirstItem = Co\FirstItem - 1
							Co\ScrollValue# = ScrollVal#
							If Co\FirstItem < 0
								Co\FirstItem = 0
							Else
								Co\OpenStatus = False
							EndIf
						EndIf
					EndIf

					; Check if it's been opened/closed
					If Co\OpenStatus <> GY_ButtonDown(Co\ButtonGadget)
						; Opened
						If GY_ButtonDown(Co\ButtonGadget) = True
							Co\OpenStatus = True
							; Show the scrollbar
							If Co\NoScrollbar = False
								GY_GadgetAlpha(Co\ScrollGadget, G\Alpha#)
								ScrollGad.GY_Gadget = Object.GY_Gadget(Co\ScrollGadget)
								Scroll.GY_ScrollBar = Object.GY_ScrollBar(ScrollGad\TypeHandle)
								GY_RecursiveEntityOrder(ScrollGad\EN, ScrollGad\ZPosition - 3)
								GY_RecursiveEntityOrder(Scroll\BarEN, ScrollGad\ZPosition - 4)
								ButtonGad.GY_Gadget = Object.GY_Gadget(Scroll\IncButton)
								GY_RecursiveEntityOrder(ButtonGad\EN, G\ZPosition - 3)
								Button.GY_Button = Object.GY_Button(ButtonGad\TypeHandle)
								GY_RecursiveEntityOrder(Button\LabelEN, G\ZPosition - 4)
								ButtonGad.GY_Gadget = Object.GY_Gadget(Scroll\DecButton)
								GY_RecursiveEntityOrder(ButtonGad\EN, G\ZPosition - 3)
								Button.GY_Button = Object.GY_Button(ButtonGad\TypeHandle)
								GY_RecursiveEntityOrder(Button\LabelEN, G\ZPosition - 4)
							EndIf
							; Show background entity
							ShowEntity(Co\ListAreaEN)
							; Position all items that fit within the height
							ShowEntity(Co\LabelEN)
							ItemsToSkip = Co\FirstItem
							ItemsSkipped = 0
							Y# = GY_GadgetY#(Handle(G), True) + 0.005
							MaxY# = GY_GadgetY#(Handle(G), True) + (GY_GadgetHeight#(Handle(G), True) - 0.03)
							For I.GY_ComboItem = Each GY_ComboItem
								If I\Box = Co
									If ItemsToSkip > 0
										ItemsToSkip = ItemsToSkip - 1
										ItemsSkipped = ItemsSkipped + 1
										I\Visible = False : HideEntity(I\LabelEN)
									Else
										Y# = Y# + 0.03
										If Y# >= MaxY#
											ItemsSkipped = ItemsSkipped + 1
											I\Visible = False : HideEntity(I\LabelEN)
										Else
											I\Y# = Y#
											I\X# = GY_GadgetX#(Handle(G), True)
											EntityParent(I\LabelEN, GY_Cam)
											PositionEntity(I\LabelEN, (I\X# * 20.0) - 10.0, (I\Y# * -15.0) + 7.5, 10.0)
											EntityParent(I\LabelEN, G\EN)
											EntityColor(I\LabelEN, I\Box\R, I\Box\G, I\Box\B)
											EntityOrder(I\LabelEN, G\ZPosition - 3)
											ShowEntity(I\LabelEN)
											I\Visible = True
										EndIf
									EndIf
								EndIf
							Next
							; Set the scrollbar position and interval
							If Co\NoScrollbar = False
								GY_SetScrollBarInterval(Co\ScrollGadget, 1.0 / Float#(ItemsSkipped + 1))
								Co\ScrollValue# = Float#(Co\FirstItem) / Float#(ItemsSkipped + 1)
								GY_UpdateScrollBar(Co\ScrollGadget, Co\ScrollValue#)
							EndIf
						; Closed
						Else
							Co\OpenStatus = False
							Co\FirstItem = 0
							; Hide scrollbar
							If Co\NoScrollbar = False Then GY_GadgetAlpha(Co\ScrollGadget, 0.0)
							; Hide background entity
							HideEntity(Co\ListAreaEN)
							; Hide items
							ShowEntity(Co\LabelEN)
							For I.GY_ComboItem = Each GY_ComboItem
								If I\Box = Co
									I\Visible = False
									; If it's the selected one, leave it visible and position it as the title
									If I = I\Box\Selected
										HideEntity(Co\LabelEN)
										EntityParent(I\LabelEN, GY_Cam)
										X# = GY_GadgetX#(Handle(G), True)
										Y# = GY_GadgetY#(Handle(G), True)
										PositionEntity(I\LabelEN, (X# * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
										EntityColor(I\LabelEN, I\Box\R, I\Box\G, I\Box\B)
										EntityParent(I\LabelEN, G\EN)
										EntityOrder(I\LabelEN, G\ZPosition - 2)
									Else
										HideEntity(I\LabelEN)
									EndIf
								EndIf
							Next
						EndIf
					EndIf

					; Catch clicks on main box section
					If GY_MouseX# > MinX# And GY_MouseY# > MinY# And GY_MouseX# < MaxX# And GY_MouseY# < MaxY#
						GY_MouseOverGadget = True
						If MouseDown(1) And (GY_HeldGadget = Null Or GY_HeldGadget = G) And G\Locked = False
							GY_HeldGadget = G
							GY_HeldData = 1
						EndIf
					EndIf
				; Checkbox
				ElseIf Object.GY_CheckBox(G\TypeHandle) <> Null
					C.GY_CheckBox = Object.GY_CheckBox(G\TypeHandle)
					GY_UpdateCheckBox(Handle(G), C\State)
					If GY_MouseX# > MinX# And GY_MouseY# > MinY# And GY_MouseX# < MaxX# And GY_MouseY# < MaxY#
						GY_MouseOverGadget = True
						If MouseDown(1) And (GY_HeldGadget = Null Or GY_HeldGadget = G) And G\Locked = False
							GY_HeldGadget = G
							GY_HeldData = 1
							EntityTexture(G\EN, GY_CheckBoxDwn)
						ElseIf GY_HeldGadget = G
							GY_UpdateCheckBox(Handle(G), Not C\State)
							C\Hits = C\Hits + 1
						EndIf
					EndIf
				; Button
				ElseIf Object.GY_Button(G\TypeHandle) <> Null
					B.GY_Button = Object.GY_Button(G\TypeHandle)
					If B\State = True And B\Toggle = True
						;EntityTexture(B\T, GY_BorderTopD)
						;EntityTexture(B\B, GY_BorderBottomD)
						;EntityTexture(B\R, GY_BorderRightD)
						;EntityTexture(B\L, GY_BorderLeftD)
						;EntityTexture(B\TR, GY_BorderTRD)
						;EntityTexture(B\TL, GY_BorderTLD)
						;EntityTexture(B\BR, GY_BorderBRD)
						;EntityTexture(B\BL, GY_BorderBLD)
						If B\UserTexture = 0
							EntityColor(G\EN, GY_ButtonDownR, GY_ButtonDownG, GY_ButtonDownB)
						Else
							EntityColor(G\EN, 200, 200, 200)
						EndIf
					Else
						EntityTexture(B\T, GY_BorderTop)
						EntityTexture(B\B, GY_BorderBottom)
						EntityTexture(B\R, GY_BorderRight)
						EntityTexture(B\L, GY_BorderLeft)
						EntityTexture(B\TR, GY_BorderTR)
						EntityTexture(B\TL, GY_BorderTL)
						EntityTexture(B\BR, GY_BorderBR)
						EntityTexture(B\BL, GY_BorderBL)
						If B\UserTexture = 0
							EntityColor(G\EN, GY_ButtonUpR, GY_ButtonUpG, GY_ButtonUpB)
						Else
							EntityColor(G\EN, 255, 255, 255)
						EndIf
					EndIf
					If GY_MouseX# > MinX# And GY_MouseY# > MinY# And GY_MouseX# < MaxX# And GY_MouseY# < MaxY#
						If G\Locked = False
							;EntityTexture(B\T, GY_BorderTopP)
							;EntityTexture(B\B, GY_BorderBottomP)
							;EntityTexture(B\R, GY_BorderRightP)
							;EntityTexture(B\L, GY_BorderLeftP)
							;EntityTexture(B\TR, GY_BorderTRP)
							;EntityTexture(B\TL, GY_BorderTLP)
							;EntityTexture(B\BR, GY_BorderBRP)
							;EntityTexture(B\BL, GY_BorderBLP)
						EndIf
						GY_MouseOverGadget = True
						If (MouseDown(1) Or MouseDown(2)) And (GY_HeldGadget = Null Or GY_HeldGadget = G) And G\Locked = False
							GY_HeldGadget = G
							If MouseDown(1) Then GY_HeldData = 1 Else GY_HeldData = 2
							; Set to held state
							EntityTexture(B\T, GY_BorderTop)
							EntityTexture(B\B, GY_BorderBottom)
							EntityTexture(B\R, GY_BorderRight)
							EntityTexture(B\L, GY_BorderLeft)
							EntityTexture(B\TR, GY_BorderTR)
							EntityTexture(B\TL, GY_BorderTL)
							EntityTexture(B\BR, GY_BorderBR)
							EntityTexture(B\BL, GY_BorderBL)
							If B\UserTexture = 0
								EntityColor(G\EN, GY_ButtonHeldR, GY_ButtonHeldG, GY_ButtonHeldB)
							Else
								EntityColor(G\EN, 150, 150, 150)
							EndIf
							If B\Toggle = False Then B\State = True
						ElseIf GY_HeldGadget = G
							PlaySound(GY_SBeep)

							; If it's toggling, change the down state
							If B\Toggle = True Then B\State = Not B\State Else B\State = False

							; Generate a click
							If GY_HeldData = 1
								B\Clicked = True
							Else
								B\RightClicked = True
							EndIf

							; Set new state
							If B\State = True
								EntityTexture(B\T, GY_BorderTopD)
								EntityTexture(B\B, GY_BorderBottomD)
								EntityTexture(B\R, GY_BorderRightD)
								EntityTexture(B\L, GY_BorderLeftD)
								EntityTexture(B\TR, GY_BorderTRD)
								EntityTexture(B\TL, GY_BorderTLD)
								EntityTexture(B\BR, GY_BorderBRD)
								EntityTexture(B\BL, GY_BorderBLD)
								If B\UserTexture = 0
									EntityColor(G\EN, GY_ButtonDownR, GY_ButtonDownG, GY_ButtonDownB)
								Else
									EntityColor(G\EN, 200, 200, 200)
								EndIf
							Else
								EntityTexture(B\T, GY_BorderTop)
								EntityTexture(B\B, GY_BorderBottom)
								EntityTexture(B\R, GY_BorderRight)
								EntityTexture(B\L, GY_BorderLeft)
								EntityTexture(B\TR, GY_BorderTR)
								EntityTexture(B\TL, GY_BorderTL)
								EntityTexture(B\BR, GY_BorderBR)
								EntityTexture(B\BL, GY_BorderBL)
								If B\UserTexture = 0
									EntityColor(G\EN, GY_ButtonUpR, GY_ButtonUpG, GY_ButtonUpB)
								Else
									EntityColor(G\EN, 255, 255, 255)
								EndIf
							EndIf
						EndIf
					ElseIf B\Toggle = False And GY_HeldGadget <> G
						B\State = False
					EndIf
				; Custom button
				ElseIf Object.GY_CustomButton(G\TypeHandle) <> Null
					CB.GY_CustomButton = Object.GY_CustomButton(G\TypeHandle)
					If CB\State = True And CB\Toggle = True
						EntityTexture(G\EN, CB\DownTex)
					Else
						EntityTexture(G\EN, CB\UpTex)
					EndIf
					If GY_MouseX# > MinX# And GY_MouseY# > MinY# And GY_MouseX# < MaxX# And GY_MouseY# < MaxY#
						If G\Locked = False Then EntityTexture(G\EN, CB\HoverTex)
						GY_MouseOverGadget = True
						If (MouseDown(1) Or MouseDown(2)) And (GY_HeldGadget = Null Or GY_HeldGadget = G) And G\Locked = False
							GY_HeldGadget = G
							If MouseDown(1) Then GY_HeldData = 1 Else GY_HeldData = 2
							; Set to held state
							EntityTexture(G\EN, CB\UpTex)
							If CB\Toggle = False Then CB\State = True
						ElseIf GY_HeldGadget = G
							PlaySound(GY_SBeep)

							; If it's toggling, change the down state
							If CB\Toggle = True Then CB\State = Not CB\State Else CB\State = False

							; Generate a click
							If GY_HeldData = 1
								CB\Clicked = True
							Else
								CB\RightClicked = True
							EndIf

							; Set new state
							If CB\State = True
								EntityTexture(G\EN, CB\DownTex)
							Else
								EntityTexture(G\EN, CB\UpTex)
							EndIf
						EndIf
					ElseIf CB\Toggle = False And GY_HeldGadget <> G
						CB\State = False
					EndIf
				; Slider
				ElseIf Object.GY_Slider(G\TypeHandle) <> Null
					S.GY_Slider = Object.GY_Slider(G\TypeHandle)
					If GY_MouseX# > MinX# And GY_MouseY# > MinY# And GY_MouseX# < MaxX# And GY_MouseY# < MaxY#
						GY_MouseOverGadget = True
						If MouseDown(1) And (GY_HeldGadget = Null Or GY_HeldGadget = G) And G\Locked = False
							GY_HeldGadget = G
							GY_HeldData = 1
							; Change slider value
							Proportion# = (GY_MouseX# - MinX#) / (MaxX# - MinX#)
							GY_UpdateSlider(Handle(G), S\Min# + (Proportion# * (S\Max# - S\Min#)))
						EndIf
					EndIf
				; Progress bar
				ElseIf Object.GY_ProgressBar(G\TypeHandle) <> Null
					If GY_MouseX# > MinX# And GY_MouseY# > MinY# And GY_MouseX# < MaxX# And GY_MouseY# < MaxY#
						GY_MouseOverGadget = True
					EndIf
				; Scrollbar
				ElseIf Object.GY_ScrollBar(G\TypeHandle) <> Null
					Sc.GY_ScrollBar = Object.GY_ScrollBar(G\TypeHandle)
					If GY_MouseX# > MinX# And GY_MouseY# > MinY# And GY_MouseX# < MaxX# And GY_MouseY# < MaxY#
						GY_MouseOverGadget = True
						If MouseDown(1) And (GY_HeldGadget = Null Or GY_HeldGadget = G) And G\Locked = False
							GY_HeldGadget = G
							GY_HeldData = 1
							; Change value
							If Sc\Vertical = False
								NewVal# = (GY_MouseX# - MinX#) / (MaxX# - MinX#)
							Else
								NewVal# = (GY_MouseY# - MinY#) / (MaxY# - MinY#)
							EndIf
							NumberOfIntervals# = (NewVal# / Sc\Interval#) - Float#(Int((NewVal# / Sc\Interval#)))
							NewVal# = NewVal# - (NumberOfIntervals# * Sc\Interval#)
							GY_UpdateScrollBar(Handle(G), NewVal#)
						EndIf
					EndIf

					; Buttons
					If GY_ButtonHit(Sc\DecButton) Then GY_UpdateScrollBar(Handle(G), Sc\Value# - Sc\Interval#)
					If GY_ButtonHit(Sc\IncButton) Then GY_UpdateScrollBar(Handle(G), Sc\Value# + Sc\Interval#)
				; Text field
				ElseIf Object.GY_TextField(G\TypeHandle) <> Null
					T.GY_TextField = Object.GY_TextField(G\TypeHandle)
					; Get focus
					If GY_MouseX# > MinX# And GY_MouseY# > MinY# And GY_MouseX# < MaxX# And GY_MouseY# < MaxY#
						GY_MouseOverGadget = True
						If MouseDown(1) And GY_HeldGadget = Null And G\Locked = False
							; Remove focus from old text field
							If GY_ActiveTextField <> Null Then HideEntity(GY_ActiveTextField\CursorEN)

							; Set focus to this text field
							GY_HeldGadget = Null
							GY_ActiveTextField = T
							ShowEntity(T\CursorEN)
							T\CursorChar = Len(T\Dat$)
							GY_UpdateTextField(Handle(T\Gadget), T\Dat$)
						EndIf
					EndIf

					If T = GY_ActiveTextField
						; Move cursor left/right
						If GY_RightHit = True Or (KeyDown(205) And MilliSecs() - GY_RightTimer > 50)
							If T\CursorChar < Len(T\Dat$)
								T\CursorChar = T\CursorChar + 1
								If GY_RightHit = False Then GY_RightTimer = MilliSecs()
							EndIf
						ElseIf GY_LeftHit = True Or (KeyDown(203) And MilliSecs() - GY_LeftTimer > 50)
							If T\CursorChar > 0
								T\CursorChar = T\CursorChar - 1
								If T\CursorChar < T\FirstChar Then GY_UpdateTextField(Handle(T\Gadget), T\Dat$)
								If GY_LeftHit = False Then GY_LeftTimer = MilliSecs()
							EndIf
						EndIf

						; Home
						If GY_HomeHit = True
							T\CursorChar = 0
							GY_UpdateTextField(Handle(T\Gadget), T\Dat$)
						; End
						ElseIf GY_EndHit = True
							T\CursorChar = Len(T\Dat$)
							GY_UpdateTextField(Handle(T\Gadget), T\Dat$)
						EndIf

						; Backspace
						If (GY_BackSpaceHit = True Or (KeyDown(14) And MilliSecs() > GY_BackTimer)) And T\CursorChar > 0
							Dat$ = Left$(T\Dat$, T\CursorChar - 1)
							If Len(T\Dat$) - T\CursorChar > 0 Then Dat$ = Dat$ + Right$(T\Dat$, Len(T\Dat$) - T\CursorChar)
							T\CursorChar = T\CursorChar - 1
							GY_UpdateTextField(Handle(T\Gadget), Dat$)
						; Delete
						ElseIf GY_DeleteHit = True And T\CursorChar < Len(T\Dat$)
							Dat$ = Left$(T\Dat$, T\CursorChar) + Right$(T\Dat$, Len(T\Dat$) - (T\CursorChar + 1))
							GY_UpdateTextField(Handle(T\Gadget), Dat$)
						EndIf

						; Receive input
						If Len(T\Dat$) < T\MaxLen
							If (GY_InKey >= 32 And GY_InKey <= 126) Or GY_InKey >= 128
								; Validate input
								Allowed = True
								If T\AllowedInput = 1
									If GY_InKey >= 48 And GY_InKey <= 57 Then Allowed = False
								ElseIf T\AllowedInput = 2
									If GY_InKey < 48 Or GY_InKey > 57 Then Allowed = False
								ElseIf T\AllowedInput = 3
									If (GY_InKey < 48 Or GY_InKey > 57) And GY_InKey <> 46 Then Allowed = False
								ElseIf T\AllowedInput = 4 ; Account Name - Username Verification
									If (GY_InKey < 48 Or GY_InKey > 57) And (GY_InKey < 65 Or GY_InKey > 90) And (GY_InKey < 97 Or GY_InKey > 122) And GY_InKey <> 95 And GY_InKey < 192 Then allowed = False
								ElseIf T\AllowedInput = 5 ; Password Verification
									If (GY_InKey < 48 Or GY_InKey > 57) And (GY_InKey < 65 Or GY_InKey > 90) And (GY_InKey < 97 Or GY_InKey > 122) And GY_InKey <> 46 And GY_InKey <> 95 And GY_InKey < 192 Then allowed = False
								ElseIf T\AllowedInput = 6 ; EMail Verification
									If (GY_InKey < 48 Or GY_InKey > 57) And (GY_InKey < 64 Or GY_InKey > 90) And (GY_InKey < 97 Or GY_InKey > 122) And GY_InKey <> 42 And GY_InKey <> 43 And GY_InKey <> 45 And GY_InKey <> 46 And GY_InKey <> 61 And GY_InKey <> 95 And GY_InKey < 192 Then allowed = False
								EndIf
								; Add input to text field
								If Allowed
									Dat$ = Left$(T\Dat$, T\CursorChar) + Chr$(GY_InKey) + Right$(T\Dat$, Len(T\Dat$) - T\CursorChar)
									T\CursorChar = T\CursorChar + 1
									GY_UpdateTextField(Handle(T\Gadget), Dat$)
								EndIf
							EndIf
						EndIf

						; Remove focus on enter
						If GY_EnterHit Then T\Hit = T\Hit + 1 : GY_ActiveTextField = Null : HideEntity(T\CursorEN) : GY_EnterHit = 0

						; Update cursor
						EntityParent(T\CursorEN, GY_Cam)
						X# = GY_GadgetX#(Handle(T\Gadget), True)
						Y# = GY_GadgetY#(Handle(T\Gadget), True)
						Dat$ = Mid$(T\Dat$, T\FirstChar + 1, T\CursorChar - T\FirstChar)
						If T\Masked = True Then Dat$ = String$("*", Len(Dat$))
						LetterOffset# = GY_TextWidth#(T\TextEN, Dat$)
						PositionEntity(T\CursorEN, ((X# + LetterOffset#) * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
						EntityParent(T\CursorEN, T\Gadget\EN)
					EndIf
				EndIf
			; Gadget is not active
			Else
				; It's a text field - remove focus
				If Object.GY_TextField(G\TypeHandle) <> Null
;					T.GY_TextField = Object.GY_TextField(G\TypeHandle)
;					If GY_ActiveTextField = T Then GY_ActiveTextField = Null : HideEntity(T\CursorEN)
				; It's a button - allow hover effects if it isn't locked
				ElseIf Object.GY_Button(G\TypeHandle) <> Null
					B.GY_Button = Object.GY_Button(G\TypeHandle)
					MinX# = GY_GadgetX#(Handle(G), True)
					MaxX# = GY_GadgetX#(Handle(G), True) + GY_GadgetWidth#(Handle(G), True)
					MinY# = GY_GadgetY#(Handle(G), True)
					MaxY# = GY_GadgetY#(Handle(G), True) + GY_GadgetHeight#(Handle(G), True)
					If GY_MouseX# > MinX# And GY_MouseY# > MinY# And GY_MouseX# < MaxX# And GY_MouseY# < MaxY# And G\Locked = False
						EntityTexture(B\T, GY_BorderTopP)
						EntityTexture(B\B, GY_BorderBottomP)
						EntityTexture(B\R, GY_BorderRightP)
						EntityTexture(B\L, GY_BorderLeftP)
						EntityTexture(B\TR, GY_BorderTRP)
						EntityTexture(B\TL, GY_BorderTLP)
						EntityTexture(B\BR, GY_BorderBRP)
						EntityTexture(B\BL, GY_BorderBLP)
					Else
						If B\State = True
							EntityTexture(B\T, GY_BorderTopD)
							EntityTexture(B\B, GY_BorderBottomD)
							EntityTexture(B\R, GY_BorderRightD)
							EntityTexture(B\L, GY_BorderLeftD)
							EntityTexture(B\TR, GY_BorderTRD)
							EntityTexture(B\TL, GY_BorderTLD)
							EntityTexture(B\BR, GY_BorderBRD)
							EntityTexture(B\BL, GY_BorderBLD)
							If B\UserTexture = 0
								EntityColor(G\EN, GY_ButtonDownR, GY_ButtonDownG, GY_ButtonDownB)
							Else
								EntityColor(G\EN, 200, 200, 200)
							EndIf
						Else
							EntityTexture(B\T, GY_BorderTop)
							EntityTexture(B\B, GY_BorderBottom)
							EntityTexture(B\R, GY_BorderRight)
							EntityTexture(B\L, GY_BorderLeft)
							EntityTexture(B\TR, GY_BorderTR)
							EntityTexture(B\TL, GY_BorderTL)
							EntityTexture(B\BR, GY_BorderBR)
							EntityTexture(B\BL, GY_BorderBL)
							If B\UserTexture = 0
								EntityColor(G\EN, GY_ButtonUpR, GY_ButtonUpG, GY_ButtonUpB)
							Else
								EntityColor(G\EN, 255, 255, 255)
							EndIf
						EndIf
					EndIf
				; Custom button - much the same thing
				ElseIf Object.GY_CustomButton(G\TypeHandle) <> Null
					CB.GY_CustomButton = Object.GY_CustomButton(G\TypeHandle)
					MinX# = GY_GadgetX#(Handle(G), True)
					MaxX# = GY_GadgetX#(Handle(G), True) + GY_GadgetWidth#(Handle(G), True)
					MinY# = GY_GadgetY#(Handle(G), True)
					MaxY# = GY_GadgetY#(Handle(G), True) + GY_GadgetHeight#(Handle(G), True)
					If GY_MouseX# > MinX# And GY_MouseY# > MinY# And GY_MouseX# < MaxX# And GY_MouseY# < MaxY# And G\Locked = False
						EntityTexture(G\EN, CB\HoverTex)
					Else
						If CB\State = True
							EntityTexture(G\EN, CB\DownTex)
						Else
							EntityTexture(G\EN, CB\UpTex)
						EndIf
					EndIf
				; It's a combo box
				ElseIf Object.GY_ComboBox(G\TypeHandle) <> Null
					Co.GY_ComboBox = Object.GY_ComboBox(G\TypeHandle)
					; If the box is open, close it
					If GY_ButtonDown(Co\ButtonGadget) = True
						GY_SetButtonState(Co\ButtonGadget, False)
						Co\OpenStatus = False
						; Hide background entity
						HideEntity(Co\ListAreaEN)
						; Hide items
						ShowEntity(Co\LabelEN)
						For I.GY_ComboItem = Each GY_ComboItem
							If I\Box = Co
								I\Visible = False
								; If it's the selected one, leave it visible and position it as the title
								If I = I\Box\Selected
									HideEntity(Co\LabelEN)
									EntityParent(I\LabelEN, GY_Cam)
									X# = GY_GadgetX#(Handle(G), True)
									Y# = GY_GadgetY#(Handle(G), True)
									PositionEntity(I\LabelEN, (X# * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
									EntityColor(I\LabelEN, I\Box\R, I\Box\G, I\Box\B)
									EntityParent(I\LabelEN, G\EN)
									EntityOrder(I\LabelEN, G\ZPosition - 2)
								Else
									HideEntity(I\LabelEN)
								EndIf
							EndIf
						Next
					EndIf
				EndIf
			EndIf
		EndIf
	Next

	; Stop gadget being held if the mouse is not down any more
	If MouseDown(1) = False And MouseDown(2) = False Then GY_HeldGadget = Null : GY_HeldData = 0

End Function

; Temporarily hides a gadget without altering its stored alpha (RECURSIVE)
Function GY_GadgetVisibility(G.GY_Gadget, Enable)

	; Change alpha for this gadget's entity and their children
	If G\EN <> 0
		If Enable = True Then Alpha# = G\Alpha# Else Alpha# = 0.0
		GY_RecursiveEntityAlpha(G\EN, Alpha#)
	EndIf

	; Find any gadget children and do it to them too
	For Child.GY_Gadget = Each GY_Gadget
		If Child\Parent = G Then GY_GadgetVisibility(Child, Enable)
	Next

End Function

; Creates a quad mesh
Function GY_CreateQuad(P = 0)

	EN = CreateMesh()
	s = CreateSurface(EN)
	v1 = AddVertex(s, 0.0, -1.0, 0.0, 0.0, 1.0)
	v2 = AddVertex(s, 1.0, -1.0, 0.0, 1.0, 1.0)
	v3 = AddVertex(s, 1.0,  0.0, 0.0, 1.0, 0.0)
	v4 = AddVertex(s, 0.0,  0.0, 0.0, 0.0, 0.0)
	AddTriangle s, v3, v2, v1
	AddTriangle s, v4, v3, v1
	EntityParent(EN, P)
	EntityFX(EN, 1 + 8)
	Return EN

End Function

; Sets the drawing order for an entity and all its children
Function GY_RecursiveEntityOrder(EN, Z)

	EntityOrder(EN, Z)
	For i = 1 To CountChildren(EN)
		GY_RecursiveEntityOrder(GetChild(EN, i), Z)
	Next

End Function

; Sets the alpha for an entity and all its children
Function GY_RecursiveEntityAlpha(EN, A#)

	EntityAlpha(EN, A#)
	For i = 1 To CountChildren(EN)
		GY_RecursiveEntityAlpha(GetChild(EN, i), A#)
	Next

End Function

; Copies a texture in memory
Function GY_CopyTexture(Tex, Flags)

	NewTex = CreateTexture(TextureWidth(Tex), TextureHeight(Tex), Flags)

	OldBuffer = GraphicsBuffer()
	SetBuffer(TextureBuffer(NewTex))
		LockBuffer()
		LockBuffer(TextureBuffer(Tex))
			For x = 0 To TextureWidth(Tex) - 1
			For y = 0 To TextureHeight(Tex) - 1
				CopyPixelFast(x, y, TextureBuffer(Tex), x, y)
			Next
			Next
		UnlockBuffer(TextureBuffer(Tex))
		UnlockBuffer()
	SetBuffer(OldBuffer)

	Return NewTex

End Function

; Returns true if the mouse is over a certain gadget
Function GY_MouseHovering(Gad)

	X# = GY_GadgetX#(Gad, True)
	Y# = GY_GadgetY#(Gad, True)
	W# = GY_GadgetWidth#(Gad, True)
	H# = GY_GadgetHeight#(Gad, True)

	If GY_MouseX# > X# And GY_MouseY# > Y#
		If GY_MouseX# < X# + W# And GY_MouseY# < Y# + H# Then Return True
	EndIf

	Return False

End Function

; Sets the camera position/rotation/scale to 0, 0, 0
Function GY_ZeroCamera()

	If GY_CamAtZero > 0
		GY_CamAtZero = GY_CamAtZero + 1
		Return
	EndIf

	GY_CamPX# = EntityX#(GY_Cam)
	GY_CamPY# = EntityY#(GY_Cam)
	GY_CamPZ# = EntityZ#(GY_Cam)
	GY_CamRX# = EntityPitch#(GY_Cam)
	GY_CamRY# = EntityYaw#(GY_Cam)
	GY_CamRZ# = EntityRoll#(GY_Cam)

	PositionEntity GY_Cam, 0, 0, 0
	RotateEntity GY_Cam, 0, 0, 0
	ScaleEntity GY_Cam, 1, 1, 1
	GY_CamAtZero = 1

End Function

; Restores the camera to its previous position/rotation/scale
Function GY_RestoreCamera()

	If GY_CamAtZero = 1
		PositionEntity GY_Cam, GY_CamPX#, GY_CamPY#, GY_CamPZ#
		RotateEntity GY_Cam, GY_CamRX#, GY_CamRY#, GY_CamRZ#
		ScaleEntity GY_Cam, GY_CamSX#, GY_CamSY#, GY_CamSZ#
	EndIf
	GY_CamAtZero = GY_CamAtZero - 1

End Function

; Scales the Gooey camera
Function GY_ScaleCamera(X#, Y#, Z#)

	GY_CamSX# = X#
	GY_CamSY# = Y#
	GY_CamSZ# = Z#
	If GY_CamAtZero = False Then ScaleEntity GY_Cam, GY_CamSX#, GY_CamSY#, GY_CamSZ#

End Function

;WideScreen edit Adjust Camera Ramoida
Function GYFovCam(Cam, xWidth, yHeight)
   Local FOV# = (2 * ATan(Tan((74) / 2) * xWidth / yHeight))
   CameraZoom(Cam, 1.0 / Tan(FOV# / 2))
End Function 