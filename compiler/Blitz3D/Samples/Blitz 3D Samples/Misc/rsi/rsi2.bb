Graphics3D 800,600
SetBuffer BackBuffer()

fpstimer = CreateTimer(100)

Type squares
	Field model, direction, speed#, life
End Type

For a = 1 To 30
	createsquare()
Next

Global camera = CreateCamera()
PositionEntity camera, 0, -50, 0
Global point = CreateCube()
PositionEntity point, 0, 0, 0
PointEntity camera, point
FreeEntity point

Global light = CreateLight(camera)
LightRange light, 200


Global crosshair = LoadMesh("models/crosshair.3ds")
PositionEntity crosshair, MouseX(), -10, MouseY()*-1
ScaleEntity crosshair, 0.3, 0.3, 0.3
RotateEntity crosshair, 90, 0, 0

Global crosshairx# = MouseX()-100
Global crosshairy# = MouseY()-100

Global socom = LoadSound("sounds\SOCOM.wav")
Global socomreload = LoadSound("sounds\SOCOMreload.wav")
Global mp5 = LoadSound("sounds\mp5.wav")
Global ak47 = LoadSound("sounds\ak47.wav")
Global enfield = LoadSound("sounds\enfield.wav")
Global reload = LoadSound("sounds\riflereload.wav")


Global selectedgun$ = "SOCOM"
Global shooting = 0
Global reloading = 0
Global lastbullet

Global ydiff#

Global lastconsolewrite
Global consoletext$


While Not KeyDown(1)
	mousekludge()
	weapons()
	blocks()
	hit()
	UpdateWorld
	RenderWorld
	Text 1, 1, selectedgun$
	Flip
	Cls
	If KeyHit(63);F5
		a = 0
		While Not done
			name$ = "screenshot" + a + ".bmp"
			If FileType(name$) = 0
				SaveBuffer(FrontBuffer(), name$)
				done = 1
			EndIf
			a = a + 1
		Wend
		WaitTimer(fpstimer)
	EndIf
Wend



Function mousekludge()
	crosshairx# = crosshairx# + Float(MouseXSpeed())*0.4
	crosshairy# = crosshairy# + Float(MouseYSpeed())*0.4
	If crosshairx# < -50
		crosshairx# = -50
	ElseIf crosshairx# > 50
		crosshairx# = 50
	EndIf
	If crosshairy# < -38
		crosshairy# = -38
	ElseIf crosshairy# > 38
		crosshairy# = 38
	EndIf
	
	
	If MouseX() < 2
		MoveMouse GraphicsWidth()-2, MouseY()
		temp = MouseXSpeed()
	ElseIf MouseX() > GraphicsWidth()-2
		MoveMouse 2, MouseY()
		temp = MouseXSpeed()
	EndIf
	If MouseY() < 2
		MoveMouse MouseX(), GraphicsHeight()-2
		temp = MouseYSpeed()
	ElseIf MouseY() > GraphicsHeight()-2
		MoveMouse MouseX(), 2
		temp = MouseYSpeed()
	EndIf
	

	PositionEntity crosshair, crosshairx#, -10, crosshairy#
End Function

