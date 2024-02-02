; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com


; Новые FX флаги в Блице
; New FX flags for entities and brushes in Blitz3D



Include "include\FastExt.bb"



Graphics3D 800,600,0,2

InitExt



CreateLight() : c=CreateCamera() : PositionEntity c,0,0,-4  :  SetFont LoadFont("",14)



; обычный меш для примера
; standart mesh
cub=CreateCone()
PositionEntity cub,-1.2,0,0


; меш с новым Fx = 64 ( FE_WIRE )
; wireframe mesh
cub1=CreateCube()
PositionEntity cub1,1.2,0,0
EntityFX cub1, FE_WIRE				; <<<<	рисуем только линии (wireframe)



; новый Fx FE_WIRE можно задать к примеру отдельному сюрфейсу
; set new wireframe FX for any surface!
cub3=CreateCone()
PositionEntity cub3,0,-1.2,0
EntityFX cub3, 16

	brush = CreateBrush()
	BrushFX brush, FE_WIRE Or 16					; <<<<	рисуем только линии (wireframe) ОДНОГО сюрфейса меша!
	PaintSurface GetSurface ( cub3, 1 ),brush



; меш с новым Fx = 128 ( FE_POINT )
; pointframe mesh
cub2=CreateSphere()
PositionEntity cub2,0,1.2,0
EntityFX cub2, FE_POINT Or 16			; <<<<	рисуем только точки (вершины) (draw vertexes only!)



While Not KeyHit(1)

	If KeyHit(57) Then
		w=1-w
		Wireframe w
	EndIf

	TurnEntity cub  ,0.1,0.2,0.3
	TurnEntity cub1,0.1,0.2,0.3
	TurnEntity cub2,0.1,0.2,0.3
	TurnEntity cub3,0.1,0.2,0.3

	RenderWorld
	Text 10,10,"SPACE for Wireframe "+w
	Flip
Wend