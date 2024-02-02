;
; SOURCE: CREATING A SCENE - PART 1
; AUTHOR: Rob Hutchinson - 5th October 2001   -  loki.sd@blueyonder.co.uk
; -----------------------------------------------------------------------------------------------
; PART: 1 - "The framework"
; -----------------------------------------------------------------------------------------------
; DESCRIPTION:
; Right, here is the framework of the scene... It includes some game timing code and the walls
; of the room the character will be placed in.
; The idea is that by part 5, there will be a full ambient room with some controls,
; some effects and some sort of interaction.
; This source will build up into the final example over 5 parts..
;
; FINAL NOTES:
; There is not a lot going on here, all we are doing is loading in the room object that
; I created earlier, which is simply just a cube split into 6 sides with textures on each
; side. You can use the arrow keys to look around the room, but thats about it..
; If you are new to blitz, I recommend studying this code and then moving onto part 2.
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
Global sceneCamera = CreateCamera()

; Load the room object. All textures are loaded here..
Global objectRoom = BuildObject("Data\Room1.x",0,0,0,0,0,0,2,2,2)


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
	If KeyDown(203) Then TurnEntity sceneCamera,0,2,0
	If KeyDown(205) Then TurnEntity sceneCamera,0,-2,0
	If KeyDown(200) Then TurnEntity sceneCamera,2,0,0
	If KeyDown(208) Then TurnEntity sceneCamera,-2,0,0
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
