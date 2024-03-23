;
; Example of use FastPointer library.
; (c) 2008-2009 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;
; Attention! Functions only for the advanced users, use them very accurately.
; If you do not understand how it works, do not use it!
;



Graphics3D 800,600,0,2
SetFont LoadFont("Tahoma",15)



MyFirstLabelPointer = LabelPointer()
	Goto skip
	Goto MyFirstLabel
	.skip
	
MySecondLabelPointer = LabelPointer()
	Goto skip1
	Goto MySecondLabel
	.skip1



GotoPointer MyFirstLabelPointer
.ret0
	Print "First label pointer: $"+Hex(MyFirstLabelPointer)
	Print "---"


GotoPointer MySecondLabelPointer
.ret1
	Print "Second label pointer: $"+Hex(MySecondLabelPointer)
	Print "---"


WaitKey()
End



.MyFirstLabel
	Print "First!"
	Goto ret0


.MySecondLabel
	Print "Second!"
	Goto ret1
	