Graphics 640,480

mnt1=LoadImage("mnt1.bmp") : HandleImage mnt1,0,0
mnt2=LoadImage("mnt2.bmp") : HandleImage mnt2,0,0
wat1=LoadImage("wat1.bmp") : HandleImage wat1,0,0
wat2=LoadImage("wat2.bmp") : HandleImage wat2,0,0
wat3=LoadImage("wat3.bmp") : HandleImage wat3,0,0
wat4=LoadImage("wat4.bmp") : HandleImage wat4,0,0
wat5=LoadImage("wat5.bmp") : HandleImage wat5,0,0
wat6=LoadImage("wat6.bmp") : HandleImage wat6,0,0
land=LoadImage("land2.bmp") : HandleImage land,0,0


SetBuffer BackBuffer ()

Repeat
	TX=MouseX():TY=MouseY()
	MX=MX+(TX-MX)/4
	MY=MY+(TY-MY)/4
	Color 0,0,119
	Rect 0,0,640,260
	
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
	DrawImage land,0-offset,228-MY/4
	
	offset=MX*2+waveoff*64
	If offset>384 Then offset=offset-384
	xpos=0-offset
	ypos=514-MY/4
	Repeat
	DrawImage wat6,xpos,ypos
	xpos=xpos+384
	Until xpos>639
	
	Color 0,0,0
	Rect 0,419,640,1

	Color 30,100,0
	Rect 0,420,640,60
	
	Color 255,255,255
	Rect MX-5,MY,11,1:Rect MX,MY-5,1,11
	
	Color 255,255,0
	Text 320,140,"LOVELY PARALLAX DISPLAY",1,0
	Text 320,170,"Move the mouse around",1,0
	Text 320,190,"ESC to quit",1,0
	
	
;	Rect 0,ScanLine(),100,1		; DISPLAY SPEED TEST
				
	
	Flip
Until KeyDown(1)

End