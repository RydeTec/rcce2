; Using functions - Part 4
; -------------------------
; 

; When a function is returning a value like that, we can use it as if it were any
; other variable. Sticking to string variables and using the same function as Part 3:

; The function:
Function JoinString$ (a$)
	Return "You passed: "+a$
End Function

; Here's what we did in Part 3:

; mysentence$=JoinString ("Hello, I'm gonna be printed.")
; Print mysentence$

; Because we made the function a string type (via the "$" after the name JoinString), it returned
; the joined string into mysentence$. But JoinString () IS effectively a string itself, because
; it's been defined with "$". So we can do things like this:

Print JoinString ("Hello, I'm gonna be printed.")

; See? We don't even have to grab it as a separate string variable before printing! We only
; really need to do this if we want to KEEP using the same returned string without having to
; call the function each time.

; A more complex use (uncomment it by removing the ; sign):

; Print Len (JoinString ("Hello, I'm gonna be printed."))

; This will print the length, in characters, of the resulting string!

; A more complex one: try using Replace$ to modify the "You passed: " part of the
; returned string.

; [ Definition for your reference: Replace$ (string$,oldstring$,newstring$) ]

; Some clues: string$ is the string we're changing (in this case, JoinString (whatever)),
; "oldstring$" will be the "You passed" part, and new string is whatever you want to
; replace "You passed" with.

; Careful with those brackets, and don't worry if you find this one quite difficult, because
; it's fairly complex anyway! The point to understand is that a function of a certain type
; can be used like any other variable of that type, so eg. a string function can be used
; directly as if it were a string itself.

; Uncomment the line below (remove the ;) and go for it! Scroll right down for the solution.

; Print Replace$ (xxxx,xxxx,xxxx)

; -------------------------------------------------------------------------------------
; Now click on the Close icon (unlit lightbulb) and load "Function Tutorial Part 5.bb"























; Solution to last problem
; -------------------------

; This replaces "You passed" with "This is what you said". Uncomment and run it:

; Print Replace$ (JoinString ("Hello, I'm gonna be printed."),"You passed","This is what you said")