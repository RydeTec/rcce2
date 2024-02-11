;
;Grass reeds
;David Bird(Birdie)
;dave@birdie72.freeserve.co.uk
;
Graphics3D 640,480
Include "elibdemo.bb"

SetBuffer BackBuffer()

plane=CreatePlane()
tex0=LoadTexture("tex0.bmp")
ScaleTexture tex0,.5,.5
EntityTexture plane,tex0

cam=CreateCamera()
CameraRange cam,1,100
PositionEntity cam,0,2,0

;Create a reed
Global Gtex=LoadTexture("reed.jpg",3)
Global G_reed=CreateReed(1,.10,19)
EntityTexture G_reed,Gtex
HideEntity G_reed


;add 50 bunches of reeds
For a=1 To 50
	x#=Rnd(-10,10)
	z#=Rnd(-10,10)
	For b=1 To 10
		ent=Add_reed(x+Rnd(-.25,.25),0,z+Rnd(-.25,.25),Rnd(.1,7))
		TurnEntity ent,Rnd(-2,2),Rnd(-10,10),Rnd(-10,10)
	Next
Next

While Not KeyDown(1)
	If KeyDown(203) TurnEntity cam,0,1,0
	If KeyDown(205) TurnEntity cam,0,-1,0
;	If KeyDown(200) TurnEntity cam,1,0,0
;	If KeyDown(208) TurnEntity cam,-1,0,0

	If KeyDown(44) MoveEntity cam,0,0,-.1
	If KeyDown(30) MoveEntity cam,0,0,.1
	Update_reeds()
	UpdateWorld 
	RenderWorld
	Text 0,0,"Grass reeds use the cursors to turn around"
	Text 0,15,"A & Z to move forward and back"
	Flip
Wend

;Erase all reeds
Erase_reeds()

FreeEntity cam
EndGraphics
End