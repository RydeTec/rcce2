; Little Maze
; By Reda Borchardt

Include "start.bb"

SetBuffer BackBuffer()


camera=CreateCamera()
MoveEntity camera,0,20,-20
RotateEntity camera,45,0,0

light=CreateLight(2)
LightColor light,30,30,250
PositionEntity light,-20,20,-20

p = CreatePivot()

outerbox=LoadMesh("outerbox.3ds")
innerbox=LoadMesh("innerbox.3ds",p)
walls=LoadMesh("walls.3ds",p)
ground=LoadMesh("floor.3ds")
knobs=LoadMesh("knobs.3ds")

ball=LoadMesh("ball.3ds",p) ;This pivot reference needs of course to be removed

tex1 = LoadTexture("plate.jpg")
tex2 = LoadTexture("walls.jpg")
tex3 = LoadTexture("floor.jpg")


RotateTexture tex1,270
ScaleTexture tex1,1,1
ScaleTexture tex2,2,2

EntityTexture outerbox,tex1
EntityTexture innerbox,tex2
EntityTexture walls,tex1
EntityTexture ground,tex3
EntityTexture knobs,tex2


While Not KeyHit(1)

	
	If KeyDown(200)	Then
		TurnEntity p,0.2,0,0
	EndIf
	
	If KeyDown(208)	Then
		TurnEntity p,-0.2,0,0
	EndIf
	
	If KeyDown(203)	Then
		TurnEntity p,0,0,0.2 
	EndIf
	
	If KeyDown(205)	Then
		TurnEntity p,0,0,-0.2
	EndIf
	
	UpdateWorld
	RenderWorld
		
	Flip

Wend
End