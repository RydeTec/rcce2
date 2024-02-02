; Using functions - Part 3
; -------------------------
; 

; OK, so that's a very simple function call, where a function basically acted like
; any other command.

; Now we'll look at another way to call functions. We can have them perform a calculation
; and "return" a value to us.

; The function:
Function JoinString$ (a$)
	Return "You passed: "+a$
End Function

; Again, we "pass" a string to the function, but this time, we store what the function
; returns:

mysentence$=JoinString ("Hello, I'm gonna be printed.")
Print mysentence$

; Run the program as before.

; Right, what happens here?

; First of all, looking at the function itself, we know we're returning a string from the
; function (it's joining "You passed: " onto whatever string you pass to it), so we add
; a $ (string) sign to the name of the function ("JoinString"), which gives us "JoinString$".

; The "Return" statement passes the joined string back to where we called it. Remember,
; this is why we added a "$" to the name; we're returning this STRING.

; Calling the function, we simply pass whatever string we want, and it's received into
; our string variable (in this case, "mysentence$"). So mysentence$ becomes "You passed: Hello,
; I'm gonna be printed." once we call the function.

; Some exercises:

; Try changing the name of the variable "mysentence$" to something of your own, eg. b$,
; something$, whatever. Note that you'll have to change it in the Print statement too! Run it.

; Change the "You passed: " string within the function to something else, and change
; the string you're passing ("Hello, I'm gonna be printed"). Run it. Try a few different things.

; -------------------------------------------------------------------------------------
; Now click on the Close icon (unlit lightbulb) and load "Function Tutorial Part 4.bb"