Graphics 640,480

land2=CreateImage(1800,600) : HandleImage land2,0,0
SetBuffer ImageBuffer(land2)
Delay 1
Cls

mnt1=LoadImage("mnt1.bmp") : HandleImage mnt1,0,0
mnt2=LoadImage("mnt2.bmp") : HandleImage mnt2,0,0
wat1=LoadImage("wat1.bmp") : HandleImage wat1,0,0
wat2=LoadImage("wat2.bmp") : HandleImage wat2,0,0
wat3=LoadImage("wat3.bmp") : HandleImage wat3,0,0
wat4=LoadImage("wat4.bmp") : HandleImage wat4,0,0
wat5=LoadImage("wat5.bmp") : HandleImage wat5,0,0
wat6=LoadImage("wat6.bmp") : HandleImage wat6,0,0
sun=LoadImage("sun.bmp") : MaskImage sun,255,0,0 : HandleImage sun,0,0
cl1=LoadImage("cloud1.bmp") : MaskImage cl1,255,0,0 : HandleImage cl1,0,0
cl2=LoadImage("cloud2.bmp") : MaskImage cl2,255,0,0 : HandleImage cl2,0,0
cl3=LoadImage("cloud3.bmp") : MaskImage cl3,255,0,0 : HandleImage cl3,0,0
cl4=LoadImage("cloud4.bmp") : MaskImage cl4,255,0,0 : HandleImage cl4,0,0

bar=CreateImage(640,61) : HandleImage bar,0,0
SetBuffer ImageBuffer(bar)
Delay 1
Color 30,100,0
Rect 0,1,640,60
Color 0,15,0
Rect 0,0,640,1



Type cloud
Field xpos#
Field ypos
Field xadd#
Field id
End Type

For row#=1 To 4
temp=-300:y=110-((row-1)*row)*6:add#=row/2
For lop=1 To 15
If temp<640
cl.cloud = New cloud
cl\xpos=temp:cl\ypos=y:cl\xadd=add
Select row
Case 1
cl\id=cl1
Case 2
cl\id=cl2
Case 3
cl\id=cl3
Case 4
cl\id=cl4

End Select
End If
temp=temp+(row+1)*60+Rnd(row*20)

Next
Next

SetBuffer BackBuffer()
Delay 1

Repeat
	TX=MouseX():TY=MouseY():MB1=MouseDown(1):MB2=MouseDown(2)
	oldx#=MX
	oldy#=MY

	MX=MX+(TX-MX)/4
	MY=MY+(TY-MY)/4

	If MB1
		tempaddx#=(MX-oldx)/16
		tempaddy#=(MY-oldy)/16
		SetBuffer ImageBuffer(land2)
		Color 127,80,0
		For lop=1 To 16
			oldx=oldx+tempaddx
			oldy=oldy+tempaddy
			Oval oldx*2.82-20,oldy*1.25-20,41,41
		Next
		SetBuffer BackBuffer()
	Else
	If MB2
		tempaddx#=(MX-oldx)/16
		tempaddy#=(MY-oldy)/16
		SetBuffer ImageBuffer(land2)
		Color 0,0,0
		For lop=1 To 16
			oldx=oldx+tempaddx
			oldy=oldy+tempaddy
			Oval oldx*2.82-20,oldy*1.25-20,41,41
		Next
		SetBuffer BackBuffer()
	End If
	End If
	Color 0,0,119
	Rect 0,0,640,260
	
	DrawImage sun,100,30
	
	For cl.cloud=Each cloud
	cl\xpos=cl\xpos+cl\xadd
	If cl\xpos>739
	cl\xpos=-300
	End If
	DrawImage cl\id,cl\xpos,cl\ypos
	Next
	
	animoff=animoff+1 Mod 30
	waveoff%=animoff/5
	
	ypos=260-MY/40

	offset=MX/15
	If offset>348 Then offset=offset-348
	xpos=0-offset
	Repeat
	DrawImage mnt1,xpos,ypos
	xpos=xpos+348
	Until xpos>639
	
	ypos=ypos+28
	offset=MX/10
	If offset>336 Then offset=offset-336
	xpos=0-offset
	Repeat
	DrawImage mnt2,xpos,ypos
	xpos=xpos+336
	Until xpos>639
	
	ypos=ypos+36
	offset=MX/8+waveoff*40
	If offset>240 Then offset=offset-240
	xpos=0-offset
	Repeat
	DrawImage wat1,xpos,ypos
	xpos=xpos+240
	Until xpos>639
	
	offset=MX/6+waveoff*44
	If offset>264 Then offset=offset-264
	xpos=0-offset
	ypos=ypos-MY/64+17
	Repeat
	DrawImage wat2,xpos,ypos
	xpos=xpos+264
	Until xpos>639
	
	offset=MX/4+waveoff*48
	If offset>288 Then offset=offset-288
	xpos=0-offset
	ypos=ypos-MY/40+24
	Repeat
	DrawImage wat3,xpos,ypos
	xpos=xpos+288
	Until xpos>639
	
	offset=MX/2+waveoff*52
	If offset>312 Then offset=offset-312
	xpos=0-offset
	ypos=ypos-MY/32+32
	Repeat
	DrawImage wat4,xpos,ypos
	xpos=xpos+312
	Until xpos>639
	
	offset=MX+waveoff*56
	If offset>336 Then offset=offset-336
	xpos=0-offset
	ypos=ypos-MY/24+40
	Repeat
	DrawImage wat5,xpos,ypos
	xpos=xpos+336
	Until xpos>639
	
	offset=MX*1.82
	DrawImage land2,0-offset,0-MY/4
	
	offset=MX*2+waveoff*64
	If offset>384 Then offset=offset-384
	xpos=0-offset
	ypos=514-MY/4
	Repeat
	DrawImage wat6,xpos,ypos
	xpos=xpos+384
	Until xpos>639
	
	DrawImage bar,0,419
	
	Color 255,255,255
	Rect MX-5,MY,11,1:Rect MX,MY-5,1,11
	
	Color 255,200,127;255
	Text 320,140,"LOVELY PARALLAX DISPLAY",1,0
	Text 320,170,"Move the mouse around",1,0
	Text 320,190,"Draw with LMB - Erase with RMB",1,0
	Text 320,210,"ESC to quit",1,0
	
	
;	Rect 0,ScanLine(),100,1		; DISPLAY SPEED TEST
				
	Flip
	
Until KeyDown(1)

End