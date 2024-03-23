;
; VECTOR BALLS - Written by Rob Kihl and Rob Hutchinson - 16.08.2000
;
; Lots of lovely lookup tables :)
; Switch debugging off for fastest speeds.
; No need to modify variables in the program, change them at prompt.
; 

AppTitle "Vector Balls"									; Give the window a title.
;
; Get some input from the user..
;
Print "VECTOR BALLS"
Repeat
	width=Input("Width of Matrix (1-50, Default=8): ")
	If width=0 Then width=8
Until width>=1 And width<=50

Repeat
	fov=Input("Field of Vision (10-1000, Default=180):")
	If fov=0 Then fov=180
Until fov>=10 And fov<=1000

Repeat
	oz=Input("Z-Vision (10-1000, Default=340)")
	If oz=0 Then oz=340
Until oz>=10 And oz<=1000

;
; Build the screen
;
Const widtha=1024,heighta=768,depth=16				; Bitmap constants.
Graphics widtha,heighta;,depth						; Setup the screen.
SetBuffer BackBuffer()

StoneImage=LoadImage("Stone.bmp")
Dim stones(31)

sp=(width*2+1)*(width*2+1)
Dim x(sp+1),y(sp+1),z(sp+1),sx((sp*4)+1),sy((sp*4)+1),sz((sp*4)+1)
Dim sitab#(361),cotab#(361),cx(sp+1),cy(sp+1),cz(sp+1)
Dim order(sp+1)

mor=0 : aktobj=0 : db=-1 : scx=0 : scy=0 : temp=0
centrex=widtha/2 : centrey=heighta/2
flipspeed=200

;****** setup states ******

i=0
For j=-width To width
	For k=-width To width
		sx(i)=k*20
		sy(i)=j*20
		If j>0
			sz(i)=j*15+Abs(k)*15-45
		Else
			sz(i)=j*-15+Abs(k)*15-45
		EndIf
		x(i)=sx(i)
		y(i)=sy(i)
		z(i)=sz(i)
		i=i+1
	Next
Next

For j=-width To width
	For k=-width To width
		sx(i)=k*20
		sy(i)=j*20
		sz(i)=j*k*-5
		i=i+1
	Next
Next

For j=-width To width
	For k=-width To width
		sx(i)=k*20
		sy(i)=j*20
		sz(i)=j*k*5
		i=i+1
	Next
Next

For j=-width To width
	For k=-width To width
		sx(i)=k*20
		sy(i)=j*20
		sz(i)=10
		i=i+1
	Next
Next
;
; Calc sin/cos tables.
;
For i=0 To 360
	sitab(i)=Sin(i)
	cotab(i)=Cos(i)
Next

ax=0
ay=0
az=0
db=0

For i=0 To sp
	order(i)=i
Next

Repeat

	Cls
	;
	; Rotate 
	;
	ax=ax+4 : If ax>359 Then ax=0
	ay=ay+3 : If ay>359 Then ay=0
	az=ay+1 : If az>359 Then az=0

	For i=0 To sp-1
		;
		; X rotation
		;
		temp =y(i)*cotab(ax)-z(i)*sitab(ax)
		cz(i)=z(i)*cotab(ax)+y(i)*sitab(ax)
		cy(i)=temp
		;
		; Y rotation
		;
		temp =cz(i)*cotab(ay)-x(i)*sitab(ay)
		cx(i)=x(i)*cotab(ay)+cz(i)*sitab(ay)
		cz(i)=temp
		;
        ; Z rotation
		;
		temp =cx(i)*cotab(az)-cy(i)*sitab(az)
		cy(i)=cy(i)*cotab(az)+cx(i)*sitab(az)
		cx(i)=temp
		;
		; World Coords
		;
		cz(i)=cz(i)+oz
	Next

	; Z sorting 

	For i=0 To sp-1
		For j=i+1 To sp-1
			If cz(order(i))<cz(order(j))
				temp=order(i)
				order(i)=order(j)
				order(j)=temp
			EndIf
		Next
	Next
	;
	; Render 
	;
	For i=0 To sp-1
		;
		; Dont draw if ball behind camera or at z position 0.
		;
		If cz(order(i))>0
			scx=cx(order(i))*fov/cz(order(i))+centrex
			scy=cy(order(i))*fov/cz(order(i))+centrey
			DrawImage StoneImage,scx,scy
		EndIf
	Next
	;
	; Morph.
	;
	mor=mor+2
	For i=0 To sp-1
		If x(i)<sx(i+aktobj) : x(i)=x(i)+2 : EndIf
		If y(i)<sy(i+aktobj) : y(i)=y(i)+2 : EndIf
		If z(i)<sz(i+aktobj) : z(i)=z(i)+2 : EndIf
		If x(i)>sx(i+aktobj) : x(i)=x(i)-2 : EndIf
		If y(i)>sy(i+aktobj) : y(i)=y(i)-2 : EndIf
		If z(i)>sz(i+aktobj) : z(i)=z(i)-2 : EndIf
	Next

	If mor>flipspeed
		mor=0 : aktobj=aktobj+sp
		If aktobj=sp*4 : aktobj=0 : EndIf
	EndIf
	Flip
Until KeyDown(1)

Repeat : VWait : Until Not KeyDown(1)
EndGraphics

Print "" : Print "End of program. [ESC]."
Repeat : VWait : Until KeyDown(1)

End