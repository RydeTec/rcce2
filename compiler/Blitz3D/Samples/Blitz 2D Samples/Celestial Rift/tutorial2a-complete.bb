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
Const INCR_ROTATE# = 5				;.. but DON'T touch this, otherwise the game will crash (I only drew the animation frames for 5 degree intervals!)
Const INCR_SPEED# = 0.5
Const INCR_SLOW# = 0.125
Const SPEED_MAX = 25
Const SPEED_MIN = -5

;set up changable variables for game/menu (with initial values, if you like - i.e.: you could just as soon as set them later!)
Global FLAG_GAMEON = 1
Global FLAG_PAUSE
Global FLAG_SAVESCREEN = 0
Global FLAG_DEBUG = 0
Global FLAG_GAMESTARTER
Global PLAYER_SHIELD#
Global PLAYER_SPEED#
Global PLAYER_ANGLE#
Global PLAYER_X#
Global PLAYER_Y#

Global timer
Global frames
Global starson
Global game_pause_frame
Global game_accept_pause
Global game_pause_stat
;a "#" symbol after the variable name means it can hold a FLOATING POINT number, i.e.: 190.1234
;without the "#" symbol, the variable is, by default, an INTEGER (Whole) number, i.e.: 190
;use the symbols when you are *sure* that you want it to hold specific types of data:
;# - floating point
;% - integer (whole number)
;$ - string (text, i.e.: "MYKE 12345"
Global tempstr$

timer = CreateTimer(50) ;create a timer set at 50ms (game speed) - play with this to see how you can increase or decrease the speed of the game.
						;this should be set at a speed which will look near enough the same on *every* PC it will be played on.
						;My PC (a 733MHz PIII with an nVidia GeForce card) will handle upwards of 150 frames per second, quite happily
						;but 'lesser' machines will not. 50, therefore, is quite sensible For a game of this nature, who's minimum system spec
						;will be something like a PII 300MHz machine (i.e.: Blitz Basic's minimum spec!)

;NOTE: there's no need to organise your variable declarations, as I have here, into sections. They can appear in any order you like, before the main program begins.
;I just do this, 'cos it looks right professional! :))))

;picture/animation (and related) variables
Dim game_stars(5)			;these 3 are ARRAYS. An array is automatically Global, but requires the Keyword (Yellow bit) DIM instead. This means "Dimension".
Global game_player
Global game_player_frame
Global game_gameover
Global game_paused

;Types are like Structures in C. You have a "Type" called whatever. Then you can make multiple versions of the type. Each version of the Type has the same properties, i.e.:
;a FISH (the Type) has EYES, MOUTH, SCALES And FINS (it's properties) - ALL FISH have these properties.
;a DOG (the Type) has EYES, MOUTH, FUR and TAIL (it's properties) - ALL DOGS have these properties (look like this.)
Type stars						;create "Type" for parallex stars
	Field depth,x#,y#			;each star has a depth, x and y position
End Type

menustars = SCREEN_HEIGHT/3 ;generate a number of stars, so that they look dense enough on all test resolutions
starson = 1 ;tells the program to show the stars (see later)
For i=0 To menustars					;create <menustars> number of stars in the STARS type
	star.stars=New stars				;add a new star for each increment of i
Next

;this code loads the image numbers into an array called "game_ stars", which we DIMmed earlier. It has 6 containers (0,1,2,3,4,5) but I'm only using 1 to 5!
;an "image number" is what Blitz uses to reference graphics held in the Video Memory, i.e.:
;1. Image number 12 is a picture of a flower.
;2. Make a variable called "flower_pic" = 12
;3. Wherever Blitz is told to draw "flower_pic", reference image number 12 in the Video Memory.
game_stars(1) = LoadImage("GfxRes/backg-star-1.bmp")	;container (1) in "game_stars" holds the image number for this picture ("GfxRes/backg-star-1.bmp")
game_stars(2) = LoadImage("GfxRes/backg-star-2.bmp")	;etc..
game_stars(3) = LoadImage("GfxRes/backg-star-3.bmp")
game_stars(4) = LoadImage("GfxRes/backg-star-4.bmp")
game_stars(5) = LoadImage("GfxRes/backg-star-5.bmp")
For i = 1 To 5
	MaskImage game_stars(i),255,0,255	;mask the images for each star, so that MAGENTA (255,0,255) is the transparent colour
Next

;notice in the following code, we're loading (and MASKING) graphics in exactly the same way as before, but into regular variables instead of arrays.
;this next bit is all animations
game_player = LoadAnimImage("GfxRes/player-ship.bmp",80,80,0,72) ;load in the 'sprite' for the player ship
MaskImage game_player,255,0,255 ;mask the images for the player ship, so that MAGENTA (255,0,255) is the transparent colour
;these are all plain single-frame pictures
;game piccies
game_gameover = LoadImage("GfxRes/game-gameover.bmp")		;the game over logo, which I tend to see a lot of.. :(
game_paused = LoadImage("GfxRes/game-paused.bmp")			;the paused logo
MaskImage game_gameover,255,0,255
MaskImage game_paused,255,0,255

;Right! That's it, we've set up *everything* we're going to need from outside the program. Let's start up the game_loop function..
game_loop()

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
			FlushKeys				
			End					;if FLAG_GAMEON isn't 1, we End the program
		End If
	Forever
End Function

;As described a minute ago, this function sets up the game variables as they should be
;at the start of every game, i.e.: Player Shields are full, the score is 0 etc..
Function game_initialise()
	FLAG_DEBUG = 1					
	FLAG_GAMESTARTER = 1
	FLAG_PAUSE = 0
	PLAYER_ANGLE = 0
	PLAYER_SPEED = 0
	game_player_randomize()			;calls a function that randomizes a player's position on the map
	game_stars_randomize()			;calls a function that randomizes the star positions

End Function

;once again, like it's menu equivalent, "game_loop_update()" is a function which analyses player input, working out all the new
;coordinate positions and anything else which needs deciding before updating the screen display.
Function game_loop_update()
	frames = WaitTimer(timer)
	For i = 1 To frames
		If KeyDown(KEY_PAUSE)									;checks to see if the user has pressed the "PAUSE" key
			FlushKeys
			If game_accept_pause = 1							;the "game_accept_pause" variable is used in the same way as "menu_accept_quit" earlier.
																;because we're using SCANCODES, the program will register a number of qualifying cases where
																;when this check is performed, the key is still held down (at 50 frames per second, if the user
																;held down the key for half a second, the KEYDOWN function would fire around 25 times!)
																
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
					PLAYER_ANGLE = PLAYER_ANGLE + INCR_ROTATE			;Add "INCR_ROTATE" degrees to the current PLAYER_ANGLE
					FlushKeys
					If PLAYER_ANGLE >= 360 Then							;if the PLAYER_ANGLE is 360, then reset it to 0.
						PLAYER_ANGLE = PLAYER_ANGLE - 360
					End If
				End If
				If KeyDown(KEY_ANTICWISE)							;exactly the same, but Subtract "INCR_ROTATE" from the player angle and
					PLAYER_ANGLE = PLAYER_ANGLE - INCR_ROTATE		;reset it to (for example) 355, if the angle is -5.
					FlushKeys
					If PLAYER_ANGLE < 0 Then
						PLAYER_ANGLE = PLAYER_ANGLE + 360
					End If
				End If
				If KeyDown(KEY_SPEEDUP) Then						;if the "SPEEDUP" key is pressed.
					If PLAYER_SPEED < SPEED_MAX							;as long as the player speed is less than the maximum speed (SPEED_MAX), then
						PLAYER_SPEED = PLAYER_SPEED + INCR_SPEED		;Add "INCR_SPEED" to the player's speed value
						FlushKeys
					Else
						PLAYER_SPEED = PLAYER_SPEED - INCR_SLOW			;if the PLAYER_SPEED is not less than the maximum speed then take "INCR_SLOW" off of it!
						FlushKeys
					End If
				Else												;if the key isn't being pressed, then..
					If PLAYER_SPEED > 0 Then							;as long as the player speed is greater than 0, then take "INCR_SLOW" off the current value
						PLAYER_SPEED = PLAYER_SPEED - INCR_SLOW			;(this is an easy "No power" decelaration for the space ship)
					End If
					FlushKeys
				End If
				If KeyDown(KEY_SPEEDDOWN)							;exactly the same thing, but with the ship's thrusters in reverse! :)
					If PLAYER_SPEED > SPEED_MIN
						PLAYER_SPEED = PLAYER_SPEED - INCR_SPEED
						FlushKeys
					Else
						PLAYER_SPEED = SPEED_MIN
						FlushKeys
					End If
				Else
					If PLAYER_SPEED < 0 Then
						PLAYER_SPEED = PLAYER_SPEED + INCR_SLOW
					End If
				End If
				
				;this next peice of code updates the player position on the map, relative to it's Speed and Angle and it's last position
				PLAYER_X = PLAYER_X + (PLAYER_SPEED*(Sin(PLAYER_ANGLE)/2))
				PLAYER_Y = PLAYER_Y - (PLAYER_SPEED*(Cos(PLAYER_ANGLE)/2))
				If PLAYER_X < 0 Then						;if the PLAYER_X value is less than 0, then wrap your position around the map
					PLAYER_X = (PLAYER_X + GAME_AREA_X)		;by adding the GAME_AREA_X value to the negative value, i.e.: -5 becomes 19995 on a 20000 X pixel map.
				End If
				If PLAYER_X > GAME_AREA_X Then				;the same in reverse, i.e. 20004 becomes 4 on the same map.
					PLAYER_X = (PLAYER_X - GAME_AREA_X)
				End If
				If PLAYER_Y < 0 Then						;and now the same for the Y direction
					PLAYER_Y = (PLAYER_Y + GAME_AREA_Y)
				End If
				If PLAYER_Y > GAME_AREA_Y Then
					PLAYER_Y = (PLAYER_Y - GAME_AREA_Y)
				End If
			

			;the parralex stars move relative to the player and is just the same "menustars" number of stars
			;scrolled at various speeds, wrapping around the screen.
			;it's a fairly cheap, but effective way of creating a nice illusion of speed!
			If starson=1 Then				;if, at the beginning of the program, you set "starson" to 0, the stars will disappear.
											;the game would also feel pretty bloody wierd.. hold on.. yep.. Absolutely mad! Try it! :)
				For star.stars=Each stars
					;the following two lines move each version of the "stars" Type an x and y distance relative to the player's speed and angle
					;with a devision relative to the depth of the star to make the smallest stars move slower than the biggest
					;thus we have our parallex effect.
					
					;it might be of interest to know that these two lines were based on the OLDSKOOL demo which comes with Blitz Basic
					;and was the starting point for the whole CELESTIAL RIFT game concept! Thanks a lot, Mr Mikkel Løkke!! :)
					star\y=(star\y+PLAYER_SPEED*(Cos(360-PLAYER_ANGLE)/(6-star\depth+1)))
					star\x=(star\x+PLAYER_SPEED*(Sin(360-PLAYER_ANGLE)/(6-star\depth+1)))
					;the maximum pixel width of for the biggest star image is 5 pixels
					;the following IF statements wrap the stars around the screen border when they reach the extremities
					If star\x < -5 Then
						star\x = star\x + (SCREEN_WIDTH + 5)
					End If
					If star\x > SCREEN_WIDTH Then
						star\x = star\x - (SCREEN_WIDTH + 5)
					End If
					If star\y <= -5 Then
						star\y = star\y + (SCREEN_HEIGHT + 5)
					End If
					If star\y >= SCREEN_HEIGHT
						star\y = star\y - (SCREEN_HEIGHT + 5)
					End If
				Next
			End If	;end of the "are the stars going to be shown" IF statement

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
	If starson=1 Then		;draw all of the stars using their correct pictures, at the correct x and y positions
		For star.stars=Each stars ;(for each star in type 'stars' do the following..)
			DrawImage game_stars(star\depth),star\x,star\y	;using the depth property of "stars" as the Array position
		Next
	End If
	
	game_player_frame = PLAYER_ANGLE/5
	If game_player_frame = 72 Then
		game_player_frame = 0
	End If
	
	DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2,(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2,game_player_frame
	
	Color 255,0,255
	
	;if the game is paused (FLAG_PAUSE = 1) then draw the image when "game_pause_stat" = 1
	;this gives us our flash on-off effect as used earlier in "menu_draw_update()"
	
	If FLAG_PAUSE = 1 And game_pause_stat = 1 Then
		DrawImage game_paused,SCREEN_WIDTH/2-ImageWidth(game_paused)/2,SCREEN_HEIGHT-((SCREEN_HEIGHT-350)/2+ImageHeight(game_paused)/2)
	End If
	
	;the following text will only be printed on the screen while the "FLAG_DEBUG" variable is set to 1 (which for the purpose of this tutorial, we've left it at!)
	If FLAG_DEBUG = 1 Then
		;the TEXT command writes a STRING of text onto the screen at the given coordinates in the currently set font
		;as I haven't used the LoadFont or SetFont commands, this will just be Blitz' default font.

		Color 255,255,255									;just in case, set the colo(u)r of the text to WHITE (255, 255, 255)
		Text 0,0,"PLAYER ANGLE = " + PLAYER_ANGLE				;"Write the string 'PLAYER ANGLE = ' followed by the number held in PLAYER_ANGLE"
		Text 0,20,"PLAYER SPEED = " + PLAYER_SPEED
		Text 0,40,PLAYER_ANGLE + " / 5 = " + PLAYER_ANGLE/5
		Text 0,60,"SHIPFRAME = " + game_player_frame
		Text 0,80,"CO-ORDS = " + Int(PLAYER_X)
		Text 140,80," , " 
		Text 160,80,Int(PLAYER_Y)
	End If
	
	;just like in "menu_draw_update()", the user can press a key which just makes the value of "FLAG_SCREENSAVE" equal 1.
	;when the code gets to here, it saves out the named Buffer as a BMP picture.
	If FLAG_SAVESCREEN=1 Then
		SaveBuffer (BackBuffer(),"CRGameScreen.bmp")
		FLAG_SAVESCREEN = 0
	End If 
	
	Flip					;as before in "menu_draw_update()", FLIP everything we've just drawn on the backbuffer and show it on the frontbuffer, i.e.: your monitor!!
End Function

;a little function which randomizes the x and y coordinates of each version of the "stars" Type, plus it's "depth" property
Function game_stars_randomize()
	For star.stars=Each stars
		star\x=Rnd(-5,SCREEN_WIDTH)			;create random x and y positions for all the stars
		star\y=Rnd(-5,SCREEN_HEIGHT)
		star\depth=Rnd(1,5)					;the depth of the stars results in our sexy parallex effect
	Next
End Function

;an even littler function which just gives us random x and y coordinates for the player.
Function game_player_randomize()
	PLAYER_X = Rand(0,GAME_AREA_X)
	PLAYER_Y = Rand(0,GAME_AREA_Y)
End Function