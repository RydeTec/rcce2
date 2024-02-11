; Star Demo by Dave Kirk (GFK)
;
; Demonstrates use of 'types', AnimImages, functions, reading the mouse position and double buffering
;
; Change the value in NewStars to change the number of new stars generated each frame
; On my AMD-900 I can set it to 75 and get over 6,000 stars on screen without losing a frame!

AppTitle "Star Demo"


Graphics 800,600,16,1									; Set graphics mode to 800x600 res, 16 bit, force full screen even in debug mode.
SetBuffer BackBuffer()									; Ready for double-buffering
Global Star = LoadAnimImage("MouseStars.bmp",9,9,0,8) 	; Load star images
Global Count = 0										; simple counter to keep track of the number of stars on screen

Global NewStars = 20									; PLAY WITH THIS VALUE FOR MORE/LESS STARS
Global FPS = 50											; Maximum frames per second

While KeyDown(1) = 0									; Continue until the ESCAPE key is pressed
	t = MilliSecs()		
	Cls
	Text 0,0,"Hold down left mouse button to generate stars!"
	Text 0,12,"Number of stars on screen: " + Count
	MouseParticles										; Call the function which updates existing stars and adds new ones
	While MilliSecs()-t < 1000/FPS : Wend
	Flip												; Switch the backbuffer and frontbuffer over (double-buffering)
Wend

End														; End the program (when ESCAPE is pressed)


Function MouseParticles()
	If MouseDown(1)										; If left mouse button pressed, add new stars!
		For N = 1 To NewStars
			P.MousePart = New MousePart					; Create a new star
			P\X = MouseX()								; Set horizontal position to same as mousex
			P\Y = MouseY()								; Set vertical position to same as mousey
			P\XS = Rnd(-5.0,5.0)						; Random horizontal speed
			P\YS = Rnd(-5.0,5.0)						; Random vertical speed
			P\image = Rand(0,7)							; Random start frame for star animation
		Next
	EndIf
	count = 0
	For P.MousePart = Each MousePart					; Update existing stars!
		count = count + 1								; Count the number of stars on screen
		P\YS = P\YS + 0.25								; Increase vertical speed
		P\X = P\X + P\XS								; Update horizontal position
		P\Y = P\Y + P\YS								; update vertical position
		P\Image = P\Image + 1							; Animate the star thru frames 0-7
		If P\Image = 8 Then P\Image = 0
		DrawImage Star,P\X,P\Y,P\Image					; Draw a star
		If P\Y > 800 Then Delete P						; Delete a star if it drops off the bottom of the screen!
	Next
End Function

Type MousePart
	Field X#,Y#,XS#,YS#,Image#
End Type