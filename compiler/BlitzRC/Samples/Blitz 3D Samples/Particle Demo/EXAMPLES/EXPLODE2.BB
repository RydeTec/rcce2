; ground explosion

pe.peEmitter = pecreateEntityEmitter(0,1,0,spark)
peSetemitterRadius(pe,0,0,0,0,0,0)
pesetemittercone(pe,-80,80,10,170,0.35)
;peAddvector(pe,0.001,0,0)
peAddvector(pe,0,-0.01,0)
peseteffectors(pe,1)

For j = 10 To 255 Step 10
	peAddColor(pe,j,j/2,0)
Next

peSetAutoEmitter(pe,50,100,3)
peActivateEmitter(pe)

ec = 1
While (Not KeyDown(1)) And ec > 0

	;TurnEntity piv,1,0,0

	pc = peProcessParticles()
	peCleanupParticles()
	ec = peProcessEmitters()
	UpdateWorld
	RenderWorld 
	Text 0,0,"Ground Explosion"
	
	Flip
	snapshot()

Wend

If pe <> Null Then pedestroyemitter(pe,1)

While KeyDown(1)
Wend