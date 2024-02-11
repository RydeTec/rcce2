; Name:			shr.bb
; Extra Files:	None
; Description:	The Blitz Manual 'Shr' example
; Author:		Mikkel Løkke
; License:		Public Domain
; Purpose:		To demonstrate how the 'Shr' command works

num=%11110000111100001111000011110000	; Define a bit pattern which is easy to recognize

For i = 0 To 31	; Loop 32 times (since Blitz values are 32 bits 'wide')

	; This line prints the current bit pattern and the corresponding value in DECIMAL
	Print "The binary sequence is now : "+Bin$(num)+" ("+num+")

	; Here we shift the bit pattern 1 position to the right
	num=num Shr 1

Next			; The end of our loop
WaitMouse		; Wait for the mouse before ending.
End