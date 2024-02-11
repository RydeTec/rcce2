; flame

pe.peEmitter = pecreateEntityEmitter(0,0,0,spark)
peSetemitterRadius(pe,0,0,0,6,0,0)
pesetemittercone(pe,0,0,90,90,.3)
peAddvector(pe,0.010,0,0)
peaddvector(pe,0,-0.01,0)
peseteffectors(pe,1)


For j = 10 To 255 Step 10
	peAddColor(pe,j,j,0)
Next

peSetAutoEmitter(pe,6,2,250)
peActivateEmitter(pe)


pe2.peEmitter = pecreateEntityEmitter(0,0,0,spark)
peSetemitterRadius(pe2,0,0,0,0,0,0)
pesetemittercone(pe2,-20,20,70,110,.5)
peAddvector(pe2,0.005,0,0)
;peaddvector(pe,0,-0.01,0)
peseteffectors(pe2,1)

peSetAutoEmitter(pe2,1,20,25)
peActivateEmitter(pe2)

ec = 1
While (Not KeyDown(1)) And ec > 0

;	TurnEntity piv,0,1,0

	pc = peProcessParticles()
	peCleanupParticles()
	ec = peProcessEmitters()
	UpdateWorld
	RenderWorld 
	Text 0,0,"Roman Candle "	
	Flip
		snapshot()

Wend

If pe <> Null Then pedestroyemitter(pe,1)
If pe2 <> Null Then pedestroyemitter(pe2,1)


While KeyDown(1)
Wend