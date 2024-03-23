Graphics 640,480
SetBuffer BackBuffer ()

Repeat
 o1x=21
 o1y=240
 p1x=21
 p1y=240
 p1dir=1 ;right
 o2x=618
 o2y=240
 p2x=618
 p2y=240
 p2dir=3 ;left
 Color 255,255,255
 Text 320,10,"Lightcycles",1,1
 Text 320,30,"Written by Ed Upton",1,1
 Text 320,150,"Player 1 controls: W-up X-down A-left D-right",1,1
 Text 320,180,"Player 2 controls: Numpad 8-up 2-down 4-left 6-right",1,1
 Text 320,400,"Press Enter to start",1,1
 Flip
 Repeat
 Until KeyDown(28)=1 Or KeyDown(1)=1
 If KeyDown(1)=1 Then End
 Cls
 Flip
 Cls
 Repeat
  Color 255,255,255
  Line 0,0,639,0
  Line 0,0,0,479
  Line 0,479,639,479
  Line 639,0,639,479
  Color 255,0,0
  Plot p1x,p1y
  Plot o1x,o1y
  Color 0,0,255
  Plot p2x,p2y
  Plot o2x,o2y
  o1x=p1x
  o1y=p1y
  o2x=p2x
  o2y=p2y
  If KeyDown(72) Then p2dir=0
  If KeyDown(80) Then p2dir=2
  If KeyDown(75) Then p2dir=3
  If KeyDown(77) Then p2dir=1
  If KeyDown(17) Then p1dir=0
  If KeyDown(45) Then p1dir=2
  If KeyDown(30) Then p1dir=3
  If KeyDown(32) Then p1dir=1
  If p1dir=0 Then p1y=p1y-1
  If p1dir=1 Then p1x=p1x+1
  If p1dir=2 Then p1y=p1y+1
  If p1dir=3 Then p1x=p1x-1  

  If p2dir=0 Then p2y=p2y-1
  If p2dir=1 Then p2x=p2x+1
  If p2dir=2 Then p2y=p2y+1
  If p2dir=3 Then p2x=p2x-1

  p1death=0
  GetColor p1x,p1y
  If ColorRed()>0 Or ColorGreen()>0 Or ColorBlue()>0 Then p1death=1
  If p1x<1 Then p1death=1
  If p1x>638 Then p1death=1
  If p1y<1 Then p1death=1
  If p1y>478 Then p1death=1
  p2death=0
  GetColor p2x,p2y
  If ColorRed()>0 Or ColorGreen()>0 Or ColorBlue()>0 Then p2death=1
  If p2x<1 Then p2death=1
  If p2x>638 Then p2death=1
  If p2y<1 Then p2death=1
  If p2y>478 Then p2death=1
  Color 255,255,255
  If p1death=1
   Text 320,240,"Player 1 crashed",1,1
   Flip
   Delay 5000
  EndIf
  If p2death=1
   Text 320,240,"Player 2 crashed",1,1
   Flip
   Delay 5000
  EndIf
  Flip
 Until KeyDown(1)=1 Or p1death=1 Or p2death=1
 If p1death=0 And p2death=0 Then End
Until p1death=0 And p2death=0
End