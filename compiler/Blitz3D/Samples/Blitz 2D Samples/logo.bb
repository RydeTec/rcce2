Graphics 640,480:SetBuffer BackBuffer()
AppTitle "LOGO"

Global drawn=CreateImage(640,480)
Global x=320,y=240,Tangle=180
Global quit=0

Type func
Field name$,commands$
End Type
Global funcs.func
Type number
Field name$,value
End Type
Global vars.number

While Not quit
	Cls
	command$=myinput$("Type a Command:",465)
	SetBuffer ImageBuffer(drawn)
	parse(command$)
	SetBuffer BackBuffer()
	draw()
	Flip
Wend
Cls
Text 320-Len("Goodbye!")*FontWidth()/2,240,"Goodbye!"
Flip
Delay 1000
End

Function myinput$(question$,y)
While Not KeyHit(28)
	Cls
	draw()
	GetKeyed=GetKey()
	If KeyHit(14)
		If Len(answer$)>0 Then answer$=Left$(answer,Len(answer)-1)
	ElseIf GetKeyed
		If GetKeyed>31 And GetKeyed<127 Then answer=answer+Chr$(GetKeyed)
	EndIf
	Text 0,y,question+" "+answer
	Flip
Wend
While KeyDown(28)
Wend
Return answer
End Function

Function draw()
	DrawImage drawn,0,0
	Text 0,0,"Position: "+(x-320)+","+(0-(y-240))
	Text 0,15,"Angle: "+wrapvalue(Tangle+180)
	Line x,y,Newxvalue(x,wrapvalue(Tangle+135),15),Newyvalue(y,wrapvalue(Tangle+135),15)
	Line x,y,Newxvalue(x,wrapvalue(Tangle+225),15),Newyvalue(y,wrapvalue(Tangle+225),15)
	Line Newxvalue(x,wrapvalue(Tangle+135),15),Newyvalue(y,wrapvalue(Tangle+135),15),Newxvalue(x,wrapvalue(Tangle+225),15),Newyvalue(y,wrapvalue(Tangle+225),15)
End Function

