; Dialogs
Type Dialog
	Field Win
	Field TextLines[13]
	Field TextText$[13]
	Field TextR[13]
	Field TextG[13]
	Field TextB[13]
	Field OptionNum[13]
	Field TotalOptions
	Field LastLine
	Field ActorInstance.ActorInstance
	Field ScriptHandle
End Type
Type TextInput
	Field Win
	Field TextBox, AcceptButton
	Field ScriptHandle
End Type

; Chat bubbles
Global UseBubbles, BubblesR, BubblesG, BubblesB
Global ChatBubbleFont, ChatBubbleEN, ChatBubbleTex
Type Bubble
	Field EN
	Field Width#, Height#
	Field Timer
	Field ActorInstance.ActorInstance
End Type

; Controls
Global ViewMode = 2 ; ViewMode is 1 for first person only, 2 for first/third, or 3 for third person only
Global Key_Forward, Key_Back, Key_TurnRight, Key_TurnLeft, Key_Run, Key_FlyUp, Key_FlyDown
Global Key_Jump
Global Key_AlwaysRun
Global Key_CameraRight, Key_CameraLeft, Key_CameraIn, Key_CameraOut
Global Key_ChangeViewMode
Global Key_Attack, Key_CycleTarget
Global Key_MoveTo, Key_TalkTo, Key_Select
Global AlwaysRun = False
Global InvertAxis1 = 1, InvertAxis3 = 1

; Action bar
Global XPEN ; Experience progress bar
Global BChat, BMap, BInventory, BSpells, BCharStats, BQuestLog, BParty, BHelp
Global BNextBar, BPrevBar ; To switch between the 3 sets of action bar slots
Global ActionBarStart = 1 ; Which action bar slots are active? 1, 2 or 3
Dim ActionBarSlots(35) ; Values greater than 0 are item IDs, -1 to -10 are memorised spells, or if memorisation isn't used -1 to -1000 are known spells
Dim BActionBar(11) ; Gooey buttons
Global ActionBarUpTex, ActionBarDownTex

; Character interaction window
Global WCharInteract, CharInteractVisible = False
Global SCharInteractHealth
Global CharInteract.ActorInstance = Null
Global LCharInteractFaction
Global LCharInteractLevel
Global LCharInteractReputation

; Tooltip window
Global WTooltip ; Created/destroyed in the fly
Global WTooltipReturn ; Set to an existing window when tooltip is created
Global LTooltip ; Label only

; Party window
Global WParty, PartyVisible = False
Global BPartyLeave
Dim PartyName$(6)
Dim LPartyName(6)

;##############################################################################################################
;	Menu window and options added by clan_fd on Mar 20, 2014
Global WMenu, MenuVisible = False
Global BMenu, BLogOut, BCharSelect, BExit

Global BOptions

;	End edit
;##############################################################################################################



; Help window
Global WHelp, HelpVisible = False
Global SHelpScroll, MaxHelpLine, CurrentHelpLine
Dim HelpText$(99)
Dim LHelp(14)

; Radar and map
Global ShowRadar = False
Global WLargeMap, LargeMapVisible

; Inventory window
Global WInventory, InventoryVisible = False
Global LInventoryGold, BInventoryDrop, BInventoryEat
Global WAmount, BAmountOK, TAmount, AmountSlot, AmountVisible
Global MouseSlotEN, MouseSlotItem.ItemInstance, MouseSlotAmount, MouseSlotSource = -1
Dim BSlots(49)
Global WItemWindow, ItemWindowFromInventory ; Image Item Window

; Trading window
Global WTrading, TradingVisible = False
Global LTradingGold, LTradingCost, BTradingOK, BTradingCancel
Global TradeType ; 1 for player -> NPC, 2 for player -> player, 3 for player -> scenery
Global BCostUp, BCostDown, TradeCost ; Player -> player only
Dim BSlotsMine(31)
Dim BSlotsHis(31)
Dim TradeAmountsMine(31)
Dim TradeAmountsHis(31)
Dim TradeItems.ItemInstance(31)
Dim TradeAmounts(31)
Dim ServerTradeIDs(31)

