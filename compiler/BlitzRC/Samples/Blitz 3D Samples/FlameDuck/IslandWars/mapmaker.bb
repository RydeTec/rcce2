Graphics 320,240,16,1
SetBuffer FrontBuffer()
SeedRnd MilliSecs()
Const side=64
Global land=.1*side^2

Include "g-blurfuncs.bb"

map=CreateImage (side,side)
map2=CreateImage (side,side)
blurmap=CreateImage (side,side)
SetBuffer ImageBuffer(map)

For t=0 To 6
	nodex=Rand(1,side-2)
	nodey=Rand(1,side-2)
	For i = 0 To land
		terminate=False
		If ReadPixel(x-1,y,ImageBuffer(map))<>0
			If ReadPixel(x+1,y,ImageBuffer(map))<>0
				If ReadPixel(x,y-1,ImageBuffer(map))<>0
					If ReadPixel(x,y+1,ImageBuffer(map))<>0
						terminate=True
					EndIf
				EndIf
			EndIf
		EndIf

		If nodex<1 Or nodex>side-2 Or nodex<0 Or nodex>side-2
			terminate=True
		EndIf			

		If terminate=False
			nadx=Rand(0,2)-1:nady=Rand(0,2)-1
			nodex=nodex+nadx:nodey=nodey+nady
			nadx=0:nady=0
			WritePixel nodex,nodey,$ffffff
		Else
			nodex=Rand(1,side-2)
			nodey=Rand(1,side-2)
		EndIf		
	Next
Next


For y=0 To side-1
	For x=0 To side-1
		landtile=land5x5(map,x,y)
		If landtile=False
			WritePixel x,y,0,ImageBuffer( map2 )
		Else
			WritePixel x,y,$FFFFFF,ImageBuffer( map2 )
		EndIf		
	Next
Next


For y=0 To side-1
	For x=0 To side-1
		rgb=gblur5x5(map2,x,y)
		WritePixel x,y,rgb,ImageBuffer( blurmap )
	Next
Next

SetBuffer FrontBuffer()

DrawImage map,0,0
DrawImage map2,side,0
DrawImage blurmap,side*2,0

Repeat
	Delay 100
Until KeyHit(1)
SaveImage blurmap,"landblur.bmp"
End



Function land5x5(image,x,y)

maxx=ImageWidth(image)
maxy=ImageHeight(image)

; Weight Matrix:
;
;  1 | 2 | 4 | 2 | 1
; ---+---+---+---+---
;  2 | 4 | 8 | 4 | 2
; ---+---+---+---+---
;  4 | 8 |16 | 8 | 4
; ---+---+---+---+---
;  2 | 4 | 8 | 4 | 2
; ---+---+---+---+---
;  1 | 2 | 4 | 2 | 1


If (x<maxx-2 And x>1) And (y<maxy-2 And y>1)

	ibuf=ImageBuffer(image)
	LockBuffer ibuf

	pixcount=0

	rgb=ReadPixelFast ( x-2 , y-2 , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel1

	rgb=ReadPixelFast ( x-1 , y-2 , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel2

	rgb=ReadPixelFast (  x  , y-2 , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel3

	rgb=ReadPixelFast ( x+1 , y-2 , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel2

	rgb=ReadPixelFast ( x+2 , y-2 , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel1


	rgb=ReadPixelFast ( x-2 , y-1 , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel2

	rgb=ReadPixelFast ( x-1 , y-1 , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel3

	rgb=ReadPixelFast (  x  , y-1 , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel4

	rgb=ReadPixelFast ( x+1 , y-1 , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel3

	rgb=ReadPixelFast ( x+2 , y-1 , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel2


	rgb=ReadPixelFast ( x-2 ,  y  , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel3

	rgb=ReadPixelFast ( x-1 ,  y  , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel4

	rgb=ReadPixelFast (  x  ,  y  , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel5

	rgb=ReadPixelFast ( x+1 ,  y  , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel4

	rgb=ReadPixelFast ( x+2 ,  y  , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel3


	rgb=ReadPixelFast ( x-2 , y+1 , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel2

	rgb=ReadPixelFast ( x-1 , y+1 , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel3

	rgb=ReadPixelFast (  x  , y+1 , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel4

	rgb=ReadPixelFast ( x+1 , y+1 , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel3

	rgb=ReadPixelFast ( x+2 , y+1 , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel2


	rgb=ReadPixelFast ( x-2 , y+2 , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel1

	rgb=ReadPixelFast ( x-1 , y+2 , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel2

	rgb=ReadPixelFast (  x  , y+2 , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel3

	rgb=ReadPixelFast ( x+1 , y+2 , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel2

	rgb=ReadPixelFast ( x+2 , y+2 , ibuf )
	If rgb > 0 Then pixcount=pixcount+1*glevel1


	UnlockBuffer ibuf

	levels=4*glevel1+8*glevel2+8*glevel3+4*glevel4+glevel5

	If pixcount=>levels/2
		Return True
	Else
		Return False
	EndIf

Else

	Return False

EndIf

End Function