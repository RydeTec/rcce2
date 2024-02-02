; Name:		mod.bb
; Extra Files:	None
; Description:	The Blitz Manual 'Mod' example
; Author:		Mikkel Løkke
; License:		Public Domain
; Purpose:		To demonstrate how the 'Mod' command works

Write "12 diveded by 3 is "
Write 12/3		; Print the RESULT of the expression 12/3
Write " with a remainder of "
Print 12 Mod 3	; Print the REMAINDER of the expression 12/3

Print ""		; Prints an empty line.

Write "12 divided by 5 is "
Write 12/5		; Print the RESULT of the expression 12/5
Write " with a remainder of "
Print 12 Mod 5		; Print the REMAINDER of the expression 12/3

Print"":Print"Click mouse to continue...."
WaitMouse		; Wait for the mouse before ending.
End