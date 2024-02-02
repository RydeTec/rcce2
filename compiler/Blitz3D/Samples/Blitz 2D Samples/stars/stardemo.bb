; parallax starfield library demo

; (c) Graham Kennedy 2001



Const width=800
Const height=600

Include "stars.bb"

Const maxstars = 1000

Graphics width,height


For i  = 1 To 1000
	addstar(Rnd(width),Rnd(height),Rnd(10)+4)
Next

Graphics width,height
SetBuffer BackBuffer()
While Not KeyDown(1)
	dx = dx + (KeyDown(205)-KeyDown(203))
	dy = dy + (KeyDown(208)-KeyDown(200))
	
	movestars(dx,dy)
	
	Cls
	showstars()
	Text 0,0,"Use the arrow keys to move the starfield"
	Flip

Wend

cleanupstars()

End