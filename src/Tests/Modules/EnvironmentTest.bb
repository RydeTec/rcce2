Strict
EnableGC

; Logging stubs so Environment's SafeWrite/WriteLog/ReadBoundedString$
; calls resolve without pulling Modules\Logging.bb (which has its own
; UI/file deps).
Global MainLog = 0

Function WriteLog(LogID%, Message$)
End Function

Function SafeWriteOpen$(FinalPath$)
	Return FinalPath$ + ".tmp"
End Function

Function SafeWriteCommit%(TempPath$, FinalPath$, F)
	Return True
End Function

Function SafeWriteAbort(TempPath$, F)
End Function

Function ReadBoundedString$(F, MaxLen)
	If F = 0 Then Return ""
	Local Length = ReadInt(F)
	If Length < 0 Or Length > MaxLen Then Return ""
	Local Value$ = ""
	For i = 1 To Length
		If Eof(F) Then Exit
		Value$ = Value$ + Chr$(ReadByte(F))
	Next
	Return Value$
End Function

Include "Modules\Environment.bb"

Global SunLoadTestFile$ = CurrentDir$() + "environment_suns_test.dat"
Global EnvironmentLoadTestFile$ = CurrentDir$() + "environment_load_test.dat"

Function ClearEnvironmentLoadTestState()
	If FileType(EnvironmentLoadTestFile$) = 1 Then DeleteFile(EnvironmentLoadTestFile$)
End Function

Function WriteEnvironmentLoadTestFile%(IncludeMonths = True)
	Local F.BBStream = WriteFile(EnvironmentLoadTestFile$)
	Local i
	If F = Null Then Return False
	WriteInt F, 7
	WriteInt F, 42
	WriteInt F, 14
	WriteInt F, 30
	WriteInt F, 10
	For i = 0 To 11
		WriteString F, "Season " + i
		WriteInt F, i * 28
		WriteInt F, 18
		WriteInt F, 6
	Next
	If IncludeMonths = True
		For i = 0 To 19
			WriteString F, "Month " + i
			WriteInt F, i * 28
		Next
	EndIf
	CloseFile F
	Return True
End Function

Function ClearSunLoadTestState()
	If FileType(SunLoadTestFile$) = 1 Then DeleteFile(SunLoadTestFile$)
	Local S.Sun = First Sun
	Local NextS.Sun
	While S <> Null
		NextS = After S
		Delete S
		S = NextS
	Wend
End Function

Function SunLoadTestCount()
	Local Count = 0
	For S.Sun = Each Sun
		Count = Count + 1
	Next
	Return Count
End Function

Function WriteSunLoadTestRecord(F.BBStream)
	Local i
	For i = 0 To 7
		WriteShort F, 0
	Next
	WriteByte F, 0
	WriteByte F, 1
	WriteFloat F, 1.0
	WriteByte F, 255
	WriteByte F, 255
	WriteByte F, 255
	WriteFloat F, 0.0
	For i = 0 To 11
		WriteByte F, 0
		WriteByte F, 0
		WriteByte F, 0
		WriteByte F, 0
	Next
	WriteByte F, 0
End Function

Function WriteSunLoadTestFile%(Count, Records, TrailingBytes = 0)
	Local F.BBStream = WriteFile(SunLoadTestFile$)
	Local i
	If F = Null Then Return False
	WriteInt F, Count
	For i = 1 To Records
		WriteSunLoadTestRecord(F)
	Next
	For i = 1 To TrailingBytes
		WriteByte F, 0
	Next
	CloseFile F
	Return True
End Function

Function WriteSunLoadTestHeaderBytes%(Bytes)
	Local F.BBStream = WriteFile(SunLoadTestFile$)
	Local i
	If F = Null Then Return False
	For i = 1 To Bytes
		WriteByte F, 0
	Next
	CloseFile F
	Return True
End Function

; TimeDelta is pure arithmetic over hour/minute pairs. The function has three
; branches (same-hour, forward-in-day, wraps-past-midnight); pin each one so
; later refactors of the day-cycle math can't drift the wall-clock delta the
; rest of the engine relies on (script timers, sun position, etc.).

