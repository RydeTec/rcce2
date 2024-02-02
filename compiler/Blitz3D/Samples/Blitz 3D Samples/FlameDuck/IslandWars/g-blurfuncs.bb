;Approximated 3x3 And 5x5 Guasian Blur Functions
;by Mikkel Løkke aka. FlameDuck (m.loekke@m2p.dk)

Const glevel1=1
Const glevel2=2
Const glevel3=4
Const glevel4=8
Const glevel5=12


Function gblur3x3(image,x,y)

maxx=ImageWidth(image)
maxy=ImageHeight(image)

; Weight Matrix:
;
;  1 | 2 | 1
; ---+---+---
;  2 | 4 | 2
; ---+---+---
;  1 | 2 | 1


If (x<maxx-1 And x>0) And (y<maxy-1 And y>0)

	ibuf=ImageBuffer(image)
	r=0:g=0:b=0
	LockBuffer ibuf

	rgb=ReadPixelFast ( x-1 , y-1 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel1:g=g+(rgb Shr 8 And $ff)*glevel1:b=b+(rgb And $ff)*glevel1

	rgb=ReadPixelFast (  x  , y-1 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel2:g=g+(rgb Shr 8 And $ff)*glevel2:b=b+(rgb And $ff)*glevel2

	rgb=ReadPixelFast ( x+1 , y-1 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel1:g=g+(rgb Shr 8 And $ff)*glevel1:b=b+(rgb And $ff)*glevel1

	rgb=ReadPixelFast ( x-1 ,  y  , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel2:g=g+(rgb Shr 8 And $ff)*glevel2:b=b+(rgb And $ff)*glevel2

	rgb=ReadPixelFast (  x  ,  y  , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel3:g=g+(rgb Shr 8 And $ff)*glevel3:b=b+(rgb And $ff)*glevel3

	rgb=ReadPixelFast ( x+1 ,  y  , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel2:g=g+(rgb Shr 8 And $ff)*glevel2:b=b+(rgb And $ff)*glevel2

	rgb=ReadPixelFast ( x-1 , y+1 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel1:g=g+(rgb Shr 8 And $ff)*glevel1:b=b+(rgb And $ff)*glevel1

	rgb=ReadPixelFast ( x-1 , y+1 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel2:g=g+(rgb Shr 8 And $ff)*glevel2:b=b+(rgb And $ff)*glevel2

	rgb=ReadPixelFast ( x-1 , y+1 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel1:g=g+(rgb Shr 8 And $ff)*glevel1:b=b+(rgb And $ff)*glevel1

	UnlockBuffer ibuf

	levels=4*glevel1+4*glevel2+glevel3

	r=r/levels:g=g/levels:b=b/levels
	rgb=0
	rgb=(r Shl 16) Or (g Shl 8) Or b

	Return rgb

Else

	Return 0

EndIf

End Function

Function gblur5x5(image,x,y)

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
	r=0:g=0:b=0
	LockBuffer ibuf

	rgb=ReadPixelFast ( x-2 , y-2 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel1:g=g+(rgb Shr 8 And $ff)*glevel1:b=b+(rgb And $ff)*glevel1

	rgb=ReadPixelFast ( x-1 , y-2 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel2:g=g+(rgb Shr 8 And $ff)*glevel2:b=b+(rgb And $ff)*glevel2

	rgb=ReadPixelFast (  x  , y-2 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel3:g=g+(rgb Shr 8 And $ff)*glevel3:b=b+(rgb And $ff)*glevel3

	rgb=ReadPixelFast ( x+1 , y-2 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel2:g=g+(rgb Shr 8 And $ff)*glevel2:b=b+(rgb And $ff)*glevel2

	rgb=ReadPixelFast ( x+2 , y-2 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel1:g=g+(rgb Shr 8 And $ff)*glevel1:b=b+(rgb And $ff)*glevel1


	rgb=ReadPixelFast ( x-2 , y-1 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel2:g=g+(rgb Shr 8 And $ff)*glevel2:b=b+(rgb And $ff)*glevel2

	rgb=ReadPixelFast ( x-1 , y-1 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel3:g=g+(rgb Shr 8 And $ff)*glevel3:b=b+(rgb And $ff)*glevel3

	rgb=ReadPixelFast (  x  , y-1 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel4:g=g+(rgb Shr 8 And $ff)*glevel4:b=b+(rgb And $ff)*glevel4

	rgb=ReadPixelFast ( x+1 , y-1 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel3:g=g+(rgb Shr 8 And $ff)*glevel3:b=b+(rgb And $ff)*glevel3

	rgb=ReadPixelFast ( x+2 , y-1 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel2:g=g+(rgb Shr 8 And $ff)*glevel2:b=b+(rgb And $ff)*glevel2


	rgb=ReadPixelFast ( x-2 ,  y  , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel3:g=g+(rgb Shr 8 And $ff)*glevel3:b=b+(rgb And $ff)*glevel3

	rgb=ReadPixelFast ( x-1 ,  y  , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel4:g=g+(rgb Shr 8 And $ff)*glevel4:b=b+(rgb And $ff)*glevel4

	rgb=ReadPixelFast (  x  ,  y  , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel5:g=g+(rgb Shr 8 And $ff)*glevel5:b=b+(rgb And $ff)*glevel5

	rgb=ReadPixelFast ( x+1 ,  y  , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel4:g=g+(rgb Shr 8 And $ff)*glevel4:b=b+(rgb And $ff)*glevel4

	rgb=ReadPixelFast ( x+2 ,  y  , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel3:g=g+(rgb Shr 8 And $ff)*glevel3:b=b+(rgb And $ff)*glevel3


	rgb=ReadPixelFast ( x-2 , y+1 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel2:g=g+(rgb Shr 8 And $ff)*glevel2:b=b+(rgb And $ff)*glevel2

	rgb=ReadPixelFast ( x-1 , y+1 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel3:g=g+(rgb Shr 8 And $ff)*glevel3:b=b+(rgb And $ff)*glevel3

	rgb=ReadPixelFast (  x  , y+1 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel4:g=g+(rgb Shr 8 And $ff)*glevel4:b=b+(rgb And $ff)*glevel4

	rgb=ReadPixelFast ( x+1 , y+1 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel3:g=g+(rgb Shr 8 And $ff)*glevel3:b=b+(rgb And $ff)*glevel3

	rgb=ReadPixelFast ( x+2 , y+1 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel2:g=g+(rgb Shr 8 And $ff)*glevel2:b=b+(rgb And $ff)*glevel2


	rgb=ReadPixelFast ( x-2 , y+2 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel1:g=g+(rgb Shr 8 And $ff)*glevel1:b=b+(rgb And $ff)*glevel1

	rgb=ReadPixelFast ( x-1 , y+2 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel2:g=g+(rgb Shr 8 And $ff)*glevel2:b=b+(rgb And $ff)*glevel2

	rgb=ReadPixelFast (  x  , y+2 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel3:g=g+(rgb Shr 8 And $ff)*glevel3:b=b+(rgb And $ff)*glevel3

	rgb=ReadPixelFast ( x+1 , y+2 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel2:g=g+(rgb Shr 8 And $ff)*glevel2:b=b+(rgb And $ff)*glevel2

	rgb=ReadPixelFast ( x+2 , y+2 , ibuf )
	r=r+(rgb Shr 16 And $ff)*glevel1:g=g+(rgb Shr 8 And $ff)*glevel1:b=b+(rgb And $ff)*glevel1


	UnlockBuffer ibuf

	levels=4*glevel1+8*glevel2+8*glevel3+4*glevel4+glevel5

	r=r/levels:g=g/levels:b=b/levels
	rgb=0
	rgb=(r Shl 16) Or (g Shl 8) Or b

	Return rgb

Else

	Return 0

EndIf

End Function