;
; CASCADE EXAMPLE THING - Written by Rob Hutchinson 
; 
;
; Switch debugging off for fastest speeds.
; Runs at decent speeds on a PII-350 with 6 Stars Per Frame.
; 

AppTitle "Cascading Star Effect"                  ; Give the window a title.
Const width=800,height=600,depth=16               ; Bitmap constants.
Graphics width,height,depth                       ; Setup the screen.
SetBuffer BackBuffer ()
Cls                                               ; Clear the screen.

Dim cab(501)                                      ; Lookup table for our stars.
For i=0 To 500
	cab(i)=Sin(i*Pi/180)*100
Next

Type star                                         ; Type for the stars.
	Field X#,Y#,AX#,AY#
	Field R,G,B
End Type

;
; Change some of these for different results.
;
Const  MaxStars=4000,StarsPer=4                   ; Maximum number of stars/Stars per frame.
Global StarSpeed#=0.10                            ; Star speed.
Global IOSpeed#=0.05                              ; InOut speed.
Global IOTo=10.00                                 ; How far back to move the ship.
Global TriAng1=0
Global InOut#=10                                  ; Floating in and out of the screen.
Global CurStars=0,Direct=0

While Not KeyDown(1)                              ; Do until ESC is pressed
	SetStars()
	Cls
	URenderStars()
	Flip
Wend

End

;
; Functions.
;
Function SetStars()                               ; This function updates the positions of the stars.
	For k=0 To StarsPer
  	TriAng1=Rnd(359)
   	If CurStars < MaxStars
			s.star=New star
			s\X=(width/2)+(cab(TriAng1))/InOut
			s\Y=(height/2)+(cab(TriAng1+90))/InOut
			s\AX=((cab(TriAng1)/InOut)*StarSpeed)
			s\AY=((cab(TriAng1+90)/InOut)*StarSpeed)
			r=Rnd(10,230)
			s\R=r : s\G=r : s\B=r                 ; Set colour of pixel.
		EndIf
	Next
End Function

Function URenderStars()                           ; This function renders the stars to the screen.
	If InOut<=1 Then Direct=1
	If Direct=0 Then InOut=InOut-IOSpeed
	If InOut>=IOTo Then Direct=0
	If Direct=1 Then InOut=InOut+IOSpeed

	For s.star=Each star
		If s\X>width Or s\X<0 Or s\Y>height Or s\Y<0
			Delete s
		Else
			Color s\R,s\G,s\B
			Plot s\X,s\Y
			s\X=s\X+s\AX
			s\Y=s\Y+s\AY
		EndIf
	Next
End Function