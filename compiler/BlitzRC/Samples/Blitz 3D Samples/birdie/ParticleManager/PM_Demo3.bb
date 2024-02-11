;----------------------------
;Particle Demo 3 : Magic Wand
;----------------------------
;Controls: Arrow keys to spin wand ~ Spacebar to pause ~ Left mouse button to cast

Graphics3D 640,480
Include "ParticleManager.bb"

light = CreateLight()

camera = CreateCamera()
CameraClsColor camera,32,0,32

wand = CreateCylinder(8,1,camera)
wandtex = LoadTexture("Wand.bmp")
EntityTexture wand,wandtex
ScaleEntity wand,2,25,2

pivot = CreatePivot(wand)
PositionEntity pivot,0,1,0

PM_AddEmitter(3,0,0,0,1,pivot)

RotateEntity wand,60,0,0

While Not KeyHit(1)
	x# = 2.0*(MouseX()-640/2)/8
	y# = -2.0*(MouseY()-480/2)/8
	z# = 640/8
	PositionEntity wand,x,y,z
	
	If MouseHit(1) PM_AddEmitter(0,0,0,0,0,pivot)
	If KeyDown(203) TurnEntity wand,0,0,5
	If KeyDown(205) TurnEntity wand,0,0,-5
	If KeyDown(200) TurnEntity wand,5,0,0
	If KeyDown(208) TurnEntity wand,-5,0,0
	If KeyHit(57) pause = Not pause
	If Not pause PM_Update(camera)
	UpdateWorld : RenderWorld : Flip
Wend : End