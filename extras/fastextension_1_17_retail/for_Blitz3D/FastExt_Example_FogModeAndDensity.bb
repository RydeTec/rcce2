; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com


; Пример новых режимов тумана (функции FogMode и FogDensity)
; New FOG modes example ( FogMode and FogDensity functions)



Include "include\FastExt.bb"	; <<<<    Include FastExt.bb file

Graphics3D 800,600,0,2

InitExt					; <<<< 	Обязательно инициализуем после Graphics3D
						;		Initialize library after Graphics3D function



testTex0 = LoadTexture("..\media\anisotropy.png")   :   ScaleTexture testTex0,1.5,1.5
For i=0 To 9   :   cube = CreateCube()   :   ScaleEntity cube,1,10,1 : PositionEntity cube, 6,1,4+i*10   :   EntityTexture cube, testTex0   :   EntityFX cube,1   :   Next
For i=0 To 9   :   cube = CreateCube()   :   ScaleEntity cube,1,10,1 : PositionEntity cube, -6,1,4+i*10   :   EntityTexture cube, testTex0   :   EntityFX cube,1   :   Next
plane = CreatePlane()   :   EntityTexture plane, testTex0   :   EntityFX plane,1
SetFont LoadFont("Tahoma",13)


camera = CreateCamera() 
PositionEntity  camera, 0, 4, -10
CameraFogMode camera,1				; enable Fog for camera
CameraFogRange camera,10,50			; set Range (actual for FogMode=1 only)
CameraFogColor camera,255,255,255



mode = 1	
density# = 0.02


MoveMouse GraphicsWidth()/2,GraphicsHeight()/2
While Not KeyDown(1)

	MouseLook camera

	If KeyHit(2) Then mode = 1					; 1 - standart Blitz3D fog
	If KeyHit(3) Then mode = 2					; 2 - new table fog (LOG)
	If KeyHit(4) Then mode = 3					; 3 - new table fog (LOG2X)
	If KeyHit(205) Then density = density + 0.005
	If KeyHit(203) Then density = density - 0.005

	FogMode mode							; set FogMode
	FogDensity density#						; set FogDensity (actual for FogMode=2 and  FogMode=3)


	RenderWorld
	Text 10,10, "FogMode: "+mode
	Text 10,30, "FogDensity: "+density
	Text 10,90, "Keys Left & Right - change FogDensity value"
	Text 10,110, "Keys 1,2,3 - change FogMode"
	Flip
Wend









Function MouseLook(cam)
	s#=0.1
	dx#=(GraphicsWidth()/2-MouseX())*0.002
	dy#=(GraphicsHeight()/2-MouseY())*0.002
	TurnEntity cam,-dy,dx,0
	RotateEntity cam,EntityPitch(cam,1),EntityYaw(cam,1),0,1
	 If KeyDown(17) MoveEntity cam,0,0,s
	 If KeyDown(31) MoveEntity cam,0,0,-s
	 If KeyDown(32) MoveEntity cam,s,0,0 
	 If KeyDown(30) MoveEntity cam,-s,0,0 
End Function

Function Musor()
	For i=1 To 50
		cub=CreateCube()
		EntityColor cub,Rand(128,255),Rand(128,255),Rand(128,255)
		EntityAlpha cub,Rnd(0.3,1.0)
		PositionEntity cub,Rnd(-10,10),Rnd(-10,10),Rnd(5,15)
		ScaleEntity cub,Rnd(0.3,0.5),Rnd(0.3,0.5),Rnd(0.3,0.5)
		TurnEntity cub,Rnd(0,90),Rnd(0,90),Rnd(0,90)
		EntityFX cub,32
	Next
End Function