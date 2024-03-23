; Using functions - Part 1
; -------------------------
;

; This series assumes you know the very "basics" of Basic programming (eg. For...Next)!

; At its most basic (ha ha, again...), a function allows you to run a commonly used piece of code.
; For example, this is a (guaranteed 100% useless) function which prints "Hello" to the screen
; whenever you "call" it:

Function PrintHello ()
	Print "Hello"
End Function

; Let's print "Hello" 5 times by calling the PrintHello () function:

For a=1 To 5
	PrintHello ()
Next

; Click on the Run icon (a little rocket; or press F5) to see the result.

; -------------------------------------------------------------------------------------
; Now click on the Close icon (unlit lightbulb) and load "Function Tutorial Part 2.bb"