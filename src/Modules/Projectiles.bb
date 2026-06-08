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

; Loads all projectiles from a file and returns how many were loaded
Function LoadProjectiles(Filename$)

	Local Projectiles = 0

	F = ReadFile(Filename$)
	If F = 0 Then Return -1

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