Graphics 640,480
SetBuffer BackBuffer()
;
; This example shows you how to easily make a Street Fighter II style floor
; I don't have time to tweak out the numbers, but you can use the left and right
; arrow keys to change perspective, and use the up and down arrows to scroll
; the screen.  Ideally, the scroll and perspective would match up that lines always 
; converge at the center of the screen.
;

ground=LoadAnimImage("stupidfloor.bmp",1000,1,0,200)

Repeat

	If KeyHit(67) Then SaveBuffer(FrontBuffer(),"screen.bmp")

	Cls
	x1#=x1#+(KeyDown(200)-KeyDown(208))
	x2#=x2#+(KeyDown(203)-KeyDown(205))*.01

	Color 255,255,255
	Text 0,0,x1#
	Text 0,20,x2#

	For x=0 To 59
		Color 128,128,128
		Line x*80,0,x*80,480
	Next ;x
	Color 128,128,255
	Line 320,0,320,480

	For i=0 To 199
		DrawBlock ground,x1#+(x2*i)-180,270+i,i
	Next ;i

	Flip
Until KeyDown(1)

End