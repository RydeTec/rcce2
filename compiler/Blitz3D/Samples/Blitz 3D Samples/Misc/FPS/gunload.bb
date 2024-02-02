Type gun
	Field model, muzzle, class, timesincelastshot
	Field selected
	Field crosshair
	Field decal
End Type

g.gun = New gun
g\model=LoadMesh("models\weapons\pistol\pistol.3ds",camera)
g\muzzle=LoadMesh("models\weapons\pistol\muzzle.3ds",g\model)
EntityAlpha g\muzzle, 0
;RotateEntity g\model,-180,0,180
;PositionEntity g\muzzle,3,-2,4

ScaleEntity g\model, 0.01, 0.01, 0.01
g\class = 1
g\selected = 1
g\crosshair = LoadSprite("artwork\crosshair\pistol\crosshair.bmp", 0, camera)
PositionEntity g\crosshair, 0, 0, 20
EntityOrder g\crosshair, -1
g\decal = LoadSprite("artwork\decal\decal1.bmp")
EntityAlpha g\decal, 0
SpriteViewMode g\decal, 2