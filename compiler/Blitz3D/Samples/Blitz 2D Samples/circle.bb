
	;  circle routine
	;     code by
	;Krysztofiak freddy


	Graphics 640,480
	SetBuffer BackBuffer()

	Global bob=LoadImage("star1.bmp")

	;****************************
	;création des tables de sinus
	; et de cosinus
	;****************************
	Dim x_sin(360)
	Dim y_cos(360)
	Dim r_sin(360)
	Dim r_cos(360)
	
	For i=1 To 360
		x_sin(i)=Sin(i)*160
		y_cos(i)=Cos(i)*160
		r_sin(i)=Sin(i)*10
		r_cos(i)=Cos(i)*5
	Next
	
	
	;**************************************
	;nombre d'étoile et l'angle de rotation
	;et le type d'étoile, 
	;enfin bon les variables, quoi !!
	;**************************************
	
	Global nb_star=50
	Global start_table=1
	Global rebond=1
	Global decalage=1
	Global temp=0
	
	Type etoile
	Field x,y,pos_table
	End Type
	
	;*****************************
	inistars()
	;*****************************
	



While Not KeyHit(1)
	Cls
	affiche()
	mouvement()
	Line 100,480,150,480
	Flip
Wend
	End	

	;********************************
	;affiche les bob
	;********************************
	
	Function affiche()

	For stars.etoile = Each etoile
  	xx=stars\x
  	yy=stars\y
	stars\pos_table=stars\pos_table+1
		If stars\pos_table>360
			stars\pos_table=1
		EndIf
	finalx=xx+x_sin(stars\pos_table)
		If finalx<0 
			finalx = 0
		Else If finalx>640
			finalx = 640
		EndIf
	finaly=yy+y_cos(stars\pos_table)
		If finaly<0
			finaly=0
		Else If finaly>470
			finaly=470
		EndIf
	DrawImage bob,finalx,finaly
	Next
	End Function
	
	;**********************************
	;routine de mouvement
	;**********************************
	Function mouvement()
	If temp>750
		For stars.etoile=Each etoile
		stars\x=stars\x+r_cos(decalage)
		stars\y=stars\y+r_sin(rebond)
		Next
		rebond=rebond+7
		If rebond>360
			rebond=1
		EndIf
		decalage=decalage+1
		If decalage>360 
		decalage=1 
		EndIf	
	Else If temp>500
		;mouvement de rebond
		For stars.etoile=Each etoile
		stars\y=stars\y+r_sin(rebond)
		Next
		rebond=rebond+5
		If rebond>360
			rebond=1
		EndIf

		EndIf
		temp=temp+1
	End Function
	
		
	;***********************************
	;init les etoiles
	;***********************************

	Function inistars()
 	For is=1 To nb_star
  	stars.etoile = New etoile
 	stars\x=300
 	stars\y=200
 	stars\pos_table=is*8
 	Next
	End Function
	