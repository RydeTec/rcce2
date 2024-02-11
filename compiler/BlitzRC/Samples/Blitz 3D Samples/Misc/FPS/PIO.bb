Const player_forward = 200
Const player_strafe_left = 203
Const player_strafe_right = 205
Const player_backward = 208
Const player_crouch = 157
Const player_jump = 57


Const mouse_sensitivity = 1

;player input/output
Global player_speed = 1
Const player_standing_speed = 1
Global player_eyeheight = 12
Const player_normal_eyeheight = 12;used for crouching

;crouching variables
Const player_crouching_speed# = 0.2
Const player_crouching_ratio# = 2;you are this many times beigger if you are not crouching
Const player_crouching_move_speed = 1
Global player_crouching_progress# = 0

;jumping variables
Const player_jump_height# = 3
Const player_jump_loose_velocity# = 0.1

Global player_jumping = 0
Global player_jumping_progress# = 0
Dim player_jump_direction#(3);0 = forward, 1 = left, 2 = right, 3 = backward - all is speed!!!!

Global player_max_speed# = -3

;movement counter
Global movement_counter = -250
Global movement_counter_direction = -1

MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
dumpvar = MouseXSpeed()
dumpvar = MouseYSpeed()

Function IO()
	cam()
	playerinput()
	physics()
	weapons()
End Function

Function cam()
	;biiiiiiiig chunk of code, to make sure that the player doesnt mouselook above 90 degrees or
	;below -90, therefore there is no risk of having the camera 'flipped'
	
	;pitch = MouseYSpeed() * mouse_sensitivity + EntityPitch(camera)
	myspeed = MouseYSpeed() * mouse_sensitivity
	If EntityPitch(camera) + myspeed < -89
		pitch = -89
	ElseIf EntityPitch(camera) + myspeed > 89
		pitch = 89
	Else
		pitch = EntityPitch(camera) + myspeed
	EndIf
	
		
	yaw = MouseXSpeed() * -1 * mouse_sensitivity + EntityYaw(camera)
	
	RotateEntity camera, pitch, yaw, 0
	RotateEntity playerbox, EntityPitch(playerbox), yaw, 0
	
	MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
	dumpvar = MouseXSpeed()
	dumpvar = MouseYSpeed()
End Function


