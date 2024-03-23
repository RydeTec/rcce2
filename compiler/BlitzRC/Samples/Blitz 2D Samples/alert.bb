; please note - this demonstration is a joke - your memory is OK
Graphics 640,480

Alert$("8008000C","not enough memory")

End
Function Alert$(number$,alert$)
width=GraphicsWidth()
innerwidth=width-16
charheight=FontHeight()
height=charheight*6+16
innerheight=height-16
SetBuffer BackBuffer()
flop=0:flopdly=800:flopitnow=MilliSecs()+flopdly
Repeat
	Flip
	Cls
	If flop=0
		Color 255,0,0
		Rect 0,0,width,height
		Color 0,0,0
		Rect 8,8,innerwidth,innerheight
		If MilliSecs()>flopitnow
			flop=1:flopitnow=MilliSecs()+flopdly
		EndIf
	Else
		If MilliSecs()>flopitnow
			flop=0:flopitnow=MilliSecs()+flopdly
		EndIf
	EndIf
	Color 255,0,0
	Text width/2,charheight*1,"GURU MEDITATION : "+Upper$(number$),1,0
	Text width/2,charheight*3,Upper$(alert$),1,0
	Text width/2,charheight*5,"CLICK MOUSE TO CONTINUE",1,0
	
Until MouseHit(1)
End Function