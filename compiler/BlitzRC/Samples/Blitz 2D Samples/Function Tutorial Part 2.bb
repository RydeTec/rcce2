; Using functions - Part 2
; -------------------------
; 

; OK, now we'll modify the function so that it'll print whatever we want, which can be
; different each time we call it.

; The function:
Function PrintSomething (a$)
	Print a$
End Function

; In this case, we "pass" a string (either a string variable such as blah$, or a piece of
; text enclosed in quotes, eg. "Hello there") to the function:

PrintSomething ("Hello, I'm gonna be printed.")
PrintSomething ("So am I.")

; Using a string variable:

sentence$="This is also being printed to the screen."
PrintSomething (sentence$)

; So, whatever is put within the brackets () when you call it is "passed"
; to the function.

; If you look at the function itself, you'll see that it takes the form "PrintSomething (a$)",
; which means it's expecting you to pass a string variable, as we've done above
; (note that "a$" could be named anything at all -- "b$"; "sometext$"; whatever).

; Look inside the function, and it takes whatever is passed into its "a$" parameter and
; uses it in the Print call ("Print a$").

; As an exercise, try changing "a$" to "b$". Make sure you change "a$" to "b$" all
; throughout the function, or it won't work!

; Do that before continuing, then run it (press F5 or the Run icon).

; -------------------------------------------------------------------------------------------------

; Here's what you should have ended up with:

; Function PrintSomething (b$)
; 	Print b$
; End Function

; Now try changing the string to something of your own choosing (as long as
; it ends with the $ string sign!).

; Click on the Run icon (or press F5) to see the result.

; -------------------------------------------------------------------------------------
; Now click on the Close icon (unlit lightbulb) and load "Function Tutorial Part 3.bb"