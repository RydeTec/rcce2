;isometric landscape demo
;written by Ed Upton

Graphics 640,480
SetBuffer FrontBuffer()

;adjust this value to adjust land height
Global LandHeight=15

Dim land(256,256)

SetupLand()

While Not KeyDown(1)
 Cls
 SetupLand()
 UpdateLand()
 Repeat
  If KeyDown(1)=1 Then End
 Until KeyDown(28)=1
Wend
End

Function SetupLand()
 SeedRnd MilliSecs()
 For yy=0 To 256
  For xx=0 To 256
   land(xx,yy)=0
  Next
 Next
 x=128 
 y=128
 timer=65535
 Repeat
  val=0+Rnd(4)
  val=val-Rnd(3)
  x=x+val
  val=0+Rnd(4)
  val=val-Rnd(3)
  y=y+val
  If y<0 Then y=256
  If x<0 Then x=256
  If x>256 Then x=0
  If y>256 Then y=0
  land(x,y)=land(x,y)+1
  timer=timer-1
 Until timer<0 Or KeyDown(1)=1
End Function

Function UpdateLand()
 ;cursor position
 cx=320
 cy=-10
 Color 255,255,255
 For yy=0 To 255
  ;actual x and y
  ax=cx
  ay=cy
  For xx=0 To 255 
;   If land(xx,yy)<1 Then Color 0,0,255
;   If land(xx,yy)=1 Then Color 255,255,0
;   If land(xx,yy)>0 Then Color 0,255,0
;   If land(xx,yy)>50 Then Color 200,200,200
;   If land(xx,yy)>100 Then Color 255,255,255
   a=100+(land(xx,yy)*5)
   If a>255 Then a=255
   Color 0,a,0
   If land(xx,yy)<1 Then Color 0,0,155
   If land(xx,yy)>10 Then Color a,a,a
   If xx<256 Then Line ax,ay-land(xx,yy),ax+2,(ay+1)-land(xx+1,yy)
   If yy<256 Then Line ax,ay-land(xx,yy),ax-2,(ay+1)-land(xx,yy+1)
   ax=ax+2
   ay=ay+1
  Next
  cx=cx-2
  cy=cy+1
 Next
End Function