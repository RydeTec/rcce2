;example of how to move a 'player' with the keyboard
; verified 1.48 4/18/2001
;use left and right arrow keys to move the player image
;
;hit ESC to exit

;go into graphics mode
Graphics 640,480

;enable double buffering
SetBuffer BackBuffer()

;load a player image
player=LoadImage("graphics\player.bmp")

;initialize player position
player_x=320
player_y=440

;loop until ESC hit...
While Not KeyDown(1)

	;is left key being held?
	If KeyDown(203)
	
		;move player to the left
		player_x=player_x-4
		
		;stop the player going 'off screen'
		If player_x<0 Then player_x=0
		
	EndIf
	
	;is right key being held?
	If KeyDown(205)
	
		;move player to the right
		player_x=player_x+4
		
		;stop the player going 'off screen'
		If player_x>604 Then player_x=604
	
	EndIf
	
	;clear the screen
	Cls
	
	;draw the player
	DrawImage player,player_x,player_y
	
	;swap front and back buffers
	Flip
	
Wend