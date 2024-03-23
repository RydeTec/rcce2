; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com



; Пример создания текстуры:
;
; 1. Имеющей любой размер (не кратный степени двойки) (+ возможность большего размера, чем задано в графическом режиме)
; 2. Готовой к рендерингу в неё
; 3. Имеющей зет-буффер при необходимости
;
; А ТАКЖЕ пример доработанной CLS команды, которая может задавать альфа-прозрачность при очистке и величину зет-буффера


; Example or render to texture




Include "include\FastExt.bb"					; <<<<    Include FastExt.bb file


Graphics3D 800,600,0,2


InitExt									; <<<<    Initialize library after Graphics3D function


texRender = CreateTexture(400,400, 1 + 2 + 256 + FE_ExSIZE + FE_RENDER + FE_ZRENDER)				;<<<<  новое! (new flags)
				; FE_ExSIZE - любая ширина и высота текстуры (не кратная степени двойки)
								; any size for texture
				; FE_RENDER - в текстуру можно рендерить
								; render to texture flag
				; FE_ZRENDER - текстура будет иметь Z-Буффер (можно рендерить не только 2Д, но и корректное 3Д)
								; create depth buffer (Z-buffer) for 3D rendering
				; Внимание! Для рендерящихся текстур флаг 256 должен обязательно быть включен!
				; Flag 256 obligatory!!!

CreateLight
cam=CreateCamera()
PositionEntity cam,0,0,-3
cub=CreateCube()
cub1=CreateCube()
ScaleEntity cub1,2.0,0.2,0.2

tex= LoadTexture ("..\media\Devil.png",1+2)
SetBuffer TextureBuffer(tex)
Rect 0,0,TextureWidth(tex),TextureHeight(tex),0
Rect 1,1,TextureWidth(tex)-2,TextureHeight(tex)-2,0


While Not KeyHit(1)
	TurnEntity cub,0.1,0.2,0.3
	TurnEntity cub1,-0.1,0,-0.3
	
	
	EntityTexture cub,tex
	SetBuffer TextureBuffer(texRender)			;<<<<	теперь команда SetBuffer может установить и текстуру для рендера
										; Set texture for render
										
	ClsColor 128,0,0,64						;<<<<	теперь в ClsColor можно задать Alpha уровень (прозрачности) и Z-уровень (только для тех кто понимает что это такое :)
										; Set Cls color 128,0,0 AND alpha = 64
										
	Cls									;<<<<	теперь Cls очищает и текстуры для рендера, значениями заданными через ClsColor
										;		(при очистке учитывается заданный Viewport и очищается только эта область!)
										
	CameraClsMode cam,0,0					; отключим Cls камеры, чтобы не мешал, мы уже почистили текстуру как нам надо!
										; disable Cls in camera
	RenderWorld											; render scene to texture
	Rect 0,0,TextureWidth(texRender),TextureHeight(texRender),0		; draw rectangle
	
	
	EntityTexture cub,texRender		; <<<<	теперь положим на кубик нашу теустуру для рендера
								; place texture to entity
	SetBuffer BackBuffer()			; и рендерим как обычно на бэк-буфер
	CameraClsMode cam,1,1			; вернем камере Cls, хотя можно корректно очистить и вручную теперь (restore Cls mode)
	RenderWorld					; render scene to back buffer
	
	
	Flip
Wend

FreeTexture texRender			;<<<<	удалять текстуру с Z-Буффером (флаг FE_ZRENDER) нужно через FreeTexture,
							;		чтобы очистить память видеокарты от Z-Буффера
							;		Актуально только при смене видеорежима (чтобы не терять видео-память),
							;		в других случаях приложение
							;		при завершении работы само удалит все свои объекты

							
							
							
							
							
							