; Simple ball test
; By Steve Smith
; (my first ever Blitz program)
; press left mouse to quit

Const c_balls=10; Number of Balls

Global g_numballs=c_balls

Type t_ball 	;    Define the type for all balls
	Field x#	;    x coord of ball
	Field y#	;	 y coord of ball
	Field dx#	;	 x movement
	Field dy#	;	 y movement
	Field im	; 	 Image number
	Field s#	;	 speed of ball
	End Type

	Graphics 640,480; Set Graphics mode

Global g_balls=LoadAnimImage("balls.png",24,24,0,8) ; Load ball pictures
Global g_bg=LoadImage("bg.jpg")						; Load background
Global g_collide=LoadSound("hit.wav")				; Load Sound


	For a=1 To g_numballs			;loop to create all balls
		balls.t_ball=New t_ball		;Create a new ball
		balls\x#=Rand(600)+16		;random X coord
		balls\y#=Rand(400)+16		;random Y coord
		balls\dx#=Rnd(2)-1			;movement between -1 and 1	
		balls\dy#=Rnd(2)-1			;
		balls\im=a Mod 8			;image frame
		balls\s#=Rnd(2)+.3			;speed
		Next 
	
	SetBuffer BackBuffer()
	
	While MouseDown(1)=0
		TileBlock g_bg,0,0
	 	For balls.t_ball=Each t_ball
			balls\x#=balls\x#+(balls\dx#*balls\s#)
			balls\y#=balls\y#+(balls\dy#*balls\s#)
			; check for screen limits
			If balls\y#<0      Then balls\dy#=1
			If balls\y#>480-24 Then balls\dy#=-1
			If balls\x#<0      Then balls\dx#=1
			If balls\x#>640-24 Then balls\dx#=-1
			; check for colisions with other balls
			For balls2.t_ball=Each t_ball
				If balls<>balls2 Then ; make sure it's not the same ball
					If ImagesCollide(g_balls,balls\x#,balls\y#,balls\im,g_balls,balls2\x#,balls2\y#,balls2\im) Then 
						If balls\x#<balls2\x# Then balls\dx#=-1
						If balls\x#>balls2\x# Then balls\dx#=1
						If balls\y#<balls2\y# Then balls\dy#=-1
						If balls\y#>balls2\y# Then balls\dy#=1
						PlaySound(g_collide)
						End If
					End If
				Next
			DrawImage g_balls,balls\x#,balls\y#,balls\im
			Next
		Flip 1
		Wend
	EndGraphics
	End