; flame

pe.peEmitter = pecreateEntityEmitter(0,2,0,spark)
peSetemitterRadius(pe,0,.5,0,.5,0,.5)
pesetemittercone(pe,0,0,90,90,0.05)
;peAddvector(pe,0.001,0,0)
peseteffectors(pe,1)

For j = 10 To 255 Step 10
	peAddColor(pe,j,j/2,0)
Next

peSetAutoEmitter(pe,1,1,500)
peActivateEmitter(pe)

ec = 1

xwind# = 0
ywind# = 0
While (Not KeyDown(1)) And ec > 0

	;TurnEntity piv,0,1,0
	
	key = Rnd(198,210)
	If Key=203 Then xwind = xwind - 0.001
	If Key=205 Then xwind = xwind + 0.001
	If key=200 Then ywind = ywind - 0.001
	If Key=208 Then ywind = ywind + 0.001

	If pe <> Null Then
		pedeletevectors(pe)
	;	peAddVector(pe,0,-0.0015,0)
		peaddvector(pe,xwind,0,ywind)
	End If


	pc = peProcessParticles()
	peCleanupParticles()
	ec = peProcessEmitters()
	UpdateWorld
	RenderWorld 
	Text 0,0,"Fire in the wind (Windspeed="+(xwind*100)+","+(ywind*100)+")"	
	Flip
	snapshot()
Wend

If pe <> Null Then pedestroyemitter(pe,1)


While KeyDown(1)
Wend