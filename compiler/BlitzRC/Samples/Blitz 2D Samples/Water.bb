
; WATER - Written By Rob Hutchinson 17/08/2000.

Const width=1024,height=768,depth=16				;
Graphics width,height;,depth						; Setup the display.
SetBuffer BackBuffer()								;

Type Pixel											; Type to hold the pixels.
	Field x#,y#										; x,y positions
    Field co#,si#									; offsets.
	Field i#,z#										; movements.
End Type

MaximumPix=width									; Maximum number of pixels. (dont change this).
f=0
updown#=10											; Fiddle with these two if you like :)
updspd#=0.70											;
direction=1
For i=0 To MaximumPix								;
	p.Pixel=New Pixel								; This sets up the pixels' postions,
	p\x=(width/2)									; speeds, movements, etc.
	p\y=(height/2)									;
	p\co=(f*3)
	p\si=3
	p\i=width/1
	p\z=(f*.10)
	f=f+1
Next

Color 0,0,255										; Set color to rich blue

Repeat
	Cls												; Clear sceen for buffering.
	j=0												; 
	For p.Pixel=Each Pixel							;
		yy=((height)/2)+(Sin(p\z)*updown)			; get th y position of pixel
		Rect j,yy,1,width-yy								; draw it, using rect instead of plot.
		p\z=p\z+p\si								;
		If p\z=360 Then p\z=0						; update positions.
		j=j+1										;
	Next
	If direction=1									;
		updown=updown+updspd						; This just changes direction.
		If updown>=(height/2)						;
			direction=0
		EndIf
	Else
		updown=updown-updspd
		If updown<=-(height/2)
			direction=1
		EndIf
	EndIf
						
	Flip
Until KeyDown(1)

End