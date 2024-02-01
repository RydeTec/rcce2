; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com



Include "include\FastExt.bb"					; <<<<    Include FastExt.bb file



w=800
h=600



.loop
Graphics3D w, h, 0, 1


InitExt									; <<<<    Initialize library after Graphics3D function



; create scene objects
SetFont LoadFont("Tahoma",13)
cam = CreateCamera()   :   PositionEntity cam,0,0,-3
light = CreateLight()   :   TurnEntity light,45,45,0
cub = CreateCube()   :   TurnEntity cub,45,45,0

; create texture for render
tex=CreateTexture (256,256,1+2+256+FE_RENDER+FE_ZRENDER)
SetBuffer TextureBuffer(tex)
CameraViewport cam,0,0,TextureWidth(tex),TextureHeight(tex)
CameraClsColor cam,100,0,0
RenderWorld
SetBuffer BackBuffer()

; restore camera properties
EntityTexture cub,tex
CameraClsColor cam,0,0,0
CameraViewport cam,0,0,GraphicsWidth(),GraphicsHeight()

; main loop
While Not KeyHit(1)

	
	If KeyHit(2) Then
		w=800
		h=600
		Goto changeResolution
	EndIf
	
	If KeyHit(3) Then
		w=1024
		h=768
		Goto changeResolution
	EndIf
	
	If KeyHit(4) Then
		w=1280
		h=1024
		Goto changeResolution
	EndIf
	
	
	TurnEntity cub, 0.1, 0.2, 0.3
	RenderWorld
	
	
	CustomPostprocessGlow (1, 4, 2, 0.25, 3) 
	RenderPostprocess FE_Glow
	
	
	Text 10,10, "Current resolution: "+GraphicsWidth()+" x "+GraphicsHeight()
	Text 10,50,"Key 1: 800x600"
	Text 10,70,"Key 2: 1024x768"
	Text 10,90,"Key 3: 1280x1024"
	Text 10,130,"AvailVidMem: "+AvailVidMem()
	Flip
Wend
End




.changeResolution

DeInitExt						; <<<<   Use before  ClearWorld and EndGraphics !
ClearWorld
EndGraphics

Goto loop