; Char stats window
Global WCharStats, CharStatsVisible = False
Global LReputation, LGold, LLevel, LXP, BNextAttribute, BPrevAttribute, FirstAttribute
Dim LAttributeNames(9)
Dim LAttributeVals(9)

; Spells (abilities) window
Global WSpells, SpellsVisible = False
Global BNextSpells, BPrevSpells
Global LSpellsPage
Global WSpellRemove, BSpellRemoveOK, BSpellRemoveCancel, SpellRemoveNum = -1
Global WSpellError, BSpellError
Global LMemorising, SMemorising, MemoriseSpell, MemoriseSlot, MemoriseProgress, LastMemoriseUpdate
Dim BSpellImgs(9)  ; 10 abilities in book
Dim LSpellNames(9) ; Ability name text
Dim LSpellLevels(9) ; Ability level text
Dim LSpellDesc(9) ; Ability Description
;Dim LSpellDesc2(9) ; Ability Description line 2
Global FirstSpell = -1 ; -1 means we are looking at memorised spells rather than the longer list of known spells
Global LastSpellRecharge

; Quest log window
Global WQuestLog, QuestLogVisible = False
Global BCompleteQuests, BNextQuest, BPrevQuest, FirstQuest
Dim LQuestLines(16)

; Actor effect icons
Type EffectIcon
	Field Name$, ID, TextureID
End Type
Type EffectIconSlot
	Field EN, Effect.EffectIcon
End Type

; Misc
Global LastMouseMove, LastLeftClick, MouseControl, MouseWasDown, InWaitPeriod, MouseDownTime, MouseClicks
Global RightWasDown, RightDownTime
Global SelectKeyWasDown, SelectKeyClickTime
Global InDialog

; Chat text display
Dim ChatHistory$(1999) ; Chat history stored permanently
Dim ChatHistoryColour(1999)
Type CurrentChat ; Chat which will disappear after a certain amount of time
	Field Dat$, cR, cG, cB
	Field Timer
End Type
Dim ChatLines(0) ; Gooey labels to display chat, this array gets resized to however many lines can fit vertically
Global MaxChatLine = 0 ; Stores the highest index of the resized ChatLines array
Global HistoryMode = False ; True if viewing history
Global FirstHistoryLine = 0 ; First line of history being viewed
Global MaxHistoryLine = -1 ; Last line of history available
Global BHistoryMode ; History mode toggle button
Global BHistoryUp, BHistoryDown ; Buttons to scroll through history

; Interface component settings
Type InterfaceComponent
	Field X#, Y#, Width#, Height# ; Position/size in fraction of screen size
	Field Alpha#                  ; Transparency level 0.0 - 1.0
	Field Component               ; Handle to an entity, Gooey gadget, whatever
	Field Texture                 ; TextureID for entity
	Field R, G, B                 ; Colours for a Gooey gadget
End Type

; Standard interface components
Global Chat.InterfaceComponent               ; Chat text
Global ChatBar.InterfaceComponent            ; Chat text background (optional)
Global ChatEntry.InterfaceComponent          ; Chat input box (fixed height)
Global BuffsArea.InterfaceComponent          ; Actor effect (buff) icons area
Global Radar.InterfaceComponent              ; Radar map
Global Compass.InterfaceComponent            ; Compass
Dim AttributeDisplays.InterfaceComponent(39) ; Bars for character attributes
Dim AttributeDisplayNumbers(39)              ; Number displays for character attribute bars
Global InventoryWindow.InterfaceComponent    ; Inventory window
Global InventoryDrop.InterfaceComponent      ; Inventory drop button
Global InventoryEat.InterfaceComponent       ; Inventory use button
Global InventoryGold.InterfaceComponent      ; Inventory money display
Dim InventoryButtons.InterfaceComponent(45)  ; Buttons for inventory slots

