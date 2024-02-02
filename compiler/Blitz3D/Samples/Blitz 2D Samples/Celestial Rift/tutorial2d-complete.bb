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
Const KEY_HYPER = 2			;(1)
Const KEY_BOOST = 50		;(m)
Const KEY_CLOAK = 46		;(c)
Const KEY_QUIT = 1			;(Escape)
Const KEY_PAUSE = 7			;(Number 6 on the main keyboard)
Const KEY_DEBUG = 59		;(F1)
Const KEY_SAVESCREEN = 88	;(F12)
;the following constants affect the way the game plays. Feel free to mess with the values..
Const INCR_ROTATE# = 5				;.. but DON'T touch this, otherwise the game will crash (I only drew the animation frames for 5 degree intervals!)
Const INCR_SPEED# = 0.5
Const INCR_SLOW# = 0.125
Const INCR_BOOSTERS_UP# = 0.025
Const INCR_BOOSTERS_DOWN# = .75
Const INCR_CLOAK_UP#= 0.0125
Const INCR_CLOAK_DOWN# = .25 
Const SPEED_MAX = 25
Const SPEED_MIN = -5

;set up changable variables for game/menu (with initial values, if you like - i.e.: you could just as soon as set them later!)
Global FLAG_GAMEON
Global FLAG_MENUON
Global FLAG_PAUSE
Global FLAG_SAVESCREEN = 0
Global FLAG_DEBUG = 0
Global FLAG_GAMESTARTER
Global PLAYER_SHIELD#
Global PLAYER_BOOSTERS#
Global PLAYER_CLOAK#
Global PLAYER_JUMPS
Global PLAYER_SPEED#
Global PLAYER_ANGLE#
Global PLAYER_X#
Global PLAYER_Y#
Global PLAYER_SCORE
Global HI_SCORE

