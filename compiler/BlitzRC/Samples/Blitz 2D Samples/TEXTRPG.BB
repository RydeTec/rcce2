Global ptstotal,move,menu,menuy,addsub,xp,nxp,lev,px,py,pz,hp,tp,i$,at$,old$,message$,name$
Global currentweap,currentweapdamage
Const Swidth=640,Sheight=480,theight=12,twidth=10,MX=9,MY=9,MZ=9,weapons=5,items=5

Dim theexits(5)
Dim roomexits(MX,MY,MZ,5)
Dim desc$(3)
Dim weaponsdam(weapons)
Dim weaponname$(weapons)
Dim weapondesc$(weapons)
Dim weaponstr(weapons)

Dim itemname$(items)
Dim itemdesc$(items)
Dim itemaction$(items)
Dim itemtype(items)

itemname$(0)="tooth brush"
itemdesc$(0)="You examine the tooth brush.  It's a tooth brush all right."
itemaction$(0)="You rub the tooth brush in a circular motion.  Dentist fresh!"

itemname$(1)="grenade"
itemdesc$(1)="You see a small grenade.  It can blow up all the enemies in the room."
itemaction$(1)="You toss the grenade and duck for cover!  It explodes, damaging everyone."

itemname$(2)="silver key"
itemdesc$(2)="This is a small shiney silvery key."
itemaction$(2)="The key fits in the lock perfectly!  The door opens..."

itemname$(3)="pass card"
itemdesc$(3)="This card has a magnetic strip on the back."
itemaction$(3)="You slide the card into the slot and it disappears.  The door slides open."

itemname$(4)="lock pick"
itemdesc$(4)="This lock pick might open some doors..."
itemaction$(4)="You use the lock pick.  You hear some clicking noises, and the door opens."

itemname$(5)="butterscotch candy"
itemdesc$(5)="This candy looks tasty."
itemaction$(5)="You eat the candy.  Mmmmm."

Type item
	Field itype
	Field have
	Field x,y,z
End Type

it.item=New item
it\x=0
it\y=0
it\z=0
it\itype=0
it\have=0


weaponsdam(0)=1
weaponname$(0)="unarmed"
weapondesc$(0)="How can you look at nothing?  I guess you close your eyes."
weaponstr(0)=0

weaponsdam(1)=2
weaponname$(1)="kitchen knife"
weapondesc$(1)="This weapon was meant to cut fish, but it's better than nothing."
weaponstr(1)=5

weaponsdam(2)=3
weaponname$(2)="hatchet"
weapondesc$(2)="You see a small wood hatchet.  It might make a good weapon."
weaponstr(2)=5

weaponsdam(3)=4
weaponname$(3)="hunting blade"
weapondesc$(3)="This long knife is razor sharp."
weaponstr(3)=6

weaponsdam(4)=5
weaponname$(4)="short sword"
weapondesc$(4)="You see a sturdy short sword.  Trained fighters can use it easily."
weaponstr(4)=8

weaponsdam(5)=6
weaponname$(5)="large sword"
weapondesc$(5)="You see a rather large sword.  It could take some strength to wield."
weaponstr(5)=10

Type weapon
	Field wtype
	Field have,armed
	Field x,y,z
End Type

w.weapon=New weapon
w\x=0
w\y=0
w\z=0
w\wtype=1
w\have=False

w.weapon=New weapon
w\x=1
w\y=0
w\z=0
w\wtype=2
w\have=False

w.weapon=New weapon
w\x=2
w\y=0
w\z=0
w\wtype=3
w\have=False

w.weapon=New weapon
w\x=3
w\y=0
w\z=0
w\wtype=4
w\have=False

w.weapon=New weapon
w\x=3
w\y=0
w\z=0
w\wtype=5
w\have=False

w.weapon=New weapon
w\x=3
w\y=0
w\z=0
w\wtype=4
w\have=False

w.weapon=New weapon
w\x=3
w\y=0
w\z=0
w\wtype=5
w\have=False

currentweap=0
currentweapdamage=weaponsdam(currentweap)

; Set display mode
Graphics Swidth,Sheight

Dim attr(7)
Dim attrmax(7)
Dim attrdesc$(7)
Dim attrmin(7)

ptstotal=20

attrmin(0)=5
attrmin(1)=5
attrmin(2)=5
attrmin(3)=5
attrmin(4)=5
attrmin(5)=5
attrmin(6)=5
attrmin(7)=0

