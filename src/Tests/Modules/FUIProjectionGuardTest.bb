Strict
EnableGC

; BlitzForge evaluates Or operands eagerly. FUI_UpdateProjection must stage
; each handle guard before reading the next Application field or using it.

Function FUIProjectionGuardsAreStaged%(Path$)
    Local F.BBStream = ReadFile(Path$)
    Local InProjection%, Stage%
    Local Line$
    If F = Null Then F = ReadFile("..\" + Path$)
    If F = Null Then F = ReadFile("..\..\" + Path$)
    If F = Null Then Return False

    While Not Eof(F)
        Line$ = ReadLine$(F)
        If Instr(Line$, "Function FUI_UpdateProjection()") > 0 Then InProjection = True
        If InProjection = True
            If Instr(Line$, "If app = Null Or") > 0
                CloseFile F
                Return False
            EndIf
            If Instr(Line$, "If app\Cam = Null Or") > 0
                CloseFile F
                Return False
            EndIf
            If Instr(Line$, "If app\Pivot = Null Or") > 0
                CloseFile F
                Return False
            EndIf
            If Stage = 0 And Instr(Line$, "If app = Null") > 0 Then Stage = 1
            If Stage = 1 And Instr(Line$, "Return") > 0 Then Stage = 2
            If Stage = 2 And Instr(Line$, "If app\Cam = Null") > 0 Then Stage = 3
            If Stage = 3 And Instr(Line$, "Return") > 0 Then Stage = 4
            If Stage = 4 And Instr(Line$, "If app\Pivot = Null") > 0 Then Stage = 5
            If Stage = 5 And Instr(Line$, "Return") > 0 Then Stage = 6
            If Stage = 6 And Instr(Line$, "CameraViewport app\Cam") > 0 Then Stage = 7
            If Stage = 7 And Instr(Line$, "PositionEntity app\Pivot") > 0 Then Stage = 8
            If Stage = 8 And Instr(Line$, "ScaleEntity app\Pivot") > 0 Then
                CloseFile F
                Return True
            EndIf
            If Instr(Line$, "End Function") > 0 Then Exit
        EndIf
    Wend

    CloseFile F
    Return False
End Function

Test testFUIProjectionStagesGuardsBeforeProjectionCalls()
    Assert(FUIProjectionGuardsAreStaged%("Modules\F-UI.bb") = True)
End Test
