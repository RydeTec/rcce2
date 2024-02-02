; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com


; Пример использования кубемап-отражений с бамп блендом
; Example of use cubemap Reflection with bump blend



Include "include\FastExt.bb"					; <<<<    Include FastExt.bb file

Graphics3D 800,600,0,2

InitExt									; <<<< 	Обязательно инициализуем после Graphics3D
										;		Initialize library after Graphics3D function



; create camera, light
cam=CreateCamera()  :  CameraRange cam, 0.1, 5000  :  CameraZoom cam,1.4   :  TurnEntity cam, 0, 65, 0  :  PositionEntity cam,3,-0.5,2
AmbientLight 40, 40, 40  :  l=CreateLight()  :  TurnEntity l, 60, 0, 0 : SetFont LoadFont("Tahoma",13)
ShowReflectFlag = 0



; This projective texture used for render all reflections
texReflect = CreateTexture ( 256, 256, 16 + 32 + 256 + FE_ZRENDER )
	TextureBlend texReflect, FE_PROJECT
	ScaleTexture texReflect, 2, 2*Float(GraphicsWidth())/Float(GraphicsHeight())
	PositionTexture texReflect, 0.5, 0.5



; Pivot contain all Reflect objects only (with pre-rendered specular cubemap for every location of scene)
pReflect = CreatePivot()
	RoomReflect = LoadMesh ( "..\media\reflection.b3d", pReflect )

	; About reflect mesh:
	; Medium poly mesh for correct view specular cubemap (all planes sub-divided to segments)
	; - 0 texture layer contain specular cubemap = room reflections
	; - 1 texture layer can be contain specular map with "Multiply" blend (used for floor and box)
	;    BrushFx = 1 (full-bright)



; Pivot contain all other scene objects
pNormal = CreatePivot()
	Sky = LoadSkyBox ( "..\media\sky", pNormal  )
	Room = LoadMesh ("..\media\normal.b3d", pNormal )
	SetBumpAndReflect Room, texReflect		; <<<<< this function find and relace black texture to reflect projective texture
										; 	and set FE_BUMP blend for bump texture (see below)

	; About standart mesh with reflections:
	; clasiic LOW poly mesh
	; - 0 texture layer contain Bump map
	; - 1 texture layer contain black texture which will be replaced to projective texture -> texReflect
	; - 2 texture layer - diffuse texture with "ADD" blend
	; - 3 texture layer - lightmap texture with "Multiply2X" or "Multiply" blend
	;   BrushFx = 1 (full-bright)



BumpPowerFactor# = 0.09


MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
While Not KeyHit(1)
	MouseLook cam
	
	
	If KeyHit(2)>0 And BumpPowerFactor>0 Then BumpPowerFactor = BumpPowerFactor - 0.01
	If KeyHit(3)>0 Then BumpPowerFactor = BumpPowerFactor + 0.01
	BumpPower BumpPowerFactor
	
	
	; render reflection-meshes only to small texture
		SetBuffer TextureBuffer (texReflect)
		CameraViewport cam, 0, 0, TextureWidth(texReflect), TextureHeight(texReflect)
		ShowEntity pReflect
		HideEntity pNormal
		RenderWorld
	
	; render main scene (without reflection-meshes)
		SetBuffer BackBuffer()
		CameraViewport cam, 0, 0, GraphicsWidth(), GraphicsHeight()
		HideEntity pReflect
		ShowEntity pNormal
		RenderWorld
	
	; for debug view reflect texture only
		If KeyHit(57) Then ShowReflectFlag=1-ShowReflectFlag
		If ShowReflectFlag=1 Then CopyRect 0,0,TextureWidth(texReflect),TextureHeight(texReflect), 0,0, TextureBuffer(texReflect),BackBuffer()
	
	; show helper
		Text 10,10,"Use W,S,A,D keys and mouse for flying"
		Text 10,30,"Press SPACE for show reflect texture"
		Text 10,50,"Keys 1/2 - decrease/increase Bump power: "+Str(BumpPowerFactor)
		
	Flip
Wend





Function SetBumpAndReflect (mesh, reflectTexture)
	Local i, q, sf, b, t0, t1, t2, t3
	If EntityClass(mesh)="Mesh" Then
		q = CountSurfaces(mesh)
		For i = 1 To q
			sf = GetSurface(mesh,i)
			b = GetSurfaceBrush( sf )
			t0 = GetBrushTexture(b,0)
			t1 = GetBrushTexture(b,1)
			t2 = GetBrushTexture(b,2)
			t3 = GetBrushTexture(b,3)
			
			b1 = CreateBrush()
			TextureBlend t0, FE_BUMP	; or FE_BUMPLUM, if bump texture contain luminocity channel
			BrushTexture b1, t0, 0, 0
			BrushTexture b1, reflectTexture, 0, 1
			BrushTexture b1, t2, 0, 2
			BrushTexture b1, t3, 0, 3
			
			PaintSurface  sf,b1
			FreeBrush b		
		Next
		EntityFX mesh, 16
	EndIf
End Function






Function MouseLook ( ent, mov# = 0.075 )
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
		PositionEntity cub,Rnd(-10,10),Rnd(-10,10),Rnd(5,15)
		ScaleEntity cub,Rnd(0.3,0.5),Rnd(0.3,0.5),Rnd(0.3,0.5)
		TurnEntity cub,Rnd(0,90),Rnd(0,90),Rnd(0,90)
		EntityFX cub,32
	Next
End Function

Function LoadSkyBox( file$, parent=0 )
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
	ScaleMesh m,1000,1000,1000
	FlipMesh m
	EntityFX m,1
	EntityParent m,parent
	Return m
End Function