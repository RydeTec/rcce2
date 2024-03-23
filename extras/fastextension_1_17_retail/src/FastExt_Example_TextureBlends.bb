; Example of use FastExt library
; (c) 2006-2009 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com



;	ѕример новых текстурных блендов дл€ јЋ№‘ј смешивани€.
;
;	FE_ALPHAMODULATE - новый бленд, позвол€ет умножать текущую альфа-составл€ющую
;	с уже существующей (с прозрачностью текстуры, лежащий на более нижнем слое)
;
;	FE_ALPHACURRENT - новый бленд, позвол€ет использовать дл€ текущей текстуры прозрачность,
;	котора€ задана в уже существующей (прозрачностью текстуры, лежащий на более нижнем слое)
;
;	¬нимание! ћожно создать огромное множество своих уникальных текстурных блендов на любой случай,
;	использу€ команду TextureBlendCustom и набор допустимых констант D3DTOP_*, смотрите хелп!



; Example for new texture blends
; You can create new 576 custom blends with TextureBlendCustom function!
; See detail info about blends in help file!




Include "include\FastExt.bb"					; <<<<    Include FastExt.bb file

	
Graphics3D 800,600,0,2


InitExt									; <<<<    Initialize library after Graphics3D function


CreateLight()   :   c=CreateCamera()   :   CameraClsColor c,0,0,64   :   PositionEntity c,0,-0.5,-6


tex0=LoadTexture ("..\media\alpha.png",1+2)		:   TextureBlend tex0, FE_ALPHAMODULATE		; <<<<  новый бленд	 (new blend)
tex1=LoadTexture ("..\media\grass.jpg")			:   TextureBlend tex1, FE_ALPHACURRENT		; <<<<  новый бленд	 (new blend)		
tex5=LoadTexture ("..\media\water.jpg")			:   TextureBlend tex5, FE_ALPHACURRENT		; <<<<  новый бленд	 (new blend)
tex2=LoadTexture ("..\media\rock.jpg")
tex3=LoadTexture ("..\media\Alien.png",1+2)
tex4=LoadTexture ("..\media\alpha1.png",1+2)


; умножение существующей в текстуре альфы на альфу второй текстуры
; multiply current alpha of the texture with next texture-layer alpha 
cub4=CreateCube()
PositionEntity cub4,-3,0,0
EntityTexture cub4,tex3,0,0		
EntityTexture cub4,tex0,0,1		
EntityFX cub4,32				; <<<<  насильно зададим объекту отображение с учетом прозрачности текстур (force alpha-blending)


; пример управл€емого альфа-канала дл€ обычной текстуры (без альфы) за счет умножени€ альфа-составл€ющих
; use alpha from alpha-texture for next texture without alpha
cub5=CreateCube()
PositionEntity cub5,-3,-3,0
EntityTexture cub5,tex2,0,0		
EntityTexture cub5,tex0,0,1		; use alpha-cannel from this texture for previous texture
EntityFX cub5,32				; <<<<  насильно зададим объекту отображение с учетом прозрачности текстур (force alpha-blending)


; пример наложени€ одной текстуры на другую через альфу третьей (ей тоже можно управл€ть)
; удобно дл€ мультитекстурировани€ ландшафтов
; use this method for lanscape multitexturing
cub6=CreateCube()
PositionEntity cub6,3,0,0
EntityTexture cub6,tex2,0,0
EntityTexture cub6,tex0,0,1
EntityTexture cub6,tex1,0,2


; аналог предыдущего, только еще более продвинутый
; накладываем уже 3 различных текстуры через две альфа-текстуры
; use this method for lanscape multitexturing
cub7=CreateCube()
PositionEntity cub7,3,-3,0
EntityTexture cub7,tex2,0,0
EntityTexture cub7,tex0,0,1	
EntityTexture cub7,tex1,0,2	; virtual alpha-cannel for previous texture
EntityTexture cub7,tex4,0,3	
EntityTexture cub7,tex5,0,4	; virtual alpha-cannel for previous texture


; дл€ примера, чтобы видеть исходные текстуры
; entities for viewing source textures only
cub0=CreateCube()   :   PositionEntity cub0,-3.0,3,0   :   ScaleEntity cub0,0.5,0.5,0.5   :   EntityTexture cub0,tex0
cub1=CreateCube()   :   PositionEntity cub1,-1.5,3,0   :   ScaleEntity cub1,0.5,0.5,0.5   :   EntityTexture cub1,tex1
cub2=CreateCube()   :   PositionEntity cub2, 0.0,3,0   :   ScaleEntity cub2,0.5,0.5,0.5   :   EntityTexture cub2,tex2
cub3=CreateCube()   :   PositionEntity cub3, 1.5,3,0   :   ScaleEntity cub3,0.5,0.5,0.5   :   EntityTexture cub3,tex3
cub8=CreateCube()   :   PositionEntity cub8, 3.0,3,0   :   ScaleEntity cub8,0.5,0.5,0.5   :   EntityTexture cub8,tex4


d#=0
While Not KeyHit(1)
	PositionTexture tex0,d,d   :   PositionTexture tex4,-d,-d   :   d=d+0.002
		
	TurnEntity cub0,0.1,0.2,0.3
	TurnEntity cub1,0.1,0.2,0.3
	TurnEntity cub2,0.1,0.2,0.3
	TurnEntity cub3,0.1,0.2,0.3
	TurnEntity cub4,0.1,0.2,0.3
	TurnEntity cub5,0.1,0.2,0.3
	TurnEntity cub6,0.1,0.2,0.3
	TurnEntity cub7,0.1,0.2,0.3
	TurnEntity cub8,0.1,0.2,0.3
	
	RenderWorld
	Flip
Wend