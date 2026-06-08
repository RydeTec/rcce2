Include "Modules\F-UI.bb"

Function AddGadgetItem(parent%, item$, arg3% = False)
	return FUI_ComboBoxItem(parent, item$)
End Function

Function HideGadget(parent%)
	FUI_HideGadget(parent)
End Function

Function ShowGadget(parent%)
	FUI_ShowGadget(parent)
End Function

Function SetGadgetText(parent%, text$)
	FUI_SendMessage(parent, M_SETTEXT, text$)
End Function

Function GadgetItemText$(parent%, index%)
	return FUI_SendMessage(parent, M_GETTEXT, index%)
End Function

Function TextFieldText$(parent%)
	return FUI_SendMessage(parent, M_GETTEXT)
End Function

Function SetPanelImage(parent%, image$)
	FUI_SendMessage(parent, M_SETIMAGE, image$)
End Function

Function SelectedGadgetItem%(parent%)
	return FUI_SendMessage(parent, M_GETSELECTED)
End Function

Function ActivateGadget(parent%)
	FUI_SendMessage(parent, M_ENABLE)
End Function

Function SetTextAreaText(parent%, text$)
	FUI_SendMessage(parent, M_SETTEXT, text$)
End Function

Function ClearGadgetItems(parent%)
	FUI_SendMessage(parent, M_RESET)
End Function

Function Confirm(text$)
    Local option = FUI_CustomMessageBox(text$, "Confirm", MB_YESNO)
    if (option = IDYES)
        return True
    end if

    return False
End Function

Function RemoveGadgetItem(parent%, index%)
	FUI_SendMessage(parent, M_DELETEINDEX, index%)
End Function

Function ModifyGadgetItem(parent%, index%, text$)
	FUI_SendMessage(parent, M_SETTEXT, index%, text$)
End Function

Function CountGadgetItems(parent%)
	return FUI_SendMessage(parent, M_COUNTITEMS)
End Function

Function CreateWindow(title$, x%, y%, width%, height%, parent%, style%)
    return FUI_Window(x%, y%, width%, height%, title$, 0, style%, 0)
End Function

Function CreateListBox(x%, y%, width%, height%, parent%)
    return FUI_ListBox(parent%, x%, y%, width%, height%)
End Function

Function AddListBoxItem(parent%, item$, arg3% = False)
	return FUI_ListBoxItem(parent, item$)
End Function

Function ClientWidth(parent%)
    return FUI_SendMessage(parent, M_GETSIZE_W)
End Function

Function ClientHeight(parent%)
    return FUI_SendMessage(parent, M_GETSIZE_H)
End Function

Function CreateButton(text$, x%, y%, width%, height%, parent%)
    return FUI_Button(parent%, x%, y%, width%, height%, text$)
End Function

Function CreateLabel(text$, x%, y%, width%, height%, parent%, arg6% = 0)
    return FUI_Label(parent%, x%, y%, text$)
End Function

Function CreateTextField(x%, y%, width%, height%, parent%)
    return FUI_TextBox(parent%, x%, y%, width%, height%)
End Function

Function CreateComboBox(x%, y%, width%, height%, parent%)
    return FUI_ComboBox(parent%, x%, y%, width%, height%)
End Function

Function CreateTextArea(x%, y%, width%, height%, parent%, style%)
    return FUI_TextBox(parent%, x%, y%, width%, height%)
End Function

Function CreatePanel(x%, y%, width%, height%, parent%)
    return FUI_Panel(parent%, x%, y%, width%, height%, "")
End Function

Function SetGadgetShape(parent%, x%, y%, width%, height%)
    FUI_SendMessage(parent, M_SETSIZE_W, width%)
    FUI_SendMessage(parent, M_SETSIZE_H, height%)
    FUI_SendMessage(parent, M_SETPOS_X, x%)
    FUI_SendMessage(parent, M_SETPOS_Y, y%)
End Function

Function GadgetX(parent%)
    return FUI_SendMessage(parent, M_GETPOS_X)
End Function

Function GadgetY(parent%)
    return FUI_SendMessage(parent, M_GETPOS_Y)
End Function

Function GadgetWidth(parent%)
    return FUI_SendMessage(parent, M_GETSIZE_W)
End Function

Function GadgetHeight(parent%)
    return FUI_SendMessage(parent, M_GETSIZE_H)
End Function

Function AddTextAreaText(parent%, text$)
    ; M_GETTEXT returns a string; the local must carry the $ sigil or Blitz
    ; quietly receives the int form (0) and the concatenation becomes
    ; "0" + text$, wiping any existing textarea contents.
    Local current_text$ = FUI_SendMessage(parent, M_GETTEXT)
    FUI_SendMessage(parent, M_SETTEXT, current_text$ + text$)
End Function

Function GadgetGroup(parent%)
    return parent
End Function

Function Desktop()
	return 0
End Function

Function CreateImageBox(x%, y%, width%, height%, parent%, image$)
	return FUI_ImageBox(parent%, x%, y%, width%, height%, image$)
End Function
