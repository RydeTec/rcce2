; Name:			superlinez.bb
; Extra Files:	None
; Description:	In tribute to the Soundwavers "Tubular Bells" demo on the c64.
; Author:		Mikkel Løkke aka. FlameDuck.
; License:		Public Domain



; --- Initialse everything ---

Graphics 1024,768			; (At least ! :o>)
SetBuffer BackBuffer()		; Use double buffering
SeedRnd MilliSecs()			; Simulate a true random function
Const speed1=12,speed2=16	; Define some speed constants for the line endpoints

Type lnedta									; Make a new type to hold all the data we need
Field x1,x2,y1,y2,r,g,b,xa1,xa2,ya1,ya2		; <- This is all the data we need :o>
End Type

; --- Define GLOBAL VARIABLES so that we can use these in functions ---
Global ox1=Rnd(16,1007),ox2=Rnd(16,1007),oy1=Rnd(16,751),oy2=Rnd(16,751),oxa1=-speed1,oya1=speed1,oxa2=speed2,oya2=-speed2

dly=25										; The delay in milliseconds between animation frames
gt=MilliSecs()+dly							; A counter for when we want to do the next animation frames
NewLine()									; Create our first line

; --- Begin main loop ---

Repeat
	Flip									; Switch Back and Front Buffers
	Cls										; Clear the BackBuffer
	lnc=DrawLines()							; Draw the lines, and count how many we've drawn
	LIms=MilliSecs()						; Make sure our MilliSecs counter doesn't 'roll-over'
	If LIms=>gt								; Have we reached our delay time ?
		AnimLines()							; Then let's animate our lines
		gt=LIms+dly							; and set a new delay time
	Else									; If we havn't reached our delay time
		NewLine():Delay dly					; Create a new line, and wait for our delay
	EndIf

	Color 255,255,0							; Set pen colour to Yellow
	Text 0,0,"SuperLinez: "+lnc				; And write how many lines we've done so far
Until KeyHit(1)>0							; If we press the escape key, end the main loop

End											; Terminate our program with a clean exit.

Function NewLine()							; Define our NewLine() function.
	lne.lnedta=New lnedta					; Add a new item to our list of lines
	lne\x1=ox1								; Define line data
	lne\x2=ox2								; -----"-----
	lne\y1=oy1								; -----"-----
	lne\y2=oy2								; -----"-----
	lne\xa1=oxa1							; -----"-----
	lne\xa2=oxa2							; -----"-----
	lne\ya1=oya1							; -----"-----
	lne\ya2=oya2							; -----"-----
	lne\r=Rnd(80,255)						; -----"-----
	lne\g=Rnd(80,255)						; -----"-----
	lne\b=Rnd(80,255)						; -----"-----
End Function								; The end of our NewLine() function.

Function DrawLines()						; Define our DrawLine() function.
	lnc=0									; Reset line counter (Just in case).
	For lne.lnedta=Each lnedta				; Loop through all our lines.
		Color lne\r,lne\g,lne\b				; Set drawing pen to line colour.
		Line lne\x1,lne\y1,lne\x2,lne\y2	; Draw the line.
		lnc=lnc+1							; Add one to the line counter.
	Next									; End of this loop.
	Return lnc								; Return the number of lines we've drawn.
End Function								; The end of our DrawLines() function.

Function AnimLines()													; Define our AnimLines() function.
	For lne.lnedta=Each lnedta											; Loop through all our lines.
		ox1=lne\x1:ox2=lne\x2:oy1=lne\y1:oy2=lne\y2						; Store the current lines coordinates in Global variables.
		oxa1=lne\xa1:oya1=lne\ya1:oxa2=lne\xa2:oya2=lne\ya2				; -----"-----
		If lne\x1<speed1 Or lne\x1>1024-speed1 Then lne\xa1=-lne\xa1	; If a lines ending point has hit the horizontal edge of the screen, bounce back.
		If lne\x2<speed2 Or lne\x2>1024-speed2 Then lne\xa2=-lne\xa2	; The same but for the other end of the line.
		If lne\y1<speed1 Or lne\y1>768-speed1 Then lne\ya1=-lne\ya1		; More of the same, but checking for the vertical edges.
		If lne\y2<speed2 Or lne\y2>768-speed2 Then lne\ya2=-lne\ya2		; -----"-----
		lne\x1=lne\x1+lne\xa1:lne\x2=lne\x2+lne\xa2						; Setting new x coordinates for all lines
		lne\y1=lne\y1+lne\ya1:lne\y2=lne\y2+lne\ya2						; Setting new y coordinates for all lines
	Next																; Loop to the next line, or end the loop.
End Function															; The end of our AnimLines() function

; --- And that, as they say, was that ! ---