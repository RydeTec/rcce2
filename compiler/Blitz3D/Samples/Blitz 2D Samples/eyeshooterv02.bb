; EyeShooter 
; Shane R. Monroe 
; Version 0.2
; What's New:
; 	- added scoring (shots cost you 50 points!)
;	- added level counter
;	- game over when hit   
;   - ship can't go off the right side of screen

; Define Constant Variables
Const width=640,height=480

; Define Global Variables (usable through all functions
Global graphPlayer, graphShot, graphAlien, shots,p.Ship, a.Shot, e.Alien, Total_aliens,alien_speed
Global sndShoot, sndBoom, graphBack, score, level


; Player's Ship Structure (think of it as an object or a mini-array)
; We need X position, Y position, and Players lives in here
Type Ship
	Field x,y,lives
End Type 

; Shot Structure (need only X and Y values)
Type Shot
	Field x,y
End Type

; Alien Structure (x, y, direction of motion, and state [dead or alive])
Type Alien
	Field x,y,dir,state
End Type

; Set our graphics mode of 640x480
Graphics width,height

; Enable double buffering
SetBuffer BackBuffer()

level=1 ; our starting level is 1

; Call the function to load graphics and sound effects
LoadGraphics()

; Start our alien move speed at 5 pixels each update
alien_speed=5

; create the player's ship object
p.Ship = New Ship

; Using a slash to denote the field, let's change the structure's x and y
p\x=100
p\y=380


; Let's draw the player's image initially
DrawImage graphPlayer,p\x,p\y

; Now we call function to create eyeballs
createAlien()

; main program loop
While Not KeyHit(1) ; do this until player hits ESC

	; Screen clean up; clear it and redraw the backdrop
	Cls
	DrawImage graphBack,0,0
	
	; Let's write the score and level to the screen
	Text 0,460,"Score:" + score
	Text 560,460,"Level:" + level
	; Let's see if the player shoots and start the shot function if he hasn't already
	If (MouseDown(1) And shots=0)
		score=score-50
		shots=1 ; increment our shot flag
		PlaySound(sndShoot) ; play the shooting sound
		createShot() ; actually create the shot
	End If

	; Assign the mouse's X coordinates to the player's ship structure x and y
	p\x=MouseX()
	
	; Don't let the ship slip off the right of the screen
	If p\x>585 Then p\x=585

	; draw the player
	DrawImage graphPlayer,p\x,p\y
	
	; is there a shot?  Let's update the location of the bullet
	If shots=1
		updateShot()
	End If

	; Are all the aliens gone?  If not, let's update the ones left, otherwise, speed up and create more
	If Total_aliens>0 Then
		updateAlien()
	Else
		alien_speed=alien_speed+3
		level=level + 1
		createAlien()
	End If
	
	; finally, we redraw our screen with the newly constructed one
	Flip

Wend


Function LoadGraphics()

	; This just loads the sounds and graphics
	graphPlayer=LoadImage( "player.bmp" )
	graphAlien=LoadImage( "alieneye.bmp" )
	graphBack=LoadImage( "backdrop.bmp" )
	graphShot=LoadImage( "shot.bmp" )
	sndShoot=LoadSound( "shoot.wav" )
	sndBoom=LoadSound( "kazap.wav" )

End Function

Function createAlien()

; Set the total number of aliens
Total_aliens=10

; Now, let's create an array of these alien 'structures' to track our eyeballs
For t = 1 To Total_aliens
	e.Alien= New Alien
	e\x=100+(t*50) ; Let's fan them out a bit
	e\y=10
	e\dir=0 ; initially, we'll set all their directions
	e\state=1 ; 1= Alive, 0=Dead
Next

End Function


Function createShot()

	; Create a new shot array of structures
	a.Shot = New Shot
	a\x=p\x+20 ; base it off the player's X
	a\y=360

End Function

Function updateShot()
	; make the shot trave up 10 pixels
	a\y=a\y - 10

	; actually DRAW the bullet
	DrawImage graphShot,a\x,a\y

	; Did the bullet leave the screen?
	If a\y<=0
		shots=0 ; flag our shots back to 0
		Delete a ; delete this array item
	End If
	
End Function

Function updateAlien()

; Loop through the Alien structure array
For e.Alien = Each Alien

	; Is this alien alive?  If so, let's process him
	If e\state=1
		
		; what direction is he going?  if 0, we'll move him left, if 1, we'll go right
		If e\dir=0
			e\x=e\x - alien_speed
				
				; Did the alien reach the edge of the screen?  Reverse him
				If e\x<=0 
					e\dir=1
					e\y=e\y+10
				End If
		Else 
			e\x=e\x + alien_speed

				; Did the alien reach the edge of the screen?  Reverse him
				If e\x>=550
					e\dir=0
					e\y=e\y+10
				End If
		End If

		; Is there a shot on the screen?  We need to check for collisions
		If shots=1 
			
			; Is the shot structure overlapping this alien?
			If ImagesOverlap( graphShot,a\x,a\y,graphAlien,e\x,e\y )
				e\state=0 ; Kill the alien
				shots=0 ; reset the shot variable
				Delete a ; delete the shot structure
				Total_aliens=Total_aliens-1 ; decrement our 'alien' counter
				PlaySound(sndBoom) ; badabing, badaboom
				;
				; Complex scoring based on level, how many are left on the level
				;
				score=score + (level * (10 * (11-Total_aliens)))
			End If
			
			

			
		End If

		; Check for player collision
		If ImagesOverlap( graphAlien,e\x,e\y,graphPlayer,p\x,p\y )
				PlaySound(sndBoom) ; badabing, badaboom
				End
		End If


		;Let's draw the alien
		DrawImage graphAlien,e\x,e\y

	End If
	
	
	
Next 
End Function