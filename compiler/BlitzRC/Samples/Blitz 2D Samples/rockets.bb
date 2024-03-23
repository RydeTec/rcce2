
;		--------------------------------------
; 		  Super Crazy Rocket SMASH FIGHTERS!
; 		--------------------------------------

; --------
; By James Boyd
; --------
; History
; --------
;
; 
; ----------------
;
; Started!
; Major effort to put spaces between commands and brackets, after commas, and between operators!
; Console screen
; Added right mouse button to re-drop logo, for thrills while developing [Note from James of the future, on 7 September: Alas, poor re-dropping logo! I knew it so well...]
; Re-organised into functions
; Changed some variables to globals
; Created timing functions (ResetTimer() and TimeOut())

; 
; ----------------

; Functionised nearly everything
; Tested gravity effect (and got it exactly right first time!!!) [Note from James of the future, on 7 September: Yeah, right, Einstein...how come I had to do it all again, then? Lazy past-self...]
; Clouds now updated in batches, which means more can easily be added if need be

;
; ----------------

; New Ship object to hold data for multiple rockets
; New timer functions to allow multiple timers running at the same time
; Four crude rotation motions put in (with fixed rocket graphic!), working nicely :)

; 
; ----------------

; Lost track of time!
; Gravity and inertia almost right now...totally reworked
; Tested with, er... 2048 rockets at once :)
; Tested with 500 *controllable* rockets! Hee hee!

; 
; ----------------

; Added crap rain effect! With wind!
; Added rocket/screen-edge wrap routine
; Added snow! Just like rain, but white!
; Added screen-grabber for development purposes
; Pre-calc'd rotations (Sin/Cos stuff)
; Went off and made "Depravity Engine" out of the rocket control code :)

; ------
; To do:
; ------

; Increasing rotation speed, starting off small?
; Rocket flames...uh-oh...
; Problem: if k or l held down before q, and kept down while q is pressed, q is ineffective...and if a tree falls over in the forest, and there's no-one around, does it make a sound? [Psst -- the answer's "yes"!]
; Create Level object, put everything into MakeLevel (level) function...
; GroundUpdate ()
; Oh, yeah, an actual *game* ;)

; --------------
; To be sorted:
; --------------
; More frames for start text
; --------------

; -----------------------------------------------------------------------------
; Basic set-up stuff
; -----------------------------------------------------------------------------

; Set taskbar title:

AppTitle "Smash Fighters!"

; Screen sizes:

Const sw = 1024
Const sh = 768

; Set graphics mode:

Graphics sw,sh

; Draw to hidden buffer all the time:

SetBuffer BackBuffer ()

; Tell 'em why they're waiting:
a$="Rendering frames..."
Text (sw/2)-(StringWidth (a$)/2),(sh/2)-(StringHeight (a$)/2),a$
Flip

; Load images:

Global rocketlogo	 =	LoadImage ("rocketlogo.bmp"); Main logo for title screen

Global groundcountry = 	LoadImage ("country.bmp")
Global countryheight = 	sh-ImageHeight (groundcountry)

; Set up cloud stuff and load images:

Const cloudsperbatch = 3								; Number of clouds in each "batch"

Dim cloudx (2, cloudsperbatch), cloudy (2, cloudsperbatch), cloudspeed (2, cloudsperbatch)
Dim cloudimage (2, cloudsperbatch), cloudwidth (2, cloudsperbatch)

; Meanings:

; cloudx		= Position across the screen
; cloudy		= Position down the screen
; cloudspeed	= Cloud's speed (duh)
; cloudimage	= Cloud's image (duh again)
; cloudwidth	= Do I really have to spell this out?

; Set 'em up:

cloudx (1, 1) 		= Rnd (sw)
cloudx (1, 2) 		= Rnd (sw)
cloudx (1, 3) 		= Rnd (sw)

cloudx (2, 1) 		= Rnd (sw)
cloudx (2, 2) 		= Rnd (sw)
cloudx (2, 3) 		= Rnd (sw)

cloudspeed (1, 1) 	= 1
cloudspeed (1, 2) 	= 3
cloudspeed (1, 3) 	= -2

cloudspeed (2, 1) 	= 2
cloudspeed (2, 2) 	= -3
cloudspeed (2, 3) 	= -1

cloudy (1, 1) 		= 50
cloudy (1, 2) 		= 550
cloudy (1, 3) 		= 300

cloudy (2, 1) 		= 520
cloudy (2, 2) 		= 100
cloudy (2, 3) 		= 620

