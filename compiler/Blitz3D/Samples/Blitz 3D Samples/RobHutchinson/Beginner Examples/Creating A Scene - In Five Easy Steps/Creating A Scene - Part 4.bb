;
; SOURCE: CREATING A SCENE - PART 4
; AUTHOR: Rob Hutchinson - 6th October 2001   -  loki.sd@blueyonder.co.uk
; -----------------------------------------------------------------------------------------------
; PART: 4 - "Adding some more objects and interaction to the scene"
; -----------------------------------------------------------------------------------------------
; DESCRIPTION:
; Ok in this example the first thing you will notice is the introduction of the 4 new models,
; (table, gem, gem holder, crate). The left mouse button can be used to pick up and drop
; gem at any time. The gem acts like the player, in that it collides with all obsticles, so if
; you drop it, it will fall until it collides (rather crap falling, but I`m trying to keep this
; as simple as possible here).. note how the gem becomes attached to a pivot around the camera
; when it is picked up, this way you can see it as though you were holding it (if there were
; any hands).. You can put the gem into the gem holder (the little grey pot thing).. yes it does
; go in, half the time..
;
; About the gem object, it is simply a mesh with its texture loaded with the correct flag
; to give it spherical environment mapping, so it looks nice.. :) Oh also its alpha blended..
;
; FINAL NOTES:
; None, move onto the final part where we will add some small final effects...
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
Global sceneRotPiv = CreatePivot(sceneCamPiv)  ; for rotations, *ADDED..
; Turn the pivot into the player...
EntityType sceneCamPiv,COLLISION_PLAYER
EntityRadius sceneCamPiv,1.6
; Turn the player to face the table...
TurnEntity sceneCamPiv,0,215,0

; Attach the camera to the pivot
Global sceneCamera = CreateCamera(sceneRotPiv)

; Load the room object. All textures are loaded here..
Global objectRoom = BuildObject("Data\Lights&Room.x",0,0,0,0,0,0,3,3.5,3)
; Turn the room object into an obsticle
EntityType objectRoom,COLLISION_OBSTICLE

; The table. where the gem is placed at start..
Global objectTable = BuildObject("Data\table.x",8,-4,-8,0,0,0,5,5,5)
; Make the table an obsticle..
EntityType objectTable,COLLISION_OBSTICLE

; Create a pivot for our gem...
Global objectGemPiv = CreatePivot()
Global objectGem = BuildObject("Data\Gem.x",0,0,0,0,0,0,.4,.4,.4)
; Attach the entity to its pivot...
EntityParent objectGem,objectGemPiv
; Load a nice texture into the gem....
Global textureGem = LoadTexture("Data\GemTexture.jpg",64 Or 4) ; <- Flags = 64
EntityTexture objectGem,textureGem
; Make it a little bit transparent
EntityAlpha objectGem,.7
; Make the gem collide with stuff like the player does...
EntityType objectGemPiv,COLLISION_PLAYER
EntityRadius objectGemPiv,.2
; Put it above the table..
MoveEntity objectGemPiv,8,5,-7.3

; Create the Crap(tm) gem holder..
Global objectGemHolder = BuildObject("Data\GemHolder.x",-10,-3,10,0,0,0,.8,.8,.8)
EntityType objectGemHolder,COLLISION_OBSTICLE

; The supplies crate... (I created this ages ago it should be in the geometrics models in blitz dir too).
Global objectCrate = BuildObject("Data\Supplies.x",8,-1.8,8,0,0,0,.3,.3,.3)
; Make the crate an obsticle..
EntityType objectCrate,COLLISION_OBSTICLE


; The pickup sprite... this becomes visible when you are close to the gem...
Global spritePick = LoadSprite("Data\gempick.jpg",1,sceneRotPiv)
ScaleSprite spritePick,.5,.1
MoveEntity spritePick,-1,1,1.5
; It must always be drawn last, so it appears ontop of everything and doesn`t go behind walls.
EntityOrder spritePick,-1      
Global pickupalpha# = 0
EntityAlpha spritePick,pickupalpha#


; -- [MAIN CODE] --------------------------------------------------------------------------------

; Setup the frame limiting code
framePeriod = 1000 / timeFPS
frameTime = MilliSecs() - framePeriod

Global gemstatus = 0   ; 0 = freefall, 1 = user holding..

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
	TurnEntity sceneRotPiv,(mYs#/3),0,0

	; Strafing..
	If KeyDown(203) Then MoveEntity sceneCamPiv,-.1,0,0
	If KeyDown(205) Then MoveEntity sceneCamPiv,.1,0,0

	; Move the player backwards and forwards...
	If KeyDown(200) Then MoveEntity sceneCamPiv,0,0,.1
	If KeyDown(208) Then MoveEntity sceneCamPiv,0,0,-.1
	
	; Lock the mouse in the middle of the screen so that MouseX/YSpeed() work properly
	MoveMouse GraphicsWidth()/2,GraphicsHeight()/2
	
	; Player is always falling...
	MoveEntity sceneCamPiv,0,-.1,0
	
	; Gem is always falling when its not being held by the player..
	If gemstatus = 0
		MoveEntity objectGemPiv,0,-.05,0
		; When the gem is not colliding with anything, rotate it as it falls..
		If Not EntityCollided(objectGemPiv,COLLISION_OBSTICLE)
			TurnEntity objectGem,2,1,3
		EndIf
	EndIf
	
	; Distance from gem... (fade in the text)
	If gemstatus = 0
		If EntityDistance(objectGemPiv,sceneCamPiv) <= 3
			pickupalpha# = pickupalpha# + 0.1
			If pickupalpha# > 1.0 Then pickupalpha# = 1.0
			
			; Do checks for LMB		
			If MouseHit(1)
				gemstatus = 1                          ; gem is now held by user
				EntityParent objectGemPiv,sceneRotPiv  ; lock it to the camera
				PositionEntity objectGemPiv,0,-.2,1.5  ; stick it infront of the camera
				EntityType objectGemPiv,0              ; Turn off collisions with the gem so it doesn`t get stuck
			EndIf
		Else
			pickupalpha# = pickupalpha# - 0.1
			If pickupalpha# < 0 Then pickupalpha# = 0
		EndIf
	Else
		pickupalpha# = pickupalpha# - 0.1   ; get rid of the text..
		If pickupalpha# < 0 Then pickupalpha# = 0 
		; make it rotate infront of the camera
		TurnEntity objectGem,0,0,2
		
		; if mouse is hit again, drop the gem...
		If MouseHit(1)
			gemstatus = 0                              ; gem is now freefalling again
			EntityParent objectGemPiv,0                ; unlock the gem..
			RotateEntity objectGemPiv,0,0,0            ; flush rotation, so it doesn`t go flying off..
			EntityType objectGemPiv,COLLISION_PLAYER   ; return its collision status
		EndIf
		
	EndIf
	
	EntityAlpha spritePick,pickupalpha#
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
