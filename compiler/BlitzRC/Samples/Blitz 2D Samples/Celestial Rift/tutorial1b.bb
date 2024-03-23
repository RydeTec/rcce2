AppTitle "Celestial Rift" ;what this program will be called in MICROSOFT WINDOWS.

;This makes two CONSTants. These are values which will NOT change at any point in the program, so we set their values now.
Const SCREEN_WIDTH = 800
Const SCREEN_HEIGHT = 600
;This is the code which tells Blitz what Display Resolution to use on the Graphics Card. The "GRAPHICS" command should always be placed before you do
;*anything* image-related in your program
Graphics SCREEN_WIDTH,SCREEN_HEIGHT,0,1	;start the graphics mode at SCREEN_WIDTH by SCREEN_HEIGHT, let Blitz choose the depth (,0) and run full screen (,1)

;the following constants are keyboard "SCAN" codes. Every key on the keyboard has a number. You can get the full list in your Blitz manual.
;by using CONSTants for the keyboard scan codes, it makes it easier to change the keys at a later stage without having to sift though
;the code and changing the numbers in your "IF KeyDown" statements (for keys that are checked a lot!)
Const KEY_QUIT = 1			;(Escape)
Const KEY_SAVESCREEN = 88	;(F12)

Global FLAG_MENUON
Global FLAG_SAVESCREEN = 0

Global timer
Global frames
Global menu_frame

timer = CreateTimer(50) ;create a timer set at 50ms (game speed) - play with this to see how you can increase or decrease the speed of the game.
						;this should be set at a speed which will look near enough the same on *every* PC it will be played on.
						;My PC (a 733MHz PIII with an nVidia GeForce card) will handle upwards of 150 frames per second, quite happily
						;but 'lesser' machines will not. 50, therefore, is quite sensible For a game of this nature, who's minimum system spec
						;will be something like a PII 300MHz machine (i.e.: Blitz Basic's minimum spec!)

;NOTE: there's no need to organise your variable declarations, as I have here, into sections. They can appear in any order you like, before the main program begins.
;I just do this, 'cos it looks right professional! :))))

;picture (and related) variables
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
MaskImage menu_ship1,255,0,255			;mask all the images.
MaskImage menu_ship2,255,0,255			;notice that it doesn't matter what order you do the masks in!
MaskImage menu_credit,255,0,255
MaskImage menu_guildhall,255,0,255		;by the way, if when testing your program, you get the error message "IMAGE DOES NOT EXIST" at this point
MaskImage menu_thanks,255,0,255			;in the program, it's because the LOADIMAGE command above didn't find the file properly.
MaskImage menu_start,255,0,255			;This is usually either because; 1 - You've typed the wrong filename in the LOADIMAGE bit, or
MaskImage menu_quit,255,0,255			;2 - You've got the variable name wrong in the MASKIMAGE bit.
										;you could perform some checks at the end of loading all the images, so that you know they've all been loaded
										;successfully. If they haven't then you could end the program properly and tell the user that they've got
										;corrupt or missing files!

										;as you'll see, I haven't bothered! :)

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
	
	music_menu_level = 1	;this value will be used to set the volume of the music track in a minute..
	Repeat		;a REPEAT.. FOREVER loop will carry out the code in between for as long as the program is running..
		If FLAG_MENUON = 1 Then	;If the FLAG_MENUON flag is set to 1 then "do" the following..
			menu_loop_update()	;call function "menu_loop_update()"

		Else									;if the FLAG_MENUON flag wasn't set to 1 then "do" the following..
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

				FLAG_MENUON = 0				;back in the "menu_loop()" function, this will cause the program to end!
		End If

		If KeyDown(KEY_SAVESCREEN)			;if the user presses the "SAVESCREEN" button on the keyboard, then..
			FlushKeys
			FLAG_SAVESCREEN = 1					;just sets FLAG_SAVESCREEN to 1. You'll see this used in the next function.
		End If
		If menu_ship_ver > (-50 - ImageHeight(menu_ship1)) ;if the bottom of the (largest) ship picture gets to 50 pixels above the top of the screen
			If menu_ship_type = 1 Then	
				;if "menu_ship_type" is 1 then..
				menu_ship_ver = menu_ship_ver - .3 ;how fast menuship1 moves up the screen
			ElseIf menu_ship_type = 2 Then
				;if "menu_ship_type" is 2 then..
				menu_ship_ver = menu_ship_ver - .8 ;how fast menuship2 moves up the screen
			End If
		Else
			menu_ship_ver = SCREEN_HEIGHT+1	;reset the vertical position so that the top of the ship is at the bottom of the screen
			menu_ship_hor = Rnd(0,SCREEN_WIDTH-ImageWidth(menu_ship1)) ;create a random horizontal position
			menu_ship_type = Int(Rnd(1,2)) ;randomly choose between values 1 or 2
		End If
		menu_frame = menu_frame + 1		;increase the value of "menu_frame" by 1
		If menu_frame = 25 Then			;if the value of "menu_frame" gets to 25 then reset it to 1
			menu_frame = 1

			If menu_start_stat = 1 Then	;this bit just switches the value of "menu_start_stat" between 1 and 0, i.e. :If it's 1, then make it 0, if it's 0, then make it 1 etc.
				menu_start_stat = 0		;this will be used in the next function to flash the "PRESS FIRE TO START" caption on and off!
			Else
				menu_start_stat = 1
			End If
		End If
	Next
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
	DrawImage menu_thanks,(SCREEN_WIDTH/2)-(ImageWidth(menu_thanks)/2),SCREEN_HEIGHT-15

	;depending on the value set earlier in "menu_loop_update()" this draws one of the two ships at the relevant coordinates on-screen
	;menu_ship_hor is the x position, and is set once per "scroll up the screen" in menu_loop_update().
	;menu_ship_ver is the y position, which is repeatedly changed in menu_loop_update() to scroll the ships up the screen!
	If menu_ship_type = 1 Then
		DrawImage menu_ship1,menu_ship_hor,menu_ship_ver
	ElseIf menu_ship_type = 2 Then
		DrawImage menu_ship2,menu_ship_hor,menu_ship_ver
	End If
		
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