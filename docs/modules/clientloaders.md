<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**ClientLoaders.bb**

This module contains the following functions:  

*   [LoadOptions](#FLoadOptions)
*   [LoadGame](#FLoadGame)

  

* * *

  
  
  

**LoadOptions()**  
  
Return value: None  
  
Parameters: None  
  
This function loads settings for the game from various files. It is called before the main menu is displayed.

  
  
  

**LoadGame()**  
  
Return value: None  
  
Parameters: None  
  
This function loads some general game settings only needed after the main menu, and creates game objects. Created objects include among others the game cameras, the actor instance for the player, the user interface, and the game environment.