; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;
; Create dynamic DOT3 with SetCubeAlign function
;
Include "include\FastExt.bb"					; <<<<    Include FastExt.bb file



Graphics3D 800,600,0,2



InitExt									; <<<<    Initialize library after Graphics3D function



MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
SetFont LoadFont ("Tahoma",13)
Cam = CreateCamera()   :   PositionEntity Cam,0,0,-3   :   CameraRange Cam,0.01,1000
Light = CreateLight()   :   AmbientLight 0,0,0
Test = CreateSphere()   :   ScaleEntity Test,0.5,0.5,0.5   :   PositionEntity Test,0,3,0



	;cubemap light source normals
	nmap = LoadTexture( "..\media\nvector.png",1+128 )
		SetCubeMode nmap,2
		TextureBlend nmap, 3
		SetCubeAlign nmap, Light					; <<<<<<   Align cubemap texture to light-source

	; mesh normals texture ( DOT3 )
	normals = LoadTexture( "..\media\hellknight_bump.jpg" )
		TextureBlend normals,4
		SetCubeMode normals,2

	; mesh ambient light texture
	ambient = CreateTexture(16,16)
		TextureBlend ambient, 3
		SetBuffer TextureBuffer(ambient)
		ClsColor 30,30,30
		Cls
		SetBuffer BackBuffer()
	
	; mesh diffuse texture
	diffuse = LoadTexture( "..\media\hellknight.jpg" )
		TextureBlend diffuse, 5
	
	;cubemap specular light source
	specular = LoadTexture( "..\media\specular.jpg",1+128 )
		SetCubeMode specular,1
		TextureBlend specular, 3
		SetCubeAlign specular, Light					; <<<<<<   Align cubemap texture to light-source


Mesh = LoadMesh("..\media\hellknight.3ds")   :   ScaleMesh Mesh, 0.03, 0.03, 0.03
	EntityFX Mesh,1
	EntityColor Mesh,0,0,0
	EntityTexture Mesh, nmap, 0,0
	EntityTexture Mesh, normals, 0,1
	EntityTexture Mesh, ambient, 0,2
	EntityTexture Mesh, diffuse, 0,3
	EntityTexture Mesh, specular, 0,4

; main loop
While Not KeyHit(1)
	MouseLook Cam
	TurnEntity Light,0.5,0.5,0						; <<<<<   Turn Light source
	RenderWorld
	Text 10,10,"Use W,S,A,D keys and Mouse for flying"
	Flip
Wend
End





Function MouseLook(cam)
	s#=0.05
	dx#=(GraphicsWidth()/2-MouseX())*0.005
	dy#=(GraphicsHeight()/2-MouseY())*0.005
	TurnEntity cam,-dy,dx,0
	RotateEntity cam,EntityPitch(cam,1),EntityYaw(cam,1),0,1
	If KeyDown(17) MoveEntity cam,0,0,s
	If KeyDown(31) MoveEntity cam,0,0,-s
	If KeyDown(32) MoveEntity cam,s,0,0 
	If KeyDown(30) MoveEntity cam,-s,0,0
End Function