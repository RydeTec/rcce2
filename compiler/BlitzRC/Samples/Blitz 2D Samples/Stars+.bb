; Another Starfield demo
; By Simon Miller


; adjust for more or less stars.
;  250 looks nice - 400 is better!

; - - - - - - - - - - - - - - - - - - - -
; Use the cursor up and down keys to 
; change the warp speed!
; Escape key exits program.
; - - - - - - - - - - - - - - - - - - - -


Global staramount=400
Global vel_per#=0.03

Global GrWidth=640
Global GrHeight=480

Global GrCentreX=GrWidth/2
Global GrCentreY=GrHeight/2

Graphics GrWidth,GrHeight
SetBuffer FrontBuffer ()

;star array
Type star
 
	Field rad#
	Field speed#
	Field angle

End Type

setupstars()

;main loop
While Not KeyDown(1)
	Cls
	update()
	Flip
	Control()
Wend
End

;set up stars
Function setupstars()
	For sc=0 To staramount
		stars.star = New star
		stars\rad#=Rnd(280)+1
		stars\angle=Rnd(65535)
		stars\speed#=Rnd(0.6)+0.01
	Next
End Function

;update each star in turn
Function update()
	For stars.star = Each star
		xx=GrCentreX+Sin((2*stars\angle)*Pi/360)*stars\rad#
		yy=GrCentreY+Cos((2*stars\angle)*Pi/360)*stars\rad#
		
		;replace star if it goes off screen
		
		If xx<0 Or xx>GrWidth Or yy<0 Or yy>GrHeight
			stars\rad#=Rnd(150)
			stars\angle=Rnd(65535)
			stars\speed#=Rnd(0.6)+0.01
		End If
		temp#=(stars\rad# * 3) * vel_per ; the acceleration factor
		stars\rad#=stars\rad#+(stars\speed#/10)+temp#
		Color 100+(stars\speed# * 150),100+(stars\speed# * 150),100+(stars\speed# * 150)
		Plot xx,yy
	Next
End Function


Function Control()

	hit=KeyDown(200) ; Up on Cursors
	If hit=1 Then
		vel_per#=vel_per#+0.0003
	End If
	
	hit=KeyDown(208) ; Down on Cursors
	If hit=1 Then
		vel_per#=vel_per#-0.0003
	End If
	
	If vel_per#<0.0001 Then vel_per#=0.0001
	If vel_per#>3.0 Then vel_per#=3.0

End Function