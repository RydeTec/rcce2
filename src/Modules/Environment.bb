; Set constants
Const W_Sun   = 0
Const W_Rain  = 1
Const W_Snow  = 2
Const W_Fog   = 3
Const W_Storm = 4
Const W_Wind  = 5

Dim SeasonName$(11)
Dim SeasonStartDay(11)
Dim SeasonDuskH(11)
Dim SeasonDawnH(11)

Dim MonthName$(19)
Dim MonthStartDay(19)

Global CurrentSeason, Year, Day, TimeH, TimeM, TimeFactor = 10
Global TimeUpdate, HourChanged = True, MinuteChanged = True

Type Sun

	;Field EN, LightEN, TexID, Size#, LightR, LightG, LightB
	Field EN, LightEN, Size#, LightR, LightG, LightB
	Field ShowPhases	; 0 = disable phases, 1 = show phases
	Field Phase_Length ; in number of days
	Field TexID[7] ; 8 possible spots for phase images
	Field CurrentPhase

	
	Field StartH[11], StartM[11], EndH[11], EndM[11]
	Field PathAngle#
	Field ShowFlares
	Field Flares[10]
End Type

Function CreateEnvironment()
	Year = 1
	Day = 1
	TimeH = 12
	TimeM = 0
	TimeFactor = 10

	Local seasons = 4
	Local months = 12
	Local monthLength = 28
	Local seasonLength = Int ((months * monthLength) / seasons)
	Local yearLength = months * monthLength

	For i = 0 To 19
		MonthName$(i) = "Month " + (i + 1)
		MonthStartDay(i) = monthLength * i
	Next

	For i = 0 To 11
		SeasonName$(i) = "Season " + (i + 1)
		SeasonStartDay(i) = seasonLength * i
		SeasonDuskH(i) = 18
		SeasonDawnH(i) = 6
	Next

	; Set first month to length of year
	MonthStartDay(0) = yearLength

	; Set first season to length of year
	SeasonStartDay(0) = yearLength
	
	TimeUpdate = MilliSecs()
	CurrentSeason = GetSeason()
	Return SaveEnvironment(True)
End Function

