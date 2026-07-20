Strict
EnableGC

; BlitzForge evaluates Or operands eagerly. FUI_UpdateProjection must stage
; each handle guard before reading the next Application field or using it.

Function FUIProjectionGuardsAreStaged%(Path$)
    Local F.BBStream = ReadFile(Path$)
    Local InProjection%, Stage%
    Local Line$, Trimmed$
    If F = Null Then F = ReadFile("..\" + Path$)
    If F = Null Then F = ReadFile("..\..\" + Path$)
    If F = Null Then Return False

    While Not Eof(F)
        Line$ = ReadLine$(F)
        Trimmed$ = Trim$(Line$)
        If Instr(Line$, "Function FUI_UpdateProjection()") > 0 Then InProjection = True
        If InProjection = True
            If Stage = 0 And Trimmed$ = "If app = Null"
                Stage = 1
            ElseIf Stage = 1
                If Trimmed$ = "Return"
                    Stage = 2
                ElseIf Trimmed$ <> "" And Left$(Trimmed$, 1) <> ";"
                    CloseFile F
                    Return False
                EndIf
            ElseIf Stage = 2 And Trimmed$ = "EndIf"
                Stage = 3
            ElseIf Stage = 3
                If Trimmed$ = "If app\Cam = Null"
                    Stage = 4
                ElseIf Instr(Trimmed$, "app\Cam") > 0
                    CloseFile F
                    Return False
                EndIf
            ElseIf Stage = 4
                If Trimmed$ = "Return"
                    Stage = 5
                ElseIf Trimmed$ <> "" And Left$(Trimmed$, 1) <> ";"
                    CloseFile F
                    Return False
                EndIf
            ElseIf Stage = 5 And Trimmed$ = "EndIf"
                Stage = 6
            ElseIf Stage = 6
                If Trimmed$ = "If app\Pivot = Null"
                    Stage = 7
                ElseIf Instr(Trimmed$, "app\Pivot") > 0
                    CloseFile F
                    Return False
                EndIf
            ElseIf Stage = 7
                If Trimmed$ = "Return"
                    Stage = 8
                ElseIf Trimmed$ <> "" And Left$(Trimmed$, 1) <> ";"
                    CloseFile F
                    Return False
                EndIf
            ElseIf Stage = 8 And Trimmed$ = "EndIf"
                Stage = 9
            ElseIf Stage = 9 And Trimmed$ = "If app\W <= 0 Or app\H <= 0"
                Stage = 10
            ElseIf Stage = 10 And Trimmed$ = "Return"
                Stage = 11
            ElseIf Stage = 11 And Trimmed$ = "EndIf"
                Stage = 12
            ElseIf Stage = 12 And Instr(Trimmed$, "app\Aspect = FUI_WindowAspect#") > 0
                Stage = 13
            ElseIf Stage = 13 And Instr(Trimmed$, "app\Scale = FUI_WindowScale#") > 0
                Stage = 14
            ElseIf Stage = 14 And Trimmed$ = "CameraViewport app\Cam, 0, 0, app\W, app\H"
                Stage = 15
            ElseIf Stage = 15 And Trimmed$ = "PositionEntity app\Pivot,-1.0, app\Aspect, 1.0"
                Stage = 16
            ElseIf Stage = 16 And Trimmed$ = "ScaleEntity app\Pivot, app\Scale,-app\Scale,-app\Scale"
                CloseFile F
                Return True
            ElseIf Instr(Line$, "End Function") > 0
                CloseFile F
                Return False
            EndIf
        EndIf
    Wend

    CloseFile F
    Return False
End Function

Global FUIProjectionGuardTestPath$ = CurrentDir$() + "fui_projection_guard_same_line.tmp"

Test testFUIProjectionStagesGuardsBeforeProjectionCalls()
    Assert(FUIProjectionGuardsAreStaged%("Modules\F-UI.bb") = True)
End Test

Test testFUIProjectionGuardScannerRejectsSameLineTransitions()
    If FileType(FUIProjectionGuardTestPath$) = 1 Then DeleteFile(FUIProjectionGuardTestPath$)
    Local F.BBStream = WriteFile(FUIProjectionGuardTestPath$)
    Assert(F <> Null)
    If F <> Null
        WriteLine F, "Function FUI_UpdateProjection()"
        WriteLine F, "If app = Null Return"
        WriteLine F, "EndIf"
        WriteLine F, "If app\Cam = Null"
        WriteLine F, "Return"
        WriteLine F, "EndIf"
        WriteLine F, "If app\Pivot = Null"
        WriteLine F, "Return"
        WriteLine F, "EndIf"
        WriteLine F, "If app\W <= 0 Or app\H <= 0"
        WriteLine F, "Return"
        WriteLine F, "EndIf"
        WriteLine F, "app\Aspect = FUI_WindowAspect#(app\W, app\H)"
        WriteLine F, "app\Scale = FUI_WindowScale#(app\W)"
        WriteLine F, "CameraViewport app\Cam, 0, 0, app\W, app\H"
        WriteLine F, "PositionEntity app\Pivot,-1.0, app\Aspect, 1.0"
        WriteLine F, "ScaleEntity app\Pivot, app\Scale,-app\Scale,-app\Scale"
        CloseFile F
        Assert(FUIProjectionGuardsAreStaged%(FUIProjectionGuardTestPath$) = False)
    EndIf
    If FileType(FUIProjectionGuardTestPath$) = 1 Then DeleteFile(FUIProjectionGuardTestPath$)
End Test
