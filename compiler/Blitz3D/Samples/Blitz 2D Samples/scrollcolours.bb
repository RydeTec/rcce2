;amusing pattern example
;written by Ed Upton

Graphics 640,480
SetBuffer BackBuffer()

While Not KeyDown(1)
 va=Rnd(16)
 vb=Rnd(16)
 vc=Rnd(16)
 For yy=0 To 480
  For xx=0 To 640
   a=Calc(va,xx,yy)
   b=Calc(vb,xx,yy)
   c=Calc(vc,xx,yy)

   Color a,b,c
   Plot xx,yy
   If KeyDown(1)=1 Then End
  Next
 Next
 Flip
Wend
End

Function Calc(val,xx,yy)
 If val=0 Then cg=xx-yy
 If val=1 Then cg=xx+yy
 If val=2 Then cg=xx*yy
 If val=3 Then cg=(xx+1)/(yy+1)
 If val=4 Then cg=yy-xx
 If val=5 Then cg=yy+xx
 If val=6 Then cg=yy*xx
 If val=7 Then cg=(yy+1)/(xx+1)
 If val=8 Then cg=xx-xx
 If val=9 Then cg=xx+xx
 If val=10 Then cg=xx*xx
 If val=11 Then cg=(xx+1)/(xx+1)
 If val=12 Then cg=yy-yy
 If val=13 Then cg=yy+yy
 If val=14 Then cg=yy*yy
 If val=14 Then cg=(yy+1)/(yy+1)
 Return cg
End Function