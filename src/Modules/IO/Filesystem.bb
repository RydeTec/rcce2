Strict

Include "Modules\IO\File.bb"

Type Filesystem
	; Copies a file, but deletes the destination first if it already exists
	Method safeCopyFile(FromFile$, ToFile$)

		If FileType(FromFile$) = 1
			If FileType(ToFile$) = 1 Then DeleteFile(ToFile$)
			CopyFile(FromFile$, ToFile$)
		EndIf

	End Method

	; Deletes a directory and all its subdirectories (RECURSIVE)
	Method delTree(Dir$)

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
	Method copyTree(Dir$, DestinationDir$)

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

	Method safeClearFile(f.File)
		if (FileSystem::fileExists(self, f\uri))
			File::remove(f)
		end if

		File::writeLine(f, "")
		File::close(f)
	End Method

	Method safeGetFile.File(uri$)
		f.File = new File(uri$)

		if (not FileSystem::fileExists(self, uri$))
			File::writeLine(f, "")
			File::close(f)
		end if

		return f
	End Method

	;Checks if a directory exists
	Method dirExists(dir$)
		return FileType(dir$) = 2
	End Method

	; Checks if a file exists
	Method fileExists(path$)
		return FileType(path$) = 1
	End Method

	; Checks if a path is empty
	Method notExists(path$)
		return FileType(path$) = 0
	End Method
End Type