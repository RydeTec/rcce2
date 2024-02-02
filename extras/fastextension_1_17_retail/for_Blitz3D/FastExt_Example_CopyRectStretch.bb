; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com


; Ќова€ команда CopyRectStretch
; Example of use new function -  CopyRectStretch

; ѕозвол€ет копировать графику с произвольными размерами источника и приемника,
; использует простую интерпол€цию (сглаживание) когда размеры источника и приемника различны
; Allows to copy graphics data with any source size and destination size (with smooth interpolation)


Include "include\FastExt.bb"


Graphics3D 800,600,0,2					; <<<<    Include FastExt.bb file


InitExt							; <<<< 	ќб€зательно инициализуем после Graphics3D
									;  Initialize library after Graphics3D function



SetFont LoadFont("Tahoma",13) : CreateLight() : Musor()
cam=CreateCamera() : CameraClsColor cam,40,40,80 : CameraViewport cam,0,0,500,600
i0=CreateImage(200,200)
i1=CreateImage(150,300)





While Not KeyHit (1)
	MouseLook cam
	RenderWorld
	
	CopyRectStretch 0,0,500,600, 0,0,200,200, BackBuffer(), ImageBuffer(i0)	; <<<< 	копируем с произвольными размерами !!!
	CopyRectStretch 0,0,500,600, 0,0,150,300, BackBuffer(), ImageBuffer(i1)	; <<<<  ANY SIZE for source and destination

	DrawImage i0,510,10
	DrawImage i1,510,220
	
	Flip
Wend








Function MouseLook(cam)
	dx#=(GraphicsWidth()/2-MouseX())*0.01
	dy#=(GraphicsHeight()/2-MouseY())*0.01
	TurnEntity cam,-dy,dx,0
	 If KeyDown(17) MoveEntity cam,0,0,.1 
	 If KeyDown(31) MoveEntity cam,0,0,-.1
	 If KeyDown(32) MoveEntity cam,0.1,0,0 
	 If KeyDown(30) MoveEntity cam,-0.1,0,0 
End Function

Function Musor()
	For i=1 To 50
		cub=CreateCube()
		EntityColor cub,Rand(128,255),Rand(128,255),Rand(128,255)
		PositionEntity cub,Rnd(-10,10),Rnd(-10,10),Rnd(-10,10)
		ScaleEntity cub,Rnd(0.3,0.5),Rnd(0.3,0.5),Rnd(0.3,0.5)
		TurnEntity cub,Rnd(0,90),Rnd(0,90),Rnd(0,90)
	Next
End Function