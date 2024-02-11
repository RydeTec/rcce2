; Double buffering, Part 1
; ------------------------

; ------------------------------
; Double buffering is easy!
; ------------------------------


; Why do we need double buffering? Run this program by
; pressing F5 or by clicking on the Run icon (the little rocket)...

Graphics 640, 480		; Set display To 640x480

For a = 0 To 639		; X position on the screen

	Cls					; Clear screen each frame
	Rect a, 100, 10, 10	; Draw a square
	Delay 1				; Slow it down so we can see it!

Next

End

; It's highly likely that the square flickered as it crossed the
; screen (if not, you can be sure it does on slower PCs!).
; We cure this by using double "buffering".

;-------------------
; Now load "Double buffering, Part2.bb"...