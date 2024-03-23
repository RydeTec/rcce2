; Name:			sar.bb
; Extra Files:	None
; Description:	The Blitz Manual 'Sar' example
; Author:		Mikkel Løkke
; License:		Public Domain
; Purpose:		To demonstrate how the 'Sar' command works

num=%11110000111100001111000011110000	; Define a bit pattern which is easy to recognize. NOTE: This is a NEGATIVE number !

For i = 0 To 31	; Loop 32 times (Since Blitz values are 32 bits 'wide')

	; This line prints the current bit pattern and the corresponding value in DECIMAL
	Print "The binary sequence is now : "+Bin$(num)+" ("+num+")

	; Here we shift the bit pattern 1 position to the right, which means we take into account a negative number
	num=num Sar 1

Next			; The end of our loop
WaitMouse		; Wait for the mouse before ending.
End