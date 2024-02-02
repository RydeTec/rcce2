; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com



; ѕростой пример отражени€ водой за 2 рендера сцены (один в текстуру низкого разрешени€)
; Simple example of water reflection per 2 render (one render to low resolution texture)




Include "include\FastExt.bb"	; <<<<    Include FastExt.bb file


Graphics3D 800,600,0,2


InitExt				; <<<< 	ќб€зательно инициализуем после Graphics3D
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
CreateReflectTexture(TextureSize, TextureSize)



Global WaterPlane
Global WaterClipplane
Global BumpTexture
CreateWater()



UnderWaterFlag = 0



MoveMouse GraphicsWidth()/2,GraphicsHeight()/2
While Not KeyHit(1)



		; ---- рендерим отражение в текстуру (воду отключим)
		; render reflections to texture (hide water)
		
			CameraFogMode Camera,0	
			HideEntity CameraSprite
				
			ShowClipplane WaterClipplane
			HideEntity WaterPlane
	
			MirrorCamera Camera, WaterPlane								; вспомогательна€ функци€ - отражает камеру от плоскости
			
			SetBuffer TextureBuffer(ReflectTexture)
			CameraViewport Camera, 0,0, TextureWidth(ReflectTexture), TextureHeight(ReflectTexture)
			ScaleEntity Camera,1,Float(GraphicsHeight())/Float(GraphicsWidth()),1		; пропорци€ камеры должна быть как у экрана! (а наша текстура квадратна€)
			RenderWorld
			
			RestoreCamera Camera										; вспомогательна€ функци€ - восстанавливает позицию камеры после отражени€
	
			
			
	
		; ---- рендерим мир на экран, на водную поверхность кладем текстуру искажени€ и текстуру отражени€
		; render world to screen (back buffer)
		; show water and set reflection texture to water-plane

			If UnderWaterFlag
				ShowEntity CameraSprite
				CameraFogMode Camera,1
				CameraFogRange Camera,0.5,25
				CameraFogColor Camera,20,20,40
				EntityAlpha WaterPlane,0.8
			Else
				HideEntity CameraSprite
				CameraFogMode Camera,1
				CameraFogRange Camera,20,250
				CameraFogColor Camera,212,242,244
				EntityAlpha WaterPlane,0.65
			EndIf
	
			HideClipplane WaterClipplane
			ShowEntity WaterPlane
			
			EntityTexture WaterPlane, BumpTexture, (BumpTextureFrame Mod 32), 0
			EntityTexture WaterPlane, ReflectTexture, 0, 1
	
			SetBuffer BackBuffer()
			CameraViewport Camera, 0,0, GraphicsWidth(), GraphicsHeight()
			ScaleEntity Camera,1,1,1									; восстановим пропорцию камеры
			RenderWorld
	



	; ---- управление камерой + проверка под водой-ли камера, если да, то переворачиваем плоскость воды и клип-плейны
	; input management

		MouseLook Camera

		If KeyHit(2) Then PositionEntity Camera, -10.3, 1.3, 34  :  RotateEntity Camera, 0, 52, 0
		If KeyHit(3) Then PositionEntity Camera, 2, 14.5, 12.8  :  RotateEntity Camera, 53, -163, 0
		If KeyHit(4) Then PositionEntity Camera, 0, -2, -4  :  RotateEntity Camera, 0, 0, 0
		If KeyHit(5) Then PositionEntity Camera, 0, 3, 0  :  RotateEntity Camera, 0, 0, 0

		; camera under water-plane?
		If EntityY(Camera)>=0 Then				;  амера под водой?
			UnderWaterFlag = 0
			RotateEntity WaterPlane,0,0,0
		Else
			UnderWaterFlag = 1
			RotateEntity WaterPlane,0,0,180
		EndIf
		AlignClipplane WaterClipplane, WaterPlane


		
	; ---- обработка анимации и остального управлени€
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
			CreateReflectTexture (TextureSize, TextureSize)
		EndIf
		If KeyHit(59) Then HelpFlag=1-HelpFlag
		If KeyHit(60) Then FlipSync=1-FlipSync



	; ---- вывод текста
	; text draw
	
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
End





Function DetectSupport()
	If GfxDriverCapsEx\ClipplanesMax=0 Then Warning = "Warning! Your video-card not support clipplanes. "
	If GfxDriverCapsEx\Bump=0 Then Warning = Warning + "Warning! Your video-card not support EMBM (Bump). "
End Function


Function CreateReflectTexture(w,h)
	; ----
	If ReflectTexture Then FreeTexture ReflectTexture
	; ----
	ReflectTexture = CreateTexture (w, h, 1+16+32+256 + FE_RENDER + FE_ZRENDER)		; <<<<	—оздадим текстуру дл€ 3ƒ рендеринга	
																		; create texture for render reflections	
	TextureBlend ReflectTexture, FE_PROJECT										; <<<<	Ќовый бленд  дл€ наложени€ текстуры как проекции
																		; new blend for 2D project texture
	PositionTexture ReflectTexture, 0.5, 0.5										; <<< —мещение = в центр 0.5
																		; set 2D projection texture to center of screen
	ScaleTexture ReflectTexture, 2, -2											; по Y скейл отрицательный, так как отражение зеркально
																		; negative scale on Y axis (since reflection mirror)
	; ----				
End Function


Function CreateWater()
	; ----
	; текстура дл€ искажени€ отражени€ (типа вода)
	; create distortion (bump) texture
	BumpTexture = LoadAnimTexture ( "..\media\water_anim.jpg", 9, 64, 64, 0, 32 )
	TextureBlend BumpTexture, FE_BUMP									; <<<< 	Ќовый бленд дл€ бампа 
	ScaleTexture BumpTexture,0.0125,0.0125
	; ----
	; вот плоскость дл€ воды
	; create water plane
	WaterPlane = LoadMesh("..\media\water32.3ds")
	ScaleMesh WaterPlane,2,1,2
	EntityFX WaterPlane,1
	EntityColor WaterPlane,220,230,240
	; ----
	; ј вот и клипплейн, он будет отсекать все что ниже воды, чтобы отрендерить правильное отражение
	; create clipplane for cut off under water	
	WaterClipplane = CreateClipplane (  WaterPlane  )					; <<<<< при создании сразу можно выровн€ть по заданному ентити
															; align clipplane by water plane
	AlignClipplane WaterClipplane, WaterPlane
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
	; ----
	CameraSprite = CreateSprite(Camera)
	PositionEntity CameraSprite,0,0,0.11
	EntityColor CameraSprite,20,20,40
	EntityAlpha CameraSprite,0.35
	; ----
	Light = CreateLight()
	; ----
End Function



; other scene entities

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





; разные вспомогательные, к теме не относ€тс€

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