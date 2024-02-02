; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com



; Простой пример преломляющих объектов за два рендера
; Example for refraction (per 2 render)



Include "include\FastExt.bb"					; <<<<    Include FastExt.bb file


Graphics3D 800,600,0,2


InitExt									; <<<<    Initialize library after Graphics3D function



cam=CreateCamera()   :   PositionEntity cam,2,-1,-3   :   CameraRange cam,0.1,1000   :   TurnEntity CreateLight(),45,-180,0   :   SetFont LoadFont ("",14)



; -------- текстуры для искажения, проекции, кубемап

	; текстуры для искажения 
	; create bump texture
	BumpTex = LoadTexture ( "..\media\wall_bump.jpg" )   :   ScaleTexture BumpTex,0.25,0.25
	TextureBlend BumpTex,FE_BUMP							; <<<< новый бленд (new blend for bump)
	KnightBumpTex = LoadTexture("..\media\hellknight_bump.jpg" )
	 TextureBlend KnightBumpTex, FE_BUMPLUM					; <<<< новый бленд (new blend for luminosity bump)
	
	; текстура для проекции
	; create 2D projected texture (for render)
	ProjectTex = CreateTexture (512,512,1+8+16+32+256 + FE_ExSIZE + FE_RENDER + FE_ZRENDER)
	TextureBlend ProjectTex, FE_PROJECT	   ; <<<< новый бленд (new blend for 2D project)
	PositionTexture ProjectTex, 0.5, 0.5
	ScaleTexture ProjectTex, 2, 2
	
	; остальные текстуры сцены
	; other textures
	CubeMapTexture = CreateTexture ( 256, 256, 1+128 )
	PlaneTex = LoadTexture ( "..\media\wall.jpg" )   :   ScaleTexture PlaneTex,4,4
	
	

; -------- объекты для главной сцены с любыми текстурами
; Entities for main scene with any textures

SceneEntities = CreatePivot()


	; скайбокс
	Sky = LoadSkyBox ( "..\media\sky", SceneEntities )

	
	; кубики для разнообразия
	Musor (SceneEntities)


	Plane = CreatePlane()
	PositionEntity Plane,0,-2.5,0
	EntityTexture Plane,PlaneTex
	
	
	Sphere = CreateSphere (16, SceneEntities) 
	EntityTexture Sphere,CubeMapTexture
	EntityAlpha Sphere,0.8
	RenderCubeMap Sphere,cam,CubeMapTexture		
		

	Knight = LoadMesh("..\media\hellknight.3ds", SceneEntities)
	ScaleEntity Knight,0.03,0.03,0.03
	PositionEntity Knight,-3,0,0
	EntityColor Knight, 128,0,0   :   EntityAlpha Knight,0.2   :   EntityFX Knight,1+8		; раскрасим кнайта в красный цвет для примера
	
	

; -------- объекты для преломления ТОЛЬКО с проекционной текстурой и бамп-текстурой
; Entities for refraction (with 2d-project texture and bump-texture)

RefractEntities = CreatePivot()


	SphereBump = CreateSphere (16, RefractEntities)   :   EntityFX SphereBump,1+8			; бамп-дубликат сферы (bump duplicate for sphere)
	EntityTexture SphereBump,BumpTex,0,0
	EntityTexture SphereBump,ProjectTex,0,1
	EntityAlpha SphereBump,0.7												; силу бампа можно регулировать прозрачностью бамп-объекта
																		; power of refraction for sphere
	
	
	KnightBump = LoadMesh("..\media\hellknight.3ds", RefractEntities)   :   EntityFX KnightBump,1+8		; бамп-дубликат кнайта (bump duplicate for Knigh)
	EntityTexture KnightBump,KnightBumpTex,0,0
	EntityTexture KnightBump,ProjectTex,0,1
	ScaleEntity KnightBump,0.03,0.03,0.03
	PositionEntity KnightBump,-3,0,0


	OnlyBump = CreateSphere (16, RefractEntities)   :   EntityFX OnlyBump,1+8		; сфера, не имеющая в сцене дубликата (только бамп объект) — только искажает!
																; sphere with bump only
	EntityTexture OnlyBump,BumpTex,0,0
	EntityTexture OnlyBump,ProjectTex,0,1
	PositionEntity OnlyBump,2.5,0,0




PointEntity cam, SceneEntities
MoveMouse GraphicsWidth()/2,GraphicsHeight()/2

