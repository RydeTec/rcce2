; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com


; Пример отключения фильтрации текстур с помощью функции TextureAnisotropy
; Disable/Enable texture filtering with TextureAnisotropy function



Include "include\FastExt.bb"	; <<<<    Include FastExt.bb file

Graphics3D 800,600,0,2

InitExt					; <<<< 	Обязательно инициализуем после Graphics3D
						; Initialize library after Graphics3D function



camera = CreateCamera()   :   PositionEntity  camera, 0, 4, -10
SetFont LoadFont("Tahoma",13)

lines = LoadTexture("..\media\anisotropy.png")   :   ScaleTexture lines, 50, 50
checker = LoadTexture("..\media\checker_large.bmp")   :   ScaleTexture checker, 10, 10
tex0 = LoadTexture ("..\media\alien_small.png",1+2)
tex1 = LoadTexture ("..\media\devil_small.png",1+2)

plane = CreatePlane()
EntityTexture plane, checker
EntityFX plane, 1

plane = CreatePlane()
PositionEntity plane, 0, 32, 0
RotateEntity plane, 180, 0, 0
EntityTexture plane, lines
EntityFX plane, 1

sprite = CreateSprite()
PositionEntity sprite, -2, 4, -8
EntityTexture sprite, tex0

sprite = CreateSprite()
PositionEntity sprite, 3, 4, -6
EntityTexture sprite, tex1

sprite = CreateSprite()
PositionEntity sprite, -3, 4, -2
EntityTexture sprite, tex1

cube = CreateCube()
EntityTexture cube, tex0
PositionEntity  cube, 0.4, 2.7, -6
EntityFX cube, 1



helper$ = "disable filtering and mipmapping"


level = -2							; -2 - disable filtering and mipmapping (for all texture layers)
TextureAnisotropy level


While Not KeyDown(1)
	If KeyHit(2) Then
		level = -2					; -2 - disable filtering and mipmapping (фильтрация и мипмаппинг полностью выключены)
		TextureAnisotropy level
		helper = "disable filtering and mipmapping"
	EndIf
	
	If KeyHit(3) Then
		level = -1					; -1 - disable filtering and use linear mipmapping (фильтрация выключена, используется линейный мипмаппинг)
		TextureAnisotropy level
		helper = "disable filtering and use linear mipmapping"
	EndIf
	
	If KeyHit(4) Then
		level = 0					; 0 - use standart filtering and mipmapping (like in Blitz3D by Default) (стандартная линейная фильтрация и мипмаппинг (как в Блиц3Д изначально))
		TextureAnisotropy level
		helper = "use standart filtering and mipmapping (like in Blitz3D by Default)"
	EndIf
	
	TurnEntity cube, 1, 0.66, 0.278
	RenderWorld
	Text 10,10, "Texture filtering level: "+level+" ("+helper+")"
	Text 10,30, "Keys 1, 2, 3 - change level"
	Flip
Wend


