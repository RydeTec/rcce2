Const STRVAL=1
Const VERVAL=0

Const DEBUG=True

Global tag$,out$

;
; Load Path(s)
;--

;Include "types.h"
;Include "math.h"
;Graphics 640,480
;loadpath("../path/sqr.arp")
;loadenermy()

Function loadpath(filename$)

	memsize=FileSize(filename$)
	memptr=CreateBank(memsize)
	fhr=ReadFile(filename$)
	If fhr=0 Then Return 
	For f=0 To memsize-1
		PokeByte memptr,f,ReadByte(fhr)
	Next
	; render info
	pathinfo\name$="test"
	pathinfo\memptr=memptr
	pathinfo\xpos=PeekShort(memptr,266)
	pathinfo\ypos=PeekShort(memptr,268)
	pathinfo\dataoffset=PeekShort(memptr,271)
	pathinfo\spacer=PeekShort(memptr,273)
				
	; count number of steps for this path
	pathinfo\steps=memsize-pathinfo\dataoffset
	
;	For x=0 To pathinfo\steps Step 4
;		xp=PeekShort(memptr,pathinfo\dataoffset+x)
;		yp=PeekShort(memptr,2+pathinfo\dataoffset+x)
;		Plot xp,yp
;	Next
;	Flip
;	While Not KeyDown(1) : Wend
	CloseFile(fhr)
End Function

;
; Read .INI file (ARG!)
;
; - Depends on -
;Include "includes/math.h"
;Include "includes/types.h"

Function loadenermy()

;	Graphics 800,600

;	While Not KeyDown(1) : Wend

;	filename$="../sprites/enermy/aliens.ini"
	filename$="sprites/enermy/aliens.ini"

	filesze=FileSize(filename$)
	
	memptr=CreateBank(filesze)
	
	fhr=ReadFile(filename$)
	While Not Eof(fhr)
		PokeByte(memptr,cn,ReadByte(fhr))
		cn=cn+1 
	Wend
	CloseFile(fhr)
	cn=0
	; nuke the comments :)
	While cn<filesze
		char$=Chr(PeekByte(memptr,cn))
		If char$=";"
			Repeat
				byte=PeekByte(memptr,cn)
				If byte<>$0D Then PokeByte(memptr,cn,0)
				cn=cn+1
			Until byte=$0D
		EndIf
		cn=cn+1
	Wend
	cn=0: char$=""
	; 0D 0A
	While cn>-1
		cn=findtag(memptr,filesze,cn)
		
		Select Lower(tag$)
			Case "name"
				findvalue(memptr,filesze,cn,STRVAL)
				Print "[TAG] - Name - "+out$
				nmeinfo\name$=out$

			Case "file"		
				findvalue(memptr,filesze,cn,STRVAL)
				Print "[TAG] - File - "+out$
				nmeinfo\filename$=out$
			Case "style"
				findvalue(memptr,filesze,cn,VERVAL)
				vlo=val("",out$)
				;print "[TAG] - Style - "+vlo
				nmeinfo\style=vlo
				
			Case "moveable"
				findvalue(memptr,filesze,cn,VERVAL)
				vlo=val("",out$)
				;print "[TAG] - Moveable - "+vlo
				nmeinfo\moveable=vlo

			Case "canshot"
				findvalue(memptr,filesze,cn,VERVAL)
				vlo=val("",out$)
				;print "[TAG] - canshot - "+vlo
				nmeinfo\canshot=vlo

			Case "shottype"
				findvalue(memptr,filesze,cn,VERVAL)
				vlo=val("",out$)
				;print "[TAG] - shottype - "+vlo
				nmeinfo\shottype=vlo
				
			Case "energypershot"
				findvalue(memptr,filesze,cn,VERVAL)
				vlo=val("",out$)
				;print "[TAG] - EnergyPerShot - "+vlo
				nmeinfo\energypershot=vlo
				
			Case "damage"
				findvalue(memptr,filesze,cn,VERVAL)
				vlo=val("",out$)
				;print "[TAG] - Damage - "+vlo
				nmeinfo\damage=vlo
			Case "frames"
				findvalue(memptr,filesze,cn,VERVAL)
				vlo=val("",out$)
				Print "[TAG] - Frame - "+vlo
				nmeinfo\frames=vlo

			;-- last tag load image
			If nmeinfo\frames=0
				nmeinfo\image=LoadImage(nmeinfo\filename$)
				MaskImage nmeinfo\image,255,0,255
				DrawImage nmeinfo\image,0,300
			Else
				nmeinfo\image=LoadAnimImage(nmeinfo\filename$,32,32,0,nmeinfo\frames-1)
				MaskImage nmeinfo\image,255,0,255
				DrawImage nmeinfo\image,0,300
			EndIf

			;-- create new							
				nmeinfo.nme_inf=New nme_inf
		End Select 

		;char$=Chr(PeekByte(memptr,cn))
		;byte=PeekByte(memptr,cn)
		
		;If byte=$0d
		;	y=y+1 : x=0
		;EndIf
		
		;If byte>16
		;	x=x+1 : If x=60 Then x=0 : y=y+1 : If y=80 Then y=0 : x=0
			;Text x*12,y*12,char$
		;EndIf
		;cn=cn+1
	Wend
	
	FreeBank(memptr)
	
	nmeinfo.nme_inf = First nme_inf

	If DEBUG=True Then While Not MouseHit(1) : Wend

	
End Function

Function findtag(memptr,size,offset)
	cn=offset
	out$=""
	
	Repeat
		tmp$=Chr(PeekByte(memptr,cn))
		If cn=>size-1 
			tag$="" 
			Return -1
		EndIf
		cn=cn+1
	Until tmp$="="
	tmp0=cn-2
	Repeat
		char=PeekByte(memptr,cn)
		If cn<0 
			tag$="" 
			Return -1
		EndIf
		cn=cn-1
	Until char=$0D
	tmp1=cn+3
	
	;;print tmp0
	;;print tmp1

	For cn=tmp1 To tmp0
		out$=out$+Chr(PeekByte(memptr,cn))
	Next

	;;print out$
	tag$=""
	tag$=out$
	Return tmp0+2
		
End Function

Function findvalue(memptr,filesze,cn,mode)
	out$=""
	tmp0=cn
	Repeat
		char=PeekByte(memptr,cn)
		tmp$=Chr$(PeekByte(memptr,cn))
;		;print tmp$
		cn=cn+1
		Select char
			Case $20
				char=$0d
			Case $09
				char=$0d
		End Select
	Until char=$0D

	tmp1=cn-2
	For cn=tmp0+mode To tmp1-mode
		out$=out$+Chr$(PeekByte(memptr,cn))
	Next
;	;print out$
;	;print tmp1
End Function