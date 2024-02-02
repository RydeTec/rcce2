; Jump & Run Demo
; By Reda Borchardt

Include "start.bb"
Include "KeyConstants.bb"

banner = LoadImage("top-banner.jpg")

; Variables
Global xpos# = 0
Global ypos# = 0.5
Global zpos# = 0
Global xvelo# = 0
Global yvelo# = 0
Global zvelo# = 0

; Create and Setup Objects
character_pivot = CreatePivot()
	PositionEntity character_pivot,xpos#,ypos#,zpos#
	RotateEntity character_pivot,0,0,0

camera_pivot = CreatePivot(character_pivot)

camera = CreateCamera(camera_pivot)
	PositionEntity camera,0,3,-3
	PointEntity camera,character_pivot

; Create Metal Plates
level1 = CreatePivot()
	
metalplate = LoadMesh("model/metal-plate.3ds",level1)
	metalplate_texture = LoadTexture("textures/metal-plate.jpg")
	EntityTexture metalplate,metalplate_texture
	PositionEntity metalplate,0,-.2,0
	ScaleEntity metalplate,1.5,1,1.5

metalplate2 = CopyEntity(metalplate,level1)
	PositionEntity metalplate,0,-.2,1.5
	
metalplate3 = CopyEntity(metalplate,level1)
	PositionEntity metalplate3,1.5,-.2,1.5
	
metalplate4 = CopyEntity(metalplate,level1)
	PositionEntity metalplate4,1.5,-.2,0
	
metalplate5 = CopyEntity(metalplate,level1)
	PositionEntity metalplate5,1.5,-.2,3

metalplate6 = CopyEntity(metalplate,level1)
	PositionEntity metalplate6,1.5,-.2,4.5
	
metalplate7 = CopyEntity(metalplate,level1)
	PositionEntity metalplate7,0,-.2,4.5
	
;metalplate8 = CopyEntity(metalplate,level1)
;	PositionEntity metalplate8,3,-.2,1.5
	
;metalplate9 = CopyEntity(metalplate,level1)
;	PositionEntity metalplate9,3,-.2,0
	
metalplate10 = CopyEntity(metalplate,level1)
	PositionEntity metalplate10,3,-.2,3

metalplate11= CopyEntity(metalplate,level1)
	PositionEntity metalplate11,3,-.2,4.5
	
level2 = CopyEntity(level1)
	PositionEntity level2,0,0,6
	RotateEntity level2,0,90,0
	
level2 = CopyEntity(level2)
	PositionEntity level2,-1.5,0,0
	RotateEntity level2,0,90,0
	
metalplate9 = CopyEntity(metalplate,level1)
	PositionEntity metalplate9,-4.5,-.2,4.5



	
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

light2 = CreateLight(0)
	PositionEntity light2,-5,3,0
	LightColor light2,0,0,255
	
; Initialize Animations
AnimateMD2 character,1,0.09,0,5
AnimateMD2 weapon_blaster,1,0.09,0,5

; Create Collision Objects

cbox = CreateCube()
	ScaleEntity cbox,0.7,1,0.7
	PositionEntity cbox,-3,-.1,3
	FitMesh cbox,-1,0,-1,2,1,6.4
	
cbox2 = CopyEntity(cbox)
	ScaleEntity cbox2,0.7,1,0.45
	PositionEntity cbox2,-1.5,-.1,2.75
	
cbox3 = CopyEntity(cbox)
	ScaleEntity cbox3,0.7,1,0.225
	PositionEntity cbox3,0,-.1,2.5
	
EntityType cbox,2
EntityType cbox2,2
EntityType cbox3,2

EntityColor cbox,255,255,0
EntityColor cbox2,255,255,0
EntityColor cbox3,255,255,0

EntityAlpha cbox,0
EntityAlpha cbox2,0
EntityAlpha cbox3,0

EntityType character_pivot,1
EntityRadius character_pivot,0.5

Collisions 1,2,2,2

; Laser
laser = CreateCube(character_pivot)
				ScaleEntity laser,1000,0.017,0.017
				RotateEntity laser,0,90,0
				PositionEntity laser,-0.12,0.12,1000
				EntityColor laser,255,0,0
				EntityAlpha laser,1
				EntityBlend laser,3

HideEntity (laser)


; Main Loop
While Not KeyHit(1)

	moved = False
	shot = False
	forward = True
	
	TurnEntity laser,40,0,0
	
	;Shoot
	If KeyDown(key_leftcontrol)	
			If MD2AnimTime(character) < 46 Or MD2AnimTime(character) > 54 Then
				AnimateMD2 character,1,-0.08,46,54
				AnimateMD2 weapon_blaster,1,-0.08,46,54
			End If
			ShowEntity (Laser)
	
			shot=True
	End If
	
	If shot = False
		HideEntity(laser)
	EndIf

	;Walk
	 If KeyDown(key_arrowpad_up) And shot = False
		MoveEntity character_pivot,0,0,0.02
		moved = True
		forward = True
	 End If
	
   	If KeyDown(key_arrowpad_down) And shot = False
		MoveEntity character_pivot,0,0,0.-0.02
		moved = True
		forward = False
	 End If
	

	
	;Turn
	 If KeyDown(key_arrowpad_left) And shot = False
		TurnEntity character_pivot,0,2,0
		TurnEntity camera_pivot,0,-2,0
		moved = True
	 End If

	 If KeyDown(key_arrowpad_right) And shot = False
		TurnEntity character_pivot,0,-2,0
		TurnEntity camera_pivot,0,2,0
		moved = True
	 End If	
 
	
	 If moved = True And shot = False
	    If forward = True	
			If MD2AnimTime(character) < 40 Or MD2AnimTime(character) > 46 Then
				AnimateMD2 character,1,0.1,40,46
				AnimateMD2 weapon_blaster,1,0.1,40,46
			End If
		End If

		If forward = False	And shot= False
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


	UpdateWorld
	RenderWorld
	
	If EntityCollided(character_pivot,2)
		EntityAlpha cbox,0.2
		EntityAlpha cbox2,0.2
		EntityAlpha cbox3,0.2
    End If

	If Not EntityCollided(character_pivot,2)
		EntityAlpha cbox,0
		EntityAlpha cbox2,0
		EntityAlpha cbox3,0
    End If

	DrawImage banner,0,0

	Flip

Wend

; End
End