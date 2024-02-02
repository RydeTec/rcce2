; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com


; Пример анизотропной фильтрации и управления уровнем детализации текстур 
; Example TextureAnisotropy and TextureLodBias functions



Include "include\FastExt.bb"	; <<<<    Include FastExt.bb file

Graphics3D 800,600,0,2

InitExt					; <<<< 	Обязательно инициализуем после Graphics3D
						; Initialize library after Graphics3D function



camera = CreateCamera()   :   PositionEntity  camera, 0, 4, -10
SetFont LoadFont("Tahoma",13)


testTex0 = LoadTexture("..\media\anisotropy.png")   :   ScaleTexture testTex0,1.5,1.5
testTex1 = LoadTexture("..\media\wall.jpg")   :   ScaleTexture testTex1,1.5,1.5


For i=0 To 9
	cube = CreateCube()   :   PositionEntity cube, 6,1,4+i*10   :   EntityTexture cube, testTex0   :   EntityFX cube,1
Next
For i=0 To 9
	cube = CreateCube()   :   PositionEntity cube, -6,1,4+i*10   :   EntityTexture cube, testTex1   :   EntityFX cube,1
Next
plane = CreatePlane()   :   EntityTexture plane, testTex0   :   EntityFX plane,1


anisotropyLevel = GfxDriverCapsEx\AnisotropyMax
lodBias# = -0.2
enable = 1


; установим значения анизотропии и уровень детализации по умолчанию ( максимальная анизитропия и  lod Bias = -0.2 )
; set default values ( maximum anisotropy level and lod Bias = -0.2 )
TextureAnisotropy
TextureLodBias


While Not KeyDown(1)


	If KeyHit(57) Then enable = 1-enable


	If KeyHit(205) Then anisotropyLevel = anisotropyLevel + 1
	If KeyHit(203) Then anisotropyLevel = anisotropyLevel - 1
	If anisotropyLevel<0 Then anisotropyLevel = 0
	If anisotropyLevel>GfxDriverCapsEx\AnisotropyMax Then anisotropyLevel = GfxDriverCapsEx\AnisotropyMax


	If KeyHit(200) Then lodBias = lodBias + 0.2
	If KeyHit(208) Then lodBias = lodBias - 0.2
		
	
	If enable = 1 Then
		TextureAnisotropy anisotropyLevel			;  <<<<  set anisotropy level
		TextureLodBias lodBias					;  <<<<  set LOD Bias
	Else
		TextureAnisotropy 0						;  <<<<  Reset anisotropy level
		TextureLodBias 0.0						;  <<<<  Reset LOD Biasl
	EndIf	
	

	RenderWorld
	Text 10,10, "Texture Anisotropy level: "+anisotropyLevel+"   /   Videocard max level: "+GfxDriverCapsEx\AnisotropyMax
	Text 10,30, "Texture LodBias: " + lodBias
	Text 10,90, "Keys Left & Right for change Anisotropy level"
	Text 10,110, "Keys Up & Down for change LodBias value"
	Text 10,140, "Key SPACE for disable\enable effects: "+enable
	Flip
Wend