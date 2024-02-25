
Include "include\FastExt.bb"						; <<<<   Include FastExtension library
Include "include\ShadowsSimple.bb"				; <<<<<   Include shadow system
Include "include\RenderCubemapFunctions.bb"		; <<<<   see details in include file

Graphics3D 1024, 786, 0, 2
AppTitle "Filax raytrace fake demo (use mouse and WSAD keys for freelook)"
InitExt										; <<< Initialize library immediately after Graphics3D function!

Global Skybox%
Global ShadowMap%

Global AmbientR%
Global AmbientG%
Global AmbientB%

; create main camera
Camera = CreateCamera()
CameraRange Camera, 0.1, 5000
PositionEntity Camera, 0, 3, -5

CameraFogMode camera,1				; enable Fog for camera
CameraFogRange camera,1,20			; set Range (actual for FogMode=1 only)
CameraFogColor camera,150,200,230

; Init world
CreateWorld(Camera,0,128,192)
CreateRayPlane(0,0,0,8,80,80,80,0.2)

; create light
CreateRayLight(15,15,15,70,30,185,255,255)
CreateRayLight(-15,15,-15,70,30,255,255,185)

; Create world
x#=0 : y#=2.3 : z#=0 : r#=1.7
Local Big.tSphere=CreateRayBall(x# ,y# ,z# , r# ,200,255,255,Rnd(0.5,1),1 ) 

For i=0 To 110 Step 15
	x# = Sin(i*3) * 3   :   	y# = 2   :   z# = Cos(i*3) * 3   :   r#=Rnd(0.1,0.5)
	CreateRayBall(x# ,y# ,z# , r# ,Rnd(50,255),Rnd(50,255),Rnd(50,255),Rnd(0.5,1),1) 
Next

PointEntity Camera,Big\entity%

; Glow setup 
Global GlowAlpha# = 0.9
Global GlowDarkPasses = 2
Global GlowBlurPasses = 5
Global GlowBlurRadius# = 0.35
Global GlowQuality = 2
Global GlowColorRed = 255
Global GlowColorGreen = 255
Global GlowColorBlue = 255
Global GlowAlphaTexture = 0

ShadowBlur()							; <<<< enable blur for shadows

MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
While Not KeyHit(1)
	MouseLook Camera
	
	For s.tSphere=Each tSphere
		s\amplitude#=s\amplitude#+0.5
		Form#=s\y+Sin(s\amplitude#)*s\speed#
		PositionEntity s\Entity,s\x#,Form#,s\z#
	Next
	
	UpdateWorld
	UpdateShadows(Camera)
	RefreshRayBall(Camera)
	RenderWorld()
	
	CustomPostprocessGlow GlowAlpha, GlowDarkPasses, GlowBlurPasses, GlowBlurRadius, GlowQuality, GlowColorRed, GlowColorGreen, GlowColorBlue, GlowAlphaTexture
	RenderPostprocess FE_GLOW

	FPS()
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
	Color 0, 0, 0
	Text 10, 10, FPSCount
	Return FPSCount
End Function

Global DeltaTimeK# = 1.0
Global DeltaTimeOld = MilliSecs()

Function DeltaTime#()
	Return DeltaTimeK
End Function



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
	PositionEntity cam, EntityX(cam,1),y,EntityZ(cam,1)
	MoveMouse GraphicsWidth()/2, GraphicsHeight()/2
End Function



Function CreateWorld(cam%,red%,green%,blue%)
	AmbientR%=red%
	AmbientG%=green%
	AmbientB%=blue%

	CreateShadow(2)						
	ShadowRange(15)							
	ShadowPower(0.7)	
	ShadowColor(10,10,10)
	
	AmbientLight AmbientR%,AmbientG%,AmbientB%
	CameraClsColor cam%, AmbientR%/2, AmbientG%/2, AmbientB%/2
	ShadowMap% = ShadowTexture()	

	Skybox%=CreateSphere()
	FlipMesh Skybox% : EntityFX Skybox%,1+8
	ScaleEntity Skybox%,20,20,20
	EntityTexture Skybox%, LoadTexture("..\media\gradient.jpg")
End Function



Type tLight
	Field x#, y#, z#
	Field scale#
	Field red%, green%, blue%	
	Field entity%
End Type

Function CreateRayLight.tLight(x#,y#,z#,pitch#,scale#,red%,green%,blue%)
	Local s.tLight=New tLight
	
	s\x#=x#
	s\y#=y#
	s\z#=z#
	
	s\red%=red%
	s\green%=green%
	s\blue%=blue%
	
	s\scale#=scale#
	
	s\entity = CreateLight(2)
	PositionEntity s\entity,s\x#,s\y#,s\z#
	RotateEntity s\entity, pitch#, 0, 0 
	LightRange s\entity,s\scale#
	
	ShadowLight s\entity
	
	Return s
End Function



Type tPlane
	Field x#, y#, z#
	Field scale#
	Field red%, green%, blue%
	Field specular#, alpha#
	Field entity%, texture%
End Type	

Function CreateRayPlane.tPlane(x#,y#,z#,scale#,red%,green%,blue%,spec#)
	
	Local s.tPlane=New tPlane
	
	s\x#=x#
	s\y#=y#
	s\z#=z#
	
	s\red%=red%
	s\green%=green%
	s\blue%=blue%
	
	s\scale#=scale#
	
	s\specular#=spec#
	s\alpha#=1.0
	
	s\entity = Createsquare(30)
	PositionEntity s\entity,s\x#,s\y#,s\z#
	
	s\texture%=LoadTexture("..\media\checker_large.bmp")
	ScaleTexture s\texture%,0.3,0.3
	
	EntityTexture s\entity , s\texture%,0,0
	EntityTexture s\entity , ShadowMap%, 0, 1	

	;
	; no need, casters are not crossed / intersected with receivers
	;
	; AttachShadowReceiver s\entity
	
	ScaleEntity s\entity,s\scale#,s\scale#,s\scale#
	EntityColor s\entity,s\red%,s\green%,s\blue%
	
	EntityShininess s\entity,s\specular#
	EntityAlpha s\entity,s\alpha#
	
	Return s
	
End Function



Type tSphere
	Field x#, y#, z#
	Field scale#
	Field red%, green%, blue%
	Field specular#, alpha#
	Field entity%, texture%
	Field amplitude#, speed#
End Type

Function CreateRayBall.tSphere(x#,y#,z#,scale#,red%,green%,blue%,spec#,alpha#)
	
	Local s.tSphere=New tSphere
	
	s\x#=x#
	s\y#=y#
	s\z#=z#
	
	s\red%=red%
	s\green%=green%
	s\blue%=blue%
	
	s\scale#=scale#
	
	s\specular#=spec#
	s\alpha#=alpha#
	
	s\entity = CreateSphere(50)
	PositionEntity s\entity,s\x#,s\y#,s\z#
	
	s\texture%=CreateTexture( 256, 256, 1 + 128 + 256 + FE_RENDER + FE_ZRENDER )
	EntityTexture s\entity , s\texture%,0,0
	
	If s\alpha#<1.0 Then
		SetCubeMode s\texture%,3
	Else
		SetCubeMode s\texture%,1
		
	EndIf
	
	ScaleEntity s\entity,s\scale#,s\scale#,s\scale#
	
	CreateShadowCaster s\entity
	
	EntityColor s\entity,s\red%,s\green%,s\blue%
	
	EntityShininess s\entity,s\specular#
	EntityAlpha s\entity,s\alpha#
	
	s\amplitude#=Rnd(-2,2)
	s\speed#=Rnd(-2,2)
	
	Return s
	
End Function



Function RefreshRayBall(cam%)
	For s.tSphere=Each tSphere
		RenderToCubemap  s\entity, cam%, s\texture%,s\red%/4,s\green%/4,s\blue%/4
	Next
End Function



Function Createsquare(segs#=2,parent=0)
    mesh=CreateMesh( parent )
    surf=CreateSurface( mesh )
	
    l# =-.5
    b# = -.5
    tvc= 0
	
    Repeat
		u# = l + .5
		v# = b + .5
		AddVertex surf,l,0,b,u,1-v
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