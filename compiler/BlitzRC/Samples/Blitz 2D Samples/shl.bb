; Name:			shl.bb
; Extra Files:	None
; Description:	The Blitz Manual 'Shl' example
; Author:		Mikkel Løkke
; License:		Public Domain
; Purpose:		To demonstrate how the 'Shl' command works

num=%1111000011110000111100001111	; Define a bit pattern which is easy to recognize

For i = 0 To 31	; Loop 32 times (Since Blitz values are 32 bits 'wide')

	; This line prints the current bit pattern and the corresponding value in DECIMAL
	Print "The binary sequence is now : "+Bin$(num)+" ("+num+")

	; Here we shift the bit pattern 1 position to the left
	num=num Shl 1

Next			; The end of our loop
WaitMouse		; Wait for the mouse before ending.
End