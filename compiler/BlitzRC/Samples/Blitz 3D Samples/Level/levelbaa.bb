;Simple FPS
;By Michael Reitzenstein [huntersd@iprimus.com.au]
;This is just the basics that a FPS can be built from, so dont expect anything too good
;Basically it loads the model for a file (which can be textured at will) and sets up collision and such
;There are no guns, weapons, pickups or enemies in here, once again I say its just the basics
;Last built with Blitz Basic 3D binaries 228

Graphics3D 800,600
SetBuffer BackBuffer();enable double buffering

;load and setup the level. The level used is made in a 3D modeler, and it can be textured or coloured at will
Global level = LoadMesh("models\level.3ds");load the level mesh
ScaleEntity level, 5, 5, 5;scale the level up to 5x its original size
EntityType level, type_scenery;set the entity collision type to type_scenery 
UpdateNormals level;update the normals of mesh

;Load and setup the playerbox, this, rather than the camera, is used for collision with the level
Global playerbox = LoadMesh("models\playerbox.3ds");a mesh used for the playerbox
ScaleEntity playerbox, 30, 50, 30;Scale the playerbox
EntityType playerbox, type_player;set the entity collision type to type_player
PositionEntity playerbox, 0, 20, 0;position the playerbox (and thus the camera)
UpdateNormals playerbox;update the normals of the mesh

Global camera = CreateCamera();create the camera (every loop the camera is placed at the 'eyes' of the playerbox
PositionEntity camera, EntityX(playerbox), EntityY(playerbox), EntityZ(playerbox);positions the camera
RotateEntity camera, EntityPitch(playerbox), EntityYaw(playerbox), EntityRoll(playerbox);rotates the camera

Global light = CreateLight(camera);creates the light with its parent as 'camera' meaning that nothing has to be done.
								  ;it will allways stay at the position of the camera, even when it moves
							
Const type_player = 1, type_scenery = 2;Constants for the collision types
Collisions type_player, type_scenery, 2, 2;Set the collision between the palyer and the scenery, sphere to polygon
										  ;collisions, and sliding (so it slides down the slopes)

Global playerspeed = 2;Variable to hold the speed of the player
Global gravity# = 1;Gravity (this value is subtracted from the Y value of the playerbox each loop - doesnt
				   ;effect the object if it is colliding with the ground
Global eyeheight = 10;The height above the base of the playerbox to have the camera

Repeat
	;rotates the camera according to the mouselook, and then turn the playerbox according to the camera
	RotateEntity camera, EntityPitch(camera) + MouseYSpeed(), EntityYaw(camera) - MouseXSpeed(), EntityRoll(camera)
	RotateEntity playerbox, EntityPitch(playerbox), EntityYaw(camera), EntityRoll(playerbox)
	
	;prevents the camera from pointing verticle (these values should be set to -89 & 90 really,
	;but it would take some workaround coding to accomplish
	If EntityPitch(camera) < -70
		RotateEntity camera, -70, EntityYaw(camera), EntityRoll(camera)
	ElseIf EntityPitch(camera) > 70
		RotateEntity camera, 70, EntityYaw(camera), EntityRoll(camera)
	EndIf

	;If the upkey is being pressed, move the playerbox
	If KeyDown(200)
		MoveEntity playerbox, 0, 0, playerspeed
	EndIf
	
	;apply gravity to the playerbox, and then move the camera to the place of the playerbox
	PositionEntity playerbox, EntityX(playerbox), EntityY(playerbox) - gravity, EntityZ(playerbox)
	PositionEntity camera, EntityX(playerbox), EntityY(playerbox)+eyeheight, EntityZ(playerbox)
	
	;These are used for the mouselook, as the way that the mouseyspeed() and mousexspeed() are structured is that
	;the mouse will not go off either side of the screen, so if it gets to a side it will stop and so will the 
	;mouselook...
	;Dont worry too much with this code, its basically a workaround
	If MouseX() < 2
		MoveMouse GraphicsWidth()-2, MouseY()
		temp = MouseXSpeed()
	ElseIf MouseX() > GraphicsWidth()-2
		MoveMouse 1, MouseY()
		temp = MouseXSpeed()
	EndIf
	If MouseY() < 2
		MoveMouse MouseX(), GraphicsHeight()-2
		temp = MouseYSpeed()
	ElseIf MouseY() > GraphicsHeight()-2
		MoveMouse MouseX(), GraphicsHeight()-2
		temp = MouseYSpeed()
	EndIf
	
	;Update & Render the world
	UpdateWorld
	RenderWorld
	
	;screenshot taker
	If KeyHit(63) = 1;F5
		For w = 1 To 100
			name$ = "screenshot" + w + ".bmp"
			If FileType(name$) = 0
				If done = 0
					SaveBuffer(FrontBuffer(), name$)
					done = 1
				EndIf
			EndIf
		Next
	EndIf
	done = 0	
	
	;flip the frontbuffer & backbuffer, and then clear the screen
	Flip
	Cls
Until KeyDown(1) = 1