cloudimage (1, 1)	= LoadImage ("cloud1.bmp")	; A cloud
cloudimage (1, 2)	= LoadImage ("cloud2.bmp")	; Another cloud
cloudimage (1, 3)	= LoadImage ("cloud3.bmp")	; Whaddya know? Another cloud!

cloudimage (2, 1) 	= cloudimage (1, 2)
cloudimage (2, 2) 	= cloudimage (1, 3)
cloudimage (2, 3) 	= cloudimage (1, 1)

cloudwidth (1, 1) 	= ImageWidth (cloudimage(1, 1))
cloudwidth (1, 2) 	= ImageWidth (cloudimage(1, 2))
cloudwidth (1, 3) 	= ImageWidth (cloudimage(1, 3))

cloudwidth (2, 1) 	= ImageWidth (cloudimage(2, 1))
cloudwidth (2, 2) 	= ImageWidth (cloudimage(2, 2))
cloudwidth (2, 3) 	= ImageWidth (cloudimage(2, 3))

; Transparency masks for images (ie background colour that will be see-through):

Const redmask		= 75
Const greenmask		= 170
Const bluemask		= 250

; Stuff for display of "Press any key to start" graphics:

Dim pressakey (3)

pressakey (1)		= LoadImage ("pressakey1.bmp"); Images for start text
pressakey (2)		= LoadImage ("pressakey2.bmp"); And again
pressakey (3)		= LoadImage ("pressakey3.bmp"); Aaand yet again

; Set images' transparencies:

MaskImage rocketlogo,			redmask, greenmask, bluemask

MaskImage cloudimage (1, 1),	redmask, greenmask, bluemask
MaskImage cloudimage (1, 2),	redmask, greenmask, bluemask
MaskImage cloudimage (1, 3),	redmask, greenmask, bluemask

MaskImage cloudimage (2, 1),	redmask, greenmask, bluemask
MaskImage cloudimage (2, 2),	redmask, greenmask, bluemask
MaskImage cloudimage (2, 3),	redmask, greenmask, bluemask

MaskImage pressakey (1),		redmask, greenmask, bluemask
MaskImage pressakey (2),		redmask, greenmask, bluemask
MaskImage pressakey (3),		redmask, greenmask, bluemask

; Clear sky:

ClsColor redmask, greenmask, bluemask
Cls

; Miscellaneous global variables:

Global ylogo										; Logo's y position; used in DropLogo ()
Global gravity#=0.1									; Gravity applied to logo

; NB Gravity will be in the Level object in future! Different gravity in some levels!

; Objects:

Type Timer											; Timer object (allows multiple timers at once)

	Field TimerInit									; Time started
	Field TimeOut									; Timeout in ticks

End Type

Type Ship											; Ship object, holds rocket data (duh)

	Field x#										; Rocket's x position
	Field y#										; Rocket's y position
	Field shipxacc#									; Rocket's horizontal acceleration
	Field shipyacc#									; Rocket's vertical acceleration
	Field frame										; Rocket's attitude

End Type

; -----------------------------------------------------------------------------

; Rain/snow (started as just rain, hence the naming, but then I realised
; a quick colour change turned it into snow!):

Type Drop

	Field x1#
	Field y1#

	Field x2#
	Field y2#

	Field xadd#
	Field yadd#

End Type

Global rainr1 = 10
Global raing1 = 41
Global rainb1 = 105

Global rainr2 = 64
Global raing2 = 120
Global rainb2 = 180

Global wind			= 2
Global raingravity	= 2
Global drops 		= 1000								; Configurable, of course!

Dim rain.Drop (drops)

For b = 1 To drops
	rain.Drop (b) 		= New Drop
	rain (b)\x1			= Rnd (sw)
	rain (b)\y1			= Rnd (sh)
	rain (b)\xadd		= Rnd (wind, wind-1)
	rain (b)\yadd		= raingravity
Next

; -----------------------------------------------------------------------------

; Create rocket graphics:

; Number of frames required:

Global 	totalframes	= 64
Dim 	myimage (totalframes)

; Upwards-facing image:
;image$="rocket01-000deg.bmp"
image$="redrocketup.bmp"

; Make future images' handles centered:
AutoMidHandle True

; Load the image the totalframes will be generated from:
myimage (0) = LoadImage (image$)

; Mask out the background:
;MaskImage myimage (0), 255, 0, 255
MaskImage myimage (0), 75, 170, 250

;ScaleImage myimage (0), 0.75, 0.75			; **** TEMPORARY!!! ****

; Set up stuff for totalframes:
Global rotationangle# 	= 0
Global totalframestep# 	= 360.0/totalframes

