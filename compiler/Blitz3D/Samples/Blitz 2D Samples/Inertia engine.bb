; -----------------------------------------------------------------------------
;
; Zodiacs inertia engine 
; 
;
; -----------------------------------------------------------------------------


AppTitle "Intertia Engine"					; Taskbar title

Const screenw				= 1024			; Screen width
Const screenh				= 768			; Screen height
Graphics screenw, screenh					; Open display

;Image variables
Global backstars = CreateImage (300,300)	; Background stars
Dim rocketframes (playerrotframes)			; Array of images for the turn frames

; -----------------------------------------------------------------------------
; Player keys
; -----------------------------------------------------------------------------

Const key_csrup				= 200			; Up cursor
Const key_csrleft			= 203			; Left cursor
Const key_csrright			= 205			; Right cursor
Const key_space				= 57			; Space

; -------------------------------------------------------------------------
; These variables define the offset of the screen to the (moving) ship
;--------------------------------------------------------------------------

Global elas_move#		=.009		;How fast the screen will move towards the ship, defines the elasticity, together with:
Global elas_bounce#		=1.08		;The Elasticity of the screen, in combination with elas_move		
Global elas_dx#			=0			;Current ScreenSpeed in x Direction (used by Routine)
Global elas_dy#			=0			;Current ScreenSpeed in Y Direction (used by Routine)
Global elas_fixoffsetx#	=0			;Fixed x-Offset of the ship (from the Center of screen)
Global elas_fixoffsety#	=0			;Fixed y-Offset of the ship (from the Center of screen)
Global elas_runaheadx#	=40			;This is multiplied with the speed of the ship to determine the number of pixels that the screen heads in front of the ship when it is moving
Global elas_runaheady#	=32			;This is multiplied with the speed of the ship to determine the number of pixels that the screen heads in front of the ship when it is moving
Global elas_posx#		=1000		;Current Position of the Screenmiddle (used by routine)
Global elas_posy#		=1000		;Current Position of the Screenmiddle (used by routine)

; -------------------------------------------------------------------------
; These variables define the movement of the player ship
;--------------------------------------------------------------------------

Global	player_x#					= screenw/2		; X position
Global	player_y#					= screenh/2		; Y position
Global	player_dx#					= 0				; X speed
Global	player_dy#					= 0				; Y speed
Global	player_frame				= 0				; Current Frame (= Direction)
Global	player_gravity#				= 0.0			; Gravity applied 
Global	player_maxspeed#			= 5				; Max Speed
Global	player_friction#			= 0.04			; Friction applied to speed
Global	player_xacceleration#		= 0.11			; How fast it boosts in the x direction (mixed with Sin/Cos though)
Global	player_yaccelerationu#		= 0.11			; Upwards boosting (again, with Sin and Cos applied)
Global	player_yaccelerationd#		= 0.11			; Downward boosting (Gravity also pulls down, so maybe this should be less than up acceleration)
Global	player_turnacceleration#	= 0.18			; Acceleration when turning
Global	player_turnfriction#		= 0.08			; The friction applied when turning
Global	player_turnmax#				= 3				; Max Turnspeed
Global	player_turnspeed#			= 0				; Current Speed in turning
Global	player_turnangle#			= 0				; Current angle of ship

Const totalvehicles = 7		;Number of different vehicle dynamics available

; -----------------------------------------------------------------------------
; Ship Image
; -----------------------------------------------------------------------------

; Number of frames for whole rotation

Const playerrotframes		= 64						; Number of rotation frames
Const playerrotangle# = Float (360)/playerrotframes		; Step

; Define the starting behavior of the player
Global aeroplane$		;Name of current vehicle
Global vehicle			;Type of current dynamic set
setdynamics (0)			;Set all movement variables accordingly

; ----------------------------------------
; Lookup Tables for trigonometric funtions
; ----------------------------------------

