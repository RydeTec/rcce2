
; -----
; Render cubemap functions ( example only )
; -----

Global RenderToCubemapCamera = 0



; Render to cubemap with FastExtension library

Function RenderToCubemap ( Entity, Camera, CubemapTexture, ClsColorRed=128, ClsColorGreen=128, ClsColorBlue=128, ClsColorAlpha=128)
	Local CubemapTextureBuffer = TextureBuffer(CubemapTexture)

	CameraProjMode Camera,0
	HideEntity Entity
	ClsColor ClsColorRed, ClsColorGreen, ClsColorBlue, ClsColorAlpha
	
	If RenderToCubemapCamera=0 Then RenderToCubemapCamera = CreateCamera()
	CameraRange RenderToCubemapCamera, 0.1, 5000
	CameraClsMode RenderToCubemapCamera, 0, 0
	CameraProjMode RenderToCubemapCamera,1
	CameraViewport RenderToCubemapCamera,0,0,TextureWidth(CubemapTexture),TextureHeight(CubemapTexture)
	PositionEntity RenderToCubemapCamera, EntityX(Entity,1), EntityY(Entity,1) ,EntityZ(Entity,1), 1
	
	; do left view
	SetCubeFace CubemapTexture,0					; <<< Set cube face for texture
	SetBuffer CubemapTextureBuffer						; <<< Set texture buffer (for selected cube face)
	RotateEntity RenderToCubemapCamera,0,90,0
	Cls : RenderWorld								; <<< Use Cls function for universal clearing textures (with and without Z-Buffer)
	
	; do forward view
	SetCubeFace CubemapTexture,1
	SetBuffer CubemapTextureBuffer
	RotateEntity RenderToCubemapCamera,0,0,0
	Cls : RenderWorld

	; do right view	
	SetCubeFace CubemapTexture,2
	SetBuffer CubemapTextureBuffer
	RotateEntity RenderToCubemapCamera,0,-90,0
	Cls : RenderWorld

	; do backward view
	SetCubeFace CubemapTexture,3
	SetBuffer CubemapTextureBuffer
	RotateEntity RenderToCubemapCamera,0,180,0
	Cls : RenderWorld

	; do up view
	SetCubeFace CubemapTexture,4
	SetBuffer CubemapTextureBuffer
	RotateEntity RenderToCubemapCamera,-90,0,0
	Cls : RenderWorld

	; do down view
	SetCubeFace CubemapTexture,5
	SetBuffer CubemapTextureBuffer
	RotateEntity RenderToCubemapCamera,90,0,0
	Cls : RenderWorld

	CameraProjMode RenderToCubemapCamera,0
	CameraProjMode Camera,1
	ShowEntity Entity
	SetBuffer BackBuffer()
End Function




; Render cubemap with classic Blitz3D functions only

Function RenderCubemapCopyRect ( Entity, Camera, CubemapTexture, ClsColorRed=128, ClsColorGreen=128, ClsColorBlue=128)
	Local CubemapTextureWidth = TextureWidth(CubemapTexture)
	Local CubemapTextureHeight = TextureHeight(CubemapTexture)

	CameraProjMode Camera,0
	HideEntity Entity
	SetBuffer BackBuffer()
	
	If RenderToCubemapCamera=0 Then RenderToCubemapCamera = CreateCamera()
	CameraRange RenderToCubemapCamera, 0.1, 5000
	CameraClsMode RenderToCubemapCamera, 1, 1
	CameraClsColor RenderToCubemapCamera, ClsColorRed, ClsColorGreen, ClsColorBlue
	CameraProjMode RenderToCubemapCamera,1
	CameraViewport RenderToCubemapCamera,0,0, CubemapTextureWidth, CubemapTextureHeight
	PositionEntity RenderToCubemapCamera, EntityX(Entity,1), EntityY(Entity,1) ,EntityZ(Entity,1), 1
	
	; do left view
	SetCubeFace CubemapTexture,0
	RotateEntity RenderToCubemapCamera,0,90,0
	RenderWorld
	CopyRect 0,0,CubemapTextureWidth,CubemapTextureHeight, 0,0,BackBuffer(), TextureBuffer(CubemapTexture)
	
	; do forward view
	SetCubeFace CubemapTexture,1
	RotateEntity RenderToCubemapCamera,0,0,0
	RenderWorld
	CopyRect 0,0,CubemapTextureWidth,CubemapTextureHeight, 0,0,BackBuffer(), TextureBuffer(CubemapTexture)

	; do right view	
	SetCubeFace CubemapTexture,2
	RotateEntity RenderToCubemapCamera,0,-90,0
	RenderWorld
	CopyRect 0,0,CubemapTextureWidth,CubemapTextureHeight, 0,0,BackBuffer(), TextureBuffer(CubemapTexture)

	; do backward view
	SetCubeFace CubemapTexture,3
	RotateEntity RenderToCubemapCamera,0,180,0
	RenderWorld
	CopyRect 0,0,CubemapTextureWidth,CubemapTextureHeight, 0,0,BackBuffer(), TextureBuffer(CubemapTexture)

	; do up view
	SetCubeFace CubemapTexture,4
	RotateEntity RenderToCubemapCamera,-90,0,0
	RenderWorld
	CopyRect 0,0,CubemapTextureWidth,CubemapTextureHeight, 0,0,BackBuffer(), TextureBuffer(CubemapTexture)

	; do down view
	SetCubeFace CubemapTexture,5
	RotateEntity RenderToCubemapCamera,90,0,0
	RenderWorld
	CopyRect 0,0,CubemapTextureWidth,CubemapTextureHeight, 0,0,BackBuffer(), TextureBuffer(CubemapTexture)

	CameraProjMode RenderToCubemapCamera,0
	CameraProjMode Camera,1
	ShowEntity Entity
End Function

