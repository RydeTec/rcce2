;PaddleBaddle3D
;Code & Models by Michael Reitzenstein [huntersd@iprimus.com.au]
;Copyright 2001 CompuGDE Technologies
;Made in Blitz3D
;Last built in Blitz3D v. 228
;Runs at 60fps on an unaccelerated PII-350 with 64mb ram

;AI on, change to 0 for 2 player action
;Keys (bottom paddle) left & right (top paddle if no AI) a & d
Global AI = 1

Graphics3D 800,600

SetBuffer BackBuffer()

;an array that will hold the id numbers of the paddles
Dim paddles(2)

;create the first paddle (the bottom one)
paddles(1) = LoadMesh("models\paddle.3ds")
PositionEntity paddles(1), 0, -70, 0

;create the second paddle (the top one)
paddles(2) = LoadMesh("models\paddle.3ds")
PositionEntity paddles(2), 0, 65, 0

;create the ball
Global ball = LoadMesh("models\ball.3ds")
PositionEntity ball, 0, 0, 0
ScaleEntity ball, 0.5, 0.5, 0.5

;create the camera
camera = CreateCamera()
PositionEntity camera, 0, 0, -100

;some globals to keep track of the ball
Global balldirection = 1
Global ballangle# = 0

;for handling the offscreen data
Global balloffscreen = 0

Global light1 = CreateLight()
Global light2 = CreateLight(2, ball)
LightRange light2, 5

;main game loop
Repeat
	paddlemoving()
	ballmoving()
	collision()
	graphicalrefresh()
Until KeyDown(1) = 1


Function paddlemoving()
	;If the leftkey is being pressed, move the bottom paddle left
	If KeyDown(203) = 1
		PositionEntity paddles(1), EntityX(paddles(1)) - 3, EntityY(paddles(1)), EntityZ(paddles(1))
	EndIf
	;If the rightkey is being pressed, move the bottom paddle right
	If KeyDown(205) = 1
		PositionEntity paddles(1), EntityX(paddles(1)) + 3, EntityY(paddles(1)), EntityZ(paddles(1))
	EndIf
	
	;The AI functions, fairly advanced (otherwise the paddle movement would be hella jerky)
	If AI;if AI enabled is true
		distance# = EntityX(ball) - EntityX(paddles(2));EntityX(paddles(2)) - EntityX(ball)
		If Abs(distance#) <> distance#;if distance# is a negetive
			If distance# > -1;if the distance between the paddle and the ball is less than one
				;move the paddle according to the size of the distance
				PositionEntity paddles(2), EntityX(paddles(2)) + distance#, EntityY(paddles(2)), EntityZ(paddles(2))
			Else
				;else, move it according to a set value
				PositionEntity paddles(2), EntityX(paddles(2)) - 1, EntityY(paddles(2)), EntityZ(paddles(2))
			EndIf
		Else
			If distance# < 1;if the distance between the paddle and the ball is less than one
				;move the paddle according to the size of the distance
				PositionEntity paddles(2), EntityX(paddles(2)) + distance#, EntityY(paddles(2)), EntityZ(paddles(2))
			Else
				;else, move it according to a set value
				PositionEntity paddles(2), EntityX(paddles(2)) + 1, EntityY(paddles(2)), EntityZ(paddles(2))
			EndIf
		EndIf
	Else
		If KeyDown(30) = 1;if the 'a' key is down, move the paddle left
			PositionEntity paddles(2), EntityX(paddles(2)) - 3, EntityY(paddles(2)), EntityZ(paddles(2))
		EndIf
		If KeyDown(32) = 1;if the 'd' key is down, move the paddle right
			PositionEntity paddles(2), EntityX(paddles(2)) + 3, EntityY(paddles(2)), EntityZ(paddles(2))
		EndIf
	EndIf
	
	For a = 1 To 2
		If EntityX(paddles(a)) < -90
			PositionEntity paddles(a), -90, EntityY(paddles(a)), EntityZ(paddles(a))
		ElseIf EntityX(paddles(a)) > 90
			PositionEntity paddles(a), 90, EntityY(paddles(a)), EntityZ(paddles(a))
		EndIf
	Next
End Function

Function ballmoving()
	;if the balldirection = 1, then move the ball down, if not then move the ball up
	If balldirection = 1
		PositionEntity ball, EntityX(ball) + ballangle#, EntityY(ball)-1, EntityZ(ball)
	Else
		PositionEntity ball, EntityX(ball) + ballangle#, EntityY(ball)+1, EntityZ(ball)
	EndIf

	;spin the ball using some cheesy random numbers
	RotateEntity ball, EntityPitch(ball)+Rand(5), EntityYaw(ball)+Rand(10), EntityRoll(ball)+Rand(10)
	
	;if the ball is going offscreen on the left or right, then flip its angle
	If EntityX(ball) < -100
		ballangle# = ballangle# * -1
	ElseIf EntityX(ball) > 100
		ballangle# = ballangle# * -1
	EndIf
	
		;if the ball is off the screen, then center it and give a point to the appropriate player
	If EntityY(ball) < -100 Or EntityY(ball) > 100
		If balloffscreen = 0
			balloffscreen = 1
		EndIf
	EndIf
		
	;If the ball has been centered onscreen, increment the balloffscreen variable, and keep the ball there until
	;the variable > 200 and is then equal to 0
	If balloffscreen > 0
		PositionEntity ball, 0, 0, 0
		balloffscreen = balloffscreen + 1
		If balloffscreen > 200
			balloffscreen = 0
			ballangle# = 0
		EndIf
	EndIf
End Function

Function collision()
	;meshesintersect(entity_a, entity_b) is a rather slow command but it needs no setting up or anything, and is 
	;very simple
	If MeshesIntersect(ball, paddles(1)) = True Or MeshesIntersect(ball, paddles(2))
		tempvar# = 0
		balldirection = balldirection * -1;change the direction of the ball
		tempvar# = Rand(100) 
		tempvar# = tempvar# / 100;gets a floating point value between 0.00 & 1.00
		If Rand(1) = 1;50% chance that the direction that the ball is going in is going to be flipped
			tempvar# = tempvar# * -1
		EndIf
		ballangle# = tempvar# * 1.5
	EndIf
End Function

Function graphicalrefresh()
	;The big four in updating the game/display :P
	UpdateWorld
	RenderWorld
	Flip
	Cls
End Function