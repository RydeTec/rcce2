Strict
EnableGC

// =============================================================================
// NameUtilTest -- unit tests for Loom/NameUtil.bb's pure name-dedup helpers.
// =============================================================================
//
// NameUtil.bb has ZERO dependencies (only Blitz string built-ins), so this
// test Includes it directly with no stubs. The logic it covers prevents a
// real data-loss bug: a new/duplicated zone whose Name$ collides with an
// existing zone would overwrite that zone's <Name$>.dat on save.

Include "Modules\Loom\NameUtil.bb"


// A name not already present comes back unchanged.
Test testReturnsBaseWhenNotTaken()
    Local setStr$ = ""
    setStr = Loom_AddNameToSet$(setStr, "Plains")
    setStr = Loom_AddNameToSet$(setStr, "Test Zone")
    Assert(Loom_NextUniqueName$("New Zone", setStr) = "New Zone")
End Test


// First collision appends " 2".
Test testAppendsTwoOnFirstCollision()
    Local setStr$ = ""
    setStr = Loom_AddNameToSet$(setStr, "New Zone")
    Assert(Loom_NextUniqueName$("New Zone", setStr) = "New Zone 2")
End Test


// Runs of taken suffixes are skipped to the first free one.
Test testSkipsToNextFreeSuffix()
    Local setStr$ = ""
    setStr = Loom_AddNameToSet$(setStr, "New Zone")
    setStr = Loom_AddNameToSet$(setStr, "New Zone 2")
    setStr = Loom_AddNameToSet$(setStr, "New Zone 3")
    Assert(Loom_NextUniqueName$("New Zone", setStr) = "New Zone 4")
End Test


// Matching is case-insensitive (the filesystem is, and zone names map to
// filenames).
Test testCaseInsensitive()
    Local setStr$ = ""
    setStr = Loom_AddNameToSet$(setStr, "PLAINS")
    Assert(Loom_NameInSet%("plains", setStr) = True)
    Assert(Loom_NextUniqueName$("Plains", setStr) = "Plains 2")
End Test


// A base must not be considered taken just because a longer name contains
// it as a substring ("Foo" vs "Foobar"). The bounding delimiters guarantee
// whole-name matching.
Test testNoSubstringFalseMatch()
    Local setStr$ = ""
    setStr = Loom_AddNameToSet$(setStr, "Foobar")
    Assert(Loom_NameInSet%("Foo", setStr) = False)
    Assert(Loom_NextUniqueName$("Foo", setStr) = "Foo")
End Test


// Empty set: nothing is taken, base returns unchanged.
Test testEmptySetReturnsBase()
    Assert(Loom_NameInSet%("Anything", "") = False)
    Assert(Loom_NextUniqueName$("Anything", "") = "Anything")
End Test
