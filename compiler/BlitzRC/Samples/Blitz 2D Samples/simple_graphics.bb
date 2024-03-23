
;simple graphics example
;
;hit ESC to exit

;go into graphics mode
Graphics 640,480

;keep looping until ESC hit
While Not KeyDown(1)

	;set a random color
	Color Rnd(255),Rnd(255),Rnd(255)
	
	;draw a random rectangle
	Rect Rnd(640),Rnd(480),Rnd(32),Rnd(32)
	
Wend
