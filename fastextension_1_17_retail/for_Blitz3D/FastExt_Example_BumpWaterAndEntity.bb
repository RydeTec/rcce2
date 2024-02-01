; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com


; Простой пример использования Bump для искажения объектов и создания самой простой воды.
; Simple example for use new texture-blend FE_BUMP for texture distortion and simple water-plane


Include "include\FastExt.bb"		; <<<<    Include FastExt.bb file


Graphics3D 800,600,0,2


InitExt								; <<<< 	Обязательно инициализуем после Graphics3D
										; <<<<    Initialize library after Graphics3D function



BumpTex = LoadTexture ( "..\media\water.jpg" )
TextureBlend BumpTex, FE_BUMP								; <<<< 	Новый бленд для бампа
														; New texture-blend for bump (distortion)
ScaleTexture BumpTex,2,2


BumpTexA = LoadAnimTexture ( "..\media\water_anim.jpg", 9, 64, 64, 0, 32 )
TextureBlend BumpTexA, FE_BUMPLUM							; <<<< 	Новый бленд для бампа  (с поддержкой яркости)
														; New texture-blend for bump with luminosity


Tex = LoadTexture ( "..\media\rock.jpg" )
TexAlpha=LoadTexture ("..\media\Alien.png",1+2)

plane = CreatePlane()   :   EntityFX plane,1
EntityTexture plane, BumpTex, 0, 0			; <<<< 	Текстуру бампа всегда кладем на нулевой слой !!!
									; set Bump texture for zero-layer
EntityTexture plane, Tex, 0, 1

cub = CreateCube()  :  PositionEntity cub,1.5,0,0
EntityTexture cub, BumpTex, 0, 0			; <<<< 	Текстуру бампа всегда кладем на нулевой слой !!!
EntityTexture cub, Tex, 0, 1

cubA = CreateCube()  :  PositionEntity cubA,-1.5,0,0
EntityTexture cubA, BumpTex, 0, 0			; <<<< 	Текстуру бампа всегда кладем на нулевой слой !!!
EntityTexture cubA, TexAlpha, 0, 1
EntityFX cubA,32




; создадим разные объекты сцены
cam = CreateCamera()   :   PositionEntity cam, 0, 0.5, -3.5   :   CameraClsColor cam, 130, 126, 120   :   CameraRange cam, 0.1, 1000   :   CreateLight   :   SetFont LoadFont("",14)   :   MoveMouse GraphicsWidth()/2,GraphicsHeight()/2


f=1
dx# = 0
tex = BumpTexA
frame = 0
MoveMouse GraphicsWidth()/2,GraphicsHeight()/2


While Not KeyHit(1)
	MouseLook cam   :   	RenderWorld
	
	
	
	;  >>>>  Будем смещать искажающие Bump текстуры для создания эффекта течения воды
	; move bump texture
	PositionTexture BumpTex, dx, dx
	PositionTexture BumpTexA, dx, dx
	dx = dx + 0.002
	
	
	If KeyHit(57) Then   :   f=1-f   :   Bump (f)   :   EndIf
	
	
	If KeyHit(28) Or MouseHit(1)  Or MouseHit(2)=1  Then
		If tex = BumpTexA
			tex = BumpTex
		Else
			tex = BumpTexA
		EndIf
		EntityTexture plane, tex, 0, 0
		EntityTexture cub, tex, 0, 0
		EntityTexture cubA, tex, 0, 0
	EndIf
	If tex = BumpTexA Then
		EntityTexture plane, tex, (frame Mod 32), 0
		EntityTexture cub, tex,  (frame Mod 32), 0
		EntityTexture cubA, tex,  (frame Mod 32), 0
		frame = frame + 1
	EndIf


	;  >>>>  С помощью функции Bump() без заданного параметра можно проверить поддержку Enbm
	; fast check bump (EMBM) support with function Bump() without params
	If ( Bump() And 1 ) Then
		Color 0, 220, 0   :   Text 10, 10, "Environment Bump Mapping ( EMBM ) support !"
	Else
		Color 220, 0, 0   :   Text 10, 10, "Environment Bump Mapping ( EMBM ) NOT support :("
	EndIf
	If ( Bump() And 2 ) Then
		Color 0, 220, 0   :   Text 10, 30, "Environment Bump Mapping Luminance ( EMBM ) support !"
	Else
		Color 220, 0, 0   :   Text 10, 30 ,"Environment Bump Mapping Luminance ( EMBM ) NOT support :("
	EndIf
	
	If ( Bump() And 4 ) Then
		Color 0, 220, 0   :   Text 10, 50, "Bump ENABLED"
	Else
		Color 220, 0, 0   :   Text 10, 50 ,"Bump DISABLED"
	EndIf	
	Color 255,255,255
	Text 10,70,"Space - disable\enable bump"
	Text 10,90,"Enter - change bump texture"
	
	
	Flip
Wend




; разные вспомогательные функции

Function MouseLook ( ent, mov# = 0.02 )
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