; a test to see how many images blitz can display on screen at once
; Richard O' Keeffe
;######################################################################

Global screenw = 800:screenh = 600
Graphics screenw,screenh
Global imno = 1000 ; play with this number
SetBuffer FrontBuffer()
Text 400,300,"Please wait grabbing images......",1,1
Global temp = CreateImage(64,64,imno)

Type Block
	Field imageno
	Field x#,y#
	Field dir
End Type	

Global b.Block

SetBuffer BackBuffer() 
getimage()

draw()
Flip

; main loop
While Not KeyDown(1)
	
	move()
	
	Flip
	Cls	
Wend

Function move()
			
	For b.Block = Each Block
		DrawImage temp,b\x,b\y,b\imageno
		b\x = b\x +Rnd(-4,4)
		b\y = b\y +Rnd(-4,4)
		If b\x <= 0 Then b\x =1
		If b\x >= 800-64 Then b\x =b\x -1
		If b\y <= 0 Then b\y =1
		If b\y >= 600-64 Then b\y =b\y-1
	Next
	Text 400,0," Images On Screen:" + imno
	
End Function


; draw the images at random positions around the screen
Function draw()
	SeedRnd MilliSecs()

	For b.Block = Each Block
		b\x = Rnd(0,700)
		b\y = Rnd(0,500)
		DrawImage temp,b\x,b\y,b\imageno
	Next
End Function

; Draw and grab the image
Function getimage()
	Color 255,255,192
	Oval 32,32,32,32 
	Color 130,133,248
	Oval 32,32,31,31
	Color 255,255,0
	Oval 39,39,4,4
	Oval 52,39,4,4
	
	
		For im = 0 To imno-1
			b.Block = New Block   
			GrabImage temp,0,0,im
			b\imageno = im
		Next
		
	Cls
End Function

	



				