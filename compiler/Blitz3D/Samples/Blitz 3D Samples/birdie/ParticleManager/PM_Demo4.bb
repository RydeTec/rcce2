;----------------------
;Particle Demo 4 : Fire
;----------------------
;Controls: Arrow keys to rotate ~ Spacebar to pause

Graphics3D 640,480
Include "ParticleManager.bb"

light = CreateLight()

AmbientLight 0,0,0

ground = CreatePlane(16)
groundtex = LoadTexture("Ground.bmp")
ScaleTexture groundtex,10,10
EntityTexture ground,groundtex

log1= CreateCylinder(8)
ScaleEntity log1,.2,1,.2 : EntityColor log1,80,50,20
RotateEntity log1,90,45,0 : PositionEntity log1,.2,.1,0
log2 = CopyEntity(log1)
RotateEntity log2,100,90,0 : PositionEntity log2,-.2,.2,0
log3 = CopyEntity(log1)
RotateEntity log3,100,-45,0 : PositionEntity log3,.4,.2,0

pivot = CreatePivot()

camera = CreateCamera(pivot)
PositionEntity camera,0,5,10
RotateEntity camera,10,180,0

flame = CreateLight(2)
PositionEntity flame,0,1,0
LightColor flame,255,200,200
LightRange flame,50

PM_AddEmitter(2,0,0,0,1,0)	;fire
PM_AddEmitter(5,0,0,0,1,0)	;sparks

While Not KeyHit(1)
	LightRange flame,Rnd(30,50)
	If KeyDown(203) TurnEntity pivot,0,2,0,1
	If KeyDown(205) TurnEntity pivot,0,-2,0,1
	If KeyHit(57) pause = Not pause
	If Not pause PM_Update(camera)
	UpdateWorld : RenderWorld : Flip
Wend : End