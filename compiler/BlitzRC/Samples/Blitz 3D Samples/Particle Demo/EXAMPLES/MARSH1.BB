; snow

EntityColor plane,0,100,0

pe.peEmitter = pecreateEntityEmitter(0,0,5,spark)
peSetemitterRadius(pe,0,15,0,0,0,15)
pesetemittercone(pe,0,0,90,90,0.05)
;peAddvector(pe,0.001,0,0)
;peAddvector(pe,0,-0.005,0)
peseteffectors(pe,1)

For j = 10 To 155 Step 10
	peAddColor(pe,0,j,0)
Next

peSetAutoEmitter(pe,1,8,200)
peActivateEmitter(pe)

ec = 1
While (Not KeyDown(1)) And ec > 0

;	TurnEntity piv,0,1,0

	pc = peProcessParticles()
	peCleanupParticles()
	ec = peProcessEmitters()

	UpdateWorld
	RenderWorld 
	Text 0,0,"Marsh Gas"
	
	Flip
	snapshot()

Wend

If pe <> Null Then pedestroyemitter(pe,1)

While KeyDown(1)
Wend