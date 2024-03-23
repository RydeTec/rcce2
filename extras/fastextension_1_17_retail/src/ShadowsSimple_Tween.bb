; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;
; Tween + Shadows example
;


Include "include\FastExt.bb"					; <<<<   Include FastExtension library
Include "include\ShadowsSimple.bb"			; <<<<<   Include shadow system


Graphics3D 800,600,0,2
InitExt									; <<<<    Initialize FastExtension library after Graphics3D function


CreateShadow	1									; <<<<<   create shadows (with quality=0) and customize his characteristics
ShadowTexture = ShadowTexture()						; <<<<<   get shadow texture (shadow map)
ShadowPower 0.4
ShadowRange 60


Pivot = CreatePivot()  :  TurnEntity Pivot, 45, 45, 0
ShadowLight Pivot									; <<<<<   set pivot as light source for shadow system (you can use any entity as light source)


; create main scene objects
Global Light = CreateLight()  :  TurnEntity Light, 45, 45, 0
Global Camera = CreateCamera()  :  CameraZoom Camera, 1.5
PositionEntity Camera, 0, 34, -27  :  PointEntity Camera, Light


; create shadow casters
Dim Meshes(8)
For i=0To 7
	Meshes(i) = CreateCube()
	CreateShadowCaster Meshes(i)						; <<<<<   attach entity to shadow system as caster
Next

AnimMesh = LoadAnimMesh( "..\media\mak_bump.b3d" )
ScaleEntity AnimMesh, 0.25, 0.25, 0.25
PositionEntity AnimMesh, 0, 0.8, 0
Animate AnimMesh, 1, 1
CreateShadowCaster AnimMesh							; <<<<<   attach entity to shadow system as caster


; create shadow receivers
Plane = CreatePlane()
EntityColor Plane, 128, 128, 128
ScaleEntity Plane, 6, 1, 6
EntityTexture Plane, LoadTexture("..\media\Panels.jpg"), 0, 0
EntityTexture Plane, ShadowTexture, 0, 1					; <<<<<   place shadow texture to receiver


; update objects function & variables
Global r# = 10
Global da# = 0
Global a# = 0
Function UpdateObjects()
	r = 12 + 6*Cos(da)
	For i=0To 7
		a# = da# + Float(i)*360.0/8.0
		PositionEntity Meshes(i), r * Cos(a), 4.5 + 3 * Sin(a*0.5), r * Sin(a)
		TurnEntity Meshes(i), 0.5, 0.7, 1.3
	Next
	da = da + 3
	MoveEntity Camera, 0.1, 0, 0
	PointEntity Camera, Light, 0
End Function


; tweening const & variables
Const FPS=30
period=1000/FPS
time=MilliSecs()-period


; main loop with classic tweening
While Not KeyHit(1)

	Repeat  :  elapsed = MilliSecs() - time  :  Until elapsed
	ticks = elapsed / period
	tween# = Float(elapsed Mod period) / Float(period)
	For k=1 To ticks
		time = time + period
		If k = ticks Then CaptureWorld
		UpdateObjects
		UpdateWorld
	Next
	UpdateShadows Camera, tween#				; <<<<<  update shadow system (shadow map)
	RenderWorld tween#

	Text 10, 10, FPS()
	Flip 0
Wend








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