attr(0)=attrmin(0)
attr(1)=attrmin(1)
attr(2)=attrmin(2)
attr(3)=attrmin(3)
attr(4)=attrmin(4)
attr(5)=attrmin(5)
attr(6)=attrmin(6)
attr(7)=attrmin(7)

Repeat
	attr(0)=attr(0)+getrnd(3)
	attr(1)=attr(1)+getrnd(3)
	attr(2)=attr(2)+getrnd(2)
	attr(3)=attr(3)+getrnd(2)
	attr(4)=attr(4)+getrnd(2)
	attr(5)=attr(5)+getrnd(2)
	attr(6)=attr(6)+getrnd(2)
	attr(7)=attr(7)+getrnd(2)
Until ptstotal<=0

attrdesc$(0)="Hit Points"
attrdesc$(1)="Tech. Points"
attrdesc$(2)="Strength"
attrdesc$(3)="Intelligence"
attrdesc$(4)="Agility"
attrdesc$(5)="Dexterity"
attrdesc$(6)="Defense"
attrdesc$(7)="Luck"

attrmax(0)=100
attrmax(1)=100
attrmax(2)=50
attrmax(3)=50
attrmax(4)=40
attrmax(5)=40
attrmax(6)=50
attrmax(7)=100

SeedRnd MilliSecs()

Type room
	Field x,y,z
	Field n,e,w,s,u,d
	Field desc0$,desc1$,desc2$,desc3$,num
End Type

Type baddie
	Field badtype
	Field x,y,z
	Field xp,hp,dp,ar,hr
	Field name$, desc$, attack$, miss$, death$, move$
End Type

makebaddies(10)

For y=0 To MY
	For x=0 To MX
		a.room=New room
		a\x=x
		a\y=y
		a\z=0
			
		number=number+1
		a\num=number

		a\desc0$=""
		a\desc1$=""
		a\desc2$=""
		a\desc3$=""

		Select number
			Case 1:
			a\x=0
			a\y=0
			a\z=0
			a\n=0
			a\s=0
			a\e=1
			a\w=0
			a\u=0
			a\d=0
			a\desc0$="You are in your living quarters.  There is a small bed and a desk.
			a\desc1$="There is a seat built into the Floor, And everything is bolted down."
			a\desc2$="As you stand here you ponder on your spartan existence and what"
			a\desc3$="else life could hold.  Then you hit yourself for being a dumbass."

			Case 2:
			a\x=1
			a\y=0
			a\z=0
			a\n=0
			a\s=0
			a\e=1
			a\w=1
			a\u=0
			a\d=0
			a\desc0$="You are in the hall east of your living quarters."

			Case 3:
			a\x=2
			a\y=0
			a\z=0
			a\n=0
			a\s=1
			a\e=1
			a\w=1
			a\u=0
			a\d=0
			a\desc0$="You are in the north hallway of your residence floor."

			Case 4:
			a\x=3
			a\y=0
			a\z=0
			a\n=0
			a\s=0
			a\e=1
			a\w=1
			a\u=0
			a\d=0
			a\desc0$="You are outside of the elevator doors.  A sign says 'out of order.'"
			
			Case 5:
			a\x=4
			a\y=0
			a\z=0
			a\n=0
			a\s=0
			a\e=0
			a\w=1
			a\u=0
			a\d=0
			a\desc0$="You are next to a soda machine."
			Case 6:
			a\x=5
			a\y=0
			a\z=0
			a\n=0
			a\s=0
			a\e=0
			a\w=0
			a\u=0
			a\d=0
			a\desc0$=""
			Case 7:
			a\x=6
			a\y=0
			a\z=0
			a\n=0
			a\s=0
			a\e=0
			a\w=0
			a\u=0
			a\d=0
			a\desc0$=""
			Case 8:
			a\x=7
			a\y=0
			a\z=0
			a\n=0
			a\s=0
			a\e=0
			a\w=0
			a\u=0
			a\d=0
			a\desc0$=""
			Case 9:
			a\x=8
			a\y=0
			a\z=0
			a\n=0
			a\s=0
			a\e=0
			a\w=0
			a\u=0
			a\d=0
			a\desc0$=""
			Case 10:
			a\x=9
			a\y=0
			a\z=0
			a\n=0
			a\s=0
			a\e=0
			a\w=0
			a\u=0
			a\d=0
			a\desc0$=""

			Case 13:
			a\x=2
			a\y=1
			a\z=0
			a\n=1
			a\s=1
			a\e=1
			a\w=0
			a\u=0
			a\d=0
			a\desc0$="You are in the west hallway of your residence floor."
			Case 14
			a\x=3
			a\y=1
			a\z=0
			a\n=0
			a\s=0
			a\e=0
			a\w=1
			a\u=0
			a\d=0
			a\desc0$="You are at a dead-end."
			Case 21:
			a\x=0
			a\y=2
			a\z=0
			a\n=1
			a\s=0
			a\e=0
			a\w=0
			a\u=0
			a\d=0
			a\desc0$=""
			Case 22:
			a\x=1
			a\y=2
			a\z=0
			a\n=0
			a\s=0
			a\e=1
			a\w=1
			a\u=0
			a\d=0
			a\desc0$=""
			Case 23:
			a\x=2
			a\y=2
			a\z=0
			a\n=1
			a\s=0
			a\e=0
			a\w=0
			a\u=0
			a\d=0
			a\desc0$=""


		
		End Select ;number
	Next ;x
