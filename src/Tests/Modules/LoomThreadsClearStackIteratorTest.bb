Strict
EnableGC

; Source-contract regression for Loom thread-history cleanup. Deleting a
; live Each cursor can skip or corrupt entries, so clearStack must capture
; the successor before deleting the current LoomFocusEntry.

Function ClearStackUsesAfterCursor%(Path$)
    Local F.BBStream = ReadFile(Path$)
    Local Line$, Trimmed$
    Local InMethod%, Stage%, SawListClear%
    If F = Null Then F = ReadFile("..\" + Path$)
    If F = Null Then F = ReadFile("..\..\" + Path$)
    If F = Null Then Return False

    While Not Eof(F)
        Line$ = ReadLine$(F)
        Trimmed$ = Trim$(Line$)
        If Trimmed$ = "Method clearStack()" Then InMethod = True
        If InMethod = False Then Continue

        If Trimmed$ = "For entry = Each LoomFocusEntry"
            CloseFile F
            Return False
        EndIf
        If Trimmed$ = "Delete entry" And Stage < 4
            CloseFile F
            Return False
        EndIf

        Select Stage
            Case 0
                If Trimmed$ = "Local entry.LoomFocusEntry = First LoomFocusEntry" Then Stage = 1
            Case 1
                If Trimmed$ = "Local nextEntry.LoomFocusEntry = Null" Then Stage = 2
            Case 2
                If Trimmed$ = "While entry <> Null" Then Stage = 3
            Case 3
                If Trimmed$ = "nextEntry = After entry" Then Stage = 4
            Case 4
                If Trimmed$ = "Delete entry" Then Stage = 5
            Case 5
                If Trimmed$ = "entry = nextEntry" Then Stage = 6
        End Select

        If Trimmed$ = "ListClear(self\backStack)" Then SawListClear = True
        If Trimmed$ = "End Method"
            CloseFile F
            Return Stage = 6 And SawListClear
        EndIf
    Wend

    CloseFile F
    Return False
End Function

Test testLoomThreadsClearStackCapturesSuccessorBeforeDelete()
    Assert(ClearStackUsesAfterCursor%("Modules\Loom\Threads.bb") = True)
End Test