astep# = Float (360.0) / (totalframes)				; Used below for pre-calc'ing angles

totalframes = totalframes - 1						; Accounts for frame 0

; Render the totalframes:
For frame = 1 To totalframes
	myimage (frame) = CopyImage (myimage (0))
	rotationangle = rotationangle + totalframestep
	TFormFilter False								; No dithering!
	RotateImage myimage (frame),rotationangle
Next

; Getting frame sizes, since I guess it's faster than calling ImageWidth (), etc
; during the game (maybe not though ;)

Dim shipheight (totalframes)
Dim shipwidth  (totalframes)

Dim tx# (totalframes), ty# (totalframes)

For b = 0 To totalframes
	shipheight (b) = ImageHeight (myimage (b))
	shipwidth  (b) = ImageWidth  (myimage (b))

	rotationangle# = b * astep						; Pre-calc angles
	tx# (b) = Sin (rotationangle)
	ty# (b) = Cos (rotationangle)
Next

; -----------------------------------------------------------------------------

; Crap weather effects:

Const none		= 0
Const raining 	= 1
Const snowing 	= 2

Global weather = snowing ; none ; raining

; -----------------------------------------------------------------------------
; Here we go!
; -----------------------------------------------------------------------------

StartClouds (5)										; Display clouds alone for x seconds (click to skip)

; NOTE: Will be accompanied by wind sound -- atmosphere and all that :)

; -----------------------------------------------------------------------------

.start												; Only for demo purposes (right-click to re-drop logo)

DropLogo ()											; Drop the logo onto screen
ClickToStart ()										; Wait for keypress or mouseclick

; -----------------------------------------------------------------------------

; Set acceleration limits:

Global maxxacc# = 15
Global maxyacc# = 10

; -----------------------------------------------------------------------------








































; -----------------------------------------------------------------------------

; Number of players:

Global players = 1									; Change to anything!

; -----------------------------------------------------------------------------

; Create rocket data:

Dim rocket.Ship (players)

For s = 1 To players

	rocket.Ship (s)		= New Ship
	rocket (s)\x 		= Rnd (sw)
	rocket (s)\y 		= 0
	rocket (s)\shipxacc = 0
	rocket (s)\shipyacc = 0
	rocket (s)\frame	= 0

Next

; -----------------------------------------------------------------------------

; For screenshot grabber while developing:

screenshotpath$ = "d:\temp\"
shot = 0

; Hold right mouse button to pause display
; While holding right mouse, press left to grab screen shot to above location

; -----------------------------------------------------------------------------
; Play!
; -----------------------------------------------------------------------------

Repeat

	Cls												; Clear hidden buffer

	CheckPlayers ()									; Get keypresses, do stuff

	PositionRocket ()								; Calculate rockets' positions
	OhNatureThouArtACruelMistressOrSomething ()		; Apply friction and gravity

	DrawImage groundcountry, 0, countryheight		; Draw ground

	UpdateClouds (1)								; Back layer of clouds
	DrawRockets ()									; Draw rockets
	UpdateClouds (2)								; Front layer of clouds
	
	If weather Then DoWeather ()					; Crap weather effects!

	Flip											; Show result

	; Testing:
	While MouseDown (2) = 1
		Delay 1										; Pause display (right mouse button)
		If MouseDown (1) = 1						; Left-click while held for screenshot!
			SaveBuffer BackBuffer (), screenshotpath$ + "smash" + shot + ".bmp"
			shot = shot + 1
		EndIf
	Wend

Until KeyDown (1) = 1								; [ESC] or right mouse button

End












































; -----------------------------------------------------------------------------
; Functions used
; -----------------------------------------------------------------------------

; Checks keypresses for all players, calls appropriate routines:

Function CheckPlayers ()

	For player = 1 To players

		Select player
			Case 1
				lkey = 203
				rkey = 205
				ukey = 200
			Case 2
				lkey = 203;37;203
				rkey = 205;38;205
				ukey = 200;16;200
		End Select
			
		If KeyDown (lkey)		; Left
			rocket (player)\frame = rocket (player)\frame - 1
			If rocket (player)\frame < 0 Then rocket (player)\frame = totalframes
		EndIf

		If KeyDown (rkey)		; Right
			rocket (player)\frame = rocket (player)\frame + 1
			If rocket (player)\frame > totalframes Then rocket (player)\frame = 0
		EndIf

		If KeyDown (ukey)		; Up
			Power (player)
		EndIf

	Next

End Function

; -----------------------------------------------------------------------------

