<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**UpdatesServer.bb**

This module contains the following globals:  

*   [Updates.UpdatesWindow](#GUpdates)

This module contains the following types:  

*   [UpdatesWindow](#TUpdatesWindow)
*   [UpdateFile](#TUpdateFile)

This module contains the following functions:  

*   [LoadUpdateFiles](#FLoadUpdateFiles)
*   [CreateUpdatesWindow](#FCreateUpdatesWindow)

  

* * *

  

**Updates.UpdatesWindow (global)**  
  
This global contains the object representing the server's Updates window, and is set when the window is created.

  

* * *

  

**UpdatesWindow (type)**  
  
This type represents a server Updates window. It contains the handles of the window and all child gadgets.

  

**UpdateFile (type)**  
  
This type represents a file which is processed by the auto-update system. It contains the name and checksum of the file.

  

* * *

  
  
  

**LoadUpdateFiles()**  
  
Return value: Total number of UpdateFiles loaded  
  
Parameters: None  
  
This function loads all data from Files.dat, which are then sent as required to clients needing to check for and download the latest updates.

  
  
  

**CreateUpdatesWindow.UpdatesWindow()**  
  
Return value: Handle of newly created UpdatesWindow object  
  
Parameters: None  
  
This function creates a new UpdatesWindow and all gadgets, then returns the handle.