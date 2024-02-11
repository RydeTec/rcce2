Const TOPLAND=1
Const BOTTLAND=2
Const SHEEP=3
Const COW=4
Const ALIEN1=5
Const framebuffer=6
Const gleft = 0
Const gright = 1
Const gup = 2
Const gdown = 3
Global count360
Global dy
Global back

Global aliencount=0

Const yes=999
Const no=998
Const intensity=40	;play with this number!
Const width=640,height=387,gravity#=.1

Type Frag
	Field x#,y#,xs#,ys#
	Field r,g,b
End Type

Global levelcounter

Type alientype
	Field x,y,frame,count
End Type

Type cowtype
	Field x,y,frame,count
End Type

Global landcount
Global firing=no
Global firecount=0


Dim images(10)
Dim sounds(10)

Global currentlevel

Global timer = CreateTimer(30)
frames = 0

Dim jump(99)
For i = 0 To 98
	Read jump(i)
Next

Data 9,9,9,9,8,8,8,8,7,7,7,7,6,6,6,6,5,5,5,5,4,4,4,4,3,3,3,3,2,2,2,2,1,1,1,1,0,0,0,0,0,0,0,0,-1,-1,-1,-1,-2,-2,-2,-2,-3,-3,-3,-3,-4,-4,-4,-4,-5,-5,-5,-5,-6,-6,-6,-6,-7,-7,-7,-7,-8,-8,-8,-8,-9,-9,-9,-9,8,7,6,5,4,3,2,1,0,0,-1,-2,-3,-4,-5,-6,-7,-8,0


Dim fire(60)
For i = 1 To 60
	Read fire(i)
Next

Data 0,0,0,0,0,0,1,1,1,1,1,1,2,2,2,2,2,2,3,3,3,3,3,3,4,4,4,4,4,4,5,5,5,5,5,5,6,6,6,6,6,6,7,7,7,7,7,7,8,8,8,8,8,8,9,9,9,9,9,9

Global manx,many,manframe,jumpcount,jumping

; main game loop
init()
gameinit()
dogame()
shutdown()








Function dogame()
	While Not KeyDown(1)
		frames = WaitTimer(timer)
		;If MusicPlaying() = False Then PlayMusic("level"+currentlevel+"\tune.Mid")

		For i = 1 To frames
			gameLogic()
		Next
		
		drawDisplay()
	Wend
End Function
	



Function gameinit()

	currentlevel=1
	levelcounter=1
	levelinit()

End Function

Function doLevelTitle()

	; nowt
	roll()

End Function


Function levelinit()

	; load graphics
	images(TOPLAND) = LoadImage("level"+currentlevel+"\top.bmp")
	images(BOTTLAND) = LoadImage("level"+currentlevel+"\bottom.bmp")
	images(COW) = LoadAnimImage("gfx\sheep.bmp",24,24,0,4)
	images(SHEEP) = LoadAnimImage("gfx\cow.bmp",32,32,0,4)
	images(ALIEN1) = LoadAnimImage("level"+currentlevel+"\alien.bmp",32,32,0,4)
	MaskImage(images(ALIEN1),255,0,255)
	;StopMusic()
	
	For b.alientype= Each alientype
		Delete b
	Next
	
	For c.frag = Each frag
		Delete c
	Next
	
	For d.cowtype = Each cowtype
		Delete d
	Next
	manx=0
	jumping=no
	many=362		
	
	doLevelTitle()
	
End Function




; game logic
Function gameLogic()
	; do stuff here
	levelcounter = levelcounter+1
	If levelcounter > 2000
		currentlevel = currentlevel + 1
		If currentlevel > 10 Then currentlevel = 1
		levelcounter=0
		levelinit()
	EndIf
	
	
	; move land across
	
	
	landcount=landcount-4
	If landcount < 0 landcount=31
	
	manframe=manframe+1
	If manframe > 3 manframe=0
	
	If KeyDown(45)
		manx = manx + 4
		If manx > 618 Then manx = 618
	
	ElseIf KeyDown(44)
		manx = manx - 4
		If manx < 0 Then manx = 0
	
	EndIf

		
	If KeyDown(25) And jumping=no
		jumping=yes
		jumpcount=0
	EndIf
	
	; fire button
	If KeyDown(38) And firing=no
		firing=yes
		PlaySound(sounds(2))
		PlaySound(sounds(4))
		firecount=10
		a.cowtype=New cowtype
		a\x=manx+24
		a\count=0
		a\y=many
		a\frame=0
	EndIf

	; update jumping
	If jumping = yes
		many=many-(jump(jumpcount)*2)
		If jumpcount = 1 Or jumpcount = 80 Then PlaySound(sounds(3))
		jumpcount=jumpcount+1
		If jumpcount > 98 
			jumping=no
			PlaySound(sounds(1))
		EndIf
	EndIf
	
	;update aliens
	For b.alientype = Each alientype
		b\x = b\x - 2
		b\count = b\count + 1
		If b\count = 360 Then b\count = 0
		b\frame = b\frame + 1
		If b\frame > 3 Then b\frame = 0
		b\y = b\y +  (Sin(b\count))
		If b\y > 360 Then b\y = 360
		If b\x < -32 Then Delete b
	Next
	
	aliencount = aliencount+1
	If aliencount > 15-currentlevel
		addalien()
		aliencount=0
	EndIf
	
	
	; update cows
	For a.cowtype=Each cowtype
		a\x = a\x + 8
		a\count = a\count+1
		a\y = a\y + fire(a\count)
		a\frame = a\frame + 1
		If a\frame > 3 Then a\frame = 0
		If a\x > 640
			Delete a
			PlaySound(sounds(2))
		Else If a\y > 372
			createfrag(a\x,372)  
			createfrag(a\x+1,372)  
			createfrag(a\x,373)  
			createfrag(a\x+1,373)  
			Delete a
		EndIf
	Next

	firecount=firecount-1
	If firecount = 0
		firing=no	
	End If
	
	checkcows()
	
