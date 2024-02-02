;a simple scrolling backdrop
; verified 1.48 4/18/2001
;
;hit ESC to exit

;go into graphics mode
Graphics 640,480

;enable double buffering
SetBuffer BackBuffer()

;load in a backdrop image
backdrop=LoadImage("graphics\stars.bmp")

;initialize scroll variable to 0
scroll_y=0

;loop until ESC hit
While Not KeyDown(1)

	;draw the backdrop
	TileBlock backdrop,0,scroll_y
	
	;scroll the backdrop
	scroll_y=scroll_y+1
	If scroll_y=ImageHeight(backdrop) Then scroll_y=0
	
	;flip the front and back buffers
	Flip

Wend

	