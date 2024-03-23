;---------------------
;Particle Manager v1.0 
;          A.Gore 2001
;---------------------
;
;	Quick Start:
;		1) Include this file (after the Graphics3D command)
;		2) Add PM_Update(active_camera) to your main loop
;		3) Use PM_AddEmitter( t,x#,y#,z#,loop,parent ) to spawn particles
;			t 		= template to use (see the bottom of this file)
;			x,y,z 	= positon, or offset from parent
;			loop	= true/false loop emitter until destroyed
;			parent 	= optional entity parent (0 = no parent)
;		4) When freeing an entity that has an emitter parented to it, use PM_FreeEmitter first
;
;	Making New Emitter Templates:
;		1) Add new data at the bottom of this file (see other templates for what they do)
;		2) Increase max_templates constant, if the number of templates exceeds it
;
;-----------------------------------------------------------------------------------------
;VARIBLES	
;-----------------------------------------------------------------------------------------

Const max_particles = 200;max number of particles per emitter
Const max_templates = 6	;remember to up this when creating new templates

Type Particle
 Field entity			;entity
 Field x#,y#,z#			;position
 Field vx#,vy#,vz#		;velocity vector
 Field r,g,b 			;colour
 Field age 				;age
End Type

Type Emitter
 Field x#,y#,z#			;position the particles are emitted from
 Field parent			;optional parent entity
 Field loop				;true - particles are reset after destroyed
 Field templateid		;the template to get particle characteristics from
 Field particle.Particle[max_particles-1]	;the particles
End Type

Type Template			;stores list of emitter templates
 Field sprite			;sprite entity
 Field viewmode			;1=faces camera, 4=independant of camera but matches yaw
 Field gravity#			;amount of gravity applied to y velocity
 Field minage#,maxage#	;age at which the particles are destroyed rnd(min,max)
 Field fade				;true - particles fade away 
 Field nopart 			;number of particles to use (must not be more than max_particles)
 Field minx#,maxx#		;staring position rnd(min,max)
 Field miny#,maxy#		; /
 Field minz#,maxz#		;/
 Field minvx#,maxvx#	;staring vector rnd(min,max)
 Field minvy#,maxvy#	; /
 Field minvz#,maxvz#	;/
 Field colr#,colg#,colb#	;staring colour
 Field colmr#,colmg#,colmb#	;color multiplier
End Type

Global ptemplate.Template[max_templates-1]

PM_ReadTemplates()

;-----------------------------------------------------------------------------------------
;FUNCTIONS	
;-----------------------------------------------------------------------------------------

Function PM_ReadTemplates()

	Local t,filename$,blend,viewmode,xsize#,ysize#,rot#
		
	For t = 0 To max_templates-1
		ptemplate[t] = New Template
		Read filename
		Read blend
		Read ptemplate[t]\viewmode
		Read ptemplate[t]\gravity
		Read ptemplate[t]\minage, ptemplate[t]\maxage
		Read xsize, ysize
		Read ptemplate[t]\fade
		Read ptemplate[t]\colr, ptemplate[t]\colg, ptemplate[t]\colb
		Read ptemplate[t]\colmr, ptemplate[t]\colmg, ptemplate[t]\colmb
		Read ptemplate[t]\nopart
		Read ptemplate[t]\minx, ptemplate[t]\maxx
		Read ptemplate[t]\miny, ptemplate[t]\maxy
		Read ptemplate[t]\minz, ptemplate[t]\maxz
		Read ptemplate[t]\minvx, ptemplate[t]\maxvx
		Read ptemplate[t]\minvy, ptemplate[t]\maxvy
		Read ptemplate[t]\minvz, ptemplate[t]\maxvz
		Read rot
		
		ptemplate[t]\sprite = LoadSprite( filename )	;pre-load all sprites.
		EntityBlend ptemplate[t]\sprite,blend
		If ptemplate[t]\viewmode = 4 Then viewmode = 2 Else viewmode = ptemplate[t]\viewmode
		SpriteViewMode ptemplate[t]\sprite,viewmode
		ScaleSprite ptemplate[t]\sprite,xsize,ysize
		RotateSprite ptemplate[t]\sprite,rot
		HideEntity ptemplate[t]\sprite
	Next
	
End Function

;----------------------------------------
;
;----------------------------------------

