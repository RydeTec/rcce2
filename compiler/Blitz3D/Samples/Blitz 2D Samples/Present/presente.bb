; Name:			presente.bb
; Extra Files:	presente.bmp
; Description:	Another small pixel effect.
; Author:		Mikkel Løkke aka. FlameDuck
; License:		Public Domain
; Purpose:		To demonstrate the use of types as an optimised array replacement. (Sort of)


Global effectxofs=0,effectyofs=0

Const effectradius=64
Const waitlimit=25
Graphics 320,200

Type effectpixel
	Field ox,oy,ang,anga,r,ra,wait
End Type

ConvertMask ("presente.bmp")
FadeB2W()
Color 0,0,0

animtimer=CreateTimer(25)

Repeat
	Flip:Cls:WaitTimer(animtimer)
	pix=DoPixels()
Until KeyHit(1) Or MouseHit(1)
FadeW2B()
End

Function ConvertMask(a$)
	img=LoadImage(a$)
	If img=0 Then RuntimeError "File not found: "+CurrentDir$()+"\"+a$
	MaskImage img,255,255,255
	SetBuffer ImageBuffer(img)
	For x=0 To ImageWidth(img)-1
		For y=0 To ImageHeight(img)-1
			GetColor x,y
			If ColorRed()=0 And ColorGreen()=0 And ColorBlue()=0
				z.effectpixel=New effectpixel
				z\ox=x
				z\oy=y
				z\ang=Rnd(360)
				z\anga=Rnd(2)
				If z\anga=0 Then z\anga=-1
				z\anga=z\anga*4
				z\r=Rnd (2)
				If z\r=0
					z\r=-effectradius+Rnd(2)
					z\ra=-1
				Else
					 z\r=effectradius-Rnd(2)
					z\ra=1
				EndIf
			EndIf
		Next
	Next
	SetBuffer BackBuffer()
	effectxofs=(GraphicsWidth()/2)-(ImageWidth(img)/2)
	effectyofs=(GraphicsHeight()/2)-(ImageHeight(img)/2)
	FreeImage img
End Function

Function FadeB2W()
For i=0 To 255 Step 8
	Flip
	ClsColor i,i,i
	Cls
Next
End Function

Function FadeW2B()
For i=255 To 0 Step -8
	Flip
	ClsColor i,i,i
	Cls
Next
End Function

Function DoPixels()
	count=0
	For z.effectpixel=Each effectpixel
		Color Rnd(200),Rnd(200),Rnd(200)
		xo=Sin(z\ang)*z\r:yo=Cos(z\ang)*z\r
		If (z\r=-effectradius Or z\r=effectradius)
				z\ra=-z\ra
		EndIf

		If z\r=0 And z\wait<waitlimit
			z\wait=z\wait+1
		Else
			z\r=z\r+z\ra
			z\wait=0
			z\ang=z\ang+z\anga
		EndIf

		Plot effectxofs+z\ox+xo,effectyofs+z\oy+yo
		count=count+1
	Next
	Return count
End Function