; Little Artic Runner
; By Reda Borchardt

Include "start.bb"
Include "keyconstants.bb"

SetBuffer BackBuffer()

banner = LoadImage("top-banner.jpg")
MaskImage banner,255,255,0
Message=1

Type bullets
	Field obj,lifetime
End Type


; Globals
Global camera_pivot
Global terrain
Dim diamond(101)
Global camera
Global weapon
Global megablast
Global bulletangle#

; Collision Groups
Global PLAYER = 1
Global SCENERY = 2
Global Enemy = 3
Global Projectile = 4


; Create Pivots
world_pivot = CreatePivot()
camera_pivot = CreatePivot()
EntityRadius camera_pivot,0.2


; Create Camera
camera = CreateCamera(camera_pivot)
CameraFogMode camera,1
CameraRange camera,0.01,1300
CameraFogColor camera,200,255,255

CameraFogRange camera,500,1200
PositionEntity camera_pivot,530,11,520
EntityRadius camera,0.1
EntityType camera,player
EntityType camera_pivot,PLAYER,1

; Create Diamonds
diamond(0) = CreateSphere(8)
diamond_tex = LoadTexture("diamond_tex.jpg")
EntityTexture diamond(0),diamond_tex
ScaleEntity diamond(0),0.1,0.1,0.1
EntityType diamond(0),ENEMY
EntityRadius diamond(0),0.1
PositionEntity diamond(0),530,14,523

For i = 1 To 100
	tempx = tempx + 5
	If tempx >20 Then tempx = 0 : tempz = tempz +5
	diamond(i) = CopyEntity(diamond(0))
	PositionEntity diamond(i),530+tempz,14,523+tempx
Next


; Load The Weapon
weapon = LoadMD2("w_rotation.md2",camera_pivot)
weapon_tex = LoadTexture("w_rotation.pcx")

EntityTexture weapon,weapon_tex

ScaleEntity weapon,0.001,0.001,0.001
PositionEntity weapon,0,-0.03,0.01

; Scenery
terrain=LoadTerrain( "height.jpg" )
TerrainDetail terrain,2000,True
ScaleEntity terrain,1,30,1

	sky = CreateSphere()
	FlipMesh sky
	ScaleEntity sky,2000,70,2000
	PositionEntity sky,1000,0,1000
	PositionEntity sky,0,50,0
	
grass_tex=LoadTexture( "sky.jpg" )
test_tex=LoadTexture( "groundtile.jpg")
sky_tex=LoadTexture( "sky.jpg",8)
ScaleTexture test_tex,0.1,0.1
ScaleTexture grass_tex,0.1,0.1
TextureBlend test_tex,2
ScaleTexture grass_tex,4000,4000
EntityTexture terrain,grass_tex,0,1
EntityTexture terrain,test_tex,0,2
EntityTexture sky,sky_tex,0,1

horizon = CreateCylinder(16,camera_pivot)
FlipMesh horizon
ScaleEntity horizon,1100,300,1100
EntityColor horizon,255,255,255
PositionEntity horizon,500,0,500

EntityType terrain,SCENERY

;Handle Collisions
Collisions PLAYER,SCENERY,2,3
Collisions PLAYER,ENEMY,2,3
Collisions ENEMY,SCENERY,2,3
Collisions Projectile, Scenery,2,3
Collisions Projectile, Enemy,2,3

; Light
AmbientLight 200,200,200

; Load Bullet Sprite
Global bulletsprite
bulletsprite = LoadSprite("bullet.jpg",0)
HideEntity bulletsprite

; Main Loop
While Not KeyHit(1)


	checkgravity()
	shooting()
	flyingbullets()
	rotatediamonds()
	movecamera()
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

		Text 0,100,"Artic Demo"
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
		TurnEntity camera_pivot,-1,0,0
	End If
	
	If KeyDown(key_a) Then
		TurnEntity camera_pivot,1,0,0
	End If
End Function

