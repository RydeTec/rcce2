; Describes a projectile
Dim ProjectileList.Projectile(5000)
Type Projectile
	Field ID, Name$
	Field MeshID
	Field Emitter1$, Emitter1TexID
	Field Emitter2$, Emitter2TexID
	Field Homing, HitChance
	Field Damage, DamageType
	Field Speed
End Type

; Creates a new blank projectile
Function CreateProjectile.Projectile()

	For ID = 0 To 5000
		If ProjectileList(ID) = Null
			P.Projectile = New Projectile
			P\ID = ID
			ProjectileList(P\ID) = P
			Exit
		EndIf
	Next

	Return P

End Function

; Delete a Projectile template. Used by Loom's entity-delete path. Strict
; callers can't write to ProjectileList directly per the Dim-inside-Method
; trap, so this lives here in the non-Strict module (same shape as
; DeleteSpellTemplate in Spells.bb).
Function DeleteProjectileTemplate(ID)
	If ID < 0 Or ID > 5000 Then Return False
	P.Projectile = ProjectileList(ID)
	If P = Null Then Return False
	ProjectileList(ID) = Null
	Delete P
	Return True
End Function

; Duplicate a Projectile template. Allocate a new ID, copy every field,
; append " (copy)" to the name. Returns the new ID or -1 if ProjectileList
; is full / source missing. Field list mirrors GUE's "Copy projectile"
; button (GUE.bb BProjCopy handler).
Function DuplicateProjectileTemplate(srcID)
	If srcID < 0 Or srcID > 5000 Then Return -1
	Src.Projectile = ProjectileList(srcID)
	If Src = Null Then Return -1

	Dst.Projectile = CreateProjectile()
	If Dst = Null Then Return -1

	Dst\Name$          = Src\Name$ + " (copy)"
	Dst\MeshID         = Src\MeshID
	Dst\Emitter1$      = Src\Emitter1$
	Dst\Emitter2$      = Src\Emitter2$
	Dst\Emitter1TexID  = Src\Emitter1TexID
	Dst\Emitter2TexID  = Src\Emitter2TexID
	Dst\Homing         = Src\Homing
	Dst\HitChance      = Src\HitChance
	Dst\Damage         = Src\Damage
	Dst\DamageType     = Src\DamageType
	Dst\Speed          = Src\Speed

	Return Dst\ID
End Function

; Checks the complete variable-length Projectiles.dat layout before the
; normal reader allocates or indexes any live Projectile. ReadShort and
; ReadInt zero-fill at EOF, so an incomplete record must not become a
; zero-default template in ProjectileList.
Function ProjectileBoundedStringIsComplete%(F, FileBytes)
	If FilePos(F) + 4 > FileBytes Then Return False
	NameBytes = ReadInt(F)
	If NameBytes < 0 Or NameBytes > 256 Then Return False
	If FilePos(F) + NameBytes > FileBytes Then Return False
	SeekFile F, FilePos(F) + NameBytes
	Return True
End Function

Function ProjectileFileIsComplete%(F, FileBytes)
	While Not Eof(F)
		If FilePos(F) + 2 > FileBytes Then Return False
		SeekFile F, FilePos(F) + 2
		If ProjectileBoundedStringIsComplete(F, FileBytes) = False Then Return False
		If FilePos(F) + 2 > FileBytes Then Return False
		SeekFile F, FilePos(F) + 2
		If ProjectileBoundedStringIsComplete(F, FileBytes) = False Then Return False
		If ProjectileBoundedStringIsComplete(F, FileBytes) = False Then Return False
		; Emitter texture IDs, homing, hit chance, damage, damage type, speed.
		If FilePos(F) + 11 > FileBytes Then Return False
		SeekFile F, FilePos(F) + 11
	Wend
	Return True
End Function

; Loads all projectiles from a file and returns how many were loaded
Function LoadProjectiles(Filename$)

	Local Projectiles = 0

	F = ReadFile(Filename$)
	If F = 0 Then Return -1
	If ProjectileFileIsComplete(F, FileSize(Filename$)) = False
		CloseFile F
		Return 0
	EndIf
	SeekFile F, 0

		While Not Eof(F)
			P.Projectile = New Projectile
			P\ID = ReadShort(F)
			; ProjectileList is dimensioned 0..5000. ReadShort is signed and a
			; malformed Projectiles.dat can surface IDs outside this range; reject
			; rather than corrupt memory via Dim out-of-range write.
			If P\ID < 0 Or P\ID > 5000
				Delete P
				Exit
			EndIf
			ProjectileList(P\ID) = P
			; Bound length-prefixed strings against corrupted Projectiles.dat.
			; Same shape as the Spells.bb / Items.bb / Animations.bb sweep.
			; Name is a display field, Emitter1/2 are relative paths into
			; Data\Emitter Configs; 256 covers either with comfortable
			; headroom.
			P\Name$ = ReadBoundedString$(F, 256)
			P\MeshID = ReadShort(F)
			P\Emitter1$ = ReadBoundedString$(F, 256)
			P\Emitter2$ = ReadBoundedString$(F, 256)
			P\Emitter1TexID = ReadShort(F)
			P\Emitter2TexID = ReadShort(F)
			P\Homing = ReadByte(F)
			P\HitChance = ReadByte(F)
			P\Damage = ReadShort(F)
			P\DamageType = ReadShort(F)
			; DamageTypes$ is Dim'd (19); A\Resistances field is [19].
			; ReadShort returns -32768..32767; clamp before downstream
			; readers (GameServer combat at line ~257/265, Resistances
			; indexing) crash on Field/Dim OOB. Same shape as #207's
			; WeaponDamageType clamp.
			If P\DamageType < 0 Or P\DamageType > 19 Then P\DamageType = 0
			P\Speed = ReadByte(F)

			Projectiles = Projectiles + 1
		Wend

	CloseFile F
	Return Projectiles

End Function

; Saves all loaded projectiles via SafeWriteOpen/Commit (atomic).
Function SaveProjectiles(Filename$)

	Local Temp$ = SafeWriteOpen$(Filename$)
	F = WriteFile(Temp$)
	If F = 0 Then Return False

		For P.Projectile = Each Projectile
			WriteShort F, P\ID
			WriteString F, P\Name$
			WriteShort F, P\MeshID
			WriteString F, P\Emitter1$
			WriteString F, P\Emitter2$
			WriteShort F, P\Emitter1TexID
			WriteShort F, P\Emitter2TexID
			WriteByte F, P\Homing
			WriteByte F, P\HitChance
			WriteShort F, P\Damage
			WriteShort F, P\DamageType
			WriteByte F, P\Speed
		Next

	Return SafeWriteCommit%(Temp$, Filename$, F)

End Function

; Finds a projectile by name
Function FindProjectile(Name$)

	Name$ = Upper$(Name$)
	For P.Projectile = Each Projectile
		If Upper$(P\Name$) = Name$ Then Return P\ID
	Next
	Return -1

End Function
