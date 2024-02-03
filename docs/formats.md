**[Realm Crafter File Formats]{.underline}**

This document provides specifications for the formats of the following
files:

1.  **Data\\Options.dat**

2.  **Data\\Emiter Configs\\\*.rpc**

3.  **Data\\Game Data\\Animations.dat**

4.  **Data\\Game Data\\Combat.dat**

5.  **Data\\Controls.dat**

6.  **Data\\Game Data\\Fixed Attributes.dat**

7.  **Data\\Game Data\\Interface.dat**

8.  **Data\\Game Data\\Meshes.dat**

9.  **Data\\Game Data\\Misc.dat**

10. **Data\\Game Data\\Money.dat**

11. **Data\\Game Data\\Music.dat**

12. **Data\\Game Data\\Other.dat**

13. **Data\\Game Data\\Sounds.dat**

14. **Data\\Game Data\\Suns.dat**

15. **Data\\Game Data\\Textures.dat**

16. **Data\\Server Data\\Accounts.dat**

17. **Data\\Server Data\\Actors.dat**

18. **Data\\Server Data\\Attributes.dat**

19. **Data\\Server Data\\Damage.dat**

20. **Data\\Server Data\\Dropped Items.dat**

21. **Data\\Server Data\\Environment.dat**

22. **Data\\Server Data\\Factions.dat**

23. **Data\\Server Data\\Files.dat**

24. **Data\\Server Data\\Fixed Attributes.dat**

25. **Data\\Server Data\\Items.dat**

26. **Data\\Server Data\\Misc.dat**

27. **Data\\Server Data\\Projectiles.dat**

28. **Data\\Server Data\\Spells.dat**

29. **Data\\Server Data\\Superglobals.dat**

30. **Data\\Server Data\\Areas\\\*.dat**

31. **Data\\Server Data\\Areas\\Ownerships\\\*.dat**

32. **Data\\Areas\\\*.dat**

The definition of data types for this document is as follows:

\[BYTE\] -- a one byte unsigned integer value

\[SHORT\] -- a two byte unsigned integer value

\[INT\] -- a four byte signed integer value

\[FLOAT\] -- a four byte floating point value

\[STRING\] -- a length identifier of type \[INT\], followed by string
data, one byte per character

\[LINE\] -- a series of characters followed by a CRLF

**\\Data\\Options.dat**

This file contains game options set by the player. This file is in the
root Data folder as opposed to Data\\Game Data, to make sure it is not
overwritten by the auto-update system. The file is loaded in the
MainMenu module. The format is a simple list:

\[SHORT\] Width of in-game resolution

\[SHORT\] Height of in-game resolution

\[BYTE\] Color depth of in-game resolution (0, 16 or 32)

\[BYTE\] Anti-aliasing flag (true or false)

\[FLOAT\] Default volume (0.0 - 1.0)

**\\Data\\Emitter Configs\\\*.rpc**

This folder contains files defining emitter configurations for the
particle system. The files are loaded in the RottParticles module. The
format is a simple list:

\[INT\] Maximum number of particles

\[INT\] Particles spawned per 'frame'

\[INT\] Number of texture tiles horizontally

\[INT\] Number of texture tiles vertically

\[INT\] Random start frame flag (true or false)

\[INT\] Texture animation speed

\[INT\] Velocity shaping setting

\[FLOAT\] Initial velocity on the X axis

\[FLOAT\] Initial velocity on the Y axis

\[FLOAT\] Initial velocity on the Z axis

\[FLOAT\] Velocity random element on the X axis

\[FLOAT\] Velocity random element on the Y axis

\[FLOAT\] Velocity random element on the Z axis

\[FLOAT\] Force on the X axis

\[FLOAT\] Force on the Y axis

\[FLOAT\] Force on the Z axis

\[FLOAT\] Initial particle scale

\[FLOAT\] Change in particle scale per 'frame'

\[INT\] Particle lifespan in 'frames'

\[FLOAT\] Initial particle alpha value

\[FLOAT\] Change in particle alpha value per 'frame'

\[INT\] Blend mode (1 = normal, 2 = multiple, 3 = add)

\[INT\] Emitter shape (1 = sphere, 2 = cylinder, 3 = box)

\[FLOAT\] Minimum radius of emitter

\[FLOAT\] Maximum radius of emitter

\[FLOAT\] Emitter size on X axis

\[FLOAT\] Emitter size on Y axis

