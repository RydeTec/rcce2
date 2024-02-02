; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;
; Simple shadows - spot light example
;


Include "include\FastExt.bb"					; <<<<   Include FastExtension library
Include "include\ShadowsSimple.bb"			; <<<<<   Include shadow system



Graphics3D 800,600,0,2
AppTitle "   SPACE - change fadeOut texture;   F1 - on\off UpdateShadow;   F2 - debug shadow texture (shadow map)"



InitExt									; <<<<    Initialize FastExtension library after Graphics3D function



CreateShadow 1									; <<<<<   create shadows (with quality=1) and customize his characteristics
ShadowTexture = ShadowTexture()						; <<<<<   get first shadow texture (shadow map) 
ShadowPower 0.4



; create light source
		
LightPivot = CreatePivot()

Light=CreateLight(3, LightPivot)
	MoveEntity Light, 0, 20, -20
	PointEntity Light, LightPivot
	LightRange Light, 100
	LightConeAngles Light, 0, 90

	
LightWire = CreateCone(4,1, Light)
	PositionMesh LightWire, 0, -1, 0
	ScaleMesh LightWire, 10, 5, 10
	RotateMesh LightWire, 0, 45, 0
	RotateMesh LightWire, -90, 0, 0
	EntityColor LightWire, 90, 90, 0
	EntityFX LightWire, 1+8+16+64
	

ShadowLight Light, 1, 90							; <<<<<  set SPOT-light source for shadows
										;             any entity can be as light source


; create scene objects 

Camera = CreateCamera()
PositionEntity Camera, 0, 20, -40
CameraRange Camera, 0.1, 5000
CameraClsColor Camera,130,150,178



; casters

For i=1 To 30
	m=CreateCube()
	PositionEntity m, Rnd(-50, 50), Rnd(-20,25), Rnd(0,40)
	RotateEntity m, Rnd(-180,180), Rnd(-180,180), Rnd(-180,180)
	EntityColor m, Rand(64,128), Rand(64,128), Rand(64,128)
	CreateShadowCaster  m									; <<<<<  attach mesh as caster to the shadow system
Next

AnimMesh = LoadAnimMesh( "..\media\mak_bump.b3d" )
ScaleEntity AnimMesh, 0.35, 0.35, 0.35
RotateEntity AnimMesh, 0, 60, 0
Animate AnimMesh, 1, .1
CreateShadowCaster AnimMesh								; <<<<<   attach entity to shadow system as caster



; receivers

t = LoadTexture("..\media\Panels.jpg")
ScaleTexture t, 0.1, 0.5

m=CreateCube()
ScaleEntity m, 140, 1, 40
PositionEntity m, 0, -20, 0
EntityColor m, 200, 200, 200
EntityFX m,1
EntityTexture m, t, 0, 0
EntityTexture m, ShadowTexture, 0, 1							; <<<<<  place shadow textures to entity (entity will be a receiver);

m = CreateCube()
ScaleMesh m, 140, 30, 1
PositionEntity m, 0, 0, 40
EntityFX m,1
EntityTexture m, t, 0, 0
EntityTexture m, ShadowTexture, 0, 1


; fade textures
FadeDark = LoadTexture("..\media\fadeBlack.png", 59)			; <<<<<  load FADE-OUT texture and set in shadow system
FadeBatman = LoadTexture("..\media\fadeBatman.png", 59)		; <<<<<  load FADE-OUT texture and set in shadow system


PointEntity Camera, m
CameraClsColor Camera,0,0,0
AmbientLight 0,0,0
ShadowFade FadeDark
ShadowPower 0.8


debug = 0
update = 1
mode = 1
MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
While Not KeyHit(1)

	; update user input and animation
	MouseLook Camera
	If KeyHit(57) Then
		mode = mode+1
		If mode=3 Then mode=0
		Select mode
			Case 0
				ShadowFade 0
				CameraClsColor Camera,130,150,178
				AmbientLight 220,220,220
				ShadowPower 0.4
			Case 1
				ShadowFade FadeDark
				CameraClsColor Camera,0,0,0
				AmbientLight 0,0,0
				ShadowPower 0.8
			Case 2
				ShadowFade FadeBatman
				CameraClsColor Camera,0,0,0
				AmbientLight 0,0,20
				ShadowPower 0.7
		End Select
	EndIf
	If KeyHit(59) Then update = 1-update
	If KeyHit(60) Then debug = 1-debug
	RotateEntity LightPivot, 0, 30 * Sin(0.05*MilliSecs()), 0
	PositionEntity LightPivot, EntityX(LightPivot), EntityY(LightPivot), -2-15 * Cos(0.02*MilliSecs())
	UpdateWorld
	
	; update shadows and render scene
	If update Then UpdateShadows Camera			; <<<<<  Update Shadow system (use after UpdateWorld function and before RenderWorld function!)
	RenderWorld
	If debug Then DebugShadow					; <<<< can view shadow texture on screen for debug (use after RenderWorld function)

	Color 0, 255, 0
	Text 10,10,FPS()
	Flip 0
Wend



FreeShadows						; <<<<<  If shadows do not needed then use function FreeShadow (before DeInitExt function)
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
	s#=0.4*dk
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
	PositionEntity cam, EntityX(cam,1),y,EntityZ(cam,1)
	MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
End Function

Function DeltaTime#()
	Return DeltaTimeK
End Function