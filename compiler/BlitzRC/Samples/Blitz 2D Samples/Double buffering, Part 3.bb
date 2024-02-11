; Double buffering, Part 3
; ------------------------

; To show how easy it is, look at the two previous examples put together
; to show the minor differences needed. Run it once, then uncomment the
; two marked lines and try it again.

Graphics 640, 480

 SetBuffer BackBuffer ()	; *** UNCOMMENT to enable double buffering ***

For a = 0 To 639			; Step 10 ; Uncomment to speed up movement when double-buffered

	Cls
	Rect a, 100, 10, 10
	Flip					; *** UNCOMMENT to enable double buffering ***
	Delay 1

Next

End

; So, the simple addition of "SetBuffer BackBuffer ()" after setting up our display,
; and the "Flip" command after drawing everything we want on-screen is all it takes!
; --
; Any questions, gimme a shout: james@thesurfaces.net
; --