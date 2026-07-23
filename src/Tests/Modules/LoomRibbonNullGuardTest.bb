Strict
EnableGC

// Ribbon depends on the optional BrokenRefs finder. The full Loom UI graph is
// not suitable for the standalone harness, so this bounded source contract
// pins the sequential guard that must contain each modal dispatch.

Function HasSequentialFinderGuard%(Path$, ClickNeedle$)
    Local F.BBStream = ReadFile(Path$)
    Local Line$
    Local Stage% = 0
    If F = Null Then F = ReadFile("..\" + Path$)
    If F = Null Then F = ReadFile("..\..\" + Path$)
    If F = Null Then Return False

    While Not Eof(F)
        Line$ = Trim$(ReadLine$(F))
        Select Stage
            Case 0
                If Instr(Line$, ClickNeedle$) > 0 Then Stage = 1
            Case 1
                If Line$ <> "If self\brokenRefs <> Null"
                    CloseFile F
                    Return False
                EndIf
                Stage = 2
            Case 2
                If Line$ <> "BrokenRefs::openModal(self\brokenRefs)"
                    CloseFile F
                    Return False
                EndIf
                Stage = 3
            Case 3
                If Line$ = "EndIf"
                    CloseFile F
                    Return True
                EndIf
        End Select
    Wend

    CloseFile F
    Return False
End Function

Function FileContains%(Path$, Needle$)
    Local F.BBStream = ReadFile(Path$)
    Local Line$
    If F = Null Then F = ReadFile("..\" + Path$)
    If F = Null Then F = ReadFile("..\..\" + Path$)
    If F = Null Then Return False

    While Not Eof(F)
        Line$ = ReadLine$(F)
        If Instr(Line$, Needle$) > 0
            CloseFile F
            Return True
        EndIf
    Wend

    CloseFile F
    Return False
End Function

Test testBrokenReferenceChipWaitsForFinderAttachment()
    Local Source$ = "Modules\Loom\Ribbon.bb"
    Assert(HasSequentialFinderGuard%(Source$, "If brkHover And clicked") = True)
    Assert(HasSequentialFinderGuard%(Source$, "If emptyHover And clicked") = True)
    Assert(FileContains%(Source$, "self\brokenRefs <> Null And") = False)
End Test
