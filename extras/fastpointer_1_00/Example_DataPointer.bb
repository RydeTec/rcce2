;
; Example of use FastPointer library.
; (c) 2008-2009 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;
; Attention! Functions only for the advanced users, use them very accurately.
; If you do not understand how it works, do not use it!
;



Graphics3D 800,600,0,2
SetFont LoadFont("Tahoma",15)



MyDataPointer = DataPointer()
	Goto skip
	Restore DataLabel
	.skip



Print "Data pointer: $"+Hex(MyDataPointer)
Print "Data type: "+Str( MemoryPeekInt (MyDataPointer) )+" & value: "+Str( MemoryPeekInt (MyDataPointer+4) )
Print "Data value: "+Str( MemoryPeekInt (MyDataPointer+8) )+" & value: "+Str( MemoryPeekFloat (MyDataPointer+12) )
WaitKey()
End



;
; data format in memory:
; 	- data type (integer = 4 bytes) (1-Int, 2-Float, 4-string)
;	- data value or pointer to null-terminated string, 4 bytes
;

.DataLabel
	Data 12345, 2.95, 3, 4, 5