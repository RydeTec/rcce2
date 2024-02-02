AppTitle "Celestial Rift" ;what this program will be called in MICROSOFT WINDOWS.

;This makes two CONSTants. These are values which will NOT change at any point in the program, so we set their values now.
Const SCREEN_WIDTH = 800
Const SCREEN_HEIGHT = 600
;This is the code which tells Blitz what Display Resolution to use on the Graphics Card. The "GRAPHICS" command should always be placed before you do
;*anything* image-related in your program
Graphics SCREEN_WIDTH,SCREEN_HEIGHT,0,1	;start the graphics mode at SCREEN_WIDTH by SCREEN_HEIGHT, let Blitz choose the depth (,0) and run full screen (,1)

;more program CONSTants..
Const GAME_AREA_X = 50000	;these two set the size of the map. You *should* keep them the same, 'cos the radar is square
Const GAME_AREA_Y = 50000	;but technically, you can change the values to anything you like! ;)
;the following constants are keyboard "SCAN" codes. Every key on the keyboard has a number. You can get the full list in your Blitz manual.
Const KEY_CLOCKWISE = 25	;(p)	
Const KEY_ANTICWISE = 24	;(o)
Const KEY_SPEEDUP = 16		;(q)
Const KEY_SPEEDDOWN = 30	;(a)
Const KEY_FIRE = 57			;(Space)
Const KEY_QUIT = 1			;(Escape)
Const KEY_PAUSE = 7			;(Number 6 on the main keyboard)
Const KEY_DEBUG = 59		;(F1)
Const KEY_SAVESCREEN = 88	;(F12)
;the following constants affect the way the game plays. Feel free to mess with the values..
Const ENEMY_MAX_DIST = 5000
Const ENEMY_MIN_DIST = 50
Const INCR_ROTATE# = 5				;.. but DON'T touch this, otherwise the game will crash (I only drew the animation frames for 5 degree intervals!)
Const INCR_SPEED# = 0.5
Const INCR_SLOW# = 0.125

;set up changable variables for game/menu (with initial values, if you like - i.e.: you could just as soon as set them later!)
Global FLAG_GAMEON
Global FLAG_SAVESCREEN = 0
Global FLAG_DEBUG = 1
Global FLAG_GAMESTARTER

Global FLAG_PAUSE = 0
Global PLAYER_X#
Global PLAYER_Y#
Global MOVE_SPEED = 100

Global timer
Global frames
Global game_pause_frame
Global game_accept_pause
Global game_pause_stat
Global gameovercounter
Global gameoverstat
Global whichway
Global pausecount	
Global tempstr$

