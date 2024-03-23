; Change to graphics mode, nothing tricky here :o>
Graphics 640,480

; Move the point of orign for all drawing commands to the middle of the left edge of the screen.
Origin 0,240

; Step across the entire screen (639 pixels wide)
For degrees=0 To 639

	; Wait 5 milli secsonds
	Delay(5)

	; Calculate the Y co-ordinate of the point of the circle using Sin,
	; and multiply it by 100 (to get a larger radius, try and change it)
	y=Sin(180+degrees)*100

	; Change the pen colour to BLUE.
	Color 0,100,500
	
	; Draw the point along the curve.
	Rect degrees,y,1,1

	; Calculate the Y co-ordinate of the point of the circle using Cos,
	; and multiply it by 100 (to get a larger radius, try and change it)
	y=Cos(180+degrees)*100

	; Change the pen colour to GREEN.
	Color 100,500,0
	
	; Draw the point along the curve.
	Rect degrees,y,1,1

	; Change the pen colour to RED.
	Color 500,0,100
	
	; Draw the point line of referance at 0.
	Rect degrees,0,1,1


; Give us another angle
Next

; Wait for the mouse, and terminate the program
MouseWait
End