; halo explosion

; ground explosion

HideEntity plane

pe.peEmitter = pecreateEntityEmitter(0,1,0,spark)
peSetemitterRadius(pe,0,0,0,0,0,0)
pesetemittercone(pe,0,360,40,40,0.1)
;peAddvector(pe,0.001,0,0)
;peAddvector(pe,0,-0.01,0)
peseteffectors(pe,1)

For j = 10 To 255 Step 10
	peAddColor(pe,j,j/2,0)
Next

peSetAutoEmitter(pe,50,102,6)
peActivateEmitter(pe)

ec = 1
While (Not KeyDown(1)) And ec > 0

	;TurnEntity piv,1,0,0

	pc = peProcessParticles()
	
	If pe <> Null Then
		pc = pe\particlecount
		If pe\particlecount = 0 Then
		
			an = Rnd(360)
			pesetemittercone(pe,an,360+an,an,an,0.075)
		End If
	
	End If
	peCleanupParticles()
	ec = peProcessEmitters()
	UpdateWorld
	RenderWorld 
	Text 0,0,"Halo Explosion "
	
	Flip
		snapshot()

Wend

If pe <> Null Then pedestroyemitter(pe,1)

While KeyDown(1)
Wend

ShowEntity plane