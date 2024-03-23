;
; SOURCE: SIMPLE SPOTLIGHTS
; AUTHOR: Rob Hutchinson - 7th October 2001   -  loki.sd@blueyonder.co.uk
; -----------------------------------------------------------------------------------------------
; DESCRIPTION:
; This source simply shows you how to create, turn, range and cone spotlights in your blitz
; 3D world.
;

; Open up the screen, here we are using 640x480 because it is almost
; gaurenteed to be supported by the users' 3D card... Not specifying
; the depth will use the best available depth for the card.
Graphics3D 640,480
SetBuffer BackBuffer()

; Create a camera to show our scene through
camera = CreateCamera() 

; LOAD AND CREATE OUR DATA
; Here we simply load in a texture to put on the shpere...
sphereObj = CreateSphere(30)         ; create the sphere
ScaleEntity sphereObj,10,10,10       ; scale it up.. 
textureSph = LoadTexture("Textures\metal.bmp")
ScaleTexture textureSph,.1,.1        ; Adjust the texture..
EntityTexture sphereObj,texturesph   ; Attach the texture to the model.
FlipMesh sphereObj                   ; Flip the sphere inside out...

; Set the ambient lighting very low
AmbientLight 5,5,5
; Create us a red spotlight, adjust its color, range and cone..
lightRed = CreateLight(3)
LightRange lightRed,5
LightColor lightRed,255,0,0
LightConeAngles lightRed,0,130
; Create us a green spotlight, adjust its color, range and cone..
lightGreen = CreateLight(3)
LightRange lightGreen,5
LightColor lightGreen,0,255,0
LightConeAngles lightGreen,0,130
; Create us a blue spotlight, adjust its color, range and cone..
lightBlue = CreateLight(3)
LightRange lightBlue,5
LightColor lightBlue,0,0,255
LightConeAngles lightBlue,0,130

Repeat
	; Turn the spotlights around..
	TurnEntity lightRed  ,0,2,0
	TurnEntity lightGreen,2,0,0
	TurnEntity lightBlue ,0,-2,0
	
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