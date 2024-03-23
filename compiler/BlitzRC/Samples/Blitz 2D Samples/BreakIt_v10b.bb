;*************************************
;*      Idiot Soft. Presents...      *
;*     Break It! version 1.0beta     *
;*************************************

AppTitle "Break It!"

Const Swidth=640,Sheight=480,Levels=5
Global ballX, ballY, ballXdir, ballYdir, lives, Mouse_X, Mouse_Y,X,Y,l,score

Dim blox(7,7,Levels)
Dim numblox(Levels)

For l=0 To Levels
	Select l
	Case 0: Restore Level0
	Case 1: Restore Level1
	Case 2: Restore Level2
	Case 3: Restore Level3
	Case 4: Restore Level4
	Case 5: Restore Level5
	End Select	
	For by=0 To 7
		For bx=0 To 7
			Read blox(bx,by,l)
			If blox(bx,by,l)>0 And blox(bx,by,l)<8 Then numblox(l)=numblox(l)+1
		Next ; bx
	Next ; by
Next ;l
.Level0
Data 0,0,0,0,0,0,0,0
Data 1,1,1,1,1,1,1,1
Data 1,1,1,1,1,1,1,1
Data 1,1,1,1,1,1,1,1
Data 1,1,1,1,1,1,1,1
Data 1,1,1,1,1,1,1,1
Data 1,1,1,1,1,1,1,1
Data 1,1,1,1,1,1,1,1
.Level1
Data 0,0,0,0,0,0,0,0
Data 0,2,2,2,2,2,2,0
Data 0,2,2,2,2,2,2,0
Data 0,2,2,2,2,2,2,0
Data 0,1,1,1,1,1,1,0
Data 0,1,1,1,1,1,1,0
Data 0,1,1,1,1,1,1,0
Data 0,0,0,0,0,0,0,0
.Level2
Data 0,0,0,0,0,0,0,0
Data 0,8,8,8,8,8,8,0
Data 0,1,1,1,1,1,1,0
Data 0,1,2,2,2,2,1,0
Data 0,1,2,3,3,2,1,0
Data 0,1,2,2,2,2,1,0
Data 0,1,1,1,1,1,1,0
Data 0,8,8,8,8,8,8,0
.Level3
Data 0,2,0,2,0,2,0,2
Data 1,0,1,0,1,0,1,0
Data 0,2,0,2,0,2,0,2
Data 1,0,1,0,1,0,1,0
Data 0,2,0,2,0,2,0,2
Data 1,0,1,0,1,0,1,0
Data 0,2,0,2,0,2,0,2
Data 3,8,3,8,3,8,3,8
.Level4
Data 8,3,0,0,0,0,3,8
Data 8,5,7,5,5,7,5,8
Data 8,8,3,0,0,3,8,8
Data 2,8,1,1,1,1,8,2
Data 2,8,1,0,0,1,8,2
Data 0,8,1,0,0,1,8,0
Data 0,8,1,0,0,1,8,0
Data 0,8,8,0,0,8,8,0
.Level5
Data 0,0,0,0,0,0,0,0
Data 0,8,8,0,0,8,8,0
Data 0,8,5,1,1,5,8,0
Data 0,8,6,0,0,6,8,0
Data 0,8,6,0,0,6,8,0
Data 0,8,5,0,0,5,8,0
Data 0,8,5,5,5,5,8,0
Data 3,8,8,8,8,8,8,3

l=0

; Set display mode
Graphics Swidth,Sheight

; Draw to back buffer
SetBuffer BackBuffer()

; Load font
SetFont LoadFont("Arial",20 )

; Initialize the variables
ballY=1000
lives=6

While lives>=0 ; While you are alive...

	;Mark's mouse smoothing hack :)
	Mouse_X=(MouseX()-Mouse_X)/3+Mouse_X 
	Mouse_Y=(MouseY()-Mouse_Y)/3+Mouse_Y 


	Cls

	drawblocks()
	drawpaddle()
	drawball()
	
	checkball2screen()
	checkball2paddle()	
		
	scoreboard()
		
	; Flip screen buffers
	Flip

	If numblox(l)<=0
		If l=Levels
			Color 255,255,255
			Text 120,200,"YOU WIN!!! Press mouse button to End..."
	
			Flip
			
			; Wait for a mouse button...
			While GetMouse()=0
			Wend
			
			End
		EndIf
		l=l+1
		initball()
		EndIf

	If KeyDown( 1 ) Then lives=-1

Wend ; ...Loop


Color 255,255,255
Text 120,200,"YOU LOSE!!! Press mouse button to End..."
Flip
		
; Wait for a mouse button...
While GetMouse()=0
Wend
		
End

Function scoreboard()
	; update the oh-so-interesting score board.
	Color 128,200,255
	Text 5,460,"Level:"+Str$(l+1)+" Lives:"+Str$(lives)+" Blocks Left:"+numblox(l)+" Score:"+score

End Function

Function checkball2screen()
	
	; bounce the ball off the left side
	If ballX<0
		ballX=0
		ballXdir=-ballXdir
	EndIf 
	; bounce the ball off the right side
	If ballX>Swidth-7
		ballX=Swidth-7
		ballXdir=-ballXdir
	EndIf
	; bounce the ball off the top
	If ballY<0 
		ballYdir=-ballYdir
		ballY=0
	EndIf
	; if it hits the bottom, you dead.
	If ballY>Sheight
		lives=lives-1
		initball()
	EndIf
