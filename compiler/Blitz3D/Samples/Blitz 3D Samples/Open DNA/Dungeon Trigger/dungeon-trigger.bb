; Dungeon Trigger
; By Reda Borchardt

Include "start.bb"
Include "keyconstants.bb"

SetBuffer BackBuffer()

banner = LoadImage("top-banner.jpg")
MaskImage banner,255,255,0
Message=1

; Variables
Dim pillar(7)
Global grunt
Global Character_pivot
Global PLAYER = 1
Global OBSTACLE = 2
Global TRIGGER = 3
Global door
Global door_state = 1
entity# = 0
Global door_moving = False


; Grunt
; Load Grunt & Weapon
	grunt_tex = LoadTexture("armour.pcx",1)
	character_pivot = CreatePivot()
	grunt = LoadMD2("tris.md2",character_pivot)
	EntityTexture grunt,grunt_tex
	ScaleEntity grunt,0.015,0.015,0.015
	PositionEntity character_pivot,0,2,-1
	EntityType character_pivot,PLAYER,1
	EntityRadius grunt,0.1
	EntityRadius character_pivot,0.4


; Create Pivots


; Scenery
	; Create Ground Floor
	groundfloor = CreateCube()
	groundfloor_tex = LoadTexture("marble.jpg")
	ScaleEntity groundfloor,2,0.1,3
	PositionEntity groundfloor,0,0,5
	EntityTexture groundfloor,groundfloor_tex
	ScaleTexture groundfloor_tex,0.3,0.3
	EntityType groundfloor,OBSTACLE
	
	groundfloor2 = CreateCube()
	groundfloor_tex = LoadTexture("marble.jpg")
	ScaleEntity groundfloor2,2,0.1,3
	PositionEntity groundfloor2,0,0,-1
	EntityTexture groundfloor2,groundfloor_tex
	ScaleTexture groundfloor_tex,0.3,0.3
	EntityType groundfloor2,OBSTACLE
	
	; Create Plane
	plane = CreatePlane()
	plane_tex = LoadTexture("plane.jpg")
	EntityTexture plane,plane_tex
	EntityAlpha plane,0.4
	EntityType plane,OBSTACLE
	
	;Mirror
	mirror = CreateMirror()
	PositionEntity mirror,0,0,0

	
	; Create Walls
	wall1 = CreateCube()
	wall_tex = LoadTexture("wall.jpg")
	ScaleEntity wall1,0.1,1,6
	PositionEntity wall1,2,1,2
	EntityTexture wall1,wall_tex
	ScaleTexture wall_tex,0.3,1
	EntityType wall1,OBSTACLE
	
	wall2 = CopyEntity(wall1)
	PositionEntity wall2,-2,1,2
	EntityType wall2,OBSTACLE
	
	;wall3 = CopyEntity(wall1)
	;PositionEntity wall3,0,1,8
	;ScaleEntity wall3,0.1,1,2
	;TurnEntity wall3,0,90,0
	;EntityType wall3,OBSTACLE
	
	wall4 = CopyEntity(wall1)
	PositionEntity wall4,3,1,1
	ScaleEntity wall4,0.1,1,2
	TurnEntity wall4,0,90,0
	EntityType wall4,OBSTACLE
	
	wall5 = CopyEntity(wall1)
	PositionEntity wall5,-3,1,1
	ScaleEntity wall5,0.1,1,2
	TurnEntity wall5,0,90,0
	EntityType wall5,OBSTACLE
	
	door = CopyEntity(wall1)
	door_tex = LoadTexture("door.jpg")
	PositionEntity door,0,1,1
	ScaleEntity door,0.1,1,1
	TurnEntity door,0,90,0
	EntityTexture door,door_tex
	EntityType door,OBSTACLE
	EntityPickMode door,1 ; DO NOT FORGET THIS ONE !!!!

; Create Camera
	camera = CreateCamera()
	PositionEntity camera,4,8,-11
	TurnEntity camera,30,20,0
	CameraZoom camera,2

; Handle Collisions
	Collisions PLAYER,OBSTACLE,2,2
	Collisions OBSTACLE,PLAYER,1,1

; Light
	AmbientLight 200,200,200
	light = CreateLight(3,character_pivot)
	TurnEntity light,0,0,90
	PositionEntity light,0,1,0
	
; Main Loop
	While Not KeyHit(1)

	movegrunt()
	checkgravity()
	triggerdoor()
	
	x=MouseX()
	y=MouseY()
	
	e=CameraPick(camera,x,y)
	If e<>entity#
		If entity Then EntityColor entity,255,255,255
		entity#=e
	EndIf
	
	If MouseHit(1) And entity#=e
		door_moving=True
		triggerdoor()
	End If
		
	If entity#
		EntityColor entity,255,0,0
	EndIf
	
	UpdateWorld
	RenderWorld
	
	DrawImage banner,0,0
	
	If KeyHit(Key_h) Then
		If message=0 Then message =1
		If message=1 Then message =0
	End If
	
	If KeyHit(17) Then
		WireMode = Not WireMode
		WireFrame WireMode
	End If
	
	If message=1 Then

		Text 0,100,"Dungeon Trigger Demo"
		Text 0,120,"Keys: Arrow Keys to Move and Rotate Charater"
		Text 0,140,"Keys: Click the door with you mouse to trigger it"
		Text 0,180,"Keys: 'W' to toggle wireframe mode"
		Text 0,200,"Keys: H to hide this message
	End If
	
	Rect x,y-3,1,7	
	Rect x-3,y,7,1
		
	Flip
	
Wend
End


; Functions
Function triggerdoor()
	If door_state = 1 And door_moving = True Then
		MoveEntity door,0,.1,0
		If EntityY(door) > 2 Then door_state = 0: door_moving = False: EntityAlpha door,0.3
	Else If door_state = 0 And door_moving = True Then
		MoveEntity door,0,-.1,0 door_moving = True
		If EntityY(door) < 1 Then door_state = 1: door_moving = False: EntityAlpha door,1
	End If
End Function

Function checkgravity()
	If Not EntityCollided(character_pivot,OBSTACLE)
			TranslateEntity character_pivot,0,-0.05,0
    End If
End Function

Function movegrunt()
	
	If KeyDown(key_arrowpad_left)
		TurnEntity character_pivot,0,3,0
	End If
	
	If KeyDown(key_arrowpad_right) 
		TurnEntity character_pivot,0,-3,0
	End If
	
	If KeyDown(key_arrowpad_up) 
		MoveEntity character_pivot,0,0,0.035
		moved = 1
		forward = 1
	End If
	
	If KeyDown(key_arrowpad_down)
		MoveEntity character_pivot,0,0,-0.035
		moved = 1
		forward = 0
	End If
	
	If moved = True
	    If forward = True	
			If MD2AnimTime(grunt) < 40 Or MD2AnimTime(grunt) > 46 Then
				AnimateMD2 grunt,1,0.1,40,46
			End If
		End If
	End If

	If forward = False 
		If MD2AnimTime(grunt) < 40 Or MD2AnimTime(grunt) > 46 Then
			AnimateMD2 grunt,1,-0.08,40,45
		End If
	End If
		
	If moved = False
		If MD2AnimTime(grunt) < 0 Or MD2AnimTime(grunt) > 10 Then
			AnimateMD2 grunt,1,0.1,0,5
		End If
    End If		
    moved = 0
	forward = 0
End Function