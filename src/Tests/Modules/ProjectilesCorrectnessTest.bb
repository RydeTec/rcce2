Strict
EnableGC

; ============================================================================
; Gameplay-correctness regression pins for src/Modules/Projectiles.bb:
; template allocation (CreateProjectile), name lookup (FindProjectile),
; and the SaveProjectiles/LoadProjectiles round trip including the
; DamageType clamp and out-of-range-ID rejection that protect the
; GameServer combat path (P\DamageType indexes Resistances[19]).
;
; PIN-CURRENT-BEHAVIOR: expected values are what the shipped code
; computes, confirmed by running it.
; ============================================================================

; --- Real persistence helpers (SafeWriteOpen/Commit + ReadBoundedString$)
; come from Logging.bb, following the ReadBoundedStringTest / SafeWriteTest
; precedent. It only needs these two globals pre-declared.
Global LogMode = 0
Global MainLog = 0

Include "Modules\Logging.bb"
Include "Modules\Projectiles.bb"

; ----------------------------------------------------------------------------
; Helpers
; ----------------------------------------------------------------------------
Global ProjTestFile$ = CurrentDir$() + "projectiles_correctness_test.dat"

Function ClearProjectiles()
	; Deleting every Projectile makes the ProjectileList Dim slots read
	; Null again, resetting CreateProjectile's free-slot walk.
	Delete Each Projectile
End Function

Function CleanupProjFile()
	; SafeWriteOpen/Commit leave .tmp and .bak siblings; sweep all three.
	If FileType(ProjTestFile$) = 1 Then DeleteFile(ProjTestFile$)
	If FileType(ProjTestFile$ + ".tmp") = 1 Then DeleteFile(ProjTestFile$ + ".tmp")
	If FileType(ProjTestFile$ + ".bak") = 1 Then DeleteFile(ProjTestFile$ + ".bak")
End Function

; ----------------------------------------------------------------------------
; CreateProjectile
; ----------------------------------------------------------------------------

; Sequential allocation from slot 0, and reuse of the lowest freed slot.
Test testCreateProjectileSequentialAndReuse()
	ClearProjectiles()
	Local P0.Projectile = CreateProjectile()
	Local P1.Projectile = CreateProjectile()
	Local P2.Projectile = CreateProjectile()
	Assert(P0\ID = 0)
	Assert(P1\ID = 1)
	Assert(P2\ID = 2)
	Delete P1
	Local P3.Projectile = CreateProjectile()
	Assert(P3\ID = 1)
	ClearProjectiles()
End Test

; ----------------------------------------------------------------------------
; FindProjectile
; ----------------------------------------------------------------------------

; Name lookup is case-insensitive and returns -1 on a miss.
Test testFindProjectileCaseInsensitiveAndMiss()
	ClearProjectiles()
	Local P.Projectile = CreateProjectile()
	P\Name$ = "Fire Arrow"
	Local P2.Projectile = CreateProjectile()
	P2\Name$ = "Ice Bolt"
	Assert(FindProjectile("fire arrow") = P\ID)
	Assert(FindProjectile("FIRE ARROW") = P\ID)
	Assert(FindProjectile("Ice Bolt") = P2\ID)
	Assert(FindProjectile("No Such Thing") = -1)
	ClearProjectiles()
End Test

; ----------------------------------------------------------------------------
; SaveProjectiles / LoadProjectiles
; ----------------------------------------------------------------------------

; Full round trip: every field survives save -> clear -> load, and the
; loader reports the record count.
Test testSaveLoadProjectilesRoundTrip()
	ClearProjectiles()
	CleanupProjFile()
	Local P.Projectile = CreateProjectile()
	P\Name$ = "Fire Arrow"
	P\MeshID = 12
	P\Emitter1$ = "Sparks.rpc"
	P\Emitter2$ = "Smoke.rpc"
	P\Emitter1TexID = 3
	P\Emitter2TexID = 4
	P\Homing = 1
	P\HitChance = 85
	P\Damage = 14
	P\DamageType = 19      ; top of the valid 0..19 range -- must survive
	P\Speed = 9

	Assert(SaveProjectiles(ProjTestFile$) = True)
	ClearProjectiles()
	Assert(LoadProjectiles(ProjTestFile$) = 1)

	Local L.Projectile = ProjectileList(0)
	Assert(L <> Null)
	Assert(L\Name$ = "Fire Arrow")
	Assert(L\MeshID = 12)
	Assert(L\Emitter1$ = "Sparks.rpc")
	Assert(L\Emitter2$ = "Smoke.rpc")
	Assert(L\Emitter1TexID = 3)
	Assert(L\Emitter2TexID = 4)
	Assert(L\Homing = 1)
	Assert(L\HitChance = 85)
	Assert(L\Damage = 14)
	Assert(L\DamageType = 19)
	Assert(L\Speed = 9)

	ClearProjectiles()
	CleanupProjFile()
