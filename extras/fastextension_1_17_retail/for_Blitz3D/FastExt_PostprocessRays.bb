; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;
; Rays postprocess FX example
;


Include "include\FastExt.bb"					; <<<<   Include FastExtension library
Include "include\ShadowsSimple.bb"			; <<<<<   Include shadow system



Graphics3D 800,600,0,2
AppTitle "   Use mouse and WSAD keys for freelook"



InitExt									; <<<<    Initialize FastExtension library after Graphics3D function


;
; create shadows system
;

CreateShadow 1	
ShadowRange 50
ShadowPower 0.5
ShadowColor 180, 200, 255	
ShadowTexture = ShadowTexture()

;
; create scene objects 
;

Camera = CreateCamera()
	RotateEntity Camera, 0, 150, 0
	PositionEntity Camera, 22, -2, 25
	CameraRange Camera, 0.1, 5000
	CameraZoom Camera, 1.4

Sky = LoadSkyBox ( "..\media\sky2" )
	ScaleEntity Sky, 10, 10, 10
	EntityOrder Sky,1

AnimMesh = LoadAnimMesh ("..\media\terror.b3d")
	ScaleEntity AnimMesh, 0.1, 0.1, 0.1
	PositionEntity AnimMesh, -5, -1.7, 5
	Animate AnimMesh, 1, 0.1
	CreateShadowCaster  AnimMesh

Scene = LoadAnimMesh("..\media\scene.b3d")
	objects = GetChild(Scene,1)														
	For i=1 To CountChildren(objects)
		child = GetChild(objects,i)
		name$ = Lower( EntityName(child) )
		If Len(name)>2 Then
			name = Left( name, Len(name)-1 )
			If name="cast" Then
				CreateShadowCaster  child
				EntityColor child,Rand(200,240),Rand(200,240),Rand(200,240)					
			EndIf
			If name="grass" Then
				CreateShadowCaster  child
				EntityColor child,Rand(200,240),Rand(200,240),Rand(200,240)
			EndIf
			If name="receive" Then
				EntityTexture child, ShadowTexture, 0, 2
				AttachShadowReceiver child
			EndIf
		EndIf
	Next

Light = CreateLight()
	PositionEntity Light, -580, 280, -900
	LightColor Light, 255, 247, 223
	PointEntity Light, Sky
	AmbientLight 87, 74, 69

	ShadowLight Light

;
; main loop
;
MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
While Not KeyHit(1)
	
	;
	; update user input, animation and shadows
	;
	MouseLook Camera
	PositionEntity Sky, EntityX(Camera,1), EntityY(Camera,1)-100, EntityZ(Camera,1), 1
	UpdateWorld
	;UpdateShadows Camera
	
	;
	; render scene
	;
	RenderWorld

	;
	; calculate & render Rays postprocess FX
	;
	CameraProject Camera, EntityX(Light,1), EntityY(Light,1), EntityZ(Light,1)
	If ProjectedZ()>0 Then
		TFormVector 0, 0, 1, Camera, Light
		pitch# =  Abs(Sin(VectorPitch (TFormedX(), TFormedY(), TFormedZ())))   :   pitch = pitch*pitch
		yaw# = Abs(Sin(VectorYaw (TFormedX(), TFormedY(), TFormedZ())))   :   yaw = yaw*yaw
		alpha# = (1.0 - pitch) * (1.0 - yaw)
		x# = ProjectedX()/Float(GraphicsWidth())
		y# = ProjectedY()/Float(GraphicsHeight())
		CustomPostprocessRays x, y, 115, 0.8 * alpha, 4, 4, 3, 180, 240, 255		; <<< Customize Rays postprocess FX
		RaysFX = FE_Rays
	Else
		RaysFX = 0
	EndIf
	
	;
	; render all postprocess FX (Rays and other)
	;
	RenderPostprocess RaysFX

	FPS()
	Flip 0
Wend





;
; other auxiliary functions
;

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
	Color 0, 0, 0
	Text 10, 10, FPSCount
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
	If y<-3 Then y=-3
	If y>5 Then y=5
	PositionEntity cam, EntityX(cam,1),y,EntityZ(cam,1)
	MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
End Function

Function DeltaTime#()
	Return DeltaTimeK
End Function

Function LoadSkyBox( file$ )
	m=CreateMesh()
	;front face
	b=LoadBrush( file$+"_FR.jpg",49 )
	s=CreateSurface( m,b )
	AddVertex s,-1,+1,-1,0,0:AddVertex s,+1,+1,-1,1,0
	AddVertex s,+1,-1,-1,1,1:AddVertex s,-1,-1,-1,0,1
	AddTriangle s,0,1,2:AddTriangle s,0,2,3:
	FreeBrush b
	;right face
	b=LoadBrush( file$+"_LF.jpg",49 )
	s=CreateSurface( m,b )
	AddVertex s,+1,+1,-1,0,0:AddVertex s,+1,+1,+1,1,0
	AddVertex s,+1,-1,+1,1,1:AddVertex s,+1,-1,-1,0,1
	AddTriangle s,0,1,2:AddTriangle s,0,2,3
	FreeBrush b
	;back face
	b=LoadBrush( file$+"_BK.jpg",49 )
	s=CreateSurface( m,b )
	AddVertex s,+1,+1,+1,0,0:AddVertex s,-1,+1,+1,1,0
	AddVertex s,-1,-1,+1,1,1:AddVertex s,+1,-1,+1,0,1
	AddTriangle s,0,1,2:AddTriangle s,0,2,3
	FreeBrush b
	;left face
	b=LoadBrush( file$+"_RT.jpg",49 )
	s=CreateSurface( m,b )
	AddVertex s,-1,+1,+1,0,0:AddVertex s,-1,+1,-1,1,0
	AddVertex s,-1,-1,-1,1,1:AddVertex s,-1,-1,+1,0,1
	AddTriangle s,0,1,2:AddTriangle s,0,2,3
	FreeBrush b
	;top face
	b=LoadBrush( file$+"_UP.jpg",49 )
	s=CreateSurface( m,b )
	AddVertex s,-1,+1,+1,0,1:AddVertex s,+1,+1,+1,0,0
	AddVertex s,+1,+1,-1,1,0:AddVertex s,-1,+1,-1,1,1
	AddTriangle s,0,1,2:AddTriangle s,0,2,3
	FreeBrush b
	;bottom face	
	b=LoadBrush( file$+"_DN.jpg",49 )
	s=CreateSurface( m,b )
	AddVertex s,-1,-1,-1,1,0:AddVertex s,+1,-1,-1,1,1
	AddVertex s,+1,-1,+1,0,1:AddVertex s,-1,-1,+1,0,0
	AddTriangle s,0,1,2:AddTriangle s,0,2,3
	FreeBrush b
	ScaleMesh m,100,100,100
	FlipMesh m
	EntityFX m,1
	Return m
End Function