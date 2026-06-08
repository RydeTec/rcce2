; Note: "Abilities" is the actual name used for spells as they are general purpose effects, not just for magic users!

; Describes a spell
Dim SpellsList.Spell(65534)
Global Spells% = 0

Type Spell
	Field ID							  ; ServerSide Spell ID
	Field Name$, Description$             ; Name and description displayed in the spellbook
	Field ThumbnailTexID                  ; Icon displayed in the spellbook
	Field ExclusiveRace$, ExclusiveClass$ ; If this spell can only be used by a certain race and/or class
	Field RechargeTime                    ; Time taken to recharge after casting in milliseconds
	Field Script$, SMethod$                ; Script to run when cast
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

; Delete a Spell template. Used by Loom's entity-delete path. Strict
; callers can't write to SpellsList directly per the Dim-inside-Method trap,
; so this lives here in the non-Strict module.
Function DeleteSpellTemplate(ID)
	If ID < 0 Or ID > 65534 Then Return False
	S.Spell = SpellsList(ID)
	If S = Null Then Return False
	SpellsList(ID) = Null
	Delete S
	Return True
End Function

; Duplicate a Spell template. Allocate a new ID, copy every field, append
; " (copy)" to the name. Returns the new ID or -1 if SpellsList full /
; source missing.
Function DuplicateSpellTemplate(srcID)
	If srcID < 0 Or srcID > 65534 Then Return -1
	Src.Spell = SpellsList(srcID)
	If Src = Null Then Return -1

	Dst.Spell = CreateSpell()
	If Dst = Null Then Return -1

	Dst\Name$           = Src\Name$ + " (copy)"
	Dst\Description$    = Src\Description$
	Dst\ThumbnailTexID  = Src\ThumbnailTexID
	Dst\ExclusiveRace$  = Src\ExclusiveRace$
	Dst\ExclusiveClass$ = Src\ExclusiveClass$
	Dst\RechargeTime    = Src\RechargeTime
	Dst\Script$         = Src\Script$
	Dst\SMethod$        = Src\SMethod$

	Return Dst\ID
End Function

; Loads all spells from file
Function LoadSpells(Filename$)

	F = ReadFile(Filename$)
	If F = 0 Then Return -1

		Local Number = 0
		While Not Eof(F)
			S.Spell = New Spell
			S\ID = ReadShort(F)
			; Same defensive bound as LoadItems: ReadShort is signed, the list is
			; dimensioned 0..65534, and a malformed Spells.dat with a negative ID
			; corrupts memory via Dim out-of-range write.
			If S\ID < 0 Or S\ID > 65534
				Delete S
				Exit
			EndIf
			SpellsList(S\ID) = S
			; Bound every length-prefixed string to keep a corrupted /
			; tampered Spells.dat from hanging the server at boot
			; allocating gigabytes on a wild ReadInt prefix (same shape
			; as ReadActorInstance / LoadSuperGlobals / LoadEnvironment).
			; 256 for display names + race/class restrictions; 1024 for
			; script paths (matches ReadActorInstance's Script$ cap).
			S\Name$ = ReadBoundedString$(F, 256)
			S\Description$ = ReadBoundedString$(F, 1024)
			S\ThumbnailTexID = ReadShort(F)
			S\ExclusiveRace$ = ReadBoundedString$(F, 256)
			S\ExclusiveClass$ = ReadBoundedString$(F, 256)
			S\RechargeTime = ReadInt(F)
			S\Script$ = ReadBoundedString$(F, 1024)
			S\SMethod$ = ReadBoundedString$(F, 1024)
			Number = Number + 1
		Wend

	CloseFile(F)
	Return Number

End Function

; Saves all spells to file via SafeWriteOpen/Commit (atomic).
Function SaveSpells(Filename$)

	Local Temp$ = SafeWriteOpen$(Filename$)
	F = WriteFile(Temp$)
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
			WriteString F, S\SMethod$
		Next

	Return SafeWriteCommit%(Temp$, Filename$, F)

End Function