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
Const ENEMY_MAX_DIST = 5000
Const ENEMY_MIN_DIST = 50
Const DAMAGE_ENEMY_BASH = 2
Const DAMAGE_ENEMY_IS_SHOT = 35
Const DAMAGE_PLAYER_BASH = 2
Const DAMAGE_PLAYER_IS_SHOT = 5
Const SCORE_ENEMYCOLLIDE = 50
Const SCORE_ICONCOLLECT = 200
Const SCORE_ENEMY_IS_SHOT = 400
Const SCORE_ENEMY_IS_DESTROYED = 1500
Const BONUS_BOOSTERS = 48
Const BONUS_SHIELD = 48
Const BONUS_CLOAK = 48

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
Global PLAYER_SCORE_STR$
Global HI_SCORE
Global HI_SCORE_STR$
Global LAST_SCORE
Global LAST_SCORE_STR$
Global SCORE_FILE

Global timer
Global frames
Global starson
Global menu_frame
Global menu_accept_quit
Global game_pause_frame
Global game_accept_pause
Global game_pause_stat
Global gameovercounter
Global gameoverstat
Global whichway
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
Dim game_jumps_level(5)		;in the case of "game_jumps_level", it has *SIX* containers: 0, 1, 2, 3, 4, 5 and 6.
Global game_background
Global game_radar
Global game_shields
Global game_jumps
Global game_boosters
Global game_cloak
Global game_shields_level
Global game_boosters_level
Global game_cloak_level
Global game_levels
Global game_score
Global game_hiscore
Global game_icon_dot
Global game_player
Global game_player_frame
Global game_player_dot
Global game_enemy1
Global game_enemy1_dot
Global game_scorefont
Global game_bullet_player
Global game_bullet_enemy1
Global game_explosion
Global player_explosion_frame#
Global game_gameover
Global game_paused
Global menu_hiscore
Global menu_lastscore
Global menu_scorefont
Global menu_start
Global menu_start_stat
Global menu_logo
Global menu_credit
Global menu_guildhall
Global menu_thanks
Global menu_quit
Global menu_ship1
Global menu_ship2
Global menu_ship_hor
Global menu_ship_ver#
Global menu_ship_type

