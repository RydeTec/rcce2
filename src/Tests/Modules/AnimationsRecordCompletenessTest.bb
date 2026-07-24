Strict
EnableGC

; Regression coverage for LoadAnimSets: only complete Animations.dat records
; may be published into AnimList. Keep the production module standalone by
; stubbing the actor shapes used only by PlayAnimation.

Type Actor
	Field MAnimationSet, FAnimationSet
End Type

Type ActorInstance
	Field Gender
	Field Actor.Actor
	Field EN
	Field AnimSeqs[149]
End Type

Global LogMode = 0
Global MainLog = 0

Include "Modules\\Logging.bb"
Include "Modules\\Animations.bb"

Global AnimationsRecordTestFile$ = CurrentDir$() + "animations_record_completeness_test.dat"

Function ClearAnimSets()
	Delete Each AnimSet
End Function

Function CleanupAnimationsRecordTestFile()
	If FileType(AnimationsRecordTestFile$) = 1 Then DeleteFile(AnimationsRecordTestFile$)
	If FileType(AnimationsRecordTestFile$ + ".tmp") = 1 Then DeleteFile(AnimationsRecordTestFile$ + ".tmp")
	If FileType(AnimationsRecordTestFile$ + ".bak") = 1 Then DeleteFile(AnimationsRecordTestFile$ + ".bak")
End Function

Function WriteCompleteAnimSet(F.BBStream, ID, Name$)
	WriteShort F, ID
	WriteString F, Name$
	For i = 0 To 149
		WriteString F, "Clip " + i
		WriteShort F, i
		WriteShort F, i + 10
		WriteFloat F, 1.0
	Next
End Function

Test testLoadAnimSetsRejectsIdOnlyRecordBeforePublish()
	ClearAnimSets()
	CleanupAnimationsRecordTestFile()
	Local F.BBStream = WriteFile(AnimationsRecordTestFile$)
	WriteShort F, 7
	CloseFile F

	Assert(LoadAnimSets(AnimationsRecordTestFile$) = 0)
	Assert(AnimList(7) = Null)

	ClearAnimSets()
	CleanupAnimationsRecordTestFile()
End Test

Test testLoadAnimSetsRejectsTruncatedSetNameBeforePublish()
	ClearAnimSets()
	CleanupAnimationsRecordTestFile()
	Local F.BBStream = WriteFile(AnimationsRecordTestFile$)
	WriteShort F, 7
	WriteInt F, 4
	WriteByte F, Asc("x")
	CloseFile F

	Assert(LoadAnimSets(AnimationsRecordTestFile$) = 0)
	Assert(AnimList(7) = Null)

	ClearAnimSets()
	CleanupAnimationsRecordTestFile()
End Test

Test testLoadAnimSetsRetainsCompleteRecordBeforeTruncatedClipTail()
	ClearAnimSets()
	CleanupAnimationsRecordTestFile()
	Local F.BBStream = WriteFile(AnimationsRecordTestFile$)
	WriteCompleteAnimSet(F, 3, "Complete")
	WriteShort F, 8
	WriteString F, "Partial"
	WriteString F, "Broken clip"
	WriteShort F, 1
	CloseFile F

	Assert(LoadAnimSets(AnimationsRecordTestFile$) = 1)
	Assert(AnimList(3) <> Null)
	Assert(AnimList(3)\Name$ = "Complete")
	Assert(AnimList(8) = Null)

	ClearAnimSets()
	CleanupAnimationsRecordTestFile()
End Test
