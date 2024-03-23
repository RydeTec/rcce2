;isometric landscape demo
;written by Ed Upton

Graphics 640,480
SetBuffer BackBuffer()

;adjust this value to adjust land height
Global LandHeight=15

Dim land(21,21)

SetupLand()

While Not KeyDown(1)
 Cls
 SetupLand()
 UpdateLand()
 Flip
Wend
End

Function SetupLand()
 For yy=0 To 21
  For xx=0 To 21
   land(xx,yy)=Rnd(LandHeight)
   land(xx,yy)=land(xx,yy)-Rnd(LandHeight)
  Next
 Next
End Function

Function UpdateLand()
 ;cursor position
 cx=320
 cy=150
 Color 255,255,255
 For yy=0 To 20
  ;actual x and y
  ax=cx
  ay=cy
  For xx=0 To 20 
   If xx<20 Then Line ax,ay-land(xx,yy),ax+16,(ay+8)-land(xx+1,yy)
   If yy<20 Then Line ax,ay-land(xx,yy),ax-16,(ay+8)-land(xx,yy+1)
   ax=ax+16
   ay=ay+8
  Next
  cx=cx-16
  cy=cy+8
 Next
End Function