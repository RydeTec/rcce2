; Flip/Backbuffer()/Scroll/Fade Example
;By Stephen Smith

; Set The Graphics Mode
Graphics 640,480

;Load the Font
Font1 = LoadFont("Times New Roman",40)

;Set the Font
SetFont(Font1)

; Go double buffering
SetBuffer BackBuffer()

;Set Starting Value of Variable (above 640 to start below the bottom of the screen)
YLocation = 480

;Set Initial Color of the Text to Scroll (White)
Color (255,255,255)

While Not KeyHit(1)

	Cls ; Clear the screen
	
	Text 200, YLocation, "Scroll Fade Text" ; Draw the Text

	Flip ; Flip it into view

	YLocation = YLocation - 2 ; Move the text up 2 pixels

	If YLocation  < 255 Then     					; If Y Location of text is equal to or less than 255
		If YLocation > 0 Then       				; If Y Location of text is equal to or greater than 0
			Color (YLocation,YLocation,YLocation)	; set the fade color of the text
		Else							; else
			Goto EndProgram				; Color = 0, so we jump to the end of the program
	
		EndIf
	Else
		Color (255,255,255) 					; else (greater) set color to white
	EndIf
	
Wend 


.EndProgram

;clear font from memory
FreeFont(Font1) 
End