Next ;y

;populate exits array
For a.room=Each room

	rx=a\x
	ry=a\y
	rz=a\z
	
;	test$=Input(Str$(ry)+"/"+Str$(rx)+":"+Str$(a\n)+Str$(a\e)+Str$(a\s)+Str$(a\w))
	
	roomexits(rx,ry,rz,0)=a\n
	roomexits(rx,ry,rz,1)=a\e
	roomexits(rx,ry,rz,2)=a\s
	roomexits(rx,ry,rz,3)=a\w
	roomexits(rx,ry,rz,4)=a\u
	roomexits(rx,ry,rz,5)=a\d

Next ;room

name$=Input$("What is your name? ")

levelup()	

px=0
py=0

hp=attr(0)
tp=attr(1)

Locate 0,78

;*****************************************************Main Loop<<<<<<<<<<<<<<<
Repeat
	Color 255,255,255
	Cls
	
	Color 0,255,255
	If message$<>"" Then Print message$
	message$=""
		
	If checkdir()=1 Then Print "You cannot go that way."
	Print at$
	
	If xp>=nxp Then levelup()
	at$=""
	exts$=""

	roomcheck()

	checkbaddies()

	Color 255,255,255
	Text 0,180,Str$(px)+" "+Str$(py)
	getinput("What do you want to do? ")
	checkcommands()

Until KeyDown(1)

;*****************************************************Check Baddies
Function checkbaddies()
For bd.baddie=Each baddie

	If bd\x=px And bd\y=py And bd\z=pz
		Color 255,255,0
		Print "There is a "+bd\name$+" here!"
		If Rnd(10)<=bd\ar 
			If Rnd(attrmax(4)*10)+attr(4)<=(bd\hr)*(attrmax(4))
				Color 255,0,0
				Print "The "+bd\name$+" "+bd\attack$
			Else
				Color 255,128,0
				Print "The "+bd\name$+" "+bd\miss$
			EndIf
		EndIf
	Else
		
		If bd\badtype=0
			gobad=Rnd(6)
			If roomexits(bd\x,bd\y,bd\z,gobad)=1
				Color 255,0,255
				If gobad=0 Then bd\y=bd\y-1
				If gobad=1 Then bd\x=bd\x+1
				If gobad=2 Then bd\y=bd\y+1
				If gobad=3 Then bd\x=bd\x-1
				If gobad=4 Then bd\z=bd\z-1
				If gobad=5 Then bd\z=bd\z+1
				If bd\x<0 Then bd\x=MX
				If bd\x>MX Then bd\x=0
				If bd\y<0 Then bd\y=MY
				If bd\y>MY Then bd\y=0
				If bd\z<0 Then bd\z=MZ
				If bd\z>MZ Then bd\z=0
				
				If bd\x=px And bd\y=py And bd\z=pz Then Print "A "+bd\name$+" "+bd\move$+" into the room!"
			EndIf
		EndIf
	EndIf
Next ;bd.baddie

End Function


