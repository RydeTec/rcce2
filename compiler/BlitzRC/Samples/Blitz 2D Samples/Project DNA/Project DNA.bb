AppTitle="Project DNA"
; Project DNA inspired by the DNA in Iridis Alpha during pause mode.
; This version by George Bray
; george@sheepsoft.org.uk
chnBackground=PlayMusic("Return.xm")
Graphics 800,600,16

SetBuffer BackBuffer()

Global num_stars = 300

Type stars
	Field x#
	Field y#
	Field brgb
	Field spd#
	Field timer
	Field rgb
	Field pulsating
	Field pulse_down
End Type

Type wave1
	Field x_start#
	Field x#
	Field y#
	Field sine#
	Field counter#
	Field col1
	Field col2
	Field col3
End Type

Type wavet
	Field x_start#
	Field x#
	Field y#
	Field sine#
	Field counter#
	Field col1
	Field col2
	Field col3
End Type

Global wave_1_speed# = 2
Global wave_1_freq#  = 50
Global wave_2_speed# = 2
Global wave_2_freq#  = 50

generate_stars()
generate_waves()

; cursor right = speed +
; cursor left  = speed -
; cursor up    = freq +
; cursor down  = freq -

; a = speed +
; s = speed -
; q = freq +
; z = freq -
While Not KeyDown(1)
	update_stars()
	update_waves()	

	If KeyDown(205)
		wave_1_speed# = wave_1_speed# + .1 
	EndIf
	If KeyDown(203)
		wave_1_speed# = wave_1_speed# - .1
	EndIf
	If KeyDown(200)
		wave_1_freq# = wave_1_freq# + 1
	EndIf
	If KeyDown(208)
		wave_1_freq# = wave_1_freq# - 1
	EndIf
	
	If KeyDown(30)
		wave_2_speed# = wave_2_speed# + .1
	EndIf
	If KeyDown(31)
		wave_2_speed# = wave_2_speed# - .1
	EndIf
	If KeyDown(16)
		wave_2_freq# = wave_2_freq# + 1
	EndIf
	If KeyDown(44)
		wave_2_freq# = wave_2_freq# - 1
	EndIf
	Color 255,255,255
	Text 280,00,"DNA By George Bray"
	Text 230,15,"Original design Jeff YAK Minter"
	Text 235,30,"Trad Peruvian Tune from RMC II"

	Text 20,205, "DNA Alpha Thread"
	Text 20,220, "Cursor right = speed +"
	Text 20,230, "Cursor Left  = speed -"
	Text 20,240,  "Cursor up = freq +"
	Text 20,250,  "Cursor down = freq -" 
	
	Text 20,280,"DNA Beta Thread"
    Text 20,295, "A = speed + "
    Text 20,305, "S = speed - "
    Text 20,315, "Q = freq + "
	Text 20,325, "Z = freq -"
	
	

	Flip
	Cls
Wend

Function generate_waves()
	For t = 1 To 34
		b.wave1 = New wave1
		b\x_start# = 320
		b\x# = 0
		b\y# = (t * 16) - 16
		b\counter# = t * 8
		b\col1 = Rnd(512)
		b\col2 = Rnd(512)
		b\col3 = Rnd(512)
	Next

	For t = 1 To 34
		bb.wavet = New wavet
		bb\x_start# = 320
		bb\x# = 0
		bb\y# = (t * 16) - 16
		bb\counter# = t * 8
		bb\col1 = Rnd(512)
		bb\col2 = Rnd(512)
		bb\col3 = Rnd(512)
	Next
End Function

Function update_waves()
	For b.wave1 = Each wave1
		b\sine# = Sin(b\counter) * 3 * Sin(b\counter / 2) * wave_1_freq#
		b\counter# = b\counter# + wave_1_speed#
		b\x# = b\x_start# + b\sine#
		Color b\col1,b\col2,b\col3
		Rect b\x#,b\y#,64,64
	Next

	For bb.wavet = Each wavet
		bb\sine# = Sin(bb\counter) * 3 * Sin(bb\counter / 2) * wave_2_freq#
		bb\counter# = bb\counter# + wave_2_speed#
		bb\x# = bb\x_start# - bb\sine#
		Color bb\col1,bb\col2,bb\col3
		Rect bb\x#,bb\y#,64,64
	Next
End Function

Function generate_stars()
	For cnt = 1 To num_stars
		s.stars = New stars
		s\x# = Rnd(0,799)
		s\y# = Rnd(0,599)
		
		rn = Rnd(1,4)
		Select rn
			Case 1
				s\brgb = 255
				s\spd# = 2
			Case 2
				s\brgb = 150
				s\spd# = 1.5
			Case 3
				s\brgb = 100
				s\spd# = 1
			Case 4
				s\brgb = 50
				s\spd# = .5
		End Select
		s\timer = Rnd(50,200)
		s\pulsating = False
	Next
End Function

Function update_stars()
	For s.stars = Each stars
		If s\x# > 840
			s\x# = -10
		EndIf
		If s\pulsating = False
			s\timer = s\timer - 1
		EndIf
		If s\timer = 0
			s\pulsating = True
			s\rgb = s\brgb
			s\pulse_down = True
			s\timer = -1
		EndIf
		If s\pulsating = True
			Color s\rgb,s\rgb,s\rgb
			Rect s\x#,s\y#,4,4
			s\x# = s\x# + s\spd#
			If s\pulse_down = True
				s\rgb = s\rgb - 4
				If s\rgb <= 0
					s\pulse_down = False
					s\rgb = 0
				EndIf
			Else
				s\rgb = s\rgb + 4
				If s\rgb >= s\brgb
					s\rgb = s\brgb
					s\timer = Rnd(50,200)
					s\pulsating = False
				EndIf
			EndIf
		Else
			Color s\brgb,s\brgb,s\brgb
			Rect s\x#,s\y#,4,4
			s\x# = s\x# + s\spd#			
		EndIf
	Next
End Function

End