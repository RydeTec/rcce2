;
; SOURCE: SIMPLE FOGGING
; AUTHOR: Rob Hutchinson - 7th October 2001   -  loki.sd@blueyonder.co.uk
; -----------------------------------------------------------------------------------------------
; DESCRIPTION:
; This source simply shows you how to do use the fogging commands.
; 
; ABOUT FOGGING:
; Whats so good about fog? Well to start with fog can help you if there are too many objects
; on screen or in the distance, to render at good speeds. Also it can look very nice in some
; scenes.
;

; Open up the screen, here we are using 640x480 because it is almost
; gaurenteed to be supported by the users' 3D card... Not specifying
; the depth will use the best available depth for the card.
Graphics3D 640,480
SetBuffer BackBuffer()

; LOAD AND CREATE OUR DATA
; Here we load in 2 textures, one for the floor and one for the cylinders.
; Then create the cylinder which will later be duplicated.
cylinderMain = CreateCylinder(20,1)
MoveEntity cylinderMain,0,2,0
ScaleEntity cylinderMain,.3,1.99,.3
textureCyl = LoadTexture("Textures\yak.bmp")
EntityTexture cylinderMain,textureCyl   ; Attach the texture to the model.

; Create a camera to show our scene through
camera = CreateCamera()             ; Attach it to its pivot
MoveEntity camera,0,2,0

; Right, now we create lots of outgoing cylinders based on the one above...
Global CYL_MAX  = 100
Global CYL_SPC# = 50.788
Global CYL_SPD# = 0.3
Global rad1c# = 10
Global ang1c# = 0
For a = 0 To CYL_MAX
	newcly = CopyEntity(cylinderMain)
	MoveEntity newcly,Cos(ang1c#)*rad1c#,0,Sin(ang1c#)*rad1c#
	rad1c# = rad1c# + CYL_SPD#
	ang1c# = ang1c# + CYL_SPC#
Next

; Hide the original
HideEntity cylinderMain

; CREATE SCENE
; Here we create a plane for the floor, and texture it.
plnTempFloor = CreatePlane()
EntityAlpha plnTempFloor,.7
texTempFloor = LoadTexture("Textures\walling.bmp")
ScaleTexture texTempFloor,10,10
EntityTexture plnTempFloor,texTempFloor

; Create some mirror stuff...
mirrorMain = CreateMirror()

; FOGGING -------------------
; Finally do the fogging, here we create a nice fog fade, as everything comes into view..
; So the fogging starts as pitch black and then the range is extended..
Global FOG_MAX# = 40
Global FOG_SPD# = 1
Global FOG_CUR# = 1
CameraFogMode camera,1       ; This turns linear fog on..
CameraFogColor camera,0,0,0  ; black, fog - experiment..
CameraFogRange camera,1,FOG_CUR#  ; set its range (right close)

Repeat
	; Fade in (well out really) the fog...
	If FOG_CUR# < FOG_MAX#
		FOG_CUR# = FOG_CUR# + FOG_SPD#
		CameraFogRange camera,1,FOG_CUR#
	EndIf
	
	; Smoothing...	
	x_speed#=(MouseXSpeed()-x_speed)/13+x_speed
	y_speed#=(MouseYSpeed()-y_speed)/13+y_speed
	TurnEntity camera,y_speed,0,0,0
	TurnEntity camera,0,-x_speed,0,1
		
	; Lock the mouse in the middle of the screen so that MouseX/YSpeed() work properly
	MoveMouse GraphicsWidth()/2,GraphicsHeight()/2

	; Update and render the world.
	UpdateWorld
	RenderWorld
	Flip  ; Double buffer
Until KeyDown(1)
; When ESC is pressed, close the program..
End