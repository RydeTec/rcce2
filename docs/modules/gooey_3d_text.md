<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**Gooey\_3D\_Text.bb**

This module contains the following constants:  

*   [GY\_Letters$](#CGYLetters)

This module contains the following globals:  

*   [GY\_Font\_Spacing(256)](#GGYFontSpacing)
*   [GY\_Font\_OffsetX(256)](#GGYFontOffsetX)
*   [GY\_Font\_OffsetY(256)](#GGYFontOffsetY)

This module contains the following types:  

*   [GY\_Font](#TGYFont)
*   [GY\_Text3D](#TGYText3D)

This module contains the following functions:  

*   [GY\_TextWidth](#FGYTextWidth)
*   [GY\_Create3DText](#FGYCreate3DText)
*   [GY\_Position3DText](#FGYPosition3DText)
*   [GY\_Set3DText](#FGYSet3DText)
*   [GY\_Free3DText](#FGYFree3DText)
*   [GY\_FreeFont](#FGYFreeFont)
*   [GY\_LoadFont](#FGYLoadFont)
*   [GY\_GenerateFont](#FGYGenerateFont)

  

* * *

  

**GY\_Letters$ (constant)**  
  
This constant contains all characters which can be displayed using Gooey's 3D text.

  

* * *

  

**GY\_Font\_Spacing(256) (global)**  
  
This global array is used as a temporary storage area by the [GY\_GenerateFont](#FGYGenerateFont) function.

  

**GY\_Font\_OffsetX(256) (global)**  
  
This global array is used as a temporary storage area by the [GY\_GenerateFont](#FGYGenerateFont) function.

  

**GY\_Font\_OffsetY(256) (global)**  
  
This global array is used as a temporary storage area by the [GY\_GenerateFont](#FGYGenerateFont) function.

  

* * *

  

**GY\_Font (type)**  
  
This type represents a font used to render Gooey 3D text. Fonts are loaded individually with the [GY\_LoadFont](#FGYLoadFont) function.

  

**GY\_Text3D (type)**  
  
This type represents a section of Gooey 3D text. It stores the entity handle for the text mesh, the font object used for the text, the maximum possible number of characters which can be displayed in this section of text, and the scale of the text on the X axis.

  

* * *

  
  
  

**GY\_TextWidth#(TextHandle, Dat$)**  
  
Return value: The screen width the specified string would take up if rendered  
  
Parameters:  

*   _TextHandle_ - The [GY\_Text3D](#TGYText3D) object which would be used to render the string
*   _Dat$_ - The string to test

  
This function returns the screen width which a particular string would take if it were rendered. Since this value can change depending on the font and scale of a text section, the value is provided for a specific GY\_Text3D object. The value returned is a floating point number between 0.0 and 1.0 representing a fraction of the screen width.

  
  
  

**GY\_Create3DText(X#, Y#, Width#, Height#, MaxLength, Font, Parent, ZOrder)**  
  
Return value: The entity handle of the newly created Gooey 3D text object  
  
Parameters:  

*   _X#_ - The X component of the screen position of the text
*   _Y#_ - The Y component of the screen position of the text
*   _Width#_ - The screen width of the text
*   _Height#_ - The screen height of the text
*   _MaxLength_ - The maximum number of characters this object will be able to display
*   _Font_ - The handle of the Gooey 3D text font to use
*   _Parent_ - Optional parent entity for the text (defaults to 0)
*   _ZOrder_ - Rendering Z order for the text (defaults to -1)

  
This function creates a new section of text and returns the handle of the entity. The mesh consists of a series of seperate quads, each representing a single letter. The X, Y, Width and Height parameters should be a fraction of the screen width/height between 0.0 and 1.0. For instance, an X parameter of 0.5 would mean that the text would be  positioned exactly halfway across the screen. It is inadvisable to use this function directly for the display of text; the label objects in the [Gooey](gooey.md) module provide a more fully featured and compatible derivation.

  
  
  

**GY\_Position3DText(TextHandle, X#, Y#)**  
  
Return value: None  
  
Parameters:  

*   _TextHandle_ - A 3D text entity previously created with [GY\_Create3DText](#FGYCreate3DText)
*   _X#_ - The X component of the new screen position of the text
*   _Y#_ - The Y component of the new screen position of the text

  
This function allows 3D text objects to be repositioned onscreen.

  
  
  

**GY\_Set3DText(TextHandle, Dat$)**  
  
Return value: Success flag  
  
Parameters:  

*   _TextHandle_ - A 3D text entity previously created with [GY\_Create3DText](#FGYCreate3DText)
*   _Dat$_ - The new string to display

  
This function is used to set the string to display in a 3D text object. Each quad in the mesh is assigned new texture coordinates to display the correct letter. If the 3D text object is not found, False is returned. The string given should not be longer than the MaxLength specified when creating the 3D text.

  
  
  

**GY\_Free3DText(TextHandle, FreeFontAlso)**  
  
Return value: Success flag  
  
Parameters:  

*   _TextHandle_ - A 3D text entity previously created with [GY\_Create3DText](#FGYCreate3DText)
*   _FreeFontAlso_ - True/False flag for whether to free the font used by the 3D text object (defaults to False)

  
This function is used to free 3D text. If the 3D text object is not found, False is returned.

  
  
  

**GY\_FreeFont(FontHandle)**  
  
Return value: Success flag  
  
Parameters:  

*   _FontHandle_ - The handle of the Gooey 3D text font to free

  
This function is used to free a GY 3D text font. If the font is not found, False is returned.

  
  
  

**GY\_LoadFont(Name$)**  
  
Return value: Newly created Gooey 3D text font handle  
  
Parameters:  

*   _Name$_ - The name of the Gooey 3D text font to load

  
This function is used to load a GY 3D text font from disk. If the font data file or texture is not found, 0 is returned.

  
  
  

**GY\_GenerateFont(Name$, Font$, Height, R, G, B, SR, SG, SB, Bold, Italic, UnderL, Shadow)**  
  
Return value: None  
  
Parameters:  

*   _Name$_ - The output name for the font
*   _Font$_ - The name of the system font to generate the output font from
*   _Height_ - The height in pixels to use for the font (recommended ~54)
*   _R_ - The red component of the output font colour
*   _G_ - The green component of the output font colour
*   _B_ - The blue component of the output font colour
*   _R_ - The red component of the output font shadow colour
*   _G_ - The green component of the output font shadow colour
*   _B_ - The blue component of the output font shadow colour
*   _Bold_ - True/False flag to enable bold on the font
*   _Italic_ - True/False flag to enable italics on the font
*   _UnderL_ - True/False flag to enable underlining on the font
*   _Shadowe_ - True/False flag to include a shadow on the output font

  
This function generates a new Gooey 3D text font, saving a data file and texture to the current directory. The font can then be loaded by the [GY\_LoadFont](#FGYLoadFont) function and used for rendering text.