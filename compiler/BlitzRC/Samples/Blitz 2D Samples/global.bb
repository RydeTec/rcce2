; "global", "local" and "constant" variable type example by Dion Guy

; Declare a variable as global for use anywhere in the program.
Global hello$
; Store something in that variable.
hello$="Hello there!"

; Declare a variable as constant and store something in it.
; Unlike other variables, what you store in constants never changes.
; These variables can also be used anywhere in the program.
Const number=25

; All other variables are defined as LOCAL by default.
; If you define a variable inside a function, what you store in it
; will only be recognised inside that function - likewise if you
; define a variable just in the main program, any function will not
; be able to access it's contents.
; You can define local variables using the LOCAL command, but it isn't strictly nescessary.
; Declare 'test' as a variable (local by default) and assign it something.
test=100 ; The line "Local test=100" does the same thing.

; Declare a function.
Function testfunction()
; Define 'value' as a local variable (local to this function) and assign it something.
value=50

; Print the contents of the various variables as they are inside this function.
Print "The contents of global variable 'hello$' inside this function = "+hello$
Print "The contents of constant variable 'number' inside this function = "+number
Print "The contents of local variable 'value' inside this function = "+value
Print "The contents of local variable 'test' inside this function = "+test
End Function

; Print the contents of the various variables as they are normally.
Print "The contents of global variable hello$ outside of a function = "+hello$
Print "The contents of constant variable 'number' outside of a function = "+number
Print "The contents of local variable 'value' outside of a function = "+value
Print "The contents of local variable 'test' outside of a function = "+test

; Print a blank line.
Print

; Call the function 'testfunction'.
testfunction()

; Print a blank line and exit message.
Print
Print "Press Escape to exit"

; Repeat loop indefinitely until the escape key (key 1) is pressed, then end program.
Repeat
Until KeyDown(1)=True
End