Test testTimeDeltaWithinSameHourReturnsMinuteDifference()
	Assert(TimeDelta(10, 5, 10, 30) = 25)
	Assert(TimeDelta(0, 0, 0, 59) = 59)
	Assert(TimeDelta(23, 10, 23, 10) = 0)
End Test

Test testTimeDeltaForwardInSameDayCrossesHourBoundary()
	; 10:50 -> 11:05 = 15
	Assert(TimeDelta(10, 50, 11, 5) = 15)
	; 10:00 -> 12:00 = 120
	Assert(TimeDelta(10, 0, 12, 0) = 120)
	; 10:30 -> 11:00 = 30
	Assert(TimeDelta(10, 30, 11, 0) = 30)
End Test

Test testTimeDeltaSpansMidnightWhenEndHourBeforeStartHour()
	; 23:50 -> 00:10 = 20
	Assert(TimeDelta(23, 50, 0, 10) = 20)
	; 22:00 -> 01:00 = 180
	Assert(TimeDelta(22, 0, 1, 0) = 180)
End Test

Test testLoadSunsRejectsMissingOrUndersizedHeadersBeforeAllocation()
	ClearSunLoadTestState()
	Assert(WriteSunLoadTestHeaderBytes(0) = True)
	Assert(LoadSunsFromFile(SunLoadTestFile$) = False)
	Assert(SunLoadTestCount() = 0)
	Assert(WriteSunLoadTestHeaderBytes(3) = True)
	Assert(LoadSunsFromFile(SunLoadTestFile$) = False)
	Assert(SunLoadTestCount() = 0)
	ClearSunLoadTestState()
End Test

Test testLoadSunsRejectsNegativeAndOverclaimedCountsBeforeAllocation()
	ClearSunLoadTestState()
	Assert(WriteSunLoadTestFile(-1, 0) = True)
	Assert(LoadSunsFromFile(SunLoadTestFile$) = False)
	Assert(SunLoadTestCount() = 0)
	Assert(WriteSunLoadTestFile(1, 0) = True)
	Assert(LoadSunsFromFile(SunLoadTestFile$) = False)
	Assert(SunLoadTestCount() = 0)
	ClearSunLoadTestState()
End Test

Test testLoadSunsRejectsTruncatedAndTrailingRecordsBeforeAllocation()
	ClearSunLoadTestState()
	Assert(WriteSunLoadTestFile(1, 0, 77) = True)
	Assert(LoadSunsFromFile(SunLoadTestFile$) = False)
	Assert(SunLoadTestCount() = 0)
	Assert(WriteSunLoadTestFile(1, 1, 1) = True)
	Assert(LoadSunsFromFile(SunLoadTestFile$) = False)
	Assert(SunLoadTestCount() = 0)
	ClearSunLoadTestState()
End Test

Test testLoadSunsLoadsTheExactDeclaredRecordCount()
	ClearSunLoadTestState()
	Assert(WriteSunLoadTestFile(1, 1) = True)
	Assert(LoadSunsFromFile(SunLoadTestFile$) = True)
	Assert(SunLoadTestCount() = 1)
	ClearSunLoadTestState()
End Test

Test testLoadEnvironmentFromFileAcceptsCompleteRequiredRecords()
	ClearEnvironmentLoadTestState()
	Assert(WriteEnvironmentLoadTestFile(True) = True)
	Assert(LoadEnvironmentFromFile(EnvironmentLoadTestFile$) = True)
	Assert(Year = 7)
	Assert(Day = 42)
	Assert(TimeH = 14)
	Assert(TimeM = 30)
	Assert(TimeFactor = 10)
	Assert(SeasonName$(0) = "Season 0")
	Assert(MonthName$(19) = "Month 19")
	ClearEnvironmentLoadTestState()
End Test

Test testLoadEnvironmentFromFileRejectsTruncatedRequiredRecords()
	ClearEnvironmentLoadTestState()
	Assert(WriteEnvironmentLoadTestFile(False) = True)
	Assert(LoadEnvironmentFromFile(EnvironmentLoadTestFile$) = False)
	ClearEnvironmentLoadTestState()
End Test
