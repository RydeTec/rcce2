
AppTitle "Blitz UDP Echo Client"

host$=Input$( "Host:" )
If host$="" Then End
If Not CountHostIPs( host$ ) RuntimeError "host not found"
host_ip=HostIP(1)

UDPTimeouts 5000

udp=CreateUDPStream()
If Not udp RuntimeError "CreateUDPStream failed"

Print "Echo Client - "+DottedIP$( UDPStreamIP( udp ) )+":"+UDPStreamPort( udp )

Repeat
	msg$=Input$( "Message to echo:" )
	
	WriteLine udp,msg$
	WriteInt udp,MilliSecs()
	SendUDPMsg udp,host_ip,7
	
	If Eof( udp )<0 RuntimeError "SendUDPMsg failed"
	
	ip=RecvUDPMsg( udp )
	If Eof( udp )<0 RuntimeError "RecvUDPMsg failed"
	
	If ip
		t=MilliSecs()
		reply$=ReadLine$( udp )
		ms=t-ReadInt( udp )
		Print "Reply: "+reply$+" ("+ms+"ms)"
	Else
		Print "Timeout"
	EndIf
Forever