;Types are like Structures in C. You have a "Type" called whatever. Then you can make multiple versions of the type. Each version of the Type has the same properties, i.e.:
;a FISH (the Type) has EYES, MOUTH, SCALES And FINS (it's properties) - ALL FISH have these properties.
;a DOG (the Type) has EYES, MOUTH, FUR and TAIL (it's properties) - ALL DOGS have these properties (look like this.)
Type stars						;create "Type" for parallex stars
	Field depth,x#,y#			;each star has a depth, x and y position and a picture
End Type

Type enemies					;same for enemies
	Field x#,y#,energy,angle,speed#,style,maxspeed,dest_angle,dist_travelled,dist_to_go,dest_opp#,dest_hyp#,dest_adj#,rotate_angle,status,bulletlimiter,exp_frame#,gen
End Type
;notice the "enemies" Type has a "style"?
;originally, I planned to have different styles of enemy space-ships, but never got around to drawing the graphics!
;the "style" is used in the subsequent code, and the Type property remains, so *technically* adding new enemy styles to the game should be pretty easy! :)

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


;This peice of code reads a file called "rift.dat" for the HI_SCORE and LAST_SCORE values from the last session
SCORE_FILE = ReadFile("rift.dat")	;use the variable SCORE_FILE (similar to image numbers before) as a reference to the file
									;open a file for READING ("ReadFile"). The file is called "Rift.dat"
If SCORE_FILE = 0 Then				;if the file wasn't found, then the reference "SCORE_FILE" will have been set to 0
	HI_SCORE = 0
	LAST_SCORE = 0
Else								;if the file wasn't found, then the reference "SCORE_FILE" will have been set to something *other* than 0.
	HI_SCORE = Int(ReadLine(SCORE_FILE))	;read in one line from the file, then make it equal a number, rather than the ASCII string it is stored as. The first line becomes the HI_SCORE
	LAST_SCORE = Int(ReadLine(SCORE_FILE))  ;read in another line from the file. This is the LAST_SCORE.
End If
CloseFile(SCORE_FILE)				;we've finished with the file, so CLOSE it (tells Blitz to disregard the reference number) - we won't be READing from it anymore!

;This section turns the Integer HI_SCORE and LAST_SCORE values into strings with leading 0's
;i.e.: 1000 becomes "0001000". You will see this exact same peice of code later on, so - professionally - it *should* have been done in a FUNCTION.
;I've kept it separate to show that you *can* just as well use copied code and not see a difference in the final program.
If HI_SCORE < 10 Then									;If the value of HI_SCORE is less than 10, then..
	HI_SCORE_STR = "000000"+Str(HI_SCORE)				;make the string "HI_SCORE_STR" = "000000" and put the value of HI_SCORE on the end as a string
														;such that, for example, HI_SCORE_STR now holds the text: "0000003"
														
ElseIf HI_SCORE >=10 And HI_SCORE < 100 Then			;If the value of HI_SCORE is 10 or more, AND the value of HI_SCORE is less than 100, then..
	HI_SCORE_STR = "00000"+Str(HI_SCORE)				;.. and so on.. the use of "AND" here means that both 'arguements' (the HI_SCORE >= 10 type bits)
														;must be true, or the resulting operation (the HI_SCORE_STR = ... type bit) won't happen.
														;In this case, for example, 104 *is* "10 or more" but it *is not* less than 100, so ignore this "If statement"
														;The "ElseIf" keyword means that if the first "IF" didn't fire, then "try this one"..
ElseIf HI_SCORE >=100 And HI_SCORE < 1000 Then
	HI_SCORE_STR = "0000"+Str(HI_SCORE)
ElseIf HI_SCORE >=1000 And HI_SCORE < 10000 Then
	HI_SCORE_STR = "000"+Str(HI_SCORE)
ElseIf HI_SCORE >=10000 And HI_SCORE < 100000 Then
	HI_SCORE_STR = "00"+Str(HI_SCORE)
ElseIf HI_SCORE >=100000 And HI_SCORE < 1000000 Then
	HI_SCORE_STR = "0"+Str(HI_SCORE)
ElseIf HI_SCORE >=100000 And HI_SCORE < 10000000 Then
	HI_SCORE_STR = Str(HI_SCORE)
Else													;The "Else" keyword means that if NONE of the other IF statements fired, then do this next bit, as a last resort!
														;The "Else" statement always comes last in an "IF.. ELSEIF.. ENDIF" peice of code
	HI_SCORE_STR = "9999999"
End If													;The "EndIf" keyword means that's the end of this set of checks.

If LAST_SCORE < 10 Then										;Starting a new "IF.. ELSEIF.. ENDIF" peice of code, rather than using more "ElseIf"s means that this
	LAST_SCORE_STR = "000000"+Str(LAST_SCORE)				;set of decisions is not affected by the previous set.
ElseIf LAST_SCORE >=10 And LAST_SCORE < 100 Then
	LAST_SCORE_STR = "00000"+Str(LAST_SCORE)
ElseIf LAST_SCORE >=100 And LAST_SCORE < 1000 Then
	LAST_SCORE_STR = "0000"+Str(LAST_SCORE)
ElseIf LAST_SCORE >=1000 And LAST_SCORE < 10000 Then
	LAST_SCORE_STR = "000"+Str(LAST_SCORE)
ElseIf LAST_SCORE >=10000 And LAST_SCORE < 100000 Then
	LAST_SCORE_STR = "00"+Str(LAST_SCORE)
ElseIf LAST_SCORE >=100000 And LAST_SCORE < 1000000 Then
	LAST_SCORE_STR = "0"+Str(LAST_SCORE)
ElseIf LAST_SCORE >=100000 And LAST_SCORE < 10000000 Then
	LAST_SCORE_STR = Str(LAST_SCORE)
Else
	LAST_SCORE_STR = "9999999"
End If

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

For i = 1 To 10
	enemy.enemies = New enemies ;make 10 enemies (we'll set their variables later)
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

game_jumps_level(1) = LoadImage("GfxRes/game-jumps-level1.bmp")
game_jumps_level(2) = LoadImage("GfxRes/game-jumps-level2.bmp")
game_jumps_level(3) = LoadImage("GfxRes/game-jumps-level3.bmp")
game_jumps_level(4) = LoadImage("GfxRes/game-jumps-level4.bmp")
game_jumps_level(5) = LoadImage("GfxRes/game-jumps-level5.bmp")
For i = 1 To 5
	MaskImage game_jumps_level(i),255,0,255	;mask the images for each level block, so that MAGENTA (255,0,255) is the transparent colour
Next

;notice in the following code, we're loading (and MASKING) graphics in exactly the same way as before, but into regular variables instead of arrays.
;this next bit is all animations
game_player = LoadAnimImage("GfxRes/player-ship.bmp",80,80,0,72) ;load in the 'sprite' for the player ship
MaskImage game_player,255,0,255 ;mask the images for the player ship, so that MAGENTA (255,0,255) is the transparent colour
game_enemy1 = LoadAnimImage("GfxRes/enemy1-ship.bmp",64,64,0,72) ;load in the 'sprite' for the enemy ship (type 1)
MaskImage game_enemy1,255,0,255 ;mask the images for the enemy ship (type 1), so that MAGENTA (255,0,255) is the transparent colour
game_bullet_player = LoadAnimImage("GfxRes/bullet-player.bmp",10,10,0,6)	; the blue player bullet animation
MaskImage game_bullet_player,255,0,255 ;mask the images
game_bullet_enemy1 = LoadAnimImage("GfxRes/bullet-enemy1.bmp",10,10,0,6)	; the orange enemy bullet animation
MaskImage game_bullet_enemy1,255,0,255 ;mask the images
game_explosion = LoadAnimImage("GfxRes/game-explosion.bmp",100,100,0,25)	; the explosion, as done by Lennart's MkExpl 3.0
MaskImage game_explosion,255,0,255 ;mask the images
game_scorefont = LoadAnimImage("GfxRes/game-scorefont.bmp",18,14,0,11)		; the score font used in the game
MaskImage game_scorefont,255,0,255 ;mask the images
menu_scorefont = LoadAnimImage("GfxRes/menu-scorefont.bmp",18,14,0,11)		; the score font used on the menu
MaskImage menu_scorefont,255,0,255 ;mask the images
;these are all plain single-frame pictures
;menu piccies
menu_logo = LoadImage("GfxRes/menu-logo.bmp")			;the CELESTIAL RIFT logo
menu_ship1 = LoadImage("GfxRes/menu-ship1.bmp")			;the big player-type ship
menu_ship2 = LoadImage("GfxRes/menu-ship2.bmp")			;the big enemy type ship
menu_guildhall = LoadImage("GfxRes/menu-guildhall.bmp")	;the GUILDHALL message at the top
menu_thanks = LoadImage("GfxRes/menu-thanks.bmp")		;the THANKS message at the bottom
menu_credit = LoadImage("GfxRes/menu-credit.bmp")		;the CREDITS message below the logo
menu_start = LoadImage("GfxRes/menu-start.bmp")			;the "PRESS FIRE TO START" caption
menu_quit = LoadImage("GfxRes/menu-quit.bmp")			;the "(ESCAPE TO QUIT)" caption
menu_hiscore = LoadImage("GfxRes/menu-hiscore.bmp")		;the "HI SCORE" caption
menu_lastscore = LoadImage("GfxRes/menu-lastscore.bmp")	;the "LAST SCORE" caption
;game piccies
game_background = LoadImage("GfxRes/game-bg.bmp") 			;the sexy blue background behind the stars
game_radar = LoadImage("GfxRes/game-radar.bmp")				;the radar box
game_shields = LoadImage("GfxRes/game-shields.bmp")			;the shields box
game_jumps = LoadImage("GfxRes/game-jumps.bmp")				;the jumps box
game_cloak = LoadImage("GfxRes/game-cloak.bmp")				;the cloak box
game_boosters = LoadImage("GfxRes/game-boosters.bmp")		;go on.. guess.. it's the boosters box
game_levels = LoadImage("GfxRes/game-levels.bmp")			;this is the big RED-to-GREEN bar used on the cloak, shields and booster's boxes.
game_score = LoadImage("GfxRes/game-score.bmp")				;the score box
game_hiscore = LoadImage("GfxRes/game-hiscore.bmp")			;the hi score box
game_gameover = LoadImage("GfxRes/game-gameover.bmp")		;the game over logo, which I tend to see a lot of.. :(
game_paused = LoadImage("GfxRes/game-paused.bmp")			;the paused logo
game_player_dot = LoadImage("GfxRes/player-radardot.bmp")	;radar dots for the player, enemy and bonus icons respectively
game_enemy1_dot = LoadImage("GfxRes/enemy1-radardot.bmp")
game_icon_dot = LoadImage("GfxRes/icon-radardot.bmp")
MaskImage menu_ship1,255,0,255			;mask all the images.
MaskImage menu_ship2,255,0,255			;notice that it doesn't matter what order you do the masks in!
MaskImage menu_credit,255,0,255
MaskImage menu_guildhall,255,0,255		;by the way, if when testing your program, you get the error message "IMAGE DOES NOT EXIST" at this point
MaskImage menu_thanks,255,0,255			;in the program, it's because the LOADIMAGE command above didn't find the file properly.
MaskImage menu_start,255,0,255			;This is usually either because; 1 - You've typed the wrong filename in the LOADIMAGE bit, or
MaskImage menu_quit,255,0,255			;2 - You've got the variable name wrong in the MASKIMAGE bit.
MaskImage menu_hiscore,255,0,255
MaskImage menu_lastscore,255,0,255		;you could perform some checks at the end of loading all the images, so that you know they've all been loaded
MaskImage game_radar,255,0,255			;successfully. If they haven't then you could end the program properly and tell the user that they've got
MaskImage game_shields,255,0,255		;corrupt or missing files!
MaskImage game_jumps,255,0,255
MaskImage game_cloak,255,0,255			;as you'll see, I haven't bothered! :)
MaskImage game_boosters,255,0,255
MaskImage game_score,255,0,255
MaskImage game_hiscore,255,0,255
MaskImage game_background,255,0,255
MaskImage game_gameover,255,0,255
MaskImage game_paused,255,0,255
MaskImage game_levels,255,0,255

;Right! That's it, we've set up *everything* we're going to need from outside the program.
menu_loop() ;start the proper program loop by 'calling' the function called "menu_loop()" - which, conveniently, is just coming up!!!

;this function keeps the menu loop going.. It starts playing tunes and sets a couple of variables. 
;Then it goes into a never ending loop which carries out a sequence of checks and function calls.
Function menu_loop() ;Start of the Function whose name is "menu_loop()"
	FLAG_MENUON = 1 ;tells the program that the menu is running, for use in the REPEAT..FOREVER statement in a sec..
	menu_ship_hor = (SCREEN_WIDTH/2)-(ImageWidth(menu_ship1)/2) ;this sets the starting horizontal position of the big ship on the menu
	menu_ship_ver = SCREEN_HEIGHT+1	;this sets the vertical starting position of the big ship on the menu at one pixel off the bottom of the screen.
									;notice that images are (by default) handled from the TOP-LEFT pixel, so, horizontally, I offset the value
									;to the left, by half the width of the ship image.
	menu_ship_type = 1				;set up some initial values for these counters
	menu_frame = 1					
	menu_start_stat = 0
	menu_accept_quit = 0
	
	Repeat		;a REPEAT.. FOREVER loop will carry out the code in between for as long as the program is running..
		If FLAG_MENUON = 1 Then	;If the FLAG_MENUON flag is set to 1 then "do" the following..
			menu_loop_update()	;call function "menu_loop_update()"
		Else									;if the FLAG_MENUON flag wasn't set to 1 then "do" the following..
			SCORE_FILE = WriteFile("rift.dat")		;Open file "rift.dat" for WRITing (using the variable "SCORE_FILE" as a reference number
			WriteLine SCORE_FILE,Str(HI_SCORE)		;Write the value of HI_SCORE (as a STRing) into the file
			WriteLine SCORE_FILE,Str(LAST_SCORE)	;Write the value of LAST_SCORE (as a STRing) into the file
			CloseFile(SCORE_FILE)					;Close the file, as we've finished with it
			End										;END the program
		End If
	Forever	;end of the REPEAT.. FOREVER loop
End Function ;End of the function

;"menu_loop_update()" checks for key-presses, alters position coordinates on the screen
;and various variables/flags for use in "menu_draw_update()" and the game functions too.
;in fact, you'll see that this has the same organisational structure as the proper game loop (albeit much less complex!)
Function menu_loop_update()
	frames = WaitTimer(timer) ;returns a value to "frames" for how many video screen refreshes (The MHz of your monitor) occured since the last call to our timer
	For i = 1 To frames	;update the screen positions for "frames" number of changes.. This enables drawing frames to be skipped on slower machines.
		If KeyDown(KEY_QUIT)				;If the user presses the "QUIT" button on the keyboard, then..
			FlushKeys						;(when using SCAN CODES, flush the keyboard buffer after each successful "Have they pressed a particular key" question.)

			If menu_accept_quit = 1 Then	;If the variable "menu_accept_quit" is 1, then set the FLAG_MENUON to 0.
				FLAG_MENUON = 0				;back in the "menu_loop()" function, this will cause the program to end!
			End If
		End If

		If KeyDown(KEY_FIRE)				;If the user presses the "FIRE" button on the keyboard, then..
			FlushKeys
			FLAG_DEBUG = 0					
			FLAG_GAMESTARTER = 1
			hypercount = 102
			FLAG_PAUSE = 0
			game_loop()							;call the function called "game_loop()" (the game will run, but once it has ended, we'll find ourselves back here...)
			
			menu_ship_ver = SCREEN_HEIGHT+1
			menu_frame = 1
			menu_start_stat = 0
			menu_accept_quit = 0				;by setting this to 0, we'll stop the program ending by any surplus "QUIT" keystrokes carrying over from the game, by
												;using the menu_frame counter at the end of this FOR loop.
			FLAG_PAUSE = 0
			If PLAYER_SCORE > HI_SCORE Then		;checks to see if the PLAYER_SCORE from the last game is higher than the currently held "HI SCORE", if it is, then
				HI_SCORE = PLAYER_SCORE			;that PLAYER_SCORE becomes the new HI SCORE!
			End If
			LAST_SCORE = PLAYER_SCORE			;the LAST SCORE is always set to the PLAYER SCORE from the last game! :)
			FlushKeys
		End If
		If KeyDown(KEY_DEBUG)				;if the user presses the "DEBUG" button on the keyboard, then..
			FlushKeys
			FLAG_DEBUG = 1						;this is all exactly the same as "FIRE" above, but now FLAG_DEBUG is set to 1.
			FLAG_GAMESTARTER = 1				;later on, you'll see that we use this to turn on, or off, some overlayed displays in the game loop!
			hypercount = 102
			FLAG_PAUSE = 0
			game_loop()
			menu_ship_ver = SCREEN_HEIGHT+1
			menu_frame = 1
			menu_start_stat = 0
			menu_accept_quit = 0
			FLAG_PAUSE = 0
			If PLAYER_SCORE > HI_SCORE Then
				HI_SCORE = PLAYER_SCORE	
			End If
			LAST_SCORE = PLAYER_SCORE	
			FlushKeys
		End If			
		If KeyDown(KEY_SAVESCREEN)			;if the user presses the "SAVESCREEN" button on the keyboard, then..
			FlushKeys
			FLAG_SAVESCREEN = 1					;just sets FLAG_SAVESCREEN to 1. You'll see this used in the next function.
		End If
		If menu_ship_ver > (-50 - ImageHeight(menu_ship1)) ;if the bottom of the (largest) ship picture gets to 50 pixels above the top of the screen
			Select menu_ship_type	;A SELECT..CASE statement is like an IF statement, but only performs 1 check, i.e.: "What is the value of I?"
				Case 1	;if "menu_ship_type" is 1 then..
					menu_ship_ver = menu_ship_ver - .3 ;how fast menuship1 moves up the screen
				Case 2	;if "menu_ship_type" is 1 then..
					menu_ship_ver = menu_ship_ver - .8 ;how fast menuship2 moves up the screen
			End Select
		Else
			menu_ship_ver = SCREEN_HEIGHT+1	;reset the vertical position so that the top of the ship is at the bottom of the screen
			menu_ship_hor = Rnd(0,SCREEN_WIDTH-ImageWidth(menu_ship1)) ;create a random horizontal position
			menu_ship_type = Int(Rnd(1,2)) ;randomly choose between values 1 or 2
		End If
		menu_frame = menu_frame + 1		;increase the value of "menu_frame" by 1
		If menu_frame = 25 Then			;if the value of "menu_frame" gets to 25 then reset it to 1
			menu_frame = 1
			menu_accept_quit = 1			;now we've turned the user's ability to press the "QUIT" button back on (see the IF statement in the KeyDown(KEY_QUIT) statement above!
			If menu_start_stat = 1 Then	;this bit just switches the value of "menu_start_stat" between 1 and 0, i.e. :If it's 1, then make it 0, if it's 0, then make it 1 etc.
				menu_start_stat = 0		;this will be used in the next function to flash the "PRESS FIRE TO START" caption on and off!
			Else
				menu_start_stat = 1
			End If
		End If
	Next
	If HI_SCORE < 10 Then										;remember this from before? I was saying it could've been done in a function?
		HI_SCORE_STR = "000000"+Str(HI_SCORE)					;exactly the same code.. (just converts the number 103 to the string "0000103")
	ElseIf HI_SCORE >=10 And HI_SCORE < 100 Then
		HI_SCORE_STR = "00000"+Str(HI_SCORE)
	ElseIf HI_SCORE >=100 And HI_SCORE < 1000 Then
		HI_SCORE_STR = "0000"+Str(HI_SCORE)
	ElseIf HI_SCORE >=1000 And HI_SCORE < 10000 Then
		HI_SCORE_STR = "000"+Str(HI_SCORE)
	ElseIf HI_SCORE >=10000 And HI_SCORE < 100000 Then
		HI_SCORE_STR = "00"+Str(HI_SCORE)
	ElseIf HI_SCORE >=100000 And HI_SCORE < 1000000 Then
		HI_SCORE_STR = "0"+Str(HI_SCORE)
	ElseIf HI_SCORE >=100000 And HI_SCORE < 10000000 Then
		HI_SCORE_STR = Str(HI_SCORE)
	Else
		HI_SCORE_STR = "9999999"
	End If
	If LAST_SCORE < 10 Then
		LAST_SCORE_STR = "000000"+Str(LAST_SCORE)
	ElseIf LAST_SCORE >=10 And LAST_SCORE < 100 Then
		LAST_SCORE_STR = "00000"+Str(LAST_SCORE)
	ElseIf LAST_SCORE >=100 And LAST_SCORE < 1000 Then
		LAST_SCORE_STR = "0000"+Str(LAST_SCORE)
	ElseIf LAST_SCORE >=1000 And LAST_SCORE < 10000 Then
		LAST_SCORE_STR = "000"+Str(LAST_SCORE)
	ElseIf LAST_SCORE >=10000 And LAST_SCORE < 100000 Then
		LAST_SCORE_STR = "00"+Str(LAST_SCORE)
	ElseIf LAST_SCORE >=100000 And LAST_SCORE < 1000000 Then
		LAST_SCORE_STR = "0"+Str(LAST_SCORE)
	ElseIf LAST_SCORE >=100000 And LAST_SCORE < 10000000 Then
		LAST_SCORE_STR = Str(LAST_SCORE)
	Else
		LAST_SCORE_STR = "9999999"
	End If
	menu_draw_update() ;call the function "menu_draw_update" which draws all these new things on the screen
End Function

;"menu_draw_update()" just draws things on your monitor, using values set and altered in the last function (menu_loop_update).
Function menu_draw_update()
	SetBuffer BackBuffer()	;draw all of the following to the backbuffer() which is an area video memory which is not shown on screen.
							;the idea is that you draw everything here, then SHOW it to the user when the full picture is complete.
							
	ClsColor 0,0,0		;changes the CLearScreen colour to black (0,0,0)
						;Color commands use the 3 parameters as RED value, GREEN value, BLUE value
						;which is how pixel colours are made up on the monitor. If you're familiar with
						;any PC Paint Packages, you'll probably be quite familiar with this.
	Cls					;CLears the Screen
	
	;this next section draws all of the pictures on to the backbuffer() at the specified coordinates.
	;notice that the pictures are drawn in sequence, with the backmost things drawn first and the foremost things drawn last
	;kind of like making a collage!
	DrawImage menu_logo,(SCREEN_WIDTH/2)-(ImageWidth(menu_logo)/2),0	;this draws the menu_logo image at "x","y"
						;I've used EQUATIONS to make up x and y for all the images in this project, so that when you change
						;the resolution values (back at the beginning of the code) everything still appears at the correct position
						;on the screen. If you know what resolution you'll be working at, then you can quite happily put x and y as
						;ordinary numbers in here.
						;For example, at 800*600, the menu_logo image will be drawn at:
						;SCREEN_WIDTH = 800
						;800/2 = 400
						;ImageWidth(menu_logo) is the width (in pixels) of this particular image (which is 547 pixels)
						;547/2 = 273.5
						;(800 - 273.5 = 126.5)
						;So, menu_logo would be drawn at 127,0 (because Blitz requires whole number coordinates, it will automatically
						;round up 126.5 to be 127.)
	DrawImage menu_guildhall,(SCREEN_WIDTH/2)-(ImageWidth(menu_guildhall)/2),5
	DrawImage menu_credit,(SCREEN_WIDTH/2)-(ImageWidth(menu_credit)/2),200
	DrawImage menu_quit,(SCREEN_WIDTH/2)-(ImageWidth(menu_quit)/2),SCREEN_HEIGHT-75
	DrawImage menu_hiscore,10,SCREEN_HEIGHT-45
	DrawImage menu_lastscore,(SCREEN_WIDTH)-282,SCREEN_HEIGHT-45

	;this next bit builds each number in the HI_SCORE_STR and LAST_SCORE_STR out of the pictures in the score font
	For i = 1 To 7	;there are 7 numbers in the STRING.
		DrawImage menu_scorefont,100+((i-1)*24),SCREEN_HEIGHT-44,Int(Mid(HI_SCORE_STR,i,1))
				;let's break this up.. firstly "100+((i-1)*24"
				;the leftmost number in the string first (i = 1)
				;x position = 100+((i-1)*24
				;=100+((0)*24)
				;=100+(0)
				;=100
				;the next number in the string (i = 2)
				;x position = 100+((i-1)*24
				;=100+((1)*24)
				;=100+(24)
				;=124
				;the next number in the string (i = 3)
				;x position = 100+((i-1)*24
				;=100+((2)*24)
				;=100+(48)
				;=148
				;etc... this offsets each number in the seven digit long number across the screen!
				
				;Okay.. Now the "Int(Mid(HI_SCORE_STR,i,1))" bit..
				;"Mid(HI_SCORE_STR,i,1)" takes one character, starting at "i" from the string "HI_SCORE_STR".
				;For example, if i=4 and HI_SCORE_STR = "0023560" then
				;"Mid(HI_SCORE_STR,i,1)" would give us "3".
				;the "INT" bit turns the ASCII string value "3" into an integer number 3.

				;Finally, the "Int(Mid(HI_SCORE_STR,i,1))" bit is the frame number in an Animation Image (remember LoadAnimImage before?)
				;Although the "menu_score_font" picture isn't technically an animation, this is a clever alternate use for AnimImages, where you can
				;simply calculate which image to show, by it's "frame" position within the animation.
				;if you check the file "menu-scorefont.bmp" in Paint Shop Pro, you'll see that the 3rd image from the left is, in fact, a pictorial number 3.
		DrawImage menu_scorefont,(SCREEN_WIDTH)-167+((i-1)*24),SCREEN_HEIGHT-44,Int(Mid(LAST_SCORE_STR,i,1))
				;exactly the same thing, but using LAST_SCORE_STR instead of HI_SCORE_STR!
	Next
	DrawImage menu_thanks,(SCREEN_WIDTH/2)-(ImageWidth(menu_thanks)/2),SCREEN_HEIGHT-15

	;depending on the value set earlier in "menu_loop_update()" this draws one of the two ships at the relevant coordinates on-screen
	;menu_ship_hor is the x position, and is set once per "scroll up the screen" in menu_loop_update().
	;menu_ship_ver is the y position, which is repeatedly changed in menu_loop_update() to scroll the ships up the screen!
	Select menu_ship_type
		Case 1
			DrawImage menu_ship1,menu_ship_hor,menu_ship_ver
		Case 2
			DrawImage menu_ship2,menu_ship_hor,menu_ship_ver
	End Select
	
	;Remember before, I was saying that "menu_frame" would be used to flash the "START GAME" caption on and off?
	;when "menu_frame" got to 25, the variable "menu_start_stat" was flipped between 1 or 0
	If menu_start_stat = 1 Then
		;if the variable "menu_start_stat" = 1 then draw the caption. If it isn't, ignore drawing the caption.
		;On and off, on and off. Simple as that! This technique will be used again later for the PAUSE and GAME_OVER captions!
		DrawImage menu_start,(SCREEN_WIDTH/2)-(ImageWidth(menu_start)/2),(SCREEN_HEIGHT/2)+(ImageHeight(menu_start)*4)
	End If

	;Remember when we checked to see if the user had pressed the "SAVESCREEN" button, earlier?
	;if they did, we set FLAG_SAVESCREEN to 1. Now this is where that takes effect..
	If FLAG_SAVESCREEN=1 Then
		SaveBuffer (BackBuffer(),"CRMenuScreen.bmp")
			;the SaveBuffer command outputs the contents of the named buffer (in this case "BackBuffer()") to the named file, in a BMP format.
		FLAG_SAVESCREEN = 0
			;after we've saved the screen, we reset the FLAG to 0, so that when we come back for the next "menu_draw_update", it won't save
			;another picture. (i.e.: it only saves 1 picture for each keypress of the "SAVESCREEN" button!)
			;This is useful for getting working screenshots of your project to impress your mates!! :)
	End If 
	Flip	;finally, FLIP everything on the backbuffer() and show it on the frontbuffer(), i.e.: your monitor!!
End Function

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
			FlushKeys				;if FLAG_GAMEON isn't 1, then instead of ENDing the program (as we did from the menu), this time
			Exit					;we EXIT the function, which effectively takes us back to the line after "game_loop()" was originally
									;called, back in "menu_loop_update()"
		End If
	Forever
End Function

;As described a minute ago, this function sets up the game variables as they should be
;at the start of every game, i.e.: Player Shields are full, the score is 0 etc..
Function game_initialise()
	PLAYER_SCORE = 0
	PLAYER_TIME = 0
	PLAYER_ANGLE = 0
	PLAYER_SPEED = 0
	PLAYER_SHIELD = 192			;the width of the game_level image is 192. Rather than working out a percentage of 100, it's much
	PLAYER_BOOSTERS = 192		;easier to use the width of the image on such BAR type displays.
	PLAYER_CLOAK = 192			
	PLAYER_JUMPS = 5
	gameovercounter = 0
	player_explosion_frame = 0
	game_player_randomize()			;calls a function that randomizes a player's position on the map
	game_stars_randomize()			;calls a function that randomizes the star positions

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
														
			If gameovercounter = 0 Then					;if the game is still in progress, i.e.: "gameovercounter" = 0 (the player is still alive) then
														;check for the following keypresses from the user - otherwise there's no point!
														
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
			End If		;end of the "If the game is still running ('gameovercounter')" IF Statement
	
			For icon.icons=Each icons				;the "icons" don't move from their randomly set x,y position, so all we need to do for them is
				icon\frame = icon\frame+.5			;update their animation frame number. There are 6 frames, and we're incrementing at 0.5 frames per update.
				If Int(icon\frame) = 6 Then			;in real terms, this means we'll be updating the frame number every 2 updates.
					icon\frame = 0					;Because the icon animations are simple 0 to 5 cycling animations, when the frame number reaches 6, we flip it back to 0
				End If								;to start the sequence again.
			Next
			
			;the following code section is probably the most complex in the game, because it's the Enemy Artificial Intelligence
			;Reader's of a nervous disposition might want to skip this - I know I did! :)))
			For enemy.enemies=Each enemies		;for each version of the "enemies" type..

				If enemy\exp_frame > 0 Then						;remember before, I said we were going to blow up the player and enemies, just by setting a value?
					enemy\exp_frame = enemy\exp_frame + .5		;well, this is it! if this "enemies" exp_frame is greater than 0, then start increasing the Explosion
				End If											;animation frame number (notice the same "update every two screen refreshes" technique?)

				If cloakon = 0		;if the player's CLOAKing device is off, then do the following bit. Otherwise, ignore it..
					;indeed. This next bit makes the enemies home in on you and shoot you to peices. If your cloak is on, they can't see you, so don't do the intelligence tests

					If enemy\exp_frame = 0 Then			;as long as this version of "enemies" isn't busy exploding..

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
						
						;so, now our enemies know where to point themselves, let's not allow them to just mindlessly run into you!
						If temp_hyp# < Rnd(1,1.5) And gameovercounter = 0 Then	;if the distance between you and them is less than (a small, but random distance)
																				;and the player is alive..
							If enemy\bulletlimiter = 0 Then								;exactly like the player shooting, the enemie's bullet needs limiting
								enemy\bulletlimiter = 1
								createbullet(enemy\x,enemy\y,enemy\angle,enemy\speed,2)
							End If
							whichway = Rand(1,100)							;the enemy comes at you, shoots, then flys off in the opposite direction, however,
							If whichway > 50 Then							;to give it a slightly random element, they can fly off to the left or the right
								enemy\dest_angle = enemy\dest_angle + 180	;of themselves. This gives them a slightly less "patterny" feel.
							Else
								enemy\dest_angle = enemy\dest_angle - 180
							End If
							enemy\dist_travelled = enemy\dist_to_go			;cancel their "distance still to travel before changing course" action by
																			;prematurely setting the distance travelled to the distance to go value.
																			
							slowdown = 0			;if the "enemies" is near enough, we don't want him to stop as he turns away from you.
						Else
							slowdown = 1			;at greater distances, we do want the "enemies" to stop, turn and then fly at you.. See later..
						End If
	
						;this is the enemy bullet limiter. Essentially, if an enemy fire's a bullet then it can't fire another one until this
						;counter reaches 10. (in another 10 frames, which at 50 fps would be in a fifth of a second.
						If enemy\bulletlimiter > 0 Then
							enemy\bulletlimiter = enemy\bulletlimiter + 1
							If enemy\bulletlimiter = 10 Then ;value essentially sets the enemies fire-rate (the lower the number, the faster the fire rate!)
								enemy\bulletlimiter = 0	;allows the enemy to fire another bullet.
							End If
						End If
						
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

						;this bit is working out how far the "enemies" has travelled since the last update and adds it to the "dist_travelled" property
						dist_x# = dist_x# + (enemy\speed*(Sin(enemy\angle)/2))
						dist_y# = dist_y# - (enemy\speed*(Cos(enemy\angle)/2))
						enemy\dist_travelled = enemy\dist_travelled + Sqr((dist_x#*dist_x#) + (dist_y#*dist_y#))
			
						;Before, we said that the enemy should travel a certain distance before he retargets and homes in on you.
						;this next section deals with the event once the "enemies" has travelled that distance!
						If enemy\dist_travelled > enemy\dist_to_go Then
							If slowdown = 1 Then				;depending on the value of "slowdown", the "enemies" will (or will not) slow down as he turns to face you.
								If enemy\speed > 0 Then
									enemy\speed = enemy\speed - (5*INCR_SLOW)
								Else
									enemy\speed = 0
								End If
							End If
							;based on the decision made a second ago, about which was the quickest way to rotate to face you fastest..
							If enemy\rotate_angle < 0 Then
								If Not enemy\angle = enemy\dest_angle Then
									enemy\angle = enemy\angle + 5				;rotate the "enemies" right until its angle is correct
									If enemy\angle >=360 Then
										enemy\angle = 360-enemy\angle			;flip the angle back to 0 when it gets to 360
									End If
								Else
									enemy\dist_to_go = Rnd(ENEMY_MIN_DIST,ENEMY_MAX_DIST)	;once the angle is correct, randomly create another distance to
									enemy\dist_travelled = 0								;travel until the next check, and reset the "travelled" distance to 0
								End If
							Else
								If Not enemy\angle = enemy\dest_angle Then
									enemy\angle = enemy\angle - 5				;exactly the same, but rotating left
									If enemy\angle < 0 Then
										enemy\angle = enemy\angle+360
									End If
								Else
									enemy\dist_to_go = Rnd(ENEMY_MIN_DIST,ENEMY_MAX_DIST)
									enemy\dist_travelled = 0
								End If
							End If
							
						Else	;if the "enemies" has not reached his destination "distance to go" value then
						
							If slowdown = 1 Then
								If enemy\speed < enemy\maxspeed Then				;speed up the enemy until he reaches maximum speed
									enemy\speed = enemy\speed + (5*INCR_SPEED)
								Else
									enemy\speed = enemy\maxspeed
								End If
							Else													;if he's within a certain distance, only allow him to
								If enemy\speed < enemy\maxspeed/2 Then				;fly at half speed
									enemy\speed = enemy\speed + (5*INCR_SPEED)
								Else
									enemy\speed = enemy\maxspeed / 2
								End If
							End If
						End If
						
					End If	;end of the "is enemy in the middle of exploding" IF statement
					
				End If	;end of the "is the Player's CLOAKing device on?" IF statement
				
				enemy\x = enemy\x + (enemy\speed*(Sin(enemy\angle)/2))		;update the "enemies" x and y positions, relative to its Speed and Angle
				enemy\y = enemy\y - (enemy\speed*(Cos(enemy\angle)/2))		;exactly the same code as the Player's positions, earlier
				If enemy\x < 0 Then
					enemy\x = (enemy\x + GAME_AREA_X)
				End If
				If enemy\x > GAME_AREA_X Then
					enemy\x = (enemy\x - GAME_AREA_X)
				End If
				If enemy\y < 0 Then
					enemy\y = (enemy\y + GAME_AREA_X)
				End If
				If enemy\y > GAME_AREA_Y Then
					enemy\y = (enemy\y - GAME_AREA_Y)
				End If
				
			Next	;end of the ENEMIES for loop
			
			
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
					game_stars_randomize()	;half way through the Hyperjump sequence, the "game_starts_randomize()" function is called, which changes all the star positions	
											;and depths, to give the impression we've moved to a completely different part of space
				End If
				If hypercount/10 = Int(hypercount/10) Then
					game_player_randomize()		;every 10 updates, the function "game_player_randomize()" is called. This randomly jumps the player around the map
												;and confuses the hell out of the bad guys! ;))
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
			
			;this is the player's bullet limiting code, which follows exactly the same logic as the "enemies" code, earlier
			If bulletlimiter > 0 Then
				bulletlimiter = bulletlimiter + 1
				If bulletlimiter = 10 Then ;value essentially sets the fire-rate (the lower the number, the faster the fire rate!)
					bulletlimiter = 0
				End If
			End If
			
			;calculate player explosions, using the "one frame advance every two screen updates" idea from before
			If player_explosion_frame > 0 Then
				player_explosion_frame = player_explosion_frame + .5
				If player_explosion_frame > 26 Then
					player_explosion_frame = 26
				End If
			End If
			
			;this bit of code is fired when the player dies (gameovercounter is set to 1)
			;and acts exactly the same as "menu_frame", in that it allows us to flash the words "GAME OVER" on and off!
			If gameovercounter > 0 Then
				PLAYER_SPEED = PLAYER_SPEED - INCR_SLOW		;slow the player speed gradually to 0 when the game ends
				If PLAYER_SPEED <= 0 Then					;a nice "That's the end for you" statement if you die in mid-battle!
					PLAYER_SPEED = 0
				End If
				gameovercounter = gameovercounter + 1
				If gameovercounter = 25 Then
					gameovercounter = 1
					If gameoverstat = 1 Then
						gameoverstat = 0
					Else
						gameoverstat = 1
					End If
				End If
			Else
				PLAYER_SCORE = PLAYER_SCORE + 1		;if the game is in progress still, then increase the PLAYER_SCORE by 1 every screen update
													;this acts as a kind of survival bonus for the more defensive player
			End If
			
			;here's that copied code again, which turns 231 into "0000231", this time
			;using PLAYER_SCORE and HI_SCORE as opposed to LAST_SCORE
			If PLAYER_SCORE < 10 Then
				PLAYER_SCORE_STR = "000000"+Str(PLAYER_SCORE)
			ElseIf PLAYER_SCORE >=10 And PLAYER_SCORE < 100 Then
				PLAYER_SCORE_STR = "00000"+Str(PLAYER_SCORE)
			ElseIf PLAYER_SCORE >=100 And PLAYER_SCORE < 1000 Then
				PLAYER_SCORE_STR = "0000"+Str(PLAYER_SCORE)
			ElseIf PLAYER_SCORE >=1000 And PLAYER_SCORE < 10000 Then
				PLAYER_SCORE_STR = "000"+Str(PLAYER_SCORE)
			ElseIf PLAYER_SCORE >=10000 And PLAYER_SCORE < 100000 Then
				PLAYER_SCORE_STR = "00"+Str(PLAYER_SCORE)
			ElseIf PLAYER_SCORE >=100000 And PLAYER_SCORE < 1000000 Then
				PLAYER_SCORE_STR = "0"+Str(PLAYER_SCORE)
			ElseIf PLAYER_SCORE >=100000 And PLAYER_SCORE < 10000000 Then
				PLAYER_SCORE_STR = Str(PLAYER_SCORE)
			Else
				PLAYER_SCORE_STR = "9999999"
			End If
			If PLAYER_SCORE >= HI_SCORE Then
				HI_SCORE = PLAYER_SCORE
				If HI_SCORE < 10 Then
					HI_SCORE_STR = "000000"+Str(HI_SCORE)
				ElseIf HI_SCORE >=10 And HI_SCORE < 100 Then
					HI_SCORE_STR = "00000"+Str(HI_SCORE)
				ElseIf HI_SCORE >=100 And HI_SCORE < 1000 Then
					HI_SCORE_STR = "0000"+Str(HI_SCORE)
				ElseIf HI_SCORE >=1000 And HI_SCORE < 10000 Then
					HI_SCORE_STR = "000"+Str(HI_SCORE)
				ElseIf HI_SCORE >=10000 And HI_SCORE < 100000 Then
					HI_SCORE_STR = "00"+Str(HI_SCORE)
				ElseIf HI_SCORE >=100000 And HI_SCORE < 1000000 Then
					HI_SCORE_STR = "0"+Str(HI_SCORE)
				ElseIf HI_SCORE >=100000 And HI_SCORE < 10000000 Then
					HI_SCORE_STR = Str(HI_SCORE)
				Else
					HI_SCORE_STR = "9999999"
				End If
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
	TileBlock game_background,0,0	;This acts as a CLear Screen, even if the Resolution is set bigger than "game_background"'s image size
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

			;this line uses the command "ImagesCollide" to place a value in the variable "checkcol" which we didn't declare as a Global
			;variable earlier. This is because it is only used once and then discarded.
			;Images collide requires the following parameters (information):
			;1. image1 name
			;2. image1 x position
			;3. image1 y position
			;4. image1 animation frame number
			;5. image2 name
			;6. image2 x position
			;7. image2 y position
			;8. image2 animation frame number
			;if the two images collide (at least one non-transparent pixel from each image is touching eachother) then
			;"checkcol" will equal 1, otherwise it will equal 0 (no non-transparent pixels were touching)
			checkcol = ImagesCollide(game_icons(icon\style), (((SCREEN_WIDTH/2)-ImageWidth(game_icons(icon\style))/2) - (PLAYER_X - icon\x)), (((SCREEN_HEIGHT/2)-ImageWidth(game_icons(icon\style))/2) - (PLAYER_Y - icon\y)),Int(icon\frame),game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2,(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2,game_player_frame)
			
			;this IF statement says what to do when the two images *have* collided (and the game is still in progress, i.e.: gameovercounter isn't 1)
			If checkcol=1 And gameovercounter = 0 Then
			
				Select icon\style	;back to SELECT..CASE statements, "Which type of icon has the player just collected?"
					Case 1
						
						PLAYER_BOOSTERS = PLAYER_BOOSTERS + BONUS_BOOSTERS	;add some extra points to the PLAYER_BOOSTERS value..
						If PLAYER_BOOSTERS > 192 Then						;and check that it isn't full
							PLAYER_BOOSTERS = 192
						End If
					Case 2
						PLAYER_SHIELD = PLAYER_SHIELD + BONUS_SHIELD		;add some points to the PLAYER_SHIELD
						If PLAYER_SHIELD > 192 Then	
							PLAYER_SHIELD = 192
						End If
					Case 3
						PLAYER_CLOAK = PLAYER_CLOAK + BONUS_CLOAK			;add some points to the PLAYER_CLOAK
						If PLAYER_CLOAK > 192 Then	
							PLAYER_CLOAK = 192
						End If
					Case 4
						PLAYER_JUMPS = PLAYER_JUMPS + 1			;this gives the PLAYER and extra Hyperjump icon
						If PLAYER_JUMPS > 5 Then				;the maximum of which is 5, so cap the PLAYER_JUMPS value at 5
							PLAYER_JUMPS = 5
						End If
				End Select
				icon\x = Rnd(0,GAME_AREA_X)							;once collected, an icon just reappears at a random point on the map
				icon\y = Rnd(0,GAME_AREA_Y)
				PLAYER_SCORE = PLAYER_SCORE + SCORE_ICONCOLLECT		;additionally, a player scores some points for collecting an icon
			End If
	    Next	;end of the "icons" FOR loop
	
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
			If enemy\exp_frame = 0 Then
			
				;exactly the same as the icons, relative to the player position. The fact that an enemy moves it's x and y position is irrelevant, because
				;it's new position was already worked out in the last function ("game_loop_update()")
				DrawImage game_enemy1, (((SCREEN_WIDTH/2)-ImageWidth(game_enemy1)/2) - (PLAYER_X - enemy\x)), (((SCREEN_HEIGHT/2)-ImageWidth(game_enemy1)/2) - (PLAYER_Y - enemy\y)),temp_enemy1_frame

				;the same collision detection as an "icons" occurs for each enemy colliding with the player
				checkcol = ImagesCollide(game_enemy1, (((SCREEN_WIDTH/2)-ImageWidth(game_enemy1)/2) - (PLAYER_X - enemy\x)), (((SCREEN_HEIGHT/2)-ImageWidth(game_enemy1)/2) - (PLAYER_Y - enemy\y)),temp_enemy1_frame,game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2,(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2,game_player_frame)

				If checkcol=1 And gameovercounter = 0 Then		;if the two images have collided (this version of "enemies" and the PLAYER)

					PLAYER_SHIELD = PLAYER_SHIELD - DAMAGE_ENEMY_BASH		;subtract some points off the player's SHIELD

					If PLAYER_SHIELD < 0 Then			;if the player's shield is less than 0, then

						PLAYER_SHIELD = 0				;set the player's shield back to it's minimum value
						gameovercounter = 1				;set the "game is still in progress" variable to 1 (i.e.: the game isn't running anymore)
														;in a second, this will start a chain of reaction in "game_loop_update" which make's the player
														;image be replaced by the explosion, followed shortly by the "GAME OVER" caption

						player_explosion_frame = 1		;set the player_explosion_frame to 1 (which in game_loop_update() will start the explosion image sequence)
					End If
					
					
					enemy\energy = enemy\energy - DAMAGE_PLAYER_BASH	;the enemy also suffers damage for this collision
					PLAYER_SCORE = PLAYER_SCORE + SCORE_ENEMYCOLLIDE	;and the player scores some points

					If enemy\energy <= 0 Then					;check to see if the "enemies"'s energy is 0 or less and
						enemy\energy = 0						;cap it at it's minimum value (0)

						enemy\exp_frame = 1			;set the "enemies" exp_frame to 1 (which in game_loop_update() will start the explosion image sequence for this
													;version of "enemies")
					End If
				End If

			Else	;if this "enemies" *was* in the middle of exploding, then
			
				;there are 25 frames in the Explosion animation, so when the explosion frame reaches 26, we don't want to draw *anything* any more!
				If enemy\exp_frame <= 25 Then
				
					;draw frames 1 to 25 at the "enemies" (still moving) postion, but instead of altering the
					;draw position for the Enemy image width/height, we alter it for the Explosion image width/height)
					DrawImage game_explosion,(((SCREEN_WIDTH/2)-ImageWidth(game_explosion)/2) - (PLAYER_X - enemy\x)),(((SCREEN_HEIGHT/2)-ImageHeight(game_explosion)/2) - (PLAYER_Y - enemy\y)),Int(enemy\exp_frame-1)

				Else							;once the explosion has run to it's last frame (it will now be at enemy\frame = 26)..

					enemy\x=Rnd(0,GAME_AREA_X)			;randomize this "enemies"'s position on the map
					enemy\y=Rnd(0,GAME_AREA_Y)
					enemy\energy = 100					;give it it's energy back..
					enemy\angle = Int(Rnd(0,72)) * 5
					enemy\maxspeed = Rnd(20,40)			;reset it's speed abilities
					enemy\speed = 0						;start the new generation of this "enemies" in a static position (as if it's just hyperjumped in!)
					enemy\style = 1
					enemy\exp_frame = 0					;stop it exploding!
					
					enemy\dist_to_go = Rnd(ENEMY_MIN_DIST,ENEMY_MAX_DIST)
					enemy\dist_travelled = 0
					enemy\gen=enemy\gen +1		;remember before, I said that the enemy would be worth more points as it regenerated?
												;here it is! If this was the first time it had been killed, next time will be worth *TWICE* the points
												;if it was the 5th time it had been killed, next time it will be worth *SIX* times the points etc..
												
				End If
			End If		;end of the "is this enemies exploding" IF statement
		Next	;end of the "enemies" FOR loop

		;now we'll do the same for all the bullets on (or off) the screen	
		For i = 0 To bulletnum-1	;this cycles though the array from position 1 to the final bullet number in the array..
									;we use "bulletnum - 1" because there are (for example) 600 possible bullets that are stored
									;in an array as 0, 1, 2, 3 ... 596, 597, 598 and 599
		
			Select bullets(i,4)		;whose bullet is it? remember when we originally DIMmed this Array, I said that 0 was off, 1 was a PLAYER bullet and 2 was an enemy bullet?
				Case 1
				
					;if it's a player bullet then..
					;draw this bullet at it's position, relative to the player, in it's current animation frame
					DrawImage game_bullet_player,(((SCREEN_WIDTH/2)-ImageWidth(game_bullet_player)/2) - (PLAYER_X - bullets(i,0))), (((SCREEN_HEIGHT/2)-ImageWidth(game_bullet_player)/2) - (PLAYER_Y - bullets(i,1))),bullets(i,5)

					;this checks for collisions with this bullet and all the enemies..
					For enemy.enemies = Each enemies
						If enemy\exp_frame = 0 Then		;.. as long as an enemy isn't already exploding..

							;to get the x and y positions for each image, I have just copied and pasted their original DRAWIMAGE parameters into this statement
							;watch out more missing off brackets and values..						
							checkcol = ImagesCollide(game_enemy1, (((SCREEN_WIDTH/2)-ImageWidth(game_enemy1)/2) - (PLAYER_X - enemy\x)), (((SCREEN_HEIGHT/2)-ImageWidth(game_enemy1)/2) - (PLAYER_Y - enemy\y)),temp_enemy1_frame,game_bullet_player,(((SCREEN_WIDTH/2)-ImageWidth(game_bullet_player)/2) - (PLAYER_X - bullets(i,0))), (((SCREEN_HEIGHT/2)-ImageWidth(game_bullet_player)/2) - (PLAYER_Y - bullets(i,1))),bullets(i,5))

							If checkcol = 1 And gameovercounter = 0 Then 	;if the bullet hit the enemy (and the game isn't over) then
								bullets(i,4) = 0											;turn off the bullet (effectively makes it disappear!)
								enemy\energy = enemy\energy - DAMAGE_ENEMY_IS_SHOT				;take off some points from the "enemies"'s shield
								PLAYER_SCORE = PLAYER_SCORE + enemy\gen*SCORE_ENEMY_IS_SHOT		;give the player points for the shot, with the 
																								;"enemies"'s GENeration value as a multiplier!

							End If
							If enemy\energy <= 0 Then					;checks to see if we've just killed the enemy!
								enemy\energy = 0
								enemy\exp_frame = 1						;starts off the enemy explosion chain reaction in "game_loop_update()"
							End If
						End If		;end of the "is enemy already dead" IF statement
					Next	;end of checking through each "enemies" FOR loop
						
				Case 2
				
					;if it's an enemy bullet then..
					;draw this bullet at it's position, relative to the player, in it's current animation frame
					DrawImage game_bullet_enemy1,(((SCREEN_WIDTH/2)-ImageWidth(game_bullet_enemy1)/2) - (PLAYER_X - bullets(i,0))), (((SCREEN_HEIGHT/2)-ImageWidth(game_bullet_enemy1)/2) - (PLAYER_Y - bullets(i,1))),bullets(i,5)

					checkcol = ImagesCollide(game_bullet_enemy1,(((SCREEN_WIDTH/2)-ImageWidth(game_bullet_enemy1)/2) - (PLAYER_X - bullets(i,0))), (((SCREEN_HEIGHT/2)-ImageWidth(game_bullet_enemy1)/2) - (PLAYER_Y - bullets(i,1))),bullets(i,5),game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2,(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2,game_player_frame)

					If checkcol = 1 And gameovercounter = 0 Then					;if the bullet hit the player, and the game isn't already over..

						PLAYER_SHIELD = PLAYER_SHIELD - DAMAGE_PLAYER_IS_SHOT		;subtract some points from the PLAYER's SHIELD

						bullets(i,4) = 0					;turn the bullet off..
					End If

					If PLAYER_SHIELD < 0 Then		;checks to see if the player's SHIELD is less than 0
						PLAYER_SHIELD = 0				;caps the shield at it's minimum (0)

						gameovercounter = 1			;sets the "the game is over" variable to 1

						player_explosion_frame = 1	;starts the player explosion chain reaction back in "game_loop_update"
					End If
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
		
		;as long as the player isn't busy exploding..
		If player_explosion_frame = 0 Then

			If cloakon = 0 Then		;if the "cloaking device" is off, then draw the player every screen update..
				DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2,(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2,game_player_frame

			Else					;if the "cloaking device" is on, only draw the player when the "cloakon" number is EVEN..
									;this is our flickering effect used again..
				If cloakon/2 = Int(cloakon/2) Then
					DrawImage game_player,(SCREEN_WIDTH/2)-ImageWidth(game_player)/2,(SCREEN_HEIGHT/2)-ImageHeight(game_player)/2,game_player_frame
				End If
			End If
		Else
					;if, on the other hand, the player *is* busy exploding, then..
					
			If player_explosion_frame <= 25 Then		;draw frames 1 to 25 of the explosion animation (in the player's position), taking into account
														;the Explosion image size, rather than the player image size..
				DrawImage game_explosion,(SCREEN_WIDTH/2)-ImageWidth(game_explosion)/2,(SCREEN_HEIGHT/2)-ImageHeight(game_explosion)/2,Int(player_explosion_frame-1)
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
	For enemy.enemies=Each enemies
		;this draws each of the icons
		DrawImage game_enemy1_dot,((SCREEN_WIDTH-211)+enemy\x/(GAME_AREA_X/200)),((9)+enemy\y/(GAME_AREA_Y/200))
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
	
	;exactly the same during a "GAME OVER" scenario
	If gameovercounter > 1 And gameoverstat = 1 Then
		DrawImage game_gameover,SCREEN_WIDTH/2-ImageWidth(game_gameover)/2,SCREEN_HEIGHT-((SCREEN_HEIGHT-350)/2+ImageHeight(game_gameover)/2)
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
		Text 0,100, "whichway = " + whichway
		Text 0,120, "SCORE = " + PLAYER_SCORE
		Text 0,140, "SCORESTR = " + PLAYER_SCORE_STR

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
		Text 0,180, "P.E.F. = " + player_explosion_frame
		i = 0
		;write some of the information about each of our enemies at the bottom of the screen.
		;this was incredibly useful when working out the A.I. code, 'cos you could see in "words" what
		;each enemy was doing, even when it wasn't on the screen!
		For enemy.enemies=Each enemies
			Text 20,((20*i)+380),i+1
			Text 40,((20*i)+380),enemy\angle
			Text 80,((20*i)+380),enemy\dest_angle
			Text 110,((20*i)+380),"y="+enemy\dest_opp
			Text 210,((20*i)+380),"x="+enemy\dest_adj
			Text 310,((20*i)+380),"hyp="+enemy\dest_hyp
			Text 430,((20*i)+380),enemy\rotate_angle
			Text 470,((20*i)+380),enemy\energy
			Text 510,((20*i)+380),enemy\exp_frame
			Text 610,((20*i)+380),enemy\gen
			Text (((SCREEN_WIDTH/2)-ImageWidth(game_enemy1)/2) - (PLAYER_X - enemy\x)), (((SCREEN_HEIGHT/2)-ImageWidth(game_enemy1)/2) - (PLAYER_Y - enemy\y)),i+1
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