\[FLOAT\] Emitter size on Z axis

\[INT\] Emitter axis

\[SHORT\] ID of default texture

\[FLOAT\] Force modifier on X axis

\[FLOAT\] Force modifier on Y axis

\[FLOAT\] Force modifier on Z axis

\[INT\] Force shaping (?)

\[BYTE\] Inital red colour (0 - 255)

\[BYTE\] Inital green colour (0 - 255)

\[BYTE\] Inital blue colour (0 - 255)

\[FLOAT\] Change in particle red colour per \'frame\'

\[FLOAT\] Change in particle green colour per \'frame\'

\[FLOAT\] Change in particle blue colour per \'frame\'

**\\Data\\Game Data\\Animations.dat**

This file contains the animation sets used by actors. The file is loaded
in the Animations module. The file is of variable length depending on
the total number of animation sets. It consists of multiple blocks of
the following format:

\[SHORT\] ID of the animation set

\[STRING\] Name of the animation set

followed by 150 repetitions of:

> \[STRING\] Animation name
>
> \[SHORT\] Start frame of animation
>
> \[SHORT\] End frame of animation
>
> \[FLOAT\] Default speed of animation

There is no limit to the number of such blocks in the file. Reading
should continue until the end of the file is reached.

**\\Data\\Game Data\\Combat.dat**

This file contains two simple combat settings. It is used in GUE and the
ClientCombat module. The format is a simple list as follows:

\[SHORT\] Combat delay in milliseconds

\[BYTE\] Damage information style (1 = none, 2 = message, 3 = floating
number)

**\\Data\\Controls.dat**

This file contains key bindings for the client. It is used in the
Interface module. The format is a simple list as follows:

\[INT\] Scancode for forward key

\[INT\] Scancode for back key

\[INT\] Scancode for turn right key

\[INT\] Scancode for turn left key

\[INT\] Scancode for fly/swim up key

\[INT\] Scancode for fly/swim down key

\[INT\] Scancode for run key

\[INT\] Scancode for change view mode key

\[INT\] Scancode for camera right key

\[INT\] Scancode for camera left key

\[INT\] Scancode for camera zoom in key

\[INT\] Scancode for camera zoom out key

\[INT\] Scancode for jump key

\[BYTE\] Flag for invert axis in 1^st^ person (0 or 2)

\[BYTE\] Flag for invert axis in 3^rd^ person (0 or 2)

\[INT\] Scancode for attack key

\[INT\] Scancode for always run key

\[INT\] Scancode for cycle target key

\[INT\] Scancode for move to key

\[INT\] Scancode for talk to key

\[INT\] Scancode for select key

**\\Data\\Game Data\\Fixed Attributes.dat**

This file contains attribute mapping for the attributes built into the
client such as Health, Speed etc. It is used by GUE and Default Project.
The format is a simple list as follows:

\[SHORT\] Health attribute (0 - 39)

\[SHORT\] Energy attribute (0 - 39)

\[SHORT\] Breath attribute (0 - 39)

\[SHORT\] Strength attribute (0 - 39)

\[SHORT\] Speed attribute (0 - 39)

**\\Data\\Game Data\\Interface.dat**

This file contains the settings for the position and layout of the
various in-game interface components. It is used in the Interface
module. The format is a list of \[COMPONENT\] blocks. The definition of
a \[COMPONENT\] block is:

\[FLOAT\] X position (0.0 - 1.0)

\[FLOAT\] Y position (0.0 - 1.0)

\[FLOAT\] Width (0.0 - 1.0)

\[FLOAT\] Height (0.0 - 1.0)

\[FLOAT\] Alpha (0.0 - 1.0)

\[BYTE\] Red (0 - 255)

\[BYTE\] Green (0 - 255)

\[BYTE\] Blue (0 - 255)

The format of the entire file is as follows:

\[COMPONENT\] Chat text area

\[SHORT\] Background texture for the chat text area

\[COMPONENT\] Chat entry input box

\[COMPONENT\] Chat text background texture area

followed by 40 repetitions of:

\[COMPONENT\] Attribute status bar

\[COMPONENT\] Buffs (actor effects) area

\[COMPONENT\] Radar area

\[COMPONENT\] Compass area

\[COMPONENT\] Inventory window

\[COMPONENT\] Inventory drop button

\[COMPONENT\] Inventory use button

\[COMPONENT\] Inventory money display

