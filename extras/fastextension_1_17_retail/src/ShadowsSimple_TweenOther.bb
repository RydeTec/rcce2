; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;
; Another Tween + Shadows example
;

Include "include\FastExt.bb"					; <<<<   Include FastExtension library
Include "include\ShadowsSimple.bb"			; <<<<<   Include shadow system



Graphics3D 800, 600, 0, 2
AppTitle "   WSAD key - move;   F1 - on\off UpdateShadow;   F2 - debug shadow texture (shadow map)"



InitExt									; <<<<    Initialize FastExtension library after Graphics3D function

			

CreateShadow 2							; <<<<<   create shadows (with quality=2) and customize his characteristics
Global ShadowTexture = ShadowTexture()		; <<<<<   get first shadow texture (shadow map) 



Global player
Global m
Global Light
Global Camera
Global level
CreateGame()



Global debug = 0
Global update = 1



; tweening const & variables
Const FPS=10
Global period=1000/FPS
Global time=MilliSecs()-period

; main loop with classic tweening
While Not KeyHit(1)

	Repeat  :  elapsed = MilliSecs() - time  :  Until elapsed
	ticks = elapsed / period
	tween# = Float(elapsed Mod period) / Float(period)
	For k=1 To ticks
		time = time + period
		If k = ticks Then CaptureWorld
		UpdateGame
		UpdateWorld
	Next

	If update Then UpdateShadows player, tween			; <<<<<  Update Shadow system (use after UpdateWorld function and before RenderWorld function!)
	RenderWorld tween

	If debug Then DebugShadow
	Color 0, 0, 0
	Text 10, 10, FPS()
	Flip 1
Wend

FreeShadows						; <<<<<  If shadows do not needed then use function FreeShadow (before DeInitExt function)
DeInitExt
End




Function CreateGame()
	player=CreateSphere()
	ScaleEntity player,5, 5, 5
	PositionEntity player, 0, 5, 0
	CreateShadowCaster player			; <<<<<  attach entity as caster
	
	; HideShadowCaster player			; <<<< for bug-fix test only
	
	Light=CreateLight(3, player)
	PositionEntity Light, 0, 8, 0
	RotateEntity Light, 120, 0, 0
	LightRange Light, 90
	LightConeAngles Light, 80, 170
	
	ShadowLight Light, 3, 120			; <<<<<  set spot-light source for shadows
	
	Camera = CreateCamera()
	PositionEntity Camera, 0, 30, 0
	CameraRange Camera, 0.1, 5000
	RotateEntity Camera, 55, 0, 0
	
	m=CreateCube()
	ScaleEntity m,1,8,1
	PositionEntity m, -15, 0, 0
	CreateShadowCaster m				; <<<<<  attach entity as caster
	
	m=CreateCube()
	ScaleEntity m,1,8,1
	PositionEntity m, 15, 0, 0
	CreateShadowCaster m				; <<<<<  attach entity as caster
	
	m=CreateCube()
	ScaleEntity m,1,8,1
	PositionEntity m, 0,0,15
	CreateShadowCaster m				; <<<<<  attach entity as caster
	
	m=CreateCube()
	ScaleEntity m,1,8,1
	PositionEntity m, 0,0,-15
	CreateShadowCaster m				; <<<<<  attach entity as caster
	
	level=CreatePlane()					; <<< Plane not have bounding box and will be checked for visibility by centre position! It's look bad in shadow system!
	AttachShadowReceiver level, 0, 1		; <<< Set showAlways flag, if you need fix it for objects without bounding box props.
	EntityTexture level, ShadowTexture
End Function



Function UpdateGame()
	TurnEntity m, 0,0,5
	If KeyHit(59) Then update = 1-update
	If KeyHit(60) Then debug = 1-debug
	If KeyDown(17) MoveEntity player,0,0,1
	If KeyDown(31) MoveEntity player,0,0,-1
	If KeyDown(32) MoveEntity player,1,0,0 
	If KeyDown(30) MoveEntity player,-1,0,0
	dx#=EntityX(player,True)-EntityX(Camera,True)
	dz#=EntityZ(player,True)-EntityZ(Camera,True)
	TranslateEntity Camera,dx*.1,0,dz*.1-2
End Function



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