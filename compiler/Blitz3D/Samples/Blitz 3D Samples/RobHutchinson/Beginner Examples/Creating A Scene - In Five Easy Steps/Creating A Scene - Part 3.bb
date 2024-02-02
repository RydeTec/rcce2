;
; SOURCE: CREATING A SCENE - PART 3
; AUTHOR: Rob Hutchinson - 6th October 2001   -  loki.sd@blueyonder.co.uk
; -----------------------------------------------------------------------------------------------
; PART: 3 - "Adding some collisions to our scene"
; -----------------------------------------------------------------------------------------------
; DESCRIPTION:
; The main thing added here are collisions between the main player (simply a pivot object) and
; the walls/lamps (obsticles).
;
; FINAL NOTES:
; Now that collision are in I`ve also given the program an apptile and hidden the pointer
; for when it is run in windowed mode / debug on.
;

; -- [SETUP] ------------------------------------------------------------------------------------

; Right lets give our program a name...
AppTitle "Creating A Scene"

; The number of frames per second to display/update
Global timeFPS = 60

; Collision variables...
Const COLLISION_OBSTICLE = 1  ; Walls, etc..
Const COLLISION_PLAYER   = 2  ; The player..

; Open up the screen, here we are using 640x480 because it is almost
; gaurenteed to be supported by the users' 3D card... Not specifying
; the depth will use the best available depth for the card.
Graphics3D 640,480
SetBuffer BackBuffer()  ; Double buffer the display.
; Get rid of the pointer... In windowed mode...
HidePointer()

; Setup the collisions
; Ok what have I setup here?
; When the player (COLLISION_PLAYER) collides with with an obsticle (COLLISION_OBSTICLE),
; a "sphere<>polygon" (2) collision check is made, the sphere is the player and the polygon
; is the object. And the response to this collision is to slide the player..
Collisions COLLISION_PLAYER,COLLISION_OBSTICLE,2,2

; Setup the camera.. sceneCamera is used to represent the camera entity.
; Note that now the camera is attached to its own pivot (axis), this will be
; used to represent the player for collisions..
Global sceneCamPiv = CreatePivot()
; Turn the pivot into the player...
EntityType sceneCamPiv,COLLISION_PLAYER
EntityRadius sceneCamPiv,2

; Attach the camera to the pivot
Global sceneCamera = CreateCamera(sceneCamPiv)

; Load the room object. All textures are loaded here..
Global objectRoom = BuildObject("Data\Lights&Room.x",0,0,0,0,0,0,3,4,3)
; Turn the room object into an obsticle
EntityType objectRoom,COLLISION_OBSTICLE

; -- [MAIN CODE] --------------------------------------------------------------------------------

; Setup the frame limiting code
framePeriod = 1000 / timeFPS
frameTime = MilliSecs() - framePeriod

Repeat

	; Here we limit the frames....
    Repeat
        frameElapsed = MilliSecs() - frameTime
    Until frameElapsed
    frameTicks = frameElapsed / framePeriod

	; Calculate tweening
    frameTween# = Float(frameElapsed Mod framePeriod) / Float(framePeriod)

	; Do the scene updating..
    For frameLimit = 1 To frameTicks
    
        If frameLimit = frameTicks Then CaptureWorld
        frameTime = frameTime + framePeriod
        
		; Update everything in our world, and catch up on missed frames..
        UpdateScene

		; Let blitz do its stuff updating..
        UpdateWorld
    Next

	; Render the whole world, it has been updated above...
    RenderWorld frameTween

	; Double buffer, flip the screen display..
    Flip
Until KeyHit(1)

; Terminate the program after ESC has been hit..
End


; -- [FUNCTIONS] --------------------------------------------------------------------------------

; --UpdateScene() Function.
; This is where we will soon do all our manual game/scene updating..
Function UpdateScene()
    ; Update our scene...

	; Get the mouse speeds, no smoothing here to keep this simple.
	mXs# = MouseXSpeed()
	mYs# = MouseYSpeed()
	
	; Note the global turning here to keep the camera straight, try switching true to false
	; and see what happens...
	TurnEntity sceneCamPiv,0,-(mXs#/3),0,True
	TurnEntity sceneCamera,(mYs#/3),0,0

	; Strafing..
	If KeyDown(203) Then MoveEntity sceneCamPiv,-.1,0,0
	If KeyDown(205) Then MoveEntity sceneCamPiv,.1,0,0

	; Move the player backwards and forwards...
	If KeyDown(200) Then MoveEntity sceneCamPiv,0,0,.1
	If KeyDown(208) Then MoveEntity sceneCamPiv,0,0,-.1
	
	; lock the mouse in the middle of the screen so that MouseX/YSpeed() work properly
	MoveMouse GraphicsWidth()/2,GraphicsHeight()/2
End Function

; --BuildObject() Function.
; This function loads in a specified object, rotates, scales and positions it
; in the world according to the parameters you specify.
; After this, the loaded object handle is returned.
Function BuildObject(objecttoload$,x#,y#,z#,pitch#,yaw#,roll#,sx#,sy#,sz#)
	; Firstly load the object in...
	Local objectScene = LoadMesh(objecttoload$)
	; Scale, Position and rotate it
	ScaleEntity objectScene,sx#,sy#,sz#
	PositionEntity objectScene,x,y,z
	RotateEntity objectScene,pitch#,yaw#,roll#
	; Return the handle...	
	Return objectScene
End Function