followed by 46 repetitions of:

\[COMPONENT\] Inventory slot button

**\\Data\\Game Data\\Meshes.dat**

This file contains the meshes database. The file is used in the Media
module. The file is of variable length depending on the total number of
meshes in the database. The file consists of two blocks: an index, and
the actual data. The index provides offsets within the file for the
data. These file offsets are \[INT\]s. Media IDs range from 0 to 65534.
Since an \[INT\] is 4 bytes, the total length of the index is 65535 \* 4
bytes. The mesh data block is itself split into multiple blocks, one for
each mesh in the database. These blocks have the following format:

\[BYTE\] Animated mesh flag (true or false)

\[FLOAT\] Default mesh scale

\[FLOAT\] Mesh offset on the X axis

\[FLOAT\] Mesh offset on the Y axis

\[FLOAT\] Mesh offset on the Z axis

\[SHORT\] Shader ID (reserved but unused in RC1)

\[STRING\] Mesh filename

To access the data for a specific mesh, the procedure is to jump to
offset (Mesh ID \* 4) in the file, and read in an \[INT\]. This value is
the file offset of the relevant mesh data block.

**\\Data\\Game Data\\Misc.dat**

This file contains details used by the client such as the game name, and
whether automatic updates are enabled. It is used by GUE and the
MainMenu and ClientLoaders modules. The format is a simple text file:

\[LINE\] Game name

\[LINE\] Updater flag ("Development Version" or "Normal")

\[LINE\] Update music flag (0 or 1)

**\\Data\\Game Data\\Money.dat**

This file contains details of the tiers for the game currency. It is
used by GUE and the ClientLoaders module. The format is a simple list:

\[STRING\] Name of tier 1 (base) currency unit

\[STRING\] Name of tier 2 currency unit

\[SHORT\] Multiplier of tier 2 currency unit

\[STRING\] Name of tier 3 currency unit

\[SHORT\] Multiplier of tier 3 currency unit

\[STRING\] Name of tier 4 currency unit

\[SHORT\] Multiplier of tier 4 currency unit

**\\Data\\Game Data\\Music.dat**

This file contains the music database. The file is used in the Media
module. The file is of variable length depending on the total number of
music files in the database. The file consists of two blocks: an index,
and the actual data. The index provides offsets within the file for the
data. These file offsets are \[INT\]s. Media IDs range from 0 to 65534.
Since an \[INT\] is 4 bytes, the total length of the index is 65535 \* 4
bytes. The music data block is itself split into multiple blocks, one
for each music file in the database. These blocks have the following
format:

\[STRING\] Music filename

To access the data for a specific music file, the procedure is to jump
to offset (Music ID \* 4) in the file, and read in an \[INT\]. This
value is the file offset of the relevant music data block.

**\\Data\\Game Data\\Other.dat**

This file contains various settings for client features. The format is a
simple list as follows:

\[BYTE\] Hide actor nametags flag (0 or 1)

\[BYTE\] Disable actor -\> actor collisions (0 or 1)

\[BYTE\] Allowed view modes (1 - 3)

\[INT\] Server UDP port (1 - 65535)

\[BYTE\] Require ability memorisation flag (0 or 1)

\[BYTE\] Use speech bubbles flag (0 or 1)

\[BYTE\] Speech bubble red colour (0 - 255)

\[BYTE\] Speech bubble green colour (0 - 255)

\[BYTE\] Speech bubble blue colour (0 - 255)

**\\Data\\Game Data\\Sounds.dat**

This file contains the sounds database. The file is used in the Media
module. The file is of variable length depending on the total number of
sounds in the database. The file consists of two blocks: an index, and
the actual data. The index provides offsets within the file for the
data. These file offsets are \[INT\]s. Media IDs range from 0 to 65534.
Since an \[INT\] is 4 bytes, the total length of the index is 65535 \* 4
bytes. The sound data block is itself split into multiple blocks, one
for each sound in the database. These blocks have the following format:

\[BYTE\] 3D sound flag (0 or 1)

\[STRING\] Sound filename

To access the data for a specific sound, the procedure is to jump to
offset (Sound ID \* 4) in the file, and read in an \[INT\]. This value
is the file offset of the relevant sound data block.

**\\Data\\Game Data\\Suns.dat**

This file contains the settings for the suns in the game. It is used by
the Environment module. The file is of variable length depending on the
total number of suns. It consists of multiple blocks of the following
format:

