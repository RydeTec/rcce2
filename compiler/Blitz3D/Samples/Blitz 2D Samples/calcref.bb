
;program to calculate monitor refresh rate...

Graphics 640,480

fps#=CalcRefresh()
ms#=1000.0/fps

Text 0,0,"Frame freq:"+fps+" period:"+ms+"ms"

WaitKey

Function CalcRefresh#()

	Local time,t_time,elapsed,t_elapsed,cnt,sum

	time=MilliSecs()
	Repeat
		t_time=time
		time=MilliSecs()
		elapsed=time-t_time
		If Abs( elapsed-t_elapsed )<2
			cnt=cnt+1:sum=sum+elapsed
			If cnt=60 Then Return 1000.0/sum*cnt
		Else
			cnt=0:sum=0
			t_elapsed=elapsed
		EndIf
		Flip
	Forever
	
End Function