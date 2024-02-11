Graphics 640,480 ; Graphics Mode
Global rotation# = Float(0.0)
Global col = 0

; CHANGE THESE:
Global xOff = 320
Global yOff = 240
Global radius = 200

SetBuffer FrontBuffer()
While Not KeyDown(1)
	;Cls
	rotation# = Float(Float(rotation#) + .005)
	If rotation# > 360 Then rotation# = 1.0
	col = col + 1
	Color Bounce(col, 255),Bounce((col+50+rotation#), 255),Bounce((col+150+rotation#), 255)
	x1 = Float(Cos#(rotation#) * radius)+ xOff
	y1 = Float(Sin#(rotation#) * radius)+ yOff
	x2 = Float(Cos#(Aop#(rotation#)) * radius) + xOff
	y2 = Float(Sin#(Aop#(rotation#)) * radius) + yOff
	Line x1,y1, x2,y2
	;Delay 10
	;Flip
Wend
End

Function Aop#(angle#)
angle# = Float(angle#) + 180.0
angle# = Float(angle#) Mod 360.0
Return angle#
End Function

Function Bounce(num, div)
	iter = num / div
	remainder = num Mod div
	If (iter Mod 2) = 0
		num = remainder
	Else
		num = div - remainder
	EndIf
	Return num
End Function