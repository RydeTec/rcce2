;strange demo
;written by Ed Upton

;adjust this for more lines
Global lns=450

Graphics 640,480
SetBuffer FrontBuffer()

Global r
Global g
Global b
Global tim

;X,Y screen coordinates
;F facing direction 
;T timer till it needs to change direction
Type bit
 Field x,y,f,t
End Type

tim=Rnd(500)
r=Rnd(255)
g=Rnd(255)
b=Rnd(255)

setup()
While Not KeyDown(1)
 update()
 tim=tim-1
 If tim<0
  tim=500
  r=Rnd(255)
  g=Rnd(255)
  b=Rnd(255)
 EndIf
Wend
End

;setup lines
Function setup()
 For n=0 To lns
  bitz.bit=New bit
  bitz\x=Rnd(640)
  bitz\y=Rnd(480)
  bitz\f=Rnd(3)
  bitz\t=Rnd(20)
 Next
End Function

;update function
Function update()
 For bitz.bit=Each bit
  Color r,g,b
  Plot bitz\x,bitz\y
  bitz\t=bitz\t-1
  If bitz\t<=0
   bitz\f=Rnd(3)
   bitz\t=Rnd(20)
  EndIf
  If bitz\f=0 Then bitz\y=bitz\y-1
  If bitz\f=1 Then bitz\x=bitz\x+1
  If bitz\f=2 Then bitz\y=bitz\y+1
  If bitz\f=3 Then bitz\x=bitz\x-1
  If bitz\x<0 Then bitz\x=639
  If bitz\x>639 Then bitz\x=0
  If bitz\y<0 Then bitz\y=479
  If bitz\y>479 Then bitz\y=0
  Color 255,255,255
  Plot bitz\x,bitz\y
 Next
End Function