;----------------------
;Particle Demo 2 : Rain
;----------------------
;Controls: Arrow keys to rotate ~ Spacebar to pause

Graphics3D 640,480
Include "ParticleManager.bb"

AmbientLight 128,128,150

ground = CreatePlane()
groundtex = LoadTexture("Ground.bmp")
ScaleTexture groundtex,100,100
EntityTexture ground,groundtex

sky = CreatePlane()
skytex = LoadTexture("Clouds.bmp")
ScaleTexture skytex,1000,1000
EntityTexture sky,skytex
EntityOrder sky,10
PositionEntity sky,0,100,0
RotateEntity sky,180,0,0

camera = CreateCamera()
PositionEntity camera,0,50,0
CameraClsColor camera,128,128,150
CameraFogRange camera,100,1000
CameraFogMode camera,1
CameraFogColor camera,128,128,150

PM_AddEmitter(1,0,100,0,1,camera)

While Not KeyHit(1)
	u#=u+.0001 : PositionTexture skytex,u,0
	If KeyDown(203) TurnEntity camera,0,2,0,1
	If KeyDown(205) TurnEntity camera,0,-2,0,1
	If KeyDown(200) TurnEntity camera,2,0,0
	If KeyDown(208) TurnEntity camera,-2,0,0
	If KeyHit(57) pause = Not pause
	If Not pause PM_Update(camera)
	UpdateWorld : RenderWorld : Flip
Wend : End