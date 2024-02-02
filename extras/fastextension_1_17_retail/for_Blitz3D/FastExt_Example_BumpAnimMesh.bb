; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com


; Простой пример использования Bump сразу в свойствах B3D файла
; Example of use bump-blend in 3D model (B3D file)


Include "include\FastExt.bb"					; <<<<    Include FastExt.bb file


Graphics3D 800,600,32,2


InitExt									; <<<< 	Обязательно инициализуем после Graphics3D
											; Initialize library after Graphics3D function
											



mesh = LoadAnimMesh( "..\media\mak_bump.b3d" )		; <<<< 	В модели (одной текстуре) задан новый бленд для бампа
											; Single texture in 3D model use new blend = FE_BUMP
Animate mesh,1,.25



; создадим разные объекты сцены
; create objects 
camera=CreateCamera()   :   PositionEntity camera,0,22,-38   :   light=CreateLight()   :   AmbientLight 128,128,128   :   SetFont LoadFont("",14)



f = 1

While Not KeyHit(1)
	TurnEntity mesh,0,0.5,0
	UpdateWorld
	RenderWorld
	
	
	If KeyHit(57) Then   :   f=1-f   :   Bump (f)   :   EndIf
	
	
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

	
	Flip
Wend