Dim sintab# (360)
Dim costab# (360)
For t = 0 To 360
	sintab#(t) = Sin (t)					; Pre-calc trig stuff (Sin)
	costab#(t) = Cos (t)					; Pre-calc trig stuff (Cos)
Next

; ----------------------------------------
; Sketch the images 
; ----------------------------------------

AutoMidHandle True					; Centre future image handles
createimages ()						; Draw rocket & background
Color 255,255,255					; Future Drawing color: white
SetBuffer BackBuffer ()				; Hidden buffer

; -----------------------------------------------------------------------------
; main loop...
; -----------------------------------------------------------------------------


Repeat
	checkjoy()									; Check Input device
	MoveRocket ()								; Calculate rocket position
	dofriction ()								; Apply friction and gravity
	DrawRocket ()								; Draw rocket + background
	Text 10,10,aeroplane$
	Text 10,24,"Change Vehicle with <space>, steer with <cursor keys>"
	Flip										; Show result

Until KeyDown (1) = 1							; [ESC] key quits game
End

; -----------------------------------------------------------------------------
; Functions used
; -----------------------------------------------------------------------------

; -----------------------------------------------------------------------------
; Check player keys
; -----------------------------------------------------------------------------

Function Checkjoy ()
		
	; Cursur right
		
		If KeyDown (key_csrright)
			player_turnspeed = player_turnspeed + player_turnacceleration
			If player_turnspeed > player_turnmax Then player_turnspeed = player_turnmax
		EndIf

	; Cursur left
		
		If KeyDown (key_csrleft) Then
			player_turnspeed = player_turnspeed - player_turnacceleration
			If player_turnspeed < -player_turnmax Then player_turnspeed = -player_turnmax
		EndIf
		
	; Cursur up, thrust
		
		If KeyDown (key_csrup) Then
			;X acceleration
			player_dx=player_dx+costab#(player_frame*playerrotangle)*player_xacceleration
			
			;Y acceleration
			dy#=-sintab#(player_frame*playerrotangle)
			If dy# > 0 Then 
					dy# = dy * player_yaccelerationu	;Upwards acceleration may differ from ...
				Else
					dy#	= dy * player_yaccelerationd	;...downwardas acceleration, because it can become too strong together with gravity
			End If
			player_dy=player_dy-dy
		EndIf

	; Space, change vehicle
		
		If KeyHit (key_space) Then 
		
			vehicle = vehicle + 1
			If vehicle = totalvehicles Then vehicle = 0
			setdynamics (vehicle)
			player_dx = 0
			player_dy = 0
			player_turnspeed = 0
				
		End If
					
End Function

; ----------------------------------------------------------------------------------
; Calculate Screen position  (screen is not fixed, it´s "elastic" to player position)
; ----------------------------------------------------------------------------------

Function DrawRocket ()
    
	;	Explanation:
	;
	;delta = (delta + (player - pos)*emove )/ebounce
	;
	;		delta 	- velocity of screen
	;		pos		- position of screen
	;		player	- position of player
	;		emove	- defines the strength of elasticity
	;		ebounce  - defines the strength of elasticity
	
    elas_dx=(elas_dx+(player_x-elas_fixoffsetx-elas_posx+elas_runaheadx*player_dx)*elas_move)/elas_bounce	;x
    elas_dy=(elas_dy+(player_y-elas_fixoffsety-elas_posy+elas_runaheady*player_dy)*elas_move)/elas_bounce	;y
    elas_posx=elas_posx+elas_dx				;New screen x positon 
    elas_posy=elas_posy+elas_dy				;New screen y positon 
	
	
	;Player postion relative to screen middle
	
	x=player_x-elas_posx+screenw/2			;x
	y=player_y-elas_posy+screenh/2			;y
	; Inverted (No good results:)
	;	x=elas_posx-player_x+screenw/2
	;	y=elas_posy-player_y+screenh/2

	
	TileBlock backstars,-elas_posx,-elas_posy
	DrawImage rocketframes (player_frame), x, y

