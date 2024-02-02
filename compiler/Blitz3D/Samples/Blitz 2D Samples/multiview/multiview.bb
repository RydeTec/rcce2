
;    
;
; Displays 5 irregular shaped viewports
;
;
; Each viewport can display a scrolling image
;  - Use keys: 1,2,3,4,5 on main keyboard to toggle
;              each scrolling image ON/OFF
;
; Each viewport can also move round the screen
;  - Use keys: F1,F2,F3,F4,F5 to toggle
;              each moving viewport ON/OFF
;
; (and it can do both at the same time :)
;
; SPACE BAR  resets everything to the start values
;
; ESC key to quit
;
; 


bitmap$="beetle2.bmp"					; Our background bitmap
										; (any 800x600 .bmp piccy would do)

Const width=800,height=600				; Set the display
Graphics width,height					;  resolution

SetBuffer BackBuffer()					; Double buffer

back=LoadImage(bitmap$)					; Try to load the bitmap
										;
If Not back Then End					; Ooops! No bitmap found,
										;  we better quit

HandleImage back,0,0					; Set the bitmap image
										;  handle to top left

Type segment							; Define a custom variable
	Field x,y,xs,ys						;
	Field xadd,yadd						;
	Field xpic,ypic						;
	Field xoff,yoff						;
	Field scrollpic,scrollport			;
	Field pskey,pskeypressed			;
	Field vskey,vskeypressed			;
End Type								;

Gosub Set_Segment_Values				; Set the start values


; ********************* PROGRAM MAIN LOOP ********************

While Not KeyDown(1)						; Quit key check


	If KeyDown(57)							; Space key check
	    Delete Each segment					; Delete the segments	
		Gosub Set_Segment_Values			; Reset start values
	End If									;


	Viewport 0,0,800,600					; Set viewport to full screen
	Cls										; Clear the screen


	For s.segment=Each segment				; Cycle through all segments


		If KeyDown(s\pskey)					; Is this segments "picture scroll key"
			If s\pskeypressed=0				; pressed ??
				s\pskeypressed=1			;
				s\scrollpic=1-s\scrollpic	; Uses keys 1,2,3,4,5 on main KB
			End If							; to toggle on/off
		Else								;
	   	 	If s\pskeypressed=1				;
				s\pskeypressed=0			;
	    	End If							;
		End If								;


		If KeyDown(s\vskey) 				; Is this segments "viewport scroll key"
			If s\vskeypressed=0				; pressed ??
				s\vskeypressed=1			;
				s\scrollport=1-s\scrollport	; Uses keys F1,F2,F3,F4,F5
			End If							; to toggle on/off
		Else								;
		    If s\vskeypressed=1				;
			    s\vskeypressed=0			;
		    End If							;
		End If								;


		If s\scrollpic=1						; Should this segment
			s\xpic=s\xpic+s\xadd				; be scrolling it`s
			If s\xpic<0 Or s\xpic>(width-s\xs)	; picture ??
				s\xadd=0-s\xadd					;
				s\xpic=s\xpic+s\xadd			; If yes.. do it
			End If								;
			s\ypic=s\ypic+s\yadd				; If no... skip it :)
			If s\ypic<0 Or s\ypic>(height-s\ys)	;
				s\yadd=0-s\yadd					;
				s\ypic=s\ypic+s\yadd			;
			End If								;
		End If									;

		
		If s\scrollport=1						; Should this segment
			s\x=s\x+s\xoff						; be scrolling it`s
			If s\x<0 Or s\x>(width-s\xs)		; viewport
				s\xoff=0-s\xoff					;
				s\x=s\x+s\xoff					; If yes.. do it
			End If								;
			s\y=s\y+s\yoff						; If no... skip it :)
			If s\y<0 Or s\y>(height-s\ys)		;
				s\yoff=0-s\yoff					;
				s\y=s\y+s\yoff					;
			End If								;
		EndIf									;


		Viewport s\x,s\y,s\xs,s\ys				; Set the viewport area
		DrawBlock back,s\x-s\xpic,s\y-s\ypic	; Draw the image


	Next										; End of cycle through segments


	Flip										; Display the freshly drawn screen

		
Wend											; Go back and do it all again :)

; ************************************************************


End                 				; End the program



.Set_Segment_Values  				; Define the segments
									; and set the initial values

	s.segment=New segment
	s\x=0:s\y=0:s\xs=500:s\ys=200
	s\xadd=1:s\yadd=1
	s\xpic=0:s\ypic=0
	s\xoff=2:s\yoff=1
	s\scrollpic=0
	s\scrollport=0
	s\pskey=2
	s\pskeypressed=0
	s\vskey=59
	s\vskeypressed=0
						
	s.segment=New segment
	s\x=500:s\y=0:s\xs=300:s\ys=400
	s\xadd=1:s\yadd=1
	s\xpic=500:s\ypic=0
	s\xoff=1:s\yoff=2
	s\scrollpic=0
	s\scrollport=0
	s\pskey=3
	s\pskeypressed=0
	s\vskey=60
	s\vskeypressed=0

	s.segment=New segment
	s\x=0:s\y=200:s\xs=300:s\ys=400
	s\xadd=1:s\yadd=1
	s\xpic=0:s\ypic=200
	s\xoff=3:s\yoff=2
	s\scrollpic=0
	s\scrollport=0
	s\pskey=4
	s\pskeypressed=0
	s\vskey=61
	s\vskeypressed=0
		
	s.segment=New segment
	s\x=300:s\y=400:s\xs=500:s\ys=200
	s\xadd=1:s\yadd=1
	s\xpic=300:s\ypic=400
	s\xoff=2:s\yoff=3
	s\scrollpic=0
	s\scrollport=0
	s\pskey=5
	s\pskeypressed=0
	s\vskey=62
	s\vskeypressed=0

	s.segment=New segment
	s\x=300:s\y=200:s\xs=200:s\ys=200
	s\xadd=1:s\yadd=1
	s\xpic=300:s\ypic=200
	s\xoff=4:s\yoff=4
	s\scrollpic=0
	s\scrollport=0
	s\pskey=6
	s\pskeypressed=0
	s\vskey=63
	s\vskeypressed=0

Return				


; Nothing more down here..... go back up a bit :)











; Honest, that`s it.....
















; See.