; Draws all rockets with appropriate frames:

Function DrawRockets ()

	For a.Ship = Each Ship
		RocketWrap (a)
		DrawImage myimage (a\frame), a\x, a\y
	Next

End Function

; -----------------------------------------------------------------------------

; Create and start a timer which runs out after "tout" ticks:

Function SetTimer.Timer (tout)

	t.Timer=New Timer
	t\TimerInit=MilliSecs ()						; Reset this Timer object to system ticks
	t\TimeOut=tout									; This Timer object's timeout
	Return t

End Function

; -----------------------------------------------------------------------------

; Checks to see if given Timer object has run out:

Function TimeOut (t.Timer)

	If t <> Null
		If MilliSecs () > t\TimerInit + t\TimeOut	; Check if Timer has run out
			Delete t								; Delete Timer object
			Return 1								; Timer has run out
		EndIf
	EndIf

End Function

; -----------------------------------------------------------------------------

; Some clouds to start things off (for "sec" seconds):

Function StartClouds (sec)

	CloudTimer.Timer=SetTimer (sec*1000)			; Reset timer (duh)

	Repeat
		Cls											; Clear screen (on hidden buffer)
		DrawImage groundcountry, 0, countryheight
		UpdateClouds (1)							; Draw back clouds (batch 1)
		UpdateClouds (2)							; Draw front clouds (batch 2)
		If weather Then DoWeather ()
		Flip										; Bring hidden buffer to front
	Until TimeOut (CloudTimer) Or Clicked ()		; For "sec" seconds

End Function

; -----------------------------------------------------------------------------

; Drop logo onto screen:

Function DropLogo	()

	acceleration# = 0								; Speeds up as it falls
	ylogo= -ImageHeight (rocketlogo)				; Start just off top of screen

	While ylogo < 250								; Repeat till we're past 250
		Cls											; Clear screen
		DrawImage groundcountry, 0, countryheight
		UpdateClouds (1)							; Draw back clouds (batch 1)
		DrawImage rocketlogo, 120, ylogo			; Draw logo
		UpdateClouds (2)							; Draw front clouds (batch 2)
		If weather Then DoWeather ()
		Flip										; Bring hidden buffer to front
		ylogo = ylogo + acceleration				; Increase y position
		acceleration = acceleration + 0.25			; Speed up
	Wend

End Function

; -----------------------------------------------------------------------------

; Press a key to start:

Function ClickToStart ()

	; Set up some stuff for the glowing "start" text:

	glowupdate 	= 1
	glowdir		= 1
	GlowTimer.Timer=SetTimer (50)									; Reset timer

	; Glowing text loop:
	
	Repeat
	
		Cls											; Clear the screen (on the hidden buffer)
		DrawImage groundcountry, 0, countryheight
	;	UpdateCountry () ; DO THIS
		UpdateClouds  (1)							; Draw back clouds (batch 1)
		DrawImage pressakey (glowupdate), 255, 470	; Draw "start" text

		If TimeOut (GlowTimer)
			glowupdate = glowupdate + glowdir		; Next "start text" image
			If glowupdate = 3 Then glowdir = -1		; Forwards...
			If glowupdate = 1 Then glowdir = 1		; ...and backwards
			GlowTimer=SetTimer (50)					; Restart the timer
		EndIf

		DrawImage rocketlogo, 120, ylogo			; Draw logo
		UpdateClouds (2)							; Draw front clouds (batch 2)
		If weather Then DoWeather ()
		Flip										; Show results of drawing to hidden buffer

	Until Clicked ()								; Click mouse or press a key to end

End Function

; -----------------------------------------------------------------------------

; Update clouds in batches of "cloudsperbatch":

Function UpdateClouds (batch)

	For a=1 To cloudsperbatch

		cloudx (batch, a) = cloudx (batch, a) + cloudspeed (batch, a)

		If cloudx (batch, a) + cloudwidth (batch, a) > sw
			DrawImage cloudimage (batch, a), cloudx (batch, a)-sw, cloudy (batch, a)
		EndIf

		If cloudx (batch, a) > sw
			cloudx (batch, a) = 0
		EndIf

		If cloudx (batch, a) < 0
			DrawImage cloudimage (batch, a), sw+cloudx (batch, a), cloudy (batch, a)
		EndIf

		If cloudx (batch, a) + cloudwidth (batch, a) < 0
			cloudx (batch, a) = sw - cloudwidth (batch, a)
		EndIf

		DrawImage cloudimage (batch, a),cloudx (batch, a), cloudy (batch, a)

	Next

End Function