End Test

; The load-side DamageType clamp: anything outside 0..19 (which would
; Field-OOB Resistances[19] in GameServer's damage math -- release builds
; have no bounds check) is forced to 0 at the deserialization boundary.
Test testLoadProjectilesClampsDamageTypeTo0()
	ClearProjectiles()
	CleanupProjFile()
	Local P.Projectile = CreateProjectile()
	P\Name$ = "Corrupt"
	P\DamageType = 25      ; out of range on disk
	P\Damage = 7
	Assert(SaveProjectiles(ProjTestFile$) = True)
	ClearProjectiles()
	Assert(LoadProjectiles(ProjTestFile$) = 1)
	Local L.Projectile = ProjectileList(0)
	Assert(L <> Null)
	Assert(L\DamageType = 0)   ; clamped
	Assert(L\Damage = 7)       ; neighbouring field unaffected
	ClearProjectiles()
	CleanupProjFile()
End Test

; A missing file reports -1.
Test testLoadProjectilesMissingFileReturnsMinusOne()
	ClearProjectiles()
	CleanupProjFile()
	Assert(LoadProjectiles(ProjTestFile$) = -1)
	ClearProjectiles()
End Test

; Out-of-range-ID rejection: a record whose ID short is outside the
; 0..5000 ProjectileList bound stops the load with 0 records accepted.
; (BlitzForge ReadShort is unsigned, so a written -1 arrives as 65535
; and is caught by the `> 5000` arm.)
Test testLoadProjectilesRejectsOutOfRangeID()
	ClearProjectiles()
	CleanupProjFile()
	Local F.BBStream = WriteFile(ProjTestFile$)
	WriteShort F, 6000
	WriteString F, "Out of range"
	WriteShort F, 0
	WriteString F, ""
	WriteString F, ""
	WriteShort F, 0
	WriteShort F, 0
	WriteByte F, 0
	WriteByte F, 0
	WriteShort F, 0
	WriteShort F, 0
	WriteByte F, 0
	CloseFile(F)
	Assert(LoadProjectiles(ProjTestFile$) = 0)
	ClearProjectiles()
	CleanupProjFile()
End Test

; An ID-only record must not be published as a default Projectile. The old
; loader allocated and indexed P immediately after this two-byte read.
Test testLoadProjectilesRejectsIdOnlyRecordBeforePublish()
	ClearProjectiles()
	CleanupProjFile()
	Local F.BBStream = WriteFile(ProjTestFile$)
	WriteShort F, 7
	CloseFile(F)

	Assert(LoadProjectiles(ProjTestFile$) = 0)
	Assert(ProjectileList(7) = Null)

	ClearProjectiles()
	CleanupProjFile()
End Test

; A tail that ends partway through a length-prefixed string must also remain
; unpublished rather than accepting EOF-zero-filled fields as a template.
Test testLoadProjectilesRejectsTruncatedStringTailBeforePublish()
	ClearProjectiles()
	CleanupProjFile()
	Local F.BBStream = WriteFile(ProjTestFile$)
	WriteShort F, 7
	WriteString F, "Partial"
	WriteShort F, 12
	WriteString F, "FirstEmitter.rpc"
	WriteInt F, 4
	WriteByte F, Asc("x")
	CloseFile(F)

	Assert(LoadProjectiles(ProjTestFile$) = 0)
	Assert(ProjectileList(7) = Null)

	ClearProjectiles()
	CleanupProjFile()
End Test

; The fixed scalar tail is also part of the record boundary. Stopping in the
; two-byte Damage field must not publish the otherwise complete template.
Test testLoadProjectilesRejectsTruncatedScalarTailBeforePublish()
	ClearProjectiles()
	CleanupProjFile()
	Local F.BBStream = WriteFile(ProjTestFile$)
	WriteShort F, 8
	WriteString F, "Scalar tail"
	WriteShort F, 12
	WriteString F, "FirstEmitter.rpc"
	WriteString F, "SecondEmitter.rpc"
	WriteShort F, 1
	WriteShort F, 2
	WriteByte F, 1
	WriteByte F, 85
	WriteByte F, 7
	CloseFile(F)

	Assert(LoadProjectiles(ProjTestFile$) = 0)
	Assert(ProjectileList(8) = Null)

	ClearProjectiles()
	CleanupProjFile()
End Test
