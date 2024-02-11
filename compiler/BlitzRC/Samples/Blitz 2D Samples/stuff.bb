
Dim day$(6)				; Dim the array... 7 elements (0 to 6)

day$(0)="Monday"		; Set the array data
day$(1)="Tuesday"		;
day$(2)="Wednesday"		;
day$(3)="Thursday"		;
day$(4)="Friday"		;
day$(5)="Saturday"		;
day$(6)="Sunday"		;

For lop=0 To 6
	Print day$(lop)		; Loop through and print the data
Next

While Not MouseDown(1)	; Wait for a left mouse press
	VWait				;
Wend					;

End						; End