End Function

; -----------------------------------------------------------------------------
; Apply velocities to player position:
; -----------------------------------------------------------------------------

Function MoveRocket ()
	player_x = player_x + player_dx
	player_y = player_y + player_dy

End Function


; -----------------------------------------------------------------------------
; Applies "friction" and gravity to rocket's velocity:
; -----------------------------------------------------------------------------

Function dofriction()

	;Set friction to movement
		;gravity
		player_dy=player_dy + player_gravity						;Add gravity to y speed

		;calculate speed & angle
		speed#=Sqr((player_dx)*(player_dx)+(player_dy)*(player_dy))	;Calculate true "diagonal" speed
		angle#=(ATan2(player_dx,player_dy)+270) Mod 360 			;Calcutate true angle

		;friction
		If speed > player_maxspeed Then speed = player_maxspeed 	;Limit speed
		speed = speed - player_friction								;Apply friction to speed
		If speed<0 Then speed = 0									;Lowest speed: 0
		
		;set back dx & dy
		player_dx = speed*Cos(angle)								;New speed in x direction
		player_dy = speed*-Sin(angle)								;New speed in y direction


	;Set friction to rotation
		;set the new turnangle
		player_turnangle = (360+player_turnangle+player_turnspeed) Mod 360
		
		;calculate the framenumber of this angle
		player_frame = player_turnangle/playerrotangle
		
		;apply momentum to player rotation
		If player_turnspeed > 0 Then
				;turnspeed > 0, clockwise
				player_turnspeed=player_turnspeed-player_turnfriction
				If player_turnspeed < 0 Then player_turnspeed = 0		;stop turning
			Else
				;turnspeed < 0, anti-clockwise
				player_turnspeed=player_turnspeed+player_turnfriction
				If player_turnspeed > 0 Then player_turnspeed = 0		;stop turning
		End If

End Function

; -----------------------------------------------------------------------------
; Sketch Player and background and grab it to an image:
; -----------------------------------------------------------------------------

Function createimages ()

; Create Star background
 SetBuffer ImageBuffer(backstars)
	For star = 0 To 400
		Color Rnd(255),Rnd(255),Rnd(255)
		Plot Rnd(300),Rnd(300)
	Next 
	For star = 0 To 70
		Color Rnd(255),Rnd(255),Rnd(255)
		Rect Rnd(300),Rnd(300),2,2
	Next 

; Create player ship
	rocketframes (0) = CreateImage (40,60)		;Image with size
	SetBuffer ImageBuffer (rocketframes(0))
	Color 150,255,180	;Drawing color
; Draw ship outlines
	Line 21,0,40,60
	Line 21,0,0,60
	Line 0,59,42,59
	Line 21,6,30,40
	Line 21,6,12,40
	Line 21,45,27,50
	Line 21,45,15,50
	
	SetBuffer BackBuffer()
	; This section renders the full 360 degrees of rocket images

	RotateImage rocketframes (0),90							;Ship is sketched facing up, but has to face right for correct angles
	For frame = 1 To playerrotframes						; Render frames
		rocketframes (frame) = CopyImage (rocketframes (0))	; Copy loaded image
		angle# = angle# + playerrotangle#	 			
		TFormFilter False									; No dithering!
		RotateImage rocketframes (frame),angle				; Rotate the copy by whatever
	Next

 End Function


; -----------------------------------------------------------------------------
; Set the movement characteristics of the different vehicles
; -----------------------------------------------------------------------------

Function setdynamics (number)

Select number

