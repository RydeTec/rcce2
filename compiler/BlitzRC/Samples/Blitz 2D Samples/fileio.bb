
;write and read a binary file...
Print "Binary file test"
test=WriteFile( "test" )

For k=1 To 3
	WriteByte test,k
	WriteShort test,k
	WriteInt test,k
	WriteFloat test,k
Next
WriteString test,"Mark Sibly!"
CloseFile test

test=ReadFile( "test" )
For k=1 To 3
	Print ReadByte( test )
	Print ReadShort( test )
	Print ReadInt( test )
	Print ReadFloat( test )
Next
t$=ReadString( test )
Print t$+" "+Len( t$ )
CloseFile test

;write and read a text file...
Print "Text file test"
test=WriteFile( "test" )
WriteLine test,"Line 1"
WriteLine test,4/2
WriteLine test,10.5
CloseFile test

test=ReadFile( "test" )
While Not Eof(test)
	Print ReadLine$( test )
Wend
CloseFile test