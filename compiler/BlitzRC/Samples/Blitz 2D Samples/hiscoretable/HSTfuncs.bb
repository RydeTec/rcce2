; Name:			HSTfuncs.bb
; Extra Files:	highscores.dat

Type highscoretable
Field name$,score
End Type

; Okay let's try it out !

; First construct a default highscore table with 5 entries !

BuildHST()

; Print those to the screen

Print "--- This is the original data ---"

PrintHST()

; Save it to disc with simple XOR encryption !

If XORSaveHST()=0
	RuntimeError "Unable to save HST"
EndIf
; Load it back from disc and decrypt it !

If XORLoadHST()=0
	RuntimeError "Unable to load HST"
EndIf
; Print the loaded one to the screen !

Print ""
Print "--- This is the decrypted data ---"

PrintHST()

MouseWait

End

; Functions start here !


; ..................................................................
; : This function will build a new High Score Table for you to use :
; : By default the scores start at 500 and all names are FlameDuck :
; : You are welcome to change this to anything more suitable for   :
; : your game                                                      :
; :................................................................:

Function BuildHST()
	PurgeHST()									; Remove any previous Highscores
	For i= 5 To 1 Step-1						; Starting at the top, move down the scores in a loop, this creates 5 scores. Since we're using a type you can create as many as you want.
		a.highscoretable=New highscoretable		; Add a New element to our High Score Table
		a\score=i*100							; Add a new Score to our High Score Table
		a\name$="FlameDuck"						; Add a new Name to go with the score
	Next										; And loop again
End Function									; That concludes the BuildHST function.

; ........................................................
; : This function will simply delete any and all entries :
; : currently in the High Score Table                    :
; :......................................................:

Function PurgeHST()
	For a.highscoretable=Each highscoretable	; Loop through all HST entries
		Delete a								; And delete them
	Next										; End of the loop
End Function									; End of this very simple Function

; ................................................
; : This function dumps the HST to the display   :
; : Only included for completeness you will most :
; : likely want to create your own routine to    :
; : display the HST                              :
; :..............................................:

Function PrintHST()
	For a.highscoretable=Each highscoretable	; Loop through all HST entries
		Print LSet$(a\name,16)+a\score			; And print them to the display (sortof)
	Next										; End of the loop
End Function									; And the end of the function aswell

; ................................................
; : This function uses a simple XOR encryption   :
; : to save the HST to disc, it will return 0 if :
; : it couldn't write the file (If it was run    :
; : from the CD or another writeprotected media) :
; : or 1 if everything was a success ! You might :
; : want to check this value just to make sure.  :
; :..............................................:

Function XORSaveHST()
	longcount=0											; Initialize the longword/doubleword counter
	For a.highscoretable=Each highscoretable			; Loop through the HST
		longcount=longcount+2							; Add two longs to the total, one for the score, and one for the EOL marker
		longcount=longcount+LongsInString(a\name$)		; Add whatever number of longwords or doublewords are in the String
	Next												; End of our little loop.

	SeedRnd longcount									; Seed the Rnd function with the number of longwords in the file

	filehandle=WriteFile("highscores.dat")				; Attempt to create the highscores.dat file
	If filehandle										; If we are successful
		For a.highscoretable=Each highscoretable		; Begin looping through the High Score Table
			b=a\score Xor Rnd(-2147483648,2147483647)	; Xor the score with a 32-bit random number
			WriteInt filehandle,b						; and write the result to the file
			For i=1 To LongsInString(a\name$)			; Loop though each individual longword in the string
				temp$=Mid$(a\name,(i)*4-3,4)			; Extract 4 characters from the name
				b=Mki(temp$) Xor Rnd(-2147483648,2147483647)	; convert them to a longword and Xor them with a 32-bit number
				WriteInt filehandle,b					; And write them to disc
			Next										; Go to the next position in the string and start over
			b=Mki("@EOL") Xor Rnd(-2147483648,2147483647);Encrypt the EOL marker
			WriteInt filehandle,b						; Write the EOL marker to disc
		Next											; Go to the next HST entry
		CloseFile filehandle							; Close the file
		Return 1										; And return a '1' for success
	Else												; If we wheren't successful in creating the file
		CloseFile filehandle							; Close the file
		Return 0										; and return a '0' for failure
	EndIf
