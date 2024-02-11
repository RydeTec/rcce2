
;Very simple demo showing the basics of a network game
;
AppTitle "Blitz Multiplayer demo"

Const width=640,height=480

Global send_freq=5	;send every fifth update
Global smoothing=5
Global Ping_time

Type Info
	Field txt$
End Type

Type Player
	Field x#,y#,r#		;where ship appears on screen
	Field dx#,dy#,dr#	;speeds - (recvd pos-current pos)/smoothing
	Field name$,net_id
	
End Type

If Not StartNetGame() Then End

Graphics width,height

send_cnt=1

;get player name
Repeat
	Cls
	Color 0,48,0
	For x=0 To width-1 Step 32
		Rect x,0,1,height
	Next
	For y=0 To height-1 Step 32
		Rect 0,y,width,1
	Next
	Color 255,255,255
	Locate 16,height/4
	name$=Input$( "Player name? " )
Until name$<>""

;create a local player
Global player.Player=New Player
player\x=width/2
player\y=height/2
player\r=0
player\name=name$
player\net_id=CreateNetPlayer( name$ )

Global chat$

SetBuffer BackBuffer()

While Not KeyDown(1)

	UpdateNetwork()
	
	send_cnt=send_cnt-1
	If send_cnt=0
		send_cnt=send_freq
		send=True
	Else
		send=False
	EndIf
	
	UpdatePlayers( send )
	
	RenderAll()
	
	Flip
Wend

End

;update incoming network messages...
Function UpdateNetwork()
	While RecvNetMsg()
		Select NetMsgType()
		Case 1:
			p.Player=FindPlayer( NetMsgFrom() )
			If p<>Null Then UnpackPlayerMsg( NetMsgData$(),p )
		Case 2:
			info( NetPlayerName$( NetMsgFrom() )+":"+NetMsgData$() )
		Case 3:
			SendNetMsg 4,"Pong!",player\net_id,0,0
		Case 4:
			t=MilliSecs()-Ping_time
			info( "Ping: "+t+"ms" )
		Case 100:
			p.Player=New Player
			p\net_id=NetMsgFrom()
			p\name=NetPlayerName$( NetMsgFrom() )
			info( p\name+" has joined the game. " )
		Case 101:
			p.Player=FindPlayer( NetMsgFrom() )
			If p<>Null
				info( p\name+" has left the game. " )
				Delete p
			EndIf
		Case 102:
			info( "I'm the new host! " )
		Case 200:
			EndGraphics
			Print "The session has been lost!"
			WaitKey
			End
		End Select
	Wend
End Function

Function UpdatePlayers( send )

	If KeyHit(59)
		Ping_time=MilliSecs()				
		SendNetMsg 3,"Ping!",player\net_id,0,0
	EndIf
	If KeyHit(60) And send_freq>0 Then send_freq=send_freq-1
	If KeyHit(61) Then send_freq=send_freq+1
	If KeyHit(62) And smoothing>0 Then smoothing=smoothing-1
	If KeyHit(63) Then smoothing=smoothing+1
	
	For p.Player=Each Player
		If NetPlayerLocal( p\net_id )
		
			;L/R rotation
			If KeyDown( 203 )
				p\r=p\r-5
			Else If KeyDown( 205 )
				p\r=p\r+5
			EndIf
			
			;Thrust
			If KeyDown( 200 )
				p\x=p\x+Cos(p\r)*5
				p\y=p\y+Sin(p\r)*5
			EndIf
			
			;Chat stuff
			key=GetKey()
			If key
				If key=13
					If chat$<>"" SendNetMsg 2,chat$,p\net_id,0,0
					chat$=""
				Else If key=8
					If Len(chat$)>0 Then chat$=Left$(chat$,Len(chat$)-1)
				Else If key>=32 And key<127
					chat$=chat$+Chr$(key)
				EndIf
			EndIf
			
			;transmit player position and rot
			If send
				SendNetMsg 1,PackPlayerMsg$(p),p\net_id,0,0
			EndIf
		Else
			p\x=p\x+p\dx
			p\y=p\y+p\dy
			p\r=p\r+p\dr
		EndIf
	Next
End Function

Function RenderPlayers()
	For p.Player=Each Player
		x1=p\x+Cos(p\r)*8
		y1=p\y+Sin(p\r)*8
		x2=p\x+Cos(p\r+150)*8
		y2=p\y+Sin(p\r+150)*8
		x3=p\x+Cos(p\r-150)*8
		y3=p\y+Sin(p\r-150)*8
		
		Color 255,255,255
		Line x1,y1,x2,y2:Line x2,y2,x3,y3:Line x3,y3,x1,y1
		
		Color 0,0,255
		Text p\x+12,p\y,p\name,0,1
	Next
	
End Function

Function info( t$ )
	i.Info=New Info
	i\txt$=t$
	Insert i Before First Info
End Function

Function RenderAll()
	;render everything!
	Cls
	Color 0,255,0
	Text 0,FontHeight()*0,"Multiplayer!"
	Text 0,FontHeight()*1,"F1:ping, type to chat"
	Text 0,FontHeight()*2,"Send freq:"+send_freq+" (F2-, F3+)"
	Text 0,FontHeight()*3,"Smoothing:"+smoothing+" (F4-, F5+)"
	Text 0,FontHeight()*4,">"+chat$
	y=FontHeight()*5
	r=255
	For i.Info=Each Info
		If r>0
			Color r,r/2,0
			Text 0,y,i\txt$
			y=y+FontHeight()
			r=r-12
		Else
			Delete i
		EndIf
	Next
	
	RenderPlayers()
	
End Function

;pack player details into a string.
;Not very elegant at present...more funcs coming!
Function PackPlayerMsg$( p.Player )
	Return LSet$( Int(p\x),6 )+LSet$( Int(p\y),6 )+LSet$( Int(p\r),6 )
End Function

;unpack player details from a string
Function UnpackPlayerMsg( msg$,p.Player )
	x=Mid$( msg$,1,6 )
	y=Mid$( msg$,7,6 )
	r=Mid$( msg$,13,6 )
	p\dx=(x-p\x)/smoothing
	p\dy=(y-p\y)/smoothing
	p\dr=(r-p\r)/smoothing
End Function

;find player with player id
Function FindPlayer.Player( id )
	For p.Player=Each Player
		If p\net_id=id Then Return p
	Next
End Function
