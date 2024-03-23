; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com



Include "include\FastExt.bb"	; <<<<    Include FastExt.bb file

Graphics3D 800,600,0,2

InitExt					; <<<< 	Обязательно инициализуем после Graphics3D
						;               Initialize library after Graphics3D function


; Создадим 3Д мир для примера
; Create world objects

Global Camera, Light, Sphere, Cylinder, Help : CreateScene


; Переменные для настройки эффекта
; Variables for adjusting the effect

Global PosterizeLevel = 1



; Create objects

;m = CreateSphere(16)
;EntityColor m, 255, 0, 0
;ScaleEntity m, 10, 10, 10
;PositionEntity m, 20, 10, 0
;EntityShininess m, 1
;Outline m

;m = CreateCylinder(32,1)
;EntityColor m, 0, 255, 0
;ScaleEntity m, 10, 10, 10
;PositionEntity m, -20, 10, 0
;Outline m

m = CreateCone(32,1)
EntityColor m, 0, 0, 255
ScaleEntity m, 10, 10, 10
PositionEntity m, 0, 10, 20
;Outline m

;m = CreateCube()
;ScaleEntity m, 10, 10, 10
;PositionEntity m, 0, 10, -20
;Outline m



; Главный цикл
; Main loop
While Not KeyDown(1)
	MouseLook Camera
	AnimateScene
	RenderWorld
	
	; Опрашиваем ввод, меняем настройки
	; Read input keys and change effect variables
		If KeyHit(59) Then Help=1-Help
		If KeyHit(2)>0
			PosterizeLevel = PosterizeLevel+1
			If PosterizeLevel>3 Then PosterizeLevel=0
		EndIf	
	
	If PosterizeLevel<3 Then
	
		; Customize effect
		CustomPostprocessPosterize PosterizeLevel
	
		; Render effect
		RenderPostprocess FE_Posterize			; <<<< 	Рендерим пост-процесс эффекты после команды RenderWorld
											;               Render postprocess effects after RenderWorld function
	EndIf
	
	DrawHelper
	Flip 0
Wend










; разные вспомогательные функции
; auxiliary functions

