;
; Example of use FastPointer library.
; (c) 2008-2009 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;
; Attention! Functions only for the advanced users, use them very accurately.
; If you do not understand how it works, do not use it!
;


Graphics3D 800,600,0,2
SetFont LoadFont("Tahoma",15)


Type MyType
	Field a%
End Type
Global MyType.MyType = New MyType
MyType\a = 123


MyTypePointer = TypePointer(MyType)


	Print "my Type pointer: $"+Hex(MyTypePointer)
	Print "---"


	Print "old value 'a' from MyType: "+MyType\a
		
	
	; can change values with pointer
	MemoryPokeInt (MyTypePointer,456)
	
	
	Print "new value 'a' for MyType: "+MyType\a
	Print "---"	


WaitKey()
End
