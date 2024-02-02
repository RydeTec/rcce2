
;
;
; 
; On screen instructions where needed
; (Mouse X / Y and mouse buttons)
;
; ESC to skip to next effect
;
; ESC to quit after last effect



bitmap$="blogo.bmp"						; Our background bitmap
										; (any 640x480 .bmp piccy would do)

Graphics 640,480						;  Set the display resolution

Global back
back=LoadAnimImage(bitmap$,640,1,0,480)	; Try to load the bitmap
										;
If Not back Then End					; Quit if bitmap not loaded

SetBuffer BackBuffer()					; Double buffer for a flicker free display



sine_wibble()							; Execute each function in turn
shear_flip()							; 
magnify()								; ESC to step to next one
roll()									;
drag_stretch()							;

End										; End program



Function drag_stretch()
	state=0
	Repeat
		Cls
		sy#=MouseY()
		oldy#=oldy+(MouseY()-oldy)/3
		sy#=Int(oldy)
		If MouseDown(1)
			If state=0
				ly=sy
				state=1
			End If
		Else
			state=0
		End If
		Select state
			Case 0
				For lop=0 To 479
					DrawImage back,0,lop,lop
				Next
			Case 1
				tadd#=ly/sy
				badd#=(479-ly)/(479-sy)
				If sy
				For lop=0 To sy
					DrawImage back,0,lop,tadd*lop
				Next
				End If
				add#=0
				For lop=sy To 479
					DrawImage back,0,lop,ly+add
					add=add+badd
				Next

		End Select
		Rect 0,sy,55,1
		Rect 330,sy,310,1
		Text 60,sy-7,"DRAG & STRETCH  ( Mouse Y + LMB )"
		Flip
	Until KeyDown(1)
	Repeat
		Cls
		Flip
	Until Not KeyDown(1)
End Function


Function roll()
	Repeat
		Cls
		oldy#=oldy+(MouseY()-oldy)/2
		sy#=oldy
		ty#=100
		off=(off+1) Mod 480 
		temp=(off-40)
		If temp<0 Then temp=temp+480
		For lop=99 To 0 Step -1
			temp=(temp+1) Mod 480
			DrawImage back,0,lop,temp
		Next
		temp=off
		For lop=1 To 135
			ty=ty+Sin(lop/1.5)
			temp=(temp+1) Mod 480
			DrawImage back,0,ty,temp
		Next
		For lop=1 To 135
			ty=ty+Cos(lop/1.5)
			temp=(temp+1) Mod 480
			DrawImage back,0,ty,temp
		Next
		temp=(temp+20) Mod 480
		For lop=ty+1 To 479
			temp=temp-1
			If temp<0 Then temp=temp+480
			DrawImage back,0,lop,temp
		Next
		Text 20,184,"ROLLER"
		Flip
	Until KeyDown(1)
	Repeat
		Cls
		Flip
	Until Not KeyDown(1)
End Function


Function magnify()
	Repeat
		Cls
		For lop=0 To 479
			DrawImage back,0,lop,lop
		Next
		oldy#=oldy+(MouseY()-oldy)/2
		sy#=oldy
		If sy<55 Then sy=54
		If sy>445 Then sy=446
		ty#=sy-55
		py#=ty
		For lop=1 To 75
			ty=ty+Sin(lop)
			py=py+Cos(lop)/1.25
			DrawImage back,0,ty,py
		Next
		For lop=1 To 75
			ty=ty+Sin(76-lop)
			py=py+Cos(76-lop)/1.25
			DrawImage back,0,ty,py
		Next
		Text 20,sy-16,"MAGNIFY  ( Mouse Y )"
		Flip
	Until KeyDown(1)
	Repeat
		Cls
		Flip
	Until Not KeyDown(1)
End Function


Function shear_flip()
	Repeat
		Cls
		oldx#=oldx+(MouseX()-oldx)/2
		oldy#=oldy+(MouseY()-oldy)/2
		sx#=Int(oldx):sx2=sx
		sy#=Int(oldy):sy2=sy
		cnt#=0
		If sy<240
			add#=240/(240-sy)
			xadd#=(320-sx)*(add/240)
			Repeat
				DrawImage back,sx-320,sy,cnt
				cnt=cnt+add
				sx=sx+xadd
				sy=sy+1
			Until cnt>479
		Else
			add#=240/(240-(479-sy))
			xadd#=(320-sx)*(add/240)
			Repeat
				DrawImage back,sx-320,sy,cnt
				cnt=cnt+add
				sx=sx+xadd
				sy=sy-1
			Until cnt>479
		End If
		Rect sx2-7,sy2,15,1
		Rect sx2,sy2-7,1,15
		Text 10,10,"MOUSE XPos = "+sx2
		Text 10,30,"MOUSE YPos = "+sy2 
		Flip
	Until KeyDown(1)
	Repeat
		Cls
		Flip
	Until Not KeyDown(1)
End Function


Function sine_wibble()
	lop#=0
	start#=0
	amp#=1
	freq#=1
	oldx#=0
	Repeat
		Cls
		oldx#=oldx+(MouseX()-oldx)/2
		oldy#=oldy+(MouseY()-oldy)/2
		If MouseDown(2)
			amp=oldx/80+1
		End If
		If MouseDown(1)
			freq#=oldy/100+1
		End If
		start=start+freq Mod 359
		For lop=0 To 479
			DrawImage back,Sin(lop*freq+start)*(amp*40),lop,lop
		Next
		Rect oldx-7,oldy,15,1
		Rect oldx,oldy-7,1,15
		Text 10,10,"AMPLITUDE = "+amp+"  ( Mouse X  +  RMB )"
		Text 10,30,"FREQUENCY = "+freq+"  ( Mouse Y  +  LMB )"
		Flip
	Until KeyDown(1)
	Repeat
		Cls
		Flip
	Until Not KeyDown(1)
End Function