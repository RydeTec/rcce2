; Nebula Runner Demo
; By Reda Borchardt

Include "start.bb"
Include "KeyConstants.bb"

message=1

banner = LoadImage("top-banner.jpg")
MaskImage banner,255,255,0

; Variables
Global xpos# = 0
Global ypos# = 0.5
Global zpos# = 0
Global xvelo# = 0
Global yvelo# = 0
Global zvelo# = 0

; Create and Setup Objects
sphere = CreateSphere(5)
nebula = LoadTexture("nebula2.jpg")
ScaleEntity sphere,50,50,50
FlipMesh sphere
TurnEntity Sphere,180,0,90
RotateTexture nebula,180
EntityTexture sphere,nebula
TextureCoords nebula,0

character_pivot = CreatePivot()
	PositionEntity character_pivot,xpos#,ypos#,zpos#
	RotateEntity character_pivot,0,0,0

camera_pivot = CreatePivot(character_pivot)

camera = CreateCamera(camera_pivot)
	PositionEntity camera,0,5,-5
	PointEntity camera,character_pivot
	
	;CameraFogMode camera,1
	;CameraFogRange camera,5,60

; Create Metal Plates
quarterlevel = CreatePivot()
level1 = CreatePivot(quarterlevel)
	
metalplate = CreateCube(level1)
	metalplate_texture = LoadTexture("textures/metal-plate.jpg",4)
	EntityTexture metalplate,metalplate_texture
	PositionEntity metalplate,0,-.2,0
	ScaleEntity metalplate,1,0.2,1
	
metalplate2 = CopyEntity(metalplate,level1)
	PositionEntity metalplate2,2,-.2,0
	
metalplate3 = CopyEntity(metalplate,level1)
	PositionEntity metalplate3,2,-.2,2
	
metalplate4 = CopyEntity(metalplate,level1)
	PositionEntity metalplate4,0,-.2,2
	
metalplate5 = CopyEntity(metalplate,level1)
	PositionEntity metalplate5,4,-.2,2
	
metalplate6 = CopyEntity(metalplate,level1)
	PositionEntity metalplate6,4,0.2,4
	TurnEntity metalplate6,-20,0,0
	
metalplate7 = CopyEntity(metalplate,level1)
	PositionEntity metalplate7,4,0.5,6
	
metalplate8 = CopyEntity(metalplate6,level1)
	PositionEntity metalplate8,2,0.9,6
	TurnEntity metalplate8,20,0,-20
	
metalplate7 = CopyEntity(metalplate,level1)
	PositionEntity metalplate7,0.1,1.2,6
	
level2 = CopyEntity(level1,quarterlevel)
	PositionEntity level2,-1.9,1.4,4
	RotateEntity level2,0,90,0
	
quarterlevel2 = CopyEntity(quarterlevel)
	RotateEntity quarterlevel2,0,270,0
	PositionEntity quarterlevel2,0,0,-2
	
quarterlevel3 = CopyEntity(quarterlevel)
	RotateEntity quarterlevel3,0,90,0
	PositionEntity quarterlevel3,-2,0,0

	
; Load and Setup External Objects
character = LoadMD2("model/tris.md2",character_pivot)
	ScaleEntity character,.02,.02,.02
	RotateEntity character,0,0,0
	
weapon_blaster = LoadMD2("model/w_railgun.md2", character_pivot)
	ScaleEntity weapon_blaster,.02,.02,.02
	RotateEntity weapon_blaster,0,0,0

; Load and Apply Textures
character_texture = LoadTexture("model/ctf_r.pcx")
	EntityTexture character, character_texture

weapon_blaster_texture = LoadTexture("model/w_railgun.pcx")
	EntityTexture weapon_blaster,weapon_blaster_texture
	
; Create Light
light = CreateLight(3,camera_pivot)
	PositionEntity light,-5,0,-30
	LightColor light,255,255,255
	PointEntity light, character_pivot
	
; Initialize Animations
AnimateMD2 character,1,0.09,0,5
AnimateMD2 weapon_blaster,1,0.09,0,5

; Create Collision Objects

EntityType quarterlevel,2,1
EntityType quarterlevel2,2,1
EntityType quarterlevel3,2,1
EntityType character_pivot,1

EntityRadius character_pivot,0.3

Collisions 1,2,2,2

; Main Loop
While Not KeyHit(1)

	moved = False
	forward = True
		
	;Shoot
	If KeyDown(key_leftcontrol)	
			If MD2AnimTime(character) < 46 Or MD2AnimTime(character) > 54 Then
				AnimateMD2 character,1,-0.08,46,54
				AnimateMD2 weapon_blaster,1,-0.08,46,54
			End If
	
	End If
	
	;Walk
	 If KeyDown(key_arrowpad_up)
		MoveEntity character_pivot,0,0,0.03
		moved = True
		forward = True
	 End If
	
   	If KeyDown(key_arrowpad_down) 
		MoveEntity character_pivot,0,0,0.-0.03
		moved = True
		forward = False
	 End If
	

	
	;Turn
	 If KeyDown(key_arrowpad_left) 
		TurnEntity character_pivot,0,2,0
		TurnEntity camera_pivot,0,-2,0
		moved = True
	 End If

	 If KeyDown(key_arrowpad_right) 
		TurnEntity character_pivot,0,-2,0
		TurnEntity camera_pivot,0,2,0
		moved = True
	 End If	
 
	
	 If moved = True 
	    If forward = True	
			If MD2AnimTime(character) < 40 Or MD2AnimTime(character) > 46 Then
				AnimateMD2 character,1,0.1,40,46
				AnimateMD2 weapon_blaster,1,0.1,40,46
			End If
		End If

		If forward = False
			If MD2AnimTime(character) < 40 Or MD2AnimTime(character) > 46 Then
				AnimateMD2 character,1,-0.08,40,46
				AnimateMD2 weapon_blaster,1,-0.08,40,46
			End If
		End If
		
     End If

	 If moved = False
		If MD2AnimTime(character) < 0 Or MD2AnimTime(character) > 10 Then
			AnimateMD2 character,1,0.1,0,5
			AnimateMD2 weapon_blaster,1,0.1,0,5
		End If
     End If 
	
	;Gravity Simulated Through Simple Motion
	If Not EntityCollided(character_pivot,2)
			MoveEntity character_pivot,0,-0.04,0
    End If

; Mouse Motion
	x_speed=0
	y_speed=0
	z_speed=0
	
		x_speed=MouseXSpeed()
		If x_speed > 1.25 Then
			x_speed = 1.25
		End If
		
		If x_speed < -1.25 Then
			x_speed = -1.25
		End If
		

		
		;y_speed=(MouseYSpeed()-y_speed)/13+y_speed
		;y_zoom=(0-y_zoom)/10+y_zoom
	
	
	;TurnEntity camera_pivot,0,-x_speed,0
	;TurnEntity camera_pivot,-y_speed,0,0
	TurnEntity character_pivot,0,-x_speed*2,0
	TurnEntity character_pivot,-y_speed*2,0,0
	MoveMouse GraphicsWidth()/2,GraphicsHeight()/2
	
	TurnEntity sphere,0.1,0,0
	

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

		Text 0,100,"Little Nebula Runner"
		Text 0,120,"Keys: Arrow Keys to Move and Rotate Charater"
		Text 0,140,"Use The Mouse To Rotate Camera And Character."
		Text 0,180,"Keys: 'W' to toggle wireframe mode"
		Text 0,200,"Keys: H to hide this message
	End If
	Flip

Wend

; End
End