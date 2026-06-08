; =============================================================================
; Modules/Path.bb -- shared filesystem-path string helpers
; =============================================================================
;
; Extracted from GUE.bb (ADR-004 Phase A) so both GUE and Loom -- and the
; future shared LoadAreaData zone-loading path -- can use GetFilename$ without
; pulling in GUE.bb. Non-Strict to match the legacy data/UI modules and the
; `For i = ...` implicit-Local idiom this function uses.

; Gets the stripped filename from a path (the text after the last \ or /).
Function GetFilename$(Path$)

	For i = Len(Path$) To 1 Step -1
		If Mid$(Path$, i, 1) = "\" Or Mid$(Path$, i, 1) = "/" Then Return Mid$(Path$, i + 1)
	Next
	Return Path$

End Function
