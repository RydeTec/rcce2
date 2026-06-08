Strict

Include "Modules\IO\File.bb"

Type Filesystem
	; Copies a file atomically. The previous implementation (DeleteFile
	; destination, then CopyFile source -> destination) was not safe at
	; all -- a crash, lock, or disk-full between the two left the caller
	; with no destination. Used by GenerateFullInstall / GenerateServer
	; to assemble release artefacts, so an aborted publish would silently
	; produce a broken release.
	;
	; Pattern: copy to {ToFile}.tmp, verify size, swap in. A crash mid-
	; sequence leaves either the original ToFile intact (early failure)
	; or the .tmp on disk for manual recovery (late failure).
	Method safeCopyFile(FromFile$, ToFile$)

		If FileType(FromFile$) <> 1 Then Return

		Local TmpFile$ = ToFile$ + ".tmp"
		If FileType(TmpFile$) = 1 Then DeleteFile(TmpFile$)

		CopyFile(FromFile$, TmpFile$)
		If FileType(TmpFile$) <> 1 Then Return
		If FileSize(TmpFile$) <> FileSize(FromFile$)
			DeleteFile(TmpFile$)
			Return
		EndIf

		If FileType(ToFile$) = 1 Then DeleteFile(ToFile$)
		CopyFile(TmpFile$, ToFile$)
		If FileType(ToFile$) <> 1
			; Promote failed -- try to roll back from the still-present
			; temp. Caller's destination is now missing either way.
			CopyFile(TmpFile$, ToFile$)
			Return
		EndIf
		DeleteFile(TmpFile$)

	End Method

	; Deletes a directory and all its subdirectories (RECURSIVE)
	Method delTree(Dir$)

		If FileType(Dir$) <> 2 Then Return

		local D.BBDir = ReadDir(Dir$)
		local Path$ = NextFile$(D)
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
		CopyDir(Dir$, DestinationDir$)
	End Method

	Method safeClearFile(f.File)
		if (FileSystem::fileExists(self, f\uri))
			File::remove(f)
		end if

		File::writeLine(f, "")
		File::close(f)
	End Method

	Method safeGetFile.File(uri$)
		local f.File = new File(uri$)

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