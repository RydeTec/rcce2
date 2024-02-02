; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com



; Пример отражения и преломления водой за 3 рендера (два в текстуры низкого разрешения)
; Water reflection and refraction example per 3 render (2 render to low resolution texture)




Include "include\FastExt.bb"	; <<<<    Include FastExt.bb file


Graphics3D 800,600,0,2


InitExt				; <<<< 	Обязательно инициализуем после Graphics3D
						; Initialize library after Graphics3D function


Global Warning$ = ""
DetectSupport()


Global Camera
Global CameraSprite
CreateCameraAndLight()



Global SkyBox
Global Duck
CreateSceneObjects()



Global TextureSize = 512
Global ReflectTexture
Global RefractAndReflectTexture
CreateRefractAndReflectTextures(TextureSize, TextureSize)



Global WaterPlane
Global WaterClipplane
Global BumpTexture
Global AlphaCubemap
Global ClipPivot
Global ClipPivotUp
CreateWater()



UnderWaterFlag = 0





MoveMouse GraphicsWidth()/2,GraphicsHeight()/2
While Not KeyHit(1)


		; ---- рендерим отражение в текстуру (воду отключим)
		; render reflection to texture (hide water)
		
			CameraFogMode Camera,0
			HideEntity CameraSprite
	
			ShowClipplane WaterClipplane
			AlignClipplane WaterClipplane, ClipPivot
			
			HideEntity WaterPlane
	
			MirrorCamera Camera, WaterPlane								; вспомогательная функция - отражает камеру от плоскости
			SetBuffer TextureBuffer(ReflectTexture)
			CameraViewport Camera, 0,0, TextureWidth(ReflectTexture), TextureHeight(ReflectTexture)
			ScaleEntity Camera,1,Float(GraphicsHeight())/Float(GraphicsWidth()),1		; пропорция камеры должна быть как у экрана! (а наша текстура квадратная)
			RenderWorld
			RestoreCamera Camera										; вспомогательная функция - восстанавливает позицию камеры после отражения
	
	
		
		; ---- рендерим в текстуру мир с водой, на которую натянуто отражение и кубемап-текстура прозрачности (отсекаем то, что выше воды, чтобы не искажалось в воде)
		; render refraction with water (set reflection texture to water)
		
			AlignClipplane WaterClipplane, ClipPivotUp
		
			ShowEntity WaterPlane
			EntityTexture WaterPlane, ReflectTexture, 0, 0			; <<<<<< текстура отражения
			EntityTexture WaterPlane, AlphaCubemap, 0, 1			; <<<<<< текстура прозрачности воды
			If UnderWaterFlag Then
				EntityAlpha WaterPlane,0.95
			Else
				EntityAlpha WaterPlane,0.85
			EndIf
			EntityColor WaterPlane,255,255,255
			
			SetBuffer TextureBuffer(RefractAndReflectTexture)
			CameraViewport Camera, 0,0, TextureWidth(RefractAndReflectTexture), TextureHeight(RefractAndReflectTexture)
			ScaleEntity Camera,1,Float(GraphicsHeight())/Float(GraphicsWidth()),1		; пропорция камеры должна быть как у экрана! (а наша текстура квадратная)
			RenderWorld
			
			
	
		; ---- рендерим мир на экран, на водную поверхность кладем текстуру искажения и текстуру преломления (в ней уже есть и отражение)
		; render world (set refraction texture to water)
	
			If UnderWaterFlag
				ShowEntity CameraSprite
				CameraFogMode Camera,1
				CameraFogRange Camera,0.5,25
				CameraFogColor Camera,20,20,40
			Else
				HideEntity CameraSprite
				CameraFogMode Camera,1
				CameraFogRange Camera,20,250
				CameraFogColor Camera,212,242,244
			EndIf
	
			HideClipplane WaterClipplane
			
			ShowEntity WaterPlane
			EntityTexture WaterPlane, BumpTexture, (BumpTextureFrame Mod 32), 0
			EntityTexture WaterPlane, RefractAndReflectTexture, 0, 1
			EntityAlpha WaterPlane,1
			EntityColor WaterPlane,220,230,240
	
			SetBuffer BackBuffer()
			CameraViewport Camera, 0,0, GraphicsWidth(), GraphicsHeight()
			ScaleEntity Camera,1,1,1		; восстановим пропорцию камеры
			RenderWorld
	



	; ---- управление камерой + проверка под водой-ли камера, если да, то переворачиваем плоскость воды и клип-плейны
	; input management

		MouseLook Camera

		If KeyHit(2) Then PositionEntity Camera, -10.3, 1.3, 34  :  RotateEntity Camera, 0, 52, 0
		If KeyHit(3) Then PositionEntity Camera, 2, 14.5, 12.8  :  RotateEntity Camera, 53, -163, 0
		If KeyHit(4) Then PositionEntity Camera, 0, -2, -4  :  RotateEntity Camera, 0, 0, 0
		If KeyHit(5) Then PositionEntity Camera, 0, 3, 0  :  RotateEntity Camera, 0, 0, 0

		; camera under water?
		If EntityY(Camera)>=0 Then				; Камера под водой?
			UnderWaterFlag = 0
			RotateEntity WaterPlane,0,0,0
			RotateEntity ClipPivot,0,0,0
			RotateEntity ClipPivotUp,0,0,180
			PositionEntity ClipPivotUp, 0, 0.05, 0
		Else
			UnderWaterFlag = 1
			RotateEntity WaterPlane,0,0,180
			RotateEntity ClipPivot,0,0,180
			RotateEntity ClipPivotUp,0,0,0
			PositionEntity ClipPivotUp, 0, -0.05, 0
		EndIf


		
	; ---- обработка анимации и остального управления
	; other input management and texture animation
	
		PositionEntity SkyBox,EntityX(Camera,1),EntityY(Camera,1),EntityZ(Camera,1),1
		MoveEntity Duck,0,0,-0.01   :  TurnEntity Duck,0,0.1,0
	
		If (MilliSecs()-BumpTextureFrameF)>33 Then
			BumpTextureFrame = BumpTextureFrame + 1
			BumpTextureFrameF = MilliSecs()
		EndIf
		
		If KeyHit(28) Then
			If TextureSize=256 Then
				TextureSize=512
			Else
				TextureSize=256
			EndIf
			CreateRefractAndReflectTextures (TextureSize, TextureSize)
		EndIf
		If KeyHit(59) Then HelpFlag=1-HelpFlag
		If KeyHit(60) Then FlipSync=1-FlipSync



	; ---- вывод текста
	; draw text
	
		Text GraphicsWidth()-70,10,"Fps: "+FPS()
		If HelpFlag
			Color 0,0,0
			Text 10,10,"F1 for hide help"
			Text 10,30,"Keys 1,2,3,4 - change position"
			Text 10,50,"Keys W,S,A,D and mouse - flying"
			Text 10,70,"Key Enter - reflection and refraction quality: "+TextureSize+" x "+TextureSize
			Text 10,90,"Key F2 - Flip synchronization: "+FlipSync
			If Len(Warning)>0 Then
				Color 255,0,0
				Text 10,110,Warning
			EndIf	
		Else
			Color 0,0,0
			Text 10,10,"F1 for help"
			If Len(Warning)>0 Then
				Color 255,0,0
				Text 10,30,Warning
			EndIf		
		EndIf
	
	
	Flip FlipSync