Function weapons()
	If KeyHit(2)
		selectedgun$ = "SOCOM"
	ElseIf KeyHit(3)
		selectedgun$ = "MP5"
	ElseIf KeyHit(4)
		selectedgun$ = "AK47"
	ElseIf KeyHit(5)
		selectedgun$ = "ENFIELD"
	EndIf
	
	
	shooting = 0
	If selectedgun$ = "SOCOM"
		If MouseHit(1)
			If Not reloading
				shooting = 1
				chnFire = PlaySound(socom)
			EndIf
		EndIf
	ElseIf selectedgun$ = "MP5"
		If MouseDown(1) 
			If MilliSecs() - lastbullet > 100
				If Not reloading
					shooting = 1
					chnFire = PlaySound(mp5)
					lastbullet = MilliSecs()
				EndIf
			EndIf
		EndIf
	ElseIf selectedgun$ = "AK47"
		If MouseDown(1)
			If MilliSecs() - lastbullet > 200
				If Not reloading
					shooting = 1
					chnFire = PlaySound(ak47)
					lastbullet = MilliSecs()
					If ydiff# < 2
						a# = Rnd(2)+0.5
						crosshairx# = crosshairx# + Rnd(2)-1
						crosshairy# = crosshairy# - a#
						ydiff# = ydiff#  + a#					
					ElseIf ydiff# < 5	
						a# = Rnd(3)+0.5
						crosshairx# = crosshairx# + Rnd(5)-2.5
						crosshairy# = crosshairy# - a#
						ydiff# = ydiff# + a#
					Else
						a# = Rnd(4)+0.5
						crosshairx# = crosshairx# + Rnd(5)-2
						crosshairy# = crosshairy# - a#
						ydiff# = ydiff# + a#
					EndIf
				EndIf
			EndIf
		EndIf
	ElseIf selectedgun$ = "ENFIELD"
		If MouseDown(1)
			If MilliSecs() - lastbullet > 1500
				If Not reloading
					shooting = 1
					chnFire = PlaySound(enfield)
					lastbullet = MilliSecs()
				EndIf
			EndIf
		EndIf
	EndIf
	
	If ydiff# > 0
		ydiff# = ydiff# - 0.1
		crosshairy# = crosshairy# + 0.1
	Else
		ydiff# = 0
	EndIf

		
	
	If MouseHit(2)
		If Not reloading
			reloading = 1
			If selectedgun$ = "SOCOM"
				chnReload = PlaySound(socomreload)
			Else
				chnReload = PlaySound(reload)
			EndIf
		EndIf
	EndIf
	If reloading
		reloading = reloading + 2
		If reloading > 180
			reloading = 0
		EndIf
		RotateEntity crosshair, reloading+90, 0, 0
	Else
		RotateEntity crosshair, 90, 0, 0
	EndIf
End Function

Function hit()
	CameraProject camera, crosshairx#,-10,crosshairy#
	x = ProjectedX()
	y = ProjectedY()
	
	If shooting
		entity = CameraPick(camera, x, y)
		For s.squares = Each squares
			If s\model = entity
				If selectedgun$ = "SOCOM"
					s\life = s\life - 1
				ElseIf selectedgun$ = "MP5"
					s\life = s\life - 1
				ElseIf selectedgun$ = "AK47"
					s\life = s\life - 4
				ElseIf selectedgun$ = "ENFIELD"
					s\life = s\life - 10
				EndIf
			EndIf
			If s\life < 1
				FreeEntity s\model
				Delete s
			EndIf
		Next
	EndIf
End Function

	
Function blocks()
	For s.squares = Each squares
		PositionEntity s\model, EntityX(s\model)+s\speed#*s\direction, EntityY(s\model), EntityZ(s\model)
		If EntityX(s\model) < -70
			PositionEntity s\model, 70, EntityY(s\model), EntityZ(s\model)
		ElseIf EntityX(s\model) > 70
			PositionEntity s\model, -70, EntityY(s\model), EntityZ(s\model)
		EndIf
		RotateEntity s\model, 0, EntityYaw(s\model)+Rnd(1), EntityRoll(s\model)+Rnd(1)
	Next
End Function

	
Function createsquare()
	s.squares = New squares
	s\model = CreateCube()
	PositionEntity s\model, Rand(100)-50, 0, Rand(75)-75/2
	EntityColor s\model, Rand(255), Rand(255), Rand(255)
	RotateEntity s\model, Rand(360), Rand(360), Rand(360)
	ScaleEntity s\model, 2.5, 2.5, 2.5
	EntityPickMode s\model, 3, True
	s\speed# = Float(Rand(5) + 2)/30
	s\direction = (Rand(0, 1)*2)-1
	s\life = 10
End Function