
; Zone detection: first attempt!
; ------------------------------
; This code is intended to make it simpler to set up zones and check
; whether a pair of co-ordinates (eg. mouse pointer position) fall
; within a given zone.

; I wanted to make it so that it could just be given a pair of co-ordinates and
; return *which* zone was hit.


; -----------------------------------------------------------------------------
; Include this section in your code...
; -----------------------------------------------------------------------------

; Zone definition:

Type Zone
	Field x										; X-position of zone
	Field y										; Y-position of zone
	Field w										; Width of zone
	Field h										; Height of zone
End Type

; Zone creator:

Function CreateZone.Zone (x,y,w,h)
	tz.Zone=New Zone							; Create a zone
	tz\x=x										; X-position
	tz\y=y										; Y-position
	tz\w=w										; Width
	tz\h=h										; Height
	Return tz
End Function

; Zone-hit detector (feed it the x and y co-ordinates to check, plus the zone):

Function InZone (x,y,z.Zone)
	If x>z\x-1 And x<z\x+z\w And y>z\y-1 And y<z\y+z\h
		Return -1
	Else Return 0
	EndIf
End Function

; -----------------------------------------------------------------------------

; Demo:

; -----------------------------------------------------------------------------

; Basic setup crap for demo:

Graphics 640,480								; Set up graphics mode
SetFont LoadFont (Arial, 12)					; Load font for result (naughty way to do it ;)

; Zone representations:

Color 255,255,0	  :	Rect 50,50,150,100	 		; Rectangle (zone 1) on-screen for reference
Color 0,255,0	  :	Rect 250,250,150,100 		; Rectangle (zone 2) on-screen for reference
Color 255,255,255 :	Rect 450,100,150,100 		; Rectangle (zone 3) on-screen for reference

; -----------------------------------------------------------------------------

; Setting up three zones:

Const numzones=3								; Number of zones
Dim z.Zone (3)									; Initialise zones

z(1)=CreateZone (50,50,150,100)					; Create a new zone (x,y,width,height)
z(2)=CreateZone (250,250,150,100)				; Create a new zone (x,y,width,height)
z(3)=CreateZone (450,100,150,100)				; Create a new zone (x,y,width,height)

; -----------------------------------------------------------------------------

; The actual demo code:

While KeyDown(1)=0								; Keep going until [Esc] key is pressed

	Clear()										; Just clears output box

	; Check all zones:

	For a=1 To numzones
		If InZone (MouseX(), MouseY(), z(a))	; In a zone?
			Hit (a)								; Show which one...
		EndIf
	Next

Wend

End

; -----------------------------------------------------------------------------




; Just a crude "show which zone was hit" thing:

Function Hit (a)
	Color 255,0,0:Rect 0,0,50,50
	Color 255,255,255:Text 8,20,"Zone: "+a
	VWait
End Function

; Clear the above:

Function Clear ()
	Color 0,0,0:Rect 0,0,50,50
	Color 0,0,255:Plot MouseX(),MouseY():VWait
End Function