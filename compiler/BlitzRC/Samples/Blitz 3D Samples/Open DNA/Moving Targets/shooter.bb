; Little Moving Targets Demo
; By Reda Borchardt
; Note: The variable PT is an abbreviation for 'Practice Target'

Include "start.bb"
Include "KeyConstants.bb"

banner = LoadImage("top-banner.jpg")
MaskImage banner,255,255,0
message=1

Global number_of_bullets

Type targets
	Field obj,x#,y#,z#,state
End Type

Type bullets
	Field obj,lifetime
End Type

; Create Mouse Cursor Pivot
mouse_pivot = CreatePivot()
	PositionEntity mouse_pivot,0,0,30


;Load Bullet
Global bulletsprite
bulletsprite = LoadSprite("bullet.bmp",0,weapon)
HideEntity bulletsprite

; LoadTexture
Global pt_tex
pt_tex = LoadTexture("target.jpg")
weapon_tex = LoadTexture("w_railgun.pcx")

; Create The Targets


Dim pt.targets(12)
tempx = -3
tempz = -3

For i=0 To 11
	If tempx > 4
		tempz = tempz + 2
		tempx = -3
	End If

	pt(i) = New targets
	pt(i)\obj = buildtarget()
	pt(i)\x# = tempx
	pt(i)\y# = tempz
	pt(i)\z# = -10
	pt(i)\state = "standing"
	EntityType pt(i)\obj,2
	tempx = tempx + 2
	
Next

; Position The Targets Into The World
For i=0 To 11
	PositionEntity pt(i)\obj,pt(i)\x#,pt(i)\y#,pt(i)\z#
Next

;Create The Scene

mirror = CreateMirror() 
	PositionEntity mirror,0,-3,0
plane = CreatePlane()
	PositionEntity plane,0,-3,0
plane_tex = LoadTexture("plate.jpg",4)
ScaleTexture plane_tex,30,30
EntityTexture plane,plane_tex
EntityAlpha plane,0.6



light = CreateLight(3)
	LightColor light,0,0,30
	PositionEntity light,0,2,-30
	
AmbientLight 255,255,255


; Create The Camera Pivot and the Camera Itself
camera_pivot = CreatePivot()

camera = CreateCamera(camera_pivot)
	PositionEntity camera,0,1,-5
	PointEntity camera,camera_pivot
	CameraFogMode camera,1
	CameraFogRange camera,20,40
	
	
; Load The Gun
weapon = LoadMD2("w_railgun.md2",camera_pivot)
	EntityTexture weapon,weapon_tex
	PositionEntity weapon,0,-1,-3
	ScaleEntity weapon,0.1,0.1,0.1
	EntityShininess weapon,1
	AnimateMD2 weapon,1,0.03,0,5
	
; Collision
Collisions 1,2,1,3
	
; debug


; Main Loop
While Not KeyHit(1)

	If KeyDown(key_arrowpad_left)
		MoveEntity weapon,-0.05,0,0
	End If
	
	If KeyDown(key_arrowpad_right)
		MoveEntity weapon,0.05,0,0
	End If
	
	If KeyDown(key_arrowpad_up)
		MoveEntity weapon,0,0.05,0
	End If
	
	If KeyDown(key_arrowpad_down) And EntityY#(weapon) > -3.5
		MoveEntity weapon,0,-0.05,0
	End If
	
	If KeyDown(key_leftcontrol)
		shoot(weapon)
	End If
	
	; Target Motion
	
        For i=0 To 11
			If pt(i) <> Null
		       MoveEntity pt(i)\obj,0,0,0.1
			End If
		Next

		timer = timer + 50
		
		If timer > 25000
			For i=0 To 11
				If pt(i) <> Null
			       	FreeEntity pt(i)\obj
					Delete pt(i)
					timer=25000
				End If
			Next
		End If
		
		If timer > 25000
		
		; ReCreate The Targets

			tempx = -3
			tempz = -3

			For i=0 To 11
				If tempx > 4
				tempz = tempz +2
				tempx = -3
				End If

				pt(i) = New targets
				pt(i)\obj = buildtarget()
				pt(i)\x# = tempx
				pt(i)\y# = tempz
				pt(i)\z# = -10
				pt(i)\state = "standing"
				EntityType pt(i)\obj,2
				tempx = tempx + 2
	
			Next

			; RePosition The Targets Into The World
			For i=0 To 11
				PositionEntity pt(i)\obj,pt(i)\x#,pt(i)\y#,pt(i)\z#
			Next

			timer = 0
		End If
		

	
	; Move Bullets & Destroy Old Bullets
		For bullet.bullets = Each bullets
		  If bullet<>Null
		   MoveEntity bullet\obj,0,0,0.4
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
		
	; Test For Collision
	For bullet.bullets = Each bullets
  		If bullet<>Null
   		deleted=0
   			If EntityCollided(bullet\obj,2)
    			deleted=1
		    EndIf 
    
        	If deleted=1
		  		FreeEntity bullet\obj
	   	  		Delete bullet
	    	EndIf
  		EndIf
    Next

	For i=0 To 11
  		If pt(i)<>Null
   		deleted=0
   			If EntityCollided(pt(i)\obj,1)
    			Deleted=1
		    EndIf 
    
        	If Deleted=1
		  		FreeEntity pt(i)\obj
	   	  		Delete pt(i)
	    	EndIf
  		EndIf
    Next
		
	;Move Plane
	MoveEntity plane,0,0,0.1
	
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

		Text 0,100,"Little Moving Targets"
		Text 0,120,"Keys: Arrow Keys to Move Gun"
		Text 0,140,"Keys: Left Control To Shoot"
		Text 0,180,"Keys: 'W' to toggle wireframe mode"
		Text 0,200,"Keys: 'H' to hide this message
	End If
	
	Flip

Wend
End

; Functions

Function buildtarget()
    temp_target_pivot = CreatePivot()
	temp_target = CreateCube(temp_target_pivot)
		PositionEntity temp_target,0,1,0
		ScaleEntity temp_target,0.5,0.5,0.1
		EntityTexture temp_target,pt_tex
	EntityType temp_target_pivot,2,1
	
	; The reason I return the pivot is because I want 
	; To be able To tilt the Object properly using the 'pt' array
	Return temp_target_pivot 
End Function

Function shoot(weapon)
 bullet.bullets = New bullets
 bullet\obj = CopyEntity(bulletsprite)
 ScaleSprite bullet\obj,0.5,0.5
 EntityType bullet\obj,1
 EntityRadius bullet\obj,0.1
 PositionEntity bullet\obj,EntityX#(weapon)-0.7,EntityY#(weapon)+0.6,EntityZ#(weapon)+2
 TurnEntity bullet\obj,EntityPitch#(weapon),EntityYaw#(weapon),EntityRoll#(weapon)
 number_of_bullets = number_of_bullets + 1
End Function