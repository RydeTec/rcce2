; *********************************
; * 3D WIREFRAME GRAPHICS EXAMPLE *
; *********************************
; * Coding by: Tracer             *
; *********************************
; * Coded as an example program   *
; * for the Blitz CD.             *
;**********************************

Graphics 640,480
SetBuffer BackBuffer()

Global numpoints = 27										; Number of points in the point table.
Global numconn   = 26										; Number of connections in the connect table.
Global distance  = 400										; Needed for perspective.
Global vx#													; X location.
Global vy#													; Y location.
Global vz#													; Z location.

; Arrays used by 3d code.
Dim points(numpoints, 3)									; Holds the point locations of the game over 3d.
Dim rotated(numpoints, 2)									; Holds rotated points for the 3d.
Dim connect(numconn, 2)										; Holds the connections of the 3d.

; Read data for 3d.
Restore points
For n = 1 To numpoints
	Read x3d, y3d, z3d
	points(n, 1) = x3d
	points(n, 2) = y3d
	points(n, 3) = z3d
Next
Restore conns
For n = 1 To numconn
	Read a3d, b3d
	connect(n, 1) = a3d
	connect(n, 2) = b3d
Next 

While Not KeyDown(1)
	threed()
	Flip
	Cls
Wend

; 3D calculations mayhem.
; Basically it calculates the points in 3d
; and connects these points using the
; connections table.
Function threed()
	vx# = vx# + 0.5
	vy# = vy# + 0.5
	vz# = vz# + 0.5

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
      
		rotated(n, 1) = nx
		rotated(n, 2) = ny
	Next

	For n = 1 To numconn
		Color 255,255,255
		Line rotated(connect(n, 1), 1), rotated(connect(n, 1), 2),rotated(connect(n, 2), 1), rotated(connect(n, 2), 2)
	Next
End Function


; All the data for the 3d game over.. lots of work to make :)

.points
; Points table
;      X,  Y,  Z
Data -100, 50, 50
Data  100, 50, 50
Data  100,-50, 50
Data -100,-50, 50

Data -100, 50,-50
Data  100, 50,-50
Data  100,-50,-50
Data -100,-50,-50

Data   0,-25, 0
Data   0, 25, 0

Data -35,-25, 0
Data -35, 25, 0
Data  -5,-25, 0

Data   5, 25, 0
Data  35, 25, 0
Data  20, 25, 0
Data  20,-25, 0

Data -70, 25, 0
Data -70,-25, 0
Data -50, 25, 0
Data -50,  0, 0
Data -40,  0, 0
Data -40,-25, 0

Data  40, 25, 0
Data  70, 25, 0
Data  40,-25, 0
Data  70,-25, 0

.conns
; Connections  (From,To, From,To, ..)

Data 1,2,2,3,3,4,4,1
Data 5,6,6,7,7,8,8,5

Data 1,5,2,6,3,7,4,8

Data 9,10
Data 11,12,11,13
Data 14,15,16,17
Data 18,19,18,20,20,21,21,22,22,23,23,19
Data 24,25,26,27,25,26