\[SHORT\] Texture ID for this sun

\[FLOAT\] Sun size

\[BYTE\] Sun red colour (0 - 255)

\[BYTE\] Sun green colour (0 - 255)

\[BYTE\] Sun blue colour (0 - 255)

\[FLOAT\] Path angle (0 - 360)

followed by 12 repetitions of:

> \[BYTE\] Sun rise hour for this season
>
> \[BYTE\] Sun rise minute for this season
>
> \[BYTE\] Sun set hour for this season
>
> \[BYTE\] Sun set minute for this season

\[BYTE\] Display lens flare flag (0 or 1)

There is no limit to the number of such blocks in the file. Reading
should continue until the end of the file is reached.

**\\Data\\Game Data\\Textures.dat**

This file contains the textures database. The file is used in the Media
module. The file is of variable length depending on the total number of
textures in the database. The file consists of two blocks: an index, and
the actual data. The index provides offsets within the file for the
data. These file offsets are \[INT\]s. Media IDs range from 0 to 65534.
Since an \[INT\] is 4 bytes, the total length of the index is 65535 \* 4
bytes. The texture data block is itself split into multiple blocks, one
for each texture in the database. These blocks have the following
format:

\[SHORT\] Texture flags

\[STRING\] Texture filename

To access the data for a specific texture, the procedure is to jump to
offset (Texture ID \* 4) in the file, and read in an \[INT\]. This value
is the file offset of the relevant texture data block.

**\\Data\\Server Data\\Accounts.dat**

This file contains details of all player accounts on the server. It is
used by the AccountsServer module. The format consists of multiple
blocks of the following format:

\[STRING\] Account username

\[STRING\] Account password (MD5 hash)

\[STRING\] Account email address

\[BYTE\] GM status flag (0 or 1)

\[BYTE\] Ban status flag (0 or 1)

\[BYTE\] Number of characters in this account

\[STRING\] Comma delimited list of accounts (not character names)
ignored by this account

followed by \<number of characters\> repetitions of:

\[ACTORINSTANCE\] All details of the character

followed by 500 repetitions of:

\[STRING\] Quest log entry name

\[STRING\] Quest log entry status

followed by 36 repetitions of:

\[STRING\] Action bar slot contents

There is no limit to the number of such blocks in the file. Reading
should continue until the end of the file is reached. The definition of
an \[ACTORINSTANCE\] block is as follows:

\[SHORT\] ID for the actor of which this is an instance

\[STRING\] Name of zone this actor is in

\[STRING\] Actor name

\[INT\] Team ID (set with SetActorGroup script command)

\[FLOAT\] Actor X position

\[FLOAT\] Actor Y position

\[FLOAT\] Actor Z position

\[BYTE\] Actor gender (0 = male or none, 1 = female)

\[INT\] XP points

\[BYTE\] XP bar level

\[SHORT\] Actor level

\[SHORT\] Face texture (0 - 4)

\[SHORT\] Hair (0 - 4)

\[SHORT\] Beard (0 - 4)

\[SHORT\] Body texture (0 - 4)

followed by 40 repetitions of:

\[SHORT\] Attribute value

\[SHORT\] Attribute maximum

followed by 20 repetitions of:

\[SHORT\] Damage type resistance value

followed by 50 repetitions of:

\[ITEMINSTANCE\] Item in this inventory slot

\[SHORT\] Quantity of this item

\[STRING\] Name of right click script

\[STRING\] Name of death script

\[SHORT\] Reputation

\[INT\] Money (in base units)

\[BYTE\] Number of slaves (pets) this actor instance has

\[BYTE\] ID of home faction

followed by 100 repetitions of:

\[BYTE\] Rating with this faction

followed by 10 repetitions of:

\[STRING\] Actor global

followed by 1000 repetitions of:

\[SHORT\] ID of known spell

\[SHORT\] Level of known spell

followed by 10 repetitions of:

\[SHORT\] Memorised spell number (0 - 999)

followed by \<number of slaves\> repetitions of:

\[ACTORINSTANCE\] Slave actor instance data

The definition of an \[ITEMINSTANCE\] block is as follows:

\[SHORT\] ID for the item of which this is an instance

if the ID is \> 0 and \< 65535, followed by 40 repetitions of:

\[SHORT\] Attribute value

\[BYTE\] Item health level (0 - 100)

