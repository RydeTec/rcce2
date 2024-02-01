; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com



;	Простой пример проекционных текстур.
;	Данный метод наложения текстуры позволяет сделать много различных эффектов,
;	в частности отражения и преломления - основу воды и преломляющих объектов.

;	Внимание! После того, как вы задали текстурный бленд FE_PROJECT
;	обязательно следует задать текстуре скейл или позицию, например ScaleTexture (texture, 1, 1),
;	чтобы включить рассчет матрицы проецирования (так как блиц по умолчанию игнорирует трансформацию
;	текстуры - это правильно, но для проекций это критично!)


; Simple example of use 2D projection blend for textures
; 2D project texture blend allows to do much different effects - reflections, refractions, water and etc.



Include "include\FastExt.bb"		; <<<<    Include FastExt.bb file

	
Graphics3D 800,600,0,2



InitExt						; <<<< 	Обязательно инициализуем после Graphics3D
								;  Initialize library after Graphics3D function



CreateLight()   :   c=CreateCamera()   :   CameraClsColor c,0,0,64   :   PositionEntity c,0,0,-3



tex0 = LoadTexture ("..\media\Devil.png",16+32)
	TextureBlend tex0, FE_PROJECT			; <<<<	Новый бленд для наложения текстуры как проекции
										;  New blend for 2D project
	ScaleTexture tex0,2,2						;		Зададим скейл 2, чтобы текстура ложилась на всю ширину экрана (вьюпорта)
										; Set scale 2 for fullscreen project
	PositionTexture tex0,0.5,0.5				;		Зададим позицию 0.5, чтобы центр текстуры был в центре экрана (вьюпорта)
										; Set position 0.5 for center texture of the screen


tex1 = LoadTexture ("..\media\Alien.png",16+32)
	TextureBlend tex1, FE_PROJECT			; <<<<	Новый бленд для наложения текстуры как проекции
	ScaleTexture tex1,2,2						;		Зададим скейл 2, чтобы текстура ложилась на всю ширину экрана (вьюпорта)
	PositionTexture tex1,0.5,0.5				;		Зададим позицию 0.5, чтобы центр текстуры был в центре экрана (вьюпорта)
	RotateTexture tex1,180					;		Повернем текстуру для примера. Положительное значение
										;		угла равно повороту против часовой стрелки.
										;  You can use rotation for texture



piv=CreatePivot()
ent0=CreateCube(piv) : PositionEntity ent0,-1.5,0,0
EntityTexture ent0,tex0

ent1=CreateSphere(5,piv) : PositionEntity ent1,1.5,0,0 : ScaleEntity ent1,1.5,1.5,1.5
EntityTexture ent1,tex1



While Not KeyHit(1)
	TurnEntity piv, 0, 0, 0.6
	TurnEntity ent0,0.3,0.5,0
	RenderWorld
	Flip
Wend