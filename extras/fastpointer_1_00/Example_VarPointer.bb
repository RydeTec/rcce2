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


MyVarPointer = VarPointer()
	Goto skip
	MyVar = 0
	.skip


	Print "my Var pointer: $"+Hex(MyVarPointer)
	Print "---"


	Print "old value for MyVar: "+MyVar
		
	
	; can change Var value with pointer
	MemoryPokeInt (MyVarPointer,998877)
	
	
	Print "new value for MyVar: "+MyVar
	Print "---"	


WaitKey()
End