;***************************************************** Check Commands
Function checkcommands()
	i$=Trim$(i$)
	i$=Upper$(i$)
	
	If i$="STATUS" Or i$="STAT" Then status()

	If i$="QUIT" Then End
	
	If Left$(i$,4)="LOOK"
		at$=Mid$(i$,6)
		look()
	EndIf
	
	If Left$(i$,6)="ATTACK"
		at$=Mid$(i$,8)
		attack()
	EndIf
	If Left$(i$,3)="HIT"
		at$=Mid$(i$,5)
		attack()
	EndIf
	If Left$(i$,4)="KILL"
		at$=Mid$(i$,6)
		attack()
	EndIf
	
	If Left$(i$,3)="GET"
		at$=Mid$(i$,5)
		get()
	EndIf	
	If Left$(i$,4)="DROP"
		at$=Mid$(i$,6)
		drop()
	EndIf
		
	If Left$(i$,3)="INV"
		at$=Mid$(i$,5)
		inv()
	EndIf
	If i$="I"
		inv()
	EndIf
	
	If Left$(i$,3)="USE"
		at$=Mid$(i$,5)
		use()
	EndIf	
	
	If Left$(i$,3)="ARM"
		at$=Mid$(i$,5)
		arm()
	EndIf
		
	If Left$(i$,5)="UNARM"
		at$=Mid$(i$,7)
		unarm()
	EndIf
			
	If Left$(i$,6)="DISARM"
		at$=Mid$(i$,7)
		unarm()
	EndIf
	
	If i$="?" 
		showcommands()
	EndIf

End Function

;***************************************************** Show Commands
Function showcommands()
	Cls
	Color 255,255,255
	Repeat
	Until Not KeyDown(28)

	
		Print "? - Show this help screen"
		Print "! - Repeat previous action"
		Print "look [something] - Look at a creature/item for a description"
		Print "hit [something] - Attack a creature (also, attack or kill)"
		Print "get [something] - pick up an item or weapon"
		Print "drop [something] - drop an item you are carrying"
		Print "use [something] - use an item"
		Print "arm [something] - arm a weapon to attack with (also wield)"
		Print "unarm - unarm current weapon (also disarm)
		Print "inventory - check your inventory (also, i or inv)
		Print "status - Shows the status screen for your character"
		Print "quit - ends this wonderous game"
		Print "(Press Enter to continue)"

	Repeat		
	Until KeyDown(28)

End Function


;***************************************************** Use an item
Function use()
	at$=Trim$(at$)
	If Left$(at$,3)="THE" Then at$=Mid$(at$,4)
	at$=Trim$(at$)
	
	For it.item=Each item
		If it\have=True
			If Left$(at$,5)=Upper$(Left$(itemname$(it\itype),5)) And ok<>1

				; here is where you'd check the item for usability in this room

				at$=itemaction$(it\itype)
				ok=1
			EndIf
		EndIf
	Next ;it.item
	;double check right side
	If ok<>1
		For it.item=Each item
			If it\have=True
				If Right$(at$,5)=Upper$(Right$(itemname$(it\itype),5)) And ok<>1
	
					; here is where you'd check the item for usability in this room
	
					at$=itemaction$(it\itype)
					ok=1
				EndIf
			EndIf
		Next ;it.item
	EndIf	

	
	If ok=0 Then at$="You aren't carrying a "+at$+"."
End Function

;***************************************************** Arm a weapon
Function arm()
	at$=Trim$(at$)
	If Left$(at$,3)="THE" Then at$=Mid$(at$,4)
	at$=Trim$(at$)
	If currentweap=0
		For wp.weapon=Each weapon
			If wp\have=True
				If Left$(at$,5)=Upper$(Left$(weaponname$(wp\wtype),5)) And ok<>1
					at$="You arm the "+weaponname$(wp\wtype)
					currentweap=wp\wtype
					currentweapdamage=weaponsdam(currentweap)
					wp\armed=True
					ok=1
				EndIf
			EndIf
		Next ;wp.weapon
		
		If ok<>1
			For wp.weapon=Each weapon
				If wp\have=True
					If Right$(at$,5)=Upper$(Right$(weaponname$(wp\wtype),5)) And ok<>1
						at$="You arm the "+weaponname$(wp\wtype)
						currentweap=wp\wtype
						currentweapdamage=weaponsdam(currentweap)
						wp\armed=True
						ok=1
					EndIf
				EndIf
			Next ;wp.weapon	
		EndIf
		
	Else
		at$="You already have a weapon armed!  Use 'unarm' first."
		ok=1
	EndIf
	
	If ok=0 Then at$="You aren't carrying a "+at$+"."
	
End Function

;***************************************************** Unarm a weapon
Function unarm()
	at$=Trim$(at$)
	If Left$(at$,3)="THE" Then at$=Mid$(at$,4)
	at$=Trim$(at$)

	For wp.weapon=Each weapon
		If wp\armed=True
			at$="You unarm the "+weaponname$(wp\wtype)
			currentweap=0
			currentweapdamage=weaponsdam(0)
			wp\armed=False
			ok=1
		EndIf
	Next ;wp.weapon
	If ok=0 Then at$="You aren't using a "+at$+"."
	
End Function

