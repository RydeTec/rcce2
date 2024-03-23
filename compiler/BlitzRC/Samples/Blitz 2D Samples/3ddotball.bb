; *****************************
; * DEPTH COLORED 3D DOT BALL *
; *****************************
; * CODING BY TRACER          *
; *****************************

dots_in_ball = 1440											; How many dots are in the ball?

; Globals for the 3d.
Global numpoints = dots_in_ball								; Number of points in the point table.
Global distance  = 400										; Needed for perspective.
Global vx#													; X location.
Global vy#													; Y location.
Global vz#													; Z location.

; Arrays used by 3d code.
Dim points(numpoints, 3)									; Holds the point locations of the game over 3d.

Graphics 640,480

SetBuffer BackBuffer()

; make points on the surface of a sphere
; calculation to make a sphere:
; x = cos(theta) * cos(phi)
; y = cos(theta) * sin(phi)
; z = sin(theta)
; where theta = -90 to 90
; and phi = 0 to 360
For t= 1 To dots_in_ball
	xd = Rnd(-90,90)
	x0 = (Cos(xd) * 10) * (Cos(t) * 10)
	y0 = (Cos(xd) * 10) * (Sin(t) * 10)
	z0 = Sin(xd) * 100
	points(t,1) = x0
	points(t,2) = y0
	points(t,3) = z0
Next

; Loop till esc is pressed.
While Not KeyDown(1)
	threed()												; Call the threed thing.
	Color 255,255,255
	Text 0,0,"Depth Colored Real 3D Dot Ball"
	dummy = MouseX()										; Removes the mouse from fullscreen.
	Flip													; Flip the screen.
	Cls														; Clear the screen.
Wend

Function threed()
	vx# = vx# + 0.5											; X rotation speed of ball.
	vy# = vy# + 0.5											; Y rotation speed of ball.
	vz# = vz# + 0.5											; Z rotation speed of ball.

	For n = 1 To numpoints
		x3d = points(n, 1)
		y3d = points(n, 2)
		z3d = points(n, 3)
       
		ty# = ((y3d * Cos(vx#)) - (z3d * Sin(vx#)))
		tz# = ((y3d * Sin(vx#)) + (z3d * Cos(vx#)))
		tx# = ((x3d * Cos(vy#)) - (tz# * Sin(vy#)))
		tz# = ((x3d * Sin(vy#)) + (tz# * Cos(vy#)))
		ox# = tx#
		tx# = ((tx# * Cos(vz#)) - (ty# * Sin(vz#)))
		ty# = ((ox# * Sin(vz#)) + (ty# * Cos(vz#)))
		nx  = Int(512 * (tx#) / (distance - (tz#))) + 320
		ny  = Int(240 - (512 * ty#) / (distance - (tz#)))
      
		setcolor(tz#)
		Rect nx,ny,1,1
	Next
End Function

; This function looks at the z-value of the pixel
; and sets the color accoordingly.
Function setcolor(t#)
	If t# <= 100 And t# >= 75
		Color 250,250,250
	EndIf
	If t# <= 75 And t# >= 50
		Color 225,225,225
	EndIf
	If t# <= 50 And t# >= 25
		Color 200,200,200
	EndIf
	If t# <= 25 And t# >= 0
		Color 175,175,175
	EndIf
	If t# <= 0 And t# >= -25
		Color 150,150,150
	EndIf
	If t# <= -25 And t# >= -50
		Color 125,125,125
	EndIf
	If t# <= -50 And t# >= -75
		Color 100,100,100
	EndIf
	If t# <= -75 And t# >= -100
		Color 50,50,50
	EndIf
End Function