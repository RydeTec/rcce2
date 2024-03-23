Global numcolours=256	; play with this value
SeedRnd(312498756)		; and this too

Const width=640,height=480

Type Ball
	Field x#,y#,xs#,ys#
	Field col
End Type

Graphics width,height
SetBuffer BackBuffer ()

Global bcnt=0

Dim ball_image(numcolours)

For loop=0 To numcolours
;	Color Rnd(255),Rnd(255),Rnd(255)
	rff=70:gff=50:bff=70: Color rff+Rnd(255-rff),gff+Rnd(255-gff),bff+Rnd(255-bff)

	Oval 0,0,16,16

	; draws eyes and mouth..
	Color 0,0,0
	Plot 5,5:Plot 11,5
	Plot 4,8:Plot 4,9:Plot 5,10 Plot 12,8:Plot 12,9:Plot 11,10: Line 6,11,11,11
	
	ball_image(loop)=CreateImage( 16,16 )
	GrabImage ball_image(loop),0,0
	ScaleImage ball_image(loop),2,2
	ScaleImage ball_image(loop),.5,.5
Next

Color 255,255,0

timer=CreateTimer( 60 )

CreateBalls()

While Not KeyDown( 1 )
	ticks=WaitTimer( timer )
	For k=1 To ticks
		UpdateBalls()
	Next
	
	Cls
	Text 0,0,"Ticks="+ticks+" Balls="+bcnt
	Text 0,FontHeight(),"Arrow key left to remove - Arrow key right to add"
	RenderBalls()
	Flip
Wend

End

Function CreateBalls()
	For k=1 To 1
		bcnt = bcnt + 1
		b.Ball=New Ball
		b\x=Rnd( 8,width-8 )
		b\y=Rnd( 8,height-8 )
		b\xs=Rnd( 1,2 )
		If Rnd(1)<.5 Then b\xs=-b\xs
		b\ys=Rnd( 1,2 )
		b\col=Rnd(numcolours)
		If Rnd(1)<.5 Then b\ys=-b\ys
	Next
End Function

Function RemoveBalls()
	For k=1 To 1
		bcnt = bcnt - 1
		Delete First Ball
	Next
End Function

Function UpdateBalls()
	If KeyDown( 205 )
		CreateBalls()
	Else If KeyDown( 203 )
		RemoveBalls()
	EndIf
	For b.Ball=Each Ball
		b\x=b\x+b\xs
		If b\x<8 Or b\x>width-8 Then b\xs=-b\xs
		b\y=b\y+b\ys
		If b\y<8 Or b\y> height-8 Then b\ys=-b\ys
	Next
End Function

Function RenderBalls()
	For b.Ball=Each Ball
		DrawImage ball_image(b\col),b\x,b\y
	Next
End Function