**\\Data\\Server Data\\Actors.dat**

This file contains the data for all actor templates created. The file is
loaded in the Actors module. The format consists of multiple blocks of
the following format:

\[SHORT\] ID of this actor

\[STRING\] Name of race

\[STRING\] Name of class

\[STRING\] Actor description

\[STRING\] Name of starting zone

\[STRING\] Name of starting portal

\[SHORT\] ID of animation set for male instances

\[SHORT\] ID of animation set for female instances

\[FLOAT\] Actor scale

\[FLOAT\] Actor radius (approximate, for server use)

\[SHORT\] Male mesh ID

\[SHORT\] Female mesh ID

followed by 6 repetitions of:

> \[SHORT\] Mesh ID for gubbin

followed by 5 repetitions of:

> \[SHORT\] Mesh ID for beard

followed by 5 repetitions of:

> \[SHORT\] Mesh ID for male hair

followed by 5 repetitions of:

> \[SHORT\] Mesh ID for female hair

followed by 5 repetitions of:

> \[SHORT\] Texture ID for male hair

followed by 5 repetitions of:

> \[SHORT\] Texture ID for female hair

followed by 5 repetitions of:

> \[SHORT\] Texture ID for male body

followed by 5 repetitions of:

> \[SHORT\] Texture ID for female body

followed by 16 repetitions of:

> \[SHORT\] Sound ID for male speech

followed by 16 repetitions of:

> \[SHORT\] Sound ID for female speech

\[SHORT\] Blood particle texture ID

followed by 40 repetitions of:

\[SHORT\] Initial attribute value

\[SHORT\] Initial attribute maximum

\[BYTE\] Valid genders for actor (0 - 3)

\[BYTE\] Playable flag (0 or 1)

\[BYTE\] Rideable flag (0 or 1)

\[BYTE\] Aggressiveness level (0 - 3)

\[INT\] Aggressiveness range

\[BYTE\] Trading mode (0 - 2)

\[BYTE\] Environment mode (0 - 3)

\[INT\] Flags for inventory slots (each bit is a slot)

\[BYTE\] Damage type for unarmed combat

\[BYTE\] Default home faction

\[INT\] XP multiplier

\[BYTE\] Triangle collision flag (0 or 1)

There is no limit to the number of such blocks in the file. Reading
should continue until the end of the file is reached.

**\\Data\\Server Data\\Attributes.dat**

This file contains the names of the attributes. The file is used by the
Actors module. The format is a simple list:

\[BYTE\] Number of attribute points available to spend in character
creation

followed by 40 repetitions of:

\[STRING\] Attribute name

\[BYTE\] Attribute is skill flag (0 or 1)

**\\Data\\Server Data\\Damage.dat**

This file contains the names of the damage types. The file is used by
GUE and the Items module. The format is a simple list:

20 repetitions of:

\[STRING\] Damage type name

**\\Data\\Server Data\\Dropped Items.dat**

This file saves the details of all items dropped on the floor of zones.
It is used by the Items module. The format consists of multiple blocks
of the following format:

\[STRING\] Item instance data (see ItemInstanceToString function)

\[SHORT\] Item quantity

\[FLOAT\] X position in zone

\[FLOAT\] Y position in zone

\[FLOAT\] Z position in zone

There is no limit to the number of such blocks in the file. Reading
should continue until the end of the file is reached.

**\\Data\\Server Data\\Environment.dat**

This file contains the settings for the game environment, such as season
names, times and dates, etc. The format is a simple list:

\[INT\] Current year

\[INT\] Current day

\[INT\] Current hour

\[INT\] Current minute

\[INT\] Time compression factor

followed by 12 repetitions of:

\[STRING\] Season name

\[INT\] Season start day

\[INT\] Season dusk hour

\[INT\] Season dawn hour

followed by 20 repetitions of:

\[STRING\] Month name

\[INT\] Month start day

**\\Data\\Server Data\\Factions.dat**

This file contains the names and ratings of all factions. It is used by
the Actors module. The format is a simple list:

100 repetitions of:

\[STRING\] Faction name

100 repetitions of:

100 repetitions of:

\[BYTE\] Default rating of faction with faction

**\\Data\\Server Data\\Files.dat**

This file contains a list of all files which should be present in a full
client build of the game. The server sends the details to clients for
the use of the auto-update system. The file is used by GUE and the
UpdatesServer module. The format consists of multiple blocks of the
following format:

