Strict
EnableGC

// Toasts.bb is part of Loom's renderer graph, so this source contract binds
// the expiry sweep's deletion-safe traversal without loading the editor.

Function ToastExpirySweepUsesAfterCursor%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$, Trimmed$
	Local InRender%, Stage%
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		Trimmed$ = Trim$(Line$)
		If Trimmed$ = "Method render(sw%, sh%)" Then InRender = True
		If InRender = False Then Continue
		If Trimmed$ = "For t = Each Toast"
			CloseFile F
			Return False
		EndIf
		Select Stage
			Case 0
				If Trimmed$ = "Local t.Toast = First Toast" Then Stage = 1
			Case 1
				If Trimmed$ = "Local nextToast.Toast = Null" Then Stage = 2
			Case 2
				If Trimmed$ = "While t <> Null" Then Stage = 3
			Case 3
				If Trimmed$ = "nextToast = After t" Then Stage = 4
			Case 4
				If Trimmed$ = "If (MilliSecs() - t\CreatedAt) >= TOAST_TTL_MS" Then Stage = 5
			Case 5
				If Trimmed$ = "Delete t" Then Stage = 6
			Case 6
				If Trimmed$ = "self\count = self\count - 1" Then Stage = 7
			Case 7
				If Trimmed$ = "t = nextToast" Then Stage = 8
		End Select
		If Trimmed$ = "End Method"
			CloseFile F
			Return Stage = 8
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Test testExpiredToastsCaptureTheSuccessorBeforeDeletion()
	Assert(ToastExpirySweepUsesAfterCursor%("Modules\Loom\Toasts.bb") = True)
End Test
