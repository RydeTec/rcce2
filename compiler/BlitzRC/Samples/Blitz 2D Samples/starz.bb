; Name:			starz.bb
; Extra Files:	None
; Description:	NetBlaster type Starfield
; Author:		Mikkel Løkke aka. FlameDuck
; License:		Public Domain

AppTitle "Starz"

Type star
	Field x#,y#,bright%
End Type

; -------------- Define all our variables --------------

Const width%=800,height%=600			; Define physical dimensions of the display
Const starz% = 2000						; Define how many stars to draw
Global angle%=0							; Define the starting angle of the starfield in degrees 

; -------------- Setup a display --------------

Graphics width,height
SetBuffer BackBuffer()

For i=1To starz							; A loop that creats and
	a.star=New star						; assigns random starting
	a\x=Rnd(0,width)					; positions and brightness
	a\y=Rnd(0,height)					; to all our starz
	a\bright=Rnd(8)+1
Next

dly=CreateTimer(50)
Repeat									; The start of our main loop
	WaitTimer(dly)
	Flip								; Double buffered display
	Cls
	For a.star=Each star				; Loop that draws our stars, and performs the math
		col=a\bright*20+95
		Color col,col,col				; Switch to color of our star
		Plot a\x,a\y				; and draw it

		a\x=a\x+Cos(angle)*a\bright
		a\y=a\y-Sin(angle)*a\bright

; -------------- Wrap the stars arround --------------

		If a\x<0 Then a\x=a\x+width
		If a\x>width Then a\x=a\x-width
		If a\y<0 Then a\y=a\y+height
		If a\y>height Then a\y=a\y-height
	Next

; -------------- Rotate stars --------------

	If KeyDown(203) Then angle=angle+1
	If KeyDown(205) Then angle=angle-1

Until KeyHit(1)

End