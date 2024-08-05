Strict

Type Filesystem
	; Copies a file, but deletes the destination first if it already exists
	Method safeCopyFile(FromFile$, ToFile$)

		If FileType(FromFile$) = 1
			If FileType(ToFile$) = 1 Then DeleteFile(ToFile$)
			CopyFile(FromFile$, ToFile$)
		EndIf

	End Method

	; Deletes a directory and all its subdirectories (RECURSIVE)
	Method DelTree(Dir$)

		If FileType(Dir$) <> 2 Then Return

		D = ReadDir(Dir$)
		Path$ = NextFile$(D)
		While Len(Path$) > 0
			If Path$ <> "." And Path$ <> ".."
				If FileType(Dir$ + "\" + Path$) = 2
					Filesystem::DelTree(self,Dir$ + "\" + Path$)
				Else
					DeleteFile(Dir$ + "\" + Path$)
				EndIf
			EndIf
			Path$ = NextFile$(D)
		Wend
		CloseDir(D)
		DeleteDir(Dir$)

	End Method

	; Copies a directory and all its subdirectories (RECURSIVE)
	Method CopyTree(Dir$, DestinationDir$)

		If FileType(DestinationDir$) = 0 Then CreateDir(DestinationDir$)

		D = ReadDir(Dir$)
		If D = 0 Then Return
		Path$ = NextFile$(D)
		While Len(Path$) > 0
			If Path$ <> "." And Path$ <> ".."
				If FileType(Dir$ + "\" + Path$) = 2
					Filesystem::CopyTree(self,Dir$ + "\" + Path$, DestinationDir$ + "\" + Path$)
				Else
					CopyFile(Dir$ + "\" + Path$, DestinationDir$ + "\" + Path$)
				EndIf
			EndIf
			Path$ = NextFile$(D)
		Wend
		CloseDir(D)

	End Method
End Type