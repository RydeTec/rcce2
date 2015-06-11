.lib " "

GY_Create3DText%(X#, Y#, Width#, Height#, MaxLength%, Font%, ZOrder%, Parent%)
GY_Set3DText(Handle%, Text%)

GY_CopyTexture%(Texture, Flags)

GY_Load(Camera)
GY_Unload()

GY_Update()

GY_MessageBox(Title$, Message$)
GY_RequestBox(Title$, Message$)

GY_GadgetX#(Gadget)
GY_GadgetY#(Gadget)
GY_GadgetWidth#(Gadget)
GY_GadgetHeight#(Gadget)
GY_PositionGadget%(Gadget, X#, Y#)
GY_ScaleGadget%(Gadget, Width#, Height#)
GY_LockGadget%(Gadget)
GY_GadgetAlpha%(Gadget, Alpha#)
GY_FreeGadget%(Gadget)
GY_MouseHovering%(Gadget)
GY_DropGadget%(Gadget)
GY_SetGadgetData%(Gadget, Dat$)
GY_GadgetData$(Gadget)

GY_CreateWindow%(Title$, X#, Y#, Width#, Height#)
GY_WindowClosed%(Gadget)
GY_WindowMinimised%(Gadget)
GY_ActivateWindow%(Gadget)
GY_WindowActive%(Gadget)

GY_CreateListBox%(Parent, X#, Y#, Width#, Height#)
GY_AddListBoxItem%(Gadget, Label$)
GY_ListBoxItem$(Gadget)
GY_UpdateListBox%(Gadget, Item$)

GY_CreateComboBox%(Parent, X#, Y#, Width#, Height#, Label$)
GY_AddComboBoxItem%(Gadget, Label$)
GY_ComboBoxItem$(Gadget)
GY_ComboBoxIsOpen%(Gadget)
GY_UpdateComboBox%(Gadget, Item$)

GY_CreateTextField%(Parent, X#, Y#, Width#, AllowedInput, MaxLength)
GY_UpdateTextField%(Gadget, Label$)
GY_TextFieldHit%(Gadget)
GY_TextFieldText$(Gadget)
GY_TextFieldActive%(Gadget)
GY_ActivateTextField%(Gadget)

GY_CreateScrollBar%(Parent, X#, Y#, Width#, Height#)
GY_SetScrollBarInterval%(Gadget, Interval#)
GY_GetScrollBarInterval#(Gadget)
GY_UpdateScrollBar%(Gadget, Value#)
GY_ScrollBarValue#(Gadget)

GY_CreateSlider%(Parent, X#, Y#, Width#, Height#, Label$, Value#, Min#, Max#)
GY_UpdateSlider%(Gadget, Value#)
GY_GetSliderValue#(Gadget)

GY_CreateButton%(Parent, X#, Y#, Width#, Height#, Label$)
GY_CreateCustomButton%(Parent, X#, Y#, Width#, Height#, UpTex, DownTex, HoverTex)
GY_SetButtonLabel%(Gadget, Label$)
GY_SetButtonState%(Gadget, State)
GY_ButtonDown%(Gadget)
GY_ButtonHit%(Gadget)
GY_ButtonRightHit%(Gadget)

GY_CreateProgressBar%(Parent, X#, Y#, Width#, Height#, Value, Max, cR, cG, cB, ZOrder)
GY_UpdateProgressBar%(Gadget, Value)

GY_CreateLabel%(Parent, X#, Y#, Label$)
GY_UpdateLabel%(Gadget, Label$)

GY_CreateCheckBox%(Parent, X#, Y#, Label$)
GY_UpdateCheckBox%(Gadget, State)
GY_CheckBoxDown%(Gadget)
GY_CheckBoxHit%(Gadget)