;***************************************************** Inventory 
Function inv()
	Repeat
	Until Not KeyDown(28)
	Cls

	Locate 0,0

	Print "You are carrying:"
	Print

	For wp.weapon=Each weapon
		Color 255,255,255
		If wp\have=True
			If wp\armed=True Then Color 0,255,255
			Print weaponname$(wp\wtype)
		EndIf
	Next ;wp.weapon

	Color 255,255,255
	For it.item=Each item
		If it\have=True
			Print itemname$(it\itype)
		EndIf
	Next ;it.item
		
	Print "Press Enter to Continue."
	
	Repeat
	Until KeyDown(28)	
End Function

;***************************************************** Get an item
Function get()
	at$=Trim$(at$)
	If Left$(at$,3)="THE" Then at$=Mid$(at$,4)
	at$=Trim$(at$)

	For wp.weapon=Each weapon
		If (wp\x=px And wp\y=py And wp\x=px) And wp\have=False
			If Left$(at$,5)=Upper$(Left$(weaponname$(wp\wtype),5)) And ok<>1
				at$="You get the "+weaponname$(wp\wtype)
				wp\have=True
				ok=1
			EndIf
		EndIf
	Next ;wp.weapon
	
	; if they just type in sword instead of short sword, let's go back and check the right half of the input
	If ok<>1
		For wp.weapon=Each weapon
			If (wp\x=px And wp\y=py And wp\x=px) And wp\have=False
				If Right$(at$,5)=Upper$(Right$(weaponname$(wp\wtype),5)) And ok<>1
					at$="You get the "+weaponname$(wp\wtype)
					wp\have=True
					ok=1
				EndIf
			EndIf
		Next ;wp.weapon	
	EndIf

	; check items
	If ok<>1
		For it.item=Each item
			If (it\x=px And it\y=py And it\x=px) And it\have=False
				If Left$(at$,5)=Upper$(Left$(itemname$(it\itype),5)) And ok<>1
					at$="You get the "+itemname$(it\itype)
					it\have=True
					ok=1
				EndIf
			EndIf
		Next ;it.item	
	EndIf
	
	If ok<>1 
		; double check the right side
		For it.item=Each item
			If (it\x=px And it\y=py And it\x=px) And it\have=False
				If Right$(at$,5)=Upper$(Right$(itemname$(it\itype),5)) And ok<>1
					at$="You get the "+itemname$(it\itype)
					it\have=True
					ok=1
				EndIf
			EndIf
		Next ;it.item
	EndIf


	
	If ok=0 Then at$="You don't see a "+at$+" here."
End Function

;***************************************************** Drop an item
Function drop()
	at$=Trim$(at$)
	If Left$(at$,3)="THE" Then at$=Mid$(at$,4)
	at$=Trim$(at$)

	For wp.weapon=Each weapon
		If wp\have=True
			If wp\armed=False
				If Left$(at$,5)=Upper$(Left$(weaponname$(wp\wtype),5)) And ok<>1
					at$="You drop the "+weaponname$(wp\wtype)
					wp\have=False
					wp\x=px
					wp\y=py
					wp\z=pz
					ok=1
				EndIf
			ElseIf ok=0
				oops$="You must unarm it first."
				ok=2
			EndIf
		EndIf
	Next ;wp.weapon

	;double check the right side
	If ok<>1
		For wp.weapon=Each weapon
			If wp\have=True
				If wp\armed=False
					If Right$(at$,5)=Upper$(Right$(weaponname$(wp\wtype),5)) And ok<>1
						at$="You drop the "+weaponname$(wp\wtype)
						wp\have=False
						wp\x=px
						wp\y=py
						wp\z=pz
						ok=1
					EndIf
				ElseIf ok=0
					oops$="You must unarm it first."
					ok=2
				EndIf
			EndIf
		Next ;wp.weapon
	EndIf

	If ok<>1
		For it.item=Each item
			If it\have=True
				If Left$(at$,5)=Upper$(Left$(itemname$(it\itype),5)) And ok<>1
					at$="You drop the "+itemname$(it\itype)
					it\have=False
					it\x=px
					it\y=py
					it\z=pz
					ok=1
				EndIf
			EndIf
		Next ;it.item
	EndIf
	
	; double check the right side
	If ok<>1
		For it.item=Each item
			If it\have=True
				If Right$(at$,5)=Upper$(Right$(itemname$(it\itype),5)) And ok<>1
					at$="You drop the "+itemname$(it\itype)
					it\have=False
					it\x=px
					it\y=py
					it\z=pz
					ok=1
				EndIf
			EndIf
		Next ;it.item
	EndIf

	
	If ok=2 Then at$=oops$
	
	If ok=0 Then at$="You don't have a "+at$+"."
	
