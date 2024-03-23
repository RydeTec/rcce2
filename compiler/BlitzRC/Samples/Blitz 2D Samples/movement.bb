;MOVEMENT FUNCTIONS
;by Warpy (Christian Perfect)

Function Newxvalue(x#,angle,dist#)
Newx#=Sin(angle)*dist+x
Return Newx
End Function
Function Newyvalue(y#,angle,dist#)
Newy#=Cos(angle)*dist+y
Return Newy
End Function

Function Newangle(x#,y#)
	If y=0
		If x<0 Then angle=270 Else angle=90
	EndIf
	If x=0
		If y>0 Then angle=0 Else angle=180
	EndIf
	If y<>0 And x<>0
		dist#=Sqr(x*x+y*y)
		angle=ACos(y/dist)
		If y>0 And x<0 Then angle=wrapvalue(angle+270)
		If y<0 And x<0 Then angle=wrapvalue(angle+180)
		If y<0 And x>0 Then angle=wrapvalue(angle+90)
	EndIf
Return angle
End Function

Function wrapvalue(angle)
zangle=angle Mod 360
If angle<0 Then zangle=360+angle
Return zangle
End Function

;Demo of how to use them
Type bullet
	Field x#,y#,an
End Type
Type asteroid
	Field size,x,y,xs,ys,turn
End Type
.start
For count=1 To 10
	a.asteroid=New asteroid
	a\x=Rnd(640)
	a\y=Rnd(480)
	a\size=50
	a\xs=Rnd(-4,4)
	a\ys=Rnd(-4,4)
	a\turn=0
Next
Graphics 640,480:SetBuffer FrontBuffer ()
xs#=0 : ys#=0
x#=320 : y#=240
While Not KeyHit(1)
Cls
	asteroidcount=0
	For a.asteroid=Each asteroid
		asteroidcount=asteroidcount+1
		a\turn=wrapvalue(a\turn+5)
		a\x=a\x+a\xs
		a\y=a\y+a\ys
		If a\x<0 Or a\x>640 Then a\xs=0-a\xs
		If a\y<0 Or a\y>480 Then a\ys=0-a\ys
		x1=Newxvalue(a\x,wrapvalue(a\turn-15),a\size)
		y1=Newyvalue(a\y,wrapvalue(a\turn-15),a\size)
		x2=Newxvalue(a\x,wrapvalue(a\turn+15),a\size)
		y2=Newyvalue(a\y,wrapvalue(a\turn+15),a\size)
		x3=Newxvalue(a\x,wrapvalue(a\turn+90),a\size)
		y3=Newyvalue(a\y,wrapvalue(a\turn+90),a\size)
		x4=Newxvalue(a\x,wrapvalue(a\turn+165),a\size)
		y4=Newyvalue(a\y,wrapvalue(a\turn+165),a\size)
		x5=Newxvalue(a\x,wrapvalue(a\turn+195),a\size)
		y5=Newyvalue(a\y,wrapvalue(a\turn+195),a\size)
		x6=Newxvalue(a\x,wrapvalue(a\turn-90),a\size)
		y6=Newyvalue(a\y,wrapvalue(a\turn-90),a\size)
;		Line x1,y1,x2,y2
;		Line x2,y2,x3,y3
;		Line x3,y3,x4,y4
;		Line x4,y4,x5,y5
;		Line x5,y5,x6,y6
;		Line x6,y6,x1,y1
		Rect x1,y1,1,1
		Rect x2,y2,1,1
		Rect x3,y3,1,1
		Rect x4,y4,1,1
		Rect x5,y5,1,1
		Rect x6,y6,1,1
		dead=0
		For b.bullet=Each bullet
			If dead=0 Then If b\x>a\x-a\size And b\x<a\x+a\size And b\y>a\y-a\size And b\y<a\y+a\size
				If a\size>6
					For c=1 To 3
						as.asteroid=New asteroid
						as\x=a\x
						as\y=a\y
						as\xs=Rnd(-4,4)
						as\ys=Rnd(-4,4)
						as\turn=0
						as\size=a\size/2
					Next
				EndIf
				Delete a
				dead=1
				Delete b
			EndIf
		Next
	Next
	If asteroidcount=0 Then Goto start
	oldan=an
	an=wrapvalue(an+KeyDown(203)*5-KeyDown(205)*5)
	If KeyDown(200)=1
		xvec#=Newxvalue(0,an,1)/10.0
		yvec#=Newyvalue(0,an,1)/10.0
		xs=xs+xvec
		ys=ys+yvec
	EndIf
	If KeyDown(208)=1
		xvec#=Newxvalue(0,wrapvalue(an+180),1)/10.0
		yvec#=Newyvalue(0,wrapvalue(an+180),1)/10.0
		xs=xs+xvec
		ys=ys+yvec
	EndIf
	If KeyDown(57)=1
		b.bullet=New bullet
		b\x=x
		b\y=y
		b\an=an
	EndIf
	For b.bullet=Each bullet
		b\x=Newxvalue(b\x,b\an,5)
		b\y=Newyvalue(b\y,b\an,5)
		Rect b\x,b\y,1,1
		If b\x<0 Or b\x>640 Or b\y<0 Or b\y>480 Then Delete b
	Next
	x=x+xs
	y=y+ys
	If x<0 Or x>640 : xs=0-xs : x=x+xs : EndIf
	If y<0 Or y>480 : ys=0-ys : y=y+ys : EndIf
	Color 255,255,0
	Line Newxvalue(x,wrapvalue(an+180-15),5),Newyvalue(y,wrapvalue(an+180-15),10),Newxvalue(x,an,5),Newyvalue(y,an,5)
	Line Newxvalue(x,wrapvalue(an+195),5),Newyvalue(y,wrapvalue(an+195),10),Newxvalue(x,an,5),Newyvalue(y,an,5)
	Color 255,255,255
Flip
Wend