; Loads all environment settings.
;
; Defensive reads throughout:
;   * Season/Month names go through ReadBoundedString$ (cap 256) so a
;     corrupted file's wild length prefix can't hang the server at boot.
;   * TimeFactor is clamped to a sane minimum (1) on load -- the comment
;     in SaveEnvironment notes the danger ("TimeFactor=0 then triggers
;     divide-by-zero in UpdateEnvironment"), but UpdateEnvironment itself
;     was unguarded. Clamp here so the SaveEnvironment-partial-write
;     defense is no longer the only line between corruption and a server
;     crash on the first UpdateEnvironment tick.
;   * TimeH / TimeM bounded to wall-clock ranges; Year / Day clamped to
;     non-negative.
Function LoadEnvironment()

	F = ReadFile("Data\Server Data\Environment.dat")
	If F = 0 Then Return CreateEnvironment()
	Year = ReadInt(F)
	Day = ReadInt(F)
	TimeH = ReadInt(F)
	TimeM = ReadInt(F)
	TimeFactor = ReadInt(F)
	For i = 0 To 11
		SeasonName$(i) = ReadBoundedString$(F, 256)
		SeasonStartDay(i) = ReadInt(F)
		SeasonDuskH(i) = ReadInt(F)
		SeasonDawnH(i) = ReadInt(F)
	Next
	For i = 0 To 19
		MonthName$(i) = ReadBoundedString$(F, 256)
		MonthStartDay(i) = ReadInt(F)
	Next
	CloseFile(F)

	; Clamp values that would crash or wedge UpdateEnvironment downstream.
	If TimeFactor < 1
		WriteLog(MainLog, "LoadEnvironment: TimeFactor was " + TimeFactor + " (would divide-by-zero); resetting to 10")
		TimeFactor = 10
	EndIf
	If Year < 0 Then Year = 0
	If Day < 0 Then Day = 0
	If TimeH < 0 Or TimeH > 23 Then TimeH = 0
	If TimeM < 0 Or TimeM > 59 Then TimeM = 0

	TimeUpdate = MilliSecs()
	CurrentSeason = GetSeason()
	Return True

End Function

; Saves all environment settings.
;
; Two modes:
;   FullSave=True  — rewrites the whole file (time fields + season/month
;                    config tables). Atomic temp+rename to avoid partial
;                    writes on crash.
;   FullSave=False — updates only the time fields at the start of the
;                    existing file (OpenFile, no truncate). Refuse this
;                    path if the file doesn't already exist OR is too small
;                    to hold the full config — otherwise LoadEnvironment
;                    later reads past EOF for TimeFactor/Seasons/Months and
;                    silently sets them to zero (TimeFactor=0 then triggers
;                    divide-by-zero in UpdateEnvironment).
Function SaveEnvironment(FullSave = False)

	Local FinalPath$ = "Data\Server Data\Environment.dat"

	If FullSave = True
		Local TempPath$ = SafeWriteOpen(FinalPath$)
		F = WriteFile(TempPath$)
		If F = 0
			WriteLog(MainLog, "SaveEnvironment: cannot open " + TempPath$ + " for write")
			Return False
		EndIf
		WriteInt F, Year
		WriteInt F, Day
		WriteInt F, TimeH
		WriteInt F, TimeM
		WriteInt F, TimeFactor
		For i = 0 To 11
			WriteString F, SeasonName$(i)
			WriteInt F, SeasonStartDay(i)
			WriteInt F, SeasonDuskH(i)
			WriteInt F, SeasonDawnH(i)
		Next
		For i = 0 To 19
			WriteString F, MonthName$(i)
			WriteInt F, MonthStartDay(i)
		Next
		Return SafeWriteCommit(TempPath$, FinalPath$, F)
	EndIf

	; Mid-session time-only update path.
	If FileType(FinalPath$) <> 1
		; No full save has ever happened. Refuse to write a half file that
		; LoadEnvironment would parse as TimeFactor=0 + zeroed Seasons/Months.
		WriteLog(MainLog, "SaveEnvironment: refusing partial save before a FullSave has been committed")
		Return False
	EndIf
	F = OpenFile(FinalPath$)
	If F = 0 Then Return False
		WriteInt F, Year
		WriteInt F, Day
		WriteInt F, TimeH
		WriteInt F, TimeM
	CloseFile(F)
	Return True

End Function

; Gets the current season
Function GetSeason()

	For i = 0 To 10
		If Day < SeasonStartDay(i + 1) Return i
	Next
	Return 11

End Function

; Gets the current month
Function GetMonth()

	For i = 0 To 18
		If Day < MonthStartDay(i + 1) Return i
	Next
	Return 19

End Function

; Updates time of day etc. 
Function UpdateEnvironment() 
    
   MinuteChanged = False 
   HourChanged = False 

   milliS = MilliSecs();<<<<<<<<<<<<<<<<< 
   timeDiff = milliS - TimeUpdate;<<<<<<<<<<<<<<<<<< 
   minFactor = 60000 / TimeFactor;<<<<<<<<<<<<<<<<<<< 

   ; Advance by one minute 
   If timeDiff > minFactor;<<<<<<<<<<<<<<<<<<< 
      TimeUpdate = milliS - (timeDiff - minFactor);<<<<<<<<<<<<<<<< 
      TimeM = TimeM + 1 
      MinuteChanged = True 
      If TimeM > 59 
         TimeH = TimeH + 1 
         TimeM = 0 
         HourChanged = True 
         If TimeH > 23 
            TimeH = 0 
            Day = Day + 1 
            CurrentSeason = GetSeason() 
            If Day > MonthStartDay(0) 
               Day = 0 
               Year = Year + 1 
            EndIf 
         EndIf 
      EndIf 
   EndIf 

End Function

; Loads and creates all suns
Function LoadSuns()

	F = ReadFile("Data\Game Data\Suns.dat")
	If F = 0 Then Return False

		Suns = ReadInt(F)
		For i = 1 To Suns
			S.Sun = New Sun
			
		
			;S\TexID = ReadShort(F)

			For j = 0 To 7
				S\TexID[j] = ReadShort(F)
			Next
			
			S\ShowPhases = ReadByte(F)
			S\Phase_Length = ReadByte(F)
			S\CurrentPhase = 0
			
			S\Size# = ReadFloat#(F)
			S\LightR = ReadByte(F)
			S\LightG = ReadByte(F)
			S\LightB = ReadByte(F)
			S\PathAngle# = ReadFloat#(F)
			For j = 0 To 11
				S\StartH[j] = ReadByte(F)
				S\StartM[j] = ReadByte(F)
				S\EndH[j] = ReadByte(F)
				S\EndM[j] = ReadByte(F)
			Next
			S\ShowFlares = ReadByte(F)
		Next

	CloseFile(F)
	Return True

End Function

; Saves sun settings atomically. Crash mid-write previously left
; Suns.dat truncated -- LoadSuns would then read a wrong sun count
; and either skip suns or read garbage for the trailing entries.
Function SaveSuns()

	Local FinalPath$ = "Data\Game Data\Suns.dat"
	Local TempPath$ = SafeWriteOpen(FinalPath$)
	F = WriteFile(TempPath$)
	If F = 0
		WriteLog(MainLog, "SaveSuns: cannot open " + TempPath$ + " for write")
		Return False
	EndIf

		Count = 0
		For S.Sun = Each Sun : Count = Count + 1 : Next
		WriteInt(F, Count)
		For S.Sun = Each Sun

		;WriteShort(F, S\TexID)

			For i = 0 To 7
				WriteShort(F, S\TexID[i])
			Next

			WriteByte(F, S\ShowPhases)
			WriteByte(F, S\Phase_Length)

			WriteFloat(F, S\Size#)
			WriteByte(F, S\LightR)
			WriteByte(F, S\LightG)
			WriteByte(F, S\LightB)
			WriteFloat(F, S\PathAngle#)
			For i = 0 To 11
				WriteByte(F, S\StartH[i])
				WriteByte(F, S\StartM[i])
				WriteByte(F, S\EndH[i])
				WriteByte(F, S\EndM[i])
			Next
			WriteByte(F, S\ShowFlares)
		Next

	Return SafeWriteCommit(TempPath$, FinalPath$, F)

End Function

; Returns the delta (in minutes) between two times
Function TimeDelta(StartH, StartM, EndH, EndM)

	If StartH = EndH ; Start and end are in the same hour
		Return EndM - StartM
	ElseIf StartH < EndH ; Start hour is before end hour
		Return (60 - StartM) + EndM + (60 * (EndH - (StartH + 1)))
	Else ; Start hour is after end hour (i.e. it spans two days)
		Return (60 - StartM) + EndM + (60 * (24 - (StartH + 1))) + (60 * EndH)
	EndIf

End Function