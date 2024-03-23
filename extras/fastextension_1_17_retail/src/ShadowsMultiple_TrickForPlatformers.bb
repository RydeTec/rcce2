; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;
; Multiple shadows - trick for platformer games (two shadow layers)
;


Include "include\FastExt.bb"					; <<<<   Include FastExtension library
Include "include\ShadowsMultiple.bb"			; <<<<<   Include shadow system



Graphics3D 800,600,0,2
AppTitle "   F1 - on\off UpdateShadow;   F2 - debug shadow texture (shadow map)"



InitExt								; <<<<    Initialize FastExtension library after Graphics3D function



FirstShadow.Shadow = CreateShadow (1)	 	; <<<<<   create first shadow object (with quality=1) and customize his characteristics
ShadowRange FirstShadow, 20						; <<<<<   set shadow range (50x50)
ShadowPower FirstShadow, 0.6
ShadowOffset FirstShadow, 0, 0, 1.5
FirstShadowTexture = ShadowTexture(FirstShadow)		; <<<<<   get first shadow texture (shadow map) 



SecondShadow.Shadow = CreateShadow(1) 			; <<<<< create second shadow object (with quality=1) and customize his characteristics
ShadowRange SecondShadow,  20
ShadowPower SecondShadow, 0.5
ShadowOffset SecondShadow, 0, 0, 1.5
SecondShadowTexture = ShadowTexture(SecondShadow)		; <<<<<   get second shadow texture (shadow map) 



; create light sources

LightPivot = CreatePivot()
TurnEntity LightPivot, 0, 30, 0
AmbientLight 90, 90, 90

FirstLight=CreateLight(1, LightPivot)
TurnEntity FirstLight, 15,0,0



ShadowLight FirstShadow, FirstLight		; <<<<<  set light source for shadow objects
ShadowLight SecondShadow, 	FirstLight
	


; create scene objects 

Camera = CreateCamera()
	PositionEntity Camera, 0, 5, -10
	CameraRange Camera, 0.1, 5000
	CameraClsColor Camera,130,150,178
	CameraPivot = CreatePivot(Camera)
	PositionEntity CameraPivot, 3, 3, 10
	EntityParent CameraPivot, 0, 1

AnimMesh = LoadAnimMesh ("..\media\terror.b3d")
	ScaleEntity AnimMesh, 0.1, 0.1, 0.1
	PositionEntity AnimMesh, 0, 4.7, 0
	RotateEntity AnimMesh, 0, 90, 0
	Animate AnimMesh, 1, 0.1

	CreateShadowCaster  FirstShadow, AnimMesh		; <<<<<  attach AnimMesh entity as caster to the shadow objects ( FIRST shadow ONLY )
	


Facture = LoadTexture("..\media\Panels.jpg")
	ScaleTexture Facture, 0.06, 0.12

Wall = CreateCube()
	ScaleMesh Wall, 50, 20, 1
	PositionMesh Wall, 0, 20, 0
	m = CreateCube()
	ScaleMesh m, 50, 1, 20
	PositionMesh m, 0, 0, -20
	AddMesh m, Wall
	FreeEntity m
	MoveEntity Wall, 0, 0, 6
	EntityTexture Wall, Facture, 0, 0

For i=-2 To 2
	Box = CreateCube()
	PositionEntity Box, 5.1-i*16, 2, 4
	EntityTexture Box, Facture
	
	EntityTexture Box, SecondShadowTexture, 0, 1	; <<<<<  place shadow textures to entity (entity will be a receiver)
	CreateShadowCaster  FirstShadow, Box			; <<<<<  attach AnimMesh entity as caster to the shadow objects ( FIRST shadow ONLY )
Next

For i=-2 To 2
	Cylinder = CreateCube()
	ScaleEntity Cylinder, 0.3, 8, 0.3
	PositionEntity Cylinder, 3.1-i*8, 9, -4
	EntityTexture Cylinder, Facture
	Box = CreateCube()
	PositionEntity Box, 3.1-i*8, 2, -4
	EntityTexture Box, Facture
	
	CreateShadowCaster  FirstShadow, Cylinder			; <<<<<  attach mesh as caster to the shadow objects ( FIRST & SECOND shadows )
	CreateShadowCaster  SecondShadow, Cylinder

	CreateShadowCaster  FirstShadow, Box 			; <<<<<  attach mesh as caster to the shadow objects ( FIRST & SECOND shadows )
	CreateShadowCaster  SecondShadow, Box 
Next

EntityTexture Wall, FirstShadowTexture, 0, 1				; <<<<<  place shadow textures to entity (entity will be a receiver)
EntityTexture AnimMesh, SecondShadowTexture, 0, 0



debug = 0
update = 1
lightAngle# = 0
MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
While Not KeyHit(1)

	; update user input and animation
	MouseLook Camera
	If KeyHit(59) Then update = 1-update
	If KeyHit(60) Then debug = 1-debug
	RotateEntity LightPivot, 0, 30+10*Sin(lightAngle), 0
	RotateEntity FirstLight, 25 + 10*Cos(lightAngle), 0, 0
	lightAngle = lightAngle + 0.5*DeltaTime()
	UpdateWorld
	
	
		HideEntity Camera		; <<<<<  Attention! Hide camera before UpdateShadows function if you use other entity in UpdateShadows function (and other cameras!)
		
		; update shadows and render scene
		If update Then UpdateShadows CameraPivot			; <<<<<  Update Shadow system (first param in function - entity, around which will be positioned a shadow map)
		
		ShowEntity Camera

	
	RenderWorld
	If debug Then
		DebugShadow FirstShadow		; <<<< can view shadow texture on screen for debug (use after RenderWorld function)
		DebugShadow SecondShadow, 0, 256
	EndIf
	Color 0, 0, 0  :  Text 10, 10, FPS()
	Flip 0
Wend



FreeShadows				; <<<<<  If shadows do not needed then use function FreeShadow (before DeInitExt function)
DeInitExt
End








; other auxiliary functions

Global FPSCount = 0
Global FPSCountTemp = 0
Global FPSTime = 0
Function FPS()
	If (MilliSecs()-FPSTime)>=1000 Then
		FPSTime = MilliSecs()
		FPSCount = FPSCountTemp
		FPSCountTemp = 0
	EndIf
	FPSCountTemp = FPSCountTemp + 1
	Return FPSCount
End Function


Global DeltaTimeK# = 1.0
Global DeltaTimeOld = MilliSecs()
Function MouseLook(cam)
	Local t=MilliSecs()
	Local dt = t - DeltaTimeOld
	DeltaTimeOld = t
	Local dk# = Float(dt)/16.666
	DeltaTimeK = dk
	s#=0.2*dk
	dx#=(GraphicsWidth()/2-MouseX())*0.2
	dy#=(GraphicsHeight()/2-MouseY())*0.2
	TurnEntity cam,-dy,dx,0
	RotateEntity cam,EntityPitch(cam,1),EntityYaw(cam,1),0,1
	 If KeyDown(17) MoveEntity cam,0,0,s
	 If KeyDown(31) MoveEntity cam,0,0,-s
	 If KeyDown(32) MoveEntity cam,s,0,0 
	 If KeyDown(30) MoveEntity cam,-s,0,0
	y# = EntityY(cam,1)
	If y<5 Then y=5
	If y>40 Then y=40
	PositionEntity cam, EntityX(cam,1),y,EntityZ(cam,1)
	MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
End Function

Function DeltaTime#()
	Return DeltaTimeK
End Function