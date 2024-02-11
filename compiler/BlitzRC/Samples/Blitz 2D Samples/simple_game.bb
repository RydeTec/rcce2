;an example of a very - and I mean VERY - simple game!
; verified 1.48 4/18/2001
;use left and right keys to move, and space to fire
;
;hit ESC to exit

;go into graphics mode
Graphics 640,480

;enable double buffering
SetBuffer BackBuffer()

;load images for player, bullet and target
player=LoadImage("graphics\player.bmp")
bullet=LoadImage("graphics\bullet.bmp")
target=LoadImage("graphics\alien.bmp" )

;initialize player position
player_x=320
player_y=440

;initialize target position and direction of movement
target_x=0
target_y=20
target_direction=1

;initialize bullet position and 'fired' flag
bullet_x=0
bullet_y=0
bullet_fired=False

;initialize player score
score=0

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
	
	;has the 'fire' key been hit?
	If KeyHit(57)
	
		;initialize bullet position
		bullet_x=player_x+9
		bullet_y=player_y-10
		
		;enable the bullet
		bullet_fired=True
	
	EndIf
	
	;update the target
	target_x=target_x+target_direction
	
	;if target hits the edge of the screen, make it bounce
	If target_x<0 Or target_x>590
		
		;reverse the direction target is moving in
		target_direction=-target_direction
		
	EndIf
	
	;update the bullet, if it exists
	If bullet_fired
	
		;move the bullet up
		bullet_y=bullet_y-4
		
		;if the bullet is still onscreen, see if it hits the target
		If bullet_y>0
		
			;does the bullet overlap the target?
			If ImagesCollide( bullet,bullet_x,bullet_y,0,target,target_x,target_y,0 )
			
				;yes! the player shot the target! increase the score
				score=score+100
				
				;reset the target
				target_x=0
				target_direction=Abs(target_direction)
				
				;increase the speed of the target a little!
				target_direction=target_direction+1
				
				;and turn the bullet off
				bullet_fired=False
				
			EndIf
		
		Else
		
			;bullet has gone offscreen - reduce the score
			score=score-20
		
			;and disable the bullet
			bullet_fired=False
			
		EndIf
		
	EndIf
	
	;clear the screen
	Cls
	
	;draw the target
	DrawImage target,target_x,target_y
	
	;draw the bullet, if it exists
	If bullet_fired Then DrawImage bullet,bullet_x,bullet_y
	
	;draw the player
	DrawImage player,player_x,player_y
	
	;show the score
	Text 320,0,score,1
	
	;swap front and back buffers
	Flip
	
Wend