\[STRING\] Filename

\[INT\] File checksum

There is no limit to the number of such blocks in the file. Reading
should continue until the end of the file is reached.

**\\Data\\Server Data\\Fixed Attributes.dat**

This file contains attribute mapping for the attributes built into the
server such as Health, Speed etc. It is used by GUE and Server. The
format is a simple list as follows:

\[SHORT\] Health attribute (0 - 39)

\[SHORT\] Energy attribute (0 - 39)

\[SHORT\] Breath attribute (0 - 39)

\[SHORT\] Toughness attribute (0 - 39)

\[SHORT\] Strength attribute (0 - 39)

\[SHORT\] Speed attribute (0 - 39)

**\\Data\\Server Data\\Items.dat**

This file contains the data for all item templates created. The file is
loaded in the Items module. The format consists of multiple blocks of
the following format:

\[SHORT\] Item ID

\[STRING\] Item name

\[STRING\] Exclusive race name

\[STRING\] Exclusive class name

\[STRING\] Item use script

\[STRING\] Item use script function

\[BYTE\] Item type (1 - 7)

\[INT\] Value

\[SHORT\] Mass

\[BYTE\] Takes damage flag (0 or 1)

\[SHORT\] Texture ID for item thumbnail image

followed by 6 repetitions of:

\[SHORT\] Gubbin activation flag (0 or 1)

\[SHORT\] Male mesh ID

\[SHORT\] Female mesh ID

\[SHORT\] Item slot type (1 - 11)

\[BYTE\] Stackable flag (0 or 1)

followed by 40 repetitions of:

\[SHORT\] Attribute value (with +5000 modifier)

The next section depends on the item type:

if the item type is I_Weapon (1):

\[SHORT\] Weapon damage

\[SHORT\] Weapon damage type

\[SHORT\] Weapon type (1 - 3)

\[SHORT\] Ranged projectile ID

\[FLOAT\] Maximum range

\[STRING\] Ranged firing animation

if the item type is I_Armour (2):

\[SHORT\] Armour level

if the type is I_Potion (4) or I_Ingredient (5):

\[SHORT\] Length of actor effect (buff)

if the type is I_Image (6):

\[SHORT\] Texture ID for image to display

if the type is I_Other (7):

\[STRING\] Misc item data for scripting use

There is no limit to the number of such blocks in the file. Reading
should continue until the end of the file is reached.

**\\Data\\Server Data\\Misc.dat**

This file contains miscellaneous server settings. It is used by GUE and
Server. The format is a simple list as follows:

\[INT\] Initial money level for new characters

\[INT\] Initial reputation for new characters

\[BYTE\] Flag for forced portal transfer (0 or 1)

\[SHORT\] Combat delay time

\[BYTE\] Combat formula to use

\[BYTE\] Flag for weapon damage during combat (0 or 1)

\[BYTE\] Flag for armour damage during combat (0 or 1)

\[BYTE\] Flag for rating adjustment after combat (0 or 1)

\[BYTE\] Flag for allowing clients to create new accounts (0 or 1)

\[BYTE\] Maximum characters allowed per account (1 - 10)

\[INT\] Server UDP port (1 - 65535)

\[BYTE\] Flag to require memorisation of abilities (0 or 1)

**\\Data\\Server Data\\Projectiles.dat**

This file stores the settings for the projectile types. The file is
loaded in the Projectiles module. The format consists of multiple blocks
of the following format:

\[SHORT\] Projectile ID

\[STRING\] Projectile name

\[SHORT\] Mesh ID for projectile

\[STRING\] Name of emitter #1

\[STRING\] Name of emitter #2

\[SHORT\] Texture ID for emitter #1

\[SHORT\] Texture ID for emitter #2

\[BYTE\] Homing flag (0 or 1)

\[BYTE\] Hit chance (1 - 100)

\[SHORT\] Projectile damage

\[SHORT\] Damage type

\[BYTE\] Projectile speed

There is no limit to the number of such blocks in the file. Reading
should continue until the end of the file is reached.

**\\Data\\Server Data\\Spells.dat**

This file stores the settings for abilities (referred to as "spells" in
the code). The file is loaded in the Spells module. The format consists
of multiple blocks of the following format:

\[SHORT\] Ability ID

\[STRING\] Ability name

\[STRING\] Ability description

\[SHORT\] Texture ID for thumbnail image

\[STRING\] Exclusive race name

