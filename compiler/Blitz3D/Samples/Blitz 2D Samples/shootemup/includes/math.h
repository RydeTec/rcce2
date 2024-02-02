;------------------------------------------------------------------------------------
;Description:
;   Used in general to stop counters climbing to high
;
;Parameters:
;   veriable - counter
;   limit    - highest value the counter can obtain
;
;Returns:
;   if counter is not the highest it can obrain returns veriable
;   if not returns the limit of the counter
;
;Usage:
;	foo=foo+1
;   foo=Max(foo,120)
;   results in foo never being higher than 120
;------------------------------------------------------------------------------------
Function Max(veriable,limit)
	If veriable=>limit Then Return limit-1 Else Return veriable
End Function

;------------------------------------------------------------------------------------
;Description:
;   Used in general to stop counters climbing to low
;
;Parameters:
;   veriable - counter
;   limit    - lowest value the counter can obtain
;
;Returns:
;   if counter is not the lowest it can obrain returns veriable
;   if not returns the limit of the counter
;
;Usage:
;   foo=120
;	foo=foo-1
;   foo=min(foo,0)
;   results in foo never being lower than 0
;------------------------------------------------------------------------------------
Function Min(veriable,limit)
	If veriable<=limit Then Return limit+1 Else Return veriable
End Function

;------------------------------------------------------------------------------------
;Description:
;   Used in general to stop counters climbing to high but when they do they
;   are returned to 0
;
;Parameters:
;   veriable - counter
;   limit    - highest value the counter can obtain before beign sent to 0
;
;Returns:
;   if counter is not the highest it can obrain returns veriable
;   if not returns 0 limit of the counter
;
;Usage:
;	foo=foo+1
;   foo=limittop(foo,120)
;   results in foo getting to 120 but return to 0 when it does
;------------------------------------------------------------------------------------
Function LoopTop(veriable,limit)
	If veriable=>limit Then Return limit=0 Else Return veriable
End Function

;------------------------------------------------------------------------------------
;Description:
;   Used in general to stop counters climbing to low but when they do they
;   are returned to limit
;
;Parameters:
;   veriable - counter
;   limit    - lowest value the counter can obtain before beign sent to limit
;
;Returns:
;   if counter is not the lowest it can obrain returns veriable
;   if not returns the limit of the counter
;
;Usage:
;	foo=120
;	foo=foo-1
;   foo=limitbottom(foo,120)
;   results in foo getting to 0 but return to 120 when it does
;------------------------------------------------------------------------------------
Function LoopBottom(veriable,limit)
	If veriable<=0 Then Return limit Else Return veriable
End Function

;--------------------------------val()-----------------------------------**
;usage val(mode$,valeur$) ou val  	
;	"%"|binaire
; 	"$"|hexadecimal
;	 ""|decimal	
;------------------------------------------------------------------------**
Function val(t$,a$)
		a$=Upper$(a$)
		; 
		Select t$
			Case""
				mul=10
			Case"$"
				mul=16
			Case"%"
				mul=2
		End Select
		;
		pow=Len(a$)-1
		ram=0
		For da = 1 To Len(a$)
		value=Asc(Mid$(a$,da,1))-48 
		Select t$
			Case "$"
			If value>9				
				value=value-7
			EndIf
		End Select
		For b=1 To pow
			value=value*mul
		Next
		pow=pow-1
		ram=ram+value
	Next
	Return ram
End Function

;************************************************************************************
;------------------------------------------------------------------------------------
;Description:
;   This function does something cool
;
;Parameters:
;   a - This Parameter tells it what to do
;   b - This Parameter is what it acts upon
;
;Returns:
;   fish - Salmon maybe?
;
;Additional Comments:
;   See Blah2 for additional information.
;------------------------------------------------------------------------------------