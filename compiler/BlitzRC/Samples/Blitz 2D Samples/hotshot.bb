; mousetest

Graphics 640,480
SetBuffer BackBuffer()
Global tile = LoadImage ("tile.bmp") 
Global mouse = LoadImage("mouse.bmp")

Type Tile
	Field x#,y#
	Field frame#
End Type

MaskImage mouse,0,0,0

DrawGrid()

While Not KeyDown(1)
	Cls	
		
	DrawGrid()
	update_mouse()
	
Flip
Wend

End



Function DrawGrid()
	Color 255,255,255
	
	;tile = CreateImage(32,32,1)

	HandleImage tile,0,0
	HandleImage mouse,16,5
		
	
	
	;draw the tiles on screen
	For x=135 To 432 Step 33
		For y=76 To 373 Step 33
			DrawImage tile,x,y
		Next				
	Next
		
	;draw vertical lines
	y1=75:y2=406
	For x1=134 To 465 Step 33
		Line x1,y1,x1,y2
	Next	
	
	;draw horizontal lines
	x1=134:x2=465
	For y1=75 To 406 Step 33
		Line x1,y1,x2,y1
	Next
			
End Function

Function update_mouse()
	
	DrawImage mouse,MouseX(),MouseY()
	
End Function