;wavy patterns
;Written by Ed Upton

Graphics 640,480
SetBuffer BackBuffer()

Dim Lines(480)
Dim ang(480)

;setup images
For n=0 To 480
 Lines(n)=CreateImage(640,1)
Next 

SeedRnd MilliSecs()

Global radius=0
cx=0
cy=0
seed=0
While Not KeyDown(1)
 ok=0
 Color (cx*cy),cx-(cy),cx*(cy*seed)
 Plot cx,cy
 cx=cx+1
 If cx>639
  cx=0
  cy=cy+1
 EndIf
 If cy>479
  cx=0
  cy=0
  ok=1
 EndIf
 If ok=1
  cx=0
  cy=0
  x1=1
  y1=1
  R=0
  G=0
  B=0
  Color 0,0,0
  Text 320,440,"PRESS LEFT MOUSE BUTTON FOR NEW PATTERN",1
  Text 321,440,"PRESS LEFT MOUSE BUTTON FOR NEW PATTERN",1
  Text 320,460,"MOVE MOUSE FOR FUN EFFECT",1
  Text 321,460,"MOVE MOUSE FOR FUN EFFECT",1
  For n=0 To 480
   GrabImage Lines(n),0,n
  Next
  radius=0
  sa=0
  For n=0 To 480
   ang(n)=sa
   sa=sa+182
  Next
  Flip
  ox=MouseX()
  While Not MouseDown(1)
   Cls
   If KeyDown(1)=1 Then End
   For n=0 To 480
    xp=0+Sin((2*ang(n))*Pi/360)*radius
    DrawImage Lines(n),xp,n
    ang(n)=ang(n)+182
   Next   
   If radius>0 Then radius=radius-1 
   mx=MouseX()
   If mx<>ox
    If mx<ox Then radius=radius+(ox-mx) Else radius=radius+(mx-ox)
    ox=mx
    If radius>500 Then radius=500
   EndIf
   Flip
  Wend
  seed=Rnd(65535)
 EndIf
Wend
End