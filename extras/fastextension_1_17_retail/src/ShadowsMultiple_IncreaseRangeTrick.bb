; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;
; Increase range trick for shadows
;


Include "include\FastExt.bb"					; <<<<   Include FastExtension library
Include "include\ShadowsMultiple.bb"			; <<<<<   Include shadow system



Graphics3D 800,600,0,2
AppTitle "   F1 - on\off UpdateShadow;   F2 - debug shadow texture (shadow map)"



InitExt								; <<<<    Initialize FastExtension library after Graphics3D function



FirstShadow.Shadow = CreateShadow (1)				; <<<<<   create first shadow (with quality=1) and customize his characteristics
ShadowRange FirstShadow, 45						; <<<<<   set small shadow range (40x40)
FirstShadowTexture = ShadowTexture(FirstShadow)		; <<<<<   get first shadow texture (shadow map) 

SecondShadow.Shadow = CreateShadow (0)				; <<<<<   create second shadow with maximum range and low quality = 0
ShadowRange SecondShadow, 160					; <<<<  and set big shadow range! (160x160)
SecondShadowTexture = ShadowTexture(SecondShadow)



; create light source	
LightPivot = CreatePivot()
TurnEntity LightPivot, 0, 180, 0

FirstLight=CreateLight(1, LightPivot)
TurnEntity FirstLight, 60, 0, 0



ShadowLight FirstShadow, FirstLight				; <<<<<  set single light source for first and second shadows
ShadowLight SecondShadow, FirstLight


; create scene objects 

Camera = CreateCamera()
PositionEntity Camera, 0, 15, -10
CameraRange Camera, 0.1, 5000
CameraClsColor Camera,130,150,178
	
For i=1 To 100
	m=CreateCube()
	PositionEntity m, Rnd(-100,100), Rnd(2,12), Rnd(-100,100)
	RotateEntity m, Rnd(-180,180), Rnd(-180,180), Rnd(-180,180)
	EntityColor m, Rand(64,128), Rand(64,128), Rand(64,128)
	CreateShadowCaster  FirstShadow, m									; <<<<<  attach mesh as caster to the shadow system
	CreateShadowCaster  SecondShadow, m
Next

m=CreatePlane()
ScaleEntity m, 6, 1, 6
EntityTexture m, LoadTexture("..\media\Panels.jpg"), 0, 0

EntityTexture m, FirstShadowTexture, 0, 1								; <<<<<  place shadow textures to entity (entity will be a receiver)
EntityTexture m, SecondShadowTexture, 0, 2


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
	If update Then
	
	
		UpdateShadows Camera			; <<<<<  Update Shadow system (use after UpdateWorld function and before RenderWorld function!)
		
		; clear rectangle in big shadow texture for small shadow texture (exclude shadows intersection)
		SetBuffer TextureBuffer(SecondShadowTexture)
			k# = ShadowRange(FirstShadow) /  ShadowRange(SecondShadow)
			w# = k * (TextureWidth(SecondShadowTexture) - 2.0)
			h# = k * (TextureHeight(SecondShadowTexture) - 2.0)
			x# = (1.0-k) / 2.0 * TextureWidth(SecondShadowTexture)
			y# = (1.0-k) / 2.0 * TextureWidth(SecondShadowTexture)
			Color 255, 255, 255
			Rect x, y, w, h, 1
		SetBuffer BackBuffer()
	
	
	EndIf
	RenderWorld 
	If debug Then DebugShadow(SecondShadow)					; <<<< can view shadow texture on screen for debug (use after RenderWorld function)

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
	If y<15 Then y=15
	PositionEntity cam, EntityX(cam,1),y,EntityZ(cam,1)
	MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
End Function

Function DeltaTime#()
	Return DeltaTimeK
End Function