timer = CreateTimer(50) ;create a timer set at 50ms (game speed) - play with this to see how you can increase or decrease the speed of the game.
						;this should be set at a speed which will look near enough the same on *every* PC it will be played on.
						;My PC (a 733MHz PIII with an nVidia GeForce card) will handle upwards of 150 frames per second, quite happily
						;but 'lesser' machines will not. 50, therefore, is quite sensible For a game of this nature, who's minimum system spec
						;will be something like a PII 300MHz machine (i.e.: Blitz Basic's minimum spec!)

;NOTE: there's no need to organise your variable declarations, as I have here, into sections. They can appear in any order you like, before the main program begins.
;I just do this, 'cos it looks right professional! :))))

;picture/animation (and related) variables
Global game_background
Global game_player
Global game_enemy1

;Types are like Structures in C. You have a "Type" called whatever. Then you can make multiple versions of the type. Each version of the Type has the same properties, i.e.:
;a FISH (the Type) has EYES, MOUTH, SCALES And FINS (it's properties) - ALL FISH have these properties.
;a DOG (the Type) has EYES, MOUTH, FUR and TAIL (it's properties) - ALL DOGS have these properties (look like this.)

Type enemies					;same for enemies
	Field x#,y#,energy,angle,speed#,style,maxspeed,dest_angle,dist_travelled,dist_to_go,dest_opp#,dest_hyp#,dest_adj#,rotate_angle,status,bulletlimiter,exp_frame#,gen
End Type
;notice the "enemies" Type has a "style"?
;originally, I planned to have different styles of enemy space-ships, but never got around to drawing the graphics!
;the "style" is used in the subsequent code, and the Type property remains, so *technically* adding new enemy styles to the game should be pretty easy! :)
For i = 1 To 10
	enemy.enemies = New enemies ;make 10 enemies (we'll set their variables later)
Next


game_player = LoadImage("AIGfxRes/player.bmp") ;load in the 'sprite' for the player ship
MaskImage game_player,255,0,255 ;mask the images for the player ship, so that MAGENTA (255,0,255) is the transparent colour
game_enemy1 = LoadAnimImage("AIGfxRes/enemy1-ship.bmp",16,16,0,72) ;load in the 'sprite' for the enemy ship (type 1)
MaskImage game_enemy1,255,0,255 ;mask the images for the enemy ship (type 1), so that MAGENTA (255,0,255) is the transparent colour
;game piccies
game_background = LoadImage("AIGfxRes/game-bg.bmp") 			;the sexy blue background behind the stars
MaskImage game_background,255,0,255

;Right! That's it, we've set up *everything* we're going to need from outside the program.
game_loop() ;start the proper program loop by 'calling' the function called "menu_loop()" - which, conveniently, is just coming up!!!


;this function keeps the game loop going.. It starts playing tunes and sets a couple of variables. 
;Then it goes into a never ending loop which carries out a sequence of checks and function calls, until such time as we want to stop it!
Function game_loop()
	FLAG_GAMEON = 1 ;tells the program that the game is running.
	game_initialise() 	;calls a function called "game_initialise()" (which is next in the code) which
						;just gives the player full energy and a score of 0 etc..)

	Repeat		;as with the menu, we want to cycle though the process of updating/drawing forever, until such time as the game has ended.
		If FLAG_GAMEON = 1 Then		;.. so, if FLAG_GAMEON is 1, then "do" the following.
			game_loop_update()								;call the game_loop_update() function
		Else
			FlushKeys				;if FLAG_GAMEON isn't 1, then END the program (as we did from the menu)
			End
		End If
	Forever
End Function

;As described a minute ago, this function sets up the game variables as they should be
;at the start of every game, i.e.: Player Shields are full, the score is 0 etc..
Function game_initialise()
	game_player_randomize()
	gameovercounter = 0
	;this next bit is the first time you'll have seen a TYPE cycle.
	;Just like a normal "For i = 0 to 20" loop, this goes through each Version of a type
	;and set's it's properties. (i.e.: That FISH type has a SCALES property. Here we tell it that this particular Fish's Scales are GOLD!)
	For enemy.enemies=Each enemies
		enemy\x=Rnd(0,GAME_AREA_X)		;randomizes each "enemies"'s position on the map!
		enemy\y=Rnd(0,GAME_AREA_Y)
		enemy\energy = 100					;gives each "enemies" a full shield strength of 100
		enemy\angle = Int(Rnd(0,72)) * 5	;gives each "enemies" a random angle between 0 and 360, at increments of 5 degrees
		enemy\maxspeed = Rnd(20,40)			;gives each "enemies" a random maximum speed between 20 and 40 (a bit slower, and a bit faster than the player's
											;maximum speed (as originally coded by me. If you change the player's maximum speed, it will have no effect on 'them'!)
		enemy\speed = 0
		enemy\style = 1						;sets the "enemies"'s style to 1. Remember before when I said it would be easily possible to implement new styles of enemy?
											;well, this would be your starting point! :)
											
		enemy\exp_frame = 0					;like the player, an "enemies" has an explosion frame which is always zero(0) until it is ready to explode!
											;see later for the code which fire's an "enemies"'s final demise.

		enemy\dist_to_go = Rnd(ENEMY_MIN_DIST,ENEMY_MAX_DIST)	;The "enemies"'s distance to go defines how far it will fly on it's current course, before it
																;retargets and turns towards you. This is a fairly simple, but reasonably effective enemy A.I.
		enemy\dist_travelled = 0			;directly related to the "enemies"'s distance to go, is how far it has travelled up to now.

		enemy\gen = 1			;a last minute addition, when an enemy dies and regenerates, it is worth more points to kill next time around, thanks to its GEN number.
	Next
End Function

;once again, like it's menu equivalent, "game_loop_update()" is a function which analyses player input, working out all the new
;coordinate positions and anything else which needs deciding before updating the screen display.
Function game_loop_update()
	frames = WaitTimer(timer)
	For i = 1 To frames
		If KeyDown(KEY_PAUSE)									;checks to see if the user has pressed the "PAUSE" key
			FlushKeys
			If game_accept_pause = 1 And gameovercounter = 0	;the "game_accept_pause" variable is used in the same way as "menu_accept_quit" earlier.
																;because we're using SCANCODES, the program will register a number of qualifying cases where
																;when this check is performed, the key is still held down (at 50 frames per second, if the user
																;held down the key for half a second, the KEYDOWN function would fire around 25 times!)
																;A check is made on the value of "game_over_counter" to see if the player has died - in which
																;case Pausing the game is not allowed.
																
				If FLAG_PAUSE = 0 Then							;If FLAG_PAUSE is 0 then..
					FLAG_PAUSE = 1								;make FLAG_PAUSE = 1
					game_accept_pause = 0						;don't accept any more "PAUSE" button presses until "game_accept_pause" is set back to 1
					game_pause_frame = 1						;make "game_pause_frame" = 1 which, similarly to "menu_frame" earlier will enable the flashing of "PAUSED".
				Else
					FLAG_PAUSE = 0								;If FLAG_PAUSE isn't 0 (the game was paused) then..
					game_accept_pause = 0						;do exactly the same, but set FLAG_PAUSE to 0.
					game_pause_frame = 1
				End If
			End If
		End If
		If KeyDown(KEY_DEBUG)									;checks to see if the user has pressed the "DEBUG" key
			FlushKeys
			If game_accept_pause = 1 And gameovercounter = 0	;same as pause																

				If FLAG_DEBUG = 0 Then							;If FLAG_DEBUG is 0 then..
					FLAG_DEBUG = 1								;make FLAG_DEBUG = 1
					game_accept_pause = 0						;don't accept any more "PAUSE" button presses until "game_accept_pause" is set back to 1
					game_pause_frame = 1						;make "game_pause_frame" = 1 which, similarly to "menu_frame" earlier will enable the flashing of "PAUSED".
				Else
					FLAG_DEBUG = 0								;If FLAG_DEBUG isn't 0 (the game was in debug mode) then..
					game_accept_pause = 0						;do exactly the same, but set FLAG_DEBUG to 0.
					game_pause_frame = 1
				End If
			End If
		End If


		If KeyDown(KEY_FIRE)									;checks to see if the user has pressed the "FIRE" key
			FlushKeys
			If game_accept_pause = 1 And gameovercounter = 0	;re-randomises all the screen (enemy/player) positions..
				game_initialise()
				FlushKeys
				game_accept_pause = 0
				game_pause_frame = 1
			End If
		End If

		If KeyDown(KEY_SAVESCREEN)		;If the "SCREENSAVE" button is pressed..
			FlushKeys					;this functions exactly the same as in menu_loop_update()
			FLAG_SAVESCREEN = 1
		End If
		If KeyDown(KEY_QUIT)		;If the "QUIT" button is pressed..
			FlushKeys
			FLAG_GAMEON=0 			;set FLAG_GAMEON to equal 0. Back in the "game_loop()" function, this will cause the code to jump back to the menu.
		End If

		If FLAG_PAUSE = 0 Then							;this is really easy! If the game is paused then *don't* do any of the following code. No values will change,
														;hence nothing will move on the screen when it comes to updating it later! :)
																					
			If KeyDown(KEY_CLOCKWISE)							;if "CLOCKWISE" key is pressed then..
				PLAYER_X = PLAYER_X + MOVE_SPEED				;Move "MOVE_SPEED" to the right of the current PLAYER_X position
				If PLAYER_X > GAME_AREA_X Then
					PLAYER_X = PLAYER_X - GAME_AREA_X
				End If
				FlushKeys
			End If
			If KeyDown(KEY_ANTICWISE)							
				PLAYER_X = PLAYER_X - MOVE_SPEED				;Move "MOVE_SPEED" to the left of the current PLAYER_X position
				If PLAYER_X < 0 Then
					PLAYER_X = PLAYER_X + GAME_AREA_X
				End If
				FlushKeys
			End If
			If KeyDown(KEY_SPEEDUP) Then						;if the "SPEEDUP" key is pressed.
				PLAYER_Y = PLAYER_Y - MOVE_SPEED
				If PLAYER_Y < 0 Then
					PLAYER_Y = PLAYER_Y + GAME_AREA_Y
				End If
				FlushKeys
			End If
			If KeyDown(KEY_SPEEDDOWN) Then						;exactly the same thing, but with the ship's thrusters in reverse! :)
				PLAYER_Y = PLAYER_Y + MOVE_SPEED
				If PLAYER_Y > GAME_AREA_Y Then
					PLAYER_Y = PLAYER_Y - GAME_AREA_Y
				End If
				FlushKeys
			End If
			
				
			
			;the following code section is probably the most complex in the game, because it's the Enemy Artificial Intelligence
			;Reader's of a nervous disposition might want to skip this - I know I did! :)))
			For enemy.enemies=Each enemies		;for each version of the "enemies" type..


;NEW Enemy AI code
;NEW Enemy AI code
;NEW Enemy AI code
;NEW Enemy AI code
;NEW Enemy AI code
;NEW Enemy AI code - Start
				;this bit checks the distances to the left, right, top and bottom of the enemy, relative to the player, to decide which way to fly would be the best
				;intercept route. I tell you, these are vicious dedicated little oiks! :)
				temp_x1# = PLAYER_X - enemy\x 		;x distance to player on the right
				temp_y1# = PLAYER_Y - enemy\y		;y distance to player on the top
				temp_x2# = enemy\x - PLAYER_X 		;x distance to player on the left
				temp_y2# = enemy\y - PLAYER_Y		;y distance to player on the bottom
				If temp_x1# < 0 Then					;if the values are bigger than the game area, or less than 0, then wrap them
					temp_x1# = temp_x1# + GAME_AREA_X	;around the game map, like with the player before.
				End If
				If temp_x1# > GAME_AREA_X Then
					temp_x1# = temp_x1# - GAME_AREA_X
				End If
				If temp_y1# < 0 Then
					temp_y1# = temp_y1# + GAME_AREA_Y
				End If
				If temp_x1# > GAME_AREA_X Then
					temp_y1# = temp_y1# - GAME_AREA_Y
				End If
				If temp_x2# < 0 Then
					temp_x2# = temp_x2# + GAME_AREA_X
				End If
				If temp_x2# > GAME_AREA_X Then
					temp_x2# = temp_x2# - GAME_AREA_X
				End If
				If temp_y2# < 0 Then
					temp_y2# = temp_y2# + GAME_AREA_Y
				End If
				If temp_x2# > GAME_AREA_X Then
					temp_y2# = temp_y2# - GAME_AREA_Y
				End If

				;this bit compares the two x directions to see which one is smaller			
				If Abs(temp_x1#) < Abs(temp_x2#) Then	;the ABS function always gives you the "+VE" version of a number, so -1 comes out as 1 and 2 comes out as 2
					temp_x# = Abs(temp_x1#)		;if "Temp_x1" is the smallest, make this the "best" x distance 
					xdir = -1
				Else
					temp_x# = Abs(temp_x2#)		;otherwise if "Temp_x2" is the smallest, make this the "best" x distance
					xdir = 1
				End If
				If Abs(temp_y1#) < Abs(temp_y2#) Then	;same with the y directions
					temp_y# = Abs(temp_y1#)
					ydir = -1
				Else
					temp_y# = Abs(temp_y2#)
					ydir = 1
				End If
				
				;because we used the ABS function, we've lost the original direction of the value's units, so using the xdir and ydir
				;values we just set over there, we can rebuild that directional picture
				If xdir = 1 And ydir = 1 Then
					sector = 4						;top left
				ElseIf xdir = 1 And ydir = -1 Then
					sector = 3						;bottom left
				ElseIf xdir = -1 And ydir = -1 Then
					sector = 2						;bottom right
				ElseIf xdir = -1 And ydir = 1 Then
					sector = 1						;top right
				End If
				
				;finally, we're going to use our knowledge of the shortest distance between this "enemies" and the player
				;using PYTHAGORAS theorum to get the angle between them.

				temp_x# = Abs(temp_x# / 200)		;this bit just makes the number's smaller and more manageable
				temp_y# = Abs(temp_y# / 200)
				temp_hyp# = (temp_x#*temp_x#) + (temp_y#*temp_y#)	;PYTHAGORAS would be proud
				temp_hyp# = Sqr(temp_hyp#)							;.. oh and Mrs Pennington, my Maths teacher! :)
				enemy\dest_adj = temp_x#		;these Type properties aren't used anymore, except in the DEBUG version
				enemy\dest_opp = temp_y#
				enemy\dest_hyp = temp_hyp#
				enemy\dest_angle = ASin(temp_y#/temp_hyp#)	;finally, back to Trigonometry, the angle is revealed.
				
				;now, once again, because we lost the directions of the original value's units, the angle we've just got will only be between 0 and 90 degrees
				;consequently, we've got to decide how to get 91 to 359 degrees back, which we can do with our "sector" values
				Select sector
					Case 1
						enemy\dest_angle = 90-enemy\dest_angle	;between 0 and 90
					Case 2
						enemy\dest_angle = 90+enemy\dest_angle	;between 90 and 180
					Case 3
						enemy\dest_angle = 270-enemy\dest_angle	;between 180 and 270
					Case 4
						enemy\dest_angle = 270+enemy\dest_angle	;between 270 and 360
				End Select
				
				
				;this bit works out what the destination angle given before looks like in increments of 5 degrees.
				;the angle the enemy *wants* to face is, for example, 212 degrees. The closest angle I drew in Paint Shop Pro is 210 degrees
				If enemy\dest_angle >= 360 Then
					enemy\dest_angle = enemy\dest_angle - 360
				ElseIf enemy\dest_angle < 0 Then
					enemy\dest_angle = enemy\dest_angle + 360
				End If
				;this next line says does the 212 to 210 conversion, i.e.: 
				;212 / 5 = 42.4
				;42.4 converted to an INTeger is 42
				;5 * 42 = 210
				enemy\dest_angle = 5 * Int(enemy\dest_angle / 5)
	
				;this next section works out the quickest rotation that the "enemies" ship can do to face the direction it wants to be
				;i.e.: if it's currently at 20 degrees, and wants to be facing 45 degrees, then he's got 25 degrees to go to the right, or 335 degrees to the left.
				;guess which one would be more sensible in a life-or-death situation?
				If enemy\angle > enemy\dest_angle Then
					enemy\rotate_angle = enemy\angle - enemy\dest_angle
				ElseIf enemy\angle < enemy\dest_angle
					enemy\rotate_angle = 0 - (enemy\dest_angle - enemy\angle)
				Else
					enemy\rotate_angle = 0
				End If
				
				If enemy\rotate_angle > 180 Then
					enemy\rotate_angle = 0-(360-enemy\rotate_angle)
				End If
				If enemy\rotate_angle < -180 Then
					enemy\rotate_angle = (360+enemy\rotate_angle)
				End If

				;based on the decision made a second ago, about which was the quickest way to rotate to face you fastest..
				If enemy\rotate_angle < 0 Then
					If Not enemy\angle = enemy\dest_angle Then
						enemy\angle = enemy\angle + 5				;rotate the "enemies" right until its angle is correct
						If enemy\angle >=360 Then
							enemy\angle = 360-enemy\angle			;flip the angle back to 0 when it gets to 360
						End If
					End If
				Else
					If Not enemy\angle = enemy\dest_angle Then
						enemy\angle = enemy\angle - 5				;exactly the same, but rotating left
						If enemy\angle < 0 Then
							enemy\angle = enemy\angle+360
						End If
					End If
				End If
;NEW Enemy AI code
;NEW Enemy AI code
;NEW Enemy AI code
;NEW Enemy AI code
;NEW Enemy AI code
;NEW Enemy AI code - End

			Next	;end of the ENEMIES for loop
			
			
		End If ;end of "IF FLAG_PAUSE = 0" IF Statement
		
		;this flashes the "PAUSED" caption on and off when needs be!
		game_pause_frame = game_pause_frame + 1
		If game_pause_frame = 25 Then
			game_pause_frame = 1
			game_accept_pause = 1
			If game_pause_stat = 1 Then
				game_pause_stat = 0
			Else
				game_pause_stat = 1
			End If
		End If

	Next	;end of the "for i = 1 to frames" FOR loop
	
	game_draw_update()	;finally, draw all the pictures on the screen, based on their (possibly) new positions.
End Function

;this function draws the game graphics in their freshly calculated positions
Function game_draw_update()
	SetBuffer BackBuffer()	;draw all of the following to the backbuffer
	ClsColor 0,0,0			;changes the CLearScreen colour to black (0,0,0)
	Cls
	TileBlock game_background,0,0	;This acts as a CLear Screen, even if the Resolution is set bigger than "game_background"'s image size
	
		;similarly, this Type FOR loop goes through each enemy, drawing it at it's new position (relative to the player)
		;and checks for collisions with the player.
		For enemy.enemies=Each enemies
		
			;this bit confirms what angle to draw each "enemies" at (which animation frame)
			temp_enemy1_frame = enemy\angle/5
			If temp_enemy1_frame >= 72 Then
				temp_enemy1_frame = temp_enemy1_frame - 72
			End If
			If temp_enemy1_frame < 0 Then
				temp_enemy1_frame = temp_enemy1_frame + 72
			End If
			
			;as long as the enemy isn't dead (busy exploding), then draw the enemy at it's x,y position and
			;check for collisions with the player.
			
			;exactly the same as the icons, relative to the player position. The fact that an enemy moves it's x and y position is irrelevant, because
			;it's new position was already worked out in the last function ("game_loop_update()")
			DrawImage game_enemy1, (enemy\x/(GAME_AREA_X/SCREEN_WIDTH))-(ImageWidth(game_enemy1)/2), (enemy\y/(GAME_AREA_X/SCREEN_HEIGHT))-(ImageWidth(game_enemy1)/2),temp_enemy1_frame
			
		Next	;end of the "enemies" FOR loop

	DrawImage game_player,PLAYER_X/(GAME_AREA_X/SCREEN_WIDTH)-ImageWidth(game_player)/2,PLAYER_Y/(GAME_AREA_X/SCREEN_HEIGHT)-ImageHeight(game_player)/2


	;if the game is paused (FLAG_PAUSE = 1) then draw the image when "game_pause_stat" = 1
	;this gives us our flash on-off effect as used earlier in "menu_draw_update()"
	If FLAG_PAUSE = 1 And game_pause_stat = 1 Then
		Text 0,0,"PAUSED"
	End If
	
	i = 0
	;the following text will only be printed on the screen while the "FLAG_DEBUG" variable is set to 1 (F1, as I programmed it, on the main menu!)
	If FLAG_DEBUG = 1 Then
		;the TEXT command writes a STRING of text onto the screen at the given coordinates in the currently set font
		;as I haven't used the LoadFont or SetFont commands, this will just be Blitz' default font.

		Color 255,255,255									;just in case, set the colo(u)r of the text to WHITE (255, 255, 255)
		Text 0,20,"PLAYER CO-ORDS = " + Int(PLAYER_X)
		Text 180,20," , " 
		Text 200,20,Int(PLAYER_Y)
		Text 0,40, "whichway = " + whichway

		;write some of the information about each of our enemies at the bottom of the screen.
		;this was incredibly useful when working out the A.I. code, 'cos you could see in "words" what
		;each enemy was doing, even when it wasn't on the screen!
		For enemy.enemies=Each enemies
		;debug text for enemies
			Text 20,((20*i)+380),i+1						;number of ship (see Text command below)
			Text 40,((20*i)+380),enemy\angle				;current enemy angle
			Text 80,((20*i)+380),enemy\dest_angle			;angle required to be facing the player
			Text 110,((20*i)+380),"y="+enemy\dest_opp		;nearest y distance from player
			Text 210,((20*i)+380),"x="+enemy\dest_adj		;nearest x distance from player
			Text 310,((20*i)+380),"hyp="+enemy\dest_hyp		;nearest "as the crow files" distance from player
			Text 430,((20*i)+380),enemy\rotate_angle		;quickest degree rotation to be facing the player (+180 to -180 from current angle)
			Text 470,((20*i)+380),enemy\dist_to_go			;distance to travel until next player-position check
			Text 510,((20*i)+380),enemy\dist_travelled		;distance travelled so far (to get to above value)
			Text (enemy\x/(GAME_AREA_X/SCREEN_WIDTH))-(ImageWidth(game_enemy1)/2), (enemy\y/(GAME_AREA_X/SCREEN_HEIGHT))-(ImageWidth(game_enemy1)/2),i+1
			;puts the number of the ship next to the actual ship, for reference when looking at the numbers..
			i = i + 1
		Next
	End If
	
	;just like in "menu_draw_update()", the user can press a key which just makes the value of "FLAG_SCREENSAVE" equal 1.
	;when the code gets to here, it saves out the named Buffer as a BMP picture.
	If FLAG_SAVESCREEN=1 Then
		SaveBuffer (BackBuffer(),"CRGameScreen.bmp")
		FLAG_SAVESCREEN = 0
	End If 
	
	Flip					;as before in "menu_draw_update()", FLIP everything we've just drawn on the backbuffer and show it on the frontbuffer, i.e.: your monitor!!
End Function

;an even littler function which just gives us random x and y coordinates for the player.
Function game_player_randomize()
	PLAYER_X = Rand(0,GAME_AREA_X)
	PLAYER_Y = Rand(0,GAME_AREA_Y)
End Function