; -----------------------------------------------------------------------------

; Check for keypress or mouse button click:

Function Clicked ()

	If MouseDown (1) = 1 Or MouseDown (2) = 1 Or GetKey() Then Return 1

End Function

; -----------------------------------------------------------------------------

; Apply velocities to rocket positions:

Function PositionRocket ()

	For a.Ship = Each Ship
		a\x = a\x + a\shipxacc
		If weather Then a\x = a\x + wind			; Raining or snowing? Windy too, then!
		a\y = a\y + a\shipyacc
	Next

End Function

; -----------------------------------------------------------------------------

; Work out x and y velocities when boost key pressed (uses pre-calc'd angles):

Function Power (player)

		If tx (rocket (player)\frame) > 0
			If rocket (player)\shipxacc < maxxacc
				rocket (player)\shipxacc = rocket (player)\shipxacc + 0.2 * tx (rocket (player)\frame)
			EndIf
		Else
			If rocket (player)\shipxacc > -maxxacc
				rocket (player)\shipxacc = rocket (player)\shipxacc + 0.2 * tx (rocket (player)\frame)
			EndIf
		EndIf

		If ty (rocket (player)\frame) > 0
			If rocket (player)\shipyacc > -maxyacc
				rocket (player)\shipyacc = rocket (player)\shipyacc - 0.2  * ty (rocket (player)\frame)	; Up   (ty is positive)
			EndIf
		Else
			If rocket (player)\shipyacc < maxyacc * 1.5
				rocket (player)\shipyacc = rocket (player)\shipyacc - 0.15 * ty (rocket (player)\frame) ; Down (ty is negative)
			EndIf
		EndIf

End Function

; -----------------------------------------------------------------------------

; Gradually applies "friction" and gravity to rocket's x and y velocities:

Function OhNatureThouArtACruelMistressOrSomething ()

	For a.Ship = Each Ship
		If a\shipxacc > 0 Then a\shipxacc = a\shipxacc - 0.02
		If a\shipxacc < 0 Then a\shipxacc = a\shipxacc + 0.02
		If a\shipyacc < maxyacc Then a\shipyacc = a\shipyacc + 0.04
		If a\shipyacc > maxyacc Then a\shipyacc = a\shipyacc - 0.1
	Next

End Function

; -----------------------------------------------------------------------------

; Crap rain effect!

Function DoWeather ()

	For d = 1 To drops
		rain (d)\x1 = rain (d)\x1 + rain (d)\xadd
		rain (d)\y1 = rain (d)\y1 + rain (d)\yadd
		If rain (d)\x1 > sw Then rain (d)\x1 = 0
		If rain (d)\y1 > sh Then rain (d)\y1 = 0
		If rain (d)\x1 <0   Then rain (d)\x1 = sw
		If rain (d)\y1 <0   Then rain (d)\y1 = sh
		DrawDrop (d)
	Next

End Function

; -----------------------------------------------------------------------------

; Draw a raindrop:

Function DrawDrop (d)

	Select weather

		Case raining
			Color Rnd (rainr1, rainr2), Rnd (raing1, raing2), Rnd (rainb1, rainb2)

		Case snowing
			dullsnow = Rnd (50)
			Color 255 - dullsnow, 255 - dullsnow, 255 - dullsnow

	End Select

	Rect rain (d)\x1, rain (d)\y1, 2, 2

End Function

; -----------------------------------------------------------------------------

; Wrap rocket round edges of screen:

Function RocketWrap (a.Ship)

		If a\x + shipwidth (a\frame) > sw
			DrawImage myimage (a\frame), a\x - sw, a\y
		EndIf

		If a\x > sw
			a\x = 0
		EndIf

		If a\x - (shipwidth (a\frame) / 2) < 0
			DrawImage myimage (a\frame), sw+a\x, a\y
		EndIf

		If a\x + shipwidth (a\frame) < 0
			a\x = sw - shipwidth (a\frame)
		EndIf

		If a\y + shipheight (a\frame) > sh
			DrawImage myimage (a\frame), a\x, a\y - sh
		EndIf

		If a\y > sh
			a\y = 0
		EndIf

		If a\y - (shipheight (a\frame) / 2) < 0
			DrawImage myimage (a\frame), a\x, sh+a\y
		EndIf

		If a\y + shipheight (a\frame) < 0
			a\y = sh - shipheight (a\frame)
		EndIf

; It's not 100% perfect -- if you get a rocket balancing at the corner of the
; screen, so it appears in all four corners, you get a little jump, but most
; people won't ever see it!

End Function