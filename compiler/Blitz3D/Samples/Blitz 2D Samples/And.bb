; AND example by Richard O' Keeffe
; When using AND all conditions must be true.

Graphics 640,480

var1 = 1
var2 = 2
var3 = 3

Color 234,128,128
Text 0,0, "Actual Values: var1 = " + var1 + "  var2 = " + var2 + "  var3 = " + var3 
Color 255,255,255

; var1 = 1, var2 = 2, var3 = 3 therefore all conditions
; are true.
Text 320,50,"Is var1 = 1 AND var2 = 2 OR var3 = 3 ?",1,1 
If var1 = 1 And var2 = 2 Or var3 = 3
	Text 320,65,"Yes",1,1
Else
	Text 320,65,"No",1,0
EndIf

WaitKey()

; here only 2 conditions are true and one is not
; var2 is not equal to 4 so it returns false
Text 320,100,"Is var1 = 1 AND var2 = 2 OR var3 = 19 ?",1,1 

If var1 = 1 And var2 = 2 Or var3 = 19
	Text 320,115,"Yes",1,1
Else
	Text 320,115,"No",1,0
EndIf

WaitKey()

; here none of the conditions are true so it's false
Text 320,150,"Is var1 = 7 AND var2 = 9 AND var3 = 4 ?",1,1 

If var1 = 7 And var2 = 9 And var3 = 4
	Text 320,165,"Yes",1,1
Else
	Text 320,165,"No",1,0
EndIf

WaitKey()	