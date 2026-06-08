Strict
EnableGC

; --- External type stubs ----------------------------------------------------
; Items.bb references Attributes (defined in Actors.bb) and ActorInstance.
; Stub both inline so we don't drag Actors.bb (with its network/world deps)
; into this unit-test build.
Type Attributes
	Field Value[39]
	Field Maximum[39]
	Field My_ID
End Type

Type ActorInstance
	Field Account
End Type

; --- RCE wire-format helpers ------------------------------------------------
; ItemInstanceToString$ / ItemInstanceFromString use RCE_StrFromInt$ /
; RCE_IntFromStr from RCEnet.bb, which themselves rely on a Bank global
; (RCE_ConvertBank). Re-implement them locally with a private Bank so this
; test file doesn't need to pull RCEnet.bb in.
Global ItemsTest_ConvertBank.BBBank = CreateBank(8)

Function RCE_IntFromStr(Dat$)
	PokeInt ItemsTest_ConvertBank, 0, 0
	Local i
	For i = 1 To Len(Dat$)
		PokeByte ItemsTest_ConvertBank, i - 1, Asc(Mid$(Dat$, i, 1))
	Next
	Return PeekInt(ItemsTest_ConvertBank, 0)
End Function

Function RCE_StrFromInt$(Num, Length = 4)
	PokeInt ItemsTest_ConvertBank, 0, Num
	Local Dat$ = ""
	Local i
	For i = Length - 1 To 0 Step -1
		Dat$ = Chr$(PeekByte(ItemsTest_ConvertBank, i)) + Dat$
	Next
	Return Dat$
End Function

; --- Logging stub -----------------------------------------------------------
Global MainLog = 0

Function WriteLog(LogID%, Message$, Timestamp% = True, Datestamp% = False)
End Function

; --- SafeWrite stubs --------------------------------------------------------
; SaveItems now routes through SafeWriteOpen/Commit in Logging.bb. This
; test build doesn't include Logging.bb (and doesn't actually exercise
; the save path), so stub the helpers as pass-throughs: SafeWriteOpen
; returns the same filename, SafeWriteCommit just closes the handle.
Function SafeWriteOpen$(FinalPath$)
	Return FinalPath$
End Function

Function SafeWriteCommit%(TempPath$, FinalPath$, F)
	; Stub: the test build doesn't exercise SaveItems, so we never
	; receive a real file handle here. Just acknowledge.
	Return True
End Function

; --- Language helper stub ---------------------------------------------------
; GetItemType$ / GetWeaponType$ in Items.bb route through LanguageString
; (from Language.bb) for localization. We don't exercise those paths here.
Function LanguageString$(key$)
	Return key
End Function

; --- ReadBoundedString$ stub -----------------------------------------------
; LoadItems / LoadDamageTypes route every length-prefixed string through
; ReadBoundedString$ (Logging.bb) so a corrupted Items.dat can't hang the
; server. This test build doesn't exercise the load path -- the existing
; tests construct items in-memory via CreateItem -- so a no-op stub is
; sufficient to let Items.bb compile under Strict.
Function ReadBoundedString$(F, MaxLen)
	Return ""
End Function

Include "Modules\Items.bb"

; --- Test helpers -----------------------------------------------------------
; Use Items.bb's own CreateItem to register into ItemList (Dim arrays from a
; non-Strict include can't be assigned directly from Strict test scope).
Function SeedItem.Item(name$)
	Local It.Item = CreateItem()
	It\Name$ = name$
	Return It
End Function

Function ClearItemList()
	; CreateItem walks ItemList(ID) for the next free slot; deleting all
	; Item objects + the matching ItemInstance objects resets that walk.
	Delete Each ItemInstance
	Delete Each Item
End Function

; ---------------------------------------------------------------------------
; ItemInstanceStringLength is a contract constant -- pin it so anyone who
; changes the serialization format has to update the test consciously.
Test testItemInstanceStringLengthIs83Bytes()
	Assert(ItemInstanceStringLength() = 83)