\[STRING\] Exclusive class name

\[INT\] Recharge time

\[STRING\] Projectile script name

\[STRING\] Projectile script function

There is no limit to the number of such blocks in the file. Reading
should continue until the end of the file is reached.

**\\Data\\Server Data\\Superglobals.dat**

This file saves the contents of the 100 script superglobals. The file is
loaded in the Scripting module. The format is a simple list:

100 repetitions of:

\[STRING\] Superglobal value

**\\Data\\Server Data\\Areas\\\*.dat**

This folder contains an unlimited number of zone data files (server
side). Each file has a counterpart in \\Data\\Areas\\ (documented
below). These files are used in the ServerAreas module. The format is a
simple list as follows:

\[BYTE\] Probability of rain

\[BYTE\] Probability of snow

\[BYTE\] Probability of fog

\[BYTE\] Probability of storm

\[BYTE\] Probability of wind

\[STRING\] Zone entry script

\[STRING\] Zone exit script

\[BYTE\] PvP flag (0 or 1)

\[SHORT\] Gravity level

\[BYTE\] Outdoors flag (0 or 1)

\[STRING\] Name of weather link zone

followed by 150 repetitions of:

\[FLOAT\] Trigger X position

\[FLOAT\] Trigger Y position

\[FLOAT\] Trigger Z position

\[FLOAT\] Trigger radius

\[STRING\] Trigger script

\[STRING\] Trigger script function

followed by 2000 repetitions of:

\[FLOAT\] Waypoint X position

\[FLOAT\] Waypoint Y position

\[FLOAT\] Waypoint Z position

\[SHORT\] Next waypoint A

\[SHORT\] Next waypoint B

\[SHORT\] Previous waypoint

followed by 100 repetitions of:

\[STRING\] Portal name

\[STRING\] Portal linked zone name

\[STRING\] Portal linked portal name

\[FLOAT\] Portal X position

\[FLOAT\] Portal Y position

\[FLOAT\] Portal Z position

\[FLOAT\] Portal radius

\[FLOAT\] Portal yaw (rotation angle)

followed by 1000 repetitions of:

\[SHORT\] Spawn point actor ID

\[SHORT\] Spawn point waypoint number

\[FLOAT\] Spawn point radius

\[STRING\] Spawn script name

\[STRING\] Spawned actor right click script name

\[STRING\] Spawned actor death script name

\[SHORT\] Maximum number of actors to spawn

\[SHORT\] Spawn delay

\[FLOAT\] Auto-movement range

\[SHORT\] Number of water areas

followed by \<number of water areas\> repetitions of:

\[FLOAT\] Water X position

\[FLOAT\] Water Y position

\[FLOAT\] Water Z position

\[FLOAT\] Water X size

\[FLOAT\] Water Z size

\[SHORT\] Water damage

\[SHORT\] Water damage type

**\\Data\\Server Data\\Areas\\Ownerships\\\*.dat**

This folder contains an unlimited number of zone scenery ownership data
files. Each file corresponds to a zone data file in \\Data\\Server
Data\\Areas\\ (documented above). The number in parentheses after the
zone name is the instance ID for the ownership data, numbered 0 - 99.
These files are used in the ServerAreas module. The format is as
follows:

500 repetitions of:

\[BYTE\] This scenery ID exists

if \<this scenery ID exists\> is 1:

\[STRING\] Owner account name

\[BYTE\] Owner character number

\[BYTE\] Scenery inventory size

if \<scenery inventory size\> is non-zero:

\<scenery inventory size\> repetitions of:

\[ITEMINSTANCE\] Item in this slot

\[SHORT\] Item quantity

The \[ITEMINSTANCE\] block is documented above as part of the
\\Data\\Server Data\\Accounts.dat file.

**\\Data\\Areas\\\*.dat**

This folder contains an unlimited number of zone data files (client
side). Each file has a counterpart in \\Data\\Server Data\\Areas\\
(documented above). These files are used in the ClientAreas module. The
format is as follows:

\[SHORT\] Texture ID of zone loading screen image

\[SHORT\] Music ID of zone loading music

\[SHORT\] Texture ID of sky

\[SHORT\] Texture ID of clouds

\[SHORT\] Texture ID of storm clouds

\[SHORT\] Texture ID of stars

\[BYTE\] Fog red colour (0 - 255)

\[BYTE\] Fog green colour (0 - 255)

