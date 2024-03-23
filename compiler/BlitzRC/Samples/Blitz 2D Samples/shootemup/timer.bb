Global oldtimer=MilliSecs()

While Not KeyDown(1)
	Print timersec(3)
Wend

Function timersec(endtime)

	stoptime=endtime*1000

	If MilliSecs()<oldtimer+stoptime
		Return True
	Else
		Return False
	EndIf

End Function

Function timermilli(endtime)

	If MilliSecs()<oldtimer+endtime
		Return True
	Else
		Return False
	EndIf

End Function
