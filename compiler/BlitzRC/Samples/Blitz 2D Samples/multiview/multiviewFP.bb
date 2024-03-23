; This is the same program as "multiview"
; but I slapped the "firepaint" demo on top
; to see what would happen to the speed......
;
; See multiview.bb for info
;
; See firepaint.bb for info
;
; 
; Give it a try :)

bitmap$="beetle2.bmp"

Const width=800,height=600,gravity#=.1
Const intensity=10
Graphics width,height

SetBuffer BackBuffer()

back=LoadImage(bitmap$)
If Not back Then End

HandleImage back,0,0

Type Frag
	Field x#,y#,xs#,ys#
	Field r,g,b
End Type

Type segment
	Field x,y,xs,ys
	Field xadd,yadd
	Field xpic,ypic
	Field xoff,yoff
	Field scrollpic,scrollport
	Field pskey,pskeypressed
	Field vskey,vskeypressed
End Type

Gosub Set_Segment_Values

While Not KeyDown(1)


	If KeyDown(57)
	    For lop=1 To 5
			temp.segment = First segment
			If temp<>Null
				Delete temp
			End If
		Next
		Gosub Set_Segment_Values
	End If

	Viewport 0,0,800,600
	Cls
	If MouseDown(1)
		CreateFrags()
	Else
		Color 255,255,255
		Rect MouseX(),MouseY()-3,1,7
		Rect MouseX()-3,MouseY(),7,1
	EndIf



	For s.segment=Each segment
	
		If KeyDown(s\pskey) 
			If s\pskeypressed=0
				s\pskeypressed=1
				s\scrollpic=1-s\scrollpic
			End If
		Else
	   	 	If s\pskeypressed=1
				s\pskeypressed=0
	    	End If
		End If

		If KeyDown(s\vskey) 
			If s\vskeypressed=0
				s\vskeypressed=1
				s\scrollport=1-s\scrollport
			End If
		Else
		    If s\vskeypressed=1
			    s\vskeypressed=0
		    End If
		End If

		If s\scrollpic=1
			s\xpic=s\xpic+s\xadd
			If s\xpic<0 Or s\xpic>(width-s\xs)
				s\xadd=0-s\xadd
				s\xpic=s\xpic+s\xadd
			End If
			s\ypic=s\ypic+s\yadd
			If s\ypic<0 Or s\ypic>(height-s\ys)
				s\yadd=0-s\yadd
				s\ypic=s\ypic+s\yadd
			End If
		End If
	
		If s\scrollport=1
			s\x=s\x+s\xoff
			If s\x<0 Or s\x>(width-s\xs)
				s\xoff=0-s\xoff
				s\x=s\x+s\xoff
			End If
			s\y=s\y+s\yoff
			If s\y<0 Or s\y>(height-s\ys)
				s\yoff=0-s\yoff
				s\y=s\y+s\yoff
			End If
		EndIf
	
		Viewport s\x,s\y,s\xs,s\ys
		DrawBlock back,s\x-s\xpic,s\y-s\ypic
	
	Next

	Viewport 0,0,800,600
	UpdateFrags()
	If MouseDown(1)
		CreateFrags()
	Else
		Color 255,255,255
		Rect MouseX(),MouseY()-3,1,7
		Rect MouseX()-3,MouseY(),7,1
	EndIf
	RenderFrags()

	Flip
Wend

End



.Set_Segment_Values

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

Function CreateFrags()
	count=Rnd(intensity)+intensity
	anstep#=(2.0*Pi/count)*180
	an#=Rnd(anstep)
	For k=1 To count
		f.Frag=New Frag
		f\x=MouseX()
		f\y=MouseY()
		f\xs=Cos( an ) * Rnd( 3,4 )
		f\ys=Sin( an ) * Rnd( 3,4 )
		f\r=255:f\g=255:f\b=255
		an=an+anstep
	Next
End Function

Function UpdateFrags()
	For f.Frag=Each Frag
		f\x=f\x+f\xs
		f\y=f\y+f\ys
		f\ys=f\ys+gravity
		If f\x<0 Or f\x>=width Or f\y>=height
			Delete f
		Else If f\b>0
			f\b=f\b-5
		Else If f\g>0
			f\g=f\g-3
		Else If f\r>0
			f\r=f\r-1
			If f\r=0 Then Delete f
		EndIf
	Next
End Function

Function RenderFrags()
	For f.Frag=Each Frag
		Color f\r,f\g,f\b
		Rect f\x-1,f\y-1,3,3
	Next
End Function
