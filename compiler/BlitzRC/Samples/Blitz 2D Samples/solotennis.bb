; Solo Tennis by Simon Harrison (si@si-design.co.uk)

; Set display mode values
Const width=640,height=480

; Set display mode
Graphics width,height

; Draw to back buffer
SetBuffer BackBuffer()

; Load bat and ball images
bat1=LoadImage("graphics/bat.bmp")
bat2=CopyImage(bat1)
ball=LoadImage("graphics/ball.bmp")

; Load beep sounds
beep1=LoadSound("sounds/beep.wav")
beep2=LoadSound("sounds/beeplow.wav")

; Load high score data
load=ReadFile("sthighscore")
high=ReadLine(load)

; Set font to arial size 32
font=LoadFont("arial",32)
SetFont font

; Set text colour to green
Color 0,255,0 

; Set initial ball position and movement values
ballx#=width/2
bally#=height/2
ballmovx#=2
ballmovy#=2

; Set initial game values
once=2
score=0

; Repeat following loop until escape key is pressed
While Not KeyDown(1)

; Clear screen
Cls

; Print current score to screen
Text width/2,0,score,1

; Print high score to screen
Text width/2,height-32,"High: "+high,1

; Get position of bats
batx1=0
batx2=width-16
baty1=MouseY()
baty2=height-MouseY()-64

; Update ball position values
ballx#=ballx#+ballmovx#
bally#=bally#+ballmovy#

; If ball image collides with either bat image then alter x ball movement value and play beep sound
If once=1 And ImagesCollide(ball,ballx#,bally#,0,bat1,batx1,baty1,0) Then ballmovx#=Rnd(1,10) : PlaySound beep1 : score=score+1 : once=2  
If once=2 And ImagesCollide(ball,ballx#,bally#,0,bat2,batx2,baty2,0) Then ballmovx#=Rnd(1,10)*-1 : PlaySound beep1 : score=score+1 : once=1

; If ball touches top or bottom of screen then alter y ball movement value
If bally#>height-16 Then ballmovy#=Rnd(1,10)*-1 : PlaySound beep2
If bally#<0 Then ballmovy#=Rnd(1,10) : PlaySound beep2

; If bats touch top or bottom of screen then prevent them from going outside of screen
If baty1<0 Then baty1=0
If baty2<0 Then baty2=0
If baty1>height-64 Then baty1=height-64
If baty2>height-64 Then baty2=height-64

; If ball touches left side of screen then save high score if necessary, reset ball and score values
If ballx#<0
If score>high Then high=score : save=WriteFile("sthighscore") : WriteLine save,high : CloseFile save
ballx#=width/2 : bally#=height/2 : ballmovx#=1 : ballmovy#=1 : once=2 : score=0
EndIf

; If ball touches right side of screen then save high score if necessary, reset ball and score values
If ballx#>width-16
If score>high Then high=score : save=WriteFile("sthighscore") : WriteLine save,high : CloseFile save
ballx#=width/2 : bally#=height/2 : ballmovx#=1 : ballmovy#=1 : once=2 : score=0
EndIf

; Draw bat and ball images to screen
DrawImage bat1,batx1,baty1
DrawImage bat2,batx2,baty2
DrawImage ball,ballx#,bally#

; Flip screen buffers
Flip

Wend