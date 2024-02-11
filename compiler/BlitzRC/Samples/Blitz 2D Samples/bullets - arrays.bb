; *************************
; * Quick bullet shooting *
; * using arrays tutorial *
; *************************

AppTitle "Bullet shooting using arrays"

; switch to graphics mode and set the doublebuffering.
Graphics 640,480
SetBuffer BackBuffer()

; maximum bullets in bullets list
max_bullets = 50

; DIM an array with the maximum amount of bullets
; and 2 slots for each of those to hold the bullet
; x,y locations.
Dim bullets(max_bullets,2)

; Load the cheesy graphics.
Global ship = LoadImage("player1.bmp")
Global bull = LoadImage("shot1 - right.bmp")

; Set some global variables.
Global player_x = 20									; Player X location on progam start.
Global player_y = 220									; Player Y location of program start.
Global bullet_num = 0									; Counter for bullets.

; The main loop.
While Not KeyDown(1)
	update_player()										; Draw, move and keep the player on the screen.
	update_bullets()									; Draw and move any bullets on screen.
	Flip												; Flip buffers
	Cls													; Clear the screen.
Wend

; This function moves the player.
; Keep the player on the screen.
; Draws the player.
; Lets the player shoot bullets.
Function update_player()
	; Read arrow keys and move the player x,y locations.
	If KeyDown(200)
		player_y = player_y - 4
	EndIf
	If KeyDown(208)
		player_y = player_y + 4
	EndIf
	If KeyDown(205)
		player_x = player_x + 4
	EndIf
	If KeyDown(203)
		player_x = player_x - 4
	EndIf
	; Space pressed? fire a bullet!
	If KeyHit(57)
		shoot_bullet()									; Goes to the function that generates a bullet.
	EndIf

	; Keep the player in the limits of the screen.
	If player_y < 0
		player_y = 0
	EndIf
	If player_y > 462
		player_y = 462
	EndIf
	If player_x < 0
		player_x = 0
	EndIf
	If player_x > 607
		player_x = 607
	EndIf

	; Draw the player.
	DrawImage ship,player_x,player_y
End Function

; Here we generate a bullet
; and place it in front of the
; players ship.
Function shoot_bullet()
	; Increase bullet counter, we use this to find
	; a location in the array and to wrap
	; to the first array element when no more bullets
	; can be put in the array (array full).
	bullet_num = bullet_num + 1
	; Here we check the array.. if it's full
	; we start at the beginning again.
	If bullet_num > 50
		bullet_num = 1
	EndIf
	; This places the x,y locations of the bullet
	; in the array.. they are placed at the
	; player location and adjusted to place
	; in front and half way down.
	bullets(bullet_num,1) = player_x + 32
	bullets(bullet_num,2) = player_y + 9
End Function

; Here we actually draw all the bullets.
; Also makes sure that the bullet doesn't
; get drawn anymore when it's off screen.
Function update_bullets()
	If bullet_num > 0										; First we check if any bullets are actually active.
		For bul_counter = 1 To bullet_num					; Go through ALL the bullets!
			bullets(bul_counter,1) = bullets(bul_counter,1) + 10	; Move the bullet 10 pixel each loop.
			If bullets(bul_counter,1) < 650							; Is the bullet on screen?
				; Draw the bullet!
				DrawImage bull,bullets(bul_counter,1),bullets(bul_counter,2)
			EndIf
		Next
	EndIf
End Function