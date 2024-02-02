; Name:			pi.bb
; Extra Files:	None
; Description:	The Blitz Manual 'Pi' example
; Author:		Mikkel Løkke
; License:		Public Domain
; Purpose:		To demonstrate how the 'Pi' command works
Graphics 800,600
Repeat			; Start a loop.
	diam=Input("Enter a circles DIAMETER (0 to quit): ")
	Write "A circle with a diameter of "+diam+" will have a circumference of "

	Print diam*Pi	; Calulate the circumfence of a circle using the diameter and Pi.
Until diam=0		; Exit the loop if the diameter is 0

Print"":Print"Click mouse to continue...."
WaitMouse			; Wait for the mouse before ending.
End