Global timer
Global frames
Global starson
Global game_pause_frame
Global game_accept_pause
Global game_pause_stat
Global hypercount#		;a "#" symbol after the variable name means it can hold a FLOATING POINT number, i.e.: 190.1234
Global pausecount		;without the "#" symbol, the variable is, by default, an INTEGER (Whole) number, i.e.: 190
Global cloakon#			;use the symbols when you are *sure* that you want it to hold specific types of data:
Global frame1			;# - floating point
Global frame2			;% - integer (whole number)
Global frame3			;$ - string (text, i.e.: "MYKE 12345"
Global frame4			;
Global frame5
Global frame6
Global frame7
Global frame8
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
Dim game_icons(4)			;the arrays have single dimensions (imagine one straight line of boxes, each that can contain a single variable) 0 to.. (the number in brackets)
Global game_icon_dot
Global game_player
Global game_player_frame
Global game_player_dot
Global game_bullet_player
Global game_gameover
Global game_paused

;Types are like Structures in C. You have a "Type" called whatever. Then you can make multiple versions of the type. Each version of the Type has the same properties, i.e.:
;a FISH (the Type) has EYES, MOUTH, SCALES And FINS (it's properties) - ALL FISH have these properties.
;a DOG (the Type) has EYES, MOUTH, FUR and TAIL (it's properties) - ALL DOGS have these properties (look like this.)
Type stars						;create "Type" for parallex stars
	Field depth,x#,y#			;each star has a depth, x and y position
End Type

Type icons						;same for icons
	Field x#,y#,style,frame#
End Type

;this is for the bullets. I've used an array, rather than a type (which would have been just as good in this case) to show you how MULTIDIMENSIONAL arrays can work.
Const bulletnum = 500
Global bulletlimiter
Global nextbullet = 0
Dim bullets(bulletnum-1,7) ;create an array for 'bulletnum' (0 to bulletnum -1) on-screen bullets with 8 values per bullet:
						;0 = bullet_x, 1 = bullet_y, 2 = bullet_angle, 3 = bullet_speed,
						;4 = bullet_style, 5 = bullet_animframe, 6 = bullet_origin_x, 7 = bullet_origin_y
						;we'll say that a bullet's style can also say whether or not it's in use, i.e.: 0 = off, 1 = player, 2 = enemy1


;this code makes 4 'objects' in a "Type" called "ICONS".
For i = 1 To 4					;do this 1, 2, 3, 4 times
	icon.icons = New icons		;create a new icon
	icon\style = i				;the icon style for each new icon is equal to the increment of i (i.e.: 1, 2, 3 or 4!)
	icon\frame = Int(Rnd(0,5))	;create a random frame number between 0 and 5 for each new icon
Next

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

;similarly, this code loads the image numbers into an array called "game_icons"
;however, these are images that contain the frames of an Animation, so a different Load command is used.
game_icons(1) = LoadAnimImage("GfxRes/icon-boost.bmp",32,32,0,6)	;LoadAnimImage has the same structure as LoadImage, with additional numbers after the picture filename
game_icons(2) = LoadAnimImage("GfxRes/icon-shield.bmp",32,32,0,6)	;these are: Frame Width (pixels), Frame Height (pixels), Starting Framenumber (usually 0)
game_icons(3) = LoadAnimImage("GfxRes/icon-cloak.bmp",32,32,0,6)	;and the Number of Frames in the Image (as *you* would count them (in this case 6)
game_icons(4) = LoadAnimImage("GfxRes/icon-jump.bmp",32,32,0,6)		;have a look at the file "icon-boost" in Paint Shop Pro and see for yourself the 6 frames of animation.
For i = 1 To 4
	MaskImage game_icons(i),255,0,255	;mask the images for each icon, so that MAGENTA (255,0,255) is the transparent colour
Next

;notice in the following code, we're loading (and MASKING) graphics in exactly the same way as before, but into regular variables instead of arrays.
;this next bit is all animations
game_player = LoadAnimImage("GfxRes/player-ship.bmp",80,80,0,72) ;load in the 'sprite' for the player ship
MaskImage game_player,255,0,255 ;mask the images for the player ship, so that MAGENTA (255,0,255) is the transparent colour
game_bullet_player = LoadAnimImage("GfxRes/bullet-player.bmp",10,10,0,6)	; the blue player bullet animation
MaskImage game_bullet_player,255,0,255 ;mask the images
;these are all plain single-frame pictures
;game piccies
game_gameover = LoadImage("GfxRes/game-gameover.bmp")		;the game over logo, which I tend to see a lot of.. :(
game_paused = LoadImage("GfxRes/game-paused.bmp")			;the paused logo
game_player_dot = LoadImage("GfxRes/player-radardot.bmp")	;radar dots for the player and bonus icons respectively
game_icon_dot = LoadImage("GfxRes/icon-radardot.bmp")
MaskImage game_gameover,255,0,255
MaskImage game_paused,255,0,255

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
	hypercount = 102
	FLAG_PAUSE = 0
	PLAYER_SCORE = 0
	PLAYER_TIME = 0
	PLAYER_ANGLE = 0
	PLAYER_SPEED = 0
	PLAYER_SHIELD = 192			;the width of the game_level image is 192. Rather than working out a percentage of 100, it's much
	PLAYER_BOOSTERS = 192		;easier to use the width of the image on such BAR type displays.
	PLAYER_CLOAK = 192			
	PLAYER_JUMPS = 5
	game_player_randomize()			;calls a function that randomizes a player's position on the map
	game_stars_randomize()			;calls a function that randomizes the star positions

	;this next bit is the first time you'll have seen a TYPE cycle.
	;Just like a normal "For i = 0 to 20" loop, this goes through each Version of a type
	;and set's it's properties. (i.e.: That FISH type has a SCALES property. Here we tell it that this particular Fish's Scales are GOLD!)
	For icon.icons = Each icons
		icon\x = Rnd(0,GAME_AREA_X)		;for each of the 4 icons on the play area at any one point, we need a random x and y position.
		icon\y = Rnd(0,GAME_AREA_Y)
	Next
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
			hypercount = 0
			cloakon = 0
			FLAG_GAMEON=0 			;set FLAG_GAMEON to equal 0. Back in the "game_loop()" function, this will cause the code to jump back to the menu.
		End If

		If FLAG_PAUSE = 0 Then							;this is really easy! If the game is paused then *don't* do any of the following code. No values will change,
														;hence nothing will move on the screen when it comes to updating it later! :)
														
				If KeyDown(KEY_HYPER)					;if the "HYPER" key is pressed then..
					If PLAYER_JUMPS > 0 Then
						If hypercount = 0 Then				;(if "hypercount" is 0, then we're not already in the middle of a HyperJump, so let's set things off!)
							hypercount = 1						;by setting "hypercount" to 1, we'll set off a chain of events later on..
							PLAYER_JUMPS = PLAYER_JUMPS - 1		;subtract the number of Jumps a player has left by 1
							If PLAYER_JUMPS <= 0 Then			;if the number of jumps left is 0 or less, then the above subtraction will make it "-1", so..
								PLAYER_JUMPS = 0				;put it back to 0.
							End If
						End If
					End If
					FlushKeys
				End If
				If hypercount = 0 Then						;as long as we're not in the middle of a hyperjump, do the following..
				
					If KeyDown(KEY_CLOAK)			;if the CLOAK key is pressed then
					
						If PLAYER_CLOAK > 2 Then		;if the player has enough (2 points of) PLAYER_CLOAK left, then..
							cloakon = cloakon + 1									;the cloakon value is incremented so that later on, we can do the flickering effect
							PLAYER_CLOAK = PLAYER_CLOAK - INCR_CLOAK_DOWN			;subtract some PLAYER_CLOAK ability at a rate of "INCR_CLOAK_DOWN"
							
						Else							;if the player doesn't have enough PLAYER_CLOAK left then
							cloakon = 0						;reset the "cloakon" value to 0
							PLAYER_CLOAK = 0				;Keep the PLAYER_CLOAK value at 0, so that it doesn't build up...
						End If
						FlushKeys
					Else							;if the CLOAK key isn't pressed then
						cloakon = 0							;reset the "cloakon" value to 0
						If PLAYER_CLOAK < 192								;if the player has less than the top value (192 points) of PLAYER_CLOAK then..
							PLAYER_CLOAK = PLAYER_CLOAK + INCR_CLOAK_UP			;Slowly build up the PLAYER_CLOAK value.
						Else
							PLAYER_CLOAK = 192								;otherwise cap the PLAYER_CLOAK value at 192 points.
						End If
						FlushKeys
					End If
					If KeyDown(KEY_BOOST)							;the BOOST key works in exactly the same way as the CLOAK key
						If PLAYER_BOOSTERS > 2 Then
							If PLAYER_SPEED < (SPEED_MAX*3) Then
								PLAYER_SPEED = PLAYER_SPEED + (INCR_SPEED * 3)
							End If
							PLAYER_BOOSTERS = PLAYER_BOOSTERS - INCR_BOOSTERS_DOWN
						Else
							PLAYER_BOOSTERS = 0
						End If
						FlushKeys
					Else
						If PLAYER_BOOSTERS < 192
							PLAYER_BOOSTERS = PLAYER_BOOSTERS + INCR_BOOSTERS_UP
						Else
							PLAYER_BOOSTERS = 192
						End If
						FlushKeys
					End If
					If KeyDown(KEY_FIRE) Then					;if the "FIRE" key is pressed, then..
						If bulletlimiter = 0 Then					;the "bulletlimiter" value works in exactly the same way as "game_accept_pause" earlier
																	;in that we don't want too many bullets spraying out of the player, due to SCANCODE/framerate issues
							bulletlimiter = 1						;so we set the "bulletlimiter value to 1, so that until we say so (a bit later) no more "FIRE" keypresses
																	;will trigger.
																	
							createbullet(PLAYER_X,PLAYER_Y,PLAYER_ANGLE,PLAYER_SPEED,1)		;however, assuming we've got a successful "FIRE" event, let's create some bullets
																							;on the screen by calling the "createbullet()" function.. This is pretty cool, so
																							;see the function itself later on for explainations!
						End If
						FlushKeys
					End If
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
				End If	;end of the "If we're not in the middle of a hyperjump" IF statement
				
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
	
			For icon.icons=Each icons				;the "icons" don't move from their randomly set x,y position, so all we need to do for them is
				icon\frame = icon\frame+.5			;update their animation frame number. There are 6 frames, and we're incrementing at 0.5 frames per update.
				If Int(icon\frame) = 6 Then			;in real terms, this means we'll be updating the frame number every 2 updates.
					icon\frame = 0					;Because the icon animations are simple 0 to 5 cycling animations, when the frame number reaches 6, we flip it back to 0
				End If								;to start the sequence again.
			Next
			
			;this next bit of code creates the "simple but effective" Hyperjump event, based entirely on the value of "hypercount" being set to 1, earlier in the code.
			If hypercount > 0 Then
				If (hypercount/2 = Int(hypercount/2)) Then	;this line says "If the value of 'hypercount' is an EVEN number"
															;because 5 (an odd number) would be 2.5 when devided by 2, but conversion to an INTeger gives 3.
															;2.5 does NOT equal 3!
					;all of this simply rotates the HyperJump ships in different offset directions
					;the only difference is, we're not bothering with degrees here, just animation frame numbers, i.e.: 355 degrees is frame 71 (i.e.: 355/5 degree increment)
					frame1 = frame1 + 3
					frame2 = frame2 - 3
					frame3 = frame1 + 9
					frame4 = frame2 - 9
					frame5 = frame1 + 36
					frame6 = frame2 - 36
					frame7 = frame1 + 54
					frame8 = frame2 - 54
					If frame1 >=72 Then
						frame1 = frame1-72
					End If
					If frame1 < 0 Then
						frame1 = frame1+72
					End If
					If frame2 >=72 Then
						frame2 = frame2-72
					End If
					If frame2 < 0 Then
						frame2 = frame2+72
					End If
					If frame3 >=72 Then
						frame3 = frame3-72
					End If
					If frame3 < 0 Then
						frame3 = frame3+72
					End If
					If frame4 >=72 Then
						frame4 = frame4-72
					End If
					If frame4 < 0 Then
						frame4 = frame4+72
					End If
					If frame5 >=72 Then
						frame5 = frame5-72
					End If
					If frame5 < 0 Then
						frame5 = frame5+72
					End If
					If frame6 >=72 Then
						frame6 = frame6-72
					End If
					If frame6 < 0 Then
						frame6 = frame6+72
					End If
					If frame7 >=72 Then
						frame7 = frame7-72
					End If
					If frame7 < 0 Then
						frame7 = frame7+72
					End If
					If frame8 >=72 Then
						frame8 = frame8-72
					End If
					If frame8 < 0 Then
						frame8 = frame8+72
					End If
				End If	;end of the "if hypercount is EVEN" IF statement
				
				;this bit increases the value of "hypercount" until it reaches 200
				hypercount = hypercount + 1
				If hypercount > 0 And hypercount <= 102 Then	;between 0 and 102, the player speed increases
					PLAYER_SPEED = PLAYER_SPEED + 2
				ElseIf hypercount > 102 And hypercount < 200 Then	;between 102 and 200, the player speed decreases (but only if FLAG_GAMESTARTER is 0)
					If FLAG_GAMESTARTER = 0 Then					;this is because when the game starts, the player speed is 0, but we've faked being in
						PLAYER_SPEED = PLAYER_SPEED - 2				;the middle of a hyperjump. Without this FLAG, the player ship would start each game
					End If											;travelling at about -90 speed backwards, which would just be weird! :)
				End If
				If hypercount = 100 Then
					game_stars_randomize()	;half way through the Hyperjump sequence, the "game_stars_randomize()" function is called, which changes all the star positions	
											;and depths, to give the impression we've moved to a completely different part of space
				End If
				If hypercount/10 = Int(hypercount/10) Then
					game_player_randomize()		;every 10 updates, the function "game_player_randomize()" is called. This randomly jumps the player around the map
												;and confuses the hell out of the bad guys! (well it will by next month's tutorial!!) ;))
				End If
				If hypercount = 200 Then		;if the "hypercount" variable reaches 200, then stop the sequence and reset the value back to 0
					hypercount = 0
					FLAG_GAMESTARTER = 0		;also, when the game first starts we were in the middle of a hyperjump. Resetting this FLAG back to 0 means that
												;the next hyperjump sequence will decrease the speed after "hypercount" gets to 103
				End If
			Else							;if we're not in the middle of a hyperjump event, then:
				frame1=game_player_frame-1	;this bit is just for the DEBUG mode. It was designed to check that 360 degrees became 0 degrees and vice versa correctly
				frame2=game_player_frame+1
				If frame1 < 0 Then
					frame1 = frame1 + 72
				End If
				If frame2 >= 72 Then
					frame2 = frame2 - 72
				End If
			End If	;end of the "Are we in the middle of a Hyperjump" IF statement
			
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

			;the following lines calculate all the bullet positions, relative to their originally set SPEED and ANGLE from the "createbullet" function.
			For i = 0 To bulletnum-1
				If bullets(i,4) > 0 Then		;if the bullet isn't 0, i.e.: it's "on"
				
					bullets(i,0) = bullets(i,0) + (bullets(i,3)*(Sin(bullets(i,2))/2))	;bullet x position = bullet x position + (bullet speed * (SIN (bullet angle) / 2)
					bullets(i,1) = bullets(i,1) - (bullets(i,3)*(Cos(bullets(i,2))/2))	;bullet y position = bullet y position + (bullet speed * (COS (bullet angle) / 2)
								;try turning these last two lines off for a crazy pulsing effect of bullets that you can use
								;to surround the player, creating a kind of enemy-deadly minefield.. Madness! :)
					
					bullets(i,5) = bullets(i,5) + 1		;increases the animation frame of the bullet
					If bullets(i,5) = 6 Then	;if the animation frame is 6 then flip it back to 0 (the bullet animation runs frames 0, 1, 2, 3, 4, 5, 0, 1, 2 ... etc)
						bullets(i,5) = 0
					End If
					
					;as with the player and enemies, this next bit wraps the bullets around the map extremities
					If bullets(i,0) < 0 Then
						bullets(i,0) = (bullets(i,0) + GAME_AREA_X)
					End If
					If bullets(i,0) > GAME_AREA_X Then
						bullets(i,0) = (bullets(i,0) - GAME_AREA_X)
					End If
					If bullets(i,1) < 0 Then
						bullets(i,1) = (bullets(i,1) + GAME_AREA_X)
					End If
					If bullets(i,1) > GAME_AREA_Y Then
						bullets(i,1) = (bullets(i,1) - GAME_AREA_Y)
					End If
					
					;like the enemies "distance from player", this bit checks if a bullet has gone a certain distance from it's origin
					temp_x1# = bullets(i,0) - bullets(i,6) ;origin x on the right
					temp_y1# = bullets(i,1) - bullets(i,7) ;origin y on the top
					temp_x2# = bullets(i,6) - bullets(i,0) ;origin x on the left
					temp_y2# = bullets(i,7) - bullets(i,1) ;origin y on the bottom

					;wraps distances around the map extremities
					If temp_x1# < 0 Then
						temp_x1# = temp_x1# + GAME_AREA_X
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
					
					;calculates the shortest distance
					If Abs(temp_x1#) < Abs(temp_x2#) Then
						temp_x# = Abs(temp_x1#)
					Else
						temp_x# = Abs(temp_x2#)
					End If
					If Abs(temp_y1#) < Abs(temp_y2#) Then
						temp_y# = Abs(temp_y1#)
					Else
						temp_y# = Abs(temp_y2#)
					End If
					
					;back to PYTHAGORAS to work out the "As the crow flies" distance from the bullet's original starting point
					temp_x# = Abs(temp_x# / 200)
					temp_y# = Abs(temp_y# / 200)
					temp_hyp# = (temp_x#*temp_x#) + (temp_y#*temp_y#)
					temp_hyp# = Sqr(temp_hyp#)

					;if the bullet has travelled "5" units from it's starting point, then..
					If temp_hyp# > 5 Then
						bullets(i,4) = 0	;turn the bullet off
					End If
				End If
			Next	;end of bullet coordinate repositioning loop
			
			;this is the player's bullet limiting code, basically only allowing the player to fire every 10 frames (otherwise it looks more like the player is spraying water!!)
			If bulletlimiter > 0 Then
				bulletlimiter = bulletlimiter + 1
				If bulletlimiter = 10 Then ;value essentially sets the fire-rate (the lower the number, the faster the fire rate!)
					bulletlimiter = 0
				End If
			End If
			
			PLAYER_SCORE = PLAYER_SCORE + 1		;if the game is in progress, then increase the PLAYER_SCORE by 1 every screen update
													;this acts as a kind of survival bonus for the more defensive player
				If PLAYER_SCORE >= HI_SCORE Then
					HI_SCORE = PLAYER_SCORE
				End If
			
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
	
		;this FOR Type loop draws all the versions of "icons" at their screen-position - relative to the PLAYER coordinates
		;in the current animation frame. It also checks for the non-transparent areas of the ICON image touching the non-
		;transparent areas of the PLAYER image and updates the PLAYER's relavent energy-level values.
		For icon.icons=Each icons
		
			;this line draws the relavant icon image at coordinates "x" and "y" on (or off) the screen
			;the correct icon image is chosen using the "icons"'s style as it's array position
			;the x and y coordinates are worked out by subtracting the PLAYER's current position from the "icons"'s current position
			;notice that we're using the same style of "SCREEN_WIDTH\2 - Half the ImageWidth of the Image" idea from the
			;"menu_draw_update()" code?
			DrawImage game_icons(icon\style), (((SCREEN_WIDTH/2)-ImageWidth(game_icons(icon\style))/2) - (PLAYER_X - icon\x)), (((SCREEN_HEIGHT/2)-ImageWidth(game_icons(icon\style))/2) - (PLAYER_Y - icon\y)),Int(icon\frame)

	    Next	;end of the "icons" FOR loop

		;now we'll do the same for all the bullets on (or off) the screen	
		For i = 0 To bulletnum-1	;this cycles though the array from position 1 to the final bullet number in the array..
									;we use "bulletnum - 1" because there are (for example) 600 possible bullets that are stored
									;in an array as 0, 1, 2, 3 ... 596, 597, 598 and 599
		
			Select bullets(i,4)		;whose bullet is it? remember when we originally DIMmed this Array, I said that 0 was off, 1 was a PLAYER bullet and 2 was an enemy bullet?
				Case 1
				
					;if it's a player bullet then..
					;draw this bullet at it's position, relative to the player, in it's current animation frame
					DrawImage game_bullet_player,(((SCREEN_WIDTH/2)-ImageWidth(game_bullet_player)/2) - (PLAYER_X - bullets(i,0))), (((SCREEN_HEIGHT/2)-ImageWidth(game_bullet_player)/2) - (PLAYER_Y - bullets(i,1))),bullets(i,5)
			End Select
		Next	;end of the "bullet drawing/collision detecting" FOR loop

	;this section draws the frames of "animation" for the hyperjump, or - if we're not mid hyperjump - draws the regular player frames
	;the hyperjump bits look quite complecated, but it's just x and y positions that are changing in relation to the value of "hypercount"
	If hypercount > 0 And hypercount < 100 Then
		If hypercount/2 = Int(hypercount/2) Then		;if the value of "hypercount" is EVEN then..
														;draw these four pictures
			DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2+hypercount,(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2-hypercount,frame2
			DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2+hypercount,(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2+hypercount,frame4
			DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2-hypercount,(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2+hypercount,frame6
			DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2-hypercount,(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2-hypercount,frame8

		Else											;otherwise, if the value of "hypercount" is ODD, then..
														;draw these four pictures..
														;this ODD/EVEN swapping results in our "flickery" effect, but you
														;can see the distinct 4 pictures if you PAUSE the game in the middle of a jump!
			DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2-(1.4*hypercount),(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2,frame7
			DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2,(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2+(1.4*hypercount),frame5
			DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2+(1.4*hypercount),(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2,frame3
			DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2,(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2-(1.4*hypercount),frame1
		End If
	ElseIf hypercount >= 100 And hypercount < 200 Then	;between 0 and 100, the 8 "ships" move outwards from the middle..
														;after that, they converge back to the centre, using exactly the same code, but "200 - hypercount"
														;instead of "hypercount". It's a cheap effect that didn't cost me any more Paint Shop Pro time
														;and it looks quite cool! :)
		If hypercount/2 = Int(hypercount/2) Then
			DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2+(200-hypercount),(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2-(200-hypercount),frame2
			DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2+(200-hypercount),(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2+(200-hypercount),frame4
			DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2-(200-hypercount),(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2+(200-hypercount),frame6
			DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2-(200-hypercount),(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2-(200-hypercount),frame8
		Else
			DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2-(1.4*(200-hypercount)),(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2,frame7
			DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2,(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2+(1.4*(200-hypercount)),frame5
			DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2+(1.4*(200-hypercount)),(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2,frame3
			DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2,(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2-(1.4*(200-hypercount)),frame1
		End If

	Else			;if, on the other hand, we're not mid-hyperjump, then we'll just be drawing the normal player frames..
	
		;checks to see that the angle we want to draw the frame for is 0 to 71 (as 360 degrees is the same as 0 degrees!)
		game_player_frame = PLAYER_ANGLE/5
		If game_player_frame = 72 Then
			game_player_frame = 0
		End If
		
		If cloakon = 0 Then		;if the "cloaking device" is off, then draw the player every screen update..
			DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2,(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2,game_player_frame

		Else					;if the "cloaking device" is on, only draw the player when the "cloakon" number is EVEN..
								;this is our flickering effect used again..
			If cloakon/2 = Int(cloakon/2) Then
				DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2,(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2,game_player_frame
			End If
		End If
	End If
	
	;As we said before, the foremost pictures/displayed objects are drawn last in our Video collage.
	Color 0,128,0
	Text SCREEN_WIDTH-210,220,"SCORE: " + PLAYER_SCORE
	Text SCREEN_WIDTH-210,240,"HISCR: " + HI_SCORE
	Text SCREEN_WIDTH-210,260,"BOOST: " + Int(PLAYER_BOOSTERS)
	Text SCREEN_WIDTH-210,280,"CLOAK: " + Int(PLAYER_CLOAK)
	Text SCREEN_WIDTH-210,300,"JUMPS: " + PLAYER_JUMPS
	Line SCREEN_WIDTH-210,3,SCREEN_WIDTH-10,3
	Line SCREEN_WIDTH-210,213,SCREEN_WIDTH-10,213
	Line SCREEN_WIDTH-210,3,SCREEN_WIDTH-210,213
	Line SCREEN_WIDTH-10,3,SCREEN_WIDTH-10,213
		;notice that by using a calculated x coordinate "SCREEN_WIDTH - value" in conjunction with a fixed
		;y coordinate, we will have the HUD images at the same distance from the top right of the screen
		;in any Graphics resolution we choose -- PLUS I've made the radar border GREEN using the color command
		
	Color 255,0,255
	
	;this next bit of code draws the coloured dots on the radar
	;by using their game map coordinates, at a ratio to the size of the radar box graphic
	For icon.icons=Each icons
		;this draws each of the icons
		DrawImage game_icon_dot,((SCREEN_WIDTH-211)+icon\x/(GAME_AREA_X/200)),((9)+icon\y/(GAME_AREA_Y/200))
	Next
	
	;this next bit draws the player's green dot on the radar
	If hypercount = 0 And cloakon=0 Then	;if both the "hypercount" and "cloakon" features are inactive (i.e.: normal play)
		;then permanently draw the player's radar dot
		DrawImage game_player_dot,((SCREEN_WIDTH-211)+PLAYER_X/(GAME_AREA_X/200)),((9)+PLAYER_Y/(GAME_AREA_Y/200))

	ElseIf hypercount = 0 And (cloakon/2 = Int(cloakon/2)) Then
		;otherwise, if the cloakon value is EVEN, draw the dot (flickery effect again)
		;but if the hypercount value isn't 0 (we're in the middle of a hyperjump) then don't do this..
		;.. consequently, even though the player's position moves on the radar map during a hyperjump,
		;and it affects the enemies, don't show this to the user!
		DrawImage game_player_dot,((SCREEN_WIDTH-211)+PLAYER_X/(GAME_AREA_X/200)),((9)+PLAYER_Y/(GAME_AREA_Y/200))
	End If

	;if the game is paused (FLAG_PAUSE = 1) then draw the image when "game_pause_stat" = 1
	;this gives us our flash on-off effect as used earlier in "menu_draw_update()"
	
	If FLAG_PAUSE = 1 And game_pause_stat = 1 Then
		DrawImage game_paused,SCREEN_WIDTH/2-ImageWidth(game_paused)/2,SCREEN_HEIGHT-((SCREEN_HEIGHT-350)/2+ImageHeight(game_paused)/2)
	End If
	
	;the following text will only be printed on the screen while the "FLAG_DEBUG" variable is set to 1 (F1, as I programmed it, on the main menu!)
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
		Text 0,120, "SCORE = " + PLAYER_SCORE

		;this debug code counts how many bullets are "active" in the array (their 'style' - bullets(i,4) is not 0
		;this was useful for deciding how big the array needed to be for the game, i.e.: how many bullets array
		;containers would be in use at any one time.
		;in reality, during the game, this value rarely gets above 100 active bullets,
		;but I kept the "available" bullets (bulletnum) at 500 anyway.
		
		;if you needed better performance, then decreasing the value of "bulletnum" might increase the speed of the program!
		bulletcount = 0
		For i = 0 To bulletnum-1
			If bullets(i,4) > 0 Then
				bulletcount = bulletcount + 1
			End If
		Next
		Text 0,160, "BULLETS = " + bulletcount
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


;This is the last function and it's MARVELLOUS! :)
;This is the type of function that you should proactively *try* to write, because they're incredibly simple, yet incredibly effective
;and they impress the girls, I can tell you... Um.. No.. That's a lie..

;anyway the beauty of this function is that when you call it in the code above, you "feed" it some parameters.
;this one is expecting an X Coordinate, a Y Coordinate, an Angle value, a Speed value and a Style value.
;it doesn't care who or what they refer to, it just knows that it wants 5 numbers to work with.

;So, when a player requires two bullets to be made, I just give it "PLAYER_X, PLAYER_Y, PLAYER_ANGLE,
;PLAYER_SPEED and 1 (which just makes sure the correct bullet AnimImage is used!)

;When an enemy needs a bullet, I just give it "Enemy X, Enemy Y, Enemy Angle, Enemy Speed, 2"

;the createbullet() function does the rest and "produces" 2 bullets on the screen, starting at the end of the calling ship's laser.. BUT THAT'S ALL..
;After the bullet has been created, its "life" is calculated in another part of the code (it's x and y coordinates are altered based on the original angle and speed
;calculated in this function..) Enjoy

Function createbullet(x,y,angle,speed,style) ;creates 2 bullets starting on the end of a ship's lasers.

	;there are "bulletnum" containers available in the "bullets()" array.
	;if we've reached the maximum number of bullets, then the very first
	;'bullet' will be overwritten with this new bullet. 
	;We do this by wrapping "nextbullet" back to 0, when it reaches bulletnum
	If nextbullet = bulletnum-1 Then
		nextbullet = 0
	End If
	
	;we want to create a bullet for the RIGHT laser of a ship
	;by messing with the angleoffset value during debugging, I was able to come up with the
	;correct values that looked right on screen (30 for this angle and 28 for the next calculation)
	angleoffset = angle - 30
	bullets(nextbullet,0) = x + 28*Cos(angleoffset)		;this sets the x and y pixel offset from the ship's game coordinates
	bullets(nextbullet,1) = y + 28*Sin(angleoffset)		;in this case 28*Cos/Sin of the angle offset

	;these next bits are just setting the origin of the bullets, including it's start position, speed and angle
	;this is so the bullets can fly independently of the PLAYER's ship's Angle and speed (unlike everything else in the game)
	bullets(nextbullet,2) = angle
	If speed <= 0 Then						;this bit essentially stops the player catching up to his bullets, or bullets flying backwards
		bullets(nextbullet,3) = 25			;if the ship is still, or moving backwards, then a bullet fires off at a speed of 25
	Else
		bullets(nextbullet,3) = speed + 25	;otherwise, if a ship is moving forwards, then the bullet flys off at a speed of 25, PLUS the ship's speed
	End If

	bullets(nextbullet,4) = style					;used to decide which bullet AnimImage should be used
	bullets(nextbullet,5) = 0							;used to keep track of the frame of animation a bullet is on
	bullets(nextbullet,6) = bullets(nextbullet,0)		;used to know where a bullet began it's life in x and y coordinates
	bullets(nextbullet,7) = bullets(nextbullet,1)
	nextbullet = nextbullet+1						;we've successfully created a bullet (whose 'life' from now on will be calculated back
													;in "game_loop_update()", so now we move the "nextbullet" value on by 1
													;ready for creation of the next bullet!



	If nextbullet = bulletnum-1 Then					;exactly the same process, but with a 120 degree offset for the left hand laser
		nextbullet = 0
	End If
	angleoffset = angle - 150
	bullets(nextbullet,0) = x + 28*Cos(angleoffset)
	bullets(nextbullet,1) = y + 28*Sin(angleoffset)
	bullets(nextbullet,2) = angle
	If speed <= 0 Then
		bullets(nextbullet,3) = 25
	Else
		bullets(nextbullet,3) = speed + 25
	End If
	bullets(nextbullet,4) = style
	bullets(nextbullet,5) = 0
	bullets(nextbullet,6) = bullets(nextbullet,0)
	bullets(nextbullet,7) = bullets(nextbullet,1)
	nextbullet = nextbullet+1
End Function