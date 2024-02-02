;written by Ed Upton

;use left/right cursors to rotate and J to jump

Graphics 640,480
SetBuffer BackBuffer()

;adjust depending on how many starts you want
starnum=250

Type stars
 Field angle,radius,speed
End Type

For s=0 To starnum
 star.stars = New stars
 star\angle=Rnd(65535)
 star\radius=Rnd(400)+80
 star\speed=Rnd(5)+1
Next

spd=0
While Not KeyDown(1)
 Cls
 update(spd)
 If KeyDown(36)=1 Then spd=25 Else spd=3
 If KeyDown(203)=1
  For star.stars=Each stars
   star\angle=star\angle+50
  Next
 EndIf
 If KeyDown(205)=1
  For star.stars=Each stars
   star\angle=star\angle-50
  Next
 EndIf
 Flip
Wend
End

Function update(speed)
 For star.stars=Each stars
  xx=320+Sin((2*star\angle)*Pi/360)*star\radius
  yy=240+Cos((2*star\angle)*Pi/360)*star\radius
  x2=320+Sin((2*star\angle)*Pi/360)*((star\radius+star\speed)+(speed-3))
  y2=240+Cos((2*star\angle)*Pi/360)*((star\radius+star\speed)+(speed-3))
  star\radius=star\radius+(star\speed+speed)
  If star\radius>480
   star\angle=Rnd(65535)
   star\radius=Rnd(40)+40
   star\speed=Rnd(5)+1
  EndIf
  If star\speed<5 Then Color 175,175,175
  If star\speed<3 Then Color 100,100,100
  If star\speed=5 Then Color 255,255,255
  Line xx,yy,x2,y2
 Next
End Function