Function Outline(m, scale# = 1.04)
	m0 = CopyMesh(m, m)
	FlipMesh m0
	EntityFX m0, 1+8
	EntityColor m0, 255, 0, 0
	ScaleMesh m0, scale, scale, scale
End Function



Global DeltaTimeK# = 1.0
Global DeltaTimeOld = MilliSecs()
Function MouseLook(cam)
	Local t=MilliSecs()
	Local dt = t - DeltaTimeOld
	DeltaTimeOld = t
	Local dk# = Float(dt)/16.666
	DeltaTimeK = dk
	s#=0.4*dk
	dx#=(GraphicsWidth()/2-MouseX())*0.2
	dy#=(GraphicsHeight()/2-MouseY())*0.2
	TurnEntity cam,-dy,dx,0
	RotateEntity cam,EntityPitch(cam,1),EntityYaw(cam,1),0,1
	 If KeyDown(17) MoveEntity cam,0,0,s
	 If KeyDown(31) MoveEntity cam,0,0,-s
	 If KeyDown(32) MoveEntity cam,s,0,0 
	 If KeyDown(30) MoveEntity cam,-s,0,0
	y# = EntityY(cam,1)
	If y<20 Then y=20
	PositionEntity cam, EntityX(cam,1),y,EntityZ(cam,1)
	MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
End Function

Function DeltaTime#()
	Return DeltaTimeK
End Function

Global FPSCount = 0
Global FPSCountTemp = 0
Global FPSTime = 0
Function FPS()
	If (MilliSecs()-FPSTime)>=1000 Then
		FPSTime = MilliSecs()
		FPSCount = FPSCountTemp
		FPSCountTemp = 0
	EndIf
	FPSCountTemp = FPSCountTemp + 1
	Return FPSCount
End Function


Function LoadSkyBox( file$ )
	m=CreateMesh()
	;front face
	b=LoadBrush( file$+"_FR.jpg",49 )
	s=CreateSurface( m,b )
	AddVertex s,-1,+1,-1,0,0:AddVertex s,+1,+1,-1,1,0
	AddVertex s,+1,-1,-1,1,1:AddVertex s,-1,-1,-1,0,1
	AddTriangle s,0,1,2:AddTriangle s,0,2,3:
	FreeBrush b
	;right face
	b=LoadBrush( file$+"_LF.jpg",49 )
	s=CreateSurface( m,b )
	AddVertex s,+1,+1,-1,0,0:AddVertex s,+1,+1,+1,1,0
	AddVertex s,+1,-1,+1,1,1:AddVertex s,+1,-1,-1,0,1
	AddTriangle s,0,1,2:AddTriangle s,0,2,3
	FreeBrush b
	;back face
	b=LoadBrush( file$+"_BK.jpg",49 )
	s=CreateSurface( m,b )
	AddVertex s,+1,+1,+1,0,0:AddVertex s,-1,+1,+1,1,0
	AddVertex s,-1,-1,+1,1,1:AddVertex s,+1,-1,+1,0,1
	AddTriangle s,0,1,2:AddTriangle s,0,2,3
	FreeBrush b
	;left face
	b=LoadBrush( file$+"_RT.jpg",49 )
	s=CreateSurface( m,b )
	AddVertex s,-1,+1,+1,0,0:AddVertex s,-1,+1,-1,1,0
	AddVertex s,-1,-1,-1,1,1:AddVertex s,-1,-1,+1,0,1
	AddTriangle s,0,1,2:AddTriangle s,0,2,3
	FreeBrush b
	;top face
	b=LoadBrush( file$+"_UP.jpg",49 )
	s=CreateSurface( m,b )
	AddVertex s,-1,+1,+1,0,1:AddVertex s,+1,+1,+1,0,0
	AddVertex s,+1,+1,-1,1,0:AddVertex s,-1,+1,-1,1,1
	AddTriangle s,0,1,2:AddTriangle s,0,2,3
	FreeBrush b
	;bottom face	
	b=LoadBrush( file$+"_DN.jpg",49 )
	s=CreateSurface( m,b )
	AddVertex s,-1,-1,-1,1,0:AddVertex s,+1,-1,-1,1,1
	AddVertex s,+1,-1,+1,0,1:AddVertex s,-1,-1,+1,0,0
	AddTriangle s,0,1,2:AddTriangle s,0,2,3
	FreeBrush b
	ScaleMesh m,100,100,100
	FlipMesh m
	EntityFX m,1
	Return m
End Function


Function CreateScene()
	SetFont LoadFont("Tahoma",13)
	AmbientLight 128, 128, 128
	
	Camera=CreateCamera()
	ScaleEntity Camera,1,1,1
	CameraRange Camera,1,5000
	
	PositionEntity Camera,0,30,65
	RotateEntity Camera,1,180,0
	
	Sky = LoadSkyBox ( "..\media\sky" )
	ScaleEntity Sky, 10, 10, 10
	EntityOrder sky,1
	
	Light=CreateLight()
	TurnEntity Light,0,180, 0
	
	Center=CreatePivot()
	
	PositionEntity Light,50,80,0
	
	Plane=CreatePlane()
	EntityFX Plane, 1
	EntityColor Plane, 192, 192, 192
	PositionEntity Plane, 0, -4, 0
	ScaleEntity Plane, 20, 20, 20
	EntityTexture Plane, LoadTexture("..\media\panels.jpg")
	
	PointEntity Camera, Plane
	
	MoveMouse GraphicsWidth()/2,GraphicsHeight()/2
End Function

Function AnimateScene()
	Local k# = 0.5 * DeltaTime()
	TurnEntity Light,0,k,0
 	MoveEntity Light,.0,.0,k
End Function

Function DrawHelper()
	Color 0,0,128
	If Help=0
		Text 10,10,FPS()+" fps"
		Text 10,30,"F1 - help"
	Else
		Text 10,10,FPS()+" fps"
		Text 10,30,"F1 - hide help"
		Text 10,50,"W,S,A,D keys for move"
		If PosterizeLevel<3 Then
			Text 10,70,"key 1 - change Posterize level: "+Str(PosterizeLevel)
		Else
			Text 10,70,"key 1 - change Posterize effect: disabled"
		EndIf
	EndIf
End Function