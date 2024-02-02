; rain


rain=LoadSprite("rain.bmp",1)  
HideEntity rain
ScaleSprite rain,.1,.5

pe.peEmitter = pecreateEntityEmitter(0,10,15,rain)
peSetemitterRadius(pe,0,15,0,0,0,15)
pesetemittercone(pe,0,0,270,270,0.5)
peAddvector(pe,0.02,0,0)
peAddvector(pe,0,-0.001,0)
peseteffectors(pe,1)

For j = 10 To 55 Step 5
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
	Text 0,0,"Rain (Needs work!)"
	
	Flip
Wend

If pe <> Null Then pedestroyemitter(pe,1)

While KeyDown(1)
Wend