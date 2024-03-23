; Sierpiñski triangles.
; ---------------------

; Adapted from Conrad Bessant's "Computers and Chaos" book (AmigaBASIC listing
; by Conrad Bessant), then ported from Blitz Basic Amiga --> Blitz Basic PC!

; Press Escape to quit.

scw=800 ; screen width
sch=600	; screen height
Graphics scw,sch

border=100 			 ; Keep it all within a border
Origin border,border ; Set offset for gfx commands
scw=scw-(border*2)	 ; Width and height are now off-screen by "border" pixels, so we bring
sch=sch-(border*2)	 ; them back in by "border" pixels, plus another "border" pixels!

; Starting point:
px=scw:py=sch

midscreen=scw/2	; Saves it having to be constantly re-calculated during the loop!
switch=-1		

a$="Sierpiñski Triangles"
Text (scw-StringWidth(a$))/2,-(StringHeight(a$)*2),a$

While KeyDown(1)=0	; Keep repeating until Escape is pressed

	vertex=Rnd(3)	; Chooses a point to start calculations 

	Select vertex
		Case 0
			tx=midscreen
			ty=0
		Case 1
			tx=0
			ty=sch
		Case 2
			tx=scw
			ty=sch
	End Select

    px=px+(tx-px)/2:py=py+(ty-py)/2

	Color Rnd(255),Rnd(255),Rnd(255)
	Plot px,py

Wend

End