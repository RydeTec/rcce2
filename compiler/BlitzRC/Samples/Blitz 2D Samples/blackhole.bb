;Starfield demo
;Copyright ©2000 EdzUp
;written by Ed Upton
; verified 1.48 4/18/2001

;adjust for more or less stars (500 looks nice)
;I can get 2500 stars without losing a single frame
Global staramount=1000

Graphics 640,480
SetBuffer BackBuffer()

Global stara=LoadImage("graphics\star1.bmp")
Global starb=LoadImage("graphics\star2.bmp")
Global starc=LoadImage("graphics\star3.bmp")

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
  stars\rad=Rnd(280)+80
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
  If xx>290 And xx<350 And yy>210 And yy<270
   stars\rad=Rnd(40)+400
   stars\angle=Rnd(65535)
   stars\speed=Rnd(5)+1
  EndIf
  stars\angle=stars\angle+100
  stars\rad=stars\rad-(stars\speed*2)
  If stars\speed=1 Then DrawImage starc,xx,yy
  If stars\speed=2 Or stars\speed=3 Then DrawImage starb,xx,yy
  If stars\speed>3 Then DrawImage stara,xx,yy
;  If stars\speed=1 Then Color 50,50,50
;  If stars\speed=2 Then Color 100,100,100
;  If stars\speed=3 Then Color 150,150,150
;  If stars\speed=4 Then Color 200,200,200
;  If stars\speed=5 Then Color 250,250,250
;  Plot xx,yy
 Next
 Color 0,0,0
 Oval 260,180,120,120
End Function