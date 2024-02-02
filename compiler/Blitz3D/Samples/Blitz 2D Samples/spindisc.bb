; Spin the Disc by Simon Harrison (si@si-design.co.uk)

; The aim of the game is to click the disc as many times as you can before it disappears off the edge of the screen!

; Set display mode values
Const width=640,height=480

; Set display mode
Graphics width,height

; Draw to back buffer
SetBuffer BackBuffer()

; Load disc image
disc=LoadImage("graphics/disc.bmp")

; Set the handle of the disc image at its centre
HandleImage disc,32,32

; Set disc image rotations value
rots=64

; Initialise array to store each rotated image of disc
Dim spindisc(rots)

; Rotate disc image
anglestep#=360.0/rots
For discno=0 To rots-1
spindisc(discno)=CopyImage(disc)
RotateImage spindisc(discno),angle#
angle#=angle#+anglestep#
Next

; Load mouse pointer image
pointer=LoadImage("graphics/pointer.bmp")

; Load high score data
load=ReadFile("sdhighscore")
high=ReadLine(load)

; Set font to arial size 32
font=LoadFont("arial",32)
SetFont font

; Set initial disc position values
discx=width/2
discy=height/2

; Repeat following loop until escape key is pressed
While Not KeyDown(1)

; Seed the random number generator with the current timer value
SeedRnd(MilliSecs())

; Clear the screen
Cls

; Print current score to screen
Text width/2,0,score,1

; Print high score to screen
Text width/2,height-32,"High: "+high,1

; Update disc position values
discx=discx+discmovx
discy=discy+discmovy

; Alter frame value depending on disc movement values
frame=frame+(discmovx+discmovy)

; Wrap the frame values
If frame>63 Then frame=frame-64
If frame<0 Then frame=frame+64

; Draw disc image to screen using current frame value
DrawImage spindisc(frame),discx,discy

; Draw pointer image to screen at current mouse x and mouse y positions
DrawImage pointer,MouseX(),MouseY()

; If left mouse button is not being pressed then release flag=1 (this means the mouse button has been released)
If MouseDown(1)=0 Then release=1

; If pointer image overlaps disc image and left mouse button pressed...
If ImagesOverlap(pointer,MouseX(),MouseY(),disc,discx,discy)=1 And MouseDown(1)=1

; If release flag=1 then randomise disc movement values
If release=1
discmovx=Rnd(0,10)-Rnd(0,10) : discmovy=Rnd(0,10)-Rnd(0,10) : score=score+1
EndIf

; Release flag=0 (this means the mouse button has not yet been released since last pressing it)
release=0

EndIf

; If disc position values are outside screen boundaries...
If discx<0-32 Or discx>width+32 Or discy<0-32 Or discy>height+32

; If current score is above high score then make current score the high score and save high score
If score>high Then high=score : save=WriteFile("sdhighscore") : WriteLine save,high : CloseFile save

; Reset values
score=0
discx=width/2
discy=height/2
discmovx=0
discmovy=0

EndIf

; Flip screen buffers
Flip

Wend