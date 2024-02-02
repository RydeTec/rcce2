;
; BOUNCY BALLS - Written by Robert Hutchinson
; This was just a quick test of the MouseXSpeed() and MouseYSpeed()
; commands I knocked up, after they were changed.
;


AppTitle "Whackable Balls"					;
Const width=1024,height=768					; Set up the screen.
Graphics width,height,depth					; 
SetBuffer BackBuffer()						;

BallX=51									; The size of the balls
BallY=51									;
TFormFilter 0

Type ball
	Field x#,y#,xs#,ys#
End Type

Const MaxBalls=2							; Change this for different number of balls.
Global stonea=LoadImage("stone.bmp")		; Load in the ball image from disk.
Global stoneb=LoadImage("stone2.bmp")		; Load in the ball image from disk.
ResizeImage(stonea,BallX,BallY)				; Resize it!
ResizeImage(stoneb,BallX,BallY)				; Resize it!
Global b.ball
Global mx#,my#,mxs#,mys#

bhx=(BallX/2)
bhy=(BallY/2)

For a=0 To MaxBalls							; Keep adding items to our list until it is full.
	b.ball=New ball
	b\x=Rnd(15,width-bhx)
	b\y=Rnd(15,height-bhy)
Next

;
; Main loop.
;
While Not KeyDown(1)						; Do until ESC is pressed
	;
	; Update mouse
	;
	Cls

	mxs=MouseXSpeed()
	mys=MouseYSpeed()
	mx=MouseX()
	my=MouseY()

	If mx<bhx Then mx=bhx
	If mx>width-bhx Then mx=width-bhx
	If my<bhy Then my=bhy
	If my>height-bhy Then my=height-bhy
	;
	; Update balls
	;
	For b.ball=Each ball
		If ImagesCollide(stonea,mx,my,0,stonea,b\x,b\y,0)
			b\xs=mxs*1.05
			b\ys=mys*1.05
		EndIf
		b\x=b\x+b\xs : b\y=b\y+b\ys			; Update each balls' position.
		;
		; Reverse direction if ball gets to the edge of the bitmap.
		;
		If b\x<bhx+20 Or b\x>(width-(bhx+20)) Then b\xs=-b\xs
		If b\y<bhy+20 Or b\y>(height-(bhy+20)) Then b\ys=-b\ys
		
		If b\xs<>0 
			b\xs=b\xs/1.002
		EndIf
			
		If b\ys<>0 
			b\ys=b\ys/1.002
		EndIf
	Next
	;
	; Render the balls.
	; 
	For b.ball=Each ball
		DrawImage stonea,b\x,b\y
	Next
	DrawImage stoneb,mx,my

	Flip
	mxa=MouseX()
	mya=MouseY()
Wend

End											; Terminate program here.

Function UpdateMouse()
End Function

;
; Functions.
;
Function UpdateBalls()						; This function updates the positions of the balls.
End Function

Function RenderBalls()						; This function renders the stars to the screen.
End Function


