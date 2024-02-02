;
; SOURCE: CREATING A SCENE - PART 2
; AUTHOR: Rob Hutchinson - 6th October 2001   -  loki.sd@blueyonder.co.uk
; -----------------------------------------------------------------------------------------------
; PART: 2 - "Adding some movement controls"
; -----------------------------------------------------------------------------------------------
; DESCRIPTION:
; Now we take part one and built on it to add some better controls to the scene.
; You can now look around with the mouse, and move with the arrow keys!
; + added here are the lights for the walls..
;
; FINAL NOTES:
; Note the complete lack of collisions here, this comes next
; Move onto part 3 after reading through this code..
;


; -- [SETUP] ------------------------------------------------------------------------------------

; The number of frames per second to display/update
Global timeFPS = 60

; Open up the screen, here we are using 640x480 because it is almost
; gaurenteed to be supported by the users' 3D card... Not specifying
; the depth will use the best available depth for the card.
Graphics3D 640,480
SetBuffer BackBuffer()  ; Double buffer the display.

; Setup the camera.. sceneCamera is used to represent the camera entity.
; Note that now the camera is attached to its own pivot (axis), this will be
; used to represent the player for collisions..
Global sceneCamPiv = CreatePivot()
; Attach the camera to the pivot
Global sceneCamera = CreateCamera(sceneCamPiv)

; Load the room object. All textures are loaded here..
Global objectRoom = BuildObject("Data\Lights&Room.x",0,0,0,0,0,0,3,4,3)

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
