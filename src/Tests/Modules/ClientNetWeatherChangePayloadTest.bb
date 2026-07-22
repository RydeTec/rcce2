Strict
EnableGC

; P_WeatherChange is a fixed [area handle:4][weather:1] client frame. Keep
; its length gate before every decode and weather side effect without loading
; the renderer-heavy ClientNet dependency graph.
Function ClientNetWeatherChangeHasExactFrameGuard%()
	Local F.BBStream = ReadFile("Modules\\ClientNet.bb")
	Local InWeatherCase%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\\Modules\\ClientNet.bb")
	If F = Null Then F = ReadFile("..\\..\\Modules\\ClientNet.bb")
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Case P_WeatherChange") > 0 Then InWeatherCase = True
		If InWeatherCase = True
			If Stage < 3 And Instr(Line$, "RCE_IntFromStr") > 0
				CloseFile F
				Return False
			EndIf
			If Stage < 4 And Instr(Line$, "SetWeather") > 0
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "Len(M\\MessageData$) <> 5") > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, "P_WeatherChange: malformed packet, dropping") > 0 Then Stage = 2
			If Stage = 2 And Trim$(Line$) = "Else" Then Stage = 3
			If Stage = 3 And Instr(Line$, "ServerArea = RCE_IntFromStr(Mid$(M\\MessageData$, 1, 4))") > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, "SetWeather(RCE_IntFromStr(Mid$(M\\MessageData$, 5, 1)))") > 0 Then Stage = 5
			If Stage = 5
				CloseFile F
				Return True
			EndIf
			If Instr(Line$, "Case ") > 0 And Instr(Line$, "Case P_WeatherChange") = 0
				CloseFile F
				Return False
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Test testWeatherChangeRequiresExactFrameBeforeDecode()
	Assert(ClientNetWeatherChangeHasExactFrameGuard%() = True)
End Test