\[BYTE\] Fog blue colour (0 - 255)

\[FLOAT\] Fog minimum distance

\[FLOAT\] Fog maximum distance

\[SHORT\] Texture ID of map for this zone

\[BYTE\] Outdoors flag (0 or 1)

\[BYTE\] Ambient light red colour (0 - 255)

\[BYTE\] Ambient light green colour (0 - 255)

\[BYTE\] Ambient light blue colour (0 - 255)

\[FLOAT\] Default light pitch (-90 - 90)

\[FLOAT\] Default light yaw (-180 - 180)

\[FLOAT\] Slope movement restriction coefficient (0 - 1)

\[SHORT\] Number of scenery objects

followed by \<number of scenery objects\> repetitions of:

\[SHORT\] Mesh ID of scenery object

\[FLOAT\] Scenery position X

\[FLOAT\] Scenery position Y

\[FLOAT\] Scenery position Z

\[FLOAT\] Scenery pitch angle

\[FLOAT\] Scenery yaw angle

\[FLOAT\] Scenery roll angle

\[FLOAT\] Scenery scale X

\[FLOAT\] Scenery scale Y

\[FLOAT\] Scenery scale Z

\[BYTE\] Scenery animation mode

\[BYTE\] Scenery ownership ID

\[SHORT\] Texture ID for mesh retexturing

\[BYTE\] Stop rain particles?

\[BYTE\] Collision mode

\[STRING\] Lightmap texture

\[STRING\] Reserved for RCTE use

\[SHORT\] Number of water areas

followed by \<number of water areas\> repetitions of:

\[SHORT\] Texture ID for water

\[FLOAT\] Water texture scale

\[FLOAT\] Water position X

\[FLOAT\] Water position Y

\[FLOAT\] Water position Z

\[FLOAT\] Water scale X

\[FLOAT\] Water scale Z

\[BYTE\] Water red colour (0 - 255)

\[BYTE\] Water green colour (0 - 255)

\[BYTE\] Water blue colour (0 - 255)

\[BYTE\] Water opacity (0 - 255)

\[SHORT\] Number of collision boxes

followed by \<number of collision boxes\> repetitions of:

\[FLOAT\] Collision box position X

\[FLOAT\] Collision box position Y

\[FLOAT\] Collision box position Z

\[FLOAT\] Collision box pitch angle

\[FLOAT\] Collision box yaw angle

\[FLOAT\] Collision box roll angle

\[FLOAT\] Collision box scale X

\[FLOAT\] Collision box scale Y

\[FLOAT\] Collision box scale Z

\[SHORT\] Number of emitters

followed by \<number of emitters\> repetitions of:

\[STRING\] Emitter configuration name

\[SHORT\] Texture ID for particles

\[FLOAT\] Emitter position X

\[FLOAT\] Emitter position Y

\[FLOAT\] Emitter position Z

\[FLOAT\] Emitter pitch angle

\[FLOAT\] Emitter yaw angle

\[FLOAT\] Emitter roll angle

\[SHORT\] Number of terrains

followed by \<number of terrains\> repetitions of:

\[SHORT\] Texture ID of base texture

\[SHORT\] Texture ID of detail texture

\[INT\] Terrain grid size (must be power of 2)

followed by \<grid size\> + 1 repetitions of:

followed by \<grid size\> + 1 repetitions of:

\[FLOAT\] Node height (0.0 - 1.0)

\[FLOAT\] Terrain position X

\[FLOAT\] Terrain position Y

\[FLOAT\] Terrain position Z

\[FLOAT\] Terrain pitch angle

\[FLOAT\] Terrain yaw angle

\[FLOAT\] Terrain roll angle

\[FLOAT\] Terrain scale X

\[FLOAT\] Terrain scale Y

\[FLOAT\] Terrain scale Z

\[FLOAT\] Detail texture scale

\[INT\] Terrain detail level

\[BYTE\] Morphing flag (0 or 1)

\[BYTE\] Shading flag (0 or 1)

\[SHORT\] Number of sound zones

followed by \<number of sound zones \> repetitions of:

\[FLOAT\] Sound zone position X

\[FLOAT\] Sound zone position Y

\[FLOAT\] Sound zone position Z

\[FLOAT\] Sound zone radius

\[SHORT\] Sound ID of zone

\[SHORT\] Music ID of zone

\[INT\] Repeat time

\[BYTE\] Volume
