;Starfield demo
;written by Ed Upton
;adjust for more or less stars (250 looks nice)

Global staramount=250

Graphics 640,480
SetBuffer FrontBuffer ()

;star array
Type star
 Field rad,speed,angle
End Type

setupstars()

;main loop
While Not KeyDown(1)
 Cls
 update()
 Flip
Wend
End

;set up stars
Function setupstars()
 For sc=0 To staramount
  stars.star = New star
  stars\rad=Rnd(280)+60
  stars\angle=Rnd(65535)
  stars\speed=Rnd(5)+1
 Next
End Function

;update each star in turn
Function update()
 For stars.star = Each star
  xx=320+Sin((2*stars\angle)*Pi/360)*stars\rad
  yy=240+Cos((2*stars\angle)*Pi/360)*stars\rad
  ;replace star if it goes off screen
  If xx<0 Or xx>639 Or yy<0 Or yy>479
   stars\rad=Rnd(40)+60
   stars\angle=Rnd(65535)
   stars\speed=Rnd(5)+1
  EndIf
  stars\rad=stars\rad+stars\speed
  If stars\speed=1 Then Color 50,50,50
  If stars\speed=2 Then Color 100,100,100
  If stars\speed=3 Then Color 150,150,150
  If stars\speed=4 Then Color 200,200,200
  If stars\speed=5 Then Color 250,250,250
  Plot xx,yy
 Next
End Function