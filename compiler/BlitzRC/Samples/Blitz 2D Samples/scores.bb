; Hi-Score sort routine using 3 lines of code
; .... or 1 line of code if you prefer (but cheating... maybe :)
;
; By Simon Hitchen 

;
;
; 
;
;
;

Dim hs(10)					; Array to hold 10 Hi-Scores
Graphics 640,480
SetBuffer FrontBuffer()

Repeat

	For lop=1 To 10			; Display scores
		If lop<10
			rank$="Rank: 0"
		Else
			rank$="Rank: "
		End If
		Print rank$+lop+" .... "+Right$("000000000"+hs(lop),10)
	Next
	
	
	a=Input$("Enter A Score (-1 to end) :")		; Enter a new number to sort
	
	Cls											; Clear the screen
	
	
	hs(0)=a

	; The 3 line sort routine
	For lop=1 To 10
		If hs(0)>hs(lop) Then temp=hs(lop):hs(lop)=hs(0):hs(0)=temp
	Next
	
	
	; It can also be done on 1 line
	; 
	; comment out the 3 line version and un-comment this one...
	; ... works the same :)
	;
;	For lop=1 To 10 : If hs(0)>hs(lop) : temp=hs(lop) : hs(lop)=hs(0) : hs(0)=temp : EndIf : Next

Until a<0

End
