; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com



Include "include\FastExt.bb"	; <<<<    Include FastExt.bb file

Graphics3D 800,600,0,2

InitExt					; <<<< 	Обязательно инициализуем после Graphics3D
						;               Initialize library after Graphics3D function


; Создадим 3Д мир для примера
; Create world objects
	Global Camera, Light, Sphere, Cylinder, Help : CreateScene


AlphaTexture = LoadTexture("..\media\fe.png",1+2)


; Переменные для настройки эффекта
; Variables for adjusting the effect

	Global Preset% = 0

	Dim BlurAlpha#(10)
	Dim BlurOriginX#(10)
	Dim BlurOriginY#(10)
	Dim BlurHandleX#(10)
	Dim BlurHandleY#(10)
	Dim BlurScaleX#(10)
	Dim BlurScaleY#(10)
	Dim BlurAngle#(10)
	Dim BlurBlend%(10)
	Dim BlurRed%(10)
	Dim BlurGreen%(10)
	Dim BlurBlue%(10)
	Global BlurAlphaTexture% = 0

	; presets data
	Data 0.95,  0, 0, 0.5,  0.5,  100.0,  100.0,  0,     0,  255,  255,  255
	Data 0.85,  0, 0, 0.5,  1.0,  100.0,  101.0,  0,     0,  255,  0,      0
	Data 0.93,  0, 0, 0.5,  0.5,  100.5,  100.5,  0.5,  0,  255,  255,  240
	Data 0.95,  0, 0, 0.5,  0.5,  101.0,  101.0,  0,     0,  255,  255,  255
	Data 0.95,  0, 0, 0.5,  1.0,  99.50,  99.50,  0,     0,  252,  255,  255
	Data 0.95,  0, 0, 0.5,  0.5,  100.5,  100.0,  0,     0,  255,  255,  255
	Data 0.90,  0, 0, 0.5,  0.5,  100.0,  101.5,  0,     0,  255,  255,  255
	Data 0.90,  0.003, 0, 0.5,  0.5,  100.0,  100.0,  0,     0,  255,  255,  255
	Data 0.90,  0.003, 0.003, 0.5,  0.5,  100.0,  100.0,  0,     0,  255,  255,  255

	; read presets to arrays
	For i=0 To 8
		Read BlurAlpha(i)
		Read BlurOriginX(i)
		Read BlurOriginY(i)
		Read BlurHandleX(i)
		Read BlurHandleY(i)
		Read BlurScaleX(i)
		Read BlurScaleY(i)
		Read BlurAngle(i)
		Read BlurBlend(i)
		Read BlurRed(i)
		Read BlurGreen(i)
		Read BlurBlue(i)
	Next



; Главный цикл
; Main loop
While Not KeyDown(1)
	MouseLook Camera
	AnimateScene
	RenderWorld

	
	; Опрашиваем ввод, меняем настройки
	; Read input keys and change effect variables
		If KeyHit(59) Then Help=1-Help
		If KeyHit(2) Then Preset=0
		If KeyHit(3) Then Preset=1
		If KeyHit(4) Then Preset=2
		If KeyHit(5) Then Preset=3
		If KeyHit(6) Then Preset=4
		If KeyHit(7) Then Preset=5
		If KeyHit(8) Then Preset=6
		If KeyHit(9) Then Preset=7
		If KeyHit(10) Then Preset=8
		If KeyHit(57) Then BlurAlphaTexture = AlphaTexture - BlurAlphaTexture
	
	
	; Customize effect
		CustomPostprocessBlurMotion BlurAlpha(Preset), BlurOriginX(Preset), BlurOriginY(Preset), BlurHandleX(Preset), BlurHandleY(Preset), BlurScaleX(Preset), BlurScaleY(Preset), BlurAngle(Preset), BlurBlend(Preset), BlurRed(Preset), BlurGreen(Preset), BlurBlue(Preset), BlurAlphaTexture
	
	
	; Render postprocess effects
		RenderPostprocess FE_BlurMotion		; <<<< 	Рендерим пост-процесс эффекты после команды RenderWorld
										;               Render postprocess effects after RenderWorld function
	
	DrawHelper
	Flip 0
Wend










; разные вспомогательные функции
; auxiliary functions

