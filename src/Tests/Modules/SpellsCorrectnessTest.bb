Strict
EnableGC

; ============================================================================
; Gameplay-correctness regression pins for src/Modules/Spells.bb: spell
; template allocation (CreateSpell), deletion (DeleteSpellTemplate),
; duplication (DuplicateSpellTemplate) and the LoadSpells/SaveSpells
; round trip including its malformed-ID rejection guard.
;
; PIN-CURRENT-BEHAVIOR: expected values are what the shipped code
; computes, confirmed by running it. Suspicious behavior is pinned and
; FLAG-FOR-HUMAN-commented, not corrected.
; ============================================================================

; --- External type stub: MemorisingSpell holds an ActorInstance ref.
Type ActorInstance
	Field Account
End Type

; --- Real persistence helpers (SafeWriteOpen/Commit + ReadBoundedString$)
; come from Logging.bb, following the ReadBoundedStringTest / SafeWriteTest
; precedent. It only needs these two globals pre-declared.
Global LogMode = 0
Global MainLog = 0

Include "Modules\Logging.bb"
Include "Modules\Spells.bb"

; ----------------------------------------------------------------------------
; Helpers
; ----------------------------------------------------------------------------
Global SpellsTestFile$ = CurrentDir$() + "spells_correctness_test.dat"

Function ClearSpells()
	; Deleting every Spell makes the SpellsList Dim slots read Null again,
	; resetting CreateSpell's free-slot walk (same pattern as ItemsTest's
	; ClearItemList).
	Delete Each Spell
	Delete Each MemorisingSpell
End Function

Function CleanupSpellsFile()
	; SafeWriteOpen/Commit leave .tmp and .bak siblings; sweep all three.
	If FileType(SpellsTestFile$) = 1 Then DeleteFile(SpellsTestFile$)
	If FileType(SpellsTestFile$ + ".tmp") = 1 Then DeleteFile(SpellsTestFile$ + ".tmp")
	If FileType(SpellsTestFile$ + ".bak") = 1 Then DeleteFile(SpellsTestFile$ + ".bak")
End Function

; ----------------------------------------------------------------------------
; CreateSpell
; ----------------------------------------------------------------------------

; First allocations take sequential IDs from 0 and carry the documented
; defaults (Name "New ability", RechargeTime 2000).
Test testCreateSpellSequentialIDsAndDefaults()
	ClearSpells()
	Local S0.Spell = CreateSpell()
	Local S1.Spell = CreateSpell()
	Assert(S0\ID = 0)
	Assert(S1\ID = 1)
	Assert(S0\Name$ = "New ability")
	Assert(S0\RechargeTime = 2000)
	ClearSpells()
End Test

; The allocator reuses the lowest freed slot.
Test testCreateSpellReusesFreedSlot()
	ClearSpells()
	CreateSpell()
	Local S1.Spell = CreateSpell()
	CreateSpell()
	Assert(S1\ID = 1)
	Assert(DeleteSpellTemplate(1) = True)
	Local S3.Spell = CreateSpell()
	Assert(S3\ID = 1)
	ClearSpells()
End Test

; ----------------------------------------------------------------------------
; DeleteSpellTemplate
; ----------------------------------------------------------------------------

; Bounds and empty-slot rejection: out-of-range IDs and unoccupied slots
; return False; a live slot returns True and frees the template.
Test testDeleteSpellTemplateBoundsAndEmptySlot()
	ClearSpells()
	Assert(DeleteSpellTemplate(-1) = False)
	Assert(DeleteSpellTemplate(65535) = False)
	Assert(DeleteSpellTemplate(5) = False)      ; empty slot
	Local S.Spell = CreateSpell()
	Assert(DeleteSpellTemplate(S\ID) = True)
	Assert(DeleteSpellTemplate(0) = False)      ; already gone
	ClearSpells()
End Test

; ----------------------------------------------------------------------------
; DuplicateSpellTemplate
; ----------------------------------------------------------------------------

; Duplication copies every template field, appends " (copy)" to the name,
; and allocates a fresh ID.
Test testDuplicateSpellTemplateCopiesAllFields()
	ClearSpells()
	Local Src.Spell = CreateSpell()
	Src\Name$ = "Fireball"
	Src\Description$ = "Burns things"
	Src\ThumbnailTexID = 42
	Src\ExclusiveRace$ = "Elf"
	Src\ExclusiveClass$ = "Mage"
	Src\RechargeTime = 1500
	Src\Script$ = "Fireball Script"
	Src\SMethod$ = "Cast"

	Local NewID = DuplicateSpellTemplate(Src\ID)
	Assert(NewID = 1)
	Local Dst.Spell = SpellsList(NewID)
	Assert(Dst <> Null)
	Assert(Dst\Name$ = "Fireball (copy)")
	Assert(Dst\Description$ = "Burns things")
	Assert(Dst\ThumbnailTexID = 42)
	Assert(Dst\ExclusiveRace$ = "Elf")
	Assert(Dst\ExclusiveClass$ = "Mage")
	Assert(Dst\RechargeTime = 1500)
	Assert(Dst\Script$ = "Fireball Script")
	Assert(Dst\SMethod$ = "Cast")
	ClearSpells()
End Test

; Missing or out-of-range sources return -1.
Test testDuplicateSpellTemplateRejectsBadSource()
	ClearSpells()
	Assert(DuplicateSpellTemplate(-1) = -1)
	Assert(DuplicateSpellTemplate(70000) = -1)
	Assert(DuplicateSpellTemplate(3) = -1)   ; empty slot
	ClearSpells()
End Test

