; Particle event queue

Const Event_ParticleDie  	=900001

Type peEvent
	Field pa.peParticle
	Field pe.PeEmitter
	Field event
End Type


Function peDeleteEmitterEvents(pe.peEmitter)
	For we.peEvent = Each peEvent
		If we\pe = pe Then 
			peRemoveEvent(we)
		End If
	Next
End Function

Function peDeleteAllEvents()
	For we.peEvent = Each peEvent
		peRemoveEvent(we)
	Next
End Function 



Function PeAddEvent(pe.peEmitter,pa.peParticle,event)
	we.peEvent = New peEvent
	we\pa = pa
	we\pe = pe
	we\event = event
End Function

Function peRemoveEvent(we.peEvent)
	Delete we
End Function

Function peNextEvent.peEvent()
	Return First peEvent
End Function