Function rotatediamonds()
	For i = 0 To 100
		If diamond(i)
		 TurnEntity diamond(i),0,3,0
		End If
		
		If diamond(i)<>0
		If EntityCollided(diamond(i),projectile)
			FreeEntity diamond(i)
			diamond(i) = 0
		End If
		End If
	Next
End Function

Function movecamera()
	
	If KeyDown(key_arrowpad_left)
		TurnEntity camera_pivot,0,2,0
	End If
	
	If KeyDown(key_arrowpad_right) 
		TurnEntity camera_pivot,0,-2,0
	End If
	
	If KeyDown(key_arrowpad_up) And KeyDown(key_leftcontrol)=False
		MoveEntity camera_pivot,0,0,0.03
		moved = 1
		forward = 1
	End If
	
	If KeyDown(key_arrowpad_down) And KeyDown(key_leftcontrol)=False
		MoveEntity camera_pivot,0,0,-0.03
		moved = 1
		forward = 0
	End If
	
	If moved = True And KeyDown(key_leftcontrol)=False
	    If forward = True	
			If MD2AnimTime(weapon) < 40 Or MD2AnimTime(weapon) > 46 Then
				AnimateMD2 weapon,1,0.1,40,46
			End If
		End If
	End If

	If forward = False And KeyDown(key_leftcontrol)=False
		If MD2AnimTime(weapon) < 40 Or MD2AnimTime(weapon) > 46 Then
			AnimateMD2 weapon,1,-0.08,40,45
		End If
	End If
		
	If moved = False And KeyDown(key_leftcontrol)=False
		If MD2AnimTime(weapon) < 0 Or MD2AnimTime(weapon) > 10 Then
			AnimateMD2 weapon,1,0.1,0,5
		End If
    End If	
	

    moved = 0
	forward = 0
End Function

Function checkgravity()
	If Not EntityCollided(camera_pivot,SCENERY)
			TranslateEntity camera_pivot,0,-0.01,0
    End If
	For i=0 To 100
	   If diamond(i)<>0
		If Not EntityCollided(diamond(i),SCENERY)
				TranslateEntity diamond(i),0,-0.01,0
   		End If
       End If
	Next
End Function

Function shoot()
 bullet.bullets = New bullets
 bullet\obj = CopyEntity(bulletsprite,camera_pivot)
 RotateSprite bullet\obj,bulletangle#

If megablast > 90 Then
 ScaleSprite bullet\obj,0.05,0.025
Else
 ScaleSprite bullet\obj,0.05/2,0.025/2
End If

 EntityType bullet\obj,Projectile
 EntityRadius bullet\obj,0.1
 PositionEntity bullet\obj,EntityX#(weapon)-0.01,EntityY#(weapon)-0.01,EntityZ#(weapon)+0.05
 TurnEntity bullet\obj,EntityPitch#(weapon),EntityYaw#(weapon),EntityRoll#(weapon)
 
 number_of_bullets = number_of_bullets + 1
 bulletangle# = bulletangle# + 20
 If bulletangle# > 360 Then bulletangle#=0
 
 megablast = megablast + 1
 If megablast = 100 Then megablast = 0

End Function

Function shooting()
	If KeyDown(key_leftcontrol)
		shoot()
		AnimateMD2 weapon,1,0,46,47
	End If
End Function

Function flyingbullets()
		For bullet.bullets = Each bullets
		  If bullet<>Null
		   EntityParent bullet\obj,world_pivot
		   MoveEntity bullet\obj,0,0,0.06
		     
		   bullet\lifetime = bullet\lifetime+50
		   deleted=0
   
			If bullet\lifetime>5000
			 FreeEntity bullet\obj
		     deleted=1
		    EndIf
			
			If deleted = 0
			If EntityCollided(bullet\obj,ENEMY)
				FreeEntity bullet\obj
				deleted = 1
			End If   
			End If

		    If deleted=1
			 Delete bullet
			End If
			
		  EndIf
		Next
		
		
End Function