; Gets the script handle for a dialog
Function DialogScriptHandle(Han)

	D.Dialog = Object.Dialog(Han)
	If D <> Null Then Return D\ScriptHandle

End Function

; Loads control bindings
Function LoadControlBindings(Filename$)

	F = ReadFile(Filename$)
	If F = 0 Then Return False

		Key_Forward        = ReadInt(F)
		Key_Back           = ReadInt(F)
		Key_TurnRight      = ReadInt(F)
		Key_TurnLeft       = ReadInt(F)
		Key_FlyUp          = ReadInt(F)
		Key_FlyDown        = ReadInt(F)
		Key_Run            = ReadInt(F)
		Key_ChangeViewMode = ReadInt(F)
		Key_CameraRight    = ReadInt(F)
		Key_CameraLeft     = ReadInt(F)
		Key_CameraIn       = ReadInt(F)
		Key_CameraOut      = ReadInt(F)
		Key_Jump           = ReadInt(F)
		InvertAxis1        = ReadByte(F) - 1
		InvertAxis3        = ReadByte(F) - 1
		Key_Attack         = ReadInt(F)
		Key_AlwaysRun      = ReadInt(F)
		Key_CycleTarget    = ReadInt(F)
		Key_MoveTo         = ReadInt(F)
		Key_TalkTo         = ReadInt(F)
		Key_Select         = ReadInt(F)

	CloseFile(F)
	Return True

End Function

; Saves control bindings
Function SaveControlBindings(Filename$)

	F = WriteFile(Filename$)
	If F = 0 Then Return False

		WriteInt(F, Key_Forward)
		WriteInt(F, Key_Back)
		WriteInt(F, Key_TurnRight)
		WriteInt(F, Key_TurnLeft)
		WriteInt(F, Key_FlyUp)
		WriteInt(F, Key_FlyDown)
		WriteInt(F, Key_Run)
		WriteInt(F, Key_ChangeViewMode)
		WriteInt(F, Key_CameraRight)
		WriteInt(F, Key_CameraLeft)
		WriteInt(F, Key_CameraIn)
		WriteInt(F, Key_CameraOut)
		WriteInt(F, Key_Jump)
		WriteByte(F, InvertAxis1 + 1)
		WriteByte(F, InvertAxis3 + 1)
		WriteInt(F, Key_Attack)
		WriteInt(F, Key_AlwaysRun)
		WriteInt(F, Key_CycleTarget)
		WriteInt(F, Key_MoveTo)
		WriteInt(F, Key_TalkTo)
		WriteInt(F, Key_Select)

	CloseFile(F)
	Return True

End Function

