; initial Blitz3D Particle engine
; bugfixed, commented, improved version available soon
; see www.btinternet.com/~gakennedy


Include "vector3d.bb"
Include "peEvents.bb"

Type peEmitter
	Field pivot		  ; internal pivot to attach particles to
	Field state			; state of this emitter 0=inactive, 1=active, 9=dieing
	Field pos.vector3d	; current position
	Field base.vector3d	; base direction
	Field stheta#,etheta#			; x range for emissions
	Field sphi#,ephi#				; y range for emissions
	Field scalar#				; cone scale
	Field irx#,iry#,irz# 	; Inner radius of emissions
	Field orx#,ory#,orz# 	; outer radius of emissions
	Field etype					; type of emitter (sprite or entity)
	Field Vectors.peVectorList ; list of vectors to apply to child particles
	Field cvect.vector3d ; Final vector calculated from all of above vectors
	Field useSwirl			 ; swirl flag
	Field svect.vector3d ; swirl vector
	Field wvect.vector3d ; swirl work vector
	Field sscale.vector3d ; swirl scale vector
	Field avect.vector3d ; Current Swirl Angles
	Field particleentity	; Entity to be used creating new particles
	Field baseentity		; entity to anchor all particles to
	Field particleCount	; Number of particles created from emitter
	Field colorset			; pointer to a buffer containing valid colors
	Field Effectors
	Field rot.vector3d	; ROTATION factor for particles
	Field rotstyle			; style of rotation, 0 = syncronised, 1=randomstart,2=randomstep
	
	Field autoCount,autoEvery,autoparticles,autoDuration ; auto particle generation functionality
	Field minlife,maxlife
	
	Field alpha_min#,alpha_max#,alpha_diff#,alpha_delta ; alpha change control
	
	Field Events 				; entities should trigger events 1=particle die, 2=particle create, 4=
End Type

Type peVectorList
	Field v.vector3d	; vector
	Field nxtVector.peVectorList ; next vector in chain
End Type

Type peParticle
	Field pos.vector3d	; Current position
	Field dir.vector3d  ; Current Direction
	Field rot.vector3d	; Current rotation (for spinning particles)
	Field entity				; entity to display (could wrap many of the above into this)
	Field	life#,alivetime#		; countdown timer until death
	Field deathaction 	; action when particle dies
	Field parent.peEmitter ; Emitter that owns this particle
	Field r,g,b					; particle color
	Field Effectors
End Type

