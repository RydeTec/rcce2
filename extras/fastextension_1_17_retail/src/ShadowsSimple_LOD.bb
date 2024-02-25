; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;
; Use low poly meshes for high poly shadow casters and increase FPS (frames per second)
;


Include "include\FastExt.bb"					; <<<<   Include FastExtension library
Include "include\ShadowsSimple.bb"			; <<<<<   Include shadow system



Graphics3D 800,600,0,2
AppTitle "   F1 - on\off UpdateShadow;   F2 - debug shadow texture (shadow map)"



InitExt									; <<<<    Initialize FastExtension library after Graphics3D function



CreateShadow 1									; <<<<<   create shadows (with quality=1) and customize his characteristics
ShadowRange 6									; <<<<<   set shadow range (70x70)
ShadowPower 0.6
ShadowTexture = ShadowTexture()						; <<<<<   get first shadow texture (shadow map) 


; create light source
LightPivot = CreatePivot()
TurnEntity LightPivot, 0, 180, 0
Light=CreateLight(1, LightPivot)
TurnEntity Light, 60, 0, 0

ShadowLight Light							; <<<<<  set light source for shadows
										;             any entity can be as light source

; create caster
HighPolyMesh = CreateSphere(64)				;   create high-poly mesh - 48384 triangles
LowPolyMesh = CreateSphere(8, HighPolyMesh)	;   create low-poly mesh - 224 triangles, and set high-poly parent for low-poly mesh
CreateShadowCaster LowPolyMesh, 1			; <<<<<  attach LOD mesh as caster to the shadow system and set autoShow flag



PositionEntity HighPolyMesh, 0, 2, 0


; create receiver	
m=CreatePlane()
ScaleEntity m, 2, 1, 2
EntityColor m, 160, 155, 145
EntityTexture m, LoadTexture("..\media\Panels.jpg"), 0, 0
EntityTexture m, ShadowTexture, 0, 1								; <<<<<  place shadow textures to entity (entity will be a receiver)


; create camera
Camera = CreateCamera()
PositionEntity Camera, 0, 3.5, -3.5
CameraRange Camera, 0.1, 5000
CameraClsColor Camera,130,150,178
PointEntity Camera, HighPolyMesh


debug = 0
update = 1
MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
While Not KeyHit(1)

	; update user input and animation
	MouseLook Camera
	If KeyHit(59) Then update = 1-update
	If KeyHit(60) Then debug = 1-debug
	TurnEntity LightPivot, 0, 0.2*DeltaTime(), 0
	UpdateWorld
	
	; update shadows and render scene
	If update Then UpdateShadows Camera			; <<<<<  Update Shadow system (use after UpdateWorld function and before RenderWorld function!)
	RenderWorld
	If debug Then DebugShadow					; <<<< can view shadow texture on screen for debug (use after RenderWorld function)

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
	s#=0.01*dk
	dx#=(GraphicsWidth()/2-MouseX())*0.2
	dy#=(GraphicsHeight()/2-MouseY())*0.2
	TurnEntity cam,-dy,dx,0
	RotateEntity cam,EntityPitch(cam,1),EntityYaw(cam,1),0,1
	 If KeyDown(17) MoveEntity cam,0,0,s
	 If KeyDown(31) MoveEntity cam,0,0,-s
	 If KeyDown(32) MoveEntity cam,s,0,0 
	 If KeyDown(30) MoveEntity cam,-s,0,0
	y# = EntityY(cam,1)
	If y<2 Then y=2
	PositionEntity cam, EntityX(cam,1),y,EntityZ(cam,1)
	MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
End Function

Function DeltaTime#()
	Return DeltaTimeK
End Function