End Function

Function initball()

	Color 255,255,255
	Text 200,200,"Press mouse button to continue..."
	
	; Initialize the ball variables each new life
	ballYdir=2
	ballXdir=-2
	newgame=1
	ballX=300
	ballY=300
	
	; Draw the ball
	Color 128,128,128
	Oval ballX,ballY,6,6,True
	Color 166,166,166
	Oval ballX,ballY,5,5,True
	Color 255,255,255
	Rect ballX+1,ballY+1,2,2,False

	Flip
	
	; Wait for a mouse button...
	While GetMouse()=0
	Wend
	
End Function

Function checkball2paddle()

	; Check the ball for impact against paddle
	If ballY>Sheight-47 And ballY<Sheight-25 And ballX>X-7 And ballX<(X+77)-6
		If ballX<X+10
			ballYdir=-1
			ballXdir=-3
		Else If	 ballX>=X+10 And ballX<X+25
			ballYdir=-2
			ballXdir=-2
		ElseIf ballX>=X+25 And ballX<X+35
			ballYdir=-3
			ballXdir=-1
		ElseIf ballX>=X+35 And ballX<X+45
			ballYdir=-3
			ballXdir=1
		ElseIf ballX>=X+45 And ballX<X+60
			ballYdir=-2
			ballXdir=2
		ElseIf ballX>=X+60
			ballYdir=-1
			ballXdir=3
		EndIf	
	EndIf

End Function

Function checkball2blox(xy)
	
	; get the ball's "block" coordinants
	bby=(ballY+ballYdir)/20
	bbx=(ballX+ballXdir)/80
	
	If bby<8 And bby>=0 And bbx<8 And bbx>=0

		If blox(bbx,bby,l)<>0 
			If xy=1
				ballYdir=-ballYdir
			Else
				ballXdir=-ballXdir			
			EndIf
			If blox(bbx,bby,l)<8 
				blox(bbx,bby,l)=blox(bbx,bby,l)-1
				score=score+5
			EndIf
			If blox(bbx,bby,l)=0 Then numblox(l)=numblox(l)-1
		EndIf

		
			
	EndIf
	
End Function

Function drawblocks()
	
	; Draw each block
	For bx=0 To 7
		For by=0 To 7
			If blox(bx,by,l)>0
				If blox(bx,by,l)=1
					r=128
					g=128
					b=128
				ElseIf blox(bx,by,l)=2
					r=128
					g=200
					b=128
				ElseIf blox(bx,by,l)=3
					r=128
					g=200
					b=0
				ElseIf blox(bx,by,l)=4
					r=0
					g=200
					b=128
				ElseIf blox(bx,by,l)=5
					r=0
					g=200
					b=200
				ElseIf blox(bx,by,l)=6
					r=0
					g=128
					b=200
				ElseIf blox(bx,by,l)=7
					r=0
					g=0
					b=200
				ElseIf blox(bx,by,l)=8
					r=200
					g=200
					b=0
				EndIf

				If r>50 Then r2=r-50 Else r2=0
				If g>50 Then g2=g-50 Else g2=0
				If b>50 Then b2=b-50 Else b2=0
				
				If r<220 Then r3=r+35 Else r3=255
				If g<220 Then g3=g+35 Else g3=255
				If b<220 Then b3=b+35 Else b3=255
								
				Color r2,g2,b2
				Rect bx*80+1,by*20+1,78,18,False
				Color r,g,b
				Rect bx*80+2,by*20+2,76,16,True
				Color r3,g3,b3
				Line bx*80+1,by*20+1,bx*80+78,by*20+1
				Line bx*80+1,by*20+1,bx*80+1,by*20+18
				
				If blox(bx,by,l)=8
					Rect bx*80+4,by*20+4,72,12,True
				EndIf
				
			EndIf
		Next ;by
	Next ;bx
	
End Function

Function drawpaddle()

	; Limit paddle to screen edge
	X=Mouse_X
	If X>(Swidth-70) Then X=(Swidth-70)
	
	Y=Sheight-40

	; Draw darker edges
	Color 128,128,128
	Rect X,Y,70,15
	Color 255,255,255
	
	; Make the shiney almost-special-effect
	For shiney=0 To 9
		Line X+((X+shiney) Mod 70),Y,X+((X+shiney) Mod 70),Y+14
	Next ;shiney

	; Draw main paddle box
	Color 185,185,155
	Rect X+1,Y+1,68,13,True
		
	; Gray dots smooth out the shiney almost-special-effect
	Color 180,180,180
	Plot X+(X Mod 70),Y	
	Plot X+((X+9) Mod 70),Y
	
End Function

Function drawball()

	; Move the ball
	ballX=ballX+(ballXdir*2)
	checkball2blox(0)
	ballY=ballY+(ballYdir*2)
	checkball2blox(1)
	
	; Draw the ball
	Color 128,128,128
	Oval ballX,ballY,6,6,True
	Color 166,166,166
	Oval ballX,ballY,5,5,True
	Color 255,255,255
	Rect ballX+1,ballY+1,2,2,False
End Function

End