Function peCreateSpriteEmitter.peEmitter(x#,y#,z#,e)
	Return pecreateemitter(x,y,z,0,e)
End Function

Function peCreateEntityEmitter.peEmitter(x#,y#,z#,e)
	Return pecreateemitter(x,y,z,1,e)
End Function

Function peCreateEmitter.peEmitter(x#,y#,z#,etype,entity)
	pe.peemitter = New peEmitter
	pe\pivot = CreatePivot()
	PositionEntity pe\pivot,x,y,z
	
;	DebugLog "x="+EntityX(pe\pivot)+" y="+EntityY(pe\pivot)+" z="+EntityZ(pe\pivot)
;	pe\pos = Vector3d_create(x,y,z)
	pe\pos = Vector3d_create(0,0,0)
	pe\cvect = vector3d_create(0,0,0)
	pe\svect = vector3d_create(0,0,0)
	pe\wvect = vector3d_create(0,0,0)
	pe\sscale = vector3d_create(0,0,0)
	pe\avect = vector3d_create(0,0,0)
	pe\rot = vector3d_create(0,0,0)
	pe\stheta = -90
	pe\etheta = 90
	pe\sphi = 0
	pe\ephi = 360
	pe\scalar = 1
	pe\irx = 1
	pe\iry = 1
	pe\irz = 1
	pe\etype = etype
	pe\particleentity = entity
	pe\colorset = CreateBank(1024) 
	pe\minlife = 100
	pe\maxlife = 100
	peSetAlphaRange(pe,1,0.5)
	
	Return pe
End Function

Function peDestroyEmitter(pe.peEmitter,immediate)

	pe\state = 9
	
	If immediate Or pe\particlecount = 0 Then

;		Stop

		Delete pe\pos
		Delete pe\cvect
		Delete pe\rot
		Delete pe\svect
		Delete pe\wvect
		Delete pe\avect
		Delete pe\sscale
	
		FreeBank pe\colorset

		; remove all vectors associated with this emitter
		peDeleteVectors(pe)
;		peDeleteEmitterEvents(pe)

		; remove all particles associated with this emitter
		For pa.peParticle = Each peParticle
			If pa\parent = pe Then peDestroyParticle(pa)
		Next	

		FreeEntity pe\pivot

		Delete pe
		
	End If
	
End Function

Function peProcessEmitters()
	pc = 0
	For pe.peEmitter = Each peEmitter

		If pe\useswirl Then				
			TurnEntity pe\pivot,pe\svect\dx,pe\svect\dy,pe\svect\dz
		End If

		pc = pc + 1
		If pe\state = 9 Then 
			peDestroyEmitter(pe,0)
		Else
			If pe\state = 1 Then
			
			
				If pe\autoduration > 0 Then
					pe\autocount = pe\autocount - 1
					If pe\autocount = 0 Then
						pe\autocount = pe\autoevery
						For i = 1 To pe\autoparticles
							peCreateParticle(pe)
						Next		
						pe\autoduration = pe\autoduration - 1
						If pe\autoduration <= 0 Then 
							peDestroyEmitter(pe,0)
						End If
					End If
				End If
			End If
		End If
	Next
	
	Return pc
End Function



Function peActivateEmitter(pe.peemitter)
	pe\state = 1
End Function

Function peDeactivateEmitter(pe.peEmitter)
	pe\state = 0
End Function

Function peAddColor(pe.peEmitter,r,g,b)
	os = PeekByte(pe\colorset,0)
	If os < 255 Then
		PokeByte pe\colorset,os*3+1,(r And 255)
		PokeByte pe\colorset,os*3+2,(g And 255)
		PokeByte pe\colorset,os*3+3,(b And 255)
	
		os = os + 1
		PokeByte pe\colorset,0,os
	End If

End Function

Function peAttachParticles(pe.peEmitter,entity)
	pe\baseentity = entity
	EntityParent pe\pivot,entity
End Function

Function peMoveEmitter(pe.peEmitter,dx#,dy#,dz#)
;	PositionEntity pe\pivot,EntityX(pe\pivot)+dx,EntityY(pe\pivot)+dy,EntityZ(pe\pivot)+dz
	MoveEntity pe\pivot,dx,dy,dz
;	DebugLog "x="+EntityX(pe\pivot)+" y="+EntityY(pe\pivot)+" z="+EntityZ(pe\pivot)
End Function

Function peSetEmitterRadius(pe.peEmitter,irx#,orx#,iry#,ory#,irz#,orz#)
	pe\irx = irx
	pe\iry = iry
	pe\irz = irz
	pe\orx = orx
	pe\ory = ory
	pe\orz = orz
End Function

Function peSetAlphaRange(pe.peEmitter,startalpha#,endalpha#)
	pe\alpha_min = startalpha
	pe\alpha_max = endalpha
	
	pe\alpha_delta = Sgn(startalpha-endalpha)
	pe\alpha_diff	= Abs(endalpha-startalpha)
	
End Function

Function peSetEmitterCone(pe.peEmitter,stheta#,etheta#,sphi#,ephi#,scale#)
	pe\stheta = stheta
	pe\sphi = sphi
	pe\etheta = etheta
	pe\ephi = ephi
	pe\scalar = scale
End Function

Function peSetEffectors(pe.peEmitter,value)
	pe\effectors = value
	
End Function

Function peSetLife(pe.peemitter,minlife,maxlife)
	pe\minlife = minlife
	pe\maxlife = maxlife
End Function

Function peSetEventTrigger(pe.peEmitter,events)
	pe\events = events
End Function 

Function peSetAutoEmitter(pe.peEmitter,particles,every,duration)
	pe\autoparticles = particles
	pe\autoevery = every
	pe\autocount = 1
	pe\autoduration = duration
End Function


Function peSetRotation(pe.peEmitter,rx#,ry#,rz#,style)
		pe\rot\dx = rx
		pe\rot\dy = ry
		pe\rot\dz = rz
		pe\rotstyle = style

End Function

Function peSetSwirl(pe.peEmitter,rx#,ry#,rz#)
		If rx = 0 And ry = 0 And rz = 0 Then
			pe\useswirl = 0
		Else 
			pe\useswirl = 1
			pe\svect\dx = rx
			pe\svect\dy = ry
			pe\svect\dz = rz
		End If
End Function

Function peCreateVector.peVectorList(vx#,vy#,vz#)
	vl.peVectorList = New peVectorList
	vl\v = vector3d_create(vx,vy,vz)
	Return vl
End Function

Function peAddVector(pe.peEmitter,vx#,vy#,vz#)
	vl.peVectorList = New peVectorList
	vl\v = vector3d_create(vx,vy,vz)
	vl\nxtvector = pe\vectors
	pe\vectors = vl

	; recalculate compound vector
	
	vector3d_copy(vl\v,pe\cvect)
	
	v1.peVectorList = vl
	While v1\nxtvector <> Null  
		v1 = v1\nxtvector
		vector3d_add(pe\cvect,v1\v,pe\cvect)
	Wend

End Function

Function peDeleteVectors(pe.peEmitter)
	vl.peVectorList = pe\vectors
	While vl <> Null
		Delete vl\v
		v1.peVectorList = vl\nxtvector
		Delete vl
		vl = v1
	Wend
End Function

Function peCreateParticle.peParticle(pe.peEmitter)
 pa.peparticle = Null
If pe\state = 1 Then
	pa.peParticle = New peParticle
	pe\particlecount = pe\particlecount + 1
	
	pa\rot = vector3d_create(0,0,0)
	vector3d_copy(pe\rot,pa\rot)
	pa\dir = vector3d_create(0,0,0)
	pa\pos = vector3d_create(0,0,0)
	pa\effectors = pe\effectors
  theta = Rnd(pe\stheta,pe\etheta)
  phi = Rnd(pe\sphi,pe\ephi)

  pa\dir\dx = ((Cos(theta)) * (Cos(phi)))*pe\scalar
  pa\dir\dy = ((Cos(theta)) * (Sin(phi)))*pe\scalar
  pa\dir\dz = (Sin(theta))*pe\scalar

	cs = PeekByte(pe\colorset,0)
	If cs = 0 Then
		If Rnd(10)< 5 Then  pa\r = 255 Else pa\r = 0
		If Rnd(10)< 5 Then 	pa\g = 255 Else pa\g = 0
		If Rnd(10)< 5 Then 	pa\b = 255 Else pa\b = 0
	Else
		c = Rnd(0,cs)
		pa\r = PeekByte(pe\colorset,c*3+1)
		pa\g = PeekByte(pe\colorset,c*3+2)
		pa\b = PeekByte(pe\colorset,c*3+3)
	End If

	pa\entity = CopyEntity(pe\particleentity,pe\pivot)
	EntityType pa\entity,2
	EntityRadius pa\entity,0.2
	If (pe\rotstyle And 1) Then 
		RotateEntity pa\entity,Rand(360),Rand(360),Rand(360)
	End If
	
	If (pe\rotstyle And 2) Then 
		pa\rot\dx = Rand(pe\rot\dx)
		pa\rot\dy = Rand(pe\rot\dy)
		pa\rot\dz = Rand(pe\rot\dz)
	End If

	If pe\baseentity = 0 Then
;		vector3d_copy(pe\pos,pa\pos)
		vector3d_set(pa\pos,EntityX(pe\pivot),EntityY(pe\pivot),EntityZ(pe\pivot))
	Else
;		EntityParent pa\entity,pe\baseentity
	End If

    ; calculate a start position in an ellipsoid round the emitter

;	DebugLog "before:"+pa\pos\dy
	
	a1# = Rnd(0,360)
	pa\pos\dx = pa\pos\dx + (Cos(a1)*Rnd(pe\irx,pe\orx))
	pa\pos\dz = pa\pos\dz + (Sin(a1)*Rnd(pe\irz,pe\orz))
	a1# = Rnd(0,360)
	pa\pos\dy = pa\pos\dy + (Sin(a1)*Rnd(pe\iry,pe\ory))

;	DebugLog "After:"+pa\pos\dy
	
	
	
;	pa\pos\dx = pa\pos\dx + (Rnd(pe\irx,pe\orx))
;	pa\pos\dy = pa\pos\dy + (Rnd(pe\iry,pe\ory))
;	pa\pos\dz = pa\pos\dz + (Rnd(pe\irz,pe\orz))
	
	pa\life=Rand(pe\minlife,pe\maxlife)
;	DebugLog pa\life
	pa\alivetime=pa\life
	EntityColor pa\entity,pa\r,pa\g,pa\b
	ShowEntity pa\entity
	pa\parent = pe
	
	peMoveParticle(pa)
;	PositionEntity pa\entity,pa\pos\dx,pa\pos\dy,pa\pos\dz
	
 End If

 Return pa

End Function

Function peDestroyParticle(pa.peParticle)

	pa\parent\particlecount = pa\parent\particlecount - 1

	Delete pa\rot 
	Delete pa\dir 
	Delete pa\pos 

	FreeEntity pa\entity
	Delete pa
End Function

Function peRecalculateParticle(pa.peParticle)

	vector3d_add(pa\dir,pa\parent\cvect,pa\dir)
;	pa\dir\dx = pa\dir\dx + pa\parent\cvect\dx
;	pa\dir\dy = pa\dir\dy + pa\parent\cvect\dy
;	pa\dir\dz = pa\dir\dz + pa\parent\cvect\dz
	
;  DebugLog pa\dir\dx+","+pa\dir\dy+","+pa\dir\dz


End Function

Function peMoveParticle(pa.peParticle)
	; temp function for testing fade

	vector3d_add(pa\pos,pa\dir,pa\pos)	
;	If pa\parent\useswirl Then vector3d_add(pa\pos,pa\parent\wvect,pa\pos)

;	pa\pos\dx = pa\pos\dx + pa\dir\dx
;	pa\pos\dy = pa\pos\dy + pa\dir\dy
;	pa\pos\dz = pa\pos\dz + pa\dir\dz
;	MoveEntity pa\entity,pa\dir\dx,pa\dir\dy,pa\dir\dz

;	pa\pos\dx = pa\pos\dx - (EntityX(pa\parent\pivot)-pa\parent\pos\dx)
;	pa\pos\dy = pa\pos\dy - (pa\parent\pos\dy-EntityY(pa\parent\pivot))
;	pa\pos\dz = pa\pos\dz - (pa\parent\pos\dz-EntityZ(pa\parent\pivot))


;	DebugLog "Ex="+EntityX(pa\parent\pivot)+" ox="+pa\parent\pos\dx+" px="+pa\pos\dx+" epx="+EntityX(pa\entity)
	ox# = (pa\parent\pos\dx-EntityX(pa\parent\pivot))
	oy# = (pa\parent\pos\dy-EntityY(pa\parent\pivot))
	oz# = (pa\parent\pos\dz-EntityZ(pa\parent\pivot))
	PositionEntity pa\entity,pa\pos\dx+ox,pa\pos\dy+oy,pa\pos\dz+oz
	If pa\parent\etype <> 0 Then peEffectorRotate(pa)

End Function

Function peDegradeParticle(pa.peParticle)
	pa\alivetime = pa\alivetime - 1
End Function

; global functions to operate on all particles
Function peProcessParticles()
	pc = 0
	For pa.peParticle = Each peParticle
		pc = pc + 1
		peRecalculateParticle(pa)
		peMoveParticle(pa)
		peDegradeParticle(pa)
		If (pa\Effectors And 1) Then peeffectoralpha(pa)
		If (pa\Effectors And 2) Then peeffectorscale(pa)
	Next
	
	Return pc

End Function




Function peCleanupParticles()
	For pa.peParticle = Each peParticle

		Select pa\alivetime 	
			Case 1
				If pa\parent\events And 1 Then 
					peAddEvent(pa\parent,pa,event_particledie)
				End If
			Case 0
				peDestroyParticle(pa)
			
		End Select
	
	Next
	
End Function

Function peEffectorAlpha(pa.peparticle)
	If pa\alivetime > 0 Then
		prog# = (pa\alivetime / pa\life)*pa\parent\alpha_diff*pa\parent\alpha_delta
;		DebugLog pa\parent\alpha_min+","+pa\parent\alpha_max+","+pa\parent\alpha_diff+"("+prog+")"
;		EntityAlpha pa\entity, pa\parent\alpha_min+prog
		EntityAlpha pa\entity,(pa\alivetime / pa\life)
	End If
End Function

Function peEffectorScale(pa.peparticle)

	If pa\alivetime > 0 Then
		If pa\parent\etype =1 Then
			ScaleEntity pa\entity,(pa\alivetime / pa\life),(pa\alivetime / pa\life),(pa\alivetime / pa\life)
		Else
			ScaleSprite pa\entity,(pa\alivetime / pa\life),(pa\alivetime / pa\life)
		End If
	End If

End Function

Function peEffectorRotate(pa.peparticle)
	TurnEntity pa\entity,pa\rot\dx,pa\rot\dy,pa\rot\dz
End Function