Function PM_AddEmitter( t,x#,y#,z#,loop,parent )

	e.Emitter = New Emitter
	e\x = x : e\y = y : e\z = z
	e\parent = parent
	e\loop = loop
	e\templateid = t				

	For i = 0 To ptemplate[t]\nopart-1
		e\particle[i] = New Particle
		e\particle[i]\entity = CopyEntity( ptemplate[t]\sprite )
		PM_ResetParticle( t,e,e\particle[i] )
	Next
	
End Function

;----------------------------------------
;
;----------------------------------------

Function PM_ResetParticle( t,e.Emitter,p.Particle )

	p\x = e\x + Rnd(ptemplate[t]\minx, ptemplate[t]\maxx)
	p\y = e\y + Rnd(ptemplate[t]\miny, ptemplate[t]\maxy)
	p\z = e\z + Rnd(ptemplate[t]\minz, ptemplate[t]\maxz)
	
	If e\parent <> 0
		p\x = p\x + EntityX(e\parent,1) 
		p\y = p\y + EntityY(e\parent,1) 
		p\z = p\z + EntityZ(e\parent,1) 
	EndIf
	
	p\vx = Rnd(ptemplate[t]\minvx, ptemplate[t]\maxvx)
	p\vy = Rnd(ptemplate[t]\minvy, ptemplate[t]\maxvy)
	p\vz = Rnd(ptemplate[t]\minvz, ptemplate[t]\maxvz)
	p\r = ptemplate[t]\colr : p\g = ptemplate[t]\colg : p\b = ptemplate[t]\colb
	p\age = 0
	
	HideEntity p\entity
	ResetEntity p\entity
		
End Function

;----------------------------------------
;
;----------------------------------------

Function PM_Update(cam)

	;update all particles in emitters
	;delete emitter if all particles dead

	Local i,t,alive
	
	For e.Emitter = Each Emitter
	
		t = e\templateid
		alive = False
		
		For i = 0 To ptemplate[t]\nopart-1
			
			If e\particle[i]\age < Rnd(ptemplate[t]\minage, ptemplate[t]\maxage)
			
				alive = True
				
				e\particle[i]\age = e\particle[i]\age + 1

				e\particle[i]\vy = e\particle[i]\vy - ptemplate[t]\gravity
				
				e\particle[i]\x = e\particle[i]\x + e\particle[i]\vx
				e\particle[i]\y = e\particle[i]\y + e\particle[i]\vy
				e\particle[i]\z = e\particle[i]\z + e\particle[i]\vz
				
				e\particle[i]\r = e\particle[i]\r + ptemplate[t]\colmr
				e\particle[i]\g = e\particle[i]\g + ptemplate[t]\colmg
				e\particle[i]\b = e\particle[i]\b + ptemplate[t]\colmb

				PositionEntity e\particle[i]\entity, e\particle[i]\x, e\particle[i]\y, e\particle[i]\z,True
				
				EntityColor e\particle[i]\entity, e\particle[i]\r, e\particle[i]\g, e\particle[i]\b
				
				If ptemplate[t]\viewmode = 4 Then
					RotateEntity e\particle[i]\entity,0,EntityYaw(cam,1),0,True				
				EndIf
				
				If ptemplate[t]\fade = True
					EntityAlpha e\particle[i]\entity, (1 - (1/ptemplate[t]\maxage) * e\particle[i]\age)
				EndIf
				
				If e\particle[i]\age = 1 Then ShowEntity e\particle[i]\entity	;particle just spawned
				
			Else	;particle is dead

				HideEntity e\particle[i]\entity
				
				If e\loop = True
					PM_ResetParticle( t,e,e\particle[i] )
					alive = True
				EndIf
				
			EndIf
			
		Next
	
		If alive = False	;if all particles in emitter are dead then delete
			For i = 0 To ptemplate[ e\templateid ]\nopart-1
				FreeEntity e\particle[i]\entity
				Delete e\particle[i]
			Next
			Delete e		
		EndIf
	
	Next
	

End Function

;----------------------------------------
;
;----------------------------------------

Function PM_FreeEmitter( entity )

	;frees all emitters and particles attached to entity

	For e.Emitter = Each Emitter
		If e\parent = entity Then e\parent = 0 : e\loop = 0
	Next
	
End Function

;----------------------------------------
;
;----------------------------------------

Function PM_FreeAll()

	;frees all emitters and particles
	
	For e.Emitter = Each Emitter
		t = e\templateid
		For i = 0 To ptemplate[t]\nopart-1
			FreeEntity e\particle[i]\entity
			Delete e\particle[i]
		Next
		Delete e
	Next	

End Function

;-----------------------------------------------------------------------------------------
;TEMPLATE DATA
;-----------------------------------------------------------------------------------------

.sparks0

Data "Particle1.bmp"	;filename
Data 3					;blendmode	1-alpha 2-multiply 3-add
Data 1					;viewmode 1=faces camera, 4=independant of camera except yaw
Data 0					;gravity
Data 30,30				;max age rnd(min,max)
Data 2,2				;sprite size
Data True				;fade true/false
Data 255,255,0			;start color r g b
Data 0,-5,0				;color mutiplier r g b (endcol = colrgb + (colmrgb * maxage))
Data 30					;number of particles to use
Data 0,0				;x starting min max position offset
Data 0,0				;y
Data 0,0				;z
Data -2,2				;vx starting min max velocity 
Data -2,2				;vy
Data -2,2				;vz
Data 0					;rotation of particles

.rain1

Data "Particle1.bmp"	;filename
Data 3					;blendmode	1-alpha 2-multiply 3-add
Data 4					;viewmode 1=faces camera, 4=independant of camera except yaw
Data 1					;gravity
Data 10,20				;max age rnd(min,max)
Data .3,10				;sprite size
Data 0					;fade true/false
Data 255,255,255		;color r g b
Data 10,0,0				;color mutiplier r g b
Data max_particles		;number of particles to use
Data -250,250			;x starting min max position offset
Data 0,0				;y
Data -250,250			;z
Data 0,0				;vx starting min max velocity 
Data -8,-8				;vy
Data 0,0				;vz
Data 0					;rotation of particles

.fire2

Data "Particle1.bmp"	;filename
Data 3					;blendmode	1-alpha 2-multiply 3-add
Data 4					;viewmode 1=faces camera, 4=independant of camera except yaw
Data 0					;gravity
Data 10,50				;max age
Data 1,4				;sprite size
Data True				;fade true/false
Data 255,255,0			;color r g b
Data -5,-10,0			;color mutiplier r g b
Data 16					;number of particles to use
Data -.5,.5				;x starting min max position offset
Data -.5,.5;0,0			;y
Data -.5,.5;0,0			;z
Data -.05,.05			;vx starting min max velocity 
Data .2,.3				;vy
Data -.05,.05			;vz
Data 0					;rotation of particles

.magic3

Data "Particle1.bmp"	;filename
Data 3					;blendmode	1-alpha 2-multiply 3-add
Data 1					;viewmode 1=faces camera, 4=independant of camera except yaw
Data 0					;gravity
Data 250,50				;max age rnd(min,max)
Data 4,4				;sprite size
Data True				;fade true/false
Data 0,255,0			;color r g b
Data 0,0,0				;color mutiplier r g b
Data max_particles/2	;number of particles to use
Data -5,5				;x starting min max position offset
Data -5,5				;y
Data -5,5				;z
Data -.1,.1				;vx starting min max velocity 
Data -.1,.1				;vy
Data -.1,.1				;vz
Data 0					;rotation of particles

.fountain4

Data "Particle1.bmp"	;filename
Data 3					;blendmode	1-alpha 2-multiply 3-add
Data 1					;viewmode 1=faces camera, 4=independant of camera except yaw
Data .2					;gravity
Data 25,50				;max age rnd(min,max)
Data 5,5				;sprite size
Data True				;fade true/false
Data 255,255,0			;color r g b
Data 0,-20,10			;color mutiplier r g b
Data max_particles		;number of particles to use
Data 0,0				;x starting min max position offset
Data -12,-12			;y
Data 0,0				;z
Data -1.5,1.5			;vx starting min max velocity 
Data 4,6				;vy
Data -1.5,1.5			;vz
Data 0					;rotation of particles

.firesparks5

Data "Particle1.bmp"	;filename
Data 3					;blendmode	1-alpha 2-multiply 3-add
Data 1					;viewmode 1=faces camera, 4=independant of camera except yaw
Data .001				;gravity
Data 20,100				;max age rnd(min,max)
Data .1,.1				;sprite size
Data True				;fade true/false
Data 255,128,0			;color r g b
Data 0,0,0				;color mutiplier r g b
Data 4					;number of particles to use
Data -.5,.5				;x starting min max position offset
Data 0,0				;y
Data -.5,.5				;z
Data -.1,.1				;vx starting min max velocity 
Data .1,.2				;vy
Data -.1,.1				;vz
Data 0					;rotation of particles