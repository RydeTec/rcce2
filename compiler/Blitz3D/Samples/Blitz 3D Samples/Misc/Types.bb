;NOTE: if this program does not run on your system, then change the graphics mode below to 640,480  
;'type' basics and applying them to your Blitz3D coding
;My machine (PII 350, 256mb 133mhz RAM, GeForce2 MX 32mb) can handle about 700 cubes onscreen before
;it slows down.
;Last built Wednesday, 8th October 10:20am (Blitz3D beta version, binaries 1.55)

;You can think of Types as something similar to display folders (Those things with the hard covers 
;and the plastic sleeves inside). When you declare a Type, you can think of it as telling the computer that
;when you ask for a new 'folder', it is made to the specifications that you told it to. For example, if you
;gave it 5 fields, each new folder of that type will contain 5 plasic sleeves.

;You can use the following syntax to ask for a new 'folder'
;pointer.typename = new typename
;'pointer' can be anything you want, but one warning - it CANNOT be a pointer to another type and it CANNOT
;be a variable that you have used earlier, otherwise Blitz will throw an error in your face.

;To scroll through all of your 'folders', you use the following syntax (which resembles the for/next loop)
;for pointer.typename = each typename
;Again, the pointer should not be a pointer to another type or a variable that you have used earlier.

;When you create a new 'folder' or when you cycle through your 'folders', you can give the different 'sleeves'
;different content, by using the following syntax:
;pointername\fieldname = integer (if you defined the field followed by a # or a $ like you would with 
;								  variables, you can replace the integer with a floating point number
;								  or a string respectivly)

;'folder's can be deleted using the following syntax:
;delete pointername

;syntax for declaring a new type
Type cubes;Creates a new type
	Field entity;Creates a new field within the type (explained later)
	;Field blah, blah2#, haz% - you can have as many fields as you want
	;Field blah3$ - they can be seperated by comma's or put on new lines
End Type

;Sets the graphics mode & sets the drawing buffer to the backbuffer
Graphics3D 800,600
SetBuffer BackBuffer()

;Creates the camera, moves it -100 units on the X axis, and then creates a tempblock at 0,0,0 for the camera
;to point to. The tempblock entity is then freed (word?)
camera = CreateCamera()
PositionEntity camera, 0, -100, 0
tempblock = CreateCube()
PointEntity camera, tempblock
FreeEntity tempblock

;Uses a for/next loop to create 100 cubes
For a = 1 To 100
	;Syntax for creating a new instance of 'cubes' and assigning data to the fields
	c.cubes = New cubes
	c\entity = CreateCube()
	PositionEntity c\entity, Rand(200)-100, Rand(50), Rand(200)-100
	RotateEntity c\entity, Rand(360), Rand(360), Rand(360)
	EntityColor c\entity, Rand(255), Rand(255), Rand(255)
Next

;Sets the speed for the cubes ingame (if you can call it a game)
;So that speed can be fine tuned, it is a floating point variable
speed# = 0.2

While Not KeyDown(1);main loop start

	count = 0;used to count the number of cubes for the text display
	For c.cubes = Each cubes
		MoveEntity c\entity, speed#, 0, 0;Moves the entities
		count = count + 1;Counts the number of cubes
		If Not EntityInView(c\entity, camera);If the cube is not visible
			FreeEntity c\entity;Free the actual entity...
			Delete c;Then delete the instance of the type
		EndIf
	Next
	
	;Create new cubes if the spacebar is being pressed
	If KeyDown(57)
		c.cubes = New cubes
		c\entity = CreateCube()
		PositionEntity c\entity, Rand(200)-100, Rand(50), Rand(200)-100
		RotateEntity c\entity, Rand(360), Rand(360), Rand(360)
		EntityColor c\entity, Rand(255), Rand(255), Rand(255)
	EndIf
	
	;If the - or + keys are being pressed, change the speed accordingly
	If KeyDown(12)
		speed# = speed# - 0.1
		If speed# < 0
			speed# = 0
		EndIf
	EndIf
	If KeyDown(13)
		speed# = speed# + 0.1
	EndIf
	
	;Updates & renders the world, displays the text onto the screen then flips the buffers so it is
	;displayed on the frontbuffer (the screen)
	UpdateWorld
	RenderWorld
	Text 1, 1, "There are "+count+" cubes on screen. Press the spacebar to add more"
	Text 1, 20, "Cubes that go out of the view of the camera are deleted"
	Text 1, 40, "Press the + or - keys to increase or decrease the speed of the cubes"
	Flip
	Cls
Wend;main loop end