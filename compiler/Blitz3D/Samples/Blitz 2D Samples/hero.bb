
; Hero Written By T.J.Roughton

Graphics 640,480

Global herosprs=LoadAnimImage("hero_spr.bmp",17,25,1,40)
Global statusbar=LoadImage("statusbar.bmp")

ourhero_sprframe=0 ; init to 0
walktime=5 ; slow down the walking :)
walkspeed=2
proplspeed=5
gravityspeed=1
plyy=100

nodown=False ; stop the effect of gravity

SetBuffer BackBuffer()

While Not KeyHit(1)
	Cls
	 
	DrawImage herosprs,plyx-propoffset,plyy-24,strfrmp+ourheropro_sprframe ; draw propellor
	DrawImage statusbar,0,327 ; draw status bar
	
	; basic left right controls.
	;
	;
	If KeyDown(200)<>True
		; Walk
		proplspeed=5
		If KeyDown(203)
			If KeyDown(203) ; left
				plyx=plyx-walkspeed : walkcnt=walkcnt+1
				If walkcnt=>walktime
					ourhero_sprframe=ourhero_sprframe+1
					walkcnt=0
				EndIf
				If ourhero_sprframe<8
					ourhero_sprframe=8
				EndIf
				If ourhero_sprframe>15
					ourhero_sprframe=8
				EndIf
				drawplayer(plyx,plyy,ourhero_sprframe)
				plystnd=0
			Else
				walkcnt=0
			EndIf
		Else
			If KeyDown(205) 
			; right
				plyx=plyx+walkspeed : walkcnt=walkcnt+1
				If walkcnt=>walktime
					ourhero_sprframe=ourhero_sprframe+1
					walkcnt=0
				EndIf
				If ourhero_sprframe>6
					ourhero_sprframe=0
				EndIf
				drawplayer(plyx,plyy,ourhero_sprframe)
				plystnd=1
			Else
				walkcnt=0
			EndIf
		EndIf

		If KeyDown(205)<>True And KeyDown(203)<>True
			drawplayer(plyx,plyy,19-plystnd)
		EndIf		
		If prplspeed<>2 Then plyy=plyy+2
	Else
		; Fly
		proplspeed=1
		If KeyDown(203)
			plystnd=0
			plyx=plyx-walkspeed
		EndIf
		
		If KeyDown(205)
			plystnd=1
			plyx=plyx+walkspeed
		EndIf
		drawplayer(plyx,plyy,17-plystnd)
		If nodown=False
			plyy=plyy-gravityspeed
		EndIf
	EndIf

	; Propellor
	;
	;
		
	If plystnd=1 Then strfrmp=20 : propoffset=-1 
	If plystnd=0 Then strfrmp=28 : propoffset=1

	If ourheropro_sprframe>6
		ourheropro_sprframe=0
	EndIf
	
	propcnt=propcnt+1
	If propcnt>proplspeed
		propcnt=0
		ourheropro_sprframe=ourheropro_sprframe+1
	EndIf
	
	If plyx>640 Then plyx=0 		
	If plyx<0 Then plyx=640

	If plyy=>300 Then plyy=300 ; Used to exit section and go to next section eventually.
	

	; Okay the general idea is this, the hole section is made out of rect
	; this rect(s) are stored in data statements, the loop flys through the
	; cords of where all rect are, and detects collsions for all of them
	; or at least that is the idea.
	
	If ImageRectOverlap(herosprs,plyx,plyy,0,230,200,95)
		nodown=True		
		If plyy<230          ; so in other words if he has hit an object y down then top else!?
			plyy=230-26		 ; why -26?, if not he would sink into the wall, and you'd see the propellor.
		Else				 ; he has hit is left right x stop player from going into it :)
			plyx=200
		EndIf
	Else
		nodown=False
	EndIf
	
	
	;
	; NB: This next peace of code, is just for test reasons. it will move to the map drawing
	; functon later.
	;
	
	Color 255,129,30
	Rect 0,230,200,95

	Flip
Wend

; Draw player, put here because I want to find it again, and might want to add things to it
; x,y,frame
;
Function drawplayer(plyx,plyy,frame)
	DrawImage herosprs,plyx,plyy,frame
End Function

; Draw Map :)
; level,which section within the level
;
Function drawmap(level,section)
End Function