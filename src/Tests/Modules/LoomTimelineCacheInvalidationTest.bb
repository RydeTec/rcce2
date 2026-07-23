Strict
EnableGC

// Timeline.bb is part of Loom's renderer graph, so this source contract pins
// the mutation sequence without loading the full editor. A revert must make
// the same shared WorldCache invalidation promise as a normal composer edit.

Function RevertInvalidatesWorldCache%(Path$)
    Local F.BBStream = ReadFile(Path$)
    Local Line$, Trimmed$
    Local InMethod%, Stage%
    If F = Null Then F = ReadFile("..\\" + Path$)
    If F = Null Then F = ReadFile("..\\..\\" + Path$)
    If F = Null Then Return False

    While Not Eof(F)
        Line$ = ReadLine$(F)
        Trimmed$ = Trim$(Line$)
        If Trimmed$ = "Method revertEntry(e.TimelineEntry)" Then InMethod = True
        If InMethod = False Then Continue

        Select Stage
            Case 0
                If Trimmed$ = "Composer::writeField(self\\composer, e\\Kind, e\\RefID, e\\FieldId, e\\OldValue)" Then Stage = 1
            Case 1
                If Trimmed$ = "Composer::markDirtyForKind(self\\composer, e\\Kind)" Then Stage = 2
            Case 2
                If Trimmed$ = "WorldCache_Invalidate()" Then Stage = 3
        End Select

        If Trimmed$ = "End Method"
            CloseFile F
            Return Stage = 3
        EndIf
    Wend

    CloseFile F
    Return False
End Function

Function WriteInvalidTimelineRevertFixture(Path$, EarlyInvalidation%)
    Local F.BBStream = WriteFile(Path$)
    If F = Null Then Return
    WriteLine(F, "Method revertEntry(e.TimelineEntry)")
    WriteLine(F, "Composer::writeField(self\\composer, e\\Kind, e\\RefID, e\\FieldId, e\\OldValue)")
    If EarlyInvalidation = True Then WriteLine(F, "WorldCache_Invalidate()")
    WriteLine(F, "Composer::markDirtyForKind(self\\composer, e\\Kind)")
    WriteLine(F, "End Method")
    CloseFile F
End Function

Function DeleteTimelineRevertFixture(Path$)
    If FileType(Path$) = 1 Then DeleteFile(Path$)
End Function

Test testTimelineRevertInvalidatesWorldCacheAfterMutation()
    Assert(RevertInvalidatesWorldCache%("Modules\\Loom\\Timeline.bb") = True)

    Local MissingFixture$ = "loom_timeline_cache_missing.bb"
    Local EarlyFixture$ = "loom_timeline_cache_early.bb"
    DeleteTimelineRevertFixture(MissingFixture$)
    DeleteTimelineRevertFixture(EarlyFixture$)

    WriteInvalidTimelineRevertFixture(MissingFixture$, False)
    Assert(RevertInvalidatesWorldCache%(MissingFixture$) = False)
    WriteInvalidTimelineRevertFixture(EarlyFixture$, True)
    Assert(RevertInvalidatesWorldCache%(EarlyFixture$) = False)

    DeleteTimelineRevertFixture(MissingFixture$)
    DeleteTimelineRevertFixture(EarlyFixture$)
End Test
