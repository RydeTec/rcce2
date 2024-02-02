;
; SOURCE: ANIMATING WITH MD2
; AUTHOR: Rob Hutchinson - 7th October 2001   -  loki.sd@blueyonder.co.uk
; -----------------------------------------------------------------------------------------------
; DESCRIPTION:
; This is a simple MD2 animation of a guy walking across some marbly stuff..
; Written to show loading and animating an MD2 model.
;
; FINAL NOTES:
; The character animation was created by Anthony Ferrandiz, and kindly used with permission.
;
; Note the lack of game timing code to keep this simple (also frame limited stuff is SUPPPPPER
; SMOOOOOOOOOOOTH! :)))
;

; Open up the screen, here we are using 640x480 because it is almost
; gaurenteed to be supported by the users' 3D card... Not specifying
; the depth will use the best available depth for the card.
Graphics3D 640,480
SetBuffer BackBuffer()

; LOAD DATA
; Here we load in the model, then load the texture into the model. Note that you must do
; this with MD2 models in blitz.
pivotCharacter = CreatePivot()
md2Character = LoadMD2("Models\Bloke.md2",pivotCharacter)
ScaleEntity md2Character,.1,.1,.1
; Load the texture.
textureMD2 = LoadTexture("Textures\char.bmp")
EntityTexture md2Character,textureMD2   ; Attach the texture to the model.

; Create a camera and point it at our model..
pivotCamera = CreatePivot(pivotCharacter)
MoveEntity pivotCamera,0,10,0
camera = CreateCamera(pivotCamera)  ; Attach it to its pivot
MoveEntity camera,0,0,-8            ; Move the camera back a bit.
PointEntity camera,md2Character     ; Aim the camera at the character

; CREATE SCENE
; Here we create a light, a plane for the floor, and texture it.
lgtMain = CreateLight()
plnTempFloor = CreatePlane()
EntityAlpha plnTempFloor,.7
texTempFloor = LoadTexture("Textures\marble.bmp")
ScaleTexture texTempFloor,10,10
EntityTexture plnTempFloor,texTempFloor
MoveEntity plnTempFloor,0,0,0

; Create some mirror stuff...
mirrorMain = CreateMirror()

; Start the character animating...
; Here we are animating only through a select few frames, forward, at speed .19.
; To reverse the animation use minus speeds.
AnimateMD2 md2Character,1,.19,14,0

Repeat
	; Move the player along the grass
	MoveEntity pivotCharacter,0,0,.1

	; Smoothing...	
	x_speed#=(MouseXSpeed()-x_speed)/13+x_speed
	y_speed#=(MouseYSpeed()-y_speed)/13+y_speed
	TurnEntity pivotCamera,y_speed,x_speed,0,True
		
	; keep camera looking at the bloke
	PointEntity camera,md2Character     ; Aim the camera at the character
	
	; Lock the mouse in the middle of the screen so that MouseX/YSpeed() work properly
	MoveMouse GraphicsWidth()/2,GraphicsHeight()/2

	; Update and render the world.
	UpdateWorld
	RenderWorld
	Flip  ; Double buffer
Until KeyDown(1)
; When ESC is pressed, close the program..
End