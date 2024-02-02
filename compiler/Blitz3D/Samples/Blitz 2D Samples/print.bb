; NAME : Richard McLoughlin
; DESC : "Hello World!" Example

; This program simply uses the "Print" command to print alphanumeric text to the screen.


; Prints the text placed in between the quotation marks ("")
Print "Hello World!"

; Text can also be stored in variables (covered elsewhere).
; The thing to note is that these strings can be added together as follows...

; Store two strings
a$="Hello "
b$="World!"

; Now there a two ways we can add these strings...

; By creating a variable, which add a$ and b$ together.
c$=a$+b$
Print c$

; or by adding the strings together on the fly...
Print a$+b$

; Prints the number specified - NOTE quotation marks NOT required.
Print 5

; The "Print" command can also be used with variables, as follows...
a=1001
Print a

; The "Print" command can also be used to perfrom calculations "on the fly".
; That is perform a sum before it actually prints the answer to the screen.
Print 1+2+3

; Brackets must be used to make sure the sum is performed correctly.
; ie. 3*4+5 gives the answer 17 - this is because it performs the sum as 3*4 then + 5.
; To get the correct answer brackets are used as follows....
Print 3*(4+5)


; While the ESC key isn`t being presses...
While Not KeyDown(1)

; Leave the program running
Wend