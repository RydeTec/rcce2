; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;
; Simple shadows - blur shadows with noise (like in STALKER, GTA IV and Crysis games)
;


Include "include\FastExt.bb"					; <<<<   Include FastExtension library
Include "include\ShadowsSimple.bb"			; <<<<<   Include shadow system



Graphics3D 800,600,0,2
AppTitle "   F1 - on\off UpdateShadow;   F2 - debug shadow texture (shadow map)"



InitExt									; <<<<    Initialize FastExtension library after Graphics3D function
TextureAnisotropy
TextureLodBias


CreateShadow 1									; <<<<<   create shadows (with quality=1) and customize his characteristics
ShadowRange 70									; <<<<<   set shadow range (70x70)
ShadowTexture = ShadowTexture()						; <<<<<   get first shadow texture (shadow map) 



Noise = LoadTexture("..\media\bump_noise.png")			; <<<<<  load noise texture and set BUMP blend
TextureBlend Noise, FE_BUMP
ScaleTexture Noise, 0.15, 0.15



FadeOutTexture = LoadTexture("..\media\fade.png", 59)
ShadowFade FadeOutTexture



; create light source
		
LightPivot = CreatePivot()
TurnEntity LightPivot, 0, 180, 0

Light=CreateLight(1, LightPivot)
TurnEntity Light, 60, 0, 0

ShadowLight Light							; <<<<<  set light source for shadows
										;             any entity can be as light source


; create scene objects 

Camera = CreateCamera()
PositionEntity Camera, 0, 10, -10
CameraRange Camera, 0.1, 5000
CameraClsColor Camera,130,150,178
	
For i=1 To 100
	m=CreateCube()
	PositionEntity m, Rnd(-100,100), Rnd(2,12), Rnd(-100,100)
	RotateEntity m, Rnd(-180,180), Rnd(-180,180), Rnd(-180,180)
	EntityColor m, Rand(64,128), Rand(64,128), Rand(64,128)
	CreateShadowCaster  m									; <<<<<  attach mesh as caster to the shadow system
Next
	

Plane = CreatePlane()
EntityColor Plane, 128, 128, 128
ScaleEntity Plane, 6, 1, 6
EntityTexture Plane, LoadTexture("..\media\Panels.jpg"), 0, 0
EntityTexture Plane, Noise, 0, 1									; <<<<<  place noise texture before shadow texture
EntityTexture Plane, ShadowTexture, 0, 2					; <<<<<   place shadow texture to receiver


PointEntity Camera, Plane


debug = 0
update = 1
MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
While Not KeyHit(1)

	; update user input and animation
	MouseLook Camera
	If KeyHit(59) Then update = 1-update
	If KeyHit(60) Then debug = 1-debug
	TurnEntity LightPivot, 0, 0.2*DeltaTime(), 0
	PositionTexture Noise, Rnd(0,1), Rnd(0,1)		; animate NOISE texture if needed
	UpdateWorld
	
	; update shadows and render scene
	If update Then UpdateShadows Camera			; <<<<<  Update Shadow system (use after UpdateWorld function and before RenderWorld function!)
	RenderWorld
	If debug Then DebugShadow					; <<<< can view shadow texture on screen for debug (use after RenderWorld function)

	Color 0,0,0
	Text 10,10,FPS()
	If Not ( Bump() And 1 ) Then
		Color 220,0,0 : Text 10,30,"Environment Bump Mapping ( EMBM ) NOT support on your videocard"
	EndIf
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
	If y<20 Then y=20
	PositionEntity cam, EntityX(cam,1),y,EntityZ(cam,1)
	MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
End Function

Function DeltaTime#()
	Return DeltaTimeK
End Function