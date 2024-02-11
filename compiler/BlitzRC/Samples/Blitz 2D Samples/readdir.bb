
dir=ReadDir( CurrentDir$() )
Print "Directory of "+CurrentDir$()
Repeat
	f$=NextFile$( dir )
	If f$="" Then Exit
	t=FileType( f$ )
	f$=LSet$( f$,32 )
	If t=1			;it's a file!
		Print f$+" ("+FileSize( f$ )+")"
	Else If t=2	;it's a dir!
		Print f$+" (DIR)"
	EndIf
Forever
CloseDir dir
