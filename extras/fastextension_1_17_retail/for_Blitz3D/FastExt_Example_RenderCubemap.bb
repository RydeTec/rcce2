; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com


; Пример рендеринга в кубемап текстуры (с помошью библиотеки FastExtension и стандартными функциями блица)
; Example of rendering cubemap textures (with FastExtension library functions and standart Blitz3D functions)


; ВНИМАНИЕ! Закройте все другие приложения перед тестированием, чтобы получить корректное кол-во кадров в секунду!
; ATTENTION! Close other application before testing for correct FPS results!





Include "include\FastExt.bb"					; <<<<    Include FastExt.bb file

Graphics3D 800,600,0,2

InitExt									; <<<< 	Обязательно инициализуем после Graphics3D
										; Initialize library after Graphics3D function




Include "include\RenderCubemapFunctions.bb"			; <<<<   see details in include file

Camera=CreateCamera()   :   PositionEntity Camera,0,0,-3   :   CameraRange Camera,0.1,5000   :   SetFont LoadFont("Tahoma",13)
Light=CreateLight()   :   TurnEntity Light,45,0,0   :   CreateBoxes() 




; create cubemap textures for render (with Z-Buffer)
RenderCubemap_Texture0 = CreateTexture( 256, 256, 1 + 128 + 256 + FE_RENDER + FE_ZRENDER )			; <<<<   cubemap for rendering (with specular rendering mode)
RenderCubemap_Texture1 = CreateTexture( 256, 256, 1 + 128 + 256 + FE_RENDER + FE_ZRENDER )			; <<<<   other cubemap for rendering for testing
SetCubeMode RenderCubemap_Texture1,2														;  (with diffuse rendering mode)

; place textures to objects
Entity0 = CreateSphere()
PositionEntity Entity0,-1.5,0,0
EntityTexture Entity0, RenderCubemap_Texture0

Entity1 = CreateSphere()
PositionEntity Entity1,1.5,0,0
EntityTexture Entity1, RenderCubemap_Texture1




Sync = 0
Mode = 0

While Not KeyHit(1)
	MouseLook Camera
	If KeyHit(28) Then Sync=1-Sync
	If KeyHit(57) Then Mode=1-Mode


	If Mode=0 Then
		; render to cubemap
		RenderToCubemap  Entity0, Camera, RenderCubemap_Texture0
		RenderToCubemap  Entity1, Camera, RenderCubemap_Texture1
	Else
		; render to BackBuffer and copy to cubemap
		RenderCubemapCopyRect  Entity0, Camera, RenderCubemap_Texture0
		RenderCubemapCopyRect  Entity1, Camera, RenderCubemap_Texture1
	EndIf
	

		; testing CopyRectStretch function with cubemap texture
		; copy graphics data from BackBuffer to cubemap face
		SetCubeFace RenderCubemap_Texture1,3
		CopyRectStretch GraphicsWidth()-200,GraphicsHeight()-200,200,200, TextureWidth(RenderCubemap_Texture1)/4, TextureHeight(RenderCubemap_Texture1)/4, TextureWidth(RenderCubemap_Texture1)/2, TextureHeight(RenderCubemap_Texture1)/2, BackBuffer(), TextureBuffer(RenderCubemap_Texture1)

	
	RenderWorld



	Text 10,10,"Fps: "+FPS()
	Text 10,30,"VSync (key Enter): "+Sync
	If Mode=0 Then
		Text 10,50,"Mode (key Space): Render To Cubemap"
	Else
		Text 10,50,"Mode (key Space): Render To BackBuffer And CopyRect"
	EndIf
	Flip Sync
Wend
End








; вспомогательные функции

Global DeltaTimeOld
Global MouseLookFirst=0
Function MouseLook(cam)
	If MouseLookFirst=0 Then
		MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
		DeltaTimeOld = MilliSecs()
		MouseLookFirst=1
	EndIf
	Local t=MilliSecs()
	Local dt = t - DeltaTimeOld
	DeltaTimeOld = t
	Local dk# = Float(dt)/16.666
	s#=0.1*dk
	dx#=(GraphicsWidth()/2-MouseX())*0.004*dk
	dy#=(GraphicsHeight()/2-MouseY())*0.004*dk
	TurnEntity cam,-dy,dx,0
	RotateEntity cam,EntityPitch(cam,1),EntityYaw(cam,1),0,1
	 If KeyDown(17) MoveEntity cam,0,0,s
	 If KeyDown(31) MoveEntity cam,0,0,-s
	 If KeyDown(32) MoveEntity cam,s,0,0 
	 If KeyDown(30) MoveEntity cam,-s,0,0 
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

Function CreateBoxes(par=0)
	For i=1 To 50
		cub=CreateCube(par)
		EntityColor cub,Rand(128,255),Rand(128,255),Rand(128,255)
		PositionEntity cub,Rnd(-10,10),Rnd(-10,10),Rnd(-10,10)
		ScaleEntity cub,Rnd(0.1,0.3),Rnd(0.1,0.3),Rnd(0.1,0.3)
		TurnEntity cub,Rnd(0,90),Rnd(0,90),Rnd(0,90)
	Next
End Function