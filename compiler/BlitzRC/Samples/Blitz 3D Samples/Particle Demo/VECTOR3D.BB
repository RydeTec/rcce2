;Include "point.bb"

; 3D calculations mayhem.
; Basically it calculates the points in 3d
; and connects these points using the
; connections table.

Type vector3D
	Field dx#,dy#,dz#
End Type

Function vector3D_Create.vector3D(x#,y#,z#)
	v.vector3d = New vector3d
	vector3d_set(v,x,y,z)
	Return v
End Function

Function vector3d_dump(v1.vector3d)
	DebugLog "dx="+v1\dx+", dy="+v1\dy+", dz="+v1\dz
End Function

Function vector3D_Set(v1.vector3d,x#,y#,z#)
	v1\dx# = x#
	v1\dy# = y#
	v1\dz# = z#
End Function

Function vector3D_magnitude#(v.vector3D)
	Return Sqr((v\dx#*v\dx#)+(v\dy#*v\dy#)+(v\dz#*v\dz#))
End Function

Function vector3D_add(v1.vector3D, v2.vector3D,v3.vector3D)
	v3\dx# = v1\dx# + v2\dx# 
	v3\dy# = v1\dy# + v2\dy#
	v3\dz# = v1\dz# + v2\dz#
End Function

Function vector3D_subtract(v1.vector3D, v2.vector3D,v3.vector3D)
	v3\dx# = v1\dx# - v2\dx#
	v3\dy# = v1\dy# - v2\dy#
	v3\dz# = v1\dz# - v2\dz#
End Function

Function vector3D_unit(v1.vector3D,v2.vector3D)
	mag# = vector3D_magnitude#(v1)
	v2\dx# = v1\dx# / mag#
	v2\dy# = v1\dy# / mag#
	v2\dz# = v1\dz# / mag#
End Function

Function vector3D_multiply(v1.vector3D,scale#,v2.vector3D)
	v2\dx# = v1\dx# * scale#
	v2\dy# = v1\dy# * scale#
	v2\dz# = v1\dz# * scale#
End Function

Function vector3D_dotproduct#(v1.vector3D,v2.vector3D)
	Return (v1\dx# * v2\dx#) + (v1\dy# * v2\dy#) + (v1\dz# * v2\dz#)
End Function

Function Vector3D_Angle#(v1.vector3D,v2.vector3D)
	m1# = vector3d_magnitude(v1)
	m2# = vector3d_magnitude(v2)
	

	Return ACos(vector3d_dotproduct(v1,v2) / (m1#*m2#))
End Function

Function vector3D_vectorproduct(v1.vector3d, v2.vector3D, v3.vector3D)
	vw1.vector3d = New vector3d
	vw2.vector3d = New vector3d
	vector3d_copy(v1,vw1)
	vector3d_copy(v2,vw2)
	
	v3\dx# = (vw1\dy# * vw2\dz#) - (vw1\dz * vw2\dy)
	v3\dy# = (vw1\dz# * vw2\dx#) - (vw1\dx * vw2\dz)
	v3\dz# = (vw1\dx# * vw2\dy#) - (vw1\dy * vw2\dx)
	
	Delete vw2
	Delete vw1

End Function

Function vector3D_copy(v1.vector3D,v2.vector3D)
	v2\dx# = v1\dx#
	v2\dy# = v1\dy#
	v2\dz# = v1\dz#
End Function

Function vector3D_frompoints(v1.vector3D,x1#,y1#,z#,x2#,y2#,z2#)

	v1\dx# = x2# - x1#
	v1\dy# = y2# - y1#
	v1\dz# = z2# - z1#

End Function

Function vector3D_decelerate(v1.vector3D,friction#)

	If friction# > vector3D_magnitude(v1) Then
		vector3D_set(v1,0,0,0)
	Else
		work.vector3D = New vector3D
		vector3D_copy(v1,work)
		vector3D_unit(work,work)
		vector3D_multiply(work,friction#,work)
		vector3D_subtract(v1,work,v1)

		Delete work
	End If

End Function

Function vector3D_Rotate(v1.vector3d,r1.vector3d,v2.vector3D)
	vx# = r1\dx#
	vy# = r1\dy#
	vz# = r1\dz#

	x3d = v1\dx#
	y3d = v1\dy#
	z3d = v1\dz#
       
	ty# = ((y3d * Cos(vx#)) - (z3d * Sin(vx#)))
	tz# = ((y3d * Sin(vx#)) + (z3d * Cos(vx#)))
	tx# = ((x3d * Cos(vy#)) - (tz# * Sin(vy#)))
	tz# = ((x3d * Sin(vy#)) + (tz# * Cos(vy#)))
	ox# = tx#
	tx# = ((tx# * Cos(vz#)) - (ty# * Sin(vz#)))
	ty# = ((ox# * Sin(vz#)) + (ty# * Cos(vz#)))
	
	v2\dx# = tx#
	v2\dy# = ty#
	v2\dz# = tz#
	
End Function

Function vector3D_Rotate2(v1.vector3d,r1.vector3d,v2.vector3D)

	rx# = r1\dx#
	cosx# = Cos(rx#)
	sinx# = Sin(rx#)
	
	ry# = r1\dy#
	cosy# = Cos(ry#)
	siny# = Sin(ry#)

	rz# = r1\dz#
	cosz# = Cos(rz#)
	sinz# = Sin(rz#)

	x# = v1\dx#
	y# = v1\dy#
	z# = v1\dz#

	
	;Rotate around x-axis
	XT#  = x#
 	YT# = (Y# * Cosx#) - (Z# * Sinx#)
	ZT# = (y# * sinx#) + (z# * cosx#) 
 	X# = XT#
	Y# = YT#
 	Z# = ZT#

 	;Rotate around y-axis
 	XT# = (x# * Cosy#) + (z# * Siny#) 
	YT# = Y#
 	ZT# =  - (x# * Siny#) + (z# * Cosy#)  	
	X# = XT#
	Y# = YT#
 	Z# = ZT#

	;Rotate around z-axis
 	XT# = (X# * Cosz#) - (y# * Sinz#)
 	YT# = (X# * Sinz#) + (y# * Cosz#) 
	ZT# = Z#
 	X# = XT#
	Y# = YT#
 	Z# = ZT#

	
	v2\dx# = x#
	v2\dy# = y#
	v2\dz# = z#
	
End Function


;Function Vector3D_To2D(v1.vector3d,distance,p1.point)

;	nx#  = Int(512 * (v1\dx#) / (distance - (v1\dz#))) + 320
;	ny#  = Int(240 - (512 * v1\dy#) / (distance - (v1\dz#)))
    

;	p1\x# = nx#
;	p1\y# = ny#
	
;End Function