<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

The following is a list of all the modules used by Realm Crafter, followed by a list of which modules are used by the client and the server. Click any module name to see the full documentation for its code.

**List of all modules:**

*   [AccountsServer](modules/accountsserver.md)  
    Contains functions to create and manage user accounts on the server, and the accounts window.
*   [GameServer](modules/gameserver.md)  
    Contains functions to update the state of all game logic on the server, and to create and manage the server game window.
*   [UpdatesServer](modules/updatesserver.md)  
    Contains functions to create and manage the server updates window, and to load update files information from disk.
*   [Actors](modules/actors.md)  
    Contains functions to create, update, save, and delete actor templates and instances, which are the people - both human and AI controlled - of the game world.
*   [Actors3D](modules/actors3d.md)  
    Contains functions relating to the creation, management and deletion of all client side 3D rendering data for actor instances.
*   [Animations](modules/animations.md)  
    Contains functions to load animation set data, apply it to actors, and save it back to disk.
*   [CharacterEditorLoader](modules/charactereditorloader.md)  
    Contains the function to load special 3D rendering data for actors created using the Character Editor tool.
*   [ClientAreas](modules/clientareas.md)  
    Contains functions to load, save, and delete client side zone data.
*   [ClientCombat](modules/clientcombat.md)  
    Contains functions to load combat settings, update the combat interface, perform combat animations on actor instances, and display combat damage.
*   [ClientLoaders](modules/clientloaders.md)  
    Contains functions to load client side data, such as host names, game options, sky spheres and the user interface.
*   [ClientNet](modules/clientnet.md)  
    Contains the function to connect to the server, and the function to process all received network messages and send player updates to the server.
*   [Environment](modules/environment.md)  
    Contains functions to load, save, and manage game environment settings, such as the time of day and weather.
*   [Environment3D](modules/environment3d.md)  
    Contains functions to load and update 3D rendering data for the game environment, such as rain/snow, suns, and 3D weather sound effects.
*   [Gooey](modules/gooey.md)  
    Custom resolution independent user interface library, used for most of the in-game interface.
*   [Gooey\_3D\_Text](modules/gooey_3d_text.md)  
    Contains functions to render text in a 3D environment for the Gooey module (see above).
*   [Interface](modules/interface.md)  
    Contains functions to load and save positioning data for the in-game interface and other related settings.
*   [Interface3D](modules/interface3d.md)  
    Contains functions to load and update the actual in-game interface components, and to manage all player input.
*   [Inventories](modules/inventories.md)  
    Contains functions to manage the inventories of actor instances.
*   [Items](modules/items.md)  
    Contains functions to load, save, and manage the items which actor instances can carry and use in the game.
*   [Language](modules/language.md)  
    Loads and provides access to all string constants from a text file, to assist localisation of Realm Crafter games.
*   [Logging](modules/logging.md)  
    Contains functions to create and write to log files.
*   [MainMenu](modules/mainmenu.md)  
    Runs the main menu on client startup.
*   [MD5](modules/md5.md)  
    Contains a function to return the MD5 checksum of a string.
*   [Media](modules/media.md)  
    Contains functions to manage the media databases, which contain information about mesh, texture, sound and music files.
*   [MediaDialogs](modules/mediadialogs.md)  
    Provides a set of standard dialogs to allow the user to choose files from the media databases in a FUI application.
*   [MySQL](modules/mysql.md)  
    Provides MySQL functionality for the server.
*   [Packets](modules/packets.md)  
    Contains a set of constants defining the various packet types for network messages.
*   [Projectiles](modules/projectiles.md)  
    Contains functions to load, save, and update projectiles and projectile settings.
*   [Projectiles3D](modules/projectiles3d.md)  
    Contains functions to create and update client side 3D rendering data for projectile instances.
*   [Radar](modules/radar.md)  
    Contains functions to create and update the in-game mini-map.
*   [RCTrees](modules/rctrees.md)  
    Contains functions to load and update trees created in the Tree Editor tool.
*   [RottNet](modules/rottnet.md)  
    This is the network library used by Realm Crafter, which sets up and manages network connections, and sends/receives all network messages.
*   [RottParticles](modules/rottparticles.md)  
    Contains functions to create, load, save, and update 3D particle effects.
*   [Scripting](modules/scripting.md)  
    The scripting interpreter which loads and runs user-created scripts on the server.
*   [ServerAreas](modules/serverareas.md)  
    Contains functions to load, save, and delete server side zone data. It also contains a function to update the current weather in a zone.
*   [ServerNet](modules/servernet.md)  
    Processes all received network messages and sends updates to all connected clients.
*   [Spells](modules/spells.md)  
    Contains functions to create, load, and save ability data. Abilities are scripted powers which can be used by actor instances.

  

**Module usage:**

*   **Server**

*   RottNet
*   Environment
*   Items
*   Projectiles
*   Spells
*   Actors
*   Inventories
*   ServerAreas
*   Scripting
*   Logging
*   AccountsServer
*   GameServer
*   UpdatesServer
*   Packets
*   ServerNet
*   MySQL

*   **Client**

*   Language
*   RottParticles
*   RottNet
*   Media
*   Environment
*   Environment3D
*   Animations
*   Spells
*   Items
*   Inventories
*   Actors
*   Actors3D
*   ClientCombat
*   Interface
*   Interface3D
*   Radar
*   RCTrees
*   ClientAreas
*   Logging
*   ClientLoaders
*   ClientNet
*   MainMenu
*   Packets
*   Gooey
*   MD5
*   Projectiles3D