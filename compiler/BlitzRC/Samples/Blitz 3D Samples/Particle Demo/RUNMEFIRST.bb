; particle heaven, Execute this code to see demo
; (c) Graham Kennedy (blitztastic) 2001
; For updates see www.bigfoot.com/~blitztastic
; or email blitztastic@bigfoot.com

Include "particle.bb"

;Graphics3D 640,480
Graphics3D 800,600
;WBuffer True

SetBuffer BackBuffer()

piv = CreatePivot()

Global snap=0
Global snapcount=0

Function snapshot()
	If KeyDown(2) Then 
		If snap = 0 Then
			snap=1
			SaveBuffer(FrontBuffer(),"screen\screen"+snapcount+".bmp")
			snapcount = snapcount + 1
		End If
	Else
		snap=0
	End If
End Function 


camera = CreateCamera(piv)
MoveEntity camera,0,2,-8
;CameraZoom camera,1

;camera = CreateCamera(piv)
;MoveEntity camera,0,2,-10
;PointEntity camera,piv
;CameraZoom camera,1

;sky = CreateSphere(4)
;ScaleEntity sky,10,10,10
;EntityColor sky,00,00,255



SeedRnd MilliSecs() 


spark=LoadSprite("spark.bmp",1)  
HideEntity spark


tex = LoadTexture("wall1.png")

light = CreateLight()
RotateEntity light,90,0,0


Color 0,0,200
Rect 0,0,640,480
Color 255,255,0

Text 320,100,"Simple demo of the Blitztastic Particle Engine",True
Text 320,120,"This is my first attempt at a particle engine, so some of the",True
Text 320,140,"Examples are a bit rough round the edges. Almost all the effects",True
Text 320,160,"Here are created using a single emitter (with the exception of the",True
Text 320,180,"Dragon, and roman candle.. 2 each). So far I havn't experimented",True
Text 320,200,"With alternate textures, and some other vector effects, but I have",True
Text 320,220,"Had some success with a waterfall, and some smoke/vapour trail effects.",True
Text 320,240,"The next release should allow rotational vectors (for swirly particles)",True
Text 320,260,"And nested emitters, to allow exploding particles (as mentioned on one",True
Text 320,280,"of the boards). Source will also be included",True
Text 320,320,"Any comments, suggestions etc to blitztastic@bigfoot.com",True

Text 320,440,"Hit Escape to continue (escape also jumps you to the next example)",True


si = CreateTexture(640,480)
CopyRect 0,0,640,480,0,0,BackBuffer(),TextureBuffer(si) 
title=CreateCube()
ScaleEntity title,5,3,0.01
EntityTexture title,si

PositionEntity title,1.8,2.2,-5
RenderWorld
Flip

While Not KeyDown(1)
Wend

;Goto skip 

For tz# = -5 To 13 Step 0.05

		TurnEntity title,0,0,1
		PositionEntity title,1.8,2.2,tz

	RenderWorld
	
	Flip

Next

For tz# = 1 To 0 Step -0.005

	EntityAlpha title,tz
	RenderWorld
	
	Flip

Next


.skip

camera2 = CreateCamera(piv)
MoveEntity camera2,0,8,0
CameraViewport camera2,700,0,100,100
PointEntity camera2,piv
;CameraZoom camera,1


plane = CreatePlane(4)
PositionEntity plane,0,0,0
EntityTexture plane,tex
EntityOrder plane,1

HideEntity title

;End

For tz# = 0 To 1 Step 0.005

	EntityAlpha plane,tz
	RenderWorld
	
	Flip

Next


While KeyDown(1)
Wend


Include "examples/explode2.bb"
Include "examples/powerup.bb"
Include "examples/fragments.bb"
Include "examples/halo1.bb"
Include "examples/explode1.bb"
Include "examples/radial.bb"
Include "examples/flame1.bb"
Include "examples/snow1.bb"
Include "examples/marsh1.bb"
Include "examples/flame2.bb"

MoveEntity camera,0,0,-10
Include "examples/rain.bb"
Include "examples/roman.bb"



End