; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;
; Perfect pixel lighting with aligned cubemaps
;

Include "include\FastExt.bb"					; <<<<    Include FastExt.bb file


Graphics3D 800,600,0,2


InitExt									; <<<<    Initialize library after Graphics3D function



Cam = CreateCamera()   :   PositionEntity Cam,0,0,-3
Light = CreateLight()   :   AmbientLight 64,64,64

DiffuseLightTex = LoadTexture("..\media\light64.png",1+128)
	SetCubeMode DiffuseLightTex, 2
	SetCubeAlign DiffuseLightTex, Light				; <<<<<<   Align cubemap texture to light-source

SpecularLightTex = LoadTexture("..\media\specular.jpg",1+128)
	TextureBlend SpecularLightTex, 3
	SetCubeAlign SpecularLightTex, Light				; <<<<<<   Align cubemap texture to light-source

TestTex0 = LoadTexture("..\media\test.jpg",1+128)
	SetCubeMode TestTex0, 2
	SetCubeAlign TestTex0, Light					; <<<<<<   Align cubemap texture to light-source

TestTex1 = LoadTexture("..\media\test.jpg",1+128)
	SetCubeMode TestTex1, 2

	
Mesh = CreateSphere(12)
	EntityFX Mesh,1	
	EntityTexture Mesh, TestTex0, 0,0
	EntityTexture Mesh, DiffuseLightTex, 0,1
	EntityTexture Mesh, SpecularLightTex, 0,2
	

Test = CreateSphere(12)
	EntityTexture Test, TestTex1
	PositionEntity Test,-2.5,0,0
	EntityShininess Test,0.5


While Not KeyHit(1)
	MouseLook Cam
	TurnEntity Light, 0.5, 0.25, 0.125
	RenderWorld
	Flip
Wend
End






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