End Function

;***************************************************** Attack
Function attack()
	at$=Trim$(at$)
	If Left$(at$,3)="THE" Then at$=Mid$(at$,4)
	at$=Trim$(at$)

	For bd.baddie=Each baddie
		If bd\x=px And bd\y=py And bd\z=pz
			If Left$(at$,5)=Upper$(Left$(bd\name$,5)) And ok<>1
				If Rnd(attrmax(5)+(attrmax(5)/3))<=attr(5)+(attrmax(5)/4)
					bd\hp=bd\hp-weaponsdam(currentweap)
					at$="You hit the "+bd\name$+"! hp:"+bd\hp
					If bd\hp<0
						at$="You killed the "+bd\name$+"! +"+bd\xp+" Experience!"
						xp=xp+bd\xp
						Delete bd
					EndIf
				Else
					at$="You miss the "+bd\name$+"!"
				EndIf
				ok=1
			EndIf
			
		EndIf
	Next ;bd.baddie
	If ok<>1
		For bd.baddie=Each baddie
			If bd\x=px And bd\y=py And bd\z=pz
				If Right$(at$,5)=Upper$(Right$(bd\name$,5)) And ok<>1
					If Rnd(attrmax(5)+(attrmax(5)/3))<=attr(5)+(attrmax(5)/4)
						bd\hp=bd\hp-weaponsdam(currentweap)
						at$="You hit the "+bd\name$+"! hp:"+bd\hp
						If bd\hp<0
							at$="You killed the "+bd\name$+"! +"+bd\xp+" Experience!"
							xp=xp+bd\xp
							Delete bd
						EndIf
					Else
						at$="You miss the "+bd\name$+"!"
					EndIf
					ok=1
				EndIf
				
			EndIf
		Next ;bd.baddie
	EndIf
	
	If ok<>1 Then at$="You don't see a "+at$+" here."
	
End Function

;***************************************************** L(o)(o)K
Function look()

	at$=Trim$(at$)
	If Left$(at$,2)="AT" Then at$=Mid$(at$,3)	
	at$=Trim$(at$)
	If Left$(at$,3)="THE" Then at$=Mid$(at$,4)
	at$=Trim$(at$)

	For bd.baddie=Each baddie
		If bd\x=px And bd\y=py And bd\z=pz
			If Left$(at$,5)=Upper$(Left$(bd\name$,5))
				at$=bd\desc$
				ok=1
			EndIf
		EndIf
	Next ;bd.baddie
	;double check right side
	If ok<>1
		For bd.baddie=Each baddie
			If bd\x=px And bd\y=py And bd\z=pz
				If Right$(at$,5)=Upper$(Right$(bd\name$,5))
					at$=bd\desc$
					ok=1
				EndIf
			EndIf
		Next ;bd.baddie
	EndIf
	If ok<>1
		For w.weapon=Each weapon
			If (w\x=px And w\y=py And w\z=pz) Or w\have=True
				If Left$(at$,5)=Upper$(Left$(weaponname$(w\wtype),5)) And ok<>1	
					at$=weapondesc$(w\wtype)
					ok=1
				EndIf		
	
			EndIf
		Next ;w.weapon
	EndIf
	;double check right side 
	If ok<>1
		For w.weapon=Each weapon
			If (w\x=px And w\y=py And w\z=pz) Or w\have=True
				If Right$(at$,5)=Upper$(Right$(weaponname$(w\wtype),5)) And ok<>1	
					at$=weapondesc$(w\wtype)
					ok=1
				EndIf		
	
			EndIf
		Next ;w.weapon 
	EndIf
	
	If ok<>1
		For it.item=Each item
			If (it\x=px And it\y=py And it\z=pz) Or it\have=True
				If Left$(at$,5)=Upper$(Left$(itemname$(it\itype),5)) And ok<>1
					at$=itemdesc$(it\itype)
					ok=1
				EndIf
	
			EndIf
		Next ;w.weapon 
	EndIf
	;double check right side
	If ok<>1
		For it.item=Each item
			If (it\x=px And it\y=py And it\z=pz) Or it\have=True
				If Right$(at$,5)=Upper$(Right$(itemname$(it\itype),5)) And ok<>1
					at$=itemdesc$(it\itype)
					ok=1
				EndIf
	
			EndIf
		Next ;w.weapon 
	EndIf
	
	If ok<>1 Then at$="You don't see any "+at$+" here."
	
