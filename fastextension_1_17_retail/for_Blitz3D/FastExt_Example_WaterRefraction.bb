; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com


; Water refraction per 2 render (one render to low resolution texture)
; Пример преломления водой за 2 рендера (один в текстуру низкого разрешения)




Include "include\FastExt.bb"


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
Global RefractTexture
CreateRefractTextures(TextureSize, TextureSize)



Global WaterPlane
Global WaterClipplane
Global BumpTexture
;Global FoamTexture
Global ClipPivotUp
CreateWater()



UnderWaterFlag = 0



Global DeltaTimeK# = 1.0
;FoamTextureDX#=0
MoveMouse GraphicsWidth()/2,GraphicsHeight()/2
While Not KeyHit(1)


		
		; ---- рендерим в текстуру мир с водой, на которую положена полупрозрачная пена, так интереснее смотрится (отсекаем то, что выше воды, чтобы не искажалось в воде)
		; render world with water plane in texture (refractions)

			CameraFogMode Camera,0
			HideEntity CameraSprite
		
			ShowClipplane WaterClipplane
			AlignClipplane WaterClipplane, ClipPivotUp
		
			EntityAlpha WaterPlane,0.25
;			EntityTexture WaterPlane, FoamTexture
			
			SetBuffer TextureBuffer(RefractTexture)
			CameraViewport Camera, 0,0, TextureWidth(RefractTexture), TextureHeight(RefractTexture)
			ScaleEntity Camera,1,Float(GraphicsHeight())/Float(GraphicsWidth()),1		; пропорция камеры должна быть как у экрана! (а наша текстура квадратная)
			RenderWorld
			
			
	
		; ---- рендерим мир на экран, на водную поверхность кладем текстуру искажения и текстуру преломления
		; render world in backBuffer (set refractions texture to water plane)
			
			If UnderWaterFlag
				ShowEntity CameraSprite
				CameraFogMode Camera,1
				CameraFogRange Camera,0.5,25
				CameraFogColor Camera,20,20,40
			Else
				HideEntity CameraSprite
				CameraFogMode Camera,1
				CameraFogRange Camera,20,250
				CameraFogColor Camera,190,200,220
			EndIf
	
			HideClipplane WaterClipplane
			
			EntityTexture WaterPlane, BumpTexture, (BumpTextureFrame Mod 32), 0
			EntityTexture WaterPlane, RefractTexture, 0, 1
			EntityAlpha WaterPlane,1
			EntityColor WaterPlane,25,30,35
	
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

	; camera under water-plane?
		If EntityY(Camera)>=0 Then				; Камера под водой?
			UnderWaterFlag = 0
			RotateEntity WaterPlane,0,0,0
			RotateEntity ClipPivotUp,0,0,180
			PositionEntity ClipPivotUp, 0, 0.05, 0
		Else
			UnderWaterFlag = 1
			RotateEntity WaterPlane,0,0,180
			RotateEntity ClipPivotUp,0,0,0
			PositionEntity ClipPivotUp, 0, -0.05, 0
		EndIf


		
	; ---- обработка анимации и остального управления
	; other input and texture animation
	
		PositionEntity SkyBox,EntityX(Camera,1),EntityY(Camera,1),EntityZ(Camera,1),1
		MoveEntity Duck,0,0,-0.01   :  TurnEntity Duck,0,0.1,0
		
		;FoamTextureDX=FoamTextureDX+0.0015*DeltaTimeK
		;PositionTexture FoamTexture,FoamTextureDX,FoamTextureDX
	
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
			CreateRefractTextures (TextureSize, TextureSize)
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

FreeTexture RefractTexture
End





Function DetectSupport()
	If GfxDriverCapsEx\ClipplanesMax=0 Then Warning = "Warning! Your video-card not support clipplanes. "
	If GfxDriverCapsEx\Bump=0 Then Warning = Warning + "Warning! Your video-card not support EMBM (Bump). "
End Function


Function CreateRefractTextures(w,h)
	; ----
	If RefractTexture Then FreeTexture RefractTexture
	; ----
	RefractTexture = CreateTexture ( w, h, 1+16+32+256 + FE_RENDER + FE_ZRENDER)	; <<<<	Создадим текстуру для 3Д рендеринга
																	; create texture for render refractions			
	TextureBlend RefractTexture, FE_PROJECTSMOOTH							; <<<<	Новый бленд для наложения текстуры как проекции
																	; new blend for 2D project texture
	PositionTexture RefractTexture, 0.5, 0.5									; <<< Смещение = в центр 0.5
																	; set texture position to center of screen = 0.5
	ScaleTexture RefractTexture, 2, 2										; resize projection texture to full-screen
	; ----				
End Function


Function CreateWater()
	; ----
	; текстура для искажения отражения (типа вода)
	; create texture for distortion (bump)
	BumpTexture = LoadAnimTexture ( "..\media\water_anim.jpg", 9, 64, 64, 0, 32 )
	TextureBlend BumpTexture, FE_BUMP									; <<<< 	Новый бленд для бампа 
	ScaleTexture BumpTexture,0.012,0.012
	
	;FoamTexture = LoadTexture ( "..\media\foam.png", 1+2 )
	;TextureBlend FoamTexture, 1
	;ScaleTexture FoamTexture,0.032,0.032
	
	; ----
	; вот плоскость для воды
	; create water plane
	WaterPlane = LoadMesh("..\media\water32.3ds")
	ScaleMesh WaterPlane,2,1,2
	EntityFX WaterPlane,1
	
	; ----
	; А вот и клипплейн, он будет отсекать все что выше воды чтобы получить правильное преломление
	; create clipplane for cut off all that on water (save all under water only)
	WaterClipplane = CreateClipplane (  WaterPlane  )					; <<<<< при создании сразу можно выровнять по заданному ентити
															; align clipplane by water-plane
	; ----
	; Вспомогательные пивоты для выравнивания клип-плейна, чтобы нам не крутить саму воду каждый раз (превернут и чуть выше воды)
	; Auxiliary pivot for aligning clipplane
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
	EntityColor LandscapeDown,40,50,70
	PositionEntity LandscapeDown,0,-4.5,0
	; ----
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


Global DeltaTimeOld = MilliSecs()
Function MouseLook(cam)
	Local t=MilliSecs()
	Local dt = t - DeltaTimeOld
	DeltaTimeOld = t
	DeltaTimeK = Float(dt)/16.666
	s#=0.1*DeltaTimeK
	dx#=(GraphicsWidth()/2-MouseX())*0.003*DeltaTimeK
	dy#=(GraphicsHeight()/2-MouseY())*0.003*DeltaTimeK
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