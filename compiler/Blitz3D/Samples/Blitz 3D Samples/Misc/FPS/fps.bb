;SWITCH LEVELS HERE:
leveltoselect = 2
;leveltoselect = 1

frametimer = CreateTimer(60)

Global forwardspeed#

Const type_playerbox = 1
Graphics3D 800,600
SetBuffer BackBuffer()
;
;Include "../start.bb"
Const type_backdrop = 10

Include "PIO.bb"
Include "functions.bb"

Global level
If leveltoselect = 1
	level = LoadMesh("models\level\level.3ds")
Else
	level = LoadMesh("models\level\level2.3ds")
EndIf
ScaleEntity level, 15, 15, 15 
EntityType level, type_backdrop
EntityPickMode level, 2, 1

Global playerbox = CreateCube()
ScaleEntity playerbox, 5, 5, 5
EntityRadius playerbox, 10
EntityType playerbox, type_playerbox
EntityAlpha playerbox, 0
PositionEntity playerbox, 0, 500, 0

Global backdrop = CreateSphere()
EntityFX backdrop, 1

Global texture = LoadTexture("models\level\sunset.jpg")
EntityTexture backdrop, texture
EntityOrder backdrop, 1
ScaleEntity backdrop, 5000, 5000, 5000
FlipMesh backdrop





Global camera = CreateCamera()
RotateEntity camera, 0, 0, 0
CameraRange camera, 1, 100000
light = CreateLight(camera)

Include "gunload.bb"

Collisions type_playerbox, type_backdrop, 2, 2

Global start
Global fps



While Not KeyHit(1)
	fps = 1000/(MilliSecs()-start)
	start = MilliSecs()
	IO()
	functions()
	UpdateWorld
	PositionEntity camera, EntityX(playerbox), EntityY(playerbox)+player_eyeheight, EntityZ(playerbox)
	RenderWorld
	WaitTimer frametimer
	Flip
	Cls
Wend