While Not KeyHit(1)
	MouseLook cam
	PositionEntity Sky,EntityX(cam,1),EntityY(cam,1),EntityZ(cam,1),1


	; рендерим только объекты сцены
	; render scene entities only (without refract duplicates)
	CameraClsMode cam,1,1
	ShowEntity SceneEntities
	HideEntity RefractEntities
	RenderWorld
	
	; copy to 2D projection texture 
	CopyRectStretch 0,0,GraphicsWidth(),GraphicsHeight(), 0,0,TextureWidth(ProjectTex),TextureHeight(ProjectTex), BackBuffer(), TextureBuffer(ProjectTex)	
	

	; рендерим только искажающие объекты
	; render bump-entities only
	CameraClsMode cam,0,0
	HideEntity SceneEntities
	ShowEntity RefractEntities
	RenderWorld


	; повернем кнайта и его бамп-копию на одинаковый угол, чтобы их позиции и углы совпадали!
	; turn entities
	TurnEntity Knight,0,1,0
	TurnEntity KnightBump,0,1,0
	
	TurnEntity Sphere,0.1,0.2,0.3
	TurnEntity SphereBump,0.1,0.2,0.3
	TurnEntity OnlyBump,0.1,0.2,0.3
		
		
	Text 10,10,"Use mouse and W,S,A,D keys for flying"	
	Flip
Wend






; разные вспомогательные, к теме не относятся

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

Function Musor(par=0)
	For i=1 To 50
		cub=CreateCube()
		EntityParent cub,par
		EntityColor cub,Rand(128,255),Rand(128,255),Rand(128,255)
		PositionEntity cub,Rnd(-20,20),Rnd(-20,20),Rnd(-20,20)
		ScaleEntity cub,Rnd(0.5,1),Rnd(0.5,1),Rnd(0.5,1)
		TurnEntity cub,Rnd(0,90),Rnd(0,90),Rnd(0,90)
		EntityFX cub,16 
	Next
End Function

Function LoadSkyBox( file$, parent=0 )
	m=CreateMesh( parent )
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
	ScaleMesh m,100,100,100
	FlipMesh m
	EntityFX m,1+8
	EntityOrder m,1
	Return m
End Function

Function RenderCubeMap(Entity,Camera,CubeMapTexture, Dark#=0)
	Local w%, FxCamera%

	HideEntity entity
	w=TextureWidth(CubeMapTexture)
	FxCamera = CreateCamera()
	CameraRange FxCamera,0.1,500
	CameraProjMode FxCamera,1
	CameraProjMode Camera,0
	CameraViewport FxCamera,0,0,w,w
	PositionEntity FxCamera,EntityX(Entity,1), EntityY(Entity,1) ,EntityZ(Entity,1),1
	
	If Dark>0 Then
		DebugLog "!!!"
		FxCube = CreateCube()
		FlipMesh FxCube
		PositionEntity FxCube,EntityX(Entity,1), EntityY(Entity,1) ,EntityZ(Entity,1),1
		EntityColor FxCube,10,10,10
		EntityFX FxCube,1+8+32
		EntityAlpha FxCube,Dark
		ScaleEntity FxCube,10,10,10
		EntityOrder FxCube,-9999
	EndIf
	
	; do left view
	SetCubeFace CubeMapTexture,0
	RotateEntity FxCamera,0,90,0
	RenderWorld
	CopyRect 0,0,w,w,0,0,BackBuffer(),TextureBuffer(CubeMapTexture)

	
	; do forward view
	SetCubeFace CubeMapTexture,1
	RotateEntity FxCamera,0,0,0
	RenderWorld
	CopyRect 0,0,w,w,0,0,BackBuffer(),TextureBuffer(CubeMapTexture)

	; do right view	
	SetCubeFace CubeMapTexture,2
	RotateEntity FxCamera,0,-90,0
	RenderWorld
	CopyRect 0,0,w,w,0,0,BackBuffer(),TextureBuffer(CubeMapTexture)

	; do backward view
	SetCubeFace CubeMapTexture,3
	RotateEntity FxCamera,0,180,0
	RenderWorld
	CopyRect 0,0,w,w,0,0,BackBuffer(),TextureBuffer(CubeMapTexture)

	; do up view
	SetCubeFace CubeMapTexture,4
	RotateEntity FxCamera,-90,0,0
	RenderWorld
	CopyRect 0,0,w,w,0,0,BackBuffer(),TextureBuffer(CubeMapTexture)		

	SetCubeFace CubeMapTexture,5
	RotateEntity FxCamera,90,0,0
	RenderWorld
	CopyRect 0,0,w,w,0,0,BackBuffer(),TextureBuffer(CubeMapTexture)	

	CameraProjMode FxCamera,0
	CameraProjMode camera,1
	ShowEntity entity
	FreeEntity FxCamera
	If Dark>0 Then FreeEntity FxCube
End Function