Wend

FreeTexture ReflectTexture
FreeTexture RefractAndReflectTexture
End





Function DetectSupport()
	If GfxDriverCapsEx\ClipplanesMax=0 Then Warning = "Warning! Your video-card not support clipplanes. "
	If GfxDriverCapsEx\Bump=0 Then Warning = Warning + "Warning! Your video-card not support EMBM (Bump). "
End Function


Function CreateRefractAndReflectTextures(w,h)
	; ----
	If ReflectTexture Then FreeTexture ReflectTexture
	If RefractAndReflectTexture Then FreeTexture RefractAndReflectTexture
	; ----
	ReflectTexture = CreateTexture (w, h, 1+16+32+256 + FE_RENDER + FE_ZRENDER)		; <<<<	Создадим текстуру для 3Д рендеринга		
	TextureBlend ReflectTexture, FE_PROJECT										; <<<<	Новый бленд  для наложения текстуры как проекции
	PositionTexture ReflectTexture, 0.5, 0.5										; <<< Смещение = в центр 0.5
	ScaleTexture ReflectTexture, 2, -2											; по Y скейл отрицательный, так как отражение зеркально
	; ----
	RefractAndReflectTexture = CreateTexture ( w, h, 1+16+32+256 + FE_RENDER + FE_ZRENDER)	; <<<<	Создадим текстуру для 3Д рендеринга			
	TextureBlend RefractAndReflectTexture, FE_PROJECT									; <<<<	Новый бленд для наложения текстуры как проекции
	PositionTexture RefractAndReflectTexture, 0.5, 0.5									; <<< Смещение = в центр 0.5
	ScaleTexture RefractAndReflectTexture, 2, 2		
	; ----				
End Function


