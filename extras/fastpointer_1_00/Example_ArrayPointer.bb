;
; Example of use FastPointer library.
; (c) 2008-2009 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;
; Attention! Functions only for the advanced users, use them very accurately.
; If you do not understand how it works, do not use it!
;



Graphics3D 800,600,0,2
SetFont LoadFont("Tahoma",15)



Dim MyArray(256)



MyArrayPointer = ArrayPointer()
	Goto skip
	MyArray(0)=0
	.skip



Print "Array pointer: $"+Hex(MyArrayPointer)



For i=0 To 15
	MyArray(i) = i+1
Next


MemoryPokeInt (MyArrayPointer, 123456)
MemoryPokeInt (MyArrayPointer + 3 * 4, 654321)


For i=0 To 15
	Print i+": "+MyArray(i)
Next



WaitKey()
End