End Function


Function checkcows()
	For a.cowtype=Each cowtype
	
		For b.alientype=Each alientype
			If ImagesCollide(images(COW),a\x,a\y,a\frame,images(ALIEN1),b\x,b\y,b\frame)
				createfrag(b\x+16,b\y+16)
				Delete b
				Delete a
				Exit
			End If
		Next
	Next
End Function
			

Function addalien()
	a.alientype=New alientype
	a\x=640
	a\y=Rnd(0,360)
	a\count=0
	a\frame=0
End Function

; System initialisation function
Function init()
	Graphics 640,480
	Cls
	sounds(1) = LoadSound("sfx\baa.wav")
	sounds(2) = LoadSound("sfx\moo.wav")
	sounds(3) = LoadSound("sfx\boing.wav")
	sounds(4) = LoadSound("sfx\pop.wav")
	sounds(5) = LoadSound("sfx\splat.wav")
	landcount=0
	manx=0
	many=362
	manframe=0
	jumping=no
	jumpcount=0

End Function

Function shutdown()
	For a.cowtype=Each cowtype
		Delete a
	Next
	;StopMusic()
	FreeSound(sounds(1))
	FreeSound(sounds(2))
	FreeSound(sounds(3))
	FreeImage(images(TOPLAND))
	FreeImage(images(BOTTLAND))
	FreeImage(images(SHEEP))
	FreeImage(images(COW))
End Function

; Draw the screen
Function drawDisplay()

	SetBuffer BackBuffer()
	Cls

	; do swirly backdrop
	psych()

	; drawland
	For i = (landcount - ((landcount/32)+1)*32) To 640 Step 32
		DrawImage(images(TOPLAND),i,(394))
		DrawImage(images(BOTTLAND),i,(416))
	Next

	; draw sheep
	DrawImage(images(SHEEP),manx,many,manframe)
	
	; draw cows
	For a.cowtype = Each cowtype
		DrawImage(images(COW),a\x,a\y,a\frame)
	Next
	Delete a
	
	; draw aliens
	For b.alientype = Each alientype
		DrawImage(images(ALIEN1),b\x,b\y,b\frame)
	Next
	Delete b
	
	; sparklies
	updatefrags()
	renderfrags()

	Flip
	
	
End Function

Function psych()
	dy=dy-1

	; replace with sheep coords
	musx=manx	
	musy=many


	For x=0 To 640 Step 8

		For y=2 To 395 Step 8
			cv=Rnd(-2,2)
			cv=cv+Abs(x-musx)*Sin(y+(dy))
			cv=cv+Abs(y-musy)*Cos((x+dy)+Sin((x+y)))
			cv=cv+Abs(x-musx)
			bv=Abs(x-musx)*(Sin(y))
			bv=bv+Abs(y-musy)*Sin(x)
			bv=bv+(musx+musy)/12
			bv=bv+(cv/12)


			cv=255-cv
			If bv<0 Then bv=0
			If bv>255 Then bv=255

			If cv>255 Then cv=255
			cv=cv-100
			If cv<0 Then cv=9
			tv=cv+bv
			tv=tv*Sin(dy)*8
			If tv<0 Then tv=0
			If tv>255 Then tv=255
			Color tv,bv,cv+(tv/3)
			;Plot x,y
			Rect x,y,8,8,True
		Next
	Next
End Function

Function CreateFrag(x,y)
	count=Rnd(intensity)+intensity
	PlaySound(sounds(4))
	anstep#=360.0/count
	an#=Rnd(anstep)
	For k=1 To count
		f.Frag=New Frag
		f\x=x
		f\y=y
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

Function roll()
	back=LoadAnimImage("gfx\bigcow.bmp",640,1,0,480)	; Try to load the bitmap
	Repeat
		Cls
		;oldy#=oldy+(MouseY()-oldy)/2
		oldy=128
		sy#=oldy
		ty#=100
		off=(off+1) Mod 480 
		temp=(off-40)
		If temp<0 Then temp=temp+480
		For lop=99 To 0 Step -1
			temp=(temp+1) Mod 480
			DrawImage back,0,lop,temp
		Next
		temp=off
		For lop=1 To 135
			ty=ty+Sin(lop/1.5)
			temp=(temp+1) Mod 480
			DrawImage back,0,ty,temp
		Next
		For lop=1 To 135
			ty=ty+Cos(lop/1.5)
			temp=(temp+1) Mod 480
			DrawImage back,0,ty,temp
		Next
		temp=(temp+20) Mod 480
		For lop=ty+1 To 479
			temp=temp-1
			If temp<0 Then temp=temp+480
			DrawImage back,0,lop,temp
		Next
		Text 20,184,"Level "+currentlevel
		Flip
	Until KeyDown(57)
	Repeat
		Cls
		Flip
	Until Not KeyDown(57)
	FreeImage( back)
End Function