Function CreateWater()
	; ----
	; текстура для искажения отражения (типа вода)
	BumpTexture = LoadAnimTexture ( "..\media\water_anim.jpg", 9, 64, 64, 0, 32 )
	TextureBlend BumpTexture, FE_BUMP									; <<<< 	Новый бленд для бампа 
	ScaleTexture BumpTexture,0.0125,0.0125
	; ----
	; кубемап-текстура плавной прозрачности воды
	AlphaCubemap = LoadTexture("..\media\grad.png",1+2+16+32+128)
	TextureBlend AlphaCubemap, FE_ALPHAMODULATE						; <<<<	Новый бленд для управления прозрачностью
	; ----
	; вот плоскость для воды
	WaterPlane = LoadMesh("..\media\water32.3ds")
	ScaleMesh WaterPlane,2,1,2
	EntityFX WaterPlane,1
	; ----
	; А вот и клипплейн, он будет отсекать все что ниже воды, чтобы отрендерить правильное отражение
	; И все что выше воды чтобы получить правильное преломление
	WaterClipplane = CreateClipplane (  WaterPlane  )					; <<<<< при создании сразу можно выровнять по заданному ентити
	; ----
	; Вспомогательные пивоты для выравнивания клип-плейна, чтобы нам не крутить саму воду каждый раз
	; Первый на уровне воды, второй перевернут и чуть выше воды
	ClipPivot = CreatePivot()
	ClipPivotUp = CreatePivot()  :  RotateEntity ClipPivotUp,0,0,180   :   PositionEntity ClipPivotUp, 0, 0.05, 0
	; ----
End Function



Function CreateCameraAndLight()
	; ----
	SetFont LoadFont("",14)
	Color 0,0,0
	; ----
	Camera=CreateCamera()
	PositionEntity Camera,0,3,0
	CameraRange Camera,0.1,5000
	CameraClsColor Camera,222,252,254
	CameraSprite = CreateSprite(Camera)
	PositionEntity CameraSprite,0,0,0.11
	EntityColor CameraSprite,20,20,40
	EntityAlpha CameraSprite,0.35
	; ----
	Light = CreateLight()
	; ----
End Function




Function CreateSceneObjects()
	; ----
	SkyBox = LoadSkyBox ( "..\media\sky" )
	; ----
	Tower = LoadMesh("..\media\tower.b3d")
	ScaleEntity Tower,0.03,0.03,0.03
	PositionEntity Tower,0,-7,15
	EntityFX Tower,16
	EntityColor Tower,180,180,180
	; ----
	For i=1 To 15
		If i=1 Then
			Box = LoadMesh("..\media\wcrate.3ds")  :  j = Box
		Else
			j=CopyEntity (Box)
		EndIf
		ScaleEntity j,0.025,0.025,0.025
		PositionEntity j,Rnd(-15,15),Rnd(-0.5,0.5),Rnd(-15,15)
		TurnEntity j,Rnd(-15,15),Rnd(-15,15),Rnd(-15,15)
		EntityFX j,16
	Next
	; ----
	Duck = LoadMesh("..\media\RDuck.3ds")
	ScaleEntity Duck,0.5,0.5,0.5
	PositionEntity Duck,5,0,6
	EntityFX Duck,16
	; ----
	LandTex1=LoadTexture("..\media\dgrass.jpg")   :   ScaleTexture LandTex1, 10, 10
	LandTex2=LoadTexture("..\media\lmap.JPG")
	Landscape=LoadTerrain( "..\media\hmap.jpg")
	ScaleEntity Landscape,1,15,1
	PositionEntity Landscape,-2*64,-4,-2*64
	TerrainDetail Landscape, 500, 1
	EntityTexture Landscape, LandTex1, 0, 0
	EntityTexture Landscape, LandTex2, 0, 1
	ScaleTexture LandTex2,TerrainSize(Landscape)/1,TerrainSize(Landscape)/1
	EntityFX Landscape,1+16
	LandscapeDown = CreateCube()
	ScaleEntity LandscapeDown,256,0.1,256
	EntityColor LandscapeDown,80,120,80
	PositionEntity LandscapeDown,0,-4.5,0
	; ----
End Function





; разные вспомогательные, к теме не относятся

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


Global DeltaTimeOld = MilliSecs()
Function MouseLook(cam)
	Local t=MilliSecs()
	Local dt = t - DeltaTimeOld
	DeltaTimeOld = t
	Local dk# = Float(dt)/16.666
	s#=0.1*dk
	dx#=(GraphicsWidth()/2-MouseX())*0.003*dk
	dy#=(GraphicsHeight()/2-MouseY())*0.003*dk
	TurnEntity cam,-dy,dx,0
	RotateEntity cam,EntityPitch(cam,1),EntityYaw(cam,1),0,1
	 If KeyDown(17) MoveEntity cam,0,0,s
	 If KeyDown(31) MoveEntity cam,0,0,-s
	 If KeyDown(32) MoveEntity cam,s,0,0 
	 If KeyDown(30) MoveEntity cam,-s,0,0 
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
	If b=0 b=CreateBrush (0,30,50)
	s=CreateSurface( m,b )
	AddVertex s,-1,-1,-1,1,0:AddVertex s,+1,-1,-1,1,1
	AddVertex s,+1,-1,+1,0,1:AddVertex s,-1,-1,+1,0,0
	AddTriangle s,0,1,2:AddTriangle s,0,2,3
	FreeBrush b
	ScaleMesh m,500,500,500
	FlipMesh m
	EntityFX m,1+8
	EntityOrder m,999
	Return m
End Function