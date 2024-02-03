<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**Interface3D.bb**

This module contains the following globals:  

*   [KnownSpellSort(999)](#GKnownSpellSort)

This module contains the following functions:  

*   [CreateTextInput](#FCreateTextInput)
*   [FreeTextInput](#FFreeTextInput)
*   [CreateDialog](#FCreateDialog)
*   [FreeDialog](#FFreeDialog)
*   [AddDialogOption](#FAddDialogOption)
*   [DialogOutput](#FDialogOutput)
*   [Output](#FOutput)
*   [BubbleOutput](#FBubbleOutput)
*   [UpdateChatTextDisplay](#FUpdateChatTextDisplay)
*   [GetTarget](#FGetTarget)
*   [UpdateInterface](#FUpdateInterface)
*   [UpdateCompass](#FUpdateCompass)
*   [UpdateSpellbook](#FUpdateSpellbook)
*   [UpdateXPBar](#FUpdateXPBar)
*   [UpdateTrading](#FUpdateTrading)
*   [UpdateEffectIcons](#FUpdateEffectIcons)
*   [CreateCharInteractionWindow](#FCreateCharInteractionWindow)
*   [CreateInterface](#FCreateInterface)
*   [CreateInterfaceQuad](#FCreateInterfaceQuad)
*   [EnableInventoryBlanks](#FEnableInventoryBlanks)
*   [SetPickModes](#FSetPickModes)
*   [CreateActionBarButton](#FCreateActionBarButton)
*   [CreateInventoryButton](#FCreateInventoryButton)
*   [RedrawQuestLog](#FRedrawQuestLog)
*   [UpdateActionBarIcons](#FUpdateActionBarIcons)
*   [UseItem](#FUseItem)

  

* * *

  

**KnownSpellSort(999) (global)**  
  
This global array acts as a layer of indirection over the top of the player actor instance's KnownSpells\[\] field. It is used to allow the display of abilities in alphabetical order while keeping them in the 'real' order in the KnownSpells\[\] field to match the server.

  

* * *

  
  
  

**CreateTextInput(Title$, Prompt$, Numeric, ScriptHandle)**  
  
Return value: Handle of newly created dialog  
  
Parameters:  

*   _Title$_ - The text to display in the dialog window title bar
*   _Prompt$_ - The text to display next to the input box
*   _Numeric_ - Controls which characters are accepted as valid input
*   _ScriptHandle_ - The handle provided by the server to uniquely identify the dialog

  
This function creates and displays a new text input dialog window. The Numeric parameter allows input to be limited to certain types -- 0 means anything, 1 means alphabetical characters only, 2 means integers only, 3 means decimal numbers only. Since the dialog handling code involves communication with the server, dialogs should not be created outside of the scripting use for which they exist.

  
  
  

**FreeTextInput(Han)**  
  
Return value: None  
  
Parameters:  

*   _Han_ - Handle of the text input dialog to free

  
This function frees an existing text input dialog.

  
  
  

**CreateDialog(Title$, A.ActorInstance, ScriptHandle, BackgroundTexID)**  
  
Return value: Handle of newly created dialog  
  
Parameters:  

*   _Title$_ - The text to display in the dialog window title bar
*   _A.ActorInstance_ - The actor instance with whom the player is 'conversing'
*   _ScriptHandle_ - The handle provided by the server to uniquely identify the dialog
*   _BackgroundTexID_ - The media ID for the window background texture to use (defaults to 65535)

  
This function creates and displays a new dialog (conversation) window. If an actor instance is given (the A parameter may be null if the dialog is not with a specific character) then its "greeting" speech sound will play. Since the dialog handling code involves communication with the server, dialogs should not be created outside of the scripting use for which they exist.

  
  
  

**FreeDialog(Han)**  
  
Return value: None  
  
Parameters:  

*   _Han_ - Handle of the dialog to free

  
This function frees an existing dialog, and plays the "goodbye" sound of the attached actor instance, if any.

  
  
  

**AddDialogOption(Han, Opt$)**  
  
Return value: None  
  
Parameters:  

*   _Han_ - Handle of the dialog to add to
*   _Opt$_ - Text to display for the option

  
This function adds a clickable option to a dialog. Multiple options can be added consecutively. When the user clicks an option, a message is sent to the server giving the number of the option selected (options are numbered in the order added). The actual addition of option text to the dialog is done by [DialogOutput](#FDialogOutput), the same as for normal dialog text, so all this function does is add one to the total number of options present in the dialog and call DialogOutput to display the text.

  
  
  

**DialogOutput(Han, T$, R, G, B, Opt)**  
  
Return value: None  
  
Parameters:  

*   _Han_ - Handle of the dialog to add to
*   _T$_ - The text to display
*   _R_ - The red component of the text colour (defaults to 255)
*   _G_ - The green component of the text colour (defaults to 255)
*   _B_ - The blue component of the text colour (defaults to 255)
*   _Opt_ - The clickable option ID of this text (defaults to 0)

  
This function adds coloured text a dialog. It automatically wraps text to fit the available width, using recursion to add multiple lines if required. If the optional Opt parameter is set, each line taken by the text is marked with that option ID, meaning that it can then be highlighted when hovered over and selected by the user.

  
  
  

**Output(Dat$, R, G, B)**  
  
Return value: None  
  
Parameters:  

*   _Dat$_ - The text to display
*   _R_ - The red component of the text colour (defaults to 255)
*   _G_ - The green component of the text colour (defaults to 255)
*   _B_ - The blue component of the text colour (defaults to 255)

  
This function adds text to the normal chat text area. The text is wrapped if necessary, using recursion to add multiple lines if required. All chat text lines are given a timer controlling how long they will stay onscreen before disappearing. However, chat text is stored and can still be reviewed after this time if the user clicks the button to drop down the chat history.

  
  
  

**BubbleOutput(Dat$, R, G, B, AI.ActorInstance, NoText)**  
  
Return value: None  
  
Parameters:  

*   _Dat$_ - The text to display
*   _R_ - The red component of the text colour (defaults to 255)
*   _G_ - The green component of the text colour (defaults to 255)
*   _B_ - The blue component of the text colour (defaults to 255)
*   _AI.ActorInstance_ - The character whom the bubble will appear above
*   _NoText_ - Set to True to prevent the text being sent to the standard chat output as well as the bubble.(defaults to False)

  
This function creates a bubble above a character's head containing text. Each actor instance may only have one bubble attached at a time, so if an existing bubble is still attached it will be destroyed. Chat bubbles disappear after a time just like normal chat text.

  
  
  

**UpdateChatTextDisplay()**  
  
Return value: None  
  
Parameters: None  
  
This function updates the chat text area labels to reflect the current state. The display has two modes, current messages and message history.

  
  
  

**GetTarget$(EN)**  
  
Return value: Character representing the player targetable object type of an entity  
  
Parameters:  

*   _EN_ - The entity to check

  
This function checks what type of targetable object an entity is, either an actor instance, a dropped item, or scenery. It is used when the player clicks something to determine the correct behaviour (select, pick up, or walk to).

  
  
  

**UpdateInterface()**  
  
Return value: None  
  
Parameters: None  
  
This function updates the entire user interface for the game. The first step is to update the [Gooey](gooey.md) user interface library. This allows updating and user input for all gadgets such as windows, buttons etc. Next various keys are checked for presses. First the screenshot key, escape to quit, and the "always run" toggle. Next is the cycle target key. If this is pressed then the next valid NPC target is found and targeted. Next the EType variable is set to the player's environment type (this can change if the player is on a mount). Keyboard movement controls are then updated so long as the chat entry box is not currently open, since all key presses are then rerouted into the text box. Otherwise, when typing into the chat entry box, the player's character might move or perform other unintended actions. Possible actions which are checked are the jump key, forward/backwards movement, and turning. Next the breath bar is shown or hidden depending on whether the player is current underwater -- but only if there is an attribute for breath and the player character does not have an environment type of swimming). Next if the player is flying or swimming the up/down keys are checked for vertical movement. At various points in this and later section you will see "QuitActive = False". This cancels quit progress if the player moves or performs any significant action while waiting to quit.  
  
The next section handles mouselook if the right mouse button is held. Looking left and right is handled different depending on whether the player is using a first or third person view mode, but looking up and down is the same in both modes. In third person, the camera can also be zoomed in and out using keys or the mousewheel.  
  
Next, if the mouse cursor is not currently over the action bar or a dialog window, and the player character is not performing a scripted animation, mouse clicks are processed to allow the player to move around, attack/speak to characters, or pick up dropped items. For the right mouse button, clicks taking more than 500 milliseconds being the button being pressed and being released are ignored. This ensures that using mouselook (which involves holding the right mouse button) is not interpreted as a click. Possible results of a right click are running an NPC right-click script, dismounting a mount, and attempting to use ownable scenery. Left clicking allows the button to be held continously after clicking on scenery, which updates the player to hold the button to run along indefinitely and steer without having to constantly click. Double clicks are also accepted and cause the player character to run instead of walking, or to attack an actor instance. The [GetTarget](#FGetTarget) function is used to see what type of target the player has selected. If a character, it is selected (or attacked if already selected and then double clicked). If a dropped item, it is picked up when in range or moved towards when not. Otherwise the target is part of the zone and is moved towards. The final part of this section is to cancel running if the player's energy stat is 0 or below.  
  
Next the attack key is checked, and the selected target (if there is one) is attacked when it is pressed. Then the mesh used to highlight the player's selected target is updated to the position and ground slope of the target, or hidden if no target is selected. Next the action bar buttons are updated, so that items/spells in the quick slots can be used, or added to quick slots if one is in the "mouse slot" (a special temporary slot for picked up items or abilities) when the quick slot is clicked. Right clicking a quick slot clears it. F-keys can also be used to use a quick slot.  
  
Any of the player's spells (abilities) needing recharging are then recharged, at 100 millisecond intervals (the same as the server does in the [GameServer](gameserver.md) module). The spells window is updated, as is the spell memorisation progress bar if visible. Dialogs are updated, which involves highlighting any lines flagged as an option when the mouse hovers over them, and sending a message to the server is one is clicked. The quest log, party and help windows are updated. In the case of the party window, clicking a name causes the chat text entry area to be displayed, with the text to send a private message to that player already entered. If the character stats window is open, its text labels are updated with the current values, and presses of the the next/previous attribute button are processed.  
  
The next section deals with tooltips, of which there are two types. The first uses a Gooey window, for inventory and spellbook tooltips. The second type uses just a text label, and is used for quick slots on the action bar, attribute bar names, and the names of actor effect icons. Next is chat text entry, and the general purpose amount dialog which is used in the inventory and trading windows. The trading and inventory windows themselves are then updated, but full description of each process is beyond the scope of this documentation. Finally comes a series of toggles for the various windows (either by hotkey, clicking the action bar icon, or clicking the window close buttons), followed by handling of the quit progress bar, and updating of the attribute displays and chat text area.

  
  
  

**UpdateCompass()**  
  
Return value: None  
  
Parameters: None  
  
This function scrolls the compass texture (by manipulating vertex texture coordinates) to match the current orientation of the player's character.

  
  
  

**UpdateSpellbook()**  
  
Return value: None  
  
Parameters: None  
  
This function updates the spellbook gadgets to reflect the current state of the player's known and memorised abilities.

  
  
  

**UpdateXPBar()**  
  
Return value: None  
  
Parameters: None  
  
This function scales the XP bar mesh to display the current progress towards the next level.

  
  
  

**UpdateTrading()**  
  
Return value: None  
  
Parameters: None  
  
This function updates the trading window with the current amounts available and being traded, and the current cost/profit to the player of the trade.

  
  
  

**UpdateEffectIcons()**  
  
Return value: None  
  
Parameters: None  
  
This function updates the textures of each [Interface->EffectIconSlot](interface.md#TEffectIconSlot) to display the current actor effects active on the player's character.

  
  
  

**CreateCharInteractionWindow()**  
  
Return value: None  
  
Parameters:  

*   _AI.ActorInstance_ - The character to interact with

  
This function creates a new 'interaction window' displaying a character's health, faction, level and reputation. It also contains a button which allows the player to interact with an NPC, e.g. mounting a horse, having a conversation, or trading. Only one interaction window should be created at once.

  
  
  

**CreateInterface()**  
  
Return value: None  
  
Parameters: None  
  
This function loads all media and creates the gadgets required for the interface. It is called from [ClientLoaders->LoadGame](clientloaders.md#FLoadGame), and should always be called after [Interface->LoadInterfaceSettings](interface.md#FLoadInterfaceSettings). At the end all the created windows are hidden ready to be displayed as required, and the [Interface->LastMouseMove](interface.md#GLastMouseMove) and [Interface->LastLeftClick](interface.md#GLastLeftClick) globals are given initial values.

  
  
  

**CreateInterfaceQuad(P)**  
  
Return value: Handle of the created mesh entity  
  
Parameters:  

*   _P_ - Optional handle of the parent entity (defaults to 0)

  
This function creates a quad mesh and returns the handle. The quad extends from -1 to 1 on the X and Y axes. It is used as a helper function within the module to create quads for displaying textures onscreen.

  
  
  

**EnableInventoryBlanks(Disable)**  
  
Return value: None  
  
Parameters:  

*   _Disable_ - True/False flag to disable rather than enabling blank inventory slots (defaults to False)

  
This function enables (or disables) the buttons in the inventory for empty inventory slots. This is used to make empty slots clickable when an item is picked up (so you can move it to that slot), but not clickable at other times. It also enables or disables the Drop and Use buttons. The Use button used to be known as Eat, and is referred to as such in the code.

  
  
  

**SetPickModes(Scenery, Actors, NonCombatants, DroppedItems)**  
  
Return value: None  
  
Parameters:  

*   _Scenery_ - True/False flag to enable pick modes on scenery objects (defaults to True)
*   _Actors_ - Pick mode to apply to actor instance entities other than the player (defaults to 0)
*   _Scenery_ - True/False flag to include non-combatant actor instances if the previous flag is non-zero (defaults to True)
*   _DroppedItems_ - Pick mode to apply to dropped item entities (defaults to 0)

  
This function sets pick modes for all pickable objects in the game. This is used to "filter" what can be picked or not at different points in the program. For instance, is is used when processing a right click to allow picking of scenery and all actor instances, but to ignore dropped items. It is also used in the **Client.bb** file to pick scenery only when finding the angle for character shadows or testing for player visibility when moving the camera.

  
  
  

**CreateActionBarButton(TexName$, X#, Toggle, FreeTex)**  
  
Return value: Handle of newly created button  
  
Parameters:  

*   _TexName$_ - Filename of the texture to apply to the button
*   _X#_ - X axis component of the position onscreen for the button
*   _Toggle_ - True/False flag for whether the button toggles states when clicked (defaults to True)
*   _FreeTex_ - True/Flag flag for whether to free the button texture once applied (defaults to True)

  
This function creates a new standard action bar button at the correct Y position, and of the correct size. It calls the [Gooey->DropGadget](gooey.md#FGYDropGadget) function to set the Z order of the new button to be underneath all other Gooey gadgets. It is used in [CreateInterface](#FCreateInterface) to create all the action bar buttons without using the same code repeatedly.

  
  
  

**CreateInventoryButton(W, S, Tex)**  
  
Return value: Handle of newly created button  
  
Parameters:  

*   _W_ - The handle of the Gooey window to create the button in
*   _S_ - The inventory slot number of the button
*   _Tex_ - The handle of the texture to apply to the button

  
This function creates a new standard inventory button with the correct position and size as defined in the [Interface->InventoryButtons](interface.md#GInventoryButtons) global array.

  
  
  

**RedrawQuestLog()**  
  
Return value: None  
  
Parameters: None  
  
This function updates all gadgets in the quest log window to reflect the current status of the player's quests.

  
  
  

**UpdateActionBarIcons()**  
  
Return value: None  
  
Parameters: None  
  
This function updates the textures on all action bar quick slot buttons to reflect the current items/abilities stored in the slots.

  
  
  

**UseItem(SlotIndex, Amount)**  
  
Return value: None  
  
Parameters:  

*   _SlotIndex_ - Inventory slot number to use the item from
*   _Amount_ - Amount of the item to use

  
This function uses an item from one of the player character's inventory slots. This can have various effects depending on the item type. If it is an ingredient or potion, the item is eaten and removed from the inventory. Otherwise, the item script (if there is one) is started by sending a message to the server, and if the item is an unequipped weapon or piece of armour it is equipped, displacing any item already equipped in the same slot.