; Catherine wheel....
; (c) Graham Kennedy 2001

Const width = 1024 ; 		screen width
Const height = 768;			screen height

; mess with the values below to experiment
Const pcount = 8000;		max number of particles
Const fade# = 0.80; 		rate of fade
Const spread = 45;			angle of particle spread
Const power = 15;			max initial energy each particle has (speed)
Const astep = 6;			speed the wheel turns (angle turned with each step)
Const alivetime = 5;		minimum amount of time a particle stays alive
Const pctregen = 1;			percentage of particles which can be generated per frame
Const killatEdge = 0;		1 = kill particles which go off screen, 0 = bounce off edges
Const radius# = 30;			radius of wheel

Dim xsin(360);				Sin table
Dim xcos(360);				cos table

; point structure

Type Point
	Field x#,y#;			x, y position
End Type

; directional vector
Type vector2D
	Field dx#,dy#; 			x, y directional vectors
End Type

; particle definition
Type particle
  Field p.point;			position on screen
  Field v.vector2d;			direction this particle is travelling
  Field r#,g#,b#;			color
  Field alive;				'stay alive' timer
End Type

Graphics width,height

; calculate middle of screen
Global cx = width/2;		
Global cy = height/2; 		

; set a vector (from vector library)
Function vector2D_set(v.vector2D,x#,y#)
	v\dx# = x#
	v\dy# = y#
End Function

; add a vector (from vector library)
Function vector2D_add(v1.vector2D, v2.vector2D,v3.vector2D)
	v3\dx# = v1\dx# + v2\dx#
	v3\dy# = v1\dy# + v2\dy#
End Function

; set a particles initial values
Function GenerateParticle(p.particle,direction,anglerange#,x,y,energy)
		
		a# = direction + (Rnd(1)*anglerange)-(anglerange/2)
		s# = Rnd(1)*energy
		s2# = Rnd(1)*energy

		p\p\x = x+Cos(a)*s2
		p\p\y = y+Sin(a)*s2

		p\v\dx = Cos(a)*s
		p\v\dy = Sin(a)*s
		If Rnd(10)< 5 Then  p\r = 255 Else p\r = 0;*(s/energy)
		If Rnd(10)< 5 Then 	p\g = 255 Else p\g = 0
		If Rnd(10)< 5 Then 	p\b = 255 Else p\b = 0
		p\alive = alivetime+Rnd(energy*2)
End Function 

; draw a particle on screen
Function DrawParticle(p.particle)
	WritePixelFast p\p\x,p\p\y,((p\r*256)+p\g)*256+p\b
End Function

; move a particle, flag : 1 = destroy at edge of screen, 0 = bounce at edge of screen
Function MoveParticle(p.particle,flag)
	p\p\x = p\p\x +p\v\dx
	p\p\y = p\p\y +p\v\dy
	If p\p\x < 0 Then 
		p\p\x = -p\p\x
		p\v\dx = -p\v\dx	
		If flag = 1 Then p\alive = 0
	End If
	
	If p\p\x > width Then 
		p\p\x = width - (p\p\x - width)
		p\v\dx = -p\v\dx
		If flag = 1 Then p\alive = 0
	End If
	
	If p\p\y < 0 Then 
		p\p\y = -p\p\y 
		p\v\dy = -p\v\dy	
		If flag = 1 Then p\alive = 0
	End If
	
	If p\p\y > height Then 
		p\p\y = height - (p\p\y - height)
		p\v\dy = -p\v\dy
		If flag = 1 Then p\alive = 0
	End If
End Function	

; degrade the particle colors until it vanishes
Function DegradeParticle(p.particle)
	p\r = p\r * fade
	p\g = p\g * fade
	p\b = p\b * fade
	p\alive = p\alive -1
End Function


; create all the particles upfront, but invisible
For i = 1 To pcount
	p.particle = New particle
	p\v.vector2d = New vector2d
	p\p.point = New point
Next


; precalculate sin/cos tables
For i = 0 To 360
	xsin(i) = Sin(i)
	xcos(i) = Cos(i)
Next 


SetBuffer BackBuffer()

; create the gravity vector
v.vector2d = New vector2d
vector2d_set(v,0,1)

; angle of the catherine wheel
a1 = 0


lx = Cos(a1)*radius+cx
ly = Sin(a1)*radius+cy+20

; generate until esc is pressed
While Not KeyDown(1)
	Cls
	
	LockBuffer BackBuffer()
	  generate = 0
	  x = Cos(a1)*radius+cx
	  y = Sin(a1)*radius+cy
	Color 255,255,255
	
	; draw spiral
		white = ((255*256)+255)*256+255
	  For I = 1 To 1000
		r1# = (1000-i)*(radius#/1000)
	  	xa = Cos((a1-i) Mod 360)*r1+cx
	  	ya = Sin((a1-i) Mod 360)*r1+cy
;		Rect xa,ya,2,2,1
		WritePixelFast xa,ya,white
	  Next
	
	; process particles
	For p.particle = Each particle
		
		If p\alive > 0 Then 
				moveparticle(p,killatedge)
				degradeparticle(p)
				If p\alive > 0 Then drawparticle(p)	
				vector2d_add(p\v,v,p\v)
			Else
				If generate < (pcount * (100/pctregen)) / 20 Then
					generate = generate + 1
					dx = x-lx
					dy = ly-y
					lx = x
					ly = y
					If (dx*dx+dy*dy) > 0 Then 
						av=ATan2(dx,dy)+90
					End If
					generateparticle(p,av,spread,x,y,power)
				End If
			End If
	  Next
	
	; rotate wheel
	a1 = (a1 - astep) Mod 360
	
	UnlockBuffer BackBuffer()

	Flip
Wend

Delete Each particle
Delete Each point
Delete Each vector2d

End