; Little Intro
; By Reda Borchardt

Graphics3D 640,480,32,1

SetBuffer BackBuffer()

p = CreatePivot()

tex = LoadTexture("floor.jpg",64)
image = LoadImage("top-banner.jpg")
image2 = LoadImage("bottom-banner.jpg")
MaskImage image,255,255,0
MaskImage image2,255,255,0

camera=CreateCamera()
PositionEntity camera,5,5,5
PointEntity camera, p

light=CreateLight(2)
LightColor light,250,250,130
PositionEntity light,5,5,5

light=CreateLight(2)
LightColor light,250,250,250
PositionEntity light,-5,-5,-5

light=CreateLight(2)
LightColor light,250,250,250
PositionEntity light,5,5,5
CameraFogRange camera,1,10

light=CreateLight(2)
LightColor light,250,250,0
PositionEntity light,5,-5,5

brush = CreateBrush()
BrushAlpha brush,0.1
BrushColor brush,250,255,0
BrushBlend brush,1
BrushShininess brush,0.5
BrushTexture brush,tex

cube = CreateCube(p)
ScaleEntity cube,0.7,0.7,0.7
PositionEntity cube,-1,-1,-1
PaintMesh cube,brush

cube2 = CreateCube(p)
ScaleEntity cube2,0.7,0.7,0.7
PositionEntity cube2,-1,1,-1
PaintMesh cube2,brush

cube3 = CreateCube(p)
ScaleEntity cube3,0.7,0.7,0.7
PositionEntity cube3,-1,1,1
PaintMesh cube3,brush

cube4 = CreateCube(p)
ScaleEntity cube4,0.7,0.7,0.7
PositionEntity cube4,-1,-1,1
PaintMesh cube4,brush

cube5 = CreateCube(p)
ScaleEntity cube5,0.7,0.7,0.7
PositionEntity cube5,1,-1,-1
PaintMesh cube5,brush

cube6 = CreateCube(p)
ScaleEntity cube6,0.7,0.7,0.7
PositionEntity cube6,1,1,-1
PaintMesh cube6,brush

cube7 = CreateCube(p)
ScaleEntity cube7,0.7,0.7,0.7
PositionEntity cube7,1,1,1
PaintMesh cube7,brush

cube8 = CreateCube(p)
ScaleEntity cube8,0.7,0.7,0.7
PositionEntity cube8,1,-1,1
PaintMesh cube8,brush

p2 = CopyEntity(p,p)
ScaleEntity p2,0.5,0.5,0.5

p3 = CopyEntity(p,p)
ScaleEntity p3,1.5,1.5,1.5

p4 = CopyEntity(p,p)
ScaleEntity p4,2,2,2 


While Not KeyHit(1)
	
	
		TurnEntity p,0.2,0,0.05
		TurnEntity p,-0.05,0,.05
			
	
	UpdateWorld
	RenderWorld
	DrawImage image,0,0
	DrawImage image2,0,420	
	Flip

Wend
End