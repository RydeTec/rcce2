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
	Field ShowPhases;				0 = disable phases, 1 = show phases
	Field Phase_Length;				in days
	Field TexID[7];					8 possible phase textures

	Field StartH[11], StartM[11], EndH[11], EndM[11]
	Field PathAngle#
	Field ShowFlares
	Field Flares[10]
End Type

; Loads all environment settings
Function LoadEnvironment()

	F = ReadFile("Data\Server Data\Environment.dat")
	If F = 0 Then Return False
		Year = ReadInt(F)
		Day = ReadInt(F)
		TimeH = ReadInt(F)
		TimeM = ReadInt(F)
		TimeFactor = ReadInt(F)
		For i = 0 To 11
			SeasonName$(i) = ReadString$(F)
			SeasonStartDay(i) = ReadInt(F)
			SeasonDuskH(i) = ReadInt(F)
			SeasonDawnH(i) = ReadInt(F)
		Next
		For i = 0 To 19
			MonthName$(i) = ReadString$(F)
			MonthStartDay(i) = ReadInt(F)
		Next
	CloseFile(F)
	TimeUpdate = MilliSecs()
	CurrentSeason = GetSeason()
	Return True

End Function

; Saves all environment settings
Function SaveEnvironment(FullSave = False)

	If FullSave = True
		F = WriteFile("Data\Server Data\Environment.dat")
	Else
		F = OpenFile("Data\Server Data\Environment.dat")
	EndIf
	If F = 0 Then Return False
		WriteInt F, Year
		WriteInt F, Day
		WriteInt F, TimeH
		WriteInt F, TimeM
		If FullSave = True
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
		EndIf
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
			For j = 0 To 7
				S\TexID[j] = ReadShort(F)
			Next
			S\ShowPhases = ReadByte(F)
			S\Phase_Length = ReadByte(F)
			
			;S\TexID = ReadShort(F)
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

; Saves sun settings
Function SaveSuns()

	F = WriteFile("Data\Game Data\Suns.dat")

		Count = 0
		For S.Sun = Each Sun : Count = Count + 1 : Next
		WriteInt(F, Count)
		For S.Sun = Each Sun
		For i = 0 To 7
				WriteShort(F, S\TexID[i])
			Next
			WriteByte(F, S\ShowPhases)
			WriteByte(F, S\Phase_Length)

			;WriteShort(F, S\TexID)
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

	CloseFile(F)

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