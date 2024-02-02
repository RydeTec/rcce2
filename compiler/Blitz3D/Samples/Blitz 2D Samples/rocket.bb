
; -----------------------------------
; Super Crazy Rocket SMASH FIGHTERS!
; -----------------------------------

;
; --------
; History
; --------
;
; 1 September 2000
; ----------------
;
; Started!
; Console screen
;
; --------------
; To be sorted:
; --------------
; Logo needs editing round the edges to remove extra blue (showing when cloud passes!)
; Logo says "All right[!!!] reserved"!
; More frames for start text
; --------------

; Screen sizes:
Global sw = 1024
Global sh = 768

; Set graphics mode:
Graphics sw,sh

; Draw to hidden buffer all the time:
SetBuffer BackBuffer ()

; Load images:
rocketlogo	=	LoadImage ("rocketlogo.bmp")		; Main logo for title screen

rocketred	=	LoadImage ("rocketred.bmp")			; Red rocket graphics
rocketblue	=	LoadImage ("rocketblue.bmp")		; Blue rocket graphic

cloud1		=	LoadImage ("cloud1.bmp")			; A cloud
cloud2		=	LoadImage ("cloud2.bmp")			; Another cloud
cloud3		=	LoadImage ("cloud3.bmp")			; Whaddya know? Another cloud!

pressakey1	=	LoadImage ("pressakey1.bmp")		; Images for start text
pressakey2	=	LoadImage ("pressakey2.bmp")		; And again
pressakey3	=	LoadImage ("pressakey3.bmp")		; Aaand yet again

; Transparency masks for images (ie background colour that will be see-through):
Const redmask	= 75
Const greenmask	= 170
Const bluemask	= 250

; Set images' transparencies:
MaskImage rocketlogo,	redmask, greenmask, bluemask
MaskImage rocketred, 	redmask, greenmask, bluemask
MaskImage rocketblue,	redmask, greenmask, bluemask
MaskImage cloud1,	 	redmask, greenmask, bluemask
MaskImage cloud2,	 	redmask, greenmask, bluemask
MaskImage cloud3,	 	redmask, greenmask, bluemask
MaskImage pressakey1,	redmask, greenmask, bluemask
MaskImage pressakey2,	redmask, greenmask, bluemask
MaskImage pressakey3,	redmask, greenmask, bluemask

; Clear sky:
ClsColor redmask, greenmask, bluemask
Cls

; Set up cloud stuff:
Global numclouds = 3								; Number of clouds

Dim cloudx (3), cloudy (3), cloudspeed (3), cloudimage (3), cloudwidth (3)

; cloudx		= Position across the screen
; cloudy		= Position down the screen
; cloudspeed	= Cloud's speed (duh)
; cloudimage	= Cloud's image (duh again)
; cloudwidth	= Do I really have to spell this out?

; Set 'em up:
cloudx (1) = 25
cloudx (2) = 250
cloudx (3) = 800

cloudspeed (1) = 1
cloudspeed (2) = 3
cloudspeed (3) = -2

cloudy (1) = 50
cloudy (2) = 200
cloudy (3) = 570

cloudimage (1) = cloud1 ;
cloudimage (2) = cloud2 ; From "load images" section...
cloudimage (3) = cloud3 ;

cloudwidth (1) = ImageWidth (cloud1)
cloudwidth (2) = ImageWidth (cloud2)
cloudwidth (3) = ImageWidth (cloud3)

; Stuff for display of "Press any key to start" graphics:
Dim pressakey (3)

pressakey (1) = pressakey1
pressakey (2) = pressakey2
pressakey (3) = pressakey3

; Bring logo onto screen:
acceleration# = 0									; Speeds up as it falls

ylogo= -ImageHeight (rocketlogo)					; Start just off top of screen

While ylogo < 250									; Repeat till we're past 250
	Cls												; Clear screen
	UpdateClouds ()
	DrawImage rocketlogo, 115, ylogo				; Draw logo
	Flip											; Bring hidden buffer to front
	ylogo = ylogo + acceleration					; Increase y position
	acceleration = acceleration + 0.25				; Speed up
Wend

; Set up some stuff for the glowing "start" text:
glowupdate 	= 1
glowdir=1
mytimer=MilliSecs ()								; Reset timer

Repeat

	Cls												; Clear the screen (on the hidden buffer)
	UpdateClouds ()									; Draw the clouds

	DrawImage pressakey (glowupdate), 255, 470		; Draw "start" text

	If MilliSecs()-mytimer>100						; 100 ticks passed?
		glowupdate=glowupdate+glowdir				; Next "start text" image
		If glowupdate=3 Then glowdir=-1				; Forwards...
		If glowupdate=1 Then glowdir=1				; ...and backwards
		mytimer=MilliSecs ()						; Reset timer
	EndIf

	DrawImage rocketlogo, 115, ylogo				; Draw logo
	Flip											; Show results of drawing to hidden buffer

Until Clicked ()									; Click mouse or press a key to end

End







































; Update clouds:
Function UpdateClouds ()
	For a=1 To numclouds
		cloudx (a) = cloudx (a) + cloudspeed (a)

		If cloudx (a) + cloudwidth (a) > sw Then DrawImage cloudimage (a), cloudx (a)-sw, cloudy (a)
		If cloudx (a) > sw Then cloudx (a) = 0

		If cloudx (a) < 0 Then DrawImage cloudimage (a), sw+cloudx (a), cloudy (a)
		If cloudx (a) + cloudwidth (a) < 0 Then cloudx (a) = sw - cloudwidth (a)

		DrawImage cloudimage (a),cloudx (a), cloudy (a)
	Next
End Function

; Check for keypress or mouse button:
Function Clicked ()
	If MouseDown (1) = 1 Or MouseDown (2) = 1 Or GetKey() Then Return 1
End Function