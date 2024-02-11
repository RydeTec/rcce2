AppTitle "Novell screensaver"

; Version 2.0
; Made by Einar Wedoe
; Programtype: Novell server screensaver look-a-like
; Use arrow-keys to change speed and length of tail !


Graphics 800,600,16,1															; Sett Grphics mode
Color Rnd (255),Rnd (255),Rnd (255)																	; Set Color

Global x=400																	; Leader-dot-x-pos
Global y=300																	; Leader-dot-y-pos
Global direction=1																; Direction variable
Global directiontmp=0															; Temporary direction variable
Global round=10																	; How long between every change of direction ?
Global roundcount=0																; Counter for the rounds
Global speed#=3																	; Speed
Global speedcount=1																; Counter for speed
Global length=35																; Initial tail-length
Global show=0																	; Variable for showing speed and length on screen

Dim tailX(100)																	; Tail x can be up to 1000 blocks long
Dim tailY(100)																	; Tail y can be up to 1000 blocks long

For a=1 To 100																	; Set the current tail off the screen
 tailx(a)=-20
 taily(a)=-20
Next

SeedRnd MilliSecs()																; Make random even more random 
Global frametimer=CreateTimer(75)												; Set up timer to make everything run nice on all PC's
SetBuffer BackBuffer()															; Start dobbelbuffering
;-----------------------------------------	
.start																			; Mainloop

While Not KeyHit(1)																; Loop until ESC is pressed
WaitTimer(frametimer)															; Wait....
Cls																				; Clean up

novell																			; Do stuff
changespeedortail																; Wanna change speed or/and tail ?

Flip																			; Change buffer
Wend																			; Next round please
End	
;-----------------------------------------
Function novell()																; Start the function to actually put something on the screen
speedcount=speedcount+1
 If speed > speedcount Then Goto draw

Select direction																; Moving blocks about
		Case 1 
		x=x-11
		y=y-13
		
		Case 2
		y=y-13
		
		Case 3
		y=y-13
		x=x+11
		
		Case 4
		x=x+11
	
		Case 5
		x=x+11
		y=y+13
		
		Case 6
		y=y+13
		
		Case 7
		x=x-11
		y=y+13
		
		Case 8
		x=x-11	
End Select

speedcount=0
border																			; Check if we hit the border
	If direction=100 Then  roundcount=round+1									; If we did, do something about it !
directiontest																	; Check if time for change of direction

For a=length To 1 Step-1														; Move the x and y down the line
	tailx(a+1)=tailx(a)
	taily(a+1)=taily(a)
Next
tailx(1)=x																		; Put x in the end of the line
taily(1)=y																		; Put y in the end of the line
			
			
.draw


colordrop=Rnd(255)/length															; Find how much to drop color pr. rect
colortmp=Rnd (255)																		; Set color to 0
For a=length To 1 Step -1														; Draw tail											
  Color colortmp,Rnd(255),Rnd(255)															; Set color for current rect
  Rect tailx(a),taily(a),10,12,1												; Draw current rect
  colortmp=colortmp+colordrop													; Count up color
Next 


End Function
;-----------------------------------------
Function border()																; Set direction to 100 if border is reached
Select direction
		Case 1 
		If 	x < 15 Or y < 5 Then direction=100
		Case 2
		If y < 5 Then direction=100
		Case 3
		If x > 780 Or y < 5 Then direction=100
		Case 4
		If x > 780 Then direction=100
		Case 5
		If x > 780 Or y > 585 Then direction=100
		Case 6
		If y > 585 Then direction=100
		Case 7
		If x < 15 Or y > 585 Then direction=100
		Case 8
		If x < 15 Then direction=100
End Select
End Function
;-----------------------------------------
Function directiontest()														; Find new direction if it's time
roundcount=roundcount+1				
	If roundcount > round Then													; Time for a change
						  directiontmp=direction								; Temporary store old direction
.findnewdirection		  direction=Rand(8)										; Find new direction
					      If direction=directiontmp Then Goto findnewdirection	; If it's the same then try again
						  border												; Check for border
						  If direction=100 Then Goto findnewdirection			; Bounce border
						  roundcount=0											; Reset counter
						  round=Rand(20)										; Set new point for change of direction again
						  EndIf		
						
						
End Function
;-----------------------------------------
Function changespeedortail()													; Change things	
	If KeyDown(200) And length < 99 Then 										; Add length of tail if arrow-up is pressed
									length=length+1								; All length by 1
									tailx(length)=-20							; Set new x-part of tail offscreen
									taily(length)=-20							; Set new x-part of tail offscreen
									show=255									; Set show variable to print to scren for 200 rasters
									EndIf
	If KeyDown(208) And length > 5 Then 										; Decrease length of tail if arrow down is pressed
									length=length-1								; Dec by 1
									show=255									; Set show variable to print to scren for 200 rasters
									EndIf
	If KeyDown(203) And speed < 10 Then 										; Add speed if arrow-right is pressed
									speed=speed+0.05							; Add speed by 0.05
									show=255									; Set show variable to print to scren for 200 rasters
									EndIf							
	If KeyDown(205) And speed > 1 Then 											; Dec speed if arrow-left is pressed		
									speed=speed-0.05							; Dec speed by 0.05
									show=255									; Set show variable to print to scren for 200 rasters
									EndIf
show=show-1																		; Decrease show-variable
If show < 0 Then show=0															; If show drops below zero keep it on zero
If show > 0 Then 																; If show-variable is activated
			Color show,0,0														; Fade away text
			Text 0,0, "Length: "+length											; Show length
			Text 0,12,"Speed:  "+Int(10-speed+1)								; Show speed but since speed is oposite of normal recalculate it
			EndIf								
									
End Function
;-----------------------------------------