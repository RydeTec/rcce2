; Joystick.bb
;
; By: Simon Hitchen
;
; Requires BB (and a joystick :)
;
; Shows you what values are being returned
; by the various joystick commands in BB
;  (set up to read up to 12 buttons....
;   ... the first 11 on my stick work ) 

If JoyType() = 0			; Is there a joystick ???
	Print "OOPS!!  No joystick found...."
	Delay 5000				; wait for 5 seconds
	End						; ...and then quit
End If


Graphics 800,600			; Set display dimensions

SetBuffer BackBuffer()		; Set up double buffer display

Repeat						; Start of program loop

	Cls						; Clear the drawing buffer

	DX=JoyXDir()			; Read DIGITAL values
	DY=JoyYDir()			; from all 3 axis
	DZ=JoyZDir()			; (X,Y,Z)

	AX#=JoyX()				; Read ANALOG values
	AY#=JoyY()				; from all 3 axis
	AZ#=JoyZ()				; (X,Y,Z)

	Color 100,0,100			; Show DIGITAL input from X & Y axis
	Rect 150,100,150,150
	Color 255,0,255
	Rect 200+DX*50,150+DY*50,50,50 
	Color 0,177,255
	Text 260+DX*60,160+DY*50,"DIGITAL X = "+DX
	Text 260+DX*60,180+DY*50,"DIGITAL Y = "+DY

	Color 0,50,50			; Show ANALOG input from X & Y axis
	Rect 150,275,150,150
	Color 0,100,100
	Oval 150,275,150,150
	Color 0,255,255
	Oval 200+AX*50,325+AY*50,50,50
	Color 0,177,255
	Text 260+AX*60,335+AY*50,"ANALOG X  = "+AX
	Text 260+AX*60,355+AY*50,"ANALOG Y  = "+AY

	Color 100,100,0			; Show DIGITAL & ANALOG input from Z axis
	Rect 150,450,150,50
	Color 200,255,0
	Rect 200+DZ*50,450,50,25
	Color 255,200,0
	Rect 200+AZ*50,475,50,25
	Color 0,177,255
	Text 260+DZ*50,455,"DIGITAL Z = "+DZ
	Text 260+AZ*50,480,"ANALOG  Z = "+AZ


	For lop=1 To 12					; Loop through 12 buttons...
		If JoyDown(lop)				; is this one pressed?
			Color 100,255,100		;  :- yes.... Green
		Else						;
			Color 255,0,0			;  :- no..... Red
		End If
		Oval 550,50+lop*36,20,20
		Text 580,54+lop*36,"Button: "+lop
	Next

	Flip					; Swap display buffer

Until KeyDown(1)			; Quit if ESC pressed

End							; End program