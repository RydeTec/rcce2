
;  Baa ;)

Const GRID=1
Const GRID2=2
Const GRID3=3
Const GRID4=4
Const framebuffer=5
Const SHIP=6
Const GUNBOT=7
Const GUNSIDE=8
Const yes=999
Const no=998
Const intensity=40	;play with this number!
Const width=640,height=387
Const GRIDX=31
Const GRIDY=18

Global cannoncount

Global cannonx, cannony

Global psychX, psychY, psychXdir, psychYdir, psychCount
Global dy
Type Frag
	Field x#,y#,xs#,ys#
	Field r,g,b
End Type

Dim images(10)
Dim sounds(10)
Dim gridmap(GRIDX,GRIDY)

Global manx, many
Global gridframe
Global timer = CreateTimer(30)
frames = 0

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

	manx=16
	many=18
	
End Function

Function doLevelTitle()


End Function


Function levelinit()

	;StopMusic()
	
	For c.frag = Each frag
		Delete c
	Next
		
End Function




; game logic
Function gameLogic()
	; do stuff here
	
	If JoyXDir() = -1 Or KeyDown(24)
		manx = manx-1
		If manx < 0 Then manx = 0
	Else If JoyXDir()=1 Or KeyDown(25)
		manx = manx+1
		If manx > GRIDX Then manx = GRIDX
	EndIf

	If JoyYDir() = -1 Or KeyDown(16)
		many = many-1
		If many < 12 Then many = 12
	Else If JoyYDir()=1 Or KeyDown(30)
		many = many+1
		If many > GRIDY Then many = GRIDY
	EndIf




	; Update cannons
	cannoncount = cannoncount + 1
	If cannoncount > 5
		cannonx = cannonx + 1
		If cannonx > GRIDX Then cannonx = 0
		cannony = cannony + 1
		If cannony > GRIDY Then cannony = 0
		cannoncount = 0
	EndIf
	
	

	
	; Update swirly thingy
	psychX = psychX + psychXdir
	If psychX > 639 
		psychXdir = psychXdir * -1
		psychX = 639
	Else If psychX < 0
		psychXdir = psychXdir * -1
		psychX = 0
	EndIf
	
	psychY = psychY + psychYdir
	If psychY > 479
		psychYdir = psychYdir * -1
		psychY = 479
	Else If psychY < 0
		psychYdir = psychYdir * -1
		psychY = 0
	EndIf
	
	psychCount = psychCount + 1
	If psychCount > 1000 
		psychCount = 0
		psychXdir = Rnd(1,6)
		psychYdir = Rnd(1,6)
	EndIf
	
End Function



; System initialisation function
Function init()
	Graphics 640,480
	Cls
	; load graphics
	images(GRID) = LoadImage("gfx\grid1.bmp")
	MaskImage(images(GRID),255,0,255)
	images(GRID2) = LoadImage("gfx\grid2.bmp")
	MaskImage(images(GRID2),255,0,255)
	images(GRID3) = LoadImage("gfx\grid3.bmp")
	MaskImage(images(GRID3),255,0,255)
	images(GRID4) = LoadImage("gfx\grid2.bmp")
	MaskImage(images(GRID4),255,0,255)	
	images(SHIP) = LoadImage("gfx\player-ship.bmp")
	MaskImage(images(SHIP),255,0,255)
	images(GUNBOT) = LoadImage("gfx\gun-bot.bmp")
	MaskImage(images(GUNBOT),255,0,255)
	images(GUNSIDE) = LoadImage("gfx\gun-side.bmp")
	MaskImage(images(GUNSIDE),255,0,255)

	psychCount = 0
	psychXdir = Rnd(1,6)
	psychYdir = Rnd(1,6)

	gridframe=0
	cannonx = 0
	cannony = 0

End Function

Function shutdown()
	;StopMusic()
	FreeImage(images(GRID))
End Function

; Draw the screen
Function drawDisplay()

	SetBuffer BackBuffer()
	Cls

	; do swirly backdrop
	psych()
	
	; Draw logo
	;DrawImage(images(LOGO),64,48)
	
	For x = 0 To GRIDX
		For y = 0 To GRIDY
				gridframe = gridframe+1
				If gridframe > 2 Then gridframe = 0
				DrawImage(images((GRID)),(32 - manX*2)+64+(x*16), (18 - manY*2)+128+(y*16))			
		Next
	Next

	; Draw Ship
	DrawImage(images(SHIP),(32 - manX*2)+64+(manx*16), (18-manY*2)+128+(many*16))

	; Enemy cannons
	; Bottom cannon
	DrawImage(images(GUNBOT),(32 - manX*2)+64+(cannonx*16), (18-manY*2)+128+(19*16))
	; Side cannon
	DrawImage(images(GUNSIDE),(32-manX*2)+48, (18-manY*2)+128+(cannony*16))

	; sparklies
	updatefrags()
	renderfrags()

	Flip	
	
End Function

Function psych()
	dy=dy-1

	; replace with sheep coords
	musx=	psychX
	musy=   psychY 


	For x=0 To 640 Step 8

		For y=0 To 480 Step 8
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
			Color tv/4,bv/4,cv+(tv/3)/4
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