End Function											; End of this very complicated Function

; ................................................
; : This function uses a simple XOR encryption   :
; : to load a previously saved HST from disc	 :
; : like the save function it will return 0 if   :
; : it couldn't read from the file (i.e. it does :
; : not exsist) or a '1' if the HST was loaded   :
; : successfully !                               :
; :..............................................:

Function XORLoadHST()
	longcount=FileSize("highscores.dat")				; Get the filesize of the HST
	If longcount Mod 4 > 0 Or longcount=0				; If the size is either 0 or not divisable by 4 it is corrupted
		Return 0										; So return a failure
	Else
		PurgeHST()										; Start by clearing the previous HST if any
		longcount=longcount/4							; Divide the filesize with 4 to find out how many longwords there are
		SeedRnd longcount								; Seed the Rnd function with the number of longwords in the file

		filehandle=ReadFile("highscores.dat")			; Open the file for reading
		Repeat											; And start our main loop
		a.highscoretable=New highscoretable				; Add a new element to our HST
			b=ReadInt(filehandle)						; Read the score from the file
			a\score=b  Xor Rnd(-2147483648,2147483647)	; Xor it and put it into the list
			add$=""										; Clear a variable to use when rebiulding the name
			While add$<>"@EOL"							; Until we decypher the EOL string.
				a\name=a\name+add$						; Add add$ to the end of our current HST name entry
				b=ReadInt(filehandle)					; Read a value coresponding to 4 *Charecters from the file
				b=b Xor Rnd(-2147483648,2147483647)		; and decrypt them using Xor
				add$=Cvi$(b)							; then convert them back into a human readable form
			Wend										; Have we decyphered the EOL marker ?
		Until FilePos(filehandle) = longcount*4			; Then unless we're at the last entry in the HST do another loop
	EndIf
	CloseFile filehandle								; Close the file again
	Return 1											; Wow ! It all worked great !
End Function											; End end yet another complicated Function

; .................................................
; : This function is simply used to calculate how :
; : many longwords there are in any given string  :
; :...............................................:

Function LongsInString(a$)
	bytes=Len(a$)										; Since each character uses one byte, the number of bytes must be equal to the Length of the string
	retvalue=bytes/4									; And since a longword is 4 bytes there must be one forth as many longwords
	If bytes Mod 4 > 0 Then retvalue=retvalue+1			; Now if we have any characters left, add another long
	Return retvalue										; And return the number
End Function											; Nice with another simple and easy to understand Function

; ......................................................
; : This function converts a 4 char string to a number :
; : a command that was in AmiBlitz but I was unable to :
; : find in the PC version                             :
; :....................................................:

Function Mki(temp$)
	If Len(temp$)<>4									; If the string we get is either shorter or longer than 4
		temp$=LSet$(temp$,4)							; make sure it is four chars long and pad with spaces if it isn't
	EndIf

	retvalue=Asc(Mid$(temp$,1,1)) Shl 24				; Take the ASCII value of the first char and shift it 3 bytes to the left
	retvalue=retvalue+Asc(Mid$(temp$,2,1)) Shl 16		; Take the ASCII value of the second char and shift it 2 bytes to the left
	retvalue=retvalue+Asc(Mid$(temp$,3,1)) Shl 8		; Take the ASCII value of the third char and shift it 1 byte to the left
	retvalue=retvalue+Asc(Mid$(temp$,4,1))				; Just add the ASCII value of the fourth char
	Return retvalue										; And return the sum of them all
End Function											; And that's as they say, was that

; ........................................................
; : This function converts a number into a 4 char string :
; : a command that was in AmiBlitz but I was unable to   :
; : find in the PC version                               :
; :......................................................:

Function Cvi$(i)
	ret$=Chr$((i And $FF000000) Shr 24)					; Use And to isolate the value we want and Shr to put it back in place for a Chr$ conversion
	ret$=ret$+Chr$((i  And $FF0000) Shr 16)				; ------------------------------"----------------------
	ret$=ret$+Chr$((i And $FF00) Shr 8)					; ------------------------------"----------------------
	ret$=ret$+Chr$((i And $FF))							; No need to shift this one, just And to filter out the other chars
	Return ret$											; And return the string.
End Function											; Whew, now all that's done.