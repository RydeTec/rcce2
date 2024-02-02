;
; Fades - Written by Rob Hutchinson.
;
; Some Fades/Wipes.
;

Graphics 1024,768			
								; or maybe 24, which should look the same as 32 since its a 24bit bitmap.
SetBuffer BackBuffer()
Global splhoriz=LoadAnimImage("BlitzLogo.bmp",1024,1,0,768)

Dim xpos#(1024)
Dim xmov#(1024)
Dim ypos#(768)
Dim ymov#(768)

FADE_ProjectXDown(1)			; A Project X Fade Down the screen.
FADE_ProjectXUp(1,4)			; A Project X Fade Up the screen.
FADE_BounceHoriz(0,2,4)			; Fly-By full image.
FADE_BounceHoriz(50,2,5)		; Fly-By Distort
FADE_BounceHoriz(360,2,2)		; Fly-By eratic.

End

;########## FADE ROUTINES ###########

Function FADE_ProjectXDown(speedy)
	movey=0
	Repeat
		Cls
		If movey<768
			For a=0 To 767
				If a<movey
					DrawImage splhoriz,0,a,a
				Else
					DrawImage splhoriz,0,a,movey
				EndIf
			Next
			movey=movey+speedy
		Else
			For a=0 To 767
				DrawImage splhoriz,0,a,a
			Next
		EndIf
		If KeyHit(1) Then End
		Flip
	Until MouseHit(1)
End Function

Function FADE_ProjectXUp(speedy,speedys)
	movey=768
	moveys=768
	Repeat
		Cls
		If movey>0
			For a=0 To 767
				If a<movey
					DrawImage splhoriz,0,a,a
				Else
					DrawImage splhoriz,0,a,movey
				EndIf
			Next
			movey=movey-speedy
		Else
			If moveys>0
				For a=0 To 767
					If a<moveys
						DrawImage splhoriz,0,a,0
					EndIf
				Next
				moveys=moveys-speedys
			EndIf
		EndIf
		If KeyHit(1) Then End
		Flip
	Until MouseHit(1)
End Function

Function FADE_BounceHoriz(blur,speed#,fadeduration#)
	rot#=360
	mnt#=1024
	For a=1 To 767
		ypos(a)=Rnd(0.0,blur)
	Next
	Repeat
		Cls
		If stopit=False
			For a=0 To 767
				inv#=(Cos(rot+ypos(a))*mnt)
				DrawImage splhoriz,inv,a,a
			Next
			mnt=mnt-fadeduration#
		Else
			For a=0 To 767
				DrawImage splhoriz,0,a,a
			Next
		EndIf
		If mnt<0
			stopit=True
		Else
			rot=rot-speed
		EndIf
		If KeyHit(1) Then End
		Flip
	Until MouseHit(1)
End Function






