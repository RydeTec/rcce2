
;
;b3d file utils to be included
;

Dim b3d_stack(100)
Global b3d_file,b3d_tos

Function b3dSetFile( file )
	b3d_tos=0
	b3d_file=file
End Function

;***** functions for reading from B3D files *****

Function b3dReadByte()
	Return ReadByte( b3d_file )
End Function

Function b3dReadInt()
	Return ReadInt( b3d_file )
End Function

Function b3dReadFloat#()
	Return ReadFloat( b3d_file )
End Function

; Reads a NUL-terminated string with a hard upper bound. A corrupted or
; tampered .b3d with no NUL terminator before EOF would otherwise grow t$
; one byte at a time -- Blitz string concatenation is O(N) per concat, so
; an unbounded loop is O(N^2) and can stall the loader for minutes on a
; megabyte-class string of garbage. 4096 bytes covers any realistic mesh
; tag, material name, or animation name in a legitimate .b3d.
Function b3dReadString$()
	Local t$
	Local n
	Repeat
		ch=b3dReadByte()
		If ch=0 Return t$
		t$=t$+Chr$(ch)
		n=n+1
		If n >= 4096 Return t$
	Forever
End Function

Function b3dReadChunk$()
	For k=1 To 4
		tag$=tag$+Chr$(b3dReadByte())
	Next
	sz=ReadInt( b3d_file )
	b3d_tos=b3d_tos+1
	b3d_stack(b3d_tos)=FilePos( b3d_file )+sz
	Return tag$
End Function

Function b3dExitChunk()
	SeekFile b3d_file,b3d_stack(b3d_tos)
	b3d_tos=b3d_tos-1
End Function

Function b3dChunkSize()
	Return b3d_stack(b3d_tos)-FilePos( b3d_file )
End Function

;***** Functions for writing to B3D files *****

Function b3dWriteByte( n )
	WriteByte( b3d_file,n )
End Function

Function b3dWriteInt( n )
	WriteInt( b3d_file,n )
End Function

Function b3dWriteFloat( n# )
	WriteFloat( b3d_file,n )
End Function

Function b3dWriteString( t$ )
	For k=1 To Len( t$ )
		ch=Asc(Mid$(t$,k,1))
		b3dWriteByte(ch)
		If ch=0 Return
	Next
	b3dWriteByte( 0 )
End Function

Function b3dBeginChunk( tag$ )
	b3d_tos=b3d_tos+1
	For k=1 To 4
		b3dWriteByte(Asc(Mid$( tag$,k,1 )))
	Next
	b3dWriteInt( 0 )
	b3d_stack(b3d_tos)=FilePos( b3d_file )
End Function

Function b3dEndChunk()
	n=FilePos( b3d_file )
	SeekFile b3d_file,b3d_stack(b3d_tos)-4
	b3dWriteInt( n-b3d_stack(b3d_tos) )
	SeekFile b3d_file,n
	b3d_tos=b3d_tos-1
End Function