; OR example by Richard O' Keeffe
; When using OR at least one condition must be true.

Graphics 640,480

var1 = 1
var2 = 2

; Print the variable values to the screen 
Color 234,128,128
Text 0,0, "Actual Values: var1 = " + var1 + "  var2 = " + var2 + "  var3 = " + var3 
Color 255,255,255

; with OR at least one of the conditions must be true
; if both are true then its true
; if one is true then it's true
; if both are false then it's false
Text 320,50,"Is var1 = 1 OR var2 = 2 ?",1,1 
If var1 = 1 Or var2 = 2 
	Text 320,65,"Yes",1,1
Else
	Text 320,65,"No",1,0
EndIf

WaitKey()

; here one condition is true and the other is false
; so a true value is returned  
Text 320,100,"Is var1 = 1 OR var2 = 19 ?",1,1 

If var1 = 1 Or var2 = 19 
	Text 320,115,"Yes",1,1
Else
	Text 320,115,"No",1,0
EndIf

WaitKey()

; here both conditions are false so the condition returns false
Text 320,150,"Is var1 = 16 OR var2 = 19 ?",1,1 

If var1 = 16 Or var2 = 19 
	Text 320,165,"Yes",1,1
Else
	Text 320,165,"No",1,0
EndIf

WaitKey()	