End Function

;***************************************************** Status Screen
Function status()
	; Draw to back buffer
	SetBuffer BackBuffer()
	Color 0,255,0
	Repeat
	Until Not KeyDown(28)
	Repeat
		Cls
		Text 0,0*theight,"Name: "+name$

		For a=0 To 7
			Color 255,255,255
			Text 10,(a+1)*theight,attrdesc$(a)+": "+Str$(attr(a))
			Text 370,(a+1)*theight,"Max: "+attrmax(a)
			drawbar(a)
		Next ;a
		Text 5,180,"Exp:"+xp+"/"+nxp+" HP:"+hp+" TP:"+tp
		Text 5,200,"Press Enter to Continue."
		Flip
	Until KeyDown(28)

	; Draw to front buffer
	SetBuffer FrontBuffer()
End Function

;***************************************************** Room Check
Function roomcheck()
	Color 200,200,200
	For a.room=Each room
		If px=a\x And py=a\y And pz=a\z
			Text 0,0,a\desc0$
			Text 0,1*theight,a\desc1$
			Text 0,2*theight,a\desc2$
			Text 0,3*theight,a\desc3$
			Text 0,4*theight,"Exits are: "
			If a\n=1 
				exts$="North"
				theexits(0)=True
				If a\e=1 Or a\s=1 Or a\w=1 Or a\u=1 Or a\d=1 
					exts$=exts$+", "
				EndIf
			Else
				theexits(0)=False
			EndIf
			If a\e=1 
				exts$=exts$+"East"
				theexits(1)=True
				If a\s=1 Or a\w=1 Or a\u=1 Or a\d=1 Then exts$=exts$+", "
			Else
				theexits(1)=False
			EndIf
			If a\s=1 
				exts$=exts$+"South"
				theexits(2)=True
				If a\w=1 Or a\u=1 Or a\d=1 Then exts$=exts$+", "
			Else
				theexits(2)=False
			EndIf	
			If a\w=1 
				exts$=exts$+"West"
				theexits(3)=True
				If a\u=1 Or a\d=1 Then exts$=exts$+", "
			Else
				theexits(3)=False
			EndIf
			If a\u=1 
				exts$=exts$+"Up"
				theexits(4)=True
				If a\d=1 Then exts$=exts$+", "
			Else
				theexits(4)=False
			EndIf	
			If a\d=1 
				theexits(5)=True
				exts$=exts$+"Down"
			Else
				theexits(5)=False
			EndIf
			
			itemsinroom=0
			For w.weapon=Each weapon
				If (w\x=px And w\y=py And w\z=pz) And w\have=False
					If itemsinroom=0 Then itemsinroom_n$="You see: "
					itemsinroom=itemsinroom+1
					If itemsinroom>1 Then itemsinroom_n$=itemsinroom_n$+", "
					itemsinroom_n$=itemsinroom_n$+weaponname$(w\wtype)
				EndIf
			Next ;w.weapon 
			
			For it.item=Each item
				If (it\x=px And it\y=py And it\z=pz) And it\have=False
					If itemsinroom=0 Then itemsinroom_n$="You see: "
					itemsinroom=itemsinroom+1
					If itemsinroom>1 Then itemsinroom_n$=itemsinroom_n$+", "
					itemsinroom_n$=itemsinroom_n$+itemname$(it\itype)
				EndIf
			Next ;it.item 			
			
		EndIf
	Next ;a.room
	Text 11*twidth,4*theight,exts$
	Text 0,5*theight,itemsinroom_n$
End Function

;*****************************************************Check Direction
Function checkdir()
	If Upper$(i$)="N" Or Upper$(i$)="NORTH" 
		If theexits(0)=True 
			py=py-1
			If py<0 Then py=MY
		Else
			Return 1
		EndIf
	EndIf
	If Upper$(i$)="E" Or Upper$(i$)="EAST"
		If theexits(1)=True 
			px=px+1
			If px>MX Then px=0
		Else
			Return 1
		EndIf
	EndIf
	If Upper$(i$)="S" Or Upper$(i$)="SOUTH"
		If theexits(2)=True 
			py=py+1
			If py>MY Then py=0
		Else
			Return 1
		EndIf
	EndIf
	If Upper$(i$)="W" Or Upper$(i$)="WEST"
		If theexits(3)=True 
			px=px-1
			If px<0 Then px=MX
		Else
			Return 1
		EndIf
	EndIf
	If Upper$(i$)="U" Or Upper$(i$)="UP"
		If theexits(4)=True 
			pz=pz-1
			If pz<0 Then pz=MZ
		Else
			Return 1
		EndIf
	EndIf
	If Upper$(i$)="D" Or Upper$(i$)="DOWN"
		If theexits(5)=True 
			pz=pz+1
			If pz>MZ Then pz=0
		Else
			Return 1
		EndIf
	EndIf
