; Change to graphics mode, nothing tricky here :o>
Graphics 640,480

; Move the point of orign for all drawing commands to the middle of the screen
Origin 320,240

; Step though all the degrees in a circle (360 in all)
For degrees=0 To 359

	; Wait 5 milli secsonds
	Delay(5)

	; Calculate the X co-ordinate of the point of the circle using Cos,
	; and multiply it by 100 (to get a larger radius, try and change it)
	x=Cos(degrees)*100

	; Calculate the Y co-ordinate of the point of the circle using Sin,
	; and multiply it by 100 (to get a larger radius, try and change it)
	y=Sin(degrees)*100

	; Draw the point on the circle
	Rect x,y,1,1

; Give us another angle
Next

; Wait for the mouse, and terminate the program
MouseWait
End