Function parse(command$)
	command=Trim$(command)
	p=1
	While Mid$(command,p,1)<>" " And p<Len(command)
		p=p+1
	Wend
	frontcommand$=Left$(command,p)
	backcommand$=Right$(command,Len(command)-p)
	Select Trim$(Lower$(frontcommand))
	Case "fd","forwards"
		num=Int(backcommand)
		If num<>0
			oldx=x:oldy=y
			x=3.2*Newxvalue(x,Tangle,num)
			y=2.4*Newyvalue(y,Tangle,num)
			Line oldx,oldy,x,y
		Else
			For vars=Each number
				If vars\name=Trim$(backcommand)
					vared=1
					oldx=x:oldy=y
					x=3.2*Newxvalue(x,Tangle,vars\value)
					y=2.4*Newyvalue(y,Tangle,vars\value)
					Line oldx,oldy,x,y
				EndIf
			Next
			If Not vared	
				SetBuffer FrontBuffer()
				Text 0,450,Trim$(backcommand)+" does not have a value."
			EndIf
		EndIf
	Case "bk","backwards"
		num=Int(backcommand)
		If num<>0
			oldx=x:oldy=y
			x=3.2*Newxvalue(x,Tangle,0-num)
			y=2.4*Newyvalue(y,Tangle,0-num)
			Line oldx,oldy,x,y
		Else
			For vars=Each number
				If vars\name=Trim$(backcommand)
					vared=1
					oldx=x:oldy=y
					x=3.2*Newxvalue(x,Tangle,0-vars\value)
					y=2.4*Newyvalue(y,Tangle,0-vars\value)
					Line oldx,oldy,x,y
				EndIf
			Next
			If Not vared	
				SetBuffer FrontBuffer()
				Text 0,450,Trim$(backcommand)+" does not have a value."
			EndIf
		EndIf
	Case "lt","left"
		num=3.6*Int(backcommand)
		If num<>0
			Tangle=wrapvalue(Tangle+num)
		Else
			For vars=Each number
				If vars\name=Trim$(backcommand)
					vared=1
					Tangle=wrapvalue(Tangle+3.6*vars\value)
				EndIf
			Next
			If Not vared	
				SetBuffer FrontBuffer()
				Text 0,450,Trim$(backcommand)+" does not have a value."
				Delay 1000
			EndIf
		EndIf
	Case "rt","right"
		num=3.6*Int(backcommand)
		If num<>0
			Tangle=wrapvalue(Tangle+num)
		Else
			For vars=Each number
				If vars\name=Trim$(backcommand)
					vared=1
					Tangle=wrapvalue(Tangle-3.6*vars\value)
				EndIf
			Next
			If Not vared	
				SetBuffer FrontBuffer()
				Text 0,450,Trim$(backcommand)+" does not have a value."
				Delay 1000
			EndIf
		EndIf
	Case "define"
		SetBuffer BackBuffer()
		Cls
		texty=45
		funcs.func=New func
		funcs\name=backcommand
		While Lower$(comm$)<>"end"
			comm$=myinput("Editing "+backcommand+":",texty)
			pp=1
			While Mid$(command,pp,1)<>" " And pp<Len(command)
				pp=pp+1
			Wend
			frontcomm$=Left$(comm,pp)
			If frontcomm$<>"define" And frontcomm$<>"end"
				SetBuffer ImageBuffer(drawn)
				parse(comm)
				SetBuffer BackBuffer()
				funcs\commands=funcs\commands+comm+"\"
			Else
				If frontcomm="define" Then Text 0,450,"The define command may not be used when editing!" : Flip : Delay 1000
			EndIf
		Wend
	Case "quit"
		quit=1
	Case "wipe","clear","cls"
		Cls
	Case "move"
		pp=1
		While pp<Len(backcommand) And Mid$(backcommand,pp,1)<>","
			pp=pp+1
		Wend
		x=3.2*Int(Left$(backcommand,pp))+320
		y=2.4*Int(Right$(backcommand,Len(backcommand)-pp))+240
	Case "red"
		num=Int(backcommand)
		Color 2.55*num,ColorGreen(),ColorBlue()
	Case "green"
		num=Int(backcommand)
		Color ColorRed(),2.55*num,ColorBlue()
	Case "blue"
		num=Int(backcommand)
		Color ColorRed(),ColorGreen(),2.55*num
	Case "what","value","print"
		SetBuffer FrontBuffer()
		For vars=Each number
			If vars\name=Trim$(backcommand)
				Text 0,450,vars\name+" equals "+vars\value
				vared=1
			EndIf
		Next
		If Not vared
			Text 0,450,Trim$(backcommand)+" does not have a value."
		EndIf
		Delay 1000
	Case "run"
		file=ReadFile(backcommand)
		While Not Eof(file)
			comm$=ReadLine$(file)
			If Not Eof(file) Then parse(comm$)
		Wend
		CloseFile file
	Default
		For tfuncs.func=Each func
			If tfuncs\name=frontcommand
				funcparse(tfuncs)
				funced=1
			EndIf
		Next
		If Left$(backcommand,1)="="
			For vars.number=Each number
				If vars\name=Trim$(frontcommand)
					vars\value=Trim$(Right$(backcommand,Len(backcommand)-1))
					varred.number=vars
					vared=1
				EndIf
			Next
			If vared=0
				vars=New number
				vars\name=Trim$(frontcommand)
				vars\value=Trim$(Right$(backcommand,Len(backcommand)-1))
				varred.number=vars
				vared=1
			EndIf
			SetBuffer FrontBuffer()
			Text 0,450,varred\name+" equals "+varred\value
			Delay 1000
		EndIf
		If Not funced Or vared
			SetBuffer FrontBuffer()
			Text 0,450,"I don't know what '"+frontcommand+"' means"
			Delay 1000
			Cls
		EndIf
	End Select
End Function

Function funcparse(funct.func)
	p=1
	comm$=funct\commands
	While p<Len(comm)
		While Mid$(funct\commands,p,1)<>"\"
			p=p+1
		Wend
		parse(Left$(comm,p-1))
		comm=Right$(comm,Len(comm)-p)
		Flip
		Delay 10
		p=1
	Wend
End Function

Function Newxvalue(x#,angle,dist#)
Newx#=Sin(angle)*dist+x
Return Newx
End Function
Function Newyvalue(y#,angle,dist#)
Newy#=Cos(angle)*dist+y
Return Newy
End Function

Function Newangle(x#,y#)
dist#=Sqr(x*x+y*y)
angle#=wrapvalue(ASin(x/dist))
If y<0 Then angle=180-angle
Return angle
End Function

Function wrapvalue(angle)
zangle=angle Mod 360
If angle<0 Then zangle=360+angle
Return zangle
End Function