Function playerinput()
	
	If Not player_jumping
		If KeyDown(player_forward)
			If KeyDown(player_strafe_left) <> 1 And KeyDown(player_strafe_right) <> 1
				MoveEntity playerbox, 0, 0, player_speed
			Else
				MoveEntity playerbox, 0, 0, player_speed*Sin(45)
			EndIf
		EndIf
		If KeyDown(player_strafe_left)
			If KeyDown(player_forward) <> 1 And KeyDown(player_backward) <> 1
				MoveEntity playerbox, player_speed*-1 , 0, 0
			Else
				MoveEntity playerbox, player_speed*-Sin(45), 0, 0
			EndIf
		EndIf
		If KeyDown(player_strafe_right)
			If KeyDown(player_forward) <> 1 And KeyDown(player_backward) <> 1
				MoveEntity playerbox, player_speed, 0, 0
			Else	
				MoveEntity playerbox, player_speed*Sin(45), 0, 0
			EndIf
		EndIf
		If KeyDown(208)
			If KeyDown(player_strafe_left) <> 1 And KeyDown(player_strafe_right) <> 1
				MoveEntity playerbox, 0, 0, player_speed * -1
			Else
				MoveEntity playerbox, 0, 0, player_speed * -(Sin(45))
			EndIf
		EndIf 		

		If KeyDown(player_crouch)
			If player_crouching_progress# < 1
				player_crouching_progress# = player_crouching_progress# + player_crouching_speed#
			EndIf
		Else
			If player_crouching_progress# > 0
				player_crouching_progress# = player_crouching_progress# - player_crouching_speed#
			EndIf
			If player_crouching_progress# < 0
				player_crouching_progress# = 0
			EndIf
		EndIf
		
		
		player_eyeheight = player_normal_eyeheight / (player_crouching_ratio#*player_crouching_progress#)
		If player_eyeheight > player_normal_eyeheight Or player_eyeheight < -1000;below -1000 catches those awful divide by 0's
			player_eyeheight = player_normal_eyeheight
		EndIf
		
		If player_eyeheight <> player_normal_eyeheight;player is crouching
			player_speed = player_crouching_move_speed
		Else
			player_speed = player_standing_speed
		EndIf
		If KeyDown(player_forward) Or KeyDown(player_backward) Or KeyDown(player_strafe_left) Or KeyDown(player_strafe_right)
			movement_counter = movement_counter + movement_counter_direction
			If movement_counter < -250
				movement_counter = -250
				movement_counter_direction = 6
			ElseIf movement_counter > -110
				movement_counter = -110
				movement_counter_direction = -6
			EndIf
		EndIf
	EndIf
	
	If Not player_jumping
		If KeyDown(player_jump)
			player_jumping = True
			player_jumping_progress# = player_jump_height#
			If KeyDown(player_forward)
				If KeyDown(player_strafe_left) Xor KeyDown(player_strafe_right)
					player_jump_direction#(0) = player_speed * Sin(45)
				Else
					player_jump_direction#(0) = player_speed
				EndIf
			EndIf
			
			If KeyDown(player_strafe_left)
				If KeyDown(player_forward) Xor KeyDown(player_backward)
					player_jump_direction#(1) = player_speed * Sin(45) * -1
				Else
					player_jump_direction#(1) = player_speed * -1
				EndIf
			EndIf
			
			If KeyDown(player_strafe_right)
				If KeyDown(player_forward) Xor KeyDown(player_backward)
					player_jump_direction#(2) = player_speed * Sin(45)
				Else
					player_jump_direction#(2) = player_speed
				EndIf
			EndIf
			
			
			If KeyDown(player_backward)
				If KeyDown(player_strafe_left) Xor KeyDown(player_strafe_right)
					player_jump_direction#(3) = player_speed * Sin(45) * -1
				Else
					player_jump_direction#(3) = player_speed * -1
				EndIf
			EndIf
		EndIf
	Else
		y# = EntityY(playerbox) + player_jumping_progress#
		PositionEntity playerbox, EntityX(playerbox), y#, EntityZ(playerbox)
		forwardspeed# = player_jump_direction#(0) + player_jump_direction#(3)
		MoveEntity playerbox, 0, 0, forwardspeed#
		sidespeed# = player_jump_direction#(1) + player_jump_direction#(2)
		MoveEntity playerbox, sidespeed#, 0, 0
		

		
		If EntityCollided(playerbox, type_backdrop)
			If Abs(player_jumping_progress)<> player_jumping_progress	
				player_jumping = 0
				For dumpvar = 0 To 3
					player_jump_direction#(dumpvar) = 0
				Next
			EndIf
		EndIf
	EndIf
	player_jumping_progress# = player_jumping_progress# - player_jump_loose_velocity#
	If player_jumping_progress# < player_max_speed#
		player_jumping_progress# = player_max_speed#
	EndIf
	
	PositionEntity camera, EntityX(playerbox), EntityY(playerbox) + player_eyeheight, EntityZ(playerbox)
End Function

Function physics()
	If Not player_jumping
		PositionEntity playerbox, EntityX(playerbox), EntityY(playerbox) + player_jumping_progress#, EntityZ(playerbox)
	EndIf
End Function


Function weapons()
	firinghitscan = 0
	
	For g.gun = Each gun
	;	xoffset# = Sin(movement_counter)
		yoffset# = Sin(EntityPitch(camera)*0.3);Cos(movement_counter)
		zoffset# = Cos(EntityPitch(camera))+Sin(movement_counter)*0.3
		If MilliSecs() - g\timesincelastshot < 200
			RotateEntity g\model, Sin(MilliSecs() - g\timesincelastshot)*-50, 0, 0
		Else
			RotateEntity g\model, 0, 0, 0
		EndIf
		PositionEntity g\model, 2.5+xoffset#,-2+yoffset#,3+zoffset#

		If g\selected = 1
			If g\class = 1
				If MilliSecs() - g\timesincelastshot > 20
					EntityAlpha g\muzzle, 0
				EndIf
				If MouseHit(1)
					;If MilliSecs() - g\timesincelastshot > 200
						g\timesincelastshot = MilliSecs()
						EntityAlpha g\muzzle, 1	
					;	Animate g\model, 3, 1, 0, 0			
					;EndIf
					firinghitscan = 1

					
				EndIf
			EndIf
		EndIf
		If firinghitscan
			target = CameraPick(camera, GraphicsWidth()/2, GraphicsHeight()/2)
			If target = level
				createparticle(PickedX(), PickedY(), PickedZ())
				createdecal(PickedX(), PickedY(), PickedZ())
			EndIf
		;	For c.clients = Each clients
	;			If target = c\entity
;					If g\class = 1;;
		;				shootclient(c\entity, 1)
		;			EndIf
		;		EndIf
		;	Next			
		EndIf
	Next
End Function