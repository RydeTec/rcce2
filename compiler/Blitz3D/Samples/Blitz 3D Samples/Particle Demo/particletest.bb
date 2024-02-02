
; particle test bed
Include "particle.bb"

;Graphics3D 640,480
Graphics3D 800,600



SetBuffer BackBuffer()

cube	= CreateCube()
ScaleEntity cube,.25,.25,.25

camera = CreateCamera()
MoveEntity camera,0,0,-8
;CameraViewport camera,0,0,500,240

;camera2 = CreateCamera()
;MoveEntity camera2,0,8,0
;CameraViewport camera2,0,240,250,240
;PointEntity camera2,cube

;camera3 = CreateCamera()
;MoveEntity camera3,8,0,0
;CameraViewport camera3,250,240,250,240
;PointEntity camera3,cube

spark=LoadSprite("spark.bmp",1)  
ScaleSprite spark,.3,.3			 
HideEntity spark
;If spark=0 Then End				 
;PositionEntity spark,1,0.2,1
;EntityColor spark,255,0,0

tex = LoadTexture("wall1.png")

cube2 = CreateCube(cube)
ScaleEntity cube2,.2,.2,.2
MoveEntity cube2,2,2,2
EntityShininess cube2,1

light = CreateLight()
RotateEntity light,90,0,0

light2 = CreateLight()

i = 0

plane = CreatePlane(4)
PositionEntity plane,0,-0.4,0
EntityType plane,1
EntityTexture plane,tex
EntityType cube,1

EntityType spark,2
pe.peEmitter = pecreateSpriteEmitter(0,0.2,0,spark)
peSetemitterRadius(pe,0,0,0,0,0,0)

;peAddVector(pe,0,-0.01,0)
peAddVector(pe,0.01,0,0)
;pesetemittercone(pe,-30,30,60,120,0.03)
pesetemittercone(pe,-40,40,50,130,0.03)
pesetlife(pe,60,100)
peseteffectors(pe,1)
peSetRotation(pe,10,10,10,1)
peSetSwirl(pe,0,5,0)
;peAttachParticles(pe,cube)
;peSetEventTrigger(pe,1)
peSetAlpharange(pe,1,1)

;For j = 10 To 255 Step 10
;	peAddColor(pe,j,j,j)
;Next

peSetAutoEmitter(pe,1,1,1)

peactivateemitter(pe)

Collisions 1,2,2,1

wind# = 0
an# = 0
ec = 1

While Not KeyDown(1) And ec > 0
	i = (i + 3) Mod 360
	
		pedeletevectors(pe)
		peDeleteAllEvents()

		If KeyDown(203) Then pemoveEmitter(pe,-0.1, 0.0, 0.0)
		If KeyDown(205) Then pemoveEmitter(pe, 0.1, 0.0, 0.0)
		If KeyDown(200) Then an = (an + 10 ) Mod 360
		If KeyDown(208) Then 
			an = (an - 10 )
			If an < 0 Then an = an + 360
		End If
;	pesetemittercone(pe,an-30,an+30,60,120,0.05)


;	PointEntity camera,cube

;	pedeletevectors(pe)
;	peAddVector(pe,0,-0.015,0)
;	peaddvector(pe,wind,0,0)
	
	
;	RotateEntity cube,i,i,i	
	
	If KeyDown(2) Then peCreateParticle(pe)
	ec = peProcessEmitters()
;  TurnEntity cube,0,1,0
	pc = peProcessParticles()
	peCleanupParticles()
	
	ev.peEvent = peNextEvent()
	While ev <> Null
		Select ev\event 
			Case  event_particledie
				DebugLog "F:"+EntityX(ev\pa\entity,False)+","+EntityY(ev\pa\entity,False)+","+EntityZ(ev\pa\entity,False)
				DebugLog "T:"+EntityX(ev\pa\entity,True)+","+EntityY(ev\pa\entity,True)+","+EntityZ(ev\pa\entity,True)
;				pe1.peEmitter = pecreateSpriteEmitter(ev\pa\pos\dx,ev\pa\pos\dy,ev\pa\pos\dz,spark)
				pe1.peEmitter = pecreateSpriteEmitter(0,1,0,spark)
				DebugLog "PF:"+EntityX(pe1\pivot,False)+","+EntityY(pe1\pivot,False)+","+EntityZ(pe1\pivot,False)
				DebugLog "PT:"+EntityX(pe1\pivot,True)+","+EntityY(pe1\pivot,True)+","+EntityZ(pe1\pivot,True)
;				FreeEntity pe1\pivot
;				CopyEntity(ev\pa\entity)
;				HideEntity pe1\pivot
				pesetLife(pe1,10,15)
				peSetemitterRadius(pe1,0,0,0,0,0,0)
				pesetemittercone(pe1,-90,90,0,360,0.1)
				pesetAutoEmitter(pe1,3,1,1)
				peseteffectors(pe1,1)
				For j = 10 To 255 Step 10
						peAddColor(pe1,j,j/2,j/2)
				Next
				peactivateemitter(pe1)
		End Select
		peRemoveEvent(ev)
		ev = peNextEvent()
	Wend	
	

	UpdateWorld
	c1= CountCollisions(plane)	
	c2 =CountCollisions(cube)
	RenderWorld 
	Text 0,0,pc+">>"+CountCollisions(plane)+","+CountCollisions(cube)+"("+ec+")"
	
	
	Flip
	
;	While Not KeyDown(3)
;	Wend
Wend

If pe <> Null Then pedestroyemitter(pe,1)
peDeleteAllEvents()

End