; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com


; Новые смешивания для кистей и объектов (EnityBlend и BrushBlend)
; Внимание! Можно создавать свои бленды с помошью функций EntityBlendCustom и BrushBlendCustom, смотрите хелп!
; New blends for entities and brushes (see details in help file)
; You can create new 169 custom blends with EntityBlendCustom & BrushBlendCustom functions!



Include "include\FastExt.bb"



Graphics3D 800,600,0,2



InitExt



tex=LoadTexture ("..\media\Devil.png",1+2)

cub0=CreateCube()
PositionEntity cub0,-2,1.5,0
EntityTexture cub0,tex

cub1=CreateCube()
PositionEntity cub1,2,1.5,0
EntityTexture cub1,tex
EntityBlend cub1, FE_INVALPHA			; <<<< инвертируем только альфа-канал (invert alpha-channel)

cub2=CreateCube()
PositionEntity cub2,-3,-1.5,0
EntityBlend cub2, FE_INVCOLOR			; <<<< инвертируем только цвет (invert color only)
EntityTexture cub2,tex

cub3=CreateCube()
PositionEntity cub3,0,-1.5,0
EntityBlend cub3, FE_INVCOLORADD		; <<<< инвертируем цвет и используем сложение (invert and add color)
EntityTexture cub3,tex

cub4=CreateCube()
PositionEntity cub4,3,-1.5,0
EntityBlend cub4, FE_NOALPHA			; <<<< принудительно отключаем использование альфа-канала для альфа-текстур! (manual disable alpha-cannel)
EntityTexture cub4,tex




CreateLight  :  c=CreateCamera()  :  CameraClsColor c,128,128,128  :  PositionEntity c,0,0,-5

While Not KeyHit(1)
	TurnEntity cub0,0.1,0.2,0.3
	TurnEntity cub1,0.1,0.2,0.3
	TurnEntity cub2,0.1,0.2,0.3
	TurnEntity cub3,0.1,0.2,0.3
	TurnEntity cub4,0.1,0.2,0.3
	RenderWorld   :   Flip
Wend