Case 0
 aeroplane$ ="Space ship (Mine Storm)"
 elas_move#			=.01		;How fast the screen will move towards the ship, definesthe elasticity, together with:
 elas_bounce#		=1.07		;The Elasticity of the screen, in combination with elas_move		
 elas_dx#			=0			;Current ScreenSpeed in x Direction (used by Routine)
 elas_dy#			=0			;Current ScreenSpeed in Y Direction (used by Routine)
 elas_fixoffsetx#	=0			;Fixed x-Offset of the ship (from the Center of screen)
 elas_fixoffsety#	=0			;Fixed y-Offset of the ship (from the Center of screen)
 elas_runaheadx#	=0			;This is multiplied with the speed of the ship to determine the number of pixels that the screen heads in front of the ship when it is moving
 elas_runaheady#	=0			;This is multiplied with the speed of the ship to determine the number of pixels that the screen heads in front of the ship when it is moving
 elas_posx#			=1000		;Current Position of the Screenmiddle (used by routine)
 elas_posy#			=1000		;Current Position of the Screenmiddle (used by routine)

 player_x#					= screenw/2		; X position
 player_y#					= screenh/2		; Y position
 player_dx#					= 0				; X speed
 player_dy#					= 0				; Y speed
 player_frame				= 0				; Current Frame (= Direction)
 player_gravity#			= 0.0			; Gravity applied 
 player_maxspeed#			= 8				; Max Speed
 player_friction#			= 0.15			; Friction applied to speed
 player_xacceleration#		= 0.50			; How fast it boosts in the x direction (mixed with Sin/Cos though)
 player_yaccelerationu#		= 0.50			; Upwards boosting (again, with Sin and Cos applied)
 player_yaccelerationd#		= 0.50			; Downward boosting (Gravity also pulls down, so maybe this should be less than up acceleration)
 player_turnacceleration#	= 0.6			; Acceleration when turning
 player_turnfriction#		= 0.3			; The friction applied when turning
 player_turnmax#			= 5				; Max Turnspeed
 player_turnspeed#			= 0				; Current Speed in turning
 player_turnangle#			= 0				; Current angle of ship
 player_realspeed#			= 0				; True AirSpeed (used by routine)
 player_realangle#			= 0				; True AirAngle (used by routine)

Case 1
 aeroplane$ ="Helicopter"
 elas_move#			=.009		;How fast the screen will move towards the ship, definesthe elasticity, together with:
 elas_bounce#		=1.08		;The Elasticity of the screen, in combination with elas_move		
 elas_dx#			=0			;Current ScreenSpeed in x Direction (used by Routine)
 elas_dy#			=0			;Current ScreenSpeed in Y Direction (used by Routine)
 elas_fixoffsetx#	=0			;Fixed x-Offset of the ship (from the Center of screen)
 elas_fixoffsety#	=0			;Fixed y-Offset of the ship (from the Center of screen)
 elas_runaheadx#	=46			;This is multiplied with the speed of the ship to determine the number of pixels that the screen heads in front of the ship when it is moving
 elas_runaheady#	=35			;This is multiplied with the speed of the ship to determine the number of pixels that the screen heads in front of the ship when it is moving
 elas_posx#			=1000		;Current Position of the Screenmiddle (used by routine)
 elas_posy#			=1000		;Current Position of the Screenmiddle (used by routine)

 player_x#					= screenw/2		; X position
 player_y#					= screenh/2		; Y position
 player_dx#					= 0				; X speed
 player_dy#					= 0				; Y speed
 player_frame				= 0				; Current Frame (= Direction)
 player_gravity#			= 0.0			; Gravity applied 
 player_maxspeed#			= 5				; Max Speed
 player_friction#			= 0.032			; Friction applied to speed
 player_xacceleration#		= 0.10			; How fast it boosts in the x direction (mixed with Sin/Cos though)
 player_yaccelerationu#		= 0.10			; Upwards boosting (again, with Sin and Cos applied)
 player_yaccelerationd#		= 0.10			; Downward boosting (Gravity also pulls down, so maybe this should be less than up acceleration)
 player_turnacceleration#	= 0.18			; Acceleration when turning
 player_turnfriction#		= 0.06			; The friction applied when turning
 player_turnmax#			= 3				; Max Turnspeed
 player_turnspeed#			= 0				; Current Speed in turning
 player_turnangle#			= 0				; Current angle of ship
 player_realspeed#			= 0				; True AirSpeed (used by routine)
 player_realangle#			= 0				; True AirAngle (used by routine)

