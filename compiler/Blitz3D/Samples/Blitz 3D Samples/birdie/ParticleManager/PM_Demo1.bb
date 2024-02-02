;--------------------------
;Particle Demo 1 : Firework
;--------------------------
;Controls: Arrow keys to rotate ~ Spacebar to pause

Graphics3D 640,480
Include "ParticleManager.bb"

light = CreateLight()
pivot = CreatePivot()
cyl = CreateCylinder(8) : EntityColor cyl,0,0,150
ScaleEntity cyl,5,10,5 : PositionEntity cyl,0,10,0
camera = CreateCamera(pivot)
PositionEntity camera,0,50,-125

PM_AddEmitter(4,0,20,0,1,cyl)

While Not KeyHit(1)
	If KeyDown(203) TurnEntity pivot,0,2,0
	If KeyDown(205) TurnEntity pivot,0,-2,0
	If KeyDown(200) TurnEntity pivot,2,0,0
	If KeyDown(208) TurnEntity pivot,-2,0,0
	If KeyHit(57) pause = Not pause
	If Not pause PM_Update(camera)
	UpdateWorld : RenderWorld : Flip
Wend : End