End Function

;*****************************************************Make Baddies
Function makebaddies(bads)

	For b=0 To bads
		bd.baddie=New baddie
		bd\badtype=0
		bd\x=Rnd(5)
		bd\y=Rnd(5)
		bd\z=0
		bd\xp=6
		bd\hp=5
		bd\dp=1
		;ar is attack rate (out of 10, how many times will it try to attack)
		bd\ar=7
		;hr is hit rate (out of 10, how many times will it hit you)
		bd\hr=5
		bd\name$="scuzzle bug"
		bd\desc$="The scuzzle bug is a disgusting hairy, slimy, armored dog-sized beast."
		bd\attack$="sinks its mandibles into you!"
		bd\miss$="barely misses you with its ferocious jaws!"
		bd\move$="scuttles"
		bd\death$="erupts with slime from its pores until it wriggles no more."
	Next ;b

End Function

;***************************************************** Get the input string (i$)
Function getinput(p$)
	
	Locate 0,78
	
	i$=Input$(p$)
	If i$="!"
		i$=old$
		message$=i$
	Else
		If i$<>"" Then old$=i$
	EndIf
	
End Function

;***************************************************** Level up!!
Function levelup()

	stat$="You made a new level!  Level "+lev+" exp:"+xp+"/"+nxp
	levup=False
	Select lev
	Case 0:
		nxp=100
		levup=True
		stat$="Customize your character.  Attributes can't go below 5 (except luck)"
	Case 1:
		nxp=250
		ptstotal=ptstotal+10
		levup=True
	
	End Select
	
	If levup=False
		nxp=xp+10000
		ptstotal=ptstotal+5
	EndIf
	
	lev=lev+1
	Color 0,255,0
	; Draw to back buffer
	SetBuffer BackBuffer()

	Repeat
	Until Not KeyDown(28)
	Repeat
		Cls
		Text 0,0*theight,"Name: "+name$
		
		For a=0 To 7
			Color 255,255,255
			Text 10,(a+1)*theight,attrdesc$(a)+": "+Str$(attr(a))
			Text 370,(a+1)*theight,"Max: "+attrmax(a)
			drawbar(a)
		Next ;a
	
		Color 255,255,255
		
		Text 0,9*theight,"Points left:"+Str$(ptstotal)
	
		If KeyDown(208) Or KeyDown(200) Then move=move+1
		If move>=10 Then menu=menu+KeyDown(208)-KeyDown(200)
		move=move Mod 10
		
		If KeyDown(208)=False And KeyDown(200)=False
			off=off+1
		Else
			off=0
		EndIf
		If off>=1
			move=7
			off=0
		EndIf
	
		If menu<0 Then menu=7 
		If menu>7 Then menu=0
		
		menuy=(menu+1)*theight
		Rect 1,menuy+4,8,8,True
	
		If KeyDown(205) Or KeyDown(203) Then addsub=addsub+1
		If addsub>=4
			If KeyDown(205) And ptstotal>0 And attr(menu)<attrmax(menu)
				attr(menu)=attr(menu)+1
				ptstotal=ptstotal-1
			EndIf
			If KeyDown(203) And attr(menu)>attrmin(menu) 
				attr(menu)=attr(menu)-1
				ptstotal=ptstotal+1
			EndIf
			addsub=0
		EndIf
		Text 5,180,stat$
		Text 5,200,"Select/Modify attributes with arrows.  Press Enter to Continue."
		Flip
	Until KeyDown(28)
	
	;set new minimums
	For a_num=0 To 7
		attrmin(a_num)=attr(a_num)
	Next ;a_num
	
	; Draw to front buffer
	SetBuffer FrontBuffer()
End Function

;***************************************************** Draw the bars for stats
Function drawbar(a)
	barsize=(attr(a)*200)
	barsize=barsize/(attrmax(a))

	Color 255,255,255
	Rect 160,(a+1)*theight+2,202,theight-2,False
	Color 128,128,128
	Rect 161,(a+1)*theight+3,barsize,theight-4,True
End Function

;***************************************************** get a random number for stats
Function getrnd(r)
If r>ptstotal Then r=ptstotal
val=Rnd(r)
ptstotal=ptstotal-val
Return val
End Function 

End