Case 2
 aeroplane$ ="Space ship with gravity (Thurst)"
 elas_move#			=.007		;How fast the screen will move towards the ship, defines the elasticity, together with:
 elas_bounce#		=1.065		;The Elasticity of the screen, in combination with elas_move		
 elas_dx#			=0			;Current ScreenSpeed in x Direction (used by Routine)
 elas_dy#			=0			;Current ScreenSpeed in Y Direction (used by Routine)
 elas_fixoffsetx#	=0			;Fixed x-Offset of the ship (from the Center of screen)
 elas_fixoffsety#	=0			;Fixed y-Offset of the ship (from the Center of screen)
 elas_runaheadx#	=0			;This is multiplied with the speed of the ship to determine the number of pixels that the screen heads in front of the ship when it is moving
 elas_runaheady#	=0			;This is multiplied with the speed of the ship to determine the number of pixels that the screen heads in front of the ship when it is moving
 elas_posx#			=1000		;Current Position of the Screenmiddle (used by routine)
 elas_posy#			=1000		;Current Position of the Screenmiddle (used by routine)

 player_x#					= screenw/2		; X position
 player_y#					= screenh/2		; Y position
 player_dx#					= 0				; X speed
 player_dy#					= 0				; Y speed
 player_frame				= 0				; Current Frame (= Direction)
 player_gravity#			= 0.05			; Gravity applied 
 player_maxspeed#			= 6				; Max Speed
 player_friction#			= 0.03			; Friction applied to speed
 player_xacceleration#		= 0.15			; How fast it boosts in the x direction (mixed with Sin/Cos though)
 player_yaccelerationu#		= 0.15			; Upwards boosting (again, with Sin and Cos applied)
 player_yaccelerationd#		= 0.1			; Downward boosting (Gravity also pulls down, so maybe this should be less than up acceleration)
 player_turnfriction#		= 0.3			; The friction applied when turning
 player_turnspeed#			= 0				; Current Speed in turning
 player_turnangle#			= 0				; Current angle of ship
 player_turnmax#			= 3				; Max Turnspeed
 player_turnacceleration#	= 1.0			; Acceleration when turning
 player_realspeed#			= 0				; True AirSpeed (used by routine)
 player_realangle#			= 0				; True AirAngle (used by routine)


Case 3
 aeroplane$ ="Tank"
 elas_move#			=.09		;How fast the screen will move towards the ship, defines the elasticity, together with:
 elas_bounce#		=3.45		;The Elasticity of the screen, in combination with elas_move		
 elas_dx#			=0			;Current ScreenSpeed in x Direction (used by Routine)
 elas_dy#			=0			;Current ScreenSpeed in Y Direction (used by Routine)
 elas_fixoffsetx#	=0			;Fixed x-Offset of the ship (from the Center of screen)
 elas_fixoffsety#	=0			;Fixed y-Offset of the ship (from the Center of screen)
 elas_runaheadx#	=60			;This is multiplied with the speed of the ship to determine the number of pixels that the screen heads in front of the ship when it is moving
 elas_runaheady#	=48			;This is multiplied with the speed of the ship to determine the number of pixels that the screen heads in front of the ship when it is moving
 elas_posx#			=1000		;Current Position of the Screenmiddle (used by routine)
 elas_posy#			=1000		;Current Position of the Screenmiddle (used by routine)

 player_x#					= screenw/2		; X position
 player_y#					= screenh/2		; Y position
 player_dx#					= 0				; X speed
 player_dy#					= 0				; Y speed
 player_frame				= 0				; Current Frame (= Direction)
 player_gravity#			= 0.0			; Gravity applied 
 player_maxspeed#			= 4.5			; Max Speed
 player_friction#			= 0.14			; Friction applied to speed
 player_xacceleration#		= 0.2			; How fast it boosts in the x direction (mixed with Sin/Cos though)
 player_yaccelerationu#		= 0.2			; Upwards boosting (again, with Sin and Cos applied)
 player_yaccelerationd#		= 0.2			; Downward boosting (Gravity also pulls down, so maybe this should be less than up acceleration)
 player_turnfriction#		= 0.18			; The friction applied when turning
 player_turnspeed#			= 0				; Current Speed in turning
 player_turnangle#			= 0				; Current angle of ship
 player_turnmax#			= 3.0				; Max Turnspeed
 player_turnacceleration#	= 0.26			; Acceleration when turning
 player_realspeed#			= 0				; True AirSpeed (used by routine)
 player_realangle#			= 0				; True AirAngle (used by routine)

