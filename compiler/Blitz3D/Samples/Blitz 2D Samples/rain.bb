;Blitz rain effect
;written by Ed Upton

;play with this for light or heavy rain!!!
Global drop_number=200

Graphics 640,480
SetBuffer BackBuffer()

Type drop
 Field x,y
 Field angle,col
End Type

Global wind

placedrops()

While Not KeyDown(1)
 Cls
 update()
 If KeyDown(75)=1 Then wind=wind-25
 If KeyDown(77)=1 Then wind=wind+25
 If wind>3000 Then wind=3000
 If wind<-3000 Then wind=-3000
 Color 255,255,255
 Text 0,0,"Wind speed:"+wind
 Text 320,460,"Numpad 4 and 6 adjust wind speed",1
 Flip
Wend
End

;place drops on the screen
Function placedrops()
 For n=0 To drop_number
  drip.drop = New drop
  al=Rnd(2048)
  drip\x=al-704
  drip\y=Rnd(380)
  drip\angle=0     ;straight down (This is modified by wind)
  drip\col=Rnd(4)
 Next
End Function

Function update()
 ;adjust the 12 at the end of the Sin and Cos lines for faster rain
 For drip.drop = Each drop
  xx=Sin((2*(drip\angle+wind))*Pi/360)*12
  yy=Cos((2*(drip\angle+wind))*Pi/360)*12
  If drip\col=0 Then Color 200,200,200 Else
  If drip\col=1 Then Color 150,150,150 Else
  If drip\col=2 Then Color 100,100,100 Else
  If drip\col=3 Then Color 50,50,50
  Line drip\x,drip\y,drip\x+(xx/2),drip\y+(yy/2)
  drip\x=drip\x+xx
  drip\y=drip\y+yy
  If drip\y>430
   percent=Rnd(100)
   If percent<50 Or drip\y>475
    al=Rnd(2048)
    drip\x=al-704
    drip\y=Rnd(100)
    drip\angle=0     ;straight down (This is modified by wind)
   EndIf
  EndIf
 Next
End Function