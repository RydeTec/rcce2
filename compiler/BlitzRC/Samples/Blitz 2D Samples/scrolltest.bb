;******************************
;*     Simple Scroll Test     *
;*      By:  Rick Felice      *
;******************************							  


; Set graphics mode to 640x480
Graphics 800,600
; Draw to back buffer
SetBuffer BackBuffer()

; load the images, and set the masks (for transparency)
mite1=LoadImage("mite1.bmp")
MaskImage mite1,255,0,255
mite2=LoadImage("mite2.bmp")
MaskImage mite2,255,0,255
mite3=LoadImage("mite3.bmp")
MaskImage mite3,255,0,255
tite1=LoadImage("tite1.bmp")
MaskImage tite1,255,0,255
tite2=LoadImage("tite2.bmp")
MaskImage tite2,255,0,255
tite3=LoadImage("tite3.bmp")
MaskImage tite3,255,0,255

bott=LoadImage("floor.bmp")
MaskImage bott,255,0,255
topp=LoadImage("ceiling.bmp")
MaskImage topp,255,0,255
cave=LoadImage("cave.bmp")
MaskImage cave,255,0,255
stars=LoadImage("simplestars.bmp")

; load the ship frames
Dim shipf(2)
shipf(0)=LoadImage("smallfighter01.bmp")
MaskImage shipf(0),255,0,255
shipf(1)=LoadImage("smallfighter01.bmp")
MaskImage shipf(1),255,0,255
shipf(2)=LoadImage("smallfighter01.bmp")
MaskImage shipf(2),255,0,255

; bullet coordinants
Dim shootx(12)
Dim shooty(12)

; scroll speed for each layer
Dim scrollspeed(5)
; x coordinant for each layer
Dim sx(5)

; set scroll speeds for each layer
scrollspeed(0)=1
scrollspeed(1)=2
scrollspeed(2)=3
scrollspeed(3)=1
scrollspeed(4)=2
scrollspeed(5)=3


; Initial ship coordinants
shipx#=50
shipy#=200

shipspeed#=1.5

;main loop
Repeat

	;clear the screen	
	Cls
	
	;draw stars background
	TileImage stars,0,0

	; cave scroll variable decreases to scroll the cave left
	cs=cs-1
	; 340 is the width of the tile, so reset at that point
	If cs<=0 Then cs=340
	
	; draw the cave wall all the way across the screen
	For x=-340 To 800 Step 340
		DrawImage cave,x+cs,133
	Next ;x
	
	; stalagtite/mite variable decreases to scroll them left too
	s=s-1
	; 87 is the width of the tile, so reset at that point
	If s<=0 Then s=87

	; for each of the stalag. scroll layers...
	For snum=0 To 5
		; scroll by the scroll speed for that layer
		sx(snum)=sx(snum)+scrollspeed(snum)
	
		; draw them all the way across the screen
		For x=-87 To 950 Step 87
	
				Select snum
				Case 0: 
					;stalagtite
					DrawImage tite3,x+s-sx(snum),108
				Case 1:
					;stalagtite
					DrawImage tite2,x+s-sx(snum),70
				Case 2:
					;stalagtite
					DrawImage tite1,x+s-sx(snum),45
				Case 3:
					;stalagmite
					DrawImage mite3,x+s-sx(snum),356
				Case 4:
					;stalagmite
					DrawImage mite2,x+s-sx(snum),392
				Case 5:
					;stalagtite
					DrawImage mite1,x+s-sx(snum),417
				End Select 

		Next ;x
		
		; 87 is the size of the tile
		sx(snum)=sx(snum) Mod 87
	Next ;y

	; loop through the ship frames (0,1,2)
	fdelay=fdelay+1
	If fdelay>3
		f=f+1
		If f>2 Then f=0
		fdelay=0
	EndIf
	
	; draw the ship
	DrawImage shipf(f),Int(shipx#),Int(shipy#)
	
	; move with the arrows
	shipx#=shipx#+(KeyDown(205)-KeyDown(203))*shipspeed#
	shipy#=shipy#+(KeyDown(208)-KeyDown(200))*shipspeed#

	; limit ship to the edges of the screen
	If shipx#<2 Then shipx#=2
	If shipx#>650 Then shipx#=650
	If shipy#<20 Then shipy#=20
	If shipy#>450 Then shipy#=450
	
	; shoot with the space bar (no more than 11 shots)
	If KeyDown(57) And shots<13
	
		; delay between shots
		scount=scount+1 
		If scount>15
			; position the bullets
			shootx(shoot)=shipx#+120
			shooty(shoot)=shipy#+55
			
			; increment the shot number
			shoot=shoot+1
			If shoot>12 Then shoot=0
			
			; increment the number of shots
			shots=shots+1
			
			; reset the delay counter
			scount=0
		EndIf
	EndIf
	
	; yellow bullets
	Color 255,255,0
	
	; draw all the shots
	For sht=0 To 12
		; if you've fired it
		If shootx(sht)<>0
			; move it forward
			shootx(sht)=shootx(sht)+4
			; draw it
			Line shootx(sht),shooty(sht),shootx(sht)+10,shooty(sht)
			; once it goes off screen...
			If shootx(sht)>=850
				; reset it
				shootx(sht)=0
				; decrease the number of shots
				shots=shots-1
			EndIf
		EndIf
	Next ;sht

	; move the floor/ceiling
	fs=fs-6
	; the floor/ceiling tile is 300 pixels across
	If fs<=0 Then fs=300

	; draw them across the screen
	For x=-300 To 800 Step 300
		DrawImage topp,x+fs,0
		DrawImage bott,x+fs,455
	Next ;x

	showscore()

	; swap buffers
	Flip
	
; loop until you hit escape.  
Until KeyDown(1)

End

Function showscore()


	Color 70,70,70
	Rect 0,550,800,50
	Color 160,160,160
	Rect 0,550,799,49
	Color 120,120,120
	Rect 1,551,798,48
	
	dotext("Lives:",5,555,200,200,200)
	
	dotext("5",60,555,128,255,255)

End Function

Function dotext(t$,x,y,r,g,b)

Color 0,0,0
Text x,y,t$

Color r,g,b
Text x+1,y+1,t$


End Function