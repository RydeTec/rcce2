<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

Warning: It is assumed at this point that you have a basic knowledge of the Blitz3D/BlitzPlus programming languages. If not, you should refer to the Blitz documentation and community for help in learning the language.

Realm Crafter is essentially split into two seperate programs -- the client and the server. When you compile the code you must open _Client.bb_ (the client) in Blitz3D and _Server.bb_ in BlitzPlus. These two files are the ones present in the root folder of your project. All files in the \\Modules folder (which make up most of the code) are used as Include files by _Client.bb_ and _Server.bb_.

Tips and tricks:

*   Running the client in debug mode will automatically use windowed graphics
*   In this mode it is possible to run multiple clients on a single PC
*   The server runs extremely slowly in debug mode, use it as little as possible
*   In the code, abilities are usually referred to as spells, and zones as areas
*   Add more here as I think of them