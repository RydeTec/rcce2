; MegaBlast Demo
; By Reda Borchardt

Include "start.bb"
Include "KeyConstants.bb"

;Contstants
Global camera_pivot
Global camera
Global shot
Global grunt
Global bulletangle#
Global megablast
Global weapon
Global Character_pivot
Global PLAYER = 1
Global GROUND_FLOOR = 2
Global PROJECTILE = 3

banner = LoadImage("top-banner.jpg")
MaskImage banner,255,255,0
Message=1

Type bullets
	Field obj,lifetime
End Type

; Light
AmbientLight 200,200,200

; Load Bullet Sprite
Global bulletsprite
bulletsprite = LoadSprite("bullet.jpg",0)
HideEntity bulletsprite

; Load Textures
cube_tile = LoadTexture("groundtile.jpg",1)
cube_tile2 = LoadTexture("groundtile2.jpg",1)
cube_tile1 = LoadTexture("groundtile.jpg")
weapon_tex = LoadTexture("w_rotation.pcx",1)
grunt_tex = LoadTexture("armour.pcx")

; Load Grunt & Weapon
character_pivot = CreatePivot()
grunt = LoadMD2("tris.md2",character_pivot)
EntityTexture grunt,grunt_tex
ScaleEntity grunt,0.04,0.04,0.04

weapon = LoadMD2("w_rotation.md2", character_pivot)
EntityTexture weapon, weapon_tex
ScaleEntity weapon,0.04,0.04,0.04

PositionEntity character_pivot,0,6,0
EntityType character_pivot,PLAYER
EntityRadius character_pivot,1

; Create The Scene
groundfloor = CreatePivot()

floor1 = CreateCube(groundfloor)
	ScaleEntity floor1,50,0.3,50
	ScaleTexture cube_tile,.03,.03
	EntityTexture floor1,cube_tile
	
obstacle = CreateCube(groundfloor)
	ScaleEntity obstacle,2,2,2
	EntityTexture obstacle,cube_tile2

	
obstacle2 = CreateCube(groundfloor)
	ScaleEntity obstacle2,2,3.4,2
	RotateEntity obstacle2,0,45,0
	PositionEntity obstacle2,3,0,7
	EntityTexture obstacle2,cube_tile2

	
obstacle3 = CreateCube(groundfloor)
	ScaleEntity obstacle3,2,2,2
	RotateEntity obstacle3,45,0,0
	PositionEntity obstacle3,7,0,4
	EntityTexture obstacle3,cube_tile2

	
EntityType groundfloor,GROUND_FLOOR,2

; Create The Camera Pivot and the Camera Itself
camera_pivot = CreatePivot()

camera = CreateCamera(camera_pivot)
	PositionEntity camera,0,4,-10
	PointEntity camera,camera_pivot
	CameraFogMode camera,1
	CameraFogRange camera,20,60
	
; Collision
Collisions PLAYER,GROUND_FLOOR,2,2

AnimateMD2 grunt,1,0.1,0,5
AnimateMD2 weapon,1,0.1,0,5

; Main Loop
While Not KeyHit(1)

	PointEntity camera,character_pivot
	checkgravity()
	shooting()
	movegrunt()
	flyingbullets()
	handlecamera()
	
	

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

		Text 0,100,"MegaBlast Demo"
		Text 0,120,"Keys: Arrow Keys to Move and Rotate Charater"
		Text 0,140,"Keys: A and Z to move camera up and down"
		Text 0,160,"Keys: Left 'Control' to Shoot"
		Text 0,180,"Keys: 'W' to toggle wireframe mode"
		Text 0,200,"Keys: H to hide this message
	End If
		
	Flip

Wend
End

; Functions
Function handlecamera()
    If KeyDown(key_z) Then
		MoveEntity camera_pivot,0,-0.1,0
	End If
	
	If KeyDown(key_a) Then
		MoveEntity camera_pivot,0,0.1,0
	End If
	
End Function

Function flyingbullets()
		For bullet.bullets = Each bullets
		  If bullet<>Null
		   EntityParent bullet\obj,groundfloor
		   MoveEntity bullet\obj,0,0,0.2
		     
		   bullet\lifetime = bullet\lifetime+50
		   deleted=0
   
			If bullet\lifetime>10000
			 FreeEntity bullet\obj
		     deleted=1
		    EndIf   

		    If deleted=1
			 Delete bullet
			End If
			
		  EndIf
		Next
End Function


Function shooting()
	If KeyDown(key_leftcontrol)
		shoot()
		AnimateMD2 grunt,1,0,53,47
		AnimateMD2 weapon,1,0,46,47
	End If
End Function

Function movegrunt()
	
	If KeyDown(key_arrowpad_left)
		TurnEntity character_pivot,0,2,0
	End If
	
	If KeyDown(key_arrowpad_right) 
		TurnEntity character_pivot,0,-2,0
	End If
	
	If KeyDown(key_arrowpad_up) And KeyDown(key_leftcontrol)=False
		MoveEntity character_pivot,0,0,0.1
		moved = 1
		forward = 1
	End If
	
	If KeyDown(key_arrowpad_down) And KeyDown(key_leftcontrol)=False
		MoveEntity character_pivot,0,0,-0.1
		moved = 1
		forward = 0
	End If
	
	If moved = True And KeyDown(key_leftcontrol)=False
	    If forward = True	
			If MD2AnimTime(grunt) < 40 Or MD2AnimTime(grunt) > 46 Then
				AnimateMD2 grunt,1,0.1,40,46
				AnimateMD2 weapon,1,0.1,40,46
			End If
		End If
	End If

	If forward = False And KeyDown(key_leftcontrol)=False
		If MD2AnimTime(grunt) < 40 Or MD2AnimTime(grunt) > 46 Then
			AnimateMD2 grunt,1,-0.08,40,45
			AnimateMD2 weapon,1,-0.08,40,45
		End If
	End If
		
	If moved = False And KeyDown(key_leftcontrol)=False
		If MD2AnimTime(grunt) < 0 Or MD2AnimTime(grunt) > 10 Then
			AnimateMD2 grunt,1,0.1,0,5
			AnimateMD2 weapon,1,0.1,0,5
		End If
    End If		
    moved = 0
	forward = 0

End Function

Function checkgravity()
	If Not EntityCollided(character_pivot,GROUND_FLOOR)
			MoveEntity character_pivot,0,-0.05,0
    End If
End Function

Function shoot()
 bullet.bullets = New bullets
 bullet\obj = CopyEntity(bulletsprite,character_pivot)
 RotateSprite bullet\obj,bulletangle#

If megablast > 90 Then
 ScaleSprite bullet\obj,1,0.5
Else
 ScaleSprite bullet\obj,0.4,0.2
End If

 EntityType bullet\obj,1
 EntityRadius bullet\obj,0.1
 PositionEntity bullet\obj,EntityX#(weapon)-0.2,EntityY#(weapon)+0.2,EntityZ#(weapon)+1
 TurnEntity bullet\obj,EntityPitch#(weapon),EntityYaw#(weapon),EntityRoll#(weapon)
 
 number_of_bullets = number_of_bullets + 1
 bulletangle# = bulletangle# + 20
 If bulletangle# > 360 Then bulletangle#=0
 
 megablast = megablast + 1
 If megablast = 100 Then megablast = 0

End Function