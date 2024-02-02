
;AppTitle "FirePaint!"
Const intensity=20	;play with this number!
;My P3-666 can handle '40' without dropping a frame (debug off)
;This looks AWESOME!

Const width=640,height=480,gravity#=.1

Graphics width,height
SetBuffer BackBuffer()

Type Frag
	Field x#,y#,xs#,ys#
	Field r,g,b
End Type

While Not KeyDown( 1 )
	
	UpdateFrags()
	
	Cls
	
	If MouseDown(1)
		CreateFrags()
	Else
		Color 255,255,255
		Rect MouseX(),MouseY()-3,1,7
		Rect MouseX()-3,MouseY(),7,1
	EndIf
	
	RenderFrags()
	Flip
Wend

End

Function CreateFrags()
	count=Rnd(intensity)+intensity
	anstep#=360.0/count
	an#=Rnd(anstep)
	For k=1 To count
		f.Frag=New Frag
		f\x=MouseX()
		f\y=MouseY()
		f\xs=Cos( an ) * Rnd( 3,4 )
		f\ys=Sin( an ) * Rnd( 3,4 )
		f\r=255:f\g=255:f\b=255
		an=an+anstep
	Next
End Function

Function UpdateFrags()
	For f.Frag=Each Frag
		f\x=f\x+f\xs
		f\y=f\y+f\ys
		f\ys=f\ys+gravity
		If f\x<0 Or f\x>=width Or f\y>=height
			Delete f
		Else If f\b>0
			f\b=f\b-5
		Else If f\g>0
			f\g=f\g-3
		Else If f\r>0
			f\r=f\r-1
			If f\r=0 Then Delete f
		EndIf
	Next
End Function

Function RenderFrags()
	For f.Frag=Each Frag
		Color f\r,f\g,f\b
		Rect f\x-1,f\y-1,3,3
	Next
End Function