
AppTitle "***** Particles *****"

Const max_particles=700	;play with this!!!!!

Const width=640,height=480,gravity#=.075

Global count

Type Particle
	Field x#,y#
	Field xs#,ys#
	Field w,h,r,g,b
End Type

Graphics width,height
SetBuffer BackBuffer()

Setup( width/2 )

Repeat
	Cls
	Update()
	Render()
;	Rect 0,ScanLine(),width,1
	Flip
Until KeyDown(1)

End

Function Setup( x )
	If count>=max_particles Then Return
	p.Particle=New Particle
	p\w=Rnd( 5 )+5:p\h=Rnd( 5 )+5
	p\x=x:p\y=height-30
	p\xs=Rnd( -2,2 ):p\ys=Rnd( -8,-5 )
	p\r=255:p\g=255:p\b=255
	count=count+1
End Function

Function Update()
	For p.Particle=Each Particle
		p\x=p\x+p\xs
		If p\x<0 Or p\x>width-p\w
			p\xs=-p\xs
			p\x=p\x+p\xs
		EndIf
		p\ys=p\ys+gravity:p\y=p\y+p\ys
		If p\y>height-p\h And p\ys>0 Then Setup( p\x ):p\ys=-p\ys
		If p\b>0
			p\b=p\b-5
		Else If p\g>0 
			p\g=p\g-5
		Else If p\r>0
			p\r=p\r-1
		Else 
			Delete p:count=count-1
		EndIf
	Next
End Function

Function Render()
	For p.Particle=Each Particle
		Color p\r,p\g,p\b
		Rect p\x,p\y,p\w,p\h
	Next
End Function