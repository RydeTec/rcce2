;##############################################################################################################################
; Realm Crafter version 1.10																									
; Copyright (C) 2007 Solstar Games, LLC. All rights reserved																	
; contact@solstargames.com																																																		
;																																																																#
; Programmer: Rob Williams																										
; Program: Realm Crafter Actors module
;																																
;This is a licensed product:
;BY USING THIS SOURCECODE, YOU ARE CONFIRMING YOUR ACCEPTANCE OF THE SOFTWARE AND AGREEING TO BECOME BOUND BY THE TERMS OF 
;THIS AGREEMENT. IF YOU DO NOT AGREE TO BE BOUND BY THESE TERMS, THEN DO NOT USE THE SOFTWARE.
;																		
;Licensee may NOT: 
; (i)   create any derivative works of the Engine, including translations Or localizations, other than Games;
; (ii)  redistribute, encumber, sell, rent, lease, sublicense, Or otherwise transfer rights To the Engine; or
; (iii) remove Or alter any trademark, logo, copyright Or other proprietary notices, legends, symbols Or labels in the Engine.
; (iv)   licensee may Not distribute the source code Or documentation To the engine in any manner, unless recipient also has a 
;       license To the Engine.													
; (v)  use the Software to develop any software or other technology having the same primary function as the Software, 
;       including but not limited to using the Software in any development or test procedure that seeks to develop like 
;       software or other technology, or to determine if such software or other technology performs in a similar manner as the
;       Software																																
;##############################################################################################################################
; Realm Crafter Spells module by Rob W (rottbott@hotmail.com), March 2005
; Note: "Abilities" is the actual name used for spells as they are general purpose effects, not just for magic users!

; Describes a spell
Dim SpellsList.Spell(65534)
Type Spell
	Field ID
	Field Name$, Description$             ; Name and description displayed in the spellbook
	Field ThumbnailTexID                  ; Icon displayed in the spellbook
	Field ExclusiveRace$, ExclusiveClass$ ; If this spell can only be used by a certain race and/or class
	Field RechargeTime                    ; Time taken to recharge after casting in milliseconds
	Field Script$, Method$                ; Script to run when cast
End Type

; A spell which is waiting for memorisation (server side)
Type MemorisingSpell
	Field AI.ActorInstance
	Field KnownNum
	Field CreatedTime
End Type

; Creates a new spell
Function CreateSpell.Spell()

	For i = 0 To 65534
		If SpellsList(i) = Null
			S.Spell = New Spell
			S\ID = i
			SpellsList(i) = S
			S\Name$ = "New ability"
			S\RechargeTime = 2000
			Return S
			Exit
		EndIf
	Next

End Function

; Loads all spells from file
Function LoadSpells(Filename$)

	F = ReadFile(Filename$)
	If F = 0 Then Return -1

		Local Number = 0
		While Not Eof(F)
			S.Spell = New Spell
			S\ID = ReadShort(F)
			SpellsList(S\ID) = S
			S\Name$ = ReadString$(F)
			S\Description$ = ReadString$(F)
			S\ThumbnailTexID = ReadShort(F)
			S\ExclusiveRace$ = ReadString$(F)
			S\ExclusiveClass$ = ReadString$(F)
			S\RechargeTime = ReadInt(F)
			S\Script$ = ReadString$(F)
			S\Method$ = ReadString$(F)
			Number = Number + 1
		Wend

	CloseFile(F)
	Return Number

End Function

; Saves all spells to file
Function SaveSpells(Filename$)

	F = WriteFile(Filename$)
	If F = 0 Then Return False

		For S.Spell = Each Spell
			WriteShort F, S\ID
			WriteString F, S\Name$
			WriteString F, S\Description$
			WriteShort F, S\ThumbnailTexID
			WriteString F, S\ExclusiveRace$
			WriteString F, S\ExclusiveClass$
			WriteInt F, S\RechargeTime
			WriteString F, S\Script$
			WriteString F, S\Method$
		Next

	CloseFile(F)
	Return True

End Function