End Test

; Happy-path round trip: an item instance with non-zero attributes and a
; health value survives ToString -> FromString unchanged.
Test testItemInstanceToStringAndFromStringRoundTrip()
	ClearItemList()
	Local sword.Item = SeedItem("Sword")

	Local original.ItemInstance = CreateItemInstance(sword)
	original\ItemHealth = 75
	Local idx
	For idx = 0 To 39
		original\Attributes\Value[idx] = idx - 20 ; some negative, some positive
	Next

	Local s$ = ItemInstanceToString$(original)
	Assert(Len(s$) = ItemInstanceStringLength())

	Local restored.ItemInstance = ItemInstanceFromString(s$)
	Assert(restored <> Null)
	Assert(ItemInstancesIdentical(original, restored) = True)

	ClearItemList()
End Test

; Truncated payload: ItemInstanceFromString must return Null rather than
; crash on under-length input.
Test testItemInstanceFromStringRejectsShortPayload()
	ClearItemList()
	Local defaultItem.Item = SeedItem("Default")

	Assert(ItemInstanceFromString("") = Null)
	Assert(ItemInstanceFromString("xx") = Null)
	; one byte short of the required length
	Local underSized$ = String$("x", ItemInstanceStringLength() - 1)
	Assert(ItemInstanceFromString(underSized) = Null)

	ClearItemList()
End Test

; Unknown item ID in the payload: function logs and returns Null instead
; of dereferencing the Null slot in ItemList. This protects against
; malformed saves and stale character payloads referring to deleted items.
Test testItemInstanceFromStringReturnsNullForUnknownItemID()
	ClearItemList()
	; Note: no SeedItem call -- ItemList(9999) is Null.

	Local payload$ = RCE_StrFromInt$(9999, 2) + String$("x", ItemInstanceStringLength() - 2)
	Assert(ItemInstanceFromString(payload) = Null)

	ClearItemList()
End Test

; ItemInstancesIdentical compares Item ref, ItemHealth, and all 40
; attributes. Differing any one of them should report false.
Test testItemInstancesIdenticalDetectsAnyDifference()
	ClearItemList()
	Local foo.Item = SeedItem("Foo")

	Local A.ItemInstance = CreateItemInstance(foo)
	A\ItemHealth = 80
	Local B.ItemInstance = CopyItemInstance(A)

	Assert(ItemInstancesIdentical(A, B) = True)

	B\ItemHealth = 81
	Assert(ItemInstancesIdentical(A, B) = False)

	B\ItemHealth = 80 ; reset
	B\Attributes\Value[7] = A\Attributes\Value[7] + 1
	Assert(ItemInstancesIdentical(A, B) = False)

	; Different Item ref entirely
	Local bar.Item = SeedItem("Bar")
	Local C.ItemInstance = CreateItemInstance(bar)
	Assert(ItemInstancesIdentical(A, C) = False)

	; Null safety
	Assert(ItemInstancesIdentical(Null, B) = False)
	Assert(ItemInstancesIdentical(A, Null) = False)

	ClearItemList()
End Test

; FindItem is case-insensitive on Name$. Pin the contract.
Test testFindItemMatchesCaseInsensitively()
	ClearItemList()
	Local sword.Item = SeedItem("Sword")
	Local shield.Item = SeedItem("Shield")

	Assert(FindItem("SWORD") = sword)
	Assert(FindItem("sword") = sword)
	Assert(FindItem("Shield") = shield)
	Assert(FindItem("nonexistent") = Null)

	ClearItemList()
End Test

; FindDamageType returns -1 when the lookup name is absent from
; DamageTypes$. We can't seed the array from Strict scope (Dim arrays
; from non-Strict includes reject direct assignment), so just pin the
; missing-case branch with a name guaranteed to be absent.
Test testFindDamageTypeReturnsNegativeOneWhenMissing()
	Assert(FindDamageType("__no_such_damage_type__") = -1)
End Test
