Strict

Function MediaImportNormalizeSlashes$(path$)
	Local normalized$ = ""
	For i = 1 To Len(path$)
		Local ch$ = Mid$(path$, i, 1)
		If ch$ = "/" Then ch$ = "\"
		normalized$ = normalized$ + ch$
	Next
	Return normalized$
End Function

Function MediaImportTrimTrailingSlash$(path$)
	path$ = MediaImportNormalizeSlashes$(path$)
	While Len(path$) > 0
		Local ch$ = Mid$(path$, Len(path$), 1)
		If ch$ <> "\" Then Exit
		path$ = Left$(path$, Len(path$) - 1)
	Wend
	Return path$
End Function

Function MediaImportEnsureTrailingSlash$(path$)
	path$ = MediaImportNormalizeSlashes$(path$)
	If Right$(path$, 1) <> "\" Then path$ = path$ + "\"
	Return path$
End Function

Function MediaImportIsAbsolute%(path$)
	path$ = MediaImportNormalizeSlashes$(path$)
	If Len(path$) >= 2 And Mid$(path$, 2, 1) = ":" Then Return True
	If Left$(path$, 2) = "\\" Then Return True
	Return False
End Function

Function MediaImportHasPrefix%(path$, prefix$)
	If Len(path$) < Len(prefix$) Then Return False
	Return Lower$(Left$(path$, Len(prefix$))) = Lower$(prefix$)
End Function

Function MediaImportFileName$(path$)
	path$ = MediaImportTrimTrailingSlash$(path$)
	For i = Len(path$) To 1 Step -1
		Local ch$ = Mid$(path$, i, 1)
		If ch$ = "\" Or ch$ = "/" Then Return Mid$(path$, i + 1)
	Next
	Return path$
End Function

Function MediaImportSourcePath$(path$, currentDir$)
	path$ = MediaImportTrimTrailingSlash$(path$)
	If MediaImportIsAbsolute(path$) Then Return path$
	Return MediaImportEnsureTrailingSlash$(currentDir$) + path$
End Function

Function MediaImportShouldCopy%(path$, mediaRoot$, currentDir$)
	path$ = MediaImportTrimTrailingSlash$(path$)
	Local relativeRoot$ = MediaImportEnsureTrailingSlash$(mediaRoot$)
	Local absoluteRoot$ = MediaImportEnsureTrailingSlash$(MediaImportSourcePath$(relativeRoot$, currentDir$))
	If MediaImportHasPrefix(path$, relativeRoot$) Then Return False
	If MediaImportHasPrefix(path$, absoluteRoot$) Then Return False
	Return True
End Function

Function MediaImportRelativePath$(path$, mediaRoot$, currentDir$, mediaFolder$ = "")
	path$ = MediaImportTrimTrailingSlash$(path$)
	Local relativeRoot$ = MediaImportEnsureTrailingSlash$(mediaRoot$)
	Local absoluteRoot$ = MediaImportEnsureTrailingSlash$(MediaImportSourcePath$(relativeRoot$, currentDir$))

	If MediaImportHasPrefix(path$, relativeRoot$)
		Return Mid$(path$, Len(relativeRoot$) + 1)
	EndIf

	If MediaImportHasPrefix(path$, absoluteRoot$)
		Return Mid$(path$, Len(absoluteRoot$) + 1)
	EndIf

	Local filename$ = MediaImportFileName$(path$)
	Local folder$ = MediaImportTrimTrailingSlash$(mediaFolder$)
	If folder$ <> "" Then filename$ = folder$ + "\" + filename$
	Return filename$
End Function