; Writes the data for any interface component to a stream
Function WriteInterfaceComponent(IC.InterfaceComponent, Stream)

	WriteFloat(Stream, IC\X#)
	WriteFloat(Stream, IC\Y#)
	WriteFloat(Stream, IC\Width#)
	WriteFloat(Stream, IC\Height#)
	WriteFloat(Stream, IC\Alpha#)
	WriteByte(Stream, IC\R)
	WriteByte(Stream, IC\G)
	WriteByte(Stream, IC\B)

End Function

; Reads in the data for any interface component from a stream
Function ReadInterfaceComponent(IC.InterfaceComponent, Stream)

	IC\X#      = ReadFloat#(Stream)
	IC\Y#      = ReadFloat#(Stream)
	IC\Width#  = ReadFloat#(Stream)
	IC\Height# = ReadFloat#(Stream)
	IC\Alpha#  = ReadFloat#(Stream)
	IC\R = ReadByte(Stream)
	IC\G = ReadByte(Stream)
	IC\B = ReadByte(Stream)

End Function

; Loads the settings for interface layout from file
Function LoadInterfaceSettings(Filename$)

	F = ReadFile(Filename$)
	If F = 0 Then Return False

		; Main game screen
		Chat = New InterfaceComponent
		ReadInterfaceComponent(Chat, F)
		Chat\Texture = ReadShort(F)
		ChatEntry = New InterfaceComponent
		ReadInterfaceComponent(ChatEntry, F)
		For i = 0 To 39
			AttributeDisplays(i) = New InterfaceComponent
			ReadInterfaceComponent(AttributeDisplays(i), F)
		Next
		BuffsArea = New InterfaceComponent
		ReadInterfaceComponent(BuffsArea, F)
		Radar = New InterfaceComponent
		ReadInterfaceComponent(Radar, F)
		Compass = New InterfaceComponent
		ReadInterfaceComponent(Compass, F)

		; Inventory
		InventoryWindow = New InterfaceComponent
		ReadInterfaceComponent(InventoryWindow, F)
		InventoryDrop = New InterfaceComponent
		ReadInterfaceComponent(InventoryDrop, F)
		InventoryEat = New InterfaceComponent
		ReadInterfaceComponent(InventoryEat, F)
		InventoryGold = New InterfaceComponent
		ReadInterfaceComponent(InventoryGold, F)
		For i = 0 To 45
			InventoryButtons(i) = New InterfaceComponent
			ReadInterfaceComponent(InventoryButtons(i), F)
		Next

	CloseFile(F)
	Return True

End Function

; Saves the interface settings to a file
Function SaveInterfaceSettings(Filename$)

	F = WriteFile(Filename$)
	If F = 0 Then Return False

		; Main game screen
		WriteInterfaceComponent(Chat, F)
		WriteShort(F, Chat\Texture)
		WriteInterfaceComponent(ChatEntry, F)
		For i = 0 To 39
			WriteInterfaceComponent(AttributeDisplays(i), F)
		Next
		WriteInterfaceComponent(BuffsArea, F)
		WriteInterfaceComponent(Radar, F)
		WriteInterfaceComponent(Compass, F)

		; Inventory
		WriteInterfaceComponent(InventoryWindow, F)
		WriteInterfaceComponent(InventoryDrop, F)
		WriteInterfaceComponent(InventoryEat, F)
		WriteInterfaceComponent(InventoryGold, F)
		For i = 0 To 45
			WriteInterfaceComponent(InventoryButtons(i), F)
		Next

	CloseFile(F)
	Return True

End Function

; Returns the position at which a string should be split to word wrap to a maximum number of characters
Function WordWrap(St$, MaxChars)

	If Len(St$) <= MaxChars Then Return 0
	For i = MaxChars To 1 Step -1
		If Mid$(St$, i, 1) = " " Then Return i
	Next
	Return MaxChars

End Function

; Returns the number of times a control has been pressed since the last call
Function ControlHit(Ctrl)

	; Keyboard
	If Ctrl < 500
		Return KeyHit(Ctrl)
	; Mouse
	ElseIf Ctrl < 1000
		Select Ctrl
			Case 501 : Return MouseHit(1)
			Case 502 : Return MouseHit(2)
			Case 503 : Return MouseHit(3)
			Case 504 : If MYSpeed < 3 Then Return True
			Case 505 : If MYSpeed > 3 Then Return True
			Case 506 : If MXSpeed > 3 Then Return True
			Case 507 : If MXSpeed < 3 Then Return True
			Case 508 : If MZSpeed > 0 Then Return True
			Case 509 : If MZSpeed < 0 Then Return True
		End Select
	; Joystick buttons
	ElseIf Ctrl < 1009
		Return JoyHit(Ctrl - 1000)
	; Joystick hat/axes
	Else
		Select Ctrl
			Case 1009
				If JoyHat() = 0 Or JoyHat() = 45 Or JoyHat() = 315
					If JoyHatUp = False Then JoyHatUp = True : Return True
				EndIf
			Case 1010
				If JoyHat() = 180 Or JoyHat() = 135 Or JoyHat() = 225
					If JoyHatDown = False Then JoyHatDown = True : Return True
				EndIf
			Case 1011
				If JoyHat() = 90 Or JoyHat() = 45 Or JoyHat() = 135
					If JoyHatRight = False Then JoyHatRight = True : Return True
				EndIf
			Case 1012
				If JoyHat() = 270 Or JoyHat() = 225 Or JoyHat() = 315
					If JoyHatLeft = False Then JoyHatLeft = True : Return True
				EndIf
			Case 1013 : If JoyYDir() = -1 Then Return True
			Case 1014 : If JoyYDir() = 1 Then Return True
			Case 1015 : If JoyXDir() = 1 Then Return True
			Case 1016 : If JoyXDir() = -1 Then Return True
		End Select
	EndIf

End Function

; Returns whether the specified control is being held down
Function ControlDown(Ctrl)

	; Keyboard
	If Ctrl < 500
		Return KeyDown(Ctrl)
	; Mouse
	ElseIf Ctrl < 1000
		Select Ctrl
			Case 501 : Return MouseDown(1)
			Case 502 : Return MouseDown(2)
			Case 503 : Return MouseDown(3)
			Case 504 : If MYSpeed < 3 Then Return True
			Case 505 : If MYSpeed > 3 Then Return True
			Case 506 : If MXSpeed > 3 Then Return True
			Case 507 : If MXSpeed < 3 Then Return True
			Case 508 : If MZSpeed > 0 Then Return True
			Case 509 : If MZSpeed < 0 Then Return True
		End Select
	; Joystick buttons
	ElseIf Ctrl < 1009
		Return JoyDown(Ctrl - 1000)
	; Joystick hat/axes
	Else
		Select Ctrl
			Case 1009 : If JoyHat() = 0 Or JoyHat() = 45 Or JoyHat() = 315 Then Return True
			Case 1010 : If JoyHat() = 180 Or JoyHat() = 135 Or JoyHat() = 225 Then Return True
			Case 1011 : If JoyHat() = 90 Or JoyHat() = 45 Or JoyHat() = 135 Then Return True
			Case 1012 : If JoyHat() = 270 Or JoyHat() = 225 Or JoyHat() = 315 Then Return True
			Case 1013 : If JoyYDir() = -1 Then Return True
			Case 1014 : If JoyYDir() = 1 Then Return True
			Case 1015 : If JoyXDir() = 1 Then Return True
			Case 1016 : If JoyXDir() = -1 Then Return True
		End Select
	EndIf

End Function

; Returns the name of a control number
Function ControlName$(ControlNumber)

	Select ControlNumber
		; Keyboard
		Case 1 : Return "Escape"
		Case 2 : Return "1"
		Case 3 : Return "2"
		Case 4 : Return "3"
		Case 5 : Return "4"
		Case 6 : Return "5"
		Case 7 : Return "6"
		Case 8 : Return "7"
		Case 9 : Return "8"
		Case 10 : Return "9"
		Case 11 : Return "0"
		Case 12 : Return "-"
		Case 13 : Return "="
		Case 14 : Return "Backspace"
		Case 15 : Return "Tab"
		Case 16 : Return "Q"
		Case 17 : Return "W"
		Case 18 : Return "E"
		Case 19 : Return "R"
		Case 20 : Return "T"
		Case 21 : Return "Y"
		Case 22 : Return "U"
		Case 23 : Return "I"
		Case 24 : Return "O"
		Case 25 : Return "P"
		Case 26 : Return "["
		Case 27 : Return "]"
		Case 28 : Return "Return"
		Case 29 : Return "Left Control"
		Case 30 : Return "A"
		Case 31 : Return "S"
		Case 32 : Return "D"
		Case 33 : Return "F"
		Case 34 : Return "G"
		Case 35 : Return "H"
		Case 36 : Return "J"
		Case 37 : Return "K"
		Case 38 : Return "L"
		Case 39 : Return ";"
		Case 40 : Return "'"
		Case 42 : Return "Left Shift"
		Case 43 : Return "\"
		Case 44 : Return "Z"
		Case 45 : Return "X"
		Case 46 : Return "C"
		Case 47 : Return "V"
		Case 48 : Return "B"
		Case 49 : Return "N"
		Case 50 : Return "M"
		Case 51 : Return ","
		Case 52 : Return "."
		Case 53 : Return "/"
		Case 54 : Return "Right Shift"
		Case 55 : Return "Numpad *"
		Case 56 : Return "Left Alt"
		Case 57 : Return "Space"
		Case 58 : Return "Caps Lock"
		Case 59 : Return "F1"
		Case 60 : Return "F2"
		Case 61 : Return "F3"
		Case 62 : Return "F4"
		Case 63 : Return "F5"
		Case 64 : Return "F6"
		Case 65 : Return "F7"
		Case 66 : Return "F8"
		Case 67 : Return "F9"
		Case 68 : Return "F10"
		Case 71 : Return "Numpad 7"
		Case 72 : Return "Numpad 8"
		Case 73 : Return "Numpad 9"
		Case 74 : Return "Numpad -"
		Case 75 : Return "Numpad 4"
		Case 76 : Return "Numpad 5"
		Case 77 : Return "Numpad 6"
		Case 78 : Return "Numpad +"
		Case 79 : Return "Numpad 1"
		Case 80 : Return "Numpad 2"
		Case 81 : Return "Numpad 3"
		Case 82 : Return "Numpad 0"
		Case 83 : Return "Numpad ."
		Case 87 : Return "F11"
		Case 88 : Return "F12"
		Case 156 : Return "Enter"
		Case 157 : Return "Right Control"
		Case 181 : Return "Numpad /"
		Case 184 : Return "Right Alt"
		Case 197 : Return "Pause"
		Case 199 : Return "Home"
		Case 200 : Return "Up Arrow"
		Case 201 : Return "Page Up"
		Case 203 : Return "Left Arrow"
		Case 205 : Return "Right Arrow"
		Case 207 : Return "End"
		Case 208 : Return "Down Arrow"
		Case 209 : Return "Page Down"
		Case 210 : Return "Insert"
		Case 211 : Return "Delete"
		; Mouse
		Case 501 : Return "Left Mouse Button"
		Case 502 : Return "Right Mouse Button"
		Case 503 : Return "Middle Mouse Button"
		Case 504 : Return "Mouse Up"
		Case 505 : Return "Mouse Down"
		Case 506 : Return "Mouse Right"
		Case 507 : Return "Mouse Left"
		Case 508 : Return "Mouse Scroll Wheel Up"
		Case 509 : Return "Mouse Scroll Wheel Down"
		; Joystick
		Case 1001 : Return "Joystick Button 1"
		Case 1002 : Return "Joystick Button 2"
		Case 1003 : Return "Joystick Button 3"
		Case 1004 : Return "Joystick Button 4"
		Case 1005 : Return "Joystick Button 5"
		Case 1006 : Return "Joystick Button 6"
		Case 1007 : Return "Joystick Button 7"
		Case 1008 : Return "Joystick Button 8"
		Case 1009 : Return "Joystick Hat Up"
		Case 1010 : Return "Joystick Hat Down"
		Case 1011 : Return "Joystick Hat Right"
		Case 1012 : Return "Joystick Hat Left"
		Case 1013 : Return "Joystick Up"
		Case 1014 : Return "Joystick Down"
		Case 1015 : Return "Joystick Right"
		Case 1016 : Return "Joystick Left"
	End Select

	Return LanguageString$(LS_Unknown)

End Function