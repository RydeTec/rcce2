; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com


; Пример цветового фильтра (маски)
; Example of color filter (color mask)

; Позволяет сделать некоторые эффекты (например эффект ночного видения или анаглиптику для стерео-очков) 
; сразу для всей сцены не используя дополнительного рендера


Include "include\FastExt.bb"					; <<<<    Include FastExt.bb file


Graphics3D 800,600,0,2


InitExt									; <<<< 	Обязательно инициализуем после Graphics3D
											; <<<<    Initialize library after Graphics3D function



cam=CreateCamera()   :   PositionEntity cam,0,0,-4  :  l=CreateLight()   :   TurnEntity l,45,0,0  :  SetFont LoadFont("",14)



; создадим разных объектов кучу для наглядности
Musor()
s = CreateCube()   :   EntityFX s,16
s = CreateCone()   :   EntityFX s,16   :   PositionEntity s,-2,0,0
s = CreateSphere()   :   EntityFX s,16   :   PositionEntity s,2,0,0


red=0
green=1
blue=0


While Not KeyHit(1)
	MouseLook cam

	If KeyHit(2) Then red=1-red
	If KeyHit(3) Then green=1-green
	If KeyHit(4) Then blue=1-blue
	
	ColorFilter red, green, blue

	RenderWorld
	Text 10,10,"RED color mask = "+red+" - key 1 for change red color mask"
	Text 10,30,"GREEN color mask = "+green+" - key 2 for change green color mask"
	Text 10,50,"BLUE color mask = "+blue+" - key 3 for change blue color mask"	
	Flip
Wend




; вспомогательные функции

Function MouseLook(cam)
	s#=0.1
	dx#=(GraphicsWidth()/2-MouseX())*0.005
	dy#=(GraphicsHeight()/2-MouseY())*0.005
	TurnEntity cam,-dy,dx,0
	RotateEntity cam,EntityPitch(cam,1),EntityYaw(cam,1),0,1
	 If KeyDown(17) MoveEntity cam,0,0,s
	 If KeyDown(31) MoveEntity cam,0,0,-s
	 If KeyDown(32) MoveEntity cam,s,0,0 
	 If KeyDown(30) MoveEntity cam,-s,0,0 
End Function

Function Musor()
	For i=1 To 50
		cub=CreateCube()
		EntityColor cub,Rand(128,255),Rand(128,255),Rand(128,255)
		EntityAlpha cub,Rnd(0.3,1.0)
		PositionEntity cub,Rnd(-10,10),Rnd(-10,10),Rnd(5,15)
		ScaleEntity cub,Rnd(0.3,0.5),Rnd(0.3,0.5),Rnd(0.3,0.5)
		TurnEntity cub,Rnd(0,90),Rnd(0,90),Rnd(0,90)
		EntityFX cub,32
	Next
End Function