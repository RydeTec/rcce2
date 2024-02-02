;
; QUAKE-O-VISION - By Rob Hutchinson 
; 
; An example of double buffering. With a little earthquake (BOOM BOOM)
; thing added, LOL..
;
; LMB = Randomize speeds - Hold for quake (HA) effect.
; RMB = Randomize speeds 
;

AppTitle "Quake-O-Vision"					; Give the app a title.
Const width=800,height=600,depth=16			; Setup the display.
Graphics width,height;,depth				;
SetBuffer BackBuffer()						; Set the drawing initially to the back buffer.

Type recto									; A structure for our rectangles.
	Field x#,y#,xs#,ys#						; X pos, Y Pos, X Speed, Y Speed.
	Field w,h								; Rect Width, Rect height.
	Field r,g,b								; Red, Green, Blue RGB values.
End Type

Const MaxRects=500							; Change this for different number of rectangles.
Global r.recto								; Allow our list of rectangles to be accessed inside functions.

For a=0 To MaxRects							; Keep adding items to our list until it is full.
	r.recto=New recto						; Create a new rectangle.
	r\x=Rnd(300,width-300)					; Set its x start position.
	r\y=Rnd(300,height-300)					; Set its y start position.
	r\xs=(Rnd(1)-.5)*8						; Set its x speed.
	r\ys=(Rnd(1)-.5)*8						; Set its y speed.
	r\w=Rnd(50)+1 : r\h=Rnd(50)+1			; Set its width and height.
	r\r=Rnd(255): r\g=Rnd(255) : r\b=Rnd(255)	; Set its RGB color values.
Next

; Main loop:
While Not KeyDown(1)						; Do until ESC is pressed
	Cls										; Clear the screen.
	UpdateRects()							; Call our function to update all our rectangles.
	Quakes()								; Call the reset speeds function.
	Flip									; Flip the buffers, to double buffer.
Wend
End											; Terminate program here.



; Functions:
Function UpdateRects()						; This function updates the positions of the rects
	For r.recto=Each recto					; Visit all entries in the list with this loop.
		r\x=r\x+r\xs : r\y=r\y+r\ys			; Update each rects' position.

		If r\x<0 Or r\x>((width)-r\w) Then r\xs=-r\xs	; Reverse direction if rect gets to the edge of the bitmap.
		If r\y<0 Or r\y>((height)-r\h) Then r\ys=-r\ys	;
		
		Color r\r,r\g,r\b					; Set the color to draw our rectangle, to this rectangles set color.
		Rect r\x,r\y,r\w,r\h				; Draw rectangle to buffer.
	Next
End Function

Function Quakes()
	If MouseDown(1)									; This will constantly change when LMB held down.
		For r.recto=Each recto						; Visit all entries in the list with this loop.
			r\xs=(Rnd(1)-.5)*8						; Set its x speed.
			r\ys=(Rnd(1)-.5)*8						; Set its y speed.
		Next
	EndIf

	If MouseHit(2)									; This will only execute when RMB is pressed. If its being held, it wont re-execute.
		For r.recto=Each recto						; Visit all entries in the list with this loop.
			r\xs=(Rnd(1)-.5)*8						; Set its x speed.
			r\ys=(Rnd(1)-.5)*8						; Set its y speed.
		Next
	EndIf
End Function