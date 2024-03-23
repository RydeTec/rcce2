; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;
; Use receivers in shadow system (AttachShadowReceiver function)
;
; If caster is located inwardly receivers or is intersected with receivers
; then use a AttachShadowReceiver function and see example below.
;


Include "include\FastExt.bb"					; <<<<   Include FastExtension library
Include "include\ShadowsSimple.bb"			; <<<<<   Include shadow system


Graphics3D 800,600,0,2
AppTitle "   F1 - on\off UpdateShadow;   F2 - debug shadow texture (shadow map)"


InitExt									; <<<<    Initialize FastExtension library after Graphics3D function


CreateShadow 1							; <<<<<   create shadows (with quality=1) and customize his characteristics
ShadowRange 60							; <<<<<   set shadow range (50x50)
ShadowPower 0.4							; <<<<<   set shadow opacity
ShadowTexture = ShadowTexture()				; <<<<<   get first shadow texture (shadow map) 


; create light
Light = CreateLight()
ShadowLight Light									; <<<<<   set pivot as light source for shadow system (you can use any entity as light source)


; create main camera
Camera = CreateCamera()
CameraRange Camera, 0.1, 5000
CameraClsMode Camera, 0, 0
PositionEntity Camera, 0, 18, -18
PointEntity Camera, Light


; create other meshes
LanscapeTexture = LoadTexture("..\media\Panels.jpg")
ScaleTexture LanscapeTexture, 0.2, 0.2

Landscape = CreateRandomQuad(30)
EntityColor Landscape, 20, 80, 20
ScaleMesh Landscape, 100, 3, 100
EntityTexture Landscape, LanscapeTexture, 0, 0
EntityTexture Landscape, ShadowTexture, 0, 1					; <<<<<   place shadow texture to entity (entity will be a shadow receiver)
AttachShadowReceiver Landscape								; <<<<<   attach entity to shadow system as receiver

m0 = CreateMesh()
EntityColor m0, 128,128,128
For i=1 To 100
	Select Rand(0, 3)
		Case 0
			m=CreateCube()
		Case 1
			m=CreateSphere(8)
		Case 2
			m=CreateCone(8,1)
		Case 3
			m=CreateCylinder(8,1)
		Default
			m=CreateCube()
	End Select
	RotateMesh m, Rnd(-180,180), Rnd(-180,180), Rnd(-180,180)
	s# = Rnd(1,2) : ScaleMesh m, s, s, s
	PositionMesh m, Rnd(-40,40), Rnd(1,5), Rnd(-40,40)
	AddMesh m, m0
	FreeEntity m
Next
CreateShadowCaster  m0								; <<<<<  attach mesh as caster to the shadow system

m=CreateCube()
ScaleMesh m, 2, 10, 2
EntityColor m, 180, 0, 0
CreateShadowCaster  m									; <<<<<  attach mesh as caster to the shadow system


a# = 90
debug = 0
update = 1
MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
While Not KeyHit(1)
	ClsColor 130,150,178 : Cls
	
	; update user input and animation
	MouseLook Camera
	If KeyHit(59) Then update = 1-update
	If KeyHit(60) Then debug = 1-debug
	TurnEntity m, 0, DeltaTime(), 0
	RotateEntity Light, 50, a, 0 : a = a - 0.5*DeltaTime()
	UpdateWorld
	
	; update shadows and render scene
	If update Then UpdateShadows Camera			; <<<<<  update shadow system (use after UpdateWorld function and before RenderWorld function!)
	RenderWorld
	If debug Then DebugShadow					; <<<< can view shadow texture on screen for debug (use after RenderWorld function)

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
	If y<15 Then y=15
	;If y>5 Then y=5
	PositionEntity cam, EntityX(cam,1),y,EntityZ(cam,1)
	MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
End Function

Function DeltaTime#()
	Return DeltaTimeK
End Function

Function CreateRandomQuad(segs#=2,parent=0)
    mesh=CreateMesh( parent )
    surf=CreateSurface( mesh )
    l# =-.5
    b# = -.5
    tvc= 0
    Repeat
      u# = l + .5
      v# = b + .5
      AddVertex surf,l, Rnd(0, 1),b,u,1-v
      tvc=tvc + 1
      l = l + 1/segs
      If l > .501 Then
        l = -.5
        b = b + 1/segs
      End If
    Until b > .5
    vc# =0
    Repeat
      AddTriangle (surf,vc,vc+segs+1,vc+segs+2)
      AddTriangle (surf,vc,vc+segs+2,vc+1)
      vc = vc + 1
      tst# =  ((vc+1) /(segs+1)) -Floor ((vc+1) /(segs+1))
      If (vc > 0) And (tst=0) Then
        vc = vc + 1
      End If
    Until vc=>tvc-segs-2
    UpdateNormals mesh
    Return mesh
End Function