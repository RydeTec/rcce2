;star system demo
;written by Ed Upton
;star system structure
Type sysa
 Field radius
 Field angle
 Field r
 Field g
 Field b
 Field size
 Field speed
End Type

Type moons
 Field radius
 Field angle
 Field size
 Field speed
 Field orbit
End Type

n=0

;set up 640 x 480 graphics screen, double buffered
Graphics 640,480
SetBuffer BackBuffer()

ClsColor 0,0,0
;call setup routine to make star system
Setup()

;main routine
While Not KeyDown(1)
 Cls
 update()
 Flip
Wend
End

;function declerations follow this

;sets up all those loverly planets
Function Setup()

 startrad=85

 For n=1 To 10
  planet.sysa =New sysa
  planet\radius =startrad
  planet\angle =Rnd(65535)
  planet\r =Rnd(255)
  planet\g =Rnd(255)
  planet\b =Rnd(255)
  planet\size =Rnd(10)+5
  planet\speed =Rnd(2)+1
  startrad=startrad+(Rnd(20)+25)
 Next

 For n=1 To 10
  moon.moons =New moons
  moon\radius =Rnd(10)+15
  moon\angle =Rnd(65535)
  moon\size =Rnd(5)+3
  moon\speed =Rnd(2)+1
  moon\orbit=Rnd(10)+1
 Next 
End Function

;updates all those loverly planets and moons
Function update()
 Color 255,255,255
 Oval 320,240,20,20
 cp=1
 For planet.sysa =Each sysa
  pax=320+Cos((2*planet\angle)*Pi/360)*planet\radius
  pay=240+Sin((2*planet\angle)*Pi/360)*planet\radius
  Color planet\r,planet\g,planet\b
  hr=planet\size/3
  Oval pax-hr,pay-hr,planet\size,planet\size
  For moon.moons =Each moons
   If moon\orbit=cp
    max=pax+Cos((2*moon\angle)*Pi/360)*moon\radius
    may=pay+Sin((2*moon\angle)*Pi/360)*moon\radius
    Color 150,150,150
    Oval max,may,moon\size,moon\size
    moon\angle=moon\angle+(moon\speed*182)
   EndIf
  Next
  planet\angle=planet\angle+(planet\speed*182)
  cp=cp+1
 Next
End Function