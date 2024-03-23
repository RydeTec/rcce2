; A simple shooter that Simon A(cid) & I wrote in an hour or so - never commented or completed
; georgebray@breathemail.net
; For Blitz downloads and help point your browser to:
; http://users.breathemail.net/georgebray  
; ctrl = fire
; arrow keys move you left and right.

AppTitle "Blitz Camels"

shootsound=LoadSound("shot.wav")



Const width=640,height=480

Const lkey=203,rkey=205,dkey=208,ukey=200

Type star
	Field x#,y,r,g,b,px#
End Type

Type bullet
	Field x,y,vx,vy
End Type

Type alien
	Field x#,y#,a,va
End Type

Graphics width,height

SetBuffer BackBuffer ()

Dim camelframes(4)

camelframes(0)=LoadImage("camel4.bmp")
camelframes(1)=LoadImage("camel1.bmp")
camelframes(2)=LoadImage("camel2.bmp")
camelframes(3)=LoadImage("camel3.bmp")

Global alienframe=LoadImage("alien.bmp")

ClsColor 0,0,0

camely=height-50

setupstars()

While KeyDown(1)=0
	
	VWait
	
	t=t+1

	Cls
	Color 16,80,16
	Rect 0,height-16,width,16

	DrawImage camelframes((t/10) And 3),x,camely	;change and 3 to mod 4

	If (t And 15)=15 Then addalien()

	vx=0
	If KeyDown(lkey) vx=-1
	If KeyDown(rkey) vx=1
	
	If KeyHit(29) PlaySound shootsound:addbullet(x+50,camely+2)	
	x=x+vx
	If x<0 Then x=width
	If x>width Then x=0

	updatestars(vx)
	updatebullets()
	updatealiens()
				
;	If KeyDown(ukey) y=y-1
;	If KeyDown(dkey) And y<height-20 Then y=y+1	
								
	Flip
Wend

End

Function addalien()
	a.alien=New alien
	a\x=width
	a\y=Rnd(height)
	a\a=270
	a\va=Rnd(2,10)
End Function

Function updatealiens()
	For a.alien=Each alien
		tx=4*Cos(2*Pi*a\a/360)-4
		ty=4*Sin(2*Pi*a\a/height-100) 
	
		a\x=a\x+tx
		a\y=a\y+ty
		a\a=a\a+a\va
		DrawImage alienframe,a\x,a\y
	Next
	End Function 
	
	
	



Function addbullet(x,y)
	a.bullet=New bullet
	a\x=x
	a\y=y
	a\vx=6
	a\vy=-4
End Function

Function updatebullets()
	For a.bullet=Each bullet	
		ox=a\x
		oy=a\y
		a\x=a\x+a\vx
		a\y=a\y+a\vy
		Color Rnd(256),Rnd(256),Rnd(256)
		Line ox,oy,a\x,a\y
		Line ox,oy+1,a\x,a\y+1
		If a\y<0 Then Delete a
		
			
	Next
End Function
			
Function setupstars()
	For i=0 To 100
		a.star=New star
		a\x=Rnd(width)
		a\y=Rnd(height-10)
		a\r=Rnd(256)
		a\g=Rnd(256)
		a\b=Rnd(256)
		a\px=Rnd(.5,1.5)
	Next
End Function

Function updatestars(vx)
	For a.star=Each star
		Color a\r,a\g,a\b
		Plot a\x,a\y
		
		a\r=a\r+Rnd(-10,10)
		a\g=a\g+Rnd(-10,10)
		a\b=a\b+Rnd(-10,10)
	
		a\x=a\x-vx*a\px
		If a\x<0 Then a\x=width
	If 	a\x>width Then a\x=0
	Next
End Function