; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com


;
; You can use spherical environment texture with bump texture. This simply and effectively.
;
; ATTENTION!
; DON'T use cubemap textures with bump texture, it's wrong (correctly works only on ATI videocards)!
; This restriction of DirectX7. About it is written in DirectX7 specification (DirectX7 SDK).
; Look an example FastExt_Example_EntityRefraction.bb with alternative technic.
;




Include "include\FastExt.bb"							; Include FastExt.bb file

Graphics3D 800,600,0,2

InitExt											;  Initialize library after Graphics3D function
						




tex_env0 = LoadTexture("..\media\panels.jpg",64)				; spherical environment map
tex_bump0 = LoadTexture("..\media\tenbm.jpg")				; bump map
TextureBlend tex_bump0, FE_BUMP						; <<<<<   new blend for bump FE_BUMP

	mesh0 = CreateCylinder()
	EntityTexture mesh0, tex_bump0, 0, 0						; <<<< 
	EntityTexture mesh0, tex_env0, 0, 1



tex_env1 = LoadTexture("..\media\blshine.jpg")				; simple texture
tex_bump1 = LoadTexture("..\media\tenbm.jpg",64)			; spherical environment bump map
TextureBlend tex_bump1, FE_BUMP						; <<<<<   new blend for bump FE_BUMP

	mesh1 = CreateCone()
	EntityTexture mesh1, tex_bump1, 0, 0						; <<<< 
	EntityTexture mesh1, tex_env1, 0, 1




; create objects 
camera=CreateCamera()   :   PositionEntity camera,0,0,-4   :   light=CreateLight()   :   AmbientLight 128,128,128
PositionEntity mesh0,1.5,0,0   :   PositionEntity mesh1,-1.5,0,0

While Not KeyHit(1)
	TurnEntity mesh0,0,0.5,0.2
	TurnEntity mesh1,0,0.5,0.2
	RenderWorld
	Flip
Wend