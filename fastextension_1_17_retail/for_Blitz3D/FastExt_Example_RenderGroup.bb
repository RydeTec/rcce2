; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;
; RenderGroup and GroupAttach functions demo
;



Include "include\FastExt.bb"					; <<<<    Include FastExt.bb file


Graphics3D 800,600,0,2


InitExt									; <<<< 	Обязательно инициализуем после Graphics3D
										; <<<<    Initialize library after Graphics3D function


; create Camera and Light
Camera=CreateCamera()   :   PositionEntity Camera,0,0,-4  :  Light=CreateLight()   :   TurnEntity Light,45,0,0  :  AmbientLight 64,64,64  :  SetFont LoadFont("",16)

; create objects
For i=1 To 50
	Select Rand(0, 2)
		Case 0
			m=CreateCube()
			GroupAttach 1, m									; <<<< attach cube to group with id=1
			ScaleEntity m, Rnd(0.3,0.5), Rnd(0.3,0.5), Rnd(0.3,0.5)
		Case 1
			m=CreateSphere(8)
			GroupAttach 2, m									; <<<< attach sphere to group with id=2
			s# = Rnd(0.3,0.5)
			ScaleEntity m, s, s, s
		Case 2
			m=CreateCone(8,1)
			GroupAttach 3, m									; <<<< attach cone to group with id=3
			ScaleEntity m, Rnd(0.3,0.5), Rnd(0.3,0.5), Rnd(0.3,0.5)
	End Select
	EntityColor m, Rand(128,255), Rand(128,255), Rand(128,255)
	If Rnd(0,1)>0.5 Then
		GroupAttach 4, m									; <<<< attach opaque entities to group with id=4
	Else	
		EntityAlpha m, 0.3
		GroupAttach 5, m									; <<<< attach transparent entities to group with id=5							
	EndIf
	PositionEntity m, Rnd(-10,10), Rnd(-10,10), Rnd(5,15)
	TurnEntity m, Rnd(0,90), Rnd(0,90), Rnd(0,90)
Next

GroupAttach 1, Light		; <<<< attach light for group 1
; GroupAttach 2, Light		; <<<< group 2 will be without light
GroupAttach 3, Light		; <<<< attach light for group 3
GroupAttach 4, Light		; <<<< attach light for group 4
GroupAttach 5, Light		; <<<< attach light for group 5


; set default variables and RenderMask
mode = 0
help$ = "Show all objects"

; main loop
While Not KeyHit(1)
	PositionEntity Camera, 0, 0, Sin(MilliSecs()*0.1)-4
	If KeyHit(57) Then mode = (mode+1) Mod 6
		
	Select mode
		Case 0
			RenderWorld
			help = "show all objects"
		Case 1
			RenderGroup 1, Camera, 1			; <<<< Render group with id=1 (cubes only) + clear camera
			help = "show cubes only"
		Case 2
			RenderGroup 2, Camera, 1			; <<<< Render group with id=2 (spheres only) + clear camera
			help = "show spheres only withou light source"
		Case 3
			RenderGroup 3, Camera, 1			; <<<< Render group with id=3 (cones only) + clear camera
			help = "show conuses only"
		Case 4
			RenderGroup 4, Camera, 1			; <<<< Render group with id=4 (opaque entities only) + clear camera
			help = "show opaque cubes and spheres only"
		Case 5
			RenderGroup 5, Camera, 1			; <<<< Render group with id=5 (transparent entities only) + clear camera
			help = "show all transparent objects only"
	End Select

	Text 10, 10, "Press SPACE key for change mode"
	Text 10, 30, "Current mode "+mode+" ("+help+")"
	Flip
Wend