Case 4
 aeroplane$ ="Little fast spaceship"
 elas_move#			=.01		;How fast the screen will move towards the ship, defines the elasticity, together with:
 elas_bounce#		=1.06		;The Elasticity of the screen, in combination with elas_move		
 elas_dx#			=0			;Current ScreenSpeed in x Direction (used by Routine)
 elas_dy#			=0			;Current ScreenSpeed in Y Direction (used by Routine)
 elas_fixoffsetx#	=0			;Fixed x-Offset of the ship (from the Center of screen)
 elas_fixoffsety#	=0			;Fixed y-Offset of the ship (from the Center of screen)
 elas_runaheadx#	=0			;This is multiplied with the speed of the ship to determine the number of pixels that the screen heads in front of the ship when it is moving
 elas_runaheady#	=0			;This is multiplied with the speed of the ship to determine the number of pixels that the screen heads in front of the ship when it is moving
 elas_posx#			=1000		;Current Position of the Screenmiddle (used by routine)
 elas_posy#			=1000		;Current Position of the Screenmiddle (used by routine)

 player_x#					= screenw/2		; X position
 player_y#					= screenh/2		; Y position
 player_dx#					= 0				; X speed
 player_dy#					= 0				; Y speed
 player_frame				= 0				; Current Frame (= Direction)
 player_gravity#			= 0.0			; Gravity applied 
 player_maxspeed#			= 15			; Max Speed
 player_friction#			= 0.08			; Friction applied to speed
 player_xacceleration#		= 0.5			; How fast it boosts in the x direction (mixed with Sin/Cos though)
 player_yaccelerationu#		= 0.5			; Upwards boosting (again, with Sin and Cos applied)
 player_yaccelerationd#		= 0.5			; Downward boosting (Gravity also pulls down, so maybe this should be less than up acceleration)
 player_turnfriction#		= 0.5			; The friction applied when turning
 player_turnspeed#			= 0				; Current Speed in turning
 player_turnangle#			= 0				; Current angle of ship
 player_turnmax#			= 5				; Max Turnspeed
 player_turnacceleration#	= 1.5			; Acceleration when turning
 player_realspeed#			= 0				; True AirSpeed (used by routine)
 player_realangle#			= 0				; True AirAngle (used by routine)

