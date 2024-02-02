; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com




Include "include\FastExt.bb"		; <<<<    Include FastExt.bb file



Graphics3D 800,600,0,2



InitExt					; <<<< 	Обязательно инициализуем после Graphics3D
						; Initialize library after Graphics3D function



CreateBoxes()
Camera=CreateCamera()   :   PositionEntity Camera,0,0,-4
Light=CreateLight()   :   TurnEntity Light, 45,0,0


RenderEntityLight 0, Light						; <<<<	 set one light source for RenderEntity function


Entity = CreateCube()
HideEntity Entity								; hide entity (cut entity from RenderWorld)


MoveMouse GraphicsWidth()/2,GraphicsHeight()/2
While Not KeyHit(1)
	MouseLook (Camera)
	TurnEntity Entity,0.5,0.7,0
	
	
	RenderWorld
		
	
	; first example:
	; simple render single entity (with childrens if present)
	RenderEntity Entity, Camera



		; next example:
		; use code below for drawing weapon in FPS ( first person shooter ) games
		;	CameraClsMode Camera,0,1						; clear Z-buffer values, don't clear color values
		;	RenderEntity Entity,Camera,2						; render weapon entity and use ClsMode from Camera
		;	CameraClsMode Camera,1,1						; restore ClsMode for Camera
		
		
		; Warning! All alpha-entities and entities with EntityFX=32 (force alpha-blending flag) will be behind a RenderEntity object

	
	Flip
Wend
End





; вспомогательные функции

Function MouseLook(cam)
	s#=0.1
	dx#=(GraphicsWidth()/2-MouseX())*0.005
	dy#=(GraphicsHeight()/2-MouseY())*0.005
	TurnEntity cam,-dy,dx,0
	RotateEntity cam,EntityPitch(cam,1),EntityYaw(cam,1),0,1
	 If KeyDown(17) MoveEntity cam,0,0,s
	 If KeyDown(31) MoveEntity cam,0,0,-s
	 If KeyDown(32) MoveEntity cam,s,0,0 
	 If KeyDown(30) MoveEntity cam,-s,0,0 
End Function

Function CreateBoxes(par=0)
	For i=1 To 50
		cub=CreateCube(par)
		EntityColor cub,Rand(128,255),Rand(128,255),Rand(128,255)
		PositionEntity cub,Rnd(-10,10),Rnd(-10,10),Rnd(-10,10)
		ScaleEntity cub,Rnd(0.1,0.3),Rnd(0.1,0.3),Rnd(0.1,0.3)
		TurnEntity cub,Rnd(0,90),Rnd(0,90),Rnd(0,90)
	Next
End Function