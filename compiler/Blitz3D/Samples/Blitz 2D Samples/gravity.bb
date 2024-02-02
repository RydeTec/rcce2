
;
;right-mouse zoom out, left-mouse zoom in.
;
Const width=640,height=480
Const num_blobs=300,min_mass#=.01,max_mass#=.02

AppTitle "Gravity simulator"

Type Blob
	Field x#,y#,xs#,ys#,mass#,r,g,b
End Type

Graphics width,height
SetBuffer BackBuffer()

SetupBlobs()

Global scale#=1

While Not KeyDown(1)
	Cls
	If MouseHit(1)
		scale=scale*2
	Else If MouseHit(2)
		scale=scale/2
	EndIf
	Origin MouseX(),MouseY()
	time=MilliSecs()
	UpdateBlobs()
	Text 0,0,MilliSecs()-time
	RenderBlobs()
	Flip
Wend

End

Function SetupBlobs()
	For k=1 To num_blobs
		b.Blob=New Blob
		ty#=Rnd(1)
		ra#=ty*width
		an#=Rnd(360)
		ma#=Rnd(1)*(max_mass-min_mass)+min_mass
		
		b\x=Cos(an)*ra
		b\y=Sin(an)*ra
		b\xs=0:b\ys=0
		b\mass=ma
		t#=(ma-min_mass)/(max_mass-min_mass)*255
		b\r=t
		b\g=t
		b\b=255
	Next
End Function

Function UpdateBlobs()
	For b.Blob=Each Blob
		For t.Blob=Each Blob
			If t=b Then Exit
			dx#=b\x-t\x
			dy#=b\y-t\y
			sq#=1.0/(dx*dx+dy*dy)
			t\xs=t\xs+dx*(b\mass*sq)
			t\ys=t\ys+dy*(b\mass*sq)
			b\xs=b\xs-dx*(t\mass*sq)
			b\ys=b\ys-dy*(t\mass*sq)
		Next
	Next
End Function

Function RenderBlobs()
	For b.Blob=Each Blob
		b\x=b\x+b\xs
		b\y=b\y+b\ys
		Color b\r,b\g,b\b
		Rect b\x*scale-1,b\y*scale-1,3,3
	Next
End Function