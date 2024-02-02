; snow

pe.peEmitter = pecreateEntityEmitter(0,10,5,spark)
peSetemitterRadius(pe,0,15,0,0,0,15)
pesetemittercone(pe,0,0,270,270,0.05)
peAddvector(pe,0.001,0,0)
peAddvector(pe,0,-0.001,0)
peseteffectors(pe,1)

For j = 10 To 155 Step 10
	peAddColor(pe,j,j,j)
Next

peSetAutoEmitter(pe,1,1,1000)
peActivateEmitter(pe)

ec = 1
While (Not KeyDown(1)) And ec > 0

;	TurnEntity piv,0,1,0

	pc = peProcessParticles()
	peCleanupParticles()
	ec = peProcessEmitters()

	UpdateWorld
	RenderWorld 
	Text 0,0,"Snowy"
	
	Flip
	snapshot()

Wend

If pe <> Null Then pedestroyemitter(pe,1)

While KeyDown(1)
Wend