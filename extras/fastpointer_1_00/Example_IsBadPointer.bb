;
; Example of use FastPointer library.
; (c) 2008-2009 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;
; Attention! Functions only for the advanced users, use them very accurately.
; If you do not understand how it works, do not use it!
;



Graphics3D 800,600,0,2
SetFont LoadFont("Tahoma",15)



Global MyVar = 123456



; bad pointer for test
BadPointer = 1



; Var pointer for test
TestPointer = VarPointer()
	Goto skip
	MyVar = 0
	.skip




If IsBadWritePointer (BadPointer, 4) Then				; Bytes = 4 - for integer & float vars;  2 - for short vars;  1 - for byte vars
	Print "Can't write to pointer: $"+Hex(BadPointer)
EndIf

If IsBadReadPointer (BadPointer, 4) Then					; Bytes = 4 - for integer & float vars;  2 - for short vars;  1 - for byte vars
	Print "Can't read from pointer: $"+Hex(BadPointer)
EndIf




If IsBadWritePointer (TestPointer, 4) Then
	Print "Can't write to pointer: $"+Hex(TestPointer)
Else
	Print "Success! I can write to pointer: $"+Hex(TestPointer)
EndIf

If IsBadReadPointer (TestPointer, 4) Then
	Print "Can't read from pointer: $"+Hex(TestPointer)
Else
	Print "Success! I can read from pointer: $"+Hex(TestPointer)
EndIf




WaitKey()
End
