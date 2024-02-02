; Fireworks by Einar Wedoe
; I'm sure you can optimize this if you really want to  :-)

Global x=640			
Global y=480
Graphics x,y,16,1

Global antall=100																	; Dots for each explosion 																	
Global ny=1																			; Flag for explosion
Global neste=60																		; Counter for rasters pr explosion
Global yhalf=y/2																	; Just to avoid making this calculation on the fly
Global xhalf=x/2																	; Just to avoid making this calculation on the fly
Global gravity#=1.1

SeedRnd MilliSecs()																	; Randomize

Type prikk																			; Dot-type
   		Field xp#,yp#																; x+y Position
   		Field xvel#,yvel#															; x+y Speed
		Field xaks#,yaks#															; x+y Acceleration
		Field xplass#,yplass#														; x+y Offset from center
   		Field farge#																; Dot-color	
		Field degrees																; Dot degree ?
		Field count#																; Couter for how long to see it ?
		Field grav#																	; y-Gravity

End Type

newexpl																				; Create explosion

SetBuffer BackBuffer()
;-------------------------------
While Not KeyDown(1)																; Wait for ESC

animerfyr
If KeyDown(57) Then ny=1															; Space for more  :-)
neste=neste-1																		; Count down
	If neste=0 Then 
				ny=1
				neste=Rand(10,60)
				EndIf

If ny=1 Then 																		; New explosion
		newexpl 
		ny=0
		EndIf

Flip
Cls  
Wend

End
;-------------------------------
Function animerfyr()
 For fyr.prikk=Each prikk							
		
	fyr\yp#=Sin(fyr\degrees)*fyr\yvel#												; Calculate degree Y
 	fyr\yp#=-fyr\yp#+yhalf															; Invert Y
	fyr\xp#=Cos(fyr\degrees)*fyr\xvel#+xhalf										; Calculate degree X
							
		fyr\xvel=fyr\xvel+fyr\xaks													; Acceleration y
		fyr\yvel=fyr\yvel+fyr\yaks													; Acceleration x
			
		fyr\count=fyr\count#+1.1													; Count down to dot disappear
			
						fyr\yp#=fyr\yp#+fyr\yplass#									; Offset y
						fyr\xp#=fyr\xp#+fyr\xplass#									; offset x
		
		fyr\grav#=fyr\grav#+gravity														; Gravity
		fyr\yp#=fyr\yp#+fyr\grav													

		fyr\yp#=fyr\yp#+fyr\count													; Increase Acceleration
													
		fyr\farge=fadecolor(fyr\farge)												; Fade color
	
		plot_pixel_fast(fyr\xp#,fyr\yp#,fyr\farge)									; Plot
		
		If fyr\xp > x Or fyr\xp < 0 Or fyr\yp > y-10 Or fyr\yp < 10 Then 			; If outside screen, kill it !
																	Delete fyr
																	Goto nestefyr
																	EndIf
		If fyr\farge < 50 Then Delete fyr											; If faded down, kill it !
.nestefyr	
 Next
End Function

;-------------------------------
Function newexpl()
	tmpx=Rand(-300,300)																; Set rnd offset x
	tmpy=Rand(-200,200)																; Set rnd offset y
	tmpc=Rand($ffffff)																; Random color


For a=1 To antall																	; Dots for explosion
  	fyr.prikk=New prikk

	fyr\degrees=Rand(360)															; Make circle
	
	fyr\farge=tmpc																	; Color
;	fyr\farge=Rand($ffffff)															; Use this line for multiple colors

	fyr\xplass#=tmpx																; Set offset x
	fyr\yplass#=tmpy																; Set offset y

	fyr\yvel#=Rnd(1,10)																; Velocity y
	fyr\yaks#=Rnd(1,2)																; Acceleration y
	fyr\xvel#=Rnd(1,10)																; Velocity x
	fyr\xaks#=Rnd(1,2)																; Acceleration x
 Next


End Function
;-------------------------------
Function fadecolor(tmp)																

r=(tmp Shr 16) And 255																; Get $ff0000
g=(tmp Shr 8) And 255																; Get $00ff00
b=tmp And 255																		; Get $0000ff

r=r-1																				; Fade down
g=g-1																				
b=b-1

If r < 1 Then r=1																	; Check for minus
If g < 1 Then g=1
If b < 1 Then b=1

tmp=0																				; Reset tmp

tmp=tmp + r Shl 16																	; New $ff0000
tmp=tmp + g Shl 8																	; New $00ff00
tmp=tmp + b																			; New $0000ff

Return tmp
End Function
;-------------------------------
Function plot_pixel_fast(py#,px#,col)
LockBuffer()																		; Lock
WritePixelFast py#,px#,col															; Plot fast
UnlockBuffer()																		; Unlock
End Function
;-------------------------------