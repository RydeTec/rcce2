;Swirl demo (featuring Sin & cos)
;written by Ed Upton

Graphics 640,480
SetBuffer BackBuffer()

Dim angle(320)
Dim r(320)
Dim g(320)
Dim b(320)

Setup()

t=0

While Not KeyDown(1)
; Cls
 update()
 Flip
 t=t+1
 If t>30 
  t=0
  Setup()
 EndIf
Wend
End

Function Setup()
 For ca=1 To 319
  angle(ca)=0
  r(ca)=Rnd(255)
  g(ca)=Rnd(255)
  b(ca)=Rnd(255)
 Next
End Function

Function update()
 For ca=1 To 319
  For nn=angle(ca) To angle(ca)+360 Step 60
   Color r(ca),g(ca),b(ca)
   xx=320+Cos((2*((angle(ca)+nn)*182))*Pi/360)*(ca+ca) ;add +nn next to the ca in (ca) at the end of
   yy=240+Sin((2*((angle(ca)+nn)*182))*Pi/360)*(ca+ca) ;these lines, for another effect
   Plot xx,yy
  Next
  angle(ca)=angle(ca)+ca
 Next
End Function