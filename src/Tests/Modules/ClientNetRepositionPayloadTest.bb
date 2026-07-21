Strict
EnableGC

; Source-contract regression for the server-to-client P_RepositionActor frame.
; The full ClientNet module has renderer/network dependencies, so this test
; binds the bounded handler shape without importing the client world graph.

Function RepositionLineIsIgnorable%(Line$)
    If Line$ = "" Then Return True
    If Left$(Line$, 1) = ";" Then Return True
    Return False
End Function

Function RepositionShortPayloadGuard$()
    Return "If (RepositionType$ = " + Chr$(34) + "M" + Chr$(34) + " And Len(M\MessageData$) < 16) Or (RepositionType$ = " + Chr$(34) + "R" + Chr$(34) + " And Len(M\MessageData$) < 7)"
End Function

Function RepositionTruncatedLog$()
    Return "WriteLog(MainLog, " + Chr$(34) + "P_RepositionActor: truncated payload, dropping" + Chr$(34) + ")"
End Function

Function RepositionKnownFrameGate$()
    Return "ElseIf RepositionType$ = " + Chr$(34) + "M" + Chr$(34) + " Or RepositionType$ = " + Chr$(34) + "R" + Chr$(34)
End Function

Function RepositionMoveBranch$()
    Return "If Left$(M\MessageData$, 1) = " + Chr$(34) + "M" + Chr$(34)
End Function

Function RepositionPayloadGuardIsOrdered%(Path$)
    Local F.BBStream = ReadFile(Path$)
    Local InCase%, Stage%
    Local Line$, Trimmed$
    If F = Null Then F = ReadFile("..\" + Path$)
    If F = Null Then F = ReadFile("..\..\" + Path$)
    If F = Null Then Return False

    While Not Eof(F)
        Line$ = ReadLine$(F)
        Trimmed$ = Trim$(Line$)
        If Instr(Line$, "Case P_RepositionActor") > 0
            InCase = True
        ElseIf InCase = True
            If Stage = 0
                If Trimmed$ = "RepositionType$ = Left$(M\MessageData$, 1)"
                    Stage = 1
                ElseIf RepositionLineIsIgnorable%(Trimmed$) = False
                    CloseFile F
                    Return False
                EndIf
            ElseIf Stage = 1
                If Trimmed$ = RepositionShortPayloadGuard$()
                    Stage = 2
                ElseIf RepositionLineIsIgnorable%(Trimmed$) = False
                    CloseFile F
                    Return False
                EndIf
            ElseIf Stage = 2
                If Trimmed$ = RepositionTruncatedLog$()
                    Stage = 3
                ElseIf RepositionLineIsIgnorable%(Trimmed$) = False
                    CloseFile F
                    Return False
                EndIf
            ElseIf Stage = 3
                If Trimmed$ = RepositionKnownFrameGate$()
                    Stage = 4
                ElseIf RepositionLineIsIgnorable%(Trimmed$) = False
                    CloseFile F
                    Return False
                EndIf
            ElseIf Stage = 4
                If Trimmed$ = "RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 2, 2))"
                    Stage = 5
                ElseIf RepositionLineIsIgnorable%(Trimmed$) = False
                    CloseFile F
                    Return False
                EndIf
            ElseIf Stage = 5
                If Trimmed$ = "AI.ActorInstance = RuntimeIDList(RuntimeID)"
                    Stage = 6
                ElseIf RepositionLineIsIgnorable%(Trimmed$) = False
                    CloseFile F
                    Return False
                EndIf
            ElseIf Stage = 6
                If Trimmed$ = "If AI <> Null"
                    Stage = 7
                ElseIf RepositionLineIsIgnorable%(Trimmed$) = False
                    CloseFile F
                    Return False
                EndIf
            ElseIf Stage = 7
                If Trimmed$ = RepositionMoveBranch$()
                    Stage = 8
                ElseIf RepositionLineIsIgnorable%(Trimmed$) = False
                    CloseFile F
                    Return False
                EndIf
            ElseIf Stage = 8
                If Trimmed$ = "AI\X# = RCE_FloatFromStr(Mid$(M\MessageData$, 4, 4))"
                    Stage = 9
                ElseIf RepositionLineIsIgnorable%(Trimmed$) = False
                    CloseFile F
                    Return False
                EndIf
            ElseIf Stage = 9
                If Trimmed$ = "PositionEntity(AI\CollisionEN, AI\X#, Y#, AI\Z#)"
                    Stage = 10
                EndIf
            ElseIf Stage = 10
                If Trimmed$ = "Else"
                    Stage = 11
                EndIf
            ElseIf Stage = 11
                If Trimmed$ = "AI\Yaw# = RCE_FloatFromStr(Mid$(M\MessageData$, 4))"
                    Stage = 12
                ElseIf RepositionLineIsIgnorable%(Trimmed$) = False
                    CloseFile F
                    Return False
                EndIf
            ElseIf Stage = 12
                If Trimmed$ = "RotateEntity(AI\CollisionEN, 0, AI\Yaw#, 0)"
                    CloseFile F
                    Return True
                ElseIf RepositionLineIsIgnorable%(Trimmed$) = False
                    CloseFile F
                    Return False
                EndIf
            EndIf
        EndIf
    Wend

    CloseFile F
    Return False
End Function

Function WriteRepositionGuardFixture(F.BBStream, DecodeBeforeGuard% = False)
    WriteLine F, "Case P_RepositionActor"
    WriteLine F, "RepositionType$ = Left$(M\MessageData$, 1)"
    If DecodeBeforeGuard = True Then WriteLine F, "RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 2, 2))"
    WriteLine F, RepositionShortPayloadGuard$()
    WriteLine F, RepositionTruncatedLog$()
    WriteLine F, RepositionKnownFrameGate$()
    WriteLine F, "RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 2, 2))"
    WriteLine F, "AI.ActorInstance = RuntimeIDList(RuntimeID)"
    WriteLine F, "If AI <> Null"
    WriteLine F, RepositionMoveBranch$()
    WriteLine F, "AI\X# = RCE_FloatFromStr(Mid$(M\MessageData$, 4, 4))"
    WriteLine F, "PositionEntity(AI\CollisionEN, AI\X#, Y#, AI\Z#)"
    WriteLine F, "Else"
    WriteLine F, "AI\Yaw# = RCE_FloatFromStr(Mid$(M\MessageData$, 4))"
    WriteLine F, "RotateEntity(AI\CollisionEN, 0, AI\Yaw#, 0)"
End Function

Global RepositionPayloadFixture$ = CurrentDir$() + "clientnet_reposition_payload_fixture.tmp"

Test testRepositionPayloadGuardsRejectShortMoveAndRotateBeforeDecode()
    Assert(RepositionPayloadGuardIsOrdered%("Modules\ClientNet.bb") = True)
End Test

Test testRepositionPayloadGuardScannerRejectsDecodeBeforeShortFrameGate()
    If FileType(RepositionPayloadFixture$) = 1 Then DeleteFile(RepositionPayloadFixture$)
    Local F.BBStream = WriteFile(RepositionPayloadFixture$)
    Assert(F <> Null)
    If F <> Null
        WriteRepositionGuardFixture(F, True)
        CloseFile F
        Assert(RepositionPayloadGuardIsOrdered%(RepositionPayloadFixture$) = False)
    EndIf
    If FileType(RepositionPayloadFixture$) = 1 Then DeleteFile(RepositionPayloadFixture$)
End Test
