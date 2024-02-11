;Ants
;written by Ed Upton


;adjust this to your preference
Global antnum=50

;init graphics
Graphics 640,480
;SetBuffer BackBuffer()

Type blob
 Field x,y,f
 Field r,g,b
End Type

PositionAnt()

While Not KeyDown(1)
 MoveAnt()
; Flip
Wend
End

Function PositionAnt()
 For n=0 To antnum
  ant.blob = New blob
  ant\x=Rnd(640)
  ant\y=Rnd(480)
  ant\f=Rnd(3)
  ant\r=Rnd(200)+55
  ant\g=Rnd(200)+55
  ant\b=Rnd(200)+55
 Next
End Function

Function MoveAnt()
 a=0
 For ant.blob=Each blob
  If ant\f=0 Then ant\y=ant\y-1
  If ant\f=1 Then ant\x=ant\x+1
  If ant\f=2 Then ant\y=ant\y+1
  If ant\f=3 Then ant\x=ant\x-1
  If ant\x<0 Then ant\x=639
  If ant\x>639 Then ant\x=0
  If ant\y<0 Then ant\y=479
  If ant\y>479 Then ant\y=0
  GetColor ant\x,ant\y
  If ColorRed()=0 And ColorGreen()=0 And ColorBlue()=0
   Color ant\r,ant\g,ant\b
   Plot ant\x,ant\y
   ant\f=ant\f-1
  Else
   Color 0,0,0
   Plot ant\x,ant\y
   ant\f=ant\f+1
  EndIf
  If ant\f<0 Then ant\f=3
  If ant\f>3 Then ant\f=0
  If a=0 Then Color 255,0,0
  a=a+1
 Next
End Function