; Double-buffering, Part 2.

; The trick is to draw our rectangle to a hidden "buffer".

; Imagine that your screen is a piece of paper and the hidden
; buffer is another piece of paper behind that; we're going
; to draw onto the piece of paper at the back (the hidden buffer),
; then bring it to the front. While we're showing the front piece
; of paper, we're drawing to the back piece, where no-one can see
; the drawing in progress!

Graphics 640, 480

SetBuffer BackBuffer ()	; Now any drawing commands will use the hidden buffer

For a = 0 To 640

	Cls					; Clear the hidden buffer
	Rect a, 100, 10, 10	; Draw our rectangle onto it
	Flip				; Bring the hidden buffer to the front
	Delay 1				; Slow it down so we can see it!

Next

; Remember, the SetBuffer BackBuffer () command means
; we're ALWAYS drawing to the hidden buffer (that means
; ALL graphics commands, including Cls!).

End

; Much smoother, don'tcha think? :)

; So the method is as simple as this:
; -----------------------------------

; · 	Set up your display with "Graphics width, height"

; · 	Use "SetBuffer BackBuffer ()" to make all graphics commands
; 		work on the hidden buffer.

; Then, in your main loop:
; ------------------

; · 	Call "Cls" to clear the buffer.

; · 	Draw the background (if any).

; · 	Draw your objects (ie. your spaceships or whatever). Note
; 		that where your images overlap, the last drawn item will
; 		appear at the front; this allows you to depth-arrange your
; 		graphics very easily! Just draw them in order, going from
; 		the background of the picture to the foreground.

; · 	Call "Flip" to see the results.

; · 	Go back to the start of the loop.

; That's it!

; -----------------------------------------
; Now load "Double buffering, Part 3.bb"...