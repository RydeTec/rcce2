; Name:			transition.bb
; Extra Files:	BlitzLogo.bmp
; Version:		1.1 (Works with final release version of Blitz)
; Description:	My 2 cents worth of a wipe/transition effect.
; Author:		Mikkel Løkke aka. FlameDuck
; License:		Public Domain
; Purpose:		To write a flexible wipe/transition that could be applied to anything.


; .........................................................
; : This part initializes all our variables and constants :
; :.......................................................:
SeedRnd MilliSecs()
Const width=1024,height=768		; Define the width/height of the effect

Graphics width,height			; And open a screen

img=LoadImage("BlitzLogo.bmp")	; Load the "target" image

Const xsize=16					; Set the horizontal size of the blocks
Const ysize=16					; Set the vertical size of the blocks
Const xdiv=width/xsize			; Calculate the number of divisions we have along the x axis
Const ydiv=height/ysize			; Calculate the number of divisions we have along the y axis
Const total=xdiv*ydiv			; Calculate the total number of tiles
Const frames=25					; This tells us across how many frames we want to spread the animation
Const choice=total/frames		; The number of tiles to draw each frame
Const fps=25					; This is how many frames we want displayed each second
Dim matrix(xdiv,ydiv)			; Define the array used to hold information on which tiles to do each frame

; .............................................
; : This part generates a random matrix using :
; : a brute force even distribution algorithm :
; :...........................................:

For ii = 1 To frames				; For each frame
	For I = 1 To choice			; Loop a certain number of choices
		Repeat
			x=Rnd(0,xdiv)		; Get a random x position
			y=Rnd(0,ydiv)		; Get a random y position
		Until matrix(x,y)=0		; And if something is allready there, get them again
		matrix(x,y)=ii			; Otherwise store the frame number
	Next
Next

; ..............................................
; : This part creates the transition animation :
; :............................................:

dly=CreateTimer(fps)			; Create a new CPU timer
For frm=0 To frames				; Loop through each frame
	WaitTimer(dly)				; Wait for a timer hit
	For x=0 To xdiv				; Loop through all the x positions
		For y=0 To ydiv			; And all the y positions
			If matrix(x,y)=frm	; If this position is to be shown at this frame
				DrawImageRect img,x*xsize,y*ysize,x*xsize,y*ysize,xsize,ysize	; Draw it
			End If
		Next
	Next
Next

WaitMouse						; Wait for the mouse to be pressed

Color 0,0,0
For frm=0 To frames				; Loop through each frame
	WaitTimer(dly)				; Wait for a timer hit
	For x=0 To xdiv				; Loop through all the x positions
		For y=0 To ydiv			; And all the y positions
			If matrix(x,y)=frm	; If this position is to be shown at this frame
				Rect x*xsize,y*ysize,xsize,ysize	; Clear it
			End If
		Next
	Next
Next

End								; Terminate the program