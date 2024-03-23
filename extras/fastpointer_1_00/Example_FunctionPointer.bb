;
; Example of use FastPointer library.
; (c) 2008-2009 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;
; Attention! Functions only for the advanced users, use them very accurately.
; If you do not understand how it works, do not use it!
;



Graphics3D 800,600,0,2
SetFont LoadFont("Tahoma",15)



MyFunctionFirstPointer = FunctionPointer()
	Goto skip
	MyFunctionFirst()
	.skip
	
	
	
MyFunctionSecondPointer = FunctionPointer()
	Goto skip1
	MyFunctionSecond(0)
	.skip1



Print "First function pointer: $"+Hex(MyFunctionFirstPointer)
Print "Second function pointer: $"+Hex(MyFunctionSecondPointer)



CallFunction (MyFunctionFirstPointer)
CallFunctionVarInt (MyFunctionSecondPointer,12345)



WaitKey()
End



Function MyFunctionFirst ()
	Print "MyFunctionFirst called!"
End Function


Function MyFunctionSecond (variable)
	Print "MyFunctionSecond called with variable: "+Str(variable)
End Function
