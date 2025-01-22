Include "Modules/IO/Managers/GameDataManager.bb"

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

; Loads all environment settings
Function LoadEnvironment()

	F = ReadFile("Data\Server Data\Environment.dat")
	If F = 0 Then Return CreateEnvironment()
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

	Local gameDataManager.GameDataManager = New GameDataManager()
	GameDataManager::Load(gameDataManager)

		Suns = ListSize(gameDataManager\sunsData\Suns)
		For i = 1 To Suns
			S.Sun = New Sun
			Local sunData.SunData = ListAt(gameDataManager\sunsData\Suns, i - 1)

			For j = 0 To 7
				S\TexID[j] = sunData\TexID[j]
			Next
			
			S\ShowPhases = sunData\ShowPhases
			S\Phase_Length = sunData\Phase_Length
			S\CurrentPhase = 0
			
			S\Size# = sunData\Size
			S\LightR = sunData\LightR
			S\LightG = sunData\LightG
			S\LightB = sunData\LightB
			S\PathAngle# = sunData\PathAngle
			For j = 0 To 11
				S\StartH[j] = sunData\StartH[j]
				S\StartM[j] = sunData\StartM[j]
				S\EndH[j] = sunData\EndH[j]
				S\EndM[j] = sunData\EndM[j]
			Next
			S\ShowFlares = sunData\ShowFlares
		Next

	Delete(gameDataManager)
	Return True

End Function

; Saves sun settings
Function SaveSuns()

	Local gameDataManager.GameDataManager = New GameDataManager()
	GameDataManager::Load(gameDataManager)

		Count = 0
		For S.Sun = Each Sun : Count = Count + 1 : Next
		
		For S.Sun = Each Sun
		
			Local sunData.SunData = New SunData()
		
			For i = 0 To 7
				sunData\TexID[i] = S\TexID[i]
			Next
			
			sunData\ShowPhases = S\ShowPhases
			sunData\Phase_Length = S\Phase_Length
		
			sunData\Size = S\Size#
			sunData\LightR = S\LightR
			sunData\LightG = S\LightG
			sunData\LightB = S\LightB
			sunData\PathAngle = S\PathAngle#
			For i = 0 To 11
				sunData\StartH[i] = S\StartH[i]
				sunData\StartM[i] = S\StartM[i]
				sunData\EndH[i] = S\EndH[i]
				sunData\EndM[i] = S\EndM[i]
			Next
			sunData\ShowFlares = S\ShowFlares

			ListAdd(gameDataManager\sunsData\Suns, sunData)
		Next

	GameDataManager::Save(gameDataManager)
	Delete(gameDataManager)

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