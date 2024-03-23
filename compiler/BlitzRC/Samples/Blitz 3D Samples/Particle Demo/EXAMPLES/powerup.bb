; powering up particles

pe.peEmitter = pecreateEntityEmitter(0,0,0,spark)
peSetemitterRadius(pe,1,1,0,0,1,1)
pesetemittercone(pe,-10,10,80,100,0.05)
peseteffectors(pe,1)
peSetSwirl(pe,0,5,0)
peSetAlpharange(pe,0.5,0)
peSetlife(pe,100,100)



;For j = 10 To 255 Step 10
;	peAddColor(pe,j,j/2,j/4)
;Next

peSetAutoEmitter(pe,3,5,30)
peActivateEmitter(pe)

ec = 1
While (Not KeyDown(1)) And ec > 0

	;TurnEntity piv,0,1,0

	pc = peProcessParticles()
	peCleanupParticles()
	ec = peProcessEmitters()

	UpdateWorld
	RenderWorld 
	Text 0,0,"I got a power up!"
	
	Flip
	snapshot()
	
Wend

If pe <> Null Then pedestroyemitter(pe,1)

While KeyDown(1)
Wend