; ----------------------------------------------------------------------------
; SaveSpells / LoadSpells
; ----------------------------------------------------------------------------

; Full round trip: every field of every template survives save -> clear ->
; load, and LoadSpells reports the record count.
Test testSaveLoadSpellsRoundTrip()
	ClearSpells()
	CleanupSpellsFile()
	Local S0.Spell = CreateSpell()
	S0\Name$ = "Heal"
	S0\Description$ = "Mends wounds"
	S0\ThumbnailTexID = 7
	S0\ExclusiveRace$ = ""
	S0\ExclusiveClass$ = "Cleric"
	S0\RechargeTime = 3000
	S0\Script$ = "Heal Script"
	S0\SMethod$ = "Main"
	Local S1.Spell = CreateSpell()
	S1\Name$ = "Zap"
	S1\RechargeTime = 100

	Assert(SaveSpells(SpellsTestFile$) = True)
	ClearSpells()
	Assert(LoadSpells(SpellsTestFile$) = 2)

	Local L0.Spell = SpellsList(0)
	Assert(L0 <> Null)
	Assert(L0\Name$ = "Heal")
	Assert(L0\Description$ = "Mends wounds")
	Assert(L0\ThumbnailTexID = 7)
	Assert(L0\ExclusiveClass$ = "Cleric")
	Assert(L0\RechargeTime = 3000)
	Assert(L0\Script$ = "Heal Script")
	Assert(L0\SMethod$ = "Main")
	Local L1.Spell = SpellsList(1)
	Assert(L1 <> Null)
	Assert(L1\Name$ = "Zap")
	Assert(L1\RechargeTime = 100)

	ClearSpells()
	CleanupSpellsFile()
End Test

; A missing file reports -1.
Test testLoadSpellsMissingFileReturnsMinusOne()
	ClearSpells()
	CleanupSpellsFile()
	Assert(LoadSpells(SpellsTestFile$) = -1)
	ClearSpells()
End Test

; Malformed-ID guard. A record whose ID short reads as 65535 (what a
; written -1 wraps to) exceeds the 0..65534 SpellsList bound and stops
; the load with 0 records accepted.
;
; FLAG-FOR-HUMAN (documentation only -- behavior is safe): the guard is
; written `If S\ID < 0 Or S\ID > 65534` with a comment claiming ReadShort
; is signed, but BlitzForge's ReadShort returns UNSIGNED 0..65535 (see
; the feedback_rce_subword memory and the KnownSpells clamp in PR #542),
; so the `< 0` arm is unreachable. The `> 65534` arm is what actually
; rejects wrapped negatives, and only -1 (= 65535) is out of range; any
; other wrapped negative lands in a valid high slot. Pinned: the reject
; works for the 65535 case. The record must otherwise be complete so this
; reaches the ID guard rather than the incomplete-record guard.
Test testLoadSpellsRejectsOutOfRangeID()
	ClearSpells()
	CleanupSpellsFile()
	Local F.BBStream = WriteFile(SpellsTestFile$)
	WriteShort F, 65535
	WriteString F, "Out of range"
	WriteString F, ""
	WriteShort F, 0
	WriteString F, ""
	WriteString F, ""
	WriteInt F, 0
	WriteString F, ""
	WriteString F, ""
	CloseFile(F)
	Assert(LoadSpells(SpellsTestFile$) = 0)
	Assert(SpellsList(65534) = Null)
	ClearSpells()
	CleanupSpellsFile()
End Test

; An ID-only tail must not be published as a zero-default Spell. The loader
; used to allocate and index a Spell immediately after this two-byte read.
Test testLoadSpellsRejectsIdOnlyRecordBeforePublish()
	ClearSpells()
	CleanupSpellsFile()
	Local F.BBStream = WriteFile(SpellsTestFile$)
	WriteShort F, 7
	CloseFile(F)

	Assert(LoadSpells(SpellsTestFile$) = 0)
	Assert(SpellsList(7) = Null)

	ClearSpells()
	CleanupSpellsFile()
End Test

; A length prefix with fewer bytes than promised is an incomplete record and
; must not publish the partial template.
Test testLoadSpellsRejectsTruncatedStringTailBeforePublish()
	ClearSpells()
	CleanupSpellsFile()
	Local F.BBStream = WriteFile(SpellsTestFile$)
	WriteShort F, 7
	WriteString F, "Partial"
	WriteInt F, 4
	WriteByte F, Asc("x")
	CloseFile(F)

	Assert(LoadSpells(SpellsTestFile$) = 0)
	Assert(SpellsList(7) = Null)

	ClearSpells()
	CleanupSpellsFile()
End Test

; A complete record before a truncated scalar tail remains loaded, while the
; incomplete second record is left unpublished.
Test testLoadSpellsRetainsCompleteRecordBeforeTruncatedScalarTail()
	ClearSpells()
	CleanupSpellsFile()
	Local F.BBStream = WriteFile(SpellsTestFile$)
	WriteShort F, 3
	WriteString F, "Complete"
	WriteString F, ""
	WriteShort F, 0
	WriteString F, ""
	WriteString F, ""
	WriteInt F, 100
	WriteString F, ""
	WriteString F, ""
	WriteShort F, 8
	WriteString F, "Scalar tail"
	WriteString F, ""
	WriteShort F, 0
	WriteString F, ""
	WriteString F, ""
	WriteShort F, 1
	CloseFile(F)

	Assert(LoadSpells(SpellsTestFile$) = 1)
	Assert(SpellsList(3) <> Null)
	Assert(SpellsList(3)\Name$ = "Complete")
	Assert(SpellsList(8) = Null)

	ClearSpells()
	CleanupSpellsFile()
End Test
