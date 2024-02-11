; Name:			sgn.bb
; Extra Files:	None
; Description:	The Blitz Manual 'Sgn' example
; Author:		Mikkel Løkke
; License:		Public Domain
; Purpose:		To demonstrate how the 'Sgn' command works

Repeat		; Start a loop
	; The next line get's a number the user types from the console.
	num=Input$("Please enter a number (0 to quit): ")

	; Here we test if the value is NEGATIVE (less than zero).
	If Sgn(num)=-1 Then Print"The number "+num+" is a NEGATIVE number."

	; Here we test if the value is POSITIVE (greater than zero).
	If Sgn(num)=1 Then Print"The number "+num+" is a POSITIVE number."

	; The number '0' is a special case since it's neither positive or negative.
	If Sgn(num)=0 Then Print"The number 0 is neither NEGATIVE or POSITIVE."

Until num=0		; Exit loop if user enters the number '0'

Print"":Print"Click mouse to continue...."
WaitMouse		; Wait for the mouse before ending.
End