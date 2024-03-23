; space explode


frag = CreateCube()
ScaleEntity frag,0.1,0.1,0.001
EntityShininess frag,1
HideEntity frag

pe.peEmitter = pecreateEntityEmitter(0,4,0,frag)
peSetemitterRadius(pe,0,0,0,0,0,0)
pesetemittercone(pe,-90,90,0,360,0.05)
pesetrotation(pe,1,1,1,3)
pesetlife(pe,100,200)
;peAddvector(pe,0.001,0,0)
peAddvector(pe,0,-0.001,0)
;peseteffectors(pe,2)

For j = 10 To 255 Step 10
	peAddColor(pe,j,j,j)
Next

peSetAutoEmitter(pe,50,100,3)
peActivateEmitter(pe)

ec = 1
While (Not KeyDown(1)) And ec > 0

;	TurnEntity piv,0,1,0

	pc = peProcessParticles()
	peCleanupParticles()
	ec = peProcessEmitters()

	UpdateWorld
	RenderWorld 
	Text 0,0,"Exploding Fragments"
	
	Flip
	snapshot()

Wend

If pe <> Null Then pedestroyemitter(pe,1)

While KeyDown(1)
Wend