Case 5
 aeroplane$ ="Little fast spaceship with gravity"
 elas_move#			=.02		;How fast the screen will move towards the ship, defines the elasticity, together with:
 elas_bounce#		=1.08		;The Elasticity of the screen, in combination with elas_move		
 elas_dx#			=0			;Current ScreenSpeed in x Direction (used by Routine)
 elas_dy#			=0			;Current ScreenSpeed in Y Direction (used by Routine)
 elas_fixoffsetx#	=0			;Fixed x-Offset of the ship (from the Center of screen)
 elas_fixoffsety#	=0			;Fixed y-Offset of the ship (from the Center of screen)
 elas_runaheadx#	=0			;This is multiplied with the speed of the ship to determine the number of pixels that the screen heads in front of the ship when it is moving
 elas_runaheady#	=0			;This is multiplied with the speed of the ship to determine the number of pixels that the screen heads in front of the ship when it is moving
 elas_posx#			=1000		;Current Position of the Screenmiddle (used by routine)
 elas_posy#			=1000		;Current Position of the Screenmiddle (used by routine)

 player_x#					= screenw/2		; X position
 player_y#					= screenh/2		; Y position
 player_dx#					= 0				; X speed
 player_dy#					= 0				; Y speed
 player_frame				= 0				; Current Frame (= Direction)
 player_gravity#			= 0.2			; Gravity applied 
 player_maxspeed#			= 15			; Max Speed
 player_friction#			= 0.08			; Friction applied to speed
 player_xacceleration#		= 0.5			; How fast it boosts in the x direction (mixed with Sin/Cos though)
 player_yaccelerationu#		= 0.5			; Upwards boosting (again, with Sin and Cos applied)
 player_yaccelerationd#		= 0.3			; Downward boosting (Gravity also pulls down, so maybe this should be less than up acceleration)
 player_turnfriction#		= 0.5			; The friction applied when turning
 player_turnspeed#			= 0				; Current Speed in turning
 player_turnangle#			= 0				; Current angle of ship
 player_turnmax#			= 5				; Max Turnspeed
 player_turnacceleration#	= 1.5			; Acceleration when turning
 player_realspeed#			= 0				; True AirSpeed (used by routine)
 player_realangle#			= 0				; True AirAngle (used by routine)

Case 6
 aeroplane$ ="Car"
 elas_move#			=.005		;How fast the screen will move towards the ship, defines the elasticity, together with:
 elas_bounce#		=1.03		;The Elasticity of the screen, in combination with elas_move		
 elas_dx#			=0			;Current ScreenSpeed in x Direction (used by Routine)
 elas_dy#			=0			;Current ScreenSpeed in Y Direction (used by Routine)
 elas_fixoffsetx#	=0			;Fixed x-Offset of the ship (from the Center of screen)
 elas_fixoffsety#	=0			;Fixed y-Offset of the ship (from the Center of screen)
 elas_runaheadx#	=5			;This is multiplied with the speed of the ship to determine the number of pixels that the screen heads in front of the ship when it is moving
 elas_runaheady#	=4			;This is multiplied with the speed of the ship to determine the number of pixels that the screen heads in front of the ship when it is moving
 elas_posx#			=1000		;Current Position of the Screenmiddle (used by routine)
 elas_posy#			=1000		;Current Position of the Screenmiddle (used by routine)

 player_x#					= screenw/2		; X position
 player_y#					= screenh/2		; Y position
 player_dx#					= 0				; X speed
 player_dy#					= 0				; Y speed
 player_frame				= 0				; Current Frame (= Direction)
 player_gravity#			= 0.0			; Gravity applied 
 player_maxspeed#			= 5				; Max Speed
 player_friction#			= 0.16			; Friction applied to speed
 player_xacceleration#		= 0.3			; How fast it boosts in the x direction (mixed with Sin/Cos though)
 player_yaccelerationu#		= 0.3			; Upwards boosting (again, with Sin and Cos applied)
 player_yaccelerationd#		= 0.3			; Downward boosting (Gravity also pulls down, so maybe this should be less than up acceleration)
 player_turnfriction#		= 0.18			; The friction applied when turning
 player_turnspeed#			= 0				; Current Speed in turning
 player_turnangle#			= 0				; Current angle of ship
 player_turnmax#			= 3.0				; Max Turnspeed
 player_turnacceleration#	= 0.3			; Acceleration when turning
 player_realspeed#			= 0				; True AirSpeed (used by routine)
 player_realangle#			= 0				; True AirAngle (used by routine)

End Select 

; Give the player ship a random position, to let the screen slide in place....
player_x# = Rnd(1000)+500
player_y# = Rnd(1000)+500

End Function