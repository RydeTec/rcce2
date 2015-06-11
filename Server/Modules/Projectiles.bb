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
; Realm Crafter Projectiles module by Rob W (rottbott@hotmail.com), October 2005

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
			ProjectileList(P\ID) = P
			P\Name$ = ReadString$(F)
			P\MeshID = ReadShort(F)
			P\Emitter1$ = ReadString$(F)
			P\Emitter2$ = ReadString$(F)
			P\Emitter1TexID = ReadShort(F)
			P\Emitter2TexID = ReadShort(F)
			P\Homing = ReadByte(F)
			P\HitChance = ReadByte(F)
			P\Damage = ReadShort(F)
			P\DamageType = ReadShort(F)
			P\Speed = ReadByte(F)

			Projectiles = Projectiles + 1
		Wend

	CloseFile F
	Return Projectiles

End Function

; Saves all loaded projectiles to a file
Function SaveProjectiles(Filename$)

	F = WriteFile(Filename$)
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

	CloseFile F
	Return True

End Function

; Finds a projectile by name
Function FindProjectile(Name$)

	Name$ = Upper$(Name$)
	For P.Projectile = Each Projectile
		If Upper$(P\Name$) = Name$ Then Return P\ID
	Next
	Return -1

End Function