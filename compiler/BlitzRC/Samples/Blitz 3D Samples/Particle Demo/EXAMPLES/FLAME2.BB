; flame


;create cloud planes (ripped straight out of Marks examples)
tex=LoadTexture( "cloud_2.bmp",3 )
ScaleTexture tex,100,100
p=CreatePlane()
EntityTexture p,tex
EntityFX p,1
PositionEntity p,0,8,0
p=CopyEntity( p )
RotateEntity p,0,0,180
EntityColor p,50,50,255



;cool dragon model!
dtex=LoadTexture( "model\dragon.bmp" )
dragon=LoadMD2( "model\dragon.md2" )
EntityTexture dragon,dtex
PositionEntity dragon,2,3,0
ScaleEntity dragon,.05,.05,.05
TurnEntity dragon,0,90,0

AnimateMD2 dragon,1,.05,0,40
AnimateMD2 dragon,1,.05,46,54


active = 6
ec = 1
While (Not KeyDown(1)) And active > 0

	;TurnEntity piv,0,1,0
	If Int(MD2AnimTime(dragon)) = 46 Then
		If pe.peEmitter = Null Then
			active = active - 1
			pe.peEmitter = pecreateEntityEmitter(-.75,2.5,0,spark)
			peSetemitterRadius(pe,0,0,0,0,0,0)
			pesetemittercone(pe,-10,10,140,180,0.3)
			peAddvector(pe,0,-0.005,0)
			peseteffectors(pe,1)
	
			For j = 10 To 255 Step 10
				peAddColor(pe,j,j/2,0)
			Next
			
			peSetAutoEmitter(pe,3,1,10)
			peActivateEmitter(pe)
			

			If active = 1 Then
				pe2.peEmitter = pecreateEntityEmitter(4.75,2.5,0,spark)
				peSetemitterRadius(pe2,0,1,0,1,0,1)
				pesetemittercone(pe2,-10,10,0,40,0.01)
	;			peAddvector(pe2,0,-0.005,0)
				peseteffectors(pe2,1)
		
				For j = 10 To 155 Step 5
					peAddColor(pe2,0,j,0)
				Next
				
				peSetAutoEmitter(pe2,6,1,5)
				peActivateEmitter(pe2)
			End If
			
			
			
		End If
	End If

	pc = peProcessParticles()
	peCleanupParticles()
	ec = peProcessEmitters()
	UpdateWorld
	RenderWorld 
	If active > 1 Then
		Text 0,0,"Dragon Breath"	
	Else 
		Text 0,0,"Dragon Breath (and gas!)"		
	End If
	Flip
	snapshot()

Wend

If pe <> Null Then pedestroyemitter(pe,1)
If pe2 <> Null Then pedestroyemitter(pe2,1)

HideEntity dragon

While KeyDown(1)
Wend