Function MouseLook ( ent, mov# = 0.3 )
	mxspd#=MouseXSpeed()*0.25
	myspd#=MouseYSpeed()*0.25	
	campitch#=EntityPitch(ent)+myspd#
	If campitch#<-65 Then campitch#=-65
	If campitch#>65 Then campitch#=65
	RotateEntity ent,campitch#,EntityYaw(ent)-mxspd#,EntityRoll(ent)
	If KeyDown(17) MoveEntity ent,0,0,mov
	If KeyDown(31) MoveEntity ent,0,0,-mov
	If KeyDown(32) MoveEntity ent,mov,0,0
	If KeyDown(30) MoveEntity ent,-mov,0,0
	MoveMouse GraphicsWidth()/2,GraphicsHeight()/2
End Function

Function Musor()
	For i=1 To 50
		cub=CreateCube()
		EntityColor cub,Rand(128,255),Rand(128,255),Rand(128,255)
		;EntityAlpha cub,Rnd(0.3,1.0)
		PositionEntity cub,Rnd(-10,10),Rnd(-10,10),Rnd(5,15)
		ScaleEntity cub,Rnd(0.3,0.5),Rnd(0.3,0.5),Rnd(0.3,0.5)
		;TurnEntity cub,Rnd(0,90),Rnd(0,90),Rnd(0,90)
		;EntityFX cub,32
	Next
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

Function Reflect(Entity,Camera,RTexture)
	Local fxCamera = CreateCamera()
	HideEntity entity
	WaterMapSize=TextureWidth(RTexture)
	CameraProjMode fxCamera,1
	CameraProjMode Camera,0
	CameraViewport fxCamera,0,0,WaterMapSize,WaterMapSize
	PositionEntity fxCamera,EntityX(Entity,1), EntityY(Entity,1) ,EntityZ(Entity,1),1; EntityY(Entity)
	; do left view
	SetCubeFace RTexture,0
	RotateEntity fxCamera,0,90,0
	RenderWorld
	CopyRect 0,0,WaterMapSize,WaterMapSize,0,0,BackBuffer(),TextureBuffer(RTexture)
	; do forward view
	SetCubeFace RTexture,1
	RotateEntity fxCamera,0,0,0
	RenderWorld
	CopyRect 0,0,WaterMapSize,WaterMapSize,0,0,BackBuffer(),TextureBuffer(RTexture)
	; do right view	
	SetCubeFace RTexture,2
	RotateEntity fxCamera,0,-90,0
	RenderWorld
	CopyRect 0,0,WaterMapSize,WaterMapSize,0,0,BackBuffer(),TextureBuffer(RTexture)
	; do backward view
	SetCubeFace RTexture,3
	RotateEntity fxCamera,0,180,0
	RenderWorld
	CopyRect 0,0,WaterMapSize,WaterMapSize,0,0,BackBuffer(),TextureBuffer(RTexture)
	; do up view
	SetCubeFace RTexture,4
	RotateEntity fxCamera,-90,0,0
	RenderWorld
	CopyRect 0,0,WaterMapSize,WaterMapSize,0,0,BackBuffer(),TextureBuffer(RTexture)		
	SetCubeFace RTexture,5
	RotateEntity fxCamera,90,0,0
	RenderWorld
	CopyRect 0,0,WaterMapSize,WaterMapSize,0,0,BackBuffer(),TextureBuffer(RTexture)	
	db=db+1
	CameraProjMode fxCamera,0
	CameraProjMode Camera,1
	ShowEntity entity
End Function




Function CreateScene()
	SetFont LoadFont("Tahoma",13)
	AmbientLight 50,50,70
	
	Camera=CreateCamera()
	ScaleEntity Camera,1,1,1
	CameraRange Camera,1,10000
	
	
	PositionEntity Camera,0,40,95
	RotateEntity Camera,1,180,0
	
	Sky = LoadSkyBox ( "..\media\sky" )
	ScaleEntity Sky, 10, 10, 10
	EntityOrder sky,1
	
	Light=CreateLight(2)
	
	Center=CreatePivot()
	
	PositionEntity Light,50,80,0
	
	ReflectTex = CreateTexture(128,128,  128+256)
	
	tex1=LoadTexture("..\media\wall.jpg")
	ScaleTexture tex1,.3,.3
	tex2=LoadTexture("..\media\wood.jpg")
	
	modelR=LoadMesh( "..\media\teapot.x" )
	PositionEntity modelR,0,35,0
	ScaleEntity modelR, 15, 15, 15
	EntityTexture modelR, ReflectTex
	EntityShininess modelR,1
	
	Sphere=CreateSphere(16)
	PositionEntity Sphere,40,40, 0
	ScaleEntity Sphere,10,10,10
	EntityTexture Sphere, ReflectTex
	EntityShininess Sphere,1
	
	Cylinder=CreateCylinder(12,1)
	PositionEntity Cylinder,30,10,20
	ScaleEntity Cylinder,5,15,5
	RotateEntity Cylinder,91,0,0
	EntityTexture Cylinder,tex2
	
	
	
	cube2=LoadMesh("..\media\cube.3DS"); create receiver
	ScaleEntity cube2,180,2,180
	EntityTexture cube2,tex1
	
	cube3=CreateCylinder(16)
	ScaleMesh cube3,3,15,3
	sp = CreateSphere(12)
	ScaleMesh sp ,5,5,5
	PositionMesh sp,0,20,0
	AddMesh sp, cube3
	FreeEntity sp
	
	
	PositionEntity cube3,60,10,60
	EntityTexture cube3,tex2
		For i=1 To 3
			cuben = CopyEntity( cube3 )
			PositionEntity cuben,60,10,60 - (30*i)
		Next
		For i=1 To 3
			cuben = CopyEntity( cube3 )
			PositionEntity cuben,-60,10,60 - (30*i)
		Next
		For i=1 To 3
			cuben = CopyEntity( cube3 )
			PositionEntity cuben,60 - (30*i),10,60 
		Next
		For i=1 To 3
			cuben = CopyEntity( cube3 )
			PositionEntity cuben,60 - (30*i),10,-60
		Next
	
	cube4=CopyEntity( cube3 )
	PositionEntity cube4,-60,10,60
	
	cube5=CopyEntity( cube3 )
	PositionEntity cube5,60,10,-60
	
	cube6=CopyEntity( cube3 )
	PositionEntity cube6,-60,10,-60
	
	Reflect( ModelR,Camera,ReflectTex)
	MoveMouse GraphicsWidth()/2,GraphicsHeight()/2
End Function

Function AnimateScene()
	TurnEntity Light,0,.5,0
 	MoveEntity Light,.0,.0,.5
 	TurnEntity Sphere,0,.3,0
 	MoveEntity Sphere,.0,.0,.2
 	TurnEntity Cylinder,0,0,1
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
		Text 10,70,"keys 1...9 - select preset for effect: "+Str(Preset)
		Text 10,90,"key SPACE - enable/disable Blur alpha-texture: $"+Hex(BlurAlphaTexture)
	EndIf
End Function