
AppTitle "Blitz UDP Echo Server"

UDPTimeouts 1000

udp=CreateUDPStream( 7 )
If Not udp RuntimeError "CreateUDPStream failed"

Print "Echo Server - "+DottedIP$( UDPStreamIP( udp ) )+":"+UDPStreamPort( udp )

Repeat
	ip=RecvUDPMsg( udp )
	If Eof( udp )<0 RuntimeError "RecvUDPMsg failed"
	If ip
		CopyStream udp,udp	;The Echo bit - COOL!
		SendUDPMsg udp,ip,UDPMsgPort(udp)
		cnt=cnt+1
		Print cnt+")"+DottedIP$( UDPMsgIP( udp ) )+":"+UDPMsgPort( udp )
	EndIf
	Delay 0
Forever