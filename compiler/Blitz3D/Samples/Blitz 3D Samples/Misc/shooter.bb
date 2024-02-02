;Simple 3D shooter
;By HunterSD[huntersd@iprimus.com.au]
;Wednesday 8th October, 2001
;This is the basics for a simple top down game. Toy with it and do whatever you want with it,
;use it without my permission, I really dont care :)

Graphics3D 800,600
SetBuffer BackBuffer()

fpstimer = CreateTimer(100)

Type enemies
	Field entity
End Type

Type bullets
	Field entity, tobedeleted
End Type

Global camera = CreateCamera()
PositionEntity camera, 0, 100, 0
temp = CreateCube()
PointEntity camera, temp
FreeEntity temp

Global playership = CreateCube()
EntityColor playership, 255, 0, 0
ScaleEntity playership, 3, 1, 5
EntityFX playership, 4

For a = 1 To 50
	createenemy()
Next


While Not KeyDown(1)
	If KeyDown(203)
		MoveEntity playership, -2, 0, 0
	EndIf
	If KeyDown(205)
		MoveEntity playership, 2, 0, 0
	EndIf
	If KeyDown(200)
		MoveEntity playership, 0, 0, 4
	EndIf
	If KeyDown(208)
		MoveEntity playership, 0, 0, -2
	EndIf
	
	
	count = 0
	For e.enemies = Each enemies
		MoveEntity e\entity, 0, 0, -1
		If EntityZ(e\entity) < -100
			FreeEntity e\entity
			Delete e
			createenemy()
		EndIf
		count = count + 1
	Next
	
	If count = 0
		endscreen()
	EndIf
	
	If KeyHit(57)
		shootbullet()
	EndIf
	
	updatebullets()
	
	UpdateWorld
	RenderWorld
	Flip
	Cls
	WaitTimer(fpstimer)
Wend


Function shootbullet()
	b.bullets = New bullets
	b\entity = CreateCube()
	PositionEntity b\entity, EntityX(playership), EntityY(playership), EntityZ(playership)
	ScaleEntity b\entity, 1, 0.2, 3
	EntityColor b\entity, 255, 255, 0
End Function

Function updatebullets()
	For b.bullets = Each bullets
		MoveEntity b\entity, 0, 0, 1
		For e.enemies = Each enemies
			If MeshesIntersect(b\entity, e\entity)
				FreeEntity e\entity
				Delete e
				b\tobedeleted = 1
			EndIf
		Next
		If b\tobedeleted = 1
			FreeEntity b\entity
			Delete b
		ElseIf EntityVisible(camera, b\entity) <> 1
			FreeEntity b\entity
			Delete b
		EndIf
	Next
End Function


Function createenemy()
	e.enemies = New enemies
	e\entity = CreateCube()
	ScaleEntity e\entity, 2, 1, 3.5
	PositionEntity e\entity, Rand(200)-100, 0, Rand(500)
End Function


Function endscreen()
	Cls
	Flip
	Text 1, 1, "Congradulations: You beat the easiest shooter in history!!!"
	WaitKey()
	End
End Function