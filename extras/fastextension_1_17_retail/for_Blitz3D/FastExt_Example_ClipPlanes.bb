; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com


; ѕример использовани€ клип-плейнов (плоскостей отсечени€)
; Example of use ClipPlanes



Include "include\FastExt.bb"					; <<<<    Include FastExt.bb file


Graphics3D 800,600,0,2


InitExt											; <<<< 	ќб€зательно инициализуем после Graphics3D
													;   Initialize library after Graphics3D function




cam=CreateCamera()   :   PositionEntity cam,0,0,-4  :  l=CreateLight()   :   TurnEntity l,45,0,0


; дл€ примера сделаем плоскость по которой будем ровн€ть клип-плейн
; create pivot for align clipplane
Plane = CreatePivot()


; а вот сам клип-плейн (их может быть 4 штуки, но лучше больше 2-х не делать, старые видюхи забьют на остальные)
; create clipplane and align by pivot
Clipplane = CreateClipplane (  Plane  )			; <<<<< при создании сразу можно выровн€ть по заданному ентити



; создадим разных объектов кучу дл€ нагл€дности
Musor()
s = CreateCube()   :   EntityFX s,16
s = CreateCone()   :   EntityFX s,16   :   PositionEntity s,-2,0,0
s = CreateSphere()   :   EntityFX s,16   :   PositionEntity s,2,0,0


Wire = 0

While Not KeyHit(1)

	TurnEntity Plane,0.3,0.4,0
	AlignClipplane Clipplane, Plane		; <<<< повернули или сместили плоскость - выровним по ней клипплейн !!!
								; align clipplane by pivot

	If KeyHit(57) Then Wire=1-Wire   :   Wireframe Wire
	RenderWorld
	Text 10,10,"press SPACE for wireframe"
	Flip
Wend






Function Musor()
	For i=1 To 50
		cub=CreateCube()
		EntityColor cub,Rand(128,255),Rand(128,255),Rand(128,255)
		PositionEntity cub,Rnd(-10,10),Rnd(-10,10),Rnd(5,15)
		ScaleEntity cub,Rnd(0.3,0.5),Rnd(0.3,0.5),Rnd(0.3,0.5)
		TurnEntity cub,Rnd(0,90),Rnd(0,90),Rnd(0,90)
		EntityFX cub,32
	Next
End Function